/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import android.app.Activity
import android.graphics.Bitmap
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.concurrent.TimeUnit
import mozilla.components.concept.engine.EngineView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SearchBackdropTest {
    private val picture = slot<(Bitmap?) -> Unit>()
    private lateinit var page: View
    private var opened = 0

    @Before
    fun setup() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        page = View(activity)
        activity.setContentView(page)
        val engineView: EngineView = mockk(relaxed = true) {
            every { asView() } returns page
            every { captureThumbnail(capture(picture)) } returns Unit
        }
        SearchBackdrop.attach(engineView)
    }

    @After
    fun tearDown() {
        // A picture still being taken times out and the page leaves the screen, so later tests open search at once.
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
        SearchBackdrop.page.value?.let(SearchBackdrop::clear)
        (page.parent as? ViewGroup)?.removeView(page)
    }

    @Test
    fun `the search screen opens once the page is pictured, with the picture`() {
        SearchBackdrop.capturePage("tab") { opened++ }
        assertEquals(0, opened)

        picture.captured(bitmap())

        assertEquals(1, opened)
        assertEquals("tab", SearchBackdrop.page.value?.tabId)
    }

    @Test
    fun `a picture coming after the search screen opened is dropped`() {
        SearchBackdrop.capturePage("tab") { opened++ }
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
        assertEquals(1, opened)

        picture.captured(bitmap())

        assertEquals(1, opened)
        assertNull(SearchBackdrop.page.value)
    }

    @Test
    fun `asking again while the page is pictured opens the search screen once`() {
        SearchBackdrop.capturePage("tab") { opened++ }
        SearchBackdrop.capturePage("tab") { opened++ }

        picture.captured(bitmap())

        assertEquals(1, opened)
    }

    @Test
    fun `without a page on screen the search screen opens at once`() {
        (page.parent as ViewGroup).removeView(page)

        SearchBackdrop.capturePage("tab") { opened++ }

        assertEquals(1, opened)
        assertNull(SearchBackdrop.page.value)
    }

    @Test
    fun `a search screen lets go only of the picture it showed`() {
        SearchBackdrop.capturePage("first") {}
        picture.captured(bitmap())
        val first = checkNotNull(SearchBackdrop.page.value)
        SearchBackdrop.capturePage("second") {}
        picture.captured(bitmap())

        SearchBackdrop.clear(first)

        assertEquals("second", SearchBackdrop.page.value?.tabId)
    }

    private fun bitmap() = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
}
