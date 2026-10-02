# Theme System Plan 5: Themes Page and Live Reload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the native theme workflow: choose and preview UI/workspace themes, import and duplicate files, remove user themes safely, and reload saved theme changes automatically.

**Architecture:** `themesui` owns the Settings page, pickers, preview and page-scoped asynchronous actions. It consumes the existing `appearance` catalog/generated values, `settingsui` draft and `themeimport` file services; none of those packages depends on `themesui`. Application wiring supplies factories and platform actions. Extend the existing serialized configuration poll to observe native theme changes and publish a refresh even when config values are unchanged.

**Tech Stack:** Java/JBR 25, Gradle wrapper, Swing/FlatLaf 3.7, tomlj, JUnit/AssertJ, existing auxiliary surfaces and canned terminal transport. No new dependencies or SDK version.

**Spec:** [Approved theme design](../specs/2026-10-01-jasper-theme-system-design.md), especially §§2.3, 6.3, 7–9. Read it together with this plan, [Plan 4](2026-10-01-theme-system-4-conversion-migration.md), and [STATUS](../../STATUS.md).

**Status:** Written and self-reviewed 2026-10-01 against local `main@cc0429f5`. Plan 4 and the newer hidden-rail terminal framing fix are merged locally. Fresh merged `./gradlew check`: **7,931 tests, zero failures/errors, six skips** (1m33s; `/tmp/moray-plan4-main-check.log`). No push or GUI launch. This plan is awaiting user review; implementation has not started.

**Workspace / execution:** Reuse `.worktrees/theme-system` on `codex/theme-system-plan5`, based on the verified merge. Preserve the user's inline execution preference if approved: TDD and one commit per task, followed by one independent whole-branch review and the executing-plans fix workflow. Do not start implementation merely because this document exists. Record departures in this banner and STATUS.

## Global Constraints

- “Choices are staged in the draft and apply on Apply or OK.” Only theme selection is staged; explicitly confirmed file operations happen immediately and survive Cancel. Show that distinction beside the file actions.
- “The workspace picker's first entry is \"Same as UI theme\" (`match`).” Store canonical `ui.theme.name` and `ui.theme.workspace` strings through the shared SettingsDraft; do not revive `terminal.colors` or `ui.theme.terminal`.
- “Each picker is a searchable list with All / Dark / Light and Bundled / Mine filters”; every row shows background, accent and four ANSI colors. Include an All source choice to clear Bundled/Mine filtering.
- “The app cannot use the Editor plugin's RSyntaxTextArea”; preview syntax uses a fixed pre-tokenised JTextPane. No app import of Editor or SDK types outside existing allowed boundaries.
- “No interface without two real implementations.” Use concrete classes, records and JDK functional parameters for desktop/test seams.
- “No network theme sources of any kind.” Import is local and data-only; About reads the packaged notices. Do not restore Marketplace, old stores, or runtime archive discovery.
- Native files remain capped at **256 KiB**. Reuse Plan 4 path validation, per-home lock, expected fingerprints and no-clobber publication. Import retains its existing archive caps and per-provider failure handling.
- One application LAF. Preview must never call ThemeManager.install, ThemeController.configure, UIManager.setLookAndFeel or mutate global UIDefaults. Use ThemeManager.generated on the EDT for the same effective values used by the app.
- Filesystem reads, conversion, hash scans, Desktop calls and publication run off EDT. Swing, SettingsDraft, preview generation and auxiliary surface management run on EDT. No blocking future.get/join on EDT.
- ConfigService retains its existing **one-second serialized poll**, close/revision guards, and forced reload behavior. No second watcher thread or independent configuration writer.
- Do not launch a GUI, shell, benchmark or native trash operation in automated tests. Preview uses SampleTerminal's existing attached fake transport. User-only visual checks appear at the end.
- Use `./gradlew`, never system Gradle; all tests headless. Keep four architecture guards, doclint, source hygiene and packaged-resource checks. End commits with `Co-Authored-By: Codex <noreply@openai.com>`.
- Preserve Plan 2 missing-theme behavior: missing chrome uses the last applied darkness's safe Jasper theme; missing workspace follows chrome; requested names remain saved and recover when repaired. File removal never silently rewrites configuration.

## Review Focus

1. An editor replaces a parent with equal-length bytes and a preserved timestamp; descendants and every open window must refresh without touching config.toml. Task 1 pins this.
2. Settings has a dirty workspace choice when its source is removed, repaired, or externally replaced; the draft must survive catalog refresh and Apply must retain ConfigService's stale-setting protection. Tasks 3 and 6 pin this.
3. An import finishes after the page closes, or a second click attempts the same operation; no late dialogs, disposed-component updates or duplicate writes. Task 5 pins this.
4. A user theme changes or becomes a symlink between removal confirmation and execution; no replacement/foreign file may be deleted, and a failed trash attempt never silently becomes permanent deletion. Task 2 pins this.
5. A converted author-specific theme has UI overrides and syntax flags under mismatched chrome/workspace; preview must use effective values without changing the running app or retaining a terminal session after disposal. Task 4 pins this.

---

## Agreed UX and implementation decisions

