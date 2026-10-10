# Consumer inventory and before evidence

Task 1 began 2026-10-05 against production revision `f16ae32f`. The [exact consumer ledger](migration.csv)
records 971 Java paths found among 1,652 app, SDK, testkit and bundled-plugin Java sources enumerated
with `rg --files`. The scan includes Swing/AWT/FlatLaf imports, UIManager/appearance, control
construction and paint overrides; explicit appearance and temporary extraction owners supplement
it. `ThemeMigrationCoverageTest` repeats source discovery and checks paths, task assignments,
existing-test paths and every bundled plugin id. Inventory integrity is not completed migration.

The initial Task 1 snapshot contained 289 standard-ui, 2 custom-content, 31 native-platform and 649 nonvisual-helper
rows (532 production owners and 439 explicitly test/fixture owners); the CSV expands as new owners migrate. A fixture-only
Swing scaffold preserves evidence; its disposition does not require production migration in Task 12.
Nonvisual helpers include geometry/data/configuration/architecture false positives; native-platform
rows retain native window ownership. The [Plan 1 temporary paths](temporary-paths.md) remain binding;
exact Java copy-removal dispositions are carried into the CSV, while resource removals stay in that
ledger. No new production owner or API gap was required to construct these captures.

Editor custom document painting has exactly two final source owners:
`plugins/editor/src/main/java/dev/jasper/editor/ui/DocumentArea.java` and
`plugins/editor/src/main/java/dev/jasper/editor/vim/VimTextArea.java`. DocumentArea owns RSTA/scroll/gutter,
EditorTheme application, glass Src fill and the vendor popup. EditorTab/FindBar are descriptors; no enclosing
surface or package is exempt. Task 9b's explicit unchanged raw VimBar child is a temporary bridge removed by 9c
before Task 9 completes; Task 11 enforces only the final owners. The actual staged preservation/corner/track images
are under `editor-hosted`; accepted banner/track visual differences and their Plan 3 Task 8 owner are ledgered in
[temporary-paths.md](temporary-paths.md).

## Immutable before snapshot

[before-f16ae32f/manifest.json](before-f16ae32f/manifest.json) records production revision, capture
runtime/theme/font/state/scale provenance, source-harness hashes, dimensions, checksums and
limitations for **408 owner PNGs across 22 families**. [SHA256SUMS](before-f16ae32f/SHA256SUMS)
covers every asset and metadata file plus the manifest and thirteen inspected contact sheets.
`ThemeMigrationCoverageTest` verifies byte integrity and required family presence. This directory
is a frozen before snapshot: never regenerate it during migration. Normal tests and preview runs
write only current `build/` outputs. Existing golden expectations were not changed.

| Family | Before states |
|---|---|
| Vault | create, invalid create, unlock, manager empty/populated/filtered/locked, add menu, login password/key; actual SSH-key form empty/invalid-path/edit |
| Remote | sessions empty/populated/filtered, add Vault/agent/invalid, connection busy/error, trust, import preview/busy/failed |
| SFTP | disconnected, loading, connected, operation error |
| Tunnels | empty, stopped, busy, failed |
| Transfers | progress, paused, failed, finished, unavailable, existing-items question |
| File Servers | empty, starting, failed, new anonymous/Vault/invalid |
| Editor | document/Vim document, find, replace, invalid pattern, go-to-line/invalid, unsaved question |
| History and Snippets | real hosted palette scopes populated/filtered-empty; History real settings. Snippets has no settings page or manager window |
| Sample | standalone real settings owner using fake SDK host, plus actual application-hosted bundled settings |
| Settings | appearance, window, font, shell/narrow, system, search, draft, keymap/search, plugin list/details and six actual bundled settings pages |
| App dialogs | chooser, theme-import conflict/pending/failure, plugin removal/busy/consent, notification card and errors empty/incident/cleared |
| App chrome | workspace pending launch, actual command palette query/no-match, dock shown/hidden, hosted content/status, titlebar context single/tmux/remote/narrow plus separate actual WindowTabs owner region, shortcuts sections/expanded/recording |

