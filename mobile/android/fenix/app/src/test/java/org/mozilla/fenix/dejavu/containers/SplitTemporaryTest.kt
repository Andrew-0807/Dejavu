/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.containers

import mozilla.components.browser.state.state.ContainerState
import org.junit.Assert.assertEquals
import org.junit.Test

class SplitTemporaryTest {
    private fun container(name: String, temporary: Boolean = false) =
        ContainerRecord(name, name, ContainerColor.BLUE, ContainerState.Icon.CIRCLE, 1L, 1L, temporary = temporary)

    @Test
    fun `permanent containers keep their order and temporary ones come last, tmp2 before tmp10`() {
        val records = listOf(
            container("tmp10", temporary = true),
            container("Work"),
            container("tmp2", temporary = true),
            container("Bank"),
            container("tmp1", temporary = true),
        )

        val (permanent, temporary) = records.splitTemporary()

        assertEquals(listOf("Work", "Bank"), permanent.map { it.name })
        assertEquals(listOf("tmp1", "tmp2", "tmp10"), temporary.map { it.name })
    }
}
