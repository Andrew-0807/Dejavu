/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import io.mockk.every
import io.mockk.mockk
import mozilla.components.browser.state.action.WebExtensionAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.extension.WebExtensionPromptRequest
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.webextension.PermissionPromptResponse
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.concept.engine.webextension.WebExtensionInstallException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UBlockOriginTest {
    private val now = 1_000L

    private fun extension(id: String): WebExtension = mockk<WebExtension>().also { every { it.id } returns id }

    private fun newStore() =
        BrowserStore(BrowserState(), middleware = listOf(UBlockOriginPromptMiddleware(now = { now })))

    private fun BrowserStore.prompt(request: WebExtensionPromptRequest) =
        dispatch(WebExtensionAction.UpdatePromptRequestWebExtensionAction(request))

    @After
    fun tearDown() {
        UBlockOrigin.quietUntil = 0L
    }

    @Test
    fun `while uBlock Origin installs, its permissions are granted in private tabs too and no dialog shows`() {
        UBlockOrigin.quietUntil = now + 1
        val store = newStore()
        var response: PermissionPromptResponse? = null

        store.prompt(
            WebExtensionPromptRequest.AfterInstallation.Permissions.Required(
                extension = extension(UBlockOrigin.ID),
                permissions = listOf("storage"),
                origins = listOf("<all_urls>"),
                dataCollectionPermissions = emptyList(),
                onConfirm = { response = it },
            ),
        )

        assertEquals(PermissionPromptResponse(isPermissionsGranted = true, isPrivateModeGranted = true), response)
        assertNull(store.state.webExtensionPromptRequest)
    }

    @Test
    fun `while uBlock Origin installs, no dialog shows once it was added or could not be downloaded`() {
        UBlockOrigin.quietUntil = now + 1
        val store = newStore()

        store.prompt(WebExtensionPromptRequest.AfterInstallation.PostInstallation(extension(UBlockOrigin.ID)))
        assertNull(store.state.webExtensionPromptRequest)

        store.prompt(
            WebExtensionPromptRequest.BeforeInstallation.InstallationFailed(
                extension = null,
                exception = WebExtensionInstallException.NetworkFailure(throwable = Exception("offline")),
            ),
        )
        assertNull(store.state.webExtensionPromptRequest)
    }

    @Test
    fun `other add-ons, and uBlock Origin installed by the user later, keep their dialogs`() {
        UBlockOrigin.quietUntil = now + 1
        val store = newStore()
        val other = WebExtensionPromptRequest.AfterInstallation.PostInstallation(extension("other@example.org"))

        store.prompt(other)
        assertEquals(other, store.state.webExtensionPromptRequest)

        UBlockOrigin.quietUntil = now
        store.dispatch(WebExtensionAction.ConsumePromptRequestWebExtensionAction)
        val later = WebExtensionPromptRequest.AfterInstallation.PostInstallation(extension(UBlockOrigin.ID))
        store.prompt(later)
        assertEquals(later, store.state.webExtensionPromptRequest)
    }
}
