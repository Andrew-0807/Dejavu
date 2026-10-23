/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.history

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.action.UndoAction
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.recover.RecoverableTab
import mozilla.components.browser.state.state.recover.TabState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.fenix.dejavu.workspaces.WorkspaceRepository

/**
 * Where a tab was when it closed.
 *
 * @property workspaceId The workspace it was in.
 * @property pinId The pinned tab or essential it was the open tab of, if any.
 * @property closedAt When it closed, in milliseconds.
 * @property containerId The container it was in, which the recently closed tabs do not keep.
 * @property temporary Whether that container was a temporary one, which goes away once its last tab closed.
 */
data class ClosedTabOrigin(
    val workspaceId: String,
    val pinId: String?,
    val closedAt: Long,
    val containerId: String? = null,
    val temporary: Boolean = false,
)

/**
 * Remembers where the latest closed tabs were, by tab id, so a reopened tab goes back to its workspace and pinned tab.
 * A closed tab keeps its id in undo and in the recently closed tabs, which only know when it was last shown, so this is
 * also what tells which tab closed last. Kept in a small file, the [MAX_TABS] latest only. Private tabs are never
 * remembered.
 */
class ClosedTabOrigins internal constructor(private val file: File) {
    // In the order the tabs closed, so the one closed first is the first to go.
    private val origins = object : LinkedHashMap<String, ClosedTabOrigin>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ClosedTabOrigin>) = size > MAX_TABS
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saving: Job? = null

    @Volatile
    private var loaded = false

    /** Where tab [tabId] was when it closed, or `null` if it is not remembered. */
    fun originOf(tabId: String): ClosedTabOrigin? = synchronized(origins) { origins[tabId] }

    /** Remembers where the closing tabs of [closing] were, by tab id. */
    fun record(closing: Map<String, ClosedTabOrigin>) {
        if (closing.isEmpty()) return
        synchronized(origins) {
            closing.forEach { (tabId, origin) ->
                origins.remove(tabId)
                origins[tabId] = origin
            }
        }
        scheduleSave()
    }

    /** Forgets tab [tabId], which opened again, and returns where it was. */
    fun remove(tabId: String): ClosedTabOrigin? {
        val removed = synchronized(origins) { origins.remove(tabId) }
        if (removed != null) scheduleSave()
        return removed
    }

    /** The tab of [closedTabs] that closed last. Tabs closed before Dejavu remembered them count from when last shown. */
    fun latest(closedTabs: List<TabState>): TabState? =
        closedTabs.maxByOrNull { tab -> originOf(tab.id)?.closedAt ?: tab.lastAccess }

    /** Reads the file, keeping what was recorded before it was read as the newer. */
    fun load() {
        if (loaded) return
        val saved = runCatching { JSONObject(file.readText()) }.getOrNull()
        synchronized(origins) {
            if (saved != null) {
                val newer = LinkedHashMap(origins)
                origins.clear()
                saved.optJSONArray(KEY_TABS)?.let { tabs ->
                    for (index in 0 until tabs.length()) {
                        val tab = tabs.optJSONArray(index) ?: continue
                        val tabId = tab.optString(0).ifEmpty { null } ?: continue
                        val workspaceId = tab.optString(1).ifEmpty { null } ?: continue
                        origins[tabId] = ClosedTabOrigin(
                            workspaceId = workspaceId,
                            pinId = tab.optString(2).ifEmpty { null },
                            closedAt = tab.optLong(3),
                            containerId = tab.optString(4).ifEmpty { null },
                            temporary = tab.optBoolean(5),
                        )
                    }
                }
                newer.forEach { (tabId, origin) ->
                    origins.remove(tabId)
                    origins[tabId] = origin
                }
            }
            loaded = true
        }
    }

    private fun scheduleSave() {
        saving?.cancel()
        saving = scope.launch {
            delay(SAVE_DELAY_MS)
            load()
            val tabs = JSONArray()
            synchronized(origins) {
                origins.forEach { (tabId, origin) ->
                    tabs.put(
                        JSONArray()
                            .put(tabId)
                            .put(origin.workspaceId)
                            .put(origin.pinId.orEmpty())
                            .put(origin.closedAt)
                            .put(origin.containerId.orEmpty())
                            .put(origin.temporary),
                    )
                }
            }
            runCatching {
                val temporary = File(file.parentFile, file.name + ".tmp")
                temporary.writeText(JSONObject().put(KEY_TABS, tabs).toString())
                temporary.renameTo(file)
            }
        }
    }

    companion object {
        private const val FILE_NAME = "dejavu_closed_tabs.json"
        private const val KEY_TABS = "tabs"
        private const val MAX_TABS = 200
        private const val SAVE_DELAY_MS = 1000L

        @Volatile
        private var instance: ClosedTabOrigins? = null

        /** Returns the process wide [ClosedTabOrigins]. Does not read the file, see [load]. */
        fun get(context: Context): ClosedTabOrigins =
            instance ?: synchronized(this) {
                instance ?: ClosedTabOrigins(File(context.applicationContext.filesDir, FILE_NAME)).also { instance = it }
            }
    }
}

