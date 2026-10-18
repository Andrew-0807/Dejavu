/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.SecurityInfo
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.toolbar.R as browserToolbarR
import mozilla.components.compose.base.progressbar.AnimatedProgressBar
import mozilla.components.compose.browser.toolbar.ActionContainer
import mozilla.components.compose.browser.toolbar.R as toolbarR
import mozilla.components.compose.browser.toolbar.concept.Action
import mozilla.components.compose.browser.toolbar.concept.PageOrigin.Companion.ContextualMenuOption
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarInteraction.BrowserToolbarEvent
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarStore
import mozilla.components.concept.engine.utils.ABOUT_HOME_URL
import mozilla.components.lib.state.ext.observeAsComposableState
import mozilla.components.support.ktx.kotlin.isContentUrl
import mozilla.components.support.utils.ClipboardHandler
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.R
import org.mozilla.fenix.components.components
import org.mozilla.fenix.dejavu.containers.DejavuContainerStorage
import org.mozilla.fenix.dejavu.ui.DejavuDropdownMenu
import org.mozilla.fenix.dejavu.ui.DejavuMenuItem
import org.mozilla.fenix.dejavu.ui.LocalPopupTheme

/**
 * Dejavu's address bar while a page is shown: only its state, like Safari's. A field in the app's colors shows the
 * page title in the middle, with the site's security icon next to it and the site's domain in small letters below it.
 * The tab's container, or the "no container" icon, sits at its start; its end is kept free for a button to come.
 * Tapping the bar starts a search, long-pressing it copies the address or pastes one, and swiping it down shows the
 * page's details, see [org.mozilla.fenix.dejavu.menu.PageInfoMenu].
 *
 * @param store The store of the address bar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DejavuTopBar(store: BrowserToolbarStore) {
    val display by store.observeAsComposableState { it.displayState }
    val origin = display.pageOrigin
    val hint = stringResource(origin.hint)
    val pageInfo = stringResource(R.string.dejavu_page_info)
    val text = origin.url?.toString()?.takeIf { it.isNotBlank() } ?: origin.title?.takeIf { it.isNotBlank() } ?: hint
    val url by components.core.store.observeAsComposableState { it.selectedTab?.content?.url }
    val haptic = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val theme = shownWorkspaceTheme()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DEJAVU_TOP_BAR_HEIGHT)
            .semantics {
                contentDescription = if (text == hint) hint else "$text. $hint"
                role = Role.Button
                // Screen readers cannot swipe the bar down, so the page's details are also an action of the bar.
                customActions = listOf(
                    CustomAccessibilityAction(pageInfo) {
                        store.dispatch(DejavuToolbar.PageInfoRequested)
                        true
                    },
                )
            }
            .combinedClickable(
                onClick = { origin.onClick?.let { store.dispatch(it) } },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuOpen = true
                },
            ),
    ) {
        AddressField(
            text = text,
            isHint = text == hint,
            url = url,
            container = containerAction(),
            pageActions = display.pageActionsStart,
            onInteraction = { store.dispatch(it) },
            modifier = Modifier.align(Alignment.Center),
        )
        display.progressBarConfig?.let { config ->
            AnimatedProgressBar(
                progress = config.progress,
                color = config.color,
                trackColor = Color.Transparent,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        CompositionLocalProvider(LocalPopupTheme provides theme) {
            LongPressMenu(
                expanded = menuOpen,
                options = origin.contextualMenuOptions,
                onPick = { option ->
                    menuOpen = false
                    store.dispatch(option.event)
                },
                onDismiss = { menuOpen = false },
            )
        }
    }
}

/**
 * How [DejavuTopBar] will show [tab] once the browser shows it, for the bar coming in while the tab opens: the same
 * field, filled from the tab instead of from the address bar's store.
 */
