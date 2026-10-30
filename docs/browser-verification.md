# Browser verification

Hand checks of the Browser plugin that headless tests cannot make, because they need Chromium on screen. Each section
is a dated record of one batch the user ran.

## Plan 4a, Task 13: the browser core on macOS

### Batch 1: the GUI check (2026-10-30, macOS aarch64)

- **How it was run:** `./gradlew :jasper-app:run` from `claude/browser-core`, on JBR 25.0.4.1 b635.75 with JCEF.
- **First attempt: failed.**
  - New Browser Tab ended Jasper. Chromium's sandboxed GPU and network helpers could not open the Chromium framework
    under the JBR ("file system sandbox blocked open()"), and Chromium stopped on `FATAL` "GPU process isn't usable".
  - A relaunch ended the same way, because the restored browser tab started Chromium again.
- **The fix:** the user chose "Turn the sandbox off now". `ChromiumOptions` sets `no_sandbox = true`
  (`bc6af7635`).
- **Second attempt: passed.** The user reported "it seems to be working" after going through the checklist (pages,
  keys, terminal link clicks and the "Open in Jasper?" prompt, OSC 8 targets, link gestures and popups, the opener
  bridge, page dialogs, closing, restore and local web files). The user did not report the ten lines one by one.

### Batch 2: memory (not run)

### Batch 3: the AOT cache (not run)

### Windows and Linux (not run)

The user has no Windows or Linux host yet.
