// Pine3 Wall - "O dia da família": one column per person with today's (or tomorrow's) events and chores, by time.
// It only joins what the modules calendar and chores already send in the home model.

import { t, locale } from "./i18n.js";
import { esc } from "./const.js";
import { eventsOn, evTitle, ymd } from "./calendar.js";
import { forDay, momentNow, weekday, MOMENTS } from "./chores.js";

const pad = (n) => String(n).padStart(2, "0");
const MOMENT_AT = { day: "00:00", morning: "08:00", afternoon: "14:00", evening: "19:30" };

/** Each person's items of the day (events and chores, by time) and the family's events. */
export function familyDay(model, date = new Date()) {
  const D = ymd(date), wd = weekday(date);
  const isToday = D === ymd(new Date());
  const nowKey = `${D}T${pad(new Date().getHours())}:${pad(new Date().getMinutes())}`;
  const now = isToday ? momentNow(model?.chores) : "";
  const events = eventsOn(model?.calendar, D);
  const chores = forDay(model?.chores, wd);
  const ev = (e) => ({
    kind: "event", id: e.id, title: evTitle(e), sort: e.all_day ? "00:00" : e.start.slice(0, 10) < D ? "00:01" : e.start.slice(11, 16),
    label: e.all_day ? t("Todo o dia") : e.start.slice(0, 10) < D ? "…" : e.start.slice(11, 16), icon: "mdi:calendar-blank-outline",
    past: isToday && !e.all_day && e.end < nowKey,
  });
  const ch = (tk) => ({
    kind: "chore", id: tk.id, title: tk.name, sort: MOMENT_AT[tk.moment || "day"] + "~", icon: tk.icon || "mdi:checkbox-marked-circle-outline",
    label: t((MOMENTS.find(([k]) => k === (tk.moment || "day")) || MOMENTS[0])[1]), hot: !!now && tk.moment && tk.moment !== "day" && tk.moment === now,
  });
  const byTime = (a, b) => (a.sort < b.sort ? -1 : a.sort > b.sort ? 1 : 0);
  const people = (model?.people || []).filter((x) => !x.hidden);
  const cols = people.map((x) => ({
    person: x,
    items: [...events.filter((e) => e.people.includes(x.id)).map(ev), ...chores.filter((tk) => tk.people.includes(x.id)).map(ch)].sort(byTime),
  }));
  const family = events.filter((e) => !e.people.length || e.people.every((id) => !people.some((x) => x.id === id))).map(ev).sort(byTime);
  return { cols, family, date: D };
}

function item(it) {
  return `<div class="fd-it ${it.kind} ${it.hot ? "now" : ""} ${it.past ? "past" : ""}"><em>${esc(it.label)}</em><ha-icon icon="${esc(it.icon)}"></ha-icon><span>${esc(it.title)}</span></div>`;
}

export function familyView(p) {
  const m = p._model || {};
  if (!m.calendar && !m.chores) {
    return `<div class="panel glass" style="max-width:640px"><h3 style="margin-top:0">${t("O dia da família")}</h3><p>${t("Junta o calendário e as tarefas de cada pessoa. Escolhe o calendário em Definições → Calendário e cria as tarefas em Definições → Tarefas.")}</p></div>`;
  }
  const off = p._famDay || 0, date = new Date(); date.setDate(date.getDate() + off);
  const day = familyDay(m, date);
  const famCol = day.family.length ? [{ person: { alias: t("Família"), color: m.calendar?.color || "var(--amber)" }, items: day.family, family: true }] : [];
  const cols = [...day.cols, ...famCol];
  const when = date.toLocaleDateString(locale(), { weekday: "long", day: "numeric", month: "long" });
  return `<div class="fd">
    <div class="fd-hd"><div><div class="eyebrow">${esc(when)}</div><div class="t">${t("O dia da família")}</div></div><span class="sp"></span>
      <div class="seg">${[[0, "Hoje"], [1, "Amanhã"]].map(([k, l]) => `<button class="pill ${off === k ? "solid" : ""}" data-famday="${k}">${t(l)}</button>`).join("")}</div></div>
    <div class="fd-cols" style="--n:${cols.length}">${cols.map((c) => `<div class="fd-col glass">
      <div class="fd-colhd"><span class="fd-av ${c.family ? "" : p._s?.(c.person.id)?.state === "home" ? "home" : "away"}" style="background-color:${c.person.color};${c.person.picture ? `background-image:url('${esc(c.person.picture)}')` : ""}">${c.person.picture ? "" : esc((c.person.alias || "?")[0])}</span>
        <b>${esc(c.person.alias)}</b></div>
      ${c.items.map(item).join("") || `<small class="fd-free">${t("Dia livre")}</small>`}</div>`).join("")}</div></div>`;
}

export function bindFamily(p, root) {
  root.querySelectorAll("[data-famday]").forEach((el) => el.addEventListener("click", () => { p._famDay = Number(el.dataset.famday); p._render(); }));
}

/** Ambient face (editor preview): one line per person. */
export function faceHtml(p, fg, dim, acc) {
  const day = familyDay(p._model || {});
  const rows = day.cols.filter((c) => c.items.length).map((c) => `<div style="color:${fg};font-size:15px"><b style="color:${c.person.color}">${esc(c.person.alias)}</b> ${esc(c.items.map((i) => (i.kind === "event" && i.label.includes(":") ? i.label + " " : "") + i.title).join(" · "))}</div>`).join("");
  return `<small style="color:${acc};font-weight:600;letter-spacing:.1em">${esc(t("O dia da família").toUpperCase())}</small>${rows || `<small style="color:${dim}">${t("Dia livre")}</small>`}`;
}

export const cssFamily = `
.fd{display:flex;flex-direction:column;gap:16px}
.fd-hd{display:flex;align-items:flex-end;gap:12px;flex-wrap:wrap}
.fd-hd .t{font-family:var(--display);font-size:38px;font-weight:500;margin-top:6px}
.fd-hd .sp{flex:1}
.fd-cols{display:grid;grid-template-columns:repeat(var(--n),minmax(200px,1fr));gap:14px;overflow-x:auto}
.fd-col{border-radius:24px;padding:16px;display:flex;flex-direction:column;gap:8px;min-height:300px}
.fd-colhd{display:flex;align-items:center;gap:12px;margin-bottom:6px}
.fd-colhd b{font-family:var(--display);font-size:22px;font-weight:500}
.fd-av{width:46px;height:46px;border-radius:50%;display:grid;place-items:center;font-weight:600;font-size:18px;color:var(--ink);background-size:cover;background-position:center;flex:none;border:2px solid transparent}
.fd-av.home{border-color:#34c759}
.fd-av.away{opacity:.6}
.fd-it{text-align:left;display:grid;grid-template-columns:58px 22px 1fr;gap:8px;align-items:center;padding:9px 10px;border-radius:14px;background:rgba(255,255,255,.06);font-size:15px;--mdc-icon-size:20px}
.fd-it em{font-style:normal;color:var(--muted);font-size:13px;font-variant-numeric:tabular-nums}
.fd-it.chore{background:rgba(255,255,255,.03);border:1px dashed var(--line)}
.fd-it.now{background:var(--amber);color:var(--ink);border-color:transparent;font-weight:600}
.fd-it.now em{color:var(--ink)}
.fd-it.past{opacity:.45}
.fd-free{color:var(--muted)}
`;
