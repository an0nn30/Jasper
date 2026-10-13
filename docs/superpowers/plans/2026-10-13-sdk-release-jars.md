# SDK release jars for external plugins — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build versioned SDK and testkit jars that a GitHub release carries, prove an external plugin builds from them,
and attach them to published releases.

**Architecture:** A root script `gradle/sdk-release.gradle.kts` adds sources/javadoc jars and manifest versions to
`jasper-sdk` and `jasper-sdk-testkit`, syncs the five release jars into `build/sdk-release/`, and verifies them as part of
`:jasper-sdk:check`. A separate example build under `docs/examples/external-plugin/` links those jars as files. A new
release workflow builds both and uploads the jars; CI keeps the example building.

**Tech Stack:** Gradle 9.7 (Kotlin DSL), Java 25, GitHub Actions, JUnit 6.1.3, AssertJ 3.27.7.

**Spec:** `docs/superpowers/specs/2026-10-13-sdk-release-jars-design.md`

**Workspace:** worktree `~/projects/moray/.claude/worktrees/sdk-release`, branch `claude/sdk-release` (from main
`9ce2bcc1`). Run every command from the worktree root. Never build in `~/projects/moray` itself (the user runs the app
there).

## Global Constraints

- `<v>` is `JasperSdk.VERSION`, read from `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java` (today `0.16.1`); no
  second copy of it in Jasper's build.
- `build/sdk-release/` holds exactly: `jasper-sdk-<v>.jar`, `jasper-sdk-<v>-sources.jar`, `jasper-sdk-<v>-javadoc.jar`,
  `jasper-sdk-testkit-<v>.jar`, `jasper-sdk-testkit-<v>-sources.jar`.
- Manifests: `Implementation-Title` `jasper-sdk` / `jasper-sdk-testkit`, `Implementation-Version` `<v>`.
- The SDK jar holds only `dev/jasper/sdk/**` classes, never `dev/jasper/sdk/testing/**`; the testkit jar holds
  `dev/jasper/sdk/testing/FakePluginHost.class` and no `dev/jasper/sdk/testing/contract/**`.
- The example is `docs/examples/external-plugin/`, plugin id `dev.example.hello`, class `dev.example.hello.HelloPlugin`,
  properties `jasperSdkDir` (default `libs`) and `jasperSdkVersion` (default `0.16.1`); CI passes only `jasperSdkDir`.
- Workflow `.github/workflows/sdk-release.yml`: `release: types: [published]` and `workflow_dispatch`; JBR 25 set up as in
  `ci.yml` (`actions/checkout@v7`, `actions/setup-java@v6` `distribution: jetbrains` `java-version: '25'`,
  `gradle/actions/setup-gradle@v6`); `ubuntu-latest`, `timeout-minutes: 30`; permissions `contents: write` only.
- In-repo plugins keep compiling against the projects; no `maven-publish`, no Gradle plugin, no signing.
- Commits end with:
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- No GUI launch, no benchmark, no AOT training. Do not push or start a workflow; merging needs the user.

## Spec interpretations

1. `verifySdkRelease` is wired into `:jasper-sdk:check` (the root project has no `check` task of its own; Jasper's other
   root verifications attach to subproject `check` tasks the same way), so `./gradlew check` runs it.
2. The example action has no icon (an icon name the fake host does not know would only add risk to a demonstration).
3. The example ships a `.gitignore` for `libs/`, so a developer who copies it does not commit downloaded jars.
4. The release upload passes the tag through an environment variable rather than interpolating it into the script, the
   usual guard against workflow script injection.

## Review Focus

1. **A version bump:** after `JasperSdk.VERSION` changes, the release jars carry the new number and the example build
   fails until its default `jasperSdkVersion` is updated. Check: the CI step passes no `jasperSdkVersion`
   (Task 3 Step 2 asserts it with a grep).
2. **A stale jar left in `build/sdk-release/`:** a previous version's jars must not ride along to a release. `Sync`
   removes files it did not produce; `verifySdkRelease` asserts exactly five files (Task 1).
