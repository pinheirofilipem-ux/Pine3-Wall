// Pine3 Wall - ambient screens (module ambient) drawn on the web: the layout editor's preview and, in the full web
// panel, the "ambient/<id>" pages of the dock (the tablet draws them natively in Ambient.kt).

import { t } from "./i18n.js";
import { esc } from "./const.js";
import { faceHtml } from "./calendar.js";
import { faceHtml as choresFace } from "./chores.js";
import { faceHtml as familyFace } from "./family.js";

/** An ambient screen drawn roughly as the tablet draws it (the real one is on the tablet with the preview on). */
export function ambientHtml(p, sec, h24 = true, cls = "") {
  const night = !!sec.night, fg = night ? "#b3261e" : "#fff", dim = night ? "#5c1410" : "rgba(255,255,255,.6)", acc = night ? "#d33a2c" : sec.color || "var(--amber)";
  const now = new Date();
  const face = (f) => {
    switch (f.type) {
      case "clock_analog": {
        const s = now.getSeconds(), m = now.getMinutes() + s / 60, h = (now.getHours() % 12) + m / 60;
        const hand = (deg, len, w, col) => { const a = (deg - 90) * Math.PI / 180; return `<line x1="50" y1="50" x2="${50 + len * Math.cos(a)}" y2="${50 + len * Math.sin(a)}" stroke="${col}" stroke-width="${w}" stroke-linecap="round"/>`; };
        const ticks = [...Array(f.style === "minimal" ? 4 : 12)].map((_, i) => { const a = (i * (f.style === "minimal" ? 90 : 30) - 90) * Math.PI / 180; return `<line x1="${50 + 40 * Math.cos(a)}" y1="${50 + 40 * Math.sin(a)}" x2="${50 + 46 * Math.cos(a)}" y2="${50 + 46 * Math.sin(a)}" stroke="${fg}" stroke-width="${f.style === "bold" ? 3.5 : 2}" stroke-linecap="round"/>`; }).join("");
        return `<svg viewBox="0 0 100 100" class="le-af">${f.style === "minimal" ? "" : `<circle cx="50" cy="50" r="47" fill="none" stroke="${dim}" stroke-width=".8"/>`}${ticks}
          ${hand(h * 30, 24, f.style === "bold" ? 5 : 3.5, fg)}${hand(m * 6, 37, f.style === "bold" ? 3.5 : 2.5, fg)}${f.seconds === false ? "" : hand(s * 6, 41, 1, acc)}<circle cx="50" cy="50" r="2" fill="${acc}"/></svg>`;
      }
      case "clock_digital":
        return `<div class="le-at" style="color:${fg}">${esc(now.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit", second: f.seconds ? "2-digit" : undefined, hour12: !h24 }))}</div><small style="color:${dim}">${esc(p._dateText())}</small>`;
      case "date":
        return `<small style="color:${acc};font-weight:600;letter-spacing:.1em">${esc(now.toLocaleDateString(undefined, { weekday: "long" }).toUpperCase())}</small><div class="le-at" style="color:${fg}">${now.getDate()}</div><small style="color:${dim}">${esc(now.toLocaleDateString(undefined, { month: "long" }))}</small>`;
      case "weather": {
        const w = p._model?.weather && p._s(p._model.weather);
        return `<ha-icon icon="mdi:weather-partly-cloudy" style="--mdc-icon-size:42px;color:${acc}"></ha-icon><div class="le-at" style="color:${fg}">${w?.attributes.temperature != null ? Math.round(w.attributes.temperature) + "°" : "–"}</div><small style="color:${dim}">${esc(w ? (p._wxLabel ? p._wxLabel(w.state) : w.state) : t("Tempo"))}</small>`;
      }
      case "sensor": {
        const s = f.entity && p._s(f.entity);
        return `<div class="le-at" style="color:${fg}">${s ? esc(s.state + (s.attributes.unit_of_measurement ? " " + s.attributes.unit_of_measurement : "")) : "–"}</div><small style="color:${acc}">${esc(f.label || (f.entity ? p._name(f.entity) : t("Escolhe um sensor")))}</small>`;
      }
      case "next_event":
      case "agenda_today":
        return faceHtml(p, f.type, fg, dim, acc);
      case "chores_today":
        return choresFace(p, fg, dim, acc);
      case "family_day":
        return familyFace(p, fg, dim, acc);
      case "people":
        return `<div class="people">${(p._model?.people || []).filter((x) => !x.hidden).map((x) => `<div class="av" style="background-color:${night ? fg : x.color};opacity:${p._s(x.id)?.state === "home" ? 1 : 0.35}">${esc((x.alias || "?")[0])}</div>`).join("")}</div>`;
      default:
        return `<small>${esc(f.type)}</small>`;
    }
  };
  return `<div class="le-amb ${cls}" style="${night ? "background:#000" : `background:radial-gradient(70% 70% at 50% 45%, ${sec.color || "#f6b83f"}33, #000 70%)`}">
    ${(sec.faces || []).map((f) => `<div class="le-face">${face(f)}</div>`).join("") || `<small>${t("Sem faces: acrescenta à direita.")}</small>`}</div>`;
}


/** The ambient section of an "ambient/<id>" page. */
export const ambientSec = (page) => page?.sections?.find((s) => s.type === "ambient");

/** A whole ambient page in the web panel: the screen fills the space above the dock and its clock ticks every second. */
export function ambientView(p, key) {
  const layout = p._model?.layout, page = layout?.pages?.[key], sec = ambientSec(page);
  if (!sec) return `<div class="empty">${t("Este ecrã já não existe no layout.")}</div>`;
  return `<div class="amb-page" data-amb="${esc(key)}">${ambientHtml(p, sec, layout.options?.clock_24h !== false, "full")}</div>`;
}

/** Redraws the open ambient page once a second (analog seconds hand, digital clock). */
export function tickAmbient(p, root) {
  clearInterval(p._ambTimer);
  const el = root.querySelector("[data-amb]");
  if (!el) return;
  p._ambTimer = setInterval(() => {
    const cur = p.shadowRoot.querySelector("[data-amb]");
    if (!cur || cur !== el) { clearInterval(p._ambTimer); return; }
    const sec = ambientSec(p._model?.layout?.pages?.[el.dataset.amb]);
    if (sec) el.innerHTML = ambientHtml(p, sec, p._model.layout.options?.clock_24h !== false, "full");
  }, 1000);
}

export const cssAmbient = `
.amb-page{height:calc(100vh - var(--dock-space,108px) - 40px);min-height:320px;margin-top:20px}
.amb-page .le-amb.full{height:100%;aspect-ratio:auto}
.amb-page .le-at{font-size:clamp(48px,9vw,150px)}
.amb-page .le-af{width:min(100%,58vh)}
.amb-page .le-face{font-size:clamp(16px,1.8vw,26px)}.amb-page .le-face small{font-size:inherit}
@media (max-width:700px){.amb-page .le-amb.full{flex-direction:column}.amb-page .le-at{font-size:64px}}
`;