- Page ID `themes`, title **Themes**, parent `appearance-behavior`, immediately after Appearance. Two labeled, independently searchable pickers (**UI theme**, **Workspace theme**) above one miniature preview. Retain `appearance` as the default Settings page and its interface-font controls; replace both old theme editor rows with links to `themes#ui` / `themes#workspace`.
- Each picker owns its query and filters; filtering never selects the first result or stages a change. `match` remains a pinned first workspace row under every filter. A missing requested name stays visible in a separate selection-status label, with an explanatory warning; it is never replaced in the draft by the preview fallback.
- The active picker determines Duplicate/Remove's target. `match` and missing/invalid selections disable these actions. Duplicate is available for bundled and user themes; Remove only for canonical regular user files. Diagnostics also list invalid files that have no selectable theme, with filename and positive line number when known.
- File operations are immediate. Copy beside buttons: **“File changes happen immediately. Theme choices apply with Apply or OK.”** Successful Import leaves the selection unchanged; Duplicate stages the new name only in the picker that initiated it. Cancel does not undo written/imported/removed files. Removing a selected theme leaves its requested name intact and displays fallback diagnostics.
- Import opens the existing owner-aware local file chooser. Its title lists `.theme.json`, `.jar`, `.zip`, `.toml`; the chooser has no extension-filter field today, so validate the chosen suffix before work and give a plain-text error for other types. No new general file-chooser API is required.
- A multi-provider import shows every successful provider, warnings and failures. Conflicting user names offer **Replace / Keep both / Skip**, default **Skip**. Nonconflicting providers default **Keep both**. A bundled-name override is explicitly labeled. Cancel before commit writes nothing; commit uses stable Item IDs, never list indices or names.
- Duplicate proposes `<original> Copy`, adding ` 2`, ` 3` against all catalog names. The submitted name is stripped, nonblank, not case-insensitive `match`, and unique ignoring case/outer whitespace; reject collisions without silently choosing another display name. The filesystem ID still uses ThemeFiles.fileId and collision-safe filenames.
- Removal uses system trash when supported. If unavailable or unsuccessful, show a separate **Delete permanently** confirmation before attempting permanent removal. Both attempts revalidate the same frozen removal intent; changing the file invalidates it. Cancel, permission failure and trash failure leave a visible report and never imply success.
- About opens packaged `THIRD-PARTY-THEMES.md` as plain text in an owned, scrollable auxiliary dialog. No web request, external temp file or network browser dependency.

## File map and ownership

| Files | Responsibility |
|---|---|
| `config/ConfigService.java`, `appearance/ThemeCatalog.java`, `application/JasperApplication.java` | Serialized theme polling, content-aware catalog identity, application publication |
| `themeimport/ThemeDuplicate.java`, `themeimport/ThemeFiles.java` | Inherited duplicate text, validated creation, frozen removal intent and safe removal |
| `themesui/ThemeChoices.java`, `ThemePicker.java`, `ThemesPage.java` | Immutable filtering, widgets, draft binding, diagnostics, page lifetime |
| `themesui/ThemePreview.java`, `SyntaxSample.java`, existing `SampleTerminal.java` | Local chrome/workspace rendering, syntax spans, session cleanup |
| `themesui/ThemeActions.java`, `ThemeImportView.java`, `ThemeDialogs.java`, `ThemeNotices.java` | Async workflow, import decisions/report, owned prompts, local notices |
| `settingsui/CorePages.java`, `Editors.java` | Supplied page factory, links and retired theme-only editors |
| `workspace/WindowChrome.java`, `WindowContent.java`, `WindowCommands.java`, `application/ConfigurationController.java` | Native catalog View menus, canonical writes and registration lifetimes |
| `config/CoreSettings.java`, package-info files, docs and corresponding tests | Updated help, dependency declarations, acceptance and handoff |

All production paths in the map are under `jasper-app/src/main/java/dev/jasper/app/`. Tests mirror owner packages under `jasper-app/src/test/java/dev/jasper/app/`. Helpers remain test-only; do not introduce production getters solely for tests. Existing package-private state/component access is appropriate in owner-package tests.

### Task 1: Reload native themes through the configuration worker

**Files:** Modify `ConfigService.java`, `ThemeCatalog.java`, `JasperApplication.java`; tests `config/ConfigServiceTest.java`, `appearance/NativeThemeCatalogTest.java`, `workspace/InstalledThemesTest.java`, `application/JasperApplicationThemesTest.java`.

**Interfaces:**
- Consumes: `ThemeCatalog.scan(): Snapshot`, `latest(): Snapshot`; `ConfigService.watchWith(Runnable): CompletableFuture<Void>`; `ConfigurationController.accept(ConfigService.State)` and its current missing-theme behavior.
- Produces: `ConfigService.watchChanges(BooleanSupplier companion): CompletableFuture<Void>`. Calls once on registration and once per scheduled/forced refresh on its worker; true means companion state changed and the existing State must be republished even if config is equal. Keep watchWith's current documented read-triggered contract for other callers/tests; do not create an ambiguous overload.
- Produces: ThemeCatalog.scan detects changed content even when size/mtime are identical, rebuilds affected descendants, preserves Theme object identity for unchanged effective themes, and continues returning immutable snapshots.

- [ ] **Step 1: Add failing regression tests.** Use the existing injected scheduler/publisher in ConfigServiceTest to run one scheduled poll deterministically (no sleeps). Add tests named `companionChangePublishesUnchangedConfigOnce`, `unchangedCompanionDoesNotPublish`, `throwingCompanionRetriesNextPoll`, and `closeDropsQueuedCompanionPublication`. Their assertions include:

```java
@Test void companionChangePublishesUnchangedConfigOnce() throws Exception {
    Path file = directory.resolve("config.toml");
    Files.writeString(file, "# unchanged config\n");
    String before = Files.readString(file);
    var worker = new PollWorker();
    var published = new ArrayList<ConfigService.State>();
    var changed = new AtomicBoolean();
    try (var service = new ConfigService(file, true, worker, Runnable::run)) {
        service.watchChanges(() -> {
            assertThat(SwingUtilities.isEventDispatchThread()).isFalse();
            return changed.getAndSet(false);
        }).get(5, TimeUnit.SECONDS);
        service.start(published::add);
        changed.set(true);
        worker.poll();
        assertThat(published).hasSize(2);
        assertThat(published.get(1)).isEqualTo(published.get(0));
        worker.poll();
        assertThat(published).hasSize(2);
        assertThat(Files.readString(file)).isEqualTo(before);
    }
}
```

Add a native catalog test using a temporary `themes/base.toml` and `child.toml`; child extends Base. The body of the content-identity regression is:

