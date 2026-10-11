#!/usr/bin/env python3
"""Side-by-side of one region, mock first then render, both at original pixels (beside when tall).

Usage: region.py MOCK RENDER OUT --crop x,y,w,h [--render-crop x,y,w,h] --region x,y,w,h [--offset dx,dy]

--crop/--render-crop select the comparable content areas (same as compare_mock.py). --region is in
content coordinates and is cut from both; --offset shifts the render region (render pixels).
A 1px magenta guide grid every 20px makes vertical/horizontal drift easy to read.
"""
import argparse

from PIL import Image, ImageDraw


def box(text):
    return [int(v) for v in text.split(",")] if text else None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mock")
    parser.add_argument("render")
    parser.add_argument("out")
    parser.add_argument("--crop")
    parser.add_argument("--render-crop")
    parser.add_argument("--region", required=True)
    parser.add_argument("--offset", default="0,0")
    parser.add_argument("--grid", type=int, default=20)
    args = parser.parse_args()
    mock = Image.open(args.mock).convert("RGB")
    render = Image.open(args.render).convert("RGB")
    mx, my = (box(args.crop) or [0, 0])[:2]
    rx, ry = (box(args.render_crop) or [0, 0])[:2]
    x, y, w, h = box(args.region)
    dx, dy = box(args.offset)[:2] if "," in args.offset else (0, 0)
    a = mock.crop((mx + x, my + y, mx + x + w, my + y + h))
    b = render.crop((rx + x + dx, ry + y + dy, rx + x + dx + w, ry + y + dy + h))
    beside = h > w
    out = Image.new("RGB", (w * 2 + 6, h) if beside else (w, h * 2 + 6), (255, 0, 255))
    second = (w + 6, 0) if beside else (0, h + 6)
    out.paste(a, (0, 0))
    out.paste(b, second)
    if args.grid:
        draw = ImageDraw.Draw(out)
        for ox, oy in ((0, 0), second):
            for gy in range(0, h, args.grid):
                draw.line([(ox, oy + gy), (ox + 6, oy + gy)], fill=(255, 0, 255))
            for gx in range(0, w, args.grid):
                draw.line([(ox + gx, oy), (ox + gx, oy + 4)], fill=(255, 0, 255))
    out.save(args.out)


if __name__ == "__main__":
    main()
