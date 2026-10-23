/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import mozilla.components.concept.engine.preferences.Branch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JitAndWebGlTest {
    @Test
    fun `the switches set the engine defaults, so about config changes stay`() {
        assertTrue(JitAndWebGl.prefs(jit = false, webGl = false).all { it.branch == Branch.DEFAULT })
    }

    @Test
    fun `JIT off turns every JIT compiler off and WebGL off disables WebGL`() {
        val prefs = JitAndWebGl.prefs(jit = false, webGl = false).associate { it.pref to it.value }

        assertEquals(
            mapOf(
                "javascript.options.baselinejit" to false,
                "javascript.options.ion" to false,
                "javascript.options.jithints" to false,
                "javascript.options.native_regexp" to false,
                "javascript.options.wasm_optimizingjit" to false,
                "webgl.disabled" to true,
            ),
            prefs,
        )
    }

    @Test
    fun `on, they keep the engine's own values`() {
        val prefs = JitAndWebGl.prefs(jit = true, webGl = true).associate { it.pref to it.value }

        assertTrue(prefs.filterKeys { it.startsWith("javascript.options.") }.values.all { it })
        assertEquals(false, prefs["webgl.disabled"])
    }
}
