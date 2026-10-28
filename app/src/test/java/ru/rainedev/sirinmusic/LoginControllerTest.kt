package ru.rainedev.sirinmusic

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import ru.rainedev.sirinmusic.data.*

class LoginControllerTest : LocalizedTest() {
    private val profiles = listOf(AccountProfile("a", "First"), AccountProfile("b", "Second"))
    private class Repository : LoginRepository {
        var statuses = 0
        var tokens = 0
        var validations = 0
        var status: suspend () -> AuthStatus = { AuthStatus(ok = true) }
        var token: suspend (String?) -> TokenReply = { TokenReply("issued") }
        var validate: suspend (ServerConnection, Boolean) -> Health = { _, _ -> Health(ok = true, tracks = 42) }
        override suspend fun status(connection: ServerConnection): AuthStatus { statuses++; return status() }
        override suspend fun token(connection: ServerConnection, username: String, password: String, profileId: String?): TokenReply {
            tokens++; return token(profileId)
        }
        override suspend fun validate(connection: ServerConnection, anonymous: Boolean): Health {
            validations++; return validate.invoke(connection, anonymous)
        }
    }
    private class Harness(scope: CoroutineScope, val repo: Repository = Repository()) {
        val connections = mutableListOf<ServerConnection>()
        val anonymous = mutableListOf<Boolean>()
        val controller = LoginController(scope, repo, { "client" }) { connection, health, public ->
            assertEquals(42, health.tracks)
            connections.add(connection); anonymous.add(public)
        }
    }

    @Test fun tokenConnectionIsValidatedBeforeCommitAndNormalized() = runBlocking {
        val h = Harness(this)
        val gate = CompletableDeferred<Unit>()
        h.repo.validate = { connection, anonymous ->
            assertEquals(ServerConnection("https://music.example", "secret", "client"), connection)
            assertFalse(anonymous); gate.await(); Health(ok = true, tracks = 42)
        }
        val job = h.controller.token(" https://music.example/ ", " secret ")!!
        assertTrue(h.controller.state.value.checking)
        yield(); assertTrue(h.connections.isEmpty())
        gate.complete(Unit); job.join()
        assertEquals(1, h.connections.size)
        assertEquals(LoginState(), h.controller.state.value)
    }

    @Test fun anonymousModeUsesAnonymousValidation() = runBlocking {
        val h = Harness(this)
        h.controller.token("https://music.example", "", anonymous = true)!!.join()
        assertEquals(listOf(true), h.anonymous)
        assertEquals("", h.connections.single().token)
    }

    @Test fun invalidInputDoesNotStartAnyRequest() = runBlocking {
        val h = Harness(this)
        assertNull(h.controller.token("not a URL", "secret"))
        assertNotNull(h.controller.state.value.error)
        assertNull(h.controller.token("https://music.example", " "))
        assertNull(h.controller.password("https://music.example", "user", ""))
        assertEquals(0, h.repo.statuses + h.repo.tokens + h.repo.validations)
        assertFalse(h.controller.state.value.checking)
    }

    @Test fun failedValidationDoesNotCommitConnection() = runBlocking {
        val h = Harness(this)
        h.repo.validate = { _, _ -> error("Unavailable") }
        h.controller.token("https://music.example", "secret")!!.join()
        assertTrue(h.connections.isEmpty())
        assertEquals("Unavailable", h.controller.state.value.error)
        assertFalse(h.controller.state.value.checking)
    }

    @Test fun cancellationDiscardsLateSuccess() = runBlocking {
        val h = Harness(this)
        val gate = CompletableDeferred<Unit>()
        h.repo.validate = { _, _ -> withContext(NonCancellable) { gate.await() }; Health(ok = true, tracks = 42) }
        val job = h.controller.token("https://old.example", "old")!!; yield()
        h.controller.cancel()
        gate.complete(Unit); job.join()
        assertTrue(h.connections.isEmpty())
        assertEquals(LoginState(), h.controller.state.value)
    }

    @Test fun oldFinallyCannotStopNewLoginOrReplaceItsError() = runBlocking {
        val h = Harness(this)
        val oldGate = CompletableDeferred<Unit>()
        val newGate = CompletableDeferred<Unit>()
        h.repo.validate = { connection, _ ->
            if (connection.token == "old") {
                withContext(NonCancellable) { oldGate.await() }
                error("Old failure")
            }
            newGate.await(); error("New failure")
        }
        val old = h.controller.token("https://old.example", "old")!!; yield()
        val next = h.controller.token("https://new.example", "new")!!; yield()
        oldGate.complete(Unit); old.join()
        assertTrue(h.controller.state.value.checking)
        assertNull(h.controller.state.value.error)
        newGate.complete(Unit); next.join()
        assertEquals("New failure", h.controller.state.value.error)
    }

    @Test fun cancelledPasswordRequestCannotRecreateProfileSelection() = runBlocking {
        val h = Harness(this)
        val gate = CompletableDeferred<Unit>()
        h.repo.token = { withContext(NonCancellable) { gate.await() }; TokenReply("old", profiles = profiles) }
        val job = h.controller.password("https://music.example", "user", "password")!!; yield()
        h.controller.cancel(); gate.complete(Unit); job.join()
        assertTrue(h.controller.state.value.profiles.isEmpty())
        assertNull(h.controller.chooseProfile("b"))
        assertTrue(h.connections.isEmpty())
    }

