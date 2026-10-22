/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import mozilla.components.ExperimentalAndroidComponentsApi
import mozilla.components.concept.engine.Engine
import mozilla.components.support.base.log.logger.Logger
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.getPreferenceKey
import org.mozilla.fenix.utils.Settings

/** In which tabs a protection works. */
enum class ProtectedTabs { ALL, PRIVATE, OFF }

/** Which trackers that sites need to work get through: Firefox's fixes for major and for minor site issues. */
enum class SiteFixes { ALL, MAJOR, OFF }

/**
 * A protection of Dejavu's tracking protection settings and the browser prefs behind it. When about:config gives one
 * of these prefs a value of its own, the protection is managed there until the pref is reset, and the engine keeps
 * that value when Dejavu starts (see [org.mozilla.gecko.GeckoAppShell.setKeptUserPrefs]).
 */
enum class Protection(val prefs: List<String>) {
    HTTPS_ONLY(listOf("dom.security.https_only_mode", "dom.security.https_only_mode_pbm")),
    SECURE_DNS(listOf("network.trr.mode", "network.trr.uri", "network.trr.default_provider_uri")),
    STRICT_DNS(listOf("network.trr.mode")),
    DNS_EXCEPTIONS(listOf("network.trr.excluded-domains")),
    TRACKING_CONTENT(
        listOf(
            "privacy.trackingprotection.enabled",
            "privacy.trackingprotection.pbmode.enabled",
            "privacy.trackingprotection.socialtracking.enabled",
            "privacy.trackingprotection.emailtracking.enabled",
            "privacy.annotate_channels.strict_list.enabled",
            "urlclassifier.trackingTable",
        ),
    ),
    CROSS_SITE_COOKIES(listOf("network.cookie.cookieBehavior", "network.cookie.cookieBehavior.pbmode")),
    CRYPTOMINERS(
        listOf(
            "privacy.trackingprotection.cryptomining.enabled",
            "urlclassifier.features.cryptomining.blacklistTables",
        ),
    ),
    KNOWN_FINGERPRINTERS(
        listOf(
            "privacy.trackingprotection.fingerprinting.enabled",
            "urlclassifier.features.fingerprinting.blacklistTables",
        ),
    ),
    SUSPECTED_FINGERPRINTERS(listOf("privacy.fingerprintingProtection", "privacy.fingerprintingProtection.pbmode")),
    REDIRECT_TRACKERS(listOf("privacy.purge_trackers.enabled", "privacy.bounceTrackingProtection.mode")),
    LINK_TRACKING(listOf("privacy.query_stripping.enabled", "privacy.query_stripping.enabled.pbmode")),
    GLOBAL_PRIVACY_CONTROL(
        listOf("privacy.globalprivacycontrol.enabled", "privacy.globalprivacycontrol.pbmode.enabled"),
    ),
    SITE_FIXES(
        listOf(
            "privacy.trackingprotection.allow_list.baseline.enabled",
            "privacy.trackingprotection.allow_list.convenience.enabled",
        ),
    ),
    SAFE_BROWSING(
        listOf(
            "browser.safebrowsing.malware.enabled",
            "browser.safebrowsing.phishing.enabled",
            "urlclassifier.malwareTable",
            "urlclassifier.phishTable",
        ),
    ),
    JIT(JitAndWebGl.JIT_PREFS),
    WEBGL(listOf(JitAndWebGl.WEBGL_DISABLED)),
    DIRECT_CALLS(CallsAndLocation.RELAY_ONLY_PREFS),
    ;

    /** Whether about:config manages this protection, given the [changed] prefs that have a value of their own there. */
    fun isManagedBy(changed: Set<String>): Boolean = prefs.any { it in changed }

