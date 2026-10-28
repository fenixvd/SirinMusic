package ru.rainedev.sirinmusic.update

import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.strings
import ru.rainedev.sirinmusic.data.awaitBody
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tag: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)
@Serializable
internal data class GitHubAsset(
    val name: String,
    val size: Long,
    val digest: String? = null,
    @SerialName("browser_download_url") val url: String,
)
@Serializable
internal data class AvailableUpdate(val version: String, val url: String, val bytes: Long, val sha256: String) {
    val page: String get() = "https://github.com/fenixvd/SirinMusic/releases/tag/v$version"
}

internal fun selectUpdate(release: GitHubRelease, installed: String): AvailableUpdate? {
    if (release.draft || release.prerelease) return null
    val remote = ReleaseVersion.parse(release.tag) ?: return null
    val current = ReleaseVersion.parse(installed) ?: return null
    if (remote.pre != null || remote <= current) return null
    val version = release.tag.removePrefix("v")
    val filename = "SirinMusic-$version.apk"
    val expectedUrl = "https://github.com/fenixvd/SirinMusic/releases/download/${release.tag}/$filename"
    val asset = release.assets.singleOrNull { it.name == filename } ?: return null
    if (asset.url != expectedUrl || asset.size !in 1L..MAX_APK_BYTES) return null
    val hash = asset.digest?.removePrefix("sha256:") ?: return null
    if (!hash.matches(Regex("[a-fA-F0-9]{64}"))) return null
    return AvailableUpdate(version, asset.url, asset.size, hash.lowercase())
}
internal const val MAX_APK_BYTES = 64L * 1024 * 1024

/** Отдельный клиент: токен musik никогда не отправляется на GitHub. */
internal class GitHubUpdates(private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(3, TimeUnit.MINUTES)
        .followSslRedirects(false).build()) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(installed: String): AvailableUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.github.com/repos/fenixvd/SirinMusic/releases/latest")
            .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "SirinMusic/$installed").build()
        val text = client.newCall(request).awaitBody { response ->
            if (response.code == 404) null else {
                if (response.code == 403 || response.code == 429) throw IOException(strings(R.string.git_hub_updates_1))
                checkResponse(response)
                response.body.string()
            }
        } ?: return@withContext null
        withContext(Dispatchers.Default) { selectUpdate(json.decodeFromString<GitHubRelease>(text), installed) }
    }

    suspend fun download(update: AvailableUpdate, partial: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        require(update.bytes in 1L..MAX_APK_BYTES)
        val request = Request.Builder().url(update.url).build()
        val caller = currentCoroutineContext()
        client.newCall(request).awaitBody { response ->
            checkResponse(response)
            require(response.request.url.scheme == "https" && response.request.url.host in setOf(
                "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")) { strings(R.string.git_hub_updates_2) }
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L; var reported = -1
            partial.outputStream().use { output -> response.body.byteStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    caller.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= update.bytes && total <= MAX_APK_BYTES) { strings(R.string.git_hub_updates_3) }
                    output.write(buffer, 0, count); digest.update(buffer, 0, count)
                    val percent = (100 * total / update.bytes).toInt()
                    if (percent != reported) { reported = percent; onProgress(total.toFloat() / update.bytes) }
                }
            } }
            require(total == update.bytes) { strings(R.string.git_hub_updates_4) }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            require(hash == update.sha256) { strings(R.string.git_hub_updates_5) }
        }
    }
    private fun checkResponse(response: Response) {
        if (!response.isSuccessful) throw IOException(strings(R.string.git_hub_updates_6, response.code))
    }
}
