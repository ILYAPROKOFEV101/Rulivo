package com.ilya.rulivo.ar

import org.junit.Assert.*
import org.junit.Test

class DiagnosticFrameSamplerTest {
    @Test fun repeatedFramesAreNotFreshAndInvalidTimestampsAreVisible() {
        val sampler = DiagnosticFrameSampler(100)
        assertEquals(1, sampler.observe(10, 0)!!["fresh_frames"])
        assertNull(sampler.observe(10, 30))
        assertNull(sampler.observe(9, 60))
        val sample = sampler.observe(11, 100)!!
        assertEquals(1, sample["fresh_frames"])
        assertEquals(1, sample["repeated_frames"])
        assertEquals(1, sample["invalid_frames"])
        assertEquals(100L, sample["sample_interval_ns"])
    }

    @Test fun placementForcesSnapshotAndResumeDoesNotInventFreshFrameOrIncludePauseTime() {
        val sampler = DiagnosticFrameSampler(100)
        sampler.observe(10, 100)
        assertNotNull(sampler.observe(10, 101, force = true))
        sampler.interrupt()
        val resumed = sampler.observe(10, 10000)!!
        assertEquals(0, resumed["fresh_frames"])
        assertEquals(1, resumed["repeated_frames"])
        assertNull(resumed["sample_interval_ns"])
    }
}
