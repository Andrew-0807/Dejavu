/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings.doh

import androidx.annotation.VisibleForTesting
import mozilla.components.concept.engine.Engine
import org.mozilla.fenix.utils.Settings

internal interface DohSettingsProvider {
    fun getProtectionLevels(): List<ProtectionLevel>

    fun getDefaultProviders(): List<Provider>

    fun getSelectedProtectionLevel(): ProtectionLevel

    fun getSelectedProvider(): Provider?

    fun getExceptions(): List<String>

    fun setProtectionLevel(protectionLevel: ProtectionLevel, provider: Provider?)

    fun setCustomProvider(url: String)

    fun setExceptions(exceptions: List<String>)
}

internal class DefaultDohSettingsProvider(
    val engine: Engine,
    val settings: Settings,
) : DohSettingsProvider {
    // Dejavu: no Default Protection. There Firefox turns secure DNS on through Mozilla's rollout, which Dejavu does not
    // run, so secure DNS stayed off; Dejavu turns it on with Increased Protection instead (see DejavuDefaults).
    override fun getProtectionLevels(): List<ProtectionLevel> {
        return listOf(
            ProtectionLevel.Increased,
            ProtectionLevel.Max,
            ProtectionLevel.Off,
        )
    }

    // Bug 1946867 - Load DoH Provider List from Gecko instead of hardcoding
    private val dohDefaultProviderUrl = settings.dohDefaultProviderUrl

    // Dejavu: the providers' own addresses, not the ones they run for Firefox under Mozilla's agreements. Quad9, a
    // non-profit that also blocks malware domains, comes first.
    private val quad9 =
        Provider.BuiltIn(
            url = quad9Uri,
            name = "Quad9",
            default = dohDefaultProviderUrl.isBlank() || dohDefaultProviderUrl == quad9Uri,
        )
    private val mullvad =
        Provider.BuiltIn(
            url = mullvadUri,
            name = "Mullvad",
            default = dohDefaultProviderUrl == mullvadUri,
        )
    private val cloudflare =
        Provider.BuiltIn(
            url = cloudflareUri,
            name = "Cloudflare",
            default = dohDefaultProviderUrl == cloudflareUri,
        )
    private val builtIn = listOf(quad9, mullvad, cloudflare)
    private val providerUrl = settings.dohProviderUrl
    private val custom =
        Provider.Custom(
            url =
                if (builtIn.none { it.url == providerUrl }) {
                    providerUrl
                } else {
                    ""
                }
        )

    override fun getDefaultProviders(): List<Provider> = builtIn + custom

    override fun getSelectedProtectionLevel(): ProtectionLevel {
        return when (settings.getDohSettingsMode()) {
            Engine.DohSettingsMode.DEFAULT -> ProtectionLevel.Default
            Engine.DohSettingsMode.INCREASED -> ProtectionLevel.Increased
            Engine.DohSettingsMode.MAX -> ProtectionLevel.Max
            Engine.DohSettingsMode.OFF -> ProtectionLevel.Off
        }
    }

    override fun getSelectedProvider(): Provider? {
        return when (settings.getDohSettingsMode()) {
            Engine.DohSettingsMode.OFF,
            Engine.DohSettingsMode.DEFAULT -> {
                null
            }

            else -> {
                when (val url = settings.dohProviderUrl) {
                    "" -> builtIn.find { it.default } ?: builtIn.first()
                    else -> builtIn.find { it.url == url } ?: custom
                }
            }
        }
    }

    override fun getExceptions(): List<String> {
        return settings.dohExceptionsList.toList()
    }

    override fun setProtectionLevel(protectionLevel: ProtectionLevel, provider: Provider?) {
        if (protectionLevel is ProtectionLevel.Increased || protectionLevel is ProtectionLevel.Max) {
            requireNotNull(provider) { "Provider must not be null for Increased/Max protection level" }
            settings.dohProviderUrl = provider.url
            engine.settings.dohProviderUrl = provider.url
        }

        val newMode = protectionLevel.toDohSettingsMode()
        // Update the app layer
        settings.setDohSettingsMode(newMode)
        engine.settings.dohSettingsMode = newMode
    }

    override fun setCustomProvider(url: String) {
        // Update the app layer
        settings.dohProviderUrl = url
        // validate the url, maybe throw some "known expectations" that we can handle in the middleware
        engine.settings.dohProviderUrl = url
    }

    override fun setExceptions(exceptions: List<String>) {
        // Update the app layer
        settings.dohExceptionsList = exceptions.toSet()
        engine.settings.dohExceptionsList = exceptions
    }

    companion object {
        @VisibleForTesting val quad9Uri = "https://dns.quad9.net/dns-query"

        @VisibleForTesting val mullvadUri = "https://dns.mullvad.net/dns-query"

        @VisibleForTesting val cloudflareUri = "https://cloudflare-dns.com/dns-query"
    }
}
