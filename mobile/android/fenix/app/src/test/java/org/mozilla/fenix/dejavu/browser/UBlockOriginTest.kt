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
import mozilla.components.concept.engine.webextension.Metadata
import mozilla.components.concept.engine.webextension.PermissionPromptResponse
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.concept.engine.webextension.WebExtensionInstallException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UBlockOriginTest {
    private val now = 1_000L

    private fun extension(id: String, downloadUrl: String? = null): WebExtension = mockk<WebExtension>().also {
        every { it.id } returns id
        every { it.getMetadata() } returns mockk<Metadata> { every { this@mockk.downloadUrl } returns downloadUrl }
    }

    private fun ownExtension() = extension(UBlockOrigin.ID, UBlockOrigin.URL)

    private fun networkFailure() = WebExtensionPromptRequest.BeforeInstallation.InstallationFailed(
        extension = null,
        exception = WebExtensionInstallException.NetworkFailure(throwable = Exception("offline")),
    )

    private fun newStore() =
        BrowserStore(BrowserState(), middleware = listOf(UBlockOriginPromptMiddleware(now = { now })))

    private fun BrowserStore.prompt(request: WebExtensionPromptRequest) =
        dispatch(WebExtensionAction.UpdatePromptRequestWebExtensionAction(request))

    @After
    fun tearDown() {
        UBlockOrigin.quietUntil = 0L
        UBlockOrigin.failureUnclaimed = false
    }

    @Test
    fun `while uBlock Origin installs, its permissions are granted in private tabs too and no dialog shows`() {
        UBlockOrigin.quietUntil = now + 1
        val store = newStore()
        var response: PermissionPromptResponse? = null

        store.prompt(
            WebExtensionPromptRequest.AfterInstallation.Permissions.Required(
                extension = ownExtension(),
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
        UBlockOrigin.failureUnclaimed = true
        val store = newStore()

        store.prompt(WebExtensionPromptRequest.AfterInstallation.PostInstallation(ownExtension()))
        assertNull(store.state.webExtensionPromptRequest)

        store.prompt(networkFailure())
        assertNull(store.state.webExtensionPromptRequest)
    }

    @Test
    fun `only one failed download that names no add-on counts as uBlock Origin's, and only a network failure`() {
        UBlockOrigin.quietUntil = now + 1
        UBlockOrigin.failureUnclaimed = true
        val store = newStore()
        val corrupt = WebExtensionPromptRequest.BeforeInstallation.InstallationFailed(
            extension = null,
            exception = WebExtensionInstallException.CorruptFile(throwable = Exception("corrupt")),
        )

        store.prompt(corrupt)
        assertEquals(corrupt, store.state.webExtensionPromptRequest)

        store.dispatch(WebExtensionAction.ConsumePromptRequestWebExtensionAction)
        store.prompt(networkFailure())
        assertNull(store.state.webExtensionPromptRequest)

        val another = networkFailure()
        store.prompt(another)
        assertEquals(another, store.state.webExtensionPromptRequest)
    }

    @Test
    fun `an add-on with uBlock Origin's id from another address keeps its permission dialog`() {
        UBlockOrigin.quietUntil = now + 1
        val store = newStore()
        var response: PermissionPromptResponse? = null
        val impostor = WebExtensionPromptRequest.AfterInstallation.Permissions.Required(
            extension = extension(UBlockOrigin.ID, "https://example.com/ublock.xpi"),
            permissions = listOf("storage"),
            origins = listOf("<all_urls>"),
            dataCollectionPermissions = emptyList(),
            onConfirm = { response = it },
        )

        store.prompt(impostor)

        assertNull(response)
        assertEquals(impostor, store.state.webExtensionPromptRequest)
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
        val later = WebExtensionPromptRequest.AfterInstallation.PostInstallation(ownExtension())
        store.prompt(later)
        assertEquals(later, store.state.webExtensionPromptRequest)
    }
}
