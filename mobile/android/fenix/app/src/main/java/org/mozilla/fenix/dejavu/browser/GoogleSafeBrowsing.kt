/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession.SafeBrowsingPolicy
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime

/**
 * Google Safe Browsing, which warns about phishing and malware sites, on or off. Turning its blocking off is not
 * enough on Android, where GeckoView keeps downloading the lists of a blocking that is off, so off also leaves Google's
 * lists out of the ones pages are checked against: Dejavu then neither downloads them nor asks Google about pages.
 * Mozilla's list of harmful add-on sites stays on.
 */
object GoogleSafeBrowsing {
    private const val GOOGLE_LISTS = "goog"

    /**
     * The two names of Google's phishing list. GeckoView picks one by how its Java code was built, the engine knows
     * one by how its library was built, and Dejavu's library comes prebuilt from Mozilla. With both, the engine
     * downloads the one it knows and leaves out the other.
     */
    private val phishingLists = arrayOf("goog-phish-proto", "googpub-phish-proto")

    // GeckoView's own lists, which every runtime starts with.
    private val defaults by lazy { ContentBlocking.Settings.Builder().build() }

    /** What the engine blocks. */
    fun policy(enabled: Boolean): Array<SafeBrowsingPolicy> =
        arrayOf(if (enabled) SafeBrowsingPolicy.RECOMMENDED else SafeBrowsingPolicy.HARMFULADDON)

    /** Sets the lists [settings] checks pages against, before its runtime starts or while it runs. */
    @Suppress("SpreadOperator")
    fun applyLists(settings: ContentBlocking.Settings, enabled: Boolean) {
        settings.setSafeBrowsingMalwareTable(*lists(defaults.safeBrowsingMalwareTable, enabled))
        val phishing = (phishingLists + defaults.safeBrowsingPhishingTable).distinct().toTypedArray()
        settings.setSafeBrowsingPhishingTable(*lists(phishing, enabled))
    }

    /** Turns Safe Browsing on or off in the running [engine] and its [runtime]. */
    fun apply(engine: Engine, runtime: GeckoRuntime, enabled: Boolean) {
        engine.settings.safeBrowsingPolicy = policy(enabled)
        applyLists(runtime.settings.contentBlocking, enabled)
    }

    private fun lists(all: Array<String>, enabled: Boolean): Array<String> =
        if (enabled) all else all.filterNot { it.startsWith(GOOGLE_LISTS) }.toTypedArray()
}
