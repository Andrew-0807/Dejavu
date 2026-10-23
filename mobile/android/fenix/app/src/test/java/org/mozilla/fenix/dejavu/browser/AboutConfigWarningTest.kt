/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutConfigWarningTest {
    @Test
    fun `about config is recognized with a search or an anchor and in any case`() {
        listOf("about:config", "about:config?filter=webgl", "about:config#top", "About:Config").forEach {
            assertTrue(it, AboutConfigWarning.isAboutConfig(it))
        }
    }

    @Test
    fun `other pages are not about config`() {
        listOf(null, "", "about:configx", "about:addons", "https://example.com/about:config").forEach {
            assertFalse(it.toString(), AboutConfigWarning.isAboutConfig(it))
        }
    }
}
