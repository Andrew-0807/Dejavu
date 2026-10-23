/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.sync

import mozilla.components.browser.state.state.ContainerState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.dejavu.containers.ContainerColor
import org.mozilla.fenix.dejavu.containers.ContainerRecord
import org.mozilla.fenix.dejavu.sync.ZenRecords.FOLDER
import org.mozilla.fenix.dejavu.sync.ZenRecords.SPACE_PERSONAL
import org.mozilla.fenix.dejavu.sync.ZenRecords.SPACE_WORK
import org.mozilla.fenix.dejavu.sync.ZenRecords.SPLIT
import org.mozilla.fenix.dejavu.sync.ZenRecords.SUBFOLDER
import org.mozilla.fenix.dejavu.sync.ZenRecords.TAB_ESSENTIAL
import org.mozilla.fenix.dejavu.sync.ZenRecords.TAB_ESSENTIAL_CONTAINER
import org.mozilla.fenix.dejavu.sync.ZenRecords.TAB_IN_FOLDER
import org.mozilla.fenix.dejavu.sync.ZenRecords.TAB_IN_SUBFOLDER
import org.mozilla.fenix.dejavu.sync.ZenRecords.TAB_NORMAL
import org.mozilla.fenix.dejavu.sync.ZenRecords.TAB_PINNED
import org.mozilla.fenix.dejavu.workspaces.PinKind
import org.mozilla.fenix.dejavu.workspaces.PinnedItem
import org.mozilla.fenix.dejavu.workspaces.WorkspaceState
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpacesNormalTabsTest {
    private val containers = ZenRecords.dejavuContainers()
    private val containerIds = containers.map { it.contextId }.toSet()
    private val news = "https://news.example/"
    private val newsTitle = "Title of $news"

    private fun apply(
        state: WorkspaceState,
        records: List<SpacesRecord>,
        tabs: List<LocalTab>?,
        normalTabs: Boolean = true,
        server: Map<String, SpacesRecord> = emptyMap(),
    ): ApplyResult = SpacesApplier(
        server = server,
        containerIds = containerIds,
        firstSync = server.isEmpty(),
        defaultName = "Home",
        now = 10L,
        normalTabs = normalTabs,
        tabs = tabs?.associateBy { it.id },
    ).apply(state, records.filter { it.kind != RecordKind.CONTAINER })

    private fun desktop(tabs: List<LocalTab>? = emptyList(), normalTabs: Boolean = true) =
        apply(ZenRecords.freshDejavu(), ZenRecords.desktop(), tabs, normalTabs)

    private fun changes(
        state: WorkspaceState,
        tabs: List<LocalTab>?,
        seen: Map<String, String> = emptyMap(),
        server: Map<String, SpacesRecord> = ZenRecords.desktop().associateBy { it.id },
        forgotten: Set<String> = emptySet(),
    ): Map<String, SpacesRecord> =
        SpacesProjector(LocalSpaces(state, containers, normalTabs = true, tabs = tabs, forgotten = forgotten), server, seen)
            .project()
            .changesAgainst(server)
            .associateBy { it.id }

    private fun tab(id: String, url: String = news, title: String = newsTitle, awake: Boolean = false) =
        LocalTab(id, url, title, contextId = null, awake = awake)

    @Test
    fun `without unpinned tabs, Zen's unpinned tabs are neither opened nor deleted`() {
        val result = desktop(normalTabs = false)

        assertTrue(result.tabOps.isEmpty())
        val server = ZenRecords.desktop().associateBy { it.id }
        val changes = SpacesProjector(LocalSpaces(result.state, containers), server).project().changesAgainst(server)
        assertTrue(changes.none { it.id == TAB_NORMAL })
    }

    @Test
    fun `Zen's unpinned tabs open without loading in their workspace`() {
        val result = desktop()

        assertEquals(listOf(TabOp.Open(TAB_NORMAL, news, newsTitle, null, SPACE_PERSONAL)), result.tabOps)
        assertEquals(SPACE_PERSONAL, result.state.assignments[TAB_NORMAL])
        assertTrue(result.state.pins.none { it.id == TAB_NORMAL })
    }

    @Test
    fun `unpinned tabs wait until the browser restored its tabs`() {
        val result = desktop(tabs = null)

        assertTrue(result.tabOps.isEmpty())
        assertEquals(listOf(TAB_NORMAL), result.failed.map { it.id })
    }

    @Test
    fun `an unpinned tab goes out in its workspace, after the pinned items`() {
        val state = desktop().state.let { it.copy(assignments = it.assignments + ("local-1" to SPACE_PERSONAL)) }
        val tabs = listOf(tab(TAB_NORMAL), tab("local-1", "https://local.example/", "Local"))

        val changes = changes(state, tabs)

        val record = changes.getValue("local-1").data!!
        assertEquals(false, record.opt("pinned"))
        assertEquals(SPACE_PERSONAL, record.string("workspaceUuid"))
        assertEquals("https://local.example/", record.string("url"))
        assertFalse(TAB_NORMAL in changes)
        assertEquals(TAB_NORMAL to "local-1", changes.getValue(SPACE_PERSONAL).data!!.strings("children")!!.takeLast(2).let { it[0] to it[1] })
    }

    @Test
    fun `a tab in a temporary container goes out without a container, and keeps it when it comes back`() {
        val temporary =
            ContainerRecord("tmp-1", "tmp1", ContainerColor.WHITE, ContainerState.Icon.FINGERPRINT, 5L, 5L, temporary = true)
        val state = desktop().state.let { it.copy(assignments = it.assignments + ("local-1" to SPACE_PERSONAL)) }
        val local = LocalTab("local-1", "https://local.example/", "Local", contextId = temporary.contextId, awake = true)
        val tabs = listOf(tab(TAB_NORMAL), local)
        val server = ZenRecords.desktop().associateBy { it.id }

        val record = SpacesProjector(LocalSpaces(state, containers + temporary, normalTabs = true, tabs = tabs), server)
            .project()
            .changesAgainst(server)
            .single { it.id == "local-1" }

        val data = record.data!!
        assertEquals(JSONObject.NULL, data.opt("containerGuid"))
        assertEquals(SPACE_PERSONAL, data.string("workspaceUuid"))
        val back = apply(state, listOf(record), tabs, server = server + (record.id to record))
        assertTrue(back.tabOps.isEmpty())
        assertEquals(SPACE_PERSONAL, back.state.assignments["local-1"])
    }

    @Test
    fun `a tab closed here is deleted on the server, one never open here is kept`() {
        val state = desktop().state

        assertTrue(changes(state, tabs = emptyList(), seen = mapOf(TAB_NORMAL to tab(TAB_NORMAL).fingerprint)).getValue(TAB_NORMAL).deleted)
        assertFalse(TAB_NORMAL in changes(state, tabs = emptyList()))
        assertFalse(TAB_NORMAL in changes(state, tabs = null, seen = mapOf(TAB_NORMAL to tab(TAB_NORMAL).fingerprint)))
    }

    @Test
    fun `a tab removed here all at once, like by deleting browsing data, is kept on the server`() {
        val state = desktop().state
        val seen = mapOf(TAB_NORMAL to tab(TAB_NORMAL).fingerprint)

        assertFalse(TAB_NORMAL in changes(state, tabs = emptyList(), seen = seen, forgotten = setOf(TAB_NORMAL)))
    }

    @Test
    fun `a page loaded here is not sent back after another device moved on, only a page opened here since`() {
        val state = desktop().state
        val older = tab(TAB_NORMAL, "https://older.example/", "Older", awake = true)
        val seen = mapOf(TAB_NORMAL to older.fingerprint)

        assertFalse(TAB_NORMAL in changes(state, listOf(older), seen))
        val newer = tab(TAB_NORMAL, "https://newer.example/", "Newer", awake = true)
        assertEquals("https://newer.example/", changes(state, listOf(newer), seen).getValue(TAB_NORMAL).data!!.string("url"))
    }

    @Test
    fun `an unloaded tab moves to the address given elsewhere, and closes when deleted elsewhere`() {
        val state = desktop().state
        val server = ZenRecords.desktop().associateBy { it.id }
        val unloaded = tab(TAB_NORMAL, "https://older.example/", "Older")
        val record = ZenRecords.tab(TAB_NORMAL, news, SPACE_PERSONAL, pinned = false)

        assertEquals(
            listOf(TabOp.Retarget(TAB_NORMAL, news, newsTitle)),
            apply(state, listOf(record), listOf(unloaded), server = server).tabOps,
        )
        assertTrue(apply(state, listOf(record), listOf(unloaded.copy(awake = true)), server = server).tabOps.isEmpty())
        assertEquals(
            listOf(TabOp.Close(TAB_NORMAL)),
            apply(state, listOf(SpacesRecord.tombstone(TAB_NORMAL)), listOf(unloaded), server = server).tabOps,
        )
    }

    @Test
    fun `a tab pinned elsewhere pins the tab open here`() {
        val state = desktop().state
        val server = ZenRecords.desktop().associateBy { it.id }
        val record = ZenRecords.tab(TAB_NORMAL, news, SPACE_PERSONAL, pinned = true)

        val result = apply(state, listOf(record), listOf(tab(TAB_NORMAL)), server = server)

        assertEquals(TAB_NORMAL, result.state.pins.single { it.id == TAB_NORMAL }.tabId)
        assertTrue(result.tabOps.isEmpty())
    }

    @Test
    fun `a tab unpinned elsewhere stays open here and syncs under the pin's id`() {
        val pin = PinnedItem(
            id = "pin-1",
            workspaceId = SPACE_PERSONAL,
            parentId = null,
            kind = PinKind.TAB,
            title = "Pinned",
            url = news,
            tabId = "tab-9",
            createdAt = 1L,
            updatedAt = 1L,
        )
        val state = desktop().state.let { it.copy(pins = it.pins + pin) }
        val record = ZenRecords.tab("pin-1", news, SPACE_PERSONAL, pinned = false)

        val result = apply(state, listOf(record), listOf(tab("tab-9")), server = mapOf("x" to record))

        assertNull(result.state.pins.firstOrNull { it.id == "pin-1" })
        assertEquals("pin-1", result.state.syncIdOf("tab-9"))
        assertEquals(SPACE_PERSONAL, result.state.assignments["tab-9"])
        assertTrue(result.tabOps.isEmpty())
    }

    @Test
    fun `without unpinned tabs, a tab unpinned here and still open is held, not deleted, like in Zen`() {
        val server = ZenRecords.desktop().associateBy { it.id }
        val synced = desktop(normalTabs = false).state
        val unpinned = synced.copy(
            pins = synced.pins.filterNot { it.id == TAB_PINNED },
            assignments = synced.assignments + ("tab-9" to SPACE_PERSONAL),
            tabSyncIds = mapOf("tab-9" to TAB_PINNED),
        )

        fun changes(state: WorkspaceState, tabs: List<LocalTab>?) =
            SpacesProjector(LocalSpaces(state, containers, normalTabs = false, tabs = tabs), server)
                .project()
                .changesAgainst(server)
                .associateBy { it.id }

        assertTrue(changes(unpinned, listOf(tab("tab-9"))).isEmpty())
        assertTrue(changes(unpinned, tabs = null).isEmpty())
        val closed = unpinned.copy(assignments = unpinned.assignments - "tab-9", tabSyncIds = emptyMap())
        assertTrue(changes(closed, tabs = emptyList()).getValue(TAB_PINNED).deleted)
    }

    @Test
    fun `a tab Zen unpinned without syncing unpinned tabs is unpinned here, and nothing goes back`() {
        val records = ZenRecords.desktop()
        val server = records.associateBy { it.id }
        val synced = desktop(normalTabs = false).state
        val withTab = synced.copy(pins = synced.pins.map { if (it.id == TAB_PINNED) it.copy(tabId = "tab-9") else it })
        val space = server.getValue(SPACE_PERSONAL).let { record ->
            SpacesRecord.of(
                record.id,
                RecordKind.SPACE,
                SyncJson.copy(record.data!!).put("children", JSONArray(listOf(FOLDER, SPLIT, TAB_NORMAL))),
            )
        }

        val open = apply(withTab, listOf(space), listOf(tab("tab-9")), normalTabs = false, server = server)

        assertNull(open.state.pins.firstOrNull { it.id == TAB_PINNED })
        assertEquals(TAB_PINNED, open.state.syncIdOf("tab-9"))
        assertTrue(open.tabOps.isEmpty())
        val after = server + (space.id to space)
        val changes = SpacesProjector(LocalSpaces(open.state, containers, tabs = listOf(tab("tab-9"))), after)
            .project()
            .changesAgainst(after)
        assertTrue(changes.isEmpty())

        val closed = apply(synced, listOf(space), tabs = emptyList(), normalTabs = false, server = server)
        assertEquals(
            listOf(TabOp.Open(TAB_PINNED, "https://example.com/", "Title of https://example.com/", null, SPACE_PERSONAL)),
            closed.tabOps,
        )
        assertEquals(SPACE_PERSONAL, closed.state.assignments[TAB_PINNED])
        val waiting = apply(synced, listOf(space), tabs = null, normalTabs = false, server = server)
        assertEquals(listOf(SPACE_PERSONAL), waiting.failed.map { it.id })
        assertTrue(waiting.state.pins.any { it.id == TAB_PINNED })
    }

    @Test
    fun `a pinned tab another record moves or deletes is not taken as unpinned`() {
        val server = ZenRecords.desktop().associateBy { it.id }
        val synced = desktop(normalTabs = false).state
        val space = ZenRecords.space(SPACE_PERSONAL, "Personal", listOf(FOLDER, SPLIT, TAB_NORMAL))
        val moved = ZenRecords.tab(TAB_PINNED, "https://example.com/", SPACE_PERSONAL, folder = FOLDER)

        val result = apply(synced, listOf(space, moved), tabs = emptyList(), normalTabs = false, server = server)

        assertEquals(FOLDER, result.state.pins.single { it.id == TAB_PINNED }.parentId)
        assertTrue(result.tabOps.isEmpty())
        val essentials = ZenRecords.layout(listOf(SPACE_PERSONAL, SPACE_WORK), mapOf("default" to listOf(TAB_ESSENTIAL)))
        val removed = apply(synced, listOf(essentials), tabs = emptyList(), normalTabs = false, server = server)
        assertNull(removed.state.pins.firstOrNull { it.id == TAB_ESSENTIAL_CONTAINER })
        assertEquals(TAB_ESSENTIAL_CONTAINER, (removed.tabOps.single() as TabOp.Open).id)
    }

    /** Zen's desktop with its unpinned tab kept in the folder, between the folder's pinned tab and its subfolder. */
    private fun desktopWithUnpinnedTabInFolder(): List<SpacesRecord> = ZenRecords.desktop().map { record ->
        when (record.id) {
            SPACE_PERSONAL -> SpacesRecord.of(
                record.id,
                RecordKind.SPACE,
                SyncJson.copy(record.data!!).put("children", JSONArray(listOf(TAB_PINNED, FOLDER, SPLIT))),
            )
            FOLDER -> ZenRecords.folder(FOLDER, "Reading", SPACE_PERSONAL, listOf(TAB_IN_FOLDER, TAB_NORMAL, SUBFOLDER))
            TAB_NORMAL -> ZenRecords.tab(TAB_NORMAL, news, SPACE_PERSONAL, folder = FOLDER, pinned = false)
            else -> record
        }
    }

    @Test
    fun `an unpinned tab Zen keeps in a folder goes back in that folder, so syncing sends nothing back`() {
        val records = desktopWithUnpinnedTabInFolder()
        val result = apply(ZenRecords.freshDejavu(), records, tabs = emptyList())

        val changes = changes(result.state, listOf(tab(TAB_NORMAL)), server = records.associateBy { it.id })

        assertEquals(SPACE_PERSONAL, result.state.assignments[TAB_NORMAL])
        assertFalse(TAB_NORMAL in changes)
        assertFalse(FOLDER in changes)
        assertFalse(SPACE_PERSONAL in changes)
    }

    @Test
    fun `an unpinned tab moved here to another workspace leaves its folder`() {
        val records = desktopWithUnpinnedTabInFolder()
        val synced = apply(ZenRecords.freshDejavu(), records, tabs = emptyList()).state
        val moved = synced.copy(assignments = synced.assignments + (TAB_NORMAL to SPACE_WORK))

        val changes = changes(moved, listOf(tab(TAB_NORMAL)), server = records.associateBy { it.id })

        val record = changes.getValue(TAB_NORMAL).data!!
        assertEquals(SPACE_WORK, record.string("workspaceUuid"))
        assertEquals(JSONObject.NULL, record.opt("folderId"))
        assertEquals(listOf(TAB_IN_FOLDER, SUBFOLDER), changes.getValue(FOLDER).data!!.strings("children"))
        assertEquals(TAB_NORMAL, changes.getValue(SPACE_WORK).data!!.strings("children")!!.last())
    }

    @Test
    fun `an unpinned tab whose folder is deleted here leaves the folder`() {
        val records = desktopWithUnpinnedTabInFolder()
        val synced = apply(ZenRecords.freshDejavu(), records, tabs = emptyList()).state
        val inFolder = setOf(FOLDER, TAB_IN_FOLDER, SUBFOLDER, TAB_IN_SUBFOLDER)
        val deleted = synced.copy(pins = synced.pins.filterNot { it.id in inFolder })

        val changes = changes(deleted, listOf(tab(TAB_NORMAL)), server = records.associateBy { it.id })

        assertTrue(changes.getValue(FOLDER).deleted)
        assertEquals(JSONObject.NULL, changes.getValue(TAB_NORMAL).data!!.opt("folderId"))
        assertEquals(TAB_NORMAL, changes.getValue(SPACE_PERSONAL).data!!.strings("children")!!.last())
    }
}
