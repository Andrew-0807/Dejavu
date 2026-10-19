/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu

import org.json.JSONObject
import java.net.URLEncoder

/** A typed keyword match: search [engineId] for [text], or, when [engineId] is null, open the URL [text]. */
data class KeywordMatch(val engineId: String?, val text: String)

/** Splits "keyword rest" and looks the keyword up in [engineKeywords] (keyword to engine id) or [bookmarkKeywords] (keyword to URL with %s). */
fun resolveSearchKeyword(
    input: String,
    engineKeywords: Map<String, String>,
    bookmarkKeywords: Map<String, String>,
): KeywordMatch? {
    val parts = input.trim().split(" ", limit = 2)
    val rest = parts.getOrElse(1) { "" }.trim()
    if (rest.isEmpty()) return null
    val word = parts[0].lowercase()
    engineKeywords[word]?.let { return KeywordMatch(it, rest) }
    return bookmarkKeywords[word]?.let { KeywordMatch(null, it.replace("%s", URLEncoder.encode(rest, "UTF-8"))) }
}

/** Returns a copy where [value] has [keyword] as its only keyword, or none when [keyword] is blank. */
fun Map<String, String>.withKeyword(keyword: String, value: String): Map<String, String> =
    filterValues { it != value } + if (keyword.isBlank()) emptyMap() else mapOf(keyword.trim().lowercase() to value)

private val DEFAULT_ENGINE_KEYWORDS = mapOf("g" to "google", "d" to "ddg")

/**
 * The engine keywords with the defaults "g" for Google and "d" for DuckDuckGo added for the installed [engineIds] the
 * user gave no keyword. A saved "" marks a default the user cleared.
 */
fun Map<String, String>.withDefaultEngineKeywords(engineIds: List<String>): Map<String, String> =
    filterValues { it.isNotEmpty() } + DEFAULT_ENGINE_KEYWORDS.mapNotNull { (keyword, idStart) ->
        engineIds.firstOrNull { it.startsWith(idStart) }
            ?.takeIf { keyword !in this && it !in values }
            ?.let { keyword to it }
    }

/** [withKeyword] for an engine, remembering a cleared default keyword so it does not come back. */
fun Map<String, String>.withEngineKeyword(
    keyword: String,
    engineId: String,
    engineIds: List<String>,
): Map<String, String> {
    val cleared = withDefaultEngineKeywords(engineIds).keywordFor(engineId)
        .takeIf { keyword.isBlank() && it in DEFAULT_ENGINE_KEYWORDS }
    return withKeyword(keyword, engineId) + listOfNotNull(cleared?.let { it to "" })
}

fun Map<String, String>.keywordFor(value: String): String = entries.find { it.value == value }?.key.orEmpty()

fun String.toKeywordMap(): Map<String, String> =
    runCatching { JSONObject(this).let { o -> o.keys().asSequence().associateWith { o.getString(it) } } }
        .getOrDefault(emptyMap())

fun Map<String, String>.toKeywordJson(): String = JSONObject(this).toString()
