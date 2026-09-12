# Configuration status render check

Rendered the actual Swing `WindowStatusBar` after installing each built-in FlatLaf theme, at 958 and 320 logical pixels wide. Each strip is 30 logical pixels high; images use 2x scale. The headings and spacing between strips are review annotations, not application UI.

Each image contains missing-file defaults, a valid file, a warning at line 4 and an error at line 9. The shell, directory, grid, config path and diagnostics are fixture values. No JFrame, terminal session, user config file or native editor was opened.

| Theme | Full width | Narrow width |
|---|---|---|
| Dark | [958px](config-status-dark.png) | [320px](config-status-dark-narrow.png) |
| Light | [958px](config-status-light.png) | [320px](config-status-light-narrow.png) |

Inspected all four images. The status surface remains seamless, semantic colors are readable, and the directory yields space to the grid and config indicator. The measured minimum remains 0 × 30 for every state and width. The native editor, real window behavior and keyboard focus still belong to the user-run acceptance checklist.
