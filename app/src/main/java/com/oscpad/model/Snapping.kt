package com.oscpad.model

import kotlin.math.abs
import kotlin.math.roundToInt

/** A snapped control plus the alignment guides to draw (page fractions; x = vertical lines). */
data class SnapResult(val control: Control, val guidesX: List<Float>, val guidesY: List<Float>)

/**
 * Edit-mode snapping. Edges and centers of other controls (and the page center/edges) attract
 * the dragged control when within the tolerance; otherwise it snaps to the grid. Any edge that
 * ends up aligned with another element produces a guide line.
 */
object Snapper {

    private const val EPS = 1e-4f

    fun move(
        start: Control, dx: Float, dy: Float, others: List<Control>,
        grid: Int, tolX: Float, tolY: Float,
    ): SnapResult {
        val refX = references(others, horizontal = true)
        val refY = references(others, horizontal = false)
        val x = snapPosition(start.x + dx, floatArrayOf(0f, start.w / 2f, start.w), refX, grid, tolX)
            .clampTo(0f, 1f - start.w)
        val y = snapPosition(start.y + dy, floatArrayOf(0f, start.h / 2f, start.h), refY, grid, tolY)
            .clampTo(0f, 1f - start.h)
        return SnapResult(
            start.copy(x = x, y = y),
            guides(floatArrayOf(x, x + start.w / 2f, x + start.w), refX),
            guides(floatArrayOf(y, y + start.h / 2f, y + start.h), refY),
        )
    }

    /** Resizes from the bottom-right corner: only the right and bottom edges move. */
    fun resize(
        start: Control, dx: Float, dy: Float, others: List<Control>,
        grid: Int, tolX: Float, tolY: Float,
    ): SnapResult {
        val refX = references(others, horizontal = true)
        val refY = references(others, horizontal = false)
        val right = snapPosition(start.x + start.w + dx, floatArrayOf(0f), refX, grid, tolX)
        val bottom = snapPosition(start.y + start.h + dy, floatArrayOf(0f), refY, grid, tolY)
        val w = (right - start.x).clampTo(MIN_SIZE, 1f - start.x)
        val h = (bottom - start.y).clampTo(MIN_SIZE, 1f - start.y)
        return SnapResult(
            start.copy(w = w, h = h),
            guides(floatArrayOf(start.x + w), refX),
            guides(floatArrayOf(start.y + h), refY),
        )
    }

    /** Guides for a final placement (e.g. after collision handling moved the snapped result). */
    fun guidesFor(c: Control, others: List<Control>, resize: Boolean): SnapResult {
        val refX = references(others, horizontal = true)
        val refY = references(others, horizontal = false)
        return if (resize) {
            SnapResult(c, guides(floatArrayOf(c.x + c.w), refX), guides(floatArrayOf(c.y + c.h), refY))
        } else {
            SnapResult(
                c,
                guides(floatArrayOf(c.x, c.x + c.w / 2f, c.x + c.w), refX),
                guides(floatArrayOf(c.y, c.y + c.h / 2f, c.y + c.h), refY),
            )
        }
    }

    /** Lines other elements offer along one axis: their start, center and end, plus the page's. */
    private fun references(others: List<Control>, horizontal: Boolean): List<Float> {
        val lines = mutableListOf(0f, 0.5f, 1f)
        for (o in others) {
            val start = if (horizontal) o.x else o.y
            val size = if (horizontal) o.w else o.h
            lines += start
            lines += start + size / 2f
            lines += start + size
        }
        return lines
    }

    /**
     * [raw] is the dragged element's start; [offsets] are its snappable points relative to that
     * start (e.g. start, center, end). Returns the snapped start.
     */
    private fun snapPosition(raw: Float, offsets: FloatArray, refs: List<Float>, grid: Int, tol: Float): Float {
        var best: Float? = null
        var bestDistance = tol
        for (o in offsets) for (r in refs) {
            val d = abs(r - (raw + o))
            if (d <= bestDistance) {
                bestDistance = d
                best = r - o
            }
        }
        return best ?: snap(raw, grid)
    }

    /** Edges that coincide with a reference line. Page borders are left out to avoid clutter. */
    private fun guides(edges: FloatArray, refs: List<Float>): List<Float> =
        edges.filter { e -> e > EPS && e < 1f - EPS && refs.any { abs(it - e) < EPS } }
            .map { (it * 10_000f).roundToInt() / 10_000f }
            .distinct()
}
