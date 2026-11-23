"""The family: people with colour, alias and picture (custom_components/pine3_wall/people.py)."""

import pytest
from homeassistant.core import HomeAssistant

from custom_components.pine3_wall.people import build_people, person_colors
from custom_components.pine3_wall.websocket import DEFAULT_SETTINGS, async_setup_api

from .test_discovery import _setup_home


async def test_defaults(hass: HomeAssistant) -> None:
    await _setup_home(hass)
    people = build_people(hass, DEFAULT_SETTINGS)
    assert [p["id"] for p in people] == ["person.ana", "person.pedro"]  # sorted by name
    ana, pedro = people
    assert ana["alias"] == "Ana" and pedro["alias"] == "Pedro"  # first name by default
    assert [ana["color"], pedro["color"]] == person_colors()[:2]  # palette by position
    assert pedro["picture"] == "/api/image/pedro.jpg" and ana["picture"] is None
    assert not ana["hidden"]


async def test_chosen_settings(hass: HomeAssistant) -> None:
    await _setup_home(hass)
    settings = {**DEFAULT_SETTINGS, "people": {"person.pedro": {"color": "#112233", "alias": "Pai", "hidden": True}}}
    pedro = next(p for p in build_people(hass, settings) if p["id"] == "person.pedro")
    assert pedro == {**pedro, "color": "#112233", "alias": "Pai", "hidden": True}


async def test_people_in_model_and_settings(hass: HomeAssistant, hass_ws_client) -> None:
    await _setup_home(hass)
    await async_setup_api(hass)
    ws = await hass_ws_client(hass)
    await ws.send_json({"id": 1, "type": "pine3_wall/settings", "people": {"person.ana": {"color": "#aabbcc", "alias": "Mãe"}}})
    assert (await ws.receive_json())["success"]
    await ws.send_json({"id": 2, "type": "pine3_wall/home"})
    model = (await ws.receive_json())["result"]
    ana = next(p for p in model["people"] if p["id"] == "person.ana")
    assert ana["alias"] == "Mãe" and ana["color"] == "#aabbcc"


@pytest.mark.parametrize("bad", [
    {"light.x": {"color": "#aabbcc"}},
    {"person.ana": {"color": "red"}},
    {"person.ana": {"alias": "x" * 41}},
])
async def test_bad_people_settings(hass: HomeAssistant, hass_ws_client, bad) -> None:
    await async_setup_api(hass)
    ws = await hass_ws_client(hass)
    await ws.send_json({"id": 1, "type": "pine3_wall/settings", "people": bad})
    assert not (await ws.receive_json())["success"]
