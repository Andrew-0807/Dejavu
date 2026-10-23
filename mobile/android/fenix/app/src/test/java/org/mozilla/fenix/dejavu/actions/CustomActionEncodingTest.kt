/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CustomActionEncodingTest {
    private val title = "x&chat_id=1 <b>\"it's\"</b>"

    private fun encode(contentType: String?) = CustomActionRunner.encodeBodyValue(title, contentType)

    @Test
    fun `a page title cannot add fields to a form body`() {
        assertEquals(
            "x%26chat_id%3D1+%3Cb%3E%22it%27s%22%3C%2Fb%3E",
            encode("application/x-www-form-urlencoded; charset=UTF-8"),
        )
    }

    @Test
    fun `a page title cannot add markup to an XML body`() {
        assertEquals("x&amp;chat_id=1 &lt;b&gt;&quot;it&apos;s&quot;&lt;/b&gt;", encode("application/xml"))
    }

    @Test
    fun `a page title cannot end a JSON string`() {
        assertEquals("x&chat_id=1 <b>\\\"it's\\\"<\\/b>", encode("application/json"))
    }

    @Test
    fun `other bodies get the value as it is`() {
        assertEquals(title, encode("text/plain"))
        assertEquals(title, encode(null))
    }

    @Test
    fun `headers whose name tells of a secret are hidden in the editor`() {
        listOf("Authorization", "X-Gotify-Key", "X-API-Token", "Cookie", "x-session-id").forEach {
            assertTrue(it, isSecretHeader(it))
        }
        listOf("Content-Type", "Accept", "User-Agent").forEach { assertFalse(it, isSecretHeader(it)) }
    }
}
