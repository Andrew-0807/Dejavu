/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.browser.tabstrip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import mozilla.components.browser.state.selector.getNormalOrPrivateTabs
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.compose.base.button.IconButton
import mozilla.components.compose.base.theme.layout.AcornWindowSize
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.R
import org.mozilla.fenix.components.components
import org.mozilla.fenix.theme.FirefoxTheme
import org.mozilla.fenix.utils.Settings

private val collapsedWidth = 56.dp
private val expandedWidth = 256.dp
private val tabHeight = 40.dp

/**
 * Zen style vertical tabs: the tabs of the selected tab's mode (normal or private) in a sidebar, collapsible to an
 * icon rail. Shows nothing on small windows, so the page keeps the full width there.
 *
 * @param settings The settings that remember whether the sidebar is collapsed.
 * @param onAddTabClick Invoked when the new tab button is clicked.
 * @param onCloseTabClick Invoked after a tab is closed.
 * @param onLastTabClose Invoked when the last remaining open tab is closed.
 * @param browserStore The [BrowserStore] the tabs are observed from.
 * @param tabsUseCases The [TabsUseCases] used to select and close tabs.
 */
@Composable
fun TabSidebar(
    settings: Settings,
    onAddTabClick: () -> Unit,
    onCloseTabClick: (isPrivate: Boolean) -> Unit,
    onLastTabClose: (isPrivate: Boolean) -> Unit,
    browserStore: BrowserStore = components.core.store,
    tabsUseCases: TabsUseCases = components.useCases.tabsUseCases,
) {
    if (!AcornWindowSize.isLargeWindow()) return

    val tabs by remember(browserStore) {
        browserStore.stateFlow.map { it.toSidebarTabs() }.distinctUntilChanged()
    }
        .collectAsState(initial = browserStore.state.toSidebarTabs())
    var collapsed by remember { mutableStateOf(settings.isVerticalTabsCollapsed) }
    val colors = TabStripColors.default()

    Column(
        modifier = Modifier
            .width(if (collapsed) collapsedWidth else expandedWidth)
            .fillMaxHeight()
            .background(brush = colors.backgroundBrush),
        horizontalAlignment = if (collapsed) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        IconButton(
            onClick = {
                collapsed = !collapsed
                settings.isVerticalTabsCollapsed = collapsed
            },
            contentDescription = stringResource(R.string.vertical_tabs_toggle),
        ) {
            val icon = if (collapsed) iconsR.drawable.mozac_ic_chevron_right_24 else iconsR.drawable.mozac_ic_chevron_left_24
            Icon(painter = painterResource(icon), contentDescription = null)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(tabs, key = { it.id }) { tab ->
                SidebarTab(
                    tab = tab,
                    collapsed = collapsed,
                    backgroundColor = colors.tabItemBackgroundColors.get(tab.isSelected),
                    onClick = { tabsUseCases.selectTab(tab.id) },
                    onCloseClick = {
                        if (tabs.size == 1) onLastTabClose(tab.isPrivate)
                        tabsUseCases.removeTab(tab.id)
                        onCloseTabClick(tab.isPrivate)
                    },
                )
            }
        }

        IconButton(onClick = onAddTabClick, contentDescription = stringResource(R.string.add_tab)) {
            Icon(painter = painterResource(iconsR.drawable.mozac_ic_plus_24), contentDescription = null)
        }
    }
}

@Composable
private fun SidebarTab(
    tab: TabStripItem,
    collapsed: Boolean,
    backgroundColor: Color,
    onClick: () -> Unit,
    onCloseClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(tabHeight)
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Tab
                selected = tab.isSelected
                if (collapsed) contentDescription = tab.title
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start,
    ) {
        if (!collapsed) Spacer(modifier = Modifier.width(8.dp))

        TabStripIcon(url = tab.url, icon = tab.icon)

        if (!collapsed) {
            Text(
                text = tab.title,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = FirefoxTheme.typography.body2,
            )
            IconButton(
                onClick = onCloseClick,
                contentDescription = stringResource(R.string.close_tab_title, tab.title),
            ) {
                Icon(painter = painterResource(iconsR.drawable.mozac_ic_cross_20), contentDescription = null)
            }
        }
    }
}

private fun BrowserState.toSidebarTabs(): List<TabStripItem> =
    getNormalOrPrivateTabs(private = selectedTab?.content?.private == true).map {
        TabStripItem(
            id = it.id,
            title = it.content.title.ifBlank { it.content.url },
            url = it.content.url,
            icon = it.content.icon,
            isPrivate = it.content.private,
            isSelected = it.id == selectedTabId,
        )
    }
