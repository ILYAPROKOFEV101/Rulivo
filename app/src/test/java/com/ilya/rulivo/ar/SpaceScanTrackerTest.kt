package com.ilya.rulivo.ar

import org.junit.Assert.*
import org.junit.Test

class SpaceScanTrackerTest {
    private fun time(frame: Int) = 1_000_000_000L + frame * 50_000_000L
    private fun observe(scan: SpaceScanTracker, frame: Int, surface: Boolean = true,
        tracking: Boolean = true, moving: Boolean = true) {
        scan.update(time(frame), time(frame), tracking,
            Point3D(if (moving) minOf(frame * 0.01, 0.1) else 0.0, 0.0, 0.0), surface)
    }
    private fun prepare(scan: SpaceScanTracker) { for (frame in 0..16) observe(scan, frame) }
    private fun measuring(): SpaceScanTracker = SpaceScanTracker().also { prepare(it); assertTrue(it.beginMeasurement()) }

    @Test fun requiresRecognizedSurfacesAndDistinctFramesBeforeMeasurement() {
        val scan = SpaceScanTracker()
        assertFalse(scan.beginMeasurement())
        for (frame in 0..15) {
            observe(scan, frame)
            assertFalse(scan.canPlace(true))
            assertEquals(SpaceScanPhase.SCANNING, scan.phase)
        }
        observe(scan, 16)
        assertEquals(SpaceScanPhase.READY, scan.phase)
        assertEquals(1f, scan.progress, 0f)
        assertFalse(scan.canPlace(true))
        assertTrue(scan.beginMeasurement())
        assertTrue(scan.canPlace(true))
    }

    @Test fun cannotScanEmptySpaceFromElapsedTimeAlone() {
        val empty = SpaceScanTracker()
        for (frame in 0..50) observe(empty, frame, surface = false)
        assertFalse(empty.beginMeasurement())
        assertEquals(0f, empty.progress, 0f)
    }

    @Test fun recognizedSurfaceDoesNotRequireAnExtraCameraTranslation() {
        val scan = SpaceScanTracker()
        for (frame in 0..16) observe(scan, frame, moving = false)
        assertTrue(scan.beginMeasurement())
    }

    @Test fun lookingAwayDuringPartialScanKeepsProgressAndResumesFromIt() {
        val scan = SpaceScanTracker()
        for (frame in 0..8) observe(scan, frame)
        val progress = scan.progress
        assertTrue(progress > 0f)
        for (frame in 9..30) {
            observe(scan, frame, surface = false)
            assertEquals(progress, scan.progress, 0f)
            assertFalse(scan.beginMeasurement())
        }
        for (frame in 31..39) observe(scan, frame)
        assertTrue(scan.beginMeasurement())
    }

    @Test fun intermittentSurfaceObservationsAccumulateInsteadOfRestartingScan() {
        val scan = SpaceScanTracker()
        var previousProgress = 0f
        for (frame in 0..40) {
            observe(scan, frame, surface = frame % 2 == 0)
            assertTrue(scan.progress >= previousProgress)
            previousProgress = scan.progress
        }
        assertTrue(scan.beginMeasurement())
    }

    @Test fun trackingLossDuringPartialScanKeepsEvidenceWithoutCountingTheGap() {
        val scan = SpaceScanTracker()
        for (frame in 0..8) observe(scan, frame)
        val progress = scan.progress
        observe(scan, 9, tracking = false)
        assertEquals(progress, scan.progress, 0f)
        observe(scan, 200)
        assertEquals(progress, scan.progress, 0f)
        assertFalse(scan.beginMeasurement())
        for (frame in 201..209) observe(scan, frame)
        assertTrue(scan.beginMeasurement())
    }

    @Test fun pauseAfterPreparationKeepsCompletedScanButRequiresFreshTracking() {
        val scan = SpaceScanTracker()
        prepare(scan)
        scan.interrupt()
        assertEquals(SpaceScanPhase.READY, scan.phase)
        assertEquals(1f, scan.progress, 0f)
        assertFalse(scan.beginMeasurement())
        observe(scan, 17); observe(scan, 18)
        assertFalse(scan.beginMeasurement())
        observe(scan, 19, surface = false)
        assertTrue(scan.beginMeasurement())
        assertFalse(scan.canPlace(false))
    }

    @Test fun frozenCameraNeverAddsEvidenceEvenAfterItBecomesStale() {
        val scan = SpaceScanTracker()
        for (frame in 0..8) observe(scan, frame)
        val progress = scan.progress
        for (frame in 9..80) {
            scan.update(time(8), time(frame), true, Point3D(0.1, 0.0, 0.0), true)
            assertEquals(progress, scan.progress, 0f)
        }
        assertFalse(scan.beginMeasurement())
    }

