package com.ilya.rulivo.measure

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import java.io.Serializable
import kotlin.math.abs
import kotlin.math.hypot

data class Point2(val x: Double, val y: Double) : Serializable
data class PolygonResult(val area: Double, val perimeter: Double)

/** Metres in an orthonormal plane. Numerical tolerances are not AR accuracy claims. */
object PolygonGeometry {
    private const val EPS = 1e-9
    fun validate(points: List<Point2>): String? {
        if (points.size < 3) return text(R.string.mark_at_least_three_corners)
        if (points.size > 32) return text(R.string.maximum_32_corners_per_contour)
        if (points.any { !it.x.isFinite() || !it.y.isFinite() }) return text(R.string.incorrect_coordinates)
        for (i in points.indices) for (j in i + 1 until points.size) {
            if (distance(points[i], points[j]) < EPS) return text(R.string.corners_and_overlap, i + 1, j + 1)
        }
        for (i in points.indices) {
            val a = points[(i + points.size - 1) % points.size]
            val b = points[i]
            val c = points[(i + 1) % points.size]
            if (abs(cross(a, b, c)) <= EPS && (a.x-b.x)*(c.x-b.x)+(a.y-b.y)*(c.y-b.y) > EPS)
                return text(R.string.edges_at_corner_overlap, i + 1)
            for (j in i + 1 until points.size) {
                if (j == i + 1 || (i == 0 && j == points.lastIndex)) continue
                if (intersects(points[i], points[(i+1)%points.size], points[j], points[(j+1)%points.size]))
                    return text(R.string.edges_and_intersect, i + 1, j + 1)
            }
        }
        if (!area(points).isFinite() || area(points) <= EPS) return text(R.string.the_outline_does_not_form_an_area)
        if (!perimeter(points).isFinite()) return text(R.string.contour_dimensions_too_large)
        return null
    }
    fun calculate(points: List<Point2>): PolygonResult {
        require(validate(points) == null) { validate(points).orEmpty() }
        return PolygonResult(area(points), perimeter(points))
    }
    fun distance(a: Point2, b: Point2) = hypot(a.x-b.x, a.y-b.y)
    private fun area(p: List<Point2>): Double {
        if (p.isEmpty()) return 0.0
        // Translate near the origin to avoid cancellation for large world coordinates.
        val o = p.first()
        return abs(p.indices.sumOf { i -> val a=p[i]; val b=p[(i+1)%p.size]
            (a.x-o.x)*(b.y-o.y)-(b.x-o.x)*(a.y-o.y) }) / 2.0
    }
    private fun perimeter(p: List<Point2>) = p.indices.sumOf { distance(p[it],p[(it+1)%p.size]) }
    private fun cross(a: Point2,b: Point2,c: Point2) = (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x)
    private fun on(a: Point2,b: Point2,p: Point2) = abs(cross(a,b,p))<=EPS &&
        p.x>=minOf(a.x,b.x)-EPS && p.x<=maxOf(a.x,b.x)+EPS &&
        p.y>=minOf(a.y,b.y)-EPS && p.y<=maxOf(a.y,b.y)+EPS
    private fun intersects(a: Point2,b: Point2,c: Point2,d: Point2): Boolean {
        val abC=cross(a,b,c); val abD=cross(a,b,d); val cdA=cross(c,d,a); val cdB=cross(c,d,b)
        return ((abC>EPS && abD < -EPS || abC < -EPS && abD>EPS) &&
            (cdA>EPS && cdB < -EPS || cdA < -EPS && cdB>EPS)) || on(a,b,c)||on(a,b,d)||on(c,d,a)||on(c,d,b)
    }
}
