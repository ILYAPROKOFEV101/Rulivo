package com.ilya.rulivo.ui.components

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilya.rulivo.ui.measurement.MeasurementUiState
import com.ilya.rulivo.ui.measurement.cameraGuidance
import com.ilya.rulivo.ui.measurement.cameraMotionCue
import com.ilya.rulivo.ui.theme.CameraPanel
import com.ilya.rulivo.ui.theme.Lime
import com.ilya.rulivo.ui.theme.Muted

@Composable
fun ScanProgressCard(state: MeasurementUiState, modifier: Modifier = Modifier, animationsEnabled: Boolean = true) {
    val guidance = state.cameraGuidance()
    Surface(modifier.widthIn(max = 280.dp), shape = RoundedCornerShape(22.dp), color = CameraPanel,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text(R.string.message_1_prepare_to_measure), color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
            state.cameraMotionCue()?.let { CameraMotionGuide(it, animationsEnabled = animationsEnabled) }
            Text(guidance.title,
                style = MaterialTheme.typography.titleLarge,
                color = if (state.canBeginMeasurement) Lime else Color.White)
            LinearProgressIndicator(progress = { state.scanProgress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(), color = Lime, trackColor = Color.White.copy(alpha = 0.1f))
            guidance.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
        }
    }
}
