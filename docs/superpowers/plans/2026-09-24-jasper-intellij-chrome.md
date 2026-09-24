# IntelliJ-style modern chrome Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In the modern style, give Jasper's title bar, toolbar, tab strip and find bar the IntelliJ classic-UI look with IntelliJ's colour action icons. Extend SDK 0.7.5 so plugins get matching artwork in both skins.

**Architecture:**
- IntelliJ SVGs are vendored under `icons/intellij/` with a hash-pinned manifest. They render untinted, and FlatLaf's global `FlatSVGIcon.ColorFilter` remaps IntelliJ palette colours to the running theme's `Actions.*` and `Objects.*` colours (verified in FlatLaf 3.7 bytecode during planning).
- `MacTitleBar` becomes a title-only row. `WindowTabs` moves under the toolbar and is rewritten as IntelliJ editor tabs, without animation. `WindowChrome` builds a separate modern toolbar row. `FindBar` builds a modern or a retro row.
- Retro code paths stay as they are.

**Tech Stack:** Java 25 on JBR 25, Swing, FlatLaf 3.7 and flatlaf-extras 3.7, JUnit 5, AssertJ, Gradle wrapper. No new dependency.

**Spec:** [2026-09-24-jasper-intellij-chrome-design.md](../specs/2026-09-24-jasper-intellij-chrome-design.md) (approved; trimmed to four new icon names on 2026-09-24).

**Status:** All tasks complete; `./gradlew check` passed (1,771 tests, 0 failures, 0 errors, 3 skipped), with the counts from Step 2. Branch `claude/intellij-chrome`; worktree `/Users/RQ7RQVF/projects/moray/.worktrees/intellij-chrome`. Another session is committing GTK work in the main checkout (`claude/gtk-theme`): never run git commands against `/Users/RQ7RQVF/projects/moray` itself.

### Decisions made while planning (the user must confirm at plan review)

1. **Tab strip height.** The strip keeps the existing *View ▸ Tab height…* setting (range 28–72, default 38). The title row is a fixed 28 px (modern) or 32 px (retro) and no longer follows it. The spec's "28 px" tab strip is reached by setting Tab height to 28.
2. **Toolbar default mode.** The saved default stays `icons_and_labels`, so modern shows the label to the right of each icon until the user picks *View ▸ Toolbar ▸ Icons*. Changing the global default would also change retro and the config defaults and their tests.
3. **Narrow toolbar.** It keeps today's collapse behaviour: labels drop first, then everything compresses proportionally. The spec's » overflow menu is dropped, because the spec also says "keeps today's collapse behaviour", which contradicts adding one.
4. **Tabs lose several extras**, following the spec's tab contents (icon, title, close):
   - the spring animation (`TabMotion`), the ‹ › paging buttons, the tab-strip + button and the painted shortcut label all go;
   - the shortcut moves into the tab tooltip;
   - New tab lives in the toolbar.
5. **Painted New tab shortcut.** The shortcut hint painted on the toolbar's New tab button moves into every toolbar button's tooltip, as in IntelliJ.
6. **`SessionRequest` (package `dev.jasper.app.terminals`) carries an opaque `javax.swing.Icon`.** That package holds no Swing objects today, so its package-info gains that one documented exception. The package never paints or inspects the icon.
7. **Names without an IntelliJ match** (`KEY`, `LOCK`, `UNLOCK`, `CONNECT`, `DISCONNECT`, `DELETE`) keep their existing tinted Tabler artwork in modern.
   - `LOCK` and `UNLOCK` must stay a matching pair, and their bytes must equal Vault's own SVGs (existing test).
   - IntelliJ has no unlocked padlock.
8. **Colour is limited to what IntelliJ itself uses.** Most terminal actions map to IntelliJ icons that are grey: add, find, split and settings. Colour appears where IntelliJ uses it: refresh, execute, the server and console nodes, and the dark variants.

## Global Constraints

- Build with `./gradlew` only (JBR 25 toolchain). The gate is `./gradlew check`. For counts, read `*/build/test-results/test/*.xml`.
- **Never launch the GUI** (`:jasper-app:run`, `:jasper-app:bench`). The user does visual acceptance in modern light, modern dark and retro.
- Work only in `/Users/RQ7RQVF/projects/moray/.worktrees/intellij-chrome` on `claude/intellij-chrome`. Make one commit per task. Every commit message ends with:
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
  Do not push.
- Source hygiene: no raw control, private-use or unpaired surrogate characters in Java source. Write glyphs as Java escapes (`"⌘"`). Run the AGENTS.md Python check before each commit.
- Retro chrome is unchanged. `RetroChromeTest`, `RetroTabsTest` and `RetroThemeTest` must pass without edits.
- SDK and testkit stay JDK-only. SDK types appear in app production code only inside `dev.jasper.app.plugins`. Plugins compile against the SDK only.
- The new SDK names are exactly `SPLIT`, `ZOOM`, `TERMINAL` and `SERVER`, each `@since 0.7.5`. `JasperSdk.VERSION = "0.7.5"`.
- Retro rasters for the new names are byte-identical copies of existing repository PNGs:
  - SPLIT: GNOME2 `columns-2` (`stock_table-split`);
  - ZOOM: GNOME2 `maximize` (`view-fullscreen`);
  - TERMINAL: GNOME2 `command` (`gtk-execute`);
  - SERVER: OldGNOME2 `NETWORK`.
- IntelliJ artwork comes from `JetBrains/intellij-community` at commit `f7377708b654b73b206da40bb382ecb4d44e8f12` (Apache-2.0). Do not vendor product logos.
- **Downloading the IntelliJ SVGs (Task 1, Step 3) needs the user's explicit approval in chat before it runs.**
- All chrome runs on the EDT. Use `UIScale.scale(...)` for every pixel constant.

## Review Focus

1. **Hostile or huge tab titles** (`<html>…`, 1000-character names) must render literally and stay bounded in the tab, the tooltip, the ▾ tab list and the title row. Covered in Task 5 by the tab-list `html.disable`, bounds and title minimum-size assertions.
2. **Live dark/light switches** must re-filter IntelliJ icons without reloading them and recolour the tab underline. Covered in Task 1 (palette probe), Task 2 (dark host icon) and Task 5 (underline after `selectTheme`).
3. **Plugin toolbar items coming and going** must never leave a dangling separator in modern. Covered in Task 6 (the plugin separator is hidden when the plugin group is empty and after the last item is removed).
4. **Live key rebinding** must update toolbar tooltips and tab tooltips immediately. Covered in Task 5 (tab tooltip) and Task 6 (toolbar tooltip).
5. **Provided sessions with and without `SessionSpec.icon`**, including a lone local tab, must always show a 16 px tab icon. Covered in Task 4.

---

### Task 1: Vendor the IntelliJ icon set and prove FlatLaf's palette remapping

**Files:**
- Create: `jasper-app/src/main/resources/dev/jasper/app/icons/intellij/` (29 SVGs plus `_dark` variants, `assets.tsv`, `LICENSE.txt`, `NOTICE.txt`, `SOURCE.md`)
- Create: `tools/icons/fetch-intellij-icons.py`
- Create: `jasper-app/src/test/resources/dev/jasper/app/icons/palette-probe.svg`
- Test: `jasper-app/src/test/java/dev/jasper/app/platform/IntellijIconsTest.java`

**Interfaces:**
- Produces: the resource directory `dev/jasper/app/icons/intellij/<name>.svg` for these names: `add`, `moveToWindow`, `splitVertically`, `expandComponent`, `find`, `gearPlain`, `refresh`, `execute`, `history`, `bookmark`, `close`, `closeHovered`, `exit`, `arrowDown`, `console`, `server`, `previousOccurence`, `nextOccurence`, `matchCase`, `regex`, `searchWithHistory`, `folder`, `menu-saveall`, `copy`, `menu-paste`, `remove`, `web`, `information` and `help`.
- Produces: the test resource `dev/jasper/app/icons/palette-probe.svg`, a solid `#389FD6` square that Task 2 reuses.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/resources/dev/jasper/app/icons/palette-probe.svg`:

```xml
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16">
  <rect width="16" height="16" fill="#389FD6"/>
