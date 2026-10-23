/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mozilla.fenix.dejavu.menu.ActionsBarLayout
import org.mozilla.fenix.dejavu.menu.MoreMenuLayout
import org.mozilla.fenix.dejavu.settings.MenuLinesEditing.BAR

class MenuLinesEditingTest {
    private val extensions = "extensions"
    private val lines = listOf(
        listOf("forward", "share", "home", "reload"),
        listOf("back", "forward", "share"),
        listOf("history", "bookmarks", "downloads", "passwords"),
        listOf(extensions),
        emptyList(),
    )
    private val fullBar = lines.mapIndexed { index, line -> if (index == BAR) line + "search" else line }

    private fun new(key: String) = MenuDragSource.New(key)

    private fun placed(lines: List<List<String>>, line: Int, index: Int) =
        MenuDragSource.Placed(lines[line][index], line, index)

    @Test
    fun `a new button goes before the button it is dropped on, or after the last one on an empty block`() {
        assertEquals(listOf("back", "print", "forward", "share"), MenuLinesEditing.drop(lines, new("print"), 1, 1)!![1])
        assertEquals(listOf("back", "forward", "share", "print"), MenuLinesEditing.drop(lines, new("print"), 1, 3)!![1])
        assertEquals(listOf("print"), MenuLinesEditing.drop(lines, new("print"), 4, 2)!![4])
    }

    @Test
    fun `a button already on the lines can be added again`() {
        assertEquals(listOf("back", "forward", "share", "back"), MenuLinesEditing.drop(lines, new("back"), 1, 3)!![1])
        assertEquals(
            listOf("forward", "share", "home", "reload", "share"),
            MenuLinesEditing.drop(lines, new("share"), BAR, 4)!![BAR],
        )
    }

    @Test
    fun `on a full line a new button replaces the one it is dropped on, and a moved one swaps with it`() {
        assertEquals(
            listOf("history", "print", "downloads", "passwords"),
            MenuLinesEditing.drop(lines, new("print"), 2, 1)!![2],
        )

        val swapped = MenuLinesEditing.drop(lines, placed(lines, 1, 2), 2, 1)!!
        assertEquals(listOf("back", "forward", "bookmarks"), swapped[1])
        assertEquals(listOf("history", "share", "downloads", "passwords"), swapped[2])
    }

    @Test
    fun `a button moves within its line, or to another line that has room`() {
        assertEquals(listOf("forward", "share", "back"), MenuLinesEditing.drop(lines, placed(lines, 1, 0), 1, 3)!![1])
        assertEquals(listOf("forward", "back", "share"), MenuLinesEditing.drop(lines, placed(lines, 1, 0), 1, 1)!![1])

        val moved = MenuLinesEditing.drop(lines, placed(lines, 2, 3), BAR, 0)!!
        assertEquals(listOf("passwords", "forward", "share", "home", "reload"), moved[BAR])
        assertEquals(listOf("history", "bookmarks", "downloads"), moved[2])
    }

    @Test
    fun `the actions bar has five blocks, and only it takes Home and Search`() {
        assertEquals(ActionsBarLayout.MAX_BUTTONS, MenuLinesEditing.capacityOf(BAR))
        assertEquals(MoreMenuLayout.MAX_PER_ROW, MenuLinesEditing.capacityOf(1))
        assertEquals(
            listOf("search", "forward", "share", "home", "reload"),
            MenuLinesEditing.drop(lines, new("search"), BAR, 0)!![BAR],
        )
        assertNull(MenuLinesEditing.drop(lines, new("search"), 1, 3))
        assertNull(MenuLinesEditing.drop(lines, placed(lines, BAR, 2), 4, 0))
        assertEquals(
            listOf("forward", "share", "print", "reload", "search"),
            MenuLinesEditing.drop(fullBar, new("print"), BAR, 2)!![BAR],
        )
        // Swapping would put Home into a row of the menu.
        assertNull(MenuLinesEditing.drop(fullBar, placed(fullBar, 1, 0), BAR, 2))
    }

    @Test
    fun `extensions only go into an empty row with nothing next to them, but in the bar they are a button`() {
        assertNull(MenuLinesEditing.drop(lines, new(extensions), 1, 3))
        assertNull(MenuLinesEditing.drop(lines, new("print"), 3, 0))
        assertEquals(listOf(extensions), MenuLinesEditing.drop(lines, new(extensions), 4, 1)!![4])
        assertEquals(
            listOf("forward", "share", "home", "reload", extensions),
            MenuLinesEditing.drop(lines, new(extensions), BAR, 4)!![BAR],
        )

        val moved = MenuLinesEditing.drop(lines, placed(lines, 3, 0), 4, 0)!!
        assertEquals(emptyList<String>(), moved[3])
        assertEquals(listOf(extensions), moved[4])
    }

    @Test
    fun `the landing block is where the button ends up`() {
        assertEquals(1..1, MenuLinesEditing.landing(lines, new("print"), 1, 1))
        assertEquals(3..3, MenuLinesEditing.landing(lines, new("print"), 1, 3))
        assertEquals(0..0, MenuLinesEditing.landing(lines, new("print"), 4, 2))
        assertEquals(4..4, MenuLinesEditing.landing(lines, new("print"), BAR, 4))
        assertEquals(2..2, MenuLinesEditing.landing(lines, placed(lines, 1, 0), 1, 3))
        assertEquals(0 until MoreMenuLayout.MAX_PER_ROW, MenuLinesEditing.landing(lines, new(extensions), 4, 0))
    }

    @Test
    fun `removing a button keeps its line, and a tapped button goes after the last one`() {
        assertEquals(listOf("forward", "share"), MenuLinesEditing.remove(lines, 1, 0)[1])
        assertEquals(emptyList<String>(), MenuLinesEditing.remove(lines, 3, 0)[3])

        assertEquals(listOf("print"), MenuLinesEditing.append(lines, "print")[4])
        assertEquals(listOf(extensions), MenuLinesEditing.append(lines, extensions)[4])
        assertEquals(listOf("forward", "share", "home", "reload", "home"), MenuLinesEditing.append(lines, "home")[BAR])
        assertEquals(fullBar, MenuLinesEditing.append(fullBar, "home"))
        val full = listOf(emptyList(), listOf("history", "bookmarks", "downloads", "passwords"))
        assertEquals(full + listOf(listOf("print")), MenuLinesEditing.append(full, "print"))
        assertEquals(listOf(emptyList(), listOf("print")), MenuLinesEditing.append(listOf(emptyList()), "print"))
    }

    @Test
    fun `saved rows keep buttons that are there more than once, without empty rows`() {
        val saved = MoreMenuLayout.normalized(
            listOf(listOf("back", "back", extensions, "share"), emptyList(), listOf("back")),
        )

        assertEquals(listOf(listOf(extensions), listOf("back", "back", "share"), listOf("back")), saved)
    }
}
