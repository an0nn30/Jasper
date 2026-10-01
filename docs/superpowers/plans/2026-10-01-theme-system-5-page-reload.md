# Theme System Plan 5: Themes Page and Live Reload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Planned, not executed. Requires native-only catalog, file operations and migration from plan 4.

**Goal:** Deliver the complete Settings › Themes flow for choosing, previewing, importing and editing both theme slots.

**Architecture:** `themesui` implements an injected SettingsPage over the existing shared SettingsDraft. Catalog work
and disk operations happen off EDT; immutable results update the page on EDT. Application owns a theme-directory
watcher independently of the page so edits reload even when Settings is closed.

**Tech Stack:** JBR 25, Swing/JTextPane, existing TerminalSession/TerminalView fixture, SettingsDraft, headless JUnit.

**Spec:** `docs/superpowers/specs/2026-10-01-jasper-theme-system-design.md`, sections 2.3, 6.3, 7 and 9.
**Prerequisite:** [Plan 4](2026-10-01-theme-system-4-conversion-migration.md).

## Global Constraints

- Pickers stage `ui.theme.name` and `ui.theme.workspace`; only Apply/OK changes the running application's choices.
- UI theme and Workspace theme lists each support search, All/Dark/Light, Bundled/Mine. Workspace starts with
  “Same as UI theme” (`match`). Each theme row shows background, accent and four ANSI swatches.
- Preview does not install a LAF, launch a shell, depend on Editor/RSyntaxTextArea, or publish app appearance events.
- Import/Duplicate/Remove are explicit file operations, committed immediately; Cancel discards staged selections,
  not a completed file operation. Show that distinction in the action result, without an extra approval flow.
- Menus, dialogs, Settings and rail panels remain chrome. SampleTerminal uses canned output and closes its session.
- Runtime remains offline; SDK types only in app.plugins. Add no interface with just one implementation.
- No GUI/benchmark by the agent. Per-task red/green, full `./gradlew check`, review and branch commit with
  `Co-Authored-By: Codex <noreply@openai.com>`. Record actual deviations here and in STATUS.

## Review Focus

1. Filtered-out or invalid/missing selections remain visible as choices with diagnostics, without changing the draft (Task 1).
2. Rapid preview selection or closing Settings cannot install stale results, leak sessions or change application state (Task 2).
3. Collision replacement/removal is cancelled or the file changes under an open dialog: existing content survives (Task 3).
4. File-only edits, atomic editor saves and directory replacement reload once; closed owners never receive callbacks (Task 4).
5. File reload while a Settings draft is dirty preserves staged choices; Apply resolves the newest catalog snapshot (Tasks 1, 5).

## File map

App paths are under `jasper-app/src/main/java/dev/jasper/app/`; tests mirror under `src/test/java/dev/jasper/app/`.

| Files | Responsibility |
|---|---|
| `themesui/{ThemesPage,ThemePicker,ThemeListModel}.java` (new) | Draft binding, filtered rows and diagnostics |
| `themesui/{ThemePreview,SyntaxPreview}.java` (new), `SampleTerminal` (moved in plan 4) | Isolated miniature window and fixtures |
| `themesui/ThemeActions.java` (new), `themeimport/ThemeFiles` | User file operations and reports |
| `appearance/ThemeDirectoryWatcher.java` (new) | Directory snapshots/debounce and lifetime |
| `application/{JasperApplication,ConfigurationController}`, `config/ConfigService` | Watcher composition and forced catalog publication |
| `settingsui/{CorePages,SettingsView}`, `workspace/WindowChrome` | Single Themes page, old route redirects, View menu |
| package-info/architecture files, `docs/{configuration,plugin-authoring,app-maintenance,STATUS}.md` | Boundaries, user instructions, completion evidence |

## Task 1: Two draft-bound searchable theme pickers

**Interfaces:** `ThemesPage implements SettingsPage`; constructor accepts PageContext, ThemeCatalog and a supplied
catalog-change subscription (existing lifecycle.Subscription), plus ThemeActions/preview dependencies defined below.
Inject the factory through `CorePages.Pages` from application; settingsui must never import themesui. `ThemeListModel`
accepts immutable catalog snapshots and filter values; it owns no threads or Swing globals. Retain the canonical theme
id/file identity separately from display name and selected config string so duplicate/invalid names are diagnosable.

