/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import android.app.Activity
import android.graphics.Bitmap
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import mozilla.components.browser.state.selector.findTab
import mozilla.components.concept.base.images.ImageLoadRequest
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.components
import org.mozilla.geckoview.captureTopEdge

private val ROW_RADIUS = 16.dp
private const val ZOOM_IN_MS = 380
private const val OPEN_AT = 0.75f
private const val ZOOM_OUT_MS = 340
private const val REVEAL_MS = 160
private const val REVEAL_TIMEOUT_MS = 1_200L
private const val HOME_TIMEOUT_MS = 1_500L
private const val ROW_FRAMES = 2
private const val SHRINK_SETTLE_NANOS = 150_000_000L
private const val DRAWN_POLL_MS = 40L
private const val THUMBNAIL_TIMEOUT_MS = 150L
private const val DIM_ALPHA = 0.3f

// The share of a zoom in which the card appears over its row, or fades into it.
private const val CARD_FADE = 0.2f

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Dejavu's zoom between a tab's row on the home screen and its page. Opening a tab grows a picture of its page out of
 * its row until it fills the screen, then the browser shows the page behind it. Going home shrinks a picture of the
 * page back into the tab's row.
 */
internal object TabZoom {
    private class Placed(val owner: Any, val bounds: Rect)

    /** A zoom in progress, with the picture of the tab's page it grows or shrinks. */
    private sealed class Zoom(val tabId: String, val picture: PageShot?) {
        class In(tabId: String, picture: PageShot?, val row: Rect, val open: () -> Unit) : Zoom(tabId, picture)
        class Out(tabId: String, picture: PageShot, val leave: () -> Unit) : Zoom(tabId, picture) {
            val homeShown = CompletableDeferred<Unit>()
        }
    }

    private val rows = HashMap<String, Placed>()
    private var zoom by mutableStateOf<Zoom?>(null)
    private var overlay: ComposeView? = null

    // The page left last for the home screen, at half size, so opening that tab again starts from where it was left.
    private var lastPicture: PageShot? = null

    /** The row of the tab with [tabId], placed by [owner], is at [bounds] in the window. */
    fun rowPlaced(tabId: String, owner: Any, bounds: Rect) {
        rows[tabId] = Placed(owner, bounds)
    }

    /** The row of the tab with [tabId] placed by [owner] left the home screen. */
    fun rowGone(tabId: String, owner: Any) {
        if (rows[tabId]?.owner === owner) rows.remove(tabId)
    }

    /**
     * Opens the tab with [tabId] from its row on the home screen of [activity]: grows a picture of its page out of the
     * row until it fills the screen, then shows the browser with [open]. Without a row on screen it just opens.
     */
    fun zoomIn(activity: Activity, tabId: String, open: () -> Unit) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val row = rows[tabId]?.bounds?.takeIf { it.isShown }
        if (content == null || row == null || isZooming) {
            open()
            return
        }
        val known = lastPicture?.takeIf { it.tabId == tabId }
        val owner = activity as? LifecycleOwner
        if (known != null || owner == null) {
            start(content, Zoom.In(tabId, known, row, open))
            return
        }
        // Pages shown since the app started have a picture saved when they loaded.
        val components = activity.components
        val isPrivate = components.core.store.state.findTab(tabId)?.content?.private == true
        val request = ImageLoadRequest(tabId, content.width, isPrivate)
        owner.lifecycleScope.launch {
            val thumbnail = withTimeoutOrNull(THUMBNAIL_TIMEOUT_MS) {
                components.core.thumbnailStorage.loadThumbnail(request).await()
            }
            start(content, Zoom.In(tabId, thumbnail?.let { savedPicture(content, tabId, it) }, row, open))
        }
    }

    /**
     * Leaves the page of the tab with [tabId] for the home screen with [leave]: pictures the page, then shrinks the
     * picture back into the tab's row once the home screen shows. Without a page on screen it just leaves.
     */
    fun zoomOut(tabId: String, leave: () -> Unit) {
        val content = PagePictures.shownView?.rootView?.findViewById<ViewGroup>(android.R.id.content)
        if (content == null || isZooming || PagePictures.isCapturing) {
            leave()
            return
        }
        PagePictures.capture(tabId) { shot ->
            if (shot == null) leave() else start(content, Zoom.Out(tabId, shot, leave))
        }
    }

    /** The home screen drew itself for the first time, so a page going home can shrink into its row. */
    fun homeShown() {
        (zoom as? Zoom.Out)?.homeShown?.complete(Unit)
    }

    private val isZooming: Boolean
        get() {
            // A zoom whose screen went away, like with the app's window, is over.
            if (zoom != null && overlay?.isAttachedToWindow != true) finish(zoom)
            return zoom != null
        }

    private fun start(content: ViewGroup, next: Zoom) {
        // The browser takes the color above the page from the picture at once, before it pictures the page itself.
        next.picture?.let { PageTint.rememberColor(next.tabId, it.edgeColor.toArgb()) }
        zoom = next
        overlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        overlay = ComposeView(content.context).apply { setContent { ZoomOverlay() } }.also {
            content.addView(it, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
    }

    private fun finish(done: Zoom?) {
        if (zoom !== done) return
        zoom = null
        overlay?.let { view -> view.post { (view.parent as? ViewGroup)?.removeView(view) } }
        overlay = null
    }

    @Composable
    private fun ZoomOverlay() {
        val current = zoom ?: return
        key(current) { ZoomCard(current) }
    }

    @Composable
    private fun ZoomCard(current: Zoom) {
        // 0 with the card on the tab's row, 1 with the card filling the screen.
        val progress = remember { Animatable(if (current is Zoom.In) 0f else 1f) }
        // How much the card shows, as it uncovers the page in the browser or a home screen without the tab's row.
        val shown = remember { Animatable(1f) }
        var row by remember { mutableStateOf((current as? Zoom.In)?.row) }
        var origin by remember { mutableStateOf(Offset.Zero) }
        val placeholder = colorResource(R.color.fx_mobile_surface)
        LaunchedEffect(current) {
            when (current) {
                is Zoom.In -> {
                    coroutineScope {
                        // The browser starts setting itself up once the card nearly fills the screen, as the last
                        // frames of the growth hardly move it.
                        launch {
                            delay((ZOOM_IN_MS * OPEN_AT).toLong())
                            current.open()
                        }
                        progress.animateTo(1f, tween(ZOOM_IN_MS, easing = Emphasized))
                    }
                    withTimeoutOrNull(REVEAL_TIMEOUT_MS) { awaitPageDrawn() }
                    shown.animateTo(0f, tween(REVEAL_MS))
                }
                is Zoom.Out -> {
                    current.leave()
                    // The card covers the home screen while it sets itself up, then shrinks into the tab's row, placed
                    // in the frames right after the home screen first drew.
                    withTimeoutOrNull(HOME_TIMEOUT_MS) { current.homeShown.await() }
                    repeat(ROW_FRAMES) { withFrameNanos { } }
                    awaitSteadyFrames(maxNanos = SHRINK_SETTLE_NANOS)
                    row = rows[current.tabId]?.bounds?.takeIf { it.isShown }
                    if (row != null) {
                        progress.animateTo(0f, tween(ZOOM_OUT_MS, easing = Emphasized))
                    } else {
                        shown.animateTo(0f, tween(REVEAL_MS))
                    }
                    current.picture?.let(::keepForReopening)
                }
            }
            finish(current)
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionInWindow() }
                .graphicsLayer { alpha = shown.value }
                // Taps wait for the zoom to end, instead of reaching what is under it.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                },
        ) {
            drawZoom(current.picture, row?.translate(-origin), progress.value, placeholder, origin)
        }
    }

    private fun keepForReopening(picture: PageShot) {
        val bitmap = picture.bitmap.asAndroidBitmap()
        val half = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)
        lastPicture = PageShot(half.asImageBitmap(), picture.offset, picture.tabId, picture.edgeColor)
    }
}

