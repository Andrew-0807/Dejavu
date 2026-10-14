/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.history

import android.content.Context
import java.io.File
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.action.TabListAction.RestoreAction.RestoreLocation
import mozilla.components.browser.state.action.UndoAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.ContainerState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.state.recover.RecoverableTab
import mozilla.components.browser.state.state.recover.TabState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.support.test.robolectric.testContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.dejavu.workspaces.PinSource
import org.mozilla.fenix.dejavu.workspaces.WorkspaceRepository
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClosedTabOriginsTest {
    private fun newRepository(): WorkspaceRepository =
        WorkspaceRepository::class.java.getDeclaredConstructor(Context::class.java)
            .apply { isAccessible = true }
            .newInstance(testContext)

    private fun newOrigins() = ClosedTabOrigins(File.createTempFile("closed", ".json").apply { delete() })

    private fun recoverable(id: String) = RecoverableTab(null, TabState(id = id, url = "https://$id.example/"))

    /** Closes [ids] the way the browser does: they reach undo while still open, then go. */
    private fun BrowserStore.close(vararg ids: String) {
        dispatch(UndoAction.AddRecoverableTabs("tag", ids.map(::recoverable), selectedTabId = null))
        dispatch(TabListAction.RemoveTabsAction(ids.toList()))
    }

    private fun BrowserStore.reopen(vararg ids: String) {
        dispatch(TabListAction.RestoreAction(ids.map(::recoverable), restoreLocation = RestoreLocation.END))
    }

    @Test
    fun `the tab closed last is the one recorded last, before tabs only known by when they were shown`() {
        val origins = newOrigins()
        origins.record(mapOf("old" to ClosedTabOrigin("home", null, closedAt = 100L)))
        origins.record(mapOf("new" to ClosedTabOrigin("home", null, closedAt = 300L)))
        val closed = listOf(
            TabState(id = "old", url = "https://old.example/", lastAccess = 50L),
            TabState(id = "unknown", url = "https://unknown.example/", lastAccess = 200L),
            TabState(id = "new", url = "https://new.example/", lastAccess = 10L),
        )

        assertEquals("new", origins.latest(closed)?.id)
        origins.remove("new")
        assertEquals("unknown", origins.latest(closed.filterNot { it.id == "new" })?.id)
    }

    @Test
    fun `a reopened tab goes back to the workspace it was closed in`() {
        val repository = newRepository()
        val home = repository.state.value.activeWorkspaceId
        repository.addWorkspace("Work", containerId = null, icon = null, theme = null)
        val work = repository.state.value.activeWorkspaceId
        val origins = newOrigins()
        val store = BrowserStore(
            BrowserState(tabs = listOf(createTab("https://a.example/", id = "a"))),
            middleware = listOf(ClosedTabOriginsMiddleware(origins, repository = { repository }, now = { 7L })),
        )
        repository.assignTab("a", work)

        store.close("a")
        repository.syncWithTabs(emptySet(), restoreComplete = true)
        repository.selectWorkspace(home)
        store.reopen("a")

        assertEquals(work, repository.state.value.assignments["a"])
        assertNull(origins.originOf("a"))
    }

    @Test
    fun `a reopened tab of a pinned tab is its tab again, unless the pinned tab opened since`() {
        val repository = newRepository()
        val origins = newOrigins()
        val store = BrowserStore(
            BrowserState(
                tabs = listOf(createTab("https://a.example/", id = "a"), createTab("https://b.example/", id = "b")),
            ),
            middleware = listOf(ClosedTabOriginsMiddleware(origins, repository = { repository })),
        )
        repository.pinTabs(listOf("a", "b").map { PinSource(it, "https://$it.example/", it, containerId = null) })
        val (pinA, pinB) = repository.state.value.pins

        store.close("a", "b")
        repository.syncWithTabs(emptySet(), restoreComplete = true)
        store.dispatch(TabListAction.AddTabAction(createTab("https://b.example/", id = "b2")))
        repository.attachPinned(pinB.id, "b2")
        store.reopen("a", "b")

        val pins = repository.state.value.pins.associateBy { it.id }
        assertEquals("a", pins.getValue(pinA.id).tabId)
        assertEquals("b2", pins.getValue(pinB.id).tabId)
        assertNull(repository.state.value.pinOf("b"))
    }

    @Test
    fun `a tab reopened from the recently closed tabs comes back in its container, or in a new temporary one`() {
        val repository = newRepository()
        val origins = newOrigins()
        val work = ContainerState("work", "Work", ContainerState.Color.BLUE, ContainerState.Icon.BRIEFCASE)
        val store = BrowserStore(
            BrowserState(
                tabs = listOf(
                    createTab("https://a.example/", id = "a", contextId = "work"),
                    createTab("https://b.example/", id = "b", contextId = "tmp"),
                ),
                containers = mapOf(work.contextId to work),
            ),
            middleware = listOf(
                ClosedTabOriginsMiddleware(
                    origins,
                    repository = { repository },
                    isTemporary = { it == "tmp" },
                    newTemporary = { "tmp2" },
                ),
            ),
        )

        store.dispatch(
            UndoAction.AddRecoverableTabs(
                "tag",
                store.state.tabs.map { RecoverableTab(null, TabState(id = it.id, url = it.content.url, contextId = it.contextId)) },
                selectedTabId = null,
            ),
        )
        store.dispatch(TabListAction.RemoveTabsAction(listOf("a", "b")))
        store.reopen("a", "b")

        assertEquals(listOf("work", "tmp2"), store.state.tabs.map { it.contextId })
    }

    @Test
    fun `private tabs are not remembered`() {
        val repository = newRepository()
        val origins = newOrigins()
        val store = BrowserStore(
            BrowserState(tabs = listOf(createTab("https://p.example/", id = "p", private = true))),
            middleware = listOf(ClosedTabOriginsMiddleware(origins, repository = { repository })),
        )

        store.dispatch(
            UndoAction.AddRecoverableTabs(
                "tag",
                listOf(RecoverableTab(null, TabState(id = "p", url = "https://p.example/", private = true))),
                selectedTabId = null,
            ),
        )

        assertNull(origins.originOf("p"))
    }
}
