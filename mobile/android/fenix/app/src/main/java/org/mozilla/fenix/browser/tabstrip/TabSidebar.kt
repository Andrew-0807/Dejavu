/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.browser.tabstrip

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.browser.state.selector.getNormalOrPrivateTabs
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.compose.base.theme.layout.AcornWindowSize
import mozilla.components.compose.browser.awesomebar.AwesomeBar
import mozilla.components.compose.browser.awesomebar.AwesomeBarDefaults
import mozilla.components.compose.browser.toolbar.ui.AddressFieldDefaults
import mozilla.components.compose.browser.toolbar.ui.addressFieldSurface
import mozilla.components.concept.engine.EngineSession.LoadUrlFlags
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.lib.state.ext.observeAsComposableState
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.R
import org.mozilla.fenix.components.components
import org.mozilla.fenix.dejavu.browser.DejavuToolbar
import org.mozilla.fenix.dejavu.browser.shownWorkspaceTheme
import org.mozilla.fenix.dejavu.browser.workspaceTheme
import org.mozilla.fenix.dejavu.home.DejavuHomeScreen
import org.mozilla.fenix.dejavu.home.TabRow
import org.mozilla.fenix.dejavu.home.displayTitle
import org.mozilla.fenix.dejavu.home.isAwake
import org.mozilla.fenix.dejavu.newtab.webSuggestionProviders
import org.mozilla.fenix.dejavu.shortcuts.KeyboardShortcuts
import org.mozilla.fenix.utils.Settings

/** The sidebar width a new install starts with, and the one a double tap on its edge goes back to. */
internal const val SIDEBAR_DEFAULT_WIDTH_DP = 320f

/** The narrowest the sidebar gets: tab icons and short titles still fit. */
internal const val SIDEBAR_MIN_WIDTH_DP = 200f

/** The widest the sidebar gets, as a share of the window, so the page always keeps most of it. */
internal const val SIDEBAR_MAX_WINDOW_FRACTION = 0.4f

private const val OPEN_FRACTION = 0.35f

/** The width the search field needs to show its whole hint, "Search tabs or enter address". */
private val FullHintMinWidth = 220.dp
private val openFlingVelocity = 400.dp

/** The strip along the inner edge of the sidebar that shows its grab line and takes double taps. */
private val ResizeGutter = 12.dp

/** How far from the inner edge a sideways drag resizes the sidebar: the gutter plus as much again over the content. */
private val ResizeTouchWidth = 24.dp

/**
 * The width in dp the sidebar takes for [wished] dp, 0 or less meaning the default width, in a window [windowWidth]
 * dp wide. It stays between [SIDEBAR_MIN_WIDTH_DP] and [SIDEBAR_MAX_WINDOW_FRACTION] of the window; on a window too
 * narrow for both, the minimum wins.
 */
internal fun sidebarWidthDp(wished: Float, windowWidth: Float): Float {
    val max = (windowWidth * SIDEBAR_MAX_WINDOW_FRACTION).coerceAtLeast(SIDEBAR_MIN_WIDTH_DP)
    val width = if (wished <= 0f) SIDEBAR_DEFAULT_WIDTH_DP else wished
    return width.coerceIn(SIDEBAR_MIN_WIDTH_DP, max)
}

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
    settings.isTabSidebarShown(AcornWindowSize.isLargeWindow()) && !settings.isVerticalTabsAutoHide

/** Where the sidebar sits, for what lays itself out beside it, like the "More" menu sheet. */
object TabSidebarLayout {
    /** The width the sidebar takes next to pages, 0 when it is not shown there. It follows a drag of its edge live. */
    var occupiedWidth: Dp by mutableStateOf(0.dp)
}

/**
 * The Dejavu home screen in a sidebar next to the page, under one search field that finds open tabs and loads
 * addresses and searches. Shows nothing on small windows, so the page keeps the full width there. Dragging the edge
 * that faces the page changes the width, and double tapping it resets it.
 *
 * @param activity The activity, for the search suggestions.
 * @param navController Used by the home screen to open settings, bookmarks and the like.
 * @param onNoTabs Invoked when the last tab is closed.
 * @param overlayShown Whether the auto-hidden sidebar is shown over the page, or null when the sidebar sits next to the
 *   page. Back, a tap beside it and a swipe toward the start of the screen close it.
 * @param showHandle Whether the handle that pulls out the auto-hidden sidebar is drawn. It works either way.
 * @param browserStore The [BrowserStore] the tabs are observed from.
 * @param tabsUseCases The [TabsUseCases] used to select tabs.
 */