Plugin surfaces use real FlatLaf 3.7 dark/light and system font; the test-only helper records effective
font and look-and-feel per image. App owner tests install existing application theme aliases; settings
and shortcut previews preserve five built-in themes with system font size and explicit 18pt override.
Representative dark/light app outputs and all 110 plugin images were inspected in contact sheets;
other app themes are supplementary evidence. Hosted-content and titlebar previews use 2x pixels.
Composite Swing list renderer children require recursive layout in the peerless test harness; the
helper lays out the actual renderer and restores it after capture. No production UI repair was made.

The plugin testRuntimeOnly FlatLaf 3.7 dependency is a controller-approved deviation for authentic
baseline captures. The helper is in SDK testkit test fixtures and uses JDK UIManager lookup, retaining
JDK-only SDK signatures and unchanged plugin production/distribution dependencies. Fake SDK icons
are labeled placeholders. The Sample fake SettingsPanelHost supplies placeholder labels; its real
application-hosted page is separately captured. Native focus, glass, traffic lights and window
placement are not proven headlessly.

App BuddyIntegration remains the supported appearance/lifecycle adapter. Its existing
BuddyIntegrationTest/BuddyTestSupport evidence is preserved. Buddy's native-window visual before/
after acceptance is **unavailable headlessly and requires a user run**; no Buddy-internal render
bridge or native show() call was added. App-owned notification and hosted status UI are captured.

The inherited SettingsDialogPreview first run discovered history through user.home/environment.
It produced no history-content capture, made no user write, stopped its plugin runtime and exited;
those outputs were discarded. The final opt-in child JVM verifies synthetic user.home, HISTFILE,
XDG_CONFIG_HOME and APPDATA before startup; discovered history/config roots resolve there. Only
fixture history and snippet data are populated. Shell HOME/CODEX_HOME are unchanged. Existing
standalone CommandPalettePreview has a stale pane-query assertion (5 expected, 12 actual baseline
rows); its source/assertion remains unchanged. The narrow fixture uses actual WindowContent and
CommandPalette with controlled commands instead.

## Reproduce current renders

Use JBR 25 and the wrapper, headlessly:

```sh
./gradlew :jasper-app:test --tests '*ThemeMigrationEvidenceTest' \
  :jasper-plugin-vault:test --tests '*ThemeMigrationEvidenceTest' \
  :jasper-plugin-remote:test --tests '*ThemeMigrationEvidenceTest' \
  :jasper-plugin-editor:test --tests '*ThemeMigrationEvidenceTest' \
  :jasper-plugin-fileserver:test --tests '*ThemeMigrationEvidenceTest' \
  :jasper-plugin-sample:test --tests '*ThemeMigrationEvidenceTest'
./gradlew :jasper-app:themeMigrationPreview
./gradlew :jasper-app:test --tests '*ThemeMigrationCoverageTest'
```

Later captures are current-state comparisons against the frozen snapshot, not a replacement of it.
Task 2 owns the carried color-expression parity gap; Task 1 does not alter the evaluator.

Review correction before migration (Task 1 round 1, base `84f7ff4a`): the inherited titlebar
preview had sized only the bar, leaving the containing root width zero and collapsing the context.
The test-only preview now lays out the actual root and asserts positive context bounds, wide/narrow
collapse state and tmux/remote text. The strip has no tabs; separate `*-window-tabs.png` captures
preserve the actual WindowTabs owner. All 24 strip/tab-region outputs were inspected. The actual
`EntryEditor.key` now has six dark/light empty, missing-path-validation and populated-edit captures
using synthetic paths with save/I/O forbidden. These authorized corrections replace the incomplete
pre-migration titlebar assets and append key/tab images; manifest provenance and all 572 asset
checksums are refreshed. Five coverage tests include a distinct-context/tab-state guard. Normal
checks still never replace the frozen baseline.
