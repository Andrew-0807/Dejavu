/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.containers

import kotlinx.coroutines.test.runTest
import mozilla.components.browser.state.state.ContainerState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.support.test.robolectric.testContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContainerReplacementTest {
    @Test
    fun `a tab opens in its container while it exists, and once it was removed in a temporary one or none`() = runTest {
        val storage = DejavuContainerStorage.get(testContext)
        val store = BrowserStore()
        storage.saveContainer("kept-container", "Kept", ContainerColor.BLUE, ContainerState.Icon.BRIEFCASE)

        assertEquals("kept-container", TemporaryContainers.orReplacement("kept-container", store, storage, true))
        assertNull(TemporaryContainers.orReplacement(null, store, storage, true))
        assertNull(TemporaryContainers.orReplacement("removed-container", store, storage, false))
        val replacement = TemporaryContainers.orReplacement("removed-container", store, storage, true)
        assertNotEquals("removed-container", replacement)
        assertTrue(storage.isTemporary(replacement!!))
    }
}
