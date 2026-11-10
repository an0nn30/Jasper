# Editor File Associations 2: The Platform Layer — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Status: not started (written 2026-11-10). Plan 2 of 3. Plan 1 (handed-in files open in the Jasper Editor, SDK
0.31.0 `FileOrigin`) is on main at 44c11698e. Plan 3 adds the SDK file-association surface, the testkit, the Editor's
File associations section and `file_association_extensions`. Planned deviations from the spec, recorded here as
AGENTS.md asks:
(a) The Windows installer, not the running app, registers the `JasperFile` ProgId and its open command. The installer
knows the install folder and its uninstall already deletes the keys it owns. The app only adds per-extension values at
the click.
(b) The extension-to-type table is a data file, `file-types.tsv`, that both the app and the packaging build read, so
the plist, the desktop entry and the code can't drift apart.
(c) Availability reasons and refusal reasons are enums, so plan 3 owns all wording.

**Goal:** An app-internal `dev.jasper.app.platform.FileAssociations` that answers, for file extensions, which app the
system opens them with, claims them for Jasper (directly on macOS and Linux, through Windows Settings on Windows),
saves and restores previous handlers, and tells whether a custom extension can be claimed. The packages also declare
the types, while installing still makes Jasper the default for nothing.

**Architecture:** `FileAssociations` is the facade. It validates extensions, keeps the previous-handler store and
serialises work on a worker. A package-private `FileAssociations.Backend` has three real implementations:
`MacFileAssociations` (Launch Services through `libjasper-macos.dylib`), `WindowsFileAssociations` (HKCU registry and
shell calls through Java FFM, like `WindowsUserPath`) and `LinuxFileAssociations` (`xdg-mime`, `mimeapps.list`,
shared-mime-info). Pure parsing lives in static methods tested on every OS, as `DefaultBrowserTest` does.
`ApplicationBootstrap` makes one for the running platform and `JasperApplication` holds it, as it holds
`DefaultBrowser`. Plan 3 exposes it through the SDK.

**Tech Stack:** Java 25 (JBR 25 JCEF) with FFM, Objective-C (Foundation, AppKit, UniformTypeIdentifiers), Gradle
(Groovy packaging scripts), WiX v3 template patching, JUnit 5, AssertJ, tomlj.

**Spec:** `docs/superpowers/specs/2026-11-10-editor-file-associations-design.md`: sections "Built-in list", "Platform
mechanics" (macOS, Windows, Linux, Custom extensions), "Saving previous handlers", and the anti-goals.

## Global Constraints

- Never write `UserChoice` or `UserChoiceLatest`, and never work around their hash or the UCPD driver. Only Windows
  Settings changes a Windows default.
- Installing, upgrading and first launch make Jasper the default for nothing. The plist ranks stay `Alternate`, and
  the packages leave every `defaults.list` alone.
- No test changes a real default, a real `mimeapps.list` or a real registry value outside a scratch key that the test
  deletes. Native harnesses use fake workspaces, as `MacLaunchServicesTest.m` does. Tests that touch the real OS only
  read.
- Associations open files for editing only. Nothing here makes Jasper run a file.
- Extensions are written `.ext`, lower case: a leading dot, then 1 to 16 characters from `[a-z0-9_+-]`. `.html` and
  `.htm` are refused (the Browser owns them).
- Jasper's identities: macOS bundle `dev.jasper.app`; Windows ProgId `JasperFile` and app key
  `Software\Jasper\Capabilities`; Linux desktop entry `jasper-workstation-Jasper.desktop`.
- Windows Settings deep link: `ms-settings:defaultapps?registeredAppUser=Jasper`, falling back to
  `ms-settings:defaultapps` when opening the first throws.
- Previous handlers live in `<home>/file-associations.toml` (`AppDirs.fileAssociations()`), written atomically. An
  unreadable file reads as empty, with a log line.
- No SDK change in this plan. Platform code stays in `dev.jasper.app.platform`.
- Never launch the GUI. Use `@TempDir` for folders. Commit messages end with the trailer block from the session's
  attribution reminder. After each task, `./gradlew check` is green and the AGENTS.md source-hygiene check is clean on
  touched files.

## Review Focus

1. A claim where one platform type is refused and the others succeed: the successful ones stay Jasper's with their
   previous handlers saved, nothing is rolled back, and the refused one reports why. Task 4:
   `aPartlyRefusedClaimKeepsWhatWorkedAndSavesOnlyWhatChanged`.
2. The previous-handler file is corrupt, or its folder is read-only: status and claim still work, the corrupt file
   reads as empty, and a failed save is logged without failing the claim. Task 4:
   `aCorruptOrUnwritableStoreNeverBlocksAClaim`.
3. A `mimeapps.list` with comments, other groups, CRLF line ends, a missing `[Default Applications]` group, or several
   types on one key: removing Jasper's default keeps every other byte. Task 7:
   `removingADefaultKeepsEveryOtherLine`.
4. Odd extension input: `.JAVA`, `java`, `.tar.gz`, `../x`, `.`, a 17-character extension, `.html`. These normalise
   to `.java`, or are refused, never passed to a system tool. Task 4: `extensionsAreNormalisedOrRefused`.
5. Restoring a type whose previous app was uninstalled: the outcome is `PREVIOUS_GONE`, the type stays Jasper's, and
   the saved entry is kept. Task 4 (facade) and Task 5 (macOS bridge `JasperHasApplication`):
   `aRestoreWhoseAppIsGoneKeepsTheEntry`.

---

### Task 1: The file-type catalogue

