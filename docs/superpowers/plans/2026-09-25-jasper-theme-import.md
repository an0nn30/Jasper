# IntelliJ Theme Import (Plan 3a) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Plan written 2026-09-25 and awaiting user review; not started. This is plan 3a of the spec. Plan 3b, the bundled set, follows it.

**Goal:** Let Jasper use any JetBrains Marketplace theme plugin dropped into `<home>/themes/`, chosen by name with `ui.theme.name`, with the theme's editor-scheme console colours as the terminal palette.

**Architecture:** Four new classes in `dev.jasper.app.appearance` do the work:
- `ThemeCatalog` lists the built-in themes and the plugin archives in the themes folder.
- `ThemeArchive` reads a plugin `.jar`, or a Marketplace `.zip` of `lib/*.jar`, in place.
- `EditorScheme` turns an editor scheme into a `Palette`, using IntelliJ's vendored Default and Darcula schemes as parents.
- `SafeXml` parses the untrusted XML.

`Theme` gains a source: a bundled resource or an archive. Any theme then installs through the existing `ThemeManager` path. `ConfigService.watchWith` runs the catalog's scan on the configuration worker, first at start-up and then after every read. `ConfigurationController` resolves `ui.theme.name` against the latest scan and shows the catalog's problems as config warnings. The `Appearance` light/dark enum, `ui.theme.variant` and the View-menu Light/Dark items are removed.

**Tech Stack:** Java 25 on JBR 25, Swing, FlatLaf 3.7 (`IntelliJTheme`, `com.formdev.flatlaf.json.Json`), `java.util.zip`, the JDK DOM parser, JUnit 5 and AssertJ. Build with `./gradlew`.

**Spec:** `docs/superpowers/specs/2026-09-25-jasper-intellij-theme-import-design.md` (sections 1–5 and 7–9; section 6 is plan 3b).

**Planning decisions**, all consistent with the spec; record any other deviation in the status line above:
1. **One file for the parent schemes.** At the pinned intellij-community commit, IntelliJ keeps its `Default` and `Darcula` editor schemes in one file, `platform/platform-resources/src/DefaultColorSchemesManager.xml` (115,228 bytes, SHA-256 `e8eef86e79a25de2ab827c6535280019c1428afe7ff4688af94a7a50e3c9ea08`). The spec names `Default.xml` and `Darcula.xml`; Jasper vendors this one file instead. The controller downloaded it on 2026-09-25 with the user's approval and checked that both schemes carry all 20 console keys.
2. **A scheme can be named, not only a path.** A theme's `editorScheme` may be a scheme name rather than a path, as IntelliJ Light's own `"IntelliJ Light"` is. A name Jasper has (`Darcula`, `Default`) uses the vendored scheme. Any other name falls back to Jasper's palette with a warning.
3. **Start-up waits for the first scan.** It waits on the configuration worker, for at most 10 s, so the first window already shows the saved theme. Later scans run with each configuration read. Bootstrap is unchanged: `JasperApplication` creates the catalog beside the config file.
4. **`WindowContent.selectTheme(Theme)` stays.** It already exists and only tests and previews use it. It remains the workspace package's way to switch themes, and it now configures the controller. There is no temporary theme override any more, so a config reload re-applies the configured theme.
5. **`ui.theme.name` accepts any string.** `ConfigLoader` checks only the type. An empty name, or one no theme has, gets the resolution warning.
6. **Where problems are reported.**
   - A catalog problem has the archive as its file and key `""`.
   - The unknown-name warning has the config file, key `ui.theme.name` and no position.
7. **Scheme field and colour fallbacks.**
   - A scheme attribute that is missing a field inherits that field from the parent scheme. IntelliJ would treat it as unset, but inheriting gives a complete palette.
   - A colour that isn't hexadecimal gets a warning and is ignored, and the next fallback applies.

## Global Constraints

- Java 25 on the JetBrains Runtime 25. Use `./gradlew`, never a system `gradle`. Never launch the GUI (`:jasper-app:run`, `bench`).
- MVP rule: `ui.theme.variant`, the `Appearance` enum, the View → Appearance Light and Dark items and `view.appearance.light` / `view.appearance.dark` are removed outright. There are no aliases, deprecations or migration notes.
- `ui.theme.name` is compared with each theme's `.theme.json` `name`, ignoring case (`Locale.ROOT`) and surrounding whitespace. The default is `"Jasper Dark"`. The built-ins are named `"IntelliJ Light"` and `"Jasper Dark"`.
- The themes folder is `themes` in the directory holding `config.toml`. Jasper creates it at start-up if it is missing.
- Archives are read in place: nothing is extracted, loaded, run or written. The caps are:
  - an archive on disk: 64 MiB;
  - each file read: 4 MiB;
  - each nested jar: 32 MiB;
  - nested jars per `.zip`: 16;
  - entries per archive: 10,000.
- `plugin.xml` and scheme XML are untrusted. DOCTYPE, external entities, external schemas and XInclude are all disabled.
- Built-in themes keep Jasper's palettes. Under `terminal = "match"`, an installed theme uses its editor-scheme palette, or Jasper's palette by `dark` when it has none. `"light"` and `"dark"` always use Jasper's palettes.
- Every theme problem is a configuration WARNING, and none blocks start-up. Start-up falls back to Jasper Dark; a live reload keeps the theme showing.
- New code lives in `dev.jasper.app.appearance`. The app package graph stays acyclic (`verifyApplicationArchitecture`). The plugin SDK doesn't change.
- Source hygiene: no raw control, private-use or unpaired-surrogate characters in Java. Run the AGENTS.md Python check.
- Commits end with:
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Review Focus

1. **A real Marketplace plugin differs from the fixtures.** Its `plugin.xml` may use `xmlns:xi`, `xi:include`, `depends`, a provider path without a leading `/`, or extra attributes. Either the theme is found, or the warning says plainly that the plugin provides no theme files. Task 4 covers both provider forms, a relative path, and an `xi:include`/`xmlns` plugin.
2. **The themes folder is unusable** because a file named `themes` sits there or it can't be read. The result is one warning, and the built-in themes still work. Task 5 covers this in `theFolderIsCreatedAndAnUnusableOneIsAWarning`.
3. **The active theme's archive is deleted or broken before a live reload.** The theme showing stays, and the warnings name the unknown theme and the broken file. Task 7 covers this in `reloadRescansTheFolderSoThemesCanBeAddedAndRemovedLive`.
4. **Several large archives in the folder.** A rescan must not re-read unchanged files, and a slow folder can't hang start-up for more than 10 s. Task 5 covers the first in `aRescanRereadsOnlyFilesWhoseSizeOrTimeChanged`, and Task 7 bounds start-up.
5. **A name typed with different case or spaces, or left empty.** Differently-typed names match. An empty name gets the unknown-name warning listing the available names. Task 5 covers the first in `theBuiltInCatalogFindsBuiltInThemesByNameIgnoringCaseAndSpaces`, and Task 7 the second in `anUnknownNameAtStartUpUsesJasperDark`.

---

## File Structure

- **Vendored:**
  - `jasper-app/src/main/resources/dev/jasper/app/themes/intellij/DefaultColorSchemesManager.xml` (new);
  - `assets.tsv` and `SOURCE.md` beside it;
  - `tools/themes/fetch-intellij-themes.py`.
- **New in `dev.jasper.app.appearance`:**
  - `SafeXml.java` (untrusted XML);
  - `EditorScheme.java` (scheme to `Palette`);
  - `ThemeArchive.java` (plugin reading);
  - `ThemeCatalog.java` (folder, precedence, cache).
- **Changed in `dev.jasper.app.appearance`:**
  - `ThemeLoader.java` (parent fallback, loader fixes, warnings);
  - `Theme.java` (source; no `Appearance`);
  - `ThemeManager.java` (`load(Source)`);
  - `ThemeState.java`;
  - `ThemeController.java`.
- **Config:**
  - `ConfigSnapshot.java` (`theme`);
  - `ConfigLoader.java` (`ui.theme.name`);
  - `ConfigService.java` (`watchWith`);
  - `ConfigTemplate.java`;
  - `config.example.toml`;
  - delete `Appearance.java`.
- **App:**
  - `application/ConfigurationController.java`;
  - `application/JasperApplication.java`;
  - `workspace/WindowContent.java`, `WindowChrome.java` and `WindowCommands.java`;
  - `windows/NativeShells.java`.
- **Tests (new):**
  - `appearance/ThemeFixtures.java` (test support);
  - `EditorSchemeTest.java`, `ThemeArchiveTest.java` and `ThemeCatalogTest.java`;
  - `workspace/InstalledThemesTest.java`;
  - `application/JasperApplicationThemesTest.java`.
- **Tests (changed):**
  - `IntellijThemesTest`, `PackagedResourcesTest`, `ThemeLoaderTest`, `ThemeManagerTest`, `ThemeStateTest`, `ThemeTestSupport`, `ConfigServiceTest` and `ConfigurationTestSupport`;
  - every test the Task 6 migration script rewrites;
  - the hand-edited `ThemeControllerTest`, `WindowChromeTest`, `TerminalColorsTest`, `ConfigurationControllerTest`, `ExpandedConfigTest`, `TerminalColorsThemeTest` and `ConfigLoaderTest`.
- **Docs:**
  - `docs/themes.md` (new);
  - `docs/configuration.md`, `docs/README.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/app-refactor-verification.md` and `docs/STATUS.md`.

---

### Task 1: Vendor IntelliJ's default editor schemes

**Files:**
- Modify: `tools/themes/fetch-intellij-themes.py`
- Create (by the tool): `jasper-app/src/main/resources/dev/jasper/app/themes/intellij/DefaultColorSchemesManager.xml`
- Modify: `jasper-app/src/main/resources/dev/jasper/app/themes/intellij/assets.tsv` (by the tool), `SOURCE.md`
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/IntellijThemesTest.java`, `jasper-app/src/test/java/dev/jasper/app/architecture/PackagedResourcesTest.java`
- Modify: `docs/app-refactor-verification.md`

**Interfaces:**
- Produces: the classpath resource `dev/jasper/app/themes/intellij/DefaultColorSchemesManager.xml`. Its root is `<component name="DefaultColorSchemesManager">`, holding `<scheme name="Default">` and `<scheme name="Darcula" parent_scheme="Default">`. Task 3 reads it.

- [ ] **Step 1: Write the failing test**

In `IntellijThemesTest.java`, replace the `PINNED` map and the manifest test with:

```java
    private static final Map<String, String> PINNED = Map.of(
        "Light.theme.json", "429151594b8a013938c3c66dd3a5121702182c028ad87ae1e68bc4243cc1c68d",
        "intellijlaf.theme.json", "4450e2a85381b7b644c73bc9ec21f898aa55b184fc80f109a43ae9ff67af21ba",
        "darcula.theme.json", "858efbd6afdb12f26b4757c4ff7b1bccb8bdefc286797fa6344769496b6c59cb",
        "DefaultColorSchemesManager.xml", "e8eef86e79a25de2ab827c6535280019c1428afe7ff4688af94a7a50e3c9ea08");
    private static final Map<String, String> SOURCES = Map.of(
        "Light.theme.json", "platform/platform-resources/src/themes/Light.theme.json",
        "intellijlaf.theme.json", "platform/platform-resources/src/themes/intellijlaf.theme.json",
        "darcula.theme.json", "platform/platform-resources/src/themes/darcula.theme.json",
        "DefaultColorSchemesManager.xml", "platform/platform-resources/src/DefaultColorSchemesManager.xml");

    @Test void manifestPinsTheClassicLightChainAndTheDefaultSchemesToTheirUpstreamFiles() throws Exception {
        List<String> rows = text("assets.tsv").lines().skip(1).toList();
        assertThat(rows).hasSize(PINNED.size());
        for (String row : rows) {
            String[] fields = row.split("\t");
            assertThat(fields).as(row).hasSize(3);
            assertThat(fields[1]).isEqualTo(SOURCES.get(fields[0]));
            assertThat(fields[2]).as(fields[0]).isEqualTo(PINNED.get(fields[0]));
            assertThat(sha256(bytes(fields[0]))).as(fields[0]).isEqualTo(fields[2]);
        }
    }

    @Test void theDefaultSchemesFileHoldsIntellijsDefaultAndDarculaSchemes() throws Exception {
        assertThat(text("DefaultColorSchemesManager.xml"))
            .contains("<scheme name=\"Default\"").contains("<scheme name=\"Darcula\"")
            .contains("CONSOLE_BACKGROUND_KEY").contains("CONSOLE_WHITE_OUTPUT");
        assertThat(text("SOURCE.md")).contains("DefaultColorSchemesManager.xml");
    }
```

In `PackagedResourcesTest.java`, add `"dev/jasper/app/themes/intellij/DefaultColorSchemesManager.xml",` to the required-entries list, directly after `"dev/jasper/app/themes/intellij/darcula.theme.json",`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*IntellijThemesTest'`
Expected: FAIL. `assets.tsv` has three rows, not four, and `DefaultColorSchemesManager.xml` is missing.

- [ ] **Step 3: Extend the fetch tool**

Replace `tools/themes/fetch-intellij-themes.py` with:

```python
#!/usr/bin/env python3
"""Vendors the classic IntelliJ Light theme chain and IntelliJ's default editor schemes at a pinned
intellij-community commit.

Run from the repository root. Rewrites themes/intellij/*.theme.json, DefaultColorSchemesManager.xml
and assets.tsv, and copies LICENSE.txt and NOTICE.txt from the IntelliJ icons vendored at the same
commit (see tools/icons/fetch-intellij-icons.py for why LICENSE.txt is the canonical Apache-2.0 text).
A missing file (HTTP 404) fails the run.
"""
import hashlib
import pathlib
import shutil
import urllib.request

SHA = "f7377708b654b73b206da40bb382ecb4d44e8f12"
BASE = f"https://raw.githubusercontent.com/JetBrains/intellij-community/{SHA}/"
OUT = pathlib.Path("jasper-app/src/main/resources/dev/jasper/app/themes/intellij")
ICONS = pathlib.Path("jasper-app/src/main/resources/dev/jasper/app/icons/intellij")
# (resource name, upstream path). IntelliJ Light -> IntelliJ -> Darcula: each theme names the next as
# its parentTheme. DefaultColorSchemesManager.xml holds IntelliJ's Default and Darcula editor schemes,
# the parents theme plugins' schemes inherit console colours from.
FILES = [
    ("Light.theme.json", "platform/platform-resources/src/themes/Light.theme.json"),
    ("intellijlaf.theme.json", "platform/platform-resources/src/themes/intellijlaf.theme.json"),
    ("darcula.theme.json", "platform/platform-resources/src/themes/darcula.theme.json"),
    ("DefaultColorSchemesManager.xml", "platform/platform-resources/src/DefaultColorSchemesManager.xml"),
]

OUT.mkdir(parents=True, exist_ok=True)
rows = ["resource\tsource\tsha256"]
for name, source in FILES:
    with urllib.request.urlopen(BASE + source) as response:
        data = response.read()
    (OUT / name).write_bytes(data)
    rows.append(f"{name}\t{source}\t{hashlib.sha256(data).hexdigest()}")
(OUT / "assets.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
for name in ("LICENSE.txt", "NOTICE.txt"):
    shutil.copyfile(ICONS / name, OUT / name)
print(f"{len(FILES)} files from {SHA}")
```

- [ ] **Step 4: Run the tool (it downloads four files)**

Downloading needs the user's approval. **The controller asks the user before this step.** It fetches the three theme files already vendored, which are byte-identical, and `DefaultColorSchemesManager.xml`.

Run: `python3 tools/themes/fetch-intellij-themes.py`
Expected: `4 files from f7377708b654b73b206da40bb382ecb4d44e8f12`. After the run, `git status --short jasper-app/src/main/resources/dev/jasper/app/themes/intellij` shows only `M assets.tsv` and `?? DefaultColorSchemesManager.xml`.

- [ ] **Step 5: Update SOURCE.md and the verification doc**

Replace `jasper-app/src/main/resources/dev/jasper/app/themes/intellij/SOURCE.md` with:

```markdown
# Classic IntelliJ theme files and default editor schemes

Source: https://github.com/JetBrains/intellij-community, files under `platform/platform-resources/src`.
Commit: f7377708b654b73b206da40bb382ecb4d44e8f12, fetched 2026-09-25 with
`tools/themes/fetch-intellij-themes.py`.
License: Apache License 2.0 (LICENSE.txt, the canonical text); upstream notice in NOTICE.txt. Both
are copies of the files vendored with the IntelliJ icons from the same commit.

Files are byte-identical copies; assets.tsv records each file's upstream path and SHA-256.
`Light.theme.json` ("IntelliJ Light") names `intellijlaf.theme.json` ("IntelliJ") as its
parentTheme, which names `darcula.theme.json` ("Darcula"). Jasper's ThemeLoader resolves that
chain. These are the classic-UI themes, not the New UI ones under `themes/expUI/`.

`DefaultColorSchemesManager.xml` holds IntelliJ's own `Default` and `Darcula` editor colour
schemes. Theme plugins' editor schemes name one of them as `parent_scheme`, and Jasper's
EditorScheme reads the console colours a scheme leaves out from them.
```

In `docs/app-refactor-verification.md`, add these four lines to the packaged-resource list. Keep the list in its existing byte order, where capitals sort before lowercase:

```markdown
- `dev/jasper/app/themes/intellij/DefaultColorSchemesManager.xml`
- `dev/jasper/app/themes/intellij/NOTICE.txt`
- `dev/jasper/app/themes/intellij/SOURCE.md`
- `dev/jasper/app/themes/intellij/assets.tsv`
```

The last three also fill a gap part 2a left.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests '*IntellijThemesTest' --tests '*PackagedResourcesTest' --tests '*AppDocumentationTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add tools/themes/fetch-intellij-themes.py jasper-app/src/main/resources/dev/jasper/app/themes/intellij \
  jasper-app/src/test/java/dev/jasper/app/appearance/IntellijThemesTest.java \
  jasper-app/src/test/java/dev/jasper/app/architecture/PackagedResourcesTest.java docs/app-refactor-verification.md
git commit -m "feat(appearance): vendor IntelliJ's Default and Darcula editor schemes

