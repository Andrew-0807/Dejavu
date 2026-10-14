/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.R
import org.mozilla.fenix.compose.list.SwitchListItem
import org.mozilla.fenix.dejavu.menu.MoreMenuEntry
import org.mozilla.fenix.dejavu.menu.MoreMenuLayout
import org.mozilla.fenix.dejavu.ui.DejavuDropdownMenu
import org.mozilla.fenix.dejavu.ui.DejavuMenuItem

/** Where a button dragged in the editor of the "More" menu comes from. */
internal sealed interface MenuDragSource {
    val key: String

    /** A new button, from the list of every button. */
    data class New(override val key: String) : MenuDragSource

    /** The button at [index] of row [row]. */
    data class Placed(override val key: String, val row: Int, val index: Int) : MenuDragSource
}

/**
 * Changes to the rows of the "More" menu while they are edited. A row holds up to [MoreMenuLayout.MAX_PER_ROW] buttons
 * from its start, or a full row button alone, like [MoreMenuLayout] lays them out, except that it can be empty until a
 * button is put into it. A button can be in the menu more than once.
 */
internal object MenuRowsEditing {
    /**
     * The rows once the button of [source] is dropped on block [slot] of row [row], or `null` when it cannot go
     * there: a full row button only goes into an empty row, and nothing goes next to it. Dropped on a button, a button
     * goes before it. On a full row it takes that button's place: a new button replaces it, a moved one swaps with it.
     */
    fun drop(rows: List<List<String>>, source: MenuDragSource, row: Int, slot: Int): List<List<String>>? {
        val target = rows.getOrNull(row) ?: return null
        val placed = source as? MenuDragSource.Placed
        return when {
            MoreMenuLayout.isFullRow(source.key) -> when {
                placed?.row == row -> rows
                target.isEmpty() -> rows.without(placed).withRow(row, listOf(source.key))
                else -> null
            }
            target.any(MoreMenuLayout::isFullRow) -> null
            placed?.row == row -> {
                val keys = target.toMutableList()
                val moved = keys.removeAt(placed.index)
                keys.add(minOf(slot, keys.size), moved)
                rows.withRow(row, keys)
            }
            target.size < MoreMenuLayout.MAX_PER_ROW ->
                rows.without(placed).withRow(row, target.toMutableList().apply { add(minOf(slot, size), source.key) })
            else -> {
                val replaced = slot.coerceIn(target.indices)
                val updated = rows.withRow(row, target.withKey(replaced, source.key))
                if (placed == null) {
                    updated
                } else {
                    updated.withRow(placed.row, rows[placed.row].withKey(placed.index, target[replaced]))
                }
            }
        }
    }

    /** The blocks of row [row] that the button of [source] takes when it is dropped on block [slot]. */
    fun landing(rows: List<List<String>>, source: MenuDragSource, row: Int, slot: Int): IntRange {
        val target = rows.getOrNull(row).orEmpty()
        val index = when {
            MoreMenuLayout.isFullRow(source.key) -> return 0 until MoreMenuLayout.MAX_PER_ROW
            (source as? MenuDragSource.Placed)?.row == row -> minOf(slot, target.size - 1)
            target.size < MoreMenuLayout.MAX_PER_ROW -> minOf(slot, target.size)
            else -> slot
        }
        return index..index
    }

    /** The rows without the button at [index] of row [row], which stays even when it is left empty. */
    fun remove(rows: List<List<String>>, row: Int, index: Int): List<List<String>> =
        rows.withRow(row, rows[row].filterIndexed { i, _ -> i != index })

    /** The rows with [key] after the last button, in a new row when the last row has no room for it. */
    fun append(rows: List<List<String>>, key: String): List<List<String>> {
        val last = rows.lastOrNull()
        val fits = last != null && if (MoreMenuLayout.isFullRow(key)) {
            last.isEmpty()
        } else {
            last.size < MoreMenuLayout.MAX_PER_ROW && last.none(MoreMenuLayout::isFullRow)
        }
        return if (fits) rows.withRow(rows.lastIndex, rows.last() + key) else rows + listOf(listOf(key))
    }

    private fun List<List<String>>.withRow(index: Int, row: List<String>) =
        mapIndexed { i, old -> if (i == index) row else old }

