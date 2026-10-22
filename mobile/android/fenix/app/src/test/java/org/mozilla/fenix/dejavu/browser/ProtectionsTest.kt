/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import androidx.core.content.edit
import mozilla.components.concept.engine.EngineSession.BounceTrackingProtectionMode
import mozilla.components.concept.engine.EngineSession.TrackingProtectionPolicy.CookiePolicy
import mozilla.components.concept.engine.EngineSession.TrackingProtectionPolicy.TrackingCategory
import mozilla.components.support.test.robolectric.testContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.R
import org.mozilla.fenix.components.TrackingProtectionPolicyFactory
import org.mozilla.fenix.ext.getPreferenceKey
import org.mozilla.fenix.utils.Settings
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProtectionsTest {
    private lateinit var settings: Settings
    private lateinit var protections: ProtectionSettings

    @Before
    fun setup() {
        settings = Settings(testContext)
        protections = ProtectionSettings(testContext, settings)
    }

    private fun useLevel(strict: Boolean = false, custom: Boolean = false) = settings.preferences.edit {
        putBoolean(testContext.getPreferenceKey(R.string.pref_key_tracking_protection_standard_option), !strict && !custom)
        putBoolean(testContext.getPreferenceKey(R.string.pref_key_tracking_protection_strict_default), strict)
        putBoolean(testContext.getPreferenceKey(R.string.pref_key_tracking_protection_custom_option), custom)
    }

    private fun policy() =
        TrackingProtectionPolicyFactory(settings, testContext.resources).createTrackingProtectionPolicy()

    @Test
    fun `Strict becomes Dejavu's defaults in Firefox's custom level`() {
        useLevel(strict = true)

        protections.takeOverTrackingProtection()

        assertTrue(settings.useCustomTrackingProtection)
        assertFalse(settings.useStrictTrackingProtection)
        assertFalse(settings.useStandardTrackingProtection)
        assertEquals(ProtectedTabs.ALL, protections.trackingContent)
        assertFalse(protections.blockCrossSiteCookies)
        assertTrue(protections.blockCryptominers)
        assertTrue(protections.blockKnownFingerprinters)
        assertEquals(ProtectedTabs.PRIVATE, protections.suspectedFingerprinters)
        assertTrue(protections.clearRedirectTrackers)
        assertEquals(SiteFixes.ALL, protections.siteFixes)
    }

    @Test
    fun `Dejavu's defaults block as Strict did, except suspected fingerprinters in normal tabs`() {
        useLevel(strict = true)
        protections.takeOverTrackingProtection()

        val policy = policy()

        assertEquals(CookiePolicy.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS, policy.cookiePolicy)
        listOf(
            TrackingCategory.SCRIPTS_AND_SUB_RESOURCES,
            TrackingCategory.EMAIL,
            TrackingCategory.CRYPTOMINING,
            TrackingCategory.FINGERPRINTING,
        ).forEach { assertTrue("$it", policy.contains(it)) }
        assertTrue(policy.cookiePurging)
        assertEquals(BounceTrackingProtectionMode.ENABLED, policy.bounceTrackingProtectionMode)
        assertEquals(true, policy.strictSocialTrackingProtection)
        assertEquals(false, policy.fingerprintingProtection)
        assertEquals(true, policy.fingerprintingProtectionPrivateBrowsing)
        assertTrue(policy.allowListBaselineTrackingProtection)
        assertTrue(policy.allowListConvenienceTrackingProtection)
        assertTrue(policy.useForRegularSessions)
        assertTrue(policy.useForPrivateSessions)
    }

    @Test
    fun `Standard keeps blocking tracking content in private tabs only`() {
        useLevel()

        protections.takeOverTrackingProtection()

        assertEquals(ProtectedTabs.PRIVATE, protections.trackingContent)
        val policy = policy()
        assertFalse(policy.useForRegularSessions)
        assertFalse(policy.contains(TrackingCategory.EMAIL))
    }

    @Test
    fun `Custom keeps its choices, with its cookies moved to the closest of the two Dejavu shows`() {
        useLevel(custom = true)
        settings.preferences.edit {
            putBoolean(testContext.getPreferenceKey(R.string.pref_key_tracking_protection_custom_cryptominers), false)
            putString(
                testContext.getPreferenceKey(R.string.pref_key_tracking_protection_custom_cookies_select),
                testContext.getString(R.string.all),
            )
        }

        protections.takeOverTrackingProtection()

        assertFalse(protections.blockCryptominers)
        assertTrue(protections.blockCrossSiteCookies)
        assertEquals(CookiePolicy.ACCEPT_ONLY_FIRST_PARTY, policy().cookiePolicy)
    }

    @Test
    fun `custom cookies that let all or trackers' cookies through become kept apart`() {
        useLevel(custom = true)
        settings.preferences.edit {
            putBoolean(testContext.getPreferenceKey(R.string.pref_key_tracking_protection_custom_cookies), false)
        }

        protections.takeOverTrackingProtection()

        assertFalse(protections.blockCrossSiteCookies)
        assertEquals(CookiePolicy.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS, policy().cookiePolicy)
    }

    @Test
    fun `tracking protection turned off turns the protections against trackers off`() {
        useLevel(strict = true)
        settings.shouldUseTrackingProtection = false

        protections.takeOverTrackingProtection()

        assertTrue(settings.shouldUseTrackingProtection)
        assertEquals(ProtectedTabs.OFF, protections.trackingContent)
        assertFalse(protections.blockCryptominers)
        assertFalse(protections.blockKnownFingerprinters)
        assertEquals(ProtectedTabs.OFF, protections.suspectedFingerprinters)
        assertFalse(protections.clearRedirectTrackers)
        val policy = policy()
        assertFalse(policy.contains(TrackingCategory.SCRIPTS_AND_SUB_RESOURCES))
        assertEquals(BounceTrackingProtectionMode.ENABLED_DRY_RUN, policy.bounceTrackingProtectionMode)
    }

    @Test
    fun `each choice reads back as it was set`() {
        useLevel(custom = true)

        ProtectedTabs.entries.forEach {
            protections.httpsOnly = it
            protections.trackingContent = it
            protections.suspectedFingerprinters = it
            assertEquals(it, protections.httpsOnly)
            assertEquals(it, protections.trackingContent)
            assertEquals(it, protections.suspectedFingerprinters)
        }
        SiteFixes.entries.forEach {
            protections.siteFixes = it
            assertEquals(it, protections.siteFixes)
        }
        protections.blockCrossSiteCookies = true
        assertTrue(protections.blockCrossSiteCookies)
        protections.blockCrossSiteCookies = false
        assertFalse(protections.blockCrossSiteCookies)
    }

    @Test
    fun `the switches that block say the opposite of the settings that allow`() {
        protections.blockJit = true
        protections.blockWebGl = true
        protections.blockDirectCalls = false
        protections.gpsOnlyLocation = false

        assertFalse(settings.jitEnabled)
        assertFalse(settings.webGlEnabled)
        assertTrue(settings.directCallConnections)
        assertTrue(settings.networkLocationEnabled)
    }

    @Test
    fun `out of the box, direct calls are blocked, the location comes from GPS only and the JIT and WebGL run`() {
        assertTrue(protections.blockDirectCalls)
        assertTrue(protections.gpsOnlyLocation)
        assertFalse(protections.blockJit)
        assertFalse(protections.blockWebGl)
        assertTrue(protections.removeLinkTracking)
        assertTrue(protections.globalPrivacyControl)
        assertEquals(ProtectedTabs.ALL, protections.httpsOnly)
    }

    @Test
    fun `a protection is managed by about config only when one of its own prefs changed there`() {
        val changed = setOf("webgl.disabled", "network.cookie.cookieBehavior")

        assertTrue(Protection.WEBGL.isManagedBy(changed))
        assertTrue(Protection.CROSS_SITE_COOKIES.isManagedBy(changed))
        assertFalse(Protection.JIT.isManagedBy(changed))
        assertFalse(Protection.GLOBAL_PRIVACY_CONTROL.isManagedBy(changed))
        assertFalse(Protection.HTTPS_ONLY.isManagedBy(emptySet()))
    }

    @Test
    fun `the engine keeps about config's values of every pref behind a protection`() {
        val prefs = Protection.allPrefs

        assertEquals(prefs.distinct(), prefs)
        assertTrue(Protection.entries.all { protection -> protection.prefs.all { it in prefs } })
        assertTrue("javascript.options.ion" in prefs)
        assertTrue("media.peerconnection.ice.relay_only" in prefs)
    }
}
