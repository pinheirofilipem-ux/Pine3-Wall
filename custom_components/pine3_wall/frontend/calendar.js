// Pine3 Wall - module calendar on the web: the agenda page (Today by person, Week, Month), the "Upcoming" card, the
// ambient faces and Settings > Calendar. The events come in the home model ("calendar"), already in local time and
// with their people worked out from the title by the integration (family_calendar.py), as the app gets them.

import { t, tp, locale } from "./i18n.js";
import { esc, ACCENTS } from "./const.js";

const pad = (n) => String(n).padStart(2, "0");
export const ymd = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const parseDay = (s) => { const [y, m, d] = s.split("-").map(Number); return new Date(y, m - 1, d); };
const addDays = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };
const monday = (d) => addDays(d, -((d.getDay() + 6) % 7));
const nowKey = () => { const d = new Date(); return `${ymd(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}`; };

/** True when the event happens (at least partly) on day [D] ("YYYY-MM-DD"). */
export function onDay(e, D) {
  const s = e.start.slice(0, 10), en = e.end.slice(0, 10);
  if (e.all_day) return D === s || (s <= D && D < en);
  if (D === s) return true;
  return s < D && (D < en || (D === en && e.end.slice(11) > "00:00"));
}
export const eventsOn = (cal, D) => (cal?.events || []).filter((e) => onDay(e, D));

/** Events not over yet, the next [n] (only those of [person] and the family's, when given). */
export function upcoming(cal, n = 4, person = "") {
  const now = nowKey(), today = now.slice(0, 10);
  return (cal?.events || []).filter((e) => (e.all_day ? e.end > today || e.start === today : e.end > now)
    && (!person || !e.people.length || e.people.includes(person))).slice(0, n);
}

export const evTitle = (e) => (e.private ? t("Ocupado") : e.title || t("(sem título)"));
const famColor = (cal) => cal?.color || "var(--amber)";
const peopleOf = (p) => (p._model?.people || []).filter((x) => !x.hidden);
const colorOf = (p, cal, e) => (e.people.length ? (p._model?.people || []).find((x) => x.id === e.people[0])?.color : null) || famColor(cal);

function dayName(D, opts = { weekday: "long", day: "numeric", month: "long" }) {
  const today = ymd(new Date());
  if (D === today) return t("Hoje");
  if (D === ymd(addDays(new Date(), 1))) return t("Amanhã");
  return parseDay(D).toLocaleDateString(locale(), opts);
}
const time = (s) => s.slice(11, 16);

/** "Hoje · 17:30", "Amanhã · Todo o dia", "sexta, 28 de novembro · 09:00–10:00". */
export function whenText(e, withEnd = false) {
  const D = e.start.slice(0, 10), today = ymd(new Date());
  const day = D < today && onDay(e, today) ? t("Hoje") : dayName(D);
  if (e.all_day) {
    const last = ymd(addDays(parseDay(e.end), -1));
    return last > D ? `${day} – ${dayName(last, { day: "numeric", month: "short" })}` : `${day} · ${t("Todo o dia")}`;
  }
  return `${day} · ${time(e.start)}${withEnd && e.end.slice(0, 10) === D ? `–${time(e.end)}` : ""}`;
}

function dots(p, cal, e) {
  const fam = peopleOf(p);
  const list = e.people.length ? e.people.map((id) => fam.find((x) => x.id === id)).filter(Boolean) : [];
  if (!list.length) return `<span class="cal-who" style="--c:${famColor(cal)}">${esc(t("Família"))}</span>`;
  return list.map((x) => `<span class="cal-who" style="--c:${x.color}">${esc(x.alias)}</span>`).join("");
}

// ---------------------------------------------------------------- "Upcoming" card (home screen grid)

