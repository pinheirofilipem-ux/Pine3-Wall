"""Edited layouts: a draft, the published layout and a short history (the web editor saves them here).

While nothing is published the layout is the automatic one (layout.py, from the settings). A published
layout is kept in step with the home: rooms and devices that no longer exist are dropped, and new ones
are added at the end of their grids (it remembers which rooms and devices it already knew, so something
the user removed on purpose doesn't come back).
"""

from __future__ import annotations

import copy
import json
from datetime import datetime, timezone
from typing import Any

import voluptuous as vol

from homeassistant.core import HomeAssistant
from homeassistant.helpers.storage import Store

from .const import DOMAIN
from .layout import LAYOUT_SCHEMA_VERSION, build_layout

STORAGE_KEY = f"{DOMAIN}.layouts"
HISTORY_MAX = 10
MAX_DOC_BYTES = 256 * 1024

_CARD = vol.Schema(
    {
        vol.Required("type"): vol.All(str, vol.Length(min=1, max=64)),
        vol.Optional("size"): vol.In(["normal", "big"]),
        vol.Optional("room"): str,
        vol.Optional("entity"): str,
        vol.Optional("summary"): str,
    },
    extra=vol.ALLOW_EXTRA,
)
_SECTION = vol.Schema(
    {
        vol.Required("type"): vol.All(str, vol.Length(min=1, max=64)),
        vol.Optional("items"): [str],
        vol.Optional("room"): str,
        vol.Optional("group"): vol.In(["category"]),
        vol.Optional("cards"): [_CARD],
        # module ambient: a composed screen
        vol.Optional("faces"): vol.All([vol.Schema({vol.Required("type"): vol.All(str, vol.Length(min=1, max=40))}, extra=vol.ALLOW_EXTRA)], vol.Length(max=3)),
        vol.Optional("color"): vol.Match(r"^#[0-9a-fA-F]{6}$"),
        vol.Optional("night"): bool,
    },
    extra=vol.ALLOW_EXTRA,
)
_PAGE = vol.Schema(
    {vol.Required("sections"): [_SECTION], vol.Optional("template"): str, vol.Optional("room"): str},
    extra=vol.ALLOW_EXTRA,
)


def _has_home(pages: dict) -> dict:
    if "home" not in pages:
        raise vol.Invalid("the layout needs a 'home' page")
    return pages


LAYOUT_DOC = vol.Schema(
    {
        vol.Required("schema_version"): LAYOUT_SCHEMA_VERSION,
        vol.Optional("generated"): bool,
        vol.Required("options"): vol.Schema(
            {vol.Optional("display"): vol.In(["grid", "list"]), vol.Optional("clock_24h"): bool, vol.Optional("animations"): bool},
            extra=vol.ALLOW_EXTRA,
        ),
        vol.Required("dock"): vol.All([str], vol.Length(min=1)),
        vol.Required("pages"): vol.All({str: _PAGE}, _has_home),
        vol.Optional("known"): {vol.Optional("rooms"): [str], vol.Optional("devices"): [str]},
    },
    extra=vol.ALLOW_EXTRA,
)


def validate_doc(doc: Any) -> dict[str, Any]:
    """A layout sent by the editor, checked (shape and size) before it is stored."""
    if len(json.dumps(doc)) > MAX_DOC_BYTES:
        raise vol.Invalid("layout too large")
    return LAYOUT_DOC(doc)


def _now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def _all_devices(home: dict[str, Any]) -> list[dict[str, Any]]:
    return [d for r in home["rooms"] for d in r["devices"]]


def known_of(home: dict[str, Any]) -> dict[str, list[str]]:
    """The rooms and devices that exist now (stored with a published layout)."""
    return {"rooms": [r["id"] for r in home["rooms"]], "devices": [d["id"] for d in _all_devices(home)]}


def _grid(page: dict | None) -> dict | None:
    return next((s for s in page["sections"] if s.get("type") == "grid"), None) if page else None


