# Theme Engine Plan 3 — Surface Composition and Visual Matching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor all migrated surfaces onto canonical composition and match the nine supplied visual references using active-theme colors.

**Architecture:** Feature controllers produce existing kit descriptors; the kit owns every design metric and paint treatment. Domain operations and the SDK boundary are already migrated in Plan 2. Reference comparisons use actual mounted feature views, and the shared gallery catches any kit correction affecting multiple features.

**Tech Stack:** JBR 25, Gradle wrapper, SDK 0.15, `jasper-ui`, headless Swing rendering, JUnit/AssertJ and original reference PNGs.

**Spec:** [Approved design, especially panel composition §6.1](../specs/2026-10-04-jasper-theme-engine-design.md).
**Prerequisites:** [Plan 1](2026-10-04-theme-engine-1-engine-and-kit.md) and [Plan 2](2026-10-04-theme-engine-2-consumer-migration.md) implemented, reviewed and verified.
**Shared contracts:** [Types, paths and test conventions](2026-10-04-theme-engine-contracts.md).
**Status:** Written for review; not executed. No deviations. Native visual acceptance remains user-run.

## Global Constraints

- “The mocks win for depicted visual detail; their sample colors are not required colors.”
- “Theme changes preserve current input, selection, focus, scroll position and domain state.”
- “Plugins use SDK contracts and host adapters, never the module or application internals directly.”
- “Mock text and example accounts are fixture data. They do not alter real stored credentials, paths, host defaults or server security settings.”
- Every visible form/control uses kit roles; fix shared rendering in the kit rather than adding feature paint overrides.
- Preserve folder-tab minimum 160, maximum 240, indicator 64×2, timing 80/100/130ms and other approved specialized behaviors.
- Native platform ownership, security decisions, async cleanup and SDK compatibility are not visual refactor choices.
- Use JBR 25 and wrapper; no GUI, benchmark, AOT, merge or push. Changes get focused tests, per-task review and Co-Authored-By commits.
- Source paths and Nodes signatures come from the shared appendix; Plan 2's migration.csv supplies the full file inventory.

## Review Focus

1. An authentication-mode switch must not discard unrelated credentials or submit hidden stale fields — Tasks 1/2/4.
2. Shrinking a dock below screenshot width must preserve independent row switches and persistent footer controls — Tasks 5–7.
3. Nested popup/sheet keyboard input must dismiss the innermost layer first and restore the right focus — Task 4.
4. Empty search results must keep search available; an empty collection can hide it — Tasks 4–7/9.
5. Screenshot calibration must not mistake preview resizing or font antialiasing for layout error — Tasks 1/11.

## File structure and visual test harness

Feature files are the migrated owner files named in Plan 2; their content changes, not their domain
service ownership. Add test-only descriptor fixtures in the corresponding plugin UI test package.
App integration renders use `AT/appearance/{SurfaceFixtures,SurfacePreview,SurfaceGeometryTest}.java`
and `AT/plugins/KitViewTestAccess.java` (test-only bridge in the owner package). Run real staged plugins
against an isolated temporary home and fake/loopback domain fixtures already used by their tests;
capture their mounted KitViews through the bridge. No production test hooks or exported fixture APIs.

`SurfaceFixtures.open(String surfaceId, String state): Opened` owns one headless fixture.
`SurfaceFixtures.Opened implements AutoCloseable` exposes `view(): KitView` and
`bounds(String id): Rectangle` from actual laid-out controls and `snapshot(): ViewSpec`.
`SurfacePreview.main(String[] args)` accepts an output directory and optional fixture filter.
Gradle `:jasper-app:themeSurfacePreview` runs it with headless=true and staged-plugin inputs;
`-Pjasper.uiScale` controls logical UI scale, while image output device scale is recorded separately.

Fixture ids: `vault-unlock`, `vault-create`, `host-add`, `fileserver-add`, `vault-manager`,
`vault-menu`, `vault-login`, `vault-key`, `sessions-panel`, `tunnels-panel`, `sftp-panel`,
`fileservers-panel`. Remaining ledger families receive stable additional ids in their owning task.
State names include `empty`, `populated`, `selected`, `focus`, `disabled`, `busy`, `error`, and
feature-specific states. Keep reference data fictional and secrets synthetic.

Reference files are listed in `docs/design/theme-engine/reference/README.md`; original local panel
image is 2810×2026. Use Plan 1 calibration.md for crop/scale anchors. Do not compare a resized chat
preview, resize the candidate arbitrarily, or overwrite source images.

## Task 1 — Vault hero dialogs and actual-feature preview harness

