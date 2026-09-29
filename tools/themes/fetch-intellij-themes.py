#!/usr/bin/env python3
"""Vendors IntelliJ's bundled UI themes and editor colour schemes at a pinned intellij-community commit.

Run from the repository root. Rewrites the files listed in FILES under themes/intellij/ (upstream
subdirectories kept) and assets.tsv, and copies LICENSE.txt and NOTICE.txt from the IntelliJ icons vendored at the same
commit (see tools/icons/fetch-intellij-icons.py for why LICENSE.txt is the canonical Apache-2.0 text).
A missing file (HTTP 404) fails the run.
"""
import hashlib
import pathlib
import shutil
import urllib.request

SHA = "f7377708b654b73b206da40bb382ecb4d44e8f12"
BASE = f"https://raw.githubusercontent.com/JetBrains/intellij-community/{SHA}/"
OUT = pathlib.Path("jasper-app/src/main/resources/dev/jasper/app/themes/intellij")
ICONS = pathlib.Path("jasper-app/src/main/resources/dev/jasper/app/icons/intellij")
# (resource name, upstream path). The themes are every themeProvider in intellij.platform.ide.impl.xml
# and the schemes every bundledColorScheme in intellij.platform.projectModel.impl.xml at SHA, plus
# DefaultColorSchemesManager.xml (IntelliJ's Default and Darcula schemes). Theme plugins name these as
# parentTheme, editorScheme or parent_scheme. IntelliJ Light -> IntelliJ -> Darcula is the classic chain;
# the New UI (expUI) and Islands themes build on it.
SRC = "platform/platform-resources/src/"
FILES = [(path.removeprefix("themes/"), SRC + path) for path in [
    "themes/Light.theme.json",
    "themes/intellijlaf.theme.json",
    "themes/darcula.theme.json",
    "themes/expUI/expUI_dark.theme.json",
    "themes/expUI/expUI_light.theme.json",
    "themes/expUI/expUI_light_with_light_header.theme.json",
    "themes/islands/ManyIslandsDark.theme.json",
    "themes/islands/ManyIslandsLight.theme.json",
    "themes/islands/ManyIslandsDarcula.theme.json",
    "themes/islands/HighContrast.theme.json",
    "DefaultColorSchemesManager.xml",
    "themes/Light.xml",
    "themes/expUI/expUI_lightScheme.xml",
    "themes/expUI/expUI_darkScheme.xml",
    "themes/expUI/expUI_darculaContrastScheme.xml",
    "themes/islands/highContrastScheme.xml",
    "themes/islands/IslandSchemeDark.xml",
]]

OUT.mkdir(parents=True, exist_ok=True)
rows = ["resource\tsource\tsha256"]
for name, source in FILES:
    with urllib.request.urlopen(BASE + source) as response:
        data = response.read()
    (OUT / name).parent.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_bytes(data)
    rows.append(f"{name}\t{source}\t{hashlib.sha256(data).hexdigest()}")
(OUT / "assets.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
for name in ("LICENSE.txt", "NOTICE.txt"):
    shutil.copyfile(ICONS / name, OUT / name)
print(f"{len(FILES)} files from {SHA}")