3. **The testkit dragging in the contract fixtures or the SDK dragging in app classes.** Asserted entry by entry (Task 1).
4. **An external plugin's tests needing the testkit at run time only:** the example's test uses the testkit jar on the
   test classpath and the SDK jar as `compileOnly` for main code (Task 2).
5. **A release with an odd tag name:** the tag reaches `gh release upload` through `env:` and quoting, never through
   `${{ }}` inside the script (Task 3 Step 2 asserts it with a grep).

## File structure

| File | Task | Responsibility |
|---|---|---|
| `gradle/sdk-release.gradle.kts` (new) | 1 | release jars, manifests, `sdkRelease`, `verifySdkRelease` |
| `build.gradle.kts` | 1 | applies it |
| `docs/examples/external-plugin/{settings.gradle.kts,build.gradle.kts,.gitignore}` (new) | 2 | the example build |
| `docs/examples/external-plugin/src/main/resources/plugin.toml` (new) | 2 | its manifest |
| `docs/examples/external-plugin/src/main/java/dev/example/hello/HelloPlugin.java` (new) | 2 | its plugin |
| `docs/examples/external-plugin/src/test/java/dev/example/hello/HelloPluginTest.java` (new) | 2 | its test |
| `.github/workflows/sdk-release.yml` (new), `.github/workflows/ci.yml` | 3 | release upload, CI step |
| `docs/plugin-authoring.md`, `jasper-sdk/README.md`, `docs/STATUS.md` | 4 | documentation |

---

### Task 1: The release jars and their verification

**Files:**
- Create: `gradle/sdk-release.gradle.kts`
- Modify: `build.gradle.kts`

**Interfaces:**
- Consumes: the existing `:jasper-sdk` (`jar`, `javadoc` with doclint) and `:jasper-sdk-testkit` (`jar`) projects.
- Produces: root tasks `sdkRelease` (writes `build/sdk-release/`) and `verifySdkRelease`; `:jasper-sdk:check` depends on
  `verifySdkRelease`.

- [ ] **Step 1: See the task missing (RED)**

Run: `./gradlew verifySdkRelease`
Expected: FAIL: `Task 'verifySdkRelease' not found in root project 'jasper'.`

- [ ] **Step 2: Add the release script**

Create `gradle/sdk-release.gradle.kts`:

