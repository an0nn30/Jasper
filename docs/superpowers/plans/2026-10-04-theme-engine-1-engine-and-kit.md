# Theme Engine Plan 1 — Engine and UI Kit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a standalone, tested theme engine and complete Swing kit that can express every reference while the existing application remains runnable.

**Architecture:** Extract pure theme capabilities into `jasper-ui`; separate semantic values from Swing installation. A stable descriptor tree drives one renderer, with a vendor-free SDK projection and real host/testkit implementations. Keep existing consumer entry points only until Plan 2's coordinated cutover.

**Tech Stack:** Java 25 JetBrains Runtime, Gradle wrapper, Swing, FlatLaf/FlatLaf Extras 3.7, TOML 1.1.1, JUnit 6.1.3, AssertJ 3.27.7; reuse current dependency versions.

**Spec:** [Approved design](../specs/2026-10-04-jasper-theme-engine-design.md).
**Shared contracts:** [Exact paths, types, signatures, defaults and test conventions](2026-10-04-theme-engine-contracts.md).
**Status:** Execution approved on 2026-10-04; in progress. Baseline: 8,743 tests, zero failures/errors, six skipped. Sequencing adjustments: Tasks 4–5 and 11–12 are each integrated implementation/review units because their initial APIs are mutually dependent; Task 14 supplies the single final whole-branch review. Task 1 pulls forward base ThemeToml into themeio to retain real resolver fixtures; Task 2 adds canonical semantics and serialization. GlassLevels retains its independent value/validation API; the app-specific GlassSettings resolve overload stays in the existing app pending Plan 2 adapter translation. Task 2 retains unknown top-level string metadata (other unknown types remain invalid), extracts color-only UiTemplate/UiColorKeys helpers, filters authored geometry from generated UI data, and reuses the bounded document deep snapshot for generated nested data. Task 2 verification: 40 module tests, zero failures/errors/skips; verifyUiArchitecture passed.

## Global Constraints

- “Themes supply colors. They cannot override design-system geometry or typography roles.”
- “Plugins use SDK contracts and host adapters, never the module or application internals directly.”
- “Terminal and Buddy remain independent libraries; the app adapts theme values to their APIs.”
- “The module has no dependency on app, SDK, terminal, Buddy, or plugins.”
- “Secret content never appears in an event, immutable descriptor or `toString()`.”
- Use JBR 25 and `./gradlew`; keep all checks headless. No GUI, benchmark, AOT training, merge or push.
- Existing chrome/workspace selection, theme import limits and domain behavior survive extraction.
- Use the shared contracts' fixed metrics, semantic-role names, EDT rules and commit trailer.
- Mocks govern depicted non-color detail; use original local images, not resized chat previews.
- Each task includes a red/green cycle, implementer/reviewer fix round and one coherent commit.

## Review Focus

1. A canonical parent token override must update dependent defaults, rather than leave a stale legacy-derived hover/focus color — Task 2.
2. A reused field id must preserve caret/input across theme updates while a removed secret id is erased — Tasks 6 and 7.
3. A small panel must keep its footer and trailing control reachable without overlapping text — Tasks 9 and 10.
4. Plugin shutdown during an event or async completion must release the view once and contain callbacks — Task 12.
5. A failure after FlatLaf installs must restore defaults, not merely the previous snapshot — Task 4.

## File structure

Use the exact source-root abbreviations in the shared contracts. New module files:
`jasper-ui/build.gradle.kts`, `jasper-ui/README.md`, `UI/{theme,themeio,themeimport,model,metrics,icons,swing}/package-info.java`.
Theme files move/copy by the explicit Task 1–3 lists. Model files are named in the contract appendix.
Renderer files are split by control family in Tasks 5–10, with `KitView` owning reconciliation/lifetime.
`UT/gallery/KitGallery.java` is a headless test-runtime entry point, not a shipped application.
`gradle/ui-architecture.gradle.kts` owns the module/package gate.

Each task's Interfaces block is binding on later tasks. Existing sources retained during extraction
must either delegate to the new module or be listed in Plan 2's removal ledger; never fix bugs in two
permanent copies of a generator. Keep old app behavior until its explicit migration task.

## Task 1 — Extract theme values and establish the module boundary

