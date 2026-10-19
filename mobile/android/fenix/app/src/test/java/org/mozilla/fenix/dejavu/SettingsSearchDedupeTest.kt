/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.fenix.settings.settingssearch.PreferenceFileInformation
import org.mozilla.fenix.settings.settingssearch.SettingsSearchItem
import org.mozilla.fenix.settings.settingssearch.distinctPreferringDejavuScreens

class SettingsSearchDedupeTest {
    private fun item(key: String, title: String, info: PreferenceFileInformation) =
        SettingsSearchItem(
            title = title,
            summary = "",
            preferenceKey = key,
            categoryHeader = "",
            preferenceFileInformation = info,
        )

    @Test
    fun `a Dejavu screen replaces the settings row with its key, in the row's place`() {
        val general = PreferenceFileInformation.GeneralPreferences
        val screen = PreferenceFileInformation.DejavuScreen(titleResourceId = 1, fragmentId = 2)
        val items = listOf(
            item("a", "First", general),
            item("shortcuts", "Keyboard shortcuts", general),
            item("b", "Other", general),
            item("b", "Other again", general),
            item("shortcuts", "Keyboard shortcuts", screen),
        )

        val result = items.distinctPreferringDejavuScreens()

        assertEquals(listOf("a", "shortcuts", "b"), result.map { it.preferenceKey })
        assertTrue(result[1].preferenceFileInformation is PreferenceFileInformation.DejavuScreen)
        assertEquals("Other", result[2].title)
    }
}
