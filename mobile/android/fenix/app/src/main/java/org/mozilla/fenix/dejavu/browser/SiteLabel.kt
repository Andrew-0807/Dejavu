/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import android.text.SpannableString
import android.text.Spanned
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.core.net.toUri
import mozilla.components.browser.state.state.SecurityInfo
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.compose.browser.toolbar.ui.HighlightedDomainUrl
import mozilla.components.lib.publicsuffixlist.PublicSuffixList
import mozilla.components.support.ktx.kotlin.getRegistrableDomainIndexRange
import mozilla.components.support.ktx.kotlin.isIpv4OrIpv6
import mozilla.components.support.ktx.util.RegistrableDomainSpan
import mozilla.components.support.ktx.util.URLStringUtils
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.R

/**
 * The site of the page at [url], as the address bar shows it: the host of a web page without "www.", or the address
 * of any other page, like an about: page, without its scheme. Never the page's title, which the page chooses.
 */
fun siteOf(url: String): String = hostOf(url) ?: URLStringUtils.toDisplayUrl(url).toString()

/** [siteOf] the page at [url], with the registrable domain of a web page marked for [SiteText]. */
suspend fun siteLabel(url: String, publicSuffixList: PublicSuffixList): CharSequence {
    val host = hostOf(url) ?: return siteOf(url)
    val domain = if (host.isIpv4OrIpv6()) null else publicSuffixList.getPublicSuffixPlusOne(host).await()
    val start = domain?.let { host.lastIndexOf(it) }?.takeIf { it >= 0 && it + domain.length == host.length } ?: 0
    return SpannableString(host).apply {
        setSpan(RegistrableDomainSpan(), start, host.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

private fun hostOf(url: String): String? {
    val web = url.removePrefix(BLOB_PREFIX).takeIf { it.startsWith("https://") || it.startsWith("http://") }
    return web?.toUri()?.host?.takeIf { it.isNotEmpty() }?.removePrefix(WWW_PREFIX)
}

/**
 * Shows [site], from [siteLabel], like Fenix shows addresses: its registrable domain stands out, and stays in view when
 * the site does not fit, so that a long made-up subdomain cannot push it out.
 */
@Composable
fun SiteText(site: CharSequence, style: TextStyle, modifier: Modifier = Modifier) {
    HighlightedDomainUrl(
        url = site.toString(),
        registrableDomainIndexRange = site.getRegistrableDomainIndexRange(),
        fadedTextStyle = style.merge(TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)),
        boldedTextStyle = style.merge(TextStyle(color = MaterialTheme.colorScheme.onSurface)),
        modifier = modifier,
    )
}

/** How safe the connection to a page is, as the icon before the address in Fenix's address bar shows it. */
internal enum class SiteSecurity(@param:DrawableRes val icon: Int, @param:StringRes val label: Int?) {
    UNKNOWN(iconsR.drawable.mozac_ic_globe_24, null),
    INSECURE(iconsR.drawable.mozac_ic_shield_slash_24, R.string.connection_security_panel_not_secure),
    PROTECTED(iconsR.drawable.mozac_ic_shield_checkmark_24, R.string.connection_security_panel_secure),
    UNPROTECTED(iconsR.drawable.mozac_ic_shield_cross_24, R.string.connection_security_panel_secure),
    ;

    companion object {
        fun of(tab: TabSessionState): SiteSecurity = when {
            tab.content.securityInfo == SecurityInfo.Unknown -> UNKNOWN
            !tab.content.securityInfo.isSecure -> INSECURE
            tab.trackingProtection.enabled && !tab.trackingProtection.ignoredOnTrackingProtection -> PROTECTED
            else -> UNPROTECTED
        }
    }
}

private const val BLOB_PREFIX = "blob:"
private const val WWW_PREFIX = "www."
