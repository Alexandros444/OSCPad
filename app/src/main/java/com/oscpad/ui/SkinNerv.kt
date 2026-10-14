package com.oscpad.ui

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Evangelion / NERV terminal look: framed panels with corner brackets and a label tab,
 * condensed type, slatted level bars, green LCD readouts, hexagons and hazard stripes.
 * Frame color comes from the control; readouts use the palette's secondary (green).
 */
object NervSkin : Skin {

    private val TabStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.04.em)
    private val BigStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
    private val ReadoutStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)

    // ---- Building blocks ---------------------------------------------------------------------

    private fun DrawScope.panel(k: Kit, fill: Float = k.theme.idleFillAlpha) {
        drawRect(k.color.copy(alpha = fill))
        outline(k.color.copy(alpha = 0.7f), 1.dp.toPx(), 0f)
        val l = minOf(9.dp.toPx(), size.minDimension / 3f)
        val t = 2.dp.toPx()
        val w = size.width
        val h = size.height
        val c = k.color
        drawRect(c, Offset(0f, 0f), Size(l, t)); drawRect(c, Offset(0f, 0f), Size(t, l))
        drawRect(c, Offset(w - l, 0f), Size(l, t)); drawRect(c, Offset(w - t, 0f), Size(t, l))
        drawRect(c, Offset(0f, h - t), Size(l, t)); drawRect(c, Offset(0f, h - l), Size(t, l))
        drawRect(c, Offset(w - l, h - t), Size(l, t)); drawRect(c, Offset(w - t, h - l), Size(t, l))
    }

    /** Small caption tab at the top-left ("▌Clock"); returns the y below it. */
    private fun DrawScope.tab(k: Kit, text: String): Float {
        val pad = 5.dp.toPx()
        val bar = 2.dp.toPx()
        val area = Rect(pad + bar + 3.dp.toPx(), pad, size.width - pad, size.height)
        val t = measureText(k, text, TabStyle, 0f, area) ?: return pad
        drawRect(k.color, Offset(pad, pad + 1.dp.toPx()), Size(bar, t.size.height - 2.dp.toPx()))
        placeText(t, k.color, Alignment.TopStart, 0f, area)
        return pad + t.size.height + 3.dp.toPx()
    }

    /** Green LCD-style readout box. */
    private fun DrawScope.readout(k: Kit, text: String, area: Rect, lit: Boolean = true, style: TextStyle = ReadoutStyle) {
        if (area.width <= 0f || area.height <= 0f) return
        val g = k.theme.secondary
        drawRect(g.copy(alpha = if (lit) 0.18f else 0.06f), area.topLeft, area.size)
        outline(g.copy(alpha = if (lit) 0.6f else 0.25f), 1.dp.toPx(), 0f, area)
        drawLabel(k, text, style, if (lit) g else g.copy(alpha = 0.4f), Alignment.Center, 2.dp.toPx(), area)
    }

    private fun DrawScope.hatch(color: Color, area: Rect, spacing: Float, width: Float) {
        clipRect(area.left, area.top, area.right, area.bottom) {
            var x = area.left - area.height
            while (x < area.right) {
                drawLine(color, Offset(x, area.bottom), Offset(x + area.height, area.top), width)
                x += spacing
            }
        }
    }

    private fun hexPath(center: Offset, r: Float): Path = Path().apply {
        for (i in 0 until 6) {
            val a = Math.toRadians(60.0 * i - 90.0)
            val p = Offset(center.x + r * kotlin.math.cos(a).toFloat(), center.y + r * kotlin.math.sin(a).toFloat())
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }

    // ---- Controls ----------------------------------------------------------------------------

    override fun DrawScope.button(k: Kit, on: Boolean) {
        panel(k)
        val inset = 4.dp.toPx()
        val inner = Rect(inset, inset, size.width - inset, size.height - inset)
        if (on) {
            drawRect(k.color.copy(alpha = 0.9f), inner.topLeft, inner.size)
            hatch(k.theme.background.copy(alpha = 0.22f), inner, 9.dp.toPx(), 3.dp.toPx())
        }
        drawLabel(k, k.control.name.uppercase(), BigStyle, if (on) k.theme.background else k.color, Alignment.Center, 8.dp.toPx())
    }

    override fun DrawScope.toggle(k: Kit, on: Boolean) {
        panel(k, fill = if (on) k.theme.idleFillAlpha * 1.6f else k.theme.idleFillAlpha)
        val top = tab(k, k.control.name.uppercase())
        // Status LED, top-right.
        val led = 6.dp.toPx()
        drawRect(if (on) k.theme.secondary else k.theme.secondary.copy(alpha = 0.15f), Offset(size.width - led - 7.dp.toPx(), 7.dp.toPx()), Size(led, led))
        val pad = 6.dp.toPx()
        val h = minOf(size.height - top - pad, 30.dp.toPx())
        val area = Rect(pad, top + (size.height - top - pad - h) / 2f, size.width - pad, top + (size.height - top - pad + h) / 2f)
        readout(k, if (on) "ACTIVE" else "OFF", area, lit = on)
    }

    override fun DrawScope.fader(k: Kit, v: Float, horizontal: Boolean) {
        panel(k)
        val top = tab(k, k.control.name.uppercase())
        val pad = 6.dp.toPx()
        val readH = 20.dp.toPx()
        val readW = 52.dp.toPx()
        val valueText = k.control.format(v)
        val slat = 5.dp.toPx()
        val gap = 3.dp.toPx()
        val fill = k.color.copy(alpha = k.theme.fillAlpha)
        val dim = k.color.copy(alpha = 0.10f)
        if (horizontal) {
            val read = Rect(size.width - pad - readW, top, size.width - pad, (top + readH).coerceAtMost(size.height - pad))
            readout(k, valueText, read)
            val area = Rect(pad, top, read.left - pad, size.height - pad)
            if (area.width <= slat) return
            val count = floor((area.width + gap) / (slat + gap)).toInt()
            val lit = (v * count).roundToInt()
            for (i in 0 until count) {
                val c = when {
                    i == lit - 1 -> k.theme.secondary
                    i < lit -> fill
                    else -> dim
                }
                drawRect(c, Offset(area.left + i * (slat + gap), area.top), Size(slat, area.height))
            }
        } else {
            val read = Rect(pad, size.height - pad - readH, size.width - pad, size.height - pad)
            readout(k, valueText, read)
            val scaleW = 6.dp.toPx()
            val area = Rect(pad + scaleW + 3.dp.toPx(), top, size.width - pad, read.top - 4.dp.toPx())
            if (area.height <= slat) return
            // Scale ticks every 10 %.
            for (i in 0..10) {
                val y = area.bottom - area.height * i / 10f
                drawLine(k.color.copy(alpha = if (i % 5 == 0) 0.9f else 0.45f), Offset(pad, y), Offset(pad + if (i % 5 == 0) scaleW else scaleW / 2f, y), 1.dp.toPx())
            }
            val count = floor((area.height + gap) / (slat + gap)).toInt()
            val lit = (v * count).roundToInt()
            for (i in 0 until count) {
                val c = when {
                    i == lit - 1 -> k.theme.secondary
                    i < lit -> fill
                    else -> dim
                }
                drawRect(c, Offset(area.left, area.bottom - (i + 1) * (slat + gap) + gap), Size(area.width, slat))
            }
        }
    }

    override fun DrawScope.knob(k: Kit, v: Float) {
        panel(k)
        val top = tab(k, k.control.name.uppercase())
        val (center, dim) = knobArea(top, 0f, 6.dp.toPx()) ?: return
        val r = dim / 2f
        val ring = r * 0.12f
        val arcR = r - ring / 2f - r * 0.06f
        val arcBox = Size(arcR * 2, arcR * 2)
        val arcTopLeft = center - Offset(arcR, arcR)
        // Outer ticks.
        for (i in 0..10) {
            val deg = 135.0 + 27.0 * i
            drawLine(k.color.copy(alpha = 0.6f), polar(center, r * 0.98f, deg), polar(center, r * 0.9f, deg), 1.dp.toPx())
        }
        drawArc(k.color.copy(alpha = 0.18f), 135f, 270f, false, arcTopLeft, arcBox, style = Stroke(ring, cap = StrokeCap.Butt))
        drawArc(k.color, 135f, 270f * v, false, arcTopLeft, arcBox, style = Stroke(ring, cap = StrokeCap.Butt))
        // Inner dial: dark disc with a green ring and the value.
        val innerR = arcR - ring
        drawCircle(k.theme.background, innerR, center)
        drawCircle(k.theme.secondary.copy(alpha = 0.7f), innerR, center, style = Stroke(1.5.dp.toPx()))
        drawCircle(k.theme.secondary.copy(alpha = 0.3f), innerR * 0.8f, center, style = Stroke(1.dp.toPx()))
        val box = Rect(center.x - innerR * 0.75f, center.y - innerR * 0.5f, center.x + innerR * 0.75f, center.y + innerR * 0.5f)
        drawLabel(k, k.control.format(v), ReadoutStyle, k.theme.secondary, Alignment.Center, 0f, box)
    }

    override fun DrawScope.xy(k: Kit, x: Float, y: Float) {
        panel(k, fill = k.theme.idleFillAlpha * 0.7f)
        val grid = k.color.copy(alpha = 0.18f)
        for (i in 1..3) {
            drawLine(grid, Offset(size.width * i / 4f, 0f), Offset(size.width * i / 4f, size.height), 1.dp.toPx())
            drawLine(grid, Offset(0f, size.height * i / 4f), Offset(size.width, size.height * i / 4f), 1.dp.toPx())
        }
        tab(k, k.control.name.uppercase())
        val px = x * size.width
        val py = (1f - y) * size.height
        val g = k.theme.secondary
        drawLine(g.copy(alpha = 0.6f), Offset(px, 0f), Offset(px, size.height), 1.dp.toPx())
        drawLine(g.copy(alpha = 0.6f), Offset(0f, py), Offset(size.width, py), 1.dp.toPx())
        val r = 15.dp.toPx()
        drawCircle(g, r, Offset(px, py), style = Stroke(2.dp.toPx()))
        for (deg in listOf(0.0, 90.0, 180.0, 270.0)) {
            drawLine(g, polar(Offset(px, py), r * 0.55f, deg), polar(Offset(px, py), r * 1.45f, deg), 2.dp.toPx())
        }
        drawCircle(k.color, 3.dp.toPx(), Offset(px, py))
        val pad = 6.dp.toPx()
        val read = Rect(size.width - pad - 118.dp.toPx(), size.height - pad - 20.dp.toPx(), size.width - pad, size.height - pad)
        readout(k, "X ${k.control.format(x)}  Y ${k.control.format(y)}", read, style = ReadoutStyle.copy(fontSize = 11.sp))
    }

    override fun DrawScope.radio(k: Kit, steps: Int, selected: Int, horizontal: Boolean) {
        val cellW = if (horizontal) size.width / steps else size.width
        val cellH = if (horizontal) size.height else size.height / steps
        // Pointy-top hexagon: width = sqrt(3) * r, height = 2r.
        val r = minOf(cellW / sqrt(3f), cellH / 2f) * 0.94f
        val stroke = Stroke(1.5.dp.toPx())
        for (i in 0 until steps) {
            val center = if (horizontal) Offset(cellW * (i + 0.5f), size.height / 2f) else Offset(size.width / 2f, cellH * (i + 0.5f))
            val path = hexPath(center, r)
            val on = i == selected
            drawPath(path, if (on) k.theme.secondary else k.color.copy(alpha = k.theme.idleFillAlpha))
            drawPath(path, if (on) k.theme.secondary else k.color.copy(alpha = 0.75f), style = stroke)
            val box = Rect(center.x - r * 0.7f, center.y - r * 0.6f, center.x + r * 0.7f, center.y + r * 0.6f)
            drawLabel(k, "${i + 1}", ReadoutStyle, if (on) k.theme.background else k.color, Alignment.Center, 0f, box)
        }
    }

    override fun DrawScope.label(k: Kit, text: String) {
        val showsIncoming = text != k.control.name
        val top = if (showsIncoming) tab(k, k.control.name.uppercase()) else 0f
        val area = Rect(0f, top, size.width, size.height)
        val fontPx = (area.height * 0.5f).coerceIn(12.sp.toPx(), 48.sp.toPx())
        drawFittedLabel(
            k, text.uppercase(),
            TextStyle(fontSize = fontPx.toSp(), lineHeight = 1.0.em, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            k.color, Alignment.Center, 4.dp.toPx(), area,
        )
    }

    override fun DrawScope.colorFrame(k: Kit, current: Color) {
        panel(k, fill = k.theme.idleFillAlpha * 0.6f)
        tab(k, k.control.name.uppercase())
    }

    /** Faint honeycomb plus "+" crosshairs at every control corner, like a NERV status board. */
    override fun DrawScope.pageBackground(theme: AppTheme, controls: List<Rect>) {
        val s = 24.dp.toPx() // hexagon radius
        val hexW = sqrt(3f) * s
        val rowH = 1.5f * s
        val line = theme.accent.copy(alpha = 0.06f)
        val stroke = Stroke(1.dp.toPx())
        var row = 0
        var cy = 0f
        while (cy < size.height + s) {
            var cx = if (row % 2 == 0) 0f else hexW / 2f
            while (cx < size.width + hexW) {
                drawPath(hexPath(Offset(cx, cy), s), line, style = stroke)
                cx += hexW
            }
            cy += rowH
            row++
        }
        val arm = 5.dp.toPx()
        val cross = theme.accent.copy(alpha = 0.7f)
        val w = 1.5.dp.toPx()
        // Corners of neighbouring controls that nearly coincide share one crosshair.
        val merge = 14.dp.toPx()
        val drawn = ArrayList<Offset>()
        for (r in controls) {
            for (p in listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight)) {
                if (drawn.any { (it - p).getDistance() < merge }) continue
                drawn += p
                drawLine(cross, Offset(p.x - arm, p.y), Offset(p.x + arm, p.y), w)
                drawLine(cross, Offset(p.x, p.y - arm), Offset(p.x, p.y + arm), w)
            }
        }
    }
}
