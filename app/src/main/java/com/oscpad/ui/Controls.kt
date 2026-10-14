package com.oscpad.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputEventHandler
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.oscpad.MainViewModel
import com.oscpad.model.Control
import com.oscpad.model.ControlType
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Renders one control in the active style. Values are read only inside draw and touch lambdas,
 * never during composition: a value change (touch or incoming OSC) just redraws this control's
 * own layer, without recomposition or layout of anything else.
 */
@Composable
fun ControlView(control: Control, vm: MainViewModel, interactive: Boolean, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    val style = LocalUiStyle.current
    val skin = style.skin
    val color = remember(control.color, theme) { theme.controlColor(control.color) }
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val kit = Kit(control, color, theme, measurer, style)
    val latest = rememberUpdatedState(control)
    val id = control.id
    val read = remember(vm, id) { { vm.values[id] ?: 0f } }
    val send = remember(vm) { { v: Float -> vm.onControlValue(latest.value, v) } }
    // Own (clipped) layer per control so redrawing one never re-records its neighbours.
    val clip = if (style == UiStyle.STANDARD) RoundedCornerShape(10.dp) else RectangleShape
    val base = modifier.graphicsLayer {
        shape = clip
        this.clip = true
    }
    when (control.type) {
        ControlType.BUTTON -> Box(
            base.touch(interactive, Unit) { trackTouch(onDown = { send(1f) }, onUp = { send(0f) }) }
                .drawBehind { with(skin) { button(kit, read() > 0.5f) } },
        )
        ControlType.TOGGLE -> Box(
            base.touch(interactive, Unit) { trackTouch(onDown = { send(if (read() > 0.5f) 0f else 1f) }) }
                .drawBehind { with(skin) { toggle(kit, read() > 0.5f) } },
        )
        ControlType.FADER -> Box(
            base.touch(interactive, Unit) {
                fun at(p: Offset): Float {
                    val horizontal = size.width > size.height
                    return (if (horizontal) p.x / size.width else 1f - p.y / size.height).coerceIn(0f, 1f)
                }
                trackTouch(onDown = { send(at(it)) }, onMove = { p, _ -> send(at(p)) })
            }.drawBehind { with(skin) { fader(kit, read(), size.width > size.height) } },
        )
        ControlType.KNOB -> Box(
            base.touch(interactive, Unit) {
                // Relative drag: up/right increases. Accumulate locally so fast moves aren't lost.
                var v = 0f
                trackTouch(
                    onDown = { v = read() },
                    onMove = { _, d ->
                        v = (v + (d.x - d.y) / (size.height.coerceAtLeast(1) * 1.2f)).coerceIn(0f, 1f)
                        send(v)
                    },
                )
            }.drawBehind { with(skin) { knob(kit, read()) } },
        )
        ControlType.RADIO -> {
            val steps = control.steps.coerceIn(2, 32)
            Box(
                base.touch(interactive, steps) {
                    fun at(p: Offset): Float {
                        val horizontal = size.width >= size.height
                        val frac = if (horizontal) p.x / size.width else p.y / size.height
                        return floor(frac * steps).toInt().coerceIn(0, steps - 1).toFloat()
                    }
                    trackTouch(onDown = { send(at(it)) }, onMove = { p, _ -> send(at(p)) })
                }.drawBehind { with(skin) { radio(kit, steps, read().roundToInt(), size.width >= size.height) } },
            )
        }
        ControlType.XY -> {
            val readY = remember(vm, id) { { vm.values[id + MainViewModel.Y_SUFFIX] ?: 0f } }
            val sendXY = remember(vm) { { x: Float, y: Float -> vm.onControlValue(latest.value, x, y) } }
            Box(
                base.touch(interactive, Unit) {
                    fun at(p: Offset) = sendXY((p.x / size.width).coerceIn(0f, 1f), (1f - p.y / size.height).coerceIn(0f, 1f))
                    trackTouch(onDown = { at(it) }, onMove = { p, _ -> at(p) })
                }.drawBehind { with(skin) { xy(kit, read(), readY()) } },
            )
        }
        ControlType.LABEL -> {
            val incoming = remember(vm, id) { { vm.labelTexts[id] } }
            Box(base.drawBehind { with(skin) { label(kit, incoming() ?: control.name) } })
        }
        ControlType.COLOR -> ColorWheelControl(kit, skin, vm, latest.value, interactive, base)
    }
}

// ---- Color wheel ----------------------------------------------------------------------------

private class ColorGeometry(val center: Offset, val radius: Float, val bar: Rect, val vertical: Boolean)

/** Wheel plus brightness bar: the bar goes beside the wheel on wide controls, below otherwise. */
private fun Density.colorGeometry(size: Size): ColorGeometry {
    val pad = 8.dp.toPx()
    val top = 22.dp.toPx() // caption row
    val barT = 22.dp.toPx()
    val gap = 8.dp.toPx()
    val avail = Rect(pad, top, size.width - pad, size.height - pad)
    val vertical = avail.width >= avail.height + barT + gap
    val bar: Rect
    val wheel: Rect
    if (vertical) {
        bar = Rect(avail.right - barT, avail.top, avail.right, avail.bottom)
        wheel = Rect(avail.left, avail.top, bar.left - gap, avail.bottom)
    } else {
        bar = Rect(avail.left, avail.bottom - barT, avail.right, avail.bottom)
        wheel = Rect(avail.left, avail.top, avail.right, bar.top - gap)
    }
    return ColorGeometry(wheel.center, (min(wheel.width, wheel.height) / 2f).coerceAtLeast(0f), bar, vertical)
}

