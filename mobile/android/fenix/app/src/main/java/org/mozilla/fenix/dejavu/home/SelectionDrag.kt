/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.R
import org.mozilla.fenix.dejavu.containers.ContainerRecord
import org.mozilla.fenix.dejavu.containers.color
import org.mozilla.fenix.dejavu.sync.workspaceIconText
import org.mozilla.fenix.dejavu.workspaces.Workspace

/**
 * Tabs, pinned tabs and folders dragged on the home screen. The drag can cross workspaces: holding the finger on the
 * strip at a side of the page turns to the workspace on that side, and the items land wherever they are let go.
 *
 * @property selection What is dragged.
 * @property label Text of the floating row that follows the finger.
 * @property isFolder Whether the long-pressed row is a folder.
 * @property from The workspace the drag started in.
 * @property canLeave Whether the items can go to other workspaces: those of a device's workspace follow the device.
 * @property pointer The finger, relative to the pages.
 * @property start Where the long press happened.
 * @property moved Whether the finger moved enough to be a drag rather than a long press.
 * @property over The workspace whose page is under the finger, `null` while it is on a side strip or the pages turn.
 * @property target Where the items would land, or `null` when they cannot go there.
 * @property blocked Whether the place under the finger refuses them, like the pinned tabs of a device's workspace.
 * @property side The side strip under the finger: -1 leads to the previous workspace, 1 to the next one, 0 is none.
 */
internal data class SelectionDrag(
    val selection: Selection,
    val label: String,
    val isFolder: Boolean,
    val from: String,
    val canLeave: Boolean,
    val pointer: Offset,
    val start: Offset,
    val moved: Boolean = false,
    val over: String? = from,
    val target: DropTarget? = null,
    val blocked: Boolean = false,
    val side: Int = 0,
)

/** Where dragged items would land on a page, and whether that page refuses them there. */
internal data class PageDrop(val target: DropTarget?, val blocked: Boolean = false)

/** A workspace page as the drag on the home screen sees it, see [SelectionDrag]. */
internal interface PageDragArea {
    /** Starts dragging the row at [offset], the tab on the end side of a split view's row when [atEnd] is set. */
    fun start(offset: Offset, atEnd: Boolean): SelectionDrag?

    /** Where [drag] would land with the finger at [y], relative to the top of the page. */
    fun drop(y: Float, drag: SelectionDrag): PageDrop

    /** Scrolls the page by [step] pixels and returns how far it went. */
    suspend fun scrollBy(step: Float): Float

    /** Height of the page. */
    val height: Int
}

/**
 * The side strip at [x] on a page [width] wide whose strips are [strip] wide: 1 at the end, which leads to the next
 * workspace, -1 at the start, which leads to the previous one, else 0. The end is on the left in right-to-left
 * languages.
 */
internal fun sideAt(x: Float, width: Float, strip: Float, isRtl: Boolean): Int {
    val onLeft = x < strip
    val onRight = x > width - strip
    return when {
        onRight -> if (isRtl) -1 else 1
        onLeft -> if (isRtl) 1 else -1
        else -> 0
    }
}

/**
 * The row that follows the finger while items are dragged, centered on it. It shows a stop sign when the place under
 * the finger refuses them ([blocked]).
 */
@Composable
internal fun DragGhost(label: String, isFolder: Boolean, pointer: Offset, blocked: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    val error = MaterialTheme.colorScheme.error
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .layout { measurable, constraints ->
                val ghost = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val x = (pointer.x - ghost.width / 2f).roundToInt()
                        .coerceIn(0, (constraints.maxWidth - ghost.width).coerceAtLeast(0))
                    ghost.place(x, (pointer.y - ghost.height / 2f).roundToInt())
                }
            }
            .widthIn(max = 280.dp)
            .shadow(8.dp, shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape)
            .border(1.5.dp, if (blocked) error else Color.Transparent, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(
            painter = painterResource(
                when {
                    blocked -> R.drawable.dejavu_ic_no_container_24
                    isFolder -> iconsR.drawable.mozac_ic_folder_24
                    else -> iconsR.drawable.mozac_ic_tab_24
                },
            ),
            contentDescription = null,
            tint = if (blocked) error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The strip along a side of the page while items are dragged, which turns to [workspace] once the finger rests on it.
 * It points to the start of the page unless [atEnd], and stands out while the finger is on it ([active]).
 */
@Composable
internal fun SideStrip(
    workspace: Workspace,
    container: ContainerRecord?,
    active: Boolean,
    atEnd: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 8.dp, horizontal = 2.dp)
            .width(SideStripWidth - 4.dp)
            .clip(shape)
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = STRIP_ALPHA)
                },
            ),
    ) {
        Icon(
            painter = painterResource(iconsR.drawable.mozac_ic_chevron_right_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = if (atEnd) 1f else -1f },
        )
        Spacer(Modifier.size(8.dp))
        val icon = workspaceIconText(workspace.icon)
        if (icon != null) {
            Text(text = icon, fontSize = 17.sp)
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(container?.color?.color ?: MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}

/** Width of the strips along the sides of the page while items are dragged. */
internal val SideStripWidth = 44.dp

private const val STRIP_ALPHA = 0.92f