**Files:**
- Create: `jasper-app/src/main/resources/dev/jasper/app/platform/file-types.tsv`
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/FileTypeCatalog.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/platform/FileTypeCatalogTest.java`

**Interfaces:**
- Produces:
  - The TSV format. `#` starts a comment line. A type line is `<ext>\t<mac UTIs, comma-separated>\t<Linux MIME types,
    comma-separated>`, where either column may be empty, meaning that system has no text type for it. An import line is
    `import\t<UTI>\t<conforms-to UTI>\t<extensions without dots, comma-separated>\t<description>`.
  - `final class FileTypeCatalog` (package-private), with:
    - `static FileTypeCatalog builtIn()`, read once from the resource;
    - `static FileTypeCatalog parse(String text)`, which throws `IllegalArgumentException` naming the line on a
      malformed line, a duplicate extension, or an import whose UTI no type line uses;
    - `List<String> extensions()`, in file order;
    - `List<String> macTypes(String extension)` and `List<String> linuxTypes(String extension)`, empty when the
      extension is unknown or its column is empty;
    - `List<Import> macImports()`, where `record Import(String identifier, String conformsTo, List<String> extensions, String description)`.

- [ ] **Step 1: Write the TSV** with one line per extension from the spec's Built-in list:
  `.java .kt .kts .c .h .cpp .cc .cxx .hpp .hh .py .go .rs .js .mjs .ts .sh .zsh .bash .md .markdown .json .yaml .yml
  .toml .xml .txt .log`. Use the spec's identifiers. For types the spec marks "imported", add import lines with
  `dev.jasper.*` identifiers (`dev.jasper.kotlin-source`, `dev.jasper.go-source`, `dev.jasper.rust-source`,
  `dev.jasper.typescript-source`, `dev.jasper.toml`), conforming to `public.source-code` (`public.plain-text` for
  TOML). Task 5 and Task 7 check the real system databases and correct this file.
- [ ] **Step 2: Write the failing tests**:

```java
@Test void theBuiltInCatalogueParsesAndCoversTheSpecsList() {
    FileTypeCatalog catalog = FileTypeCatalog.builtIn();
    assertThat(catalog.extensions()).contains(".java", ".py", ".md", ".json", ".yaml", ".yml", ".txt").doesNotContain(".html", ".htm");
    assertThat(catalog.macTypes(".java")).containsExactly("com.sun.java-source");
    assertThat(catalog.linuxTypes(".yaml")).isEqualTo(catalog.linuxTypes(".yml"));
    assertThat(catalog.macTypes(".nope")).isEmpty();
    assertThat(catalog.macImports()).allSatisfy(i -> assertThat(i.identifier()).startsWith("dev.jasper."));
}

