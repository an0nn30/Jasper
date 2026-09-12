# Moray — Status and Handoff

**As of:** 2026-09-11. All UI work is merged into main at `1bd8b49`; merged verification passed 332 tests (331 passed, one known font skip), all eight tasks executed. The completed mock-ui worktree/branch were removed. Plan 4a is implemented on `codex/plan-4-config` in `.worktrees/plan-4`, with the same clean 332-test baseline. See [config design](superpowers/specs/2026-09-11-moray-plan-4a-config-design.md) and [implementation plan](superpowers/plans/2026-09-11-moray-plan-4a-config.md). Native UI, benchmark and daily-use acceptance remain open.

**Plan 4a progress:** Saved settings and live reload are implemented on `codex/plan-4-config`, with all task and final reviews complete through `acd5ea3`. Fresh full verification: 394 tests, 393 passed and one known font skip, no failures/errors. Actual status renders passed visual inspection in dark/light at normal/narrow widths. Final whole-branch review approved after correcting Windows-specific test fixtures; no findings remain open. Native and Windows execution remain unverified. See [configuration usage](configuration.md), [status renders](design/config-status-comparison.md) and [native acceptance](superpowers/plans/2026-09-11-moray-plan-4a-manual-check.md).

**Current follow-up:** Default title/tab height is now 38px, configurable from 28–72 through View → Tab height… for the current window. Both Swing and native height use the same value; reset restores 38, cancellation preserves the previous value. Height survives theme changes but is session-only. Opening and closing tabs and the selected underline animate with a shared 180ms eased settle; selection/focus/launch remain immediate and timers stop when idle/hidden/disposed. The existing shortcut engine now provides Cmd/Ctrl+1–9 and Cmd/Ctrl+{ / } (Shift+brackets), with plain Ctrl for tab navigation outside macOS. Unrelated shortcut behavior is retained. [Design](superpowers/specs/2026-09-11-moray-tab-motion-design.md), [plan](superpowers/plans/2026-09-11-moray-tab-motion.md), [native checks](superpowers/plans/2026-09-11-moray-tab-motion-manual-check.md).

**Active revision:** The user's exact mock supersedes the earlier separate title and two-tone toolbar choices. Implemented title-bar tabs, horizontal neutral toolbar, and seamless terminal/status surface. See the [design](superpowers/specs/2026-09-11-moray-mock-ui-design.md), [implementation plan](superpowers/plans/2026-09-11-moray-mock-ui.md), [original reference](design/mock-ui-reference.png), and [measured comparison](design/mock-ui-comparison.md). The current compact height supersedes the original mock geometry; measured colors are retained. Font metrics, real grid dimensions, native window appearance and motion still require visual acceptance.

Read [AGENTS.md](../AGENTS.md) for repository rules, [README.md](../README.md) for running, and the [Phase 1 design](superpowers/specs/2026-09-10-moray-phase-1-terminal-design.md) for binding product requirements. GUI launches and benchmarks are user-run only.

## 1. Goal and delivery phases

Moray is a Java Swing terminal workstation with a MobaXterm-style layout, built to replace `~/projects/conch` as the daily terminal. Product phases: terminal → SSH session management → credential vault → SFTP → tunnels → plugins and editor. Reuse tested non-UI Java from `~/projects/termlab-bundle` in later phases. Do not redesign the terminal foundation before the Phase 1 switch-over test.

| Plan | State |
|---|---|
| 1 — Terminal core | Complete on main (`6daa61f`..`d0ccbfb`) |
| 2 — Terminal completeness | Complete on main (`196e24d`..`0ce4add`) |
| 3 — App chrome | Implemented and reviewed on main; all automated checks passed; native acceptance pending |
| 3.5 — macOS chrome, themes and toolbar | Integrated on main and fully reviewed; native acceptance pending |
| Screenshot UI revision | Implemented and fully reviewed; compact/motion follow-up implemented and fully reviewed; native acceptance pending; integrated on main |
| 4a — Saved settings and live reload | Implemented and fully reviewed on the feature branch; native acceptance and integration pending |
| 4b onward — Remaining configuration and packaging | Additional terminal options, custom themes/system appearance, logging/launcher and .app packaging remain |

