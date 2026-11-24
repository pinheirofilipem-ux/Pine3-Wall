package pt.pine3.wall

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

/** Um evento do calendário da família (schema/home.schema.json, "event"): horas locais e pessoas já tratadas pela integração. */
class CalEvent(
    val id: String, val title: String, val start: String, val end: String, val allDay: Boolean,
    val people: List<String>, val calendar: String, val location: String?, val private: Boolean,
) {
    val day: String get() = start.take(10)
    val time: String get() = if (allDay) "" else start.drop(11).take(5)
    val label: String get() = if (private) Tr.t("Ocupado") else title.ifBlank { Tr.t("(sem título)") }

    /** Acontece (pelo menos em parte) no dia [d] ("AAAA-MM-DD")? */
    fun on(d: String): Boolean {
        val s = day; val e = end.take(10)
        if (allDay) return d == s || (s <= d && d < e)
        if (d == s) return true
        return s < d && (d < e || (d == e && end.drop(11) > "00:00"))
    }

    companion object {
        fun parse(o: JSONObject) = CalEvent(
            o.optString("id"), o.optString("title"), o.optString("start"), o.optString("end", o.optString("start")), o.optBoolean("all_day"),
            o.optJSONArray("people")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
            o.optString("calendar"), o.optString("location", "").ifBlank { null }, o.optBoolean("private"),
        )
    }
}

/** O módulo calendar no modelo da casa: os eventos lidos, a cor da família e o erro da última leitura. */
class CalendarData(val entities: List<String>, val days: Int, val color: String?, val from: String?, val to: String?, val error: String?, val events: List<CalEvent>) {
    fun on(d: String) = events.filter { it.on(d) }

    /** Os próximos [n] que ainda não acabaram (só os de [person] e os da família, se houver pessoa). */
    fun upcoming(n: Int, person: String? = null, now: LocalDateTime = LocalDateTime.now()): List<CalEvent> {
        val key = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")); val today = key.take(10)
        return events.filter { e -> (if (e.allDay) e.end > today || e.day == today else e.end > key) && (person == null || e.people.isEmpty() || person in e.people) }.take(n)
    }

    companion object {
        fun parse(o: JSONObject) = CalendarData(
            o.optJSONArray("entities")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(), o.optInt("days", 14),
            o.optString("color", "").ifBlank { null }, o.optString("from", "").ifBlank { null }, o.optString("to", "").ifBlank { null },
            o.optString("error", "").ifBlank { null },
            o.optJSONArray("events")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(CalEvent::parse) } }.orEmpty(),
        )
    }
}

/** Datas e textos do calendário ("Hoje · 17:30", "Amanhã · Todo o dia"). */
object CalFmt {
    fun key(d: LocalDate): String = d.toString()
    private fun parse(s: String): LocalDate = LocalDate.parse(s.take(10))

    fun dayName(d: String, today: LocalDate = LocalDate.now()): String = when (d) {
        key(today) -> Tr.t("Hoje")
        key(today.plusDays(1)) -> Tr.t("Amanhã")
        else -> parse(d).format(DateTimeFormatter.ofPattern(if (Tr.en) "EEEE d MMMM" else "EEEE, d 'de' MMMM", Tr.locale))
    }

    fun whenText(e: CalEvent, withEnd: Boolean = false, today: LocalDate = LocalDate.now()): String {
        val day = if (e.day < key(today) && e.on(key(today))) Tr.t("Hoje") else dayName(e.day, today)
        if (e.allDay) {
            val last = parse(e.end).minusDays(1)
            return if (key(last) > e.day) "$day – ${last.format(DateTimeFormatter.ofPattern("d MMM", Tr.locale))}" else "$day · ${Tr.t("Todo o dia")}"
        }
        val end = if (withEnd && e.end.take(10) == e.day) "–" + e.end.drop(11).take(5) else ""
        return "$day · ${e.time}$end"
    }

    fun monday(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun cap(s: String) = s.replaceFirstChar { it.uppercase() }
}

/** O módulo calendar no registo de módulos: a secção "agenda" (página própria) e o cartão "upcoming". */
object CalendarModule : Module {
    override val id = "calendar"
    override val sectionTypes = setOf("agenda")
    override val cardTypes = setOf("upcoming")
    override fun card(c: CardContext, spec: LCard): Card? = when (spec.type) {
        "upcoming" -> UpcomingCard(c.kit, spec, c.nav)
        else -> null
    }