@Test void aMalformedCatalogueNamesItsLine() {
    assertThatIllegalArgumentException().isThrownBy(() -> FileTypeCatalog.parse("# c\n.java\tcom.sun.java-source\n"))
        .withMessageContaining("line 2");
    assertThatIllegalArgumentException().isThrownBy(() -> FileTypeCatalog.parse(".a\tx\ty\n.a\tx\ty\n")).withMessageContaining("line 2");
    assertThatIllegalArgumentException().isThrownBy(() -> FileTypeCatalog.parse(".a\tx\ty\nimport\tdev.jasper.q\tpublic.text\tq\tQ\n"))
        .withMessageContaining("dev.jasper.q");
}
```

- [ ] **Step 3:** `./gradlew :jasper-app:test --tests '*FileTypeCatalogTest'`. Expected: compile FAIL.
- [ ] **Step 4:** Implement `FileTypeCatalog`. Extensions in the file carry their dot. Import extensions don't.
- [ ] **Step 5:** Run Step 3's command. Expected: PASS.
- [ ] **Step 6:** Commit: `Platform: the file-type catalogue`.

### Task 2: macOS and Linux packages declare the types

**Files:**
- Modify: `gradle/packaging-support.gradle` (a new `ext.fileTypeCatalog` loader; `linuxDesktopMimeTypes` built from it)
- Modify: `gradle/packaging.gradle` (`macDocumentTypes` and a new `macImportedTypes` built from it; `packageApp`'s
  plutil calls near line 340; `verifyPackage`'s checks near line 704)
- Modify: `gradle/packaging-support-test.gradle` (the URL-types fixture near line 696 grows document types and imports;
  the desktop-entry fixtures near lines 747-765)
- Modify: `docs/packaging.md`

**Interfaces:**
- Consumes: `file-types.tsv` (Task 1), read by path
  `jasper-app/src/main/resources/dev/jasper/app/platform/file-types.tsv`.
- Produces:
  - `ext.fileTypeCatalog`, a Groovy map `[types: [[ext, mac: [..], linux: [..]], ...], imports: [[identifier,
    conformsTo, extensions, description], ...]]`, parsed with the same rules as Task 1.
  - `macDocumentTypes`: the two existing entries unchanged, plus a third,
    `{"CFBundleTypeName":"Source and text","CFBundleTypeRole":"Editor","LSHandlerRank":"Alternate","LSItemContentTypes":[<every mac UTI in the catalogue, in file order, without repeats>]}`.
  - `ext.macImportedTypes`: a JSON array with one dictionary per import,
    `{"UTTypeIdentifier":…,"UTTypeConformsTo":[…],"UTTypeDescription":…,"UTTypeTagSpecification":{"public.filename-extension":[…]}}`.
  - `linuxDesktopMimeTypes`: `MimeType=x-scheme-handler/http;x-scheme-handler/https;` followed by every Linux MIME type
    in the catalogue, in file order, without repeats, each ending in `;`.

- [ ] **Step 1: Extend the fixtures first.** In `packaging-support-test.gradle`, assert:
  - `macDocumentTypes` parses to three entries, all `Alternate`, the third containing `com.sun.java-source` and
    `public.json`;
  - `macImportedTypes` contains `dev.jasper.toml` with extension `toml`;
  - `linuxDesktopMimeTypes` starts with `MimeType=x-scheme-handler/http;x-scheme-handler/https;text/x-java;` and has no
    repeats;
  - on macOS, `UTImportedTypeDeclarations` round-trips through a stand-in plist, as `CFBundleURLTypes` does.
- [ ] **Step 2:** `./gradlew verifyPackagingSupport`. Expected: FAIL.
- [ ] **Step 3:** Implement per Interfaces. `packageApp` also runs
  `plutil -replace UTImportedTypeDeclarations -json <macImportedTypes>`. `verifyPackage` checks it as it checks
  `CFBundleDocumentTypes`. `linuxDesktopEntryProblem` keeps requiring the full `linuxDesktopMimeTypes` line.
- [ ] **Step 4:** `./gradlew verifyPackagingSupport`, then `./gradlew check`. Expected: PASS.
- [ ] **Step 5: Docs.** In `docs/packaging.md`'s macOS and Linux package sections, one sentence each:
  - the app declares the source and text types in the catalogue (ranked `Alternate`, plus its own declarations for the
    types macOS lacks);
  - the desktop entry lists their MIME types, so desktops offer Jasper under Open With;
  - installing still makes Jasper the default for nothing.
- [ ] **Step 6:** Commit: `Packaging: macOS and Linux declare the source and text types`.

### Task 3: The Windows installer registers `JasperFile`

**Files:**
- Modify: `packaging/windows/cygwin/CygwinInstallerTemplate.java` (`BROWSER_COMPONENT`)
- Modify: `packaging/windows/cygwin/CygwinInstallerTemplateTest.java` (`REGISTRATION`, `REMOVED_KEYS`)
- Modify: `packaging/windows/Verify-Installer.ps1` (if it lists the registered keys, add the ProgId the same way)
- Modify: `docs/packaging.md` (the Windows installer section)

**Interfaces:**
- Produces: these registry values after installation. Plan 2's Windows backend relies on the command value existing.
  - `HKCU|Software\Classes\JasperFile||Jasper document`
  - `HKCU|Software\Classes\JasperFile\DefaultIcon||"[INSTALLDIR]Jasper.exe",0`
  - `HKCU|Software\Classes\JasperFile\shell\open\command||"[INSTALLDIR]Jasper.exe" "%1"`

  The new `RegistryKey` for `Software\Classes\JasperFile` has `ForceDeleteOnUninstall="yes"`. It goes in the same
  component, whose GUID is kept.

- [ ] **Step 1:** Add the three lines to `REGISTRATION` and `"HKCU|Software\\Classes\\JasperFile"` to `REMOVED_KEYS`.
  `./gradlew verifyInstallerTemplate`. Expected: FAIL.
- [ ] **Step 2:** Add the key to `BROWSER_COMPONENT`. Extend the comment above it: the file ProgId takes a plain
  `"%1"`, because File Explorer passes absolute paths, which can't start with `--`. Run Step 1's command. Expected: PASS.
- [ ] **Step 3: Docs.** In `docs/packaging.md`'s Windows installer section, one sentence: the installer registers the
  `JasperFile` ProgId (the Editor's File associations use it), sets no default, and uninstalling removes it.
- [ ] **Step 4:** `./gradlew check`, then commit: `Windows installer: the JasperFile ProgId`.

### Task 4: The facade, its values and the previous-handler store

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/FileAssociations.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/PreviousHandlers.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java` (`fileAssociations()` returns
  `root.resolve("file-associations.toml")`)
- Test: `jasper-app/src/test/java/dev/jasper/app/platform/FileAssociationsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/platform/PreviousHandlersTest.java`

