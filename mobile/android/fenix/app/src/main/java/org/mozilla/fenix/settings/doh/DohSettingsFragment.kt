/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings.doh

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.navigation.NavHostController
import androidx.navigation.fragment.findNavController
import mozilla.components.lib.state.helpers.StoreProvider.Companion.fragmentStore
import org.mozilla.fenix.R
import org.mozilla.fenix.e2e.SystemInsetsPaddedFragment
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.openToBrowser
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.ext.showToolbar
import org.mozilla.fenix.theme.FirefoxTheme

/** Settings for DNS over HTTPS (DoH) */
internal class DohSettingsFragment : Fragment(), SystemInsetsPaddedFragment {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = content {
        val buildStore = { composeNavController: NavHostController ->
            val navController = findNavController()
            val settingsProvider =
                DefaultDohSettingsProvider(
                    engine = requireContext().components.core.engine,
                    settings = requireContext().components.settings,
                )

            val store by
                fragmentStore(DohSettingsState()) {
                    DohSettingsStore(
                        middleware =
                            listOf(
                                DohSettingsMiddleware(
                                    getSettingsProvider = { settingsProvider },
                                    getNavController = { composeNavController },
                                    openUrlInBrowser = { url ->
                                        navController.openToBrowser()
                                        requireComponents.useCases.fenixBrowserUseCases.loadUrlOrSearch(
                                            searchTermOrURL = url,
                                            newTab = true,
                                        )
                                    },
                                    exitDohSettings = { navController.popBackStack() },
                                )
                            )
                    )
                }

            store
        }

        FirefoxTheme {
            DohSettingsNavHost(
                buildStore = buildStore,
                // Dejavu: the tracking protection settings open the list of exceptions on its own.
                startDestination = if (arguments?.getBoolean(START_ON_EXCEPTIONS) == true) {
                    DohSettingsDestinations.EXCEPTIONS_LIST
                } else {
                    DohSettingsDestinations.ROOT
                },
                onUpdateToolbar = { titleResId ->
                    safeShowToolbar(titleResId)
                },
            )
        }
    }

    private fun safeShowToolbar(titleResId: Int) {
        // Only update the toolbar if the Fragment is still visible
        if (isResumed && isVisible) {
            showToolbar(getString(titleResId))
        }
    }

    override fun onResume() {
        super.onResume()
        // Dejavu: the list of exceptions keeps its own title when it was opened on its own.
        val onExceptions = arguments?.getBoolean(START_ON_EXCEPTIONS) == true
        showToolbar(getString(if (onExceptions) R.string.preference_doh_exceptions else R.string.preference_doh_title))
    }

    companion object {
        /** Dejavu: the argument that opens the list of exceptions instead of the secure DNS settings. */
        const val START_ON_EXCEPTIONS = "dejavu_start_on_exceptions"
    }
}
