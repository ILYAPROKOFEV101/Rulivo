package com.ilya.rulivo.i18n

import com.ilya.rulivo.R
import org.junit.Before
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** JVM tests read the actual resource catalogs without needing an Android runtime. */
open class LocalizedTest {
    @Before fun initializeEnglishStrings() { useLanguage("en") }

    protected fun useLanguage(tag: String) {
        val locale = Locale.forLanguageTag(tag)
        val root = File("src/main/res")
        fun read(directory: String): Map<String, String> {
            val files = File(root, directory).listFiles { file -> file.extension == "xml" }
                ?: return emptyMap()
            return files.flatMap { file ->
                val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
                val nodes = document.getElementsByTagName("string")
                (0 until nodes.length).map { index ->
                    val node = nodes.item(index)
                    node.attributes.getNamedItem("name").nodeValue to node.textContent.removeSurrounding("\"")
                        .replace("\\\"", "\"").replace("\\'", "'").replace("\\n", "\n")
                }
            }.toMap()
        }
        val qualifier = if (locale.language == "id") "in" else locale.language
        val values = read("values") + read("values-$qualifier")
        val byId = R.string::class.java.fields.associate { it.getInt(null) to values.getValue(it.name) }
        AppStrings.source = object : AppStrings.Source {
            override val locale = locale
            override fun get(id: Int) = byId.getValue(id)
        }
    }
}
