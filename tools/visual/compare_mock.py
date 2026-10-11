#!/usr/bin/env python3
"""Compare an actual Jasper render against a reference mock, geometry first.

Usage:
  compare_mock.py MOCK RENDER OUT_PREFIX [--crop x,y,w,h] [--render-crop x,y,w,h] [--search N]

Both images are compared at their own pixel scale; nothing is resized. The mock crop and the
render crop must describe the same logical area at the same device scale (the references are 2x).
Colors come from the active theme, so the geometry check uses luminance edges, not raw RGB.

Outputs:
  OUT_PREFIX-side.png    mock | render | edge overlay (mock edges red, render edges cyan, shared white)
  OUT_PREFIX-blend.png   50% blend of the aligned pair
  OUT_PREFIX.txt         best alignment offset and edge agreement per row band
"""
import argparse
import sys

import numpy as np
from PIL import Image, ImageDraw


def box(text):
    if text is None:
        return None
    values = [int(v) for v in text.split(",")]
    if len(values) != 4:
        raise SystemExit("crop must be x,y,w,h")
    return values


def load(path, crop):
    image = Image.open(path).convert("RGB")
    if crop:
        x, y, w, h = crop
        image = image.crop((x, y, x + w, y + h))
    return image


def edges(image):
    gray = np.asarray(image.convert("L"), dtype=np.float32)
    gx = np.zeros_like(gray)
    gy = np.zeros_like(gray)
    gx[:, 1:-1] = gray[:, 2:] - gray[:, :-2]
    gy[1:-1, :] = gray[2:, :] - gray[:-2, :]
    magnitude = np.hypot(gx, gy)
    # Relative threshold: theme colors differ, so only strong local transitions count as geometry.
    threshold = max(12.0, np.percentile(magnitude, 90) * 0.5)
    return magnitude > threshold


def shift(mask, dx, dy):
    result = np.zeros_like(mask)
    h, w = mask.shape
    xs, xd = (0, dx) if dx >= 0 else (-dx, 0)
    ys, yd = (0, dy) if dy >= 0 else (-dy, 0)
    cw, ch = w - abs(dx), h - abs(dy)
    if cw <= 0 or ch <= 0:
        return result
    result[yd:yd + ch, xd:xd + cw] = mask[ys:ys + ch, xs:xs + cw]
    return result


def dilate(mask, radius=1):
    out = mask.copy()
    for dy in range(-radius, radius + 1):
        for dx in range(-radius, radius + 1):
            out |= shift(mask, dx, dy)
    return out


def agreement(a, b):
    # Fraction of edge pixels in each image that land within one pixel of an edge in the other.
    if a.sum() == 0 or b.sum() == 0:
        return 0.0
    da, db = dilate(a), dilate(b)
    return float(((a & db).sum() / a.sum() + (b & da).sum() / b.sum()) / 2)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mock")
    parser.add_argument("render")
    parser.add_argument("out")
    parser.add_argument("--crop")
    parser.add_argument("--render-crop")
    parser.add_argument("--search", type=int, default=12)
    args = parser.parse_args()

    mock = load(args.mock, box(args.crop))
    render = load(args.render, box(args.render_crop))
    width, height = max(mock.width, render.width), max(mock.height, render.height)
    canvas_mock = Image.new("RGB", (width, height), (255, 0, 255))
    canvas_render = Image.new("RGB", (width, height), (255, 0, 255))
    canvas_mock.paste(mock, (0, 0))
    canvas_render.paste(render, (0, 0))
    em, er = edges(canvas_mock), edges(canvas_render)

    best = (-1.0, 0, 0)
    for dy in range(-args.search, args.search + 1, 2):
        for dx in range(-args.search, args.search + 1, 2):
            score = agreement(em, shift(er, dx, dy))
            if score > best[0]:
                best = (score, dx, dy)
    _, bx, by = best
    for dy in range(by - 1, by + 2):
        for dx in range(bx - 1, bx + 2):
            score = agreement(em, shift(er, dx, dy))
            if score > best[0]:
                best = (score, dx, dy)
    score, dx, dy = best
    aligned = shift(er, dx, dy)

    overlay = np.zeros((height, width, 3), dtype=np.uint8)
    overlay[em] = (255, 70, 70)
    overlay[aligned] = (60, 220, 255)
    overlay[em & dilate(aligned)] = (255, 255, 255)

    side = Image.new("RGB", (width * 3 + 20, height), (40, 40, 40))
    side.paste(canvas_mock, (0, 0))
    side.paste(canvas_render, (width + 10, 0))
    side.paste(Image.fromarray(overlay), (width * 2 + 20, 0))
    side.save(args.out + "-side.png")

    shifted_render = Image.new("RGB", (width, height), (255, 0, 255))
    shifted_render.paste(canvas_render, (dx, dy))
    Image.blend(canvas_mock, shifted_render, 0.5).save(args.out + "-blend.png")

    lines = [f"mock={mock.width}x{mock.height} render={render.width}x{render.height}",
             f"best_offset_render_px dx={dx} dy={dy} edge_agreement={score:.3f}"]
    band = 40
    for top in range(0, height, band):
        a, b = em[top:top + band], aligned[top:top + band]
        if a.sum() + b.sum() == 0:
            continue
        lines.append(f"rows {top:5d}-{min(height, top + band):5d}: agreement={agreement(a, b):.3f} mock_edges={int(a.sum())} render_edges={int(b.sum())}")
    with open(args.out + ".txt", "w") as report:
        report.write("\n".join(lines) + "\n")
    print(lines[0])
    print(lines[1])


if __name__ == "__main__":
    sys.exit(main())
