package pt.pine3.wall

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A app lê o mesmo exemplo que os testes da integração validam contra schema/home.schema.json. */
class ContractTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "schema/home.schema.json").exists() }
    private fun example() = JSONObject(File(root, "schema/examples/home.json").readText())
    private fun integrationConst(name: String): String =
        Regex("""^$name = "?([^"\s]+)"?""", RegexOption.MULTILINE).find(File(root, "custom_components/pine3_wall/const.py").readText())!!.groupValues[1]

    @Test fun parsesTheExampleHome() {
        val home = Home.parse(example())
        assertTrue(home.rooms.isNotEmpty())
        val all = home.rooms.flatMap { it.devices }
        assertTrue(all.any { it.kind == "outlet" && it.extras.isNotEmpty() })
        assertTrue(home.rooms.all { r -> r.icon.startsWith("mdi:") })
        assertEquals(example().getJSONObject("settings").getString("theme"), home.settings.getString("theme"))
        assertEquals(Compat.API_VERSION, home.apiVersion)
        assertNotNull(home.minAppVersion)
    }

    @Test fun apiVersionMatchesTheIntegration() {
        assertEquals(integrationConst("API_VERSION").toInt(), Compat.API_VERSION)
    }

    @Test fun thisAppIsAcceptedByTheIntegration() {
        val appVersion = File(root, "VERSION").readText().trim()
        assertNull(Compat.problem(Compat.API_VERSION, integrationConst("MIN_APP_VERSION"), appVersion))
    }

    @Test fun compatDetectsMismatches() {
        assertNull(Compat.problem(1, "0.1.0", "0.1.0"))
        assertNull(Compat.problem(1, "0.1.0", "0.10.0"))
        assertNotNull(Compat.problem(0, null, "0.1.0"))          // integração anterior ao contrato
        assertNotNull(Compat.problem(Compat.API_VERSION + 1, null, "9.9.9")) // contrato mais novo do que a app
        assertNotNull(Compat.problem(1, "0.2.0", "0.1.9"))       // app abaixo da mínima
        assertTrue(Compat.compare("0.10.0", "0.9.9") > 0)
        assertEquals(0, Compat.compare("1.2", "1.2.0"))
    }

    @Test fun everyThemeFromTokensIsGenerated() {
        val tokens = JSONObject(File(root, "schema/tokens.json").readText())
        val keys = (0 until tokens.getJSONArray("themes").length()).map { tokens.getJSONArray("themes").getJSONObject(it).getString("key") }
        assertEquals(keys, Themes.all.map { it.key })
        assertEquals(tokens.getJSONArray("accents").length(), ACCENTS.size)
    }

    @Test fun parsesThePeople() {
        val people = Home.parse(example()).people
        assertEquals(listOf("person.ana", "person.pedro"), people.map { it.id })
        val pedro = people[1]
        assertEquals("Pedro", pedro.alias)
        assertEquals("/api/image/pedro.jpg", pedro.picture)
        assertNull(people[0].picture) // "picture": null não pode virar o texto "null"
        val tokens = JSONObject(File(root, "schema/tokens.json").readText()).getJSONArray("person_colors")
        assertEquals((0 until tokens.length()).map { tokens.getString(it) }, PERSON_COLORS)
        assertEquals(PERSON_COLORS[0], people[0].color)
    }
}
