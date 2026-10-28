package ru.rainedev.sirinmusic.data

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiException(val code: Int, message: String) : IOException(message)

internal val apiJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

internal suspend inline fun <reified T> decodeApiJson(text: String): T =
    withContext(Dispatchers.Default) { apiJson.decodeFromString<T>(text) }

/** Keep cancellation attached until the entire body is consumed, not just the headers. */
internal suspend fun <T> Call.awaitBody(read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                response.use {
                    if (continuation.isActive) continuation.resume(read(it))
                }
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
    })
}
