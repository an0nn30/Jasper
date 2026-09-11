# Moray — Status and Handoff

**As of:** 2026-09-11. Plan 3 implementation is on `codex/plan-3-app-chrome` in `.worktrees/plan-3`; final reviewed code `dda35e2`. All task reviews and final whole-branch/fix reviews passed, with no unresolved review findings. Root fresh `./gradlew check --rerun-tasks`: **277 tests, 0 failures/errors, 1 expected font skip** (276 passed; 46 app + 231 terminal). No merge to main or remote push has occurred.

Read [AGENTS.md](../AGENTS.md) for repository rules, [README.md](../README.md) for running, and the [Phase 1 design](superpowers/specs/2026-09-10-moray-phase-1-terminal-design.md) for binding product requirements. GUI launches and benchmarks are user-run only.

## 1. Goal and delivery phases

Moray is a Java Swing terminal workstation with a MobaXterm-style layout, built to replace `~/projects/conch` as the daily terminal. Product phases: terminal → SSH session management → credential vault → SFTP → tunnels → plugins and editor. Reuse tested non-UI Java from `~/projects/termlab-bundle` in later phases. Do not redesign the terminal foundation before the Phase 1 switch-over test.

| Plan | State |
|---|---|
| 1 — Terminal core | Complete on main (`6daa61f`..`d0ccbfb`) |
| 2 — Terminal completeness | Complete on main (`196e24d`..`0ce4add`) |
| 3 — App chrome | Implemented and reviewed on feature branch; all automated checks passed; native acceptance pending |
| 4 — Config and packaging | Not written yet |

Plan 3 design: [application design](superpowers/specs/2026-09-11-moray-plan-3-app-chrome-design.md). Execution: [implementation plan](superpowers/plans/2026-09-11-moray-plan-3-app-chrome.md). The per-plan scratch workspace was removed after final review; this handoff, completed plan checkboxes and git history preserve the record. Continue from this reviewed branch; do not start Plan 3 again.

## 2. Implemented behavior on the Plan 3 branch

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

Settings and Reload config are visible but disabled; status says Built-in defaults. No configuration file is read or written. Chrome choices are session-only. Plan 4 owns configuration, persistence, automatic system appearance and packaging.

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

1. Choose whether to merge the reviewed feature branch locally after inspecting it. No remote exists; ask before configuring one or pushing.
2. User-run [Plan 3 acceptance checklist](superpowers/plans/2026-09-11-moray-plan-3-manual-check.md): native windows/menu/Quit, split/zoom/focus/dividers, tab gestures, find, clipboard, links, mouse reporting, directory inheritance and chrome readability. Headless tests do not establish these native visual results.
3. User-run benchmark after integration, only with no game or VM running. Historical Plan 1 result: 41.5 MB/s. A Plan 2 baseline measured during this session before newly added AGENTS restrictions were discovered: **35.9 MB/s** (105.5 MB in 2.94 s). No post-integration benchmark has run. Gate ≥35 MB/s, target ≥45 MB/s.
4. CI has never run: no git remote exists. Workflow covers macOS, Ubuntu and Windows with JBR25. Ask before adding a remote or pushing. Windows ConPTY/forced-close and Linux desktop/font behavior remain unverified on their native systems.
5. After Plan 4, satisfy the Phase 1 manual checklist and two-week Moray-only trial. Record every reason to reopen conch and fix the blockers before SSH begins.

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

## 6. Plan 4 and later

Plan 4: AppDirs/per-OS paths, TOML diagnostics with line numbers and last-good retention, live reload, themes, settings-file creation, --config, app logging and macOS .app bundling JBR. Carryovers:

- Replace printStackTrace/silent I/O catches with appropriate app logging/feedback.
- Scrub launcher environment variables (TERM_PROGRAM, TMUX, iTerm variables) and provide UTF-8 LANG for Dock launch.
- Implement line_height; revisit logical-pixel font-cell rounding.
- Validate palette color/index inputs and implement palette/theme loading.
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
- Settings/reload and persisted config remain Plan 4. Light/dark chrome selection works now; automatic system appearance waits for Plan 4.
- Four direct multi-pane carryovers were included; the remaining hardening list above is follow-up scope.
- Native acceptance is user-run per newly merged AGENTS.md. No agent GUI launch or benchmark followed discovery of that rule.
- The user selected the local `~/projects/tabler-icons` repository. Seven outline assets and full MIT license are bundled; SOURCE.txt records the source commit and theme-color adaptation.

## 8. Workflow and next action

Superpowers spec/plan → task implementer with TDD → separate task review → scoped fix/re-review → final branch review. Reviewed commits: models `a75d5fa`; terminal hooks `56b251d`; lifecycle fixes `73f105c`; desktop `acfc79f`; desktop review fixes `6fac903`/`dcc0b00`; final minor fixes `dda35e2`. Task reviews and final whole-branch review approved. Final fixes synchronize appearance menu state and make the forced-close test wait for installed signal traps. A mutation check confirmed that removing force fallback makes the latter test fail; production cleanup was restored before verification.

Root final verification on `dda35e2`: all 8 Gradle tasks executed successfully in 8 seconds; 277 tests, zero failures/errors, one expected font skip. Source-hygiene and `git diff --check` passed. No compiler warnings. Final reviewer compared the exact default key catalog with the parent spec and found it aligned; selected shortcut regressions for live overrides belong in Plan 4, without duplicating source constants in tests.

Next: run user acceptance, choose whether to merge locally, then implement Plan 4 and complete the daily-use gate. Keep the feature worktree/branch until the user chooses integration.

## 9. Execution rulings preserved from the completed ledger

- The existing approved Phase 1 design plus the user's Plan 3 go-ahead authorizes execution; use a supplemental execution design instead of restarting approval — avoids repeating settled layout decisions — costs rework if the detailed choices differ from user intent.
- Non-mac defaults containing explicit cmd+shift add Alt because cmd already means Ctrl+Shift — prevents duplicate destructive shortcuts — costs non-mac shortcut familiarity; macOS unchanged.
- Settings/reload remain visibly disabled and status says Built-in defaults until Plan 4 — no configuration subsystem in Plan 3 — costs waiting for settings until the next planned milestone.
- Light/dark chrome is selectable in Plan 3; automatic system appearance remains Plan 4 with config — keeps scope aligned — costs manual appearance selection temporarily.
- Fold four immediate STATUS carryovers into Task 2 (lost link gesture, alternate-buffer invalidation, prompt pruning, child termination), retain remaining performance/selection findings for follow-up — these directly affect reliable multiple panes — costs additional Task 2 work and later hardening before Phase 1 acceptance.
- GUI smoke and final benchmark are user-run under newly added AGENTS.md — respects desktop-operation restriction — costs leaving visual/performance acceptance pending after headless completion.
- Keep disabled Settings/Reload tooltips user-facing, without internal Plan 4 numbers — developer instructions exclude implementation details from product flows and implementation already explains unavailable config — costs losing an internal milestone reference in tooltips, which remains in docs. Corrected ambiguous plan phrase before re-review.
