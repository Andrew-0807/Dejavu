/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.WebExtensionAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.extension.WebExtensionPromptRequest
import mozilla.components.concept.engine.webextension.InstallationMethod
import mozilla.components.concept.engine.webextension.PermissionPromptResponse
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store
import org.mozilla.fenix.dejavu.features.wasUpdated
import org.mozilla.fenix.ext.components

/**
 * uBlock Origin, the ad and tracker blocker, which a fresh install of Dejavu adds from addons.mozilla.org in the
 * background, allowed in private tabs too and without the add-on dialogs. When it cannot be downloaded, the next start
 * tries again. An update of Dejavu does not add it, and once it was added Dejavu never adds it again, also after the
 * user removed it.
 */
object UBlockOrigin {
    const val ID = "uBlock0@raymondhill.net"
    private const val URL = "https://addons.mozilla.org/android/downloads/latest/uBlock0@raymondhill.net/latest.xpi"

    // Kept with Dejavu's other once-only defaults.
    private const val PREFS_NAME = "kaizen_settings"
    private const val KEY_STATE = "ublock_origin"
    private const val DUE = "due"
    private const val DONE = "done"

    // How long its prompts are answered without dialogs: while it installs, and for prompts that arrive after.
    private const val QUIET_INSTALL_MS = 120_000L
    private const val QUIET_AFTER_MS = 10_000L

    /** Until when, in [SystemClock.elapsedRealtime], the prompts of the install started here need no dialog. */
    @Volatile
    @VisibleForTesting
    internal var quietUntil = 0L

    /**
     * Whether a prompt about [extensionId] belongs to the install started here, so needs no dialog. A failed download
     * has no extension yet, so `null` counts as this one while it installs.
     */
    internal fun isQuiet(extensionId: String?, now: Long = SystemClock.elapsedRealtime()): Boolean =
        (extensionId == null || extensionId == ID) && now < quietUntil

    /** Adds uBlock Origin when this install is due to get it and the add-ons the engine has, [installed], lack it. */
    fun addIfDue(context: Context, installed: List<WebExtension>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val state = prefs.getString(KEY_STATE, null)
            ?: (if (context.wasUpdated()) DONE else DUE).also { prefs.edit { putString(KEY_STATE, it) } }
        if (state != DUE) return
        if (installed.any { it.id == ID }) {
            prefs.edit { putString(KEY_STATE, DONE) }
        } else if (SystemClock.elapsedRealtime() >= quietUntil) {
            install(context, prefs)
        }
    }

    private fun install(context: Context, prefs: SharedPreferences) {
        quietUntil = SystemClock.elapsedRealtime() + QUIET_INSTALL_MS
        val ended = { quietUntil = SystemClock.elapsedRealtime() + QUIET_AFTER_MS }
        context.components.addonManager.installAddon(
            url = URL,
            installationMethod = InstallationMethod.MANAGER,
            onSuccess = {
                prefs.edit { putString(KEY_STATE, DONE) }
                ended()
            },
            onError = { ended() },
        )
    }
}

/**
 * Answers the prompts of the uBlock Origin install that [UBlockOrigin] starts, so no dialog shows: its permissions are
 * granted, in private tabs too, and the dialogs after it was added or could not be added are left out. Other prompts
 * pass on.
 */
internal class UBlockOriginPromptMiddleware(
    private val now: () -> Long = SystemClock::elapsedRealtime,
) : Middleware<BrowserState, BrowserAction> {
    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        val request = (action as? WebExtensionAction.UpdatePromptRequestWebExtensionAction)?.promptRequest
        val extensionId = when (request) {
            is WebExtensionPromptRequest.AfterInstallation.Permissions.Required -> request.extension.id
            is WebExtensionPromptRequest.AfterInstallation.PostInstallation -> request.extension.id
            is WebExtensionPromptRequest.BeforeInstallation.InstallationFailed -> request.extension?.id
            else -> return next(action)
        }
        if (!UBlockOrigin.isQuiet(extensionId, now())) return next(action)
        if (request is WebExtensionPromptRequest.AfterInstallation.Permissions.Required) {
            request.onConfirm(PermissionPromptResponse(isPermissionsGranted = true, isPrivateModeGranted = true))
        }
    }
}
