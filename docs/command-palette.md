# Command palette

Every window's header has a search field in the middle showing the focused pane's context, with the
palette shortcut on the right. Clicking it, Cmd+K on macOS or Ctrl+K on Windows and Linux, or View →
Command Palette drops the palette from the field: the card covers the field, centred on it and at
least 560 px wide, moving or narrowing to stay clear of the window controls. When the window is too
narrow for the field, the palette opens centred near the
top fifth of the window instead.

The palette opens with no **scope chip**. An empty query shows **Recently used**, the last three rows
you ran from the palette, then **Most used**, rows ranked by how often and how recently you ran them,
topped up with each scope's own suggestions (Commands' starters, …) until there are ten. History's
shell commands and Vault's entries never appear here unprompted; they show once you type. A row
that no longer exists (a deleted host or snippet) drops out. Uses are saved with the command
history (`command-history.toml`, at most 50). Typing anything else searches every scope at once and groups the
matches under each scope's name. The list shows `palette.visible_rows` rows (five by default) and scrolls
to the rest; if a very large result set runs past 200 rows, a "More in <Scope>…" row sets that scope's chip with the query kept. Vault's secrets stay out of that search;
its chip searches them.

Chips: `>` at the start of the query is Commands; `<scope> >` (for example `history > ls`, or any
unique start such as `hist >`) is that scope, and the prefix leaves the field. With a chip set, `>`
is ordinary text. Backspace in an empty query removes the chip. Each scope's own shortcut opens the
palette with its chip, or switches to it while the palette is open: Cmd+R (Ctrl+Shift+R elsewhere) History,
Cmd+J (Ctrl+Shift+J) Snippets, Cmd+\\ (Ctrl+Shift+\\) Vault.
Pressing the shortcut of the chip already showing, or Cmd/Ctrl+K with no chip, closes the palette.
Tab does nothing outside a form.

Rows are one line: an icon, the title, a grey detail and a right-aligned tag. Up and Down move
between rows, skipping the section headers. Enter runs a row's first verb, Cmd+Enter (Ctrl+Enter
elsewhere) its second and Shift+Enter its third. The hint bar under the list shows the selected
row's detail and, when its scope has more than one verb, every verb with its key, Enter's first; click one to run it. Escape leaves an open step,
keeping the list underneath, and otherwise closes the palette, as does an outside click. Plain
digits are search text. Every colour comes from the installed theme.

| Scope | Enter | Cmd/Ctrl+Enter | Shift+Enter |
|---|---|---|---|
| Commands | Run | | |
| History | Paste | Paste and run | Save as snippet… |
| Snippets | Insert | Paste and run | Edit file |

## The Run window

Under the Enterprise look the palette is the Run window, centred across the window, in its upper part. Its shortcut, the "▶ Run…" status item and the toolbar's Search everywhere field open it,
and put the cursor back in its Open field while it is open, as a click on the window does. It stays open while you
work: clicking a pane gives that pane the keyboard, and a row runs in the pane that is current when you run it. It
closes on Escape (with the cursor in it), ×, Cancel or running a row. Drag it by its title row or an empty area;
dropped near its home, it goes back there, and a double click on its title row sends it home. Its place is
kept for every window (`[palette.run_window]` in `ui-state.toml`). The list keeps the height of `palette.visible_rows`
rows (fewer when the window is too short for its home) under "Recent" and "Most used" (or "Suggested"), or one
"Matches" heading that counts the matches while you type; each row shows its kind's colour. The line under the list
names the selected row ("Session: dustin@192.168.1.119:22"); "Open in a split pane" makes Enter and the primary button
run the row's split verb where its kind has one; the primary button reads the row's first verb, and the other two are
in the row's context menu. Browse… opens a file with the default editor, as File › Open File… does, and the Run window
stays open.

## Commands

