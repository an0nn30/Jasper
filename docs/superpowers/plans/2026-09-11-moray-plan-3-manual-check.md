# Plan 3 GUI acceptance run

Status: pending user execution. AGENTS.md reserves GUI launches and benchmarks for the user. Run these checks on macOS and record failures; the automated suite does not prove the visual acceptance criteria.

1. Launch from isolated worktree with ./gradlew :moray-app:run. Confirm shell prompt, SVG toolbar, tab strip, status.
2. New tab (Cmd+T). Rename F2; emit OSC title; ensure explicit rename persists. Try a long shell title, tab name and OSC 7 directory; the window must still shrink normally and full tab titles remain available in tooltips.
3. In a pane, emit OSC 7 for /tmp. Cmd+T and split should launch there; verify pwd.
4. Cmd+D, Cmd+Shift+D creates nested splits with distinct shell sessions. Print distinct markers in each pane.
5. Directional Cmd+Option arrows select expected pane. Cmd+Shift+Enter zoom and restore; markers and divider ratios survive.
6. Cmd+F searches emitted text; verify count, next/previous (including Enter/Shift+Enter immediately after typing), Case/Regex, invalid regex feedback retained during navigation, Escape returns typing to terminal. Confirm native field Copy/Paste and application shortcuts while a find control is focused.
7. Cmd+= / Cmd+- / Cmd+0 changes/reset focused font only. Cmd+K clears history without blanking live screen. With nested splits and enlarged fonts, shrink the window and drag dividers; verify panes retain a usable minimum size and the shell grid matches the display.
8. New window Cmd+N; close one window and verify remaining window accepts input.
9. Drag tabs, middle-click a nonselected tab, close pane, close last tab/window; remaining targets remain usable.
10. Toolbar modes and status visibility work. Light/dark chrome readable. Settings/reload disabled with explanatory tooltip, no false green config claim.
11. Context menu local right-click; app mouse-reporting right-click stays in tmux. Clipboard and Shift selection preserved.
12. When no game or VM is running, benchmark separately ./gradlew :moray-app:bench; record actual MB/s.

Manual daily-use follow-up: tmux drag-resize, vim/htop, Option/dead keys and IME, Unicode/emoji/CJK, resizing with output, two-week switch-over gate. GUI smoke cannot substitute for this.
