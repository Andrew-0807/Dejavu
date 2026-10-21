/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mozilla.fenix.dejavu.workspaces.SplitOrientation
import org.mozilla.fenix.dejavu.workspaces.SplitOrientation.SIDE_BY_SIDE
import org.mozilla.fenix.dejavu.workspaces.SplitOrientation.TOP_AND_BOTTOM

class SplitSpaceTest {
    @Test
    fun `a phone held upright splits top and bottom only`() {
        assertEquals(listOf(TOP_AND_BOTTOM), SplitSpace.supported(widthDp = 411, heightDp = 868))
    }

    @Test
    fun `a phone on its side splits side by side only`() {
        assertEquals(listOf(SIDE_BY_SIDE), SplitSpace.supported(widthDp = 876, heightDp = 387))
    }

    @Test
    fun `a tablet splits both ways, along its long side first`() {
        assertEquals(listOf(SIDE_BY_SIDE, TOP_AND_BOTTOM), SplitSpace.supported(widthDp = 1280, heightDp = 752))
        assertEquals(listOf(TOP_AND_BOTTOM, SIDE_BY_SIDE), SplitSpace.supported(widthDp = 800, heightDp = 1232))
    }

    @Test
    fun `a screen too small for two pages splits no way`() {
        assertEquals(emptyList<SplitOrientation>(), SplitSpace.supported(widthDp = 360, heightDp = 500))
    }

    @Test
    fun `a split shows the way it was made when that fits, and the way that fits otherwise`() {
        assertEquals(SIDE_BY_SIDE, SplitSpace.shown(SIDE_BY_SIDE, listOf(TOP_AND_BOTTOM, SIDE_BY_SIDE)))
        assertEquals(SIDE_BY_SIDE, SplitSpace.shown(TOP_AND_BOTTOM, listOf(SIDE_BY_SIDE)))
        assertEquals(TOP_AND_BOTTOM, SplitSpace.shown(TOP_AND_BOTTOM, emptyList()))
    }

    @Test
    fun `a new split goes along the long side`() {
        assertEquals(SIDE_BY_SIDE, SplitSpace.preferred(listOf(SIDE_BY_SIDE, TOP_AND_BOTTOM)))
        assertEquals(TOP_AND_BOTTOM, SplitSpace.preferred(listOf(TOP_AND_BOTTOM)))
        assertEquals(TOP_AND_BOTTOM, SplitSpace.preferred(emptyList()))
    }

    @Test
    fun `the pages keep their places whichever is in focus, the divider centered where the first one's share ends`() {
        val area = PixelRect(left = 0, top = 100, right = 720, bottom = 1300)

        val firstInFocus = SplitGeometry.place(area, TOP_AND_BOTTOM, fraction = 0.5f, divider = 24, paneFirst = false)
        val secondInFocus = SplitGeometry.place(area, TOP_AND_BOTTOM, fraction = 0.5f, divider = 24, paneFirst = true)

        assertEquals(PixelRect(0, 100, 720, 688), firstInFocus.main)
        assertEquals(PixelRect(0, 688, 720, 712), firstInFocus.divider)
        assertEquals(PixelRect(0, 712, 720, 1300), firstInFocus.pane)
        assertEquals(firstInFocus.main, secondInFocus.pane)
        assertEquals(firstInFocus.pane, secondInFocus.main)
        assertEquals(firstInFocus.divider, secondInFocus.divider)
    }

    @Test
    fun `side by side, the first page is on the left`() {
        val area = PixelRect(left = 70, top = 161, right = 1604, bottom = 552)

        val places = SplitGeometry.place(area, SIDE_BY_SIDE, fraction = 0.3f, divider = 24, paneFirst = true)

        assertEquals(PixelRect(70, 161, 518, 552), places.pane)
        assertEquals(PixelRect(518, 161, 542, 552), places.divider)
        assertEquals(PixelRect(542, 161, 1604, 552), places.main)
    }

    @Test
    fun `each page keeps at least a quarter of the area, however far the divider goes`() {
        val area = PixelRect(left = 0, top = 100, right = 720, bottom = 1300)

        assertEquals(0.5f, SplitGeometry.fractionAt(area, TOP_AND_BOTTOM, position = 700f))
        assertEquals(0.25f, SplitGeometry.fractionAt(area, TOP_AND_BOTTOM, position = 120f))
        assertEquals(0.75f, SplitGeometry.fractionAt(area, TOP_AND_BOTTOM, position = 1290f))
        assertEquals(0.25f, SplitGeometry.fractionAt(area, SIDE_BY_SIDE, position = 10f))
        assertEquals(
            SplitGeometry.place(area, TOP_AND_BOTTOM, fraction = 0.75f, divider = 24, paneFirst = false),
            SplitGeometry.place(area, TOP_AND_BOTTOM, fraction = 0.95f, divider = 24, paneFirst = false),
        )
    }
}
