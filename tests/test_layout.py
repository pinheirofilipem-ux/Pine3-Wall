"""The layout document generated from the settings (custom_components/pine3_wall/layout.py)."""

import json
from pathlib import Path

from homeassistant.core import HomeAssistant

from custom_components.pine3_wall.discovery import build_home
from custom_components.pine3_wall.layout import build_layout
from custom_components.pine3_wall.websocket import DEFAULT_SETTINGS

from .test_contract import LAYOUT_SCHEMA, _validate
from .test_discovery import _setup_home

CATALOG = json.loads((Path(__file__).parents[1] / "schema/catalog.json").read_text())


async def _layout(hass: HomeAssistant, **changes):
    h = await _setup_home(hass)
    settings = {**DEFAULT_SETTINGS, **changes}
    home = build_home(hass, settings)
    return h, home, build_layout(home, settings)


def _types(layout: dict) -> tuple[set[str], set[str]]:
    sections = {s["type"] for p in layout["pages"].values() for s in p["sections"]}
    cards = {c["type"] for p in layout["pages"].values() for s in p["sections"] for c in s.get("cards", [])}
    return sections, cards


def _grid(page: dict) -> list[dict]:
    return next(s for s in page["sections"] if s["type"] == "grid")["cards"]


async def test_default_layout(hass: HomeAssistant) -> None:
    _, home, layout = await _layout(hass)
    _validate(layout, LAYOUT_SCHEMA)
    assert layout["generated"] is True
    assert layout["dock"] == ["home", "rooms", "house", "scenes"]
    page = layout["pages"]["home"]
    assert page["template"] == "top"
    assert [s["type"] for s in page["sections"]] == ["clock", "status", "weather", "scenes", "grid"]
    cards = _grid(page)
    rooms = [c["room"] for c in cards if c["type"] == "room"]
    assert rooms == [r["id"] for r in home["rooms"]]
    # summary cards come after the rooms when room_order doesn't place them
    assert [c["summary"] for c in cards if c["type"] == "summary"] == ["climate", "security", "media"]
    assert cards.index(next(c for c in cards if c["type"] == "summary")) == len(rooms)
    # one page per room, devices grouped by category
    for r in home["rooms"]:
        rp = layout["pages"][f"room/{r['id']}"]
        assert rp["sections"][0] == {"type": "room_header", "room": r["id"]}
        grid = next(s for s in rp["sections"] if s["type"] == "grid")
        assert grid["group"] == "category"
        assert [c["entity"] for c in grid["cards"]] == [d["id"] for d in r["devices"]]


async def test_every_type_is_in_the_catalog(hass: HomeAssistant) -> None:
    _, _, layout = await _layout(hass, home_people=True)
    sections, cards = _types(layout)
    home = CATALOG["modules"]["home"]
    assert sections <= set(home["sections"])
    assert cards <= set(home["cards"])
    assert layout["pages"]["home"]["template"] in home["templates"]


async def test_order_hidden_and_size(hass: HomeAssistant) -> None:
    h = await _setup_home(hass)
    by_name = {r["name"]: r["id"] for r in build_home(hass, DEFAULT_SETTINGS)["rooms"]}
    sala, cozinha = by_name["Sala"], by_name["Cozinha"]
    settings = dict(
        DEFAULT_SETTINGS,
        room_order=["@security", sala],
        hidden_rooms=[cozinha],
        big_rooms=[sala],
        big_devices=[h["tv_id"]],
        hidden=[h["lamp_id"]],
        device_order={sala: [h["tv_id"], h["r2"]]},
    )
    layout = build_layout(build_home(hass, settings), settings)
    cards = _grid(layout["pages"]["home"])
    assert cards[0] == {"type": "summary", "summary": "security"}
    assert cards[1] == {"type": "room", "room": sala, "size": "big"}
    assert all(c.get("room") != cozinha for c in cards)
    assert all(c.get("room") != cozinha for c in _grid(layout["pages"]["rooms"]))
    sala_cards = _grid(layout["pages"][f"room/{sala}"])
    assert [c["entity"] for c in sala_cards][:2] == [h["tv_id"], h["r2"]]
    assert h["lamp_id"] not in [c["entity"] for c in sala_cards]
    assert next(c for c in sala_cards if c["entity"] == h["tv_id"])["size"] == "big"


async def test_flags_and_dock(hass: HomeAssistant) -> None:
    _, _, layout = await _layout(
        hass, show_status=False, show_weather=False, show_scenes=False, show_summary=False,
        home_layout="columns", dock=["security", "climate"], layout="list", clock_24h=False,
    )
    page = layout["pages"]["home"]
    assert page["template"] == "columns"
    assert [s["type"] for s in page["sections"]] == ["clock", "grid"]
    assert all(c["type"] == "room" for c in _grid(page))
    assert layout["dock"] == ["home", "climate", "security"]  # fixed order, Home always there
    assert layout["options"] == {"display": "list", "clock_24h": False, "animations": True}


async def test_vacuum_summary(hass: HomeAssistant) -> None:
    h = await _setup_home(hass)
    home = build_home(hass, DEFAULT_SETTINGS)
    home["rooms"][0]["devices"].append({"id": "vacuum.robot", "name": "Robot", "kind": "vacuum", "extras": [], "hidden": False})
    layout = build_layout(home, DEFAULT_SETTINGS)
    summaries = [c for c in _grid(layout["pages"]["home"]) if c["type"] == "summary"]
    assert {"type": "summary", "summary": "vacuum", "entity": "vacuum.robot"} in summaries
    assert h  # fixture used


def test_catalog_faces_and_modules() -> None:
    ambient = CATALOG["modules"]["ambient"]
    assert set(ambient["sections"]) == {"ambient"}
    assert set(ambient["faces"]) == {"clock_analog", "clock_digital", "date", "weather", "sensor", "people"}
