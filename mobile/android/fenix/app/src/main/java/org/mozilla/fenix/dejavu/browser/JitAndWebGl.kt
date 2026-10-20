/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import mozilla.components.ExperimentalAndroidComponentsApi
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.preferences.Branch
import mozilla.components.concept.engine.preferences.SetBrowserPreference
import mozilla.components.support.base.log.logger.Logger

/**
 * The JavaScript JIT compilers and WebGL, which most attacks on browsers go through, on or off as the privacy and
 * security settings say. They are set as the engine's defaults, so values changed in about:config stay.
 */
object JitAndWebGl {
    private val logger = Logger("JitAndWebGl")

    // Read when a page's process starts, so a change reaches every page once Dejavu restarts.
    private val JIT_PREFS = listOf(
        "javascript.options.baselinejit",
        "javascript.options.ion",
        "javascript.options.jithints",
        "javascript.options.native_regexp",
        "javascript.options.wasm_optimizingjit",
    )
    private const val WEBGL_DISABLED = "webgl.disabled"

    /** The engine defaults for the JIT on or off as [jit] says, and WebGL as [webGl] says. */
    internal fun prefs(jit: Boolean, webGl: Boolean): List<SetBrowserPreference<Boolean>> =
        JIT_PREFS.map { SetBrowserPreference.setBoolPref(it, jit, Branch.DEFAULT) } +
            SetBrowserPreference.setBoolPref(WEBGL_DISABLED, !webGl, Branch.DEFAULT)

    /** Sets the JIT and WebGL in [engine], when it starts and when a switch changes. */
    @OptIn(ExperimentalAndroidComponentsApi::class)
    fun apply(engine: Engine, jit: Boolean, webGl: Boolean) {
        engine.setBrowserPrefs(
            prefs = prefs(jit, webGl),
            onSuccess = {},
            onError = { logger.error("Could not set the JIT and WebGL prefs", it) },
        )
    }
}
