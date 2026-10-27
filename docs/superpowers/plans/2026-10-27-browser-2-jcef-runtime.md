# Browser 2: the JCEF runtime — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build, test and package Jasper on the JetBrains Runtime build that includes JCEF, so every package carries
Chromium, with no measurable cost to start-up or memory. Nothing in this plan starts Chromium.

**Architecture:**
- **The toolchain.** It stays "vendor JetBrains, Java 25". A check reads the toolchain's `release` file and refuses a
  runtime without JCEF, in `check` and before packaging.
- **The Java modules.** jlink adds the `jcef` module, with `jogl.all` and `gluegen.rt`, which it requires. On Windows
  and Linux, Chromium's native files arrive inside `jcef.jmod`.
- **The Chromium framework on macOS.** It lives outside the jmods (`<SDK>/Contents/Frameworks/cef_server.app`), so
  packaging copies it into `Jasper.app/Contents/runtime/Contents/Frameworks` before the ad hoc re-seal. JCEF looks for
  it there (`java.home/../Frameworks/...`).

**Tech Stack:** Gradle 9.7 (Groovy packaging scripts, Kotlin DSL build), jpackage and jlink from JBR 25.0.4.1 b635.75
with JCEF, `codesign`, GitHub Actions `setup-java`.

**Spec:** [docs/superpowers/specs/2026-10-26-jasper-browser-plugin-design.md](../specs/2026-10-26-jasper-browser-plugin-design.md),
sections 4 and 19. The facts below were gathered on 2026-10-27 from the repository and from JetBrains' build scripts,
`jb/project/tools/*/scripts/mkimages*.sh`.

## Global Constraints

- **The runtime:** JBR 25.0.4.1, build b635.75, with JCEF.
  - Its `release` file has `IMPLEMENTOR_VERSION="JBRSDK-25.0.4.1+1-635.75-jcef"`, `MODULES="… jcef …"` and
    `JCEF_VERSION_DETAILED=150.0.14-g7c1aa68-chromium-150.0.7871.129-api-1.21-263-b16` (unquoted).
  - The SDK archives are `jbrsdk_jcef-25.0.4.1-<platform>-b635.75.tar.gz` from
    `https://cache-redirector.jetbrains.com/intellij-jbr/`, each with a `.checksum` file (SHA-512).
  - Platforms: `osx-aarch64`, `osx-x64`, `windows-x64`, `windows-aarch64`, `linux-x64` and `linux-aarch64`.
- **Nothing starts Chromium.** No Jasper code loads an `org.cef` class in this plan, and AOT training must not load
  one either.
- **GUI launches, benchmarks and AOT training are the user's,** one approved batch at a time (AGENTS.md). Never run
  `./gradlew --stop`.
- **Downloads need the user's explicit OK:** JDK archives, and licence texts fetched from the network.
- **Every shipped component** has a `notices.toml` entry, which `verifyThirdPartyNotices` checks.
- **Jasper is an MVP:** no fallback to the plain runtime and no "either runtime" mode.
- **Work on branch `claude/browser-jcef-runtime`** from `main`, in its own worktree. Commits end with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Prerequisites (the user, before Task 1)

1. **The JCEF SDK on the dev Mac.**
   - Replace `~/Library/Java/JavaVirtualMachines/jbrsdk-25.0.4.1-osx-aarch64-b583.48` with
     `jbrsdk_jcef-25.0.4.1-osx-aarch64-b635.75`. The spike's copy at
     `.claude/worktrees/browser-spike/tools/spikes/browser/.jdk/` can be moved, or a fresh download checked against
     its `.checksum`.
   - Both builds report vendor JetBrains and Java 25.0.4.1, so Gradle cannot tell them apart. With both installed it
     may pick either.
   - The JCEF build is a superset, so other branches keep building.
2. **Permission to download** the five other platforms' archives (about 250 to 290 MB each), for Task 3's file lists
   and Task 6's checksums. Agents only list their contents and never run anything from them.
3. **Permission to fetch** the licence texts for Task 5.

## Review Focus

