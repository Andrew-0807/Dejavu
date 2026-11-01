/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.sync

import org.mozilla.fenix.dejavu.workspaces.PinnedItem
import org.mozilla.fenix.dejavu.workspaces.PinnedSplit
import org.mozilla.fenix.dejavu.workspaces.SplitOrientation

private const val SPLIT_SIZE = 2

/**
 * Zen's split views of two pinned tabs among [records] whose tabs are both among [pins], as Dejavu shows them: Zen's
 * "vsep" puts them side by side, "hsep" one above the other. Zen's grids of more tabs stay separate pinned tabs, as
 * Dejavu shows two pages at most.
 */
internal fun pinnedSplitsOf(records: Collection<SpacesRecord>, pins: List<PinnedItem>): List<PinnedSplit> {
    val tabPins = pins.filterNot { it.isFolder }.map { it.id }.toSet()
    return records.mapNotNull { record ->
        val data = record.data?.takeIf { record.kind == RecordKind.SPLIT && record.isSynced } ?: return@mapNotNull null
        val orientation = when (data.string("gridType")) {
            "vsep" -> SplitOrientation.SIDE_BY_SIDE
            "hsep" -> SplitOrientation.TOP_AND_BOTTOM
            else -> return@mapNotNull null
        }
        val tabs = data.strings("tabs")?.takeIf { it.size == SPLIT_SIZE && it.all(tabPins::contains) }
        tabs?.let { PinnedSplit(record.id, it, orientation) }
    }
}
