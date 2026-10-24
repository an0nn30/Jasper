# Buddy translucent Linux rendering implementation plan

> **For agentic workers:** Use superpowers:subagent-driven-development, with one implementer, task review and whole-branch review.

**Status:** Implemented; eight focused pixel tests and all 162 Buddy tests pass. Full `check` passed: 12,850 tests, zero failures/errors, 29 skipped (Python 3.12.14). Task and whole-branch reviews approved without findings. Scope expanded by one explanatory sentence in `docs/buddy-architecture.md`; the controller runs full `check` once, alongside report preparation. Native Ubuntu GNOME Wayland acceptance remains user-run.

**Goal:** Preserve Buddy's transparent surfaces and present each animation frame in one blit on Linux.

**Architecture:** All four Buddy windows use one internal JWindow subclass. It disables the root's Swing double buffer and composes Window.paint (including its background clear) into a reusable ARGB image before replacing the destination pixels in one Src blit. A package-private renderer holds the image and can be tested headlessly. Panel painting, timers, geometry and the public facade stay the same.

**Tech Stack:** JBR 25, JDK Swing/Java2D, JUnit/AssertJ, Gradle wrapper.

**Spec:** docs/superpowers/specs/2026-09-20-jasper-app-buddy-refactor-design.md, sections 6, 7 and 9; existing Buddy presentation contract.

## Evidence

User recording on Ubuntu GNOME Wayland shows opaque rectangles behind capsules and sprite flicker. Local JBR sources: SunToolkit.isSwingBackbufferTranslucencySupported returns false; XToolkit inherits it. RepaintManager therefore allocates an OPAQUE offscreen buffer. JComponent.paintImmediately climbs the nonopaque tree to Window.repaint; Window.paint clears the native destination before drawing Swing children. The sprite explicitly clears its pixels while capsule panels do not. Own ARGB composition fixes both mechanisms without global pipeline switches. Actual compositor acceptance remains unverified on this Mac.

## Global constraints

- JDK-only Buddy module; no new public API, external dependency or app import.
- Headless tests only; never launch GUI, benchmark or AOT training.
- Keep existing movement, animation timing, hide/dispose and appearance behavior.
- Work on codex/buddy-wayland-rendering; no merge or push.
- Commit ends with Co-Authored-By: Codex <noreply@openai.com>.

## Review focus

Transparent pixels must erase previous frames; clipping must preserve untouched destination pixels; device scaling must retain sharpness at 1x, 2x and fractional scales; resized buffers must not leak stale content; every Buddy window must use this path. Cover these in the tests below. Linux compositor acceptance is manual.

### Task 1: Compose Buddy windows into ARGB frames

**Files:** Create BuddyFrameBuffer.java and BuddySurfaceWindow.java under jasper-buddy/src/main/java/dev/jasper/buddy/internal/presentation; create BuddyFrameBufferTest.java in matching tests. Modify BuddyWindow.java, BuddyBubble.java, BuddyDeckWindow.java and BuddyColumnWindow.java to instantiate BuddySurfaceWindow. Record evidence in docs/STATUS.md and this plan; add one rendering sentence to docs/buddy-architecture.md.

**Interfaces:** package-private final BuddyFrameBuffer with void paint(Graphics graphics, int width, int height, Consumer<Graphics2D> painter) and void dispose(). Package-private final BuddySurfaceWindow extends JWindow, constructor disables getRootPane().setDoubleBuffered(false); paint(Graphics) delegates to buffer with super.paint as callback; dispose releases buffer and super.dispose. No callback retention.

- [x] Write pixel tests before production code. Use real BufferedImages/Graphics2D. First frame paints a translucent shape, second paints a moved shape: old pixels alpha must be zero and new pixels match fresh rendering; prefill destination opaque to prove Src replacement. Paint with a small destination clip and assert pixels outside remain identical. Resize between frames and render at 1x, 2x and 1.25x; compare output to direct fresh ARGB reference with matching device transform. Zero dimensions do nothing. Disposal followed by paint safely recreates storage. A package-private surface test may inspect wiring in source like existing architecture tests, but prefer real rendering assertions; do not launch windows.
- [x] Run ./gradlew :jasper-buddy:test --tests '*BuddyFrameBufferTest' and record expected failure.
- [x] Implement renderer with cached TYPE_INT_ARGB_PRE BufferedImage sized ceil(logical width * device scale X) by ceil(logical height * device scale Y). For each frame clear entire image using AlphaComposite.Clear, restore SrcOver, scale graphics to device pixels and clip to logical window bounds, invoke painter on that offscreen graphics, dispose it. On a copy of target graphics, retain caller clip, intersect logical bounds, use AlphaComposite.Src and draw image with inverse-scale AffineTransform (avoids rounding-induced resampling), then dispose. Cache by pixel dimensions; ensure current transform is applied each paint. Flush replaced/released images; no per-frame image allocation when dimensions unchanged. Normal window transforms are axis aligned positive scales. Handle zero dimensions before allocating.
- [x] Implement shared window as above. Replace all four `new JWindow()` with `new BuddySurfaceWindow()`. Leave panels and timers unchanged. Explain in a short code comment why Swing's opaque X11 buffer and native clear are avoided.
- [x] Run ./gradlew :jasper-buddy:test and ./gradlew check with Python 3.11–3.13 via -Pjasper.python if needed. Controller may run the full check while review runs; report all failures explicitly. Test source hygiene and git diff --check. Update plan status and short STATUS entry with verified results and pending Ubuntu acceptance (capsules, sprite, dragging, hide/show, menu/drawer and scaling).
- [x] Commit task with required trailer. Per-task review then final whole-branch review; fix findings and update evidence before final delivery.


## Implementation evidence

- Red: `./gradlew :jasper-buddy:test --tests '*BuddyFrameBufferTest'` first failed compilation because the new renderer did not exist; with a minimal no-op API, eight tests ran and seven failed on pixel assertions (opaque alpha, uncleared pixels and missing artwork). The zero-size no-op passed as expected.
- Green: the same focused command passed all eight tests after implementation. `./gradlew :jasper-buddy:test` then passed all 162 tests, zero failures/errors/skips (XML results).
- Tests compare actual premultiplied ARGB pixels against fresh direct rendering, including 1×, 2×, 1.25×, independent axis scales and translated destination bounds. They also exercise changed scale with unchanged pixel dimensions, target damage clipping, zero dimensions and dispose/repaint.
- Source hygiene scanned all 2,930 Java source files with zero findings; `git diff --check` passed.
- Full `./gradlew check -Pjasper.python=/Users/dustin/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3` passed in 16m 57s: 12,850 tests, zero failures/errors, 29 skipped. Existing compiler/JavaDoc and Byte Buddy deprecation warnings remain. Task and final whole-branch reviews approved without findings. Native Ubuntu acceptance is pending as listed above.
