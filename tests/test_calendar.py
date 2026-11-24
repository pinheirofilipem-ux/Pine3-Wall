"""Module calendar: the family calendar (custom_components/pine3_wall/family_calendar.py)."""

from datetime import date

import pytest
from homeassistant.core import HomeAssistant, ServiceCall, SupportsResponse

from custom_components.pine3_wall.const import DOMAIN
from custom_components.pine3_wall.family_calendar import assign, normalize, window
from custom_components.pine3_wall.websocket import async_setup_api

PEOPLE = [
    {"id": "person.pedro", "name": "Pedro Silva", "alias": "Pai"},
    {"id": "person.ana", "name": "Ana", "alias": "Ana"},
    {"id": "person.joao", "name": "João", "alias": "João"},
]


@pytest.mark.parametrize(("title", "people", "clean"), [
    ("[Pedro] Natação", ["person.pedro"], "Natação"),
    ("[Pai, Ana] Jantar", ["person.pedro", "person.ana"], "Jantar"),
    ("Pedro e Ana: Cinema", ["person.pedro", "person.ana"], "Cinema"),
    ("joao: Futebol", ["person.joao"], "Futebol"),  # sem acentos nem maiúsculas
    ("Natação [João]", ["person.joao"], "Natação"),
    ("[Família] Jantar na avó", [], "Jantar na avó"),
    ("Reunião: orçamento", [], "Reunião: orçamento"),  # "Reunião" não é ninguém
    ("17:30 Dentista", [], "17:30 Dentista"),
    ("[Pedro / Desconhecido] X", [], "[Pedro / Desconhecido] X"),
    ("Aniversário da Ana", [], "Aniversário da Ana"),
])
def test_assign(title, people, clean) -> None:
    assert assign(title, PEOPLE) == (people, clean)


def test_normalize_private_all_day_and_order() -> None:
    raw = {
        "calendar.familia": {"events": [
            {"start": "2026-11-24T18:30:00", "end": "2026-11-24T19:30:00", "summary": "[Ana] Ballet", "location": "Escola"},
            {"start": "2026-11-24", "end": "2026-11-26", "summary": "Férias"},
        ]},
        "calendar.trabalho": {"events": [{"start": "2026-11-24T09:00:00", "end": "2026-11-24T10:00:00", "summary": "[Pai] 1:1"}]},
    }
    ev = normalize(raw, PEOPLE, private=["calendar.trabalho"])
    assert [e["start"] for e in ev] == ["2026-11-24", "2026-11-24T09:00", "2026-11-24T18:30"]  # all-day first
    ferias, work, ballet = ev
    assert ferias["all_day"] and ferias["end"] == "2026-11-26" and ferias["people"] == []
    assert work["private"] and work["title"] == "" and work["people"] == ["person.pedro"] and "location" not in work
    assert ballet["title"] == "Ballet" and ballet["people"] == ["person.ana"] and ballet["location"] == "Escola"
    assert len({e["id"] for e in ev}) == 3


def test_window() -> None:
    # terça, 24 de novembro: desde 1 de novembro (o mês) até ao maior de: mês seguinte, fim da semana, 14 dias
    start, end = window(date(2026, 11, 24), 14)
    assert start == date(2026, 11, 1) and end == date(2026, 12, 8)
    # dia 1 numa quinta: a semana começa no mês anterior
    start, end = window(date(2026, 10, 1), 3)
    assert start == date(2026, 9, 28) and end == date(2026, 11, 1)


async def test_calendar_in_model(hass: HomeAssistant, hass_ws_client) -> None:
    hass.states.async_set("person.pedro", "home", {"friendly_name": "Pedro"})
    hass.states.async_set("calendar.familia", "off", {"friendly_name": "Família"})
    asked: list = []

    async def get_events(call: ServiceCall):
        asked.append(call.data)
        return {"calendar.familia": {"events": [{"start": "2030-01-02T17:30:00", "end": "2030-01-02T18:30:00", "summary": "Pedro: Natação"}]}}

    hass.services.async_register("calendar", "get_events", get_events, supports_response=SupportsResponse.ONLY)
    await async_setup_api(hass)
    await hass.async_block_till_done()
    ws = await hass_ws_client(hass)
    await ws.send_json({"id": 1, "type": "pine3_wall/home"})
    assert "calendar" not in (await ws.receive_json())["result"]  # nenhum calendário escolhido
    assert not asked

    await ws.send_json({"id": 2, "type": "pine3_wall/settings", "calendar": {"entities": ["calendar.familia"], "days": 7}})
    assert (await ws.receive_json())["success"]
    await hass.async_block_till_done()
    assert asked and asked[-1]["entity_id"] == ["calendar.familia"]
    await ws.send_json({"id": 3, "type": "pine3_wall/home"})
    cal = (await ws.receive_json())["result"]["calendar"]
    assert cal["days"] == 7 and cal["entities"] == ["calendar.familia"] and "from" in cal
    assert cal["events"][0] | {} == {**cal["events"][0], "title": "Natação", "people": ["person.pedro"], "start": "2030-01-02T17:30"}

    await ws.send_json({"id": 4, "type": "pine3_wall/calendar/assign", "titles": ["[Pedro] Ténis", "Jantar"]})
    assert (await ws.receive_json())["result"] == [{"people": ["person.pedro"], "title": "Ténis"}, {"people": [], "title": "Jantar"}]

    # a calendar that fails keeps the last events and says why
    hass.services.async_remove("calendar", "get_events")
    await ws.send_json({"id": 5, "type": "pine3_wall/calendar/refresh"})
    res = (await ws.receive_json())["result"]
    assert res["error"] and res["events"] == cal["events"]
    hass.data[DOMAIN]["calendar"].async_stop()


@pytest.mark.parametrize("bad", [
    {"entities": ["light.x"]},
    {"days": 0},
    {"color": "blue"},
])
async def test_bad_calendar_settings(hass: HomeAssistant, hass_ws_client, bad) -> None:
    await async_setup_api(hass)
    ws = await hass_ws_client(hass)
    await ws.send_json({"id": 1, "type": "pine3_wall/settings", "calendar": bad})
    assert not (await ws.receive_json())["success"]


async def test_times_in_local_time(hass: HomeAssistant) -> None:
    await hass.config.async_set_time_zone("Europe/Lisbon")
    raw = {"calendar.f": {"events": [{"start": "2026-07-01T16:30:00+00:00", "end": "2026-07-01T17:00:00+00:00", "summary": "x"}]}}
    assert normalize(raw, PEOPLE, [])[0]["start"] == "2026-07-01T17:30"  # hora de verão
