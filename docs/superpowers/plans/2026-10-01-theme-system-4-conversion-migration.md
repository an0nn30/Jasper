# Theme System Plan 4: Conversion, Vendoring and Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the offline native theme catalog, a shared converter/importer and a recoverable upgrade, then remove the two legacy theme stores.

**Architecture:** `appearance` owns native documents, serialization, generation, the FlatLaf adapter and catalog.
`themeimport` owns legacy readers, conversion and safe theme-file operations; it depends on appearance, never the
reverse. Bootstrap runs an application-owned upgrade coordinator before constructing ConfigService; config owns
selection rewrites. The existing selectors remain usable until Plan 5 supplies the Themes page and import UI.

**Tech Stack:** JBR 25, Gradle wrapper/JavaExec, existing tomlj/JSON/SafeXml readers, Python standard library fetch
script, Swing/FlatLaf 3.7, headless JUnit/AssertJ. No new runtime dependencies or SDK release.

**Spec:** [Approved theme design](../specs/2026-10-01-jasper-theme-system-design.md), §§2, 6, 8–9.
**Companion:** [Requirement coverage and decisions](2026-10-01-theme-system-remaining-work.md).
**Status:** Approved by the user; inline implementation in progress (2026-10-01). Re-audited against local `main@c8365dd1`,
with Plans 2 and 3 merged and verified. This replaces the earlier six-task outline with seven sequential review gates.
Only existing local provenance was inspected; the other upstream inputs/licenses have not been acquired or verified.
Task 3 freezes those inputs. The `[icons]` addition below is an explicit proposed format amendment, reviewed with this
plan; record it in the spec when implemented. Execution decisions and verification are recorded below.
**Execution:** User chose inline execution after approving the plan: one implementer in this session, TDD/commits
per task, then one independent whole-branch review. This supersedes the per-task subagent workflow in the draft. Reuse `.worktrees/theme-system` / `codex/theme-system-plans`; do not commit on main. No merge or push is part
of this planning change. User review received; continue through all tasks without intermediate approval requests.

**Execution evidence:** Task 1 completed inline: icon/native round trips, nested-value immutability, source diagnostics,
author-specific install parity and Unicode supplementary names are tested. Initial regressions failed for missing
icons/shallow copies; a separate Unicode regression failed before correction. Focused checks and full `./gradlew check`
passed: **7,904 tests, zero failures/errors, six skips**. The `[icons]` amendment is recorded in the spec. No GUI run.
The two temporary ThemeManager legacy-loader entry points remain until Task 2; its install/materialization paths
already use the extracted data-only adapter. Inline execution (explicit user selection) replaces per-task reviewers.

Task 2 conversion is complete: full `./gradlew check` passed **7,918 tests, zero failures/errors, six skips**. Legacy parsers/resources now belong to `themeimport`;
classic built-ins are native TOML. Conversion preserves syntax/font flags, icons, authors, ordered/per-OS UI and
local/archive theme and scheme parents. Strict and partial-result APIs share bounded parsing. The transitional catalog
retains filename precedence, diagnostics and cache identity, including archive inspection of `.part` downloads.
Deviations: unknown IntelliJ parents retain the existing explicit fallback warning rather than failing (binding design
requires preserving loader behavior). Inverse tokens use canonical keys and verified equivalent FlatLaf aliases only;
forward template fan-out is not reversible. Unauthored Jasper chrome aliases and OS-dependent tokens remain omitted,
so retained UI/private materialization preserve appearance. Five full-suite effective-color mismatches proved this
necessary. Cost: a smaller exported token section; authored UI and effective appearance remain covered by parity.

Task 3 catalog/tooling is complete. Final `./gradlew --offline check` passed **7,926 tests, zero failures/errors, six skips**; the subsequent notices-only trailing-blank cleanup passed the complete drift/provenance/catalog test class. Twelve pinned sources and 413 hashed inputs produce
284 native themes: Jasper 2, IntelliJ 2, Alacritty 168, One Dark 2, Dracula 3, Solarized 2, Catppuccin 4,
Material Lite 88, Code 3, Nord 1, Gruvbox 6, One Dark Modern 1 and Studio 2. Catppuccin 3.6.1's release JAR digest
and source tag commit are verified. Full variant accounting, licenses, renames and exclusions are in
`tools/themes/sources.json`, per-source `SOURCE.md`/`assets.tsv` and `THIRD-PARTY-THEMES.md`. Material's Monokai Pro
variants and out-of-scope Islands variants are explicitly excluded; current Gruvbox's six are retained under approved
names. Alacritty Frappe aliases canonical Frappé. The largest native file is 97,426 bytes, so no limit increase is needed.
Decisions: One Dark Modern's unchanged scheme bytes get an explicit packaging alias correcting its broken declared
path; Darcula's lock explicitly preserves its prior named editor-scheme association. Root-relative scheme paths are
supported alongside sibling paths inside bounded archives. Inversion honors actual start/end button precedence and
ordered mapped aliases; tokens affected by FlatLaf's post-JSON author/theme adjustments are omitted using its pinned
properties data. Whole-catalog headless installed/workspace parity failed across 12 keys before these fixes and now
passes. The golden's only change is the correctly inferred gradient button border. Costs: fewer explicit exported
tokens (effective UI preserved); packaging aliases and extra catalog variants remain reversible manifest decisions.

Task 4 is complete: full `./gradlew check` passed **7,946 tests, zero failures/errors, six skips**. Frozen import
plans preserve per-provider identities, independent choices, source bytes and diagnostics. Whole-selection validation
precedes publication; user conflicts default to Skip, Keep Both reserves batch-wide display names, and built-in
shadows stay user files. File publication uses cooperating home/process locks, hard-link creation without replacement,
expected-hash/identity rechecks and atomic replacement. Regression tests cover stale/new conflicts, partial writes,
case/device/Unicode filenames, concurrent creators, symlinks, directory swaps, EDT rejection and late provider errors.
Decision: cleanup errors after successful publication are logged without falsely reporting the committed output as
failed; cleanup targets only the original temporary's identity (a secure directory handle where supported). Cost:
an owned hidden temporary may remain after a cleanup failure. Observed external edits fail safely; the API does not
claim compare-and-swap against arbitrary external editors racing the final rename.

## Global Constraints

- Native files live at `<home>/themes/<id>.toml`; bundled resources at `dev/jasper/app/themes/<id>.toml`, with `index.txt`.
- Names match ignoring case and surrounding whitespace. User files override bundled names; duplicate user names
  resolve in file-name order with a warning. Preserve this precedence, including previously selected legacy sources.
- Convert `.theme.json`, `.jar`, `.zip` and Alacritty TOML; process every archive `themeProvider`. No YAML, executable
  plugin loading, Marketplace browsing, runtime network or network dependency of conversion/drift tests.
- Export standalone documents without `extends`; retain author, ordered UI data, icon data, syntax flags and provenance.
  Preserve explicit colors. Only Alacritty's derived accent must be moved to at least 3:1 against background.
- Keep archive caps: 64 MiB archive, 4 MiB entry, 32 MiB nested JAR, 16 nested JARs, 10,000 entries, 64 providers,
  256 MiB total inflated; retain SafeXml's protections. New file operations are bounded and off EDT.
- Originals move to `themes/legacy/`, never deleted or overwritten. Failed conversions stay in place with persistent
  warnings. `.migrated` prevents another automatic conversion pass; interrupted work resumes from its journal.
