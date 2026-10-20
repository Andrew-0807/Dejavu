/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.home

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionColumnsTest {
    @Test
    fun `a wide home shows up to six actions a row`() {
        assertEquals(6, selectionColumns(12, 1000f))
    }

    @Test
    fun `the columns are evened out so no row is left nearly empty`() {
        // 5 fit in 360dp, but 12 actions then end with a row of 2: 4 rows of 3 would be taller, 3 rows of 4 is even.
        assertEquals(4, selectionColumns(12, 360f))
        assertEquals(4, selectionColumns(7, 360f))
    }

    @Test
    fun `the narrow sidebar keeps two columns`() {
        assertEquals(2, selectionColumns(12, 176f))
    }

    @Test
    fun `a very narrow home still has one column`() {
        assertEquals(1, selectionColumns(12, 40f))
        assertEquals(1, selectionColumns(0, 40f))
    }
}
