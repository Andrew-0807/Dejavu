/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.workspaces

import android.content.Context
import mozilla.components.support.test.robolectric.testContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenedByPagesTest {
    private fun newRepository(): WorkspaceRepository =
        WorkspaceRepository::class.java.getDeclaredConstructor(Context::class.java)
            .apply { isAccessible = true }
            .newInstance(testContext)

    @Test
    fun `a tab a page opened stays marked after a restart, until the user moves it to a workspace`() {
        val repository = newRepository()
        repository.syncWithTabs(setOf(POP_UP, OTHER), restoreComplete = true)
        repository.markOpenedByPage(POP_UP)

        assertEquals(setOf(POP_UP), newRepository().state.value.openedByPages)

        repository.addWorkspace("Work", containerId = null, icon = null, theme = null)
        repository.moveToWorkspace(setOf(POP_UP), emptySet(), repository.state.value.activeWorkspaceId)
        assertTrue(repository.state.value.openedByPages.isEmpty())
    }

    @Test
    fun `a tab that was closed loses its mark once the browser restored its tabs`() {
        val repository = newRepository()
        repository.markOpenedByPage(POP_UP)

        repository.syncWithTabs(setOf(OTHER), restoreComplete = false)
        assertEquals(setOf(POP_UP), repository.state.value.openedByPages)

        repository.syncWithTabs(setOf(OTHER), restoreComplete = true)
        assertTrue(repository.state.value.openedByPages.isEmpty())
    }

    @Test
    fun `a tab that replaces one a page opened keeps its mark`() {
        val repository = newRepository()
        repository.markOpenedByPage(POP_UP)

        repository.replaceTab(POP_UP, OTHER)

        assertEquals(setOf(OTHER), repository.state.value.openedByPages)
    }

    private companion object {
        const val POP_UP = "pop-up"
        const val OTHER = "other"
    }
}
