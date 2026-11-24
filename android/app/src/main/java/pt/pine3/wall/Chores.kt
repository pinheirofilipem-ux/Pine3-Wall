package pt.pine3.wall

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle

/** Uma tarefa da casa (schema/home.schema.json, "chore"). */
class Chore(val id: String, val name: String, val icon: String, val moment: String)

/** O módulo chores no modelo: as tarefas e quem as faz em cada dia (segunda = 0). Só a lista: não há "feito". */
class ChoresData(val tasks: List<Chore>, val schedule: Map<String, List<List<String>>>, val moments: Map<String, Pair<Int, Int>>) {
    fun people(task: String, weekday: Int): List<String> = schedule[task]?.getOrNull(weekday).orEmpty()

    /** As tarefas do dia [weekday] com as pessoas, pela ordem da lista (sem as que ninguém faz). */
    fun forDay(weekday: Int): List<Pair<Chore, List<String>>> = tasks.map { it to people(it.id, weekday) }.filter { it.second.isNotEmpty() }

    fun ofPerson(person: String, weekday: Int): List<Chore> = forDay(weekday).filter { person in it.second }.map { it.first }

    /** O momento do dia agora ("morning", "afternoon" ou "evening"), com as horas da integração. */
    fun momentNow(now: LocalDateTime = LocalDateTime.now()): String =
        moments.entries.firstOrNull { (_, h) -> now.hour >= h.first && now.hour < h.second }?.key ?: "evening"

    companion object {
        const val DEFAULT_ICON = "mdi:checkbox-marked-circle-outline"
        fun today(d: LocalDate = LocalDate.now()) = d.dayOfWeek.value - 1

        fun parse(o: JSONObject): ChoresData {
            val tasks = o.optJSONArray("tasks")?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { t ->
                    Chore(t.optString("id"), t.optString("name"), t.optString("icon", "").ifBlank { DEFAULT_ICON }, t.optString("moment", "day"))
                }
            }.orEmpty()
            val schedule = HashMap<String, List<List<String>>>()
            o.optJSONObject("schedule")?.let { s ->
                s.keys().forEach { k ->
                    val days = s.optJSONArray(k) ?: return@forEach
                    schedule[k] = (0 until 7).map { i -> days.optJSONArray(i)?.let { p -> (0 until p.length()).map { p.optString(it) } }.orEmpty() }
                }
            }
            val moments = LinkedHashMap<String, Pair<Int, Int>>()
            (o.optJSONObject("moments") ?: JSONObject("""{"morning":[5,12],"afternoon":[12,19],"evening":[19,24]}""")).let { m ->
                m.keys().forEach { k -> m.optJSONArray(k)?.let { if (it.length() == 2) moments[k] = it.optInt(0) to it.optInt(1) } }
            }
            return ChoresData(tasks, schedule, moments)
        }
    }
}

/** O módulo chores no registo de módulos: a secção "chores_week" (página própria) e o cartão "chores_today". */
object ChoresModule : Module {
    override val id = "chores"
    override val sectionTypes = setOf("chores_week")
    override val cardTypes = setOf("chores_today")
    override fun card(c: CardContext, spec: LCard): Card? = if (spec.type == "chores_today") ChoresCard(c.kit, spec, c.nav) else null

    fun color(kit: Kit, p: Person): Int = try { Color.parseColor(p.color) } catch (_: Exception) { kit.accent }
}

/** Filhos lado a lado que passam para a linha seguinte quando não cabem (as pílulas das tarefas). */
class Flow(ctx: Context, private val gapX: Int, private val gapY: Int) : ViewGroup(ctx) {
    override fun onMeasure(w: Int, h: Int) {
        val maxW = MeasureSpec.getSize(w)
        var x = 0; var y = 0; var row = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            c.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            if (x > 0 && x + c.measuredWidth > maxW) { x = 0; y += row + gapY; row = 0 }
            x += c.measuredWidth + gapX; row = maxOf(row, c.measuredHeight)
        }
        setMeasuredDimension(maxW, y + row)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l
        var x = 0; var y = 0; var row = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (x > 0 && x + c.measuredWidth > maxW) { x = 0; y += row + gapY; row = 0 }
            c.layout(x, y, x + c.measuredWidth, y + c.measuredHeight)
            x += c.measuredWidth + gapX; row = maxOf(row, c.measuredHeight)
        }
    }
}

