/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import mozilla.components.concept.engine.EngineSession.SafeBrowsingPolicy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.ContentBlocking
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoogleSafeBrowsingTest {
    @Test
    fun `turned off, pages are checked against no list from Google and only harmful add-on sites are blocked`() {
        val settings = ContentBlocking.Settings.Builder().build()

        GoogleSafeBrowsing.applyLists(settings, enabled = false)

        assertTrue(settings.safeBrowsingMalwareTable.isNotEmpty())
        assertTrue(settings.safeBrowsingMalwareTable.none { it.startsWith("goog") })
        assertTrue(settings.safeBrowsingPhishingTable.none { it.startsWith("goog") })
        assertArrayEquals(arrayOf(SafeBrowsingPolicy.HARMFULADDON), GoogleSafeBrowsing.policy(enabled = false))
    }

    @Test
    fun `turned on again, Google's lists are back, its phishing list by both its names, and every unsafe site is blocked`() {
        val settings = ContentBlocking.Settings.Builder().build()
        val malware = settings.safeBrowsingMalwareTable.toList()
        val phishing = settings.safeBrowsingPhishingTable.toList()

        GoogleSafeBrowsing.applyLists(settings, enabled = false)
        GoogleSafeBrowsing.applyLists(settings, enabled = true)

        assertTrue(malware.any { it.startsWith("goog") })
        assertEquals(malware, settings.safeBrowsingMalwareTable.toList())
        assertEquals(
            (listOf("goog-phish-proto", "googpub-phish-proto") + phishing).distinct(),
            settings.safeBrowsingPhishingTable.toList(),
        )
        assertArrayEquals(arrayOf(SafeBrowsingPolicy.RECOMMENDED), GoogleSafeBrowsing.policy(enabled = true))
    }
}
