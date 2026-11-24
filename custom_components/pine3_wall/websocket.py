"""Websocket API and registry listeners for Pine3 Wall."""

from __future__ import annotations

import asyncio
import time
from typing import Any

import voluptuous as vol

from homeassistant.components import websocket_api
from homeassistant.core import Event, HomeAssistant, callback
from homeassistant.helpers.aiohttp_client import async_get_clientsession
from homeassistant.helpers.event import async_call_later
from homeassistant.helpers.storage import Store

from .const import API_VERSION, DOMAIN, EVENT_UPDATED, MIN_APP_VERSION, VERSION
from .chores import CHORES_SCHEMA, DEFAULT_CHORES, model as chores_model
from .discovery import build_home
from .family_calendar import DEFAULT_CALENDAR, FamilyCalendar, assign
from .layout import DOCK_ITEMS
from .layout_store import MAIN, TARGET_NAME, LayoutStore, validate_doc
from .panels import COMMANDS, Panels
from .people import build_people

STORAGE_KEY = f"{DOMAIN}.settings"
DEFAULT_SETTINGS: dict[str, Any] = {
    "hidden": [],
    "hidden_rooms": [],
    "room_order": [],
    "theme": "ameixa",
    "appearance": "fixed",
    "theme_day": "linho",
    "theme_night": "ameixa",
    "card_color": "#f5efe1",
    "accent_color": "#f6b83f",
    "clock_24h": True,
    "animations": True,
    "show_weather": True,
    "show_people": True,
    "home_people": False,
    "show_status": True,
    "show_scenes": True,
    "show_summary": True,
    "dock": ["home", "rooms", "house", "scenes"],
    "language": "pt",
    "layout": "grid",
    "home_layout": "top",
    "weather_entity": "",
    "screensaver": 0,
    "big_rooms": [],
    "big_devices": [],
    "device_order": {},
    "house_size": {},
    "house_layout": {},
    "shown": [],
    "placements": {},
    "cam_doors": {},
    "people": {},
    "wall_height": 30,
    "calendar": DEFAULT_CALENDAR,
    "chores": DEFAULT_CHORES,
    "settings_version": 2,
}
DEVICE_TYPES = [
    "ceiling", "lamp", "led", "spot", "bulb", "tv", "appletv", "homepod", "speaker", "ac", "fan", "purifier",
    "blind", "door", "window", "plug", "sensor", "motion", "camera", "lock", "vacuum", "other",
]
COLOR = vol.Match(r"^#[0-9a-fA-F]{6}$")
REGISTRY_EVENTS = (
    "area_registry_updated",
    "device_registry_updated",
    "entity_registry_updated",
    "floor_registry_updated",
)


async def async_setup_api(hass: HomeAssistant) -> None:
    store: Store = Store(hass, 1, STORAGE_KEY)
    stored = await store.async_load() or {}
    settings = {**DEFAULT_SETTINGS, **stored}
    if stored and stored.get("settings_version", 1) < 2:
        # v0.5 adds the 3D Home: show it in the dock of existing installs once.
        if "house" not in settings["dock"]:
            settings["dock"] = [d for d in DOCK_ITEMS if d in settings["dock"] or d == "house"]
        settings["settings_version"] = 2
        await store.async_save(settings)
    layouts = LayoutStore(hass)
    await layouts.async_load()
    panels = Panels(hass)
    await panels.async_load()
    calendar = FamilyCalendar(hass, settings, lambda: build_people(hass, hass.data[DOMAIN]["settings"]))
    hass.data[DOMAIN] = {"store": store, "settings": settings, "layouts": layouts, "panels": panels, "calendar": calendar, "unsubs": []}

    websocket_api.async_register_command(hass, ws_home)
    websocket_api.async_register_command(hass, ws_settings)
    websocket_api.async_register_command(hass, ws_subscribe)
    websocket_api.async_register_command(hass, ws_ipma_warnings)
    for cmd in (
        ws_layout_get, ws_layout_save_draft, ws_layout_discard, ws_layout_publish, ws_layout_reset, ws_layout_restore,
        ws_layout_preview, ws_layout_edit, ws_layout_create, ws_layout_delete,
        ws_panel_connect, ws_panel_state, ws_panel_list, ws_panel_update, ws_panel_remove, ws_panel_command,
        ws_calendar_assign, ws_calendar_refresh,
    ):
        websocket_api.async_register_command(hass, cmd)

    pending: list = []

    @callback
    def _fire(_now=None) -> None:
        pending.clear()
        hass.bus.async_fire(EVENT_UPDATED)

    @callback
    def _changed(_event: Event) -> None:
        # Registries change in bursts (e.g. a new integration): debounce.
        if not pending:
            pending.append(async_call_later(hass, 2, _fire))

    for ev in REGISTRY_EVENTS:
        hass.data[DOMAIN]["unsubs"].append(hass.bus.async_listen(ev, _changed))
    hass.data[DOMAIN]["unsubs"].append(calendar.async_stop)
    # the calendar entities may not exist yet while HA starts: read in the background
    hass.async_create_background_task(calendar.async_start(), f"{DOMAIN}_calendar")


