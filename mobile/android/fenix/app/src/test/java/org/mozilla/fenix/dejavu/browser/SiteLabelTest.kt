/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import kotlinx.coroutines.test.runTest
import mozilla.components.lib.publicsuffixlist.PublicSuffixList
import mozilla.components.support.ktx.kotlin.getRegistrableDomainIndexRange
import mozilla.components.support.test.robolectric.testContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SiteLabelTest {
    @Test
    fun `a web page shows its host, never the rest of its address`() {
        assertEquals("accounts.example.com", siteOf("https://www.accounts.example.com/signin?next=/inbox#top"))
        assertEquals("example.com", siteOf("blob:https://example.com/0f1e2d3c"))
        assertEquals("example.com", siteOf("http://example.com"))
    }

    @Test
    fun `a page that is not on a web site shows its address`() {
        assertEquals("about:blank", siteOf("about:blank"))
        assertEquals("file:///sdcard/page.html", siteOf("file:///sdcard/page.html"))
    }

    @Test
    fun `the registrable domain stands out, so a made up subdomain cannot pass for another site`() = runTest {
        val publicSuffixList = PublicSuffixList(testContext)

        val label = siteLabel("https://accounts.google.com.example.co.uk/signin", publicSuffixList)

        assertEquals("accounts.google.com.example.co.uk", label.toString())
        assertEquals(20 to 33, label.getRegistrableDomainIndexRange())
        assertEquals(null, siteLabel("about:blank", publicSuffixList).getRegistrableDomainIndexRange())
    }
}
