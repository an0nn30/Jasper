# Jasper desk buddy — design

**Status:** Approved by the user on 2026-09-14 (placement, click behavior, size, PNG sprite sheet and the draft frames). Not yet implemented. The implementation plan follows this spec.

**Scope:** A small pixel-art Jasper (the turtle with round black glasses) that floats above every window on the desktop while the terminal is open. He blinks while idle and dances when the pointer hovers over him. This deliverable covers the floating window, the sprite pipeline, idle and hover animation, placement and persistence, the visibility lifecycle tied to terminal windows, and the on/off controls. Speech bubbles and status messages are explicitly deferred (see Out of scope).

The reference is the ChatGPT desktop mascot: about 75 × 90 logical px, drawn on a coarse grid where each art pixel is roughly 2 logical px, a 1-pixel dark outline, nearest-neighbour upscaling, idle blinks and winks, and a wave/tilt/kick dance on hover. Jasper must look and behave the same way in his own palette.

## Approaches considered

**Recommended: Swing `JWindow`, per-pixel translucent, always on top, utility type.** Pure Java on the existing stack. Frameless, transparent background, hidden from the Dock and Cmd-Tab through `Window.Type.UTILITY`, non-focusable so the terminal never loses keyboard focus. Java's always-on-top does not follow macOS full-screen Spaces; the ChatGPT mascot lives on the normal desktop too, so this is acceptable.

A native `NSPanel` through the FFM API would allow a non-activating panel that joins all Spaces, at the cost of platform-specific native code plus a Windows equivalent. Not worth it for a mascot. An overlay inside the terminal window would be trivial but clips Jasper to the app's own window, which fails the requirement that he sits on top of windows on the desktop. Neither alternative is selected.

## Sprite pipeline

- **Master art:** `packaging/buddy/jasper-buddy.ase`, one RGBA layer, eight frames of 42 × 48 art pixels, transparent background. The user edits it in LibreSprite.
- **Runtime asset:** `jasper-app/src/main/resources/dev/jasper/app/buddy/jasper-buddy.png`, a horizontal strip of the eight frames in order (336 × 48). Exported with:

  ```sh
  /Applications/libresprite.app/Contents/MacOS/libresprite --batch packaging/buddy/jasper-buddy.ase \
      --sheet jasper-app/src/main/resources/dev/jasper/app/buddy/jasper-buddy.png
  ```

  Ordinary Gradle builds and tests consume only the committed PNG; LibreSprite and Python are never build requirements.
- **Bootstrap:** `packaging/buddy/generate.py` writes the first `.ase` and the first PNG from the procedural drawing approved on 2026-09-14 (olive skin, tan shell rim, cream belly, cream lenses in black rings, `#332f27` outline; palette taken from `packaging/icons/jasper.svg`). It writes the `.ase` file directly from the documented Aseprite format so no GUI is needed. Once the user hand-edits in LibreSprite, the export command above is the source of truth and the script is history, the same role `packaging/icons/generate.py` plays. `packaging/buddy/README.md` records palette, frame order, frame meaning and both commands.
- **Frame order** is fixed by the Java enum `BuddyFrame`: `IDLE`, `BLINK`, `WINK`, `WAVE_A`, `WAVE_B`, `HOP`, `LEAN_LEFT`, `LEAN_RIGHT`. A test asserts the PNG is exactly eight 42 × 48 cells with a transparent margin and opaque pixels in every frame, so a bad export fails the build.
- **Rendering:** each art pixel is drawn as 2 logical px with `RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR`; on a 2× display that is a crisp 4-device-pixel block. The window is 84 × 96 logical px.

## Components

All classes live in `dev.jasper.app` with a `Buddy` prefix, flat like the rest of the module. No interface is introduced; each unit has one implementation.

