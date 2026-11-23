package pt.pine3.wall

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** O layout do exemplo (gerado pela integração) e o catálogo de módulos, lidos pela app. */
class LayoutTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "schema/catalog.json").exists() }
    private fun json(path: String) = JSONObject(File(root, path).readText())
    private val home = Home.parse(json("schema/examples/home.json"))

    @Test fun exampleHasALayout() {
        val layout = home.layout
        assertNotNull(layout)
        assertEquals("home", layout!!.dock.first())
        val page = layout.page("home")!!
        assertTrue(page.template in HomeModule.templates)
        assertTrue(page.has("clock"))
        val grid = page.section("grid")!!
        val rooms = grid.cards.filter { it.type == "room" }.map { it.room }
        assertEquals(home.rooms.filter { !it.hidden }.map { it.id }, rooms)
        home.rooms.forEach { r -> assertNotNull("page for ${r.id}", layout.page("room/${r.id}")) }
    }

    @Test fun appKnowsEveryTypeOfTheHomeModule() {
        val m = json("schema/catalog.json").getJSONObject("modules").getJSONObject("home")
        assertEquals(m.getJSONObject("sections").keys().asSequence().toSet(), HomeModule.sectionTypes)
        assertEquals(m.getJSONObject("cards").keys().asSequence().toSet(), HomeModule.cardTypes)
        val templates = m.getJSONArray("templates").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        assertEquals(templates, HomeModule.templates)
    }

    @Test fun everyTypeInTheExampleIsKnown() {
        val layout = home.layout!!
        layout.pages.values.flatMap { it.sections }.forEach { s ->
            assertTrue("section ${s.type}", ModuleRegistry.knowsSection(s.type))
            s.cards.forEach { c -> assertTrue("card ${c.type}", ModuleRegistry.knowsCard(c.type)) }
        }
        assertFalse(ModuleRegistry.knowsCard("calendar_upcoming"))
    }

    @Test fun cardKeysMatchTheEditMode() {
        val doc = LayoutDoc.parse(JSONObject("""{"dock":[],"pages":{"home":{"sections":[{"type":"grid","cards":[
            {"type":"room","room":"sala","size":"big"},{"type":"summary","summary":"climate"},
            {"type":"summary","summary":"vacuum","entity":"vacuum.robot"},{"type":"device","entity":"light.x"}]}]}}}"""))
        val cards = doc.page("home")!!.section("grid")!!.cards
        assertEquals(listOf("sala", "@climate", "@vacuum:vacuum.robot", "light.x"), cards.map { it.key })
        assertTrue(cards[0].big)
        assertEquals(listOf("home"), doc.dock) // dock vazia: só o Início
        assertNull(doc.page("rooms"))
    }

    @Test fun missingLayoutMeansAnOldIntegration() {
        assertNotNull(Compat.problem(Compat.API_VERSION, "0.1.0", "0.4.0", hasLayout = false))
        assertNull(Compat.problem(Compat.API_VERSION, "0.1.0", "0.4.0", hasLayout = true))
    }
}
