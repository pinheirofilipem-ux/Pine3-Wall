"""The layout document: what each screen shows, in which order and size (schema/layout.schema.json).

For now it is generated from the shared settings (home layout, dock, room and device order, hidden and
double-size items), so it describes exactly what the app and the web panel already draw. The web editor
(phase 3) will store edited documents; this generator stays as the automatic default.
"""

from __future__ import annotations

from typing import Any

LAYOUT_SCHEMA_VERSION = 1
SUMMARY_CARDS = ("climate", "security", "vacuum", "media")
DOCK_ITEMS = ("home", "rooms", "house", "scenes", "climate", "security")


def _visible_devices(home: dict[str, Any]) -> list[dict[str, Any]]:
    return [d for r in home["rooms"] if not r["hidden"] for d in r["devices"] if not d["hidden"]]


def _ordered(items: list[tuple[str, dict]], order: list[str]) -> list[dict]:
    """Same rule as the panels: keys in `order` first, by that order; the rest after, in their own order."""

    def pos(item: tuple[int, tuple[str, dict]]) -> int:
        i, (key, _) = item
        return order.index(key) if key in order else 10_000 + i

    return [card for _, (_, card) in sorted(enumerate(items), key=pos)]


def _size(key: str, big: list[str]) -> str:
    return "big" if key in big else "normal"


def _home_page(home: dict[str, Any], s: dict[str, Any]) -> dict[str, Any]:
    sections: list[dict[str, Any]] = [{"type": "clock"}]
    if s.get("home_people"):
        sections.append({"type": "people"})
    if s.get("show_status", True):
        sections.append({"type": "status"})
    if s.get("show_weather", True):
        sections.append({"type": "weather"})
    if s.get("show_scenes", True) and home["scenes"]:
        sections.append({"type": "scenes", "items": home["scenes"]})

    big_rooms = s.get("big_rooms", [])
    items: list[tuple[str, dict]] = [
        (r["id"], {"type": "room", "room": r["id"], "size": _size(r["id"], big_rooms)})
        for r in home["rooms"]
        if not r["hidden"]
    ]
    if s.get("show_summary", True):
        items.append(("@climate", {"type": "summary", "summary": "climate"}))
        items.append(("@security", {"type": "summary", "summary": "security"}))
        for d in _visible_devices(home):
            if d["kind"] == "vacuum":
                items.append((f"@vacuum:{d['id']}", {"type": "summary", "summary": "vacuum", "entity": d["id"]}))
        items.append(("@media", {"type": "summary", "summary": "media"}))
    sections.append({"type": "grid", "cards": _ordered(items, s.get("room_order", []))})
    return {"template": s.get("home_layout", "top"), "sections": sections}


def _rooms_page(home: dict[str, Any], s: dict[str, Any]) -> dict[str, Any]:
    big_rooms = s.get("big_rooms", [])
    items = [(r["id"], {"type": "room", "room": r["id"], "size": _size(r["id"], big_rooms)}) for r in home["rooms"] if not r["hidden"]]
    return {"sections": [{"type": "grid", "cards": _ordered(items, s.get("room_order", []))}]}


def _room_page(room: dict[str, Any], s: dict[str, Any]) -> dict[str, Any]:
    big = s.get("big_devices", [])
    items = [(d["id"], {"type": "device", "entity": d["id"], "size": _size(d["id"], big)}) for d in room["devices"] if not d["hidden"]]
    sections: list[dict[str, Any]] = [{"type": "room_header", "room": room["id"]}]
    if room["scenes"]:
        sections.append({"type": "scenes", "items": room["scenes"]})
    # "category": the renderer groups the cards by kind (lights, climate, covers...) keeping this order inside each group.
    sections.append({"type": "grid", "group": "category", "cards": _ordered(items, s.get("device_order", {}).get(room["id"], []))})
    return {"room": room["id"], "sections": sections}


def build_layout(home: dict[str, Any], settings: dict[str, Any]) -> dict[str, Any]:
    """The layout document for a home model (discovery.build_home) and the shared settings."""
    pages: dict[str, Any] = {
        "home": _home_page(home, settings),
        "rooms": _rooms_page(home, settings),
        "house": {"sections": [{"type": "house3d"}]},
        "scenes": {"sections": [{"type": "scene_list"}]},
        "climate": {"sections": [{"type": "climate_list"}]},
        "security": {"sections": [{"type": "security_list"}]},
    }
    for room in home["rooms"]:
        pages[f"room/{room['id']}"] = _room_page(room, settings)
    # Same rule as the panels: the fixed order of DOCK_ITEMS, with Home always there.
    chosen = settings.get("dock") or []
    dock = [k for k in DOCK_ITEMS if k == "home" or k in chosen]
    return {
        "schema_version": LAYOUT_SCHEMA_VERSION,
        "generated": True,
        "options": {
            "display": settings.get("layout", "grid"),
            "clock_24h": settings.get("clock_24h", True),
            "animations": settings.get("animations", True),
        },
        "dock": dock,
        "pages": pages,
    }
