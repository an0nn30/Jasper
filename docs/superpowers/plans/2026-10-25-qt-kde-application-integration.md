# Qt / KDE Desktop colors application integration plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Execute tasks in order, one implementation commit and independent review per task, then whole-branch review. Steps use checkbox syntax.

**Goal:** Enable Qt / KDE in Desktop colors with correct Automatic selection, healthy current-only palette observation, truthful fallback status and the existing UI transaction.
**Architecture:** Extend immutable source identities and the existing supervised protocol. A single pure source-order policy consumes captured desktop hints; the application owner uses it for both demand and resolution. Cache colors by actual source/polarity, but retain reply availability for the requested appearance separately so opposite-polarity previews finish without restart loops or misleading status.
**Tech Stack:** JBR25, JUnit/AssertJ, Gradle; the four Qt/KDE helpers from deliverable 1.
**Spec:** [Approved design](../specs/2026-10-25-qt-kde-desktop-colors-design.md).
**Prerequisite:** [Native helper plan](2026-10-25-qt-kde-native-helpers.md). Tasks 1–2 here can run headlessly once its protocol/native interfaces exist; Task 3 must not expose the UI until the native gate is recorded as passed.
**Status:** Approved for execution, 2026-10-25. Task 1 implemented and independently reviewed without findings. Its test-only Linux diagnostic-race follow-up passes local focused checks; scoped follow-up review approved without findings. Fedora VM access is restored; the native plan records passing deterministic aarch64 integration evidence, with the remaining release/desktop matrix still open. Task 2 implements shared desktop-aware ordering and captured reload context; the selector remains for Task 3. Its additional nonvisual policy-test migration row is the only file-scope adjustment. Local Desktop/Settings coverage passes 234 tests with zero failures/errors and one existing Linux-only skip; application architecture passes. Task 2 review approved without findings; full check remains scheduled for Task 4. Small plan decisions: Qt hint lists use exact semicolon-separated keys with optional colon parameters; requested versus observed polarity is tracked separately in the application owner; protocol remains v1 because message fields do not change. Task 1 also updates DesktopProcess's unsolicited-palette admission and adds expected-source context so actual-polarity Qt events reach the supervisor without relaxing GTK admission or source identity. The new protocol test has the required nonvisual migration-ledger entry. The existing restart test now awaits a nonblank failure reason because concurrent child-exit and pipe-failure callbacks can choose different diagnostics; restart/generation assertions are retained. Task 3 implements request-aware availability and the single Qt / KDE selector. Focused macOS coverage passes 76 tests; affected Desktop/configuration/Settings/presentation coverage passes 684 tests with zero failures/errors and one Linux-only skip, and application architecture passes. The separate Settings search-term list in settingsui/Editors.java is the only additional file-scope change. Supplied failure reasons remain intact at the 1024-character bound. Task 3 independent review approved without findings; updated-source Linux execution and real-helper sampling are recorded in the Linux follow-up below. Full check and remaining release/desktop cases stay in Task 4. Record later deviations in this banner and docs/STATUS.md. Task 4 local fixtures cover 42 inputs in both kits, preserving original GTK bytes and authored captures. Its test-only bridge/context and finally-based RSyntax timer cleanup are scoped deviations. Focused tests and architecture pass; the full Mac check passes 13,111 JUnit tests with zero failures/errors and 30 skips, plus 72 theme-tool tests. The final changed matrix/conflict methods and headless install/package verification pass. Linux execution and full-candidate resource measurements are recorded below; Windows/CI and user desktop acceptance remain open, and release checkboxes stay pending. The combined application final-review fix uses the newest current-only observation across requested-polarity slots for preview colours/status and adds bounded context for observed higher-ranked fallback failures. Regression RED reproduced four failures among 48 tests; focused GREEN passes all 48, and affected Desktop/Settings coverage passes 439 tests with zero failures/errors and one Linux-only skip; application architecture passes. Completion bookkeeping and sampling are unchanged. Scoped re-review approved both findings with no new breakage; existing full-check, Linux/native and desktop acceptance limits remain as recorded.