/** Pílula de uma tarefa; realçada quando é do momento do dia em que estamos. */
private fun taskChip(kit: Kit, t: Chore, now: String, small: Boolean = false): View = LinearLayout(kit.ctx).apply {
    val hot = t.moment != "day" && t.moment == now
    val fg = if (hot) kit.ink else kit.fg
    gravity = Gravity.CENTER_VERTICAL
    background = kit.rounded(if (hot) kit.accent else Color.argb(26, 255, 255, 255), 20)
    setPadding(kit.dp(if (small) 10 else 12), kit.dp(if (small) 4 else 6), kit.dp(if (small) 10 else 12), kit.dp(if (small) 4 else 6))
    addView(kit.icon(t.icon, fg, if (small) 15 else 18))
    addView(kit.text(t.name, if (small) 13f else 15f, fg, if (hot) 600 else 500).apply { setSingleLine() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = kit.dp(6) })
}

private fun avatar(kit: Kit, p: Person, sizeDp: Int) = FrameLayout(kit.ctx).apply {
    background = kit.circle(ChoresModule.color(kit, p))
    addView(kit.text(p.alias.take(1).uppercase(), sizeDp * 0.42f, kit.ink, 600), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
    layoutParams = LinearLayout.LayoutParams(kit.dp(sizeDp), kit.dp(sizeDp))
}

/** As tarefas de hoje, uma linha por pessoa (só de [person], se houver); null se ninguém tiver tarefas. */
private fun todayRows(kit: Kit, home: Home, ch: ChoresData, person: String?, small: Boolean): List<View> {
    val wd = ChoresData.today(); val now = ch.momentNow()
    return home.people.filter { !it.hidden && (person == null || it.id == person) }.mapNotNull { p ->
        val mine = ch.ofPerson(p.id, wd)
        if (mine.isEmpty()) return@mapNotNull null
        LinearLayout(kit.ctx).apply {
            orientation = LinearLayout.VERTICAL
            val hd = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
            hd.addView(avatar(kit, p, if (small) 30 else 36))
            hd.addView(kit.text(p.alias, if (small) 16f else 18f, kit.fg, 600), LinearLayout.LayoutParams(-2, -2).apply { marginStart = kit.dp(10) })
            addView(hd)
            val flow = Flow(kit.ctx, kit.dp(8), kit.dp(8))
            mine.forEach { flow.addView(taskChip(kit, it, now, small)) }
            addView(flow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(8) })
        }
    }
}

/** Cartão "Tarefas de hoje" (de todos ou de uma pessoa); toca para abrir a página Tarefas. */
class ChoresCard(private val kit: Kit, private val spec: LCard, private val nav: Nav) : Card(kit.ctx) {
    private val list = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
    private val title = kit.text("", 20f, kit.fg, 500, display = true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
    private var sig = ""

    init {
        background = kit.glass(26)
        isClickable = true
        setOnClickListener { nav.openScreen("chores") }
        val pad = kit.dp(22)
        val col = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        val row = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(FrameLayout(kit.ctx).apply {
            background = kit.circle(Color.argb(40, 255, 255, 255))
            addView(kit.icon("mdi:broom", kit.fg, 24), FrameLayout.LayoutParams(kit.dp(24), kit.dp(24), Gravity.CENTER))
        }, LinearLayout.LayoutParams(kit.dp(46), kit.dp(46)))
        row.addView(title, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = kit.dp(12) })
        col.addView(row)
        col.addView(list, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(12) })
        addView(col, LayoutParams(-1, -2))
        update()
    }

    override fun update() {
        val home = Ha.home ?: return
        val ch = home.chores
        val who = spec.person?.let { id -> home.people.firstOrNull { it.id == id } }
        // muda à meia-noite e quando muda o momento do dia (o realce)
        val s = "${ch?.tasks?.size}|${ch?.schedule}|${who?.alias}|${LocalDate.now()}|${ch?.momentNow()}"
        if (s == sig) return
        sig = s
        title.text = if (who != null) Tr.s("Tarefas: ${who.alias}", "Chores: ${who.alias}") else Tr.t("Tarefas de hoje")
        title.setTextColor(kit.fg)
        list.removeAllViews()
        if (ch == null) { list.addView(kit.text(Tr.t("Define as tarefas nas Definições → Tarefas."), 15f, kit.muted)); return }
        val rows = todayRows(kit, home, ch, spec.person, small = true)
        if (rows.isEmpty()) list.addView(kit.text(Tr.t("Sem tarefas hoje."), 15f, kit.muted))
        rows.forEach { list.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = kit.dp(12) }) }
    }
}

