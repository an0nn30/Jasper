# Moray Plan 3 — Application Design

Date: 2026-09-11
Status: Execution design under the approved Phase 1 specification and user's Plan 3 go-ahead.
Authority: `2026-09-10-moray-phase-1-terminal-design.md`, particularly §§3, 5, 5.1, 6 and 9.

Execution update: newly merged AGENTS.md reserves GUI launches and benchmarks for the user. Agent verification remains headless. STATUS.md carryovers included here are lost link-gesture reset, alternate-screen row invalidation, pruning expired prompt marks, and reliable child termination on pane close. Other performance/selection refinements remain recorded for follow-up hardening.

## Outcome and scope

Turn the existing single terminal pane into a daily-use terminal application: multiple windows, reorderable tabs, right/down splits, pane navigation and zoom, application shortcuts, menus, toolbar, find bar, status bar, and context menus. Keep the emulator, PTY and own renderer. No SSH, sidebar, plugin API, layout restoration, configuration parser or packaging in this plan.

The already approved desktop layout stays menu → toolbar → tabs → pane area → status bar. The find bar belongs above its terminal pane. FlatLaf supplies desktop chrome; terminal colors continue to use the current palette. Use FlatLaf 3.7 and flatlaf-extras 3.7 for bundled SVG icons. The seven toolbar buttons are New tab, New window, Split (right/down popup), Zoom pane, Find, Settings, Reload config. Settings and Reload config are visible but disabled with explanatory tooltips until Plan 4; no fake config success indicator or silent no-op. Status says Built-in defaults and explains configuration is not loaded yet. Toolbar modes and status visibility are functional in View; light/dark chrome choices are functional but not persisted. Plan 4 owns automatic system appearance and TOML persistence.

## Approach

Use a small application controller and plain models. Extending Main into one large event-handler class would mix process ownership, layout and actions. A generic docking or plugin framework would introduce unused concepts. Instead, Main initializes look-and-feel and launches a controller; each window owns its tabs, each tab owns a split tree and pane objects, and each pane owns exactly one session and view.

No public terminal API exposes JediTerm. The terminal module remains independent of app actions/config files. Standard JDK functional callbacks are sufficient for command interception; no new service interfaces without two real implementations.

## Models and ownership

`SplitTree` is independent of Swing. Leaves are UUID pane IDs. Branches have UUID identity, orientation RIGHT/DOWN, two children, and a first-child ratio. Splitting replaces the focused leaf with a branch containing the existing and new leaves, then focuses the new leaf. Closing collapses the parent to the surviving sibling; closing the only leaf yields an empty tree. Focus is always a living leaf or absent on an empty tree. Zoom is a view of the focused leaf and never destroys the tree. Splitting unzooms. Closing a pane clears zoom. Directional navigation uses normalized leaf rectangles and chooses the nearest neighbor in the requested direction with perpendicular overlap; no wrap. Ratios are clamped to 0.1–0.9 and retained across zoom/unzoom, tab changes and renderer rebuilds. Focus and navigation while zoomed select and show the new focused pane without losing hidden sessions.

`TabState` keeps the user rename separately from the shell title. Default tab title is the focused pane's nonblank OSC title, otherwise the working-directory basename, otherwise Terminal. User renaming survives shell title and directory changes. Clearing a rename restores automatic naming. Selecting a tab restores that tab's focused pane. Drag reorder preserves selected tab identity; middle-click and close buttons close the clicked tab.

UI/session creation and disposal are explicit. Child shell creation runs away from the EDT; while pending, indicate Starting terminal. Completion is delivered to the EDT. If the owner closed before creation finishes, immediately close the newly created session. A failed new tab/split displays a useful error and leaves existing sessions intact. Window close closes only that window's panes and removes its handlers/timers/listeners. Quit closes all windows; closing the final tab closes its window. An exited shell retains the message and closes only its pane on the next ordinary key. Keep Main.windowTitle compatibility for its existing unit tests.

New tabs and splits start in the focused pane's last reported OSC 7 directory; until reported, use its launch directory. New windows inherit the invoking pane's directory where available. Invalid or unavailable directories cause a visible launch error, not silent redirection. Track the shell label from the launched command. All Swing changes and model mutations used by Swing occur on the EDT.

## Commands and keyboard routing

One ActionId catalog defines the Phase 1 action names, labels and defaults. Menus, toolbar buttons, context menus and shortcuts call the same Swing Actions. Actions resolve the focused pane when invoked, never retain the pane that existed when created. Disable actions without a valid target; settings/reload remain disabled for Plan 4.