@callback
def async_unload_api(hass: HomeAssistant) -> None:
    for unsub in hass.data.get(DOMAIN, {}).get("unsubs", []):
        unsub()
    hass.data.pop(DOMAIN, None)


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/home", vol.Optional("panel_id"): str})
@callback
def ws_home(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    connection.send_result(msg["id"], _model(hass, msg.get("panel_id")))


def _model(hass: HomeAssistant, panel_id: str | None = None) -> dict[str, Any]:
    """The home model; with a tablet's id, with the layout that tablet uses (main or a variant)."""
    settings = hass.data[DOMAIN]["settings"]
    home = build_home(hass, settings)
    target = hass.data[DOMAIN]["panels"].layout_of(panel_id)
    layout, source = hass.data[DOMAIN]["layouts"].active(home, settings, target)
    return {
        **home,
        "layout": layout,
        "layout_source": source,
        "people": build_people(hass, settings),
        **({"calendar": cal} if (cal := hass.data[DOMAIN]["calendar"].model()) else {}),
        **({"chores": ch} if (ch := chores_model(settings.get("chores"))) else {}),
        "version": VERSION,
        "api_version": API_VERSION,
        "min_app_version": MIN_APP_VERSION,
    }


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/subscribe", vol.Optional("panel_id"): str})
@callback
def ws_subscribe(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    """Push a fresh home model whenever the registries or settings change."""

    @callback
    def _forward(_event: Event) -> None:
        if DOMAIN in hass.data:
            connection.send_message(
                websocket_api.event_message(msg["id"], _model(hass, msg.get("panel_id")))
            )

    connection.subscriptions[msg["id"]] = hass.bus.async_listen(EVENT_UPDATED, _forward)
    connection.send_result(msg["id"])


@websocket_api.websocket_command(
    {
        vol.Required("type"): f"{DOMAIN}/settings",
        vol.Optional("hidden"): [str],
        vol.Optional("hidden_rooms"): [str],
        vol.Optional("room_order"): [str],
        vol.Optional("theme"): str,
        vol.Optional("appearance"): vol.In(["fixed", "auto"]),
        vol.Optional("theme_day"): str,
        vol.Optional("theme_night"): str,
        vol.Optional("card_color"): COLOR,
        vol.Optional("accent_color"): COLOR,
        vol.Optional("clock_24h"): bool,
        vol.Optional("animations"): bool,
        vol.Optional("show_weather"): bool,
        vol.Optional("show_people"): bool,
        vol.Optional("home_people"): bool,
        vol.Optional("show_status"): bool,
        vol.Optional("show_scenes"): bool,
        vol.Optional("show_summary"): bool,
        vol.Optional("dock"): [vol.In(DOCK_ITEMS)],
        vol.Optional("language"): vol.In(["pt", "en"]),
        vol.Optional("layout"): vol.In(["grid", "list"]),
        vol.Optional("home_layout"): vol.In(["classic", "top", "columns", "sidebar"]),
        vol.Optional("weather_entity"): vol.Any("", "open-meteo", vol.Match(r"^weather\.[a-z0-9_]+$")),
        vol.Optional("screensaver"): vol.All(int, vol.Range(min=0, max=240)),
        vol.Optional("big_rooms"): [str],
        vol.Optional("big_devices"): [str],
        vol.Optional("device_order"): {str: [str]},
        vol.Optional("house_size"): {str: vol.In([1, 2, 3])},
        vol.Optional("house_layout"): {
            str: {
                vol.Required("f"): vol.All(int, vol.Range(min=-5, max=20)),
                vol.Required("x"): vol.All(int, vol.Range(min=0, max=60)),
                vol.Required("y"): vol.All(int, vol.Range(min=0, max=60)),
                vol.Required("w"): vol.All(int, vol.Range(min=1, max=30)),
                vol.Required("d"): vol.All(int, vol.Range(min=1, max=30)),
                # free-shape rooms: the grid cells they cover
                vol.Optional("cells"): vol.All(
                    [vol.All([vol.All(int, vol.Range(min=0, max=60))], vol.Length(min=2, max=2))],
                    vol.Length(min=1, max=1000),
                ),
            }
        },
        vol.Optional("shown"): [str],
        vol.Optional("wall_height"): vol.All(int, vol.Range(min=8, max=120)),
        # entities placed on the floor plan: floor, position in grid units, optional device type
        vol.Optional("placements"): {
            str: {
                vol.Required("f"): vol.All(int, vol.Range(min=-5, max=20)),
                vol.Required("x"): vol.All(vol.Coerce(float), vol.Range(min=0, max=60)),
                vol.Required("y"): vol.All(vol.Coerce(float), vol.Range(min=0, max=60)),
                vol.Optional("t"): vol.In(DEVICE_TYPES),
                # LED strips: angle in degrees and length in grid units
                vol.Optional("r"): vol.All(vol.Coerce(float), vol.Range(min=0, max=360)),
                vol.Optional("l"): vol.All(vol.Coerce(float), vol.Range(min=0.25, max=30)),
            }
        },
        # the doors shown beside each camera's live picture
        vol.Optional("cam_doors"): {vol.Match(r"^camera\.[a-z0-9_]+$"): [str]},
        # the family: colour, alias and whether to show each person (calendar, chores, home screen)
        vol.Optional("people"): {
            vol.Match(r"^person\.[a-z0-9_]+$"): {
                vol.Optional("color"): COLOR,
                vol.Optional("alias"): vol.All(str, vol.Length(max=40)),
                vol.Optional("hidden"): bool,
            }
        },
        # module calendar: the family calendar(s), those shown only as "Busy", how many days ahead and the family colour
        vol.Optional("calendar"): {
            vol.Optional("entities"): vol.All([vol.Match(r"^calendar\.[a-z0-9_]+$")], vol.Length(max=5)),
            vol.Optional("private"): [vol.Match(r"^calendar\.[a-z0-9_]+$")],
            vol.Optional("days"): vol.All(int, vol.Range(min=1, max=62)),
            vol.Optional("color"): COLOR,
        },
        # module chores: the tasks and the weekly grid task x weekday -> people
        vol.Optional("chores"): CHORES_SCHEMA,
        vol.Optional("settings_version"): int,
    }
)
@websocket_api.async_response
async def ws_settings(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    data = hass.data[DOMAIN]
    for key in DEFAULT_SETTINGS:
        if key in msg:
            data["settings"][key] = msg[key]
    await data["store"].async_save(data["settings"])
    hass.bus.async_fire(EVENT_UPDATED)
    connection.send_result(msg["id"], data["settings"])
    if "calendar" in msg or "people" in msg:  # new calendars, or names/aliases that change whose an event is
        hass.async_create_task(data["calendar"].async_settings_changed())


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/calendar/assign", vol.Required("titles"): vol.All([str], vol.Length(max=50))})
@callback
def ws_calendar_assign(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    """Whose an event would be, for the settings' preview of the title rule."""
    people = build_people(hass, hass.data[DOMAIN]["settings"])
    out = []
    for title in msg["titles"]:
        who, clean = assign(title, people)
        out.append({"people": who, "title": clean})
    connection.send_result(msg["id"], out)


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/calendar/refresh"})
@websocket_api.async_response
async def ws_calendar_refresh(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    """Reads the calendar again now (settings' "Atualizar")."""
    cal = hass.data[DOMAIN]["calendar"]
    await cal.async_refresh()
    connection.send_result(msg["id"], cal.model())


# IPMA (Portuguese weather service): the HA integration exposes only the first warning of an area,
# so the panel asks for all of them here (server side: no CORS, one download for every panel).
IPMA_WARNINGS_URL = "https://api.ipma.pt/open-data/forecast/warnings/warnings_www.json"
IPMA_TTL = 600  # seconds
IPMA_FIELDS = ("text", "awarenessTypeName", "awarenessLevelID", "startTime", "endTime")


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/ipma_warnings", vol.Required("area"): str})
@websocket_api.async_response
async def ws_ipma_warnings(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    cache = hass.data.setdefault(f"{DOMAIN}_ipma", {})
    if cache.get("raw") is None or time.monotonic() - cache.get("at", 0) > IPMA_TTL:
        try:
            async with asyncio.timeout(10):
                resp = await async_get_clientsession(hass).get(IPMA_WARNINGS_URL)
                resp.raise_for_status()
                raw = await resp.json(content_type=None)
            if isinstance(raw, list):
                cache.update(raw=raw, at=time.monotonic())
        except Exception as err:  # noqa: BLE001 - network or format: keep the last list if there is one
            if cache.get("raw") is None:
                connection.send_error(msg["id"], "unavailable", str(err))
                return
    warnings = [
        {k: w.get(k) for k in IPMA_FIELDS}
        for w in cache.get("raw") or []
        if isinstance(w, dict) and w.get("idAreaAviso") == msg["area"] and w.get("awarenessLevelID") not in (None, "green")
    ]
    connection.send_result(msg["id"], {"warnings": warnings})


# ---------------------------------------------------------------- layouts (web editor)


def _home_now(hass: HomeAssistant) -> tuple[dict[str, Any], dict[str, Any]]:
    settings = hass.data[DOMAIN]["settings"]
    return build_home(hass, settings), settings


def _layout_summary(hass: HomeAssistant, target: str | None = None) -> dict[str, Any]:
    home, settings = _home_now(hass)
    return hass.data[DOMAIN]["layouts"].summary(home, settings, target)


async def _layout_changed(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict, notify: bool = True) -> None:
    await hass.data[DOMAIN]["layouts"].async_save()
    if notify:
        hass.bus.async_fire(EVENT_UPDATED)  # the tablets redraw with the new layout
    connection.send_result(msg["id"], _layout_summary(hass, msg.get("target")))


def _target_ok(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> bool:
    if msg.get("target", MAIN) in hass.data[DOMAIN]["layouts"].data["targets"]:
        return True
    connection.send_error(msg["id"], "not_found", "no layout with that name")
    return False


def _doc(value: Any) -> dict[str, Any]:
    return validate_doc(value)


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/layout/get", vol.Optional("target"): TARGET_NAME})
@callback
def ws_layout_get(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    """Draft, published and automatic layouts, history and preview state."""
    if _target_ok(hass, connection, msg):
        connection.send_result(msg["id"], _layout_summary(hass, msg.get("target")))


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/layout/save_draft", vol.Required("layout"): _doc, vol.Optional("target"): TARGET_NAME})
@websocket_api.async_response
async def ws_layout_save_draft(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    if not _target_ok(hass, connection, msg):
        return
    layouts = hass.data[DOMAIN]["layouts"]
    home, _ = _home_now(hass)
    layouts.save_draft(msg["layout"], home, msg.get("target"))
    # with the preview on, the tablets show the draft as it is edited
    await _layout_changed(hass, connection, msg, notify=bool(layouts.t(msg.get("target")).get("preview")))


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/layout/discard_draft", vol.Optional("target"): TARGET_NAME})
@websocket_api.async_response
async def ws_layout_discard(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    if not _target_ok(hass, connection, msg):
        return
    hass.data[DOMAIN]["layouts"].discard_draft(msg.get("target"))
    await _layout_changed(hass, connection, msg)


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/layout/publish", vol.Optional("layout"): _doc, vol.Optional("target"): TARGET_NAME})
@websocket_api.async_response
async def ws_layout_publish(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    if not _target_ok(hass, connection, msg):
        return
    home, _ = _home_now(hass)
    try:
        hass.data[DOMAIN]["layouts"].publish(msg.get("layout"), home, msg.get("target"))
    except vol.Invalid as err:
        connection.send_error(msg["id"], "invalid", str(err))
        return
    await _layout_changed(hass, connection, msg)


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/layout/reset", vol.Optional("target"): TARGET_NAME})
@websocket_api.async_response
async def ws_layout_reset(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    """Back to the automatic layout."""
    if not _target_ok(hass, connection, msg):
        return
    hass.data[DOMAIN]["layouts"].reset(msg.get("target"))
    await _layout_changed(hass, connection, msg)


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/layout/restore", vol.Required("index"): vol.All(int, vol.Range(min=0)), vol.Optional("target"): TARGET_NAME})
@websocket_api.async_response
async def ws_layout_restore(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    """A layout from the history becomes the draft."""
    if not _target_ok(hass, connection, msg):
        return
    layouts = hass.data[DOMAIN]["layouts"]
    try:
        layouts.restore(msg["index"], msg.get("target"))
    except vol.Invalid as err:
        connection.send_error(msg["id"], "not_found", str(err))
        return
    await _layout_changed(hass, connection, msg, notify=bool(layouts.t(msg.get("target")).get("preview")))


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/layout/preview", vol.Required("on"): bool, vol.Optional("target"): TARGET_NAME})
@websocket_api.async_response
async def ws_layout_preview(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    """Show the draft on the tablets while it is edited (without publishing it)."""
    if not _target_ok(hass, connection, msg):
        return
    hass.data[DOMAIN]["layouts"].set_preview(msg["on"], msg.get("target"))
    await _layout_changed(hass, connection, msg)


@websocket_api.websocket_command(
    {
        vol.Required("type"): f"{DOMAIN}/layout/edit",
        vol.Optional("room_order"): [str],
        vol.Optional("hidden_rooms"): [str],
        vol.Optional("big_rooms"): [str],
        vol.Optional("hidden"): [str],
        vol.Optional("big_devices"): [str],
        vol.Optional("device_order"): {str: [str]},
        vol.Optional("panel_id"): str,
    }
)
@websocket_api.async_response
async def ws_layout_edit(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    """The app's edit mode when a layout is published: order, hidden and double size go into that layout."""
    home, settings = _home_now(hass)
    edit = {k: msg[k] for k in ("room_order", "hidden_rooms", "big_rooms", "hidden", "big_devices", "device_order") if k in msg}
    target = hass.data[DOMAIN]["panels"].layout_of(msg.get("panel_id"))
    if not hass.data[DOMAIN]["layouts"].edit(home, settings, edit, target):
        connection.send_error(msg["id"], "not_published", "the layout is the automatic one: edit the settings")
        return
    await _layout_changed(hass, connection, msg)


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/layout/create", vol.Required("name"): TARGET_NAME, vol.Optional("layout"): _doc})
@websocket_api.async_response
async def ws_layout_create(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    """A new layout variant (for tablets that should show something else), starting from the given layout as its draft."""
    home, _ = _home_now(hass)
    try:
        hass.data[DOMAIN]["layouts"].create(msg["name"], msg.get("layout"), home)
    except vol.Invalid as err:
        connection.send_error(msg["id"], "exists", str(err))
        return
    msg = {**msg, "target": msg["name"]}
    await _layout_changed(hass, connection, msg, notify=False)


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/layout/delete", vol.Required("name"): TARGET_NAME})
@websocket_api.async_response
async def ws_layout_delete(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    """Deletes a variant; the tablets that used it go back to the main layout."""
    try:
        hass.data[DOMAIN]["layouts"].delete(msg["name"])
    except vol.Invalid as err:
        connection.send_error(msg["id"], "invalid", str(err))
        return
    panels: Panels = hass.data[DOMAIN]["panels"]
    for pid, p in panels.data.items():
        if p.get("layout") == msg["name"]:
            await panels.update(pid, layout="")
    await _layout_changed(hass, connection, {**msg, "target": MAIN})


# ---------------------------------------------------------------- tablets (panels)


@websocket_api.websocket_command(
    {
        vol.Required("type"): f"{DOMAIN}/panel/connect",
        vol.Required("panel_id"): vol.All(str, vol.Length(min=6, max=64)),
        vol.Optional("name"): vol.All(str, vol.Length(max=60)),
        vol.Optional("model"): vol.All(str, vol.Length(max=80)),
        vol.Optional("app_version"): vol.All(str, vol.Length(max=20)),
    }
)
@websocket_api.async_response
async def ws_panel_connect(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    """A tablet connects: it is registered (an HA device) and receives the services' commands as events."""
    panels: Panels = hass.data[DOMAIN]["panels"]
    pid = msg["panel_id"]

    @callback
    def sender(command: dict[str, Any]) -> None:
        connection.send_message(websocket_api.event_message(msg["id"], command))

    @callback
    def gone() -> None:
        panels.disconnect(pid, sender)

    connection.subscriptions[msg["id"]] = gone
    await panels.connect(pid, msg, sender)
    connection.send_result(msg["id"], {"name": panels.data[pid]["name"], "layout": panels.data[pid].get("layout", "")})


@websocket_api.websocket_command(
    {
        vol.Required("type"): f"{DOMAIN}/panel/state",
        vol.Required("panel_id"): str,
        vol.Optional("motion"): bool,
        vol.Optional("screen_on"): bool,
        vol.Optional("battery"): vol.All(vol.Coerce(int), vol.Range(min=0, max=100)),
        vol.Optional("charging"): bool,
        vol.Optional("brightness"): vol.All(vol.Coerce(int), vol.Range(min=0, max=100)),
        vol.Optional("page"): vol.All(str, vol.Length(max=80)),
    }
)
@callback
def ws_panel_state(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    hass.data[DOMAIN]["panels"].set_state(msg["panel_id"], {k: v for k, v in msg.items() if k not in ("type", "id", "panel_id")})
    connection.send_result(msg["id"])


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/panel/list"})
@callback
def ws_panel_list(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    connection.send_result(msg["id"], {"panels": hass.data[DOMAIN]["panels"].listing(), "layouts": hass.data[DOMAIN]["layouts"].targets()})


def _panel_ok(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> bool:
    if msg["panel_id"] in hass.data[DOMAIN]["panels"].data:
        return True
    connection.send_error(msg["id"], "not_found", "unknown tablet")
    return False


@websocket_api.websocket_command(
    {
        vol.Required("type"): f"{DOMAIN}/panel/update",
        vol.Required("panel_id"): str,
        vol.Optional("name"): vol.All(str, vol.Length(min=1, max=60)),
        vol.Optional("layout"): vol.Any("", TARGET_NAME),
    }
)
@websocket_api.async_response
async def ws_panel_update(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    """Rename a tablet or choose its layout ('' = the main one)."""
    if not _panel_ok(hass, connection, msg):
        return
    layout = msg.get("layout")
    if layout and layout not in hass.data[DOMAIN]["layouts"].data["targets"]:
        connection.send_error(msg["id"], "not_found", "no layout with that name")
        return
    await hass.data[DOMAIN]["panels"].update(msg["panel_id"], msg.get("name"), "" if layout == MAIN else layout)
    hass.bus.async_fire(EVENT_UPDATED)  # that tablet redraws with its layout
    connection.send_result(msg["id"], {"panels": hass.data[DOMAIN]["panels"].listing()})


@websocket_api.websocket_command({vol.Required("type"): f"{DOMAIN}/panel/remove", vol.Required("panel_id"): str})
@websocket_api.async_response
async def ws_panel_remove(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    if not _panel_ok(hass, connection, msg):
        return
    await hass.data[DOMAIN]["panels"].remove(msg["panel_id"])
    connection.send_result(msg["id"], {"panels": hass.data[DOMAIN]["panels"].listing()})


@websocket_api.websocket_command(
    {
        vol.Required("type"): f"{DOMAIN}/panel/command",
        vol.Required("panel_id"): str,
        vol.Required("command"): vol.In(COMMANDS),
        vol.Optional("data", default={}): dict,
    }
)
@callback
def ws_panel_command(hass: HomeAssistant, connection: websocket_api.ActiveConnection, msg: dict) -> None:
    """Sends a command to one tablet (the web panel's test buttons)."""
    if not _panel_ok(hass, connection, msg):
        return
    if not hass.data[DOMAIN]["panels"].send(msg["panel_id"], msg["command"], **msg["data"]):
        connection.send_error(msg["id"], "offline", "the tablet is not connected")
        return
    connection.send_result(msg["id"])
