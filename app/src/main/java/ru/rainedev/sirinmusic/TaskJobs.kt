package ru.rainedev.sirinmusic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Вызывается из потока ViewModel; завершённые задачи не остаются в реестре. */
internal class TaskJobs(private val scope: CoroutineScope) {
    private val jobs = mutableMapOf<String, Job>()
    val size: Int get() = jobs.size
    fun isCurrent(key: String, job: Job?): Boolean = job != null && jobs[key] === job

    fun launchIfIdle(key: String, block: suspend CoroutineScope.() -> Unit): Job =
        jobs[key]?.takeIf { it.isActive } ?: launch(key, block)

    fun launch(key: String, block: suspend CoroutineScope.() -> Unit): Job {
        jobs.remove(key)?.cancel()
        val job = scope.launch(start = CoroutineStart.LAZY, block = block)
        jobs[key] = job
        job.invokeOnCompletion { if (jobs[key] === job) jobs.remove(key) }
        job.start()
        return job
    }

    fun cancel(key: String) { jobs.remove(key)?.cancel() }
    fun cancelAll() {
        val pending = jobs.values.toList()
        jobs.clear()
        pending.forEach { it.cancel() }
    }
}
