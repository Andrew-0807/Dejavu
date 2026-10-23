/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.home

import mozilla.components.browser.state.state.createTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.fenix.dejavu.workspaces.PinKind
import org.mozilla.fenix.dejavu.workspaces.PinnedItem
import org.mozilla.fenix.dejavu.workspaces.SyncedDevice
import org.mozilla.fenix.dejavu.workspaces.Workspace
import org.mozilla.fenix.dejavu.workspaces.WorkspaceState

class SelectionDragTest {
    private val home = Workspace(id = "home", name = "Home", createdAt = 1L, updatedAt = 1L)
    private val work = Workspace(id = "work", name = "Work", createdAt = 1L, updatedAt = 1L)
    private val laptop = Workspace(id = "laptop", name = "Laptop", createdAt = 1L, updatedAt = 1L, device = SyncedDevice.DESKTOP)
    private val tab = createTab("https://tab.example/", id = "tab", title = "Tab")

    private fun item(id: String, workspaceId: String?, kind: PinKind = PinKind.TAB, parentId: String? = null) = PinnedItem(
        id = id,
        workspaceId = workspaceId,
        parentId = parentId,
        kind = kind,
        title = id,
        url = "https://$id.example/".takeIf { kind == PinKind.TAB },
        essential = workspaceId == null,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private val folder = item("folder", "work", PinKind.FOLDER)
    private val inside = item("inside", "work", parentId = "folder")
    private val essential = item("essential", workspaceId = null)

    private val state = WorkspaceState(
        workspaces = listOf(home, work, laptop),
        pins = listOf(folder, inside, essential),
        activeWorkspaceId = "home",
        assignments = mapOf(tab.id to "work"),
    )

    private fun targets(selection: Selection) = ActionTargets.of(state, listOf(tab), selection)

    @Test
    fun `the selection is in the workspace of its tabs or pinned items, and in none when it is only essentials`() {
        assertEquals("work", targets(Selection(tabIds = setOf(tab.id))).workspaceIn(state))
        assertEquals("work", targets(Selection(folderIds = setOf("folder"), pinIds = setOf("essential"))).workspaceIn(state))
        assertNull(targets(Selection(pinIds = setOf("essential"))).workspaceIn(state))
    }

    @Test
    fun `the workspace of a device takes selected tabs, but no pinned tabs or folders`() {
        assertTrue(laptop.canTake(targets(Selection(tabIds = setOf(tab.id)))))
        assertFalse(laptop.canTake(targets(Selection(folderIds = setOf("folder")))))
        assertFalse(laptop.canTake(targets(Selection(tabIds = setOf(tab.id), pinIds = setOf("essential")))))
        assertTrue(home.canTake(targets(Selection(folderIds = setOf("folder"), tabIds = setOf(tab.id)))))
    }

    @Test
    fun `the strip at the end of the page leads to the next workspace, the one at the start to the previous one`() {
        assertEquals(1, sideAt(x = 990f, width = 1000f, strip = 40f, isRtl = false))
        assertEquals(-1, sideAt(x = 10f, width = 1000f, strip = 40f, isRtl = false))
        assertEquals(0, sideAt(x = 500f, width = 1000f, strip = 40f, isRtl = false))
    }

    @Test
    fun `in right-to-left languages the next workspace is on the left`() {
        assertEquals(1, sideAt(x = 10f, width = 1000f, strip = 40f, isRtl = true))
        assertEquals(-1, sideAt(x = 990f, width = 1000f, strip = 40f, isRtl = true))
    }
}
