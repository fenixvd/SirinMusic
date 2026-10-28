package ru.rainedev.sirinmusic

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.rainedev.sirinmusic.data.Track
import ru.rainedev.sirinmusic.sharing.trackShareText

class TrackSharingTest {
    @Test fun sharesMetadataWithoutServerOrPlaybackCredentials() {
        val track = Track(
            id = 42, title = "Тихий вечер", artist = "Исполнитель", album = "Альбом",
            artwork = "https://private.example/artwork?token=secret",
            stream = "https://private.example/stream?token=secret",
            impressionId = "private-impression", explanation = "private recommendation",
        )
        assertEquals("Тихий вечер\nИсполнитель\nАльбом", trackShareText(track))
    }

    @Test fun missingMetadataDoesNotProduceNullsOrEmptyLines() {
        assertEquals("Title\nArtist", trackShareText(Track(title = " Title ", artist = "Artist", album = "  ")))
        assertEquals("Artist\nAlbum", trackShareText(Track(artist = "Artist", album = "Album")))
        assertEquals("—", trackShareText(Track()))
    }
}
