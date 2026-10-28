package ru.rainedev.sirinmusic

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.rainedev.sirinmusic.data.*

internal data class ContextSession(val id: String, val contextIds: List<String>)

internal class ProfileController(
    scope: CoroutineScope,
    private val repository: ProfileRepository,
    private val session: () -> ContextSession?,
    private val onContexts: (String, List<String>) -> Unit,
    private val onMessage: (String) -> Unit,
) {
    private val jobs = TaskJobs(scope)
    private val _state = MutableStateFlow(ProfileState())
    val state = _state.asStateFlow()
    private suspend fun current(key: String) = jobs.isCurrent(key, currentCoroutineContext()[Job])

    fun load(): Job = jobs.launch("load") {
        listOf(loadTaste(), loadWeekly(), loadPolicy(), loadContexts(), loadRules(), loadShares()).joinAll()
    }
    private fun loadTaste() = read("taste", repository::profile) { s, v -> s.copy(profile = v) }
    private fun loadWeekly() = read("weekly", repository::weekly) { s, v -> s.copy(weekly = v) }
    private fun loadPolicy() = read("policy", repository::recommendations) { s, v -> s.copy(recommendations = v) }
    private fun loadContexts() = read("contexts", repository::contexts) { s, v -> s.copy(contexts = v) }
    private fun loadRules() = read("rules", repository::rules) { s, v -> s.copy(rules = v.filter { it.action == "block" }) }
    private fun loadShares() = read("shares", repository::shares) { s, v -> s.copy(shares = v.filter { it.active }) }

    private fun <T> read(section: String, fetch: suspend () -> T, apply: (ProfileState, T) -> ProfileState): Job {
        _state.update { it.copy(loading = it.loading + section) }
        return jobs.launch("read:$section") {
            try {
                val value = fetch()
                currentCoroutineContext().ensureActive()
                if (current("read:$section")) _state.update { apply(it, value).copy(errors = it.errors - section) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (current("read:$section")) _state.update { it.copy(errors = it.errors + (section to (e.message ?: strings(R.string.app_view_model_11)))) }
            } finally { if (current("read:$section")) _state.update { it.copy(loading = it.loading - section) } }
        }
    }

    private fun confirmed(section: String, apply: (ProfileState) -> ProfileState) {
        jobs.cancel("read:$section")
        _state.update { apply(it).copy(loading = it.loading - section, errors = it.errors - section) }
    }

    fun saveExplore(lo: Double, hi: Double) = mutate("explore", "taste") {
        repository.saveExplore(lo, hi)
        currentCoroutineContext().ensureActive()
        confirmed("taste") { it.copy(profile = (it.profile ?: Profile()).copy(exploreLo = lo, exploreHi = hi)) }
        onMessage(strings(R.string.app_view_model_12)); loadTaste()
    }
    fun createContext(name: String, kind: String) = mutate("contexts") {
        val created = repository.createContext(name, kind)
        currentCoroutineContext().ensureActive()
        confirmed("contexts") { it.copy(contexts = it.contexts.filterNot { row -> row.id == created.id } + created, contextCreated = it.contextCreated + 1) }
        onMessage(strings(R.string.app_view_model_13)); loadContexts()
    }
    fun toggleContext(id: String, active: Boolean) = mutate("contexts") {
        val playback = session() ?: error(strings(R.string.app_view_model_14))
        val ids = repository.activateContext(id, playback.id, active)
        currentCoroutineContext().ensureActive()
        onContexts(playback.id, ids)
    }
    fun deleteContext(id: String) = mutate("contexts") {
        val playback = session()
        if (playback != null && id in playback.contextIds) {
            val ids = repository.activateContext(id, playback.id, false)
            currentCoroutineContext().ensureActive()
            onContexts(playback.id, ids)
        }
        repository.deleteContext(id)
        currentCoroutineContext().ensureActive()
        confirmed("contexts") { it.copy(contexts = it.contexts.filterNot { row -> row.id == id }) }
        loadContexts()
    }
    fun restoreRule(id: String?) = mutate("rules") {
        repository.restoreRule(id)
        currentCoroutineContext().ensureActive()
        confirmed("rules") { if (id != null) it.copy(rules = it.rules.filterNot { row -> row.id == id }) else it }
        loadRules()
    }
    fun createShare(send: Boolean = false) = mutate("shares") { createShareResult(send) }
    fun shareRadio() = mutate("shares") {
        val shares = repository.shares().filter { it.active }
        currentCoroutineContext().ensureActive()
        val existing = shares.firstOrNull()
        if (existing != null) {
            confirmed("shares") { it.copy(shares = shares, shareToSend = existing) }
        } else createShareResult(send = true)
    }
    private suspend fun createShareResult(send: Boolean) {
        val created = repository.createShare()
        currentCoroutineContext().ensureActive()
        confirmed("shares") { it.copy(shares = it.shares.filterNot { row -> row.token == created.token } + created,
            shareToSend = if (send) created else it.shareToSend) }
        loadShares()
    }
    fun consumeShare() { _state.update { it.copy(shareToSend = null) } }
    fun revokeShare(token: String) = mutate("shares") {
        repository.revokeShare(token)
        currentCoroutineContext().ensureActive()
        confirmed("shares") { it.copy(shares = it.shares.filterNot { row -> row.token == token },
            shareToSend = it.shareToSend?.takeUnless { row -> row.token == token }) }
        loadShares()
    }

    private fun mutate(key: String, section: String = key, write: suspend () -> Unit): Job? {
        if (key in _state.value.busy) return null
        _state.update { it.copy(busy = it.busy + key, errors = it.errors - section) }
        return jobs.launchIfIdle("write:$key") {
            try { write() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (current("write:$key")) {
                    val message = e.message ?: strings(R.string.account_activity_2)
                    _state.update { it.copy(errors = it.errors + (section to message)) }
                    onMessage(message)
                }
            } finally { if (current("write:$key")) _state.update { it.copy(busy = it.busy - key) } }
        }
    }
    fun reset() { jobs.cancelAll(); _state.value = ProfileState() }
}
