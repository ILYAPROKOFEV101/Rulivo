package com.ilya.rulivo.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementLabelPlacementTest {
    @Test fun horizontalCaptionIsCenteredWithGapAboveLine() {
        val bounds = place(100f, 400f, 300f, 400f)!!
        assertEquals(200f, bounds.left + bounds.width / 2f, 0.001f)
        assertEquals(392f, bounds.top + bounds.height, 0.001f)
    }

    @Test fun swappingPointsDoesNotFlipCaptionToOtherSide() {
        assertEquals(place(100f, 500f, 300f, 300f), place(300f, 300f, 100f, 500f))
        assertEquals(place(200f, 200f, 200f, 600f), place(200f, 600f, 200f, 200f))
    }

    @Test fun verticalLineDoesNotCrossCaption() {
        val bounds = place(200f, 200f, 200f, 600f)!!
        assertEquals(208f, bounds.left, 0.001f)
    }

    @Test fun nearScreenEdgeCaptionRemainsFullyVisible() {
        val bounds = place(0f, 5f, 40f, 5f)!!
        assertTrue(bounds.left >= 8f && bounds.top >= 8f)
        assertTrue(bounds.left + bounds.width <= 392f)
        assertTrue(bounds.top + bounds.height <= 792f)
    }

    @Test fun offscreenSegmentHasNoFloatingCaptionAtEdge() {
        assertNull(place(-200f, 300f, -100f, 300f))
        assertNull(place(100f, -20f, 300f, -20f))
    }

    @Test fun zeroLengthSegmentStillHasFiniteCaptionPosition() {
        val bounds = place(200f, 400f, 200f, 400f)!!
        assertEquals(150f, bounds.left, 0.001f)
        assertEquals(352f, bounds.top, 0.001f)
    }

    @Test fun usesProjectedWorldMidpointInsteadOfScreenEndpointAverage() {
        val bounds = MeasurementLabelPlacement.aboveSegment(100f, 400f, 300f, 400f,
            400f, 800f, 100f, 40f, 8f, middleX = 250f, middleY = 400f)!!
        assertEquals(250f, bounds.left + bounds.width / 2f, 0.001f)
        val midpoint = MeasurementGeometry.midpoint(Point3D(-1.0, 2.0, -1.0), Point3D(3.0, 4.0, -5.0))
        assertEquals(Point3D(1.0, 3.0, -3.0), midpoint)
    }

    @Test fun captionFlipsSideInsteadOfCrossingLineNearTopOrRightEdge() {
        val top = place(100f, 20f, 300f, 20f)!!
        assertTrue(top.top > 20f)
        val right = place(380f, 200f, 380f, 600f)!!
        assertTrue(right.left + right.width < 380f)
    }

    @Test fun contourCaptionsPreferOutsideTheArea() {
        val area = LabelBounds(255f, 595f, 210f, 90f)
        val left = MeasurementLabelPlacement.aboveSegment(180f,197f,180f,1083f,
            720f,1280f,82f,51f,12f,avoidBounds=area)!!
        assertTrue(left.left+left.width<180f)
        val bottom = MeasurementLabelPlacement.aboveSegment(180f,1083f,540f,1083f,
            720f,1280f,130f,51f,12f,avoidBounds=area)!!
        assertTrue(bottom.top>1083f)
    }

    @Test fun contourCaptionNearViewportEdgeMovesAlongSideWithoutCoveringArea() {
        val area = LabelBounds(388f,595f,210f,90f)
        val right = MeasurementLabelPlacement.aboveSegment(672f,197f,672f,1083f,
            720f,1280f,82f,51f,12f,avoidBounds=area)!!
        assertTrue(right.left+right.width<672f)
        assertTrue(right.top+right.height<=area.top || right.top>=area.top+area.height)
    }

    private fun place(ax: Float, ay: Float, bx: Float, by: Float) =
        MeasurementLabelPlacement.aboveSegment(ax, ay, bx, by, 400f, 800f, 100f, 40f, 8f)
}