- **`BuddyFrame`** — the enum above; each constant knows its column index.
- **`BuddySprite`** — loads the sheet from the classpath, validates its dimensions, slices frames, and paints one frame at an integer scale onto any `Graphics2D`. Headless-testable through `BufferedImage`. Loading failures throw `IllegalStateException` with the resource name.
- **`BuddyAnimator`** — pure state machine driven by a supplied nanosecond clock and a seeded `Random`. Idle: `IDLE` with a blink every 3–6 s lasting 120 ms; one blink in four is a `WINK`. Hover: the dance loop `WAVE_A`, `WAVE_B`, `HOP`, `LEAN_LEFT`, `LEAN_RIGHT` at 8 frames per second, repeating while hovered. Leaving hover finishes the current cycle and returns to idle. API: `frame()`, `nextDueNanos()` (empty only after `hidden()`), `tick(now)`, `hoverEntered(now)`, `hoverExited(now)`, `shown(now)` and `hidden()`. While shown there is always a pending blink or dance step, so a due time is always present; `hidden()` clears the schedule and resets to `IDLE`. The view schedules one Swing timer for the next due time only, so no timer runs when nothing changes and no timer runs while the buddy is hidden.
- **`BuddyPlacement`** — pure functions. `defaultLocation(usableBounds, size)` puts the sprite in the bottom-right corner of the primary screen's usable area (inside Dock and menu-bar insets) with a 24 px margin. `clamp(saved, screens, size)` returns the saved point if at least half the sprite lies inside some screen's usable bounds, otherwise the nearest point that keeps it fully on the closest screen.
- **`BuddyStateFile`** — `buddy.toml` in the `AppDirs` root beside `command-history.toml`: `version = 1`, `x`, `y` (integers, logical screen coordinates). Same strict read (size cap, exact key set, version check) and atomic temp-file-then-move write as `CommandHistoryFile`. A missing or invalid file yields "no saved position" and a warning in the app log; it is never repaired silently.
- **`BuddyVisibility`** — pure rule: shown when the buddy is enabled (saved default combined with the session toggle) and at least one terminal window is showing and not iconified. Hidden otherwise, including when every window is minimized or closed.
- **`BuddyWindow`** — the thin `JWindow` shell, the counterpart of `TerminalWindow`. Transparent background, `setAlwaysOnTop(true)`, `Window.Type.UTILITY`, `setFocusableWindowState(false)`. Mouse handling: enter/exit feeds the animator; press then move beyond 3 px drags the window and writes the position through `BuddyStateFile` on release; press then release without movement raises the most recently active terminal window; right-click (or the platform popup trigger) shows a `JPopupMenu` with one item, Hide Jasper. Paints the animator's current frame through `BuddySprite`. Not unit-tested; the user checks it on the desktop.
- **`JasperApplication`** owns at most one `BuddyWindow`. `TerminalWindow` reports shown, iconified, deiconified, activated and closed events to the application, which remembers the most recently activated window and re-evaluates `BuddyVisibility` on every change. The buddy is created lazily the first time it should be shown and disposed on shutdown.

## Configuration and commands

- New key `buddy.enabled` (boolean, default `true`) in `ConfigLoader`, `ConfigSnapshot`, `ConfigTemplate`, the root `config.example.toml` and `docs/configuration.md`. Live: turning it off hides Jasper and turning it on shows him again, through the existing `ConfigurationController` delivery. The controller notifies the application, not each window, because the buddy is application-owned.
- New view command `view.buddy`, labelled Show Jasper, registered in `WindowCommands` beside `view.status_bar` and shown as a `JCheckBoxMenuItem` in the View menu after Status Bar. The command palette picks it up through the existing registry. It is a session toggle layered over the saved default, the same split the status-bar and tab-height settings use: a saved-value change resets the session toggle. Right-click Hide Jasper invokes the same toggle. `ActionId` and its keybinding catalog are unchanged.

## Error handling

- Headless environment, or a toolkit that reports no per-pixel translucency or no always-on-top support: the buddy is never created and one warning is logged once. Everything else runs unchanged.
- Sprite resource missing or malformed: tests fail; in production the failure is logged once and the buddy is disabled for the session.
- Saved position off every screen: clamped onto the nearest screen, never discarded.
- State file write failure: logged; the buddy keeps its on-screen position for the session.

## Out of scope

Speech bubbles and status messages (SFTP progress, long-command completion), sound, click opening the command palette, following the terminal window, following full-screen Spaces, and Windows native acceptance. The window is sized to the sprite alone; a later plan can grow it upward and add a `say` API once a message producer exists.

## Testing

Headless unit tests with the existing JUnit and AssertJ setup:

- `BuddySprite`: sheet dimensions and frame count, slicing returns the right cell, scaled painting produces exact 2 × 2 (and 3 × 3) blocks with no blended edge pixels.
- `BuddyAnimator`: deterministic blink schedule within 3–6 s, wink ratio over a seeded run, hover starts the dance at 8 fps in the fixed order, exit completes the cycle then idles, a due time is always present while shown, and `hidden()` leaves no schedule and shows `IDLE`.
- `BuddyPlacement`: default corner with Dock insets, clamping on and off screen, multiple screens.
- `BuddyStateFile`: round trip, missing file, oversized file, wrong version, extra keys, non-integer values, atomic write leaves no temp file.
- `BuddyVisibility`: every combination of enabled/session toggle and window states.
- `ConfigLoader`/`ConfigTemplate`: `buddy.enabled` parsing, template round trip, live delivery to the application.
- `WindowCommands`/`WindowChrome`: `view.buddy` registered, checkbox present in the View menu, palette search finds "Show Jasper".
- `generate.py` output is committed; a test reads the committed PNG, so the script itself is not run by Gradle.

`./gradlew check` stays headless. Desktop acceptance is user-run and recorded in a manual checklist: transparency, always on top over other apps, absence from Dock and Cmd-Tab, drag and remembered position across restart, hover dance and return to idle, click raising the terminal, hiding on minimize and reappearing on restore, hiding when the last window closes, the View menu and palette toggles, and `buddy.enabled = false` taking effect live.
