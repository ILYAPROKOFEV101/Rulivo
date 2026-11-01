package com.ilya.rulivo.ar

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import com.ilya.rulivo.measure.Point2
import com.ilya.rulivo.measure.PolygonGeometry
import kotlin.math.*

/** Coordinates relative to the first surface anchor: X/Z are the base, Y its normal. */
data class FigureEdge(val start: Point3D, val end: Point3D)
data class FigureEvaluation(
    val edges: List<FigureEdge> = emptyList(),
    val outline: List<Point2> = emptyList(),
    val dimensions: Map<String, Double> = emptyMap(),
    val area: Double? = null,
    val volume: Double? = null,
    val complete: Boolean = false,
    val baseValid: Boolean = false,
    val error: String? = null,
)

/** The exact same geometry is used for preview, GL rendering and the accepted numeric snapshot. */
object FigureGeometry {
    const val PLANE_RESIDUAL_METERS = 0.03
    const val MIN_HEIGHT_METERS = 0.001
    const val MAX_HEIGHT_METERS = 1000.0
    private const val EPS = 1e-6

    fun validHeight(type: CaptureType,height: Double)=height.isFinite()&&
        abs(height) in MIN_HEIGHT_METERS..MAX_HEIGHT_METERS&&(type!=CaptureType.ROOM_VOLUME||height>0)

    /** An explicitly entered extrusion, never a fabricated AR surface or anchor. */
    fun withHeight(type: CaptureType,base: List<Point3D>,baseCount: Int?,height: Double?): FigureEvaluation {
        val floor=evaluate(type,base,baseCount)
        if(!floor.baseValid||height==null)return floor
        if(!validHeight(type,height))return floor.copy(error=text(R.string.the_height_must_be_between_0_001))
        val origin=base.first()
        val top=origin.copy(y=if(type==CaptureType.ROOM_VOLUME)height else origin.y+height)
        return evaluate(type,base+top,baseCount)
    }

    fun evaluate(type: CaptureType, samples: List<Point3D>, baseCount: Int? = null): FigureEvaluation {
        if (samples.any { !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite() })
            return FigureEvaluation(error = text(R.string.failed_to_determine_point_coordinates))
        return when (type) {
            CaptureType.BOX_VOLUME -> box(samples)
            CaptureType.CYLINDER_VOLUME -> cylinder(samples)
            CaptureType.AREA, CaptureType.ROOM_FLOOR, CaptureType.ROOM_VOLUME -> contour(type, samples, baseCount)
            else -> FigureEvaluation()
        }
    }

    private fun box(samples: List<Point3D>): FigureEvaluation {
        if (samples.size < 2) return FigureEvaluation()
        val a = samples[0]
        val b = samples[1]
        if (samples.take(3).any { abs(it.y - a.y) > PLANE_RESIDUAL_METERS })
            return FigureEvaluation(error = text(R.string.the_base_must_lie_in_the_same))
        val length = hypot(b.x - a.x, b.z - a.z)
        if (length <= EPS) return FigureEvaluation(error = text(R.string.mark_the_other_end_of_the_first))
        val ux = (b.x - a.x) / length
        val uz = (b.z - a.z) / length
        val baseB = Point3D(b.x, a.y, b.z)
        if (samples.size < 3) return FigureEvaluation(edges = listOf(FigureEdge(a, baseB)), dimensions = mapOf("length" to length))
        val c = samples[2]
        val along = (c.x - a.x) * ux + (c.z - a.z) * uz
        val signedWidth = (c.x - a.x) * -uz + (c.z - a.z) * ux
        val width = abs(signedWidth)
        if (width <= EPS) return FigureEvaluation(error = text(R.string.mark_the_opposite_edge_of_the_base))
        if (along < -PLANE_RESIDUAL_METERS || along > length + PLANE_RESIDUAL_METERS)
            return FigureEvaluation(error = text(R.string.mark_the_edge_opposite_the_first_edge))
        val vx = -uz * sign(signedWidth)
        val vz = ux * sign(signedWidth)
        val base = listOf(a, baseB, Point3D(baseB.x + vx * width, a.y, baseB.z + vz * width), Point3D(a.x + vx * width, a.y, a.z + vz * width))
        val dimensions = linkedMapOf("length" to length, "width" to width)
        val result = FigureEvaluation(loop(base), base.map { Point2(it.x, it.z) }, dimensions, length * width, baseValid = true)
        if (samples.size < 4) return result
        val top = samples[3]
        val h = top.y - a.y
        if (abs(h) <= EPS) return result.copy(error = text(R.string.point_to_the_opposite_face_set_the))
        val topU = (top.x - a.x) * ux + (top.z - a.z) * uz
        val topV = (top.x - a.x) * vx + (top.z - a.z) * vz
        if (topU < -PLANE_RESIDUAL_METERS || topU > length + PLANE_RESIDUAL_METERS ||
            topV < -PLANE_RESIDUAL_METERS || topV > width + PLANE_RESIDUAL_METERS)
            return result.copy(error = text(R.string.the_top_point_must_be_above_the))
        dimensions["height"] = abs(h)
        return result.copy(edges = prism(base, h), dimensions = dimensions, volume = length * width * abs(h), complete = true)
    }

