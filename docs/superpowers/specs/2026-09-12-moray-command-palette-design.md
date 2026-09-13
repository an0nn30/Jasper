# Moray command palette — approved design

**Status:** Approved by the user on 2026-09-12 following the interactive visual review. The selected treatment is the 560px raised card, 12px outer corners and 40px result rows in the app’s purple chrome, with equivalent classic-dark/light treatments. Implementation has not started.

**Branch:** `codex/command-palette-design`, from main `1db31b1`.

**Implementation plan:** [Six-task delivery plan](../plans/2026-09-12-moray-command-palette.md).

This user-requested design brings the palette forward from the Phase 1 spec's later list. It preserves the terminal architecture and introduces no plugin framework. It is the scoped amendment for palette behavior and its conflicting default shortcut.

## Confirmed requirements

- A floating, Spotlight-like text box matching Moray's aesthetic, centered in the terminal window.
- Default activation with Cmd+K on macOS and Ctrl+K elsewhere.
- Commands register throughout the app and execute through the palette. Include toolbar capabilities, vertical/horizontal splits, new window and Settings.
- Search shows at most five results, numbered 1–5.
- **Cmd/Ctrl+1–5 executes the corresponding result; plain digits remain searchable.** Confirmed in discussion.
- Show the three most recent commands. **Recents persist across restarts.** Confirmed in discussion.
- Registration must be simple and fast, with room for future plugins to contribute commands. Plugin loading, discovery, public SDKs and a plugin framework are outside this deliverable.

## Approach and registration

Add a small concrete command registry in `moray-app`, wrapping existing Swing `Action` objects with search metadata. Preserve `ActionId` and existing configuration IDs. Built-in entries reuse the window's actions, enabled state, effective shortcuts and execution behavior. A new feature registers its own action without adding palette dispatch branches or extending `ActionId` solely to appear in search.

Metadata directly on `ActionId` would be smaller initially but tie registration to a closed enum. Replacing the entire menu/shortcut/action system would create unnecessary scope. The registry permits incremental additions while retaining existing behavior. No new custom interface is required: use concrete classes/records, Swing `Action` and standard JDK callbacks. All code remains in `moray-app`.

Each window owns a `CommandRegistry`; the application owns one shared `CommandHistory`. A command definition contains a stable string ID, title, immutable keyword list, optional icon and action. Existing built-ins use their `ActionId.id()` strings. New commands use namespaced IDs such as `sessions.connect`; IDs never depend on labels or window identity. Duplicate IDs fail clearly rather than replacing handlers.

Feature initialization registers commands for each relevant window. A concrete closeable registration handle removes only that registration. Registry changes refresh an open palette and invalidate preprocessed metadata. Window disposal removes listeners and releases bound actions. History stores only IDs, never actions, windows or terminal panes.

| Component | Responsibility |
|---|---|
| `CommandRegistry` and command definition | Registration, stable identity, search metadata and action ownership |
| `CommandSearch` | Pure deterministic ranking and five-result limit |
| `CommandPalette` | Swing overlay, input/results, keyboard handling and selection |
| `CommandHistory` | Shared three-item recency order and persistence |
| `WindowContent` integration | Built-in registration, origin/focus lifecycle, dispatch and theme updates |

This is internal application functionality, without a public binary compatibility promise. Registration does not automatically provide configurable shortcuts for new commands. Future plugin integration may use the same mechanism; no class loading, discovery, argument forms or dynamic provider protocol is introduced now.

## Placement and visual treatment

Placement is the center of the selected tab's whole terminal pane area, across all splits, excluding title/tabs, toolbar and status bar. It does not center on the focused split or physical display. Keep the card centered on resize and as its result count changes.

Use a lightweight Swing overlay owned by `WindowContent`, above existing content, without reparenting terminal views or changing layout/PTY dimensions. Do not create a separate native window. Outside clicks dismiss and are consumed, preventing click-through into terminal applications.

