/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mozilla.components.compose.base.progressbar.AnimatedProgressBar
import mozilla.components.compose.browser.toolbar.ActionContainer
import mozilla.components.compose.browser.toolbar.R as toolbarR
import mozilla.components.compose.browser.toolbar.concept.PageOrigin.Companion.ContextualMenuOption
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarStore
import mozilla.components.compose.browser.toolbar.ui.AddressDisplayText
import mozilla.components.compose.browser.toolbar.ui.AddressFieldDefaults
import mozilla.components.compose.browser.toolbar.ui.addressFieldSurface
import mozilla.components.compose.browser.toolbar.utils.addressParts
import mozilla.components.lib.state.ext.observeAsComposableState
import mozilla.components.support.utils.ClipboardHandler
import org.mozilla.fenix.dejavu.ui.DejavuDropdownMenu
import org.mozilla.fenix.dejavu.ui.DejavuMenuItem
import org.mozilla.fenix.dejavu.ui.LocalPopupTheme
import org.mozilla.fenix.ext.components

/**
 * Dejavu's address bar while a page is shown, over the colors of the tab's workspace. The rounded field shows the icons
 * of the tab's container and of the site's security, the page title or its address (see [AddressDisplayText]), and the
 * page actions such as reader view. Tapping the bar starts a search, and long-pressing it copies the address or pastes
 * one. The look is shared with the other address bars, see `docs/dejavu/address-bar.md`.
 *
 * @param store The store of the address bar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DejavuTopBar(store: BrowserToolbarStore) {
    // Narrow slices, so the progress ticks of a loading page only redraw the progress bar.
    val origin by store.observeAsComposableState { it.displayState.pageOrigin }
    val pageActionsStart by store.observeAsComposableState { it.displayState.pageActionsStart }
    val pageActionsEnd by store.observeAsComposableState { it.displayState.pageActionsEnd }
    val hint = stringResource(origin.hint)
    val parts = remember(origin.title, origin.url) { addressParts(origin.title, origin.url?.toString()) }
    val haptic = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val theme = shownWorkspaceTheme()
    val settings = LocalContext.current.components.settings
    fun run(key: String) {
        if (key == "address_menu") menuOpen = true else store.dispatch(DejavuToolbar.GestureTriggered(key))
    }
    // A toolbar gesture seen outside this bar, see runToolbarGesture.
    LaunchedEffect(DejavuAddressMenu.requested) {
        if (DejavuAddressMenu.requested) {
            DejavuAddressMenu.requested = false
            menuOpen = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .background(MaterialTheme.colorScheme.surface)
            .workspaceTheme(theme)
            .semantics {
                contentDescription = parts?.let { "${it.spoken}. $hint" } ?: hint
                role = Role.Button
            }
            .combinedClickable(
                onClick = { origin.onClick?.let { store.dispatch(it) } },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    run(settings.toolbarLongPressGesture)
                },
                onDoubleClick = settings.toolbarDoubleTapGesture.takeIf { it != "none" }?.let { key -> { run(key) } },
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = SIDE_PADDING)
                .height(AddressFieldDefaults.Height)
                .addressFieldSurface(AddressFieldDefaults.colors()),
        ) {
            ActionContainer(actions = pageActionsStart, onInteraction = { store.dispatch(it) })
            AddressDisplayText(
                parts = parts,
                hint = hint,
                // The bar's own description already says it.
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (pageActionsStart.isEmpty()) AddressFieldDefaults.HorizontalPadding else 0.dp,
                        end = if (pageActionsEnd.isEmpty()) AddressFieldDefaults.HorizontalPadding else 0.dp,
                    )
                    .clearAndSetSemantics {},
            )
            ActionContainer(actions = pageActionsEnd, onInteraction = { store.dispatch(it) })
        }
        ProgressBar(store, Modifier.align(Alignment.BottomCenter))
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

@Composable
private fun ProgressBar(store: BrowserToolbarStore, modifier: Modifier) {
    val config by store.observeAsComposableState { it.displayState.progressBarConfig }
    config?.let {
        AnimatedProgressBar(
            progress = it.progress,
            color = it.color,
            trackColor = Color.Transparent,
            modifier = modifier,
        )
    }
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

private val BAR_HEIGHT = 64.dp
private val SIDE_PADDING = 16.dp
