/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.menu

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionsBarDefaultTest {
    @Test
    fun `the bar starts with New tab, Home in the middle`() {
        assertEquals(listOf("new_tab", "share", "home", "refresh", "search"), ActionsBarLayout.DEFAULT)
    }

    @Test
    fun `a bar saved as Dejavu 2_1 to 3_0 started it gets New tab in place of Forward`() {
        val saved = listOf("forward", "share", "home", "refresh", "search")

        assertEquals(ActionsBarLayout.DEFAULT, ActionsBarLayout.upgraded(saved))
    }

    @Test
    fun `a bar the user changed stays as it is`() {
        val moved = listOf("share", "forward", "home", "refresh", "search")
        val shorter = listOf("forward", "home", "search")

        assertEquals(moved, ActionsBarLayout.upgraded(moved))
        assertEquals(shorter, ActionsBarLayout.upgraded(shorter))
        assertEquals(ActionsBarLayout.DEFAULT, ActionsBarLayout.upgraded(ActionsBarLayout.DEFAULT))
    }
}
