# Theme-selected Splashes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Select the approved themed splash from saved interface preferences at next startup, at the existing logical size with sharp native text.

**Architecture:** Bootstrap resolves appearance and validated theme identity, then hands a string id to SplashPainter. A package-private ThemeSplash loads one allowlisted illustration, paints it and draws runtime text and progress; explicit branded looks retain their current path.

**Tech Stack:** JBR 25, Java2D, ImageIO PNG, JUnit/AssertJ, Gradle wrapper.

**Spec:** ../specs/2026-10-26-theme-splashes-design.md

**Status:** In progress. Art-size deviation: requested 2880 × 1760, generator returns roughly 1604 × 980; preserve native >2× sources, render 4× only as scaling, and use uniform cover/tiny crop for aspect rounding. User explicitly authorized implementation after reviewing the art; no redundant plan approval requested. Reusing the clean current checkout on `codex/theme-splashes`, rather than creating another worktree. Complete implementation bodies are delegated to the implementer instead of duplicated here; this deviates from the older plan-body convention and keeps decisions/test contracts here. Controller prepares art while implementer handles the single integrated code task.

## Global Constraints

720 × 440 logical pixels. Seven allowlisted bundled ids. Lossless source artwork is at least 1440 × 880 native pixels with aspect tolerance 0.002; generated outputs are roughly 1604 × 980. Verify actual output. Runtime text, version and progress never baked into assets. Keep native branded behavior, shutdown and launch exclusions. No GUI, AOT, SDK changes, new dependencies, merge or push. Still themed poses; existing animated Meadow remains failure fallback.

## Review Focus

- Invalid, missing, dormant and wrong-polarity selections follow theme validation.
- SYSTEM/light/dark policy and native observer cleanup; no extra desktop helpers.
- Resource failure preserves usable splash/startup; ids never become arbitrary resource paths.
- Fractional scaling and partial-buffer updates exactly match fresh frames, including text shrinking and backward progress.
- Seven resources have correct dimensions, no baked lettering, and scene/character remain legible at 1×/2×/4×.

### Task 1: Integrate theme selection, illustrated painter and regression coverage

**Files:** `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java`, new bootstrap selection helper if useful; `jasper-app/src/main/java/dev/jasper/app/splash/{SplashPainter,ThemeSplash,SplashBuffer,SplashColors}.java`; package-info and focused tests in those packages. Minimal relevant guide corrections in docs/looks.md, docs/configuration.md and docs/app-architecture.md.

**Interfaces:** Consumes PNG resources `dev/jasper/app/splash/themes/<id>.png`, supplied by controller. Produces `SplashPainter.themed(String themeId, String version, int scale)`; bootstrap selects validated active interface id. Keep `SplashPainter.meadow` and branded `of` compatibility.

- [ ] Write regression tests first: theme selection honors DARK/LIGHT/SYSTEM (DARK fallback on unavailable); known seven ids select distinct art; custom/Desktop ids use Meadow; invalid/wrong-polarity ids use validated fallback; next startup after preference save uses changed theme; branded path and excluded starts unchanged.
- [ ] Run focused tests and capture RED. Resource tests may wait for controller art.
- [ ] Implement selection and painter. Decode once, cache scale rasters if needed, contain resource errors with original Meadow fallback. Use allowlisted id mapping. Draw landscape within top 354 logical pixels, footer at 354, status at 399, progress at 434..438, border 0..719/439. Draw title at x262 baseline151 (58 bold), tagline baseline191 (18), address baseline219 (22 mono), build version baseline257 (11 mono). Character placement comes from art. For each theme choose readable text/footer colors within SplashColors (literal scanner exemption), reflecting approved preview palette. Use fresh graphics and existing fit/displayable helpers. Theme-specific first-fill background matches illustration.
- [ ] Add real-render regressions at 1×, 1.25×, 2× and 4×, long/short status, changed version, progress 0/1/out-of-range/NaN, and buffer-vs-fresh pixel equality for forward/backward steps. Verify raster quality dimensions. Save headless preview gallery for inspection.
- [ ] Run focused splash/bootstrap tests and architecture checks. Update the smallest guide text describing the behavior, including still illustration and next-launch theme choice. Commit task with Co-Authored-By trailer.
- [ ] Independent task review, resolve findings, then final whole-branch review.

### Task 2: Finish art and validate the complete deliverable

**Files:** seven `jasper-app/src/main/resources/dev/jasper/app/splash/themes/*.png`, artifact provenance/prompt record beside assets, plan/status records.

**Interfaces:** Supplies Task 1's resource ids. Only the art assets are edited by the controller while Task 1 runs.

- [ ] Use image generation to remove all lettering and footer controls from each approved revised preview, retaining landscape, mascot, position and colors. Request 2880 × 1760 high-resolution outputs. Inspect actual outputs; normalize only by user-authorized size/quality preparation if necessary, recording dimensions honestly.
- [ ] Preserve original previews and save production PNG assets in repo, with source names and prompts/provenance. Inspect at display size.
- [ ] Run full `./gradlew check` (Python 3.11–3.13). Fix change-related failures with regression evidence; report any unrelated failures precisely.
- [ ] Inspect generated headless renders and record exact test counts from XML. Record deviations/results in this plan and docs/STATUS.md. Commit with Co-Authored-By trailer. Leave branch unmerged/unpushed.
