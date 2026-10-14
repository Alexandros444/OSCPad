package com.oscpad.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {

    @Test
    fun roundTripsPaletteAndPrimaries() {
        val colors = listOf(0xFFFF9F1C, 0xFF2EC4B6, 0xFFE71D36, 0xFF4CC9F0, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF, 0xFF000000, 0xFF808080)
            .map { it.toInt() }
        for (c in colors) {
            val hsv = argbToHsv(c)
            assertEquals(Integer.toHexString(c), c, hsvToArgb(hsv[0], hsv[1], hsv[2]))
        }
    }

    @Test
    fun knownValues() {
        val red = argbToHsv(0xFFFF0000.toInt())
        assertEquals(0f, red[0], 0.01f); assertEquals(1f, red[1], 0.01f); assertEquals(1f, red[2], 0.01f)
        val blue = argbToHsv(0xFF0000FF.toInt())
        assertEquals(240f, blue[0], 0.01f)
        assertEquals(0xFF00FFFF.toInt(), hsvToArgb(180f, 1f, 1f))
        assertEquals(0xFFFF0000.toInt(), hsvToArgb(360f, 1f, 1f)) // hue wraps
    }

    @Test
    fun nightThemesKeepColorsApartWithinTheirHueBand() {
        val palette = com.oscpad.model.Palette
        for (theme in listOf(Themes.NightRed, Themes.Amber, Themes.Phosphor)) {
            val colors = palette.map { theme.controlColor(it) }
            // Distinct enough to tell controls apart...
            assertEquals(theme.name, palette.size, colors.map { it.value }.toSet().size)
            // ...but every hue stays close to the theme's hue.
            val target = theme.hueTargets!!.single()
            for (c in colors) {
                val h = argbToHsv(c.toArgbInt())[0]
                val d = ((h - target + 540f) % 360f) - 180f
                assertTrue(theme.name + ' ' + h, kotlin.math.abs(d) <= 30f)
            }
        }
    }

    @Test
    fun nervKeepsGreensGreenAndOrangesOrange() {
        val green = argbToHsv(Themes.Nerv.controlColor(0xFFB5E48C.toInt()).toArgbInt())[0]
        val orange = argbToHsv(Themes.Nerv.controlColor(0xFFFF9F1C.toInt()).toArgbInt())[0]
        assertTrue(green in 80f..120f)
        assertTrue(orange in 10f..40f)
    }

    private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int =
        (0xFF shl 24) or ((red * 255f + 0.5f).toInt() shl 16) or ((green * 255f + 0.5f).toInt() shl 8) or (blue * 255f + 0.5f).toInt()
}
