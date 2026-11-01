package com.ilya.rulivo.ar

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import android.os.SystemClock
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotTrackingException
import com.google.ar.core.exceptions.ResourceExhaustedException

/** Owns real surface anchors. Computed corners are geometry, never fabricated AR anchors. */
internal class ArCaptureController {
    @Volatile var request: CaptureRequest?=null
    private var draft: CaptureDraft<Anchor>?=null
    private val commands=CaptureCommands()
    private var confirmed: CaptureResult?=null
    private var last: CaptureFrame?=null
    private var message: String?=null
    private var messageUntil=0L
    private var worldEdges: List<FigureEdge> = emptyList()
    private var heightGuide: List<Pose> = emptyList()
    fun submit(c: CaptureCommand){commands.submit(c)}
    fun interrupt(){commands.clear()}
    fun destroy(){interrupt();draft?.clear();draft=null;last=null;worldEdges=emptyList()}
    fun releaseUnused(){if(request==null){draft?.clear();draft=null;last=null;confirmed=null;worldEdges=emptyList()}}
    fun update(hit: HitResult?,fresh: Boolean,ray: CameraRay?=null): CaptureFrame? {
        val req=request
        if(req?.id!=draft?.request?.id){draft?.clear();draft=req?.let { CaptureDraft(it,Anchor::detach) };confirmed=null;last=null;message=null;worldEdges=emptyList()}
        val d=draft ?: run {commands.clear();return null}
        if(SystemClock.uptimeMillis()>=messageUntil)message=null
        val anchors=d.anchors
        val stopped=anchors.indexOfFirst { it.trackingState==TrackingState.STOPPED }
        val tracking=fresh&&anchors.all { it.trackingState==TrackingState.TRACKING }
        val poses=anchors.map {it.pose}
        val basis=poses.firstOrNull()?.let(::captureBasis)
        fun samples(values: List<Pose>): List<Point3D> {
            val inverse=values.firstOrNull()?.let(::captureBasis)?.inverse() ?: return emptyList()
            return values.map {inverse.compose(it).point()}
        }
        val local=samples(poses)
        val heightAvailable=d.heightAvailable&&FigureGeometry.evaluate(d.request.type,local.take(d.heightBaseCount ?: 0),d.baseCount).baseValid
        val baseSamples=local.take(d.heightBaseCount ?: 0)
        val reference=if(d.request.type==CaptureType.CYLINDER_VOLUME||baseSamples.isEmpty())Point3D(0.0,0.0,0.0)
            else Point3D(baseSamples.map {it.x}.average(),0.0,baseSamples.map {it.z}.average())
        val localRay=if(tracking&&heightAvailable&&basis!=null&&ray!=null) {
            val inverse=basis.inverse()
            val origin=inverse.transformPoint(floatArrayOf(ray.origin.x.toFloat(),ray.origin.y.toFloat(),ray.origin.z.toFloat()))
            val direction=inverse.rotateVector(floatArrayOf(ray.direction.x.toFloat(),ray.direction.y.toFloat(),ray.direction.z.toFloat()))
            CameraRay(Point3D(origin[0].toDouble(),origin[1].toDouble(),origin[2].toDouble()),Point3D(direction[0].toDouble(),direction[1].toDouble(),direction[2].toDouble()))
        }else null
        d.updateCameraHeight(localRay,reference)
        val accepted=if(tracking&&d.request.isFigure)d.evaluate(local)else null
        val index=d.replacement ?: anchors.size
        val candidatePoses=poses.toMutableList().apply {
            if(hit!=null){if(d.replacement!=null)this[index]=hit.hitPose else add(hit.hitPose)}
        }
        val candidate=if(hit!=null&&d.request.isFigure)FigureGeometry.evaluate(d.request.type,samples(candidatePoses),d.baseCount)else null
        val isFloor=d.request.type in listOf(CaptureType.ROOM_FLOOR,CaptureType.ROOM_VOLUME)
        val horizontal=isFloor||d.request.target in listOf("fitSpace","fitItem")
        val firstPlaneAllowed=!d.request.isFigure||index!=0||hit?.trackable.let {it is Plane&&(!horizontal||it.type==Plane.Type.HORIZONTAL_UPWARD_FACING)}
        val openContour=d.request.isContour&&!d.closed
        // An open chain may have an invalid closing edge; closure is checked separately.
        val candidateValid=if(openContour&&candidate!=null) {
            val local=samples(candidatePoses)
            local.all {kotlin.math.abs(it.y)<=FigureGeometry.PLANE_RESIDUAL_METERS} &&
                local.indices.all {a->local.indices.all {b->a==b||DistanceCalculator.distanceMeters(local[a],local[b])>1e-6}}
        } else candidate?.error==null
        val replaceStopped=fresh&&d.replacement==stopped&&stopped>=0&&anchors.indices.all {it==stopped||anchors[it].trackingState==TrackingState.TRACKING}
        val canPlace=hit!=null&&fresh&&firstPlaneAllowed&&(tracking||replaceStopped)&&d.acceptsPoint&&candidateValid&&confirmed==null
        val scalarEnd=poses.getOrNull(1) ?: hit?.hitPose
        val meters=if(tracking&&!d.request.isFigure&&basis!=null&&scalarEnd!=null)
            captureDistance(d.request.type,basis.point(),scalarEnd.point())
            else if(!tracking)last?.meters else null
        val scalarValid=anchors.size==2&&meters!=null&&meters.isFinite()&&meters>0.0
        val canClose=tracking&&d.request.isContour&&!d.closed&&accepted?.baseValid==true&&d.replacement==null
        val canConfirm=tracking&&!d.cameraHeight&&d.replacement==null&&confirmed==null&&
            (if(d.request.isFigure)accepted?.complete==true&&(!d.request.isContour||d.closed)else scalarValid)
        val c=commands.poll()
        if(c!=null&&d.matches(c)&&confirmed==null) {
            message=null
            when(c.action) {
                CaptureAction.PLACE -> try {
                    if(!d.place(c,canPlace){hit!!.createAnchor()})message=candidate?.error ?: text(R.string.point_at_a_suitable_surface)
                } catch(e: NotTrackingException){message=text(R.string.tracking_lost_aim_again)}
                  catch(e: ResourceExhaustedException){message=text(R.string.not_enough_ar_resources_remove_points_or)}
                CaptureAction.UNDO -> d.undo(c)
                CaptureAction.REPLACE -> d.select(c)
                CaptureAction.CLEAR -> d.clear()
                CaptureAction.CLOSE -> if(!d.close(c,canClose))message=accepted?.error ?: text(R.string.add_corners_and_wait_for_tracking)
                CaptureAction.SET_HEIGHT -> if(heightAvailable)d.setHeight(c)
                CaptureAction.CAMERA_HEIGHT -> if(heightAvailable)d.measureHeight(c)
                CaptureAction.AIM_HEIGHT -> if(heightAvailable)d.aimHeight(c)
                CaptureAction.FREEZE_HEIGHT -> if(tracking)d.freezeHeight(c)
                CaptureAction.CONFIRM -> if(canConfirm)confirmed=d.result(accepted,meters)
            }
            if(message!=null)messageUntil=SystemClock.uptimeMillis()+4_000L
            if(c.action!=CaptureAction.CONFIRM)return update(hit,fresh,ray)
        }
        val preview=canPlace&&candidate!=null&&d.replacement==null
        val displayed=if(preview)candidate else accepted
        val displayBasis=if(preview)candidatePoses.firstOrNull()?.let(::captureBasis)else basis
        worldEdges=if(tracking&&displayBasis!=null)displayed?.edges.orEmpty().map {edge->
            fun world(p: Point3D)=displayBasis.compose(Pose.makeTranslation(p.x.toFloat(),p.y.toFloat(),p.z.toFloat())).point()
            FigureEdge(world(edge.start),world(edge.end))
        }else emptyList()
        heightGuide=if(tracking&&heightAvailable&&d.manualHeight&&basis!=null&&d.signedHeight!=null)listOf(
            basis.compose(Pose.makeTranslation(reference.x.toFloat(),0f,reference.z.toFloat())),
            basis.compose(Pose.makeTranslation(reference.x.toFloat(),d.signedHeight!!.toFloat(),reference.z.toFloat())))else emptyList()
        val hint=when {
            stopped>=0 -> text(R.string.point_lost_select_it_for_re_measurement, stopped+1)
            !tracking -> text(R.string.restoring_tracking_return_to_the_marked_points)
            message!=null -> message!!
            d.replacement!=null -> text(R.string.mark_point_again, d.replacement!!+1)
            heightAvailable&&d.cameraHeight -> if(d.cameraHeightReady)text(R.string.raise_or_lower_the_crosshair_to_move)
                else text(R.string.step_slightly_to_the_side_of_the)
            heightAvailable&&d.manualHeight -> if(d.signedHeight==null)text(R.string.base_fixed_enter_the_height_below)else text(R.string.height_entered_manually_check_the_top_of)
            canConfirm -> if(d.request.isFigure)text(R.string.walk_around_the_shape_and_check_its)else text(R.string.check_the_size_and_use_the_result)
            !d.request.isFigure&&anchors.size==2&&!scalarValid -> if(d.request.type==CaptureType.HEIGHT)text(R.string.the_second_point_must_be_higher_than)else text(R.string.the_points_overlap_adjust_one_endpoint)
            !firstPlaneAllowed -> if(isFloor)text(R.string.aim_at_the_detected_floor) else if(horizontal)text(R.string.aim_at_a_horizontal_face)else text(R.string.find_a_flat_face_by_moving_the)
            d.acceptsPoint&&candidate?.error!=null&&!openContour -> candidate.error
            accepted?.error!=null -> accepted.error
            else -> captureHint(d.request.type,anchors.size,d.closed)
        }
        return CaptureFrame(d.request.id,d.revision,anchors.size,d.closed,canPlace,canConfirm,tracking,
            accepted?.outline ?: last?.points.orEmpty(),meters,
            if(tracking)displayed?.area else last?.area,hint,d.replacement,confirmed,
            volume=if(tracking)displayed?.volume else last?.volume,
            dimensions=if(tracking)displayed?.dimensions.orEmpty()else last?.dimensions.orEmpty(),
            stage=captureStage(d.request.type,anchors.size,d.closed),placeLabel=capturePlaceLabel(d.request.type,index,d.closed),
            canClose=canClose,showPlace=d.acceptsPoint,baseCount=d.baseCount,preview=preview,
            heightAvailable=heightAvailable,manualHeight=d.manualHeight,
            cameraHeight=d.cameraHeight&&heightAvailable,cameraHeightReady=d.cameraHeightReady,heightSource=d.heightSource,
            signedHeight=if(d.manualHeight)d.signedHeight else if(displayed?.complete==true) {
                val base=d.heightBaseCount ?: 0
                val displayedSamples=if(preview)samples(candidatePoses)else local
                displayedSamples.getOrNull(base)?.y?.minus(displayedSamples.first().y)
            }else null).also {last=it}
    }
    fun draw(renderer: MeasurementRenderer,width: Int,height: Int,hit: HitResult?) {
        val d=draft ?: return
        val poses=d.anchors.map {if(it.trackingState==TrackingState.TRACKING)it.pose else null}
        if(d.request.isFigure) {
            renderer.drawFigure(worldEdges,width,height,last?.preview==true)
            if(heightGuide.isNotEmpty())renderer.drawMeasurement(heightGuide,width,height,
                text(R.string.m, com.ilya.rulivo.measure.Quantities.number(kotlin.math.abs(d.signedHeight ?: 0.0))),labelId=-3)
            poses.filterNotNull().forEach {renderer.drawMeasurement(listOf(it),width,height,null,labelId=-1)}
        } else {
            renderer.drawMeasurement(poses.filterNotNull(),width,height,null,
                previewPose=if(last?.canPlace==true&&d.replacement==null)hit?.hitPose else null,labelId=-1)
        }
    }
    private fun Pose.point()=Point3D(tx().toDouble(),ty().toDouble(),tz().toDouble())
}

