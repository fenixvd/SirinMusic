package ru.rainedev.sirinmusic.data

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class AccountUser(val id: String = "", @SerialName("display_name") val displayName: String = "", val roles: List<String> = emptyList())
@Serializable
data class AccountMe(val ok: Boolean = false, @SerialName("multi_user") val multiUser: Boolean = false, @SerialName("local_account") val localAccount: Boolean = false, val user: AccountUser = AccountUser(), @SerialName("active_profile") val activeProfile: AccountProfile? = null, val profiles: List<AccountProfile> = emptyList())
@Serializable
data class ManagedDevice(val id: String = "", val name: String = "", @SerialName("profile_id") val profileId: String = "", @SerialName("profile_name") val profileName: String = "", @SerialName("created_at") val createdAt: String = "", @SerialName("last_used_at") val lastUsedAt: String? = null, @SerialName("expires_at") val expiresAt: String = "", @SerialName("revoked_at") val revokedAt: String? = null)
@Serializable
data class ManagedDevices(val tokens: List<ManagedDevice> = emptyList())
@Serializable
class IssuedDevice(val token: String = "", val device: ManagedDevice = ManagedDevice(), val qr: String = "")
@Serializable
data class ManagedUser(val id: String = "", @SerialName("display_name") val displayName: String = "", val status: String = "", val admin: Boolean = false, val profiles: Int = 0)
@Serializable
data class ManagedInvitation(val id: String = "", val email: String = "", @SerialName("created_at") val createdAt: String = "", @SerialName("expires_at") val expiresAt: String = "", @SerialName("consumed_at") val consumedAt: String = "", @SerialName("revoked_at") val revokedAt: String = "")
@Serializable
class IssuedInvitation(val id: String = "", val secret: String = "", val url: String = "")

/** Separate browser credential channel: never mixes account cookies with the player's Bearer. */
class AccountApi(baseUrl: String) {
    val base = baseUrl.trimEnd('/').toHttpUrl()
    val origin: String = base.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val jar = AccountCookieJar(base)
    val client = OkHttpClient.Builder().cookieJar(jar).followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private fun quoted(value: String) = JsonPrimitive(value).toString()
    private fun target(path: String): HttpUrl = base.resolve(path)?.also { require(sameOrigin(base, it)) { strings(R.string.account_api_1) } } ?: error(strings(R.string.account_api_2))
    private fun segment(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    private suspend fun raw(path: String, method: String = "GET", body: String? = null): String = withContext(Dispatchers.IO) {
        val url = target(path)
        val request = Request.Builder().url(url).header("Origin", origin)
            .method(method, body?.toRequestBody("application/json; charset=utf-8".toMediaType())).apply {
                if (method !in listOf("GET", "HEAD")) jar.csrf(url)?.let { header("X-CSRF-Token", it) }
            }.build()
        val call = client.newCall(request)
        val response = suspendCancellableCoroutine<Response> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) { continuation.resume(response) { _, value, _ -> value.close() } }
            })
        }
        response.use {
            val text = it.body.string()
            if (!it.isSuccessful) throw ApiException(it.code, when (it.code) {
                401 -> strings(R.string.account_api_3)
                403 -> strings(R.string.account_api_4)
                404 -> strings(R.string.account_api_5)
                409 -> strings(R.string.account_api_6)
                429 -> strings(R.string.account_api_7)
                else -> strings(R.string.account_api_8, it.code)
            })
            text
        }
    }
    private suspend inline fun <reified T> get(path: String): T = json.decodeFromString(raw(path))
    suspend fun me(): AccountMe = get("/api/auth/me")
    suspend fun login(username: String, password: String): AccountMe {
        require(username.isNotBlank() && password.length in 8..256) { strings(R.string.account_api_9) }
        jar.clear()
        raw("/api/auth/login", "POST", "{\"username\":${quoted(username.trim())},\"password\":${quoted(password)}}")
        return me().also { require(it.ok && it.multiUser) { strings(R.string.account_api_10) } }
    }
    suspend fun logout() { try { raw("/api/auth/logout", "POST", "{}") } finally { jar.clear() } }
    suspend fun password(current: String, new: String) {
        require(new.length in 8..256) { strings(R.string.account_api_11) }
        raw("/api/account/password", "POST", "{\"current\":${quoted(current)},\"new\":${quoted(new)}}")
    }
    suspend fun createProfile(name: String) { raw("/api/profiles", "POST", "{\"name\":${quoted(name.trim())}}") }
    suspend fun renameProfile(id: String, name: String) { raw("/api/profiles/${segment(id)}", "PATCH", "{\"name\":${quoted(name.trim())}}") }
    suspend fun deleteProfile(id: String) { raw("/api/profiles/${segment(id)}", "DELETE") }
    suspend fun activateProfile(id: String) { raw("/api/profiles/${segment(id)}/activate", "POST", "{}") }
    suspend fun devices(): List<ManagedDevice> = get<ManagedDevices>("/api/account/device-tokens").tokens
    suspend fun createDevice(name: String, profile: String): IssuedDevice = json.decodeFromString(raw("/api/account/device-tokens", "POST", "{\"name\":${quoted(name.trim())},\"profile_id\":${quoted(profile)}}"))
    suspend fun replaceDevice(id: String): IssuedDevice = json.decodeFromString(raw("/api/account/device-tokens/${segment(id)}/replace", "POST", "{}"))
    suspend fun revokeDevice(id: String) { raw("/api/account/device-tokens/${segment(id)}", "DELETE") }
    suspend fun users(): List<ManagedUser> = get("/api/admin/users")
    suspend fun invitations(): List<ManagedInvitation> = get("/api/admin/invitations")
    suspend fun invite(email: String, hours: Int): IssuedInvitation {
        require(hours in 1..168) { strings(R.string.account_api_12) }
        return json.decodeFromString(raw("/api/admin/invitations", "POST", "{\"email\":${quoted(email.trim())},\"ttl_hours\":$hours}"))
    }
    suspend fun revokeInvitation(id: String) { raw("/api/admin/invitations/${segment(id)}", "DELETE") }
    suspend fun updateUser(id: String, active: Boolean, admin: Boolean) { raw("/api/admin/users/${segment(id)}", "PATCH", "{\"status\":${quoted(if (active) "active" else "disabled")},\"admin\":$admin}") }
    suspend fun settings(): ServerSettingsReply = get("/api/settings")
    fun close() { jar.clear(); client.dispatcher.cancelAll(); client.connectionPool.evictAll() }
}

internal fun sameOrigin(a: HttpUrl, b: HttpUrl) = a.scheme == b.scheme && a.host == b.host && a.port == b.port
internal class AccountCookieJar(private val base: HttpUrl) : CookieJar {
    private val cookies = mutableListOf<Cookie>()
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!sameOrigin(base, url)) return
        cookies.filter { it.hostOnly && it.domain == base.host && it.matches(url) }.forEach { cookie ->
            this.cookies.removeAll { it.name == cookie.name && it.path == cookie.path }
            if (cookie.expiresAt > System.currentTimeMillis()) this.cookies.add(cookie)
        }
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!sameOrigin(base, url)) return emptyList()
        cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
        return cookies.filter { it.matches(url) }
    }
    fun csrf(url: HttpUrl): String? = loadForRequest(url).firstOrNull { it.name == "musik_csrf" }?.value
    @Synchronized fun clear() { cookies.clear() }
}