- Preserve canonical `ui.theme.workspace`, legacy path-specific light/dark aliases and `match`. Canonical values win;
  a genuinely named `Light` is not the legacy alias. Ignore only `themes.marketplace.enabled`, with no warning.
- One global chrome LAF. The private workspace materializer remains isolated. SDK 0.12.0 and package DAG remain intact;
  SDK imports belong only in app.plugins. Do not launch GUI, benchmark or a login shell.
- Run focused red/green checks, then one serialized `./gradlew check` for each task; review before the next task.
  Fixes get focused checks, with another full gate when warranted. Commit each reviewed deliverable with
  `Co-Authored-By: Codex <noreply@openai.com>`. Update this status banner and `docs/STATUS.md` for deviations.

## Review Focus

1. Disk round trips change author-specific FlatLaf behavior, wildcard order, per-OS values or icon colors — Tasks 1–2
   test structural round trips plus installed/private-LAF parity, including explicit false values.
2. Generated templates, platform-sensitive conversion and duplicate/renamed upstream names make the catalog
   incomplete or irreproducible — Tasks 2–3 test platform-independent bytes, exact manifest coverage and provenance.
3. Malformed archives, delayed collision choices, symlinks or a failed write overwrite a user's theme — Task 4 tests
   bounded partial conversion, stale plans, original bytes and publication failures.
4. A crash between publishing output, moving input and saving config duplicates themes or loses the selection —
   Tasks 5–6 inject failure at each durable boundary and resume from the same home.
5. Native/legacy name collisions and failed inputs disappear behind the marker or store removal — Tasks 5–7 pin
   pre-upgrade ownership, saved aliases, persistent diagnostics and functioning interim selectors.

## File and interface map

Paths in task file lists are relative to `jasper-app/src/main/java/dev/jasper/app/` unless a module/root path is shown.
Test paths mirror the production package under `jasper-app/src/test/java/dev/jasper/app/`.

| Files | Responsibility |
|---|---|
| `appearance/ThemeTomlWriter.java`, `FlatLafThemeData.java` (new); `ThemeToml`, `ThemeResolver`, `ThemeDocument` | Native format/export and data-only adapter kept separate from legacy input |
| `appearance/{ThemeManager,WorkspaceValues,WorkspaceTokenKeys,ThemeCatalog,Theme,BuiltInThemes}.java` | Runtime adapter, pure inverse keys, transitional seam, then native catalog |
| `themeimport/{ThemeLoader,ThemeArchive,EditorScheme,SafeXml,IntellijConversion,AlacrittyTheme}.java` (move) | Existing legacy parsing/security, extended conversion |
| `themeimport/{AlacrittyConversion,ThemeConverter,ConversionResult,LegacyThemeReader}.java` | Pure shared conversion and temporary legacy catalog bridge |
| `themeimport/{ConvertThemes,ThemeSources}.java`; `tools/themes/{fetch-themes.py,sources.json}` | Locked input manifest and reproducible generation |
| `third_party/themes/<source>/`; app resources `themes/`, `themeimport/intellij/`; `THIRD-PARTY-THEMES.md` | Upstream evidence, native outputs and importer-only parent resources |
| `themeimport/{ThemeFiles,ThemeImporter,ThemeMigration,MigrationJournal}.java` | Validated file writes, import conflicts, resumable filesystem migration |
| `config/ThemeSelectionMigration.java`, `application/ThemeUpgrade.java` | Config-only rewrite and off-EDT upgrade coordination |
| `bootstrap/ApplicationBootstrap.java`; `application/{JasperApplication,ConfigurationController}.java` | Upgrade before first config snapshot, retained diagnostic reporting |
| `themesui/SampleTerminal.java` (move); `settingsui/{CorePages,Editors}.java`; `workspace/WindowChrome.java` | Keep interim selectors/navigation working while deleting stores |
| `jasper-app/build.gradle.kts`; package-info files; architecture tests/build rules; guides/status | Build wiring, allowed dependencies and user/contributor documentation |

New records defensively copy collections. Public app-internal methods have JavaDoc; do not expose parser internals
just to make tests compile. Use current owner-package tests and small test-only fixtures. Paths from manifests,
archives and journals are validated before use. No new single-implementation interface.

## Task 1: Lossless native serialization and a runtime-only FlatLaf adapter

**Files:** Create `appearance/ThemeTomlWriter.java`, `appearance/FlatLafThemeData.java`; modify `ThemeToml.java`,
`ThemeResolver.java`, `ThemeDocument.java`, `ThemeLoader.java`, `ThemeManager.java`, `WorkspaceValues.java`.
Tests: `appearance/ThemeTomlWriterTest.java` (new), existing `ThemeTomlTest`, `PluginThemeParityTest`,
`WorkspaceValuesTest`. Fixtures: `src/test/resources/dev/jasper/app/appearance/round-trip.toml`.

**Consumes:** Current `ThemeDocument` with `icons`, `uiComplete`, ordered `ui`, metadata and syntax.
**Produces:** `public static String ThemeTomlWriter.write(ThemeDocument document)`;
`public static ThemeDocument ThemeToml.parse(String text, String origin)`;
public `ThemeResolver.resolve(ThemeDocument)` and `resolve(ThemeDocument, Function<String,List<ThemeDocument>>)`.
The writer handles inherited documents too (needed by Plan 5); conversion callers resolve first.
`public final FlatLafThemeData` exposes public static `json(String name, boolean dark, String author, Map<String,Object> ui, Map<String,Object> icons)`
returning String and `explicitColors(Map<String,Object> ui)` returning Map<String,Color>, extracted from ThemeLoader. Its public static `Color color(Object value)` returns null for noncolors;
ThemeManager and WorkspaceValues installation paths no longer mention `ThemeLoader.Resolved`; legacy loader entry points move in Task 2.

- [x] **Step 1: Write round-trip and parity regressions.** The round-trip fixture has all five seeds, 16 ANSI colors,
48 tokens, 16 syntax roles, author/license/source/commit, explicit `dark=false` / `ui_complete=false`, alpha colors,
escaped quotes/backslashes/newlines and Unicode. Include ordered `*.background`/specific keys in both orders,
Long/Double/boolean values, per-OS maps and a nested icon `ColorPalette` with dark-suffixed keys. Add separate inherited
and empty-table fixtures; origin and source line numbers need not round-trip. Parse fixtures before comparing numeric
values, so Java numeric wrapper differences are not mistaken for a semantic difference.

```java
@Test void roundTripsCompleteNativePayload() throws IOException {
    String text = new String(getClass().getResourceAsStream("round-trip.toml").readAllBytes(), StandardCharsets.UTF_8);
    ThemeDocument before = ThemeToml.parse(text, "before.toml");
    String encoded = ThemeTomlWriter.write(before);
    ThemeDocument after = ThemeToml.parse(encoded, "after.toml");
    assertThat(ThemeTomlWriter.write(after)).isEqualTo(encoded);
    assertThat(after.metadata()).isEqualTo(before.metadata());
    assertThat(after.dark()).contains(false);
    assertThat(after.uiComplete()).contains(false);
    assertThat(after.seeds()).isEqualTo(before.seeds());
    assertThat(after.ansi()).isEqualTo(before.ansi());
    assertThat(after.tokens()).isEqualTo(before.tokens());
    assertThat(after.syntax()).isEqualTo(before.syntax());
    assertThat(after.ui()).isEqualTo(before.ui());
    assertThat(new ArrayList<>(after.ui().keySet())).isEqualTo(new ArrayList<>(before.ui().keySet()));
    assertThat(after.icons()).isEqualTo(before.icons());
}
```

