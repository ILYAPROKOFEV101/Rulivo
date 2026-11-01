package com.ilya.rulivo

import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.platform.app.InstrumentationRegistry
import com.ilya.rulivo.i18n.AppStrings
import com.ilya.rulivo.measure.*
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class LocaleResourcesTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun resources(tags: String) = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tags)) }
    ).resources

    @After fun restoreResources() { AppStrings.initialize(context.resources) }

    @Test fun androidAutomaticallyMatchesLanguageListsAndFallsBackToEnglish() {
        val cases = mapOf("en-US" to "en", "id-ID" to "id", "in-ID" to "id", "ur-PK" to "ur",
            "ha-NG" to "ha", "hi-IN" to "hi", "fr-FR" to "en", "ru-RU" to "en", "ar-SA" to "en",
            "en-IN" to "en", "en-PK" to "en", "en-NG" to "en", "fr-FR,id-ID" to "id",
            "en-US,hi-IN" to "en", "ur-PK,en-US" to "ur")
        cases.forEach { (tags, expected) ->
            assertEquals(tags, expected, resources(tags).getString(R.string.resource_locale))
        }
    }

    @Test fun allTranslatedFormatsResolveAndContainNoRussianOrUnfilledArguments() {
        for (tag in listOf("en", "id", "ur", "ha", "hi")) {
            val resources = resources(tag)
            AppStrings.initialize(resources)
            for (field in R.string::class.java.fields) {
                val id = field.getInt(null)
                val pattern = resources.getString(id)
                val count = Regex("%(\\d+)[$]s").findAll(pattern).map { it.groupValues[1].toInt() }.maxOrNull() ?: 0
                val resolved = AppStrings.get(id, *Array<Any>(count) { it + 1 })
                assertFalse("$tag/${field.name}: $resolved", Regex("[А-Яа-яЁё]|%\\d+[$]s").containsMatchIn(resolved))
            }
        }
    }

    @Test fun oldHistoryLoadsAndNewHistoryStoresNoTranslatedTitles() {
        val file = File(context.cacheDir, "locale-history-${UUID.randomUUID()}.json")
        try {
            AppStrings.initialize(resources("en"))
            val draft = MeasureDraft(name = "Personal name").withInput("length", "2")
                .withInput("width", "3").withInput("height", "4")
            val record = SavedMeasurement("saved", 10L, draft, MeasurementCalculator.calculate(draft).result!!)
            val repository = MeasurementRepository(file)
            repository.save(listOf(record))
            val json = JSONObject(file.readText())
            val result = json.getJSONArray("records").getJSONObject(0).getJSONObject("result")
            assertEquals(2, json.getInt("version"))
            assertFalse(result.has("title")); assertFalse(result.has("notes"))
            // Version 1 stored translated presentation strings; they must not leak after switching.
            json.put("version", 1)
            result.put("title", "Объём по габаритам").put("notes", org.json.JSONArray().put("Старое описание"))
            file.writeText(json.toString())
            AppStrings.initialize(resources("hi-IN"))
            val loaded = repository.load().single()
            assertEquals("Personal name", loaded.displayName)
            assertEquals(record.result.metrics, loaded.result.metrics)
            assertNotEquals(record.result.title, loaded.localizedResult.title)
            assertFalse(loaded.localizedResult.toString().contains("Старое описание"))
        } finally { file.delete() }
    }
}