Approved UI-scaled dimensions: preferred width 560px, at least 16px side clearance where space permits, 56px input row, 40px result rows, 12px outer corners and 6px row corners. Clamp to small windows, keeping the input and selected result visible; no scrollbar is normally needed for five results. Keep these dimensions; headless Swing renders will verify the implementation against the approved treatment.

Use existing chrome theme roles for raised surface, border, main/muted text, accent and selection. Purple defaults come from selected-tab background `#241532`, border `#4e2c69`, chrome text `#bcc2d2`, accent `#c089ef` and selection `#492b61`. Classic dark and light use their active theme's equivalent roles. Never hard-code purple into the component or inherit arbitrary terminal ANSI colors.

Use the app UI font and existing outline icon vocabulary. Placeholder: “Type a command…”. A result shows an optional icon, title, current application shortcut where present and trailing selection badge (`⌘1` or `Ctrl+1`). At narrow widths, omit the application shortcut before truncating the title; retain the selection badge. Integrate the input into the card with a subtle separator and shadow. Start without blur or animation.

## Search and input

Opening clears the query and focuses the input. Empty search shows up to three distinct, available recent commands, newest first, under “Recent”. Do not fill a partially populated Recent list with suggestions. With no available history, show three available starters under “Suggested”: New Tab, Split Right and Settings, using New Window as fallback.

Typing replaces that list with the best five available matches. Search title and keywords case-insensitively after trimming/collapsing whitespace. Prefer exact titles, title prefixes, title word prefixes, title substrings, keyword matches and finally ordered-character fuzzy matches. All query words must match; prefer shorter fuzzy gaps. Recency breaks equal relevance, followed by normalized title and stable ID, so history cannot displace stronger matches. Exact scoring examples belong in the implementation plan and tests.

Select the first result on open or query change. Up/Down moves within the visible list, stopping at its ends. Enter executes the selection; click executes that row; Cmd/Ctrl+1–5 executes the corresponding visible result, including in the empty-query list. Missing numbers do nothing and stay consumed. Plain numbers are text. Escape, the activation shortcut again, or an outside click dismisses. “No matching commands” is nonselectable; Enter then does nothing.

While open, the palette owns result shortcuts ahead of numbered-tab routing. Native input editing, selection, clipboard shortcuts and input-method composition remain usable. Other app shortcuts must not unexpectedly mutate the underlying tab/pane. Activation suppresses repeat-triggered duplicate execution and residual typed events must not leak to the input or terminal.

## Focus and execution

Remember the opening window, tab, logical focused pane and previous focus component. Input focus in the palette must not change the logical target pane. Dismiss on owner deactivation, tab switch, removal of the originating pane/tab or window disposal. Commands always act in their owning window.

Unavailable commands are omitted from results/recents. Refresh from owner/action state changes, independently of terminal repaint frequency. Preserve selection by ID on availability refresh if still visible; otherwise select the first result. Recheck registration, owner validity and enabled state at activation. Never execute or record a stale result.

Resolve the target, hide the palette and restore valid prior focus before dispatch. Actions opening Find, a dialog or a new window take focus normally; no deferred restoration may steal it back. Cancellation falls back to the current terminal if the previous focus component is gone.

Reuse action error handling and existing asynchronous shell/configuration work. Unexpected callback exceptions are logged and shown through existing error presentation; do not record failed dispatch. Metadata/availability checks must not do I/O, inspect terminal buffers or start workers per keystroke. Give the input an accessible name, expose list selection/count to assistive technology and retain visible keyboard focus.

## Persistent recents

History scope: palette invocations across all windows in one application process. Toolbar/menu/ordinary-shortcut use does not alter this history. Move a command to the front after accepted dispatch, remove duplicates and keep at most three IDs. Opening/highlighting/stale activation do not count. For asynchronous actions, acceptance means the request was dispatched, not that later work succeeded. Existing dialog actions count as invoked even if the subsequent dialog is cancelled.