</svg>
```

Create `jasper-app/src/test/java/dev/jasper/app/platform/IntellijIconsTest.java`:

```java
package dev.jasper.app.platform;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ThemeStyle;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class IntellijIconsTest {
    private static final String BASE = "dev/jasper/app/icons/intellij/";
    static final List<String> REQUIRED = List.of("add", "moveToWindow", "splitVertically", "expandComponent", "find",
        "gearPlain", "refresh", "execute", "history", "bookmark", "close", "closeHovered", "exit", "arrowDown", "console",
        "server", "previousOccurence", "nextOccurence", "matchCase", "regex", "searchWithHistory", "folder",
        "menu-saveall", "copy", "menu-paste", "remove", "web", "information", "help");

    @Test void manifestPinsEveryBundledSvgToItsUpstreamPathAndHash() throws Exception {
        List<String> rows;
        try (var in = getClass().getResourceAsStream("/" + BASE + "assets.tsv")) {
            assertThat(in).as("manifest").isNotNull();
            rows = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().skip(1).toList();
        }
        Set<String> listed = new HashSet<>();
        for (String row : rows) {
            String[] fields = row.split("\t");
            assertThat(fields).as(row).hasSize(3);
            assertThat(fields[1]).startsWith("platform/icons/src/").endsWith(".svg");
            listed.add(fields[0]);
            try (var asset = getClass().getResourceAsStream("/" + BASE + fields[0])) {
                assertThat(asset).as(fields[0]).isNotNull();
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(asset.readAllBytes()));
                assertThat(hash).as(fields[0]).isEqualTo(fields[2]);
            }
        }
        for (String name : REQUIRED) assertThat(listed).contains(name + ".svg");
        for (String file : new String[]{"LICENSE.txt", "NOTICE.txt", "SOURCE.md"})
            try (var in = getClass().getResourceAsStream("/" + BASE + file)) { assertThat(in).as(file).isNotNull(); }
    }

    @Test void everyRequiredIconParsesAndPaintsAtSixteenPixels() {
        new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
        try {
            for (String name : REQUIRED) {
                var icon = new FlatSVGIcon(BASE + name + ".svg", 16, 16);
                assertThat(icon.hasFound()).as(name).isTrue();
                assertThat(icon.getIconWidth()).isEqualTo(16);
                int[] pixels = pixels(icon);
                assertThat(java.util.Arrays.stream(pixels).anyMatch(pixel -> (pixel >>> 24) != 0)).as(name + " has ink").isTrue();
            }
        } finally { new ThemeController(); }
    }

    @Test void flatLafRemapsIntellijPaletteColoursForTheRunningTheme() {
        var themes = new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
        try {
            var probe = new FlatSVGIcon("dev/jasper/app/icons/palette-probe.svg", 16, 16);
            assertThat(probe.hasFound()).isTrue();
            int light = centre(probe);
            assertThat(light).isEqualTo(UIManager.getColor("Actions.Blue").getRGB());
            themes.selectAppearance(Appearance.DARK);
            int dark = centre(probe);
            assertThat(dark).as("the same icon instance re-filters on a live theme change")
                .isEqualTo(UIManager.getColor("Actions.Blue").getRGB());
            assertThat(dark).isNotEqualTo(light);
        } finally { new ThemeController(); }
    }

    static int centre(Icon icon) { return pixels(icon)[8 * 16 + 8]; }

    static int[] pixels(Icon icon) {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { icon.paintIcon(new JLabel(), g, 0, 0); } finally { g.dispose(); }
        return image.getRGB(0, 0, 16, 16, null, 0, 16);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.platform.IntellijIconsTest`

Expected:
- `manifestPinsEveryBundledSvgToItsUpstreamPathAndHash` and `everyRequiredIconParsesAndPaintsAtSixteenPixels` fail: there is no manifest and no icons yet.
- `flatLafRemapsIntellijPaletteColoursForTheRunningTheme` **passes**. This is the spec's gate: if it fails, stop and report to the user, because the palette design depends on it.

- [ ] **Step 3: Ask the user, then fetch the artwork**

Ask in chat:

> Task 1 downloads 29 IntelliJ classic-UI SVGs (plus 19 `_dark` variants), about 60 KB in total, and `LICENSE.txt` and `NOTICE.txt` from `raw.githubusercontent.com/JetBrains/intellij-community` at commit `f7377708…`, which is Apache-2.0. May I run the fetch script?

Wait for a clear yes before continuing. Then create `tools/icons/fetch-intellij-icons.py`:

```python
#!/usr/bin/env python3
"""Vendors Jasper's IntelliJ classic-UI icons at a pinned intellij-community commit.

Run from the repository root. Rewrites icons/intellij/*.svg, assets.tsv, LICENSE.txt and NOTICE.txt.
A missing file (HTTP 404) fails the run: update ICONS instead of skipping silently.
"""
import hashlib
import pathlib
import urllib.request

SHA = "f7377708b654b73b206da40bb382ecb4d44e8f12"
BASE = f"https://raw.githubusercontent.com/JetBrains/intellij-community/{SHA}/"
OUT = pathlib.Path("jasper-app/src/main/resources/dev/jasper/app/icons/intellij")
# local name: (upstream path without ".svg", upstream ships a "_dark" variant)
ICONS = {
    "add": ("platform/icons/src/general/add", True),
    "moveToWindow": ("platform/icons/src/actions/moveToWindow", True),
    "splitVertically": ("platform/icons/src/actions/splitVertically", True),
    "expandComponent": ("platform/icons/src/general/expandComponent", False),
    "find": ("platform/icons/src/actions/find", True),
    "gearPlain": ("platform/icons/src/general/gearPlain", True),
    "refresh": ("platform/icons/src/actions/refresh", True),
    "execute": ("platform/icons/src/actions/execute", True),
    "history": ("platform/icons/src/vcs/history", True),
    "bookmark": ("platform/icons/src/nodes/bookmark", True),
    "close": ("platform/icons/src/actions/close", False),
    "closeHovered": ("platform/icons/src/actions/closeHovered", False),
    "exit": ("platform/icons/src/actions/exit", True),
    "arrowDown": ("platform/icons/src/general/arrowDown", True),
    "console": ("platform/icons/src/debugger/console", True),
    "server": ("platform/icons/src/webreferences/server", True),
    "previousOccurence": ("platform/icons/src/actions/previousOccurence", True),
    "nextOccurence": ("platform/icons/src/actions/nextOccurence", True),
    "matchCase": ("platform/icons/src/actions/matchCase", False),
    "regex": ("platform/icons/src/actions/regex", False),
    "searchWithHistory": ("platform/icons/src/actions/searchWithHistory", False),
    "folder": ("platform/icons/src/nodes/folder", False),
    "menu-saveall": ("platform/icons/src/actions/menu-saveall", True),
    "copy": ("platform/icons/src/actions/copy", True),
    "menu-paste": ("platform/icons/src/actions/menu-paste", True),
    "remove": ("platform/icons/src/general/remove", True),
    "web": ("platform/icons/src/general/web", True),
    "information": ("platform/icons/src/general/information", False),
    "help": ("platform/icons/src/actions/help", True),
}


def fetch(path):
    with urllib.request.urlopen(BASE + path) as response:
        return response.read()


OUT.mkdir(parents=True, exist_ok=True)
rows = ["resource\tsource\tsha256"]
for local, (upstream, dark) in sorted(ICONS.items()):
    for suffix in ([".svg", "_dark.svg"] if dark else [".svg"]):
        data = fetch(upstream + suffix)
        (OUT / (local + suffix)).write_bytes(data)
        rows.append(f"{local}{suffix}\t{upstream}{suffix}\t{hashlib.sha256(data).hexdigest()}")
(OUT / "assets.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
(OUT / "LICENSE.txt").write_bytes(fetch("LICENSE.txt"))
(OUT / "NOTICE.txt").write_bytes(fetch("NOTICE.txt"))
print(f"{len(rows) - 1} files from {SHA}")
```

Run: `python3 tools/icons/fetch-intellij-icons.py`
Expected: `48 files from f7377708b654b73b206da40bb382ecb4d44e8f12`

- [ ] **Step 4: Write the provenance note**

Create `jasper-app/src/main/resources/dev/jasper/app/icons/intellij/SOURCE.md`:

```markdown
# IntelliJ Platform classic-UI icons

Source: https://github.com/JetBrains/intellij-community, directory `platform/icons/src`.
Commit: f7377708b654b73b206da40bb382ecb4d44e8f12, fetched 2026-09-24 with
`tools/icons/fetch-intellij-icons.py`.
License: Apache License 2.0 (LICENSE.txt); upstream notice in NOTICE.txt.

Files are byte-identical copies. assets.tsv records each file's upstream path and SHA-256.
`_dark.svg` siblings are upstream dark variants; FlatLaf's FlatSVGIcon selects them under a dark
look and feel. Other icons use IntelliJ's light palette, which FlatLaf's global colour filter maps
to the running theme's Actions.* and Objects.* colours.

No JetBrains product logos are included. Refresh by editing ICONS in the script and rerunning it
from the repository root.
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.platform.IntellijIconsTest`
Expected: all 3 tests pass.

- [ ] **Step 6: Commit**

```bash
git add tools/icons jasper-app/src/main/resources/dev/jasper/app/icons/intellij jasper-app/src/test/resources/dev/jasper/app/icons/palette-probe.svg jasper-app/src/test/java/dev/jasper/app/platform/IntellijIconsTest.java
git commit -m "feat(icons): vendor IntelliJ classic-UI icons with a pinned manifest

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: Modern host icons use IntelliJ artwork; plugin SVGs render as authored

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/AppIcons.java` (whole file)
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/NamedIcons.java` (whole file)
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java` (`appearance()`, `icon(String)`)
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Appearance.java` (Javadoc of both SVG methods)
- Delete: the Tabler root icons `jasper-app/src/main/resources/dev/jasper/app/icons/{square-plus,app-window,columns-2,maximize,search,settings,refresh,command,history,bookmark,exit}.svg`. Keep `icons/SOURCE.txt` and `icons/LICENSE.txt` for now: `title/SOURCE.txt` points at that licence until Task 5 removes `title/`.
- Modify tests: `AppIconsTest.java` (rewrite); rename `AppIconsThemedTest.java` to `AppIconsPluginTest.java` (rewrite); `HostedUiTest.java` and `WindowChromeContributionsTest.java` (resource path)

**Interfaces:**
- Consumes: `dev/jasper/app/icons/intellij/*.svg` and `palette-probe.svg` (Task 1).
- Produces:
  - `public static javax.swing.Icon AppIcons.chrome(String intellijName)`: 16 px untinted IntelliJ artwork. It throws `IllegalArgumentException` for an unknown name.
  - `public static javax.swing.Icon AppIcons.plugin(ClassLoader loader, String svgResourcePath)`: untinted. It throws `NullPointerException` for a null loader and `IllegalArgumentException` for a missing path.
  - `AppIcons.themed(...)` is removed.
  - `static boolean NamedIcons.tinted(String name)`.

- [ ] **Step 1: Write the failing tests**

Replace `jasper-app/src/test/java/dev/jasper/app/platform/AppIconsTest.java`:

```java
package dev.jasper.app.platform;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ThemeStyle;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.*;

class AppIconsTest {
    private static final String[] ICONS = {"square-plus", "app-window", "columns-2", "maximize", "search", "settings",
        "refresh", "command", "history", "bookmark", "close", "exit"};

    @Test void modernApplicationIconsAreUntintedIntellijArtwork() throws Exception {
        edt(() -> {
            new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
            try {
                for (String name : ICONS) {
                    FlatSVGIcon icon = (FlatSVGIcon) AppIcons.icon(name);
                    assertThat(icon.hasFound()).as(name).isTrue();
                    assertThat(icon.getName()).as(name).startsWith("dev/jasper/app/icons/intellij/");
                    assertThat(icon.getIconWidth()).isEqualTo(16);
                    assertThat(icon.getColorFilter()).as(name + " keeps its authored colours").isNull();
                }
                assertThat(Arrays.stream(IntellijIconsTest.pixels(AppIcons.icon("search")))
                    .anyMatch(pixel -> pixel == 0xff6e6e6e)).as("IntelliJ light grey").isTrue();
            } finally { new ThemeController(); }
        });
    }

    @Test void theSameHostIconFollowsALiveDarkSwitch() throws Exception {
        edt(() -> {
            var themes = new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
            try {
                var icon = AppIcons.icon("search");
                int[] light = IntellijIconsTest.pixels(icon);
                themes.selectAppearance(Appearance.DARK);
                int[] dark = IntellijIconsTest.pixels(icon);
                assertThat(dark).isNotEqualTo(light);
                assertThat(Arrays.stream(dark).anyMatch(pixel -> pixel == 0xff6e6e6e)).isFalse();
            } finally { new ThemeController(); }
        });
    }

    @Test void chromeResolvesIntellijNamesAndRejectsUnknownOnes() throws Exception {
        edt(() -> {
            assertThat(AppIcons.chrome("closeHovered").getIconWidth()).isEqualTo(16);
            assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.chrome("absent")).withMessageContaining("absent");
        });
    }
}
```

Delete `AppIconsThemedTest.java` and create `jasper-app/src/test/java/dev/jasper/app/platform/AppIconsPluginTest.java`:

```java
package dev.jasper.app.platform;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ThemeStyle;
import dev.jasper.app.testsupport.EdtTestExtension;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class AppIconsPluginTest {
    @Test void pluginSvgRendersAsAuthoredWithPaletteRemappingAndRejectsBadArguments() {
        var themes = new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
        try {
            var loader = getClass().getClassLoader();
            var icon = AppIcons.plugin(loader, "dev/jasper/app/icons/palette-probe.svg");
            assertThat(icon.getIconWidth()).isEqualTo(16);
            assertThat(IntellijIconsTest.centre(icon)).as("not tinted to the chrome foreground").isEqualTo(0xff389fd6);
            themes.selectAppearance(Appearance.DARK);
            assertThat(IntellijIconsTest.centre(icon)).isEqualTo(UIManager.getColor("Actions.Blue").getRGB());
            assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.plugin(loader, "dev/jasper/app/icons/absent.svg"))
                .withMessageContaining("absent.svg");
            assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.plugin(loader, null));
            assertThatNullPointerException().isThrownBy(() -> AppIcons.plugin(null, "x.svg"));
        } finally { new ThemeController(); }
    }
}
```

In `HostedUiTest.java` and `WindowChromeContributionsTest.java`, replace every occurrence of `"dev/jasper/app/icons/search.svg"` with `"dev/jasper/app/icons/intellij/find.svg"`. That's three occurrences in total; confirm with `grep -rn 'icons/search.svg' jasper-app/src/test`, which afterwards must print nothing.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.AppIcons*'`
Expected: compilation fails, because `AppIcons.chrome` and `AppIcons.plugin` do not exist.

- [ ] **Step 3: Implement**

Replace `jasper-app/src/main/java/dev/jasper/app/platform/AppIcons.java`:

```java
package dev.jasper.app.platform;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import java.util.Map;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * Bundled IntelliJ, Tabler, GNOME 2 and Tango icons; no network access is needed to render chrome.
 * Modern SVGs render as authored: FlatLaf's global colour filter remaps IntelliJ palette colours
 * for the running theme at paint time.
 */
public final class AppIcons {
    private static final String INTELLIJ = "dev/jasper/app/icons/intellij/";
    /** Modern artwork for the application's own chrome names. */
    private static final Map<String, String> MODERN = Map.ofEntries(
        Map.entry("square-plus", "add"), Map.entry("app-window", "moveToWindow"),
        Map.entry("columns-2", "splitVertically"), Map.entry("maximize", "expandComponent"),
        Map.entry("search", "find"), Map.entry("settings", "gearPlain"), Map.entry("refresh", "refresh"),
        Map.entry("command", "execute"), Map.entry("history", "history"), Map.entry("bookmark", "bookmark"),
        Map.entry("close", "close"), Map.entry("exit", "exit"));
    private AppIcons() {}

    public static Icon icon(String name) {
        if (!GnomeIcons.NAMES.contains(name))
            throw new IllegalArgumentException("Unknown application icon: " + name);
        if (SwingAppearance.retro()) return GnomeIcons.icon(name);
        return chrome(MODERN.get(name));
    }

    /** Application toolbar artwork: large classic icons in retro, regular modern icons otherwise. */
    public static Icon toolbarIcon(String name) {
        return SwingAppearance.retro() ? GnomeIcons.icon(name, 28) : icon(name);
    }

    /** Modern chrome artwork by IntelliJ file name, for example {@code "closeHovered"}. */
    public static Icon chrome(String intellijName) {
        return svg(AppIcons.class.getClassLoader(), INTELLIJ + intellijName + ".svg");
    }

    /** A plugin's 16-pixel SVG drawn as authored; IntelliJ light-palette colours follow the theme. */
    public static Icon plugin(ClassLoader loader, String svgResourcePath) {
        Objects.requireNonNull(loader, "loader");
        return svg(loader, svgResourcePath);
    }

    /** Host-owned semantic artwork, independent of any plugin's resource loader. */
    public static Icon named(String name) {
        String resource = NamedIcons.resource(name);
        if (SwingAppearance.retro()) return new SkinIcon(name);
        return NamedIcons.tinted(name) ? tinted(resource) : svg(AppIcons.class.getClassLoader(), resource);
    }

    /** Selects plugin SVG or explicit OldGNOME2 artwork for the running style. */
    public static Icon skin(ClassLoader loader, String modernSvgResourcePath, String retroName) {
        Objects.requireNonNull(loader, "loader");
        if (retroName == null || !OldGnomeCatalog.NAMES.contains(retroName))
            throw new IllegalArgumentException("Unknown OldGNOME2 icon: " + retroName);
        if (modernSvgResourcePath == null || loader.getResource(modernSvgResourcePath) == null)
            throw new IllegalArgumentException("No such icon resource: " + modernSvgResourcePath);
        return SwingAppearance.retro() ? new SkinIcon(retroName) : plugin(loader, modernSvgResourcePath);
    }

    /** Sizes only managed retro icons; shared compact icons and external artwork remain untouched. */
    public static Icon forToolbar(Icon icon) {
        return icon instanceof SkinIcon managed ? managed.toolbar() : icon;
    }

    private static FlatSVGIcon svg(ClassLoader loader, String path) {
        if (path == null || loader.getResource(path) == null)
            throw new IllegalArgumentException("No such icon resource: " + path);
        return new FlatSVGIcon(path, 16, 16, loader);
    }

    /** Monochrome Tabler fallback for names IntelliJ has no artwork for; it follows the chrome foreground. */
    private static Icon tinted(String path) {
        FlatSVGIcon icon = svg(AppIcons.class.getClassLoader(), path);
        return icon.setColorFilter(new FlatSVGIcon.ColorFilter(source -> themed("Jasper.chromeForeground", source)));
    }

    private static Color themed(String key, Color source) {
        Color target = UIManager.getColor(key);
        if (target == null) return source;
        return new Color(target.getRed(), target.getGreen(), target.getBlue(), source.getAlpha());
    }
}
```

Replace `jasper-app/src/main/java/dev/jasper/app/platform/NamedIcons.java`:

```java
package dev.jasper.app.platform;

import java.util.Map;
import java.util.Set;

/**
 * App-owned modern resources for semantic SDK icon names: IntelliJ artwork, or the tinted Tabler
 * outline where IntelliJ has no equivalent. LOCK and UNLOCK stay a Tabler pair shared with Vault.
 */
final class NamedIcons {
    private static final String INTELLIJ = "dev/jasper/app/icons/intellij/";
    private static final String STANDARD = "dev/jasper/app/icons/standard/";
    private static final Map<String, String> MODERN = Map.ofEntries(
        Map.entry("ADD", INTELLIJ + "add.svg"),
        Map.entry("BOOKMARK", INTELLIJ + "bookmark.svg"),
        Map.entry("CLOSE", INTELLIJ + "close.svg"),
        Map.entry("CONNECT", STANDARD + "CONNECT.svg"),
        Map.entry("COPY", INTELLIJ + "copy.svg"),
        Map.entry("DELETE", STANDARD + "DELETE.svg"),
        Map.entry("DISCONNECT", STANDARD + "DISCONNECT.svg"),
        Map.entry("EXECUTE", INTELLIJ + "execute.svg"),
        Map.entry("FOLDER", INTELLIJ + "folder.svg"),
        Map.entry("HELP", INTELLIJ + "help.svg"),
        Map.entry("HISTORY", INTELLIJ + "history.svg"),
        Map.entry("INFO", INTELLIJ + "information.svg"),
        Map.entry("KEY", STANDARD + "KEY.svg"),
        Map.entry("LOCK", STANDARD + "LOCK.svg"),
        Map.entry("NETWORK", INTELLIJ + "web.svg"),
        Map.entry("PASTE", INTELLIJ + "menu-paste.svg"),
        Map.entry("REFRESH", INTELLIJ + "refresh.svg"),
        Map.entry("REMOVE", INTELLIJ + "remove.svg"),
        Map.entry("SAVE", INTELLIJ + "menu-saveall.svg"),
        Map.entry("SEARCH", INTELLIJ + "find.svg"),
        Map.entry("SETTINGS", INTELLIJ + "gearPlain.svg"),
        Map.entry("UNLOCK", STANDARD + "UNLOCK.svg"));
    static final Set<String> NAMES = MODERN.keySet();
    private NamedIcons() { }

    static String resource(String name) {
        if (name == null || !NAMES.contains(name)) throw new IllegalArgumentException("Unknown icon name: " + name);
        return MODERN.get(name);
    }

    /** True for the Tabler fallback, which is recoloured to the chrome foreground. */
    static boolean tinted(String name) { return resource(name).startsWith(STANDARD); }
}
```

In `HostedUi.appearance()`, replace the `icon(String svgResourcePath)` override:

```java
            @Override public Icon icon(String svgResourcePath) { return AppIcons.plugin(loader, svgResourcePath); }
```

In `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Appearance.java`, replace the Javadoc of `Icon icon(String svgResourcePath);` with:

```java
    /**
     * A 16 by 16 icon from an SVG in the plugin's own jars, drawn with its authored colours. Colours
     * from the IntelliJ light icon palette (grey {@code #6E6E6E}, blue {@code #389FD6}, green
     * {@code #59A869}, red {@code #DB5860}, yellow {@code #EDA200}) follow dark and light themes
     * without reloading; other colours are drawn as they are. Since 0.7.5 the icon is no longer
     * recoloured to the chrome foreground.
     *
     * @param svgResourcePath classpath path without a leading slash, for example {@code dev/example/tool/run.svg}
     * @return the icon
     * @throws IllegalArgumentException when the plugin's jars hold no such resource
     */
```

Replace the Javadoc of `icon(String modernSvgResourcePath, OldGnomeIcon retroIcon)` with:

```java
    /**
     * A 16 by 16 icon using the plugin's SVG in modern mode, drawn as {@link #icon(String)} draws it,
     * and bundled OldGNOME2 artwork in retro mode. Jasper adapts it to 28 pixels in retro host
     * toolbars without resizing this shared icon. Both arguments are validated in both skins.
     * Older implementations inherit the modern-only fallback.
     *
     * @param modernSvgResourcePath SVG classpath path in the plugin's jars, without a leading slash
     * @param retroIcon bundled retro artwork
     * @return the icon for the running skin
     * @throws NullPointerException if retroIcon is null
     * @throws IllegalArgumentException if the SVG path is null or its resource is absent
     * @since 0.7.4
     */
```

Delete the Tabler root artwork:

```bash
cd jasper-app/src/main/resources/dev/jasper/app/icons
git rm square-plus.svg app-window.svg columns-2.svg maximize.svg search.svg settings.svg refresh.svg command.svg history.svg bookmark.svg exit.svg
cd -
grep -rn 'app/icons/[a-z-]*\.svg' jasper-app/src plugins | grep -v 'icons/intellij/\|icons/standard/\|icons/title/\|palette-probe'
```

Expected: the grep prints nothing.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.*' --tests dev.jasper.app.plugins.HostedUiTest --tests dev.jasper.app.workspace.WindowChromeContributionsTest --tests dev.jasper.app.plugins.BundledSamplePluginTest`
Expected: pass. `NamedIconsTest` still passes: `LOCK` is still tinted Tabler, so it still recolours on a theme change.

- [ ] **Step 5: Commit** (run the AGENTS.md character check first)

```bash
git add -A jasper-app/src jasper-sdk/src/main/java/dev/jasper/sdk/ui/Appearance.java
git commit -m "feat(icons): draw modern host and plugin SVGs as authored IntelliJ artwork

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: SDK 0.7.5 adds SPLIT, ZOOM, TERMINAL and SERVER in both skins

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/IconName.java`, `OldGnomeIcon.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/OldGnomeCatalog.java` and `NamedIcons.java`
- Create: `jasper-app/src/main/resources/dev/jasper/app/icons/oldgnome-sdk/{16,24}/{SPLIT,ZOOM,TERMINAL}.png` and `{16,24,32,48}/SERVER.png` (copies)
- Modify: `oldgnome-sdk/assets.tsv`, `oldgnome-sdk/NOTICE.md`
- Test: create `jasper-sdk/src/test/java/dev/jasper/sdk/ui/IconCatalogTest.java`; modify `jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java:64`, `NamedIconsTest.java:25` and `OldGnomeCatalogTest.java:21`

**Interfaces:**
- Consumes: `NamedIcons` map and `OldGnomeCatalog.SOURCES` (Task 2).
- Produces: `IconName.SPLIT`, `IconName.ZOOM`, `IconName.TERMINAL` and `IconName.SERVER`, the same `OldGnomeIcon` constants, and `JasperSdk.VERSION == "0.7.5"`. `AppIcons.named("TERMINAL")` and `AppIcons.named("SERVER")` resolve in both skins.

- [ ] **Step 1: Write the failing tests**

Create `jasper-sdk/src/test/java/dev/jasper/sdk/ui/IconCatalogTest.java`:

```java
package dev.jasper.sdk.ui;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IconCatalogTest {
    @Test void retroChoicesMirrorEverySemanticNameIncludingTheChromeNames() {
        assertThat(Arrays.stream(OldGnomeIcon.values()).map(Enum::name))
            .containsExactlyElementsOf(Arrays.stream(IconName.values()).map(Enum::name).toList());
        assertThat(IconName.values()).hasSize(26)
            .contains(IconName.SPLIT, IconName.ZOOM, IconName.TERMINAL, IconName.SERVER);
    }
}
```

In `PaletteValuesTest.java` line 64, change `isEqualTo("0.7.4")` to `isEqualTo("0.7.5")`. In `NamedIconsTest.java` line 25, change `assertThat(NamedIcons.NAMES).hasSize(22);` to `hasSize(26)`. Leave line 68, the 22-row `standard/assets.tsv` manifest, unchanged. In `OldGnomeCatalogTest.java` line 21, change `hasSize(22)` to `hasSize(26)`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests dev.jasper.sdk.ui.IconCatalogTest --tests dev.jasper.sdk.palette.PaletteValuesTest`
Expected: compilation fails, because `IconName.SPLIT` does not exist.

- [ ] **Step 3: Implement the SDK constants and the version**

In `IconName.java`, change `CLOSE;` to `CLOSE,` and append:

```java
    /**
     * Split the current pane.
     * @since 0.7.5
     */
    SPLIT,
    /**
     * Enlarge the current pane to fill its tab.
     * @since 0.7.5
     */
    ZOOM,
    /**
     * A local terminal session.
     * @since 0.7.5
     */
    TERMINAL,
    /**
     * A remote host or server session.
     * @since 0.7.5
     */
    SERVER;
```

Make the same edit in `OldGnomeIcon.java`, with the same four constants and Javadoc. In `JasperSdk.java`, set `public static final String VERSION = "0.7.5";`.

- [ ] **Step 4: Copy the retro rasters and record them**

Run from the repository root:

```bash
python3 - <<'PY'
import hashlib, pathlib, shutil
icons = pathlib.Path("jasper-app/src/main/resources/dev/jasper/app/icons")
sdk = icons / "oldgnome-sdk"
def source_of(manifest, resource):
    for line in (manifest).read_text().splitlines()[1:]:
        fields = line.split("\t")
        if fields[0] == resource: return fields[1]
    raise SystemExit(f"{resource} missing from {manifest}")
copies = []  # (target, source file, original collection path)
for name, gnome in (("SPLIT", "columns-2"), ("ZOOM", "maximize"), ("TERMINAL", "command")):
    for size in (16, 24):
        copies.append((f"{size}/{name}.png", icons / "gnome2" / str(size) / f"{gnome}.png",
                       source_of(icons / "gnome2" / "assets.tsv", f"{size}/{gnome}.png")))
for size in (16, 24, 32, 48):
    copies.append((f"{size}/SERVER.png", sdk / str(size) / "NETWORK.png", source_of(sdk / "assets.tsv", f"{size}/NETWORK.png")))
rows = []
for target, source, original in copies:
    shutil.copyfile(source, sdk / target)
    rows.append(f"{target}\t{original}\t{hashlib.sha256((sdk / target).read_bytes()).hexdigest()}")
with open(sdk / "assets.tsv", "a", encoding="utf-8") as manifest:
    manifest.write("\n".join(rows) + "\n")
print(len(rows), "rasters")
PY
tail -c 1 jasper-app/src/main/resources/dev/jasper/app/icons/oldgnome-sdk/assets.tsv | xxd | grep -q 0a && echo "ends with newline"
```

Expected: `10 rasters` and `ends with newline`. If the manifest did not end with a newline before the append, fix the joined line by hand so that each row stands alone.

Append this to `oldgnome-sdk/NOTICE.md`:

```markdown

## SDK 0.7.5 chrome names

SPLIT, ZOOM and TERMINAL are byte-identical copies of this collection's `stock_table-split`,
`view-fullscreen` and `gtk-execute` artwork already bundled in `../gnome2/`; SERVER copies this
directory's NETWORK artwork. They share this notice and license. assets.tsv records each original
collection path and hash.
```

- [ ] **Step 5: Map the names in the app**

In `OldGnomeCatalog.SOURCES`, change the `CLOSE` entry's closing `)));` to `)),` and append:

```java
        Map.entry("SPLIT", java.util.List.of(16, 24)),
        Map.entry("ZOOM", java.util.List.of(16, 24)),
        Map.entry("TERMINAL", java.util.List.of(16, 24)),
        Map.entry("SERVER", java.util.List.of(16, 24, 32, 48)));