Type to search available commands. The list shows `palette.visible_rows` rows (five by default; the default depends on the active look;
see [configuration](configuration.md#palette)) and scrolls to the rest. An empty search
shows your last three distinct palette commands, newest first.
Recents are shared across windows and saved in `command-history.toml` under
Jasper's application directory. Running Settings from the palette counts as a
recent command like any other accepted palette command. Invoking an action from
the toolbar, menu, or an ordinary shortcut does not change palette recency, and
the main configuration file is never used to store recents. Before any history
exists, the palette offers starter commands.

Clear Scrollback uses Cmd+Shift+K on macOS and Ctrl+Shift+K elsewhere. You can
customize `command_palette` and `clear_scrollback` in `[keybindings]`. On Windows
and Linux, `cmd` continues to mean Ctrl+Shift; use literal `ctrl+k` to bind the
palette to Ctrl+K.

Settings opens the existing configuration file in the [default editor](editor.md#default-editor). Split Right
creates side-by-side panes; Split Down creates vertically stacked panes.

To keep Cmd+K for Clear Scrollback on macOS and open the palette with Cmd+P:

```toml
[keybindings]
command_palette = "cmd+p"
clear_scrollback = "cmd+k"
```

To free Ctrl+K for the terminal on Windows or Linux and open the palette with
Ctrl+P:

```toml
[keybindings]
command_palette = "ctrl+p"
```

## Shell history

The History scope is the bundled **Shell History plugin** (`dev.jasper.history`); disable it in
File → Manage Plugins… and the scope and its shortcut go with it. It searches every command Jasper
can find, from two places:

| Shell | File | Format |
|---|---|---|
| zsh | `$HISTFILE` from the app's environment, else `~/.zsh_history` | Extended `: <ts>:<dur>;<cmd>` or plain lines; backslash-newline continuation; zsh metafied bytes (0x83 marker) unescaped before UTF-8 decoding |
| bash | `~/.bash_history` | Plain lines; `#<ts>` lines from `HISTTIMEFORMAT` supply timestamps |
| fish | `~/.local/share/fish/fish_history` | `- cmd:` / `when:` / `paths:` blocks, hand-parsed; escapes in `cmd` decoded |
| nushell | `$XDG_CONFIG_HOME/nushell/history.txt` (default `~/.config/nushell/history.txt`), plus `~/Library/Application Support/nushell/history.txt` on macOS | Plain lines. The SQLite backend is not read |
| PowerShell | PSReadLine `ConsoleHost_history.txt` in its platform location | Plain lines; trailing backtick continuation |

and **live capture**: shell integration marks the input start with OSC 133 B,
execution/output start with C, and completion with D. Jasper's zsh, bash and fish
scripts also supply exact command text; otherwise Jasper captures the text between
B and C from the terminal. Exact text plus C can start capture without B. Completion
publishes the command, current working directory and exit status when available;
a subsequent new prompt can finish a pending command without an exit status.
Shells without these integration signals contribute only their history files.
See [Shell integration](configuration.md#shell-integration). Bash's integration
suppresses live capture when it detects a command omitted under
`HISTCONTROL=ignorespace` or `ignoreboth`.

The list refreshes about once a second while the palette is open, so a command
you just ran appears without reopening it — for any shell that writes its
history file promptly, with no shell integration needed. Integration is still
the instant path, and the only one that records the working directory and exit
status. A shell that records no timestamps in its history file — bash, unless
you set `HISTTIMEFORMAT` — is ranked by when that file was last written rather
than sinking below every timestamped entry, so a bash command from minutes ago
sits above a zsh command from last week.

Jasper does not persist the live shell-history index; your shell's files are only
ever read, never modified. The separate Commands recents file is described above.
On a History row, Enter pastes into the pane captured when the palette opened;
Cmd+Enter (Ctrl+Enter elsewhere) pastes and runs it, exactly as
a clipboard paste would (bracketed paste markers and newline normalization
included). Commands pasted this way are not added to the Commands scope's
recents; the shell's own history is the record.

For a nonempty query, entries rank by search-term match quality, then by a match
to the origin pane's current directory, then by recency. Timestamps come from the
shell where supported (zsh, bash with `HISTTIMEFORMAT`, live capture). Entries
without timestamps use the file's modification time with offsets preserving file
order. For an empty query, configured trivial commands move behind other entries;
each group retains recency order.

The trivial-command list and its on/off switch live in the plugin's own configuration table,
`[plugins."dev.jasper.history"]`; see [configuration](configuration.md#shell-history). The old
`[palette.scopes.history]` table is reported as moved.

## Snippets

Snippets are named commands you save yourself: shell aliases the traditional way don't fit,
since each shell spells aliases differently. The scope is the bundled **Snippets plugin**
(`dev.jasper.snippets`). `snippets.toml` lives in the plugin's data directory,
`<Jasper home>/plugin-data/dev.jasper.snippets/snippets.toml`, independent of `--config`; it is
never read or written by the configuration loader. A `snippets.toml` from before the plugin, in the
Jasper home itself, is moved there on the first launch (with both present, both are left alone and
the log says so). Jasper only ever appends new `[[snippet]]` tables, so hand edits and comments
survive:

```toml
# Jasper snippets. Edit freely; Jasper only ever appends new [[snippet]] tables.

[[snippet]]
name = "Rebase onto main"
command = "git fetch origin && git rebase origin/{{branch}}"
keywords = ["git", "rebase"]
```

`name` is required, nonblank, at most 128 characters and unique ignoring case and surrounding
whitespace. `command` is required, nonblank, may be multi-line, and at most 16,384 UTF-16 code units. `keywords`
is an optional array of nonblank strings that also rank in search. File order is the order the
empty query shows.

Placeholders are `{{identifier}}` tokens, where `identifier` matches
`[A-Za-z_][A-Za-z0-9_]*`; a backslash before the opening braces, `\{{`, produces a literal
`{{` instead. A snippet with placeholders opens a fill-in step before Insert or
Paste-and-run: one labelled field per distinct placeholder, in the order it first appears in
the command, the first field focused. Tab and Shift+Tab move between fields and wrap; each
field is prefilled with the value you last typed for that placeholder name in this process.
Enter substitutes every occurrence and runs the verb you chose; Escape returns to the list
with your search intact. A snippet without placeholders skips the step entirely.

Enter pastes the filled-in command into the pane captured when the palette opened; Cmd+Enter (Ctrl+Enter elsewhere)
pastes and runs it, the same as a History paste. Shift+Enter is Edit file: it creates
`snippets.toml` with its header comment if missing, then opens it in the [default editor](editor.md#default-editor), as
Settings does for `config.toml`.

From the History scope, Shift+Enter is "Save as snippet…": it opens a one-field name step
prefilled with the command's first word and its first argument (for example `git rebase`),
with the command itself as the step's heading. Enter appends the snippet to `snippets.toml`;
on success the palette dismisses and reopens in Snippets with the new row selected. A name
that already exists (case-insensitive) is refused with "A snippet named … exists" shown under
the field, keeping focus so you can pick a different name; a write failure shows its message
the same way. Escape returns to the History list.

Snippets rows scroll like every other scope's (see
[configuration](configuration.md#palette)); each row's tag shows its
placeholder count ("1 field", "2 fields") when nonzero, and the command itself shows in
grey after the name, on the same line. If `snippets.toml` fails to parse, the last good snapshot
keeps working but the scope's list also shows an error row, "Snippets file has errors", until
you fix the file and Reload Config.

## Scopes for features

`PaletteScope` is the seam behind every scope. The application implements it for Commands;
plugins implement the SDK's `dev.jasper.sdk.palette.PaletteScope`, which the plugin runtime adapts
onto this one, and the History → Snippets "Save as snippet…" hop is a service dependency between
two plugins. A scope supplies data rows and verbs.
One shared renderer paints every scope's rows, and a scope's `search` and
`execute` methods see only a `PaletteContext`, never a window or pane. Two more
hooks are optional: `available` is rechecked right before a verb runs (a scope
can refuse a verb per row, for example when its target is no longer live), and
`step` can show a small form in the palette instead of running the verb directly:

```java
final class FakeFeatureScope implements PaletteScope {
    @Override public String id() { return "jasper.fake-feature"; }
    @Override public String label() { return "Fake Feature"; }
    @Override public String placeholder() { return "Search fake feature"; }
    @Override public List<PaletteVerb> verbs() {
        return List.of(new PaletteVerb("open", "Open"), new PaletteVerb("open_pinned", "Open pinned"));
    }
    @Override public PaletteResults search(String query, PaletteContext context) {
        return new PaletteResults(List.of(new PaletteRow("fake.1", "Example result", null, null, null, true, null)),
            "Suggested", null);
    }
    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        return row.enabled(); // refuse a verb per row; a false answer refreshes the list instead of executing
    }
    @Override public PaletteStep step(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        return null; // return a PaletteStep to show a form instead of calling execute for this verb
    }
    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        context.target().paste().accept(row.title());
    }
    @Override public dev.jasper.app.lifecycle.Subscription onChanged(Runnable listener) {
        return new dev.jasper.app.lifecycle.Subscription(() -> {});
    }
}

var registration = owner.scopes().register(new FakeFeatureScope());
// Feature disposal calls registration.close() on EDT.
```

Scopes receive no `WindowContent`, `TerminalPane` or JediTerm type. This internal
contract makes no compatibility promise for a future plugin API. No plugin loading
or discovery exists; Jasper ships Commands, History and Snippets.

`WindowCommandPalette` captures the origin tab/pane and restores focus; changing
chips retains that target. `PaletteController` owns the chip, query and step state.
Completion callbacks are marshalled to EDT and recheck generation, step identity,
registered scope and origin validity before publishing. Dismissal, scope changes,
step cancellation, pane/tab changes and window closure invalidate stale results.
These publication guards do not cancel an already-submitted file write. A provider
that performs asynchronous side effects must own their cancellation/lifetime checks.

The Commands scope itself wraps the existing command registry, which stays the
same internal facility it always was. A feature that already belongs to a
window can register its existing Swing action into it:

```java
var action = new javax.swing.AbstractAction("Connect Session") {
    @Override public void actionPerformed(java.awt.event.ActionEvent event) {
        openSessionPicker.run();
    }
};
var registration = owner.commands().register(
    new Command("sessions.connect", action, java.util.List.of("ssh", "host")));
// Feature disposal calls registration.close() on EDT.
```

Here `openSessionPicker` is the feature's existing `Runnable`; the example does
not create a session feature or add a production API. Register and unregister on
the Event Dispatch Thread. IDs are stable, unique within a window, at most 128
characters, and match `[a-z][a-z0-9_.-]{0,127}`. Closing a registration removes
that exact command identity, so an old result cannot execute a later replacement
with the same ID.

`Action.NAME` supplies the default title and `Action.SMALL_ICON` the default icon.
Set `Command.TITLE` or `Command.ICON` on the action for palette-specific values.
The registry refreshes its preprocessed search metadata when these properties or
the enabled state change. Disabled actions are omitted, and execution checks
registration, window/pane ownership, and enabled state again immediately before
dispatch. Metadata and availability getters run on EDT and must do no I/O, read
terminal buffers, or create workers.

## Command recents persistence

History file format version 1 stores only an ordered `recent` array. The reader
accepts strict UTF-8, at most 16 KiB, at most three distinct IDs, and IDs no longer
than 128 characters. Unregistered IDs remain bounded stored data but do not
appear until the corresponding feature registers. A missing file means empty
history. Malformed, unsupported, or unreadable files log a warning and leave the
in-memory feature usable.

The application updates in-memory recency on EDT and serializes immutable
snapshots through one worker. Writes happen off EDT, use a sibling temporary file,
and atomically replace the target where the filesystem supports it. A write
failure is logged and does not roll back memory. Normal shutdown drains the latest
snapshot with a bounded wait; abrupt process termination cannot promise the final
write. Separate Jasper processes use last-writer-wins history without cross-process
locking.

The [actual Swing renders, pure-search and shell-history-search measurements, and
reproduction commands](design/command-palette/README.md) cover the headless
verification, including the mode list, chips, History rows, the Snippets list and
its fill-in and name steps. Native focus, input methods, accessibility, tab
rendering on the real title-bar theme, physical-display placement and editing
`snippets.toml` in the default editor remain in the [manual acceptance
checklist](superpowers/plans/2026-09-12-jasper-command-palette-manual-check.md).
