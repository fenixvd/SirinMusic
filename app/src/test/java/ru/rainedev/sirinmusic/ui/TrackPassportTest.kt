package ru.rainedev.sirinmusic.ui

import org.junit.Assert.*
import org.junit.Test
import ru.rainedev.sirinmusic.LocalizedTest
import ru.rainedev.sirinmusic.data.TrackPassport
import java.util.Locale

class TrackPassportTest : LocalizedTest() {
    @Test fun absentAndInvalidMeasurementsAreNotShownAsZeroOrNan() {
        assertTrue(passportLabels(TrackPassport(), Locale.US).isEmpty())
        assertTrue(passportLabels(TrackPassport(bitrate=0, sampleRate=0, bpm=Double.NaN,
            lufs=Double.POSITIVE_INFINITY, year=0, duration=-1.0, plays=0, earlySkips=0,
            lastPlayedAt="invalid"), Locale.US).isEmpty())
    }
    @Test fun measuredValuesKeepUnitsAndUseSelectedLocale() {
        loadTextResources("en")
        val en = passportLabels(TrackPassport(format="mp3", bitrate=320000, sampleRate=44100,
            bpm=86.0, lufs=-11.3, key="D", mode="minor", duration=190.0, plays=2, earlySkips=1), Locale.US)
        assertTrue(en.containsAll(listOf("MP3", "320 kbit/s", "44.1 kHz", "86 BPM", "-11.3 LUFS", "D minor", "3:10", "Plays: 2", "Early skips: 1")))
        loadTextResources("ru")
        val ru = passportLabels(TrackPassport(sampleRate=44100, lufs=-11.3), Locale.forLanguageTag("ru"))
        assertTrue(ru.contains("44,1 кГц"))
        assertTrue(ru.contains("-11,3 LUFS"))
    }
}
