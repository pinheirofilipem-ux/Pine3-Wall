package pt.pine3.wall

import org.json.JSONArray
import org.json.JSONObject

/**
 * O documento de layout que a integração envia no modelo da casa (schema/layout.schema.json):
 * o que cada ecrã mostra, por que ordem e com que tamanho. Tipos desconhecidos são mantidos (o renderer mostra um aviso).
 */
class LayoutDoc(val generated: Boolean, val display: String, val clock24h: Boolean, val animations: Boolean, val dock: List<String>, val pages: Map<String, LPage>) {
    fun page(key: String): LPage? = pages[key]

    companion object {
        private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }

        fun parse(o: JSONObject): LayoutDoc {
            val opts = o.optJSONObject("options") ?: JSONObject()
            val pages = LinkedHashMap<String, LPage>()
            o.optJSONObject("pages")?.let { p -> p.keys().forEach { k -> p.optJSONObject(k)?.let { pages[k] = LPage.parse(it) } } }
            return LayoutDoc(
                o.optBoolean("generated", true), opts.optString("display", "grid"), opts.optBoolean("clock_24h", true),
                opts.optBoolean("animations", true), strings(o.optJSONArray("dock")).ifEmpty { listOf("home") }, pages,
            )
        }
    }
}

class LPage(val template: String?, val room: String?, val sections: List<LSection>, val title: String? = null, val raw: JSONObject? = null) {
    fun has(type: String) = sections.any { it.type == type }
    fun section(type: String) = sections.firstOrNull { it.type == type }

    companion object {
        fun parse(o: JSONObject) = LPage(
            o.optString("template", "").ifBlank { null }, o.optString("room", "").ifBlank { null },
            o.optJSONArray("sections")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(LSection::parse) } } ?: emptyList(),
            o.optString("title", "").ifBlank { null }, o,
        )
    }
}

class LSection(val type: String, val items: List<String>, val room: String?, val group: String?, val cards: List<LCard>) {
    companion object {
        fun parse(o: JSONObject) = LSection(
            o.optString("type"),
            o.optJSONArray("items")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
            o.optString("room", "").ifBlank { null }, o.optString("group", "").ifBlank { null },
            o.optJSONArray("cards")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(LCard::parse) } } ?: emptyList(),
        )
    }
}

class LCard(val type: String, val size: String, val room: String?, val entity: String?, val summary: String?, val person: String? = null, val count: Int = 0) {
    val big get() = size == "big"
    /** A mesma chave que o modo de edição usa para ordenar (id da divisão, do dispositivo, "@climate", "@vacuum:<id>"). */
    val key: String get() = when (type) {
        "room" -> room.orEmpty()
        "device" -> entity.orEmpty()
        "summary" -> if (summary == "vacuum") "@vacuum:${entity.orEmpty()}" else "@${summary.orEmpty()}"
        "upcoming" -> "@upcoming:${person.orEmpty()}"
        else -> "?$type"
    }

    companion object {
        fun parse(o: JSONObject) = LCard(
            o.optString("type"), o.optString("size", "normal"), o.optString("room", "").ifBlank { null },
            o.optString("entity", "").ifBlank { null }, o.optString("summary", "").ifBlank { null },
            o.optString("person", "").ifBlank { null }, o.optInt("count", 0),
        )
    }
}