```java
Path base = home.resolve("themes/base.toml");
Files.createDirectories(base.getParent());
Files.writeString(base, "name='Base'\nextends='Jasper Dark'\n[colors]\nbackground='#112233'\n");
Files.writeString(base.resolveSibling("child.toml"), "name='Child'\nextends='Base'\n");
ThemeCatalog catalog = ThemeCatalog.beside(home.resolve("config.toml"));
Theme before = catalog.scan().find("Child").orElseThrow();
var stamp = Files.getLastModifiedTime(base);
Files.writeString(base, Files.readString(base).replace("#112233", "#445566"));
Files.setLastModifiedTime(base, stamp);
Theme after = catalog.scan().find("Child").orElseThrow();
assertThat(after).isNotSameAs(before);
assertThat(after.source().document().seeds()).containsEntry(Seed.BACKGROUND, new Color(0x445566));
assertThat(catalog.scan().find("Child").orElseThrow()).isSameAs(after);
```

Extend existing real WindowContent fixtures: edit/delete/recreate a selected native workspace without changing config; assert every retained owner and plugin appearance observer updates once per changed effective theme, while duplicate unchanged polls cause no reinstall. Also test malformed→repaired file diagnostics and directory delete/recreate. Keep selection strings unchanged throughout.

- [ ] **Step 2: Run RED.**

```bash
./gradlew :jasper-app:test --tests '*ConfigServiceTest' --tests '*NativeThemeCatalogTest' --tests '*InstalledThemesTest' --tests '*JasperApplicationThemesTest'
```

Expected: missing watchChanges API and/or no publication for a theme-only change; equal-metadata content is currently cached.

- [ ] **Step 3: Implement the interfaces.** Store the BooleanSupplier separately from the old Runnable companion. In refresh, read config, run each applicable companion once, then publish if forced, config changed, or watchChanges returned true. Catch a companion failure outside the scheduled executor's lifecycle, log and retry; never interpret failure as successful change. Registration returns a future and obeys existing close/pending guards. Startup still waits off EDT for the first catalog scan before constructing the configuration controller.

Change the catalog cache from metadata-only invalidation to bounded content fingerprints: each regular native file is read at most MAX_THEME_FILE+1 per scan; hash/read the same bytes once, then reuse its parsed document when unchanged. Include presence and read/parse diagnostics in snapshot comparison. Do not make name-order/cache identity depend on directory iteration order. Preserve existing per-file errors and fallback resolution; a missing directory is recreated by existing scan behavior. Ignore legacy directories, lock/stage files and non-TOML files. Reuse unchanged resolved Theme instances; a changed inherited parent invalidates descendants even when child bytes match.

In `JasperApplication.scanThemes`, register watchChanges. Capture previous `themeCatalog.latest()` and migrationProblems, scan/update them on the worker, then return whether either changed. No migration rerun or repeated config parsing is needed for theme-only updates. ConfigurationController's existing accept path handles repaint, settings refresh and plugin appearance notifications; retain its installation-failure fallback.

- [ ] **Step 4: Run GREEN.** Repeat Step 2; then `./gradlew verifyApplicationArchitecture`. All pass. Prove the test observes the scheduled poll, not only explicit reload.
- [ ] **Step 5: Commit.**

```bash
git add jasper-app/src
git commit -m "feat(themes): reload native changes without config edits" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

### Task 2: Add safe Duplicate and Remove primitives

**Files:** Create `themeimport/ThemeDuplicate.java`; modify `ThemeFiles.java`; create tests `themeimport/ThemeDuplicateTest.java`, `ThemeRemovalTest.java`; extend `ThemeFilesTest.java`.

**Interfaces:**
- Consumes: `Theme`, `GeneratedTheme`, `ThemeFiles.fingerprint(Path)`, existing per-home lock/publication, `ThemeCatalog.beside(Path)`, `ThemeToml.parse`, `ThemeResolver.resolve`.
- Produces: `public static String ThemeDuplicate.text(Theme original, GeneratedTheme effective, String name)` (pure).
- Produces on ThemeFiles: `public Path duplicate(Theme original, GeneratedTheme effective, String name) throws IOException`; `public Removal prepareRemoval(Path target) throws IOException`; `public boolean trash(Removal intent, Predicate<Path> moveToTrash) throws IOException`; `public void delete(Removal intent) throws IOException`.
- Produces: `public record ThemeFiles.Removal(Path path, String sha256, String fileKey)`. This is an expected-state value, never authority by itself: each operation validates canonical direct-child `.toml`, regular/no symlink, matching hash and stable identity under the shared lock.

- [ ] **Step 1: Write failing tests.** Generate effective values on EDT using ThemeManager.generated, then perform file calls on the test worker. For every Seed and Token require exactly one commented assignment; include all 16 ANSI entries as commented normal/bright tables. Verify active values contain only name and extends, inheritance resolves to the original and Unicode/quotes/newlines in names round-trip. Core assertions:

```java
String text = ThemeDuplicate.text(Theme.DARK, effectiveDark, "My \"Night\" Theme");
ThemeDocument copy = ThemeToml.parse(text, "copy.toml");
assertThat(copy.name()).isEqualTo("My \"Night\" Theme");
assertThat(copy.parent()).contains("Jasper Dark");
assertThat(copy.seeds()).isEmpty();
assertThat(copy.tokens()).isEmpty();
for (Seed seed : Seed.values()) assertThat(text).contains("# " + seed.key() + " = \"");
for (Token token : Token.values()) assertThat(text).contains("# " + token.key() + " = \"");
assertThat(ThemeResolver.resolve(copy, name -> List.of(Theme.DARK.source().document())).seeds())
    .isEqualTo(Theme.DARK.source().document().seeds());
