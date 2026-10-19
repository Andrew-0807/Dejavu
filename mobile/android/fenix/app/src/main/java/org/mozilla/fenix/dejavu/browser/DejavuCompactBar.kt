/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarInteraction.BrowserToolbarEvent.Source
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarStore
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.R
import org.mozilla.fenix.components.toolbar.DisplayActions

/**
 * The compact toolbar: one bar at the bottom of the screen with the address bar and a Home button beside it. Swiping it
 * up shows [buttons] above it, swiping up again opens the "More" menu, and swiping down hides the buttons.
 *
 * @param store The store of the address bar.
 * @param buttons The bar with the other toolbar buttons, shown only while the compact toolbar is swiped up.
 * @param onSwipeDownWhenCollapsed Called on a swipe down while [buttons] are hidden. Nothing happens when `null`.
 */
@Composable
fun DejavuCompactBar(
    store: BrowserToolbarStore,
    buttons: @Composable () -> Unit,
    onSwipeDownWhenCollapsed: (() -> Unit)? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val onSwipeDown by rememberUpdatedState(onSwipeDownWhenCollapsed)
    val toggleLabel = stringResource(
        if (expanded) R.string.compact_toolbar_hide_buttons else R.string.compact_toolbar_show_buttons,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                var distance = 0f
                detectVerticalDragGestures(
                    onDragStart = { distance = 0f },
                    onDragEnd = {
                        when {
                            distance < 0f && !expanded -> expanded = true
                            distance < 0f -> store.dispatch(DejavuToolbar.MenuRequested)
                            expanded -> expanded = false
                            // Hook for the integrator: a swipe down on the collapsed bar, e.g. to go immersive.
                            else -> onSwipeDown?.invoke()
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        distance += dragAmount
                    },
                )
            }
            .semantics {
                // Screen readers cannot swipe the bar, so it offers showing and hiding the buttons as an action.
                customActions = listOf(
                    CustomAccessibilityAction(toggleLabel) {
                        expanded = !expanded
                        true
                    },
                )
            },
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Bottom),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            buttons()
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .workspaceTheme(shownWorkspaceTheme()),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                DejavuTopBar(store)
            }
            val homeLabel = stringResource(R.string.dejavu_actions_bar_home)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) {
                        store.dispatch(DisplayActions.HomepageClicked(Source.AddressBar.BrowserEnd))
                    },
            ) {
                Icon(
                    painter = painterResource(iconsR.drawable.mozac_ic_home_24),
                    contentDescription = homeLabel,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
