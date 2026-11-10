# Editor File Associations 1: Handed-In Files Open in the Jasper Editor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Status: not started (written 2026-11-10). Plan 1 of 3 for the spec below. Plan 2 adds the app's platform layer and
packaging declarations (`dev.jasper.app.platform.FileAssociations`); plan 3 adds the SDK file-association surface,
the testkit, the Editor's File associations section and `file_association_extensions`.

**Goal:** A file another program hands to Jasper (the macOS open-file and `file:` open-URI events, launch arguments,
a second launch, `jasper <path>`) always opens in the Jasper Editor, whatever Default editor is set to. Jasper's own
opens still follow Default editor.

**Architecture:** SDK 0.31.0 adds `dev.jasper.sdk.ui.FileOrigin` and a default four-argument
`FileEditor.edit(..., FileOrigin)` that delegates to the existing three-argument method. The app threads an origin
from `JasperApplication.openRequestedFile` (the one funnel for handed-in files) through `PluginRuntime` and
`FileEditors` to the provided editor. `JasperApplication` sits outside `dev.jasper.app.plugins` and may not name SDK
types, so `PluginRuntime` gets an app-side `Origin` enum, as it already has `Placement`. The Editor's `DefaultEditor`
implements `FileEditor` and sends `OUTSIDE` opens straight to the Jasper Editor.

**Tech Stack:** Java 25 (JBR 25 JCEF), Gradle wrapper, JUnit 5, AssertJ, the SDK testkit.

**Spec:** `docs/superpowers/specs/2026-11-10-editor-file-associations-design.md`, section "Where handed-in files open
(your decision)", the "A loop, or a file that ends up only revealed" anti-goal, and the Tests bullets "Handed-in
routing" and "The loop anti-goal".

## Global Constraints

- SDK version 0.31.0. New SDK members carry `@since 0.31.0`. Bundled plugins and docs declare `sdk = ">=0.31.0, <0.32"`.
- `FileEditor` stays a `@FunctionalInterface` whose one abstract method is the existing
  `edit(Path, OptionalInt, Optional<WindowOwner>)`. A plugin that implements only that method keeps working unchanged
  and gets every open, handed-in ones included.
- SDK types appear in the app only inside `dev.jasper.app.plugins` (`verifySdkArchitecture`).
- Handed-in means exactly the opens that go through `JasperApplication.openRequestedFile`. `openFileAt` (File › Open
  File…), `openClickedFile` (Cmd-click), `PluginRuntime.open(Path, Consumer)` (config files) and
  `Platform.openInEditor`/`openFile` (`HostedContext`) are `JASPER`.
- When the Jasper Editor declines a handed-in file (for example, larger than `max_file_mib`), the app's existing
  fallback, revealing the file, applies. It never falls through to the Default editor choice.
- Never launch the GUI. Commit messages end with the trailer block from the session's attribution reminder. Work on
  `claude/editor-file-associations`.
- After each task: `./gradlew check` green and the source-hygiene Python check from AGENTS.md clean on touched files.

## Review Focus

1. A third-party plugin whose `FileEditor` is a three-argument lambda gets handed-in opens exactly as before. Task 2
   pins this with `aLambdaEditorGetsEveryOriginThroughItsThreeArgumentEdit`.
2. A handed-in file the Jasper Editor declines (too large) is revealed, and VS Code or the custom command is never
   started. Task 4 pins this with `aHandedInFileTheJasperEditorDeclinesIsNotGivenToTheChoice`.
3. A handed-in file before the plugins start is still only revealed. The existing
   `aRequestedFilesDesktopFallbackOnlyReveals` must stay green unchanged (Task 3 Step 4 runs it).
4. Cmd-click and File › Open File… still follow Default editor. Task 3 pins this in `handedInFilesCarryTheOutsideOrigin`
   (`openFileAt` and `openClickedFile` record `JASPER`).
5. With Default editor *system*, a Jasper-side open returns false to the desktop, and the round trip comes back as
   `OUTSIDE` to the Jasper Editor once. Task 4 pins the plugin half
   (`withSystemAJasperSideOpenGoesToTheDesktopAndItsHandBackOpensHere`); Task 3 pins that requested files are
   `OUTSIDE`.

---

### Task 1: SDK 0.31.0 version bump

