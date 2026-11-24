"""Wall tablets (panels) known to Home Assistant.

Each Pine3 Wall app connects with pine3_wall/panel/connect and stays subscribed: it becomes a device in Home
Assistant (motion, screen, battery, brightness, page, online, app version and a Wake button) and receives the
commands of the pine3_wall.* services (wake, show_page, show_camera, notify, screensaver). The app reports its
state with pine3_wall/panel/state. Per tablet it is also stored which layout it uses (main or a variant).
"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Callable

from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers import device_registry as dr
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.storage import Store

from .const import DOMAIN

STORAGE_KEY = f"{DOMAIN}.panels"
SIGNAL_NEW = f"{DOMAIN}_panel_new"
STATE_KEYS = ("motion", "screen_on", "battery", "charging", "brightness", "page")
COMMANDS = ("wake", "show_page", "show_camera", "notify", "screensaver")


def signal_update(panel_id: str) -> str:
    return f"{DOMAIN}_panel_update_{panel_id}"


def _now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


class Panels:
    """Registered tablets, their last state and the live connection of each one."""

    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._store: Store = Store(hass, 1, STORAGE_KEY)
        self.data: dict[str, dict[str, Any]] = {}
        self.state: dict[str, dict[str, Any]] = {}  # not stored: comes from the app while it is connected
        self._senders: dict[str, Callable[[dict[str, Any]], None]] = {}
        self.entry_id: str | None = None

    async def async_load(self) -> None:
        self.data = (await self._store.async_load() or {}).get("panels", {})

    async def async_save(self) -> None:
        await self._store.async_save({"panels": self.data})

    # ------------------------------------------------------------ registry

    def device_info(self, panel_id: str) -> dict[str, Any]:
        p = self.data.get(panel_id, {})
        return {
            "identifiers": {(DOMAIN, panel_id)},
            "name": f"Pine3 Wall {p.get('name') or panel_id[:6]}",
            "manufacturer": "Pine3",
            "model": p.get("model") or "Wall tablet",
            "sw_version": p.get("app_version"),
        }

    @callback
    def _update_device(self, panel_id: str) -> None:
        """Keeps the HA device's name, model and version in step (after the entities created it)."""
        reg = dr.async_get(self.hass)
        dev = reg.async_get_device(identifiers={(DOMAIN, panel_id)})
        if dev:
            info = self.device_info(panel_id)
            reg.async_update_device(dev.id, name=info["name"], model=info["model"], sw_version=info["sw_version"])

    def panel_of_device(self, device_id: str) -> str | None:
        dev = dr.async_get(self.hass).async_get(device_id)
        if dev:
            for domain, ident in dev.identifiers:
                if domain == DOMAIN and ident in self.data:
                    return ident
        return None

    async def connect(self, panel_id: str, info: dict[str, Any], sender: Callable[[dict[str, Any]], None]) -> bool:
        """A tablet connected. True when it is new (its entities are then created)."""
        new = panel_id not in self.data
        p = self.data.setdefault(panel_id, {"name": info.get("name") or panel_id[:6], "layout": ""})
        if info.get("name") and new:
            p["name"] = info["name"]
        p.update({k: info[k] for k in ("model", "app_version") if info.get(k)})
        p["last_seen"] = _now()
        self._senders[panel_id] = sender
        self.state.setdefault(panel_id, {})
        await self.async_save()
        if new:
            async_dispatcher_send(self.hass, SIGNAL_NEW, panel_id)
        else:
            self._update_device(panel_id)
        async_dispatcher_send(self.hass, signal_update(panel_id))
        return new

    @callback
    def disconnect(self, panel_id: str, sender: Callable) -> None:
        if self._senders.get(panel_id) is sender:
            del self._senders[panel_id]
            if panel_id in self.data:
                self.data[panel_id]["last_seen"] = _now()
            async_dispatcher_send(self.hass, signal_update(panel_id))

    def online(self, panel_id: str) -> bool:
        return panel_id in self._senders

    @callback
    def set_state(self, panel_id: str, state: dict[str, Any]) -> None:
        if panel_id not in self.data:
            return
        self.state.setdefault(panel_id, {}).update({k: v for k, v in state.items() if k in STATE_KEYS})
        self.data[panel_id]["last_seen"] = _now()
        async_dispatcher_send(self.hass, signal_update(panel_id))

    async def update(self, panel_id: str, name: str | None = None, layout: str | None = None) -> None:
        p = self.data[panel_id]
        if name is not None:
            p["name"] = name
        if layout is not None:
            p["layout"] = layout
        await self.async_save()
        self._update_device(panel_id)
        async_dispatcher_send(self.hass, signal_update(panel_id))

    async def remove(self, panel_id: str) -> None:
        self.data.pop(panel_id, None)
        self.state.pop(panel_id, None)
        await self.async_save()
        reg = dr.async_get(self.hass)
        dev = reg.async_get_device(identifiers={(DOMAIN, panel_id)})
        if dev:
            reg.async_remove_device(dev.id)

    def layout_of(self, panel_id: str | None) -> str | None:
        return self.data.get(panel_id or "", {}).get("layout") or None

    # ------------------------------------------------------------ commands

    @callback
    def send(self, panel_id: str, command: str, **data: Any) -> bool:
        """Sends a command to a connected tablet. False when it is offline."""
        sender = self._senders.get(panel_id)
        if sender is None:
            return False
        sender({"command": command, **{k: v for k, v in data.items() if v is not None}})
        return True

    def listing(self) -> list[dict[str, Any]]:
        """For the web panel's Settings > Tablets."""
        return [
            {"id": pid, **p, "online": self.online(pid), "state": self.state.get(pid, {})}
            for pid, p in sorted(self.data.items(), key=lambda kv: kv[1].get("name", "").lower())
        ]
