package ru.rainedev.sirinmusic

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import ru.rainedev.sirinmusic.data.*

class FavoritesControllerTest : LocalizedTest() {
    private class Repository : FavoritesRepository {
        var list: suspend () -> FavoritesReply = { FavoritesReply() }
        var recommend: suspend () -> FavoriteRecommendations = { FavoriteRecommendations() }
        var write: suspend () -> FavoriteReply = { FavoriteReply(true) }
        var writes = 0
        override suspend fun list() = list.invoke()
        override suspend fun recommendations() = recommend()
        override suspend fun toggleTrack(id: Long): FavoriteReply { writes++; return write() }
        override suspend fun toggleArtist(artist: String): FavoriteReply { writes++; return write() }
        override suspend fun toggleAlbum(artist: String, album: String): FavoriteReply { writes++; return write() }
    }
    private class Harness(scope: CoroutineScope) {
        val repo = Repository()
        val published = mutableListOf<Set<Long>>()
        val messages = mutableListOf<String>()
        val controller = FavoritesController(scope, repo, published::add, messages::add)
    }
    @Test fun normalizationAndPlayerFavoritesUseSameIds() = runBlocking {
        val h = Harness(this)
        h.repo.list = { FavoritesReply(tracks = listOf(FavoriteEntry(trackId = 7), FavoriteEntry(trackId = 7), FavoriteEntry(trackId = -2))) }
        h.controller.load().join()
        assertEquals(listOf(7L), h.controller.state.value.tracks.map { it.key })
        assertEquals(setOf(7L), h.published.last())
    }
    @Test fun confirmedToggleSurvivesRefreshFailureAndIsNotRepeated() = runBlocking {
        val h = Harness(this)
        h.repo.list = { error("Refresh failed") }
        h.controller.toggleTrack(Track(id = 7, title = "Song"))!!.join(); yield()
        assertEquals(listOf(7L), h.controller.state.value.tracks.map { it.key })
        assertEquals(setOf(7L), h.published.last())
        assertEquals(1, h.repo.writes)
        assertEquals(listOf("Refresh failed"), h.messages)
        assertTrue(h.controller.state.value.busy.isEmpty())
    }
    @Test fun duplicateTapIsIgnoredUntilWriteCompletesAndServerReplyDefinesState() = runBlocking {
        val h = Harness(this)
        val gate = CompletableDeferred<Unit>()
        h.repo.write = { gate.await(); FavoriteReply(false) }
        h.repo.list = { error("Offline") }
        val job = h.controller.toggleTrack(Track(id = 7))!!
        assertNull(h.controller.toggleTrack(Track(id = 7)))
        assertTrue("favorite:7" in h.controller.state.value.busy)
        gate.complete(Unit); job.join()
        assertEquals(1, h.repo.writes)
        assertTrue(h.controller.state.value.tracks.isEmpty())
    }
    @Test fun failedToggleKeepsExistingFavorite() = runBlocking {
        val h = Harness(this)
        h.repo.list = { FavoritesReply(tracks = listOf(FavoriteEntry(trackId = 7))) }
        h.controller.load().join()
        h.repo.write = { error("Write failed") }
        h.controller.toggleTrack(Track(id = 7))!!.join()
        assertEquals(setOf(7L), h.published.last())
        assertEquals(1, h.controller.state.value.tracks.size)
        assertEquals(listOf("Write failed"), h.messages)
    }
    @Test fun differentFavoritesWritesAreSerializedWithoutDroppingEitherResult() = runBlocking {
        val h = Harness(this)
        val gate = CompletableDeferred<Unit>()
        h.repo.list = { error("Offline") }
        h.repo.write = { if (h.repo.writes == 1) gate.await(); FavoriteReply(true) }
        val first = h.controller.toggleTrack(Track(id = 7))!!; yield()
        val second = h.controller.toggleTrack(Track(id = 8))!!; yield()
        assertEquals(1, h.repo.writes)
        gate.complete(Unit); first.join(); second.join()
        assertEquals(setOf(7L, 8L), h.published.last())
        assertTrue(h.controller.state.value.busy.isEmpty())
    }
    @Test fun artistAndAlbumRepliesSurviveFailedRefresh() = runBlocking {
        val h = Harness(this)
        h.repo.list = { error("Offline") }
        h.controller.toggleArtist(ArtistRow(artist = "Artist"))!!.join()
        h.controller.toggleAlbum(AlbumRow(artist = "Artist", album = "Album"))!!.join()
        assertEquals("Artist", h.controller.state.value.artists.single().artist)
        assertEquals("Album", h.controller.state.value.albums.single().album)
        h.repo.write = { FavoriteReply(false) }
        h.controller.toggleAlbum(AlbumRow(artist = "Artist", album = "Album"))!!.join()
        assertTrue(h.controller.state.value.albums.isEmpty())
        assertEquals(1, h.controller.state.value.artists.size)
    }
    @Test fun resetRejectsLateWriteAndClearsPlayerHearts() = runBlocking {
        val h = Harness(this)
        val gate = CompletableDeferred<Unit>()
        h.repo.write = { withContext(NonCancellable) { gate.await() }; FavoriteReply(true) }
        val job = h.controller.toggleTrack(Track(id = 7))!!; yield()
        h.controller.reset(); gate.complete(Unit); job.join()
        assertEquals(FavoritesState(), h.controller.state.value)
        assertEquals(emptySet<Long>(), h.published.last())
        assertTrue(h.messages.isEmpty())
    }
    @Test fun resetRejectsLateListAndRecommendationErrors() = runBlocking {
        val h = Harness(this)
        val gate = CompletableDeferred<Unit>()
        h.repo.list = { withContext(NonCancellable) { gate.await() }; error("Old list") }
        h.repo.recommend = { withContext(NonCancellable) { gate.await() }; throw ApiException(500, "Old recommendations") }
        val list = h.controller.load(); val rec = h.controller.loadRecommendations(); yield()
        h.controller.reset(); gate.complete(Unit); list.join(); rec.join()
        assertEquals(FavoritesState(), h.controller.state.value)
        assertTrue(h.messages.isEmpty())
    }
    @Test fun unsupportedRecommendationsClearOldResultsWithoutError() = runBlocking {
        val h = Harness(this)
        h.repo.recommend = { FavoriteRecommendations(tracks = listOf(Track(id = 7))) }
        h.controller.loadRecommendations().join()
        assertNotNull(h.controller.state.value.recommendations)
        h.repo.recommend = { throw ApiException(404, "Unsupported") }
        h.controller.loadRecommendations().join()
        assertNull(h.controller.state.value.recommendations)
        assertNull(h.controller.state.value.recommendationsError)
        assertFalse(h.controller.state.value.recommendationsLoading)
    }
    @Test fun replacedRecommendationsCannotOverwriteLatestResultOrLoading() = runBlocking {
        val h = Harness(this)
        val oldGate = CompletableDeferred<Unit>(); val newGate = CompletableDeferred<Unit>()
        h.repo.recommend = { withContext(NonCancellable) { oldGate.await() }; error("Old failure") }
        val old = h.controller.loadRecommendations(); yield()
        h.repo.recommend = { newGate.await(); FavoriteRecommendations(hint = "New") }
        val next = h.controller.loadRecommendations(); yield()
        oldGate.complete(Unit); old.join()
        assertTrue(h.controller.state.value.recommendationsLoading)
        assertNull(h.controller.state.value.recommendationsError)
        newGate.complete(Unit); next.join()
        assertEquals("New", h.controller.state.value.recommendations?.hint)
    }
}
