package pt.pine3.wall

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.FrameLayout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Uma face de um ecrã ambiente (schema/catalog.json, módulo "ambient"). */
class AmbientFace(val type: String, val style: String, val seconds: Boolean?, val entity: String?, val label: String?) {
    companion object {
        /** As faces que esta app sabe desenhar (as mesmas do catálogo; verificado nos testes). */
        val TYPES = setOf("clock_analog", "clock_digital", "date", "weather", "sensor", "people", "next_event", "agenda_today", "chores_today", "family_day")

        fun parse(o: JSONObject) = AmbientFace(
            o.optString("type"), o.optString("style", "classic"), if (o.has("seconds")) o.optBoolean("seconds") else null,
            o.optString("entity", "").ifBlank { null }, o.optString("label", "").ifBlank { null },
        )
    }
}

/** Um ecrã ambiente: 1 a 3 faces lado a lado, cor de destaque e modo noturno (vermelho escuro, pouco brilho). */
class AmbientSpec(val title: String, val faces: List<AmbientFace>, val color: Int?, val night: Boolean) {
    companion object {
        /** O ecrã da página "ambient/<id>" do layout, ou null se a página não for um ecrã ambiente. */
        fun of(layout: LayoutDoc?, key: String): AmbientSpec? {
            val page = layout?.page(key) ?: return null
            val raw = page.raw?.optJSONArray("sections") ?: return null
            val sec = (0 until raw.length()).mapNotNull { raw.optJSONObject(it) }.firstOrNull { it.optString("type") == "ambient" } ?: return null
            val faces = sec.optJSONArray("faces")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(AmbientFace::parse) } }.orEmpty().take(3)
            val color = sec.optString("color", "").takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) }?.let { Color.parseColor(it) }
            return AmbientSpec(page.title ?: key.removePrefix("ambient/"), faces.ifEmpty { listOf(AmbientFace("clock_digital", "classic", null, null, null)) }, color, sec.optBoolean("night"))
        }

        /** Os ecrãs ambiente do layout: chave da página -> título. */
        fun all(layout: LayoutDoc?): List<Pair<String, String>> =
            layout?.pages?.keys?.filter { it.startsWith("ambient/") }?.map { it to (layout.page(it)?.title ?: it.removePrefix("ambient/")) }.orEmpty()
    }
}

/** O módulo ambient no registo de módulos: uma secção "ambient" desenhada pela [AmbientView]. */
object AmbientModule : Module {
    override val id = "ambient"
    override val sectionTypes = setOf("ambient")
    override val cardTypes = emptySet<String>()
    override fun card(c: CardContext, spec: LCard): Card? = null
}

