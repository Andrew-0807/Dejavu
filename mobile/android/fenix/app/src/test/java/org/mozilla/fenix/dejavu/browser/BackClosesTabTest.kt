/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.content.HistoryState
import mozilla.components.browser.state.state.createTab
import mozilla.components.concept.engine.history.HistoryItem
import mozilla.components.concept.engine.utils.ABOUT_HOME_URL
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackClosesTabTest {
    private fun tab(
        vararg urls: String,
        current: Int = urls.lastIndex,
        canGoBack: Boolean = current > 0,
    ): TabSessionState {
        val tab = createTab(url = urls[current])
        val history = HistoryState(items = urls.map { HistoryItem(title = "", uri = it) }, currentIndex = current)
        return tab.copy(content = tab.content.copy(history = history, canGoBack = canGoBack))
    }

    @Test
    fun `Back goes to the earlier page of a tab`() {
        assertTrue(tab("https://a.example/", "https://b.example/").canGoBackToPage)
    }

    @Test
    fun `Back on the first page closes the tab`() {
        assertFalse(tab("https://a.example/").canGoBackToPage)
    }

    @Test
    fun `Back on a tab opened on the homepage closes it instead of showing the homepage`() {
        assertFalse(tab(ABOUT_HOME_URL, "https://a.example/").canGoBackToPage)
        assertTrue(tab(ABOUT_HOME_URL, "https://a.example/", "https://b.example/").canGoBackToPage)
    }

    @Test
    fun `without the history of a tab, Back follows the page's own back`() {
        val tab = createTab(url = "https://a.example/")
        assertTrue(tab.copy(content = tab.content.copy(canGoBack = true)).canGoBackToPage)
        assertFalse(tab.canGoBackToPage)
    }
}
