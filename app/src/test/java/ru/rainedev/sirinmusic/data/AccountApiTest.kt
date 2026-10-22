package ru.rainedev.sirinmusic.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AccountApiTest : ru.rainedev.sirinmusic.LocalizedTest() {
    private lateinit var server: HttpServer
    private lateinit var api: AccountApi
    private var body = """{"ok":true,"multi_user":true,"local_account":true,"user":{"id":"u","display_name":"User","roles":["admin"]},"profiles":[]}"""
    private var status = 200
    private data class Seen(val method: String, val path: String, val body: String, val auth: String?, val cookie: String?, val csrf: String?, val origin: String?)
    private val seen = LinkedBlockingQueue<Seen>()
    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { e ->
            seen.add(Seen(e.requestMethod,e.requestURI.toString(),e.requestBody.bufferedReader().readText(),e.requestHeaders.getFirst("Authorization"),e.requestHeaders.getFirst("Cookie"),e.requestHeaders.getFirst("X-CSRF-Token"),e.requestHeaders.getFirst("Origin")))
            if (e.requestURI.path == "/api/auth/login") {
                e.responseHeaders.add("Set-Cookie", "musik_session=session-fixture; Path=/; HttpOnly")
                e.responseHeaders.add("Set-Cookie", "musik_csrf=csrf-fixture; Path=/")
            }
            val bytes=body.toByteArray();e.sendResponseHeaders(status,bytes.size.toLong());e.responseBody.use { it.write(bytes) }
        }
        server.start();api=AccountApi("http://127.0.0.1:${server.address.port}")
    }
    @After fun stop() { api.close();api.client.dispatcher.executorService.shutdown();server.stop(0) }
    @Test fun accountSessionUsesCookiesAndCsrfWithoutPlayerBearer() = runBlocking {
        assertTrue(api.login("User","fixture-password").ok)
        val login=seen.take();val me=seen.take()
        assertEquals("POST",login.method);assertEquals("/api/auth/login",login.path)
        assertNull(login.auth);assertNull(me.auth);assertNull(login.cookie)
        assertEquals(api.origin,login.origin)
        assertTrue(me.cookie!!.contains("musik_session=session-fixture"))
        api.renameProfile("profile +/", "New Name")
        val renamed=seen.take();assertEquals("PATCH",renamed.method)
        assertEquals("csrf-fixture",renamed.csrf);assertNull(renamed.auth)
        assertTrue(renamed.path.contains("profile%20%2B%2F"))
        assertTrue(renamed.body.contains("New Name"))
        api.logout();assertEquals("POST",seen.take().method)
        api.me();assertNull(seen.take().cookie)
    }
    @Test fun cookieJarRejectsForeignOriginPortsDomainsAndInsecureTransport() {
        val base="https://music.example.test".toHttpUrl();val jar=AccountCookieJar(base)
        jar.saveFromResponse(base,listOf(Cookie.parse(base,"musik_session=private; Path=/; Secure; HttpOnly")!!))
        assertEquals(1,jar.loadForRequest(base).size)
        assertTrue(jar.loadForRequest("https://evil.test".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://music.example.test:8443".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("http://music.example.test".toHttpUrl()).isEmpty())
        jar.saveFromResponse(base,listOf(Cookie.Builder().name("wide").value("bad").domain("example.test").path("/").secure().build()))
        assertEquals(1,jar.loadForRequest(base).size)
        jar.saveFromResponse(base,listOf(Cookie.parse(base,"musik_session=gone; Path=/; Secure; Max-Age=0")!!))
        assertTrue(jar.loadForRequest(base).isEmpty())
    }
    @Test fun tokensAndInvitationsDoNotExposeSecretsInToString() = runBlocking {
        body="""{"token":"token-fixture","device":{"id":"d","profile_id":"p","name":"Phone"},"qr":"<svg/>"}"""
        val device=api.createDevice("Phone","p")
        assertEquals("token-fixture",device.token);assertFalse(device.toString().contains(device.token))
        assertEquals("/api/account/device-tokens",seen.take().path)
        body="""{"id":"i","secret":"invite-fixture","url":"https://music.test/?invitation=invite-fixture"}"""
        val invite=api.invite("",24)
        assertFalse(invite.toString().contains(invite.secret));assertEquals("invite-fixture",invite.secret)
        val request=seen.take();assertEquals("POST",request.method);assertTrue(request.body.contains("\"ttl_hours\":24"))
    }
    @Test fun privilegedEditsUseCorrectVerbsAndNeverEchoErrorBodies() = runBlocking {
        body="{}"
        api.updateUser("user",true,false)
        val edit=seen.take();assertEquals("PATCH",edit.method);assertEquals("/api/admin/users/user",edit.path);assertTrue(edit.body.contains("\"admin\":false"))
        api.revokeDevice("device");assertEquals("DELETE",seen.take().method)
        api.deleteProfile("profile");assertEquals("DELETE",seen.take().method)
        api.revokeInvitation("invite");assertEquals("DELETE",seen.take().method)
        status=401;body="secret-server-response"
        try { api.me();fail("Expected unauthorized") } catch(e: ApiException) { assertEquals(401,e.code);assertFalse(e.message!!.contains(body)) }
    }
}