    companion object {
        private val logger = Logger("Protection")

        /** Every pref behind a protection. */
        val allPrefs: List<String> by lazy { entries.flatMap { it.prefs }.distinct() }

        /** Hands [onResult] the prefs behind the protections that about:config gave a value of their own. */
        @OptIn(ExperimentalAndroidComponentsApi::class)
        fun readChangedPrefs(engine: Engine, onResult: (Set<String>) -> Unit) {
            engine.getBrowserPrefs(
                prefs = allPrefs,
                onSuccess = { prefs -> onResult(prefs.filter { it.hasUserChangedValue }.map { it.pref }.toSet()) },
                onError = { logger.error("Could not read the prefs behind the protections", it) },
            )
        }
    }
}

/**
 * The protections as Dejavu keeps them: in Firefox's own settings, the trackers in those of its custom tracking
 * protection, which Dejavu shows one protection at a time instead of as standard, strict and custom levels. On means
 * protected: the switches of the JIT, WebGL, direct calls and network location say the opposite of their settings.
 */
class ProtectionSettings(private val context: Context, private val settings: Settings) {
    var httpsOnly: ProtectedTabs
        get() = when {
            !settings.shouldUseHttpsOnly -> ProtectedTabs.OFF
            settings.shouldUseHttpsOnlyInPrivateTabsOnly -> ProtectedTabs.PRIVATE
            else -> ProtectedTabs.ALL
        }
        set(value) {
            settings.shouldUseHttpsOnly = value != ProtectedTabs.OFF
            settings.shouldUseHttpsOnlyInAllTabs = value == ProtectedTabs.ALL
            settings.shouldUseHttpsOnlyInPrivateTabsOnly = value == ProtectedTabs.PRIVATE
        }

    var trackingContent: ProtectedTabs
        get() = when {
            !settings.blockTrackingContentInCustomTrackingProtection -> ProtectedTabs.OFF
            settings.blockTrackingContentSelectionInCustomTrackingProtection == privateTabs -> ProtectedTabs.PRIVATE
            else -> ProtectedTabs.ALL
        }
        set(value) = settings.preferences.edit {
            putBoolean(key(R.string.pref_key_tracking_protection_custom_tracking_content), value != ProtectedTabs.OFF)
            putString(
                key(R.string.pref_key_tracking_protection_custom_tracking_content_select),
                if (value == ProtectedTabs.PRIVATE) privateTabs else allTabs,
            )
        }

    /** On, no cookies from other sites; off, each site still keeps its cookies apart (Total Cookie Protection). */
    var blockCrossSiteCookies: Boolean
        get() = settings.blockCookiesInCustomTrackingProtection &&
            settings.blockCookiesSelectionInCustomTrackingProtection in blockingCookieChoices
        set(value) = settings.preferences.edit {
            putBoolean(key(R.string.pref_key_tracking_protection_custom_cookies), true)
            putString(
                key(R.string.pref_key_tracking_protection_custom_cookies_select),
                context.getString(if (value) R.string.third_party else R.string.total_protection),
            )
        }

    var blockCryptominers: Boolean
        get() = settings.blockCryptominersInCustomTrackingProtection
        set(value) = putBoolean(R.string.pref_key_tracking_protection_custom_cryptominers, value)

    var blockKnownFingerprinters: Boolean
        get() = settings.blockFingerprintersInCustomTrackingProtection
        set(value) = putBoolean(R.string.pref_key_tracking_protection_custom_fingerprinters, value)

    var suspectedFingerprinters: ProtectedTabs
        get() = when {
            !settings.blockSuspectedFingerprintersInCustomTrackingProtection -> ProtectedTabs.OFF
            settings.blockSuspectedFingerprintersSelectionInCustomTrackingProtection == allTabs -> ProtectedTabs.ALL
            else -> ProtectedTabs.PRIVATE
        }
        set(value) = settings.preferences.edit {
            putBoolean(key(R.string.pref_key_tracking_protection_suspected_fingerprinters), value != ProtectedTabs.OFF)
            putString(
                key(R.string.pref_key_tracking_protection_suspected_fingerprinters_select),
                if (value == ProtectedTabs.ALL) allTabs else privateTabs,
            )
        }

    var clearRedirectTrackers: Boolean
        get() = settings.blockRedirectTrackersInCustomTrackingProtection
        set(value) = putBoolean(R.string.pref_key_tracking_protection_redirect_trackers, value)

