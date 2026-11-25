// Pine3 Wall - module chores on the web: who does what on each weekday. Only the list of what is defined (no "done").
// Settings > Chores edits the tasks and the weekly grid; the "Today" card, the week page and the ambient face show them.

import { t, locale } from "./i18n.js";
import { esc } from "./const.js";

export const CHORE_ICONS = [
  "mdi:silverware-fork-knife", "mdi:silverware-clean", "mdi:dishwasher", "mdi:trash-can-outline", "mdi:broom", "mdi:robot-vacuum",
  "mdi:washing-machine", "mdi:tshirt-crew", "mdi:bed-outline", "mdi:dog", "mdi:cat", "mdi:paw", "mdi:watering-can", "mdi:flower",
  "mdi:cart", "mdi:basket", "mdi:food-apple", "mdi:book-open-variant", "mdi:toothbrush", "mdi:checkbox-marked-circle-outline",
];
export const MOMENTS = [["day", "Todo o dia"], ["morning", "Manhã"], ["afternoon", "Tarde"], ["evening", "Noite"]];
const DEFAULT_ICON = "mdi:checkbox-marked-circle-outline";
const DAYS = [1, 2, 3, 4, 5, 6, 0]; // Monday first, as the schedule

/** Monday = 0 … Sunday = 6 (the schedule's index). */
export const weekday = (d = new Date()) => (d.getDay() + 6) % 7;
const dayName = (i, style = "short") => { const d = new Date(2026, 0, 5 + i); return d.toLocaleDateString(locale(), { weekday: style }); };

/** The current moment of the day, with the integration's hours. */
export function momentNow(ch, now = new Date()) {
  const h = now.getHours();
  for (const [k, [a, b]] of Object.entries(ch?.moments || { morning: [5, 12], afternoon: [12, 19], evening: [19, 24] })) if (h >= a && h < b) return k;
  return "evening";
}

/** The tasks of weekday [wd] with their people. */
export function forDay(ch, wd) {
  return (ch?.tasks || []).map((tk) => ({ ...tk, people: ch.schedule?.[tk.id]?.[wd] || [] })).filter((tk) => tk.people.length);
}

const people = (p) => (p._model?.people || []).filter((x) => !x.hidden);
const av = (x, size = 34) => `<span class="ch-av" style="width:${size}px;height:${size}px;background-color:${x.color};${x.picture ? `background-image:url('${esc(x.picture)}')` : ""}">${x.picture ? "" : esc((x.alias || "?")[0])}</span>`;

function chip(tk, now) {
  const hot = tk.moment && tk.moment !== "day" && tk.moment === now;
  return `<span class="ch-task ${hot ? "now" : ""}"><ha-icon icon="${esc(tk.icon || DEFAULT_ICON)}"></ha-icon>${esc(tk.name)}</span>`;
}

/** Today's chores, one row per person (only [person]'s when given). */
function todayRows(p, person = "") {
  const ch = p._model?.chores, tasks = forDay(ch, weekday()), now = momentNow(ch);
  const rows = people(p).filter((x) => !person || x.id === person).map((x) => {
    const mine = tasks.filter((tk) => tk.people.includes(x.id));
    return mine.length ? `<div class="ch-row">${av(x)}<b>${esc(x.alias)}</b><div class="ch-tasks">${mine.map((tk) => chip(tk, now)).join("")}</div></div>` : "";
  }).join("");
  return rows || `<small class="ch-none">${t("Sem tarefas hoje.")}</small>`;
}

// ---------------------------------------------------------------- "Today" card

export function choresCard(p, spec = {}) {
  const who = spec.person ? (p._model?.people || []).find((x) => x.id === spec.person) : null;
  const title = who ? t("Tarefas: {n}", { n: who.alias }) : t("Tarefas de hoje");
  const body = p._model?.chores ? todayRows(p, spec.person || "") : `<small>${t("Define as tarefas nas Definições → Tarefas.")}</small>`;
  return `<div class="rc glass chcard ${spec.size === "big" ? "big" : ""}" data-nav="chores"><div class="row1"><div class="ri"><ha-icon icon="mdi:broom"></ha-icon></div><div class="nm">${esc(title)}</div></div>
    <div class="ch-list">${body}</div></div>`;
}

// ---------------------------------------------------------------- the week page

