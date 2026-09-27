# Plugin icons

SDK 0.8.2 supplies 47 semantic meanings and the complete vendored platform New UI catalog
plus Terminal: **885 choices / 1,744 SVGs** including automatic dark variants. No icon downloads
happen at build time or runtime.

Prefer a semantic name so Jasper can update its artwork centrally:

```java
import dev.jasper.sdk.ui.IconName;

button.setIcon(context.appearance().icon(IconName.LOCK));
```

For another icon, use the generated catalog. IDE completion and `BundledIcon.values()` expose
every choice, including upstream size variants:

```java
import dev.jasper.sdk.ui.BundledIcon;

button.setIcon(context.appearance().icon(BundledIcon.TOOLWINDOWS_PROJECT));
var search = context.appearance().icon(BundledIcon.GENERAL_SEARCH);
```

The app's `AppIcons` manager owns loading, caching, size, light/dark selection and validation.
`NamedIcons` alone maps meanings to artwork IDs. Plugins never import app classes or use app
resource paths. Host artwork loads through the host's class loader, independently of plugin
resources. A retained icon follows live theme switches. Catalog icons preserve their authored
colours and upstream dark siblings, and fit proportionally in a 16-logical-pixel square.

The collection comes from IntelliJ's `platform/icons/src/expui` and the Terminal plugin's
`resources/icons/expui` at `60b8fbadc2f08ef18a6ecab0c138a849e14f13fb`. It includes every SVG
in those collections, including intentional empty/stub artwork, rather than every language
plugin's assets across the repository. Source paths, hashes, license and notice live beside
the resources. The semantic KEY keeps Jasper's existing tinted Tabler key because this
upstream collection has no key symbol; the mapping and fallback remain central.

SDK 0.8.2 adds EDIT, MORE, PREVIOUS, NEXT, DROPDOWN, CLOSE_HOVERED, MATCH_CASE, REGEX,
SEARCH_HISTORY, NEW_WINDOW, EXIT, PREVIOUS_MATCH and NEXT_MATCH. Remote and Vault require
`>=0.8.2, <0.9`; plugins using only older semantic meanings continue to work. Older custom
`Appearance` implementations inherit defaults that throw UnsupportedOperationException for
unsupported host catalogs. Null enum arguments throw NullPointerException.

## Custom artwork

`icon("path/in/your/jar.svg")` remains available for plugin-specific artwork. This optional
API draws an authored SVG in a 16px square, with FlatLaf's classic palette remapping. The
sample plugin intentionally demonstrates it. Ordinary controls should use the host catalogs.

## Headless testing

`FakeNamedIcon` and `FakeBundledIcon` record the choice, report 16px dimensions and paint nothing:

```java
var icon = context.appearance().icon(BundledIcon.GENERAL_SEARCH);
assertThat(icon).isEqualTo(new FakeBundledIcon(BundledIcon.GENERAL_SEARCH));
```

## Updating the catalog

Update the commit and subtree pins in `tools/icons/fetch-intellij-icons.py`, then run it.
It stages and verifies every download against Git blob IDs, writes SHA-256 provenance,
and regenerates the host catalog and SDK enum together. The canonical Apache-2.0 license is
retained; upstream's root IDE build-terms file is not the artwork license.
Run `./gradlew check`: rendering tests cover all choices in light/dark at 1x/2x, live switching,
non-square artwork, invalid IDs, provenance, SDK defaults and plugin class-loader isolation.

For a headless before/after contact sheet, run `python3 tools/icons/render-preview.py`.
It uses the configured JBR toolchain and writes `jasper-app/build/icon-preview/comparison.png`.
It creates no native windows and starts no terminal session. The baseline is pinned to
Jasper commit `90c2e574` and must be present in the local Git history.