Plan 3 design: [application design](superpowers/specs/2026-09-11-moray-plan-3-app-chrome-design.md). Execution: [implementation plan](superpowers/plans/2026-09-11-moray-plan-3-app-chrome.md). The per-plan scratch workspace was removed after final review; this handoff, completed plan checkboxes and git history preserve the record. Do not restart Plan 3 or Plan 3.5; the active configuration work is in `.worktrees/plan-4`.

Plan 3.5 was developed on `codex/plan-3-5-chrome-themes` and merged locally. That milestone is available in `/Users/dustin/projects/moray` on `main`; current development uses the Plan 4 worktree above. [Title/theme design](superpowers/specs/2026-09-11-moray-plan-3-5-titlebar-themes-design.md) and [execution plan](superpowers/plans/2026-09-11-moray-plan-3-5-titlebar-themes-implementation.md) cover the first runnable deliverable. [Toolbar study](design/plan-3-5-toolbar-study.html) is an illustrative discussion aid; the user selected B (fuller, two-tone colored icons), now included as Task 4.

Previous milestone: [Plan 3.5 — macOS chrome, themes and toolbar](superpowers/plans/2026-09-11-moray-plan-3-5-chrome-and-themes.md). The user has tried Plan 3 during development and reports that it looks good overall; this is qualitative feedback, not a claim that every native checklist item or benchmark was completed.

## 2. Implemented behavior

The existing terminal retains ligatures, fallback fonts, wide characters, colors, cursor modes, Option input, mouse reporting, scrollback, selection, clipboard, bracketed paste, prompt jumps, OSC integration and clickable links.

Plan 3 adds:

- Multiple independently owned windows and tabs; close buttons, middle-click close, drag reorder, F2 rename with shell-title override retention.
- Pure split-tree model; nested right/down splits, directional focus, divider ratios, zoom/restore without restarting shells.
- Shared action catalog and keybinding parser; application shortcuts before terminal encoding; native editing in find fields retained.
- FlatLaf menus, seven-button toolbar with Tabler outline SVGs, view visibility controls, light/dark chrome, focused shell/directory/dimensions status, and inactive-pane dimming.
- Async find bar with debounce, case/regex options, next/previous, count and retained error state, Escape/close, and stale-result rejection.
- Local context menus that preserve terminal-program mouse ownership.
- Per-pane font controls, screen-preserving history clear, working-directory inheritance with launch-time validation.
- Background shell launch with close-before-completion cleanup; native Quit routes through app cleanup.

Plan 4a now reads saved TOML settings, polls for changes, enables Settings/Reload and exposes positioned diagnostics through status. Configured height, toolbar/status visibility, font size, built-in theme and shortcuts apply live across owners. Unrelated reloads preserve temporary View/font overrides; Settings creates a template only when absent. The integrated UI baseline on main predates this configuration behavior.

The earlier Plan 3.5 implementation (visual geometry superseded below) includes:

- Live dark/light terminal palettes and coordinated FlatLaf themes (`09e382e`, `6bdd298`). Existing hidden/zoomed panes, delayed launches and new windows use the selected theme while retaining sessions, fonts, selection/find state and split ratios. Explicit terminal truecolor remains unchanged.
- A slim macOS title surface (`7833692`) using the decorated frame and supported public properties. Native traffic lights and window behavior remain owned by macOS; separate tabs and toolbar sit beneath the title. Other platforms retain their existing decorations.
- The user's selected toolbar B (`116709b`): 28-pixel two-tone Tabler icons, distinct action colors in both themes, labels and visibility modes retained. Settings/Reload remain muted while disabled.

Actual headless Swing previews: [dark](design/plan-3-5-titlebar-dark.png) and [light](design/plan-3-5-titlebar-light.png). Native traffic lights are absent from these renders. Native acceptance remains user-run; Plan 4 retains configuration, custom theme files, persistence, automatic system appearance and packaging.

The screenshot revision now supplies:

