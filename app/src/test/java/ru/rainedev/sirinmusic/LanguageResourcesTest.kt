package ru.rainedev.sirinmusic

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LanguageResourcesTest : LocalizedTest() {
    @Test fun bothLanguagesCoverTheSameMessagesAndFormattingArguments() {
        fun entries(directory: String): Map<String, String> {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File("src/main/res/$directory/strings.xml")).getElementsByTagName("string")
            return (0 until nodes.length).map { nodes.item(it) }
                .filter { it.attributes.getNamedItem("translatable")?.nodeValue != "false" }.associate { n ->
                n.attributes.getNamedItem("name").nodeValue to n.textContent
            }
        }
        val en = entries("values"); val ru = entries("values-ru")
        assertEquals(en.keys, ru.keys)
        val placeholders = Regex("%[0-9]+\\$[sdf]")
        en.forEach { (name, value) ->
            assertTrue("Empty English text: $name", value.isNotBlank())
            assertTrue("Empty Russian text: $name", ru.getValue(name).isNotBlank())
            assertEquals(name, placeholders.findAll(value).map { it.value }.sorted().toList(),
                placeholders.findAll(ru.getValue(name)).map { it.value }.sorted().toList())
        }
    }
    @Test fun languageChangeUpdatesMenuAndBackgroundMessages() {
        loadTextResources("en")
        assertEquals("App language", strings(R.string.language_title))
        assertEquals("Sirin Music 2.1 is available", strings(R.string.update_manager_3, "2.1"))
        loadTextResources("ru")
        assertEquals("Язык приложения", strings(R.string.language_title))
        assertEquals("Доступна Sirin Music 2.1", strings(R.string.update_manager_3, "2.1"))
    }
    @Test fun countMessagesHaveSingularFewAndManyForms() {
        loadTextResources("ru")
        assertEquals("1 трек", trackCount(1)); assertEquals("2 трека", trackCount(2))
        assertEquals("11 треков", trackCount(11)); assertEquals("21 трек", trackCount(21))
        loadTextResources("en")
        assertEquals("1 track", trackCount(1)); assertEquals("2 tracks", trackCount(2))
    }
}
