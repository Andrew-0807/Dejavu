/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.dejavu.browser

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabZoomGrowTest {
    private val progress = Animatable(0f)

    @Test
    fun `frames spent setting up the zoom skip none of the start of the growth`() = runTest {
        val frames = Frames(firstMs = listOf(0, 120))
        withContext(frames) { progress.grow {} }

        // The frame after the stall shows the card one frame on, not 120 ms on, which would be most of the way.
        assertTrue("came ${frames.seen[2]} of the way", frames.seen[2] in 0.001f..0.05f)
        assertEquals(1f, progress.value)
    }

    @Test
    fun `the browser starts opening once, near the end of the growth`() = runTest {
        var opened = 0
        var openedAt = 0f
        withContext(Frames()) {
            progress.grow {
                opened++
                openedAt = progress.value
            }
        }

        assertEquals(1, opened)
        assertTrue("opened $openedAt of the way", openedAt in 0.9f..0.999f)
    }

    @Test
    fun `the browser setting itself up lets the card fill the screen at once`() = runTest {
        val frames = Frames()
        withContext(frames) { progress.grow { frames.stallNext = true } }

        assertEquals(1f, progress.value)
        assertEquals(1, frames.afterStall)
    }

    /**
     * Hands out frames at [firstMs], then one every 60th of a second, noting how far [progress] came before each. With
     * [stallNext] set, the next frame comes 400 ms late and the frames after it are counted.
     */
    private inner class Frames(firstMs: List<Long> = listOf(0)) : MonotonicFrameClock {
        private val next = ArrayDeque(firstMs.map { it * NANOS_PER_MS })
        private var last = 0L
        val seen = mutableListOf<Float>()
        var stallNext = false
        var afterStall = -1

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            seen += progress.value
            if (afterStall >= 0) afterStall++
            last = next.removeFirstOrNull() ?: (last + FRAME_NANOS)
            if (stallNext) {
                stallNext = false
                afterStall = 1
                last += STALL_MS * NANOS_PER_MS
            }
            return onFrame(last)
        }
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val FRAME_NANOS = 16_666_667L
        const val STALL_MS = 400L
    }
}
