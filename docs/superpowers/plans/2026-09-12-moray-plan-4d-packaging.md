# Plan 4d Packaging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build native macOS and portable Windows distributions with bundled JBR, ready for manual desktop testing.

**Architecture:** A focused Groovy Gradle script applied by the app module stages dependencies and invokes the existing Java toolchain's jpackage with argument lists. Image verification reads actual output and executes only the bundled Java version command. Launcher environment changes stay in LaunchSettings.

**Tech Stack:** Gradle wrapper 9.7, JBR SDK 25, jpackage/jlink, Groovy build DSL, Java/JUnit.

**Spec:** `docs/superpowers/specs/2026-09-12-moray-plan-4d-packaging-design.md` (approved).

**Status:** Executing. Native validation correction: macOS jpackage requires a positive major version; default changed to 1.0.0 on both hosts, with no post-generation bundle metadata rewriting. Worktree `.worktrees/plan-4d-packaging`, branch `codex/plan-4d-packaging`, baseline `dbf75d9`. Both task gates and whole-branch review are required. The user approved portable Windows ZIP as part of the proposed design; no installer is included.

## Global Constraints

- Use the same Java 25 JetBrains toolchain for compilation, runtime generation and jpackage.
- The entry point remains `dev.moray.app.Main`.
- Keep the existing two Java modules and avoid a new runtime abstraction or installer framework.
- `./gradlew check` remains headless and does not implicitly invoke packaging or require platform packaging tools.
- No GUI or benchmark is launched by agents.
- Task inputs cover dependency files, the application JAR, bundled docs/config, version, platform, toolchain and launch options.
- Any cleanup is limited to task-owned output directories.
- Use argument lists rather than shell command strings so repository paths containing spaces work on both platforms.
- No push, merge, release, signing credentials or CI deployment in this execution.
- All commits end with `Co-Authored-By: Codex <noreply@openai.com>`.

## File structure

`gradle/packaging.gradle` owns packaging tasks; `moray-app/build.gradle.kts` applies it. `packaging/README.txt` is bundled user guidance. `docs/packaging.md` contains developer build commands and manual acceptance. `LaunchSettings.java` owns environment assembly. Its existing test file owns behavior regressions. Config guide/template/example comments describe the resolved behavior. STATUS records verification and remaining native acceptance.

### Task 1: Native images, distributions and package verification

**Files:**
- Create: `gradle/packaging.gradle`, `packaging/README.txt`, `docs/packaging.md`.
- Modify: `moray-app/build.gradle.kts`, `README.md`.
- Verification: real Gradle task execution and actual image/DMG inspection, not tests that duplicate Gradle source constants.

**Interfaces:**
- Consumes: `JavaApplication.mainClass`, `applicationDefaultJvmArgs`, `JavaPluginExtension.toolchain`, `jar.archiveFile`, `configurations.runtimeClasspath` from `moray-app`.
- Produces: `:moray-app:packageApp`, `:moray-app:packageDist`, `:moray-app:verifyPackage`; property `-PmorayVersion=X.Y.Z` default `1.0.0`.
- Output root: `moray-app/build/packaging/`; `input/` for Sync staging, `image/` for the application image, `dist/` for archives. Artifact names: `Moray-<version>-macos-<aarch64|x64>.dmg` and `Moray-<version>-windows-x64.zip`.

- [ ] **Step 1: Prove the task boundary is missing.**

```bash
./gradlew :moray-app:packageApp :moray-app:verifyPackage
```

Expected RED: task not found, with no GUI launch. This is build integration, so the behavioral acceptance is the real generated package. Do not add a buildSrc plugin or Gradle TestKit framework just to test fixed command strings.

- [ ] **Step 2: Register staging and host preflight in the applied script.**

Add to the end of the app build:

```kotlin
apply(from = rootProject.file("gradle/packaging.gradle"))
```

Resolve toolchain provider in the Groovy script:

```groovy
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaToolchainService

def app = extensions.getByType(JavaApplication)
def toolchains = extensions.getByType(JavaToolchainService)
def java = extensions.getByType(JavaPluginExtension)
def launcher = toolchains.launcherFor(java.toolchain)
def version = providers.gradleProperty('morayVersion').orElse('1.0.0')
def jarTask = tasks.named('jar')
def stage = tasks.register('stagePackage', Sync) {
    from(jarTask.flatMap { it.archiveFile })
    from(configurations.runtimeClasspath)
    from(rootProject.file('config.example.toml'))
    from(rootProject.file('packaging/README.txt'))
    into(layout.buildDirectory.dir('packaging/input'))
    duplicatesStrategy = DuplicatesStrategy.FAIL
}
```