The parents theme plugins' editor schemes inherit console colours from, pinned
with the IntelliJ Light chain at the same intellij-community commit.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: Loader fixes for untrusted themes

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeLoader.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeLoaderTest.java`, `ThemeManagerTest.java`

**Interfaces:**
- Produces: `ThemeLoader.Resolved(String name, boolean dark, String json, Map<String, Color> colors, List<String> warnings)`.
  - A `parentTheme` that `filesByName` doesn't have now falls back to the `"Darcula"` entry when the theme is dark, or `"IntelliJ"` when it is light, and adds a warning.
  - If that base isn't in `filesByName` either, the loader still throws `ThemeException`.
  - A `parentTheme` that isn't a string throws `ThemeException`.

- [ ] **Step 1: Write the failing tests**

Add to `ThemeLoaderTest.java`:

```java
    @Test void anUnknownParentFallsBackToTheBaseForTheThemesDarknessWithAWarning() throws Exception {
        var files = Map.of(
            "darcula.json", """
                {"name": "Darcula", "dark": true, "ui": {"A.x": "#101010"}}""",
            "light.json", """
                {"name": "IntelliJ", "ui": {"A.x": "#f0f0f0"}}""",
            "child.json", """
                {"name": "Child", "dark": true, "parentTheme": "ExperimentalDark", "ui": {"B.y": "#202020"}}""",
            "bright.json", """
                {"name": "Bright", "parentTheme": "Islands Light"}""");
        var loader = new ThemeLoader(files::get, Map.of("Darcula", "darcula.json", "IntelliJ", "light.json"));
        var dark = loader.load("child.json");
        assertThat(ui(dark)).containsEntry("A.x", "#101010").containsEntry("B.y", "#202020");
        assertThat(dark.warnings()).singleElement().asString()
            .contains("child.json").contains("ExperimentalDark").contains("Darcula");
        var light = loader.load("bright.json");
        assertThat(ui(light)).containsEntry("A.x", "#f0f0f0");
        assertThat(light.warnings()).singleElement().asString().contains("Islands Light").contains("IntelliJ");
        assertThat(loader.load("darcula.json").warnings()).isEmpty();
    }

    @Test void aParentThemeThatIsNotAStringIsAnError() {
        for (String parent : List.of("{}", "[\"Parent\"]"))
            assertThatThrownBy(() -> loader(Map.of("child.json", "{\"name\": \"Child\", \"parentTheme\": " + parent + "}"))
                .load("child.json"))
                .isInstanceOf(ThemeLoader.ThemeException.class).hasMessageContaining("child.json").hasMessageContaining("parentTheme");
    }

    @Test void aPerOsValueDropsOnlyItsIntellijClassBranches() throws Exception {
        var ui = ui(loader(Map.of("child.json", """
            {"name": "Child", "ui": {"Menu.border": {"os.mac": "com.intellij.ide.ui.laf.darcula.ui.DarculaMenuBorder",
              "os.default": "1,1,1,1"}}}""")).load("child.json"));
        assertThat(ui).containsEntry("Menu.border", Map.of("os.default", "1,1,1,1"));
    }
