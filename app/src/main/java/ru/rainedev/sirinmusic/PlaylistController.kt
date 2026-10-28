package ru.rainedev.sirinmusic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.rainedev.sirinmusic.data.Playlist
import ru.rainedev.sirinmusic.data.PlaylistRepository

/** Owns playlist operations and drafts; server writes complete independently of follow-up reads. */
internal class PlaylistController(
    scope: CoroutineScope,
    private val repository: PlaylistRepository,
    private val onMessage: (String) -> Unit,
) {
    private val jobs = TaskJobs(scope)
    private val _state = MutableStateFlow(PlaylistState())
    val state = _state.asStateFlow()
    private var listRevision = 0L
    private val revisions = mutableMapOf<Long, Long>()

    private suspend fun current(key: String): Boolean = jobs.isCurrent(key, currentCoroutineContext()[Job])
    private fun message(e: Exception) = e.message ?: strings(R.string.account_activity_2)

    fun load(): Job = jobs.launch("list") {
        val revision = listRevision
        _state.update { it.copy(loading = true) }
        try {
            val playlists = repository.list()
            currentCoroutineContext().ensureActive()
            if (current("list") && revision == listRevision) _state.update { it.copy(playlists = playlists) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (current("list")) onMessage(message(e)) }
        finally { if (current("list")) _state.update { it.copy(loading = false) } }
    }

    fun open(id: Long, clear: Boolean = true): Job {
        _state.update { it.copy(selectedId = id, playlist = if (clear) null else it.playlist, detailLoading = true) }
        return jobs.launch("detail") {
            val revision = revisions[id]
            try {
                val playlist = repository.get(id)
                currentCoroutineContext().ensureActive()
                if (current("detail") && _state.value.selectedId == id && revisions[id] == revision) {
                    _state.update { it.copy(playlist = playlist) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current("detail")) onMessage(message(e)) }
            finally { if (current("detail")) _state.update { it.copy(detailLoading = false) } }
        }
    }

    fun close() {
        jobs.cancel("detail")
        _state.update { it.copy(selectedId = null, playlist = null, detailLoading = false) }
    }

    fun edit(playlist: Playlist? = null) {
        if (!_state.value.busy) _state.update { it.copy(editor = PlaylistDraft(playlist)) }
    }
    fun changeName(name: String) {
        if (!_state.value.busy) _state.update { it.copy(editor = it.editor?.copy(name = name, error = null)) }
    }
    fun changeDescription(description: String) {
        if (!_state.value.busy) _state.update { it.copy(editor = it.editor?.copy(description = description, error = null)) }
    }
    fun dismissEditor() {
        if (!_state.value.busy) _state.update { it.copy(editor = null) }
    }

    fun save(): Job? {
        val draft = _state.value.editor ?: return null
        if (draft.name.isBlank()) {
            _state.update { it.copy(editor = draft.copy(error = strings(R.string.app_view_model_22))) }
            return null
        }
        return mutate(editor = true) {
            val saved = draft.original?.let { repository.edit(it, draft.name.trim(), draft.description.trim()) }
                ?: repository.create(draft.name.trim(), draft.description.trim())
            currentCoroutineContext().ensureActive()
            changed(saved.id)
            _state.update { it.copy(editor = null,
                playlists = if (it.playlists.any { row -> row.id == saved.id })
                    it.playlists.map { row -> if (row.id == saved.id) saved else row } else it.playlists + saved,
                playlist = if (it.selectedId == saved.id) saved else it.playlist) }
            load() // A failed refresh must not turn a confirmed creation into a retry/duplicate.
        }
    }

    fun delete(id: Long): Job? = mutate {
        repository.delete(id)
        currentCoroutineContext().ensureActive()
        changed(id)
        if (_state.value.selectedId == id) jobs.cancel("detail")
        _state.update { it.copy(playlists = it.playlists.filterNot { p -> p.id == id },
            playlist = if (it.selectedId == id) null else it.playlist,
            detailLoading = if (it.selectedId == id) false else it.detailLoading,
            deletedId = id) }
        load()
    }

    fun consumeDeletion(id: Long) {
        _state.update { if (it.deletedId == id) it.copy(deletedId = null) else it }
    }

    fun add(id: Long, trackId: Long): Job? = mutate {
        repository.add(id, trackId)
        currentCoroutineContext().ensureActive()
        changed(id); refreshAfterWrite(id)
        onMessage(strings(R.string.app_view_model_23))
    }
    fun remove(id: Long, itemId: String): Job? = mutate {
        repository.remove(id, itemId)
        currentCoroutineContext().ensureActive()
        changed(id); refreshAfterWrite(id)
    }
    fun move(playlist: Playlist, index: Int, delta: Int): Job? {
        val next = index + delta
        if (index !in playlist.tracks.indices || next !in playlist.tracks.indices) return null
        return mutate {
            val ids = playlist.tracks.map { it.itemId }.toMutableList()
            ids.add(next, ids.removeAt(index))
            val saved = repository.reorder(playlist.id, ids)
            currentCoroutineContext().ensureActive()
            changed(playlist.id)
            if (_state.value.selectedId == playlist.id) _state.update { it.copy(playlist = saved) }
            load()
        }
    }

    private fun refreshAfterWrite(id: Long) {
        load()
        if (_state.value.selectedId == id) open(id, clear = false)
    }
    private fun changed(id: Long) { listRevision++; revisions[id] = (revisions[id] ?: 0) + 1 }

    private fun mutate(editor: Boolean = false, block: suspend () -> Unit): Job? {
        if (_state.value.busy) return null
        _state.update { it.copy(busy = true, editor = it.editor?.copy(error = null)) }
        return jobs.launchIfIdle("mutation") {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (current("mutation")) {
                    if (editor) _state.update { it.copy(editor = it.editor?.copy(error = message(e))) }
                    else onMessage(message(e))
                }
            } finally { if (current("mutation")) _state.update { it.copy(busy = false) } }
        }
    }

    fun reset() {
        jobs.cancelAll(); listRevision++; revisions.clear()
        _state.value = PlaylistState()
    }
}