export function choresView(p) {
  const ch = p._model?.chores;
  if (!ch) {
    return `<div class="panel glass" style="max-width:640px"><h3 style="margin-top:0">${t("Tarefas")}</h3><p>${t("Ainda não há tarefas. Em Definições → Tarefas cria as tarefas da casa e escolhe quem faz cada uma em cada dia da semana.")}</p>
      <button class="pill solid" data-chsettings><ha-icon icon="mdi:cog-outline"></ha-icon>${t("Abrir as definições")}</button></div>`;
  }
  const today = weekday(), fam = people(p), byId = Object.fromEntries(fam.map((x) => [x.id, x]));
  const head = `<div class="ch-h"></div>${DAYS.map((_, i) => `<div class="ch-h ${i === today ? "today" : ""}">${esc(dayName(i))}</div>`).join("")}`;
  const rows = ch.tasks.map((tk) => `<div class="ch-tn"><ha-icon icon="${esc(tk.icon || DEFAULT_ICON)}"></ha-icon><span>${esc(tk.name)}${tk.moment && tk.moment !== "day" ? `<small>${esc(t(MOMENTS.find(([k]) => k === tk.moment)[1]))}</small>` : ""}</span></div>
    ${DAYS.map((_, i) => `<div class="ch-cell ${i === today ? "today" : ""}">${(ch.schedule[tk.id]?.[i] || []).map((id) => byId[id]).filter(Boolean).map((x) => av(x, 30)).join("")}</div>`).join("")}`).join("");
  return `<div class="ch">
    <div class="ch-hd"><div class="t">${t("Tarefas")}</div></div>
    <div class="panel glass ch-today"><div class="eyebrow">${esc(t("Hoje"))} · ${esc(dayName(today, "long"))}</div><div class="ch-list">${todayRows(p)}</div></div>
    <div class="panel glass ch-week"><div class="ch-grid">${head}${rows}</div></div></div>`;
}

export function bindChores(p, root) {
  root.querySelectorAll("[data-chsettings]").forEach((el) => el.addEventListener("click", () => p._openSettings("chores")));
}

// ---------------------------------------------------------------- ambient face (editor preview)

export function faceHtml(p, fg, dim, acc) {
  const ch = p._model?.chores;
  if (!ch) return `<small style="color:${dim}">${t("Sem tarefas")}</small>`;
  const tasks = forDay(ch, weekday());
  const rows = people(p).map((x) => { const mine = tasks.filter((tk) => tk.people.includes(x.id)); return mine.length ? `<div style="color:${fg};font-size:15px"><b style="color:${x.color}">${esc(x.alias)}</b> ${esc(mine.map((tk) => tk.name).join(" · "))}</div>` : ""; }).join("");
  return `<small style="color:${acc};font-weight:600;letter-spacing:.1em">${esc(t("Tarefas").toUpperCase())}</small>${rows || `<small style="color:${dim}">${t("Sem tarefas hoje.")}</small>`}`;
}

// ---------------------------------------------------------------- Settings > Chores