Host names: use `os.name` normalized with Locale.ROOT, macOS starts `mac`, Windows starts `windows`; architecture aliases `aarch64/arm64` → `aarch64`, `amd64/x86_64/x64` → `x64`. Only macOS x64/aarch64 and Windows x64 pass packaging preflight. Validate the selected toolchain architecture matches the process host; fail with a clear instruction to use a matching SDK if not. Do not fail merely evaluating the script during `check` on Linux.

Version validator: exactly three decimal components with no leading zeroes except `0`; major 1..255, minor 0..255, patch 0..65535. Bound lengths before parsing to avoid overflow. Throw `GradleException` naming `-PmorayVersion` and the accepted form on error. Ensure version validation precedes native packaging execution and output cleanup. Missing SDK jpackage is an actionable failure naming that SDK path.

- [ ] **Step 3: Invoke jpackage to create the image.**

Construct argument lists, never a shell string. Use Gradle's `ProviderFactory.exec` (available through `providers`) or an injected public `ExecOperations` API; Gradle 9 does not support the old `project.exec`. Propagate nonzero exits and report native output, including on failure.

```groovy
List<String> args = [jpackage.absolutePath, '--type', 'app-image',
    '--name', 'Moray', '--app-version', version.get(),
    '--description', 'Moray terminal', '--vendor', 'Moray',
    '--input', inputDir.absolutePath, '--dest', imageDir.absolutePath,
    '--main-jar', jarTask.get().archiveFile.get().asFile.name,
    '--main-class', app.mainClass.get(),
    '--jlink-options', '--strip-debug --no-man-pages --no-header-files --bind-services']
app.applicationDefaultJvmArgs.each { args.addAll(['--java-options', it.toString()]) }
if (mac) args.addAll(['--mac-package-identifier', 'dev.moray.app', '--mac-package-name', 'Moray'])
```

Keep native Java commands in the runtime (omit `--strip-native-commands`) so verification can run its own `java -version` without starting Moray. Resolve jpackage beneath `launcher.get().metadata.installationPath/bin`, with `.exe` on Windows. Ensure the tool uses that same SDK's modules. Preserve all runtime dependency resources; no fat-JAR transformation or native-library pruning.

Register task inputs for the staged directory, toolchain installation identity/release, launch arguments/main class, OS/architecture and version. Give each task its own output directory. Delete only that task's old image before execution; write a completion marker only after successful native generation. A failure deletes partial image/marker, with native failure preserved. Repeated runs may be up-to-date when inputs and outputs are intact; verification itself must execute when requested. Removed dependencies are removed by Sync, not accumulated.

- [ ] **Step 4: Verify real images and create distributions.**

`verifyPackage` depends on `packageApp` and checks actual output, not a cached success marker alone. On macOS the image is `image/Moray.app`, the app directory `Contents/app`, runtime home `Contents/runtime/Contents/Home`, launcher `Contents/MacOS/Moray`, metadata `Contents/Info.plist`. On Windows these are `image/Moray`, `app`, `runtime`, and `Moray.exe`.

Verify each staged file is present and byte-identical, the cfg main class and each runtime JAR's classpath entry exist, required JVM options remain, runtime `release` identifies Java 25, bundled `java -version` identifies JetBrains, runtime-reported properties/native files confirm the expected architecture, and native launcher/runtime exist. The jlink-generated release file can omit SDK-only IMPLEMENTOR/OS_ARCH keys; verification must never add those keys or modify any generated metadata. Read pty4j and JNA JAR entries to confirm host native payloads are preserved. On Mac use `plutil` to verify identifier/version and `file` to inspect native architecture; fail on mismatch. Execute only bundled `java -version` (with `-XshowSettings:properties` when needed), assert a successful exit and JetBrains version output. On Mac also run `codesign --verify --deep --strict` against the unmodified bundle. Do not invoke Main or a shell.

