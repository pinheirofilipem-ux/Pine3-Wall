"""Tablet button: wake the screen."""

from __future__ import annotations

from homeassistant.components.button import ButtonEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .entity import PanelEntity, setup_panel_platform


class WakeButton(PanelEntity, ButtonEntity):
    async def async_press(self) -> None:
        self.panels.send(self.panel_id, "wake")


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback) -> None:
    setup_panel_platform(hass, entry, async_add_entities, lambda panels, pid: [WakeButton(panels, pid, "wake")])
