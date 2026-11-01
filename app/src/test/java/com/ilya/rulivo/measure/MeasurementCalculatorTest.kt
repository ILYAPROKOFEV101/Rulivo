package com.ilya.rulivo.measure

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import org.junit.Assert.*
import org.junit.Test

class MeasurementCalculatorTest : com.ilya.rulivo.i18n.LocalizedTest() {
    private fun draft(kind: MeasureKind,vararg values: Pair<String,String>)=values.fold(MeasureDraft(kind)){d,(k,v)->d.withInput(k,v)}
    private fun result(d: MeasureDraft)=MeasurementCalculator.calculate(d).result!!
    @Test fun boxAndUnitsUseCubicConversion() {
        val d=draft(MeasureKind.BOX,"length" to "0,6","width" to ".4","height" to "0.35")
        val v=result(d).metrics.getValue("volume")
        assertEquals(.084,v,1e-12)
        assertEquals("84 l",Quantities.volume(v,VolumeUnit.LITERS))
        assertEquals("84000 cm³",Quantities.volume(v,VolumeUnit.CUBIC_CENTIMETERS))
        assertEquals("0.084 m³",Quantities.volume(v,VolumeUnit.CUBIC_METERS))
        assertEquals("0.000125 m³",Quantities.volume(.05*.05*.05,VolumeUnit.CUBIC_METERS))
        assertEquals(text(R.string.capacity),result(d.copy(internal=true)).title)
    }
    @Test fun cylinderUsesRadiusNotDiameterSquared() {
        val d=draft(MeasureKind.CYLINDER,"diameter" to ".4","height" to ".7")
        assertEquals(.028*Math.PI,result(d).metrics.getValue("volume"),1e-12)
    }
    @Test fun roomWithMissingHeightStillProvidesAreaButInvalidHeightDoesNot() {
        val d=draft(MeasureKind.ROOM,"length" to "4.2","width" to "3.6")
        assertFalse(result(d).metrics.containsKey("volume"))
        assertEquals(15.12,result(d).metrics.getValue("area"),1e-10)
        assertEquals(40.824,result(d.withInput("height","2.7")).metrics.getValue("volume"),1e-10)
        listOf("0","-2","NaN","Infinity","wrong").forEach {assertNull(MeasurementCalculator.calculate(d.withInput("height",it)).result)}
    }
    @Test fun concaveRoomExcludesNotchAndSupportsArContour() {
        val d=draft(MeasureKind.ROOM,"length" to "4","width" to "4","cutLength" to "2","cutWidth" to "2","height" to "2.7").copy(shape=FloorShape.L_SHAPE)
        val r=result(d)
        assertEquals(12.0,r.metrics.getValue("area"),1e-10)
        assertEquals(16.0,r.metrics.getValue("perimeter"),1e-10)
        assertEquals(32.4,r.metrics.getValue("volume"),1e-10)
        val ar=d.copy(shape=FloorShape.CONTOUR,contour=r.outline,contourSource=ValueSource.AR)
        assertEquals(r.metrics,result(ar).metrics)
        assertNull(MeasurementCalculator.calculate(d.withInput("cutLength","4")).result)
    }
    @Test fun fitUsesEachAxisAndTwoSidedClearanceNotVolume() {
        val d=draft(MeasureKind.FIT,"width" to "1","depth" to ".6","height" to ".8","itemWidth" to ".98","itemDepth" to ".55","itemHeight" to ".75","gap" to ".01")
        val r=result(d)
        assertEquals(text(R.string.exact_fit),r.title)
        assertEquals(.03,r.metrics.getValue("remainingDepth"),1e-10)
        val turned=result(d.copy(orientation=1))
        assertEquals(text(R.string.does_not_fit),turned.title)
        assertEquals(-.4,turned.metrics.getValue("remainingDepth"),1e-10)
        assertNull(MeasurementCalculator.calculate(d.copy(orientation=2)).result)
        assertNotNull(MeasurementCalculator.calculate(d.copy(orientation=2,allowTipping=true)).result)
    }
    @Test fun materialRoundingAndCoverageAreDimensionallyCorrect() {
        val floor=draft(MeasureKind.MATERIALS,"area" to "15.12","reserve" to "10","packArea" to "2.2")
        assertEquals(8.0,result(floor).metrics.getValue("packages"),0.0)
        assertEquals("8",Quantities.metric("packages",8.0,VolumeUnit.CUBIC_METERS))
        assertEquals(16.632,result(floor).metrics.getValue("requiredArea"),1e-10)
        val paint=floor.copy(material=MaterialKind.PAINT).withInput("coats","2").withInput("coverage","10")
        assertEquals(3.3264,result(paint).metrics.getValue("paintLiters"),1e-10)
        assertNull(MeasurementCalculator.calculate(paint.withInput("coats","1.5")).result)
        val base=floor.copy(material=MaterialKind.BASEBOARD).withInput("perimeter","15.6").withInput("excluded",".9").withInput("pieceLength","2.5")
        assertEquals(7.0,result(base).metrics.getValue("pieces"),0.0)
        assertNull(MeasurementCalculator.calculate(base.withInput("excluded","20")).result)
    }
    @Test fun incompleteOrNonfiniteInputsCannotProduceSavableResult() {
        assertNull(MeasurementCalculator.calculate(MeasureDraft()).result)
        listOf("", " ", "1,2,3", "NaN", "Infinity", "-1", "1e309").forEach {assertNull(Quantities.parse(it))}
        assertNull(MeasurementCalculator.calculate(draft(MeasureKind.BOX,"length" to "0","width" to "1","height" to "1")).result)
        assertNull(MeasurementCalculator.calculate(draft(MeasureKind.BOX,"length" to "99999999999999999999999999","width" to "99999999999999999999999999","height" to "99999999999999999999999999")).result)
    }
}