Parity tests install original and reloaded complete themes on EDT using existing fixtures, compare the asserted
FlatLaf defaults/icon palettes, and prove private workspace materialization does not change global UIManager state.
Reject unsafe UI delegates/class strings, unsupported icon values and excess nesting with file/line diagnostics.

- [x] **Step 2: Run RED.**

```bash
./gradlew :jasper-app:test --tests '*ThemeTomlWriterTest' --tests '*ThemeTomlTest' --tests '*PluginThemeParityTest' --tests '*WorkspaceValuesTest'
```

Expected: missing writer/public API or `[icons]` rejected; record the actual failure, not an unrelated build error.

- [x] **Step 3: Implement the format and adapter.** Emit fixed section order: metadata/flags/parent, colors, normal,
bright, tokens, syntax, UI, icons; use enum/ANSI order and preserve UI insertion order. Quote arbitrary keys, canonicalize
colors to lowercase `#rrggbb[aa]`, preserve false/empty values and optional absence, end with one newline. Reject NaN,
infinity and unsupported objects instead of silently dropping them. `[ui]` retains current scalar/per-OS semantics;
`[icons]` accepts recursive string-keyed maps, lists and JSON scalars only (no null, classes or executable references),
with maximum depth 32 and 100,000 values. Apply the existing no-foreign-class rule recursively. Keep icon map hierarchy.
Deep-copy accepted nested collections in ThemeDocument so an ImportPlan cannot change after validation.

Keep the existing 256 KiB native file limit for now: Task 3 measures complete outputs; any required bounded increase
must be explicit and covered by catalog/import size tests. Never publish a file the catalog will reject. Add the
reviewed `[icons]` amendment to spec §2 and update ThemeDocument JavaDoc. Extract JSON emission/explicit colors into
FlatLafThemeData; ThemeLoader delegates to it until Task 2 moves. Change ThemeManager's private install path to accept
native generated data/JSON/colors directly, preserving existing author-specific behavior and APP_DEFAULTS.

- [x] **Step 4: Run GREEN and the full gate.** Repeat Step 2, then `./gradlew check`; expect exit 0 and parity assertions
unchanged. Independent review focuses on ordering, typed data, class filtering and global LAF isolation.
- [x] **Step 5: Commit the reviewed task.**

```bash
git add jasper-app/src docs/superpowers/specs/2026-10-01-jasper-theme-system-design.md
git commit -m "feat(themes): serialize native themes without losing UI data" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

## Task 2: Extract and complete the shared converter without breaking either store

**Files:** Move `appearance/{ThemeLoader,ThemeArchive,EditorScheme,SafeXml,IntellijConversion}.java` and
`terminalthemes/AlacrittyTheme.java` plus their owner tests to `themeimport/`; create `themeimport/AlacrittyConversion.java`,
`ThemeConverter.java`, `ConversionResult.java`, `LegacyThemeReader.java`, `package-info.java`.
Modify `appearance/{WorkspaceTokenKeys,ThemeCatalog,Theme,BuiltInThemes,ThemeManager}.java`, transitional
`terminalthemes/{AlacrittyConversion,TerminalThemeCatalog}.java`, old store callers and application composition.
Tests: `themeimport/ThemeConverterTest.java`, `LegacyThemeReaderTest.java`, existing archive/scheme/parser/parity tests;
small golden inputs/outputs under `src/test/resources/dev/jasper/app/themeimport/`.

**Consumes:** Task 1 parser/writer/resolver and FlatLafThemeData.
**Produces:**

```java
public record ConversionResult(ThemeDocument document, List<String> warnings, String sourceEntry) {}
// Nested in ThemeConverter:
public record Failure(String sourceEntry, String message) {}
public record Batch(List<ConversionResult> results, List<Failure> failures) {}
public static Batch convertAll(Path source);
public static List<ConversionResult> convert(Path source); // throws IllegalArgumentException if any failure
// In WorkspaceTokenKeys (made public):
public static Map<Token, Color> explicitTokens(Map<String, Object> resolvedUi);
// Temporary bridge, removed in Task 7:
public static ThemeCatalog.Snapshot LegacyThemeReader.read(Path source);
public static ThemeCatalog.Inspection LegacyThemeReader.inspect(Path source);
public static ThemeCatalog ThemeCatalog.beside(Path configFile,
    Function<Path, ThemeCatalog.Snapshot> legacyReader);
```

The callback reads one archive, preserving catalog file order, caching and `Problem` diagnostics; a supplier of a
flat theme list would lose both ownership and problems. `beside(Path)`/`builtIn()` become native-only defaults;
application and existing store tests explicitly inject LegacyThemeReader until Task 7. Move `ThemeCatalog.inspect`
callers to LegacyThemeReader. Make `Theme.of(String, Theme.Source)` public for this app-native boundary.

- [x] **Step 1: Write exact conversion goldens and bridge tests.** Fixture `child.theme.json` extends a local parent
with named colors, ordered wildcards, per-OS UI, icons and a relative XML scheme. Scheme parent supplies normal ANSI,
child overrides bright blue `#010203`, keyword `#aabbcc` with FONT_TYPE=3. Expected assertions:

```java
@Test void flattensParentsAndSyntaxWithoutLosingFlags() throws Exception {
    Path source = Path.of(getClass().getResource("golden/child.theme.json").toURI());
    ConversionResult result = ThemeConverter.convert(source).getFirst();
    ThemeDocument saved = ThemeToml.parse(ThemeTomlWriter.write(result.document()), "converted.toml");
    assertThat(saved.parent()).isEmpty();
    assertThat(saved.ansi().get(12)).isEqualTo(new Color(0x010203));
    assertThat(saved.syntax().get(SyntaxRole.KEYWORD)).isEqualTo(new SyntaxStyle(new Color(0xaabbcc), true, true));
    assertThat(saved.metadata().get("author")).isEqualTo("Fixture Author");
    assertThat(saved.ui()).containsEntry("Button.arc", "7");
    assertThat(saved.icons()).containsKey("ColorPalette");
}
```

FlatLaf's current JSON parser represents scalar numbers/booleans as strings; retain those resolved values rather
than guessing new types. Native TOML typed scalars are covered in Task 1. Store complete expected TOML alongside
each fixture; compare bytes. Additional named tests and required assertions:

| Test | Input → assertion |
|---|---|
| `alacrittyCopiesPaletteAndDerivesAccent` | Existing parser forms `#rrggbb`/`0xrrggbb`, cursor/selection, dim → exact 16 ANSI colors, no dim data, accent contrast ≥3 |
| `reportsEveryConsoleFallback` | No scheme / no console / partial console → named Jasper Light/Dark fallback and explicit missing-key warnings |
| `preservesExplicitIntellijAccent` | Authored low-contrast accent → unchanged; four spec accent candidates → exact stated priority |
| `inverseUsesSuppliedKeysOnly` | Button.startBackground, wildcard then specific key, absent tokens → expected explicit subset, absent stays absent |
| `conversionIsPlatformIndependent` | Per-OS UI entries → same serialized bytes for simulated OS selectors; map retained, no current-OS-only token frozen |
| `allProvidersAndFailuresSurvive` | Two valid + one invalid provider → 2 results, 1 contextual failure; strict convert throws |
| `rejectsInvalidParentsAndBoundedSidecars` | Parent/color/scheme cycles, missing parent, escaped sidecar, external entity, oversized file → contextual failure, no external read |
| `legacyBridgeKeepsPrecedenceAndProblems` | Native/archive files ordered both ways, bad provider and unchanged rescan → same winning source, warnings, retained theme identity |

