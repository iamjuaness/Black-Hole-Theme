package com.github.iamjuaness.blackholetheme

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.pow

/** Static checks over the bundled themes: no IDE needed, so they run in plain JUnit. */
class ThemeResourcesTest {

    private val themes = listOf("dark", "purple", "green", "gray").map { "/theme/black_hole_$it" }

    private fun json(base: String): JsonObject =
        javaClass.getResourceAsStream("$base.theme.json")!!.reader().use { JsonParser.parseReader(it).asJsonObject }

    @Test
    fun `editor scheme exists and is valid xml`() {
        for (base in themes) {
            val scheme = json(base)["editorScheme"].asString
            val stream = javaClass.getResourceAsStream(scheme)
            assertTrue("$base: missing $scheme", stream != null)
            stream!!.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        }
    }

    @Test
    fun `every named color used in ui is declared in colors`() {
        for (base in themes) {
            val theme = json(base)
            val palette = theme["colors"].asJsonObject.keySet()
            val missing = mutableSetOf<String>()
            fun walk(e: com.google.gson.JsonElement) {
                when {
                    e.isJsonObject -> e.asJsonObject.entrySet().forEach { walk(it.value) }
                    e.isJsonPrimitive && e.asJsonPrimitive.isString -> {
                        val v = e.asString
                        // palette names are snake_case words; hex, numbers and keys with dots/commas are not
                        if (Regex("[a-z][a-z0-9_]*").matches(v) && v !in palette && v !in setOf("none", "true", "false")) missing += v
                    }
                }
            }
            walk(theme["ui"])
            assertTrue("$base: undeclared colors $missing", missing.isEmpty())
        }
    }

    @Test
    fun `default text has readable contrast`() {
        for (base in themes) {
            val theme = json(base)
            val colors = theme["colors"].asJsonObject
            val star = theme["ui"].asJsonObject["*"].asJsonObject
            fun hex(key: String) = star[key].asString.let { colors[it]?.asString ?: it }
            val ratio = contrast(hex("foreground"), hex("background"))
            assertTrue("$base: contrast $ratio < 4.5", ratio >= 4.5)
        }
    }

    private fun luminance(hex: String): Double {
        val c = hex.removePrefix("#").chunked(2).take(3).map { it.toInt(16) / 255.0 }
            .map { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
    }

    private fun contrast(a: String, b: String): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }
}
