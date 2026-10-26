package ru.rainedev.sirinmusic.update

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class GitHubUpdatesTest : ru.rainedev.sirinmusic.LocalizedTest() {
    private fun client(bytes: ByteArray, code: Int = 200) = OkHttpClient.Builder().addInterceptor { chain ->
        assertNull(chain.request().header("Authorization"))
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
            .body(bytes.toResponseBody()).build()
    }.build()
    private val bytes = "signed APK fixture".toByteArray()
    private fun update(size: Long = bytes.size.toLong(), digest: String = hash(bytes)) = AvailableUpdate("2.0.0",
        "https://github.com/fenixvd/SirinMusic/releases/download/v2.0.0/SirinMusic-2.0.0.apk", size, digest)
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun downloadVerifiesBytesAndReportsProgress() = runBlocking {
        val file = File.createTempFile("sirin-update-", ".part")
        try {
            var progress = 0f
            GitHubUpdates(client(bytes)).download(update(), file) { progress = it }
            assertArrayEquals(bytes, file.readBytes())
            assertEquals(1f, progress, .001f)
        } finally { file.delete() }
    }
    @Test fun badDigestAndPartialOrOversizedDownloadsAreRejected() {
        for (update in listOf(update(digest = "a".repeat(64)), update(size = bytes.size - 1L), update(size = bytes.size + 1L))) {
            val file = File.createTempFile("sirin-update-", ".part")
            try {
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { GitHubUpdates(client(bytes)).download(update, file) {} }
                }
            } finally { file.delete() }
        }
    }
    @Test fun missingReleaseIsNotAnErrorButRateLimitIs() = runBlocking<Unit> {
        assertNull(GitHubUpdates(client(ByteArray(0), 404)).latest("1.5-dev"))
        assertThrows(java.io.IOException::class.java) {
            runBlocking { GitHubUpdates(client(ByteArray(0), 429)).latest("1.5-dev") }
        }
    }
}
