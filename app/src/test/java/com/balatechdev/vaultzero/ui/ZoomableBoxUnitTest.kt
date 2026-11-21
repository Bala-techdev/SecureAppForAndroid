package com.balatechdev.vaultzero.ui

import com.balatechdev.vaultzero.core.designsystem.components.ZoomMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying two-finger pinch-to-zoom math, pan boundary clamping,
 * double-tap centering, and gesture pass-through logic for parent scroll containers.
 */
class ZoomableBoxUnitTest {

    @Test
    fun testClampScale_withinBounds() {
        val scale = ZoomMath.clampScale(2.5f, minScale = 1f, maxScale = 5f)
        assertEquals(2.5f, scale, 0.001f)
    }

    @Test
    fun testClampScale_exceedsMinAndMax() {
        val belowMin = ZoomMath.clampScale(0.5f, minScale = 1f, maxScale = 5f)
        assertEquals(1.0f, belowMin, 0.001f)

        val aboveMax = ZoomMath.clampScale(8.0f, minScale = 1f, maxScale = 5f)
        assertEquals(5.0f, aboveMax, 0.001f)
    }

    @Test
    fun testCalculateMaxOffset_unzoomedIsZero() {
        val maxOffset = ZoomMath.calculateMaxOffset(size = 1000f, scale = 1.0f)
        assertEquals(0f, maxOffset, 0.001f)
    }

    @Test
    fun testCalculateMaxOffset_zoomed() {
        // At 2x scale on 1000px viewport, rendered width is 2000px.
        // Content extends 500px to the left and 500px to the right of center.
        // Therefore max allowed pan is 500px.
        val maxOffset = ZoomMath.calculateMaxOffset(size = 1000f, scale = 2.0f)
        assertEquals(500f, maxOffset, 0.001f)

        // At 5x scale on 1000px viewport, max offset is (1000 * 4) / 2 = 2000px
        val maxOffset5x = ZoomMath.calculateMaxOffset(size = 1000f, scale = 5.0f)
        assertEquals(2000f, maxOffset5x, 0.001f)
    }

    @Test
    fun testClampOffset_boundsExceeded() {
        // At 2x scale, max offset is 500f
        val clampedPositive = ZoomMath.clampOffset(offset = 750f, size = 1000f, scale = 2.0f)
        assertEquals(500f, clampedPositive, 0.001f)

        val clampedNegative = ZoomMath.clampOffset(offset = -900f, size = 1000f, scale = 2.0f)
        assertEquals(-500f, clampedNegative, 0.001f)

        val withinBounds = ZoomMath.clampOffset(offset = 200f, size = 1000f, scale = 2.0f)
        assertEquals(200f, withinBounds, 0.001f)
    }

    @Test
    fun testCalculateCentroidOffset_unzoomedReturnsZero() {
        val offset = ZoomMath.calculateCentroidOffset(
            currentOffset = 100f,
            centroidFromCenter = 50f,
            oldScale = 1.0f,
            newScale = 1.0f,
            pan = 10f,
            size = 1000f
        )
        assertEquals(0f, offset, 0.001f)
    }

    @Test
    fun testCalculateCentroidOffset_pinchZoomWithPan() {
        val offset = ZoomMath.calculateCentroidOffset(
            currentOffset = 0f,
            centroidFromCenter = 100f,
            oldScale = 1.0f,
            newScale = 2.0f,
            pan = 20f,
            size = 1000f
        )
        // (0 - 100) * 2/1 + 100 + 20 = -200 + 100 + 20 = -80
        assertEquals(-80f, offset, 0.001f)
    }

    @Test
    fun testCalculateDoubleTapOffset_centersTappedRegion() {
        // User taps 100px to the right of center; at 2.5x, offset moves left to center it
        val offset = ZoomMath.calculateDoubleTapOffset(
            tapFromCenter = 100f,
            targetScale = 2.5f,
            size = 1000f
        )
        // rawOffset = -100 * (2.5 - 1) = -150f. Max offset at 2.5x is 1000 * 1.5 / 2 = 750f.
        assertEquals(-150f, offset, 0.001f)
    }

    @Test
    fun testShouldConsumeDrag_scrollPassThroughWhenUnzoomed() {
        // At 1.0x scale, single-finger drag must NOT be consumed
        // to preserve normal vertical scrolling of LazyColumn and verticalScroll
        assertFalse("At 1.0x scale, single-finger drag should not be consumed", ZoomMath.shouldConsumeDrag(1.0f))
        assertFalse("At 1.03x scale, slight jitter should not consume scroll", ZoomMath.shouldConsumeDrag(1.03f))

        // When zoomed in, drag must be consumed for smooth pan navigation
        assertTrue("At 1.5x scale, drag must be consumed for panning", ZoomMath.shouldConsumeDrag(1.5f))
        assertTrue("At 3.0x scale, drag must be consumed for panning", ZoomMath.shouldConsumeDrag(3.0f))
    }
}
