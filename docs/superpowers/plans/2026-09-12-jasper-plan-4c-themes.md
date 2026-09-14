# Jasper Plan 4c — Themes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver reloadable custom terminal palettes and automatic system appearance, switching both chrome and built-in palettes while preserving custom palettes and terminal sessions.

**Architecture:** Separate saved color intent, loaded palette data and effective UI appearance. Extend the existing configuration worker for bounded theme-file polling; use one application-owned OS listener and an EDT-owned immutable preference reducer for runtime overrides. Reuse existing retained-pane theme application and FlatLaf rollback.

**Tech Stack:** JBR 25, Java 25, Gradle wrapper, Swing, FlatLaf 3.7, TomlJ 1.1.1, jSystemThemeDetector 3.9.1, JUnit Jupiter 6.1.3 and AssertJ 3.27.7.

**Spec:** [Plan 4c design](../specs/2026-09-12-jasper-plan-4c-themes-design.md), amending the [Phase 1 design](../specs/2026-09-10-jasper-phase-1-terminal-design.md).

## Global Constraints

- Planning only at preparation time; do not interpret this document as authorization to launch the GUI or start a login shell.
- JBR 25; use `./gradlew check` headlessly, never system Gradle.
- `jasper-terminal` never depends on `jasper-app`; no public JediTerm types, no new plugin/backend interfaces.
- No interface without two real implementations; use records and standard functional injection boundaries.
- Swing and effective theme selection are EDT-confined; file I/O, parsing and native detector initialization are off the EDT.
- Preserve sessions, history/selection, find state, font overrides, retained split components/ratios, zoom, focus and tab motion.
- Keep 4px terminal padding; status background equals terminal background.
- Never rewrite the user's config to save a View choice. `--config` does not relocate themes.
- Never commit directly on main; explicit task-path staging and `Co-Authored-By: Codex <noreply@openai.com>` on every commit.
- Source uses Java escapes for control/private-use/surrogate characters.
- Per-task implementer/reviewer gates, then whole-branch review. Record deviations here and in `docs/STATUS.md`.

---

**Execution status:** Authorized by the user on 2026-09-12. All four tasks implemented and independently reviewed; whole-branch findings resolved and scoped re-review approved through `3c3af48`. No review findings remain open. Fresh final verification: 506 tests, 505 passed, one known font skip; all eight tasks executed. Developed from `b92221f` in `.worktrees/plan-4c` on `codex/plan-4c-themes`; user-approved local integration fast-forwarded main to `944f3c3`. The main checkout was clean at integration. Merged verification passed all eight tasks: 506 tests, 505 passed, one known font skip. Baseline `./gradlew check` executed all eight tasks successfully: 463 tests, 462 passed, one known font skip, zero failures/errors. Native detection and visual acceptance were not run.

## File responsibilities and contracts

All new Java files use package `dev.jasper.app` under `jasper-app/src/main/java/dev/jasper/app/`; test counterparts use `jasper-app/src/test/java/dev/jasper/app/`.

| File | Responsibility |
|---|---|
| `Appearance.java` | Saved/runtime System, Light, Dark choice |
| `ColorsConfig.java` | Validated theme selector and saved appearance |
| `ResolvedTheme.java` | Independent chrome and terminal palette |
| `ThemeState.java` | Pure, immutable preference/override reducer |
| `ThemeLoader.java` | Pure supported-subset TOML palette parser |
| `ThemeFiles.java` | Bounded read, cache, diagnostics and last-good palette |
| `SystemAppearance.java` | Deferred native adapter, serial callbacks and cleanup |
| `ConfigSnapshot.java`, `ConfigLoader.java` | New colors model and legacy parsing |
| `ConfigService.java`, `Main.java`, `AppDirs.java` | Independent theme polling; explicit default theme directory plumbing |
| `ThemeController.java`, `ConfigurationController.java`, `JasperApplication.java` | Resolve/apply saved, manual and system changes; own listener lifecycle |
| `WindowContent.java`, `TerminalPane.java`, `WindowChrome.java`, `WindowStatusBar.java`, `MacTitleBar.java`, `TerminalWindow.java` | Apply palette/chrome to appropriate surfaces and synchronize menus |
| `ConfigTemplate.java`, root `config.example.toml`, `docs/configuration.md`, `README.md`, `docs/STATUS.md` | User instructions and accurate status |
| `docs/examples/themes/jasper-custom.toml` | Complete copyable supported theme fixture |

Keep `BuiltinTheme` unchanged as the two FlatLaf choices and built-in palette factory. Replace `ConfigSnapshot`'s `BuiltinTheme theme` component with `ColorsConfig colors`; do not invent a `theme()` accessor that lies for custom themes. Migrate callers by compiler errors and `rg '\.theme\(\)|BuiltinTheme' jasper-app/src`.

Exact shared interfaces:

```java
enum Appearance { SYSTEM, LIGHT, DARK }
record ColorsConfig(Appearance appearance, String theme) {
    static ColorsConfig defaults();
    static boolean validSelector(String value);
    boolean custom();
}
record ResolvedTheme(BuiltinTheme chrome, dev.jasper.terminal.Palette palette) {}
record ThemeState(ColorsConfig saved, dev.jasper.terminal.Palette loaded,
                  Appearance override, BuiltinTheme system) {
    static ThemeState defaults();
    ThemeState configure(ColorsConfig next, dev.jasper.terminal.Palette palette);
    ThemeState choose(Appearance choice);
    ThemeState systemChanged(BuiltinTheme choice);
    Appearance choice();
    ResolvedTheme resolve();
}
// ThemeLoader and ThemeFiles return non-null palettes even on failure.
// rejected means the caller keeps its last good palette.
// Their diagnostic lists are defensive copies.
// ThemeLoader.Result(Palette palette, List<ConfigDiagnostic> diagnostics, boolean rejected)
// static ThemeLoader.Result ThemeLoader.parse(Path file, String text)
// ThemeFiles.Result(Palette palette, List<ConfigDiagnostic> diagnostics)
// new ThemeFiles(Path directory)
// ThemeFiles.Result ThemeFiles.refresh(ColorsConfig colors, boolean force)
// ConfigService.State(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics,
//                     Path file, boolean present, Palette palette)
```

