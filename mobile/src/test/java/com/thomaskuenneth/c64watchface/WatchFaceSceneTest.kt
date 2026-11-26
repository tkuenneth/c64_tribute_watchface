package com.thomaskuenneth.c64watchface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private val SCENES = listOf(
    File("../watchface/src/main/res/raw/watchface_round.xml"),
    File("../watchface/src/main/res/raw/watchface_square.xml"),
)

private val LOCALES = listOf("values", "values-de")

private fun scene(file: File): Element =
    DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement

private fun elements(root: Element, tag: String): List<Element> {
    val nodes = root.getElementsByTagName(tag)
    return (0 until nodes.length).map { index -> nodes.item(index) as Element }
}

private fun stringsIn(locale: String): Map<String, String> {
    val file = File("../watchface/src/main/res/$locale/strings.xml")
    return elements(scene(file), "string")
        .associate { string -> string.getAttribute("name") to string.textContent }
}

private fun attributeValues(root: Element, attribute: String): List<String> {
    val found = mutableListOf<String>()
    fun walk(node: Node) {
        if (node is Element) {
            node.getAttribute(attribute).takeIf { it.isNotEmpty() }?.let(found::add)
            (0 until node.childNodes.length).forEach { index -> walk(node.childNodes.item(index)) }
        }
    }
    walk(root)
    return found
}

class WatchFaceSceneTest {

    @Test
    fun everySceneIsWellFormedXml() {
        SCENES.forEach { file ->
            assertTrue("${file.name} is missing", file.isFile)
            assertEquals("WatchFace", scene(file).tagName)
        }
    }

    @Test
    fun everyStringReferenceResolvesInEveryLocale() {
        val declared = LOCALES.associateWith(::stringsIn)
        SCENES.forEach { file ->
            val root = scene(file)
            val referenced = (attributeValues(root, "displayName") +
                attributeValues(root, "screenReaderText") +
                attributeValues(root, "expression"))
                .filter { it.startsWith("@string/") }
                .map { it.removePrefix("@string/") }
            assertTrue("${file.name} references no strings", referenced.isNotEmpty())
            declared.forEach { (locale, names) ->
                referenced.forEach { name ->
                    assertTrue("$name missing in $locale (${file.name})", name in names.keys)
                }
            }
        }
    }

    @Test
    fun theDateUsesOurOwnWeekdayNamesInEveryLocale() {
        LOCALES.forEach { locale ->
            val strings = stringsIn(locale)
            (1..7).map { "weekday_$it" }.forEach { name ->
                val text = strings[name]
                assertTrue("$name missing in $locale", text != null)
                assertTrue("$name must not end with a dot in $locale", !text!!.endsWith("."))
            }
        }
    }

    @Test
    fun everyDateSiteOffersAllSevenWeekdays() {
        SCENES.forEach { file ->
            val sites = dateSites(scene(file))
            assertTrue("${file.name} has no date sites", sites.isNotEmpty())
            sites.forEach { site ->
                assertEquals(
                    "a date site in ${file.name} is missing weekdays",
                    (1..7).map { "weekday_$it" },
                    site.map { part -> part.weekday },
                )
            }
        }
    }

    @Test
    fun theSevenBranchesOfADateSiteAreIdenticalApartFromTheWeekday() {
        SCENES.forEach { file ->
            dateSites(scene(file)).forEach { site ->
                val shapes = site.map { part -> part.shape }.toSet()
                assertEquals("branches of a date site differ in ${file.name}: $shapes", 1, shapes.size)
            }
        }
    }

    @Test
    fun dateTextOfTheSameSizeIsLaidOutTheSameEverywhere() {
        SCENES.forEach { file ->
            val bySize = dateSites(scene(file)).flatten().groupBy { part -> part.size }
            assertTrue("${file.name} has no date text", bySize.isNotEmpty())
            bySize.forEach { (size, parts) ->
                assertEquals(
                    "date text at size $size is boxed differently in ${file.name}: " +
                        parts.map { part -> part.box }.toSet(),
                    1,
                    parts.map { part -> part.box }.toSet().size,
                )
            }
        }
    }

    @Test
    fun everyConfigurationDefaultsToOneOfItsOptions() {
        SCENES.forEach { file ->
            val configurations = elements(scene(file), "ListConfiguration")
                .filter { it.hasAttribute("defaultValue") }
            assertEquals("${file.name} configurations", 3, configurations.size)
            configurations.forEach { configuration ->
                val options = elements(configuration, "ListOption").map { it.getAttribute("id") }
                assertTrue(
                    "${configuration.getAttribute("id")} default is not an option (${file.name})",
                    configuration.getAttribute("defaultValue") in options,
                )
            }
        }
    }

    private data class DatePart(
        val weekday: String,
        val size: String,
        val box: String,
        val style: String,
    ) {
        val shape: String get() = "$size $box $style"
    }

    private fun datePart(part: Element): DatePart? {
        val weekday = elements(part, "Parameter")
            .map { it.getAttribute("expression") }
            .firstOrNull { it.startsWith("weekday_") }
            ?: return null
        val font = elements(part, "Font").first()
        val text = elements(part, "Text").first()
        return DatePart(
            weekday = weekday,
            size = font.getAttribute("size"),
            box = listOf(
                part.getAttribute("x"), part.getAttribute("y"),
                part.getAttribute("width"), part.getAttribute("height"),
                text.getAttribute("align"),
            ).joinToString(" "),
            style = listOf(
                font.getAttribute("color"), font.getAttribute("family"),
                elements(part, "Upper").isNotEmpty().toString(),
            ).joinToString(" "),
        )
    }

    private fun children(element: Element, tag: String): List<Element> =
        (0 until element.childNodes.length)
            .mapNotNull { index -> element.childNodes.item(index) as? Element }
            .filter { child -> child.tagName == tag }

    private fun ownExpressions(condition: Element): List<String> =
        children(condition, "Expressions")
            .flatMap { expressions -> children(expressions, "Expression") }
            .map { expression -> expression.textContent }

    private fun dateSites(root: Element): List<List<DatePart>> =
        elements(root, "Condition")
            .filter { condition ->
                ownExpressions(condition).any { it.contains("[DAY_OF_WEEK] == 1") }
            }
            .map { condition -> elements(condition, "PartText").mapNotNull(::datePart) }
            .filter { parts -> parts.isNotEmpty() }
}
