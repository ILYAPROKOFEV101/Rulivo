package com.ilya.rulivo.ar

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import com.ilya.rulivo.measure.Point2
import com.ilya.rulivo.measure.ValueSource

enum class CaptureType { DISTANCE, HEIGHT, AREA, ROOM_FLOOR, BOX_VOLUME, CYLINDER_VOLUME, ROOM_VOLUME }
internal fun captureDistance(type: CaptureType,a: Point3D,b: Point3D): Double =
    if(type==CaptureType.HEIGHT)b.y-a.y else DistanceCalculator.distanceMeters(a,b)
data class CaptureRequest(val id: String,val type: CaptureType,val target: String) {
    val isContour get()=type in listOf(CaptureType.AREA,CaptureType.ROOM_FLOOR,CaptureType.ROOM_VOLUME)
    val isFigure get()=isContour||type==CaptureType.BOX_VOLUME||type==CaptureType.CYLINDER_VOLUME
    val isVolume get()=type in listOf(CaptureType.BOX_VOLUME,CaptureType.CYLINDER_VOLUME,CaptureType.ROOM_VOLUME)
    val pointLimit get()=when(type){CaptureType.BOX_VOLUME->4;CaptureType.CYLINDER_VOLUME->3;CaptureType.ROOM_VOLUME->33;CaptureType.AREA,CaptureType.ROOM_FLOOR->32;else->2}
}
data class CaptureResult(val requestId: String,val meters: Double?=null,val points: List<Point2> = emptyList(),val dimensions: Map<String,Double> = emptyMap(),
    val dimensionSources: Map<String,ValueSource> = emptyMap())
data class CaptureFrame(
    val requestId: String,val revision: Long=0,val count: Int=0,val closed: Boolean=false,
    val canPlace: Boolean=false,val canConfirm: Boolean=false,val tracking: Boolean=false,
    val points: List<Point2> = emptyList(),val meters: Double?=null,val area: Double?=null,
    val hint: String=text(R.string.aim_at_the_surface),val replacement: Int?=null,
    val confirmed: CaptureResult?=null,
    val volume: Double?=null,val dimensions: Map<String,Double> = emptyMap(),
    val stage: String="",val placeLabel: String="",val canClose: Boolean=false,
    val showPlace: Boolean=true,val baseCount: Int?=null,val preview: Boolean=false,
    val heightAvailable: Boolean=false,val manualHeight: Boolean=true,val signedHeight: Double?=null,
    val cameraHeight: Boolean=false,val cameraHeightReady: Boolean=false,val heightSource: ValueSource=ValueSource.MANUAL,
)
enum class CaptureAction { PLACE, UNDO, CLOSE, CONFIRM, REPLACE, CLEAR, SET_HEIGHT, CAMERA_HEIGHT, AIM_HEIGHT, FREEZE_HEIGHT }
data class CaptureCommand(val requestId: String,val revision: Long,val action: CaptureAction,val index: Int?=null,
    val height: Double?=null)

/** Preserve command order, coalescing typing only within the same structural revision. */
internal class CaptureCommands {
    private val pending=ArrayDeque<CaptureCommand>()
    @Synchronized fun submit(command: CaptureCommand) {
        val last=pending.lastOrNull()
        if(command.action==CaptureAction.SET_HEIGHT&&last?.action==command.action&&
            last.requestId==command.requestId&&last.revision==command.revision)pending.removeLast()
        pending.addLast(command)
    }
    @Synchronized fun poll(): CaptureCommand?=pending.removeFirstOrNull()
    @Synchronized fun clear()=pending.clear()
}

