package com.ilya.rulivo.ui.measurement

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ilya.rulivo.ar.*
import com.ilya.rulivo.measure.*
import com.ilya.rulivo.ui.components.MeasurementIcons
import com.ilya.rulivo.ui.theme.*
import java.text.DateFormat
import java.util.Date
import com.ilya.rulivo.i18n.AppStrings

@Composable
fun RulivoScreen(activity: Activity,measurementModel: MeasurementViewModel,tools: ToolkitViewModel = viewModel(),
    onSupportSettings: () -> Unit = {}) {
    val state by tools.state.collectAsStateWithLifecycle()
    val measurements by measurementModel.uiState.collectAsStateWithLifecycle()
    var cameraStarted by rememberSaveable { mutableStateOf(false) }
    var cameraVisible by remember { mutableStateOf(false) }
    fun openCamera() {cameraStarted=true;cameraVisible=true}
    fun returnToTools(){tools.cancelCapture();cameraVisible=false}
    BackHandler(cameraVisible) {returnToTools()}
    Box(Modifier.fillMaxSize()) {
        if(cameraStarted)MeasurementScreen(activity,measurementModel,visible=cameraVisible,
            onTools={returnToTools()},captureRequest=state.capture,onCaptured={tools.acceptCapture(it);cameraVisible=tools.state.value.capture!=null})
        if(!cameraVisible)ToolkitScreen(state,tools,measurements.measurements,
            onSupportSettings=onSupportSettings,
            onCamera={key,type->tools.requestCapture(key,type);openCamera()},onRuler={tools.cancelCapture();openCamera()},
            onShare={record->activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {type="text/plain";putExtra(Intent.EXTRA_TEXT,measurementText(record))},text(R.string.share_measurement)))})
    }
}

fun measurementText(record: SavedMeasurement): String = buildString {
    appendLine(record.displayName);appendLine(record.localizedResult.title)
    record.localizedResult.metrics.forEach { (key,value)->appendLine(text(R.string.label_value, Quantities.label(key), Quantities.metric(key,value,record.draft.volumeUnit))) }
    appendLine(text(R.string.approximate_rulivo_result))
    record.draft.inputs.forEach { (key,value)->if(value.text.isNotBlank())appendLine(text(R.string.input_shared_with_source, inputLabel(key), value.text, value.source.title)) }
    if(record.localizedResult.outline.isNotEmpty())appendLine(text(R.string.outline_m)+record.localizedResult.outline.joinToString { text(R.string.coordinates_pair, Quantities.number(it.x), Quantities.number(it.y)) })
    record.localizedResult.notes.forEach {appendLine(it)}
}

