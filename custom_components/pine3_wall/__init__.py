"""Pine3 Wall: a personal, auto-organised Home Assistant dashboard."""

from pathlib import Path

from homeassistant.components import frontend, panel_custom
from homeassistant.components.http import StaticPathConfig
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant

from .services import async_setup_services, async_unload_services
from .websocket import async_setup_api, async_unload_api
from .const import (
    DOMAIN,
    PANEL_ELEMENT,
    PANEL_ICON,
    PANEL_TITLE,
    PANEL_URL_PATH,
    STATIC_URL,
    VERSION,
)

FRONTEND_DIR = Path(__file__).parent / "frontend"
# each wall tablet is a device with these entities (panels.py)
PLATFORMS = ["binary_sensor", "sensor", "button"]
STATIC_KEY = f"{DOMAIN}_static_registered"


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    await async_setup_api(hass)
    # Serve the frontend under a versioned path so that an update changes the URL of every
    # module (the panel and the files it imports), and browsers can't keep stale copies.
    base = f"{STATIC_URL}/{VERSION}"
    if not hass.data.get(STATIC_KEY):
        await hass.http.async_register_static_paths(
            [StaticPathConfig(base, str(FRONTEND_DIR), cache_headers=False)]
        )
        hass.data[STATIC_KEY] = True  # routes can't be unregistered; survive integration reloads
    await panel_custom.async_register_panel(
        hass,
        webcomponent_name=PANEL_ELEMENT,
        frontend_url_path=PANEL_URL_PATH,
        module_url=f"{base}/pine3-wall-panel.js",
        sidebar_title=PANEL_TITLE,
        sidebar_icon=PANEL_ICON,
        require_admin=False,
    )
    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    await async_setup_services(hass)
    return True


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    unloaded = await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
    frontend.async_remove_panel(hass, PANEL_URL_PATH)
    async_unload_services(hass)
    async_unload_api(hass)
    return unloaded
