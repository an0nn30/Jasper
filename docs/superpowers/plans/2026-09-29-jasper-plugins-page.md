# Plugins Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Implemented on claude/settings-polish; ./gradlew check green, 6,860 tests, 0 failures (6 skipped).
Record every deviation from this text here and in `docs/STATUS.md`.

Deviations so far:
- Task 4: `PluginManagerPanel`'s private accessor is `cardList()`, not `list()`: `java.awt.Component` already has a
  `list()` method.
- Task 6: the preview showed two problems, both fixed with tests updated first. A plugin waiting for review showed a
  ticked checkbox and counted as enabled although it cannot run until reviewed; its checkbox is now unticked
  (`PluginCard.checked`), and the group counts use the same rule. The homepage link shared a flowing line with the
  vendor and was clipped when that line wrapped at 18 pt; it now has a line of its own.
- Merge: main had meanwhile made SDK 0.9.2 mean `ScopeSpec.withInRecents` (palette recents). So that 0.9.2 does not
  name two different Jaspers, the manifest keys moved to SDK 0.9.3, and every bundled plugin requires
  `>=0.9.3, <0.10`.

Scope notes (decided while writing this plan):
- The ▾ menu keeps today's labels: "Open Settings", "Open Plugin Folder" and "Open Data Folder", then Remove…,
  Keep (for a plugin marked for removal) or Discard Install. The spec's shorter "Open Folder" and "Open Data" were
  descriptions, not labels.
- A needs-review plugin's primary button reads "Review…", as the button does today. Its checkbox opens the same review.
- Icons are decoded in the view. So a corrupt image still reaches `Row.icon()`, and the view draws the default icon;
  the runtime returns no icon only when the file is missing or larger than 256 KiB.
- `PluginManager.Hooks` gains the URI opener the homepage link needs; the application passes its existing
  `browse(URI)`.
- The preview shows the real bundled plugins (the `settingsPreview` task stages them, as the tests do), plus fixture
  user plugins that are active, need review, and failed.

**Goal:** Settings › Plugins becomes IntelliJ-style: grouped plugin cards with icons and enable checkboxes beside a
details pane with Overview and Details tabs, and `plugin.toml` gains optional `icon` and `homepage` keys.

**Architecture:**
- **Manifest and rows:** the descriptor parser learns `icon` and `homepage`. `PluginCatalog` reads each plugin's icon
  bytes once per version (`PluginIconFiles`), and `PluginRuntime.Row` carries `homepage`, `exports` and `icon`.
- **View:** the passive view `PluginManagerPanel` is rebuilt from `PluginCardList` (cards, groups, search, keyboard),
  `PluginCard`, `PluginDetails` (header, actions and tabs) and `PluginIcons` (drawing).
- **Controller:** `PluginManager` keeps its logic, driving the view through the same calls.

**Tech Stack:** Java 25 (JetBrains Runtime), Swing with FlatLaf 3.7 (`FlatSVGIcon`), JUnit 5, AssertJ, Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-09-29-jasper-plugins-page-design.md`

## Global Constraints

- Build and test only with `./gradlew` from `/Users/dustin/projects/moray/.worktrees/settings-polish`. Never launch
  the GUI (`:jasper-app:run`, `bench`). `:jasper-app:settingsPreview` renders PNGs offscreen and is allowed.
- Run `git branch --show-current` before every commit and confirm it prints `claude/settings-polish`.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Do not push; do not merge.
- `JasperSdk.VERSION` becomes `0.9.2` (Task 1). Bundled plugins that set `icon` or `homepage` declare
  `sdk = ">=0.9.2, <0.10"` (Task 5).
- `icon`: a path inside the plugin's jars ending in `.svg` or `.png`, at most 256 KiB (`262144` bytes).
- `homepage`: an absolute `http` or `https` URI.
- A bad icon or homepage never stops a plugin loading.
- All plugin-supplied text (name, vendor, description, reason, homepage) is shown as plain text: every label showing it
  carries `putClientProperty("html.disable", Boolean.TRUE)`.
- SDK types appear in the application only inside `dev.jasper.app.plugins`. `dev.jasper.app.pluginmanager` adds no new
  Jasper package dependency; it may use FlatLaf classes. `verifyApplicationArchitecture` must pass.
- Source hygiene per AGENTS.md: no raw non-ASCII in Java source. Write `…` for the ellipsis, `▾` for ▾,
  `↗` for ↗ and `—` for the em dash.
- Javadoc: complete for public members, with each tag and its description on the same line.

---

## File Structure

- `jasper-app/src/main/java/dev/jasper/app/plugins/PluginDescriptor.java`: gains `icon` and `homepage`.
- `jasper-app/src/main/java/dev/jasper/app/plugins/DescriptorParser.java`: accepts and checks the two keys.
- `jasper-app/src/main/java/dev/jasper/app/plugins/PluginIconFiles.java` (new): reads icon bytes from jars, cached.
- `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java`: `PluginIcon`, and `Row` gains
  `homepage`, `exports` and `icon`.
- `jasper-app/src/main/java/dev/jasper/app/plugins/PluginCatalog.java`: fills the new `Row` fields.
- `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginIcons.java` (new): draws an icon, default, greyed.
- `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginCard.java` (new): one card.
- `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginCardList.java` (new): groups, search, selection, keys.
- `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginDetails.java` (new): header, actions, tabs, menu.
- `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginManagerPanel.java`: rebuilt from the parts above.
- `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginManager.java`: `Hooks.browse`.
- `jasper-app/src/test/java/dev/jasper/app/pluginmanager/TestRows.java` (new): a builder for rows in tests.
- Bundled plugins: an icon under `src/main/resources/icons/` each, and a new `plugin.toml` version, range and `icon`.
- `SettingsDialogPreview.java` and `jasper-app/build.gradle.kts`: the preview's plugin fixtures and bundled plugins.

---

### Task 1: The `icon` and `homepage` manifest keys, and SDK 0.9.2

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginDescriptor.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/DescriptorParser.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginResolverTest.java`, `PluginCatalogTest.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`,
  `jasper-sdk/src/test/java/dev/jasper/sdk/ui/AppearanceContractTest.java`,
  `jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java`
- Modify: `docs/plugin-authoring.md`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/DescriptorParserTest.java`

**Interfaces:**
- Produces: `PluginDescriptor` components `String icon` and `String homepage`, added after `requires`. Each is empty
  when unset or dropped.

- [ ] **Step 1: Write the failing test**

Add to `DescriptorParserTest.java`:

```java
    @Test void anIconAndAHomepageAreOptionalAndABadOneIsDropped() throws Exception {
        String base = "id = \"a.b\"\nname = \"AB\"\nversion = \"1.0.0\"\nentry = \"a.b.Main\"\nsdk = \">=0.1\"\n";
        PluginDescriptor full = DescriptorParser.parse(base + "icon = \"icons/ab.svg\"\nhomepage = \"https://example.dev/ab\"\n");
        assertThat(full.icon()).isEqualTo("icons/ab.svg");
        assertThat(full.homepage()).isEqualTo("https://example.dev/ab");
        assertThat(DescriptorParser.parse(base + "icon = \"icons/ab.PNG\"\nhomepage = \"http://example.dev\"\n"))
            .satisfies(descriptor -> {
                assertThat(descriptor.icon()).isEqualTo("icons/ab.PNG");
                assertThat(descriptor.homepage()).isEqualTo("http://example.dev");
            });
        PluginDescriptor bad = DescriptorParser.parse(base + "icon = \"icons/ab.gif\"\nhomepage = \"javascript:alert(1)\"\n");
        assertThat(bad.icon()).as("not an .svg or .png").isEmpty();
        assertThat(bad.homepage()).as("not http or https").isEmpty();
        assertThat(DescriptorParser.parse(base + "homepage = \"example.dev\"\n").homepage()).as("not absolute").isEmpty();
        PluginDescriptor minimal = DescriptorParser.parse(base);
        assertThat(minimal.icon()).isEmpty();
        assertThat(minimal.homepage()).isEmpty();
    }
```

In `jasper-sdk/src/test/java/dev/jasper/sdk/ui/AppearanceContractTest.java` and
`jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java`, replace `isEqualTo("0.9.1")` with
`isEqualTo("0.9.2")`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.DescriptorParserTest' :jasper-sdk:test`
Expected: FAIL. `DescriptorParserTest` does not compile (`icon()` does not exist), and the SDK version tests expect
0.9.2.

- [ ] **Step 3: Implement**

In `PluginDescriptor.java`, replace

```java
record PluginDescriptor(String id, String name, Version version, String entry, VersionRange sdk,
                        String description, String vendor, Set<String> capabilities, Set<String> exports,
                        List<Requirement> requires) {
```

with:

```java
record PluginDescriptor(String id, String name, Version version, String entry, VersionRange sdk,
                        String description, String vendor, Set<String> capabilities, Set<String> exports,
                        List<Requirement> requires, String icon, String homepage) {
```

In `DescriptorParser.java`:
- Replace the `KEYS` declaration with:

```java
    private static final Set<String> KEYS = Set.of("id", "name", "version", "entry", "sdk", "description",
        "vendor", "capabilities", "exports", "requires", "icon", "homepage");
    private static final System.Logger LOG = System.getLogger(DescriptorParser.class.getName());
```

- Replace

```java
        return new PluginDescriptor(id, name, version, entry, sdk, text(toml, "description", false),
            text(toml, "vendor", false), capabilities, exports, requires(toml, id));
```

  with:

```java
        return new PluginDescriptor(id, name, version, entry, sdk, text(toml, "description", false),
            text(toml, "vendor", false), capabilities, exports, requires(toml, id),
            icon(text(toml, "icon", false), id), homepage(text(toml, "homepage", false), id));
```

- Add after the `forbidden` method:

```java
    /** A path ending in .svg or .png; anything else is dropped, because an icon never stops a plugin loading. */
    private static String icon(String path, String id) {
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (path.isEmpty() || lower.endsWith(".svg") || lower.endsWith(".png")) return path;
        LOG.log(System.Logger.Level.WARNING, "Plugin " + id + ": icon must be an .svg or .png file; ignored: " + path);
        return "";
    }

    /** An absolute http or https URI; anything else is dropped with a warning. */
    private static String homepage(String text, String id) {
        if (text.isEmpty()) return "";
        try {
            var uri = new java.net.URI(text);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            if ((scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null) return text;
        } catch (java.net.URISyntaxException malformed) {
            // Falls through to the warning.
        }
        LOG.log(System.Logger.Level.WARNING, "Plugin " + id + ": homepage must be an http or https address; ignored: " + text);
        return "";
    }
```

In `PluginResolverTest.java` and `PluginCatalogTest.java`, each `new PluginDescriptor(...)` call gains two final
arguments, `""` and `""`. For example, in `PluginResolverTest` the call
`new PluginDescriptor(id, id, Version.parse(version), "x.Main", VersionRange.parse(sdk), "", "", capabilities, Set.of(), List.of(requires))`
becomes
`new PluginDescriptor(id, id, Version.parse(version), "x.Main", VersionRange.parse(sdk), "", "", capabilities, Set.of(), List.of(requires), "", "")`.
In `PluginCatalogTest`, the call ending in
`List.of(new PluginDescriptor.Requirement("dev.example.base", VersionRange.parse(">=1.0"), true)))` gets
`, "", ""` before its final `)`.

In `JasperSdk.java`, replace `public static final String VERSION = "0.9.1";` with
`public static final String VERSION = "0.9.2";`.

In `docs/plugin-authoring.md`, in the manifest example after the line
`exports = []                       # packages other plugins may use`, add:

