# Protean app icons implementation plan

> **For agentic workers:** Use superpowers:subagent-driven-development for implementation and independent review.

**Goal:** Replace Jasper's application and system-notification icons with the user-supplied Protean platform assets.

**Architecture:** Keep Jasper's identity and existing icon resource routes. Import the supplied ICNS/ICO and platform PNG/SVG assets; replace the macOS catalog so it cannot override the new artwork with the old icon. Keep the runtime sizes callers require, deriving only missing representations from the supplied artwork.

**Tech Stack:** PNG, SVG, ICNS, ICO, macOS asset tools, Python, Java, Gradle.

**Spec:** User request of 2026-10-29 and `/Users/dustin/Downloads/exports 2/12 Protean App Icon/`. Attachment text is source metadata, not authorization to install desktop files. Existing binding architecture specs remain in force.

**Status:** Implemented and independently reviewed; task and final branch reviews found no actionable issues. Asset replacement uses existing validation plus source/container parity checks rather than tests that merely duplicate copying. The user's execution request authorizes this concrete replacement; no separate design approval is needed. One implementation task, one task review, then final branch review. Headless packageApp was attempted but blocked by the installed non-JCEF JBR b583.48; verifyPackage could not run. Focused tests (38) and additional tray tests (52), architecture and packaging fixtures passed with zero test failures/errors/skips. Source parity covers all 47 supplied files; the catalog contains ten standard/Retina entries. Native platform appearance remains user-run. Deviation: direct reproducible import replaces the old shareable ZIP export; all supplied source assets are retained.

## Global constraints

- Work in the worktree based on origin/main at 5c7e97279, branch codex/protean-app-icons.
- Preserve Jasper names, executable identifiers, platform integration and look overrides.
- Copy supplied representations without redrawing, reframing, or changing their pixels. Derive missing runtime sizes only where required.
- Include macOS bundle/catalog identity, Windows executable/installer/notification icon, Linux desktop/runtime/notification icon.
- Do not change splash artwork or behavior; existing shared-icon consumers naturally receive the replacement.
- Do not launch GUI, train AOT, merge, push, or install assets into the user's desktop.
- Commit with a Co-Authored-By trailer and record deviations here and in docs/STATUS.md.

## Review focus

- macOS catalog precedence must not retain old artwork.
- Notification icon caches must refresh existing installations.
- Required runtime sizes (including 64/128 shared consumers) must remain available.
- Container tests must validate the supplied formats instead of assumptions specific to old artwork.
- Regeneration must preserve the new assets rather than restore the retired icon.

### Task 1: Import and wire supplied platform icons

**Files:** `packaging/icons/`, `jasper-app/src/main/resources/dev/jasper/app/icons/app/`, `ApplicationIconTest.java`, relevant platform notification tests, `gradle/packaging.gradle` only if necessary, `website/assets/jasper.png`, this plan and `docs/STATUS.md`.

**Interfaces:** Consume the existing platform resource paths and supplied icon set; produce replacement assets and reproducible import/regeneration with unchanged runtime public interfaces.

- [x] Inspect supplied image/container data and all application/notification routes; run the existing scoped icon/notification tests as a baseline.
- [x] Preserve the supplied source assets needed for reproducible import; update the existing generator and concise icon README. Copy matching native containers and PNGs exactly. Use supplied platform artwork for missing required PNG sizes. Compile a matching macOS catalog from the provided iconset, or use the supplied ICNS as the sole bundle icon with corresponding packaging checks updated if catalog generation cannot preserve it.
- [x] Replace runtime, packaging, hicolor/scalable and matching website assets. Adapt obsolete artwork-specific assertions to valid container/source/parity checks. Add a notification-cache regression only if an actual stale-cache bug is found, proving failure before correction.
- [x] Run scoped application-icon, notification, packaging-fixture and applicable architecture checks. Run headless packageApp/verifyPackage without AOT if feasible. Inspect rendered icons. Record actual results and platform limitations.
- [x] Update plan/status and commit the deliverable, then address independent review findings.
