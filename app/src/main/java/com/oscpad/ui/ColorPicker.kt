package com.oscpad.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * HSV color wheel (hue around, saturation outward) with a brightness bar and a hex field.
 * Hue and saturation are kept locally so they survive passing through black or gray.
 */
@Composable
fun ColorWheelPicker(color: Int, onColorChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var hue by remember { mutableFloatStateOf(0f) }
    var sat by remember { mutableFloatStateOf(0f) }
    var value by remember { mutableFloatStateOf(1f) }
    val emit by rememberUpdatedState(onColorChange)

    LaunchedEffect(color) {
        if (hsvToArgb(hue, sat, value) != color) {
            val hsv = argbToHsv(color)
            hue = hsv[0]
            sat = hsv[1]
            value = hsv[2]
        }
    }

    fun update(h: Float = hue, s: Float = sat, v: Float = value) {
        hue = h
        sat = s
        value = v
        emit(hsvToArgb(h, s, v))
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(184.dp).pointerInput(Unit) {
                dragAnywhere { p ->
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val d = p - c
                    val radius = min(size.width, size.height) / 2f
                    val angle = (atan2(d.y, d.x) * 180f / PI.toFloat()).let { if (it < 0f) it + 360f else it }
                    update(h = angle, s = (d.getDistance() / radius).coerceIn(0f, 1f))
                }
            }.drawWithCache {
                // Exact HSV disc, rendered once per size; brightness is a cheap overlay on top.
                val diameter = size.minDimension.toInt()
                val disc = hueSaturationDisc(diameter)
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                onDrawBehind {
                    val r = diameter / 2f
                    drawImage(disc, topLeft)
                    drawCircle(Color.Black.copy(alpha = 1f - value), r)
                    val rad = Math.toRadians(hue.toDouble())
                    val marker = center + Offset(cos(rad).toFloat() * sat * r, sin(rad).toFloat() * sat * r)
                    drawCircle(Color.Black, 9.dp.toPx(), marker, style = Stroke(3.5.dp.toPx()))
                    drawCircle(Color.White, 9.dp.toPx(), marker, style = Stroke(1.5.dp.toPx()))
                }
            },
        )
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Brightness", style = MaterialTheme.typography.labelMedium)
            Canvas(
                Modifier.fillMaxWidth().height(32.dp).pointerInput(Unit) {
                    dragAnywhere { p -> update(v = (p.x / size.width).coerceIn(0f, 1f)) }
                },
            ) {
                val r = CornerRadius(8.dp.toPx())
                drawRoundRect(Brush.horizontalGradient(listOf(Color.Black, Color(hsvToArgb(hue, sat, 1f)))), cornerRadius = r)
                val x = value * size.width
                drawRoundRect(
                    Color.White,
                    topLeft = Offset((x - 3.dp.toPx()).coerceIn(0f, size.width - 6.dp.toPx()), 0f),
                    size = androidx.compose.ui.geometry.Size(6.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(2.dp.toPx()),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(40.dp)
                        .background(Color(color), RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                )
                HexField(color, Modifier.weight(1f)) { emit(it) }
            }
        }
    }
}

@Composable
private fun HexField(color: Int, modifier: Modifier, onColor: (Int) -> Unit) {
    var text by remember(color) { mutableStateOf(String.format(Locale.US, "#%06X", color and 0xFFFFFF)) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            val hex = input.trim().removePrefix("#")
            if (hex.length == 6) hex.toLongOrNull(16)?.let { onColor((0xFF000000 or it).toInt()) }
        },
        label = { Text("Hex") },
        singleLine = true,
        modifier = modifier,
        isError = text.trim().removePrefix("#").let { it.length != 6 || it.toLongOrNull(16) == null },
    )
}

/** Hue around, saturation outward, full brightness; anti-aliased edge, transparent outside. */
fun hueSaturationDisc(diameter: Int): ImageBitmap {
    val d = diameter.coerceAtLeast(1)
    val r = d / 2f
    val pixels = IntArray(d * d)
    for (y in 0 until d) for (x in 0 until d) {
        val dx = x + 0.5f - r
        val dy = y + 0.5f - r
        val dist = sqrt(dx * dx + dy * dy)
        if (dist > r) continue
        val hue = (atan2(dy, dx) * 180f / PI.toFloat()).let { if (it < 0f) it + 360f else it }
        val rgb = hsvToArgb(hue, dist / r, 1f) and 0xFFFFFF
        val alpha = ((r - dist).coerceIn(0f, 1f) * 255f).toInt()
        pixels[y * d + x] = (alpha shl 24) or rgb
    }
    return Bitmap.createBitmap(pixels, d, d, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Reports the pointer position on touch-down and while dragging, consuming the gesture. */
private suspend fun PointerInputScope.dragAnywhere(onPosition: (Offset) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        onPosition(down.position)
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: continue
            if (!change.pressed) break
            change.consume()
            onPosition(change.position)
        }
    }
}
