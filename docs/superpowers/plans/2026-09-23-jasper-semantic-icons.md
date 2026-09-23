# Semantic SDK icons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for native implementation task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Plugins request `appearance().icon(IconName.LOCK)` and the application supplies modern or retro artwork automatically.

**Architecture:** Add a semantic SDK enum and an app-owned modern/retro mapping, retaining previous custom SVG overloads. Reuse existing 16/28px rendering and raster resources. Vault requests LOCK/UNLOCK without knowing a skin or resource path.

**Tech Stack:** JBR25, Swing, Gradle wrapper, JUnit/AssertJ; no new dependency.

**Spec:** User's 2026-09-23 clarification supersedes the primary API in [prior design](../specs/2026-09-23-jasper-sdk-skin-icons-design.md). Semantic built-ins are preferred; custom SVG pairing remains supported. User requested update from main and direct implementation, continuing native execution.

**Status:** All implementation tasks complete, full verification and independent review passed with no findings; main c1eb385 merged before implementation. Merge resolution keeps main's modern title background and existing light Metal background, plus Vault File-menu placement without a rail entry. Relevant app/Vault tests passed.

## Global Constraints

- JDK-only SDK/testkit, app SDK imports only in plugins, no package cycles or new dependencies.
- Existing API signatures and custom icons continue working; skin remains restart-required.
- Compact icons16, retro host toolbar28; modern follows live foreground.
- Vault modern LOCK/UNLOCK SVG bytes stay identical to its original artwork.
- No native GUI/login shell/benchmark; no merge into main or push. Commit trailers required.

## Review Focus

- Semantic requests must never resolve bundled artwork through a plugin class loader (task2).
- LOCK/UNLOCK must preserve Vault's original modern pixels, and differ in both skins (task2).
- Both real and fake host must reject null names and select every enum entry (tasks1/2).
- Multiple shared placements must preserve sizes and existing custom-overload behavior (task2 + existing tests).
- Main's removal of Vault rail action must survive this follow-up (task3 + merge tests).

### Task 1: Semantic SDK and fake contract

**Files:** `jasper-sdk/.../ui/{IconName,Appearance}.java`, `JasperSdk.java`, `jasper-sdk-testkit/.../{FakeNamedIcon,FakePluginContext}.java`, existing SDK/fake tests.
**Interfaces:** `Appearance.icon(IconName): Icon`, `IconName` with same22 semantic names as current catalog, `FakeNamedIcon(IconName name, boolean retro): Icon`.

- [x] Add failing tests calling every semantic name in both fake styles; assert FakeNamedIcon name/retro and16px dimensions. Legacy Appearance implementations compile and default throws UnsupportedOperationException for a nonnull semantic choice, NPE for null. Existing custom fallback remains unchanged.
```java
assertThat(context.appearance().icon(IconName.LOCK)).isEqualTo(new FakeNamedIcon(IconName.LOCK, retro));
assertThatNullPointerException().isThrownBy(() -> context.appearance().icon((IconName) null));
```
- [x] Run SDK/testkit tests; expect absent enum/overload compile failure.
- [x] Add documented default overload: requireNonNull(name), then throw UnsupportedOperationException("Host does not provide named icons"). Hosts override; no plugin resource fallback. Add IconName with22 individually documented entries and SDK version0.7.3. FakeNamedIcon is an immutable public record (nonnull name,16px width/height, no-op paint); FakePluginContext returns it using captured fake style. Update VERSION assertion.
- [x] Rerun SDK/testkit tests and commit `feat: add semantic SDK icon names`.

### Task 2: Application-owned modern/retro catalog

**Files:** `platform/{NamedIcons,AppIcons}.java`, `plugins/HostedUi.java`, `icons/standard/` assets/notices/manifest, platform and HostedUi tests.
**Interfaces:** `AppIcons.named(String): Icon` maps host-native names; HostedUi passes IconName.name(). `NamedIcons` owns string->modern resource map only; retro reuses SkinIcon.