    @Test fun multiUserLoginRequiresUsernameBeforeIssuingToken() = runBlocking {
        val h = Harness(this)
        h.repo.status = { AuthStatus(ok = false, multiUser = true) }
        h.controller.password("https://music.example", "  ", "password")!!.join()
        assertEquals(0, h.repo.tokens)
        assertEquals(strings(R.string.app_view_model_4), h.controller.state.value.error)
    }

    @Test fun profileSelectionReusesMatchingDeviceAndValidatesIt() = runBlocking {
        val h = Harness(this)
        h.repo.token = { TokenReply("first-token", DeviceToken("device", "a"), profiles) }
        h.controller.password("https://music.example", "user", "password")!!.join()
        assertEquals(profiles, h.controller.state.value.profiles)
        assertTrue(h.connections.isEmpty())
        assertNull(h.controller.chooseProfile("unknown"))
        h.controller.chooseProfile("a")!!.join()
        assertEquals(1, h.repo.tokens)
        assertEquals("first-token", h.connections.single().token)
        assertEquals(LoginState(), h.controller.state.value)
        assertNull(h.controller.chooseProfile("b"))
    }

    @Test fun selectingAnotherProfileUsesItsIssuedTokenAndIgnoresDoubleTap() = runBlocking {
        val h = Harness(this)
        val gate = CompletableDeferred<Unit>()
        h.repo.token = { id ->
            if (id == null) TokenReply("first", DeviceToken("device", "a"), profiles)
            else { assertEquals("b", id); gate.await(); TokenReply("second", DeviceToken("other", "b"), profiles) }
        }
        h.controller.password("https://music.example", "user", "password")!!.join()
        val job = h.controller.chooseProfile("b")!!
        assertNull(h.controller.chooseProfile("a"))
        assertNull(h.controller.chooseProfile("b"))
        gate.complete(Unit); job.join()
        assertEquals(2, h.repo.tokens)
        assertEquals("second", h.connections.single().token)
    }

    @Test fun wrongProfileReplyKeepsSelectionForRetry() = runBlocking {
        val h = Harness(this)
        h.repo.token = { TokenReply("first", DeviceToken("device", "a"), profiles) }
        h.controller.password("https://music.example", "user", "password")!!.join()
        h.controller.chooseProfile("b")!!.join()
        assertTrue(h.connections.isEmpty())
        assertEquals(profiles, h.controller.state.value.profiles)
        assertEquals(strings(R.string.app_view_model_9), h.controller.state.value.error)
        h.repo.token = { TokenReply("second", DeviceToken("device", "b"), profiles) }
        h.controller.chooseProfile("b")!!.join()
        assertEquals("second", h.connections.single().token)
    }

    @Test fun emptyTokenIsRejectedBothBeforeAndAfterProfileSelection() = runBlocking {
        val h = Harness(this)
        h.repo.token = { TokenReply(" ") }
        h.controller.password("https://music.example", "user", "password")!!.join()
        assertEquals(0, h.repo.validations)
        h.repo.token = { id -> if (id == null) TokenReply("first", profiles = profiles) else TokenReply("") }
        h.controller.password("https://music.example", "user", "password")!!.join()
        h.controller.chooseProfile("b")!!.join()
        assertEquals(0, h.repo.validations)
        assertTrue(h.connections.isEmpty())
        assertNotNull(h.controller.state.value.error)
    }

    @Test fun singleProfilePasswordLoginValidatesIssuedTokenWithoutSelection() = runBlocking {
        val h = Harness(this)
        h.repo.token = { TokenReply("issued", DeviceToken("device", "a"), profiles.take(1)) }
        h.controller.password("https://music.example", "user", "password")!!.join()
        assertEquals("issued", h.connections.single().token)
        assertEquals(listOf(false), h.anonymous)
        assertEquals(LoginState(), h.controller.state.value)
    }

    @Test fun oldPasswordCompletionCannotEraseNewPendingCredentials() = runBlocking {
        val h = Harness(this)
        val gate = CompletableDeferred<Unit>()
        h.repo.token = { withContext(NonCancellable) { gate.await() }; TokenReply("old", profiles = profiles) }
        val old = h.controller.password("https://old.example", "old-user", "old-password")!!; yield()
        h.repo.token = { id -> TokenReply("new", DeviceToken("device", id ?: "a"), profiles) }
        h.controller.password("https://new.example", "new-user", "new-password")!!.join()
        gate.complete(Unit); old.join()
        assertEquals(profiles, h.controller.state.value.profiles)
        h.controller.chooseProfile("b")!!.join()
        assertEquals("https://new.example", h.connections.single().baseUrl)
        assertEquals("new", h.connections.single().token)
    }

    @Test fun passwordErrorsKeepTheirLocalizedMessages() = runBlocking {
        for ((code, text) in listOf(404 to R.string.app_view_model_6, 401 to R.string.app_view_model_7, 409 to R.string.app_view_model_8)) {
            val h = Harness(this)
            h.repo.token = { throw ApiException(code, "Server text") }
            h.controller.password("https://music.example", "user", "password")!!.join()
            assertEquals(strings(text), h.controller.state.value.error)
            assertFalse(h.controller.state.value.checking)
        }
    }

    @Test fun rejectionMessageSurvivesLateFailureOfCancelledLogin() = runBlocking {
        val h = Harness(this)
        val gate = CompletableDeferred<Unit>()
        h.repo.validate = { _, _ -> withContext(NonCancellable) { gate.await() }; error("Late failure") }
        val job = h.controller.token("https://music.example", "secret")!!; yield()
        h.controller.cancel(error = "Session expired")
        gate.complete(Unit); job.join()
        assertEquals(LoginState(error = "Session expired"), h.controller.state.value)
        assertTrue(h.connections.isEmpty())
    }
}
