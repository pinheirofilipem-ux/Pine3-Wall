"""Generate the theme code of the web panel and of the Android app from schema/tokens.json.

    python3 schema/tools/gen_tokens.py          # write the generated files
    python3 schema/tools/gen_tokens.py --check  # exit 1 if they are out of date (used by the tests)
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TOKENS = ROOT / "schema" / "tokens.json"
JS_OUT = ROOT / "custom_components" / "pine3_wall" / "frontend" / "tokens.js"
KT_OUT = ROOT / "android" / "app" / "src" / "main" / "java" / "pt" / "pine3" / "wall" / "ThemeTokens.kt"
HEADER = "Generated from schema/tokens.json by schema/tools/gen_tokens.py. Do not edit by hand."


def _pct(v: float) -> str:
    return f"{round(v * 100)}%"


def _css_alpha(a: float) -> str:
    s = f"{a:.2f}"
    return s[1:] if s.startswith("0.") else s


def _css_color(g: dict) -> str:
    if "alpha" not in g:
        return g["color"]
    c = g["color"].lstrip("#")
    r, gr, b = (int(c[i:i + 2], 16) for i in (0, 2, 4))
    return f"rgba({r},{gr},{b},{_css_alpha(g['alpha'])})"


def theme_css(t: dict) -> str:
    parts = [
        f"radial-gradient({_pct(g['rx'])} {_pct(g['ry'])} at {_pct(g['cx'])} {_pct(g['cy'])}, {_css_color(g)} 0%, transparent {_pct(g['stop'])})"
        for g in t["glows"]
    ]
    stops = t["base"]["stops"]
    cols = []
    for i, (col, pos) in enumerate(stops):
        edge = (i == 0 and pos == 0) or (i == len(stops) - 1 and pos == 1)
        cols.append(col if edge else f"{col} {_pct(pos)}")
    parts.append(f"linear-gradient({t['base']['angle']}deg, {', '.join(cols)})")
    return ", ".join(parts)


def gen_js(tokens: dict) -> str:
    themes = tokens["themes"]
    out = [f"// {HEADER}", ""]
    out.append("export const THEMES = {")
    out += [f'  {t["key"]}: "{theme_css(t)}",' for t in themes]
    out.append("};")
    for name, group in (("LIGHT_THEMES", "light"), ("AMBIENT_THEMES", "ambient"), ("VIVID_THEMES", "vivid")):
        keys = ", ".join(f'"{t["key"]}"' for t in themes if t["group"] == group)
        out.append(f"export const {name} = new Set([{keys}]);")
    names = ", ".join(f'{t["key"]}: "{t["name"]}"' for t in themes)
    out.append(f"export const THEME_NAMES = {{ {names} }};")
    out.append("export const CARD_COLORS = [" + ", ".join(f'"{c}"' for c in tokens["card_colors"]) + "];")
    out.append("export const ACCENTS = [" + ", ".join(f'"{c}"' for c in tokens["accents"]) + "];")
    return "\n".join(out) + "\n"


def _kt_float(v: float) -> str:
    return f"{float(v)!r}f"


def _kt_color(g: dict) -> str:
    c = g["color"].lstrip("#").upper()
    a = round(g.get("alpha", 1) * 255)
    return f"0x{a:02X}{c}.toInt()"


def gen_kt(tokens: dict) -> str:
    out = [f"// {HEADER}", "package pt.pine3.wall", "", "internal val THEME_TOKENS: List<Theme> = listOf("]
    for t in tokens["themes"]:
        glows = ", ".join(
            f"Glow({_kt_float(g['rx'])}, {_kt_float(g['ry'])}, {_kt_float(g['cx'])}, {_kt_float(g['cy'])}, {_kt_color(g)}, {_kt_float(g['stop'])})"
            for g in t["glows"]
        )
        cols = ", ".join(_kt_color({"color": c}) for c, _ in t["base"]["stops"])
        pos = ", ".join(_kt_float(p) for _, p in t["base"]["stops"])
        light = "true" if t["group"] == "light" else "false"
        out.append(
            f'    Theme("{t["key"]}", "{t["name"]}", {light}, listOf({glows}), '
            f"LinearBg({_kt_float(t['base']['angle'])}, intArrayOf({cols}), floatArrayOf({pos})), \"{t['group']}\"),"
        )
    out.append(")")
    out.append("")
    out.append("internal val CARD_COLORS: List<String> = listOf(" + ", ".join(f'"{c}"' for c in tokens["card_colors"]) + ")")
    out.append("internal val ACCENTS: List<String> = listOf(" + ", ".join(f'"{c}"' for c in tokens["accents"]) + ")")
    return "\n".join(out) + "\n"


def outputs() -> dict[Path, str]:
    tokens = json.loads(TOKENS.read_text(encoding="utf-8"))
    return {JS_OUT: gen_js(tokens), KT_OUT: gen_kt(tokens)}


def main(argv: list[str]) -> int:
    stale = []
    for path, text in outputs().items():
        if "--check" in argv:
            if not path.exists() or path.read_text(encoding="utf-8") != text:
                stale.append(path.relative_to(ROOT))
        else:
            path.write_text(text, encoding="utf-8")
    if stale:
        print("Out of date (run python3 schema/tools/gen_tokens.py):", *stale, sep="\n  ")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
