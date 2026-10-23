/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.home

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mozilla.fenix.dejavu.workspaces.PinKind
import org.mozilla.fenix.dejavu.workspaces.PinnedItem
import org.mozilla.fenix.dejavu.workspaces.SplitView
import org.mozilla.fenix.dejavu.workspaces.Workspace
import org.mozilla.fenix.dejavu.workspaces.WorkspaceState

class PinLinesTest {
    private fun pin(id: String, tabId: String? = "tab-$id", parentId: String? = null, kind: PinKind = PinKind.TAB) =
        PinnedItem(
            id = id,
            workspaceId = "workspace",
            parentId = parentId,
            kind = kind,
            title = id,
            url = "https://$id.example/".takeIf { kind == PinKind.TAB },
            tabId = tabId.takeIf { kind == PinKind.TAB },
            createdAt = 1L,
            updatedAt = 1L,
        )

    private fun rows(pins: List<PinnedItem>, vararg splits: Pair<String, String>): List<List<String>> {
        val state = WorkspaceState(
            workspaces = listOf(Workspace(id = "workspace", name = "Workspace", createdAt = 1L, updatedAt = 1L)),
            pins = pins,
            activeWorkspaceId = "workspace",
            assignments = emptyMap(),
            splits = splits.mapIndexed { index, (first, second) -> SplitView("split$index", listOf(first, second)) },
        )
        return pinLines(state.pinnedTree("workspace"), state).map { line -> line.entries.map { it.item.id } }
    }

    @Test
    fun `two split pinned tabs share one row, where the first one is`() {
        val pins = listOf(pin("a"), pin("x"), pin("b"), pin("c"))

        assertEquals(listOf(listOf("a", "b"), listOf("x"), listOf("c")), rows(pins, "tab-b" to "tab-a"))
    }

    @Test
    fun `split pinned tabs in different folders keep rows of their own`() {
        val pins = listOf(pin("folder", kind = PinKind.FOLDER), pin("a", parentId = "folder"), pin("b"))

        assertEquals(listOf(listOf("folder"), listOf("a"), listOf("b")), rows(pins, "tab-a" to "tab-b"))
    }

    @Test
    fun `split pinned tabs inside the same folder share a row there`() {
        val pins = listOf(pin("folder", kind = PinKind.FOLDER), pin("a", parentId = "folder"), pin("b", parentId = "folder"))

        assertEquals(listOf(listOf("folder"), listOf("a", "b")), rows(pins, "tab-a" to "tab-b"))
    }

    @Test
    fun `a pinned tab split with a tab that is not pinned keeps its own row`() {
        val pins = listOf(pin("a"), pin("b"), pin("closed", tabId = null))

        assertEquals(listOf(listOf("a"), listOf("b"), listOf("closed")), rows(pins, "tab-a" to "unpinned"))
    }
}
