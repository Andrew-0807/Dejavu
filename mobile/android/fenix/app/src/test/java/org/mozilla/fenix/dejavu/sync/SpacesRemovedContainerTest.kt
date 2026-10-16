/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.sync

import mozilla.components.browser.state.state.ContainerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.dejavu.containers.ContainerColor
import org.mozilla.fenix.dejavu.containers.ContainerRecord
import org.mozilla.fenix.dejavu.sync.ZenRecords.SPACE_PERSONAL
import org.mozilla.fenix.dejavu.sync.ZenRecords.SPACE_WORK
import org.mozilla.fenix.dejavu.sync.ZenRecords.TAB_NORMAL
import org.mozilla.fenix.dejavu.sync.ZenRecords.TAB_PINNED
import org.mozilla.fenix.dejavu.sync.ZenRecords.TAB_RENAMED
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpacesRemovedContainerTest {
    private val containers = ZenRecords.dejavuContainers()
    private val news = "https://news.example/"
    private val removed = "removed-container"

    /** Zen's desktop with its unpinned tab, a pinned tab and the work space in a container that was removed. */
    private val records: List<SpacesRecord> = ZenRecords.desktop().map { record ->
        when (record.id) {
            TAB_NORMAL -> ZenRecords.tab(TAB_NORMAL, news, SPACE_PERSONAL, pinned = false, containerGuid = removed)
            TAB_PINNED -> ZenRecords.tab(TAB_PINNED, "https://example.com/", SPACE_PERSONAL, containerGuid = removed)
            SPACE_WORK -> ZenRecords.space(SPACE_WORK, "Work", listOf(TAB_RENAMED), icon = "💼", containerGuid = removed)
            else -> record
        }
    }

    private fun apply(removedContainers: Set<String>): ApplyResult = SpacesApplier(
        server = emptyMap(),
        containerIds = containers.map { it.contextId }.toSet(),
        firstSync = true,
        defaultName = "Home",
        now = 10L,
        normalTabs = true,
        tabs = emptyMap(),
        removedContainers = removedContainers,
    ).apply(ZenRecords.freshDejavu(), records.filter { it.kind != RecordKind.CONTAINER })

    @Test
    fun `tabs and a space whose container was removed are applied without it instead of waiting for it`() {
        val result = apply(removedContainers = setOf(removed))

        assertTrue(result.failed.isEmpty())
        assertEquals(
            listOf(TabOp.Open(TAB_NORMAL, news, "Title of $news", removed, SPACE_PERSONAL, containerRemoved = true)),
            result.tabOps,
        )
        assertEquals(removed, result.state.pins.single { it.id == TAB_PINNED }.containerId)
        assertEquals(removed, result.state.workspaces.single { it.id == SPACE_WORK }.containerId)
    }

    @Test
    fun `a tab in a container that is not known to be removed still waits for it`() {
        val result = apply(removedContainers = emptySet())

        assertTrue(result.failed.map { it.id }.containsAll(listOf(TAB_NORMAL, TAB_PINNED, SPACE_WORK)))
        assertTrue(result.tabOps.isEmpty())
    }

    @Test
    fun `what was applied without its removed container goes back unchanged`() {
        val state = apply(removedContainers = setOf(removed)).state
        val temporary =
            ContainerRecord("tmp-1", "tmp1", ContainerColor.WHITE, ContainerState.Icon.CIRCLE, 5L, 5L, temporary = true)
        val tab = LocalTab(TAB_NORMAL, news, "Title of $news", contextId = temporary.contextId, awake = false)
        val server = records.associateBy { it.id } + (removed to SpacesRecord.tombstone(removed))

        val changes = SpacesProjector(
            LocalSpaces(state, containers + temporary, normalTabs = true, tabs = listOf(tab)),
            server,
            seen = mapOf(TAB_NORMAL to tab.fingerprint),
        ).project().changesAgainst(server).map { it.id }

        assertFalse(TAB_NORMAL in changes)
        assertFalse(TAB_PINNED in changes)
        assertFalse(SPACE_WORK in changes)
    }
}
