"""The family: Home Assistant people (person.*) with the colour, alias and picture the modules share.

Calendar, chores and the home screen all show people the same way, so their colour and alias live
in the shared settings ("people") and the model sends them ready to use.
"""

from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path
from typing import Any

from homeassistant.core import HomeAssistant

TOKENS = Path(__file__).parent / "person_colors.json"


@lru_cache(maxsize=1)
def person_colors() -> list[str]:
    """The default colours (schema/tokens.json "person_colors", copied next to this file by gen_tokens.py)."""
    return json.loads(TOKENS.read_text(encoding="utf-8"))


def build_people(hass: HomeAssistant, settings: dict[str, Any]) -> list[dict[str, Any]]:
    """Every person, sorted by name, with its colour (chosen or by position), alias and picture."""
    chosen: dict[str, dict] = settings.get("people", {}) or {}
    states = sorted(hass.states.async_all("person"), key=lambda s: (s.attributes.get("friendly_name") or s.entity_id).lower())
    colors = person_colors()
    out = []
    for i, st in enumerate(states):
        cfg = chosen.get(st.entity_id, {})
        name = st.attributes.get("friendly_name") or st.entity_id.split(".", 1)[1].replace("_", " ").title()
        out.append({
            "id": st.entity_id,
            "name": name,
            "alias": cfg.get("alias") or name.split()[0],
            "color": cfg.get("color") or colors[i % len(colors)],
            "picture": st.attributes.get("entity_picture"),
            "hidden": bool(cfg.get("hidden", False)),
        })
    return out
