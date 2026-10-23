/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import mozilla.components.browser.state.selector.normalTabs
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.createTab
import org.mozilla.fenix.dejavu.home.isAwake
import org.mozilla.fenix.dejavu.home.label
import org.mozilla.fenix.dejavu.home.pageUrl
import org.mozilla.fenix.dejavu.home.titleOf
import org.mozilla.fenix.dejavu.openClosedPin
import org.mozilla.fenix.dejavu.settings.DejavuSettings
import org.mozilla.fenix.dejavu.workspaces.PinPlacement
import org.mozilla.fenix.dejavu.workspaces.PinnedItem
import org.mozilla.fenix.dejavu.workspaces.WorkspaceRepository
import org.mozilla.fenix.dejavu.workspaces.WorkspaceState
import org.mozilla.fenix.ext.components

/** How faded sleeping and closed tabs are in the tab bar, as on the home screen. */
internal const val SLEEPING_TAB_ALPHA = 0.55f

/**
 * A tab of the place the shown tab is in, see [siblingTabs]: an open tab, awake or sleeping, or a pinned tab whose tab
 * is closed, which opens when it is picked.
 */
internal sealed interface SiblingTab {
    /** The tab bar's key of the tab: the open tab's id, or one made from the pin's. */
    val key: String

    /** The title the home screen shows for it. */
    val title: String

    /** Its pinned tab or essential, for a pinned one. */
    val pin: PinnedItem?

    /** Whether its page is loaded. */
    val isAwake: Boolean

    data class Open(
        val tab: TabSessionState,
        override val title: String,
        override val pin: PinnedItem? = null,
    ) : SiblingTab {
        override val key: String
            get() = tab.id

        override val isAwake: Boolean
            get() = tab.isAwake
    }

    data class Closed(override val pin: PinnedItem) : SiblingTab {
        // Also the id of its stand-in, so the preview of a swipe looks up a thumbnail by it, and the thumbnail cache
        // only takes keys of up to 64 of the characters a-z, 0-9, _ and -.
        override val key: String
            get() = (CLOSED_PIN_PREFIX + pin.id.lowercase().replace(KEY_UNSAFE, "_")).take(MAX_KEY_LENGTH)

        override val title: String
            get() = pin.label(null)

        override val isAwake: Boolean
            get() = false

        /** A tab standing for the pin's page until it opens, like in the preview of a swipe to it. */
        fun standIn(): TabSessionState = createTab(url = pin.pageUrl, id = key, title = title)
    }

    private companion object {
        const val CLOSED_PIN_PREFIX = "dejavu-pin-"
        const val MAX_KEY_LENGTH = 64
        val KEY_UNSAFE = Regex("[^a-z0-9_-]")
    }
}

/**
 * The tabs of the place the selected tab is in, in the order the home screen shows them: the essentials of its
 * workspace, the pinned tabs of its folder or of its workspace's pinned section, or its workspace's other tabs. The tab
 * bar shows them, and swiping the address bar sideways goes through them. Sleeping tabs are among them, and so are
 * pinned tabs whose tab is closed. `null` without a selected tab, and for a private tab, which goes with the other
 * private tabs. [perContainer] tells whether each container has its own essentials.
 */
internal fun siblingTabs(browser: BrowserState, state: WorkspaceState, perContainer: Boolean): List<SiblingTab>? {
    val selected = browser.selectedTab?.takeUnless { it.content.private } ?: return null
    val openTabs = browser.normalTabs.associateBy { it.id }
    val workspaceId = state.workspaceOf(selected.id)
    val workspace = state.workspaces.firstOrNull { it.id == workspaceId }
    val pin = state.pinOf(selected.id)
    val pins = when {
        pin == null -> null
        pin.essential -> state.essentialsFor(workspace?.containerId, perContainer)
        else -> {
            val folderIds = state.pins.filter { it.isFolder && it.workspaceId == pin.workspaceId }.map { it.id }.toSet()
            fun folderOf(item: PinnedItem) = item.parentId?.takeIf { it in folderIds }
            state.pins.filter { !it.isFolder && it.workspaceId == pin.workspaceId && folderOf(it) == folderOf(pin) }
        }
    }
    val siblings = pins?.mapNotNull { item ->
        val tab = item.tabId?.let(openTabs::get)
        when {
            tab != null -> SiblingTab.Open(tab, state.titleOf(tab), item)
            item.pageUrl.isNotEmpty() -> SiblingTab.Closed(item)
            else -> null
        }
    } ?: run {
        val pinnedTabIds = state.pins.mapNotNullTo(HashSet()) { it.tabId }
        browser.normalTabs
            .filter { it.id !in pinnedTabIds && state.workspaceOf(it.id) == workspaceId }
            .map { SiblingTab.Open(it, state.titleOf(it)) }
    }
    return siblings.withSplitPartnersTogether(state)
}

