#!/usr/bin/env python3
"""Check classic renders against the Enterprise mocks: edge agreement and colour presence (plan 4c).

Usage: check_pairs.py OUT_DIR [NAME_FILTER]

For every pairs.json entry with "min_agreement", runs compare_mock.py into OUT_DIR and passes the pair when its edge
agreement is at least that floor and every colour in "colours" fills a 2x2 block within Delta E 6 (CIE76) in both the
mock crop and the render. Prints one PASS or FAIL line per pair and exits 1 when any pair fails. Opt-in: never part of
./gradlew check. Render first: ./gradlew :jasper-ui:gallery -Pjasper.galleryKit=enterprise -Pjasper.galleryTheme=light
-Pjasper.galleryScale=2
"""
import json
import pathlib
import re
import subprocess
import sys

import numpy as np
from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent.parent
TOLERANCE = 6.0


def lab(rgb):
    """sRGB in 0..1, shape (..., 3), as CIE L*a*b* under D65."""
    linear = np.where(rgb <= 0.04045, rgb / 12.92, ((rgb + 0.055) / 1.055) ** 2.4)
    xyz = linear @ np.array([[0.4124, 0.3576, 0.1805], [0.2126, 0.7152, 0.0722], [0.0193, 0.1192, 0.9505]]).T
    xyz = xyz / np.array([0.95047, 1.0, 1.08883])
    f = np.where(xyz > 0.008856, np.cbrt(xyz), 7.787 * xyz + 16 / 116)
    return np.stack([116 * f[..., 1] - 16, 500 * (f[..., 0] - f[..., 1]), 200 * (f[..., 1] - f[..., 2])], axis=-1)


def present(image, colour):
    """Whether a 2x2 block of image is within TOLERANCE of colour (#RRGGBB)."""
    pixels = lab(np.asarray(image.convert("RGB"), dtype=np.float64) / 255.0)
    target = lab(np.array([int(colour[i:i + 2], 16) for i in (1, 3, 5)], dtype=np.float64) / 255.0)
    close = np.sqrt(((pixels - target) ** 2).sum(axis=-1)) <= TOLERANCE
    block = close[:-1, :-1] & close[1:, :-1] & close[:-1, 1:] & close[1:, 1:]
    return bool(block.any())


def crop(path, box):
    image = Image.open(path).convert("RGB")
    if box:
        x, y, w, h = box
        image = image.crop((x, y, x + w, y + h))
    return image


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    out = pathlib.Path(sys.argv[1])
    wanted = sys.argv[2] if len(sys.argv) > 2 else ""
    out.mkdir(parents=True, exist_ok=True)
    failed = False
    for pair in json.loads((HERE / "pairs.json").read_text(encoding="utf-8")):
        if "min_agreement" not in pair or (wanted and wanted not in pair["name"]):
            continue
        mock, render = ROOT / pair["mock"], ROOT / pair["render"]
        if not render.exists():
            print(f"{pair['name']}: FAIL missing render {pair['render']}")
            failed = True
            continue
        command = [sys.executable, "-I", str(HERE / "compare_mock.py"), str(mock), str(render), str(out / pair["name"]),
                   "--crop", ",".join(map(str, pair["crop"]))]
        if pair.get("render_crop"):
            command += ["--render-crop", ",".join(map(str, pair["render_crop"]))]
        result = subprocess.run(command, capture_output=True, text=True)
        found = re.search(r"edge_agreement=([0-9.]+)", result.stdout)
        agreement = float(found.group(1)) if found else 0.0
        mock_image, render_image = crop(mock, pair["crop"]), crop(render, pair.get("render_crop"))
        missing = [c for c in pair.get("colours", []) if not (present(mock_image, c) and present(render_image, c))]
        ok = found is not None and agreement >= pair["min_agreement"] and not missing
        failed |= not ok
        detail = f"agreement={agreement:.3f} (floor {pair['min_agreement']:.2f})"
        if missing:
            detail += " missing " + " ".join(missing)
        print(f"{pair['name']}: {'PASS' if ok else 'FAIL'} {detail}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
