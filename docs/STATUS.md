# Moray — Status and Handoff

**As of:** 2026-09-11 · code on `main` last changed at `0ce4add` · `./gradlew check`: 216 tests, 0 failures, 1 skipped (expected; see "Known skips")

> **Plan 3 is in progress elsewhere.** Branch `codex/plan-3-app-chrome`, checked out in the worktree `.worktrees/plan-3` (git-ignored), holds a plan-3 design (`docs/superpowers/specs/2026-09-11-moray-plan-3-app-chrome-design.md`) and plan (`docs/superpowers/plans/2026-09-11-moray-plan-3-app-chrome.md`, 3 tasks), with its subagent-driven ledger in that worktree's `.superpowers/sdd/`. At the time of writing, its Task 1 had started. Continue there rather than starting plan 3 again. Section 5 lists "Plan 3" items that its plan does not yet cover. Fold them in, or leave them for a follow-up plan.

Read this first. It is written for whoever — person or coding agent — picks up the work next. Conventions for agents are in [`AGENTS.md`](../AGENTS.md); build and run instructions are in [`README.md`](../README.md).

## 1. What Moray is and where it is going

Moray is a cross-platform (macOS, Linux, Windows) terminal workstation written in Java Swing, laid out like MobaXterm. It is being built to replace the user's current daily driver, `~/projects/conch` (Rust + Tauri + xterm.js), which stays in daily use until Moray passes the switch-over test in the spec (§9).

Product phases (spec §1): **1. the terminal** (in progress) → 2. SSH session management → 3. vault → 4. SFTP → 5. tunnels → plugins and an editor. Tested non-UI Java from `~/projects/termlab-bundle` (MINA SSH client, vault crypto, host store) is to be reused from phase 2 on.

Phase 1 is delivered as four implementation plans, each ending in software the user can run:

| Plan | Scope | State |
|---|---|---|
| 1 — Terminal core | build, CI, renderer (ligatures, fallback fonts, colours, cursor), PTY session, keyboard, one-window app, benchmark | **Done**, merged (`6daa61f`..`d0ccbfb`) |
| 2 — Terminal completeness | scrollback, mouse reporting, selection/copy/paste, search API, shell integration (OSC 7/133/8), exit message | **Done**, merged (`196e24d`..`0ce4add`) |
| 3 — App chrome | spec §5–§6: menu bar, MobaXterm-style toolbar, tabs, split tree, find bar, status bar, configurable keymap (§5.1), FlatLaf look, context menu, `dim_inactive_panes` | **In progress** on `codex/plan-3-app-chrome` (see the banner above) |
| 4 — Config and packaging | spec §7: per-OS paths (`AppDirs`), TOML config, themes, live reload, error reporting; app logging; macOS `.app` bundling the JBR (§9) | **Not written yet** |

Documents: spec `docs/superpowers/specs/2026-09-10-moray-phase-1-terminal-design.md` (binding authority); plans in `docs/superpowers/plans/`.

## 2. What works now

A single window running the user's login shell, drawn by Moray's own Swing view on top of `jediterm-core` 3.76 and pty4j 0.13.10:

- Rendering: ligatures (JetBrains Mono), per-code-point fallback fonts (effective for Nerd Font icons), wide characters, 256-colour and truecolour, SGR styles, cursor shapes (DECSCUSR honoured, DECSCUSR 0 / RIS restore the configured shape), coalesced repaint.
- Keyboard: full xterm encoding, application cursor mode, Option-as-Meta (left Option by default), Ctrl chords, bracketed paste.
- Scrollback: wheel and trackpad (fractional movement adds up to whole notches), Shift+PageUp/PageDown, a scroll indicator; the view stays put while output arrives; typing or pasting returns to the live screen.
- Mouse reporting for tmux/vim/htop (X10/normal/button/any-motion, SGR and legacy), including right-click; Shift keeps the mouse local; clicks above the live screen are not reported.
- Selection: drag, double-click word (paths and URLs count as one word), triple-click logical line, Alt/Option-drag block; ⌘C / ⌘V (Ctrl+Shift+C/V on Linux/Windows); copy joins soft-wrapped rows and trims trailing spaces.
- Search API (`TerminalView.find/findNext/findPrevious/clearFind`, plain or regex, case option) with highlighted matches — no find bar UI yet (plan 3).
- Shell integration: OSC 7 working directory (`TerminalSession.workingDirectory()`), OSC 133 prompt marks with ⌘↑/⌘↓ jumps, OSC 8 hyperlinks and detected URLs opened with ⌘-click (Ctrl-click on Linux/Windows); OSC 8 targets limited to http/https/ftp/mailto.
- Exit: the window stays open showing `[process exited with code N]` until a key is pressed; a shell that fails to start shows an error dialog.
- Benchmark: `./gradlew :moray-app:bench` — 41.5 MB/s at the end of plan 1 (gate 35 MB/s; target 45).