```

Add tests `duplicateRejectsTakenDisplayNameAndChangedParent`, `duplicateNeverOverwritesOccupiedFilename`, `removalRejectsBundledOutsideAndSymlinkTargets`, `removalRechecksHashAndFileIdentity`, `failedTrashDoesNotDelete`, `explicitDeleteUsesSameFrozenIntent`. A fake trash predicate records its argument and moves to a temp trash directory; never invoke the real desktop. After preparing intent, swap in equal bytes under a different inode and assert refusal as well as the ordinary changed-byte case. Assert EDT calls fail consistently with existing ThemeFiles methods.

- [ ] **Step 2: Run RED.**

```bash
./gradlew :jasper-app:test --tests '*ThemeDuplicateTest' --tests '*ThemeRemovalTest' --tests '*ThemeFilesTest'
```

Expected: new APIs missing; existing create cannot publish an unresolved inherited template.

- [ ] **Step 3: Implement safe file operations.** ThemeDuplicate emits a header from ThemeTomlWriter (use its escaping via a minimal ThemeDocument), followed by commented `[colors]`, `[colors.normal]`, `[colors.bright]` and `[tokens]` sections. Comment every table header as well as its assignments. Seeds use effective terminal background/foreground/cursor/selection and effective ACCENT; token comments use effective.tokens, ANSI comments use effective.terminal. No syntax/UI/icon flattening: the active extends preserves these. Do not activate commented defaults.

`duplicate` locks, rescans, refuses any display-name collision (including bundled), verifies the original still resolves to the supplied source/document, parses the candidate text, resolves it against current catalog documents, enforces the unchanged size cap, then uses the existing no-clobber publisher and safe filename allocation. This dedicated path allows validated inheritance without weakening `create`/`replace`'s standalone import contract. Check source identity/document consistency again immediately before publication; a removed/changed parent requires retry.

`prepareRemoval` returns a frozen fingerprint/identity. `trash` validates it immediately before invoking the supplied desktop function under the lock; returns false if unsupported/unsuccessful and verifies the source is gone before reporting success. It never calls delete. `delete` is only the explicit-confirmation path and rechecks the same intent before removal. Keep the documented Java filesystem compare/delete race limitation; reject every observed symlink/path swap and never follow links. No config writes or automatic name substitution.

- [ ] **Step 4: Run GREEN.** Repeat Step 2 and existing `*ThemeImporterTest`, `*ThemeMigrationTest`; all pass with original import/migration semantics intact.
- [ ] **Step 5: Commit.**

```bash
git add jasper-app/src
git commit -m "feat(themes): add safe inherited copies and removal" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

### Task 3: Build searchable pickers and bind the shared draft

**Files:** Create `themesui/ThemeChoices.java`, `ThemePicker.java`, `ThemesPage.java`; modify `themesui/package-info.java`; create tests `themesui/ThemeChoicesTest.java`, `ThemesPageTest.java`.

**Interfaces:**
- Consumes: SettingsPage, PageContext, SettingsDraft.value/stage/reset/onChanged, CoreSettings.UI_THEME_NAME/UI_THEME_WORKSPACE (both Setting<String>), ThemeCatalog.Snapshot, ThemeManager.generated, lifecycle.Subscription.
- Produces: `public final class ThemesPage implements SettingsPage`, `public static final String ID = "themes"`, constructor `ThemesPage(PageContext context, Supplier<ThemeCatalog.Snapshot> catalog, Supplier<TerminalOptions> terminalOptions)`. No production file I/O in this constructor. Task 5 extends this constructor with the concrete ThemeActions collaborator.
- Produces package-private ThemeChoices enums `Tone { ALL, DARK, LIGHT }`, `Source { ALL, BUNDLED, MINE }` and `static List<Theme> filter(ThemeCatalog.Snapshot catalog, String query, Tone tone, Source source)`.
- Produces package-private ThemePicker with `ThemePicker(boolean workspace, Consumer<String> selected)`, `void update(ThemeCatalog.Snapshot catalog, String requested)`, `String requested()`, `void reveal()`. Owns search/filter widgets and a JList; its root is a JPanel. A row's identity uses Theme source/id; callback carries the canonical display name or `match`.

- [ ] **Step 1: Write failing tests.** Filter a snapshot containing a bundled light theme, bundled dark theme and a user dark theme; combine case-insensitive substring query with both filter axes and assert deterministic case-insensitive name order. For a user override of a bundled name, only the catalog winner appears and is Mine. Test empty-result text, keyboard selection and name strings starting `<html>` displayed literally.

Create PageContext and SettingsDraft from ConfigLoader.parse; run widget work on EDT. Tests stage UI and workspace independently and assert:

```java
draft.stage(CoreSettings.UI_THEME_NAME, Optional.of("Jasper Light"));
draft.stage(CoreSettings.UI_THEME_WORKSPACE, Optional.of("Dracula"));
page.refresh();
assertThat(draft.value(CoreSettings.UI_THEME_NAME)).isEqualTo("Jasper Light");
assertThat(draft.value(CoreSettings.UI_THEME_WORKSPACE)).isEqualTo("Dracula");
assertThat(draft.edit().changes()).hasSize(2);
assertThat(page.apply()).isCompleted(); // SettingsDialog saves the shared draft
page.reset();
assertThat(draft.modified(CoreSettings.UI_THEME_NAME)).isFalse();
assertThat(draft.modified(CoreSettings.UI_THEME_WORKSPACE)).isFalse();
```

Drive actual list selection as well; assert it produces those edits, while filtering produces none. Refresh after deleting the staged theme: requested name and draft edit remain, the missing status appears, and a repair restores the row. `match` is first and available under every filter. Invalid files without a Theme still appear in diagnostics with filename/line. Repeated refresh must preserve filters, caret and scroll selection; dispose twice closes draft listeners once.

- [ ] **Step 2: Run RED.**

```bash
./gradlew :jasper-app:test --tests '*ThemeChoicesTest' --tests '*ThemesPageTest'
```

Expected: page/filter/picker classes missing.

