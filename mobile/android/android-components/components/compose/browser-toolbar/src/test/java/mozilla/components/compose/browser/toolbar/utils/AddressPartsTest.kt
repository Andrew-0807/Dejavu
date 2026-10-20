/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.browser.toolbar.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressPartsTest {
    @Test
    fun `WHEN the page has a real title THEN it is kept with the domain and path split`() {
        assertEquals(
            AddressParts("Hello - Google Search", "google.com", "/search?q=Hello"),
            addressParts("Hello - Google Search", "https://www.google.com/search?q=Hello"),
        )
    }

    @Test
    fun `WHEN the title is the address THEN only the address is shown`() {
        val url = "google.com/search?client=firefox-b-m&q=Hello"
        assertEquals(AddressParts(null, "google.com", "/search?client=firefox-b-m&q=Hello"), addressParts(url, url))
        assertEquals(null, addressParts("google.com/searc…", url)?.title)
        assertEquals(null, addressParts("https://www.google.com/", "google.com")?.title)
        assertEquals("Google", addressParts("Google", url)?.title)
    }

    @Test
    fun `WHEN the address is the root page or has no host THEN the path is empty or the whole address is the domain`() {
        assertEquals(AddressParts(null, "mozilla.org", ""), addressParts(null, "https://mozilla.org/"))
        assertEquals(AddressParts(null, "about:config", ""), addressParts("", "about:config"))
        assertEquals(AddressParts(null, "/sdcard/a.html", ""), addressParts(null, "file:///sdcard/a.html"))
    }

    @Test
    fun `WHEN there is nothing to show THEN it is null`() {
        assertNull(addressParts(null, null))
        assertNull(addressParts("  ", ""))
    }

    @Test
    fun `screen readers hear the title and the full address`() {
        assertEquals("Docs, example.com/a/b", AddressParts("Docs", "example.com", "/a/b").spoken)
    }
}