```

In `NamedIcons.MODERN`, add after the `SETTINGS` entry:

```java
        Map.entry("SERVER", INTELLIJ + "server.svg"),
        Map.entry("SPLIT", INTELLIJ + "splitVertically.svg"),
        Map.entry("TERMINAL", INTELLIJ + "console.svg"),
        Map.entry("ZOOM", INTELLIJ + "expandComponent.svg"),
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests 'dev.jasper.app.platform.*' --tests dev.jasper.app.plugins.HostedUiTest`

Expected: pass. `HostedUiTest.selectsEverySdkCatalogChoiceAndValidatesBothArguments` now also compares the four new retro PNGs pixel for pixel, and `namedIconsBelongToHostRatherThanPluginLoader` covers all 26 names in both skins. `FakeSkinIconTest` iterates the enums and passes unchanged.

- [ ] **Step 7: Commit**

```bash
git add -A jasper-sdk jasper-app/src
git commit -m "feat(sdk): add SPLIT, ZOOM, TERMINAL and SERVER icons in SDK 0.7.5

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Provided sessions carry a tab icon

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/terminals/SessionRequest.java` and `package-info.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedSessions.java:36-43`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java` (accessor) and `TerminalTab.java` (accessor)
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/SessionSpec.java` (Javadoc of `icon`)
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java` (lines 132 and 252) and `plugins/remote/src/main/resources/plugin.toml` (sdk range)
- Test: create `jasper-app/src/test/java/dev/jasper/app/workspace/TerminalTabIconTest.java`; add a test to `jasper-app/src/test/java/dev/jasper/app/plugins/HostedSessionsTest.java`

