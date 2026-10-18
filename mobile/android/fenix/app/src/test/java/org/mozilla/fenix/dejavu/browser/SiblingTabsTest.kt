/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import io.mockk.mockk
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.EngineState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.createTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.fenix.browser.tabstrip.toTabStripState
import org.mozilla.fenix.dejavu.workspaces.PinKind
import org.mozilla.fenix.dejavu.workspaces.PinnedItem
import org.mozilla.fenix.dejavu.workspaces.SplitView
import org.mozilla.fenix.dejavu.workspaces.Workspace
import org.mozilla.fenix.dejavu.workspaces.WorkspaceState

class SiblingTabsTest {
    private fun tab(id: String, awake: Boolean = false, private: Boolean = false): TabSessionState {
        val tab = createTab(url = "https://$id.example/", id = id, title = id, private = private)
        return if (awake) tab.copy(engineState = EngineState(engineSession = mockk(relaxed = true))) else tab
    }

    private fun pin(
        id: String,
        tabId: String? = null,
        workspaceId: String? = HOME,
        parentId: String? = null,
        essential: Boolean = false,
        containerId: String? = null,
        url: String? = "https://$id.example/",
    ) = PinnedItem(
        id = id,
        workspaceId = if (essential) null else workspaceId,
        parentId = parentId,
        kind = PinKind.TAB,
        title = id,
        url = url,
        containerId = containerId,
        essential = essential,
        tabId = tabId,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun folder(id: String, workspaceId: String = HOME) = PinnedItem(
        id = id,
        workspaceId = workspaceId,
        parentId = null,
        kind = PinKind.FOLDER,
        title = id,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun workspace(id: String, containerId: String? = null) =
        Workspace(id = id, name = id, containerId = containerId, createdAt = 1L, updatedAt = 1L)

    private fun state(
        pins: List<PinnedItem> = emptyList(),
        assignments: Map<String, String> = emptyMap(),
        splits: List<SplitView> = emptyList(),
        workspaces: List<Workspace> = listOf(workspace(HOME), workspace(WORK)),
    ) = WorkspaceState(workspaces, pins, activeWorkspaceId = HOME, assignments = assignments, splits = splits)

    private fun siblings(
        selected: String,
        tabs: List<TabSessionState>,
        state: WorkspaceState,
        perContainer: Boolean = false,
    ): List<SiblingTab>? = siblingTabs(BrowserState(tabs = tabs, selectedTabId = selected), state, perContainer)

    private fun List<SiblingTab>?.keys() = this?.map { it.key }

    @Test
    fun `an unpinned tab goes through the other unpinned tabs of its workspace, sleeping ones too`() {
        val tabs = listOf(tab("a", awake = true), tab("pinned"), tab("b"), tab("work"), tab("c"))
        val state = state(pins = listOf(pin("p", tabId = "pinned")), assignments = mapOf("work" to WORK))

        val siblings = siblings("a", tabs, state)

        assertEquals(listOf("a", "b", "c"), siblings.keys())
        assertEquals(listOf(true, false, false), siblings?.map { it.isAwake })
    }

    @Test
    fun `a pinned tab in a folder goes through that folder's pinned tabs, closed ones too`() {
        val tabs = listOf(tab("one"), tab("top"), tab("other"))
        val state = state(
            pins = listOf(
                pin("top", tabId = "top"),
                folder("f"),
                pin("first", tabId = "one", parentId = "f"),
                pin("closed", parentId = "f"),
                folder("g"),
                pin("elsewhere", tabId = "other", parentId = "g"),
            ),
        )

        val siblings = siblings("one", tabs, state)

        assertEquals(listOf("one", "dejavu-pin-closed"), siblings.keys())
        val closed = siblings!![1] as SiblingTab.Closed
        assertEquals("https://closed.example/", closed.standIn().content.url)
    }

    @Test
    fun `a pinned tab at the top goes through the other pinned tabs at the top of its workspace`() {
        val tabs = listOf(tab("one"), tab("two"), tab("inside"))
        val state = state(
            pins = listOf(
                pin("first", tabId = "one"),
                folder("f"),
                pin("nested", tabId = "inside", parentId = "f"),
                pin("second", tabId = "two"),
                pin("work", workspaceId = WORK),
                pin("essential", essential = true),
            ),
        )

        assertEquals(listOf("one", "two"), siblings("two", tabs, state).keys())
    }

    @Test
    fun `an essential goes through the essentials its workspace shows`() {
        val tabs = listOf(tab("mail"))
        val state = state(
            pins = listOf(
                pin("mail", tabId = "mail", essential = true),
                pin("bank", essential = true, containerId = "banking"),
                pin("news", essential = true),
            ),
            workspaces = listOf(workspace(HOME), workspace(WORK, containerId = "banking")),
        )

        assertEquals(
            listOf("mail", "dejavu-pin-bank", "dejavu-pin-news"),
            siblings("mail", tabs, state, perContainer = false).keys(),
        )
        assertEquals(listOf("mail", "dejavu-pin-news"), siblings("mail", tabs, state, perContainer = true).keys())
    }

    @Test
    fun `the two tabs of a split view come next to each other`() {
        val tabs = listOf(tab("a"), tab("b"), tab("c"), tab("d"))
        val state = state(splits = listOf(SplitView("split", listOf("d", "a"))))

        assertEquals(listOf("a", "d", "b", "c"), siblings("b", tabs, state).keys())
    }

    @Test
    fun `a private tab goes with the other private tabs`() {
        val tabs = listOf(tab("a"), tab("secret", private = true))

        assertNull(siblings("secret", tabs, state()))
    }

    @Test
    fun `a closed pinned tab's stand-in has an id the thumbnail cache takes`() {
        val zenId = "{4D2C1B0A-9E8F-4A7B-8C6D-5E4F3A2B1C0D}:" + "x".repeat(40)

        val standIn = SiblingTab.Closed(pin(zenId)).standIn()

        assertTrue(standIn.id, standIn.id.matches(Regex("[a-z0-9_-]{1,64}")))
    }

    @Test
    fun `a closed pinned tab without an address is left out`() {
        val tabs = listOf(tab("one"))
        val state = state(pins = listOf(pin("first", tabId = "one"), pin("empty", url = null)))

        assertEquals(listOf("one"), siblings("one", tabs, state).keys())
    }

    @Test
    fun `the tab bar shows the siblings, sleeping ones faded, and closes only unpinned tabs`() {
        val tabs = listOf(tab("one", awake = true), tab("loose", awake = true))
        val state = state(pins = listOf(pin("first", tabId = "one"), pin("closed")))
        val browser = BrowserState(tabs = tabs, selectedTabId = "one")

        val strip = browser.toTabStripState(
            isSelectDisabled = false,
            isPossiblyPrivateMode = false,
            addTab = {},
            closeTab = { _, _ -> },
            siblings = siblingTabs(browser, state, perContainer = false),
        )

        assertEquals(listOf("one", "dejavu-pin-closed"), strip.tabs.map { it.id })
        assertTrue(strip.tabs[0].isSelected)
        assertFalse(strip.tabs[0].isAsleep)
        assertTrue(strip.tabs[1].isAsleep)
        assertTrue(strip.tabs.none { it.isCloseButtonVisible })

        val unpinned = BrowserState(tabs = tabs, selectedTabId = "loose")
        val looseStrip = unpinned.toTabStripState(
            isSelectDisabled = false,
            isPossiblyPrivateMode = false,
            addTab = {},
            closeTab = { _, _ -> },
            siblings = siblingTabs(unpinned, state, perContainer = false),
        )
        assertEquals(listOf("loose"), looseStrip.tabs.map { it.id })
        assertTrue(looseStrip.tabs.single().isCloseButtonVisible)
    }

    private companion object {
        const val HOME = "home"
        const val WORK = "work"
    }
}