Only `ThemeState.override` may be null, meaning no runtime override. Palette lists already enforce immutability in `Palette`.

## Task 1: Saved appearance and pure theme resolution

**Files:** Create `Appearance.java`, `ColorsConfig.java`, `ResolvedTheme.java`, `ThemeState.java`; modify `ConfigSnapshot.java`, `ConfigLoader.java`; create `ThemeStateTest.java`, extend `ExpandedConfigTest.java`, `ConfigLoaderTest.java`. Migrate constructor usages throughout existing app tests without weakening assertions. For this independently passing task, existing UI consumers temporarily use `snapshot.colors().appearance()` to pick built-in chrome; Task 4 removes that adapter. Custom filenames become accepted intent but are not advertised as usable before Task 2.

**Interfaces:** Produces the four records/enums above. Existing six-argument and full old `ConfigSnapshot` constructors remain overloads, converting `BuiltinTheme.LIGHT/DARK` to explicit Light/Dark `ColorsConfig`; `defaults()` must directly use `ColorsConfig.defaults()` so it remains System.

- [x] **1. Write resolution and migration tests.** Add these complete test bodies with the normal JUnit/AssertJ imports:

```java
@Test void automaticBuiltinsFollowSystemButCustomColorsStayFixed() {
    var initial = ThemeState.defaults();
    assertThat(initial.systemChanged(BuiltinTheme.LIGHT).resolve().palette())
        .isEqualTo(Palette.jasperLight());
    var fixed = initial.configure(new ColorsConfig(Appearance.SYSTEM, "custom"), Palette.jasperDark());
    assertThat(fixed.systemChanged(BuiltinTheme.LIGHT).resolve())
        .isEqualTo(new ResolvedTheme(BuiltinTheme.LIGHT, Palette.jasperDark()));
}
@Test void manualChoiceSurvivesPaletteEditsButSavedAppearanceChangeClearsIt() {
    var colors = new ColorsConfig(Appearance.SYSTEM, "custom");
    var chosen = ThemeState.defaults().configure(colors, Palette.jasperDark())
        .choose(Appearance.DARK).systemChanged(BuiltinTheme.LIGHT);
    var edited = chosen.configure(colors, Palette.jasperLight());
    assertThat(edited.resolve().chrome()).isEqualTo(BuiltinTheme.DARK);
    assertThat(edited.resolve().palette()).isEqualTo(Palette.jasperLight());
    assertThat(edited.choose(Appearance.SYSTEM).resolve().chrome()).isEqualTo(BuiltinTheme.LIGHT);
    assertThat(edited.configure(new ColorsConfig(Appearance.LIGHT, "custom"), Palette.jasperLight()).override())
        .isNull();
}
@Test void legacyThemeRetainsAppearanceAndExplicitSystemOptsIn() {
    var path = Path.of("config.toml");
    var legacy = ConfigLoader.parse(path, "[colors]\ntheme = 'jasper-light'\n", true);
    var automatic = ConfigLoader.parse(path,
        "[colors]\ntheme = 'jasper-light'\nappearance = 'system'\n", true);
    assertThat(legacy.snapshot().colors().appearance()).isEqualTo(Appearance.LIGHT);
    assertThat(automatic.snapshot().colors().appearance()).isEqualTo(Appearance.SYSTEM);
    assertThat(ConfigLoader.parse(path, "", true).snapshot().colors()).isEqualTo(ColorsConfig.defaults());
}
```

Also parameterize selector acceptance/rejection: `night`, `night.toml`, `My Theme`, Unicode names; reject `../night`, `/night`, backslash, colon, `.`/`..`, control characters and blank names. Cover invalid appearance string/default versus wrong-type/rejected, and order-independent TOML fields.

- [x] **2. Run RED.** `./gradlew :jasper-app:test --tests '*ThemeStateTest' --tests '*ExpandedConfigTest' --tests '*ConfigLoaderTest'`; missing model types/accessors should fail compilation.

- [x] **3. Implement models and parser migration.** Use these complete model bodies (imports `java.util.Objects`, `dev.jasper.terminal.Palette`):

```java
record ColorsConfig(Appearance appearance, String theme) {
    ColorsConfig {
        Objects.requireNonNull(appearance);
        if (!validSelector(theme)) throw new IllegalArgumentException("Use a theme basename in Jasper's themes directory.");
    }
    static ColorsConfig defaults() { return new ColorsConfig(Appearance.SYSTEM, "jasper-dark"); }
    static boolean validSelector(String value) {
        return value != null && !value.isBlank() && !value.equals(".") && !value.equals("..")
            && value.codePoints().noneMatch(c -> Character.isISOControl(c) || "/\\:*?\"<>|".indexOf(c) >= 0);
    }
    boolean custom() { return !theme.equals("jasper-dark") && !theme.equals("jasper-light"); }
}
record ResolvedTheme(BuiltinTheme chrome, Palette palette) {
    ResolvedTheme { Objects.requireNonNull(chrome); Objects.requireNonNull(palette); }
}
record ThemeState(ColorsConfig saved, Palette loaded, Appearance override, BuiltinTheme system) {
    ThemeState { Objects.requireNonNull(saved); Objects.requireNonNull(loaded); Objects.requireNonNull(system); }
    static ThemeState defaults() {
        return new ThemeState(ColorsConfig.defaults(), Palette.jasperDark(), null, BuiltinTheme.DARK);
    }
    ThemeState configure(ColorsConfig next, Palette palette) {
        return new ThemeState(next, palette, saved.appearance() == next.appearance() ? override : null, system);
    }
    ThemeState choose(Appearance next) { return new ThemeState(saved, loaded, Objects.requireNonNull(next), system); }
    ThemeState systemChanged(BuiltinTheme next) { return new ThemeState(saved, loaded, override, next); }
    Appearance choice() { return override == null ? saved.appearance() : override; }
    ResolvedTheme resolve() {
        BuiltinTheme chrome = switch (choice()) {
            case SYSTEM -> system;
            case LIGHT -> BuiltinTheme.LIGHT;
            case DARK -> BuiltinTheme.DARK;
        };
        return new ResolvedTheme(chrome, saved.custom() ? loaded : chrome.palette());
    }
}
```

