/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.browser.tabstrip

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import mozilla.components.browser.state.selector.getNormalOrPrivateTabs
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.compose.base.textfield.TextField
import mozilla.components.compose.base.theme.layout.AcornWindowSize
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.lib.state.ext.observeAsComposableState
import org.mozilla.fenix.R
import org.mozilla.fenix.components.components
import org.mozilla.fenix.dejavu.home.DejavuHomeScreen
import org.mozilla.fenix.theme.FirefoxTheme
import org.mozilla.fenix.utils.Settings

private val sidebarWidth = 320.dp
private const val OPEN_FRACTION = 0.35f
private val openFlingVelocity = 400.dp

/**
 * Whether the auto-hidden sidebar, pulled out to [pulled] px of [width] px and let go at [velocity] px/s toward the end
 * of the screen, ends open. It opens past [OPEN_FRACTION] of its width or on a fling of [flingVelocity] toward the
 * end, and closes on a fling toward the start, whichever way it was dragged.
 */
internal fun sidebarSettlesOpen(pulled: Float, width: Float, velocity: Float, flingVelocity: Float): Boolean = when {
    velocity >= flingVelocity -> true
    velocity <= -flingVelocity -> false
    else -> pulled > width * OPEN_FRACTION
}

/** Whether the home screen shows in a sidebar next to pages, which then need no Home button. */
@Composable
fun isHomeSidebarShown(settings: Settings = components.settings): Boolean =
    settings.isVerticalTabsEnabled && !settings.isVerticalTabsAutoHide && AcornWindowSize.isLargeWindow()

/**
 * The Dejavu home screen in a sidebar next to the page. Shows nothing on small windows, so the page keeps the full
 * width there.
 *
 * @param navController Used by the home screen to open settings, bookmarks and the like.
 * @param onNoTabs Invoked when the last tab is closed.
 * @param overlayShown Whether the auto-hidden sidebar is shown over the page, or null when the sidebar sits next to the
 *   page. Back, a tap beside it and a swipe toward the start of the screen close it.
 * @param showHandle Whether the handle that pulls out the auto-hidden sidebar is drawn. It works either way.
 * @param browserStore The [BrowserStore] the tabs are observed from.
 * @param tabsUseCases The [TabsUseCases] used to select tabs.
 */
@Composable
fun TabSidebar(
    navController: NavController,
    onNoTabs: () -> Unit,
    overlayShown: MutableState<Boolean>? = null,
    showHandle: Boolean = true,
    browserStore: BrowserStore = components.core.store,
    tabsUseCases: TabsUseCases = components.useCases.tabsUseCases,
) {
    if (!AcornWindowSize.isLargeWindow()) return

    val selectedTabId by browserStore.observeAsComposableState { it.selectedTabId }
    val restoreComplete by browserStore.observeAsComposableState { it.restoreComplete }
    LaunchedEffect(selectedTabId, restoreComplete) {
        if (selectedTabId == null && restoreComplete) onNoTabs()
    }
    var query by remember { mutableStateOf("") }
    val tabs by browserStore.observeAsComposableState { state ->
        state.getNormalOrPrivateTabs(private = state.selectedTab?.content?.private == true)
    }
    val isShown = overlayShown?.value ?: true
    val hide: () -> Unit = { overlayShown?.value = false }
    BackHandler(enabled = overlayShown?.value == true, onBack = hide)
    val density = LocalDensity.current
    val widthPx = with(density) { sidebarWidth.toPx() }
    val flingPx = with(density) { openFlingVelocity.toPx() }
    var pulled by remember { mutableFloatStateOf(if (isShown) widthPx else 0f) }
    val isPulled by remember { derivedStateOf { pulled > 0f } }
    // One state for the handle and the open sidebar. Animations run through it too, so a new drag stops them.
    val dragState = rememberDraggableState { delta -> pulled = (pulled + delta).coerceIn(0f, widthPx) }
    suspend fun animatePulled(target: Float) {
        dragState.drag { animate(pulled, target) { value, _ -> pulled = value } }
    }
    // Drags are in screen pixels: toward the end of the screen opens, which is to the left in RTL.
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val onDragStopped: suspend CoroutineScope.(Float) -> Unit = { velocity ->
        // The velocity is reversed along with the deltas in RTL.
        val open = sidebarSettlesOpen(pulled, widthPx, velocity, flingPx)
        if (open == isShown) animatePulled(if (open) widthPx else 0f)
        overlayShown?.value = open
    }
    LaunchedEffect(isShown) { animatePulled(if (isShown) widthPx else 0f) }

    val hasScrim = overlayShown != null && (isShown || isPulled)
    Box(modifier = if (hasScrim) Modifier.fillMaxSize() else Modifier.fillMaxHeight()) {
        if (hasScrim) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = pulled / widthPx }
                    .background(Color.Black.copy(alpha = 0.32f))
                    .pointerInput(Unit) { detectTapGestures { hide() } },
            )
        }

        if (overlayShown != null && !isShown) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer { alpha = 1f - pulled / widthPx }
                    .systemGestureExclusion()
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        reverseDirection = isRtl,
                        onDragStopped = onDragStopped,
                    )
                    .clickable { overlayShown.value = true }
                    .padding(start = 2.dp, end = 8.dp)
                    .size(width = 6.dp, height = 120.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = if (showHandle) 0.4f else 0f),
                        RoundedCornerShape(3.dp),
                    ),
            )
        }

        if (isShown || isPulled) {
            val onOpenTab: (String) -> Unit = { tabId ->
                tabsUseCases.selectTab(tabId)
                hide()
            }
            Column(
                modifier = Modifier
                    // Mirrored in RTL, so the closed sidebar sits past the start edge there too.
                    .offset { IntOffset((pulled - widthPx).roundToInt(), 0) }
                    .then(
                        if (overlayShown != null) {
                            // Swiping the open sidebar toward the start of the screen closes it.
                            Modifier.draggable(
                                state = dragState,
                                orientation = Orientation.Horizontal,
                                reverseDirection = isRtl,
                                onDragStopped = onDragStopped,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .width(sidebarWidth)
                    .fillMaxHeight(),
            ) {
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

