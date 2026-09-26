#!/usr/bin/env python3
"""Vendors the classic IntelliJ Light theme chain at a pinned intellij-community commit.

Run from the repository root. Rewrites themes/intellij/*.theme.json and assets.tsv, and copies
LICENSE.txt and NOTICE.txt from the IntelliJ icons vendored at the same commit (see
tools/icons/fetch-intellij-icons.py for why LICENSE.txt is the canonical Apache-2.0 text).
A missing file (HTTP 404) fails the run.
"""
import hashlib
import pathlib
import shutil
import urllib.request

SHA = "f7377708b654b73b206da40bb382ecb4d44e8f12"
BASE = f"https://raw.githubusercontent.com/JetBrains/intellij-community/{SHA}/"
SOURCE = "platform/platform-resources/src/themes/"
OUT = pathlib.Path("jasper-app/src/main/resources/dev/jasper/app/themes/intellij")
ICONS = pathlib.Path("jasper-app/src/main/resources/dev/jasper/app/icons/intellij")
# IntelliJ Light -> IntelliJ -> Darcula: each file names the next as its parentTheme.
THEMES = ["Light.theme.json", "intellijlaf.theme.json", "darcula.theme.json"]

OUT.mkdir(parents=True, exist_ok=True)
rows = ["resource\tsource\tsha256"]
for name in THEMES:
    with urllib.request.urlopen(BASE + SOURCE + name) as response:
        data = response.read()
    (OUT / name).write_bytes(data)
    rows.append(f"{name}\t{SOURCE}{name}\t{hashlib.sha256(data).hexdigest()}")
(OUT / "assets.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
for name in ("LICENSE.txt", "NOTICE.txt"):
    shutil.copyfile(ICONS / name, OUT / name)
print(f"{len(THEMES)} themes from {SHA}")