`Appearance.java` contains the enum declared in contracts. `ConfigLoader` adds `appearance` to the colors field whitelist, changes its theme field to `String theme = "jasper-dark"`, and adds `Appearance appearance = Appearance.SYSTEM`. Parse with existing positioned helpers:

```java
case "colors.theme" -> theme = string(path, value, ColorsConfig::validSelector,
    "Use a theme basename in Jasper's themes directory; using the default.", theme);
case "colors.appearance" -> appearance = choice(path, value, Map.of(
    "system", Appearance.SYSTEM, "light", Appearance.LIGHT, "dark", Appearance.DARK), Appearance.SYSTEM);
```

After `readTable`, compute legacy inference from the raw presence/value, never iteration order:

```java
if (!toml.contains(List.of("colors", "appearance"))) {
    Object legacy = toml.get(List.of("colors", "theme"));
    if ("jasper-light".equals(legacy)) appearance = Appearance.LIGHT;
    else if ("jasper-dark".equals(legacy)) appearance = Appearance.DARK;
}
ColorsConfig colors = new ColorsConfig(appearance, theme);
```

Pass `colors` to the canonical snapshot constructor. Keep existing numeric/keybinding validation unchanged. The temporary built-in bridge for current UI call sites is:

```java
BuiltinTheme selected = snapshot.colors().appearance() == Appearance.LIGHT
    ? BuiltinTheme.LIGHT : BuiltinTheme.DARK;
```

- [x] **4. Run GREEN and commit.** Run the RED command then `./gradlew check`. Stage the four model files, changed snapshot/loader and exact migrated caller/test files (inspect `git diff --name-only` first). Commit `feat: model saved and effective appearance` with the required coauthor trailer. Review before Task 2.

## Task 2: Custom palette parsing and independent live file reload

**Files:** Create `ThemeLoader.java`, `ThemeFiles.java`, `ThemeLoaderTest.java`, `ThemeFilesTest.java`; modify `ConfigService.java`, `Main.java`, `ConfigServiceTest.java`, `MainConfigurationTest.java`, and State constructor callers. Create `docs/examples/themes/jasper-custom.toml` alongside parser tests, but keep runtime documentation marked upcoming until Task 4.

**Interfaces:** `ThemeLoader.parse` and `ThemeFiles.refresh` from contracts. Add `ConfigService(Path file, Path themes, boolean macOs)` and the corresponding injected-worker/publisher overload. Existing test overloads delegate to a sibling fixture `file.toAbsolutePath().getParent().resolve("themes")`; production `Main.start` explicitly supplies `AppDirs.themes()`. `ConfigService.State` adds `Palette palette`, never null; compatibility four-argument constructor uses the snapshot's saved built-in seed or Dark for custom intent.

- [x] **1. Write parser and last-good lifecycle tests.**

```java
@Test void parsesSupportedColorsAndKeepsFixedDefaults() {
    var result = ThemeLoader.parse(Path.of("night.toml"), """
        [colors.primary]
        background = '#101820'
        [colors.normal]
        red = '0xEF3340'
        """);
    assertThat(result.rejected()).isFalse();
    assertThat(result.palette().background()).isEqualTo(new Color(0x101820));
    assertThat(result.palette().ansi().get(1)).isEqualTo(new Color(0xef3340));
    assertThat(result.palette().foreground()).isEqualTo(Palette.jasperDark().foreground());
}
@Test void fileRepairSucceedsWithoutChangingTheSelector(@TempDir Path directory) throws Exception {
    var file = directory.resolve("night.toml");
    var files = new ThemeFiles(directory);
    var colors = new ColorsConfig(Appearance.SYSTEM, "night");
    Files.writeString(file, "[colors.primary]\nbackground = '#101820'\n");
    var good = files.refresh(colors, false);
    Files.writeString(file, "[colors.primary]\nbackground = 'broken'\n");
    assertThat(files.refresh(colors, true).palette()).isEqualTo(good.palette());
    Files.delete(file);
    assertThat(files.refresh(colors, false).diagnostics()).isNotEmpty();
    Files.writeString(file, "[colors.primary]\nbackground = '#fafafa'\n");
    var repaired = files.refresh(colors, false);
    assertThat(repaired.palette().background()).isEqualTo(new Color(0xfafafa));
    assertThat(repaired.diagnostics()).isEmpty();
}
```

Add fixtures for every supported field and ANSI ordering, unknown-key warning with source position, invalid supported value/type, symbolic color rejection, duplicate/syntax errors, no supported keys, Unicode selectors, 256 KiB limit, invalid UTF-8, nonregular file, missing directory, in-root/escaping symlinks (conditional where platform permissions require it). Service-level tests must actually leave config content/mtime unchanged while editing a theme; assert delivery, last-good palette, continued unrelated config updates, stale publication rejection and forced reload of equal-size/equal-mtime content. Use existing injected executor/publisher rather than sleeping one second.

