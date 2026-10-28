package ru.rainedev.sirinmusic

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import ru.rainedev.sirinmusic.data.*

class ProfileControllerTest : LocalizedTest() {
    private class Repository : ProfileRepository {
        var taste: suspend () -> Profile = { Profile(exploreLo = 0.1, exploreHi = 0.4) }
        var weekly: suspend () -> WeeklyMetrics = { WeeklyMetrics() }
        var policy: suspend () -> Recommendations = { Recommendations() }
        var contexts: suspend () -> List<TasteContext> = { emptyList() }
        var rules: suspend () -> List<RadioRule> = { emptyList() }
        var shares: suspend () -> List<RadioShare> = { emptyList() }
        var write: suspend () -> Unit = {}
        var writes = 0
        var activations = mutableListOf<Triple<String, String, Boolean>>()
        override suspend fun profile() = taste()
        override suspend fun weekly() = weekly.invoke()
        override suspend fun recommendations() = policy()
        override suspend fun contexts() = contexts.invoke()
        override suspend fun rules() = rules.invoke()
        override suspend fun shares() = shares.invoke()
        private suspend fun write() { writes++; write.invoke() }
        override suspend fun saveExplore(lo: Double, hi: Double) = write()
        override suspend fun createContext(name: String, kind: String): TasteContext { write(); return TasteContext("created", name, kind) }
        override suspend fun activateContext(id: String, session: String, active: Boolean): List<String> {
            activations.add(Triple(id, session, active)); write(); return if (active) listOf(id) else emptyList()
        }
        override suspend fun deleteContext(id: String) = write()
        override suspend fun restoreRule(id: String?) = write()
        override suspend fun createShare(): RadioShare { write(); return RadioShare(token = "created", url = "https://music.example/radio") }
        override suspend fun revokeShare(token: String) = write()
    }
    private class Harness(scope: CoroutineScope) {
        val repo = Repository()
        val messages = mutableListOf<String>()
        var session: ContextSession? = null
        val applied = mutableListOf<Pair<String, List<String>>>()
        val controller = ProfileController(scope, repo, { session }, { id, ids -> applied.add(id to ids) }, messages::add)
    }
    @Test fun sectionsLoadIndependentlyAndKeepOtherDataWhenOneFails() = runBlocking {
        val h = Harness(this)
        val gate = CompletableDeferred<Unit>()
        h.repo.taste = { gate.await(); error("Taste unavailable") }
        h.repo.weekly = { WeeklyMetrics(windowDays = 7) }
        h.repo.rules = { listOf(RadioRule(id = "blocked", action = "block"), RadioRule(id = "allowed", action = "allow")) }
        h.repo.shares = { listOf(RadioShare(token = "active"), RadioShare(token = "revoked", active = false)) }
        val job = h.controller.load(); yield(); yield()
        assertEquals(7, h.controller.state.value.weekly?.windowDays)
        assertTrue("taste" in h.controller.state.value.loading)
        gate.complete(Unit); job.join()
        assertEquals("Taste unavailable", h.controller.state.value.errors["taste"])
        assertEquals(listOf("blocked"), h.controller.state.value.rules.map { it.id })
        assertEquals(listOf("active"), h.controller.state.value.shares.map { it.token })
        assertTrue(h.controller.state.value.loading.isEmpty())
    }
    @Test fun confirmedContextCreationSurvivesRefreshFailureAndRejectsDoubleSubmission() = runBlocking {
        val h = Harness(this)
        val gate = CompletableDeferred<Unit>()
        h.repo.write = { gate.await() }; h.repo.contexts = { error("Refresh failed") }
        val job = h.controller.createContext("Work", "activity")!!
        assertNull(h.controller.createContext("Duplicate", "mood"))
        gate.complete(Unit); job.join(); yield()
        assertEquals("Work", h.controller.state.value.contexts.single().name)
        assertEquals(1L, h.controller.state.value.contextCreated)
        assertEquals(1, h.repo.writes)
        assertEquals("Refresh failed", h.controller.state.value.errors["contexts"])
        assertTrue(h.controller.state.value.busy.isEmpty())
    }
    @Test fun failedContextCreationKeepsFormOpenViaUnchangedRevision() = runBlocking {
        val h = Harness(this)
        h.repo.write = { error("Cannot save") }
        h.controller.createContext("Work", "activity")!!.join()
        assertEquals(0L, h.controller.state.value.contextCreated)
        assertEquals("Cannot save", h.controller.state.value.errors["contexts"])
        assertTrue(h.controller.state.value.contexts.isEmpty())
    }
    @Test fun confirmedExploreIsKeptWhenReadFails() = runBlocking {
        val h = Harness(this)
        h.controller.load().join()
        h.repo.taste = { error("Cannot refresh") }
        h.controller.saveExplore(0.2, 0.6)!!.join(); yield()
        assertEquals(0.2, h.controller.state.value.profile?.exploreLo)
        assertEquals(0.6, h.controller.state.value.profile?.exploreHi)
        assertEquals("Cannot refresh", h.controller.state.value.errors["taste"])
    }
    @Test fun shareIsDeliveredAfterCreationEvenWhenListRefreshFails() = runBlocking {
        val h = Harness(this)
        h.repo.shares = { error("Cannot refresh") }
        h.controller.createShare(send = true)!!.join(); yield()
        assertEquals("created", h.controller.state.value.shareToSend?.token)
        assertEquals("created", h.controller.state.value.shares.single().token)
        assertEquals(1, h.repo.writes)
        h.controller.consumeShare()
        assertNull(h.controller.state.value.shareToSend)
    }
    @Test fun shareRadioReusesActiveLinkAndDoesNotCreateAnother() = runBlocking {
        val h = Harness(this)
        h.repo.shares = { listOf(RadioShare(token = "old", active = false), RadioShare(token = "active")) }
        h.controller.shareRadio()!!.join(); yield()
        assertEquals("active", h.controller.state.value.shareToSend?.token)
        assertEquals(0, h.repo.writes)
    }
    @Test fun sharingExistingLinkUsesTheAlreadyLoadedListWithoutSecondRequest() = runBlocking {
        val h = Harness(this)
        var reads = 0
        h.repo.shares = {
            reads++
            if (reads > 1) error("Redundant refresh failed")
            listOf(RadioShare(token = "active"), RadioShare(token = "revoked", active = false))
        }
        h.controller.shareRadio()!!.join(); yield()
        assertEquals(1, reads)
        assertEquals("active", h.controller.state.value.shares.single().token)
        assertEquals("active", h.controller.state.value.shareToSend?.token)
        assertTrue(h.controller.state.value.errors.isEmpty())
    }