- A single 54px title/tab row with native traffic-light space, persistent tab controls, overflow navigation and a clipped trailing title. Public JBR title-height integration retains native decoration with a fallback.
- A 53px horizontal toolbar using 16px neutral Tabler outlines, inline labels, highlighted New tab, separators and right-aligned Settings/Reload. Existing actions, menus and visibility modes remain functional.
- A 30px status bar that shares the terminal background, live shell/path/grid metadata and running dot; 24px terminal padding and app default/reset font size 16.
- Measured dark surface and visible ANSI accent colors, with equivalent light geometry. Actual headless previews: [dark](design/mock-ui-dark.png), [light](design/mock-ui-light.png). Reproduce with `./gradlew :moray-app:mockUiPreview`.

## 3. Fixes included during Plan 3

- `openingLink` capture clears on new press and focus loss; a missed release cannot swallow a later gesture.
- Alternate-screen transitions invalidate selections, matches, pending find and viewport row references.
- A session row epoch reconciles history/alternate/reflow resets missed during temporary view detachment.
- Search keeps one bounded per-view executor through reparenting; its thread retires after idle. New requests supersede queued work; cancellation cannot multiply workers on repeated zoom/reparent.
- Expired prompt rows are physically pruned during history eviction.
- PTY close returns promptly to the EDT, sends Unix hangup, applies a bounded force fallback, and closes acquired streams. A non-daemon cleanup worker survives final-window disposal long enough to perform fallback. A real headless child ignoring HUP/TERM is covered on Unix.
- Find navigation requested during debounce/inflight search is retained; invalid regex errors persist until the query changes. Reparenting clears canceled search state.
- Copy action enablement checks selection presence in constant time without extracting text or taking the buffer lock.
- Font and nested-split minimum dimensions propagate to the native frame; PTY, view and emulator agree on the pinned JediTerm minimum of 5 columns × 2 rows.
- Appearance menus synchronize their selection with the active global theme when opened, including changes made from sibling windows.
- Long directory/status text and tab names cannot inflate native minimum width; full tab titles remain in tooltips.
- Global shortcuts are installed on the root pane, with native text-field editing retained.
- F13–F24 override parsing uses Java's separate high-function-key range; actual F13/F24 strokes are regression-tested.

## 4. Verification still required

1. User-run [compact tab/motion checklist](superpowers/plans/2026-09-11-moray-tab-motion-manual-check.md), then [screenshot UI checklist](superpowers/plans/2026-09-11-moray-mock-ui-manual-check.md): native controls, title-tab hit testing, dragging/double-click, fullscreen, scaling and exact visual comparison in real windows. The earlier Plan 3.5 appearance checklist is superseded by this reference. Also retain the [Plan 3 acceptance checklist](superpowers/plans/2026-09-11-moray-plan-3-manual-check.md): native windows/menu/Quit, split/zoom/focus/dividers, tab gestures, find, clipboard, links, mouse reporting, directory inheritance and chrome readability. Headless tests do not establish these native visual results.
2. User-run benchmark after integration, only with no game or VM running. Historical Plan 1 result: 41.5 MB/s. A Plan 2 baseline measured during this session before newly added AGENTS restrictions were discovered: **35.9 MB/s** (105.5 MB in 2.94 s). No post-integration benchmark has run. Gate ≥35 MB/s, target ≥45 MB/s.
3. CI has never run: no git remote exists. Workflow covers macOS, Ubuntu and Windows with JBR25. Ask before adding a remote or pushing. Windows ConPTY/forced-close and Linux desktop/font behavior remain unverified on their native systems.
4. After Plan 4, satisfy the Phase 1 manual checklist and two-week Moray-only trial. Record every reason to reopen conch and fix the blockers before SSH begins.

Known skip: `FontSetTest.fallsBackWhenPrimaryCannotDisplay` skips on this Mac because its probe finds no differentiating glyph. Nerd Font-specific fallback tests run with a suitable installed font and skip on systems lacking it.

## 5. Remaining terminal hardening

These carried-over findings remain explicitly open; they were not silently counted as implemented by Plan 3:

