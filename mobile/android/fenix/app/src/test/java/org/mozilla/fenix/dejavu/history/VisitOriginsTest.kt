/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.support.test.robolectric.testContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisitOriginsTest {
    private val home = VisitOrigin("home", null)
    private val work = VisitOrigin("work", "folder")

    @Test
    fun `forgets deleted pages and everything on clear`() {
        val origins = VisitOrigins.get(testContext)
        origins.record("https://a.example", home)
        origins.record("https://b.example", work)

        origins.forget(listOf("https://a.example"))
        assertNull(origins.originOf("https://a.example"))
        assertEquals(work, origins.originOf("https://b.example"))

        origins.clear()
        assertNull(origins.originOf("https://b.example"))

        origins.record("https://c.example", home)
        assertEquals(home, origins.originOf("https://c.example"))
    }
}