export function upcomingCard(p, spec = {}) {
  const cal = p._model?.calendar;
  const person = spec.person || "";
  const who = person ? (p._model?.people || []).find((x) => x.id === person) : null;
  const title = who ? t("Próximos: {n}", { n: who.alias }) : t("Próximos");
  let body;
  if (!cal) body = `<small>${t("Escolhe o calendário nas Definições → Calendário.")}</small>`;
  else {
    const list = upcoming(cal, spec.count || (spec.size === "big" ? 6 : 4), person);
    body = list.map((e) => `<div class="cal-row" data-calev="${esc(e.id)}"><i style="background:${colorOf(p, cal, e)}"></i>
      <div><b>${esc(evTitle(e))}</b><small>${esc(whenText(e))}</small></div></div>`).join("") || `<small>${t("Nada marcado.")}</small>`;
  }
  return `<div class="rc glass calcard ${spec.size === "big" ? "big" : ""}" data-nav="calendar"><div class="row1"><div class="ri"><ha-icon icon="mdi:calendar-month-outline"></ha-icon></div><div class="nm">${esc(title)}</div></div>
    <div class="cal-list">${body}</div></div>`;
}

// ---------------------------------------------------------------- the agenda page

function evLine(p, cal, e, D) {
  const t0 = e.all_day ? "" : e.start.slice(0, 10) === D ? time(e.start) : "…";
  return `<div class="cal-ev ${e.all_day ? "allday" : ""}" data-calev="${esc(e.id)}" style="--c:${colorOf(p, cal, e)}">
    ${t0 ? `<em>${t0}</em>` : ""}<span>${esc(evTitle(e))}</span></div>`;
}

function dayView(p, cal, D) {
  const evs = eventsOn(cal, D), fam = peopleOf(p);
  const cols = [...fam.map((x) => [x.id, x.alias, x.color, evs.filter((e) => e.people.includes(x.id))]),
    ["", t("Família"), famColor(cal), evs.filter((e) => !e.people.length || e.people.every((id) => !fam.some((x) => x.id === id)))]];
  return `<div class="cal-day" style="--n:${cols.length}">${cols.map(([id, name, color, list]) => `<div class="cal-col glass">
    <div class="cal-colhd"><span class="cal-av" style="background:${color}">${esc((name || "?")[0])}</span><b>${esc(name)}</b><small>${list.length || ""}</small></div>
    ${list.map((e) => evLine(p, cal, e, D)).join("") || `<small class="cal-none">${t("Livre")}</small>`}</div>`).join("")}</div>`;
}

function weekView(p, cal, D) {
  const start = monday(parseDay(D)), today = ymd(new Date());
  return `<div class="cal-week">${[...Array(7)].map((_, i) => {
    const d = addDays(start, i), k = ymd(d);
    return `<div class="cal-col glass ${k === today ? "today" : ""}" data-calday="${k}">
      <div class="cal-colhd"><b>${esc(d.toLocaleDateString(locale(), { weekday: "short" }))}</b><span class="cal-num">${d.getDate()}</span></div>
      ${eventsOn(cal, k).map((e) => evLine(p, cal, e, k)).join("")}</div>`;
  }).join("")}</div>`;
}

function monthView(p, cal, D) {
  const cur = parseDay(D), first = new Date(cur.getFullYear(), cur.getMonth(), 1), start = monday(first), today = ymd(new Date());
  const heads = [...Array(7)].map((_, i) => `<div class="cal-mh">${esc(addDays(start, i).toLocaleDateString(locale(), { weekday: "short" }))}</div>`).join("");
  const weeks = Math.ceil(((first.getDay() + 6) % 7 + new Date(cur.getFullYear(), cur.getMonth() + 1, 0).getDate()) / 7);
  const cells = [...Array(weeks * 7)].map((_, i) => {
    const d = addDays(start, i), k = ymd(d), evs = eventsOn(cal, k);
    return `<div class="cal-cell glass ${d.getMonth() !== cur.getMonth() ? "out" : ""} ${k === today ? "today" : ""}" data-calday="${k}">
      <span class="cal-num">${d.getDate()}</span>
      ${evs.slice(0, 3).map((e) => `<div class="cal-mev" style="--c:${colorOf(p, cal, e)}">${esc(evTitle(e))}</div>`).join("")}
      ${evs.length > 3 ? `<small>+${evs.length - 3}</small>` : ""}</div>`;
  }).join("");
  return `<div class="cal-month">${heads}${cells}</div>`;
}

