/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.menu

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.concept.engine.EngineView
import org.mozilla.fenix.R
import org.mozilla.fenix.components.Components
import org.mozilla.fenix.ext.asActivity

private const val FOLDER = "Dejavu"

/** Saves the whole page of the shown tab to Pictures/Dejavu, then offers to open or share it. */
internal fun captureFullPageScreenshot(context: Context, components: Components) {
    val activity = context.asActivity() ?: return
    // ponytail: first engine view of the window, so in split view the main pane is captured
    val engineView = activity.window.decorView.findEngineView() ?: return
    engineView.captureFullPage { bitmap ->
        if (bitmap == null) {
            Toast.makeText(activity, R.string.dejavu_screenshot_failed, Toast.LENGTH_SHORT).show()
            return@captureFullPage
        }
        components.applicationScope.launch(Dispatchers.Main) {
            val uri = withContext(Dispatchers.IO) { save(activity, bitmap) }
            if (uri == null) {
                Toast.makeText(activity, R.string.dejavu_screenshot_failed, Toast.LENGTH_SHORT).show()
            } else if (!activity.isFinishing) {
                showSaved(activity, uri)
            }
        }
    }
}

private fun View.findEngineView(): EngineView? = when {
    this is EngineView && isShown -> this
    this is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findEngineView() }
    else -> null
}

private fun save(context: Context, bitmap: Bitmap): Uri? {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Dejavu-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    val saved = runCatching {
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
    }.getOrDefault(false)
    if (!saved) resolver.delete(uri, null, null)
    return uri.takeIf { saved }
}

private fun showSaved(activity: Context, uri: Uri) {
    AlertDialog.Builder(activity)
        .setMessage(activity.getString(R.string.dejavu_screenshot_saved, "Pictures/$FOLDER"))
        .setPositiveButton(R.string.dejavu_screenshot_open) { _, _ ->
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/png")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { activity.startActivity(view) }
        }
        .setNeutralButton(R.string.dejavu_screenshot_share) { _, _ ->
            val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { activity.startActivity(Intent.createChooser(send, null)) }
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}
