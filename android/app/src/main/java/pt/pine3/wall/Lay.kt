package pt.pine3.wall

import org.json.JSONArray
import org.json.JSONObject

/**
 * Ordem, esconder e tamanho duplo de divisões e dispositivos, mais o modo de edição com cópia de trabalho.
 * Com o layout automático, a edição grava nas definições (as mesmas do painel web); com um layout publicado no editor web,
 * grava nesse layout (pine3_wall/layout/edit) e as definições não mudam.
 */
object Lay {
    class Edit(
        val roomOrder: MutableList<String>, val hiddenRooms: MutableList<String>, val bigRooms: MutableList<String>,
        val hidden: MutableList<String>, val bigDevices: MutableList<String>, val deviceOrder: MutableMap<String, MutableList<String>>,
        /** A edição é sobre um layout publicado (e não sobre as definições). */
        val custom: Boolean = false,
    )

    var edit: Edit? = null

    /** Edição gravada num layout publicado, até o HA mandar o layout novo: as páginas desenham-se a partir dela. */
    @Volatile var pending: Edit? = null
    private val src: Edit? get() = edit ?: pending
    var version = 0

    /**
     * Gravou-se uma edição e o HA ainda não mandou o layout atualizado: até lá as páginas desenham-se a partir das definições
     * (já atualizadas localmente), para a mudança aparecer logo. Volta a false quando chega o modelo novo (Ha.setHome).
     */
    @Volatile var awaitingLayout = false

    /** As páginas desenham-se a partir do layout do HA (fora do modo de edição e sem edição gravada à espera do HA). */
    val useLayout: Boolean get() = edit == null && !awaitingLayout
    var onChange: (() -> Unit)? = null
    fun changed() { version++; onChange?.invoke() }

    private fun list(s: JSONObject, k: String): MutableList<String> = (0 until (s.optJSONArray(k)?.length() ?: 0)).map { s.optJSONArray(k)!!.getString(it) }.toMutableList()

    /** A cópia de trabalho a partir de um layout publicado: o que está nas grelhas, por ordem, e o que falta (escondido). */
    fun fromLayout(home: Home, layout: LayoutDoc): Edit {
        val homeGrid = layout.page("home")?.section("grid")?.cards.orEmpty()
        val roomsInGrid = homeGrid.filter { it.type == "room" }.mapNotNull { it.room }
        val order = (homeGrid.map { it.key } + home.rooms.map { it.id }).distinct().toMutableList()
        val hiddenRooms = home.rooms.map { it.id }.filter { it !in roomsInGrid }.toMutableList()
        val bigRooms = homeGrid.filter { it.type == "room" && it.big }.mapNotNull { it.room }.toMutableList()
        val hidden = ArrayList<String>(); val bigDevices = ArrayList<String>(); val dord = HashMap<String, MutableList<String>>()
        home.rooms.forEach { r ->
            val cards = layout.page("room/${r.id}")?.section("grid")?.cards.orEmpty().filter { it.type == "device" }
            val ids = cards.mapNotNull { it.entity }
            dord[r.id] = ids.toMutableList()
            hidden += r.devices.map { it.id }.filter { it !in ids }
            bigDevices += cards.filter { it.big }.mapNotNull { it.entity }
        }
        return Edit(order, hiddenRooms, bigRooms, hidden, bigDevices, dord, custom = true)
    }

    fun begin(home: Home) {
        home.layout?.takeIf { !it.generated }?.let { edit = fromLayout(home, it); version++; return }
        val s = home.settings
        val order = list(s, "room_order").ifEmpty { home.rooms.map { it.id }.toMutableList() }
        val dord = HashMap<String, MutableList<String>>()
        s.optJSONObject("device_order")?.let { o -> o.keys().forEach { k -> dord[k] = list(o, k) } }
        edit = Edit(order, list(s, "hidden_rooms"), list(s, "big_rooms"), list(s, "hidden"), list(s, "big_devices"), dord)
        version++
    }

    fun finish(home: Home?, save: Boolean) {
        val e = edit ?: return
        edit = null
        if (save && home != null) {
            awaitingLayout = true
            val dord = JSONObject(); e.deviceOrder.forEach { (k, v) -> dord.put(k, JSONArray(v)) }
            val changes = JSONObject().put("room_order", JSONArray(e.roomOrder)).put("hidden_rooms", JSONArray(e.hiddenRooms)).put("big_rooms", JSONArray(e.bigRooms))
                .put("hidden", JSONArray(e.hidden)).put("big_devices", JSONArray(e.bigDevices)).put("device_order", dord)
            if (e.custom) {
                pending = e
                Ha.request(changes.put("type", "pine3_wall/layout/edit")) { if (it == null) { pending = null; awaitingLayout = false; changed() } }
            } else Ha.saveSettings(changes)
        }
        version++
    }

    private fun hiddenRooms(home: Home) = src?.hiddenRooms ?: list(home.settings, "hidden_rooms")
    private fun hiddenDevs(home: Home) = src?.hidden ?: list(home.settings, "hidden")
    fun roomOrder(home: Home): List<String> = src?.roomOrder ?: list(home.settings, "room_order")

    /** Divisões visíveis, pela ordem escolhida. */
    fun rooms(home: Home): List<Room> {
        val hide = hiddenRooms(home); val order = roomOrder(home)
        return home.rooms.filter { it.id !in hide }.sortedBy { r -> order.indexOf(r.id).let { if (it < 0) 10_000 else it } }
    }

    fun hiddenRoomCount(home: Home) = home.rooms.count { it.id in hiddenRooms(home) }

    /** Dispositivos visíveis de uma divisão, pela ordem escolhida. */
    fun devs(home: Home, room: Room): List<Dev> {
        val hide = hiddenDevs(home)
        val ord = src?.deviceOrder?.get(room.id) ?: home.settings.optJSONObject("device_order")?.let { list(it, room.id) } ?: emptyList()
        return room.devices.filter { it.id !in hide }.sortedBy { d -> ord.indexOf(d.id).let { if (it < 0) 10_000 else it } }
    }

    fun hiddenDevCount(home: Home, room: Room) = room.devices.count { it.id in hiddenDevs(home) }

    fun isBigRoom(home: Home, id: String) = id in (src?.bigRooms ?: list(home.settings, "big_rooms"))
    fun isBigDev(home: Home, id: String) = id in (src?.bigDevices ?: list(home.settings, "big_devices"))

    fun hide(kind: String, key: String) { val e = edit ?: return; if (kind == "room") { if (key !in e.hiddenRooms) e.hiddenRooms += key } else if (key !in e.hidden) e.hidden += key; changed() }
    fun toggleBig(kind: String, key: String) { val e = edit ?: return; val l = if (kind == "room") e.bigRooms else e.bigDevices; if (!l.remove(key)) l += key; changed() }
    fun restoreRooms() { edit?.hiddenRooms?.clear(); changed() }
    fun restoreDevs(room: Room) { val e = edit ?: return; e.hidden.removeAll(room.devices.map { it.id }.toSet()); changed() }

    /** A grelha de divisões foi reordenada: as chaves visíveis ficam à frente, o resto mantém a ordem. */
    fun reorderRooms(keys: List<String>) { val e = edit ?: return; val rest = e.roomOrder.filter { it !in keys }; e.roomOrder.clear(); e.roomOrder.addAll(keys + rest); changed() }

    fun reorderDevs(room: Room, keys: List<String>) {
        val e = edit ?: return
        val full = room.devices.map { it.id }
        val rest = full.filter { it !in keys }
        e.deviceOrder[room.id] = (keys + rest).toMutableList()
        changed()
    }
}