**Files:** Create `jasper-ui/build.gradle.kts`, `UI/theme/{TerminalColors,ThemeDocument,ThemeFileException,Seed,Token,SyntaxRole,SyntaxStyle,WorkspaceGlass,GlassLevels,ThemeResolver}.java`, `UT/theme/ThemeResolverTest.java`, `gradle/ui-architecture.gradle.kts`; modify `settings.gradle.kts`, `build.gradle.kts`, `jasper-app/build.gradle.kts`; add all initial package-info files and module README.
**Interfaces:** Consume current app classes with the same basenames. Produce the model/resolver signatures in the shared contracts; no app or terminal imports.

- [x] Copy the existing `ThemeResolverTest` and theme fixture data into the new package, retaining inheritance, same-name overrides, missing seed and cycle assertions. Add a complete defensive-copy test:
  ```java
  @Test void terminalColorsCopyTheAnsiList() {
      var colors = new java.util.ArrayList<java.awt.Color>();
      for (int i = 0; i < 16; i++) colors.add(new java.awt.Color(i, i, i));
      var value = new TerminalColors(java.awt.Color.BLACK, java.awt.Color.WHITE,
          java.awt.Color.WHITE, java.awt.Color.BLUE, colors);
      colors.clear();
      assertThat(value.ansi()).hasSize(16);
      assertThatThrownBy(() -> value.ansi().clear()).isInstanceOf(UnsupportedOperationException.class);
  }
  ```
- [x] Run `./gradlew :jasper-ui:test --tests '*ThemeResolverTest'`; first observe missing module/classes, then retain the failing test after registering the module.
- [x] Add `java-library`, `implementation("org.tomlj:tomlj:1.1.1")`, `implementation("com.formdev:flatlaf:3.7")`, `implementation("com.formdev:flatlaf-extras:3.7")`; inherit root test/toolchain conventions. Port the listed model classes without behavior changes; substitute module `TerminalColors` for terminal palette references. Add app `implementation(project(":jasper-ui"))` so distribution resolution includes the jar.
- [x] Implement `verifyUiArchitecture` using the existing jdeps/JBR pattern: ban all other Jasper modules from UI bytecode, ban Swing in theme/model/themeio, check every package edge for cycles. Wire it into UI `check`; do not let app tests depend on a circular check task.
- [x] Run `./gradlew :jasper-ui:check verifyUiArchitecture`; expect green resolver/value tests and dependency gate. Review then commit `Extract independent UI theme values and resolver` with the required trailer.

## Task 2 — Canonical colors, fallback graph and TOML round-trip

**Files:** Create `UI/theme/{SemanticColor,SemanticPalette,SemanticDefaults,ColorMath,GeneratedTheme,ThemeGenerator}.java`, `UI/themeio/{ThemeToml,ThemeTomlWriter}.java`; tests `UT/theme/{SemanticPaletteTest,ThemeGeneratorTest}.java`, `UT/themeio/ThemeTomlTest.java`.
**Interfaces:** Consume Task 1 model; produce complete `SemanticPalette`, generated values and parse/write signatures from the appendix. Move pure contrast/mix functions out of app `ChromeKeys`; no UIManager in this algorithm.

- [x] Port existing generator/TOML round-trip tests and add the complete-coverage guard:
  ```java
  @Test void incompleteSemanticPaletteIsRejected() {
      assertThatThrownBy(() -> new SemanticPalette(java.util.Map.of()))
          .isInstanceOf(IllegalArgumentException.class);
  }
  @Test void everySemanticNameRoundTrips() {
      for (var role : SemanticColor.values())
          assertThat(SemanticColor.of(role.key())).contains(role);
  }
  ```
  Add a fixture with legacy accent red and canonical accent green: focus must be green and hover must derive from green. Assert canonical `surface.card` overrides its old alias, partial legacy files resolve all keys, and a read/write/read preserves ANSI/syntax/glass/alpha.
- [x] Run `./gradlew :jasper-ui:test --tests '*Semantic*Test' --tests '*ThemeGeneratorTest' --tests '*ThemeTomlTest'`; confirm red missing semantic support.
- [x] Implement the exact token set, `[semantic]` format and precedence table from the appendix. Derive in topological order from final canonical parents. Reuse tested legacy generation for old roles; keep explicit authored colors unchanged and preserve its contrast behavior for generated values. Writers add canonical values only when present in the authored document; reading does not write files.
- [x] Add scrim/derived-alpha tests: 55% is applied once at paint, not stored twice; tile alpha is 16%, focus is 25%. Invalid/missing base values include origin in diagnostics. Run the command above plus `verifyUiArchitecture`; expect no null token or Swing model dependency.
- [x] Review then commit `Resolve canonical semantic colors and preserve legacy theme input`.