`packageDist` depends on `verifyPackage`, so a broken image is not distributed. macOS: invoke jpackage `--type dmg --app-image <existing image> --dest <task-owned temporary dist directory>` and move the resulting DMG to the version/OS/architecture filename; `hdiutil verify` checks integrity without mounting. Windows: Gradle Zip includes the whole image under top-level `Moray/`, with destination `dist/`, preserving structure. ZIP and DMG task graph must select only the host operation. Keep stale distribution cleanup inside its owned directory and do not erase another task's output.

- [ ] **Step 5: Add usable instructions and perform host acceptance.**

`packaging/README.txt` explains macOS drag-to-Applications and Windows extracting the complete folder, config locations and the included example (not automatically installed), no external Java required, current development signing/icon status. `docs/packaging.md` documents both native-host commands:

```bash
./gradlew check :moray-app:packageDist
./gradlew :moray-app:verifyPackage
./gradlew :moray-app:packageDist -PmorayVersion=1.0.1
```

```powershell
.\gradlew.bat check :moray-app:packageDist
.\gradlew.bat :moray-app:verifyPackage
```

Document JBR **SDK** 25, matching host architecture, setting JAVA_HOME/PATH on Windows or `org.gradle.java.installations.paths` if discovery needs it, no WiX for ZIP, host-specific build limitation, exact output paths, development signing status and untested Windows boundary. Add native checklists from the spec with all GUI items unchecked. Link the guide from README and remove its claim that app packaging is wholly planned.

Run locally:

```bash
./gradlew check :moray-app:packageDist
./gradlew :moray-app:packageDist
./gradlew :moray-app:packageDist -PmorayVersion=1.0.1
./gradlew :moray-app:packageDist -PmorayVersion=bad
./gradlew :moray-app:packageDist
```

Expect valid builds, up-to-date behavior when unchanged, distinct versioned filename, early invalid-version failure, restored default build. Exercise a real packaging path containing spaces using an isolated copied fixture or build-directory override scoped to this worktree; avoid copying generated build outputs or creating another Git checkout. Record command and artifact evidence in the report. Check unsupported host validation at a safe build-only boundary; do not spoof `os.name` on the JVM because that changes unrelated Java native behavior. Record Windows as unexecuted.

- [ ] **Step 6: Self-review, diff check and commit.**

```bash
git diff --check
git add gradle/packaging.gradle moray-app/build.gradle.kts packaging/README.txt docs/packaging.md README.md
git commit -m "build: package Moray for macOS and Windows" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

### Task 2: Clean desktop launcher environment

**Files:**
- Modify: `moray-app/src/main/java/dev/moray/app/LaunchSettings.java`, `moray-app/src/test/java/dev/moray/app/LaunchSettingsTest.java`.
- Modify docs/comments: `docs/configuration.md`, `config.example.toml`, `moray-app/src/main/java/dev/moray/app/ConfigTemplate.java`.

**Interfaces:**
- Consumes and retains `LaunchSettings.resolve(ConfigSnapshot, String, Map<String,String>, int, int)` and its immutable result.
- Produces sanitized inherited child environment with configured overlays, reserved TERM/COLORTERM and a macOS-only fallback LANG.
- Task 1 packaging includes the current app JAR and example automatically; final package must be rebuilt after this task.

- [ ] **Step 1: Add behavioral regressions before production changes.**

Add to existing LaunchSettingsTest, using its `snapshot` helper:

```java
@Test void dropsInheritedTerminalIdentityButKeepsExplicitOverrides() {
    var inherited = new HashMap<>(Map.of("TERM_PROGRAM", "iTerm.app", "TERM_PROGRAM_VERSION", "1",
        "TERM_SESSION_ID", "old", "TMUX", "socket", "TMUX_PANE", "%1",
        "ITERM_SESSION_ID", "old", "ITERM_PROFILE", "old", "PATH", "/bin"));
    var result = LaunchSettings.resolve(ConfigSnapshot.defaults(), "Mac OS X", inherited, 80, 24);
    assertThat(result.environment()).doesNotContainKeys("TERM_PROGRAM", "TERM_PROGRAM_VERSION",
        "TERM_SESSION_ID", "TMUX", "TMUX_PANE", "ITERM_SESSION_ID", "ITERM_PROFILE");
    assertThat(result.environment()).containsEntry("PATH", "/bin");
    assertThat(inherited).containsEntry("TMUX", "socket").containsEntry("ITERM_PROFILE", "old");
    var config = snapshot("", List.of(), Map.of("TERM_PROGRAM", "custom", "ITERM_PROFILE", "chosen"), 100, 80, 24);
    assertThat(LaunchSettings.resolve(config, "Mac OS X", inherited, 80, 24).environment())
        .containsEntry("TERM_PROGRAM", "custom").containsEntry("ITERM_PROFILE", "chosen")
        .containsEntry("TERM", "xterm-256color").containsEntry("COLORTERM", "truecolor");
}