- [ ] **Step 1: Add `ThemesPageTest` red tests** `pickerStagesWithoutApplying`, `filtersDoNotReplaceSelection`,
`missingSelectionShowsProblem`, `externalCatalogRefreshPreservesDirtyDraft`.

```java
assertThat(draft.value(CoreSettings.UI_THEME_WORKSPACE)).isEqualTo("Dracula");
assertThat(appliedWorkspaceNames).isEmpty(); // selecting a row only edits the shared draft
assertThat(page.modified()).isTrue();
page.reset();
assertThat(page.modified()).isFalse();
```

Assert against SettingsDraft.value(setting), which includes pending values; do not introduce a page-local draft. Test match appears only in the workspace picker, case/space-insensitive search, every filter intersection,
user-shadowed builtin identification, empty list, row swatches, and problems containing filename and line.
- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*ThemesPageTest'`; expect missing page/model behavior.
- [ ] **Step 3: Implement pickers and shared draft integration.** Use stable sorted catalog order, name plus six
swatches (background, accent, ANSI red/green/blue/yellow), and visible provenance Bundled/Mine. Search/filter changes
only the visible rows; retain the selection with a pinned summary when hidden. Represent a missing or invalid
configured name as a diagnostic row, never silently set Jasper Dark in the draft. Its preview may show the documented
fallback with an explicit label. Row problems identify their file even if parsing prevented a display name.

Bind UI_THEME_NAME/UI_THEME_WORKSPACE directly to SettingsDraft, notify PageContext.changed, and participate in the
existing apply/reset/rebase flow. Do not write config or call ThemeController from row listeners. Page.valid handles
new invalid selections while a preexisting unresolved config value remains inspectable/correctable. Refresh catalog
snapshots without resetting dirty settings. Dispose subscriptions when the page/window closes.
- [ ] **Step 4: Run** model/page/draft tests and `./gradlew check`; commit
`feat(settings): add draft-bound UI and workspace theme pickers` with required trailer.

## Task 2: Isolated live preview with terminal and syntax

**Files:** new ThemePreview/SyntaxPreview, existing moved SampleTerminal; tests `ThemePreviewTest`, `SyntaxPreviewTest`.

**Interfaces:** `ThemePreview(TerminalOptions options)`, `JComponent component()`,
`void show(ResolvedTheme staged)`, `void close()`; owns exactly one SampleTerminal. SyntaxPreview accepts native
`Map<SyntaxRole,SyntaxStyle>` plus editor tokens. A fixed text/role-span fixture is checked into the class; no syntax
parser or plugin dependency. Preview and materialization are EDT-confined; catalog file work remains off EDT.

- [ ] **Step 1: Add red tests** `previewDoesNotChangeApplicationAppearance`, `previewUsesBothStagedSlots`,
`closingPreviewReleasesTerminalAndRejectsLateWork`. Capture global LAF/UI defaults and app callbacks, stage two
workspaces and assert terminal palette and each text span's foreground/bold/italic. Pin the miniature chrome/sidebar/
status colors separately from workspace tabs/editor. Match shares the generated value just as live resolution does.

```java
assertThat(UIManager.getLookAndFeel()).isSameAs(beforeLaf);
assertThat(applicationThemeEvents).isEmpty();
assertThat(previewTerminal.palette()).isEqualTo(staged.workspace().terminal());
assertThat(document.getCharacterElement(keywordOffset).getAttributes()
        .getAttribute(javax.swing.text.StyleConstants.Foreground))
        .isEqualTo(staged.workspace().syntax().get(SyntaxRole.KEYWORD).color());
```

Use test package access for fixture/document/session lifecycle, no new public testing API.
- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*ThemePreviewTest' --tests '*SyntaxPreviewTest'`; expect missing preview.
- [ ] **Step 3: Implement miniature rendering.** Custom-paint the chrome title/search/sidebar/status and workspace tabs
from the two generated values; set JTextPane attributes directly from pretokenized spans, including editor background,
foreground and caret/selection where shown. Keep the sample noninteractive and unfocusable. Update the retained terminal
palette rather than create a session per selection. Never wrap preview changes in global LAF installation/restoration.
Use a monotonically increasing request generation for any deferred catalog result; ignore stale/closed results. Close
the one preview session and listeners idempotently with the Settings page. Use accessible labels for preview regions.
- [ ] **Step 4: Run** preview/page tests and `./gradlew check`; commit
`feat(settings): preview staged chrome and workspace themes` with required trailer.

