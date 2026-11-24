"""Services to drive the wall tablets from automations: pine3_wall.wake, show_page, show_camera, notify, screensaver.

Target one or more tablet devices (or none: every connected tablet). A tablet that is offline is skipped; the
call fails only when no target tablet is connected.
"""

from __future__ import annotations

from typing import Any

import voluptuous as vol

from homeassistant.core import HomeAssistant, ServiceCall
from homeassistant.exceptions import ServiceValidationError
from homeassistant.helpers import config_validation as cv

from .const import DOMAIN
from .panels import Panels

TARGET = {vol.Optional("device_id"): vol.All(cv.ensure_list, [str])}

SCHEMAS: dict[str, vol.Schema] = {
    "wake": vol.Schema({**TARGET, vol.Optional("seconds", default=60): vol.All(int, vol.Range(min=5, max=3600))}),
    "show_page": vol.Schema({**TARGET, vol.Required("page"): str}),
    "show_camera": vol.Schema({**TARGET, vol.Required("entity_id"): cv.entity_domain("camera"), vol.Optional("seconds", default=120): vol.All(int, vol.Range(min=10, max=3600))}),
    "notify": vol.Schema({**TARGET, vol.Required("message"): vol.All(str, vol.Length(max=500)), vol.Optional("title"): vol.All(str, vol.Length(max=80)),
                          vol.Optional("seconds", default=30): vol.All(int, vol.Range(min=3, max=3600)), vol.Optional("wake", default=True): bool}),
    "screensaver": vol.Schema({**TARGET, vol.Required("on"): bool}),
}


def _targets(panels: Panels, call: ServiceCall) -> list[str]:
    devices = call.data.get("device_id") or []
    if not devices:
        return list(panels.data)
    ids = [panels.panel_of_device(d) for d in devices]
    return [i for i in ids if i]


async def async_setup_services(hass: HomeAssistant) -> None:
    async def handle(call: ServiceCall) -> None:
        panels: Panels = hass.data[DOMAIN]["panels"]
        data: dict[str, Any] = {k: v for k, v in call.data.items() if k != "device_id"}
        sent = [pid for pid in _targets(panels, call) if panels.send(pid, call.service, **data)]
        if not sent:
            raise ServiceValidationError("No Pine3 Wall tablet is connected for this call")

    for name, schema in SCHEMAS.items():
        if not hass.services.has_service(DOMAIN, name):
            hass.services.async_register(DOMAIN, name, handle, schema=schema)


def async_unload_services(hass: HomeAssistant) -> None:
    for name in SCHEMAS:
        hass.services.async_remove(DOMAIN, name)
