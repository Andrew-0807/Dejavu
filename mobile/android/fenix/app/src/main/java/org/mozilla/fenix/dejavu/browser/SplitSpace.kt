/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import android.content.Context
import android.content.res.Configuration
import org.mozilla.fenix.dejavu.workspaces.SplitOrientation

/**
 * Which ways a window has room to split the browser between two tabs: next to each other when each page gets
 * [MIN_PAGE_WIDTH_DP] of width, one above the other when each page gets [MIN_PAGE_HEIGHT_DP] of height besides the
 * bars. Phones held upright split top and bottom, phones on their side split side by side, and tablets can do both.
 */
internal object SplitSpace {
    const val MIN_PAGE_WIDTH_DP = 320
    const val MIN_PAGE_HEIGHT_DP = 200

    /** The height of the address bar and the actions bar, which both pages share. */
    const val BARS_HEIGHT_DP = 160

    /** The ways a window [widthDp] wide and [heightDp] high can split, the one along its long side first. */
    fun supported(widthDp: Int, heightDp: Int): List<SplitOrientation> {
        val ways = buildList {
            if (widthDp >= 2 * MIN_PAGE_WIDTH_DP) add(SplitOrientation.SIDE_BY_SIDE)
            if (heightDp - BARS_HEIGHT_DP >= 2 * MIN_PAGE_HEIGHT_DP) add(SplitOrientation.TOP_AND_BOTTOM)
        }
        return if (widthDp >= heightDp) ways else ways.reversed()
    }

    fun supported(configuration: Configuration): List<SplitOrientation> =
        supported(configuration.screenWidthDp, configuration.screenHeightDp)

    fun supported(context: Context): List<SplitOrientation> = supported(context.resources.configuration)

    /**
     * How a split made [orientation] shows in a window that can split the [supported] ways: as it was made when the
     * window has room for it, else the way it has room for. A window too small for either keeps it as it was made.
     */
    fun shown(orientation: SplitOrientation, supported: List<SplitOrientation>): SplitOrientation =
        if (orientation in supported || supported.isEmpty()) orientation else supported.first()

    /** The way a new split takes when the user does not choose: along the window's long side when it has room. */
    fun preferred(supported: List<SplitOrientation>): SplitOrientation =
        supported.firstOrNull() ?: SplitOrientation.TOP_AND_BOTTOM
}