@Composable
internal fun DejavuTopBarPreview(tab: TabSessionState) {
    val hint = stringResource(R.string.search_hint)
    val text = DejavuToolbar.displayText(tab).takeUnless { it.isBlank() || tab.content.url == ABOUT_HOME_URL } ?: hint
    Box(modifier = Modifier.fillMaxWidth().height(DEJAVU_TOP_BAR_HEIGHT)) {
        AddressField(
            text = text,
            isHint = text == hint,
            url = tab.content.url,
            container = containerAction(tab.id),
            pageActions = listOfNotNull(siteInfoPreview(tab)),
            onInteraction = {},
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/**
 * The rounded field of the address bar: [container] at its start, then [pageActions] and [text] in the middle, with
 * the domain of [url] in small letters below the text unless the text already says it.
 */
@Composable
private fun AddressField(
    text: String,
    isHint: Boolean,
    url: String?,
    container: Action?,
    pageActions: List<Action>,
    onInteraction: (BrowserToolbarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val domain = url?.let(::domainOf)?.takeUnless { text.contains(it, ignoreCase = true) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ADDRESS_FIELD_SIDE_MARGIN)
            .height(ADDRESS_FIELD_HEIGHT)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = FIELD_PADDING),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(SLOT_SIZE)) {
            ActionContainer(actions = listOfNotNull(container), onInteraction = onInteraction)
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionContainer(actions = pageActions, onInteraction = onInteraction)
                // The bar's own description already says the title.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = TITLE_END_PADDING)
                        .clearAndSetSemantics {},
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isHint) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    domain?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        // Kept free for a button to come, so the title stays in the middle.
        Spacer(Modifier.size(SLOT_SIZE))
    }
}

/**
 * The site's security icon for [tab], as the address bar's store shows it once the page is shown; none in Reader
 * View.
 */
private fun siteInfoPreview(tab: TabSessionState): Action? {
    if (tab.readerState.active) return null
    val security = tab.content.securityInfo
    val protection = tab.trackingProtection
    val icon = when {
        tab.content.url.isContentUrl() -> iconsR.drawable.mozac_ic_page_portrait_24
        security == SecurityInfo.Unknown -> iconsR.drawable.mozac_ic_globe_24
        !security.isSecure -> iconsR.drawable.mozac_ic_shield_slash_24
        protection.enabled && !protection.ignoredOnTrackingProtection -> iconsR.drawable.mozac_ic_shield_checkmark_24
        else -> iconsR.drawable.mozac_ic_shield_cross_24
    }
    return Action.ActionButtonRes(
        drawableResId = icon,
        contentDescription = browserToolbarR.string.mozac_browser_toolbar_content_description_site_info,
        highlighted = tab.content.permissionHighlights.permissionsChanged || protection.ignoredOnTrackingProtection,
        onClick = object : BrowserToolbarEvent {},
    )
}

@Composable
private fun LongPressMenu(
    expanded: Boolean,
    options: List<ContextualMenuOption>,
    onPick: (ContextualMenuOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = remember(context) { ClipboardHandler(context) }
    val shown = remember(expanded, options) {
        if (!expanded) return@remember emptyList()
        options.filter { option ->
            when (option) {
                ContextualMenuOption.CopyURLToClipboard -> true
                ContextualMenuOption.PasteFromClipboard -> clipboard.containsText()
                ContextualMenuOption.LoadFromClipboard -> clipboard.containsURL()
            }
        }
    }
    DejavuDropdownMenu(expanded = expanded && shown.isNotEmpty(), onDismissRequest = onDismiss) {
        shown.forEach { option ->
            DejavuMenuItem(text = stringResource(option.label), onClick = { onPick(option) })
        }
    }
}

private val ContextualMenuOption.label: Int
    get() = when (this) {
        ContextualMenuOption.CopyURLToClipboard -> toolbarR.string.mozac_browser_toolbar_long_press_popup_copy
        ContextualMenuOption.PasteFromClipboard -> toolbarR.string.mozac_browser_toolbar_long_press_popup_paste
        ContextualMenuOption.LoadFromClipboard -> toolbarR.string.mozac_browser_toolbar_long_press_popup_paste_and_go
    }

/**
 * The button of the container of the tab with [tabId], the shown tab when `null`, or of "no container", which opens
 * the menu to move the tab to another.
 */
@Composable
private fun containerAction(tabId: String? = null): Action? {
    val context = LocalContext.current
    val browserStore = components.core.store
    val shown by browserStore.observeAsComposableState { state ->
        state.tabOrShown(tabId)?.let { tab ->
            Triple(tab.contextId, state.containers[tab.contextId], tab.content.private)
        }
    }
    val records by remember { DejavuContainerStorage.get(context).records }.collectAsState()
    return remember(shown, records) {
        DejavuToolbar.containerAction(context, browserStore.state, browserStore.state.tabOrShown(tabId))
    }
}

/** The domain of [url], like "google.com" for "https://www.google.com/search", or `null` for one without a host. */
internal fun domainOf(url: String): String? = url.toUri().host?.removePrefix("www.")?.takeIf { it.isNotBlank() }

/** The height of Dejavu's address bar while a page is shown, below the status bar. */
internal val DEJAVU_TOP_BAR_HEIGHT = 64.dp

/** The size and place of the rounded field of the address bar, in the middle of the bar. */
internal val ADDRESS_FIELD_HEIGHT = 48.dp
internal val ADDRESS_FIELD_SIDE_MARGIN = 16.dp
internal val ADDRESS_FIELD_TOP_MARGIN = (DEJAVU_TOP_BAR_HEIGHT - ADDRESS_FIELD_HEIGHT) / 2
private val FIELD_PADDING = 4.dp
private val SLOT_SIZE = 48.dp
private val TITLE_END_PADDING = 8.dp
