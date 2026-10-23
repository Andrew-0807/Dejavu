/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.state.recover.RecoverableTab
import org.mozilla.fenix.R
import org.mozilla.fenix.dejavu.containers.DejavuContainerStorage
import org.mozilla.fenix.dejavu.containers.TemporaryContainers
import org.mozilla.fenix.dejavu.home.pageUrl
import org.mozilla.fenix.dejavu.settings.DejavuSettings
import org.mozilla.fenix.dejavu.sync.DejavuSync
import org.mozilla.fenix.dejavu.workspaces.PinnedItem
import org.mozilla.fenix.dejavu.workspaces.WorkspaceRepository
import org.mozilla.fenix.ext.components

/**
 * Tells which tabs to restore even when Firefox would close them for being unused for too long: Dejavu only closes
 * unpinned tabs, so pinned tabs and essentials stay. Sync forgets the tabs closed this way, rather than closing them
 * on the other devices, see [DejavuSync.forgetTabs]. Reads the workspaces on first use, so call it off the main thread.
 */
fun dejavuKeepsTab(context: Context): (RecoverableTab) -> Boolean {
    val state by lazy { WorkspaceRepository.get(context).state.value }
    val pinnedTabIds by lazy { state.pins.mapNotNullTo(HashSet()) { it.tabId } }
    return { tab ->
        val id = tab.state.id
        (id in pinnedTabIds).also { kept -> if (!kept) DejavuSync.forgetTabs(context, listOf(state.syncIdOf(id))) }
    }
}

/**
 * Opens pinned tab [pin], whose tab is closed, where that tab was, like a sleeping tab wakes up: on the page it closed
 * on, in its container, and shows it when [select] is set. Returns the new tab's id, or `null` for a pin without an
 * address.
 *
 * A pinned tab or essential whose address is not a web page, which sync may have brought from another device, first
 * shows that address and opens only once the user agrees. Then `null` is returned, and [onOpened] gets the new tab's id.
 */
fun openClosedPin(
    context: Context,
    pin: PinnedItem,
    select: Boolean = true,
    onOpened: (String) -> Unit = {},
): String? {
    val url = pin.pageUrl.takeIf { it.isNotEmpty() } ?: return null
    if (!url.startsWith("https://") && !url.startsWith("http://")) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dejavu_open_non_web_pin_title)
            .setMessage(context.getString(R.string.dejavu_open_non_web_pin_message, url.shortened()))
            .setNegativeButton(R.string.dejavu_cancel, null)
            .setPositiveButton(R.string.dejavu_open) { _, _ -> onOpened(openPin(context, pin, url, select)) }
            .show()
        return null
    }
    return openPin(context, pin, url, select)
}

/** The start of a long address, enough to tell what it opens, like a data: page or an app's intent. */
private fun String.shortened(): String = if (length > MAX_SHOWN_ADDRESS) take(MAX_SHOWN_ADDRESS) + "…" else this

private const val MAX_SHOWN_ADDRESS = 200

private fun openPin(context: Context, pin: PinnedItem, url: String, select: Boolean): String {
    val components = context.components
    val store = components.core.store
    val tabId = components.useCases.tabsUseCases.addTab(
        url = url,
        selectTab = select,
        title = pin.openTitle ?: pin.title,
        contextId = TemporaryContainers.orReplacement(
            pin.containerId,
            store,
            DejavuContainerStorage.get(context),
            DejavuSettings.get(context).temporaryContainersByDefault.value,
        ),
        source = SessionState.Source.Internal.None,
    )
    WorkspaceRepository.get(context).attachPinned(pin.id, tabId)
    return tabId
}
