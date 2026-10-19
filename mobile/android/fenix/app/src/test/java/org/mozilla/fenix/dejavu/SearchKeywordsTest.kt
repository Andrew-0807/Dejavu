/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchKeywordsTest {
    private val engines = mapOf("w" to "wikipedia")
    private val bookmarks = mapOf("gh" to "https://github.com/search?q=%s")

    @Test
    fun `resolves engine and bookmark keywords`() {
        assertEquals(KeywordMatch("wikipedia", "cats and dogs"), resolveSearchKeyword("W cats and dogs", engines, bookmarks))
        assertEquals(
            KeywordMatch(null, "https://github.com/search?q=a+b%26c"),
            resolveSearchKeyword("gh a b&c", engines, bookmarks),
        )
    }

    @Test
    fun `bookmark keywords follow the bookmark guid and still read old URL entries`() {
        val urls = mapOf("guid1" to "https://example.com/new?q=%s")
        val saved = mapOf("ex" to "guid1", "gh" to "https://github.com/search?q=%s", "gone" to "guid2")
        assertEquals(
            KeywordMatch(null, "https://example.com/new?q=cats"),
            resolveSearchKeyword("ex cats", engines, saved, urls::get),
        )
        assertEquals(
            KeywordMatch(null, "https://github.com/search?q=cats"),
            resolveSearchKeyword("gh cats", engines, saved, urls::get),
        )
        assertNull(resolveSearchKeyword("gone cats", engines, saved, urls::get))
    }

    @Test
    fun `falls through without keyword or rest`() {
        assertNull(resolveSearchKeyword("x cats", engines, bookmarks))
        assertNull(resolveSearchKeyword("w", engines, bookmarks))
        assertNull(resolveSearchKeyword("w   ", engines, bookmarks))
    }

    @Test
    fun `default keywords search Google and DuckDuckGo until the user changes them`() {
        val ids = listOf("google-b-m", "ddg", "wikipedia")
        val defaults = emptyMap<String, String>().withDefaultEngineKeywords(ids)
        assertEquals(KeywordMatch("google-b-m", "cats"), resolveSearchKeyword("g cats", defaults, emptyMap()))
        assertEquals(KeywordMatch("ddg", "cats"), resolveSearchKeyword("d cats", defaults, emptyMap()))
        val googleOnly = emptyMap<String, String>().withDefaultEngineKeywords(listOf("google"))
        assertNull(resolveSearchKeyword("d cats", googleOnly, emptyMap()))
        val cleared = emptyMap<String, String>().withEngineKeyword("", "ddg", ids)
        assertNull(resolveSearchKeyword("d cats", cleared.withDefaultEngineKeywords(ids), emptyMap()))
        val renamed = emptyMap<String, String>().withEngineKeyword("gg", "google-b-m", ids)
            .withDefaultEngineKeywords(ids)
        assertNull(resolveSearchKeyword("g cats", renamed, emptyMap()))
    }

    @Test
    fun `bookmark keywords open alone, and substitute or ignore the rest`() {
        val urls = mapOf("plain" to "https://example.com/", "query" to "https://example.com/?q=%s")
        val saved = mapOf("ex" to "plain", "q" to "query")
        assertEquals(KeywordMatch(null, "https://example.com/"), resolveSearchKeyword("ex", engines, saved, urls::get))
        assertEquals(
            KeywordMatch(null, "https://example.com/"),
            resolveSearchKeyword("ex cats", engines, saved, urls::get),
        )
        assertEquals(
            KeywordMatch(null, "https://example.com/?q=cats+dogs"),
            resolveSearchKeyword("Q cats dogs", engines, saved, urls::get),
        )
        assertEquals(
            KeywordMatch(null, "https://example.com/?q="),
            resolveSearchKeyword("q", engines, saved, urls::get),
        )
    }

    @Test
    fun `keywordOwner finds collisions across engines, bookmarks and the settings keyword`() {
        val withDefaults = engines.withDefaultEngineKeywords(listOf("google", "wikipedia"))
        assertEquals("wikipedia", keywordOwner(" W ", withDefaults, bookmarks, "o"))
        assertEquals("google", keywordOwner("g", withDefaults, bookmarks, "o"))
        assertEquals("https://github.com/search?q=%s", keywordOwner("gh", withDefaults, bookmarks, "o"))
        assertEquals(SETTINGS_KEYWORD_OWNER, keywordOwner("O", withDefaults, bookmarks, "o"))
        assertNull(keywordOwner("free", withDefaults, bookmarks, "o"))
        assertNull(keywordOwner("  ", withDefaults, bookmarks, "o"))
        // A cleared default keyword is saved as "" and frees the keyword.
        assertNull(keywordOwner("d", mapOf("d" to ""), bookmarks, "o"))
    }

    @Test
    fun `withKeyword replaces old keyword`() {
        assertEquals(mapOf("wk" to "wikipedia"), engines.withKeyword("WK", "wikipedia"))
        assertEquals(emptyMap<String, String>(), engines.withKeyword("", "wikipedia"))
    }
}
