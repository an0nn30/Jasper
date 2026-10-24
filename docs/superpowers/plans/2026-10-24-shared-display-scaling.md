# Shared display scaling implementation plan

> **For agentic workers:** Use superpowers:subagent-driven-development, with test-first implementation and independent review.

**Status:** Implementation, focused/fractional checks and full check complete (12,847 tests; zero failures/errors; 29 skips). Independent task and whole-branch reviews passed without findings. Native GNOME Wayland and KDE acceptance remain pending. User approved proceeding directly on 2026-10-24. Working in the existing clean checkout on `codex/shared-display-scaling`; no extra worktree. The investigation determines the precise control fixes before implementation, rather than guessing them in advance; this is a deviation from the complete-code planning convention.

**Goal:** Make Enterprise controls and terminal content follow the shared user scale on Linux, preserving logical terminal zoom and saved settings.

**Architecture:** Keep FlatLaf responsible for desktop-derived user scaling (including its GNOME/KDE platform handling), and Java2D responsible for device transforms. The independent terminal library receives an explicit host scale, defaulting to one; the app supplies the shared scale. Do not multiply device transforms into font sizes.

**Tech Stack:** Java 25 JBR, Swing, FlatLaf 3.7, Gradle wrapper, JUnit.

**Spec:** User request of 2026-10-24: “Just proceed with fixing the shared scaling path. Make sure it works for both GNOME and KDE and preserves terminal zoom.” Existing terminal and app/Buddy architecture amendments remain binding.

## Global constraints

- Keep jasper-terminal independent of jasper-ui and FlatLaf. No new dependency or SDK change unless evidence requires it.
- Logical terminal font sizes remain 6–72; scaling must not clamp a rendered size to 72.
- Preserve per-pane zoom, reset, option reload, palette changes and session resize behavior.
- Do not launch GUI, benchmarks or AOT training. Native GNOME/KDE verification must be explicitly recorded as pending.
- Use a feature branch, no merge or push; commits end in a Co-Authored-By trailer.

## Review focus

- Fractional scales 1.25 and 1.5, plus 1 and 2; no double application at device scale 2.
- Zoom steps and reset operate on logical font sizes, including the upper size bound.
- Live scale changes preserve zoom and resize the PTY; disposed panes release listeners.
- Enterprise's registered fonts must not replace the platform font used to derive scale.
- Native Swing and custom controls, icons, hit targets and initial window sizing must agree.

### Task 1: Repair and test the shared scaling path

**Files:** TerminalView and its view tests; TerminalPane, InitialWindowSize and relevant app workspace tests; confirmed shared UI/control defects and their tests. Update existing guides only where contracts change.

**Interfaces:** Add `TerminalView.setUserScaleFactor(float)` with a default factor of one, finite positive validation, and no change to `options().fontSize()` or `fontSize()`. The app binds it to `UIScale.getUserScaleFactor()` and removes its listener when the pane closes. All FontSet rebuilds use logical size times user scale. Initial terminal geometry uses the same effective size. Refine this interface only with recorded evidence.

- [x] Trace FlatLaf scale initialization and Enterprise font installation, and reproduce each suspected control defect before changing it.
- [x] Write and run failing behavior tests: at scale 1.5 logical 14 renders as 21; zoom to 15 renders as 22.5; changing to scale 2 keeps logical 15 and renders as 30; logical 72 at scale 2 renders as 144. Assert real cell metrics, minimum size and PTY resizing. Cover validation and default scale.
- [x] Implement separate logical size and host scale in TerminalView, reusing its typography invalidation/resize path.
- [x] Bind the app's terminal scale at creation and on user-scale changes with lifecycle cleanup; scale initial terminal sizing and padding consistently. Test config reload, zoom/reset and unchanged zoom on scale changes.
- [x] Fix confirmed shared control scaling defects with failing regression tests. Preserve the desktop's platform scale provenance across Enterprise font setup. Use cached FlatLaf sources to verify assumptions.
- [x] Run focused terminal/app/UI tests and fractional scale subprocess tests, then `./gradlew check` using Python 3.11–3.13. No external-plugin check unless SDK changes.
- [x] Commit implementation with coauthor trailer; independent task review and fix rounds.
- [x] Whole-branch review, update this status and docs/STATUS.md with results and native Linux acceptance remaining.
