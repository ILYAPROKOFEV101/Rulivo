package com.ilya.rulivo.measure

import com.ilya.rulivo.R
import com.ilya.rulivo.i18n.text

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Called on Dispatchers.IO. Atomic replacement preserves the last successful history. */
class MeasurementRepository(file: File) {
    private val storage=AtomicFile(file)
    fun load(): List<SavedMeasurement> {
        val bytes=try { storage.openRead().use { it.readBytes() } } catch(e: java.io.FileNotFoundException) {
            if(storage.baseFile.exists())throw e else return emptyList()
        }
        val root=JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.getInt("version") in 1..2) { text(R.string.history_version_not_supported) }
        val records=root.getJSONArray("records")
        return (0 until records.length()).map { decode(records.getJSONObject(it)) }
    }
    fun save(records: List<SavedMeasurement>) {
        val json=JSONObject().put("version",2).put("records",JSONArray().apply { records.forEach { put(encode(it)) } })
        val stream=storage.startWrite()
        try { stream.write(json.toString().toByteArray(Charsets.UTF_8));storage.finishWrite(stream) }
        catch(e: Exception) { storage.failWrite(stream);throw e }
    }
    private fun points(p: List<Point2>)=JSONArray().apply { p.forEach { put(JSONArray().put(it.x).put(it.y)) } }
    private fun readPoints(a: JSONArray)=(0 until a.length()).map { Point2(a.getJSONArray(it).getDouble(0),a.getJSONArray(it).getDouble(1)) }
    private fun encode(record: SavedMeasurement): JSONObject {
        val d=record.draft
        val inputs=JSONObject();d.inputs.forEach { (k,v)-> inputs.put(k,JSONObject().put("text",v.text).put("source",v.source.name)) }
        val draft=JSONObject().put("kind",d.kind.name).put("inputs",inputs).put("shape",d.shape.name)
            .put("contour",points(d.contour)).put("contourSource",d.contourSource.name).put("internal",d.internal)
            .put("orientation",d.orientation).put("allowTipping",d.allowTipping).put("material",d.material.name)
            .put("name",d.name).put("volumeUnit",d.volumeUnit.name)
        val metrics=JSONObject();record.result.metrics.forEach { (k,v)->metrics.put(k,v) }
        return JSONObject().put("id",record.id).put("createdAt",record.createdAt).put("draft",draft)
            .put("result",JSONObject().put("metrics",metrics).put("outline",points(record.result.outline)))
    }
    private fun decode(record: JSONObject): SavedMeasurement {
        val j=record.getJSONObject("draft");val inputs=j.getJSONObject("inputs")
        val draft=MeasureDraft(MeasureKind.valueOf(j.getString("kind")),inputs.keys().asSequence().associateWith { k ->
            val v=inputs.getJSONObject(k);DimensionInput(v.getString("text"),ValueSource.valueOf(v.getString("source")))
        },FloorShape.valueOf(j.getString("shape")),readPoints(j.getJSONArray("contour")),ValueSource.valueOf(j.getString("contourSource")),
            j.getBoolean("internal"),j.getInt("orientation"),j.getBoolean("allowTipping"),MaterialKind.valueOf(j.getString("material")),j.getString("name"),VolumeUnit.valueOf(j.getString("volumeUnit")))
        val result=record.getJSONObject("result");val metrics=result.getJSONObject("metrics");val presentation=MeasurementCalculator.calculate(draft).result
        val calculated=CalculatedMeasurement(presentation?.title ?: draft.kind.title,metrics.keys().asSequence().associateWith { metrics.getDouble(it) },readPoints(result.getJSONArray("outline")),presentation?.notes.orEmpty())
        require(calculated.metrics.values.all { it.isFinite() }) { text(R.string.corrupted_values_in_history) }
        return SavedMeasurement(record.getString("id"),record.getLong("createdAt"),draft,calculated)
    }
}
