/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureTourTest {
    @Test
    fun `every feature shows once, under the version that brought it`() {
        val features = FeatureTour.Releases.flatMap { it.features }

        assertEquals(DejavuFeature.entries.toSet(), features.toSet())
        assertEquals(features.size, features.toSet().size)
    }

    @Test
    fun `the versions go from the oldest to the newest`() {
        FeatureTour.Releases.zipWithNext().forEach { (older, newer) ->
            assertTrue(FeatureTour.compareVersions(older.version, newer.version) < 0)
        }
    }

    @Test
    fun `versions compare part by part`() {
        assertTrue(FeatureTour.compareVersions("2.0", "1.8") > 0)
        assertTrue(FeatureTour.compareVersions("1.10", "1.9") > 0)
        assertEquals(0, FeatureTour.compareVersions("2", "2.0"))
        assertTrue(FeatureTour.compareVersions("2.0", "2.0.1") < 0)
    }
}
