/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.menu

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuLayoutTest {
    private fun item(item: MoreMenuItem) = MoreMenuEntry.BuiltIn(item)
    private val back = item(MoreMenuItem.BACK)
    private val forward = item(MoreMenuItem.FORWARD)
    private val share = item(MoreMenuItem.SHARE)

    @Test
    fun `actions bar always has Back, first unless placed, and no Home beside the home sidebar`() {
        val bar = listOf(forward, share, MoreMenuEntry.Home, MoreMenuEntry.Search)
        assertEquals(listOf(back) + bar, ActionsBarLayout.shown(bar, hasHomeSidebar = false))
        assertEquals(
            listOf(back, forward, share, MoreMenuEntry.Search),
            ActionsBarLayout.shown(bar, hasHomeSidebar = true),
        )
        val placed = listOf(forward, back, share)
        assertEquals(placed, ActionsBarLayout.shown(placed, hasHomeSidebar = true))
    }

    @Test
    fun `menu columns follow the width, from 4 on phones to 8`() {
        assertEquals(4, MoreMenuLayout.columnsFor(300f))
        assertEquals(4, MoreMenuLayout.columnsFor(400f))
        assertEquals(7, MoreMenuLayout.columnsFor(616f))
        assertEquals(8, MoreMenuLayout.columnsFor(2000f))
    }

    @Test
    fun `wide menus join rows into a grid and keep full rows alone`() {
        val first = listOf(back, forward, share, item(MoreMenuItem.REFRESH))
        val extensions = listOf(item(MoreMenuItem.EXTENSIONS))
        val last = listOf(item(MoreMenuItem.HISTORY), item(MoreMenuItem.BOOKMARKS))
        val rows = listOf(first, extensions, last)
        assertEquals(rows, MoreMenuLayout.gridRows(rows, columns = 4))
        val second = listOf(item(MoreMenuItem.FIND_IN_PAGE), item(MoreMenuItem.DESKTOP_SITE))
        assertEquals(
            listOf(first + second, extensions, last),
            MoreMenuLayout.gridRows(listOf(first, second, extensions, last), columns = 6),
        )
        val joined = first + second
        assertEquals(
            listOf(joined.take(5), joined.drop(5)),
            MoreMenuLayout.gridRows(listOf(first, second), columns = 5),
        )
    }
}