## Task 3 — Extract import conversion without weakening input checks

**Files:** Create `UI/themeimport/{AlacrittyConversion,AlacrittyTheme,ConversionResult,EditorScheme,IntellijConversion,SafeXml,ThemeArchive,ThemeConverter,ThemeLoader,ThemeParents,ThemeSources}.java`, `UI/themeimport/ImportedColors.java`; mirror existing pure conversion resources under `jasper-ui/src/main/resources/dev/jasper/ui/themeimport/`; tests `UT/themeimport/{ImportParityTest,ImportedColorsTest,ThemeArchiveTest}.java`.
**Interfaces:** Preserve `ThemeConverter.convertAll(Path)` and its existing report/failure record fields, returning module `ThemeDocument`. `ImportedColors.filter(Map<String,Object>): Result` returns supported color entries and discarded-override diagnostics; define `Result(Map<String,Object> colors,List<String> warnings)` in that class.

- [ ] Port existing conversion golden fixtures, safe-XML/archive rejection tests, duplicate parent ordering and Alacritty ANSI tests. Add this policy test:
  ```java
  @Test void geometryOverridesAreReportedAndDiscarded() {
      var result = ImportedColors.filter(java.util.Map.of(
          "Button.arc", 50, "Button.background", "#123456"));
      assertThat(result.colors()).containsEntry("Button.background", "#123456")
          .doesNotContainKey("Button.arc");
      assertThat(result.warnings()).anyMatch(s -> s.contains("Button.arc"));
  }
  ```
- [ ] Run `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.themeimport.*'`; confirm conversion extraction/policy failures.
- [ ] Port conversion logic and bundled parent resources. Retain source/path limits, symlink/zip traversal checks, XML entity rejection, wildcard/reference resolution and source provenance. Filter font/size/inset/radius/delegate data after resolving imported color references. Preserve syntax/ANSI/glass and diagnostic identity; do not treat color-like arbitrary strings as trusted delegate specifications.
- [ ] Keep `ThemeImporter`, `ThemeFiles`, `ThemeMigration` and their app filesystem publication/locking policies in the app for Plan 2. Run conversion suite and module architecture; compare color fixtures rather than old geometry blobs.
- [ ] Review then commit `Extract color-only theme conversion with existing import protections`.

## Task 4 — Fixed metrics, typography and transactional appearance binding

**Files:** Create `UI/metrics/{DesignMetrics,Typography,MotionPolicy}.java`, `UI/theme/{ThemePair,ThemeRoute,ThemeBinding}.java`, `UI/swing/{KitContext,FlatLafInstaller,AppearanceTransaction}.java`; tests `UT/metrics/DesignMetricsTest.java`, `UT/swing/AppearanceTransactionTest.java`, `UT/testsupport/{Edt,KitFixtures}.java`.
**Interfaces:** Use shared defaults and signatures. `AppearanceTransaction(ThemeBinding,Consumer<GeneratedTheme> install,Consumer<GeneratedTheme> restore)`; `apply(ThemePair): void`. Consumer callbacks are real production installer and injected failure test hooks, not a new service interface.

- [ ] Write tests asserting default fields 28, hero fields 32, label 96, gap 12, radii 6/9/12, focus 3; assert 18pt font settings enlarge content bounds without clipping. Add transaction tests where installation changes UIManager then throws: old LAF, fonts, semantic defaults and published pair are restored and subscribers receive no success event.
- [ ] Run `./gradlew :jasper-ui:test --tests '*DesignMetricsTest' --tests '*AppearanceTransactionTest'`; confirm missing metrics/binding or rollback failures.
- [ ] Implement role-based metric access and font resolution, same-variant publication and listener containment. Capture/restorable global state before install; publish only after success. Replaying subscribers receive a complete pair; close removes them. `animationsEnabled` comes from the context's supplier and can settle effects already running.
- [ ] Implement `Edt.run` and deterministic fixtures from the appendix. Tests must restore the previous global LAF in finally. Run the focused command with default scale and `-Dflatlaf.uiScale=2`; ensure the Gradle test task forwards that property explicitly.
- [ ] Review then commit `Centralize metrics typography and atomic appearance installation`.

## Task 5 — Named icons and immutable view descriptors