```kotlin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar

// The jars a Jasper GitHub release carries so a plugin built outside this repository can link the SDK as files:
//   sdkRelease       -> build/sdk-release/ (jasper-sdk-<v>.jar, -sources, -javadoc; jasper-sdk-testkit-<v>.jar, -sources)
//   verifySdkRelease -> checks those five files (part of :jasper-sdk:check)
// <v> is JasperSdk.VERSION; the build keeps no second copy of it.
val sdkVersion: String = Regex("VERSION = \"([^\"]+)\"")
    .find(file("jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java").readText())?.groupValues?.get(1)
    ?: error("jasper-sdk: JasperSdk.VERSION not found")

val sdk = project(":jasper-sdk")
val testkit = project(":jasper-sdk-testkit")

for ((module, title) in listOf(sdk to "jasper-sdk", testkit to "jasper-sdk-testkit")) {
    module.extensions.configure<JavaPluginExtension> { withSourcesJar() }
    module.tasks.named<Jar>("jar") {
        manifest { attributes("Implementation-Title" to title, "Implementation-Version" to sdkVersion) }
    }
}
// The javadoc jar is built from the same doclint-checked javadoc task :jasper-sdk:check already runs.
sdk.extensions.configure<JavaPluginExtension> { withJavadocJar() }

val releaseNames = listOf("jasper-sdk-$sdkVersion.jar", "jasper-sdk-$sdkVersion-sources.jar",
    "jasper-sdk-$sdkVersion-javadoc.jar", "jasper-sdk-testkit-$sdkVersion.jar", "jasper-sdk-testkit-$sdkVersion-sources.jar")
val releaseDirectory = layout.buildDirectory.dir("sdk-release")

val sdkRelease = tasks.register<Sync>("sdkRelease") {
    description = "Builds the SDK and testkit jars a GitHub release carries for external plugins."
    group = "distribution"
    into(releaseDirectory)
    from(sdk.tasks.named("jar")) { rename { releaseNames[0] } }
    from(sdk.tasks.named("sourcesJar")) { rename { releaseNames[1] } }
    from(sdk.tasks.named("javadocJar")) { rename { releaseNames[2] } }
    from(testkit.tasks.named("jar")) { rename { releaseNames[3] } }
    from(testkit.tasks.named("sourcesJar")) { rename { releaseNames[4] } }
}

val verifySdkRelease = tasks.register("verifySdkRelease") {
    description = "Checks the release jars: names, contents and manifest versions."
    group = "verification"
    dependsOn(sdkRelease)
    doLast {
        val folder = releaseDirectory.get().asFile
        val present = folder.listFiles().orEmpty().map { it.name }.sorted()
        check(present == releaseNames.sorted()) { "build/sdk-release holds $present, expected ${releaseNames.sorted()}" }
        fun entries(name: String): List<String> =
            java.util.zip.ZipFile(folder.resolve(name)).use { zip -> zip.entries().asSequence().map { it.name }.toList() }
        fun manifestVersion(name: String): String? =
            java.util.jar.JarFile(folder.resolve(name)).use { it.manifest?.mainAttributes?.getValue("Implementation-Version") }

        val sdkClasses = entries(releaseNames[0]).filter { it.endsWith(".class") }
        check("dev/jasper/sdk/JasperSdk.class" in sdkClasses) { "${releaseNames[0]} lacks dev/jasper/sdk/JasperSdk.class" }
        val foreign = sdkClasses.filter { !it.startsWith("dev/jasper/sdk/") || it.startsWith("dev/jasper/sdk/testing/") }
        check(foreign.isEmpty()) { "${releaseNames[0]} carries classes outside the SDK: ${foreign.take(10)}" }

        val kitEntries = entries(releaseNames[3])
        check("dev/jasper/sdk/testing/FakePluginHost.class" in kitEntries) { "${releaseNames[3]} lacks FakePluginHost" }
        val fixtures = kitEntries.filter { it.startsWith("dev/jasper/sdk/testing/contract/") }
        check(fixtures.isEmpty()) { "${releaseNames[3]} carries the contract fixtures: ${fixtures.take(10)}" }

        for (jar in listOf(releaseNames[0], releaseNames[3]))
            check(manifestVersion(jar) == sdkVersion) { "$jar has Implementation-Version ${manifestVersion(jar)}, expected $sdkVersion" }
    }
}

sdk.tasks.named("check") { dependsOn(verifySdkRelease) }
```

In `build.gradle.kts` replace

```kotlin
apply(from = "gradle/plugin-packaging.gradle.kts")
```

with

```kotlin
apply(from = "gradle/plugin-packaging.gradle.kts")
apply(from = "gradle/sdk-release.gradle.kts")
```

- [ ] **Step 3: Run it (GREEN) and look inside**

Run: `./gradlew verifySdkRelease :jasper-sdk:check`
Expected: `BUILD SUCCESSFUL`.

Run: `ls build/sdk-release && unzip -p build/sdk-release/jasper-sdk-0.16.1.jar META-INF/MANIFEST.MF && unzip -l build/sdk-release/jasper-sdk-testkit-0.16.1.jar | grep -c 'dev/jasper/sdk/testing/contract/'`
Expected: the five file names; a manifest with `Implementation-Title: jasper-sdk` and `Implementation-Version: 0.16.1`;
`0`.

Then prove `Sync` drops a stale jar: `touch build/sdk-release/jasper-sdk-0.15.0.jar && ./gradlew verifySdkRelease && ls build/sdk-release | grep -c 0.15.0`
Expected: `BUILD SUCCESSFUL` and `0`.

- [ ] **Step 4: Commit**