**Files:** Modify `P(vault)/ui/PasswordPanel.java` and existing prompt mounting owners; add `PT(vault)/ui/PasswordCompositionTest.java`, app test harness files above, `jasper-app/build.gradle.kts` preview task; update calibration.md with measured hero anchors.
**Interfaces:** Existing SDK UiView controller uses Nodes.Form HERO, hero stack, Secret fields, Strength, SettingRow and equal action group. App harness returns `Opened` with view/snapshot/bounds/close.

- [ ] Add failing descriptor/geometry tests: centered tile/title/subtitle; Unlock one password with reveal and focus; Create password+confirm, four-segment meter, device-binding setting, warning row; equal buttons. Use a meaningful guard against accidental secret descriptors:
  ```java
  @Test void secretDescriptorCannotCaptureTypedPassword() {
      var node = new dev.jasper.sdk.ui.Nodes.Secret("password",
          dev.jasper.sdk.ui.ControlState.READY, "Master password", "Master Password", "master");
      assertThat(node.toString()).doesNotContain("synthetic-secret");
      assertThat(java.util.Arrays.stream(node.getClass().getRecordComponents())
          .map(java.lang.reflect.RecordComponent::getName)).doesNotContain("value");
  }
  ```
  Pair this structural check with a live typed-secret/theme-switch/close test; a static string check alone is insufficient.
- [ ] Run `./gradlew :jasper-plugin-vault:test --tests '*Password*Test'`; verify layout expectations fail before restructuring. Add app preview harness tests and confirm a fixture opens no Window.
- [ ] Build hero composition matching unlock-vault.png/create-vault.png. Preserve 8-character/matching policy already enforced by Vault, actual bind default and async outcomes. Meter is informational; do not introduce a stricter entropy requirement. Toggle/reveal/error handling comes from kit.
- [ ] Render both references at their calibrated bounds with focus/disabled/error/busy states in calibration, dark, light and custom themes. Compare bounds/typography/icon paths separately from colors; fix shared metrics once where appropriate. Run Vault behavior tests and app SurfaceGeometryTest.
- [ ] Review artifacts and tests, then commit `Compose Vault create and unlock dialogs with canonical hero controls`.

## Task 2 — Add Host grouped form

**Files:** Modify `P(remote)/ui/HostEditor.java`, related picker/host-option descriptor builders; add `PT(remote)/ui/HostCompositionTest.java`; extend SurfaceFixtures `host-add`.
**Interfaces:** Nodes.Form FULL with Connection, Authentication, Options sections; ordered key ItemList and domain picker actions. Stable field ids survive auth/protocol/view updates.

- [ ] Test Connection's Name, Host+Port, Username and Jump Host rows; Authentication's Vault Credential/SSH Agent segments, credential picker and ordered keys; Options' Group/Favorite/Track Folder rows; SSH preview and Cancel/Add Host footer. Assert the first key cannot move up, last cannot move down, and remove/reorder retain selection by key id.
- [ ] Run `./gradlew :jasper-plugin-remote:test --tests '*Host*Test'`; confirm new composition expectations fail, preserving existing validation tests.
- [ ] Replace migration layout with section/card composition matching add-host.png. Keep actual key ordering, agent availability, picker grant flows and directory-follow behavior. Hidden irrelevant fields retain draft state for switching back but submission uses active auth mode only. Technical host/port/key/preview values use mono roles.
- [ ] Render empty/valid/picker-selected/agent/error/busy with long host and key names. Verify field alignment, footer spacing, scroll on short screens, selection after reorder, theme change during a pending picker and Enter/Esc behavior. Run focused tests and inspect overlays against reference.
- [ ] Review then commit `Compose Add Host with grouped authentication and ordered keys`.

## Task 3 — New File Server grouped form

**Files:** Modify `P(fileserver)/ui/ServerEditor.java`; add `PT(fileserver)/ui/ServerCompositionTest.java`; extend `fileserver-add` fixture.
**Interfaces:** FULL form with Server, Access, Security & Startup; protocol Choice SEGMENTED with Hue categories; path+browse/reachability+port rows; login mode and account picker; conditional settings.

- [ ] Write failing tests for HTTP/FTP/SFTP/RSYNC segments, proper domain-supported login choices, optional account explanation and protocol-specific preview. Assert Require TLS depends on supported Use TLS behavior without silently changing a stored security requirement; existing protocol validation remains authoritative.
- [ ] Run `./gradlew :jasper-plugin-fileserver:test --tests '*Server*Test'`; confirm composition/visibility expectations fail first.
- [ ] Implement cards and mixed form/settings sections matching new-file-server.png. Arrange trailing switches consistently and disable dependent settings with the source's visual indentation. Show only applicable credentials/TLS/startup controls, using real capability/model state. Bind primary enabled state to full domain validation.
- [ ] Render each protocol and login choice; test browse cancellation, a long path, invalid port, failed start/save, parent close while picker is open, short-screen scrolling and same-variant theme switch. Inspect reference overlay and run File Server tests.
- [ ] Review then commit `Compose New File Server with protocol-aware grouped controls`.