**Files:** Create `UI/icons/{IconRegistry,IconArtwork,IconRegistration}.java`, `UI/model/{Nodes,ControlState,Option,MenuItem,ListItem,Column,TableRow,TreeItem,ViewSpec,UiEvent}.java`; tests `UT/model/ViewSpecTest.java`, `UT/icons/IconRegistryTest.java`.
**Interfaces:** Produce the full record/enum vocabulary in the appendix. The registry signature is `icon(String name, SemanticPalette palette, Nodes.Hue hue, int logicalSize): javax.swing.Icon`.

- [ ] Write duplicate-id/invalid-reference, immutable-list, blank-label and invalid-strength tests, plus icon tint and selected-tile tests. Include all source named icons in registry enumeration. Add a secret descriptor regression:
  ```java
  @Test void secretsHaveNoValueProperty() {
      assertThat(java.util.Arrays.stream(Nodes.Secret.class.getRecordComponents())
          .map(java.lang.reflect.RecordComponent::getName))
          .doesNotContain("value", "password", "text");
  }
  ```
- [ ] Run `./gradlew :jasper-ui:test --tests '*ViewSpecTest' --tests '*IconRegistryTest'`; observe red.
- [ ] Implement immutable validating records and typed enums. Central artwork uses existing suitable outline assets or new vector paths, not raster-generated UI assets; strip authored colors from kit icons. Registration validates namespaced ids and monochrome size/stroke metadata; close removes plugin registrations. Keep licensing/attribution with reused artwork.
- [ ] Test all names render at caption/control/rail/hero sizes in light, dark and selected states. Run focused tests plus `verifyUiArchitecture`; review then commit `Define typed view vocabulary and theme-tinted icon registry`.

## Task 6 — View reconciliation, basic controls and lifecycle

**Files:** Create `UI/swing/{KitView,ViewReconciler,ControlRegistry,Buttons,TextFields,Labels,ThemePainting,KitOwnership}.java`; tests `UT/swing/{KitViewTest,ButtonsTest,TextFieldsTest}.java`.
**Interfaces:** Implement shared `KitView` API except specialized families completed by following tasks. `ViewReconciler.update(ViewSpec): void` tracks stable node ids and owns disposal; `KitOwnership` records kit provenance for diagnostics. Unsupported kinds fail explicitly during this task, not silently render placeholders.

- [ ] Write tests for text/search/port/path/textarea, five button kinds, inline adornments, label association and icon tooltips. Test stable-id update retains the same document, caret, selection and focus while changing text style; changed kind replaces only that control. A removed id's listeners close once.
- [ ] Run `./gradlew :jasper-ui:test --tests '*KitViewTest' --tests '*ButtonsTest' --tests '*TextFieldsTest'`; verify red before renderer implementation.
- [ ] Implement typed dispatch, central colors/metrics/state treatments, field validation messages, keyboard-only focus indication where available, and component reuse. PORT rejects nonnumeric edits without losing valid input and emits text for domain range validation. PATH remains mono and invokes its browse action; no filesystem access in renderer. All event callbacks pass through one containment hook.
- [ ] Test same-variant palette replacement, disabled no-hover/no-action, and no duplicate events after ten updates. Mount/close cycles leave zero active registrations/timers. Run focused tests; review then commit `Render stable kit views and basic controls with owned lifetimes`.

## Task 7 — Secret controls, choices, switches and state indicators

**Files:** Create `UI/swing/{SecretFields,SecretBuffer,Choices,Switches,Indicators}.java`; tests `UT/swing/{SecretFieldsTest,ChoicesTest,SwitchesTest}.java`.
**Interfaces:** Complete Secret/Choice/Toggle/Chip/StatusDot/Progress/Strength rendering and `KitView.withSecret/clearSecret`. `SecretBuffer extends PlainDocument` erases mutable storage on removal/close; port the Vault's tested storage approach without depending on Vault.