    fun color(kit: Kit, cal: CalendarData?, e: CalEvent?): Int {
        val pid = e?.people?.firstOrNull()
        val person = pid?.let { id -> Ha.home?.people?.firstOrNull { it.id == id } }
        return parseColor(person?.color) ?: parseColor(cal?.color) ?: kit.accent
    }

    private fun parseColor(s: String?): Int? = try { s?.let { Color.parseColor(it) } } catch (_: Exception) { null }
}

/** Cartão "Próximos": os próximos eventos (de uma pessoa e da família, se escolhida); toca para abrir a Agenda. */
class UpcomingCard(private val kit: Kit, private val spec: LCard, private val nav: Nav) : Card(kit.ctx) {
    private val list = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
    private val title = kit.text("", 20f, kit.fg, 500, display = true).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.END }
    private var sig = ""

    init {
        background = kit.glass(26)
        isClickable = true
        setOnClickListener { nav.openScreen("calendar") }
        val pad = kit.dp(22)
        val col = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        val row = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(FrameLayout(kit.ctx).apply {
            background = kit.circle(Color.argb(40, 255, 255, 255))
            addView(kit.icon("mdi:calendar-month-outline", kit.fg, 24), FrameLayout.LayoutParams(kit.dp(24), kit.dp(24), Gravity.CENTER))
        }, LinearLayout.LayoutParams(kit.dp(46), kit.dp(46)))
        row.addView(title, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = kit.dp(12) })
        col.addView(row)
        col.addView(list, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(12) })
        addView(col, LayoutParams(-1, -2))
        update()
    }

    override fun update() {
        val cal = Ha.home?.calendar
        val who = spec.person?.let { id -> Ha.home?.people?.firstOrNull { it.id == id } }
        val items = cal?.upcoming(if (spec.count > 0) spec.count else if (spec.big) 6 else 4, spec.person).orEmpty()
        val s = (who?.alias ?: "") + "|" + (cal == null) + items.joinToString { it.id + it.start + it.label } + "|" + LocalDate.now()
        if (s == sig) return
        sig = s
        title.text = if (who != null) Tr.s("Próximos: ${who.alias}", "Coming up: ${who.alias}") else Tr.t("Próximos")
        title.setTextColor(kit.fg)
        list.removeAllViews()
        if (cal == null) { list.addView(kit.text(Tr.t("Escolhe o calendário nas Definições → Calendário."), 15f, kit.muted)); return }
        if (items.isEmpty()) { list.addView(kit.text(Tr.t("Nada marcado."), 15f, kit.muted)); return }
        items.forEach { e -> list.addView(eventRow(kit, cal, e), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = kit.dp(10) }) }
    }
}

/** Uma linha: barra com a cor da pessoa, título e quando. */
private fun eventRow(kit: Kit, cal: CalendarData, e: CalEvent): View = LinearLayout(kit.ctx).apply {
    addView(View(kit.ctx).apply { background = kit.rounded(CalendarModule.color(kit, cal, e), 3) }, LinearLayout.LayoutParams(kit.dp(6), -1))
    val txt = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
    txt.addView(kit.text(e.label, 16f, kit.fg, 600).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.END })
    txt.addView(kit.text(CalFmt.whenText(e), 14f, kit.muted).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(2) })
    addView(txt, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = kit.dp(12) })
}

