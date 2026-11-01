package com.ilya.rulivo.measure

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import java.io.Serializable
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import com.ilya.rulivo.i18n.AppStrings
import com.ilya.rulivo.i18n.LocalizedNumbers
import kotlin.math.ceil

enum class MeasureKind(private val textId: Int) { BOX(R.string.mode_object), CYLINDER(R.string.mode_cylinder), AREA(R.string.quantity_area), ROOM(R.string.mode_room), FIT(R.string.mode_fit), MATERIALS(R.string.mode_materials);
    val title: String get() = text(textId)
}
enum class ValueSource(private val textId: Int) { MANUAL(R.string.source_manual), AR(R.string.source_camera), DERIVED(R.string.source_result), CAMERA_AIM(R.string.source_camera_aim);
    val title: String get() = text(textId)
}
enum class FloorShape { RECTANGLE, L_SHAPE, CONTOUR }
enum class MaterialKind(private val textId: Int) { FLOOR(R.string.material_flooring), PAINT(R.string.material_paint), BASEBOARD(R.string.material_baseboard);
    val title: String get() = text(textId)
}
enum class VolumeUnit(private val textId: Int, val scale: Double) { CUBIC_METERS(R.string.unit_cubic_meter,1.0), LITERS(R.string.unit_liter,1000.0), CUBIC_CENTIMETERS(R.string.unit_cubic_centimeter,1_000_000.0);
    val title: String get() = text(textId)
}
data class DimensionInput(val text: String = "", val source: ValueSource = ValueSource.MANUAL) : Serializable
data class MeasureDraft(
    val kind: MeasureKind = MeasureKind.BOX,
    val inputs: Map<String,DimensionInput> = emptyMap(),
    val shape: FloorShape = FloorShape.RECTANGLE,
    val contour: List<Point2> = emptyList(),
    val contourSource: ValueSource = ValueSource.MANUAL,
    val internal: Boolean = false,
    val orientation: Int = 0,
    val allowTipping: Boolean = false,
    val material: MaterialKind = MaterialKind.FLOOR,
    val name: String = "",
    val volumeUnit: VolumeUnit = VolumeUnit.CUBIC_METERS,
) : Serializable {
    fun input(key: String) = inputs[key] ?: DimensionInput()
    fun value(key: String) = Quantities.parse(input(key).text)
    fun withInput(key: String, text: String, source: ValueSource = ValueSource.MANUAL) = copy(inputs=inputs+(key to DimensionInput(text,source)))
    val hasData get() = inputs.values.any { it.text.isNotBlank() } || contour.isNotEmpty()
}
data class CalculatedMeasurement(
    val title: String,
    val metrics: Map<String,Double>,
    val outline: List<Point2> = emptyList(),
    val notes: List<String> = emptyList(),
)
data class Calculation(val result: CalculatedMeasurement? = null, val error: String? = null)
data class SavedMeasurement(val id: String, val createdAt: Long, val draft: MeasureDraft, val result: CalculatedMeasurement) {
    /** Rebuild presentation text from stable inputs; preserve the saved numeric result. */
    val localizedResult: CalculatedMeasurement get() {
        val presentation = MeasurementCalculator.calculate(draft).result
        return result.copy(title = presentation?.title ?: draft.kind.title, notes = presentation?.notes.orEmpty())
    }
    val displayName: String get() = draft.name.ifBlank { localizedResult.title }
}

