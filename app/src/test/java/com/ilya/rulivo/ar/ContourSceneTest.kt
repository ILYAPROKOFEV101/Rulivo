package com.ilya.rulivo.ar

import org.junit.Assert.*
import org.junit.Test

class ContourSceneTest {
    private class Anchor(var detached: Int = 0, var stopped: Boolean = false)
    private val anchors = mutableListOf<Anchor>()
    private fun scene() = MeasurementScene<Anchor> { it.detached++ }
    private fun point(s: MeasurementScene<Anchor>) = s.place(s.revision, true) { Anchor().also(anchors::add) }
    private fun line(s: MeasurementScene<Anchor>) { point(s); point(s) }
    private fun rectangle(s: MeasurementScene<Anchor>) {
        line(s); assertTrue(s.continueLast(s.revision)); point(s); point(s)
        assertTrue(s.closeContour(s.revision))
    }

    @Test fun continuedEdgesAndClosureShareCornersWithoutCreatingExtraAnchors() {
        val s = scene(); rectangle(s)
        assertEquals(4, anchors.size); assertEquals(4, s.measurements.size)
        assertEquals(1, s.contours.size); assertEquals(anchors, s.contours.single().anchors)
        assertSame(s.measurements[0].endAnchor, s.measurements[1].startAnchor)
        assertSame(s.measurements[0].startAnchor, s.measurements.last().endAnchor)
        assertNull(s.currentStart); assertFalse(s.isContinuing); assertFalse(s.canContinue)
        assertTrue(anchors.all { it.detached == 0 })
        line(s)
        assertEquals(1, s.contours.size); assertTrue(s.canContinue)
        s.clear(); s.clear()
        assertTrue(anchors.all { it.detached == 1 })
    }

    @Test fun undoClosingEdgeReopensContourThenUndoCornerKeepsEarlierEdges() {
        val s = scene(); rectangle(s)
        assertTrue(s.undoLast(s.revision)); assertTrue(s.contours.isEmpty())
        assertEquals(4, s.activeCorners.size); assertTrue(s.canClose)
        assertSame(anchors.last(), s.currentStart); assertTrue(anchors.all { it.detached == 0 })
        assertTrue(s.undoLast(s.revision)); assertEquals(3, s.activeCorners.size)
        assertSame(anchors[2], s.currentStart); assertEquals(1, anchors[3].detached)
        assertTrue(s.closeContour(s.revision)); assertEquals(3, s.contours.single().anchors.size)
        s.clear(); assertTrue(anchors.all { it.detached == 1 })
    }

    @Test fun finishingOpenLineCanResumeAndDoesNotDetachItsLastCorner() {
        val s = scene(); line(s); s.continueLast(s.revision); point(s)
        assertTrue(s.cancelCurrent(s.revision)); assertEquals(2, s.measurements.size)
        assertFalse(s.isContinuing); assertTrue(s.canContinue)
        assertTrue(anchors.all { it.detached == 0 })
        assertTrue(s.continueLast(s.revision)); assertEquals(3, s.activeCorners.size)
        assertTrue(s.closeContour(s.revision)); s.clear()
        assertTrue(anchors.all { it.detached == 1 })
    }

    @Test fun undoEntireActiveChainReturnsToAnOrdinaryFirstPoint() {
        val s = scene(); line(s); s.continueLast(s.revision); point(s)
        s.undoLast(s.revision); s.undoLast(s.revision)
        assertFalse(s.isContinuing); assertSame(anchors[0], s.currentStart)
        assertTrue(s.measurements.isEmpty()); assertFalse(s.canClose)
        s.cancelCurrent(s.revision); assertTrue(s.isEmpty)
        assertTrue(anchors.all { it.detached == 1 })
    }

    @Test fun stoppedCornerRemovesOnlyItsWholeContourAndLeavesOtherPaths() {
        val s = scene(); rectangle(s); rectangle(s); point(s)
        val secondId = s.contours[1].id
        anchors[1].stopped = true
        assertTrue(s.removeStopped { it.stopped })
        assertEquals(secondId, s.contours.single().id); assertEquals(4, s.measurements.size)
        assertSame(anchors.last(), s.currentStart)
        assertTrue(anchors.take(4).all { it.detached == 1 })
        assertTrue(anchors.drop(4).all { it.detached == 0 })
        assertFalse(s.removeStopped { it.stopped })
        s.clear(); assertTrue(anchors.all { it.detached == 1 })
    }

    @Test fun stoppedActiveCornerClearsChainAndPreview() {
        val s = scene(); line(s); s.continueLast(s.revision); point(s)
        anchors[0].stopped = true; s.removeStopped { it.stopped }
        assertTrue(s.isEmpty); assertFalse(s.isContinuing); assertTrue(s.activeCorners.isEmpty())
        assertTrue(anchors.all { it.detached == 1 })
    }

    @Test fun staleAndPrematureActionsDoNotMutateGeometry() {
        val s = scene(); line(s)
        val old = s.revision; assertTrue(s.continueLast(old)); assertFalse(s.continueLast(old))
        assertFalse(s.closeContour(s.revision)); point(s)
        assertFalse(s.closeContour(old)); assertFalse(s.cancelCurrent(old))
        assertEquals(3, anchors.size); assertTrue(s.closeContour(s.revision))
        assertFalse(s.closeContour(s.revision)); assertFalse(s.continueLast(s.revision))
    }

    @Test fun contourReservesTheLastSceneEdgeForClosure() {
        val s = scene(); repeat(27) { line(s) }
        line(s); assertTrue(s.continueLast(s.revision)); point(s)
        assertEquals(29, s.measurements.size); assertFalse(s.canAddSegment); assertTrue(s.canClose)
        val count = anchors.size
        assertEquals(MeasurementScene.Placement.LIMIT_REACHED, point(s)); assertEquals(count, anchors.size)
        assertTrue(s.closeContour(s.revision)); assertTrue(s.atLimit)
        s.undoLast(s.revision); assertTrue(s.canClose); assertTrue(s.closeContour(s.revision))
        s.clear(); assertTrue(anchors.all { it.detached == 1 })
    }

    @Test fun undoClosedContourPreservesIndependentPendingPoint() {
        val s = scene(); rectangle(s); point(s)
        val pending = s.currentStart; s.undoLast(s.revision)
        assertSame(pending, s.currentStart); assertFalse(s.isContinuing); assertTrue(s.contours.isEmpty())
        s.cancelCurrent(s.revision); assertTrue(s.continueLast(s.revision)); assertTrue(s.canClose)
        s.clear(); assertTrue(anchors.all { it.detached == 1 })
    }
}
