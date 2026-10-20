/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.browser.toolbar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import mozilla.components.compose.browser.toolbar.utils.AddressParts

/**
 * The scale the toolbar is drawn at, from the toolbar size setting. The address field uses it to keep its text
 * readable when the whole bar is shrunk.
 */
val LocalToolbarScale = compositionLocalOf { 1f }

/**
 * Colors of the address field.
 *
 * @property fill Fill of the pill.
 * @property hairline Outline while the field is not edited.
 * @property ring Outline while the field is edited.
 */
@Immutable
data class AddressFieldColors(val fill: Color, val hairline: Color, val ring: Color)

/** The look of the address field, shared by every bar that shows it. */
object AddressFieldDefaults {
    val Height = 48.dp
    val Shape = CircleShape
    val HorizontalPadding = 12.dp

    internal val HairlineWidth = 1.dp
    internal val RingWidth = 2.dp
    private const val HAIRLINE_ALPHA = 0.12f
    private const val TEXT_SIZE = 16f
    private const val MIN_VISIBLE_TEXT_SIZE = 13f
    private const val FOCUS_IN_MILLIS = 150
    private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** The colors of the field, from the current theme. */
    @Composable
    fun colors() =
        AddressFieldColors(
            fill = MaterialTheme.colorScheme.surfaceContainerHighest,
            hairline = MaterialTheme.colorScheme.onSurface.copy(alpha = HAIRLINE_ALPHA),
            ring = MaterialTheme.colorScheme.primary,
        )

    /** The text size of the field, larger when the toolbar is shrunk so it stays readable. */
    @Composable
    fun textSize(): TextUnit = maxOf(TEXT_SIZE, MIN_VISIBLE_TEXT_SIZE / LocalToolbarScale.current).sp

    /**
     * How far the field is into its focused look, from 0 to 1. It animates in when [focused] turns true (by default
     * as soon as the field enters edit mode) and snaps back when it turns false, as exits are faster than entries.
     * Focusing from a hardware keyboard (Ctrl+L) skips the animation. Compose applies the system animation scale, so
     * "Remove animations" makes it instant too.
     */
    @Composable
    fun rememberFocusProgress(focused: Boolean = true): Animatable<Float, AnimationVector1D> {
        val progress = remember { Animatable(0f) }
        val fromKeyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
        LaunchedEffect(focused) {
            if (!focused || fromKeyboard) {
                progress.snapTo(if (focused) 1f else 0f)
            } else {
                progress.animateTo(1f, tween(FOCUS_IN_MILLIS, easing = EaseOut))
            }
        }
        return progress
    }
}

/** The fill and outline of the field, with the focus ring drawn at [focus] (0 not focused, 1 focused). */
fun Modifier.addressFieldSurface(colors: AddressFieldColors, focus: () -> Float = { 0f }): Modifier =
    clip(AddressFieldDefaults.Shape).drawBehind {
        drawRect(colors.fill)
        val progress = focus()
        val stroke = lerp(AddressFieldDefaults.HairlineWidth, AddressFieldDefaults.RingWidth, progress).toPx()
        val inset = stroke / 2
        drawRoundRect(
            color = lerp(colors.hairline, colors.ring, progress),
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(size.height / 2 - inset),
            style = Stroke(stroke),
        )
    }

/**
 * One line of the address field while it is not edited: the page title with its domain dimmed after it when there is
 * room, or the domain with the path dimmed. The path goes first when space is tight.
 *
 * @param parts What to show, or `null` to show [hint].
 * @param hint Shown when there is no page.
 */
@Composable
fun AddressDisplayText(
    parts: AddressParts?,
    hint: String,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = AddressFieldDefaults.textSize())
    val strong = MaterialTheme.colorScheme.onSurface
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    BoxWithConstraints(modifier = modifier) {
        when {
            parts == null -> OneLine(hint, style.copy(color = dim))
            parts.title != null ->
                Row {
                    OneLine(parts.title, style.copy(color = strong), Modifier.weight(1f, fill = false))
                    if (maxWidth >= TITLE_WITH_DOMAIN_MIN_WIDTH && parts.domain.isNotEmpty()) {
                        OneLine(parts.domain, style.copy(color = dim), Modifier.padding(start = 8.dp))
                    }
                }
            else -> {
                val path = parts.path.takeIf { maxWidth >= PATH_MIN_WIDTH }.orEmpty()
                val text =
                    remember(parts.domain, path, strong, dim) {
                        buildAnnotatedString {
                            // Keeps right to left domains in address order, see HighlightedDomainUrl.
                            append(LTR_MARK)
                            withStyle(SpanStyle(color = strong, fontWeight = FontWeight.Medium)) {
                                append(parts.domain)
                            }
                            withStyle(SpanStyle(color = dim)) { append(path) }
                        }
                    }
                Text(text = text, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun OneLine(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

private const val LTR_MARK = "\u200E"
private val TITLE_WITH_DOMAIN_MIN_WIDTH = 280.dp
private val PATH_MIN_WIDTH = 200.dp