## 3. Open items before starting plan 3

In recommended order:

1. **Fix the ⌘-click `openingLink` flag (real bug, small).** In `TerminalView.handleMouse`, a ⌘-click on a link sets `openingLink` so that click's drag and release are swallowed. It is cleared only by a later release. If the release never arrives (focus loss, a second press first), the flag later swallows an unrelated gesture's drag/release, and a mouse-reporting program can be left with a press that is never released. Recipe: clear `openingLink` on every `PRESSED` that is not itself a ⌘-press on a link; add a test (⌘-press on a link, then a plain press + drag + release with SGR mouse on → the plain gesture's release is reported). macOS-only path.
2. **The user's manual check** (no agent may launch the GUI — see `AGENTS.md`). Ask the user to run `./gradlew :moray-app:run` and check:
   - scroll back with the trackpad, then run `yes | head -500` — the view holds still; typing returns to live;
   - select (drag / double / triple / Option-drag), ⌘C, ⌘V a multi-line block;
   - inside tmux: click panes, drag pane borders, right-click; Shift+drag selects locally; a diagonal trackpad swipe must not send arrow keys;
   - ⌘-click the URL from `echo https://example.com`, and the word `link` from `printf '\e]8;;https://example.com\e\\link\e]8;;\e\\\n'`;
   - prompt jumps after enabling shell integration in bash: `PROMPT_COMMAND='printf "\e]7;file://%s%s\e\\\\" "$HOSTNAME" "$PWD"; printf "\e]133;A\e\\\\"'`, then ⌘↑/⌘↓;
   - vim: entering and leaving insert mode switches the cursor between beam and block;
   - `exit`: the message stays until a key is pressed, then the window closes;
   - fonts: ligatures (`echo '-> => != ==='`), Nerd Font icons, CJK and emoji.
3. **Re-run the benchmark** (`./gradlew :moray-app:bench`, opens a window for a few seconds) — skipped in plan 2 because the Minecraft launcher was open. `ShellIntegrationConnector` now sits on the read path; record the number against the 41.5 MB/s baseline.
4. **CI has never run.** The repo has no remote. `.github/workflows/ci.yml` runs `./gradlew check` on macOS, Ubuntu and Windows with JBR 25 (`actions/setup-java@v6`, distribution `jetbrains`). Linux headless fonts, the Windows ConPTY path and the Nerd-Font tests' skip-on-missing-font path are unverified until it runs. Ask the user before adding a remote or pushing.

## 4. Known limitations (by design or deferred)

- **Absolute rows** (`discardedLines + historyLines + screenRow`) are stable only while output streams. Erasing the scrollback (ED 3 / RIS) or a width change (jediterm-core reflows soft-wrapped lines) clears the selection, search matches and prompt marks and returns the view to live (`Listener.scrollbackReset`, `TerminalSession.resize`).
- **Alternate screen:** jediterm-core reports 0 history lines while the alternate buffer is active, so its absolute rows overlap the main screen's; a selection or match kept across a screen switch can point at the wrong line. Fix in plan 3: clear selection and matches on an alternate-screen switch.
- Search matches do not span soft-wrapped rows (per physical row).
- Strikethrough (SGR 9) is not shown — jediterm-core 3.76's `TextStyle` has no such option.
- Fallback fonts on macOS take effect only for glyphs the primary font truly lacks (JBR's cascade draws CJK/emoji anyway).
- Shortcuts are hard-wired in `TerminalView.handleViewShortcut` until plan 3's keymap: copy, paste, previous/next prompt, Shift+PageUp/PageDown.
- On macOS, Shift+wheel is ignored (macOS delivers horizontal scrolling that way); Shift+PageUp still scrolls locally inside mouse-reporting programs.

### Known skips

- `FontSetTest.fallsBackWhenPrimaryCannotDisplay` skips on this Mac (no code point differentiates the primary and fallback fonts it probes). Real fallback coverage is `FontSetTest.aNerdFontIconFallsBackToAConfiguredNerdFont` and `RunBuilderTest.aFontChangeStartsANewRun`, which run wherever a Nerd Font is installed and skip otherwise.

## 5. Deferred findings, sorted by where they belong

From the per-task and final reviews of plans 1 and 2. None blocks the current state.

**Plan 3 (app chrome / view behaviour)**

The `codex/plan-3-app-chrome` plan already covers several of these items: find off the Event Dispatch Thread, keeping find's regex error, and a minimum pane size. Shortcuts after exit move into the keymap. New tabs start in the OSC 7 directory, and an unusable directory shows an error. Its plan does **not** mention these: the gesture owner and the `openingLink` fix, clearing on an alternate-screen switch, stopping timers, hang-up on close, the bounded logical-line walk, and pruning `promptRows`.
- Replace the two gesture flags (`capturingSelection`, `openingLink`) with a press-time gesture owner (NONE / LOCAL / REPORT). Closes: a reported press followed by a Shift drag/release is re-routed locally (program sees a stuck button); the release override does not check which button; ⌘-click on a non-link followed by a drag extends the existing selection; the `openingLink` sticking bug (item 3.1) if not already fixed.
- Clear selection and matches on an alternate-screen switch (section 4).
- One bounded shared "logical line" walk for `urlAcrossWrappedRows` and `lineSelection` (both walk unbounded under the buffer lock; one huge wrapped line stalls the reader thread on ⌘-click).
- Find bar: run `find` off the Event Dispatch Thread or bound it (find-as-you-type with a pathological regex freezes the UI); keep the regex error in the find bar's state (`findNext` after an invalid regex returns `(0,0,null)`).
- OSC 7 host is ignored; check the host (or that the directory exists) before starting new tabs in that directory.
- Frame and blink timers never stop (idle wakeups per pane); start the frame timer on the first dirty mark.
- `GridSize` allows 1×1 but `JediTerminal.resize` clamps to its own minimum — set a window minimum size.
- `PtyConnector.close` uses `destroy()` (SIGTERM; interactive bash ignores it) — for closing tabs use hang-up, then SIGKILL after a timeout.
- ⌘V after exit still pastes into the finished program (shortcuts run before the exit check) — resolve in the keymap.
- `openInBrowser` runs `Desktop.browse` on the Event Dispatch Thread.
- A reported wheel event sends one report whatever its notch count.
- The selection outlives content overwritten on the live screen; dragging after a double-click extends by characters, not words; a selection ending on half of a wide character drops it.
- `promptRows` grows forever (copy-on-write list); prune it in `linesDiscardedFromHistory`.
- `highlights()` scans every match each frame; matches are sorted by row, so binary-search the visible range.
- Every mouse event takes a full snapshot and repaints, including motion while reporting.
- Tests missing for `removeNotify` / `focusLost` cleanup.

**Plan 4 (config, logging, packaging)**
- App logging: replace `printStackTrace` in `TerminalSession.readLoop` and the silent catches (`write` after exit, clipboard, browser).
- Child environment: scrub launcher variables (`TERM_PROGRAM`, `TMUX`, iTerm's) and set a UTF-8 `LANG` for a Dock-launched `.app`.
- `FontSet` rounds the cell width in logical pixels (JetBrains Mono 14 pt: 8.4 → 8 px); revisit with `line_height`.
- Theme loading: bounds-check `Palette.indexed` and null-check palette colours.
- Config keys for `copy_on_select` and for naming a Nerd Font fallback (default list names "Symbols Nerd Font Mono", which is often not installed).
- `ShellIntegrationFilter`'s 4096-character hold limit counts the `ESC]7;` prefix (cosmetic).

**Any time (performance / tidiness)**
- Unused `TextStyle[]` allocations in `SelectionText.extract`, `WordBoundaries.wordAt`, `RowText.of` (they call `RunBuilder.readCells` for characters only).
- `FontSet`'s code-point cache autoboxes.
- `Bench`: add a frames-painted counter (the MB/s figure measures ingestion; slow painting does not lower it); Windows `cmd /c type` path quoting.
- Measure `ShellIntegrationConnector` (per-character filtering, `pending.delete(0, n)`) with the benchmark.
- Comments: explain the nested buffer lock in `snapshot(long)` + `ScreenSnapshot.capture`; reword the "under the buffer lock" comment on `linesDiscardedFromHistory` as an assumption.

**Test gaps (coverage only):** astral characters in search; highlights at edge columns / overlapping; scroll-indicator geometry for large scrollback; `linkAt` on continuation rows and 3+-row URLs; empty-scrollback `Viewport`; `MouseRouting` middle button; `copyOnSelect`; alternate-screen wheel → arrows; reported wheel; paste returning to live; Shift+PageDown; find wrap-around; match colours.

## 6. Decisions that constrain future work

- **Option+Left/Right** send `ESC[1;3D` / `ESC[1;3C` (xterm encoding) — decided by the user on 2026-09-10; the user's Homebrew bash binds them to word motion. Do not change to `ESC b` / `ESC f`.
- **OSC 8** link targets: only http, https, ftp, mailto; `file:` only for URLs detected in visible text; a refused OSC 8 cell does not fall back to text detection.
- **Mouse:** Shift keeps the mouse local; a started local selection stays local through its release; clicks on rows above the live screen are not reported; wheel movement is counted in whole notches from `getPreciseWheelRotation()`; on macOS Shift+wheel is ignored; on macOS ⌘-left-click on a link opens it even when a program wants the mouse.
- **Search** copies lines under the buffer lock and runs the regex outside it.
- **URL detection** joins soft-wrapped rows.
- **Exit:** the window stays open on the exit message; the next key closes it.
- **Hard-wired shortcuts** are temporary; plan 3's keymap owns them (spec §5.1 table).
- Spec guardrails still apply: no redesign of phase-1 structure until the §9 switch-over test passes; no interface without two real implementations; JediTerm is the emulator, full stop.

## 7. How the work was done

Each plan: brainstorming → spec → `superpowers:writing-plans` → `superpowers:subagent-driven-development` (pre-flight conflict scan; one implementer subagent per task with the task text extracted as a brief; a task reviewer per task; fix rounds; a final whole-branch review on the most capable model; one fix wave). The per-plan ledgers lived in `.superpowers/sdd/` (git-ignored scratch) and were deleted after merging; this file and `git log` are the record. Notable events: a model-service outage stalled plan 2's Task 2 review four times (resumed next day); several plan-text defects were caught in review and fixed with recorded rulings (listed per plan in each plan's status banner).

## 8. Suggested next steps

1. Fix item 3.1, with its test. It touches `TerminalView.handleMouse`, and plan 3's Task 2 also changes `TerminalView`. So either add the fix on `codex/plan-3-app-chrome`, or fix it on `main` and merge `main` into that branch.
2. Hand the user the manual check (3.2) and the benchmark (3.3); ask about a remote for CI (3.4).
3. Finish plan 3 on `codex/plan-3-app-chrome`, following its ledger in `.worktrees/plan-3/.superpowers/sdd/`. Then decide which uncovered "Plan 3" items from section 5 go into it or into a follow-up plan.
4. Then plan 4 (spec §7 and the "Plan 4" items), then the §9 definition of done and the two-week switch-over test against conch.
