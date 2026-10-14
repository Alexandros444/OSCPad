package com.oscpad.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Overlap-safe placement for edit-mode drags. Positions are page fractions; touching edges is
 * allowed, overlapping is not. Controls a dragged control already overlaps when the drag
 * starts are not obstacles, so existing overlaps can be pulled apart.
 */
object Collision {

    private const val EPS = 1e-4f

    fun overlaps(a: Control, b: Control): Boolean =
        a.x < b.x + b.w - EPS && b.x < a.x + a.w - EPS &&
            a.y < b.y + b.h - EPS && b.y < a.y + a.h - EPS

    /** What may block [start] during a drag: everything it doesn't already overlap. */
    fun obstacles(start: Control, others: List<Control>): List<Control> = others.filter { !overlaps(start, it) }

    private fun free(c: Control, obstacles: List<Control>) = obstacles.none { overlaps(c, it) }

    private fun spansOverlap(a: Float, aLen: Float, b: Float, bLen: Float) = a < b + bLen - EPS && b < a + aLen - EPS

    /**
     * Moves from [last] (a valid placement) toward [desired]. A free [desired] is taken as is, so a
     * control can jump past a neighbour into open space; otherwise it slides along the blocking
     * edges and stops flush against them.
     */
    fun move(last: Control, desired: Control, obstacles: List<Control>): Control {
        if (free(desired, obstacles)) return desired
        val xFirst = slideX(last, desired.x, obstacles).let { slideY(it, desired.y, obstacles) }
        val yFirst = slideY(last, desired.y, obstacles).let { slideX(it, desired.x, obstacles) }
        return listOf(xFirst, yFirst)
            .filter { free(it, obstacles) }
            .minByOrNull { abs(it.x - desired.x) + abs(it.y - desired.y) }
            ?: last
    }

    /** Resizing from the bottom-right corner: growing edges stop at neighbours; shrinking is always fine. */
    fun resize(last: Control, desired: Control, obstacles: List<Control>): Control {
        if (free(desired, obstacles)) return desired
        val wFirst = growW(last, desired.w, obstacles).let { growH(it, desired.h, obstacles) }
        val hFirst = growH(last, desired.h, obstacles).let { growW(it, desired.w, obstacles) }
        return listOf(wFirst, hFirst)
            .filter { free(it, obstacles) && it.w >= MIN_SIZE - EPS && it.h >= MIN_SIZE - EPS }
            .minByOrNull { abs(it.w - desired.w) + abs(it.h - desired.h) }
            ?: last
    }

    private fun slideX(c: Control, toX: Float, obstacles: List<Control>): Control {
        var x = toX
        for (o in obstacles) {
            if (!spansOverlap(c.y, c.h, o.y, o.h)) continue
            if (toX > c.x && o.x >= c.x + c.w - EPS) x = min(x, o.x - c.w)
            if (toX < c.x && o.x + o.w <= c.x + EPS) x = max(x, o.x + o.w)
        }
        return c.copy(x = x)
    }

    private fun slideY(c: Control, toY: Float, obstacles: List<Control>): Control {
        var y = toY
        for (o in obstacles) {
            if (!spansOverlap(c.x, c.w, o.x, o.w)) continue
            if (toY > c.y && o.y >= c.y + c.h - EPS) y = min(y, o.y - c.h)
            if (toY < c.y && o.y + o.h <= c.y + EPS) y = max(y, o.y + o.h)
        }
        return c.copy(y = y)
    }

    private fun growW(c: Control, toW: Float, obstacles: List<Control>): Control {
        if (toW <= c.w) return c.copy(w = toW)
        var right = c.x + toW
        for (o in obstacles) {
            if (spansOverlap(c.y, c.h, o.y, o.h) && o.x >= c.x + c.w - EPS) right = min(right, o.x)
        }
        return c.copy(w = right - c.x)
    }

    private fun growH(c: Control, toH: Float, obstacles: List<Control>): Control {
        if (toH <= c.h) return c.copy(h = toH)
        var bottom = c.y + toH
        for (o in obstacles) {
            if (spansOverlap(c.x, c.w, o.x, o.w) && o.y >= c.y + c.h - EPS) bottom = min(bottom, o.y)
        }
        return c.copy(h = bottom - c.y)
    }

    /**
     * Nearest free grid position for a new control (Add, Duplicate), searched outward from where
     * it would normally go. Returns [c] unchanged if the page has no room for it.
     */
    fun freeSpot(c: Control, others: List<Control>, divisions: Int = FINE_GRID): Control {
        if (free(c, others)) return c
        val step = 1f / divisions
        val maxX = ((1f - c.w) / step).toInt()
        val maxY = ((1f - c.h) / step).toInt()
        val startX = (c.x / step).roundToInt()
        val startY = (c.y / step).roundToInt()
        val candidates = ArrayList<Pair<Int, Int>>((maxX + 1) * (maxY + 1))
        for (i in 0..maxX) for (j in 0..maxY) candidates += i to j
        candidates.sortBy { (i, j) -> (i - startX) * (i - startX) + (j - startY) * (j - startY) }
        for ((i, j) in candidates) {
            val candidate = c.copy(x = i * step, y = j * step)
            if (free(candidate, others)) return candidate
        }
        return c
    }
}
