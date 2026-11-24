"""Module calendar: the family calendar (one shared "Família" calendar, e.g. iCloud through the CalDAV integration).

It reads the events of the chosen calendar.* entities with calendar.get_events, puts them in local time, works out
whose they are from the title ("[Pedro] Natação", "Pedro e Ana: Jantar", "Natação [Pedro]") and sends them in the
home model, so the app and the web draw the same agenda. A calendar marked private only shows "Busy".
"""

from __future__ import annotations

import hashlib
import logging
import re
import unicodedata
from datetime import date, datetime, timedelta
from typing import Any

from homeassistant.core import Event, HomeAssistant, callback
from homeassistant.helpers.event import async_track_state_change_event, async_track_time_interval
from homeassistant.util import dt as dt_util

from .const import EVENT_UPDATED

_LOGGER = logging.getLogger(__name__)

REFRESH = timedelta(minutes=5)
MAX_EVENTS = 400
DEFAULT_CALENDAR: dict[str, Any] = {"entities": [], "private": [], "days": 14}
# words that mean "the whole family" in a title tag
FAMILY_WORDS = {"familia", "family", "todos", "all", "casa"}
_SPLIT = re.compile(r"\s*(?:,|/|&|\+|\be\b|\band\b)\s*", re.IGNORECASE)
_LEAD_TAG = re.compile(r"^\s*\[([^\]]{1,80})\]\s*(.*)$", re.DOTALL)
_TAIL_TAG = re.compile(r"^(.*?)\s*\[([^\]]{1,80})\]\s*$", re.DOTALL)
_COLON = re.compile(r"^\s*([^:\[\]]{1,60}?)\s*:\s*(.+)$", re.DOTALL)


def _fold(s: str) -> str:
    """Lower case, without accents or extra spaces (so "Joao" finds "João")."""
    s = unicodedata.normalize("NFKD", s or "")
    return " ".join("".join(c for c in s if not unicodedata.combining(c)).lower().split())


def _names(people: list[dict[str, Any]]) -> dict[str, str]:
    """Folded name, first name and alias -> person id (the first person wins a shared first name)."""
    out: dict[str, str] = {}
    for p in people:
        for n in (p.get("alias"), p.get("name"), (p.get("name") or "").split(" ")[0]):
            if n and _fold(n) not in out:
                out[_fold(n)] = p["id"]
    return out


def _match(tag: str, names: dict[str, str]) -> list[str] | None:
    """The people in a tag ("Pedro e Ana"), or None if a part isn't a person; [] means the family."""
    found: list[str] = []
    parts = [p for p in _SPLIT.split(tag.strip()) if p]
    if not parts:
        return None
    for part in parts:
        f = _fold(part)
        if f in FAMILY_WORDS:
            continue
        pid = names.get(f)
        if pid is None:
            return None
        if pid not in found:
            found.append(pid)
    return found


def assign(title: str, people: list[dict[str, Any]]) -> tuple[list[str], str]:
    """Whose an event is, from its title, and the title without the tag. No tag (or "Família") = the family ([])."""
    names = _names(people)
    title = (title or "").strip()
    for rx, tag_i, rest_i in ((_LEAD_TAG, 1, 2), (_TAIL_TAG, 2, 1), (_COLON, 1, 2)):
        m = rx.match(title)
        if not m:
            continue
        who = _match(m.group(tag_i), names)
        if who is not None:
            return who, m.group(rest_i).strip() or title
    return [], title


def _local(value: Any) -> tuple[str, bool]:
    """An event time as local "YYYY-MM-DDTHH:MM" (or "YYYY-MM-DD" for all-day events) and whether it is a date."""
    if isinstance(value, datetime):
        return dt_util.as_local(value).strftime("%Y-%m-%dT%H:%M"), False
    if isinstance(value, date):
        return value.isoformat(), True
    s = str(value or "")
    if len(s) == 10:
        return s, True
    parsed = dt_util.parse_datetime(s)
    if parsed is None:
        return s[:16], False
    if parsed.tzinfo is None:
        return parsed.strftime("%Y-%m-%dT%H:%M"), False
    return dt_util.as_local(parsed).strftime("%Y-%m-%dT%H:%M"), False