@Test void macLocaleFallbackPreservesExplicitLocaleAndDoesNotAffectOtherPlatforms() {
    var defaults = ConfigSnapshot.defaults();
    for (var inherited : List.of(Map.<String,String>of(), Map.of("LANG", " "))) {
        assertThat(LaunchSettings.resolve(defaults, "Mac OS X", inherited, 80, 24).environment())
            .containsEntry("LANG", "en_US.UTF-8");
    }
    var config = snapshot("", List.of(), Map.of("LANG", "fr_FR.UTF-8"), 100, 80, 24);
    assertThat(LaunchSettings.resolve(config, "Mac OS X", Map.of("LANG", "de_DE.UTF-8", "LC_ALL", "C"), 80, 24).environment())
        .containsEntry("LANG", "fr_FR.UTF-8").containsEntry("LC_ALL", "C");
    assertThat(LaunchSettings.resolve(defaults, "Mac OS X", Map.of("LANG", "C", "LC_CTYPE", "UTF-8"), 80, 24).environment())
        .containsEntry("LANG", "C").containsEntry("LC_CTYPE", "UTF-8");
    for (String os : List.of("Windows 11", "Linux")) {
        assertThat(LaunchSettings.resolve(defaults, os, Map.of(), 80, 24).environment()).doesNotContainKey("LANG");
    }
}
```

Also cover an explicit blank configured LANG on Mac using the same helper: it receives the fallback after overlay. Preserve existing shell argument/immutability assertions.

- [ ] **Step 2: Verify RED.**

```bash
./gradlew :moray-app:test --tests dev.moray.app.LaunchSettingsTest
```

Expected failures on retained inherited terminal keys and missing macOS LANG.

- [ ] **Step 3: Implement at the existing environment assembly boundary.**

Import Locale; between inherited copy and configured overlay insert removal. After overlay and before reserved values insert fallback:

```java
environment.keySet().removeIf(name -> name.equals("TERM_PROGRAM") || name.equals("TERM_PROGRAM_VERSION")
    || name.equals("TERM_SESSION_ID") || name.equals("TMUX") || name.equals("TMUX_PANE")
    || name.startsWith("ITERM_"));
environment.putAll(terminal.env());
if (osName.toLowerCase(Locale.ROOT).startsWith("mac")
        && environment.getOrDefault("LANG", "").isBlank()) {
    environment.put("LANG", "en_US.UTF-8");
}
```

Do not duplicate the existing `putAll`. Keep default-shell resolution, reserved values and return/copy semantics unchanged. No new public API or environment abstraction.

- [ ] **Step 4: Verify GREEN and document behavior.**

```bash
./gradlew :moray-app:test --tests dev.moray.app.LaunchSettingsTest
./gradlew check
```

Add the same concise rules to guide/template/example comments: inherited terminal identity variables removed before overlay, intentional configured overrides permitted, Mac LANG only defaults if absent/blank, LC_* preserved, TERM/COLORTERM reserved. No new config option is introduced. Change configuration guide's future-work sentence to leave app logging and refer to packaging guide.

- [ ] **Step 5: Diff/source hygiene, self-review and commit.**

```bash
git diff --check
git add moray-app/src/main/java/dev/moray/app/LaunchSettings.java moray-app/src/test/java/dev/moray/app/LaunchSettingsTest.java docs/configuration.md config.example.toml moray-app/src/main/java/dev/moray/app/ConfigTemplate.java
git commit -m "fix: prepare shell environment for desktop launches" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

## Final verification and handoff

After both task reviews, rebuild the default package with `./gradlew check :moray-app:packageDist --rerun-tasks`, count XML tests and record artifact sizes/checksums. Update STATUS/spec/this status banner and checkboxes with exact evidence, native checks still pending, remote configured but unpushed. Request a whole-branch review against `dbf75d9`, resolve findings through SDD, and verify amended paths if any. Commit documentation with the required trailer. Offer the built DMG and Windows build instructions; leave the branch/worktree for user review and eventual integration approval.
