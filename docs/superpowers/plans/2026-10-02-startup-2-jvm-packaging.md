# Start-up Speed Plan 2: JVM and Packaging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the packaged app start faster with no application code: a trimmed runtime with a default CDS archive, the FlatLaf, JNA and pty4j native libraries shipped inside the app, and on macOS a JDK 25 AOT cache trained at release packaging and kept valid all the way into the DMG.

**Architecture:** Packaging decisions become pure helpers in a new `gradle/packaging-support.gradle` (runtime module list, bundled-native table, launcher-configuration parsing and editing, the AOT training launch), applied by `gradle/packaging.gradle` and tested by a portable Gradle verification task, `verifyPackagingSupport`, in `gradle/packaging-support-test.gradle` — the same idiom as `mac-launcher-identity.gradle` and its `verifyMacLauncherIdentity` fixtures. `packageApp` passes jpackage an explicit module list and `--generate-cds-archive`, copies the natives into `$APPDIR/native` and adds their launcher options; `trainAotCache` launches the image once, isolated like Plan 1's `startupBench`, and installs `jasper.aot`; `packageDist` (macOS) always trains and builds the DMG with `ditto` and `hdiutil`, because jpackage's DMG copy resets modification times and would invalidate the cache. `verifyPackage` checks everything headlessly, including a test-source probe (`BundledNativesProbe`) run with the image's own runtime.

**Tech Stack:** Gradle 9.7 Groovy script plugins, jpackage/jlink from the JetBrains Runtime 25 SDK, JDK 25 AOT cache (JEP 483/514 one-step `-XX:AOTCacheOutput`), FlatLaf 3.7, JNA 5.14.0, pty4j 0.13.10, macOS `codesign`/`ditto`/`hdiutil`, JUnit 5 + AssertJ for the probe test.

**Spec:** [Start-up speed design](../specs/2026-10-02-jasper-startup-speed-design.md), "Part 2 — JVM and packaging" (2a–2e), its Risks and Decisions log. It amends the [packaging design](../specs/2026-09-12-jasper-plan-4d-packaging-design.md) (jlink options, launcher configuration, runtime module set). Read both with this plan and [STATUS](../../STATUS.md).

**Builds on:** Plan 1 (Measure), exactly as named in the shared contract: `StartupTrace.TRACE_FILE_PROPERTY = "jasper.startupTrace"`, its JSON (`schema`, `pid`, `kind`, `settled`, `milestones` with `WINDOW_SHOWN`, `OUTPUT_PAINTED`, `PLUGINS_BOUND`, `jvm.aotCache` = `used`/`stale`/`none`), written atomically; and `tools/benchmarks/run-macos.sh <app> startup <revision> <output.json> [--mode cold|warm] [--runs N] [--prime N] [--real-home]` (Plan 1, Task 6), whose report lists each run's trace `jvm` status.

**Status:** Executed 2026-10-02/03 on `claude/startup-speed` with subagent-driven development (per-task implementer
and review, final whole-branch review). Second of five plans (1 → 2 → 4 → 3A → 3B). Final `./gradlew check`: BUILD
SUCCESSFUL, 7,991 tests (Plan 1's 7,990 plus the probe test), 0 failures, 0 errors, 6 skips, with
`verifyPackagingSupport` run. The task text below is unchanged; the result differs from it in these ways:

1. The runtime is 189 MB on disk (192 MB before), not the plan's ~80 MB. `lib/modules` fell from 146 to 96 MB, but jlink's
   default CDS archives add `classes.jsa` (31 MB) and `classes_nocoops.jsa` (32 MB); `lib/modules` stays uncompressed by
   design. The trimmed runtime has 41 modules (42 on Windows).
2. The AOT cache is 63,569,920 bytes (60.6 MiB), not about 67 MB. A training launch settles in about 3-4 s, and the JVM
   then writes the cache in about 1 s.
3. `runAotTraining(File, File, Long, Long)` takes boxed `Long`. With two primitive `long` closure parameters, Groovy
   4.0.32 (Gradle 9.7.0) generates bytecode that the verifier rejects.
4. Training's `finally` kills the launcher's descendants first (the cache-assembly JVM), then the launcher. The
   environment scrub is a pure, fixture-pinned helper, `aotTrainingEnvironment(Map, String)`. Both came from the Task 3
   review.
5. The three GUI steps were run by the controller after the user approved each batch in chat: Task 3 Step 7 (training),
   Task 4 Step 7 (`packageDist`) and Task 5 Step 9 (before/after benchmark).
6. Final-review fixes: the DMG detach in `packageDist` retries once, then forces (`detachMount(File, Closure, Closure)`
   in `gradle/packaging-support.gradle`, pinned by fixtures in `verifyPackagingSupport`), fails the build with the
   recovery command if the mount stays, and pre-detaches a stale mount left by a killed run; the probe test's wait is
   bounded (60 s, then the child is killed); documentation corrections.
7. Measured, from 10 cold runs each (medians): `WINDOW_SHOWN` went from 1908 to 1184 ms, and `OUTPUT_PAINTED` from 2727
   to 2057 ms. The AOT cache was used and CDS was on in 10/10 runs. See
   `docs/benchmarks/2026-10-02-startup-baseline.md`, "Part 2 — JVM and packaging".
8. Known and deferred:
   - `-Pjasper.aot=true` throws at configuration time on Windows and Linux, even for unrelated tasks. It is an explicit
     opt-in and fails loudly.
   - Windows packaging has not been run.
   - CI on Linux and Windows, and `.github/workflows/linux-packages.yml`, have not yet run this branch (that needs a
     push).
   - On a Mac with 128 GB or more of RAM, the cache stays valid. JDK 25 caps the ergonomic heap to keep compressed
     pointers, because the launcher sets no heap flags.

## Global Constraints

- 2a: "jlink's `--generate-cds-archive` restores `lib/server/classes.jsa` in the runtime: the fallback whenever the AOT cache is unusable."
- 2b: "Headless verification: the image's `java -XX:AOTMode=on -XX:AOTCache=… -cp <the configured class path> -version` must succeed, so a broken or stale cache fails the build instead of silently costing 600 ms."
- 2b: "**Policy (user decision):** `packageDist` (the release DMG) always trains; `packageApp` stays headless and untrained unless `-Pjasper.aot=true`. Training opens a window, so `AGENTS.md` gains a note: agents ask before running `packageDist` or `trainAotCache`."
- 2b: "JDK 25 caches only classes from the JDK and the application class path; plugin classes, from their own class loaders, are not cached." The training launch is "a temporary `jasper.home` with a fixture configuration whose terminal program is a fixture that prints a prompt and idles (no login shell), bundled plugins enabled, the window kept off-screen with no Dock icon, and `-Djasper.startupTrace` on" (see Spec deviations: the app has no off-screen position). The temporary home is set as Plan 1's bench sets it, with `-Duser.home=<dir>` (so `AppDirs` resolves `<dir>/.config/jasper`), and `JASPER_HOME` is removed from the launch environment.
- 2c: "No extraction, no checksum per launch, no temporary-folder litter, and a library macOS has already validated." "SQLite belongs to the Remote plugin and is handled in Part 3."
- 2d: "Kept: `java.se` (the full SE API for plugin authors), the crypto, zipfs, net, charsets, locale-data, accessibility and naming modules, and JFR, management and attach for field diagnostics." "`bin/java` stays (cache assembly needs it). The module list is documented as the runtime plugins may rely on."
- 2e: "Only measured winners; G1 and tiered compilation stay." The only new JVM flag is `-XX:AOTCache`; `verifyPackage` rejects any other.
- "Not in Part 2: AOT training on Windows (Windows gets CDS, bundled native libraries and the trimmed runtime); de-duplicating plugin jars."
- No application code: nothing under `jasper-app/src/main`, `jasper-terminal`, `jasper-buddy`, `jasper-sdk` or `plugins/` changes. The only Java added is a test-source probe and its test.
- Agents never launch the GUI. `trainAotCache`, `packageDist` on macOS and any task with `-Pjasper.aot=true` open a Jasper window for a few seconds: **ask the user before each such run**. `packageApp`, `verifyPackage` and `check` stay headless.
- `check` never depends on packaging. `verifyPackagingSupport` (pure fixtures, all three CI hosts) and `BundledNativesProbeTest` (macOS only) join `check`.
- Use `./gradlew`, never a system Gradle; JBR 25 toolchain. Source hygiene per AGENTS.md (escapes, no raw control or private-use characters). MVP: no compatibility shims. No interface without two real implementations (none is added).
- Commit messages end with a blank line and exactly:
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Review Focus

1. **A copy that changes a jar's modification time** (jpackage's own DMG step, `cp -R`, a sync tool): the JVM then drops the AOT cache *and* the default CDS archive, so the app starts slower than an untrained one. Expected: the released DMG's copy keeps every time and passes `-XX:AOTMode=on`; a `cp -R` copy keeps working and reports the cache as stale. Task 4 pins this (`modificationTimeMismatches` fixtures and the mounted-DMG check); Task 3 adds the headless `cp -R` demonstration.
2. **A training launch that settles only through Plan 1's 10-second cap** (the stand-in shell printed nothing, a plugin hung) or whose trace belongs to another process. Expected: training fails instead of shipping a cache that misses the window, paint or plugin classes. Task 3 pins this (`trainingTraceProblem` fixtures and the stand-in launcher that settles without `OUTPUT_PAINTED`).
3. **Paths with spaces or quotes** (a checkout under "My Projects", an app in "/Applications/My Apps"). Expected: every JVM option arrives whole through `JAVA_TOOL_OPTIONS` and `$APPDIR`. Task 3 pins the quoting round trip through a `/bin/sh` stand-in in a directory with a space; Task 2 pins `$APPDIR` expansion with a space.
4. **A bundled plugin or library that needs a JDK module the trimmed runtime lacks.** Expected: the build fails, not a `NoClassDefFoundError` in the field. Task 1 pins this (`jdeps` over the application and every bundled plugin in `verifyPackage`, and the docs-equals-build module list check in `verifyPackagingSupport`).
5. **A FlatLaf, JNA or pty4j upgrade that renames its native resource or ignores the property.** Expected: `check` (macOS) or `verifyPackage` fails instead of the app silently extracting again. Task 2 pins this (`BundledNativesProbeTest`, byte comparison with the jar entries, and the `-Xlog:library` probe in `verifyPackage`).

---

## Findings that shape this plan (verified 2026-10-02 without Gradle or a window)

- **Current runtime:** 192 MB, 65 modules, no `lib/server/classes.jsa`, `lib/modules` uncompressed (jlink default). jpackage got `--jlink-options '--strip-debug --no-man-pages --no-header-files --bind-services'`; `--bind-services` binds every `ToolProvider` and `javax.tools` provider, which is why javac, jshell, jlink, jpackage, javadoc and jdeps are in the runtime, plus JBR's `jcef`, `jogl.all`, `gluegen.rt`. Dropping it is what trims the runtime.
- **jdeps** (`--print-module-deps`) over the packaged class path: `java.base,java.desktop,java.logging,java.prefs,jdk.management`; bundled plugins: Remote `java.desktop,java.management,java.naming,java.rmi,java.security.jgss,java.sql`; File Server `java.desktop,java.naming,java.security.jgss,java.security.sasl,java.sql,jdk.httpserver`; Vault `java.management,java.naming,java.sql`; Editor, History, Snippets, Sample `java.desktop`. `jdk.httpserver` is used (`plugins/fileserver/.../engine/http`).
- **Module closure** of the chosen roots (`java --describe-module`): `java.se` requires the 20 Java SE modules; `jdk.attach` requires `jdk.internal.jvmstat`; every other root requires only modules already listed. Total 41 (42 on Windows with `jdk.crypto.mscapi`). `jdk.crypto.ec` is an empty, deprecated-for-removal module since JDK 22 and is omitted.
- **FlatLaf 3.7** (`FlatNativeLibrary.createNativeLibrary`, javap): with `flatlaf.nativeLibraryPath=<dir>` it loads `new File(dir, System.mapLibraryName("flatlaf-macos-arm64"))`, i.e. `<dir>/libflatlaf-macos-arm64.dylib` (`flatlaf-windows-x86_64.dll` on Windows), via `System.load`. `FlatNativeMacLibrary.isLoaded()` / `FlatNativeWindowsLibrary.isLoaded()` (public static) trigger it and **work headless** (checked with a scratch probe and `-Djava.awt.headless=true`).
- **JNA 5.14** (`Native.loadNativeDispatchLibrary`): tries each `jna.boot.library.path` entry for `libjnidispatch.jnilib` (then `.dylib`), sets `jnidispatch.path`; `jna.nosys` defaults to `true`; `jna.noclasspath`/`jna.nounpack` would forbid extraction. These properties are process-wide and apply to any JNA a plugin bundles, so this plan sets only `jna.boot.library.path` and `jna.nosys` (Spec deviation 4). On macOS JNA's temp folder is `~/Library/Caches/JNA/temp` unless `jna.tmpdir`.
- **pty4j 0.13.10** (`PtyUtil.resolveNativeFile`): with `pty4j.preferred.native.folder=<dir>` (an existing directory) it resolves `<dir>/darwin/<name>` on macOS, `<dir>/win/<JNA ARCH>/<name>` (`win/x86-64`) on Windows, `<dir>/linux/<ARCH>/<name>`; otherwise it extracts with MD5 checks into `pty4j.tmpdir` or `java.io.tmpdir`. Jasper starts PTYs with `setUseWinConPty(true)` on Windows (`PtyChild.builder`). libpty is opened by JNA's own `dlopen`, so `-Xlog:library` does not list it.
- **Scratch probe** (JBR 25 SDK, headless, these exact properties pointing at a folder of files copied from the jars): `-Xlog:library` showed `Loaded library <folder>/libjnidispatch.jnilib` and `<folder>/libflatlaf-macos-arm64.dylib`, pty4j started `/usr/bin/true` (exit 0) with the folder's spawn helper, and the temp folder stayed empty.
- **Signatures:** the jar entries are already signed and verify with `codesign --verify --strict`: FlatLaf's by FormDev (Sectigo), JNA's ad-hoc linker-signed, pty4j's `libpty.dylib` and spawn helper by JetBrains (team `2ZEFAR8TH3`, universal). Copying them byte for byte after jpackage keeps those signatures; the outer ad-hoc re-sign seals them as resources, like `libjasper-notifications.dylib` today.
- **Linux:** FlatLaf's Linux library links `libgtk-3.so.0` (ELF `NEEDED`). Bundling it would add GTK 3 to the `.rpm` requirements (`rpmLibraryRequirements`) and to `check-install.sh`'s `ldd` check in minimal containers. JNA's and pty4j's Linux libraries need only libc.
- **AOT assembly child** (`jdk.internal.misc.CDS$ProcessLauncher.execWithJavaToolOptions`, from the runtime's `lib/modules`): the JVM starts `bin/java` with `inheritIO()`, *replaces* `JAVA_TOOL_OPTIONS` with its own quoted argument list and removes `_JAVA_OPTIONS` and `CLASSPATH`. So a training launch may carry its options in `JAVA_TOOL_OPTIONS`, and the child's output lands in the training log. The JDK quotes a whitespace or `"` character inside single quotes and a `'` inside double quotes; `javaToolOptions` mirrors that.
- **jpackage `--type dmg`** (`MacDmgPackager`, `PackagingPipeline.copyAppImage`, javap of `jdk.jpackage` 25): copies the app image with `Files.copy(…, NOFOLLOW_LINKS)` — no `COPY_ATTRIBUTES` — before `hdiutil create -srcfolder`; JDK 25's `BsdFileSystem.copyFile` keeps times only with `COPY_ATTRIBUTES` (clonefile). Every jar in a jpackage DMG therefore gets a new modification time, which invalidates the AOT cache (the spec's Findings: "If any jar's modification time changes (plain `cp -R`), the JVM logs an error and runs without the cache and without the default CDS archive"). Hence Task 4.
- **Launch facts:** the jpackage launcher sets `jpackage.app-path` (`ApplicationIcon.installTaskbarIcon` relies on it), so training runs the real `Contents/MacOS/Jasper`, not `bin/java`. The app has **no** window-position setting (`TerminalWindow` uses `setLocationByPlatform(true)`). Bundled plugins need no consent and are enabled by default (`PluginStateStore` default `enabled = true`). The shell settings are `terminal.shell.program` and `terminal.shell.args`; residency is `system.run_in_background` (default `false`). With `-Duser.home=<dir>` and no `jasper.home`/`JASPER_HOME`, the config is `<dir>/.config/jasper/config.toml` on macOS (`AppDirs.resolve`).
- **Docs test:** `AppDocumentationTest` checks every local link in `docs/**/*.md` (this plan included), outside fenced code. This plan therefore links only to files that exist now.

