package ru.rainedev.sirinmusic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Foreground requests share one fetch; failed/cancelled fetches never mark data as fresh. */
internal class ContentRefresh(
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val minIntervalMs: Long = 30_000,
    private val onLoading: (Boolean) -> Unit,
    private val onError: (Exception) -> Unit,
    private val fetch: suspend () -> Unit,
) {
    private var running: Job? = null
    private var lastSuccess: Long? = null

    fun request(force: Boolean = false): Job? {
        running?.takeIf { it.isActive }?.let { return it }
        if (!force && lastSuccess?.let { now() - it < minIntervalMs } == true) return null
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                fetch()
                currentCoroutineContext().ensureActive()
                lastSuccess = now()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
            }
        }
        running = job
        onLoading(true)
        job.invokeOnCompletion {
            if (running === job) {
                running = null
                onLoading(false)
            }
        }
        job.start()
        return job
    }

    fun isOlderThan(ageMs: Long): Boolean = lastSuccess?.let { now() - it >= ageMs } ?: true

    fun reset() {
        running?.cancel()
        running = null
        lastSuccess = null
        onLoading(false)
    }
}
