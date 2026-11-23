package pt.pine3.wall

/**
 * Compatibilidade entre a app e a integração (schema/home.schema.json).
 * A integração envia `api_version` (versão do contrato) e `min_app_version` (app mais antiga que a entende).
 */
object Compat {
    /** Versão do contrato que esta app entende (igual a API_VERSION em custom_components/pine3_wall/const.py). */
    const val API_VERSION = 1

    /** Compara versões X.Y.Z (partes em falta ou inválidas contam como 0). */
    fun compare(a: String, b: String): Int {
        fun parts(v: String) = v.trim().split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val pa = parts(a); val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    /** null se a app e a integração se entendem; senão, o que fazer (texto para o utilizador). Sem layout = integração anterior à 0.3.0. */
    fun problem(serverApi: Int, minAppVersion: String?, appVersion: String, hasLayout: Boolean = true): String? = when {
        serverApi <= 0 || !hasLayout -> Tr.s(
            "A integração Pine3 Wall no Home Assistant é antiga para esta app. Atualiza-a no HACS e reinicia o HA.",
            "The Pine3 Wall integration in Home Assistant is too old for this app. Update it in HACS and restart HA.",
        )
        serverApi > API_VERSION || (minAppVersion != null && compare(appVersion, minAppVersion) < 0) -> Tr.s(
            "Esta app ($appVersion) é antiga para a integração Pine3 Wall. Instala a versão ${minAppVersion ?: "mais recente"} ou mais recente.",
            "This app ($appVersion) is too old for the Pine3 Wall integration. Install version ${minAppVersion ?: "latest"} or newer.",
        )
        serverApi < API_VERSION -> Tr.s(
            "A integração Pine3 Wall no Home Assistant é antiga para esta app. Atualiza-a no HACS e reinicia o HA.",
            "The Pine3 Wall integration in Home Assistant is too old for this app. Update it in HACS and restart HA.",
        )
        else -> null
    }
}