The bump alone, as `827ac12ba` ("SDK: 0.30.0") did for 0.30.0. That commit's file list is the checklist.

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java` (`VERSION = "0.31.0"`)
- Modify: every `sdk = ">=0.30.0, <0.31"` in bundled `plugins/*/src/main/resources/plugin.toml`, `docs/examples/external-plugin`
  (`plugin.toml`, `build.gradle.kts`, `README.md`), `jasper-sdk/README.md`, and the guides that 827ac12ba touched
  (`docs/plugin-authoring.md`, `docs/plugins.md`, `docs/sdk-icons.md`, `docs/looks.md`, `docs/creating-themes.md`,
  `docs/packaging.md`), plus any further hit of `git grep -n '0.30.0, <0.31'` outside dated records
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/VersionRangeTest.java` (0.30 becomes a former range),
  `PresentationSelectionTest.java` (future-range literal `>=0.32.0`), `KitCompatibilityTest.java`,
  `PluginStateStoreOptInTest.java`, `PresentationConformanceTest.java`, `jasper-app/src/test/java/dev/jasper/app/testsupport/LookFixtures.java`,
  `jasper-sdk/src/test/java/dev/jasper/sdk/CapabilitiesTest.java`: each pinned `0.30` becomes `0.31`, as in 827ac12ba
- Modify: `docs/sdk-reference.html` (regenerated), `docs/STATUS.md` (one line under a new "Editor file associations"
  entry: plan 1 started, SDK 0.31.0)

- [ ] **Step 1:** Change `JasperSdk.VERSION` to `"0.31.0"` and run `./gradlew :jasper-app:test --tests '*VersionRangeTest'`.
  Expected: FAIL, because the ranges still say `<0.31`.
- [ ] **Step 2:** Apply the replacements above. Dated records (`docs/superpowers/**`, STATUS entries other than the new
  one) keep their numbers.
- [ ] **Step 3:** `./gradlew sdkReference`, then `./gradlew check`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4:** Commit: `SDK: 0.31.0` with a body naming plan 1 Task 1 and "the version bump alone; FileOrigin
  follows in Task 2".

### Task 2: SDK `FileOrigin`, `FileEditor`'s four-argument edit, the testkit

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/FileOrigin.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/FileEditor.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java` (`edit` near line 2509,
  `editWith` near 2490)
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java`
- Modify: `docs/plugin-authoring.md` (the `FileEditor` bullet near line 1196 and the testkit table row that lists
  `edit(file, line, windowId)`), `docs/sdk-reference.html` (regenerated)

**Interfaces:**
- Produces:
  - `public enum FileOrigin { JASPER, OUTSIDE }` in `dev.jasper.sdk.ui`, `@since 0.31.0`. Javadoc: `JASPER` is an open
    Jasper or a plugin asked for (`Platform.openInEditor`, `Platform.openFile`, a Cmd-click, File › Open File…, a
    settings file); `OUTSIDE` is a file another program handed to Jasper (the system's open-file event, a launch's
    files, a second launch, the `jasper` command).
  - In `FileEditor`:
    `default boolean edit(Path file, OptionalInt line, Optional<WindowOwner> window, FileOrigin origin)`, `@since
    0.31.0`. It requires a non-null `origin` (`NullPointerException`), returns `edit(file, line, window)`, and its Javadoc
    says Jasper calls this one and an editor overrides it to tell the origins apart.
  - In `FakePluginHost`: `public boolean edit(Path file, OptionalInt line, Optional<UUID> windowId, FileOrigin origin)`,
    `@since 0.31.0`. The existing three-argument `edit` calls it with `FileOrigin.JASPER`. `editWith` gains the origin
    and calls the four-argument `FileEditor.edit`.

- [ ] **Step 1: Write the failing contract tests** in `PluginContractTest`, beside
  `providingTheEditorNeedsTheCapabilityAndOnePluginProvidesItAtATime`:

```java
@Test void theEditorHearsWhereAnOpenCameFrom() {
    var a = new AtomicReference<PluginContext>();
    h.start(info("test.a", Capabilities.EDITOR_PROVIDE), Set.of(), Set.of(), a::set);
    List<String> calls = new CopyOnWriteArrayList<>();
    h.ui(() -> a.get().platform().editor(new dev.jasper.sdk.ui.FileEditor() {
        @Override public boolean edit(java.nio.file.Path file, java.util.OptionalInt line, Optional<dev.jasper.sdk.WindowOwner> window) {
            calls.add("three"); return false;
        }
        @Override public boolean edit(java.nio.file.Path file, java.util.OptionalInt line, Optional<dev.jasper.sdk.WindowOwner> window,
                                      dev.jasper.sdk.ui.FileOrigin origin) {
            calls.add(origin.name()); return true;
        }
    }));
    java.nio.file.Path notes = HERE.resolve("notes.txt");
    assertThat(h.edit(notes, java.util.OptionalInt.empty(), Optional.empty())).isTrue();
    assertThat(h.edit(notes, java.util.OptionalInt.empty(), Optional.empty(), dev.jasper.sdk.ui.FileOrigin.OUTSIDE)).isTrue();
    assertThat(calls).as("the three-argument host call is a Jasper open").containsExactly("JASPER", "OUTSIDE");
}

@Test void aLambdaEditorGetsEveryOriginThroughItsThreeArgumentEdit() {
    var a = new AtomicReference<PluginContext>();
    h.start(info("test.a", Capabilities.EDITOR_PROVIDE), Set.of(), Set.of(), a::set);
    List<String> calls = new CopyOnWriteArrayList<>();
    h.ui(() -> a.get().platform().editor(recordingEditor(calls, true)));
    java.nio.file.Path notes = HERE.resolve("notes.txt");
    assertThat(h.edit(notes, java.util.OptionalInt.of(4), Optional.empty(), dev.jasper.sdk.ui.FileOrigin.OUTSIDE)).isTrue();
    assertThat(calls).containsExactly("notes.txt|4|-");
    assertThatNullPointerException().isThrownBy(() -> h.edit(notes, java.util.OptionalInt.empty(), Optional.empty(), null));
}
```

- [ ] **Step 2:** `./gradlew :jasper-sdk-testkit:test --tests '*PluginContractTest*'`. Expected: compile FAIL (no
  `FileOrigin`).
- [ ] **Step 3:** Add `FileOrigin`, the default method and the testkit overload per Interfaces. `FakePluginHost`'s
  four-argument `edit` requires a non-null `origin` before anything else.
- [ ] **Step 4:** Run Step 2's command again. Expected: PASS.
- [ ] **Step 5: Docs.** In `plugin-authoring.md`'s `FileEditor` bullet, add one sentence: Jasper calls
  `edit(file, line, window, origin)`, whose default calls the three-argument method; override it to open files
  `FileOrigin.OUTSIDE` hands in differently (the Editor opens them itself whatever Default editor says). Link the
  anchors `sdk-reference.html#dev.jasper.sdk.ui.FileOrigin` and
  `#dev.jasper.sdk.ui.FileEditor.edit(Path,OptionalInt,Optional,FileOrigin)`, after checking the generated page's
  exact anchor spelling. In the testkit table row, add `edit(file, line, windowId, origin)`. Run `./gradlew sdkReference`.
- [ ] **Step 6:** `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 7:** Commit: `SDK: FileOrigin and FileEditor's four-argument edit`.

### Task 3: The app passes the origin

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/FileEditors.java` (`edit`, `editFromWorker`, both
  `openFromWorker`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java` (the two public `open` methods near line
  687 and 703, private `openFromWorker` near 706)
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java` (lines ~212 and ~233 pass `JASPER`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (`openFile` near line 856,
  `openRequestedFile` near 851)
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/FileEditorsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java`
- Modify: `docs/sdk-architecture.md` (the `Platform.editor` paragraph near line 418)

**Interfaces:**
- Consumes: `FileOrigin`, the four-argument `FileEditor.edit` (Task 2).
- Produces:
  - `FileEditors.edit(Path, OptionalInt, Optional<UUID>, FileOrigin)`,
    `editFromWorker(Path, OptionalInt, Supplier<Optional<UUID>>, FileOrigin)` and
    `openFromWorker(Path, OptionalInt, Supplier<Optional<UUID>>, FileOrigin, Consumer<Path>)`. The existing signatures
    without an origin stay, delegating with `FileOrigin.JASPER`, so the other callers and tests don't change.
  - `PluginRuntime.Origin { JASPER, OUTSIDE }`: a public nested enum with the same Javadoc idea as `Placement` ("as
    the application states it; `dev.jasper.sdk.ui.FileOrigin` stays in this package"). Add
    `PluginRuntime.open(Path, OptionalInt, Optional<UUID>, Origin, Consumer<Path>)`; the existing four-argument `open`
    delegates with `Origin.JASPER`. The mapping to `FileOrigin` is private to `PluginRuntime`.
  - `JasperApplication.openFile(...)` gains a `PluginRuntime.Origin origin` parameter. `openRequestedFile` passes
    `OUTSIDE`; `openFileAt` and `openClickedFile` pass `JASPER`.

- [ ] **Step 1: Write the failing unit test** in `FileEditorsTest`:

```java
@Test void theOriginReachesTheEditorAndTheOldCallsAreJaspersOwn() {
    List<String> calls = new CopyOnWriteArrayList<>();
    editors.register("dev.x.a", new FileEditor() {
        @Override public boolean edit(Path file, OptionalInt line, Optional<WindowOwner> window) { calls.add("three"); return true; }
        @Override public boolean edit(Path file, OptionalInt line, Optional<WindowOwner> window, dev.jasper.sdk.ui.FileOrigin origin) {
            calls.add(origin.name()); return true;
        }
    }, FileEditorsTest::noWindow);
    editors.edit(NOTES, OptionalInt.empty(), Optional.empty());
    editors.edit(NOTES, OptionalInt.empty(), Optional.empty(), dev.jasper.sdk.ui.FileOrigin.OUTSIDE);
    assertThat(calls).containsExactly("JASPER", "OUTSIDE");
}
```

- [ ] **Step 2: Write the failing application test** in `JasperApplicationPluginsTest`. Add an `ORIGIN_EDITOR` fixture
  plugin source beside `LINE_EDITOR`. It is the same, except that the editor is an anonymous `FileEditor` overriding
  both methods: the four-argument one appends `<file name>|<origin>` to `data/edited`, and the three-argument one
  throws `AssertionError("Jasper calls the four-argument edit")`. Then:

```java
/** Plan 1 Task 3: openRequestedFile, the funnel for files other programs hand over, is OUTSIDE; Jasper's own opens are JASPER. */
@Test void handedInFilesCarryTheOutsideOrigin() throws Exception {
    AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
    Path dev = home.resolve("origin-plugin");
    PluginJars.build(dev, "origin.jar", PluginJars.descriptor("dev.example.origin", "1.0.0", "fix.origin.Main")
        + "capabilities = [\"editor.provide\"]\n", Map.of("fix.origin.Main", ORIGIN_EDITOR), List.of());
    Path handed = java.nio.file.Files.writeString(home.resolve("Handed.java"), "class Handed { }\n");
    Path picked = java.nio.file.Files.writeString(home.resolve("Picked.java"), "class Picked { }\n");
    Path clicked = java.nio.file.Files.writeString(home.resolve("Clicked.java"), "class Clicked { }\n");
    var terminated = new CountDownLatch(1);
    JasperApplication[] application = new JasperApplication[1];
    java.util.UUID window = java.util.UUID.randomUUID();
    edt(() -> {
        application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
        application[0].startPlugins(null, dev, false, dirs);
        application[0].openRequestedFile(handed, window);
        application[0].openFileAt(picked, 0, window);
        application[0].openClickedFile(clicked, 0, window);
    });
    assertThat(awaitLines(dirs.plugins().resolve("dev.example.origin/data/edited"), 3))
        .containsExactlyInAnyOrder("Handed.java|OUTSIDE", "Picked.java|JASPER", "Clicked.java|JASPER");
    edt(application[0]::quit);
    assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    ApplicationPluginCleanup.await(application[0]);
}
```

  (`awaitLines` is the file's existing helper. If its signature differs, use it as `jasperFileHtmlStillOpensInTheEditor`
  does.)
- [ ] **Step 3:** `./gradlew :jasper-app:test --tests '*FileEditorsTest' --tests '*JasperApplicationPluginsTest.handedInFilesCarryTheOutsideOrigin'`.
  Expected: compile FAIL.
- [ ] **Step 4:** Implement per Interfaces. `FileEditors.edit` calls `current.editor().edit(file, line, window, origin)`
  inside the existing `containment.attempt`. Run Step 3's command, then `./gradlew :jasper-app:test --tests '*JasperApplicationPluginsTest*' --tests '*FileEditorsTest'`.
  Expected: PASS, with `aRequestedFilesDesktopFallbackOnlyReveals` and `jasperFileHtmlStillOpensInTheEditor` unchanged and green.
- [ ] **Step 5: Docs.** In `sdk-architecture.md`'s `Platform.editor` paragraph, add one sentence:
  `JasperApplication.openRequestedFile` passes `PluginRuntime.Origin.OUTSIDE`, which reaches the editor as
  `FileOrigin.OUTSIDE`; every other open is `JASPER`.
- [ ] **Step 6:** `./gradlew check`. Expected: BUILD SUCCESSFUL (`verifySdkArchitecture` passes, because
  `JasperApplication` names only `PluginRuntime.Origin`).
- [ ] **Step 7:** Commit: `App: files other programs hand over reach the editor as OUTSIDE`.

### Task 4: The Editor opens handed-in files itself

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/external/DefaultEditor.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java:177`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/external/DefaultEditorTest.java`
- Modify: `docs/editor.md` ("Opening files" bullet near lines 47-49, the "Default editor" intro near 589-593, and the
  **System default app** bullet near 604-605), `docs/STATUS.md`

**Interfaces:**
- Consumes: `FileOrigin`, `FileEditor` (Task 2).
- Produces: `public final class DefaultEditor implements FileEditor`. The existing
  `edit(Path, OptionalInt, Optional<WindowOwner>)` keeps its body and gains `@Override`. Add
  `@Override public boolean edit(Path file, OptionalInt line, Optional<WindowOwner> window, FileOrigin origin)`:
  `OUTSIDE` returns `jasper.edit(target, line, window)` (the same normalised `target` as today); `JASPER` returns
  `edit(file, line, window)`. `EditorPlugin` registers `context.platform().editor(defaults)`.

- [ ] **Step 1: Write the failing tests** in `DefaultEditorTest` (the `opened`, `launched`, `host` fields and the
  `install`, `choose`, `detected` and `tab` helpers already exist):

```java
@Test void aHandedInFileOpensHereWhateverTheChoice() {
    install("nvim");
    install("code");
    for (String choice : List.of("jasper", "nvim", "code", "custom", "system")) {
        preference.set(new DefaultEditor.Preference(choice, "subl -w", true, true));
        opened.clear();
        DefaultEditor defaults = detected(MAC);
        assertThat(defaults.edit(file, OptionalInt.of(9), Optional.empty(), FileOrigin.OUTSIDE)).as(choice).isTrue();
        assertThat(opened).as(choice).containsExactly(new Opened(file, OptionalInt.of(9), Optional.empty()));
    }
    assertThat(launched).isEmpty();
    assertThat(host.openRequests()).isEmpty();
}

