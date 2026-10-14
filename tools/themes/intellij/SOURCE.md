# IntelliJ's bundled themes and editor schemes

Source: https://github.com/JetBrains/intellij-community, files under `platform/platform-resources/src`.
Commit: f7377708b654b73b206da40bb382ecb4d44e8f12, fetched 2026-09-25 (classic themes and
`DefaultColorSchemesManager.xml`) and 2026-09-29 (the rest), then restored byte for byte from Jasper's commit
`c1e968ee^` (where they were `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/intellij/`) for the theme
converter, `tools/themes/jasper_theme.py`.
License: Apache License 2.0 (LICENSE.txt, the canonical text); upstream notice in NOTICE.txt. Both
are copies of the files vendored with the IntelliJ icons from the same commit. Since June 2026 the
repository's root LICENSE.txt holds JetBrains' "Open-Source Build Terms", which govern JetBrains'
distributed IntelliJ IDEA and PyCharm builds and state that those builds consist of software under
the Apache 2.0 license; the source files vendored here remain Apache 2.0.

Files are byte-identical copies, in their upstream subdirectories; assets.tsv records each file's
upstream path and SHA-256. They are every `themeProvider` IntelliJ registers in
`platform/platform-impl/resources/intellij.platform.ide.impl.xml` and every `bundledColorScheme` in
`platform/projectModel-impl/resources/intellij.platform.projectModel.impl.xml` at that commit.

Themes (id, file, parentTheme). A plugin's `parentTheme` names the id; jasper_theme.py resolves the chain.
- `Darcula` darcula.theme.json; `IntelliJ` intellijlaf.theme.json (parent Darcula);
  `JetBrainsLightTheme` ("IntelliJ Light") Light.theme.json (parent IntelliJ). These are the classic-UI themes.
- `ExperimentalDark` ("Dark") expUI/expUI_dark.theme.json (Darcula); `ExperimentalLight` ("Light")
  expUI/expUI_light.theme.json (IntelliJ); `ExperimentalLightWithLightHeader`
  expUI/expUI_light_with_light_header.theme.json (ExperimentalLight). The New UI themes.
- `Islands Dark` islands/ManyIslandsDark.theme.json (ExperimentalDark); `Islands Light`
  islands/ManyIslandsLight.theme.json (ExperimentalLightWithLightHeader); `Islands Darcula`
  islands/ManyIslandsDarcula.theme.json (ExperimentalDark); `JetBrainsHighContrastTheme`
  islands/HighContrast.theme.json (Islands Dark).

None is offered as a Jasper theme: they are the parents and editor schemes that theme plugins build on.

Editor schemes (name, file, parent_scheme). A theme's `editorScheme` and a scheme's `parent_scheme` name them;
jasper_theme.py reads the colours a scheme leaves out from its parents.
- `Default` and `Darcula` in DefaultColorSchemesManager.xml.
- `IntelliJ Light` Light.xml (Default); `Light` expUI/expUI_lightScheme.xml (Default); `Dark`
  expUI/expUI_darkScheme.xml (Darcula); `Darcula Contrast` expUI/expUI_darculaContrastScheme.xml
  (Darcula); `High contrast` islands/highContrastScheme.xml (Darcula); `Islands Dark`
  islands/IslandSchemeDark.xml (Darcula).
