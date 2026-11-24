"""Wall tablets: registration, state, entities, services and the layout of each tablet."""

import copy

import pytest
from homeassistant.core import HomeAssistant
from homeassistant.exceptions import ServiceValidationError
from pytest_homeassistant_custom_component.common import MockConfigEntry

from custom_components.pine3_wall import binary_sensor, button, sensor
from custom_components.pine3_wall.const import DOMAIN
from custom_components.pine3_wall.services import async_setup_services
from custom_components.pine3_wall.websocket import async_setup_api

from .test_discovery import _setup_home

PANEL = "a1b2c3d4e5f6"


def _grid(page):
    return next(s for s in page["sections"] if s["type"] == "grid")["cards"]


async def _client(hass, hass_ws_client):
    ws = await hass_ws_client(hass)
    n = iter(range(1, 1000))
    events = []

    async def call(type_, **kw):
        await ws.send_json({"id": next(n), "type": f"pine3_wall/{type_}", **kw})
        while True:
            m = await ws.receive_json()
            if m["type"] == "event":
                events.append(m["event"])
                continue
            return m

    async def next_event():
        while not events:
            m = await ws.receive_json()
            if m["type"] == "event":
                events.append(m["event"])
        return events.pop(0)

    return ws, call, next_event


async def test_connect_state_and_commands(hass: HomeAssistant, hass_ws_client) -> None:
    await _setup_home(hass)
    await async_setup_api(hass)
    await async_setup_services(hass)
    ws, call, next_event = await _client(hass, hass_ws_client)

    got = await call("panel/connect", panel_id=PANEL, name="Cozinha", model="OPPO Pad SE", app_version="0.8.0")
    assert got["success"] and got["result"] == {"name": "Cozinha", "layout": ""}
    listing = (await call("panel/list"))["result"]
    assert listing["panels"][0]["online"] and listing["panels"][0]["model"] == "OPPO Pad SE"
    assert listing["layouts"] == ["main"]

    assert (await call("panel/state", panel_id=PANEL, motion=True, screen_on=True, battery=81, page="home"))["success"]
    panels = hass.data[DOMAIN]["panels"]
    assert panels.state[PANEL] == {"motion": True, "screen_on": True, "battery": 81, "page": "home"}

    # services reach the tablet as events on its connection (no target: every tablet)
    await hass.services.async_call(DOMAIN, "notify", {"title": "Jantar", "message": "Está na mesa!", "seconds": 20}, blocking=True)
    assert await next_event() == {"command": "notify", "title": "Jantar", "message": "Está na mesa!", "seconds": 20, "wake": True}
    await hass.services.async_call(DOMAIN, "show_page", {"page": "room/sala"}, blocking=True)
    assert (await next_event())["page"] == "room/sala"
    assert (await call("panel/command", panel_id=PANEL, command="wake", data={"seconds": 30}))["success"]
    assert await next_event() == {"command": "wake", "seconds": 30}

    await ws.close()
    await hass.async_block_till_done()
    assert not panels.online(PANEL)
    with pytest.raises(ServiceValidationError):
        await hass.services.async_call(DOMAIN, "wake", {}, blocking=True)


async def test_entities_follow_the_tablet(hass: HomeAssistant, hass_ws_client) -> None:
    await async_setup_api(hass)
    entry = MockConfigEntry(domain=DOMAIN)
    entry.add_to_hass(hass)
    added = []
    for platform in (binary_sensor, sensor, button):
        await platform.async_setup_entry(hass, entry, lambda ents, *_: added.extend(ents))
    ws, call, next_event = await _client(hass, hass_ws_client)
    await call("panel/connect", panel_id=PANEL, name="Sala")
    await hass.async_block_till_done()
    keys = sorted(e.unique_id.split("_", 1)[1] for e in added)
    assert keys == ["battery", "brightness", "motion", "online", "page", "screen_on", "wake"]
    by = {e.unique_id.split("_", 1)[1]: e for e in added}
    await call("panel/state", panel_id=PANEL, motion=True, battery=55)
    assert by["motion"].is_on is True and by["battery"].native_value == 55
    assert by["online"].is_on and by["battery"].available
    by["wake"].hass = hass
    await by["wake"].async_press()
    assert await next_event() == {"command": "wake"}
    await ws.close()
    await hass.async_block_till_done()
    assert by["online"].is_on is False and by["online"].available and not by["battery"].available