- [x] **2. Run RED.** `./gradlew :jasper-app:test --tests '*ThemeLoaderTest' --tests '*ThemeFilesTest' --tests '*ConfigServiceTest' --tests '*MainConfigurationTest'`; absent loader/files types and new state accessors must fail.

- [x] **3. Implement pure parsing.** Build a `LinkedHashMap<List<String>,Color>` from `Palette.jasperDark()` using the field table below. Traverse tables recursively with component lists (not dotted-string splitting) to preserve quoted-key semantics. TOML syntax errors return `rejected=true`. Unknown tables/keys produce a warning at `toml.inputPositionOf(path)`. Any wrong type at a supported table/leaf or malformed supported value produces an error; at least one supported leaf is required. Return the entire Dark candidate on rejection; callers retain last-good.

The field map and candidate assembly code are:

```java
Palette base = Palette.jasperDark();
var values = new LinkedHashMap<List<String>, Color>();
values.put(List.of("colors", "primary", "foreground"), base.foreground());
values.put(List.of("colors", "primary", "background"), base.background());
values.put(List.of("colors", "cursor", "cursor"), base.cursor());
values.put(List.of("colors", "selection", "background"), base.selection());
String[] names = {"black", "red", "green", "yellow", "blue", "magenta", "cyan", "white"};
for (int i = 0; i < 16; i++) {
    values.put(List.of("colors", i < 8 ? "normal" : "bright", names[i % 8]), base.ansi().get(i));
}
// After validated traversal replaces supported entries:
var ansi = new ArrayList<Color>();
for (int i = 0; i < 16; i++) ansi.add(values.get(List.of("colors", i < 8 ? "normal" : "bright", names[i % 8])));
Palette candidate = new Palette(values.get(List.of("colors", "primary", "foreground")),
    values.get(List.of("colors", "primary", "background")), values.get(List.of("colors", "cursor", "cursor")),
    values.get(List.of("colors", "selection", "background")), ansi);
```

Use this complete color decoder; catch its `IllegalArgumentException` at the positioned leaf boundary, not at the whole service boundary:

```java
private static Color decode(Object value) {
    if (!(value instanceof String text) || !text.matches("(?:#|0x)[0-9a-fA-F]{6}")) {
        throw new IllegalArgumentException("Use a quoted #RRGGBB color; per-cell color references are not supported.");
    }
    return new Color(Integer.parseInt(text.substring(text.startsWith("#") ? 1 : 2), 16));
}
```

The recursive traversal uses the actual TomlJ component-list APIs:

```java
private static void visit(TomlParseResult root, TomlTable table, List<String> parent,
                          Map<List<String>, Color> values, List<ConfigDiagnostic> diagnostics,
                          Path file, int[] supported) {
    for (String key : table.keySet()) {
        var path = new ArrayList<>(parent); path.add(key);
        Object value = table.get(List.of(key));
        TomlPosition pos = root.inputPositionOf(path);
        String message = null;
        ConfigDiagnostic.Severity severity = ConfigDiagnostic.Severity.WARNING;
        if (values.containsKey(path)) {
            supported[0]++;
            try { values.put(List.copyOf(path), decode(value)); }
            catch (IllegalArgumentException failure) { message = failure.getMessage(); severity = ConfigDiagnostic.Severity.ERROR; }
        } else if (values.keySet().stream().anyMatch(p -> p.size() > path.size() && p.subList(0, path.size()).equals(path))) {
            if (value instanceof TomlTable nested) visit(root, nested, path, values, diagnostics, file, supported);
            else { message = "Expected a color table."; severity = ConfigDiagnostic.Severity.ERROR; }
        } else message = "Unsupported theme setting or table; ignored.";
        if (message != null) diagnostics.add(new ConfigDiagnostic(severity, file,
            pos == null ? 0 : pos.line(), pos == null ? 0 : pos.column(), String.join(".", path), message));
    }
}
```

`parse` initializes the map, translates `toml.errors()` exactly as `ConfigLoader` does, invokes `visit` with `new int[1]`, adds an error at 0/0 if its count is zero, and returns `Result(candidate, diagnostics, any ERROR)`. Sort diagnostics by line/column; copy lists in record constructors.

- [x] **4. Implement file cache and join.** `ThemeFiles` owns directory, previous successful palette (initial Dark), last key and cached Result. Its key record is `Key(Path requested, Path real, FileTime modified, long size)`; only successful metadata resolution creates a key. Missing/access errors clear the key so later creation can recover. Resolve selectors with `directory.resolve(name.endsWith(".toml") ? name : name + ".toml")`. Resolve both directory and target to real paths and require the target starts with the real directory. Read attributes from the real target and reject nonregular or oversized files before opening. Catch only I/O, invalid path and security exceptions and translate them to file-position 0/0 diagnostics.

Use this bounded reading code after those checks:

```java
byte[] bytes;
try (var input = Files.newInputStream(real)) { bytes = input.readNBytes(256 * 1024 + 1); }
if (bytes.length > 256 * 1024) throw new IOException("Theme exceeds 256 KiB.");
String text = StandardCharsets.UTF_8.newDecoder()
    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    .decode(ByteBuffer.wrap(bytes)).toString();
var parsed = ThemeLoader.parse(requested, text);
if (!parsed.rejected()) lastGood = parsed.palette();
cached = new Result(lastGood, parsed.diagnostics());
```

On a built-in selector, clear the custom cache, set `lastGood` to that saved ID's palette, and return it with no diagnostics. On a custom cache hit and `!force`, return the cached result. On failure return lastGood plus one error; do not lose the desired selector. An escaping symlink must never be opened. No glob scan or eager loading of unrelated files.

