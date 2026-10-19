/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.browser.tabstrip

import android.graphics.PointF
import android.graphics.Rect
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import mozilla.components.browser.state.selector.getNormalOrPrivateTabs
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.compose.base.button.IconButton
import mozilla.components.compose.base.textfield.TextField
import mozilla.components.compose.base.theme.layout.AcornWindowSize
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.R
import org.mozilla.fenix.browser.SwipeGestureListener
import org.mozilla.fenix.components.components
import org.mozilla.fenix.ext.getRectWithScreenLocation
import org.mozilla.fenix.theme.FirefoxTheme
import org.mozilla.fenix.utils.Settings

private val collapsedWidth = 56.dp
private val expandedWidth = 256.dp
private val tabHeight = 40.dp
private const val EDGE_SWIPE_WIDTH_DP = 20

/**
 * Zen style vertical tabs: the tabs of the selected tab's mode (normal or private) in a sidebar, collapsible to an
 * icon rail. Shows nothing on small windows, so the page keeps the full width there.
 *
 * @param settings The settings that remember whether the sidebar is collapsed.
 * @param onAddTabClick Invoked when the new tab button is clicked.
 * @param onCloseTabClick Invoked after a tab is closed.
 * @param onLastTabClose Invoked when the last remaining open tab is closed.
 * @param overlayShown Whether the auto-hidden sidebar is shown over the page, or null when the sidebar sits next to the
 *   page.
 * @param browserStore The [BrowserStore] the tabs are observed from.
 * @param tabsUseCases The [TabsUseCases] used to select and close tabs.
 */
@Composable
fun TabSidebar(
    settings: Settings,
    onAddTabClick: () -> Unit,
    onCloseTabClick: (isPrivate: Boolean) -> Unit,
    onLastTabClose: (isPrivate: Boolean) -> Unit,
    overlayShown: MutableState<Boolean>? = null,
    browserStore: BrowserStore = components.core.store,
    tabsUseCases: TabsUseCases = components.useCases.tabsUseCases,
) {
    if (!AcornWindowSize.isLargeWindow()) return

    val tabs by remember(browserStore) {
        browserStore.stateFlow.map { it.toSidebarTabs() }.distinctUntilChanged()
    }
        .collectAsState(initial = browserStore.state.toSidebarTabs())
    var collapsed by remember { mutableStateOf(settings.isVerticalTabsCollapsed) }
    var query by remember { mutableStateOf("") }
    val colors = TabStripColors.default()
    val isCollapsed = collapsed && overlayShown == null
    val listedTabs = if (isCollapsed) {
        tabs
    } else {
        tabs.filter { it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true) }
    }
    val isShown = overlayShown?.value ?: true
    val hide: () -> Unit = { overlayShown?.value = false }

    Box(modifier = if (overlayShown != null && isShown) Modifier.fillMaxSize() else Modifier) {
        if (overlayShown != null && isShown) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .pointerInput(Unit) { detectTapGestures { hide() } },
            )
        }

        AnimatedVisibility(
            visible = isShown,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
        ) {
            Column(
                modifier = Modifier
                    .width(if (isCollapsed) collapsedWidth else expandedWidth)
                    .fillMaxHeight()
                    .background(brush = colors.backgroundBrush)
                    .pointerInput(overlayShown) {
                        if (overlayShown != null) {
                            detectHorizontalDragGestures { _, dragAmount -> if (dragAmount < 0) hide() }
                        }
                    },
                horizontalAlignment = if (isCollapsed) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                IconButton(
                    onClick = {
                        if (overlayShown != null) {
                            hide()
                        } else {
                            collapsed = !collapsed
                            settings.isVerticalTabsCollapsed = collapsed
                        }
                    },
                    contentDescription = stringResource(R.string.vertical_tabs_toggle),
                ) {
                    val icon = if (isCollapsed) {
                        iconsR.drawable.mozac_ic_chevron_right_24
                    } else {
                        iconsR.drawable.mozac_ic_chevron_left_24
                    }
                    Icon(painter = painterResource(icon), contentDescription = null)
                }

                if (!isCollapsed) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(R.string.vertical_tabs_search),
                        errorText = "",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        trailingIcon = { if (query.isNotEmpty()) CrossTextFieldButton { query = "" } },
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(listedTabs, key = { it.id }) { tab ->
                        SidebarTab(
                            tab = tab,
                            collapsed = isCollapsed,
                            backgroundColor = colors.tabItemBackgroundColors.get(tab.isSelected),
                            onClick = {
                                tabsUseCases.selectTab(tab.id)
                                hide()
                            },
                            onCloseClick = {
                                if (tabs.size == 1) onLastTabClose(tab.isPrivate)
                                tabsUseCases.removeTab(tab.id)
                                onCloseTabClick(tab.isPrivate)
                            },
                        )
                    }
                }

                IconButton(
                    onClick = {
                        onAddTabClick()
                        hide()
                    },
                    contentDescription = stringResource(R.string.add_tab),
                ) {
                    Icon(painter = painterResource(iconsR.drawable.mozac_ic_plus_24), contentDescription = null)
                }
            }
        }
    }
}

/**
 * Shows the auto-hidden sidebar when a swipe to the right starts within [EDGE_SWIPE_WIDTH_DP] of the left edge of
 * [layout], so swipes that start anywhere else still reach the page and the toolbar.
 *
 * @param layout The view the swipe is detected in.
 * @param overlayShown Whether the sidebar is shown over the page.
 */
internal class TabSidebarEdgeSwipe(
    private val layout: View,
    private val overlayShown: MutableState<Boolean>,
) : SwipeGestureListener {
    private val edgeWidth = (EDGE_SWIPE_WIDTH_DP * layout.resources.displayMetrics.density).toInt()

    init {
        // Keeps the system back gesture from taking swipes that start in the edge strip. Android honors at most
        // 200dp of exclusion height per edge.
        layout.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            ViewCompat.setSystemGestureExclusionRects(view, listOf(Rect(0, 0, edgeWidth, view.height)))
        }
    }

    override fun onSwipeStarted(start: PointF, next: PointF): Boolean {
        val isFromEdge = start.x - layout.getRectWithScreenLocation().left < edgeWidth
        val isRightward = next.x - start.x > abs(next.y - start.y)
        if (overlayShown.value || !isFromEdge || !isRightward || !AcornWindowSize.isLargeWindow(layout.context)) {
            return false
        }
        overlayShown.value = true
        return true
    }

    override fun onSwipeUpdate(distanceX: Float, distanceY: Float) = Unit

    override fun onSwipeFinished(velocityX: Float, velocityY: Float) = Unit
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