KeyBindings parses cmd/ctrl/alt/shift plus keys, case-insensitively; `none` removes an action. Unknown actions, unknown keys and colliding active bindings report errors instead of silently discarding commands. Parsing remains a plain class for Plan 4 reuse. macOS defaults exactly match Phase 1 §5.1. On Linux/Windows, the original specification's cmd=Ctrl+Shift makes cmd+d and cmd+shift+d identical. Use Ctrl+Alt+Shift for defaults explicitly written cmd+shift, preserving distinct actions and reserving ordinary Ctrl for terminal applications. This compatibility resolution is explicit and covered by tests; it does not alter the macOS bindings. Literal user overrides are parsed literally and collisions reported.

App commands run before terminal encoding through a terminal callback. The terminal suppresses a following KEY_TYPED for a handled command. Retain standalone view shortcuts when no app handler is installed, so the benchmark/component remain independently usable. App-installed views use the app handler for copy/paste/prompt actions; `none` therefore really disables a shortcut. Shift+PageUp/PageDown remain local terminal scrolling. In text fields, editing shortcuts retain native behavior; global pane/window actions still work. Escape closes the find bar and restores terminal focus. Ensure focus navigation shortcuts work while an application has requested mouse reporting.

Font bigger/smaller/reset changes only the focused pane, by 1 point, bounded 6–72, with reset to 14. Rebuild only font metrics, key encoder and painter as necessary; preserve the session. Resize the PTY after a font change. `clear_scrollback` clears history while preserving the live screen and emits the existing reset event. Dimming uses a background overlay for inactive panes at 0.3 by default, scoped to application focus; the standalone component remains undimmed unless configured by its owner.

## Find and context menu

Find shows a field, previous/next buttons, Case and Regex toggles, count/current index, close button and regex errors. Enter finds next, Shift+Enter previous. Do not run potentially expensive search directly inside a text document listener. Debounce queries and perform search outside the EDT; apply results on the EDT only if still current. A closed pane/bar or superseded request cannot apply stale results. Provide terminal-owned asynchronous search entry points as needed to preserve encapsulation. Document the existing per-physical-row matching limit. Reflow/history reset invalidates the count as well as highlights. A slow regex must not hold the emulator buffer lock. Cancellation is best effort for Java regex, with bounded worker/thread allocation.

The terminal offers a popup callback only when right-click is locally owned: application mouse reporting retains its right-click events, and Shift bypass permits local context menu. Handle platform popup press/release differences exactly once per gesture. Context actions target the clicked pane and include Copy, Paste, Find, Split right/down, Zoom pane, Close pane. Copy is disabled for an empty selection.

## Chrome and feedback

Render the split model as nested JSplitPanes, with proportional resizing and persisted divider ratios. Reuse terminal pane components when rebuilding; never restart processes for layout changes. Minimum pane dimensions prevent dividers from collapsing a terminal to zero cells. Mark the focused pane subtly, dim inactive panes, and avoid stealing keyboard focus when shell output/title updates arrive.

Status shows focused shell, working directory, columns × rows and Built-in defaults. Updates follow pane/tab focus, directory events, resize and font changes. Every executable action appears in a menu, including direct tab selection and prompt navigation. View includes toolbar modes, status visibility, and light/dark appearance. SVG assets are bundled with their source/license; use the user-supplied Tabler Icons repository at ~/projects/tabler-icons and retain its MIT license attribution.

## Verification and completion

Tests first for model invariants, geometric navigation, zoom/ratios, tab naming/reordering, key parsing/collision detection, app-before-terminal routing, font/scrollback APIs, and asynchronous result invalidation. Headless Swing tests cover action dispatch, find bar lifecycle and menu/context behavior where practical; use real PTY integration tests for process lifecycle where appropriate. Run `./gradlew check --rerun-tasks` on the final branch. Hand the GUI smoke checklist to the user in accordance with AGENTS.md. Record any manual checks that remain instead of claiming the Phase 1 two-week gate is complete.

Acceptance: multiple windows remain independent; tabs reorder/rename/close; nested splits retain shells, ratios and focus through zoom; commands reach the right pane; find/clipboard/links/mouse behavior stays intact; chrome controls are accessible; existing terminal tests pass. Plan 4 and the Phase 1 switch-over gate remain separate work.
