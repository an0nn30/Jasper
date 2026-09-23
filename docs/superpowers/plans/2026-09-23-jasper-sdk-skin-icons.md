# SDK skin icons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Plugins pair modern SVGs with OldGNOME2 enum choices; Jasper selects and sizes them consistently.

**Architecture:** Add a default Appearance overload, with real host/testkit overrides. The app platform package owns a raster catalog and ImageIcon subclass with immutable dimensions; the plugin boundary translates SDK enum names, and workspace asks for toolbar variants without changing shared icons.

**Tech Stack:** Java/JBR 25, Swing, Gradle wrapper, JUnit 5, AssertJ. No new dependency.

**Spec:** [Approved design](../specs/2026-09-23-jasper-sdk-skin-icons-design.md).

**Status:** All four implementation tasks complete; full gate passed (1,591 tests, zero failures/errors, three expected skips). Independent final review pending. User explicitly authorized writing and executing on 2026-09-23; execute inline without another approval checkpoint. Base `4234a95`; existing isolated `codex/retro-metal` worktree. One independent final review.

## Global Constraints

- SDK and testkit remain JDK-only; SDK imports in app production stay in `dev.jasper.app.plugins`.
- Running skin remains fixed until restart. Modern light/dark recoloring remains live.
- New API icons are 16 logical pixels; retro host toolbar variants are 28. Arbitrary and legacy icons retain existing behavior.
- Exact 22 OldGNOME2 choices from the spec; no Tango substitution in this catalog.
- License/provenance/hash manifest accompany unmodified bundled source PNGs.
- No GUI, shell-session or benchmark launch. No merge/push; commits include `Co-Authored-By: Codex <noreply@openai.com>`.
- Preserve modern behavior, action/contribution signatures, terminal and Buddy boundaries.

## Review Focus

- One icon shared across several placements/windows must never change dimensions (task 3 regression).
- Null retro choice and missing SVG in retro must fail before any restart (tasks 1/3 regressions).
- Native Metal disabled rendering must still show managed icons (task 2 regression).
- Legacy Appearance implementations and arbitrary custom Icon instances must still work (tasks 1/3 regressions).
- Failed plugin start must still freeze fake style, and live modern recoloring must survive managed sizing (tasks 1/3 regressions).

## File map

- SDK: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/{Appearance,OldGnomeIcon}.java`, `JasperSdk.java`, SDK tests.
- Testkit: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/{FakePluginHost,FakePluginContext,FakeSkinIcon}.java`, `FakeSkinIconTest.java`.
- Platform: `jasper-app/src/main/java/dev/jasper/app/platform/{OldGnomeCatalog,SkinIcon,AppIcons}.java`, catalog resources in `icons/oldgnome-sdk/`, `OldGnomeCatalogTest.java`.
- Host/workspace: `plugins/HostedUi.java`, `workspace/WindowChrome.java`, corresponding existing tests.
- Adoption: sample/Vault Java and descriptors, plugin tests.
- Docs: plugin authoring, SDK README/architecture, app maintenance, STATUS and this plan; preview under `docs/images/oldgnome-sdk-catalog.png`.

### Task 1: Add compatible SDK contract and inspectable fake

**Interfaces:** Produces `OldGnomeIcon`, default `Appearance.icon(String, OldGnomeIcon)`, SDK 0.7.2, `FakeSkinIcon(String modernSvgResourcePath, OldGnomeIcon retroIcon, boolean retro)`, `FakePluginHost.setRetroIcons(boolean)`.

- [x] Write `AppearanceIconTest` with an anonymous old-style Appearance implementing only variant/onChanged/icon(String); assert new overload returns its legacy icon, rejects null enum, and propagates missing resource rejection. Add fake test using an existing classpath resource, both styles, dimensions/selection/variant, missing resources, null enums, post-start style rejection (including failed start), and DARK rejection in retro.

```java
assertThat(legacy.icon("icon.svg", OldGnomeIcon.LOCK)).isSameAs(original);
assertThatNullPointerException().isThrownBy(() -> legacy.icon("icon.svg", null));
// Inside the fake test, after start:
var icon = (FakeSkinIcon) context.appearance().icon(resource, OldGnomeIcon.LOCK);
assertThat(icon.retro()).isEqualTo(retro);
assertThat(icon.getIconWidth()).isEqualTo(16);
assertThatIllegalStateException().isThrownBy(() -> host.setRetroIcons(!retro));
```

