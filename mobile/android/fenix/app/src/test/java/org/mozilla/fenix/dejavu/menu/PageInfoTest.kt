/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.menu

import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.state.state.ContainerState
import mozilla.components.browser.state.state.ReaderState
import mozilla.components.browser.state.state.SecurityInfo
import mozilla.components.browser.state.state.TrackingProtectionState
import mozilla.components.browser.state.state.TranslationsState
import mozilla.components.browser.state.state.createTab
import mozilla.components.concept.engine.content.blocking.Tracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.dejavu.containers.ContainerColor
import org.mozilla.fenix.dejavu.containers.ContainerRecord
import org.mozilla.fenix.dejavu.workspaces.PinKind
import org.mozilla.fenix.dejavu.workspaces.PinnedItem
import org.mozilla.fenix.dejavu.workspaces.SplitView
import org.mozilla.fenix.dejavu.workspaces.SyncedDevice
import org.mozilla.fenix.dejavu.workspaces.Workspace
import org.mozilla.fenix.dejavu.workspaces.WorkspaceState

@RunWith(AndroidJUnit4::class)
class PageInfoTest {
    private val home = Workspace(id = "home", name = "Home", icon = "H", createdAt = 0, updatedAt = 0)
    private val work = Workspace(id = "work", name = "Work", containerId = WORK, createdAt = 0, updatedAt = 0)
    private val laptop = Workspace(
        id = "laptop",
        name = "Laptop",
        createdAt = 0,
        updatedAt = 0,
        device = SyncedDevice.DESKTOP,
    )
    private val workContainer = ContainerRecord(
        contextId = WORK,
        name = "Office",
        color = ContainerColor.ORANGE,
        icon = ContainerState.Icon.BRIEFCASE,
        createdAt = 0,
        updatedAt = 0,
    )
    private val temporary =
        workContainer.copy(contextId = "tmp", name = "tmp1", color = ContainerColor.WHITE, temporary = true)

    private fun workspaces(
        assignments: Map<String, String> = emptyMap(),
        pins: List<PinnedItem> = emptyList(),
        splits: List<SplitView> = emptyList(),
        tabTitles: Map<String, String> = emptyMap(),
    ) = WorkspaceState(
        workspaces = listOf(home, work, laptop),
        pins = pins,
        activeWorkspaceId = home.id,
        assignments = assignments,
        splits = splits,
        tabTitles = tabTitles,
    )

