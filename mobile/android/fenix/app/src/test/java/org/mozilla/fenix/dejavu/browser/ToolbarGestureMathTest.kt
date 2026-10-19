/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.fenix.browser.immersiveMenuPosition
import org.mozilla.fenix.browser.tabstrip.sidebarSettlesOpen

class ToolbarGestureMathTest {
    private val minDistance = 60f
    private val minVelocity = 1000f

    private fun settle(start: Float, distance: Float, velocity: Float = 0f) =
        compactBarSettle(start, distance, velocity, minDistance, minVelocity)

    @Test
    fun `compact bar ignores small slow slips`() {
        assertEquals(CompactBarSettle.COLLAPSE, settle(start = 0f, distance = -10f))
        assertEquals(CompactBarSettle.COLLAPSE, settle(start = 0f, distance = 10f))
        assertEquals(CompactBarSettle.EXPAND, settle(start = 1f, distance = -10f))
        assertEquals(CompactBarSettle.EXPAND, settle(start = 1f, distance = 10f))
    }

    @Test
    fun `compact bar opens, runs the gesture, hides and reports a swipe down past the thresholds`() {
        assertEquals(CompactBarSettle.EXPAND, settle(start = 0f, distance = -80f))
        assertEquals(CompactBarSettle.EXPAND, settle(start = 0f, distance = -10f, velocity = -2000f))
        assertEquals(CompactBarSettle.EXPAND_AND_RUN_GESTURE, settle(start = 1f, distance = -80f))
        assertEquals(CompactBarSettle.EXPAND_AND_RUN_GESTURE, settle(start = 1f, distance = -10f, velocity = -2000f))
        assertEquals(CompactBarSettle.COLLAPSE, settle(start = 1f, distance = 80f))
        assertEquals(CompactBarSettle.SWIPE_DOWN_COLLAPSED, settle(start = 0f, distance = 80f))
        assertEquals(CompactBarSettle.SWIPE_DOWN_COLLAPSED, settle(start = 0f, distance = 10f, velocity = 2000f))
    }

    @Test
    fun `compact bar follows a reversing fling and the nearest end mid animation`() {
        assertEquals(CompactBarSettle.COLLAPSE, settle(start = 0f, distance = -80f, velocity = 2000f))
        assertEquals(CompactBarSettle.EXPAND, settle(start = 0.7f, distance = 5f))
        assertEquals(CompactBarSettle.COLLAPSE, settle(start = 0.3f, distance = -5f))
    }

    @Test
    fun `compact bar claims only vertical drags past the slop`() {
        assertFalse(compactBarClaimsDrag(dx = 0f, dy = 5f, touchSlop = 8f))
        assertTrue(compactBarClaimsDrag(dx = 2f, dy = -9f, touchSlop = 8f))
        assertFalse(compactBarClaimsDrag(dx = 9f, dy = 8f, touchSlop = 8f))
        assertFalse(isPastTouchSlop(dx = 5f, dy = 5f, touchSlop = 8f))
        assertTrue(isPastTouchSlop(dx = 6f, dy = 6f, touchSlop = 8f))
    }

    @Test
    fun `sidebar opens past its share or on a fling and closes on a fling back`() {
        assertFalse(sidebarSettlesOpen(pulled = 100f, width = 320f, velocity = 0f, flingVelocity = 400f))
        assertTrue(sidebarSettlesOpen(pulled = 120f, width = 320f, velocity = 0f, flingVelocity = 400f))
        assertTrue(sidebarSettlesOpen(pulled = 20f, width = 320f, velocity = 500f, flingVelocity = 400f))
        assertFalse(sidebarSettlesOpen(pulled = 300f, width = 320f, velocity = -500f, flingVelocity = 400f))
    }

    @Test
    fun `immersive menu stays in the window`() {
        // Room below.
        assertEquals(10 to 60, immersiveMenuPosition(10, 10, 50, 200, 300, 1000, 2000))
        // Near the right and bottom edges: moved left and opened above.
        assertEquals(800 to 1640, immersiveMenuPosition(950, 1940, 50, 200, 300, 1000, 2000))
        // Taller than the room on both sides: as low as it fits, here the top.
        assertEquals(0 to 0, immersiveMenuPosition(0, 100, 50, 200, 3000, 1000, 2000))
    }
}