export function choresSettings(p, d) {
  if (!p._model) return `<div class="panel glass"><p>${t("Esta secção precisa da integração Pine3 Wall instalada.")}</p></div>`;
  const c = { tasks: [], schedule: {}, ...(d.chores || {}) };
  const fam = people(p);
  const today = weekday();
  const tasks = c.tasks.map((tk, i) => `<div class="it ch-edit">
      <select data-chicon="${i}" class="ch-sel" title="${t("Ícone")}">${CHORE_ICONS.map((ic) => `<option value="${ic}" ${(tk.icon || DEFAULT_ICON) === ic ? "selected" : ""}>${esc(ic.slice(4))}</option>`).join("")}</select>
      <ha-icon icon="${esc(tk.icon || DEFAULT_ICON)}"></ha-icon>
      <input class="search" data-chname="${i}" value="${esc(tk.name)}" maxlength="60" style="flex:1;min-width:160px;height:40px">
      <select data-chmoment="${i}" class="ch-sel">${MOMENTS.map(([k, l]) => `<option value="${k}" ${(tk.moment || "day") === k ? "selected" : ""}>${esc(t(l))}</option>`).join("")}</select>
      <button class="mini" data-chmove="${i}:-1" ${i === 0 ? "disabled" : ""}><ha-icon icon="mdi:chevron-up"></ha-icon></button>
      <button class="mini" data-chmove="${i}:1" ${i === c.tasks.length - 1 ? "disabled" : ""}><ha-icon icon="mdi:chevron-down"></ha-icon></button>
      <button class="mini" data-chdel="${i}" title="${t("Apagar")}"><ha-icon icon="mdi:delete-outline"></ha-icon></button></div>`).join("");
  const grid = c.tasks.length && fam.length ? `<div class="ch-grid edit">
      <div class="ch-h"></div>${DAYS.map((_, i) => `<div class="ch-h ${i === today ? "today" : ""}">${esc(dayName(i))}</div>`).join("")}
      ${c.tasks.map((tk) => `<div class="ch-tn"><ha-icon icon="${esc(tk.icon || DEFAULT_ICON)}"></ha-icon><span>${esc(tk.name)}</span></div>
        ${DAYS.map((_, i) => `<div class="ch-cell ${i === today ? "today" : ""}">${fam.map((x) => {
          const on = (c.schedule[tk.id]?.[i] || []).includes(x.id);
          return `<button class="ch-pick ${on ? "on" : ""}" data-chpick="${esc(tk.id)}|${i}|${esc(x.id)}" title="${esc(x.alias)}" style="--c:${x.color}">${esc((x.alias || "?")[0])}</button>`;
        }).join("")}</div>`).join("")}`).join("")}</div>` : "";
  return `<div class="panel glass"><h3 style="margin-top:0">${t("Tarefas da casa")}</h3>
      <p>${t("As tarefas e quem as faz em cada dia da semana. Aparecem no tablet (cartão \"Tarefas de hoje\", página Tarefas e ecrã ambiente) e cada pessoa tem um sensor no Home Assistant com as tarefas de hoje. Não há \"feito\": é só a lista do que está combinado.")}</p>
      <div class="list big">${tasks || `<small>${t("Ainda não há tarefas.")}</small>`}</div>
      <div class="row" style="justify-content:flex-start;margin-top:12px;gap:10px;flex-wrap:wrap">
        <input class="search" data-chnew placeholder="${t("Nova tarefa (ex.: Pôr a mesa)")}" maxlength="60" style="width:280px;height:42px">
        <button class="pill" data-chadd><ha-icon icon="mdi:plus"></ha-icon>${t("Acrescentar")}</button></div></div>
    ${c.tasks.length ? `<div class="panel glass"><h3 style="margin-top:0">${t("Quem faz o quê")}</h3>
      ${fam.length ? `<p>${t("Toca nas iniciais para escolher quem faz cada tarefa em cada dia.")}</p>${grid}` : `<p>${t("Não há pessoas no Home Assistant. Cria-as em Definições → Pessoas.")}</p>`}</div>` : ""}`;
}

export function bindChoresSettings(p, root, d) {
  const c = () => (d.chores = { tasks: [], schedule: {}, ...(d.chores || {}) });
  const on = (sel, fn, ev = "click") => root.querySelectorAll(sel).forEach((el) => el.addEventListener(ev, () => fn(el)));
  const upd = () => p._render();
  const add = () => {
    const inp = root.querySelector("[data-chnew]"), name = inp?.value.trim().slice(0, 60);
    if (!name) return;
    const x = c();
    x.tasks = [...x.tasks, { id: `t_${Date.now().toString(36)}`, name, icon: DEFAULT_ICON, moment: "day" }];
    upd();
  };
  on("[data-chadd]", add);
  const inp = root.querySelector("[data-chnew]");
  if (inp) inp.addEventListener("keydown", (ev) => { if (ev.key === "Enter") add(); });
  on("[data-chname]", (el) => { const v = el.value.trim().slice(0, 60); if (!v) return; const x = c(); x.tasks = x.tasks.map((tk, i) => (i === +el.dataset.chname ? { ...tk, name: v } : tk)); upd(); }, "change");
  on("[data-chicon]", (el) => { const x = c(); x.tasks = x.tasks.map((tk, i) => (i === +el.dataset.chicon ? { ...tk, icon: el.value } : tk)); upd(); }, "change");
  on("[data-chmoment]", (el) => { const x = c(); x.tasks = x.tasks.map((tk, i) => (i === +el.dataset.chmoment ? { ...tk, moment: el.value } : tk)); upd(); }, "change");
  on("[data-chmove]", (el) => {
    const [i, dir] = el.dataset.chmove.split(":").map(Number), x = c(), list = [...x.tasks], j = i + dir;
    if (j < 0 || j >= list.length) return;
    [list[i], list[j]] = [list[j], list[i]]; x.tasks = list; upd();
  });
  on("[data-chdel]", (el) => {
    const x = c(), tk = x.tasks[+el.dataset.chdel];
    if (!tk || !confirm(t("Apagar a tarefa \"{n}\"?", { n: tk.name }))) return;
    x.tasks = x.tasks.filter((_, i) => i !== +el.dataset.chdel);
    const s = { ...x.schedule }; delete s[tk.id]; x.schedule = s; upd();
  });
  on("[data-chpick]", (el) => {
    const [id, day, pid] = el.dataset.chpick.split("|"), x = c();
    const week = (x.schedule[id] || [[], [], [], [], [], [], []]).map((l) => [...l]);
    const list = week[+day];
    week[+day] = list.includes(pid) ? list.filter((q) => q !== pid) : [...list, pid];
    x.schedule = { ...x.schedule, [id]: week };
    if (week.every((l) => !l.length)) { const s = { ...x.schedule }; delete s[id]; x.schedule = s; }
    upd();
  });
}

