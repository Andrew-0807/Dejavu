/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.sync

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.state.state.createTab
import mozilla.components.compose.base.theme.Theme
import mozilla.components.support.test.robolectric.testContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.R
import org.mozilla.fenix.dejavu.workspaces.Workspace
import org.mozilla.fenix.dejavu.workspaces.WorkspaceState
import org.mozilla.fenix.dejavu.workspaces.WorkspaceTheme
import org.mozilla.fenix.theme.FirefoxTheme

@RunWith(AndroidJUnit4::class)
class SyncSourceDialogTest {
    @get:Rule val composeTestRule = createComposeRule()

    private val chosen = mutableListOf<Pair<DejavuSyncSource, SyncStart>>()

    private val home = Workspace("home", "Home", theme = WorkspaceTheme.Gold, createdAt = 1L, updatedAt = 1L)

    private fun string(id: Int) = testContext.getString(id)

    private fun show(workspaces: List<Workspace>, tabIds: List<String> = emptyList()) {
        val state = WorkspaceState(
            workspaces = workspaces,
            pins = emptyList(),
            activeWorkspaceId = workspaces.first().id,
            assignments = emptyMap(),
        )
        val zen = FoundZen(devices = listOf("Laptop"), spacesSyncOn = true, spaces = 2)
        composeTestRule.setContent {
            FirefoxTheme(theme = Theme.Light) {
                SyncSourceDialog(
                    status = DejavuSyncStatus(signedIn = true, found = FoundSyncData(firefox = emptyList(), zen = zen)),
                    workspaces = state,
                    tabs = tabIds.map { createTab(url = "https://$it.example/", id = it) },
                    onChoose = { source, start -> chosen += source to start },
                    onDismiss = {},
                )
            }
        }
    }

    private fun button(id: Int) = composeTestRule.onNode(
        hasText(string(id)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
    )

    @Test
    fun `a fresh Dejavu starts syncing at once, merging`() {
        show(listOf(home))

        button(R.string.dejavu_sync_choose_zen).performClick()

        assertEquals(listOf(DejavuSyncSource.ZEN to SyncStart.MERGE), chosen)
    }

    @Test
    fun `with workspaces and tabs of its own, Dejavu asks whether to merge or start fresh`() {
        val work = Workspace("work", "Work", createdAt = 1L, updatedAt = 1L)
        show(listOf(home, work), tabIds = listOf("a", "b"))

        button(R.string.dejavu_sync_choose_zen).performClick()
        composeTestRule.onNodeWithText(string(R.string.dejavu_sync_start_title)).assertIsDisplayed()
        assertEquals(emptyList<Pair<DejavuSyncSource, SyncStart>>(), chosen)

        composeTestRule.onNode(hasText(string(R.string.dejavu_sync_start_fresh)) and isSelectable()).performClick()
        button(R.string.dejavu_sync_start_fresh).performClick()

        assertEquals(listOf(DejavuSyncSource.ZEN to SyncStart.FRESH), chosen)
    }

    @Test
    fun `merging is the choice until another is picked, and Back goes back to the browsers`() {
        show(listOf(home), tabIds = listOf("a"))

        button(R.string.dejavu_sync_choose_zen).performClick()
        button(R.string.dejavu_back).performClick()
        composeTestRule.onNodeWithText(string(R.string.dejavu_sync_choose_title)).assertIsDisplayed()

        button(R.string.dejavu_sync_choose_zen).performClick()
        button(R.string.dejavu_sync_start_merge).performClick()

        assertEquals(listOf(DejavuSyncSource.ZEN to SyncStart.MERGE), chosen)
    }
}
