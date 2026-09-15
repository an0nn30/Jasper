# Jasper palette scopes and shell history — design

**Status:** Implemented on `claude/palette-scopes` in `.worktrees/palette-scopes`, commits `bd76fe4..67743ba` plus the Task 11 documentation commit that records this status. Fresh `./gradlew check --rerun-tasks`: 739 tests, 738 passed, one existing font skip, zero failures/errors. No GUI, merge or push.

**Development branch:** `claude/palette-scopes` in `.worktrees/palette-scopes`, from main `86352b9`.

**Supersedes in part:** the single-scope model of the [command palette design](2026-09-12-jasper-command-palette-design.md). Its geometry, focus rules, key ownership, recents persistence and command registration remain binding; this document changes only what it states.

## Purpose

The command palette is going to hold more than commands. The first addition is shell history: every command the user has run in any popular shell, searchable from the palette and pasted, or pasted and run, into the focused terminal. Later additions may include a ripgrep interface. Without a structure, every addition would pour more rows into one list and the palette would become noisy.

This design adds **scopes**. A scope is one kind of searchable thing with its own index, ranking, rows, verbs and shortcut. The palette shows exactly one scope at a time. Results from different scopes are never mixed. Adding a scope can never change what another scope shows.

The scope model is also the seam for a later plugin API: a scope sees only its own query, produces only data rows, and touches no Swing and no window internals. No plugin loading, discovery or public API is introduced here.

## Confirmed decisions

- One palette component with a visible **scope chip**; in-palette switching by typing `>` at the start of an empty query, by Tab or Enter in the resulting picker, by clicking the chip, or by pressing a scope's shortcut while the card is open.
- Cmd+K (Ctrl+K elsewhere) always opens the **Commands** scope; Cmd+R (Ctrl+Shift+R elsewhere) always opens the **History** scope. The last used scope is not remembered.
- History comes from **both** shell history files on disk and **live capture** through OSC 133 marks, for **every shell found on the machine**, with a shell tag on rows.
- On a history row **Enter pastes**; **Cmd+Enter pastes and runs**.
- Implementation approach A: a real `PaletteScope` abstraction with two built-in implementations (commands, history), one palette component. The alternatives (a sibling history palette copying the overlay code, or history lines registered as commands) were rejected in discussion.

## Scope model

`PaletteScope` is a package-private interface in `jasper-app`. It has two real implementations in this deliverable, `CommandsScope` and `ShellHistoryScope`, which satisfies the project rule against single-implementation interfaces. Its shape is chosen so a later public plugin API could expose it unchanged.

A scope supplies:

| Member | Meaning |
|---|---|
| `id()` | Namespaced stable ID: `jasper.commands`, `jasper.history`; plugins later `plugin.<name>.<scope>`. Same character rules as command IDs. |
| `label()`, `icon()`, `description()` | Chip label, chip/picker icon and the one-line picker description. |
| `placeholder()` | Input placeholder. Every built-in placeholder ends with "or > to switch scope". |
| `aliases()` | Extra words the picker matches, for example `hist`, `shell` for history. |
| `verbs()` | Ordered list of `PaletteVerb(id, label)`. The first verb is Enter, the second is Cmd/Ctrl+Enter. Commands: `run` ("Run"). History: `paste` ("Paste"), `paste_run` ("Paste and run"). |
| `preferredRows()` | Visible rows before the list scrolls: five for commands, twelve for history. |
| `search(query, context)` | Synchronous, in memory, no I/O, no terminal-buffer locks. Returns immutable `Results`. |
| `execute(row, verb, context)` | Performs the verb. Called only after the palette validated the origin, hid itself and restored focus. |
| `onChanged(listener)` | Subscription fired when the scope's index changed. The palette re-runs the current query. A future asynchronous scope such as grep publishes partial results through the same subscription. |

`Results` is a record of rows, a section label used for the empty query ("Recent", "Suggested", "Most recent"), and the ID of the row to select initially (normally the first). `PaletteRow` is a record of id, title, optional detail line, optional tag text, optional icon and an enabled flag. Rows are data; one renderer paints every scope, so scopes never touch Swing.

`PaletteContext` is a record of the OS flag and a `PaletteTarget`, an opaque handle to the origin pane offering `paste(String)`, `workingDirectory()` and `shellName()`. Scopes receive no reference to `WindowContent`, `TerminalPane` or any JediTerm type. This is the boundary a plugin API would later publish.

`ScopeRegistry` is window-owned like `CommandRegistry`: `register(scope)` returns a closeable handle, duplicate IDs throw, and changes refresh an open palette. `WindowContent` registers the built-ins. The default scope is Commands. The shortcut catalog stays the closed `ActionId` enum with one new entry, `history_palette`; dynamic shortcuts for plugin scopes belong to the future plugin API.

The Commands scope wraps the existing `CommandRegistry`, `CommandSearch` and `CommandHistory` with no change to their behaviour: search tiers, the five-result limit, the three persisted recents, the "Suggested" starters, dynamic titles and enabled-state omission are as approved on 2026-09-12. A command's application shortcut is carried in the row's tag text.