```bash
git add gradle/sdk-release.gradle.kts build.gradle.kts
git commit -F - <<'EOF'
Build versioned SDK and testkit jars for GitHub releases

sdkRelease writes jasper-sdk and jasper-sdk-testkit jars (with sources
and javadoc) named from JasperSdk.VERSION to build/sdk-release, and
verifySdkRelease checks their names, contents and manifest versions as
part of :jasper-sdk:check.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: The example external plugin

**Files:**
- Create: `docs/examples/external-plugin/settings.gradle.kts`, `build.gradle.kts`, `.gitignore`,
  `src/main/resources/plugin.toml`, `src/main/java/dev/example/hello/HelloPlugin.java`,
  `src/test/java/dev/example/hello/HelloPluginTest.java`

**Interfaces:**
- Consumes: `build/sdk-release/jasper-sdk-0.16.1.jar` and `jasper-sdk-testkit-0.16.1.jar` from Task 1; SDK
  `ActionSpec`, `StandardMenu.TOOLS`, `Platform.copy`; testkit `FakePluginHost.menu`, `invoke`, `clipboard().copies()`.
- Produces: the example build, run as
  `./gradlew -p docs/examples/external-plugin -PjasperSdkDir=<dir> check pluginZip`.

- [ ] **Step 1: Write the build and the failing test**

Create `docs/examples/external-plugin/settings.gradle.kts`:

```kotlin
// A plugin built outside the Jasper repository, from the jars a Jasper GitHub release carries.
rootProject.name = "hello-plugin"
```

Create `docs/examples/external-plugin/.gitignore`:

```
# The Jasper SDK and testkit jars you download from a Jasper release.
libs/
build/
.gradle/
```

Create `docs/examples/external-plugin/build.gradle.kts`:

```kotlin
// An external Jasper plugin built from the jars a Jasper GitHub release carries. Put jasper-sdk-<v>.jar and
// jasper-sdk-testkit-<v>.jar in libs/, or pass -PjasperSdkDir=<folder>; -PjasperSdkVersion=<v> picks another release.
// The SDK is compileOnly: Jasper supplies it at run time. Libraries your plugin bundles go in implementation.
plugins { `java-library` }

val jasperSdkDir = file(providers.gradleProperty("jasperSdkDir").getOrElse("libs"))
val jasperSdkVersion = providers.gradleProperty("jasperSdkVersion").getOrElse("0.16.1")
fun jasperJar(name: String) = files(jasperSdkDir.resolve("$name-$jasperSdkVersion.jar"))

/** The id and version come from plugin.toml, so the build keeps no second copy of either. */
val descriptor = file("src/main/resources/plugin.toml").readText()
fun field(name: String) = Regex("^$name\\s*=\\s*\"([^\"]+)\"", RegexOption.MULTILINE).find(descriptor)?.groupValues?.get(1)
    ?: error("plugin.toml has no $name")
val pluginId = field("id")
version = field("version")

repositories { mavenCentral() }

// Jasper runs plugins on Java 25; any Java 25 compiles them (Jasper itself needs the JetBrains Runtime only to run).
java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

dependencies {
    compileOnly(jasperJar("jasper-sdk"))
    testImplementation(jasperJar("jasper-sdk"))
    testImplementation(jasperJar("jasper-sdk-testkit"))
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

tasks.jar { archiveBaseName.set(pluginId) }

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}

// What Jasper installs: the plugin jar beside the libraries it bundles (its runtime classpath).
//   stagePlugin -> build/plugin/<id>/                     (jasper --plugin-dir build/plugin/<id>)
//   pluginZip   -> build/distributions/<id>-<version>.zip (File > Manage Plugins... > Install from Zip...)
val pluginContents = files(tasks.jar, configurations.runtimeClasspath)

tasks.register<Sync>("stagePlugin") {
    description = "Stages $pluginId as a plugin directory for jasper --plugin-dir."
    group = "distribution"
    from(pluginContents)
    into(layout.buildDirectory.dir("plugin/$pluginId"))
}