- Replace gesture flags with a press-time owner for the whole gesture. A reported press followed by Shift drag/release can still change routing; matching release to the correct button needs stronger handling. Command-click on non-link selection behavior also needs review.
- Share and bound the logical-line walk for URL lookup and line selection; a huge wrapped line can hold the buffer lock too long.
- Stop unnecessary frame/blink timer wakeups per idle pane; avoid full snapshots/repaints for every mouse motion; binary-search visible search highlights instead of scanning all matches each frame.
- Move Desktop.browse off the EDT; report every notch of a multi-notch reported wheel event.
- Selection refinements: overwritten live content, word-wise drag after double-click, and selections ending on half a wide character.
- Search does not span physical soft-wrapped rows. Java regex cancellation remains best effort; a pathological running regex can delay the next request in that pane, although queued work and worker allocation are bounded.
- Width reflow or history erase clears row-based selection/matches/prompts and returns to live. This is intentional with JediTerm's row model.
- Strikethrough is unsupported by jediterm-core 3.76 TextStyle.
- macOS font fallback uses JBR/system cascading for CJK/emoji; explicit fallback chiefly affects missing Nerd Font glyphs.

Retained coverage opportunities: astral search, edge/overlapping highlights, large-history indicator geometry, multi-row URL continuation lookup, empty-scrollback viewport, middle-button routing, copy-on-select, alternate-screen wheel/arrows, multi-notch reports, paste-to-live, Shift+PageDown and match colors. Add behavioral regressions when working in these areas, not tests that mirror source constants.

## 6. Native acceptance and remaining Plan 4 work

The screenshot revision supersedes Plan 3.5 geometry and artwork. User visual/native acceptance remains open; the user approved integration and starting Plan 4. The integrated UI baseline runs from `/Users/dustin/projects/moray` with `./gradlew :moray-app:run`. To try Plan 4a before integration, run that command from `/Users/dustin/projects/moray/.worktrees/plan-4`. No complete pixel-identity claim: native frame controls/focus/font rasterization are not headless-verifiable, and the existing terminal renderer computes 91 × 35 cells versus the mock's 100 × 40 at the measured content size. See the comparison report for exact prompt ink bounds. Built-in palettes and live theme application have moved forward from Plan 4.

Plan 4a supplies per-OS paths, positioned TOML diagnostics, last-good retention, live reload, Settings creation and --config. Remaining Plan 4 work includes additional terminal settings, custom theme files, automatic system appearance, app logging and macOS .app packaging with JBR. Carryovers:

- Replace printStackTrace/silent I/O catches with appropriate app logging/feedback.
- Scrub launcher environment variables (TERM_PROGRAM, TMUX, iTerm variables) and provide UTF-8 LANG for Dock launch.
- Implement line_height; revisit logical-pixel font-cell rounding.
- Implement custom palette/theme loading; Palette now validates required colors and exactly 16 non-null ANSI entries.
- Make selecting installed Nerd Font fallbacks and copy-on-select easy through config.
- ShellIntegrationFilter hold-limit prefix counting is cosmetic cleanup.

Any-time performance work: remove unused style-array allocation during text-only extraction; reduce font-cache autoboxing; benchmark shell integration filtering; add frames-painted evidence to Bench (MB/s alone measures ingestion, not frame latency); review Windows cmd/type path quoting.

## 7. Decisions and implementation deviations