- [x] Run `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test`; expect compile failure for absent enum/overload/fake contract.
- [x] Implement the enum with the spec's exact constants. Add documented default overload:

```java
default Icon icon(String modernSvgResourcePath, OldGnomeIcon retroIcon) {
    java.util.Objects.requireNonNull(retroIcon, "retroIcon");
    return icon(modernSvgResourcePath);
}
```

`FakeSkinIcon` is a public record implementing Icon: compact constructor requires nonnull path and enum; width/height return 16; paintIcon is empty. FakePluginHost stores private boolean retroIcons, exposes package-private getter, and setter checks `contexts.isEmpty()` before setting style; true sets variant LIGHT, false preserves current variant. `setVariant` requires nonnull and rejects DARK when retroIcons. FakePluginContext override validates enum, calls existing `icon(path)` for resource validation, returns new FakeSkinIcon with host's captured boolean. Update version assertion in PaletteValuesTest and JasperSdk.VERSION to 0.7.2. Add JavaDoc for every public member.
- [x] Rerun task command, expect all tests PASS; commit `feat: add SDK modern and retro icon contract`.

### Task 2: Bundle and render the explicit OldGNOME2 catalog

**Interfaces:** Consumes spec mappings. Produces app-native `OldGnomeCatalog.icon(String name, int size)` (package-private) and `SkinIcon(String name)` plus `SkinIcon.toolbar()` (package-private). Catalog keys exactly match enum names but platform never imports SDK.

- [x] Write `OldGnomeCatalogTest`: iterate all keys at 16/28 and scales1/2, assert nontransparent colored pixels and dimensions; verify copied resource SHA256s and notices; reject unknown keys/sizes; compare managed enabled/disabled Metal button images for visibility and changed pixels. Assert separate toolbar variant leaves original16; same cached catalog/size reuses image.

```java
var small = new SkinIcon("LOCK");
var large = small.toolbar();
assertThat(small.getIconWidth()).isEqualTo(16);
assertThat(large.getIconWidth()).isEqualTo(28);
assertThat(large).isInstanceOf(javax.swing.ImageIcon.class);
```

- [x] Run `./gradlew :jasper-app:test --tests '*OldGnomeCatalogTest'`; expect absent catalog/type compile failure.
- [x] Copy every available 16/24/32/48 source for each mapping to `oldgnome-sdk/<size>/<KEY>.png`. Write `assets.tsv` with resource, original source and SHA256 columns. Copy original GNOME NOTICE/LICENSE; preserve provenance and add catalog purpose.
- [x] Implement OldGnomeCatalog with explicit key-to-available-size Map, concurrent bounded image cache keyed by name/target size, classpath ImageIO loading and source-dimension checks. Sizes supported are16/28. Build base/2x image variants by selecting first source size >= target, otherwise largest; bicubic resize into ARGB only if dimensions differ. Return ImageIcon backed by BaseMultiResolutionImage. Reject bad names/sizes with IllegalArgumentException; absent/bad packaged files with IllegalStateException or UncheckedIOException.
- [x] Implement final SkinIcon extending ImageIcon: keep final catalog key, construct using catalog image at16; toolbar returns a new ImageIcon from the catalog at28. No plugin reference or mutable shared size; Metal recognizes ImageIcon for disabled generation.
- [x] Rerun task command, expect PASS; commit `feat: bundle OldGNOME2 SDK icon catalog`.

### Task 3: Wire host selection and placement sizing

**Interfaces:** Consumes tasks1/2; produces `AppIcons.skin(ClassLoader,String,String): Icon`, `AppIcons.forToolbar(Icon): Icon` and HostedUi override. Modern returns existing themed SVG; retro returns SkinIcon. No SDK imports outside plugins.

- [x] Extend HostedUiTest for every enum and both styles: icon16, valid raster in retro; null enum and absent SVG rejection; old single-path API unchanged. Verify host maps all constants and legacy icons remain usable.
- [x] Extend WindowChromeContributionsTest with one managed icon shared by two WindowContent instances, action, menu and dropdown: toolbars28 in retro, model/menu16 and unchanged identity, modern16, custom ImageIcon unchanged. Layout in narrow/wide widths remains usable. Add platform regression for modern SVG paint colors across LIGHT/DARK and current-style capture (change LAF in isolated test only; a previously created icon keeps its family).