## Switching and keys

The card shows a chip at the left of the input with the active scope's icon and label. It is always shown, including in Commands, so the layout does not jump between scopes. Clicking the chip opens the picker.

**Picker.** Typing `>` as the first character of an otherwise empty query enters picker mode. The list becomes the scope list: one row per registered scope showing label, description and shortcut, filtered against labels and aliases by the text after `>`, using the command-search prefix and substring rules. Tab or Enter commits the highlighted scope: the chip changes, the input clears and the new scope's empty-query list appears. Escape, or deleting the `>`, leaves picker mode with the previous scope. `>` anywhere except position zero of an empty field is ordinary text, so a history search for `cat foo > bar` works once `cat` has been typed. When only one scope is registered the picker still works and shows that scope.

**Shortcuts while open.** A scope's shortcut pressed while the card is open switches to that scope in place and keeps the query. Pressing the shortcut of the scope that is already active dismisses, preserving today's Cmd+K toggle. Cmd/Ctrl+1–5 executes the first verb on the corresponding visible row in every scope. Cmd/Ctrl+Enter executes the second verb when the scope has one and is consumed without effect otherwise. Up, Down, Escape, outside click, tab-switch and origin-loss dismissal, the swallowing of key tails after execution and native text editing behave as in the existing design. `PaletteKeyRouter` learns the second opening action and Cmd/Ctrl+Enter, nothing else.

| Action | macOS | Windows/Linux |
|---|---|---|
| `command_palette` (open Commands) | Cmd+K | Ctrl+K |
| `history_palette` (open History) | Cmd+R | Ctrl+Shift+R |
| Result 1–5, first verb, while open | Cmd+1–5 | Ctrl+1–5 |
| Second verb, while open | Cmd+Enter | Ctrl+Enter |

`history_palette` gets an explicit platform-specific default like `command_palette`; the `cmd` token is not redefined. Plain Ctrl+R is never consumed, so the shell's own reverse search keeps working outside macOS.

## History index

One application-wide `ShellHistoryIndex`, shared by every window, owns an immutable `Snapshot` of entries. An entry holds the command text, the latest timestamp when known, the set of shell names it came from, and an optional working directory and exit status from live capture. Entries are deduplicated on exact command text; the most recent occurrence wins and shell sets merge. The snapshot keeps the most recent 50,000 entries, so a search is a substring scan of a few megabytes and may run on the EDT within the scope contract.

### File sources

Each source is a small parser with fixture-file tests. A source is active when its file exists at startup or at a later refresh.

| Shell | File | Format |
|---|---|---|
| zsh | `$HISTFILE` from the app's environment, else `~/.zsh_history` | Extended `: <ts>:<dur>;<cmd>` or plain lines; backslash-newline continuation; zsh metafied bytes (0x83 marker) unescaped before UTF-8 decoding |
| bash | `~/.bash_history` | Plain lines; `#<ts>` lines from `HISTTIMEFORMAT` supply timestamps |
| fish | `~/.local/share/fish/fish_history` | `- cmd:` / `when:` / `paths:` blocks, hand-parsed; escapes in `cmd` decoded |
| nushell | `~/.config/nushell/history.txt` | Plain lines. The SQLite backend is not read |
| PowerShell | PSReadLine `ConsoleHost_history.txt` in its platform location | Plain lines; trailing backtick continuation |

The index loads on one serial daemon worker after the first window opens and never blocks startup. When the palette opens in the History scope, the worker checks each file's size and modification time: a grown file is tail-read from the previous offset, a shrunk or rewritten file is re-read in full, an unchanged file is skipped. Each refresh publishes a new snapshot on the EDT and fires `onChanged`. Unreadable or malformed files are logged once per process and contribute nothing. A refresh is coalesced: at most one runs at a time and one is queued. Decoding is lenient (malformed bytes replaced), and lines longer than 16 KiB are skipped.

### Live capture

`TerminalSession` records only the OSC 133 A mark today. It will also track B (command start) and C (output start). At C it reads, under the buffer lock and bounded to the rows between the B position and the cursor, the text the user entered, joining soft-wrapped rows and trimming trailing spaces. At D it attaches the exit status when present. It then calls a new `TerminalSession.Listener` method, `commandExecuted(String text, OptionalInt exitStatus, Optional<Path> workingDirectory)`, on the reader thread with only JDK types, keeping JediTerm types inside `jasper-terminal`. `TerminalPane` forwards the callback to the index off the lock, tagged with the pane's shell name.

Sessions whose shell emits no B mark contribute nothing live and rely on files. Jasper ships no shell-integration script; that remains a separate future feature and is the main limit on live capture. Continuation prompts inside multi-line commands appear in captured text as the shell drew them; this is accepted.

### Ranking

