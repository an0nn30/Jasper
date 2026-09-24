# IntelliJ Platform classic-UI icons

Source: https://github.com/JetBrains/intellij-community, directory `platform/icons/src`.
Commit: f7377708b654b73b206da40bb382ecb4d44e8f12, fetched 2026-09-24 with
`tools/icons/fetch-intellij-icons.py`.
License: Apache License 2.0 (LICENSE.txt, the canonical text; upstream's root LICENSE.txt holds
JetBrains' open-source build terms for its IDE binaries and is not vendored); upstream notice in
NOTICE.txt.

Files are byte-identical copies. assets.tsv records each file's upstream path and SHA-256.
`_dark.svg` siblings are upstream dark variants; FlatLaf's FlatSVGIcon selects them under a dark
look and feel. Other icons use IntelliJ's light palette, which FlatLaf's global colour filter maps
to the running theme's Actions.* and Objects.* colours.

No JetBrains product logos are included. Refresh by editing ICONS in the script and rerunning it
from the repository root.