**Interfaces:**
- Consumes: `AppIcons.named("TERMINAL")` and `IconName.SERVER` (Task 3).
- Produces:
  - `record SessionRequest(String providerId, String title, boolean closeOnExit, Consumer<SessionAttempt> connector, Executor cleanup, javax.swing.Icon icon)`, where `icon` may be null, plus a five-argument convenience constructor;
  - `Icon TerminalPane.providedIcon()`, which may be null;
  - `Icon TerminalTab.icon()`, which is never null.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/TerminalTabIconTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.terminals.SessionRequest;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class TerminalTabIconTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void localTabsShowTheTerminalIconAndProvidedSessionsShowTheirOwn() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            Icon local = owner.currentTab().icon();
            assertThat(local).isNotNull();
            assertThat(local.getIconWidth()).isEqualTo(16);
            assertThat(owner.currentTab().icon()).as("cached per tab").isSameAs(local);
            var provided = new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));
            var remote = owner.openTab(HOME, new SessionRequest("dev.x", "remote", false, attempt -> {}, Runnable::run, provided));
            assertThat(remote.icon()).isSameAs(provided);
            var plain = owner.openTab(HOME, new SessionRequest("dev.x", "plain", false, attempt -> {}, Runnable::run));
            assertThat(plain.icon()).isNotSameAs(provided);
            assertThat(plain.icon().getIconWidth()).isEqualTo(16);
        });
    }
}
```

Append to `HostedSessionsTest`:

```java
    @Test void theSpecIconBecomesTheRequestIconAndAnAbsentIconMeansTheHostDefault() {
        terminals(Capabilities.SESSION_PROVIDE);
        var icon = new javax.swing.ImageIcon();
        assertThat(sessions.request(new SessionSpec("build-host", Optional.of(icon), ExitPolicy.KEEP_OPEN, pendings::add)).icon())
            .isSameAs(icon);
        assertThat(sessions.request(SessionSpec.of("build-host", pendings::add)).icon()).isNull();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.workspace.TerminalTabIconTest --tests dev.jasper.app.plugins.HostedSessionsTest`
Expected: compilation fails. There is no six-argument `SessionRequest` constructor, no `TerminalTab.icon()` and no `SessionRequest.icon()`.

- [ ] **Step 3: Implement**

Replace the record in `SessionRequest.java`, keeping the file's existing Javadoc and adding the new `@param`:

```java
package dev.jasper.app.terminals;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.swing.Icon;

/**
 * A pane whose session somebody else provides. The pane appears first; {@code connector} is called on the EDT
 * once per attempt, first connect and every reconnect alike, and must not block. {@code cleanup} runs the
 * closes of connections and the cancellation handlers, never on the EDT.
 *
 * @param providerId who provides the session, for display and diagnostics
 * @param title the pane's title until the program sets one
 * @param closeOnExit close the pane when the session ends instead of offering Reconnect
 * @param connector starts one connection attempt
 * @param cleanup where closes and cancellation handlers run
 * @param icon the provider's tab icon, or {@code null} for the host terminal icon; carried, never painted here
 */
public record SessionRequest(String providerId, String title, boolean closeOnExit, Consumer<SessionAttempt> connector,
                             Executor cleanup, Icon icon) {
    public SessionRequest {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(connector, "connector");
        Objects.requireNonNull(cleanup, "cleanup");
    }

    /** A request whose tab shows the host terminal icon. */
    public SessionRequest(String providerId, String title, boolean closeOnExit, Consumer<SessionAttempt> connector,
                          Executor cleanup) {
        this(providerId, title, closeOnExit, connector, cleanup, null);
    }
}
```

In `terminals/package-info.java`, change the first sentence's clause "for features that must not hold Swing objects" to "for features that must not hold Swing objects (the one exception is the opaque tab icon a {@link dev.jasper.app.terminals.SessionRequest} carries, which this package never paints or inspects)".

In `HostedSessions.request`, pass the icon as the last argument:

```java
    SessionRequest request(SessionSpec spec) {
        Objects.requireNonNull(spec, "spec");
        Executor contained = task -> cleanup.execute(() -> containment.run(pluginId, "session cleanup", task));
        return new SessionRequest(pluginId, spec.title(), spec.onExit() == ExitPolicy.CLOSE_PANE, attempt -> {
            track(attempt);
            Throwable failure = containment.attempt(pluginId, "session connector", () -> { spec.connector().accept(new Pending(attempt)); return null; });
            if (failure != null) attempt.fail(failure.getMessage() == null ? failure.toString() : failure.getMessage());
        }, contained, spec.icon().orElse(null));
    }
```

In `TerminalPane.java`, add below the `request` field's other accessors:

```java
    /** The provider's tab icon, or {@code null} for a local shell or a provider that chose none. */
    javax.swing.Icon providedIcon() { return request == null ? null : request.icon(); }
```

In `TerminalTab.java`, add the import `dev.jasper.app.platform.AppIcons`, a field `private Icon terminalIcon;` and, after `title()`:

```java
    /** The focused pane's provider icon, or the host terminal icon for local shells. */
    Icon icon() {
        TerminalPane pane = focusedPane();
        Icon provided = pane == null ? null : pane.providedIcon();
        if (provided != null) return provided;
        if (terminalIcon == null) terminalIcon = AppIcons.named("TERMINAL");
        return terminalIcon;
    }
```

`javax.swing.*` is already imported there.

In `SessionSpec.java`, change `@param icon reserved for a tab icon` to `@param icon the tab's icon (since 0.7.5); empty shows Jasper's terminal icon`.

In `RemotePlugin.java`:
- next to `icon = context.appearance().icon(dev.jasper.sdk.ui.IconName.NETWORK);`, add `serverIcon = context.appearance().icon(dev.jasper.sdk.ui.IconName.SERVER);`, and declare `private javax.swing.Icon serverIcon;` beside the existing `icon` field;
- replace `SessionSpec spec = SessionSpec.of(host.name(), pending -> {` with `SessionSpec spec = new SessionSpec(host.name(), java.util.Optional.of(serverIcon), dev.jasper.sdk.terminal.ExitPolicy.KEEP_OPEN, pending -> {`.

In `plugins/remote/src/main/resources/plugin.toml`, set `sdk = ">=0.7.5, <0.8"`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.workspace.TerminalTabIconTest --tests dev.jasper.app.plugins.HostedSessionsTest :plugins:remote:test`

Use `./gradlew projects` to confirm the Remote project path if `:plugins:remote` is not it. Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app/src jasper-sdk/src plugins/remote
git commit -m "feat(sessions): carry a provider's tab icon from SessionSpec to the tab

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: Title-only title bar and IntelliJ editor tabs below the toolbar

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/MacTitleBar.java` (whole file)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowTabs.java` (whole file)
- Delete: `jasper-app/src/main/java/dev/jasper/app/workspace/TabMotion.java`, `jasper-app/src/test/java/dev/jasper/app/workspace/TabMotionTest.java`, `jasper-app/src/main/resources/dev/jasper/app/icons/title/` (Tabler `plus`, `terminal-2` and `x`, plus `SOURCE.txt`), and the now-unused Tabler `icons/SOURCE.txt` and `icons/LICENSE.txt`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (constructors at 129-143, north stack at 162-166, `installTitleBar` at 186-202)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalWindow.java:42` and `jasper-app/src/main/java/dev/jasper/app/windows/NativeShells.java` (lines 45, 131, 159 and 163-166)
- Modify: `jasper-app/src/main/resources/dev/jasper/app/themes/FlatLightLaf.properties` and `FlatDarkLaf.properties` (new keys)
- Modify tests: `WindowTabsTest.java` (rewrite), `MacTitleBarTest.java`, `TabHeightTest.java`, `MockUiTest.java` (first test), `TitleBarPreview.java:44`, and the constructor callers in `CommandPalettePreview`, `CommandPaletteShortcutsTest`, `PaletteScopesTest` and `WindowCommandPaletteTest`

**Interfaces:**
- Consumes: `TerminalTab.icon()` (Task 4); `AppIcons.chrome("close")`, `"closeHovered"` and `"arrowDown"` (Task 2).
- Produces:
  - `MacTitleBar.install(JRootPane root, JComponent content, Runnable minimumSizeChanged, boolean supported)`;
  - `MacTitleBar.setTitle(String)`;
  - `MacTitleBar.MODERN_HEIGHT = 28` and `RETRO_HEIGHT = 32`;
  - `WindowTabs(WindowContent owner)`;
  - `void WindowTabs.scrollTabs(int delta)` and `JPopupMenu WindowTabs.tabList()`;
  - UIManager keys `Jasper.tabUnderline`, `Jasper.tabUnderlineInactive`, `Jasper.tabHoverBackground` and `Jasper.findErrorBackground` (the last is used by Task 7);
  - the `WindowContent` constructors lose their `LongSupplier animationClock` parameter.

- [ ] **Step 1: Write the failing tests**

Replace `jasper-app/src/test/java/dev/jasper/app/workspace/WindowTabsTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.BuiltinTheme;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.KeyBindings;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Map;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class WindowTabsTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void stripIsAlwaysVisibleAndSelectsAndClosesRealTabs() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            JComponent strip = named(owner, "windowTabs");
            assertThat(strip).isNotNull();
            assertThat(strip.getPreferredSize().height).isEqualTo(38);
            assertThat(strip.isVisible()).as("a lone tab still shows, as in IntelliJ").isTrue();
            var first = owner.currentTab(); first.rename("first"); owner.newTab(HOME);
            var second = owner.currentTab(); second.rename("second"); owner.update();
            ((AbstractButton) named(strip, "select:first")).doClick();
            assertThat(owner.currentTab()).isSameAs(first);
            ((AbstractButton) named(strip, "close:second")).doClick();
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(1);
            assertThat(strip.isVisible()).isTrue();
            assertThat(named(strip, "newTab")).as("New tab lives in the toolbar").isNull();
            owner.tabStrip().setSize(600, 300); owner.tabStrip().doLayout();
            assertThat(owner.tabStrip().getComponentAt(0).getY()).isLessThanOrEqualTo(1);
        });
    }

    @Test void tabsAreContentSizedLeftAlignedWithIconTitleAndTrailingClose() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.currentTab().rename("first"); owner.newTab(HOME); owner.currentTab().rename("second");
            var strip = owner.windowTabs(); layout(strip, 800, 38);
            var first = (AbstractButton) named(strip, "select:first");
            var second = (AbstractButton) named(strip, "select:second");
            assertThat(first.getIcon()).isNotNull();
            assertThat(first.getIcon().getIconWidth()).isEqualTo(16);
            assertThat(first.getHorizontalAlignment()).isEqualTo(SwingConstants.LEADING);
            Container one = first.getParent(), two = second.getParent();
            assertThat(one.getX()).isZero();
            assertThat(two.getX()).isEqualTo(one.getWidth());
            assertThat(one.getWidth()).isBetween(80, 240);
            assertThat(one.getWidth() + two.getWidth()).isLessThan(800 - 24);
            var close = named(strip, "close:second");
            assertThat(close.isVisible()).as("the selected tab always offers close").isTrue();
            assertThat(close.getX()).isGreaterThanOrEqualTo(second.getX() + second.getWidth());
            var otherClose = named(strip, "close:first");
            assertThat(otherClose.isVisible()).isFalse();
            mouse(first, MouseEvent.MOUSE_ENTERED, 10, 10, MouseEvent.NOBUTTON);
            assertThat(otherClose.isVisible()).isTrue();
            mouse(first, MouseEvent.MOUSE_EXITED, -100, -100, MouseEvent.NOBUTTON);
            assertThat(otherClose.isVisible()).isFalse();
            assertThat(named(strip, "tabList").getBounds()).isEqualTo(new Rectangle(776, 0, 24, 38));
        });
    }

    @Test void tooltipsCarryTheTitleAndTheLiveSelectShortcut() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.currentTab().rename("first"); owner.newTab(HOME); owner.currentTab().rename("second"); owner.update();
            var strip = owner.windowTabs();
            assertThat(named(strip, "select:second").getToolTipText()).isEqualTo("second (⌘2)");
            owner.setBindings(KeyBindings.withOverrides(true, Map.of("select_tab_2", "ctrl+alt+2")));
            assertThat(named(strip, "select:second").getToolTipText()).isEqualTo("second (⌃⌥2)");
            owner.setBindings(KeyBindings.withOverrides(true, Map.of("select_tab_2", "none")));
            assertThat(named(strip, "select:second").getToolTipText()).isEqualTo("second");
        });
    }

    @Test void selectedTabPaintsAnAccentUnderlineThatGreysWhenInactiveAndFollowsTheTheme() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.currentTab().rename("first"); owner.newTab(HOME); owner.currentTab().rename("second"); owner.update();
            var strip = owner.windowTabs(); layout(strip, 800, 38);
            Container selected = named(strip, "select:second").getParent();
            Container other = named(strip, "select:first").getParent();
            assertThat(pixel(selected, 36)).isEqualTo(UIManager.getColor("Jasper.tabUnderline"));
            assertThat(pixel(selected, 4)).isEqualTo(UIManager.getColor("Jasper.tabSelectedBackground"));
            assertThat(pixel(other, 36)).isNotEqualTo(UIManager.getColor("Jasper.tabUnderline"));
            owner.setActive(false);
            assertThat(pixel(selected, 36)).isEqualTo(UIManager.getColor("Jasper.tabUnderlineInactive"));
            owner.setActive(true); owner.selectTheme(BuiltinTheme.LIGHT); layout(strip, 800, 38);
            assertThat(pixel(selected, 36)).isEqualTo(UIManager.getColor("Jasper.tabUnderline"));
        });
    }

    @Test void controlsRetainIdentityAcrossMetadataSelectionReorderAndTheme() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            JComponent strip = named(owner, "windowTabs");
            var first = owner.currentTab(); first.rename("first"); owner.update();
            JComponent control = named(strip, "select:first");
            owner.newTab(HOME); var second = owner.currentTab();
            owner.reorderTab(1, 0);
            assertThat(owner.currentTab()).isSameAs(second);
            first.rename("<html>literal title"); owner.update();
            owner.selectTheme(BuiltinTheme.LIGHT);
            assertThat(named(strip, "select:<html>literal title")).isSameAs(control);
            assertThat(control.getClientProperty("html.disable")).isEqualTo(true);
            assertThat(control.getToolTipText()).startsWith("<html>literal title");
            assertThat(owner.currentTab()).isSameAs(second);
        });
    }

    @Test void middleClickAndDragUseTheSameSelectionAndOrderModel() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            JComponent strip = named(owner, "windowTabs");
            var first = owner.currentTab(); first.rename("first"); owner.newTab(HOME);
            var second = owner.currentTab(); second.rename("second"); owner.update();
            layout(strip, 600, 38);
            JComponent one = named(strip, "select:first"), two = named(strip, "select:second");
            Point target = SwingUtilities.convertPoint(two, 10, 10, one);
            mouse(one, MouseEvent.MOUSE_PRESSED, 10, 10, MouseEvent.BUTTON1);
            mouse(one, MouseEvent.MOUSE_RELEASED, target.x, target.y, MouseEvent.BUTTON1);
            assertThat(owner.tabStrip().getComponentAt(1)).isSameAs(first);
            assertThat(owner.currentTab()).isSameAs(first);
            mouse(two, MouseEvent.MOUSE_PRESSED, 10, 10, MouseEvent.BUTTON2);
            assertThat(owner.tabStrip().getTabCount()).isEqualTo(1);
            assertThat(owner.currentTab()).isSameAs(first);
        });
    }

    @Test void overflowScrollsKeepsSelectionVisibleAndTheListReachesEveryLiteralTitle() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var tabs = owner.windowTabs();
            Dimension minimum = tabs.getMinimumSize();
            for (int i = 0; i < 12; i++) { owner.currentTab().rename("<b>long name ".repeat(100) + i); owner.newTab(HOME); }
            owner.update(); layout(tabs, 330, 38);
            assertThat(tabs.getMinimumSize()).isEqualTo(minimum);
            for (Component child : tabs.getComponents())
                if (child.isVisible()) assertThat(child.getX() + child.getWidth()).isLessThanOrEqualTo(330);
            owner.invoke(ActionId.SELECT_TAB_1); layout(tabs, 330, 38);
            JComponent selected = named(tabs, "select:" + owner.currentTab().title());
            assertThat(selected.getParent().isVisible()).isTrue();
            tabs.scrollTabs(1); layout(tabs, 330, 38);
            assertThat(selected.getParent().isVisible()).isFalse();
            owner.invoke(ActionId.NEXT_TAB); layout(tabs, 330, 38);
            assertThat(named(tabs, "select:" + owner.currentTab().title()).getParent().isVisible()).isTrue();
            JPopupMenu list = tabs.tabList();
            assertThat(list.getComponentCount()).isEqualTo(13);
            var item = (JMenuItem) list.getComponent(3);
            assertThat(item.getClientProperty("html.disable")).isEqualTo(true);
            assertThat(item.getText()).startsWith("<b>long name");
            ((JMenuItem) list.getComponent(12)).doClick(); layout(tabs, 330, 38);
            assertThat(owner.tabStrip().getSelectedIndex()).isEqualTo(12);
            assertThat(named(tabs, "select:" + owner.currentTab().title()).getParent().isVisible()).isTrue();
        });
    }

    @Test void narrowingTheWindowKeepsTheSelectedTabVisible() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            JComponent strip = named(owner, "windowTabs");
            for (int i = 0; i < 4; i++) owner.newTab(HOME);
            owner.currentTab().rename("selected"); owner.update();
            layout(strip, 850, 38);
            assertThat(named(strip, "select:selected").getParent().isVisible()).isTrue();
            layout(strip, 250, 38);
            assertThat(named(strip, "select:selected").getParent().isVisible()).isTrue();
        });
    }

    @Test void closedOwnerDisablesTheTabList() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            JComponent strip = named(owner, "windowTabs");
            owner.close();
            assertThat(named(strip, "tabList").isEnabled()).isFalse();
        });
    }

    static JComponent named(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JComponent component && name.equals(component.getName())) return component;
            if (child instanceof Container container) { JComponent found = named(container, name); if (found != null) return found; }
        }
        return null;
    }
    static void layout(Container component, int width, int height) {
        component.setSize(width, height); layoutTree(component);
    }
    static void layoutTree(Container component) {
        component.doLayout();
        for (Component child : component.getComponents()) if (child instanceof Container container) layoutTree(container);
    }
    private static void mouse(Component component, int id, int x, int y, int button) {
        component.dispatchEvent(new MouseEvent(component, id, 1, 0, x, y, 1, false, button));
    }
    private static Color pixel(Container entry, int y) {
        var image = new BufferedImage(entry.getWidth(), entry.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { entry.paint(g); } finally { g.dispose(); }
        return new Color(image.getRGB(2, y));
    }
}
```

Edit `MacTitleBarTest.java`:
- Replace `nativeBoundsKeepOneIntegratedHeaderSafeAndTitleClipped` with:

```java
    @Test void modernHeaderIsATitleRowAboveTheToolbarAndTheTabStrip() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            var minimumChanges = new AtomicInteger();
            owner.onMinimumSizeChanged = minimumChanges::incrementAndGet;
            try (var bar = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                assertThat(bar.getPreferredSize().height).isEqualTo(28);
                assertThat(bar.getComponentCount()).as("only the title").isEqualTo(1);
                JComponent tabs = WindowTabsTest.named(owner, "windowTabs");
                assertThat(tabs).as("tabs live in the window content").isNotNull();
                root.setSize(959, 600); layoutTree(root);
                Point toolbar = SwingUtilities.convertPoint(owner.toolbar(), 0, 0, root);
                Point strip = SwingUtilities.convertPoint(tabs, 0, 0, root);
                assertThat(toolbar.y).isEqualTo(bar.getHeight());
                assertThat(strip.y).isEqualTo(toolbar.y + owner.toolbar().getHeight());
                assertThat(label(bar).isVisible()).isTrue();
                assertThat(label(bar).getX() * 2 + label(bar).getWidth()).isEqualTo(bar.getWidth());
                owner.newTab(HOME); layoutTree(root);
                assertThat(label(bar).isVisible()).as("the title stays with many tabs").isTrue();
                root.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, new Rectangle(12, 6, 110, 34));
                assertThat(minimumChanges.get()).isPositive();
                bar.doLayout();
                assertThat(label(bar).getX()).isGreaterThanOrEqualTo(130);
                var before = bar.getMinimumSize();
                owner.currentTab().rename("extremely long shell title ".repeat(100)); owner.update();
                assertThat(bar.getMinimumSize()).isEqualTo(before);
                assertThat(bar.getPreferredSize().width).isLessThan(1000);
                bar.setSize(80, 28); bar.doLayout();
                assertThat(label(bar).getWidth()).isZero();
            }
        });
    }
```

- In `themeAndActivationRecolorActualHeaderAndHidingToolbarRetainsItsHeight`, change the last assertion's `+ 38` to `+ 28`.
- In `titleOnlyHeaderDoesNotPaintThePlaceholderOverItsBackground`:
  - replace `MacTitleBar.install(root, new JPanel(), placeholder, () -> 38, () -> { }, true)` with `MacTitleBar.install(root, new JPanel(), () -> { }, true)`;
  - replace `bar.setTitle("Credential Vault", true)` with `bar.setTitle("Credential Vault")`;
  - change every `38` in that test to `28`;
  - delete the now-unused `placeholder` local.

Edit `TabHeightTest.java`:
- In `liveHeightChangesBothRowsAndMinimumWithoutReplacingSessionOrFont`, change all three `assertThat(header.getHeight()).isEqualTo(...)` to `isEqualTo(28)`. Keep every `owner.windowTabs().getHeight()` expectation (38 and then 44) and the `+ 6` minimum.
- Rename it to `liveHeightChangesTheTabStripAndMinimumButNotTheTitleRow`.
- In `plainWindowUsesSameHeightAndRejectsValuesOutsideInclusiveRange`, change `assertThat(owner.windowTabs().isVisible()).isFalse();` to `isTrue()`.

In `TitleBarPreview.java` line 44, change `38` to `28`.

In `MockUiTest.java`, replace `referenceRowsAndSurfaceRemainContinuousAtActualWindowSize` with a version that derives rows instead of hard-coding the old 38 px integrated header:

```java
    @Test void referenceRowsAndSurfaceRemainContinuousAtActualWindowSize() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var root = new JRootPane();
            try (var title = WindowContent.installTitleBar(root, owner, true, value -> {})) {
                assertThat(title).isNotNull();
                root.setSize(958, 958); layoutTree(root);
                int toolbar = owner.toolbar().getHeight(), tabs = owner.windowTabs().getHeight();
                assertThat(title.getHeight()).isEqualTo(28);
                assertThat(tabs).isEqualTo(38);
                assertThat(owner.status().getHeight()).isEqualTo(30);
                assertThat(owner.currentPane().getSize()).isEqualTo(new Dimension(958, 958 - 28 - toolbar - tabs - 30));
                var image = new BufferedImage(958, 958, BufferedImage.TYPE_INT_RGB);
                var g = image.createGraphics(); root.printAll(g); g.dispose();
                assertThat(image.getRGB(650, 27) & 0xffffff).as("title separator").isEqualTo(0x313439);
                assertThat(image.getRGB(650, 28 + toolbar / 2) & 0xffffff).as("toolbar surface").isEqualTo(0x23262c);
                assertThat(image.getRGB(650, 28 + toolbar + tabs - 1) & 0xffffff).as("tab strip separator").isEqualTo(0x313439);
                assertThat(new Color(image.getRGB(500, 940))).isEqualTo(UIManager.getColor("Jasper.titleBackground"));
            }
        });
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WindowTabsTest' --tests 'dev.jasper.app.workspace.MacTitleBarTest' --tests 'dev.jasper.app.workspace.TabHeightTest'`
Expected: compilation fails (`scrollTabs`, `tabList` and the new `MacTitleBar.install` signature are missing).

