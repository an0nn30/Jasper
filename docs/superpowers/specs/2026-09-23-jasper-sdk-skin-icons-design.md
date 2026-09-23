# SDK icons for modern and retro appearance

**Status:** API direction approved by the user on 2026-09-23. Written specification awaiting review. No implementation has started.

## Purpose and scope

Let a plugin declare its modern SVG and a bundled OldGNOME2 icon together. Jasper selects the appropriate artwork for the running skin, so plugin controls fit the application's appearance without plugin-side skin detection or dependencies on application rendering libraries.

The user approved this API shape, an OldGNOME2 enum, compatibility with existing icons, and 28-pixel retro host toolbar artwork with 16-pixel compact artwork. Modern icons continue following the live light/dark foreground. Retro style remains fixed until restart.

This is an additive amendment to the earlier retro design's prohibition on SDK changes. That prohibition applied to the initial Metal implementation; the user explicitly requested this SDK extension afterward. All other module, threading, lifecycle and restart constraints remain binding.

This deliverable includes the SDK contract, application implementation, bundled catalog, testkit, sample/Vault adoption at their existing Appearance.icon calls, documentation and verification. It does not add a general skin framework, change contribution record signatures, replace arbitrary third-party artwork, rewrite plugin-owned custom icon painters, or alter the main toolbar's approved Tango artwork.

## Public contract

Add `dev.jasper.sdk.ui.OldGnomeIcon` and an overload on `Appearance`:

```java
Icon icon(String modernSvgResourcePath, OldGnomeIcon retroIcon);
```

Example:

```java
var lock = context.appearance().icon(
    "dev/example/icons/lock.svg", OldGnomeIcon.LOCK);
button.setIcon(lock);
context.actions().register(
    ActionSpec.of("dev.example.lock", "Lock").withIcon(lock),
    invocation -> lockVault());
```

The returned icon has stable 16-by-16 logical dimensions. It can be used wherever Swing `Icon` is accepted, including actions, toolbar dropdowns, menus, rail/panel specifications, status items, palette rows and ordinary Swing controls in plugin content. Plugins do not inspect the active skin to choose an icon. No new SDK style enum or theme event is needed.

The modern argument follows the existing method's classpath and monochrome SVG contract. Both arguments are required. A null retro choice throws `NullPointerException`; a null or missing SVG resource throws `IllegalArgumentException`, including when running retro. Validate both choices at creation so restarting in another skin does not discover a missing resource. Preserve existing malformed-SVG handling rather than promising a new SVG validator.

`Appearance.icon(String)` retains its current semantics and signature. Implement the new overload as a default method that validates the retro argument and delegates to the old method. Jasper's real host and testkit override it to select retro artwork. The default preserves source and binary compatibility for existing third-party implementations of `Appearance`; such implementations provide modern-only fallback until they implement the overload.

Publish the additive API as SDK 0.7.2, following the existing 0.7.1 additive release. Existing plugins remain compatible with the new host. Plugins using the overload declare `sdk = ">=0.7.2, <0.8"`; they cannot call new methods on older hosts. Update the sample and Vault requirements accordingly. Add `@since 0.7.2` documentation and keep SDK signatures and dependencies JDK-only.

## Catalog and assets

The enum is a curated, stable set of actual OldGNOME2 choices. It has no image-loading logic, application resource paths or vendor types. New constants can be added later; released constants keep their meaning. These initial mappings are relative to a source size directory under the user-provided `~/Downloads/OldGNOME2`:

| Constant | Original resource |
| --- | --- |
| `LOCK` | `stock/data/stock_lock.png` |
| `UNLOCK` | `stock/data/stock_lock-open.png` |
| `KEY` | `stock/generic/stock_keyring.png` |
| `FOLDER` | `filesystems/gnome-fs-directory.png` |
| `SAVE` | `stock/io/stock_save.png` |
| `SEARCH` | `actions/system-search.png` |
| `HISTORY` | `stock/navigation/stock_undo-history.png` |
| `BOOKMARK` | `places/user-bookmarks.png` |
| `ADD` | `actions/gtk-add.png` |
| `REMOVE` | `actions/gtk-remove.png` |
| `DELETE` | `actions/gtk-delete.png` |
| `COPY` | `stock/generic/stock_copy.png` |
| `PASTE` | `stock/generic/stock_paste.png` |
| `REFRESH` | `actions/view-refresh.png` |
| `SETTINGS` | `actions/gtk-preferences.png` |
| `EXECUTE` | `actions/gtk-execute.png` |
| `CONNECT` | `actions/gtk-connect.png` |
| `DISCONNECT` | `actions/gtk-disconnect.png` |
| `NETWORK` | `filesystems/gnome-fs-network.png` |
| `INFO` | `actions/gtk-info.png` |
| `HELP` | `actions/gtk-help.png` |
| `CLOSE` | `stock/generic/stock_close.png` |

Bundle unmodified available 16/24/32/48-pixel source images for these mappings in an explicitly separate SDK catalog resource directory. Reuse shared loading/scaling logic where useful, but do not route these choices through the application's semantic-name map: that map deliberately substitutes Tango for some toolbar actions. `OldGnomeIcon.SEARCH`, for example, must render the listed OldGNOME2 search artwork.

The user supplied GPL2+ licensing for this collection. Retain the existing license and provenance wording and extend the hash manifest to cover each added original resource. No runtime access to Downloads, network fetch or plugin-supplied raster file is needed. Produce a labeled catalog preview for documentation/review; this is a contact sheet of bundled assets, not newly generated artwork.