1. **A machine with the plain runtime picked by Gradle** must fail at once, naming the fix, never halfway through a
   package. Task 1 pins this.
2. **Linux's `lib/jcef_helper` loses its execute bit through jlink.** JetBrains' own script runs `chmod a+x` after
   jlink. A package without the bit installs, then fails the first time Chromium starts. Task 3 pins this.
3. **On macOS, `codesign --verify --deep --strict` must still pass** after the copied framework is sealed. JetBrains'
   signatures (TeamIdentifier `2ZEFAR8TH3`) stay on the helpers. Task 2 pins this.
4. **Linux package dependencies.** `packaging/linux/dependencies.py` makes every ELF's libraries required, so
   `libcef.so`'s libraries join the package's dependencies. They must resolve on Ubuntu 22.04 and the other supported
   distributions. Task 3 pins this.
5. **AOT training and the default CDS archive** must still pass `verifyAotCache` and `-Xshare:on` with three more
   modules, and the trained cache must hold no `org.cef` class. Task 7 pins this.

---

### Task 1: The toolchain must be the JCEF build

**Files:**
- Modify: `gradle/packaging-support.gradle`: add `jcefRuntimeProblem`.
- Modify: `gradle/packaging-support-test.gradle`: fixtures.
- Modify: `gradle/packaging.gradle:90-106` (`nativeContext`): call it.
- Modify: `build.gradle.kts`: register `verifyJcefToolchain` in the root `check`.
- Modify: `AGENTS.md` ("Build and test"), `docs/packaging.md:13-16`, `README.md:152-155`,
  `jasper-app/README.md:23-25`, `docs/app-maintenance.md` (the runtime section around 552-603).

**Interfaces:**
- Produces: `ext.jcefRuntimeProblem = { String releaseText -> String }`. It returns `null` for a JCEF build, or the
  problem in words.
- Produces: the task `verifyJcefToolchain` (group `verification`).

- [ ] **Step 1: Write the failing fixtures** in a new `doLast` of `verifyPackagingSupport`:

```groovy
String jcef = '''JAVA_VERSION="25.0.4.1"
IMPLEMENTOR_VERSION="JBRSDK-25.0.4.1+1-635.75-jcef"
MODULES="java.base java.desktop gluegen.rt jogl.all jcef jdk.jpackage"
JCEF_VERSION_DETAILED=150.0.14-g7c1aa68-chromium-150.0.7871.129-api-1.21-263-b16
'''
String plain = '''JAVA_VERSION="25.0.4.1"
IMPLEMENTOR_VERSION="JBRSDK-25.0.4.1+1-583.48-nomod"
MODULES="java.base java.desktop jdk.jpackage"
'''
assert jcefRuntimeProblem(jcef) == null
assert jcefRuntimeProblem(plain) == 'it has no jcef module'
assert jcefRuntimeProblem(jcef.replace(' jcef', '')) == 'it has no jcef module'
assert jcefRuntimeProblem(jcef.readLines().findAll { !it.startsWith('JCEF_VERSION_DETAILED') }.join('\n')) == 'its release file names no JCEF version'
assert jcefRuntimeProblem('') == 'it has no jcef module'
```

- [ ] **Step 2: Run** `./gradlew verifyPackagingSupport`. Expected: FAIL, because `jcefRuntimeProblem` is missing.

- [ ] **Step 3: Implement `jcefRuntimeProblem`.**
  - A module check tokenizes the `MODULES="…"` line, so `jcef` must be a whole token. `jcef.something` doesn't count.
  - It returns the first problem found, in the order the fixtures give.

- [ ] **Step 4: Wire the check in.**
  - **In `nativeContext`,** after the architecture check, throw:
    `GradleException("The selected JBR SDK at ${sdkHome} is not the JCEF build: ${problem}. Install jbrsdk_jcef-25.0.4.1 (docs/packaging.md).")`.
  - **`verifyJcefToolchain`** reads the release file of `javaToolchains.launcherFor { 25, JETBRAINS }` and throws the
    same message. Add it to the root `check`.

