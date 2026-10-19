# Selected Jasper engine originals

This engine catalog admits only seventeen byte-identical SVG files: fourteen listed in assets-selected.tsv, and
`run` (`classic/actions/execute` and its dark sibling) and `application` (`classic/runConfigurations/application`)
listed in assets-classic.tsv.
Recovered from Jasper revision d0f2747eb3ccb3e0fd8e01f4719cfd18aff17e38 (aab810d2 parent),
whose upstream revision and original complete-catalog provenance follow below. The complete catalog,
its generated SDK BundledIcon API and refresh script described below are historical provenance;
they are not part of this selected engine catalog. No automatic sibling/size discovery is performed.

The native workspace chrome context (`IconRegistry.chromeIcon`: title bar, toolbar, tool window stripes and
status bar) admits twenty further byte-identical SVG files listed in assets-chrome.tsv (add, moveToWindow,
splitVertically, expand, chevronDown, nodes/folder, webReferences/server, toolwindows/notifications, locked
and the terminal tool window icon, each with its dark sibling). They were recovered from Jasper `main`
revision c2b6283a4e814e2aaac2a375a52acd96ce5f8e89 (`jasper-app/.../icons/intellij`, whose assets.tsv
records the same source paths and SHA-256 values) for Plan 3 Task 10; the Desktop workspace references
depict exactly this artwork. Descriptor views, panels, tiles and plugin registrations never receive it.

# IntelliJ New UI artwork

Byte-identical SVGs from JetBrains/intellij-community commit `60b8fbadc2f08ef18a6ecab0c138a849e14f13fb`.
Complete `platform/icons/src/expui` plus `plugins/terminal/resources/icons/expui`
(the latter under `terminal/`): 1744 SVGs, 885 base/size choices.
Dark siblings are automatic; size variants are individually available. Intentional
empty/stub assets are preserved. This includes upstream plugin/file-type marks;
these assets do not replace Jasper's application branding.

Artwork copyright and Apache-2.0 notices are retained in each SVG. LICENSE.txt is
the canonical Apache-2.0 text; NOTICE.txt is upstream's notice. The upstream root
LICENSE.txt contains IDE build terms and is not this artwork's license.

assets.tsv records source paths and SHA-256. catalog.txt and SDK BundledIcon are
generated together. Refresh with `python3 tools/icons/fetch-intellij-icons.py`.
The script pins tree IDs, verifies Git blob hashes, stages downloads, and only
then replaces managed SVGs. Builds and runtime do not access the network.

# IntelliJ classic artwork

Fourteen SVGs under `classic/`, listed in assets-classic.tsv with their upstream paths under `platform/icons/src/`
and SHA-256 values: `general/add`, `actions/splitVertically`, `general/settings`, `actions/moveToWindow`,
`actions/find` and `actions/execute`, each with its dark sibling, plus `general/expandComponent` and
`runConfigurations/application`. They come from JetBrains/intellij-community at tag `idea/2026.2.3`
(commit `8062c061ff33e22041d9f1844db5b8b8a6b5e4d7`) and are byte-identical, each keeping its copyright comment.
`python3 -I tools/icons/fetch-classic-icons.py` reproduces them. A file without `_dark` serves both variants.
Chrome receivers marked `ChromeArtwork.CLASSIC` get six of them (add, split, zoom, settings, window.add and search);
the catalog's `run` and `application` get the rest.
