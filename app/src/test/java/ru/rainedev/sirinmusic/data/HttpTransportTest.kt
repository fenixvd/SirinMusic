package ru.rainedev.sirinmusic.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class HttpTransportTest : ru.rainedev.sirinmusic.LocalizedTest() {
    @Test fun cancellationInterruptsAnAlreadyStartedResponseBody() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val reading = CountDownLatch(1)
        val release = CountDownLatch(1)
        val client = OkHttpClient()
        server.createContext("/") { exchange ->
            try {
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.write("partial".toByteArray())
                exchange.responseBody.flush()
                release.await(5, TimeUnit.SECONDS)
            } finally { exchange.close() }
        }
        server.start()
        try {
            val call = client.newCall(Request.Builder().url("http://127.0.0.1:${server.address.port}/").build())
            val task = launch {
                call.awaitBody {
                    val source = it.body.source()
                    source.readUtf8(7) // Consume the first chunk before requesting cancellation.
                    reading.countDown()
                    source.readUtf8()
                }
            }
            assertTrue(withContext(Dispatchers.IO) { reading.await(3, TimeUnit.SECONDS) })
            withTimeout(1_500) { task.cancelAndJoin() }
            assertTrue(call.isCanceled())
            withTimeout(1_500) { while (client.dispatcher.runningCallsCount() != 0) delay(5) }
        } finally {
            release.countDown(); server.stop(0)
            client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
        }
    }

    @Test fun oldUnauthorizedResponseCannotLogOutTheNewAccount() = verifyUnauthorized(changedAccount = true)
    @Test fun currentUnauthorizedResponseStillInvalidatesTheConnection() = verifyUnauthorized(changedAccount = false)

    private fun verifyUnauthorized(changedAccount: Boolean) = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val received = CountDownLatch(1)
        val release = CountDownLatch(1)
        val settings = object : ConnectionSettings {
            override val baseUrl = "http://127.0.0.1:${server.address.port}"
            @Volatile override var token = "old-fixture-token"
            override val clientId = "fixture-client"
        }
        val api = MusikApi(settings)
        val notices = Channel<ServerConnection>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { api.unauthorized.collect { notices.send(it) } }
        server.createContext("/") { exchange ->
            try {
                received.countDown(); release.await(3, TimeUnit.SECONDS)
                exchange.sendResponseHeaders(401, -1)
            } finally { exchange.close() }
        }
        server.start()
        try {
            val request = async { runCatching { api.health() }.exceptionOrNull() }
            assertTrue(withContext(Dispatchers.IO) { received.await(3, TimeUnit.SECONDS) })
            if (changedAccount) settings.token = "new-fixture-token"
            release.countDown()
            assertEquals(401, (withTimeout(3_000) { request.await() } as ApiException).code)
            yield()
            if (changedAccount) assertTrue(notices.tryReceive().isFailure)
            else {
                val rejected = withTimeout(1_000) { notices.receive() }
                assertTrue(api.isCurrentConnection(rejected))
                settings.token = "new-fixture-token"
                assertFalse(api.isCurrentConnection(rejected)) // A queued notice is rechecked by the UI.
            }
        } finally {
            release.countDown(); collector.cancelAndJoin(); server.stop(0)
            api.client.connectionPool.evictAll(); api.client.dispatcher.executorService.shutdown()
        }
    }
}
