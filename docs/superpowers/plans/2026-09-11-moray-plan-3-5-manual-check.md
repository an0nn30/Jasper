# Plan 3.5 native acceptance

Pending user execution. Automated headless checks cannot establish native window behavior or subjective visual quality. The user's earlier positive Plan 3 feedback remains recorded separately.

Run from the active Plan 3.5 checkout with `./gradlew :moray-app:run`.

- [ ] Dark and light appearance change the terminal, title bar, tabs, toolbar, menus, find controls, dividers and status together. Check both with macOS set to the opposite appearance.
- [ ] Create two windows, several tabs and nested splits; change appearance with some tabs hidden and a pane zoomed. Restore everything: colors match, content/find/selection/fonts and split ratios remain.
- [ ] Change theme while a new shell is starting; the new pane uses the latest theme. New windows inherit it.
- [ ] Native traffic lights have clear spacing; minimize, close, fullscreen/restore, drag and double-click title bar behave normally. Check long shell/tab titles, inactive windows and a display with different scaling.
- [ ] Custom title text stays legible and clipped; window minimum is usable and not inflated by long text. Hiding the toolbar leaves title-bar behavior intact.
- [ ] Toolbar matches selected study B: distinct two-tone action colors, readable light/dark outlines and muted disabled Settings/Reload. Try icons with labels, icons only and hidden; check Split popup, tooltips and shortcuts.
- [ ] Native screen menus, Quit, independent window closure, find-field editing and ordinary terminal input still work.
- [ ] ANSI output, explicit RGB output, cursor, search hits, selection and dimmed panes are readable in both themes. Existing output recolors without a shell restart.

No benchmark is implied by these checks. Run the benchmark only when requested and with no game or VM running. Cross-platform CI and the Phase 1 daily-use gate remain separate acceptance work.
