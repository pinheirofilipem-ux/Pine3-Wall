"""Tablet sensors (battery, screen brightness, the page on screen) and the chores of each person."""

from __future__ import annotations

from homeassistant.components.sensor import SensorDeviceClass, SensorEntity, SensorStateClass
from homeassistant.config_entries import ConfigEntry
from homeassistant.const import PERCENTAGE
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.device_registry import DeviceInfo
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.event import async_track_time_change
from homeassistant.util import dt as dt_util

from .chores import by_person
from .const import DOMAIN, EVENT_UPDATED
from .entity import PanelEntity, setup_panel_platform
from .people import build_people


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
    setup_chores(hass, entry, async_add_entities)


class ChoresSensor(SensorEntity):
    """Module chores: a person's tasks for today (state: how many; attributes: the list)."""

    _attr_has_entity_name = True
    _attr_should_poll = False
    _attr_icon = "mdi:broom"
    _attr_native_unit_of_measurement = None

    def __init__(self, hass: HomeAssistant, person_id: str, alias: str) -> None:
        self.hass = hass
        self.person_id = person_id
        self._attr_unique_id = f"chores_{person_id}"
        self._attr_name = alias
        self._attr_device_info = DeviceInfo(identifiers={(DOMAIN, "chores")}, name="Tarefas da casa", manufacturer="Pine3", model="Tarefas")

    def _today(self) -> list[dict]:
        cfg = self.hass.data.get(DOMAIN, {}).get("settings", {}).get("chores")
        return by_person(cfg, dt_util.now().date()).get(self.person_id, [])

    @property
    def native_value(self) -> int:
        return len(self._today())

    @property
    def extra_state_attributes(self) -> dict:
        tasks = self._today()
        return {
            "tasks": [t["name"] for t in tasks],
            "details": [{"name": t["name"], "moment": t.get("moment", "day"), "icon": t.get("icon")} for t in tasks],
            "person": self.person_id,
        }


def setup_chores(hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback) -> None:
    """One sensor per person once there are tasks; refreshed when the settings change and after midnight."""
    sensors: dict[str, ChoresSensor] = {}

    @callback
    def _sync(_event=None) -> None:
        data = hass.data.get(DOMAIN)
        if not data or not (data["settings"].get("chores") or {}).get("tasks"):
            return
        new = [ChoresSensor(hass, p["id"], p["alias"]) for p in build_people(hass, data["settings"]) if p["id"] not in sensors]
        for s in new:
            sensors[s.person_id] = s
        if new:
            async_add_entities(new)
        for s in sensors.values():
            if s.hass is not None and s.entity_id:
                s.async_write_ha_state()

    _sync()
    entry.async_on_unload(hass.bus.async_listen(EVENT_UPDATED, _sync))
    entry.async_on_unload(async_track_time_change(hass, _sync, hour=0, minute=0, second=5))
