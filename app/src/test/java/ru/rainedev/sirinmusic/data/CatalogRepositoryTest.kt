package ru.rainedev.sirinmusic.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class CatalogRepositoryTest : ru.rainedev.sirinmusic.LocalizedTest() {
    @Test fun independentSectionsLoadConcurrentlyIntoOneSnapshot() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool()
        val started = CountDownLatch(3)
        server.executor = executor
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val concurrent = if (path == "/api/health") true else {
                started.countDown()
                started.await(3, TimeUnit.SECONDS)
            }
            val body = when (path) {
                "/api/health" -> """{"ok":true,"tracks":1}"""
                "/api/library" -> """[{"id":42,"title":"Track"}]"""
                "/api/artists" -> """{"artists":[{"artist":"Artist","tracks":1}]}"""
                "/api/albums" -> """{"albums":[{"artist":"Artist","album":"Album","tracks":1}]}"""
                else -> "{}"
            }.toByteArray()
            exchange.sendResponseHeaders(if (concurrent) 200 else 500, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val api = MusikApi(ServerConnection("http://127.0.0.1:${server.address.port}", "fixture", "client"))
        try {
            val snapshot = withTimeout(5_000) { CatalogRepository(api).fetch() }
            assertEquals(1, snapshot.health.tracks)
            assertEquals(42L, snapshot.tracks.single().key)
            assertEquals("Artist", snapshot.artists.single().artist)
            assertEquals("Album", snapshot.albums.single().album)
        } finally {
            server.stop(0); executor.shutdownNow()
            api.client.connectionPool.evictAll(); api.client.dispatcher.executorService.shutdown()
        }
    }
}
