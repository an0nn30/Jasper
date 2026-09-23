# Keyboard shortcut reference

Open **Help → Keyboard Shortcuts…** to browse Jasper's current shortcut mappings.
Opening it again brings the same window forward and preserves the search. The window
remembers its size and position through the normal auxiliary-window state.

Rows are grouped by app category or plugin name. The table shows the action, effective
shortcut, section/plugin and context. The context distinguishes a terminal-window
shortcut from a key used only in a particular field or list. Unbound actions show
**Unassigned**; a plugin default rejected by the shortcut resolver shows its reason
in Context (hover to read long text). This is a read-only reference.

Type an action name, plugin, category or shortcut such as `cmd+shift+h` in the search
field. Modifier order is flexible; `command`/`meta`, `control`, and `option` are accepted
aliases for `cmd`, `ctrl`, and `alt`. **Record shortcut** listens for the keys you press
in this window and finds exact matches, including Tab and Escape. **Stop recording**
returns to ordinary text input; **Clear** clears the filter. Leaving the window stops
recording and releases any held-key state. OS-reserved combinations can still be
handled by the operating system; use text search for those combinations.

The catalog uses the same resolution as terminal windows, so app `[keybindings]`
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

Run `./gradlew check :jasper-app:installDist`. To inspect the real panel headlessly,
run `./gradlew :jasper-app:shortcutHelpPreview`; PNGs are written under
`jasper-app/build/reports/shortcut-help/` for both themes and default/18-point typography.

Native acceptance (user-run):

- Open Help → Keyboard Shortcuts from two terminal windows; only one reference opens.
- Search `remote`, `find`, and `cmd+shift+h`; record that chord, Tab, Escape, Cmd+W and
  Cmd+Q while the recorder is active. App commands should not execute during capture.
- Stop recording, clear the filter, then close the reference normally with Cmd+W.
- Change an app or Remote shortcut setting while the window is open and check the row.
- Hold a recorded key, switch away, release it, then return and type in a terminal.
- Resize the reference and change the app's theme/UI font; headings and controls stay usable.
