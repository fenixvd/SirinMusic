package ru.rainedev.sirinmusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : IOException(message)

/**
 * Клиент к Go-плееру musik. Запросы простые, поэтому без Retrofit: OkHttp плюс
 * kotlinx.serialization. Токен подставляется интерсептором — он нужен не только
 * для JSON, но и для /api/stream и /api/artwork, иначе те отвечают 401.
 */
class MusikApi(private val settings: ConnectionSettings) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val _unauthorized = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val unauthorized = _unauthorized.asSharedFlow()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            val base = settings.baseUrl.toHttpUrlOrNull()
            val sameServer = base != null && request.url.scheme == base.scheme &&
                request.url.host == base.host && request.url.port == base.port
            val authorized = request.newBuilder().removeHeader("Authorization").apply {
                if (sameServer && settings.token.isNotEmpty()) header("Authorization", "Bearer ${settings.token}")
            }.build()
            val response = chain.proceed(authorized)
            if (sameServer && response.code == 401) _unauthorized.tryEmit(Unit)
            response
        })
        .build()

    fun url(path: String): String {
        val base = settings.baseUrl.toHttpUrlOrNull() ?: throw IllegalArgumentException("Некорректный адрес сервера")
        val target = if (path.startsWith("/")) (settings.baseUrl + path).toHttpUrlOrNull()
            else path.toHttpUrlOrNull()
        require(target != null && target.scheme == base.scheme && target.host == base.host && target.port == base.port) {
            "Ссылка ведёт на другой сервер"
        }
        return target.toString()
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    private suspend fun raw(path: String, body: String?, method: String = if (body == null) "GET" else "POST"): String = withContext(Dispatchers.IO) {
        val payload = body?.toRequestBody(JSON_TYPE)
        val request = Request.Builder().url(url(path)).method(method, payload).build()
        val call = client.newCall(request)
        try {
            call.await().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) throw ApiException(response.code, shortError(response.code, text))
                text
            }
        } finally { call.cancel() }
    }

    private fun shortError(code: Int, text: String): String = when (code) {
        401 -> "Неверный токен (401)"
        404 -> "Маршрут не найден (404)"
        429 -> "Слишком часто — подожди минуту (429)"
        else -> "Сервер ответил $code: ${text.take(120)}"
    }

    private suspend inline fun <reified T> get(path: String): T =
        withContext(Dispatchers.Default) { json.decodeFromString(raw(path, null)) }

    private suspend inline fun <reified T> post(path: String, body: String = "{}"): T =
        withContext(Dispatchers.Default) { json.decodeFromString(raw(path, body)) }

    suspend fun health(): Health = get("/api/health")

    suspend fun startRadio(seedTrackId: Long? = null): RadioStart =
        post("/api/radio/start", if (seedTrackId != null) """{"seed_track_id":$seedTrackId}""" else "{}")

    suspend fun mixes(): MixesReply = get("/api/mixes")

    suspend fun playMix(kind: String): RadioStart = post("/api/mixes/$kind/play")

    suspend fun playTrack(trackId: Long): RadioStart = post("/api/play", """{"track_id":$trackId}""")

    suspend fun playArtist(artist: String): RadioStart =
        post("/api/play", """{"artist":${quote(artist)}}""")

    suspend fun playAlbum(artist: String, album: String): RadioStart =
        post("/api/play", """{"artist":${quote(artist)},"album":${quote(album)}}""")

    /**
     * Серверного поиска НЕТ: у /api/library нет параметра q (проверено — сервер его
     * игнорирует и отдаёт начало списка). Веб-морда грузит библиотеку целиком и
     * фильтрует у себя, здесь так же: фильтрация в LibraryScreen.
     */
    suspend fun library(): List<Track> = get("/api/library")

    suspend fun artists(): ArtistsReply = get("/api/artists")

    suspend fun albums(): AlbumsReply = get("/api/albums")

    suspend fun toggleFavorite(trackId: Long): FavoriteReply =
        post("/api/favorites/toggle", """{"type":"track","track_id":$trackId}""")

    suspend fun favorites(): FavoritesReply = withContext(Dispatchers.Default) {
        val element = json.parseToJsonElement(raw("/api/favorites", null))
        // Older installations returned a flat track array.
        if (element is JsonArray) FavoritesReply(tracks = json.decodeFromJsonElement<List<Track>>(element).map { FavoriteEntry(trackId = it.key, track = it) })
        else json.decodeFromJsonElement(element)
    }

    suspend fun toggleArtistFavorite(artist: String): FavoriteReply =
        post("/api/favorites/toggle", """{"type":"artist","artist":${quote(artist)}}""")

    suspend fun toggleAlbumFavorite(artist: String, album: String): FavoriteReply =
        post("/api/favorites/toggle", """{"type":"album","artist":${quote(artist)},"album":${quote(album)}}""")

    suspend fun playlists(): PlaylistsReply = get("/api/playlists")
    suspend fun playlist(id: Long): Playlist = get("/api/playlists/$id")
    suspend fun createPlaylist(name: String, description: String): Playlist =
        post("/api/playlists", """{"name":${quote(name)},"description":${quote(description)},"type":"manual"}""")
    suspend fun editPlaylist(playlist: Playlist, name: String, description: String): Playlist = withContext(Dispatchers.Default) {
        json.decodeFromString(raw("/api/playlists/${playlist.id}",
            """{"name":${quote(name)},"description":${quote(description)},"sort_mode":${quote(playlist.sortMode)},"allow_duplicates":${playlist.allowDuplicates}}""", "PATCH"))
    }
    suspend fun deletePlaylist(id: Long) { raw("/api/playlists/$id?hard=1", null, "DELETE") }
    suspend fun addPlaylistTrack(id: Long, trackId: Long) {
        raw("/api/playlists/$id/tracks", """{"track_id":$trackId}""")
    }
    suspend fun removePlaylistTrack(id: Long, itemId: String) {
        val encoded = java.net.URLEncoder.encode(itemId, "UTF-8").replace("+", "%20")
        raw("/api/playlists/$id/tracks/$encoded", null, "DELETE")
    }
    suspend fun reorderPlaylist(id: Long, itemIds: List<String>): Playlist =
        post("/api/playlists/$id/reorder", """{"item_ids":[${itemIds.joinToString(",") { quote(it) }}]}""")
    suspend fun playPlaylist(id: Long, index: Int = 0): RadioStart =
        post("/api/playlists/$id/play", """{"start_index":$index}""")
    suspend fun lyrics(id: Long): Lyrics = get("/api/tracks/$id/lyrics")


    suspend fun profile(): Profile = get("/api/profile")
    suspend fun weeklyMetrics(): WeeklyMetrics = get("/api/metrics/weekly")
    suspend fun recommendations(): Recommendations = get("/api/metrics/recommendations")
    suspend fun saveExplore(lo: Double, hi: Double) {
        require(lo.isFinite() && hi.isFinite() && lo in 0.0..1.0 && hi in lo..1.0) { "Некорректные границы новизны" }
        raw("/api/profile/explore", """{"explore_lo":$lo,"explore_hi":$hi}""", "PUT")
    }
    suspend fun contexts(): ContextsReply = get("/api/contexts")
    suspend fun createContext(name: String, kind: String): TasteContext {
        require(name.isNotBlank() && kind in setOf("mood", "place", "activity"))
        return post("/api/contexts", """{"name":${quote(name.trim())},"kind":${quote(kind)},"influence":1,"learning_enabled":true}""")
    }
    suspend fun activateContext(id: String, session: String, active: Boolean): ContextActivation =
        post("/api/contexts/${segment(id)}/${if (active) "activate" else "deactivate"}", """{"session_id":${quote(session)}}""")
    suspend fun deleteContext(id: String) { raw("/api/contexts/${segment(id)}", null, "DELETE") }
    suspend fun rules(): RulesReply = get("/api/rules")
    suspend fun restoreRule(id: String) { raw("/api/rules/${segment(id)}", null, "DELETE") }
    suspend fun undoRule() { raw("/api/rules/undo", "{}") }
    suspend fun shares(): SharesReply = get("/api/share/radio")
    suspend fun createShare(): RadioShare = post("/api/share/radio", """{"name":"Sirin Music"}""")
    suspend fun revokeShare(token: String) { raw("/api/share/radio/${segment(token)}", null, "DELETE") }
    suspend fun refreshMixes(): MixJob = post("/api/jobs/mix_pack")
    suspend fun mixJob(id: Long): MixJob = get("/api/jobs/$id")
    private fun segment(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")


    suspend fun jump(sessionId: String, trackId: Long): RadioStart =
        post("/api/session/jump", """{"session_id":${quote(sessionId)},"track_id":$trackId}""")

    suspend fun jumpIndex(sessionId: String, index: Int): RadioStart =
        post("/api/session/jump", """{"session_id":${quote(sessionId)},"index":$index}""")

    suspend fun previous(sessionId: String): RadioStart =
        post("/api/session/back", """{"session_id":${quote(sessionId)}}""")

    /**
     * Событие прослушивания. Ответ сервера содержит next — именно он решает,
     * что играть дальше, поэтому очередь не строится на клиенте.
     */
    suspend fun event(
        type: String,
        trackId: Long,
        sessionId: String?,
        impressionId: String?,
        positionSec: Double,
        durationSec: Double,
        listenedSec: Double,
    ): EventReply {
        val body = buildString {
            append("{")
            append(""""type":${quote(type)},""")
            append(""""event_id":${quote(randomId())},""")
            append(""""track_id":$trackId,""")
            if (sessionId != null) append(""""session_id":${quote(sessionId)},""")
            if (impressionId != null) append(""""impression_id":${quote(impressionId)},""")
            append(""""client_id":${quote(settings.clientId)},""")
            append(""""device_id":"android",""")
            append(""""position_sec":${fmt(positionSec)},""")
            append(""""duration_sec":${fmt(durationSec)},""")
            append(""""listened_sec":${fmt(listenedSec)}""")
            append("}")
        }
        return post("/api/events", body)
    }

    private fun fmt(v: Double): String = if (v.isFinite()) "%.3f".format(java.util.Locale.US, v) else "0"

    /** Корректное экранирование: имена артистов и альбомов содержат кавычки и слэши. */
    private fun quote(s: String): String = JsonPrimitive(s).toString()

    companion object {
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        fun randomId(): String = java.util.UUID.randomUUID().toString().replace("-", "")
    }
}


fun validateServerUrl(value: String): String? {
    val parsed = value.trim().toHttpUrlOrNull() ?: return "Укажи полный адрес: https://сервер или http://адрес:порт"
    if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty() || parsed.query != null || parsed.fragment != null)
        return "Адрес не должен содержать логин, пароль, параметры или фрагмент"
    return null
}
