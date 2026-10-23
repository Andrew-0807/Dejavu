/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import mozilla.components.concept.engine.Engine
import mozilla.components.support.base.log.logger.Logger

/**
 * Firefox's search telemetry extensions ("ads" and "cookies"), which read search result pages for telemetry Dejavu
 * never sends. Dejavu installs them no more, but the engine keeps the ones earlier versions installed until they are
 * uninstalled.
 */
object SearchTelemetryExtensions {
    private val logger = Logger("SearchTelemetryExtensions")

    internal val IDS = setOf("ads@mozac.org", "cookies@mozac.org")

    /** Uninstalls them from [engine] when an earlier version left them installed. */
    fun uninstall(engine: Engine) {
        engine.listInstalledWebExtensions(
            onSuccess = { extensions ->
                extensions.filter { it.id in IDS }.forEach { extension ->
                    engine.uninstallWebExtension(
                        extension,
                        onError = { id, throwable -> logger.error("Could not uninstall $id", throwable) },
                    )
                }
            },
            onError = { logger.error("Could not list the installed extensions", it) },
        )
    }
}