object Quantities {
    fun parse(text: String): Double? {
        val normalized=LocalizedNumbers.normalize(text)
        if (!Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)").matches(normalized)) return null
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }
    fun number(value: Double, digits: Int = 3): String {
        val magnitude=kotlin.math.abs(value)
        val precision=if(digits>0&&magnitude>0&&magnitude<Math.pow(10.0,-digits.toDouble()))
            maxOf(digits,(-kotlin.math.floor(kotlin.math.log10(magnitude))).toInt()+2) else digits
        val pattern=when {precision==0->"0";precision>12->"0.###E0";else->"0."+"#".repeat(precision)}
        return DecimalFormat(pattern,DecimalFormatSymbols(AppStrings.locale)).format(value)
    }
    fun editable(value: Double) = LocalizedNumbers.editable(value)
    fun volume(value: Double, unit: VolumeUnit) = number(value*unit.scale)+" "+unit.title
    fun metric(key: String, value: Double, unit: VolumeUnit): String = when(key) {
        "volume" -> volume(value,unit)
        "area", "requiredArea", "purchasedArea" -> number(value)+text(R.string.square_meters_suffix)
        "perimeter", "height", "length", "width", "depth", "diameter", "remainingWidth", "remainingDepth", "remainingHeight", "requiredLength", "purchasedLength" -> number(value)+text(R.string.meters_suffix)
        "paintLiters" -> number(value)+text(R.string.liters_suffix)
        else -> number(value,0)
    }
    fun label(key: String, shortfall: Boolean = false): String {
        if (shortfall) when (key) {
            "remainingWidth" -> return text(R.string.shortfall_width)
            "remainingDepth" -> return text(R.string.shortfall_depth)
            "remainingHeight" -> return text(R.string.shortfall_height)
        }
        return mapOf("volume" to R.string.volume, "area" to R.string.quantity_area, "perimeter" to R.string.perimeter, "height" to R.string.height, "length" to R.string.length, "width" to R.string.width, "depth" to R.string.depth, "diameter" to R.string.diameter, "remainingWidth" to R.string.width_clearance, "remainingDepth" to R.string.depth_clearance, "remainingHeight" to R.string.height_clearance, "packages" to R.string.packs, "requiredArea" to R.string.area_including_allowance, "purchasedArea" to R.string.area_in_purchased_packs, "paintLiters" to R.string.material_paint, "pieces" to R.string.planks, "requiredLength" to R.string.length_including_allowance, "purchasedLength" to R.string.length_in_purchased_boards)[key]?.let { text(it) } ?: key
    }
}

