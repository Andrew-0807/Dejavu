/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyRankTest {
    @Test
    fun `fuzzyRanked keeps matches only, best first, with the same scores as fuzzyScore`() {
        val titles = listOf("Settings top", "Tracking protection", "Theme", "Toolbar position")
        val ranked = titles.fuzzyRanked(" T P ") { it }
        assertEquals(listOf("Toolbar position", "Tracking protection", "Settings top"), ranked.map { it.first })
        ranked.forEach { (title, score) -> assertEquals(fuzzyScore("tp", title), score) }
        assertTrue(titles.fuzzyRanked("   ") { it }.isEmpty())
    }
}
