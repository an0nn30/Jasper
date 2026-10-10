# Plan 2 consumer migration completion audit

**Final-review fix wave (2026-10-10):** The whole-branch review found four Important defects and two Minor issues.
The consolidated fixes retain NUMBER document/shape/normalization behavior, editable Choice pending editor state
across real FlatLaf publication/tree refresh, readable fallback light/dark switches, and title-hint/card-count
surface floors. Independent literal source-color assertions and exceptional dialog cleanup are corrected.
Explicit switch roles retain precedence. Shared authored text roles stay raw; the existing consuming-surface policy
may adjust low-contrast hint/count paint. `./gradlew check :jasper-app:installDist` passed: 9,658 XML tests, zero failures/errors, six environment skips; 107 tasks, 22 executed/85 up-to-date.
The first full attempt failed only because the new paint-test scaffold needed its migration.csv row; the row is
recorded and the failed XML/log preserved. This required rerun is documented in the final-fix report. Earlier Task12
evidence below remains historical and unchanged. Scoped independent re-review and controller native rerun are pending;
this audit does not claim final Plan3/native/mock acceptance.

Task12 implementation, 2026-10-10. Tasks1–11 are independently approved through `2fc3bea8`.
The controller owns the independent whole-branch review after this task; this document does not claim it passed.
Plan3 owns native composition and visual acceptance. No GUI, native benchmark, AOT training, merge or push was
performed by this task. Detailed commands, XML snapshots, timings and source scans are in the local Task12 report.

## Authority and audit scope

`jasper-ui` is the only theme parsing/resolution/generation and standard-control geometry/typography/artwork authority.
SDK0.15 exposes immutable descriptors and owned views; both host and fake implement its contracts. All eight bundled
plugins use this boundary. Custom content is limited to the exact specialized document owners and documented
terminal/domain/native ownership, never ordinary controls wrapped in a custom canvas.

The inventory keeps every source row and its removal/move/disposition. Source discovery includes tests, helpers,
Settings, hidden dialogs and pickers. `ThemeMigrationCoverageTest` verifies path/existing-test references and owner
coverage. `verifyPluginArchitecture` compiles the authored-plugin presentation check and its negative/positive
fixtures; it checks plugin outputs, **not app classes or vendor jars**. SDK classfile signatures include inherited
and generic exposure. The other package gates enforce app/SDK confinement and independent module DAGs. Direct static
construction/style/paint checks do not promise reflection/dynamic-code sandboxing.

The app source sweep found the stopped-plugin Settings page, rejected-file warning and failed-page fallback still
constructing raw labels/buttons. Task12 migrated `PluginStubPage`, `PluginPageBody`, `PluginPanelPage` and
`PluginSettingsPage` to the dialog's chrome KitContext, with contained failure handling, owned view disposal,
plain literal text and real Open in Editor dispatch retained. Existing error/reset/reload tests remain, and live
light/dark 24pt tests verify retained owner identity, correct colors, literal HTML-like text and disposal.
`PluginRuntime` supplies the same context to stopped pages. DETAIL replaces local numeric padding.

Remaining direct app controls are the explicitly retained workspace/native shells in the temporary-path ledger:
window/tool headers, tabs/stripes, terminal find/notices/start/error, status/progress/breadcrumbs, toolbar/menu,
WindowOverlay and Mac platform installation. Their styling consumes module roles, and Plan3 Task10 owns their final
composition. App chooser preview, plugin detail text, incident message/trace and notification wrapped text are
specialized readable/domain surfaces using live module palette/font values. Layout-only JPanel/scroll/split shells
are not additional styling engines. No new raw plugin standard-control allowance was added.

## Actions, lifetime and live appearance

The targeted source/action sweep checked descriptor Button actions, Form cancel and Field submit routes against
controllers in app Settings/chooser/shortcut help/theme import/plugin manager/notifications/palette and bundled
Vault, Remote Sessions/Tunnels/SFTP/transfers, Editor dialogs/Find, File Server, Sample and Indexer. The prior Task10
ID/action mismatch fixes are present. Intentional differing node/action IDs include Editor answer/GoToLine,
Remote rename/create and Vault manager cancel/okay→close. Existing staged-host tests exercise actual button/key
routes; fake node-ID injection is not treated as mounted action evidence. No further broken route was found.

A real-kit regression reproduced the controller's native plain-Choice observation: Swing `updateComponentTreeUI`
replaced the module delegate with MetalComboBoxUI. `ChoiceCombo.updateUI` now reinstalls kit painting and semantic
popup styling; repeated updates retain selection, dispatch no synthetic selection events and keep actual selection
routing. Searchable-choice tests also remain green. This is a lifecycle correction, not Plan3 pixel calibration.

`ThemeCompletionSmokeTest` covers all five shipped themes and an actually converted custom `.theme.json`, on both
live chrome/workspace routes at 13pt and24pt. It asserts every semantic role, retained field/Choice identity,
text/selection and enlarged fonts, and saves 24 real-control frames. Existing actual-owner captures cover settings,
workspace, chooser, theme import, shortcut help, managers, notifications, Vault, Remote, Editor and File Server;
Fresh default evidence contains376frames across19families plus22title/tab regions. The2× actual-owner suite
contains96frames across9families;2× existing previews add252frames across8families plus22title/tab regions.
Settings already includes five shipped themes at default/18pt, and shortcut help default/18/24pt. The composite
preview previously ignored the scale option; Task12 forwards it and verifies actual26pt metadata. Enlarged plugin
and title-bar canvases use equivalent logical sizes; app fixed canvases are constrained-font stress fixtures.
The title-bar wide/narrow and Remote full-fingerprint assertions remain. SDK fake-only descriptor captures are
not represented as real plugin pixels. Native focus, monitor scaling,
window decorations and final reference-image matching remain Plan3 acceptance.

