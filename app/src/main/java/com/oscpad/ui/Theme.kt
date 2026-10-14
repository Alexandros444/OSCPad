@file:OptIn(ExperimentalMaterial3Api::class)

package com.oscpad.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.abs

/**
 * Color palette. Every palette is very dark (the app is mostly used at night); they differ in
 * accent, contrast and how control colors are dimmed or pulled toward the palette's hues.
 */
@Immutable
data class AppTheme(
    val id: String,
    val name: String,
    val background: Color,
    val bar: Color,
    val surface: Color,
    val tabIdle: Color,
    val accent: Color,
    val onAccent: Color,
    val secondary: Color,
    val text: Color,
    val muted: Color,
    val outline: Color,
    val guide: Color,
    val error: Color,
    /** Brightness multiplier for control colors; lower is gentler on dark-adapted eyes. */
    val controlDim: Float = 0.85f,
    /**
     * When set, control hues are pulled toward the nearest of these hues, keeping [hueKeep] of
     * their original distance so different control colors stay distinguishable.
     */
    val hueTargets: FloatArray? = null,
    val hueKeep: Float = 0.15f,
    /** Fill opacity of controls in their idle (off) state. */
    val idleFillAlpha: Float = 0.12f,
    /** Opacity of value fills (fader level, active segments). */
    val fillAlpha: Float = 0.65f,
) {
    fun controlColor(argb: Int): Color {
        val hsv = argbToHsv(argb)
        val targets = hueTargets
        if (targets != null) {
            val target = targets.minBy { angularDistance(hsv[0], it) }
            hsv[0] = (target + signedAngle(hsv[0], target) * hueKeep + 360f) % 360f
            hsv[1] = maxOf(hsv[1], 0.6f)
            if (targets.size == 1) {
                // Monochrome: brightness carries the difference between colors.
                hsv[2] = 0.55f + 0.45f * relativeLuminance(argb)
            }
        }
        return Color(hsvToArgb(hsv[0], hsv[1], hsv[2] * controlDim))
    }

    override fun equals(other: Any?) = other is AppTheme && other.id == id
    override fun hashCode() = id.hashCode()
}

private fun angularDistance(a: Float, b: Float): Float = abs(signedAngle(a, b))

/** a - b, wrapped into -180..180. */
private fun signedAngle(a: Float, b: Float): Float = ((a - b + 540f) % 360f) - 180f