An entry must contain every query word, case-insensitively after the same normalization `CommandSearch` uses. Entries whose command starts with the whole query rank first, then entries where every word matches at a word boundary, then plain substring matches. Within a tier the most recent timestamp wins; entries whose recorded working directory equals the target pane's current directory rank ahead within their tier. The empty query shows the most recent entries under "Most recent". Commands from the palette's own paste-and-run are not recorded by Jasper; the shell's history is the record.

## Verbs and the paste path

`PaletteTarget.paste(text)` calls `TerminalView.paste`, so bracketed paste markers and newline normalization match a clipboard paste. The `paste` verb sends the text. The `paste_run` verb sends the text and then a single carriage return. Neither touches the Commands recents. If the origin pane has no live session at execution time the palette refreshes instead of executing, matching the stale-target rule.

The Commands scope keeps its dispatch: recheck registration, owner validity and enabled state; restore focus; fire the action; record the recent; route failures through the existing error presentation.

## Layout and rendering

The approved geometry stays: 560px preferred width, 56px input row, 40px rows, 12px outer corners, 6px row corners, centered one-third down the terminal area with the existing small-window clamps. Additions:

- **Chip:** a 24px rounded pill inside the input row, left of the text, icon plus label in the accent colour on the raised surface. Placeholder and typed text start after it. At narrow widths the chip drops its label and keeps its icon.
- **Rows:** title, optional muted detail line, optional trailing tag, and the numbered badge for the top five. Commands rows are unchanged apart from being produced as `PaletteRow`. History rows show the command text in the terminal's monospace font, the shell name as the tag, and the working directory as the detail line only when live capture recorded one. Rows stay 40px.
- **Scrolling:** the card shows up to the scope's preferred rows; the picker shows every scope. Beyond that the list scrolls and keeps the selected row visible, reusing the scrolled-card handling that exists for small windows.
- **Footer:** a 24px strip with verb hints, shown only when the active scope has more than one verb, so Commands looks as it does today. Text: "⏎ Paste  ⌘⏎ Paste and run" on macOS, "Enter Paste  Ctrl+Enter Paste and run" elsewhere.

Existing chrome theme roles supply every colour; nothing is hard-coded. The palette render matrix under `docs/design/command-palette/` is regenerated and extended with History and picker states.

## Persistence, threading and configuration

The history index is in memory only. Jasper writes no history file of its own; the user's shell files are only ever read. File reads run on one serial daemon worker following the `CommandHistoryFile` pattern. Snapshots are immutable and published on the EDT. Shutdown does not wait for the worker.

Live capture does its bounded row read on the session reader thread under the buffer lock and hands the text to the index off the lock.

New configuration:

```toml
[history]
enabled = true
```

When `false` the History scope is not registered, so it is absent from the picker and `history_palette` does nothing. The setting is live. There are no per-shell toggles and no extra paths in this slice. `history_palette` is added to the keybinding catalog, generated template, root example and configuration guide. `docs/command-palette.md` gains a scopes section and the scope extension example.

## Accessibility

The chip has an accessible name "Scope: History". The picker announces itself as a list of scopes. Verb hints are exposed as the list's accessible description. Existing input naming, list selection and count exposure and visible keyboard focus are retained.

## Testing

- **Scope contract:** headless tests drive `WindowCommandPalette` with a fake scope to prove that switching by shortcut keeps the query, the picker filters by label and alias, Tab and Enter commit, Escape and deleting `>` revert, the chip updates, and rows and verbs from the fake render and execute with no Swing in the scope.
- **Commands regression:** every existing palette test keeps its behaviour with the Commands scope wrapping the registry. The regenerated render matrix shows the chip as the only visual delta in Commands.
- **Parsers:** fixtures per shell covering timestamps, continuation lines, zsh metafied bytes, fish escapes and malformed lines. Dedup, shell-set merge and the 50,000 cap on the index.
- **Incremental refresh:** grown, shrunk, unchanged and deleted files in a temporary directory, asserting which files are re-read and that one refresh is queued at most.
- **Live capture:** `FakeConnector` feeds A, B, command text, C, output and D; the listener receives text, status and directory. Missing B yields no callback. Soft-wrapped command rows join correctly.
- **Verbs:** paste and paste-and-run assert the exact bytes written to `FakeConnector`, including bracketed-paste markers when enabled and the trailing carriage return only for run.
- **Keys:** `PaletteKeyRouter` tests for Cmd+R open, in-place switch, same-shortcut dismiss, Cmd+Enter with one and two verbs, plain Ctrl+R untouched outside macOS, and zero leakage to `FakeConnector`.
- **Configuration:** `history.enabled` parsing, template and example presence, live toggling registers and removes the scope.
- **Performance:** search over a synthetic 50,000-entry snapshot measured like the 1,000-command benchmark and recorded without wall-clock assertions.

User-run native checks: input-method composition with the chip present, chip rendering on the real title-bar theme, Cmd+R on macOS, and paste and paste-and-run into a real zsh with bracketed paste enabled.

## Out of scope

Shipping shell-integration scripts, a plugin API or dynamic shortcuts for plugin scopes, a ripgrep scope, per-shell configuration, nushell's SQLite history, remembering the last scope, and any Jasper-written history file.
