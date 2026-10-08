package ru.rainedev.sirinmusic.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MusikApiTest {
    private lateinit var server: HttpServer
    private lateinit var api: MusikApi
    private var body = "{}"
    private var status = 200
    private val requests = LinkedBlockingQueue<Triple<String, String, String>>()
    private val authorization = LinkedBlockingQueue<String>()
    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.add(Triple(exchange.requestMethod, exchange.requestURI.toString(), exchange.requestBody.bufferedReader().readText()))
            authorization.add(exchange.requestHeaders.getFirst("Authorization").orEmpty())
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        api = MusikApi(ServerConnection("http://127.0.0.1:${server.address.port}", "fixture-token", "fixture-client"))
    }
    @After fun stop() { server.stop(0); api.client.connectionPool.evictAll(); api.client.dispatcher.executorService.shutdown() }

    @Test fun nestedFavoritesAndUnknownFields() = runBlocking {
        body = """{"tracks":[{"track_id":42,"track":{"id":42,"title":"Song","artist":"Artist","album":"Album"}}],"artists":[{"artist":"Artist","tracks":3}],"albums":[{"artist":"Artist","album":"Album","tracks":2}],"counts":{"tracks":1}}"""
        val r = api.favorites()
        assertEquals(42L, r.tracks.single().asTrack().key)
        assertEquals("Album", r.tracks.single().asTrack().album)
        assertEquals("Artist", r.artists.single().artist)
        assertEquals("Album", r.albums.single().album)
        assertEquals("Bearer fixture-token", authorization.take())
    }
    @Test fun flatFavoritesRemainCompatible() = runBlocking {
        body = """[{"track_id":7,"title":"Legacy"}]"""
        assertEquals(7L, api.favorites().tracks.single().asTrack().key)
    }
    @Test fun previousUsesServerHistoryAndCurrentTrack() = runBlocking {
        body = """{"session_id":"s","current":{"id":42},"queue":[{"track_id":43}],"fixed":false}"""
        val reply = api.previous("s")
        val request = requests.take()
        assertEquals("POST", request.first)
        assertEquals("/api/session/back", request.second)
        assertEquals("s", Json.parseToJsonElement(request.third).jsonObject.getValue("session_id").jsonPrimitive.content)
        assertEquals(42L, reply.current?.key)
        assertEquals(43L, reply.queue.single().key)
    }
    @Test fun libraryNeverSilentlyTruncatesAtServerLimit() = runBlocking {
        body = "[]"; api.library(); assertEquals("/api/library", requests.take().second)
        body = "{}"; api.artists(); assertEquals("/api/artists", requests.take().second)
        api.albums(); assertEquals("/api/albums", requests.take().second)
    }
    @Test fun playlistUsesDetailResourceAndKeepsRepeatedTracks() = runBlocking {
        body = """{"id":9,"name":"Mix","tracks":[{"item_id":"a","track_id":42},{"item_id":"b","track_id":42},{"item_id":"missing","unresolved":true,"unresolved_title":"Missing"}]}"""
        val p = api.playlist(9)
        assertEquals("/api/playlists/9", requests.take().second)
        assertEquals(listOf("a", "b", "missing"), p.tracks.map { it.itemId })
        assertFalse(p.tracks.last().asTrack().ready)
        assertEquals("Missing", p.tracks.last().asTrack().title)
    }
    @Test fun playlistMutationVerbsAndEscaping() = runBlocking {
        api.createPlaylist("Quotes \" and /", "line\nbreak")
        val create = requests.take(); assertEquals("POST", create.first)
        assertEquals("Quotes \" and /", Json.parseToJsonElement(create.third).jsonObject["name"]!!.jsonPrimitive.content)
        api.editPlaylist(Playlist(id = 9, allowDuplicates = true), "New", "Description")
        val edit = requests.take(); assertEquals("PATCH", edit.first)
        assertEquals("true", Json.parseToJsonElement(edit.third).jsonObject["allow_duplicates"]!!.jsonPrimitive.content)
        api.addPlaylistTrack(9, 42); assertEquals("/api/playlists/9/tracks", requests.take().second)
        api.removePlaylistTrack(9, "item/id")
        assertEquals(Triple("DELETE", "/api/playlists/9/tracks/item%2Fid", ""), requests.take())
        api.deletePlaylist(9); assertEquals(Triple("DELETE", "/api/playlists/9?hard=1", ""), requests.take())
    }
    @Test fun playlistPlaybackAndJumpDecodeCurrent() = runBlocking {
        body = """{"session_id":"s","fixed":true,"mode":"playlist","current":{"id":42},"tracks":[{"id":42}],"queue":null}"""
        val start = api.playPlaylist(9, 2)
        assertTrue(start.fixed); assertEquals(42L, start.current!!.key)
        assertEquals("2", Json.parseToJsonElement(requests.take().third).jsonObject["start_index"]!!.jsonPrimitive.content)
        assertEquals(42L, api.jump("s", 42).current!!.key)
    }
    @Test fun eventHasSessionAndRatingDoesNotInventNextTrack() = runBlocking {
        body = """{"rating":"dislike","session_id":"s","current":42,"queue":[]}"""
        val r = api.event("dislike", 42, "s", "impression", 12.0, 100.0, 8.0)
        assertEquals("dislike", r.rating); assertNull(r.next)
        val payload = Json.parseToJsonElement(requests.take().third).jsonObject
        assertEquals("s", payload["session_id"]!!.jsonPrimitive.content)
        assertEquals("fixture-client", payload["client_id"]!!.jsonPrimitive.content)
        assertEquals("impression", payload["impression_id"]!!.jsonPrimitive.content)
    }
    @Test fun absentAndInstrumentalLyrics() = runBlocking {
        body = """{"track_id":42,"status":"absent","plain_lyrics":"","synced_lyrics":""}"""
        assertEquals("absent", api.lyrics(42).status)
        body = """{"track_id":42,"instrumental":true,"status":"found"}"""
        assertTrue(api.lyrics(42).instrumental)
    }
    @Test fun otherServersNeverReceiveTheToken() {
        val other = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var auth: String? = "not called"
        other.createContext("/") { e -> auth = e.requestHeaders.getFirst("Authorization"); e.sendResponseHeaders(200, -1); e.close() }
        other.start()
        val external = "http://127.0.0.1:${other.address.port}/art"
        try {
            api.client.newCall(Request.Builder().url(external).header("Authorization", "unsafe").build()).execute().close()
            assertNull(auth)
            try { api.url(external); fail("foreign URL must be rejected") } catch (_: IllegalArgumentException) { }
        } finally { other.stop(0) }
    }
    @Test fun unauthorizedResponseIsAnError() = runBlocking {
        status = 401
        try { api.health(); fail("401 must fail") } catch (e: ApiException) { assertEquals(401, e.code) }
    }
    @Test fun connectionValidation() {
        assertNull(validateServerUrl("https://music.example.org"))
        assertNull(validateServerUrl("http://192.168.1.2:8787"))
        assertNotNull(validateServerUrl("music.example.org"))
        assertNotNull(validateServerUrl("https://user:password@music.example.org"))
        assertNotNull(validateServerUrl("https://music.example.org?token=secret"))
    }
    @Test fun liveProfileContractUsesPositiveAndNegativeCounts() = runBlocking {
        body = """{"maturity":"ready","n_positive":119,"n_negative":302,"explore_lo":0.1,"explore_hi":0.4,"explore_ratio":0.15,"top_artists":[{"artist":"Linkin Park","count":24}],"source":"online_ema"}"""
        val p = api.profile()
        assertEquals(119, p.likes); assertEquals(302, p.skips)
        assertEquals(0.15, p.exploreRatio!!, 0.0001)
        assertEquals("Linkin Park", p.topArtists.single().artist)
        assertNull(p.plays)
    }
    @Test fun weeklyMetricsDoNotInventMissingValues() = runBlocking {
        body = """{"window_days":7,"overall":{"played":387,"unique_artists":175,"finish_rate":0.18,"early_skip_rate":0.67},"breakdowns":[{"dimension":"source","value":"exploit","played":359}]}"""
        val metrics = api.weeklyMetrics()
        assertEquals(7, metrics.windowDays)
        assertEquals(175, metrics.overall!!.uniqueArtists)
        assertEquals(0.67, metrics.overall!!.skipRate!!, 0.0001)
        assertNull(metrics.breakdowns.single().finishRate)
    }
    @Test fun exploreUsesPutAndRejectsReversedBoundsBeforeRequest() = runBlocking {
        api.saveExplore(0.1, 0.4)
        val request = requests.take()
        assertEquals("PUT", request.first)
        assertEquals("/api/profile/explore", request.second)
        assertEquals("0.1", Json.parseToJsonElement(request.third).jsonObject["explore_lo"]!!.jsonPrimitive.content)
        try { api.saveExplore(0.8, 0.1); fail("reversed bounds accepted") } catch (_: IllegalArgumentException) { }
        try { api.saveExplore(Double.NaN, 0.4); fail("NaN accepted") } catch (_: IllegalArgumentException) { }
        assertTrue(requests.isEmpty())
    }
    @Test fun contextsPreserveOtherActiveIdsAndEscapePath() = runBlocking {
        body = """{"ok":true,"context_ids":["old","new/id"]}"""
        val reply = api.activateContext("new/id", "session", true)
        assertEquals(listOf("old", "new/id"), reply.contextIds)
        val request = requests.take()
        assertEquals("/api/contexts/new%2Fid/activate", request.second)
        assertEquals("session", Json.parseToJsonElement(request.third).jsonObject["session_id"]!!.jsonPrimitive.content)
    }
    @Test fun mixJobAndRadioShareUseIndependentServerResources() = runBlocking {
        body = """{"id":6,"status":"pending"}"""
        assertEquals(6L, api.refreshMixes().id)
        assertEquals("/api/jobs/mix_pack", requests.take().second)
        body = """{"id":6,"status":"done","result":{"count":11}}"""
        assertEquals("done", api.mixJob(6).status)
        assertEquals("/api/jobs/6", requests.take().second)
        body = """{"token":"public-token","url":"https://example.org/listen/public-token.mp3","name":"Sirin Music"}"""
        val share = api.createShare()
        assertEquals("public-token", share.token)
        assertFalse(share.url.contains("fixture-token"))
        assertEquals("/api/share/radio", requests.take().second)
    }

}
