# Normal and Compact view modes

Status: SDD complete in an isolated worktree based on main `43894416`: both task reviews and the whole-branch code review approved the implementation with no critical or important findings. The minor stale checklist reference is corrected. Final controller full check on `713b53b5` passed 8,373 tests with no failures/errors and 6 skipped; focused headless verification passed 97 tests with no failures/errors/skips. Source hygiene (1,747 Java files) and diff checks passed. Inspected dark/default-font and light/18pt headless Appearance previews fit the row and restart notice; the full theme preview matrix was intentionally stopped after these renders (SIGTERM, exit 143). Native acceptance remains [user-run](../plans/2026-10-03-jasper-view-modes-manual-check.md). The user supplied the visual contract; restart-required remains the announced implementation default; the optional user preference question is unanswered.

## Intent

Add Settings > Appearance > View mode with Normal and Compact. Normal is the current window without visual or behavioral changes. Compact uses the native macOS title bar, removes the title search field and all toolbar buttons, and opens the existing command palette as a floating card in the upper part of the workspace. Preserve the activated palette design; only placement changes. Tabs, dock stripes, panels, status, shortcuts and running sessions retain their behavior.

## Chosen approach

Reuse the existing settings schema, native decorations, and palette's existing `positioned` geometry with Compact's activated width retained at scaled 560 logical pixels. Normal's existing fallback width remains unchanged. Do not create a second palette or emulate a native title bar with a Swing label. The saved key is `window.view_mode = "normal" | "compact"`, default `normal`.

Switching mode takes effect after application restart (the optional user preference question is unanswered; this is the announced implementation default). Appearance displays the existing restart notice. The application captures the startup mode once; all windows created before the next restart retain it, including windows opened after saving a different mode. Reloading other settings does not change the active mode.

Compact removes the entire workspace header, including plugin toolbar contributions and overflow. Toolbar menu commands cannot reveal controls in Compact. Keep the saved toolbar preference so Normal restores it after restart. The native title uses existing working-context metadata. Native appearance follows the selected UI theme. macOS Compact does not install full-window content, transparent title bar, hidden native title text, or JBR custom title geometry. Normal retains those exact existing behaviors.

On Windows/Linux, Compact likewise uses native decorations with menus available and no workspace header. Normal continues to respect `window.native_decorations`. On macOS, auxiliary windows and dialogs keep their existing appearance; header/palette changes govern workspace windows. Windows/Linux share the existing global native-decoration defaults when Compact is selected.

Palette activation through existing menu/keyboard/plugin paths remains available. Compact retains the scaled 560-logical-pixel activated-card width, clamped to narrow workspaces, and uses the existing floating geometry: horizontally centered over the complete workspace including docked panels, with its top anchored one fifth down, clamped inside small windows. Reuse the existing component, styling, scope selection, keyboard handling, dismissal, focus restoration and captured target lifecycle.

## Boundaries and verification

No terminal, SDK, plugin API or theme format changes. Only config/settings, startup mode selection and workspace chrome integration change. Preserve ConfigSnapshot compatibility constructors and builder copy semantics. Invalid values follow schema diagnostics and defaults.

Use headless RED/GREEN tests for saved values, Appearance editing, startup capture, native-root properties, toolbar suppression across reload/contribution changes, floating layout, palette dismissal and Normal regressions. Run full `./gradlew check`, architecture gates, source hygiene and diff checks. Native GUI acceptance is user-run: both modes, theme changes, traffic lights/drag/fullscreen, palette placement/focus, and reopening windows after saving a pending mode. Never launch GUI or benchmark unattended.
