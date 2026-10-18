/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import android.content.Context
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import mozilla.components.support.test.robolectric.testContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.dejavu.workspaces.PinSource
import org.mozilla.fenix.dejavu.workspaces.WorkspaceRepository
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TabStripMoveTest {
    private fun newRepository(): WorkspaceRepository =
        WorkspaceRepository::class.java.getDeclaredConstructor(Context::class.java)
            .apply { isAccessible = true }
            .newInstance(testContext)

    private fun source(tabId: String) = PinSource(tabId, "https://$tabId.example/", tabId, containerId = null)

    private fun browser(selected: String, vararg tabIds: String) =
        BrowserState(tabs = tabIds.map { createTab(url = "https://$it.example/", id = it) }, selectedTabId = selected)

    private fun move(repository: WorkspaceRepository, browser: BrowserState, key: String, target: String, after: Boolean) =
        DejavuTabStrip.movePinned(
            siblings = siblingTabs(browser, repository.state.value, perContainer = false).orEmpty(),
            key = key,
            targetKey = target,
            after = after,
            repository = repository,
            perContainer = false,
        )

    @Test
    fun `dragging a pinned tab in the tab bar moves it among the pinned tabs on the home screen`() {
        val repository = newRepository()
        repository.pinTabs(listOf(source("a"), source("b"), source("c")))

        assertTrue(move(repository, browser("a", "a", "b", "c"), key = "c", target = "a", after = false))

        val state = repository.state.value
        assertEquals(listOf("c", "a", "b"), state.children(state.activeWorkspaceId, parentId = null).map { it.tabId })
    }

    @Test
    fun `dragging an essential moves it among the essentials`() {
        val repository = newRepository()
        repository.addToEssentials(listOf(source("a"), source("b"), source("c")), emptySet(), perContainer = false)

        assertTrue(move(repository, browser("b", "a", "b", "c"), key = "a", target = "c", after = true))

        assertEquals(listOf("b", "c", "a"), repository.state.value.essentials.map { it.tabId })
    }

    @Test
    fun `unpinned tabs are left to the browser to move`() {
        val repository = newRepository()

        assertFalse(move(repository, browser("a", "a", "b"), key = "b", target = "a", after = false))
    }
}
