#!/usr/bin/env python3
"""Extract the built-in GLSL strings and validate them with glslangValidator."""
from __future__ import annotations

import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "fx-gles/src/main/kotlin/org/infiniforge/fx/gles/FxShaders.kt"
EXPECTED = {"VERTEX", "COPY", "CRT", "LCD", "ROTOZOOM", "KALEIDOSCOPE", "WATER", "HEAT_STEP", "HEAT_RIPPLE", "FIRE", "BLEND"}


def main() -> int:
    validator = shutil.which("glslangValidator")
    if validator is None:
        print("glslangValidator is required on PATH", file=sys.stderr)
        return 2
    source = SOURCE.read_text(encoding="utf-8")
    shaders = dict(re.findall(r'const val (\w+) = """\n(.*?)\n    """', source, re.S))
    missing = EXPECTED - shaders.keys()
    if missing:
        print(f"Missing expected shader string(s): {', '.join(sorted(missing))}", file=sys.stderr)
        return 2
    helpers = shaders["COLOR_HELPERS"] if "COLOR_HELPERS" in shaders else ""
    # COLOR_HELPERS is a Kotlin constant too, but not a standalone shader stage.
    if not helpers:
        # Helpers are shared source text, not one of the validated stages.
        print("Missing COLOR_HELPERS shader source", file=sys.stderr)
        return 2
    build_dir = ROOT / "build"
    build_dir.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="shader-validation-", dir=build_dir) as temporary:
        temp = Path(temporary)
        checked = 0
        for name in sorted(EXPECTED):
            stage = "vert" if name == "VERTEX" else "frag"
            shader = shaders[name]
            if stage == "frag":
                shader = shader.replace(
                    "precision highp float;",
                    "precision highp float;\n" + helpers,
                    1,
                )
            path = temp / f"{name}.{stage}"
            path.write_text(shader, encoding="utf-8")
            result = subprocess.run(
                [validator, "-S", stage, str(path)],
                check=False,
                capture_output=True,
                text=True,
            )
            if result.returncode:
                print(f"FAIL {name}:\n{result.stdout}{result.stderr}", file=sys.stderr)
                return result.returncode
            print(f"PASS {name}")
            checked += 1
    print(f"Validated {checked} shader stages; this does not replace a GLES driver compile/link test.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
