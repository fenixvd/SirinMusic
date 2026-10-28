package ru.rainedev.sirinmusic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CancellationException
import ru.rainedev.sirinmusic.TaskJobs

/** One screen operation at a time, cancelled with the screen's composition. */
@Composable
internal fun rememberScreenAction(
    onStart: () -> Unit,
    onError: (Exception) -> Unit,
    onFinish: () -> Unit,
): (suspend () -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    val jobs = remember(scope) { TaskJobs(scope) }
    val start by rememberUpdatedState(onStart)
    val error by rememberUpdatedState(onError)
    val finish by rememberUpdatedState(onFinish)
    return remember(jobs) {
        { block ->
            jobs.launchIfIdle("screen-action") {
                try { start(); block() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error(e) }
                finally { finish() }
            }
            Unit
        }
    }
}
