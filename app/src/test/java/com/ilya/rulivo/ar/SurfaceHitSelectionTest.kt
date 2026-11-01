package com.ilya.rulivo.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SurfaceHitSelectionTest {
    @Test fun foregroundObjectWinsOverPlaneBehindIt() {
        assertEquals("chair", SurfaceHitSelection.nearestSurface(listOf(
            SurfaceHit("floor", SurfaceKind.PLANE, 2.0), SurfaceHit("chair", SurfaceKind.DEPTH, 1.0))))
    }
    @Test fun fittedPlaneWinsWhenDepthAgrees() {
        assertEquals("wall", SurfaceHitSelection.nearestSurface(listOf(
            SurfaceHit("depth", SurfaceKind.DEPTH, 1.0), SurfaceHit("wall", SurfaceKind.PLANE, 1.01))))
    }
    @Test fun supportsDepthWithoutPlanes() {
        assertEquals("depth", SurfaceHitSelection.nearestSurface(listOf(SurfaceHit("depth", SurfaceKind.DEPTH, 0.8))))
    }
    @Test fun nearestOfSeveralPlanesWinsRegardlessOfInputOrder() {
        assertEquals("desk", SurfaceHitSelection.nearestSurface(listOf(
            SurfaceHit("floor", SurfaceKind.PLANE, 2.0), SurfaceHit("desk", SurfaceKind.PLANE, 1.0))))
    }
    @Test fun invalidOrMissingHitCannotPlacePoint() {
        assertNull(SurfaceHitSelection.nearestSurface(emptyList<SurfaceHit<String>>()))
        assertNull(SurfaceHitSelection.nearestSurface(listOf(
            SurfaceHit("bad", SurfaceKind.DEPTH, Double.NaN), SurfaceHit("behind", SurfaceKind.PLANE, -1.0))))
    }
}