    private fun List<String>.withKey(index: Int, key: String) = mapIndexed { i, old -> if (i == index) key else old }

    private fun List<List<String>>.without(placed: MenuDragSource.Placed?) =
        if (placed == null) this else remove(this, placed.row, placed.index)
}

private val BlockHeight = 64.dp
private val BlockShape = RoundedCornerShape(14.dp)
private val BlockGap = 6.dp
private val FloatingWidth = 76.dp
private val AutoScrollEdge = 40.dp
private const val AUTO_SCROLL_STEP = 12f
private const val AUTO_SCROLL_FRAME_MS = 16L
private const val DRAGGED_ALPHA = 0.35f
private const val REMOVE_ALPHA = 0.85f

/** A button being dragged: where it comes from, where the finger is in root coordinates, and where it would land. */
private data class MenuDrag(val source: MenuDragSource, val pointer: Offset, val target: MenuDropTarget? = null)

/** Where a dragged button would land. */
private sealed interface MenuDropTarget {
    /** Block [slot] of row [row]. */
    data class Block(val row: Int, val slot: Int) : MenuDropTarget

    /** Back among every button, which takes it out of the menu. */
    data object Remove : MenuDropTarget
}

/** The layout of a dragged button, to turn finger positions on it into root ones. */
private class Anchor {
    var coordinates: LayoutCoordinates? = null
}

/**
 * Edits the rows of the "More" menu on a screen split in two: the rows on top, each with the same blocks, and every
 * button below. A button is held and dragged into a block of a row, from below or from another block, and dragged back
 * down it leaves the menu. Tapping a button below adds it after the last one; tapping one in a row offers to remove it.
 */