def normalize(raw: dict[str, Any], people: list[dict[str, Any]], private: list[str]) -> list[dict[str, Any]]:
    """calendar.get_events' response ({entity: {"events": [...]}}) as the model's events, sorted by start."""
    out: list[dict[str, Any]] = []
    for entity, body in (raw or {}).items():
        for ev in (body or {}).get("events", []) or []:
            start, all_day = _local(ev.get("start"))
            end, _ = _local(ev.get("end") or ev.get("start"))
            who, title = assign(ev.get("summary") or "", people)
            hidden = entity in private
            key = hashlib.sha1(f"{entity}|{start}|{ev.get('summary')}".encode()).hexdigest()[:12]
            item: dict[str, Any] = {
                "id": key,
                "title": "" if hidden else title,
                "start": start,
                "end": end if end >= start else start,
                "all_day": all_day,
                "people": who,
                "calendar": entity,
            }
            if hidden:
                item["private"] = True
            elif ev.get("location"):
                item["location"] = str(ev["location"])[:200]
            out.append(item)
    out.sort(key=lambda e: (e["start"][:10], not e["all_day"], e["start"], e["title"]))
    return out[:MAX_EVENTS]


def window(today: date, days: int) -> tuple[date, date]:
    """The days to read: this whole month and this whole week (for the Month and Week views) and the next [days]."""
    first = today.replace(day=1)
    monday = today - timedelta(days=today.weekday())
    nxt = (first + timedelta(days=32)).replace(day=1)
    end = max(today + timedelta(days=days), nxt, monday + timedelta(days=7))
    return min(first, monday), end


class FamilyCalendar:
    """Reads and keeps the family calendar; pushes a new home model when the events change."""

    def __init__(self, hass: HomeAssistant, settings: dict[str, Any], people) -> None:
        self.hass = hass
        self._settings = settings
        self._people = people  # () -> list of people (people.build_people)
        self.events: list[dict[str, Any]] = []
        self.range: tuple[str, str] | None = None
        self.error: str | None = None
        self._unsubs: list = []
        self._watch: list = []

    @property
    def config(self) -> dict[str, Any]:
        return {**DEFAULT_CALENDAR, **(self._settings.get("calendar") or {})}

    @property
    def enabled(self) -> bool:
        return bool(self.config["entities"])

    def model(self) -> dict[str, Any] | None:
        """What the home model carries ("calendar"), or None when no calendar is chosen."""
        if not self.enabled:
            return None
        cfg = self.config
        out: dict[str, Any] = {"entities": cfg["entities"], "days": cfg["days"], "events": self.events}
        if cfg.get("color"):
            out["color"] = cfg["color"]
        if self.range:
            out["from"], out["to"] = self.range
        if self.error:
            out["error"] = self.error
        return out

    async def async_start(self) -> None:
        self._unsubs.append(async_track_time_interval(self.hass, self._tick, REFRESH))
        self._rewatch()
        await self.async_refresh()

    @callback
    def async_stop(self) -> None:
        for unsub in self._unsubs + self._watch:
            unsub()
        self._unsubs, self._watch = [], []

    @callback
    def _rewatch(self) -> None:
        # a calendar entity changes state when an event starts or ends, and when CalDAV syncs a change
        for unsub in self._watch:
            unsub()
        self._watch = []
        if self.enabled:
            self._watch.append(async_track_state_change_event(self.hass, self.config["entities"], self._changed))

    async def _tick(self, _now=None) -> None:
        await self.async_refresh()

    async def _changed(self, _event: Event) -> None:
        await self.async_refresh()

    async def async_settings_changed(self) -> None:
        self._rewatch()
        await self.async_refresh()

    async def async_refresh(self) -> bool:
        """Reads the events again. True when they changed (a new home model was pushed)."""
        cfg = self.config
        if not cfg["entities"]:
            changed = bool(self.events) or self.error is not None
            self.events, self.range, self.error = [], None, None
            return changed
        start, end = window(dt_util.now().date(), int(cfg["days"]))
        tz = dt_util.get_default_time_zone()
        error = None
        try:
            raw = await self.hass.services.async_call(
                "calendar",
                "get_events",
                {
                    "start_date_time": datetime.combine(start, datetime.min.time(), tz),
                    "end_date_time": datetime.combine(end, datetime.min.time(), tz),
                },
                target={"entity_id": cfg["entities"]},
                blocking=True,
                return_response=True,
            )
            events = normalize(raw or {}, self._people(), cfg.get("private") or [])
        except Exception as err:  # noqa: BLE001 - a missing entity or a CalDAV error must not break the panel
            _LOGGER.warning("Pine3 Wall: could not read the family calendar: %s", err)
            events, error = self.events, str(err)[:200]
        rng = (start.isoformat(), end.isoformat())
        changed = events != self.events or rng != self.range or error != self.error
        self.events, self.range, self.error = events, rng, error
        if changed:
            self.hass.bus.async_fire(EVENT_UPDATED)
        return changed