function detail(p, cal, id) {
  const e = (cal.events || []).find((x) => x.id === id);
  if (!e) return "";
  return `<div class="cal-pop-bg" data-calclose><div class="cal-pop glass" style="--c:${colorOf(p, cal, e)}">
    <div class="cal-pophd"><b>${esc(evTitle(e))}</b><div class="iconbtn" data-calclose><ha-icon icon="mdi:close"></ha-icon></div></div>
    <p><ha-icon icon="mdi:clock-outline"></ha-icon>${esc(whenText(e, true))}</p>
    ${e.location ? `<p><ha-icon icon="mdi:map-marker-outline"></ha-icon>${esc(e.location)}</p>` : ""}
    <p class="cal-whos">${dots(p, cal, e)}</p></div></div>`;
}

export function agendaView(p) {
  const cal = p._model?.calendar;
  if (!cal) {
    return `<div class="panel glass cal-empty"><h3 style="margin-top:0">${t("Agenda")}</h3><p>${t("Ainda não há calendário escolhido. Em Definições → Calendário escolhe o calendário da família (por exemplo o do iCloud, ligado pela integração CalDAV).")}</p>
      <button class="pill solid" data-calsettings><ha-icon icon="mdi:cog-outline"></ha-icon>${t("Abrir as definições")}</button></div>`;
  }
  const sec = p._model?.layout?.pages?.calendar?.sections?.find((s) => s.type === "agenda");
  const view = p._calView || sec?.view || "day";
  const D = p._calDay || ymd(new Date());
  const cur = parseDay(D);
  const title = view === "month" ? cur.toLocaleDateString(locale(), { month: "long", year: "numeric" })
    : view === "week" ? `${monday(cur).toLocaleDateString(locale(), { day: "numeric", month: "short" })} – ${addDays(monday(cur), 6).toLocaleDateString(locale(), { day: "numeric", month: "short" })}`
    : [D === ymd(new Date()) || D === ymd(addDays(new Date(), 1)) ? dayName(D) : "", cur.toLocaleDateString(locale(), { weekday: "long", day: "numeric", month: "long" })].filter(Boolean).join(" · ");
  const body = view === "month" ? monthView(p, cal, D) : view === "week" ? weekView(p, cal, D) : dayView(p, cal, D);
  return `<div class="cal">
    <div class="cal-hd"><div class="t">${esc(title.charAt(0).toUpperCase() + title.slice(1))}</div><span class="sp"></span>
      <div class="seg">${[["day", "Dia"], ["week", "Semana"], ["month", "Mês"]].map(([k, l]) => `<button class="pill ${view === k ? "solid" : ""}" data-calview="${k}">${t(l)}</button>`).join("")}</div>
      <div class="iconbtn" data-calstep="-1"><ha-icon icon="mdi:chevron-left"></ha-icon></div>
      <button class="pill" data-calstep="0">${t("Hoje")}</button>
      <div class="iconbtn" data-calstep="1"><ha-icon icon="mdi:chevron-right"></ha-icon></div></div>
    ${cal.error ? `<div class="cal-err">${t("Não foi possível ler o calendário: {e}", { e: esc(cal.error) })}</div>` : ""}
    ${body}${p._calEv ? detail(p, cal, p._calEv) : ""}</div>`;
}

