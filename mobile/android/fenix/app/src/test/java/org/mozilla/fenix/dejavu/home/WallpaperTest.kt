/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.dejavu.workspaces.WorkspaceWallpaper

@RunWith(AndroidJUnit4::class)
class WallpaperTest {
    private val wallpapers = mapOf(
        0 to WorkspaceWallpaper("a.jpg"),
        2 to WorkspaceWallpaper("c.jpg", blur = 0.9f),
    )

    private fun wallpaperAt(page: Int) = wallpapers[page]

    @Test
    fun `a page with a wallpaper is lightly covered by the plain background`() {
        assertEquals(WALLPAPER_COVER, coverAt(0f, ::wallpaperAt), DELTA)
        assertEquals(WALLPAPER_COVER, coverAt(2f, ::wallpaperAt), DELTA)
    }

    @Test
    fun `a page without a wallpaper is fully covered, so its theme shows as it is`() {
        assertEquals(1f, coverAt(1f, ::wallpaperAt), DELTA)
        assertEquals(1f, coverAt(3f, ::wallpaperAt), DELTA)
    }

    @Test
    fun `swiping goes from the cover of one page to the cover of the next`() {
        assertEquals((WALLPAPER_COVER + 1f) / 2, coverAt(0.5f, ::wallpaperAt), DELTA)
        assertEquals(1f - (1f - WALLPAPER_COVER) / 4, coverAt(1.25f, ::wallpaperAt), DELTA)
    }

    private companion object {
        const val DELTA = 0.001f
    }
}