/** Keeps the last rendered disc so recompositions (e.g. while dragging in edit mode) reuse it. */
private class DiscCache {
    private var diameter = -1
    private var bitmap: ImageBitmap? = null
    fun get(d: Int): ImageBitmap = bitmap?.takeIf { d == diameter } ?: hueSaturationDisc(d).also {
        bitmap = it
        diameter = d
    }
}

@Composable
private fun ColorWheelControl(kit: Kit, skin: Skin, vm: MainViewModel, control: Control, interactive: Boolean, modifier: Modifier) {
    val id = control.id
    val latest = rememberUpdatedState(control)
    val hue = remember(vm, id) { { (vm.values[id] ?: 0f) * 360f } }
    val sat = remember(vm, id) { { vm.values[id + MainViewModel.S_SUFFIX] ?: 0f } }
    val bright = remember(vm, id) { { vm.values[id + MainViewModel.V_SUFFIX] ?: 1f } }
    val send = remember(vm) { { h: Float, s: Float, v: Float -> vm.onControlColor(latest.value, h, s, v) } }
    val discs = remember { DiscCache() }
    Box(
        modifier
            .touch(interactive, Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val g = colorGeometry(Size(size.width.toFloat(), size.height.toFloat()))
                    val onBar = g.bar.inflate(6.dp.toPx()).contains(down.position)
                    fun apply(p: Offset) {
                        if (onBar) {
                            val v = if (g.vertical) 1f - (p.y - g.bar.top) / g.bar.height else (p.x - g.bar.left) / g.bar.width
                            send(hue(), sat(), v.coerceIn(0f, 1f))
                        } else {
                            val d = p - g.center
                            val angle = (atan2(d.y, d.x) * 180f / PI.toFloat()).let { if (it < 0f) it + 360f else it }
                            send(angle, (d.getDistance() / g.radius.coerceAtLeast(1f)).coerceIn(0f, 1f), bright())
                        }
                    }
                    apply(down.position)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: continue
                        if (!change.pressed) break
                        change.consume()
                        apply(change.position)
                    }
                }
            }
            .drawWithCache {
                val g = colorGeometry(size)
                val disc = if (g.radius >= 1f) discs.get((g.radius * 2).toInt()) else null
                onDrawBehind {
                    val h = hue()
                    val s = sat()
                    val v = bright()
                    val current = Color(hsvToArgb(h, s, v))
                    with(skin) { colorFrame(kit, current) }
                    if (disc != null) {
                        drawImage(disc, g.center - Offset(disc.width / 2f, disc.height / 2f))
                        drawCircle(Color.Black.copy(alpha = 1f - v), g.radius, g.center)
                        val rad = Math.toRadians(h.toDouble())
                        val marker = g.center + Offset(cos(rad).toFloat() * s * g.radius, sin(rad).toFloat() * s * g.radius)
                        drawCircle(Color.Black, 9.dp.toPx(), marker, style = Stroke(3.5.dp.toPx()))
                        drawCircle(Color.White, 9.dp.toPx(), marker, style = Stroke(1.5.dp.toPx()))
                    }
                    val full = Color(hsvToArgb(h, s, 1f))
                    val brush = if (g.vertical) Brush.verticalGradient(listOf(full, Color.Black), g.bar.top, g.bar.bottom)
                    else Brush.horizontalGradient(listOf(Color.Black, full), g.bar.left, g.bar.right)
                    val corner = CornerRadius(if (kit.style == UiStyle.STANDARD) 6.dp.toPx() else 0f)
                    drawRoundRect(brush, g.bar.topLeft, g.bar.size, corner)
                    val knob = 4.dp.toPx()
                    if (g.vertical) {
                        val y = g.bar.top + (1f - v) * g.bar.height
                        drawRect(Color.White, Offset(g.bar.left - 2.dp.toPx(), (y - knob / 2).coerceIn(g.bar.top, g.bar.bottom - knob)), Size(g.bar.width + 4.dp.toPx(), knob))
                    } else {
                        val x = g.bar.left + v * g.bar.width
                        drawRect(Color.White, Offset((x - knob / 2).coerceIn(g.bar.left, g.bar.right - knob), g.bar.top - 2.dp.toPx()), Size(knob, g.bar.height + 4.dp.toPx()))
                    }
                    // Current color swatch in the caption row (top-right).
                    val sw = 14.dp.toPx()
                    drawRect(current, Offset(size.width - sw - 8.dp.toPx(), 5.dp.toPx()), Size(sw, sw))
                    drawRect(kit.color, Offset(size.width - sw - 8.dp.toPx(), 5.dp.toPx()), Size(sw, sw), style = Stroke(1.dp.toPx()))
                }
            },
    )
}

// ---- Touch ----------------------------------------------------------------------------------

/**
 * Tracks the first finger that lands on a control until it lifts. Each control owns its own
 * pointer, so several controls can be played at once (multitouch).
 */
private suspend fun PointerInputScope.trackTouch(
    onDown: (Offset) -> Unit = {},
    onMove: (position: Offset, delta: Offset) -> Unit = { _, _ -> },
    onUp: () -> Unit = {},
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        onDown(down.position)
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                if (!change.pressed) {
                    change.consume()
                    break
                }
                val delta = change.position - change.previousPosition
                if (delta != Offset.Zero) {
                    change.consume()
                    onMove(change.position, delta)
                }
            }
        } finally {
            onUp()
        }
    }
}

private fun Modifier.touch(enabled: Boolean, key: Any?, handler: PointerInputEventHandler): Modifier =
    if (enabled) pointerInput(key, handler) else this
