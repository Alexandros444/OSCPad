package com.oscpad.ui

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.oscpad.model.Control
import kotlin.math.cos
import kotlin.math.sin

/** Everything a skin needs to draw one control; rebuilt only when the control or theme changes. */
class Kit(
    val control: Control,
    val color: Color,
    val theme: AppTheme,
    val text: TextMeasurer,
    val style: UiStyle,
) {
    val font = style.fontFamily
}

/**
 * Draws controls in one appearance style. All functions run in the draw phase, so a value
 * change only redraws the affected control.
 */
interface Skin {
    fun DrawScope.button(k: Kit, on: Boolean)
    fun DrawScope.toggle(k: Kit, on: Boolean)
    fun DrawScope.fader(k: Kit, v: Float, horizontal: Boolean)
    fun DrawScope.knob(k: Kit, v: Float)
    fun DrawScope.xy(k: Kit, x: Float, y: Float)
    fun DrawScope.radio(k: Kit, steps: Int, selected: Int, horizontal: Boolean)
    fun DrawScope.label(k: Kit, text: String)

    /** Frame and readout around a color wheel; the wheel and brightness bar are drawn on top. */
    fun DrawScope.colorFrame(k: Kit, current: Color)

    /** Static page decoration behind the controls ([controls] are their cells in pixels). */
    fun DrawScope.pageBackground(theme: AppTheme, controls: List<Rect>) = Unit

    /** Static decoration drawn over the controls (never intercepts touches). */
    fun DrawScope.pageOverlay(theme: AppTheme) = Unit
}

// ---- Shared drawing helpers ---------------------------------------------------------------

/** Measures text to fit inside [area] minus [pad], stepping the font down before ellipsizing. */
fun DrawScope.measureText(
    k: Kit,
    text: String,
    style: TextStyle,
    pad: Float,
    area: Rect = Rect(Offset.Zero, size),
    maxLines: Int = 1,
): TextLayoutResult? {
    val p = fitPad(pad, area)
    val maxWidth = (area.width - 2 * p).toInt()
    if (maxWidth <= 0 || text.isEmpty()) return null
    val base = style.copy(fontFamily = k.font)
    fun measure(s: TextStyle) = k.text.measure(
        text, s,
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
        constraints = Constraints(maxWidth = maxWidth),
    )
    var layout = measure(base)
    for (scale in floatArrayOf(0.85f, 0.7f)) {
        if (!layout.hasVisualOverflow) break
        layout = measure(base.copy(fontSize = base.fontSize * scale))
    }
    return layout
}

/** Padding shrinks on narrow controls so short names still fit. */
fun fitPad(pad: Float, area: Rect): Float = minOf(pad, area.width * 0.06f)

fun DrawScope.placeText(layout: TextLayoutResult, color: Color, align: Alignment, pad: Float, area: Rect = Rect(Offset.Zero, size)) {
    val p = fitPad(pad, area)
    val space = IntSize((area.width - 2 * p).toInt(), (area.height - 2 * p).toInt().coerceAtLeast(0))
    val offset = align.align(layout.size, space, layoutDirection)
    drawText(layout, color = color, topLeft = Offset(area.left + p + offset.x, area.top + p + offset.y))
}

/** Measures and draws text; returns its size (zero if it didn't fit). */
fun DrawScope.drawLabel(
    k: Kit,
    text: String,
    style: TextStyle,
    color: Color,
    align: Alignment,
    pad: Float,
    area: Rect = Rect(Offset.Zero, size),
    maxLines: Int = 1,
): IntSize {
    val layout = measureText(k, text, style, pad, area, maxLines) ?: return IntSize.Zero
    placeText(layout, color, align, pad, area)
    return layout.size
}

/**
 * Big display text (labels): prefers one line, shrinking the font first; wraps only at spaces
 * when that still doesn't fit, so words are never split across lines.
 */