- [ ] **Step 3: Implement the page and widgets.** Use JList single selection with a renderer that shows plain text plus six swatches: background, accent, ANSI red/green/blue/yellow (indices 1/2/4/3). Derive the row's effective colors on EDT, cache only current rows/themes, and do not generate every theme repeatedly from paint. Set each list to seven visible rows in its own scroll pane; place the page body in a scrollable container so file actions and preview remain reachable in the existing Settings window. Search is a separate JTextField per picker; filter selectors are keyboard accessible and have labels/accessible names. Rebuilding models suppresses selection callbacks; selecting a row by keyboard/mouse stages precisely one setting. Match is a distinct row, never a fake user Theme.

ThemesPage owns both controls, a diagnostics area and a reserved preview region for Task 4. `modified()` returns false and `apply()` returns completedFuture(null): the shared draft owns pending changes and saves exactly once. `reset()` calls draft.reset for these two settings only. `valid()` permits existing missing names because fallback is supported; parse/install warnings are visible rather than silently rewriting selections. `refresh()` reads only supplier.latest data and draft values. `reveal("ui"|"workspace")` focuses the corresponding picker; unknown fragments ignored. `helpAnchor()` is `theme`.

`searchTerms()` includes exactly the requested keywords plus the two labels: theme, colour scheme, palette, alacritty, intellij, marketplace, syntax, workspace. `highlight` updates matching labels without changing list queries. Implement lazy component creation and idempotent dispose, releasing subscriptions even if component was never requested. Dependencies of themesui become appearance, config, settingsui, lifecycle and terminal config/session/view; later tasks add themeimport/windows. No settingsui→themesui dependency.

- [ ] **Step 4: Run GREEN.** Repeat Step 2, then `./gradlew verifyApplicationArchitecture`; pass. The page remains unregistered until Task 6 supplies complete actions/preview.
- [ ] **Step 5: Commit.**

```bash
git add jasper-app/src
git commit -m "feat(themes): add searchable staged theme pickers" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

### Task 4: Render a local two-zone preview and release its resources

**Files:** Create `themesui/ThemePreview.java`, `SyntaxSample.java`; modify `ThemesPage.java`, `SampleTerminal.java` only if needed for deterministic cleanup; tests `themesui/ThemePreviewTest.java`, `SyntaxSampleTest.java`, `SampleTerminalTest.java`.

**Interfaces:**
- Consumes: `ThemeManager.generated(Theme): GeneratedTheme` on EDT, `ResolvedTheme(GeneratedTheme chrome, GeneratedTheme workspace)`, `SampleTerminal(TerminalOptions)`, palette/setPalette behavior, close; Token/SyntaxRole/SyntaxStyle enums.
- Produces: package-private `ThemePreview(TerminalOptions options) implements AutoCloseable`, `JComponent component()`, `void show(ResolvedTheme themes)`, `void close()`.
- Produces: package-private `SyntaxSample extends JTextPane`, `SyntaxSample()`, `void theme(GeneratedTheme workspace)`; a fixed source string and immutable `Span(int start, int end, SyntaxRole role)` table in the same file.

- [ ] **Step 1: Write failing tests.** Build preview on EDT with UIManager's current LAF/defaults captured. Render it to a BufferedImage headlessly, choosing IntelliJ Light chrome + Dracula workspace, then reverse. Assert sampled interior pixels of named preview regions and syntax document attributes match effective generated values; choose interior coordinates, not font-antialiased edges. Include flags from a fixture whose keyword is both bold and italic.

```java
var beforeLaf = UIManager.getLookAndFeel();
Object beforePanel = UIManager.get("Panel.background");
preview.show(new ResolvedTheme(light, dark));
assertThat(UIManager.getLookAndFeel()).isSameAs(beforeLaf);
assertThat(UIManager.get("Panel.background")).isEqualTo(beforePanel);
assertThat(sample.palette()).isEqualTo(dark.terminal());
assertThat(StyleConstants.isBold(keywordAttributes)).isTrue();
assertThat(StyleConstants.isItalic(keywordAttributes)).isTrue();
preview.close();
preview.close();
assertThat(sample.closed()).isTrue();
```

Also assert a `match` preview passes the same GeneratedTheme instance to both zones; staged preview changes do not emit ThemeController subscriptions or change its active choice. Cover an author-adjusted converted theme using the same effective values tested by BundledWorkspaceParityTest. Canned output becomes visible via the existing session test Await helper; no shell/PTY.

- [ ] **Step 2: Run RED.**

```bash
./gradlew :jasper-app:test --tests '*ThemePreviewTest' --tests '*SyntaxSampleTest' --tests '*SampleTerminalTest'
```

Expected: missing preview/snippet implementation.

- [ ] **Step 3: Implement the miniature.** Use locally painted chrome strips and labels: title/search, short sidebar list, status bar; workspace tab strip above the canned terminal and snippet. Token mapping: chrome TITLE_BAR/SIDEBAR/STATUS_BAR/TEXT/BORDER/INPUT_BACKGROUND; workspace TAB_BAR/TAB_ACTIVE/TAB_ACTIVE_TEXT/EDITOR_BACKGROUND/EDITOR_FOREGROUND/EDITOR_SELECTION. The noninteractive fake search field may be painted so it cannot inherit ambient LAF colors. Real JTextPane is read-only/nonfocusable, styled explicitly from SyntaxStyle color/bold/italic; reset all attributes on every theme switch so flags never leak. Use this exact fixed snippet and spans (end offsets exclusive); the default role for all remaining text is VARIABLE. Test each span against its substring and the styled document, so edits to the literal cannot silently misalign colors.

```java
static final String TEXT = "// Theme preview\nclass Sample {\n    String greet(int count) {\n        return \"Hello\" + 42;\n    }\n}\n";
static final List<Span> SPANS = List.of(
    new Span(0, 16, SyntaxRole.COMMENT),
    new Span(17, 22, SyntaxRole.KEYWORD),
    new Span(23, 29, SyntaxRole.TYPE),
    new Span(36, 42, SyntaxRole.TYPE),
    new Span(43, 48, SyntaxRole.FUNCTION),
    new Span(49, 52, SyntaxRole.KEYWORD),
    new Span(70, 76, SyntaxRole.KEYWORD),
    new Span(77, 84, SyntaxRole.STRING),
    new Span(85, 86, SyntaxRole.OPERATOR),
    new Span(87, 89, SyntaxRole.NUMBER));
