/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.home

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.action.RecentlyClosedAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.action.TabListAction.RestoreAction.RestoreLocation
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.selector.normalTabs
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.state.recover.RecoverableTab
import mozilla.components.browser.state.state.recover.TabState
import org.mozilla.fenix.components.Components
import org.mozilla.fenix.dejavu.history.ClosedTabOrigins
import org.mozilla.fenix.dejavu.history.VisitOrigins
import org.mozilla.fenix.dejavu.workspaces.WorkspaceRepository

/** Whether a closed tab is left to reopen: one of the recently closed tabs, or one that can still be undone. */
internal val BrowserState.hasClosedTab: Boolean
    get() = closedTabs.isNotEmpty() || undoHistory.tabs.any { !it.state.private }

/**
 * Reopens closed tabs one at a time, the one closed last first, each in the workspace it was closed in, and shows that
 * workspace. A reopened tab comes first among the tabs of its workspace, right below New Tab, so the next one can be
 * reopened from where the button is. While the last tabs closed can still be undone, they all come back together, where
 * they were, the way undo brings them back. A tab closed in the workspace of a device on the account opens there anew,
 * which sends it to the device. Where a tab was is remembered by [ClosedTabOrigins]; for a tab closed before that, the
 * history of [VisitOrigins] tells, though never the workspace of a device, which would send it there.
 */
internal class ClosedTabReopener(
    private val context: Context,
    private val components: Components,
    private val repository: WorkspaceRepository,
    private val scope: CoroutineScope,
) {
    private val store = components.core.store
    private val tabsUseCases = components.useCases.tabsUseCases
    private val origins by lazy { ClosedTabOrigins.get(context) }

    // Closed tabs being restored, still listed among the recently closed tabs until they are back.
    private val reopening: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Reopens the tab closed last and returns its id, or `null` when no closed tab is left. */
    fun reopen(): String? {
        val state = store.state
        val undoable = state.undoHistory.tabs.filterNot { it.state.private }
        if (undoable.isNotEmpty()) {
            val shown = undoable.firstOrNull { it.state.id == state.undoHistory.selectedTabId } ?: undoable.first()
            show(shown.state)
            tabsUseCases.undo()
            return shown.state.id
        }

        val (open, closed) = state.closedTabs.filterNot { it.id in reopening }.partition { state.findTab(it.id) != null }
        open.forEach { store.dispatch(RecentlyClosedAction.RemoveClosedTabAction(it)) }
        val tab = origins.latest(closed) ?: return null
        val workspaceId = show(tab)
        if (repository.state.value.isDeviceWorkspace(workspaceId)) {
            origins.remove(tab.id)
            store.dispatch(RecentlyClosedAction.RemoveClosedTabAction(tab))
            val tabId = tabsUseCases.addTab(
                url = tab.url,
                startLoading = false,
                title = tab.title,
                contextId = null,
                source = SessionState.Source.Internal.None,
            )
            repository.assignTab(tabId, workspaceId)
            moveFirst(tabId, workspaceId)
            return tabId
        }

        reopening.add(tab.id)
        repository.expectTab(tab.id)
        repository.assignTab(tab.id, workspaceId)
        scope.launch {
            if (store.state.findTab(tab.id) == null) {
                val storage = components.core.recentlyClosedTabsStorage.value.engineStateStorage()
                val engineState = withContext(Dispatchers.IO) { storage.read(tab.id) }
                store.dispatch(TabListAction.RestoreAction(listOf(RecoverableTab(engineState, tab)), restoreLocation = RestoreLocation.BEGINNING))
                tabsUseCases.selectTab(tab.id)
            }
            store.dispatch(RecentlyClosedAction.RemoveClosedTabAction(tab))
            reopening.remove(tab.id)
        }
        return tab.id
    }

    /**
     * Shows the workspace closed tab [tab] was in, unless it was the tab of an essential, which every workspace shows,
     * and returns that workspace, or the one shown when it is not known.
     */
    private fun show(tab: TabState): String {
        val state = repository.state.value
        fun known(workspaceId: String?) = workspaceId?.takeIf { id -> state.workspaces.any { it.id == id } }
        val origin = origins.originOf(tab.id)
        val workspaceId = known(origin?.workspaceId)
            ?: known(VisitOrigins.get(context).originOf(tab.url)?.workspaceId)?.takeUnless(state::isDeviceWorkspace)
            ?: state.activeWorkspaceId
        val essential = origin?.pinId?.let { id -> state.pins.firstOrNull { it.id == id } }?.essential == true
        if (!essential) repository.selectWorkspace(workspaceId)
        return workspaceId
    }

    /** Puts open tab [tabId] first among the tabs of [workspaceId] that are not pinned. */
    private fun moveFirst(tabId: String, workspaceId: String) {
        val state = repository.state.value
        val first = store.state.normalTabs.firstOrNull {
            it.id != tabId && state.workspaceOf(it.id) == workspaceId && state.pinOf(it.id) == null
        } ?: return
        store.dispatch(TabListAction.MoveTabsAction(listOf(tabId), first.id, placeAfter = false))
    }
}
