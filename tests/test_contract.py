"""The contract between the integration, the web panel and the Android app (schema/).

The Android unit tests parse the same schema/examples/home.json, so a change here that the app can't read
fails on both sides. Regenerate the example after a model change with:
    PINE3_UPDATE_EXAMPLES=1 pytest tests/test_contract.py
"""

import json
import os
import subprocess
import sys
from pathlib import Path

import jsonschema
from homeassistant.core import HomeAssistant

from custom_components.pine3_wall.const import API_VERSION, MIN_APP_VERSION, VERSION
from custom_components.pine3_wall.websocket import DEFAULT_SETTINGS, async_setup_api

from .test_discovery import _setup_home

ROOT = Path(__file__).parents[1]
SCHEMA = json.loads((ROOT / "schema/home.schema.json").read_text())
EXAMPLE = ROOT / "schema/examples/home.json"


def _validate(model: dict) -> None:
    jsonschema.Draft202012Validator.check_schema(SCHEMA)
    jsonschema.validate(model, SCHEMA, cls=jsonschema.Draft202012Validator)


def _normalise(model: dict) -> dict:
    """Stable example: registry ids are random, so replace them by readable ones."""
    ids = {}
    for room in model["rooms"]:
        for dev in room["devices"]:
            if dev.get("device_id"):
                dev["device_id"] = ids.setdefault(dev["device_id"], f"device_{len(ids) + 1}")
    model["version"] = "0.0.0"
    return model


async def test_live_model_matches_schema(hass: HomeAssistant, hass_ws_client) -> None:
    await _setup_home(hass)
    await async_setup_api(hass)
    ws = await hass_ws_client(hass)
    await ws.send_json({"id": 1, "type": "pine3_wall/home"})
    msg = await ws.receive_json()
    model = msg["result"]
    assert model["api_version"] == API_VERSION
    assert model["min_app_version"] == MIN_APP_VERSION
    assert model["version"] == VERSION
    _validate(model)
    if os.environ.get("PINE3_UPDATE_EXAMPLES"):
        EXAMPLE.write_text(json.dumps(_normalise(model), ensure_ascii=False, indent=2) + "\n")


def test_example_matches_schema() -> None:
    example = json.loads(EXAMPLE.read_text())
    _validate(example)
    assert example["api_version"] == API_VERSION, "regenerate schema/examples/home.json (see the module docstring)"


def test_schema_lists_every_setting() -> None:
    assert set(SCHEMA["$defs"]["settings"]["properties"]) == set(DEFAULT_SETTINGS)


def test_min_app_version_not_newer_than_release() -> None:
    as_tuple = lambda v: tuple(int(x) for x in v.split("."))  # noqa: E731
    assert as_tuple(MIN_APP_VERSION) <= as_tuple(VERSION)


def test_tokens_generated_files_up_to_date() -> None:
    res = subprocess.run([sys.executable, str(ROOT / "schema/tools/gen_tokens.py"), "--check"], capture_output=True, text=True)
    assert res.returncode == 0, res.stdout


def test_tokens_consistent() -> None:
    tokens = json.loads((ROOT / "schema/tokens.json").read_text())
    keys = [t["key"] for t in tokens["themes"]]
    assert len(keys) == len(set(keys))
    assert {t["group"] for t in tokens["themes"]} <= {"dark", "ambient", "vivid", "light"}
    for key in ("theme", "theme_day", "theme_night"):
        assert DEFAULT_SETTINGS[key] in keys
    assert DEFAULT_SETTINGS["card_color"] in tokens["card_colors"]
    assert DEFAULT_SETTINGS["accent_color"] in tokens["accents"]


def test_schema_rejects_a_broken_model() -> None:
    import pytest

    example = json.loads(EXAMPLE.read_text())
    for broken in (
        {k: v for k, v in example.items() if k != "api_version"},
        {**example, "rooms": [{**example["rooms"][0], "devices": [{"id": "not an entity"}]}]},
        {**example, "settings": {**example["settings"], "layout": "tiles"}},
    ):
        with pytest.raises(jsonschema.ValidationError):
            _validate(broken)