    private fun cylinder(samples: List<Point3D>): FigureEvaluation {
        if (samples.size < 2) return FigureEvaluation()
        val center = samples[0]
        val rim = samples[1]
        if (abs(rim.y - center.y) > PLANE_RESIDUAL_METERS)
            return FigureEvaluation(error = text(R.string.the_center_and_edge_must_be_in))
        val radius = hypot(rim.x - center.x, rim.z - center.z)
        if (radius <= EPS) return FigureEvaluation(error = text(R.string.mark_the_edge_of_the_circle_not))
        val angle = atan2(rim.z - center.z, rim.x - center.x)
        val base = (0 until 32).map { i ->
            val theta = angle + i * 2.0 * Math.PI / 32
            Point3D(center.x + radius * cos(theta), center.y, center.z + radius * sin(theta))
        }
        val area = Math.PI * radius * radius
        val result = FigureEvaluation(loop(base) + FigureEdge(center, Point3D(rim.x, center.y, rim.z)),
            dimensions = mapOf("diameter" to radius * 2), area = area, baseValid = true)
        if (samples.size < 3) return result
        val top = samples[2]
        val h = top.y - center.y
        if (abs(h) <= EPS) return result.copy(error = text(R.string.mark_the_opposite_base_or_its_edge))
        if (hypot(top.x - center.x, top.z - center.z) > radius + PLANE_RESIDUAL_METERS)
            return result.copy(error = text(R.string.the_height_point_must_be_above_the))
        val upper = base.map { it.copy(y = it.y + h) }
        val edges = loop(base) + loop(upper) + listOf(0, 8, 16, 24).map { FigureEdge(base[it], upper[it]) }
        return result.copy(edges = edges, dimensions = mapOf("diameter" to radius * 2, "height" to abs(h)), volume = area * abs(h), complete = true)
    }

    private fun contour(type: CaptureType, samples: List<Point3D>, baseCount: Int?): FigureEvaluation {
        val floor = samples.take(baseCount ?: samples.size)
        val outline = floor.map { Point2(it.x, it.z) }
        val flat = floor.map { it.copy(y = 0.0) }
        val preliminary = flat.zipWithNext { a, b -> FigureEdge(a, b) }
        if (floor.any { abs(it.y) > PLANE_RESIDUAL_METERS })
            return FigureEvaluation(preliminary, outline, error = text(R.string.the_base_points_must_lie_in_the))
        if (floor.size < 3) return FigureEvaluation(preliminary, outline)
        val error = PolygonGeometry.validate(outline)
        if (error != null) return FigureEvaluation(preliminary, outline, error = error)
        val polygon = PolygonGeometry.calculate(outline)
        val result = FigureEvaluation(loop(flat), outline, mapOf("area" to polygon.area, "perimeter" to polygon.perimeter),
            polygon.area, complete = type != CaptureType.ROOM_VOLUME, baseValid = true)
        if (type != CaptureType.ROOM_VOLUME || baseCount == null || samples.size <= baseCount) return result
        val top = samples[baseCount]
        if (top.y <= EPS) return result.copy(error = text(R.string.mark_the_ceiling_or_top_of_the_87))
        if (!contains(outline, Point2(top.x, top.z)))
            return result.copy(error = text(R.string.the_ceiling_point_must_be_inside_the))
        return result.copy(edges = prism(flat, top.y), dimensions = result.dimensions + ("height" to top.y),
            volume = polygon.area * top.y, complete = true)
    }

    private fun loop(points: List<Point3D>) = points.indices.map { FigureEdge(points[it], points[(it + 1) % points.size]) }
    private fun prism(base: List<Point3D>, height: Double): List<FigureEdge> {
        val top = base.map { it.copy(y = it.y + height) }
        return loop(base) + loop(top) + base.indices.map { FigureEdge(base[it], top[it]) }
    }
    private fun contains(polygon: List<Point2>, p: Point2): Boolean {
        var inside = false
        for (i in polygon.indices) {
            val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]
            val dx = b.x - a.x; val dy = b.y - a.y
            val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
            if (hypot(p.x - a.x - t * dx, p.y - a.y - t * dy) <= PLANE_RESIDUAL_METERS) return true
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        }
        return inside
    }
}