/**
 * Records in [ClosedTabOrigins] where the tabs that can be reopened were as they close, and puts reopened tabs back
 * there: in their container, in their workspace, and the tab of a pinned tab in that pinned tab, unless it opened again
 * since. A tab whose temporary container went away comes back in a new one. Tabs Dejavu closes on its own never reach
 * undo, and so are not recorded.
 *
 * @param origins Where the latest closed tabs were.
 * @param repository The workspaces, or `null` while they are not loaded yet.
 * @param isTemporary Whether the container with this context id is a temporary one.
 * @param newTemporary Starts a new temporary container and returns its context id.
 * @param now The current time in milliseconds.
 */
internal class ClosedTabOriginsMiddleware(
    private val origins: ClosedTabOrigins,
    private val repository: () -> WorkspaceRepository? = WorkspaceRepository::peek,
    private val isTemporary: (String) -> Boolean = { false },
    private val newTemporary: (Store<BrowserState, BrowserAction>) -> String? = { null },
    private val now: () -> Long = System::currentTimeMillis,
) : Middleware<BrowserState, BrowserAction> {
    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        when (action) {
            // Tabs reach undo while they are still open, so their workspace is still known.
            is UndoAction.AddRecoverableTabs -> {
                record(action.tabs)
                next(action)
            }
            is TabListAction.RestoreAction -> {
                next(action.copy(tabs = action.tabs.map { withContainer(store, it) }))
                putBack(store, action.tabs)
            }
            else -> next(action)
        }
    }

    private fun record(tabs: List<RecoverableTab>) {
        val state = repository()?.state?.value ?: return
        val closedAt = now()
        origins.record(
            tabs.filterNot { it.state.private }.associate { tab ->
                val pin = state.pinOf(tab.state.id)
                val containerId = tab.state.contextId
                tab.state.id to ClosedTabOrigin(
                    workspaceId = pin?.workspaceId ?: state.workspaceOf(tab.state.id),
                    pinId = pin?.id,
                    closedAt = closedAt,
                    containerId = containerId,
                    temporary = containerId != null && isTemporary(containerId),
                )
            },
        )
    }

    /** [tab] in the container it closed in, when it comes back without one, like from the recently closed tabs. */
    private fun withContainer(store: Store<BrowserState, BrowserAction>, tab: RecoverableTab): RecoverableTab {
        if (tab.state.private || tab.state.contextId != null) return tab
        val origin = origins.originOf(tab.state.id) ?: return tab
        val containerId = when {
            origin.containerId == null -> null
            store.state.containers.containsKey(origin.containerId) -> origin.containerId
            origin.temporary -> newTemporary(store)
            else -> null
        }
        return if (containerId == null) tab else tab.copy(state = tab.state.copy(contextId = containerId))
    }

    private fun putBack(store: Store<BrowserState, BrowserAction>, tabs: List<RecoverableTab>) {
        val workspaces = repository() ?: return
        tabs.filterNot { it.state.private }.forEach { tab ->
            val tabId = tab.state.id
            val origin = origins.remove(tabId) ?: return@forEach
            val state = workspaces.state.value
            val pin = origin.pinId?.let { id -> state.pins.firstOrNull { it.id == id && !it.isFolder } }
            val pinIsFree = pin != null &&
                (pin.tabId == null || pin.tabId == tabId || store.state.findTab(pin.tabId) == null)
            when {
                pin != null && pinIsFree -> workspaces.attachPinned(pin.id, tabId)
                state.workspaces.any { it.id == origin.workspaceId } -> workspaces.assignTab(tabId, origin.workspaceId)
            }
        }
    }
}