export const cssChores = `
.ch{display:flex;flex-direction:column;gap:16px}
.ch-hd .t{font-family:var(--display);font-size:34px;font-weight:500}
.ch-av{border-radius:50%;display:inline-grid;place-items:center;font-weight:600;color:var(--ink);background-size:cover;background-position:center;flex:none;font-size:14px}
.ch-list{display:flex;flex-direction:column;gap:12px;margin-top:12px}
.ch-row{display:flex;align-items:center;gap:12px;flex-wrap:wrap}
.ch-row>b{min-width:70px}
.ch-tasks{display:flex;flex-wrap:wrap;gap:8px;flex:1}
.ch-task{white-space:nowrap;display:inline-flex;align-items:center;gap:6px;padding:6px 12px;border-radius:999px;background:rgba(255,255,255,.08);font-size:15px;--mdc-icon-size:18px}
.ch-task.now{background:var(--amber);color:var(--ink);font-weight:600}
.ch-none{color:var(--muted)}
.chcard{cursor:pointer}
.chcard .row1{display:flex;align-items:center;gap:12px;justify-content:flex-start}
.chcard .nm{font-family:var(--display);font-size:20px}
.chcard .ch-task{font-size:13px;padding:4px 10px}
.chcard .ch-tasks{flex-basis:100%}
.chcard .ch-row{gap:8px}
.ch-grid{display:grid;grid-template-columns:minmax(170px,1.4fr) repeat(7,minmax(64px,1fr));gap:6px;overflow-x:auto}
.ch-h{font-size:13px;color:var(--muted);text-transform:uppercase;letter-spacing:.1em;text-align:center;padding:4px 0}
.ch-h.today{color:var(--amber);font-weight:700}
.ch-tn{display:flex;align-items:center;gap:10px;padding:8px 4px;--mdc-icon-size:22px}
.ch-tn span{display:flex;flex-direction:column}
.ch-tn small{color:var(--muted);font-size:12px}
.ch-cell{display:flex;flex-wrap:wrap;gap:4px;align-items:center;justify-content:center;border-radius:14px;background:rgba(255,255,255,.04);padding:6px;min-height:42px}
.ch-cell.today{background:rgba(246,184,63,.14);outline:1px solid var(--amber)}
.ch-pick{all:unset;width:28px;height:28px;border-radius:50%;display:grid;place-items:center;font-size:12px;font-weight:700;cursor:pointer;border:2px solid var(--c);color:var(--c);opacity:.45}
.ch-pick.on{background:var(--c);color:var(--ink);opacity:1}
.ch-edit{flex-wrap:wrap;gap:10px}
.ch-sel{all:unset;box-sizing:border-box;height:40px;padding:0 12px;border-radius:12px;background:rgba(255,255,255,.08);border:1px solid var(--line);color:inherit;font-size:14px;cursor:pointer;max-width:170px}
`;