- Keep JediTerm, own renderer, two one-way modules, no emulator abstraction/plugin API.
- Option+Left/Right remains ESC[1;3D / ESC[1;3C, not ESC b / ESC f.
- OSC8 schemes remain http/https/ftp/mailto; detected text may include file URLs. Refused OSC8 targets do not fall through to text detection.
- macOS Command-click links takes precedence over program mouse reports; Shift provides local mouse behavior; horizontal Shift-wheel on macOS is ignored.
- The user's approved Phase 1 direction and Plan 3 go-ahead were used for execution; a supplemental written design records concrete app contracts.
- On Linux/Windows, cmd already means Ctrl+Shift. Defaults explicitly written cmd+shift add Alt to avoid collisions; user overrides parse literally and collisions are errors. macOS defaults are unchanged.
- Settings/reload and saved configuration were deferred from Plan 3 to Plan 4a and are now implemented. Plan 3.5 supplied coordinated built-in themes and macOS title-bar work; automatic system appearance remains a later Plan 4 deliverable.
- Four direct multi-pane carryovers were included; the remaining hardening list above is follow-up scope.
- Native acceptance is user-run per newly merged AGENTS.md. No agent GUI launch or benchmark followed discovery of that rule.
- The user selected the local `~/projects/tabler-icons` repository. Seven outline assets and full MIT license are bundled; SOURCE.txt records the source commit and theme-color adaptation.

## 8. Workflow and historical Plan 3 verification

Superpowers spec/plan → task implementer with TDD → separate task review → scoped fix/re-review → final branch review. Reviewed commits: models `a75d5fa`; terminal hooks `56b251d`; lifecycle fixes `73f105c`; desktop `acfc79f`; desktop review fixes `6fac903`/`dcc0b00`; final minor fixes `dda35e2`. Task reviews and final whole-branch review approved. Final fixes synchronize appearance menu state and make the forced-close test wait for installed signal traps. A mutation check confirmed that removing force fallback makes the latter test fail; production cleanup was restored before verification.

Root final verification on `dda35e2`: all 8 Gradle tasks executed successfully in 8 seconds; 277 tests, zero failures/errors, one expected font skip. Source-hygiene and `git diff --check` passed. No compiler warnings. Final reviewer compared the exact default key catalog with the parent spec and found it aligned; selected shortcut regressions for live overrides belong in Plan 4, without duplicating source constants in tests.

Plan 3 was merged locally and its feature worktree/branch removed after merged checks passed (277 tests, zero failures/errors, one expected skip). Plan 3.5 is now integrated on main; continue native acceptance from `/Users/dustin/projects/moray`, retaining the daily-use gate.

## 9. Execution rulings preserved from the completed Plan 3 ledger

These are historical Plan 3 scope decisions. The user's subsequent Plan 3.5 amendment above changes the sequencing of built-in theme work.

- The existing approved Phase 1 design plus the user's Plan 3 go-ahead authorizes execution; use a supplemental execution design instead of restarting approval — avoids repeating settled layout decisions — costs rework if the detailed choices differ from user intent.
- Non-mac defaults containing explicit cmd+shift add Alt because cmd already means Ctrl+Shift — prevents duplicate destructive shortcuts — costs non-mac shortcut familiarity; macOS unchanged.
- Settings/reload remain visibly disabled and status says Built-in defaults until Plan 4 — no configuration subsystem in Plan 3 — costs waiting for settings until the next planned milestone.
- Light/dark chrome is selectable in Plan 3; automatic system appearance remains Plan 4 with config — keeps scope aligned — costs manual appearance selection temporarily.
- Fold four immediate STATUS carryovers into Task 2 (lost link gesture, alternate-buffer invalidation, prompt pruning, child termination), retain remaining performance/selection findings for follow-up — these directly affect reliable multiple panes — costs additional Task 2 work and later hardening before Phase 1 acceptance.
- GUI smoke and final benchmark are user-run under newly added AGENTS.md — respects desktop-operation restriction — costs leaving visual/performance acceptance pending after headless completion.
- Keep disabled Settings/Reload tooltips user-facing, without internal Plan 4 numbers — developer instructions exclude implementation details from product flows and implementation already explains unavailable config — costs losing an internal milestone reference in tooltips, which remains in docs. Corrected ambiguous plan phrase before re-review.

## 10. Plan 3.5 execution rulings

- The user's go-ahead authorizes implementation of the requested title/theme direction with reviewable initial colors — avoids repeated approval of the already requested milestone — costs visual rework if the initial palette differs from intent.
- Start with slim custom title surface and separate tabs unless the pending optional layout answer changes it — limits geometry changes before toolbar discussion — costs a later layout revision if tabs should be integrated.
- Deliver title/themes as 3.5a and discuss toolbar artwork as 3.5b before implementing that selection — honors the explicit request to discuss toolbar appearance — costs leaving existing icons during the first deliverable.
- Reuse supported decorated-frame macOS full-content properties and FlatLaf bounds rather than add JBR API/native dependencies — verified in pinned source and retains native behavior — costs revisiting integration if native user checks reveal a platform limitation.

The third ruling was superseded when the user selected toolbar B: Task 4 delivers the chosen artwork in this same milestone. The later screenshot explicitly resolves the title layout: integrated title-bar tabs replace that initial layout.

Task reviews: palettes `09e382e`, application themes `6bdd298`, title surface `7833692`, toolbar `116709b` approved. Final whole-branch review covered `942f5f7..0b8461b`. Its only minor finding was closed by `f487631`: the rendered icon test now distinguishes soft fields from enclosed face fills. A mutation removing the four face fills failed the new test while the earlier aggregate test still passed; all resources were restored. The scoped fix review approved with no residual findings. The alpha-specific test runs in dark mode against shared SVG opacity layers; existing color tests cover both themes.

Root final verification on `f487631`: `./gradlew check --rerun-tasks` ran all eight tasks in 11 seconds, with 299 tests, zero failures/errors and one known font skip. Source hygiene and `git diff --check` passed; no compiler warnings. Production code/resources are unchanged since `116709b`. The plan-specific scratch workspace is removed after final review; this handoff, plan checkboxes, committed previews and git history preserve the result.

The user selected local integration. The completed feature worktree and branch are cleaned up after merged verification succeeds. No GUI or benchmark was launched and no remote is configured. Native title-bar/appearance checks and the terminal daily-use gate remain open.

## 11. Screenshot revision execution rulings

- Treat the supplied mock and explicit exact-match instruction as the approved visual design, superseding earlier colored-toolbar and separate-title decisions — avoids asking approval for the mock the user just selected — costs rework for any mistaken measurements.
- Interpret the144dpi screenshot as2x and convert its monitor ICC colors to sRGB for Swing values — aligns logical sizes and displayed colors — costs refinement if the mock's intended logical scale differs.
- Add public jbr-api1.9.0 native title-height integration with fallback — exact54px native-control placement needs more than oldfullcontentproperties — costs native-platform validation and a small app dependency.
- Use sampled visible green/blue/cursor colors and refine toolbar text to11.5px after actual-render comparison — user’s exact mock takes precedence over the initial retain-ANSI/12px approximation — costs changing those built-in dark ANSI accents and potential font refinement on other systems.

Both task reviews and the final whole-branch review (`182b9fb..be39bbf`) approved without actionable findings. The screenshot-only production/test commit was `63a3325`; the follow-up below changes it. Fresh root verification and independent reviewer XML checks confirm 308 total tests, 307 passed, one known skip. This plan’s scratch workspace is removed after review; the committed plan, comparison report, previews and this handoff retain the evidence and rulings. The feature worktree remains available for user inspection and integration approval.

## 12. Compact tab follow-up

Task 1 (`0c4ab43`) approved without findings: shared live native/Swing height, current-window numeric control, real platform shortcut/override dispatch, 80 app tests. Task 2 (`e46eaae`) implements 180ms entry/underline motion; nine deterministic real-strip tests cover intermediate/final rendered positions, retargeting, metadata continuity, overflow/resize/removal and idle/lifecycle cleanup. Fresh root full verification is recorded at the top. The updated previews show settled 38px geometry; the original 54px mock remains a historical reference. Persistence remains Plan 4.

- Treat the user's concrete follow-up as authorization to implement these reversible refinements — avoids repeating approval already given — costs visual rework if chosen defaults differ from intent.
- Default to 38px height and 180ms motion with 3.5% overshoot — makes the row materially shorter and movement quick with a small settle — costs tuning after native viewing.
- Provide a live current-window height control; persistence stays Plan 4 — keeps existing session-only appearance behavior consistent — costs reapplying height after restart.

Final whole-branch review (`182b9fb..7449ac4`) found one integration issue: the active title’s preferred width changed tab-strip allocation and prematurely settled animation. The single fix wave `20764b3` preserves motion when allocation changes leave tab coordinates valid, and adds three real MacTitleBar/JRootPane regressions. Scoped review (`7449ac4..20764b3`) marked it addressed with no new findings. Twelve motion tests now pass; root fresh final verification is 328 total, 327 passed, one known skip. Native smoothness, controls and physical keyboard-layout acceptance remain user-run. The plan scratch workspace is removed after review; committed reports, plan, source and this handoff preserve the result. At that milestone the feature remained in `.worktrees/mock-ui`; section 14 records its subsequent integration and removal.

## 13. Reverse close animation

User-requested bounded follow-up `0f4cd74` reverses the existing motion for visible tab closure. The tab and shell leave the live model immediately; a disabled departing entry contracts while following tabs fill its space and the underline targets the new selection. Closing during entry uses its current width; consecutive closes have independent deadlines. Completed, hidden, reordered, cramped/overflow and disposed departing visuals are removed. Final-tab closure is not delayed.

Four new real MacTitleBar/JRootPane regressions cover active/inactive closes, closing during entry, consecutive deadlines and cleanup. Initial three regressions failed against the prior behavior; the empty-owner timer regression also failed before its fix. Final covering tests:27 passed. Fresh full `./gradlew check --rerun-tasks`:332 total,331 passed, one known font skip; all eight tasks executed, no warnings. Source hygiene and diff checks pass. Independent review of `600c378..0f4cd74` approved without actionable findings; native reverse-motion acceptance remains user-run. This bounded extension updates the existing spec without a new subsystem or plan.

## 14. Approved integration and Plan 4 start

The user explicitly approved merging all current UI work into main and beginning Plan 4 with saved settings and live reload. Main received the complete UI branch at `1bd8b49`. Fresh merged verification passed 332 tests (331 passed, one known font skip), and the completed mock-ui worktree/branch were removed. Plan 4a then started in the isolated `.worktrees/plan-4` worktree. Native UI/benchmark/daily-use acceptance remains open and is not implied by merge approval.

Plan 4a covers existing live controls only: tab height, toolbar/status, font size, built-in theme and shortcut overrides. Runtime View choices remain temporary; saved defaults are in the user-edited file. Additional terminal options, custom themes, system appearance and packaging follow in separate runnable slices. TomlJ 1.1.1 is selected for precise TOML source positions.

## 15. Plan 4a implementation and execution rulings

Parser/paths/CLI: `8c6fa0e`. File service/template: `e25b4c8`. Live application integration: `a18de61`. Review fixes: `9c0ae64` (all modifiers in rendered toolbar hints and removal of obsolete configuration tooltip). Task reviews are complete with no open findings. Full check after fixes executed all eight Gradle tasks: app 158 passed; terminal 235 passed and one known skip. Source hygiene and diff checks passed. No terminal-module changes were needed.

The configuration service retains its parse platform for application of shortcuts. Existing Action-backed toolbar hints and pending-pane hooks were reused; the toolbar formatter was corrected after review exposed its omission of extra macOS modifiers. Native editor, real window and daily-use acceptance remain user-run. The implementation remains in the Plan 4 worktree until separately integrated.

- Ruling: The user approved local UI merge and starting the proposed first Plan4 deliverable; execute without repeating design approval — follows explicit go-ahead — costs rework if detailed choices differ from intent.
- Ruling: Split Plan4 into runnable stages, first covering existing live controls and config lifecycle — follows the accepted saved-settings/live-reload first step and repository one-deliverable-per-plan rule — costs waiting for additional terminal options, custom themes, automatic appearance and packaging.
- Ruling: Saved defaults are file contents; runtime View choices remain temporary and unrelated reloads preserve them — respects the parent prohibition on unasked config writes — costs editing the file to persist a menu adjustment.
- Ruling: Select TomlJ1.1.1 for positioned diagnostics instead of Jackson binding — upstream API provides parser and key positions directly, an alternative allowed by the parent — costs one parser dependency and validation code.
- Ruling: Invalid or colliding shortcut values fall back to the entire default binding map, while unknown action names warn and are ignored — bindings are mutually constrained and per-action fallbacks can introduce collisions — costs resetting valid custom bindings in the same invalid map until corrected.
- Ruling: Expose the service’s immutable parse platform to the controller and use it to materialize snapshot bindings — resolves the task review’s cross-platform collision concern without changing immutable snapshot data — costs a small package-private accessor.

Final whole-branch review (`1bd8b49..b23ee71`) found one P2 test portability issue: Windows-invalid filename characters and Unix-only expected path separators. The single combined fix wave `acd5ea3` uses portable fixtures and native path formatting while retaining plain-text safety assertions. Scoped re-review approved with no new findings. Fresh final `./gradlew check --rerun-tasks` ran all eight tasks in 10 seconds: 394 total, 393 passed, one known skip, zero failures/errors. Source hygiene and diff checks passed. Production code remains `9c0ae64`; the final fix changed tests only. Native and Windows execution remain unverified. This plan’s scratch workspace is removed after preserving these decisions and evidence; the feature branch/worktree remain available for integration.
