"""The integration and the Android app share one version: the VERSION file at the repository root."""

import json
import re
from pathlib import Path

from custom_components.pine3_wall.const import VERSION

ROOT = Path(__file__).parents[1]


def test_versions_match():
    version = (ROOT / "VERSION").read_text().strip()
    assert re.fullmatch(r"\d+\.\d{1,2}\.\d{1,2}", version), "VERSION must be X.Y.Z (minor and patch below 100: the app's versionCode)"
    manifest = json.loads((ROOT / "custom_components/pine3_wall/manifest.json").read_text())
    assert manifest["version"] == version
    assert VERSION == version