- [x] **Step 2: Run RED.** `./gradlew :jasper-app:test --tests '*ThemeConverterTest' --tests '*LegacyThemeReaderTest'`.
Expect missing conversion API or missing syntax/inverse token behavior.
- [x] **Step 3: Implement extraction and conversion.** Move parser/resource ownership without creating an appearance→
themeimport edge. Split AlacrittyConversion's pure `convert(String name, Palette palette, String origin)` returning a
ThemeDocument from the transitional TerminalTheme wrapper; the wrapper stays in terminalthemes and delegates.
Move AlacrittyTheme's bundled-resource listing out to TerminalThemeCatalog; the importer parser has no legacy-store
resource dependency. Put IntelliJ parent-name/resource mapping in themeimport, including classic, New UI, Islands
and High Contrast; update resource base to `dev/jasper/app/themeimport/intellij/`.

Produce native IntelliJ Light/Darcula resources using the converter and switch their constants to BuiltInThemes.toml
before removing their runtime loader calls. This avoids an import/runtime cycle and static initialization recursion.
Do not let conversion depend on `Theme.INTELLIJ_LIGHT` or DARCULA to obtain fallbacks: use Jasper palettes/documents.
Retain all archive security tests after moving them; migration/import must never load jar classes.

Extend EditorScheme to expose resolved foreground/font-type fields (including inherited `baseAttributes`) to the
converter. Missing syntax stays absent for generation; foreground is not the effect/underline color. FONT_TYPE bits
1 and 2 are bold/italic. Use these deterministic priorities:

| Role | First present foreground attribute |
|---|---|
| keyword, type | DEFAULT_KEYWORD; DEFAULT_CLASS_NAME respectively |
| function | DEFAULT_FUNCTION_DECLARATION, DEFAULT_FUNCTION_CALL |
| variable, constant, number, string | DEFAULT_IDENTIFIER; DEFAULT_CONSTANT; DEFAULT_NUMBER; DEFAULT_STRING respectively |
| comment, doc_comment | DEFAULT_LINE_COMMENT then DEFAULT_BLOCK_COMMENT; DEFAULT_DOC_COMMENT |
| annotation, operator | DEFAULT_METADATA; DEFAULT_OPERATION_SIGN |
| punctuation | DEFAULT_COMMA, DEFAULT_SEMICOLON |
| tag, attribute | HTML_TAG then XML_TAG; HTML_ATTRIBUTE_NAME then XML_ATTRIBUTE_NAME |
| regex | REGEXP_CHAR_CLASS, REGEXP_CHARACTER, remaining REGEXP_* in lexical order |
| error | ERRORS_ATTRIBUTES |

Console uses inherited CONSOLE_BACKGROUND_KEY/CONSOLE_NORMAL_OUTPUT/CONSOLE_*_OUTPUT. Accent order is exactly
ColorPalette.accent, Component.focusColor, EditorTabs.underlineColor, Button.default.startBackground, then ANSI blue.
Keep remaining UI keys too; `ui_complete=true` and author survive. Derive inverse token candidates from UiTemplate
and WorkspaceTokenKeys together: canonical WorkspaceTokenKeys key first, then matching UiTemplate keys in declaration
order (specific keys before wildcard/base-variable fallbacks). Honor wildcard ordering. Do not invoke EDT-only
WorkspaceValues during conversion. Per-OS data stays in UI; only OS-independent values become explicit tokens (omit a token when its source varies by OS),
so runtime workspace materialization can still select the current OS without an explicit token overriding it.
For required seeds/accent, use scalar then `os.default` then the documented palette fallback, never host OS. Test this
against the actual `os.default` rule; do not use ThemeLoader.Resolved.colors (host-selected) in the offline exporter.

Standalone JSON may read bounded sibling theme/scheme files inside its source directory, never arbitrary absolute
paths or `..` escapes. Archive references resolve inside the same archive under existing limits. Resolve bundled
parents as fallbacks. Native TOML import is additive convenience: classify it separately, resolve only against its
source directory plus bundled themes, and export standalone; do not misclassify malformed native input as Alacritty.
`sourceEntry` is a stable relative provider path (including nested-jar path), not a display name or absolute path.

- [x] **Step 4: Run GREEN.** Repeat Step 2, moved archive/parser/scheme tests, existing store/catalog/FlatLaf parity tests,
then `./gradlew check`. Verify both old stores still work headlessly and no appearance→themeimport edge exists.
- [x] **Step 5: Commit after independent review.**

