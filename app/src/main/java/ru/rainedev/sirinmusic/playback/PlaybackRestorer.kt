package ru.rainedev.sirinmusic.playback

import kotlinx.coroutines.*
import ru.rainedev.sirinmusic.TaskJobs
import ru.rainedev.sirinmusic.data.*

internal data class RestoredPlayback(val saved: PlaybackState, val session: RadioStart, val track: Track)

/** Idle restore never claims playback; local actions cancel it before changing position or source. */
internal class PlaybackRestorer(
    scope: CoroutineScope,
    private val readState: suspend () -> PlaybackState?,
    private val readSession: suspend (String) -> RadioStart,
    private val canRestore: () -> Boolean,
    private val onRestore: (RestoredPlayback) -> Unit,
    private val onUnavailable: () -> Unit,
    private val onError: (Exception) -> Unit,
) {
    private val jobs = TaskJobs(scope)
    private var running: Job? = null
    private suspend fun current() = jobs.isCurrent("restore", currentCoroutineContext()[Job])

    fun request(stillCurrent: () -> Boolean = { true }): Job? {
        running?.takeIf { it.isActive }?.let { return it }
        if (!canRestore() || !stillCurrent()) return null
        return jobs.launch("restore") {
            try {
                val saved = try { readState() }
                catch (e: ApiException) {
                    if (e.code != 404) throw e
                    if (current() && canRestore() && stillCurrent()) onUnavailable()
                    return@launch
                }
                currentCoroutineContext().ensureActive()
                if (!current() || !canRestore() || !stillCurrent() || saved == null || saved.trackId <= 0 || saved.sessionId.isBlank()) return@launch
                val session = readSession(saved.sessionId)
                currentCoroutineContext().ensureActive()
                if (!current() || !canRestore() || !stillCurrent()) return@launch
                val track = restorableTrack(saved, session) ?: return@launch
                onRestore(RestoredPlayback(saved, session, track))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current() && canRestore() && stillCurrent()) onError(e) }
        }.also { running = it }
    }

    fun cancel() { jobs.cancelAll(); running = null }
}
