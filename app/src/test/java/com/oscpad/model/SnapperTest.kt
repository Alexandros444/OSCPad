package com.oscpad.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapperTest {

    private fun box(x: Float, y: Float, w: Float, h: Float) =
        Control(id = "c${x}_$y", type = ControlType.BUTTON, name = "", address = "/a", x = x, y = y, w = w, h = h)

    private val tol = 0.01f
    private val eps = 1e-5f

    @Test
    fun snapsLeftEdgeToNeighbourRightEdgeAndShowsGuide() {
        val other = box(0.1f, 0.1f, 0.2f, 0.2f) // right edge at 0.3
        val moving = box(0.5f, 0.6f, 0.1f, 0.1f)
        // Drag so the left edge lands at 0.305 — within tolerance of 0.3
        val r = Snapper.move(moving, dx = -0.195f, dy = 0f, others = listOf(other), grid = 48, tolX = tol, tolY = tol)
        assertEquals(0.3f, r.control.x, eps)
        assertTrue(r.guidesX.any { kotlin.math.abs(it - 0.3f) < eps })
    }

    @Test
    fun snapsCentersTogether() {
        val other = box(0.2f, 0.2f, 0.2f, 0.2f) // center x 0.3
        val moving = box(0.6f, 0.6f, 0.1f, 0.1f) // center offset 0.05
        val r = Snapper.move(moving, dx = -0.354f, dy = 0f, others = listOf(other), grid = 0, tolX = tol, tolY = tol)
        assertEquals(0.25f, r.control.x, eps) // center now at 0.3
        assertTrue(r.guidesX.any { kotlin.math.abs(it - 0.3f) < eps })
    }

    @Test
    fun fallsBackToGridWhenNothingIsNear() {
        val moving = box(0.0f, 0.0f, 0.1f, 0.1f)
        val r = Snapper.move(moving, dx = 0.131f, dy = 0.0f, others = emptyList(), grid = 8, tolX = tol, tolY = tol)
        assertEquals(0.125f, r.control.x, eps) // nearest 1/8
    }

    @Test
    fun gridOffKeepsRawPosition() {
        val moving = box(0.0f, 0.0f, 0.1f, 0.1f)
        val r = Snapper.move(moving, dx = 0.1312f, dy = 0.0f, others = emptyList(), grid = 0, tolX = tol, tolY = tol)
        assertEquals(0.1312f, r.control.x, eps)
        assertTrue(r.guidesX.isEmpty())
    }

    @Test
    fun coarseGridOnlyUsesCoarseSteps() {
        val moving = box(0.0f, 0.0f, 0.1f, 0.1f)
        val fine = Snapper.move(moving, dx = 0.07f, dy = 0f, others = emptyList(), grid = 48, tolX = 0f, tolY = 0f)
        val coarse = Snapper.move(moving, dx = 0.07f, dy = 0f, others = emptyList(), grid = 12, tolX = 0f, tolY = 0f)
        assertEquals(3f / 48f, fine.control.x, eps)
        assertEquals(1f / 12f, coarse.control.x, eps)
    }

    @Test
    fun resizeSnapsBottomEdgeToNeighbourBottom() {
        val other = box(0.5f, 0.1f, 0.2f, 0.5f) // bottom at 0.6
        val sizing = box(0.1f, 0.1f, 0.2f, 0.2f) // bottom at 0.3
        val r = Snapper.resize(sizing, dx = 0f, dy = 0.296f, others = listOf(other), grid = 48, tolX = tol, tolY = tol)
        assertEquals(0.5f, r.control.h, eps)
        assertTrue(r.guidesY.any { kotlin.math.abs(it - 0.6f) < eps })
        assertEquals(14f / 48f - 0.1f, r.control.w, eps) // right edge 0.3 isn't near a line: grid snaps it
    }

    @Test
    fun resizeNeverGoesBelowMinimumOrOffPage() {
        val sizing = box(0.8f, 0.8f, 0.1f, 0.1f)
        val shrink = Snapper.resize(sizing, dx = -0.5f, dy = -0.5f, others = emptyList(), grid = 48, tolX = tol, tolY = tol)
        assertEquals(MIN_SIZE, shrink.control.w, eps)
        val grow = Snapper.resize(sizing, dx = 0.5f, dy = 0.5f, others = emptyList(), grid = 48, tolX = tol, tolY = tol)
        assertEquals(0.2f, grow.control.w, eps)
    }

    @Test
    fun pageCenterAttractsAndShowsGuide() {
        val moving = box(0.1f, 0.1f, 0.2f, 0.2f)
        val r = Snapper.move(moving, dx = 0.302f, dy = 0f, others = emptyList(), grid = 48, tolX = tol, tolY = tol)
        assertEquals(0.4f, r.control.x, eps) // center at 0.5
        assertEquals(listOf(0.5f), r.guidesX)
    }
}
