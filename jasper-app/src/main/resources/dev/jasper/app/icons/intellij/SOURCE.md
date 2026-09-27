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
