/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.browser.toolbar.utils

/**
 * What the address field shows for a page while not edited.
 *
 * @property title The page title, or `null` when the page has none or its title is only its address.
 * @property domain The host, or the whole address when it has no host (like `about:config`).
 * @property path Everything after the host, empty for the root page.
 */
data class AddressParts(val title: String?, val domain: String, val path: String) {
    /** What a screen reader says for the field, before the hint. */
    val spoken: String
        get() = listOfNotNull(title, domain + path).joinToString(", ")
}

/** Splits [url] (with or without scheme) into [AddressParts], or `null` when there is nothing to show. */
fun addressParts(title: String?, url: String?): AddressParts? {
    val bare = withoutScheme(url?.trim().orEmpty())
    val cut = bare.indexOfFirst { it == '/' || it == '?' || it == '#' }
    val (domain, path) =
        when {
            cut <= 0 -> bare to ""
            else -> bare.substring(0, cut) to bare.substring(cut).takeUnless { it == "/" }.orEmpty()
        }
    val realTitle = title?.trim()?.takeIf { it.isNotEmpty() && !isAddress(it, bare) }
    if (domain.isEmpty() && realTitle == null) return null
    return AddressParts(realTitle, domain, path)
}

private fun withoutScheme(text: String): String {
    val afterScheme = text.indexOf("://").let { if (it < 0) text else text.substring(it + SCHEME_SEPARATOR_LENGTH) }
    return afterScheme.removePrefix("www.")
}

/** Whether [title] is only the address [bare], as tabs without a title show it, possibly cut short. */
private fun isAddress(title: String, bare: String): Boolean {
    val shown = withoutScheme(title).trimEnd('/', '…')
    if (shown.isEmpty()) return true
    return shown.equals(bare.trimEnd('/'), ignoreCase = true) ||
        (' ' !in shown && '.' in shown && bare.startsWith(shown, ignoreCase = true))
}

private const val SCHEME_SEPARATOR_LENGTH = 3