async def test_each_tablet_its_layout(hass: HomeAssistant, hass_ws_client) -> None:
    await _setup_home(hass)
    await async_setup_api(hass)
    ws, call, _ = await _client(hass, hass_ws_client)
    await call("panel/connect", panel_id=PANEL, name="Quarto")
    auto = (await call("layout/get"))["result"]["auto"]
    variant = copy.deepcopy(auto)
    variant["pages"]["home"]["template"] = "classic"
    got = await call("layout/create", name="Quartos", layout=variant)
    assert got["success"] and got["result"]["target"] == "Quartos" and got["result"]["targets"] == ["main", "Quartos"]
    assert not (await call("layout/create", name="Quartos"))["success"]
    assert (await call("layout/publish", target="Quartos"))["success"]

    # nothing changes until the tablet is given the variant
    assert (await call("home", panel_id=PANEL))["result"]["layout"]["pages"]["home"]["template"] == "top"
    assert (await call("panel/update", panel_id=PANEL, layout="Quartos"))["success"]
    mine = (await call("home", panel_id=PANEL))["result"]
    assert mine["layout"]["pages"]["home"]["template"] == "classic" and mine["layout_source"] == "published"
    assert (await call("home"))["result"]["layout"]["pages"]["home"]["template"] == "top"  # others: the main one

    # the tablet's edit mode edits its own layout
    sala = next(r["id"] for r in mine["rooms"] if r["name"] == "Sala")
    assert (await call("layout/edit", panel_id=PANEL, room_order=[sala], big_rooms=[sala]))["success"]
    assert _grid((await call("home", panel_id=PANEL))["result"]["layout"]["pages"]["home"])[0] == {"type": "room", "room": sala, "size": "big"}
    assert not (await call("layout/edit", room_order=[sala]))["success"]  # main is automatic: edit the settings

    # deleting the variant sends the tablet back to the main layout
    assert (await call("layout/delete", name="Quartos"))["success"]
    assert (await call("panel/list"))["result"]["panels"][0]["layout"] == ""
    assert not (await call("layout/delete", name="main"))["success"]
    assert not (await call("layout/get", target="Quartos"))["success"]
    assert not (await call("panel/update", panel_id=PANEL, layout="Nope"))["success"]


async def test_rename_remove_and_offline(hass: HomeAssistant, hass_ws_client) -> None:
    await async_setup_api(hass)
    ws, call, _ = await _client(hass, hass_ws_client)
    await call("panel/connect", panel_id=PANEL, name="Entrada")
    got = (await call("panel/update", panel_id=PANEL, name="Hall"))["result"]
    assert got["panels"][0]["name"] == "Hall"
    assert not (await call("panel/command", panel_id="unknown123", command="wake"))["success"]
    assert not (await call("panel/command", panel_id=PANEL, command="explode"))["success"]
    assert (await call("panel/remove", panel_id=PANEL))["result"]["panels"] == []


async def test_old_layout_storage_migrates(hass: HomeAssistant, hass_storage) -> None:
    hass_storage["pine3_wall.layouts"] = {"version": 1, "key": "pine3_wall.layouts", "data": {"draft": None, "published": {"x": 1}, "history": [], "preview": False}}
    await async_setup_api(hass)
    store = hass.data[DOMAIN]["layouts"]
    assert store.targets() == ["main"] and store.t()["published"] == {"x": 1}