/** Página Tarefas (dock "chores"): o resumo de hoje por pessoa e a semana (tarefa × dia), com hoje realçado. */
class ChoresPage(kit: Kit, private val home: Home) : Page(kit) {
    private val root = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(kit.dp(40), kit.dp(30), kit.dp(40), dockSpace(kit)) }
    private var sig = ""
    override val view: View = ScrollView(kit.ctx).apply { isVerticalScrollBarEnabled = false; addView(root, FrameLayout.LayoutParams(-1, -2)) }

    init { update() }

    override fun update() {
        val ch = home.chores
        val s = "${LocalDate.now()}|${ch?.momentNow()}"
        if (s == sig) return
        sig = s
        root.removeAllViews()
        root.addView(kit.text(Tr.t("Tarefas"), kit.clamp(30f, kit.vw(4f), 44f), kit.fg, 400, display = true))
        if (ch == null) {
            root.addView(kit.text(Tr.t("Ainda não há tarefas. Em Definições → Tarefas cria as tarefas da casa e escolhe quem faz cada uma em cada dia da semana."), 17f, kit.muted),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(14) })
            return
        }
        val today = ChoresData.today()
        // hoje, por pessoa
        val top = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL; background = kit.glass(26); setPadding(kit.dp(24), kit.dp(20), kit.dp(24), kit.dp(10)) }
        val dayName = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Tr.locale)
        top.addView(kit.text((Tr.t("Hoje") + " · " + dayName).uppercase(), 13f, kit.muted, 600, spacing = 0.2f), LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = kit.dp(12) })
        val rows = todayRows(kit, home, ch, null, small = false)
        if (rows.isEmpty()) top.addView(kit.text(Tr.t("Sem tarefas hoje."), 16f, kit.muted), LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = kit.dp(10) })
        rows.forEach { top.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = kit.dp(14) }) }
        root.addView(top, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(18) })
        // a semana
        val week = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL; background = kit.glass(26); setPadding(kit.dp(20), kit.dp(18), kit.dp(20), kit.dp(18)) }
        val people = home.people.filter { !it.hidden }.associateBy { it.id }
        val nameW = kit.dp(190); val cellW = kit.dp(kit.clamp(70f, (kit.wdp - 80f - 40f - 190f) / 7f, 180f))
        fun line(first: View, cells: List<View>) = LinearLayout(kit.ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(first, LinearLayout.LayoutParams(nameW, -2))
            cells.forEach { addView(it, LinearLayout.LayoutParams(cellW, kit.dp(46)).apply { marginStart = kit.dp(6) }) }
        }
        val heads = (0 until 7).map { i ->
            kit.text(java.time.DayOfWeek.of(i + 1).getDisplayName(TextStyle.SHORT, Tr.locale).uppercase(), 13f, if (i == today) kit.accent else kit.muted, if (i == today) 700 else 600, spacing = 0.1f)
                .apply { gravity = Gravity.CENTER }
        }
        val table = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
        table.addView(line(View(kit.ctx), heads))
        ch.tasks.forEach { t ->
            val name = LinearLayout(kit.ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(kit.icon(t.icon, kit.fg, 22))
                val txt = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
                txt.addView(kit.text(t.name, 16f, kit.fg, 500).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
                if (t.moment != "day") txt.addView(kit.text(Tr.t(MOMENT_PT[t.moment] ?: ""), 12f, kit.muted))
                addView(txt, LinearLayout.LayoutParams(-1, -2).apply { marginStart = kit.dp(10) })
            }
            val cells = (0 until 7).map { i ->
                LinearLayout(kit.ctx).apply {
                    gravity = Gravity.CENTER
                    background = if (i == today) android.graphics.drawable.LayerDrawable(arrayOf(kit.rounded(Color.argb(36, 246, 184, 63), 14),
                        android.graphics.drawable.GradientDrawable().apply { cornerRadius = kit.dp(14).toFloat(); setStroke(kit.dp(1), kit.accent) })) else kit.rounded(Color.argb(12, 255, 255, 255), 14)
                    ch.people(t.id, i).mapNotNull { people[it] }.forEach { p -> addView(avatar(kit, p, 30), LinearLayout.LayoutParams(kit.dp(30), kit.dp(30)).apply { marginStart = kit.dp(2); marginEnd = kit.dp(2) }) }
                }
            }
            table.addView(line(name, cells), LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(6) })
        }
        week.addView(HorizontalScrollView(kit.ctx).apply { isHorizontalScrollBarEnabled = false; addView(table) })
        root.addView(week, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(16) })
    }

    companion object {
        val MOMENT_PT = mapOf("morning" to "Manhã", "afternoon" to "Tarde", "evening" to "Noite", "day" to "Todo o dia")
    }
}
