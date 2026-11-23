package pt.pine3.wall

import org.json.JSONObject
import java.net.InetAddress

/** Teste de ligação passo a passo, para ver exatamente onde falha (rede, endereço, token ou Pine3 Wall). */
object HaTest {
    fun run(url: String?, token: String, trustAll: Boolean, appVersion: String, line: (ok: Boolean, text: String) -> Unit, done: () -> Unit) {
        Thread {
            try {
                if (url == null) { line(false, Tr.s("Falta o endereço do Home Assistant.", "The Home Assistant address is missing.")); return@Thread }
                val uri = java.net.URI(url)
                val host = uri.host ?: run { line(false, Tr.s("Endereço inválido: ", "Invalid address: ") + url); return@Thread }
                line(true, Tr.s("Endereço: ", "Address: ") + url)
                val ip = try { InetAddress.getByName(host).hostAddress } catch (e: Exception) {
                    line(false, Ha.describe(e)); return@Thread
                }
                line(true, "1/5  " + Tr.s("DNS ok", "DNS ok") + " ($ip)")
                val ws = MiniWebSocket(url, trustAll, readTimeoutMs = 10_000)
                try {
                    try { ws.connect(6000) } catch (e: Exception) { line(false, "2/5  ${Ha.describe(e)}"); return@Thread }
                    line(true, "2/5  " + Tr.s("WebSocket aberto", "WebSocket open"))
                    var m = JSONObject(ws.receive() ?: run { line(false, Tr.s("O HA fechou a ligação", "HA closed the connection")); return@Thread })
                    if (m.optString("type") != "auth_required") { line(false, Tr.s("Resposta inesperada: ", "Unexpected reply: ") + m.optString("type")); return@Thread }
                    ws.send(JSONObject().put("type", "auth").put("access_token", token).toString())
                    m = JSONObject(ws.receive() ?: run { line(false, "O HA fechou a ligação"); return@Thread })
                    if (m.optString("type") != "auth_ok") { line(false, "3/5  " + Tr.s("Token recusado. Cria um novo token de acesso de longa duração no teu perfil do HA.", "Token rejected. Create a new long-lived access token in your HA profile.")); return@Thread }
                    line(true, "3/5  " + Tr.s("Token aceite", "Token accepted") + " (HA ${m.optString("ha_version")})")
                    ws.send(JSONObject().put("id", 1).put("type", "get_states").toString())
                    val st = JSONObject(ws.receive() ?: run { line(false, "O HA fechou a ligação"); return@Thread })
                    line(st.optBoolean("success"), "4/5  " + Tr.s("Entidades: ", "Entities: ") + (st.optJSONArray("result")?.length() ?: 0))
                    ws.send(JSONObject().put("id", 2).put("type", "pine3_wall/home").toString())
                    val h = JSONObject(ws.receive() ?: run { line(false, "O HA fechou a ligação"); return@Thread })
                    if (h.optBoolean("success")) {
                        val res = h.optJSONObject("result") ?: JSONObject()
                        val rooms = res.optJSONArray("rooms")?.length() ?: 0
                        val ver = res.optString("version", "?")
                        line(true, "5/5  " + Tr.s("Pine3 Wall $ver respondeu: $rooms divisões", "Pine3 Wall $ver answered: $rooms rooms"))
                        Compat.problem(res.optInt("api_version", 0), res.optString("min_app_version", "").ifBlank { null }, appVersion, res.has("layout"))?.let { line(false, it) }
                    } else {
                        line(false, "5/5  " + Tr.s("O Pine3 Wall não respondeu", "Pine3 Wall did not answer") + " (${h.optJSONObject("error")?.optString("code")}). " + Tr.s("Está instalado no HA e o HA foi reiniciado?", "Is it installed in HA, and was HA restarted?"))
                    }
                } finally { ws.close() }
            } catch (e: Exception) {
                line(false, Ha.describe(e))
            } finally { done() }
        }.start()
    }
}
