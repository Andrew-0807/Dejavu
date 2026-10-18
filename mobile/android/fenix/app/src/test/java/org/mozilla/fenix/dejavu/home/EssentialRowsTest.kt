/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.home

import org.junit.Assert.assertEquals
import org.junit.Test

class EssentialRowsTest {
    @Test
    fun `essentials that fit in one row take one row`() {
        assertEquals(listOf(1), essentialRows(1, COLUMNS))
        assertEquals(listOf(3), essentialRows(3, COLUMNS))
        assertEquals(listOf(4), essentialRows(4, COLUMNS))
    }

    @Test
    fun `more essentials are spread evenly over as few rows as fit them, the fuller rows first`() {
        assertEquals(listOf(3, 2), essentialRows(5, COLUMNS))
        assertEquals(listOf(3, 3), essentialRows(6, COLUMNS))
        assertEquals(listOf(4, 3), essentialRows(7, COLUMNS))
        assertEquals(listOf(4, 4), essentialRows(8, COLUMNS))
        assertEquals(listOf(3, 3, 3), essentialRows(9, COLUMNS))
        assertEquals(listOf(4, 3, 3), essentialRows(10, COLUMNS))
        assertEquals(listOf(4, 4, 3), essentialRows(11, COLUMNS))
        assertEquals(listOf(4, 4, 4), essentialRows(12, COLUMNS))
    }

    @Test
    fun `a wider grid takes fewer rows`() {
        assertEquals(listOf(5, 5), essentialRows(10, columns = 6))
        assertEquals(listOf(6, 6), essentialRows(12, columns = 8))
    }

    @Test
    fun `no essentials take no rows`() {
        assertEquals(emptyList<Int>(), essentialRows(0, COLUMNS))
    }

    @Test
    fun `a list is cut into rows of the given sizes, in order`() {
        assertEquals(listOf(listOf(1, 2, 3), listOf(4, 5)), (1..5).toList().inRows(listOf(3, 2)))
        assertEquals(emptyList<List<Int>>(), emptyList<Int>().inRows(emptyList()))
    }

    private companion object {
        const val COLUMNS = 4
    }
}