def resolve(doc: dict[str, Any], home: dict[str, Any], settings: dict[str, Any]) -> dict[str, Any]:
    """A stored layout, brought in step with the home as it is now."""
    out = copy.deepcopy(doc)
    out["generated"] = False
    auto = build_layout(home, settings)
    rooms = {r["id"]: r for r in home["rooms"]}
    devices = {d["id"] for d in _all_devices(home)}
    known = out.get("known") or known_of(home)
    known_rooms, known_devices = set(known.get("rooms", [])), set(known.get("devices", []))

    def alive(card: dict) -> bool:
        t = card.get("type")
        if t == "room":
            return card.get("room") in rooms
        if t == "device":
            return card.get("entity") in devices
        if t == "summary" and card.get("summary") == "vacuum":
            return card.get("entity") in devices
        return True

    pages = out["pages"]
    for key in list(pages):
        if key.startswith("room/") and key[5:] not in rooms:
            del pages[key]
            continue
        for section in pages[key]["sections"]:
            if "cards" in section:
                section["cards"] = [c for c in section["cards"] if alive(c)]

    # fixed pages added by a newer version (e.g. "calendar"): they come in as in the automatic layout
    for key, page in auto["pages"].items():
        if not key.startswith("room/") and key not in pages:
            pages[key] = copy.deepcopy(page)

    for rid, room in rooms.items():
        page_key = f"room/{rid}"
        if page_key not in pages:
            pages[page_key] = copy.deepcopy(auto["pages"][page_key])
        elif rid in known_rooms:
            grid = _grid(pages[page_key])
            if grid is not None:
                have = {c.get("entity") for c in grid.get("cards", [])}
                grid.setdefault("cards", []).extend(
                    {"type": "device", "entity": d["id"], "size": "normal"}
                    for d in room["devices"]
                    if d["id"] not in known_devices and d["id"] not in have
                )
        if rid not in known_rooms:
            for page in (pages.get("home"), pages.get("rooms")):
                grid = _grid(page)
                if grid is not None and all(c.get("room") != rid for c in grid.get("cards", [])):
                    grid.setdefault("cards", []).append({"type": "room", "room": rid, "size": "normal"})
    return out


def apply_edit(doc: dict[str, Any], home: dict[str, Any], settings: dict[str, Any], edit: dict[str, Any]) -> dict[str, Any]:
    """The app's edit mode on a published layout: order, hidden and double size of rooms and devices.

    The same edit applied to the settings would give the automatic layout; its grids replace the home,
    rooms and room grids of the published one (keeping only the summary cards it had, and any card of
    another module at the end).
    """
    out = resolve(doc, home, settings)
    edited = {**settings, **{k: v for k, v in edit.items() if v is not None}, "show_summary": True}
    # the edit's hidden lists apply only here, not to the settings (they drive the automatic layout)
    home = copy.deepcopy(home)
    for r in home["rooms"]:
        r["hidden"] = r["id"] in edited.get("hidden_rooms", [])
        for d in r["devices"]:
            d["hidden"] = d["id"] in edited.get("hidden", [])
    auto = build_layout(home, edited)

    def merge(page_key: str, keep_summaries: bool) -> None:
        mine, theirs = _grid(out["pages"].get(page_key)), _grid(auto["pages"].get(page_key))
        if mine is None or theirs is None:
            return
        old = mine.get("cards", [])
        summaries = {(c.get("summary"), c.get("entity")) for c in old if c.get("type") == "summary"}
        cards = [
            c for c in theirs["cards"]
            if c["type"] != "summary" or (keep_summaries and (c.get("summary"), c.get("entity")) in summaries)
        ]
        cards += [c for c in old if c.get("type") not in ("room", "device", "summary")]
        mine["cards"] = cards

    merge("home", True)
    merge("rooms", False)
    for r in home["rooms"]:
        merge(f"room/{r['id']}", False)
    out["known"] = known_of(home)
    return out


MAIN = "main"
TARGET_NAME = vol.All(str, vol.Length(min=1, max=40))


def _empty_target() -> dict[str, Any]:
    return {"draft": None, "published": None, "history": [], "preview": False}