Resolve `command-history.toml` through `AppDirs` under the app root, independent of `--config`. Store a version and ordered IDs only; never queries, terminal output, directories or callbacks. Keep `config.toml` untouched. Missing files mean empty history. Bound file size to 16 KiB, IDs to 128 characters and accepted entries to three. Malformed or unsupported data warns and yields empty memory state without blocking startup; valid entries are deduplicated. Unregistered IDs remain bounded stored data but are skipped in display, allowing features to register after loading.

Use one serial background worker for reads/writes. Merge loaded history behind commands invoked during startup so late loading cannot overwrite newer use. Publish state on EDT. Update memory immediately, coalesce pending immutable snapshots and write via a sibling temporary file with atomic replacement where supported and serialized replacement as fallback. Failures leave the in-memory feature usable and are logged without repeated modal interruptions. Normal shutdown drains the latest write off EDT with a bounded wait; abrupt termination cannot guarantee the final update. Separate application processes use last-writer-wins history; cross-process synchronization is outside this slice.

## Initial commands and shortcuts

Include every toolbar action: New Tab, New Window, Split Right, Split Down, Zoom/Restore Pane, Find, Settings and Reload Config. Palette split titles are “Split Right · Vertical” and “Split Down · Horizontal”, searchable by direction or orientation. Settings retains its current behavior of opening the configuration file in the OS editor.

Include existing menu capabilities: close tab/pane, rename tab, next/previous/numbered tabs, directional pane focus, next/previous find result and prompt, terminal copy/paste, clear scrollback, font increase/decrease/reset and Quit. Register current View operations: toolbar modes, status visibility, Light/Dark/Follow System and Tab Height. Reuse existing handlers/state, extracting only menu-local handlers necessary for reuse. Dynamic titles such as Restore Pane and Hide Status Bar refresh searchable metadata when state changes.

Add `command_palette` to the existing action/keybinding catalog and View menu. Defaults:

| Action | macOS | Windows/Linux |
|---|---|---|
| Open palette | Cmd+K | Ctrl+K |
| Clear scrollback | Cmd+Shift+K | Ctrl+Shift+K (existing effective default) |
| Result 1–5, while open | Cmd+1–5 | Ctrl+1–5 |

The existing `cmd` token maps to Ctrl+Shift outside macOS. Give the palette an explicit platform-specific default; do not redefine `cmd`. Use explicit non-macOS clear-scrollback text to avoid the compatibility modifier rewrite applied to `cmd+shift` defaults.

Explicit user bindings retain current collision validation; do not rewrite saved config. Document how to keep Cmd+K for Clear Scrollback by rebinding/disabling `command_palette` in the same configuration. Update the generated template, root example, configuration guide and Phase 1 shortcut amendment during implementation. Ctrl+K outside macOS is intentionally consumed before terminal encoding while the default is enabled; users can rebind/disable it.

## Verification and delivery

Create one implementation plan following repository TDD, per-task implementer/reviewer and whole-branch review conventions. Cover ranking/limits, registration/removal, startup history merging, persistence failures/shutdown, shortcut defaults/collisions, headless `WindowContent`/`JRootPane` routing, zero shortcut leakage to `FakeConnector`, stale targets/disposal, text editing, live themes, resize centering and unchanged terminal grid dimensions.

Measure search on a deterministic synthetic 1,000-command catalog and record latency/allocations without fragile wall-clock unit-test thresholds. Search stays in memory with preprocessed metadata, no debounce, filesystem reads or terminal-buffer locks. Inspect headless renders in purple/classic/light, recents, five results, empty state, narrow sizes and scale factors. Native focus, input methods, accessibility and physical-display placement require user-run acceptance. No GUI or benchmark window is needed to review this design.

The interactive review used illustrative terminal content, sample recents, local simulated execution and a preview-only reopen button. Those preview helpers are not app requirements. Production uses real command availability, native actions and persistent history. Implementation, merge and publication are not claimed by this design.