```toml
icon = "icons/tool.svg"            # optional, SDK 0.9.2: an SVG or PNG in your jars, up to 256 KiB, shown in Settings › Plugins
homepage = "https://example.dev/tool"  # optional, SDK 0.9.2: an http or https link on your plugin's page
```

and after that code block's closing fence add this paragraph:

```markdown
`icon` and `homepage` need Jasper 0.9.2 or later (declare `sdk = ">=0.9.2, <0.10"`); an older Jasper rejects a
manifest with keys it does not know. An icon that is missing, too large or not an SVG or PNG, and a homepage that is
not an `http` or `https` address, are ignored: the plugin still loads, with the default icon and no link.
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests '*AppDocumentationTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-sdk jasper-app docs/plugin-authoring.md
git commit -m "Let plugin.toml name an icon and a homepage (SDK 0.9.2)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Icons, homepages and exports in the runtime's rows

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginIconFiles.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginCatalog.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/pluginmanager/PluginManagerPanelTest.java` (its `row` helper only)
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginIconFilesTest.java` (new)

**Interfaces:**
- Consumes: Task 1's `PluginDescriptor.icon()` and `homepage()`.
- Produces:
  - `public record PluginRuntime.PluginIcon(String name, byte[] data)`, with `boolean svg()`.
  - `PluginRuntime.Row` components appended after `dataDirectory`: `String homepage`, `List<String> exports`,
    `Optional<PluginIcon> icon`.
  - Package-private `PluginIconFiles.read(PluginCandidate)`, `PluginIconFiles.MAX_BYTES` and
    `PluginIconFiles.reads()`.

- [ ] **Step 1: Write the failing test**

Create `PluginIconFilesTest.java`:

```java
package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.testsupport.PluginJars;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginIconFilesTest {
    static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" viewBox=\"0 0 16 16\">"
        + "<rect width=\"16\" height=\"16\" rx=\"3\" fill=\"#3574F0\"/></svg>";
    @TempDir Path root;

    private PluginCandidate candidate(String id, String extraToml) throws Exception {
        String toml = PluginJars.descriptor(id, "1.0.0", "fix.Main") + extraToml;
        Path directory = root.resolve(id);
        Path jar = PluginJars.build(directory, "main.jar", toml, Map.of(), List.of());
        return new PluginCandidate(DescriptorParser.parse(toml), directory, List.of(jar), PluginCandidate.Origin.USER);
    }

    @Test void readsTheNamedIconOnceAndGivesNoneWhenMissingOrTooLarge() throws Exception {
        var withIcon = candidate("dev.example.icon", "icon = \"icons/tool.svg\"\n");
        PluginJars.addResource(withIcon.jars().get(0), "icons/tool.svg", SVG);
        int before = PluginIconFiles.reads();
        var icon = PluginIconFiles.read(withIcon).orElseThrow();
        assertThat(icon.svg()).isTrue();
        assertThat(new String(icon.data(), StandardCharsets.UTF_8)).isEqualTo(SVG);
        assertThat(PluginIconFiles.read(withIcon)).contains(icon);
        assertThat(PluginIconFiles.reads() - before).as("read once per plugin version").isEqualTo(1);

        assertThat(PluginIconFiles.read(candidate("dev.example.missing", "icon = \"icons/none.png\"\n"))).isEmpty();
        var large = candidate("dev.example.large", "icon = \"icons/big.svg\"\n");
        PluginJars.addResource(large.jars().get(0), "icons/big.svg", "x".repeat(PluginIconFiles.MAX_BYTES + 1));
        assertThat(PluginIconFiles.read(large)).isEmpty();
        assertThat(PluginIconFiles.read(candidate("dev.example.plain", ""))).isEmpty();
    }

    @Test void theRuntimesRowsCarryTheIconTheHomepageAndTheExports() throws Exception {
        Path user = root.resolve("user");
        Path jar = PluginJars.build(user.resolve("dev.example.shown/jars"), "main.jar",
            PluginJars.descriptor("dev.example.shown", "1.0.0", "fix.Main")
                + "icon = \"icons/shown.svg\"\nhomepage = \"https://example.dev/shown\"\nexports = [\"dev.example.shown.api\", \"dev.example.shown.a\"]\n",
            Map.of(), List.of());
        PluginJars.addResource(jar, "icons/shown.svg", SVG);
        var runtime = new AtomicReference<PluginRuntime>();
        onEdt(() -> {
            runtime.set(new PluginRuntime(new PluginRuntime.Options(null, user, null, false, root.resolve("plugins.toml"),
                root.resolve("plugins.lock")), new ActivityNotifier(new BuddyTestSupport().companion(), () -> { }),
                PluginRuntime.SettingsReports.IGNORED, new dev.jasper.app.contributions.Contributions(),
                AppContractTest.headlessWindows(), new dev.jasper.app.terminals.TerminalRegistry()));
            runtime.get().start(Map.of(), true);
        });
        try {
            var outcome = new CompletableFuture<PluginRuntime.Outcome>();
            onEdt(() -> runtime.get().snapshot(outcome::complete));
            var row = outcome.get(10, TimeUnit.SECONDS).snapshot().rows().stream()
                .filter(candidate -> candidate.id().equals("dev.example.shown")).findFirst().orElseThrow();
            assertThat(row.homepage()).isEqualTo("https://example.dev/shown");
            assertThat(row.exports()).containsExactly("dev.example.shown.a", "dev.example.shown.api");
            assertThat(row.icon()).hasValueSatisfying(icon -> assertThat(icon.svg()).isTrue());
        } finally {
            var pending = new AtomicReference<List<CompletableFuture<?>>>();
            onEdt(() -> pending.set(runtime.get().stop()));
            CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.PluginIconFilesTest'`
Expected: compilation FAILS: `PluginIconFiles`, `Row.homepage()` and friends do not exist.

- [ ] **Step 3: Implement**

In `PluginRuntime.java`:
- Replace the `Row` declaration's header and compact constructor

```java
    public record Row(String id, String name, String version, String description, String vendor, String origin,
                      String state, String reason, List<String> capabilities, List<String> unconsented,
                      List<String> requires, int errors, boolean enabled, boolean needsConsent, boolean canToggle,
                      boolean canRemove, boolean pendingRemoval, boolean pendingInstall, String pending,
                      Path directory, Path settingsFile, Path dataDirectory) {
        /** Copies the lists. */
        public Row {
            capabilities = List.copyOf(capabilities);
            unconsented = List.copyOf(unconsented);
            requires = List.copyOf(requires);
            Objects.requireNonNull(directory, "directory"); Objects.requireNonNull(settingsFile, "settingsFile"); Objects.requireNonNull(dataDirectory, "dataDirectory");
        }
    }
```

  with:

```java
    public record Row(String id, String name, String version, String description, String vendor, String origin,
                      String state, String reason, List<String> capabilities, List<String> unconsented,
                      List<String> requires, int errors, boolean enabled, boolean needsConsent, boolean canToggle,
                      boolean canRemove, boolean pendingRemoval, boolean pendingInstall, String pending,
                      Path directory, Path settingsFile, Path dataDirectory, String homepage, List<String> exports,
                      Optional<PluginIcon> icon) {
        /** Copies the lists. */
        public Row {
            capabilities = List.copyOf(capabilities);
            unconsented = List.copyOf(unconsented);
            requires = List.copyOf(requires);
            exports = List.copyOf(exports);
            Objects.requireNonNull(directory, "directory"); Objects.requireNonNull(settingsFile, "settingsFile"); Objects.requireNonNull(dataDirectory, "dataDirectory");
            Objects.requireNonNull(homepage, "homepage"); Objects.requireNonNull(icon, "icon");
        }
    }

    /**
     * A plugin's icon as its jar holds it; the Plugins page decodes it.
     *
     * @param name the path in the jar, ending in {@code .svg} or {@code .png}
     * @param data the file's bytes
     */
    public record PluginIcon(String name, byte[] data) {
        /** Copies the bytes. */
        public PluginIcon {
            Objects.requireNonNull(name, "name");
            data = data.clone();
        }

        /** @return a copy of the bytes */
        @Override public byte[] data() { return data.clone(); }

        /** @return whether the icon is an SVG rather than a PNG */
        public boolean svg() { return name.toLowerCase(java.util.Locale.ROOT).endsWith(".svg"); }

        @Override public boolean equals(Object other) {
            return other instanceof PluginIcon icon && icon.name.equals(name) && java.util.Arrays.equals(icon.data, data);
        }

        @Override public int hashCode() { return 31 * name.hashCode() + java.util.Arrays.hashCode(data); }

        @Override public String toString() { return "PluginIcon[" + name + ", " + data.length + " bytes]"; }
    }
```

- In the `Row` record's Javadoc, after its `@param dataDirectory` line, add:

```java
     * @param homepage the plugin's homepage, an http or https address; empty when it has none
     * @param exports the packages the plugin exports, sorted
     * @param icon the icon the plugin names in its manifest, as its jar holds it; empty when it names none or it cannot be read
```

- Add the import `java.util.Optional` if the file lacks it.

Create `PluginIconFiles.java`:

```java
package dev.jasper.app.plugins;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Reads the icon a plugin's manifest names from its jars, once per plugin version. Runs no plugin code. Any thread. */
final class PluginIconFiles {
    /** The largest icon read, in bytes. */
    static final int MAX_BYTES = 256 * 1024;
    private static final System.Logger LOG = System.getLogger(PluginIconFiles.class.getName());
    private static final Map<String, Optional<PluginRuntime.PluginIcon>> CACHE = new ConcurrentHashMap<>();
    private static final AtomicInteger READS = new AtomicInteger();

    private PluginIconFiles() { }

    /** The candidate's icon; empty when it names none, or the file is missing or larger than {@link #MAX_BYTES}. */
    static Optional<PluginRuntime.PluginIcon> read(PluginCandidate candidate) {
        String name = candidate.descriptor().icon();
        if (name.isEmpty()) return Optional.empty();
        String key = candidate.id() + "@" + candidate.descriptor().version() + "@" + candidate.directory() + "@" + name;
        return CACHE.computeIfAbsent(key, ignored -> load(candidate, name));
    }

    /** Test seam: how many icons have been read from jars. */
    static int reads() { return READS.get(); }

    private static Optional<PluginRuntime.PluginIcon> load(PluginCandidate candidate, String name) {
        READS.incrementAndGet();
        for (Path jar : candidate.jars()) {
            try (var file = new JarFile(jar.toFile())) {
                JarEntry entry = file.getJarEntry(name);
                if (entry == null) continue;
                try (var input = file.getInputStream(entry)) {
                    byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                    if (bytes.length > MAX_BYTES) {
                        LOG.log(System.Logger.Level.DEBUG, "The icon of " + candidate.id() + " is larger than 256 KiB; the default is used");
                        return Optional.empty();
                    }
                    return Optional.of(new PluginRuntime.PluginIcon(name, bytes));
                }
            } catch (IOException failure) {
                LOG.log(System.Logger.Level.DEBUG, "Could not read the icon of " + candidate.id() + " from " + jar, failure);
            }
        }
        LOG.log(System.Logger.Level.DEBUG, "No " + name + " in the jars of " + candidate.id() + "; the default icon is used");
        return Optional.empty();
    }
}
```

In `PluginCatalog.java`, in `compute`, replace

```java
                PluginSettingsFiles.folder(userDirectory, id), PluginSettingsFiles.file(userDirectory, id), PluginSettingsFiles.data(userDirectory, id)));
```

with:

```java
                PluginSettingsFiles.folder(userDirectory, id), PluginSettingsFiles.file(userDirectory, id), PluginSettingsFiles.data(userDirectory, id),
                descriptor.homepage(), List.copyOf(new TreeSet<>(descriptor.exports())), PluginIconFiles.read(shown)));
```

In `PluginManagerPanelTest.java`, in the `row(...)` helper, replace the last line
`java.nio.file.Path.of("/plugins/" + id), java.nio.file.Path.of("/plugins/" + id + "/" + id + ".toml"), java.nio.file.Path.of("/plugins/" + id + "/data"));`
with
`java.nio.file.Path.of("/plugins/" + id), java.nio.file.Path.of("/plugins/" + id + "/" + id + ".toml"), java.nio.file.Path.of("/plugins/" + id + "/data"), "", List.of(), java.util.Optional.empty());`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.pluginmanager.*' :jasper-app:javadoc verifyApplicationArchitecture`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app
git commit -m "Carry each plugin's icon, homepage and exports in the runtime's rows

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Plugin icons and the card list

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginIcons.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginCard.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginCardList.java`
- Create (test): `jasper-app/src/test/java/dev/jasper/app/pluginmanager/TestRows.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/pluginmanager/PluginIconsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/pluginmanager/PluginCardListTest.java`

**Interfaces:**
- Consumes: Task 2's `Row` fields, including `icon()`, and `PluginRuntime.PluginIcon`.
- Produces (package-private, used by Task 4):
  - `PluginIcons`:
    - `static Icon of(PluginRuntime.Row row, int size, boolean greyed)`
    - `static Optional<Icon> decode(PluginRuntime.PluginIcon icon, int size, String id)`
    - `static Icon grey(Icon icon)`
    - `static Icon fallback(int size)`
  - `PluginCard`:
    - `final JCheckBox enabled`, `final JLabel name`
    - `PluginRuntime.Row row()`, `String text()`, `void busy(boolean)`, `void setSelected(boolean)`
    - `static String badge(Row)`, `static boolean greyed(Row)`, `static boolean toggleable(Row)`
  - `PluginCardList`, constructed with
    `PluginCardList(Consumer<PluginRuntime.Row> toggle, Runnable selectionChanged, BiConsumer<Component, Point> menu)`,
    with:
    - `void show(List<Row>)`, `void search(String)`, `void busy(boolean)`, `void select(String id)`
    - `String selected()`, `Row selectedRow()`, `PluginCard card(String id)`
    - `List<String> listed()`, `List<String> groupTitles()`, `String emptyText()`
  - Test helper `TestRows`, a row builder.

- [ ] **Step 1: Write the failing tests**

Create `TestRows.java`:

```java
package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Builds {@link PluginRuntime.Row}s for view tests: by default an active, enabled, user-installed plugin. */
final class TestRows {
    private String id;
    private String name;
    private String origin = "Installed";
    private String state = "ACTIVE";
    private String reason = "";
    private String pending = "";
    private String homepage = "";
    private String vendor = "Example";
    private String description = "Does things";
    private boolean enabled = true, needsConsent, canToggle = true, canRemove = true, pendingRemoval, pendingInstall;
    private int errors;
    private List<String> capabilities = List.of(), unconsented = List.of(), requires = List.of(), exports = List.of();
    private Optional<PluginRuntime.PluginIcon> icon = Optional.empty();

    static TestRows plugin(String id, String name) {
        var rows = new TestRows();
        rows.id = id;
        rows.name = name;
        return rows;
    }

    /** "Bundled" and "Development" plugins cannot be removed. */
    TestRows origin(String value) { origin = value; canRemove = value.equals("Installed"); return this; }
    TestRows state(String value, String why) { state = value; reason = why; return this; }
    TestRows disabled() { enabled = false; return this; }
    TestRows needsReview(String... asked) {
        needsConsent = true; canToggle = false; state = "NEEDS_CONSENT";
        capabilities = List.of(asked); unconsented = List.of(asked);
        return this;
    }
    TestRows removing() { pendingRemoval = true; canToggle = false; enabled = false; pending = "Will be removed at restart"; return this; }
    TestRows installing() { pendingInstall = true; state = "NOT_LOADED"; pending = "Version 1.0.0 will be installed at restart"; return this; }
    TestRows pending(String text) { pending = text; return this; }
    TestRows vendor(String value) { vendor = value; return this; }
    TestRows description(String value) { description = value; return this; }
    TestRows homepage(String value) { homepage = value; return this; }
    TestRows errors(int value) { errors = value; return this; }
    TestRows capabilities(List<String> value) { capabilities = value; return this; }
    TestRows requires(List<String> value) { requires = value; return this; }
    TestRows exports(List<String> value) { exports = value; return this; }
    TestRows icon(PluginRuntime.PluginIcon value) { icon = Optional.of(value); return this; }

    PluginRuntime.Row row() {
        return new PluginRuntime.Row(id, name, "1.0.0", description, vendor, origin, state, reason, capabilities, unconsented,
            requires, errors, enabled, needsConsent, canToggle, canRemove, pendingRemoval, pendingInstall, pending,
            Path.of("/plugins/" + id), Path.of("/plugins/" + id + "/" + id + ".toml"), Path.of("/plugins/" + id + "/data"),
            homepage, exports, icon);
    }
}
```

Create `PluginIconsTest.java`:

```java
package dev.jasper.app.pluginmanager;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EdtTestExtension.class)
class PluginIconsTest {
    static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" viewBox=\"0 0 16 16\">"
        + "<rect width=\"16\" height=\"16\" rx=\"3\" fill=\"#3574F0\"/></svg>";

    static byte[] png() throws Exception {
        var image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) image.setRGB(x, y, 0xFF3574F0);
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test void svgAndPngIconsDrawAtTheAskedSizeAndABrokenOneDrawsTheDefault() throws Exception {
        var svg = TestRows.plugin("a.svg", "Svg").icon(new PluginRuntime.PluginIcon("icons/a.svg", SVG.getBytes(StandardCharsets.UTF_8))).row();
        var png = TestRows.plugin("a.png", "Png").icon(new PluginRuntime.PluginIcon("icons/a.png", png())).row();
        var broken = TestRows.plugin("a.bad", "Bad").icon(new PluginRuntime.PluginIcon("icons/a.svg", "not an image".getBytes(StandardCharsets.UTF_8))).row();
        var none = TestRows.plugin("a.none", "None").row();
        for (int size : new int[]{40, 64})
            for (var row : List.of(svg, png, broken, none)) {
                Icon icon = PluginIcons.of(row, size, false);
                assertThat(icon.getIconWidth()).as(row.id() + " at " + size).isEqualTo(UIScale.scale(size));
                assertThat(icon.getIconHeight()).as(row.id() + " at " + size).isEqualTo(UIScale.scale(size));
            }
        assertThat(PluginIcons.decode(svg.icon().orElseThrow(), 40, "a.svg")).isPresent();
        assertThat(PluginIcons.decode(png.icon().orElseThrow(), 40, "a.png")).isPresent();
        assertThat(PluginIcons.decode(broken.icon().orElseThrow(), 40, "a.bad")).as("a corrupt SVG").isEmpty();
        assertThat(PluginIcons.decode(new PluginRuntime.PluginIcon("icons/a.png", "not an image".getBytes(StandardCharsets.UTF_8)), 40, "a.bad"))
            .as("a corrupt PNG").isEmpty();
    }

    @Test void aGreyedIconKeepsItsSize() throws Exception {
        Icon svg = PluginIcons.fallback(40);
        Icon greySvg = PluginIcons.grey(svg);
        assertThat(greySvg).isNotSameAs(svg);
        assertThat(greySvg.getIconWidth()).isEqualTo(svg.getIconWidth());
        Icon image = PluginIcons.decode(new PluginRuntime.PluginIcon("icons/a.png", png()), 40, "a.png").orElseThrow();
        assertThat(image).isInstanceOf(ImageIcon.class);
        Icon greyImage = PluginIcons.grey(image);
        assertThat(greyImage).isNotSameAs(image);
        assertThat(greyImage.getIconWidth()).isEqualTo(image.getIconWidth());
    }
}
```

Create `PluginCardListTest.java`:

```java
package dev.jasper.app.pluginmanager;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EdtTestExtension.class)
class PluginCardListTest {
    private final List<String> events = new ArrayList<>();
    private final List<String> menus = new ArrayList<>();
    private int selections;
    private final PluginCardList list = new PluginCardList(row -> events.add("toggle:" + row.id()), () -> selections++,
        (component, point) -> menus.add("menu"));

    private void key(int code, int modifiers) {
        Object name = list.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(code, modifiers));
        list.getActionMap().get(name).actionPerformed(new ActionEvent(list, ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test void cardsAreGroupedSortedAndCounted() {
        list.show(List.of(
            TestRows.plugin("dev.jasper.remote", "Remote").origin("Bundled").row(),
            TestRows.plugin("dev.jasper.editor", "Editor").origin("Bundled").disabled().row(),
            TestRows.plugin("dev.example.zeta", "zeta tool").row(),
            TestRows.plugin("dev.example.alpha", "Alpha").row(),
            TestRows.plugin("dev.example.local", "Local").origin("Development").row()));
        assertThat(list.groupTitles()).containsExactly("Development", "User-installed (2 of 2 enabled)", "Bundled (1 of 2 enabled)");
        assertThat(list.listed()).containsExactly("Local  1.0.0", "Alpha  1.0.0", "zeta tool  1.0.0", "Editor  1.0.0", "Remote  1.0.0");
        assertThat(list.selected()).as("the first visible card").isEqualTo("dev.example.local");
        assertThat(selections).isPositive();
    }

    @Test void eachCardShowsAtMostOneBadgeInTheSpecsOrder() {
        list.show(List.of(
            TestRows.plugin("a.one", "One").removing().row(),
            TestRows.plugin("a.two", "Two").installing().row(),
            TestRows.plugin("a.three", "Three").pending("Enabled at restart").row(),
            TestRows.plugin("a.four", "Four").needsReview("terminal.inject").row(),
            TestRows.plugin("a.five", "Five").state("FAILED", "boom").row(),
            TestRows.plugin("a.six", "Six").state("SKIPPED", "requires a.base").row(),
            TestRows.plugin("a.seven", "Seven").row(),
            TestRows.plugin("a.eight", "Eight").needsReview().pending("Enabled at restart").row()));
        assertThat(list.listed()).containsExactly(
            "Eight  1.0.0 — Restart to apply", "Five  1.0.0 — Failed", "Four  1.0.0 — Needs review",
            "One  1.0.0 — Remove at restart", "Seven  1.0.0", "Six  1.0.0 — Skipped",
            "Three  1.0.0 — Restart to apply", "Two  1.0.0 — Install at restart");
    }

    @Test void theCheckboxShowsTheSnapshotAndAsksTheHandler() {
        var rows = List.of(TestRows.plugin("a.on", "On").row(), TestRows.plugin("a.review", "Review").needsReview("terminal.inject").row(),
            TestRows.plugin("a.fixed", "Fixed").removing().row());
        list.show(rows);
        list.card("a.on").enabled.doClick();
        assertThat(events).containsExactly("toggle:a.on");
        assertThat(list.card("a.on").enabled.isSelected()).as("what the snapshot says until the next one").isTrue();
        list.card("a.review").enabled.doClick();
        assertThat(events).containsExactly("toggle:a.on", "toggle:a.review");
        list.show(rows);
        assertThat(list.card("a.on").enabled.isSelected()).isTrue();
        assertThat(list.card("a.fixed").enabled.isEnabled()).isFalse();
        assertThat(list.card("a.fixed").enabled.getToolTipText()).isEqualTo("Will be removed at restart");
        list.busy(true);
        assertThat(list.card("a.on").enabled.isEnabled()).isFalse();
        list.busy(false);
        assertThat(list.card("a.on").enabled.isEnabled()).isTrue();
    }

    @Test void searchFiltersCardsHidesEmptyGroupsAndMovesTheSelection() {
        list.show(List.of(TestRows.plugin("dev.jasper.remote", "Remote").origin("Bundled").description("SSH and SFTP").row(),
            TestRows.plugin("dev.example.tool", "Tool").vendor("Acme").row()));
        list.select("dev.example.tool");
        list.search("ssh");
        assertThat(list.listed()).containsExactly("Remote  1.0.0");
        assertThat(list.groupTitles()).as("counts cover the whole group").containsExactly("Bundled (1 of 1 enabled)");
        assertThat(list.selected()).isEqualTo("dev.jasper.remote");
        list.search("ACME");
        assertThat(list.listed()).containsExactly("Tool  1.0.0");
        list.search("example.tool");
        assertThat(list.listed()).as("the id matches too").containsExactly("Tool  1.0.0");
        list.search("nothing like it");
        assertThat(list.listed()).isEmpty();
        assertThat(list.emptyText()).isEqualTo("No plugins match");
        assertThat(list.selected()).isNull();
        list.search("");
        list.show(List.of());
        assertThat(list.emptyText()).isEqualTo("No plugins installed");
    }

    @Test void theSelectionSurvivesARefreshAndTheKeysMoveToggleAndOpenTheMenu() {
        var a = TestRows.plugin("a.a", "A").row();
        var b = TestRows.plugin("a.b", "B").row();
        var c = TestRows.plugin("a.c", "C").row();
        list.show(List.of(a, b, c));
        list.select("a.b");
        list.show(List.of(a, b, c));
        assertThat(list.selected()).isEqualTo("a.b");
        key(KeyEvent.VK_DOWN, 0);
        assertThat(list.selected()).isEqualTo("a.c");
        key(KeyEvent.VK_DOWN, 0);
        assertThat(list.selected()).as("stays on the last card").isEqualTo("a.c");
        key(KeyEvent.VK_UP, 0);
        assertThat(list.selected()).isEqualTo("a.b");
        key(KeyEvent.VK_SPACE, 0);
        assertThat(events).containsExactly("toggle:a.b");
        key(KeyEvent.VK_F10, InputEvent.SHIFT_DOWN_MASK);
        key(KeyEvent.VK_CONTEXT_MENU, 0);
        assertThat(menus).hasSize(2);
        list.show(List.of(a, c));
        assertThat(list.selected()).as("the first card when the selected one is gone").isEqualTo("a.a");
    }

    @Test void pluginTextIsNeverMarkup() {
        list.show(List.of(TestRows.plugin("a.h", "<html><b>Bold").vendor("<html><i>Acme").row()));
        assertThat(list.listed()).containsExactly("<html><b>Bold  1.0.0");
        assertThat(list.card("a.h").name.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.pluginmanager.PluginIconsTest' --tests 'dev.jasper.app.pluginmanager.PluginCardListTest'`
Expected: compilation FAILS: `PluginIcons`, `PluginCard` and `PluginCardList` do not exist.

- [ ] **Step 3: Implement**

Create `PluginIcons.java`:

```java
package dev.jasper.app.pluginmanager;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.plugins.PluginRuntime;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.swing.GrayFilter;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/** Draws a plugin's icon from the bytes its row carries, or the default plugin logo. EDT only. */
final class PluginIcons {
    private static final String DEFAULT = "dev/jasper/app/icons/intellij/nodes/pluginLogo.svg";
    private static final System.Logger LOG = System.getLogger(PluginIcons.class.getName());

    private PluginIcons() { }

    /** The plugin's icon at {@code size} logical pixels, greyed on request; the default when it has none or it cannot be drawn. */
    static Icon of(PluginRuntime.Row row, int size, boolean greyed) {
        Icon icon = row.icon().flatMap(found -> decode(found, size, row.id())).orElseGet(() -> fallback(size));
        return greyed ? grey(icon) : icon;
    }

    /** IntelliJ's plugin logo; FlatLaf picks its dark variant under a dark theme. */
    static Icon fallback(int size) { return new FlatSVGIcon(DEFAULT, size, size, PluginIcons.class.getClassLoader()); }

    /** The icon drawn at {@code size}, or empty when its bytes are not a readable SVG or PNG. */
    static Optional<Icon> decode(PluginRuntime.PluginIcon icon, int size, String id) {
        try {
            if (icon.svg()) {
                var svg = new FlatSVGIcon(new ByteArrayInputStream(icon.data()));
                if (!svg.hasFound() || svg.getIconWidth() <= 0 || svg.getIconHeight() <= 0) throw new IOException("not an SVG image");
                return Optional.of(svg.derive(size, size));
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(icon.data()));
            if (image == null) throw new IOException("not a PNG image");
            int pixels = UIScale.scale(size);
            return Optional.of(new ImageIcon(image.getScaledInstance(pixels, pixels, Image.SCALE_SMOOTH)));
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.DEBUG, "Could not draw the icon of " + id + "; the default is used", failure);
            return Optional.empty();
        }
    }

    /** The same icon drawn greyed, for a plugin that is disabled or not running. */
    static Icon grey(Icon icon) {
        if (icon instanceof FlatSVGIcon svg) return svg.getDisabledIcon();
        if (icon instanceof ImageIcon image) return new ImageIcon(GrayFilter.createDisabledImage(image.getImage()));
        return icon;
    }
}
```

If `FlatSVGIcon` has no `InputStream` constructor in the FlatLaf version on the classpath, write the bytes to a temporary
`.svg` file and use `new FlatSVGIcon(file)`, deleting the file after `derive`. Record that as a deviation.

Create `PluginCard.java`:

```java
package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/** One plugin in the list: its icon, name, version and vendor, a badge when it needs attention, and its checkbox. EDT only. */
final class PluginCard extends JPanel {
    final JCheckBox enabled = new JCheckBox();
    final JLabel name;
    private final JLabel meta;
    private final JLabel badge;
    private final PluginRuntime.Row row;
    private boolean selected;

    PluginCard(PluginRuntime.Row row, Runnable toggle, Runnable select, Consumer<MouseEvent> popup) {
        super(new BorderLayout(10, 0));
        this.row = row;
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        add(new JLabel(PluginIcons.of(row, 40, greyed(row))), BorderLayout.WEST);
        name = PluginManagerPanel.plain(new JLabel(row.name()));
        meta = PluginManagerPanel.plain(new JLabel(row.vendor().isBlank() ? row.version() : row.version() + "  " + row.vendor()));
        badge = PluginManagerPanel.plain(new JLabel(badge(row)));
        badge.setVisible(!badge.getText().isEmpty());
        var text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        text.add(name);
        text.add(meta);
        text.add(badge);
        add(text, BorderLayout.CENTER);
        enabled.setOpaque(false);
        // The list is one focus stop: Space toggles the selected card, so the checkbox takes no focus of its own.
        enabled.setFocusable(false);
        enabled.setSelected(row.enabled());
        enabled.getAccessibleContext().setAccessibleName("Enable " + row.name());
        if (!toggleable(row)) enabled.setToolTipText(!row.pending().isEmpty() ? row.pending()
            : !row.reason().isEmpty() ? row.reason() : "This plugin cannot be enabled or disabled here.");
        // The checkbox shows the snapshot's state; the next snapshot says whether anything changed.
        enabled.addActionListener(event -> { enabled.setSelected(row.enabled()); toggle.run(); });
        add(enabled, BorderLayout.EAST);
        var mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { select.run(); if (event.isPopupTrigger()) popup.accept(event); }
            @Override public void mouseReleased(MouseEvent event) { if (event.isPopupTrigger()) popup.accept(event); }
        };
        addMouseListener(mouse);
        restyle();
        busy(false);
    }

    @Override public void updateUI() {
        super.updateUI();
        // Called by JPanel's constructor, before the labels exist.
        if (badge != null) restyle();
    }

    private void restyle() {
        Font base = UIManager.getFont("Label.font");
        if (base != null) {
            name.setFont(base.deriveFont(Font.BOLD));
            badge.setFont(base.deriveFont(base.getSize2D() - 1f));
        }
        meta.setForeground(UIManager.getColor("Label.disabledForeground"));
        Color warning = UIManager.getColor("Actions.Red");
        badge.setForeground(row.state().equals("FAILED") || row.state().equals("SKIPPED") ? warning
            : UIManager.getColor("Component.accentColor"));
        setSelected(selected);
    }

    PluginRuntime.Row row() { return row; }

    /** The card as a line: name, version and badge. */
    String text() { return row.name() + "  " + row.version() + (badge.getText().isEmpty() ? "" : " — " + badge.getText()); }

    void busy(boolean busy) { enabled.setEnabled(!busy && toggleable(row)); }

    void setSelected(boolean value) {
        selected = value;
        Color background = UIManager.getColor(value ? "List.selectionInactiveBackground" : "List.background");
        if (value && background == null) background = UIManager.getColor("List.selectionBackground");
        setBackground(background);
        setOpaque(value);
    }

    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

    /** The first badge that applies, or empty. */
    static String badge(PluginRuntime.Row row) {
        if (row.pendingRemoval()) return "Remove at restart";
        if (row.pendingInstall()) return "Install at restart";
        if (!row.pending().isEmpty()) return "Restart to apply";
        if (row.needsConsent()) return "Needs review";
        return switch (row.state()) {
            case "FAILED" -> "Failed";
            case "SKIPPED" -> "Skipped";
            default -> "";
        };
    }

    /** Disabled, or not running because it needs review, failed or was skipped. */
    static boolean greyed(PluginRuntime.Row row) {
        return !row.enabled() || row.needsConsent() || row.state().equals("FAILED") || row.state().equals("SKIPPED");
    }

    /** Whether the checkbox can act: toggle it, or review it first. */
    static boolean toggleable(PluginRuntime.Row row) { return row.canToggle() || row.needsConsent(); }
}
```

Create `PluginCardList.java`:

```java
package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.UIManager;

/**
 * The plugins as cards under group headers (Development, User-installed, Bundled), filtered by a search. One visible card
 * is selected. The list is one focus stop: Up and Down move, Space toggles, Shift+F10 or the menu key opens the card's
 * menu. EDT only.
 */
final class PluginCardList extends JPanel implements Scrollable {
    /** A header's title and its plugins, sorted by name. */
    record Group(String title, List<PluginRuntime.Row> rows) { }

    private final Consumer<PluginRuntime.Row> toggle;
    private final Runnable selectionChanged;
    private final BiConsumer<Component, Point> menu;
    private final List<PluginCard> cards = new ArrayList<>();
    private final List<String> titles = new ArrayList<>();
    private final JLabel empty = PluginManagerPanel.plain(new JLabel());
    private List<PluginRuntime.Row> rows = List.of();
    private String query = "";
    private String selected;
    private boolean busy;

    PluginCardList(Consumer<PluginRuntime.Row> toggle, Runnable selectionChanged, BiConsumer<Component, Point> menu) {
        this.toggle = toggle;
        this.selectionChanged = selectionChanged;
        this.menu = menu;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setFocusable(true);
        getAccessibleContext().setAccessibleName("Plugins");
        empty.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "jasper.up", () -> move(-1));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "jasper.down", () -> move(1));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "jasper.toggle", () -> {
            PluginCard card = card(selected);
            if (card != null && card.enabled.isEnabled()) card.enabled.doClick();
        });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_F10, InputEvent.SHIFT_DOWN_MASK), "jasper.menu", this::menuAtSelection);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0), "jasper.menu", this::menuAtSelection);
        rebuild();
    }

    private void bind(KeyStroke stroke, String name, Runnable action) {
        getInputMap(JComponent.WHEN_FOCUSED).put(stroke, name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    /** Replaces the plugins, keeping the selection and the search. */
    void show(List<PluginRuntime.Row> next) { rows = List.copyOf(next); rebuild(); }

    /** Shows only plugins whose name, vendor, description or id contains {@code text}, ignoring case. */
    void search(String text) { query = text == null ? "" : text.strip(); rebuild(); }

    void busy(boolean value) { busy = value; cards.forEach(card -> card.busy(value)); }

    /** Selects the visible card for {@code id}; does nothing when it is not shown. */
    void select(String id) {
        PluginCard card = card(id);
        if (card == null) return;
        selected = id;
        applySelection();
        scrollRectToVisible(card.getBounds());
        selectionChanged.run();
    }

    String selected() { return selected; }

    PluginRuntime.Row selectedRow() {
        PluginCard card = card(selected);
        return card == null ? null : card.row();
    }

    PluginCard card(String id) {
        if (id == null) return null;
        for (PluginCard card : cards) if (card.row().id().equals(id)) return card;
        return null;
    }

    List<String> listed() { return cards.stream().map(PluginCard::text).toList(); }

    List<String> groupTitles() { return List.copyOf(titles); }

    /** The text shown when no card is, or empty. */
    String emptyText() { return empty.getParent() == this ? empty.getText() : ""; }

    private void rebuild() {
        removeAll();
        cards.clear();
        titles.clear();
        for (Group group : groups(rows)) {
            List<PluginRuntime.Row> shown = group.rows().stream().filter(row -> matches(row, query)).toList();
            if (shown.isEmpty()) continue;
            titles.add(group.title());
            add(header(group.title()));
            for (PluginRuntime.Row row : shown) {
                var card = new PluginCard(row, () -> toggle.accept(row), () -> { requestFocusInWindow(); select(row.id()); },
                    event -> popup(event));
                card.setAlignmentX(0f);
                card.busy(busy);
                cards.add(card);
                add(card);
            }
        }
        if (cards.isEmpty()) {
            empty.setText(rows.isEmpty() ? "No plugins installed" : "No plugins match");
            empty.setAlignmentX(0f);
            add(empty);
        }
        if (card(selected) == null) selected = cards.isEmpty() ? null : cards.get(0).row().id();
        applySelection();
        revalidate();
        repaint();
        selectionChanged.run();
    }

    private JComponent header(String title) {
        var label = PluginManagerPanel.plain(new JLabel(title));
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        label.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
        var holder = new JPanel(new java.awt.BorderLayout()) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        holder.setOpaque(false);
        holder.setAlignmentX(0f);
        holder.add(label);
        return holder;
    }

    private void applySelection() { cards.forEach(card -> card.setSelected(card.row().id().equals(selected))); }

    private void move(int by) {
        int index = cards.indexOf(card(selected));
        int target = Math.max(0, Math.min(cards.size() - 1, index + by));
        if (!cards.isEmpty() && target != index) select(cards.get(target).row().id());
    }

    private void menuAtSelection() {
        PluginCard card = card(selected);
        if (card != null) menu.accept(card, new Point(12, card.getHeight() / 2));
    }

    private void popup(MouseEvent event) { menu.accept(event.getComponent(), event.getPoint()); }

    /** The groups in page order, each sorted by name ignoring case; empty groups are left out. */
    static List<Group> groups(List<PluginRuntime.Row> rows) {
        var result = new ArrayList<Group>();
        for (String origin : List.of("Development", "Installed", "Bundled")) {
            List<PluginRuntime.Row> members = rows.stream().filter(row -> row.origin().equals(origin))
                .sorted(Comparator.comparing(PluginRuntime.Row::name, String.CASE_INSENSITIVE_ORDER)).toList();
            if (members.isEmpty()) continue;
            long on = members.stream().filter(PluginRuntime.Row::enabled).count();
            String title = switch (origin) {
                case "Development" -> "Development";
                case "Installed" -> "User-installed (" + on + " of " + members.size() + " enabled)";
                default -> "Bundled (" + on + " of " + members.size() + " enabled)";
            };
            result.add(new Group(title, members));
        }
        return result;
    }

    static boolean matches(PluginRuntime.Row row, String query) {
        if (query.isBlank()) return true;
        String haystack = String.join("\n", row.name(), row.vendor(), row.description(), row.id()).toLowerCase(Locale.ROOT);
        return haystack.contains(query.toLowerCase(Locale.ROOT));
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return visible.height; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
```

`PluginManagerPanel.plain(...)` already exists as a private static method; make it package-private (`static <T extends javax.swing.JComponent> T plain(T component)`) so the new classes can use it.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.pluginmanager.*' verifyApplicationArchitecture`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app
git commit -m "Add plugin cards, their icons, groups, search and keys

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The details pane and the new Plugins page

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginDetails.java`
- Rewrite: `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginManagerPanel.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginManager.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsDialogPreview.java` (the `PluginManager.Hooks` call only)
- Rewrite: `jasper-app/src/test/java/dev/jasper/app/pluginmanager/PluginManagerPanelTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/pluginmanager/PluginManagerTest.java`

**Interfaces:**
- Consumes: Task 3's `PluginCardList`, `PluginCard`, `PluginIcons` and `TestRows`.
- Produces:
  - `PluginManagerPanel.Handlers` gains a last component, `Consumer<URI> browse`.
  - `PluginManager.Hooks` gains a last component, `Consumer<URI> browse`.
  - `PluginManagerPanel` test seams:
    - selection and list: `show`, `busy`, `select`, `selected`, `listed`, `groupTitles`, `card(String)`, `search(String)`
    - details: `primary()`, `menuLabels`, `clickMenu`, `gearLabels`, `clickGear`, `details`, `tabs()`,
      `homepageShown()`, `clickHomepage()`
    - messages: `banner`, `notice`, `message`, `bannerText`, `noticeText`, `messageText`, `bannerButtons`

- [ ] **Step 1: Write the failing tests**

Replace the whole of `PluginManagerPanelTest.java` with:

```java
package dev.jasper.app.pluginmanager;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EdtTestExtension.class)
class PluginManagerPanelTest {
    private final List<String> events = new ArrayList<>();
    private final List<String> opened = new ArrayList<>();
    private final List<URI> browsed = new ArrayList<>();
    private final PluginManagerPanel panel = new PluginManagerPanel(new PluginManagerPanel.Handlers(
        row -> events.add("toggle:" + row.id()), row -> events.add("review:" + row.id()), row -> events.add("remove:" + row.id()),
        row -> events.add("discard:" + row.id()), () -> events.add("install"),
        row -> opened.add("settings:" + row.id()), row -> opened.add("folder:" + row.id()), row -> opened.add("data:" + row.id()),
        browsed::add));

    private static PluginRuntime.Snapshot snapshot(PluginRuntime.Row... rows) { return new PluginRuntime.Snapshot(List.of(rows), true, false); }

    @Test void thePrimaryButtonAndTheMenuFollowTheSelectedPlugin() {
        panel.show(snapshot(
            TestRows.plugin("dev.example.active", "Active").errors(2).capabilities(List.of("terminal.inject", "made.up"))
                .requires(List.of("dev.example.base >=1.0.0")).exports(List.of("dev.example.active.api")).row(),
            TestRows.plugin("dev.example.bundled", "Bundled One").origin("Bundled")
                .state("SKIPPED", "requires dev.example.base, which is not available").row(),
            TestRows.plugin("dev.example.found", "Found").needsReview("terminal.inject").row(),
            TestRows.plugin("dev.example.doomed", "Doomed").removing().row(),
            TestRows.plugin("dev.example.staged", "Staged").installing().row()));
        panel.select("dev.example.active");
        assertThat(panel.primary().getText()).isEqualTo("Disable");
        assertThat(panel.menuLabels()).containsExactly("Open Settings", "Open Plugin Folder", "Open Data Folder", "-", "Remove…");
        assertThat(panel.details()).contains("Active", "1.0.0", "Example", "Does things", "Type into your terminals (terminal.inject)",
            "made.up", "dev.example.base >=1.0.0", "dev.example.active.api", "2 errors", java.nio.file.Path.of("/plugins/dev.example.active/data").toString());
        panel.select("dev.example.bundled");
        assertThat(panel.primary().getText()).isEqualTo("Disable");
        assertThat(panel.menuLabels()).containsExactly("Open Settings", "Open Plugin Folder", "Open Data Folder");
        assertThat(panel.details()).contains("requires dev.example.base, which is not available", "Bundled with Jasper");
        panel.select("dev.example.found");
        assertThat(panel.primary().getText()).isEqualTo("Review…");
        panel.select("dev.example.doomed");
        assertThat(panel.primary().isVisible()).isFalse();
        assertThat(panel.menuLabels()).endsWith("Keep");
        panel.select("dev.example.staged");
        assertThat(panel.menuLabels()).endsWith("Discard Install");

        panel.select("dev.example.found");
        panel.primary().doClick();
        panel.select("dev.example.active");
        panel.primary().doClick();
        panel.clickMenu("Remove…");
        panel.select("dev.example.staged");
        panel.clickMenu("Discard Install");
        panel.clickMenu("Open Settings");
        panel.clickMenu("Open Plugin Folder");
        panel.clickMenu("Open Data Folder");
        assertThat(panel.gearLabels()).containsExactly("Install from Zip…");
        panel.clickGear("Install from Zip…");
        assertThat(events).containsExactly("review:dev.example.found", "toggle:dev.example.active", "remove:dev.example.active",
            "discard:dev.example.staged", "install");
        assertThat(opened).containsExactly("settings:dev.example.staged", "folder:dev.example.staged", "data:dev.example.staged");
    }

    @Test void aCardsCheckboxReviewsOrTogglesAndTheListIsGroupedAndSearchable() {
        panel.show(snapshot(TestRows.plugin("dev.example.found", "Found").needsReview("terminal.inject").row(),
            TestRows.plugin("dev.example.on", "On").row(), TestRows.plugin("dev.jasper.remote", "Remote").origin("Bundled").row()));
        assertThat(panel.groupTitles()).containsExactly("User-installed (2 of 2 enabled)", "Bundled (1 of 1 enabled)");
        panel.card("dev.example.found").enabled.doClick();
        panel.card("dev.example.on").enabled.doClick();
        assertThat(events).containsExactly("review:dev.example.found", "toggle:dev.example.on");
        panel.search("remote");
        assertThat(panel.listed()).containsExactly("Remote  1.0.0");
        assertThat(panel.selected()).isEqualTo("dev.jasper.remote");
    }

    @Test void theHomepageLinkShowsOnlyForAnHttpOrHttpsAddress() {
        panel.show(snapshot(TestRows.plugin("a.web", "Web").homepage("https://example.dev/web").row(),
            TestRows.plugin("a.ftp", "Ftp").homepage("ftp://example.dev").row(), TestRows.plugin("a.none", "None").row()));
        panel.select("a.web");
        assertThat(panel.homepageShown()).isTrue();
        panel.clickHomepage();
        assertThat(browsed).containsExactly(URI.create("https://example.dev/web"));
        panel.select("a.ftp");
        assertThat(panel.homepageShown()).isFalse();
        panel.select("a.none");
        assertThat(panel.homepageShown()).isFalse();
    }

    @Test void theTabsHoldTheOverviewAndTheDetails() {
        panel.show(snapshot(TestRows.plugin("a.quiet", "Quiet").description("").row()));
        assertThat(panel.tabs().getTabCount()).isEqualTo(2);
        assertThat(panel.tabs().getTitleAt(0)).isEqualTo("Overview");
        assertThat(panel.tabs().getTitleAt(1)).isEqualTo("Details");
        assertThat(panel.details()).contains("No description.", "Capabilities", "None", "Requires", "Exports", "Locations",
            java.nio.file.Path.of("/plugins/a.quiet/a.quiet.toml").toString());
    }

    @Test void busyDisablesEveryControlAndTheSelectionSurvivesARefresh() {
        var rows = new PluginRuntime.Row[]{TestRows.plugin("dev.example.a", "A").row(), TestRows.plugin("dev.example.b", "B").row()};
        panel.show(snapshot(rows));
        panel.select("dev.example.b");
        panel.show(snapshot(rows));
        assertThat(panel.selected()).isEqualTo("dev.example.b");
        panel.busy(true);
        assertThat(panel.primary().isEnabled() || panel.card("dev.example.a").enabled.isEnabled() || panel.gear.isEnabled()).isFalse();
        panel.clickGear("Install from Zip…");
        assertThat(events).as("nothing runs while busy").isEmpty();
        panel.busy(false);
        assertThat(panel.primary().isEnabled() && panel.card("dev.example.a").enabled.isEnabled() && panel.gear.isEnabled()).isTrue();
        panel.show(snapshot());
        assertThat(panel.selected()).isNull();
        assertThat(panel.details()).isEqualTo("No plugins installed");
    }

    @Test void pluginTextIsNeverMarkup() {
        panel.show(snapshot(TestRows.plugin("a.h", "<html><b>Bold").vendor("<html><i>Acme").row()));
        assertThat(panel.card("a.h").name.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
        assertThat(panel.details()).contains("<html><b>Bold", "<html><i>Acme");
    }

    @Test void theBannerTheNoticeAndTheMessageAreIndependent() {
        List<String> clicks = new ArrayList<>();
        assertThat(panel.bannerText()).isEmpty();
        panel.banner("Restart Jasper to apply your changes.", List.of(new PluginManagerPanel.BannerAction("Restart Now", () -> clicks.add("restart"))));
        assertThat(panel.bannerText()).isEqualTo("Restart Jasper to apply your changes.");
        assertThat(panel.bannerButtons()).extracting(JButton::getText).containsExactly("Restart Now");
        panel.bannerButtons().get(0).doClick();
        assertThat(clicks).containsExactly("restart");
        panel.notice("Another Jasper process is still running.");
        panel.message("The zip holds no jar files", true);
        assertThat(panel.noticeText()).isEqualTo("Another Jasper process is still running.");
        assertThat(panel.messageText()).isEqualTo("The zip holds no jar files");
        panel.banner("", List.of());
        assertThat(panel.bannerText()).isEmpty();
        assertThat(panel.bannerButtons()).isEmpty();
        assertThat(panel.noticeText()).isNotEmpty();
    }

    @Test void theConsentViewSaysWhatIsAskedAndThatItIsNoSandbox() {
        List<String> decisions = new ArrayList<>();
        var view = new ConsentView("<html>Tool", "2.0.0", "Example", List.of("terminal.inject", "made.up"), true, "Install",
            () -> decisions.add("allow"), () -> decisions.add("cancel"));
        assertThat(view.text()).contains("<html>Tool 2.0.0", "Example", "replaces the installed version",
            "Type into your terminals (terminal.inject)", "made.up", "unrestricted code", "not a sandbox");
        assertThat(view.allow.getText()).isEqualTo("Install");
        view.allow.doClick(); view.cancel.doClick();
        assertThat(decisions).containsExactly("allow", "cancel");
        assertThat(new ConsentView("Quiet", "1.0.0", "", List.of(), false, "Allow and Enable", () -> { }, () -> { }).text())
            .contains("asks for no access to your terminals").doesNotContain("replaces");
    }
}
```

In `PluginManagerTest.java`:
- In `start`, add `, uri -> { }` as the last argument of `new PluginManager.Hooks(...)`, after `revealed::add`.
- Replace every `manager.panel().install.doClick()` with `manager.panel().clickGear("Install from Zip…")`.
- Replace `"dev.example.zipped  1.0.0 — Not loaded yet (restart to apply)"` with
  `"dev.example.zipped  1.0.0 — Install at restart"`.
- Replace `"dev.example.found  1.0.0 — Needs review"` with `"dev.example.found  1.0.0 — Needs review"`.
- Replace `manager.panel().review.doClick();` with `manager.panel().primary().doClick();`.
- In `removingAsksFirstAndTheOpenersGoThroughTheHooks`:
  - replace both `edt(() -> manager.panel().remove.doClick());` with `edt(() -> manager.panel().clickMenu("Remove…"));`;
  - replace `edt(() -> assertThat(manager.panel().remove.getText()).isEqualTo("Remove…"));` with
    `edt(() -> assertThat(manager.panel().menuLabels()).contains("Remove…"));`;
  - replace `until(() -> manager.panel().remove.getText().equals("Keep"));` with
    `until(() -> manager.panel().menuLabels().contains("Keep"));`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.pluginmanager.*'`
Expected: compilation FAILS: `Handlers` takes eight arguments, and `primary()`, `clickGear` and friends do not exist.

- [ ] **Step 3: Implement**

Create `PluginDetails.java`:

```java
package dev.jasper.app.pluginmanager;

import com.formdev.flatlaf.FlatClientProperties;
import dev.jasper.app.plugins.PluginRuntime;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/** The selected plugin: header, primary action and ▾ menu, then Overview and Details tabs. A passive view; EDT only. */
final class PluginDetails extends JPanel {
    final JButton primary = new JButton();
    final JButton more = new JButton("▾");
    final JButton homepage = new JButton("Plugin homepage ↗");
    final JTabbedPane tabs = new JTabbedPane();
    private final JLabel icon = new JLabel();
    private final JLabel name = PluginManagerPanel.plain(new JLabel(" "));
    private final JLabel vendor = PluginManagerPanel.plain(new JLabel());
    private final JLabel version = PluginManagerPanel.plain(new JLabel());
    private final JTextArea overview = area();
    private final JTextArea details = area();
    private final JPanel header = new JPanel(new BorderLayout(14, 0));
    private final PluginManagerPanel.Handlers handlers;
    private PluginRuntime.Row row;

    PluginDetails(PluginManagerPanel.Handlers handlers) {
        super(new BorderLayout(0, 12));
        this.handlers = handlers;
        setBorder(BorderFactory.createEmptyBorder(4, 16, 4, 4));
        homepage.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        more.setToolTipText("More actions");
        more.getAccessibleContext().setAccessibleName("More actions");
        var line = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        line.setOpaque(false);
        line.add(vendor);
        line.add(homepage);
        var actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        actions.setOpaque(false);
        actions.add(primary);
        actions.add(more);
        actions.add(Box.createHorizontalStrut(12));
        actions.add(version);
        var text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        for (JComponent part : List.of(name, line, actions)) {
            part.setAlignmentX(0f);
            text.add(part);
            text.add(Box.createVerticalStrut(6));
        }
        header.setOpaque(false);
        header.add(icon, BorderLayout.WEST);
        header.add(text, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);
        tabs.addTab("Overview", scroll(overview));
        tabs.addTab("Details", scroll(details));
        add(tabs, BorderLayout.CENTER);
        primary.addActionListener(event -> {
            if (row != null) (row.needsConsent() ? handlers.review() : handlers.toggle()).accept(row);
        });
        more.addActionListener(event -> { if (row != null) menu().show(more, 0, more.getHeight()); });
        homepage.addActionListener(event -> { if (row != null) homepage(row).ifPresent(handlers.browse()); });
        restyle();
        show(null, false);
    }

    @Override public void updateUI() {
        super.updateUI();
        // Called by JPanel's constructor, before the labels exist.
        if (version != null) restyle();
    }

    private void restyle() {
        Font base = UIManager.getFont("Label.font");
        if (base != null) name.setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 5f));
        Color secondary = UIManager.getColor("Label.disabledForeground");
        vendor.setForeground(secondary);
        version.setForeground(secondary);
        Color link = UIManager.getColor("Component.linkColor");
        if (link != null) homepage.setForeground(link);
    }

    private static JTextArea area() {
        var area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
        return area;
    }

    private static JScrollPane scroll(JTextArea area) {
        var scroll = new JScrollPane(area, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        return scroll;
    }

    /** Shows {@code shown}, or nothing when it is null; {@code busy} disables the actions. */
    void show(PluginRuntime.Row shown, boolean busy) {
        row = shown;
        header.setVisible(row != null);
        tabs.setVisible(row != null);
        if (row == null) {
            overview.setText("");
            details.setText("");
            return;
        }
        icon.setIcon(PluginIcons.of(row, 64, PluginCard.greyed(row)));
        name.setText(row.name());
        vendor.setText(row.vendor().isBlank() ? row.origin() : row.vendor());
        homepage.setVisible(homepage(row).isPresent());
        version.setText(row.version());
        primary.setVisible(PluginCard.toggleable(row));
        primary.setText(row.needsConsent() ? "Review…" : row.enabled() ? "Disable" : "Enable");
        primary.setEnabled(!busy);
        more.setEnabled(!busy);
        overview.setText(row.description().isBlank() ? "No description." : row.description());
        overview.setCaretPosition(0);
        details.setText(describe(row));
        details.setCaretPosition(0);
    }

    /** Everything the pane shows as text, for tests and search. */
    String text() {
        if (row == null) return "";
        return name.getText() + " " + version.getText() + "\n" + vendor.getText() + "\n\n" + overview.getText() + "\n\n" + details.getText();
    }

    /** The selected plugin's other actions: the three openers, then Remove…, Keep or Discard Install when it has one. */
    JPopupMenu menu() {
        var menu = new JPopupMenu();
        if (row == null) return menu;
        PluginRuntime.Row shown = row;
        item(menu, "Open Settings", () -> handlers.openSettings().accept(shown));
        item(menu, "Open Plugin Folder", () -> handlers.openFolder().accept(shown));
        item(menu, "Open Data Folder", () -> handlers.openData().accept(shown));
        String removal = removal(shown);
        if (!removal.isEmpty()) {
            menu.addSeparator();
            item(menu, removal, () -> (shown.pendingInstall() ? handlers.discard() : handlers.remove()).accept(shown));
        }
        return menu;
    }

    private static void item(JPopupMenu menu, String label, Runnable action) {
        var item = new JMenuItem(label);
        item.addActionListener(event -> action.run());
        menu.add(item);
    }

    List<String> menuLabels() {
        List<String> labels = new ArrayList<>();
        for (Component component : menu().getComponents())
            labels.add(component instanceof JMenuItem item ? item.getText() : "-");
        return labels;
    }

    void clickMenu(String label) {
        for (Component component : menu().getComponents())
            if (component instanceof JMenuItem item && item.getText().equals(label)) { item.doClick(); return; }
        throw new IllegalArgumentException("No menu item " + label);
    }

    static String removal(PluginRuntime.Row row) {
        if (row.pendingInstall()) return "Discard Install";
        if (row.canRemove()) return row.pendingRemoval() ? "Keep" : "Remove…";
        return "";
    }

    /** The plugin's homepage when it is an http or https address. */
    static Optional<URI> homepage(PluginRuntime.Row row) {
        if (row.homepage().isBlank()) return Optional.empty();
        try {
            URI uri = new URI(row.homepage());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null ? Optional.of(uri) : Optional.empty();
        } catch (java.net.URISyntaxException malformed) {
            return Optional.empty();
        }
    }

    static String stateLabel(String state) {
        return switch (state) {
            case "ACTIVE" -> "Active"; case "DISABLED" -> "Disabled"; case "NEEDS_CONSENT" -> "Needs review";
            case "SKIPPED" -> "Skipped"; case "FAILED" -> "Failed"; case "NOT_LOADED" -> "Not loaded yet";
            default -> state;
        };
    }

    static String describe(PluginRuntime.Row row) {
        var text = new StringBuilder("Status\n  ").append(stateLabel(row.state()));
        if (!row.reason().isEmpty()) text.append(": ").append(row.reason());
        if (!row.pending().isEmpty()) text.append("\n  ").append(row.pending());
        if (row.errors() > 0)
            text.append("\n  ").append(row.errors()).append(row.errors() == 1 ? " error" : " errors").append(" since Jasper started; see the log");
        switch (row.origin()) {
            case "Bundled" -> text.append("\n  Bundled with Jasper; disable it here, or remove it from the application image.");
            case "Development" -> text.append("\n  Loaded from --plugin-dir.");
            default -> text.append("\n  Installed by you.");
        }
        text.append("\n  Id: ").append(row.id());
        text.append("\n\nCapabilities");
        if (row.capabilities().isEmpty()) text.append("\n  None");
        for (String capability : row.capabilities())
            text.append("\n  • ").append(Capabilities.describe(capability)).append(row.unconsented().contains(capability) ? " — not reviewed" : "");
        text.append("\n\nRequires");
        if (row.requires().isEmpty()) text.append("\n  None");
        for (String requirement : row.requires()) text.append("\n  • ").append(requirement);
        text.append("\n\nExports");
        if (row.exports().isEmpty()) text.append("\n  None");
        for (String exported : row.exports()) text.append("\n  • ").append(exported);
        text.append("\n\nLocations");
        text.append("\n  Folder: ").append(row.directory());
        text.append("\n  Settings: ").append(row.settingsFile());
        text.append("\n  Data: ").append(row.dataDirectory());
        return text.toString();
    }
}
```

Replace the whole of `PluginManagerPanel.java` with:

```java
package dev.jasper.app.pluginmanager;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import dev.jasper.app.plugins.PluginRuntime;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The Plugins page's passive view: a search bar and gear menu, the restart banner, the plugin cards and the selected
 * plugin's details. It renders a snapshot and reports clicks; it decides nothing. EDT only.
 */
final class PluginManagerPanel extends JPanel {
    /** What the user can ask for. */
    record Handlers(Consumer<PluginRuntime.Row> toggle, Consumer<PluginRuntime.Row> review, Consumer<PluginRuntime.Row> remove,
                    Consumer<PluginRuntime.Row> discard, Runnable install, Consumer<PluginRuntime.Row> openSettings,
                    Consumer<PluginRuntime.Row> openFolder, Consumer<PluginRuntime.Row> openData, Consumer<URI> browse) { }

    /** One button of the banner. */
    record BannerAction(String label, Runnable run) { }

    final JTextField search = new JTextField(18);
    final JButton gear = new JButton();
    private final Handlers handlers;
    private final PluginDetails details;
    private final PluginCardList list;
    private final JPanel banner = new JPanel(new BorderLayout(12, 0));
    private final JLabel bannerLabel = plain(new JLabel());
    private final JPanel bannerActions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
    private final JLabel notice = plain(new JLabel());
    private final JLabel message = plain(new JLabel(" "));
    private boolean busy;

    /** Swing renders label text that starts with an html tag; plugin-supplied text must never be markup. */
    static <T extends JComponent> T plain(T component) {
        component.putClientProperty("html.disable", Boolean.TRUE);
        return component;
    }

    PluginManagerPanel(Handlers handlers) {
        super(new BorderLayout(0, 8));
        this.handlers = handlers;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        details = new PluginDetails(handlers);
        // The list reports its first selection while it is being built, before this field is set.
        list = new PluginCardList(row -> (row.needsConsent() ? handlers.review() : handlers.toggle()).accept(row),
            () -> { PluginCardList built = list(); if (built != null) details.show(built.selectedRow(), busy); },
            (at, point) -> { if (!busy) details.menu().show(at, point.x, point.y); });

        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search plugins");
        search.getAccessibleContext().setAccessibleName("Search plugins");
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { list.search(search.getText()); }
            @Override public void removeUpdate(DocumentEvent event) { list.search(search.getText()); }
            @Override public void changedUpdate(DocumentEvent event) { list.search(search.getText()); }
        });
        gear.setIcon(new FlatSVGIcon("dev/jasper/app/icons/intellij/general/settings.svg", 16, 16, PluginManagerPanel.class.getClassLoader()));
        gear.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        gear.setToolTipText("Plugin options");
        gear.getAccessibleContext().setAccessibleName("Plugin options");
        gear.addActionListener(event -> gearMenu().show(gear, 0, gear.getHeight()));
        var bar = new JPanel(new BorderLayout(8, 0));
        bar.add(search, BorderLayout.CENTER);
        bar.add(gear, BorderLayout.EAST);

        banner.add(bannerLabel, BorderLayout.CENTER);
        banner.add(bannerActions, BorderLayout.EAST);
        banner.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        banner.setVisible(false);
        notice.setVisible(false);
        var top = new JPanel(new BorderLayout());
        top.add(bar, BorderLayout.NORTH);
        top.add(banner, BorderLayout.CENTER);
        top.add(notice, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        var listScroll = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        listScroll.setMinimumSize(new Dimension(240, 100));
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, details);
        split.setDividerLocation(340);
        split.setResizeWeight(0.4);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
        add(message, BorderLayout.SOUTH);
        restyle();
        details.show(list.selectedRow(), false);
    }

    private PluginCardList list() { return list; }

    @Override public void updateUI() {
        super.updateUI();
        // Called by JPanel's constructor, before the labels exist.
        if (message != null) restyle();
    }

    private void restyle() {
        Font base = UIManager.getFont("Label.font");
        if (base != null) bannerLabel.setFont(base.deriveFont(Font.BOLD));
    }

    private JPopupMenu gearMenu() {
        var menu = new JPopupMenu();
        var install = new JMenuItem("Install from Zip…");
        install.setEnabled(!busy);
        install.addActionListener(event -> handlers.install().run());
        menu.add(install);
        return menu;
    }

    List<String> gearLabels() {
        List<String> labels = new ArrayList<>();
        for (Component component : gearMenu().getComponents()) if (component instanceof JMenuItem item) labels.add(item.getText());
        return labels;
    }

    /** Test seam: clicks the gear menu's item; a disabled item does nothing. */
    void clickGear(String label) {
        for (Component component : gearMenu().getComponents())
            if (component instanceof JMenuItem item && item.getText().equals(label)) { item.doClick(); return; }
        throw new IllegalArgumentException("No gear menu item " + label);
    }

    /** Replaces the plugins, keeping the selection and the search. */
    void show(PluginRuntime.Snapshot snapshot) { list.show(snapshot.rows()); }

    void busy(boolean value) {
        busy = value;
        list.busy(value);
        gear.setEnabled(!value);
        details.show(list.selectedRow(), value);
    }

    /** An empty text hides the banner. */
    void banner(String text, List<BannerAction> actions) {
        bannerLabel.setText(text);
        bannerActions.removeAll();
        for (BannerAction action : actions) {
            var button = new JButton(action.label());
            button.addActionListener(event -> action.run().run());
            bannerActions.add(button);
        }
        banner.setVisible(!text.isEmpty());
        banner.revalidate();
        banner.repaint();
    }

    void notice(String text) { notice.setText(text); notice.setVisible(!text.isEmpty()); }

    void message(String text, boolean error) {
        message.setText(text.isEmpty() ? " " : text);
        message.setForeground(UIManager.getColor(error ? "Actions.Red" : "Label.foreground"));
    }

    List<String> listed() { return list.listed(); }
    List<String> groupTitles() { return list.groupTitles(); }
    String selected() { return list.selected(); }
    void select(String id) { list.select(id); }
    PluginCard card(String id) { return list.card(id); }
    void search(String text) { search.setText(text); }
    JButton primary() { return details.primary; }
    JTabbedPane tabs() { return details.tabs; }
    List<String> menuLabels() { return details.menuLabels(); }
    void clickMenu(String label) { details.clickMenu(label); }
    boolean homepageShown() { return details.homepage.isVisible(); }
    void clickHomepage() { details.homepage.doClick(); }

    /** The selected plugin's details as text, or the list's empty text when nothing is selected. */
    String details() { return list.selectedRow() == null ? list.emptyText() : details.text(); }

    String bannerText() { return banner.isVisible() ? bannerLabel.getText() : ""; }
    String noticeText() { return notice.isVisible() ? notice.getText() : ""; }
    String messageText() { return message.getText().strip(); }

    List<JButton> bannerButtons() {
        List<JButton> buttons = new ArrayList<>();
        if (banner.isVisible()) for (Component component : bannerActions.getComponents()) buttons.add((JButton) component);
        return buttons;
    }
}
```

`details` is assigned before `list` in the constructor. The list's selection callback reads the field through `list()`,
which is null during the list's own construction; the constructor's last line shows the first selection instead.

In `PluginManager.java`:
- Replace the `Hooks` record header and its compact constructor

```java
    public record Hooks(Function<AuxiliarySurface, Optional<Path>> chooseZip, RestartFlow restarts, Runnable quit,
                        ResidentControl resident, boolean standaloneNotice, Executor worker,
                        java.util.function.Consumer<Path> openInEditor, java.util.function.Consumer<Path> reveal) {
        /** Rejects nulls. */
        public Hooks {
            Objects.requireNonNull(chooseZip); Objects.requireNonNull(restarts); Objects.requireNonNull(quit);
            Objects.requireNonNull(resident); Objects.requireNonNull(worker);
            Objects.requireNonNull(openInEditor); Objects.requireNonNull(reveal);
        }
    }
```

  with:

```java
    public record Hooks(Function<AuxiliarySurface, Optional<Path>> chooseZip, RestartFlow restarts, Runnable quit,
                        ResidentControl resident, boolean standaloneNotice, Executor worker,
                        java.util.function.Consumer<Path> openInEditor, java.util.function.Consumer<Path> reveal,
                        java.util.function.Consumer<java.net.URI> browse) {
        /** Rejects nulls. */
        public Hooks {
            Objects.requireNonNull(chooseZip); Objects.requireNonNull(restarts); Objects.requireNonNull(quit);
            Objects.requireNonNull(resident); Objects.requireNonNull(worker);
            Objects.requireNonNull(openInEditor); Objects.requireNonNull(reveal); Objects.requireNonNull(browse);
        }
    }
```

- Add ` * @param browse opens a plugin's homepage in the browser` to the `Hooks` Javadoc, after its `@param worker` line.
- In `attach`, replace
  `this::install, this::openSettings, this::openFolder, this::openData));` with
  `this::install, this::openSettings, this::openFolder, this::openData, hooks.browse()));`.

In `JasperApplication.java`, in `pluginManager()`, replace `editor::open, editor::reveal));` with
`editor::open, editor::reveal, this::browse));`.

In `SettingsDialogPreview.java`, in the `new PluginManager.Hooks(...)` call, replace `path -> { }, path -> { }));` with
`path -> { }, path -> { }, uri -> { }));`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.pluginmanager.*' --tests 'dev.jasper.app.settingsui.*' :jasper-app:javadoc verifyApplicationArchitecture`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app
git commit -m "Show plugins as cards with a details pane in Settings > Plugins

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Icons for the bundled plugins

**Files:**
- Create: `plugins/editor/src/main/resources/icons/editor.svg`, `plugins/history/src/main/resources/icons/history.svg`,
  `plugins/snippets/src/main/resources/icons/snippets.svg`, `plugins/vault/src/main/resources/icons/vault.svg`,
  `plugins/remote/src/main/resources/icons/remote.svg`
- Modify: every `plugins/*/src/main/resources/plugin.toml`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java`

**Interfaces:**
- Consumes: Task 1's manifest keys and Task 2's `Row.icon()` and `Row.homepage()`.

- [ ] **Step 1: Write the failing test**

In `BundledSamplePluginTest.java`, replace `"dev.jasper.sample", "0.2.0", "BUNDLED", "ACTIVE"` with
`"dev.jasper.sample", "0.2.1", "BUNDLED", "ACTIVE"`, and add this test after
`theStagedSampleLoadsFromItsJarAndItsActivityReachesBuddy`:

```java
    @Test void everyBundledPluginShipsAnIconAndRemoteNamesItsHomepage() throws Exception {
        Path staged = Path.of(System.getProperty("jasper.stagedPlugins"));
        var runtime = new AtomicReference<PluginRuntime>();
        onEdt(() -> {
            runtime.set(new PluginRuntime(new PluginRuntime.Options(staged, root.resolve("user"), null, false,
                root.resolve("plugins.toml"), root.resolve("plugins.lock")),
                new ActivityNotifier(new BuddyTestSupport().companion(), () -> { }), PluginRuntime.SettingsReports.IGNORED,
                new dev.jasper.app.contributions.Contributions(), AppContractTest.headlessWindows(),
                new dev.jasper.app.terminals.TerminalRegistry()));
            runtime.get().start(Map.of(), true);
        });
        try {
            var outcome = new CompletableFuture<PluginRuntime.Outcome>();
            onEdt(() -> runtime.get().snapshot(outcome::complete));
            var rows = outcome.get(10, TimeUnit.SECONDS).snapshot().rows();
            assertThat(rows).hasSize(6).allSatisfy(row -> assertThat(row.icon()).as(row.id()).isPresent());
            assertThat(rows).filteredOn(row -> row.id().equals("dev.jasper.remote")).singleElement()
                .satisfies(row -> assertThat(row.homepage()).isEqualTo("https://github.com/an0nn30/Jasper"));
        } finally {
            var pending = new AtomicReference<List<CompletableFuture<?>>>();
            onEdt(() -> pending.set(runtime.get().stop()));
            CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        }
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.BundledSamplePluginTest'`
Expected: FAIL. No bundled plugin names an icon yet, and the Sample is still 0.2.0.

- [ ] **Step 3: Ship the icons**

Run from the worktree root:

```bash
I=jasper-app/src/main/resources/dev/jasper/app/icons/intellij
mkdir -p plugins/editor/src/main/resources/icons plugins/history/src/main/resources/icons plugins/snippets/src/main/resources/icons plugins/vault/src/main/resources/icons plugins/remote/src/main/resources/icons
cp "$I/fileTypes/text.svg" plugins/editor/src/main/resources/icons/editor.svg
cp "$I/general/history.svg" plugins/history/src/main/resources/icons/history.svg
cp "$I/nodes/textArea.svg" plugins/snippets/src/main/resources/icons/snippets.svg
cp "$I/nodes/locked.svg" plugins/vault/src/main/resources/icons/vault.svg
cp "$I/nodes/ssh.svg" plugins/remote/src/main/resources/icons/remote.svg
```

These are IntelliJ Platform icons, which the application already bundles under Apache-2.0. The Sample uses the
`dev/jasper/sample/flask.svg` it already ships.

In each `plugin.toml`, set `version` and `sdk` to these values, and add the `icon` line (and Remote's `homepage` line)
directly after `vendor = "Jasper"`:

| Plugin | `version` | `sdk` | Lines to add after `vendor` |
|---|---|---|---|
| editor | `"0.3.1"` | `">=0.9.2, <0.10"` | `icon = "icons/editor.svg"` |
| history | `"0.2.1"` | `">=0.9.2, <0.10"` | `icon = "icons/history.svg"` |
| remote | `"0.6.1"` | `">=0.9.2, <0.10"` | `icon = "icons/remote.svg"` and `homepage = "https://github.com/an0nn30/Jasper"` |
| sample | `"0.2.1"` | `">=0.9.2, <0.10"` | `icon = "dev/jasper/sample/flask.svg"` |
| snippets | `"0.2.1"` | `">=0.9.2, <0.10"` | `icon = "icons/snippets.svg"` |
| vault | `"0.3.1"` | `">=0.9.2, <0.10"` | `icon = "icons/vault.svg"` |

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*' verifyPluginArchitecture`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add -A plugins jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java
git commit -m "Give each bundled plugin an icon, and Remote a homepage

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Preview, docs and the full check

**Files:**
- Modify: `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsDialogPreview.java`
- Modify: `jasper-app/build.gradle.kts` (the `settingsPreview` task)
- Modify: `docs/configuration.md`, `docs/STATUS.md`, and this plan's status banner

**Interfaces:**
- Consumes: everything above; `PluginsPage.highlight(String)` selects a plugin by name.

- [ ] **Step 1: The preview's plugins**

In `jasper-app/build.gradle.kts`, in the `settingsPreview` task, after `dependsOn(tasks.testClasses)` add:

```kotlin
    dependsOn(stagePlugins)
    systemProperty("jasper.stagedPlugins", layout.buildDirectory.dir("plugins").get().asFile.absolutePath)
```

In `SettingsDialogPreview.java`:
- After the existing `PluginJars.build(home.resolve("plugins/dev.example.preview/jars"), ...)` statement, add:

```java
        Path tidyJar = PluginJars.build(home.resolve("plugins/dev.example.tidy/jars"), "main.jar",
            PluginJars.descriptor("dev.example.tidy", "1.2.0", "fix.tidy.Main").replace("name = \"dev.example.tidy\"", "name = \"Tidy Tabs\"")
                + "description = \"Groups and tidies terminal tabs by project.\"\nvendor = \"Example Labs\"\n"
                + "icon = \"icons/tidy.svg\"\nhomepage = \"https://example.dev/tidy\"\n",
            Map.of("fix.tidy.Main", PluginJars.emptyPlugin("fix.tidy", "Main")), List.of());
        PluginJars.addResource(tidyJar, "icons/tidy.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\""
            + " viewBox=\"0 0 16 16\"><rect x=\"1\" y=\"1\" width=\"14\" height=\"14\" rx=\"3\" fill=\"#3574F0\"/>"
            + "<rect x=\"4\" y=\"5\" width=\"8\" height=\"1.5\" fill=\"#FFFFFF\"/><rect x=\"4\" y=\"9\" width=\"5\" height=\"1.5\" fill=\"#FFFFFF\"/></svg>");
        PluginJars.build(home.resolve("plugins/dev.example.broken/jars"), "main.jar",
            PluginJars.descriptor("dev.example.broken", "0.3.0", "fix.broken.Missing").replace("name = \"dev.example.broken\"", "name = \"Broken Example\""),
            Map.of(), List.of());
        Files.writeString(home.resolve("plugins.toml"), "version = 1\n\n[plugins.\"dev.example.tidy\"]\nenabled = true\nconsented = []\n\n"
            + "[plugins.\"dev.example.broken\"]\nenabled = true\nconsented = []\n");
```

- In the `new PluginRuntime.Options(null, home.resolve("plugins"), ...)` call, replace the first argument `null` with
  `Path.of(System.getProperty("jasper.stagedPlugins"))`.
- Replace the `await(...)` condition's `term.contains("dev.example.preview")` with `term.contains("dev.example.tidy")`.
- Replace

```java
                    render(holder[0], output.resolve("plugins-" + suffix));
```

  with:

```java
                    dialog.pages().stream().filter(page -> page.id().equals("plugins")).findFirst().orElseThrow().highlight("Tidy Tabs");
                    render(holder[0], output.resolve("plugins-" + suffix));
                    tabbed(holder[0]).setSelectedIndex(1);
                    render(holder[0], output.resolve("plugins-details-" + suffix));
```

- Add this helper beside `render`:

```java
    private static javax.swing.JTabbedPane tabbed(java.awt.Component root) {
        var found = findTabbed(root);
        if (found == null) throw new IllegalStateException("No tabbed pane on the Plugins page");
        return found;
    }

    private static javax.swing.JTabbedPane findTabbed(java.awt.Component component) {
        if (component instanceof javax.swing.JTabbedPane tabs) return tabs;
        if (component instanceof java.awt.Container container)
            for (var child : container.getComponents()) {
                var found = findTabbed(child);
                if (found != null) return found;
            }
        return null;
    }
```

- [ ] **Step 2: Render and look**

Run: `./gradlew :jasper-app:settingsPreview`
Expected: BUILD SUCCESSFUL, with `plugins-*.png` and `plugins-details-*.png` in `jasper-app/build/settings-preview/`
for each theme and size.

Open `plugins-jasper-dark-0.png`, `plugins-intellij-light-0.png` and `plugins-details-jasper-dark-18.png` with the image
reader, and check:
- the groups read "User-installed (… enabled)" and "Bundled (6 of 6 enabled)";
- the bundled cards show their icons;
- Tidy Tabs is selected, with its blue icon, "Example Labs", "Plugin homepage ↗" and a Disable button with ▾;
- Broken Example shows the "Failed" badge and a greyed default icon;
- the Details render shows the Details tab.

Fix anything that overlaps or is cut off, and describe what you saw in your report.

- [ ] **Step 3: Docs**

In `docs/configuration.md`, in the Plugins section, replace the sentence that starts `Right-click a plugin in Settings › Plugins`
and ends `when the plugin is\nnot running).` with:

```markdown
Settings › Plugins (File → Manage Plugins…) lists each plugin as a card, grouped as Development, User-installed and
Bundled; tick or untick a card to enable or disable it (after a restart). Select a card for its details, and use the
▾ menu beside Enable/Disable (or right-click the card) and choose Open Settings to go to its page in Settings (or to
its file, when the plugin is not running).
```

Add at the top of `docs/STATUS.md`'s "Current state" list:

```markdown
- **Settings fixes and the Plugins page** (`claude/settings-polish`, 2026-09-29; spec
  `docs/superpowers/specs/2026-09-29-jasper-plugins-page-design.md`, plan
  `docs/superpowers/plans/2026-09-29-jasper-plugins-page.md`):
  - Settings fixes: the theme picker enables Apply again, since every dialog replays the theme when it opens, which
    gave the editable combo a new editor field. Narrow pages no longer squash lists, tables and fields: descriptions
    wrap, editors keep their size, and a page too narrow for its editors scrolls sideways.
  - Settings › Plugins is IntelliJ-style: cards grouped Development, User-installed and Bundled, with icons, badges and
    enable checkboxes, a search field and a gear menu (Install from Zip…), and a details pane with the icon, vendor,
    homepage link, Enable/Disable or Review… with a ▾ menu, and Overview and Details tabs.
  - `plugin.toml` gains optional `icon` and `homepage` (SDK 0.9.2); every bundled plugin ships an icon and requires
    `>=0.9.2, <0.10`.
  - `./gradlew check`: N tests, 0 failures, S skipped.
  - GUI checks pending with the user:
    - the theme picker enabling Apply;
    - Terminal › Shell and Tools › Shell History at a narrow width;
    - cards and groups;
    - toggling from a card, and review from a checkbox;
    - the ▾ and right-click menus;
    - search;
    - the homepage link;
    - icons in dark and light.
  - Deviations: see the plan's status banner. Not pushed; not merged.
```

- [ ] **Step 4: The full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Read the counts from `*/build/test-results/test/*.xml` and
`plugins/*/build/test-results/test/*.xml`, and put them in place of N and S above.

Set this plan's `**Status:**` line to
`**Status:** Implemented on claude/settings-polish; ./gradlew check green, N tests, 0 failures (S skipped).` with the
same counts, and under it list `Deviations so far:` (or "none").

Run: `./gradlew :jasper-app:test --tests '*AppDocumentationTest*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add -A jasper-app docs
git commit -m "Preview and document the new Plugins page

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
