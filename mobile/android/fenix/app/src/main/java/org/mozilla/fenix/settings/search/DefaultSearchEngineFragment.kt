/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings.search

import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import mozilla.components.browser.state.state.searchEngines
import mozilla.components.support.ktx.android.view.hideKeyboard
import org.mozilla.fenix.R
import org.mozilla.fenix.dejavu.withDefaultEngineKeywords
import org.mozilla.fenix.e2e.SystemInsetsPaddedFragment
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.getPreferenceKey
import org.mozilla.fenix.ext.showToolbar

/** A [Fragment] that allows user to set the default search engine. */
class DefaultSearchEngineFragment : PreferenceFragmentCompat(), SystemInsetsPaddedFragment {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.default_search_engine_preferences, rootKey)
    }

    override fun onResume() {
        super.onResume()
        view?.hideKeyboard()
        showToolbar(getString(R.string.preferences_default_search_engine))
        showSettingsKeyword()
    }

    private fun showSettingsKeyword() {
        findPreference<Preference>(getPreferenceKey(R.string.pref_key_dejavu_settings_keyword))?.summary =
            requireContext().components.settings.settingsSearchKeyword
    }

    private fun editSettingsKeyword() {
        val context = requireContext()
        val settings = context.components.settings
        val input = EditText(context).apply { setText(settings.settingsSearchKeyword) }
        AlertDialog.Builder(context)
            .setTitle(R.string.dejavu_settings_search_keyword)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val keyword = input.text.toString().trim().lowercase()
                val engineIds = context.components.core.store.state.search.searchEngines.map { it.id }
                val engineKeywords = settings.engineKeywords.withDefaultEngineKeywords(engineIds)
                if (keyword in engineKeywords || keyword in settings.bookmarkKeywords) {
                    showKeywordTaken(context, keyword)
                } else {
                    settings.settingsSearchKeyword = keyword
                    showSettingsKeyword()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            getPreferenceKey(R.string.pref_key_add_search_engine) -> {
                val directions =
                    DefaultSearchEngineFragmentDirections.actionDefaultEngineFragmentToSaveSearchEngineFragment(null)
                findNavController().navigate(directions)
            }
            getPreferenceKey(R.string.pref_key_dejavu_settings_keyword) -> editSettingsKeyword()
        }

        return super.onPreferenceTreeClick(preference)
    }
}
