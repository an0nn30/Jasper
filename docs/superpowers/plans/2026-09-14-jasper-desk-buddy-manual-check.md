# Desk buddy — native acceptance checklist (user-run)

Run the app from the `claude/desk-buddy` branch (`./gradlew :jasper-app:run`). Tick each item.

- [ ] Jasper appears in the bottom-right corner above the Dock when the first window opens, 84 × 96 px, crisp 2-px art pixels, transparent background (no square behind him).
- [ ] Appearance: he fades in over half a second with yellow and white sparkles around him, sparkles fade out, he waves for a second, then stands. Repeats on restore from minimize and when re-enabled.
- [ ] He stays above other apps' windows (click into Safari/Finder over him: he remains on top).
- [ ] He is absent from the Dock and from Cmd-Tab.
- [ ] Idle: he blinks every few seconds; roughly one blink in four is a wink.
- [ ] Hover: he waves, hops and leans for about one second, then stands still; hovering again during the wave does nothing; hovering after it finished waves again.
- [ ] Drag: he follows the pointer; after release, quit and relaunch — he reappears where you left him (`~/.config/jasper/buddy.toml` holds `x`/`y`).
- [ ] Single click does nothing (beyond the wave). Double-click brings the most recently active Jasper window to the front with focus; the terminal keeps keyboard focus at all times.
- [ ] Leave him alone: he sits after 20 s, tucks into his shell at 60 s and sleeps with Zs rising every 0.6 s; hovering pops him out, he stands, then waves.
- [ ] Close the lid or sleep the Mac for a few minutes with him asleep: on wake he is still asleep and animating (no burst of catch-up frames).
- [ ] Right-click: a dark rounded bubble reading Hide Jasper appears beside him; it lightens on hover, clicking it hides him, moving the pointer off it dismisses it, and it disappears by itself after 5 s. View → Show Jasper shows him again and the checkbox reflects the state; Cmd+K "Show Jasper" also toggles.
- [ ] Minimize the only window: he disappears; restore it: he returns. With two windows, minimizing one keeps him.
- [ ] Close the last window: he disappears with the app.
- [ ] `buddy.enabled = false` in `config.toml` hides him within a second; `true` brings him back and resets any session toggle.
- [ ] External display: drag him onto it, quit, unplug the display, relaunch — he is pulled back onto the built-in screen.
- [ ] Open packaging/buddy/jasper-buddy.ase in LibreSprite: all seventeen frames load with the transparent background intact (batch export from the command line hung during development, so this is the only check that LibreSprite reads the master).
- [ ] No trails or ghosting while he blinks and dances (each frame fully replaces the previous one).
- [ ] The bubble goes away without hiding him when you left-press Jasper or move the pointer away from it.
- [ ] Cmd+H (Hide Jasper app): note whether he stays on screen while the app is hidden; the spec only requires hiding on minimize, so record the observed behaviour for a follow-up decision.

Record findings and the date below.
