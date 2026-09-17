# iTerm-style title bar and session titles

The user's 2026-09-17 screenshots at 14:48:52, 14:49:48 and 14:50:27 authorize
replacing the previous Jasper tab presentation. The first shows Jasper truncating
a tmux title in a fixed 160-point slot while repeating it at the right. The others
show iTerm's compact integrated tabs, including `~ (-zsh)` outside tmux.

## Presentation

- Keep the configurable 38-point row and public JBR native title-bar integration.
- Reserve 120 points for native stoplights and window dragging, expanded when the
  runtime reports larger native-control bounds. Native controls remain OS-owned.
- With one tab, hide the strip and center a plain window title. With multiple tabs,
  hide that label and divide all remaining width equally between tabs, leaving
  a 24-point add button at the right edge.
- Use the resolved macOS system font at 13 points, centered titles, trailing actual
  shortcuts, hover-only close controls at the left, and thin tab dividers. Remove
  terminal icons and the sliding underline. Keep the current light/dark theme.
- Keep 180ms arrival/departure width motion, retargeted from the current widths.
  Normalize animated widths to fill the available bar. Closing down to one hides
  the strip immediately. Overflow, resizing and reordering settle geometry.
- Full title strings remain available to accessibility and tooltips. Ellipsis is
  still necessary when the available width cannot contain a title.

The [iTerm appearance documentation](https://iterm2.com/documentation-preferences-appearance.html)
describes compact tabs in the title bar, hiding the strip for one tab, stretching
tabs to fill the bar, hover close buttons and shortcut labels.

## Title sources

The tab/window title is `<OSC title> (<foreground job>)`, or `<directory>
(<foreground job>)` without OSC. Home is `~`; root is `/`. Manual names override
both components. The job comes from the PTY's foreground process group and Java
process metadata, queried every 500ms off Swing's EDT; no `ps` helper process is
spawned. Shell launch options identify login shells for the leading dash. An
unavailable process falls back to the configured program label. The polling timer
stops on process exit and pane disposal. OSC 7 remains the directory source.

The buddy retains the full program title without the parenthesized job, and uses
the captured command as fallback. Its ordered title snapshots and finished-title
capture remain independent of process polling.

The user's running tmux server used its default title format:
`#S:#I:#W - "#T" #{session_alerts}`. That wrapper appeared in both Jasper and iTerm;
it was not an escape-sequence decoding bug. With explicit user authorization,
`~/.tmux.conf` now uses the compact forwarding format documented in
[configuration](../configuration.md#automatic-tab-and-notification-titles).
`set-titles on` was already enabled. Only `set-titles-string` was changed on the
running server; the full configuration and plugins were not reloaded. Backup:
`~/.tmux.conf.jasper-backup-20260917-145844`. The live shell title evaluated to `~`,
and sample custom titles remained unchanged.

## Verification and limits

Run `./gradlew :jasper-app:titleBarPreview` to render actual Swing title-bar
components at 2x into `jasper-app/build/reports/title-bar/`. These fixtures create
no JFrame and launch no shell. They cover a single tab, two shell tabs, tmux-style
titles, narrow windows, and both themes. They deliberately do not fabricate
native stoplights or the macOS window shadow.

Headless tests exercise layout, hide/show, hover, live shortcut changes, selection,
reorder, overflow, interrupted width motion, cleanup and the unchanged terminal
bounds during animation. Controlled PTY tests exercise shell → foreground job →
shell transitions without OSC/integration, login-shell names, real tmux title
forwarding, manual names, OSC 0/1/2 and finished-notification ordering.

Native title-bar dragging, native controls and appearance on the user's desktop
still need user-run acceptance under the repository's no-unattended-GUI rule.
The screenshot establishes layout, not every undocumented iTerm behavior;
Jasper retains its own window management and theme colors.