- [ ] Write secret tests for paired reveal, clear-on-remove/close, re-theme preservation, absence from event/spec strings, and callback failure cleanup. Complete callback test body:
  ```java
  @Test void temporarySecretCopyIsErasedAfterCallbackFailure() {
      Edt.run(() -> {
          try (var view = new KitView(KitFixtures.context(), KitFixtures.form("unlock"), e -> {})) {
              var pending = new java.util.ArrayDeque<java.awt.Component>();
              pending.add(view);
              javax.swing.JPasswordField input = null;
              while (!pending.isEmpty()) {
                  var component = pending.removeFirst();
                  if (component instanceof javax.swing.JPasswordField password) { input = password; break; }
                  if (component instanceof java.awt.Container container)
                      java.util.Collections.addAll(pending, container.getComponents());
              }
              assertThat(input).isNotNull();
              input.setText("synthetic-secret");
              char[][] captured = {null};
              assertThatThrownBy(() -> view.withSecret("password", value -> {
                  captured[0] = value;
                  throw new IllegalStateException("consumer failed");
              })).isInstanceOf(IllegalStateException.class);
              assertThat(captured[0]).hasSize(16).containsOnly((char) 0);
          }
      });
  }
  ```
- [ ] Run `./gradlew :jasper-ui:test --tests '*SecretFieldsTest' --tests '*ChoicesTest' --tests '*SwitchesTest'`; observe red.
- [ ] Implement semantic dropdown/picker/segments with keyboard selection, switches at 32×18 and compact 28×16, indicators and 0–4 meter. Toggle event reports intent; displayed confirmation comes from descriptor update. Busy prevents repeats; failure restores confirmed state and error. Animation settles immediately when policy disables it.
- [ ] Verify first/last ordered-action disabled alpha later uses the same state painter; test immutable secret-free outputs and async ownership docs. Run focused tests; review then commit `Add secure fields semantic choices switches and indicators`.

## Task 8 — Cards, forms, menus, hero and sheet content

**Files:** Create `UI/swing/{Stacks,FormRows,SettingRows,Cards,Forms,Menus,EmptyStates,SplitViews}.java`; tests `UT/swing/{FormsTest,MenusTest,EmptyStatesTest}.java`.
**Interfaces:** Complete Stack/FormRow/SettingRow/Card/Section/Empty/Form/Split/MenuButton nodes. `Forms` renders content only; app-native title bar/window ownership stays outside the module.

- [ ] Test 96px label column, 12 gap, card 14 padding, helper alignment, host+port/path+browse trailing cells, hero equal-width buttons, and source width tiers 400/480/540 before measured calibration. Test empty-state dropdown action, menu-only/split-button action separation, menu Esc/outside dismissal, no separately bordered combo arrow, and scrolling above 85% of host-provided available height.
- [ ] Run `./gradlew :jasper-ui:test --tests '*FormsTest' --tests '*MenusTest' --tests '*EmptyStatesTest'`; verify red.
- [ ] Implement source anatomy using roles, not plugin numeric hints. Validate one primary per form; Enter dispatches only when valid/not busy and does not consume textarea newline or open-menu confirmation. Esc delegates cancel after popup dismissal. Focus follows first applicable input and stable ids through conditional changes. Setting dependencies are descriptor state supplied by domain logic, not automatic destructive resets.
- [ ] Test narrow bounds and 18pt font wrapping, bottom-only sheet corners, correct focus/error ring inset clipping, and menu anchor alignment/clamping. Run focused suite; review then commit `Compose canonical forms cards menus and empty states`.

## Task 9 — Interactive lists, tables, trees and navigation

**Files:** Create `UI/swing/{ItemLists,InteractiveRows,Tables,Trees,Navigation}.java`; tests `UT/swing/{InteractiveRowsTest,TablesTest,TreesTest}.java`.
**Interfaces:** Render ItemList/ListItem/Table/Tree and semantic NAVIGATION stacks. Use stable item ids; inline controls are real focus targets while large data sets retain bounded rendering. Implement viewport-backed row component reuse rather than a permanent Swing component per item.

- [ ] Write the File Server row test: leading folder, title, HTTP chip + mono endpoint, trailing toggle; toggle click/Space emits one Toggled and zero row Action events. Test 10,000-item list component count bounded by viewport rows plus overscan, selection retained after reorder/filter, and leading-path ellipsis retains filename.
- [ ] Run `./gradlew :jasper-ui:test --tests '*InteractiveRowsTest' --tests '*TablesTest' --tests '*TreesTest'`; observe red.
- [ ] Implement row reuse with rebinding that removes old listeners and focus state. Provide ordered list index/reorder/remove/add actions, disabled impossible directions, column numeric alignment, tree expand/collapse, context actions, and list/table multi-select checkboxes. Keep file operation behavior out of kit navigation.
- [ ] Test narrow row text never paints over toggle; focused recycled control restores by item/control identity; context actions follow current selection. Test empty filtered results retain query input through parent panel composition. Run suite; review then commit `Render interactive data rows tables and trees without unbounded widgets`.