internal fun inputLabel(key: String) = when(key) {
    "length"->text(R.string.length_m);"width"->text(R.string.width_m);"depth"->text(R.string.depth_m);"height"->text(R.string.height_m);"diameter"->text(R.string.diameter_m)
    "cutLength"->text(R.string.cutout_length_m);"cutWidth"->text(R.string.cutout_width_m)
    "itemWidth"->text(R.string.object_width_m);"itemDepth"->text(R.string.object_depth_m);"itemHeight"->text(R.string.height_of_object_m)
    "gap"->text(R.string.clearance_on_each_side_m);"reserve"->text(R.string.allowance);"area"->text(R.string.area_m);"perimeter"->text(R.string.perimeter_m)
    "packArea"->text(R.string.area_per_pack_m);"coats"->text(R.string.number_of_coats_359);"coverage"->text(R.string.coverage_m_l)
    "excluded"->text(R.string.excluded_length_m);"pieceLength"->text(R.string.board_length_m);else->key
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ToolkitScreen(state: ToolkitState,model: ToolkitViewModel,lines: List<MeasurementReading>,
    onCamera: (String,CaptureType)->Unit,onRuler: ()->Unit,onShare: (SavedMeasurement)->Unit,
    onSupportSettings: () -> Unit = {}) {
    var pendingKind by remember { mutableStateOf<MeasureKind?>(null) }
    var pendingCopy by remember { mutableStateOf<SavedMeasurement?>(null) }
    var lineTarget by remember { mutableStateOf<String?>(null) }
    var boxTarget by remember { mutableStateOf<Boolean?>(null) }
    var contourEditor by remember { mutableStateOf(false) }
    val d=state.draft
    var manual by rememberSaveable(d.kind){mutableStateOf(false)}
    fun build(kind: MeasureKind=d.kind) {
        val (target,type)=figureCapture(kind)
        onCamera(target,type)
    }
    fun startCamera(kind: MeasureKind){manual=false;model.start(kind);build(kind)}
    BackHandler(state.page!=ToolsPage.MENU){model.back()}
    Surface(Modifier.fillMaxSize(),color=Ink) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text(text(R.string.app_name),style=MaterialTheme.typography.headlineMedium)
                if(state.page!=ToolsPage.MENU)TextButton(onClick=model::back) {
                    ButtonIcon(Icons.AutoMirrored.Outlined.ArrowBack)
                    Text(text(R.string.back))
                }
                else TextButton(onClick={model.page(ToolsPage.HISTORY)}){
                    ButtonIcon(Icons.Outlined.BookmarkBorder)
                    Text(text(R.string.saved, state.history.size))
                }
            }
            state.message?.let {Text(it,color=Lime)}
            when(state.page) {
                ToolsPage.MENU -> {
                    Text(text(R.string.what_are_we_measuring),style=MaterialTheme.typography.headlineLarge)
                    Text(text(R.string.build_a_shape_on_the_object_using),color=Muted)
                    if(d.hasData)OutlinedButton(onClick=model::resumeDraft,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                        ButtonIcon(MeasurementIcons.forKind(d.kind))
                        Text(text(R.string.continue_measurement, d.kind.title))
                    }
                    ModeButton(text(R.string.line),text(R.string.line_and_contours),MeasurementIcons.Line,onRuler)
                    val descriptions=mapOf(MeasureKind.BOX to text(R.string.dimensions_and_volume_of_the_object),MeasureKind.CYLINDER to text(R.string.diameter_height_and_capacity),MeasureKind.AREA to text(R.string.surface_area_and_perimeter),MeasureKind.ROOM to text(R.string.floor_contour_and_room_volume),MeasureKind.FIT to text(R.string.recess_object_and_clearance),MeasureKind.MATERIALS to text(R.string.flooring_paint_and_baseboard))
                    MeasureKind.entries.forEach {kind->ModeButton(kind.title,descriptions.getValue(kind),MeasurementIcons.forKind(kind)){
                        if(d.hasData&&state.savedDraft!=d)pendingKind=kind else startCamera(kind)
                    }}
                    Text(text(R.string.ar_measurements_are_approximate_furniture_is_included),style=MaterialTheme.typography.bodySmall,color=Muted)
                    OutlinedButton(onClick=onSupportSettings,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                        ButtonIcon(Icons.Outlined.Settings)
                        Text(text(R.string.settings_dunta_battery_title))
                    }
                }
                ToolsPage.HISTORY -> {
                    Text(text(R.string.saved_measurements),style=MaterialTheme.typography.headlineSmall)
                    if(state.loading)CircularProgressIndicator()
                    else if(state.history.isEmpty())Text(text(R.string.save_the_first_result_it_will_remain),color=Muted)
                    state.history.forEach {record->Surface(onClick={model.open(record)},color=CameraPanel,shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                            Text(record.displayName,style=MaterialTheme.typography.titleMedium)
                            Text(record.localizedResult.title,color=Muted)
                            record.localizedResult.metrics.entries.firstOrNull()?.let {Text(Quantities.metric(it.key,it.value,record.draft.volumeUnit),color=Lime)}
                            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,AppStrings.locale).format(Date(record.createdAt)),style=MaterialTheme.typography.bodySmall,color=Muted)
                        }
                    }}
                }
                ToolsPage.DETAIL -> state.selected?.let {record->
                    Text(record.displayName,style=MaterialTheme.typography.headlineSmall)
                    ResultPanel(record.localizedResult,record.draft.volumeUnit)
                    if(record.localizedResult.outline.isNotEmpty())PlanPreview(record.localizedResult.outline)
                    Panel(text(R.string.input_values)) {
                        record.draft.inputs.forEach { (key,input)->if(input.text.isNotBlank())Text(text(R.string.input_with_source, inputLabel(key), input.text, input.source.title)) }
                        if(record.draft.contour.isNotEmpty())Text(text(R.string.outline, record.draft.contourSource.title))
                        record.localizedResult.outline.forEachIndexed {index,p->Text(text(R.string.corner_m, index+1, Quantities.number(p.x), Quantities.number(p.y)))}
                    }
                    Button(onClick={onShare(record)},modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)){Text(text(R.string.share))}
                    OutlinedButton(onClick={if(d.hasData&&state.savedDraft!=d)pendingCopy=record else model.editCopy(record)},modifier=Modifier.fillMaxWidth()){Text(text(R.string.create_an_editable_copy))}
                    if("area" in record.localizedResult.metrics)TextButton(onClick={model.materials(record.localizedResult)}){Text(text(R.string.calculate_materials))}
                }
                ToolsPage.EDIT -> {
                    Text(d.kind.title,style=MaterialTheme.typography.headlineLarge)
                    val calculation=MeasurementCalculator.calculate(d)
                    if(calculation.result!=null)ResultPanel(calculation.result,d.volumeUnit)
                    else Text(if(manual||d.kind==MeasureKind.MATERIALS)calculation.error.orEmpty()else text(R.string.mark_points_on_the_object_its_outline),color=Muted)
                    Button(onClick={build()},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(if(d.hasData)text(R.string.rebuild_with_camera)else text(R.string.build_with_camera))}
                    TextButton(onClick={manual=!manual}){Text(if(manual)text(R.string.hide_manual_input)else text(R.string.enter_dimensions_manually))}
                    if(!manual&&d.contour.isNotEmpty()) {PlanPreview(d.contour);Text(text(R.string.outline, d.contourSource.title),color=Muted)}
                    if(d.kind in listOf(MeasureKind.BOX,MeasureKind.CYLINDER,MeasureKind.ROOM))FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        VolumeUnit.entries.forEach {unit->FilterChip(d.volumeUnit==unit,{model.change {it.copy(volumeUnit=unit)}},label={Text(unit.title)})}
                    }
                    @Composable fun field(key: String,camera: Boolean=true,type: CaptureType=CaptureType.DISTANCE) {
                        DimensionField(key,d.input(key),onChange={text->model.change {it.withInput(key,text.take(50))}},
                            onCamera=if(camera)({onCamera(key,type)})else null,
                            onLine=if(camera&&lines.any {it.distanceMeters!=null})({lineTarget=key})else null)
                    }
                    when(d.kind) {
                        MeasureKind.BOX,MeasureKind.CYLINDER -> {
                            ToggleRow(text(R.string.internal_dimensions_of_the_container),d.internal){checked->model.change {it.copy(internal=checked)}}
                            Text(if(d.internal)text(R.string.mark_internal_boundaries_and_fill_height)else text(R.string.the_outline_must_enclose_the_object_including),color=Muted)
                            if(manual) {
                                if(d.kind==MeasureKind.BOX){field("length");field("width")}else field("diameter")
                                field("height")
                            }
                        }
                        MeasureKind.AREA,MeasureKind.ROOM -> if(manual) {
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                listOf(FloorShape.RECTANGLE to text(R.string.rectangle),FloorShape.L_SHAPE to text(R.string.l_shaped),FloorShape.CONTOUR to text(R.string.outline_394)).forEach { (shape,title)->
                                    FilterChip(d.shape==shape,{model.change {it.copy(shape=shape)}},label={Text(title)},
                                        leadingIcon={Icon(MeasurementIcons.forShape(shape),contentDescription=null,modifier=Modifier.size(20.dp))})
                                }
                            }
                            if(d.shape==FloorShape.CONTOUR) {
                                if(d.contour.isNotEmpty()){PlanPreview(d.contour);Text(text(R.string.outline, d.contourSource.title),color=Muted)}
                                OutlinedButton(onClick={contourEditor=true},modifier=Modifier.fillMaxWidth()){Text(text(R.string.edit_corners_manually))}
                            }else {
                                field("length");field("width")
                                if(d.shape==FloorShape.L_SHAPE){field("cutLength");field("cutWidth")}
                                calculation.result?.let {PlanPreview(it.outline)}
                            }
                            if(d.kind==MeasureKind.ROOM){Text(text(R.string.you_can_save_the_area_without_a),color=Muted);field("height",type=CaptureType.HEIGHT)}
                        }
                        MeasureKind.FIT -> {
                            if(manual) {
                                Text(text(R.string.inside_dimensions_of_the_recess),style=MaterialTheme.typography.titleMedium)
                                field("width");field("depth");field("height")
                                TextButton(onClick={boxTarget=false}){Text(text(R.string.use_a_saved_object_as_the_recess))}
                                HorizontalDivider()
                                Text(text(R.string.external_dimensions_of_the_item),style=MaterialTheme.typography.titleMedium)
                                field("itemWidth");field("itemDepth");field("itemHeight")
                                TextButton(onClick={boxTarget=true}){Text(text(R.string.use_a_saved_object))}
                            } else {
                                Text(text(R.string.build_the_recess_first_then_the_object),color=Muted)
                                if(d.input("width").text.isNotBlank()) {
                                    Text(text(R.string.recess_m, d.input("width").text, d.input("depth").text, d.input("height").text))
                                    OutlinedButton(onClick={onCamera("fitItem",CaptureType.BOX_VOLUME)},modifier=Modifier.fillMaxWidth()){Text(text(R.string.build_object_with_camera))}
                                }
                                if(d.input("itemWidth").text.isNotBlank())Text(text(R.string.object_m, d.input("itemWidth").text, d.input("itemDepth").text, d.input("itemHeight").text))
                            }
                            field("gap",camera=false)
                            ToggleRow(text(R.string.allow_tilting_onto_a_side),d.allowTipping){checked->model.change {it.copy(allowTipping=checked,orientation=0)}}
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                val labels=listOf(text(R.string.w_d_h),text(R.string.d_w_h),text(R.string.w_h_d),text(R.string.h_w_d),text(R.string.d_h_w),text(R.string.h_d_w))
                                labels.take(if(d.allowTipping)6 else 2).forEachIndexed {index,label->FilterChip(d.orientation==index,{model.change {it.copy(orientation=index)}},label={Text(label)})}
                            }
                        }
                        MeasureKind.MATERIALS -> {
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {MaterialKind.entries.forEach {kind->FilterChip(d.material==kind,{model.change {it.copy(material=kind)}},label={Text(kind.title)})}}
                            if(manual){field("area",camera=false);field("perimeter",camera=false)}
                            else {
                                d.value("area")?.let {Text(text(R.string.area_m_412, Quantities.number(it), d.input("area").source.title))}
                                d.value("perimeter")?.let {Text(text(R.string.perimeter_m_413, Quantities.number(it), d.input("perimeter").source.title))}
                            }
                            if(d.material==MaterialKind.BASEBOARD){field("excluded",camera=false);field("pieceLength",camera=false)}
                            else if(d.material==MaterialKind.FLOOR)field("packArea",camera=false)
                            else {field("coats",camera=false);field("coverage",camera=false)}
                            field("reserve",camera=false)
                        }
                    }
                    OutlinedTextField(d.name,{text->model.change {it.copy(name=text.take(100))}},label={Text(text(R.string.measurement_name))},singleLine=true,modifier=Modifier.fillMaxWidth())
                    Button(onClick=model::save,enabled=calculation.result!=null&&!state.loading&&!state.saving&&state.savedDraft!=d,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                        Text(if(state.saving)text(R.string.saving) else if(state.savedDraft==d)text(R.string.saved_416) else text(R.string.save_result))
                    }
                    if(calculation.result?.metrics?.containsKey("area")==true)OutlinedButton(onClick={model.materials(calculation.result)},modifier=Modifier.fillMaxWidth()){Text(text(R.string.calculate_materials))}
                    TextButton(onClick={model.page(ToolsPage.HISTORY)}){Text(text(R.string.open_saved_measurements))}
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    pendingKind?.let {kind->AlertDialog(onDismissRequest={pendingKind=null},title={Text(text(R.string.unfinished_measurement))},text={Text(text(R.string.continue_the_current_draft_or_start_a))},
        confirmButton={TextButton(onClick={pendingKind=null;startCamera(kind)}){Text(text(R.string.discard_and_start))}},dismissButton={TextButton(onClick={pendingKind=null;model.resumeDraft()}){Text(text(R.string.continue_draft))}})}
    pendingCopy?.let {record->AlertDialog(onDismissRequest={pendingCopy=null},title={Text(text(R.string.replace_current_draft))},text={Text(text(R.string.the_saved_measurement_will_remain_unchanged_the))},
        confirmButton={TextButton(onClick={pendingCopy=null;model.editCopy(record)}){Text(text(R.string.replace_draft))}},dismissButton={TextButton(onClick={pendingCopy=null}){Text(text(R.string.keep_draft))}})}
    lineTarget?.let {target->AlertDialog(onDismissRequest={lineTarget=null},title={Text(text(R.string.select_line))},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(text(R.string.the_last_line_value_in_this_session),color=Muted)
        lines.forEach {line->line.distanceMeters?.let {distance->TextButton(onClick={model.change {it.withInput(target,Quantities.editable(distance),ValueSource.AR)};lineTarget=null}){Text(text(R.string.line_m, line.id, Quantities.number(distance)))}} }
    }},confirmButton={TextButton(onClick={lineTarget=null}){Text(text(R.string.close))}})}
    boxTarget?.let {asItem->AlertDialog(onDismissRequest={boxTarget=null},title={Text(text(R.string.saved_dimensions))},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        val boxes=state.history.filter {it.draft.kind==MeasureKind.BOX&&it.draft.internal!=asItem}
        if(boxes.isEmpty())Text(if(asItem)text(R.string.first_save_the_outside_dimensions_in_object) else text(R.string.first_save_the_inside_dimensions_in_object))
        boxes.forEach {record->TextButton(onClick={model.useBox(record,asItem);boxTarget=null}){Text(record.displayName)}}
    }},confirmButton={TextButton(onClick={boxTarget=null}){Text(text(R.string.close))}})}
    if(contourEditor)ContourEditor(d.contour,onDismiss={contourEditor=false},onSave={points->model.change {it.copy(contour=points,contourSource=ValueSource.MANUAL)};contourEditor=false})
}