class LayoutStore:
    """The editor's layouts in Home Assistant's storage (pine3_wall.layouts).

    There is one set of layouts per *target*: "main" (every tablet by default) and named variants that a tablet can
    use instead (Settings > Tablets). Each target has its own draft, published layout, history and preview switch.
    A variant with nothing published nor on preview shows the main one.
    """

    def __init__(self, hass: HomeAssistant) -> None:
        self._store: Store = Store(hass, 1, STORAGE_KEY)
        self.data: dict[str, Any] = {"targets": {MAIN: _empty_target()}}

    async def async_load(self) -> None:
        stored = await self._store.async_load() or {}
        if "targets" in stored:
            self.data = stored
        elif stored:  # 0.7: one set of layouts, now the main target
            self.data = {"targets": {MAIN: {**_empty_target(), **stored}}}
        self.data["targets"].setdefault(MAIN, _empty_target())

    async def async_save(self) -> None:
        await self._store.async_save(self.data)

    def targets(self) -> list[str]:
        return [MAIN] + sorted(k for k in self.data["targets"] if k != MAIN)

    def t(self, target: str | None = None) -> dict[str, Any]:
        """One target's layouts (KeyError for a variant that doesn't exist)."""
        return self.data["targets"][target or MAIN]

    def create(self, name: str, doc: dict[str, Any] | None, home: dict[str, Any]) -> None:
        if name in self.data["targets"]:
            raise vol.Invalid("a layout with that name already exists")
        self.data["targets"][name] = _empty_target()
        if doc is not None:
            self.save_draft(doc, home, name)

    def delete(self, name: str) -> None:
        if name == MAIN:
            raise vol.Invalid("the main layout can't be deleted")
        self.data["targets"].pop(name, None)

    def _remember(self, t: dict[str, Any], doc: dict | None, label: str) -> None:
        if doc is None:
            return
        t["history"] = ([{"at": _now(), "label": label, "doc": doc}] + t["history"])[:HISTORY_MAX]

    def active(self, home: dict[str, Any], settings: dict[str, Any], target: str | None = None) -> tuple[dict[str, Any], str]:
        """The layout a tablet draws and where it comes from: 'preview' (the draft), 'published' or 'auto'."""
        for key in ([target] if target and target != MAIN and target in self.data["targets"] else []) + [MAIN]:
            t = self.t(key)
            if t.get("preview") and t.get("draft"):
                return resolve(t["draft"], home, settings), "preview"
            if t.get("published"):
                return resolve(t["published"], home, settings), "published"
        return build_layout(home, settings), "auto"

    def summary(self, home: dict[str, Any], settings: dict[str, Any], target: str | None = None) -> dict[str, Any]:
        """Everything the editor needs, for one target."""
        target = target or MAIN
        t = self.t(target)
        _, source = self.active(home, settings, target)
        return {
            "target": target,
            "targets": self.targets(),
            "source": source,
            "preview": bool(t.get("preview")),
            "draft": resolve(t["draft"], home, settings) if t.get("draft") else None,
            "published": resolve(t["published"], home, settings) if t.get("published") else None,
            "auto": build_layout(home, settings),
            "history": [{"at": h["at"], "label": h["label"]} for h in t["history"]],
        }

    def save_draft(self, doc: dict[str, Any], home: dict[str, Any], target: str | None = None) -> None:
        self.t(target)["draft"] = {**doc, "generated": False, "known": known_of(home)}

    def discard_draft(self, target: str | None = None) -> None:
        t = self.t(target)
        t["draft"] = None
        t["preview"] = False

    def publish(self, doc: dict[str, Any] | None, home: dict[str, Any], target: str | None = None) -> None:
        t = self.t(target)
        doc = doc if doc is not None else t.get("draft")
        if doc is None:
            raise vol.Invalid("nothing to publish")
        self._remember(t, t.get("published"), "published")
        t["published"] = {**doc, "generated": False, "known": known_of(home)}
        t["draft"] = None
        t["preview"] = False

    def reset(self, target: str | None = None) -> None:
        """Back to the automatic layout (the published one goes to the history)."""
        t = self.t(target)
        self._remember(t, t.get("published"), "published")
        t["published"] = None
        t["draft"] = None
        t["preview"] = False

    def restore(self, index: int, target: str | None = None) -> None:
        """A layout from the history becomes the draft."""
        t = self.t(target)
        try:
            t["draft"] = copy.deepcopy(t["history"][index]["doc"])
        except IndexError as err:
            raise vol.Invalid("no such layout in the history") from err

    def set_preview(self, on: bool, target: str | None = None) -> None:
        self.t(target)["preview"] = on

    def edit(self, home: dict[str, Any], settings: dict[str, Any], edit: dict[str, Any], target: str | None = None) -> bool:
        """The app's edit mode on the layout the tablet shows: the draft while it is on preview, else the published one.
        A variant with nothing of its own edits the main layout. False when the layout is the automatic one."""
        names = ([target] if target and target != MAIN and target in self.data["targets"] else []) + [MAIN]
        for name in names:
            t = self.t(name)
            key = "draft" if t.get("preview") and t.get("draft") else "published" if t.get("published") else None
            if key:
                t[key] = apply_edit(t[key], home, settings, edit)
                return True
        return False
