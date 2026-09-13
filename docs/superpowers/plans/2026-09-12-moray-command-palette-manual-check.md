# Command palette native acceptance

Run this checklist in a native packaged or development build on the named platform.
Headless tests and PNG inspection do not complete any item below. Use a harmless
shell prompt and confirm its input after each keyboard sequence.

## Activation and routing

- [ ] On macOS, Cmd+K opens the palette and View → Command Palette shows the same behavior.
- [ ] On Windows and Linux, Ctrl+K opens the palette and View → Command Palette shows the same behavior.
- [ ] Numbered and previous/next tab shortcuts work before opening, palette result shortcuts take priority while open, and tab shortcuts work again after dismissal.
- [ ] Holding the opening shortcut or a result shortcut executes once; releasing it leaves no delayed typed event, and a fresh unrelated press still works.
- [ ] Plain digits edit the query. Native select/copy/cut/paste/undo and caret movement work in the input.
- [ ] Opening, searching, executing, dismissing, holding, and releasing palette shortcuts send no stray bytes to the shell.

## Input, focus, and windows

- [ ] IME composition can begin, update, commit, and cancel without arrows or Enter executing a result mid-composition.
- [ ] With two windows, each palette searches and executes only its owning window; held-key tails do not execute in the other window.
- [ ] Running Find restores the terminal target before dispatch, then leaves focus in Find without a later focus steal.
- [ ] Running Settings leaves focus with the OS editor and does not restore terminal focus later.
- [ ] Escape, the opening shortcut again, an outside click, window deactivation, and switching tabs dismiss the palette without click-through or terminal input.

## Geometry and live state

- [ ] Moving and resizing a window keeps the card centered on the selected tab's complete terminal area, including split layouts and display-scale changes.
- [ ] Splitting right/down, zooming/restoring, and changing the focused pane while the palette is open either refreshes or dismisses according to the target lifecycle without resizing terminal grids merely from opening/closing.
- [ ] Closing the target pane/tab during a delayed launch or process exit dismisses safely and never focuses or executes against the stale pane.
- [ ] Changing purple/classic/light appearance while open preserves the query/selection and updates readable colors immediately.
- [ ] Changing, disabling, and re-enabling the palette shortcut while open applies the new map without leaking the old sequence.

## Persistence and accessibility

- [ ] Run three distinct palette commands, restart Moray normally, and confirm those three appear newest first in another window with no duplicates.
- [ ] Confirm the accessible input announces “Search commands” and the list announces “Commands,” result count, and current selection.
- [ ] At native 1×/2× display scaling, inspect focus visibility, card corners/shadow, narrow-title truncation, shortcut badges and contrast on purple, classic dark and light.