@Test void aJasperOpenStillFollowsTheChoice() {
    Path code = install("code");
    choose("code");
    DefaultEditor defaults = detected(MAC);
    assertThat(defaults.edit(file, OptionalInt.empty(), Optional.empty(), FileOrigin.JASPER)).isTrue();
    assertThat(launched).containsExactly(List.of(code.toString(), file.toString()));
    assertThat(opened).isEmpty();
}

@Test void aHandedInFileTheJasperEditorDeclinesIsNotGivenToTheChoice() {
    install("code");
    choose("code");
    DefaultEditor defaults = editor(MAC, Optional.of(client), (path, line, window) -> false);
    defaults.detect();
    drain();
    assertThat(defaults.edit(file, OptionalInt.empty(), Optional.empty(), FileOrigin.OUTSIDE)).isFalse();
    assertThat(launched).isEmpty();
}

@Test void withSystemAJasperSideOpenGoesToTheDesktopAndItsHandBackOpensHere() {
    choose("system");
    DefaultEditor defaults = detected(MAC);
    assertThat(defaults.edit(file, OptionalInt.empty(), Optional.empty(), FileOrigin.JASPER)).as("to the desktop").isFalse();
    assertThat(defaults.edit(file, OptionalInt.empty(), Optional.empty(), FileOrigin.OUTSIDE)).as("handed back").isTrue();
    assertThat(opened).hasSize(1);
}
```

  `editor(String, Optional<Path>, FileEditor)` is a new private overload of the test's `editor` helper that takes the
  Jasper opener; the existing two-argument helper calls it with the recording opener it uses today.
- [ ] **Step 2:** `./gradlew :jasper-plugin-editor:test --tests '*DefaultEditorTest'`. Expected: compile FAIL (no
  four-argument `edit` on `DefaultEditor`).
- [ ] **Step 3:** Implement per Interfaces. Run Step 2's command. Expected: PASS, every existing `DefaultEditorTest`
  test included.
- [ ] **Step 4: Docs, in `docs/editor.md`.**
  - The Opening files bullet: files a launch names, `jasper <file>`, and on macOS files opened with Jasper in Finder or
    dropped on its Dock icon open in this Editor, whatever Default editor says, in the most recently active window.
  - The Default editor intro: drop "the files a launch or Finder hands to Jasper (the link "Opening files" to #opening-files)"
    from the list.
  - The **System default app** bullet: drop the sentence "A file a launch or Finder hands to Jasper is shown in the file
    manager instead."

  In `docs/command-line.md`'s `jasper PATH...` row (near line 114), "Opens each file in the default editor, or reveals
  it … when no editor plugin runs or the default editor is **System default app**" becomes: opens each file in the
  Jasper Editor (linked to editor.md#opening-files), or reveals it in the file manager when no editor plugin runs. Add the plan's
  line to the STATUS.md entry from Task 1.
- [ ] **Step 5:** `./gradlew check`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 6:** Commit: `Editor: files handed to Jasper open in the Jasper Editor`.
