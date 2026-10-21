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
 * PDFs open in the built-in viewer without running the scripts they carry, which most attacks through PDFs need.
 * Fillable forms still work, only their calculations and checks do not run. It is set as the engine's default when
 * Dejavu starts, because geckoview-prefs.js cannot set it: PdfJsDefaultPrefs.js is read after it and turns scripts
 * back on. A value changed in about:config stays.
 */
object PdfScripts {
    private val logger = Logger("PdfScripts")

    internal val prefs = listOf(SetBrowserPreference.setBoolPref("pdfjs.enableScripting", false, Branch.DEFAULT))

    /** Turns scripts in PDFs off in [engine]. */
    @OptIn(ExperimentalAndroidComponentsApi::class)
    fun turnOff(engine: Engine) {
        engine.setBrowserPrefs(
            prefs = prefs,
            onSuccess = {},
            onError = { logger.error("Could not turn scripts in PDFs off", it) },
        )
    }
}