## Task 4 — Vault manager, menu and attached item sheets

**Files:** Modify `P(vault)/ui/{ManagerPanel,VaultManagerWindow,EntryEditor,EditorForm,KeyGeneratorForm,KeyImportPanel,ChangePasswordForm,GrantPanel,PickerPanel}.java`; modify `APP/windows/{AuxiliaryWindows,NativeShells,WindowOverlay,TopModal}.java` only for sheet ownership; add `PT(vault)/ui/VaultCompositionTest.java`, `AT/windows/SheetOwnershipTest.java`.
**Interfaces:** Three-column Split, semantic list rows/detail cards, window footer, Forms of kind SHEET; existing owner/close APIs retain semantics. App renders sheet beneath title bar and retains parent modality, dragging/resizing and lifetime.

- [ ] Test All Items/category counts, search/list/detail selection, selected tile/text roles, detail copy/reveal actions, and footer metadata/Done. Test anchored add menu with Login/SSH Key/Secure Note/Generate action. Add Login sheet uses identity card+authentication segments; Add SSH Key uses path+browse rows. Test owner resize centers sheet, parent closure closes it once, and popup Esc precedes sheet Esc.
- [ ] Run `./gradlew :jasper-plugin-vault:test --tests '*Manager*Test' --tests '*Editor*Test' --tests '*CompositionTest'` and `./gradlew :jasper-app:test --tests '*SheetOwnershipTest'`; observe red before re-layout.
- [ ] Implement credential-vault.png, vault-add-menu.png, add-login-sheet.png and add-ssh-key-sheet.png anatomy. Keep type identity, grants, field copy/secret reveal policy and storage behavior. Use native ownership plus kit content; parent dimming covers content without making window controls counterfeit. Generate/import/change-password/secure-note/grant/picker surfaces use corresponding unpictured canonical forms.
- [ ] Test filter-empty versus collection-empty, removed selected item, async rename/save, closing while secret is revealed, long paths/fingerprints, keyboard navigation and tab reparenting. Render all four reference states and unpictured companion forms; inspect at default and enlarged fonts in dark/light/custom themes.
- [ ] Review then commit `Compose Vault manager menus and owned item sheets`.

## Task 5 — Sessions panel and selected-host detail footer

**Files:** Modify `P(remote)/ui/{HostsPanel,HostRows,ConnectionPanel}.java`; add `PT(remote)/ui/SessionsCompositionTest.java`; extend `sessions-panel` fixture.
**Interfaces:** Panel Header title/count/primary/overflow, search, SINGLE rows with dot+name+host, DETAIL footer. Host docking/expand/collapse remains app-owned.

- [ ] Test screenshot arrangement at the panel's calibrated logical width: inline Sessions count, same-row actions, selected row, reserved footer with identity and endpoint plus Vault state/Edit/Connect. Collection-empty hides search but preserves header; filtered-empty retains it. Assert header host action does not count as a second plugin primary.
- [ ] Run `./gradlew :jasper-plugin-remote:test --tests '*HostsPanelTest' --tests '*SessionsCompositionTest'`; verify new geometry/state assertions fail before changes.
- [ ] Replace migration layout using Panel and semantic rows; preserve favorites/group behavior outside the depicted state. Keep connection state indicators and authentication prompts driven by model. The footer persists through list scrolling and changes by selected host identity, not row index.
- [ ] Render the supplied panel crop plus empty/filter-empty/disconnected/connecting/error states. Shrink width/height and assert no icon/action overlap and all actions reachable; change themes while a row and footer are focused. Run host panel/domain behavior tests.
- [ ] Review then commit `Compose Sessions with inline host rows and persistent details`.

## Task 6 — Tunnels and SFTP body/empty/footer composition

**Files:** Modify `P(remote)/ui/tunnels/{TunnelsPanel,TunnelRows,TunnelEditor,ForwardPanel}.java`, `P(remote)/ui/sftp/{SftpPanel,SftpUi,DestinationPanel}.java`; add `PT(remote)/ui/tunnels/TunnelsCompositionTest.java`, `PT(remote)/ui/sftp/SftpCompositionTest.java`.
**Interfaces:** Tunnels full-body Empty with New Tunnel; SFTP disconnected Empty with Choice/dropdown action plus persistent footer Toggle. Connected SFTP uses NAVIGATION, browser/table and the same footer.

