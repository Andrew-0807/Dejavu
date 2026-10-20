/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mozilla.fenix.R

class SettingsSectionTest {
    private val tabKeys = listOf("tab_strip_show", "tab_strip_location")
    private val inputKey = "dynamic_toolbar"

    @Test
    fun `customization categories go to the anchor of their settings`() {
        assertEquals(
            R.string.pref_key_dejavu_customize_tabs,
            customizationAnchor(listOf("category", "tab_strip_show"), tabKeys, inputKey),
        )
        assertEquals(
            R.string.pref_key_dejavu_customize_tabs,
            customizationAnchor(listOf("tab_strip_location", "top"), tabKeys, inputKey),
        )
        assertEquals(
            R.string.pref_key_dejavu_customize_input,
            customizationAnchor(listOf("pull_to_refresh", "dynamic_toolbar"), tabKeys, inputKey),
        )
        assertEquals(R.string.pref_key_customize, customizationAnchor(listOf("light", "dark"), tabKeys, inputKey))
        assertEquals(R.string.pref_key_customize, customizationAnchor(emptyList(), tabKeys, inputKey))
    }

    @Test
    fun `the spied category is the one of the first row until the list ends`() {
        val groups = listOf("look", "look", "tabs", "tabs", "about")
        val groupAt = { position: Int -> groups.getOrNull(position) }

        assertEquals("look", spiedGroup(first = 0, last = 2, atEnd = false, groupAt = groupAt))
        assertEquals("tabs", spiedGroup(first = 2, last = 3, atEnd = false, groupAt = groupAt))
        // A short last category is highlighted once the list cannot scroll further.
        assertEquals("about", spiedGroup(first = 2, last = 4, atEnd = true, groupAt = groupAt))
        // A row without a category at the end falls back to the first row.
        assertEquals("tabs", spiedGroup(first = 3, last = 9, atEnd = true, groupAt = groupAt))
        assertNull(spiedGroup(first = -1, last = -1, atEnd = false, groupAt = groupAt))
    }
}