- [ ] **Step 5: Run** `./gradlew verifyPackagingSupport verifyJcefToolchain`. Expected: PASS on the prepared Mac.
  Then, as a manual check, run `verifyJcefToolchain` with `-Porg.gradle.java.installations.paths=<plain JBR>
  -Porg.gradle.java.installations.auto-detect=false`. Expected: FAIL with the message.

- [ ] **Step 6: Update the docs.**
  - The runtime is `jbrsdk_jcef` 25.0.4.1 b635.75.
  - The dev Mac path is `~/Library/Java/JavaVirtualMachines/jbrsdk_jcef-25.0.4.1-osx-aarch64-b635.75`.
  - Keep only one JBR 25 installed, or point `org.gradle.java.installations.paths` at the JCEF build.
  - Record no download URL in AGENTS.md beyond the archive name.

- [ ] **Step 7: Commit** "Build: the toolchain must be the JetBrains Runtime with JCEF".

### Task 2: The packaged runtime carries JCEF; macOS gets Chromium's framework

**Files:**
- Modify: `gradle/packaging-support.gradle:59-81`: `runtimeRootModules` adds `jcef`; the comment says why.
- Modify: `gradle/packaging-support-test.gradle:9-14`: fixtures.
- Modify: `gradle/packaging.gradle`:
  - `packageApp`'s macOS branch (around 304-337): copy the framework before `resignImage(image)`;
  - `verifyPackage`'s runtime section (around 561-578) and its macOS signature checks (around 684).
- Modify: `docs/plugin-authoring.md:1500-1508` ("The Java runtime" module list) and `docs/packaging.md` (what
  `verifyPackage` checks).

**Interfaces:**
- Produces: `ext.macJcefFrameworks = 'Frameworks'`, the folder copied from `<SDK>/Contents/` to
  `Jasper.app/Contents/runtime/Contents/`.
- Produces: `ext.macJcefPayload`, the paths `verifyPackage` requires under `Contents/runtime/Contents/Frameworks/`:
  - `cef_server.app/Contents/Frameworks/Chromium Embedded Framework.framework`;
  - `cef_server.app/Contents/Frameworks/jcef Helper.app`, and its `(GPU)`, `(Renderer)` and `(Plugin)` variants.

- [ ] **Step 1: Change the fixtures:**

```groovy
assert roots.contains('jcef')
assert roots.intersect(['jdk.compiler', 'jdk.jshell', 'jdk.jlink', 'jdk.jpackage', 'jdk.jdi', 'jdk.jdwp.agent',
    'jdk.incubator.vector', 'jdk.dynalink']).isEmpty()
assert modules.size() == 44 && modules.containsAll(['jcef', 'jogl.all', 'gluegen.rt'])
assert runtimeModules('windows').size() == 45
assert macJcefPayload.size() == 5 && macJcefPayload.every { it.startsWith('cef_server.app/Contents/Frameworks/') }
```

- [ ] **Step 2: Run** `./gradlew verifyPackagingSupport`. Expected: FAIL on `roots.contains('jcef')`.

- [ ] **Step 3: Implement.**
  - `runtimeRootModules` adds `'jcef'`; `runtimeModules` adds `'gluegen.rt'`, `'jcef'` and `'jogl.all'`.
  - Rewrite the comment to say that JCEF is now a root and why.
  - Define `macJcefFrameworks` and `macJcefPayload`.

- [ ] **Step 4: Copy the framework in `packageApp`.** On macOS, after the command and watcher are signed and before
  `resignImage(image)`, run:

  ```
  /usr/bin/ditto <sdkHome>/../Frameworks <image>/Contents/runtime/Contents/Frameworks
  ```

  `ditto` keeps the symlinks, the extended attributes and the nested signatures. `sdkHome` is `Contents/Home` of the
  toolchain. Fail with "The JBR SDK has no Chromium framework at …" when the source folder is missing.

- [ ] **Step 5: Extend `verifyPackage`.**
  - **Every OS:** `MODULES` now matches the 44 or 45 modules (the existing comparison).
  - **macOS:**
    - every `macJcefPayload` path exists;
    - `codesign -dv --verbose=2` on `jcef Helper (Renderer).app` reports `TeamIdentifier=2ZEFAR8TH3`;
    - the existing `codesign --verify --deep --strict` passes on the whole app.
  - **Windows and Linux:** these are Task 3's.