**Interfaces:**
- Consumes: `FileTypeCatalog` (Task 1).
- Produces (all public nested types of `FileAssociations` unless noted):
  - `sealed interface Handler`, with `record Jasper()`, `record Other(String id, String name)` (id: bundle id, ProgId
    or desktop id; name: display name, the id when unknown) and `record None()`.
  - `record TypeStatus(String type, Handler handler, Optional<Handler> previous)`. `previous` is the saved handler,
    possibly `None`; it is empty when nothing is saved.
  - `record ExtensionStatus(String extension, List<TypeStatus> types)`. `types` is empty when the extension can't be
    claimed here.
  - `enum Unavailable { DEVELOPMENT_RUN, NOT_INSTALLED, NO_XDG_MIME, UNSUPPORTED_SYSTEM }`.
  - `enum Refusal { INVALID, BROWSER_OWNED, NO_SYSTEM_TYPE, NOT_TEXT }`.
  - `sealed interface Claimability`, with `record Claimable(List<String> types, boolean declares)` and
    `record NotClaimable(Refusal refusal)`.
  - `sealed interface Outcome`, with `record Done()`, `record InSettings()`, `record NoPrevious()`,
    `record PreviousGone()` and `record Failed(String detail)` (the system's own message, for the log and the row).
  - `record TypeResult(String extension, String type, Outcome outcome)`.
  - Package-private `interface Backend`. Its three real implementations come in Tasks 5-7.
    - `Optional<Unavailable> unavailable()`
    - `Claimability claimability(String extension)`: called only with a normalised extension the facade accepted.
    - `Handler handler(String type) throws IOException`
    - `List<TypeResult> claim(Map<String, List<String>> typesByExtension)`: one call per claim, so Windows opens
      Settings once.
    - `List<TypeResult> restore(Map<String, Map<String, Handler>> previousByTypeByExtension)`
    - `void forget(String extension)`: drops a declaration Jasper made for a custom extension; a no-op where none was
      made.
  - The public facade:
    - `FileAssociations(Executor worker, Backend backend, PreviousHandlers store)`: package-private.
    - `static FileAssociations unsupported()`: always `UNSUPPORTED_SYSTEM`, for tests and for an app that was given
      none.
    - `static Optional<String> normalise(String extension)`: lower case, adds the dot, applies the Global Constraints
      rule. `.html` and `.htm` normalise but are refused by `claimability`.
    - `Optional<Unavailable> unavailable()`: any thread, cached after the first answer.
    - `CompletableFuture<Claimability> claimability(String extension)`: invalid gives `NotClaimable(INVALID)`,
      Browser-owned gives `NotClaimable(BROWSER_OWNED)`, otherwise it asks the backend.
    - `CompletableFuture<List<ExtensionStatus>> status(List<String> extensions)`
    - `CompletableFuture<List<TypeResult>> claim(List<String> extensions)`: before claiming each claimable type, reads
      its handler. If that handler isn't Jasper and nothing is saved for the type, it saves it. The earliest previous
      handler wins, and a type Jasper already owns saves nothing.
    - `CompletableFuture<List<TypeResult>> restore(List<String> extensions)`: only types with a saved entry. The
      facade gives the backend nothing for a saved `None` on macOS and Windows; the backend reports `NoPrevious`, and
      only the Linux backend can act on `None`. A `Done` deletes the entry. `PreviousGone` and failures keep it.
    - `CompletableFuture<Void> forget(String extension)`
    - `void onChanged(Runnable listener)`: one listener; runs on the EDT after each claim or restore, and after a
      `refresh` whose answers differ from the last `status` it gave.
    - `void refresh()`: re-asks the last `status` extensions on the worker, at most once per 5 seconds and never
      while one is running, as `DefaultBrowser.refresh` does.

    Backend work runs on `worker` under one lock, so calls never overlap. A backend exception becomes `Failed` for that
    extension's types (or, for `status`, a `TypeStatus` whose handler is `Other("", "")`), with a log line. The
    returned futures never complete exceptionally.
  - `final class PreviousHandlers` (package-private):
    - `PreviousHandlers(Path file)`
    - `Optional<FileAssociations.Handler> get(String type)`
    - `void put(String type, FileAssociations.Handler handler)`
    - `void remove(String type)`

    The TOML holds one table per type, `[previous."<type>"]`, with `id` and `name` strings; an empty `id` is `None`.
    Writes go through a temporary file in the same folder and an atomic move. A read failure is treated as empty and logged once; a write failure is logged and
    swallowed.

- [ ] **Step 1: Write the failing tests.** A test-only `FakeBackend` (nested in the test) holds a mutable map from type
  to `Handler`, a map from extension to `Claimability`, a set of types whose claim fails, and a list of the calls it
  received. The tests:

```java
@Test void extensionsAreNormalisedOrRefused() {
    assertThat(FileAssociations.normalise(".JAVA")).contains(".java");
    assertThat(FileAssociations.normalise("java")).contains(".java");
    for (String bad : List.of(".tar.gz", "../x", ".", "", ".abcdefghijklmnopq", ".a b", ".é"))
        assertThat(FileAssociations.normalise(bad)).as(bad).isEmpty();
    var associations = withBackend();
    assertThat(associations.claimability(".html").join()).isEqualTo(new FileAssociations.Claimability.NotClaimable(FileAssociations.Refusal.BROWSER_OWNED));
    assertThat(associations.claimability("../x").join()).isEqualTo(new FileAssociations.Claimability.NotClaimable(FileAssociations.Refusal.INVALID));
    assertThat(backend.calls).as("nothing odd reaches the backend").isEmpty();
}

@Test void aClaimSavesTheFirstPreviousHandlerOnlyOnce() { /* .java held by Other("com.apple.dt.Xcode","Xcode"); claim; the
   fake now says Jasper; claim again; store.get("com.sun.java-source") is Xcode; a type that was already Jasper saves nothing */ }

@Test void aPartlyRefusedClaimKeepsWhatWorkedAndSavesOnlyWhatChanged() { /* .cpp has two types; the second fails;
   results: first Done, second Failed("boom"); the first's previous is saved, the second's is not; no restore call was made */ }

@Test void restoreHandsBackAndForgetsTheEntryOnlyWhenDone() { /* Done deletes; NoPrevious and Failed keep */ }

@Test void aRestoreWhoseAppIsGoneKeepsTheEntry() { /* fake restore answers PreviousGone; store still has the entry */ }

@Test void aCorruptOrUnwritableStoreNeverBlocksAClaim(@TempDir Path home) throws Exception {
    Path file = Files.writeString(home.resolve("file-associations.toml"), "[[[not toml");
    var store = new PreviousHandlers(file);
    assertThat(store.get("x")).isEmpty();
    // a read-only folder (skip the permission half on Windows): put logs and returns; claim still gives Done
}

@Test void refreshReportsOnlyChangesAndAtMostEveryFiveSeconds() { /* injected clock as in DefaultBrowserTest */ }
```

  The commented tests are written in full, with exactly the values and assertions their comments give. `PreviousHandlersTest` covers a round trip of
  `Other` and `None`, a type with dots and quotes in it (`com.sun.java-source`, `text/x-c++src`), and an atomic write
  that leaves no temporary file behind.
- [ ] **Step 2:** `./gradlew :jasper-app:test --tests '*FileAssociationsTest' --tests '*PreviousHandlersTest'`.
  Expected: compile FAIL.
- [ ] **Step 3:** Implement per Interfaces. The refresh throttle reuses `DefaultBrowser`'s rule: a clock set back never
  holds a check back.
- [ ] **Step 4:** Run Step 2's command. Expected: PASS.
- [ ] **Step 5:** `./gradlew check` (`verifyApplicationArchitecture` must pass), then commit:
  `Platform: FileAssociations and its previous-handler store`.

### Task 5: macOS: the Launch Services bridge and backend

**Files:**
- Modify: `jasper-app/src/main/native/MacLaunchServices.m`
- Modify: `jasper-app/src/test/native/MacLaunchServicesTest.m`
- Modify: `gradle/mac-native.gradle` (add `-framework UniformTypeIdentifiers` to `compilerArguments`; give
  `testMacLaunchServices` the catalogue path as its one argument)
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/MacLaunchServices.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/MacFileAssociations.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/platform/MacFileAssociationsTest.java`
- Modify (if the check below finds mismatches): `file-types.tsv`

**Interfaces:**
- Consumes: `FileAssociations.Backend` and the values (Task 4); `FileTypeCatalog` (Task 1).
- Produces:
  - Objective-C, each taking the workspace so fakes can stand in:
    - `NSArray<NSString *> *JasperContentTypeHandler(NSWorkspace *workspace, NSString *uti)` returns
      `@[bundleId, displayName]`, or nil when no app opens the type. On macOS 12+ it uses
      `URLForApplicationToOpenContentType:` with `[UTType typeWithIdentifier:]`; on 11 it uses
      `LSCopyDefaultRoleHandlerForContentType(kLSRolesAll)` and `URLForApplicationWithBundleIdentifier:`.
    - `NSString *JasperSetContentTypeHandler(NSWorkspace *workspace, NSString *uti, NSString *bundleId, double seconds)`
      returns nil on success, else the error's `localizedDescription`, or "timed out". On 12+ it uses
      `URLForApplicationWithBundleIdentifier:`, then `setDefaultApplicationAtURL:toOpenContentType:completionHandler:`,
      waiting on a semaphore for at most `seconds`. On 11 it uses `LSSetDefaultRoleHandlerForContentType`.
    - `BOOL JasperHasApplication(NSWorkspace *workspace, NSString *bundleId)`
    - `NSString *JasperTextTypeForExtension(NSString *extension)` returns the UTI, or nil when the type is dynamic or
      doesn't conform to `public.text`.
  - JNI in `MacLaunchServices`, each behind `MacNative.load()` as `defaultHandler` is:
    - `static String[] contentTypeHandler(String uti)`
    - `static String setContentTypeHandler(String uti, String bundleId)`, with a 5-second wait
    - `static boolean hasApplication(String bundleId)`
    - `static String textTypeForExtension(String extension)`
  - `final class MacFileAssociations implements FileAssociations.Backend`, built with
    `MacFileAssociations(FileTypeCatalog catalog, MacCalls calls)`. `MacCalls` is a package-private record of functions
    (not an interface, which AGENTS.md allows only with two real implementations):
    `record MacCalls(BooleanSupplier loaded, Function<String, String[]> handler, BiFunction<String, String, String> setHandler, Predicate<String> hasApplication, Function<String, String> textType)`,
    with `static MacCalls system()` wiring `MacNative.load` and `MacLaunchServices`. Tests build one from lambdas.
    - `unavailable()`: `DEVELOPMENT_RUN` when `MacNative.load()` is false.
    - `claimability(ext)`: from the catalogue's mac types. An empty mac column gives `NotClaimable(NOT_TEXT)`.
      Otherwise it asks `textTypeForExtension`: a UTI gives `Claimable(List.of(uti), false)`, nil gives
      `NotClaimable(NO_SYSTEM_TYPE)`.
    - `handler(type)`: `dev.jasper.app` gives `Jasper`, null gives `None`, else `Other(id, name)`.
    - `claim`: `setContentTypeHandler(type, "dev.jasper.app")` per type. Null gives `Done`; a message gives
      `Failed(message)`.
    - `restore`: `None` gives `NoPrevious`; an app that `hasApplication` says is gone gives `PreviousGone`; otherwise
      it sets the previous bundle id.
    - `forget` is a no-op.

- [ ] **Step 1: Extend the native harness** with a fake workspace that implements the 12+ selectors (it records the
  content type and application it was given, and calls the completion handler with nil, an `NSError`, or never). It
  asserts:
  - the handler pair for a stand-in bundle;
  - nil for no handler;
  - the set call's nil on success, the error description on failure, and "timed out" after a short wait;
  - `JasperHasApplication` true and false;
  - `JasperTextTypeForExtension(@"swift")` (a system text type) and nil for `@"jasper-no-such-ext"`.

  Then, reading the catalogue file from `argv[1]`, it asserts that each extension whose mac column is a system type
  (not an import) resolves, through `[UTType typeWithFilenameExtension:]`, to exactly that UTI. It prints each
  mismatch before failing.
- [ ] **Step 2:** `./gradlew :jasper-app:testMacLaunchServices`. Expected: FAIL (the functions don't exist).
- [ ] **Step 3:** Implement the Objective-C functions and the JNI wrappers. Then fix `file-types.tsv` for every
  mismatch the harness prints. Where macOS gives an extension a type that isn't text (for example `.ts` as an MPEG-2
  transport stream), empty that extension's mac column; claiming it would take over other files. Rerun until it passes.
- [ ] **Step 4: Java tests** in `MacFileAssociationsTest` with a fake `MacCalls`. They cover every bullet under
  `MacFileAssociations` above, including `aRestoreWhoseAppIsGoneKeepsTheEntry`'s backend half: `hasApplication` false
  gives `PreviousGone`, and nothing is set. Run
  `./gradlew :jasper-app:test --tests '*MacFileAssociationsTest'`. Expected: FAIL, then PASS after implementing.
- [ ] **Step 5:** `./gradlew check`, then commit: `macOS: Launch Services file associations`.

### Task 6: Windows: the registry and the backend

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/WindowsRegistry.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/WindowsFileAssociations.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/DefaultBrowser.java` (`askWindows` uses
  `WindowsRegistry.userChoice`; behaviour unchanged, so `DefaultBrowserTest` stays green unchanged)
- Test: `jasper-app/src/test/java/dev/jasper/app/platform/WindowsFileAssociationsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/platform/WindowsRegistryTest.java`

**Interfaces:**
- Consumes: Task 4's `Backend` and values. Task 3's ProgId command key.
- Produces:
  - `final class WindowsRegistry` (package-private), FFM like `WindowsUserPath`, Windows only, otherwise it throws
    `IOException`:
    - `static Optional<String> read(String key, String name)`: delegates to `WindowsUserPath.read`.
    - `static boolean exists(String key)`: `RegOpenKeyExW` / `RegCloseKey`.
    - `static void writeString(String key, String name, String value)`: `RegSetKeyValueW` with `REG_SZ` (1).
    - `static void changeNotify()`: `shell32!SHChangeNotify(0x08000000, 0, NULL, NULL)`; failures are logged.
    - `static Optional<String> friendlyAppName(String extension)`:
      `shlwapi!AssocQueryStringW(ASSOCF_NONE, ASSOCSTR_FRIENDLYAPPNAME (4), extension, NULL, buf, &len)`.
    - `static Optional<String> userChoice(String baseKey, BiFunction<String, String, Optional<String>> registry)`: the
      first `ProgId` of `UserChoiceLatest` (value), `UserChoiceLatest\ProgId` (value), `UserChoice` (value). This is
      the rule `DefaultBrowser.askWindows` uses today, moved here. It throws `UncheckedIOException` when a read fails.
  - `final class WindowsFileAssociations implements FileAssociations.Backend`, built with
    `WindowsFileAssociations(Registry registry, Consumer<URI> openSettings)`. `Registry` is a package-private record
    of functions, not an interface:
    `record Registry(BiFunction<String, String, Optional<String>> read, Predicate<String> exists, Writer write, Runnable changeNotify, Function<String, Optional<String>> friendlyAppName)`,
    where `Writer` is `(key, name, value) -> void throws IOException` (a nested `@FunctionalInterface`). It has
    `static Registry system()` over `WindowsRegistry`. Tests build one over an in-memory map.
    - `unavailable()`: `NOT_INSTALLED` unless `Software\Classes\JasperFile\shell\open\command` exists.
    - `claimability(ext)`: `Claimable(List.of(ext), false)`. The type id on Windows is the extension.
    - `handler(ext)`: the `userChoice` of `Software\Microsoft\Windows\CurrentVersion\Explorer\FileExts\<ext>`.
      `JasperFile` (ignoring case) gives `Jasper`; another ProgId gives `Other(progId, friendlyAppName(ext) or progId)`;
      none gives `None`.
    - `claim`: for each extension, write `Software\Classes\<ext>\OpenWithProgids` value `JasperFile` = `""` and
      `Software\Jasper\Capabilities\FileAssociations` value `<ext>` = `JasperFile`. Then `changeNotify()` once, then
      `openSettings` once with `ms-settings:defaultapps?registeredAppUser=Jasper`. If that throws, try
      `ms-settings:defaultapps`. If both throw, every type gives `Failed(message)`; otherwise every type gives
      `InSettings`. Repeating a claim writes the same values again.
    - `restore`: `None` gives `NoPrevious`; otherwise it opens Settings once and gives `InSettings`. Windows can't
      restore by itself.
    - `forget` is a no-op. The values stay, and point at a ProgId that uninstalling removes.

- [ ] **Step 1: Write the failing tests.** In `WindowsFileAssociationsTest`, use the in-memory registry and a recording
  opener. Cover:
  - availability with and without the command key;
  - the handler for `JasperFile`, `jasperfile`, `VSCode.java` (with its friendly name and without one) and no choice;
  - a claim of `.java` and `.py`, which writes exactly the four values, notifies once and opens the deep link once;
  - a deep link that throws, which falls back once;
  - both throwing, which gives `Failed`;
  - a restore of `None` (`NoPrevious`) and of `Other` (`InSettings`, one opening);
  - an assertion that no key containing `UserChoice` is ever written.

  In `WindowsRegistryTest`, under `@EnabledOnOs(OS.WINDOWS)`: round-trip `writeString`, `read` and `exists` in a
  scratch key `Software\JasperTest-<uuid>`, deleted in `finally` with `WindowsUserPath.delete`. Also check that
  `userChoice` orders `UserChoiceLatest` before `UserChoice`, a case moved over from `DefaultBrowserTest`'s coverage.
- [ ] **Step 2:** `./gradlew :jasper-app:test --tests '*WindowsFileAssociationsTest' --tests '*DefaultBrowserTest'`.
  Expected: compile FAIL.
- [ ] **Step 3:** Implement per Interfaces, and switch `DefaultBrowser.askWindows` to `WindowsRegistry.userChoice`.
- [ ] **Step 4:** Run Step 2's command. Expected: PASS. The `@EnabledOnOs(WINDOWS)` tests run on Windows CI only. Say
  so in the commit body.
- [ ] **Step 5:** `./gradlew check`, then commit: `Windows: file associations through Windows Settings`.

### Task 7: Linux: xdg-mime, mimeapps.list and shared-mime-info

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/LinuxMime.java` (pure text work)
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/LinuxFileAssociations.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/DefaultBrowser.java` (`run` moves to a package-private
  `static Optional<String> run(List<String> command, Duration timeout)` in a new `ToolProcess` class with the same
  behaviour and its descendant cleanup; `DefaultBrowser` calls it)
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/ToolProcess.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/platform/LinuxMimeTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/platform/LinuxFileAssociationsTest.java`
- Modify (if the check below finds mismatches): `file-types.tsv`

**Interfaces:**
- Consumes: Task 4's `Backend` and values. `FileTypeCatalog`.
- Produces:
  - `final class LinuxMime` (package-private, static methods):
    - `Optional<String> globType(List<String> globs2Texts, String extension)`: parses `weight:mime:pattern[:flags]`
      lines and matches `*.<ext>` ignoring case. The highest weight wins; on a tie, the earlier file wins.
    - `String withoutDefault(String mimeapps, String type, String desktopId)`: in `[Default Applications]`, removes
      `desktopId` from `type`'s list, and the whole line when the list empties. Every other byte (comments, groups,
      line ends) is kept.
    - `String withAddedAssociation(String mimeapps, String type, String desktopId)`: in `[Added Associations]`
      (creating the group at the end if missing), adds `desktopId` first in `type`'s list, once.
    - `SortedSet<String> declaredExtensions(String packageXml)` and `String packageXml(SortedSet<String> extensions)`:
      the shared-mime-info package for Jasper's own types. Each extension becomes
      `<mime-type type="text/x-jasper-<ext>"><sub-class-of type="text/plain"/><comment>.<ext> file</comment><glob pattern="*.<ext>"/></mime-type>`.
    - `Optional<String> desktopName(String desktopEntry)`: the `Name=` in `[Desktop Entry]`.
  - `final class LinuxFileAssociations implements FileAssociations.Backend`, built with
    `LinuxFileAssociations(FileTypeCatalog catalog, Map<String, String> environment, Path home, Tools tools)`.
    `Tools` is a package-private record of functions, not an interface:
    `record Tools(BiFunction<List<String>, Duration, Optional<String>> run, Predicate<String> onPath)`, with
    `static Tools system()` (`ToolProcess.run`, and a PATH search). Calls use 2 seconds, and 20 seconds for
    `update-mime-database`.
    - Folders follow the XDG Base Directory spec from `environment`: `XDG_DATA_HOME` (default `~/.local/share`),
      `XDG_DATA_DIRS` (default `/usr/local/share:/usr/share`), `XDG_CONFIG_HOME` (default `~/.config`).
    - `unavailable()`: `NO_XDG_MIME` unless `onPath("xdg-mime")`; `NOT_INSTALLED` unless
      `applications/jasper-workstation-Jasper.desktop` exists under one of the data folders.
    - `claimability(ext)`: the catalogue's Linux types, if any. An extension in the catalogue with an empty Linux
      column gives `NotClaimable(NOT_TEXT)`. Otherwise it uses `globType` over each data folder's `mime/globs2`: a
      text type (`text/*`, or the catalogue's `application/*` types) gives `Claimable(List.of(type), false)`; another
      type gives `NotClaimable(NOT_TEXT)`; none gives `Claimable(List.of("text/x-jasper-" + ext without dot), true)`.
    - `handler(type)`: `xdg-mime query default <type>`. Empty gives `None`; `jasper-workstation-Jasper.desktop` gives
      `Jasper`; else `Other(id, desktopName(entry found under the data folders) or id)`.
    - `claim`:
      1. For declared types, write `<data home>/mime/packages/jasper-workstation.xml` with the union, then run
         `update-mime-database <data home>/mime`, and add an `[Added Associations]` entry to `<config>/mimeapps.list`.
      2. Then run `xdg-mime default jasper-workstation-Jasper.desktop <types...>` once per extension.
      3. Exit 0 and a re-query naming Jasper give `Done`; otherwise `Failed(<what failed>)`.
    - `restore`: `Other` runs `xdg-mime default <id> <type>`; `None` rewrites `<config>/mimeapps.list` with
      `withoutDefault`. Both give `Done` when a re-query no longer names Jasper.
    - `forget(ext)`: removes the extension from the package file and reruns `update-mime-database`. If the package is
      then empty, it deletes the file.

- [ ] **Step 1: Write `LinuxMimeTest`**, including:

```java
@Test void removingADefaultKeepsEveryOtherLine() {
    String before = "# mine\r\n[Added Associations]\r\ntext/x-java=vim.desktop;\r\n\r\n[Default Applications]\r\n"
        + "text/x-java=jasper-workstation-Jasper.desktop;code.desktop;\r\ntext/plain=gedit.desktop;\r\n";
    assertThat(LinuxMime.withoutDefault(before, "text/x-java", "jasper-workstation-Jasper.desktop"))
        .isEqualTo(before.replace("text/x-java=jasper-workstation-Jasper.desktop;code.desktop;", "text/x-java=code.desktop;"));
    assertThat(LinuxMime.withoutDefault("[Default Applications]\ntext/x-java=jasper-workstation-Jasper.desktop;\nx=y;\n",
        "text/x-java", "jasper-workstation-Jasper.desktop")).isEqualTo("[Default Applications]\nx=y;\n");
    assertThat(LinuxMime.withoutDefault("[Added Associations]\na=b;\n", "text/x-java", "jasper-workstation-Jasper.desktop"))
        .as("no group: unchanged").isEqualTo("[Added Associations]\na=b;\n");
}
```

  Also cover:
  - `globType`: weights, case, flags, a tie across files;
  - `withAddedAssociation`: an existing list, a missing group, and no duplicates;
  - `packageXml` and `declaredExtensions` round-tripping, with `+` and `-` in extensions XML-safe;
  - `desktopName`: a `Name[de]=` ignored, and `Name=` outside `[Desktop Entry]` ignored.
- [ ] **Step 2: Write `LinuxFileAssociationsTest`** with `@TempDir` as home and data and config folders, and a fake
  `Tools` that records commands and answers scripted outputs. Cover every bullet under `LinuxFileAssociations`,
  including:
  - a claim of `.proto` (unknown) that writes the package, runs `update-mime-database`, adds the association and sets
    the default;
  - a restore of `None` that edits only the temp `mimeapps.list`;
  - `forget` of the last custom extension, which deletes the package file.

  Add one `@EnabledOnOs(OS.LINUX)` test that reads the real `/usr/share/mime/globs2` (skipped when it's missing). For
  each catalogue extension with a Linux column, it asserts that `globType` gives a type in that column, printing every
  mismatch.
- [ ] **Step 3:** `./gradlew :jasper-app:test --tests '*LinuxMimeTest' --tests '*LinuxFileAssociationsTest' --tests '*DefaultBrowserTest'`.
  Expected: compile FAIL. Implement per Interfaces and run again until it passes on the Mac.
- [ ] **Step 4: The real database check.** Run the Linux tests in the `jasper-ci-linux` Docker container, the way
  earlier Linux-only failures were reproduced (`--init`, UTF-8 locale, the image's shared-mime-info installed). Fix
  `file-types.tsv` for each mismatch it prints. Where the database gives an extension a non-text type (for example
  `.ts` as `video/mp2t`), empty that extension's Linux column. Record the container command and result in the commit
  body.
- [ ] **Step 5:** `./gradlew check`, then commit: `Linux: file associations through xdg-mime`.

### Task 8: Wiring, the end-to-end loop check, docs

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/FileAssociations.java` (the factory)
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java` (beside `defaultBrowser`, near
  line 390)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (field, setter, getter,
  `windowActivated`)
- Test: `jasper-app/src/test/java/dev/jasper/app/platform/FileAssociationsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java`
- Modify: `docs/app-architecture.md` (where `DefaultBrowser` is described), `docs/app-maintenance.md` (a short "Adding a
  file type to the catalogue" recipe: edit `file-types.tsv`, run the macOS harness and the Linux container check,
  `verifyPackagingSupport`), `docs/STATUS.md`, and this plan's status banner.

**Interfaces:**
- Consumes: everything above.
- Produces:
  - `public static FileAssociations forThisPlatform(String osName, AppDirs dirs, Map<String, String> environment,
    Path home, Consumer<URI> openSettings)`. The worker starts a virtual thread per task, named
    `jasper-file-associations`. macOS, Windows and Linux get their backend; any other OS gets `unsupported()`'s.
  - `JasperApplication.fileAssociations(FileAssociations)`, a setter like `defaultBrowser`, and
    `public FileAssociations fileAssociations()`, which returns `FileAssociations.unsupported()` until one is set.
    `windowActivated` calls `fileAssociations.refresh()`, beside `defaultBrowser.refresh()`.
  - `ApplicationBootstrap` passes `System.getProperty("os.name")`, `dirs`, `System.getenv()`,
    `Path.of(System.getProperty("user.home"))`, and an opener that calls `Desktop.getDesktop().browse(uri)`.

- [ ] **Step 1: Write the failing tests:**
  - `forThisPlatformPicksTheBackend` (in `FileAssociationsTest`): "Mac OS X" gives a Mac backend, "Windows 11" a
    Windows one, "Linux" a Linux one, and "FreeBSD" `UNSUPPORTED_SYSTEM`. The backend class is exposed through a
    package-private `backendForTests()`.
  - `theLoopEndsInTheJasperEditorOnce` (in `JasperApplicationPluginsTest`), the end-to-end check plan 1 left to this
    plan. A fixture editor plugin records `<file name>|<origin>` and declines `JASPER` opens, as the Editor does with
    Default editor *system*. A fake `ConfigEditor` desktop hands the file back: its edit action calls
    `application.filesOpened(List.of(file.toFile()))`. Then `openFileAt(file, 0, window, desktop)`. Assert the record
    file holds exactly `[Note.txt|JASPER, Note.txt|OUTSIDE]` after it settles, with no third line within 2 seconds.
    Reuse plan 1's `ORIGIN_EDITOR` fixture style.
- [ ] **Step 2:** Run both. Expected: FAIL (no factory; no hand-back without the wiring, or a missing method).
- [ ] **Step 3:** Implement per Interfaces. Run Step 1's tests. Expected: PASS.
- [ ] **Step 4: Docs.**
  - `app-architecture.md`: two sentences beside `DefaultBrowser`. `FileAssociations` (with its three backends,
    `file-types.tsv` and `file-associations.toml`) is what the Editor's File associations section will use. It never
    writes `UserChoice`.
  - The maintenance recipe.
  - The STATUS entry: plan 2 done, the deviations (a)-(c) and any catalogue changes Tasks 5 and 7 made.
  - The plan banner.
- [ ] **Step 5:** `./gradlew check`, then commit: `App: FileAssociations wired in; the loop check`.

## GUI checks for the user (after the series, batched)

These can't be headless, and they are the spec's builder checks:
- On a scratch macOS user account, list the content types whose handler changes before and after claiming Plain text,
  XML and JSON. If more change than were ticked, plan 3 drops that row on macOS.
- Claiming Shell changes no Terminal "run" binding.
- On Windows 10 22H2 and Windows 11 23H2+, the deep link opens Jasper's Default apps page.
- On GNOME and KDE, a claimed custom extension, such as `.proto`, opens in Jasper from the file manager.
