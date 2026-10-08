package ru.rainedev.sirinmusic

import org.junit.Assert.*
import org.junit.Test

class RadioSharingTest {
    @Test fun publicListenLinkCanUseConfiguredPublicHost() {
        assertEquals("https://radio.example/listen/public.mp3", publicRadioLink("https://radio.example/listen/public.mp3"))
    }
    @Test fun refusesApiLinksCredentialsAndTokenQueries() {
        listOf("https://example/api/stream/1", "https://secret@example/listen/public.mp3",
            "https://example/listen/public.mp3?token=secret", "file:///listen/public.mp3").forEach {
            try { publicRadioLink(it); fail("unsafe sharing link") } catch (_: IllegalArgumentException) { }
            catch (_: IllegalStateException) { }
        }
    }
}