## Task 10 — Panel scaffold and the four required panel fixtures

**Files:** Create `UI/swing/{Panels,PanelHeaders,PanelFooters}.java`, `UT/gallery/PanelFixtures.java`, `UT/swing/PanelCompositionTest.java`; extend `KitFixtures.panel`.
**Interfaces:** Panel node owns header/search/body/footer content layout; `KitView.panelHeader` and `setPanelHostActions` support app placement/collapse/drag integration without exposing it to plugins. `PanelFixtures.create(String): ViewSpec` supports sessions/tunnels/sftp/fileservers.

- [ ] Write tests with a 320×480 panel: Sessions title/count stay inline; footer attaches to bottom; empty Tunnels action centers in remaining body; SFTP chooser remains available with persistent footer; File Server row reserves trailing switch. Add short 240×180, default 320×480 and enlarged-font cases with no unreachable footer.
- [ ] Run `./gradlew :jasper-ui:test --tests '*PanelCompositionTest'`; verify missing scaffold/arrangement fails.
- [ ] Implement header optional count/context/primary/overflow and compose reserved host controls. Count hides for truly empty collections; search hides only for collection-empty, not filtered-empty. Body flexes, footer stays anchored; short content scrolls instead of clipping controls. Switching body empty↔populated preserves header/footer identity.
- [ ] Make fixture actions actually update local descriptor state. Test toggle pending/failure, chooser selection, add-item transition, collapse/restore without losing state, and theme switch retaining footer toggle. Run focused tests and render all four fixtures; inspect output.
- [ ] Review then commit `Provide composable panel headers bodies footers and working fixtures`.

## Task 11 — SDK descriptors, real testkit and contract suite

**Files:** Create `SDK/ui/{Nodes,ControlState,Option,MenuItem,ListItem,Column,TableRow,TreeItem,ViewSpec,UiEvent,UiView,UiKit,CustomContent,IconAsset,SemanticColor}.java`, `FAKE/{FakeKit,FakeKitView}.java`, `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/KitContractTest.java`; update `SDK/plugin/PluginContext.java`, `FAKE/FakePluginContext.java`; tests `jasper-sdk/src/test/java/dev/jasper/sdk/ui/KitValuesTest.java`, `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeKitTest.java`.
**Interfaces:** SDK contract includes the appendix projection, semantic-color lookup and path-only icon registration; host implementation follows in Task 12. Keep `JasperSdk.VERSION` at 0.14.1 during Plan 1; new development methods are not a released compatibility promise before Plan 2.

- [ ] Mirror immutable/secret-free descriptor tests; contract tests cover create/update/action/close, same-id preservation, duplicate ids, off-EDT calls, cross-owner rejection, secret callback cleanup and contained handler failure. Fake supports inspecting spec and emitting typed events, not a no-op default implementation.
- [ ] Run `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test --tests '*Kit*Test'`; ensure both modules' filter patterns find their new tests.
- [ ] Implement factory/view interfaces in the SDK, a functioning FakeKit and FakeKitView, and `PluginContext.kit()` in every known context implementation. Avoid default methods that throw unsupported in the in-repo host/fake. Fake handles close idempotently and keep no secrets in diagnostics.
- [ ] Run `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check verifySdkArchitecture`; all contract/value tests, doclint and JDK-only gate pass. Review then commit `Define portable kit SDK contracts and functional testkit`.

## Task 12 — Host translation, containment and specialized content

**Files:** Create `APP/plugins/{HostedKit,HostedKitView,KitValues,KitContent,KitDiagnostics}.java`, tests `AT/plugins/{HostedKitTest,HostedKitContractTest,KitValuesTest}.java`; modify `APP/plugins/{HostedContext,HostedUi}.java` and plugin runtime ownership wiring.
**Interfaces:** `HostedKit implements UiKit`; owns per-plugin KitViews; `KitValues.toModule(dev.jasper.sdk.ui.ViewSpec): dev.jasper.ui.model.ViewSpec` and `toSdk(dev.jasper.ui.model.UiEvent): dev.jasper.sdk.ui.UiEvent`. Exhaustive switches cover every node/event variant.

