package com.facet.music.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringResourcesParityTest {

    @Test
    fun `en and ru strings xml have identical keys, plurals and format placeholders`() {
        val enFile = locate("src/main/res/values/strings.xml")
        val ruFile = locate("src/main/res/values-ru/strings.xml")

        val en = parseStrings(enFile)
        val ru = parseStrings(ruFile)

        assertEquals("Missing in RU: ${en.strings.keys - ru.strings.keys}", emptySet<String>(), en.strings.keys - ru.strings.keys)
        assertEquals("Extra in RU: ${ru.strings.keys - en.strings.keys}", emptySet<String>(), ru.strings.keys - en.strings.keys)

        assertEquals("Plurals mismatch", en.plurals.keys, ru.plurals.keys)
        en.plurals.forEach { (key, enQuantities) ->
            val ruQuantities = ru.plurals.getValue(key)
            assertTrue("Plural $key missing 'one'/'other' in EN", "one" in enQuantities && "other" in enQuantities)
            assertTrue("Plural $key missing 'other' in RU", "other" in ruQuantities)
        }

        en.strings.forEach { (key, enValue) ->
            val ruValue = ru.strings.getValue(key)
            assertTrue("Empty EN string: $key", enValue.isNotBlank())
            assertTrue("Empty RU string: $key", ruValue.isNotBlank())
            assertEquals("Format placeholders mismatch for $key", placeholders(enValue), placeholders(ruValue))
        }
    }

    private data class ParsedResources(
        val strings: Map<String, String>,
        val plurals: Map<String, Set<String>>,
    )

    private fun parseStrings(file: File): ParsedResources {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = linkedMapOf<String, String>()
        val plurals = linkedMapOf<String, Set<String>>()

        val stringNodes = doc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val el = stringNodes.item(i) as Element
            strings[el.getAttribute("name")] = el.textContent.orEmpty()
        }

        val pluralNodes = doc.getElementsByTagName("plurals")
        for (i in 0 until pluralNodes.length) {
            val el = pluralNodes.item(i) as Element
            val items = el.getElementsByTagName("item")
            val quantities = mutableSetOf<String>()
            for (j in 0 until items.length) {
                quantities += (items.item(j) as Element).getAttribute("quantity")
            }
            plurals[el.getAttribute("name")] = quantities
        }
        return ParsedResources(strings, plurals)
    }

    private fun placeholders(value: String): List<String> =
        FORMAT_SPECIFIER.findAll(value).map { it.value }.toList().sorted()

    private fun locate(relative: String): File = listOf(
        File(relative),
        File("app/$relative"),
        File("../app/$relative"),
    ).firstOrNull { it.isFile } ?: error("Resource file not found: $relative")

    private companion object {
        val FORMAT_SPECIFIER = Regex("""%(\d+\$)?[sdfox]""")
    }
}