- [x] Add failing host tests in both styles, every name, with a ClassLoader that resolves no resources to establish app ownership; assert widths and rendering. Check missing/null names, modern live recoloring, legacy methods. Add packaged-manifest test and assert modern LOCK/UNLOCK source hashes match Vault originals; verify two different images for the states in each skin.
```java
assertThat(AppIcons.named("LOCK").getIconWidth()).isEqualTo(16);
assertThat(AppIcons.forToolbar(AppIcons.named("LOCK")).getIconWidth()).isEqualTo(retro ? 28 : 16);
```
- [x] Run relevant app tests; expect missing helper/override failure.
- [x] Bundle original Vault lock/unlock SVGs under app `icons/standard/LOCK.svg` and `UNLOCK.svg`. Reuse matching existing bundled Tabler SVG bytes; add missing semantic choices from pinned official Tabler commit0239805680a36bab4e1070529b6744924402d804. Keep MIT license/source URLs/hash manifest with assets. Explicit map covers22 names; no network at runtime. `AppIcons.named` validates key then returns captured SkinIcon in retro, otherwise calls themed with the APP classloader/resource. HostedUi adds override with nonnull IconName.
- [x] Rerun app icon/host/workspace regressions and architecture guards; commit `feat: resolve named icons entirely in the host`.

### Task 3: Vault adoption, docs and verification

**Files:** Vault plugin/tests/manifest, semantic documentation/catalog previews, SDK README, STATUS and this plan/design amendment.
**Interfaces:** Vault uses `context.appearance().icon(IconName.LOCK/UNLOCK)`; fake tests inspect FakeNamedIcon instead of FakeSkinIcon.

- [x] Change Vault regression expectations to exactly LOCK/UNLOCK FakeNamedIcon records; run and observe failure before migration. Retain existing tests for original sample custom pairing and Vault File menu/no-rail.
- [x] Change Vault's calls/import to IconName and require SDK>=0.7.3,<0.8. No style conditionals in plugin. Keep original SVGs for compatibility/hash evidence; normal app artwork is byte-identical copies.
- [x] Run Vault tests, expect PASS. Document named catalog as preferred (both families app-owned), custom overloads as optional, old host default UnsupportedOperationException, version and fake inspection. Render real Vault status/lock/unlock controls headlessly in both styles; inspect pixels/sizes.
- [x] Run all architecture guards, check and installDist, count XML tests and verify installed standard assets hashes/notices. Update STATUS/plan evidence; commit `feat: use host-owned icons in Vault`.
- [x] Independent review of semantic follow-up plus merge resolution; fix important findings with regression evidence, keep branch/worktree and no push/merge to main.

## Execution adjustments

Included the actual Vault manager's lock/unlock buttons in semantic adoption after discovering
its independent custom lock painter. Modern manager controls now use the shared lock artwork
and the unlock button gains a matching icon. Status/menu modern SVGs remain byte-identical.
The two-style manager fixture uses separate temporary vault directories to avoid recreating an
existing vault. Full-suite checks exposed stale tests after main's change: status expectations
now target chrome background (contrast checks retained), and the staged plugin test verifies
File-menu placement instead of the removed rail item. Focused tests passed after correction.

## Verification

SDK/testkit contract RED->GREEN (`68173d0`); application named catalog/host RED->GREEN
(`14808f0`), including class-loader isolation, all22 name renders, modern recoloring and
byte-identical Vault source tests. Vault startup and actual manager requests RED->GREEN;
main integration fixtures corrected as described above. Full architecture/check/installDist
gate passed:1596 tests,1593 passed,3 expected skips,0 failures/errors. Installed app jar
verified22 modern SVG hashes and55 retro PNG hashes with notices/licenses. Actual manager
buttons and status-bar controls rendered headlessly in both skins and lock states; preview
in docs/images/vault-semantic-icons.png. No native window or login shell launched.

## Independent review and final decisions

Fresh read-only review of `8ad055b..9750043` found no actionable issues. Reviewer confirmed
all1596 XML tests (zero failures/errors,3 skips), all22 installed SVG hashes/legal notices,
original Vault lock/unlock identity and preview. No fix pass or deferred findings.

- Proceeded directly under the user's current implementation request and earlier native
  execution authorization; the semantic API matches the clarified contract. Cost if wrong:
  revision after native feedback.
- Resolved main's status-background conflict by retaining its title color for modern and
  Metal's Panel.background for retro. Cost if wrong: a style-specific color adjustment.
- Included manager lock/unlock controls, preventing their separate painter from bypassing
  skin selection. Effect: manager uses the shared modern lock and gains an unlock glyph.
- Native window/physical Retina acceptance remains user-run under AGENTS.md; headless
  tests/previews pass. Cost if wrong: a native-only visual issue may need follow-up.

No merge back to main or push. This plan's scratch ledger is removed after this durable
record is committed; branch/worktree remain ready for user testing.