- [ ] **Step 3: Add the theme keys**

In `jasper-app/src/main/resources/dev/jasper/app/themes/FlatLightLaf.properties`, add after `Jasper.titleSeparator`:

```properties
Jasper.tabUnderline = #4083c9
Jasper.tabUnderlineInactive = #9ca7b8
Jasper.tabHoverBackground = #dfdfe1
Jasper.findErrorBackground = #ffd6d6
```

In `FlatDarkLaf.properties`, add after `Jasper.titleSeparator`:

```properties
Jasper.tabUnderline = #4a88c7
Jasper.tabUnderlineInactive = #747a80
Jasper.tabHoverBackground = #2c3036
Jasper.findErrorBackground = #5c3b3b
```

- [ ] **Step 4: Replace `MacTitleBar.java`**

```java
package dev.jasper.app.platform;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import com.formdev.flatlaf.util.SystemInfo;
import com.jetbrains.JBR;
import com.jetbrains.WindowDecorations;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import javax.swing.*;

/** A centred window title over macOS's native controls; toolbar and tabs live in the window content. */
public final class MacTitleBar extends JPanel implements AutoCloseable {
    /** Unscaled height of the modern title row. */
    public static final int MODERN_HEIGHT = 28;
    /** Unscaled height of the retro title row. */
    public static final int RETRO_HEIGHT = 32;
    private final JRootPane root;
    private final boolean retro;
    private final Runnable minimumSizeChanged;
    private final JLabel title = new JLabel("Jasper", SwingConstants.CENTER);
    private final PropertyChangeListener boundsChanged;
    private Window window;
    private WindowDecorations decorations;
    private WindowDecorations.CustomTitleBar nativeTitle;
    private int nativeLeft, nativeRight;
    private boolean active = true;
    private boolean closed;
    private final ComponentAdapter frameBounds = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent event) { refreshNativeGeometry(); }
        @Override public void componentShown(ComponentEvent event) { refreshNativeGeometry(); }
    };
    private final WindowStateListener stateChanged = event -> SwingUtilities.invokeLater(this::refreshNativeGeometry);
    private final HierarchyListener peerChanged = event -> {
        if ((event.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0)
            SwingUtilities.invokeLater(this::refreshNativeGeometry);
    };

    /** Retro requires JBR's native-control geometry; other platforms retain native decorations. */
    public static boolean isSupported() {
        return SystemInfo.isMacFullWindowContentSupported
            && (!SwingAppearance.retro() || JBR.isWindowDecorationsSupported());
    }

    /** Called before pack; the host owns its component and notification wiring. */
    public static MacTitleBar install(JRootPane root, JComponent content, Runnable minimumSizeChanged, boolean supported) {
        root.setContentPane(content);
        if (!supported) return null;
        root.putClientProperty("apple.awt.fullWindowContent", true);
        root.putClientProperty("apple.awt.transparentTitleBar", true);
        root.putClientProperty("apple.awt.windowTitleVisible", false);
        var bar = new MacTitleBar(root, minimumSizeChanged);
        var surface = new JPanel(new BorderLayout());
        surface.add(bar, BorderLayout.NORTH); surface.add(content, BorderLayout.CENTER);
        root.setContentPane(surface);
        return bar;
    }

    private MacTitleBar(JRootPane root, Runnable minimumSizeChanged) {
        super(null);
        this.root = root; this.retro = SwingAppearance.retro();
        this.minimumSizeChanged = minimumSizeChanged;
        boundsChanged = event -> { revalidate(); repaint(); minimumSizeChanged.run(); };
        title.putClientProperty("html.disable", true);
        title.getAccessibleContext().setAccessibleName("Window title");
        add(title);
        root.addPropertyChangeListener(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, boundsChanged);
    }

    /** Places in-window retro menus below the native title region; modern menus retain root ownership. */
    public void setMenuBar(JMenuBar menuBar) {
        if (closed) return;
        if (!retro) { root.setJMenuBar(menuBar); return; }
        root.setJMenuBar(null);
        var heading = new JPanel(new BorderLayout());
        heading.add(this, BorderLayout.NORTH);
        menuBar.setBackground(new Color(UIManager.getColor("Panel.background").getRGB()));
        menuBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("MenuBar.borderColor")));
        heading.add(menuBar, BorderLayout.CENTER);
        root.getContentPane().add(heading, BorderLayout.NORTH);
        root.revalidate();
    }

    /** Updates metadata independently of the window's workspace model. */
    public void setTitle(String value) {
        if (closed) return;
        if (!value.equals(title.getText())) title.setText(value);
        revalidate(); repaint();
    }

    /** Public JBR API only. Root properties above remain the fallback on other runtimes. */
    public void attach(Window window) {
        if (!(window instanceof Frame) && !(window instanceof Dialog))
            throw new IllegalArgumentException("A title bar needs a frame or dialog");
        if (closed || this.window != null || !JBR.isWindowDecorationsSupported()) return;
        decorations = JBR.getWindowDecorations();
        nativeTitle = decorations.createCustomTitleBar();
        nativeTitle.setHeight(titleHeight());
        this.window = window;
        applyNativeTitle(nativeTitle);
        window.addComponentListener(frameBounds);
        window.addWindowStateListener(stateChanged);
        window.addHierarchyListener(peerChanged);
        refreshNativeGeometry();
    }

    private void applyNativeTitle(WindowDecorations.CustomTitleBar value) {
        if (window instanceof Frame frame) decorations.setCustomTitleBar(frame, value);
        else if (window instanceof Dialog dialog) decorations.setCustomTitleBar(dialog, value);
    }

    private void refreshNativeGeometry() {
        if (closed || nativeTitle == null) return;
        if (nativeTitle.getHeight() != titleHeight()) nativeTitle.setHeight(titleHeight());
        int left = (int) Math.ceil(nativeTitle.getLeftInset());
        int right = (int) Math.ceil(nativeTitle.getRightInset());
        if (nativeLeft != left || nativeRight != right) {
            nativeLeft = left; nativeRight = right;
            revalidate(); repaint(); minimumSizeChanged.run();
        }
    }

    private int safeInset() {
        Object bounds = root.getClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS);
        // Published native bounds are already in root-pane coordinates; do not scale again.
        int controlsEnd = bounds instanceof Rectangle rectangle ? Math.max(0, rectangle.x + rectangle.width) : 0;
        return Math.max(UIScale.scale(120), Math.max(nativeLeft, controlsEnd) + UIScale.scale(8));
    }

    private int titleHeight() { return UIScale.scale(retro ? RETRO_HEIGHT : MODERN_HEIGHT); }

    @Override public Dimension getMinimumSize() {
        return new Dimension(2 * Math.max(safeInset(), nativeRight) + UIScale.scale(64), titleHeight());
    }
    @Override public Dimension getPreferredSize() { return new Dimension(Math.max(UIScale.scale(400), getMinimumSize().width), titleHeight()); }

    @Override public void doLayout() {
        refreshNativeGeometry();
        // Reserve the same space at both ends so native controls cannot shift the title off centre.
        int inset = Math.max(Math.min(safeInset(), getWidth()), nativeRight);
        title.setBounds(inset, 0, Math.max(0, getWidth() - 2 * inset), getHeight());
    }

    public void setLight(boolean light) {
        if (closed) return;
        root.putClientProperty("apple.awt.windowAppearance",
            light ? "NSAppearanceNameAqua" : "NSAppearanceNameDarkAqua");
        refreshColors();
    }

    public void setActive(boolean active) { this.active = active; refreshColors(); }

    private void refreshColors() {
        setBackground(UIManager.getColor(retro ? "Jasper.retroTitleBackground" : "Jasper.titleBackground"));
        title.setFont(retro ? UIManager.getFont("Label.font").deriveFont(Font.PLAIN, UIManager.getFont("Label.font").getSize2D() + UIScale.scale(1f))
            : SystemFonts.ui(Font.BOLD, 13f));
        title.setForeground(UIManager.getColor(active ? "Jasper.titleForeground" : "Jasper.titleInactiveForeground"));
        repaint();
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(UIManager.getColor(retro ? "MenuBar.borderColor" : "Jasper.titleSeparator"));
        g.fillRect(0, getHeight() - UIScale.scale(1), getWidth(), UIScale.scale(1));
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        root.removePropertyChangeListener(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS, boundsChanged);
        if (window != null) {
            window.removeComponentListener(frameBounds);
            window.removeWindowStateListener(stateChanged);
            window.removeHierarchyListener(peerChanged);
            applyNativeTitle(null);
            window = null; nativeTitle = null; decorations = null;
        }
    }
}
```

- [ ] **Step 5: Replace `WindowTabs.java`**

```java
package dev.jasper.app.workspace;

import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.SystemFonts;
import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import javax.swing.*;

/** IntelliJ-style editor tabs over WindowContent's retained Swing selection model. */
final class WindowTabs extends JPanel implements AutoCloseable {
    private static final int MIN_TAB = 80, MAX_TAB = 240, LIST_WIDTH = 24, EDGE = 8, GAP = 6, UNDERLINE = 3;
    private final WindowContent owner;
    private final IdentityHashMap<TerminalTab, Entry> entries = new IdentityHashMap<>();
    private final List<TerminalTab> order = new ArrayList<>();
    private final JButton list = new JButton();
    private TerminalTab selected;
    private int firstVisible;
    private int layoutWidth = -1;
    private boolean revealSelection = true;
    private boolean active = true;
    private boolean disposed;

    WindowTabs(WindowContent owner) {
        super(null);
        this.owner = owner;
        setName("windowTabs");
        flat(list);
        list.setName("tabList");
        list.setIcon(AppIcons.chrome("arrowDown"));
        list.setToolTipText("Show all tabs"); list.getAccessibleContext().setAccessibleName("Show all tabs");
        list.addActionListener(event -> tabList().show(list, 0, list.getHeight()));
        add(list);
        addMouseWheelListener(event -> scrollTabs(event.getWheelRotation()));
    }

    void refresh() {
        var updated = new ArrayList<TerminalTab>();
        for (int i = 0; i < owner.tabStrip().getTabCount(); i++) updated.add((TerminalTab) owner.tabStrip().getComponentAt(i));
        if (!updated.equals(order)) {
            for (TerminalTab tab : List.copyOf(order)) if (!updated.contains(tab)) remove(entries.remove(tab));
            order.clear(); order.addAll(updated);
            for (TerminalTab tab : order) if (!entries.containsKey(tab)) {
                Entry entry = new Entry(tab); entries.put(tab, entry); add(entry);
            }
            revealSelection = true;
        }
        if (selected != owner.currentTab()) { selected = owner.currentTab(); revealSelection = true; }
        setBackground(UIManager.getColor("Jasper.titleBackground"));
        for (TerminalTab tab : order) entries.get(tab).refresh();
        list.setEnabled(!disposed);
        revalidate(); repaint();
    }

    void setActive(boolean active) { this.active = active; refresh(); }

    /** Moves the first visible tab by {@code delta}; the mouse wheel scrolls the strip. */
    void scrollTabs(int delta) {
        if (order.isEmpty()) return;
        firstVisible = Math.max(0, Math.min(order.size() - 1, firstVisible + delta));
        revalidate(); repaint();
    }

    /** Every tab in order, titles literal; choosing one selects and reveals it. */
    JPopupMenu tabList() {
        var menu = new JPopupMenu();
        for (TerminalTab tab : order) {
            var item = new JCheckBoxMenuItem(tab.title(), tab.icon(), tab == selected);
            item.putClientProperty("html.disable", true);
            item.addActionListener(event -> { if (!disposed) owner.selectTab(tab); });
            menu.add(item);
        }
        return menu;
    }

    @Override public Dimension getMinimumSize() { return new Dimension(UIScale.scale(MIN_TAB), UIScale.scale(owner.tabHeight())); }
    @Override public Dimension getPreferredSize() { return new Dimension(UIScale.scale(MIN_TAB), UIScale.scale(owner.tabHeight())); }

    @Override public void doLayout() {
        int width = getWidth(), height = getHeight();
        if (width != layoutWidth) { layoutWidth = width; revealSelection = true; }
        int listWidth = Math.min(width, UIScale.scale(LIST_WIDTH));
        int space = Math.max(0, width - listWidth);
        int[] widths = new int[order.size()];
        for (int i = 0; i < widths.length; i++) widths[i] = entries.get(order.get(i)).tabWidth();
        firstVisible = Math.max(0, Math.min(firstVisible, order.size() - 1));
        int selectedIndex = order.indexOf(selected);
        if (revealSelection && selectedIndex >= 0) {
            if (selectedIndex < firstVisible) firstVisible = selectedIndex;
            while (firstVisible < selectedIndex && sum(widths, firstVisible, selectedIndex + 1) > space) firstVisible++;
            revealSelection = false;
        }
        // Scrolled-away tabs return once everything after them fits again.
        while (firstVisible > 0 && sum(widths, firstVisible - 1, widths.length) <= space) firstVisible--;
        int x = 0;
        boolean full = false;
        for (int i = 0; i < order.size(); i++) {
            Entry entry = entries.get(order.get(i));
            boolean visible = !full && i >= firstVisible && (x + widths[i] <= space || i == firstVisible);
            if (i >= firstVisible && !visible) full = true;
            entry.setVisible(visible);
            if (!visible) continue;
            int tabWidth = Math.max(0, Math.min(widths[i], space - x));
            entry.setBounds(x, 0, tabWidth, height);
            entry.doLayout();
            x += tabWidth;
        }
        list.setBounds(width - listWidth, 0, listWidth, height);
    }

    private static int sum(int[] values, int from, int to) {
        int total = 0;
        for (int i = from; i < to; i++) total += values[i];
        return total;
    }

    @Override public void close() { disposed = true; list.setEnabled(false); }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(UIManager.getColor("Jasper.titleSeparator"));
        g.fillRect(0, getHeight() - UIScale.scale(1), getWidth(), UIScale.scale(1));
    }

    private static void flat(JButton button) {
        button.putClientProperty("html.disable", true);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setContentAreaFilled(false); button.setFocusable(false);
    }

    private final class Entry extends JPanel {
        private final TerminalTab tab;
        private final JButton select = new JButton(), close = new JButton();
        private boolean hovered;
        private Point origin;

        Entry(TerminalTab tab) {
            super(null);
            this.tab = tab;
            setOpaque(false);
            flat(select); flat(close);
            select.setHorizontalAlignment(SwingConstants.LEADING);
            select.setIconTextGap(UIScale.scale(GAP));
            select.addActionListener(event -> { if (!disposed) owner.selectTab(tab); });
            close.setIcon(AppIcons.chrome("close"));
            close.setRolloverIcon(AppIcons.chrome("closeHovered"));
            close.setRolloverEnabled(true);
            close.setToolTipText("Close tab"); close.getAccessibleContext().setAccessibleName("Close tab");
            close.addActionListener(event -> { if (!disposed) owner.closeTab(tab); });
            MouseAdapter gestures = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { hover(true); }
                @Override public void mouseExited(MouseEvent event) {
                    hover(contains(SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), Entry.this)));
                }
                @Override public void mousePressed(MouseEvent event) {
                    origin = null;
                    if (disposed) return;
                    if (SwingUtilities.isMiddleMouseButton(event)) { owner.closeTab(tab); return; }
                    if (SwingUtilities.isLeftMouseButton(event) && event.getComponent() != close) {
                        owner.selectTab(tab);
                        origin = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), WindowTabs.this);
                    }
                }
                @Override public void mouseReleased(MouseEvent event) {
                    Point start = origin; origin = null;
                    if (disposed || start == null || !SwingUtilities.isLeftMouseButton(event)) return;
                    Point end = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), WindowTabs.this);
                    if (start.distance(end) <= UIScale.scale(5)) return;
                    for (int i = 0; i < order.size(); i++) {
                        Entry target = entries.get(order.get(i));
                        if (target.isVisible() && target.getBounds().contains(end)) {
                            owner.reorderTab(owner.tabStrip().indexOfComponent(tab), i); break;
                        }
                    }
                }
            };
            addMouseListener(gestures); select.addMouseListener(gestures); close.addMouseListener(gestures);
            add(select); add(close);
        }

        private void hover(boolean value) {
            hovered = value;
            close.setVisible(tab == selected || hovered);
            repaint();
        }

        int tabWidth() {
            FontMetrics metrics = select.getFontMetrics(select.getFont());
            int content = UIScale.scale(EDGE + 16 + GAP) + metrics.stringWidth(tab.title()) + UIScale.scale(GAP + 16 + EDGE);
            return Math.max(UIScale.scale(MIN_TAB), Math.min(UIScale.scale(MAX_TAB), content));
        }

        void refresh() {
            String text = tab.title();
            if (!text.equals(select.getText())) {
                select.setText(text); select.getAccessibleContext().setAccessibleName(text);
                select.setName("select:" + text); close.setName("close:" + text);
            }
            String shortcut = owner.tabShortcut(order.indexOf(tab));
            select.setToolTipText(shortcut.isEmpty() ? text : text + " (" + shortcut + ")");
            select.setIcon(tab.icon());
            select.setSelected(tab == selected);
            select.setFont(SystemFonts.ui(Font.PLAIN, 13f));
            select.setForeground(UIManager.getColor(!active ? "Jasper.titleInactiveForeground"
                : tab == selected ? "Jasper.tabSelectedForeground" : "Jasper.titleForeground"));
            close.setVisible(tab == selected || hovered);
        }

        @Override public void doLayout() {
            int edge = UIScale.scale(EDGE), closeWidth = Math.min(UIScale.scale(16), getWidth());
            int closeX = Math.max(0, getWidth() - edge - closeWidth);
            close.setBounds(closeX, 0, closeWidth, getHeight());
            select.setBounds(Math.min(edge, getWidth()), 0, Math.max(0, closeX - UIScale.scale(GAP) - edge), getHeight());
        }

        @Override protected void paintComponent(Graphics graphics) {
            if (tab == selected || hovered) {
                graphics.setColor(UIManager.getColor(tab == selected ? "Jasper.tabSelectedBackground" : "Jasper.tabHoverBackground"));
                graphics.fillRect(0, 0, getWidth(), getHeight());
            }
            super.paintComponent(graphics);
            if (tab == selected) {
                int line = UIScale.scale(UNDERLINE);
                graphics.setColor(UIManager.getColor(active ? "Jasper.tabUnderline" : "Jasper.tabUnderlineInactive"));
                graphics.fillRect(0, getHeight() - line, getWidth(), line);
            }
        }
    }
}
```

