package com.ilya.rulivo.ar

import org.junit.Assert.*
import org.junit.Test

class MeasurementSceneTest {
    private data class TestAnchor(val number: Int, var detachCount: Int = 0, var stopped: Boolean = false)
    private val created = mutableListOf<TestAnchor>()
    private fun newScene() = MeasurementScene<TestAnchor> { it.detachCount++ }
    private fun place(scene: MeasurementScene<TestAnchor>, ready: Boolean = true, revision: Long = scene.revision) =
        scene.place(revision, ready) { TestAnchor(created.size).also { created.add(it) } }
    private fun line(scene: MeasurementScene<TestAnchor>) { place(scene); place(scene) }

    @Test fun fiveIndependentLinesKeepTheirOwnAnchorsAndIds() {
        val scene = newScene()
        repeat(5) { line(scene) }
        assertEquals(5, scene.measurements.size)
        assertEquals(5, scene.measurements.map { it.id }.toSet().size)
        assertNull(scene.currentStart)
        scene.measurements.forEachIndexed { index, m ->
            assertSame(created[index * 2], m.startAnchor)
            assertSame(created[index * 2 + 1], m.endAnchor)
        }
        assertTrue(created.all { it.detachCount == 0 })
        place(scene)
        assertEquals(5, scene.measurements.size)
        assertSame(created.last(), scene.currentStart)
    }

    @Test fun invalidSurfaceNeverInvokesAnchorFactoryForEitherPoint() {
        val scene = newScene()
        assertEquals(MeasurementScene.Placement.REJECTED, place(scene, false))
        assertTrue(created.isEmpty())
        place(scene)
        assertEquals(MeasurementScene.Placement.REJECTED, place(scene, false))
        assertEquals(1, created.size)
        assertTrue(scene.measurements.isEmpty())
    }

    @Test fun staleTapCannotCreateANewLineAfterAutomaticReturnToFirstPoint() {
        val scene = newScene()
        val oldRevision = scene.revision
        line(scene)
        assertEquals(MeasurementScene.Placement.REJECTED, place(scene, revision = oldRevision))
        assertEquals(2, created.size)
        assertNull(scene.currentStart)
    }

    @Test fun undoRemovesOnlyLastCompletePairAndPreservesPendingPoint() {
        val scene = newScene()
        repeat(5) { line(scene) }
        place(scene)
        val pending = scene.currentStart
        assertTrue(scene.undoLast(scene.revision))
        assertEquals(4, scene.measurements.size)
        assertSame(pending, scene.currentStart)
        assertEquals(listOf(8, 9), created.filter { it.detachCount == 1 }.map { it.number })
    }

    @Test fun cancelCurrentPreservesEveryFinishedMeasurement() {
        val scene = newScene()
        repeat(5) { line(scene) }
        place(scene)
        assertTrue(scene.cancelCurrent(scene.revision))
        assertNull(scene.currentStart)
        assertEquals(5, scene.measurements.size)
        assertEquals(listOf(10), created.filter { it.detachCount == 1 }.map { it.number })
    }

    @Test fun clearReleasesEveryAnchorExactlyOnceAndLeavesSceneReusable() {
        val scene = newScene()
        repeat(5) { line(scene) }
        place(scene)
        scene.clear()
        scene.clear()
        assertTrue(scene.isEmpty)
        assertTrue(created.all { it.detachCount == 1 })
        line(scene)
        assertEquals(1, scene.measurements.size)
        assertEquals(0, created.last().detachCount)
    }

    @Test fun thirtyLineLimitRejectsCreationBeforeAllocatingAnotherAnchor() {
        val scene = newScene()
        repeat(30) { line(scene) }
        assertTrue(scene.atLimit)
        assertEquals(MeasurementScene.Placement.LIMIT_REACHED, place(scene))
        assertEquals(60, created.size)
        scene.undoLast(scene.revision)
        assertFalse(scene.atLimit)
        line(scene)
        assertEquals(30, scene.measurements.size)
    }

    @Test fun onlyPermanentlyStoppedPairsAreRemoved() {
        val scene = newScene()
        repeat(5) { line(scene) }
        assertFalse(scene.removeStopped { it.stopped }) // PAUSED/non-stopped anchors are retained.
        created[2].stopped = true
        assertTrue(scene.removeStopped { it.stopped })
        assertEquals(4, scene.measurements.size)
        assertEquals(listOf(2, 3), created.filter { it.detachCount == 1 }.map { it.number })
    }

    @Test fun staleUndoAndCancelCannotDeleteTheNextMeasurement() {
        val scene = newScene()
        repeat(2) { line(scene) }
        val revision = scene.revision
        assertTrue(scene.undoLast(revision))
        assertFalse(scene.undoLast(revision))
        assertEquals(1, scene.measurements.size)
        place(scene)
        assertFalse(scene.cancelCurrent(revision))
        assertNotNull(scene.currentStart)
    }

    @Test fun failedAnchorCreationPreservesExistingScene() {
        val scene = newScene()
        line(scene)
        place(scene)
        val revision = scene.revision
        try {
            scene.place(revision, true) { throw IllegalStateException("tracking lost") }
            fail("Expected factory exception")
        } catch (_: IllegalStateException) { }
        assertEquals(revision, scene.revision)
        assertSame(created.last(), scene.currentStart)
        assertEquals(1, scene.measurements.size)
    }
}
