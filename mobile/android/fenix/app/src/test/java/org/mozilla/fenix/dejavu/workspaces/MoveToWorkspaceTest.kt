/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.workspaces

import android.content.Context
import mozilla.components.support.test.robolectric.testContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MoveToWorkspaceTest {
    private fun newRepository(): WorkspaceRepository =
        WorkspaceRepository::class.java.getDeclaredConstructor(Context::class.java)
            .apply { isAccessible = true }
            .newInstance(testContext)

    private fun source(tabId: String) = PinSource(tabId, "https://$tabId.example/", tabId, containerId = null)

    /** A folder holding a pinned tab in the first workspace, then a pinned tab in a second one, "Work". */
    private fun repositoryWithFolder(): WorkspaceRepository {
        val repository = newRepository()
        val home = repository.state.value.activeWorkspaceId
        repository.pinTabs(listOf(source(INSIDE)))
        repository.createFolder(home, parentId = null, name = "Folder", itemIds = setOf(pinOf(repository, INSIDE)))
        repository.addWorkspace("Work", containerId = null, icon = null, theme = null)
        repository.pinTabs(listOf(source(WORK_PIN)))
        return repository
    }

    private fun pinOf(repository: WorkspaceRepository, tabId: String) = repository.state.value.pinOf(tabId)!!.id

    private fun folderOf(repository: WorkspaceRepository) = repository.state.value.pins.first { it.isFolder }.id

    @Test
    fun `a folder dragged from another workspace comes over with everything inside it, then lands where it was dropped`() {
        val repository = repositoryWithFolder()
        val work = repository.state.value.activeWorkspaceId
        val folder = folderOf(repository)
        val workPin = pinOf(repository, WORK_PIN)

        repository.moveToWorkspace(tabIds = emptySet(), itemIds = setOf(folder), workspaceId = work)
        repository.placePins(work, setOf(folder), emptyList(), PinPlacement.Next(workPin, after = true))

        val state = repository.state.value
        assertEquals(listOf(workPin, folder), state.children(work, parentId = null).map { it.id })
        assertEquals(listOf(pinOf(repository, INSIDE)), state.children(work, parentId = folder).map { it.id })
        assertEquals(work, state.assignments[INSIDE])
    }

    @Test
    fun `a tab dragged from another workspace into its pinned tabs is pinned there`() {
        val repository = repositoryWithFolder()
        val work = repository.state.value.activeWorkspaceId

        repository.moveToWorkspace(tabIds = setOf(LOOSE), itemIds = emptySet(), workspaceId = work)
        repository.placePins(work, emptySet(), listOf(source(LOOSE)), PinPlacement.Edge(atEnd = true))

        val state = repository.state.value
        assertEquals(work, state.pinOf(LOOSE)?.workspaceId)
        assertEquals(
            listOf(pinOf(repository, WORK_PIN), pinOf(repository, LOOSE)),
            state.children(work, parentId = null).map { it.id },
        )
        assertEquals(work, state.assignments[LOOSE])
    }

    @Test
    fun `items moved to a workspace keep their place in the list of pinned items otherwise`() {
        val repository = repositoryWithFolder()
        val work = repository.state.value.activeWorkspaceId
        val folder = folderOf(repository)

        repository.moveToWorkspace(tabIds = emptySet(), itemIds = setOf(folder), workspaceId = work)

        assertEquals(
            listOf(folder, pinOf(repository, WORK_PIN)),
            repository.state.value.children(work, parentId = null).map { it.id },
        )
    }

    private companion object {
        const val INSIDE = "inside"
        const val WORK_PIN = "work-pin"
        const val LOOSE = "loose"
    }
}
