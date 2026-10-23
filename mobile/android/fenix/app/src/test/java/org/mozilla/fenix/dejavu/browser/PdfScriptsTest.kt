/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import mozilla.components.concept.engine.preferences.Branch
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfScriptsTest {
    @Test
    fun `scripts in PDFs are turned off as the engine default, so about config changes stay`() {
        val pref = PdfScripts.prefs.single()

        assertEquals("pdfjs.enableScripting", pref.pref)
        assertEquals(false, pref.value)
        assertEquals(Branch.DEFAULT, pref.branch)
    }
}
