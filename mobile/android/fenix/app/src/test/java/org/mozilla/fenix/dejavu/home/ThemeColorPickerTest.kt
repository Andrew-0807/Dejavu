/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeColorPickerTest {
    @Test
    fun `a complete color code reads as an opaque color, with or without its hash`() {
        assertEquals(0xFFF2B84B.toInt(), parseHex("#F2B84B"))
        assertEquals(0xFFF2B84B.toInt(), parseHex("f2b84b"))
    }

    @Test
    fun `an incomplete or wrong color code reads as nothing`() {
        assertNull(parseHex("#F2B8"))
        assertNull(parseHex("#F2B84B0"))
        assertNull(parseHex("#GGGGGG"))
        assertNull(parseHex(""))
    }

    @Test
    fun `a color goes to hue, saturation and value and back`() {
        val color = 0xFF2E32BD.toInt()

        assertEquals(color, Hsv.of(color).toArgb())
    }

    @Test
    fun `white, gray and black keep the hue picked before them`() {
        val blue = Hsv(hue = 236f, saturation = 0.75f, value = 0.74f)

        assertEquals(236f, Hsv.of(0xFFFFFFFF.toInt()).keepHueOf(blue).hue, 0.01f)
        assertEquals(236f, Hsv.of(0xFF000000.toInt()).keepHueOf(blue).hue, 0.01f)
        assertEquals(0f, Hsv.of(0xFFFF0000.toInt()).keepHueOf(blue).hue, 0.01f)
    }
}
