package com.ilya.rulivo.ui.measurement

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ilya.rulivo.ar.*
import com.ilya.rulivo.ui.components.CameraMotionGuide
import com.ilya.rulivo.measure.Quantities
import com.ilya.rulivo.ui.theme.Lime
import com.ilya.rulivo.ui.theme.Muted

@Composable
internal fun CaptureHeightControls(request: CaptureRequest,frame: CaptureFrame,
    onHeight: (Double?)->Unit,onValidity: (Boolean)->Unit,onCamera: ()->Unit,onFreeze: ()->Unit,
    animationsEnabled: Boolean=true) {
    val focus=LocalFocusManager.current
    val largeFont=LocalDensity.current.fontScale>1.3f
    var focused by remember {mutableStateOf(false)}
    var text by remember(request.id,frame.revision,frame.manualHeight) {
        mutableStateOf(frame.signedHeight?.let {Quantities.editable(it)} ?: "")
    }
    LaunchedEffect(frame.manualHeight,frame.cameraHeight) {if(!frame.manualHeight||frame.cameraHeight)focus.clearFocus()}
    LaunchedEffect(frame.signedHeight,frame.manualHeight,focused) {
        if(!focused) {
            text=frame.signedHeight?.let {Quantities.editable(it)} ?: ""
        }
    }
    val value=HeightEditing.parse(text)
    val valid=value!=null&&FigureGeometry.validHeight(request.type,value)
    LaunchedEffect(valid){onValidity(valid)}
    fun applyHeight(height: Double) {
        text=Quantities.editable(height)
        onHeight(height.takeIf {FigureGeometry.validHeight(request.type,it)})
        focus.clearFocus()
    }
    Column(Modifier.fillMaxWidth().testTag("capture-height"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(text(R.string.height_from_base),color=Lime,style=MaterialTheme.typography.titleMedium)
        if(frame.cameraHeight) {
            if(frame.tracking&&frame.cameraHeightReady)CameraMotionGuide(CameraMotionCue.HEIGHT,animationsEnabled=animationsEnabled)
            else Text(if(!frame.tracking)text(R.string.pause_top_position_saved)
                else text(R.string.step_slightly_to_the_side_of_the),style=MaterialTheme.typography.bodySmall,color=Muted)
            Button(onClick=onFreeze,enabled=frame.tracking&&frame.cameraHeightReady&&frame.signedHeight!=null,
                modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)) {Text(text(R.string.lock_height))}
        } else OutlinedButton(onClick=onCamera,enabled=frame.tracking,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Text(text(R.string.move_the_top_with_the_camera))
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick={applyHeight(HeightEditing.step(request.type,value ?: 0.0,-0.05))},
                enabled=frame.tracking&&(value ?: 0.0)>(if(request.type==CaptureType.ROOM_VOLUME)0.0 else -FigureGeometry.MAX_HEIGHT_METERS),
                colors=ButtonDefaults.filledTonalButtonColors(containerColor=Lime.copy(alpha=0.15f),contentColor=Lime),
                modifier=Modifier.heightIn(min=64.dp).semantics {contentDescription=text(R.string.lower_the_top_by_5_cm)},
                contentPadding=PaddingValues(10.dp)) {Text(text(R.string.n_5_cm),textAlign=TextAlign.Center)}
            OutlinedTextField(value=text,onValueChange={input->
                text=input.take(16)
                val parsed=HeightEditing.parse(text)
                onHeight(parsed?.takeIf {FigureGeometry.validHeight(request.type,it)})
            },label={Text(if(largeFont)text(R.string.unit_meter)else text(R.string.top_above_base_m))},placeholder={Text(Quantities.number(0.0))},singleLine=true,
                enabled=frame.tracking,isError=text.isNotBlank()&&!valid,
                textStyle=MaterialTheme.typography.headlineSmall,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=ImeAction.Done),
                keyboardActions=KeyboardActions(onDone={focus.clearFocus()}),
                modifier=Modifier.weight(1f).onFocusChanged {
                    focused=it.isFocused
                    if(it.isFocused&&frame.cameraHeight)onHeight(frame.signedHeight)
                }.semantics {contentDescription=text(R.string.height_in_meters)}.testTag("capture-height-input"))
            FilledTonalButton(onClick={applyHeight(HeightEditing.step(request.type,value ?: 0.0,0.05))},
                enabled=frame.tracking&&(value ?: 0.0)<FigureGeometry.MAX_HEIGHT_METERS,
                colors=ButtonDefaults.filledTonalButtonColors(containerColor=Lime.copy(alpha=0.15f),contentColor=Lime),
                modifier=Modifier.heightIn(min=64.dp).semantics {contentDescription=text(R.string.raise_the_top_by_5_cm)},
                contentPadding=PaddingValues(10.dp)) {Text(text(R.string.n_5_cm_255),textAlign=TextAlign.Center)}
        }
        Text(when {
            !frame.tracking -> text(R.string.paused_height_saved_return_to_the_base)
            text.isNotBlank()&&!valid -> if(request.type==CaptureType.ROOM_VOLUME)text(R.string.height_must_be_greater_than_0_and) else text(R.string.from_1000_to_1000_m_0_is)
            frame.cameraHeight -> text(R.string.base_fixed_you_can_also_enter_a)
            !frame.manualHeight -> text(R.string.camera_height_lock_the_top_or_enter)
            (value ?: 0.0)<0 -> text(R.string.top_below_base_moves_it_up)
            else -> text(R.string.base_fixed_the_top_updates_immediately)
        },color=if(text.isNotBlank()&&!valid)MaterialTheme.colorScheme.error else Muted,style=MaterialTheme.typography.bodySmall)
    }
}
