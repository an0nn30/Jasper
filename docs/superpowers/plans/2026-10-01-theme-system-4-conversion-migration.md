# Theme System Plan 4: Conversion, Vendoring and Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Planned, not executed. Requires plans 2–3. Upstream artifacts beyond today's pins have not been fetched or
reverified by this planning pass; Task 3 freezes exact inputs before generating the catalog.

**Goal:** Ship a reproducible native theme collection and a safe offline upgrade/import path, then retire both old stores.

**Architecture:** `appearance` owns native format/runtime generation. `themeimport` owns legacy parsing, conversion,
file publication and migration, depending on appearance only. Application composes the startup migration and catalog;
build-time and runtime conversion use the same Java code. Settings presentation follows in plan 5.

**Tech Stack:** JBR 25, Gradle JavaExec, tomlj, existing JSON/XML/archive readers, Python fetch tooling, headless JUnit.

**Spec:** `docs/superpowers/specs/2026-10-01-jasper-theme-system-design.md`, sections 2, 6 and 8–9.
**Companion:** [Remaining-work assessment](2026-10-01-theme-system-remaining-work.md).

## Global Constraints

- Runtime accepts native themes only after migration; no Marketplace or runtime network theme source.
- Import supports `.theme.json`, `.jar`, `.zip`, Alacritty `.toml`; every archive themeProvider is considered.
- Converted output is standalone (no `extends`); preserve name, author, UI keys, icons, syntax and provenance.
- Retain archive/XML safety limits (64 MiB archive, 4 MiB file, 32 MiB nested JAR, 16 nested JARs,
  10,000 entries, 64 providers, 256 MiB total inflated) and no plugin class loading. File I/O/conversion off EDT; UI publication on EDT.
- Preserve originals in `themes/legacy/`; failures stay in place with warnings. Never overwrite a different original.
- Canonical config aliases are from plan 2; `themes.marketplace.enabled` becomes silently ignored legacy input.
- SDK remains 0.12.0 and JDK-only; new app packages may not import it. Maintain the package DAG/allowlists.
- No GUI or benchmark. One commit per task after red/green tests, full `./gradlew check` and review; append
  `Co-Authored-By: Codex <noreply@openai.com>`. Record deviations in this banner and STATUS.

## Review Focus

1. Export/reparse loses author adjustments, icon palette, typed/per-OS UI entries or false flags (Tasks 1–2).
2. Upstream templates, duplicate display names, Unicode names and renamed variants produce unstable output (Task 3).
3. An archive is hostile or partially invalid, or the target changes between preview and commit (Task 4).
4. Migration crashes after output publication but before moving an original; rerun neither duplicates nor loses data (Task 5).
5. Existing selected names, failed inputs and ignored Marketplace config survive store removal with clear diagnostics (Tasks 5–6).

## File map

App files below use `jasper-app/src/main/java/dev/jasper/app/`; tests mirror under `src/test/java/dev/jasper/app/`.

| Files | Responsibility |
|---|---|
| `appearance/ThemeTomlWriter.java` (new), `ThemeToml`, `ThemeDocument` | Deterministic native serialization and icons round trip |
| `themeimport/{ThemeLoader,ThemeArchive,EditorScheme,IntellijConversion,AlacrittyConversion,AlacrittyTheme}.java` (moved) | One legacy parsing/conversion implementation |
| `themeimport/{ThemeConverter,ConversionResult,ThemeImporter,ThemeFiles,ThemeMigration}.java` (new) | Public app-native conversion/file/migration operations |
| `themeimport/ConvertThemes.java` (new), `jasper-app/build.gradle.kts` | JavaExec offline bundle generation |
| `tools/themes/fetch-themes.py`, `tools/themes/sources.json` (new) | Explicitly pinned maintainer acquisition |
| `third_party/themes/<source>/` (new), app resource `themes/` and `themeimport/intellij/` | Source evidence, generated native output, importer parents |
| `appearance/{Theme,ThemeCatalog,BuiltInThemes}`, `application/{JasperApplication,ConfigurationController}` | Native-only runtime and startup sequencing |
| `themesui/SampleTerminal.java` (move), `settingsui/CorePages`, `workspace/WindowChrome` | Functional interim settings/menu flow |
| `gradle/application-architecture.gradle.kts`, affected package-info files, docs | Package inventory, guides, notices and status |

