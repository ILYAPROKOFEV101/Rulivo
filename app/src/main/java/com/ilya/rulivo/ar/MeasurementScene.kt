package com.ilya.rulivo.ar

/** GL-thread owner of live anchors. Shared corners are detached only when no edge uses them. */
internal class MeasurementScene<A>(private val release: (A) -> Unit) {
    data class Measurement<A>(val id: Long, val startAnchor: A, val endAnchor: A, val pathId: Long = id)
    data class Contour<A>(val id: Long, val anchors: List<A>)
    enum class Placement { FIRST_POINT, COMPLETED, REJECTED, LIMIT_REACHED }

    private val finished = mutableListOf<Measurement<A>>()
    private val owned = mutableListOf<A>()
    private val closedPaths = mutableSetOf<Long>()
    private var activePathId: Long? = null
    val measurements: List<Measurement<A>> get() = finished
    val contours: List<Contour<A>> get() = closedPaths.map { id ->
        Contour(id, finished.filter { it.pathId == id }.map { it.startAnchor })
    }
    val activeCorners: List<A> get() = activePathId?.let { id ->
        val edges = finished.filter { it.pathId == id }
        edges.firstOrNull()?.let { listOf(it.startAnchor) + edges.map { edge -> edge.endAnchor } }
    }.orEmpty()
    var currentStart: A? = null
        private set
    var revision: Long = 0
        private set
    private var nextId = 1L
    val atLimit: Boolean get() = finished.size >= MAX_MEASUREMENTS
    val isContinuing: Boolean get() = activePathId != null
    // Reserve one edge for closing the current outline even when the scene is nearly full.
    val canAddSegment: Boolean get() = finished.size < MAX_MEASUREMENTS - if (isContinuing) 1 else 0
    val canContinue: Boolean get() = currentStart == null && finished.isNotEmpty() &&
        finished.last().pathId !in closedPaths && finished.size < MAX_MEASUREMENTS - 1
    val canClose: Boolean get() = activeCorners.size >= 3 && !atLimit
    val isEmpty: Boolean get() = finished.isEmpty() && currentStart == null

    /** The factory is never called for an invalid surface, a stale tap, or a full scene. */
    fun place(expectedRevision: Long, surfaceReady: Boolean, createAnchor: () -> A): Placement {
        if (expectedRevision != revision) return Placement.REJECTED
        if (!canAddSegment) return Placement.LIMIT_REACHED
        if (!surfaceReady) return Placement.REJECTED
        val anchor = createAnchor()
        owned.add(anchor)
        val start = currentStart
        val result = if (start == null) {
            currentStart = anchor
            Placement.FIRST_POINT
        } else {
            val id = nextId++
            finished.add(Measurement(id, start, anchor, activePathId ?: id))
            currentStart = if (isContinuing) anchor else null
            Placement.COMPLETED
        }
        revision++
        return result
    }

    fun continueLast(expectedRevision: Long): Boolean {
        if (expectedRevision != revision || !canContinue) return false
        activePathId = finished.last().pathId
        currentStart = finished.last().endAnchor
        revision++
        return true
    }

    /** Geometry and tracking are checked by the caller; closing reuses the original corner. */
    fun closeContour(expectedRevision: Long): Boolean {
        if (expectedRevision != revision || !canClose) return false
        val id = activePathId ?: return false
        finished.add(Measurement(nextId++, activeCorners.last(), activeCorners.first(), id))
        closedPaths.add(id)
        currentStart = null
        activePathId = null
        revision++
        return true
    }

    /** Also finishes an open polyline, keeping every completed edge. */
    fun cancelCurrent(expectedRevision: Long): Boolean {
        if (expectedRevision != revision || currentStart == null) return false
        currentStart = null
        activePathId = null
        releaseUnused()
        revision++
        return true
    }

    fun undoLast(expectedRevision: Long): Boolean {
        if (expectedRevision != revision || finished.isEmpty()) return false
        val removed = finished.removeAt(finished.lastIndex)
        val wasClosed = closedPaths.remove(removed.pathId)
        if (activePathId == removed.pathId || wasClosed && currentStart == null) {
            currentStart = removed.startAnchor
            activePathId = removed.pathId.takeIf { id -> finished.any { it.pathId == id } }
        }
        releaseUnused()
        revision++
        return true
    }

    fun clear() {
        currentStart = null
        activePathId = null
        closedPaths.clear()
        finished.clear()
        releaseUnused()
        revision++
    }

    /** Losing a shared corner removes its whole path; other paths and paused anchors survive. */
    fun removeStopped(isStopped: (A) -> Boolean): Boolean {
        val lostPaths = finished.filter { isStopped(it.startAnchor) || isStopped(it.endAnchor) }
            .map { it.pathId }.toSet()
        val lostCurrent = currentStart?.let(isStopped) == true || activePathId in lostPaths
        if (lostPaths.isEmpty() && !lostCurrent) return false
        finished.removeAll { it.pathId in lostPaths }
        closedPaths.removeAll(lostPaths)
        if (lostCurrent) { currentStart = null; activePathId = null }
        releaseUnused()
        revision++
        return true
    }

    private fun releaseUnused() {
        val iterator = owned.iterator()
        while (iterator.hasNext()) {
            val anchor = iterator.next()
            if (anchor !== currentStart && finished.none { it.startAnchor === anchor || it.endAnchor === anchor }) {
                release(anchor)
                iterator.remove()
            }
        }
    }

    companion object { const val MAX_MEASUREMENTS = 30 }
}
