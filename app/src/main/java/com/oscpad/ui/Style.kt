package com.oscpad.ui

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.unit.dp

/**
 * Appearance style: shapes, fonts, decorations and how controls are drawn. Independent of the
 * color palette ([AppTheme]); picking a style applies its matching palette, if it has one.
 */
enum class UiStyle(val label: String, val suggestedThemeId: String?) {
    STANDARD("Standard", null),
    MINIMAL("Minimal", null),
    TERMINAL("Retro terminal", "phosphor"),
    NERV("Evangelion", "nerv"),
}

val LocalUiStyle = staticCompositionLocalOf { UiStyle.STANDARD }

/** Roboto Condensed ships with Android; it is the closest built-in match to the NERV look. */
private val Condensed: FontFamily by lazy {
    FontFamily(Typeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)))
}

val UiStyle.fontFamily: FontFamily
    get() = when (this) {
        UiStyle.STANDARD, UiStyle.MINIMAL -> FontFamily.Default
        UiStyle.TERMINAL -> FontFamily.Monospace
        UiStyle.NERV -> Condensed
    }

val UiStyle.skin: Skin
    get() = when (this) {
        UiStyle.STANDARD -> FlatSkin.Standard
        UiStyle.MINIMAL -> FlatSkin.Minimal
        UiStyle.TERMINAL -> TerminalSkin
        UiStyle.NERV -> NervSkin
    }

/** Shape of page tabs, toolbar buttons and edit outlines. */
val UiStyle.chromeShape: Shape
    get() = when (this) {
        UiStyle.STANDARD -> RoundedCornerShape(10.dp)
        UiStyle.MINIMAL, UiStyle.TERMINAL -> RectangleShape
        UiStyle.NERV -> CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)
    }

/** Text shown on a page tab. */
fun UiStyle.tabText(name: String): String = when (this) {
    UiStyle.TERMINAL -> "[ ${name.uppercase()} ]"
    UiStyle.NERV, UiStyle.MINIMAL -> name.uppercase()
    UiStyle.STANDARD -> name
}

fun UiStyle.typography(): Typography {
    val ff = fontFamily
    val t = Typography()
    fun TextStyle.f() = copy(fontFamily = ff)
    return t.copy(
        displayLarge = t.displayLarge.f(), displayMedium = t.displayMedium.f(), displaySmall = t.displaySmall.f(),
        headlineLarge = t.headlineLarge.f(), headlineMedium = t.headlineMedium.f(), headlineSmall = t.headlineSmall.f(),
        titleLarge = t.titleLarge.f(), titleMedium = t.titleMedium.f(), titleSmall = t.titleSmall.f(),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(), labelMedium = t.labelMedium.f(), labelSmall = t.labelSmall.f(),
    )
}

fun UiStyle.materialShapes(): Shapes = when (this) {
    UiStyle.STANDARD -> Shapes()
    UiStyle.MINIMAL, UiStyle.TERMINAL -> RoundedCornerShape(0.dp).let { Shapes(it, it, it, it, it) }
    UiStyle.NERV -> Shapes(
        extraSmall = CutCornerShape(2.dp),
        small = CutCornerShape(4.dp),
        medium = CutCornerShape(6.dp),
        large = CutCornerShape(8.dp),
        extraLarge = CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp),
    )
}
