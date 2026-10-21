/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.fenix.dejavu.workspaces.SplitOrientation
import org.mozilla.fenix.ext.components

/**
 * How the browser shows a split view: its tabs in their order, on top or on the left first, the way it shows them and
 * the share of the page area the first one takes.
 */
internal data class ShownSplit(
    val firstTabId: String,
    val secondTabId: String,
    val orientation: SplitOrientation,
    val fraction: Float,
) {
    val tabIds: List<String>
        get() = listOf(firstTabId, secondTabId)
}

/**
 * The split view the browser shows, for the bars to show both of its tabs: the selected tab is the one in focus, which
 * the bars act on, and the other one gets the focus when the user turns to it.
 */
internal object SplitScreen {
    /** The split view the browser shows, or `null` while it shows a single page. */
    var shown by mutableStateOf<ShownSplit?>(null)

    /**
     * Turns to the tab with [tabId] of the shown split, then calls [then]: the tab becomes the selected tab, kept in
     * its place, which the bars act on from then on.
     */
    fun focus(context: Context, tabId: String, then: () -> Unit = {}) {
        val components = context.components
        if (components.core.store.state.selectedTabId == tabId) {
            then()
            return
        }
        val split = SplitPanes.active?.takeIf { it.showsInPane(tabId) }
        if (split != null) {
            split.focus(tabId, then)
        } else {
            components.useCases.tabsUseCases.selectTab(tabId)
            then()
        }
    }
}
