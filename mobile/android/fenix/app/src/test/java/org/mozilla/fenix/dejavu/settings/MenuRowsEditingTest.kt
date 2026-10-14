/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mozilla.fenix.dejavu.menu.MoreMenuLayout

class MenuRowsEditingTest {
    private val extensions = "extensions"
    private val rows = listOf(
        listOf("back", "forward", "share"),
        listOf("history", "bookmarks", "downloads", "passwords"),
        listOf(extensions),
        emptyList(),
    )

    private fun new(key: String) = MenuDragSource.New(key)

    private fun placed(row: Int, index: Int) = MenuDragSource.Placed(rows[row][index], row, index)

    @Test
    fun `a new button goes before the button it is dropped on, or after the last one on an empty block`() {
        assertEquals(listOf("back", "print", "forward", "share"), MenuRowsEditing.drop(rows, new("print"), 0, 1)!![0])
        assertEquals(listOf("back", "forward", "share", "print"), MenuRowsEditing.drop(rows, new("print"), 0, 3)!![0])
        assertEquals(listOf("print"), MenuRowsEditing.drop(rows, new("print"), 3, 2)!![3])
    }

    @Test
    fun `a button already in the menu can be added again`() {
        val updated = MenuRowsEditing.drop(rows, new("back"), 0, 3)!!

        assertEquals(listOf("back", "forward", "share", "back"), updated[0])
    }

    @Test
    fun `on a full row a new button replaces the one it is dropped on, and a moved one swaps with it`() {
        assertEquals(
            listOf("history", "print", "downloads", "passwords"),
            MenuRowsEditing.drop(rows, new("print"), 1, 1)!![1],
        )

        val swapped = MenuRowsEditing.drop(rows, placed(0, 2), 1, 1)!!
        assertEquals(listOf("back", "forward", "bookmarks"), swapped[0])
        assertEquals(listOf("history", "share", "downloads", "passwords"), swapped[1])
    }

    @Test
    fun `a button moves within its row, or to another row that has room`() {
        assertEquals(listOf("forward", "share", "back"), MenuRowsEditing.drop(rows, placed(0, 0), 0, 3)!![0])
        assertEquals(listOf("forward", "back", "share"), MenuRowsEditing.drop(rows, placed(0, 0), 0, 1)!![0])

        val moved = MenuRowsEditing.drop(rows, placed(1, 3), 0, 0)!!
        assertEquals(listOf("passwords", "back", "forward", "share"), moved[0])
        assertEquals(listOf("history", "bookmarks", "downloads"), moved[1])
    }

    @Test
    fun `extensions only go into an empty row, and nothing goes next to them`() {
        assertNull(MenuRowsEditing.drop(rows, new(extensions), 0, 3))
        assertNull(MenuRowsEditing.drop(rows, new("print"), 2, 0))
        assertEquals(listOf(extensions), MenuRowsEditing.drop(rows, new(extensions), 3, 1)!![3])

        val moved = MenuRowsEditing.drop(rows, placed(2, 0), 3, 0)!!
        assertEquals(emptyList<String>(), moved[2])
        assertEquals(listOf(extensions), moved[3])
    }

    @Test
    fun `the landing block is where the button ends up`() {
        assertEquals(1..1, MenuRowsEditing.landing(rows, new("print"), 0, 1))
        assertEquals(3..3, MenuRowsEditing.landing(rows, new("print"), 0, 3))
        assertEquals(0..0, MenuRowsEditing.landing(rows, new("print"), 3, 2))
        assertEquals(2..2, MenuRowsEditing.landing(rows, placed(0, 0), 0, 3))
        assertEquals(0 until MoreMenuLayout.MAX_PER_ROW, MenuRowsEditing.landing(rows, new(extensions), 3, 0))
    }

    @Test
    fun `removing a button keeps its row, and a tapped button goes after the last one`() {
        assertEquals(listOf("forward", "share"), MenuRowsEditing.remove(rows, 0, 0)[0])
        assertEquals(emptyList<String>(), MenuRowsEditing.remove(rows, 2, 0)[2])

        assertEquals(listOf("print"), MenuRowsEditing.append(rows, "print")[3])
        assertEquals(listOf(extensions), MenuRowsEditing.append(rows, extensions)[3])
        val full = listOf(listOf("history", "bookmarks", "downloads", "passwords"))
        assertEquals(full + listOf(listOf("print")), MenuRowsEditing.append(full, "print"))
        assertEquals(listOf(listOf("back", "print")), MenuRowsEditing.append(listOf(listOf("back")), "print"))
    }

    @Test
    fun `saved rows keep buttons that are there more than once, without empty rows`() {
        val saved = MoreMenuLayout.normalized(
            listOf(listOf("back", "back", extensions, "share"), emptyList(), listOf("back")),
        )

        assertEquals(listOf(listOf(extensions), listOf("back", "back", "share"), listOf("back")), saved)
    }
}
