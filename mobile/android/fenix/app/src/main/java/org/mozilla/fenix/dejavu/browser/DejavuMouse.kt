/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.snackbar.Snackbar
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.feature.contextmenu.R as contextMenuR
import org.json.JSONObject
import org.mozilla.fenix.ext.requireComponents
import org.mozilla.fenix.snackbar.FenixSnackbarDelegate

/**
 * Mouse support the engine lacks: a middle-click on a link opens it in a background tab, and hovering a link shows its
 * address in a pill at the bottom-left of the page. A built-in extension reports both. Touch input is ignored.
 */
object DejavuMouse {
    private const val ID = "dejavu-mouse@dejavu.app"
    private const val URL = "resource://android/assets/extensions/dejavu_mouse/"
    private const val NAME = "dejavuMouse"

    private var onHover: ((String) -> Unit)? = null
    private var onOpen: ((String) -> Unit)? = null
    private var installed = false

    fun install(fragment: Fragment, browserLayout: CoordinatorLayout, snackbarContainer: View) {
        val components = fragment.requireComponents
        if (!components.settings.isMouseSupportEnabled) return
        val context = fragment.requireContext()
        val density = context.resources.displayMetrics.density
        val pill = TextView(context).apply {
            visibility = View.GONE
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT
            setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
            background = GradientDrawable().apply {
                setColor(0xE6202020.toInt())
                cornerRadius = 8 * density
            }
            layoutParams = CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins((8 * density).toInt(), 0, 0, (80 * density).toInt())
            }
        }
        browserLayout.addView(pill)

        fragment.viewLifecycleOwner.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    onHover = { url ->
                        pill.text = url
                        pill.visibility = if (url.isEmpty()) View.GONE else View.VISIBLE
                    }
                    onOpen = { url -> openInBackground(fragment, snackbarContainer, url) }
                }

                override fun onStop(owner: LifecycleOwner) {
                    onHover = null
                    onOpen = null
                    pill.visibility = View.GONE
                }

                override fun onDestroy(owner: LifecycleOwner) = browserLayout.removeView(pill)
            },
        )

        if (installed) return
        installed = true
        components.core.engine.installBuiltInWebExtension(
            ID,
            URL,
            onSuccess = { it.registerBackgroundMessageHandler(NAME, handler) },
            onError = { installed = false },
        )
    }

    private val handler = object : MessageHandler {
        override fun onMessage(message: Any, source: EngineSession?): Any? {
            val json = message as? JSONObject ?: return null
            val url = json.optString("url")
            when (json.optString("type")) {
                "hover" -> onHover?.invoke(url)
                "open" -> if (url.isNotEmpty()) onOpen?.invoke(url)
            }
            return null
        }
    }

    private fun openInBackground(fragment: Fragment, snackbarContainer: View, url: String) {
        val components = fragment.requireComponents
        val tab = components.core.store.state.selectedTab ?: return
        val tabId = components.useCases.tabsUseCases.addTab(
            url = url,
            selectTab = false,
            startLoading = true,
            parentId = tab.id,
            contextId = tab.contextId,
            private = tab.content.private,
            textDirectiveUserActivation = true,
        )
        FenixSnackbarDelegate(snackbarContainer).show(
            snackBarParentView = snackbarContainer,
            text = fragment.getString(contextMenuR.string.mozac_feature_contextmenu_snackbar_new_tab_opened),
            duration = Snackbar.LENGTH_LONG,
            action = fragment.getString(contextMenuR.string.mozac_feature_contextmenu_snackbar_action_switch),
            listener = { components.useCases.tabsUseCases.selectTab(tabId) },
        )
    }
}
