# Normal and Compact View Modes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Add the saved Appearance view mode, preserving Normal and giving Compact a native title and floating existing palette.

**Architecture:** Extend the schema and immutable snapshot with ViewMode. Capture it once for the application lifetime; workspace owners use it to suppress the header and choose native decoration installation. Reuse palette floating positioning.

**Tech Stack:** JBR 25, Java Swing, FlatLaf, Gradle wrapper, JUnit/AssertJ.

**Spec:** `docs/superpowers/specs/2026-10-03-jasper-view-modes-design.md`

**Status:** SDD complete: Tasks 1 and 2 implemented and independently approved; whole-branch code review approved with no critical or important findings. The minor stale Task 3 reference in the manual checklist is corrected. Restart-required remains the announced implementation default; the optional user preference question is unanswered. Final controller full check on `713b53b5`: 8,373 tests, 0 failures/errors, 6 skipped; Task 2 focused verification: 97 tests, 0 failures/errors/skips; native acceptance is [user-run](2026-10-03-jasper-view-modes-manual-check.md). Deviations: existing-method edits use exact contracts/tests instead of duplicated large classes; `TerminalWindow` and `WindowCommandPalette` required no changes because existing root installation/menu ownership and palette fallback already satisfy the behavior. Controller clarification: service-null windows keep their existing null initial configuration and sizing; `windowConfiguration()` supplies the default snapshot when called without a service. No merge/push/GUI is authorized.

## Global Constraints

- Normal is the current window without visual or behavioral changes.
- Compact removes the entire workspace header, including plugin toolbar contributions and overflow.
- Preserve the activated palette design; only placement changes.
- The saved key is `window.view_mode = "normal" | "compact"`, default `normal`.
- Switching mode takes effect after application restart. All windows created before the next restart retain the startup mode.
- No terminal, SDK, plugin API or theme format changes. Java 25 on JBR; wrapper only; headless tests; no GUI or benchmark.
- Work only in `/Users/dustin/.codex/worktrees/appearance-view-modes/moray`, branch `codex/appearance-view-modes`. No merge or push. End commits with `Co-Authored-By: Codex <noreply@openai.com>`.

## Review Focus

1. Saving a new mode then creating a window must keep the process's startup mode (Task 2 application test).
2. Toolbar reload, View menu overrides and newly contributed toolbar buttons must never reveal Compact controls (Task 2 workspace tests).
3. Compact must leave native title rendering enabled and add no Swing substitute or blank header row (Task 2 root/layout tests).
4. Normal must retain anchored palette placement and existing platform decoration choices (Task 2 regression tests).
5. Invalid config and builder copies must retain schema diagnostics and unrelated settings (Task 1 tests).

### Task 1: Saved view mode and Appearance editor

**Files:**
- Create `jasper-app/src/main/java/dev/jasper/app/config/ViewMode.java`.
- Modify `config/CoreSettings.java`, `config/ConfigSnapshot.java`, `settingsui/CorePages.java` under `jasper-app/src/main/java/dev/jasper/app/`.
- Test `config/ViewModeTest.java`, `config/CoreSettingsTest.java`, `settingsui/AppearancePageTest.java` under app test sources; amend related template/schema assertions if they enumerate all keys.
- Update `config.example.toml` and `docs/configuration.md`.

**Interfaces:** Produces public `ViewMode.NORMAL`, `ViewMode.COMPACT`; `CoreSettings.WINDOW_VIEW_MODE`; `ConfigSnapshot.viewMode()` and `ConfigSnapshot.Builder.viewMode(ViewMode)`.

- [x] Write failing config tests using real `ConfigLoader.parse(Path.of("config.toml"), text, true)`: absent value is NORMAL; each accepted token maps to enum; invalid token diagnoses `window.view_mode` and falls back NORMAL; wrong type uses existing parser policy. A Compact snapshot copied with `.toBuilder().toolbarHeight(40).build()` remains Compact. Prior canonical and convenience constructors default NORMAL.
- [x] Write Appearance test that finds setting by key instead of fragile row offsets: View mode displays Normal and Compact, defaults Normal, stages Compact for Apply and resets correctly; setting applicability is RESTART and page search contains Compact. Preserve existing theme/font tests.
- [x] Run focused tests and record expected failures before implementation.
- [x] Create the entire value type:

```java
package dev.jasper.app.config;
/** Saved workspace title and command-palette presentation. */
public enum ViewMode { NORMAL, COMPACT }
```

Add schema setting with tokens normal/compact, label View mode, keywords title bar/compact/normal/command palette, `Applies.RESTART`, description explaining native title, hidden toolbar and floating palette. Add to CoreSettings list. Append the field to ConfigSnapshot canonical record, null-check, add prior-canonical compatibility constructor, populate `from`, copy/build and setter. Preserve all older overloads. Append a View mode section/row on Appearance after existing rows to preserve their order. Use existing choice editor (prettifies tokens).
- [x] Document key/default/restart effect and toolbar preference retention; update copyable example. The generated template already consumes the schema: amend expectations only where necessary.
- [x] Run `./gradlew :jasper-app:test --tests '*ViewModeTest' --tests '*CoreSettingsTest' --tests '*AppearancePageTest' --tests '*ConfigTemplateTest' --tests '*ConfigSnapshotBuilderTest'`; adapt only nonexistent test selectors, record actual commands and counts. Self-review, commit task with coauthor trailer, write report with RED/GREEN evidence.

