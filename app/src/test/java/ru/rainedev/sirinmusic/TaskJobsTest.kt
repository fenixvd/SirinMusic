package ru.rainedev.sirinmusic

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TaskJobsTest {
    @Test fun singleFlightPreventsDoubleSubmissionBeforeTheCoroutineStarts() = runBlocking {
        val jobs = TaskJobs(this)
        var calls = 0
        val first = jobs.launchIfIdle("save") { calls++; awaitCancellation() }
        val second = jobs.launchIfIdle("save") { calls++ }
        assertSame(first, second)
        kotlinx.coroutines.yield()
        assertEquals(1, calls)
        jobs.cancelAll()
        first.join()
        jobs.launchIfIdle("save") { calls++ }.join()
        assertEquals(2, calls)
    }

    @Test fun cancellationRevokesOwnershipBeforeOldFinallyRuns() = runBlocking {
        val jobs = TaskJobs(this)
        val ownership = mutableListOf<Boolean>()
        val old = jobs.launch("load") {
            try { awaitCancellation() }
            finally { ownership.add(jobs.isCurrent("load", kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job])) }
        }
        kotlinx.coroutines.yield()
        val next = jobs.launch("load") { awaitCancellation() }
        old.join()
        assertEquals(listOf(false), ownership)
        jobs.cancel("load")
        assertFalse(jobs.isCurrent("load", next))
        next.join()
    }

    @Test fun completedTasksDoNotAccumulate() = runBlocking {
        val jobs = TaskJobs(this)
        repeat(1000) { jobs.launch("favorite:$it") {}.join() }
        assertEquals(0, jobs.size)
    }
    @Test fun replacingATaskCancelsOnlyTheOldTask() = runBlocking {
        val jobs = TaskJobs(this)
        val old = jobs.launch("library") { awaitCancellation() }
        assertTrue(jobs.isCurrent("library", old))
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val next = jobs.launch("library") { release.await() }
        assertFalse(jobs.isCurrent("library", old))
        assertTrue(jobs.isCurrent("library", next))
        release.complete(Unit)
        next.join(); old.join()
        assertTrue(old.isCancelled)
        assertFalse(next.isCancelled)
        assertEquals(0, jobs.size)
    }
    @Test fun cancelAllCanRemoveTasksDuringCompletion() = runBlocking {
        val jobs = TaskJobs(this)
        val tasks = List(20) { jobs.launch("task:$it") { awaitCancellation() } }
        jobs.cancelAll()
        tasks.forEach { it.join(); assertTrue(it.isCancelled) }
        assertEquals(0, jobs.size)
    }
}
