package com.ilya.rulivo.ui.components

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ilya.rulivo.ui.measurement.MeasurementUiState
import com.ilya.rulivo.ui.measurement.MeasurementUnit
import com.ilya.rulivo.ui.measurement.cameraGuidance
import com.ilya.rulivo.ui.measurement.cameraMotionCue
import com.ilya.rulivo.ui.measurement.scanActionLabel
import com.ilya.rulivo.ui.theme.CameraPanel
import com.ilya.rulivo.ui.theme.Ink
import com.ilya.rulivo.ui.theme.Lime
import com.ilya.rulivo.ui.theme.Muted

@Composable
fun MeasurementControls(
    state: MeasurementUiState,
    onPlace: () -> Unit,
    onReset: () -> Unit,
    onUnitChange: (MeasurementUnit) -> Unit,
    onUndo: () -> Unit,
    onCancelCurrent: () -> Unit,
    modifier: Modifier = Modifier,
    onHelp: () -> Unit = {},
    animationsEnabled: Boolean = true,
    onContinue: () -> Unit = {},
    onCloseContour: () -> Unit = {},
) {
    val guidance = state.cameraGuidance()
    Surface(modifier, shape = RoundedCornerShape(26.dp), color = CameraPanel) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!state.scanPhase.isPreparing) state.cameraMotionCue()?.let {
                CameraMotionGuide(it, animationsEnabled = animationsEnabled, showCaption = false)
            }
            if (!state.scanPhase.isPreparing) Text(guidance.title, color = Lime,
                style = MaterialTheme.typography.titleMedium)
            Text(guidance.instruction, modifier = Modifier.fillMaxWidth(), color = Color.White,
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            if (state.scanPhase.isPreparing) {
                Button(onClick = onPlace, enabled = state.canBeginMeasurement,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(state.scanActionLabel)
                }
                TextButton(onClick = onHelp, modifier = Modifier.fillMaxWidth()) { Text(text(R.string.need_help)) }
            } else {
                guidance.detail?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                if (state.canContinue) Button(onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(text(R.string.continue_line))
                }
                Button(onClick = onPlace, enabled = state.canPlacePoint && !state.segmentLimitReached && !state.atLimit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (state.canPlacePoint) Lime else Color(0xFF354340),
                        contentColor = if (state.canPlacePoint) Ink else Muted, disabledContainerColor = Color(0xFF354340), disabledContentColor = Muted)) {
                    Text(when {
                        state.atLimit -> text(R.string.limit_reached_30_lines)
                        state.isContinuing && state.segmentLimitReached -> text(R.string.contour_close_or_finish)
                        state.isContinuing -> text(R.string.contour_add_corner)
                        state.pointCount == 0 -> text(R.string.place_first_point)
                        else -> text(R.string.place_second_point)
                    }, textAlign = TextAlign.Center)
                }
                if (state.isContinuing) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onCloseContour, enabled = state.canCloseContour,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) {
                            Text(text(R.string.contour_close), textAlign = TextAlign.Center)
                        }
                        TextButton(onClick = onCancelCurrent, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(text(R.string.contour_finish_open), textAlign = TextAlign.Center)
                        }
                    }
                }
                UnitSelector(state.unit, onUnitChange, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onUndo, enabled = state.measurements.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Text(text(R.string.undo_last), textAlign = TextAlign.Center)
                    }
                    TextButton(onClick = onReset, enabled = state.hasElements, modifier = Modifier.weight(1f)) { Text(text(R.string.clear_all)) }
                }
                if (state.pointCount == 1 && !state.isContinuing) TextButton(onClick = onCancelCurrent, modifier = Modifier.fillMaxWidth()) {
                    Text(text(R.string.remove_first_point))
                }
            }
        }
    }
}

@Composable
private fun UnitSelector(unit: MeasurementUnit, onChange: (MeasurementUnit) -> Unit, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(Ink).selectableGroup()) {
        MeasurementUnit.entries.forEach { option ->
            val selected = unit == option
            Text(option.label, color = if (selected) Ink else Muted, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Lime else Color.Transparent)
                    .selectable(selected, role = Role.RadioButton, onClick = { onChange(option) })
                    .semantics { contentDescription = when (option) {
                        MeasurementUnit.AUTO -> text(R.string.automatic_units)
                        MeasurementUnit.CENTIMETERS -> text(R.string.centimeters)
                        MeasurementUnit.METERS -> text(R.string.meters)
                    } }
                    .padding(horizontal = 8.dp, vertical = 14.dp))
        }
    }
}
