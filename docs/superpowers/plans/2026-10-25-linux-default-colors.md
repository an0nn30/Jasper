# Linux Desktop colors default implementation plan

> **For agentic workers:** Use superpowers:subagent-driven-development. Execute this single task with TDD and independent review.

**Goal:** Make Desktop colors the default interface theme on Linux.
**Architecture:** CoreSettings supplies platform-dependent defaults for both theme picks. Saved values and look overrides retain their established precedence. The existing Automatic source and fallback remain unchanged.
**Tech Stack:** Java 25, JUnit, Gradle.
**Spec:** User request of 2026-10-25: “this GTK theme to be the default theme when the app is installed on Linux.” This small change extends the approved Linux Desktop colors design.
**Status:** Complete on `codex/linux-default-colors`; not merged or pushed. Task and whole-branch reviews passed without findings. Native Linux RED confirmed the empty-config expectation failed before implementation. The work follows the existing platform-default pattern. Scope adjustments: the configuration guide table and generated appearance description were corrected, and authored-only Settings fixtures now declare their Meadow look defaults explicitly. Native Linux tests also exposed a preview/runtime mismatch for invalid authored picks: Settings now uses the same authored fallback constants as ThemeSelection, preserving the existing fallback policy.

## Global Constraints
- Linux default theme id is `desktop-colors` for both dark and light; source remains `auto` and appearance remains system.
- Other platforms retain Jasper Meadow. Existing saved picks and optional look overrides win.
- No editor/terminal scheme changes, migrations, new dependencies, GUI runs, or changes to GTK extraction/fallback.
- Commit on the feature branch with a Co-Authored-By trailer; no automatic merge or push for this follow-up.

## Review Focus
- Missing configuration keys use Linux defaults; explicit values survive reload.
- Reset reads the same default source as first run.
- Optional presentation look defaults retain precedence.
- macOS, Windows, unknown and null OS names retain authored defaults.
- Configuration examples and generated setting comments accurately describe platform defaults.

### Task 1: Set and verify Linux defaults

**Files:** CoreSettings.java and config tests in jasper-app/src/{main,test}/java/dev/jasper/app/config; docs/themes.md, config.example.toml and related existing default descriptions only as required.
**Interfaces:** Add package-private `static String interfaceThemeByDefault(String osName, String authoredDefault)` in CoreSettings; use it for both UI_THEME_DARK and UI_THEME_LIGHT with their existing JasperTheme constants.

- [x] Add tests asserting Linux -> desktop-colors, Mac OS X/Windows 11/FreeBSD/null -> passed authored default; assert host setting defaults, source auto, appearance SYSTEM; exercise absent and explicit persisted keys and existing look/reset behavior.
- [x] Run focused config tests and observe the new expectation failing before implementation.
- [x] Implement the helper using case-insensitive Linux detection and apply it to both default declarations; preserve existing precedence. Update Linux-dependent test expectations, without weakening assertions.
- [x] Update current guides/example comments and the status entry minimally; record any deviation here.
- [x] Run config, appearance, settings/presentation tests and application architecture checks. Run relevant native Linux config tests on the authorized VM if available; no GUI. Verify generated configuration example consistency.
- [x] Commit the deliverable with `Co-Authored-By: Codex <noreply@openai.com>` and obtain independent task and whole-branch review.

## Verification

- Native Linux RED: empty configuration expected `desktop-colors` and received `jasper-meadow` before implementation.
- macOS full `./gradlew check --continue --max-workers=2` with Python 3.13.15: 13,019 tests, zero failures/errors, 30 skips.
- Final macOS config, appearance, settingsui and presentation rerun after the final edits: 594 tests, zero failures/errors/skips; application architecture passed. Configuration example and generated template consistency tests passed.
- Final native Linux packages: 594 tests, 591 passed, two previously documented baseline rendering failures (`PaintedContrastTest` Slate Cobalt Dark hint and `SelectionRenderingTest` text-pixel count), one skip. All changed default/configuration/Settings assertions passed. Application architecture passed.
- No GUI, AOT training, merge or push.
