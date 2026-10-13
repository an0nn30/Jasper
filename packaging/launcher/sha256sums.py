#!/usr/bin/env python3
"""Writes packaging/launcher/upstream/SHA256SUMS: every vendored file as upstream ships it (README.md)."""
import hashlib
import pathlib
import re

root = pathlib.Path(__file__).resolve().parent / 'upstream'
lines = ["# SHA-256 of each vendored file with LF line endings and without JASPER-HOOK lines. See ../README.md."]
for path in sorted(p for p in root.rglob('*') if p.is_file() and p.name != 'SHA256SUMS'):
    data = path.read_bytes().replace(b'\r\n', b'\n')
    data = re.sub(rb'(?m)^[^\n]*JASPER-HOOK[^\n]*\n', b'', data)
    lines.append(f'{hashlib.sha256(data).hexdigest()}  {path.relative_to(root).as_posix()}')
(root / 'SHA256SUMS').write_text('\n'.join(lines) + '\n', encoding='utf-8')
