/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import androidx.compose.runtime.Composable
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import org.mozilla.fenix.R

/**
 * Dejavu: the logic of a group of preferences that shows both on its own screen and inline on the settings page, see
 * docs/dejavu/settings-redesign.md. The host calls these from its own lifecycle. A section finds its preferences by
 * key in the host, so the same section works in either host.
 */
interface SettingsSection {
    /** Called once the preferences are in the host, from `onCreatePreferences`. */
    fun onCreate() = Unit

    /** Called from the host's `onViewCreated`. */
    fun onViewCreated(view: View) = Unit

    /** Called from the host's `onResume`. */
    fun onResume() = Unit

    /** Called from the host's `onPreferenceTreeClick`. Returns `true` when the click was handled. */
    fun onPreferenceTreeClick(preference: Preference): Boolean = false

    /** Called from the host's `onConfigurationChanged`. */
    fun onConfigurationChanged() = Unit
}

/** Space between the orders of the children of a settings category, so inlined sections fit after their anchor. */
private const val ORDER_STEP = 1000

/**
 * Dejavu: spaces the orders of the preferences in every category of this screen by [ORDER_STEP], which
 * [inlinePreferences] needs to put sections right after their anchor. Call once, before [inlinePreferences].
 */
internal fun PreferenceFragmentCompat.spaceSectionOrders() {
    val screen = preferenceScreen
    for (index in 0 until screen.preferenceCount) {
        val category = screen.getPreference(index) as? PreferenceGroup ?: continue
        for (child in 0 until category.preferenceCount) {
            category.getPreference(child).order = (child + 1) * ORDER_STEP
        }
    }
}

/**
 * Dejavu: adds the preferences of [xml] to this screen and moves them next to the anchor category with the key
 * [anchor]. A titled category of [xml] becomes a section right after the anchor [route] picks for it, any other
 * preference goes into [anchor] itself, whose title is then the section header. [store], when given, keeps the values
 * of the moved preferences. Call from `onCreatePreferences`, before the preferences are bound.
 */
internal fun PreferenceFragmentCompat.inlinePreferences(
    @XmlRes xml: Int,
    anchor: String,
    store: PreferenceDataStore? = null,
    route: (PreferenceCategory) -> String = { anchor },
) {
    val screen = preferenceScreen
    val before = screen.preferenceCount
    addPreferencesFromResource(xml)
    val added = (before until screen.preferenceCount).map { screen.getPreference(it) }
    added.forEach { preference ->
        screen.removePreference(preference)
        store?.let { preference.useStore(it) }
        val section = (preference as? PreferenceCategory)?.takeUnless { it.title.isNullOrEmpty() }
        if (section != null) {
            val target = requireNotNull(findPreference<Preference>(route(section))) { "No anchor ${route(section)}" }
            val parent = requireNotNull(target.parent)
            val taken = (0 until parent.preferenceCount).count {
                parent.getPreference(it).order in (target.order + 1) until (target.order + ORDER_STEP)
            }
            section.order = target.order + taken + 1
            parent.addPreference(section)
        } else {
            preference.order = Preference.DEFAULT_ORDER
            requireNotNull(findPreference<PreferenceGroup>(anchor)) { "No anchor $anchor" }.addPreference(preference)
        }
    }
}

private fun Preference.useStore(store: PreferenceDataStore) {
    preferenceDataStore = store
    if (this is PreferenceGroup) {
        for (index in 0 until preferenceCount) getPreference(index).useStore(store)
    }
}

/**
 * Dejavu: the anchor category on the settings page that a titled category of the customization screen goes after,
 * from the [keys] of that category and of its preferences: tab bar settings go to tabs, gestures to input, the rest to
 * the look of the app.
 */
@StringRes
internal fun customizationAnchor(keys: Collection<String>, tabKeys: Collection<String>, inputKey: String): Int =
    when {
        keys.any { it in tabKeys } -> R.string.pref_key_dejavu_customize_tabs
        inputKey in keys -> R.string.pref_key_dejavu_customize_input
        else -> R.string.pref_key_customize
    }

/**
 * Dejavu: the category a scrolled settings list is at, for the highlight of the category list. It is the category of
 * the [first] row shown, or of the [last] one once the list is at its end ([atEnd]), so a short last category can be
 * highlighted too. [groupAt] gives the category of a row, or `null`.
 */
internal fun <T> spiedGroup(first: Int, last: Int, atEnd: Boolean, groupAt: (Int) -> T?): T? =
    if (atEnd) groupAt(last) ?: groupAt(first) else groupAt(first)

/** Dejavu: keeps the values of preferences in [preferences] instead of the default preferences file. */
internal class SharedPreferencesDataStore(private val preferences: SharedPreferences) : PreferenceDataStore() {
    override fun putBoolean(key: String, value: Boolean) = preferences.edit { putBoolean(key, value) }

    override fun getBoolean(key: String, defValue: Boolean) = preferences.getBoolean(key, defValue)

    override fun putFloat(key: String, value: Float) = preferences.edit { putFloat(key, value) }

    override fun getFloat(key: String, defValue: Float) = preferences.getFloat(key, defValue)

    override fun putString(key: String, value: String?) = preferences.edit { putString(key, value) }

    override fun getString(key: String, defValue: String?): String? = preferences.getString(key, defValue)
}

/**
 * Dejavu: a Compose section of the settings page. It draws its own rounded cards, so [DejavuPreferenceCards] leaves it
 * alone.
 *
 * [DejavuPreferenceCards]: org.mozilla.fenix.dejavu.settings.DejavuPreferenceCards
 */
class InlineComposePreference(context: Context, private val content: @Composable () -> Unit) :
    ComposePreference(context) {
    init {
        isSelectable = false
    }

    @Composable
    override fun Content() {
        content()
    }
}
