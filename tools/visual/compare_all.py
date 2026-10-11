#!/usr/bin/env python3
"""Run compare_mock.py for every reference/render pair listed in pairs.json.

Usage: compare_all.py OUT_DIR [NAME_FILTER]

pairs.json entries: {"name", "mock", "crop", "render", "render_crop"?}. Mock paths are relative to the
repository's preserved references; render paths are relative to the repository root (build outputs).
"""
import json
import pathlib
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent.parent


def main():
    out = pathlib.Path(sys.argv[1])
    wanted = sys.argv[2] if len(sys.argv) > 2 else ""
    out.mkdir(parents=True, exist_ok=True)
    pairs = json.loads((HERE / "pairs.json").read_text())
    for pair in pairs:
        if wanted and wanted not in pair["name"]:
            continue
        mock = ROOT / pair["mock"]
        render = ROOT / pair["render"]
        if not render.exists():
            print(f"{pair['name']}: MISSING render {pair['render']}")
            continue
        command = [sys.executable, "-I", str(HERE / "compare_mock.py"), str(mock), str(render), str(out / pair["name"]),
                   "--crop", ",".join(map(str, pair["crop"]))]
        if pair.get("render_crop"):
            command += ["--render-crop", ",".join(map(str, pair["render_crop"]))]
        result = subprocess.run(command, capture_output=True, text=True)
        summary = (result.stdout.strip().splitlines() or [result.stderr.strip()])[-1]
        print(f"{pair['name']}: {summary}")


if __name__ == "__main__":
    main()
