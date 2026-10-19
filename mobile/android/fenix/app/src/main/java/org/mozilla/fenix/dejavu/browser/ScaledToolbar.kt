/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import android.content.SharedPreferences
import androidx.annotation.DimenRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.components

/**
 * Draws [content] at the toolbar size picked in Settings > Customize. The bar is measured at full size in a space
 * larger by 1 / scale, then drawn shrunk by a layer, so heights, icons, padding and text keep their proportions and
 * touches still land where they are drawn. The density stays as it is, so popups, menus and dropdowns opened from the
 * bar, which take the density but not the layer, keep their normal size and open next to the shrunk bar. Wrap each
 * bar once, as nesting scales twice. The size follows the setting while the bar is shown.
 *
 * The heights given to the engine view are scaled the same way, in `getTopToolbarHeight` and `getBottomToolbarHeight`.
 */
@Composable
fun ScaledToolbar(content: @Composable () -> Unit) {
    val scale = if (LocalInspectionMode.current) 1f else observedToolbarScale()
    if (scale == 1f) {
        content()
        return
    }
    Layout(content = content) { measurables, constraints ->
        fun unscaled(px: Int) = if (px == Constraints.Infinity) px else (px / scale).roundToInt()
        val inner = Constraints(
            minWidth = unscaled(constraints.minWidth),
            maxWidth = unscaled(constraints.maxWidth),
            minHeight = unscaled(constraints.minHeight),
            maxHeight = unscaled(constraints.maxHeight),
        )
        val placeables = measurables.map { it.measure(inner) }
        val width = constraints.constrainWidth(((placeables.maxOfOrNull { it.width } ?: 0) * scale).roundToInt())
        val height = constraints.constrainHeight(((placeables.maxOfOrNull { it.height } ?: 0) * scale).roundToInt())
        layout(width, height) {
            placeables.forEach {
                it.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }
}

/** Settings' toolbar scale, updated when the user changes the toolbar size. */
@Composable
private fun observedToolbarScale(): Float {
    val context = LocalContext.current
    val settings = context.components.settings
    var scale by remember(settings) { mutableFloatStateOf(settings.toolbarScale) }
    DisposableEffect(settings) {
        val key = context.getString(R.string.pref_key_dejavu_toolbar_size)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key) scale = settings.toolbarScale
        }
        settings.preferences.registerOnSharedPreferenceChangeListener(listener)
        // A change made while this bar was not shown.
        scale = settings.toolbarScale
        onDispose { settings.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return scale
}

/** The dimension [id] in dp from the display density, which [ScaledToolbar]'s layer shrinks like everything else. */
@Composable
fun unscaledDimensionResource(@DimenRes id: Int): Dp =
    LocalContext.current.resources.run { (getDimension(id) / displayMetrics.density).dp }