/** Lets [TabZoom] grow the page of the tab with [tabId] out of this row, and shrink it back into it. */
@Composable
internal fun Modifier.tabZoomTarget(tabId: String?): Modifier {
    if (tabId == null) return this
    val owner = remember { Any() }
    DisposableEffect(tabId) {
        onDispose { TabZoom.rowGone(tabId, owner) }
    }
    return onGloballyPositioned { TabZoom.rowPlaced(tabId, owner, it.boundsInWindow()) }
}

/** A row cut away by the edge of its list, or on another workspace's page, has no room left. */
private val Rect.isShown: Boolean
    get() = width > 1f && height > 1f

/** Waits until the browser draws a page, which it can picture only once it does. */
private suspend fun awaitPageDrawn() {
    while (true) {
        delay(DRAWN_POLL_MS)
        val view = PagePictures.shownGeckoView ?: continue
        if (captureTopEdge(view, 1)?.valueOrNull() != null) return
    }
}

/** The picture saved for the tab with [tabId] when its page loaded, placed where the browser shows the page. */
private fun savedPicture(content: ViewGroup, tabId: String, bitmap: Bitmap): PageShot {
    val pageTop = content.statusBarHeight + DEJAVU_TOP_BAR_HEIGHT.value * content.resources.displayMetrics.density
    return PageShot(bitmap.asImageBitmap(), Offset(0f, pageTop), tabId, Color(bitmap.topEdgeColor()))
}

/**
 * Draws the card [progress] of the way from [row] to the whole screen, holding [picture], the page with what showed
 * above it scaled to the card's width and cut at its bottom, or [placeholder] without a picture. The screen behind the
 * card dims as it grows.
 */
private fun DrawScope.drawZoom(picture: PageShot?, row: Rect?, progress: Float, placeholder: Color, origin: Offset) {
    val full = Rect(Offset.Zero, size)
    val card = lerp(row ?: full, full, progress)
    val cardAlpha = if (row == null) 1f else (progress / CARD_FADE).coerceIn(0f, 1f)
    if (row != null) drawRect(Color.Black, alpha = DIM_ALPHA * progress)
    val corners = CornerRadius(lerp(ROW_RADIUS.toPx(), 0f, progress))
    clipPath(Path().apply { addRoundRect(RoundRect(card, corners)) }) {
        val scale = card.width / size.width
        translate(card.left, card.top) {
            scale(scale, scale, pivot = Offset.Zero) {
                drawRect(picture?.edgeColor ?: placeholder, size = size, alpha = cardAlpha)
                if (picture != null) {
                    // Saved pictures can be smaller than the page was shown, so they are drawn as wide as the screen.
                    val bitmap = picture.bitmap
                    val height = bitmap.height * size.width / bitmap.width
                    drawImage(
                        image = bitmap,
                        dstOffset = IntOffset(
                            (picture.offset.x - origin.x).roundToInt(),
                            (picture.offset.y - origin.y).roundToInt(),
                        ),
                        dstSize = IntSize(size.width.roundToInt(), height.roundToInt()),
                        alpha = cardAlpha,
                    )
                }
            }
        }
    }
}
