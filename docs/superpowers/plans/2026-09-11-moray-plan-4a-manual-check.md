# Plan 4a native acceptance

Pending user execution after implementation. Agents do not launch the GUI or editor. During development, run from `.worktrees/plan-4` with `./gradlew :moray-app:run`; after integration use the main checkout.

- [ ] Start without a config file: built-in defaults apply and startup does not create directories or a file. Existing terminal/chrome behavior remains intact.
- [ ] Click Settings: a commented template is created only if absent, then opens in the default editor or is revealed in Finder/Explorer. Clicking again preserves existing content. An editor failure is reported visibly.
- [ ] Edit supported settings: window.tab_height, window.toolbar, window.status_bar, font.size, colors.theme and keybindings. Saved changes apply within the polling interval; Reload config forces a reread. Restart honors the file.
- [ ] Use multiple windows, hidden tabs and zoomed splits. Configured font/theme/chrome changes reach the intended owners without restarting shells or clearing selection/find. New panes and windows get current saved defaults.
- [ ] Make a temporary View/font adjustment, then edit a different config key: the unrelated runtime override survives. Editing that same saved field reapplies it. Font reset uses the configured font size.
- [ ] Override a shortcut and set another to none: menus, toolbar hints and actual terminal/root dispatch agree; the old shortcut is removed. Native copy/paste in the find field continues to work.
- [ ] Introduce a syntax/type error: retain last-good settings and show a red indicator with source line. Unknown keys/actions warn; invalid values use their defaults and report errors. Correct the file and observe recovery. Click diagnostics to view/copy the plain-text details.
- [ ] Long config paths and error messages do not inflate the window minimum width or obscure live shell/path/grid metadata. Inspect status and config indicator in dark/light.
- [ ] Delete the file: defaults return. Test `--config` with another file and verify Settings opens that file; other app paths remain unchanged.
- [ ] Close the final window while a reload/editor request is pending: no late UI changes or lingering polling process. Native Quit still closes sessions normally.

This slice intentionally supports existing live controls only. Additional terminal options, custom theme files, automatic appearance, logging/launcher refinements and packaging remain later Plan 4 deliverables. This checklist does not replace the terminal benchmark or daily-use acceptance gate.
