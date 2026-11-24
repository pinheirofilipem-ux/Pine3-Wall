"""Edited layouts: draft, publish, history, preview, keeping in step with the home and the app's edit mode."""

import copy

import pytest
from homeassistant.core import HomeAssistant
from homeassistant.helpers import area_registry as ar

from custom_components.pine3_wall.discovery import build_home
from custom_components.pine3_wall.layout import build_layout
from custom_components.pine3_wall.layout_store import apply_edit, known_of, resolve
from custom_components.pine3_wall.websocket import DEFAULT_SETTINGS, async_setup_api

from .test_contract import LAYOUT_SCHEMA, _validate
from .test_discovery import _setup_home


def _grid(page):
    return next(s for s in page["sections"] if s["type"] == "grid")["cards"]


async def _ws(hass, hass_ws_client):
    h = await _setup_home(hass)
    await async_setup_api(hass)
    ws = await hass_ws_client(hass)
    n = iter(range(1, 1000))

    async def call(type_, **kw):
        await ws.send_json({"id": next(n), "type": f"pine3_wall/{type_}", **kw})
        while True:
            m = await ws.receive_json()
            if m["type"] == "result":
                return m

    return h, call


async def test_draft_publish_history_reset(hass: HomeAssistant, hass_ws_client) -> None:
    _, call = await _ws(hass, hass_ws_client)
    got = (await call("layout/get"))["result"]
    assert got["source"] == "auto" and got["draft"] is None and got["published"] is None
    doc = copy.deepcopy(got["auto"])
    doc["pages"]["home"]["template"] = "columns"
    doc["pages"]["home"]["sections"] = [s for s in doc["pages"]["home"]["sections"] if s["type"] != "weather"]

    got = (await call("layout/save_draft", layout=doc))["result"]
    assert got["draft"]["pages"]["home"]["template"] == "columns"
    home = (await call("home"))["result"]
    assert home["layout_source"] == "auto"  # a draft alone changes nothing on the tablets

    got = (await call("layout/publish"))["result"]
    assert got["source"] == "published" and got["draft"] is None
    home = (await call("home"))["result"]
    assert home["layout_source"] == "published" and home["layout"]["generated"] is False
    assert home["layout"]["pages"]["home"]["template"] == "columns"
    _validate(home["layout"], LAYOUT_SCHEMA)

    got = (await call("layout/reset"))["result"]
    assert got["source"] == "auto" and len(got["history"]) == 1
    got = (await call("layout/restore", index=0))["result"]
    assert got["draft"]["pages"]["home"]["template"] == "columns"
    assert not (await call("layout/restore", index=5))["success"]
    got = (await call("layout/discard_draft"))["result"]
    assert got["draft"] is None


async def test_preview_shows_the_draft(hass: HomeAssistant, hass_ws_client) -> None:
    _, call = await _ws(hass, hass_ws_client)
    doc = copy.deepcopy((await call("layout/get"))["result"]["auto"])
    doc["pages"]["home"]["template"] = "sidebar"
    await call("layout/save_draft", layout=doc)
    await call("layout/preview", on=True)
    home = (await call("home"))["result"]
    assert home["layout_source"] == "preview" and home["layout"]["pages"]["home"]["template"] == "sidebar"
    await call("layout/publish")  # publishing ends the preview
    home = (await call("home"))["result"]
    assert home["layout_source"] == "published"


@pytest.mark.parametrize("bad", [
    {"schema_version": 1, "options": {}, "dock": ["home"], "pages": {}},  # no home page
    {"schema_version": 2, "options": {}, "dock": ["home"], "pages": {"home": {"sections": []}}},
    {"schema_version": 1, "options": {}, "dock": [], "pages": {"home": {"sections": []}}},
    {"schema_version": 1, "options": {}, "dock": ["home"], "pages": {"home": {"sections": [{"type": "grid", "cards": [{"size": "huge", "type": "room"}]}]}}},
])
async def test_rejects_bad_layouts(hass: HomeAssistant, hass_ws_client, bad) -> None:
    _, call = await _ws(hass, hass_ws_client)
    assert not (await call("layout/save_draft", layout=bad))["success"]


async def test_resolve_follows_the_home(hass: HomeAssistant) -> None:
    h = await _setup_home(hass)
    home = build_home(hass, DEFAULT_SETTINGS)
    doc = {**build_layout(home, DEFAULT_SETTINGS), "known": known_of(home)}
    sala = ar.async_get(hass).async_get_area_by_name("Sala").id
    # the user removed the TV from the room page and the Sala card from the home grid
    for c in list(_grid(doc["pages"][f"room/{sala}"])):
        if c["entity"] == h["tv_id"]:
            _grid(doc["pages"][f"room/{sala}"]).remove(c)
    doc["pages"]["home"]["sections"][-1]["cards"] = [c for c in _grid(doc["pages"]["home"]) if c.get("room") != sala]

    # a new device in Sala, a new room, and a device that disappears
    new_home = copy.deepcopy(home)
    room = next(r for r in new_home["rooms"] if r["id"] == sala)
    room["devices"].append({"id": "light.novo", "name": "Novo", "kind": "light", "extras": [], "hidden": False})
    room["devices"] = [d for d in room["devices"] if d["id"] != h["r1"]]
    new_home["rooms"].append({**copy.deepcopy(room), "id": "escritorio", "name": "Escritório", "devices": [], "scenes": []})

    out = resolve(doc, new_home, DEFAULT_SETTINGS)
    sala_cards = [c["entity"] for c in _grid(out["pages"][f"room/{sala}"])]
    assert "light.novo" in sala_cards  # new device added
    assert h["r1"] not in sala_cards  # gone device dropped
    assert h["tv_id"] not in sala_cards  # removed on purpose stays removed
    home_rooms = [c.get("room") for c in _grid(out["pages"]["home"])]
    assert "escritorio" in home_rooms and sala not in home_rooms
    assert "room/escritorio" in out["pages"]
    assert out["generated"] is False