    @Test fun revokedShareAndRestoredRuleDoNotReturnWhenRefreshFails() = runBlocking {
        val h = Harness(this)
        h.repo.shares = { listOf(RadioShare(token = "old")) }
        h.repo.rules = { listOf(RadioRule(id = "blocked", action = "block")) }
        h.controller.load().join()
        h.repo.shares = { error("Offline") }; h.repo.rules = { error("Offline") }
        h.controller.revokeShare("old")!!.join(); h.controller.restoreRule("blocked")!!.join(); yield()
        assertTrue(h.controller.state.value.shares.isEmpty())
        assertTrue(h.controller.state.value.rules.isEmpty())
    }
    @Test fun deletingActiveContextDeactivatesItInOriginalPlaybackSession() = runBlocking {
        val h = Harness(this)
        h.session = ContextSession("session", listOf("work"))
        h.repo.contexts = { listOf(TasteContext("work", "Work")) }
        h.controller.load().join()
        h.repo.contexts = { error("Offline") }
        h.controller.deleteContext("work")!!.join(); yield()
        assertEquals(listOf(Triple("work", "session", false)), h.repo.activations)
        assertEquals(listOf("session" to emptyList<String>()), h.applied)
        assertTrue(h.controller.state.value.contexts.isEmpty())
    }
    @Test fun activationRequiresPlaybackSession() = runBlocking {
        val h = Harness(this)
        h.controller.toggleContext("work", true)!!.join()
        assertEquals(0, h.repo.writes)
        assertEquals(strings(R.string.app_view_model_14), h.messages.single())
    }
    @Test fun resetRejectsLateWriteWithoutSendingShareOrContextEvent() = runBlocking {
        val h = Harness(this)
        val gate = CompletableDeferred<Unit>()
        h.repo.write = { withContext(NonCancellable) { gate.await() } }
        val job = h.controller.createShare(send = true)!!; yield()
        h.controller.reset(); gate.complete(Unit); job.join()
        assertEquals(ProfileState(), h.controller.state.value)
        assertTrue(h.messages.isEmpty())
    }
    @Test fun resetRejectsLateSectionFailures() = runBlocking {
        val h = Harness(this)
        val gate = CompletableDeferred<Unit>()
        h.repo.taste = { withContext(NonCancellable) { gate.await() }; error("Old failure") }
        val job = h.controller.load(); yield(); yield()
        h.controller.reset(); gate.complete(Unit); job.join()
        assertEquals(ProfileState(), h.controller.state.value)
    }
    @Test fun confirmedWriteCancelsOldReadEvenIfOldFailureArrivesLater() = runBlocking {
        val h = Harness(this)
        val gate = CompletableDeferred<Unit>()
        h.repo.contexts = { withContext(NonCancellable) { gate.await() }; error("Old failure") }
        val old = h.controller.load(); yield(); yield()
        h.repo.contexts = { error("New refresh failure") }
        h.controller.createContext("Work", "activity")!!.join(); yield()
        gate.complete(Unit); old.join()
        assertEquals("New refresh failure", h.controller.state.value.errors["contexts"])
        assertEquals("Work", h.controller.state.value.contexts.single().name)
    }
    @Test fun replacingReadKeepsLoadingUntilNewReadCompletes() = runBlocking {
        val h = Harness(this)
        val firstGate = CompletableDeferred<Unit>(); val nextGate = CompletableDeferred<Unit>()
        h.repo.taste = { firstGate.await(); Profile() }
        val old = h.controller.load(); yield(); yield()
        h.repo.taste = { nextGate.await(); Profile(plays = 9) }
        val next = h.controller.load(); yield(); yield()
        assertTrue("taste" in h.controller.state.value.loading)
        nextGate.complete(Unit); next.join(); old.join()
        assertEquals(9, h.controller.state.value.profile?.plays)
        assertTrue(h.controller.state.value.loading.isEmpty())
    }
}
