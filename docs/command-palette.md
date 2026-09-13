# Command palette

Open the command palette with Cmd+K on macOS or Ctrl+K on Windows and Linux, or
choose View → Command Palette. Type to search available commands. At most five
results appear. Use Up/Down and Enter, or Cmd+1–5 / Ctrl+1–5 to run a numbered
result. Plain digits are search text. Escape, the opening shortcut again, or an
outside click closes the palette.

An empty search shows your last three distinct palette commands, newest first.
Recents are shared across windows and saved in `command-history.toml` under
Moray's application directory. Running Settings from the palette counts as a
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

## Internal command registration

The palette registry is an internal app facility, not a public plugin SDK. A
feature that already belongs to a window can register its existing Swing action:

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

## History and verification notes

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
write. Separate Moray processes use last-writer-wins history without cross-process
locking.

The [actual Swing renders, pure-search measurements, and reproduction commands](design/command-palette/README.md)
cover the headless verification. Native focus, input methods, accessibility, and
physical-display placement remain in the [manual acceptance checklist](superpowers/plans/2026-09-12-moray-command-palette-manual-check.md).
