package com.ilya.rulivo.ar

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text
import com.ilya.rulivo.measure.Point2
import com.ilya.rulivo.measure.PolygonGeometry
import kotlin.math.abs
import kotlin.math.sqrt

internal data class ContourEvaluation(
    val area: Double? = null,
    val perimeter: Double? = null,
    val labelPosition: Point3D? = null,
    val triangles: List<Point3D> = emptyList(),
    val error: String? = null,
)

/** Projects onto an orthonormal plane, so walls, floors and tilted surfaces use the same units. */
internal object ContourGeometry {
    private const val EPS = 1e-9
    fun evaluate(points: List<Point3D>): ContourEvaluation {
        if (points.size < 3) return ContourEvaluation(error = text(R.string.mark_at_least_three_corners))
        if (points.any { !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite() })
            return ContourEvaluation(error = text(R.string.incorrect_coordinates))
        val origin = points.first()
        val vectors = points.map { it - origin }
        val longest = vectors.maxBy { it.dot(it) }
        val normalVector = vectors.map { longest.cross(it) }.maxBy { it.dot(it) }
        if (normalVector.dot(normalVector) <= EPS * EPS)
            return ContourEvaluation(error = text(R.string.the_outline_does_not_form_an_area))
        val normal = normalVector.unit()
        if (vectors.any { abs(it.dot(normal)) > FigureGeometry.PLANE_RESIDUAL_METERS })
            return ContourEvaluation(error = text(R.string.contour_same_plane))
        val u = longest.unit()
        val v = normal.cross(u)
        val outline = vectors.map { Point2(it.dot(u), it.dot(v)) }
        PolygonGeometry.validate(outline)?.let { return ContourEvaluation(error = it) }
        val result = PolygonGeometry.calculate(outline)
        val indices = triangulate(outline)
        if (indices.isEmpty()) return ContourEvaluation(error = text(R.string.the_outline_does_not_form_an_area))
        fun world(p: Point2) = origin + u * p.x + v * p.y
        val signedTwiceArea = outline.indices.sumOf { i -> cross(outline[i], outline[(i + 1) % outline.size]) }
        val centroid = Point2(
            outline.indices.sumOf { i -> val a = outline[i]; val b = outline[(i + 1) % outline.size]; (a.x + b.x) * cross(a, b) } / (3 * signedTwiceArea),
            outline.indices.sumOf { i -> val a = outline[i]; val b = outline[(i + 1) % outline.size]; (a.y + b.y) * cross(a, b) } / (3 * signedTwiceArea),
        )
        val triangles = indices.chunked(3).map { tri -> tri.map { outline[it] } }
        // A concave polygon's centroid can fall outside it; use an interior triangle in that case.
        val label = if (triangles.any { insideTriangle(centroid, it[0], it[1], it[2]) }) centroid else {
            val tri = triangles.maxBy { abs(turn(it[0], it[1], it[2])) }
            Point2(tri.sumOf { it.x } / 3, tri.sumOf { it.y } / 3)
        }
        return ContourEvaluation(result.area,
            points.indices.sumOf { DistanceCalculator.distanceMeters(points[it], points[(it + 1) % points.size]) },
            world(label), indices.map { world(outline[it]) })
    }

    private fun triangulate(points: List<Point2>): List<Int> {
        val remaining = points.indices.toMutableList()
        val winding = if (points.indices.sumOf { cross(points[it], points[(it + 1) % points.size]) } > 0) 1 else -1
        val result = mutableListOf<Int>()
        while (remaining.size > 3) {
            var clipped = false
            for (i in remaining.indices) {
                val a = remaining[(i + remaining.size - 1) % remaining.size]
                val b = remaining[i]
                val c = remaining[(i + 1) % remaining.size]
                val turn = turn(points[a], points[b], points[c]) * winding
                if (abs(turn) <= EPS) { remaining.removeAt(i); clipped = true; break }
                if (turn < 0 || remaining.any { it != a && it != b && it != c &&
                        insideTriangle(points[it], points[a], points[b], points[c]) }) continue
                result.addAll(listOf(a, b, c))
                remaining.removeAt(i)
                clipped = true
                break
            }
            if (!clipped) return emptyList()
        }
        if (remaining.size == 3) result.addAll(remaining)
        return result
    }

    private fun insideTriangle(p: Point2, a: Point2, b: Point2, c: Point2): Boolean {
        val turns = listOf(turn(a, b, p), turn(b, c, p), turn(c, a, p))
        return turns.all { it >= -EPS } || turns.all { it <= EPS }
    }
    private fun cross(a: Point2, b: Point2) = a.x * b.y - a.y * b.x
    private fun turn(a: Point2, b: Point2, c: Point2) = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
    private operator fun Point3D.minus(b: Point3D) = Point3D(x - b.x, y - b.y, z - b.z)
    private operator fun Point3D.plus(b: Point3D) = Point3D(x + b.x, y + b.y, z + b.z)
    private operator fun Point3D.times(scale: Double) = Point3D(x * scale, y * scale, z * scale)
    private fun Point3D.dot(b: Point3D) = x * b.x + y * b.y + z * b.z
    private fun Point3D.cross(b: Point3D) = Point3D(y * b.z - z * b.y, z * b.x - x * b.z, x * b.y - y * b.x)
    private fun Point3D.unit() = this * (1 / sqrt(dot(this)))
}
