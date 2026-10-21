/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import kotlin.math.roundToInt
import org.mozilla.fenix.dejavu.workspaces.SplitOrientation

/** A rectangle of whole pixels, [right] and [bottom] excluded. */
internal data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Where the two pages of a split view go in the page area between the bars: the split's first tab on top or on the
 * left, the divider, and the second tab below it or on its right. They keep their places whichever tab is in focus.
 *
 * @property main Where the selected tab shows, in the browser's own view.
 * @property pane Where the other tab shows.
 * @property divider Where the divider between them goes.
 */
internal data class SplitPlaces(val main: PixelRect, val pane: PixelRect, val divider: PixelRect)

internal object SplitGeometry {
    const val MIN_FRACTION = 0.25f
    const val MAX_FRACTION = 0.75f

    /**
     * Places the pages of a split shown [orientation] in [area], with a divider [divider] pixels across between them,
     * centered where the first tab's [fraction] of the area ends. [paneFirst] says whether that first tab is the one in
     * the pane, the selected tab being the other one.
     */
    fun place(
        area: PixelRect,
        orientation: SplitOrientation,
        fraction: Float,
        divider: Int,
        paneFirst: Boolean,
    ): SplitPlaces {
        val share = fraction.coerceIn(MIN_FRACTION, MAX_FRACTION)
        val (first, line, second) = when (orientation) {
            SplitOrientation.TOP_AND_BOTTOM -> {
                val start = area.top + (area.height * share).roundToInt() - divider / 2
                Triple(
                    area.copy(bottom = start),
                    area.copy(top = start, bottom = start + divider),
                    area.copy(top = start + divider),
                )
            }
            SplitOrientation.SIDE_BY_SIDE -> {
                val start = area.left + (area.width * share).roundToInt() - divider / 2
                Triple(
                    area.copy(right = start),
                    area.copy(left = start, right = start + divider),
                    area.copy(left = start + divider),
                )
            }
        }
        return if (paneFirst) {
            SplitPlaces(main = second, pane = first, divider = line)
        } else {
            SplitPlaces(main = first, pane = second, divider = line)
        }
    }

    /**
     * The share of [area] the first tab takes when the divider between the pages is dragged to [position], a y for a
     * split shown top and bottom and an x for one side by side.
     */
    fun fractionAt(area: PixelRect, orientation: SplitOrientation, position: Float): Float {
        val share = when (orientation) {
            SplitOrientation.TOP_AND_BOTTOM -> (position - area.top) / area.height.coerceAtLeast(1)
            SplitOrientation.SIDE_BY_SIDE -> (position - area.left) / area.width.coerceAtLeast(1)
        }
        return share.coerceIn(MIN_FRACTION, MAX_FRACTION)
    }
}
