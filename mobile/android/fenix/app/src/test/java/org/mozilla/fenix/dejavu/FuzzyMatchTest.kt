/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatchTest {
    @Test
    fun `matches characters in order and ranks word starts and runs higher`() {
        assertNull(fuzzyScore("xyz", "Tracking protection"))
        assertNull(fuzzyScore("pt", "Tracking"))
        assertNotNull(fuzzyScore("tkp", "Tracking protection"))
        assertTrue(fuzzyScore("tp", "Tracking protection")!! > fuzzyScore("tp", "Settings top")!!)
        assertTrue(fuzzyScore("dark", "Dark theme")!! >= "dark".length * FUZZY_GOOD_SCORE_PER_CHAR)
    }
}
