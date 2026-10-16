/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.menu

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mozilla.fenix.R
import org.mozilla.fenix.dejavu.browser.shownWorkspaceTheme
import org.mozilla.fenix.dejavu.browser.workspaceTheme

/**
 * The swipe up on the actions bar that opens the "More" menu. The swipe starts in the browser's window and stays
 * there after the menu's dialog came up over it, so the browser passes it on here for the menu to follow.
 */
internal object MoreMenuSwipe {
    private var startedAt = 0L
    private var claimed = true

    /** Whether the finger that opened the menu is still on the screen. */
    var isFollowing by mutableStateOf(false)
        private set

    /** How far the finger moved up since it touched the bar, in pixels. */
    var distance by mutableFloatStateOf(0f)
        private set

    /** How fast the finger moved up when it left the screen, in pixels per second. */
    var releaseVelocity = 0f
        private set

    fun start() {
        startedAt = SystemClock.uptimeMillis()
        claimed = false
        distance = 0f
        releaseVelocity = 0f
        isFollowing = true
    }

    fun move(distanceUp: Float) {
        if (isFollowing) distance = distanceUp
    }

    fun end(velocityUp: Float) {
        if (!isFollowing) return
        releaseVelocity = velocityUp
        isFollowing = false
    }

    fun cancel() {
        isFollowing = false
        claimed = true
    }

    /** Whether a swipe just opened the menu, which then follows it. Each swipe is followed by one menu only. */
    fun claim(): Boolean {
        val unclaimed = !claimed && SystemClock.uptimeMillis() - startedAt < CLAIM_WINDOW_MS
        claimed = true
        return unclaimed
    }

    private const val CLAIM_WINDOW_MS = 2000L
}

/** Closes the floating "More" menu, shrinking it back into the actions bar first. */
internal interface MenuCloser {
    /** Closes the menu, running [then] once the panel shrank away, so its work does not hold up the animation. */
    fun close(then: () -> Unit = {})

    /**
     * Shrinks the panel away, then runs [action], which closes the menu itself once its work is done, so the work
     * neither holds up the animation nor gets cut short by the menu going away.
     */
    fun closeBy(action: () -> Unit)
}

/** Closes the floating "More" menu the content is in; `null` outside such a menu. */
internal val LocalMenuCloser = staticCompositionLocalOf<MenuCloser?> { null }

/**
 * The "More" menu of a web page: a panel that grows out of the actions bar and keeps the bar's margins. While the
 * finger that swiped the bar up stays down, the panel follows it, and letting go opens or closes the panel. The menu's
 * tiles wait where they end up while the panel grows over them, and fade in as it nears its full size. The open panel
 * can be dragged down by its handle or its content to close it.
 *
 * @param onDismiss Removes the menu, once the panel shrank back into the bar.
 */
@Composable
internal fun FloatingMoreMenu(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val panel = remember {
        MenuPanel(
            scope = scope,
            barHeight = with(density) { BAR_HEIGHT.toPx() },
            flingVelocity = with(density) { FLING_VELOCITY.toPx() },
            onClosed = onDismiss,
        )
    }
    val close = remember(panel) { { panel.close() } }

    LaunchedEffect(panel) {
        if (MoreMenuSwipe.claim()) panel.followSwipe() else panel.open()
    }
    // Back, and actions done with their work, close the menu through here; see DejavuMenu.closeFloating.
    BackHandler(onBack = panel::remove)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(panel) {
                awaitPointerEventScope {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull() ?: continue
                        panel.fingerAt(change.position.y, change.uptimeMillis, change.changedToDownIgnoreConsumed())
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = panel.progress.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClickLabel = stringResource(R.string.browser_close_main_menu_handlebar_content_description),
                    onClick = close,
                ),
        )
        Layout(
            content = {
                CompositionLocalProvider(LocalMenuCloser provides panel) {
                    PanelMenu(panel, content)
                }
                PanelHandle(panel)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    start = BAR_SIDE_MARGIN,
                    end = BAR_SIDE_MARGIN,
                    top = PANEL_TOP_MARGIN,
                    bottom = ACTIONS_BAR_BOTTOM_MARGIN,
                )
                .graphicsLayer {
                    alpha = smoothstep(panel.progress / SURFACE_FADE)
                    shape = PANEL_SHAPE
                    clip = true
                }
                .background(MaterialTheme.colorScheme.surface)
                .workspaceTheme(shownWorkspaceTheme())
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = BORDER_ALPHA), PANEL_SHAPE),
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val handle = measurables[1].measure(Constraints.fixedWidth(width))
            val menu = measurables[0].measure(
                Constraints(
                    minWidth = width,
                    maxWidth = width,
                    maxHeight = (constraints.maxHeight - handle.height).coerceAtLeast(0),
                ),
            )
            val travel = (handle.height + menu.height - panel.barHeight).coerceAtLeast(1f)
            panel.measured(travel)
            val height = (panel.barHeight + travel * panel.progress).roundToInt().coerceIn(0, constraints.maxHeight)
            layout(width, height) {
                // The menu waits where it ends up while the panel grows over it, so its tiles do not ride up from the
                // bar; an open panel pulled down takes the menu along under its edge.
                val menuY = if (panel.ridesEdge) handle.height else height - menu.height
                panel.menuHiddenTop = (handle.height - menuY).coerceAtLeast(0).toFloat()
                menu.place(0, menuY)
                handle.place(0, 0)
            }
        }
    }
}

