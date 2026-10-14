package com.oscpad.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollisionTest {

    private fun box(id: String, x: Float, y: Float, w: Float, h: Float) =
        Control(id = id, type = ControlType.BUTTON, name = id, address = "/$id", x = x, y = y, w = w, h = h)

    private val eps = 1e-4f
    private val wall = box("wall", 0.5f, 0.2f, 0.1f, 0.4f) // x 0.5..0.6, y 0.2..0.6

    @Test
    fun freeMoveIsTakenAsIs() {
        val c = box("c", 0.1f, 0.1f, 0.1f, 0.1f)
        val r = Collision.move(c, c.copy(x = 0.3f), listOf(wall))
        assertEquals(0.3f, r.x, eps)
    }

    @Test
    fun stopsFlushAgainstNeighbour() {
        val c = box("c", 0.1f, 0.3f, 0.1f, 0.1f)
        val r = Collision.move(c, c.copy(x = 0.45f), listOf(wall))
        assertEquals(0.4f, r.x, eps) // right edge touches the wall's left edge
        assertFalse(Collision.overlaps(r, wall))
    }

    @Test
    fun slidesAlongAWallWhenMovingDiagonally() {
        val c = box("c", 0.35f, 0.3f, 0.1f, 0.1f)
        // Push right into the wall while moving down: x stops, y follows.
        val r = Collision.move(c, c.copy(x = 0.48f, y = 0.4f), listOf(wall))
        assertEquals(0.4f, r.x, eps)
        assertEquals(0.4f, r.y, eps)
    }

    @Test
    fun jumpsOverANeighbourIntoFreeSpace() {
        val c = box("c", 0.3f, 0.3f, 0.1f, 0.1f)
        val r = Collision.move(c, c.copy(x = 0.7f), listOf(wall))
        assertEquals(0.7f, r.x, eps)
    }

    @Test
    fun alreadyOverlappingControlsDontBlock() {
        val c = box("c", 0.45f, 0.3f, 0.1f, 0.1f) // overlaps the wall at the start
        val obstacles = Collision.obstacles(c, listOf(wall))
        assertTrue(obstacles.isEmpty())
        val r = Collision.move(c, c.copy(x = 0.52f), obstacles)
        assertEquals(0.52f, r.x, eps)
    }

    @Test
    fun touchingEdgesAreNotAnOverlap() {
        assertFalse(Collision.overlaps(box("a", 0f, 0f, 0.5f, 0.5f), box("b", 0.5f, 0f, 0.5f, 0.5f)))
        assertTrue(Collision.overlaps(box("a", 0f, 0f, 0.51f, 0.5f), box("b", 0.5f, 0f, 0.5f, 0.5f)))
    }

    @Test
    fun resizeStopsAtNeighbourButShrinksFreely() {
        val c = box("c", 0.2f, 0.3f, 0.1f, 0.1f)
        val grown = Collision.resize(c, c.copy(w = 0.6f, h = 0.15f), listOf(wall))
        assertEquals(0.3f, grown.w, eps) // right edge stops at 0.5
        assertEquals(0.15f, grown.h, eps)
        val shrunk = Collision.resize(c, c.copy(w = 0.05f), listOf(wall))
        assertEquals(0.05f, shrunk.w, eps)
    }

    @Test
    fun freeSpotAvoidsExistingControls() {
        val existing = box("e", 0.4f, 0.4f, 0.2f, 0.2f)
        val wanted = box("n", 0.45f, 0.45f, 0.1f, 0.1f)
        val placed = Collision.freeSpot(wanted, listOf(existing))
        assertFalse(Collision.overlaps(placed, existing))
        // Nearest free grid spot: just beside the existing control.
        assertTrue(kotlin.math.abs(placed.x - 0.45f) + kotlin.math.abs(placed.y - 0.45f) < 0.25f)
    }

    @Test
    fun layoutNameComesFromFileName() {
        assertEquals("My Show", layoutNameFromFileName("My Show.json"))
        assertEquals("set", layoutNameFromFileName("set.JSON"))
        assertEquals("plain", layoutNameFromFileName("plain"))
        assertNull(layoutNameFromFileName(" .json"))
        assertNull(layoutNameFromFileName(null))
    }
}
