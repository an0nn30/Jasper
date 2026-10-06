# Theme Engine Plan 2 — Consumer and SDK Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the new engine the only styling authority, migrate all bundled plugins, and enforce the new SDK boundary while preserving feature behavior.

**Architecture:** Application owners keep persistence, native windows and domain coordination; KitViews replace feature-owned controls and styles. Host adapters mount SDK UiView handles. Temporary old signatures are removed together after every bundled consumer has migrated.

**Tech Stack:** JBR 25, Gradle wrapper, Swing/FlatLaf 3.7 through `jasper-ui`, JDK-only SDK/testkit, existing plugin implementation libraries.

**Spec:** [Approved design](../specs/2026-10-04-jasper-theme-engine-design.md).
**Prerequisite:** [Plan 1](2026-10-04-theme-engine-1-engine-and-kit.md) implemented, reviewed and verified.

**Mandatory Task 4 policy wiring:** replace HostedKit's temporary `() -> true` animation supplier
with the live `ui.animations.enabled` application setting before mounting migrated production surfaces.
Test disabled motion and setting changes on an existing hosted view. This is also recorded in the
[temporary-path ledger](../../design/theme-engine/temporary-paths.md).
**Shared contracts:** [Exact paths and APIs](2026-10-04-theme-engine-contracts.md).
**Status:** Task 8 split by controller ruling into 8-kit, 8a (hosts and dialogs), 8b (tunnels) and 8c (SFTP and transfers), each reviewed and committed separately; 8-kit implemented 2026-10-05, review pending: kit/SDK additions `ListItem.tooltip`/`TableRow.tooltip`, item-list `Clicked`, `ItemList.enterAction`/`deleteAction`, `Toggle.pending`, `Button.iconSeverity` (warning favourite star) and the `star`/`star.filled` glyphs, mirrored module/SDK/host/fake with contract cases (SDK version unchanged, Task 11); 8-kit review clean after fix round 1. 8a (Sessions panel and host dialogs) implemented 2026-10-06, review pending: `HostsPanel` (kit Panel via `registerView`; groups toggle on a single click; favourite star reports only its action; row-owned menus), `HostEditor`, `ConnectionPanel` (kit indeterminate Progress, `ConnectionDots` deleted), `HostKeyPanel`, `ImportPanel`, `GroupNamePanel` and `ConfirmPanel` are owned UiViews with no primary (Enter submits only the group name from its field; Escape cancels everywhere, including the connecting overlay); Task 6d acceptance asserted in a real window (`StagedRemoteSessionsTest`) and through the testkit `panelHeader`; Remote plugin tests drive the EDT-confined views on the EDT and `ManagedImportIntegrationTest` waits off the EDT; 8a review clean after fix round 1. 8b (Tunnels) implemented 2026-10-06, review pending: `TunnelsPanel` (kit Panel via `registerView`; DOUBLE rows keyed by tunnel id; the switch shows only confirmed `TunnelManager` state, pending and operable while Starting, the failure as its error, once per pointer gesture; Enter toggles, a double click edits or offers Save as Tunnel, Delete/Backspace delete saved tunnels; row-owned menus; live counters in the row tooltip; kit Empty with New Tunnel), `TunnelEditor` and `ForwardPanel` owned UiViews with no primary (Escape cancels), `TunnelSwitch` deleted; Task 6d acceptance asserted in a real window (`StagedRemoteTunnelsTest`) and through the testkit `panelHeader`; deviation: More no longer re-packs the Forward a Port window (no SDK pack; the field is in the form's scroll area). Task 7 (Vault) implemented 2026-10-05, review pending: owned SDK UiViews with kit SecretBuffer secrets; kit/SDK additions `SecretKind.MULTILINE`, `Secret.submitAction` (fix round 1), field-named Browse, testkit `FakePluginHost.windowView`; Enter per baseline `f16ae32f` (Grant has no primary; import unlocks only from its passphrase field and never commits; the manager's inline Unlock is the window default); accepted Enter/Escape deviations: Escape runs each dialog's Cancel/Deny and closes the manager (or cancels its inline prompt), Okay is the default again after an inline unlock, Enter on a form whose primary is disabled by a field error does nothing; fix round 1 adds the shared `UnboundVault` keychain guard; visual-only changes and evidence (descriptor scenarios + real-host `VaultRenderEvidenceTest`) in `docs/STATUS.md`. Task 6d (post-merge) implemented 2026-10-05 and reviewed: SDK `PanelHeader` detail, connected dot and actions now show on borrowed kit panel headers (module host-only `KitView.setPanelHostHeader`/`PanelHostHeader`, `ToolWindowArea` translation, shared contract test on host and fake; no SDK change); merge-review minors done; Tasks 8/10 carry the acceptance item. Earlier: Main merged into the branch on 2026-10-05 (main `d3455d47`; merge recorded in `docs/STATUS.md` and the [temporary ledger](../../design/theme-engine/temporary-paths.md)): main's equal-width tabs, PanelHeader tool headers and notification-groups editor were re-bound to module roles/named metrics/kit in the merge; new raw consumers are owned by a new unit **Task 6d (post-merge)** (PanelHeader on borrowed kit headers), Task 8 (Remote panel redesign), Task 10 (File Server FS-3/FS-4 UI and the new Project Index plugin) and Task 11 (raw `PanelHeader` icons; eight bundled manifests). Task 6 complete pending the 6c review: 6c (pluginmanager, notificationui, Buddy adapter, carried chooser prompts) implemented in four commits (`8dfccd78` kit capabilities: row-owned ItemList menus, Shift+F10, Space operating a row switch, Toggle tooltip, Icon severity tint with `error`/`plugin` glyphs, flowing body text, pointer-only KitView; `d155155b` Plugins page and sheets; `1642c5d4` notifications/Errors/Buddy assertion with the five notification aliases unpublished; `ec7b8c2b` chooser prompt sheets), every Task 6 standard-ui ledger row migrated. Task 6 split into sequential sub-units 6a (settingsui/restart/themesui), 6b, 6c; 6b split again into 6b-1 (kit additions + shortcuthelp + files chooser, complete: `b5b5597b` kit, then one commit per family; review clean after fix round 3) and 6b-2 (palette kit additions + palette; implemented: a carried preliminary fix (window Enter follows a disabled OK/primary), the palette kit additions, then the palette; review fix round 1 done (queued-key prefix, step geometry, transparent query field), re-review pending). Task 6a implemented with controller-approved kit additions (tree counts/emphasis, row highlight + reveal, NUMBER field, SEARCHABLE choice) committed first, review pending. Task 5 complete (review clean after fix round 1). Earlier: Task 5 review fix round 1 committed, including the controller's `surface.status` ruling (contract change in module, SDK projection and TOML), the per-paint font-family leak fix and the plugin LAF identity test; fix review pending. Single-JVM app scope 212 suites/1535 tests, module 37/294, SDK 18/60, testkit 16/116, module check and all four architecture checks pass. Task 5 implemented; fix review pending. Tasks 1–4 and their fix rounds are reviewed complete. Task 5 default scope: app811/module283 tests; final affected scope105/module283; all four architecture checks and module check pass. Every requested filter matched (83 tests). Verified 2× scope: app6/module8, actual scale2.0 asserted. Exact commands/XML and intermediate corrections are in the Task 5 implementation report. Full repository gate and single final review remain Task 12. Checkboxes record executed work, not completion of all consumer migration.
**Integration corrections:** Module import-only facade additions, authored parent/dependent token regeneration, strict optional legacy bare-name fallback, proven imported color-slot restoration and live raw body-font overrides are documented in shared contracts. Task 5 removed ChromeKeys derivation; the approved retained defaults adapters have exact Tasks 6–10 migration and Task 11 removal ownership in the ledger; app/model serialization bridges are removed. The pinned spin/rgba gap is corrected with differential regressions. Retained tests moved to module-owner packages in the app integration source set when they need package-private access; old geometry/delegate/vendor-heuristic expectations now assert the approved module policy. Original source and before evidence bytes are unchanged.

