# Plan 4b native acceptance

Pending user execution after implementation. Agents do not launch native windows, login shells, editors, benchmarks or audio. During development use `/Users/dustin/projects/moray/.worktrees/plan-4b`; after integration use the main checkout. Start with `./gradlew :moray-app:run`.

- [ ] Keep existing terminals and a long-running program open while editing config. Unrelated changes must not restart shells, lose find controls, change manual font size or reset a manually selected global theme.
- [ ] Change font family, fallbacks, ligatures and line height. Check ordinary text, ligatures, Nerd Font icons, emoji and CJK in normal/hidden tabs and zoomed splits. A missing family should follow JBR fallback behavior. Font size reset uses the saved size.
- [ ] Use line height1.0 and1.5. Check selection, links, mouse-reporting programs, search highlights, cursor placement and streamed-output resizing. Height changes must keep hit testing aligned with painted cells; ordinary width reflow retains its documented row-invalidation behavior.
- [ ] Make a temporary per-pane font-size adjustment, then change font.family or line_height: the temporary size survives. Change font.size: the new saved size reaches existing views. Create panes while configuration reloads; their live appearance reflects the latest settings when ready.
- [ ] Change Option-as-Meta modes and cursor shape/blink. Check both Option keys on the physical keyboard. A program's explicit cursor request takes precedence until it resets the terminal.
- [ ] Toggle copy-on-select and inactive-pane dimming. Focus windows, split/zoom and switch themes; dimming must continue using the saved value. Native copy/paste in the find field remains intact.
- [ ] Exercise visual, sound and none bell modes deliberately. Repeated bells should not leave a stuck overlay; closing or reparenting a view must not produce late effects. Visual strength/duration and sound are native acceptance, not claims from headless tests.
- [ ] Configure a shell executable and arguments with spaces. New panes receive exact arguments, environment overrides and scrollback defaults; existing sessions continue unchanged. Restore an empty executable and check default login-shell behavior. Invalid executables/directories report useful launch errors.
- [ ] Change session settings while a shell launch is queued. The pending request retains the settings captured when requested; the next pane gets the new defaults. Each pane's shell label remains accurate.
- [ ] Set window.columns/lines and open a new window. The initial desired grid uses configured fonts/padding/chrome and is constrained by the available display. Existing windows keep their size; delayed launches and subsequent reloads do not repack them. Move between displays and check native decorations/minimum dimensions.
- [ ] Introduce wrong types, invalid values and unknown nested fields. Confirm positioned diagnostics, last-good retention for type errors and per-field fallback for invalid values. Correct the file and confirm recovery. Existing files must never be rewritten by Settings.

Custom themes, automatic system appearance, logging/launcher cleanup and packaging remain subsequent Plan4 work. Retain the Phase1 benchmark, platform CI and two-week daily-use acceptance gates.
