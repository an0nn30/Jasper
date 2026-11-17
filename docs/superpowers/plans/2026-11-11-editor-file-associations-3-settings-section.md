# Editor File Associations 3: SDK Surface and the Editor's Section — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Status: done (2026-11-17) on branch claude/editor-file-associations-3 (written 2026-11-11). Plan 3 of 3. Plans 1 (handed-in files open in the Jasper Editor) and 2 (the
platform layer, `dev.jasper.app.platform.FileAssociations`) are on main at 0a4ec9bb9.

Planned deviations, recorded as AGENTS.md asks:
- (a) The new SDK members carry `@since 0.31.0`, not 0.32.0. 0.31.0 (plan 1's bump) has never left this machine, so
  there is no released 0.31.0 for them to break.
- (b) Plugins need a new capability, `files.associate`, to use `Platform.fileAssociations()`. The Editor declares it.
- (c) The plan fixes every label and message (the "Copy" table below), which the spec left to the builder.
- (d) Task 3's `FileAssociationsBridge` is an instance class (open/facade/type/handler/unavailable/close), not a static
  `scripted(...)`, because the facade caches its availability answer.
- (e) Task 4's setting validator also requires a normalised value, and `withUser` drops duplicates.
- (f) Task 5: user rows have a per-row Remove context item and no list `deleteAction` (it cannot name a row under
  SelectionMode.NONE); an added extension is claimable only once Settings is applied ("Apply Settings to choose Jasper
  for <ext>"), so a claimed-then-cancelled extension cannot leak a declaration; strings added beyond the Copy table:
  Linux NoPrevious ("…choose one in your file manager"), macOS NOT_INSTALLED (the Windows line), accessible labels
  "Choose Jasper for <name>" and "Extension"; the NO_SYSTEM_TYPE text names the system.

**Goal:** The Editor's settings page gets a **File associations** section. It lists file types with their current
app, has ticks and **Make Jasper the Default** (**Choose in Windows Settings…** on Windows), **Restore Previous**, and
**Add Extension…**, with the user's extensions saved as `file_association_extensions`. All of it goes through a new
SDK surface, `Platform.fileAssociations()`.

**Architecture:**
- The SDK gets one interface, `dev.jasper.sdk.ui.FileAssociations`, with nested values that mirror the app facade's
  (minus Windows ProgIds and macOS bundle ids).
- Two real implementations: the app's `dev.jasper.app.plugins.HostedFileAssociations`, an adapter over the plan 2
  facade, and the testkit's fake, which is scripted from `FakePluginHost`.
- One contract suite runs against both.
- The Editor keeps the rows as product data (`FileTypeRows`), folds per-type answers into row states
  (`FileTypeRowState`), and draws the section with SDK nodes in `EditorSettingsPanel`.

**Tech Stack:** Java 25, the Jasper SDK and testkit, JUnit 5, AssertJ, Gradle (`sdkReference`).

**Spec:** `docs/superpowers/specs/2026-11-10-editor-file-associations-design.md`: "What the user sees", "Built-in
list", "Custom extensions", "SDK surface", "Docs", "Tests".

## Global Constraints

- SDK: new members `@since 0.31.0`; `JasperSdk.VERSION` stays `0.31.0`; the capability is
  `Capabilities.FILE_ASSOCIATIONS = "files.associate"` (added to `Capabilities.ALL`, last).
- SDK values never carry a bundle id or ProgId: `Handler.Other` has only a display `name`.
- SDK interfaces have two real implementations (app and testkit); no other new interface.
- Every future `FileAssociations` returns completes on the UI thread and never exceptionally; failures arrive as
  `Outcome.Failed`. `unavailable()` never blocks.
- The plugin presentation check applies: the Editor draws only with SDK nodes; no Swing in plugin code.
- Ticks are not saved. Only `file_association_extensions` is saved (Editor settings, staged like the page's other
  settings, applied with Settings' OK/Apply).
- Extensions: `.ext`, lower case, a leading dot then 1 to 16 characters from `[a-z0-9_+-]`; `.html` and `.htm` and
  any built-in row's extension are refused as user extensions.
- Wording is per platform: Finder on macOS, File Explorer and Windows Settings on Windows, "your file manager" on
  Linux. Every string comes from the Copy table.
- Never launch the GUI. Never call a real system claim or restore in a test. Commit trailers per the session's
  attribution reminder. `./gradlew check` green after every task; source-hygiene check clean; after the SDK tasks also
  `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`.

## Copy

| Key | Text |
|---|---|
| section title | `File associations` |
| intro (macOS) | `Choose the file types Finder opens with Jasper.` |
| intro (Windows) | `Choose the file types File Explorer opens with Jasper.` |
| intro (Linux) | `Choose the file types your file manager opens with Jasper.` |
| claim button (macOS, Linux) | `Make Jasper the Default` |
| claim button (Windows) | `Choose in Windows Settings…` |
| Windows note under the buttons | `Windows asks you to pick Jasper for each type in Settings › Default apps.` |
| restore button | `Restore Previous` |
| add field placeholder / button | `.ext` / `Add Extension` |
| row status: Jasper | `Jasper` |
| row status: one other app | `<name>` |
| row status: several other apps | `Several apps` |
| row status: none | `No default app` |
| row status: partly | `Partly Jasper's` |
| row status: checking / unknown | `Checking…` / `Unknown` |
| not claimable, NO_SYSTEM_TYPE | `macOS has no file type for <ext>` |
| not claimable, NOT_TEXT | `<system> treats <ext> as another kind of file` (`<system>`: macOS, Windows, Linux) |
| result Failed | `<system> refused: <detail>` |
| result InSettings | `Pick Jasper for <ext> in Windows Settings` |
| result NoPrevious (macOS) | `No app had this type before Jasper; choose one in Finder's Get Info` |
| result NoPrevious (Windows) | `No app had this type before Jasper; choose one in Windows Settings › Default apps` |
| result PreviousGone | `The app it had before is no longer installed` |
| add errors | invalid `Use a dot and up to 16 letters, digits, _, + or -`; built-in `<ext> is already in the list`; Browser-owned `<ext> opens in Jasper's browser` |
| unavailable DEVELOPMENT_RUN | `File associations work in an installed Jasper.` |
| unavailable NOT_INSTALLED (Windows) | `Install Jasper with its installer to choose it for file types.` |
| unavailable NOT_INSTALLED (Linux) | `Install Jasper's .deb or .rpm package to choose it for file types.` |
| unavailable NO_XDG_MIME | `Install xdg-utils to choose Jasper for file types.` |
| unavailable UNSUPPORTED_SYSTEM | `This system has no file associations Jasper can set.` |
| remove action (user rows) | `Remove` |

## Review Focus

1. **A page closed or a plugin stopped while a claim is in flight.** The late future must not touch a disposed view,
   or throw. The result simply goes nowhere. Task 5: `aResultAfterThePageClosesIsDropped`.
2. **A user extension that the system later reports as not claimable** (a catalogue change, or a newer OS). The row
   stays listed and removable, with its tick disabled and the reason shown. Task 5:
   `aUserRowThatCannotBeClaimedStaysRemovable`.
3. **A claim with nothing ticked, or only rows that are already Jasper's, not claimable or still checking.** The claim
   button is disabled, and a stray event calls nothing. Task 5: `theClaimButtonNeedsATickedClaimableRow`.
4. **`onChanged` firing while a status request is still pending.** The later answer wins, and no stale answer
   overwrites a newer one. Task 5: `aStaleStatusAnswerNeverOverwritesANewerOne`.
5. **A plugin without `files.associate`.** `Platform.fileAssociations()` throws `MissingCapabilityException` in both
   implementations. Task 2: the contract test `fileAssociationsNeedTheCapability`.

---

### Task 1: The SDK surface

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/FileAssociations.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Platform.java`,
  `jasper-sdk/src/main/java/dev/jasper/sdk/Capabilities.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/FileAssociationsValuesTest.java`,
  `jasper-sdk/src/test/java/dev/jasper/sdk/CapabilitiesTest.java`,
  `jasper-sdk/src/test/java/dev/jasper/sdk/terminal/TerminalValuesTest.java` (its `Capabilities.ALL` assertion)
- Modify: `docs/plugin-authoring.md` (the capabilities list and the Platform section, one paragraph),
  `docs/plugin-apis.md` if it lists Platform members, `tools/sdk-reference/guide/` pages that list capabilities,
  `docs/sdk-reference.html` (regenerated)

**Interfaces:**
- Produces: `public interface FileAssociations`, `@since 0.31.0`. Nested types:
  - `sealed interface Handler`, with `record Jasper()`, `record Other(String name)`, `record None()`,
    `record Unknown()`.
  - `record TypeState(String type, Handler handler, Optional<Handler> previous)`
  - `record FileTypeStatus(String extension, List<TypeState> types)`. `types` is copied, and is empty when the
    extension can't be claimed here.
  - `enum Unavailable { DEVELOPMENT_RUN, NOT_INSTALLED, NO_XDG_MIME, UNSUPPORTED_SYSTEM }`
  - `enum Refusal { INVALID, BROWSER_OWNED, NO_SYSTEM_TYPE, NOT_TEXT }`
  - `sealed interface Claimability`, with `record Claimable(List<String> types, boolean declares)` and
    `record NotClaimable(Refusal refusal)`.
  - `sealed interface Outcome`, with `Done()`, `InSettings()`, `NoPrevious()`, `PreviousGone()`, `Refused(Refusal)`
    and `Failed(String detail)`.
  - `record TypeResult(String extension, String type, Outcome outcome)`

  Methods (all on the UI thread, all throwing `IllegalStateException` elsewhere):
  - `Optional<Unavailable> unavailable()`, which never blocks;
  - `CompletableFuture<List<FileTypeStatus>> status(List<String> extensions)`;
  - `CompletableFuture<Claimability> claimability(String extension)`;
  - `CompletableFuture<List<TypeResult>> claim(List<String> extensions)`;
  - `CompletableFuture<List<TypeResult>> restore(List<String> extensions)`;
  - `CompletableFuture<Void> forget(String extension)`;
  - `Subscription onChanged(Runnable handler)`, whose handler runs on the UI thread, and whose registration ends with
    the plugin.

  The Javadoc states the facade's rules:
  - futures complete on the UI thread and never exceptionally;
  - `status(List.of())` stops the background re-checks;
  - a claim saves each type's previous app;
  - Restore hands back only types that are still Jasper's;
  - Windows answers `InSettings`.

  In `Platform`: `default FileAssociations fileAssociations()`, which throws `UnsupportedOperationException("File
  associations are unavailable")`, `@since 0.31.0`. Implementations throw `MissingCapabilityException` without
  `files.associate`.

  In `Capabilities`: `FILE_ASSOCIATIONS = "files.associate"`, `@since 0.31.0`, with Javadoc "Choosing Jasper as the
  system's app for file types, through `Platform.fileAssociations`".

- [ ] **Step 1: Write the failing tests.**
  - `FileAssociationsValuesTest` checks the record invariants:
    - null components throw NPE;
    - lists are copied (mutating the input changes nothing);
    - `Claimable` with empty `types` throws `IllegalArgumentException`;
    - `Failed` with a null detail throws NPE.
  - `CapabilitiesTest` asserts `FILE_ASSOCIATIONS` equals `"files.associate"`.
  - `TerminalValuesTest`'s `ALL` list ends with `"files.associate"`.
- [ ] **Step 2:** `./gradlew :jasper-sdk:test`. Expected: compile FAIL.
- [ ] **Step 3:** Implement per Interfaces.
- [ ] **Step 4:** Run Step 2's command. Expected: PASS.
- [ ] **Step 5: Docs.**
  - The capability row and one Platform paragraph in `plugin-authoring.md`, linking
    `sdk-reference.html#dev.jasper.sdk.ui.FileAssociations` after you've checked the generated anchor.
  - Update any guide page that lists every capability.
  - Run `./gradlew sdkReference`.
- [ ] **Step 6:** `./gradlew check`, then the external-plugin build from Global Constraints. Commit:
  `SDK: Platform.fileAssociations and the files.associate capability`.

### Task 2: The testkit fake and the contract

**Files:**
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java`,
  `FakePluginContext.java` (`platform().fileAssociations()`)
- Create: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeFileAssociations.java`
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java`,
  `PluginContractTest.java`, `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`
- Modify: `docs/plugin-authoring.md` (the testkit table's "Everything else" row), `jasper-sdk-testkit/README.md` if
  it lists the fake's setters, `docs/sdk-reference.html` (regenerated)

**Interfaces:**
- Consumes: Task 1's types.
- Produces, on `FakePluginHost`, each `@since 0.31.0`:
  - `setFileAssociationsUnavailable(Optional<FileAssociations.Unavailable> reason)`, default empty;
  - `setFileType(String extension, List<String> types)`: claimable with those types;
  - `setFileTypeRefused(String extension, FileAssociations.Refusal refusal)`. An extension set with neither is
    `NotClaimable(NO_SYSTEM_TYPE)`.
  - `setFileTypeHandler(String type, FileAssociations.Handler handler)`, default `None`;
  - `failFileTypeClaims(String type, String detail)`;
  - `setFileAssociationsInSettings(boolean windowsLike)`: claims and restores answer `InSettings` and change no
    handler;
  - `List<String> fileAssociationCalls()`, holding `claim:<ext>`, `restore:<ext>` and `forget:<ext>` in order;
  - `fileTypesChanged()`, which runs the `onChanged` handlers on the EDT.

  The fake's semantics are the facade's:
  - extensions are normalised (lower case, dot added; invalid gives `Refused(INVALID)`);
  - `.html` and `.htm` give `Refused(BROWSER_OWNED)`;
  - a claim saves each type's first previous app, unless it was already Jasper, and sets the handler to `Jasper` on
    `Done`;
  - a restore hands back only types that are Jasper's now: a saved `None` gives `NoPrevious`, otherwise `Done` with
    the previous handler set and the saved entry dropped;
  - `onChanged` runs after every claim or restore;
  - futures complete on the EDT.
- `ContractHarness` gets `fileAssociationsUnavailable(Optional<Unavailable>)`, `fileType(String, List<String>)` and
  `fileTypeHandler(String, Handler)`. Task 3 implements them for the app.

- [ ] **Step 1: Write the contract tests in `PluginContractTest`.** Each uses `h.ui(...)` and awaits futures as the
  file's other async tests do.
  - `fileAssociationsNeedTheCapability`: without `files.associate`, `MissingCapabilityException` naming it.
  - `unavailableIsReportedAndBlocksNothing`: `fileAssociationsUnavailable(Optional.of(NOT_INSTALLED))` makes
    `unavailable()` give it. A claim gives one `Failed` row per extension, whose detail starts with `"unavailable"`.
  - `aClaimSavesThePreviousAppAndARestoreHandsItBack`:
    - set up `fileType(".java", ["t.java"])` with handler `Other("Xcode")`;
    - claim `".java"` gives `[TypeResult(".java","t.java",Done)]`, and status shows `Jasper` with previous
      `Other("Xcode")`;
    - restore gives `Done`, and status shows `Other("Xcode")` with no previous.
  - `browserOwnedAndInvalidExtensionsAreRefused`: `.HTML` gives `Refused(BROWSER_OWNED)`, and `../x` gives
    `Refused(INVALID)`.
  - `onChangedRunsOnTheUiThreadAfterAClaimAndEndsWithThePlugin`: the handler runs once after a claim. After the plugin
    stops, a further claim from another plugin runs nothing.
  - `everyFutureCompletesOnTheUiThread`: records `SwingUtilities.isEventDispatchThread()` in `thenRun`.
- [ ] **Step 2:** `./gradlew :jasper-sdk-testkit:test --tests '*FakeContractTest*'`. Expected: FAIL. `AppContractTest`
  won't compile until Task 3, so stub its three new harness methods with `throw new UnsupportedOperationException()`
  and mark the six tests `@Disabled("Task 3")` in `AppContractTest` only, if it overrides per test. Otherwise exclude
  them with the file's existing mechanism.
- [ ] **Step 3:** Implement the fake per Interfaces.
- [ ] **Step 4:** Run Step 2's command. Expected: PASS.
- [ ] **Step 5:** Docs and `sdkReference`, then `./gradlew check` and the external-plugin build. Commit:
  `Testkit: a scripted FileAssociations fake and its contract`.

### Task 3: The app's adapter

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedFileAssociations.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java`, `HostedContext.java`,
  `PluginRuntime.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (wires it, next to
  `plugins.defaultBrowser(...)` near line 1685, and the facade's `onChanged` next to `fileAssociations(...)` near
  line 1233)
- Create (test source, owner package): `jasper-app/src/test/java/dev/jasper/app/platform/FileAssociationsBridge.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java` (the three harness methods; remove
  Task 2's `@Disabled`)
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/HostedFileAssociationsTest.java`

**Interfaces:**
- Consumes: plan 2's facade `dev.jasper.app.platform.FileAssociations` (`status`, `claimability`, `claim`,
  `restore`, `forget`, `unavailable`, `onChanged`), and Task 1's SDK types.
- Produces:
  - `final class HostedFileAssociations implements dev.jasper.sdk.ui.FileAssociations`, one per plugin, made in
    `HostedContext.platform().fileAssociations()` after `requireOpen()`, `requireUi` and the capability check.
    - It maps app values to SDK values one to one: `Other(id, name)` becomes `Other(name)`, the enums map by name,
      and so do the outcomes.
    - It completes each SDK future with `SwingUtilities.invokeLater` from the facade future's completion.
    - It drops a completion once the plugin has closed: the future stays incomplete, and nothing runs.
  - `PluginHost`:
    - `volatile Supplier<dev.jasper.app.platform.FileAssociations> fileAssociations = FileAssociations::unsupported`;
    - `onFileAssociationsChanged(Runnable)` and `fileAssociationsChanged()`, which mirror `onDefaultBrowserChanged`
      and `defaultBrowserChanged`, handlers in containment.
  - `PluginRuntime.fileAssociations(Supplier<FileAssociations>)` and `PluginRuntime.fileAssociationsChanged()`.
  - `JasperApplication`:
    - `plugins.fileAssociations(() -> fileAssociations)`;
    - its `fileAssociations(FileAssociations)` setter now also registers
      `associations.onChanged(() -> { if (fileAssociations == associations && plugins != null) plugins.fileAssociationsChanged(); })`.
  - `FileAssociationsBridge` (public, test-only, in `dev.jasper.app.platform`) has
    `public static FileAssociations scripted(Map<String, List<String>> types, Map<String, FileAssociations.Handler> handlers, Optional<FileAssociations.Unavailable> unavailable)`.
    It builds the facade over an in-memory `Backend` (`Runnable::run` worker, `PreviousHandlers` in a temp file the
    bridge deletes on exit). It also has setters to change handlers and unavailability later, for the harness.

- [ ] **Step 1: Write `HostedFileAssociationsTest`.**
  - The mapping of each value.
  - A completion after the plugin closed runs nothing.
  - Calls off the UI thread throw `IllegalStateException`.

  Then implement `AppContractTest`'s three harness methods with the bridge, and remove the `@Disabled`.
- [ ] **Step 2:** `./gradlew :jasper-app:test --tests '*HostedFileAssociationsTest' --tests '*AppContractTest*'`.
  Expected: FAIL.
- [ ] **Step 3:** Implement per Interfaces.
- [ ] **Step 4:** Run Step 2's command. Expected: PASS. The six contract tests pass for the app too.
- [ ] **Step 5:** `./gradlew check` (`verifySdkArchitecture`: SDK types only in `dev.jasper.app.plugins`), then
  commit: `App: Platform.fileAssociations over the platform layer`.

### Task 4: The Editor's rows, row states and setting

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/associations/FileTypeRows.java`,
  `FileTypeRowState.java`, `package-info.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettings.java` (declare the setting),
  `plugins/editor/src/main/resources/settings.toml` (its comment block), `plugins/editor/src/main/resources/plugin.toml`
  (`files.associate` added to `capabilities`)
- Modify: `gradle/plugin-architecture.gradle.kts` only if a new Editor package must be declared there
- Test: `plugins/editor/src/test/java/dev/jasper/editor/associations/FileTypeRowsTest.java`,
  `FileTypeRowStateTest.java`, and the Editor's manifest test if one pins `capabilities`

**Interfaces:**
- Produces:
  - `record FileTypeRows.Row(String id, String name, List<String> extensions, boolean user)`
  - `FileTypeRows.BUILT_IN`: these rows, in order (ids are lower-case names):

    | Row | Extensions |
    |---|---|
    | Java | `.java` |
    | Kotlin | `.kt .kts` |
    | C | `.c .h` |
    | C++ | `.cpp .cc .cxx .hpp .hh` |
    | Python | `.py` |
    | Go | `.go` |
    | Rust | `.rs` |
    | JavaScript | `.js .mjs` |
    | Shell | `.sh .zsh .bash` |
    | Markdown | `.md .markdown` |
    | JSON | `.json` |
    | YAML | `.yaml .yml` |
    | TOML | `.toml` |
    | XML | `.xml` |
    | Plain text | `.txt .log` |

  - `static List<Row> withUser(List<String> userExtensions)`: the built-in rows, then one row per user extension, in
    the order saved, with id `user:<ext>` and name `<ext>`.
  - `static Optional<String> addError(String typed, List<String> userExtensions)`. It returns the Copy table's add
    error, or empty when the extension may be added.
    - It normalises: lower case, and adds the dot.
    - An extension already among the user's own is the "already in the list" error.
  - `static String normalised(String typed)`
  - `sealed interface FileTypeRowState`, with `Checking()`, `Unavailable()`, `NotClaimable(Refusal refusal)`,
    `Jasper(boolean restorable)`, `Partly(boolean restorable)`, `Other(String name)`, `Several()`, `None()` and
    `Unknown()`, plus `static FileTypeRowState of(List<FileAssociations.FileTypeStatus> statuses, Map<String, FileAssociations.Refusal> refusals, boolean noneRestorable)`.
    `noneRestorable` is true on Linux.
    The folding rules:
    - all types of all claimable extensions Jasper's: `Jasper`;
    - some but not all: `Partly`;
    - otherwise one shared other-app name: `Other`;
    - several names: `Several`;
    - all `None`: `None`;
    - any `Unknown` and nothing Jasper's: `Unknown`;
    - no claimable extension: `NotClaimable` with the first refusal.

    `restorable` means some Jasper type has a saved previous app that isn't `None`. A saved `None` also counts when
    `noneRestorable` is true.
  - The setting: `file_association_extensions`, `SettingKind.stringList` validated by
    `FileTypeRows.addError(value, List.of()).isEmpty()`, default empty. Label `File association extensions`, section
    `File associations`. Add an accessor on `EditorSettings.Declared`. The `settings.toml` comment says: "Extra file
    extensions the File associations section lists, such as [".proto"]."

- [ ] **Step 1: Write the failing tests.**
  - The built-in table, verbatim.
  - `withUser` order and ids.
  - `addError` for `.JAVA` (built-in), `java` (built-in after normalising), `.html`, `.htm`, `.a b`, 17 characters,
    a user duplicate, and `.proto` (empty).
  - Every folding rule above, with a two-extension row (`.yaml`, `.yml`) whose types are shared.
  - `restorable` with `Other`, `None` on macOS, and `None` on Linux.
- [ ] **Step 2:** `./gradlew :jasper-plugin-editor:test --tests '*FileTypeRows*' --tests '*FileTypeRowState*'`.
  Expected: compile FAIL.
- [ ] **Step 3:** Implement. Declare the setting, add the capability to `plugin.toml`, and update the manifest test
  if it pins capabilities.
- [ ] **Step 4:** Run Step 2's command, then `./gradlew check` (`verifyPluginArchitecture` and the presentation
  check). Commit: `Editor: file-type rows, row states and file_association_extensions`.

### Task 5: The File associations section

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/ui/FileAssociationsSection.java` (builds the nodes and
  handles the section's events; the panel delegates to it)
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettingsPanel.java` (the section after "Default
  editor"; keywords add `file associations`, `default app`, `open with`), `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java`
  (passes `context.platform().fileAssociations()` and the OS name to the panel)
- Test: `plugins/editor/src/test/java/dev/jasper/editor/ui/FileAssociationsSectionTest.java`

**Interfaces:**
- Consumes: Tasks 1, 2 and 4.
- Produces:
  - `final class FileAssociationsSection`, built with
    `FileAssociationsSection(FileAssociations associations, String osName, Supplier<List<String>> userExtensions, Consumer<List<String>> stageUserExtensions, Runnable render)`.
  - `List<Nodes.Node> nodes(String query)`: the section's rows, which the panel wraps in its `section(...)`.
  - `boolean handle(UiEvent event)`, true when the event was the section's.
  - `void open()`, which asks `status` and the claimabilities and subscribes to `onChanged`.
  - `void close()`, which cancels the subscription, ignores late answers and calls `status(List.of())`.
- Layout (node ids prefixed `editor.associations`):
  1. A muted intro `Text`.
  2. Then either one unavailable `Text` (and nothing else), or:
     - an `ItemList` (kind `DOUBLE`, `SelectionMode.NONE`), one item per row:
       - title: the row name;
       - subtitle: the extensions joined by `, `, ` · `, then the row status or result text from the Copy table;
       - trailing: one compact `Toggle` (the tick; disabled for `NotClaimable`, `Checking` and `Unavailable`);
       - user rows carry the context action `Remove` and `deleteAction` `…:remove`.
     - A `Stack` of buttons: the claim button, then `Restore Previous`, then on Windows the note `Text`.
     - A `FormRow`: a `Field` (placeholder `.ext`, the add error as its validation error) and the `Add Extension`
       button.
- Behaviour:
  - Default ticks come from the state: ticked unless `Jasper`, `NotClaimable`, `Checking` or `Unavailable`. A tick the
    user changes is kept until the page closes, and is recomputed only for rows the user hasn't touched.
  - The claim button is enabled when at least one ticked row is claimable and not `Jasper`.
    - It calls `claim` with all extensions of the ticked rows that aren't wholly Jasper's, then `status`.
    - Results show in each row's subtitle until the next claim or restore. A row with any non-Done outcome shows the
      first such outcome's text.
  - Restore is enabled when a ticked row is `Jasper` or `Partly` and restorable. It calls `restore` with those rows'
    extensions.
  - Add stages `userExtensions + normalised` when `addError` is empty, then asks `claimability`, then `status` for
    the new row.
  - Remove stages the list without the row. The section never calls `forget`. Instead, `EditorPlugin`, which already
    reacts to applied Editor settings, compares the old and new `file_association_extensions`. It calls
    `context.platform().fileAssociations().forget(ext)` for each removed extension. This happens only when the plugin
    has the capability and `unavailable()` is empty. Cancelling Settings forgets nothing. Test it in
    `FileAssociationsSectionTest` or the Editor's settings-apply test, whichever already drives applied settings.
  - Every status answer carries a generation number. An answer older than the newest request is dropped.

- [ ] **Step 1: Write `FileAssociationsSectionTest`** against the testkit fake (Task 2's setters) and a recording
  panel stub. It covers:
  - the macOS, Windows and Linux intro, button and note copy;
  - each unavailable reason's single line;
  - the default ticks;
  - claim and restore calls and texts;
  - the add errors;
  - add, then remove, then apply (`forget` is called once), and add, then remove, then cancel (no `forget`);
  - the four Review Focus tests named there (`aResultAfterThePageClosesIsDropped`,
    `aUserRowThatCannotBeClaimedStaysRemovable`, `theClaimButtonNeedsATickedClaimableRow`,
    `aStaleStatusAnswerNeverOverwritesANewerOne`).
- [ ] **Step 2:** `./gradlew :jasper-plugin-editor:test --tests '*FileAssociationsSectionTest' --tests '*EditorSettingsPanel*'`.
  Expected: FAIL.
- [ ] **Step 3:** Implement. `EditorSettingsPanel.create` calls `open()` and `dispose` calls `close()`. `event`
  hands events to `handle` first.
- [ ] **Step 4:** Run Step 2's command, then `./gradlew check`. Commit: `Editor: the File associations section`.

### Task 6: User docs, status

**Files:**
- Modify: `docs/editor.md` (a new `## Making Jasper the default for file types` section after `## Default editor`,
  linked from the page's settings table), `docs/configuration.md` (the `file_association_extensions` row in the
  Editor settings table), `docs/STATUS.md`, this plan's banner
- Check: `docs/app-maintenance.md` "Tests that read the documentation", for any test that reads `editor.md` headings
  or the Editor settings table, and keep it green

**Interfaces:**
- Consumes: the behaviour of Tasks 1-5 and plan 2. The section describes what the system does on each platform:
  - **macOS:** the claim changes Finder's default at once; Get Info shows it.
  - **Windows:** Settings opens at Jasper's page and you pick Jasper for each type; an upgrade keeps your choices.
  - **Linux:** your `mimeapps.list` changes at once; custom extensions get a file type of Jasper's own.
  - **Restore Previous** hands back types that are still Jasper's.
  - When the section is unavailable, it says why.
  - Uninstalling leaves your choices as the system keeps them.
  - Installing makes Jasper the default for nothing.

- [ ] **Step 1:** Write the section in the voice of browser.md's "Making Jasper your default browser", one
  subsection per platform, using the Copy table's labels exactly. Add the configuration row.
- [ ] **Step 2:** `./gradlew check` (`AppDocumentationTest` link-checks). Commit:
  `Docs: making Jasper the default for file types`.

## GUI checks for the user (after the series, batched)

Plan 2's list, plus these:
- The section's layout in the three themes.
- Add, remove and claim round trips on each platform.
- The Windows flow from the button to Settings and back. The rows update when the window becomes active again.