async def test_app_edit_on_a_published_layout(hass: HomeAssistant, hass_ws_client) -> None:
    h, call = await _ws(hass, hass_ws_client)
    assert not (await call("layout/edit", hidden_rooms=[]))["success"]  # automatic: edit the settings instead
    auto = (await call("layout/get"))["result"]["auto"]
    doc = copy.deepcopy(auto)
    doc["pages"]["home"]["template"] = "classic"
    home_cards = _grid(doc["pages"]["home"])
    doc["pages"]["home"]["sections"][-1]["cards"] = [c for c in home_cards if c.get("summary") != "media"]
    await call("layout/publish", layout=doc)

    sala = ar.async_get(hass).async_get_area_by_name("Sala").id
    cozinha = ar.async_get(hass).async_get_area_by_name("Cozinha").id
    got = await call("layout/edit", room_order=[sala, "@climate"], hidden_rooms=[cozinha], big_rooms=[sala],
                     hidden=[h["lamp_id"]], big_devices=[h["tv_id"]], device_order={sala: [h["tv_id"]]})
    assert got["success"]
    layout = (await call("home"))["result"]["layout"]
    cards = _grid(layout["pages"]["home"])
    assert cards[0] == {"type": "room", "room": sala, "size": "big"}
    assert all(c.get("room") != cozinha for c in cards)
    assert not any(c.get("summary") == "media" for c in cards)  # removed in the editor: stays out
    assert layout["pages"]["home"]["template"] == "classic"  # the rest of the layout is kept
    sala_cards = _grid(layout["pages"][f"room/{sala}"])
    assert sala_cards[0] == {"type": "device", "entity": h["tv_id"], "size": "big"}
    assert h["lamp_id"] not in [c["entity"] for c in sala_cards]
    settings = (await call("settings"))["result"]
    assert settings["hidden_rooms"] == []  # the automatic layout's settings are untouched


async def test_apply_edit_keeps_other_modules(hass: HomeAssistant) -> None:
    await _setup_home(hass)
    home = build_home(hass, DEFAULT_SETTINGS)
    doc = build_layout(home, DEFAULT_SETTINGS)
    _grid(doc["pages"]["home"]).append({"type": "calendar_upcoming"})
    out = apply_edit(doc, home, DEFAULT_SETTINGS, {"room_order": []})
    assert _grid(out["pages"]["home"])[-1] == {"type": "calendar_upcoming"}


async def test_app_edit_goes_to_the_draft_on_preview(hass: HomeAssistant, hass_ws_client) -> None:
    """While the tablets show the draft, the tablet's edit mode changes the draft (what it is showing)."""
    _, call = await _ws(hass, hass_ws_client)
    auto = (await call("layout/get"))["result"]["auto"]
    await call("layout/publish", layout=copy.deepcopy(auto))
    await call("layout/save_draft", layout=copy.deepcopy(auto))
    await call("layout/preview", on=True)
    sala = ar.async_get(hass).async_get_area_by_name("Sala").id
    assert (await call("layout/edit", room_order=[sala], big_rooms=[sala]))["success"]
    got = (await call("layout/get"))["result"]
    assert _grid(got["draft"]["pages"]["home"])[0] == {"type": "room", "room": sala, "size": "big"}
    assert _grid(got["published"]["pages"]["home"])[0].get("size") == "normal"  # the published one is untouched
    home = (await call("home"))["result"]
    assert _grid(home["layout"]["pages"]["home"])[0]["room"] == sala


async def test_ambient_screens_are_kept(hass: HomeAssistant, hass_ws_client) -> None:
    """Module ambient: 'ambient/<id>' pages (1-3 faces) are stored, published and can go in the dock."""
    _, call = await _ws(hass, hass_ws_client)
    doc = copy.deepcopy((await call("layout/get"))["result"]["auto"])
    doc["pages"]["ambient/noite"] = {"title": "Noite", "sections": [{"type": "ambient", "color": "#f6b83f", "night": True,
                                     "faces": [{"type": "clock_analog", "style": "bold"}, {"type": "weather"}]}]}
    doc["dock"].append("ambient/noite")
    assert (await call("layout/publish", layout=doc))["success"]
    layout = (await call("home"))["result"]["layout"]
    _validate(layout, LAYOUT_SCHEMA)
    assert layout["pages"]["ambient/noite"]["sections"][0]["faces"][0]["style"] == "bold"
    assert "ambient/noite" in layout["dock"]
    too_many = copy.deepcopy(doc)
    too_many["pages"]["ambient/noite"]["sections"][0]["faces"] *= 2
    assert not (await call("layout/save_draft", layout=too_many))["success"]