private fun relativeLuminance(argb: Int): Float {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

object Themes {
    val Midnight = AppTheme(
        id = "midnight", name = "Midnight",
        background = Color(0xFF07080A), bar = Color(0xFF0D0E12), surface = Color(0xFF14161A),
        tabIdle = Color(0xFF1A1C21), accent = Color(0xFFE88E14), onAccent = Color.Black,
        secondary = Color(0xFF25A89C), text = Color(0xFFD2D4D8), muted = Color(0xFF767C85),
        outline = Color(0xFF30333A), guide = Color(0xFFE040C0), error = Color(0xFFE85A5A),
        controlDim = 0.82f,
    )

    val Oled = AppTheme(
        id = "oled", name = "OLED Black",
        background = Color.Black, bar = Color.Black, surface = Color(0xFF09090B),
        tabIdle = Color(0xFF121215), accent = Color(0xFF7FA6E6), onAccent = Color.Black,
        secondary = Color(0xFF5DB8AE), text = Color(0xFFB0B0B4), muted = Color(0xFF5E5E63),
        outline = Color(0xFF242428), guide = Color(0xFF9E7BE6), error = Color(0xFFE05A5A),
        controlDim = 0.68f, idleFillAlpha = 0.09f, fillAlpha = 0.6f,
    )

    val DeepBlue = AppTheme(
        id = "deepblue", name = "Deep Blue",
        background = Color(0xFF04070D), bar = Color(0xFF080D18), surface = Color(0xFF0D1524),
        tabIdle = Color(0xFF121C31), accent = Color(0xFF3FB0D6), onAccent = Color.Black,
        secondary = Color(0xFF8A7AE6), text = Color(0xFFC6D2E4), muted = Color(0xFF627592),
        outline = Color(0xFF1D2B44), guide = Color(0xFFE35CC4), error = Color(0xFFE05A5A),
        controlDim = 0.78f,
    )

    /** Mostly red (crimson to orange-red), like astronomy apps: preserves night vision. */
    val NightRed = AppTheme(
        id = "nightred", name = "Night Red",
        background = Color.Black, bar = Color(0xFF060000), surface = Color(0xFF0F0303),
        tabIdle = Color(0xFF180505), accent = Color(0xFF9E1A1A), onAccent = Color.Black,
        secondary = Color(0xFFC2402E), text = Color(0xFFC25050), muted = Color(0xFF743030),
        outline = Color(0xFF3A1010), guide = Color(0xFFD9602E), error = Color(0xFFFF7A6E),
        controlDim = 0.62f, hueTargets = floatArrayOf(356f), hueKeep = 0.16f, idleFillAlpha = 0.10f,
    )

    /** Warm amber to yellow, low glare. */
    val Amber = AppTheme(
        id = "amber", name = "Amber",
        background = Color(0xFF040200), bar = Color(0xFF090500), surface = Color(0xFF110C02),
        tabIdle = Color(0xFF1A1103), accent = Color(0xFFD99200), onAccent = Color.Black,
        secondary = Color(0xFFE0AE3A), text = Color(0xFFD6A44E), muted = Color(0xFF7D5E26),
        outline = Color(0xFF3B290C), guide = Color(0xFFF0D070), error = Color(0xFFE8663A),
        controlDim = 0.72f, hueTargets = floatArrayOf(36f), hueKeep = 0.14f, idleFillAlpha = 0.10f,
    )

    /** Green phosphor CRT. Pairs with the retro terminal style. */
    val Phosphor = AppTheme(
        id = "phosphor", name = "Phosphor",
        background = Color(0xFF010401), bar = Color(0xFF020802), surface = Color(0xFF051005),
        tabIdle = Color(0xFF0A180A), accent = Color(0xFF2EDB5A), onAccent = Color.Black,
        secondary = Color(0xFF9CF0A8), text = Color(0xFF43D46A), muted = Color(0xFF26743A),
        outline = Color(0xFF14401E), guide = Color(0xFFC8F060), error = Color(0xFFE8663A),
        controlDim = 0.76f, hueTargets = floatArrayOf(128f), hueKeep = 0.18f, idleFillAlpha = 0.09f,
    )

    /** Orange, green and red on dark maroon. Pairs with the Evangelion style. */
    val Nerv = AppTheme(
        id = "nerv", name = "NERV",
        background = Color(0xFF080201), bar = Color(0xFF110403), surface = Color(0xFF180605),
        tabIdle = Color(0xFF260A06), accent = Color(0xFFF07214), onAccent = Color.Black,
        secondary = Color(0xFF86D93A), text = Color(0xFFF08A30), muted = Color(0xFF9A4E22),
        outline = Color(0xFF4E2312), guide = Color(0xFF86D93A), error = Color(0xFFFF5A3A),
        controlDim = 0.92f, hueTargets = floatArrayOf(24f, 98f, 352f), hueKeep = 0.2f,
        idleFillAlpha = 0.10f, fillAlpha = 0.75f,
    )

    val all = listOf(Midnight, Oled, DeepBlue, NightRed, Amber, Phosphor, Nerv)

    fun byId(id: String?): AppTheme = all.firstOrNull { it.id == id } ?: Midnight
}

val LocalAppTheme = staticCompositionLocalOf { Themes.Midnight }

@Composable
fun OscPadTheme(theme: AppTheme, style: UiStyle, content: @Composable () -> Unit) {
    val scheme = remember(theme) {
        val tint = theme.accent.copy(alpha = 0.24f).compositeOver(theme.surface)
        darkColorScheme(
            primary = theme.accent,
            onPrimary = theme.onAccent,
            primaryContainer = tint,
            onPrimaryContainer = theme.text,
            secondary = theme.secondary,
            onSecondary = Color.Black,
            secondaryContainer = tint,
            onSecondaryContainer = theme.text,
            tertiary = theme.secondary,
            background = theme.background,
            onBackground = theme.text,
            surface = theme.surface,
            onSurface = theme.text,
            surfaceVariant = theme.tabIdle,
            onSurfaceVariant = theme.muted,
            surfaceContainerLowest = theme.background,
            surfaceContainerLow = theme.bar,
            surfaceContainer = theme.surface,
            surfaceContainerHigh = theme.surface,
            surfaceContainerHighest = theme.tabIdle,
            surfaceBright = theme.tabIdle,
            surfaceDim = theme.background,
            inverseSurface = theme.text,
            inverseOnSurface = theme.background,
            outline = theme.outline,
            outlineVariant = theme.outline,
            error = theme.error,
            onError = Color.Black,
            scrim = Color.Black,
        )
    }
    val typography = remember(style) { style.typography() }
    val shapes = remember(style) { style.materialShapes() }
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes) {
        // No ripples: nothing animates that doesn't need to.
        CompositionLocalProvider(
            LocalAppTheme provides theme,
            LocalUiStyle provides style,
            LocalRippleConfiguration provides null,
            content = content,
        )
    }
}