```

Add to `ThemeManagerTest.java`:

```java
    @Test void theBuiltInThemesLoadWithoutWarnings() {
        for (Theme theme : ThemeManager.builtIns()) assertThat(ThemeManager.resolve(theme).warnings()).as(theme.name()).isEmpty();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*ThemeLoaderTest' --tests '*ThemeManagerTest'`
Expected: FAIL to compile, because `Resolved` has no `warnings()`.

- [ ] **Step 3: Implement**

In `ThemeLoader.java`:

1. Replace the `Resolved` record with:

```java
    /** A resolved theme: FlatLaf's JSON input, every explicit colour keyed as the theme names it, and the problems met. */
    record Resolved(String name, boolean dark, String json, Map<String, Color> colors, List<String> warnings) {
        Resolved { colors = Map.copyOf(colors); warnings = List.copyOf(warnings); }
    }
```

2. In `load`, replace the first line and the `ui` loop, and pass the warnings on. The method becomes:

```java
    Resolved load(String file) {
        var warnings = new ArrayList<String>();
        Raw theme = resolve(file, new ArrayList<>(), warnings);
        Map<String, String> colors = resolveColors(file, theme.colors);
        var ui = new LinkedHashMap<String, Object>();
        theme.ui.forEach((key, value) -> {
            Object kept = withoutIntellijClasses(value);
            if (kept != null) ui.put(key, named(kept, colors));
        });
        var json = new LinkedHashMap<String, Object>();
        json.put("name", theme.name);
        json.put("dark", theme.dark);
        // FlatLaf 3.7 fails on a theme without an author.
        json.put("author", theme.author);
        json.put("colors", new LinkedHashMap<String, Object>(colors));
        json.put("ui", ui);
        if (!theme.icons.isEmpty()) json.put("icons", theme.icons);
        return new Resolved(theme.name, theme.dark, write(json), explicitColors(ui), warnings);
    }
```

3. Replace `resolve` with:

```java
    private Raw resolve(String file, List<String> chain, List<String> warnings) {
        if (chain.contains(file)) throw new ThemeException("Theme parents form a cycle: " + String.join(" -> ", chain) + " -> " + file);
        chain.add(file);
        String text = read.apply(file);
        if (text == null) throw new ThemeException("Theme file not found: " + file);
        Map<String, Object> json;
        try {
            if (!(Json.parse(new StringReader(text)) instanceof Map<?, ?> map)) throw new ThemeException(file + ": a theme must be a JSON object");
            json = strings(map);
        } catch (IOException | ParseException failure) {
            throw new ThemeException(file + ": " + failure.getMessage(), failure);
        }
        // FlatLaf's parser returns every scalar, booleans included, as a String.
        var own = new Raw(json.get("name") instanceof String name ? name : file, "true".equals(String.valueOf(json.get("dark"))),
            json.get("author") instanceof String author ? author : "",
            flatten(object(json, "colors", file)), flatten(object(json, "ui", file)), new LinkedHashMap<>(object(json, "icons", file)));
        Object parent = json.get("parentTheme");
        if (parent == null) return own;
        if (!(parent instanceof String parentName)) throw new ThemeException(file + ": \"parentTheme\" must be a string");
        String parentFile = filesByName.get(parentName);
        if (parentFile == null) {
            // A parent Jasper does not ship (a New UI or another plugin's theme) becomes the classic base.
            String base = own.dark ? "Darcula" : "IntelliJ";
            parentFile = filesByName.get(base);
            if (parentFile == null) throw new ThemeException(file + ": unknown parentTheme \"" + parentName + "\"");
            warnings.add(file + ": parentTheme \"" + parentName + "\" is not a theme Jasper has; using " + base + " instead");
        }
        return own.over(resolve(parentFile, chain, warnings));
    }
```

4. Replace the `intellijClass` method with:

```java
    /**
     * The value without IntelliJ's own UI, border and painter classes, which Jasper cannot load (FlatLaf
     * would try to); a per-OS value keeps its other branches. Null when nothing is left.
     */
    private static Object withoutIntellijClasses(Object value) {
        if (value instanceof String text) return text.startsWith("com.intellij.") ? null : value;
        if (!(value instanceof Map<?, ?> byOs) || !perOs(byOs)) return value;
        var kept = new LinkedHashMap<String, Object>();
        byOs.forEach((os, branch) -> {
            if (!(branch instanceof String text && text.startsWith("com.intellij."))) kept.put(os.toString(), branch);
        });
        return kept.isEmpty() ? null : kept;
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests '*ThemeLoaderTest' --tests '*ThemeManagerTest'`
Expected: PASS. `brokenThemesFailNamingTheFileAndTheKey` still passes: its loader has no `Darcula` or `IntelliJ` entry, so `"Nobody"` still throws.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/appearance/ThemeLoader.java \
  jasper-app/src/test/java/dev/jasper/app/appearance/ThemeLoaderTest.java jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java
git commit -m "fix(appearance): load untrusted themes with parent fallback and warnings

An unknown parentTheme becomes Darcula or IntelliJ with a warning; a parentTheme that
is not a string is an error; a per-OS value drops only its IntelliJ class branches.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: Editor schemes as terminal palettes

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/appearance/SafeXml.java`, `EditorScheme.java`
- Create (test support): `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeFixtures.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/EditorSchemeTest.java`

**Interfaces:**
- Consumes: `DefaultColorSchemesManager.xml` from Task 1.
- Produces:
  - `SafeXml.parse(byte[]) -> org.w3c.dom.Element`, which throws `IllegalArgumentException("not a readable XML document (…)")`;
  - `SafeXml.children(Element)` and `SafeXml.children(Element, String)`;
  - `EditorScheme.Result(Palette palette, List<String> warnings)`;
  - `EditorScheme.read(byte[] xml, String label, boolean dark)`;
  - `EditorScheme.named(String name, String label, boolean dark)`;
  - `ThemeFixtures.theme`, `scheme`, `pluginXml`, `plugin`, `entries`, `jar` and `jarBytes` (test-only).

- [ ] **Step 1: Write the test support**

Create `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeFixtures.java`:

```java
package dev.jasper.app.appearance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Test-only IntelliJ theme plugins, laid out the way the JetBrains Marketplace ships them. */
public final class ThemeFixtures {
    private ThemeFixtures() {}

    /** A {@code .theme.json} naming its editor scheme ({@code null} for none) and setting {@code Panel.background}. */
    public static String theme(String name, boolean dark, String editorScheme, String panelBackground) {
        return "{\"name\": \"" + name + "\", \"dark\": " + dark + ", \"author\": \"Fixture\""
            + (editorScheme == null ? "" : ", \"editorScheme\": \"" + editorScheme + "\"")
            + ", \"ui\": {\"Panel.background\": \"" + panelBackground + "\"}}";
    }

    /** An editor scheme setting the console background and ANSI red; every other key comes from {@code parent}. */
    public static String scheme(String name, String parent, String background, String red) {
        return "<scheme name=\"" + name + "\" version=\"142\" parent_scheme=\"" + parent + "\">\n"
            + "  <colors>\n    <option name=\"CONSOLE_BACKGROUND_KEY\" value=\"" + background + "\"/>\n  </colors>\n"
            + "  <attributes>\n    <option name=\"CONSOLE_RED_OUTPUT\">\n      <value>\n"
            + "        <option name=\"FOREGROUND\" value=\"" + red + "\"/>\n      </value>\n    </option>\n  </attributes>\n"
            + "</scheme>\n";
    }

    /** A {@code plugin.xml} whose themeProvider elements name {@code themePaths}. */
    public static String pluginXml(String name, String... themePaths) {
        var providers = new StringBuilder();
        for (int i = 0; i < themePaths.length; i++)
            providers.append("    <themeProvider id=\"theme").append(i).append("\" path=\"").append(themePaths[i]).append("\"/>\n");
        return "<idea-plugin>\n  <id>fixture." + name.replace(' ', '-') + "</id>\n  <name>" + name + "</name>\n"
            + "  <extensions defaultExtensionNs=\"com.intellij\">\n" + providers + "  </extensions>\n</idea-plugin>\n";
    }

    /**
     * A typical theme plugin jar: one theme under {@code /themes/} with its editor scheme, whose parent is
     * Darcula for a dark theme and Default for a light one. Colours: {@code panelBackground} as {@code #rrggbb},
     * {@code consoleBackground} as IntelliJ writes scheme colours ({@code rrggbb}); ANSI red is {@code ff5555}.
     */
    public static Path plugin(Path file, String name, boolean dark, String panelBackground, String consoleBackground) {
        String base = name.replace(' ', '_');
        return jar(file, entries(
            "META-INF/plugin.xml", pluginXml(name, "/themes/" + base + ".theme.json"),
            "themes/" + base + ".theme.json", theme(name, dark, "/themes/" + base + ".xml", panelBackground),
            "themes/" + base + ".xml", scheme(name, dark ? "Darcula" : "Default", consoleBackground, "ff5555")));
    }

    /** Entries from alternating names and contents (a String, written as UTF-8, or a byte[]), in order. */
    public static Map<String, byte[]> entries(Object... namesAndContents) {
        var entries = new LinkedHashMap<String, byte[]>();
        for (int i = 0; i < namesAndContents.length; i += 2)
            entries.put((String) namesAndContents[i], namesAndContents[i + 1] instanceof byte[] bytes ? bytes
                : namesAndContents[i + 1].toString().getBytes(StandardCharsets.UTF_8));
        return entries;
    }

    /** Writes {@code entries} as a zip (a jar is a zip) to {@code file}, creating its folder. */
    public static Path jar(Path file, Map<String, byte[]> entries) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.write(file, jarBytes(entries));
            return file;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public static byte[] jarBytes(Map<String, byte[]> entries) {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return bytes.toByteArray();
    }
}
```

- [ ] **Step 2: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/appearance/EditorSchemeTest.java`:

```java
package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EditorSchemeTest {
    private static EditorScheme.Result read(String xml, boolean dark) {
        return EditorScheme.read(xml.getBytes(StandardCharsets.UTF_8), "fixture.xml", dark);
    }

    @Test void intellijsOwnSchemesAreTheParents() {
        var darcula = EditorScheme.named("Darcula", "t.theme.json", true);
        assertThat(darcula.warnings()).isEmpty();
        Palette palette = darcula.palette();
        assertThat(palette.background()).isEqualTo(new Color(0x2b2b2b));
        assertThat(palette.foreground()).isEqualTo(new Color(0xbbbbbb));
        assertThat(palette.cursor()).isEqualTo(new Color(0xbbbbbb));
        assertThat(palette.selection()).isEqualTo(new Color(0x214283));
        assertThat(palette.ansi().get(1)).isEqualTo(new Color(0xf0524f));
        assertThat(palette.ansi().get(8)).isEqualTo(new Color(0x595959));
        assertThat(palette.ansi().get(15)).isEqualTo(new Color(0xffffff));
        var light = EditorScheme.named("Default", "t.theme.json", false).palette();
        assertThat(light.background()).isEqualTo(Color.WHITE);
        assertThat(light.cursor()).as("\"0\" is black").isEqualTo(Color.BLACK);
        assertThat(light.ansi().get(1)).isEqualTo(new Color(0xce0505));
    }

    @Test void everyConsoleKeyMapsToItsPaletteField() {
        String[] ansi = {"BLACK", "RED", "GREEN", "YELLOW", "BLUE", "MAGENTA", "CYAN", "GRAY",
            "DARKGRAY", "RED_BRIGHT", "GREEN_BRIGHT", "YELLOW_BRIGHT", "BLUE_BRIGHT", "MAGENTA_BRIGHT", "CYAN_BRIGHT", "WHITE"};
        var attributes = new StringBuilder(
            "<option name=\"CONSOLE_NORMAL_OUTPUT\"><value><option name=\"FOREGROUND\" value=\"131415\"/></value></option>");
        for (int i = 0; i < ansi.length; i++)
            attributes.append("<option name=\"CONSOLE_").append(ansi[i]).append("_OUTPUT\"><value><option name=\"FOREGROUND\" value=\"")
                .append(String.format("%06x", 0x200000 + i)).append("\"/></value></option>");
        var result = read("<scheme name=\"All\" parent_scheme=\"Darcula\"><colors>"
            + "<option name=\"CONSOLE_BACKGROUND_KEY\" value=\"0a0b0c\"/><option name=\"CARET_COLOR\" value=\"0d0e0f\"/>"
            + "<option name=\"SELECTION_BACKGROUND\" value=\"101112\"/></colors><attributes>" + attributes
            + "</attributes></scheme>", true);
        assertThat(result.warnings()).isEmpty();
        assertThat(result.palette().background()).isEqualTo(new Color(0x0a0b0c));
        assertThat(result.palette().cursor()).isEqualTo(new Color(0x0d0e0f));
        assertThat(result.palette().selection()).isEqualTo(new Color(0x101112));
        assertThat(result.palette().foreground()).isEqualTo(new Color(0x131415));
        for (int i = 0; i < 16; i++) assertThat(result.palette().ansi().get(i)).as(ansi[i]).isEqualTo(new Color(0x200000 + i));
    }

    @Test void keysASchemeLeavesOutComeFromItsParent() {
        var result = read(ThemeFixtures.scheme("Partial", "Darcula", "101010", "abcdef"), true);
        assertThat(result.warnings()).isEmpty();
        assertThat(result.palette().background()).isEqualTo(new Color(0x101010));
        assertThat(result.palette().ansi().get(1)).isEqualTo(new Color(0xabcdef));
        assertThat(result.palette().ansi().get(2)).as("Darcula's green").isEqualTo(new Color(0x5c962c));
        assertThat(result.palette().foreground()).as("Darcula's console text").isEqualTo(new Color(0xbbbbbb));
        var orphan = read("<scheme name=\"Orphan\"><colors><option name=\"CONSOLE_BACKGROUND_KEY\" value=\"202020\"/></colors></scheme>", true);
        assertThat(orphan.warnings()).isEmpty();
        assertThat(orphan.palette().ansi().get(1)).as("no parent_scheme means Default").isEqualTo(new Color(0xce0505));
    }

    @Test void baseAttributesPointAtAnotherKey() {
        var result = read("""
            <scheme name="Based" parent_scheme="Darcula"><attributes>
              <option name="MY_RED"><value><option name="FOREGROUND" value="123456"/></value></option>
              <option name="CONSOLE_RED_OUTPUT" baseAttributes="MY_RED"/>
            </attributes></scheme>""", true);
        assertThat(result.palette().ansi().get(1)).isEqualTo(new Color(0x123456));
    }

    @Test void withoutAKnownParentTheEditorTextAndThenJaspersPaletteFillIn() {
        var result = read("""
            <scheme name="Lonely" parent_scheme="Nobody"><attributes>
              <option name="TEXT"><value><option name="FOREGROUND" value="111111"/><option name="BACKGROUND" value="222222"/></value></option>
            </attributes></scheme>""", true);
        assertThat(result.palette().foreground()).isEqualTo(new Color(0x111111));
        assertThat(result.palette().background()).isEqualTo(new Color(0x222222));
        assertThat(result.palette().ansi()).isEqualTo(Palette.jasperDark().ansi());
        assertThat(result.palette().cursor()).isEqualTo(Palette.jasperDark().cursor());
        assertThat(result.warnings()).singleElement().asString().contains("fixture.xml").contains("Nobody");
    }

    @Test void coloursAreHexNumbersThatMayDropLeadingZeros() {
        var result = read("""
            <scheme name="Hex" parent_scheme="Default"><colors>
              <option name="CARET_COLOR" value="0"/><option name="SELECTION_BACKGROUND" value="ff"/>
              <option name="CONSOLE_BACKGROUND_KEY" value="#00ff00"/>
            </colors></scheme>""", false);
        assertThat(result.warnings()).isEmpty();
        assertThat(result.palette().cursor()).isEqualTo(Color.BLACK);
        assertThat(result.palette().selection()).isEqualTo(new Color(0x0000ff));
        assertThat(result.palette().background()).isEqualTo(new Color(0x00ff00));
    }

    @Test void badColoursAndDocumentsFallBackWithWarnings() {
        var bad = read("<scheme name=\"Bad\" parent_scheme=\"Default\"><colors><option name=\"CARET_COLOR\" value=\"zz\"/></colors></scheme>", false);
        assertThat(bad.palette().cursor()).isEqualTo(Palette.jasperLight().cursor());
        assertThat(bad.warnings()).singleElement().asString().contains("CARET_COLOR").contains("zz");
        var broken = read("<scheme", true);
        assertThat(broken.palette()).isEqualTo(Palette.jasperDark());
        assertThat(broken.warnings()).singleElement().asString().contains("fixture.xml").contains("not a readable XML document");
        var xxe = read("<!DOCTYPE scheme [<!ENTITY x SYSTEM \"file:///etc/hosts\">]><scheme name=\"&x;\"/>", true);
        assertThat(xxe.palette()).isEqualTo(Palette.jasperDark());
        assertThat(xxe.warnings()).singleElement().asString().contains("DOCTYPE");
        var two = read("<component><scheme name=\"A\"/><scheme name=\"B\"/></component>", false);
        assertThat(two.palette()).isEqualTo(Palette.jasperLight());
        assertThat(two.warnings()).singleElement().asString().contains("found 2");
    }

    @Test void aSchemeNameJasperDoesNotHaveFallsBack() {
        var result = EditorScheme.named("IntelliJ Light", "t.theme.json", false);
        assertThat(result.palette()).isEqualTo(Palette.jasperLight());
        assertThat(result.warnings()).singleElement().asString().contains("IntelliJ Light");
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*EditorSchemeTest'`
Expected: FAIL to compile, because `EditorScheme` does not exist.

- [ ] **Step 4: Implement SafeXml and EditorScheme**

Create `jasper-app/src/main/java/dev/jasper/app/appearance/SafeXml.java`:

```java
package dev.jasper.app.appearance;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** XML from theme plugins, which Jasper does not trust: no DOCTYPE, no external entities or schemas, no XInclude. */
final class SafeXml {
    private SafeXml() {}

    /** The document element; an {@link IllegalArgumentException} naming the problem when {@code xml} is not a readable document. */
    static Element parse(byte[] xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(false);
            var builder = factory.newDocumentBuilder();
            // The default handler prints parse errors to standard error; the exception already carries them.
            builder.setErrorHandler(new DefaultHandler());
            return builder.parse(new ByteArrayInputStream(xml)).getDocumentElement();
        } catch (ParserConfigurationException | SAXException | IOException failure) {
            throw new IllegalArgumentException("not a readable XML document (" + failure.getMessage() + ")", failure);
        }
    }

    /** Every element child of {@code parent}, in document order. */
    static List<Element> children(Element parent) {
        var result = new ArrayList<Element>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
            if (node instanceof Element element) result.add(element);
        return result;
    }

    /** The element children of {@code parent} named {@code tag}, in document order. */
    static List<Element> children(Element parent, String tag) {
        return children(parent).stream().filter(element -> element.getTagName().equals(tag)).toList();
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/appearance/EditorScheme.java`:

```java
package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;

/**
 * Turns an IntelliJ editor colour scheme into a terminal palette: the console colours, then the
 * editor text colours IntelliJ's console falls back to. Keys a scheme leaves out come from its parent
 * scheme (IntelliJ's own Default or Darcula, vendored), and only then from Jasper's palette. Scheme
 * colours are used as the theme author chose them: no contrast floor applies.
 */
final class EditorScheme {
    /** A complete palette and the problems met building it. */
    record Result(Palette palette, List<String> warnings) {
        Result { warnings = List.copyOf(warnings); }
    }

    private record Attribute(String base, Map<String, String> values) {}
    private record Scheme(String name, String parent, Map<String, String> colors, Map<String, Attribute> attributes) {}

    private static final String PARENTS_FILE = "dev/jasper/app/themes/intellij/DefaultColorSchemesManager.xml";
    private static final List<String> ANSI = List.of(
        "CONSOLE_BLACK_OUTPUT", "CONSOLE_RED_OUTPUT", "CONSOLE_GREEN_OUTPUT", "CONSOLE_YELLOW_OUTPUT",
        "CONSOLE_BLUE_OUTPUT", "CONSOLE_MAGENTA_OUTPUT", "CONSOLE_CYAN_OUTPUT", "CONSOLE_GRAY_OUTPUT",
        "CONSOLE_DARKGRAY_OUTPUT", "CONSOLE_RED_BRIGHT_OUTPUT", "CONSOLE_GREEN_BRIGHT_OUTPUT", "CONSOLE_YELLOW_BRIGHT_OUTPUT",
        "CONSOLE_BLUE_BRIGHT_OUTPUT", "CONSOLE_MAGENTA_BRIGHT_OUTPUT", "CONSOLE_CYAN_BRIGHT_OUTPUT", "CONSOLE_WHITE_OUTPUT");

    /** IntelliJ's own schemes by name, read once. */
    private static final class Parents {
        static final Map<String, Scheme> SCHEMES = readParents();
    }

    private EditorScheme() {}

    /** The palette of the one scheme in {@code xml}; {@code label} names its file in warnings. */
    static Result read(byte[] xml, String label, boolean dark) {
        var warnings = new ArrayList<String>();
        List<Scheme> schemes;
        try { schemes = schemes(SafeXml.parse(xml)); }
        catch (IllegalArgumentException failure) {
            warnings.add(label + " is " + failure.getMessage() + "; the terminal uses Jasper's palette");
            return new Result(fallback(dark), warnings);
        }
        if (schemes.size() != 1) {
            warnings.add(label + ": expected one <scheme>, found " + schemes.size() + "; the terminal uses Jasper's palette");
            return new Result(fallback(dark), warnings);
        }
        return palette(schemes.getFirst(), label, dark, warnings);
    }

    /** The palette of a scheme IntelliJ ships, named by a theme's {@code editorScheme} instead of a path. */
    static Result named(String name, String label, boolean dark) {
        Scheme scheme = Parents.SCHEMES.get(name);
        if (scheme == null)
            return new Result(fallback(dark), List.of(label + ": editor scheme \"" + name + "\" is not one Jasper has; the terminal uses Jasper's palette"));
        return palette(scheme, label, dark, new ArrayList<>());
    }

    private static Result palette(Scheme leaf, String label, boolean dark, List<String> warnings) {
        var lookup = new Lookup(chain(leaf, label, warnings), label, warnings);
        Palette base = fallback(dark);
        Color background = first(lookup.color("CONSOLE_BACKGROUND_KEY"), lookup.attribute("TEXT", "BACKGROUND"), base.background());
        Color foreground = first(lookup.attribute("CONSOLE_NORMAL_OUTPUT", "FOREGROUND"), lookup.attribute("TEXT", "FOREGROUND"), base.foreground());
        Color cursor = first(lookup.color("CARET_COLOR"), base.cursor());
        Color selection = first(lookup.color("SELECTION_BACKGROUND"), base.selection());
        var ansi = new ArrayList<Color>();
        for (int i = 0; i < ANSI.size(); i++) ansi.add(first(lookup.attribute(ANSI.get(i), "FOREGROUND"), base.ansi().get(i)));
        return new Result(new Palette(foreground, background, cursor, selection, ansi), warnings);
    }

    /** The scheme and its parents. A missing {@code parent_scheme} means Default, as in IntelliJ. */
    private static List<Scheme> chain(Scheme leaf, String label, List<String> warnings) {
        var chain = new ArrayList<Scheme>();
        Scheme scheme = leaf;
        while (scheme != null && !chain.contains(scheme)) {
            chain.add(scheme);
            String parent = scheme.parent() != null ? scheme.parent() : "Default".equals(scheme.name()) ? null : "Default";
            if (parent == null) break;
            Scheme next = Parents.SCHEMES.get(parent);
            if (next == null) warnings.add(label + ": parent scheme \"" + parent + "\" is not one Jasper has; missing colours come from Jasper's palette");
            scheme = next;
        }
        return chain;
    }

    private static Color first(Color... colors) {
        for (Color color : colors) if (color != null) return color;
        throw new IllegalStateException("No colour");
    }

    private static Palette fallback(boolean dark) { return dark ? Palette.jasperDark() : Palette.jasperLight(); }

    /** Finds a key along the scheme chain, following {@code baseAttributes}. */
    private static final class Lookup {
        private final List<Scheme> chain;
        private final String label;
        private final List<String> warnings;

        Lookup(List<Scheme> chain, String label, List<String> warnings) {
            this.chain = chain; this.label = label; this.warnings = warnings;
        }

        Color color(String key) {
            for (Scheme scheme : chain) {
                String value = scheme.colors().get(key);
                if (value != null && !value.isBlank()) return hex(value, key);
            }
            return null;
        }

        Color attribute(String key, String field) { return attribute(key, field, 0); }

        private Color attribute(String key, String field, int depth) {
            for (Scheme scheme : chain) {
                Attribute attribute = scheme.attributes().get(key);
                if (attribute == null) continue;
                String value = attribute.values().get(field);
                if (value != null && !value.isBlank()) return hex(value, key);
                // baseAttributes redirects to another key, looked up from the start of the chain.
                if (attribute.base() != null && depth < 16) return attribute(attribute.base(), field, depth + 1);
            }
            return null;
        }

        /** IntelliJ writes {@code Integer.toHexString(rgb)}: hexadecimal RGB without {@code #}, leading zeros dropped. */
        private Color hex(String value, String key) {
            String digits = value.startsWith("#") ? value.substring(1) : value;
            if (digits.isEmpty() || digits.length() > 8 || !digits.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
                warnings.add(label + ": " + key + " has colour \"" + value + "\", which is not hexadecimal RGB; ignoring it");
                return null;
            }
            return new Color((int) (Long.parseLong(digits, 16) & 0xffffff));
        }
    }

    private static List<Scheme> schemes(Element root) {
        if (root.getTagName().equals("scheme")) return List.of(scheme(root));
        return SafeXml.children(root, "scheme").stream().map(EditorScheme::scheme).toList();
    }

    private static Scheme scheme(Element element) {
        var colors = new LinkedHashMap<String, String>();
        for (Element section : SafeXml.children(element, "colors"))
            for (Element option : SafeXml.children(section, "option")) colors.put(option.getAttribute("name"), option.getAttribute("value"));
        var attributes = new LinkedHashMap<String, Attribute>();
        for (Element section : SafeXml.children(element, "attributes"))
            for (Element option : SafeXml.children(section, "option")) {
                var values = new LinkedHashMap<String, String>();
                for (Element value : SafeXml.children(option, "value"))
                    for (Element field : SafeXml.children(value, "option")) values.put(field.getAttribute("name"), field.getAttribute("value"));
                String base = option.getAttribute("baseAttributes");
                attributes.put(option.getAttribute("name"), new Attribute(base.isEmpty() ? null : base, values));
            }
        String parent = element.getAttribute("parent_scheme");
        return new Scheme(element.getAttribute("name"), parent.isEmpty() ? null : parent, colors, attributes);
    }

    private static Map<String, Scheme> readParents() {
        try (InputStream in = EditorScheme.class.getClassLoader().getResourceAsStream(PARENTS_FILE)) {
            if (in == null) throw new IllegalStateException("Missing " + PARENTS_FILE);
            var schemes = new HashMap<String, Scheme>();
            for (Scheme scheme : schemes(SafeXml.parse(in.readAllBytes()))) schemes.put(scheme.name(), scheme);
            return Map.copyOf(schemes);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests '*EditorSchemeTest'`
Expected: PASS, with no `[Fatal Error]` lines in the test output.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/appearance/SafeXml.java jasper-app/src/main/java/dev/jasper/app/appearance/EditorScheme.java \
  jasper-app/src/test/java/dev/jasper/app/appearance/ThemeFixtures.java jasper-app/src/test/java/dev/jasper/app/appearance/EditorSchemeTest.java
git commit -m "feat(appearance): turn IntelliJ editor schemes into terminal palettes

Console colours first, then the editor text colours, then the parent scheme
(IntelliJ's vendored Default or Darcula), then Jasper's palette. Scheme XML is
parsed with DOCTYPE and external entities disabled.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Reading theme plugins

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeArchive.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeArchiveTest.java`

**Interfaces:**
- Consumes:
  - `SafeXml.parse`, `SafeXml.children(Element)` and `SafeXml.children(Element, String)` (Task 3);
  - `ThemeFixtures` (Task 3).
- Produces:
  - `ThemeArchive.read(Path archive) -> ThemeArchive.Contents`, which never throws;
  - `ThemeArchive.Contents(List<ThemeArchive.Found> themes, List<String> problems)`;
  - `ThemeArchive.Found(String label, String json, byte[] schemeXml, String schemeName, String schemeLabel)`. `label` has the form `archive!/entry`, or `archive!/lib-jar!/entry` for a nested jar. `schemeXml` is null when there is no scheme file. `schemeName` is set when `editorScheme` is a name rather than a path;
  - the constants `MAX_ARCHIVE`, `MAX_FILE`, `MAX_NESTED`, `MAX_NESTED_JARS` and `MAX_ENTRIES`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeArchiveTest.java`:

```java
package dev.jasper.app.appearance;

import java.io.ByteArrayOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.appearance.ThemeFixtures.*;
import static org.assertj.core.api.Assertions.*;

class ThemeArchiveTest {
    @TempDir Path directory;

    @Test void aPluginJarProvidesEachThemeWithItsEditorScheme() {
        Path file = jar(directory.resolve("Pair.jar"), entries(
            "META-INF/plugin.xml", pluginXml("Pair", "/themes/Night.theme.json", "themes/Day.theme.json"),
            "themes/Night.theme.json", theme("Night", true, "/themes/Night.xml", "#101010"),
            "themes/Day.theme.json", theme("Day", false, "Day.xml", "#fafafa"),
            "themes/Night.xml", scheme("Night", "Darcula", "000000", "ff0000"),
            "themes/Day.xml", scheme("Day", "Default", "ffffff", "aa0000"),
            "fix/Pair.class", new byte[]{(byte) 0xca, (byte) 0xfe}));
        var contents = ThemeArchive.read(file);
        assertThat(contents.problems()).isEmpty();
        assertThat(contents.themes()).extracting(ThemeArchive.Found::label)
            .containsExactly("Pair.jar!/themes/Night.theme.json", "Pair.jar!/themes/Day.theme.json");
        var night = contents.themes().getFirst();
        assertThat(night.json()).contains("\"Night\"");
        assertThat(new String(night.schemeXml(), StandardCharsets.UTF_8)).contains("parent_scheme=\"Darcula\"");
        assertThat(night.schemeLabel()).isEqualTo("Pair.jar!/themes/Night.xml");
        assertThat(contents.themes().get(1).schemeLabel()).as("a relative scheme path is beside its theme")
            .isEqualTo("Pair.jar!/themes/Day.xml");
    }

    @Test void aMarketplaceZipIsReadThroughTheJarsInItsLibFolder() {
        byte[] plugin = jarBytes(entries(
            "META-INF/plugin.xml", pluginXml("Dracula Fixture", "/themes/Dracula.theme.json"),
            "themes/Dracula.theme.json", theme("Dracula Fixture", true, "/themes/Dracula.xml", "#282a36"),
            "themes/Dracula.xml", scheme("Dracula Fixture", "Darcula", "282a36", "ff5555")));
        byte[] library = jarBytes(entries("kotlin/Unit.class", new byte[]{1, 2, 3}));
        Path file = jar(directory.resolve("Dracula.zip"), entries(
            "Dracula/lib/kotlin-stdlib.jar", library, "Dracula/lib/dracula.jar", plugin, "Dracula/README.txt", "hello"));
        var contents = ThemeArchive.read(file);
        assertThat(contents.problems()).isEmpty();
        assertThat(contents.themes()).singleElement().satisfies(found -> {
            assertThat(found.label()).isEqualTo("Dracula.zip!/Dracula/lib/dracula.jar!/themes/Dracula.theme.json");
            assertThat(found.schemeXml()).isNotNull();
        });
    }

    @Test void bothFormsOfThemeProviderAreRead() {
        Path plain = jar(directory.resolve("Plain.jar"), entries(
            "META-INF/plugin.xml", "<idea-plugin><name>Plain</name><extensions>"
                + "<com.intellij.themeProvider id=\"a\" path=\"/a.theme.json\"/></extensions></idea-plugin>",
            "a.theme.json", theme("Plain", false, null, "#eeeeee")));
        assertThat(ThemeArchive.read(plain).themes()).singleElement().satisfies(found -> {
            assertThat(found.schemeXml()).isNull();
            assertThat(found.schemeName()).isNull();
        });
        Path real = jar(directory.resolve("Real.jar"), entries(
            "META-INF/plugin.xml", """
                <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude" require-restart="false">
                  <id>com.example.real</id><name>Real Theme</name>
                  <depends>com.intellij.modules.platform</depends>
                  <xi:include href="/META-INF/other.xml" xpointer="xpointer(/idea-plugin/*)"/>
                  <extensions defaultExtensionNs="com.intellij">
                    <themeProvider id="real-dark" path="/real.theme.json" targetUi="classic"/>
                    <bundledColorScheme path="/real"/>
                  </extensions>
                </idea-plugin>""",
            "real.theme.json", theme("Real", true, null, "#123123")));
        var contents = ThemeArchive.read(real);
        assertThat(contents.problems()).isEmpty();
        assertThat(contents.themes()).extracting(ThemeArchive.Found::label).containsExactly("Real.jar!/real.theme.json");
    }

    @Test void aSchemeNamedInsteadOfAPathIsPassedOnByName() {
        Path file = jar(directory.resolve("Named.jar"), entries(
            "META-INF/plugin.xml", pluginXml("Named", "/Named.theme.json"),
            "Named.theme.json", theme("Named", true, "Darcula", "#222222")));
        assertThat(ThemeArchive.read(file).themes()).singleElement().satisfies(found -> {
            assertThat(found.schemeName()).isEqualTo("Darcula");
            assertThat(found.schemeXml()).isNull();
        });
    }

    @Test void aPluginWithoutThemeProvidersSaysItDrawsItsThemeInCode() {
        Path file = jar(directory.resolve("Material.jar"), entries("META-INF/plugin.xml", """
            <idea-plugin><name>Material Theme UI</name>
              <extensions defaultExtensionNs="com.intellij"><applicationService serviceImplementation="x.Y"/></extensions>
            </idea-plugin>"""));
        var contents = ThemeArchive.read(file);
        assertThat(contents.themes()).isEmpty();
        assertThat(contents.problems()).containsExactly("Material Theme UI provides no theme files; Jasper can't use themes drawn in code");
    }

    @Test void pathsThatLeaveThePluginAreRejected() {
        Path file = jar(directory.resolve("Escape.jar"), entries(
            "META-INF/plugin.xml", pluginXml("Escape", "/../outside.theme.json", "/ok.theme.json"),
            "ok.theme.json", theme("Ok", true, "../../scheme.xml", "#111111")));
        var contents = ThemeArchive.read(file);
        assertThat(contents.themes()).extracting(ThemeArchive.Found::label).containsExactly("Escape.jar!/ok.theme.json");
        assertThat(contents.themes().getFirst().schemeXml()).isNull();
        assertThat(contents.problems()).hasSize(2).allMatch(problem -> problem.contains("leaves the plugin"));
    }

    @Test void missingThemeAndSchemeFilesAreReported() {
        Path file = jar(directory.resolve("Gaps.jar"), entries(
            "META-INF/plugin.xml", pluginXml("Gaps", "/gone.theme.json", "/here.theme.json"),
            "here.theme.json", theme("Here", false, "/missing.xml", "#ffffff")));
        var contents = ThemeArchive.read(file);
        assertThat(contents.themes()).singleElement().satisfies(found -> assertThat(found.schemeXml()).isNull());
        assertThat(contents.problems()).hasSize(2);
        assertThat(contents.problems().get(0)).contains("Gaps.jar!/gone.theme.json").contains("missing");
        assertThat(contents.problems().get(1)).contains("Gaps.jar!/missing.xml").contains("missing");
    }

    @Test void unreadableArchivesAndPluginXmlAreReported() throws Exception {
        Files.writeString(directory.resolve("text.jar"), "not a zip");
        assertThat(ThemeArchive.read(directory.resolve("text.jar")).problems()).singleElement().asString()
            .contains("text.jar").contains("cannot be read as a plugin archive");
        Path noPlugin = jar(directory.resolve("NoPlugin.zip"), entries("Thing/lib/other.jar", jarBytes(entries("a.txt", "a"))));
        assertThat(ThemeArchive.read(noPlugin).problems()).singleElement().asString().contains("no META-INF/plugin.xml");
        Path broken = jar(directory.resolve("Broken.jar"), entries("META-INF/plugin.xml", "<idea-plugin"));
        assertThat(ThemeArchive.read(broken).problems()).singleElement().asString()
            .contains("Broken.jar!/META-INF/plugin.xml").contains("not a readable XML document");
    }

    @Test void aDoctypeIsRejectedWithoutReadingItsEntities() throws Exception {
        Path secret = Files.writeString(directory.resolve("secret.txt"), "top secret");
        Path file = jar(directory.resolve("Xxe.jar"), entries("META-INF/plugin.xml",
            "<?xml version=\"1.0\"?><!DOCTYPE idea-plugin [<!ENTITY leak SYSTEM \"" + secret.toUri() + "\">]>"
                + "<idea-plugin><name>&leak;</name><extensions defaultExtensionNs=\"com.intellij\">"
                + "<themeProvider id=\"x\" path=\"/x.theme.json\"/></extensions></idea-plugin>",
            "x.theme.json", theme("X", true, null, "#000000")));
        var contents = ThemeArchive.read(file);
        assertThat(contents.themes()).isEmpty();
        assertThat(contents.problems()).singleElement().asString().contains("DOCTYPE").doesNotContain("top secret");
    }

    @Test void everyReadIsCapped() throws Exception {
        Path huge = directory.resolve("Huge.jar");
        try (var file = new RandomAccessFile(huge.toFile(), "rw")) { file.setLength(ThemeArchive.MAX_ARCHIVE + 1); }
        assertThat(ThemeArchive.read(huge).problems()).singleElement().asString().contains("larger than 64 MiB");

        Path bigTheme = jar(directory.resolve("BigTheme.jar"), entries(
            "META-INF/plugin.xml", pluginXml("Big", "/big.theme.json"),
            "big.theme.json", new byte[ThemeArchive.MAX_FILE + 1]));
        assertThat(ThemeArchive.read(bigTheme).problems()).singleElement().asString()
            .contains("BigTheme.jar!/big.theme.json").contains("larger than 4 MiB");

        var many = new LinkedHashMap<String, byte[]>();
        for (int i = 0; i <= ThemeArchive.MAX_ENTRIES; i++) many.put("e" + i, new byte[0]);
        assertThat(ThemeArchive.read(jar(directory.resolve("Many.jar"), many)).problems()).singleElement().asString()
            .contains("more than 10,000 entries");

        var libs = new LinkedHashMap<String, byte[]>();
        for (int i = 0; i <= ThemeArchive.MAX_NESTED_JARS; i++) libs.put(String.format("P/lib/l%02d.jar", i), jarBytes(entries("a.txt", "a")));
        libs.put("P/lib/zz-theme.jar", jarBytes(entries(
            "META-INF/plugin.xml", pluginXml("Late", "/late.theme.json"), "late.theme.json", theme("Late", true, null, "#000000"))));
        var crowded = ThemeArchive.read(jar(directory.resolve("Crowded.zip"), libs));
        assertThat(crowded.themes()).as("only the first 16 jars are read").isEmpty();
        assertThat(crowded.problems()).anySatisfy(problem -> assertThat(problem).contains("more than 16 jars"));

        Path bigLib = jar(directory.resolve("BigLib.zip"), entries("P/lib/big.jar", storedJar("blob.bin", ThemeArchive.MAX_NESTED + 1)));
        assertThat(ThemeArchive.read(bigLib).problems()).anySatisfy(problem ->
            assertThat(problem).contains("BigLib.zip!/P/lib/big.jar").contains("larger than 32 MiB"));
    }

    /** A jar whose one entry is stored uncompressed, so the jar itself is at least {@code size} bytes. */
    private static byte[] storedJar(String name, int size) throws Exception {
        byte[] data = new byte[size];
        var crc = new CRC32();
        crc.update(data);
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            var entry = new ZipEntry(name);
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(size);
            entry.setCompressedSize(size);
            entry.setCrc(crc.getValue());
            zip.putNextEntry(entry);
            zip.write(data);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*ThemeArchiveTest'`
Expected: FAIL to compile, because `ThemeArchive` does not exist.

- [ ] **Step 3: Implement ThemeArchive**

Create `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeArchive.java`:

```java
package dev.jasper.app.appearance;

import com.formdev.flatlaf.json.Json;
import com.formdev.flatlaf.json.ParseException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Reads the themes an IntelliJ theme plugin provides, straight from its {@code .jar} or Marketplace
 * {@code .zip} (a folder holding {@code lib/*.jar}): {@code META-INF/plugin.xml} names each
 * {@code .theme.json} through a themeProvider, and a theme may name its editor scheme. Nothing is
 * extracted, loaded or run; every read is capped, and problems are returned rather than thrown.
 */
final class ThemeArchive {
    static final long MAX_ARCHIVE = 64L << 20;
    static final int MAX_FILE = 4 << 20;
    static final int MAX_NESTED = 32 << 20;
    static final int MAX_NESTED_JARS = 16;
    static final int MAX_ENTRIES = 10_000;
    private static final String PLUGIN_XML = "META-INF/plugin.xml";

    /**
     * One provided theme: {@code label} ({@code archive!/entry}, or {@code archive!/lib-jar!/entry}) and its
     * JSON, with the scheme file's bytes, or the name of an IntelliJ scheme, when the theme names one.
     */
    record Found(String label, String json, byte[] schemeXml, String schemeName, String schemeLabel) {}

    record Contents(List<Found> themes, List<String> problems) {
        Contents { themes = List.copyOf(themes); problems = List.copyOf(problems); }
    }

    /** A jar and a reader for a set of its entries; names it lacks are absent from the result. */
    private record Jar(String label, Function<Set<String>, Map<String, byte[]>> reader) {
        byte[] read(String name) { return reader.apply(Set.of(name)).get(name); }
    }

    /** An entry over its cap; carries no stack trace. */
    private static final class TooLarge extends RuntimeException {
        TooLarge() { super(null, null, false, false); }
    }

    private ThemeArchive() {}

    static Contents read(Path archive) {
        var themes = new ArrayList<Found>();
        var problems = new ArrayList<String>();
        String name = archive.getFileName().toString();
        try {
            if (Files.size(archive) > MAX_ARCHIVE) {
                problems.add(name + " is larger than 64 MiB; skipped");
                return new Contents(themes, problems);
            }
            try (var zip = new ZipFile(archive.toFile())) {
                if (zip.size() > MAX_ENTRIES) {
                    problems.add(name + " has more than 10,000 entries; skipped");
                    return new Contents(themes, problems);
                }
                if (zip.getEntry(PLUGIN_XML) != null) plugin(new Jar(name, wanted -> fromZip(zip, wanted)), themes, problems);
                else nested(zip, name, themes, problems);
            }
        } catch (IOException | UncheckedIOException | SecurityException failure) {
            problems.add(name + " cannot be read as a plugin archive: " + failure.getMessage());
        }
        return new Contents(themes, problems);
    }

    /** A Marketplace zip: the plugin is one of the jars in its {@code lib} folder. */
    private static void nested(ZipFile zip, String name, List<Found> themes, List<String> problems) {
        List<String> jars = zip.stream().map(ZipEntry::getName).filter(ThemeArchive::libJar).sorted().toList();
        if (jars.size() > MAX_NESTED_JARS) problems.add(name + " has more than 16 jars in lib/; only the first 16 were read");
        boolean found = false;
        for (String entry : jars.subList(0, Math.min(jars.size(), MAX_NESTED_JARS))) {
            byte[] bytes;
            try { bytes = entryBytes(zip, zip.getEntry(entry), MAX_NESTED); }
            catch (TooLarge tooLarge) { problems.add(name + "!/" + entry + " is larger than 32 MiB; skipped"); continue; }
            boolean hasPlugin;
            // An oversized plugin.xml still marks the plugin jar; plugin() reports its size.
            try { hasPlugin = !fromBytes(bytes, Set.of(PLUGIN_XML)).isEmpty(); }
            catch (TooLarge tooLarge) { hasPlugin = true; }
            if (!hasPlugin) continue;
            found = true;
            plugin(new Jar(name + "!/" + entry, wanted -> fromBytes(bytes, wanted)), themes, problems);
        }
        if (!found) problems.add(name + " has no META-INF/plugin.xml, in itself or in a lib/*.jar; it is not an IntelliJ plugin");
    }

    private static void plugin(Jar jar, List<Found> themes, List<String> problems) {
        String location = jar.label() + "!/" + PLUGIN_XML;
        Element root;
        try {
            byte[] xml = jar.read(PLUGIN_XML);
            root = SafeXml.parse(xml);
        } catch (TooLarge tooLarge) {
            problems.add(location + " is larger than 4 MiB; skipped");
            return;
        } catch (IllegalArgumentException failure) {
            problems.add(location + " is " + failure.getMessage());
            return;
        }
        List<String> paths = providers(root);
        if (paths.isEmpty()) {
            List<Element> names = SafeXml.children(root, "name");
            String plugin = names.isEmpty() || names.getFirst().getTextContent().isBlank() ? jar.label() : names.getFirst().getTextContent().strip();
            problems.add(plugin + " provides no theme files; Jasper can't use themes drawn in code");
            return;
        }
        for (String path : paths) theme(jar, path, themes, problems);
    }

    /** Every themeProvider path, in either declaration form. */
    private static List<String> providers(Element root) {
        var paths = new ArrayList<String>();
        NodeList extensions = root.getElementsByTagName("extensions");
        for (int i = 0; i < extensions.getLength(); i++) {
            var section = (Element) extensions.item(i);
            boolean intellij = "com.intellij".equals(section.getAttribute("defaultExtensionNs"));
            for (Element child : SafeXml.children(section)) {
                String tag = child.getTagName();
                if ((intellij && tag.equals("themeProvider")) || tag.equals("com.intellij.themeProvider")) {
                    String path = child.getAttribute("path");
                    if (!path.isBlank()) paths.add(path);
                }
            }
        }
        return paths;
    }

    private static void theme(Jar jar, String path, List<Found> themes, List<String> problems) {
        String entry = entry(path, "");
        if (entry == null) {
            problems.add(jar.label() + ": theme path \"" + path + "\" leaves the plugin; skipped");
            return;
        }
        String label = jar.label() + "!/" + entry;
        byte[] bytes;
        try { bytes = jar.read(entry); }
        catch (TooLarge tooLarge) { problems.add(label + " is larger than 4 MiB; skipped"); return; }
        if (bytes == null) {
            problems.add(label + " is named by plugin.xml but missing; skipped");
            return;
        }
        String json = new String(bytes, StandardCharsets.UTF_8);
        byte[] schemeXml = null;
        String schemeName = null, schemeLabel = null;
        String scheme = editorScheme(json);
        if (scheme != null && !scheme.isBlank()) {
            if (!schemePath(scheme)) { schemeName = scheme; schemeLabel = label; }
            else {
                String schemeEntry = entry(scheme, directory(entry));
                if (schemeEntry == null)
                    problems.add(label + ": editor scheme \"" + scheme + "\" leaves the plugin; the terminal uses Jasper's palette");
                else {
                    schemeLabel = jar.label() + "!/" + schemeEntry;
                    try {
                        schemeXml = jar.read(schemeEntry);
                        if (schemeXml == null)
                            problems.add(schemeLabel + " is named by " + label + " but missing; the terminal uses Jasper's palette");
                    } catch (TooLarge tooLarge) {
                        problems.add(schemeLabel + " is larger than 4 MiB; the terminal uses Jasper's palette");
                    }
                }
            }
        }
        themes.add(new Found(label, json, schemeXml, schemeName, schemeLabel));
    }

    /** The theme's {@code editorScheme}, or null; an unparsable theme is left for ThemeLoader to report. */
    private static String editorScheme(String json) {
        try {
            return Json.parse(new StringReader(json)) instanceof Map<?, ?> map && map.get("editorScheme") instanceof String scheme ? scheme : null;
        } catch (IOException | ParseException failure) {
            return null;
        }
    }

    private static boolean schemePath(String scheme) {
        String lower = scheme.toLowerCase(Locale.ROOT);
        return scheme.contains("/") || lower.endsWith(".xml") || lower.endsWith(".icls");
    }

    /** The jar entry for {@code path}: from the jar root when it starts with {@code /}, else from {@code directory}; null when it leaves the jar. */
    private static String entry(String path, String directory) {
        String joined = path.startsWith("/") ? path.substring(1) : directory + path;
        var parts = new ArrayList<String>();
        for (String part : joined.replace('\\', '/').split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) return null;
            parts.add(part);
        }
        return parts.isEmpty() ? null : String.join("/", parts);
    }

    private static String directory(String entry) {
        int slash = entry.lastIndexOf('/');
        return slash < 0 ? "" : entry.substring(0, slash + 1);
    }

    private static boolean libJar(String name) {
        return name.matches("[^/]+/lib/[^/]+") && name.toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    private static Map<String, byte[]> fromZip(ZipFile zip, Set<String> wanted) {
        var found = new HashMap<String, byte[]>();
        for (String name : wanted) {
            ZipEntry entry = zip.getEntry(name);
            if (entry != null && !entry.isDirectory()) found.put(name, entryBytes(zip, entry, MAX_FILE));
        }
        return found;
    }

    private static byte[] entryBytes(ZipFile zip, ZipEntry entry, int limit) {
        try (InputStream in = zip.getInputStream(entry)) {
            return capped(in, limit);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** One streaming pass over an in-memory jar, keeping the wanted entries. */
    private static Map<String, byte[]> fromBytes(byte[] jar, Set<String> wanted) {
        var found = new HashMap<String, byte[]>();
        try (var in = new ZipInputStream(new ByteArrayInputStream(jar))) {
            int count = 0;
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                if (++count > MAX_ENTRIES) throw new IOException("a nested jar has more than 10,000 entries");
                if (!entry.isDirectory() && wanted.contains(entry.getName())) found.put(entry.getName(), capped(in, MAX_FILE));
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return found;
    }

    private static byte[] capped(InputStream in, int limit) throws IOException {
        byte[] bytes = in.readNBytes(limit + 1);
        if (bytes.length > limit) throw new TooLarge();
        return bytes;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests '*ThemeArchiveTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/appearance/ThemeArchive.java \
  jasper-app/src/test/java/dev/jasper/app/appearance/ThemeArchiveTest.java
git commit -m "feat(appearance): read IntelliJ theme plugins in place

A plugin .jar, or a Marketplace .zip of lib/*.jar: plugin.xml names each theme,
and a theme may name its editor scheme. Reads are capped and paths may not leave
the plugin; problems are returned, never thrown.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: Theme sources and the theme catalog

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/Theme.java`, `ThemeManager.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeCatalog.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeCatalogTest.java`, `ThemeManagerTest.java`

**Interfaces:**
- Consumes:
  - `ThemeArchive.read` and `Found` (Task 4);
  - `EditorScheme.read`, `EditorScheme.named` and `Result` (Task 3);
  - `ThemeLoader.Resolved.warnings()` (Task 2);
  - `ThemeFixtures` (Task 3).
- Produces:
  - `Theme(String id, String name, boolean dark, Palette palette, Theme.Source source)`, where `Source` is sealed with two records: `Theme.Resource(String file)` and `Theme.Archive(Path archive, String entry, String json)`. `Theme.of(Appearance)` and `appearance()` stay until Task 6.
  - `ThemeManager.load(Theme.Source) -> ThemeLoader.Resolved`, which is safe off the Event Dispatch Thread.
  - The `ThemeCatalog` public API:
    - `ThemeCatalog.builtIn()`;
    - `ThemeCatalog.beside(Path configFile)`;
    - `scan() -> Snapshot` (synchronized, file work);
    - `latest() -> Snapshot`.
  - The records `ThemeCatalog.Snapshot(List<Theme> themes, List<Problem> problems)`, with `find(String name) -> Optional<Theme>` and `names() -> List<String>`, and `ThemeCatalog.Problem(Path file, String message)`.
  - The package-private constructor `ThemeCatalog(Path directory, Function<Path, ThemeArchive.Contents> reader)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeCatalogTest.java`:

```java
package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.appearance.ThemeFixtures.*;
import static org.assertj.core.api.Assertions.*;

class ThemeCatalogTest {
    @TempDir Path home;

    private Path themes() { return home.resolve("themes"); }
    private ThemeCatalog catalog() { return ThemeCatalog.beside(home.resolve("config.toml")); }

    @Test void theBuiltInCatalogFindsBuiltInThemesByNameIgnoringCaseAndSpaces() {
        var snapshot = ThemeCatalog.builtIn().scan();
        assertThat(snapshot.themes()).containsExactly(Theme.LIGHT, Theme.DARK);
        assertThat(snapshot.find("  jasper DARK ")).contains(Theme.DARK);
        assertThat(snapshot.find("intellij light")).contains(Theme.LIGHT);
        assertThat(snapshot.find("")).isEmpty();
        assertThat(snapshot.find("Dracula")).isEmpty();
        assertThat(snapshot.names()).containsExactly("IntelliJ Light", "Jasper Dark");
        assertThat(snapshot.problems()).isEmpty();
    }

    @Test void anInstalledThemeCarriesItsResolvedNameDarknessAndSchemePalette() {
        plugin(themes().resolve("fixture.jar"), "Fixture Dark", true, "#123456", "101820");
        var snapshot = catalog().scan();
        assertThat(snapshot.problems()).isEmpty();
        Theme theme = snapshot.find("fixture dark").orElseThrow();
        assertThat(theme.name()).isEqualTo("Fixture Dark");
        assertThat(theme.dark()).isTrue();
        assertThat(theme.id()).isEqualTo("installed:fixture.jar!/themes/Fixture_Dark.theme.json");
        assertThat(theme.palette().background()).isEqualTo(new Color(0x101820));
        assertThat(theme.palette().ansi().get(1)).isEqualTo(new Color(0xff5555));
        assertThat(theme.palette().ansi().get(2)).as("Darcula's green").isEqualTo(new Color(0x5c962c));
        assertThat(theme.source()).isInstanceOfSatisfying(Theme.Archive.class,
            archive -> assertThat(archive.archive()).isEqualTo(themes().resolve("fixture.jar")));
        assertThat(ThemeManager.load(theme.source()).name()).isEqualTo("Fixture Dark");
        assertThat(snapshot.themes()).as("installed themes come first").startsWith(theme).endsWith(Theme.LIGHT, Theme.DARK);
        assertThat(catalog().latest().themes()).as("a new catalog has scanned nothing yet").containsExactly(Theme.LIGHT, Theme.DARK);
    }

    @Test void anInstalledThemeTakesPrecedenceOverABuiltInThemeOfTheSameName() {
        plugin(themes().resolve("mine.jar"), "Jasper Dark", true, "#010203", "010203");
        var snapshot = catalog().scan();
        assertThat(snapshot.find("Jasper Dark").map(Theme::source)).get().isInstanceOf(Theme.Archive.class);
        assertThat(snapshot.themes()).doesNotContain(Theme.DARK).contains(Theme.LIGHT);
    }

    @Test void duplicateInstalledNamesWarnAndTheAlphabeticallyFirstFileWins() {
        plugin(themes().resolve("b.jar"), "Twin", true, "#bbbbbb", "0b0b0b");
        plugin(themes().resolve("A.jar"), "Twin", true, "#aaaaaa", "0a0a0a");
        var snapshot = catalog().scan();
        assertThat(snapshot.find("Twin").map(Theme::source)).get().isInstanceOfSatisfying(Theme.Archive.class,
            archive -> assertThat(archive.archive().getFileName()).hasToString("A.jar"));
        assertThat(snapshot.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.file().getFileName()).hasToString("b.jar");
            assertThat(problem.message()).contains("Twin").contains("A.jar");
        });
    }

    @Test void aRescanRereadsOnlyFilesWhoseSizeOrTimeChanged() throws Exception {
        plugin(themes().resolve("one.jar"), "One", true, "#111111", "111111");
        plugin(themes().resolve("two.jar"), "Two", false, "#222222", "222222");
        var reads = new ArrayList<String>();
        var catalog = new ThemeCatalog(themes(), file -> { reads.add(file.getFileName().toString()); return ThemeArchive.read(file); });
        Theme one = catalog.scan().find("One").orElseThrow();
        assertThat(catalog.scan().find("One")).as("an unchanged file keeps its Theme instance").containsSame(one);
        assertThat(reads).containsExactlyInAnyOrder("one.jar", "two.jar");
        plugin(themes().resolve("two.jar"), "Two Again", false, "#232323", "232323");
        Files.setLastModifiedTime(themes().resolve("two.jar"), FileTime.fromMillis(System.currentTimeMillis() + 60_000));
        var snapshot = catalog.scan();
        assertThat(reads).containsExactlyInAnyOrder("one.jar", "two.jar", "two.jar");
        assertThat(snapshot.find("Two Again")).isPresent();
        assertThat(snapshot.find("Two")).isEmpty();
        Files.delete(themes().resolve("one.jar"));
        assertThat(catalog.scan().find("One")).isEmpty();
        assertThat(catalog.latest().find("Two Again")).isPresent();
    }

    @Test void onlyPluginArchivesAreReadAndLooseThemeFilesAreExplained() throws Exception {
        Files.createDirectories(themes().resolve("folder"));
        Files.writeString(themes().resolve("notes.txt"), "hello");
        Files.writeString(themes().resolve("Dracula.theme.json"), theme("Dracula", true, null, "#282a36"));
        plugin(themes().resolve(".hidden.jar"), "Hidden", true, "#000000", "000000");
        var snapshot = catalog().scan();
        assertThat(snapshot.names()).containsExactly("IntelliJ Light", "Jasper Dark");
        assertThat(snapshot.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.file().getFileName()).hasToString("Dracula.theme.json");
            assertThat(problem.message()).contains("aren't supported").contains(".jar or .zip");
        });
    }

    @Test void theFolderIsCreatedAndAnUnusableOneIsAWarning() throws Exception {
        catalog().scan();
        assertThat(themes()).isDirectory();
        Path other = Files.createDirectories(home.resolve("other"));
        Files.writeString(other.resolve("themes"), "a file, not a folder");
        var snapshot = ThemeCatalog.beside(other.resolve("config.toml")).scan();
        assertThat(snapshot.themes()).containsExactly(Theme.LIGHT, Theme.DARK);
        assertThat(snapshot.problems()).singleElement().satisfies(problem ->
            assertThat(problem.message()).contains("Cannot read the themes folder"));
    }

    @Test void brokenThemesAndLoaderWarningsBecomeProblemsWhileGoodThemesLoad() {
        jar(themes().resolve("mixed.jar"), entries(
            "META-INF/plugin.xml", pluginXml("Mixed", "/good.theme.json", "/bad.theme.json", "/newui.theme.json"),
            "good.theme.json", theme("Good", false, null, "#fefefe"),
            "bad.theme.json", "{\"name\": \"Bad\", \"colors\": {\"a\": \"nowhere\"}}",
            "newui.theme.json", "{\"name\": \"New UI\", \"dark\": true, \"parentTheme\": \"ExperimentalDark\"}"));
        var snapshot = catalog().scan();
        assertThat(snapshot.find("Good").map(Theme::palette)).contains(Palette.jasperLight());
        assertThat(snapshot.find("Bad")).isEmpty();
        assertThat(snapshot.find("New UI")).isPresent();
        assertThat(snapshot.problems()).extracting(ThemeCatalog.Problem::message)
            .anySatisfy(message -> assertThat(message).contains("mixed.jar!/bad.theme.json").contains("nowhere"))
            .anySatisfy(message -> assertThat(message).contains("ExperimentalDark").contains("Darcula"));
    }
}
```

Add to `ThemeManagerTest.java`. It already runs on the EDT through `EdtTestExtension`. Add the imports `java.nio.file.Path` and `org.junit.jupiter.api.io.TempDir`.

```java
    @Test void anInstalledThemeInstallsWithItsOwnColoursAndDerivedChromeKeys(@TempDir Path home) throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            ThemeFixtures.plugin(home.resolve("themes/fixture.jar"), "Fixture Dark", true, "#123456", "101820");
            Theme theme = ThemeCatalog.beside(home.resolve("config.toml")).scan().find("Fixture Dark").orElseThrow();
            assertThat(ThemeManager.install(theme)).isTrue();
            assertThat(UIManager.getLookAndFeel().getName()).isEqualTo("Fixture Dark");
            assertThat(UIManager.getColor("Panel.background")).isEqualTo(new Color(0x123456));
            assertThat(FlatLaf.isLafDark()).isTrue();
            assertThat(UIManager.getColor("Jasper.titleBackground")).isNotNull();
        } finally { UIManager.setLookAndFeel(original); }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*ThemeCatalogTest' --tests '*ThemeManagerTest'`
Expected: FAIL to compile, because `ThemeCatalog`, `Theme.Archive` and `ThemeManager.load` do not exist.

- [ ] **Step 3: Give Theme a source**

Replace `jasper-app/src/main/java/dev/jasper/app/appearance/Theme.java` with the following. It keeps `of(Appearance)` and `appearance()`, which Task 6 removes.

```java
package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.terminal.config.Palette;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A theme Jasper can install: an IntelliJ-format theme and the terminal palette used when the
 * terminal matches the UI (its editor scheme's console colours, or a Jasper palette).
 */
public record Theme(String id, String name, boolean dark, Palette palette, Source source) {
    /** Where a theme's {@code .theme.json} comes from. */
    public sealed interface Source permits Resource, Archive {}

    /** A theme file under {@code dev/jasper/app/themes/}. */
    public record Resource(String file) implements Source {
        public Resource { Objects.requireNonNull(file); }
    }

    /** A theme an installed plugin provides; its JSON is read when the catalog reads {@code archive}. */
    public record Archive(Path archive, String entry, String json) implements Source {
        public Archive { Objects.requireNonNull(archive); Objects.requireNonNull(entry); Objects.requireNonNull(json); }
    }

    /** Classic IntelliJ Light, vendored from intellij-community. */
    public static final Theme LIGHT = new Theme("intellij-light", "IntelliJ Light", false, Palette.jasperLight(),
        new Resource("intellij/Light.theme.json"));
    /** Jasper's own dark theme. */
    public static final Theme DARK = new Theme("jasper-dark", "Jasper Dark", true, Palette.jasperDark(),
        new Resource("jasper-dark.theme.json"));

    public Theme {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(palette);
        Objects.requireNonNull(source);
    }

    static Theme of(Appearance appearance) { return appearance == Appearance.LIGHT ? LIGHT : DARK; }

    public Appearance appearance() { return dark ? Appearance.DARK : Appearance.LIGHT; }
}
```

In `ThemeManager.java`, replace the `resolve` method with:

```java
    static ThemeLoader.Resolved resolve(Theme theme) { return RESOLVED.computeIfAbsent(theme, key -> load(key.source())); }

    /** Resolves a theme's file; touches no Swing state, so it is safe off the EDT. */
    static ThemeLoader.Resolved load(Theme.Source source) {
        return switch (source) {
            case Theme.Resource resource -> LOADER.load(resource.file());
            // Parents a plugin theme names resolve against the chains Jasper ships.
            case Theme.Archive archive ->
                new ThemeLoader(file -> file.equals(archive.entry()) ? archive.json() : read(file), FILES).load(archive.entry());
        };
    }
```

- [ ] **Step 4: Implement ThemeCatalog**

Create `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeCatalog.java`:

```java
package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Every theme Jasper can install: the plugins in the {@code themes} folder beside {@code config.toml},
 * then the built-in themes. An installed theme takes precedence over a built-in theme of the same name.
 * {@link #scan()} does the file work and may run on any thread; {@link #latest()} may be read anywhere.
 */
public final class ThemeCatalog {
    /** A problem with one file in the themes folder, shown as a configuration warning. */
    public record Problem(Path file, String message) {
        public Problem { Objects.requireNonNull(file); Objects.requireNonNull(message); }
    }

    /** One scan: the themes in precedence order, and the problems met. */
    public record Snapshot(List<Theme> themes, List<Problem> problems) {
        public Snapshot { themes = List.copyOf(themes); problems = List.copyOf(problems); }

        /** The theme with this name, ignoring case and surrounding whitespace; none for a blank name. */
        public Optional<Theme> find(String name) {
            String key = key(name);
            if (key.isEmpty()) return Optional.empty();
            return themes.stream().filter(theme -> key(theme.name()).equals(key)).findFirst();
        }

        /** Every theme's name, sorted ignoring case. */
        public List<String> names() {
            return themes.stream().map(Theme::name).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
    }

    private record Stamp(long size, FileTime modified) {}
    private record Read(Stamp stamp, List<Theme> themes, List<Problem> problems) {}

    private static final Snapshot BUILT_IN = new Snapshot(ThemeManager.builtIns(), List.of());

    private final Path directory;
    private final Function<Path, ThemeArchive.Contents> reader;
    /** Each archive's last read, by path; guarded by this. */
    private Map<Path, Read> reads = Map.of();
    private volatile Snapshot latest = BUILT_IN;

    ThemeCatalog(Path directory, Function<Path, ThemeArchive.Contents> reader) {
        this.directory = directory;
        this.reader = Objects.requireNonNull(reader);
    }

    /** Only the built-in themes; nothing is ever scanned. */
    public static ThemeCatalog builtIn() { return new ThemeCatalog(null, ThemeArchive::read); }

    /** The {@code themes} folder in the directory holding {@code configFile}. */
    public static ThemeCatalog beside(Path configFile) {
        return new ThemeCatalog(configFile.toAbsolutePath().getParent().resolve("themes"), ThemeArchive::read);
    }

    /** The last scan's result; the built-in themes before the first scan. */
    public Snapshot latest() { return latest; }

    /** Reads the themes folder, creating it when missing, and re-reads only files whose size or modified time changed. */
    public synchronized Snapshot scan() {
        if (directory == null) return latest;
        var problems = new ArrayList<Problem>();
        var next = new HashMap<Path, Read>();
        var installed = new ArrayList<Theme>();
        var owners = new HashMap<String, Path>();
        for (Path file : files(problems)) {
            String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".theme.json")) {
                problems.add(new Problem(file, "Standalone .theme.json files aren't supported; install the theme plugin's .jar or .zip instead."));
                continue;
            }
            if (!lower.endsWith(".jar") && !lower.endsWith(".zip")) continue;
            Read read = read(file);
            if (read == null) continue;
            next.put(file, read);
            problems.addAll(read.problems());
            for (Theme theme : read.themes()) {
                Path owner = owners.putIfAbsent(key(theme.name()), file);
                if (owner == null) installed.add(theme);
                else if (owner.equals(file)) problems.add(new Problem(file, "Theme \"" + theme.name() + "\" is provided twice; the first is used."));
                else problems.add(new Problem(file, "Theme \"" + theme.name() + "\" is also installed from " + owner.getFileName() + ", which is used."));
            }
        }
        reads = next;
        var themes = new ArrayList<>(installed);
        for (Theme builtIn : ThemeManager.builtIns()) if (!owners.containsKey(key(builtIn.name()))) themes.add(builtIn);
        return latest = new Snapshot(themes, problems);
    }

    /** Regular, non-hidden files in the folder, sorted by name ignoring case. */
    private List<Path> files(List<Problem> problems) {
        try {
            Files.createDirectories(directory);
            try (Stream<Path> listing = Files.list(directory)) {
                return listing.filter(file -> !file.getFileName().toString().startsWith("."))
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing((Path file) -> file.getFileName().toString(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(file -> file.getFileName().toString()))
                    .toList();
            }
        } catch (IOException | SecurityException failure) {
            problems.add(new Problem(directory, "Cannot read the themes folder: " + failure));
            return List.of();
        }
    }

    /** The archive's themes, from the last read when its size and modified time are unchanged; null when it vanished. */
    private Read read(Path file) {
        Stamp stamp;
        try {
            var attributes = Files.readAttributes(file, BasicFileAttributes.class);
            stamp = new Stamp(attributes.size(), attributes.lastModifiedTime());
        } catch (IOException | SecurityException failure) {
            return null;
        }
        Read previous = reads.get(file);
        if (previous != null && previous.stamp().equals(stamp)) return previous;
        ThemeArchive.Contents contents;
        try { contents = reader.apply(file); }
        catch (RuntimeException failure) {
            return new Read(stamp, List.of(), List.of(new Problem(file, file.getFileName() + " cannot be read: " + failure)));
        }
        var problems = new ArrayList<Problem>();
        contents.problems().forEach(problem -> problems.add(new Problem(file, problem)));
        var themes = new ArrayList<Theme>();
        for (ThemeArchive.Found found : contents.themes()) {
            var source = new Theme.Archive(file, found.label(), found.json());
            ThemeLoader.Resolved resolved;
            try { resolved = ThemeManager.load(source); }
            catch (ThemeLoader.ThemeException failure) { problems.add(new Problem(file, failure.getMessage())); continue; }
            catch (RuntimeException failure) { problems.add(new Problem(file, found.label() + " cannot be loaded: " + failure)); continue; }
            resolved.warnings().forEach(warning -> problems.add(new Problem(file, warning)));
            EditorScheme.Result scheme = scheme(found, resolved.dark());
            scheme.warnings().forEach(warning -> problems.add(new Problem(file, warning)));
            themes.add(new Theme("installed:" + found.label(), resolved.name(), resolved.dark(), scheme.palette(), source));
        }
        return new Read(stamp, themes, problems);
    }

    private static EditorScheme.Result scheme(ThemeArchive.Found found, boolean dark) {
        if (found.schemeXml() != null) return EditorScheme.read(found.schemeXml(), found.schemeLabel(), dark);
        if (found.schemeName() != null) return EditorScheme.named(found.schemeName(), found.schemeLabel(), dark);
        return new EditorScheme.Result(dark ? Palette.jasperDark() : Palette.jasperLight(), List.of());
    }

    private static String key(String name) { return name.strip().toLowerCase(Locale.ROOT); }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/appearance/Theme.java jasper-app/src/main/java/dev/jasper/app/appearance/ThemeManager.java \
  jasper-app/src/main/java/dev/jasper/app/appearance/ThemeCatalog.java \
  jasper-app/src/test/java/dev/jasper/app/appearance/ThemeCatalogTest.java jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java
git commit -m "feat(appearance): catalog the themes installed beside the configuration

Theme gains a source, a bundled resource or a plugin archive, so an installed theme
installs through ThemeManager like a built-in one. ThemeCatalog lists the themes
folder (installed first, then the built-ins), re-reads only changed files and
collects every problem.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: `ui.theme.name` replaces the variant

**Files:**
- Delete: `jasper-app/src/main/java/dev/jasper/app/config/Appearance.java`
- Modify:
  - `config/ConfigSnapshot.java`, `ConfigLoader.java`, `ConfigTemplate.java` and `TerminalColors.java` (its class comment);
  - `config.example.toml`;
  - `appearance/Theme.java`, `ThemeState.java` and `ThemeController.java`;
  - `workspace/WindowContent.java`, `WindowChrome.java` and `WindowCommands.java`;
  - `windows/NativeShells.java`;
  - `application/ConfigurationController.java` and `JasperApplication.java`.
- Test: the migration script, run over `jasper-app/src/test/java`, then the hand edits below, plus `ThemeTestSupport.java`.

**Interfaces:**
- Consumes:
  - `ThemeCatalog.builtIn().latest().find(String)` (Task 5).
- Produces:
  - `ConfigSnapshot.theme()` (String), `ConfigSnapshot.DEFAULT_THEME = "Jasper Dark"` and `ConfigSnapshot.Builder.theme(String)`;
  - the `ThemeController` API:
    - constructors `ThemeController()`, `ThemeController(Theme)`, `ThemeController(Predicate<Theme>)` and the package-private `ThemeController(Theme, Predicate<Theme>)`;
    - `choice() -> Theme`;
    - `configure(Theme)`, `configure(Theme, UiFontConfig)` and `configure(Theme, TerminalColors, UiFontConfig)`;
    - `selectAppearance` and `select` are removed;
  - `ThemeState(Theme saved, TerminalColors terminalSaved, TerminalColors terminalOverride)`;
  - `WindowContent.selectTheme(Theme)`, which now configures the controller; `appearance()` and `selectAppearance` are removed;
  - the View submenu is `"Terminal Colors"`, holding exactly the three `view.terminal_colors.*` items;
  - the test helper `ThemeTestSupport.named(String) -> Theme`.

- [ ] **Step 1: Migrate the tests with the rewrite script**

Run this from the repository root. It rewrites every test source mechanically: the `variant` values and accessors, `Appearance.*`, and the theme-switch calls. It adds the `Theme` import wherever it is needed.

```bash
python3 - <<'PY'
import pathlib, re
RULES = [
    # TOML: the two built-in variants become theme names; the key and its messages follow.
    (r"variant(\s*=\s*)(['\"])light\2", r"name\1\2IntelliJ Light\2"),
    (r"variant(\s*=\s*)(['\"])dark\2", r"name\1\2Jasper Dark\2"),
    (r"ui\.theme\.variant=1", "ui.theme.name=1"),
    (r'"ui\.theme\.variant"', '"ui.theme.name"'),
    (r'"variant", "terminal"', '"name", "terminal"'),
    # The snapshot's theme is a name now.
    (r"\.variant\(\)\)((?:\s*\.as\([^()]*\))?\s*)\.isEqualTo\(Appearance\.LIGHT\)", r'.theme())\1.isEqualTo("IntelliJ Light")'),
    (r"\.variant\(\)\)((?:\s*\.as\([^()]*\))?\s*)\.isEqualTo\(Appearance\.DARK\)", r'.theme())\1.isEqualTo("Jasper Dark")'),
    # Not the SDK's Appearance.variant() or its THEME_CHANGED event's variant().
    (r"(?<!appearance\(\))(?<!event)\.variant\(\)", ".theme()"),
    (r"Theme\.(LIGHT|DARK)\.appearance\(\)", r"Theme.\1.name()"),
    (r"\(Appearance\) null", "(String) null"),
    (r"dev\.jasper\.app\.config\.Appearance\.(LIGHT|DARK)", r"dev.jasper.app.appearance.Theme.\1"),
    (r"import dev\.jasper\.app\.config\.Appearance;\n", ""),
    (r"for \(Appearance (\w+) : Appearance\.values\(\)\)",
     r"for (dev.jasper.app.appearance.Theme \1 : dev.jasper.app.appearance.ThemeManager.builtIns())"),
    (r"\bAppearance\.(LIGHT|DARK)\b", r"Theme.\1"),
    # A ThemeController configures its theme; a window selects one.
    (r"\b(themes(?:\[\d\])?|theme)\.selectAppearance\(", r"\1.configure("),
    (r"\.selectAppearance\(", ".selectTheme("),
    (r"\b(themes(?:\[\d\])?)\.select\(", r"\1.configure("),
    (r'"view\.appearance\.dark"', '"view.terminal_colors.dark"'),
]
changed = []
for path in pathlib.Path("jasper-app/src/test/java").rglob("*.java"):
    text = original = path.read_text(encoding="utf-8")
    for pattern, replacement in RULES:
        text = re.sub(pattern, replacement, text)
    in_appearance = "package dev.jasper.app.appearance;" in text
    has_import = re.search(r"import dev\.jasper\.app\.appearance\.(Theme|\*);", text)
    if re.search(r"(?<![.\w])Theme\.(LIGHT|DARK)\b", text) and not in_appearance and not has_import:
        text = re.sub(r"^import ", "import dev.jasper.app.appearance.Theme;\nimport ", text, count=1, flags=re.M)
    if text != original:
        path.write_text(text, encoding="utf-8")
        changed.append(str(path))
print(len(changed), "files changed")
for name in sorted(changed): print(name)
PY
```

Expected: about 40 files changed, including `ConfigLoaderTest`, `ExpandedConfigTest`, `ConfigurationControllerTest`, `ThemeControllerTest`, `TerminalColorsThemeTest` and `WindowCommandsTest`.

- [ ] **Step 2: Rewrite the tests whose meaning changed**

Make these edits by hand. Each "replace" names text as it reads after Step 1.

1. **`jasper-app/src/test/java/dev/jasper/app/appearance/ThemeStateTest.java`**: replace the whole file.

```java
package dev.jasper.app.appearance;

import dev.jasper.app.config.TerminalColors;
import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ThemeStateTest {
    private static final Palette SCHEME = new Palette(Color.WHITE, new Color(0x101820), Color.WHITE, Color.GRAY,
        Collections.nCopies(16, Color.RED));
    private static final Theme INSTALLED = new Theme("installed:fixture", "Fixture", true, SCHEME,
        new Theme.Resource("jasper-dark.theme.json"));

    @Test void theConfiguredThemeResolvesWithItsOwnPalette() {
        assertThat(ThemeState.defaults().resolve()).isEqualTo(new ResolvedTheme(Theme.DARK, Palette.jasperDark()));
        assertThat(ThemeState.defaults().configure(Theme.LIGHT).resolve()).isEqualTo(new ResolvedTheme(Theme.LIGHT, Palette.jasperLight()));
        assertThat(ThemeState.defaults().configure(INSTALLED).resolve()).isEqualTo(new ResolvedTheme(INSTALLED, SCHEME));
    }

    @Test void terminalChoiceResolvesThePaletteIndependentlyOfTheChrome() {
        for (Theme chrome : List.of(Theme.LIGHT, Theme.DARK, INSTALLED)) {
            var base = ThemeState.defaults().configure(chrome);
            assertThat(base.configureTerminal(TerminalColors.MATCH).resolve()).isEqualTo(new ResolvedTheme(chrome, chrome.palette()));
            assertThat(base.configureTerminal(TerminalColors.LIGHT).resolve()).isEqualTo(new ResolvedTheme(chrome, Palette.jasperLight()));
            assertThat(base.configureTerminal(TerminalColors.DARK).resolve()).isEqualTo(new ResolvedTheme(chrome, Palette.jasperDark()));
        }
    }

    @Test void temporaryTerminalChoiceOverridesSavedUntilTheSavedValueChanges() {
        var chosen = ThemeState.defaults().chooseTerminal(TerminalColors.LIGHT);
        assertThat(chosen.terminalChoice()).isEqualTo(TerminalColors.LIGHT);
        assertThat(chosen.configureTerminal(TerminalColors.MATCH).terminalChoice())
            .as("rewriting the same saved value keeps the session choice").isEqualTo(TerminalColors.LIGHT);
        assertThat(chosen.configureTerminal(TerminalColors.DARK).terminalOverride()).isNull();
        assertThat(chosen.configureTerminal(TerminalColors.DARK).terminalChoice()).isEqualTo(TerminalColors.DARK);
        assertThat(chosen.configure(Theme.LIGHT).terminalChoice())
            .as("a theme change keeps the terminal choice").isEqualTo(TerminalColors.LIGHT);
    }
}
```

2. **`jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java`**: add inside the class:

```java
    /** A built-in theme by name, or Jasper Dark: what start-up picks for a name no theme has. */
    public static Theme named(String name) { return ThemeCatalog.builtIn().latest().find(name).orElse(Theme.DARK); }
```

3. **`jasper-app/src/test/java/dev/jasper/app/workspace/ThemeControllerTest.java`**:
   - Replace `appearance(owners[0]).getItem(0).doClick();` with `owners[0].selectTheme(Theme.LIGHT);`.
   - Replace `appearance(owners[1]).getItem(0).doClick();` with `owners[1].selectTheme(Theme.LIGHT);`.
   - Replace `assertThat(appearance(owners[0]).getItem(0).isSelected()).isTrue();` with `assertThat(themes[0].choice()).isEqualTo(Theme.LIGHT);`.
   - Replace `appearance(owners[1]).getItem(1).doClick();` with `owners[1].selectTheme(Theme.DARK);`.
   - Replace `assertThat(appearance(owners[1]).getItem(1).isSelected()).isTrue();` with `assertThat(themes[0].choice()).isEqualTo(Theme.DARK);`.
   - Replace `appearance(owner[0]).getItem(0).doClick();` with `owner[0].selectTheme(Theme.LIGHT);`.
   - Delete the test `temporaryChoiceSurvivesAnUnchangedSavedVariantAndClearsWhenItChanges` entirely, and the `static JMenu appearance(WindowContent owner)` helper at the end of the class.
   - Replace the whole test `failedInstallationKeepsPreviousThemeAndResetsMenuSelection` with:

```java
    @Test void failedInstallationKeepsThePreviousThemeAndReportsTheError() throws Exception {
        edt(() -> {
            var themes = new ThemeController(theme -> theme != Theme.LIGHT && ThemeTestSupport.install(theme));
            var owner = content(launcher(new ArrayDeque<>()), themes);
            var errors = new ArrayList<String>(); owner.onError = errors::add;
            var before = UIManager.getLookAndFeel();
            owner.selectTheme(Theme.LIGHT);
            assertThat(themes.current().chrome()).isEqualTo(Theme.DARK);
            assertThat(themes.choice()).isEqualTo(Theme.DARK);
            assertThat(UIManager.getLookAndFeel()).isSameAs(before);
            assertThat(errors).singleElement().asString().contains("Could not apply theme");
        });
    }
```

4. **`jasper-app/src/test/java/dev/jasper/app/workspace/WindowChromeTest.java`**: replace the whole test `openingSiblingAppearanceMenuReflectsTheCurrentGlobalTheme` (through its closing brace) and the `appearance` helper at the end of the class with:

```java
    @Test void openingSiblingTerminalColorsMenuReflectsTheCurrentGlobalChoice() throws Exception {
        edt(() -> {
            WindowContent first = null;
            WindowContent second = null;
            try {
                var themes = new ThemeController();
                first = content(launcher(new ArrayDeque<>()), themes);
                second = content(launcher(new ArrayDeque<>()), themes);
                JMenu firstColors = terminalColors(first);
                JMenu secondColors = terminalColors(second);
                firstColors.getItem(1).doClick();
                secondColors.setSelected(true);
                assertThat(secondColors.getItem(1).isSelected()).isTrue();
                assertThat(secondColors.getItem(0).isSelected()).isFalse();
                secondColors.setSelected(false);
                firstColors.getItem(2).doClick();
                secondColors.setSelected(true);
                assertThat(secondColors.getItem(2).isSelected()).isTrue();
                assertThat(secondColors.getItem(1).isSelected()).isFalse();
            } finally {
                if (first != null) first.close();
                if (second != null) second.close();
            }
        });
    }

    @Test void theViewMenuOffersTerminalColorsButNoThemeChoice() throws Exception {
        edt(() -> {
            try (var owner = content(launcher(new ArrayDeque<>()))) {
                var names = new java.util.ArrayList<String>();
                for (var component : owner.menuBar().getMenu(2).getMenuComponents())
                    if (component instanceof JMenuItem item) names.add(item.getText());
                assertThat(names).contains("Terminal Colors").doesNotContain("Appearance");
                assertThat(java.util.Arrays.stream(terminalColors(owner).getMenuComponents())
                    .filter(JMenuItem.class::isInstance).map(item -> ((JMenuItem) item).getText()))
                    .containsExactly("Terminal: Match UI", "Terminal: Light", "Terminal: Dark");
                assertThat(owner.commands().entries().stream().map(entry -> entry.command().id()))
                    .noneMatch(id -> id.startsWith("view.appearance."));
            }
        });
    }

    private static JMenu terminalColors(WindowContent owner) {
        for (var component : owner.menuBar().getMenu(2).getMenuComponents()) {
            if (component instanceof JMenu menu && menu.getText().equals("Terminal Colors")) return menu;
        }
        throw new AssertionError("Terminal Colors menu is absent");
    }
```

5. **`jasper-app/src/test/java/dev/jasper/app/workspace/TerminalColorsTest.java`**:
   - Rename the helper `appearance(WindowContent owner)` to `terminalColors(WindowContent owner)` and its call `JMenu menu = appearance(owner);` to `JMenu menu = terminalColors(owner);`.
   - In the helper, replace `menu.getText().equals("Appearance")` with `menu.getText().equals("Terminal Colors")`, and `"Appearance menu is absent"` with `"Terminal Colors menu is absent"`.
   - Rename the test `appearanceMenuOffersTerminalChoicesThatApplyLiveAndReflectTheCurrentChoice` to `terminalColorsMenuOffersChoicesThatApplyLiveAndReflectTheCurrentChoice`.

6. **`jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java`**:
   - In `start`, replace `new ThemeController(service.initialState().snapshot().theme())` with `new ThemeController(ThemeTestSupport.named(service.initialState().snapshot().theme()))`.
   - In `unrelatedChangesPreserveTemporaryChoicesAndNewOwnersUseSavedDefaults`:
     - Replace `first.setStatusVisible(false); first.selectTheme(Theme.LIGHT); first.currentPane().view().setFontSize(28);` with `first.setStatusVisible(false); first.currentPane().view().setFontSize(28);`.
     - Replace `assertThat(first.status().isVisible()).isFalse(); assertThat(first.theme().chrome()).isEqualTo(Theme.LIGHT);` with `assertThat(first.status().isVisible()).isFalse(); assertThat(first.theme().chrome()).isEqualTo(Theme.DARK);`.
     - Replace the line `            assertThat(first.theme().chrome()).isEqualTo(Theme.LIGHT);` that directly precedes `        }); launchAll();` with `            assertThat(next.theme().chrome()).isEqualTo(Theme.DARK);`.
     - In the last `edt` block, after `assertThat(first.currentPane().view().fontSize()).isEqualTo(20);`, add `assertThat(first.theme().chrome()).as("the saved theme name applies").isEqualTo(Theme.LIGHT);`.
   - In `typographyAndBehaviorChangesPreserveManualSizeAndThemeUntilSavedSizeChanges`, replace `assertThat(pane.view().palette()).isEqualTo(Theme.LIGHT.palette());` with `assertThat(pane.view().palette()).as("a reload applies the configured theme").isEqualTo(Theme.DARK.palette());`.
   - In the theme-install-failure test (the one using `attempts` and `broken`), replace

```java
            assertThat(themes.choice()).isEqualTo(Theme.DARK);
            assertThat(themes.current().chrome()).isEqualTo(Theme.DARK);
            assertThat(attempts).containsExactly(Theme.DARK, Theme.LIGHT,
                Theme.LIGHT, Theme.DARK);
