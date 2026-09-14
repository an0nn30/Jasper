# Jasper desk buddy — design

**Status:** Approved by the user on 2026-09-14 (placement, click behavior, size, PNG sprite sheet and the draft frames) and implemented on `claude/desk-buddy`. Amended by an approved follow-up on 2026-09-14: the buddy now has an autonomous idle timeline (stand, sit, tuck, sleep), a fixed one-second hover greeting instead of a dance loop, and double-click rather than single click raises the terminal; the strip grew to fourteen frames.

**Scope:** A small pixel-art Jasper (the turtle with round black glasses) that floats above every window on the desktop while the terminal is open. He blinks while idle, gets bored and falls asleep in his shell when left alone, and greets the pointer with a one-second wave. This deliverable covers the floating window, the sprite pipeline, idle and hover animation, placement and persistence, the visibility lifecycle tied to terminal windows, and the on/off controls. Speech bubbles and status messages are explicitly deferred (see Out of scope).

The reference is the ChatGPT desktop mascot: about 75 × 90 logical px, drawn on a coarse grid where each art pixel is roughly 2 logical px, a 1-pixel dark outline, nearest-neighbour upscaling, idle blinks and winks, and a wave/tilt/kick dance on hover. Jasper must look and behave the same way in his own palette.

## Approaches considered

**Recommended: Swing `JWindow`, per-pixel translucent, always on top, utility type.** Pure Java on the existing stack. Frameless, transparent background, hidden from the Dock and Cmd-Tab through `Window.Type.UTILITY`, non-focusable so the terminal never loses keyboard focus. Java's always-on-top does not follow macOS full-screen Spaces; the ChatGPT mascot lives on the normal desktop too, so this is acceptable.

A native `NSPanel` through the FFM API would allow a non-activating panel that joins all Spaces, at the cost of platform-specific native code plus a Windows equivalent. Not worth it for a mascot. An overlay inside the terminal window would be trivial but clips Jasper to the app's own window, which fails the requirement that he sits on top of windows on the desktop. Neither alternative is selected.

## Sprite pipeline

- **Master art:** `packaging/buddy/jasper-buddy.ase`, one RGBA layer, fourteen frames of 42 × 48 art pixels, transparent background. The user edits it in LibreSprite.
- **Runtime asset:** `jasper-app/src/main/resources/dev/jasper/app/buddy/jasper-buddy.png`, a horizontal strip of the fourteen frames in order (588 × 48). Exported with:

  ```sh
  /Applications/libresprite.app/Contents/MacOS/libresprite --batch packaging/buddy/jasper-buddy.ase \
      --sheet jasper-app/src/main/resources/dev/jasper/app/buddy/jasper-buddy.png
  ```

  Ordinary Gradle builds and tests consume only the committed PNG; LibreSprite and Python are never build requirements.
- **Bootstrap:** `packaging/buddy/generate.py` writes the first `.ase` and the first PNG from the procedural drawing approved on 2026-09-14 (olive skin, tan shell rim, cream belly, cream lenses in black rings, `#332f27` outline; palette taken from `packaging/icons/jasper.svg`). It writes the `.ase` file directly from the documented Aseprite format so no GUI is needed. Once the user hand-edits in LibreSprite, the export command above is the source of truth and the script is history, the same role `packaging/icons/generate.py` plays. `packaging/buddy/README.md` records palette, frame order, frame meaning and both commands.
- **Frame order** is fixed by the Java enum `BuddyFrame`: `IDLE`, `BLINK`, `WINK`, `WAVE_A`, `WAVE_B`, `HOP`, `LEAN_LEFT`, `LEAN_RIGHT`, `SIT`, `SIT_BLINK`, `TUCK`, `SLEEP_A`, `SLEEP_B`, `SLEEP_C`. A test asserts the PNG is exactly fourteen 42 × 48 cells with a transparent margin and opaque pixels in every frame, so a bad export fails the build.
- **Rendering:** each art pixel is drawn as 2 logical px with `RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR`; on a 2× display that is a crisp 4-device-pixel block. The window is 84 × 96 logical px.

## Components

All classes live in `dev.jasper.app` with a `Buddy` prefix, flat like the rest of the module. No interface is introduced; each unit has one implementation.

