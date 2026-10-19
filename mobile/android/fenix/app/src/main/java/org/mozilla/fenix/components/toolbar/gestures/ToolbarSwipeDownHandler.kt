/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.toolbar.gestures

import android.graphics.PointF
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.contains
import androidx.core.graphics.toPoint
import kotlin.math.abs
import org.mozilla.fenix.browser.SwipeGestureListener
import org.mozilla.fenix.ext.getRectWithScreenLocation

/**
 * Dejavu: the one place that detects a swipe down which starts on a browser bar. Taps, horizontal swipes and swipes
 * that start anywhere else are left alone.
 *
 * @param targetView The bar the swipe has to start on, or `null` when there is none.
 * @param isEnabled Whether swipes should be handled at all.
 * @param onSwipeDown Invoked when the swipe ends as a downward fling or a drag of at least half the bar height.
 */
class ToolbarSwipeDownHandler(
    private val targetView: () -> View?,
    private val isEnabled: () -> Boolean,
    private val onSwipeDown: () -> Unit,
) : SwipeGestureListener {
    private var target: View? = null
    private var swipeYDistance = 0f

    override fun onSwipeStarted(start: PointF, next: PointF): Boolean {
        val dx = next.x - start.x
        val dy = next.y - start.y
        swipeYDistance = dy
        target =
            targetView()?.takeIf {
                isEnabled() && dy > abs(dx) && it.getRectWithScreenLocation().contains(start.toPoint())
            }
        return target != null
    }

    override fun onSwipeUpdate(distanceX: Float, distanceY: Float) {
        swipeYDistance -= distanceY
    }

    override fun onSwipeFinished(velocityX: Float, velocityY: Float) {
        val bar = target ?: return
        target = null
        val minimumFlingVelocity = ViewConfiguration.get(bar.context).scaledMinimumFlingVelocity
        if (swipeYDistance >= bar.height / 2f || velocityY >= minimumFlingVelocity) {
            onSwipeDown()
        }
    }
}
