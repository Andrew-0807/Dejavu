/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.R
import org.mozilla.fenix.compose.list.IconListItem

/**
 * A category of the settings home. [key] is a top level category of the root preferences, which holds the settings of
 * the category. Categories with the same [card] share one rounded card.
 */
enum class SettingsGroup(
    @param:StringRes val key: Int,
    @param:StringRes val title: Int,
    @param:StringRes val summary: Int,
    @param:DrawableRes val icon: Int,
    val card: Int,
) {
    ACCOUNT(
        R.string.pref_key_dejavu_group_account,
        R.string.dejavu_settings_group_account,
        R.string.dejavu_settings_group_account_summary,
        iconsR.drawable.mozac_ic_avatar_circle_24,
        0,
    ),
    LOOK(
        R.string.pref_key_dejavu_group_look,
        R.string.dejavu_settings_group_look,
        R.string.dejavu_settings_group_look_summary,
        iconsR.drawable.mozac_ic_themes_24,
        1,
    ),
    TABS(
        R.string.pref_key_dejavu_group_tabs,
        R.string.dejavu_settings_group_tabs,
        R.string.dejavu_settings_group_tabs_summary,
        iconsR.drawable.mozac_ic_tab_24,
        1,
    ),
    INPUT(
        R.string.pref_key_dejavu_group_input,
        R.string.dejavu_settings_group_input,
        R.string.dejavu_settings_group_input_summary,
        iconsR.drawable.mozac_ic_cursor_arrow_24,
        1,
    ),
    SEARCH(
        R.string.pref_key_dejavu_group_search,
        R.string.dejavu_settings_group_search,
        R.string.dejavu_settings_group_search_summary,
        iconsR.drawable.mozac_ic_search_24,
        1,
    ),
    PRIVACY(
        R.string.pref_key_privacy_security_category,
        R.string.preferences_category_privacy_security,
        R.string.dejavu_settings_group_privacy_summary,
        iconsR.drawable.mozac_ic_shield_24,
        2,
    ),
    DOWNLOADS(
        R.string.pref_key_dejavu_group_downloads,
        R.string.dejavu_settings_group_downloads,
        R.string.dejavu_settings_group_downloads_summary,
        iconsR.drawable.mozac_ic_download_24,
        2,
    ),
    ADVANCED(
        R.string.pref_key_advanced,
        R.string.preferences_category_advanced,
        R.string.dejavu_settings_group_advanced_summary,
        iconsR.drawable.mozac_ic_tool_24,
        2,
    ),
    ABOUT(
        R.string.pref_key_dejavu_group_about,
        R.string.dejavu_settings_group_about,
        R.string.dejavu_settings_group_about_summary,
        iconsR.drawable.mozac_ic_information_24,
        3,
    ),
}

/**
 * The settings home: a search field that opens the settings search, then every [SettingsGroup] as rounded cards.
 * [selected] is highlighted, for the two pane layout of wide windows, where it follows the scrolling of the settings.
 */
@Composable
fun SettingsHome(selected: SettingsGroup?, onSearch: (() -> Unit)?, onSelect: (SettingsGroup) -> Unit) {
    val cards = SettingsGroup.entries.groupBy { it.card }.values.toList()
    val listState = rememberLazyListState()
    // Keeps the highlighted category in view when the scrolling of the settings moves it. The entries are declared in
    // card order, so the item index of a category is its ordinal after the search field.
    LaunchedEffect(selected) {
        val index = (selected ?: return@LaunchedEffect).ordinal + if (onSearch != null) 1 else 0
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
        val fullyVisible = item != null && item.offset >= layout.viewportStartOffset &&
            item.offset + item.size <= layout.viewportEndOffset - layout.afterContentPadding
        if (!fullyVisible) listState.animateScrollToItem(index)
    }
    // The bottom padding lets the last category scroll clear of the screen edge.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        onSearch?.let { search -> item { SearchField(search) } }
        cards.forEach { groups ->
            itemsIndexed(groups) { index, group ->
                val highlight = if (group == selected) {
                    Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    Modifier
                }
                IconListItem(
                    label = stringResource(group.title),
                    description = stringResource(group.summary),
                    // Summaries wrap instead of being cut, and every row is tall enough for two lines so the rows
                    // keep one height.
                    maxDescriptionLines = Int.MAX_VALUE,
                    minHeight = 88.dp,
                    beforeIconPainter = painterResource(group.icon),
                    modifier = Modifier.settingsCard(index, groups.size).then(highlight),
                    onClick = { onSelect(group) },
                )
            }
        }
    }
}

@Composable
private fun SearchField(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconsR.drawable.mozac_ic_search_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.settings_search_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
