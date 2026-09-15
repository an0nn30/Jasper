# Command palette

The palette holds more than one kind of searchable thing, called a **scope**.
Cmd+K on macOS or Ctrl+K on Windows and Linux (or View → Command Palette) always
opens the **Commands** scope; Cmd+R on macOS or Ctrl+Shift+R elsewhere always
opens the **History** scope. Pressing the shortcut for the scope that is already
active dismisses the palette instead of reopening it. Each scope shows a chip —
its icon and label — at the left of the input; type `>` at the start of an empty
query to open the scope picker, use Tab or Enter to switch to the highlighted
scope, and Escape to leave the picker and keep the previous scope. Clicking the
chip opens the picker too.

Within a scope, Enter runs the first verb and Cmd+Enter (Ctrl+Enter elsewhere)
runs the second verb, shown as a footer hint whenever a scope has more than one
verb. Commands has one verb, Run, so its footer stays hidden, as before. History
has two: Enter pastes, Cmd+Enter pastes and runs. Escape, the active scope's own
opening shortcut again, or an outside click closes the palette. Use Up/Down and
Enter, or Cmd+1–5 / Ctrl+1–5 to act on a numbered result; plain digits are search
text.

## Commands

Type to search available commands. At most five results appear. An empty search
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

Settings opens the existing configuration file in the OS editor. Split Right
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

The History scope searches every command Jasper can find, from two places:

| Shell | File | Format |
|---|---|---|
| zsh | `$HISTFILE` from the app's environment, else `~/.zsh_history` | Extended `: <ts>:<dur>;<cmd>` or plain lines; backslash-newline continuation; zsh metafied bytes (0x83 marker) unescaped before UTF-8 decoding |
| bash | `~/.bash_history` | Plain lines; `#<ts>` lines from `HISTTIMEFORMAT` supply timestamps |
| fish | `~/.local/share/fish/fish_history` | `- cmd:` / `when:` / `paths:` blocks, hand-parsed; escapes in `cmd` decoded |
| nushell | `~/.config/nushell/history.txt` | Plain lines. The SQLite backend is not read |
| PowerShell | PSReadLine `ConsoleHost_history.txt` in its platform location | Plain lines; trailing backtick continuation |

and **live capture**: while a shell session is running, Jasper watches for the
OSC 133 B (command start) and C (output start) marks its shell integration
emits and records the command text, working directory and exit status straight
from the terminal, tagged with that pane's shell. A shell that never emits a B
mark — because it has no shell integration configured — contributes nothing
live and is covered by its history file alone. Jasper ships no shell-integration
script of its own; wiring one up is a separate future feature, and it remains
the main limit on live capture today.

Jasper writes no history file of its own — your shell's files are only ever
read, never modified. On a History row, Enter pastes the command into the
focused pane and Cmd+Enter (Ctrl+Enter elsewhere) pastes and runs it, exactly as
a clipboard paste would (bracketed paste markers and newline normalization
included). Commands pasted this way are not added to the Commands scope's
recents; the shell's own history is the record.

Entries are ranked by search-term match quality, then by recency; entries whose
recorded working directory matches the target pane's current directory rank
ahead within their tier. Timestamps come from the shell where the format
supports them (zsh, bash with `HISTTIMEFORMAT`, live capture); entries without a
known timestamp — bash without `HISTTIMEFORMAT`, nushell, PowerShell — sort
after every timestamped entry, in their file's own order.

Set `history.enabled = false` under `[history]` to remove the History scope
entirely: it disappears from the scope picker and its shortcut does nothing.
See [configuration](configuration.md#shell-history).

## Scopes for features

`PaletteScope` is the internal seam behind every scope; it is not a public
plugin SDK. A scope supplies its own rows and verbs and never touches Swing —
one shared renderer paints every scope's rows, and a scope's `search` and
`execute` methods see only a `PaletteContext`, never a window or pane:

```java
final class FakeFeatureScope implements PaletteScope {
    @Override public String id() { return "jasper.fake-feature"; }
    @Override public String label() { return "Fake Feature"; }
    @Override public String placeholder() { return "Search fake feature, or > to switch scope"; }
    @Override public List<PaletteVerb> verbs() {
        return List.of(new PaletteVerb("open", "Open"), new PaletteVerb("open_pinned", "Open pinned"));
    }
    @Override public PaletteResults search(String query, PaletteContext context) {
        return new PaletteResults(List.of(new PaletteRow("fake.1", "Example result", null, null, null, true, null)),
            "Suggested", null);
    }
    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) {
        context.target().paste().accept(row.title());
    }
    @Override public CommandRegistry.Subscription onChanged(Runnable listener) {
        return new CommandRegistry.Subscription(() -> {});
    }
}

var registration = owner.scopes().register(new FakeFeatureScope());
// Feature disposal calls registration.close() on EDT.
```

This is the seam a future plugin API would expose unchanged: a scope sees only
its own query, produces only data rows, and receives no `WindowContent`,
`TerminalPane` or JediTerm type. No plugin loading or discovery exists yet; this
deliverable ships exactly two scopes, Commands and History.

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
verification, including scopes, the picker and History rows. Native focus, input
methods, accessibility, chip rendering on the real title-bar theme, and
physical-display placement remain in the [manual acceptance checklist](superpowers/plans/2026-09-12-jasper-command-palette-manual-check.md).