In `ConfigService`, split current config-only state from the published joined State so theme errors never feed back as main-config parse diagnostics. Every refresh performs this join even if `readState` takes its config-fingerprint early return:

```java
State config = readState(lastConfig.snapshot(), force); // existing config read logic, kept config-only
lastConfig = config;
ThemeFiles.Result selected = themeFiles.refresh(config.snapshot().colors(), force);
var diagnostics = new ArrayList<>(config.diagnostics());
diagnostics.addAll(selected.diagnostics());
State joined = new State(config.snapshot(), diagnostics, config.file(), config.present(), selected.palette());
```

Use `joined` for existing equality/revision/publication logic and initialState; `lastConfig` is private worker-owned state. Preserve current failure/rejected-snapshot semantics, startup constructor off-EDT, queued-close guards and future completion rules. In Main resolve `AppDirs dirs` once and pass both chosen config path and `dirs.themes()`.

- [x] **5. Run GREEN and commit.** Repeat RED command and `./gradlew check`. Stage exact changed files and commit `feat: load and reload custom terminal palettes` with coauthor. Review before Task 3.

## Task 3: Application-owned system appearance source

**Files:** Create `SystemAppearance.java`, `SystemAppearanceTest.java`; modify root `build.gradle.kts`, `jasper-app/build.gradle.kts`. No native initialization in headless tests.

**Interfaces:**

```java
// Nested records in SystemAppearance; all consumers are non-null.
record Binding(BooleanSupplier dark, Consumer<Consumer<Boolean>> register,
               Consumer<Consumer<Boolean>> remove) {}
record Reading(BuiltinTheme theme, String warning) {} // warning empty when healthy
// static SystemAppearance production()
// static SystemAppearance fixed(BuiltinTheme theme)
// SystemAppearance(Supplier<Binding> factory, ExecutorService worker, Consumer<Runnable> publisher)
// void start(Consumer<Reading> listener) // once; schedules initialization, never blocks EDT
// void close() // idempotent; no callbacks after closure
```

This source may own one daemon executor for initialization/event serialization; it does not poll themes/config and never creates one source per window. Initial effective appearance is Dark until the first asynchronous reading. Unsupported/failing native detection produces Dark with one diagnostic. Existing fixed Light/Dark config still applies immediately. Do not claim the asynchronous source eliminates all startup recoloring.

- [x] **1. Write a synthetic callback lifecycle test.** Use the existing test `edt`/`until` helpers; this test uses no detector factory or native state:

```java
@Test void serializesReadAndEventsAndDropsQueuedPublicationAfterClose() throws Exception {
    var callbacks = new java.util.concurrent.CopyOnWriteArrayList<java.util.function.Consumer<Boolean>>();
    var queue = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
    var received = new java.util.concurrent.CopyOnWriteArrayList<SystemAppearance.Reading>();
    var dark = new java.util.concurrent.atomic.AtomicBoolean(false);
    var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
    var source = new SystemAppearance(() -> new SystemAppearance.Binding(dark::get,
        callbacks::add, callbacks::remove), worker, queue::add);
    try {
        source.start(received::add);
        until(() -> !queue.isEmpty());
        queue.remove().run();
        assertThat(received).extracting(SystemAppearance.Reading::theme).containsExactly(BuiltinTheme.LIGHT);
        dark.set(true);
        callbacks.getFirst().accept(true);
        until(() -> !queue.isEmpty());
        source.close();
        queue.remove().run();
        assertThat(received).hasSize(1);
        until(callbacks::isEmpty);
    } finally { source.close(); }
}
```

Add tests for duplicate readings, unsupported factory, linkage/runtime initialization failure, register-before-read event race, callback after close, close before initialization, repeated close and registration failure cleanup. An initial read failure must not discard a successfully registered listener; a later valid event clears its warning.

- [x] **2. Run RED.** `./gradlew :jasper-app:test --tests '*SystemAppearanceTest'`; missing adapter must fail compilation.

- [x] **3. Add pinned dependency and deferred factory.** Root `subprojects.repositories` adds this restricted repository:

```kotlin
exclusiveContent {
    forRepository { maven("https://jitpack.io") }
    filter { includeGroup("com.github.Dansoftowner") }
}
```

App dependencies add:

```kotlin
implementation("com.github.Dansoftowner:jSystemThemeDetector:3.9.1")
```

Production factory code, invoked only inside the source worker:

```java
if (!com.jthemedetecor.OsThemeDetector.isSupported())
    throw new IllegalStateException("System appearance is unavailable on this desktop; using Dark.");
var detector = com.jthemedetecor.OsThemeDetector.getDetector();
return new Binding(detector::isDark, detector::registerListener, detector::removeListener);
```

Use the upstream package spelling `jthemedetecor`, despite the artifact's different spelling. `production()` allocates a daemon single-thread executor named `jasper-appearance`, passing `SwingUtilities::invokeLater`; `fixed` uses the same constructor with a no-op register/remove and a constant BooleanSupplier, so tests never call the production factory.

Serialize all initialization, reads, notifications and unregister work on that executor. Use an atomic closed flag; `start` has a separate once-only flag. Keep one stable `Consumer<Boolean> callback`; it enqueues a fresh `binding.dark().getAsBoolean()` sample and conversion to `Reading`, ignoring the event payload. Re-sampling prevents an event queued before the initial read from overwriting that newer read with an older Boolean. Catch `RejectedExecutionException` only when the source is already closed, otherwise propagate it. This is the worker submission/publication core (fields `closed`, `worker`, `publisher`, `listener`, `last`):

```java
private void enqueue(Runnable action) {
    if (closed.get()) return;
    try { worker.execute(() -> { if (!closed.get()) action.run(); }); }
    catch (RejectedExecutionException failure) { if (!closed.get()) throw failure; }
}
private void publish(Reading reading) {
    if (reading.equals(last)) return;
    last = reading;
    publisher.accept(() -> { if (!closed.get()) listener.accept(reading); });
}
```

