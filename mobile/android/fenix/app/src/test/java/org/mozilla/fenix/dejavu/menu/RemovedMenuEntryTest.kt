/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.menu

import org.junit.Assert.assertEquals
import org.junit.Test

class RemovedMenuEntryTest {
    @Test
    fun `a saved menu or actions bar with Report site issue simply leaves it out`() {
        val rows = MoreMenuLayout.resolve(listOf(listOf("back", "report_broken_site", "share")), emptyList())
        val bar = ActionsBarLayout.resolve(listOf("forward", "report_broken_site", "home"), emptyList())

        assertEquals(listOf("back", "share"), rows.single().map { it.key })
        assertEquals(listOf("forward", "home"), bar.map { it.key })
    }
}
