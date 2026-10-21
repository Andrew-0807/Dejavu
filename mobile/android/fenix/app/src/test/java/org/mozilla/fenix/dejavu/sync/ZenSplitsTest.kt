/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.sync

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.dejavu.workspaces.PinKind
import org.mozilla.fenix.dejavu.workspaces.PinnedItem
import org.mozilla.fenix.dejavu.workspaces.PinnedSplit
import org.mozilla.fenix.dejavu.workspaces.SplitOrientation
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ZenSplitsTest {
    private val pins = listOf("a", "b", "c", "d").map { id ->
        PinnedItem(
            id = id,
            workspaceId = "workspace",
            parentId = null,
            kind = PinKind.TAB,
            title = id,
            url = "https://$id.example/",
            createdAt = 1L,
            updatedAt = 1L,
        )
    }

    private fun split(id: String, gridType: String, vararg tabs: String, pinned: Boolean = true) = SpacesRecord.of(
        id,
        RecordKind.SPLIT,
        JSONObject().put("splitId", id).put("gridType", gridType).put("pinned", pinned).put("tabs", JSONArray(tabs)),
    )

    @Test
    fun `Zen's splits of two pinned tabs show the way Zen shows them`() {
        val records = listOf(split("side", "vsep", "a", "b"), split("stacked", "hsep", "c", "d"))

        assertEquals(
            listOf(
                PinnedSplit("side", listOf("a", "b"), SplitOrientation.SIDE_BY_SIDE),
                PinnedSplit("stacked", listOf("c", "d"), SplitOrientation.TOP_AND_BOTTOM),
            ),
            pinnedSplitsOf(records, pins),
        )
    }

    @Test
    fun `grids, splits of tabs that are not pinned here and removed splits stay separate tabs`() {
        val records = listOf(
            split("grid", "grid", "a", "b", "c"),
            split("missing", "vsep", "a", "elsewhere"),
            split("unpinned", "vsep", "c", "d", pinned = false),
            SpacesRecord.tombstone("gone"),
        )

        assertEquals(emptyList<PinnedSplit>(), pinnedSplitsOf(records, pins))
    }
}