## Spec deviations (decided here; reviewers may reject)

1. **No off-screen window.** The app cannot place its window off-screen without application code, which Part 2 rules out. Training uses `-Dapple.awt.UIElement=true` (no Dock icon, no menu bar, not in Cmd+Tab) and accepts a Jasper window at the platform's default position for a few seconds; the stand-in shell swallows anything typed into it. Part 4's off-screen rehearsal could later replace this.
2. **jpackage's DMG step is replaced** by `ditto` + `hdiutil create`, plus a read-only, Finder-hidden mount that checks the installed copy. The DMG loses jpackage's default background picture and icon layout; it keeps `Jasper.app` and an `Applications` link.
3. **One native folder:** all bundled natives go to `$APPDIR/native` (FlatLaf and JNA at its top, pty4j's in its `darwin/` or `win/x86-64/` subfolder), so every property points at one place; the spec wrote `$APPDIR` for FlatLaf.
4. **JNA options:** only `jna.boot.library.path` and `jna.nosys`, not `jna.noclasspath`/`jna.nounpack` (process-wide; a plugin's own JNA must still be able to extract its copy). The app's JNA finds the bundled file first; `verifyPackage` proves it.
5. **Module list details:** `java.smartcardio` kept (a crypto provider, SunPCSC); `jdk.crypto.mscapi` added on Windows (certificate stores); `jdk.crypto.ec` omitted (empty); dropped beyond the spec's list: `jdk.dynalink`, `jdk.jartool`, `jdk.xml.dom`, `jdk.jsobject`, `jdk.nio.mapmode`, `jdk.sctp`, `jdk.internal.md`.
6. **Linux** is not in the spec. It gets the trimmed runtime and CDS (shared jlink options) but keeps native extraction (GTK 3 dependency, above).

## File map

| File | Change | Responsibility |
|---|---|---|
| `gradle/packaging-support.gradle` | Create (Task 1), append (Tasks 2–4) | Pure helpers: `runtimeRootModules`, `runtimeModules`, `jlinkOptions`, `bundledNatives`, `bundledNativeJvmOptions`, `launcherClasspath`, `launcherJavaOptions`, `loadedLibraries`, `aotCacheOption`, `withJavaOption`, `javaToolOptions`, `aotTrainingShell`, `aotTrainingConfig`, `trainingTraceProblem`, `runAotTraining`, `aotCheckCommand`, `modificationTimeMismatches` |
| `gradle/packaging-support-test.gradle` | Create (Task 1), append (Tasks 2–4) | `verifyPackagingSupport` (in `check`); passes `jasper.bundledNativeOptions` to `:jasper-app:test` |
| `gradle/packaging.gradle` | Modify (Tasks 1–4) | jpackage arguments, native install, `verifyAotCache`, `trainAotCache`, `-Pjasper.aot`, `verifyPackage` checks, macOS `packageDist` |
| `jasper-app/src/test/java/dev/jasper/app/architecture/BundledNativesProbe.java` | Create (Task 2) | Headless main run by `verifyPackage` against the image |
| `jasper-app/src/test/java/dev/jasper/app/architecture/BundledNativesProbeTest.java` | Create (Task 2) | macOS-only test of the probe with the packaged options |
| `.github/workflows/linux-packages.yml` | Modify (Task 1) | Run the Linux packaging workflow when the helpers change |
| `packaging/README.txt` | Modify (Task 4) | Tell users how to copy the app without losing the cache |
| `docs/plugin-authoring.md` | Modify (Tasks 1, 5) | "The Java runtime": module list plugins may rely on, JNA/pty4j note |
| `AGENTS.md`, `README.md`, `docs/packaging.md`, `docs/app-maintenance.md`, `docs/diagnostics.md`, `docs/benchmarks.md`, `jasper-sdk/README.md`, `docs/STATUS.md` | Modify (Task 5) | Policy note, packaging guide, maintenance recipe, diagnostics, status |

All Gradle helper closures are project extra properties (`ext.<name>`), as `macLauncherWithIdentity` is, so `packaging.gradle` and the test script can call them. Groovy needs every `import` before the first statement, so Task 1 writes all imports the later tasks use. Line numbers in the tasks refer to `gradle/packaging.gradle` at `3b1a1544`; every edit shifts them, so locate each anchor by the quoted text next to its number.

---

### Task 1: Trimmed runtime with a default CDS archive (2a, 2d)

**Files:**
- Create: `gradle/packaging-support.gradle`
- Create: `gradle/packaging-support-test.gradle`
- Modify: `gradle/packaging.gradle` (apply lines after line 12; `packageApp` inputs and jpackage arguments, lines 201–224; `verifyPackage`, after lines 321 and 433)
- Modify: `docs/plugin-authoring.md` (new section before `## Distributing a plugin`)
- Modify: `.github/workflows/linux-packages.yml` (both `paths:` lists)

**Interfaces:**
- Consumes: `nativeContext()` (`[version, host: [os, architecture], launcher, sdkHome, jpackage]`), `executeNative(List<String>, String)` → `[stdout, stderr]`, `releaseText`, `runtimeHome`, `appDir` inside `verifyPackage`.
- Produces: `ext.runtimeRootModules(String os) → List<String>`, `ext.runtimeModules(String os) → List<String>` (sorted), `ext.jlinkOptions` (`String`); task `:jasper-app:verifyPackagingSupport` (in `check`, other tasks append `doLast` blocks through the script variable `verifySupport`); the `runtime-modules` block in `docs/plugin-authoring.md`; `verifyPackage` log line `Runtime: <n> modules, <m> MB, default CDS archive <k> MB`.

- [ ] **Step 0: Preserve the before image for the final comparison** (headless; `packageApp` opens no window)

```bash
./gradlew :jasper-app:packageApp
mkdir -p "$HOME/jasper-bench/startup-part2/before"
rm -rf "$HOME/jasper-bench/startup-part2/before/Jasper.app"
ditto jasper-app/build/packaging/image/Jasper.app "$HOME/jasper-bench/startup-part2/before/Jasper.app"
git rev-parse --short HEAD > "$HOME/jasper-bench/startup-part2/before/REVISION"
du -sh "$HOME/jasper-bench/startup-part2/before/Jasper.app/Contents/runtime"
```

Expected: `BUILD SUCCESSFUL`; `du` prints about `192M`. Nothing to commit.

- [ ] **Step 1: Write the failing fixture test.** Create `gradle/packaging-support-test.gradle`:

```groovy
// Portable fixtures for gradle/packaging-support.gradle, in the style of mac-launcher-identity-test.gradle: no
// jpackage, window, signing identity or network. Each plan task adds its own doLast block through verifySupport.
def verifySupport = tasks.register('verifyPackagingSupport') {
    group = 'verification'
    description = 'Checks the packaging helpers with fixtures: runtime modules, bundled natives, launcher configuration and AOT training.'
    doLast {
        List<String> roots = runtimeRootModules('macos')
        assert roots.containsAll(['java.se', 'jdk.httpserver', 'jdk.unsupported', 'jdk.jfr', 'jdk.attach', 'jdk.zipfs'])
        assert roots.intersect(['jcef', 'jogl.all', 'gluegen.rt', 'jdk.compiler', 'jdk.jshell', 'jdk.jlink', 'jdk.jpackage',
            'jdk.jdi', 'jdk.jdwp.agent', 'jdk.incubator.vector', 'jdk.dynalink']).isEmpty()
        assert runtimeRootModules('linux') == roots
        assert runtimeRootModules('windows') == roots + ['jdk.crypto.mscapi']
        List<String> modules = runtimeModules('macos')
        assert modules == modules.toSorted() && modules == modules.toUnique() && modules.size() == 41
        assert modules.containsAll(roots + ['java.base', 'java.desktop', 'java.sql', 'jdk.internal.jvmstat'])
        assert runtimeModules('linux') == modules
        assert runtimeModules('windows') == (modules + ['jdk.crypto.mscapi']).toSorted()
        assert jlinkOptions.tokenize(' ').containsAll(['--generate-cds-archive', '--strip-debug'])
        assert !jlinkOptions.contains('--bind-services') && !jlinkOptions.contains('--compress') &&
            !jlinkOptions.contains('--strip-native-commands')
        // The list plugin authors may rely on is the list the build ships.
        String guide = rootProject.file('docs/plugin-authoring.md').getText('UTF-8')
        def block = guide =~ /(?s)<!-- runtime-modules:start -->(.*?)<!-- runtime-modules:end -->/
        assert block.find() : 'docs/plugin-authoring.md has no runtime-modules block'
        assert (block.group(1) =~ /`([a-z][a-z0-9.]*)`/).collect { it[1] } == modules :
            'docs/plugin-authoring.md lists other runtime modules than gradle/packaging-support.gradle'
        logger.lifecycle('Verified the runtime module list')
    }
}
tasks.named('check') { dependsOn(verifySupport) }
```

In `gradle/packaging.gradle`, after line 12 (`apply from: rootProject.file('gradle/mac-launcher-identity-test.gradle')`), add:

```groovy
apply from: rootProject.file('gradle/packaging-support-test.gradle')
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :jasper-app:verifyPackagingSupport`
Expected: FAIL, `Could not get unknown property 'runtimeRootModules'`.

- [ ] **Step 3: Implement the module helpers.** Create `gradle/packaging-support.gradle`:

```groovy
import groovy.json.JsonException
import groovy.json.JsonSlurper
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.concurrent.TimeUnit

// What packaging decides, as pure helpers: the runtime's modules, the native libraries copied into the app, the
// launcher configuration and the AOT training launch. gradle/packaging.gradle applies this file;
// gradle/packaging-support-test.gradle checks it with fixtures (verifyPackagingSupport, part of check). The imports
// serve helpers further down: Groovy accepts imports only before the first statement.

// The Java runtime packaged Jasper ships; docs/plugin-authoring.md ("The Java runtime") lists it for plugin authors.
// jpackage's default for a class-path application is every module, and --bind-services then also bound javac,
// jshell, jlink, jpackage and the JetBrains Runtime's JCEF and JOGL (192 MB). These roots keep all of Java SE for
// plugins, the providers a desktop application needs (charsets, locale data, ZIP file system, PKCS#11, smart cards,
// Kerberos, JAAS, DNS and RMI naming, socket options, the JDK HTTP server that File Server uses, sun.misc.Unsafe and
// Swing interop) and the field diagnostics (JFR, JMX, attach). Windows adds its certificate stores. jdk.crypto.ec is
// left out: it has been empty since JDK 22. jlink adds what the roots require.
ext.runtimeRootModules = { String os ->
    List<String> roots = ['java.se', 'java.smartcardio', 'jdk.accessibility', 'jdk.attach', 'jdk.charsets',
        'jdk.crypto.cryptoki', 'jdk.httpserver', 'jdk.jfr', 'jdk.localedata', 'jdk.management', 'jdk.management.agent',
        'jdk.management.jfr', 'jdk.naming.dns', 'jdk.naming.rmi', 'jdk.net', 'jdk.security.auth', 'jdk.security.jgss',
        'jdk.unsupported', 'jdk.unsupported.desktop', 'jdk.zipfs']
    os == 'windows' ? roots + ['jdk.crypto.mscapi'] : roots
}

// Every module of the packaged runtime, sorted: the roots and what they require (java.se's Java SE modules and
// jdk.attach's jvmstat support). verifyPackage compares this with the MODULES line of the runtime's release file.
ext.runtimeModules = { String os ->
    (runtimeRootModules(os) + ['java.base', 'java.compiler', 'java.datatransfer', 'java.desktop', 'java.instrument',
        'java.logging', 'java.management', 'java.management.rmi', 'java.naming', 'java.net.http', 'java.prefs',
        'java.rmi', 'java.scripting', 'java.security.jgss', 'java.security.sasl', 'java.sql', 'java.sql.rowset',
        'java.transaction.xa', 'java.xml', 'java.xml.crypto', 'jdk.internal.jvmstat']).toSorted()
}

// No --bind-services: the roots name every provider Jasper wants. --generate-cds-archive writes the runtime's default
// CDS archive (lib/server/classes.jsa), the fallback whenever the AOT cache is unusable. No --compress: jlink's
// default leaves lib/modules uncompressed, which loads classes faster. No --strip-native-commands: bin/java stays,
// because the JVM runs it to assemble the AOT cache at the end of a training launch.
ext.jlinkOptions = '--strip-debug --no-man-pages --no-header-files --generate-cds-archive'
```

In `gradle/packaging.gradle`, insert this line directly **above** the test apply added in Step 1:

```groovy
apply from: rootProject.file('gradle/packaging-support.gradle')
```

In `docs/plugin-authoring.md`, insert before the line `## Distributing a plugin`:

```markdown
## The Java runtime

Packaged Jasper runs on its own trimmed JetBrains Runtime 25, not on every JDK module. The runtime has exactly these
modules, and a plugin may rely on them; `verifyPackage` checks every packaged runtime against this list:

<!-- runtime-modules:start -->
`java.base`, `java.compiler`, `java.datatransfer`, `java.desktop`, `java.instrument`, `java.logging`,
`java.management`, `java.management.rmi`, `java.naming`, `java.net.http`, `java.prefs`, `java.rmi`,
`java.scripting`, `java.se`, `java.security.jgss`, `java.security.sasl`, `java.smartcardio`, `java.sql`,
`java.sql.rowset`, `java.transaction.xa`, `java.xml`, `java.xml.crypto`, `jdk.accessibility`, `jdk.attach`,
`jdk.charsets`, `jdk.crypto.cryptoki`, `jdk.httpserver`, `jdk.internal.jvmstat`, `jdk.jfr`, `jdk.localedata`,
`jdk.management`, `jdk.management.agent`, `jdk.management.jfr`, `jdk.naming.dns`, `jdk.naming.rmi`, `jdk.net`,
`jdk.security.auth`, `jdk.security.jgss`, `jdk.unsupported`, `jdk.unsupported.desktop`, `jdk.zipfs`
<!-- runtime-modules:end -->

On Windows it also has `jdk.crypto.mscapi`, the Windows certificate stores. That is all of Java SE (`java.se`) plus
the charsets, locale data, ZIP file system, PKCS#11 and smart-card providers, Kerberos and JAAS login modules, DNS and
RMI naming, `jdk.net`'s socket options, the JDK HTTP server, `sun.misc.Unsafe` (`jdk.unsupported`), and JFR, JMX and
attach for diagnostics.

Not there: the compiler and the other development tools (`jdk.compiler`, so `ToolProvider.getSystemJavaCompiler()`
returns null; `jdk.javadoc`, `jdk.jshell`, `jdk.jlink`, `jdk.jpackage`, `jdk.jdeps`, `jdk.jartool`, `jdk.jconsole`,
`jdk.jstatd`, `jdk.editpad`), the debugger (`jdk.jdi`, `jdk.jdwp.agent`, so `-agentlib:jdwp` does not work in the
packaged app), `jdk.dynalink` (a bundled Nashorn cannot run), `jdk.incubator.vector`, `jdk.sctp`, `jdk.xml.dom`,
`jdk.jsobject`, `jdk.nio.mapmode`, and the JetBrains Runtime's JCEF and JOGL (`jcef`, `jogl.all`, `gluegen.rt`). A
library that needs one of them fails with `NoClassDefFoundError` in the packaged app although it works under
`./gradlew :jasper-app:run`, which uses the full SDK. Bundle a jar instead, or ask for the module to be added
(`runtimeRootModules` in `gradle/packaging-support.gradle`).
```

In `.github/workflows/linux-packages.yml`, add `      - gradle/packaging-support.gradle` directly after each of the two `      - gradle/packaging.gradle` lines (under `push:` and under `pull_request:`).

- [ ] **Step 4: Run the fixtures to make sure they pass**

Run: `./gradlew :jasper-app:verifyPackagingSupport`
Expected: `BUILD SUCCESSFUL`, log line `Verified the runtime module list`.

- [ ] **Step 5: Write the failing image checks.** In `gradle/packaging.gradle`, `verifyPackage`, insert after line 321 (`if (releaseArchitecture && normalizedReleaseArchitecture != context.host.architecture) throw …`):

```groovy
        // The trimmed runtime: exactly the documented modules, with a default CDS archive.
        def modulesMatcher = releaseText =~ /(?m)^MODULES="([^"]*)"/
        List<String> packagedModules = modulesMatcher.find() ? modulesMatcher.group(1).tokenize(' ').toSorted() : []
        List<String> expectedModules = runtimeModules(context.host.os)
        if (packagedModules != expectedModules) {
            throw new GradleException("Package verification failed: the runtime's modules differ from runtimeModules in gradle/packaging-support.gradle\n" +
                "missing: ${expectedModules - packagedModules}\nunexpected: ${packagedModules - expectedModules}")
        }
        File cdsArchive = new File(runtimeHome, context.host.os == 'windows' ? 'bin/server/classes.jsa' : 'lib/server/classes.jsa')
        if (!cdsArchive.isFile() || cdsArchive.length() == 0) {
            throw new GradleException("Package verification failed: the runtime has no default CDS archive at ${cdsArchive}")
        }
        // jdeps sees each jar's static module needs. Without this, a bundled plugin needing a module the runtime lacks
        // would fail with NoClassDefFoundError only in the packaged app.
        File jdeps = new File(context.sdkHome, "bin/jdeps${context.host.os == 'windows' ? '.exe' : ''}")
        List<File> applicationJars = appDir.listFiles().findAll { it.name.endsWith('.jar') }.sort { it.name }
        def requireModules = { List<File> jars, List<File> classPath, String what ->
            String output = executeNative([jdeps.absolutePath, '--multi-release', '25', '--ignore-missing-deps', '--print-module-deps',
                '--class-path', classPath*.absolutePath.join(File.pathSeparator)] + jars*.absolutePath, "jdeps for ${what}").stdout
            List<String> needed = output.readLines().findAll { it.trim() }.last().tokenize(',')*.trim()
            List<String> missing = needed - expectedModules
            if (missing) {
                throw new GradleException("Package verification failed: ${what} needs ${missing}, which the runtime lacks; " +
                    'add them to runtimeRootModules (gradle/packaging-support.gradle) and docs/plugin-authoring.md')
            }
        }
        requireModules(applicationJars, applicationJars, 'the application class path')
        new File(appDir, 'plugins').eachDir { plugin ->
            List<File> pluginJars = plugin.listFiles().findAll { it.name.endsWith('.jar') }.sort { it.name }
            requireModules(pluginJars, pluginJars + applicationJars, "bundled plugin ${plugin.name}")
        }
```

Then, in the same task, replace the end of `verifyPackage` (lines 431–434):

```groovy
        if (runtimeArchitecture != context.host.architecture) {
            throw new GradleException("Package verification failed: bundled Java architecture is ${runtimeArchitecture ?: 'unknown'}")
        }
        logger.lifecycle("Verified Jasper ${context.version} ${context.host.os}-${context.host.architecture} image at ${image}")
```

with:

```groovy
        if (runtimeArchitecture != context.host.architecture) {
            throw new GradleException("Package verification failed: bundled Java architecture is ${runtimeArchitecture ?: 'unknown'}")
        }
        def sharing = executeNative([bundledJava.absolutePath, '-Xshare:on', '-version'], 'bundled java with its default CDS archive (-Xshare:on)')
        if (!(sharing.stdout + sharing.stderr).contains('sharing')) {
            throw new GradleException("Package verification failed: the bundled java reports no class data sharing\n${sharing.stderr}")
        }
        long runtimeBytes = 0
        runtimeHome.eachFileRecurse(groovy.io.FileType.FILES) { runtimeBytes += it.length() }
        logger.lifecycle("Runtime: ${packagedModules.size()} modules, ${runtimeBytes.intdiv(1024 * 1024)} MB, " +
            "default CDS archive ${cdsArchive.length().intdiv(1024 * 1024)} MB")
        logger.lifecycle("Verified Jasper ${context.version} ${context.host.os}-${context.host.architecture} image at ${image}")
```

- [ ] **Step 6: Run the checks against the current image to make sure they fail** (headless)

Run: `./gradlew :jasper-app:packageApp :jasper-app:verifyPackage`
Expected: `packageApp` is UP-TO-DATE (its inputs did not change) and `verifyPackage` FAILS with `the runtime's modules differ from runtimeModules`, `missing: [java.se]` (jpackage's default set has no aggregator) and `unexpected:` the 25 modules this plan drops: `gluegen.rt`, `jcef`, `jdk.compiler`, `jdk.dynalink`, `jdk.editpad`, `jdk.incubator.vector`, `jdk.internal.ed`, `jdk.internal.le`, `jdk.internal.md`, `jdk.internal.opt`, `jdk.jartool`, `jdk.javadoc`, `jdk.jconsole`, `jdk.jdeps`, `jdk.jdi`, `jdk.jdwp.agent`, `jdk.jlink`, `jdk.jpackage`, `jdk.jshell`, `jdk.jsobject`, `jdk.jstatd`, `jdk.nio.mapmode`, `jdk.sctp`, `jdk.xml.dom`, `jogl.all`.

- [ ] **Step 7: Pass the module list and the jlink options to jpackage.** In `packageApp`, after line 202 (`inputs.property('jvmArgs', …)`), add:

```groovy
    inputs.property('runtimeRootModules', providers.provider { runtimeRootModules(normalizedHost().os) })
    inputs.property('jlinkOptions', jlinkOptions)
```

and replace line 224:

```groovy
            '--jlink-options', '--strip-debug --no-man-pages --no-header-files --bind-services']
```

with:

```groovy
            '--add-modules', runtimeRootModules(context.host.os).join(','),
            '--jlink-options', jlinkOptions]
```

- [ ] **Step 8: Run the image checks to make sure they pass** (headless)

Run: `./gradlew :jasper-app:packageApp :jasper-app:verifyPackage`
Expected: `BUILD SUCCESSFUL`; lines `Runtime: 41 modules, <about 80> MB, default CDS archive <about 15> MB` and `Verified Jasper 1.0.0 macos-aarch64 image at …`. Note the MB figure for Task 5. Also:

```bash
ls jasper-app/build/packaging/image/Jasper.app/Contents/runtime/Contents/Home/lib/server
du -sh jasper-app/build/packaging/image/Jasper.app/Contents/runtime
```

Expected: `classes.jsa`, `classes_nocoops.jsa`, `libjsig.dylib`, `libjvm.dylib`; `du` well under the before image's `192M`.

- [ ] **Step 9: Run the whole suite**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL` (includes `verifyPackagingSupport` and `AppDocumentationTest`, which checks the new docs section). Record totals:

```bash
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t = f = e = s = 0
for path in glob.glob('*/build/test-results/test/*.xml') + glob.glob('plugins/*/build/test-results/test/*.xml'):
    root = ET.parse(path).getroot()
    t += int(root.get('tests')); f += int(root.get('failures')); e += int(root.get('errors')); s += int(root.get('skipped'))
print(f"{t} tests, {f} failures, {e} errors, {s} skipped")
PY
```

- [ ] **Step 10: Commit**

```bash
git add gradle/packaging-support.gradle gradle/packaging-support-test.gradle gradle/packaging.gradle docs/plugin-authoring.md .github/workflows/linux-packages.yml
git commit -F - <<'EOF'
build(packaging): trimmed runtime with a default CDS archive

jpackage now gets an explicit module list instead of every module with
--bind-services, which also bound javac, jshell, jlink, jpackage and
JBR's JCEF and JOGL, and jlink writes the runtime's default CDS archive.
verifyPackage checks the module list, the archive (-Xshare:on) and, with
jdeps, that the application and every bundled plugin need nothing the
runtime lacks. docs/plugin-authoring.md lists the modules plugins may
rely on; verifyPackagingSupport keeps that list and the build's in step.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: Native libraries shipped inside the app (2c)

**Files:**
- Modify: `gradle/packaging-support.gradle` (append), `gradle/packaging-support-test.gradle` (append)
- Create: `jasper-app/src/test/java/dev/jasper/app/architecture/BundledNativesProbe.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/architecture/BundledNativesProbeTest.java`
- Modify: `gradle/packaging.gradle` (helpers after `resignImage`, `packageApp` inputs, java-options and host branches; `verifyPackage`)

**Interfaces:**
- Consumes: Task 1's `verifySupport`, `runtimeModules`; `packaging.gradle`'s `resignImage`, `executeNative`, `cfg`, `appDir`, `bundledJava`, `context`.
- Produces: `ext.bundledNatives(String os, String architecture) → List<Map>` with keys `jar` (file-name regex), `entry`, `path` (under the app directory), `executable`, `loaded`; `ext.bundledNativeJvmOptions(String os) → List<String>` (literal `$APPDIR`); `ext.launcherClasspath(String config, File appDir) → List<String>`; `ext.launcherJavaOptions(String config, File appDir) → List<String>`; `ext.loadedLibraries(String log) → List<String>`; `packaging.gradle` locals `bundledNativeBytes(File appDir, Map nativeFile) → byte[]`, `installBundledNatives(File appDir, Map host)`; system property `jasper.bundledNativeOptions` on `:jasper-app:test`; `dev.jasper.app.architecture.BundledNativesProbe` (`main`, prints `pty4j <name> <path>` lines and `bundled natives probe passed`).

- [ ] **Step 1: Write the failing fixtures.** Append to `gradle/packaging-support-test.gradle`:

```groovy
verifySupport.configure {
    doLast {
        def mac = bundledNatives('macos', 'aarch64')
        assert mac*.path == ['native/libflatlaf-macos-arm64.dylib', 'native/libjnidispatch.jnilib',
            'native/darwin/libpty.dylib', 'native/darwin/pty4j-unix-spawn-helper']
        assert mac*.entry == ['com/formdev/flatlaf/natives/libflatlaf-macos-arm64.dylib',
            'com/sun/jna/darwin-aarch64/libjnidispatch.jnilib', 'resources/com/pty4j/native/darwin/libpty.dylib',
            'resources/com/pty4j/native/darwin/pty4j-unix-spawn-helper']
        assert mac.findAll { it.executable }*.path == ['native/darwin/pty4j-unix-spawn-helper']
        assert mac.findAll { it.loaded }*.path == ['native/libflatlaf-macos-arm64.dylib', 'native/libjnidispatch.jnilib']
        assert bundledNatives('macos', 'x64')*.entry.take(2) == ['com/formdev/flatlaf/natives/libflatlaf-macos-x86_64.dylib',
            'com/sun/jna/darwin-x86-64/libjnidispatch.jnilib']
        assert bundledNatives('windows', 'x64')*.path == ['native/flatlaf-windows-x86_64.dll', 'native/jnidispatch.dll',
            'native/win/x86-64/conpty.dll', 'native/win/x86-64/OpenConsole.exe', 'native/win/x86-64/cyglaunch.exe',
            'native/win/x86-64/win-helper.dll', 'native/win/x86-64/winpty-agent.exe', 'native/win/x86-64/winpty.dll']
        assert bundledNatives('windows', 'x64').findAll { it.loaded }*.path == ['native/flatlaf-windows-x86_64.dll', 'native/jnidispatch.dll']
        assert bundledNatives('linux', 'x64').isEmpty() && bundledNatives('linux', 'aarch64').isEmpty()
        // Each pattern names exactly its own jar among the packaged ones.
        List<String> jars = ['flatlaf-3.7.jar', 'flatlaf-extras-3.7.jar', 'jna-5.14.0.jar', 'jna-platform-5.14.0.jar', 'pty4j-0.13.10.jar']
        assert mac.collect { nativeFile -> jars.findAll { it ==~ nativeFile.jar } } ==
            [['flatlaf-3.7.jar'], ['jna-5.14.0.jar'], ['pty4j-0.13.10.jar'], ['pty4j-0.13.10.jar']]
        assert bundledNativeJvmOptions('macos') == ['-Dflatlaf.nativeLibraryPath=$APPDIR/native',
            '-Djna.boot.library.path=$APPDIR/native', '-Djna.nosys=true', '-Dpty4j.preferred.native.folder=$APPDIR/native']
        assert bundledNativeJvmOptions('windows') == bundledNativeJvmOptions('macos')
        assert bundledNativeJvmOptions('linux').isEmpty()

        // An app folder with a space, as under "/Applications/My Apps"; Windows writes CRLF.
        File spaced = new File(temporaryDir, 'My Apps/Jasper.app/Contents/app')
        String root = spaced.absolutePath
        String config = '[Application]\napp.classpath=$APPDIR/jasper-app.jar\napp.mainclass=dev.jasper.app.Main\n' +
            'app.classpath=$APPDIR/flatlaf-3.7.jar\n\n[JavaOptions]\njava-options=-Djpackage.app-version=1.0.0\n' +
            'java-options=-Dflatlaf.nativeLibraryPath=$APPDIR/native\n'
        assert launcherClasspath(config, spaced) == ["${root}/jasper-app.jar", "${root}/flatlaf-3.7.jar"]*.toString()
        assert launcherJavaOptions(config, spaced) == ['-Djpackage.app-version=1.0.0', "-Dflatlaf.nativeLibraryPath=${root}/native".toString()]
        assert launcherClasspath(config.replace('\n', '\r\n'), spaced) == launcherClasspath(config, spaced)
        String log = '[0.212s][info][library] Loaded library /a b/native/libjnidispatch.jnilib, handle 0x000000007549ce80\n' +
            '[0.212s][info][library] Found JNI_OnLoad in library with handle 0x000000007549ce80\n' +
            '[0.300s][info][library] Loaded library C:\\J\\app\\native\\jnidispatch.dll, handle 0x00007ffa12340000\n'
        assert loadedLibraries(log) == ['/a b/native/libjnidispatch.jnilib', 'C:\\J\\app\\native\\jnidispatch.dll']
        logger.lifecycle('Verified bundled natives and launcher configuration parsing')
    }
}
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew :jasper-app:verifyPackagingSupport`
Expected: FAIL, `Could not get unknown property 'bundledNatives'` (Task 1's block still passes first).

- [ ] **Step 3: Implement the helpers.** Append to `gradle/packaging-support.gradle`:

```groovy
// Native libraries the packaged app loads from its app directory's native/ folder instead of extracting a copy with
// a new random name on every launch, which also defeats macOS's code-signature cache and the endpoint-security
// agent's. Each is copied byte for byte from its jar, so it keeps its vendor signature. Linux keeps extraction:
// FlatLaf's Linux library links GTK 3, which would become a package dependency. Keys: jar, the jar's file-name pattern;
// entry, the resource in it; path, where it goes under the app directory; executable, needs the execute bit; loaded,
// the JVM's System.load opens it, so -Xlog:library lists it (JNA opens pty4j's libraries with its own dlopen).
ext.bundledNatives = { String os, String architecture ->
    String flatLaf = 'flatlaf-[0-9][^/]*[.]jar', jna = 'jna-[0-9][^/]*[.]jar', pty4j = 'pty4j-[0-9][^/]*[.]jar'
    if (os == 'macos') {
        String flatLafArchitecture = architecture == 'aarch64' ? 'arm64' : 'x86_64'
        String jnaArchitecture = architecture == 'aarch64' ? 'aarch64' : 'x86-64'
        return [
            [jar: flatLaf, entry: "com/formdev/flatlaf/natives/libflatlaf-macos-${flatLafArchitecture}.dylib".toString(),
             path: "native/libflatlaf-macos-${flatLafArchitecture}.dylib".toString(), executable: false, loaded: true],
            [jar: jna, entry: "com/sun/jna/darwin-${jnaArchitecture}/libjnidispatch.jnilib".toString(),
             path: 'native/libjnidispatch.jnilib', executable: false, loaded: true],
            [jar: pty4j, entry: 'resources/com/pty4j/native/darwin/libpty.dylib', path: 'native/darwin/libpty.dylib',
             executable: false, loaded: false],
            [jar: pty4j, entry: 'resources/com/pty4j/native/darwin/pty4j-unix-spawn-helper',
             path: 'native/darwin/pty4j-unix-spawn-helper', executable: true, loaded: false],
        ]
    }
    if (os == 'windows') {
        return [
            [jar: flatLaf, entry: 'com/formdev/flatlaf/natives/flatlaf-windows-x86_64.dll',
             path: 'native/flatlaf-windows-x86_64.dll', executable: false, loaded: true],
            [jar: jna, entry: 'com/sun/jna/win32-x86-64/jnidispatch.dll', path: 'native/jnidispatch.dll',
             executable: false, loaded: true],
        ] + ['conpty.dll', 'OpenConsole.exe', 'cyglaunch.exe', 'win-helper.dll', 'winpty-agent.exe', 'winpty.dll'].collect { String name ->
            [jar: pty4j, entry: "resources/com/pty4j/native/win/x86-64/${name}".toString(),
             path: "native/win/x86-64/${name}".toString(), executable: false, loaded: false]
        }
    }
    []
}

// The launcher options that point FlatLaf, JNA and pty4j at those files ($APPDIR is the launcher's app directory).
// pty4j looks in native/darwin or native/win/x86-64 below its folder. jna.nosys keeps JNA off java.library.path.
// No jna.noclasspath or jna.nounpack: JNA's properties are process-wide, and a plugin that bundles its own JNA must
// still be able to extract its own copy (docs/plugin-authoring.md, "The Java runtime").
ext.bundledNativeJvmOptions = { String os ->
    os in ['macos', 'windows'] ? ['-Dflatlaf.nativeLibraryPath=$APPDIR/native', '-Djna.boot.library.path=$APPDIR/native',
        '-Djna.nosys=true', '-Dpty4j.preferred.native.folder=$APPDIR/native'] : []
}

// A jpackage launcher configuration's class path and JVM options, in file order, with $APPDIR resolved to appDir.
ext.launcherClasspath = { String config, File appDir ->
    config.readLines().findAll { it.startsWith('app.classpath=') }
        .collect { it.substring('app.classpath='.length()).replace('$APPDIR', appDir.absolutePath) }
}
ext.launcherJavaOptions = { String config, File appDir ->
    config.readLines().findAll { it.startsWith('java-options=') }
        .collect { it.substring('java-options='.length()).replace('$APPDIR', appDir.absolutePath) }
}

// The files -Xlog:library reports as loaded ("Loaded library <path>, handle 0x...").
ext.loadedLibraries = { String log -> (log =~ /Loaded library (.+), handle 0x\p{XDigit}+/).collect { it[1] } }
```

Append to `gradle/packaging-support-test.gradle`:

```groovy
// BundledNativesProbeTest runs the probe with the packaged launcher's own native-library options.
tasks.named('test', Test) { systemProperty('jasper.bundledNativeOptions', bundledNativeJvmOptions('macos').join('\n')) }
```

- [ ] **Step 4: Run the fixtures to make sure they pass**

Run: `./gradlew :jasper-app:verifyPackagingSupport`
Expected: `BUILD SUCCESSFUL`, `Verified bundled natives and launcher configuration parsing`.

- [ ] **Step 5: Write the failing probe test.** Create `jasper-app/src/test/java/dev/jasper/app/architecture/BundledNativesProbeTest.java`:

```java
package dev.jasper.app.architecture;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The packaged launcher's native-library options (passed in by gradle/packaging-support-test.gradle), with $APPDIR
 * resolved to a temporary app folder laid out as packageApp lays out the image: every library loads from that folder
 * and nothing is extracted. Catches a FlatLaf, JNA or pty4j upgrade that moves its resources or ignores the options.
 */
@EnabledOnOs(OS.MAC)
class BundledNativesProbeTest {
    @TempDir Path temp;

    @Test void everyBundledLibraryLoadsFromTheAppFolderAndNothingIsExtracted() throws Exception {
        String options = System.getProperty("jasper.bundledNativeOptions");
        assertThat(options).as("set by gradle/packaging-support-test.gradle").isNotBlank();
        boolean arm = System.getProperty("os.arch").equals("aarch64");
        Path root = temp.toRealPath();
        Path app = root.resolve("app");
        Path natives = app.resolve("native");
        String flatLaf = "libflatlaf-macos-" + (arm ? "arm64" : "x86_64") + ".dylib";
        copy("com.formdev.flatlaf.ui.FlatNativeMacLibrary", "com/formdev/flatlaf/natives/" + flatLaf, natives.resolve(flatLaf));
        copy("com.sun.jna.Native", "com/sun/jna/darwin-" + (arm ? "aarch64" : "x86-64") + "/libjnidispatch.jnilib",
            natives.resolve("libjnidispatch.jnilib"));
        copy("com.pty4j.PtyProcessBuilder", "resources/com/pty4j/native/darwin/libpty.dylib", natives.resolve("darwin/libpty.dylib"));
        Path helper = natives.resolve("darwin/pty4j-unix-spawn-helper");
        copy("com.pty4j.PtyProcessBuilder", "resources/com/pty4j/native/darwin/pty4j-unix-spawn-helper", helper);
        assertThat(helper.toFile().setExecutable(true, false)).isTrue();
        Path scratch = root.resolve("scratch");
        Files.createDirectories(scratch.resolve("home"));
        Files.createDirectories(scratch.resolve("tmp"));

        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "--enable-native-access=ALL-UNNAMED"));
        for (String option : options.split("\n")) command.add(option.replace("$APPDIR", app.toString()));
        command.addAll(List.of("-Djava.awt.headless=true", "-Duser.home=" + scratch.resolve("home"),
            "-Djava.io.tmpdir=" + scratch.resolve("tmp"), "-Djna.tmpdir=" + scratch.resolve("jna"),
            "-Dpty4j.tmpdir=" + scratch.resolve("pty4j"), "-Xlog:library=info:stdout",
            "-cp", classPath(), BundledNativesProbe.class.getName()));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(output).contains("Loaded library " + natives.resolve(flatLaf) + ", handle",
            "Loaded library " + natives.resolve("libjnidispatch.jnilib") + ", handle",
            "pty4j libpty.dylib " + natives.resolve("darwin/libpty.dylib"),
            "pty4j pty4j-unix-spawn-helper " + helper,
            "bundled natives probe passed");
        try (var files = Files.walk(scratch)) {
            assertThat(files.filter(Files::isRegularFile).toList()).as("files extracted while loading").isEmpty();
        }
    }

    private static void copy(String owner, String entry, Path target) throws Exception {
        Files.createDirectories(target.getParent());
        try (var jar = new ZipFile(codeSource(owner).toFile())) {
            var source = jar.getEntry(entry);
            assertThat(source).as("%s in %s", entry, jar.getName()).isNotNull();
            try (var input = jar.getInputStream(source)) { Files.copy(input, target); }
        }
    }

    /** Where a class on the test runtime class path comes from, without initialising it (JNA would load itself). */
    private static Path codeSource(String className) throws Exception {
        Class<?> type = Class.forName(className, false, BundledNativesProbeTest.class.getClassLoader());
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    /** The probe and what the packaged class path gives it: FlatLaf, JNA, pty4j and their runtime needs. */
    private static String classPath() throws Exception {
        List<String> entries = new ArrayList<>();
        for (String type : List.of(BundledNativesProbe.class.getName(), "com.formdev.flatlaf.ui.FlatNativeMacLibrary",
                "com.sun.jna.Native", "com.sun.jna.platform.unix.LibCAPI", "com.pty4j.PtyProcessBuilder", "kotlin.Unit",
                "org.slf4j.LoggerFactory", "org.slf4j.nop.NOPServiceProvider"))
            entries.add(codeSource(type).toString());
        return String.join(File.pathSeparator, entries);
    }
}
```

- [ ] **Step 6: Run it to make sure it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.architecture.BundledNativesProbeTest'`
Expected: FAIL at `compileTestJava`, `cannot find symbol … class BundledNativesProbe`.

- [ ] **Step 7: Write the probe.** Create `jasper-app/src/test/java/dev/jasper/app/architecture/BundledNativesProbe.java`:

```java
package dev.jasper.app.architecture;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Packaging probe, not a test: verifyPackage runs it headless with a packaged image's runtime, launcher options and
 * class path, and BundledNativesProbeTest runs it against a folder laid out like the image. It loads the native
 * libraries Jasper ships in the image (JNA's dispatch library, FlatLaf's window library) and starts one process through
 * pty4j as Jasper does, which opens libpty and, on macOS, runs the spawn helper. JNA opens pty4j's files with its own
 * dlopen, which -Xlog:library does not see, so the probe prints the files pty4j resolved, one "pty4j name path" line
 * each. The app compiles against neither JNA nor pty4j, hence the reflection. Exits 1 when a step fails.
 */
public final class BundledNativesProbe {
    private BundledNativesProbe() {}

    public static void main(String[] args) throws Exception {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        Class.forName("com.sun.jna.Native");
        String flatLaf = windows ? "com.formdev.flatlaf.ui.FlatNativeWindowsLibrary" : "com.formdev.flatlaf.ui.FlatNativeMacLibrary";
        if (!Boolean.TRUE.equals(Class.forName(flatLaf).getMethod("isLoaded").invoke(null))) fail("FlatLaf's native library did not load");
        String[] command = windows
            ? new String[] {System.getenv().getOrDefault("SystemRoot", "C:\\Windows") + "\\System32\\hostname.exe"}
            : new String[] {"/usr/bin/true"};
        Class<?> builderType = Class.forName("com.pty4j.PtyProcessBuilder");
        Object builder = builderType.getConstructor(String[].class).newInstance((Object) command);
        builderType.getMethod("setUseWinConPty", boolean.class).invoke(builder, windows);
        Process process = (Process) builderType.getMethod("start").invoke(builder);
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("The PTY process did not exit");
        }
        if (process.exitValue() != 0) fail("The PTY process exited with " + process.exitValue());
        Method resolve = Class.forName("com.pty4j.util.PtyUtil").getMethod("resolveNativeFile", String.class);
        List<String> files = windows ? List.of("conpty.dll", "OpenConsole.exe", "win-helper.dll") : List.of("libpty.dylib", "pty4j-unix-spawn-helper");
        for (String name : files) System.out.println("pty4j " + name + " " + ((File) resolve.invoke(null, name)).getAbsolutePath());
        System.out.println("bundled natives probe passed");
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
```

- [ ] **Step 8: Run the test to make sure it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.architecture.BundledNativesProbeTest'`
Expected: PASS (1 test; skipped on Linux and Windows).

Then check source hygiene of the two new files:

```bash
python3 - jasper-app/src/test/java/dev/jasper/app/architecture/BundledNativesProbe.java jasper-app/src/test/java/dev/jasper/app/architecture/BundledNativesProbeTest.java <<'PY'
import pathlib, sys
for name in sys.argv[1:]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(name, "bad chars:", bad)
PY
```

Expected: no output.

- [ ] **Step 9: Write the failing image checks.** In `gradle/packaging.gradle`, `verifyPackage`:

After `    dependsOn(packageApp)` (line 266) add:

```groovy
    // BundledNativesProbe lives in the test sources: verification tooling, never in the application jar.
    if (normalizedHost().os in ['macos', 'windows']) dependsOn(tasks.named('testClasses'))
```

After the existing `cfg` checks (the block ending `throw new GradleException('Package verification failed: required JVM options are missing from Jasper.cfg')` + `}`, line 303) add:

```groovy
        // Part 2 adds no JVM tuning flags: only the application's own options, the bundled-native options and
        // jpackage's app version may appear (trainAotCache adds -XX:AOTCache on macOS).
        List<String> cfgOptions = cfg.readLines().findAll { it.startsWith('java-options=') }.collect { it.substring('java-options='.length()) }
        List<String> requiredOptions = app.applicationDefaultJvmArgs.collect { it.toString() } + bundledNativeJvmOptions(context.host.os)
        List<String> permittedOptions = requiredOptions + ["-Djpackage.app-version=${context.version}".toString()]
        if (cfgOptions != cfgOptions.toUnique() || !cfgOptions.containsAll(requiredOptions) || !(cfgOptions - permittedOptions).isEmpty()) {
            throw new GradleException("Package verification failed: Jasper.cfg's JVM options are ${cfgOptions}; expected ${requiredOptions} and jpackage's app version")
        }
```

After the existing jar payload loop (the `[[jar: ptyJar, …]].each { … }` block ending `} finally { zip.close() }` + `}`, line 338) add:

```groovy
        // Bundled native libraries: present, byte-identical to their jar entries (so their vendor signatures hold),
        // executable where needed and, on macOS, validly signed.
        List<Map> natives = bundledNatives(context.host.os, context.host.architecture)
        natives.each { Map nativeFile ->
            File packaged = new File(appDir, nativeFile.path)
            if (!packaged.isFile() || !Arrays.equals(bundledNativeBytes(appDir, nativeFile), Files.readAllBytes(packaged.toPath()))) {
                throw new GradleException("Package verification failed: ${nativeFile.path} is missing or differs from ${nativeFile.entry}")
            }
            if (nativeFile.executable && !packaged.canExecute()) throw new GradleException("Package verification failed: ${nativeFile.path} is not executable")
            if (context.host.os == 'macos') {
                executeNative(['/usr/bin/codesign', '--verify', '--strict', packaged.absolutePath], "signature of ${nativeFile.path}")
            }
        }
```

At the end of `verifyPackage`, directly before the final `logger.lifecycle("Verified Jasper …")`, add:

```groovy
        if (natives) {
            // Headless: the image's java, launcher options and class path load every bundled library and start one
            // process through pty4j. -Xlog:library shows where the JVM loaded each library from, the probe prints the
            // files pty4j resolved, and the scratch home and temporary folders must stay empty (nothing extracted).
            File scratch = new File(temporaryDir, 'natives-probe')
            delete(scratch)
            ['home', 'tmp'].each { new File(scratch, it).mkdirs() }
            List<String> probe = ([bundledJava.absolutePath] +
                launcherJavaOptions(cfg, appDir).findAll { !it.startsWith('-XX:AOTCache=') } +
                ['-Djava.awt.headless=true', "-Duser.home=${new File(scratch, 'home')}", "-Djava.io.tmpdir=${new File(scratch, 'tmp')}",
                 "-Djna.tmpdir=${new File(scratch, 'jna')}", "-Dpty4j.tmpdir=${new File(scratch, 'pty4j')}", '-Xlog:library=info:stdout',
                 '-cp', (launcherClasspath(cfg, appDir) + sourceSets.test.output.classesDirs.files*.absolutePath).join(File.pathSeparator),
                 'dev.jasper.app.architecture.BundledNativesProbe'])*.toString()
            String output = executeNative(probe, 'bundled natives probe (headless)').stdout
            if (!output.contains('bundled natives probe passed')) throw new GradleException("Package verification failed: the natives probe did not finish\n${output}")
            Set<File> loaded = loadedLibraries(output).collect { new File(it).canonicalFile } as Set
            natives.findAll { it.loaded }.each { Map nativeFile ->
                if (!loaded.contains(new File(appDir, nativeFile.path).canonicalFile)) {
                    throw new GradleException("Package verification failed: the JVM did not load ${nativeFile.path} from the image; it loaded\n${loaded.join('\n')}")
                }
            }
            File nativeRoot = new File(appDir, 'native').canonicalFile
            def resolved = (output =~ /(?m)^pty4j (\S+) (.+)$/).collect { [name: it[1], file: new File(it[2].trim()).canonicalFile] }
            if (!resolved) throw new GradleException("Package verification failed: the natives probe reported no pty4j files\n${output}")
            resolved.each { pty ->
                if (!pty.file.isFile() || !pty.file.path.startsWith(nativeRoot.path + File.separator)) {
                    throw new GradleException("Package verification failed: pty4j resolved ${pty.name} to ${pty.file}, not a file under ${nativeRoot}")
                }
            }
            List<File> extracted = []
            scratch.eachFileRecurse(groovy.io.FileType.FILES) { extracted << it }
            if (extracted) throw new GradleException("Package verification failed: loading the natives extracted ${extracted}")
            logger.lifecycle("Bundled natives load from ${nativeRoot}")
        }
```

After `resignImage` (line 172) add the two helpers the checks and `packageApp` share:

```groovy
// The bytes of one bundled native library (gradle/packaging-support.gradle), read from its jar in the image's app directory.
def bundledNativeBytes = { File appDir, Map nativeFile ->
    List<File> jars = (appDir.listFiles() ?: [] as File[]).findAll { it.name ==~ nativeFile.jar }
    if (jars.size() != 1) throw new GradleException("Expected one jar matching ${nativeFile.jar} in ${appDir}, found ${jars*.name}")
    new ZipFile(jars[0]).withCloseable { zip ->
        def entry = zip.getEntry(nativeFile.entry)
        if (entry == null) throw new GradleException("${jars[0].name} has no ${nativeFile.entry}; update bundledNatives in gradle/packaging-support.gradle")
        zip.getInputStream(entry).withCloseable { it.readAllBytes() }
    }
}

// Copies the host's bundled native libraries into the image's app directory, byte for byte.
def installBundledNatives = { File appDir, Map host ->
    bundledNatives(host.os, host.architecture).each { Map nativeFile ->
        File target = new File(appDir, nativeFile.path)
        target.parentFile.mkdirs()
        target.bytes = bundledNativeBytes(appDir, nativeFile)
        if (nativeFile.executable) target.setExecutable(true, false)
    }
}
```

- [ ] **Step 10: Run the image checks to make sure they fail** (headless)

Run: `./gradlew :jasper-app:packageApp :jasper-app:verifyPackage`
Expected: FAIL with `Jasper.cfg's JVM options are [-Djpackage.app-version=1.0.0, --enable-native-access=ALL-UNNAMED, -Dapple.awt.application.name=Jasper]; expected […, -Dflatlaf.nativeLibraryPath=$APPDIR/native, …]`.

- [ ] **Step 11: Install the natives and their options in `packageApp`.** After the two inputs added in Task 1 Step 7, add:

```groovy
    inputs.property('bundledNatives', providers.provider { bundledNatives(normalizedHost().os, normalizedHost().architecture).collect { it.toString() } })
    inputs.property('bundledNativeOptions', providers.provider { bundledNativeJvmOptions(normalizedHost().os) })
```

Replace

```groovy
        app.applicationDefaultJvmArgs.each { args.addAll(['--java-options', it.toString()]) }
```

with

```groovy
        app.applicationDefaultJvmArgs.each { args.addAll(['--java-options', it.toString()]) }
        bundledNativeJvmOptions(context.host.os).each { args.addAll(['--java-options', it]) }
```

In the host branches after `executeNative(args, 'jpackage app-image')`, replace

```groovy
                removePluginClasspath(new File(image, 'Contents/app/Jasper.cfg'))
```

with

```groovy
                removePluginClasspath(new File(image, 'Contents/app/Jasper.cfg'))
                // After jpackage, which re-signs native inputs: these keep their vendor signatures, sealed by resignImage below.
                installBundledNatives(new File(image, 'Contents/app'), context.host)
```

and replace the Windows branch

```groovy
            } else {
                removePluginClasspath(new File(imageDir, 'Jasper/app/Jasper.cfg'))
            }
```

with

```groovy
            } else {
                removePluginClasspath(new File(imageDir, 'Jasper/app/Jasper.cfg'))
                installBundledNatives(new File(imageDir, 'Jasper/app'), context.host)
            }
```

The Linux branch stays as it is.

- [ ] **Step 12: Run the image checks to make sure they pass** (headless; the probe starts `/usr/bin/true`, no window)

Run: `./gradlew :jasper-app:packageApp :jasper-app:verifyPackage`
Expected: `BUILD SUCCESSFUL` with `Bundled natives load from …/Jasper.app/Contents/app/native`. Then:

```bash
find jasper-app/build/packaging/image/Jasper.app/Contents/app/native -type f
grep '^java-options=' jasper-app/build/packaging/image/Jasper.app/Contents/app/Jasper.cfg
```

Expected: the four files of `bundledNatives('macos', 'aarch64')`; `java-options` lines for `-Djpackage.app-version=1.0.0`, `--enable-native-access=ALL-UNNAMED`, `-Dapple.awt.application.name=Jasper` and the four native options with literal `$APPDIR`.

Windows (not verifiable on this Mac): the same two commands on a Windows packaging machine must pass, with `app\native\flatlaf-windows-x86_64.dll`, `app\native\jnidispatch.dll` and the six `app\native\win\x86-64\` files.

- [ ] **Step 13: Run the whole suite**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL`; totals with Task 1's script, one more test than Task 1 on macOS.

- [ ] **Step 14: Commit**

```bash
git add gradle/packaging-support.gradle gradle/packaging-support-test.gradle gradle/packaging.gradle jasper-app/src/test/java/dev/jasper/app/architecture/BundledNativesProbe.java jasper-app/src/test/java/dev/jasper/app/architecture/BundledNativesProbeTest.java
git commit -F - <<'EOF'
build(packaging): ship FlatLaf, JNA and pty4j natives inside the app

packageApp copies the natives byte for byte from their jars into the
app directory's native/ folder on macOS and Windows and points FlatLaf,
JNA and pty4j at it through launcher options, so no launch extracts a
copy under a new name. verifyPackage compares the files with their jar
entries, checks their signatures, allows no other JVM options, and runs
a headless probe with the image's runtime: -Xlog:library must show each
library loaded from the image and nothing may be extracted.
BundledNativesProbeTest runs the same probe in check on macOS.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: AOT cache trained from the packaged image (2b, 2e)

**Files:**
- Modify: `gradle/packaging-support.gradle` (append), `gradle/packaging-support-test.gradle` (append)
- Modify: `gradle/packaging.gradle` (`aotRequested`, `verifyAotCache`, `verifyPackage`, host branches at the bottom)

**Interfaces:**
- Consumes: Task 2's `launcherClasspath`, `launcherJavaOptions`; Plan 1's trace JSON and `jasper.startupTrace` property; `resignImage`, `executeNative`.
- Produces: `ext.aotCacheOption` (`'-XX:AOTCache=$APPDIR/jasper.aot'`); `ext.withJavaOption(String config, String option) → String`; `ext.javaToolOptions(List<String>) → String`; `ext.aotTrainingShell` (`String`); `ext.aotTrainingConfig(File shell) → String`; `ext.trainingTraceProblem(Map trace, long pid) → String` (null when fit); `ext.runAotTraining(File launcher, File work, long settleSeconds, long exitSeconds) → File` (the cache); `ext.aotCheckCommand(File java, String config, File appDir) → List<String>`; `packaging.gradle` local `verifyAotCache(File runtimeHome, File appDir) → boolean` (false when the configuration names no cache); task `:jasper-app:trainAotCache` (macOS only); property `-Pjasper.aot=true`; files `Contents/app/jasper.aot` and `jasper-app/build/packaging/aot-training/` (`home/`, `startup-trace.json`, `training.log`).

- [ ] **Step 1: Write the failing fixtures.** Append to `gradle/packaging-support-test.gradle`:

```groovy
verifySupport.configure {
    doLast {
        String config = '[Application]\napp.classpath=$APPDIR/jasper-app.jar\napp.classpath=$APPDIR/flatlaf-3.7.jar\n\n' +
            '[JavaOptions]\njava-options=-Djpackage.app-version=1.0.0\n'
        String trained = withJavaOption(config, aotCacheOption)
        assert trained == config + 'java-options=-XX:AOTCache=$APPDIR/jasper.aot\n'
        assert withJavaOption(trained, aotCacheOption) == trained
        assert withJavaOption(trained.replace('jasper.aot', 'old.aot'), aotCacheOption) == trained
        assert withJavaOption('[Application]\napp.mainclass=dev.jasper.app.Main\n', '-Dx=y') ==
            '[Application]\napp.mainclass=dev.jasper.app.Main\n\n[JavaOptions]\njava-options=-Dx=y\n'
        assert withJavaOption('[JavaOptions]\njava-options=-Da=1\n\n[Other]\nkey=v\n', '-Db=2') ==
            '[JavaOptions]\njava-options=-Da=1\njava-options=-Db=2\n\n[Other]\nkey=v\n'

        File appDir = new File(temporaryDir, 'My Apps/Jasper.app/Contents/app')
        File javaBinary = new File(temporaryDir, 'runtime/bin/java')
        String root = appDir.absolutePath
        assert aotCheckCommand(javaBinary, trained, appDir) == [javaBinary.absolutePath, '-Djpackage.app-version=1.0.0',
            "-XX:AOTCache=${root}/jasper.aot", '-XX:AOTMode=on', '-cp',
            ["${root}/jasper-app.jar", "${root}/flatlaf-3.7.jar"].join(File.pathSeparator), '-version']*.toString()

        assert javaToolOptions(['-Da=b c', "-Dq=it's", '-Dd="x"', '-Dplain']) == '''-Da=b' 'c -Dq=it"'"s -Dd='"'x'"' -Dplain'''

        Map good = [pid: 42, kind: 'cold', settled: true, milestones: [WINDOW_SHOWN: 1.0, OUTPUT_PAINTED: 2.0, PLUGINS_BOUND: 3.0]]
        assert trainingTraceProblem(good, 42L) == null
        assert trainingTraceProblem(good + [pid: 7], 42L) == 'the trace belongs to process 7, not the training launch 42'
        assert trainingTraceProblem(good + [kind: 'background'], 42L) == "the launch was 'background', not a cold start with a window"
        assert trainingTraceProblem(good + [milestones: [WINDOW_SHOWN: 1.0]], 42L) == 'start-up settled without OUTPUT_PAINTED, PLUGINS_BOUND'
        assert trainingTraceProblem(good + [milestones: null], 42L) == 'start-up settled without WINDOW_SHOWN, OUTPUT_PAINTED, PLUGINS_BOUND'
        logger.lifecycle('Verified launcher configuration edits and the AOT checks')
    }
}

// runAotTraining against stand-ins for the launcher: /bin/sh scripts that read JAVA_TOOL_OPTIONS the way the JVM does,
// write Plan 1's trace JSON and, on SIGTERM, the cache. Windows has no /bin/sh, and AOT training is macOS-only.
verifySupport.configure {
    doLast {
        if (System.getProperty('os.name').startsWith('Windows')) return
        File fakes = new File(temporaryDir, 'aot training')   // a space, as in a checkout under "My Projects"
        fakes.deleteDir()
        fakes.mkdirs()
        String readOptions = '''eval "set -- $JAVA_TOOL_OPTIONS"
for option in "$@"; do
  case "$option" in
    -Djasper.startupTrace=*) trace=${option#-Djasper.startupTrace=} ;;
    -XX:AOTCacheOutput=*) cache=${option#-XX:AOTCacheOutput=} ;;
  esac
done
printf '%s\\n' "$@" > "${cache%/*}/options.txt"
'''
        def settles = { String milestones, boolean writesCache ->
            (writesCache ? 'trap \'printf cache > "$cache"; exit 143\' TERM\n' : 'trap \'exit 143\' TERM\n') +
                'printf \'{"schema":1,"pid":%s,"kind":"cold","settled":true,"milestones":{' + milestones + '}}\' "$$" > "$trace.part"\n' +
                'mv "$trace.part" "$trace"\n' +
                'while :; do sleep 1; done\n'
        }
        def fake = { String name, String body ->
            File script = new File(fakes, "${name}.sh")
            script.setText('#!/bin/sh\n' + readOptions + body, 'UTF-8')
            script.setExecutable(true, false)
            script
        }
        def expectFailure = { String expected, Closure action ->
            try {
                action()
                assert false : "expected a failure mentioning: ${expected}"
            } catch (GradleException failure) {
                assert failure.message.contains(expected) : failure.message
            }
        }
        String complete = '"WINDOW_SHOWN":1.0,"OUTPUT_PAINTED":2.0,"PLUGINS_BOUND":3.0'
        File work = new File(fakes, 'work')
        File cache = runAotTraining(fake('trains', settles(complete, true)), work, 20, 20)
        assert cache == new File(work, 'jasper.aot') && cache.getText('UTF-8') == 'cache'
        assert new File(work, 'options.txt').readLines('UTF-8') == ["-XX:AOTCacheOutput=${cache.absolutePath}",
            "-Duser.home=${new File(work, 'home').absolutePath}", "-Djasper.startupTrace=${new File(work, 'startup-trace.json').absolutePath}",
            '-Dapple.awt.UIElement=true']*.toString()
        String trainingConfig = new File(work, 'home/.config/jasper/config.toml').getText('UTF-8')
        assert trainingConfig.contains('run_in_background = false') && trainingConfig.contains('program = "/bin/sh"')
        assert trainingConfig.contains('args = ["' + new File(work, 'training-shell.sh').absolutePath + '"]')
        assert new File(work, 'training-shell.sh').canExecute()
        assert aotTrainingConfig(new File('/w/say "hi"/s.sh')).contains('args = ["/w/say \\"hi\\"/s.sh"]')
        expectFailure('Jasper exited (3) before start-up settled') { runAotTraining(fake('exits', 'exit 3\n'), work, 20, 20) }
        expectFailure('start-up settled without OUTPUT_PAINTED, so the cache would miss') {
            runAotTraining(fake('no-output', settles('"WINDOW_SHOWN":1.0,"PLUGINS_BOUND":3.0', true)), work, 20, 20)
        }
        expectFailure('start-up did not settle within 1 s') { runAotTraining(fake('stalls', 'while :; do sleep 1; done\n'), work, 1, 20) }
        expectFailure('the JVM wrote no cache') { runAotTraining(fake('no-cache', settles(complete, false)), work, 20, 20) }
        logger.lifecycle('Verified AOT training orchestration against stand-in launchers')
    }
}
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew :jasper-app:verifyPackagingSupport`
Expected: FAIL, `Could not get unknown property 'aotCacheOption'`.

- [ ] **Step 3: Implement the training helpers.** Append to `gradle/packaging-support.gradle`:

```groovy
// The launcher option that makes the JVM use the trained cache; trainAotCache adds it to Jasper.cfg.
ext.aotCacheOption = '-XX:AOTCache=$APPDIR/jasper.aot'

// config with option as a java-options line at the end of [JavaOptions] (a new section if there is none), replacing
// every earlier line for the same option name (the text up to its first '=').
ext.withJavaOption = { String config, String option ->
    String line = 'java-options=' + option
    boolean named = option.contains('=')
    String name = named ? line.substring(0, line.indexOf('=', 'java-options='.length()) + 1) : line
    List<String> lines = config.readLines().findAll { it != line && !(named && it.startsWith(name)) }
    int section = lines.indexOf('[JavaOptions]')
    if (section < 0) return (lines + ['', '[JavaOptions]', line]).join('\n') + '\n'
    int insert = section + 1
    for (int index = section + 1; index < lines.size() && !lines[index].startsWith('['); index++) {
        if (lines[index].startsWith('java-options=')) insert = index + 1
    }
    lines.add(insert, line)
    lines.join('\n') + '\n'
}

// JAVA_TOOL_OPTIONS for options that may contain spaces or quotes, quoted the way the JDK quotes them for its own AOT
// assembly child (jdk.internal.misc.CDS.ProcessLauncher): a whitespace or double-quote character inside single
// quotes, a single quote inside double quotes. The JVM's parser and a POSIX shell's eval both undo it.
ext.javaToolOptions = { List<String> options ->
    options.collect { String option ->
        StringBuilder quoted = new StringBuilder()
        for (char c : option.toCharArray()) {
            String text = String.valueOf(c)
            if (text == '"' || Character.isWhitespace(c)) quoted.append("'").append(text).append("'")
            else if (text == "'") quoted.append('"').append(text).append('"')
            else quoted.append(text)
        }
        quoted.toString()
    }.join(' ')
}

// The stand-in for the login shell while training: one prompt, then it reads and discards input (anything typed into
// the training window) until the terminal closes.
ext.aotTrainingShell = "#!/bin/sh\nprintf 'jasper-aot-training\$ '\nexec /bin/cat >/dev/null\n"

// The training launch's config.toml: the stand-in shell instead of the login shell, and no resident process.
ext.aotTrainingConfig = { File shell ->
    String path = shell.absolutePath.replace('\\', '\\\\').replace('"', '\\"')
    '# trainAotCache: a short-lived Jasper with a stand-in shell and no resident process.\n' +
        '[system]\nrun_in_background = false\n\n' +
        '[terminal.shell]\nprogram = "/bin/sh"\nargs = ["' + path + '"]\n'
}

// Why a settled start-up trace (Plan 1's JSON) is unfit to train from, or null: it must be this launch's, and a cold
// start that showed its window, painted the shell's output and bound every plugin.
ext.trainingTraceProblem = { Map trace, long pid ->
    if (((trace.pid ?: -1) as long) != pid) return "the trace belongs to process ${trace.pid}, not the training launch ${pid}".toString()
    if (trace.kind != 'cold') return "the launch was '${trace.kind}', not a cold start with a window".toString()
    Map milestones = (trace.milestones ?: [:]) as Map
    List<String> missing = ['WINDOW_SHOWN', 'OUTPUT_PAINTED', 'PLUGINS_BOUND'].findAll { !milestones.containsKey(it) }
    missing ? "start-up settled without ${missing.join(', ')}".toString() : null
}

// One training launch of launcher (the image's Contents/MacOS/Jasper, so jpackage's launcher sets everything a real
// launch has) in work, isolated like startupBench: user.home is work/home with the training configuration, and
// JAVA_TOOL_OPTIONS asks for the cache, the start-up trace and no Dock icon. Once the trace has settled, SIGTERM; the
// JVM assembles the cache while it exits, running the runtime's bin/java (whose output lands in the same log, and
// whose JAVA_TOOL_OPTIONS the JVM replaces). Returns the cache; throws with the log's tail on any failure.
ext.runAotTraining = { File launcher, File work, long settleSeconds, long exitSeconds ->
    work.deleteDir()
    File home = new File(work, 'home')
    File config = new File(home, '.config/jasper/config.toml')
    config.parentFile.mkdirs()
    File shell = new File(work, 'training-shell.sh')
    shell.setText(aotTrainingShell, 'UTF-8')
    shell.setExecutable(true, false)
    config.setText(aotTrainingConfig(shell), 'UTF-8')
    File trace = new File(work, 'startup-trace.json')
    File cache = new File(work, 'jasper.aot')
    File log = new File(work, 'training.log')
    def builder = new ProcessBuilder(launcher.absolutePath).directory(work).redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.to(log))
    Map<String, String> environment = builder.environment()
    ['JASPER_HOME', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH'].each { environment.remove(it) }
    environment.put('JAVA_TOOL_OPTIONS', javaToolOptions(["-XX:AOTCacheOutput=${cache.absolutePath}",
        "-Duser.home=${home.absolutePath}", "-Djasper.startupTrace=${trace.absolutePath}", '-Dapple.awt.UIElement=true']*.toString()))
    def tail = { log.isFile() ? log.readLines('UTF-8').takeRight(40).join('\n') : '(no output)' }
    Process process = builder.start()
    try {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(settleSeconds)
        String problem
        while (true) {
            Map settled = null
            if (trace.isFile()) {
                try { settled = new JsonSlurper().parse(trace, 'UTF-8') as Map } catch (JsonException ignored) { }
            }
            if (settled?.settled == true) {
                problem = trainingTraceProblem(settled, process.pid())
                break
            }
            if (!process.isAlive()) throw new GradleException("AOT training: Jasper exited (${process.exitValue()}) before start-up settled\n${tail()}")
            if (System.nanoTime() > deadline) throw new GradleException("AOT training: start-up did not settle within ${settleSeconds} s\n${tail()}")
            Thread.sleep(200)
        }
        process.destroy()
        if (!process.waitFor(exitSeconds, TimeUnit.SECONDS)) {
            throw new GradleException("AOT training: Jasper did not exit within ${exitSeconds} s of SIGTERM\n${tail()}")
        }
        if (problem != null) throw new GradleException("AOT training: ${problem}, so the cache would miss that part of start-up\n${tail()}")
        if (!cache.isFile() || cache.length() == 0) throw new GradleException("AOT training: the JVM wrote no cache to ${cache}\n${tail()}")
        return cache
    } finally {
        if (process.isAlive()) {
            process.destroyForcibly()
            process.waitFor(10, TimeUnit.SECONDS)
        }
    }
}

// The headless acceptance check: the image's java with the launcher's JVM options and class path, in file order, and
// -XX:AOTMode=on, which exits with an error instead of running without a stale or broken cache.
ext.aotCheckCommand = { File java, String config, File appDir ->
    ([java.absolutePath] + launcherJavaOptions(config, appDir) +
        ['-XX:AOTMode=on', '-cp', launcherClasspath(config, appDir).join(File.pathSeparator), '-version'])*.toString()
}
```

- [ ] **Step 4: Run the fixtures to make sure they pass**

Run: `./gradlew :jasper-app:verifyPackagingSupport`
Expected: `BUILD SUCCESSFUL` with `Verified launcher configuration edits and the AOT checks` and `Verified AOT training orchestration against stand-in launchers` (about 6 s for the stand-ins).

- [ ] **Step 5: Wire the cache check, the task and the policy into `gradle/packaging.gradle`.**

After `def markerFile = …` (line 24) add:

```groovy
// -Pjasper.aot=true makes packageApp (and verifyPackage) train the AOT cache; packageDist on macOS always does.
def aotRequested = providers.gradleProperty('jasper.aot').map { it == 'true' }.getOrElse(false)
```

After `installBundledNatives` (Task 2) add:

```groovy
// The image's own java with the launcher's JVM options and class path and -XX:AOTMode=on, which exits with an error
// instead of running without a stale or broken cache. Returns false when Jasper.cfg names no AOT cache.
def verifyAotCache = { File runtimeHome, File appDir ->
    String config = new File(appDir, 'Jasper.cfg').getText('UTF-8')
    if (!config.readLines().contains('java-options=' + aotCacheOption)) return false
    File cache = new File(appDir, 'jasper.aot')
    if (!cache.isFile() || cache.length() == 0) {
        throw new GradleException("Package verification failed: Jasper.cfg names an AOT cache, but ${cache} is missing or empty")
    }
    def result = executeNative(aotCheckCommand(new File(runtimeHome, 'bin/java'), config, appDir),
        'AOT cache check (java -XX:AOTMode=on): the JVM refuses the cache; train it again with trainAotCache')
    String output = result.stdout + result.stderr
    if (!output.contains('sharing')) {
        throw new GradleException("Package verification failed: java ran without class data sharing although it accepted the AOT cache\n${output}")
    }
    true
}
```

In `verifyPackage`, change the Task 2 line

```groovy
        List<String> permittedOptions = requiredOptions + ["-Djpackage.app-version=${context.version}".toString()]
```

to

```groovy
        List<String> permittedOptions = requiredOptions + ["-Djpackage.app-version=${context.version}".toString()] +
            (context.host.os == 'macos' ? [aotCacheOption] : [])
```

and in its macOS block, after `executeNative(['/usr/bin/codesign', '--verify', '--deep', '--strict', image.absolutePath], 'codesign verification')`, add:

```groovy
            if (verifyAotCache(runtimeHome, appDir)) {
                logger.lifecycle("AOT cache accepted (-XX:AOTMode=on): ${new File(appDir, 'jasper.aot').length().intdiv(1024 * 1024)} MB")
            } else {
                logger.lifecycle('No AOT cache in this image: packageDist and -Pjasper.aot=true train one')
            }
```

In the Windows branch, directly after `if (normalizedHost().os == 'windows') {`, and in the Linux branch, directly after `} else if (normalizedHost().os == 'linux') {`, add:

```groovy
    if (aotRequested) throw new GradleException('-Pjasper.aot=true: AOT training runs on macOS only')
```

In the macOS branch (the final `} else {`), insert before `tasks.register('packageDist') {`:

```groovy
    def aotTrainingDirectory = packagingRoot.map { it.dir('aot-training') }
    // Launches the image once: a Jasper window for a few seconds, without a Dock icon. Agents ask the user first.
    // Training changes packageApp's output, so the next packageApp rebuilds the image from scratch, untrained.
    def trainAotCache = tasks.register('trainAotCache') {
        group = 'distribution'
        description = 'Launches the packaged app once (a window for a few seconds, no Dock icon) to record its AOT cache.'
        dependsOn(packageApp)
        doLast {
            File image = new File(imageDirectory.get().asFile, 'Jasper.app')
            File appDir = new File(image, 'Contents/app')
            File config = new File(appDir, 'Jasper.cfg')
            if (config.readLines('UTF-8').contains('java-options=' + aotCacheOption)) {
                throw new GradleException("${image} already has an AOT cache; packageApp rebuilds the image before training")
            }
            File cache = runAotTraining(new File(image, 'Contents/MacOS/Jasper'), aotTrainingDirectory.get().asFile, 90, 300)
            Files.copy(cache.toPath(), new File(appDir, 'jasper.aot').toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            config.setText(withJavaOption(config.getText('UTF-8'), aotCacheOption), 'UTF-8')
            resignImage(image)
            if (!verifyAotCache(new File(image, 'Contents/runtime/Contents/Home'), appDir)) {
                throw new GradleException('AOT training left Jasper.cfg without the cache option')
            }
            logger.lifecycle("Trained the AOT cache: ${new File(appDir, 'jasper.aot').length().intdiv(1024 * 1024)} MB in ${appDir}")
        }
    }
    verifyPackage.configure { mustRunAfter(trainAotCache) }
    if (aotRequested) {
        packageApp.configure { finalizedBy(trainAotCache) }
        verifyPackage.configure { dependsOn(trainAotCache) }
    }
```

(`packageDist` starts depending on `trainAotCache` only in Task 4, once its DMG keeps modification times.)

- [ ] **Step 6: Run the headless checks**

Run: `./gradlew :jasper-app:verifyPackagingSupport :jasper-app:packageApp :jasper-app:verifyPackage`
Expected: `BUILD SUCCESSFUL`, with `No AOT cache in this image: packageDist and -Pjasper.aot=true train one`. Also `./gradlew :jasper-app:tasks --group distribution` lists `trainAotCache`.

- [ ] **Step 7: Train once — ask the user first** (GUI: a Jasper window for a few seconds)

Ask: "Task 3 needs one AOT training launch: Jasper opens a window for a few seconds (no Dock icon) from the packaged image, with an isolated home and no login shell. Run `./gradlew :jasper-app:verifyPackage -Pjasper.aot=true` now?" If declined, record "Task 3 training run pending" in this plan's Status line and continue with Step 9; Task 4's approved `packageDist` run covers it.

Run (only with approval): `./gradlew :jasper-app:verifyPackage -Pjasper.aot=true`
Expected: `Trained the AOT cache: <about 67> MB …`, then `AOT cache accepted (-XX:AOTMode=on): … MB` and `Verified Jasper …`. `jasper-app/build/packaging/aot-training/startup-trace.json` has `"settled": true` and the three milestones; `training.log` contains no `[error]` line.

- [ ] **Step 8: Show that a time-changing copy is refused** (headless; needs the trained image from Step 7)

```bash
scratch="$(mktemp -d)"
for copy in ditto cp; do
  if [ "$copy" = ditto ]; then ditto jasper-app/build/packaging/image/Jasper.app "$scratch/$copy/Jasper.app"
  else mkdir -p "$scratch/$copy" && cp -R jasper-app/build/packaging/image/Jasper.app "$scratch/$copy/Jasper.app"; fi
  app="$scratch/$copy/Jasper.app/Contents/app"
  classpath=$(sed -n 's/^app\.classpath=//p' "$app/Jasper.cfg" | sed "s#\$APPDIR#$app#" | paste -sd: -)
  "$scratch/$copy/Jasper.app/Contents/runtime/Contents/Home/bin/java" --enable-native-access=ALL-UNNAMED \
    -XX:AOTCache="$app/jasper.aot" -XX:AOTMode=on -cp "$classpath" -version >/dev/null 2>"$scratch/$copy.err"
  echo "$copy: exit $?"; head -3 "$scratch/$copy.err"
done
rm -rf "$scratch"
```

Expected: `ditto: exit 0`; `cp: exit 1` with an AOT cache error naming a jar whose time changed. Record both lines for Task 5.

- [ ] **Step 9: Run the whole suite**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```bash
git add gradle/packaging-support.gradle gradle/packaging-support-test.gradle gradle/packaging.gradle
git commit -F - <<'EOF'
build(packaging): train a JDK 25 AOT cache from the packaged app

trainAotCache launches the image's own launcher once with an isolated
home, a stand-in shell and no Dock icon, waits for Plan 1's start-up
trace to settle with output painted and plugins bound, sends SIGTERM
and installs the cache the JVM writes at exit as Contents/app/jasper.aot
with -XX:AOTCache in Jasper.cfg. packageApp trains with
-Pjasper.aot=true. verifyPackage runs the image's java with
-XX:AOTMode=on, which refuses a stale or broken cache. The training
orchestration is tested against /bin/sh stand-ins.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: A release DMG that keeps the cache valid

**Files:**
- Modify: `gradle/packaging-support.gradle` (append), `gradle/packaging-support-test.gradle` (append)
- Modify: `gradle/packaging.gradle` (the macOS `packageDist`, lines 674–706)
- Modify: `packaging/README.txt` (macOS paragraph)

**Interfaces:**
- Consumes: Task 3's `trainAotCache`, `verifyAotCache`; `nativeContext`, `executeNative`, `distributionDirectory`, `packagingRoot`.
- Produces: `ext.modificationTimeMismatches(File expectedRoot, File actualRoot) → List<String>` (sorted relative paths); macOS `packageDist` depends on `trainAotCache` and `verifyPackage` and builds `Jasper-<version>-macos-<arch>.dmg` with `ditto` and `hdiutil`.

- [ ] **Step 1: Write the failing fixture.** Append to `gradle/packaging-support-test.gradle`:

```groovy
verifySupport.configure {
    doLast {
        File original = new File(temporaryDir, 'times/original')
        File copied = new File(temporaryDir, 'times/copied')
        [original, copied].each { it.deleteDir(); new File(it, 'plugins').mkdirs() }
        long stamp = 1_759_400_000_000L
        ['jasper-app.jar', 'plugins/tool.jar', 'jasper.aot'].each { String name ->
            [original, copied].each { File root ->
                File file = new File(root, name)
                file.text = name
                assert file.setLastModified(stamp)
            }
        }
        assert modificationTimeMismatches(original, copied).isEmpty()
        assert new File(copied, 'plugins/tool.jar').setLastModified(stamp + 1_000)
        assert new File(copied, 'jasper.aot').delete()
        assert new File(copied, 'jasper-app.jar').setLastModified(stamp + 400)   // same second: HFS+ and the JVM keep whole seconds
        assert modificationTimeMismatches(original, copied) == ['jasper.aot', "plugins${File.separator}tool.jar".toString()]
        logger.lifecycle('Verified modification-time comparison')
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :jasper-app:verifyPackagingSupport`
Expected: FAIL, `Could not get unknown property 'modificationTimeMismatches'`.

- [ ] **Step 3: Implement the helper.** Append to `gradle/packaging-support.gradle`:

```groovy
// Files under expectedRoot whose copy under actualRoot is missing or has another modification time, to the second
// (HFS+ stores whole seconds, and so does the JVM's check of the class-path jars an AOT cache was trained with).
ext.modificationTimeMismatches = { File expectedRoot, File actualRoot ->
    List<String> mismatches = []
    def expected = expectedRoot.toPath(), actual = actualRoot.toPath()
    Files.walk(expected).withCloseable { paths ->
        paths.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }.forEach { file ->
            String relative = expected.relativize(file).toString()
            def copy = actual.resolve(relative)
            if (!Files.isRegularFile(copy, LinkOption.NOFOLLOW_LINKS) ||
                    Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).to(TimeUnit.SECONDS) !=
                    Files.getLastModifiedTime(copy, LinkOption.NOFOLLOW_LINKS).to(TimeUnit.SECONDS)) {
                mismatches << relative
            }
        }
    }
    mismatches.sort()
}
```

- [ ] **Step 4: Run it to make sure it passes**

Run: `./gradlew :jasper-app:verifyPackagingSupport`
Expected: `BUILD SUCCESSFUL`, `Verified modification-time comparison`.

- [ ] **Step 5: Rebuild the macOS `packageDist`.** Replace the whole macOS `tasks.register('packageDist') { … }` (from `    tasks.register('packageDist') {` after the Task 3 insertion to its closing `    }`) with:

```groovy
    tasks.register('packageDist') {
        group = 'distribution'
        description = 'Trains the AOT cache, verifies the image and creates the macOS DMG (launches Jasper once: ask first).'
        dependsOn(trainAotCache, verifyPackage)
        inputs.dir(imageDirectory).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.property('version', version)
        outputs.files(version.map { new File(distributionDirectory.get().asFile, "Jasper-${it}-macos-${normalizedHost().architecture}.dmg") })
        doLast {
            def context = nativeContext()
            File image = new File(imageDirectory.get().asFile, 'Jasper.app')
            File distDir = distributionDirectory.get().asFile
            distDir.mkdirs()
            File artifact = new File(distDir, "Jasper-${context.version}-macos-${context.host.architecture}.dmg")
            delete(artifact)
            File temporary = new File(packagingRoot.get().asFile, 'dist-dmg-work')
            delete(temporary)
            File volume = new File(temporary, 'volume')
            File mount = new File(temporary, 'mount')
            volume.mkdirs()
            mount.mkdirs()
            try {
                // jpackage --type dmg copies the image without modification times (Files.copy without COPY_ATTRIBUTES),
                // and the JVM then refuses the AOT cache and the CDS archive. ditto and hdiutil keep the times.
                executeNative(['/usr/bin/ditto', image.absolutePath, new File(volume, 'Jasper.app').absolutePath], 'ditto Jasper.app into the DMG folder')
                Files.createSymbolicLink(new File(volume, 'Applications').toPath(), new File('/Applications').toPath())
                int attempt = 0
                while (true) {
                    attempt++
                    try {
                        executeNative(['/usr/bin/hdiutil', 'create', '-volname', 'Jasper', '-srcfolder', volume.absolutePath,
                            '-fs', 'HFS+', '-format', 'UDZO', '-ov', artifact.absolutePath], 'hdiutil create')
                        break
                    } catch (GradleException busy) {
                        // hdiutil sometimes reports a busy resource; jpackage retries for the same reason.
                        if (attempt == 3) throw busy
                        Thread.sleep(2000)
                    }
                }
                executeNative(['/usr/bin/hdiutil', 'verify', artifact.absolutePath], 'hdiutil verify')
                // Check what a user installs: mounted read-only and hidden from Finder, no window opens.
                executeNative(['/usr/bin/hdiutil', 'attach', artifact.absolutePath, '-readonly', '-nobrowse', '-noautoopen',
                    '-mountpoint', mount.absolutePath], 'hdiutil attach')
                try {
                    File installed = new File(mount, 'Jasper.app')
                    List<String> changed = modificationTimeMismatches(new File(image, 'Contents/app'), new File(installed, 'Contents/app'))
                    if (changed) throw new GradleException("DMG verification failed: modification times differ for ${changed.take(10)}; the JVM would refuse the AOT cache")
                    executeNative(['/usr/bin/codesign', '--verify', '--deep', '--strict', installed.absolutePath], 'codesign verification of the DMG copy')
                    if (!verifyAotCache(new File(installed, 'Contents/runtime/Contents/Home'), new File(installed, 'Contents/app'))) {
                        throw new GradleException('DMG verification failed: the app in the DMG has no AOT cache')
                    }
                } finally {
                    try {
                        executeNative(['/usr/bin/hdiutil', 'detach', mount.absolutePath], 'hdiutil detach')
                    } catch (GradleException detachFailure) {
                        logger.warn("Could not detach ${mount}: ${detachFailure.message}")
                    }
                }
            } catch (Exception failure) {
                delete(artifact)
                throw failure
            } finally {
                if (!new File(mount, 'Jasper.app').exists()) delete(temporary)
            }
            logger.lifecycle("Created ${artifact}")
        }
    }
```

In `packaging/README.txt`, replace the first paragraph's first sentence

```
macOS: open the DMG and drag Jasper.app to Applications. The current development
```

with

```
macOS: open the DMG and drag Jasper.app to Applications. Copy it with Finder or
ditto: a copy that resets file modification times, such as cp -R, still runs
but loses Jasper's start-up cache. The current development
```

(`verifyPackage` compares this staged file with the packaged copy, so no other change is needed.)

- [ ] **Step 6: Run the headless checks**

Run: `./gradlew :jasper-app:verifyPackagingSupport :jasper-app:packageApp :jasper-app:verifyPackage`
Expected: `BUILD SUCCESSFUL` (the image is untrained; `packageDist` is not run here). `./gradlew :jasper-app:tasks --group distribution` shows the new `packageDist` description.

- [ ] **Step 7: Build the release DMG — ask the user first** (GUI: training opens a Jasper window for a few seconds)

Ask: "Task 4 builds the release DMG, which trains the AOT cache: Jasper opens a window for a few seconds (no Dock icon). Run `./gradlew :jasper-app:packageDist` now?" If declined, record "Task 4 packageDist run pending" in the Status line and continue.

Run (only with approval): `./gradlew :jasper-app:packageDist`
Expected: in order `Trained the AOT cache …`, `AOT cache accepted …`, `Verified Jasper …`, `Created …/jasper-app/build/packaging/dist/Jasper-1.0.0-macos-aarch64.dmg`. Then `ls -lh jasper-app/build/packaging/dist/` shows the DMG, and `jasper-app/build/packaging/dist-dmg-work` no longer exists.

- [ ] **Step 8: Run the whole suite**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add gradle/packaging-support.gradle gradle/packaging-support-test.gradle gradle/packaging.gradle packaging/README.txt
git commit -F - <<'EOF'
build(packaging): release DMG keeps the AOT cache valid

jpackage's DMG step copies the app image without modification times,
which makes the JVM refuse the AOT cache and the CDS archive. The macOS
packageDist now always trains, copies the verified image with ditto,
creates the DMG with hdiutil, and mounts it read-only and hidden from
Finder to check the installed copy's times, signature and cache
(-XX:AOTMode=on). The package README tells users to copy with Finder.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: Documentation, status and the before/after measurement

**Files:**
- Modify: `AGENTS.md`, `README.md`, `docs/packaging.md`, `docs/plugin-authoring.md`, `jasper-sdk/README.md`, `docs/app-maintenance.md`, `docs/diagnostics.md`, `docs/benchmarks.md`, `docs/STATUS.md`
- Modify (after the user-approved measurement): `docs/benchmarks/2026-10-02-startup-baseline.md` (created by Plan 1)

**Interfaces:**
- Consumes: everything above; the runtime size from Task 1 Step 8; the Task 3 Step 8 lines; Plan 1's `run-macos.sh … startup …`.
- Produces: the AGENTS.md policy note; `docs/packaging.md` sections `## Start-up performance`, `### Training the AOT cache`, `### Keeping the cache valid` (anchors `start-up-performance`, `keeping-the-cache-valid`); the macOS and Windows checklist items.

- [ ] **Step 1: AGENTS.md.** In `## Never do these without the user`, after the `**Do not launch the GUI**` bullet, add:

```markdown
- **Do not train the AOT cache without asking.** On macOS, `./gradlew :jasper-app:packageDist`, `:jasper-app:trainAotCache`
  and any packaging task with `-Pjasper.aot=true` launch the packaged app once (a window for a few seconds, no Dock icon)
  to record `Contents/app/jasper.aot`. Ask the user first. `packageApp` and `verifyPackage` without that property stay
  headless.
```

- [ ] **Step 2: README.md.** In "Build a macOS app and DMG", replace

```markdown
This compiles Jasper, runs the headless tests, creates `Jasper.app` with its own JBR runtime, verifies the application image, and builds and verifies the DMG. It does not launch Jasper.
```

with

```markdown
This compiles Jasper, runs the headless tests, creates `Jasper.app` with its own trimmed JBR runtime, launches it once for a few seconds (no Dock icon) to train its start-up cache, verifies the application image, and builds and verifies the DMG. See [start-up performance](docs/packaging.md#start-up-performance).
```

- [ ] **Step 3: docs/packaging.md.** In the macOS command block near the top, change the line `./gradlew check :jasper-app:packageDist` to `./gradlew check :jasper-app:packageDist   # trains the AOT cache: a Jasper window for a few seconds`, and directly after that code block add:

```markdown
On macOS, `packageDist` launches the packaged app once to train its start-up cache; run it from your desktop session.
See [Start-up performance](#start-up-performance).
```

Then insert before `## macOS Local Network access`:

```markdown
## Start-up performance

Part 2 of the [start-up design](superpowers/specs/2026-10-02-jasper-startup-speed-design.md) makes the packaged app
start faster without application code. `gradle/packaging-support.gradle` holds the lists and helpers;
`verifyPackagingSupport` (part of `check`) tests them with fixtures, and `verifyPackage` checks a built image.

- **Trimmed runtime.** jpackage gets an explicit module list (`runtimeRootModules`) instead of every module with
  `--bind-services`, which also bound javac, jshell, jlink, jpackage and JBR's JCEF and JOGL (192 MB). The list is the
  runtime plugins may rely on ([The Java runtime](plugin-authoring.md#the-java-runtime)). `verifyPackage` fails when
  the packaged runtime has other modules or when `jdeps` finds that the application or a bundled plugin needs one it
  lacks, and logs the runtime's size. `lib/modules` stays uncompressed (jlink's default), which loads classes faster;
  `bin/java` stays because the AOT cache assembly runs it.
- **Default CDS archive.** jlink's `--generate-cds-archive` writes `lib/server/classes.jsa` (`bin\server` on Windows),
  the fallback whenever the AOT cache cannot be used. `verifyPackage` runs the bundled `java -Xshare:on`.
- **Native libraries in the app (macOS and Windows).** FlatLaf's window library, JNA's dispatch library and pty4j's
  `libpty` and spawn helper (its ConPTY files on Windows) are copied byte for byte from their jars into
  `Contents/app/native/` (`app\native\`), keeping their vendor signatures, and `Jasper.cfg` points the libraries there
  (`flatlaf.nativeLibraryPath`, `jna.boot.library.path` with `jna.nosys`, `pty4j.preferred.native.folder`). No launch
  extracts a copy under a new name, so macOS can reuse its signature checks. `verifyPackage` compares each file with its
  jar, checks its signature on macOS and runs `BundledNativesProbe` headless with the image's runtime, options and class
  path: `-Xlog:library` must show each library loaded from the image, and the probe's temporary folders must stay
  empty. Linux keeps extraction: FlatLaf's Linux library links GTK 3, which would become a package dependency.
- **AOT cache (macOS).** `trainAotCache` launches the freshly built image once and records a JDK 25 AOT cache,
  `Contents/app/jasper.aot` (about 67 MB), then adds `-XX:AOTCache=$APPDIR/jasper.aot` to `Jasper.cfg` and re-signs.
  `packageDist` always trains; `packageApp` trains only with `-Pjasper.aot=true`. The cache holds the JDK's and the
  application class path's classes, not plugin classes, which come from their own class loaders. `verifyPackage` runs
  the image's `java -XX:AOTMode=on` with the launcher's options and class path, which fails for a stale or broken cache.
  `Jasper.cfg` carries no other JVM tuning: C1-only compilation and SerialGC measured no gain.

### Training the AOT cache

The training launch is isolated like `startupBench`: a temporary `user.home` under
`jasper-app/build/packaging/aot-training/` whose configuration replaces the login shell with a stand-in that prints
one prompt and discards input, keeps residency off and enables the bundled plugins, plus `-Dapple.awt.UIElement=true`
(no Dock icon or menu bar) and `-Djasper.startupTrace`. It needs your desktop session: a Jasper window appears for a
few seconds where macOS places new windows, and anything typed into it goes to the stand-in. When the trace reports
start-up settled with the shell's output painted and every plugin bound, the task sends SIGTERM and the JVM assembles
the cache as it exits. A launch that exits early, never settles, or settles without its window, output or plugins
fails the task. The trace, the log and the isolated home stay in `aot-training/` for diagnosis.

### Keeping the cache valid

The JVM uses the cache only while every class-path jar keeps the size and modification time it had at training;
otherwise it logs an error and starts without the cache and without the CDS archive, slower than an untrained app.
Moving the app or copying it with Finder, `ditto` or the DMG keeps the times; `cp -R` and some sync tools do not.
jpackage's own DMG step copies without times, so `packageDist` builds the DMG with `ditto` and `hdiutil`, mounts it
read-only and hidden from Finder, and checks the installed copy's times, signature and cache. The DMG holds
`Jasper.app` and an `Applications` link, without jpackage's background picture.
```

In `### macOS` of the acceptance checklist, after the first item, add:

```markdown
- [ ] After installing from the DMG with Finder, launch Jasper from the Dock: the start-up line in `~/.config/jasper/logs` reports the AOT cache as used.
- [ ] After several launches, no new `pty4j` folders or `libflatlaf-macos-*` files appear in `$TMPDIR` and nothing new appears in `~/Library/Caches/JNA/temp`.
- [ ] Copy the installed app with `cp -R` to another folder and launch the copy: it starts, and its start-up line reports the cache as stale.
```

In `### Windows`, after the ZIP item ("Build on Windows with a JBR 25 x64 SDK, extract the entire ZIP …"), add:

```markdown
- [ ] Confirm `app\native\` holds FlatLaf's DLL, `jnidispatch.dll` and pty4j's `win\x86-64` files, and that launching Jasper creates no `flatlaf-windows-*` file, `jna*.tmp` file or `pty4j` folder under `%TEMP%`.
```

- [ ] **Step 4: docs/plugin-authoring.md and jasper-sdk/README.md.** Append this paragraph to the `## The Java runtime` section written in Task 1, directly before `## Distributing a plugin`:

```markdown
The packaged JVM loads JNA's native library from Jasper's own copy (`jna.boot.library.path`, JNA 5.14.0) and points
pty4j at Jasper's bundled files (`pty4j.preferred.native.folder`). Both properties are process-wide. A plugin that
bundles JNA should bundle the same version: another version fails to load its native part if it is the first in the
process to initialise JNA. Start terminals through `context.terminals()` rather than a pty4j of your own.
```

In `jasper-sdk/README.md`, after the paragraph that starts "Start with the [authoring guide]", add:

```markdown
Packaged Jasper ships a trimmed Java runtime: [The Java runtime](../docs/plugin-authoring.md#the-java-runtime) lists
the JDK modules a plugin may rely on.
```

- [ ] **Step 5: docs/app-maintenance.md, docs/diagnostics.md, docs/benchmarks.md.** In `docs/app-maintenance.md`, insert before `## Session lifecycle change`:

```markdown
## Packaged runtime, bundled natives and the AOT cache

`gradle/packaging-support.gradle` holds what packaging decides: the runtime's modules, the native libraries copied into
the app and the AOT training launch. `verifyPackagingSupport` (in `check`) tests it with fixtures; `verifyPackage`
checks a built image ([start-up performance](packaging.md#start-up-performance)).

- **A plugin or library needs another JDK module.** Add it to `runtimeRootModules` (and to the required list in
  `runtimeModules` when it pulls in a module not yet listed), add it to the `runtime-modules` block of
  [The Java runtime](plugin-authoring.md#the-java-runtime), and rebuild: `verifyPackagingSupport` fails until the
  documented list matches, and `verifyPackage`'s `jdeps` check names any module a bundled plugin still lacks.
- **Upgrading FlatLaf, JNA or pty4j.** Check that the resources in `bundledNatives` still exist under the same names
  and that the properties in `bundledNativeJvmOptions` still apply. `BundledNativesProbeTest` (macOS, in `check`)
  loads them from a folder laid out like the image; `verifyPackage` fails when a jar lacks an entry or the image loads
  a library from elsewhere.
- **The AOT cache.** It is trained per build, so it follows class-path changes automatically; never edit a jar inside a
  trained image, rebuild it. Training opens a window: agents ask the user before `packageDist`, `trainAotCache` or
  `-Pjasper.aot=true`.
```

Append to `docs/diagnostics.md`:

```markdown
## Start-up cache (packaged macOS app)

The packaged macOS app starts with an AOT cache (`Contents/app/jasper.aot`) trained when it was built, and the start-up
line in the log says whether the JVM used it. If it reports the cache as stale, a copy changed the application's file
modification times (for example `cp -R`): Jasper works, but starts slower than an app without a cache, because the JVM
then also skips the runtime's default CDS archive. Reinstall from the DMG with Finder. See
[keeping the cache valid](packaging.md#keeping-the-cache-valid).
```

In `docs/benchmarks.md`, `## Preserve and invoke a packaged image`, after the sentence ending `A later package build replaces its build outputs.`, add: "Copy with `ditto`, as below: it keeps modification times, so a trained AOT cache stays valid; `cp -R` does not."

- [ ] **Step 6: Run the documentation test and the whole suite**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.AppDocumentationTest'`
Expected: PASS (all new links and anchors resolve).

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL`. Record the totals with the script from Task 1 Step 9.

- [ ] **Step 7: docs/STATUS.md.** Under `## Current state — 2026-10-02`, add as the first bullet (replace `RUNTIME_MB` with the size Task 1 Step 8 printed, `CHECK_TOTALS` with Step 6's totals as `tests/failures/errors/skipped`, and list under "Not done" whichever of Task 3 Step 7, Task 4 Step 7 and this task's Step 9 were not run):

```markdown
- **Start-up Part 2: JVM and packaging** (branch `claude/startup-speed`, worktree `.claude/worktrees/startup-speed`,
  plan `docs/superpowers/plans/2026-10-02-startup-2-jvm-packaging.md`): packaged Jasper ships a trimmed runtime (41
  modules, 42 on Windows; `runtimeRootModules` in `gradle/packaging-support.gradle`, documented in plugin-authoring
  "The Java runtime"; RUNTIME_MB MB, was 192 MB) with jlink's default CDS archive; loads FlatLaf's, JNA's and pty4j's
  native libraries from `Contents/app/native` on macOS and Windows (Linux keeps extraction: FlatLaf's Linux library
  links GTK 3); and on macOS carries a JDK 25 AOT cache from `trainAotCache` (`packageDist` always trains,
  `packageApp` with `-Pjasper.aot=true`; AGENTS.md: ask first, it opens a window). `packageDist` builds the DMG with
  `ditto` and `hdiutil` because jpackage's DMG copy resets modification times, which invalidates the cache, and checks
  the mounted copy. `verifyPackage` checks modules, CDS, natives (bytes, signatures, a headless load probe), JVM options
  and the cache (`-XX:AOTMode=on`). Full check: **CHECK_TOTALS**. Not done: Windows packaging run (not verifiable on
  the Mac), the GUI checklist items in `docs/packaging.md`, push.
```

- [ ] **Step 8: Commit**

```bash
git add AGENTS.md README.md docs/packaging.md docs/plugin-authoring.md jasper-sdk/README.md docs/app-maintenance.md docs/diagnostics.md docs/benchmarks.md docs/STATUS.md
git commit -F - <<'EOF'
docs: start-up Part 2 packaging, runtime and AOT cache

AGENTS.md: agents ask before packageDist, trainAotCache or
-Pjasper.aot=true, which open a window. The packaging guide explains the
trimmed runtime, the CDS archive, the bundled natives, AOT training and
how to keep the cache valid, with new macOS and Windows checklist items.
Plugin authors learn about JNA and pty4j's process-wide properties;
maintenance, diagnostics, benchmarks and STATUS are updated.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

- [ ] **Step 9: Before/after start-up comparison — ask the user first** (GUI: 20 launches through LaunchServices; no game or VM running)

Ask: "Part 2 is ready for the startupBench comparison: 10 cold launches of the Plan 1 image and 10 of the trained Part 2 image, each opening and quitting a Jasper window, about two minutes. Run it now?" Only with approval, and only from a trained image (Task 4 Step 7):

```bash
mkdir -p "$HOME/jasper-bench/startup-part2/after"
rm -rf "$HOME/jasper-bench/startup-part2/after/Jasper.app"
ditto jasper-app/build/packaging/image/Jasper.app "$HOME/jasper-bench/startup-part2/after/Jasper.app"
./tools/benchmarks/run-macos.sh "$HOME/jasper-bench/startup-part2/before/Jasper.app" startup \
  "$(cat "$HOME/jasper-bench/startup-part2/before/REVISION")" "$HOME/jasper-bench/startup-part2/before.json" --mode cold --runs 10
./tools/benchmarks/run-macos.sh "$HOME/jasper-bench/startup-part2/after/Jasper.app" startup \
  "$(git rev-parse --short HEAD)" "$HOME/jasper-bench/startup-part2/after.json" --mode cold --runs 10
```

Expected: both reports written; the after launches' traces report `jvm.aotCache` `used` and `cds` `on` (Plan 1's JSON fields); the window-shown and output-painted medians drop by roughly the spec's −640 ms (AOT) plus the native-library savings (spec estimate: cold ~2.3 s → ~1.5 s). Add a section "Part 2 — JVM and packaging" to `docs/benchmarks/2026-10-02-startup-baseline.md` with a table of the median, minimum and maximum per milestone for both images, the two revisions, the runtime sizes (`du -sh …/Contents/runtime` of each image) and the `jasper.aot` size, then commit:

```bash
git add docs/benchmarks/2026-10-02-startup-baseline.md docs/STATUS.md
git commit -F - <<'EOF'
docs(benchmarks): start-up Part 2 before and after

Cold-launch medians from startupBench for the Plan 1 image and the
trained Part 2 image, with runtime and AOT cache sizes.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

(Also update STATUS's "Not done" list to drop the comparison once it is recorded.)