**Execution rulings/deviations:** Task 6c (rulings G1–G9): plugin rows are an ItemList DOUBLE with row-owned menus (a context press never selects) and a compact switch replacing the checkbox; the details use a SEGMENTED Overview | Details choice over one Custom EDITOR text area and show the manifest icon as Custom PREVIEW content (the list shows the kit `plugin` tile); notification cards and history rows are app-owned layout shells of pointer-only kit views with former geometry as `NOTIFICATION_*`/`HISTORY_*` metrics; body/strong/description/caption Text now flows in vertical containers (kit-wide; BODY_STRONG included beyond the ruling's three roles so unread history titles wrap); a host-set selection becomes the keyboard row (kit, documented); poster raw notification icons are omitted until Task 11. Task 6b-2: the palette card is an app-owned layout shell hosting chrome KitViews with named `PALETTE_*`/`RESULTS_*` metrics and exact geometry assertions; the RESULTS list follows the query field (no focus, press runs, fixed heights, tag drops first, selection kept in view); a typed scope prefix becomes the chip through host normalization of `Edited` (the field shows the rest one event-loop turn later, which tests settle); a SEARCH field shows no label (its accessible name only; deviation: kit-wide, restoring the pre-Plan-2 look of the Settings and chooser search fields too); plugin raw palette icons are omitted until Task 11 (P5) and the chip has no icon (P9). Task 6b-1: app-owned layout shells host several chrome KitViews (as 6a); named `KEYMAP_*`/`CHOOSER_*` metrics keep the former geometry; the chooser preview text registers as `CustomKind.EDITOR` content because the kit's PREVIEW slot admits only canvases; kit ACTIONS stacks wrap (replacing the app WrapLayout) and crumb menus report only their action (no navigation). Task 6a keeps the Settings dialog's app-owned split hosting separate KitViews (shell unification Plan 3 Task 9); module kit additions (spec §6) precede the migration commit; SearchComboTest's behaviours moved to the module SearchableChoiceTest. Task 5 uses the actual outer-header owner ToolWindowArea (the brief named ToolWindowSurface). Native identity-sensitive Swing layouts remain Plan 3 Task 10 adapters with module-owned styling. Dynamic SDK key construction requires WorkspaceDefaults exact module-value publication until Tasks 7–10/11, superseding the preliminary zero-literal-reader audit. AppearanceTransaction batch restoration corrects an integration-discovered partial-font-table rollback failure. Task 5 adds explicit opt-in jasper.uiScale forwarding to app tests (previously only previews forwarded it), with a direct observed-scale assertion; scale-aware geometry/rollback scope runs at 2×, pointer-offset dock tests remain default-scale. Tasks 2–3 form one integrated engine/import implementation/review unit because of direct model dependencies. Task 12 provides the single final review (no duplicate). Task 1 adds pinned FlatLaf 3.7 only to plugin test runtime and uses test-only recursive layout of actual renderers. Buddy native-window visual evidence is unavailable headlessly and remains user acceptance; supported adapter behavioral evidence and app-owned notification/status captures are preserved. The inherited first preview's home-discovery outputs were discarded; final captures verify synthetic history/config roots. The unchanged stale standalone CommandPalettePreview is replaced only for evidence by a narrow real-owner fixture. See [inventory and frozen before evidence](../../design/theme-engine/migration-evidence.md).

## Global Constraints

- “All plugins must migrate. There is no supported legacy raw-Swing plugin UI compatibility path in the completed overhaul.”
- “Plan 2 changes styling consumption; Plan 3 changes screen composition and finishes visual matching.”
- “Themes supply colors. They cannot override design-system geometry or typography roles.”
- “Theme changes preserve current input, selection, focus, scroll position and domain state.”
- SDK types stay inside app `plugins`; UI module has no dependency on SDK, terminal, Buddy or app.
- Keep terminal/editor palettes, chrome/workspace separation, opacity, storage semantics, async operations, secret cleanup and existing platform ownership.
- Keep dependencies/version pins; JBR 25 and wrapper only. No native GUI, benchmark, AOT training, merge or push.
- Use exact shared types; any required API correction updates the contract appendix, both projections, host and fake in the same reviewed task.
- Review each task, fix findings, and commit with the required Co-Authored-By trailer. Full final review remains required.

## Review Focus

1. A plugin settings page cancelled after partial edits must still discard drafts and clean listeners — Tasks 4/6/10.
2. A moved editor/content tab must rebind workspace opacity and palette without losing its document or pin/close guard — Tasks 4/9.
3. Imported theme conflicts or installation failures must not rewrite a user's theme or saved selection — Tasks 2/3.
4. Async Vault/remote/file-server completion after owner close must not update disposed views or retain passwords — Tasks 7/8/10.
5. A misleading plugin SDK range must not silently activate an incompatible raw-Swing plugin — Task 11.

## File structure and migration ledger

Use source-root abbreviations defined in the shared contracts. Preserve domain types; replace view
composition in existing named UI owner files rather than introducing a second feature controller.
Existing `JPanel` classes become view controllers owning `UiView`/`KitView`, or descriptor builders,
according to their existing lifetime. No plugin gets a raw component from `UiView`.

The [exact temporary extraction/removal ledger](../../design/theme-engine/temporary-paths.md)
records the Plan 1 app copies, differently named helpers, bridges and copied resources assigned to
Tasks 2/3/5. It is audit evidence, not consumer migration; Task 1 folds these dispositions into its
complete inventory. App `config.TerminalColors` is a saved-choice record and remains app-owned.

The ledger below assigns every family, including non-panel surfaces. Task 1 expands each row into
exact current files and existing tests in `docs/design/theme-engine/migration.csv` and classifies
nonvisual helpers explicitly. Newly found UI owners must get a task before implementation proceeds.

| Family | Migration task | Composition task in Plan 3 |
|---|---|---|
| appearance, config theme consumers, application startup/configuration | 2 | 10 |
| themeimport, themesui | 3 and 6 | 9 |
| plugin runtime, settings adapters, windows/content mounting | 4 | 4 and 10 |
| workspace, platform visual helpers, dock presentation | 5 | 10 |
| settingsui, shortcuthelp, files, palette, pluginmanager, restart confirmation | 6 | 9 |
| Vault UI, Vault prompts/settings/pickers and manager | 7 | 1 and 4 |
| Remote Hosts, SFTP, Tunnels, Transfers, import/trust prompts | 8 | 2 and 5–7 |
| Editor UI/settings/find/Vim chrome; custom document area | 9 | 8 |
| File Servers UI and prompts | 10 | 3 and 7 |
| History, Snippets, Sample and their settings/palette UI | 10 | 8 |
| notificationui, application Buddy adapter, status/progress | 6 | 9 and 10 |
| SDK compatibility/legacy removal, documentation, distribution | 11–12 | 11 |

Temporary app-native migration layout helpers, if needed, belong to
`UI/swing/MigrationLayouts.java`, expose named roles only, and cannot accept arbitrary pixel values.
Task 1 records each call site and owning Plan 3 removal task. Plan 3 Task 11 deletes this class.
Old SDK signatures are a development-only bridge removed by Task 11; do not release intermediate
commits as a compatibility-supporting SDK.

## Task 1 — Freeze the consumer inventory and migration regressions

**Files:** Create `docs/design/theme-engine/migration.csv`, `AT/appearance/ThemeMigrationCoverageTest.java`; update this plan if new source owners are found.
**Interfaces:** CSV columns: `path,owner,surface,classification,migration_task,composition_task,existing_test,disposition`.
Classification is `standard-ui`, `custom-content`, `native-platform`, or `nonvisual-helper`.

- [x] Inventory Java sources importing Swing/AWT/FlatLaf, referencing UIManager/appearance, constructing controls, or overriding paint in app and every plugin. Include SDK signatures and fake implementations. Compare with `rg --files`; classify helper false positives rather than dropping them invisibly.
- [x] Add a coverage test that checks each inventoried path exists, every standard-ui row has a migration/composition task, and all bundled plugin ids occur. Assert History/Snippets are palette/settings consumers rather than inventing nonexistent manager windows.
- [x] Run `./gradlew :jasper-app:test --tests '*ThemeMigrationCoverageTest'`; verify the test initially exposes deliberately omitted fixture ownership, then complete the ledger. This checks inventory integrity, not proof that migration is finished.
- [x] Record existing behavioral tests next to each owner and the allowed custom-content classes for Editor. Capture pre-migration headless renders for each family and view states without updating golden expectations.
- [x] Review completeness against spec §8 and commit `Inventory all appearance consumers and preserve migration evidence`.

## Task 2 — Replace app resolution with the module engine

**Carried review obligation (Task 2, before Task 3 imported-theme visual acceptance):** correct the
known Minor pure evaluator parity gap. Bounded final-review probes found
`spin(#00ff00,30,relative)` → `ff00ff00` versus pinned FlatLaf `ff00ff7f`,
`spin(#00ff00,30)` → `ff00ff80` versus `ff00ff7f`, and
`rgba(#123456,50%)` → `80123456` versus `7f123456` (ARGB).
Add differential regressions, make spin's hue rotation absolute despite `relative`, and match pinned
channel materialization or obtain an explicit documented rounding-policy ruling. Until corrected,
affected imported themes cannot receive exact parity acceptance. This is separate from the approved
Carbon/vendor-heuristic distinction. [Evidence and ownership](../../ui-maintenance.md#known-minor-expression-parity-gap).

**Files:** Modify `APP/appearance/{Theme,ThemeCatalog,ThemeController,ThemeState,ResolvedTheme,BuiltInThemes,ThemeManager}.java`, `APP/config/{TerminalColors,UiFontConfig,WorkspaceThemeChoice}.java` as type adaptation requires, `APP/application/{JasperApplication,ThemeUpgrade}.java`; create `APP/appearance/{TerminalPaletteAdapter,AppearanceBindings}.java`; remove extracted algorithm copies when no references remain.
**Interfaces:** App `ThemeController` remains the public app selection owner. `TerminalPaletteAdapter.toTerminal(dev.jasper.ui.theme.TerminalColors): dev.jasper.terminal.config.Palette`; `AppearanceBindings.context(ThemeRoute): KitContext` routes app-owned bindings without SDK types.

- [x] Extend `AT/appearance/{ThemeResolverTest,WorkspaceGlassThemeTest,TerminalColorsThemeTest}.java` and `AT/workspace/ThemeControllerTest.java`. Preserve old selection/inheritance tests, add same-dark-theme switch, font override, independent workspace selection, and a failed installer that modified defaults before throwing.
- [x] Run `./gradlew :jasper-app:test --tests '*ThemeControllerTest' --tests '*TerminalColorsThemeTest' --tests '*WorkspaceGlassThemeTest'`; confirm the added new-binding/rollback tests fail before wiring.
- [x] Replace duplicated generation/parsing/resolution with module values and adapters. Publish `ThemePair` transactionally through module binding; keep persisted app names and config schema stable. Convert every terminal palette field losslessly. Retain EDT confinement, listener teardown and workspace-only updates.
- [x] Test compare-all-fields terminal round-trip and a source file checksum unchanged by theme load. Run focused tests and `verifyApplicationArchitecture verifyUiArchitecture`. Remove app copies listed in Plan 1 Tasks 1–2 once imports are redirected; leave no competing generator.
- [ ] Independent review of the integrated Tasks 2–3 commit (implementation self-review complete).

## Task 3 — Migrate discovery/import publication and installed themes

**Files:** Modify `APP/themeimport/{ThemeImporter,ThemeFiles,ThemeMigration,MigrationJournal,ConvertThemes}.java`, `APP/themesui/{ThemeImport,ThemeDialogs,ThemeImportView}.java`; remove extracted conversion/parser copies; redirect bundled parent/theme resource paths.
**Interfaces:** Keep `ThemeImporter.plan(Path)` and `commit(ImportPlan,Map<String,ConflictChoice>)` behavior and lock/fingerprint boundaries; convert module documents at the boundary without serializing through a lossy legacy model.

- [x] Extend existing `AT/themeimport/{ThemeImporterTest,ThemeFilesTest,ThemeMigrationTest,BundledThemeConversionTest}.java` and `AT/appearance/NativeThemeCatalogTest.java`: geometry override is diagnosed once, authored colors survive, a changed fingerprint still rejects replacement, and an invalid candidate leaves selected theme/file bytes intact.
- [x] Run `./gradlew :jasper-app:test --tests '*ThemeImporterTest' --tests '*ThemeFilesTest' --tests '*ThemeMigrationTest' --tests '*NativeThemeCatalogTest'`; confirm new semantic/import expectations fail initially.
- [x] Delegate conversion to `dev.jasper.ui.themeimport`, retain app-owned discovery precedence and atomic filesystem operations. Remove conversion copies and adjust bundled resource loading. Show discarded override diagnostics through current import review UI; no automatic rewriting of existing theme files.
- [x] Test dark/light/custom/IntelliJ/Alacritty fixtures plus duplicate names, corrupt XML/archive, inherited colors and workspace glass. Run focused tests and conversion parity suite; commit with Task 2 as the approved integration unit. Independent review remains pending.

## Task 4 — Mount kit handles across all plugin surface entry points

**Files:** Modify `SDK/ui/{Panels,PanelFactory,Windows,WindowSurface,PanelSpec}.java`, `SDK/settings/{SettingsPanel,SettingsPanelHost}.java`, `APP/plugins/{HostedKit,HostedKitView,HostedUi,PluginSettingsPage,HostedContext}.java`, `APP/windows/{AuxiliarySurface,AuxiliaryWindows}.java`, `APP/contributions/PanelEntry.java`, `APP/workspace/WindowContributions.java`, fake counterparts; create `AT/plugins/KitMountingTest.java`.
**Interfaces:** New `PanelFactory.create(PanelHost): UiView`; `WindowSurface.setContent(UiView): void`; `Windows.openTab(ContentSpec,WindowOwner,UiView): ContentTab`; `SettingsPanel.create(SettingsPanelHost): UiView`; `SettingsPanelHost.row(Setting<?>): Nodes.Node`. `PanelSpec` uses a semantic icon name instead of arbitrary Icon; keep identity/anchor/split/toggle-action behavior.

- [x] Contract tests cover foreign UiView rejection, singleton window replacement, content-tab pinning and close guards, owner closure during chooser, settings Apply/Cancel, and mount/unmount repeated without double-close. Register a throwing callback and verify one plugin failure is contained.
- [x] Run `./gradlew :jasper-app:test --tests '*KitMountingTest' --tests '*HostedKit*Test'`; first red identifies missing mount paths.
- [x] Add new typed overloads/adapters while bundled callers migrate in Tasks 7–10. During the transition, keep changed factory method names separate (e.g. `registerView`, `createView`) to avoid impossible return-type-only overloads. Task 11 replaces final old signatures and removes bridge names. Translate SDK handles to app-native module content entirely inside app `plugins`.
- [x] Preserve current window placement, modality, settings draft lifecycle and content close/quit guards. Reparent a hosted editor view and assert current workspace palette/opacity without recreating the document. Run host/fake contracts plus app architecture checks.
- [x] Self-review then commit `Mount owned kit views in plugin panels windows tabs and settings`.

**Execution deviations:** The real custom settings draft/factory owner is `PluginPanelPage`, so it is
adapted instead of changing generated `PluginSettingsPage`. Existing app-native window/contribution
lifetimes already expose the needed disposal/guard/reparenting hooks; SDK translation stays entirely
inside `app.plugins`, with no gratuitous change to those transport owners. Nested factory View markers
preserve legacy functional settings implementers until Task 11 and select exactly one route. A stale
ConfigurationController test now verifies the Tasks 2–3 approved subscriber containment contract:
publication and other subscribers continue, with no false installation warning. Full gate remains Task 12.

**Review round 1 correction:** Standard-row Reset removes a saved override rather than staging an
explicit default. The fake page rebases clean/pending rows on reload and removes its subscription on
close; its behavior now uses an independent tombstone draft and FakeSetting's existing conversion,
leaving only explicit descriptor projection in KitSettingRows. Typed duplicate open retries stranded
ContentRegistry entries with their original component, leaving the incoming view caller-owned.
Regression and scope notes are in the task report. Generic custom-document tests do not claim actual
Editor-plugin syntax palette application; that integration remains Task 9. SDK JavaDoc baseline
warning noise remains assigned to Task 12.

## Task 5 — Migrate workspace and native chrome styling

**Files:** Modify `APP/workspace/{WorkspaceTheme,WorkspaceStyler,WorkspaceStyles,WorkspaceStyleLayer,WorkspaceComboPopup,WindowContent,WindowChrome,WindowHeader,WindowTabs,WindowToolbar,WindowStatusBar,ToolWindowHeader,ToolWindowSurface,ToolStripe,SplitDividerBorder,FindBar,TitleSearchField}.java`, `APP/platform/{AppIcons,NamedIcons,ThemeZone}.java` and additional standard-ui owners from the ledger.
**Interfaces:** Ordinary controls consume KitContext/roles; specialized folder/terminal/dock painting consumes module metrics/palette adapters. Preserve public app-native signatures where they coordinate behavior.

- [x] Extend current workspace/theme, folder-tab, ToolWindow and status tests. Assert exact existing tab minimum 160, maximum 240, accent pill 64×2, open/close/switch timing 80/100/130ms and hidden-rail square corner behavior survive source migration. Add a standard-control theme switch test retaining focused search text.
- [x] Run `./gradlew :jasper-app:test --tests '*WorkspaceThemeTest' --tests '*ThemeControllerTest' --tests '*WindowTabsTest' --tests '*WindowTabMotionTest' --tests '*FolderTabScaleTest' --tests '*ToolWindow*Test'`; ensure each requested filter matches tests.
- [x] Remove direct feature color/font/inset/FlatLaf styling in favor of module roles and kit controls. Keep docking/drag geometry in JDK-only `dock`; visual adapters live in workspace. Preserve native title-bar actions and window glass plumbing; named icons route through kit tinting while app identity icons remain platform assets.
- [x] Mount kit panels with their existing module header: suppress ToolWindowSurface's old outer header, attach host drag/focus listeners through `panelHeader`, and inject host placement/collapse actions through `setPanelHostActions`. Keep teardown with the surface. Test one visible title/count row and one invocation per host action.
- [x] Run existing headless corner/tab/docking/glass tests and 2× geometry checks. Verify no duplicate panel headers after plugin mounts, and context-menu focus behavior survives. Review then commit `Bind workspace and chrome presentation to shared design roles`.

## Task 6 — Migrate app-owned forms, browsers, palette and notifications

**Files:** Modify ledger owners under `APP/settingsui`, `APP/shortcuthelp`, `APP/files`, `APP/palette`, `APP/themesui`, `APP/pluginmanager`, `APP/restart`, `APP/notificationui`; modify `APP/application/BuddyIntegration.java` only for supported appearance adaptation.
**Interfaces:** App controllers build module ViewSpec/KitView directly; no SDK imports. Preserve SettingsDraft, file chooser models, palette scopes, notification actions/deduplication and Buddy facade contracts.

- [x] Extend existing SettingsDialog, FileChooserPanel, palette, plugin-manager and notification tests: Cancel preserves config bytes, search highlight/query survives theme change, chooser selection/scroll/preview survives, palette selection persists, notification actions fire once. Add Buddy adapter assertion that it still uses only supported values.
- [x] Run family-scoped tests from the ledger before replacing controls; add failure assertions showing current non-kit construction/stale style paths. Use behavior assertions rather than deleting old tests that depend on form names.
- [x] Replace standard controls through descriptors/kit composition while retaining present navigation arrangement. Use kit tables/trees for chooser and settings navigation; preserve overlay scrollbar semantics, saved bounds and preview behavior. Notifications use kit presentation, existing notification services and accessible actions. Map settings schema editors to typed field/choice/toggle roles.
- [x] Reuse current headless preview entry points, adapting them to real new controls. Run app standard-ui family tests plus architecture check. Review then commit `Migrate app forms navigation palette and notification presentation`.

## Task 6d (post-merge) — PanelHeader on borrowed kit panel headers

**Why:** main's SDK 0.14.2 `PanelHost.setHeader(PanelHeader)` (detail, connected dot, icon actions) shows on the native `ToolWindowHeader`, but a kit (ViewSpec) Panel mounts with its borrowed kit header, where `ToolWindowArea.setHeader` was a silent no-op. Tasks 8 and 10 convert exactly the four callers (HostsPanel, SftpPanel, TunnelsPanel, FileServersPanel) to kit panels, so this unit runs before Task 7. Controller-defined unit after the main merge (`fc3fe566`); merge-review Important 1 and minors.
**Files:** `UI/swing/{PanelHostHeader (new),PanelHeaders,Buttons,KitView}.java`; `APP/workspace/ToolWindowArea.java`; tests `jasper-ui` `PanelHostHeaderTest`, app `ToolWindowKitPanelTest`, `WindowPanelsTest`, `KitMountingTest`, `WorkspaceMetricsTest`, `AppContractTest`; testkit `ContractHarness`, `PluginContractTest`, `FakeContractTest`, `FakePluginHost.closePanel`; contracts appendix, ui/sdk architecture docs, plugin authoring, `migration.csv`, temporary ledger.
**Interfaces:** module host-only `KitView.setPanelHostHeader(String, PanelHostHeader)`; `PanelHostHeader(detail, active, actions)` with `Action(label, Icon, Runnable)` and `EMPTY`. No SDK change (version unchanged); SDK types stay in `app.plugins` (`HostedUi` → `PanelHeaderState`), `ToolWindowArea` translates `PanelHeaderState` → `PanelHostHeader`.
**Arrangement (mirrors the native header):** one row: title, count, detail (COUNT/TEXT_SECONDARY) with the connected dot before it (`STATUS_DOT`, success floored to 3:1 on `SURFACE_PANEL`, the surface it sits on); then the panel's own header actions; then the contributed icon actions (GHOST panel actions, focusable, host artwork); then Options and Hide.

- [x] RED: module `PanelHostHeaderTest` (detail/dot/order/focusable/once, kept by position across metadata refreshes and descriptor updates, EMPTY clears and releases listeners, theme switch restyles, replacement header starts empty, close releases) failed 4/5 against an API stub; app `ToolWindowKitPanelTest` (3 new: borrowed-row projection and update/clear; hide/re-show/replacement/remove/close teardown; theme switch) failed 3/6; `WindowPanelsTest` window-level test failed with the `ToolWindowArea` change stashed.
- [x] GREEN: kit header gains the host detail label, dot and contributed buttons (`Buttons.ButtonControl` accepts host artwork); `ToolWindowArea` keeps per-id metadata, applies it to whichever header the frame has (native or borrowed), and clears it with the host actions in the borrowed header's release (guarded so a closed or replaced view is untouched).
- [x] Shared contract: `PluginContractTest.aKitPanelsHeaderIsPerWindowReplacedContainedAndIgnoredAfterClose` on the app host and the fake through new harness methods `panelHeader`, `runPanelHeaderAction`, `closePanel`; `KitMountingTest` pins the HostedUi view-panel boundary (contained failing action, inert after close).
- [x] Merge-review minors: `PanelHeaderState` CSV owner `6;11`; `WorkspaceMetricsTest` pins `TOOL_HEADER_DETAIL_GAP`/`TOOL_HEADER_ACTIVE_DOT` = 6 and requires them in `ToolWindowHeader` (mutation-checked); `WindowTabs.surface()` blend constants recorded in the temporary ledger for Plan 3 Task 10; merge GUI checks listed in `docs/STATUS.md`.
- [x] Run module/SDK/testkit checks, the app suite, Remote and File Server suites and the four architecture checks; commit `Show PanelHeader metadata on borrowed kit panel headers` (controller review pending).

## Task 7 — Migrate Vault views and secure input ownership

**Files:** Modify `P(vault)/ui/{PasswordPanel,EditorForm,EntryEditor,KeyGeneratorForm,ChangePasswordForm,ManagerPanel,PickerPanel,KeyImportPanel,GrantPanel,VaultManagerWindow}.java` and all UI-bearing Vault owners in the ledger; tests `PT(vault)/ui`.
**Interfaces:** Controllers own SDK UiView and descriptors. Secret values are consumed through `withSecret`; existing SecretDocument cleanup becomes kit SecretBuffer ownership, with async operation copies explicitly owned by Vault service code. Keep Vault exported API unchanged.

- [x] Extend PasswordPanel/EditorForm/VaultManager and chooser tests for nonempty mutable passwords, paired reveal, cancel/lock/dispose, failed creation and owner closure while unlock/save is pending. Assert no secret in ViewSpec/events/log text and rejected operations do not clear unrelated fields.
- [x] Run `./gradlew :jasper-plugin-vault:test --tests 'dev.jasper.vault.ui.*'`; observe new UiView/cleanup assertions fail before migration.
- [x] Replace Swing field/form construction with SDK descriptors and stable ids. Keep master-password acceptance, device-binding default, Vault account/key/note persistence, grants, clipboard expiration and SSH generation behavior. Convert manager/picker lists using semantic row ids; do not introduce screenshot example credentials.
- [x] Migrate all Vault panel/window/dialog/settings mounting to typed handles. Complete per-plugin ownership audit; run the entire Vault test task and host contract test for closing a Vault prompt. Review then commit `Migrate Vault UI to secure host-rendered kit views`.

**Execution deviations (controller rulings 2026-10-05):** kit/SDK additions precede the Vault commit (`SecretKind.MULTILINE`,
"Browse for <label>" names, `FakePluginHost.windowView`). Inputs use kit labels above rather than a fixed label column;
switches replace checkboxes; Enter follows baseline `f16ae32f` per dialog (fix round 1 adds the kit `Secret.submitAction` so
Enter stays scoped to the import passphrase field, and makes the manager's inline Unlock the window default). Accepted
Enter/Escape deviations: Escape runs each dialog's existing Cancel/Deny; Escape closes the manager or cancels its inline
prompt; Okay is the default again after an inline unlock (baseline `removeNotify` bug); Enter on a form whose primary is
disabled by a field error does nothing. Evidence is descriptor scenarios plus the reusable real-host capture
`AT/plugins/VaultRenderEvidenceTest`. Remote's `ManagedImportIntegrationTest` now drives Vault's views on the EDT.

## Task 8 — Migrate Remote panels, dialogs and transfer UI

**Files:** Modify all standard-ui owners in `P(remote)/ui`, including `HostEditor`, `HostsPanel`, `HostRows`, `ConnectionPanel`, `HostKeyPanel`, `ImportPanel`, `GroupNamePanel`, `ConfirmPanel`, `PanelStyle` (main merge), `tunnels/*` (including main's `TunnelSwitch`), `sftp/*` presentation files, `transfers/*` presentation files; delete `FlatToolBar` after replacing its callers. Keep nonvisual controllers in the ledger unchanged unless callback adaptation requires it.
**Interfaces:** SDK Panel/Form/ItemList/Table descriptors; existing remote/Vault/Editor APIs and transfer models remain the operation owners. Stable host, tunnel and transfer ids back row selection.

Execution note (controller rulings, 2026-10-05): Task 8 runs as 8-kit (kit/SDK gaps G1–G5: row tooltips, item-list
`Clicked`, list Enter/Delete actions, pending switch, star glyphs), then 8a (Sessions panel, host/connection/host-key/
import/group/confirm dialogs, `ManagedImportIntegrationTest` EDT restructure, `ConnectionDots` deletion), 8b (Tunnels) and
8c (SFTP, transfers, panel shortcut actions, `FlatToolBar`/`PanelStyle` deletion, ledger close). Remote dialogs have no
PRIMARY (baseline `f16ae32f` had no default button); Enter submits only from the group-name and new-folder fields.
The checkboxes below are ticked when 8c closes the task.

- [ ] Add contract-oriented assertions to existing Remote UI tests for auth mode changes retaining keys, stale connection completion, trust decisions, SFTP selection after refresh, editable transfers, and collapsed/reopened panels. Test per-row toggle intent separate from row activation and no raw component returned to host.
- [ ] Run `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.ui.*'`; make new migration assertions fail before changing UI.
- [ ] Convert forms and panel content; keep layouts recognizable until Plan 3. Bind switches to confirmed manager state/futures, never silently assume start/stop succeeded. Use FileSources/Windows chooser APIs, preserve follow-folder provenance and conflict resolution. Remove plugin paint/delegate/border/font/color code, using chips/status/icon roles instead.
- [ ] Acceptance (Task 6d): the converted Hosts, SFTP and Tunnels kit panels keep main's header metadata through `PanelHost.setHeader` on the borrowed header — Hosts its count and New session/Import sessions actions, SFTP its connection name and connected dot, Tunnels its count and New Tunnel… action. Assert it with the panel mounted in a real `ToolWindowArea`/window (one title row, no duplicate in-panel action row) and through the testkit `panelHeader`.
- [ ] Run all Remote tests plus architecture check for optional Editor/Vault imports. Verify no networking in new UI fixture tests and no changed host-key/security defaults. Review then commit `Migrate Remote controls panels and transfer presentation to the kit`.

## Task 9 — Migrate Editor chrome and preserve its custom document area

**Files:** Modify `P(editor)/ui/{EditorWindow,EditorTab,Forms,FindBar,GoToLinePanel,LanguagePicker,EditorSettings,StatusLine,EditorTheme,GlassSurface,DialogAsker}.java`, `P(editor)/vim/ui/VimBar.java`, related UI owners from ledger; tests `PT(editor)/ui` and existing editing contract suites.
**Interfaces:** Ordinary chrome/forms use SDK UiView; only the RSyntaxTextArea/document area registers CustomContent of kind EDITOR. Continue using SDK syntax palette and view-scoped workspace opacity, now mapped from module values.

- [ ] Add theme/reparent tests retaining buffer text, undo history, selection, caret, language, dirty flag and close guard; cover Find/Vim/search overlays after dark→different-dark switch. Confirm settings Cancel leaves editor configuration unchanged.
- [ ] Run `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.*' --tests '*VisualSearchUiTest'`; observe new custom-content mounting assertions fail.
- [ ] Keep RSyntaxTextArea and document painting in the narrow approved custom-content class; move standard buttons/fields/settings/status to kit descriptors. Declare exact custom-content paths in the architecture allowlist, not an exemption for all of `dev.jasper.editor.ui`.
- [ ] Test view shutdown unregisters palette/opacity updates, async save completion after close, and host content-tab relocation. Run full Editor test task and plugin architecture gate; review then commit `Migrate editor chrome while preserving specialized document behavior`.

## Task 10 — Migrate File Servers, remaining plugins and settings

**Files:** Modify `P(fileserver)/ui/{ServerEditor,FileServersPanel,ServerRows,ConfirmPanel,PanelStyle,RowArtwork,ProtocolChip}.java` (the last three arrived with the main merge); `P(indexer)/{IndexerPlugin,ui/IndexerSettingsPanel,ui/FilesScope}.java` (Project Index, arrived with the main merge); `P(sample)/{SamplePlugin,SampleSettingsPanel}.java`; `P(history)/{HistoryPlugin,HistorySettings,HistoryScope}.java` and `P(snippets)/{SnippetsPlugin,SnippetsScope}.java` only where the inventory identifies UI/icon/settings consumers; corresponding tests.
**Interfaces:** File Server rows expose confirmed state through compact switches and protocol hue chips; Sample settings use descriptor rows; History/Snippets retain palette contribution APIs with semantic icons/labels.

- [ ] Add ServerEditor tests for protocol changes, account chooser, port/path input, TLS dependencies and async start failure; preserve existing security rules. Test hidden field values do not change unrelated model state, stale completion after close is ignored, and no request reads live user folders during UI tests.
- [ ] Run `./gradlew :jasper-plugin-fileserver:test :jasper-plugin-sample:test :jasper-plugin-history:test :jasper-plugin-snippets:test :jasper-plugin-indexer:test`; first confirm the added new-contract assertions fail on old paths.
- [ ] Convert all standard UI construction and raw icon access. Keep protocol listeners, network operation/service code, settings persistence and palette search semantics intact. All plugins now mount using new handles; identify any remaining raw factory calls by the ledger and `rg`.
- [ ] Acceptance (Task 6d): the converted `FileServersPanel` kit panel keeps its count and New File Server… action through `PanelHost.setHeader` on the borrowed header (one title row; asserted mounted in a real window and through the testkit `panelHeader`).
- [ ] Run these full plugin tests and `verifyPluginArchitecture`; review then commit `Migrate remaining bundled plugin presentation and settings contracts`.

## Task 11 — SDK 0.15.0 cutover and legacy enforcement

**Files:** Modify `SDK/JasperSdk.java`, final `SDK/ui`/`SDK/settings` signatures from Task 4, `APP/plugins/{PluginResolver,HostedUi,HostedKit}.java`, `APP/pluginmanager/PluginIcons.java`, `FAKE` implementations, all eight `plugins/*/src/main/resources/plugin.toml` (Project Index joined with the main merge), `gradle/{sdk-architecture,plugin-architecture,application-architecture}.gradle.kts`; add `AT/plugins/KitCompatibilityTest.java` and forbidden-construction fixtures.
**Interfaces:** SDK version becomes `0.15.0`; bundled ranges become `>=0.15.0, <0.16`. Final signatures are those in Task 4. Also change `ActionSpec.icon` and `ScopeSpec.icon` to `Optional<String>`, `ContentPresentation.icon` to a semantic `String`, and `StatusItem.setIcon`/`StatusProgressState.withIcon` to take a semantic `String` (preserve each existing optional convention). Update `SDK/palette/ScopeSpec.java` and all host/fake adapters. Remove old raw factory/mount methods and arbitrary Icon overloads for standard controls; named kit icons and constrained monochrome registration remain.

- [ ] Add a complete version assertion:
  ```java
  @Test void advertisesTheKitCompatibilityBoundary() {
      assertThat(dev.jasper.sdk.JasperSdk.VERSION).isEqualTo("0.15.0");
  }
  ```
  Add resolver tests for old `<0.15` plugin, falsely broad range with obsolete bytecode references, and valid 0.15 kit plugin. Expected: actionable incompatibility before activation when detectable; contained linkage failure otherwise. Add architecture negative fixtures for JButton construction, paint override and arbitrary icon color outside custom-content allowance.
- [ ] Run `./gradlew :jasper-app:test --tests '*KitCompatibilityTest' :jasper-sdk:test`; see red version/legacy-boundary assertions.
- [ ] Remove development-only overloads and factory bridge names after all consumers compile against final API. Update exact-version assertions in SDK tests and manifests/test resources/docs. Scan plugin bytecode entry-point references for removed SDK methods at admission where feasible; retain existing classloader containment for unanticipated linkage failures. Do not blanket-reject ordinary domain use of JDK AWT value types.
- [ ] Enforce no direct visible Swing construction/styling/painting in standard plugin code; recognize narrow approved custom-content owners. Remove obsolete app appearance/FlatLaf/style adapters, retained legacy `WorkspacePalette.current()` UIManager fallback paths and unneeded duplicated icon resources. Keep legacy theme-file parsing only in the new engine.
- [ ] Run SDK/testkit contracts, every plugin test and all architecture gates. Review then commit `Cut over to kit-only SDK 0.15 and remove legacy presentation paths`.

## Task 12 — Migration completion, documentation and distribution review

**Files:** Update `docs/{sdk-architecture,plugin-authoring,app-architecture,app-maintenance,ui-architecture,ui-maintenance}.md`, module READMEs, migration.csv, this plan and `docs/STATUS.md`; adjust packaging checks only for actual dependency changes.
**Interfaces:** Every inventory row has evidence/disposition; remaining temporary migration layouts have specific Plan 3 removal owners. New engine is the only algorithm/token authority.

- [ ] Audit inventory against current source and bytecode, including settings/plugins/pickers/hidden dialogs. Confirm no migrated owner caches stale resolved colors, reconstructs a view on theme switch, or owns numeric design literals. Domain geometry and specialized rendering exceptions remain explicitly classified.
- [ ] Update compile-checked SDK examples to new contracts, document 0.15 incompatibility and custom-content limits, and remove instructions to construct raw Swing controls. Run `./gradlew check :jasper-app:installDist` headlessly; inspect staged plugin manifests, module jar visibility and exact XML totals.
- [ ] Render each surface family after live chrome/workspace switches, with default and enlarged fonts. Test all shipped themes through semantic completeness and representative control smoke renders; inspect dark/light/imported custom frames. Plan 2 acceptance is complete source migration and retained behavior, not final layout matching.
- [ ] Perform independent whole-branch review, fix findings with regressions, run changed-source hygiene and `git diff --check`, record actual evidence and remaining Plan 3 layout work. Commit the verified status. No merge/push/native launch.

## Spec coverage and handoff

Spec §§3–5 engine replacement/compatibility → Tasks 2–3/11; §6 all consumers → Tasks 5–10;
§7 complete plugin contract boundary → Tasks 4/7–11; §8 inventory → Tasks 1/12;
§10 behavior, theme switching and ownership → all tasks, finalized in Task 12.
Plan 3 changes composition using this engine; it must not discover an unowned legacy styling subsystem.