## Task 1: Lossless native writer and acyclic converter extraction

**Interfaces:** `public static String ThemeTomlWriter.write(ThemeDocument document)` serializes in stable section/key
order; preserves semantically significant `[ui]` insertion order. Add native `[icons]` support for the data already
held by ThemeDocument.icons, with the same bounded, data-only validation as the JSON conversion. No class-valued keys.
Make `ThemeToml` and `parse(String text, String origin)` public with JavaDoc for themeimport; no importer dependency
back into appearance. The writer accepts fully resolved documents for export; duplication's inherited document is a separate plan-5 path.

- [ ] **Step 1: Add `nativeRoundTripPreservesCompleteThemePayload`.** Fixture includes author/license/source/commit,
explicit false ui_complete, all seed/token/syntax forms, alpha colors, escaped Unicode/quotes, nested/per-OS UI data,
icon palette and an explicit empty or false field. Compare parsed structural values, then install both sides on EDT
using existing parity fixtures to catch author-dependent differences. Keep private materialization isolation tests.

```java
String encoded = ThemeTomlWriter.write(document);
assertThat(encoded).isEqualTo(ThemeTomlWriter.write(document));
assertThat(roundTripped.metadata()).isEqualTo(document.metadata());
assertThat(roundTripped.icons()).isEqualTo(document.icons());
assertThat(roundTripped.ui()).isEqualTo(document.ui());
assertThat(roundTripped.syntax()).isEqualTo(document.syntax());
```

Use the existing ThemeToml parse helper to assign `roundTripped`; parse errors include output filename and line.
Add malformed icon-palette/unsafe-value rejection tests. Run `./gradlew :jasper-app:test --tests '*ThemeToml*'`; expect
missing writer/icons parser behavior.
- [ ] **Step 2: Implement the writer/parser amendment.** Stable metadata→colors→tokens→syntax→ui→icons sections;
quote arbitrary UI keys and strings with TOML escaping, preserve numeric/boolean/list/table types, omit absent optionals
but never false. Serialize Java Color through one canonical hexadecimal representation, including alpha. Do not sort
UI entries where wildcard/specific-key order matters. Record `[icons]` in the design as the reviewed format amendment.
- [ ] **Step 3: Extract legacy parsing without a cycle.** Move the named readers/converters into themeimport with their
tests; retain visibility only for app composition and the build entry point. Generate native IntelliJ Light/Darcula
resources now so BuiltInThemes no longer calls legacy parsers. Temporarily inject legacy catalog contributions through
`ThemeCatalog`'s constructor `Supplier<List<Theme>>` from application composition (existing constructor delegates to an
empty supplier); keep archive diagnostics on the existing catalog problem channel. themeimport may construct public
Theme.Source values; appearance never imports themeimport. The old pages stay functional until Task 6. Move the
Alacritty parser and plan-2 converter together; update terminalthemes imports to consume themeimport, not the reverse.
- [ ] **Step 4: Run** format/parity/archive tests, architecture checks and `./gradlew check`. Expect exit 0.
- [ ] **Step 5: Commit** `refactor(themes): add native writer and extract legacy importers` with required trailer.

## Task 2: Complete shared conversion, inverse tokens and syntax

**Files:** moved converters/readers, new `themeimport/ThemeConverter.java`, `ConversionResult.java`, golden fixtures
under `jasper-app/src/test/resources/dev/jasper/app/themeimport/`, `ThemeConverterTest.java`.

**Interfaces:** `public static List<ConversionResult> ThemeConverter.convert(Path source)` dispatches supported local
inputs; `public record ConversionResult(ThemeDocument document, List<String> warnings, String sourceEntry)` defensively
copies warnings. Invalid inputs use existing typed exceptions with source/entry context. Archive providers that fail
produce contextual diagnostics alongside valid results through the importer report in Task 4; do not discard successes.
Provide `convertAll(Path)` returning a batch record of results plus failures for partial archives; `convert(Path)` is
the strict convenience operation (throws when any provider fails). Define that nested `Batch` in ThemeConverter.

