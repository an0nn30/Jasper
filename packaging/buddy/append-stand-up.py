#!/usr/bin/env python3
"""Append the measured ImageGen transition cells without re-rendering existing pixels."""
import sys
from pathlib import Path
from PIL import Image

runtime, transitions = map(Path, sys.argv[1:])
old = Image.open(runtime).convert('RGBA')
new = Image.open(transitions).convert('RGBA')
assert old.size in ((1680, 96), (1932, 96)), old.size
assert new.size == (252, 96), new.size
for i in range(3):
    cell = new.crop((i * 84, 0, (i + 1) * 84, 96))
    left, top, right, bottom = cell.getbbox()
    assert 0 < left < right < 84 and 0 < top < bottom == 92
    assert set(cell.getchannel('A').get_flattened_data()) <= {0, 255}
result = Image.new('RGBA', (1932, 96))
original = old.crop((0, 0, 1680, 96))
result.paste(original, (0, 0))
result.paste(new, (1680, 0))
assert result.crop((0, 0, 1680, 96)).tobytes() == original.tobytes()
result.save(runtime)
print('Appended three cells; original twenty preserved byte-for-byte as decoded RGBA')