- [ ] Run the Task 11 contract suite against HostedKit; add adversarial handler throwing during close/update, retained view after plugin stop, cross-plugin ownership, and forbidden ordinary content-tree registration. Test Editor custom content gets opacity and syntax/theme refresh while its document/caret remain intact.
- [ ] Run `./gradlew :jasper-app:test --tests '*HostedKit*Test' --tests '*KitValuesTest'`; expect red until adapter and host methods exist.
- [ ] Implement exhaustive projection, callback containment, EDT scheduling, and plugin-owned close tracking. Register approved specialized content only under matching descriptor id/kind. Diagnostics use ownership metadata, allowing kit/FlatLaf internals; plugin-native ordinary controls produce explicit diagnostics. Keep SDK types within app `plugins`.
- [ ] Check create/fail-start/stop/restart cycles leave zero registered views, icons and timers. Run focused suite plus `verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture`; review then commit `Host declarative plugin views with owned lifecycle and containment`.

## Task 13 — Calibration gallery, API examples and distribution

**Files:** Create `UT/gallery/{KitGallery,FormFixtures,StateFixtures}.java`, `UT/gallery/GalleryCoverageTest.java`, `docs/design/theme-engine/calibration.md`, `docs/ui-architecture.md`, `docs/ui-maintenance.md`; update module README, `docs/README.md`, `jasper-ui/build.gradle.kts`, `gradle/packaging.gradle` only where runtime module/jar verification needs it.
**Interfaces:** `KitGallery.main(String[] args)` writes PNGs plus bounds/state metadata under `jasper-ui/build/gallery`; Gradle `:jasper-ui:gallery` runs this test-runtime entry point headlessly. Inputs: `-Pjasper.galleryScale=1|2`, `-Pjasper.galleryTheme=dark|light|custom|calibration`.

- [ ] Write coverage tests enumerating every Nodes variant, state and the nine reference fixtures; gallery output includes all four panels and both ordinary/dropdown empty actions. Test that the jar does not contain gallery or testsupport classes and distribution includes `jasper-ui`.
- [ ] Run `./gradlew :jasper-ui:test --tests '*GalleryCoverageTest'`; confirm missing coverage fails by node/fixture name.
- [ ] Add fixtures for all eight initial mocks and panel screenshot states using the real kit. Record original crop, scale anchors and measurements per image in calibration.md; explicitly separate native chrome/font rasterization from app geometry. Render matched states with calibration colors for inspection and active-theme colors for color-routing assertions. Add compile-checked public usage examples and package dependency documentation.
- [ ] Run `./gradlew :jasper-ui:gallery -Pjasper.galleryScale=1` and again with `=2`, then light/custom fixtures; inspect every rendered sheet. Run `./gradlew :jasper-app:installDist` headlessly and verify its lib contains the module and plugin classpaths do not. Correct artifacts, not thresholds, when a component differs from the source.
- [ ] Review then commit `Add reference-calibrated kit gallery documentation and distribution checks`.

## Task 14 — Deliverable verification and final branch review

**Files:** Update this plan's status, `docs/STATUS.md`, `docs/ui-maintenance.md` with actual evidence; fix only reviewed findings in their owning files/tests.
**Interfaces:** Consumes Tasks 1–13; produces the stable API used by Plan 2 and four working panel fixtures.

- [ ] Confirm every required source component/container has a renderer and SDK projection; unsupported-kind placeholders are gone. Confirm pure theme/import extraction has a single designated authority and every temporary app path is named in Plan 2.
- [ ] Run `./gradlew check` and count XML tests/failures/errors/skips across all modules. Run `git diff --check`; run the AGENTS Java character-hygiene scan on changed Java sources. Do not claim any planned test ran before it actually did.
- [ ] Perform per-repository independent whole-branch review with the approved spec, shared contracts, reference images, test evidence and package graph. Fix actionable findings with reproducing tests and targeted reruns.
- [ ] Record native acceptance still user-run; do not launch it. Commit the verified deliverable status with the required trailer. Do not merge/push; hand off a runnable Plan 1 and request the user’s review of its visual artifacts before entering Plan 2 execution.

## Spec coverage and handoff

Spec §§4–5 → Tasks 1–4; §6 controls/containers → Tasks 5–10; §6.1 panel layouts → Tasks 9–10/13;
§7 SDK/lifecycle/secrets → Tasks 7/11–12; §§8–10 references/verification → Tasks 13–14.
Complete consumer migration and legacy removal are explicitly Plan 2; final feature composition is Plan 3.
This plan is written and self-reviewed, not executed. Review the three plans before implementation.
