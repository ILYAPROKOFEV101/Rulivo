package com.ilya.rulivo.ui.measurement

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ilya.rulivo.ar.CaptureRequest
import com.ilya.rulivo.ar.CaptureResult
import com.ilya.rulivo.ar.CaptureType
import com.ilya.rulivo.measure.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class ToolsPage { MENU, EDIT, HISTORY, DETAIL }
data class ToolkitState(
    val draft: MeasureDraft = MeasureDraft(), val page: ToolsPage = ToolsPage.MENU,
    val history: List<SavedMeasurement> = emptyList(),val selected: SavedMeasurement? = null,
    val loading: Boolean = true,val saving: Boolean = false,val messageProvider: (() -> String?)? = null,
    val capture: CaptureRequest? = null,val savedDraft: MeasureDraft? = null,
) {
    val message: String? get() = messageProvider?.invoke()
}

class ToolkitViewModel @JvmOverloads constructor(application: Application,private val savedState: SavedStateHandle,
    private val repository: MeasurementRepository = MeasurementRepository(File(application.filesDir,"measurements.json"))): AndroidViewModel(application) {
    private val mutex=Mutex()
    private var readable=true
    private val mutable=MutableStateFlow(ToolkitState(draft=savedState.get<MeasureDraft>("measure_draft") ?: MeasureDraft(),
        page=if(savedState.get<Boolean>("editing")==true)ToolsPage.EDIT else ToolsPage.MENU))
    val state=mutable.asStateFlow()
    init { viewModelScope.launch {
        mutex.withLock {
            try { val history=withContext(Dispatchers.IO){repository.load()};mutable.update { it.copy(history=history,loading=false) } }
            catch(e: Exception) { readable=false;mutable.update { it.copy(loading=false,messageProvider={ text(R.string.failed_to_read_history_the_existing_file) }) } }
        }
    } }
    fun change(transform: (MeasureDraft)->MeasureDraft) {
        val draft=transform(mutable.value.draft);savedState["measure_draft"]=draft
        mutable.update { it.copy(draft=draft,messageProvider=null) }
    }
    fun start(kind: MeasureKind) {
        savedState.remove<MeasureDraft>("material_parent")
        val base=MeasureDraft(kind=kind).let { d->when(kind) {
            MeasureKind.FIT -> d.withInput("gap","0")
            MeasureKind.MATERIALS -> d.withInput("reserve","0").withInput("coats","1").withInput("excluded","0")
            else -> d
        } }
        change { base };page(ToolsPage.EDIT);mutable.update { it.copy(savedDraft=null,capture=null) }
    }
    fun resumeDraft() = page(ToolsPage.EDIT)
    fun back() {
        val parent=savedState.get<MeasureDraft>("material_parent")
        if(mutable.value.page==ToolsPage.EDIT && mutable.value.draft.kind==MeasureKind.MATERIALS && parent!=null) {
            savedState.remove<MeasureDraft>("material_parent");change {parent}
        } else page(if(mutable.value.page==ToolsPage.DETAIL)ToolsPage.HISTORY else ToolsPage.MENU)
    }
    fun page(page: ToolsPage) {savedState["editing"]=page==ToolsPage.EDIT;mutable.update { it.copy(page=page) } }
    private fun message(resolve: () -> String?) {mutable.update { it.copy(messageProvider=resolve) } }
    fun open(record: SavedMeasurement) {mutable.update { it.copy(selected=record,page=ToolsPage.DETAIL) } }
    fun editCopy(record: SavedMeasurement) { change { record.draft };page(ToolsPage.EDIT);mutable.update { it.copy(savedDraft=null,capture=null) } }
    fun requestCapture(key: String,type: CaptureType) {
        mutable.update { it.copy(capture=CaptureRequest(UUID.randomUUID().toString(),type,key),messageProvider=null) }
    }
    fun cancelCapture() {mutable.update { it.copy(capture=null) } }
    fun acceptCapture(result: CaptureResult) {
        val request=mutable.value.capture ?: return
        if(request.id!=result.requestId)return
        change { original ->
            var d=if(request.target=="fitSpace")original.copy(inputs=original.inputs.filterKeys {it !in listOf("itemWidth","itemDepth","itemHeight")})else original
            if(result.points.isNotEmpty()&&request.target!="materialsSurface")
                d=d.copy(shape=FloorShape.CONTOUR,contour=result.points,contourSource=ValueSource.AR)
            val mapping=when(request.target) {
                "fitSpace" -> mapOf("length" to "width","width" to "depth","height" to "height")
                "fitItem" -> mapOf("length" to "itemWidth","width" to "itemDepth","height" to "itemHeight")
                "materialsSurface" -> mapOf("area" to "area","perimeter" to "perimeter")
                else -> result.dimensions.keys.associateWith {it}
            }
            mapping.forEach { (source,target)->result.dimensions[source]?.let {d=d.withInput(target,Quantities.editable(it),result.dimensionSources[source] ?: ValueSource.AR)} }
            result.meters?.let {d=d.withInput(request.target,Quantities.editable(it),ValueSource.AR)}
            d
        }
        if(request.target=="fitSpace")requestCapture("fitItem",CaptureType.BOX_VOLUME)else cancelCapture()
    }

    fun materials(result: CalculatedMeasurement) {
        val parent=mutable.value.draft
        start(MeasureKind.MATERIALS)
        savedState["material_parent"]=parent
        change { draft -> var d=draft
            result.metrics["area"]?.let { d=d.withInput("area",Quantities.editable(it),ValueSource.DERIVED) }
            result.metrics["perimeter"]?.let { d=d.withInput("perimeter",Quantities.editable(it),ValueSource.DERIVED) }
            d
        }
    }
    fun useBox(record: SavedMeasurement,asItem: Boolean) {
        if(record.draft.kind!=MeasureKind.BOX || record.draft.internal==asItem)return
        val m=record.result.metrics
        val keys=if(asItem)listOf("itemWidth","itemDepth","itemHeight")else listOf("width","depth","height")
        val values=listOf(m["length"],m["width"],m["height"])
        if(values.any { it==null })return
        change { d -> keys.zip(values).fold(d) { draft,(k,v)->draft.withInput(k,Quantities.editable(v!!),ValueSource.DERIVED) } }
    }
    fun save() {
        val s=mutable.value
        if(s.loading||s.saving||s.savedDraft==s.draft)return
        if(!readable){message { text(R.string.history_not_available_existing_data_is_not) };return}
        val calculated=MeasurementCalculator.calculate(s.draft)
        val result=calculated.result ?: run {message { MeasurementCalculator.calculate(s.draft).error };return}
        val draft=s.draft.copy(name=s.draft.name.trim())
        val record=SavedMeasurement(UUID.randomUUID().toString(),System.currentTimeMillis(),draft,result)
        mutable.update { it.copy(saving=true,messageProvider=null) }
        viewModelScope.launch {
            mutex.withLock {
                try {
                    val updated=listOf(record)+mutable.value.history
                    withContext(Dispatchers.IO){repository.save(updated)}
                    mutable.update { it.copy(history=updated,saving=false,savedDraft=s.draft,messageProvider={ text(R.string.saved_451, record.displayName) }) }
                } catch(e: Exception) {mutable.update { it.copy(saving=false,messageProvider={ text(R.string.failed_to_save_check_your_free_space) }) } }
            }
        }
    }
}

/** One camera session produces the complete dimensions needed by each mode. */
internal fun figureCapture(kind: MeasureKind): Pair<String,CaptureType> = when(kind) {
    MeasureKind.BOX -> "figure" to CaptureType.BOX_VOLUME
    MeasureKind.CYLINDER -> "figure" to CaptureType.CYLINDER_VOLUME
    MeasureKind.AREA -> "contour" to CaptureType.AREA
    MeasureKind.ROOM -> "room" to CaptureType.ROOM_VOLUME
    MeasureKind.FIT -> "fitSpace" to CaptureType.BOX_VOLUME
    MeasureKind.MATERIALS -> "materialsSurface" to CaptureType.AREA
}
