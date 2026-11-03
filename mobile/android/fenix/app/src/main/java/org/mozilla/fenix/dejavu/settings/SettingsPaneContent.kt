/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.R
import org.mozilla.fenix.dejavu.menu.BORDER_ALPHA
import org.mozilla.fenix.settings.settingssearch.SettingsSearchItem

private val SearchShape = RoundedCornerShape(28.dp)

/**
 * The list of categories beside the settings: a search field on top and the categories below it, or the settings the
 * search finds while something is typed.
 *
 * @param selected The category whose settings are shown.
 * @param search Finds the settings for what was typed, for as long as it is typed.
 * @param categoryOf The category a found setting belongs to.
 * @param onSelect Called with the category that was tapped.
 * @param onOpen Called with the found setting that was tapped.
 */
@Composable
internal fun SettingsPaneContent(
    selected: SettingsCategory,
    search: suspend (String) -> List<SettingsSearchItem>,
    categoryOf: (SettingsSearchItem) -> SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    onOpen: (SettingsSearchItem) -> Unit,
) {
    val hairline = MaterialTheme.colorScheme.onSurface.copy(alpha = BORDER_ALPHA)
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<SettingsSearchItem>()) }
    LaunchedEffect(query) { results = if (query.isBlank()) emptyList() else search(query) }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        SearchField(query, onChange = { query = it })
        if (query.isBlank()) {
            LazyColumn(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(SettingsCategory.entries) { category ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(category.title)) },
                        icon = { Icon(painterResource(category.icon), contentDescription = null) },
                        selected = category == selected,
                        onClick = { onSelect(category) },
                        // See-through like the cards beside it, over the backdrop of the page.
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                .copy(alpha = SETTINGS_CARD_ALPHA),
                            unselectedContainerColor = Color.Transparent,
                        ),
                        modifier = if (category == selected) Modifier.border(1.dp, hairline, SearchShape) else Modifier,
                    )
                }
            }
        } else if (results.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_search_no_results_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn {
                items(results, key = { it.preferenceKey }) { item ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(item) }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    ) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = stringResource(categoryOf(item).title),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fill = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = SETTINGS_CARD_ALPHA)
    TextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        shape = SearchShape,
        placeholder = { Text(stringResource(R.string.settings_search_title)) },
        leadingIcon = { Icon(painterResource(iconsR.drawable.mozac_ic_search_24), contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(
                        painterResource(iconsR.drawable.mozac_ic_cross_circle_fill_24),
                        contentDescription = stringResource(R.string.content_description_settings_search_clear_search),
                    )
                }
            }
        },
        // Drawn like the sidebar's search field: a see-through pill with a hairline that turns into the accent ring.
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .onFocusChanged { focused = it.hasFocus }
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = BORDER_ALPHA),
                shape = SearchShape,
            ),
    )
}