- [ ] **Step 6: Rewire `WindowContent`, `TerminalWindow` and `NativeShells`**

In `WindowContent.java`:
- Replace the constructor overloads at lines 129–143 so that none takes a clock:

```java
    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings) {
        this(launcher, directory, newWindow, quit, onEmpty, themes, bindings,
            new CommandHistory(), System.getProperty("os.name").startsWith("Mac"));
    }

    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings, CommandHistory history, boolean macOs) {
```

  Delete the old `(… bindings, LongSupplier animationClock)` overload entirely.
- Replace lines 162–166 with:

```java
        windowTabs = retro() ? null : new WindowTabs(this);
        retroTabs = retro() ? new RetroTabs(this) : null;
        var north = new JPanel(new BorderLayout());
        north.add(chrome.toolbar(), BorderLayout.NORTH);
        if (windowTabs != null) north.add(windowTabs, BorderLayout.SOUTH);
```

- Replace `installTitleBar`:

```java
    /** Workspace adapter: wires title/theme values to the platform-only title bar. */
    static MacTitleBar installTitleBar(JRootPane root, WindowContent content, boolean supported,
                                      Consumer<String> nativeTitle) {
        var bar = MacTitleBar.install(root, content, () -> content.onMinimumSizeChanged.run(), supported);
        content.onTitle = value -> {
            String display = TerminalTitle.windowTitle(value);
            nativeTitle.accept(display);
            if (bar != null) bar.setTitle(display);
        };
        if (bar != null) {
            content.onThemeChanged = theme -> bar.setLight(theme.chrome().appearance() == Appearance.LIGHT);
            bar.setLight(content.theme().chrome().appearance() == Appearance.LIGHT);
        }
        content.update();
        return bar;
    }
```

Remove the `System::nanoTime` argument from every `WindowContent` constructor call:

```bash
python3 - <<'PY'
import pathlib, re
files = ["jasper-app/src/main/java/dev/jasper/app/workspace/TerminalWindow.java"] + [str(p) for p in pathlib.Path("jasper-app/src/test/java").rglob("*.java")]
for name in files:
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    updated = re.sub(r",\s*System::nanoTime(?=\s*,)", "", text)
    if updated != text: path.write_text(updated, encoding="utf-8"); print("updated", name)
PY
grep -rn "nanoTime" jasper-app/src/main/java/dev/jasper/app/workspace jasper-app/src/test/java/dev/jasper/app/workspace | grep -v "DesktopTestSupport"
```

Expected: the script lists `TerminalWindow.java`, `CommandPalettePreview.java`, `CommandPaletteShortcutsTest.java`, `PaletteScopesTest.java` and `WindowCommandPaletteTest.java`. The grep prints nothing.

In `NativeShells.java`:
- delete `private static final int TITLE_HEIGHT = 38;`;
- replace `MacTitleBar.install(root, surface.holder(), new JPanel(), () -> TITLE_HEIGHT, () -> { }, supported)` with `MacTitleBar.install(root, surface.holder(), () -> { }, supported)`;
- replace each `bar.setTitle(title, true)` and `bar.setTitle(surface.title(), true)` with the one-argument form.

Delete the animation and the Tabler title icons:

```bash
git rm jasper-app/src/main/java/dev/jasper/app/workspace/TabMotion.java jasper-app/src/test/java/dev/jasper/app/workspace/TabMotionTest.java
git rm -r jasper-app/src/main/resources/dev/jasper/app/icons/title
git rm jasper-app/src/main/resources/dev/jasper/app/icons/SOURCE.txt jasper-app/src/main/resources/dev/jasper/app/icons/LICENSE.txt
grep -rn "icons/title\|TabMotion\|animationTimer\|refreshHeight\|onTabHeightChanged = bar" jasper-app/src
```

Expected: the grep prints nothing.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test`

Expected: pass, including the unchanged `RetroChromeTest`, `RetroTabsTest`, `NativeShellsChromeTest` and `TerminalTitleTest`. If `NativeShellsChromeTest`'s modern case asserted a 38 px header, change that expectation to 28. That is the only permitted edit there.

- [ ] **Step 8: Commit** (run the AGENTS.md character check first)

```bash
git add -A jasper-app/src
git commit -m "feat(chrome): title-only title row and IntelliJ editor tabs below the toolbar

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: IntelliJ toolbar row in modern

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java` (constructor lines 92-112, `contributedButton`, `renderContributedToolbar`, `addButton`, `ReferenceButton`, `ReferenceToolbar`, `refreshTheme`)
- Modify tests: `WindowChromeTest.java`, `WindowChromeHintTest.java`, `WindowChromeContributionsTest.java`, `MockUiTest.java` (one assertion)

**Interfaces:**
- Consumes: `AppIcons.toolbarIcon(...)` (Task 2).
- Produces:
  - the modern toolbar order `New tab, New window │ Split, Zoom pane │ <plugins> ⟶ Find, Settings`;
  - the component name `pluginSeparator` on the modern plugin-group separator;
  - `static String ReferenceButton.shortcutText(KeyStroke)` (package-private nested class).

- [ ] **Step 1: Write the failing tests**

In `WindowChromeTest.referenceToolbarKeepsLabelsAccessibilityDisabledActionsAndVisibilityModes`:
- replace both `.containsExactly("New tab", "New window", "Split", "Zoom pane", "Find")` with `.containsExactly("New tab", "New window", "Split", "Zoom pane", "Find", "Settings")`;
- then append this test to the class:

```java
    @Test void modernToolbarGroupsActionsPinsFindAndSettingsRightAndHidesTheEmptyPluginGroup() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var toolbar = owner.toolbar();
            toolbar.setSize(1000, 30); toolbar.doLayout();
            List<String> shape = new java.util.ArrayList<>();
            for (var child : toolbar.getComponents()) {
                if (child instanceof JButton button) shape.add((String) button.getClientProperty("label"));
                else if (child instanceof JSeparator separator) shape.add(separator.isVisible() ? "|" : "(|)");
                else if (child instanceof Box.Filler) shape.add("->");
            }
            assertThat(shape).containsExactly("New tab", "New window", "|", "Split", "Zoom pane", "(|)", "->", "Find", "Settings");
            var find = (JButton) java.util.Arrays.stream(toolbar.getComponents())
                .filter(child -> child instanceof JButton button && "Find".equals(button.getClientProperty("label"))).findFirst().orElseThrow();
            var settings = toolbar.getComponent(toolbar.getComponentCount() - 1);
            assertThat(settings.getX() + settings.getWidth()).isGreaterThan(900);
            assertThat(find.getX()).isGreaterThan(700);
            assertThat(find.getHeight()).isEqualTo(24);
            assertThat(toolbar.getPreferredSize().height).isEqualTo(30);
        });
    }
```

Replace the first test in `WindowChromeHintTest` with:

```java
    @Test void liveMacBindingChangesReachTheToolbarTooltipAndNoneRemovesTheShortcut() throws Exception {
        Path file = directory.resolve("config.toml");
        var service = new ConfigService(file, true);
        ConfigurationTestSupport[] controller = new ConfigurationTestSupport[1];
        WindowContent[] owner = new WindowContent[1];
        JButton[] button = new JButton[1];
        try {
            edt(() -> {
                var themes = new ThemeController();
                controller[0] = new ConfigurationTestSupport(themes, service);
                owner[0] = new WindowContent(launcher(new ArrayDeque<>()), HOME, path -> {}, () -> {}, () -> {}, themes);
                controller[0].register(owner[0]);
                button[0] = (JButton) owner[0].toolbar().getComponent(0);
            });
            String[][] examples = {
                {"cmd+t", "New Tab (⌘T)"},
                {"cmd+shift+t", "New Tab (⇧⌘T)"},
                {"cmd+alt+t", "New Tab (⌥⌘T)"},
                {"ctrl+alt+shift+cmd+t", "New Tab (⌃⌥⇧⌘T)"},
                {"F12", "New Tab (F12)"},
                {"none", "New Tab"}
            };
            for (String[] example : examples) {
                Files.writeString(file, "[keybindings]\nnew_tab='" + example[0] + "'\n");
                service.reload().get();
                edt(() -> {
                    assertThat(owner[0].toolbar().getComponent(0)).isSameAs(button[0]);
                    assertThat(button[0].getToolTipText()).as("tooltip for %s", example[0]).isEqualTo(example[1]);
                });
            }
        } finally {
            edt(() -> { if (owner[0] != null) owner[0].close(); if (controller[0] != null) controller[0].close(); });
            service.close();
        }
    }
```

Delete the now-unused `hintInk` helper and the unused imports (`UIScale`, `BufferedImage`, `ArrayList`, and `java.awt.*` if nothing else uses it).

In `WindowChromeContributionsTest.toolbarControlsFollowBuiltinsAndFollowModeAndRemoval`, replace

```java
            assertThat(after).containsSubsequence("Find", "Run Tool", "Tool");
```

with

```java
            if (retro) assertThat(after).containsSubsequence("Find", "Run Tool", "Tool");
            else assertThat(after).endsWith("Zoom pane", "Run Tool", "Tool", "Find", "Settings");
            if (!retro) assertThat(WindowTabsTest.named(owner.toolbar(), "pluginSeparator").isVisible()).isTrue();
```

Then add a dedicated test:

```java
    @Test void modernPluginSeparatorAppearsOnlyWhileThePluginGroupHasItems() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.connectContributions(model);
            var separator = WindowTabsTest.named(owner.toolbar(), "pluginSeparator");
            assertThat(separator.isVisible()).isFalse();
            ActionEntry run = model.addAction("dev.x.run", "Run Tool", null, List.of(), Optional.empty(), invocation -> {});
            model.addToolbar(new ToolbarEntry.Button("dev.x.run"));
            assertThat(separator.isVisible()).isTrue();
            run.close();
            assertThat(separator.isVisible()).isFalse();
        });
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WindowChrome*'`
Expected: fail. There is no Settings button in modern, no `pluginSeparator`, the tooltip is `New Tab`, and the height is 42.

- [ ] **Step 3: Implement**

In `WindowChrome`:
- add the field `private final ToolbarSeparator pluginSeparator = new ToolbarSeparator();`;
- replace constructor lines 92–112 (from `addButton(ActionId.NEW_TAB, "square-plus");` through the closing `}` of `if (owner.retro()) { … }`) with:

```java
        pluginSeparator.setName("pluginSeparator");
        if (owner.retro()) buildRetroToolbar(); else buildModernToolbar();
    }

    /** Metal's labelled row, unchanged: plugin items sit before the glue, then Settings and Exit. */
    private void buildRetroToolbar() {
        addButton(ActionId.NEW_TAB, "square-plus"); addButton(ActionId.NEW_WINDOW, "app-window");
        addToolbarSeparator();
        addSplitButton();
        toolbar.add(Box.createHorizontalStrut(UIScale.scale(4)));
        addButton(ActionId.ZOOM_PANE, "maximize"); addButton(ActionId.FIND, "search");
        addToolbarSeparator();
        toolbar.add(toolbarGlue);
        addButton(ActionId.OPEN_SETTINGS, "settings");
        addButton(ActionId.QUIT, "exit");
    }

    /** IntelliJ grouping: tab and window, pane, plugin items, then Find and Settings pinned right. */
    private void buildModernToolbar() {
        addButton(ActionId.NEW_TAB, "square-plus"); addButton(ActionId.NEW_WINDOW, "app-window");
        toolbar.add(new ToolbarSeparator());
        addSplitButton();
        addButton(ActionId.ZOOM_PANE, "maximize");
        pluginSeparator.setVisible(false);
        toolbar.add(pluginSeparator);
        toolbar.add(toolbarGlue);
        addButton(ActionId.FIND, "search");
        addButton(ActionId.OPEN_SETTINGS, "settings");
    }

    private void addSplitButton() {
        JButton split = addButton(ActionId.SPLIT_RIGHT, "columns-2");
        split.setAction(null); split.setText("Split"); split.setIcon(AppIcons.toolbarIcon("columns-2"));
        split.setToolTipText("Split pane right or down"); split.getAccessibleContext().setAccessibleName("Split pane");
        split.addActionListener(event -> {
            owner.updateActions(); JPopupMenu popup = new JPopupMenu();
            popup.add(owner.action(ActionId.SPLIT_RIGHT)); popup.add(owner.action(ActionId.SPLIT_DOWN));
            popup.show(split, 0, split.getHeight());
        });
        owner.action(ActionId.SPLIT_RIGHT).addPropertyChangeListener(event -> {
            if (event.getPropertyName().equals("enabled")) split.setEnabled(owner.action(ActionId.SPLIT_RIGHT).isEnabled());
        });
    }
```

