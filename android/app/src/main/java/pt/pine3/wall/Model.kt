package pt.pine3.wall

import org.json.JSONArray
import org.json.JSONObject

/** Um dispositivo (um cartão) de uma divisão, como o modelo do Pine3 Wall o descreve. */
class Dev(val id: String, val name: String, val kind: String, val extras: List<String>, val members: List<String>, val hidden: Boolean)

/** Uma entidade de uma divisão, para escolher o que aparece (definições > Entidades). */
class RoomEntity(val id: String, val name: String, val primary: Boolean, val category: Boolean, val haHidden: Boolean)

/** Uma pessoa da família (person.*), com a cor, o nome curto e a foto partilhados pelos módulos. */
class Person(val id: String, val name: String, val alias: String, val color: String, val picture: String?, val hidden: Boolean)

class Room(
    val id: String, val name: String, val icon: String, val floor: String?,
    val devices: List<Dev>, val temperature: List<String>, val humidity: List<String>,
    val scenes: List<String>, val hidden: Boolean, val entities: List<RoomEntity> = emptyList(), val floorLevel: Int = 0,
)

/** O modelo `pine3_wall/home`: divisões, dispositivos, cenas, tempo, pessoas e as definições guardadas no HA. */
class Home(
    val rooms: List<Room>, val scenes: List<String>, val scripts: List<String>,
    val weather: String?, val persons: List<String>, val settings: JSONObject,
    /** Versão do contrato e app mínima (schema/home.schema.json); 0 / null numa integração anterior ao contrato. */
    val apiVersion: Int = 0, val minAppVersion: String? = null, val version: String? = null,
    /** O layout de todos os ecrãs (schema/layout.schema.json); null numa integração anterior à 0.3.0. */
    val layout: LayoutDoc? = null,
    /** A família (definições > Pessoas); vazia numa integração anterior à 0.6.0. */
    val people: List<Person> = emptyList(),
    /** De onde vem o layout: "auto" (definições), "published" (editor web) ou "preview" (o rascunho em pré-visualização). */
    val layoutSource: String = "auto",
    /** Módulo calendar: os eventos do calendário da família; null se não houver calendário escolhido (ou integração < 0.10.0). */
    val calendar: CalendarData? = null,
) {
    fun color(key: String, fallback: Int): Int = try {
        android.graphics.Color.parseColor(settings.optString(key, "").ifBlank { return fallback })
    } catch (_: Exception) { fallback }

    /** Todas as entidades que mostramos (para só reagir a mudanças relevantes). */
    fun watched(): Set<String> {
        val out = HashSet<String>()
        rooms.forEach { r ->
            out += r.temperature; out += r.humidity; out += r.scenes
            r.devices.forEach { d -> out += d.id; out += d.extras; out += d.members }
        }
        out += scenes; out += scripts; out += persons
        weather?.let { out += it }
        out += "sun.sun"
        return out
    }

    companion object {
        private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

        fun parse(o: JSONObject): Home {
            val rooms = o.optJSONArray("rooms")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val r = arr.getJSONObject(i)
                    Room(
                        id = r.optString("id"), name = r.optString("name"), icon = r.optString("icon", "mdi:home-outline"),
                        floor = if (r.isNull("floor")) null else r.optString("floor"),
                        devices = r.optJSONArray("devices")?.let { d ->
                            (0 until d.length()).map { j ->
                                val x = d.getJSONObject(j)
                                Dev(
                                    x.optString("id"), x.optString("name"), x.optString("kind"),
                                    strings(x.optJSONArray("extras")), strings(x.optJSONArray("members")), x.optBoolean("hidden"),
                                )
                            }
                        } ?: emptyList(),
                        temperature = strings(r.optJSONArray("temperature")), humidity = strings(r.optJSONArray("humidity")),
                        scenes = strings(r.optJSONArray("scenes")), hidden = r.optBoolean("hidden"),
                        entities = r.optJSONArray("entities")?.let { e ->
                            (0 until e.length()).map { k -> e.getJSONObject(k).let { RoomEntity(it.optString("id"), it.optString("name"), it.optBoolean("primary"), it.optBoolean("category"), it.optBoolean("ha_hidden")) } }
                        } ?: emptyList(),
                        floorLevel = r.optInt("floor_level", 0),
                    )
                }
            } ?: emptyList()
            return Home(
                rooms, strings(o.optJSONArray("scenes")), strings(o.optJSONArray("scripts")),
                if (o.isNull("weather")) null else o.optString("weather"), strings(o.optJSONArray("persons")),
                o.optJSONObject("settings") ?: JSONObject(),
                apiVersion = o.optInt("api_version", 0),
                minAppVersion = o.optString("min_app_version", "").ifBlank { null },
                version = o.optString("version", "").ifBlank { null },
                layout = o.optJSONObject("layout")?.let { LayoutDoc.parse(it) },
                layoutSource = o.optString("layout_source", "auto"),
                calendar = o.optJSONObject("calendar")?.let { CalendarData.parse(it) },
                people = o.optJSONArray("people")?.let { a ->
                    (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { p ->
                        Person(p.optString("id"), p.optString("name"), p.optString("alias").ifBlank { p.optString("name") },
                            p.optString("color", "#F3A96B"), p.optString("picture", "").ifBlank { null }.takeIf { !p.isNull("picture") }, p.optBoolean("hidden"))
                    }
                } ?: emptyList(),
            )
        }
    }
}
