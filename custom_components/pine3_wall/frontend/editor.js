// Pine3 Wall - layout editor: builds what the tablets show (schema/layout.schema.json), with a preview drawn with the
// panel's own cards and the home's real states. Draft, publish, history, back to automatic, and a live preview on the tablets.

import { t } from "./i18n.js";
import { esc, dom } from "./const.js";

const clone = (o) => JSON.parse(JSON.stringify(o));

const FIXED_PAGES = [
  ["home", "mdi:home-outline", "Início"], ["rooms", "mdi:view-grid-outline", "Divisões"], ["house", "mdi:cube-outline", "Casa 3D"],
  ["scenes", "mdi:creation-outline", "Cenas"], ["climate", "mdi:thermometer", "Clima"], ["security", "mdi:shield-outline", "Segurança"],
];
const DOCKABLE = ["home", "rooms", "house", "scenes", "climate", "security"];
const TEMPLATES = [["top", "Relógio grande"], ["columns", "Duas colunas"], ["sidebar", "Barra lateral"], ["classic", "Clássica"]];
const SECTIONS = {
  clock: ["mdi:clock-outline", "Relógio e data"], people: ["mdi:account-group-outline", "Pessoas"], status: ["mdi:home-lightbulb-outline", "Estado da casa"],
  weather: ["mdi:weather-partly-cloudy", "Tempo"], scenes: ["mdi:creation-outline", "Cenas"], grid: ["mdi:view-grid-outline", "Grelha de cartões"],
  room_header: ["mdi:format-header-1", "Cabeçalho da divisão"], house3d: ["mdi:cube-outline", "Casa 3D"], scene_list: ["mdi:format-list-bulleted", "Todas as cenas"],
  climate_list: ["mdi:thermometer", "Clima de toda a casa"], security_list: ["mdi:shield-outline", "Portas, janelas e câmaras"],
};
const SUMMARIES = { climate: ["mdi:thermometer", "Clima"], security: ["mdi:shield-check-outline", "Segurança"], media: ["mdi:music", "A tocar"], vacuum: ["mdi:robot-vacuum", "Aspirador"] };

// ---------------------------------------------------------------- state and server

/** Opens the editor: loads the layouts and starts from the draft, else the published layout, else the automatic one. */
export async function openEditor(p) {
  p._le = { page: "home", sel: null, dirty: false, busy: true, msg: "" };
  p._go("layout");
  try {
    const data = await p._hass.callWS({ type: "pine3_wall/layout/get" });
    Object.assign(p._le, { data, doc: clone(data.draft || data.published || data.auto), busy: false });
  } catch (e) {
    console.error("Pine3 Wall editor", e);
    Object.assign(p._le, { busy: false, error: t("Esta função precisa da integração Pine3 Wall 0.7.0 ou mais recente.") });
  }
  p._render();
}

async function call(p, type, extra = {}, msg = "") {
  const le = p._le;
  le.busy = true; p._render();
  try {
    le.data = await p._hass.callWS({ type: `pine3_wall/layout/${type}`, ...extra });
    le.msg = msg;
    return true;
  } catch (e) {
    console.error("Pine3 Wall editor", e);
    le.msg = t("Não foi possível: {e}", { e: e.message || e.code || "?" });
    return false;
  } finally {
    le.busy = false; p._render();
    clearTimeout(le.msgTimer);
    le.msgTimer = setTimeout(() => { le.msg = ""; p._render(); }, 2500);
  }
}

/** A change in the working copy; with the preview on, the draft is saved (debounced) so the tablets follow. */
function changed(p) {
  const le = p._le;
  le.dirty = true;
  if (le.data?.preview) {
    clearTimeout(le.saveTimer);
    le.saveTimer = setTimeout(async () => { if (await call(p, "save_draft", { layout: le.doc })) le.dirty = false; }, 700);
  }
  p._render();
}

// ---------------------------------------------------------------- preview of one page

function roomsById(p) {
  const all = [...p._rooms(), ...(p._hiddenRooms || [])];
  return Object.fromEntries(all.map((r) => [r.id, r]));
}