- In `renderContributedToolbar()`, just before `toolbar.revalidate(); toolbar.repaint();`, add `pluginSeparator.setVisible(!contributedToolbar.isEmpty());`.
- In `contributedButton` and `addButton`, change the modern branch's `button.setIconTextGap(UIScale.scale(8));` to `button.setIconTextGap(UIScale.scale(4));`.
- In `addButton`, replace `if (button.getToolTipText() == null) button.setToolTipText(id.label());` with `button.setToolTipText(id.label());`.
- Replace the whole `ReferenceButton` class with:

```java
    /** An IntelliJ toolbar button: transparent at rest, a rounded fill on hover or press, a small arrow for menus. */
    private static final class ReferenceButton extends JButton {
        private final ActionId id;
        private boolean compact;
        private boolean chevron;
        ReferenceButton(Action action, ActionId id) { super(action); this.id = id; setRolloverEnabled(true); }
        private boolean labels() { return getText() != null && !compact; }
        private boolean menu() { return id == ActionId.SPLIT_RIGHT || chevron; }

        /** The tooltip names the action and, while one is bound, its live shortcut. */
        @Override public String getToolTipText() {
            String base = super.getToolTipText();
            if (base == null || getAction() == null) return base;
            Object value = getAction().getValue(Action.ACCELERATOR_KEY);
            String shortcut = value instanceof KeyStroke stroke ? shortcutText(stroke) : "";
            return shortcut.isEmpty() ? base : base + " (" + shortcut + ")";
        }

        static String shortcutText(KeyStroke stroke) {
            int modifiers = stroke.getModifiers();
            String key = KeyEvent.getKeyText(stroke.getKeyCode());
            if ((modifiers & InputEvent.META_DOWN_MASK) != 0) {
                String prefix = "";
                if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) prefix += "⌃";
                if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) prefix += "⌥";
                if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) prefix += "⇧";
                return prefix + "⌘" + key;
            }
            String prefix = KeyEvent.getModifiersExText(modifiers &
                (InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK));
            return prefix.isEmpty() ? key : prefix + "+" + key;
        }

        private Font chromeFont() {
            Font font = UIManager.getFont("Label.font");
            return font.deriveFont(Font.PLAIN, font.getSize2D() - UIScale.scale(.5f));
        }

        @Override public Dimension getPreferredSize() {
            if (dev.jasper.app.platform.SwingAppearance.retro()) return super.getPreferredSize();
            int width = UIScale.scale(24);
            if (labels()) width += UIScale.scale(4) + getFontMetrics(chromeFont()).stringWidth(getText());
            if (menu()) width += UIScale.scale(10);
            return new Dimension(width, UIScale.scale(24));
        }

        @Override protected void paintComponent(Graphics graphics) {
            if (dev.jasper.app.platform.SwingAppearance.retro()) { super.paintComponent(graphics); return; }
            var g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                ButtonModel model = getModel();
                if (isEnabled() && (model.isRollover() || model.isPressed() || model.isSelected())) {
                    g.setColor(UIManager.getColor(model.isPressed() || model.isSelected()
                        ? "Button.toolbar.pressedBackground" : "Button.toolbar.hoverBackground"));
                    int arc = UIScale.scale(6);
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                }
                if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(.38f));
                Icon icon = getIcon();
                int x = labels() || menu() ? UIScale.scale(4) : (getWidth() - icon.getIconWidth()) / 2;
                icon.paintIcon(this, g, x, (getHeight() - icon.getIconHeight()) / 2);
                x += icon.getIconWidth();
                if (labels()) {
                    x += UIScale.scale(4);
                    g.setFont(chromeFont()); g.setColor(UIManager.getColor("Jasper.chromeForeground"));
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(getText(), x, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                    x += fm.stringWidth(getText());
                }
                if (menu()) {
                    x += UIScale.scale(3);
                    int y = getHeight() / 2 - UIScale.scale(1), w = UIScale.scale(5), h = UIScale.scale(3);
                    g.setColor(UIManager.getColor("Jasper.mutedForeground"));
                    g.fillPolygon(new int[]{x, x + w, x + w / 2}, new int[]{y, y, y + h}, 3);
                }
            } finally { g.dispose(); }
        }
    }
```

- Replace the whole `ReferenceToolbar` class with:

```java
    /** A 30 px IntelliJ row; shrinks to icon controls before any action can disappear at narrow widths. */
    private static final class ReferenceToolbar extends JToolBar {
        @Override public Dimension getMinimumSize() { return new Dimension(0, UIScale.scale(30)); }
        @Override public Dimension getPreferredSize() { return new Dimension(0, UIScale.scale(30)); }
        @Override public void doLayout() {
            int available = Math.max(0, getWidth() - UIScale.scale(16));
            int preferred = 0;
            for (Component child : getComponents()) {
                if (!child.isVisible()) continue;
                if (child instanceof ReferenceButton button) { button.compact = false; preferred += button.getPreferredSize().width; }
                else if (child instanceof JSeparator) preferred += UIScale.scale(9);
                else preferred += child.getPreferredSize().width;
            }
            boolean compact = preferred > available;
            int fixed = 0;
            for (Component child : getComponents()) {
                if (!child.isVisible()) continue;
                if (child instanceof ReferenceButton button) { button.compact = compact; fixed += button.getPreferredSize().width; }
                else if (child instanceof JSeparator) fixed += UIScale.scale(9);
                else fixed += child.getPreferredSize().width;
            }
            // At extreme widths compress spacing/buttons together; menus retain the same actions.
            double ratio = Math.min(1, available / (double) Math.max(1, fixed));
            int x = UIScale.scale(8), y = (getHeight() - UIScale.scale(24)) / 2;
            for (Component child : getComponents()) {
                if (!child.isVisible()) continue;
                int width;
                if (child instanceof JButton) {
                    width = (int) Math.floor(child.getPreferredSize().width * ratio);
                    child.setBounds(x, y, width, UIScale.scale(24));
                } else if (child instanceof JSeparator) {
                    width = (int) Math.floor(UIScale.scale(9) * ratio);
                    child.setBounds(x + width / 2, (getHeight() - UIScale.scale(16)) / 2, UIScale.scale(1), UIScale.scale(16));
                } else {
                    width = child.getPreferredSize().width > 0 ? (int) Math.floor(child.getPreferredSize().width * ratio) : Math.max(0, available - fixed);
                    child.setBounds(x, 0, width, getHeight());
                }
                x += width;
            }
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(UIManager.getColor("Separator.foreground"));
            g.fillRect(0, getHeight() - UIScale.scale(1), getWidth(), UIScale.scale(1));
        }
    }
```

- In `MockUiTest.referenceRowsAndSurfaceRemainContinuousAtActualWindowSize`, add `assertThat(toolbar).isEqualTo(30);` after the `int toolbar = …` line.
- In `refreshTheme()`, `button.setFont(((ReferenceButton) button).chromeFont());` still compiles, because `chromeFont` stays private in the nested class and the outer class can still reach it.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*'`
Expected: pass, including the unchanged `RetroChromeTest` and the retro cases of `WindowChromeContributionsTest`.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app/src
git commit -m "feat(chrome): IntelliJ toolbar row with plugin group and live shortcut tooltips

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: IntelliJ single-line find bar in modern

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/FindBar.java` (whole file)
- Test: create `jasper-app/src/test/java/dev/jasper/app/workspace/FindBarModernTest.java`. `FindBarTest` and `FindBarVisibilityTest` must pass unchanged.

**Interfaces:**
- Consumes: `AppIcons.chrome("searchWithHistory" | "close" | "matchCase" | "regex" | "previousOccurence" | "nextOccurence")` (Task 2) and the UIManager key `Jasper.findErrorBackground` (Task 5).
- Produces these package-private accessors for tests: `JToggleButton caseButton()`, `JLabel countLabel()`, `boolean missing()` and `JPopupMenu recentMenu()`. Existing accessors are kept.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/FindBarModernTest.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.FlatClientProperties;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ThemeStyle;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.config.TerminalOptions;
import dev.jasper.terminal.session.SessionLaunchOptions;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.view.TerminalView;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(OS.WINDOWS)
class FindBarModernTest {
    @AfterEach void reset() throws Exception { edt(() -> new ThemeController()); }

    @Test void modernBarIsOneRowWithInFieldTogglesAndIconOnlyNavigation() throws Exception {
        try (TerminalSession session = shell(HOME)) {
            edt(() -> {
                new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
                var bar = new FindBar(new TerminalView(session, TerminalOptions.defaults()));
                var query = bar.queryField();
                assertThat(query.getAccessibleContext().getAccessibleName()).isEqualTo("Find in terminal");
                var leading = (JComponent) query.getClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT);
                assertThat(leading.getName()).isEqualTo("findHistory");
                var trailing = (JComponent) query.getClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT);
                assertThat(names(trailing)).containsSubsequence("clearFind", "caseSensitive", "regex");
                assertThat(bar.caseButton().getText()).isNull();
                assertThat(bar.caseButton().getIcon().getIconWidth()).isEqualTo(16);
                assertThat(bar.caseButton().getAccessibleContext().getAccessibleName()).isEqualTo("Case sensitive");
                assertThat(bar.regexButton().getText()).isNull();
                assertThat(bar.regexButton().getAccessibleContext().getAccessibleName()).isEqualTo("Regular expression");
                String[][] buttons = {{"previousMatch", "Previous"}, {"nextMatch", "Next"}, {"closeFind", "Close"}};
                for (String[] expected : buttons) {
                    var button = (AbstractButton) WindowTabsTest.named(bar, expected[0]);
                    assertThat(button).as(expected[0]).isNotNull();
                    assertThat(button.getText()).as(expected[0]).isNull();
                    assertThat(button.getIcon().getIconWidth()).isEqualTo(16);
                    assertThat(button.getAccessibleContext().getAccessibleName()).isEqualTo(expected[1]);
                    assertThat(button.getToolTipText()).isNotBlank();
                }
                var clear = (AbstractButton) WindowTabsTest.named(bar, "clearFind");
                assertThat(clear.isVisible()).isFalse();
                query.setText("alpha");
                assertThat(clear.isVisible()).isTrue();
                clear.doClick();
                assertThat(query.getText()).isEmpty();
                assertThat(clear.isVisible()).isFalse();
                bar.dispose();
            });
        }
    }

    @Test void togglesDriveTheQueryMissesTintTheFieldAndRecentSearchesStayInMemory() throws Exception {
        try (TerminalSession session = TerminalSession.start(SessionLaunchOptions.builder().command(List.of("/bin/sh", "-c",
            "printf 'alpha alpha\\n\\033]2;ready\\007'; read answer")).environment(System.getenv()).workingDirectory(HOME)
            .grid(new GridSize(80, 24)).scrollback(100).build())) {
            until(() -> session.title().equals("ready"));
            FindBar[] bar = new FindBar[1];
            edt(() -> {
                new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
                var view = new TerminalView(session, TerminalOptions.defaults());
                view.setSize(view.getPreferredSize());
                bar[0] = attached(view); bar[0].open();
                bar[0].queryField().setText("ALPHA");
                bar[0].caseButton().doClick();
            });
            until(() -> bar[0].missing());
            edt(() -> {
                var query = bar[0].queryField();
                assertThat(query.getClientProperty(FlatClientProperties.OUTLINE)).isEqualTo(FlatClientProperties.OUTLINE_ERROR);
                assertThat(query.getBackground()).isEqualTo(UIManager.getColor("Jasper.findErrorBackground"));
                assertThat(bar[0].countLabel().getText()).isEqualTo("0 results");
                bar[0].caseButton().doClick();
            });
            until(() -> bar[0].result().count() == 2);
            edt(() -> {
                assertThat(bar[0].missing()).isFalse();
                assertThat(bar[0].queryField().getClientProperty(FlatClientProperties.OUTLINE)).isNull();
                assertThat(bar[0].countLabel().getText()).isEqualTo("2/2");
                bar[0].next();
                bar[0].queryField().setText("beta");
                bar[0].close();
                var menu = bar[0].recentMenu();
                assertThat(menu.getComponentCount()).isEqualTo(2);
                assertThat(((JMenuItem) menu.getComponent(0)).getText()).isEqualTo("beta");
                assertThat(((JMenuItem) menu.getComponent(1)).getText()).isEqualTo("ALPHA");
                assertThat(((JMenuItem) menu.getComponent(0)).getClientProperty("html.disable")).isEqualTo(true);
                ((JMenuItem) menu.getComponent(1)).doClick();
                assertThat(bar[0].queryField().getText()).isEqualTo("ALPHA");
                bar[0].dispose(); bar[0].removeNotify();
            });
        }
    }

    @Test void retroKeepsTheTextButtonRow() throws Exception {
        try (TerminalSession session = shell(HOME)) {
            edt(() -> {
                new ThemeController(ThemeStyle.RETRO, Appearance.LIGHT);
                var bar = new FindBar(new TerminalView(session, TerminalOptions.defaults()));
                List<String> texts = new ArrayList<>();
                for (Component child : bar.getComponents()) {
                    if (child instanceof AbstractButton button) texts.add(button.getText());
                    else if (child instanceof JLabel label) texts.add(label.getText());
                    else if (child instanceof JTextField) texts.add("[field]");
                }
                assertThat(texts).containsExactly("Find:", "[field]", "Previous", "Next", "Case", "Regex", "0 / 0", "Close");
                bar.dispose();
            });
        }
    }

    private static FindBar attached(TerminalView view) {
        var bar = new FindBar(view);
        // Search runs only while showing; keep the root lightweight and omit native caret location queries.
        bar.queryField().removeCaretListener((javax.swing.event.CaretListener) bar.queryField().getAccessibleContext());
        bar.addNotify();
        return bar;
    }

    private static List<String> names(Container parent) {
        List<String> names = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            if (child.getName() != null) names.add(child.getName());
            if (child instanceof Container nested) names.addAll(names(nested));
        }
        return names;
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.workspace.FindBarModernTest`
Expected: compilation fails (`caseButton`, `countLabel`, `missing` and `recentMenu` are missing).

- [ ] **Step 3: Replace `FindBar.java`**

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.SwingAppearance;
import dev.jasper.terminal.search.FindResult;
import dev.jasper.terminal.search.SearchQuery;
import dev.jasper.terminal.view.TerminalView;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** A pane-local, debounced search UI. Matching and generation checks belong to TerminalView. */
final class FindBar extends JPanel {
    private static final int RECENT_LIMIT = 10;
    private final TerminalView view;
    private final boolean modern = !SwingAppearance.retro();
    private final JTextField query = new JTextField(18);
    private final JToggleButton regex = new JToggleButton(modern ? null : "Regex");
    private final JToggleButton caseSensitive = new JToggleButton(modern ? null : "Case");
    private final JLabel count = new JLabel(modern ? "" : "0 / 0");
    private final JButton clear = new JButton();
    private final Deque<String> recent = new ArrayDeque<>();
    private final Timer debounce;
    private FindResult result = new FindResult(0, 0, null);
    private boolean disposed;
    private boolean searching;
    private boolean dirty;
    private volatile boolean missing;
    private long generation;
    private long pendingNavigation;