```bash
git add jasper-app/src gradle/application-architecture.gradle.kts
git commit -m "feat(themes): share complete legacy theme conversion" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

## Task 3: Lock upstream sources and generate the complete offline collection

**Files:** Create `tools/themes/fetch-themes.py`, `sources.json`, `third_party/themes/<source>/`,
`themeimport/{ThemeSources,ConvertThemes}.java`, `themeimport/BundledThemeConversionTest.java` (test).
Modify `jasper-app/build.gradle.kts`, generated `src/main/resources/dev/jasper/app/themes/{index.txt,*.toml}`,
`THIRD-PARTY-THEMES.md`; replace `tools/themes/fetch-intellij-themes.py` after parity. Keep importer parent resources.

**Consumes:** ThemeConverter, ThemeTomlWriter; existing source bytes and provenance.
**Produces:** `public static void ConvertThemes.main(String[] args)` accepting
`--manifest <sources.json> --sources <third_party/themes> --output <directory> --notices <file>`;
`:jasper-app:convertThemes` supplies repository defaults. `ThemeSources.read(Path manifest)` validates a versioned
manifest shared in shape with Python. Each source has repository, full commit, license path, asset path/hash list,
optional release artifact URL/hash, provider paths, canonical output IDs/names, excluded variants/reasons and aliases.
Manifest entries, not filesystem traversal or network discovery, determine the release catalog.

- [x] **Step 1: Write coverage, drift, uniqueness and provenance tests.** Tests regenerate into `@TempDir`, compare
all output bytes plus index/notices against checked-in files, reject extra/missing output files, require complete
provider-to-variant accounting, unique normalized names and IDs, and reparse every generated theme through the catalog.
Use small malformed manifests to test duplicate IDs, path escape, bad asset hash and unrecorded variant. Assert:

```java
assertThat(generatedNames).doesNotHaveDuplicates(); // strip + lowercase(Locale.ROOT)
assertThat(generatedPaths).containsExactlyElementsOf(indexPaths);
assertThat(actualTomlBytes).isEqualTo(expectedTomlBytes);
assertThat(document.parent()).isEmpty();
assertThat(document.metadata()).containsKeys("author", "license", "source", "commit");
assertThat(document.metadata().get("commit")).matches("[0-9a-f]{40}");
assertThat(Files.isRegularFile(licenseFile)).isTrue();
```

`generatedNames` comes from all manifest outputs including Jasper's two reserved names; metadata checks exclude
Jasper's own themes. Verify SHA-256 of every listed upstream asset/license/notice and the Catppuccin release artifact.
- [x] **Step 2: Run RED.** `./gradlew :jasper-app:test --tests '*BundledThemeConversionTest'`; expect missing manifest,
generator or ungenerated variants, not a network lookup.
- [x] **Step 3: Freeze inputs, then implement generation.** Acquire using the explicit maintainer script only. Existing
local SOURCE.md provides both complete pins below; do not re-resolve the Alacritty short SHA to a newer revision.

| Source ID / upstream | Required variants and acquisition decision |
|---|---|
| intellij / JetBrains/intellij-community | Existing `f7377708b654b73b206da40bb382ecb4d44e8f12`: IntelliJ Light, Darcula; retain all importer parents/schemes |
| alacritty / alacritty/alacritty-theme | Existing `ab88d5a80d676b5dc6157e91aba8067f2078dc94`: 177 inputs, minus normalized names already supplied by IntelliJ-family conversions |
| one-dark / one-dark/jetbrains-one-dark-theme | One Dark, One Dark Vivid |
| dracula / dracula/jetbrains | Dracula, Alucard, Dracula Colorful |
| solarized / snowe2010/solarized-jetbrains | Solarized Dark, Solarized Light |
| catppuccin / catppuccin/jetbrains | Catppuccin Latte, Frappé, Macchiato, Mocha; pinned release JAR plus corresponding full source commit (repository contains templates) |
| material-lite / AtomMaterialUI/material-theme-ui-lite | All variants except Monokai Pro; freeze explicit renames for variants named after other projects |
| vscode / dinbtechit/vscode-theme | Code Dark, Code Dark Modern, Code Light |
| nord / nordtheme/jetbrains | Nord |
| gruvbox / lonre/gruvbox-intellij-theme | Gruvbox Dark/Light × Hard/Medium/Soft (six) |
| one-dark-modern / cweijan/idea-one-dark-modern-theme | One Dark Modern |
| xcode / WhiteVermouth/XcodeTheme | Studio Dark, Studio Light; checked-in handcrafted terminal `extends` overrides, flattened for output |

For unpinned sources, inspect authoritative upstream artifacts, freeze actual full commits/hashes, and record precise
LICENSE/NOTICE evidence. Preserve the spec's excluded projects. Do not silently replace a missing variant or weaken
license scope: report the concrete source mismatch if it prevents fulfilling the approved set. No invented pins or
claims that the design table alone verifies current assets. Store exact bytes, `SOURCE.md`, `assets.tsv` (upstream
path + SHA-256) and licenses/notices per source. Renames/exclusions and old-name aliases must be machine-readable and
also explained in SOURCE.md. Retain original author text; FlatLaf uses it for behavior.

Use stable `<source>-<variant>` IDs with an explicit collision-checked manifest; keep existing four built-in IDs.
Reserve Jasper Light/Dark. IntelliJ-family canonical names win over equivalent Alacritty names, with every exclusion
recorded. Preserve the old Alacritty display-name mapping for migration aliases. Missing author in upstream metadata
uses credited upstream attribution from its source evidence, recorded as such. Studio overrides live in
`third_party/themes/xcode/overrides/`; label locally authored files separately from upstream assets.

JavaExec depends on compileJava and dependency jars, not processResources or a runtimeClasspath task cycle. Supply
importer parent resources explicitly on its classpath. It writes generated outputs only after validating the whole
set; refuses to erase unknown files in the output directory. Drift tests never modify repository files. Include
licenses/notices for generated bundles and importer parents in the packaged distribution. If a valid full theme is
larger than 256 KiB, increase the shared native limit to 4 MiB (the existing entry cap), test both limits and document
measured maximum/decision; do not trim theme data to meet a size cap.

- [x] **Step 4: Run GREEN and reproducibility gates.**

```bash
./gradlew :jasper-app:convertThemes
./gradlew :jasper-app:convertThemes
./gradlew :jasper-app:test --tests '*BundledThemeConversionTest'
./gradlew --offline check
```

Record output hashes after the first generation; the second must match exactly. Offline Gradle assumes dependencies
already cached from preceding tasks. Verify a dry-run task graph has no cycle and package resources include notices.
Record actual source/variant/output counts, all renamed/excluded names and any deviation; independent review checks
the manifest against spec §6.2 rather than accepting only a total count.
- [x] **Step 5: Commit the reviewed catalog and tooling.**

```bash
git add tools/themes third_party/themes jasper-app/build.gradle.kts jasper-app/src THIRD-PARTY-THEMES.md
git commit -m "feat(themes): vendor the reproducible native theme collection" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

## Task 4: Safe file publication and import conflict reports

**Files:** Create `themeimport/{ThemeFiles,ThemeImporter}.java` and tests `ThemeFilesTest.java`, `ThemeImporterTest.java`;
reuse ThemeConverter fixtures/archive builders. No UI in this task.

**Consumes:** ConversionResult/Batch and native parser/resolver/writer; catalog snapshot for user-name ownership.
**Produces:**

```java
public ThemeFiles(Path home);
public Path create(ThemeDocument document) throws IOException; // no overwrite, returns actual path
public void replace(Path target, String expectedSha256, ThemeDocument document) throws IOException;
public String fingerprint(Path target) throws IOException;
public ThemeImporter(Path home);
public ImportPlan plan(Path source); // worker thread
public ImportReport commit(ImportPlan plan, Map<String, ConflictChoice> choices);
// Nested in ThemeImporter:
public enum ConflictChoice { REPLACE, KEEP_BOTH, SKIP }
public record Item(String id, ConversionResult conversion, Optional<Path> target,
                   Optional<String> expectedSha256, boolean shadowsBundled) {}
public record ImportPlan(List<Item> items, List<ThemeConverter.Failure> failures) {}
public record Imported(String id, String name, Path path) {}
public record ImportReport(List<Imported> imported, List<String> warnings,
                           List<ThemeConverter.Failure> failures) {}
```

Item IDs derive from source/provider identity, never list position/display name. An absent choice on a user conflict
means SKIP; conflict-free items import by default. Built-in name overlap is reported as a user override, without
writing into resources. All native documents in the frozen plan are immutable. The source is not reread at commit.

- [x] **Step 1: Add filesystem tests with actual temporary files.** Test names/assertions:

| Test | Required result |
|---|---|
| `keepBothPreservesOriginal` | Existing Example and Example 2 → new Example 3; both original byte arrays unchanged |
| `replaceRequiresUnchangedTarget` | Edit target after plan → failure, edited bytes intact, no success reported |
| `newConflictAfterPlanIsNotOverwritten` | Another file acquires that name → fail/replan, no silent overwrite or duplicate-name success |
| `reportsPartialArchiveByProvider` | Two valid/one bad → two imported entries, one contextual failure, fallback warnings preserved |
| `sameNameProvidersHaveDistinctChoices` | Same display name from two paths → independent stable IDs, deterministic suffixes |
| `rejectsUnsafeTargets` | Symlink theme directory/target, traversal, nonregular target, escape/case aliases → no writes outside home |
| `failedPublicationPreservesPreviousBytes` | Inject failed temp write/move → original preserved, only owned temp removed |
| `validatesWholeSelectionBeforeWriting` | Later invalid selected document → no output committed during validation |

```java
@Test void refusesStaleReplacement() throws IOException {
    ThemeFiles files = new ThemeFiles(home);
    ThemeDocument original = Theme.DARK.source().document();
    Path target = files.create(original);
    String expected = files.fingerprint(target);
    Files.writeString(target, "# user edit\n" + Files.readString(target));
    String edited = Files.readString(target);
    assertThatThrownBy(() -> files.replace(target, expected, original)).isInstanceOf(IOException.class);
    assertThat(Files.readString(target)).isEqualTo(edited);
}
```

