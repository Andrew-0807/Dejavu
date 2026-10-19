/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.menu

import mozilla.components.browser.state.state.recover.TabState
import org.junit.Assert.assertEquals
import org.junit.Test

class DejavuTabCommandsTest {
    private val old = TabState(id = "old", url = "https://old.example", lastAccess = 1)
    private val recent = TabState(id = "recent", url = "https://recent.example", lastAccess = 2)
    private val privateTab = TabState(id = "private", url = "https://private.example", private = true)

    @Test
    fun `prefers the tabs undo still offers in the same browsing mode`() {
        assertEquals(RestoreChoice.Undo, restoreChoiceOf(private = false, undoTabs = listOf(old), closedTabs = listOf(recent)))
        assertEquals(RestoreChoice.Undo, restoreChoiceOf(private = true, undoTabs = listOf(privateTab), closedTabs = listOf(recent)))
    }

    @Test
    fun `restores the closed tab used last in normal browsing`() {
        assertEquals(
            RestoreChoice.Closed(recent),
            restoreChoiceOf(private = false, undoTabs = listOf(privateTab), closedTabs = listOf(old, recent)),
        )
    }

    @Test
    fun `never restores normal tabs in private browsing`() {
        assertEquals(RestoreChoice.None, restoreChoiceOf(private = true, undoTabs = listOf(old), closedTabs = listOf(recent)))
        assertEquals(RestoreChoice.None, restoreChoiceOf(private = false, undoTabs = emptyList(), closedTabs = emptyList()))
    }
}
