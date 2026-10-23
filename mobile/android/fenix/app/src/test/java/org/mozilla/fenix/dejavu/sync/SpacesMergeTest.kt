/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.dejavu.sync.ZenRecords.FOLDER
import org.mozilla.fenix.dejavu.sync.ZenRecords.SPACE_PERSONAL
import org.mozilla.fenix.dejavu.sync.ZenRecords.SPACE_WORK
import org.mozilla.fenix.dejavu.sync.ZenRecords.TAB_PINNED
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpacesMergeTest {
    private val url = "https://example.com/"
    private val base = ZenRecords.tab(TAB_PINNED, url, SPACE_PERSONAL)
    private val movedHere = ZenRecords.tab(TAB_PINNED, url, SPACE_WORK)
    private val renamedInZen = ZenRecords.tab(TAB_PINNED, url, SPACE_PERSONAL, staticLabel = "Zen label")

    @Test
    fun `a field changed on one side keeps that side's change`() {
        val merged = keepLocalChanges(renamedInZen, base, movedHere, incomingTime = 10.0, localTime = 5.0).data!!

        assertEquals("Zen label", merged.string("staticLabel"))
        assertEquals(SPACE_WORK, merged.string("workspaceUuid"))
        assertSame(renamedInZen, keepLocalChanges(renamedInZen, base, base, 10.0, 20.0))
    }

    @Test
    fun `a field changed on both sides keeps the newer change, or the incoming one without times`() {
        val renamedHere = ZenRecords.tab(TAB_PINNED, url, SPACE_PERSONAL, staticLabel = "Phone label")

        fun label(incomingTime: Double?, localTime: Double?) =
            keepLocalChanges(renamedInZen, base, renamedHere, incomingTime, localTime).data!!.string("staticLabel")

        assertEquals("Zen label", label(10.0, 5.0))
        assertEquals("Phone label", label(10.0, 20.0))
        assertEquals("Zen label", label(null, 20.0))
    }

    @Test
    fun `deletions, orders and tabs unpinned elsewhere come in as they are`() {
        val tombstone = SpacesRecord.tombstone(TAB_PINNED)
        assertSame(tombstone, keepLocalChanges(tombstone, base, movedHere, 10.0, 20.0))

        val space = ZenRecords.space(SPACE_PERSONAL, "Personal", listOf(TAB_PINNED, FOLDER))
        val reordered = ZenRecords.space(SPACE_PERSONAL, "Personal", listOf(FOLDER, TAB_PINNED))
        val grownHere = ZenRecords.space(SPACE_PERSONAL, "Personal", listOf(TAB_PINNED, FOLDER, "new-pin"))
        assertSame(reordered, keepLocalChanges(reordered, space, grownHere, 10.0, 20.0))

        val unpinned = ZenRecords.tab(TAB_PINNED, url, SPACE_PERSONAL, pinned = false)
        assertSame(unpinned, keepLocalChanges(unpinned, base, movedHere, 10.0, 20.0))
    }
}
