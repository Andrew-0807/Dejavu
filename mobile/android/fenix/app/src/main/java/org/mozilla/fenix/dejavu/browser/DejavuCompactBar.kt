/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarInteraction.BrowserToolbarEvent.Source
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarStore
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.R
import org.mozilla.fenix.browser.tabstrip.isHomeSidebarShown
import org.mozilla.fenix.components.toolbar.DisplayActions
import org.mozilla.fenix.ext.components

/** How the compact toolbar ends a swipe, see [compactBarSettle]. */
internal enum class CompactBarSettle {
    /** Shows the buttons. */
    EXPAND,

    /** Hides the buttons. */
    COLLAPSE,

    /** Keeps the buttons and runs the gesture set for "Swipe the toolbar up". */
    EXPAND_AND_RUN_GESTURE,

    /** Keeps the buttons hidden and reports a swipe down on the collapsed bar. */
    SWIPE_DOWN_COLLAPSED,
}

/**
 * How the compact toolbar ends a swipe that started with the buttons [startReveal] shown (0 hidden, 1 shown), moved
 * the finger by [distance] px (negative is up) and ended at [velocity] px/s (negative is up). A swipe shorter than
 * [minDistance] and slower than [minVelocity] goes back to where it started, so a small slip never opens the menu,
 * hides the buttons or starts full immersive mode.
 */
internal fun compactBarSettle(
    startReveal: Float,
    distance: Float,
    velocity: Float,
    minDistance: Float,
    minVelocity: Float,
): CompactBarSettle {
    val flingUp = velocity <= -minVelocity
    val flingDown = velocity >= minVelocity
    val farUp = -distance >= minDistance
    val farDown = distance >= minDistance
    return when {
        startReveal >= 1f && distance < 0f ->
            if (farUp || flingUp) CompactBarSettle.EXPAND_AND_RUN_GESTURE else CompactBarSettle.EXPAND
        // One swipe up from the collapsed bar shows the buttons and runs the gesture, with no second swipe needed.
        startReveal <= 0f && distance < 0f && (farUp || flingUp) && !flingDown -> CompactBarSettle.EXPAND_AND_RUN_GESTURE
        startReveal <= 0f && distance > 0f ->
            if (farDown || flingDown) CompactBarSettle.SWIPE_DOWN_COLLAPSED else CompactBarSettle.COLLAPSE
        flingUp -> CompactBarSettle.EXPAND
        flingDown -> CompactBarSettle.COLLAPSE
        farUp -> CompactBarSettle.EXPAND
        farDown -> CompactBarSettle.COLLAPSE
        startReveal >= HALF -> CompactBarSettle.EXPAND
        else -> CompactBarSettle.COLLAPSE
    }
}

/** Whether a drag that moved by [dx] and [dy] px is claimed by the compact toolbar: past [touchSlop] and vertical. */
internal fun compactBarClaimsDrag(dx: Float, dy: Float, touchSlop: Float): Boolean =
    hypot(dx, dy) > touchSlop && abs(dy) > abs(dx)

/** Whether a drag that moved by [dx] and [dy] px is past [touchSlop], so that it is claimed or left to others. */
internal fun isPastTouchSlop(dx: Float, dy: Float, touchSlop: Float): Boolean = hypot(dx, dy) > touchSlop

private const val HALF = 0.5f
private val minSwipeDistance = 24.dp
private val minFlingVelocity = 400.dp

