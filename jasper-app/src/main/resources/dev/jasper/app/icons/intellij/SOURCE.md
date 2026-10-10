# Retained IntelliJ native artwork

Byte-identical SVGs from JetBrains/intellij-community commit `60b8fbadc2f08ef18a6ecab0c138a849e14f13fb`.
`assets.tsv` records the three retained upstream paths and SHA-256 hashes.

- `terminal/toolwindow/terminal.svg`: ResidentTray's native macOS menu-bar template.
- `nodes/pluginLogo.svg` and its dark sibling: manifest identity preview fallback in PluginIcons.

Standard controls use the jasper-ui named glyph registry. The obsolete 885-choice catalog,
resource-loader API and catalog generation script are removed. Refresh these retained assets
individually from the pinned source and update their hashes after review.
Artwork copyright and Apache-2.0 notices remain in each SVG, LICENSE.txt and NOTICE.txt.