/** Desenha um ecrã ambiente, atualizado a cada segundo. */
class AmbientView(private val kit: Kit, private val spec: AmbientSpec, private val h24: Boolean, private val weatherPref: String) : View(kit.ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val night = spec.night
    private val fg = if (night) Color.parseColor("#B3261E") else Color.WHITE
    private val dim = if (night) Color.parseColor("#5C1410") else Color.argb(150, 255, 255, 255)
    private val accent = if (night) Color.parseColor("#D33A2C") else spec.color ?: kit.accent
    private val tick = object : Runnable { override fun run() { invalidate(); postDelayed(this, 1000) } }

    init { setBackgroundColor(Color.BLACK) }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); post(tick) }
    override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (!night) {
            // fundo: um brilho suave da cor de destaque, como os temas do painel
            p.shader = android.graphics.RadialGradient(width * 0.5f, height * 0.45f, maxOf(width, height) * 0.7f,
                intArrayOf(Color.argb(60, Color.red(accent), Color.green(accent), Color.blue(accent)), Color.TRANSPARENT), null, android.graphics.Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
            p.shader = null
        }
        val n = spec.faces.size
        val pad = kit.dp(24).toFloat()
        val w = (width - pad * (n + 1)) / n
        spec.faces.forEachIndexed { i, f ->
            val r = RectF(pad + i * (w + pad), pad, pad + i * (w + pad) + w, height - pad - kit.dp(if (n > 1) 70 else 40))
            when (f.type) {
                "clock_analog" -> analog(c, r, f)
                "clock_digital" -> digital(c, r, f)
                "date" -> date(c, r)
                "weather" -> weather(c, r)
                "sensor" -> sensor(c, r, f)
                "people" -> people(c, r)
                "next_event" -> nextEvent(c, r)
                "agenda_today" -> agendaToday(c, r)
                "chores_today" -> choresToday(c, r)
                "family_day" -> familyDay(c, r)
                else -> text(c, Tr.s("Atualiza a app", "Update the app"), r.centerX(), r.centerY(), kit.dp(18).toFloat(), dim, 500)
            }
        }
    }

    private fun text(c: Canvas, s: String, cx: Float, baseline: Float, size: Float, color: Int, weight: Int = 300, display: Boolean = true) {
        p.reset(); p.isAntiAlias = true; p.color = color; p.textSize = size; p.typeface = kit.font(weight, display); p.textAlign = Paint.Align.CENTER
        c.drawText(s, cx, baseline, p)
    }

    /** O maior tamanho de letra com que [s] cabe na largura [w] (sem passar de [max]). */
    private fun fit(s: String, w: Float, max: Float, weight: Int = 300): Float {
        p.reset(); p.typeface = kit.font(weight, true); p.textSize = max
        val tw = p.measureText(s)
        return if (tw <= w) max else max * w / tw
    }

    private fun analog(c: Canvas, r: RectF, f: AmbientFace) {
        val cx = r.centerX(); val cy = r.centerY(); val rad = min(r.width(), r.height()) * 0.46f
        val cal = Calendar.getInstance()
        val sec = cal.get(Calendar.SECOND); val min = cal.get(Calendar.MINUTE) + sec / 60f; val hour = cal.get(Calendar.HOUR) + min / 60f
        val bold = f.style == "bold"; val minimal = f.style == "minimal"
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        if (!minimal) { p.color = dim; p.strokeWidth = rad * 0.012f; c.drawCircle(cx, cy, rad, p) }
        for (i in 0 until 60) {
            val major = i % 5 == 0
            if (minimal && !major) continue
            if (minimal && i % 15 != 0) continue
            val a = Math.toRadians(i * 6.0 - 90)
            val r1 = rad * (if (major) 0.84f else 0.9f); val r2 = rad * 0.95f
            p.color = if (major) fg else dim; p.strokeWidth = rad * (if (major) (if (bold) 0.045f else 0.025f) else 0.01f)
            c.drawLine(cx + (r1 * cos(a)).toFloat(), cy + (r1 * sin(a)).toFloat(), cx + (r2 * cos(a)).toFloat(), cy + (r2 * sin(a)).toFloat(), p)
        }
        if (bold) listOf(12, 3, 6, 9).forEach { h ->
            val a = Math.toRadians(h * 30.0 - 90); val rr = rad * 0.66f
            text(c, h.toString(), cx + (rr * cos(a)).toFloat(), cy + (rr * sin(a)).toFloat() + rad * 0.07f, rad * 0.2f, fg, 500)
        }
        fun hand(angle: Double, len: Float, width: Float, color: Int, tail: Float = 0.1f) {
            val a = Math.toRadians(angle - 90)
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.color = color; p.strokeWidth = width
            c.drawLine(cx - (rad * tail * cos(a)).toFloat(), cy - (rad * tail * sin(a)).toFloat(), cx + (len * cos(a)).toFloat(), cy + (len * sin(a)).toFloat(), p)
        }
        hand(hour * 30.0, rad * 0.5f, rad * (if (bold) 0.07f else 0.05f), fg)
        hand(min * 6.0, rad * 0.78f, rad * (if (bold) 0.05f else 0.035f), fg)
        if (f.seconds != false) hand(sec * 6.0, rad * 0.86f, rad * 0.015f, accent, 0.18f)
        p.reset(); p.isAntiAlias = true; p.color = accent; c.drawCircle(cx, cy, rad * 0.035f, p)
    }

    private fun digital(c: Canvas, r: RectF, f: AmbientFace) {
        val pattern = (if (h24) "HH:mm" else "h:mm") + (if (f.seconds == true) ":ss" else "")
        val now = SimpleDateFormat(pattern, Tr.locale).format(Date())
        val size = fit(now, r.width(), r.height() * 0.5f)
        text(c, now, r.centerX(), r.centerY() + size * 0.3f, size, fg)
        val d = SimpleDateFormat(if (Tr.en) "EEEE d MMMM" else "EEEE, d 'de' MMMM", Tr.locale).format(Date()).replaceFirstChar { it.uppercase() }
        text(c, d, r.centerX(), r.centerY() + size * 0.3f + kit.dp(46), fit(d, r.width(), kit.dp(26).toFloat(), 500), dim, 500, false)
    }

    private fun date(c: Canvas, r: RectF) {
        val now = Date()
        val wd = SimpleDateFormat("EEEE", Tr.locale).format(now).uppercase()
        val day = SimpleDateFormat("d", Tr.locale).format(now)
        val month = SimpleDateFormat("MMMM", Tr.locale).format(now).replaceFirstChar { it.uppercase() }
        val big = min(r.height() * 0.5f, r.width() * 0.7f)
        text(c, wd, r.centerX(), r.centerY() - big * 0.55f, fit(wd, r.width(), kit.dp(30).toFloat(), 600), accent, 600, false)
        text(c, day, r.centerX(), r.centerY() + big * 0.35f, big, fg)
        text(c, month, r.centerX(), r.centerY() + big * 0.35f + kit.dp(48), kit.dp(30).toFloat(), dim, 500, false)
    }

    private fun weather(c: Canvas, r: RectF) {
        val cur = Weather.current(weatherPref) { postInvalidate() }
        if (cur == null) { Weather.load(weatherPref) { postInvalidate() }; text(c, Tr.t("A carregar o tempo…"), r.centerX(), r.centerY(), kit.dp(20).toFloat(), dim, 500, false); return }
        val iconSize = min(r.width(), r.height()) * 0.32f
        kit.mdi(Fmt.WEATHER_ICON[cur.cond] ?: "mdi:weather-cloudy").mutate().apply {
            setTint(accent)
            setBounds((r.centerX() - iconSize / 2).toInt(), (r.centerY() - iconSize * 1.25f).toInt(), (r.centerX() + iconSize / 2).toInt(), (r.centerY() - iconSize * 0.25f).toInt())
            draw(c)
        }
        val t = if (cur.temp == null) "–" else Fmt.num(Math.round(cur.temp).toDouble(), 0) + "°"
        val size = min(r.height() * 0.32f, r.width() * 0.45f)
        text(c, t, r.centerX(), r.centerY() + size * 0.55f, size, fg)
        val today = Weather.daily.firstOrNull()
        val line = Tr.t(Fmt.WEATHER_PT[cur.cond] ?: cur.cond) + (today?.let { d -> "  ·  " + (d.temp?.let { Math.round(it).toString() + "°" } ?: "–") + " / " + (d.low?.let { Math.round(it).toString() + "°" } ?: "–") } ?: "")
        text(c, line, r.centerX(), r.centerY() + size * 0.55f + kit.dp(42), fit(line, r.width(), kit.dp(24).toFloat(), 500), dim, 500, false)
    }

    private fun sensor(c: Canvas, r: RectF, f: AmbientFace) {
        val id = f.entity
        val value = id?.let { Fmt.reading(it) } ?: "–"
        val size = fit(value, r.width(), r.height() * 0.38f)
        text(c, value, r.centerX(), r.centerY() + size * 0.3f, size, fg)
        val name = f.label ?: id?.let { Fmt.name(it) } ?: Tr.s("Escolhe um sensor", "Choose a sensor")
        text(c, name, r.centerX(), r.centerY() + size * 0.3f + kit.dp(46), fit(name, r.width(), kit.dp(26).toFloat(), 500), accent, 500, false)
    }

    /** Módulo calendar: o próximo evento (título, quando e de quem). */
    private fun nextEvent(c: Canvas, r: RectF) {
        val cal = Ha.home?.calendar
        val e = cal?.upcoming(1)?.firstOrNull()
        if (cal == null || e == null) { text(c, if (cal == null) Tr.s("Sem calendário", "No calendar") else Tr.t("Nada marcado."), r.centerX(), r.centerY(), kit.dp(22).toFloat(), dim, 500, false); return }
        val color = if (night) accent else CalendarModule.color(kit, cal, e)
        val title = e.label
        val size = fit(title, r.width(), min(r.height() * 0.16f, kit.dp(64).toFloat()), 400)
        text(c, Tr.s("PRÓXIMO", "NEXT"), r.centerX(), r.centerY() - size * 1.1f, kit.dp(22).toFloat(), color, 600, false)
        text(c, title, r.centerX(), r.centerY() + size * 0.35f, size, fg, 400)
        val w = CalFmt.whenText(e)
        text(c, w, r.centerX(), r.centerY() + size * 0.35f + kit.dp(44), fit(w, r.width(), kit.dp(24).toFloat(), 500), dim, 500, false)
        val who = e.people.mapNotNull { id -> Ha.home?.people?.firstOrNull { it.id == id }?.alias }.ifEmpty { listOf(Tr.t("Família")) }.joinToString(" · ")
        text(c, who, r.centerX(), r.centerY() + size * 0.35f + kit.dp(80), fit(who, r.width(), kit.dp(20).toFloat(), 600), color, 600, false)
    }

    /** Módulo calendar: os eventos de hoje, por hora. */
    private fun agendaToday(c: Canvas, r: RectF) {
        val cal = Ha.home?.calendar
        if (cal == null) { text(c, Tr.s("Sem calendário", "No calendar"), r.centerX(), r.centerY(), kit.dp(22).toFloat(), dim, 500, false); return }
        val list = cal.on(CalFmt.key(java.time.LocalDate.now())).take(6)
        val line = min(kit.dp(48).toFloat(), r.height() / (list.size + 2))
        var y = r.centerY() - line * (list.size + 1) / 2f + line * 0.5f
        text(c, Tr.t("Hoje").uppercase(), r.centerX(), y, kit.dp(22).toFloat(), accent, 600, false)
        if (list.isEmpty()) { text(c, Tr.t("Nada marcado."), r.centerX(), y + line * 1.2f, kit.dp(24).toFloat(), dim, 500, false); return }
        list.forEach { e ->
            y += line
            val s = (if (e.allDay) "" else e.time + "  ") + e.label
            text(c, s, r.centerX(), y, fit(s, r.width(), line * 0.55f, 400), if (night) fg else fg, 400, false)
        }
    }

    /** Módulo chores: as tarefas de hoje, uma linha por pessoa ("Pedro  Pôr a mesa · Lixo"). */
    private fun choresToday(c: Canvas, r: RectF) {
        val home = Ha.home; val ch = home?.chores
        if (home == null || ch == null) { text(c, Tr.s("Sem tarefas", "No chores"), r.centerX(), r.centerY(), kit.dp(22).toFloat(), dim, 500, false); return }
        val wd = ChoresData.today()
        val rows = home.people.filter { !it.hidden }.mapNotNull { p -> ch.ofPerson(p.id, wd).takeIf { it.isNotEmpty() }?.let { p to it } }.take(6)
        val line = min(kit.dp(52).toFloat(), r.height() / (rows.size + 2))
        var y = r.centerY() - line * (rows.size + 1) / 2f + line * 0.5f
        text(c, Tr.t("Tarefas").uppercase(), r.centerX(), y, kit.dp(22).toFloat(), accent, 600, false)
        if (rows.isEmpty()) { text(c, Tr.t("Sem tarefas hoje."), r.centerX(), y + line * 1.2f, kit.dp(24).toFloat(), dim, 500, false); return }
        rows.forEach { (p, tasks) ->
            y += line
            val s = p.alias + "  " + tasks.joinToString(" · ") { it.name }
            val col = if (night) fg else ChoresModule.color(kit, p)
            text(c, s, r.centerX(), y, fit(s, r.width(), line * 0.5f, 400), col, 400, false)
        }
    }

    /** O dia da família: uma linha por pessoa com os eventos e as tarefas de hoje ("Pedro  17:30 Natação · Pôr a mesa"). */
    private fun familyDay(c: Canvas, r: RectF) {
        val home = Ha.home
        if (home == null || (home.calendar == null && home.chores == null)) { text(c, Tr.s("Sem calendário nem tarefas", "No calendar or chores"), r.centerX(), r.centerY(), kit.dp(22).toFloat(), dim, 500, false); return }
        val rows = FamilyDay.of(home).columns.filter { it.second.isNotEmpty() }.take(6)
        val line = min(kit.dp(52).toFloat(), r.height() / (rows.size + 2))
        var y = r.centerY() - line * (rows.size + 1) / 2f + line * 0.5f
        text(c, Tr.s("O DIA DA FAMÍLIA", "THE FAMILY'S DAY"), r.centerX(), y, kit.dp(22).toFloat(), accent, 600, false)
        if (rows.isEmpty()) { text(c, Tr.s("Dia livre", "Free day"), r.centerX(), y + line * 1.2f, kit.dp(24).toFloat(), dim, 500, false); return }
        rows.forEach { (p, items) ->
            y += line
            val s = p.alias + "  " + items.filter { !it.past }.ifEmpty { items }.joinToString(" · ") { (if (it.kind == "event" && ':' in it.label) it.label + " " else "") + it.title }
            text(c, s, r.centerX(), y, fit(s, r.width(), line * 0.5f, 400), if (night) fg else ChoresModule.color(kit, p), 400, false)
        }
    }

    private fun people(c: Canvas, r: RectF) {
        val fam = Ha.home?.people?.filter { !it.hidden }.orEmpty().take(6)
        if (fam.isEmpty()) { text(c, Tr.s("Sem pessoas", "No people"), r.centerX(), r.centerY(), kit.dp(22).toFloat(), dim, 500, false); return }
        val cols = if (fam.size > 3) 3 else fam.size
        val rows = (fam.size + cols - 1) / cols
        val cell = min(r.width() / cols, r.height() / rows)
        val rad = cell * 0.3f
        fam.forEachIndexed { i, person ->
            val cx = r.centerX() + (i % cols - (cols - 1) / 2f) * cell
            val cy = r.centerY() + (i / cols - (rows - 1) / 2f) * cell - kit.dp(10)
            val home = Fmt.state(person.id)?.state == "home"
            val col = if (night) fg else try { Color.parseColor(person.color) } catch (_: Exception) { accent }
            p.reset(); p.isAntiAlias = true; p.color = col; p.alpha = if (home) 255 else 70
            c.drawCircle(cx, cy, rad, p)
            text(c, person.alias.take(1).uppercase(), cx, cy + rad * 0.35f, rad, if (night) Color.BLACK else kit.ink, 600)
            text(c, person.alias, cx, cy + rad + kit.dp(28), kit.dp(20).toFloat(), if (home) fg else dim, 500, false)
        }
    }
}

/** Página da dock com um ecrã ambiente (o resto do ecrã fica para ele). */
class AmbientPage(kit: Kit, home: Home, key: String) : Page(kit) {
    override val view: View = FrameLayout(kit.ctx).apply {
        val spec = AmbientSpec.of(home.layout, key)
        if (spec == null) addView(kit.text(Tr.s("Este ecrã já não existe no layout.", "This screen is no longer in the layout."), 18f, kit.muted), FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER))
        else addView(AmbientView(kit, spec, home.layout?.clock24h ?: true, home.settings.optString("weather_entity", "")), FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = dockSpace(kit) })
    }
}