- **`BuddyFrame`** — the enum above; each constant knows its column index.
- **`BuddySprite`** — loads the sheet from the classpath, validates its dimensions, slices frames, and paints one frame at an integer scale onto any `Graphics2D`. Headless-testable through `BufferedImage`. Loading failures throw `IllegalStateException` with the resource name.
- **`BuddyAnimator`** — pure state machine driven by a supplied nanosecond clock and a seeded `Random`. Standing: `IDLE` with a blink every 3–6 s lasting 120 ms; one blink in four is a `WINK`. Twenty seconds after the boredom clock starts he sits (`SIT`, blinking as `SIT_BLINK` on the same schedule, never winking); at sixty seconds he tucks in (`TUCK` for 400 ms) and then sleeps, cycling `SLEEP_A`, `SLEEP_B`, `SLEEP_C` every 600 ms indefinitely. The pointer entering while he is standing or sitting plays a fixed one-second greeting — `WAVE_A`, `WAVE_B`, `HOP`, `LEAN_LEFT`, `LEAN_RIGHT`, `WAVE_A`, `WAVE_B`, `HOP` at 125 ms per frame — after which he stands with the boredom clock restarted from the greeting's end; entering while he is tucked or asleep first wakes him (`TUCK` 250 ms, `IDLE` 250 ms) and then greets. Entering during a greeting or a wake is ignored; the pointer leaving has no effect. Postures are absolute deadlines measured from the boredom origin, so a tick that arrives arbitrarily late (a closed lid) resolves in constant time and never spins; greeting and wake steps are replayed at most their own length. API: `frame()`, `nextDueNanos()` (empty only after `hidden()`), `tick(now)`, `hoverEntered(now)`, `shown(now)` and `hidden()`. While shown a due time is always present; `hidden()` clears the schedule and resets to `IDLE`. The view schedules one Swing timer for the next due time only, so no timer runs when nothing changes and no timer runs while the buddy is hidden.
- **`BuddyPlacement`** — pure functions. `defaultLocation(usableBounds, size)` puts the sprite in the bottom-right corner of the primary screen's usable area (inside Dock and menu-bar insets) with a 24 px margin. `clamp(saved, screens, size)` returns the saved point if at least half the sprite lies inside some screen's usable bounds, otherwise the nearest point that keeps it fully on the closest screen.
- **`BuddyStateFile`** — `buddy.toml` in the `AppDirs` root beside `command-history.toml`: `version = 1`, `x`, `y` (integers, logical screen coordinates). Same strict read (size cap, exact key set, version check) and atomic temp-file-then-move write as `CommandHistoryFile`. A missing or invalid file yields "no saved position" and a warning in the app log; it is never repaired silently.
- **`BuddyVisibility`** — pure rule: shown when the buddy is enabled (saved default combined with the session toggle) and at least one terminal window is showing and not iconified. Hidden otherwise, including when every window is minimized or closed.
- **`BuddyWindow`** — the thin `JWindow` shell, the counterpart of `TerminalWindow`. Transparent background, `setAlwaysOnTop(true)`, `Window.Type.UTILITY`, `setFocusableWindowState(false)`. Mouse handling: the pointer entering feeds the animator (leaving is not reported, because it has no effect); press then move beyond 3 px drags the window and writes the position through `BuddyStateFile` on release; a left double-click without a drag raises the most recently active terminal window, while a single click does nothing beyond the greeting; right-click (or the platform popup trigger) shows a `BuddyBubble` beside him with one entry, Hide Jasper, and any left press dismisses it. Paints the animator's current frame through `BuddySprite`. Not unit-tested; the user checks it on the desktop.
- **`BuddyBubble`** — a reusable translucent bubble in its own non-focusable always-on-top `JWindow`: a dark rounded rectangle (fill `rgba(30, 30, 32, 0.92)`, a hairline white border, 14 px corners) holding a bold white title, an optional grey detail line and an optional 18 px glyph at the right edge, drawn as antialiased vector UI rather than pixel art by `BuddyBubblePanel` and truncated with an ellipsis between 120 and 320 px wide. That is the message style for later status bubbles; the menu style used by right-click is a compact regular-weight pill (13 px text, 12 × 7 px padding, 9 px corners, natural width). `BuddyBubblePlacement` is the pure geometry: `beside` puts it 8 px to the right of the anchor and vertically centred on it, flipping to the left when it would not fit, and `above` centres it 10 px over the anchor's top; both clamp inside the anchor's usable screen. Because the window never takes focus it dismisses on pointer exit, on a click, on a left press on Jasper and after five seconds. The right-click menu is its first use; the deferred status messages will reuse it (through `above`).
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

Status messages (SFTP progress, long-command completion) — the `BuddyBubble` component now exists and `BuddyBubblePlacement.above` is ready for them, but nothing produces such a message yet — sound, click opening the command palette, reacting to terminal activity (typing, running commands: the idle timeline is driven by pointer interaction only), following the terminal window, following full-screen Spaces, and Windows native acceptance. The window is sized to the sprite alone; a later plan can grow it upward and add a `say` API once a message producer exists.

## Testing

Headless unit tests with the existing JUnit and AssertJ setup:

- `BuddySprite`: sheet dimensions and frame count, slicing returns the right cell, scaled painting produces exact 2 × 2 (and 3 × 3) blocks with no blended edge pixels.
- `BuddyAnimator`: deterministic blink schedule within 3–6 s, wink ratio over a seeded run, the sit/tuck/sleep timeline on schedule, hover greeting in the fixed order at 125 ms per frame followed by a restarted boredom clock, hover ignored mid-greeting, waking from tuck and from sleep, an arbitrarily late tick landing in the right posture without spinning, a due time always present while shown, and `hidden()` leaving no schedule and showing `IDLE`.
- `BuddyPlacement`: default corner with Dock insets, clamping on and off screen, multiple screens.
- `BuddyStateFile`: round trip, missing file, oversized file, wrong version, extra keys, non-integer values, atomic write leaves no temp file.
- `BuddyVisibility`: every combination of enabled/session toggle and window states.
- `ConfigLoader`/`ConfigTemplate`: `buddy.enabled` parsing, template round trip, live delivery to the application.
- `WindowCommands`/`WindowChrome`: `view.buddy` registered, checkbox present in the View menu, palette search finds "Show Jasper".
- `generate.py` output is committed; a test reads the committed PNG, so the script itself is not run by Gradle.

`./gradlew check` stays headless. Desktop acceptance is user-run and recorded in a manual checklist: transparency, always on top over other apps, absence from Dock and Cmd-Tab, drag and remembered position across restart, hover greeting and return to standing, the sit/sleep timeline, double-click raising the terminal, hiding on minimize and reappearing on restore, hiding when the last window closes, the View menu and palette toggles, and `buddy.enabled = false` taking effect live.
