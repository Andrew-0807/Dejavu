/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.R
import org.mozilla.fenix.dejavu.workspaces.SyncedDevice
import org.mozilla.fenix.dejavu.workspaces.Workspace
import org.mozilla.fenix.dejavu.workspaces.WorkspaceState

/** A selected tab or folder as the card of the held selection shows it, see [HeldSelection]. */
internal data class HeldItem(val title: String, val url: String?, val isFolder: Boolean)

/** The folders, pinned tabs and tabs of [targets], in that order, as [HeldSelection] shows them. */
internal fun heldItems(state: WorkspaceState, targets: ActionTargets): List<HeldItem> {
    val open = targets.pinnedTabs.associateBy { it.id }
    return targets.folders.map { HeldItem(it.title, url = null, isFolder = true) } +
        targets.pins.map { pin ->
            val tab = pin.tabId?.let(open::get)
            HeldItem(pin.label(tab), tab?.content?.url ?: pin.pageUrl, isFolder = false)
        } +
        targets.tabs.map { HeldItem(state.titleOf(it), it.content.url, isFolder = false) }
}

/**
 * One of the arrows on the sides of the workspace page in selection mode, which move the selection to the workspace on
 * that side. [icon] is turned around when [mirrored], for the arrow pointing to the previous workspace with the icon
 * pointing to the next one, which also turns around in right-to-left languages.
 */
@Composable
internal fun ShiftArrow(
    @DrawableRes icon: Int,
    @StringRes description: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    mirrored: Boolean = false,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(horizontal = 4.dp)
            .size(width = 40.dp, height = 72.dp)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClickLabel = stringResource(description), onClick = onClick),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(description),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = if (mirrored) -1f else 1f },
        )
    }
}

/**
 * The selection held in the air above [workspace], the workspace of a device on the account the arrows passed it to.
 * Leaving selection mode puts it there, unless the device cannot take it ([blocked]): pinned tabs and folders stay in
 * the user's own workspaces. Covers the page below, whose rows cannot be picked meanwhile.
 */
@Suppress("LongMethod")
@Composable
internal fun HeldSelection(
    items: List<HeldItem>,
    workspace: Workspace,
    blocked: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val float by rememberInfiniteTransition(label = "held").animateFloat(
        initialValue = -FLOAT_DP,
        targetValue = FLOAT_DP,
        animationSpec = infiniteRepeatable(tween(FLOAT_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
            .pointerInput(Unit) {
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 56.dp)
                .graphicsLayer { translationY = float.dp.toPx() },
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .shadow(16.dp, shape)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(1.5.dp, if (blocked) MaterialTheme.colorScheme.error else Color.Transparent, shape)
                    .padding(16.dp),
            ) {
                items.take(MAX_HELD_ROWS).forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                    ) {
                        if (item.isFolder || item.url == null) {
                            Icon(
                                painter = painterResource(
                                    if (item.isFolder) iconsR.drawable.mozac_ic_folder_24 else iconsR.drawable.mozac_ic_tab_24,
                                ),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            SiteIcon(url = item.url, size = 20.dp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (items.size > MAX_HELD_ROWS) {
                    Text(
                        text = "+${items.size - MAX_HELD_ROWS}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 32.dp, top = 2.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val icon = when {
                        blocked -> R.drawable.dejavu_ic_no_container_24
                        workspace.device == SyncedDevice.PHONE -> iconsR.drawable.mozac_ic_device_mobile_24
                        else -> iconsR.drawable.mozac_ic_device_desktop_send_24
                    }
                    val color = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(
                            if (blocked) R.string.dejavu_shift_blocked else R.string.dejavu_shift_held,
                            workspace.name,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                    )
                }
            }
            if (blocked) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = (-10).dp)
                        .size(32.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.dejavu_ic_no_container_24),
                        contentDescription = stringResource(R.string.dejavu_shift_blocked, workspace.name),
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

private const val MAX_HELD_ROWS = 3
private const val SCRIM_ALPHA = 0.28f
private const val FLOAT_DP = 4f
private const val FLOAT_MS = 1400