object MeasurementCalculator {
    val rotations = listOf(listOf(0,1,2),listOf(1,0,2),listOf(0,2,1),listOf(2,0,1),listOf(1,2,0),listOf(2,1,0))
    fun calculate(d: MeasureDraft): Calculation = try {
        fun pos(k: String,label: String): Double = d.value(k)?.takeIf { it>0.0 } ?: error(text(R.string.enter_greater_than_zero, label))
        fun zero(k: String,label: String): Double = d.value(k)?.takeIf { it>=0.0 } ?: error(text(R.string.enter_of_zero_or_more, label))
        val metrics=linkedMapOf<String,Double>(); val notes=mutableListOf<String>();var outline=emptyList<Point2>()
        val title=when(d.kind) {
            MeasureKind.BOX -> {
                val l=pos("length",text(R.string.length_136));val w=pos("width",text(R.string.width_137));val h=pos("height",text(R.string.height_138))
                metrics.putAll(linkedMapOf("volume" to l*w*h,"length" to l,"width" to w,"height" to h))
                notes += if(d.internal) text(R.string.calculated_from_the_inside_dimensions_of_a) else text(R.string.rectangular_outside_dimensions_including_empty_spaces_the)
                if(d.internal) text(R.string.capacity) else text(R.string.volume_from_dimensions)
            }
            MeasureKind.CYLINDER -> {
                val diameter=pos("diameter",text(R.string.diameter_143));val h=pos("height",text(R.string.height_138))
                metrics.putAll(linkedMapOf("volume" to Math.PI*diameter*diameter/4*h,"diameter" to diameter,"height" to h))
                notes += if(d.internal) text(R.string.inner_diameter_and_usable_height_straight_cylinder) else text(R.string.round_base_and_perpendicular_height_measure_the)
                if(d.internal) text(R.string.cylinder_capacity) else text(R.string.cylinder_volume)
            }
            MeasureKind.AREA, MeasureKind.ROOM -> {
                outline = if(d.shape==FloorShape.CONTOUR) d.contour else {
                    val l=pos("length",text(R.string.length_136));val w=pos("width",text(R.string.width_137))
                    if(d.shape==FloorShape.RECTANGLE) listOf(Point2(0.0,0.0),Point2(l,0.0),Point2(l,w),Point2(0.0,w))
                    else { val a=pos("cutLength",text(R.string.cutout_length));val b=pos("cutWidth",text(R.string.cutout_width))
                        require(a<l&&b<w){text(R.string.the_cutout_must_be_smaller_than_the)}
                        listOf(Point2(0.0,0.0),Point2(l,0.0),Point2(l,w-b),Point2(l-a,w-b),Point2(l-a,w),Point2(0.0,w)) }
                }
                val polygon=PolygonGeometry.calculate(outline)
                if(d.kind==MeasureKind.ROOM && d.input("height").text.isNotBlank()) {
                    val h=pos("height",text(R.string.height_138));metrics["volume"]=polygon.area*h;metrics["height"]=h
                }
                metrics["area"]=polygon.area;metrics["perimeter"]=polygon.perimeter
                notes += if(d.kind==MeasureKind.ROOM) text(R.string.assumes_a_constant_ceiling_height_furniture_is) else text(R.string.one_flat_contour_without_subtracting_openings)
                if(d.kind==MeasureKind.ROOM && "volume" !in metrics)notes+=text(R.string.add_height_to_calculate_volume_the_area)
                if(d.kind==MeasureKind.ROOM) text(R.string.mode_room) else text(R.string.surface_area)
            }
            MeasureKind.FIT -> {
                val cavity=listOf(pos("width",text(R.string.recess_width)),pos("depth",text(R.string.recess_depth)),pos("height",text(R.string.recess_height)))
                val item=listOf(pos("itemWidth",text(R.string.object_width)),pos("itemDepth",text(R.string.object_depth)),pos("itemHeight",text(R.string.object_height)))
                val gap=zero("gap",text(R.string.clearance_on_each_side))
                require(d.orientation in 0 until if(d.allowTipping)6 else 2){text(R.string.select_allowed_rotation)}
                val remaining=cavity.indices.map { cavity[it]-item[rotations[d.orientation][it]]-2*gap }
                listOf("remainingWidth","remainingDepth","remainingHeight").zip(remaining).forEach { metrics[it.first]=if(kotlin.math.abs(it.second)<1e-10)0.0 else it.second }
                notes+=text(R.string.checks_a_rectangular_recess_the_chosen_orientation)
                when { metrics.values.any { it<0 } -> text(R.string.does_not_fit);metrics.values.any { it==0.0 } -> text(R.string.exact_fit);else -> text(R.string.fits_by_dimensions) }
            }
            MeasureKind.MATERIALS -> {
                val reserve=zero("reserve",text(R.string.allowance_percentage))
                when(d.material) {
                    MaterialKind.FLOOR -> { val a=pos("area",text(R.string.area));val pack=pos("packArea",text(R.string.area_per_pack));val need=a*(1+reserve/100)
                        metrics.putAll(linkedMapOf("packages" to ceil(need/pack),"requiredArea" to need,"purchasedArea" to ceil(need/pack)*pack)) }
                    MaterialKind.PAINT -> { val a=pos("area",text(R.string.area_to_be_painted));val coats=pos("coats",text(R.string.number_of_coats));require(coats%1.0==0.0){text(R.string.the_number_of_coats_must_be_a)}
                        metrics["paintLiters"]=a*coats/pos("coverage",text(R.string.coverage_in_m_l))*(1+reserve/100) }
                    MaterialKind.BASEBOARD -> { val p=pos("perimeter",text(R.string.perimeter_174));val excluded=zero("excluded",text(R.string.total_excluded_length));require(excluded<p){text(R.string.excluded_length_must_be_less_than_perimeter)}
                        val need=(p-excluded)*(1+reserve/100);val piece=pos("pieceLength",text(R.string.board_length))
                        metrics.putAll(linkedMapOf("pieces" to ceil(need/piece),"requiredLength" to need,"purchasedLength" to ceil(need/piece)*piece)) }
                }
                notes+=text(R.string.coverage_and_allowance_are_entered_by_the)
                d.material.title
            }
        }
        require(metrics.values.all { it.isFinite() && kotlin.math.abs(it)<1e15 }) { text(R.string.dimensions_too_large_to_calculate) }
        require(metrics.filterKeys { it !in listOf("remainingWidth","remainingDepth","remainingHeight") }.values.all { it>0 }) { text(R.string.dimensions_too_small_to_calculate) }
        Calculation(CalculatedMeasurement(title,metrics,outline,notes))
    } catch(e: IllegalArgumentException) { Calculation(error=e.message) } catch(e: IllegalStateException) { Calculation(error=e.message) }
}