internal fun captureStage(type: CaptureType,count: Int,closed: Boolean)=when(type) {
    CaptureType.BOX_VOLUME -> text(R.string.object_step_of_4, minOf(count+1,4))
    CaptureType.CYLINDER_VOLUME -> text(R.string.cylinder_step_of_3, minOf(count+1,3))
    CaptureType.ROOM_VOLUME -> if(closed)text(R.string.message_2_2_room_height) else text(R.string.message_1_2_floor_outline)
    CaptureType.AREA,CaptureType.ROOM_FLOOR -> text(R.string.outline_corners, count)
    else -> text(R.string.point_of_2, minOf(count+1,2))
}
internal fun capturePlaceLabel(type: CaptureType,index: Int,closed: Boolean)=when(type) {
    CaptureType.BOX_VOLUME -> listOf(text(R.string.first_corner_of_the_face),text(R.string.end_of_the_first_edge),text(R.string.opposite_edge),text(R.string.opposite_face))[index.coerceIn(0,3)]
    CaptureType.CYLINDER_VOLUME -> listOf(text(R.string.center_of_the_circle),text(R.string.circle_edge),text(R.string.opposite_base))[index.coerceIn(0,2)]
    CaptureType.ROOM_VOLUME -> if(closed)text(R.string.mark_ceiling)else text(R.string.add_corner, index+1)
    CaptureType.AREA,CaptureType.ROOM_FLOOR -> text(R.string.add_corner, index+1)
    else -> text(R.string.place_point, index+1)
}
internal fun captureHint(type: CaptureType,count: Int,closed: Boolean)=when(type) {
    CaptureType.BOX_VOLUME -> when(count){0->text(R.string.select_the_flat_face_of_the_object);1->text(R.string.mark_the_second_end_of_the_edge);2->text(R.string.mark_the_opposite_edge_of_this_face);else->text(R.string.aim_at_the_opposite_face_or_its)}
    CaptureType.CYLINDER_VOLUME -> when(count){0->text(R.string.mark_the_center_of_the_flat_circular);1->text(R.string.mark_the_edge_of_the_same_circle);else->text(R.string.mark_the_second_base_or_its_edge)}
    CaptureType.ROOM_VOLUME -> if(closed)text(R.string.mark_the_ceiling_or_top_of_the)else text(R.string.mark_the_floor_corners_in_order_then)
    CaptureType.AREA,CaptureType.ROOM_FLOOR -> text(R.string.mark_corners_on_one_plane_in_order)
    CaptureType.HEIGHT -> if(count==0)text(R.string.mark_the_bottom_point)else text(R.string.mark_the_top_point_vertical_height_is)
    CaptureType.DISTANCE -> if(count==0)text(R.string.mark_the_start_of_the_measurement)else text(R.string.mark_the_end_of_the_measurement)
}