Initialization stores the Binding, registers the callback, then publishes `dark.getAsBoolean()`; events queue behind this action and re-read current appearance. Catch RuntimeException/LinkageError around native initialization/read, publish Dark with an actionable plain warning (do not catch arbitrary Throwable). Keep the registered Binding after a read failure. Register attempts set the cleanup-required flag before the call so partial registration is still removed.

Close must enqueue cleanup directly (not via the closed-guarded enqueue helper), then `worker.shutdown()` so cleanup drains:

```java
@Override public void close() {
    if (!closed.compareAndSet(false, true)) return;
    worker.execute(() -> {
        if (binding != null && cleanupRequired) {
            try { binding.remove().accept(callback); }
            catch (RuntimeException | LinkageError ignored) { /* Owner already closed; no UI callback. */ }
        }
        binding = null;
        listener = ignored -> {};
    });
    worker.shutdown();
}
```

Do not invoke the upstream source in tests, change host appearance or launch a GUI. Resolve and inspect runtime dependencies with `./gradlew :jasper-app:dependencies --configuration runtimeClasspath`; confirm no downgrade of existing JNA or SLF4J selections. Preserve the existing native-access JVM option. The upstream process-wide observer remains alive after removeListener; record that limitation rather than adding unsupported teardown.

- [x] **4. Run GREEN and commit.** Repeat RED and `./gradlew check`. Commit exact adapter/test/build paths as `feat: observe system appearance outside the EDT`, with coauthor; review before Task 4.

## Task 4: Apply resolved themes across the application and document usage

**Files:** Modify `ThemeController.java`, `ConfigurationController.java`, `JasperApplication.java`, `Main.java`, `WindowContent.java`, `TerminalPane.java`, `WindowChrome.java`, `WindowStatusBar.java`, `MacTitleBar.java`, `TerminalWindow.java`, `ConfigTemplate.java`, root `config.example.toml`, `docs/configuration.md`, `README.md`, `docs/STATUS.md`. Extend `ThemeControllerTest.java`, `ConfigurationControllerTest.java`, `ConfigurationStatusTest.java`, `ConfigTemplateTest.java`, `MockUiTest.java`, `MacTitleBarTest.java`; create `SystemThemeIntegrationTest.java` and `docs/superpowers/plans/2026-09-12-jasper-plan-4c-manual-check.md`. Remove the Task 1 temporary bridge.

**Interfaces:**

```java
// ThemeController, EDT-only:
// ResolvedTheme current()
// Appearance choice()
// void configure(ColorsConfig colors, Palette loaded)
// void selectAppearance(Appearance appearance)
// void systemChanged(BuiltinTheme system)
// Existing select(BuiltinTheme) remains a delegating Light/Dark compatibility overload.
// WindowContent: ResolvedTheme theme(); void selectAppearance(Appearance choice)
// void applyTheme(ResolvedTheme theme, boolean updateDelegates)
// onThemeChanged becomes Consumer<ResolvedTheme>.
// TerminalPane: void applyTheme(Palette palette)
// MacTitleBar: existing applyTheme(BuiltinTheme chrome) remains chrome-only.
// ConfigurationController overload adds SystemAppearance source; owns start/close.
// JasperApplication(ConfigService service, SystemAppearance source) is used by Main;
// existing service-only/test constructors delegate to SystemAppearance.fixed(DARK).
```

- [x] **1. Write tests against actual retained components.** Add this integration test using `DesktopTestSupport` static imports, ArrayDeque and the existing owner cleanup after each test:

```java
@Test void customPaletteSurvivesSystemChangeWithoutReplacingSession() throws Exception {
    var pending = new ArrayDeque<Runnable>();
    ThemeController[] themes = new ThemeController[1];
    WindowContent[] owner = new WindowContent[1];
    edt(() -> {
        themes[0] = new ThemeController();
        owner[0] = content(launcher(pending), themes[0]);
    });
    pending.remove().run();
    edt(() -> {
        var pane = owner[0].currentPane();
        var session = pane.session();
        var palette = new Palette(new Color(0xffeeee), new Color(0x101820),
            new Color(0xffcc00), new Color(0x334455), Palette.jasperDark().ansi());
        themes[0].configure(new ColorsConfig(Appearance.SYSTEM, "night"), palette);
        var delegate = owner[0].getUI();
        themes[0].configure(new ColorsConfig(Appearance.SYSTEM, "night"), Palette.jasperDark());
        assertThat(owner[0].getUI()).isSameAs(delegate); // palette-only edit
        themes[0].configure(new ColorsConfig(Appearance.SYSTEM, "night"), palette);
        themes[0].systemChanged(BuiltinTheme.LIGHT);
        assertThat(themes[0].current()).isEqualTo(new ResolvedTheme(BuiltinTheme.LIGHT, palette));
        assertThat(pane.session()).isSameAs(session);
        assertThat(pane.view().palette()).isEqualTo(palette);
        assertThat(pane.getBackground()).isEqualTo(palette.background());
        assertThat(owner[0].toolbar().getBackground()).isEqualTo(Palette.jasperLight().background());
    });
}
```

Extend existing retained/zoomed/late-pane and failed-installation tests with resolved themes. Do not replace those tests with reducer-only assertions. Verify status background equals the exact custom color, all owner menus reflect Follow System, explicit choices ignore OS changes, file changes preserve manual appearance, and changing saved appearance clears the override. Verify initial configuration reaches pending views and new windows. Count installer calls: custom palette edits must not call it. Make a failed LAF installation preserve the effective theme and selected menu, while retry succeeds. Synthetic source warnings must appear alongside config/theme diagnostics and clear independently; queued source/config callbacks after close must be ignored.

