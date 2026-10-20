/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import org.mozilla.fenix.GleanMetrics.SentFromFirefox
import org.mozilla.fenix.R
import org.mozilla.fenix.e2e.SystemInsetsPaddedFragment
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.showToolbar

/** Lets the user customize link sharing feature. */
class LinkSharingFragment : PreferenceFragmentCompat(), SystemInsetsPaddedFragment {
    private val section = LinkSharingSection(this)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.link_sharing_preferences, rootKey)
        section.onCreate()
    }

    override fun onResume() {
        super.onResume()
        showToolbar(getString(R.string.preferences_link_sharing))
    }
}

/** The preference of [LinkSharingFragment]. The settings page shows it inline, see docs/dejavu/settings-redesign.md. */
class LinkSharingSection(private val fragment: PreferenceFragmentCompat) : SettingsSection {
    override fun onCreate() {
        fragment.requirePreference<SwitchPreferenceCompat>(R.string.pref_key_link_sharing).apply {
            isChecked = context.components.settings.whatsappLinkSharingEnabled

            onPreferenceChangeListener =
                object : SharedPreferenceUpdater() {
                    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
                        SentFromFirefox.settingsToggled.record(
                            SentFromFirefox.SettingsToggledExtra(enabled = newValue as Boolean)
                        )

                        return super.onPreferenceChange(preference, newValue)
                    }
                }

            title =
                this@LinkSharingSection.fragment.getString(
                    R.string.link_sharing_toggle_body,
                    this@LinkSharingSection.fragment.getString(R.string.firefox),
                )
            summary =
                this@LinkSharingSection.fragment.getString(
                    R.string.link_sharing_toggle_title,
                    this@LinkSharingSection.fragment.getString(R.string.firefox),
                )
        }
    }
}
