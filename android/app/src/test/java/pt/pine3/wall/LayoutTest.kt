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

    @Test fun roomPagesListTheVisibleDevices() {
        val layout = home.layout!!
        home.rooms.forEach { r ->
            val grid = layout.page("room/${r.id}")!!.section("grid")!!
            assertEquals("category", grid.group)
            assertEquals(r.devices.filter { !it.hidden }.map { it.id }.toSet(), grid.cards.map { it.entity }.toSet())
        }
        val rooms = layout.page("rooms")!!.section("grid")!!.cards.map { it.room }
        assertEquals(home.rooms.filter { !it.hidden }.map { it.id }, rooms)
    }

    @Test fun pagesWaitForTheLayoutAfterAnEdit() {
        Lay.awaitingLayout = false
        assertTrue(Lay.useLayout)
        Lay.awaitingLayout = true // gravou-se uma edição: desenha-se pelas definições até o HA mandar o layout novo
        assertFalse(Lay.useLayout)
        Lay.awaitingLayout = false
    }

    /** Um layout publicado: a Sala grande à frente, a Cozinha escondida, uma lâmpada fora da página da Sala. */
    private fun customHome(): Home {
        val o = json("schema/examples/home.json")
        val l = o.getJSONObject("layout").put("generated", false)
        val grid = l.getJSONObject("pages").getJSONObject("home").getJSONArray("sections").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.first { it.getString("type") == "grid" } }
        val cards = org.json.JSONArray()
        cards.put(JSONObject().put("type", "room").put("room", "sala").put("size", "big"))
        cards.put(JSONObject().put("type", "summary").put("summary", "climate"))
        grid.put("cards", cards)
        o.put("layout_source", "published")
        return Home.parse(o)
    }

    @Test fun editModeStartsFromAPublishedLayout() {
        val h = customHome()
        assertEquals("published", h.layoutSource)
        val e = Lay.fromLayout(h, h.layout!!)
        assertTrue(e.custom)
        assertEquals(listOf("sala", "@climate"), e.roomOrder.take(2))
        assertTrue("cozinha" in e.hiddenRooms && "sala" !in e.hiddenRooms)
        assertEquals(listOf("sala"), e.bigRooms)
        val sala = h.rooms.first { it.id == "sala" }
        assertEquals(sala.devices.filter { !it.hidden }.map { it.id }.toSet(), e.deviceOrder["sala"]!!.toSet())
    }

    @Test fun automaticLayoutEditsTheSettings() {
        Lay.begin(home)
        assertFalse(Lay.edit!!.custom)
        Lay.edit = null
        Lay.begin(customHome())
        assertTrue(Lay.edit!!.custom)
        Lay.edit = null
    }

    @Test fun appKnowsTheAmbientModule() {
        val m = json("schema/catalog.json").getJSONObject("modules").getJSONObject("ambient")
        assertEquals(m.getJSONObject("sections").keys().asSequence().toSet(), AmbientModule.sectionTypes)
        assertEquals(m.getJSONObject("faces").keys().asSequence().toSet(), AmbientFace.TYPES)
        assertTrue(ModuleRegistry.knowsSection("ambient"))
    }

    @Test fun appKnowsTheCalendarModule() {
        val m = json("schema/catalog.json").getJSONObject("modules").getJSONObject("calendar")
        assertEquals(m.getJSONObject("sections").keys().asSequence().toSet(), CalendarModule.sectionTypes)
        assertEquals(m.getJSONObject("cards").keys().asSequence().toSet(), CalendarModule.cardTypes)
        assertTrue(ModuleRegistry.knowsCard("upcoming") && ModuleRegistry.knowsSection("agenda"))
    }

    @Test fun parsesTheFamilyCalendar() {
        val cal = CalendarData.parse(JSONObject("""{"entities":["calendar.familia"],"days":14,"color":"#4aa8ff","events":[
            {"id":"b","title":"Natação","start":"2026-11-24T17:30","end":"2026-11-24T18:30","all_day":false,"people":["person.pedro"],"calendar":"calendar.familia","location":"Piscina"},
            {"id":"d","title":"Noite","start":"2026-11-24T22:00","end":"2026-11-25T00:00","all_day":false,"people":[],"calendar":"calendar.t"},
            {"id":"c","title":"","start":"2026-11-24T23:00","end":"2026-11-25T01:00","all_day":false,"people":[],"calendar":"calendar.t","private":true},
            {"id":"a","title":"Férias","start":"2026-11-26","end":"2026-11-29","all_day":true,"people":[],"calendar":"calendar.familia"}]}"""))
        assertEquals("#4aa8ff", cal.color)
        assertEquals(listOf("b", "d", "c"), cal.on("2026-11-24").map { it.id })
        assertEquals(listOf("c"), cal.on("2026-11-25").map { it.id }) // "d" acaba à meia-noite
        assertEquals(listOf("a"), cal.on("2026-11-28").map { it.id })
        assertTrue(cal.on("2026-11-29").isEmpty()) // o fim de um evento de dia inteiro é exclusivo
        assertTrue(cal.events[2].private)
        assertEquals("17:30", cal.events[0].time)
        // às 18:00 de dia 24 a Natação ainda não acabou (acaba às 18:30)
        val now = java.time.LocalDateTime.of(2026, 11, 24, 18, 0)
        assertEquals(listOf("b", "d", "c"), cal.upcoming(3, now = now).map { it.id })
        assertEquals(listOf("a"), cal.upcoming(5, person = "person.ana", now = java.time.LocalDateTime.of(2026, 11, 25, 9, 0)).map { it.id })
        val card = LCard.parse(JSONObject("""{"type":"upcoming","person":"person.ana","count":3}"""))
        assertEquals("@upcoming:person.ana", card.key)
        assertEquals(3, card.count)
    }

    @Test fun parsesAnAmbientScreen() {
        val doc = LayoutDoc.parse(JSONObject("""{"dock":["home","ambient/noite"],"pages":{"home":{"sections":[]},
            "ambient/noite":{"title":"Noite","sections":[{"type":"ambient","night":true,"color":"#ff0000",
            "faces":[{"type":"clock_analog","style":"bold","seconds":false},{"type":"sensor","entity":"sensor.t","label":"Sala"},{"type":"date"},{"type":"weather"}]}]}}}"""))
        val spec = AmbientSpec.of(doc, "ambient/noite")!!
        assertEquals("Noite", spec.title)
        assertTrue(spec.night)
        assertEquals(listOf("clock_analog", "sensor", "date"), spec.faces.map { it.type }) // no máximo 3
        assertEquals("bold", spec.faces[0].style)
        assertEquals(false, spec.faces[0].seconds)
        assertEquals("sensor.t", spec.faces[1].entity)
        assertEquals(listOf("ambient/noite" to "Noite"), AmbientSpec.all(doc))
        assertNull(AmbientSpec.of(doc, "home"))
    }
}