- [x] **2. Run RED.** `./gradlew :jasper-app:test --tests '*SystemThemeIntegrationTest' --tests '*ThemeControllerTest' --tests '*ConfigurationControllerTest' --tests '*ConfigurationStatusTest'`; missing resolved UI APIs should fail compilation.

- [x] **3. Replace ThemeController's current field with the immutable reducer.** Retain `Predicate<BuiltinTheme> installer`, existing `installOrThrow`, owner registry and EDT checks. Use this implementation for transition entry points and candidate application:

```java
private ThemeState state = ThemeState.defaults();
ResolvedTheme current() { requireEdt(); return state.resolve(); }
Appearance choice() { requireEdt(); return state.choice(); }
void configure(ColorsConfig colors, Palette loaded) {
    requireEdt(); apply(state.configure(colors, loaded));
}
void selectAppearance(Appearance choice) { requireEdt(); apply(state.choose(choice)); }
void select(BuiltinTheme theme) {
    selectAppearance(theme == BuiltinTheme.LIGHT ? Appearance.LIGHT : Appearance.DARK);
}
void systemChanged(BuiltinTheme system) { requireEdt(); apply(state.systemChanged(system)); }
private void apply(ThemeState candidate) {
    ResolvedTheme previous = state.resolve();
    ResolvedTheme next = candidate.resolve();
    boolean chromeChanged = previous.chrome() != next.chrome();
    boolean choiceChanged = state.choice() != candidate.choice();
    if (chromeChanged) installOrThrow(next.chrome()); // commit preference only after successful install
    state = candidate;
    if (!previous.equals(next) || choiceChanged) {
        for (WindowContent owner : List.copyOf(owners)) owner.applyTheme(next, chromeChanged);
    }
}
```

Constructor installs `state.resolve().chrome()`. Registration applies `current()` with `updateDelegates=true`; unregister retains existing behavior. A successful no-pixel-change choice still refreshes the menu, so System → manual Dark is visible on a dark OS. Keep the existing LAF restoration and error reporting, without catching arbitrary errors from application code.

- [x] **4. Join configuration, native events and UI lifecycle.** `ConfigurationController` uses the palette from every accepted State, even when ColorsConfig itself compares equal:

```java
themes.configure(next.snapshot().colors(), next.palette());
```

This replaces comparison of built-in IDs; leave application of unrelated settings outside the theme failure catch. Retain previous effective theme on an installation failure and report it through existing owners. Start the injected source after initial configuration has been applied; its listener calls `themes.systemChanged(reading.theme())` on the EDT, catches/reports installation failure and retains the latest source warning. The listener must check `closed` before any mutation. Keep config state separate from displayed diagnostic state so source warnings do not accumulate on subsequent reloads.

The diagnostic join for each owner is:

```java
var diagnostics = new ArrayList<>(state.diagnostics());
if (!appearanceWarning.isEmpty()) diagnostics.add(new ConfigDiagnostic(
    ConfigDiagnostic.Severity.WARNING, state.file(), 0, 0, "colors.appearance", appearanceWarning));
var displayed = new ConfigService.State(state.snapshot(), diagnostics, state.file(), state.present(), state.palette());
owner.setConfigurationState(displayed);
```

Initialize `appearanceWarning` to the empty string. Reuse this join for registration, source changes and config changes. `close()` sets closed first, closes source, unregisters owners and closes ConfigService. `Main` alone constructs `SystemAppearance.production()` for actual startup and passes it into the new JasperApplication overload; tests/preview constructors retain `fixed(DARK)`. If creating the application fails, close both source and service. `--help`/CLI errors must never allocate a source.

- [x] **5. Apply chrome and palette to their own surfaces.** Keep the full existing `WindowContent.applyTheme` retained-tab traversal and `beginThemeUpdate`/`endThemeUpdate` guards. Wrap its `SwingUtilities.updateComponentTreeUI` calls in `if (updateDelegates)`; use `theme.palette()` for view/pane updates and `theme.chrome()` for the native title property. Do not recreate or reparent split children. The view attachment hook uses `snapshot.viewOptions(effectiveSize, themes.current().palette())` at attachment time.

The pane conversion is:

```java
void applyTheme(Palette palette) {
    setBackground(palette.background());
    if (view != null) view.setPalette(palette);
    setActive(active); // retain the existing configured dimming implementation
}
```

The existing active-state field is `active`; this replaces its built-in-theme parameter and palette lookup, not its dimming logic. Preserve other existing child refresh operations. `WindowContent`/terminal deck/padding and status use the effective palette background. `WindowChrome` toolbar uses `owner.theme().chrome().palette().background()` and FlatLaf foreground/icon tokens; find controls remain LAF colors. `TerminalWindow` passes `resolved.chrome()` to `MacTitleBar.applyTheme`. `WindowStatusBar` must retain a `Palette palette` field across `setConfiguration`, since that method calls `refreshTheme()` and would otherwise reset a custom background to the LAF default. Add `void applyPalette(Palette next)` to store it and call `refreshTheme()`. WindowContent invokes it on each resolved theme application. Keep constructor default Dark until its owner applies the effective theme. Use the palette background for the status panel and derive custom muted text/separators with the helper below. Built-in palettes retain existing token colors. Config warning/error/success and running-dot accent colors need a readability fallback against the custom background; use the palette foreground if their contrast is below 3:1. The text labels still communicate state without relying on color.

```java
private static Color blend(Color foreground, Color background, double weight) {
    return new Color((int) Math.round(foreground.getRed() * weight + background.getRed() * (1 - weight)),
        (int) Math.round(foreground.getGreen() * weight + background.getGreen() * (1 - weight)),
        (int) Math.round(foreground.getBlue() * weight + background.getBlue() * (1 - weight)));
}
```