- [ ] **Step 1: Add red golden tests** `intellijParentsNamedColorsAndSyntaxFlatten`,
`alacrittyCopiesConsoleAndDropsDim`, `missingConsoleReportsChosenFallback`, `archiveConvertsEveryProvider`.
Assert an exported/reparsed document has no extends; ANSI matches source; accent reaches 3:1; syntax flags survive;
all residual UI/icon entries survive; no missing-console fallback is silent. Include parent cycles, missing parents,
relative editor-scheme references, inherited attribute values and one invalid provider among valid providers.
- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*ThemeConverterTest'`; expect incomplete inverse/syntax conversion.
- [ ] **Step 3: Complete IntelliJ extraction.** Retain ThemeLoader's exact parent/named-color merge order and author.
Console seeds come from inherited `CONSOLE_BACKGROUND_KEY`, `CONSOLE_NORMAL_OUTPUT`, and normal/bright console ANSI
attributes. Accent precedence: ColorPalette.accent, Component.focusColor, EditorTabs.underlineColor,
Button.default.startBackground; generator fallback only when none exists. No console colors: choose Jasper Light/Dark
by resolved darkness and report it. Partial console definitions overlay the chosen fallback and report missing values.
Expose plan 2's pure `WorkspaceTokenKeys` mapping as
`public static Map<Token,Color> explicitTokens(Map<String,Object> resolvedUi)`; converters use only supplied resolved
keys, leaving missing tokens for generation. They must not invoke WorkspaceValues' EDT-only private-LAF materializer.
Share key priorities with that materializer; no duplicate inverse tables with different precedence. Explicit upstream
UI entries remain in `[ui]` so token generation cannot erase a supplied override.

| Syntax role | Scheme attribute priority |
|---|---|
| keyword / type / function | DEFAULT_KEYWORD / DEFAULT_CLASS_NAME / DEFAULT_FUNCTION_DECLARATION then DEFAULT_FUNCTION_CALL |
| variable / constant / number | DEFAULT_IDENTIFIER / DEFAULT_CONSTANT / DEFAULT_NUMBER |
| string / comment / doc_comment | DEFAULT_STRING / DEFAULT_LINE_COMMENT then DEFAULT_BLOCK_COMMENT / DEFAULT_DOC_COMMENT |
| annotation / operator / punctuation | DEFAULT_METADATA / DEFAULT_OPERATION_SIGN / DEFAULT_COMMA then DEFAULT_SEMICOLON |
| tag / attribute | HTML_TAG then XML_TAG / HTML_ATTRIBUTE_NAME then XML_ATTRIBUTE_NAME |
| regex / error | REGEXP_CHAR_CLASS then REGEXP_CHARACTER then remaining REGEXP_* in lexical order / ERRORS_ATTRIBUTES |

Resolve inherited/fallback attribute references before extraction. Font type bits 1=bold and 2=italic; use foreground
only when present (an underline/effect color is not foreground). Missing roles stay absent for generator derivation.
- [ ] **Step 4: Complete Alacritty conversion.** Reuse plan-2 algorithm, read primary/cursor/selection/normal/bright,
ignore dim, reject YAML with a useful error, keep source filename in diagnostics. TOML colors may use the accepted
existing parser forms; generated output uses native canonical form. Classify `.toml` by structure so a native file is
reported as already native or explicitly supported by copying through the native resolver, never misread as Alacritty.
- [ ] **Step 5: Run** conversion goldens, old parity tests and `./gradlew check`; commit
`feat(themes): complete IntelliJ and Alacritty conversion` with required trailer.

## Task 3: Freeze sources and generate the full reproducible catalog

**Files:** tools/source/generated paths in the file map; `themeimport/ConvertThemes.java`, app Gradle build;
`BundledThemeDriftTest`, `BundledThemeProvenanceTest`, `BundledThemeUniquenessTest` (new).