/** [this] with the two tabs of each split view side by side, where the first of them is, like on the home screen. */
private fun List<SiblingTab>.withSplitPartnersTogether(state: WorkspaceState): List<SiblingTab> {
    val byTabId = filterIsInstance<SiblingTab.Open>().associateBy { it.tab.id }
    val placed = HashSet<String>()
    return flatMap { sibling ->
        if (!placed.add(sibling.key)) return@flatMap emptyList()
        val tabId = (sibling as? SiblingTab.Open)?.tab?.id
        val partner = tabId?.let(state::splitOf)?.tabIds?.firstOrNull { it != tabId }?.let(byTabId::get)
        listOfNotNull(sibling, partner?.takeIf { placed.add(it.key) })
    }
}

/**
 * Dejavu's tab bar and sideways swipe of the address bar, which go through the tabs of the place the shown tab is in,
 * see [siblingTabs], instead of every tab.
 */
internal object DejavuTabStrip {
    /** The tabs of the shown tab's place, along with the browser's state, each time either or the workspaces change. */
    fun siblings(context: Context, browser: Flow<BrowserState>): Flow<Pair<BrowserState, List<SiblingTab>?>> =
        combine(
            browser,
            WorkspaceRepository.get(context).state,
            DejavuSettings.get(context).essentialsPerContainer,
        ) { state, workspaces, perContainer -> state to siblingTabs(state, workspaces, perContainer) }

    /** The tabs of the shown tab's place now. */
    fun siblings(context: Context): List<SiblingTab>? = siblingTabs(
        context.components.core.store.state,
        WorkspaceRepository.get(context).state.value,
        DejavuSettings.get(context).essentialsPerContainer.value,
    )

    /**
     * Shows [sibling]: selects its tab, or opens the pinned tab whose tab is closed. Returns the tab's id, or `null`
     * when it could not.
     */
    fun show(context: Context, sibling: SiblingTab): String? = when (sibling) {
        is SiblingTab.Open -> sibling.tab.id.also { context.components.useCases.tabsUseCases.selectTab(it) }
        is SiblingTab.Closed -> openClosedPin(context, sibling.pin)
    }

    /**
     * Shows the tab bar's tab [key] when it is a pinned tab whose tab is closed, by opening it. Returns whether it was
     * one; other tabs are selected as usual.
     */
    fun openClosed(context: Context, key: String): Boolean {
        val sibling = siblings(context)?.firstOrNull { it.key == key } as? SiblingTab.Closed ?: return false
        show(context, sibling)
        return true
    }

    /**
     * Moves the tab bar's pinned tab or essential [key] next to [targetKey], before it or [after] it, where the home
     * screen shows it too. Returns whether it was one; other tabs move among the open tabs as usual.
     */
    fun movePinned(context: Context, key: String, targetKey: String, after: Boolean): Boolean = movePinned(
        siblings = siblings(context).orEmpty(),
        key = key,
        targetKey = targetKey,
        after = after,
        repository = WorkspaceRepository.get(context),
        perContainer = DejavuSettings.get(context).essentialsPerContainer.value,
    )

    /** [movePinned] among [siblings], the tabs of the shown tab's place, in [repository]. */
    @VisibleForTesting
    internal fun movePinned(
        siblings: List<SiblingTab>,
        key: String,
        targetKey: String,
        after: Boolean,
        repository: WorkspaceRepository,
        perContainer: Boolean,
    ): Boolean {
        val moving = siblings.firstOrNull { it.key == key }?.pin ?: return false
        val target = siblings.firstOrNull { it.key == targetKey }?.pin
        val workspaceId = moving.workspaceId
        when {
            target == null -> Unit
            moving.essential -> {
                val others = siblings.mapNotNull { it.pin }.filter { it.id != moving.id }
                val index = others.indexOfFirst { it.id == target.id } + if (after) 1 else 0
                repository.moveEssential(moving.id, index, perContainer)
            }
            workspaceId != null ->
                repository.placePins(workspaceId, setOf(moving.id), emptyList(), PinPlacement.Next(target.id, after))
        }
        return true
    }
}
