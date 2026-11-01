package com.ilya.rulivo.ar

internal object MeasurementGeometry {
    // Compute in world coordinates BEFORE projection; a screen-space average drifts in perspective.
    fun midpoint(a: Point3D, b: Point3D) = Point3D((a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2)
}
