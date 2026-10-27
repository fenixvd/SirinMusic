package ru.rainedev.sirinmusic.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MusikApiTest : ru.rainedev.sirinmusic.LocalizedTest() {
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

    @Test fun refreshingLibraryFetchesNewServerTracksWithTheSameApiInstance() = runBlocking {
        body = """[{"id":1,"title":"Existing"}]"""
        assertEquals(listOf(1L), api.library().map { it.key })
        body = """[{"id":1,"title":"Existing"},{"id":2,"title":"New upload"}]"""
        assertEquals(listOf(1L, 2L), api.library().map { it.key })
        repeat(2) {
            assertEquals("/api/library", requests.take().second)
            assertEquals("Bearer fixture-token", authorization.take())
        }
    }

    @Test fun trackPassportUsesAuthenticatedDetailRouteAndDecodesMeasuredAndProfileData() = runBlocking {
        body = """{"id":42,"passport":{"format":"MP3","bitrate":320000,"sample_rate":44100,"channels":2,"lufs":-11.3,"bpm":86.0,"key":"D","mode":"minor","year":2009,"duration":190,"genres":["Rock","Alternative"],"plays":2,"early_skips":1,"last_played_at":"2026-10-18T10:00:00Z"}}"""
        val detail = api.trackDetails(42)
        assertEquals(42L, detail.key)
        assertEquals(44100L, detail.passport!!.sampleRate)
        assertEquals(2, detail.passport!!.plays)
        assertEquals(listOf("Rock", "Alternative"), detail.passport!!.genres)
        val request = requests.take()
        assertEquals("GET", request.first)
        assertEquals("/api/tracks/42", request.second)
        assertEquals("Bearer fixture-token", authorization.take())
    }

    @Test fun missingOrPartialPassportDoesNotInventMeasurements() = runBlocking {
        body = """{"id":42}"""
        assertNull(api.trackDetails(42).passport)
        body = """{"id":42,"passport":{"format":"FLAC"}}"""
        val p = api.trackDetails(42).passport!!
        assertEquals("FLAC", p.format)
        assertNull(p.bitrate)
        assertNull(p.plays)
    }

    @Test fun closeEventRetryUsesExplicitIdAndReasonAndDecodesIgnoredReply() = runBlocking {
        body = """{"ignored":true,"reason":"already_closed","next":{"id":42,"impression_id":"same"},"queue":[]}"""
        repeat(2) {
            val reply = api.event("track_end", 42, "s", "same", 120.0, 120.0, 115.0,
                eventId = "close-once", reason = "completed")
            assertTrue(reply.ignored)
            assertEquals("already_closed", reply.reason)
            assertEquals("same", reply.next!!.impressionId)
            val payload = Json.parseToJsonElement(requests.take().third).jsonObject
            assertEquals("close-once", payload.getValue("event_id").jsonPrimitive.content)
            assertEquals("completed", payload.getValue("reason").jsonPrimitive.content)
        }
    }
    @Test fun legacyEventResponseDoesNotNeedTheNewFields() = runBlocking {
        body = """{"next":{"id":43},"fixed":false}"""
        val reply = api.event("skip", 42, "s", "i", 1.0, 120.0, 1.0)
        assertFalse(reply.ignored); assertNull(reply.reason)
        assertEquals(43L, reply.next!!.key)
    }
    @Test fun favoriteRecommendationsDecodeAllThreeKindsAndUnknownRankerFields() = runBlocking {
        body = """{"ok":true,"tracks":[{"id":42,"title":"Recommended","source":"likes"}],"artists":[{"artist":"Artist","cover_track_id":7}],"albums":[{"artist":"Artist","album":"Album"}],"based_on":{"artists":["Liked artist"]},"explanation":"по звучанию и лайкам"}"""
        val result = api.favoriteRecommendations()
        assertEquals("/api/recommend/favorites", requests.take().second)
        assertEquals(42L, result.tracks.single().key)
        assertEquals("Artist", result.artists.single().artist)
        assertEquals("Album", result.albums.single().album)
        assertEquals("по звучанию и лайкам", result.explanation)
    }
    @Test fun emptyFavoriteRecommendationsRetainTheServerHint() = runBlocking {
        body = """{"ok":false,"empty":true,"tracks":[],"artists":[],"albums":[],"hint":"Добавь любимую музыку"}"""
        val result = api.favoriteRecommendations()
        assertTrue(result.empty); assertTrue(result.tracks.isEmpty())
        assertEquals("Добавь любимую музыку", result.hint)
    }
    @Test fun compatibleStreamKeepsTheAuthenticatedOriginAndReplacesQuality() {
        val url = api.compatibleStreamUrl(Track(id = 42, stream = "/api/stream/42?q=mobile&other=1")).toHttpUrl()
        assertEquals("/api/stream/42", url.encodedPath)
        assertEquals(listOf("mp3"), url.queryParameterValues("q"))
        assertEquals("1", url.queryParameter("other"))
        assertThrows(IllegalArgumentException::class.java) {
            api.compatibleStreamUrl(Track(id = 42, stream = "https://other.example/api/stream/42"))
        }
    }

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
    @Test fun favoriteEntryRetainsOuterIdWhenNestedMetadataHasNoId() = runBlocking {
        body = """{"tracks":[{"track_id":42,"track":{"title":"Song","ready":true}}]}"""
        assertEquals(42L, api.favorites().tracks.single().asTrack().key)
    }

    @Test fun favoriteStartsTappedTrackInFullQueueAndNextUsesThatSession() = runBlocking {
        body = """{"session_id":"favorites-session","current":{"id":42},"fixed":true,"mode":"favorites","tracks":[{"id":21},{"id":42},{"id":84}],"queue":null}"""
        val reply = api.playFavorite(42)
        val request = requests.take()
        assertEquals("POST", request.first)
        assertEquals("/api/mixes/favorites/play", request.second)
        assertEquals(42L, Json.parseToJsonElement(request.third).jsonObject.getValue("start_track_id").jsonPrimitive.content.toLong())
        assertEquals(42L, reply.current!!.key)
        assertEquals(listOf(21L,42L,84L), reply.tracks.map { it.key })
        assertTrue(reply.fixed)
        body = """{"session_id":"favorites-session","next":{"id":84},"fixed":true,"tracks":[{"id":21},{"id":42},{"id":84}]}"""
        val next = api.event("skip",42,reply.sessionId,null,10.0,180.0,8.0)
        val event = requests.take()
        assertEquals("/api/events", event.second)
        assertEquals("favorites-session", Json.parseToJsonElement(event.third).jsonObject.getValue("session_id").jsonPrimitive.content)
        assertEquals(84L, next.next!!.key)
    }

    @Test fun favoriteMustNotSilentlyStartFirstTrackWhenServerIgnoresSelection() = runBlocking {
        body = """{"session_id":"s","current":{"id":21},"fixed":true,"tracks":[{"id":21},{"id":42}]}"""
        try { api.playFavorite(42); fail("Wrong favorite accepted") }
        catch (_: IllegalStateException) { }
        Unit
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

    @Test fun passwordLoginUsesMobileTokenContractAndEscapesCredentials() = runBlocking {
        body = """{"token":"device-secret","device":{"id":"d","profile_id":"p","profile_name":"Музыка"},"profiles":[{"id":"p","name":"Музыка","is_default":true}]}"""
        val reply = api.passwordToken(" Owner ", "a\"b\\c", "p")
        val request = requests.take()
        assertEquals("POST", request.first)
        assertEquals("/api/auth/token", request.second)
        val payload = Json.parseToJsonElement(request.third).jsonObject
        assertEquals("Owner", payload.getValue("username").jsonPrimitive.content)
        assertEquals("a\"b\\c", payload.getValue("password").jsonPrimitive.content)
        assertEquals("p", payload.getValue("profile_id").jsonPrimitive.content)
        assertEquals("p", reply.device?.profileId)
        assertTrue(reply.profiles.single().isDefault)
        // Reply must not disclose the bearer through a generated data-class toString.
        assertFalse(reply.toString().contains("device-secret"))
    }

    @Test fun singleOwnerPasswordDoesNotSendInventedUsernameOrProfile() = runBlocking {
        body = """{"token":"legacy"}"""
        assertEquals("legacy", api.passwordToken("", "password").token)
        val payload = Json.parseToJsonElement(requests.take().third).jsonObject
        assertFalse(payload.containsKey("username"))
        assertFalse(payload.containsKey("profile_id"))
    }

    @Test fun publicHealthCannotMakeInvalidTokenLookAuthenticated() = runBlocking {
        body = """{"ok":false,"auth_enabled":true}"""
        try {
            api.validateConnection()
            fail("Rejected token must not be saved")
        } catch (_: IllegalStateException) { }
        assertEquals("/api/auth/me", requests.take().second)
        assertTrue(requests.isEmpty())
    }

    @Test fun connectionChecksAuthenticationBeforeHealth() = runBlocking {
        body = """{"ok":true,"auth_enabled":true,"version":"test"}"""
        assertTrue(api.validateConnection().ok)
        assertEquals("/api/auth/me", requests.take().second)
        assertEquals("/api/health", requests.take().second)
    }

    @Test fun anonymousConnectionRequiresExplicitServerPermission() = runBlocking {
        body = """{"ok":true,"auth_enabled":true}"""
        try { api.validateAnonymousConnection(); fail("Auth-enabled server must reject tokenless mode") }
        catch (_: IllegalStateException) { }
        assertEquals("/api/auth/me", requests.take().second)
        assertTrue(requests.isEmpty())
        body = """{"ok":true,"auth_enabled":false}"""
        assertTrue(api.validateAnonymousConnection().ok)
        assertEquals("/api/auth/me", requests.take().second)
        assertEquals("/api/health", requests.take().second)
    }

    @Test fun credentialExchangeDoesNotReuseSavedBearer() = runBlocking {
        val anonymous = MusikApi(ServerConnection("http://127.0.0.1:${server.address.port}", "", "fixture-client"))
        body = """{"token":"new-device"}"""
        try {
            anonymous.passwordToken("owner", "secret")
            assertEquals("", authorization.take())
            assertEquals("/api/auth/token", requests.take().second)
        } finally {
            anonymous.client.connectionPool.evictAll()
            anonymous.client.dispatcher.executorService.shutdown()
        }
    }

    @Test fun playbackClaimKeepsPositionListeningAndDeviceIdentity() = runBlocking {
        body = """{"ok":true,"state":{"session_id":"session","track_id":42,"position_sec":37.5,"listened_sec":28.0,"playing":true,"client_id":"phone"}}"""
        val reply = api.savePlaybackState(PlaybackState("session",42,37.5,28.0,true,"phone"),true)
        val request = requests.take()
        assertEquals("PUT",request.first)
        assertEquals("/api/playback/state",request.second)
        val payload = Json.parseToJsonElement(request.third).jsonObject
        assertEquals("true",payload.getValue("claim").jsonPrimitive.content)
        assertEquals("phone",payload.getValue("client_id").jsonPrimitive.content)
        assertEquals(28.0,payload.getValue("listened_sec").jsonPrimitive.content.toDouble(),0.001)
        assertEquals(37.5,reply.state!!.positionSec,0.001)
        assertTrue(reply.ok)
    }

    @Test fun ownershipRejectionRemainsVisibleEvenWithHttp200() = runBlocking {
        body = """{"ok":false,"state":{"track_id":9,"client_id":"browser"}}"""
        val reply = api.savePlaybackState(PlaybackState("session",42,0.0,0.0,true,"phone"),false)
        assertFalse(reply.ok)
        assertEquals("browser",reply.state!!.clientId)
        val payload = Json.parseToJsonElement(requests.take().third).jsonObject
        assertEquals("false",payload.getValue("claim").jsonPrimitive.content)
    }

    @Test fun restoredSessionUsesEncodedIdAndKeepsImpression() = runBlocking {
        body = """{"session_id":"s +&","current":{"id":42,"impression_id":"existing"},"fixed":true,"tracks":[{"id":42},{"id":43}]}"""
        val reply = api.now("s +&")
        assertEquals("/api/now?session_id=s%20%2B%26",requests.take().second)
        assertEquals("existing",reply.current!!.impressionId)
        assertTrue(reply.fixed)
        assertEquals(2,reply.tracks.size)
    }

    @Test fun artistPhotosAcceptServerRelativeMapAndKeepUnknownFieldsCompatible() = runBlocking {
        body = """{"photos":{"сплин":"/api/artist-photos/5"},"count":1,"future":true}"""
        assertEquals("/api/artist-photos/5",api.artistPhotos().photos["сплин"])
        assertEquals("/api/artist-photos",requests.take().second)
        assertEquals("сплин",artistPhotoKey("  СПЛИН "))
        assertEquals("a b",artistPhotoKey("A\u00a0  B"))
    }

    @Test fun serverSettingsKeepSecretsOutOfToStringAndSaveExactValues() = runBlocking {
        body = """{"saved":{"addr":":8787","public_base_url":"https://music.example","library":"/music"},"restart_pending":true,"restart_supported":false,"phone":{"token":"secret-fixture","urls":["https://music.example"]}}"""
        val reply = api.serverSettings()
        assertTrue(reply.restartPending)
        assertFalse(reply.restartSupported)
        assertEquals("secret-fixture", reply.phone.token)
        assertFalse(reply.toString().contains("secret-fixture"))
        assertFalse(reply.phone.toString().contains("secret-fixture"))
        assertEquals("GET", requests.take().first)
        api.saveServerSettings(ServerValues("[::1]:9000", "", "/music/Новая папка"))
        val request = requests.take()
        assertEquals("PUT", request.first)
        assertEquals("/api/settings", request.second)
        val saved = Json.parseToJsonElement(request.third).jsonObject
        assertEquals("[::1]:9000", saved.getValue("addr").jsonPrimitive.content)
        assertEquals("", saved.getValue("public_base_url").jsonPrimitive.content)
        assertEquals("/music/Новая папка", saved.getValue("library").jsonPrimitive.content)
    }

    @Test fun serverFolderPickerHandlesNullEmptyDirectoriesAndEncodesPath() = runBlocking {
        body = """{"path":"/music/папка","parent":"/music","dirs":null,"roots":null}"""
        assertNull(api.serverDirs("/music/папка +").dirs)
        val request = requests.take()
        assertEquals("GET", request.first)
        assertTrue(request.second.contains("%2B"))
        body = """{"ip":"203.0.113.1"}"""
        assertEquals("203.0.113.1", api.serverExternalIp().ip)
        assertEquals("/api/settings/external-ip?refresh=1", requests.take().second)
        body = """{"ok":true,"restarting":true}"""
        api.restartServer()
        assertEquals("POST", requests.take().first)
    }

}
