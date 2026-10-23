/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import mozilla.components.concept.engine.preferences.Branch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallsAndLocationTest {
    private val relayOnlyPrefs = setOf(
        "media.peerconnection.ice.relay_only",
        "media.peerconnection.ice.no_host",
        "media.peerconnection.ice.default_address_only",
    )

    @Test
    fun `the switch sets the engine defaults, so about config changes stay`() {
        assertTrue(CallsAndLocation.prefs(direct = true).all { it.branch == Branch.DEFAULT })
    }

    @Test
    fun `without direct connections, calls use only relay servers and offer no own address`() {
        val prefs = CallsAndLocation.prefs(direct = false).associate { it.pref to it.value }

        assertEquals(relayOnlyPrefs.associateWith { true }, prefs)
    }

    @Test
    fun `direct connections turn the relay only prefs off`() {
        val prefs = CallsAndLocation.prefs(direct = true).associate { it.pref to it.value }

        assertEquals(relayOnlyPrefs.associateWith { false }, prefs)
    }
}