```

Set component names `theme-preview-title`, `theme-preview-sidebar`, `theme-preview-status`, `theme-preview-tabs`, and `theme-preview-editor` for accessible diagnostics and headless component lookup.

ThemePreview constructs one SampleTerminal lazily when shown, updates only its palette, and closes it from ThemesPage.dispose. No repeated sessions per choice, file operation or paint. Preview resolves staged names through the latest catalog: unknown UI uses the same safe last-preview darkness fallback; unknown workspace follows preview chrome. Explain fallback beside the missing selection and leave draft unchanged. Use the current terminal options supplier for the sample's typography; this plan does not add a live font editor.

- [ ] **Step 4: Run GREEN.** Repeat Step 2 and `*ThemesPageTest`; then verify app architecture. Every preview assertion passes without any global look-and-feel mutation.
- [ ] **Step 5: Commit.**

```bash
git add jasper-app/src
git commit -m "feat(themes): preview staged chrome and workspace locally" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

### Task 5: Connect Import, Duplicate, Remove, folder and notices actions

**Files:** Create `themesui/ThemeActions.java`, `ThemeImportView.java`, `ThemeDialogs.java`, `ThemeNotices.java`; modify `ThemesPage.java`, `package-info.java`; tests `ThemeActionsTest.java`, `ThemeImportViewTest.java`, `ThemeNoticesTest.java`, extend `ThemesPageTest.java`. Add public safe directory accessor `ThemeFiles.ensureDirectory(): Path throws IOException` delegating to its locked existing directory checks.

**Interfaces:**
- Consumes: ThemeImporter.plan(Path): ImportPlan; commit(ImportPlan, Map<String,ConflictChoice>): ImportReport; stable Item.id, optional target/hash, shadowsBundled; Task 2 file APIs; PageContext.chooseFile/surface; AuxiliaryWindows.dialog(String, boolean, AuxiliarySurface).
- Produces `public final class ThemeActions implements AutoCloseable` with constructor `(PageContext context, Path home, AuxiliaryWindows windows, Supplier<CompletableFuture<Void>> refresh, Consumer<Path> openFolder, Predicate<Path> trash, ExecutorService worker)`. Owns supplied single-thread worker and shuts it down; application creates one daemon `jasper-theme-actions` worker per Settings opening, not one per app/window. Desktop functions run only on worker.
- Produces methods on ThemeActions: `void importThemes(Consumer<ThemeImporter.ImportReport> report)`, `void duplicate(Theme original, GeneratedTheme effective, Consumer<String> created)`, `void remove(Theme original, Consumer<String> result)`, `void openFolder(Consumer<String> failure)`, `void about()`, `boolean busy()`, `void close()`; all entry points/callbacks EDT-only. Each operation takes a lifetime ticket checked after every async/modal boundary. Context.changed signals busy transitions.
- Produces ThemeImportView `(ThemeImporter.ImportPlan plan, Consumer<Map<String,ThemeImporter.ConflictChoice>> commit, Runnable cancel)`; owns rows/decisions but performs no I/O. ThemeDialogs owns concrete name/confirmation dialogs, not a new interface. ThemeNotices exposes `static String read() throws IOException` for the packaged text and the controller displays it in a plain JTextArea.
- Final ThemesPage constructor is `(PageContext, Supplier<ThemeCatalog.Snapshot>, Supplier<TerminalOptions>, ThemeActions)`. Page owns/closes actions. No placeholder/no-op actions in production registration.

- [ ] **Step 1: Write failing tests.** Use real temporary themes/importer and a controllable ExecutorService plus headless AuxiliaryWindows Shells, patterned after PluginManagerTest. Tests click actual view buttons and complete queued worker tasks explicitly. Required assertions:

```java
assertThat(view.choices().get(conflictingItem.id())).isEqualTo(ThemeImporter.ConflictChoice.SKIP);
assertThat(view.choices().get(newItem.id())).isEqualTo(ThemeImporter.ConflictChoice.KEEP_BOTH);
assertThat(savedConfigBytes).isEqualTo(initialConfigBytes);
assertThat(report.imported()).extracting(ThemeImporter.Imported::name).contains("Example 2");
assertThat(report.warnings()).anyMatch(message -> message.contains("console"));
assertThat(report.failures()).isNotEmpty();
```

Define package-private `choices()` on ThemeImportView for its owner tests. Cover Replace, Keep both, Skip, canceled plan, changed target after confirmation, two providers with the same display name, one bad provider among good providers and a wholly invalid source. Failure reports list only actual committed files; a write failure must not appear imported. Text is literal, never Swing HTML.

Lifetime tests: double-click while busy invokes one commit; close before planning finishes opens no dialog and writes nothing; close after commit starts allows an already committed operation to finish/refresh application state but invokes no page callback/dialog; queued unstarted operations are canceled. An explicit user commit may complete after closing; report only through an existing application refresh, not a resurrected page. Verify all worker errors reset busy on a live page.

Duplicate prompts default name, rejects a collision and stages only the initiating picker after successful publication. Remove rejects match/bundled, successful trash does not prompt permanent deletion, false trash prompts separately, cancel retains file, edited intent refuses both attempts. About must read the packaged JAR resource; folder opening creates/reveals the actual config override home's themes folder, not the default home.

- [ ] **Step 2: Run RED.**

