"""Module chores: the house chores, who does what on each weekday ("Pôr a mesa", Monday, Pedro).

Only the list of what is defined: there is no "done". The settings keep the tasks and a weekly grid
task x weekday -> people; the home model carries both, and each person gets a sensor with today's
tasks (the number as the state, the list in the attributes) for automations and notifications.
"""

from __future__ import annotations

from datetime import date, datetime
from typing import Any

import voluptuous as vol

DEFAULT_CHORES: dict[str, Any] = {"tasks": [], "schedule": {}}
MOMENTS = ("morning", "afternoon", "evening", "day")
# the moment of the day a time falls in (the tablet highlights the current one)
MOMENT_HOURS = {"morning": (5, 12), "afternoon": (12, 19), "evening": (19, 24)}

_TASK = vol.Schema(
    {
        vol.Required("id"): vol.Match(r"^[a-z0-9_]{1,40}$"),
        vol.Required("name"): vol.All(str, vol.Length(min=1, max=60)),
        vol.Optional("icon"): vol.Match(r"^mdi:[a-z0-9-]{1,60}$"),
        vol.Optional("moment"): vol.In(MOMENTS),
    }
)
_PEOPLE = vol.All([vol.Match(r"^person\.[a-z0-9_]+$")], vol.Length(max=10))


def _valid(cfg: dict) -> dict:
    ids = [t["id"] for t in cfg.get("tasks", [])]
    if len(ids) != len(set(ids)):
        raise vol.Invalid("task ids must be unique")
    for tid in cfg.get("schedule", {}):
        if tid not in ids:
            raise vol.Invalid(f"schedule for an unknown task: {tid}")
    return cfg


CHORES_SCHEMA = vol.All(
    {
        vol.Optional("tasks", default=[]): vol.All([_TASK], vol.Length(max=60)),
        # task id -> 7 lists of person ids, Monday first
        vol.Optional("schedule", default={}): {str: vol.All([_PEOPLE], vol.Length(min=7, max=7))},
    },
    _valid,
)


def moment_of(now: datetime) -> str:
    for name, (start, end) in MOMENT_HOURS.items():
        if start <= now.hour < end:
            return name
    return "evening"


def for_day(cfg: dict[str, Any] | None, day: date) -> list[dict[str, Any]]:
    """The tasks of [day] with their people, in the order of the task list (tasks nobody does that day are left out)."""
    cfg = cfg or {}
    wd = day.weekday()
    out = []
    for task in cfg.get("tasks", []):
        days = cfg.get("schedule", {}).get(task["id"])
        people = days[wd] if days and len(days) == 7 else []
        if people:
            out.append({**task, "people": list(people)})
    return out


def by_person(cfg: dict[str, Any] | None, day: date) -> dict[str, list[dict[str, Any]]]:
    """person id -> that person's tasks on [day]."""
    out: dict[str, list[dict[str, Any]]] = {}
    for task in for_day(cfg, day):
        for pid in task["people"]:
            out.setdefault(pid, []).append({k: v for k, v in task.items() if k != "people"})
    return out


def model(cfg: dict[str, Any] | None) -> dict[str, Any] | None:
    """What the home model carries ("chores"), or None while there are no tasks."""
    cfg = cfg or {}
    if not cfg.get("tasks"):
        return None
    ids = {t["id"] for t in cfg["tasks"]}
    return {
        "tasks": cfg["tasks"],
        "schedule": {k: v for k, v in cfg.get("schedule", {}).items() if k in ids},
        "moments": {k: list(v) for k, v in MOMENT_HOURS.items()},
    }