```java
var icon = AppIcons.skin(getClass().getClassLoader(), "dev/jasper/app/icons/search.svg", "SEARCH");
assertThat(icon.getIconWidth()).isEqualTo(16);
assertThat(AppIcons.forToolbar(icon).getIconWidth()).isEqualTo(retro ? 28 : 16);
assertThat(AppIcons.forToolbar(custom)).isSameAs(custom);
```

- [x] Run `./gradlew :jasper-app:test --tests '*HostedUiTest' --tests '*WindowChromeContributionsTest' --tests '*SkinIconsTest'`; expect missing host/helper behavior failure.
- [x] Implement `AppIcons.skin`: validate nonnull catalog key against catalog; validate SVG via loader resource lookup in both skins; return new SkinIcon when SwingAppearance.retro(), otherwise existing themed SVG. `forToolbar` returns `skin.toolbar()` only for SkinIcon, otherwise input. HostedUi override rejects null enum and passes `retroIcon.name()` to helper (exhaustive catalog test prevents enum/mapping drift). In WindowChrome contributedButton replace nonnull icon with `AppIcons.forToolbar(icon)`; preserve fallback and styling.
- [x] Rerun task command, expect PASS. Run architecture checks; commit `feat: resolve plugin icons for active skin and toolbar size`.

### Task 4: Adopt, document and verify the complete deliverable

**Interfaces:** Consumes overload/catalog/fake/host; publishes examples, migrated plugins and packaged assets.

- [x] Add plugin tests capturing requested FakeSkinIcon values during startup in modern/retro (plugin subclass/capturing context fixture as existing tests allow). Sample uses EXECUTE for existing flask SVG calls; Vault LOCK/UNLOCK for existing SVG calls. Run plugin tests to observe failure against original one-argument calls.
- [x] Change those calls and descriptor SDK ranges to >=0.7.2,<0.8; preserve SVGs. Rerun sample/Vault tests, expect PASS.
- [x] Document exact Java usage, default16/toolbar28 behavior, opt-in compatibility, SDK minimum, enum mappings and fake usage in SDK README, plugin authoring and architecture; add icon maintenance/resource workflow. Create a labeled contact sheet from actual bundled PNGs using headless Java/AWT and inspect it. Update STATUS/spec/plan status with implementation evidence.
- [x] Run `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist`; expect BUILD SUCCESSFUL. Count XML failures/errors/skips; verify installed app jar contains resources and matching manifests. Inspect headless modern/retro toolbar/disabled previews at1x/2x. Do not launch native windows.
- [x] Commit `docs: adopt and document SDK skin icons`. Request one fresh independent review for diff from base4234a95 with spec/plan/ledger and review focus. Fix important findings with RED/GREEN regressions, rerun full gate; record final report. Preserve branch/worktree and do not merge/push.

## Execution evidence and adjustments

Task 1 `5162006`: missing contract RED -> SDK/testkit GREEN. Task 2 `8543ed5`: missing
catalog RED, then original KEY 46x48 source exposed overly strict square validation; centered
proportional scaling fixed it. Task 3 `7a928b1`: missing host/helper RED -> GREEN, including
all enum pixel comparisons and two-window shared placement. Contributed actions now publish
SMALL_ICON alongside Command.ICON because Swing menus previously received null icons.
The EDT extension intercepts regular tests only; parameter coverage uses a regular test loop.
Task 4: sample/Vault requested no paired icons before migration (both tests RED), then GREEN.
A documentation rewrite error was caught by the guide-link test and corrected before final gate.
User explicitly requested plan plus execution, so no redundant plan-review pause was made.

Full gate: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture
verifyPluginArchitecture check :jasper-app:installDist` -> BUILD SUCCESSFUL. XML totals:
1,591 tests, 1,588 passed, three expected skips, zero failures/errors. Installed jar verification:
55 original PNG hashes and both legal notices. Contact sheet and 1x/2x enabled/disabled toolbar
renders inspected. Source hygiene and diff checks pass. Native acceptance remains user-run.
