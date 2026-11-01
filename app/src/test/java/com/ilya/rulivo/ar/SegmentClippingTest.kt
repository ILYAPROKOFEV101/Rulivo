package com.ilya.rulivo.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentClippingTest {
    @Test fun visibleSegmentKeepsOriginalEndpoints() {
        val a = ClipPoint(-0.5f, 0f, 0f, 1f)
        val b = ClipPoint(0.5f, 0f, 0f, 1f)
        assertEquals(a to b, SegmentClipping.visiblePart(a, b))
    }
    @Test fun offscreenEndpointKeepsVisibleLine() {
        val visible = SegmentClipping.visiblePart(ClipPoint(-3f, 0f, 0f, 1f), ClipPoint(0.5f, 0f, 0f, 1f))!!
        assertEquals(-1f, visible.first.x / visible.first.w, 0.0001f)
        assertEquals(0.5f, visible.second.x / visible.second.w, 0.0001f)
    }
    @Test fun nearPlaneClippingRetainsLineWhenOnePointIsBehindCamera() {
        // Perspective coordinates for a segment crossing both the near and left planes.
        val visible = SegmentClipping.visiblePart(ClipPoint(-0.1f, 0f, -0.3f, -0.1f), ClipPoint(0.5f, 0f, 1.8f, 2f))
        assertNotNull(visible)
        for (point in listOf(visible!!.first, visible.second)) {
            assertTrue(point.w > 0f)
            assertTrue(point.z >= -point.w - 0.0001f)
            assertTrue(kotlin.math.abs(point.x / point.w) <= 1.0001f)
        }
    }
    @Test fun invisibleOrInvalidSegmentsAreDiscarded() {
        assertNull(SegmentClipping.visiblePart(ClipPoint(2f, 0f, 0f, 1f), ClipPoint(3f, 0f, 0f, 1f)))
        assertNull(SegmentClipping.visiblePart(ClipPoint(0f, 0f, -2f, -1f), ClipPoint(0f, 1f, -3f, -2f)))
        assertNull(SegmentClipping.visiblePart(ClipPoint(Float.NaN, 0f, 0f, 1f), ClipPoint(0f, 0f, 0f, 1f)))
    }
    @Test fun clippingIsIndependentOfPointOrder() {
        val a = ClipPoint(-2f, 0f, 0f, 1f)
        val b = ClipPoint(2f, 0.5f, 0f, 1f)
        val forward = SegmentClipping.visiblePart(a, b)
        val reverse = SegmentClipping.visiblePart(b, a)
        assertNotNull(forward)
        assertEquals(forward?.first, reverse?.second)
        assertEquals(forward?.second, reverse?.first)
    }
}
