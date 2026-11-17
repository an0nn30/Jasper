# Optional Linux Integrations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Build and run Jasper with available Linux desktop integrations without mandatory GTK or Qt SDK installation.

**Architecture:** Detect capabilities per integration and distinguish missing dependencies from build errors. Propagate selected helper names through staging, tests and packaging. Retain strict CI/release validation separately from host builds.

**Tech Stack:** Gradle/Groovy, CMake, Python, Java/JUnit, Linux ARM64.

**Spec:** ../specs/2026-11-01-optional-linux-integrations-design.md

**Task 1 status:** Implemented; spec and quality reviews passed. Eight policy regressions, five selected-package regressions, available native suites, 84 focused JVM tests, and default/off/required/strict/stale Linux transitions pass. Baseline CI keeps its split from equipped runtime coverage: explicit required integrations plus seven-helper release-floor validation; global strict mode remains for fully equipped hosts. Full build/check and launch are Task 2.

## Global Constraints

- Default automatic detection; explicit disable and required settings; missing optional dependencies warn.
- Actual compiler errors fail. Normal builds never download toolkit SDKs.
- Selected integrations control staging/tests/packages; stale helpers never ship.
- Strict CI requires coverage and release ABI floors; local builds use the host ABI.
- Preserve core JBR/X11/GLib requirements and non-Linux behavior.
- Feature branch only; no push or merge.

## Review Focus

- SDK removal/disable after a successful build must remove prior helpers.
- Missing GTK headers must not block default build/check/run.
- Invalid options/prefixes fail clearly rather than degrade silently.
- Real compiler failures cannot be interpreted as missing SDKs.
- Package verification rejects unexpected/mismatched artifacts.

### Task 1: Optional integration build contract

**Files:** gradle/linux-desktop-colors.gradle; packaging/desktop-colors/CMakeLists.txt; packaging/desktop-colors/test/verify-qt-package.py; gradle/packaging.gradle; affected Java tests; Linux CI workflows; README.md; packaging/desktop-colors/README.md; docs/STATUS.md. Add focused policy tests alongside existing desktop-colors tests.

**Interfaces:** `jasper.desktop.<gtk3|gtk4|qt5|qt6|kde5|kde6>=auto|off|required` defaults auto. `jasper.desktop.strict=true` requires all integrations and their test prerequisites plus release verification. Portal remains required. Share selected helper names with verification through explicit expected names or a manifest outside the executable-only staged directory.

- [x] Capture failing missing-Qt5 baseline and write failing policy regressions for missing dependencies, explicit off, strict absence, invalid options, compile errors and stale outputs.
- [x] Implement per-helper discovery without swallowing build errors. GTK production probes need GIO/dlopen; missing GTK SDKs skip native ABI/tests; explicit off omits probes. Warn clearly about what is skipped.
- [x] Propagate selection through Sync staging, package/ELF verification and Java tests; retain strict full integration CI gates. Apply release ABI floors only to release validation.
- [x] Verify policy regressions, actual Linux Gradle selection/stale transitions, and focused JVM helper/resource tests. Verify all integrations explicitly off still builds.
- [x] Update concise documentation/status; self-review and commit with Co-Authored-By trailer.

### Task 2: Gentoo build and launch

**Approved scope extension — fixture portability:** The initial full Gentoo build exposed exact 1x glyph-coverage assumptions, fixed catalog heights that ignored documented font growth, and a distro prompt callback under nounset. Test-only corrections preserve exact ink, compact sizing, alignment and document-state assertions while isolating the shell fixture. Local affected scope passes 37 tests (one optional-fish skip); five mutation checks reject wrong ink, missing text, shell errors and inflated field/segment sizes. Targeted Gentoo affected/source-scan scope passes 46 tests with seven optional-shell skips. Full-build retry and review remain pending.

**Further Task 2 extension — UI fixture portability:** The second full build passed the application scope and exposed five UI tests with the same fixed-height assumption. A dedicated test helper now enforces catalog floor, padded actual font line and minimality; normal navigation rows also retain their inherited BODY/LABEL allocation. All fixed positions, adjacency, rules and hairlines stay exact. Gentoo passes 47 focused tests and the full UI suite (971 tests, two skips), with five mutations rejected. No production change; final whole-build confirmation remains pending.

**Files:** Record evidence in this plan/status; production changes only if verification finds defects.

**Interfaces:** Consume reviewed Task 1 branch; pinned JBR25 JCEF and Python3.13 in guest.

- [ ] Transfer reviewed branch without overwriting user changes.
- [ ] Run Gradle build/check as dustin and resolve regressions, recording optional skips.
- [ ] Launch `:jasper-app:run` in user's XFCE X11 session (explicitly authorized), verify logs/process/window and leave it running.
- [ ] Report branch, commands and results; defer ebuild until user visual acceptance.