Use weight `.8` for custom muted text, `.25` for separators; use palette foreground instead of muted text if the blend's contrast falls below 3:1. Reuse the luminance/contrast calculation already exercised in ThemeControllerTest, moving a small color helper into WindowStatusBar if required. Add a regression calling `setConfiguration` after applying a custom palette, asserting its background remains exactly the custom background. Avoid chrome-derived text on a custom terminal background.

Change the Appearance radio map to `EnumMap<Appearance,JRadioButtonMenuItem>`. Keep Light at index 0 and Dark at index 1 for existing tests; append Follow System at index 2. Bind each item to `owner.selectAppearance(choice)` and synchronize from `themes.choice()`, not effective chrome. `WindowContent.selectAppearance` retains the existing error/menu-reset path used by `selectTheme`; keep `selectTheme(BuiltinTheme)` delegating for existing tests/callers. No new shortcut or toolbar control is necessary.

- [x] **6. Finish copyable configuration and native checklist.** In ConfigTemplate, root example and configuration guide, place this text in the colors block:

```toml
[colors]
# System switches both chrome and the built-in palette; custom palettes stay fixed.
appearance = "system"
# Either built-in ID follows appearance. A custom basename is loaded from themes/.
theme = "jasper-dark"
# theme = "my-theme.toml"
```

Document the full precedence matrix from the design, including legacy omitted-key behavior. Add the supported-format table, 256 KiB/UTF-8/path constraints, fixed Dark inheritance and last-good recovery. Include explicit copy instructions using an absent target or user-chosen filename; never copy to the user's live config automatically. `docs/examples/themes/jasper-custom.toml` must be a complete 20-color example:

```toml
[colors.primary]
foreground = "#abb2bf"
background = "#292c34"
[colors.cursor]
cursor = "#abb2bf"
[colors.selection]
background = "#3e4451"
[colors.normal]
black = "#282c34"
red = "#e06c75"
green = "#98c379"
yellow = "#e5c07b"
blue = "#61afef"
magenta = "#c678dd"
cyan = "#56b6c2"
white = "#abb2bf"
[colors.bright]
black = "#5c6370"
red = "#e06c75"
green = "#98c379"
yellow = "#e5c07b"
blue = "#61afef"
magenta = "#c678dd"
cyan = "#56b6c2"
white = "#ffffff"
```

The actual-file test must parse this fixture with no diagnostics. Extend the existing root-example parse test to check System default without overwriting any user-owned main edit during integration. README and STATUS should link configuration and the native checklist, and keep logging/launcher/packaging pending. The checklist contains unchecked items from spec §6, records JBR/OS/screens tested and distinguishes automated proof from user-run results.

- [x] **7. Run GREEN, full validation and review.** Repeat the RED command, then `./gradlew check --rerun-tasks`. Read XML counts and record any skip rather than saying all tests ran. Run `git diff --check` and AGENTS source-hygiene scan over both modules. Use headless component renders for dark/light plus one high-contrast custom theme, inspect images, and verify the status/padding surface visually; do not run GUI or benchmark. Commit explicit changed paths as `feat: apply system and custom themes across windows` with coauthor. Per-task review then whole-branch review must pass before proposing local integration. Resolve the main-checkout example edit with the user’s changes preserved, never by replacing that file wholesale.

## Self-review and execution handoff

Preparation self-review mapping: spec §2 → Tasks 1/4; §3 → Task 2; §4 → Tasks 2/3/4; §5 → Tasks 3/4; §6 → all test cycles and Task 4 native checklist; §7 remains future work. Parent full-Alacritty compatibility is explicitly narrowed in the design instead of silently promised. No source implementation is included in the preparation commit.

Execution uses the established subagent-driven workflow: one task implementer and review gate at a time, then final whole-branch review. The user subsequently authorized execution; all task gates are complete, with whole-branch review and native acceptance tracked separately. Inline execution with `superpowers:executing-plans` remains an alternative if the user changes that preference.

## Execution rulings

Ruling: Exclude the detector dependency’s transitive net.java.dev.jna group while retaining existing pty4j-provided JNA and JNA-platform 5.14.0 — detector metadata requested an unpublished jpms variant during Gradle test resolution; this avoids replacing the established runtime — costs revisiting the exclusion if native dependency requirements change. Implementer must include runtime dependency evidence.

Ruling: Keep the latest OS appearance reading separately from the committed effective theme and use it when retrying selection/configuration — the plan’s transactional pseudocode otherwise forgets an OS change when LAF installation fails — costs one extra state field, but preserves visual rollback and permits Follow System retry without another OS event. Task 4 implements and tests it.

Ruling: Explicit Reload publishes one accepted state through the existing revision/close guard even when its value is unchanged, while polling and Settings still suppress unchanged values — equal-state suppression prevented the promised failed-theme retry — costs one deliberate UI reapplication per user reload and avoids a second unguarded completion path.

## Final verification and handoff

All task reviews approved. Whole-branch review found an unchanged-file Reload retry gap and two service-test improvements; combined fix `3c3af48` resolves all three, and scoped re-review approved without new findings. Root `./gradlew check --rerun-tasks` passed in 13 seconds with all eight tasks executed: 506 tests, 505 passed, one known `FontSetTest.fallsBackWhenPrimaryCannotDisplay()` skip, zero failures/errors. Both-module source hygiene, documentation links, diff checks and commit trailers passed. Root inspected all three headless renders and verified the main example-config checksum was unchanged. Native checks are deliberately still unchecked in the [manual checklist](2026-09-12-jasper-plan-4c-manual-check.md). User-approved local integration is complete. Fresh merged verification reproduced 506 tests, 505 passed and one known skip with no failures/errors; continue from the main checkout. The completed feature branch/worktree are cleaned up after this verified integration.