Test safe ASCII filenames for Unicode/display names, trailing dots/spaces, reserved device names, case-insensitive
filesystems and filename occupancy independently of theme-name occupancy. Keep archive cap/bomb/path tests active.
- [x] **Step 2: Run RED.** `./gradlew :jasper-app:test --tests '*ThemeFilesTest' --tests '*ThemeImporterTest'`.
Expected: missing publication/import APIs.
- [x] **Step 3: Implement validation and publication.** Reserve names using catalog normalization; suffix ` 2`, ` 3`, …
using the full selected batch, not each item independently. Sanitize filesystem IDs independently of display names.
Create validates/reparses output under the catalog byte cap before touching its target. Serialize operations per home;
use a cooperating process file lock as well as an in-process lock. Journaled migration in Task 5 uses the same lock.
Require canonical direct-child regular user files, NOFOLLOW checks and expected hashes for replacement. Recheck target
identity/content immediately before publish. Use a fully written sibling temporary and atomic replacement; if the
filesystem cannot supply required atomic replacement, fail and preserve the old target. For creation use an atomic
no-replace primitive (hard-link completed sibling temp to final path, then unlink temp); if unsupported, fail safely.
Do not assume `ATOMIC_MOVE` without REPLACE_EXISTING provides portable no-clobber behavior.

The lock coordinates Jasper writers; Java's filesystem API cannot provide compare-and-swap against an arbitrary
external editor racing the final rename. Document that limit accurately and minimize the interval; tests cover
observed changes before publication, not an impossible cross-process CAS guarantee. Do not claim batch atomicity:
validation is all-or-nothing, publication can partly succeed on I/O failure, and the report lists exactly committed
paths/names. No input file is moved by interactive import. Package-private fault hooks use existing functional types
and are inactive in production. Plan 5 will compose these operations with dialogs and cancellation.
- [x] **Step 4: Run GREEN.** Repeat Step 2 and `./gradlew check`; expect safe conflict behavior and no orphan temps after
handled failures. Independently review all filesystem paths, current-name revalidation and partial reports.
- [x] **Step 5: Commit after review.**

```bash
git add jasper-app/src/main/java/dev/jasper/app/themeimport jasper-app/src/test/java/dev/jasper/app/themeimport
git commit -m "feat(themes): import native themes with safe conflict handling" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

**Task 5 complete:** bounded journal, no-clobber migration, source ownership and interruption recovery verified. All converted bytes are staged before any original moves so local-parent dependencies survive resume. Legacy Alacritty aliases are generated offline alongside the index. Per-provider serialization errors retain originals while unrelated inputs proceed.

## Task 5: Resumable filesystem migration with preserved source ownership

**Files:** Create `themeimport/{ThemeMigration,MigrationJournal}.java`, tests `ThemeMigrationTest.java`,
`MigrationJournalTest.java`; extend ThemeFiles' package-private publication hooks. No startup invocation yet.

**Consumes:** Task 4 file operations, Task 2 legacy reader, Task 3 aliases and native catalog.
**Produces:** `public ThemeMigration(Path home)`; `public MigrationReport run()`; `public void complete(MigrationReport report) throws IOException`;
`public List<ThemeCatalog.Problem> problems()` (diagnostic-only refresh; never converts).
Nested `MigrationReport` contains `String generation`, `boolean completed`, `Map<String,String> chromeRenames`,
`Map<String,String> workspaceRenames`, `List<Path> outputs`, `List<Path> originals`, `List<ThemeCatalog.Problem> problems`.
Keys in rename maps are normalized old names and values are actual new names. `run` writes recovery state, never
`.migrated`; `complete` verifies the journal generation before marking the pass complete, after Task 6 saves config.

- [x] **Step 1: Write interruption/ownership regressions.** Temporary home contains multi-provider archive, user native
file, two Alacritty inputs, an invalid archive and occupied legacy destinations. Test every durable boundary with a
package-private `Consumer<String>` hook (labels: `intent`, `output`, `original`, `before-complete`, `complete`). Throw
an injected exception only after the corresponding durable write; construct a fresh migration object to resume.

```java
@Test void completedPassKeepsFailedOriginalAndDiagnostics() throws IOException {
    Files.createDirectories(home.resolve("themes"));
    Path bad = home.resolve("themes/bad.jar");
    byte[] bytes = { 1, 2, 3 };
    Files.write(bad, bytes);
    ThemeMigration migration = new ThemeMigration(home);
    ThemeMigration.MigrationReport first = migration.run();
    migration.complete(first);
    ThemeMigration.MigrationReport second = new ThemeMigration(home).run();
    assertThat(Files.readAllBytes(bad)).isEqualTo(bytes);
    assertThat(second.outputs()).isEmpty();
    assertThat(second.problems()).anyMatch(problem -> problem.file().equals(bad));
    assertThat(home.resolve("themes/legacy/.migrated")).exists();
}
```

Pin additional cases: valid multi-provider input moves only after all providers succeed; partial input stays while
its successful outputs are recorded; retry after each injected crash creates no duplicate outputs; existing/mutated
outputs are never overwritten; move failure leaves original; same basenames in both source folders never overwrite;
invalid/corrupt/traversing journal fails closed with originals intact. A corrupt marker cannot bypass journal validation.

Precedence tests interleave native/archive filenames both ways and user Alacritty/bundled/native names. Assert rename
maps describe the source that previously won each slot, not merely the first converter result. Test equal-byte legacy
destination occupancy, changed source hashes, missing sources and diagnostics after marker.
- [x] **Step 2: Run RED.** `./gradlew :jasper-app:test --tests '*ThemeMigrationTest' --tests '*MigrationJournalTest'`.
Expected: missing migration/journal or resume semantics.
- [x] **Step 3: Implement the bounded journal and state machine.** Scan direct regular non-hidden files under themes
(`.jar`, `.zip`, `.theme.json`) and terminal-themes (`.toml`), skipping symlinks and legacy/. Do not rewrite native TOMLs.
Capture pre-upgrade ownership using the baseline catalog file order and Plan 2 workspace precedence before publishing
anything. Existing native files remain authoritative where they already won; use KEEP_BOTH for migrated collisions,
and map each previously winning legacy selection to its actual new name. Built-in rename aliases come from Task 3.

Write versioned `themes/legacy/migration.toml` with bounded input (4 MiB), source relative path + SHA-256,
provider ID, chosen output path/name/hash, chosen legacy destination and progress. Serialize intent before publication,
then record each published output and moved original. Validate paths, hashes and generation on every resume. A crash
before a progress update recognizes only outputs matching the pre-recorded hash; edited output becomes a conflict
requiring attention, never a reason to overwrite or invent another successful theme. Changed source hash similarly
blocks that source's automatic resume with a warning; keep both input and prior outputs.

Stage/validate all outputs for a source before publishing. A partial archive may retain valid outputs but its original
stays in place and its failures are persisted; a completed pass does not reconvert it automatically. Move a fully
converted original to a uniquely reserved legacy path without replace. Never delete an original merely because a
legacy destination has equal bytes. Keep separate origin-folder identity and deterministic suffixes in the journal.
Clean only owned abandoned temporary files; do not recursively delete terminal-themes (remove it only if empty).

`complete` writes the marker only after durable journal state and successful config step. On a marked rerun, return
saved diagnostics and no new conversion/output activity; suppress resolved diagnostics for removed inputs, and report
changed failed input as changed with manual Import as the retry route. Persist changes to that diagnostic state.
Retain rename maps until config completion; a config failure can retry without reconverting or losing moved sources.
Use the same per-home lock as interactive import. Recoverable per-source errors do not abort unrelated sources.
- [x] **Step 4: Run GREEN.** Repeat Step 2 plus Task 4 tests and `./gradlew check`; review the state machine using each
crash window, including publication succeeded but the following journal write failed.
- [x] **Step 5: Commit after review.**

```bash
git add jasper-app/src/main/java/dev/jasper/app/themeimport jasper-app/src/test/java/dev/jasper/app/themeimport
git commit -m "feat(themes): migrate legacy files with resumable recovery" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