**Interfaces:** `ConvertThemes.main(String[] args)` accepts `--sources <directory> --output <directory>` and emits native
TOMLs, `index.txt`, `THIRD-PARTY-THEMES.md`. Gradle `:jasper-app:convertThemes` invokes it. Avoid processResources↔JavaExec
cycles: compileJava plus dependency runtime jars and explicit importer resource input, not the sourceSet runtimeClasspath
that requires the generated resources. Tests regenerate into a temp directory; never rewrite working files in check.

- [ ] **Step 1: Add red uniqueness/provenance/drift tests.** Read index, assert every entry exists exactly once, compare
normalized display names using ThemeCatalog's case/space normalization, require author/license/source/full commit for
all non-Jasper themes, verify license files and assets.tsv SHA-256, and byte-compare regenerated files/index/notices.
Require expected variant families below, not an invented final total (Alacritty name overlap changes that total).
- [ ] **Step 2: Run** the new tests; expect the incomplete hardcoded catalog to fail.
- [ ] **Step 3: Freeze actual upstreams with `fetch-themes.py`.** Use sources.json with full immutable commit, artifact
URL/path, SHA-256, include/rename/exclude rules, license path and importer-parent dependencies. Preserve today's full
IntelliJ pin `f7377708b654b73b206da40bb382ecb4d44e8f12`; resolve Alacritty `ab88d5a8` to its full commit. Fetch other
sources from the spec, verify licenses against downloaded artifacts, and check in evidence before generation. Never
invent unknown commits or take a mutable latest URL without a digest. Catppuccin requires a pinned release JAR and
its matching source commit because source files are templates.

| Source id / repository | Required variants / transformation |
|---|---|
| intellij / JetBrains/intellij-community | IntelliJ Light, Darcula; classic/New UI/Islands/High Contrast importer parents |
| one-dark / one-dark/jetbrains-one-dark-theme | One Dark, One Dark Vivid |
| dracula / dracula/jetbrains | Dracula, Alucard, Dracula Colorful |
| solarized / snowe2010/solarized-jetbrains | Solarized Dark, Solarized Light |
| catppuccin / catppuccin/jetbrains | Catppuccin Latte, Frappé, Macchiato, Mocha |
| material-lite / AtomMaterialUI/material-theme-ui-lite | All except Monokai Pro; explicit rename table for other-project names |
| vscode / dinbtechit/vscode-theme | Code Dark, Code Dark Modern, Code Light |
| nord / nordtheme/jetbrains | Nord |
| gruvbox / lonre/gruvbox-intellij-theme | Dark/Light × Hard/Medium/Soft (six) |
| one-dark-modern / cweijan/idea-one-dark-modern-theme | One Dark Modern |
| xcode / WhiteVermouth/XcodeTheme | Studio Dark, Studio Light; hand-authored terminal override documents |
| alacritty / alacritty/alacritty-theme | Existing 177 inputs minus names won by IntelliJ conversions |

Each source contains LICENSE, NOTICE when supplied, SOURCE.md (commit/artifact/renames/exclusions), assets.tsv with
upstream path and hash. Keep the spec's excluded repositories excluded. Source acquisition is the only network step;
respect existing archive caps and safe paths. If a promised variant cannot be sourced under the stated license,
report the exact mismatch before changing scope; do not silently ship an incomplete family.
- [ ] **Step 4: Implement deterministic generation.** Stable id allocation from source-id plus variant id; sort output
index; one normalized display name globally. Native Jasper names reserved; IntelliJ wins Alacritty duplicates. Freeze
Material renames in the manifest before generation, with no collisions against other families. Studio overrides are
checked-in native extends files applied during conversion and flattened into standalone outputs. Add provenance after
conversion without losing original author (needed for FlatLaf adjustments). Notices cover both generated bundle and
importer-parent resources; package the generated notice alongside resources for plan 5's About action.
- [ ] **Step 5: Run** `./gradlew :jasper-app:convertThemes` twice and verify the second changes nothing, then drift tests
and `./gradlew check --offline` with dependencies already cached. Check no network operation exists in generation or
runtime code. Commit `feat(themes): vendor and generate the native theme catalog` with required trailer.

