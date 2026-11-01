package com.ilya.rulivo.ui.measurement

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.ilya.rulivo.ar.*
import com.ilya.rulivo.measure.Quantities
import com.ilya.rulivo.ui.components.CenterReticle
import com.ilya.rulivo.ui.components.ScanProgressCard
import com.ilya.rulivo.ui.components.CameraMotionGuide
import com.ilya.rulivo.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CaptureOverlay(state: MeasurementUiState,request: CaptureRequest,onBegin: ()->Unit,
    onCommand: (CaptureCommand)->Unit,onCancel: ()->Unit,animationsEnabled: Boolean=true) {
    val frame=state.capture?.takeIf {it.requestId==request.id}
    fun command(action: CaptureAction,index: Int?=null){frame?.let {onCommand(CaptureCommand(request.id,it.revision,action,index))}}
    var showPoints by remember(request.id){mutableStateOf(false)}
    var showHelp by remember(request.id){mutableStateOf(false)}
    val guidance=state.cameraGuidance(frame)
    var heightInputValid by remember(request.id){mutableStateOf(true)}
    val controlsScroll=rememberScrollState()
    LaunchedEffect(request.id,frame?.heightAvailable,frame?.tracking,state.scanPhase,state.trackingIssue) {
        controlsScroll.scrollTo(0)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape=maxWidth>maxHeight
        val panelWidth=if(landscape)minOf(320.dp,maxWidth/2-60.dp)else minOf(480.dp,maxWidth-32.dp)
        val panelHeight=if(landscape)maxHeight-64.dp else maxHeight/2-70.dp
        CenterReticle(frame?.canPlace==true||frame?.cameraHeightReady==true,state.hasTarget||frame?.cameraHeightReady==true,Modifier.align(Alignment.Center))
        Column(Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(16.dp).widthIn(max=if(landscape)panelWidth else 340.dp).heightIn(max=panelHeight.coerceAtLeast(100.dp)).verticalScroll(rememberScrollState()).testTag("capture-header"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Surface(color=CameraPanel,shape=RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(when(request.type){CaptureType.DISTANCE->inputLabel(request.target);CaptureType.HEIGHT->text(R.string.vertical_height);CaptureType.AREA->text(R.string.surface_outline);CaptureType.ROOM_FLOOR->text(R.string.floor_outline);CaptureType.BOX_VOLUME->when(request.target){"fitSpace"->text(R.string.message_1_2_recess);"fitItem"->text(R.string.message_2_2_object);else->text(R.string.object_volume)};CaptureType.CYLINDER_VOLUME->text(R.string.cylinder_volume);CaptureType.ROOM_VOLUME->text(R.string.room_volume)},modifier=Modifier.weight(1f))
                    TextButton(onClick={showHelp=true}){Text(text(R.string.help))}
                    TextButton(onClick=onCancel){Text(text(R.string.back))}
                }
            }
            if(state.scanPhase.isPreparing)ScanProgressCard(state,animationsEnabled=animationsEnabled&&!showHelp)
            else if(frame?.volume!=null||frame?.area!=null||frame?.meters!=null||frame?.dimensions?.isNotEmpty()==true)Surface(color=CameraPanel,shape=RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp)) {
                    val f=frame!!
                    val main=when {f.volume!=null->text(R.string.approximate_cubic_meters, Quantities.number(f.volume));f.area!=null->text(R.string.approximate_square_meters, Quantities.number(f.area));f.meters!=null->text(R.string.approximate_meters, Quantities.number(f.meters));else->text(R.string.shape_base)}
                    Text(main,color=Lime,style=MaterialTheme.typography.headlineSmall)
                    val dimensions=f.dimensions.filterKeys {it in listOf("length","width","height","diameter")}
                    if(dimensions.isNotEmpty())Text(dimensions.entries.joinToString(" · "){(key,value)->text(R.string.dimension_label_meters, Quantities.label(key), Quantities.number(value))},style=MaterialTheme.typography.bodySmall)
                    Text(if(!f.tracking)text(R.string.last_value_paused) else if(f.cameraHeight)text(R.string.the_top_follows_the_camera) else if(f.heightAvailable&&f.manualHeight&&f.signedHeight!=null)text(R.string.base_camera_height, f.heightSource.title) else if(f.preview||(!request.isFigure&&f.count==1))text(R.string.preview_mark_point) else text(R.string.points_locked),color=Muted)
                }
            }
        }
        Surface(color=CameraPanel,shape=RoundedCornerShape(24.dp),modifier=Modifier
            .align(if(landscape)Alignment.BottomEnd else Alignment.BottomCenter).safeDrawingPadding().imePadding().padding(16.dp)
            .widthIn(max=panelWidth).fillMaxWidth().heightIn(max=panelHeight.coerceAtLeast(100.dp)).testTag("capture-controls")) {
            Column(Modifier.verticalScroll(controlsScroll).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                if(!state.scanPhase.isPreparing&&(frame?.heightAvailable!=true||state.trackingIssue!=null||frame.tracking==false)) {
                    state.cameraMotionCue(frame)?.let {CameraMotionGuide(it,animationsEnabled=animationsEnabled&&!showHelp,showCaption=false)}
                }
                if(frame?.heightAvailable!=true||state.scanPhase.isPreparing||state.trackingIssue!=null||frame.tracking==false) {
                    if(!state.scanPhase.isPreparing)Text(guidance.title,color=Lime,style=MaterialTheme.typography.titleMedium)
                    Text(guidance.instruction)
                    if(!state.scanPhase.isPreparing)guidance.detail?.let {Text(it,color=Muted,style=MaterialTheme.typography.bodySmall)}
                }
                if(state.scanPhase.isPreparing) {
                    Button(onClick=onBegin,enabled=state.canBeginMeasurement,modifier=Modifier.fillMaxWidth()){Text(state.scanActionLabel)}
                    TextButton(onClick={showHelp=true},modifier=Modifier.fillMaxWidth()){Text(text(R.string.need_help))}
                }
                else {
                    if(frame?.heightAvailable==true)CaptureHeightControls(request,frame,
                        onHeight={onCommand(CaptureCommand(request.id,frame.revision,CaptureAction.SET_HEIGHT,height=it))},
                        onValidity={heightInputValid=it},onCamera={command(CaptureAction.AIM_HEIGHT)},onFreeze={command(CaptureAction.FREEZE_HEIGHT)},
                        animationsEnabled=animationsEnabled&&!showHelp)
                    if(frame?.showPlace!=false)Button(onClick={command(CaptureAction.PLACE)},enabled=frame?.canPlace==true,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)) {
                        Text(if(frame?.replacement!=null)text(R.string.replace_point, frame.replacement+1) else frame?.placeLabel?.takeIf {it.isNotBlank()} ?: text(R.string.place_point_282))
                    }
                    if(request.isContour&&frame?.closed!=true)Button(onClick={command(CaptureAction.CLOSE)},enabled=frame?.canClose==true,modifier=Modifier.fillMaxWidth()){Text(if(request.type==CaptureType.ROOM_VOLUME)text(R.string.close_floor_outline)else text(R.string.close_outline))}
                    if(frame!=null&&!frame.cameraHeight&&(frame.heightAvailable||frame.canConfirm||!frame.showPlace&&(!request.isContour||frame.closed)||frame.closed&&request.type!=CaptureType.ROOM_VOLUME))Button(onClick={command(CaptureAction.CONFIRM)},enabled=frame.canConfirm&&(!frame.heightAvailable||heightInputValid),modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)){Text(if(request.target=="fitSpace")text(R.string.next_build_the_object)else text(R.string.use_result))}
                    FlowRow {
                        TextButton(onClick={command(CaptureAction.UNDO)},enabled=(frame?.count ?:0)>0){Text(if(frame?.replacement!=null)text(R.string.cancel_replacement) else if(frame?.heightAvailable==true&&frame.signedHeight!=null||request.type==CaptureType.ROOM_VOLUME&&frame?.closed==true&&frame.count>(frame.baseCount ?:frame.count))text(R.string.remove_height) else if(frame?.closed==true)text(R.string.reopen_outline) else text(R.string.remove_point))}
                        TextButton(onClick={showPoints=true},enabled=(frame?.count ?:0)>0){Text(text(R.string.edit))}
                        TextButton(onClick={command(CaptureAction.CLEAR)},enabled=(frame?.count ?:0)>0){Text(text(R.string.start_over))}
                    }
                }
            }
        }
    }
    if(showHelp)HelpDialog {showHelp=false}
    if(showPoints&&frame!=null)AlertDialog(onDismissRequest={showPoints=false},title={Text(text(R.string.remeasure_point))},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
        for(i in 0 until frame.count)TextButton(onClick={command(CaptureAction.REPLACE,i);showPoints=false},modifier=Modifier.fillMaxWidth()) {
            Text(text(R.string.point, i+1)+(frame.points.getOrNull(i)?.let {text(R.string.point_coordinates_meters, Quantities.number(it.x), Quantities.number(it.y))} ?: ""))
        }
    }},confirmButton={TextButton(onClick={showPoints=false}){Text(text(R.string.close))}})
}
