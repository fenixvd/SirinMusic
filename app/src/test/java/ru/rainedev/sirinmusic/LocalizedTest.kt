package ru.rainedev.sirinmusic

import org.junit.Before
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** Real XML strings, without an Android runtime, for JVM API/validation tests. */
open class LocalizedTest {
    @Before fun installTestTextResources() = loadTextResources("ru")

    protected fun loadTextResources(language: String) {
        val locale = Locale.forLanguageTag(language)
        val file = File("src/main/res/${if (language == "ru") "values-ru" else "values"}/strings.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        val values = (0 until nodes.length).associate { i ->
            val n = nodes.item(i)
            R.string::class.java.getField(n.attributes.getNamedItem("name").nodeValue).getInt(null) to
                n.textContent.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
        }
        TextResources.locale = { locale }
        val pluralNodes = document.getElementsByTagName("plurals")
        val plurals = (0 until pluralNodes.length).associate { i ->
            val node = pluralNodes.item(i) as org.w3c.dom.Element
            val children = node.getElementsByTagName("item")
            R.plurals::class.java.getField(node.getAttribute("name")).getInt(null) to
                (0 until children.length).associate { j ->
                    val child = children.item(j) as org.w3c.dom.Element
                    child.getAttribute("quantity") to child.textContent
                }
        }
        TextResources.quantityResolver = { id, count, args ->
            val category = if (language == "ru") when {
                count % 10 == 1 && count % 100 != 11 -> "one"
                count % 10 in 2..4 && count % 100 !in 12..14 -> "few"
                else -> "many"
            } else if (count == 1) "one" else "other"
            val forms = plurals.getValue(id)
            String.format(locale, forms[category] ?: forms.getValue("other"), *args)
        }
        TextResources.resolver = { id, args ->
            val value = values.getValue(id)
            if (args.isEmpty()) value else String.format(locale, value, *args)
        }
    }
}
