#!/usr/bin/env python3
"""Vendors Jasper's IntelliJ classic-UI icons at a pinned intellij-community commit.

Run from the repository root. Rewrites icons/intellij/*.svg, assets.tsv and NOTICE.txt.
It does not touch LICENSE.txt: upstream's root LICENSE.txt is JetBrains' "OPEN-SOURCE BUILD
TERMS" for its IDE binaries, not the Apache License 2.0 that actually covers these SVGs (each
SVG header cites Apache 2.0). LICENSE.txt here is the canonical Apache-2.0 text instead, vendored
by hand; update it manually if it ever needs to move.
A missing file (HTTP 404) fails the run: update ICONS instead of skipping silently.
"""
import hashlib
import pathlib
import urllib.request

SHA = "f7377708b654b73b206da40bb382ecb4d44e8f12"
BASE = f"https://raw.githubusercontent.com/JetBrains/intellij-community/{SHA}/"
OUT = pathlib.Path("jasper-app/src/main/resources/dev/jasper/app/icons/intellij")
# local name: (upstream path without ".svg", upstream ships a "_dark" variant)
ICONS = {
    "add": ("platform/icons/src/general/add", True),
    "moveToWindow": ("platform/icons/src/actions/moveToWindow", True),
    "splitVertically": ("platform/icons/src/actions/splitVertically", True),
    "expandComponent": ("platform/icons/src/general/expandComponent", False),
    "find": ("platform/icons/src/actions/find", True),
    "gearPlain": ("platform/icons/src/general/gearPlain", True),
    "refresh": ("platform/icons/src/actions/refresh", True),
    "execute": ("platform/icons/src/actions/execute", True),
    "history": ("platform/icons/src/vcs/history", True),
    "bookmark": ("platform/icons/src/nodes/bookmark", True),
    "close": ("platform/icons/src/actions/close", False),
    "closeHovered": ("platform/icons/src/actions/closeHovered", False),
    "exit": ("platform/icons/src/actions/exit", True),
    "arrowDown": ("platform/icons/src/general/arrowDown", True),
    "console": ("platform/icons/src/debugger/console", True),
    "server": ("platform/icons/src/webreferences/server", True),
    "previousOccurence": ("platform/icons/src/actions/previousOccurence", True),
    "nextOccurence": ("platform/icons/src/actions/nextOccurence", True),
    "matchCase": ("platform/icons/src/actions/matchCase", False),
    "regex": ("platform/icons/src/actions/regex", False),
    "searchWithHistory": ("platform/icons/src/actions/searchWithHistory", False),
    "folder": ("platform/icons/src/nodes/folder", False),
    "menu-saveall": ("platform/icons/src/actions/menu-saveall", True),
    "copy": ("platform/icons/src/actions/copy", True),
    "menu-paste": ("platform/icons/src/actions/menu-paste", True),
    "remove": ("platform/icons/src/general/remove", True),
    "web": ("platform/icons/src/general/web", True),
    "information": ("platform/icons/src/general/information", False),
    "help": ("platform/icons/src/actions/help", True),
}


def fetch(path):
    with urllib.request.urlopen(BASE + path) as response:
        return response.read()


OUT.mkdir(parents=True, exist_ok=True)
rows = ["resource\tsource\tsha256"]
for local, (upstream, dark) in sorted(ICONS.items()):
    for suffix in ([".svg", "_dark.svg"] if dark else [".svg"]):
        data = fetch(upstream + suffix)
        (OUT / (local + suffix)).write_bytes(data)
        rows.append(f"{local}{suffix}\t{upstream}{suffix}\t{hashlib.sha256(data).hexdigest()}")
(OUT / "assets.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
(OUT / "NOTICE.txt").write_bytes(fetch("NOTICE.txt"))
print(f"{len(rows) - 1} files from {SHA}")