## Task 4: Transactional import and file operations

**Files:** new `themeimport/ThemeFiles.java`, `ThemeImporter.java`, tests `ThemeImporterTest`, `ThemeFilesTest`.

**Interfaces:** ThemeFiles owns `<home>/themes`, native target validation and atomic writes. Define
`Path create(ThemeDocument document)` (no overwrite), `void replace(Path target, String expectedSha256, ThemeDocument document)`
(only validated direct-child user TOML), and `String fingerprint(Path target)`. Importer produces immutable nested
`ImportPlan` (converted results, failures, collisions, expected target hashes) via `plan(Path source)`;
`ImportReport commit(ImportPlan plan, Map<String,ConflictChoice> choices)` uses stable per-result ids; enum
`ConflictChoice { REPLACE, KEEP_BOTH, SKIP }`. Report lists successful native paths, warnings/failures and actual names.
No dialogs here. SKIP represents cancellation/one skipped provider; it cannot delete an existing theme.

- [ ] **Step 1: Add red tests** `replaceRequiresUnchangedTarget`, `keepBothUsesDeterministicNameAndSafeFilename`,
`invalidArchiveKeepsExistingFiles`, `partialArchiveReportsEachProvider`. Pin foo → foo 2 → foo 3, case/space collision,
Unicode names, duplicate providers, builtin/user shadowing, source with path separators, archive traversal/zip bomb,
failed writes and symlink target. Ensure import reports reused fallback messages and never writes outside themes.

```java
assertThat(report.importedNames()).containsExactly("Example 2");
assertThat(Files.readString(existing)).isEqualTo(originalBytes);
assertThat(report.failures()).isEmpty();
```

Define `importedNames()`/`failures()` on ImportReport with immutable lists; tests also inspect actual output parse.
- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*ThemeImporterTest' --tests '*ThemeFilesTest'`; expect missing operations.
- [ ] **Step 3: Implement planning then publication.** Convert/validate all selected outputs before any writes. Allocate
safe ids independently of display names; no user name becomes a relative path. Native user-name collision prompts
through the plan; builtin-name import may create the documented user override, with a clear report. Recheck hashes
immediately before REPLACE; a changed target returns a new conflict, never overwrites silently. Stage sibling temp
files then atomic moves; preserve original on any failure and clean only own temps. CREATE uses no-replace semantics;
if atomic no-replace is unavailable, reserve/revalidate the destination safely before publication. Operations in one
host serialize; a partial I/O failure reports exactly which outputs committed so retry is explicit.
- [ ] **Step 4: Run** importer/archive/file tests and `./gradlew check`; commit
`feat(themes): add safe offline theme import operations` with required trailer.

## Task 5: Resumable first-start migration and selection preservation

**Files:** new `themeimport/ThemeMigration.java`, `ThemeMigrationTest.java`; `application/JasperApplication`,
`config/ConfigMigration`, `ConfigLoader` and their tests.

**Interfaces:** `ThemeMigration(Path home).run()` returns a nested immutable `MigrationReport` with warnings,
source→actual-name mappings, moved originals and created outputs. Use existing ConfigMigration/SettingWriter for
necessary selected-name changes; keep backups/comments and do not rewrite unrelated settings.

- [ ] **Step 1: Add red temporary-home tests.** Include one archive with multiple providers, Alacritty, existing native
user and builtin collisions, two originals sharing a basename, an invalid file, and selected legacy names in both
slots. Test first run, marked rerun, interrupted after each publication/move/config boundary, changed source during
resume, and failure moving original. Assertions include exact original bytes, one output per source/provider, valid
selected-name resolution, failed source stays, warnings persist, and marker only after the run's outcomes are durable.
- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*ThemeMigrationTest'`; expect missing migration behavior.
- [ ] **Step 3: Implement a journaled sequence.** Startup runs off EDT before the first catalog resolution or plugin
start. Scan direct regular files only; ignore legacy/ and symlinks. Use a versioned journal under themes/legacy with
source relative path+hash, provider id, planned output/name/hash, legacy destination, and state. Persist each intent
before publishing; validate output hash on recovery. Stage and parse outputs first, publish them, then move the original
only after every provider from it succeeded. For partial archives, preserve original and record completed outputs so
retry cannot duplicate them. Legacy destination preserves source-folder distinction and chooses a suffix if occupied.
Never overwrite or delete an original, even when another file has equal bytes.