- [ ] **Step 6: Run** `./gradlew verifyPackagingSupport check`. Expected: PASS. `check` does not package.

- [ ] **Step 7: Package on the Mac, headless:** `./gradlew :jasper-app:packageApp :jasper-app:verifyPackage`. Do not
  pass `-Pjasper.aot`. Expected: PASS, and the log line "Runtime: 44 modules, N MB". Record N and the size of
  `Jasper.app` (`du -sh`) for Task 7.

- [ ] **Step 8: A headless JCEF check against the package.** On branch `claude/browser-spike`, run
  `tools/spikes/browser/spike.sh --self-check` with
  `SPIKE_RUN_JDK=<Jasper.app>/Contents/runtime/Contents/Home`. Expected: every PASS line and `SELF-CHECK PASSED`. It
  shows that the packaged runtime finds the framework and the helpers. Never run the spike without `--self-check`.

- [ ] **Step 9: Update the docs** (the module list and what `verifyPackage` checks), then **commit** "Packaging: the
  runtime carries JCEF, and Jasper.app carries Chromium's framework".

### Task 3: Windows and Linux packages carry Chromium's files

**Files:**
- Create: `packaging/jcef/windows-x64.txt`, `windows-aarch64.txt`, `linux-x64.txt`, `linux-aarch64.txt`, the
  JCEF-native files of each SDK's `jmods/jcef.jmod`, as image-relative paths.
- Modify: `gradle/packaging-support.gradle`: add `jcefPayload`.
- Modify: `gradle/packaging.gradle`:
  - the Linux app-image step (around 338-345): `chmod a+x` the helpers;
  - `verifyPackage`'s Windows and Linux runtime checks.
- Modify: `packaging/linux/dependencies.py` only if Step 5 shows that it must change.

**Interfaces:**
- Produces: `ext.jcefPayload = { String os, String architecture -> List<String> }`. It reads
  `packaging/jcef/<os>-<arch>.txt`, one path per line, and ignores `#` comments.

- [ ] **Step 1: Write the lists** (needs the user's download OK).
  - For each of the four archives, list `jmods/jcef.jmod`'s native entries with `jmod list` (from the dev Mac's JDK,
    run against the extracted file), keeping its `lib/` and `bin/` entries.
  - Write each list as paths inside a packaged runtime image, for example `lib/libcef.so` or `bin/libcef.dll`.
  - Each list must contain the CEF library, the `jcef_helper` executable, `icudtl.dat`, the `.pak` files and the
    `locales` folder's files, as that archive has them.
  - Do not run anything from the archives.

- [ ] **Step 2: Write the failing fixtures:**

```groovy
['windows-x64', 'windows-aarch64', 'linux-x64', 'linux-aarch64'].each { p ->
    def (os, arch) = p.tokenize('-')
    List<String> files = jcefPayload(os, arch)
    assert !files.isEmpty() && files == files.toUnique()
    assert files.any { it.endsWith(os == 'windows' ? 'libcef.dll' : 'libcef.so') }
    assert files.any { it.endsWith(os == 'windows' ? 'jcef_helper.exe' : 'jcef_helper') }
}
```

- [ ] **Step 3: Implement `jcefPayload`.**

- [ ] **Step 4: Restore the helpers' execute bit on Linux.**
  - In the Linux branch of `packageApp`, after jpackage, set owner, group and other execute on `lib/jcef_helper`, and
    on `lib/cef_server` when the list names it, under `Jasper/lib/runtime/`.
  - `verifyPackage` on Linux checks `Files.isExecutable` for both.
  - Fixture: a temporary folder with a non-executable `lib/jcef_helper`, passed through the helper the step adds
    (`markJcefHelpersExecutable(File runtimeHome)`), comes out executable.

