/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.utils.Settings.Companion.MAX_TOOLBAR_SIZE_PERCENT
import org.mozilla.fenix.utils.Settings.Companion.MIN_TOOLBAR_SIZE_PERCENT

private const val TOOLBAR_SIZE_STEP = 5

/**
 * A slider for the size of the toolbars, saved as [org.mozilla.fenix.utils.Settings.toolbarSizePercent]. Its title is
 * the header of its category. The bars use the new size when the browser or home screen shows again.
 *
 * @param context The context used to initialize the preference.
 * @param attrs Optional attribute set applied from XML.
 */
class ToolbarSizePreference(
    context: Context,
    attrs: AttributeSet? = null,
) : ComposePreference(context, attrs) {

    @Composable
    override fun Content() {
        val settings = context.components.settings
        var percent by remember {
            mutableIntStateOf(settings.toolbarSizePercent.coerceIn(MIN_TOOLBAR_SIZE_PERCENT, MAX_TOOLBAR_SIZE_PERCENT))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.dejavu_settings_toolbar_size_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = percent.toFloat(),
                    onValueChange = { percent = it.roundToInt() },
                    onValueChangeFinished = { settings.toolbarSizePercent = percent },
                    valueRange = MIN_TOOLBAR_SIZE_PERCENT.toFloat()..MAX_TOOLBAR_SIZE_PERCENT.toFloat(),
                    steps = (MAX_TOOLBAR_SIZE_PERCENT - MIN_TOOLBAR_SIZE_PERCENT) / TOOLBAR_SIZE_STEP - 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.dejavu_settings_toolbar_size_value, percent),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
