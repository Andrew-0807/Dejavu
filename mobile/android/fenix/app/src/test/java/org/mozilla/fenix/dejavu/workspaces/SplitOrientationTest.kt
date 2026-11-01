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
class SplitOrientationTest {
    private val workspace = Workspace("workspace", "Home", createdAt = 1L, updatedAt = 1L)

    private fun read(json: String) = WorkspaceSerializer.read(json, defaultName = "Home", now = 1L)

    @Test
    fun `split views keep the way they were made when saved and read back`() {
        val splits = listOf(
            SplitView("side", listOf("a", "b"), SplitOrientation.SIDE_BY_SIDE),
            SplitView("stacked", listOf("c", "d"), SplitOrientation.TOP_AND_BOTTOM),
        )
        val zen = listOf(PinnedSplit("zen", listOf("e", "f"), SplitOrientation.SIDE_BY_SIDE))
        val state = WorkspaceState(listOf(workspace), emptyList(), workspace.id, emptyMap(), splits, pinnedSplits = zen)

        val saved = read(WorkspaceSerializer.write(state))
        assertEquals(splits, saved.splits)
        assertEquals(zen, saved.pinnedSplits)
    }

    @Test
    fun `split views saved before they had a way show top and bottom`() {
        val json = """
            {"version": 2, "workspaces": [{"id": "workspace", "name": "Home"}],
             "splits": [{"id": "old", "tabIds": ["a", "b"]}]}
        """.trimIndent()

        assertEquals(listOf(SplitView("old", listOf("a", "b"), SplitOrientation.TOP_AND_BOTTOM)), read(json).splits)
    }

    @Test
    fun `a split view turned the other way keeps its tabs in their places`() {
        val repository = WorkspaceRepository::class.java.getDeclaredConstructor(Context::class.java)
            .apply { isAccessible = true }
            .newInstance(testContext)
        repository.createSplit("a", "b", SplitOrientation.SIDE_BY_SIDE)

        repository.setSplitOrientation("b", SplitOrientation.TOP_AND_BOTTOM)

        val split = repository.state.value.splitOf("a")
        assertEquals(listOf("a", "b"), split?.tabIds)
        assertEquals(SplitOrientation.TOP_AND_BOTTOM, split?.orientation)
    }
}