    FindBar(TerminalView view) {
        this.view = view;
        debounce = new Timer(180, event -> search());
        debounce.setRepeats(false);
        query.getAccessibleContext().setAccessibleName("Find in terminal");
        query.setToolTipText("Search each terminal row; matches do not span wrapped rows");
        if (modern) buildModern(); else buildRetro();
        query.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { edited(); }
            public void removeUpdate(DocumentEvent event) { edited(); }
            public void changedUpdate(DocumentEvent event) { edited(); }
        });
        regex.addActionListener(event -> schedule());
        caseSensitive.addActionListener(event -> schedule());
        bind(query, KeyStroke.getKeyStroke("ENTER"), "next", this::next);
        bind(query, KeyStroke.getKeyStroke("shift ENTER"), "previous", this::previous);
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ESCAPE"), "close");
        getActionMap().put("close", new AbstractAction() {
            public void actionPerformed(ActionEvent event) { close(); }
        });
        view.setFindResultListener(found -> {
            invalidateSearch(); dirty = true;
            if (result.error() == null) { miss(found.count() == 0 && !query.getText().isEmpty()); showResult(found); }
        });
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) refreshShowing();
        });
        setVisible(false);
    }

    private void buildRetro() {
        setLayout(new FlowLayout(FlowLayout.LEADING, 4, 3));
        add(new JLabel("Find:")); add(query);
        add(button("Previous", this::previous)); add(button("Next", this::next));
        add(caseSensitive); add(regex); add(count); add(button("Close", this::close));
    }

    /** IntelliJ's find row: history and toggles inside the field, then count, arrows and a far-right close. */
    private void buildModern() {
        setLayout(new BorderLayout(UIScale.scale(6), 0));
        setBorder(BorderFactory.createEmptyBorder(UIScale.scale(2), UIScale.scale(6), UIScale.scale(3), UIScale.scale(6)));
        JButton history = iconButton("findHistory", "Recent searches", "Recent searches", "searchWithHistory", null);
        history.addActionListener(event -> recentMenu().show(history, 0, history.getHeight()));
        configure(clear, "clearFind", "Clear search", "Clear search", "close");
        clear.addActionListener(event -> { query.setText(""); query.requestFocusInWindow(); });
        clear.setVisible(false);
        configure(caseSensitive, "caseSensitive", "Case sensitive", "Match case", "matchCase");
        configure(regex, "regex", "Regular expression", "Regular expression", "regex");
        var trailing = new JToolBar();
        trailing.setFloatable(false); trailing.setOpaque(false); trailing.setBorder(BorderFactory.createEmptyBorder());
        trailing.add(clear); trailing.addSeparator(); trailing.add(caseSensitive); trailing.add(regex);
        query.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT, history);
        query.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, trailing);
        query.putClientProperty(FlatClientProperties.STYLE, "borderWidth: 0; focusWidth: 0; innerFocusWidth: 0");
        count.setName("findCount");
        var controls = new JPanel(new FlowLayout(FlowLayout.LEADING, UIScale.scale(4), 0));
        controls.setOpaque(false);
        controls.add(count);
        controls.add(iconButton("previousMatch", "Previous", "Previous match (Shift+Enter)", "previousOccurence", this::previous));
        controls.add(iconButton("nextMatch", "Next", "Next match (Enter)", "nextOccurence", this::next));
        controls.add(Box.createHorizontalStrut(UIScale.scale(16)));
        controls.add(iconButton("closeFind", "Close", "Close (Escape)", "close", this::close));
        add(query, BorderLayout.CENTER);
        add(controls, BorderLayout.EAST);
    }

    private static JButton button(String label, Runnable task) {
        JButton button = new JButton(label);
        button.addActionListener(event -> task.run());
        return button;
    }

    private static JButton iconButton(String name, String accessible, String tooltip, String artwork, Runnable task) {
        JButton button = new JButton();
        configure(button, name, accessible, tooltip, artwork);
        if (task != null) button.addActionListener(event -> task.run());
        return button;
    }

    private static void configure(AbstractButton button, String name, String accessible, String tooltip, String artwork) {
        button.setName(name);
        button.setIcon(AppIcons.chrome(artwork));
        button.setToolTipText(tooltip);
        button.getAccessibleContext().setAccessibleName(accessible);
        button.setFocusable(false);
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
    }

    private static void bind(JComponent component, KeyStroke stroke, String name, Runnable task) {
        component.getInputMap().put(stroke, name);
        component.getActionMap().put(name, new AbstractAction() {
            public void actionPerformed(ActionEvent event) { task.run(); }
        });
    }

    @Override public void updateUI() {
        super.updateUI();
        // A theme change replaces UIResource colours; keep the no-match tint.
        if (query != null) miss(missing);
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (!modern) return;
        g.setColor(UIManager.getColor("Jasper.titleSeparator"));
        g.fillRect(0, getHeight() - UIScale.scale(1), getWidth(), UIScale.scale(1));
    }

    @Override public void removeNotify() {
        dirty |= searching;
        cancelSearch(); // a temporary reparent cancels work, not the user's queued navigation
        super.removeNotify();
    }

    @Override public void addNotify() {
        super.addNotify();
        refreshShowing();
    }

    private void refreshShowing() {
        if (!isShowing()) {
            dirty |= searching;
            cancelSearch(); // retain the query, completed result and queued navigation across hidden tabs
        } else if (dirty && !disposed) {
            debounce.restart();
        }
    }

    void open() {
        if (disposed) return;
        setVisible(true); query.requestFocusInWindow(); query.selectAll(); schedule();
    }

    private void edited() {
        if (modern) clear.setVisible(!query.getText().isEmpty());
        schedule();
    }

    private void schedule() {
        if (isVisible() && !disposed) {
            // Cancel old matching immediately, including during the debounce interval.
            invalidateSearch(); dirty = true;
            view.clearFind(); miss(false); showResult(new FindResult(0, 0, null));
            if (isShowing()) debounce.restart();
        }
    }

    private void search() {
        if (disposed || !isShowing()) return;
        searching = true; dirty = false;
        long request = generation;
        view.findAsync(new SearchQuery(query.getText(), regex.isSelected(), caseSensitive.isSelected()), found -> {
            if (disposed || !isShowing() || request != generation) return;
            searching = false;
            FindResult navigated = found;
            if (found.error() == null && found.count() > 0) {
                long steps = pendingNavigation % found.count();
                while (steps > 0) { navigated = view.findNext(); steps--; }
                while (steps < 0) { navigated = view.findPrevious(); steps++; }
            }
            pendingNavigation = 0;
            miss(navigated.error() != null || navigated.count() == 0 && !query.getText().isEmpty());
            showResult(navigated);
        });
    }

    void next() { navigate(1); }
    void previous() { navigate(-1); }

    private void navigate(int direction) {
        if (disposed) return;
        remember();
        if (dirty || searching) {
            pendingNavigation += direction;
            if (!searching) { debounce.stop(); search(); }
        } else if (result.error() == null) {
            showResult(direction > 0 ? view.findNext() : view.findPrevious());
        }
    }

    private void cancelSearch() {
        generation++; debounce.stop(); searching = false;
    }

    private void invalidateSearch() {
        cancelSearch(); pendingNavigation = 0;
    }

    private void showResult(FindResult found) {
        result = found;
        if (!modern) count.setText(found.error() == null ? found.current() + " / " + found.count() : "Invalid regex");
        else if (found.error() != null) count.setText("Invalid regex");
        else if (found.count() > 0) count.setText(found.current() + "/" + found.count());
        else count.setText(missing ? "0 results" : "");
        count.setToolTipText(found.error());
        count.getAccessibleContext().setAccessibleDescription(found.error());
    }

    /** IntelliJ tints the field when nothing matches or the pattern is invalid. */
    private void miss(boolean value) {
        missing = value;
        if (!modern) return;
        query.putClientProperty(FlatClientProperties.OUTLINE, value ? FlatClientProperties.OUTLINE_ERROR : null);
        query.setBackground(UIManager.getColor(value ? "Jasper.findErrorBackground" : "TextField.background"));
    }

    private void remember() {
        String text = query.getText();
        if (text.isBlank()) return;
        recent.remove(text); recent.addFirst(text);
        while (recent.size() > RECENT_LIMIT) recent.removeLast();
    }

    /** This pane's recent queries, newest first; held in memory only. */
    JPopupMenu recentMenu() {
        var menu = new JPopupMenu();
        if (recent.isEmpty()) {
            var none = new JMenuItem("No recent searches"); none.setEnabled(false); menu.add(none);
        }
        for (String text : recent) {
            var item = new JMenuItem(text);
            item.putClientProperty("html.disable", true);
            item.addActionListener(event -> { query.setText(text); query.requestFocusInWindow(); });
            menu.add(item);
        }
        return menu;
    }

    void close() {
        remember();
        invalidateSearch(); dirty = true; view.clearFind(); miss(false); showResult(new FindResult(0, 0, null));
        setVisible(false); view.requestFocusInWindow();
    }

    void dispose() { close(); disposed = true; view.setFindResultListener(null); }
    JTextField queryField() { return query; }
    JToggleButton regexButton() { return regex; }
    JToggleButton caseButton() { return caseSensitive; }
    JLabel countLabel() { return count; }
    boolean missing() { return missing; }
    FindResult result() { return result; }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.FindBar*'`
Expected: `FindBarModernTest`, `FindBarTest` and `FindBarVisibilityTest` all pass.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app/src
git commit -m "feat(find): IntelliJ single-line find bar with in-field toggles and recent searches

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: Documentation, the full gate and the status record

**Files:**
- Modify: `docs/plugin-authoring.md` (the Icons bullet around line 111)
- Modify: `docs/sdk-icons.md` (the 0.7.4 intro and the "22 meanings" paragraph)
- Modify: `docs/sdk-architecture.md` (new 0.7.5 section after "Host-owned semantic catalog (SDK 0.7.4)")
- Modify: `docs/app-architecture.md` (the window chrome description) and `jasper-sdk/README.md` (only if it states the SDK version)
- Modify: `docs/STATUS.md`, the spec status line, and this plan's **Status** line

- [ ] **Step 1: Update the plugin docs**

In `docs/plugin-authoring.md`, replace the **Icons** bullet with:

```markdown
- **Icons.** Prefer semantic names: `context.appearance().icon(IconName.LOCK)`. SDK 0.7.5
  (`sdk = ">=0.7.5, <0.8"`) adds `SPLIT`, `ZOOM`, `TERMINAL` and `SERVER`. Jasper supplies
  IntelliJ-style artwork in modern and OldGNOME2 artwork in retro, and selects the skin; plugins
  need no artwork or style check. Returned icons are 16px, with separate 28px variants in retro
  host toolbars. For custom artwork, draw a 16×16 SVG in the IntelliJ light palette (grey
  `#6E6E6E`, blue `#389FD6`, green `#59A869`, red `#DB5860`, yellow `#EDA200`) and load it with
  `icon("path/in/your/jar.svg")`: since 0.7.5 it is drawn in its own colours, and palette colours
  follow dark and light themes. Colour carries meaning: green for run or success, red for stop or
  error, blue for navigation and transfer, grey for everything else. Pair the path with an
  `OldGnomeIcon` for retro, as the sample demonstrates. A session's `SessionSpec.icon` becomes its
  tab icon. See the [catalog and testing example](sdk-icons.md).
```

In `docs/sdk-icons.md`:
- change the first sentence to "Prefer semantic names (SDK 0.7.4; SDK 0.7.5 adds SPLIT, ZOOM, TERMINAL and SERVER):";
- change "Modern icons follow the live light/dark foreground" to "Modern icons are IntelliJ classic-UI artwork whose palette colours follow the live light/dark theme (KEY, LOCK, UNLOCK, CONNECT, DISCONNECT and DELETE keep tinted monochrome outlines)";
- change "`IconName` contains all 22 meanings" to "`IconName` contains all 26 meanings";
- add `SPLIT`, `ZOOM`, `TERMINAL` and `SERVER` to any table of names in that file.

In `docs/sdk-architecture.md`, add after the 0.7.4 catalog section:

```markdown
### Chrome icons and session tab icons (SDK 0.7.5)

- `IconName` and `OldGnomeIcon` gain `SPLIT`, `ZOOM`, `TERMINAL` and `SERVER`. Retro artwork copies
  existing GNOME2/OldGNOME2 rasters; modern artwork is IntelliJ classic-UI SVG vendored under
  `jasper-app/.../icons/intellij/` (Apache-2.0, hash-pinned in `assets.tsv`).
- Behaviour change: `Appearance.icon(String)` and the modern half of `icon(String, OldGnomeIcon)`
  no longer recolour to the chrome foreground. They draw the SVG as authored; FlatLaf's global
  colour filter maps IntelliJ light-palette colours to the running theme. Grey `#6E6E6E` artwork
  looks as before.
- `SessionSpec.icon` is no longer reserved: it is the tab icon. Empty shows `IconName.TERMINAL`.
  Inside the app the icon travels opaquely on `SessionRequest`.
```

In `docs/app-architecture.md`, find the paragraph describing the window chrome and title bar (search for `MacTitleBar` or `WindowTabs`) and replace its description with:

```markdown
Modern windows stack three rows: `MacTitleBar` (macOS only) is a title-only 28 px row with the
native controls and a centred bold title; `WindowChrome`'s IntelliJ-style toolbar (tab/window,
pane, plugin and right-pinned Find/Settings groups; tooltips carry live shortcuts); and
`WindowTabs`, IntelliJ editor tabs sized to content with a 3 px selection underline, wheel
scrolling and a ▾ list of all tabs. Each pane's `FindBar` is IntelliJ's single-line find row.
Retro keeps its Metal toolbar, `RetroTabs`, text-button find bar and 32 px title row.
```

- [ ] **Step 2: Run the full gate**

Run: `./gradlew check`

Expected: `BUILD SUCCESSFUL`. Then count the results:

```bash
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t = f = e = s = 0
for path in glob.glob("*/build/test-results/test/*.xml") + glob.glob("plugins/*/build/test-results/test/*.xml"):
    root = ET.parse(path).getroot()
    t += int(root.get("tests")); f += int(root.get("failures")); e += int(root.get("errors")); s += int(root.get("skipped"))
print(f"tests={t} failures={f} errors={e} skipped={s}")
PY
```

Expected: `failures=0 errors=0`. Also run the AGENTS.md source-hygiene Python check; it must print nothing.

- [ ] **Step 3: Record the status**

In `docs/STATUS.md`, add an entry at the top of the current work list:

```markdown
- **IntelliJ-style modern chrome** (branch `claude/intellij-chrome`, plan
  `docs/superpowers/plans/2026-09-24-jasper-intellij-chrome.md`): title-only title row, IntelliJ
  toolbar, editor tabs and find bar with vendored IntelliJ icons; SDK 0.7.5 adds
  SPLIT/ZOOM/TERMINAL/SERVER, draws plugin SVGs as authored and uses `SessionSpec.icon` as the tab
  icon. Planning decisions (tab height setting kept, toolbar default mode kept, no toolbar
  overflow menu, tab animation removed) are listed in the plan. Visual acceptance in modern
  light, modern dark and retro is user-run. Not merged, not pushed.
```

Set the spec's status line to "Implemented on `claude/intellij-chrome`; visual acceptance pending." Set this plan's **Status** line to "All tasks complete; `./gradlew check` passed (N tests, 0 failures, 0 errors, S skipped), with the counts from Step 2."

- [ ] **Step 4: Commit**

```bash
git add -A docs jasper-sdk/README.md
git commit -m "docs(chrome): document IntelliJ chrome, SDK 0.7.5 icons and status

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

After this task, run one independent whole-branch review. Then hand GUI acceptance to the user: modern light, modern dark and retro, checking the title row, toolbar hover, tabs, the find bar and Remote's server tab icon.