export function bindAgenda(p, root) {
  const on = (sel, fn) => root.querySelectorAll(sel).forEach((el) => el.addEventListener("click", (ev) => fn(el, ev)));
  on("[data-calview]", (el) => { p._calView = el.dataset.calview; p._render(); });
  on("[data-calstep]", (el) => {
    const n = Number(el.dataset.calstep), view = p._calView || "day", d = parseDay(p._calDay || ymd(new Date()));
    if (!n) p._calDay = null;
    else if (view === "month") p._calDay = ymd(new Date(d.getFullYear(), d.getMonth() + n, 1));
    else p._calDay = ymd(addDays(d, n * (view === "week" ? 7 : 1)));
    p._render();
  });
  on("[data-calday]", (el, ev) => { if (ev.target.closest("[data-calev]")) return; p._calDay = el.dataset.calday; p._calView = "day"; p._render(); });
  on("[data-calev]", (el, ev) => { ev.stopPropagation(); p._calEv = el.dataset.calev; p._render(); });
  on("[data-calclose]", (el, ev) => { if (ev.target !== el && !el.classList.contains("iconbtn")) return; p._calEv = null; p._render(); });
  on("[data-calsettings]", () => p._openSettings("calendar"));
}

// ---------------------------------------------------------------- ambient faces (preview in the editor)

export function faceHtml(p, type, fg, dim, acc) {
  const cal = p._model?.calendar;
  if (!cal) return `<small style="color:${dim}">${t("Sem calendário")}</small>`;
  if (type === "next_event") {
    const e = upcoming(cal, 1)[0];
    if (!e) return `<small style="color:${dim}">${t("Nada marcado.")}</small>`;
    return `<small style="color:${acc};font-weight:600;letter-spacing:.1em">${esc(t("Próximo").toUpperCase())}</small><div class="le-at" style="color:${fg};font-size:28px">${esc(evTitle(e))}</div><small style="color:${dim}">${esc(whenText(e))}</small>`;
  }
  const list = eventsOn(cal, ymd(new Date())).slice(0, 5);
  return `<small style="color:${acc};font-weight:600;letter-spacing:.1em">${esc(t("Hoje").toUpperCase())}</small>${list.map((e) => `<div style="color:${fg};font-size:15px">${e.all_day ? "" : `<b style="color:${dim}">${time(e.start)}</b> `}${esc(evTitle(e))}</div>`).join("") || `<small style="color:${dim}">${t("Nada marcado.")}</small>`}`;
}

// ---------------------------------------------------------------- Settings > Calendar

