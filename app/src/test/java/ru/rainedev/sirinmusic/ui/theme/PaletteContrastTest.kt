package ru.rainedev.sirinmusic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.rainedev.sirinmusic.data.Palette

class PaletteContrastTest : ru.rainedev.sirinmusic.LocalizedTest() {
    @Test fun arbitrarySeedsKeepTextReadableInBothThemes() {
        val seeds = Palette.entries.map { it.seed } + listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(),
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())
        for (seed in seeds) for (dark in listOf(false, true)) {
            val scheme = seedColors(seed, dark)
            val pairs = listOf(scheme.primary to scheme.onPrimary,
                scheme.secondary to scheme.onSecondary, scheme.tertiary to scheme.onTertiary,
                scheme.primaryContainer to scheme.onPrimaryContainer,
                scheme.background to scheme.onBackground, scheme.surface to scheme.onSurface,
                scheme.error to scheme.onError)
            for ((background, text) in pairs) assertTrue("seed=$seed dark=$dark contrast=${contrast(background, text)}",
                contrast(background, text) >= 4.45)
        }
    }
    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble(); val l2 = b.luminance().toDouble()
        return (maxOf(l1, l2) + .05) / (minOf(l1, l2) + .05)
    }
}
