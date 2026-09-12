# Compact terminal padding

The September12 iTerm comparison supersedes the earlier24px terminal margin. Terminal panes now use4logical pixels on every side; initial window sizing derives its padding from the same constant.

These renders paint the actual Swing components headlessly at958×958logical pixels,2×scale. The terminal pane measures958×837; its view is at(4,4) and measures950×829. Title38, toolbar53 and status30 retain their existing heights. The prompt is a controlled test fixture. No native frame, traffic lights or login shell is created.

- [Dark preview](compact-padding-dark.png)
- [Light preview](compact-padding-light.png)

Both renders were visually inspected. Native macOS frame behavior remains user-run. The older mock images and measurements are retained as historical evidence.