- [ ] **Step 5: Check Linux package dependencies.**
  - Run `linuxLibraryRequirements` on a Linux x64 host, in the `jasper-ci-linux` Docker container described in
    `docs/app-maintenance.md` if one is set up, else on CI once the Actions budget allows.
  - It must list `libcef.so`'s libraries (libnss3, libgbm, libasound2 and the like) as requirements, and every one must
    resolve on Ubuntu 22.04.
  - If `dependencies.py` cannot express one (for example a library with distribution-specific names), add it to the
    script's existing per-distribution mapping, with a test in its own test file.
  - These dependencies stay required: Chromium is part of every package *(user)*.

- [ ] **Step 6: `verifyPackage` on Windows and Linux** requires every `jcefPayload` path under the runtime home. It
  names the missing ones, then the host-specific checks follow.

- [ ] **Step 7: Run** `./gradlew verifyPackagingSupport check`. Expected: PASS on the Mac. Windows and Linux packaging
  runs where those hosts or CI are available; record that it was not run if neither is.

- [ ] **Step 8: Commit** "Packaging: Windows and Linux packages carry Chromium's files".

### Task 4: CI and release builds use the JCEF SDK

**Files:**
- Modify: every `actions/setup-java` step with `distribution: jetbrains`:
  - `.github/workflows/ci.yml`: the `check` and `desktop-gtk-modern` jobs;
  - `release.yml`: `package`;
  - `linux-packages.yml`: `package`;
  - `sdk-release.yml`.
- Test: a new assertion in `gradle/packaging-support-test.gradle`, which reads the workflow files.

- [ ] **Step 1: Write the failing fixture.** Every workflow under `.github/workflows` that names `distribution:
  jetbrains` also has `java-package: jdk+jcef` in the same step. Parse the files as text, one setup-java step at a
  time.

- [ ] **Step 2: Run** `./gradlew verifyPackagingSupport`. Expected: FAIL, naming the five steps.