## Task 3: Import, duplicate, open, remove and bundled notices

**Files:** ThemeActions, ThemeFiles extensions, ThemesPage; tests `ThemeActionsTest`, `ThemeDuplicateTest`.

**Interfaces:** ThemeActions composes plan-4 ThemeImporter/ThemeFiles and platform file operations using injected
existing functional types (chooser, collision choice, confirm, trash, open), not new single-implementation interfaces.
Add `String ThemeTomlWriter.duplicate(Theme original, GeneratedTheme generated, String name)`;
`ThemeFiles.createText(String id, String text)` validates/reparses before atomic no-overwrite publication. Remove uses
`ThemeFiles.remove(Path target, String expectedSha256, boolean confirmedPermanentDelete)` with an injected trash
function supplied by application; return an explicit outcome (trashed/deleted/cancelled/changed/failed).

- [ ] **Step 1: Add red tests.** Import all supported extensions and multi-provider archives; Replace/Keep both/Cancel;
partial failure report; duplicate escaping/filename collisions; Remove unavailable for bundled themes; trash success,
unavailable-trash confirmation/cancel, I/O error and changed target. Verify original theme remains untouched and
native output parses/resolves to the same generated seeds/tokens before any user edits.

```java
assertThat(duplicateText).contains("extends = \"Jasper Dark\"");
for (Token token : Token.values()) assertThat(duplicateText).contains("# " + token.key() + " = ");
assertThat(Files.readString(originalPath)).isEqualTo(originalText);
assertThat(permanentDeleteCalls).isZero(); // user cancelled fallback confirmation
```

Also assert comments for every Seed and normal/bright ANSI key, optional cursor/selection current values, user-supplied
name quotes/newlines escaped, and no active copied token assignments. Duplicate an inherited/imported theme too.
- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*ThemeActionsTest' --tests '*ThemeDuplicateTest'`; expect missing actions.
- [ ] **Step 3: Implement import UI.** Choose file once, plan off EDT, present per-theme user-name conflicts with Replace
or Keep both (close/cancel skips), then commit choices off EDT and show actual names/fallbacks/failures. Serialize file
operations; disable only affected actions during work. Recheck plan fingerprints after a delayed dialog. Refresh
catalog and retain staged selections; importing alone does not apply a theme. Closing the page cancels uncommitted
work; an already committed write remains and reports on next catalog scan, without touching a disposed component.
- [ ] **Step 4: Implement duplicate and removal.** Ask for a nonempty unique display name, allocate a safe file id, write
name+extends and every current seed/token as comments, organized under commented section headings (not active empty
sections that alter semantics). Parent references use exact original display name. Preserve generated ANSI/current
cursor/selection in comments. Explain that inherited values continue tracking their parent until uncommented.

Open folder uses the existing platform open operation. Remove is enabled only for a regular direct-child user file;
resolve/check its identity/hash immediately before action. Use system trash when supported; if unsupported, ask
before permanent removal as the spec requires. A failed trash attempt is an error, not permission to delete. If the
removed theme was selected, preserve config names and display normal missing-theme fallback/diagnostics; removing
an inheritance parent surfaces dependent errors instead of silently flattening them. Reappearance repairs selection.
- [ ] **Step 5: Implement About bundled themes.** Open packaged THIRD-PARTY-THEMES.md through the app's document/help
surface; if it requires a real file, extract only that bundled resource to app cache and open locally. No network fetch,
Marketplace links or plugin dependency. Run all actions/page tests and `./gradlew check`; commit
`feat(settings): add native theme file actions and provenance` with required trailer.

## Task 4: Theme-only edits trigger debounced reload independently of Settings

**Files:** ThemeDirectoryWatcher (new), ThemeCatalog/ConfigService/application integration;
`ThemeDirectoryWatcherTest`, ConfigService and ConfigurationController tests.

**Interfaces:** `ThemeDirectoryWatcher(Path directory, Runnable changed)` implements AutoCloseable, with start/close
lifetime owned by application. Use the same scheduled polling/debounce cadence as ConfigService (currently one second)
and reuse its injected scheduler test pattern. changed requests `ConfigService.reload()` so companion catalogs rescan
and consumers publish even when ConfigSnapshot values did not change. It must not call Swing directly.