@Composable
private fun PanelHandle(panel: MenuPanel) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                state = rememberDraggableState { panel.follow(startY = panel.downY) },
                orientation = Orientation.Vertical,
                enabled = !panel.isClosing,
                startDragImmediately = true,
                onDragStopped = { panel.release() },
            )
            .padding(vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = HANDLE_ALPHA)),
        )
    }
}

@Composable
private fun PanelMenu(panel: MenuPanel, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .drawWithContent { clipRect(top = panel.menuHiddenTop) { this@drawWithContent.drawContent() } }
            .graphicsLayer { alpha = smoothstep((panel.progress - CONTENT_FADE_START) / CONTENT_FADE) }
            .nestedScroll(panel)
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        content = content,
    )
}

/**
 * How far the floating menu is open and what moves it: the swipe that opened it, drags on the panel, and the springs
 * that settle it open or shrink it away.
 */
private class MenuPanel(
    private val scope: CoroutineScope,
    val barHeight: Float,
    private val flingVelocity: Float,
    private val onClosed: () -> Unit,
) : NestedScrollConnection, MenuCloser {
    private val settled = Animatable(0f, visibilityThreshold = VISIBILITY_THRESHOLD)
    private var dragged by mutableStateOf<Float?>(null)
    private var followsSwipe by mutableStateOf(false)
    private var isMeasured by mutableStateOf(false)
    private var travel = 1f
    private var dragStart = 0f
    private var anchorY = 0f
    private var fingerY = 0f
    private val fingerVelocity = VelocityTracker()
    private var motion: Job? = null

    /** Where the finger last touched down on the menu, in the coordinates of [fingerAt]. */
    var downY = 0f
        private set

    /** Whether the panel is shrinking away for good. */
    var isClosing by mutableStateOf(false)
        private set

    /** Whether the menu moves with the panel's top edge, as it does while an open panel is pulled down. */
    var ridesEdge by mutableStateOf(false)
        private set

    /** How much of the top of the menu is behind the strip of the handle, which it is not drawn over, in pixels. */
    var menuHiddenTop by mutableFloatStateOf(0f)

    private var hasShrunk = false

    /** How far the panel is open, from 0 at the size of the actions bar to 1 at the size of the whole menu. */
    val progress: Float
        get() = if (followsSwipe) (MoreMenuSwipe.distance / travel).coerceIn(0f, 1f) else dragged ?: settled.value

    /** Takes how far the panel grows from the size of the bar to the size of the whole menu, in pixels. */
    fun measured(travel: Float) {
        this.travel = travel
        if (!isMeasured) isMeasured = true
    }

    fun open() = move {
        awaitMeasured()
        settled.animateTo(1f, OPEN_SPRING)
    }

    fun followSwipe() = move {
        awaitMeasured()
        followsSwipe = true
        snapshotFlow { MoreMenuSwipe.isFollowing }.first { !it }
        settle(MoreMenuSwipe.releaseVelocity, startedAt = 0f)
    }

    /** Takes where the finger on the menu is, in the coordinates of the box around the panel, which does not move. */
    fun fingerAt(y: Float, uptimeMillis: Long, pressed: Boolean) {
        if (pressed) {
            downY = y
            fingerVelocity.resetTracking()
        }
        fingerY = y
        fingerVelocity.addPosition(uptimeMillis, Offset(0f, y))
    }

    /**
     * Moves the panel with the finger, by as much as the finger moved from [startY] when the panel started following
     * it. Drags follow where the finger is rather than add up their own steps, which come out short while the panel
     * moves the content under the finger.
     */
    fun follow(startY: Float) {
        if (isClosing) return
        if (dragged == null) {
            dragStart = progress
            anchorY = startY
            motion?.cancel()
            followsSwipe = false
            // Only a panel picked up at its full size takes the menu along, where both places of the menu meet.
            if (dragStart >= FULLY_OPEN) ridesEdge = true
        }
        dragged = (dragStart + (anchorY - fingerY) / travel).coerceIn(0f, 1f)
    }

    /** Opens or closes the panel the finger let go of. */
    fun release() {
        if (dragged == null || isClosing) return
        val velocityUp = -fingerVelocity.calculateVelocity().y
        val startedAt = dragStart
        move { settle(velocityUp, startedAt) }
    }

    override fun close(then: () -> Unit) = shrinkAway {
        then()
        onClosed()
    }

    override fun closeBy(action: () -> Unit) = shrinkAway {
        action()
        delay(REMOVAL_TIMEOUT_MS)
        onClosed()
    }

    /** Removes the menu when asked to: right away once the panel shrank away, after shrinking it otherwise. */
    fun remove() {
        if (hasShrunk) onClosed() else close()
    }

    private fun shrinkAway(after: suspend () -> Unit) {
        if (isClosing) return
        isClosing = true
        val from = progress
        move {
            settled.snapTo(from)
            followsSwipe = false
            dragged = null
            settled.animateTo(0f, CLOSE_SPRING)
            hasShrunk = true
            after()
        }
    }

    /**
     * Opens or closes the panel the finger let go of, whichever way it took the panel far enough from [startedAt],
     * where it picked the panel up.
     */
    private suspend fun settle(velocityUp: Float, startedAt: Float) {
        val from = progress
        val threshold = SETTLE_SHARE + (1f - 2 * SETTLE_SHARE) * startedAt
        val open = when {
            velocityUp > flingVelocity -> true
            velocityUp < -flingVelocity -> false
            else -> from > threshold
        }
        settled.snapTo(from)
        followsSwipe = false
        dragged = null
        if (open) {
            settled.animateTo(1f, OPEN_SPRING, initialVelocity = velocityUp / travel)
            ridesEdge = false
        } else {
            isClosing = true
            settled.animateTo(0f, CLOSE_SPRING, initialVelocity = velocityUp / travel)
            onClosed()
        }
    }

    private fun move(block: suspend () -> Unit) {
        motion?.cancel()
        motion = scope.launch { block() }
    }

    private suspend fun awaitMeasured() {
        snapshotFlow { isMeasured }.first { it }
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val from = dragged ?: return Offset.Zero
        if (source != NestedScrollSource.UserInput || available.y >= 0f || from >= 1f) return Offset.Zero
        // A finger going back up first opens the panel again, then scrolls the menu.
        follow(anchorY)
        return Offset(0f, available.y)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f || isClosing) return Offset.Zero
        // The menu is at its top, so the rest of the finger's way down pulls the panel down.
        follow(startY = fingerY - available.y)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (dragged == null) return Velocity.Zero
        release()
        return available
    }
}

