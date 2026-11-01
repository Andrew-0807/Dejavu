/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.menu

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mozilla.fenix.dejavu.actions.RowAction
import org.mozilla.fenix.dejavu.actions.TabAction
import org.mozilla.fenix.dejavu.actions.forSplitWays
import org.mozilla.fenix.dejavu.workspaces.SplitOrientation.SIDE_BY_SIDE
import org.mozilla.fenix.dejavu.workspaces.SplitOrientation.TOP_AND_BOTTOM

class SplitEntriesTest {
    private val menu = listOf(MoreMenuItem.SHARE, MoreMenuItem.SPLIT_VIEW, MoreMenuItem.FIND_IN_PAGE)
        .map { MoreMenuEntry.BuiltIn(it) }
    private val row = listOf(TabAction.CLOSE, TabAction.SPLIT_VIEW).map { RowAction.BuiltIn(it) }

    private fun keys(entries: List<MoreMenuEntry>) = entries.map { it.key }

    @Test
    fun `with room for one way, the menus keep their split view entry`() {
        assertEquals(keys(menu), keys(menu.forSplitWays(listOf(TOP_AND_BOTTOM), isSplit = false)))
        assertEquals(row, row.forSplitWays(listOf(SIDE_BY_SIDE)))
    }

    @Test
    fun `with room for both ways, the split view entry becomes one for each, in their order`() {
        assertEquals(
            listOf("share", "split_side_by_side", "split_top_and_bottom", "find_in_page"),
            keys(menu.forSplitWays(listOf(SIDE_BY_SIDE, TOP_AND_BOTTOM), isSplit = false)),
        )
        assertEquals(
            listOf(TabAction.CLOSE, TabAction.SPLIT_TOP_AND_BOTTOM, TabAction.SPLIT_SIDE_BY_SIDE),
            row.forSplitWays(listOf(TOP_AND_BOTTOM, SIDE_BY_SIDE)).map { (it as RowAction.BuiltIn).action },
        )
    }

    @Test
    fun `with room for no way, the split view entry goes away`() {
        assertEquals(listOf("share", "find_in_page"), keys(menu.forSplitWays(emptyList(), isSplit = false)))
        assertEquals(listOf(RowAction.BuiltIn(TabAction.CLOSE)), row.forSplitWays(emptyList()))
    }

    @Test
    fun `a tab shown split keeps the entry that ends the split`() {
        assertEquals(keys(menu), keys(menu.forSplitWays(listOf(SIDE_BY_SIDE, TOP_AND_BOTTOM), isSplit = true)))
        assertEquals(keys(menu), keys(menu.forSplitWays(emptyList(), isSplit = true)))
    }
}
