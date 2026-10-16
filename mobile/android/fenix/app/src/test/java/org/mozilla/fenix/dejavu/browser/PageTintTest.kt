/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PageTintTest {
    private val header = 0xFFEAECF0.toInt()

    @Test
    fun `the page's background wins over a logo crossing its top edge`() {
        val edge = edge(header, header, Color.BLACK, Color.BLACK, header, header, header)

        assertEquals(header, edge.topEdgeColor())
    }

    @Test
    fun `nearly the same colors count as one`() {
        val edge = edge(header, 0xFFEBEDF1.toInt(), 0xFFE8EAF2.toInt(), Color.RED, Color.RED)

        assertEquals(header, edge.topEdgeColor())
    }

    private fun edge(vararg colors: Int) = Bitmap.createBitmap(colors.size, 1, Bitmap.Config.ARGB_8888).apply {
        colors.forEachIndexed { x, color -> setPixel(x, 0, color) }
    }
}