    var siteFixes: SiteFixes
        get() = when {
            !settings.customAllowListBaselineTrackingProtection -> SiteFixes.OFF
            settings.customAllowListConvenienceTrackingProtection -> SiteFixes.ALL
            else -> SiteFixes.MAJOR
        }
        set(value) {
            settings.customAllowListBaselineTrackingProtection = value != SiteFixes.OFF
            settings.customAllowListConvenienceTrackingProtection = value == SiteFixes.ALL
        }

    var globalPrivacyControl: Boolean
        get() = settings.shouldEnableGlobalPrivacyControl
        set(value) {
            settings.shouldEnableGlobalPrivacyControl = value
        }

    var removeLinkTracking: Boolean
        get() = settings.stripLinkTracking
        set(value) {
            settings.stripLinkTracking = value
        }

    var safeBrowsing: Boolean
        get() = settings.safeBrowsingEnabled
        set(value) {
            settings.safeBrowsingEnabled = value
        }

    var blockJit: Boolean
        get() = !settings.jitEnabled
        set(value) {
            settings.jitEnabled = !value
        }

    var blockWebGl: Boolean
        get() = !settings.webGlEnabled
        set(value) {
            settings.webGlEnabled = !value
        }

    var blockDirectCalls: Boolean
        get() = !settings.directCallConnections
        set(value) {
            settings.directCallConnections = !value
        }

    var gpsOnlyLocation: Boolean
        get() = !settings.networkLocationEnabled
        set(value) {
            settings.networkLocationEnabled = !value
        }

    /**
     * Moves Firefox's tracking protection level to the protections Dejavu shows, which Firefox's custom level carries.
     * Strict, Dejavu's default before, becomes Dejavu's defaults: it blocked suspected fingerprinters in every tab,
     * which can break sites, so now only in private tabs, and every site fix is on. Standard keeps blocking tracking
     * content in private tabs only. Custom keeps its choices, with its cookies moved to one of the two Dejavu shows.
     * Tracking protection turned off turns the protections against trackers off.
     */
    fun takeOverTrackingProtection() {
        val strict = settings.useStrictTrackingProtection
        val custom = settings.useCustomTrackingProtection && !strict
        when {
            !settings.shouldUseTrackingProtection -> {
                trackingContent = ProtectedTabs.OFF
                blockCrossSiteCookies = false
                blockCryptominers = false
                blockKnownFingerprinters = false
                suspectedFingerprinters = ProtectedTabs.OFF
                clearRedirectTrackers = false
                siteFixes = SiteFixes.ALL
                settings.shouldUseTrackingProtection = true
            }
            custom -> {
                val blocked = blockCrossSiteCookies
                blockCrossSiteCookies = blocked
            }
            else -> {
                trackingContent = if (strict) ProtectedTabs.ALL else ProtectedTabs.PRIVATE
                blockCrossSiteCookies = false
                blockCryptominers = true
                blockKnownFingerprinters = true
                suspectedFingerprinters = ProtectedTabs.PRIVATE
                clearRedirectTrackers = true
                siteFixes = if (strict && !settings.strictAllowListBaselineTrackingProtection) {
                    SiteFixes.OFF
                } else {
                    SiteFixes.ALL
                }
            }
        }
        settings.preferences.edit {
            putBoolean(key(R.string.pref_key_tracking_protection_standard_option), false)
            putBoolean(key(R.string.pref_key_tracking_protection_strict_default), false)
            putBoolean(key(R.string.pref_key_tracking_protection_custom_option), true)
        }
    }

    private val allTabs get() = context.getString(R.string.all)

    private val privateTabs get() = context.getString(R.string.private_string)

    private val blockingCookieChoices
        get() = listOf(R.string.third_party, R.string.unvisited, R.string.all).map(context::getString)

    private fun key(@StringRes id: Int) = context.getPreferenceKey(id)

    private fun putBoolean(@StringRes id: Int, value: Boolean) =
        settings.preferences.edit { putBoolean(key(id), value) }
}
