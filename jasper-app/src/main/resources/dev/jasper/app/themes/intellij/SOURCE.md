# Classic IntelliJ theme files and default editor schemes

Source: https://github.com/JetBrains/intellij-community, files under `platform/platform-resources/src`.
Commit: f7377708b654b73b206da40bb382ecb4d44e8f12, fetched 2026-09-25 with
`tools/themes/fetch-intellij-themes.py`.
License: Apache License 2.0 (LICENSE.txt, the canonical text); upstream notice in NOTICE.txt. Both
are copies of the files vendored with the IntelliJ icons from the same commit.

Files are byte-identical copies; assets.tsv records each file's upstream path and SHA-256.
`Light.theme.json` ("IntelliJ Light") names `intellijlaf.theme.json` ("IntelliJ") as its
parentTheme, which names `darcula.theme.json` ("Darcula"). Jasper's ThemeLoader resolves that
chain. These are the classic-UI themes, not the New UI ones under `themes/expUI/`.

`DefaultColorSchemesManager.xml` holds IntelliJ's own `Default` and `Darcula` editor colour
schemes. Theme plugins' editor schemes name one of them as `parent_scheme`, and Jasper's
EditorScheme reads the console colours a scheme leaves out from them.
