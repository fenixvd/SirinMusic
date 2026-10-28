package ru.rainedev.sirinmusic

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import ru.rainedev.sirinmusic.data.Playlist
import ru.rainedev.sirinmusic.data.PlaylistItem
import ru.rainedev.sirinmusic.data.PlaylistRepository

class PlaylistControllerTest : LocalizedTest() {
    private class Repository : PlaylistRepository {
        var rows = listOf(Playlist(id = 1, name = "Original"))
        var list: suspend () -> List<Playlist> = { rows }
        var get: suspend (Long) -> Playlist = { id -> rows.first { it.id == id } }
        var beforeWrite: suspend () -> Unit = {}
        var creates = 0
        var writes = 0
        var reordered = emptyList<String>()
        override suspend fun list() = list.invoke()
        override suspend fun get(id: Long) = get.invoke(id)
        override suspend fun create(name: String, description: String): Playlist {
            creates++; beforeWrite(); writes++
            return Playlist(id = 2, name = name, description = description).also { rows = rows + it }
        }
        override suspend fun edit(playlist: Playlist, name: String, description: String): Playlist {
            beforeWrite(); writes++
            return playlist.copy(name = name, description = description).also { saved -> rows = rows.map { if (it.id == saved.id) saved else it } }
        }
        override suspend fun delete(id: Long) { beforeWrite(); writes++; rows = rows.filterNot { it.id == id } }
        override suspend fun add(id: Long, trackId: Long) { beforeWrite(); writes++ }
        override suspend fun remove(id: Long, itemId: String) { beforeWrite(); writes++ }
        override suspend fun reorder(id: Long, itemIds: List<String>): Playlist {
            beforeWrite(); writes++; reordered = itemIds
            return rows.first { it.id == id }
        }
    }

    @Test fun failedCreationKeepsDraftAndRetryClosesIt() = runBlocking {
        val repo = Repository()
        val controller = PlaylistController(this, repo) { fail(it) }
        controller.edit(); controller.changeName("  Road  "); controller.changeDescription("  For driving  ")
        repo.beforeWrite = { error("Offline") }
        controller.save()!!.join()
        assertEquals("  Road  ", controller.state.value.editor?.name)
        assertEquals("  For driving  ", controller.state.value.editor?.description)
        assertEquals("Offline", controller.state.value.editor?.error)
        assertFalse(controller.state.value.busy)
        repo.beforeWrite = {}
        controller.save()!!.join()
        assertNull(controller.state.value.editor)
        assertEquals("Road", repo.rows.last().name)
        assertEquals("For driving", repo.rows.last().description)
        assertEquals(1, repo.writes)
    }

    @Test fun failedEditKeepsOriginalAndDraft() = runBlocking {
        val repo = Repository()
        val controller = PlaylistController(this, repo) { fail(it) }
        controller.open(1).join(); controller.edit(controller.state.value.playlist)
        controller.changeName("Renamed")
        repo.beforeWrite = { error("Permission denied") }
        controller.save()!!.join()
        assertEquals("Original", controller.state.value.playlist?.name)
        assertEquals("Renamed", controller.state.value.editor?.name)
        assertEquals(1L, controller.state.value.editor?.original?.id)
        repo.beforeWrite = {}; controller.save()!!.join()
        assertEquals("Renamed", controller.state.value.playlist?.name)
    }

    @Test fun failedRefreshAfterConfirmedCreationDoesNotOfferDuplicateCreation() = runBlocking {
        val repo = Repository()
        val messages = mutableListOf<String>()
        val controller = PlaylistController(this, repo, messages::add)
        repo.list = { error("Refresh failed") }
        controller.edit(); controller.changeName("New")
        controller.save()!!.join(); yield()
        assertNull(controller.state.value.editor)
        assertNull(controller.save())
        assertEquals(1, repo.creates)
        assertEquals("New", controller.state.value.playlists.single().name)
        assertEquals(listOf("Refresh failed"), messages)
    }

    @Test fun savingFreezesDraftAndPreventsDoubleSubmission() = runBlocking {
        val repo = Repository()
        val gate = CompletableDeferred<Unit>()
        repo.beforeWrite = { gate.await() }
        val controller = PlaylistController(this, repo) { fail(it) }
        controller.edit(); controller.changeName("Keep")
        val job = controller.save()!!
        assertTrue(controller.state.value.busy)
        assertNull(controller.save())
        controller.changeName("Lost"); controller.changeDescription("Lost"); controller.dismissEditor(); controller.edit()
        assertEquals("Keep", controller.state.value.editor?.name)
        assertEquals("", controller.state.value.editor?.description)
        gate.complete(Unit); job.join()
        assertEquals(1, repo.creates)
    }

    @Test fun blankNameDoesNotWrite() = runBlocking {
        val repo = Repository()
        val controller = PlaylistController(this, repo) { fail(it) }
        controller.edit(); controller.changeName("   ")
        assertNull(controller.save())
        assertNotNull(controller.state.value.editor?.error)
        assertEquals(0, repo.creates)
    }

