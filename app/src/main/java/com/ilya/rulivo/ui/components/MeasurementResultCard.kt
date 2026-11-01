package com.ilya.rulivo.ui.components

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilya.rulivo.ui.measurement.ContourFormatter
import com.ilya.rulivo.ui.measurement.DistanceFormatter
import com.ilya.rulivo.ui.measurement.MeasurementUiState
import com.ilya.rulivo.ui.theme.CameraPanel
import com.ilya.rulivo.ui.theme.Lime
import com.ilya.rulivo.ui.theme.Muted

@Composable
fun MeasurementResultCard(state: MeasurementUiState, modifier: Modifier = Modifier) {
    Surface(modifier.widthIn(min = 180.dp), shape = RoundedCornerShape(22.dp), color = CameraPanel,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            val contour = state.lastContour
            if (contour != null) {
                Text(text(R.string.surface_area), color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                Text(contour.areaSquareMeters?.let { ContourFormatter.area(it, state.unit) } ?: "—",
                    color = Lime, fontFamily = FontFamily.Monospace, fontSize = 30.sp)
                Text(text(R.string.contour_perimeter_label, contour.perimeterMeters?.let { DistanceFormatter.format(it, state.unit) } ?: "—"),
                    color = Muted, style = MaterialTheme.typography.labelMedium)
                Text(if (contour.isTracking) text(R.string.approximate_measurement) else text(R.string.last_value_paused),
                    color = Muted, style = MaterialTheme.typography.labelSmall)
            } else {
                Text(if (state.pointCount == 1) text(R.string.preview) else text(R.string.last_line), color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                Text(state.distanceMeters?.let { DistanceFormatter.format(it, state.unit) } ?: "—",
                    color = if (state.distanceMeters == null) Color.White else Lime,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 34.sp,
                    lineHeight = 44.sp)
                Text(if (state.distanceMeters == null) text(R.string.of_2_points, state.pointCount) else
                    if (state.resultIsTracking) text(R.string.approximate_measurement) else text(R.string.last_value_paused),
                    style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            Text(text(R.string.lines_30, state.measurements.size), style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}
