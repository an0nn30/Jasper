# Theme-selected Splashes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Select the approved themed splash from saved interface preferences at next startup, at the existing logical size with sharp native text.

**Architecture:** Bootstrap resolves appearance and validated theme identity, then hands a string id to SplashPainter. A package-private ThemeSplash loads one allowlisted illustration, paints it and draws runtime text and progress; explicit branded looks retain their current path.

**Tech Stack:** JBR 25, Java2D, ImageIO PNG, JUnit/AssertJ, Gradle wrapper.

**Spec:** ../specs/2026-10-26-theme-splashes-design.md

**Status:** Complete on `codex/theme-splashes`, unmerged/unpushed. User authorized implementation after approving the art; reused the clean checkout on a feature branch. Implementation bodies were delegated rather than duplicated in the plan. Art resolution deviation: generation requested 2880 × 1760 but returned 1604 × 980/981; preserved native sources exceeding 2× and used uniform cover/tiny crop for rounding. Shared appearance policy moved to `appearance`; the migration inventory records the moved/new owners. Splash validation uses the bundled/plugin library without rescanning imports (both imports and unavailable ids use Meadow art). Illustrated steps redraw a whole buffered frame to preserve fractional sampling. A macOS review fix initializes the toolkit after desktop properties/exclusions and before the SYSTEM snapshot. Task and whole-branch reviews approved; final full headless check passed in 16m 55s at `822d29853`: 13,450 JUnit tests, zero failures/errors, 30 skips. No GUI, AOT, merge or push.

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

- [x] Write regression tests first: theme selection honors DARK/LIGHT/SYSTEM (DARK fallback on unavailable); known seven ids select distinct art; custom/Desktop ids use Meadow; invalid/wrong-polarity ids use validated fallback; next startup after preference save uses changed theme; branded path and excluded starts unchanged.
- [x] Run focused tests and capture RED. Resource tests may wait for controller art.
- [x] Implement selection and painter. Decode once, cache scale rasters if needed, contain resource errors with original Meadow fallback. Use allowlisted id mapping. Draw landscape within top 354 logical pixels, footer at 354, status at 399, progress at 434..438, border 0..719/439. Draw title at x262 baseline151 (58 bold), tagline baseline191 (18), address baseline219 (22 mono), build version baseline257 (11 mono). Character placement comes from art. For each theme choose readable text/footer colors within SplashColors (literal scanner exemption), reflecting approved preview palette. Use fresh graphics and existing fit/displayable helpers. Theme-specific first-fill background matches illustration.
- [x] Add real-render regressions at 1×, 1.25×, 2× and 4×, long/short status, changed version, progress 0/1/out-of-range/NaN, and buffer-vs-fresh pixel equality for forward/backward steps. Verify raster quality dimensions. Save headless preview gallery for inspection.
- [x] Run focused splash/bootstrap tests and architecture checks. Update the smallest guide text describing the behavior, including still illustration and next-launch theme choice. Commit task with Co-Authored-By trailer.
- [x] Independent task review, resolve findings, then final whole-branch review.

### Task 2: Finish art and validate the complete deliverable

**Files:** seven `jasper-app/src/main/resources/dev/jasper/app/splash/themes/*.png`, artifact provenance/prompt record beside assets, plan/status records.

**Interfaces:** Supplies Task 1's resource ids. Only the art assets are edited by the controller while Task 1 runs.

- [x] Use image generation to remove all lettering and footer controls from each approved revised preview, retaining landscape, mascot, position and colors. Request 2880 × 1760 high-resolution outputs. Inspect actual outputs; normalize only by user-authorized size/quality preparation if necessary, recording dimensions honestly.
- [x] Preserve original previews and save production PNG assets in repo, with source names and prompts/provenance. Inspect at display size.
- [x] Run full `./gradlew check` (Python 3.11–3.13). Fix change-related failures with regression evidence; report any unrelated failures precisely.
- [x] Inspect generated headless renders and record exact test counts from XML. Record deviations/results in this plan and docs/STATUS.md. Commit with Co-Authored-By trailer. Leave branch unmerged/unpushed.


## Verification and decisions

- Baseline focused splash/bootstrap tests: 60 tests, zero failures/errors/skips.
- Selection RED reproduced wrong active-theme and saved-next-start selection; GREEN coverage expanded to 124 focused tests and application architecture. Real-art Yaru contrast RED reproduced 1.0439:1; the corrected light palette passes the contrast regression.
- First full check failed in 9m 56s with only two migration-inventory failures among 4,860 app tests (20 skips, log count); `9596da949` updates those entries and the moved historical authority path.
- Review P1 identified native appearance observation before AWT/NSApp initialization. Cold-ordering RED reproduced missing initialization; `822d29853` fixes the ordering. Scoped GREEN: 39 tests, zero failures/errors/skips plus architecture and migration checks. Scoped re-review approved without new findings.
- Final command: `./gradlew check --continue -Pjasper.python=/Users/dustin/.local/share/uv/python/cpython-3.13.15-macos-aarch64-none/bin/python3.13`. Passed, 159 actionable tasks (59 executed, 100 up-to-date). XML totals: 13,450 tests, 13,420 passed, 30 skipped, zero failures/errors; app alone 4,863 tests with 20 skips. Existing compiler/ByteBuddy deprecation warnings remain.
- All seven native PNG dimensions and SHA-256 hashes checked against `jasper-app/src/main/resources/dev/jasper/app/splash/themes/provenance.json`; exact generation prompts are beside it in `prompts.json`. Built-in image_gen produced the assets; no offline upsampling or image alteration. The renderer's 720 × 440 and 1440 × 880 outputs preserve the existing normal/Retina sizes. 4× inspection is scaled native artwork, with native text rendered at that scale.
- Inspected all seven real-headless previews, including Retina and representative 4× renders, under `jasper-app/build/reports/splash/themes/`. Final independent whole-branch review approved without findings.
- Native GUI acceptance remains user-run: save a different interface theme, fully quit Jasper, and launch this build to inspect its startup splash. System preference defaults Dark if unavailable; Linux does not start extra helpers just to choose splash art.

Decisions carried forward from the execution ledger:

1. Proceed on the explicit implementation go-ahead after approved visuals, without repeating approval; a different intended behavior would need a follow-up adjustment.
2. Reuse the clean checkout on a feature branch, keeping main uncommitted; the branch remains available for integration.
3. Use still themed poses and live loading text/progress; animation of the new costumes would require authored frames. Original animated Meadow remains the asset-error fallback.
4. Use one reviewed illustration per theme, including themes with both appearances; separate light/dark scenes would require additional art.
5. Preserve actual native 1604 × 980/981 PNGs instead of claiming upsampled 4× detail; 4× illustration detail is scaled, and tiny uniform cropping absorbs aspect rounding.
6. Avoid rescanning imported theme files merely to select the same Meadow fallback; normal application theme validation remains authoritative.
7. Redraw the full illustrated buffer per startup step for exact fractional-scale results; this costs more pixels per step, without an idle animation thread.
8. Initialize the toolkit after desktop properties/exclusions, before a SYSTEM native snapshot; the native macOS observer requires NSApp. Tests verify ordering without opening a window.