```

     with

```java
            // A reload re-applies the configured theme over a window's own choice.
            assertThat(themes.choice()).isEqualTo(Theme.LIGHT);
            assertThat(themes.current().chrome()).isEqualTo(Theme.LIGHT);
            assertThat(attempts).containsExactly(Theme.DARK, Theme.LIGHT,
                Theme.LIGHT, Theme.DARK, Theme.LIGHT);
```

   - Add `import dev.jasper.app.appearance.ThemeTestSupport;` if the file lacks it.

7. **`jasper-app/src/test/java/dev/jasper/app/workspace/ExpandedConfigTest.java`**: replace

```java
    @Test void invalidVariantDefaultsWhileWrongTypeRejectsAndLegacyColorsWarn() {
        var invalid = parse("[ui.theme]\nvariant='system'");
        assertThat(invalid.rejected()).isFalse();
        assertThat(invalid.snapshot().theme()).isEqualTo("Jasper Dark");
        assertPosition(invalid, "ui.theme.name", 2, 1, ConfigDiagnostic.Severity.ERROR);

```

   with

```java
    @Test void aThemeNameOfTheWrongTypeRejectsAndLegacyColorsWarn() {
```

   Also rename `readsThemeVariantInEveryKeyForm` to `readsTheThemeNameInEveryKeyForm`.

8. **`jasper-app/src/test/java/dev/jasper/app/appearance/TerminalColorsThemeTest.java`**: in `changingTheUiVariantKeepsAFixedTerminalPalette`, replace

```java
            assertThat(themes.current()).as("MATCH follows the effective chrome, including the View override")
                .isEqualTo(new ResolvedTheme(Theme.DARK, Palette.jasperDark()));
```

   with

```java
            assertThat(themes.current()).as("MATCH follows the configured theme")
                .isEqualTo(new ResolvedTheme(Theme.LIGHT, Palette.jasperLight()));
```

   Then rename the test to `changingTheThemeKeepsAFixedTerminalPalette`.

9. **`jasper-app/src/test/java/dev/jasper/app/config/ConfigLoaderTest.java`**: rename `themeVariantDefaultsToDarkAndAcceptsLight` to `themeNameDefaultsToJasperDarkAndAcceptsBuiltInNames`, and add:

```java
    @Test void themeNameIsAStrippedStringOfAnyValue() {
        assertThat(parse("").snapshot().theme()).isEqualTo(ConfigSnapshot.DEFAULT_THEME).isEqualTo("Jasper Dark");
        assertThat(parse("[ui.theme]\nname = '  Dracula '\n").snapshot().theme()).isEqualTo("Dracula");
        assertThat(parse("[ui.theme]\nname = ''\n").diagnostics()).as("resolution reports an empty name").isEmpty();
        var wrongType = parse("ui.theme.name = 7\n");
        assertThat(wrongType.rejected()).isTrue();
        assertThat(wrongType.diagnostics()).singleElement().satisfies(problem -> assertThat(problem.key()).isEqualTo("ui.theme.name"));
    }

    @Test void theRemovedVariantKeyIsAnOrdinaryUnknownSetting() {
        var result = parse("[ui.theme]\nvariant = 'light'\n");
        assertThat(result.rejected()).isFalse();
        assertThat(result.snapshot().theme()).isEqualTo("Jasper Dark");
        assertThat(result.diagnostics()).singleElement().satisfies(problem -> {
            assertThat(problem.key()).isEqualTo("ui.theme.variant");
            assertThat(problem.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING);
        });
    }
```

- [ ] **Step 3: Compile the tests to see them fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL. `ConfigSnapshot.theme()`, `ThemeController(Theme)`, `choice()` returning `Theme`, `configure(Theme…)` and `WindowContent.selectTheme` over `configure` don't exist yet. The errors name only production APIs, no test typos. If a test error remains that Step 2 doesn't cover, apply the same mappings as the script and note it in the report.

- [ ] **Step 4: Change the configuration**

Delete `jasper-app/src/main/java/dev/jasper/app/config/Appearance.java`.

Run this over `ConfigSnapshot.java`:

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path("jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java")
t = p.read_text(encoding="utf-8")
for old, new in [
    ("Appearance variant", "String theme"),
    ("font, variant, keybindings", "font, theme, keybindings"),
    ('Objects.requireNonNull(variant, "variant");', 'Objects.requireNonNull(theme, "theme");'),
    ("FontConfig.defaults(), Appearance.DARK, Map.of()", "FontConfig.defaults(), DEFAULT_THEME, Map.of()"),
    ("private Appearance variant;", "private String theme;"),
    ("variant = source.variant();", "theme = source.theme();"),
    ("public Builder variant(Appearance value) { variant = value; return this; }", "public Builder theme(String value) { theme = value; return this; }"),
]:
    assert old in t, old
    t = t.replace(old, new)
t = t.replace("    public ConfigSnapshot {\n",
    "    /** The theme a configuration without {@code ui.theme.name} uses. */\n"
    "    public static final String DEFAULT_THEME = \"Jasper Dark\";\n\n    public ConfigSnapshot {\n", 1)
assert "variant" not in t and "Appearance" not in t, [line for line in t.splitlines() if "variant" in line or "Appearance" in line]
p.write_text(t, encoding="utf-8")
PY
```

In `ConfigLoader.java`:
- Replace `Map.entry(List.of("ui", "theme"), Set.of("variant", "terminal")),` with `Map.entry(List.of("ui", "theme"), Set.of("name", "terminal")),`.
- Replace `private Appearance variant = Appearance.DARK;` with `private String theme = ConfigSnapshot.DEFAULT_THEME;`.
- Replace `new FontConfig(fontFamily, fontSize, fallback, ligatures, lineHeight), variant, keybindings, columns, lines,` with `new FontConfig(fontFamily, fontSize, fallback, ligatures, lineHeight), theme, keybindings, columns, lines,`.
- Replace

```java
            case "ui.theme.variant" -> variant = choice(path, value, Map.of(
                "light", Appearance.LIGHT, "dark", Appearance.DARK), variant);
```

  with

```java
            // Any name: whether a theme has it is known only to the theme catalog.
            case "ui.theme.name" -> theme = string(path, value, name -> true, "", theme).strip();
```

In `TerminalColors.java`, replace the class comment `/** Saved terminal palette choice, independent of the UI chrome variant; MATCH follows the chrome. */` with `/** Saved terminal palette choice, independent of the UI theme; MATCH uses the theme's own palette. */`.

In `ConfigTemplate.java`, replace

```
            # Theme variant for the UI chrome: "dark" or "light"; switches live across windows.
            # variant = "dark"
            # Terminal colors: "match" follows the variant; "light" or "dark" keeps the terminal
            # independent of the UI. Switches live.
```

with

```
            # The UI theme by name: "Jasper Dark" (the default), "IntelliJ Light", or a theme plugin
            # installed in the themes folder beside this file. Switches live across windows.
            # name = "Jasper Dark"
            # Terminal colors: "match" uses the theme's console colours (Jasper's palette when it has
            # none); "light" or "dark" keeps Jasper's palette. Switches live.
```

In `config.example.toml`, replace

```toml
# Live across open windows. Options: "dark" or "light" for the FlatLaf chrome.
variant = "dark"
# Terminal colors, live: "match" follows variant; "light" or "dark" is independent of the UI.
```

with

```toml
# Live across open windows: "Jasper Dark", "IntelliJ Light", or an installed theme plugin's name.
name = "Jasper Dark"
# Terminal colors, live: "match" uses the theme's console colours; "light" or "dark" keeps Jasper's palette.
```

- [ ] **Step 5: Change the theme state, the controller and the windows**

Replace `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeState.java` with:

```java
package dev.jasper.app.appearance;

import dev.jasper.app.config.TerminalColors;
import dev.jasper.terminal.config.Palette;
import java.util.Objects;

/**
 * The configured theme and terminal colours, the latter with an optional temporary View choice. The
 * chrome is the configured theme; the terminal palette follows the terminal choice, where MATCH uses
 * the theme's own palette.
 */
record ThemeState(Theme saved, TerminalColors terminalSaved, TerminalColors terminalOverride) {
    ThemeState { Objects.requireNonNull(saved); Objects.requireNonNull(terminalSaved); }

    static ThemeState defaults() { return new ThemeState(Theme.DARK, TerminalColors.MATCH, null); }

    ThemeState configure(Theme next) {
        return new ThemeState(Objects.requireNonNull(next), terminalSaved, terminalOverride);
    }

    /** A changed saved terminal choice clears its temporary choice; rewriting the same value keeps it. */
    ThemeState configureTerminal(TerminalColors next) {
        return new ThemeState(saved, Objects.requireNonNull(next), terminalSaved == next ? terminalOverride : null);
    }

    ThemeState chooseTerminal(TerminalColors next) {
        return new ThemeState(saved, terminalSaved, Objects.requireNonNull(next));
    }

    Theme choice() { return saved; }

    TerminalColors terminalChoice() { return terminalOverride == null ? terminalSaved : terminalOverride; }

    ResolvedTheme resolve() {
        Palette palette = switch (terminalChoice()) {
            case MATCH -> saved.palette();
            case LIGHT -> Palette.jasperLight();
            case DARK -> Palette.jasperDark();
        };
        return new ResolvedTheme(saved, palette);
    }
}
```

In `Theme.java`, delete the `of(Appearance)` and `appearance()` methods and the `import dev.jasper.app.config.Appearance;` line.

In `ThemeController.java`, delete the `import dev.jasper.app.config.Appearance;` line, then:
- Replace

```java
    public ThemeController() { this(Appearance.DARK); }
    public ThemeController(Appearance saved) { this(saved, ThemeManager::install); }
    public ThemeController(Predicate<Theme> installer) { this(Appearance.DARK, installer); }
    ThemeController(Appearance saved, Predicate<Theme> installer) {
```

  with

```java
    public ThemeController() { this(Theme.DARK); }
    public ThemeController(Theme saved) { this(saved, ThemeManager::install); }
    public ThemeController(Predicate<Theme> installer) { this(Theme.DARK, installer); }
    ThemeController(Theme saved, Predicate<Theme> installer) {
```

- Replace `    public Appearance choice() { requireEdt(); return state.choice(); }` with

```java
    /** The configured theme. */
    public Theme choice() { requireEdt(); return state.choice(); }
```

- Delete

```java
    public void selectAppearance(Appearance choice) {
        requireEdt(); apply(state.choose(Objects.requireNonNull(choice)));
    }
```

- Replace

```java
    public void configure(Appearance saved) { configure(saved, uiFont); }
    public void configure(Appearance saved, UiFontConfig font) { configure(saved, state.terminalSaved(), font); }
    public void configure(Appearance saved, TerminalColors terminal, UiFontConfig font) {
```

  with

```java
    public void configure(Theme saved) { configure(saved, uiFont); }
    public void configure(Theme saved, UiFontConfig font) { configure(saved, state.terminalSaved(), font); }
    public void configure(Theme saved, TerminalColors terminal, UiFontConfig font) {
```

- Delete `    public void select(Theme theme) { selectAppearance(Objects.requireNonNull(theme).appearance()); }`.

In `WindowContent.java`, delete the `import dev.jasper.app.config.Appearance;` line, then:
- Replace

```java
            content.onThemeChanged = theme -> bar.setLight(theme.chrome().appearance() == Appearance.LIGHT);
            bar.setLight(content.theme().chrome().appearance() == Appearance.LIGHT);
```

  with

```java
            content.onThemeChanged = theme -> bar.setLight(!theme.chrome().dark());
            bar.setLight(!content.theme().chrome().dark());
```

- Replace everything from `    Appearance appearance() { return themes.choice(); }` through the end of `selectAppearance` with

```java
    /** Applies {@code theme} to every window, as the saved configuration applies its theme. */
    void selectTheme(Theme theme) {
        if (closed) return;
        try { themes.configure(theme); }
        catch (ThemeController.InstallationFailure failure) { onError.accept(failure.getMessage()); }
        finally { chrome.refreshTheme(); chrome.status().refreshTheme(); }
    }
```

  That span covers `appearance()`, the old `selectTheme` and `selectAppearance`.

In `WindowChrome.java`, delete the `import dev.jasper.app.config.Appearance;` line and the field `private final java.util.EnumMap<Appearance, JRadioButtonMenuItem> themeItems = new java.util.EnumMap<>(Appearance.class);`, then:
- Replace the block from `        JMenu appearance = new JMenu("Appearance");` through `        view.add(appearance);` with

```java
        JMenu terminalColors = new JMenu("Terminal Colors");
        ButtonGroup terminalGroup = new ButtonGroup();
        for (TerminalColors colors : TerminalColors.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(owner.windowCommands().view(
                "view.terminal_colors." + colors.name().toLowerCase(java.util.Locale.ROOT)));
            terminalItems.put(colors, item); terminalGroup.add(item); terminalColors.add(item);
        }
        terminalColors.addMenuListener(new MenuListener() {
            @Override public void menuSelected(MenuEvent event) { refreshTheme(); }
            @Override public void menuDeselected(MenuEvent event) {}
            @Override public void menuCanceled(MenuEvent event) {}
        });
        view.add(terminalColors);
```

- Delete the line `        themeItems.forEach((theme, item) -> item.setSelected(owner.appearance() == theme));`.

In `WindowCommands.java`, delete the `import dev.jasper.app.config.Appearance;` line, then delete

```java
        for (var appearance : Appearance.values()) {
            String label = switch (appearance) { case LIGHT -> "Light"; case DARK -> "Dark"; };
            add(registry, "view.appearance." + appearance.name().toLowerCase(java.util.Locale.ROOT), label,
                "Appearance: " + label, () -> owner.selectAppearance(appearance));
        }
```

and

```java
for (var appearance : Appearance.values()) {
    var action = view("view.appearance." + appearance.name().toLowerCase(java.util.Locale.ROOT));
    action.putValue(Action.SELECTED_KEY, owner.appearance() == appearance);
    action.setEnabled(true);
}
```

In `NativeShells.java`, replace both occurrences of `bar.setLight(resolved.chrome().appearance() == dev.jasper.app.config.Appearance.LIGHT);` with `bar.setLight(!resolved.chrome().dark());`.

In `ConfigurationController.java`, add the imports `dev.jasper.app.appearance.Theme` and `dev.jasper.app.appearance.ThemeCatalog`, then:
- Replace `        themes.configure(state.snapshot().variant(), state.snapshot().terminalColors(), state.snapshot().uiFont());` with `        configureThemes(state);`.
- Replace `        try { themes.configure(next.snapshot().variant(), next.snapshot().terminalColors(), next.snapshot().uiFont()); }` with `        try { configureThemes(next); }`.
- Add this method before `close()`. Task 7 replaces it with the catalog-backed version.

```java
    /** Applies the saved theme, terminal colours and UI font; an unknown theme name keeps the theme showing. */
    private void configureThemes(ConfigService.State next) {
        Theme theme = ThemeCatalog.builtIn().latest().find(next.snapshot().theme()).orElse(themes.choice());
        themes.configure(theme, next.snapshot().terminalColors(), next.snapshot().uiFont());
    }
```

In `JasperApplication.java`, add the imports `dev.jasper.app.appearance.Theme` and `dev.jasper.app.appearance.ThemeCatalog`, and replace `themes = new ThemeController(startup.variant());` with `themes = new ThemeController(ThemeCatalog.builtIn().latest().find(startup.theme()).orElse(Theme.DARK));`.

- [ ] **Step 6: Run the affected tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.*' --tests 'dev.jasper.app.config.*' --tests '*ConfigurationControllerTest' --tests '*ExpandedConfigTest' --tests '*ThemeControllerTest' --tests '*WindowChromeTest' --tests '*TerminalColorsTest' --tests '*WindowCommandsTest' --tests '*MacTitleBarTest' --tests '*WindowCommandPaletteTest'`
Expected: PASS.

Run: `git grep -nE '\bAppearance\b' -- jasper-app/src/main/java`
Expected, and nothing else:
- `HostedContext` and `HostedUi`, which use the SDK's `dev.jasper.sdk.ui.Appearance`;
- `ShortcutCatalog`'s `"Appearance"` category label for the font actions.

Run: `git grep -n 'variant' -- jasper-app/src/main/java`
Expected: only SDK-facing `Variant` / `variant()` uses in `plugins/`, and `PaletteController`'s unrelated "modifier variants" comment.

- [ ] **Step 7: Run check and commit**

Run: `./gradlew check > /tmp/theme-import-task6.log 2>&1; tail -5 /tmp/theme-import-task6.log`
Expected: `BUILD SUCCESSFUL`.

Run the AGENTS.md source-hygiene check. Expected: no output.

```bash
git add -A jasper-app/src config.example.toml
git commit -m "feat(config): name the UI theme with ui.theme.name

ui.theme.variant, the Appearance enum, the View menu's Light and Dark items and the
view.appearance.* commands are gone; the View submenu is now Terminal Colors.
A theme is chosen only in config.toml, so a reload re-applies it.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: Scan the themes folder with the configuration

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigService.java`, `application/ConfigurationController.java`, `application/JasperApplication.java`
- Modify (test support): `jasper-app/src/test/java/dev/jasper/app/application/ConfigurationTestSupport.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/config/ConfigServiceTest.java`, `workspace/InstalledThemesTest.java` (new), `application/JasperApplicationThemesTest.java` (new)

**Interfaces:**
- Consumes:
  - `ThemeCatalog.beside`, `builtIn`, `scan`, `latest`, `Snapshot.find`, `Snapshot.names`, `Snapshot.problems` and `Problem` (Task 5);
  - `ConfigSnapshot.theme()` and `ThemeController.configure(Theme, TerminalColors, UiFontConfig)` (Task 6);
  - `ThemeFixtures.plugin` (Task 3).
- Produces:
  - `ConfigService.watchWith(Runnable) -> CompletableFuture<Void>`;
  - the constructors `ConfigurationController(ThemeController, ConfigService, ThemeCatalog)` and `ConfigurationController(ThemeController, ConfigService, ThemeCatalog, Consumer<Path>)`;
  - a new constructor, `ConfigurationTestSupport(ThemeController, ConfigService, ThemeCatalog)`.

- [ ] **Step 1: Write the failing tests**

Add to `ConfigServiceTest.java`:

```java
    @Test void aCompanionRunsOnTheWorkerNowAndOnEveryForcedRead() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[window]\ntab_height=40\n");
        try (var service = new ConfigService(file, false)) {
            var threads = new java.util.concurrent.CopyOnWriteArrayList<String>();
            service.watchWith(() -> threads.add(Thread.currentThread().getName())).get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(threads).containsExactly("jasper-config");
            service.reload().get(5, java.util.concurrent.TimeUnit.SECONDS);
            service.reload().get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(threads).containsExactly("jasper-config", "jasper-config", "jasper-config");
        }
    }

    @Test void aFailingCompanionDoesNotStopConfigurationReads() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[window]\ntab_height=40\n");
        try (var service = new ConfigService(file, false)) {
            var first = service.watchWith(() -> { throw new IllegalStateException("scan failed"); });
            assertThat(first).failsWithin(5, java.util.concurrent.TimeUnit.SECONDS);
            Files.writeString(file, "[window]\ntab_height=44\n");
            assertThat(service.reload().get(5, java.util.concurrent.TimeUnit.SECONDS).snapshot().tabHeight()).isEqualTo(44);
        }
    }
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/InstalledThemesTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeFixtures;
import dev.jasper.app.application.ConfigurationTestSupport;
import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.ConfigService;
import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class InstalledThemesTest {
    @TempDir Path home;
    private ConfigService service;
    private ConfigurationTestSupport controller;

    @AfterEach void cleanup() throws Exception {
        edt(() -> { if (controller != null) controller.close(); new ThemeController(); });
        if (service != null) service.close();
    }

    private Path config() { return home.resolve("config.toml"); }
    private Path themes() { return home.resolve("themes"); }

    /** As start-up: the first scan on the configuration worker, then the controller on the EDT. */
    private ThemeController start(String text) throws Exception {
        Files.writeString(config(), text);
        service = new ConfigService(config(), false);
        var catalog = ThemeCatalog.beside(config());
        service.watchWith(catalog::scan).get();
        ThemeController[] themes = new ThemeController[1];
        edt(() -> {
            themes[0] = new ThemeController(catalog.latest().find(service.initialState().snapshot().theme()).orElse(Theme.DARK));
            controller = new ConfigurationTestSupport(themes[0], service, catalog);
        });
        return themes[0];
    }

    private void reload(String text) throws Exception {
        Files.writeString(config(), text);
        reload();
    }

    private void reload() throws Exception {
        service.reload().get();
        edt(() -> {});
    }

    @Test void aThemeInstalledInTheFolderAppliesByNameWithItsConsoleColours() throws Exception {
        ThemeFixtures.plugin(themes().resolve("fixture.jar"), "Fixture Dark", true, "#123456", "101820");
        var themes = start("[ui.theme]\nname = 'fixture dark'\n");
        edt(() -> {
            assertThat(themes.current().chrome().name()).isEqualTo("Fixture Dark");
            assertThat(themes.current().palette().background()).isEqualTo(new Color(0x101820));
            assertThat(UIManager.getColor("Panel.background")).isEqualTo(new Color(0x123456));
            assertThat(controller.shown().diagnostics()).isEmpty();
        });
        reload("[ui.theme]\nname = 'fixture dark'\nterminal = 'light'\n");
        edt(() -> {
            assertThat(themes.current().chrome().name()).isEqualTo("Fixture Dark");
            assertThat(themes.current().palette()).isEqualTo(Palette.jasperLight());
        });
    }

    @Test void anUnknownNameKeepsTheCurrentThemeAndListsWhatIsAvailable() throws Exception {
        ThemeFixtures.plugin(themes().resolve("fixture.jar"), "Fixture Dark", true, "#123456", "101820");
        var themes = start("[ui.theme]\nname = 'Fixture Dark'\n");
        reload("[ui.theme]\nname = 'Nope'\n");
        edt(() -> {
            assertThat(themes.current().chrome().name()).isEqualTo("Fixture Dark");
            assertThat(controller.shown().diagnostics()).singleElement().satisfies(problem -> {
                assertThat(problem.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING);
                assertThat(problem.key()).isEqualTo("ui.theme.name");
                assertThat(problem.message()).contains("\"Nope\"").contains("keeping Fixture Dark")
                    .contains("Available: Fixture Dark, IntelliJ Light, Jasper Dark.");
            });
        });
    }

    @Test void anUnknownNameAtStartUpUsesJasperDark() throws Exception {
        var themes = start("[ui.theme]\nname = ''\n");
        edt(() -> {
            assertThat(themes.current().chrome()).isEqualTo(Theme.DARK);
            assertThat(controller.shown().diagnostics()).singleElement().satisfies(problem ->
                assertThat(problem.message()).contains("Unknown theme \"\"").contains("keeping Jasper Dark"));
        });
    }

    @Test void reloadRescansTheFolderSoThemesCanBeAddedAndRemovedLive() throws Exception {
        var themes = start("[ui.theme]\nname = 'Fixture Dark'\n");
        edt(() -> assertThat(themes.current().chrome()).isEqualTo(Theme.DARK));
        ThemeFixtures.plugin(themes().resolve("fixture.jar"), "Fixture Dark", true, "#123456", "101820");
        reload();
        edt(() -> {
            assertThat(themes.current().chrome().name()).isEqualTo("Fixture Dark");
            assertThat(controller.shown().diagnostics()).isEmpty();
        });
        Files.delete(themes().resolve("fixture.jar"));
        Files.writeString(themes().resolve("broken.jar"), "not a zip");
        reload();
        edt(() -> {
            assertThat(themes.current().chrome().name()).as("the theme showing stays").isEqualTo("Fixture Dark");
            assertThat(controller.shown().diagnostics()).extracting(ConfigDiagnostic::message)
                .anySatisfy(message -> assertThat(message).contains("broken.jar").contains("cannot be read"))
                .anySatisfy(message -> assertThat(message).contains("Unknown theme \"Fixture Dark\""));
        });
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeFixtures;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.ConfigService;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

class JasperApplicationThemesTest {
    @TempDir Path home;

    @Test void theApplicationStartsInTheInstalledThemeItsConfigurationNames() throws Exception {
        Files.writeString(home.resolve("config.toml"), "ui.theme.name = 'Fixture Dark'\n");
        ThemeFixtures.plugin(home.resolve("themes/fixture.jar"), "Fixture Dark", true, "#123456", "101820");
        var service = new ConfigService(home.resolve("config.toml"), false);
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(),
                    null, terminated::countDown);
                assertThat(UIManager.getLookAndFeel().getName()).isEqualTo("Fixture Dark");
                assertThat(UIManager.getColor("Panel.background")).isEqualTo(new Color(0x123456));
            });
        } finally {
            if (application[0] != null) { edt(application[0]::quit); assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue(); }
            service.close();
            edt(() -> new ThemeController());
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*ConfigServiceTest' --tests '*InstalledThemesTest' --tests '*JasperApplicationThemesTest'`
Expected: FAIL to compile, because `ConfigService.watchWith` and `ConfigurationTestSupport(…, ThemeCatalog)` don't exist.

- [ ] **Step 3: Implement the companion hook in ConfigService**

In `ConfigService.java`:
- Add the fields

```java
    private static final System.Logger LOG = System.getLogger(ConfigService.class.getName());
    /** Runs on the worker after each read of the file; see {@link #watchWith}. */
    private volatile Runnable companion;
```

- Add after `public boolean macOs() { return macOs; }`:

```java
    /**
     * Runs {@code companion} on the configuration worker now, and again after every later read that
     * changed the state or was forced (Reload config, Settings), before that state is published. The
     * future completes when this first run finishes. The theme catalog rescans its folder this way.
     */
    public CompletableFuture<Void> watchWith(Runnable companion) {
        Objects.requireNonNull(companion, "companion");
        this.companion = companion;
        return submit(() -> { companion.run(); return null; });
    }
```

- In `refresh`, replace `        lastConfig = readState(lastConfig.snapshot(), force);` with

```java
        State previous = lastConfig;
        lastConfig = readState(lastConfig.snapshot(), force);
        if (force || !lastConfig.equals(previous)) runCompanion();
```

- Add after `refresh`:

```java
    private void runCompanion() {
        Runnable current = companion;
        if (current == null) return;
        // A failing companion must not stop configuration reads or the change poll.
        try { current.run(); }
        catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "Configuration companion failed", failure); }
    }
```

- [ ] **Step 4: Resolve names against the catalog and report its problems**

Replace `jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java` with:

```java
package dev.jasper.app.application;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.platform.ConfigEditor;
import dev.jasper.app.workspace.WindowContent;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.SwingUtilities;

/** EDT-owned join between saved configuration and retained window contents. */
final class ConfigurationController implements AutoCloseable {
    private final ThemeController themes;
    private final ConfigService service;
    private final ThemeCatalog catalog;
    private final Set<WindowContent> owners = new LinkedHashSet<>();
    private final Consumer<Path> editor;
    private ConfigService.State state;
    private boolean closed;
    private Consumer<ConfigSnapshot> applicationListener = snapshot -> {};
    private final List<ConfigDiagnostic> reported = new java.util.ArrayList<>();
    /** The theme catalog's problems, and an unknown theme name, from the last state applied. */
    private List<ConfigDiagnostic> themeProblems = List.of();

    /** The saved state plus theme problems and problems plugins reported about their own tables since the last reload. */
    ConfigService.State shown() {
        requireEdt();
        var merged = new java.util.ArrayList<>(state.diagnostics());
        merged.addAll(themeProblems);
        merged.addAll(reported);
        return new ConfigService.State(state.snapshot(), merged, state.file(), state.present());
    }

    /** A plugin's complaint about one of its settings; shown with the file's diagnostics until the next reload. */
    void report(String key, String message) {
        requireEdt();
        if (closed) return;
        reported.add(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, state.file(), 0, 0, key, message));
        for (WindowContent owner : List.copyOf(owners)) owner.setConfigurationState(shown());
    }

    ConfigurationController(ThemeController themes, ConfigService service, ThemeCatalog catalog) {
        this(themes, service, catalog, new ConfigEditor()::open);
    }

    ConfigurationController(ThemeController themes, ConfigService service, ThemeCatalog catalog, Consumer<Path> editor) {
        requireEdt();
        this.themes = themes; this.service = service; this.catalog = catalog; this.editor = editor;
        state = service.initialState();
        configureThemes(state);
        service.start(this::accept);
    }

    /** The platform the saved shortcuts were parsed for. */
    boolean macOs() { return service.macOs(); }

    ConfigSnapshot snapshot() {
        requireEdt();
        return state.snapshot();
    }

    /** One application-level observer for settings no window owns; receives the current snapshot at once. */
    void onSnapshot(Consumer<ConfigSnapshot> listener) {
        requireEdt();
        applicationListener = java.util.Objects.requireNonNull(listener, "listener");
        if (!closed) listener.accept(state.snapshot());
    }

    void register(WindowContent owner) {
        requireEdt();
        if (closed || !owners.add(owner)) return;
        owner.connectConfiguration(() -> reportFailure(owner, service.openSettings(editor)),
            () -> reportFailure(owner, service.reload()), () -> unregister(owner));
        owner.applyConfiguration(state.snapshot(), service.macOs());
        owner.setConfigurationState(shown());
    }

    private void reportFailure(WindowContent owner, CompletableFuture<?> result) {
        result.whenComplete((ignored, failure) -> {
            if (failure != null) SwingUtilities.invokeLater(() -> {
                if (!closed && owners.contains(owner)) owner.onError.accept(failure.getMessage());
            });
        });
    }

    void unregister(WindowContent owner) {
        requireEdt();
        if (owners.remove(owner)) owner.disconnectConfiguration();
    }

    void accept(ConfigService.State next) {
        requireEdt();
        if (closed) return;
        try { configureThemes(next); }
        catch (ThemeController.InstallationFailure failure) {
            for (WindowContent owner : List.copyOf(owners)) owner.onError.accept(failure.getMessage());
        }
        state = next;
        reported.clear();
        applicationListener.accept(next.snapshot());
        for (WindowContent owner : List.copyOf(owners)) {
            owner.applyConfiguration(next.snapshot(), service.macOs());
            owner.setConfigurationState(shown());
        }
    }

    /**
     * Applies the saved theme, terminal colours and UI font from the catalog's latest scan. An unknown
     * theme name keeps the theme showing (Jasper Dark at start-up) and is reported with the names available.
     */
    private void configureThemes(ConfigService.State next) {
        ThemeCatalog.Snapshot available = catalog.latest();
        var problems = new java.util.ArrayList<ConfigDiagnostic>();
        for (ThemeCatalog.Problem problem : available.problems())
            problems.add(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, problem.file(), 0, 0, "", problem.message()));
        String name = next.snapshot().theme();
        Theme theme = available.find(name).orElse(null);
        if (theme == null) {
            theme = themes.choice();
            problems.add(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, next.file(), 0, 0, "ui.theme.name",
                "Unknown theme \"" + name + "\"; keeping " + theme.name() + ". Available: " + String.join(", ", available.names()) + "."));
        }
        themeProblems = List.copyOf(problems);
        themes.configure(theme, next.snapshot().terminalColors(), next.snapshot().uiFont());
    }

    @Override public void close() {
        requireEdt();
        if (closed) return;
        closed = true;
        applicationListener = snapshot -> {};
        for (WindowContent owner : List.copyOf(owners)) unregister(owner);
        service.close();
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Configuration UI requires the EDT");
    }
}
```

In `jasper-app/src/test/java/dev/jasper/app/application/ConfigurationTestSupport.java`, replace the two constructors with:

```java
    public ConfigurationTestSupport(ThemeController themes, ConfigService service) { this(themes, service, ThemeCatalog.builtIn()); }
    public ConfigurationTestSupport(ThemeController themes, ConfigService service, ThemeCatalog catalog) {
        controller = new ConfigurationController(themes, service, catalog);
    }
    public ConfigurationTestSupport(ThemeController themes, ConfigService service, Consumer<Path> editor) {
        controller = new ConfigurationController(themes, service, ThemeCatalog.builtIn(), editor);
    }
```

Add `import dev.jasper.app.appearance.ThemeCatalog;`.

- [ ] **Step 5: Start in the saved theme**

In `JasperApplication.java`:
- Add the field `    private final ThemeCatalog themeCatalog;` beside `themes`.
- Add the imports `java.util.concurrent.ExecutionException` and `java.util.concurrent.TimeoutException`.
- Replace `        themes = new ThemeController(ThemeCatalog.builtIn().latest().find(startup.theme()).orElse(Theme.DARK));` with

```java
        themeCatalog = service == null ? ThemeCatalog.builtIn() : ThemeCatalog.beside(service.initialState().file());
        if (service != null) scanThemes(service);
        themes = new ThemeController(themeCatalog.latest().find(startup.theme()).orElse(Theme.DARK));
```

- Replace `        configuration = service == null ? null : new ConfigurationController(themes, service);` with `        configuration = service == null ? null : new ConfigurationController(themes, service, themeCatalog);`.
- Add this method to the class:

```java
    /**
     * The first scan of the themes folder runs on the configuration worker; start-up waits for it, at most
     * 10 s, so the first window shows the saved theme. Later scans run with each configuration read.
     */
    private void scanThemes(ConfigService service) {
        try { service.watchWith(themeCatalog::scan).get(10, java.util.concurrent.TimeUnit.SECONDS); }
        catch (ExecutionException | TimeoutException failure) {
            LOG.log(System.Logger.Level.WARNING, "The themes folder could not be read at start-up", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests '*ConfigServiceTest' --tests '*InstalledThemesTest' --tests '*JasperApplicationThemesTest' --tests '*ConfigurationControllerTest' --tests '*JasperApplicationPluginsTest'`
Expected: PASS. The failing-companion test logs one `Configuration companion failed` warning.

- [ ] **Step 7: Run check and commit**

Run: `./gradlew check > /tmp/theme-import-task7.log 2>&1; tail -5 /tmp/theme-import-task7.log`
Expected: `BUILD SUCCESSFUL`.

Run: `git status --short --untracked-files=all | grep -E '(^| |/)themes/'`
Expected: no output. No `themes/` folder appears in the repository, because every application test's configuration lives under a temporary directory.

Run the AGENTS.md source-hygiene check. Expected: no output.

```bash
git add jasper-app/src/main/java/dev/jasper/app/config/ConfigService.java \
  jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java \
  jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java \
  jasper-app/src/test/java/dev/jasper/app/application/ConfigurationTestSupport.java \
  jasper-app/src/test/java/dev/jasper/app/config/ConfigServiceTest.java \
  jasper-app/src/test/java/dev/jasper/app/workspace/InstalledThemesTest.java \
  jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java
git commit -m "feat(appearance): apply installed themes by name, rescanning with each config read

ConfigService.watchWith runs the theme catalog's scan on the configuration worker
at start-up and after each read; ConfigurationController resolves ui.theme.name
against it and shows the catalog's problems and an unknown name as warnings.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: Documentation and status

**Files:**
- Create: `docs/themes.md`
- Modify: `docs/configuration.md`, `docs/README.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/STATUS.md`

- [ ] **Step 1: Write the themes guide**

Create `docs/themes.md`:

````markdown
# Themes

Jasper's UI theme is an IntelliJ theme. Two are built in: **Jasper Dark** (the default) and
**IntelliJ Light**, the classic IntelliJ Light. Any theme plugin from the JetBrains Marketplace
(plugins.jetbrains.com) can be installed.

## Choosing a theme

Name the theme in `config.toml`:

```toml
[ui.theme]
name = "Dracula"
```

The name is the theme's own name, matched ignoring case and surrounding spaces. Changing it
applies the theme to every window at once, keeping shells, scrollback and splits. There is no
theme picker: the configuration file is the only place a theme is chosen.

## Installing a Marketplace theme

1. Download the theme plugin from the Marketplace, using the version for a recent IntelliJ. It is a `.jar` or a
   `.zip`.
2. Put the file, unchanged, in the `themes` folder beside `config.toml`
   (`~/.config/jasper/themes/` on macOS). Jasper creates the folder when it starts.
3. Set `ui.theme.name` to the theme's name and save. If the name is already set, choose
   **Reload config**.

Jasper reads the folder when it starts, when `config.toml` changes and on **Reload config**. It
finds a plugin's themes through its `META-INF/plugin.xml`, and reads each theme's `.theme.json`
and editor colour scheme straight from the file. Nothing is unpacked, and no code in the plugin
runs. A theme you install takes precedence over a built-in theme of the same name.

## What is supported

- Classic-UI theme plugins: the `.theme.json` files a plugin declares with `themeProvider`,
  including several themes in one plugin.
- A theme's `parentTheme` when it is `Darcula` or `IntelliJ`. Any other parent, such as a New UI
  theme's `ExperimentalDark`, is replaced by Darcula or IntelliJ by the theme's darkness, with a
  warning.
- The theme's editor colour scheme, for the terminal (below).

Not supported:
- themes a plugin draws in code, such as Material Theme UI;
- loose `.theme.json` files;
- background images;
- icon recolouring;
- New UI-only layout keys.

## Terminal colours

With `ui.theme.terminal = "match"` (the default), a theme that has an editor colour scheme
colours the terminal with the scheme's console colours: background, text, cursor, selection and
the 16 ANSI colours.
- **Missing colours:** colours the scheme leaves out come from its parent scheme (IntelliJ's own Default or Darcula), as in IntelliJ's console.
- **No scheme:** a theme without one, and the built-in themes, use Jasper Dark or Jasper Light by the theme's darkness.
- **Forcing Jasper's palettes:** `"light"` and `"dark"` always use Jasper's palettes.

Scheme colours are used exactly as the theme author chose them.

## When something is wrong

Problems appear under the status bar's **Config warnings**, naming the file:
- An unknown `ui.theme.name` lists the themes available. At start-up Jasper uses Jasper Dark; on a reload it keeps the theme showing.
- A file that isn't a readable plugin, or a plugin with no theme files, is reported.
- A theme that fails to load, or names a parent theme Jasper doesn't have, is reported.
- Two installed themes with the same name are reported, and the file that sorts first wins.
- An unreadable editor scheme is reported, and the terminal then uses Jasper's palette.

No problem stops Jasper from starting. Each plugin is capped at 64 MiB, each file read from it at
4 MiB, and each jar inside a `.zip` at 32 MiB, with at most 16 such jars read.
````

- [ ] **Step 2: Update the configuration reference**

In `docs/configuration.md`:

1. In the sample file, replace

```
[ui.theme]
# "dark" or "light" for the UI chrome.
variant = "dark"
# "match" (follow variant), "light" or "dark" terminal colors.
```

   with

```
[ui.theme]
# A built-in or installed theme's name; see Themes.
name = "Jasper Dark"
# "match" (the theme's console colours), "light" or "dark" terminal colors.
```

2. In the settings table, replace the row ``| `ui.theme.variant` | `"dark"` | `"dark"`, `"light"` | Live |`` with ``| `ui.theme.name` | `"Jasper Dark"` | A built-in or installed theme's name ([Themes](themes.md)) | Live |``.

3. Replace the section from `## Theme variant` up to, but not including, `## Terminal colors` with:

```markdown
## Theme

`ui.theme.name` names the UI theme:
- `"Jasper Dark"` (the default);
- `"IntelliJ Light"` (classic IntelliJ Light, the theme IntelliJ IDEA ships for its classic UI);
- or a theme plugin installed in the `themes` folder beside this file.

The name is matched ignoring case and surrounding spaces. [Themes](themes.md) covers installing
Marketplace themes and how a theme colours the terminal. An unknown name is a warning that lists
the available themes: at start-up Jasper uses Jasper Dark, and on a reload it keeps the theme
showing. A non-string value rejects the configuration.

Terminal padding and the area behind panes match the terminal palette; toolbar, menus, find
controls and the native title follow the theme. Theme changes retain shells, scrollback, split
ratios and font choices, and a failed installation keeps the previous theme and can be retried
with Reload config.

The former `[colors]` table (`appearance`, `theme`) and custom palette files are no longer
supported: a `[colors]` table is reported as an unknown setting and ignored. See the
[dark](design/mock-ui-dark.png) and [light](design/mock-ui-light.png) renders.
```

4. In `## Terminal colors`:
   - Replace ``"match"` (the default) follows `ui.theme.variant`;`` with ``"match"` (the default) uses the theme's console colours, or Jasper Dark or Jasper Light by the theme's darkness when it has none ([Themes](themes.md));``.
   - Replace `View → Appearance offers Terminal: Match UI, Light and Dark` with `View → Terminal Colors offers Terminal: Match UI, Light and Dark`.

5. Run `git grep -nE 'ui\.theme\.variant|theme-variant|View → Appearance|Theme variant' -- 'docs/*.md' README.md 'jasper-app/*.md' ':!docs/superpowers/**' ':!docs/STATUS.md'`. Rewrite every remaining hit for the new key and menu, and point any `#theme-variant` anchor at `#theme`. Expected afterwards: no output.

- [ ] **Step 3: Update the index, architecture and maintenance docs**

In `docs/README.md`, add this line directly after the Configuration line:

```markdown
- [Themes](themes.md): choosing a theme, installing IntelliJ Marketplace themes and how a theme colours the terminal.
```

In `docs/app-architecture.md`, at the end of the `## Themes` section (after the sentence ending `the rail stays chrome.`), add:

```markdown
Installed themes come from `ThemeCatalog`, which lists the plugins in the `themes` folder beside
`config.toml`, then the built-ins. The pieces:
- **Reading:** `ThemeArchive` reads a Marketplace plugin (a `.jar`, or a `.zip` of `lib/*.jar`) in place, with
  caps. Its `META-INF/plugin.xml` names each theme through `themeProvider`, and a theme names its
  editor scheme.
- **Terminal palette:** `EditorScheme` turns the scheme's console colours into the theme's terminal palette,
  with IntelliJ's own Default and Darcula schemes
  (`themes/intellij/DefaultColorSchemesManager.xml`) as parents.
- **Untrusted XML:** `SafeXml` parses plugin XML with DOCTYPE and external entities disabled.
- **Installing:** a `Theme` records its source, a bundled resource or an archive, so `ThemeManager` installs
  an installed theme the same way as a built-in one.
- **Scanning and resolving:** `ConfigService.watchWith` runs the catalog's scan on the configuration worker at start-up and
  after each read. `ConfigurationController` resolves `ui.theme.name` against the latest scan and
  shows the catalog's problems as configuration warnings.
```

In `docs/app-maintenance.md`, at the end of the `## Themes` recipe, add:

```markdown
The vendored `DefaultColorSchemesManager.xml` (IntelliJ's Default and Darcula editor schemes) comes
from the same commit through the same tool, and its hash is pinned in `IntellijThemesTest` too.
To support another theme-plugin layout, extend `ThemeArchive` and add a case to
`ThemeArchiveTest`; `ThemeFixtures` builds plugin jars and Marketplace zips. Run
`ThemeArchiveTest`, `EditorSchemeTest`, `ThemeCatalogTest` and `InstalledThemesTest`, then `check`.
```

- [ ] **Step 4: Add the STATUS entry**

In `docs/STATUS.md`, under `## Current state — 2026-09-23`, directly after the "Search Everywhere palette" bullet and its sub-bullets, add:

```markdown
- **IntelliJ theme import, plan 3a** (branch `claude/theme-import`, plan
  `docs/superpowers/plans/2026-09-25-jasper-theme-import.md`):
  - **Choosing:** `ui.theme.name` names a built-in or installed theme. `ui.theme.variant`, the View → Appearance
    Light and Dark items and `view.appearance.*` are removed; the View submenu is Terminal Colors.
  - **Installing:** JetBrains Marketplace theme plugins (`.jar`, `.zip`) in `<home>/themes/`, read in
    place and rescanned with each configuration read.
  - **Terminal:** under `match`, a theme's editor-scheme console colours, with IntelliJ's vendored
    Default and Darcula schemes as parents.
  - **Problems** are configuration warnings; none blocks start-up.
  - **Planning decisions:** listed in the plan header.
  - **Not done:** plan 3b (the bundled set), visual acceptance (user-run), merge and push.
```

- [ ] **Step 5: Verify and commit**

Run: `./gradlew check > /tmp/theme-import-task8.log 2>&1; tail -5 /tmp/theme-import-task8.log`
Expected: `BUILD SUCCESSFUL`. `AppDocumentationTest` resolves every link, including `themes.md`.

```bash
git add docs
git commit -m "docs(appearance): document choosing and installing IntelliJ themes

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
