package pt.pine3.wall

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Uma linha do dia de uma pessoa: um evento (com hora) ou uma tarefa (com o momento do dia). */
class FamilyItem(val kind: String, val title: String, val label: String, val icon: String, val sort: String, val hot: Boolean, val past: Boolean)

/** O dia da família: para cada pessoa os eventos (módulo calendar) e as tarefas (módulo chores) do dia, por hora. */
class FamilyDay(val columns: List<Pair<Person, List<FamilyItem>>>, val family: List<FamilyItem>) {
    companion object {
        private val MOMENT_AT = mapOf("day" to "00:00", "morning" to "08:00", "afternoon" to "14:00", "evening" to "19:30")

        fun of(home: Home, day: LocalDate = LocalDate.now(), now: LocalDateTime = LocalDateTime.now()): FamilyDay {
            val d = day.toString()
            val today = day == now.toLocalDate()
            val nowKey = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
            val moment = if (today) home.chores?.momentNow(now) else null
            val events = home.calendar?.on(d).orEmpty()
            val chores = home.chores?.forDay(ChoresData.today(day)).orEmpty()
            fun ev(e: CalEvent) = FamilyItem(
                "event", e.label,
                if (e.allDay) Tr.t("Todo o dia") else if (e.day < d) "…" else e.time, "mdi:calendar-blank-outline",
                if (e.allDay) "00:00" else if (e.day < d) "00:01" else e.time, false, today && !e.allDay && e.end < nowKey,
            )
            fun ch(t: Chore) = FamilyItem(
                "chore", t.name, Tr.t(ChoresPage.MOMENT_PT[t.moment] ?: "Todo o dia"), t.icon,
                (MOMENT_AT[t.moment] ?: "00:00") + "~", moment != null && t.moment != "day" && t.moment == moment, false,
            )
            val people = home.people.filter { !it.hidden }
            val cols = people.map { p ->
                p to (events.filter { p.id in it.people }.map(::ev) + chores.filter { p.id in it.second }.map { ch(it.first) }).sortedBy { it.sort }
            }
            val family = events.filter { e -> e.people.isEmpty() || e.people.none { id -> people.any { it.id == id } } }.map(::ev).sortedBy { it.sort }
            return FamilyDay(cols, family)
        }
    }
}