### Task 2: Startup mode, native chrome and floating palette

**Files:**
- Modify `bootstrap/ApplicationBootstrap.java`, `platform/TitleBarStyle.java`, `application/JasperApplication.java`, `workspace/TerminalWindow.java`, `workspace/WindowContent.java`, `workspace/WindowChrome.java`, `workspace/WorkspaceConfiguration.java` under app main sources.
- Modify `workspace/WindowCommandPalette.java` only if existing fallback needs adjustment; keep rendering/controller untouched.
- Tests: `platform/TitleBarStyleTest.java`, `workspace/ViewModeChromeTest.java`, `workspace/WindowCommandPaletteTest.java`, `workspace/WindowChromeContributionsTest.java`, appropriate `application` startup test, existing `workspace/MacTitleBarTest.java` and `workspace/CustomTitleBarTest.java`.
- Update spec/plan status, `docs/STATUS.md`, and create `docs/superpowers/plans/2026-10-03-jasper-view-modes-manual-check.md`.

**Interfaces:** Consumes Task 1 `ViewMode`, snapshot getter/builder. Add overloaded `TitleBarStyle.choose(boolean macOs, boolean nativeDecorations, boolean compact)`; preserve existing two-argument normal behavior. Add `WindowContent.viewMode()` package-private getter and initialize startup mode on first configuration application; later configuration applications must not change it. Expose a package-private `JasperApplication.windowConfiguration()` snapshot helper whose mode is replaced with final captured startup mode; use in actual window construction and prove with headless test.

- [x] Write failing tests and run them. Exact assertions: `choose(true, false, true)` and `choose(false, false, true)` equal NATIVE; passing false equals existing two-argument result for every platform/native combination. Compact root installation returns no MacTitleBar, root content is owner, fullWindowContent/transparentTitleBar are not true, windowTitleVisible is not false. No header is attached, toolbar invisible, palette anchor null, title metadata still updated. Reloaded toolbar settings and View toolbar actions do not reveal controls; a new plugin toolbar contribution remains suppressed. Normal retains current title/header and anchor tests.
- [x] Write startup-lifetime tests: application starts Compact, saved snapshot becomes Normal, `windowConfiguration().viewMode()` stays COMPACT while another live setting reflects latest config; inverse direction too. A WindowContent first configured with Compact stays Compact through later Normal reload and vice versa; unrelated settings apply.
- [x] Implement TitleBarStyle overload: `return compact ? NATIVE : choose(macOs, nativeDecorations);`. Bootstrap uses startup view mode to configure decorations. Capture startup view mode in final application field and use windowConfiguration() in the production new-window path. Preserve service-null defaults. Capture workspace mode only at first configuration apply, before setting toolbar mode.
- [x] Make workspace header visibility and toolbar effective visibility follow captured mode, keeping saved toolbar preference independent. Compact's `paletteAnchor(Component)` returns null. `installTitleBar` skips MacTitleBar install in Compact, keeps native title metadata callback, and applies native macOS appearance without custom geometry. Keep normal path byte-for-byte where possible. `installCustomTitleBar` returns immediately in Compact without installing a deferred decoration listener. Native menu stays on frame, palette root bindings stay installed. No GUI peer recreation or session restart. Keep macOS dialog appearance unchanged; Windows/Linux use the existing native decoration defaults.
- [x] Write/run real palette tests for Compact: invoke normal palette action, assert existing component uses `positioned` full workspace bounds, fixed top for varying results where space allows, stays inside narrow/short windows, remains centered with side dock panels. Escape/outside click dismiss and existing target/focus lifecycle tests still pass. No duplicate palette implementation. If tests expose absent layout refresh, fix at existing owner boundary.
- [x] Run focused tests `./gradlew :jasper-app:test --tests '*ViewMode*' --tests '*TitleBar*Test' --tests '*WindowCommandPaletteTest' --tests '*WindowChrome*Test' --tests '*ConfigurationControllerTest'`. Use known existing test selectors and record commands/results. Update user-run native checklist and status with actual evidence and deviations. Self-review and commit with coauthor trailer, report RED/GREEN evidence. Controller runs final full check and whole-branch review after task review.


## Final controller verification and review — 2026-10-03

Both task reviews approved their implementations. The final whole-branch review approved the code with no critical or important findings; its one minor docs finding (a stale reference to a nonexistent Task 3 in the native checklist) is corrected.

Fresh `./gradlew check` on `713b53b5` passed all 85 tasks (9 executed, 76 up-to-date). XML totals: **8,373 tests, 0 failures, 0 errors, 6 skipped**. Architecture gates and documentation checks passed. Source hygiene covered 1,747 Java files with no forbidden characters; `git diff --check` was clean. Existing compiler warnings remain.

The controller inspected actual headless `settingsPreview` Appearance PNGs in dark/default-font and light/18pt variants: the View mode row and restart notice fit. The preview task enumerates every bundled theme, so it was intentionally stopped with SIGTERM after the relevant renders (exit 143). This verifies those inspected renders only; the full preview matrix did not complete.

Native GUI acceptance remains [user-run](2026-10-03-jasper-view-modes-manual-check.md). No GUI, benchmark, merge or push was performed. Restart-required is the announced implementation default pending a response to the optional preference question, not a separately confirmed user choice.