```bash
./gradlew :jasper-app:test --tests '*ThemeActionsTest' --tests '*ThemeImportViewTest' --tests '*ThemeNoticesTest' --tests '*ThemesPageTest'
```

Expected: missing action controller/views and final constructor.

- [ ] **Step 3: Implement the workflows.** Add one serialized worker per page, busy gating and generation/disposed checks visible across threads. `close()` invalidates the lifetime, cancels unstarted work and calls orderly worker shutdown; do not interrupt a confirmed publication already executing. Worker jobs check lifetime before starting unconfirmed work. Planning/conversion/commit/duplicate/removal/notices reads run off EDT; marshal completion to SwingUtilities.invokeLater and verify lifetime before touching context/surfaces. Do not cancel a running filesystem commit by pretending it never happened; after successful or partially successful writes, call the supplied refresh even if the page closed. The refresh future completes after the catalog scan, and the page updates only when still live. Exceptions become plain-text operation reports, not uncaught EDT failures.

Create all confirmation/import/report surfaces through AuxiliaryWindows, owned by context.surface().get(); close them on controller close and refuse to open if owner is null/closed. Use bounded scrollable import rows and a fixed action footer; provider warnings/failures remain visible, and zero viable providers disables commit. User name/content is data. Import invokes existing immutable plan/commit methods, never edits sources and never reconverts while committing. Unsupported file suffix is rejected before submission. No native confirmation is invoked by tests.

Bind buttons to the active picker. After Duplicate, stage the returned name only if the page still lives and the initiating selection is still the one captured; otherwise leave the user's newer choice alone and report the created filename. Reports persist until the next explicit action, and catalog diagnostics update independently. Folder action calls ensureDirectory then the injected opener. Production trash predicate checks Desktop.Action.MOVE_TO_TRASH support and invokes moveToTrash; unavailable/false/exception leads to a visible failure with a separate permanent-delete confirmation, never implicit fallback.

Read notices from `/dev/jasper/app/themes/THIRD-PARTY-THEMES.md` (the existing packaged resource); keep resource UTF-8/size bounded at 1 MiB, with a visible error if absent. No Markdown web renderer is necessary. Task 6 installs the page factory and concrete Desktop functions.

- [ ] **Step 4: Run GREEN.** Repeat Step 2 plus `*ThemeImporterTest`, `*ThemeDuplicateTest`, `*ThemeRemovalTest`; `./gradlew verifyApplicationArchitecture` passes. No test touches a real desktop/trash or opens a native dialog.
- [ ] **Step 5: Commit.**

```bash
git add jasper-app/src
git commit -m "feat(themes): wire local import and file actions" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

### Task 6: Register the page, catalog menus and end-to-end acceptance

**Files:** Modify `settingsui/CorePages.java`, `Editors.java`, `application/JasperApplication.java`, `ConfigurationController.java`, `workspace/WindowContent.java`, `WindowChrome.java`, `WindowCommands.java`, `config/CoreSettings.java` and package-info files; tests `settingsui/CorePagesTest.java`, `NativeThemeNavigationTest.java`, `SettingsDialogTest.java`, `workspace/WindowThemeMenuTest.java`, `application/JasperApplicationThemesTest.java`; create `themesui/ThemesWorkflowTest.java` and `workspace/WindowThemeMenuTest.java`; update `docs/themes.md`, `docs/configuration.md`, `docs/app-maintenance.md`, `docs/STATUS.md`, this plan and remaining-work assessment.

**Interfaces:**
- Consumes: final ThemesPage constructor and ThemeActions from Task 5; latest catalog and configuration.reload future from Task 1; existing SettingsDialog save/rebase lifecycle.
- Produces: CorePages.Pages adds `Function<PageContext,SettingsPage> themes` between plugins and tools; update all factories/callers/tests explicitly. Its production caller is application wiring, so settingsui does not import themesui. Extend `CorePages.specs` with a `Consumer<String> navigate` parameter for link actions, keeping old overloads as forwarding compatibility for fixtures only where still used.
- Produces `WindowContent.connectThemeCatalog(Supplier<ThemeCatalog.Snapshot>)`; package-private `ThemeCatalog.Snapshot availableThemes()`, `void chooseUiTheme(String name)`, `void chooseWorkspaceTheme(String name)`. ConfigurationController.register supplies catalog::latest; unregister/close releases supplier and menu listeners. Unconnected test windows default to the bundled snapshot. All writes go through existing choose(Setting, value, fallback).

- [ ] **Step 1: Add failing integration tests.** CorePages must contain exactly one `themes` page; theme settings are absent from schema rows and owned by ThemesPage instead. Update the “every core setting exactly once” test to combine schema settings with the explicit pair `UI_THEME_NAME` / `UI_THEME_WORKSPACE`, not to remove the coverage assertion. Both links navigate to correct fragments. All required keywords find Themes; removed store IDs are absent.

Use SettingsDialog's actual headless shell/hooks to test preview → Apply → Cancel/OK, including a failed/stale ConfigService save. Pin shared-draft saving once, no page-side second write, Cancel discarding choices, and an externally refreshed catalog preserving dirty choices. An already confirmed Import/Duplicate remains on disk after Cancel.

```java
assertThat(pages).extracting(SettingsPage::id).containsOnlyOnce("themes");
assertThat(navigation).contains("themes#ui", "themes#workspace");
assertThat(themePage.searchTerms()).contains("theme", "colour scheme", "palette", "alacritty",
    "intellij", "marketplace", "syntax", "workspace");