The full gate also exposed nonfatal catalog advisories being promoted to import failures. Task12 preserves an
app-only advisory flag through ImportPlan warnings, preview details and ImportReport; genuine malformed catalog
errors remain failures and valid providers can still import. The focused regression checks source bytes and
once-only advisory reporting. No SDK value or parser contract changed. Imported/bundled fixture checks now accept
only the documented diagnostic classes while retaining original byte/color/provenance assertions.

Legacy `[ui] Panel.background` edits remain in the generated snapshot, but inherited explicit `surface` tokens
win in canonical presentation after the raw adapter removal. The application polling regression checks both that
retention and a second `[semantic] surface.panel` edit that changes actual installed color, with configuration
bytes unchanged. This is intentional documented precedence, not a new Task12 production policy.

## Actual-owner performance

JBR `25.0.4.1+1-b583.48`, JetBrains, headless macOS, EDT, no network/process/GUI benchmark:

| Owner and fixture | Measured work | Initial bounded run |
| --- | --- | --- |
| HistoryPanel, 500 distinct entries with content/action, 1,502 mounted KitViews, 400×600 | Construct; three recursive layouts plus visible paint; four live light/dark updates plus layout/paint | Mount137.205ms; first layout/paint152.502ms; theme passes93.377/74.863/69.607/75.084ms |
| Actual staged Remote SFTP panel, 200 files, 3 changed transfer rows, 360×720 | TransferStrip.rows→owning panel render→host reconciliation→layout/paint, 5Hz; five warmup and20 measured ticks | Mean7.293ms; p9510.447ms; max10.807ms against200ms cadence |

History's first mount plus layout/paint is a visible one-off cost around290ms at the maximum store size; theme updates at500 rows are
not frame-budget work. No interactive per-keystroke path was added. SFTP ticks were below6% of the200ms interval in
this bounded run, so no speculative table caching was introduced. These timings are observations, not portable
thresholds or native latency guarantees; memory/GC tail latency and larger-than-fixture listings are not measured.
The final full run measured History mount52.539ms/layoutpaint91.999ms and theme passes64.678–69.664ms;
SFTP mean6.683ms/p958.108ms/max11.451ms. Both fixtures retain meaningful owner assertions and print timings without timing-based test failures.

## Distribution and verification

`./gradlew check :jasper-app:installDist` passed on JBR25 in2m48s:107tasks,24executed/83up-to-date.
Frozen XML records9,645tests:9,639passed,zero failures/errors,six skips. App/Buddy/terminal/UI tests executed;
SDK/testkit/eight plugin test suites were UP-TO-DATE. Skips are absent fish, unavailable font-fallback fixture,
three Windows-only tests and opt-in real keychain. All five architecture tasks and app/Buddy/terminal/UI JavaDoc
executed; SDK JavaDoc was UP-TO-DATE. Compile-checked examples/documentation tests passed within those suites.
Installed distribution contains20app runtime jars and49jars across8plugin folders. Every installed plugin manifest
requires `>=0.15.0, <0.16`; plugin jar hashes match staging, and app/UI/SDK jars match module outputs. All plugin
runtime jars were scanned for embedded app/UI/SDK packages; none were present. Exact hashes/manifests and XML are
preserved in Task12's local evidence. Narrow reruns are preserved separately and never added to distinct full-suite totals.
The staged app owns `jasper-ui.jar`; plugin directories contain their plugin and pinned vendor jars, not engine,
app, SDK or testkit copies. The isolated loader tests verify runtime visibility. No dependency versions changed.

The new unused try-resource warning in `PresentationOwnershipTest` was genuinely introduced by Task11b; Task12
uses the controller meaningfully during setup and removes it. The prior report's blanket “existing warnings”
attribution was too broad. Final log emitted100app/36Buddy/100terminal JavaDoc warnings (caps may hide more), plus4existing
Remote/Vault capture try/InterruptedException warnings. SDK JavaDoc warning backlog was not rerun; no global
suppression or deletion of meaningful tests was used to manufacture a clean log.

The historical uncompiled `tools/previews/PluginPanelsPreview.java` and its sole runnable caller,
`tools/previews/plugin-panels.gradle`, are retired. They required removed raw APIs. Historical STATUS commands are
historical evidence only. Plan3 Task1 provides the new actual-feature harness.

## Named Plan3 handoff

| Surface | Final composition owner |
| --- | --- |
| Vault heroes and new preview harness | Task1 |
| Add Host; New File Server | Tasks2/3 |
| Vault manager/menu/sheets | Task4 |
| Sessions; Tunnels/SFTP | Tasks5/6 |
| File Server rows and Remote transfer companion | Task7 |
| Editor banner/scrollbar/Find/Vim strip and remaining plugin composition | Task8 |
| App Settings/chooser/palette/managers/notifications, including migrated host stub/errors | Task9 |
| Workspace/native shells, tabs/header/status geometry and native text footprint | Task10 |
| Native mixed-theme/font/accessibility and whole-surface acceptance | Task11 |

Earlier temporary-path entries are chronological. Completed Task11 removals supersede their old Task11 TODOs.
Review carries remain available to the controller's whole-branch reviewer: headless versus native coverage,
minor accessibility/interaction probes, documented receiver failure atomicity limits, bounded expression parity,
warning backlog and measured performance limits. None is silently declared independently reviewed by this task.