**Linux follow-up, 2026-10-26:** Source `3bfde469f` was copied with explicit user authorization. The full Linux check executed 13,114 tests with 34 failures: 24 baseline reproductions, nine corrected-environment passes, and one unresolved full-order Rehearsal callback. New Qt/KDE cases, current-Java/real-helper active/opposite previews and recovery, seven-helper dual-preview resource checks, native compatibility and minimal/equipped installer checks pass. Full-check and desktop/release checkboxes remain open; the launcher-cache baseline and test-harness limitations are recorded in [the compatibility record](../../design/qt-kde-native-compatibility.md#linux-application-verification--2026-10-26). No production changes were needed for this verification.

## Global Constraints

- Qt5 5.15, generic Qt6 6.2; KF5 5.92; KDE6 KF6 6.0 with Qt6 6.5; preserve Ubuntu22 glibc baseline on x86_64/aarch64.
- One Qt / KDE selector with saved value qt; existing auto/gtk3/gtk4 semantics and Automatic default remain. Source identities are GTK3, GTK4, QT5, QT6, KDE5, KDE6.
- KDE major preference follows KDE_SESSION_VERSION, fallback major6 when unknown; KDE source candidates only on KDE. LXQt prefers Qt; GNOME prefers GTK4; unknown prefers GTK3 unless an explicit qt5ct/qt6ct hint applies.
- One source snapshot per palette. Preserve compatible last-good priority; recovered preferred source preempts fallback. Explicit Qt never borrows GTK; explicit GTK remains single-toolkit.
- Actual palette polarity is not the requested polarity. Healthy incompatible/missing Qt pairs keep helpers alive; process failures keep existing bounded restart behavior.
- Portal remains appearance authority; forced appearance wins, absent preference retains Dark fallback. No Qt inversion or theme forcing.
- Keep seven normalized pairs and existing contrast targets: 4.5:1 normal text, 7:1 high-contrast text, 3:1 necessary boundaries. No new SDK roles.
- Native sampling/decoding/resolution off EDT; one existing transactional theme install on EDT. Generation, cancellation and rollback guarantees remain.
- Terminal scheme and independent Editor choice remain unchanged. Linux defaults, optional look precedence and authored-theme output remain unchanged.
- No GUI/benchmark/AOT training without authorization; use JBR25/Gradle wrapper; no automatic merge/push. Every commit ends with Co-Authored-By: Codex <noreply@openai.com>.

## Review Focus

1. Dark preview receiving Light data must finish sampling and preserve the same live helper, without starving the active theme (Tasks 1, 3).
2. A compatible cached Dark palette plus a current Light observation must say last-good/mismatch instead of pretending current native Dark support (Task 3).
3. Mixed desktop hints and Qt5/Qt6 coexistence must select one reproducible order shared by demand and resolution (Task 2).
4. Startup/reload/disposal races must not publish old generations or change the source of another preview (Tasks 1, 3).
5. Package-dependent initialization failure must fall back only within the selected family and recover without rewriting saved settings (Tasks 3, 4).

## File map

- `jasper-app/src/main/java/dev/jasper/app/platform/DesktopObservation.java`, `DesktopProtocol.java`, `DesktopColors.java`: source identity, decode, supervisor.
- New `jasper-app/src/main/java/dev/jasper/app/platform/DesktopEnvironment.java`: immutable JDK-only desktop hint capture.
- `jasper-app/src/main/java/dev/jasper/app/appearance/DesktopPalette.java`: pure ordering, source-aware labels and existing palette generation.
- `jasper-app/src/main/java/dev/jasper/app/application/DesktopAppearance.java`, `JasperApplication.java`: captured environment, actual-polarity cache, request availability, preview scheduling and reload.
- `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java`; `jasper-app/src/main/java/dev/jasper/app/settingsui/AppearanceSelection.java`, `DesktopChoices.java`: saved Qt family and UI.
- Matching owner-package tests plus existing paint fixtures; docs/themes.md, docs/configuration.md, docs/app-maintenance.md, config.example.toml and native README.

### Task 1: Decode new sources and keep healthy Qt observers alive

**Files:** DesktopObservation.java, DesktopProtocol.java, DesktopColors.java; existing platform/DesktopProtocolTest.java, DesktopColorsTest.java, DesktopColorsHarness.java, DesktopHelperDirectoryTest.java; new platform/DesktopQtProtocolTest.java.
**Interfaces:** Append QT5/QT6/KDE5/KDE6 to `DesktopObservation.Source`. Add concrete enum methods `String helperName()` and `boolean currentPaletteOnly()` (true only for Qt/KDE). `DesktopColors.helperNames()` returns portal then each Source helperName, preserving the old first three names. Wire source values map exactly to native plan's qt-vectors.jsonl. Keep Palette record and v1 envelope unchanged.

- [x] **Add protocol and real-supervisor tests first.** Decode each new wire source, including opposite and UNKNOWN polarity, alpha, missing pairs, large integer generation and metadata; reject unknown source and a source mismatched to its launched slot. Reuse native qt-vectors.jsonl as Java fixtures. Extend the pipe harness only with operations needed to inject stale generations/malformed output and inspect liveness. The healthy mismatch test uses existing harness methods:

```java
@Test void currentOnlyQtReplyKeepsObserverAlive() {
    try (var harness = new DesktopColorsHarness()) {
        harness.service.request(1, Set.of(Source.QT5), true);
        harness.palette(Source.QT5, false, "1,1,1,1", "");
        assertThat(harness.service.current().palettes().get(Source.QT5).appearance())
            .isEqualTo(Polarity.LIGHT);
        assertThat(harness.toolkitAlive(Source.QT5)).isTrue();
        long launches = harness.launches().stream().filter("jasper-desktop-qt5"::equals).count();
        harness.advance(30);
        harness.service.request(2, Set.of(Source.QT5), false);
        harness.palette(Source.QT5, false, "1,1,1,1", "");
        assertThat(harness.launches().stream().filter("jasper-desktop-qt5"::equals).count())
            .isEqualTo(launches);
    }
}
```

Parameterize for all four new sources. Also assert missing-pair replies keep them alive, a subsequent valid reply recovers, GTK mismatch retains its existing failure behavior, wrong-source messages are failures, and close stops every process. Respond to periodic samples before advancing additional deadline windows; the test must not accidentally substitute a timeout for healthy unavailability.
- [x] **Observe RED.** `./gradlew :jasper-app:test --tests '*DesktopProtocolTest' --tests '*DesktopQtProtocolTest' --tests '*DesktopColorsTest' --console=plain` fails on new source decoding/liveness assertions before implementation.
- [x] **Implement explicit source dispatch and healthy observation handling.** Replace helper array-index/GTK ternaries with `slot.source.helperName()`. Reject source mismatch and stale generation before accepting any reply. For currentPaletteOnly sources, a valid v1 reply is an observation even with opposite/UNKNOWN polarity or no Window pair; publish it with original polarity and reason, satisfy response timing, reset failure attempts, and keep the process. For GTK retain current admission behavior. On a genuine Qt process failure, preserve the prior snapshot's actual polarity and colors with failure metadata; never relabel Light colors as Dark. Do not change portal handling or shared process framing.
- [x] **Verify GREEN and bounds.** Run the focused command plus `--tests '*DesktopProcessTest' --tests '*DesktopPortalTest' --tests '*DesktopHelperDirectoryTest'` and `verifyApplicationArchitecture`. Simulate timeout, malformed JSON, source mismatch, late reply, close and restart: each follows existing backoff/deadline rules. Confirm all helper names are covered by packaging assertions.
- [x] **Commit:** `feat: supervise Qt and KDE palette observations`, with required trailer.

### Task 2: Implement one shared desktop-aware source-order policy

**Files:** new platform/DesktopEnvironment.java and test; appearance/DesktopPalette.java and DesktopPaletteTest.java; new appearance/DesktopSourcePolicyTest.java; application/DesktopAppearance.java, JasperApplication.java and affected tests (constructor/context plumbing only).
**Interfaces:** Add the immutable record below; `capture()` reads System.getenv once and calls `from`, with no process launches or OS queries. `DesktopPalette.SourceMode` adds QT. Replace source-order input with `preferredSources(SourceMode mode, DesktopEnvironment environment)` and `resolve(boolean dark, SourceMode mode, DesktopEnvironment environment, DesktopObservation input)`. All call sites, tests and preview demand use the same method. DesktopAppearance receives `Supplier<DesktopEnvironment>`, captures on construction and again on reload; tests supply a fixed or mutable supplier.

```java
public record DesktopEnvironment(String currentDesktop, String kdeSessionVersion,
                                 String kdeFullSession, String qtPlatformTheme) {
    public DesktopEnvironment {
        Objects.requireNonNull(currentDesktop);
        Objects.requireNonNull(kdeSessionVersion);
        Objects.requireNonNull(kdeFullSession);
        Objects.requireNonNull(qtPlatformTheme);
    }
    public static DesktopEnvironment from(Map<String, String> environment) {
        Objects.requireNonNull(environment);
        return new DesktopEnvironment(environment.getOrDefault("XDG_CURRENT_DESKTOP", ""),
            environment.getOrDefault("KDE_SESSION_VERSION", ""),
            environment.getOrDefault("KDE_FULL_SESSION", ""),
            environment.getOrDefault("QT_QPA_PLATFORMTHEME", ""));
    }
    public static DesktopEnvironment capture() { return from(System.getenv()); }
}
```

Use java.util.Map and java.util.Objects imports; no interface or platform-package dependency on app/theme types. Policy trims tokens, uses Locale.ROOT/exact case-insensitive matching, and treats only trimmed `5` as KDE5; every other version chooses KDE6-first. KDE_FULL_SESSION accepts trimmed case-insensitive true only, after no known desktop token. First recognized KDE/GNOME/LXQt token wins.

- [x] **Write a table-driven failing test for every approved order.** Test exact lists, not just first source:

```java
assertThat(DesktopPalette.preferredSources(SourceMode.AUTO,
    DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "KDE", "KDE_SESSION_VERSION", "5"))))
    .containsExactly(Source.KDE5, Source.QT5, Source.KDE6, Source.QT6, Source.GTK3, Source.GTK4);
assertThat(DesktopPalette.preferredSources(SourceMode.QT,
    DesktopEnvironment.from(Map.of("XDG_CURRENT_DESKTOP", "GNOME"))))
    .containsExactly(Source.QT6, Source.QT5);
```

Other rows: KDE6/unknown -> KDE6,QT6,KDE5,QT5,GTK3,GTK4; LXQt -> QT6,QT5,GTK3,GTK4; GNOME -> GTK4,GTK3,QT6,QT5; unknown -> GTK3,GTK4,QT6,QT5. Explicit GTK modes are singleton lists. Explicit QT filters the same context's order to Qt/KDE only.

Add `" vendor : gNoMe : KDE "` (GNOME wins), `"KDE:GNOME"` (KDE wins), unknown plus KDE_FULL_SESSION=true, malformed KDE versions, and irrelevant installed-library information. Parse QT_QPA_PLATFORMTHEME as semicolon-separated entries, using the first exact qt5ct or qt6ct key before an optional colon parameter. Unknown substrings such as notqt5ct do not match. On LXQt this chooses Qt major; on unknown it also moves Qt ahead of GTK; known GNOME/KDE ignore it for ordering. Cover qt5ct:parameters, mixed case, blank input, both keys and leading unknown entries.
- [x] **Observe RED** using `./gradlew :jasper-app:test --tests '*DesktopEnvironmentTest' --tests '*DesktopSourcePolicyTest' --tests '*DesktopPaletteTest' --console=plain`.
- [x] **Implement the policy and context plumbing.** Use a single ordered immutable list; no separate UI/supervisor policy. Keep source identities rather than toolkit-family aliases in caches. Change capability labels from GTK ternaries to an exhaustive switch for all six sources, with actual implementation/library version and no claim that an environment plugin loaded. No new user-visible qt choice yet.
- [x] **Verify GREEN.** Test resolver rejection of incompatible preferred palettes, optional-role omission without switching, no cross-source pair blending, healthy preferred recovery, and explicit Qt refusing an otherwise-valid GTK palette. `DesktopAppearance` tests prove supplied environment changes only after reload and invalidates old worker tickets without overwriting saved source. Existing GTK explicit tests retain behavior; Automatic fixtures now answer the extra candidates deliberately rather than hanging on old two-source assumptions.
- [x] **Commit:** `feat: select desktop colors for KDE and Qt sessions`, with required trailer.

### Task 3: Integrate requested/observed polarity, previews and the Qt selector

**Gate:** Native plan Task 4 must have passing required compatibility evidence before this task starts. Do not ship a selector backed only by fake helpers.
**Files:** application/DesktopAppearance.java, JasperApplication.java; settingsui/AppearanceSelection.java, DesktopChoices.java; config/CoreSettings.java; application/DesktopAppearanceTest.java, DesktopColorsRetentionTest.java; settingsui/DesktopSourceSelectionTest.java, AppearancePageTest.java; config/CoreSettingsTest.java, ConfigTemplateTest.java, ConfigLoaderTest.java; docs/themes.md, docs/configuration.md, config.example.toml.
**Interfaces:** Keep color cache `Key(Source, Polarity)` keyed by actual observation. The capabilities map represents the outcome for a requested polarity: for a current generation, key it by `(source, demand.dark ? DARK : LIGHT)` while its Palette value retains actual observed polarity. A missing-pair/UNKNOWN reply counts as completion for that requested generation. Add no production-only test hooks or SDK types.

- [x] **Add failing end-to-end owner tests before changing UI.** Using DesktopColorsHarness and the existing EDT fixture, request a Dark active theme and Light preview; supply a single Light Qt palette for every request. Both requests finish sampling, process launch count remains one per demanded source, generation count stabilizes after the preview round, and saved Dark uses the bundled fallback. Next supply Dark through the same process: active Dark updates, Light preview retains only its compatible cached palette with correct status. Add a second preview with another source, cancellation, rapid source/appearance changes, no portal preference, explicit forced appearance, high contrast and reload. Late generations and closed previews cannot publish.

Assertions must include actual color samples and source/status, not merely callback counts:

```java
assertThat(result.source()).contains(Source.QT5); // only when a compatible QT5 palette exists
assertThat(result.theme().terminal()).isEqualTo(before.terminal());
assertThat(result.theme().editor()).isEqualTo(before.editor());
assertThat(result.theme().syntax()).isEqualTo(before.syntax());
assertThat(harness.toolkitAlive(Source.QT5)).isTrue();
assertThat(harness.launches().stream().filter("jasper-desktop-qt5"::equals).count()).isEqualTo(1);
```

Here before is the GeneratedTheme captured before a source/accent update at the same polarity. Also exercise the existing terminal/editor retention fixtures so equality of scheme values is backed by unchanged visible content; appearance changes retain the existing matching-scheme behavior.

Add UI/config assertions for exactly Automatic, Qt / KDE, GTK 3, GTK 4 / libadwaita; saved qt reload; invalid values -> auto diagnostic; Apply saves, Cancel discards; Reset uses auto and platform defaults; non-Linux keeps saved qt but acquires no helpers. Explicit source draft previews must not replace saved source demand.
- [x] **Observe RED.** `./gradlew :jasper-app:test --tests '*DesktopAppearanceTest' --tests '*DesktopColorsRetentionTest' --tests '*DesktopSourceSelectionTest' --tests '*AppearancePageTest' --tests '*CoreSettingsTest' --tests '*ConfigTemplateTest' --console=plain`.
- [x] **Implement request-aware availability.** In observed(), accept only the current generation, retain latest request capability with actual polarity, and admit valid actual pairs to the actual-polarity cache. Mark preview completion by generation/source receipt, not polarity success. In input(requestedDark), use only compatible cached colors. If latest requested result is incompatible or missing, retain its metadata and synthesize a truthful status reason such as `desktop palette is Light; Jasper requests Dark`. With compatible old cache, return cached colors with last-good status; without it, retain unavailable metadata and let the existing bundled fallback resolve. Never rewrite actual colors' polarity to pass usability. Preserve process-failure reasons and show source family/implementation version. Suppress equal results and preserve rollback/generation/close behavior.
- [x] **Activate source setting and UI.** Add Map.entry("qt", "qt") to the CoreSettings choice and the matching label in the selector. Extend the existing source-mode conversion; keep shared source, appearance and theme defaults unchanged. Pending Apply status and non-Linux retention follow existing GTK behavior. Update current guides and example source list minimally. Document current-only Qt appearance behavior and Reload recovery for integrations that do not emit changes.
- [ ] **Verify GREEN with native helpers and fake races.** Run the focused command, `--tests '*DesktopQtProtocolTest'`, and verifyApplicationArchitecture; run config/appearance/settings/presentation coverage on Mac and the authorized Linux VM. Validate a real helper sample through the Java decoder. Repeat Qt5/KDE5 and Qt6/KDE6 cases with active/preview polarities. No GUI launch is required for these tests.
- [x] **Commit:** `feat: expose Qt and KDE desktop colors in Settings`, with required trailer.

### Task 4: Verify actual paints, recovery and packaged application behavior

**Files:** existing appearance/DesktopPaintedContrastTest.java, DesktopColorsRenderTest.java, DesktopOwnerPaints.java and resources `jasper-app/src/test/resources/dev/jasper/app/appearance/desktop-palettes.jsonl`; application/DesktopColorsRetentionTest.java; plugins/DesktopEditorSchemeTest.java, platform/DesktopQtProtocolTest.java; native fixtures and CI workflows as needed; docs/app-maintenance.md, packaging/desktop-colors/README.md, docs/STATUS.md, compatibility report and plan banners.
**Interfaces:** Reuse the original actual-paint pair inventory and retention fixtures. Extend the palette fixture format with source names supported by Task 1; preserve existing authored captures and GTK expected values. Keep reported test evidence tied to commit, architecture, toolkit versions and exact commands.

- [ ] **Add failing coverage assertions for native palette fixtures.** Include Qt5, Qt6, KDE5 and KDE6 Light/Dark plus high-contrast cases; incomplete optional sets; translucent ink; a low-contrast shared-role conflict; and all-sources-unavailable. Assert the native-to-normalized fixture pairs first, then actual rendered paint thresholds. Existing authored-theme digest set stays identical. Test missing runtime libraries/plugin/display through real child processes, including family-limited explicit fallback and successful recovery.
- [x] **Observe RED** with `./gradlew :jasper-app:test --tests '*DesktopPaintedContrastTest' --tests '*DesktopColorsRenderTest' --tests '*DesktopColorsRetentionTest' --tests '*DesktopEditorSchemeTest' --tests '*DesktopQtProtocolTest' --console=plain`; failures must demonstrate absent fixture coverage or a behavioral defect, not just forced count changes.
- [ ] **Complete fixtures and diagnostics only where required.** Do not change contrast thresholds, existing paint expectations or authored colors to make Qt fixtures pass. Repair genuine mapping/integration defects at their owner and record plan deviations. Verify live menus/dialogs/plugin bindings share one atomic UI theme transaction; do not add a new Qt-specific paint path. Measure idle helper lifetime/resource use with Automatic's full candidate set and retained dual-polarity previews; report actual memory/CPU and process churn, not a fabricated pass threshold.
- [ ] **Run release verification.** Full `./gradlew check --continue --max-workers=2 -Pjasper.python=<verified Python 3.11–3.13> --console=plain` on Mac and Linux; targeted Windows checks/CI for enum/config retention. On Linux run `:jasper-app:checkDesktopQtNative :jasper-app:installDist :jasper-app:packageApp :jasper-app:verifyPackage` and package-install matrix from deliverable 1. Existing baseline Linux render failures must be independently confirmed against the refreshed base and recorded, never silently excluded. SDK surface is unchanged, so external SDK plugin rebuild is not triggered unless implementation actually changes it.
- [ ] **Prepare and execute authorized desktop acceptance separately.** Record Plasma5 and Plasma6, Wayland and X11, plus Qt5/Qt6-configured non-KDE sessions: first-run Automatic; each explicit source; live scheme/accent/high-contrast changes; source unavailable and recovery; no portal; forced opposite appearance; settings preview/Cancel; restart; custom style; terminal/editor retention; zero mapped helper windows, focus theft or theme flashes. Ask the user to run GUI cases unless they explicitly authorize agent-driven desktop execution. CI containers and Xvfb do not count as this acceptance.
- [ ] **Record evidence, review and commit.** Update both plan status banners, the native report and docs/STATUS.md with tests, observed limitations and unrun cases. Update existing user/maintenance guides for final behavior only; index a new guide only if one was actually created. Commit `test: verify Qt and KDE desktop color integration` with required trailer. Obtain whole-branch review and fix scoped findings before offering merge/push choices. Keep pending GUI acceptance explicit.

## Execution handoff

Use the repository's existing subagent-driven workflow; no new execution-method decision is needed. Read both plans
and the approved spec before starting. Refresh from main, preserve unrelated work, and record any conflict resolution.
The native compatibility gate and app source-selection feature are separate reviewable deliverables, not one large
unverified UI change. There is no authorization in this planning request to implement, merge, push or launch a GUI.