/** Adds classes and a toolbar to the root element of a card's HTML. */
function decorate(html, cls, tools, attrs) {
  const close = html.indexOf(">");
  let open = html.slice(0, close).replace(/\sbig(?=[\s"])/, "");
  open = open.replace(/class="/, `class="${cls} `);
  return `${open} ${attrs}>${tools}${html.slice(close + 1)}`;
}

function placeholder(icon, label, sub = "") {
  return `<div class="le-ph glass"><ha-icon icon="${icon}"></ha-icon><b>${esc(t(label))}</b>${sub ? `<small>${esc(sub)}</small>` : ""}</div>`;
}

function summaryCard(spec) {
  const [icon, label] = SUMMARIES[spec.summary] || ["mdi:card-outline", spec.summary];
  return `<div class="rc glass"><div class="row1"><div class="ri"><ha-icon icon="${icon}"></ha-icon></div></div>
    <div class="foot"><div class="nm">${esc(t(label))}</div><div class="meta">${spec.entity ? esc(spec.entity) : t("Cartão de resumo")}</div></div></div>`;
}

function cardHtml(p, spec, rooms) {
  try {
    if (spec.type === "room") {
      const r = rooms[spec.room];
      return r ? p._roomCard(r) : placeholder("mdi:help-circle-outline", "Divisão que já não existe", spec.room);
    }
    if (spec.type === "device") return p._s(spec.entity) ? p._tile(spec.entity) : `<div class="tile glass">${placeholder("mdi:help-circle-outline", "Sem estado", spec.entity)}</div>`;
    if (spec.type === "summary") return summaryCard(spec);
  } catch (e) { console.warn("Pine3 Wall editor card", spec, e); }
  return `<div class="tile glass">${placeholder("mdi:puzzle-outline", "Cartão de outro módulo", spec.type)}</div>`;
}

function sectionBody(p, page, sec, si, rooms) {
  const le = p._le;
  switch (sec.type) {
    case "clock":
      return `<div class="le-clock"><div class="eyebrow">${esc(p._dateText())}</div><div class="clock">${esc(p._clockText())}</div></div>`;
    case "people": {
      const fam = (p._model?.people || []).filter((x) => !x.hidden);
      return `<div class="people">${fam.map((x) => `<div class="av ${p._s(x.id)?.state === "home" ? "home" : ""}" title="${esc(x.alias)}" style="background-color:${x.color};${x.picture ? `background-image:url('${esc(x.picture)}')` : ""}">${x.picture ? "" : esc(x.alias[0] || "?")}<i></i></div>`).join("") || `<small>${t("Sem pessoas")}</small>`}</div>`;
    }
    case "status": {
      const lit = Object.keys(p._hass.states).filter((i) => dom(i) === "light" && p._isOn(i)).length;
      return `<div class="status">${lit ? t("{n} luzes acesas em casa", { n: lit }) : t("Todas as luzes apagadas")}</div>`;
    }
    case "weather":
      try { return p._wxCard(page.template === "columns" || page.template === "sidebar" ? "tall" : "big"); } catch { return placeholder("mdi:weather-partly-cloudy", "Tempo"); }
    case "scenes": {
      const items = (sec.items || []).filter((i) => p._s(i));
      return `<div class="scenes">${items.slice(0, 12).map((i) => `<div class="scene glass"><ha-icon icon="${p._s(i).attributes.icon || "mdi:creation-outline"}"></ha-icon>${esc(p._name(i))}</div>`).join("") || `<small>${t("Sem cenas")}</small>`}</div>`;
    }
    case "room_header": {
      const r = rooms[sec.room || page.room];
      return `<div class="rhead"><div class="rtitle"><div class="t">${esc(r ? p._roomName(r) : sec.room || "?")}</div></div></div>`;
    }
    case "grid": {
      const cls = page.room ? "tiles" : "grid";
      const cards = (sec.cards || []).map((spec, ci) => {
        const selected = le.sel && le.sel[0] === si && le.sel[1] === ci;
        const tools = `<div class="le-ctools">
          <button data-le-cmove="${si}:${ci}:-1" title="${t("Para trás")}"><ha-icon icon="mdi:chevron-left"></ha-icon></button>
          <button data-le-cmove="${si}:${ci}:1" title="${t("Para a frente")}"><ha-icon icon="mdi:chevron-right"></ha-icon></button>
          <button data-le-csize="${si}:${ci}" title="${t("Tamanho duplo")}"><ha-icon icon="${spec.size === "big" ? "mdi:arrow-collapse" : "mdi:arrow-expand"}"></ha-icon></button>
          <button data-le-cdel="${si}:${ci}" title="${t("Tirar")}"><ha-icon icon="mdi:close"></ha-icon></button></div>`;
        const html = cardHtml(p, spec, rooms);
        return decorate(html, `le-card ${spec.size === "big" ? "big" : ""} ${selected ? "le-on" : ""}`, tools, `data-le-ci="${si}:${ci}"`);
      }).join("");
      const note = sec.group === "category" ? `<small class="le-note">${t("No tablet os cartões são agrupados por categoria (luzes, clima…), por esta ordem dentro de cada uma.")}</small>` : "";
      return `${note}<div class="${cls}">${cards || `<small>${t("Grelha vazia: acrescenta cartões à direita.")}</small>`}</div>`;
    }
    default: {
      const [icon, label] = SECTIONS[sec.type] || ["mdi:puzzle-outline", sec.type];
      return placeholder(icon, label, t("Desenhado no tablet"));
    }
  }
}

function pagePreview(p, key, rooms) {
  const le = p._le, page = le.doc.pages[key];
  if (!page) return `<div class="empty">${t("Página sem conteúdo.")}</div>`;
  return page.sections.map((sec, si) => {
    const [icon, label] = SECTIONS[sec.type] || ["mdi:puzzle-outline", sec.type];
    return `<div class="le-sec">
      <div class="le-sechd"><ha-icon icon="${icon}"></ha-icon><b>${esc(t(label))}</b><span class="sp"></span>
        <button class="mini" data-le-smove="${si}:-1" ${si === 0 ? "disabled" : ""}><ha-icon icon="mdi:chevron-up"></ha-icon></button>
        <button class="mini" data-le-smove="${si}:1" ${si === page.sections.length - 1 ? "disabled" : ""}><ha-icon icon="mdi:chevron-down"></ha-icon></button>
        <button class="mini" data-le-sdel="${si}" title="${t("Tirar")}"><ha-icon icon="mdi:close"></ha-icon></button></div>
      ${sectionBody(p, page, sec, si, rooms)}</div>`;
  }).join("") || `<div class="empty">${t("Página vazia: acrescenta secções à direita.")}</div>`;
}

// ---------------------------------------------------------------- side panels

function pageList(p, rooms) {
  const le = p._le, doc = le.doc;
  const roomPages = Object.keys(doc.pages).filter((k) => k.startsWith("room/"));
  const item = (key, icon, label) => `<button class="${key === le.page ? "act" : ""}" data-le-page="${esc(key)}"><ha-icon icon="${icon}"></ha-icon>${esc(label)}</button>`;
  return `<div class="le-pages">${FIXED_PAGES.filter(([k]) => doc.pages[k]).map(([k, icon, label]) => item(k, icon, t(label))).join("")}
    <div class="eyebrow" style="margin:14px 0 6px">${t("Divisões")}</div>
    ${roomPages.map((k) => { const r = rooms[k.slice(5)]; return item(k, r?.icon || "mdi:door", r ? p._roomName(r) : k.slice(5)); }).join("")}</div>`;
}

function addPanel(p, rooms) {
  const le = p._le, page = le.doc.pages[le.page];
  if (!page) return "";
  const have = new Set(page.sections.map((s) => s.type));
  const secOpts = Object.entries(SECTIONS).filter(([k]) => !have.has(k) || k === "grid").map(([k, [, label]]) => `<option value="${k}">${esc(t(label))}</option>`).join("");
  const gi = le.sel ? le.sel[0] : page.sections.findIndex((s) => s.type === "grid");
  const grid = page.sections[gi]?.type === "grid" ? page.sections[gi] : null;
  let cardOpts = "";
  if (grid) {
    const inGrid = new Set((grid.cards || []).map((c) => `${c.type}:${c.room || c.entity || c.summary}`));
    const roomOpts = Object.values(rooms).filter((r) => !inGrid.has(`room:${r.id}`)).map((r) => `<option value="room:${esc(r.id)}">${esc(p._roomName(r))}</option>`).join("");
    const devRooms = page.room ? [rooms[page.room]].filter(Boolean) : Object.values(rooms);
    const devOpts = devRooms.map((r) => {
      const ids = (p._model?.rooms.find((m) => m.id === r.id)?.devices || []).map((d) => d.id).filter((i) => !inGrid.has(`device:${i}`));
      return ids.length ? `<optgroup label="${esc(p._roomName(r))}">${ids.map((i) => `<option value="device:${esc(i)}">${esc(p._name(i))}</option>`).join("")}</optgroup>` : "";
    }).join("");
    const vacs = Object.keys(p._hass.states).filter((i) => dom(i) === "vacuum");
    const sumOpts = [...["climate", "security", "media"].filter((k) => !inGrid.has(`summary:${k}`)).map((k) => `<option value="summary:${k}">${esc(t(SUMMARIES[k][1]))}</option>`),
      ...vacs.map((v) => `<option value="summary:vacuum:${esc(v)}">${esc(t("Aspirador"))}: ${esc(p._name(v))}</option>`)].join("");
    cardOpts = `<option value="">${t("Escolher…")}</option>${roomOpts ? `<optgroup label="${t("Divisões")}">${roomOpts}</optgroup>` : ""}${devOpts}${sumOpts ? `<optgroup label="${t("Resumos")}">${sumOpts}</optgroup>` : ""}`;
  }
  return `<div class="panel glass le-add"><h3 style="margin-top:0">${t("Acrescentar")}</h3>
    <label>${t("Secção")}</label><select data-le-addsec><option value="">${t("Escolher…")}</option>${secOpts}</select>
    ${grid ? `<label>${t("Cartão na grelha")}${le.sel ? "" : ` <small>(${t("a primeira")})</small>`}</label><select data-le-addcard>${cardOpts}</select>` : `<p>${t("Acrescenta uma grelha para poder pôr cartões.")}</p>`}
    ${le.page === "home" ? `<label>${t("Disposição do início")}</label><select data-le-template>${TEMPLATES.map(([k, l]) => `<option value="${k}" ${page.template === k ? "selected" : ""}>${esc(t(l))}</option>`).join("")}</select>` : ""}
  </div>`;
}

function dockPanel(p) {
  const doc = p._le.doc;
  const names = Object.fromEntries(FIXED_PAGES.map(([k, , l]) => [k, t(l)]));
  const opt = doc.options || {};
  return `<div class="panel glass"><h3 style="margin-top:0">${t("Dock")}</h3><div class="list big">${DOCKABLE.map((k) => {
    const i = doc.dock.indexOf(k), on = i >= 0;
    return `<div class="it"><span>${esc(names[k])}</span>${on && k !== "home" ? `<button class="mini" data-le-dockmove="${k}:-1"><ha-icon icon="mdi:chevron-up"></ha-icon></button><button class="mini" data-le-dockmove="${k}:1"><ha-icon icon="mdi:chevron-down"></ha-icon></button>` : ""}
      ${k === "home" ? `<small>${t("Sempre")}</small>` : `<div class="tg ${on ? "on" : ""}" data-le-dock="${k}"><i></i></div>`}</div>`;
  }).join("")}</div>
  <h3>${t("Opções")}</h3><div class="opts">
    <div class="opt"><div><b>${t("Lista")}</b><small>${t("Divisões e dispositivos em lista em vez de grelha.")}</small></div><div class="tg ${opt.display === "list" ? "on" : ""}" data-le-opt="display"><i></i></div></div>
    <div class="opt"><div><b>${t("Relógio de 24 horas")}</b></div><div class="tg ${opt.clock_24h !== false ? "on" : ""}" data-le-opt="clock_24h"><i></i></div></div>
    <div class="opt"><div><b>${t("Animações")}</b></div><div class="tg ${opt.animations !== false ? "on" : ""}" data-le-opt="animations"><i></i></div></div>
  </div></div>`;
}

// ---------------------------------------------------------------- view

export function editorView(p) {
  const le = p._le;
  if (!le) return "";
  if (le.error) return `<div class="set"><div class="panel glass"><p>${esc(le.error)}</p><button class="pill" data-le-act="close">${t("Fechar")}</button></div></div>`;
  if (!le.doc) return `<div class="set"><div class="empty">${t("A carregar…")}</div></div>`;
  const rooms = roomsById(p);
  const d = le.data, src = d.source;
  const srcText = src === "preview" ? t("Os tablets estão a mostrar o rascunho") : src === "published" ? t("Os tablets mostram o layout publicado") : t("Os tablets mostram o layout automático (das definições)");
  const pageName = FIXED_PAGES.find(([k]) => k === le.page)?.[2];
  const roomName = le.page.startsWith("room/") ? (rooms[le.page.slice(5)] ? p._roomName(rooms[le.page.slice(5)]) : le.page) : null;
  return `<div class="set le">
    <div class="set-hd"><div><div class="eyebrow">Pine3 Wall</div><div class="t">${t("Editor de layout")}</div>
      <div class="s">${esc(srcText)}${le.dirty ? ` · <b>${t("alterações por guardar")}</b>` : d.draft ? ` · ${t("há um rascunho guardado")}` : ""}${le.msg ? ` · ${esc(le.msg)}` : ""}</div></div>
      <div class="actions le-actions">
        <div class="opt" style="padding:0;gap:10px"><b style="font-size:15px">${t("Rascunho nos tablets")}</b><div class="tg ${d.preview ? "on" : ""}" data-le-act="preview" title="${t("Mostra o rascunho nos tablets enquanto editas, sem publicar.")}"><i></i></div></div>
        <button class="pill" data-le-act="save" ${le.busy ? "disabled" : ""}><ha-icon icon="mdi:content-save-outline"></ha-icon>${t("Guardar rascunho")}</button>
        <button class="pill solid" data-le-act="publish" ${le.busy ? "disabled" : ""}><ha-icon icon="mdi:upload"></ha-icon>${t("Publicar")}</button>
        <button class="pill" data-le-act="more"><ha-icon icon="mdi:dots-horizontal"></ha-icon></button>
        <div class="iconbtn" data-le-act="close" title="${t("Fechar")}"><ha-icon icon="mdi:close"></ha-icon></div></div></div>
    ${le.menu ? `<div class="panel glass le-menu">
      <button class="pill" data-le-act="revert">${t("Desfazer alterações não guardadas")}</button>
      ${d.draft ? `<button class="pill" data-le-act="discard">${t("Apagar o rascunho")}</button>` : ""}
      ${d.published ? `<button class="pill" data-le-act="reset">${t("Voltar ao automático")}</button>` : ""}
      <button class="pill" data-le-act="fromauto">${t("Começar do automático")}</button>
      ${d.history.length ? `<label style="margin-left:8px">${t("Histórico")}</label><select data-le-restore><option value="">${t("Recuperar como rascunho…")}</option>${d.history.map((h, i) => `<option value="${i}">${esc(new Date(h.at).toLocaleString())}</option>`).join("")}</select>` : ""}
    </div>` : ""}
    <div class="le-body">
      <nav class="le-nav">${pageList(p, rooms)}</nav>
      <div class="le-canvas ${le.doc.options?.display === "list" ? "as-list" : ""}">
        <div class="le-cap"><b>${esc(roomName || t(pageName || le.page))}</b>${le.page === "home" ? ` · ${esc(t(TEMPLATES.find(([k]) => k === le.doc.pages.home.template)?.[1] || "Relógio grande"))}` : ""}
          <small>${t("Pré-visualização simplificada; com \"Rascunho nos tablets\" ligado vês o resultado real no tablet.")}</small></div>
        ${pagePreview(p, le.page, rooms)}
      </div>
      <div class="le-side">${addPanel(p, rooms)}${dockPanel(p)}</div>
    </div>
  </div>`;
}

// ---------------------------------------------------------------- events

export function bindEditor(p) {
  const root = p.shadowRoot, le = p._le;
  if (!le) return;
  const on = (sel, fn, ev = "click") => root.querySelectorAll(sel).forEach((el) => el.addEventListener(ev, (e) => { e.stopPropagation(); fn(el, e); }));
  const page = () => le.doc?.pages[le.page];
  const pair = (v) => v.split(":").map(Number);

  on("[data-le-act]", async (el) => {
    const a = el.dataset.leAct;
    if (a === "close") {
      if (le.dirty && !confirm(t("Há alterações por guardar. Fechar mesmo assim?"))) return;
      p._le = null; p._go("home"); return;
    }
    if (a === "more") { le.menu = !le.menu; p._render(); return; }
    if (a === "save") { if (await call(p, "save_draft", { layout: le.doc }, t("Rascunho guardado"))) le.dirty = false; p._render(); return; }
    if (a === "publish") {
      if (!confirm(t("Publicar este layout? Os tablets passam a mostrá-lo."))) return;
      if (await call(p, "publish", { layout: le.doc }, t("Publicado"))) { le.dirty = false; le.doc = clone(le.data.published); }
      p._render(); return;
    }
    if (a === "preview") {
      const turnOn = !le.data.preview;
      if (turnOn && !(await call(p, "save_draft", { layout: le.doc }))) return;
      if (await call(p, "preview", { on: turnOn }, turnOn ? t("Os tablets mostram o rascunho") : t("Os tablets voltaram ao layout publicado"))) le.dirty = false;
      p._render(); return;
    }
    if (a === "revert") { le.doc = clone(le.data.draft || le.data.published || le.data.auto); le.dirty = false; le.menu = false; p._render(); return; }
    if (a === "fromauto") { le.doc = clone(le.data.auto); le.menu = false; changed(p); return; }
    if (a === "discard") {
      if (!confirm(t("Apagar o rascunho?"))) return;
      if (await call(p, "discard_draft", {}, t("Rascunho apagado"))) { le.doc = clone(le.data.published || le.data.auto); le.dirty = false; }
      le.menu = false; p._render(); return;
    }
    if (a === "reset") {
      if (!confirm(t("Voltar ao layout automático? O publicado fica no histórico."))) return;
      if (await call(p, "reset", {}, t("Layout automático"))) { le.doc = clone(le.data.auto); le.dirty = false; }
      le.menu = false; p._render();
    }
  });
  on("[data-le-restore]", async (el) => {
    if (el.value === "") return;
    if (await call(p, "restore", { index: Number(el.value) }, t("Recuperado como rascunho"))) { le.doc = clone(le.data.draft); le.dirty = false; }
    le.menu = false; p._render();
  }, "change");

  on("[data-le-page]", (el) => { le.page = el.dataset.lePage; le.sel = null; p._render(); });
  on("[data-le-ci]", (el) => { const [s, c] = pair(el.dataset.leCi); le.sel = le.sel && le.sel[0] === s && le.sel[1] === c ? null : [s, c]; p._render(); });

  on("[data-le-smove]", (el) => {
    const [i, dir] = pair(el.dataset.leSmove), secs = page().sections, j = i + dir;
    if (j < 0 || j >= secs.length) return;
    [secs[i], secs[j]] = [secs[j], secs[i]]; le.sel = null; changed(p);
  });
  on("[data-le-sdel]", (el) => { page().sections.splice(Number(el.dataset.leSdel), 1); le.sel = null; changed(p); });

  on("[data-le-cmove]", (el) => {
    const [s, c, dir] = pair(el.dataset.leCmove), cards = page().sections[s].cards, j = c + dir;
    if (j < 0 || j >= cards.length) return;
    [cards[c], cards[j]] = [cards[j], cards[c]]; le.sel = [s, j]; changed(p);
  });
  on("[data-le-csize]", (el) => { const [s, c] = pair(el.dataset.leCsize), card = page().sections[s].cards[c]; card.size = card.size === "big" ? "normal" : "big"; changed(p); });
  on("[data-le-cdel]", (el) => { const [s, c] = pair(el.dataset.leCdel); page().sections[s].cards.splice(c, 1); le.sel = null; changed(p); });

  on("[data-le-addsec]", (el) => {
    const type = el.value;
    if (!type) return;
    const pg = page(), sec = { type };
    if (type === "grid") { sec.cards = []; if (pg.room) sec.group = "category"; }
    if (type === "scenes") sec.items = pg.room ? (p._model?.rooms.find((r) => r.id === pg.room)?.scenes || []) : (p._model?.scenes || []);
    if (type === "room_header" && pg.room) sec.room = pg.room;
    pg.sections.push(sec); changed(p);
  }, "change");
  on("[data-le-addcard]", (el) => {
    const v = el.value;
    if (!v) return;
    const pg = page(), gi = le.sel ? le.sel[0] : pg.sections.findIndex((s) => s.type === "grid");
    const grid = pg.sections[gi];
    if (!grid) return;
    // values: "room:<id>", "device:<entity>", "summary:<kind>" or "summary:vacuum:<entity>"
    const kind = v.slice(0, v.indexOf(":")), rest = v.slice(v.indexOf(":") + 1);
    const card = kind === "room" ? { type: "room", room: rest, size: "normal" }
      : kind === "device" ? { type: "device", entity: rest, size: "normal" }
      : rest.startsWith("vacuum:") ? { type: "summary", summary: "vacuum", entity: rest.slice(7) } : { type: "summary", summary: rest };
    (grid.cards ||= []).push(card); changed(p);
  }, "change");
  on("[data-le-template]", (el) => { page().template = el.value; changed(p); }, "change");

  on("[data-le-dock]", (el) => {
    const k = el.dataset.leDock, dock = le.doc.dock;
    le.doc.dock = dock.includes(k) ? dock.filter((x) => x !== k) : [...dock, k];
    changed(p);
  });
  on("[data-le-dockmove]", (el) => {
    const [k, dir] = el.dataset.leDockmove.split(":"), dock = le.doc.dock, i = dock.indexOf(k), j = i + Number(dir);
    if (j < 1 || j >= dock.length) return; // Home stays first
    [dock[i], dock[j]] = [dock[j], dock[i]]; changed(p);
  });
  on("[data-le-opt]", (el) => {
    const k = el.dataset.leOpt, o = (le.doc.options ||= {});
    if (k === "display") o.display = o.display === "list" ? "grid" : "list";
    else o[k] = o[k] === false;
    changed(p);
  });
}

export const cssEditor = `
.le .set-hd .actions{display:flex;flex-wrap:wrap;gap:10px;align-items:center}
.le-menu{display:flex;flex-wrap:wrap;gap:10px;align-items:center;margin-top:18px}
.le-menu select,.le-add select{all:unset;box-sizing:border-box;height:42px;padding:0 14px;border-radius:14px;background:rgba(255,255,255,.08);border:1px solid var(--line);color:inherit;font-size:15px;width:100%;cursor:pointer}
.le-menu select{width:auto;min-width:220px}
.le-add label,.le-menu label{display:block;font-size:13px;color:var(--muted);margin:14px 0 6px}
.le-body{display:grid;grid-template-columns:220px minmax(0,1fr) 340px;gap:24px;margin-top:28px;align-items:start}
.le-pages{display:flex;flex-direction:column;gap:4px;position:sticky;top:20px;max-height:calc(100vh - 40px);overflow:auto}
.le-pages button{all:unset;cursor:pointer;display:flex;align-items:center;gap:10px;padding:10px 12px;border-radius:14px;font-size:15px}
.le-pages button:hover{background:rgba(255,255,255,.06)}
.le-pages button.act{background:rgba(255,255,255,.9);color:var(--ink)}
.le-pages ha-icon{--mdc-icon-size:20px}
.le-canvas{border-radius:28px;border:1px dashed var(--line);padding:18px;min-height:400px}
.le-cap{display:flex;flex-wrap:wrap;gap:6px 10px;align-items:baseline;margin-bottom:12px}
.le-cap small{color:var(--muted);flex-basis:100%}
.le-sec{border-radius:22px;padding:10px 12px 14px;margin-bottom:12px;background:rgba(255,255,255,.03)}
.le-sechd{display:flex;align-items:center;gap:8px;font-size:14px;color:var(--muted);margin-bottom:8px}
.le-sechd .sp{flex:1}
.le-sechd ha-icon{--mdc-icon-size:18px}
.le-clock .clock{font-size:64px;font-weight:300;line-height:1.05}
.le-note{display:block;color:var(--muted);margin-bottom:6px}
.le-card{position:relative;outline:2px solid transparent;outline-offset:3px;transition:outline-color .15s}
.le-card:hover{outline-color:rgba(255,255,255,.35)}
.le-card.le-on{outline-color:var(--amber)}
.le-ctools{position:absolute;top:8px;right:8px;z-index:3;display:none;gap:4px;background:rgba(20,12,24,.82);border-radius:999px;padding:4px}
.le-card:hover .le-ctools,.le-card.le-on .le-ctools{display:flex}
.le-ctools button{all:unset;cursor:pointer;width:32px;height:32px;border-radius:50%;display:grid;place-items:center;color:#fff}
.le-ctools button:hover{background:rgba(255,255,255,.15)}
.le-ctools ha-icon{--mdc-icon-size:18px}
.le-ph{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px;min-height:110px;border-radius:20px;padding:16px;text-align:center}
.le-ph small{color:var(--muted)}
.le-side .panel{margin-bottom:16px}
@media (max-width:1200px){.le-body{grid-template-columns:180px minmax(0,1fr)}.le-side{grid-column:1/-1}}
`;