/**
 * The compact toolbar: one bar at the bottom of the screen with the address bar and a Home button beside it. A swipe up
 * shows [buttons] above it, following the finger, and runs the gesture set for "Swipe the toolbar up" (the "More" menu
 * by default) on release. Swiping down hides the buttons. Taps on the address bar and the Home button stay taps until the finger
 * moves past the touch slop, mostly up or down.
 *
 * @param store The store of the address bar.
 * @param buttons The bar with the other toolbar buttons, shown only while the compact toolbar is swiped up. It must
 *   apply [ScaledToolbar] itself, as only the address bar row is wrapped here.
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
    var reveal by remember { mutableFloatStateOf(if (expanded) 1f else 0f) }
    val shown by remember { derivedStateOf { reveal > 0f } }
    // Read only by the swipe, so it is a plain holder: measuring must not write state.
    val buttonsHeight = remember { IntArray(1) }
    val scope = rememberCoroutineScope()
    var settling by remember { mutableStateOf<Job?>(null) }
    val view = LocalView.current
    val settings = LocalContext.current.components.settings
    fun settle(open: Boolean) {
        expanded = open
        settling?.cancel()
        settling = scope.launch { animate(reveal, if (open) 1f else 0f) { value, _ -> reveal = value } }
    }
    val toggleLabel = stringResource(
        if (expanded) R.string.compact_toolbar_hide_buttons else R.string.compact_toolbar_show_buttons,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                val minDistance = minSwipeDistance.toPx()
                val minVelocity = minFlingVelocity.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPointerInputChange(down)
                    var dx = 0f
                    var dy = 0f
                    // Nothing is consumed until the drag is claimed, so taps and long presses still reach the buttons.
                    while (!isPastTouchSlop(dx, dy, slop)) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed || change.isConsumed) return@awaitEachGesture
                        velocityTracker.addPointerInputChange(change)
                        dx += change.positionChange().x
                        dy += change.positionChange().y
                    }
                    // Mostly sideways: leave it to the parent's swipe between tabs.
                    if (!compactBarClaimsDrag(dx, dy, slop)) return@awaitEachGesture
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    settling?.cancel()
                    val start = reveal
                    val range = if (buttonsHeight[0] > 0) buttonsHeight[0].toFloat() else 64.dp.toPx()
                    var distance = dy
                    fun follow() {
                        // Pulled further up while shown, the buttons stay; the gesture runs on release.
                        if (start < 1f || distance > 0f) reveal = (start - distance / range).coerceIn(0f, 1f)
                    }
                    follow()
                    val finished = verticalDrag(down.id) { change ->
                        change.consume()
                        velocityTracker.addPointerInputChange(change)
                        distance += change.positionChange().y
                        follow()
                    }
                    if (!finished) {
                        settle(expanded)
                        return@awaitEachGesture
                    }
                    val velocity = velocityTracker.calculateVelocity().y
                    when (compactBarSettle(start, distance, velocity, minDistance, minVelocity)) {
                        CompactBarSettle.EXPAND -> settle(true)
                        CompactBarSettle.COLLAPSE -> settle(false)
                        CompactBarSettle.EXPAND_AND_RUN_GESTURE -> {
                            settle(true)
                            settings.toolbarSwipeGesture.takeIf { it != "none" }?.let {
                                store.dispatch(DejavuToolbar.GestureTriggered(it))
                            }
                        }
                        CompactBarSettle.SWIPE_DOWN_COLLAPSED -> {
                            settle(false)
                            // Hook for the integrator: a swipe down on the collapsed bar, e.g. to go immersive.
                            onSwipeDown?.invoke()
                        }
                    }
                }
            }
            .semantics {
                // Screen readers cannot swipe the bar, so it offers showing and hiding the buttons as an action.
                customActions = listOf(
                    CustomAccessibilityAction(toggleLabel) {
                        settle(!expanded)
                        true
                    },
                )
            },
    ) {
        if (shown) {
            Box(
                modifier = Modifier
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minHeight = 0))
                        buttonsHeight[0] = placeable.height
                        val height = (placeable.height * reveal).roundToInt()
                        layout(placeable.width, height) { placeable.place(0, height - placeable.height) }
                    },
            ) {
                buttons()
            }
        }
        // The buttons scale themselves (BrowserNavigationBar wraps them), so only this row is wrapped here.
        ScaledToolbar {
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
                if (!isHomeSidebarShown()) {
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
    }
}
