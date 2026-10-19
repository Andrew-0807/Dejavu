/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.browser.tabstrip

import android.graphics.PointF
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlin.math.abs
import mozilla.components.browser.state.selector.getNormalOrPrivateTabs
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.compose.base.textfield.TextField
import mozilla.components.compose.base.theme.layout.AcornWindowSize
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.lib.state.ext.observeAsComposableState
import org.mozilla.fenix.R
import org.mozilla.fenix.browser.SwipeGestureListener
import org.mozilla.fenix.components.components
import org.mozilla.fenix.dejavu.home.DejavuHomeScreen
import org.mozilla.fenix.ext.getRectWithScreenLocation
import org.mozilla.fenix.theme.FirefoxTheme
import org.mozilla.fenix.utils.Settings

private val sidebarWidth = 320.dp
private const val EDGE_SWIPE_WIDTH_DP = 48

/** Whether the home screen shows in a sidebar next to pages, which then need no Home button. */
@Composable
fun isHomeSidebarShown(settings: Settings = components.settings): Boolean =
    settings.isVerticalTabsEnabled && AcornWindowSize.isLargeWindow()

/**
 * The Dejavu home screen in a sidebar next to the page. Shows nothing on small windows, so the page keeps the full
 * width there.
 *
 * @param navController Used by the home screen to open settings, bookmarks and the like.
 * @param onNoTabs Invoked when the last tab is closed.
 * @param overlayShown Whether the auto-hidden sidebar is shown over the page, or null when the sidebar sits next to the
 *   page.
 * @param browserStore The [BrowserStore] the tabs are observed from.
 * @param tabsUseCases The [TabsUseCases] used to select tabs.
 */
@Composable
fun TabSidebar(
    navController: NavController,
    onNoTabs: () -> Unit,
    overlayShown: MutableState<Boolean>? = null,
    browserStore: BrowserStore = components.core.store,
    tabsUseCases: TabsUseCases = components.useCases.tabsUseCases,
) {
    if (!AcornWindowSize.isLargeWindow()) return

    val selectedTabId by browserStore.observeAsComposableState { it.selectedTabId }
    LaunchedEffect(selectedTabId) {
        if (selectedTabId == null && browserStore.state.restoreComplete) onNoTabs()
    }
    var query by remember { mutableStateOf("") }
    val tabs by browserStore.observeAsComposableState { state ->
        state.getNormalOrPrivateTabs(private = state.selectedTab?.content?.private == true)
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
            val onOpenTab: (String) -> Unit = { tabId ->
                tabsUseCases.selectTab(tabId)
                hide()
            }
            Column(modifier = Modifier.width(sidebarWidth).fillMaxHeight()) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.vertical_tabs_search),
                    errorText = "",
                    modifier = Modifier.padding(horizontal = 8.dp),
                    trailingIcon = { if (query.isNotEmpty()) CrossTextFieldButton { query = "" } },
                )
                if (query.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        DejavuHomeScreen(navController = navController, onOpenTab = onOpenTab, isSidebar = true)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(
                            tabs.orEmpty().filter {
                                it.content.title.contains(query, ignoreCase = true) ||
                                    it.content.url.contains(query, ignoreCase = true)
                            },
                            key = { it.id },
                        ) { tab ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenTab(tab.id) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TabStripIcon(url = tab.content.url, icon = tab.content.icon)
                                Text(
                                    text = tab.content.title.ifBlank { tab.content.url },
                                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = FirefoxTheme.typography.body2,
                                )
                            }
                        }
                    }
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