tasks.register<Zip>("pluginZip") {
    description = "Zips $pluginId for File > Manage Plugins... > Install from Zip..."
    group = "distribution"
    from(pluginContents)
    archiveFileName.set("$pluginId-$version.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}
```

Create `docs/examples/external-plugin/src/main/resources/plugin.toml`:

```toml
id = "dev.example.hello"
name = "Hello"
version = "1.0.0"
entry = "dev.example.hello.HelloPlugin"
sdk = ">=0.16.1, <0.17"
description = "An example external plugin: a Tools menu command that copies a greeting."
vendor = "Example"
capabilities = []
exports = []
```

Create `docs/examples/external-plugin/src/test/java/dev/example/hello/HelloPluginTest.java`:

```java
package dev.example.hello;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HelloPluginTest {
    @Test void theToolsCommandCopiesTheGreeting() {
        try (var host = new FakePluginHost()) {
            host.start(new PluginInfo("dev.example.hello", "Hello", "1.0.0", Set.of()), Set.of(), Set.of(), new HelloPlugin());
            assertThat(host.menu("TOOLS")).containsExactly("item:" + HelloPlugin.COPY);
            var window = host.addTerminalWindow();
            assertThat(host.invoke(HelloPlugin.COPY, window, null)).isTrue();
            assertThat(host.clipboard().copies()).containsExactly("copy|dev.example.hello|" + HelloPlugin.GREETING);
            assertThat(host.failures()).isEmpty();
        }
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew sdkRelease && ./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check`
Expected: FAIL at `:compileTestJava` with `cannot find symbol ... class HelloPlugin`.

- [ ] **Step 3: Write the plugin**

Create `docs/examples/external-plugin/src/main/java/dev/example/hello/HelloPlugin.java`:

```java
package dev.example.hello;

import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.StandardMenu;
import java.util.List;

/** An example external plugin: one command, in the Tools menu and the palette, that copies a greeting. */
public final class HelloPlugin implements Plugin {
    static final String COPY = "dev.example.hello.copy";
    static final String GREETING = "Hello from an external Jasper plugin";

    @Override public void start(PluginContext context) {
        context.actions().register(ActionSpec.of(COPY, "Copy Greeting").withKeywords(List.of("hello", "greeting", "example")),
            invoked -> context.platform().copy(GREETING));
        context.menus().standard(StandardMenu.TOOLS).add(COPY);
    }
}
```

- [ ] **Step 4: Run it to see it pass, and package it**

Run: `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`
Expected: `BUILD SUCCESSFUL`; `docs/examples/external-plugin/build/test-results/test/TEST-dev.example.hello.HelloPluginTest.xml`
shows `tests="1" failures="0" errors="0"`; `unzip -l docs/examples/external-plugin/build/distributions/dev.example.hello-1.0.0.zip`
lists exactly `dev.example.hello-1.0.0.jar` (no SDK jar: it is `compileOnly`).

Then: `git status --short docs/examples` lists only the six new files (no `build/`, `.gradle/` or `libs/`).

- [ ] **Step 5: Commit**

```bash
git add docs/examples/external-plugin
git commit -F - <<'EOF'
Add an example plugin built from the SDK release jars

docs/examples/external-plugin is a separate Gradle build that links
jasper-sdk and jasper-sdk-testkit as files, tests a Tools menu command
that copies a greeting, and packages the plugin for Install from Zip.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: The release workflow and the CI step

**Files:**
- Create: `.github/workflows/sdk-release.yml`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: `sdkRelease`, `verifySdkRelease` (Task 1); the example build and its `jasperSdkDir` property (Task 2).
- Produces: nothing code depends on.

- [ ] **Step 1: Write the workflow and the CI step**

Create `.github/workflows/sdk-release.yml`:

```yaml
name: sdk-release

# Attaches the SDK and testkit jars an external plugin builds against to a published GitHub release, after building
# docs/examples/external-plugin from those exact jars. A manual run builds the same jars as a workflow artifact.
on:
  release:
    types: [published]
  workflow_dispatch:

jobs:
  jars:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    permissions:
      contents: write
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with:
          distribution: jetbrains
          java-version: '25'
      - uses: gradle/actions/setup-gradle@v6
      - name: Build the SDK jars
        run: ./gradlew sdkRelease verifySdkRelease --stacktrace
      - name: Build the example plugin from them
        run: ./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip --stacktrace
      - name: Attach the jars to the release
        if: github.event_name == 'release'
        env:
          GH_TOKEN: ${{ github.token }}
          TAG: ${{ github.event.release.tag_name }}
          REPOSITORY: ${{ github.repository }}
        run: gh release upload "$TAG" build/sdk-release/*.jar --clobber --repo "$REPOSITORY"
      - name: Keep the jars as a workflow artifact
        if: github.event_name == 'workflow_dispatch'
        uses: actions/upload-artifact@v7
        with:
          name: jasper-sdk-jars
          path: build/sdk-release/*.jar
```

In `.github/workflows/ci.yml` replace

```yaml
      - name: Check
        shell: bash
        run: ./gradlew check --continue --stacktrace
```

with

```yaml
      - name: Check
        shell: bash
        run: ./gradlew check --continue --stacktrace
      # check built the release jars; the example plugin must keep building from them.
      - name: Example external plugin
        if: runner.os == 'Linux'
        shell: bash
        run: ./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip --stacktrace
```

- [ ] **Step 2: Check the YAML and the guards**

Run:

```bash
python3 - <<'PY'
import yaml
for path in (".github/workflows/sdk-release.yml", ".github/workflows/ci.yml"):
    doc = yaml.safe_load(open(path, encoding="utf-8"))
    triggers = doc.get(True) or doc.get("on")   # YAML 1.1 reads the key `on` as True
    print(path, "ok", sorted(triggers) if isinstance(triggers, dict) else triggers)
release = yaml.safe_load(open(".github/workflows/sdk-release.yml", encoding="utf-8"))
job = release["jobs"]["jars"]
assert job["permissions"] == {"contents": "write"}, job["permissions"]
upload = next(step for step in job["steps"] if step.get("name") == "Attach the jars to the release")
assert "${{" not in upload["run"], "the tag must reach the script through env"
ci = yaml.safe_load(open(".github/workflows/ci.yml", encoding="utf-8"))
example = next(step for step in ci["jobs"]["check"]["steps"] if step.get("name") == "Example external plugin")
assert "jasperSdkVersion" not in example["run"], "CI must not pass jasperSdkVersion"
print("guards ok")
PY
```

Expected: `.github/workflows/sdk-release.yml ok ['release', 'workflow_dispatch']`,
`.github/workflows/ci.yml ok ['pull_request', 'push']`, `guards ok`.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/sdk-release.yml .github/workflows/ci.yml
git commit -F - <<'EOF'
Attach the SDK jars to published releases and keep the example building

sdk-release.yml builds the jars and the example plugin from them, then
uploads the jars to the published release (or keeps them as a workflow
artifact on a manual run); CI builds the example on Linux.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 4: Documentation and the whole check

**Files:**
- Modify: `docs/plugin-authoring.md`, `jasper-sdk/README.md`, `docs/STATUS.md`

**Interfaces:**
- Consumes: everything above; documents it.
- Produces: nothing code depends on.

- [ ] **Step 1: The plugin authoring guide**

In `docs/plugin-authoring.md`, insert before `## Running a plugin in Jasper`:

```markdown
## Building a plugin outside the Jasper repository

Each Jasper GitHub release carries the SDK as jars: `jasper-sdk-<v>.jar` (with `-sources` and `-javadoc` jars) and
`jasper-sdk-testkit-<v>.jar` (with `-sources`), where `<v>` is that release's `JasperSdk.VERSION`. Take them from the
release whose SDK version your `plugin.toml` `sdk` range admits, put them in your plugin's `libs/`, and start from the
[example external plugin](examples/external-plugin/build.gradle.kts):

- [`build.gradle.kts`](examples/external-plugin/build.gradle.kts) links the SDK as `compileOnly` (Jasper supplies it at
  run time), puts the SDK and testkit on the test classpath with JUnit and AssertJ from Maven Central, compiles with any
  Java 25, and adds `stagePlugin` and `pluginZip`.
- [`plugin.toml`](examples/external-plugin/src/main/resources/plugin.toml),
  [`HelloPlugin`](examples/external-plugin/src/main/java/dev/example/hello/HelloPlugin.java) and
  [`HelloPluginTest`](examples/external-plugin/src/test/java/dev/example/hello/HelloPluginTest.java) are a minimal
  plugin and its `FakePluginHost` test.

To build the jars yourself from a Jasper checkout, run `./gradlew sdkRelease`; they land in `build/sdk-release/`. The
example builds from them with `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`
(CI does exactly this), and `-PjasperSdkVersion=<v>` selects another release's jars. The zip then installs as described
in [Distributing a plugin](#distributing-a-plugin).

```

- [ ] **Step 2: The SDK README**

In `jasper-sdk/README.md`, after the paragraph that starts `The API Jasper plugins compile against.` (the first
paragraph), insert:

```markdown

Each Jasper GitHub release carries `jasper-sdk-<v>.jar` and `jasper-sdk-testkit-<v>.jar` (with sources and Javadoc) for
plugins built outside this repository; `./gradlew sdkRelease` builds them into `build/sdk-release/`, and
`verifySdkRelease` (part of `check`) pins their names and contents. Start from the
[example external plugin](../docs/examples/external-plugin/build.gradle.kts) and the
[authoring guide](../docs/plugin-authoring.md#building-a-plugin-outside-the-jasper-repository).
```

- [ ] **Step 3: Run the whole check and the example**

Run: `./gradlew check --continue > build/sdk-release-check.log 2>&1; echo "exit=$?"; tail -3 build/sdk-release-check.log`
Expected: `exit=0`, `BUILD SUCCESSFUL`. Then count:

```bash
python3 - <<'PY'
import glob, re
t = f = e = s = 0
for p in glob.glob("**/build/test-results/test/*.xml", recursive=True):
    if p.startswith("docs/"): continue
    head = open(p, encoding="utf-8").read(800)
    get = lambda k: int(re.search(k + '="(\\d+)"', head).group(1))
    t += get("tests"); f += get("failures"); e += get("errors"); s += get("skipped")
print(f"tests={t} failures={f} errors={e} skipped={s}")
PY
```

Expected: `failures=0 errors=0`.

Run: `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`
Expected: `BUILD SUCCESSFUL`.

Then `git diff --check main...HEAD` prints nothing.

- [ ] **Step 4: Record the work in STATUS**

In `docs/STATUS.md`, insert after the first line (`# Jasper — Status and Handoff`) and its blank line, using the
totals from Step 3:

```markdown
**SDK release jars for external plugins — 2026-10-13** (`claude/sdk-release`, worktree `.claude/worktrees/sdk-release`;
[spec](superpowers/specs/2026-10-13-sdk-release-jars-design.md), [plan](superpowers/plans/2026-10-13-sdk-release-jars.md)).
`./gradlew sdkRelease` writes `jasper-sdk-<v>.jar` (with `-sources` and `-javadoc`) and `jasper-sdk-testkit-<v>.jar`
(with `-sources`) to `build/sdk-release/`, named from `JasperSdk.VERSION`; `verifySdkRelease` (part of `:jasper-sdk:check`)
pins their names, contents and manifest versions. `docs/examples/external-plugin/` is a separate Gradle build that
links those jars as files, tests a Tools command that copies a greeting, and zips the plugin. `.github/workflows/sdk-release.yml`
builds the jars and the example, then attaches the jars to a published release (a manual run keeps them as a workflow
artifact); CI builds the example on Linux. Official publishing (Maven coordinates, a Gradle plugin) is left for later.
`./gradlew check --continue`: **<tests> tests, 0 failures, 0 errors, <skipped> skipped**; the example builds from the
fresh jars. No GUI launched. For the user: publish a release (or dispatch sdk-release) and confirm the five jars are
attached. Not merged or pushed.

```

replacing `<tests>` and `<skipped>` with the numbers Step 3 printed.

- [ ] **Step 5: Commit**

```bash
git add docs/plugin-authoring.md jasper-sdk/README.md docs/STATUS.md
git commit -F - <<'EOF'
Document building plugins from the SDK release jars

The authoring guide and the SDK README explain where the release jars
come from and point at the example external plugin; STATUS records the
work.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## After the last task

- Whole-branch review against the spec and this plan.
- Report the `./gradlew check` totals and the example build.
- Ask the user before merging `claude/sdk-release` into `main` or pushing; pushing the workflow does not start it, but
  the next published release will.
- Then piece 3: move the external encrypt/decrypt plugin to SDK 0.16.1 using these jars.
