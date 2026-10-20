# The Enterprise theme, ported from IntelliJ

`port.json` describes the Enterprise theme (`plugins/enterprise/src/main/resources/themes/enterprise.json`) as a port of
two of IntelliJ's own bundled themes, which `jasper_theme.py`'s `convert_port` converts: the theme's name and author,
and for each variant the IntelliJ theme and editor scheme, the terminal scheme's name, the roles it chooses and the
override keys it sets.

Light converts "IntelliJ Light" (`Light.theme.json`, parent `intellijlaf.theme.json`, with the editor scheme
`Light.xml`); Dark converts the New UI "Dark" (`expUI/expUI_dark.theme.json`, parent `darcula.theme.json`, with
`expUI/expUI_darkScheme.xml`). The inputs are the vendored files in `../intellij`, from JetBrains/intellij-community
commit f7377708b654b73b206da40bb382ecb4d44e8f12 (`../intellij/SOURCE.md`); the port is identical at tag idea/2026.2.3
(commit 8062c061ff33e22041d9f1844db5b8b8a6b5e4d7), whose copies of these files differ only in keys the port does not read.

Each variant's roles are the converter's mapping with `roles` chosen over it. An override key is filled when IntelliJ's
own theme sets its IntelliJ key exactly, not through a `*.` wildcard, laid over the role it stands on; `overrides` then
replace values: Light's `surface.header.active` is #DFE2E9, the focused tool window header the user asked for. The
accents are IntelliJ's selection blues, Light's #2675BF (`List.selectionBackground`) and Dark's `Blue5` #366ACE, not
IntelliJ's default-button blues (#528CC7 and #3574F0), which hold white labels under 4.5:1, so the text guard would
paint every label on the accent black.

`test_jasper_theme.py` converts `port.json` and compares the result with `enterprise.json` byte for byte. After a
converter change, `JASPER_THEME_UPDATE_GOLDEN=1 python3 -m unittest discover -s tools/themes -p 'test_*.py'` rewrites it.
