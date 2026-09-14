# Desk buddy — native acceptance checklist (user-run)

Run the app from the `claude/desk-buddy` branch (`./gradlew :jasper-app:run`). Tick each item.

- [ ] Jasper appears in the bottom-right corner above the Dock when the first window opens, 84 × 96 px, crisp 2-px art pixels, transparent background (no square behind him).
- [ ] He stays above other apps' windows (click into Safari/Finder over him: he remains on top).
- [ ] He is absent from the Dock and from Cmd-Tab.
- [ ] Idle: he blinks every few seconds; roughly one blink in four is a wink.
- [ ] Hover: he waves, hops and leans in a loop; moving the pointer away finishes the cycle and he returns to idle.
- [ ] Drag: he follows the pointer; after release, quit and relaunch — he reappears where you left him (`~/.config/jasper/buddy.toml` holds `x`/`y`).
- [ ] Click (no drag): the most recently active Jasper window comes to the front and gets focus; the terminal keeps keyboard focus at all times (typing never goes to Jasper).
- [ ] Right-click → Hide Jasper hides him; View → Show Jasper shows him again and the checkbox reflects the state; Cmd+K "Show Jasper" also toggles.
- [ ] Minimize the only window: he disappears; restore it: he returns. With two windows, minimizing one keeps him.
- [ ] Close the last window: he disappears with the app.
- [ ] `buddy.enabled = false` in `config.toml` hides him within a second; `true` brings him back and resets any session toggle.
- [ ] External display: drag him onto it, quit, unplug the display, relaunch — he is pulled back onto the built-in screen.
- [ ] Open packaging/buddy/jasper-buddy.ase in LibreSprite: all eight frames load with the transparent background intact (batch export from the command line hung during development, so this is the only check that LibreSprite reads the master).

Record findings and the date below.
