"""Tablet binary sensors: online, motion (front camera) and screen on."""

from __future__ import annotations

from homeassistant.components.binary_sensor import BinarySensorDeviceClass, BinarySensorEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity import EntityCategory
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .entity import PanelEntity, setup_panel_platform


class OnlineSensor(PanelEntity, BinarySensorEntity):
    always_available = True
    _attr_device_class = BinarySensorDeviceClass.CONNECTIVITY
    _attr_entity_category = EntityCategory.DIAGNOSTIC

    @property
    def is_on(self) -> bool:
        return self.panels.online(self.panel_id)


class StateSensor(PanelEntity, BinarySensorEntity):
    def __init__(self, panels, panel_id, key, device_class=None) -> None:
        super().__init__(panels, panel_id, key)
        self._key = key
        self._attr_device_class = device_class

    @property
    def is_on(self) -> bool | None:
        v = self.panel_state.get(self._key)
        return None if v is None else bool(v)


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback) -> None:
    setup_panel_platform(hass, entry, async_add_entities, lambda panels, pid: [
        OnlineSensor(panels, pid, "online"),
        StateSensor(panels, pid, "motion", BinarySensorDeviceClass.MOTION),
        StateSensor(panels, pid, "screen_on"),
    ])