## Rendering and sizing

The icon returned by the new overload selects artwork from the captured running style. A pending configuration change cannot affect it before restart. In modern mode the SVG retains live foreground recoloring across light/dark changes; in retro it uses original raster colors.

Host toolbar buttons and dropdowns recognize only icons produced by this new API and request an independent 28-by-28 variant in retro. Modern toolbar variants stay 16-by-16. The shared 16-pixel icon is never resized or mutated: one action can appear concurrently in a menu, toolbar, palette and multiple windows. All non-toolbar placements and direct Swing use retain 16 pixels. There is no implicit resizing based on the component passed to `paintIcon`, and no size change during painting.

Keep this capability in an application-owned concrete icon class and a platform helper that accepts `Icon`. No new single-implementation interface is necessary. An ImageIcon-compatible retro implementation preserves Metal's native disabled-icon generation. The helper returns arbitrary custom icons and legacy one-argument icons unchanged. It must not infer managed icons from filenames or wrap every external icon.

Use the source nearest to the target resolution, preferring downsampling over upsampling where possible, and JDK multi-resolution image support for 1x/2x displays. Collections lacking large originals can be scaled from their largest bundled source; document that limitation rather than substitute different artwork. Cache bounded catalog/size variants. Do not cache plugin class loaders in a global registry; plugin-owned modern icon objects live only as long as their normal UI/contribution owners.

Disabled/enabled states, alpha, captions, accessible labels, tooltip behavior and flat retro toolbar styling remain the host's responsibility. This extension does not change button dimensions other than adapting new managed toolbar icons to the existing 28-pixel standard.

## Ownership and module boundaries

`dev.jasper.app.plugins.HostedUi` implements the SDK overload and translates `OldGnomeIcon` to an app-native catalog key. Exhaustive mapping and tests ensure every SDK constant resolves. Production SDK imports remain confined to `dev.jasper.app.plugins`.

`dev.jasper.app.platform` owns image resources, rendering, caching and managed icon sizing. It receives app-native values only. Workspace toolbar code calls the platform sizing helper when presenting contributions. Contribution records continue carrying `javax.swing.Icon`; they do not depend on platform or SDK types and retain their existing package DAG.

No terminal or Buddy implementation changes are necessary. Swing UI mutation remains on the EDT. Icon creation retains the existing method's threading contract and does not install or mutate a look and feel.

## Testkit and adoption

Add `FakePluginHost.setRetroIcons(boolean)` as a pre-start configuration helper. It defaults to false and rejects changes once any plugin has been started; restarting a fake host represents a style restart. Retro configuration reports LIGHT and rejects attempts to switch to DARK. Existing modern-only test behavior remains unchanged.

The fake overload validates the SVG path and enum exactly as the host does. It returns a testkit `FakeSkinIcon` record implementing `Icon`, with accessors `modernSvgResourcePath()`, `retroIcon()` and `retro()`. It reports 16-by-16 dimensions and paints nothing, consistent with the existing fake's blank icons. This permits inspection without adding methods to the SDK's `Icon` return contract. The fake has no production image dependencies and is not a visual renderer. Real app tests cover actual pixels, toolbar sizing and disabled rendering.

Update the sample's existing SVG icon calls to pair their artwork with `OldGnomeIcon.EXECUTE`, and Vault's existing locked/unlocked SDK icons with `LOCK`/`UNLOCK`. Retain their modern SVGs, actions and behavior. Plugin-owned custom painters such as Vault's internal editor icons remain valid ordinary Swing icons; migrating those is outside this additive API deliverable.

Update SDK JavaDoc/README, plugin authoring, SDK architecture and application maintenance documentation with the overload, catalog, size behavior, compatibility rule and sample. Explain that opting in supplies the cohesive appearance; legacy plugins remain valid but cannot acquire a meaningful retro icon automatically.

## Verification and acceptance

Use meaningful regressions for:

- Legacy Appearance implementations, old one-argument calls and custom Swing icons remaining compatible.
- Both real and fake overloads rejecting null choices and missing modern resources in both styles.
- Every enum mapping resolving to bundled, decodable images with manifest hashes and license notice present in the installed distribution.
- Modern live light/dark recoloring; original retro color rendering; pending style changes not changing the running artwork.
- One managed icon shared between a menu/palette and toolbar yielding 16/28 pixels respectively without mutation; dropdowns and multiple windows follow the same rule.
- Metal disabled rendering, 1x/2x headless previews, toolbar label compaction/restoration and minimum-width layout.
- Sample and Vault contributions selecting their configured family under both fake styles, with no changes to actions or lifecycle cleanup.

Run targeted tests during implementation, then all architecture guards, `./gradlew check` and `:jasper-app:installDist` with JBR 25. Inspect test XML and packaged assets. Render lightweight Swing components headlessly for visual checks; do not launch the application, a login shell or benchmark. Native acceptance stays user-run.

## Alternatives and execution

An overload returning the existing Swing type is chosen because it fits all current contribution and plugin-content APIs. Replacing every icon field with a descriptor would impose broader migration. Plugin-side style detection would duplicate host policy and sizing. Neither is needed for the approved requirement.

The implementation remains one runnable deliverable on the existing isolated `codex/retro-metal` branch. After approval of this written spec, prepare the implementation plan for review. The user's earlier native execution preference remains the default; do not infer permission to merge or push. Record any execution deviation in the plan and `docs/STATUS.md`.
