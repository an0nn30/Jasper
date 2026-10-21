# Keyboard shortcuts

Open **Help → Keyboard Shortcuts…**, or search **Keyboard Shortcuts** / **keymap** in the
command palette’s **Commands** scope, to browse and edit Jasper's current shortcut mappings.
Opening it again brings the same window forward and preserves the search. The window
remembers its size and position through the normal auxiliary-window state.

Rows are grouped by app category or plugin name. The table shows the action, effective
shortcut, section/plugin and context. The context distinguishes a Jasper-window
shortcut from a key used only in a particular field or list. Unbound actions show
**Unassigned**; a plugin default rejected by the shortcut resolver shows its reason
in Context (hover to read long text).

Click a shortcut or **Unassigned** in an app/plugin action row, or select a row and press
Enter, to edit it. Press the new combination, then click **Save**. The recorder stays
active until you save, cancel, stop recording, or leave the window, so shortcuts such
as Cmd+W and Cmd+Q do not execute while recording. You can click the captured shortcut
or **Record shortcut** to record again. **Cancel** leaves the binding unchanged.

**Remove shortcut** proposes an unassigned binding; **Restore default** proposes the
app/plugin default. Both require **Save**. A conflicting action is named before saving;
check **Reassign from conflicting action** to remove its binding and use the shortcut
for this action. Fixed contextual keys (for example, palette navigation and Find-field
Enter) are read-only.

Saving writes `[keybindings]` in the active `config.toml` and applies the change to all
Jasper windows immediately. Missing settings files get the normal commented template.
Unrelated settings and comments are preserved, including edits made outside Jasper.
An invalid file, a concurrent shortcut edit, or a write failure leaves an error in the
editor instead of claiming success. If a plugin default cannot be restored because
another default wins, assign the desired shortcut explicitly. OS-reserved combinations
may not reach Jasper.

Type an action name, plugin, category or shortcut such as `cmd+shift+h` in the search
field. Modifier order is flexible; `command`/`meta`, `control`, and `option` are accepted
aliases for `cmd`, `ctrl`, and `alt`. **Record shortcut** listens for the keys you press
in this window and finds exact matches, including Tab and Escape. **Stop recording**
returns to ordinary text input; **Clear** clears the filter. Leaving the window stops
recording and releases any held-key state. OS-reserved combinations can still be
handled by the operating system; use text search for those combinations.

The catalog uses the same resolution as Jasper windows, so app `[keybindings]`
overrides, plugin defaults, disabled bindings and conflicts are represented accurately.
Configuration reloads and plugin action changes update an open reference immediately.
The catalog covers every built-in action and every currently registered contribution,
plus Jasper's explicit contextual keys for Find, the palette, auxiliary windows, the
Plugins manager and the bundled SSH Hosts/Vault lists. Private keyboard listeners in
third-party plugin UI are not exposed by the SDK and cannot be discovered automatically.
Shell, tmux, editor and operating-system mappings are outside Jasper's catalog.

## Maintenance and verification

`shortcuthelp/ShortcutCatalog` derives configurable rows from `ActionId`, contributed
actions and `KeyBindings.withExtensions`. Update the category switch when adding a new
app action. Plugin names come from runtime descriptors without disk reads. Contextual
rows document explicit UI handlers; update them when changing those handlers. This
feature adds no SDK surface or dependency on plugin implementation classes.

`ShortcutHelp` owns the live contribution subscription only while its auxiliary surface
is open. `ShortcutPanel` installs its keyboard dispatcher, focus listener and native-menu
recorder property only while mounted, and removes them on disposal. Native auxiliary
Close and macOS Quit consult that window's recorder before executing.

Shortcut edits run on `ConfigService`'s worker and publish through its existing reload
path. `KeybindingText` patches TOML assignments, including quoted/dotted keys, inline
tables and multiline strings. `ShortcutEditPanel` owns the pending capture, conflict
choice and save result; no write occurs until Save. App/plugin actions are editable;
contextual rows carry an explicit read-only flag. No SDK changes.

Run `./gradlew check :jasper-app:installDist`. To inspect the real panel headlessly,
run `./gradlew :jasper-app:shortcutHelpPreview`; PNGs are written under
`jasper-app/build/reports/shortcut-help/` for both themes and default/18-point typography.

Native acceptance (user-run):

- Open Help → Keyboard Shortcuts and Commands → Keyboard Shortcuts from different
  Jasper windows; only one editor opens.
- Click an assigned shortcut and an Unassigned row, record a new combination and save.
  Verify the menu shortcut and behavior update in both windows and survive a restart.
- Try a conflicting combination, check Reassign, save and verify the old action is
  unassigned. Exercise Remove shortcut, Restore default and Cancel.
- Edit a shortcut while the file is read-only or invalid; Save must show the failure.
- Repeat capture of Cmd+W and Cmd+Q; neither should close the window or quit while recording.
- Search `remote`, `find`, and `cmd+shift+h`; record that chord, Tab, Escape, Cmd+W and
  Cmd+Q while the recorder is active. App commands should not execute during capture.
- Stop recording, clear the filter, then close the reference normally with Cmd+W.
- Change an app or Remote shortcut setting while the window is open and check the row.
- Hold a recorded key, switch away, release it, then return and type in a terminal.
- Resize the reference and change the app's theme/UI font; headings and controls stay usable.

## Editor tabs

The bundled Editor's tabs have their own shortcuts, listed in [Editor](editor.md) and, while the window shows its tab
strip, in the menu of a file's tab.
They are its tabs' commands, not registered actions, so they are not in this catalog; the
action **Open in Editor…** (`dev.jasper.editor.open`, Cmd/Ctrl+O) is.
