package eu.filipnone.starbooru

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.NodeList

/** Keeps English and Italian in step: the same names, and the same arguments in each. */
class StringsInStepTest {
    private data class Text(
        val file: String,
        val plural: Boolean,
        val translatable: Boolean,
        val args: Set<String>,
        val quantities: Set<String>,
    )

    /** `src/main/res` from the module (where Gradle runs tests) or from the repository root. */
    private val res: File =
        listOf("src/main/res", "app/src/main/res")
            .map { File(it).absoluteFile }
            .firstOrNull { it.resolve("values").isDirectory }
            ?: throw AssertionError("no src/main/res under ${File(".").absolutePath}")

    private val english = read("values")
    private val italian = read("values-it")

    private fun read(folder: String): Map<String, Text> {
        val files =
            res
                .resolve(folder)
                .listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }
                .orEmpty()
                .sortedBy { it.name }
        assertTrue("no strings*.xml in $folder", files.isNotEmpty())
        val texts = LinkedHashMap<String, Text>()
        for (file in files) {
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            for (node in root.childNodes.elements()) {
                val plural =
                    when (node.tagName) {
                        "string" -> false
                        "plurals" -> true
                        else -> continue
                    }
                val name = node.getAttribute("name")
                val items = if (plural) node.childNodes.elements().filter { it.tagName == "item" } else listOf(node)
                val text =
                    Text(
                        file = "$folder/${file.name}",
                        plural = plural,
                        translatable = node.getAttribute("translatable") != "false",
                        // A string marked unformatted takes no arguments: its % is a percent.
                        args = if (node.getAttribute("formatted") ==
                            "false"
                        ) {
                            emptySet()
                        } else {
                            items.flatMap { arguments(it.textContent) }.toSet()
                        },
                        quantities = items.map { it.getAttribute("quantity") }.toSet(),
                    )
                val before = texts.put(name, text)
                assertTrue("$name is in both ${before?.file} and ${text.file}", before == null)
            }
        }
        return texts
    }

    private fun NodeList.elements(): List<Element> = (0 until length).mapNotNull { item(it) as? Element }

    @Test
    fun bothLanguagesHaveText() {
        assertTrue(english.size > 100)
        assertTrue(italian.size > 100)
    }

    @Test
    fun everyEnglishTextIsInItalian() {
        val missing = english.filter { (name, t) -> t.translatable && name !in italian }.keys
        assertEquals("not in values-it", emptySet<String>(), missing)
    }

    @Test
    fun italianHasNothingEnglishLacks() {
        val extra = italian.keys - english.filter { it.value.translatable }.keys
        assertEquals("in values-it but not translatable in values", emptySet<String>(), extra)
    }

    @Test
    fun pluralsArePluralsInBoth() {
        val differ = english.filter { (name, t) -> italian[name]?.let { it.plural != t.plural } == true }.keys
        assertEquals(emptySet<String>(), differ)
    }

    @Test
    fun everyPluralHasOther() {
        val lacking = (english + italian.mapKeys { "it:${it.key}" }).filter { it.value.plural && "other" !in it.value.quantities }.keys
        assertEquals(emptySet<String>(), lacking)
    }

    @Test
    fun bothLanguagesTakeTheSameArguments() {
        val differ =
            english.mapNotNull { (name, t) ->
                val other = italian[name] ?: return@mapNotNull null
                if (other.args == t.args) null else "$name: ${t.args.sorted()} in English, ${other.args.sorted()} in Italian"
            }
        assertEquals(emptyList<String>(), differ)
    }

    @Test
    fun argumentsAreReadAsAndroidDoes() {
        assertEquals(setOf("1\$s", "2\$d"), arguments("Signed in as %1\$s, %2\$d posts"))
        assertEquals(setOf("1\$s", "2\$d"), arguments("%s and %d"))
        assertEquals(emptySet<String>(), arguments("100%% sure"))
        assertEquals(setOf("1\$.2f"), arguments("%1\$.2f MB"))
    }

    private companion object {
        val FORMAT = Regex("""%(?:(\d+)\$)?([-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%])""")

        /** The format arguments in [text], each as `index$conversion`. */
        fun arguments(text: String): Set<String> {
            var next = 0
            return FORMAT
                .findAll(text)
                .mapNotNull { m ->
                    val conversion = m.groupValues[2]
                    if (conversion == "%") return@mapNotNull null
                    val index = m.groupValues[1].ifEmpty { (++next).toString() }
                    "$index\$$conversion"
                }.toSet()
        }
    }
}