/** Página "O dia da família" (dock "family"): uma coluna por pessoa, e a da família se houver eventos de todos; Hoje / Amanhã. */
class FamilyPage(kit: Kit, private val home: Home) : Page(kit) {
    private var offset = 0L
    private var sig = ""
    private val root = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(kit.dp(40), kit.dp(30), kit.dp(40), dockSpace(kit)) }
    override val view: View = root

    init { update() }

    override fun update() {
        val s = "${LocalDate.now()}|${home.chores?.momentNow()}|$offset|${LocalDateTime.now().hour}"
        if (s == sig) return
        sig = s
        root.removeAllViews()
        val day = LocalDate.now().plusDays(offset)
        val hd = LinearLayout(kit.ctx).apply { gravity = Gravity.BOTTOM }
        val titles = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(kit.text(day.format(DateTimeFormatter.ofPattern(if (Tr.en) "EEEE d MMMM" else "EEEE, d 'de' MMMM", Tr.locale)).uppercase(), 13f, kit.muted, 600, spacing = 0.2f))
        titles.addView(kit.text(Tr.s("O dia da família", "The family's day"), kit.clamp(30f, kit.vw(4f), 44f), kit.fg, 400, display = true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(6) })
        hd.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        listOf(0L to Tr.t("Hoje"), 1L to Tr.s("Amanhã", "Tomorrow")).forEach { (k, l) ->
            hd.addView(kit.pill(l, null, offset == k) { offset = k; update() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = kit.dp(8) })
        }
        root.addView(hd)
        if (home.calendar == null && home.chores == null) {
            root.addView(kit.text(Tr.s("Junta o calendário e as tarefas de cada pessoa. Escolhe o calendário e cria as tarefas nas definições do painel web.",
                "It joins each person's calendar and chores. Choose the calendar and create the chores in the web panel's settings."), 17f, kit.muted),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(16) })
            return
        }
        val fd = FamilyDay.of(home, day)
        val famColor = CalendarModule.color(kit, home.calendar, null)
        val cols = fd.columns.map { (p, items) -> Triple(p.alias, ChoresModule.color(kit, p), items) to p } +
            (if (fd.family.isNotEmpty()) listOf(Triple(Tr.t("Família"), famColor, fd.family) to null) else emptyList())
        val row = LinearLayout(kit.ctx)
        val minW = kit.dp(220)
        cols.forEachIndexed { i, (c, person) ->
            val (name, color, items) = c
            val col = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL; background = kit.glass(24); setPadding(kit.dp(16), kit.dp(16), kit.dp(16), kit.dp(16)) }
            val h = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
            val atHome = person?.let { Fmt.state(it.id)?.state == "home" }
            h.addView(FrameLayout(kit.ctx).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color); if (atHome == true) setStroke(kit.dp(2), Color.parseColor("#34C759")) }
                alpha = if (atHome == false) 0.6f else 1f
                addView(kit.text(name.take(1).uppercase(), 18f, kit.ink, 600), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            }, LinearLayout.LayoutParams(kit.dp(46), kit.dp(46)))
            h.addView(kit.text(name, 22f, kit.fg, 500, display = true).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = kit.dp(12) })
            col.addView(h, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = kit.dp(6) })
            items.forEach { col.addView(itemRow(it), LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(8) }) }
            if (items.isEmpty()) col.addView(kit.text(Tr.s("Dia livre", "Free day"), 15f, kit.muted), LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(10) })
            row.addView(ScrollView(kit.ctx).apply { isVerticalScrollBarEnabled = false; addView(col, FrameLayout.LayoutParams(-1, -2)) },
                LinearLayout.LayoutParams(0, -1, 1f).apply { if (i > 0) marginStart = kit.dp(14) })
        }
        val wide = cols.size * (minW + kit.dp(14)) > kit.dp(kit.wdp) - kit.dp(80)
        val body: View = if (wide) HorizontalScrollView(kit.ctx).apply {
            isHorizontalScrollBarEnabled = false
            row.layoutParams = FrameLayout.LayoutParams(cols.size * (minW + kit.dp(14)), -1)
            addView(row)
        } else row
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = kit.dp(18) })
    }

    private fun itemRow(it: FamilyItem): View = LinearLayout(kit.ctx).apply {
        gravity = Gravity.CENTER_VERTICAL
        val fg = if (it.hot) kit.ink else kit.fg
        background = when {
            it.hot -> kit.rounded(kit.accent, 14)
            it.kind == "chore" -> GradientDrawable().apply { cornerRadius = kit.dp(14).toFloat(); setColor(Color.argb(8, 255, 255, 255)); setStroke(kit.dp(1), Color.argb(50, 255, 255, 255), kit.dp(5).toFloat(), kit.dp(4).toFloat()) }
            else -> kit.rounded(Color.argb(16, 255, 255, 255), 14)
        }
        alpha = if (it.past) 0.45f else 1f
        setPadding(kit.dp(10), kit.dp(9), kit.dp(10), kit.dp(9))
        addView(kit.text(it.label, 13f, if (it.hot) kit.ink else kit.muted).apply { setSingleLine() }, LinearLayout.LayoutParams(kit.dp(64), -2))
        addView(kit.icon(it.icon, fg, 20))
        addView(kit.text(it.title, 15f, fg, if (it.hot) 600 else 500).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = kit.dp(8) })
    }
}

/** O módulo do dia da família no registo de módulos: a secção "family_day" (página própria). */
object FamilyModule : Module {
    override val id = "family"
    override val sectionTypes = setOf("family_day")
    override val cardTypes = emptySet<String>()
    override fun card(c: CardContext, spec: LCard): Card? = null
}
