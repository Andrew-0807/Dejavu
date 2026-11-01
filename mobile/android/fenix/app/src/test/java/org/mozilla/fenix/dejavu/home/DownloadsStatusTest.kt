/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.home

import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.browser.state.state.content.DownloadState.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsStatusTest {
    private fun download(id: String, status: Status, copied: Long = 0, length: Long? = null) = DownloadState(
        url = "https://example.org/$id",
        id = id,
        status = status,
        currentBytesCopied = copied,
        contentLength = length,
        directoryPath = "downloads",
    )

    private fun statusOf(vararg downloads: DownloadState) =
        BrowserState(downloads = downloads.associateBy { it.id }).downloadsStatus()

    @Test
    fun `without downloads nothing runs`() {
        val status = statusOf()

        assertFalse(status.isRunning)
        assertFalse(status.isPaused)
        assertNull(status.progress)
    }

    @Test
    fun `progress counts the bytes of all running downloads that know their size`() {
        val status = statusOf(
            download("a", Status.DOWNLOADING, copied = 30, length = 100),
            download("b", Status.DOWNLOADING, copied = 70, length = 300),
            download("c", Status.DOWNLOADING, copied = 500),
            download("d", Status.COMPLETED, copied = 50, length = 50),
        )

        assertEquals(setOf("a", "b", "c"), status.runningIds)
        assertEquals(0.25f, status.progress!!, 0.001f)
    }

    @Test
    fun `progress is unknown while no running download knows its size`() {
        assertNull(statusOf(download("a", Status.DOWNLOADING, copied = 500)).progress)
        assertNull(statusOf(download("a", Status.INITIATED, length = 0)).progress)
    }

    @Test
    fun `downloads are paused only when every running one is`() {
        val paused = download("a", Status.PAUSED, copied = 10, length = 100)

        assertTrue(statusOf(paused).isPaused)
        assertFalse(statusOf(paused, download("b", Status.DOWNLOADING)).isPaused)
    }

    @Test
    fun `finished and failed downloads are told apart from running ones`() {
        val status = statusOf(
            download("a", Status.COMPLETED),
            download("b", Status.FAILED),
            download("c", Status.CANCELLED),
        )

        assertFalse(status.isRunning)
        assertEquals(setOf("a"), status.completedIds)
        assertEquals(setOf("b"), status.failedIds)
    }
}
