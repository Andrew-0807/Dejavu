/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.webextension.WebExtension
import org.junit.Test

class SearchTelemetryExtensionsTest {
    private fun extension(id: String): WebExtension = mockk { every { this@mockk.id } returns id }

    @Test
    fun `only the search telemetry extensions earlier versions installed are uninstalled`() {
        val ads = extension("ads@mozac.org")
        val cookies = extension("cookies@mozac.org")
        val uBlock = extension("uBlock0@raymondhill.net")
        val engine = mockk<Engine>(relaxed = true)
        val listed = slot<(List<WebExtension>) -> Unit>()
        every { engine.listInstalledWebExtensions(capture(listed), any()) } answers {
            listed.captured(listOf(ads, uBlock, cookies))
        }

        SearchTelemetryExtensions.uninstall(engine)

        verify { engine.uninstallWebExtension(ads, any(), any()) }
        verify { engine.uninstallWebExtension(cookies, any(), any()) }
        verify(exactly = 0) { engine.uninstallWebExtension(uBlock, any(), any()) }
    }
}
