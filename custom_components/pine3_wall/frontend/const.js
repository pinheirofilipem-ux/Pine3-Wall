// Pine3 Wall - shared constants and helpers.

export const SHOWN = new Set([
  "light", "switch", "fan", "cover", "lock", "climate", "media_player", "sensor",
  "binary_sensor", "scene", "script", "vacuum", "camera", "input_boolean", "button",
]);
export const TOGGLE = new Set(["light", "switch", "fan", "input_boolean", "lock", "cover", "media_player"]);
export const DETAIL = new Set(["light", "cover", "climate", "media_player", "fan"]);

export const SECTIONS = [
  ["Luzes", ["light"]],
  ["Clima", ["climate", "fan"]],
  ["Cortinas e estores", ["cover"]],
  ["Multimédia", ["media_player"]],
  ["Segurança", ["lock", "camera"]],
  ["Tomadas e interruptores", ["switch", "input_boolean"]],
  ["Ações", ["script", "button", "vacuum"]],
  ["Sensores", ["sensor", "binary_sensor"]],
];

export const SECTION_ICON = {
  "Luzes": "mdi:lightbulb-outline", "Clima": "mdi:thermometer", "Cortinas e estores": "mdi:window-shutter", "Multimédia": "mdi:television",
  "Segurança": "mdi:shield-outline", "Tomadas e interruptores": "mdi:power-socket-eu", "Ações": "mdi:gesture-tap-button", "Sensores": "mdi:gauge",
};
export const DOMAIN_ICON = {
  light: "mdi:lightbulb", switch: "mdi:power-socket-eu", fan: "mdi:fan", cover: "mdi:window-shutter",
  lock: "mdi:lock", climate: "mdi:thermostat", media_player: "mdi:speaker", sensor: "mdi:gauge",
  binary_sensor: "mdi:checkbox-blank-circle-outline", scene: "mdi:palette", script: "mdi:script-text-play",
  vacuum: "mdi:robot-vacuum", camera: "mdi:cctv", input_boolean: "mdi:toggle-switch", button: "mdi:gesture-tap-button",
};
export const DEVICE_CLASS_ICON = {
  temperature: "mdi:thermometer", humidity: "mdi:water-percent", power: "mdi:flash", energy: "mdi:lightning-bolt",
  door: "mdi:door", window: "mdi:window-closed-variant", motion: "mdi:motion-sensor", battery: "mdi:battery",
  illuminance: "mdi:brightness-5", garage_door: "mdi:garage", blind: "mdi:blinds", curtain: "mdi:curtains",
  pm25: "mdi:blur", pm10: "mdi:blur", pm1: "mdi:blur", aqi: "mdi:air-filter", carbon_dioxide: "mdi:molecule-co2",
  volatile_organic_compounds: "mdi:molecule", volatile_organic_compounds_parts: "mdi:molecule",
  signal_strength: "mdi:wifi", duration: "mdi:timer-outline",
};
// Icon hints from an entity's name, for integrations that don't set icons or device classes
// (e.g. an air purifier's filter life, child lock, display light, beep).
export const NAME_ICON = [
  [/purif|air ?clean/i, "mdi:air-purifier"], [/filtr|filter|hepa/i, "mdi:air-filter"], [/bloqueio|child ?lock|\block\b/i, "mdi:lock"],
  [/al[ée]rg|allerg/i, "mdi:flower-pollen"], [/luz|light|display|ecr[ãa]/i, "mdi:lightbulb-on-outline"], [/beep|som|buzzer|sound/i, "mdi:volume-high"],
  [/modo|mode/i, "mdi:tune-variant"], [/velocidade|speed/i, "mdi:fan"], [/timer|temporizador|remaining|restante/i, "mdi:timer-outline"], [/rssi|wi-?fi|sinal|signal/i, "mdi:wifi"],
];
export const ACCENT = {
  light: "#ffb340", switch: "#5ac8fa", fan: "#64d2ff", cover: "#5e9cff", lock: "#30d158", climate: "#ff9f0a",
  media_player: "#bf5af2", input_boolean: "#5ac8fa", vacuum: "#30d158", script: "#5e5ce6", button: "#5e5ce6",
  binary_sensor: "#ff453a", camera: "#8e8e93",
};
export const STATE_PT = {
  on: "Ligado", off: "Desligado", open: "Aberto", closed: "Fechado", opening: "A abrir", closing: "A fechar",
  locked: "Trancado", unlocked: "Destrancado", playing: "A tocar", paused: "Em pausa", idle: "Inativo",
  standby: "Em espera", unavailable: "Indisponível", unknown: "Desconhecido", heat: "Aquecer", cool: "Arrefecer",
  auto: "Automático", heat_cool: "Aquecer/Arrefecer", dry: "Desumidificar", fan_only: "Ventilação",
  balanced: "Equilibrado", max: "Máximo", max_plus: "Máximo+", gentle: "Suave", charging: "A carregar", cleaning: "A limpar", docked: "Na base", returning: "A regressar",
  // fan / air purifier preset modes
  sleep: "Noturno", turbo: "Turbo", manual: "Manual", silent: "Silencioso", eco: "Eco", boost: "Reforço",
  allergen: "Alérgenos", bacteria: "Bactérias", night: "Noturno", smart: "Inteligente", natural: "Natural",
  low: "Baixo", medium: "Médio", high: "Alto",
  // climate: what it is doing now (hvac_action), fan / swing / preset modes (e.g. Tado via HomeKit)
  heating: "A aquecer", cooling: "A arrefecer", drying: "A desumidificar", fan: "A ventilar",
  preheating: "A pré-aquecer", defrosting: "A descongelar", middle: "Médio", quiet: "Silencioso",
  focus: "Focado", diffuse: "Difuso", both: "Ambos", vertical: "Vertical", horizontal: "Horizontal",
  // option lists of air purifiers (e.g. Philips)
  air_quality: "Qualidade do ar", ambient_light_mode: "Luz ambiente", indoor_allergen_index: "Índice de alérgenos",
  pm25: "PM2.5", humidity: "Humidade", temperature: "Temperatura", gas: "Gás",
  away: "Ausente", home: "Em casa", comfort: "Conforto", activity: "Atividade", none: "Nenhum",
};
export const WEATHER_PT = {
  "clear-night": "Céu limpo", cloudy: "Nublado", exceptional: "Excecional", fog: "Nevoeiro", hail: "Granizo",
  lightning: "Trovoada", "lightning-rainy": "Trovoada e chuva", partlycloudy: "Pouco nublado", pouring: "Chuva forte",
  rainy: "Chuva", snowy: "Neve", "snowy-rainy": "Chuva e neve", sunny: "Sol", windy: "Ventoso", "windy-variant": "Ventoso",
};
export const WEATHER_ICON = {
  "clear-night": "mdi:weather-night", cloudy: "mdi:weather-cloudy", fog: "mdi:weather-fog", hail: "mdi:weather-hail",
  lightning: "mdi:weather-lightning", "lightning-rainy": "mdi:weather-lightning-rainy", partlycloudy: "mdi:weather-partly-cloudy",
  pouring: "mdi:weather-pouring", rainy: "mdi:weather-rainy", snowy: "mdi:weather-snowy", "snowy-rainy": "mdi:weather-snowy-rainy",
  sunny: "mdi:weather-sunny", windy: "mdi:weather-windy", "windy-variant": "mdi:weather-windy-variant",
};
export const BIN_PT = {
  door: ["Fechada", "Aberta"], window: ["Fechada", "Aberta"], garage_door: ["Fechada", "Aberta"],
  motion: ["Sem movimento", "Movimento"], occupancy: ["Livre", "Ocupado"], moisture: ["Seco", "Fuga"],
  battery: ["Normal", "Fraca"], smoke: ["Sem fumo", "Fumo"], problem: ["OK", "Problema"],
};

