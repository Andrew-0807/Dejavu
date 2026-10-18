/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu

import android.content.Context
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.state.recover.RecoverableTab
import org.mozilla.fenix.dejavu.containers.DejavuContainerStorage
import org.mozilla.fenix.dejavu.containers.TemporaryContainers
import org.mozilla.fenix.dejavu.home.pageUrl
import org.mozilla.fenix.dejavu.settings.DejavuSettings
import org.mozilla.fenix.dejavu.workspaces.PinnedItem
import org.mozilla.fenix.dejavu.workspaces.WorkspaceRepository
import org.mozilla.fenix.ext.components

/**
 * Tells which tabs to restore even when Firefox would close them for being unused for too long: Dejavu only closes
 * unpinned tabs, so pinned tabs and essentials stay. Reads the workspaces on first use, so call it off the main thread.
 */
fun dejavuKeepsTab(context: Context): (RecoverableTab) -> Boolean {
    val pinnedTabIds by lazy { WorkspaceRepository.get(context).state.value.pins.mapNotNullTo(HashSet()) { it.tabId } }
    return { tab -> tab.state.id in pinnedTabIds }
}

/**
 * Opens pinned tab [pin], whose tab is closed, where that tab was, like a sleeping tab wakes up: on the page it closed
 * on, in its container, and shows it. Returns the new tab's id, or `null` for a pin without an address.
 */
fun openClosedPin(context: Context, pin: PinnedItem): String? {
    val url = pin.pageUrl.takeIf { it.isNotEmpty() } ?: return null
    val components = context.components
    val store = components.core.store
    val tabId = components.useCases.tabsUseCases.addTab(
        url = url,
        selectTab = true,
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