**Task 6 complete:** pre-service upgrade and journaled config intent preserve aliases, comments and backups; startup warnings remain distinct from transient catalog problems. Crash recovery uses ConfigLoader canonicalization before reconstructing rename maps (unknown/multiple legacy aliases cannot change precedence). Full check passed 7,982 tests / zero failures/errors / six skips.

## Task 6: Integrate upgrade before startup configuration and retain diagnostics

**Files:** Create `config/ThemeSelectionMigration.java`, `application/ThemeUpgrade.java`; modify
`bootstrap/ApplicationBootstrap.java`, `application/{JasperApplication,ConfigurationController}.java`,
`config/ConfigLoader.java`; tests `config/ThemeSelectionMigrationTest.java`, `application/ThemeUpgradeTest.java`,
`bootstrap/ApplicationBootstrapTest.java`, existing config migration tests. Update affected package-info allowlists.

**Consumes:** Task 5 migration report/two-phase completion; existing ConfigLoader, SettingWriter, TomlDocument,
ConfigMigration.backUp and persistence.TomlStateFile. themeimport does not depend on config or application.
**Produces:**

```java
// In ThemeSelectionMigration; maps contain only present physical theme paths and their string values:
public record Edit(Path target, String expectedSha256, String desiredSha256, String text,
                   Map<String,String> before, Map<String,String> after) {}
public static Optional<Edit> plan(Path config, boolean macOs, Map<String,String> chromeRenames,
                                  Map<String,String> workspaceRenames) throws IOException;
public static void apply(Path config, boolean macOs, Edit edit) throws IOException;
// Added to ThemeMigration (nested record, config-independent values):
public record ConfigIntent(String configFileName, String expectedSha256, String desiredSha256,
                           Map<String,String> before, Map<String,String> after) {}
public Optional<ConfigIntent> configIntent();
public void recordConfigIntent(String generation, ConfigIntent intent) throws IOException;
// In ThemeUpgrade:
public static List<ThemeCatalog.Problem> prepare(Path config, boolean macOs);
public static List<ThemeCatalog.Problem> diagnostics(Path config);
```

`plan` returns empty for a missing/unchanged config; `apply` checks expected bytes/location and backs up before writing.
Physical theme paths are `ui.theme.name`, `ui.theme.workspace`, `ui.theme.variant`, `ui.theme.terminal`, `terminal.colors`.
Journal records the direct-child config filename, hashes and theme values only; validate that filename before use.
Do not apply a pending intent to a different config override in the same directory; preserve it and report a conflict. `recordConfigIntent` verifies the still-pending migration generation under the shared home lock.
Add an ApplicationBootstrap package-private `Startup(ConfigService service, AppArguments options,
List<ThemeCatalog.Problem> themeProblems)` record and `start(String[] args, PrintStream out, PrintStream error,
Consumer<Startup> launch)` overload. Production uses the one-argument callback; preserve the existing BiConsumer
entry for tests by delegating to it. Carry the problems through to the exact application overload:
`JasperApplication(ConfigService service, ShellLauncher launcher, CommandHistory history, Path buddyStateFile,
Runnable terminate, Path shellIntegrationDir, List<ThemeCatalog.Problem> startupThemeProblems)`.
Old test/benchmark constructors delegate with an empty list. ConfigurationController holds these problems separately
from transient per-scan problems.

- [x] **Step 1: Write config/startup tests.** Start with comments, unrelated plugin tables, aliases and pre-existing
backups; verify exact preserved text outside changed keys. Parameterize canonical versus alias paths and names
`light`, `dark`, `Light`, `match`, unknown requested names. Canonical values always win; only aliases transform.
If a selection's legacy source lost a name collision, assert the selected converted source retains its palette and
actual suffixed name. Failed sources retain their requested name and warning, not a persisted fallback.

```java
@Test void rewritesOnlyTheSelectedMigratedName() throws IOException {
    Path config = home.resolve("config.toml");
    Files.writeString(config, "# keep\n[ui.theme]\nname = \"Example\"\nworkspace = \"match\"\n");
    ThemeSelectionMigration.Edit edit = ThemeSelectionMigration.plan(config, false, Map.of("example", "Example 2"), Map.of()).orElseThrow();
    ThemeSelectionMigration.apply(config, false, edit);
    String text = Files.readString(config);
    assertThat(text).contains("# keep", "name = \"Example 2\"", "workspace = \"match\"");
    assertThat(ThemeSelectionMigration.plan(config, false, Map.of("example", "Example 2"), Map.of())).isEmpty();
    assertThat(Files.readString(config)).isEqualTo(text);
}
```

Test a rename chain (old A→A 2, old A 2→A 3): interruption after config write must not apply A→A 2 twice or turn it
into A 3. Test config changed while upgrade runs, rejected config, failed backup/write, pre-existing marker, and no
config file (do not create one), and a different config override sharing the same home. Bootstrap's injected launch callback asserts migrated files/config are already visible
in `service.initialState()` before application/theme/plugin construction. The help and successful resident-handoff
paths perform no migration. No test constructs a native window or starts a shell.
- [x] **Step 2: Run RED.**

```bash
./gradlew :jasper-app:test --tests '*ThemeSelectionMigrationTest' --tests '*ThemeUpgradeTest' --tests '*ApplicationBootstrapTest' --tests '*ConfigMigrationTest'
```

Expected: missing upgrade ordering/config rewrite behavior.
- [x] **Step 3: Implement pre-service upgrade and exact config recovery.** In ApplicationBootstrap.start, after
`handsOff` and resolution of the actual config override, call ThemeUpgrade.prepare before `new ConfigService`.
Migration home is the absolute config file's parent, matching ThemeCatalog.beside, not blindly AppDirs.home. This runs
on the startup worker/caller before EDT composition. Thread the returned problems into application creation and
configuration display; do not hold the EDT waiting for file conversion or a config future.

Config helper parses through current ConfigLoader, writes only theme selections/aliases with SettingWriter, retains
comments/unrelated tables, backs up before the first changed write and uses bounded TomlStateFile atomic writes.
Refuse rejected config and preserve its bytes; let startup continue with warnings and leave completion pending.
Canonicalize old theme paths using the existing ConfigMigration transformation rules, without automatically migrating
all unrelated config. Recheck bytes and canonical target before replacement, as ConfigService.transact does.

To make the post-write/pre-marker crash safe, extend the migration journal with a config edit intent: original and
planned config hashes plus exact original/desired theme-key values. Save intent before config write; on resume recognize
the planned values/hash as already applied. If a concurrent edit changes theme keys, do not replay old name maps over
it; keep the file, report a recoverable conflict and leave completion pending. Unrelated edits can be preserved by
replanning only while original theme-key values still match. A missing config requires no edit. The application
coordinator joins these steps without introducing config→themeimport or themeimport→config dependencies; use
the exact ConfigIntent/recordConfigIntent contract above for this handshake. On retry compare current physical theme
values with intent.after before computing any rename, preventing chained names from being migrated twice. Pin this
with the A→A 2→A 3 test; hash disagreement alone due to unrelated edits must not replay a completed theme edit.

