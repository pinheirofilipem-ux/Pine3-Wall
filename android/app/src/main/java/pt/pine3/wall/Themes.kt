package pt.pine3.wall

/** Os temas vêm de schema/tokens.json (ThemeTokens.kt é gerado por schema/tools/gen_tokens.py). */
class Glow(val rx: Float, val ry: Float, val cx: Float, val cy: Float, val color: Int, val stop: Float)
class LinearBg(val angle: Float, val colors: IntArray, val pos: FloatArray)
/** [group]: "dark", "ambient", "vivid" ou "light" (secções da escolha do fundo). */
class Theme(val key: String, val name: String, val light: Boolean, val glows: List<Glow>, val base: LinearBg, val group: String = "dark")

object Themes {
    val all: List<Theme> = THEME_TOKENS

    fun get(key: String?): Theme = all.firstOrNull { it.key == key } ?: all[0]

    /** O tema em uso: o fixo, ou o de dia/noite conforme o sol (sun.sun; sem ele, das 7h às 19h). */
    fun pick(settings: org.json.JSONObject, sunState: String?, hour: Int): Theme {
        if (settings.optString("appearance", "fixed") == "auto") {
            val day = if (sunState != null) sunState == "above_horizon" else hour in 7..18
            return get(settings.optString(if (day) "theme_day" else "theme_night", if (day) "linho" else "ameixa"))
        }
        return get(settings.optString("theme", "ameixa"))
    }
}
