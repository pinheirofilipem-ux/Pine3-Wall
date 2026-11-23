"""Constants for Pine3 Wall."""

DOMAIN = "pine3_wall"
PANEL_URL_PATH = "pine3-wall"
PANEL_TITLE = "Pine3 Wall"
PANEL_ICON = "mdi:home-variant"
PANEL_ELEMENT = "pine3-wall-panel"
STATIC_URL = "/pine3_wall_static"
EVENT_UPDATED = "pine3_wall_updated"
VERSION = "0.5.1"  # keep in sync with ../../VERSION and manifest.json (checked by tests)
# Contract with the Android app (schema/home.schema.json). Raise API_VERSION when a field of the home model is
# removed or changes meaning (new optional fields don't need it), and MIN_APP_VERSION to the first app that copes.
API_VERSION = 1
MIN_APP_VERSION = "0.1.0"