- [ ] **Step 3: Add** `java-package: jdk+jcef` to each step. `setup-java`'s JetBrains distribution accepts `jdk`,
  `jre`, `jdk+jcef`, `jre+jcef`, `jdk+ft` and `jre+ft` (setup-java's `docs/advanced-usage.md`).

- [ ] **Step 4: Run** `./gradlew verifyPackagingSupport`. Expected: PASS. CI itself runs only once the Actions budget
  allows; record whether it ran.

- [ ] **Step 5: Commit** "CI: build and package on the JetBrains Runtime with JCEF".

### Task 5: Licence notices for Chromium, CEF, JCEF, JOGL and GlueGen

**Files:**
- Modify: `jasper-app/src/main/resources/dev/jasper/app/about/licenses/notices.toml`: five components, after the
  JetBrains Runtime entry at 140-145.
- Create, under `jasper-app/src/main/resources/dev/jasper/app/about/licenses/`:
  - `chromium.LICENSE.txt`, Chromium's `LICENSE` at tag `150.0.7871.129`;
  - `chromium.NOTICE.txt`;
  - `cef.LICENSE.txt`, CEF's `LICENSE.txt` on its 150 branch;
  - `jcef.LICENSE.txt`, JetBrains/jcef's `LICENSE.txt` at `9df0879`;
  - `jogl.LICENSE.txt` and `gluegen.LICENSE.txt`, JogAmp's `LICENSE.txt` for the versions JBR bundles. Read the
    `Implementation-Version` in the jmods' manifests.
- Modify: `jasper-app/src/main/java/dev/jasper/app/about/ThirdPartyNotices.java:24-33`, only if a new SPDX id is needed.
  `Map.of` stops at 10 pairs; move to `Map.ofEntries` past that.
- Test: `jasper-app/src/test/java/dev/jasper/app/about/ThirdPartyNoticesTest.java`.

- [ ] **Step 1: Write the failing test** `chromiumAndItsJavaBindingAreListed`:

```java
@Test
void chromiumAndItsJavaBindingAreListed() {
    var names = ThirdPartyNotices.load().components().stream().map(ThirdPartyNotices.Component::name).toList();
    assertTrue(names.containsAll(List.of("Chromium", "Chromium Embedded Framework", "JCEF", "JOGL", "GlueGen")), names.toString());
    var chromium = ThirdPartyNotices.load().components().stream().filter(c -> c.name().equals("Chromium")).findFirst().orElseThrow();
    assertEquals("150.0.7871.129", chromium.version());
    assertEquals("BSD-3-Clause", chromium.license());
}
```

  Use the class's real accessors; read the file first.

- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*ThirdPartyNoticesTest'`. Expected: FAIL.

- [ ] **Step 3: Add the entries and texts** (needs the user's OK to fetch them).
  - **Versions:**
    - Chromium `150.0.7871.129`;
    - Chromium Embedded Framework `150.0.14`;
    - JCEF `150.0.14.1233+g9df0879`;
    - JOGL and GlueGen from the jmod manifests.
  - **Homepages:** the projects' sites.
  - **`chromium.NOTICE.txt`** says that Chromium includes third-party software under the licences it lists, and
    gives the URL of Chromium's third-party credits for 150.0.7871.129. The user confirms at review that a link meets
    their notice obligations.

- [ ] **Step 4: Run** `./gradlew :jasper-app:test --tests '*ThirdPartyNoticesTest' :jasper-app:verifyThirdPartyNotices`.
  Expected: PASS.

- [ ] **Step 5: Commit** "About: licence notices for Chromium and its Java binding".

### Task 6: The launcher sources, the checksums and the docs

**Files:**
- Modify: `packaging/launcher/README.md:16-47`.
- Modify: `gradle/launcher.gradle:561`, only if the version changes.
- Create: `packaging/jcef/checksums.txt`, the six archives' SHA-512 values from their `.checksum` files.
- Modify: `docs/packaging.md` (the runtime and the package sizes) and `docs/STATUS.md` (this plan's entry).

- [ ] **Step 1: Compare the vendored launcher sources** (tag `jbr-release-25.0.4.1b583.48`, commit `868e6ea…`) with
  the JCEF build's commit `8a91bff64015`. Use the JBR repository's compare view, reading only the folders the README
  lists.
  - If they are the same, the README records that the runtime is b635.75 JCEF and that the launcher sources at
    b583.48 match it.
  - Otherwise, follow the README's update recipe (lines 25-47), and run the launcher's native tests through `check`.

- [ ] **Step 2: Record the checksums** with the archive names. `docs/packaging.md` names the file.

- [ ] **Step 3: Run** `./gradlew check`. Expected: PASS. Count the tests from the XML in `*/build/test-results/test/`.

- [ ] **Step 4: Commit** "Docs: the JCEF runtime, its launcher sources and checksums".

### Task 7: Measurements (the user's OK for each batch)

**Files:**
- Create: `docs/benchmarks/2026-10-XX-jcef-runtime.md`.

- [ ] **Step 1: Ask the user for this batch.** It is two packaged images trained with `-Pjasper.aot=true`, one from
  `main` and one from this branch. Each training opens a window for a few seconds. Then the start-up and memory runs
  of `tools/benchmarks/run-macos.sh` on both, following `docs/benchmarks.md` (cold and warm, `--runs 10`,
  `--prime 1`, an isolated home, empty vmoptions).

- [ ] **Step 2: Check the trained cache.** It has no `org.cef` class: list the AOT configuration's classes with the
  method `verifyAotCache` uses. `-Xshare:on` must pass, as `verifyPackage` checks.

- [ ] **Step 3: Record** both images' start-up (median and p90), RSS after start-up, package sizes (`Jasper.app`, the
  DMG) and the module count.
  - **Acceptance:** start-up and memory match within run-to-run noise (spec section 19).
  - **A real difference:** stop, and bring it to the user.

- [ ] **Step 4: Commit** "Benchmarks: the JCEF runtime against today's".

## Execution notes

- Tasks 1, 2, 4 and 5 run on the Mac.
- Task 3's lists need the downloads, and its packaging checks need Windows and Linux hosts. Without them, record "not
  run" in `docs/STATUS.md`, as earlier plans did.
- After the last task: the whole-branch review, then `./gradlew check` on the branch tip.