/** Página Agenda (dock "calendar"): Dia com uma coluna por pessoa e a da família, Semana e Mês; toca num evento para o detalhe. */
class AgendaPage(kit: Kit, private val home: Home) : Page(kit) {
    private val cal = home.calendar
    private var view0 = home.layout?.page("calendar")?.raw?.optJSONArray("sections")?.let { a ->
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.firstOrNull { it.optString("type") == "agenda" }?.optString("view", "day")
    } ?: "day"
    private var day: LocalDate = LocalDate.now()
    private val titleView = kit.text("", kit.clamp(30f, kit.vw(4f), 44f), kit.fg, 400, display = true).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.END }
    private val seg = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
    private val body = FrameLayout(kit.ctx)
    private var shownDate = LocalDate.now()
    override val view: View

    init {
        val root = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(kit.dp(40), kit.dp(30), kit.dp(40), dockSpace(kit)) }
        if (cal == null) {
            root.gravity = Gravity.CENTER
            root.addView(kit.text(Tr.t("Agenda"), 40f, kit.fg, 400, display = true).apply { gravity = Gravity.CENTER })
            root.addView(kit.text(Tr.t("Ainda não há calendário escolhido. Em Definições → Calendário escolhe o calendário da família (por exemplo o do iCloud, ligado pela integração CalDAV)."), 17f, kit.muted).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(kit.dp(kit.clamp(300f, kit.wdp * 0.6f, 720f)), -2).apply { topMargin = kit.dp(14) })
        } else {
            val hd = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
            hd.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
            hd.addView(seg)
            root.addView(hd)
            cal.error?.let { root.addView(kit.text(Tr.s("Não foi possível ler o calendário: $it", "Could not read the calendar: $it"), 14f, kit.accent), LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(8) }) }
            root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = kit.dp(16) })
            render()
        }
        view = root
    }

    /** Muda de dia à meia-noite (a página fica aberta na parede). */
    override fun update() { if (cal != null && LocalDate.now() != shownDate) { if (day == shownDate) day = LocalDate.now(); render() } }

    private fun render() {
        val c = cal ?: return
        shownDate = LocalDate.now()
        titleView.setTextColor(kit.fg)
        titleView.text = CalFmt.cap(when (view0) {
            "month" -> day.format(DateTimeFormatter.ofPattern("LLLL yyyy", Tr.locale))
            "week" -> CalFmt.monday(day).let { m -> m.format(DateTimeFormatter.ofPattern("d MMM", Tr.locale)) + " – " + m.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM", Tr.locale)) }
            else -> CalFmt.dayName(CalFmt.key(day)).let { n -> if (day == LocalDate.now() || day == LocalDate.now().plusDays(1)) n + " · " + day.format(DateTimeFormatter.ofPattern(if (Tr.en) "EEEE d MMMM" else "EEEE, d 'de' MMMM", Tr.locale)) else n }
        })
        seg.removeAllViews()
        listOf("day" to Tr.s("Dia", "Day"), "week" to Tr.t("Semana"), "month" to Tr.t("Mês")).forEach { (k, l) ->
            seg.addView(kit.pill(l, null, view0 == k) { view0 = k; render() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = kit.dp(8) })
        }
        seg.addView(kit.roundButton("mdi:chevron-left", 48, Color.argb(40, 255, 255, 255), kit.fg) { step(-1) }.also { (it.layoutParams as LinearLayout.LayoutParams).marginStart = kit.dp(16) })
        seg.addView(kit.pill(Tr.t("Hoje"), null, false) { day = LocalDate.now(); render() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = kit.dp(8) })
        seg.addView(kit.roundButton("mdi:chevron-right", 48, Color.argb(40, 255, 255, 255), kit.fg) { step(1) }.also { (it.layoutParams as LinearLayout.LayoutParams).marginStart = kit.dp(8) })
        body.removeAllViews()
        body.addView(when (view0) { "month" -> month(c); "week" -> week(c); else -> dayView(c) }, FrameLayout.LayoutParams(-1, -1))
    }

    private fun step(n: Int) {
        day = when (view0) { "month" -> day.withDayOfMonth(1).plusMonths(n.toLong()); "week" -> day.plusWeeks(n.toLong()); else -> day.plusDays(n.toLong()) }
        render()
    }

    private fun column(header: View, events: List<CalEvent>, d: String, c: CalendarData, today: Boolean = false, empty: String? = null, onTap: (() -> Unit)? = null): View {
        val col = LinearLayout(kit.ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = kit.glass(22).let { g -> if (today) android.graphics.drawable.LayerDrawable(arrayOf(g, GradientDrawable().apply { cornerRadius = kit.dp(22).toFloat(); setStroke(kit.dp(2), kit.accent) })) else g }
            setPadding(kit.dp(14), kit.dp(14), kit.dp(14), kit.dp(14))
            if (onTap != null) { isClickable = true; setOnClickListener { onTap() } }
        }
        col.addView(header)
        events.forEach { e -> col.addView(evChip(c, e, d), LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(8) }) }
        if (events.isEmpty() && empty != null) col.addView(kit.text(empty, 15f, kit.muted), LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(10) })
        return ScrollView(kit.ctx).apply { isVerticalScrollBarEnabled = false; addView(col, FrameLayout.LayoutParams(-1, -2)) }
    }

    private fun evChip(c: CalendarData, e: CalEvent, d: String): View {
        val color = CalendarModule.color(kit, c, e)
        return LinearLayout(kit.ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.LayerDrawable(arrayOf(
                kit.rounded(if (e.allDay) Color.argb(80, Color.red(color), Color.green(color), Color.blue(color)) else Color.argb(18, 255, 255, 255), 12),
                GradientDrawable().apply { setColor(color); cornerRadius = kit.dp(2).toFloat() },
            )).apply { setLayerSize(1, kit.dp(4), -1); setLayerGravity(1, Gravity.START or Gravity.FILL_VERTICAL) }
            setPadding(kit.dp(12), kit.dp(9), kit.dp(10), kit.dp(9))
            isClickable = true; setOnClickListener { detail(c, e) }
            val t = if (e.allDay) "" else if (e.day == d) e.time else "…"
            if (t.isNotEmpty()) addView(kit.text(t, 14f, kit.muted), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = kit.dp(8) })
            addView(kit.text(e.label, 15f, kit.fg, 500).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun avatar(name: String, color: Int) = FrameLayout(kit.ctx).apply {
        background = kit.circle(color)
        addView(kit.text(name.take(1).uppercase(), 15f, kit.ink, 600), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(kit.dp(34), kit.dp(34))
    }

    private fun dayView(c: CalendarData): View {
        val d = CalFmt.key(day)
        val evs = c.on(d)
        val fam = home.people.filter { !it.hidden }
        val famColor = CalendarModule.color(kit, c, null)
        val cols = fam.map { p -> Triple(p.alias, try { Color.parseColor(p.color) } catch (_: Exception) { kit.accent }, evs.filter { p.id in it.people }) } +
            Triple(Tr.t("Família"), famColor, evs.filter { e -> e.people.isEmpty() || e.people.none { id -> fam.any { it.id == id } } })
        val row = LinearLayout(kit.ctx)
        val minW = kit.dp(200)
        cols.forEachIndexed { i, (name, color, list) ->
            val hd = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
            hd.addView(avatar(name, color))
            hd.addView(kit.text(name, 17f, kit.fg, 600), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = kit.dp(10) })
            if (list.isNotEmpty()) hd.addView(kit.text(list.size.toString(), 14f, kit.muted))
            row.addView(column(hd, list, d, c, empty = Tr.t("Livre")), LinearLayout.LayoutParams(0, -1, 1f).apply { if (i > 0) marginStart = kit.dp(12) })
        }
        // muitas pessoas: a linha rola na horizontal em vez de apertar as colunas
        return if (cols.size * (minW + kit.dp(12)) > kit.dp(kit.wdp) - kit.dp(80)) HorizontalScrollView(kit.ctx).apply {
            isHorizontalScrollBarEnabled = false
            row.layoutParams = FrameLayout.LayoutParams(cols.size * (minW + kit.dp(12)), -1)
            addView(row)
        } else row
    }

    private fun week(c: CalendarData): View {
        val start = CalFmt.monday(day); val today = LocalDate.now()
        val row = LinearLayout(kit.ctx)
        for (i in 0 until 7) {
            val d = start.plusDays(i.toLong())
            val hd = LinearLayout(kit.ctx).apply { gravity = Gravity.CENTER_VERTICAL }
            hd.addView(kit.text(CalFmt.cap(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Tr.locale)), 15f, kit.fg, 600), LinearLayout.LayoutParams(0, -2, 1f))
            hd.addView(kit.text(d.dayOfMonth.toString(), 22f, kit.fg, 400, display = true))
            row.addView(column(hd, c.on(CalFmt.key(d)), CalFmt.key(d), c, today = d == today) { day = d; view0 = "day"; render() },
                LinearLayout.LayoutParams(0, -1, 1f).apply { if (i > 0) marginStart = kit.dp(10) })
        }
        return row
    }

    private fun month(c: CalendarData): View {
        val first = day.withDayOfMonth(1); val start = CalFmt.monday(first); val today = LocalDate.now()
        val weeks = (first.dayOfWeek.value - 1 + first.lengthOfMonth() + 6) / 7
        val grid = LinearLayout(kit.ctx).apply { orientation = LinearLayout.VERTICAL }
        val heads = LinearLayout(kit.ctx)
        for (i in 0 until 7) heads.addView(kit.text(start.plusDays(i.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Tr.locale).uppercase(), 13f, kit.muted, 600, spacing = 0.1f),
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = kit.dp(6) })
        grid.addView(heads)
        for (w in 0 until weeks) {
            val r = LinearLayout(kit.ctx)
            for (i in 0 until 7) {
                val d = start.plusDays((w * 7 + i).toLong()); val k = CalFmt.key(d); val evs = c.on(k)
                val cell = LinearLayout(kit.ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    background = kit.glass(16).let { g -> if (d == today) android.graphics.drawable.LayerDrawable(arrayOf(g, GradientDrawable().apply { cornerRadius = kit.dp(16).toFloat(); setStroke(kit.dp(2), kit.accent) })) else g }
                    alpha = if (d.month != first.month) 0.45f else 1f
                    setPadding(kit.dp(8), kit.dp(6), kit.dp(8), kit.dp(6))
                    isClickable = true; setOnClickListener { day = d; view0 = "day"; render() }
                    addView(kit.text(d.dayOfMonth.toString(), 17f, kit.fg, 500, display = true))
                    evs.take(3).forEach { e ->
                        val col = CalendarModule.color(kit, c, e)
                        addView(kit.text(e.label, 12f, kit.fg, 500).apply {
                            setSingleLine(); ellipsize = TextUtils.TruncateAt.END; setPadding(kit.dp(6), kit.dp(1), kit.dp(6), kit.dp(1))
                            background = kit.rounded(Color.argb(95, Color.red(col), Color.green(col), Color.blue(col)), 6)
                        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = kit.dp(3) })
                    }
                    if (evs.size > 3) addView(kit.text("+${evs.size - 3}", 12f, kit.muted))
                }
                r.addView(cell, LinearLayout.LayoutParams(0, -1, 1f).apply { if (i > 0) marginStart = kit.dp(8) })
            }
            grid.addView(r, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = kit.dp(8) })
        }
        return grid
    }

    /** O detalhe de um evento: título, quando, onde e de quem. */
    private fun detail(c: CalendarData, e: CalEvent) {
        val act = kit.ctx as? Activity ?: return
        val dialog = PokeDialog(act)
        val color = CalendarModule.color(kit, c, e)
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.LayerDrawable(arrayOf(kit.rounded(Color.argb(240, 30, 22, 34), 28), GradientDrawable().apply { setColor(color); cornerRadius = kit.dp(3).toFloat() }))
                .apply { setLayerSize(1, -1, kit.dp(6)); setLayerGravity(1, Gravity.TOP or Gravity.FILL_HORIZONTAL); setLayerInset(1, kit.dp(28), 0, kit.dp(28), 0) }
            setPadding(kit.dp(30), kit.dp(28), kit.dp(30), kit.dp(26))
        }
        card.addView(kit.text(e.label, 28f, Color.WHITE, 400, display = true))
        fun line(icon: String, s: String) = LinearLayout(act).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(kit.icon(icon, Color.argb(200, 255, 255, 255), 20))
            addView(kit.text(s, 17f, Color.argb(225, 255, 255, 255)), LinearLayout.LayoutParams(-2, -2).apply { marginStart = kit.dp(10) })
        }
        card.addView(line("mdi:clock-outline", CalFmt.whenText(e, withEnd = true)), LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(14) })
        e.location?.let { card.addView(line("mdi:map-marker-outline", it), LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(10) }) }
        val who = LinearLayout(act)
        val people = e.people.mapNotNull { id -> home.people.firstOrNull { it.id == id } }
        (if (people.isEmpty()) listOf(Tr.t("Família") to CalendarModule.color(kit, c, null)) else people.map { it.alias to (try { Color.parseColor(it.color) } catch (_: Exception) { kit.accent }) }).forEach { (n, col) ->
            who.addView(kit.text(n, 14f, kit.ink, 600).apply { background = kit.rounded(col, 20); setPadding(kit.dp(12), kit.dp(4), kit.dp(12), kit.dp(4)) },
                LinearLayout.LayoutParams(-2, -2).apply { marginEnd = kit.dp(6) })
        }
        card.addView(who, LinearLayout.LayoutParams(-2, -2).apply { topMargin = kit.dp(16) })
        val root = FrameLayout(act).apply {
            setBackgroundColor(Color.argb(150, 10, 4, 12)); isClickable = true; setOnClickListener { dialog.dismiss() }
            addView(card, FrameLayout.LayoutParams(kit.dp(kit.clamp(300f, kit.wdp * 0.45f, 520f)), -2, Gravity.CENTER))
        }
        dialog.setContentView(root)
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
        immersive(dialog)
        dialog.show()
        Elastic.pop(card, 0, 0.92f)
    }
}