assertThat(saveRequests).hasSize(1);
assertThat(saveRequests.getFirst().changes()).containsKeys(CoreSettings.UI_THEME_NAME, CoreSettings.UI_THEME_WORKSPACE);
```

Menu tests add a native file after window creation, open View, choose it, then delete/repair it; rows and selected marks follow the current catalog/request, and canonical config strings are written. Include a native theme literally named Light or Dark: it must not be interpreted as a legacy alias. With 284 bundled choices, every catalog name appears exactly once per zone, no missing off-screen-only path; test submenus' bounded item counts. Settings workflow test combines a duplicate with commented defaults, external edit, automatic preview/live-window refresh, malformed edit warning and repair. No GUI process.

- [ ] **Step 2: Run RED.**

```bash
./gradlew :jasper-app:test --tests '*CorePagesTest' --tests '*NativeThemeNavigationTest' --tests '*SettingsDialogTest' --tests '*WindowThemeMenuTest' --tests '*JasperApplicationThemesTest' --tests '*ThemesWorkflowTest'
```

Expected: missing page/links and current View menu limited to three legacy terminal choices.

- [ ] **Step 3: Wire and finish the deliverable.** Application page factory passes real catalog supplier, current terminal options and page-owned ThemeActions. The options supplier calls `configuration.snapshot().viewOptions(configuration.snapshot().font().size(), themes.current().palette())` on EDT; use the configured base size, not a particular window’s zoom. Map configuration.reload to CompletableFuture<Void> for actions; directory home is configuration.state().file().toAbsolutePath().getParent(), honoring --config. Use Desktop.OPEN for the folder (off EDT), exposing unavailable/failure text. Never launch a terminal shell as a folder fallback.

Replace Appearance's UI-theme row with “Choose UI theme…” → themes#ui; retain interface-font rows. Replace Font & Colors' workspace dropdown with “Choose workspace theme…” → themes#workspace. Keep existing Font & Colors title and terminal typography. Retire Editors.theme/terminalColors and their now-unused ActionCombo infrastructure only after checking no other consumers. Update CoreSettings descriptions to point to native TOML/Settings › Themes; this closes Plan 4's deferred misleading-plugin help text.

View gets **UI Theme** and **Workspace Theme** menus sourced from availableThemes on opening, plus **Themes…**. Workspace starts with **Same as UI theme**. For more than 30 names, sort and partition into submenus of at most 30 named `1–30`, `31–60`, etc.; each submenu remains keyboard reachable. Selected marks compare canonical names, not darkness. Rebuild only when snapshot/request changes. UI choice writes UI_THEME_NAME; workspace writes UI_THEME_WORKSPACE directly using WorkspaceThemeChoice, bypassing TerminalColors' light/dark normalization. Retain the old three command IDs as compatibility shortcuts mapping explicitly to match/Jasper Light/Jasper Dark; expose no second legacy picker. Menu-generated actions use existing active-window/input-blocking checks. No 284×window permanent command registrations; dynamic menu actions live with the menu and Themes… provides command-palette discovery.

Preserve standalone-window fallback behavior using current ThemeController and resolved catalog themes; application-connected windows always save through ConfigurationController. Record allowed dependencies without weakening architecture guards. Update documentation for selection vs immediate file actions, filtering, fallback/errors, duplicate inheritance, trash/permanent-confirmation behavior, config override homes, and one-second live refresh.

- [ ] **Step 4: Run GREEN and final review.** Repeat Step 2, then one serialized full gate:

```bash
./gradlew check
```

Count test XML only from `jasper-*/build/test-results/test` and `plugins/*/build/test-results/test`; record tests/failures/errors/skips. Verify all four architecture tasks, packaged notices, JavaDoc/doclint, compiled docs, Java source hygiene and authored diff whitespace. Run one independent whole-branch review according to the approved inline workflow; fix Important/Critical findings with regression RED/GREEN and the full gate. Preserve ledger rulings/deferred minors in the final report. Do not claim native desktop acceptance from headless tests.

User-only acceptance checklist (record unperformed until user reports results):
1. UI IntelliJ Light + workspace Dracula; then reverse and match. Preview changes immediately, running windows only on Apply/OK; Cancel restores saved choices.
2. Filter All/Dark/Light and Bundled/Mine; keyboard search/selection; diagnostics for malformed file with line; About notices scroll correctly.
3. Import a copied IntelliJ multi-provider archive and Alacritty TOML; review Replace/Keep both/Skip and fallback report; cancel a plan before publication.
4. Duplicate a bundled theme, inspect its inherited/commented file, edit a token externally, and see it reload without editing config. Repeat malformed→repair and delete→restore.
5. Remove a temporary user copy to trash; verify unsupported-trash permanent-delete confirmation can be canceled. Do not remove real user themes merely to test.
6. Check two app windows, settings closed/reopened, light/dark syntax flags, plugin panes, and the accepted hidden-rail framing. No preview shell should appear.

- [ ] **Step 5: Commit and record evidence.**

```bash
git add jasper-app/src docs
git commit -m "feat(themes): finish settings workflow and catalog navigation" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

## Self-review and handoff

- Spec §7 pickers/staging/errors → Task 3; miniature/terminal/snippet → Task 4; Import/report → Task 5 using Plan 4; Duplicate/Remove → Tasks 2 and 5; live reload → Task 1; keywords/About/links/View → Tasks 3, 5 and 6. §9 page/acceptance coverage → all task tests and Task 6 user checklist.
- All five Review Focus cases have explicit owner tests. No spec requirement is deferred to another theme plan; optional visual theme editing remains out of scope.
- Interfaces use actual Setting<String>, Snapshot, Theme.UserFile, GeneratedTheme, PageContext and frozen ImportPlan types. No new interface abstraction, SDK dependency, private Editor import, global preview LAF or extra watcher.
- Task 3's initial constructor is deliberately extended in Task 5, with all tests updated then; registration only occurs in Task 6. Each prior commit has independently testable behavior and keeps the existing app runnable.
- Plan decisions needing user review are explicit above: immediate file actions, local preview fallback, one-second polling reuse, trash failure confirmation, duplicate-name validation and menu grouping. These refine the approved design without adding network sources or a visual editor.
- Implementation remains unstarted. Ask the user to review this complete plan, preserving their inline execution preference for the next phase.
