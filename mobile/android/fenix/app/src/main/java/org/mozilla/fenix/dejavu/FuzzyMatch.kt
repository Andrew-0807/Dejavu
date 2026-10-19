/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu

private const val SCORE_MATCH = 16
private const val BONUS_BOUNDARY = 8
private const val BONUS_CONSECUTIVE = 4
private const val PENALTY_GAP_START = 3
private const val PENALTY_GAP_EXTENSION = 1

/** A score per typed character from which a match counts as good. */
const val FUZZY_GOOD_SCORE_PER_CHAR = 20

/**
 * Scores [text] for [query] like fzf: every query character must appear in order, and matches at word starts or right
 * after the previous match score higher, gaps lower. Returns null when [text] does not match.
 */
fun fuzzyScore(query: String, text: String): Int? {
    // ponytail: greedy leftmost match, not fzf's optimal alignment. Add the backward pass if rankings look off.
    val needle = query.lowercase().filterNot { it.isWhitespace() }
    if (needle.isEmpty()) return null
    val haystack = text.lowercase()
    var score = 0
    var from = 0
    var last = -1
    for (char in needle) {
        val at = haystack.indexOf(char, from)
        if (at < 0) return null
        score += SCORE_MATCH
        if (at == 0 || !haystack[at - 1].isLetterOrDigit()) score += BONUS_BOUNDARY
        if (last >= 0 && at == last + 1) score += BONUS_CONSECUTIVE
        if (last >= 0 && at > last + 1) score -= PENALTY_GAP_START + (at - last - 2) * PENALTY_GAP_EXTENSION
        last = at
        from = at + 1
    }
    return score
}