// Themes, card colours and accents come from schema/tokens.json (generated tokens.js).
export { THEMES, LIGHT_THEMES, AMBIENT_THEMES, VIVID_THEMES, THEME_NAMES, CARD_COLORS, ACCENTS } from "./tokens.js";
export const DOCK_ITEMS = [
  ["home", "mdi:home-outline", "Casa", ""],
  ["rooms", "mdi:view-grid-outline", "Divisões", "Todas as divisões numa grelha."],
  ["house", "mdi:cube-outline", "Casa 3D", "Vista 3D da casa, por pisos."],
  ["scenes", "mdi:creation-outline", "Cenas", "Cenas e scripts."],
  ["climate", "mdi:thermometer", "Clima", "Ar condicionado, termóstatos e temperatura por divisão."],
  ["security", "mdi:shield-outline", "Segurança", "Portas, janelas, fechaduras e câmaras."],
];
export const DEFAULTS = {
  theme: "ameixa", card_color: "#f5efe1", accent_color: "#f6b83f", clock_24h: true,
  show_weather: true, show_people: true, home_people: false, show_status: true, show_scenes: true, show_summary: true,
  dock: ["home", "rooms", "house", "scenes"], hidden: [], hidden_rooms: [], room_order: [],
  language: "pt", layout: "grid", home_layout: "top", appearance: "fixed", theme_day: "linho", theme_night: "ameixa", weather_entity: "", screensaver: 0, animations: true,
  big_rooms: [], big_devices: [], device_order: {}, house_size: {}, house_layout: {}, shown: [], placements: {}, cam_doors: {}, wall_height: 30,
};



export const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
export const dom = (id) => id.split(".")[0];
export const store = {
  get(k, d) { try { const v = localStorage.getItem("pine3wall." + k); return v == null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem("pine3wall." + k, JSON.stringify(v)); } catch { /* ignore */ } },
};

export function loadFonts() {
  // Fonts: Outfit (display) + Figtree (text), both SIL OFL 1.1, shipped in ./fonts.
(() => {
  if (document.getElementById("pine3-fonts")) return;
  const base = new URL("./fonts/", import.meta.url).href;
  const LATIN = "U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,U+2000-206F,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD";
  const EXT = "U+0100-02BA,U+02BD-02C5,U+02C7-02CC,U+02CE-02D7,U+02DD-02FF,U+0304,U+0308,U+0329,U+1D00-1DBF,U+1E00-1E9F,U+1EF2-1EFF,U+2020,U+20A0-20AB,U+20AD-20C0,U+2113,U+2C60-2C7F,U+A720-A7FF";
  const face = (fam, file, range) => `@font-face{font-family:"${fam}";font-style:normal;font-display:swap;font-weight:100 900;src:url(${base}${file}) format("woff2-variations"),url(${base}${file}) format("woff2");unicode-range:${range}}`;
  const st = document.createElement("style");
  st.id = "pine3-fonts";
  st.textContent = [
    face("Pine3 Outfit", "outfit-latin-wght-normal.woff2", LATIN), face("Pine3 Outfit", "outfit-latin-ext-wght-normal.woff2", EXT),
    face("Pine3 Figtree", "figtree-latin-wght-normal.woff2", LATIN), face("Pine3 Figtree", "figtree-latin-ext-wght-normal.woff2", EXT),
  ].join("");
  document.head.appendChild(st);
})();
}