@Composable
private fun ModeButton(title: String,description: String,icon: ImageVector,onClick: ()->Unit) {
    Surface(onClick=onClick,color=CameraPanel,shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(Lime.copy(alpha=.09f),RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center) {
                // The adjacent title names the action for TalkBack; the shape is decorative.
                Icon(icon,contentDescription=null,tint=Lime,modifier=Modifier.size(34.dp))
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(title,style=MaterialTheme.typography.titleLarge,color=Lime)
                Text(description,color=Muted)
            }
        }
    }
}

@Composable
private fun RowScope.ButtonIcon(icon: ImageVector) {
    Icon(icon,contentDescription=null,modifier=Modifier.size(20.dp))
    Spacer(Modifier.width(8.dp))
}
@Composable
internal fun Panel(title: String,content: @Composable ColumnScope.()->Unit) {
    Surface(color=CameraPanel,shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(title,style=MaterialTheme.typography.titleMedium);content()}
    }
}
@Composable
internal fun ResultPanel(result: CalculatedMeasurement,unit: VolumeUnit) {
    Panel(result.title) {
        val isFit="remainingWidth" in result.metrics
        result.metrics.entries.forEachIndexed {index,entry->
            if(index==0&&!isFit)Text(text(R.string.approximate_value, Quantities.metric(entry.key,entry.value,unit)),style=MaterialTheme.typography.headlineLarge,color=Lime)
            val label=Quantities.label(entry.key, shortfall=isFit&&entry.value<0)
            Text(text(R.string.label_value, label, Quantities.metric(entry.key,if(isFit)kotlin.math.abs(entry.value)else entry.value,unit)),color=if(entry.value<0)MaterialTheme.colorScheme.error else Muted)
        }
        result.notes.forEach {Text(it,style=MaterialTheme.typography.bodySmall,color=Muted)}
    }
}
@Composable
private fun DimensionField(key: String,input: DimensionInput,onChange: (String)->Unit,onCamera: (()->Unit)?,onLine: (()->Unit)?) {
    val keyboard=LocalSoftwareKeyboardController.current
    Column {
        OutlinedTextField(input.text,onChange,label={Text(inputLabel(key))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={keyboard?.hide()}),
            supportingText={Text(input.source.title)},modifier=Modifier.fillMaxWidth())
        if(onCamera!=null)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick=onCamera,modifier=Modifier.heightIn(min=48.dp).semantics {contentDescription=text(R.string.measure, inputLabel(key))}){Text(text(R.string.source_camera))}
            if(onLine!=null)TextButton(onClick=onLine,modifier=Modifier.heightIn(min=48.dp)){Text(text(R.string.from_line))}
        }
    }
}
@Composable
private fun ToggleRow(label: String,checked: Boolean,onChange: (Boolean)->Unit) {
    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()) {Checkbox(checked,onChange);Text(label,modifier=Modifier.weight(1f))}
}
@Composable
internal fun PlanPreview(points: List<Point2>) {
    if(points.size<2)return
    var expanded by remember(points){mutableStateOf(false)}
    val description=text(R.string.plan)+points.mapIndexed {i,p->text(R.string.corner_m_438, i+1, Quantities.number(p.x), Quantities.number(p.y))}.joinToString("; ")
    Canvas(Modifier.fillMaxWidth().height(190.dp).background(CameraPanel,RoundedCornerShape(18.dp)).semantics {contentDescription=description}) {
        val minX=points.minOf {it.x};val minY=points.minOf {it.y};val w=(points.maxOf {it.x}-minX).coerceAtLeast(.01);val h=(points.maxOf {it.y}-minY).coerceAtLeast(.01)
        val pad=24.dp.toPx();val scale=minOf((size.width-2*pad)/w,(size.height-2*pad)/h)
        fun map(p: Point2)=Offset(((p.x-minX-w/2)*scale+size.width/2).toFloat(),((p.y-minY-h/2)*scale+size.height/2).toFloat())
        val path=Path();points.forEachIndexed {i,p->val xy=map(p);if(i==0)path.moveTo(xy.x,xy.y)else path.lineTo(xy.x,xy.y)};path.close()
        drawPath(path,Lime.copy(alpha=.1f));drawPath(path,Lime,style=Stroke(2.dp.toPx()))
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {color=Muted.toArgb();textSize=12.sp.toPx()}
        points.forEachIndexed {index,p->val xy=map(p);drawCircle(Lime,4.dp.toPx(),xy)
            if(points.size<=12)drawContext.canvas.nativeCanvas.drawText(text(R.string.coordinate_label, index+1),xy.x+6.dp.toPx(),xy.y-6.dp.toPx(),paint)
        }
    }
    TextButton(onClick={expanded=!expanded}){Text(if(expanded)text(R.string.hide_sides) else text(R.string.sides_and_corners, points.size))}
    if(expanded)points.forEachIndexed {index,p->val next=(index+1)%points.size
        Text(text(R.string.edge_length_meters, index+1, next+1, Quantities.number(PolygonGeometry.distance(p,points[next]))),color=Muted)
    }
}
@Composable
private fun ContourEditor(initial: List<Point2>,onDismiss: ()->Unit,onSave: (List<Point2>)->Unit) {
    var entries by remember {mutableStateOf(initial.map {Quantities.editable(it.x) to Quantities.editable(it.y)}.ifEmpty {listOf("0" to "0","" to "","" to "")})}
    fun signed(value: String)=value.trim().replace(',','.').toDoubleOrNull()?.takeIf {it.isFinite()}
    val points=entries.mapNotNull { (x,y)->val px=signed(x);val py=signed(y);if(px!=null&&py!=null)Point2(px,py)else null }
    val error=if(points.size!=entries.size)text(R.string.enter_both_coordinates_for_each_corner) else PolygonGeometry.validate(points)
    AlertDialog(onDismissRequest=onDismiss,title={Text(text(R.string.corners_in_the_plane_m))},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(text(R.string.list_points_in_order_around_the_outline))
        entries.forEachIndexed {index,p->Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(p.first,{text->entries=entries.mapIndexed {i,v->if(i==index)text to v.second else v}},label={Text(text(R.string.coordinate_x, index+1))},modifier=Modifier.weight(1f),singleLine=true)
            OutlinedTextField(p.second,{text->entries=entries.mapIndexed {i,v->if(i==index)v.first to text else v}},label={Text(text(R.string.coordinate_y, index+1))},modifier=Modifier.weight(1f),singleLine=true)
        }}
        Row {TextButton(onClick={entries=entries+("" to "")},enabled=entries.size<32){Text(text(R.string.add_corner_445))};TextButton(onClick={entries=entries.dropLast(1)},enabled=entries.size>3){Text(text(R.string.remove))}}
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={onSave(points)},enabled=error==null){Text(text(R.string.apply))}},dismissButton={TextButton(onClick=onDismiss){Text(text(R.string.cancel))}})
}
