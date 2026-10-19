/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.shortcuts

import android.os.StrictMode
import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import mozilla.components.browser.state.selector.getNormalOrPrivateTabs
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarStore
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.R
import org.mozilla.fenix.dejavu.newtab.NewTabPrompt
import org.mozilla.fenix.dejavu.settings.DejavuSettings
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.search.AddressBarEnterPressed

/** A key and the modifiers held with it, as [KeyEvent.META_CTRL_ON], [KeyEvent.META_SHIFT_ON] and [KeyEvent.META_ALT_ON]. */
data class KeyCombo(val keyCode: Int, val meta: Int) {
    val hasCtrlOrAlt: Boolean get() = meta and (KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON) != 0

    fun encode() = "$meta:$keyCode"

    fun label(): String = buildString {
        if (meta and KeyEvent.META_CTRL_ON != 0) append("Ctrl+")
        if (meta and KeyEvent.META_ALT_ON != 0) append("Alt+")
        if (meta and KeyEvent.META_SHIFT_ON != 0) append("Shift+")
        append(KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_"))
    }

    companion object {
        private const val MODIFIERS = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_ALT_ON

        /** The combo [event] presses, or `null` when only a modifier key is down. */
        fun of(event: KeyEvent): KeyCombo? = if (KeyEvent.isModifierKey(event.keyCode)) {
            null
        } else {
            KeyCombo(event.keyCode, event.metaState and MODIFIERS)
        }

        fun parse(text: String): KeyCombo? {
            val parts = text.split(":")
            val meta = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val key = parts.getOrNull(1)?.toIntOrNull() ?: return null
            return KeyCombo(key, meta)
        }
    }
}

private const val CTRL = KeyEvent.META_CTRL_ON
private const val SHIFT = KeyEvent.META_SHIFT_ON

/** Everything a keyboard shortcut can do, with the key it is on until the user remaps it. */
enum class ShortcutAction(
    val key: String,
    @param:StringRes val label: Int,
    val default: KeyCombo,
    val labelArg: Int = 0,
) {
    NEXT_TAB("next_tab", R.string.dejavu_shortcut_next_tab, KeyCombo(KeyEvent.KEYCODE_TAB, CTRL)),
    PREVIOUS_TAB("previous_tab", R.string.dejavu_shortcut_previous_tab, KeyCombo(KeyEvent.KEYCODE_TAB, CTRL or SHIFT)),
    NEW_TAB("new_tab", R.string.dejavu_shortcut_new_tab, KeyCombo(KeyEvent.KEYCODE_T, CTRL)),
    CLOSE_TAB("close_tab", R.string.dejavu_shortcut_close_tab, KeyCombo(KeyEvent.KEYCODE_W, CTRL)),
    RESTORE_TAB("restore_tab", R.string.dejavu_shortcut_restore_tab, KeyCombo(KeyEvent.KEYCODE_T, CTRL or SHIFT)),
    ADDRESS_BAR("address_bar", R.string.dejavu_shortcut_address_bar, KeyCombo(KeyEvent.KEYCODE_L, CTRL)),
    RELOAD("reload", R.string.dejavu_shortcut_reload, KeyCombo(KeyEvent.KEYCODE_R, CTRL)),
    TAB_1("tab_1", R.string.dejavu_shortcut_tab_n, KeyCombo(KeyEvent.KEYCODE_1, CTRL), 1),
    TAB_2("tab_2", R.string.dejavu_shortcut_tab_n, KeyCombo(KeyEvent.KEYCODE_2, CTRL), 2),
    TAB_3("tab_3", R.string.dejavu_shortcut_tab_n, KeyCombo(KeyEvent.KEYCODE_3, CTRL), 3),
    TAB_4("tab_4", R.string.dejavu_shortcut_tab_n, KeyCombo(KeyEvent.KEYCODE_4, CTRL), 4),
    TAB_5("tab_5", R.string.dejavu_shortcut_tab_n, KeyCombo(KeyEvent.KEYCODE_5, CTRL), 5),
    TAB_6("tab_6", R.string.dejavu_shortcut_tab_n, KeyCombo(KeyEvent.KEYCODE_6, CTRL), 6),
    TAB_7("tab_7", R.string.dejavu_shortcut_tab_n, KeyCombo(KeyEvent.KEYCODE_7, CTRL), 7),
    TAB_8("tab_8", R.string.dejavu_shortcut_tab_n, KeyCombo(KeyEvent.KEYCODE_8, CTRL), 8),
    LAST_TAB("last_tab", R.string.dejavu_shortcut_last_tab, KeyCombo(KeyEvent.KEYCODE_9, CTRL)),
}

object KeyboardShortcuts {
    /** Set by the browser screen to focus its address bar. */
    var focusAddressBar: (() -> Unit)? = null

    /** The user's changed shortcuts, by action key. */
    fun saved(activity: HomeActivity): Map<String, String> =
        activity.components.strictMode.allowViolation(StrictMode::allowThreadDiskReads) {
            DejavuSettings.get(activity).shortcuts.value
        }

    /** The combo of [action] given the user's [saved] choices, or `null` when cleared. A saved "" means cleared. */
    fun comboOf(action: ShortcutAction, saved: Map<String, String>): KeyCombo? = when (val text = saved[action.key]) {
        null -> action.default
        "" -> null
        else -> KeyCombo.parse(text) ?: action.default
    }

    fun actionFor(combo: KeyCombo, saved: Map<String, String>): ShortcutAction? =
        ShortcutAction.entries.firstOrNull { comboOf(it, saved) == combo }

    /** Does what [action] says. Returns whether it did something. */
    fun run(activity: HomeActivity, action: ShortcutAction): Boolean {
        val components = activity.components
        val state = components.core.store.state
        val tabs = state.getNormalOrPrivateTabs(activity.browsingModeManager.mode.isPrivate)
        val selectedId = state.selectedTab?.id
        val index = tabs.indexOfFirst { it.id == selectedId }
        val tabsUseCases = components.useCases.tabsUseCases
        fun select(target: Int?) = target?.let { tabs.getOrNull(it) }?.let { tabsUseCases.selectTab(it.id) } != null
        return when (action) {
            ShortcutAction.NEXT_TAB -> tabs.size > 1 && select((index + 1) % tabs.size)
            ShortcutAction.PREVIOUS_TAB -> tabs.size > 1 && select((index - 1 + tabs.size) % tabs.size)
            ShortcutAction.LAST_TAB -> select(tabs.lastIndex)
            ShortcutAction.CLOSE_TAB -> selectedId?.let { tabsUseCases.removeTab(it) } != null
            ShortcutAction.RESTORE_TAB -> {
                tabsUseCases.undo.invoke()
                true
            }
            ShortcutAction.RELOAD -> selectedId?.let { components.useCases.sessionUseCases.reload(it) } != null
            ShortcutAction.NEW_TAB -> {
                NewTabPrompt.show(activity)
                true
            }
            ShortcutAction.ADDRESS_BAR -> focusAddressBar?.invoke() != null
            else -> select(action.labelArg - 1)
        }
    }
}

/**
 * Tells [store] whether Shift is held on each Enter in the address bar inside this node, before the field commits
 * the address, so Shift+Enter opens it in a new tab.
 */
fun Modifier.reportShiftEnter(store: BrowserToolbarStore): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
        store.dispatch(AddressBarEnterPressed(event.isShiftPressed))
    }
    false
}