@Suppress("LongMethod", "LongParameterList", "CognitiveComplexMethod")
@Composable
fun TabSidebar(
    activity: HomeActivity,
    navController: NavController,
    onNoTabs: () -> Unit,
    overlayShown: MutableState<Boolean>? = null,
    showHandle: Boolean = true,
    browserStore: BrowserStore = components.core.store,
    tabsUseCases: TabsUseCases = components.useCases.tabsUseCases,
) {
    DisposableEffect(Unit) { onDispose { TabSidebarLayout.occupiedWidth = 0.dp } }
    val settings = components.settings
    if (!settings.isTabSidebarShown(AcornWindowSize.isLargeWindow())) {
        SideEffect { TabSidebarLayout.occupiedWidth = 0.dp }
        return
    }

    val selectedTabId by browserStore.observeAsComposableState { it.selectedTabId }
    val restoreComplete by browserStore.observeAsComposableState { it.restoreComplete }
    LaunchedEffect(selectedTabId, restoreComplete) {
        if (selectedTabId == null && restoreComplete) onNoTabs()
    }

    // The width: the saved one, or the one being dragged, kept inside what this window allows. Rotating or resizing
    // the window recomposes with its new width.
    val windowWidth = LocalConfiguration.current.screenWidthDp.toFloat()
    var wishedWidth by remember { mutableFloatStateOf(settings.sidebarWidthDp.toFloat()) }
    val widthDp = sidebarWidthDp(wishedWidth, windowWidth)
    val currentWidth = rememberUpdatedState(widthDp)
    SideEffect { TabSidebarLayout.occupiedWidth = if (overlayShown == null) widthDp.dp else 0.dp }
    var resizing by remember { mutableStateOf(false) }
    fun saveWidth(width: Float) {
        wishedWidth = width
        settings.sidebarWidthDp = width.roundToInt()
    }

    val isShown = overlayShown?.value ?: true
    val hide: () -> Unit = { overlayShown?.value = false }
    BackHandler(enabled = overlayShown?.value == true, onBack = hide)
    val density = LocalDensity.current
    val widthPx = with(density) { widthDp.dp.toPx() }
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
    // A shown sidebar takes its new width at once, so its outer edge stays on the screen edge while it is resized.
    LaunchedEffect(widthPx) { if (isShown) pulled = widthPx }

    val hasScrim = overlayShown != null && (isShown || isPulled)
    Box(modifier = if (hasScrim) Modifier.fillMaxSize() else Modifier.fillMaxHeight()) {
        if (hasScrim) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = pulled / widthPx }
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
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
            val placement = remember { arrayOfNulls<LayoutCoordinates>(1) }
            Row(
                modifier = Modifier
                    // Mirrored in RTL, so the closed sidebar sits past the start edge there too.
                    .offset { IntOffset((pulled - widthPx).roundToInt(), 0) }
                    .onPlaced { placement[0] = it }
                    .resizeEdge(
                        isRtl = isRtl,
                        placement = { placement[0] },
                        currentWidth = currentWidth,
                        onResize = { width ->
                            resizing = true
                            wishedWidth = width
                        },
                        onResizeEnd = {
                            resizing = false
                            saveWidth(currentWidth.value)
                        },
                    )
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
                    .width(widthDp.dp)
                    .fillMaxHeight(),
            ) {
                SidebarContent(
                    activity = activity,
                    navController = navController,
                    isShown = isShown,
                    browserStore = browserStore,
                    tabsUseCases = tabsUseCases,
                    onDone = hide,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                ResizeGrip(active = resizing, onReset = { saveWidth(0f) })
            }
        }
    }
}

/**
 * Resizes the sidebar by sideways drags that start within [ResizeTouchWidth] of its inner edge, the end edge, mirrored
 * in RTL, far from the screen edge where Android's back gesture starts. The drag is taken before the content sees it,
 * so the workspace pager does not scroll, while taps and vertical drags there still reach the content. The width comes
 * from window positions, since the sidebar itself moves under the finger in RTL as it resizes.
 */
private fun Modifier.resizeEdge(
    isRtl: Boolean,
    placement: () -> LayoutCoordinates?,
    currentWidth: State<Float>,
    onResize: (widthDp: Float) -> Unit,
    onResizeEnd: () -> Unit,
): Modifier = pointerInput(isRtl) {
    val zone = ResizeTouchWidth.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val inZone = if (isRtl) down.position.x <= zone else down.position.x >= size.width - zone
        if (!inZone) return@awaitEachGesture
        var dx = 0f
        var dy = 0f
        while (hypot(dx, dy) <= viewConfiguration.touchSlop) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed) return@awaitEachGesture
            dx += change.positionChange().x
            dy += change.positionChange().y
        }
        if (abs(dy) >= abs(dx)) return@awaitEachGesture
        val start = placement() ?: return@awaitEachGesture
        val startX = start.localToWindow(down.position).x
        val startWidth = currentWidth.value
        val direction = if (isRtl) -1f else 1f
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed) break
            change.consume()
            val layout = placement()?.takeIf { it.isAttached } ?: break
            val x = layout.localToWindow(change.position).x
            onResize(startWidth + (x - startX) * direction / density)
        }
        onResizeEnd()
    }
}

