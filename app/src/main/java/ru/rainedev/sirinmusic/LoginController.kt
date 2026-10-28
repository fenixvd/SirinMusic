package ru.rainedev.sirinmusic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.rainedev.sirinmusic.data.*

internal class LoginController(
    scope: CoroutineScope,
    private val repository: LoginRepository,
    private val clientId: () -> String,
    private val onConnected: (ServerConnection, Health, Boolean) -> Unit,
) {
    private val jobs = TaskJobs(scope)
    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()
    // Credentials are held only during profile selection, outside observable/saved state.
    private class Pending(val connection: ServerConnection, val username: String, val password: String, val reply: TokenReply)
    private var pending: Pending? = null

    private suspend fun current(): Boolean = jobs.isCurrent("login", currentCoroutineContext()[Job])
    private suspend fun checkCurrent() {
        currentCoroutineContext().ensureActive()
        if (!current()) throw CancellationException("Superseded login")
    }

    fun token(url: String, token: String, anonymous: Boolean = false): Job? {
        cancel()
        val error = validateServerUrl(url) ?: if (!anonymous && token.isBlank()) strings(R.string.app_view_model_2) else null
        if (error != null) { _state.value = LoginState(error = error); return null }
        val connection = ServerConnection(url.trim().trimEnd('/'), token.trim(), clientId())
        return request {
            connect(connection, anonymous)
        }
    }

    fun password(url: String, username: String, password: String): Job? {
        cancel()
        val error = validateServerUrl(url) ?: if (password.isEmpty()) strings(R.string.app_view_model_3) else null
        if (error != null) { _state.value = LoginState(error = error); return null }
        val connection = ServerConnection(url.trim().trimEnd('/'), "", clientId())
        return request(passwordErrors = true) {
            val status = repository.status(connection)
            checkCurrent()
            check(!status.multiUser || username.isNotBlank()) { strings(R.string.app_view_model_4) }
            val reply = repository.token(connection, username, password)
            checkCurrent()
            check(reply.token.isNotBlank()) { strings(R.string.app_view_model_5) }
            if (reply.profiles.size > 1) {
                pending = Pending(connection, username, password, reply)
                _state.update { it.copy(profiles = reply.profiles) }
            } else connect(connection.copy(token = reply.token), anonymous = false)
        }
    }

    fun chooseProfile(id: String): Job? {
        if (_state.value.checking) return null
        val account = pending ?: return null
        if (account.reply.profiles.none { it.id == id }) return null
        return request {
            val reply = if (account.reply.device?.profileId == id) account.reply else
                repository.token(account.connection, account.username, account.password, id)
            checkCurrent()
            check(reply.token.isNotBlank()) { strings(R.string.app_view_model_5) }
            check(reply.device?.profileId == id) { strings(R.string.app_view_model_9) }
            connect(account.connection.copy(token = reply.token), anonymous = false)
        }
    }

    private suspend fun connect(connection: ServerConnection, anonymous: Boolean) {
        val health = repository.validate(connection, anonymous)
        checkCurrent()
        pending = null
        _state.value = LoginState()
        onConnected(connection, health, anonymous)
    }

    private fun request(passwordErrors: Boolean = false, block: suspend () -> Unit): Job {
        _state.update { it.copy(checking = true, error = null) }
        return jobs.launch("login") {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (current()) {
                    val error = if (passwordErrors && e is ApiException) when (e.code) {
                        404 -> strings(R.string.app_view_model_6)
                        401 -> strings(R.string.app_view_model_7)
                        409 -> strings(R.string.app_view_model_8)
                        else -> e.message
                    } else e.message
                    _state.update { it.copy(error = error ?: strings(R.string.account_activity_2)) }
                }
            } finally { if (current()) _state.update { it.copy(checking = false) } }
        }
    }

    fun cancel(error: String? = null) {
        jobs.cancelAll()
        pending = null
        _state.value = LoginState(error = error)
    }
}