    @Test fun deletionIsReportedOnlyAfterSuccessfulWriteEvenWhenRefreshFails() = runBlocking {
        val repo = Repository()
        val messages = mutableListOf<String>()
        val controller = PlaylistController(this, repo, messages::add)
        controller.load().join(); controller.open(1).join()
        repo.beforeWrite = { error("Cannot delete") }
        controller.delete(1)!!.join()
        assertNotNull(controller.state.value.playlist)
        assertEquals(1, controller.state.value.playlists.size)
        assertNull(controller.state.value.deletedId)
        repo.beforeWrite = {}; repo.list = { error("Cannot refresh") }
        controller.delete(1)!!.join(); yield()
        assertNull(controller.state.value.playlist)
        assertTrue(controller.state.value.playlists.isEmpty())
        assertEquals(1L, controller.state.value.deletedId)
        controller.consumeDeletion(2)
        assertEquals(1L, controller.state.value.deletedId)
        controller.consumeDeletion(1)
        assertNull(controller.state.value.deletedId)
        assertEquals(listOf("Cannot delete", "Cannot refresh"), messages)
    }

    @Test fun failedDetailLoadDoesNotReportDeletionAndCanBeRetried() = runBlocking {
        val repo = Repository()
        val messages = mutableListOf<String>()
        val controller = PlaylistController(this, repo, messages::add)
        repo.get = { error("Unavailable") }
        controller.open(1).join()
        assertNull(controller.state.value.playlist)
        assertFalse(controller.state.value.detailLoading)
        assertNull(controller.state.value.deletedId)
        repo.get = { repo.rows.single() }
        controller.open(1).join()
        assertEquals(1L, controller.state.value.playlist?.id)
    }

    @Test fun resetRejectsCancelledWriteAndItsFinallyCannotUnlockNextWrite() = runBlocking {
        val repo = Repository()
        val gate = CompletableDeferred<Unit>()
        repo.beforeWrite = { withContext(NonCancellable) { gate.await() } }
        val controller = PlaylistController(this, repo) { fail(it) }
        controller.edit(); controller.changeName("Old server")
        val old = controller.save()!!; yield()
        controller.reset()
        controller.edit(); controller.changeName("New server")
        val nextGate = CompletableDeferred<Unit>()
        repo.beforeWrite = { nextGate.await() }
        val next = controller.save()!!; yield()
        gate.complete(Unit); old.join()
        assertTrue(controller.state.value.busy)
        assertEquals("New server", controller.state.value.editor?.name)
        nextGate.complete(Unit); next.join()
        assertFalse(controller.state.value.busy)
        assertNull(controller.state.value.editor)
    }

    @Test fun replacedDetailCannotOverwriteAnotherPlaylist() = runBlocking {
        val repo = Repository()
        val gate = CompletableDeferred<Unit>()
        val controller = PlaylistController(this, repo) { fail(it) }
        repo.get = { id ->
            if (id == 1L) withContext(NonCancellable) { gate.await() }
            Playlist(id = id, name = "List $id")
        }
        val old = controller.open(1); yield()
        controller.open(2).join()
        gate.complete(Unit); old.join()
        assertEquals(2L, controller.state.value.playlist?.id)
        assertFalse(controller.state.value.detailLoading)
    }

    @Test fun listReadStartedBeforeWriteCannotResurrectDeletedPlaylist() = runBlocking {
        val repo = Repository()
        val gate = CompletableDeferred<Unit>()
        val oldRows = repo.rows
        repo.list = { withContext(NonCancellable) { gate.await() }; oldRows }
        val controller = PlaylistController(this, repo) { fail(it) }
        val old = controller.load(); yield()
        repo.list = { repo.rows }
        controller.delete(1)!!.join()
        gate.complete(Unit); old.join(); yield()
        assertTrue(controller.state.value.playlists.isEmpty())
    }

    @Test fun detailReadStartedBeforeEditCannotReplaceConfirmedChanges() = runBlocking {
        val repo = Repository()
        val controller = PlaylistController(this, repo) { fail(it) }
        controller.load().join(); controller.open(1).join()
        val original = repo.rows.single()
        val gate = CompletableDeferred<Unit>()
        repo.get = { gate.await(); original }
        val oldRead = controller.open(1, clear = false); yield()
        controller.edit(original); controller.changeName("Updated")
        controller.save()!!.join()
        gate.complete(Unit); oldRead.join()
        assertEquals("Updated", controller.state.value.playlist?.name)
    }

    @Test fun reorderUsesItemIdentityForDuplicateTracksAndChecksBothBounds() = runBlocking {
        val repo = Repository()
        val p = Playlist(id = 1, tracks = listOf(PlaylistItem("first", 7), PlaylistItem("second", 7)))
        repo.rows = listOf(p)
        val controller = PlaylistController(this, repo) { fail(it) }
        assertNull(controller.move(p, -1, 1))
        assertNull(controller.move(p, 2, -1))
        assertNull(controller.move(p, 0, -1))
        controller.move(p, 0, 1)!!.join()
        assertEquals(listOf("second", "first"), repo.reordered)
        assertEquals(1, repo.writes)
    }
}
