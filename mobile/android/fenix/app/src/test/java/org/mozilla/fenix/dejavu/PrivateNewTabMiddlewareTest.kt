/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu

import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.SessionState.Source.Internal.UserEntered
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.Middleware
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrivateNewTabMiddlewareTest {
    /** Whether each tab was private as the middleware after [PrivateNewTabMiddleware], like history's, saw it. */
    private val seen = mutableListOf<Boolean>()

    private val store = BrowserStore(
        middleware = listOf(
            PrivateNewTabMiddleware(),
            recordAddedTabs(),
        ),
    )

    private fun recordAddedTabs(): Middleware<BrowserState, BrowserAction> = { _, next, action ->
        if (action is TabListAction.AddTabAction) seen += action.tab.content.private
        next(action)
    }

    @After
    fun tearDown() = NewTabContainerChoice.clear()

    @Test
    fun `a search started with the Private chip is private before any other middleware sees it`() {
        NewTabContainerChoice.setPrivate()

        store.dispatch(TabListAction.AddTabAction(createTab("https://search.example/?q=x", source = UserEntered)))

        assertEquals(listOf(true), seen)
        assertEquals(true, store.state.tabs.single().content.private)
        assertNull(NewTabContainerChoice.peek())
    }

    @Test
    fun `a tab a page opens does not take the private tab asked for`() {
        store.dispatch(TabListAction.AddTabAction(createTab("https://page.example/", id = "page", source = UserEntered)))
        NewTabContainerChoice.setPrivate()

        store.dispatch(TabListAction.AddTabAction(createTab("https://popup.example/", parentId = "page", source = UserEntered)))

        assertEquals(listOf(false, false), seen)
        assertEquals(true, NewTabContainerChoice.peek()?.private)
    }

    @Test
    fun `a tab started without the chip stays normal`() {
        store.dispatch(TabListAction.AddTabAction(createTab("https://typed.example/", source = UserEntered)))

        assertEquals(listOf(false), seen)
    }
}
