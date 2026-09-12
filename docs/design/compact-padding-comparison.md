# Compact terminal padding

The September 12 iTerm comparison supersedes the earlier 24px terminal margin. Terminal panes now use 4 logical pixels on every side; initial window sizing derives its padding from the same constant.

These renders paint the actual Swing components headlessly at 958×958 logical pixels, 2× scale. The terminal pane measures 958×837; its view is at (4,4) and measures 950×829. Title 38, toolbar 53 and status 30 retain their existing heights. The prompt is a controlled test fixture. No native frame, traffic lights or login shell is created.

- [Dark preview](compact-padding-dark.png)
- [Light preview](compact-padding-light.png)

Both renders were visually inspected. Native macOS frame behavior remains user-run. The older mock images and measurements are retained as historical evidence.
