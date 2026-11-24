"""Module chores: tasks by weekday and person (custom_components/pine3_wall/chores.py)."""

from datetime import date, datetime

import pytest
from homeassistant.core import HomeAssistant

from custom_components.pine3_wall.chores import by_person, for_day, model, moment_of
from custom_components.pine3_wall.websocket import async_setup_api

P, A = "person.pedro", "person.ana"
CFG = {
    "tasks": [
        {"id": "mesa", "name": "Pôr a mesa", "icon": "mdi:silverware-fork-knife", "moment": "evening"},
        {"id": "lixo", "name": "Lixo", "moment": "morning"},
        {"id": "nada", "name": "Ninguém"},
    ],
    # segunda: Pedro põe a mesa; terça: os dois; o lixo é da Ana ao fim de semana
    "schedule": {"mesa": [[P], [P, A], [], [], [], [], []], "lixo": [[], [], [], [], [], [A], [A]]},
}
MONDAY, TUESDAY, SUNDAY = date(2026, 11, 23), date(2026, 11, 24), date(2026, 11, 29)


def test_for_day_and_by_person() -> None:
    assert [t["id"] for t in for_day(CFG, MONDAY)] == ["mesa"]
    assert for_day(CFG, TUESDAY)[0]["people"] == [P, A]
    assert by_person(CFG, TUESDAY) == {P: [CFG["tasks"][0]], A: [CFG["tasks"][0]]}
    assert by_person(CFG, SUNDAY) == {A: [CFG["tasks"][1]]}
    assert by_person({}, SUNDAY) == {} and for_day(None, SUNDAY) == []


def test_model_and_moments() -> None:
    assert model({"tasks": [], "schedule": {}}) is None
    m = model({**CFG, "schedule": {**CFG["schedule"], "velha": [[]] * 7}})
    assert set(m["schedule"]) == {"mesa", "lixo"}  # tarefas apagadas ficam de fora
    assert m["moments"]["evening"] == [19, 24]
    assert [moment_of(datetime(2026, 1, 1, h)) for h in (8, 13, 21, 2)] == ["morning", "afternoon", "evening", "evening"]


async def test_chores_in_model(hass: HomeAssistant, hass_ws_client) -> None:
    await async_setup_api(hass)
    ws = await hass_ws_client(hass)
    await ws.send_json({"id": 1, "type": "pine3_wall/settings", "chores": CFG})
    assert (await ws.receive_json())["success"]
    await ws.send_json({"id": 2, "type": "pine3_wall/home"})
    ch = (await ws.receive_json())["result"]["chores"]
    assert [t["id"] for t in ch["tasks"]] == ["mesa", "lixo", "nada"] and ch["schedule"]["mesa"][1] == [P, A]


@pytest.mark.parametrize("bad", [
    {"tasks": [{"id": "a", "name": "A"}, {"id": "a", "name": "B"}]},  # ids repetidos
    {"tasks": [{"id": "a", "name": "A"}], "schedule": {"b": [[]] * 7}},  # tarefa que não existe
    {"tasks": [{"id": "a", "name": "A"}], "schedule": {"a": [[]] * 6}},  # 6 dias
    {"tasks": [{"id": "a", "name": "A"}], "schedule": {"a": [["light.x"]] + [[]] * 6}},
    {"tasks": [{"id": "A b", "name": "A"}]},
    {"tasks": [{"id": "a", "name": "A", "moment": "noite"}]},
])
async def test_bad_chores(hass: HomeAssistant, hass_ws_client, bad) -> None:
    await async_setup_api(hass)
    ws = await hass_ws_client(hass)
    await ws.send_json({"id": 1, "type": "pine3_wall/settings", "chores": bad})
    assert not (await ws.receive_json())["success"]


async def test_a_sensor_per_person(hass: HomeAssistant, hass_ws_client) -> None:
    from pytest_homeassistant_custom_component.common import MockConfigEntry

    from custom_components.pine3_wall import sensor
    from custom_components.pine3_wall.const import DOMAIN

    hass.states.async_set(P, "home", {"friendly_name": "Pedro"})
    hass.states.async_set(A, "home", {"friendly_name": "Ana"})
    await async_setup_api(hass)
    entry = MockConfigEntry(domain=DOMAIN)
    entry.add_to_hass(hass)
    added = []
    await sensor.async_setup_entry(hass, entry, lambda ents, *_: added.extend(ents))
    assert not added  # sem tarefas, sem sensores
    ws = await hass_ws_client(hass)
    every_day = {"tasks": [{"id": "mesa", "name": "Pôr a mesa"}], "schedule": {"mesa": [[P]] * 7}}
    await ws.send_json({"id": 1, "type": "pine3_wall/settings", "chores": every_day})
    assert (await ws.receive_json())["success"]
    await hass.async_block_till_done()
    by = {e.person_id: e for e in added}
    assert set(by) == {P, A} and by[P].name == "Pedro"
    assert by[P].native_value == 1 and by[P].extra_state_attributes["tasks"] == ["Pôr a mesa"]
    assert by[A].native_value == 0