- [ ] Write tests for empty-body centering excluding header/footer, retained header add action, SFTP chooser option dispatch and follow-folder toggle while disconnected. Test populated tunnel rows, confirmed enabled state, and connected browser navigation with no extra toolbar beyond the allowed navigation row.
- [ ] Run `./gradlew :jasper-plugin-remote:test --tests '*Tunnels*Test' --tests '*Sftp*Test' --tests '*ForwardPanelTest'`; confirm new composition assertions fail.
- [ ] Implement screenshot panel anatomy and canonical populated states. Keep follow-folder behavior, remote provenance, transfer integration and forwarding security policies. A row toggle is independently focusable and cannot also invoke row activation; failures restore confirmed state and expose an error.
- [ ] Render screenshot crops, no-session/selected-session/loading/error/populated states at default/narrow/short bounds. Verify empty→populated transitions preserve footer setting, tab order and search semantics. Run relevant Remote behavioral suites and inspect references.
- [ ] Review then commit `Compose Tunnels and SFTP with full-panel empty actions and persistent controls`.

## Task 7 — File Server rows and Remote transfer companion UI

**Files:** Modify `P(fileserver)/ui/{FileServersPanel,ServerRows,ConfirmPanel}.java`, `P(remote)/ui/transfers/{TransferStrip,TransferRows,ExistingItemsPanel,ResolvePanel,TransferUi}.java`; add FileServersCompositionTest and TransferCompositionTest in owner test packages.
**Interfaces:** COMPACT ListItem combines leading icon/title, subtitle Chip+mono endpoint, and trailing Toggle; transfer rows use progress, status, context actions and domain identity.

- [ ] Test File Servers inline count/actions, HTTP chip+address on second line, trailing switch layout and no unexpected selection activation when toggled. Add populated/empty server and transfer conflict/resume/cancel/busy tests. Type hue is derived from theme and never a literal in the plugin.
- [ ] Run `./gradlew :jasper-plugin-fileserver:test --tests '*FileServers*Test'` and `./gradlew :jasper-plugin-remote:test --tests '*Transfer*Test' --tests '*ResolvePanelTest'`; confirm new composition assertions fail first.
- [ ] Match the reference File Servers crop; create full-body empty action using the same Empty component. Refactor transfer strip/details/conflict forms with canonical rows, cards and dialogs while preserving job lifecycle, conflict choice and transfer policy.
- [ ] Test long endpoint/name, many jobs, scroll virtualization, pending start/stop and errors, action once semantics and view close during completion. Render light/dark/custom plus narrow bounds; inspect shared-row controls against gallery and reference.
- [ ] Review then commit `Compose File Server status rows and transfer companion surfaces`.

## Task 8 — Editor and remaining plugin composition

**Files:** Modify ledger standard-ui owners in `P(editor)/ui`, `P(editor)/vim/ui`, Sample, History and Snippets; test owner packages; extend SurfaceFixtures with editor-find/editor-settings/sample-settings and palette-scoped plugin rows.
**Interfaces:** Existing descriptor/controller contracts; Editor document remains Custom EDITOR. Standard toolbar/search/status/settings uses semantic kit composition only.

- [ ] Write tests for grouped Editor settings, find/replace row alignment and keyboard access, go-to-line form, unsaved/save/error prompts and compact status. Assert actual document content, undo/redo, Vim mode, selection and dirty state remain unchanged by visual updates.
- [ ] Run the existing Editor/Sample/History/Snippets UI/settings/palette suites with added composition assertions before implementation; record exact failing tests in task evidence.
- [ ] Replace temporary layouts with canonical forms/settings/inline rows; keep editor canvas and syntax behavior intact. Use kit empty states where applicable, preserve palette row density and scope interactions. Do not add fictitious History/Snippets manager windows.
- [ ] Render each changed family and state, including enlarged font and theme change during editing. Run complete affected plugin suites and style/architecture checks.
- [ ] Review then commit `Finish canonical composition for editor and remaining plugin UI`.

## Task 9 — App settings, chooser, palette, managers and notification UI

**Files:** Modify Plan 2 ledger owners in `APP/{settingsui,shortcuthelp,files,palette,themesui,pluginmanager,restart,notificationui}`; extend existing preview/tests and SurfaceFixtures.
**Interfaces:** Existing app-native KitView controllers; module tokens/metrics and domain models. No SDK leakage into these packages.

