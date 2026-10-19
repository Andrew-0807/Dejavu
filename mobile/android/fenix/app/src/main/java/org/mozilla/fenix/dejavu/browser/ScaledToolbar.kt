/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import androidx.annotation.DimenRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mozilla.fenix.ext.components

/**
 * Draws [content] at the toolbar size picked in Settings > Customize. Scaling the density shrinks heights, icons,
 * padding and text by the same factor, so a bar keeps its proportions. Wrap each bar once, as nesting scales twice.
 *
 * The heights given to the engine view are scaled the same way, in `getTopToolbarHeight` and `getBottomToolbarHeight`.
 */
@Composable
fun ScaledToolbar(content: @Composable () -> Unit) {
    val scale = if (LocalInspectionMode.current) 1f else LocalContext.current.components.settings.toolbarScale
    if (scale == 1f) {
        content()
        return
    }
    val density = LocalDensity.current
    // ponytail: text in the bars scales linearly, losing Android 14's non-linear scaling at large font sizes.
    val scaled = remember(density, scale) { Density(density.density * scale, density.fontScale) }
    CompositionLocalProvider(LocalDensity provides scaled, content = content)
}

/** Like `dimensionResource`, which divides by the scaled [LocalDensity] and so undoes [ScaledToolbar]'s shrinking. */
@Composable
fun unscaledDimensionResource(@DimenRes id: Int): Dp =
    LocalContext.current.resources.run { (getDimension(id) / displayMetrics.density).dp }