    private fun pin(tabId: String, workspaceId: String?, essential: Boolean = false) = PinnedItem(
        id = "pin-$tabId",
        workspaceId = workspaceId,
        parentId = null,
        kind = PinKind.TAB,
        title = "Pinned",
        url = PAGE,
        essential = essential,
        tabId = tabId,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun secureTab(blocked: Int = 0, ignored: Boolean = false, enabled: Boolean = true) = createTab(
        url = PAGE,
        id = TAB,
        title = "Example",
        securityInfo = SecurityInfo.Secure(host = "example.com", issuer = "Let's Encrypt"),
        trackingProtection = TrackingProtectionState(
            enabled = enabled,
            blockedTrackers = List(blocked) { Tracker("https://tracker$it.example/") },
            ignoredOnTrackingProtection = ignored,
        ),
    )

    @Test
    fun `a secure page says who verified it and how many trackers were blocked on it`() {
        val info = pageInfoOf(secureTab(blocked = 3), workspaces(), emptyList(), emptyMap())

        assertEquals("Example", info.title)
        assertEquals(PAGE, info.url)
        assertTrue(info.isWebPage)
        assertEquals(true, info.isSecure)
        assertEquals("Let's Encrypt", info.verifier)
        assertEquals(3, info.trackersBlocked)
    }

    @Test
    fun `trackers are not blocked on a site with an exception, nor with tracking protection off`() {
        val excepted = secureTab(blocked = 2, ignored = true)

        assertNull(pageInfoOf(excepted, workspaces(), emptyList(), emptyMap()).trackersBlocked)
        assertNull(pageInfoOf(secureTab(enabled = false), workspaces(), emptyList(), emptyMap()).trackersBlocked)
    }

    @Test
    fun `a page that is not secure names no verifier`() {
        val tab = createTab(url = "http://example.com/", id = TAB, securityInfo = SecurityInfo.Insecure(issuer = "No"))

        val info = pageInfoOf(tab, workspaces(), emptyList(), emptyMap())

        assertEquals(false, info.isSecure)
        assertNull(info.verifier)
    }

    @Test
    fun `nothing is said about the connection while it is not known, nor for a page that is not from the web`() {
        val loading = pageInfoOf(createTab(url = PAGE, id = TAB), workspaces(), emptyList(), emptyMap())
        val blank = pageInfoOf(createTab(url = "about:blank", id = TAB), workspaces(), emptyList(), emptyMap())

        assertTrue(loading.isWebPage)
        assertNull(loading.isSecure)
        assertFalse(blank.isWebPage)
        assertNull(blank.isSecure)
    }

    @Test
    fun `a tab shows the container Dejavu keeps, or else the one the browser knows`() {
        val known = ContainerState("shop", "Shopping", ContainerState.Color.GREEN, ContainerState.Icon.CART)
        fun containerOf(contextId: String?) = pageInfoOf(
            createTab(url = PAGE, id = TAB, contextId = contextId),
            workspaces(),
            listOf(workContainer, temporary),
            mapOf(known.contextId to known),
        ).container

        assertEquals(
            PageContainer("Office", ContainerColor.ORANGE, ContainerState.Icon.BRIEFCASE, temporary = false),
            containerOf(WORK),
        )
        assertEquals(true, containerOf("tmp")?.temporary)
        assertEquals(
            PageContainer("Shopping", ContainerColor.GREEN, ContainerState.Icon.CART, temporary = false),
            containerOf("shop"),
        )
        assertNull(containerOf(null))
    }

    @Test
    fun `a tab shows the workspace it was opened in, with the container that marks it`() {
        val info = pageInfoOf(
            createTab(url = PAGE, id = TAB),
            workspaces(assignments = mapOf(TAB to work.id)),
            listOf(workContainer),
            emptyMap(),
        )

        assertEquals(work, info.workspace)
        assertEquals(workContainer, info.workspaceContainer)
        assertTrue(info.canChangePlace)
    }

    @Test
    fun `a pinned tab shows the workspace it is pinned in, and an essential is marked as one`() {
        val pinned = pageInfoOf(
            createTab(url = PAGE, id = TAB),
            workspaces(assignments = mapOf(TAB to home.id), pins = listOf(pin(TAB, work.id))),
            emptyList(),
            emptyMap(),
        )
        val essential = pageInfoOf(
            createTab(url = PAGE, id = TAB),
            workspaces(pins = listOf(pin(TAB, workspaceId = null, essential = true))),
            emptyList(),
            emptyMap(),
        )

        assertEquals(work, pinned.workspace)
        assertEquals(listOf(PageMark.PINNED), pinned.marks)
        assertEquals(listOf(PageMark.ESSENTIAL), essential.marks)
    }

    @Test
    fun `a private tab has neither a container nor a workspace, and cannot be moved`() {
        val info = pageInfoOf(
            createTab(url = PAGE, id = TAB, private = true, contextId = WORK),
            workspaces(),
            listOf(workContainer),
            emptyMap(),
        )

        assertTrue(info.isPrivate)
        assertNull(info.container)
        assertNull(info.workspace)
        assertFalse(info.canChangePlace)
    }

    @Test
    fun `a tab of another device on the account cannot be moved, since it follows the device`() {
        val info = pageInfoOf(
            createTab(url = PAGE, id = TAB),
            workspaces(assignments = mapOf(TAB to laptop.id)),
            emptyList(),
            emptyMap(),
        )

        assertEquals(laptop, info.workspace)
        assertFalse(info.canChangePlace)
    }

    @Test
    fun `a tab the user renamed shows its own title`() {
        val info = pageInfoOf(secureTab(), workspaces(tabTitles = mapOf(TAB to "Reading")), emptyList(), emptyMap())

        assertEquals("Reading", info.title)
    }

    @Test
    fun `split view, the desktop site, reader view and translation are marked`() {
        val tab = createTab(url = PAGE, id = TAB, desktopMode = true, readerState = ReaderState(active = true))
            .copy(translationsState = TranslationsState(isTranslated = true))

        val info = pageInfoOf(
            tab,
            workspaces(splits = listOf(SplitView("split", listOf(TAB, "other")))),
            emptyList(),
            emptyMap(),
        )

        assertEquals(
            listOf(PageMark.SPLIT_VIEW, PageMark.DESKTOP_SITE, PageMark.READER_VIEW, PageMark.TRANSLATED),
            info.marks,
        )
    }

    private companion object {
        const val TAB = "tab"
        const val WORK = "work-container"
        const val PAGE = "https://example.com/page?q=1"
    }
}