- [ ] **Step 1: Add red tests** `themeEditReloadsWithUnchangedConfig`, `atomicSaveAndRenameCoalesce`,
`closedWatcherDropsQueuedEvents`, `malformedThenRepairedThemeRetainsRequestedChoice`.
Use temp files and controllable scheduler/executor; no arbitrary sleeps. Include file add/delete, directory initially
missing then created, directory rename/recreation, same-size edit, inherited parent edit and failed read. Verify line
warnings and page diagnostics update; source identity invalidates generation caches. Assert one coalesced change and
zero post-close publications. A dirty Settings draft retains its selected strings through external reload.
- [ ] **Step 2: Run** watcher/config/controller tests; expect no reload when config.toml is unchanged.
- [ ] **Step 3: Implement snapshots and publication.** Fingerprint direct `.toml` regular files by normalized filename
plus size/mtime/content digest (same-size/same-timestamp edits must still be detected); ignore legacy, own temp files
and symlinks. Keep filesystem work off EDT. Debounce using observed snapshots so atomic save bursts produce one
stable reload; parse failure is a diagnostic, not silent retention of an invalid cached Theme. Retry transient read
failures on the next poll. Monitor directory creation/replacement without requiring a page to be open.

Coalesce reload requests and guard worker completion/EDT publication with lifecycle generation. ConfigService must
force companion publication for theme changes even when configuration equality suppresses ordinary updates. Avoid
watch→reload→watch loops: reads do not rewrite files. Invalidate only obsolete generator/cache identities and preserve
unchanged ones. On shutdown cancel scheduling, drop callbacks and release thread resources. Keep missing slot behavior
from plan 2 and migration-failure diagnostics from plan 4.
- [ ] **Step 4: Run** watcher/controller/draft tests and `./gradlew check`; commit
`feat(themes): reload edited native themes without config changes` with required trailer.

## Task 5: Final navigation, documentation and acceptance

**Files:** CorePages, SettingsView, WindowChrome, JasperApplication; settings search/menu tests;
configuration examples, docs/configuration.md, docs/app-maintenance.md, docs/plugin-authoring.md, docs/STATUS.md,
this plan's banner and the remaining-work assessment.

**Interfaces:** `CorePages.THEMES` now identifies the single page, not a group. Old `themes.intellij` and
`themes.alacritty` routes redirect to it for retained navigation references. Inject one ThemesPage factory through
CorePages.Pages, remove old two-page factory fields. Font & Colors links to THEMES, View menu reads native catalog.

- [ ] **Step 1: Add red route/search/apply tests.** Keywords exactly: theme, colour scheme, palette, alacritty, intellij,
marketplace, syntax, workspace. All find Themes. Font & Colors has no terminal color dropdown. View menu choices and
checkmarks match both configured slots and match. Apply/OK resolves the latest catalog, saves canonical keys and
updates running windows once; Cancel leaves running selections unchanged. Open Settings after an external edit and
verify current diagnostics/choices. Catalog removal while the page is open must not crash menu rebuild.
- [ ] **Step 2: Run** settings search/navigation/menu tests; expect old or incomplete routing.
- [ ] **Step 3: Wire the final page/menu and documentation.** Keep menus/windows chrome; picker preview is staged.
Remove duplicate selectors where the final Themes page owns those choices, retain deep-link redirects. Explain config
aliases, native format/extends/comments, importing collisions, immediate file actions versus Apply, live reload and
migration recovery. Update About notices and package-info/architecture allowlists. Mark each requirement complete only
with its owning automated test or user-only visual check; do not call unperformed GUI acceptance passed.
- [ ] **Step 4: Run final verification and independent review.** `./gradlew check`, exact XML test counts, source hygiene,
`git diff --check`; conversion drift/uniqueness/provenance remain part of check. Review the whole branch against the
full design and plans 1–5, fix findings, and record actual deviations and remaining manual checks in STATUS.
- [ ] **Step 5: Commit** `feat(settings): finish unified Themes navigation and documentation` with required trailer.

**User-only visual acceptance:** IntelliJ Light chrome + a dark workspace (original mismatch), both Jasper variants,
Editor syntax in both variants, tabs/find/dividers/icons, workspace plugin controls versus rail panels, several bundled
families, importing a JAR and Alacritty TOML, Duplicate→edit→reload with Settings open and closed, invalid file line
warnings and repair, Remove/trash, Apply versus Cancel, and chrome menus/tooltips/dialogs. Agent never opens GUI.
