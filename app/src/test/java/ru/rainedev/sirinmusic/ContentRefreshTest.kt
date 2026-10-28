package ru.rainedev.sirinmusic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ContentRefreshTest {
    @Test fun newServerMusicAppearsWithoutRecreatingTheClient() = runBlocking {
        var now = 0L
        var server = listOf(1)
        var shown = emptyList<Int>()
        var requests = 0
        val refresh = ContentRefresh(this, { now }, onLoading = {}, onError = { throw it }) {
            requests++
            shown = server.toList()
        }
        refresh.request()!!.join()
        server = listOf(1, 2)
        now = 1_000
        assertNull(refresh.request()) // Navigation/rotation does not download the catalogue again.
        assertEquals(listOf(1), shown)
        now = 30_000
        refresh.request()!!.join() // Returning to the same live client updates its existing state.
        assertEquals(listOf(1, 2), shown)
        assertEquals(2, requests)
    }

    @Test fun swipeBypassesFreshnessButSharesAnInFlightRequest() = runBlocking {
        val release = CompletableDeferred<Unit>()
        var requests = 0
        val loading = mutableListOf<Boolean>()
        val refresh = ContentRefresh(this, { 0L }, onLoading = loading::add, onError = { throw it }) {
            requests++
            if (requests == 1) release.await()
        }
        val first = refresh.request()!!
        yield()
        assertSame(first, refresh.request(force = true))
        release.complete(Unit)
        first.join()
        refresh.request(force = true)!!.join()
        assertEquals(2, requests)
        assertEquals(listOf(true, false, true, false), loading)
    }

    @Test fun networkFailureRetainsOldContentAndAllowsRetry() = runBlocking {
        var fails = true
        var shown = listOf(1)
        val errors = mutableListOf<Exception>()
        val refresh = ContentRefresh(this, { 0L }, onLoading = {}, onError = errors::add) {
            if (fails) error("offline")
            shown = listOf(1, 2)
        }
        refresh.request()!!.join()
        assertEquals(listOf(1), shown)
        assertEquals(1, errors.size)
        fails = false
        refresh.request()!!.join()
        assertEquals(listOf(1, 2), shown)
    }

    @Test fun switchingAccountsCancelsOldWorkAndDoesNotClearNewLoadingState() = runBlocking {
        val oldServer = CompletableDeferred<Unit>()
        val newServer = CompletableDeferred<Unit>()
        var account = 1
        var shown = 0
        var loading = false
        val refresh = ContentRefresh(this, { 0L }, onLoading = { loading = it }, onError = { throw it }) {
            val snapshot = account
            if (snapshot == 1) oldServer.await() else newServer.await()
            shown = snapshot
        }
        val old = refresh.request()!!
        yield()
        refresh.reset()
        account = 2
        val current = refresh.request()!!
        yield()
        old.join()
        assertTrue(old.isCancelled)
        assertTrue(loading)
        assertEquals(0, shown)
        newServer.complete(Unit)
        current.join()
        assertFalse(loading)
        assertEquals(2, shown)
    }

    @Test fun lateFailureFromOldAccountCannotShowErrorOrClearNewLoading() = runBlocking {
        val oldGate = CompletableDeferred<Unit>()
        val newGate = CompletableDeferred<Unit>()
        var oldAccount = true
        var loading = false
        val errors = mutableListOf<Exception>()
        val refresh = ContentRefresh(this, { 0L }, onLoading = { loading = it }, onError = errors::add) {
            if (oldAccount) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { oldGate.await() }
                error("Old server failed")
            } else newGate.await()
        }
        val old = refresh.request()!!; yield()
        refresh.reset(); oldAccount = false
        val next = refresh.request()!!; yield()
        oldGate.complete(Unit); old.join()
        assertTrue(errors.isEmpty())
        assertTrue(loading)
        newGate.complete(Unit); next.join()
        assertFalse(loading)
    }

    @Test fun resetFinishesLoadingExactlyOnceEvenWithImmediateCancellation() = runBlocking {
        val loading = mutableListOf<Boolean>()
        val refresh = ContentRefresh(this, { 0L }, onLoading = loading::add, onError = { throw it }) {
            kotlinx.coroutines.awaitCancellation()
        }
        val job = refresh.request()!!; yield()
        refresh.reset(); job.join()
        assertEquals(listOf(true, false), loading)
    }

    @Test fun successfulDataEventuallyExpiresEvenWhenTrackCountIsUnchanged() = runBlocking {
        var now = 0L
        val refresh = ContentRefresh(this, { now }, onLoading = {}, onError = { throw it }) {}
        assertTrue(refresh.isOlderThan(300_000))
        refresh.request()!!.join()
        now = 299_999
        assertFalse(refresh.isOlderThan(300_000))
        now = 300_000
        assertTrue(refresh.isOlderThan(300_000))
    }
}
