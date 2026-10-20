/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.browser.tabstrip

import org.junit.Assert.assertEquals
import org.junit.Test

class SidebarWidthTest {
    @Test
    fun `no saved width gives the default`() {
        assertEquals(SIDEBAR_DEFAULT_WIDTH_DP, sidebarWidthDp(wished = 0f, windowWidth = 1280f))
    }

    @Test
    fun `the width stays between the minimum and 40 percent of the window`() {
        assertEquals(SIDEBAR_MIN_WIDTH_DP, sidebarWidthDp(wished = 120f, windowWidth = 1280f))
        assertEquals(512f, sidebarWidthDp(wished = 900f, windowWidth = 1280f))
        assertEquals(400f, sidebarWidthDp(wished = 400f, windowWidth = 1280f))
    }

    @Test
    fun `rotating to a narrower window narrows a wide sidebar`() {
        assertEquals(512f, sidebarWidthDp(wished = 500f + 12f, windowWidth = 1280f))
        assertEquals(320f, sidebarWidthDp(wished = 512f, windowWidth = 800f))
    }

    @Test
    fun `on a window too narrow for both bounds the minimum wins`() {
        assertEquals(SIDEBAR_MIN_WIDTH_DP, sidebarWidthDp(wished = 300f, windowWidth = 400f))
    }
}
