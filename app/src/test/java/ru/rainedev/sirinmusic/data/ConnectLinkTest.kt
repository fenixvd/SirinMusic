package ru.rainedev.sirinmusic.data

import org.junit.Assert.*
import org.junit.Test

class ConnectLinkTest : ru.rainedev.sirinmusic.LocalizedTest() {
    @Test fun generatedConnectionLinksRoundTripEscapedTokensAndServerPaths() {
        val original = ConnectLink("https://music.example.com/subpath", "a+b/c=d&value")
        assertEquals(original, ConnectLink.parse(original.encode()))
        assertEquals(ConnectLink("https://music.example.com", null),
            ConnectLink.parse(ConnectLink("https://music.example.com", null).encode()))
    }

    @Test fun connectionDiagnosticsNeverPrintTokens() {
        val token = "private-fixture-token"
        assertFalse(ConnectLink("https://music.example.com", token).toString().contains(token))
        assertFalse(ServerConnection("https://music.example.com", token, "client").toString().contains(token))
    }

    @Test fun serverQrWithToken() {
        val link = ConnectLink.parse("musik://connect?token=ab%2Bc+d&url=http%3A%2F%2F192.168.1.5%3A8787%2F")
        assertEquals(ConnectLink("http://192.168.1.5:8787", "ab+c d"), link)
    }

    @Test fun serverQrWithoutTokenAndHttpsDomain() {
        assertEquals(ConnectLink("https://music.example.com", null),
            ConnectLink.parse("  musik://connect?url=https%3A%2F%2Fmusic.example.com  "))
        assertEquals(ConnectLink("https://music.example.com", null),
            ConnectLink.parse("musik://connect?url=https%3A%2F%2Fmusic.example.com&token="))
    }

    @Test fun plainAddressFillsOnlyTheUrl() {
        assertEquals(ConnectLink("http://10.0.0.2:8787", null), ConnectLink.parse("http://10.0.0.2:8787/?x=1#y"))
    }

    @Test fun foreignOrBrokenCodesAreRejected() {
        listOf(
            "", "hello", "WIFI:S:home;T:WPA;P:secret;;", "musik://other?url=http%3A%2F%2Fx",
            "musik://connect?token=t", "musik://connect?url=ftp%3A%2F%2Fx", "mailto:a@b.c",
        ).forEach { assertNull(it, ConnectLink.parse(it)) }
    }
}