- [ ] Add grouped settings/section-row tests, chooser heading/navigation/list/detail tests, palette typography/action/selection tests, theme-import/plugin-manager dialog tests, notification action/accessibility tests. Assert Settings Apply/Cancel, keybinding editing, chooser overlay scrollbar/selection and notification severity/actions unchanged.
- [ ] Run affected tests selected from migration.csv; confirm new composition requirements fail without erasing behavioral checks.
- [ ] Replace remaining migration layouts with canonical roles. Preserve specialized chooser geometry and scroll semantics while adopting controls/type/spacing; retain settings navigation and search behavior. Empty and filtered-empty states remain distinct. Notifications/errors use semantic status hues and action hierarchy without changing deduplication or delivery.
- [ ] Render all changed families, open popups, invalid/busy/empty states and long translated-style strings. Test enlarged font and all routes during live theme switching. Inspect outputs for alignment, clipping and focus visibility, using shared references for controls not depicted as a whole screen.
- [ ] Review then commit `Finish canonical app forms browsers managers and notices`.

## Task 10 — Workspace chrome integration and retirement of per-feature styling

**Files:** Modify Plan 2 ledger owners in `APP/workspace` and visual `APP/platform` helpers; maintain theme adapters for Terminal/Buddy; tests for tabs/dock/header/status/glass.
**Interfaces:** Kit-owned activity/header/status/menu components with app-native docking/window behavior; specialized terminal/folder composition keeps its supported metrics.

- [ ] Test dock headers align with migrated plugin Panel content without duplicated title/count/actions, More and Collapse route correctly, and active rail/status controls use current tokens. Preserve exact existing tab/indicator/timing tests and window opacity routes.
- [ ] Run focused workspace/dock/tab/glass suites before changes; add a failure for each remaining duplicated/custom standard-control style path discovered in the ledger.
- [ ] Remove per-feature standard-control painting and style literals. Bind composition to kit roles while retaining approved tab widths/motion, physical split geometry and native title-bar behavior. Keep native system icons/controls platform-owned. No redesign of the center terminal is inferred from the additional panel screenshot.
- [ ] Render combined Sessions/Tunnels + terminal + SFTP/File Servers fixture at calibrated panel widths. Verify layout at 1×/2×, hidden rails, Zen mode, collapsed panels, long titles and mixed chrome/workspace themes. Inspect integrated screenshots and run existing regression suites.
- [ ] Review then commit `Unify workspace control presentation without changing terminal behavior`.

## Task 11 — Visual acceptance, cleanup and final whole-branch review

**Files:** Remove `UI/swing/MigrationLayouts.java` and all remaining named migration adapters; update migration.csv, calibration.md, `docs/design/theme-engine/acceptance.md`, module docs, all plan statuses and `docs/STATUS.md`.
**Interfaces:** Every inventory row has a final disposition and evidence; no plugin painting or raw standard-control path remains. Renderer supports the entire approved contract and all reference panels.

- [ ] Assert source/bytecode contains no remaining MigrationLayouts usage and no unsupported node dispatch branch. Cross-check all inventory entries with actual source; unpictured states get explicit render evidence instead of being silently excluded.
- [ ] Run `./gradlew :jasper-ui:gallery :jasper-app:themeSurfacePreview`, then 2× output and light/custom runs. Compare all nine original reference images by recorded crops/scale, with bounds/baseline/icon/shape evidence and separate theme-color assertions. Identify native/font-rasterization masks explicitly; never hide app geometry errors behind a global similarity threshold.
- [ ] Inspect every surface family and interaction state. Request user review of corrected visual artifacts where the reference lacks a state or a shared metric change affects approved specialized composition; continue independent checks while that review is pending. Do not auto-accept updated goldens solely because tests fail.
- [ ] Run `./gradlew check :jasper-app:installDist`; read XML totals. Run whitespace and changed-Java character-hygiene checks. Perform independent whole-branch review against approved spec, all plans, references, SDK boundary and behavior evidence; fix findings with reproducing tests and appropriate reruns.
- [ ] Write acceptance.md with reference→fixture→output→review mapping, remaining user-run native checks (sheet attachment, platform controls, fonts, physical input and accessibility), and actual test evidence. Record honestly whether those native checks are pending. Commit verified status with required trailer; no merge/push/native launch.

## Spec coverage and handoff

Spec §6 control anatomy and §8 eight original references → Tasks 1–4;
§6.1 ninth panel reference → Tasks 5–7/10; remaining surface inventory → Tasks 8–10;
live theme, accessibility, behavior and secret constraints → every owning task;
§10 visual verification and final review → Task 11.
Implementation approval and execution method follow review of the written plans. This file is a plan,
not evidence that the depicted layouts have been implemented or visually accepted.
