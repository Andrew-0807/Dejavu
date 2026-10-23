/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.lib.state.ext.consumeFlow
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.requireComponents

/**
 * The warning shown before about:config is used: its preferences can weaken Dejavu's security and privacy. The ones
 * behind Dejavu's tracking protection stay as changed there, and the protection shows it is managed there (see
 * [Protection]); the others Dejavu's own settings control go back to those settings when Dejavu restarts. After the
 * user went on once, it is not shown again until Dejavu restarts.
 */
object AboutConfigWarning {
    @Volatile
    internal var accepted = false

    /** Whether [url] is about:config, with or without a search or an anchor. */
    internal fun isAboutConfig(url: String?): Boolean {
        val address = url?.lowercase() ?: return false
        return address == PAGE || address.startsWith("$PAGE?") || address.startsWith("$PAGE#")
    }

    private const val PAGE = "about:config"
}

/**
 * Shows [AboutConfigWarning] over the page when the shown tab opens about:config, and takes it away when another tab
 * or page is shown. Cancel leaves the page as Back does: it goes back, or closes the tab when it has no page left.
 * Custom tabs are left alone.
 */
fun Fragment.installAboutConfigWarning(customTabSessionId: String?) {
    if (customTabSessionId != null) return
    val components = requireComponents
    var dialog: AlertDialog? = null
    consumeFlow(components.core.store) { flow ->
        flow.map { state -> state.selectedTab?.let { it.id to it.content.url } }
            .distinctUntilChanged()
            .collect { shown ->
                dialog?.dismiss()
                val (tabId, url) = shown ?: return@collect
                if (AboutConfigWarning.accepted || !AboutConfigWarning.isAboutConfig(url)) return@collect
                dialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.dejavu_about_config_warning_title)
                    .setMessage(R.string.dejavu_about_config_warning_message)
                    .setCancelable(false)
                    .setNegativeButton(R.string.dejavu_cancel) { _, _ ->
                        val tab = components.core.store.state.findTab(tabId)
                        when {
                            tab == null -> Unit
                            tab.content.canGoBack -> components.useCases.sessionUseCases.goBack(tabId)
                            else -> closeShownTab(components, findNavController(), tab)
                        }
                    }
                    .setPositiveButton(R.string.dejavu_open) { _, _ -> AboutConfigWarning.accepted = true }
                    .show()
            }
    }
    viewLifecycleOwner.lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                dialog?.dismiss()
            }
        },
    )
}