@Suppress("LongMethod", "CognitiveComplexMethod", "CyclomaticComplexMethod")
@Composable
internal fun MoreMenuRowsEditor(settings: DejavuSettings, modifier: Modifier = Modifier) {
    val customActions by settings.customActions.collectAsState()
    val editHidden by settings.moreMenuEditHidden.collectAsState()
    var rows by remember {
        mutableStateOf(
            settings.moreMenuRows.value.map { row -> row.filter { MoreMenuLayout.entryOf(it, customActions) != null } },
        )
    }
    val palette = remember(customActions) { MoreMenuLayout.available(customActions) }
    var drag by remember { mutableStateOf<MenuDrag?>(null) }
    var menuFor by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val blocks = remember { mutableStateMapOf<Pair<Int, Int>, Rect>() }
    var rowsArea by remember { mutableStateOf(Rect.Zero) }
    var paletteArea by remember { mutableStateOf(Rect.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val rowsScroll = rememberScrollState()
    val haptics = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { AutoScrollEdge.toPx() }
    val current = drag
    val target = current?.target as? MenuDropTarget.Block
    val dragged = current?.source as? MenuDragSource.Placed
    val dragging = current != null

    fun update(updated: List<List<String>>) {
        if (updated == rows) return
        rows = updated
        settings.setMoreMenuRows(updated)
    }

    fun targetAt(source: MenuDragSource, pointer: Offset): MenuDropTarget? {
        if (paletteArea.contains(pointer)) return MenuDropTarget.Remove.takeIf { source is MenuDragSource.Placed }
        val (row, slot) = blocks.entries.firstOrNull { it.value.contains(pointer) }?.key ?: return null
        return MenuDropTarget.Block(row, slot).takeIf { MenuRowsEditing.drop(rows, source, row, slot) != null }
    }

    fun startDrag(source: MenuDragSource, pointer: Offset) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        menuFor = null
        drag = MenuDrag(source, pointer, targetAt(source, pointer))
    }

    fun moveDrag(pointer: Offset) {
        val moving = drag ?: return
        drag = moving.copy(pointer = pointer, target = targetAt(moving.source, pointer))
    }

    fun endDrag() {
        val ended = drag ?: return
        drag = null
        when (val landed = ended.target) {
            is MenuDropTarget.Block -> MenuRowsEditing.drop(rows, ended.source, landed.row, landed.slot)?.let(::update)
            MenuDropTarget.Remove -> (ended.source as? MenuDragSource.Placed)?.let {
                update(MenuRowsEditing.remove(rows, it.row, it.index))
            }
            null -> Unit
        }
    }

    // Rows out of sight scroll into it while a button is held near the top or the bottom of the rows.
    LaunchedEffect(dragging) {
        while (dragging) {
            val pointer = drag?.pointer ?: break
            val step = when {
                pointer.x !in rowsArea.left..rowsArea.right || pointer.y !in rowsArea.top..rowsArea.bottom -> 0f
                pointer.y < rowsArea.top + edge -> -AUTO_SCROLL_STEP
                pointer.y > rowsArea.bottom - edge -> AUTO_SCROLL_STEP
                else -> 0f
            }
            if (step != 0f) rowsScroll.scrollBy(step)
            val latest = drag ?: break
            val updated = targetAt(latest.source, latest.pointer)
            if (updated != latest.target) drag = latest.copy(target = updated)
            delay(AUTO_SCROLL_FRAME_MS)
        }
    }

    Box(modifier = modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(BlockGap),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { rowsArea = it.boundsInRoot() }
                    .verticalScroll(rowsScroll, enabled = !dragging)
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
            ) {
                rows.forEachIndexed { row, keys ->
                    val landing = if (current != null && target != null && target.row == row) {
                        MenuRowsEditing.landing(rows, current.source, row, target.slot)
                    } else {
                        null
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(BlockGap), modifier = Modifier.weight(1f)) {
                            val wide = keys.singleOrNull()?.takeIf(MoreMenuLayout::isFullRow)
                            val slots = if (wide != null) listOf(0) else (0 until MoreMenuLayout.MAX_PER_ROW).toList()
                            slots.forEach { slot ->
                                val key = keys.getOrNull(slot)
                                val entry = key?.let { MoreMenuLayout.entryOf(it, customActions) }
                                DisposableEffect(row, slot) { onDispose { blocks.remove(row to slot) } }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .onGloballyPositioned { blocks[row to slot] = it.boundsInRoot() },
                                ) {
                                    val highlighted = landing != null && (wide != null || slot in landing)
                                    if (key != null && entry != null) {
                                        DraggableBlock(
                                            onDragStart = { startDrag(MenuDragSource.Placed(key, row, slot), it) },
                                            onDrag = ::moveDrag,
                                            onDragEnd = ::endDrag,
                                            onDragCancel = { drag = null },
                                            onClick = { if (drag == null) menuFor = row to slot },
                                        ) {
                                            BlockContent(
                                                entry = entry,
                                                highlighted = highlighted,
                                                dimmed = dragged?.row == row && dragged.index == slot,
                                                wide = wide != null,
                                            )
                                        }
                                        DejavuDropdownMenu(
                                            expanded = menuFor == row to slot,
                                            onDismissRequest = { menuFor = null },
                                        ) {
                                            DejavuMenuItem(
                                                text = stringResource(R.string.dejavu_more_menu_remove),
                                                onClick = {
                                                    menuFor = null
                                                    update(MenuRowsEditing.remove(rows, row, slot))
                                                },
                                            )
                                        }
                                    } else {
                                        BlockContent(entry = null, highlighted = highlighted)
                                    }
                                }
                            }
                        }
                        IconButton(
                            onClick = { update(rows.filterIndexed { index, _ -> index != row }) },
                            enabled = !dragging,
                        ) {
                            Icon(
                                painter = painterResource(iconsR.drawable.mozac_ic_cross_24),
                                contentDescription = stringResource(R.string.dejavu_more_menu_delete_row),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 6.dp, end = 12.dp),
                ) {
                    OutlinedButton(onClick = { update(rows + listOf(emptyList())) }) {
                        Text(stringResource(R.string.dejavu_more_menu_add_row))
                    }
                    TextButton(
                        onClick = {
                            settings.resetMoreMenu()
                            rows = MoreMenuLayout.DEFAULT
                        },
                    ) {
                        Text(stringResource(R.string.dejavu_more_menu_reset))
                    }
                }
                SwitchListItem(
                    label = stringResource(R.string.dejavu_more_menu_hide_edit),
                    description = stringResource(R.string.dejavu_more_menu_hide_edit_summary),
                    maxDescriptionLines = 2,
                    checked = editHidden,
                    showSwitchAfter = true,
                    modifier = Modifier
                        .padding(top = 6.dp, end = 12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    onClick = settings::setMoreMenuEditHidden,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { paletteArea = it.boundsInRoot() },
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(BlockGap),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState(), enabled = !dragging)
                        .padding(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.dejavu_more_menu_drag_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    palette.chunked(MoreMenuLayout.MAX_PER_ROW).forEach { chunk ->
                        Row(horizontalArrangement = Arrangement.spacedBy(BlockGap)) {
                            chunk.forEach { entry ->
                                DraggableBlock(
                                    onDragStart = { startDrag(MenuDragSource.New(entry.key), it) },
                                    onDrag = ::moveDrag,
                                    onDragEnd = ::endDrag,
                                    onDragCancel = { drag = null },
                                    onClick = { if (drag == null) update(MenuRowsEditing.append(rows, entry.key)) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    BlockContent(entry = entry)
                                }
                            }
                            repeat(MoreMenuLayout.MAX_PER_ROW - chunk.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                if (dragged != null) RemoveArea(active = current.target == MenuDropTarget.Remove)
            }
        }
        if (current != null) {
            MoreMenuLayout.entryOf(current.source.key, customActions)?.let { entry ->
                FloatingBlock(entry = entry, position = current.pointer - origin)
            }
        }
    }
}

/** A button that a long press starts dragging, with finger positions in root coordinates. A tap runs [onClick]. */
@Suppress("LongParameterList")
@Composable
private fun DraggableBlock(
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val anchor = remember { Anchor() }
    val start by rememberUpdatedState(onDragStart)
    val move by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val cancel by rememberUpdatedState(onDragCancel)
    Box(
        modifier = modifier
            .onGloballyPositioned { anchor.coordinates = it }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> anchor.coordinates?.let { start(it.localToRoot(offset)) } },
                    onDrag = { change, _ ->
                        change.consume()
                        anchor.coordinates?.let { move(it.localToRoot(change.position)) }
                    },
                    onDragEnd = { end() },
                    onDragCancel = { cancel() },
                )
            }
            .clickable(onClick = onClick),
    ) {
        content()
    }
}

/**
 * A block of a row, or of the list of every button: [entry]'s icon and name, or an empty outlined block without one. A
 * [wide] block holds a full row button, with its name next to its icon.
 */
@Composable
private fun BlockContent(
    entry: MoreMenuEntry?,
    highlighted: Boolean = false,
    dimmed: Boolean = false,
    wide: Boolean = false,
) {
    val border = when {
        highlighted -> MaterialTheme.colorScheme.primary
        entry == null -> MaterialTheme.colorScheme.outlineVariant
        else -> Color.Transparent
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(BlockHeight)
            .alpha(if (dimmed) DRAGGED_ALPHA else 1f)
            .clip(BlockShape)
            .background(if (entry == null) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(if (highlighted) 2.dp else 1.dp, border, BlockShape)
            .padding(horizontal = 4.dp),
    ) {
        when {
            entry == null -> Unit
            wide -> Row(verticalAlignment = Alignment.CenterVertically) {
                BlockIcon(entry)
                Spacer(Modifier.width(10.dp))
                BlockLabel(entry)
            }
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BlockIcon(entry)
                Spacer(Modifier.height(4.dp))
                BlockLabel(entry)
            }
        }
    }
}

@Composable
private fun BlockIcon(entry: MoreMenuEntry) {
    Icon(
        painter = painterResource(entry.icon),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(22.dp),
    )
}

@Composable
private fun BlockLabel(entry: MoreMenuEntry) {
    Text(
        text = entry.label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Covers the list of every button while a button of a row is dragged, where dropping it takes it out of the menu. */
@Composable
private fun RemoveArea(active: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = if (active) 1f else REMOVE_ALPHA)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(iconsR.drawable.mozac_ic_delete_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.dejavu_more_menu_remove),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/** The dragged button, under the finger at [position] in the editor's coordinates. */
@Composable
private fun FloatingBlock(entry: MoreMenuEntry, position: Offset) {
    val density = LocalDensity.current
    val halfWidth = with(density) { FloatingWidth.toPx() } / 2
    val halfHeight = with(density) { BlockHeight.toPx() } / 2
    Box(
        modifier = Modifier
            .offset { IntOffset((position.x - halfWidth).roundToInt(), (position.y - halfHeight).roundToInt()) }
            .width(FloatingWidth)
            .shadow(8.dp, BlockShape)
            .clip(BlockShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        BlockContent(entry = entry)
    }
}