private fun smoothstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private val PANEL_SHAPE = RoundedCornerShape(BAR_HEIGHT / 2)
private val PANEL_TOP_MARGIN = 48.dp

/** How long the shrunk menu waits at most for an action to close it, in case the action never does. */
private const val REMOVAL_TIMEOUT_MS = 3000L

/** How fast a finger has to leave the screen for the panel to go its way whatever its size, per second. */
private val FLING_VELOCITY = 1000.dp

/** How close the panel has to come to where it settles to stop there, small enough not to show the last step. */
private const val VISIBILITY_THRESHOLD = 0.001f
private val OPEN_SPRING = spring(dampingRatio = 0.9f, stiffness = 600f, visibilityThreshold = VISIBILITY_THRESHOLD)
private val CLOSE_SPRING = spring(dampingRatio = 1f, stiffness = 500f, visibilityThreshold = VISIBILITY_THRESHOLD)

/**
 * How much of the way between the bar and the whole menu a finger has to take the panel for it to go on that way when
 * let go, rather than back.
 */
private const val SETTLE_SHARE = 0.3f

/** How far the panel opens while its surface fades in over the actions bar. */
private const val SURFACE_FADE = 0.2f

/** How far the panel is open when the menu's tiles start to fade in, and over how much of the way they do. */
private const val CONTENT_FADE_START = 0.55f
private const val CONTENT_FADE = 0.45f

/** How far open a panel picked up has to be to take the menu along when pulled down, see [MenuPanel.ridesEdge]. */
private const val FULLY_OPEN = 0.995f
private const val SCRIM_ALPHA = 0.32f
private const val HANDLE_ALPHA = 0.4f