    @Test fun aGapBetweenTrackedFramesDoesNotResetProgressOrCountAsObservation() {
        val scan = SpaceScanTracker()
        for (frame in 0..8) observe(scan, frame)
        val progress = scan.progress
        observe(scan, 100)
        assertEquals(progress, scan.progress, 0f)
        assertFalse(scan.beginMeasurement())
        for (frame in 101..109) observe(scan, frame)
        assertTrue(scan.beginMeasurement())
    }

    @Test fun newSessionStartsWithNoInheritedSurfaceEvidence() {
        val previous = SpaceScanTracker()
        prepare(previous)
        val fresh = SpaceScanTracker()
        observe(fresh, 0, surface = false)
        assertEquals(0f, fresh.progress, 0f)
        assertFalse(fresh.beginMeasurement())
    }

    @Test fun oneRepeatedCameraFrameCannotCompleteScan() {
        val scan = SpaceScanTracker()
        for (frame in 0..50) scan.update(time(0), time(frame), true, Point3D(0.1, 0.0, 0.0), true)
        assertFalse(scan.beginMeasurement())
    }

    @Test fun scanReadinessSurvivesLookingAwayButNeverAllowsPointInAir() {
        val scan = SpaceScanTracker()
        prepare(scan)
        observe(scan, 17, surface = false)
        assertEquals(SpaceScanPhase.READY, scan.phase)
        assertTrue(scan.beginMeasurement())
        assertFalse(scan.canPlace(false))
        observe(scan, 18)
        assertTrue(scan.canPlace(true))
    }

    @Test fun subsequentPointsCanBePlacedOnEveryFreshFrameWithoutDwell() {
        val scan = measuring()
        val scene = MeasurementScene<Int> { }
        var created = 0
        for (frame in 17..26) {
            observe(scan, frame)
            assertTrue(scan.canPlace(true))
            scene.place(scene.revision, scan.canPlace(true)) { ++created }
        }
        assertEquals(5, scene.measurements.size)
        assertEquals(10, created)
        assertEquals(SpaceScanPhase.MEASURING, scan.phase)
    }

    @Test fun missingHitRejectsPlacementAndNextValidHitIsImmediatelyUsable() {
        val scan = measuring()
        val scene = MeasurementScene<Int> { }
        observe(scan, 17, surface = false)
        scene.place(scene.revision, scan.canPlace(false)) { fail("Must not create anchor"); 1 }
        observe(scan, 18)
        assertEquals(MeasurementScene.Placement.FIRST_POINT, scene.place(scene.revision, scan.canPlace(true)) { 1 })
    }

    @Test fun trackingLossRecoversWithoutErasingAnchorsOrRepeatingFullScan() {
        val scan = measuring()
        val scene = MeasurementScene<Int> { fail("Tracking pause must not release anchors") }
        scene.place(scene.revision, scan.canPlace(true)) { 1 }
        observe(scan, 17, tracking = false)
        assertEquals(SpaceScanPhase.RECOVERING, scan.phase)
        assertFalse(scan.canPlace(true))
        observe(scan, 18); observe(scan, 19)
        assertFalse(scan.canPlace(true))
        observe(scan, 20)
        assertTrue(scan.canPlace(true))
        assertEquals(1, scene.currentStart)
    }

    @Test fun staleFramesAndBackgroundPauseBlockImmediatePlacement() {
        val scan = measuring()
        scan.update(time(16), time(23), true, Point3D(0.1, 0.0, 0.0), true)
        assertFalse(scan.canPlace(true))
        for (frame in 24..26) observe(scan, frame)
        assertTrue(scan.canPlace(true))
        scan.interrupt()
        assertFalse(scan.canPlace(true))
    }

    @Test fun sceneClearAndUndoDoNotRequireScanningAgain() {
        val scan = measuring()
        val scene = MeasurementScene<Int> { }
        scene.place(scene.revision, scan.canPlace(true)) { 1 }
        scene.place(scene.revision, scan.canPlace(true)) { 2 }
        scene.undoLast(scene.revision)
        scene.clear()
        observe(scan, 17)
        assertTrue(scan.canPlace(true))
    }

    @Test fun invalidCameraPositionCannotEstablishScanReadiness() {
        val scan = SpaceScanTracker()
        for (frame in 0..30) scan.update(time(frame), time(frame), true, Point3D(Double.NaN, 0.0, 0.0), true)
        assertEquals(SpaceScanPhase.SCANNING, scan.phase)
        assertFalse(scan.beginMeasurement())
    }
}