Resolve collisions with KEEP_BOTH automatically in migration. Preserve old selected-source precedence: if migration
had to rename that selected source, update only its slot to the actual name through ConfigMigration after native
outputs exist. Reserve native names before allocation; preserve builtin display-name mappings for renamed variants.
Journal the intended config update so restart completes it once, not repeatedly. Record failure outcomes and write
`.migrated` after the pass is durable; subsequent startup reads saved failure diagnostics while skipping conversion.
Changed/missing failed sources refresh diagnostics; an explicit later Import is the retry route. Reading a marker
alone must not hide unresolved migration failures. Keep source hashes in diagnostics/journal, never archive contents.
- [ ] **Step 4: Implement ignored Marketplace input.** Remove it from active schema/UI; list its old path in the loader's
ignored legacy paths so true/false or old values do not cause an unknown-key warning. Verify aliases from plan 2 remain
canonical-first and preserve requested values when fallback applies.
- [ ] **Step 5: Run** migration/config tests and `./gradlew check`; commit
`feat(themes): migrate legacy themes without losing originals` with required trailer.

## Task 6: Native-only runtime and retirement of the stores

**Files:** appearance/application/settings/menu paths in file map; delete `themestore/` and `terminalthemes/` after
moving shared converter/parser and SampleTerminal; resource relocation; affected tests, package-info and architecture
allowlists; `docs/{configuration,app-architecture,app-maintenance,README,STATUS}.md` and theme design.

**Interfaces:** ThemeCatalog reads bundled `themes/index.txt` plus user native `.toml` only. Remove injected legacy
supplier from Task 1, `Theme.Archive` and transitional sources from plan 2; Theme.Source now has Bundled/UserFile.
ThemeManager keeps its in-memory FlatLaf JSON adapter. Native generation does not call themeimport.

- [ ] **Step 1: Add red integration tests** `startupMigratesBeforeCatalogResolution`, `runtimeIgnoresLegacySources`,
`interimThemeSelectorsHaveNoDeadTargets`. Load a temporary old home, resolve both selections and assert generated
values; leave invalid archives in place and assert diagnostics without runtime archive parsing. Inspect settings tree,
menu actions and config schema: no Marketplace client/page/options, no Alacritty page or dead navigation.
- [ ] **Step 2: Run** targeted application/catalog/settings tests; expect legacy runtime/page paths remain.
- [ ] **Step 3: Remove obsolete owners.** Move SampleTerminal to new themesui (still fake connector/canned output),
add package-info and architecture inventory now. Replace hardcoded bundled list with index. Move original vendored
inputs to third_party and importer parents to themeimport/intellij; package only necessary importer resources plus
native catalog/notices. Delete stores, installers/network dependencies used solely by them, old tests that assert
retired UI, and runtime archive catalog paths. Keep parser/safety/parity tests in themeimport.

Until plan 5, Appearance and workspace dropdowns use the native catalog. Update View menu to use it and direct
More Themes navigation to a functioning existing settings control; no link targets to deleted pages. Keep config file
selection fully functional. Update packaging/license checks and configuration examples.
- [ ] **Step 4: Finish documentation and review.** Explain offline import API, native theme location, migration originals,
marker/failure recovery and interim UI. Run `./gradlew check`, report XML counts, hygiene/diff checks, and independent
whole-branch review. Record actual output theme count, source pins, deviations and remaining manual checks in STATUS.
- [ ] **Step 5: Commit** `refactor(themes): retire legacy stores after native migration` with required trailer.

**User-only visual acceptance:** start with a copied legacy home; verify selected themes and legacy originals, try
Dracula/Catppuccin/Solarized/Gruvbox/Code Dark, confirm missing-theme warnings and both native dropdowns. Runtime Import
has no new settings UI until plan 5; its API is headlessly exercised here. Never launch GUI as the agent.