export function calendarSettings(p, d) {
  if (!p._model) return `<div class="panel glass"><p>${t("Esta secção precisa da integração Pine3 Wall instalada.")}</p></div>`;
  const c = { entities: [], private: [], days: 14, ...(d.calendar || {}) };
  const all = Object.keys(p._hass.states).filter((i) => i.startsWith("calendar.")).sort();
  const cal = p._model.calendar;
  const test = p._calTest;
  return `<div class="panel glass"><h3 style="margin-top:0">${t("Calendário da família")}</h3>
      <p>${t("Um calendário partilhado \"Família\" no iCloud, ligado ao Home Assistant pela integração CalDAV (servidor https://caldav.icloud.com, o teu Apple ID e uma palavra-passe específica de app). Também serve um calendário Google, Outlook ou o local do HA.")}</p>
      ${all.length ? `<div class="list big">${all.map((id) => { const on = c.entities.includes(id), priv = c.private.includes(id); return `<div class="it" style="flex-wrap:wrap;gap:10px">
        <ha-icon icon="mdi:calendar"></ha-icon><span>${esc(p._name(id))} <small>${esc(id)}</small></span>
        ${on ? `<label style="display:flex;gap:8px;align-items:center"><small>${t("Só \"Ocupado\"")}</small><div class="tg ${priv ? "on" : ""}" data-calpriv="${esc(id)}"><i></i></div></label>` : ""}
        <div class="tg ${on ? "on" : ""}" data-calent="${esc(id)}"><i></i></div></div>`; }).join("")}</div>`
        : `<p><b>${t("Não há entidades calendar.* no Home Assistant. Acrescenta a integração CalDAV (ou outra de calendários) e volta aqui.")}</b></p>`}
    </div>
    <div class="panel glass"><div class="opts">
      <div class="opt col"><div><b>${t("Dias à frente")}</b><small>${t("Quanto do calendário se lê (o mês e a semana atuais vêm sempre).")}</small></div>
        <div class="seg">${[[7, t("7 dias")], [14, t("14 dias")], [31, t("1 mês")], [62, t("2 meses")]].map(([v, l]) => `<button class="pill ${c.days === v ? "solid" : ""}" data-caldays="${v}">${l}</button>`).join("")}</div></div>
      <div class="opt col"><div><b>${t("Cor da família")}</b><small>${t("Para os eventos sem pessoa (de toda a família).")}</small></div>
        <div class="swr">${ACCENTS.map((col) => `<div class="swc ${col === c.color ? "act" : ""}" data-calcolor="${col}" style="background:${col}"></div>`).join("")}</div></div>
    </div></div>
    <div class="panel glass"><h3 style="margin-top:0">${t("De quem é cada evento")}</h3>
      <p>${t("Pelo título: o nome ou o nome curto da pessoa entre parênteses retos ou antes de dois pontos. Vários nomes, várias pessoas; sem nome, é da família.")}</p>
      <p><small>${esc("[Pedro] Natação · Pedro: Natação · Pedro e Ana: Jantar · Natação [Pedro] · [Família] Jantar na avó")}</small></p>
      <input class="search" data-caltest placeholder="${t("Experimenta um título…")}" value="${esc(test?.title || "")}" style="height:42px">
      ${test?.res ? `<p class="cal-whos" style="margin-top:10px">${dots(p, cal, { people: test.res.people })} <b>${esc(test.res.title)}</b></p>` : ""}
      ${cal ? `<h3>${t("Próximos eventos")}</h3><div class="cal-list">${upcoming(cal, 6).map((e) => `<div class="cal-row"><i style="background:${colorOf(p, cal, e)}"></i>
        <div><b>${esc(evTitle(e))}</b><small>${esc(whenText(e))}</small></div><span class="cal-whos">${dots(p, cal, e)}</span></div>`).join("") || `<small>${t("Nada marcado.")}</small>`}</div>
        <p><small>${tp(cal.events.length, "{n} evento lido", "{n} eventos lidos")}${cal.from ? ` · ${esc(cal.from)} → ${esc(cal.to)}` : ""}</small>${cal.error ? `<br><b>${esc(cal.error)}</b>` : ""}</p>
        <button class="pill" data-calrefresh><ha-icon icon="mdi:refresh"></ha-icon>${t("Ler agora")}</button>` : ""}
    </div>`;
}

export function bindCalendarSettings(p, root, d) {
  const on = (sel, fn) => root.querySelectorAll(sel).forEach((el) => el.addEventListener("click", () => fn(el)));
  const c = () => (d.calendar = { entities: [], private: [], days: 14, ...(d.calendar || {}) });
  const toggle = (list, id) => (list.includes(id) ? list.filter((x) => x !== id) : [...list, id]);
  on("[data-calent]", (el) => { const x = c(); x.entities = toggle(x.entities, el.dataset.calent).slice(0, 5); x.private = x.private.filter((i) => x.entities.includes(i)); p._render(); });
  on("[data-calpriv]", (el) => { const x = c(); x.private = toggle(x.private, el.dataset.calpriv); p._render(); });
  on("[data-calcolor]", (el) => { const x = c(); if (x.color === el.dataset.calcolor) delete x.color; else x.color = el.dataset.calcolor; p._render(); });
  on("[data-caldays]", (el) => { c().days = Number(el.dataset.caldays); p._render(); });
  on("[data-calrefresh]", async () => {
    try { p._model.calendar = await p._hass.callWS({ type: "pine3_wall/calendar/refresh" }); } catch (e) { console.error("Pine3 Wall", e); }
    p._render();
  });
  const inp = root.querySelector("[data-caltest]");
  if (inp) inp.addEventListener("change", async () => {
    const title = inp.value.trim();
    p._calTest = { title };
    if (title) try { [p._calTest.res] = await p._hass.callWS({ type: "pine3_wall/calendar/assign", titles: [title] }); } catch (e) { console.error("Pine3 Wall", e); }
    p._render();
  });
}

