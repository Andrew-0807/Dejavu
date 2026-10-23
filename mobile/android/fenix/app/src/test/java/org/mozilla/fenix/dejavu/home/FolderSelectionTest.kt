/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.home

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mozilla.fenix.dejavu.workspaces.PinKind
import org.mozilla.fenix.dejavu.workspaces.PinnedItem
import org.mozilla.fenix.dejavu.workspaces.Workspace
import org.mozilla.fenix.dejavu.workspaces.WorkspaceState

class FolderSelectionTest {
    private fun item(id: String, parentId: String? = null, folder: Boolean = false) = PinnedItem(
        id = id,
        workspaceId = "workspace",
        parentId = parentId,
        kind = if (folder) PinKind.FOLDER else PinKind.TAB,
        title = id,
        url = "https://$id.example/".takeIf { !folder },
        createdAt = 1L,
        updatedAt = 1L,
    )

    /** Folder "a" with tab "a1" and subfolder "b", which holds tab "b1"; tab "c" stays outside. */
    private val state = WorkspaceState(
        workspaces = listOf(Workspace(id = "workspace", name = "Home", createdAt = 1L, updatedAt = 1L)),
        pins = listOf(
            item("a", folder = true),
            item("a1", parentId = "a"),
            item("b", parentId = "a", folder = true),
            item("b1", parentId = "b"),
            item("c"),
        ),
        activeWorkspaceId = "workspace",
        assignments = emptyMap(),
    )

    @Test
    fun `picking a folder picks everything inside it, its subfolders and their tabs included`() {
        val picked = Selection.ofFolder("a", state)

        assertEquals(setOf("a", "b"), picked.folderIds)
        assertEquals(setOf("a1", "b1"), picked.pinIds)
        assertEquals(Selection(), picked.toggleFolder("a", state))
    }

    @Test
    fun `leaving out something inside a picked folder leaves out the folders it is in, and keeps the rest`() {
        val picked = Selection.ofFolder("a", state)

        assertEquals(Selection(pinIds = setOf("a1")), picked.togglePin("b1", state))
        assertEquals(Selection(pinIds = setOf("a1")), picked.toggleFolder("b", state))
        assertEquals(Selection(pinIds = setOf("c")), Selection().togglePin("c", state))
    }

    @Test
    fun `a picked folder moves as a whole, and an action reaches each tab inside it once`() {
        val picked = Selection.ofFolder("a", state)
        val targets = ActionTargets.of(state, emptyList(), picked)

        assertEquals(listOf("a"), targets.folders.map { it.id })
        assertEquals(emptyList<String>(), targets.pins.map { it.id })
        assertEquals(listOf("a1", "b1"), targets.folderPins.map { it.id })
        assertEquals(setOf("a"), targets.itemIds)
        assertEquals(4, picked.size)
        assertEquals(1, picked.topLevelCount(state))
        assertEquals(2, (picked + Selection(pinIds = setOf("c"))).topLevelCount(state))
    }
}
