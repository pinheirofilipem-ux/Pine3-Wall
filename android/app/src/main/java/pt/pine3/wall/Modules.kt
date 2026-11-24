package pt.pine3.wall

import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout

/** O que um módulo precisa para desenhar um cartão. */
class CardContext(val kit: Kit, val home: Home, val nav: Nav, val dense: Boolean, val short: Boolean = false, val room: Room? = null)

/**
 * Um módulo (schema/catalog.json): os tipos de secção e de cartão que sabe desenhar.
 * Novos módulos (calendário, tarefas, ecrãs compostos) entram aqui com os seus tipos.
 */
interface Module {
    val id: String
    val sectionTypes: Set<String>
    val cardTypes: Set<String>
    /** O cartão para [spec], ou null se não houver nada para mostrar (ex.: a divisão já não existe). */
    fun card(c: CardContext, spec: LCard): Card?
}

object ModuleRegistry {
    val modules: List<Module> = listOf(HomeModule, AmbientModule, CalendarModule)

    fun knowsSection(type: String) = modules.any { type in it.sectionTypes }
    fun knowsCard(type: String) = modules.any { type in it.cardTypes }

    /** Desenha um cartão do layout; um tipo que esta app não conhece dá um cartão a pedir para a atualizar. */
    fun card(c: CardContext, spec: LCard): Card? {
        val m = modules.firstOrNull { spec.type in it.cardTypes } ?: return UnknownCard(c.kit, spec.type)
        return m.card(c, spec)
    }
}

/** O módulo da casa: divisões, dispositivos, resumos, cenas, tempo e o ecrã de Início. */
object HomeModule : Module {
    override val id = "home"
    override val sectionTypes = setOf(
        "clock", "people", "status", "weather", "scenes", "grid", "room_header", "house3d", "scene_list", "climate_list", "security_list",
    )
    override val cardTypes = setOf("room", "device", "summary")
    val templates = setOf("classic", "top", "columns", "sidebar")

    override fun card(c: CardContext, spec: LCard): Card? = when (spec.type) {
        "room" -> c.home.rooms.firstOrNull { it.id == spec.room }?.let { r ->
            RoomCard(c.kit, r, c.dense, c.short, bigSize = spec.big) { c.nav.openRoom(r) }
        }
        "summary" -> spec.summary?.let { SummaryCard(c.kit, it, spec.entity, c.home, c.nav, c.dense) }
        "device" -> spec.entity?.let { id ->
            val d = (c.room?.devices ?: c.home.rooms.flatMap { it.devices }).firstOrNull { it.id == id } ?: devOf(id)
            when {
                d.kind == "climate" && Fmt.state(d.id) != null -> ClimateCard(c.kit, d, null) { c.nav.openDevice(it) }
                d.kind == "vacuum" && Fmt.state(d.id) != null -> VacCard(c.kit, d, c.room) { c.nav.openDevice(it) }
                else -> Tile(c.kit, d, bigSize = spec.big) { c.nav.openDevice(it) }
            }
        }
        else -> null
    }
}

/** Cartão de um tipo que esta versão da app não sabe desenhar (o layout veio de uma integração mais recente). */
class UnknownCard(kit: Kit, type: String) : Card(kit.ctx) {
    init {
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = kit.dp(26).toFloat(); setColor(Color.TRANSPARENT); setStroke(kit.dp(2), Color.argb(80, 255, 255, 255), kit.dp(8).toFloat(), kit.dp(6).toFloat())
        }
        val col = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(kit.dp(16), kit.dp(16), kit.dp(16), kit.dp(16)) }
        col.addView(kit.icon("mdi:update", kit.muted, 28), LinearLayout.LayoutParams(kit.dp(28), kit.dp(28)))
        col.addView(kit.text(Tr.s("Atualiza a app para ver este cartão", "Update the app to see this card"), 14f, kit.muted, 600).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(8) })
        col.addView(kit.text(type, 12f, kit.muted).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(4) })
        addView(col, LayoutParams(-1, -1, Gravity.CENTER))
    }
    override fun update() {}
}