fun DrawScope.drawFittedLabel(
    k: Kit,
    text: String,
    style: TextStyle,
    color: Color,
    align: Alignment,
    pad: Float,
    area: Rect = Rect(Offset.Zero, size),
): IntSize {
    val p = fitPad(pad, area)
    val maxWidth = (area.width - 2 * p).toInt()
    val maxHeight = area.height - 2 * p
    if (maxWidth <= 0 || text.isEmpty()) return IntSize.Zero
    val base = style.copy(fontFamily = k.font)
    val constraints = Constraints(maxWidth = maxWidth)
    fun sized(scale: Float) = base.copy(fontSize = base.fontSize * scale)
    fun oneLine(scale: Float) = k.text.measure(text, sized(scale), softWrap = false, maxLines = 1, constraints = constraints)
    fun twoLines(scale: Float) = k.text.measure(text, sized(scale), maxLines = 2, overflow = TextOverflow.Ellipsis, constraints = constraints)
    fun fits(t: TextLayoutResult) = !t.hasVisualOverflow && t.size.height <= maxHeight
    val layout = floatArrayOf(1f, 0.85f, 0.72f).map { oneLine(it) }.firstOrNull(::fits)
        ?: (if (' ' in text.trim()) floatArrayOf(1f, 0.85f, 0.72f, 0.6f).map { twoLines(it) }.firstOrNull(::fits) else null)
        ?: floatArrayOf(0.6f, 0.5f).map { oneLine(it) }.firstOrNull(::fits)
        ?: k.text.measure(text, sized(0.5f), overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = constraints)
    placeText(layout, color, align, pad, area)
    return layout.size
}

/** Rounded (or square) outline drawn fully inside [area]. */
fun DrawScope.outline(color: Color, width: Float, radius: Float, area: Rect = Rect(Offset.Zero, size)) {
    val half = width / 2f
    drawRoundRect(
        color,
        topLeft = Offset(area.left + half, area.top + half),
        size = Size(area.width - width, area.height - width),
        cornerRadius = CornerRadius((radius - half).coerceAtLeast(0f)),
        style = Stroke(width),
    )
}

/** Dark text on bright fills, theme text otherwise. */
fun textOn(k: Kit, fill: Color, onFill: Boolean, alpha: Float = 1f): Color =
    if (onFill && fill.luminance() > 0.45f) k.theme.background.copy(alpha = 0.9f) else k.theme.text.copy(alpha = alpha)

fun DrawScope.polar(center: Offset, radius: Float, degrees: Double): Offset {
    val rad = Math.toRadians(degrees)
    return center + Offset(cos(rad).toFloat() * radius, sin(rad).toFloat() * radius)
}

/** Standard knob arc geometry: centered square below [top] and above [bottom] reserved rows. */
fun DrawScope.knobArea(top: Float, bottom: Float, pad: Float): Pair<Offset, Float>? {
    val areaH = size.height - top - bottom - 2 * pad
    val dim = minOf(size.width - 2 * pad, areaH)
    if (dim <= 0f) return null
    return Offset(size.width / 2f, top + pad + areaH / 2f) to dim
}

// ---- Standard & Minimal ---------------------------------------------------------------------

/**
 * Flat skin. Standard: rounded corners, 2dp outlines, value handle. Minimal: sharp corners,
 * hairline outlines, uppercase captions, square markers (TouchOSC-like).
 */
class FlatSkin private constructor(private val radius: Dp, private val minimal: Boolean) : Skin {

    companion object {
        val Standard = FlatSkin(10.dp, minimal = false)
        val Minimal = FlatSkin(0.dp, minimal = true)
    }

