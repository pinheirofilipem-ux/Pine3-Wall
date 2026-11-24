"""Entities of a wall tablet (one HA device per tablet)."""

from __future__ import annotations

from collections.abc import Callable
from typing import Any

from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.device_registry import DeviceInfo
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity import Entity

from .const import DOMAIN
from .panels import SIGNAL_NEW, Panels, signal_update


class PanelEntity(Entity):
    """Base: follows the tablet's updates; available while the tablet is connected (except 'online')."""

    _attr_has_entity_name = True
    _attr_should_poll = False
    always_available = False

    def __init__(self, panels: Panels, panel_id: str, key: str) -> None:
        self.panels = panels
        self.panel_id = panel_id
        self._attr_unique_id = f"{panel_id}_{key}"
        self._attr_translation_key = key
        self._attr_device_info = DeviceInfo(**panels.device_info(panel_id))

    @property
    def available(self) -> bool:
        return self.always_available or self.panels.online(self.panel_id)

    @property
    def panel_state(self) -> dict[str, Any]:
        return self.panels.state.get(self.panel_id, {})

    async def async_added_to_hass(self) -> None:
        self.async_on_remove(async_dispatcher_connect(self.hass, signal_update(self.panel_id), self.async_write_ha_state))


def setup_panel_platform(
    hass: HomeAssistant, entry: ConfigEntry, async_add_entities: Callable, make: Callable[[Panels, str], list[Entity]]
) -> None:
    """Adds the entities of every known tablet now, and of each new one when it first connects."""
    panels: Panels = hass.data[DOMAIN]["panels"]
    async_add_entities([e for pid in panels.data for e in make(panels, pid)])

    @callback
    def _new(panel_id: str) -> None:
        async_add_entities(make(panels, panel_id))

    entry.async_on_unload(async_dispatcher_connect(hass, SIGNAL_NEW, _new))