Ignore the exact legacy Marketplace key in ConfigLoader.special once Task 7 removes its schema field; prepare tests
here but remove the active field and enable those assertions together in Task 7. Retain migration warnings across
catalog/config reloads without duplication; changed/deleted failed inputs refresh diagnostics through ThemeUpgrade.diagnostics on the existing off-EDT
configuration companion scan, then publish the replacement diagnostic list on EDT without reconversion.
If MigrationReport.completed is true, return diagnostics without replaying the saved rename maps.
Call `complete` only after the required config operation succeeded or was unnecessary. A per-source conversion failure
is a completed pass with persistent diagnostics; a config/journal infrastructure failure is retryable and unmarked.
- [x] **Step 4: Run GREEN.** Repeat Step 2, migration crash tests and `./gradlew check`; independently review startup
order and recovery after config write. Verify the original startup regression tests stay green.
- [x] **Step 5: Commit after review.**

```bash
git add jasper-app/src
git commit -m "feat(themes): upgrade selections before application startup" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

## Task 7: Native-only runtime, retire stores and finish the runnable deliverable

**Files:** Modify `appearance/{Theme,ThemeCatalog,BuiltInThemes,ThemeManager}.java`,
`application/{JasperApplication,ConfigurationController}.java`, `settingsui/{CorePages,Editors}.java`,
`workspace/WindowChrome.java`, `config/{CoreSettings,ConfigSnapshot,ConfigLoader,ConfigTemplate}.java` where referenced.
Delete `themestore/`, remaining `terminalthemes/` and obsolete resources/tests after moving reusable coverage;
move SampleTerminal to `themesui/SampleTerminal.java` with package-info.
Remove `themeimport/LegacyThemeReader.java` and temporary catalog seam. Modify packaging, docs/themes.md,
docs/app-architecture.md, docs/app-maintenance.md, jasper-app/README.md, docs/STATUS.md and assessment.
Tests: `appearance/NativeThemeCatalogTest.java`, `application/NativeThemeUpgradeTest.java`,
`settingsui/NativeThemeNavigationTest.java`, migrated existing catalog/config/menu tests.

**Consumes:** Tasks 1–6, generated index and startup migration diagnostics.
**Produces:** `Theme.Source` permits only Bundled/UserFile; ThemeManager.builtIns reads the indexed native collection;
ThemeCatalog scans only native user TOML; all current chrome/workspace choices read that catalog. Plan 5 consumes
ThemeImporter/ThemeFiles, ThemeTomlWriter and the relocated canned SampleTerminal.

- [ ] **Step 1: Write native-only integration/navigation regressions.** Populate a copied legacy home, run the headless
upgrade coordinator then build the catalog/controller through controlled tests; assert chrome/workspace choices keep
expected generated colors, invalid legacy input remains with a warning, native same-name precedence is stable, and
marker rerun performs no legacy conversion. Editing an unselected leftover archive after completion cannot change a
native theme. Catalog index tests load all entries from a packaged-resource-style classloader; never list a JAR directory.

```java
assertThat(catalog.latest().themes()).allMatch(theme ->
    theme.source() instanceof Theme.Bundled || theme.source() instanceof Theme.UserFile);
assertThat(catalog.latest().find("Studio Dark")).isPresent();
assertThat(catalog.latest().find("Catppuccin Mocha")).isPresent();
assertThat(CoreSettings.SCHEMA.find(List.of("themes", "marketplace", "enabled"))).isEmpty();
```

Test legacy Marketplace values true/false/string/table all ignored at that exact path without a diagnostic; an
unknown sibling still warns. Assert removed pages/actions are not registered, both interim pickers use native names,
workspace has `match`, and every remaining View/settings navigation target exists. Preserve Plan 2 missing-theme
fallback and startup tests, and Plan 3 workspace/plugin styling tests across an imported light/dark pair.
- [ ] **Step 2: Run RED.**

```bash
./gradlew :jasper-app:test --tests '*NativeThemeCatalogTest' --tests '*NativeThemeUpgradeTest' --tests '*NativeThemeNavigationTest'
```

Expected: legacy sources/pages/schema still present or catalog restricted to four hardcoded entries.
- [ ] **Step 3: Retire runtime legacy paths.** Read the generated index lazily without circular Theme constant
initialization; reuse the four existing Theme singleton instances by stable ID where callers rely on them. Remove
Archive/LegacyPalette source branches, legacy reader injection, terminal catalog polling and store construction.
Retain only importer JSON/archive/XML resources/readers and the in-memory FlatLaf JSON adapter. Native catalog keeps
per-file diagnostics, inheritance, duplicate precedence and unchanged-theme identity. Do not delete migration files
or failed original user inputs when removing store code.

Keep Appearance chrome and Font & Colors workspace dropdowns operational with the native catalog; keep `match` and
View's theme menu. Replace stale More Themes/More Terminal Themes navigation with a real existing Appearance or
Font & Colors target, or remove the obsolete browse affordance. No pretend Themes page before Plan 5. Move SampleTerminal
with its existing fake/canned transport; no shell. Remove Marketplace active setting/template/docs and all store page
registrations/callers; preserve exact-key ignored compatibility. Keep relevant parser/security/installation parity tests
in their new owner packages, deleting only tests whose feature was deliberately removed. Update dependencies in
package-info/architecture fixtures; no weakened verifier. Update notices and distribution resources.
- [ ] **Step 4: Run GREEN, final review and acceptance documentation.** Repeat Step 2, then one serialized
`./gradlew check`. Count XML from explicit module/plugin test roots, record tests/failures/errors/skips and all four
architecture gates. Run AGENTS.md's Java control/private-use/surrogate hygiene check and `git diff --check`.
Final independent whole-branch review covers the complete source manifest, format parity, migration/config crash
windows and the interim UI; fix and re-review findings. Record actual theme counts/pins/renames, format amendment,
size-cap decision if any, deviations and evidence in this plan/STATUS/assessment. Do not claim user visual acceptance.

User-only acceptance after implementation: launch against a copied legacy home; confirm selected chrome/workspace,
preserved originals and failed-input warning; choose Dracula, Catppuccin Mocha, Solarized Light, Gruvbox and Code Dark
with a mismatched workspace and plugin pane; confirm current selectors/menu work. Import dialogs, Duplicate/Open/Remove,
staged preview and live theme-file reload are Plan 5, not missing Plan 4 UI.
- [ ] **Step 5: Commit the reviewed deliverable.**

```bash
git add jasper-app gradle docs THIRD-PARTY-THEMES.md
git commit -m "refactor(themes): retire legacy stores after native migration" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

## Plan self-review and handoff

- Spec coverage: §2 persistence/data parity → Tasks 1–2; §6.1 converters → Task 2; §6.2 full offline collection → Task 3;
  §6.3 importer/conflicts/report → Task 4 (dialogs remain Plan 5); §6.4 migration/config/marker → Tasks 5–6;
  §6.5 removals and §8 runnable intermediate UI → Task 7; §9 golden/drift/provenance/migration tests → respective gates.
- This revision fixes the outline's lost-diagnostics supplier, appearance/import cycle, Alacritty wrapper dependency,
  incomplete source pin, OS-dependent export risk and migration being placed after the initial config snapshot.
- Task interfaces must remain the contract between implementer/reviewer contexts. If repository evidence forces a
  change, update downstream signatures/tests and the status banner before continuing; do not silently fork converters.
- No code was implemented, upstream fetched, GUI launched or Gradle test rerun for this documentation-only revision.
  Baseline evidence is Plan 3's already recorded 7,897 tests / zero failures/errors / six skips, not a Plan 4 result.
- Next: user reviews this plan; execute the seven gates using the preserved subagent workflow. Plan 5 then adds the
  Themes page, staged preview, file actions and live reload on these APIs.
