"""Tablet sensors: battery, screen brightness and the page on screen."""

from __future__ import annotations

from homeassistant.components.sensor import SensorDeviceClass, SensorEntity, SensorStateClass
from homeassistant.config_entries import ConfigEntry
from homeassistant.const import PERCENTAGE
from homeassistant.core import HomeAssistant
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .entity import PanelEntity, setup_panel_platform


class ValueSensor(PanelEntity, SensorEntity):
    def __init__(self, panels, panel_id, key, device_class=None, unit=None, state_class=None) -> None:
        super().__init__(panels, panel_id, key)
        self._key = key
        self._attr_device_class = device_class
        self._attr_native_unit_of_measurement = unit
        self._attr_state_class = state_class

    @property
    def native_value(self):
        return self.panel_state.get(self._key)


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback) -> None:
    setup_panel_platform(hass, entry, async_add_entities, lambda panels, pid: [
        ValueSensor(panels, pid, "battery", SensorDeviceClass.BATTERY, PERCENTAGE, SensorStateClass.MEASUREMENT),
        ValueSensor(panels, pid, "brightness", None, PERCENTAGE, SensorStateClass.MEASUREMENT),
        ValueSensor(panels, pid, "page"),
    ])
