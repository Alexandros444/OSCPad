package com.oscpad.ui

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Retro terminal: monospace, square cells, phosphor glow, inverse video for active states,
 * segmented bars and LED rings instead of smooth fills.
 */
object TerminalSkin : Skin {

    private fun glow(color: Color) = Shadow(color.copy(alpha = 0.65f), Offset.Zero, 8f)
    private fun nameStyle(k: Kit, color: Color = k.color) = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, shadow = glow(color))
    private val valueStyle = TextStyle(fontSize = 11.sp)

    private fun DrawScope.box(k: Kit, double: Boolean = false, alpha: Float = 1f) {
        outline(k.color.copy(alpha = alpha), 1.5.dp.toPx(), 0f)
        if (double) {
            val i = 4.dp.toPx()
            outline(k.color.copy(alpha = alpha * 0.6f), 1.dp.toPx(), 0f, Rect(i, i, size.width - i, size.height - i))
        }
    }

    override fun DrawScope.button(k: Kit, on: Boolean) {
        if (on) drawRect(k.color, topLeft = Offset(4.dp.toPx(), 4.dp.toPx()), size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()))
        box(k, double = true)
        val color = if (on) k.theme.background else k.color
        drawLabel(k, k.control.name.uppercase(), nameStyle(k, color).copy(shadow = if (on) null else glow(color)), color, Alignment.Center, 8.dp.toPx())
    }

    override fun DrawScope.toggle(k: Kit, on: Boolean) {
        if (on) drawRect(k.color.copy(alpha = 0.18f))
        box(k, alpha = if (on) 1f else 0.6f)
        drawLabel(k, (if (on) "[X] " else "[ ] ") + k.control.name.uppercase(), nameStyle(k), k.color.copy(alpha = if (on) 1f else 0.7f), Alignment.Center, 6.dp.toPx())
    }

    override fun DrawScope.fader(k: Kit, v: Float, horizontal: Boolean) {
        box(k, alpha = 0.7f)
        val pad = 6.dp.toPx()
        val nameH = drawLabel(k, k.control.name.uppercase(), nameStyle(k), k.color, if (horizontal) Alignment.CenterStart else Alignment.TopCenter, pad)
        val valueT = "${(v * 100).roundToInt().toString().padStart(3, '0')}%"
        val valueH = drawLabel(k, valueT, valueStyle, k.color.copy(alpha = 0.8f), if (horizontal) Alignment.CenterEnd else Alignment.BottomCenter, pad)
        // Segmented bar between the caption and the readout.
        val cell = 6.dp.toPx()
        val gap = 2.dp.toPx()
        val area = if (horizontal) Rect(pad * 2 + nameH.width, pad * 1.5f, size.width - pad * 2 - valueH.width, size.height - pad * 1.5f)
        else Rect(pad * 1.5f, pad * 2 + nameH.height, size.width - pad * 1.5f, size.height - pad * 2 - valueH.height)
        val length = if (horizontal) area.width else area.height
        if (length <= cell) return
        val count = floor((length + gap) / (cell + gap)).toInt()
        val lit = (v * count).roundToInt()
        for (i in 0 until count) {
            val on = i < lit
            val c = if (on) k.color else k.color.copy(alpha = 0.12f)
            if (horizontal) {
                drawRect(c, topLeft = Offset(area.left + i * (cell + gap), area.top), size = Size(cell, area.height))
            } else {
                drawRect(c, topLeft = Offset(area.left, area.bottom - (i + 1) * (cell + gap) + gap), size = Size(area.width, cell))
            }
        }
    }

    override fun DrawScope.knob(k: Kit, v: Float) {
        box(k, alpha = 0.5f)
        val pad = 5.dp.toPx()
        val top = drawLabel(k, k.control.name.uppercase(), nameStyle(k), k.color, Alignment.TopCenter, pad).height + pad
        val (center, dim) = knobArea(top, 0f, pad) ?: return
        // LED ring: discrete ticks over 270 degrees.
        val ticks = 21
        val lit = (v * (ticks - 1)).roundToInt()
        val outer = dim / 2f
        val inner = outer * 0.72f
        val stroke = max(2f, dim * 0.035f)
        for (i in 0 until ticks) {
            val deg = 135.0 + 270.0 * i / (ticks - 1)
            val c = if (i <= lit) k.color else k.color.copy(alpha = 0.15f)
            drawLine(c, polar(center, inner, deg), polar(center, outer, deg), stroke, cap = StrokeCap.Square)
        }
        val box = Rect(center.x - inner, center.y - inner * 0.5f, center.x + inner, center.y + inner * 0.5f)
        drawLabel(k, k.control.format(v), valueStyle.copy(fontSize = 12.sp, shadow = glow(k.color)), k.color, Alignment.Center, 0f, box)
    }

    override fun DrawScope.xy(k: Kit, x: Float, y: Float) {
        box(k, alpha = 0.7f)
        val dot = k.color.copy(alpha = 0.25f)
        for (i in 1 until 10) for (j in 1 until 10) {
            drawCircle(dot, 1.dp.toPx(), Offset(size.width * i / 10f, size.height * j / 10f))
        }
        val px = x * size.width
        val py = (1f - y) * size.height
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        val line = k.color.copy(alpha = 0.55f)
        drawLine(line, Offset(px, 0f), Offset(px, size.height), 1.dp.toPx(), pathEffect = dash)
        drawLine(line, Offset(0f, py), Offset(size.width, py), 1.dp.toPx(), pathEffect = dash)
        val arm = 10.dp.toPx()
        val w = 2.5.dp.toPx()
        drawLine(k.color, Offset(px - arm, py), Offset(px + arm, py), w)
        drawLine(k.color, Offset(px, py - arm), Offset(px, py + arm), w)
        val pad = 7.dp.toPx()
        drawLabel(k, k.control.name.uppercase(), nameStyle(k), k.color, Alignment.TopStart, pad)
        drawLabel(k, "X:${k.control.format(x)} Y:${k.control.format(y)}", valueStyle, k.color.copy(alpha = 0.8f), Alignment.BottomEnd, pad)
    }

    override fun DrawScope.radio(k: Kit, steps: Int, selected: Int, horizontal: Boolean) {
        val gap = 3.dp.toPx()
        val segW = if (horizontal) (size.width - gap * (steps - 1)) / steps else size.width
        val segH = if (horizontal) size.height else (size.height - gap * (steps - 1)) / steps
        for (i in 0 until steps) {
            val r = if (horizontal) Rect(i * (segW + gap), 0f, i * (segW + gap) + segW, segH)
            else Rect(0f, i * (segH + gap), segW, i * (segH + gap) + segH)
            val on = i == selected
            if (on) drawRect(k.color, r.topLeft, r.size)
            outline(k.color.copy(alpha = if (on) 1f else 0.55f), 1.5.dp.toPx(), 0f, r)
            val c = if (on) k.theme.background else k.color.copy(alpha = 0.8f)
            drawLabel(k, "${i + 1}", valueStyle.copy(fontSize = 13.sp), c, Alignment.Center, 0f, r)
        }
    }

    override fun DrawScope.label(k: Kit, text: String) {
        val fontPx = (size.height * 0.36f).coerceIn(10.sp.toPx(), 34.sp.toPx())
        drawFittedLabel(
            k, "> ${text.uppercase()}_", TextStyle(fontSize = fontPx.toSp(), shadow = glow(k.color)),
            k.color, Alignment.CenterStart, 4.dp.toPx(),
        )
    }

    override fun DrawScope.colorFrame(k: Kit, current: Color) {
        box(k, alpha = 0.7f)
        drawLabel(k, k.control.name.uppercase(), nameStyle(k), k.color, Alignment.TopStart, 6.dp.toPx())
    }

    /** CRT scanlines across the page. Static, so it is drawn once per size. */
    override fun DrawScope.pageOverlay(theme: AppTheme) {
        val step = 3.dp.toPx()
        val line = Color.Black.copy(alpha = 0.22f)
        var y = 0f
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
    }
}