export const cssCalendar = `
.cal{display:flex;flex-direction:column;gap:16px;position:relative}
.cal-hd{display:flex;flex-wrap:wrap;align-items:center;gap:10px}
.cal-hd .t{font-family:var(--display);font-size:34px;font-weight:500}
.cal-hd .sp{flex:1}
.cal-err{padding:10px 16px;border-radius:14px;border:1px solid var(--amber);font-size:14px}
.cal-day{display:grid;grid-template-columns:repeat(var(--n),minmax(150px,1fr));gap:12px;overflow-x:auto}
.cal-week{display:grid;grid-template-columns:repeat(7,minmax(120px,1fr));gap:10px;overflow-x:auto}
.cal-col{border-radius:22px;padding:14px;display:flex;flex-direction:column;gap:8px;min-height:220px}
.cal-week .cal-col{cursor:pointer;min-height:320px}
.cal-col.today,.cal-cell.today{border-color:var(--amber)}
.cal-colhd{display:flex;align-items:center;gap:10px;margin-bottom:4px}
.cal-colhd b{font-size:16px;text-transform:capitalize}
.cal-colhd small{margin-left:auto;color:var(--muted)}
.cal-av{width:34px;height:34px;border-radius:50%;display:grid;place-items:center;font-weight:600;color:var(--ink);flex:none}
.cal-num{font-family:var(--display);font-size:22px;margin-left:auto}
.cal-ev{display:flex;gap:8px;align-items:baseline;padding:8px 10px;border-radius:12px;background:rgba(255,255,255,.06);border-left:4px solid var(--c);cursor:pointer;font-size:15px;line-height:1.25}
.cal-ev.allday{background:color-mix(in srgb,var(--c) 30%,transparent)}
.cal-ev em{font-style:normal;color:var(--muted);font-variant-numeric:tabular-nums;flex:none}
.cal-none{color:var(--muted)}
.cal-month{display:grid;grid-template-columns:repeat(7,1fr);gap:8px}
.cal-mh{font-size:13px;color:var(--muted);text-transform:uppercase;letter-spacing:.1em;padding:0 6px}
.cal-cell{border-radius:16px;padding:8px;min-height:92px;display:flex;flex-direction:column;gap:3px;cursor:pointer;overflow:hidden}
.cal-cell.out{opacity:.45}
.cal-cell .cal-num{font-size:17px;margin-left:0}
.cal-mev{font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;padding:1px 6px;border-radius:6px;background:color-mix(in srgb,var(--c) 35%,transparent)}
.cal-pop-bg{position:fixed;inset:0;background:rgba(0,0,0,.45);display:grid;place-items:center;z-index:50}
.cal-pop{border-radius:26px;padding:22px;width:min(420px,90vw);border-top:6px solid var(--c)}
.cal-pophd{display:flex;align-items:flex-start;gap:12px}
.cal-pophd b{font-family:var(--display);font-size:26px;font-weight:500;flex:1}
.cal-pop p{display:flex;gap:10px;align-items:center;margin:12px 0 0}
.cal-whos{display:flex;flex-wrap:wrap;gap:6px;align-items:center}
.cal-who{font-size:13px;font-weight:600;padding:3px 10px;border-radius:999px;background:var(--c);color:var(--ink)}
.calcard{cursor:pointer}
.calcard .row1{display:flex;align-items:center;gap:12px}
.calcard .nm{font-family:var(--display);font-size:20px}
.cal-list{display:flex;flex-direction:column;gap:10px;margin-top:12px}
.cal-row{display:flex;gap:12px;align-items:center}
.cal-row>i{width:6px;align-self:stretch;border-radius:3px;flex:none}
.cal-row>div{display:flex;flex-direction:column;min-width:0;flex:1}
.cal-row b{white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-weight:600}
.cal-row small{color:var(--muted)}
.calcard .cal-row small{color:inherit;opacity:.7}
.cal-empty{max-width:640px}
@media (max-width:700px){.cal-hd .t{font-size:26px;width:100%}.cal-cell{min-height:60px}.cal-mev{display:none}}
`;
