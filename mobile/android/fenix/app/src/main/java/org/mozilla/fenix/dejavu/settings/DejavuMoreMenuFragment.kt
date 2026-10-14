/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.R
import org.mozilla.fenix.dejavu.menu.ActionsBarLayout
import org.mozilla.fenix.dejavu.menu.MoreMenuEntry
import org.mozilla.fenix.dejavu.ui.DejavuDialog
import org.mozilla.fenix.dejavu.ui.DejavuDialogButton
import org.mozilla.fenix.dejavu.ui.DejavuDropdownMenu
import org.mozilla.fenix.dejavu.ui.DejavuMenuItem

/**
 * Arranges the buttons of the browser's "More" menu in rows, and the buttons of the actions bar below pages, each on a
 * tab of its own. Both pick from the menu's own items, every tab action and the custom actions.
 */
class DejavuMoreMenuFragment : DejavuComposeFragment(R.string.dejavu_settings_more_menu) {
    @Composable
    override fun DejavuScreen() {
        val settings = remember { dejavuSettings() }
        var page by rememberSaveable { mutableIntStateOf(MENU_PAGE) }
        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = page) {
                Tab(
                    selected = page == MENU_PAGE,
                    onClick = { page = MENU_PAGE },
                    text = { Text(stringResource(R.string.dejavu_settings_more_menu)) },
                )
                Tab(
                    selected = page == BAR_PAGE,
                    onClick = { page = BAR_PAGE },
                    text = { Text(stringResource(R.string.dejavu_settings_actions_bar)) },
                )
            }
            if (page == MENU_PAGE) {
                MoreMenuRowsEditor(settings = settings, modifier = Modifier.weight(1f))
            } else {
                ActionsBarSettings(settings = settings, modifier = Modifier.weight(1f))
            }
        }
    }

    private companion object {
        const val MENU_PAGE = 0
        const val BAR_PAGE = 1
    }
}

/** The buttons of the actions bar: a preview, and the buttons to move, remove, add to or reset. */
@Composable
private fun ActionsBarSettings(settings: DejavuSettings, modifier: Modifier = Modifier) {
    val barKeys by settings.actionsBarKeys.collectAsState()
    val customActions by settings.customActions.collectAsState()
    val bar = remember(barKeys, customActions) { ActionsBarLayout.resolve(barKeys, customActions) }
    var adding by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(
            text = stringResource(R.string.dejavu_actions_bar_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        ActionsBarPreview(bar)
        ActionsBarEditor(
            entries = bar,
            onChange = { keys -> settings.setActionsBar(keys) },
            onAdd = { adding = true },
            onReset = settings::resetActionsBar,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (adding) {
        EntryPicker(
            entries = ActionsBarLayout.available(customActions).filter { it.key !in barKeys },
            allAdded = R.string.dejavu_actions_bar_all_added,
            onPick = { entry ->
                settings.addToActionsBar(entry.key)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun EntryChip(
    entry: MoreMenuEntry,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onMoveToRow: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable { menuOpen = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Icon(
                painter = painterResource(entry.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = entry.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DejavuDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            val close = { menuOpen = false }
            ChipOption(R.string.dejavu_more_menu_move_left, canMoveLeft, close) { onMove(-1) }
            ChipOption(R.string.dejavu_more_menu_move_right, canMoveRight, close) { onMove(1) }
            ChipOption(R.string.dejavu_more_menu_move_up, canMoveUp, close) { onMoveToRow(-1) }
            ChipOption(R.string.dejavu_more_menu_move_down, canMoveDown, close) { onMoveToRow(1) }
            ChipOption(R.string.dejavu_more_menu_remove, enabled = true, close = close, onClick = onRemove)
        }
    }
}

@Composable
private fun ChipOption(@StringRes label: Int, enabled: Boolean, close: () -> Unit, onClick: () -> Unit) {
    DejavuMenuItem(
        text = stringResource(label),
        enabled = enabled,
        onClick = {
            close()
            onClick()
        },
    )
}

@Composable
private fun EntryPicker(
    entries: List<MoreMenuEntry>,
    @StringRes allAdded: Int,
    onPick: (MoreMenuEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    DejavuDialog(
        title = stringResource(R.string.dejavu_more_menu_add_item),
        onDismissRequest = onDismiss,
        buttons = { DejavuDialogButton(text = stringResource(R.string.dejavu_cancel), onClick = onDismiss) },
    ) {
        if (entries.isEmpty()) {
            Text(stringResource(allAdded))
        }
        entries.forEach { entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick(entry) }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                Icon(
                    painter = painterResource(entry.icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

internal val MoreMenuEntry.icon: Int
    get() = when (this) {
        is MoreMenuEntry.BuiltIn -> item.icon
        is MoreMenuEntry.Tab -> action.icon
        is MoreMenuEntry.Custom -> iconsR.drawable.mozac_ic_lightning_24
        MoreMenuEntry.Home -> iconsR.drawable.mozac_ic_home_24
        MoreMenuEntry.Search -> iconsR.drawable.mozac_ic_search_24
    }

internal val MoreMenuEntry.label: String
    @Composable get() = when (this) {
        is MoreMenuEntry.BuiltIn -> stringResource(item.label)
        is MoreMenuEntry.Tab -> stringResource(action.label)
        is MoreMenuEntry.Custom -> action.name
        MoreMenuEntry.Home -> stringResource(R.string.dejavu_actions_bar_home)
        MoreMenuEntry.Search -> stringResource(R.string.dejavu_actions_bar_search)
    }

/** How the actions bar looks with [entries], as icons in a row. */
@Composable
private fun ActionsBarPreview(entries: List<MoreMenuEntry>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 8.dp),
    ) {
        entries.forEach { entry ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
                Icon(
                    painter = painterResource(entry.icon),
                    contentDescription = entry.label,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** The buttons of the actions bar, to move left or right, remove, add to or reset. */
@Composable
private fun ActionsBarEditor(
    entries: List<MoreMenuEntry>,
    onChange: (List<String>) -> Unit,
    onAdd: () -> Unit,
    onReset: () -> Unit,
) {
    val keys = entries.map { it.key }
    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in keys.indices) return
        onChange(keys.toMutableList().apply { add(target, removeAt(index)) })
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            EntryChip(
                entry = entry,
                canMoveLeft = index > 0,
                canMoveRight = index < entries.size - 1,
                canMoveUp = false,
                canMoveDown = false,
                onMove = { delta -> move(index, delta) },
                onMoveToRow = {},
                onRemove = { onChange(keys - entry.key) },
            )
        }
        if (entries.size < ActionsBarLayout.MAX_BUTTONS) AddChip(onClick = onAdd)
    }
    TextButton(onClick = onReset, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(stringResource(R.string.dejavu_more_menu_reset))
    }
}

@Composable
private fun AddChip(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(
            painter = painterResource(iconsR.drawable.mozac_ic_plus_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.dejavu_more_menu_add_item),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