    private val nameStyle = if (minimal) TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.08.em)
    else TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
    private val valueStyle = TextStyle(fontSize = 11.sp)

    private fun name(k: Kit) = if (minimal) k.control.name.uppercase() else k.control.name
    private fun DrawScope.r() = radius.toPx()
    private fun DrawScope.edge(k: Kit, strong: Boolean = true) =
        if (minimal) outline(k.color.copy(alpha = if (strong) 0.8f else 0.45f), 1.dp.toPx(), 0f)
        else outline(k.color, (if (strong) 2.dp else 1.5.dp).toPx(), r())

    override fun DrawScope.button(k: Kit, on: Boolean) {
        drawRoundRect(if (on) k.color else k.color.copy(alpha = k.theme.idleFillAlpha), cornerRadius = CornerRadius(r()))
        edge(k)
        drawLabel(k, name(k), nameStyle, if (on) k.theme.background else k.color, Alignment.Center, 4.dp.toPx())
    }

    override fun DrawScope.toggle(k: Kit, on: Boolean) {
        drawRoundRect(k.color.copy(alpha = k.theme.idleFillAlpha * 0.6f), cornerRadius = CornerRadius(r()))
        if (on) {
            val inset = if (minimal) 0f else 6.dp.toPx()
            drawRoundRect(
                k.color,
                topLeft = Offset(inset, inset),
                size = Size(size.width - 2 * inset, size.height - 2 * inset),
                cornerRadius = CornerRadius(if (minimal) 0f else 6.dp.toPx()),
            )
        }
        edge(k)
        drawLabel(k, name(k), nameStyle, if (on) k.theme.background else k.color, Alignment.Center, 8.dp.toPx())
    }

    override fun DrawScope.fader(k: Kit, v: Float, horizontal: Boolean) {
        val handle = (if (minimal) 2.dp else 3.dp).toPx()
        drawRect(k.color.copy(alpha = k.theme.idleFillAlpha))
        val fill = k.color.copy(alpha = k.theme.fillAlpha)
        val handleColor = if (minimal) k.color else k.theme.text
        if (horizontal) {
            val w = size.width * v
            drawRect(fill, size = Size(w, size.height))
            drawRect(handleColor, topLeft = Offset((w - handle / 2).coerceIn(0f, size.width - handle), 0f), size = Size(handle, size.height))
        } else {
            val h = size.height * v
            drawRect(fill, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
            drawRect(handleColor, topLeft = Offset(0f, (size.height - h - handle / 2).coerceIn(0f, size.height - handle)), size = Size(size.width, handle))
        }
        edge(k, strong = !minimal)
        val pad = 6.dp.toPx()
        measureText(k, name(k), nameStyle, pad)?.let { t ->
            val onFill = if (horizontal) size.width * v >= pad + t.size.width / 2f else size.height * (1f - v) <= pad + t.size.height / 2f
            placeText(t, textOn(k, fill, onFill), if (horizontal) Alignment.CenterStart else Alignment.TopCenter, pad)
        }
        measureText(k, k.control.format(v), valueStyle, pad)?.let { t ->
            val onFill = if (horizontal) size.width * v >= size.width - pad - t.size.width / 2f else size.height * v >= pad + t.size.height / 2f
            placeText(t, textOn(k, fill, onFill, 0.75f), if (horizontal) Alignment.CenterEnd else Alignment.BottomCenter, pad)
        }
    }

    override fun DrawScope.knob(k: Kit, v: Float) {
        if (!minimal) drawRoundRect(k.color.copy(alpha = k.theme.idleFillAlpha * 0.45f), cornerRadius = CornerRadius(r()))
        else edge(k, strong = false)
        val pad = 4.dp.toPx()
        val top = drawLabel(k, name(k), nameStyle, k.color, Alignment.TopCenter, pad).height + pad
        val bottom = drawLabel(k, k.control.format(v), valueStyle, k.color.copy(alpha = 0.85f), Alignment.BottomCenter, pad).height + pad
        val (center, dim) = knobArea(top, bottom, pad) ?: return
        val stroke = dim * (if (minimal) 0.06f else 0.12f)
        val d = dim - stroke
        val topLeft = center - Offset(d / 2f, d / 2f)
        val cap = if (minimal) StrokeCap.Butt else StrokeCap.Round
        drawArc(k.color.copy(alpha = 0.2f), 135f, 270f, false, topLeft, Size(d, d), style = Stroke(stroke, cap = cap))
        drawArc(k.color, 135f, 270f * v, false, topLeft, Size(d, d), style = Stroke(stroke, cap = cap))
        if (minimal) {
            drawCircle(k.color, stroke * 1.4f, polar(center, d / 2f, 135.0 + 270.0 * v))
        } else {
            drawLine(k.theme.text, center, polar(center, d / 2f - stroke, 135.0 + 270.0 * v), strokeWidth = stroke * 0.4f, cap = StrokeCap.Round)
        }
    }

    override fun DrawScope.xy(k: Kit, x: Float, y: Float) {
        drawRoundRect(k.color.copy(alpha = k.theme.idleFillAlpha * 0.6f), cornerRadius = CornerRadius(r()))
        val px = x * size.width
        val py = (1f - y) * size.height
        val line = k.color.copy(alpha = 0.5f)
        drawLine(line, Offset(px, 0f), Offset(px, size.height), 1.dp.toPx())
        drawLine(line, Offset(0f, py), Offset(size.width, py), 1.dp.toPx())
        if (minimal) {
            val s = 14.dp.toPx()
            drawRect(k.color, topLeft = Offset(px - s / 2, py - s / 2), size = Size(s, s))
        } else {
            drawCircle(k.color.copy(alpha = 0.35f), radius = 22.dp.toPx(), center = Offset(px, py))
            drawCircle(k.color, radius = 12.dp.toPx(), center = Offset(px, py))
        }
        edge(k, strong = false)
        val pad = 8.dp.toPx()
        drawLabel(k, name(k), nameStyle, k.color, Alignment.TopStart, pad)
        drawLabel(k, "x ${k.control.format(x)}   y ${k.control.format(y)}", valueStyle, k.color.copy(alpha = 0.8f), Alignment.BottomEnd, pad)
    }

    override fun DrawScope.radio(k: Kit, steps: Int, selected: Int, horizontal: Boolean) {
        val gap = (if (minimal) 2.dp else 4.dp).toPx()
        val corner = CornerRadius(if (minimal) 0f else 8.dp.toPx())
        val segW = if (horizontal) (size.width - gap * (steps - 1)) / steps else size.width
        val segH = if (horizontal) size.height else (size.height - gap * (steps - 1)) / steps
        val stroke = Stroke((if (minimal) 1.dp else 1.5.dp).toPx())
        for (i in 0 until steps) {
            val topLeft = if (horizontal) Offset(i * (segW + gap), 0f) else Offset(0f, i * (segH + gap))
            val seg = Size(segW, segH)
            drawRoundRect(if (i == selected) k.color else k.color.copy(alpha = k.theme.idleFillAlpha), topLeft, seg, corner)
            drawRoundRect(k.color.copy(alpha = if (minimal) 0.6f else 1f), topLeft, seg, corner, style = stroke)
        }
    }

    override fun DrawScope.label(k: Kit, text: String) {
        val fontPx = (size.height * 0.4f).coerceIn(10.sp.toPx(), 40.sp.toPx())
        val style = TextStyle(
            fontSize = fontPx.toSp(), lineHeight = 1.15.em,
            fontWeight = if (minimal) FontWeight.Normal else FontWeight.SemiBold, textAlign = TextAlign.Center,
        )
        drawFittedLabel(k, if (minimal) text.uppercase() else text, style, k.color, Alignment.Center, 2.dp.toPx())
    }

    override fun DrawScope.colorFrame(k: Kit, current: Color) {
        drawRoundRect(k.color.copy(alpha = k.theme.idleFillAlpha * 0.5f), cornerRadius = CornerRadius(r()))
        edge(k, strong = false)
        drawLabel(k, name(k), nameStyle, k.color, Alignment.TopStart, 6.dp.toPx(), Rect(0f, 0f, size.width - 28.dp.toPx(), size.height))
    }
}
