/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.settings

import android.content.Context
import org.mozilla.fenix.R
import org.mozilla.fenix.settings.settingssearch.PreferenceFileInformation
import org.mozilla.fenix.settings.settingssearch.SettingsSearchItem
import org.mozilla.fenix.settings.settingssearch.SettingsSearchProvider

/**
 * Makes Dejavu's own settings screens findable, each opening that screen. They share the key of the row that opens
 * them, so the row and the screen are one result. Sleeping tabs and links from other apps are shown inline on the
 * settings page, which these results open. The rows inside Customize are indexed from its XML.
 */
object DejavuSettingsSearchProvider : SettingsSearchProvider {
    private val screens = listOf(
        Triple(R.string.dejavu_settings_shortcuts, R.string.pref_key_dejavu_shortcuts, R.id.dejavu_shortcuts_graph),
        Triple(
            R.string.dejavu_settings_tab_actions,
            R.string.pref_key_dejavu_tab_actions,
            R.id.dejavu_tab_actions_graph,
        ),
        // Shown inline on the settings page, under a category with the key of the old row.
        Triple(R.string.dejavu_settings_tab_sleep, R.string.pref_key_dejavu_tab_sleep, R.id.settingsFragment),
        Triple(R.string.dejavu_settings_containers, R.string.pref_key_dejavu_containers, R.id.dejavu_containers_graph),
        Triple(
            R.string.dejavu_settings_external_links,
            R.string.pref_key_dejavu_external_links,
            R.id.settingsFragment,
        ),
        Triple(R.string.dejavu_settings_more_menu, R.string.pref_key_dejavu_more_menu, R.id.dejavu_more_menu_graph),
    )

    /**
     * Sections shown inline on the settings page whose name was only findable as the row that opened their screen, by
     * title and key of their category.
     */
    private val inlineSections = listOf(
        R.string.preferences_home_2 to R.string.pref_key_home,
        R.string.preferences_accessibility to R.string.pref_key_accessibility,
        R.string.preferences_https_only_title to R.string.pref_key_https_only_settings,
        R.string.preferences_private_browsing_options to R.string.pref_key_private_browsing,
        R.string.preferences_downloads_2 to R.string.pref_key_downloads,
    )

    override fun getSearchItems(context: Context): List<SettingsSearchItem> = screens.map { (title, key, graph) ->
        SettingsSearchItem(
            title = context.getString(title),
            summary = "",
            preferenceKey = context.getString(key),
            categoryHeader = context.getString(R.string.dejavu_settings_title),
            preferenceFileInformation = PreferenceFileInformation.DejavuScreen(title, graph),
        )
    } + inlineSections.map { (title, key) ->
        SettingsSearchItem(
            title = context.getString(title),
            summary = "",
            preferenceKey = context.getString(key),
            categoryHeader = context.getString(R.string.settings_title),
            preferenceFileInformation = PreferenceFileInformation.GeneralPreferences,
        )
    }
}