/** Single GL-thread owner; factories never run for a stale command or over the anchor budget. */
internal class CaptureDraft<A>(val request: CaptureRequest,private val release: (A)->Unit) {
    private val owned=mutableListOf<A>()
    val anchors: List<A> get()=owned
    var revision=0L;private set
    var closed=false;private set
    var replacement: Int?=null;private set
    var baseCount: Int?=null;private set
    var manualHeight=true;private set
    var signedHeight: Double?=null;private set
    var cameraHeight=request.isVolume;private set
    var cameraHeightReady=false;private set
    var heightSource=ValueSource.MANUAL;private set
    private val heightMotion=CameraHeightMotion()
    val heightBaseCount: Int? get()=when(request.type) {
        CaptureType.BOX_VOLUME -> 3.takeIf {owned.size>=it}
        CaptureType.CYLINDER_VOLUME -> 2.takeIf {owned.size>=it}
        CaptureType.ROOM_VOLUME -> baseCount?.takeIf {closed}
        else -> null
    }
    val heightAvailable get()=heightBaseCount!=null&&(replacement==null||replacement==heightBaseCount)
    val acceptsPoint get()=replacement!=null || if(manualHeight&&heightAvailable)false else if(request.type==CaptureType.ROOM_VOLUME) {
        if(closed)owned.size==baseCount else owned.size<32
    } else !closed&&owned.size<request.pointLimit
    fun matches(c: CaptureCommand)=c.requestId==request.id&&c.revision==revision
    fun setHeight(c: CaptureCommand): Boolean {
        if(!matches(c)||!heightAvailable||c.height?.let { !FigureGeometry.validHeight(request.type,it) }==true)return false
        val base=heightBaseCount!!
        while(owned.size>base)release(owned.removeAt(owned.lastIndex))
        manualHeight=true;signedHeight=c.height;replacement=null
        cameraHeight=false;cameraHeightReady=false;heightMotion.reset();heightSource=ValueSource.MANUAL
        // Height edits retain the structural revision so rapid typing and Confirm remain ordered.
        return true
    }
    fun measureHeight(c: CaptureCommand): Boolean {
        if(!matches(c)||!heightAvailable)return false
        val base=heightBaseCount!!
        manualHeight=false
        cameraHeight=false;cameraHeightReady=false;heightMotion.reset()
        if(owned.size>base)replacement=base
        revision++;return true
    }
    fun aimHeight(c: CaptureCommand): Boolean {
        if(!matches(c)||!heightAvailable)return false
        val base=heightBaseCount!!
        while(owned.size>base)release(owned.removeAt(owned.lastIndex))
        manualHeight=true;replacement=null;cameraHeight=true;cameraHeightReady=false;heightMotion.reset()
        revision++;return true
    }
    fun freezeHeight(c: CaptureCommand): Boolean {
        if(!matches(c)||!cameraHeight||!cameraHeightReady||signedHeight==null)return false
        cameraHeight=false;cameraHeightReady=false;heightMotion.reset();revision++;return true
    }
    fun updateCameraHeight(ray: CameraRay?,reference: Point3D) {
        if(!cameraHeight||!heightAvailable){cameraHeightReady=false;heightMotion.reset();return}
        val height=heightMotion.height(ray,reference,signedHeight ?: 0.0)
        cameraHeightReady=height!=null
        if(height!=null) {
            val limited=if(request.type==CaptureType.ROOM_VOLUME)height.coerceAtLeast(0.0)else height
            if(limited!=height)heightMotion.reset()
            signedHeight=limited.takeIf {FigureGeometry.validHeight(request.type,it)}
            heightSource=ValueSource.CAMERA_AIM
        }
    }
    fun evaluate(samples: List<Point3D>): FigureEvaluation =
        if(manualHeight&&heightBaseCount!=null)FigureGeometry.withHeight(request.type,samples.take(heightBaseCount!!),baseCount,signedHeight)
        else FigureGeometry.evaluate(request.type,samples,baseCount)
    fun result(figure: FigureEvaluation?,meters: Double?=null)=CaptureResult(request.id,
        meters=if(request.isFigure)null else meters,
        points=if(request.isContour)figure?.outline.orEmpty().toList()else emptyList(),
        dimensions=figure?.dimensions.orEmpty().toMap(),
        dimensionSources=if(manualHeight&&signedHeight!=null)mapOf("height" to heightSource)else emptyMap())
    fun place(c: CaptureCommand,allowed: Boolean,create: ()->A): Boolean {
        if(!matches(c)||!allowed)return false
        val replacing=replacement
        if(!acceptsPoint)return false
        val anchor=create()
        if(replacing!=null){val previous=owned[replacing];owned[replacing]=anchor;release(previous)}else owned.add(anchor)
        replacement=null;revision++;return true
    }
    fun select(c: CaptureCommand): Boolean {
        if(!matches(c)||c.index !in owned.indices)return false
        if(c.index==heightBaseCount)manualHeight=false
        cameraHeight=false;cameraHeightReady=false;heightMotion.reset()
        replacement=c.index;revision++;return true
    }
    fun undo(c: CaptureCommand): Boolean {
        if(!matches(c))return false
        when {
            replacement!=null->replacement=null
            manualHeight&&signedHeight!=null->{signedHeight=null;cameraHeight=false}
            closed&&request.type==CaptureType.ROOM_VOLUME&&owned.size>(baseCount ?:owned.size)->release(owned.removeAt(owned.lastIndex))
            closed->{closed=false;baseCount=null}
            owned.isNotEmpty()->release(owned.removeAt(owned.lastIndex))
            else->return false
        }
        cameraHeightReady=false;heightMotion.reset()
        if(heightBaseCount==null){signedHeight=null;manualHeight=true;cameraHeight=request.isVolume}
        revision++;return true
    }
    fun close(c: CaptureCommand,valid: Boolean): Boolean {
        if(!matches(c)||!valid||closed||!request.isContour||owned.size<3||replacement!=null)return false
        closed=true;baseCount=owned.size;revision++;return true
    }
    fun clear() { owned.forEach(release);owned.clear();closed=false;baseCount=null;replacement=null;manualHeight=true;signedHeight=null;cameraHeight=request.isVolume;cameraHeightReady=false;heightMotion.reset();heightSource=ValueSource.MANUAL;revision++ }
}
