/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import android.graphics.Bitmap
import android.graphics.Color
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineView
import mozilla.components.lib.state.ext.flow
import org.mozilla.fenix.components.AppStore
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.captureTopEdge

private const val EDGE_SAMPLES = 24
private const val SETTLE_MS = 120L
private const val LATER_LOOKS = 5
private const val LATER_LOOK_MS = 1_000L
private const val REMEMBERED_TABS = 32

// Colors this close count as one, so a page's background wins over a logo or text crossing its top edge.
private const val COLOR_BUCKET = 0xF0F0F0
private const val OPAQUE = 0xFF shl 24

/**
 * Paints what shows above the page, behind Dejavu's address bar and the status bar, in the color along the page's top
 * edge, so the address bar floats over the page as in Candy instead of over a band in the app's color. The status bar's
 * icons turn dark over light pages and light over dark ones, and so does the fade behind them. While a search screen
 * covers the page, they follow the app's theme again.
 *
 * @param behindPage The view whose background shows above the page.
 * @param layout The browser layout holding the fade behind the status bar.
 * @param restoreTheme Gives the status bar the icons of the app's theme back.
 */
internal class PageTint(
    private val window: Window,
    private val engineView: EngineView,
    private val behindPage: View,
    private val layout: CoordinatorLayout,
    private val restoreTheme: () -> Unit,
) {
    private var tinted: Int? = null

    /** Follows the page of the selected tab of [browserStore] while [owner] is resumed. */
    fun start(owner: LifecycleOwner, browserStore: BrowserStore, appStore: AppStore) {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val searches = appStore.flow().map { it.searchState.isSearchActive }.distinctUntilChanged()
                merge(pageChanges(browserStore), pageMoves(), searches).collectLatest {
                    if (appStore.state.searchState.isSearchActive) {
                        restoreTheme()
                        return@collectLatest
                    }
                    val tab = browserStore.state.selectedTab ?: return@collectLatest
                    // A tab shown again takes its color at once, before its page is pictured.
                    rememberedColors[tab.id]?.let(::tint)
                    if (!tab.content.firstContentfulPaint || window.isFullScreen) return@collectLatest
                    // Waits for the page to settle, as scrolling and loading keep changing what is at its top, then
                    // looks a few more times, as styles and add-ons like Dark Reader may still change its colors.
                    delay(SETTLE_MS)
                    tintLike(tab.id)
                    repeat(LATER_LOOKS) {
                        delay(LATER_LOOK_MS)
                        tintLike(tab.id)
                    }
                }
            }
        }
    }

    private fun pageChanges(store: BrowserStore): Flow<ShownPage?> = store.flow()
        .map { state ->
            state.selectedTab?.let { tab ->
                ShownPage(
                    tabId = tab.id,
                    url = tab.content.url,
                    loading = tab.content.loading,
                    painted = tab.content.firstContentfulPaint,
                    scrollY = tab.readerState.scrollY,
                )
            }
        }
        .distinctUntilChanged()

    /** Where the page is in the window, as the address bar scrolls away and back. */
    private fun pageMoves(): Flow<Int> = callbackFlow {
        val view = engineView.asView()
        val location = IntArray(2)
        var top = Int.MIN_VALUE
        val listener = ViewTreeObserver.OnPreDrawListener {
            view.getLocationInWindow(location)
            if (location[1] != top) {
                top = location[1]
                trySend(top)
            }
            true
        }
        val observer = view.viewTreeObserver
        observer.addOnPreDrawListener(listener)
        awaitClose {
            (if (observer.isAlive) observer else view.viewTreeObserver).removeOnPreDrawListener(listener)
        }
    }

    /** Tints like the page of the tab with [tabId] shows at its top now, and remembers that color for the tab. */
    private suspend fun tintLike(tabId: String) {
        val view = (engineView.asView() as? ViewGroup)?.children?.firstOrNull { it is GeckoView } as? GeckoView
        if (view == null || !view.isAttachedToWindow) return
        val edge = captureTopEdge(view, EDGE_SAMPLES)?.valueOrNull() ?: return
        val color = edge.topEdgeColor()
        edge.recycle()
        rememberedColors.put(tabId, color)
        tint(color)
    }

    private fun tint(color: Int) {
        val isLightPage = isLight(color)
        if (color != tinted) {
            tinted = color
            behindPage.setBackgroundColor(color)
            layout.tintStatusBarScrim(if (isLightPage) Color.WHITE else Color.BLACK)
        }
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = isLightPage
    }

    private data class ShownPage(
        val tabId: String,
        val url: String,
        val loading: Boolean,
        val painted: Boolean,
        val scrollY: Int?,
    )

    private companion object {
        val rememberedColors = LruCache<String, Int>(REMEMBERED_TABS)
    }
}

/** Whether dark text and icons stand out more than light ones on [color]. */
private fun isLight(color: Int): Boolean =
    ColorUtils.calculateContrast(Color.BLACK, color) > ColorUtils.calculateContrast(Color.WHITE, color)

/** The color most of the top row of [this] picture has: the page's background there, not a logo or text crossing it. */
internal fun Bitmap.topEdgeColor(): Int {
    val row = IntArray(width).also { getPixels(it, 0, width, 0, 0, width, 1) }
    return row.groupBy { it and COLOR_BUCKET }.maxBy { it.value.size }.value.first() or OPAQUE
}

private suspend fun <T> GeckoResult<T>.valueOrNull(): T? = suspendCancellableCoroutine { continuation ->
    then(
        { value ->
            continuation.resume(value)
            GeckoResult<Void>()
        },
        {
            continuation.resume(null)
            GeckoResult<Void>()
        },
    )
}