/** The grab line on the inner edge of the sidebar: faint at rest, clear while hovered, pressed or dragged. */
@Composable
private fun ResizeGrip(active: Boolean, onReset: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    var pressed by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (active || hovered || pressed) GRIP_ACTIVE_ALPHA else GRIP_REST_ALPHA,
        label = "SidebarGripAlpha",
    )
    val label = stringResource(R.string.vertical_tabs_resize)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(ResizeGutter)
            .fillMaxHeight()
            .hoverable(interactions)
            // TalkBack's double tap resets the width, as the label says.
            .semantics {
                contentDescription = label
                onClick {
                    onReset()
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onDoubleTap = { onReset() },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 40.dp)
                .graphicsLayer { this.alpha = alpha }
                .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
        )
    }
}

private const val GRIP_REST_ALPHA = 0.18f
private const val GRIP_ACTIVE_ALPHA = 0.7f

/**
 * The search field on top and, under it, the home screen or, while something is typed, the open tabs that match and
 * then the suggestions of the address bar. Enter loads what was typed in the shown tab, Shift+Enter in a new tab.
 */
@Suppress("LongMethod", "LongParameterList")
@Composable
private fun SidebarContent(
    activity: HomeActivity,
    navController: NavController,
    isShown: Boolean,
    browserStore: BrowserStore,
    tabsUseCases: TabsUseCases,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fenix = components
    var query by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val selectedTabId by browserStore.observeAsComposableState { it.selectedTabId }
    val isPrivate by browserStore.observeAsComposableState { it.selectedTab?.content?.private == true }
    val tabs by browserStore.observeAsComposableState { state ->
        state.getNormalOrPrivateTabs(private = state.selectedTab?.content?.private == true)
    }

    fun close() {
        query = ""
        focusManager.clearFocus()
    }

    fun openTab(tabId: String) {
        tabsUseCases.selectTab(tabId)
        close()
        onDone()
    }

    // The address bar's own loading, so search keywords, search engines and new tabs work the same.
    fun load(
        text: String,
        newTab: Boolean,
        forceSearch: Boolean = false,
        engine: SearchEngine? = null,
        flags: LoadUrlFlags = LoadUrlFlags.none(),
    ) {
        if (text.isBlank()) return
        fenix.useCases.fenixBrowserUseCases.loadUrlOrSearch(
            searchTermOrURL = text.trim(),
            newTab = newTab,
            private = isPrivate == true,
            forceSearch = forceSearch,
            searchEngine = engine
                ?: browserStore.state.search.selectedOrDefaultSearchEngine(private = isPrivate == true),
            flags = flags,
        )
        close()
        onDone()
    }

    BackHandler(enabled = query.isNotEmpty() || focused) { close() }

    // Ctrl+L and Ctrl+K come to this field while the sidebar is shown.
    DisposableEffect(isShown) {
        val focusField: () -> Unit = { focus.requestFocus() }
        if (isShown) KeyboardShortcuts.focusSidebarSearch = focusField
        onDispose {
            if (KeyboardShortcuts.focusSidebarSearch === focusField) KeyboardShortcuts.focusSidebarSearch = null
        }
    }

    // A private field shows no history, see webSuggestionProviders.
    val providers = remember(isPrivate) {
        webSuggestionProviders(
            activity = activity,
            isPrivate = isPrivate == true,
            engine = browserStore.state.search.selectedOrDefaultSearchEngine(private = isPrivate == true),
            // Open tabs have their own section above the suggestions.
            showOpenTabs = false,
            typedQuery = { query },
            onLoad = { url, forceSearch, engine, flags -> load(url, newTab = false, forceSearch, engine, flags) },
            onSelectTab = ::openTab,
        )
    }

    // Opaque, as the auto-hidden sidebar slides over the page. Drawn like the bars, so it matches them when docked.
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .workspaceTheme(shownWorkspaceTheme()),
    ) {
        SidebarSearchField(
            value = query,
            onValueChange = { query = it },
            onSubmit = { newTab -> load(query, newTab) },
            onClear = ::close,
            onMoveSelection = { down -> focusManager.moveFocus(if (down) FocusDirection.Down else FocusDirection.Up) },
            modifier = Modifier
                .padding(8.dp)
                .focusRequester(focus)
                .onFocusChanged { focused = it.hasFocus },
        )
        if (query.isBlank()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                DejavuHomeScreen(navController = navController, onOpenTab = ::openTab, isSidebar = true)
            }
        } else {
            val typed = query.trim()
            val matches = tabs.orEmpty().filter {
                it.content.title.contains(typed, ignoreCase = true) || it.content.url.contains(typed, ignoreCase = true)
            }
            if (matches.isNotEmpty()) {
                SectionHeader(stringResource(R.string.vertical_tabs_search_open_tabs))
                Column(
                    modifier = Modifier
                        .heightIn(max = OpenTabsMaxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                ) {
                    matches.forEach { tab ->
                        TabRow(
                            title = tab.displayTitle,
                            url = tab.content.url,
                            depth = 0,
                            container = null,
                            isAwake = tab.isAwake,
                            isCurrent = tab.id == selectedTabId,
                            isSplit = false,
                            selection = null,
                            actions = emptyList(),
                            onAction = {},
                            onClick = { openTab(tab.id) },
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AwesomeBar(
                    text = typed,
                    // Rows sit on the sidebar's own background, with headers like the address bar's dropdown.
                    colors = AwesomeBarDefaults.colors(
                        background = Color.Transparent,
                        groupTitle = MaterialTheme.colorScheme.primary,
                    ),
                    providers = providers,
                    onSuggestionClicked = { it.onSuggestionClicked?.invoke() },
                    onAutoComplete = { suggestion -> suggestion.editSuggestion?.let { query = it } },
                    onRemoveClicked = {},
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        // Like the suggestion group headers below it.
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .heightIn(min = 36.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { heading() },
    )
}

private val OpenTabsMaxHeight = 280.dp

/**
 * The one search field of the sidebar, drawn like the address bar (see `docs/dejavu/address-bar.md`): a 48dp pill
 * with a hairline that turns into the accent ring while focused, glass over the workspace colors at rest and solid
 * while typing, and the address bar's text size.
 *
 * @param value What is typed.
 * @param onValueChange Called as the user types.
 * @param onSubmit Enter or the keyboard's Go, with whether it opens a new tab (Shift+Enter).
 * @param onClear Esc or the clear button: empties the field and leaves it.
 * @param onMoveSelection Up or down arrow, true for down, to move through the results.
 * @param modifier The [Modifier] of the field.
 */
@Suppress("LongMethod")
@Composable
internal fun SidebarSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (newTab: Boolean) -> Unit,
    onClear: () -> Unit,
    onMoveSelection: (down: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val focus = AddressFieldDefaults.rememberFocusProgress(focused)
    val scheme = MaterialTheme.colorScheme
    // At rest the fill is the same glass tint the address bar has over pages, see DejavuToolbar.glass.
    val colors = AddressFieldDefaults.colors().let {
        if (focused) it else it.copy(fill = DejavuToolbar.glass(scheme).surfaceContainerHighest)
    }
    val textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = AddressFieldDefaults.textSize())
    val hint = stringResource(R.string.vertical_tabs_search_or_address)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(AddressFieldDefaults.Height)
            .onFocusChanged { focused = it.hasFocus }
            .addressFieldSurface(colors) { focus.value },
    ) {
        Box(modifier = Modifier.size(AddressFieldDefaults.Height), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconsR.drawable.mozac_ic_search_24),
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
            )
        }
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .padding(end = if (value.isEmpty()) AddressFieldDefaults.HorizontalPadding else 0.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            // The narrow sidebar has no room for the whole hint, so it gets the short one instead of a cut one.
            val shownHint = if (maxWidth < FullHintMinWidth) stringResource(R.string.dejavu_search_tabs_short) else hint
            if (value.isEmpty()) {
                Text(
                    text = shownHint,
                    style = textStyle,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // The field itself carries the hint for screen readers.
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = textStyle.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { onSubmit(false) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = hint }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter -> onSubmit(event.isShiftPressed)
                            Key.Escape -> onClear()
                            Key.DirectionDown -> onMoveSelection(true)
                            Key.DirectionUp -> onMoveSelection(false)
                            else -> return@onPreviewKeyEvent false
                        }
                        true
                    },
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = onClear) {
                Icon(
                    painter = painterResource(iconsR.drawable.mozac_ic_cross_24),
                    contentDescription = stringResource(R.string.vertical_tabs_search_clear),
                    tint = scheme.onSurfaceVariant,
                )
            }
        }
    }
}
