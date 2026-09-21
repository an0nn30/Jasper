# Jasper Plugin SDK Plan 1: SDK Core and Runtime — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Not started. Record every deviation from this text here and in `docs/STATUS.md`.

**Goal:** Ship the JDK-only `jasper-sdk`, a testkit with a shared contract suite, and the application runtime that discovers, resolves, loads, starts and stops third-party plugins, proven by a bundled sample plugin whose activity appears on Buddy.

**Architecture:** `jasper-sdk` defines the plugin-facing interfaces and values. `jasper-sdk-testkit` supplies a headless fake and an abstract contract suite. `dev.jasper.app.plugins` (one package, everything package-private except `PluginRuntime`) implements descriptor parsing, locked state, resolution, per-plugin classloaders, a queued EDT event bus, activities, a commit-on-success service registry and lifecycle with rollback; the same contract suite runs against it. `JasperApplication` owns one `PluginRuntime`, feeds it configuration and theme changes, bridges activities to Buddy through an app-native notifier, and arms an exit deadline before any plugin shutdown code runs.

**Tech Stack:** Java 25 on JBR 25, Gradle wrapper, JUnit 6.1.3, AssertJ 3.27.7, tomlj 1.1.1 (already an app dependency). No new third-party dependency.

**Spec:** `docs/superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md` (sections 2, 3, 7, 8, 9, 10 flags, 11, 12, 13 item 1). Executors read the spec alongside this plan.

## Global Constraints

- Use `./gradlew`, never a system Gradle. Java 25, JetBrains vendor toolchain. `./gradlew check` is headless and must pass at the end of every task.
- **Never launch the GUI** (`:jasper-app:run`, benchmarks). GUI checks are the user's; the last task lists them.
- Do not commit on `main`. Work on branch `claude/plugin-sdk-plan-1` in `.worktrees/plugin-sdk-plan-1`; run `git branch --show-current` before every commit. End commit messages with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- `jasper-sdk` and `jasper-sdk-testkit` main code reference only the JDK and `dev.jasper.sdk.*`. No JediTerm, FlatLaf, tomlj, app, terminal or Buddy type.
- SDK types (`dev.jasper.sdk.*`) appear in the app **only** inside `dev.jasper.app.plugins`. Other app packages expose hooks in app-native types. `jasper-buddy` is unchanged.
- No interface without two real implementations: every SDK interface is implemented by the testkit fake and by the app; `Plugin` by the sample and by test plugins. Callbacks use JDK functional interfaces.
- Plugin ids and topic ids match `[a-z][a-z0-9_.-]{0,127}`. The `jasper` id and the `jasper.` prefix are reserved for the application.
- `JasperSdk.VERSION` is `"0.1.0"`; 0.x carries no compatibility promise.
- Threading: plugin `start`, `stop`, `subscribe`, service publication and event handlers run on the EDT. `publish`, activity handles, `require`/`find`, `log`, `background`, `Subscription.close` are safe from any thread. Event delivery is always queued, never synchronous.
- Source hygiene: no raw control, private-use or unpaired surrogate characters in source; write Java escapes. Run the AGENTS.md Python check over new source roots.
- Every production package has a `package-info.java` with a Javadoc comment. App packages' package-info names allowed outgoing dependencies and ends with the sentence "The module architecture check forbids package cycles; app types are not an external plugin API."
- Javadoc runs with `-Xdoclint:all` and `failOnError` for `jasper-sdk` and `jasper-app`: every public and protected type and member needs a doc comment; malformed HTML or bad references fail the build.

### Deliberate deviations from the spec's plan-1 text

1. `PluginContext` contains only the accessors this plan implements (`plugin`, `log`, `dataDirectory`, `config`, `background`, `events`, `activities`, `services`). The spec said later accessors would exist and throw; that would require shipping empty interface types. Later plans add accessors, which 0.x permits.
2. The app-owned cleanup worker and `MissingCapabilityException` move to plan 4, where their first clients (`PendingSession`, gated terminal calls) arrive.
3. The runtime lives in the single package `dev.jasper.app.plugins` rather than sub-packages, keeping everything but `PluginRuntime` package-private.
4. Plugin activities are posted to Buddy when they start (no threshold), and do not drive Buddy's "working" animation, which the command notifier owns.
5. The spec's second `Services.publish` overload is named `publishPerConsumer`: with both named `publish`, passing a lambda for a functional service interface does not compile (ambiguous overload).

## File Structure

```
settings.gradle.kts                          modify: include the three new projects
build.gradle.kts                             modify: apply the two new architecture scripts
gradle/sdk-architecture.gradle.kts           create: verifySdkArchitecture
gradle/plugin-architecture.gradle.kts        create: verifyPluginArchitecture
gradle/application-architecture.gradle.kts   modify: confine SDK types to dev.jasper.app.plugins
gradle/packaging.gradle                      modify: stage and verify bundled plugins

jasper-sdk/build.gradle.kts
jasper-sdk/README.md
jasper-sdk/src/main/java/dev/jasper/sdk/
  package-info.java  JasperSdk.java  Subscription.java  PluginInfo.java  Variant.java
  events/    package-info.java  Topic.java  Events.java  AppEvents.java
  activity/  package-info.java  Activities.java  ActivitySpec.java  ActivityHandle.java  ActivityEvent.java
  services/  package-info.java  Services.java  ServiceUnavailableException.java
  plugin/    package-info.java  Plugin.java  PluginContext.java  PluginConfig.java
jasper-sdk/src/test/java/dev/jasper/sdk/...  value tests

jasper-sdk-testkit/build.gradle.kts
jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/
  package-info.java  FakePluginHost.java  FakePluginContext.java
jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/
  ContractHarness.java  PluginContractTest.java
jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java

jasper-app/src/main/java/dev/jasper/app/plugins/
  package-info.java
  Version.java  VersionRange.java  PluginDescriptor.java  DescriptorParser.java
  PluginStateStore.java
  PluginCandidate.java  PluginStatus.java  PluginDiscovery.java  PluginResolver.java
  PluginClassLoader.java  PluginLoader.java
  Containment.java  EventBus.java  ActivityHub.java
  PluginSettings.java
  ServiceRegistry.java  HostedPlugin.java  HostedContext.java  PluginHost.java
  PluginRuntime.java                          the only public type
jasper-app/src/main/java/dev/jasper/app/notifications/ActivityNotifier.java   create
jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java, ConfigLoader.java          modify
jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java,
  ApplicationShutdown.java, JasperApplication.java                                           modify
jasper-app/src/main/java/dev/jasper/app/bootstrap/AppArguments.java, ApplicationBootstrap.java modify
jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java                                 modify
jasper-app/src/test/java/dev/jasper/app/testsupport/PluginJars.java           create: compiles fixture plugins into jars
jasper-app/src/test/java/dev/jasper/app/plugins/...                           tests per task

plugins/sample/build.gradle.kts
plugins/sample/src/main/resources/plugin.toml
plugins/sample/src/main/java/dev/jasper/sample/package-info.java  SamplePlugin.java
plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java

docs/sdk-architecture.md  docs/plugin-authoring.md                            create
AGENTS.md  docs/README.md  docs/STATUS.md  docs/app-architecture.md  docs/configuration.md      modify
```

---

### Task 0: Workspace

- [ ] **Step 1: Create the worktree from the approved spec branch**

```bash
cd /Users/dustin/projects/moray
git worktree add -b claude/plugin-sdk-plan-1 .worktrees/plugin-sdk-plan-1 claude/plugin-sdk-design
cd .worktrees/plugin-sdk-plan-1 && git branch --show-current
```

Expected: `claude/plugin-sdk-plan-1`. All later commands run from this directory.

- [ ] **Step 2: Confirm the baseline is green**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL`. If it fails, stop and report; do not start on a red baseline.

---

### Task 1: `jasper-sdk` module, core values and `verifySdkArchitecture`

**Files:**
- Modify: `settings.gradle.kts`, `build.gradle.kts`
- Create: `jasper-sdk/build.gradle.kts`, `gradle/sdk-architecture.gradle.kts`
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/{package-info,JasperSdk,Subscription,PluginInfo,Variant}.java`
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/events/{package-info,Topic}.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/PluginInfoTest.java`, `jasper-sdk/src/test/java/dev/jasper/sdk/events/TopicTest.java`

**Interfaces:**
- Produces: `JasperSdk.VERSION` (`String`); `Subscription` (`void close()`); `PluginInfo(String id, String name, String version, Set<String> capabilities)` with `static boolean validId(String)`; `Variant { DARK, LIGHT }`; `Topic<T>` with `static <T> Topic<T> of(String id, Class<T> payloadType)`, `id()`, `payloadType()`.

- [ ] **Step 1: Register the module**

`settings.gradle.kts` becomes:

```kotlin
rootProject.name = "jasper"
include("jasper-terminal", "jasper-app", "jasper-buddy", "jasper-sdk")
```

`jasper-sdk/build.gradle.kts`:

```kotlin
// The plugin SDK depends only on the JDK; verifySdkArchitecture enforces it from bytecode.
plugins { `java-library` }

tasks.withType<Javadoc>().configureEach {
    isFailOnError = true
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addBooleanOption("Xdoclint:all", true)
    }
}
tasks.named("check") { dependsOn(tasks.named("javadoc")) }
```

- [ ] **Step 2: Write the failing tests**

`jasper-sdk/src/test/java/dev/jasper/sdk/PluginInfoTest.java`:

```java
package dev.jasper.sdk;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PluginInfoTest {
    @Test void acceptsNamespacedIdsAndCopiesCapabilities() {
        var capabilities = new HashSet<>(Set.of("terminal.observe"));
        var info = new PluginInfo("dev.example.tool", "Tool", "1.2.3", capabilities);
        capabilities.add("terminal.inject");
        assertThat(info.capabilities()).containsExactly("terminal.observe");
        assertThat(JasperSdk.VERSION).matches("0\\.\\d+\\.\\d+");
    }

    @Test void rejectsMalformedAndReservedIds() {
        for (String id : new String[]{"", "Upper", "1abc", "has space", "jasper", "jasper.core", "a".repeat(129)}) {
            assertThat(PluginInfo.validId(id)).as(id).isFalse();
            assertThatIllegalArgumentException().as(id)
                .isThrownBy(() -> new PluginInfo(id, "n", "1.0.0", Set.of()));
        }
        assertThat(PluginInfo.validId("jasperish.tool")).isTrue();
    }

    @Test void rejectsBlankNameAndVersion() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PluginInfo("a.b", " ", "1.0.0", Set.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new PluginInfo("a.b", "n", "", Set.of()));
        assertThatNullPointerException().isThrownBy(() -> new PluginInfo("a.b", "n", "1.0.0", null));
    }
}
```

`jasper-sdk/src/test/java/dev/jasper/sdk/events/TopicTest.java`:

```java
package dev.jasper.sdk.events;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TopicTest {
    record Ping(int n) {}

    @Test void equalityUsesIdAndPayloadType() {
        assertThat(Topic.of("a.b.ping", Ping.class)).isEqualTo(Topic.of("a.b.ping", Ping.class))
            .hasSameHashCodeAs(Topic.of("a.b.ping", Ping.class))
            .isNotEqualTo(Topic.of("a.b.ping", String.class))
            .isNotEqualTo(Topic.of("a.b.pong", Ping.class));
        assertThat(Topic.of("a.b.ping", Ping.class).toString()).contains("a.b.ping");
    }

    @Test void rejectsMalformedIdsAndNulls() {
        for (String id : new String[]{"", "Upper.case", "9start", "sp ace"})
            assertThatIllegalArgumentException().as(id).isThrownBy(() -> Topic.of(id, Ping.class));
        assertThatNullPointerException().isThrownBy(() -> Topic.of(null, Ping.class));
        assertThatNullPointerException().isThrownBy(() -> Topic.of("a.b", null));
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test`
Expected: compilation FAILS with "cannot find symbol" for `PluginInfo`, `JasperSdk`, `Topic`.

- [ ] **Step 4: Write the core values**

`jasper-sdk/src/main/java/dev/jasper/sdk/package-info.java`:

```java
/**
 * Leaf values shared by every SDK package: the SDK version, plugin identity, the theme variant and
 * the subscription returned by every registration. This package depends on no other SDK package.
 */
package dev.jasper.sdk;
```

`JasperSdk.java`:

```java
package dev.jasper.sdk;

/** The SDK version a plugin's {@code sdk} range is checked against. 0.x carries no compatibility promise. */
public final class JasperSdk {
    /** Semantic version of this SDK. */
    public static final String VERSION = "0.1.0";

    private JasperSdk() { }
}
```

`Subscription.java`:

```java
package dev.jasper.sdk;

/**
 * Ends one registration. Closing is idempotent, never throws and is safe from any thread. When called
 * on the UI thread the contribution is gone before this returns; from another thread, at most one
 * delivery already in progress may still complete. The runtime closes whatever a plugin leaves open.
 */
public interface Subscription extends AutoCloseable {
    /** Removes the registration; later calls do nothing. */
    @Override void close();
}
```

`Variant.java`:

```java
package dev.jasper.sdk;

/** The application's current look: plugins pick colors and icons that suit it. */
public enum Variant {
    /** Dark chrome and terminal palette. */
    DARK,
    /** Light chrome and terminal palette. */
    LIGHT
}
```

`PluginInfo.java`:

```java
package dev.jasper.sdk;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A plugin's verified identity, as read from its descriptor by the runtime.
 *
 * @param id namespaced id matching {@code [a-z][a-z0-9_.-]{0,127}}; {@code jasper} and the
 *           {@code jasper.} prefix are reserved for the application
 * @param name non-blank display name
 * @param version non-blank semantic version
 * @param capabilities capabilities the user consented to; copied
 */
public record PluginInfo(String id, String name, String version, Set<String> capabilities) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_.-]{0,127}");

    /** Validates the id, rejects blank text and copies the capability set. */
    public PluginInfo {
        if (!validId(id)) throw new IllegalArgumentException("Invalid plugin id: " + id);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A plugin needs a name");
        if (version == null || version.isBlank()) throw new IllegalArgumentException("A plugin needs a version");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
    }

    /**
     * Whether a string may be used as a plugin id.
     *
     * @param id candidate id, possibly null
     * @return true when well-formed and outside the reserved {@code jasper} namespace
     */
    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches() && !id.equals("jasper") && !id.startsWith("jasper.");
    }
}
```

`jasper-sdk/src/main/java/dev/jasper/sdk/events/package-info.java`:

```java
/**
 * The typed event bus: topics, subscription and publication, and the application's own topics.
 * Depends only on {@code dev.jasper.sdk}.
 */
package dev.jasper.sdk.events;
```

`Topic.java`:

```java
package dev.jasper.sdk.events;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A named, typed channel. The id's namespace decides who may publish: {@code jasper.*} belongs to the
 * application and {@code <plugin id>.*} to that plugin. Two topics are the same when both the id and
 * the payload type match; the runtime rejects one id used with two payload types.
 *
 * @param <T> payload type, normally an immutable record
 */
public final class Topic<T> {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_.-]{0,127}");
    private final String id;
    private final Class<T> payloadType;

    private Topic(String id, Class<T> payloadType) {
        this.id = id;
        this.payloadType = payloadType;
    }

    /**
     * Creates a topic constant.
     *
     * @param id namespaced id matching {@code [a-z][a-z0-9_.-]{0,127}}
     * @param payloadType the exact payload class
     * @param <T> payload type
     * @return the topic
     */
    public static <T> Topic<T> of(String id, Class<T> payloadType) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(payloadType, "payloadType");
        if (!ID.matcher(id).matches()) throw new IllegalArgumentException("Invalid topic id: " + id);
        return new Topic<>(id, payloadType);
    }

    /**
     * The namespaced id.
     *
     * @return the id
     */
    public String id() { return id; }

    /**
     * The payload class.
     *
     * @return the payload class
     */
    public Class<T> payloadType() { return payloadType; }

    @Override public boolean equals(Object other) {
        return other instanceof Topic<?> topic && id.equals(topic.id) && payloadType == topic.payloadType;
    }

    @Override public int hashCode() { return Objects.hash(id, payloadType); }

    @Override public String toString() { return "Topic[" + id + ", " + payloadType.getName() + "]"; }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test`
Expected: PASS, 5 tests.

- [ ] **Step 6: Add `verifySdkArchitecture`**

`gradle/sdk-architecture.gradle.kts`:

```kotlin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec

// SDK modules may reference only the JDK and dev.jasper.sdk, and their package graphs are acyclic.
// A module listed here must have the java plugin applied and produce classes/java/main.
val sdkModules = listOf(":jasper-sdk")
// Resolved at configuration time, like the other architecture scripts; tasks must not call project() while running.
val sdkProjects = sdkModules.associateWith { project(it) }
val verifySdkArchitecture = tasks.register("verifySdkArchitecture") {
    sdkModules.forEach { dependsOn("$it:classes") }
    doLast {
        val launcher = sdkProjects.values.first().extensions.getByType<JavaToolchainService>().launcherFor {
            languageVersion = JavaLanguageVersion.of(25)
            vendor = JvmVendorSpec.JETBRAINS
        }.get()
        val bin = launcher.metadata.installationPath.dir("bin").asFile
        val suffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
        fun tool(name: String, args: List<String>): String {
            val process = ProcessBuilder(listOf(bin.resolve(name + suffix).absolutePath) + args)
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { output }; return output
        }
        fun jdk(name: String) = name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.")
        fun packageOf(name: String) = name.substringBeforeLast('.')
        val edge = Regex("^\\s*(dev\\.jasper\\.sdk\\.\\S+)\\s+->\\s+(\\S+).*$")
        for (path in sdkModules) {
            val module = sdkProjects.getValue(path)
            val classes = module.layout.buildDirectory.dir("classes/java/main").get().asFile
            val classpath = module.configurations.getByName("runtimeClasspath").asPath
            val args = mutableListOf("--multi-release", "25", "--ignore-missing-deps", "-verbose:class", "-filter:none")
            if (classpath.isNotEmpty()) args += listOf("--class-path", classpath)
            val output = tool("jdeps", args + classes.absolutePath)
            val graph = mutableMapOf<String, MutableSet<String>>()
            output.lineSequence().forEach { line ->
                val match = edge.matchEntire(line) ?: return@forEach
                val (from, to) = match.destructured
                check(jdk(to) || to.startsWith("dev.jasper.sdk.")) { "$path depends on a non-JDK, non-SDK type: $line" }
                if (to.startsWith("dev.jasper.sdk.") && packageOf(from) != packageOf(to))
                    graph.getOrPut(packageOf(from)) { linkedSetOf() }.add(packageOf(to))
            }
            val active = linkedSetOf<String>(); val done = mutableSetOf<String>()
            fun visit(node: String) {
                if (node in done) return
                check(active.add(node)) { "SDK package cycle: $active -> $node" }
                graph[node].orEmpty().forEach { visit(it) }; active.remove(node); done.add(node)
            }
            graph.keys.forEach { visit(it) }
        }
    }
}
gradle.projectsEvaluated {
    sdkProjects.values.forEach { it.tasks.named("check") { dependsOn(verifySdkArchitecture) } }
}
```

Append to the end of the root `build.gradle.kts`:

```kotlin
apply(from = "gradle/sdk-architecture.gradle.kts")
```

- [ ] **Step 7: Run the guard and the module's checks**

The SDK has no dependencies, so a forbidden reference cannot even compile yet; the guard's failing case is exercised in Task 3, where the testkit gains a classpath.

Run: `./gradlew :jasper-sdk:check verifySdkArchitecture`
Expected: `BUILD SUCCESSFUL`, including `:jasper-sdk:javadoc`.

- [ ] **Step 8: Commit**

```bash
git branch --show-current
git add settings.gradle.kts build.gradle.kts gradle/sdk-architecture.gradle.kts jasper-sdk
git commit -m "feat: add the jasper-sdk module and its architecture guard

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: SDK plugin-facing API

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/events/{Events,AppEvents}.java`
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/activity/{package-info,Activities,ActivitySpec,ActivityHandle,ActivityEvent}.java`
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/services/{package-info,Services,ServiceUnavailableException}.java`
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/plugin/{package-info,Plugin,PluginContext,PluginConfig}.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/activity/ActivityValuesTest.java`

**Interfaces:**
- Consumes: `Subscription`, `PluginInfo`, `Variant`, `Topic` from Task 1.
- Produces (exact signatures used by every later task):
  - `Events`: `<T> Subscription subscribe(Topic<T> topic, Consumer<? super T> handler)`; `<T> void publish(Topic<T> topic, T payload)`
  - `AppEvents`: `Topic<ThemeChanged> THEME_CHANGED` (`jasper.app.theme-changed`), `Topic<ConfigReloaded> CONFIG_RELOADED` (`jasper.app.config-reloaded`); records `ThemeChanged(Variant variant)`, `ConfigReloaded()`
  - `Activities`: `Topic<ActivityEvent> TOPIC` (`jasper.activity`); `ActivityHandle begin(ActivitySpec spec)`; `List<ActivityEvent> current()`
  - `ActivitySpec(String title, String detail, Optional<String> activateActionId, Optional<Runnable> cancel)` plus `static ActivitySpec of(String title)`
  - `ActivityHandle`: `UUID id()`, `void progress(double fraction, String detail)`, `void detail(String detail)`, `void succeed(String detail)`, `void fail(String detail)`, `void cancelled()`
  - `ActivityEvent(UUID id, String sourcePluginId, String title, State state, OptionalDouble fraction, String detail, Optional<String> activateActionId)` with `enum State { STARTED, PROGRESS, SUCCEEDED, FAILED, CANCELLED }` and `boolean terminal()`
  - `Services`: `<T> void publish(Class<T> api, T implementation)`; `<T> void publishPerConsumer(Class<T> api, Function<PluginInfo, T> perConsumer)` (named differently on purpose: with two `publish` overloads, a lambda passed for a functional service interface is ambiguous to the compiler); `<T> T require(Class<T> api)`; `<T> Optional<T> find(Class<T> api)`
  - `ServiceUnavailableException(Class<?> api)` extends `RuntimeException`
  - `Plugin`: `void start(PluginContext context) throws Exception`; `default void stop()`
  - `PluginContext`: `PluginInfo plugin()`, `System.Logger log()`, `Path dataDirectory()`, `PluginConfig config()`, `Executor background()`, `Events events()`, `Activities activities()`, `Services services()`
  - `PluginConfig`: `Optional<String> string(String key)`, `OptionalLong integer(String key)`, `Optional<Boolean> bool(String key)`, `List<String> stringList(String key)`, `Optional<PluginConfig> table(String key)`, `Subscription onChanged(Runnable handler)`, `void report(String key, String message)`

- [ ] **Step 1: Write the failing test**

`jasper-sdk/src/test/java/dev/jasper/sdk/activity/ActivityValuesTest.java`:

```java
package dev.jasper.sdk.activity;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ActivityValuesTest {
    @Test void specRequiresATitleAndNonNullParts() {
        assertThat(ActivitySpec.of("Upload").detail()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> ActivitySpec.of(" "));
        assertThatNullPointerException().isThrownBy(() -> new ActivitySpec("t", null, Optional.empty(), Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new ActivitySpec("t", "", null, Optional.empty()));
    }

    @Test void eventValidatesFractionAndKnowsTerminalStates() {
        UUID id = UUID.randomUUID();
        var progress = new ActivityEvent(id, "a.b", "Upload", ActivityEvent.State.PROGRESS,
            OptionalDouble.of(0.5), "half", Optional.empty());
        assertThat(progress.terminal()).isFalse();
        assertThat(new ActivityEvent(id, "a.b", "Upload", ActivityEvent.State.FAILED,
            OptionalDouble.empty(), "no", Optional.empty()).terminal()).isTrue();
        for (double bad : new double[]{-0.1, 1.1, Double.NaN})
            assertThatIllegalArgumentException().isThrownBy(() -> new ActivityEvent(id, "a.b", "Upload",
                ActivityEvent.State.PROGRESS, OptionalDouble.of(bad), "", Optional.empty()));
    }

    @Test void topicLivesInTheApplicationNamespace() {
        assertThat(Activities.TOPIC.id()).isEqualTo("jasper.activity");
        assertThat(Activities.TOPIC.payloadType()).isEqualTo(ActivityEvent.class);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-sdk:test --tests '*ActivityValuesTest'`
Expected: compilation FAILS, `ActivitySpec` not found.

- [ ] **Step 3: Write the events API**

`events/Events.java`:

```java
package dev.jasper.sdk.events;

import dev.jasper.sdk.Subscription;
import java.util.function.Consumer;

/**
 * The central bus. Publication always enqueues; handlers run later on the UI thread in one global
 * first-in, first-out order, never synchronously and never re-entrantly. There is no replay: read
 * current state from its owner, then listen.
 */
public interface Events {
    /**
     * Subscribes on the UI thread. A handler that throws is logged against its plugin and does not
     * affect other subscribers.
     *
     * @param topic the topic; its payload type must agree with every other use of the same id
     * @param handler runs on the UI thread for each later publication
     * @param <T> payload type
     * @return the registration
     * @throws IllegalArgumentException when the id is already in use with another payload type
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     */
    <T> Subscription subscribe(Topic<T> topic, Consumer<? super T> handler);

    /**
     * Publishes from any thread to a topic the caller owns.
     *
     * @param topic a topic whose id starts with the publishing plugin's id followed by a dot
     * @param payload non-null instance of the topic's payload type
     * @param <T> payload type
     * @throws IllegalArgumentException when the caller does not own the topic or the type disagrees
     * @throws IllegalStateException when the plugin's context is closed
     */
    <T> void publish(Topic<T> topic, T payload);
}
```

`events/AppEvents.java`:

```java
package dev.jasper.sdk.events;

import dev.jasper.sdk.Variant;
import java.util.Objects;

/** Application topics that need no capability. */
public final class AppEvents {
    /** The look changed; read {@link ThemeChanged#variant()}. */
    public static final Topic<ThemeChanged> THEME_CHANGED = Topic.of("jasper.app.theme-changed", ThemeChanged.class);
    /** The configuration file was reloaded; plugin tables may have changed. */
    public static final Topic<ConfigReloaded> CONFIG_RELOADED = Topic.of("jasper.app.config-reloaded", ConfigReloaded.class);

    private AppEvents() { }

    /**
     * The application's look changed.
     *
     * @param variant the new variant
     */
    public record ThemeChanged(Variant variant) {
        /** Rejects a null variant. */
        public ThemeChanged { Objects.requireNonNull(variant, "variant"); }
    }

    /** The configuration was reloaded. */
    public record ConfigReloaded() { }
}
```

- [ ] **Step 4: Write the activity API**

`activity/package-info.java`:

```java
/**
 * Long-running work a plugin volunteers to show: begin an activity, report progress, end it once.
 * Depends on {@code dev.jasper.sdk.events}.
 */
package dev.jasper.sdk.activity;
```

`activity/ActivitySpec.java`:

```java
package dev.jasper.sdk.activity;

import java.util.Objects;
import java.util.Optional;

/**
 * What an activity is when it begins.
 *
 * @param title non-blank title shown to the user
 * @param detail initial detail line, possibly empty
 * @param activateActionId id of an action that brings the user to this work; resolved once the
 *                         actions API exists, ignored before then
 * @param cancel invoked when the user asks to stop the work; absent when it cannot be cancelled
 */
public record ActivitySpec(String title, String detail, Optional<String> activateActionId, Optional<Runnable> cancel) {
    /** Rejects a blank title and null parts. */
    public ActivitySpec {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("An activity needs a title");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(activateActionId, "activateActionId");
        Objects.requireNonNull(cancel, "cancel");
    }

    /**
     * An activity with only a title.
     *
     * @param title non-blank title
     * @return the spec
     */
    public static ActivitySpec of(String title) {
        return new ActivitySpec(title, "", Optional.empty(), Optional.empty());
    }
}
```

`activity/ActivityHandle.java`:

```java
package dev.jasper.sdk.activity;

import java.util.UUID;

/**
 * The publishing side of one activity; every method is safe from any thread. Exactly one of
 * {@link #succeed}, {@link #fail} or {@link #cancelled} ends it, and every later call is ignored.
 * Progress is coalesced: between two deliveries only the latest report survives, and a report still
 * waiting when the activity ends is dropped.
 */
public interface ActivityHandle {
    /**
     * The activity's identity in every {@link ActivityEvent}.
     *
     * @return the id
     */
    UUID id();

    /**
     * Reports determinate progress.
     *
     * @param fraction from 0 to 1 inclusive
     * @param detail detail line
     * @throws IllegalArgumentException when the fraction is outside 0 to 1 or not a number
     */
    void progress(double fraction, String detail);

    /**
     * Changes the detail line and keeps the last fraction.
     *
     * @param detail detail line
     */
    void detail(String detail);

    /**
     * Ends the activity successfully.
     *
     * @param detail final detail line
     */
    void succeed(String detail);

    /**
     * Ends the activity as failed.
     *
     * @param detail final detail line
     */
    void fail(String detail);

    /** Ends the activity because it was cancelled. */
    void cancelled();
}
```

`activity/ActivityEvent.java`:

```java
package dev.jasper.sdk.activity;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * One step in an activity's life, stamped by the runtime with the plugin that owns it.
 *
 * @param id the activity's identity
 * @param sourcePluginId the owning plugin, set by the runtime
 * @param title the title given when the activity began
 * @param state the step
 * @param fraction last reported fraction, absent while indeterminate
 * @param detail current detail line
 * @param activateActionId the spec's activation action, if any
 */
public record ActivityEvent(UUID id, String sourcePluginId, String title, State state,
                            OptionalDouble fraction, String detail, Optional<String> activateActionId) {
    /** The steps of an activity. */
    public enum State {
        /** The activity began. */
        STARTED,
        /** Progress or detail changed. */
        PROGRESS,
        /** Ended successfully. */
        SUCCEEDED,
        /** Ended in failure, or its plugin stopped while it was open. */
        FAILED,
        /** Ended by cancellation. */
        CANCELLED
    }

    /** Rejects null parts and a fraction outside 0 to 1. */
    public ActivityEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourcePluginId, "sourcePluginId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(fraction, "fraction");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(activateActionId, "activateActionId");
        if (fraction.isPresent()) {
            double value = fraction.getAsDouble();
            if (Double.isNaN(value) || value < 0 || value > 1)
                throw new IllegalArgumentException("An activity fraction must be from 0 to 1: " + value);
        }
    }

    /**
     * Whether this event ends the activity.
     *
     * @return true for SUCCEEDED, FAILED and CANCELLED
     */
    public boolean terminal() {
        return state == State.SUCCEEDED || state == State.FAILED || state == State.CANCELLED;
    }
}
```

`activity/Activities.java`:

```java
package dev.jasper.sdk.activity;

import dev.jasper.sdk.events.Topic;
import java.util.List;

/**
 * Begins activities and lists the running ones. Anyone may subscribe to {@link #TOPIC}; only handles
 * returned by {@link #begin} publish to it, so the lifecycle is well-formed and the source is verified.
 */
public interface Activities {
    /** Every activity's events, from every plugin. */
    Topic<ActivityEvent> TOPIC = Topic.of("jasper.activity", ActivityEvent.class);

    /**
     * Begins an activity and publishes its STARTED event. An activity still open when its plugin
     * stops is ended with FAILED by the runtime.
     *
     * @param spec what the activity is
     * @return the handle that reports progress and ends it
     * @throws IllegalStateException when the plugin's context is closed
     */
    ActivityHandle begin(ActivitySpec spec);

    /**
     * The latest event of each running activity, the one deliberate exception to "no replay", so a
     * consumer that starts late can show work already under way.
     *
     * @return an immutable snapshot
     */
    List<ActivityEvent> current();
}
```

- [ ] **Step 5: Write the services API**

`services/package-info.java`:

```java
/**
 * Plugin-to-plugin services: a provider publishes an interface from one of its exported packages, a
 * plugin that declares {@code requires} on the provider looks it up. Depends only on {@code dev.jasper.sdk}.
 */
package dev.jasper.sdk.services;
```

`services/ServiceUnavailableException.java`:

```java
package dev.jasper.sdk.services;

/** Thrown by {@link Services#require} when no started provider the caller requires offers the API. */
public final class ServiceUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Names the missing API.
     *
     * @param api the requested service interface
     */
    public ServiceUnavailableException(Class<?> api) {
        super("No service is available for " + api.getName());
    }
}
```

`services/Services.java`:

```java
package dev.jasper.sdk.services;

import dev.jasper.sdk.PluginInfo;
import java.util.Optional;
import java.util.function.Function;

/**
 * The typed service registry. A publication lasts from the provider's successful start until it stops
 * at shutdown; there is no withdrawal. Publications become visible only when the provider's
 * {@code start} returns normally, so no consumer sees a service from a plugin that failed to start.
 * Lookups are cache reads that run no provider code and are safe from any thread.
 */
public interface Services {
    /**
     * Publishes one shared implementation. UI thread, during the provider's own {@code start} only.
     *
     * @param api an interface from one of the provider's exported packages
     * @param implementation the instance every consumer receives
     * @param <T> service type
     * @throws IllegalStateException outside the provider's {@code start}
     * @throws IllegalArgumentException when {@code api} is not an interface the provider owns, or is already published
     */
    <T> void publish(Class<T> api, T implementation);

    /**
     * Publishes a per-consumer factory. The runtime calls it on the UI thread once for each plugin
     * that declares {@code requires} on the provider, just before that consumer starts, with the
     * consumer's verified identity. This gives caller attribution; it is not a security boundary.
     *
     * @param api an interface from one of the provider's exported packages
     * @param perConsumer creates the instance one consumer receives
     * @param <T> service type
     * @throws IllegalStateException outside the provider's {@code start}
     * @throws IllegalArgumentException when {@code api} is not an interface the provider owns, or is already published
     */
    <T> void publishPerConsumer(Class<T> api, Function<PluginInfo, T> perConsumer);

    /**
     * Looks up a service from a required provider.
     *
     * @param api the service interface
     * @param <T> service type
     * @return the caller's instance
     * @throws ServiceUnavailableException when no required, started provider offers it
     */
    <T> T require(Class<T> api);

    /**
     * Looks up a service from an optional provider.
     *
     * @param api the service interface
     * @param <T> service type
     * @return the caller's instance, or empty
     */
    <T> Optional<T> find(Class<T> api);
}
```

- [ ] **Step 6: Write the plugin entry API**

`plugin/package-info.java`:

```java
/**
 * The entry point a plugin implements and the context the runtime hands it. This package depends on
 * every other SDK package and nothing depends on it.
 */
package dev.jasper.sdk.plugin;
```

`plugin/Plugin.java`:

```java
package dev.jasper.sdk.plugin;

/**
 * A plugin's entry class: public, with a public no-argument constructor, named by {@code entry} in
 * {@code plugin.toml}. Both methods run on the UI thread and must return promptly; slow work belongs
 * on {@link PluginContext#background()}.
 */
public interface Plugin {
    /**
     * Registers the plugin's contributions. If this throws, everything the context handed out is
     * rolled back, the context is closed and the plugin is reported as failed.
     *
     * @param context this plugin's services
     * @throws Exception to fail the start
     */
    void start(PluginContext context) throws Exception;

    /**
     * Called once at shutdown, before the runtime closes what the plugin left open. Must not block:
     * a blocked UI thread cannot be abandoned and ends the process at the exit deadline.
     */
    default void stop() { }
}
```

`plugin/PluginConfig.java`:

```java
package dev.jasper.sdk.plugin;

import dev.jasper.sdk.Subscription;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Read-only view of the plugin's {@code [plugins."<id>"]} table in the user's configuration file.
 * Getters return empty when the key is absent or has another type, and are safe from any thread.
 */
public interface PluginConfig {
    /**
     * A string value.
     *
     * @param key key within this table
     * @return the value, or empty
     */
    Optional<String> string(String key);

    /**
     * An integer value.
     *
     * @param key key within this table
     * @return the value, or empty
     */
    OptionalLong integer(String key);

    /**
     * A boolean value.
     *
     * @param key key within this table
     * @return the value, or empty
     */
    Optional<Boolean> bool(String key);

    /**
     * An array of strings.
     *
     * @param key key within this table
     * @return the values, or an empty list when absent or not all strings
     */
    List<String> stringList(String key);

    /**
     * A nested table.
     *
     * @param key key within this table
     * @return the nested view, or empty
     */
    Optional<PluginConfig> table(String key);

    /**
     * Runs the handler on the UI thread after a reload that changed this plugin's table.
     *
     * @param handler change callback
     * @return the registration
     */
    Subscription onChanged(Runnable handler);

    /**
     * Reports a problem with one of the plugin's settings through the application's configuration
     * diagnostics. Reports are cleared on the next reload.
     *
     * @param key the offending key within this table
     * @param message plain text
     */
    void report(String key, String message);
}
```

`plugin/PluginContext.java`:

```java
package dev.jasper.sdk.plugin;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.events.Events;
import dev.jasper.sdk.services.Services;
import java.nio.file.Path;
import java.util.concurrent.Executor;

/**
 * Everything the application offers one plugin. Each plugin has its own context, so every
 * registration is attributed to it. After a failed start, and after shutdown, the context is closed:
 * contributing calls throw {@link IllegalStateException}, {@link #background()} rejects tasks and
 * handles already issued do nothing. {@link #log()} keeps working.
 */
public interface PluginContext {
    /**
     * This plugin's verified identity.
     *
     * @return the identity
     */
    PluginInfo plugin();

    /**
     * A logger routed to the application log and named after the plugin.
     *
     * @return the logger
     */
    System.Logger log();

    /**
     * The plugin's private data directory, created on first use. The plugin owns its contents.
     *
     * @return an existing directory
     */
    Path dataDirectory();

    /**
     * The plugin's configuration table.
     *
     * @return the read-only view
     */
    PluginConfig config();

    /**
     * A plugin-scoped executor for slow work. It stops admitting tasks at shutdown; tasks already
     * accepted are interrupted when the shutdown wait elapses.
     *
     * @return the executor
     */
    Executor background();

    /**
     * The event bus.
     *
     * @return the bus as seen by this plugin
     */
    Events events();

    /**
     * Activity publication.
     *
     * @return the activities service
     */
    Activities activities();

    /**
     * The service registry.
     *
     * @return the registry as seen by this plugin
     */
    Services services();
}
```

- [ ] **Step 7: Run the tests and guards**

Run: `./gradlew :jasper-sdk:check verifySdkArchitecture`
Expected: `BUILD SUCCESSFUL`; 8 tests pass; Javadoc reports no errors; no package cycle (`plugin` → `events`, `activity`, `services`, root; `activity` → `events`; `events`, `services` → root).

- [ ] **Step 8: Commit**

```bash
git branch --show-current
git add jasper-sdk
git commit -m "feat: define the SDK plugin, events, activities and services API

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 3: Testkit fake and the shared contract suite

The contract suite is the specification of runtime semantics. It is written once, as an abstract JUnit class in the testkit's test fixtures, and runs here against the fake and in Task 10 against the application.

**Files:**
- Modify: `settings.gradle.kts`, `gradle/sdk-architecture.gradle.kts`
- Create: `jasper-sdk-testkit/build.gradle.kts`
- Create: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/{package-info,FakePluginHost,FakePluginContext,FakePluginConfig}.java`
- Create: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/{ContractHarness,PluginContractTest}.java`
- Test: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`, `FakePluginHostTest.java`

**Interfaces:**
- Consumes: the whole SDK API from Tasks 1–2.
- Produces:
  - `FakePluginHost` (public, `AutoCloseable`): `FakePluginContext start(PluginInfo info, Set<String> requires, Set<String> optional, Plugin plugin)`, `boolean active(String pluginId)`, `void flush()`, `int runBackground()`, `<T> void publishApp(Topic<T> topic, T payload)`, `void setConfig(String pluginId, Map<String, Object> table)`, `List<ActivityEvent> activityLog()`, `List<String> failures()`, `List<String> reports()`, `void stopAll()`, `void close()`
  - `ContractHarness` (test fixture interface): `void start(PluginInfo info, Set<String> requires, Set<String> optional, Plugin plugin)`, `boolean active(String pluginId)`, `void ui(Runnable action)`, `void flush()`, `<T> void publishApp(Topic<T> topic, T payload)`, `List<ActivityEvent> activityLog()`, `void stopAll()`, `void close()`
  - `PluginContractTest` (abstract): `protected abstract ContractHarness newHarness()`; nested `public interface Greeter { String greet(String name); }`

- [ ] **Step 1: Register the module**

`settings.gradle.kts`:

```kotlin
rootProject.name = "jasper"
include("jasper-terminal", "jasper-app", "jasper-buddy", "jasper-sdk", "jasper-sdk-testkit")
```

`jasper-sdk-testkit/build.gradle.kts`:

```kotlin
// Headless fake of the plugin runtime plus the contract suite every runtime must pass.
plugins { `java-library`; `java-test-fixtures` }

dependencies {
    api(project(":jasper-sdk"))
    testFixturesApi(platform("org.junit:junit-bom:6.1.3"))
    testFixturesApi("org.junit.jupiter:junit-jupiter")
    testFixturesApi("org.assertj:assertj-core:3.27.7")
}
```

In `gradle/sdk-architecture.gradle.kts` change the module list:

```kotlin
val sdkModules = listOf(":jasper-sdk", ":jasper-sdk-testkit")
```

- [ ] **Step 2: Write the harness and the contract suite (the failing tests)**

`jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java`:

```java
package dev.jasper.sdk.testing.contract;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.plugin.Plugin;
import java.util.List;
import java.util.Set;

/** Adapts one plugin runtime to {@link PluginContractTest}. Implemented by the fake and by the application. */
public interface ContractHarness extends AutoCloseable {
    /**
     * Starts one plugin on the UI thread and waits. A plugin whose hard requirement is not active is
     * skipped without its {@code start} being called. A failing {@code start} is contained.
     */
    void start(PluginInfo info, Set<String> requires, Set<String> optional, Plugin plugin);

    /** Whether the plugin started successfully and has not been stopped. */
    boolean active(String pluginId);

    /** Runs the action on the UI thread and waits; assertion errors propagate to the caller. */
    void ui(Runnable action);

    /** Returns once every event queued so far, and every event those deliveries queued, has been delivered. */
    void flush();

    /** Publishes as the application. */
    <T> void publishApp(Topic<T> topic, T payload);

    /** Every activity event delivered so far, in delivery order, as the application's own observer sees them. */
    List<ActivityEvent> activityLog();

    /** Stops every active plugin in reverse start order. */
    void stopAll();

    @Override void close();
}
```

`jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java`:

```java
package dev.jasper.sdk.testing.contract;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import dev.jasper.sdk.events.AppEvents;
import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.services.ServiceUnavailableException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static dev.jasper.sdk.activity.ActivityEvent.State.FAILED;
import static dev.jasper.sdk.activity.ActivityEvent.State.PROGRESS;
import static dev.jasper.sdk.activity.ActivityEvent.State.STARTED;
import static dev.jasper.sdk.activity.ActivityEvent.State.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runtime semantics every implementation of the SDK must share. Test bodies run off the UI thread. */
public abstract class PluginContractTest {
    /** A service interface owned by this package, which harnesses register as the provider's export. */
    public interface Greeter { String greet(String name); }

    /** A plugin-owned payload. */
    public record Ping(int n) { }

    static final Topic<Ping> PING = Topic.of("test.alpha.ping", Ping.class);

    private ContractHarness h;

    /** A fresh runtime; the suite closes it after each test. */
    protected abstract ContractHarness newHarness();

    @BeforeEach void open() { h = newHarness(); }
    @AfterEach void close() { h.close(); }

    private static PluginInfo info(String id) { return new PluginInfo(id, id, "1.0.0", Set.of()); }

    private PluginContext started(String id, Set<String> requires) {
        var context = new AtomicReference<PluginContext>();
        h.start(info(id), requires, Set.of(), context::set);
        assertThat(h.active(id)).as("%s started", id).isTrue();
        return context.get();
    }

    @Test void deliveryIsQueuedOrderedAndNeverReentrant() {
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.events().subscribe(PING, ping -> {
                seen.add("in:" + ping.n());
                if (ping.n() == 1) context.events().publish(PING, new Ping(3));
                seen.add("out:" + ping.n());
            });
        });
        h.ui(() -> {
            alpha.get().events().publish(PING, new Ping(1));
            alpha.get().events().publish(PING, new Ping(2));
            seen.add("published");
        });
        h.flush();
        assertThat(seen).containsExactly("published", "in:1", "out:1", "in:2", "out:2", "in:3", "out:3");
    }

    @Test void onlyTheOwnerPublishesAndPayloadTypesCannotConflict() {
        PluginContext alpha = started("test.alpha", Set.of());
        PluginContext beta = started("test.beta", Set.of());
        assertThatIllegalArgumentException().isThrownBy(() -> beta.events().publish(PING, new Ping(1)));
        assertThatIllegalArgumentException().isThrownBy(() ->
            alpha.events().publish(AppEvents.CONFIG_RELOADED, new AppEvents.ConfigReloaded()));
        assertThatIllegalArgumentException().isThrownBy(() -> alpha.events().publish(Activities.TOPIC,
            new ActivityEvent(UUID.randomUUID(), "test.alpha", "forged", STARTED, OptionalDouble.empty(), "", Optional.empty())));
        assertThatIllegalArgumentException().as("a longer id sharing the prefix is another namespace").isThrownBy(() ->
            alpha.events().publish(Topic.of("test.alphabet.x", Ping.class), new Ping(1)));
        h.ui(() -> {
            beta.events().subscribe(PING, ping -> { });
            assertThatIllegalArgumentException().isThrownBy(() ->
                beta.events().subscribe(Topic.of("test.alpha.ping", String.class), text -> { }));
        });
    }

    @Test void closedSubscriptionsReceiveNothingAndCloseIsIdempotentFromAnyThread() {
        List<Integer> first = Collections.synchronizedList(new ArrayList<>());
        List<Integer> second = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        var subscriptions = new ArrayList<Subscription>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            subscriptions.add(context.events().subscribe(PING, ping -> first.add(ping.n())));
            subscriptions.add(context.events().subscribe(PING, ping -> second.add(ping.n())));
        });
        alpha.get().events().publish(PING, new Ping(1));
        h.flush();
        h.ui(() -> { subscriptions.get(0).close(); subscriptions.get(0).close(); });
        subscriptions.get(1).close();
        subscriptions.get(1).close();
        alpha.get().events().publish(PING, new Ping(2));
        h.flush();
        assertThat(first).containsExactly(1);
        assertThat(second).containsExactly(1);
    }

    @Test void aFailingHandlerDoesNotStopOtherSubscribers() {
        List<Integer> seen = Collections.synchronizedList(new ArrayList<>());
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            alpha.set(context);
            context.events().subscribe(PING, ping -> { throw new IllegalStateException("handler failure"); });
            context.events().subscribe(PING, ping -> seen.add(ping.n()));
        });
        alpha.get().events().publish(PING, new Ping(7));
        h.flush();
        assertThat(seen).containsExactly(7);
    }

    @Test void activitiesAreStampedCoalescedAndEndExactlyOnce() {
        PluginContext alpha = started("test.alpha", Set.of());
        var handle = new AtomicReference<ActivityHandle>();
        h.ui(() -> {
            ActivityHandle activity = alpha.activities().begin(
                new ActivitySpec("Upload", "starting", Optional.empty(), Optional.empty()));
            handle.set(activity);
            activity.progress(0.1, "a");
            activity.progress(0.2, "b");
            activity.progress(0.9, "almost");
            assertThatIllegalArgumentException().isThrownBy(() -> activity.progress(1.5, "too far"));
        });
        assertThat(alpha.activities().current()).singleElement()
            .satisfies(event -> assertThat(event.detail()).isEqualTo("almost"));
        h.flush();
        assertThat(h.activityLog()).extracting(ActivityEvent::state).containsExactly(STARTED, PROGRESS);
        ActivityEvent progress = h.activityLog().get(1);
        assertThat(progress.id()).isEqualTo(handle.get().id());
        assertThat(progress.sourcePluginId()).isEqualTo("test.alpha");
        assertThat(progress.title()).isEqualTo("Upload");
        assertThat(progress.fraction()).hasValue(0.9);

        handle.get().detail("still going");
        h.flush();
        assertThat(h.activityLog().get(2).fraction()).hasValue(0.9);
        assertThat(h.activityLog().get(2).detail()).isEqualTo("still going");

        h.ui(() -> {
            handle.get().progress(1.0, "dropped because the end arrives first");
            handle.get().succeed("done");
            handle.get().fail("ignored");
        });
        h.flush();
        assertThat(h.activityLog()).extracting(ActivityEvent::state)
            .containsExactly(STARTED, PROGRESS, PROGRESS, SUCCEEDED);
        assertThat(h.activityLog().get(3).detail()).isEqualTo("done");
        assertThat(alpha.activities().current()).isEmpty();
    }

    @Test void activitiesLeftOpenFailWhenTheirPluginStops() {
        PluginContext alpha = started("test.alpha", Set.of());
        alpha.activities().begin(ActivitySpec.of("Sync"));
        h.flush();
        h.stopAll();
        h.flush();
        assertThat(h.activityLog()).extracting(ActivityEvent::state).containsExactly(STARTED, FAILED);
        assertThat(h.active("test.alpha")).isFalse();
        assertThatIllegalStateException().isThrownBy(() -> alpha.activities().begin(ActivitySpec.of("late")));
    }

    @Test void servicesCommitAfterStartAndAreBuiltOncePerRequirer() {
        List<String> built = Collections.synchronizedList(new ArrayList<>());
        h.start(info("test.provider"), Set.of(), Set.of(), context -> {
            context.services().publishPerConsumer(Greeter.class, consumer -> {
                built.add(consumer.id());
                return name -> "hi " + name + " from " + consumer.id();
            });
            assertThat(context.services().find(Greeter.class)).as("not visible before commit").isEmpty();
        });
        var greeting = new AtomicReference<String>();
        h.start(info("test.consumer"), Set.of("test.provider"), Set.of(), context -> {
            greeting.set(context.services().require(Greeter.class).greet("x"));
            assertThat(context.services().require(Greeter.class))
                .isSameAs(context.services().find(Greeter.class).orElseThrow());
        });
        assertThat(greeting).hasValue("hi x from test.consumer");
        assertThat(built).containsExactly("test.consumer");

        PluginContext stranger = started("test.stranger", Set.of());
        assertThat(stranger.services().find(Greeter.class)).as("no requires, no service").isEmpty();
        assertThatThrownBy(() -> stranger.services().require(Greeter.class))
            .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test void publicationIsLimitedToStartAndToInterfacesPublishedOnce() {
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            assertThatIllegalArgumentException().isThrownBy(() -> context.services().publish(String.class, "text"));
            context.services().publish(Greeter.class, name -> name);
            assertThatIllegalArgumentException().isThrownBy(() -> context.services().publish(Greeter.class, name -> name));
        });
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.late"), Set.of(), Set.of(), alpha::set);
        h.ui(() -> assertThatIllegalStateException().isThrownBy(() ->
            alpha.get().services().publish(Runnable.class, () -> { })));
    }

    @Test void aFailedStartRollsEverythingBackAndClosesTheContext() {
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        var failed = new AtomicReference<PluginContext>();
        h.start(info("test.provider"), Set.of(), Set.of(), context -> {
            failed.set(context);
            context.events().subscribe(AppEvents.CONFIG_RELOADED, event -> seen.add("handler ran"));
            context.services().publish(Greeter.class, name -> name);
            context.activities().begin(ActivitySpec.of("Doomed"));
            throw new IllegalStateException("start failure");
        });
        assertThat(h.active("test.provider")).isFalse();

        var hardStarted = new AtomicBoolean();
        h.start(info("test.consumer"), Set.of("test.provider"), Set.of(), context -> hardStarted.set(true));
        assertThat(hardStarted).as("hard dependents are skipped").isFalse();
        assertThat(h.active("test.consumer")).isFalse();

        var optional = new AtomicReference<Optional<Greeter>>();
        h.start(info("test.optional"), Set.of(), Set.of("test.provider"),
            context -> optional.set(context.services().find(Greeter.class)));
        assertThat(h.active("test.optional")).isTrue();
        assertThat(optional.get()).as("the publication was discarded").isEmpty();

        h.publishApp(AppEvents.CONFIG_RELOADED, new AppEvents.ConfigReloaded());
        h.flush();
        assertThat(seen).isEmpty();
        assertThat(h.activityLog()).extracting(ActivityEvent::state).containsExactly(STARTED, FAILED);

        PluginContext closed = failed.get();
        h.ui(() -> assertThatIllegalStateException().isThrownBy(() -> closed.events().subscribe(PING, ping -> { })));
        assertThatIllegalStateException().isThrownBy(() -> closed.activities().begin(ActivitySpec.of("late")));
        assertThatThrownBy(() -> closed.background().execute(() -> { })).isInstanceOf(RejectedExecutionException.class);
        assertThat(closed.log()).isNotNull();
    }
}
```

`jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.testing.contract.ContractHarness;
import dev.jasper.sdk.testing.contract.PluginContractTest;
import java.util.List;
import java.util.Set;

/** The fake must behave like the application's runtime. */
class FakeContractTest extends PluginContractTest {
    @Override protected ContractHarness newHarness() {
        var host = new FakePluginHost();
        return new ContractHarness() {
            @Override public void start(PluginInfo info, Set<String> requires, Set<String> optional, Plugin plugin) {
                host.start(info, requires, optional, plugin);
            }
            @Override public boolean active(String pluginId) { return host.active(pluginId); }
            @Override public void ui(Runnable action) { action.run(); }
            @Override public void flush() { host.flush(); }
            @Override public <T> void publishApp(Topic<T> topic, T payload) { host.publishApp(topic, payload); }
            @Override public List<ActivityEvent> activityLog() { return host.activityLog(); }
            @Override public void stopAll() { host.stopAll(); }
            @Override public void close() { host.close(); }
        };
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :jasper-sdk-testkit:test`
Expected: compilation FAILS, `FakePluginHost` not found.

- [ ] **Step 4: Write the fake**

`jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/package-info.java`:

```java
/**
 * A headless, single-threaded stand-in for Jasper's plugin runtime, for plugin unit tests. The thread
 * that calls {@link dev.jasper.sdk.testing.FakePluginHost} plays the UI thread: events wait in a queue
 * until {@code flush()}, and background tasks wait until {@code runBackground()}.
 */
package dev.jasper.sdk.testing;
```

`FakePluginConfig.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.plugin.PluginConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Map-backed configuration view; nested tables share the root's listeners and report sink. */
final class FakePluginConfig implements PluginConfig {
    private final Supplier<Map<String, Object>> values;
    private final String prefix;
    private final CopyOnWriteArrayList<Runnable> listeners;
    private final BiConsumer<String, String> report;

    FakePluginConfig(Supplier<Map<String, Object>> values, String prefix,
                     CopyOnWriteArrayList<Runnable> listeners, BiConsumer<String, String> report) {
        this.values = values; this.prefix = prefix; this.listeners = listeners; this.report = report;
    }

    @Override public Optional<String> string(String key) {
        return values.get().get(key) instanceof String text ? Optional.of(text) : Optional.empty();
    }
    @Override public OptionalLong integer(String key) {
        return values.get().get(key) instanceof Long number ? OptionalLong.of(number) : OptionalLong.empty();
    }
    @Override public Optional<Boolean> bool(String key) {
        return values.get().get(key) instanceof Boolean flag ? Optional.of(flag) : Optional.empty();
    }
    @Override public List<String> stringList(String key) {
        if (!(values.get().get(key) instanceof List<?> list)) return List.of();
        for (Object item : list) if (!(item instanceof String)) return List.of();
        return list.stream().map(String.class::cast).toList();
    }
    @Override public Optional<PluginConfig> table(String key) {
        if (!(values.get().get(key) instanceof Map<?, ?>)) return Optional.empty();
        return Optional.of(new FakePluginConfig(() -> nested(key), prefix + key + ".", listeners, report));
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> nested(String key) {
        return values.get().get(key) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
    @Override public Subscription onChanged(Runnable handler) {
        listeners.add(handler);
        return () -> listeners.remove(handler);
    }
    @Override public void report(String key, String message) { report.accept(prefix + key, message); }
}
```

`FakePluginContext.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import dev.jasper.sdk.events.Events;
import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginConfig;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.services.ServiceUnavailableException;
import dev.jasper.sdk.services.Services;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

/** One fake plugin's context. Obtain it from {@link FakePluginHost#start}. */
public final class FakePluginContext implements PluginContext {
    enum State { STARTING, ACTIVE, STOPPING, CLOSED }

    private final FakePluginHost host;
    private final PluginInfo info;
    final Set<String> requires;
    final Plugin plugin;
    volatile State state = State.STARTING;
    volatile Map<String, Object> table = Map.of();
    final CopyOnWriteArrayList<Runnable> configListeners = new CopyOnWriteArrayList<>();
    final Map<Class<?>, Object> services = new ConcurrentHashMap<>();
    private final List<Subscription> owned = new ArrayList<>();

    FakePluginContext(FakePluginHost host, PluginInfo info, Set<String> requires, Plugin plugin) {
        this.host = host; this.info = info; this.requires = requires; this.plugin = plugin;
    }

    void requireOpen() {
        if (state == State.CLOSED) throw new IllegalStateException("Plugin context is closed: " + info.id());
    }

    void closeOwned() {
        List<Subscription> copy;
        synchronized (owned) { copy = new ArrayList<>(owned); owned.clear(); }
        for (int i = copy.size() - 1; i >= 0; i--) copy.get(i).close();
    }

    @Override public PluginInfo plugin() { return info; }
    @Override public System.Logger log() { return System.getLogger("dev.jasper.plugins." + info.id()); }

    @Override public Path dataDirectory() {
        try { return Files.createDirectories(host.dataRoot().resolve(info.id())); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    @Override public PluginConfig config() {
        return new FakePluginConfig(() -> table, "", configListeners,
            (key, message) -> host.reports.add(info.id() + ": " + key + ": " + message));
    }

    @Override public Executor background() {
        return task -> {
            if (state == State.CLOSED) throw new RejectedExecutionException("Plugin context is closed: " + info.id());
            host.background.add(task);
        };
    }

    @Override public Events events() {
        return new Events() {
            @Override public <T> Subscription subscribe(Topic<T> topic, Consumer<? super T> handler) {
                requireOpen();
                Subscription subscription = host.subscribe(info.id(), topic, handler);
                synchronized (owned) { owned.add(subscription); }
                return subscription;
            }
            @Override public <T> void publish(Topic<T> topic, T payload) {
                requireOpen();
                host.publish(info.id(), topic, payload);
            }
        };
    }

    @Override public Activities activities() {
        return new Activities() {
            @Override public ActivityHandle begin(ActivitySpec spec) {
                requireOpen();
                return host.begin(info.id(), spec);
            }
            @Override public List<ActivityEvent> current() { return host.currentActivities(); }
        };
    }

    @Override public Services services() {
        return new Services() {
            @Override public <T> void publish(Class<T> api, T implementation) {
                publishPerConsumer(api, consumer -> implementation);
            }
            @Override public <T> void publishPerConsumer(Class<T> api, Function<PluginInfo, T> perConsumer) {
                if (state != State.STARTING)
                    throw new IllegalStateException("Services may be published only during start(): " + info.id());
                host.stage(info.id(), api, perConsumer);
            }
            @Override public <T> T require(Class<T> api) {
                return find(api).orElseThrow(() -> new ServiceUnavailableException(api));
            }
            @Override public <T> Optional<T> find(Class<T> api) {
                return Optional.ofNullable(api.cast(services.get(api)));
            }
        };
    }
}
```

`FakePluginHost.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.plugin.Plugin;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A single-threaded plugin runtime for tests. It follows the same rules as the application: queued
 * ordered delivery, topic ownership, coalesced activity progress, services committed on a successful
 * start, rollback and a closed context on failure. It enforces no thread rules.
 */
public final class FakePluginHost implements AutoCloseable {
    private static final String APP = "jasper";

    private record Entry(String pluginId, Consumer<Object> handler, AtomicBoolean closed) { }
    private record Provider(String pluginId, Function<PluginInfo, ?> factory) { }

    private final Queue<Runnable> events = new ConcurrentLinkedQueue<>();
    final Queue<Runnable> background = new ConcurrentLinkedQueue<>();
    private final Map<String, Class<?>> topicTypes = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<Entry>> subscribers = new ConcurrentHashMap<>();
    private final Map<UUID, ActivityEvent> running = new ConcurrentHashMap<>();
    private final List<ActivityEvent> activityLog = new CopyOnWriteArrayList<>();
    private final Map<Class<?>, Provider> committed = new LinkedHashMap<>();
    private final Map<String, Map<Class<?>, Provider>> staged = new HashMap<>();
    private final Map<String, FakePluginContext> contexts = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> presets = new HashMap<>();
    private final List<String> failures = new CopyOnWriteArrayList<>();
    final List<String> reports = new CopyOnWriteArrayList<>();
    private Path dataRoot;

    /**
     * Starts a plugin the way the application would. A plugin whose hard requirement is not active is
     * not started; a throwing {@code start} is rolled back and recorded in {@link #failures()}.
     *
     * @param info the plugin's identity
     * @param requires ids of hard dependencies
     * @param optional ids of optional dependencies
     * @param plugin the plugin under test
     * @return the context the plugin received, closed when the plugin was skipped or failed
     */
    public FakePluginContext start(PluginInfo info, Set<String> requires, Set<String> optional, Plugin plugin) {
        if (contexts.containsKey(info.id())) throw new IllegalArgumentException("Already started: " + info.id());
        var all = new java.util.HashSet<>(requires);
        all.addAll(optional);
        var context = new FakePluginContext(this, info, Set.copyOf(all), Objects.requireNonNull(plugin));
        context.table = presets.getOrDefault(info.id(), Map.of());
        contexts.put(info.id(), context);
        for (String required : requires) {
            if (!active(required)) {
                context.state = FakePluginContext.State.CLOSED;
                failures.add(info.id() + " skipped: requires " + required);
                return context;
            }
        }
        for (var provider : committed.entrySet()) {
            if (!context.requires.contains(provider.getValue().pluginId())) continue;
            try { context.services.put(provider.getKey(), provider.getValue().factory().apply(info)); }
            catch (RuntimeException | LinkageError failure) { failures.add(provider.getValue().pluginId() + " factory: " + failure); }
        }
        try {
            plugin.start(context);
            committed.putAll(staged.getOrDefault(info.id(), Map.of()));
            context.state = FakePluginContext.State.ACTIVE;
        } catch (Exception | LinkageError failure) {
            failures.add(info.id() + " start: " + failure);
            teardown(context, "Plugin failed to start");
        } finally {
            staged.remove(info.id());
        }
        return context;
    }

    /**
     * Whether the plugin started and has not stopped.
     *
     * @param pluginId the plugin
     * @return true while active
     */
    public boolean active(String pluginId) {
        FakePluginContext context = contexts.get(pluginId);
        return context != null && context.state == FakePluginContext.State.ACTIVE;
    }

    /** Delivers every queued event, including those queued by the deliveries themselves. */
    public void flush() {
        Runnable delivery;
        while ((delivery = events.poll()) != null) delivery.run();
    }

    /**
     * Runs the background tasks queued so far on the calling thread.
     *
     * @return how many tasks ran
     */
    public int runBackground() {
        int count = 0;
        Runnable task;
        while ((task = background.poll()) != null) {
            try { task.run(); } catch (RuntimeException failure) { failures.add("background: " + failure); }
            count++;
        }
        return count;
    }

    /**
     * Publishes as the application, for example {@code AppEvents.THEME_CHANGED}.
     *
     * @param topic a topic in the {@code jasper.} namespace
     * @param payload the payload
     * @param <T> payload type
     */
    public <T> void publishApp(Topic<T> topic, T payload) { publish(APP, topic, payload); }

    /**
     * Replaces a plugin's configuration table. Before the plugin starts this is its initial table;
     * afterwards its change listeners run.
     *
     * @param pluginId the plugin
     * @param table values of type String, Long, Boolean, List of String, or nested Map
     */
    public void setConfig(String pluginId, Map<String, Object> table) {
        presets.put(pluginId, Map.copyOf(table));
        FakePluginContext context = contexts.get(pluginId);
        if (context == null) return;
        context.table = Map.copyOf(table);
        for (Runnable listener : context.configListeners) listener.run();
    }

    /**
     * Activity events delivered so far.
     *
     * @return the events in delivery order
     */
    public List<ActivityEvent> activityLog() { return List.copyOf(activityLog); }

    /**
     * Contained failures: throwing starts, stops, handlers, factories and background tasks.
     *
     * @return one line per failure
     */
    public List<String> failures() { return List.copyOf(failures); }

    /**
     * Configuration problems plugins reported.
     *
     * @return lines of the form {@code pluginId: key: message}
     */
    public List<String> reports() { return List.copyOf(reports); }

    /** Stops every active plugin in reverse start order. */
    public void stopAll() {
        var order = new ArrayList<>(contexts.values());
        for (int i = order.size() - 1; i >= 0; i--) {
            FakePluginContext context = order.get(i);
            if (context.state != FakePluginContext.State.ACTIVE) continue;
            context.state = FakePluginContext.State.STOPPING;
            try { context.plugin.stop(); }
            catch (RuntimeException | LinkageError failure) { failures.add(context.plugin().id() + " stop: " + failure); }
            teardown(context, "Plugin stopped");
        }
    }

    @Override public void close() {
        stopAll();
        flush();
        if (dataRoot == null) return;
        try (var files = Files.walk(dataRoot)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
            // A leftover temporary directory is harmless.
        }
    }

    Path dataRoot() {
        if (dataRoot == null) {
            try { dataRoot = Files.createTempDirectory("jasper-fake-plugins"); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
        return dataRoot;
    }

    private void teardown(FakePluginContext context, String reason) {
        context.state = FakePluginContext.State.CLOSED;
        context.closeOwned();
        String id = context.plugin().id();
        for (var list : subscribers.values()) list.removeIf(entry -> entry.pluginId().equals(id));
        for (ActivityEvent open : List.copyOf(running.values())) {
            if (!open.sourcePluginId().equals(id)) continue;
            running.remove(open.id());
            enqueue(new ActivityEvent(open.id(), id, open.title(), ActivityEvent.State.FAILED,
                open.fraction(), reason, open.activateActionId()));
        }
    }

    <T> Subscription subscribe(String pluginId, Topic<T> topic, Consumer<? super T> handler) {
        Objects.requireNonNull(handler, "handler");
        checkType(topic);
        @SuppressWarnings("unchecked")
        var entry = new Entry(pluginId, (Consumer<Object>) handler, new AtomicBoolean());
        var list = subscribers.computeIfAbsent(topic.id(), id -> new CopyOnWriteArrayList<>());
        list.add(entry);
        return () -> { entry.closed().set(true); list.remove(entry); };
    }

    <T> void publish(String owner, Topic<T> topic, T payload) {
        Objects.requireNonNull(payload, "payload");
        boolean owns = owner.equals(APP) ? topic.id().startsWith("jasper.") : topic.id().startsWith(owner + ".");
        if (!owns) throw new IllegalArgumentException(owner + " may not publish to " + topic.id());
        checkType(topic);
        if (!topic.payloadType().isInstance(payload))
            throw new IllegalArgumentException("Payload is not a " + topic.payloadType().getName());
        events.add(() -> deliver(topic.id(), payload));
    }

    private void checkType(Topic<?> topic) {
        Class<?> known = topicTypes.putIfAbsent(topic.id(), topic.payloadType());
        if (known != null && known != topic.payloadType())
            throw new IllegalArgumentException("Topic " + topic.id() + " already carries " + known.getName());
    }

    private void enqueue(ActivityEvent event) {
        checkType(Activities.TOPIC);
        events.add(() -> deliver(Activities.TOPIC.id(), event));
    }

    private void deliver(String topicId, Object payload) {
        if (payload instanceof ActivityEvent event && topicId.equals(Activities.TOPIC.id())) activityLog.add(event);
        for (Entry entry : subscribers.getOrDefault(topicId, new CopyOnWriteArrayList<>())) {
            if (entry.closed().get()) continue;
            try { entry.handler().accept(payload); }
            catch (RuntimeException | LinkageError failure) { failures.add(entry.pluginId() + " handler: " + failure); }
        }
    }

    void stage(String pluginId, Class<?> api, Function<PluginInfo, ?> factory) {
        Objects.requireNonNull(factory, "factory");
        if (!api.isInterface()) throw new IllegalArgumentException("A service API must be an interface: " + api.getName());
        var mine = staged.computeIfAbsent(pluginId, id -> new LinkedHashMap<>());
        if (committed.containsKey(api) || mine.containsKey(api))
            throw new IllegalArgumentException("Service already published: " + api.getName());
        mine.put(api, new Provider(pluginId, factory));
    }

    List<ActivityEvent> currentActivities() { return List.copyOf(running.values()); }

    ActivityHandle begin(String pluginId, ActivitySpec spec) {
        var activity = new FakeActivity(pluginId, spec);
        var started = activity.event(ActivityEvent.State.STARTED, OptionalDouble.empty(), spec.detail());
        running.put(activity.id, started);
        enqueue(started);
        return activity;
    }

    private final class FakeActivity implements ActivityHandle {
        private final UUID id = UUID.randomUUID();
        private final String pluginId;
        private final ActivitySpec spec;
        private boolean ended;
        private OptionalDouble fraction = OptionalDouble.empty();
        private ActivityEvent waiting;

        FakeActivity(String pluginId, ActivitySpec spec) { this.pluginId = pluginId; this.spec = spec; }

        ActivityEvent event(ActivityEvent.State state, OptionalDouble value, String detail) {
            return new ActivityEvent(id, pluginId, spec.title(), state, value, detail, spec.activateActionId());
        }

        @Override public UUID id() { return id; }

        @Override public void progress(double value, String detail) {
            report(OptionalDouble.of(value), detail);
        }

        @Override public void detail(String detail) {
            OptionalDouble last;
            synchronized (this) { last = fraction; }
            report(last, detail);
        }

        private void report(OptionalDouble value, String detail) {
            ActivityEvent event = event(ActivityEvent.State.PROGRESS, value, Objects.requireNonNull(detail, "detail"));
            boolean schedule;
            synchronized (this) {
                if (ended || !running.containsKey(id)) return;
                fraction = value;
                running.put(id, event);
                schedule = waiting == null;
                waiting = event;
            }
            if (schedule) events.add(() -> {
                ActivityEvent latest;
                synchronized (this) { latest = waiting; waiting = null; }
                if (latest != null) deliver(Activities.TOPIC.id(), latest);
            });
        }

        @Override public void succeed(String detail) { end(ActivityEvent.State.SUCCEEDED, detail); }
        @Override public void fail(String detail) { end(ActivityEvent.State.FAILED, detail); }
        @Override public void cancelled() { end(ActivityEvent.State.CANCELLED, ""); }

        private void end(ActivityEvent.State state, String detail) {
            OptionalDouble last;
            synchronized (this) {
                if (ended || running.remove(id) == null) { ended = true; return; }
                ended = true;
                waiting = null;
                last = fraction;
            }
            enqueue(event(state, last, Objects.requireNonNull(detail, "detail")));
        }
    }
}
```

- [ ] **Step 5: Add a fake-specific test for what the contract does not cover**

`jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePluginHostTest.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FakePluginHostTest {
    private static final PluginInfo INFO = new PluginInfo("test.alpha", "Alpha", "1.0.0", Set.of());

    @Test void configurationIsTypedNestedObservableAndReportable() {
        try (var host = new FakePluginHost()) {
            List<String> changes = new ArrayList<>();
            var context = host.start(INFO, Set.of(), Set.of(), c -> c.config().onChanged(() -> changes.add("changed")));
            host.setConfig("test.alpha", Map.of("name", "x", "count", 3L, "on", true,
                "hosts", List.of("a", "b"), "mixed", List.of("a", 1L), "ssh", Map.of("port", 22L)));
            assertThat(changes).containsExactly("changed");
            var config = context.config();
            assertThat(config.string("name")).hasValue("x");
            assertThat(config.string("count")).isEmpty();
            assertThat(config.integer("count")).hasValue(3L);
            assertThat(config.bool("on")).hasValue(true);
            assertThat(config.stringList("hosts")).containsExactly("a", "b");
            assertThat(config.stringList("mixed")).isEmpty();
            assertThat(config.table("ssh").orElseThrow().integer("port")).hasValue(22L);
            assertThat(config.table("name")).isEmpty();
            config.table("ssh").orElseThrow().report("port", "out of range");
            assertThat(host.reports()).containsExactly("test.alpha: ssh.port: out of range");
        }
    }

    @Test void backgroundWorkWaitsUntilRunAndDataDirectoriesArePerPlugin() {
        try (var host = new FakePluginHost()) {
            List<String> ran = new ArrayList<>();
            var context = host.start(INFO, Set.of(), Set.of(), c -> c.background().execute(() -> ran.add("task")));
            assertThat(ran).isEmpty();
            assertThat(host.runBackground()).isEqualTo(1);
            assertThat(ran).containsExactly("task");
            assertThat(context.dataDirectory()).isDirectory().hasFileName("test.alpha");
            assertThat(Files.isDirectory(context.dataDirectory())).isTrue();
        }
    }
}
```

- [ ] **Step 6: Run the tests and the guard, and prove the guard bites**

Run: `./gradlew :jasper-sdk-testkit:check verifySdkArchitecture`
Expected: `BUILD SUCCESSFUL`; `FakeContractTest` runs 9 inherited tests, `FakePluginHostTest` 2.

Then temporarily add `implementation("org.assertj:assertj-core:3.27.7")` to the testkit and `private static final Object LEAK = org.assertj.core.api.Assertions.class;` to `FakePluginHost`. `./gradlew verifySdkArchitecture` must FAIL with "depends on a non-JDK, non-SDK type". Remove both lines and confirm it passes again.

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add settings.gradle.kts gradle/sdk-architecture.gradle.kts jasper-sdk-testkit
git commit -m "feat: add the SDK testkit fake and the shared runtime contract suite

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 4: Descriptor and version ranges

**Files:**
- Modify: `jasper-app/build.gradle.kts` (add the SDK dependency)
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/{package-info,Version,VersionRange,PluginDescriptor,DescriptorParser}.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/{VersionRangeTest,DescriptorParserTest}.java`

**Interfaces:**
- Produces (all package-private in `dev.jasper.app.plugins`):
  - `record Version(int major, int minor, int patch) implements Comparable<Version>`; `static Version parse(String)` throws `IllegalArgumentException`
  - `record VersionRange(List<Bound> bounds)`; `static VersionRange parse(String)`; `boolean contains(Version)`; `static final VersionRange ANY`
  - `record PluginDescriptor(String id, String name, Version version, String entry, VersionRange sdk, String description, String vendor, Set<String> capabilities, Set<String> exports, List<Requirement> requires)`; nested `record Requirement(String id, VersionRange version, boolean optional)`; `PluginInfo info()`
  - `DescriptorParser.parse(String text)` throws `DescriptorParser.InvalidDescriptor` (checked); `DescriptorParser.CAPABILITIES` (`Set<String>`); `DescriptorParser.FORBIDDEN_PACKAGES` (`List<String>`)

- [ ] **Step 1: Add the dependency**

In `jasper-app/build.gradle.kts` `dependencies { }`, after `implementation(project(":jasper-buddy"))`:

```kotlin
    implementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
    testImplementation(testFixtures(project(":jasper-sdk-testkit")))
```

- [ ] **Step 2: Write the failing tests**

`VersionRangeTest.java`:

```java
package dev.jasper.app.plugins;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VersionRangeTest {
    @Test void versionsParseOneToThreeComponentsAndCompareNumerically() {
        assertThat(Version.parse("0.1")).isEqualTo(new Version(0, 1, 0));
        assertThat(Version.parse("2")).isEqualTo(new Version(2, 0, 0));
        assertThat(Version.parse("1.10.0")).isGreaterThan(Version.parse("1.9.9"));
        assertThat(Version.parse("1.2.3").toString()).isEqualTo("1.2.3");
        for (String bad : new String[]{"", "1.", "1.2.3.4", "01.2", "1.x", "-1", "1.0.0-beta"})
            assertThatIllegalArgumentException().as(bad).isThrownBy(() -> Version.parse(bad));
    }

    @Test void rangesCombineComparators() {
        VersionRange range = VersionRange.parse(">=0.1, <0.2");
        assertThat(range.contains(Version.parse("0.1.0"))).isTrue();
        assertThat(range.contains(Version.parse("0.1.9"))).isTrue();
        assertThat(range.contains(Version.parse("0.2.0"))).isFalse();
        assertThat(range.contains(Version.parse("0.0.9"))).isFalse();
        assertThat(VersionRange.parse("=1.2.3").contains(Version.parse("1.2.3"))).isTrue();
        assertThat(VersionRange.parse(">1, <=2").contains(Version.parse("2.0.0"))).isTrue();
        assertThat(VersionRange.parse(">1, <=2").contains(Version.parse("1.0.0"))).isFalse();
        assertThat(VersionRange.parse("  ").contains(Version.parse("9.9.9"))).isTrue();
        assertThat(VersionRange.ANY.toString()).isEqualTo("any");
        assertThat(range.toString()).isEqualTo(">=0.1.0, <0.2.0");
        for (String bad : new String[]{"0.1", "~1.0", ">=", ">=1,,<2", "=>1"})
            assertThatIllegalArgumentException().as(bad).isThrownBy(() -> VersionRange.parse(bad));
    }
}
```

`DescriptorParserTest.java`:

```java
package dev.jasper.app.plugins;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DescriptorParserTest {
    private static final String FULL = """
        id = "dev.jasper.ssh"
        name = "SSH"
        version = "0.1.0"
        entry = "dev.jasper.ssh.SshPlugin"
        sdk = ">=0.1, <0.2"
        description = "SSH sessions"
        vendor = "Jasper"
        capabilities = ["terminal.observe", "session.provide"]
        exports = ["dev.jasper.ssh.api"]

        [[requires]]
        id = "dev.jasper.vault"
        version = ">=0.1"

        [[requires]]
        id = "dev.example.extra"
        optional = true
        """;

    @Test void parsesEveryField() throws Exception {
        PluginDescriptor descriptor = DescriptorParser.parse(FULL);
        assertThat(descriptor.id()).isEqualTo("dev.jasper.ssh");
        assertThat(descriptor.version()).isEqualTo(new Version(0, 1, 0));
        assertThat(descriptor.entry()).isEqualTo("dev.jasper.ssh.SshPlugin");
        assertThat(descriptor.sdk().contains(Version.parse("0.1.5"))).isTrue();
        assertThat(descriptor.capabilities()).containsExactlyInAnyOrder("terminal.observe", "session.provide");
        assertThat(descriptor.exports()).containsExactly("dev.jasper.ssh.api");
        assertThat(descriptor.requires()).containsExactly(
            new PluginDescriptor.Requirement("dev.jasper.vault", VersionRange.parse(">=0.1"), false),
            new PluginDescriptor.Requirement("dev.example.extra", VersionRange.ANY, true));
        assertThat(descriptor.info().version()).isEqualTo("0.1.0");
        assertThat(descriptor.info().capabilities()).isEqualTo(descriptor.capabilities());
    }

    @Test void optionalFieldsDefaultToEmpty() throws Exception {
        PluginDescriptor minimal = DescriptorParser.parse("""
            id = "a.b"
            name = "AB"
            version = "1.0.0"
            entry = "a.b.Main"
            sdk = ">=0.1"
            """);
        assertThat(minimal.description()).isEmpty();
        assertThat(minimal.vendor()).isEmpty();
        assertThat(minimal.capabilities()).isEmpty();
        assertThat(minimal.exports()).isEmpty();
        assertThat(minimal.requires()).isEmpty();
    }

    @Test void rejectsEveryMalformedDescriptorWithANamedKey() {
        record Case(String replace, String with, String expected) { }
        for (Case c : List.of(
            new Case("id = \"dev.jasper.ssh\"", "id = \"jasper.core\"", "id"),
            new Case("id = \"dev.jasper.ssh\"", "id = 7", "id"),
            new Case("name = \"SSH\"", "", "name"),
            new Case("version = \"0.1.0\"", "version = \"one\"", "version"),
            new Case("entry = \"dev.jasper.ssh.SshPlugin\"", "entry = \"not a class\"", "entry"),
            new Case("sdk = \">=0.1, <0.2\"", "sdk = \"~0.1\"", "sdk"),
            new Case("\"session.provide\"", "\"root.everything\"", "capabilities"),
            new Case("\"dev.jasper.ssh.api\"", "\"dev.jasper.app.workspace\"", "exports"),
            new Case("id = \"dev.example.extra\"", "id = \"dev.jasper.vault\"", "requires"),
            new Case("id = \"dev.example.extra\"", "id = \"dev.jasper.ssh\"", "requires"),
            new Case("vendor = \"Jasper\"", "vendor = \"Jasper\"\nsurprise = true", "surprise"))) {
            assertThat(FULL).contains(c.replace());
            assertThatThrownBy(() -> DescriptorParser.parse(FULL.replace(c.replace(), c.with())))
                .as(c.with()).isInstanceOf(DescriptorParser.InvalidDescriptor.class).hasMessageContaining(c.expected());
        }
        assertThatThrownBy(() -> DescriptorParser.parse("id = ")).isInstanceOf(DescriptorParser.InvalidDescriptor.class);
        assertThat(DescriptorParser.CAPABILITIES).isEqualTo(Set.of("terminal.observe", "terminal.selection",
            "terminal.inject", "terminal.open", "session.provide"));
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*'`
Expected: compilation FAILS, `Version` not found.

- [ ] **Step 4: Implement**

`package-info.java`:

```java
/**
 * The plugin runtime: descriptors, locked consent state, resolution, per-plugin classloaders, the
 * queued EDT event bus, activities, the service registry and plugin lifetimes. {@link dev.jasper.app.plugins.PluginRuntime}
 * is the only public type and is owned and closed by the application. This is the only application
 * package that may reference {@code dev.jasper.sdk}; everything it needs from other packages arrives
 * as app-native values or JDK functional types.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.notifications, dev.jasper.app.persistence, dev.jasper.sdk, dev.jasper.sdk.activity, dev.jasper.sdk.events, dev.jasper.sdk.plugin, dev.jasper.sdk.services.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.plugins;
```

`Version.java`:

```java
package dev.jasper.app.plugins;

/** A one-to-three component numeric version; missing components are zero and pre-release tags are rejected. */
record Version(int major, int minor, int patch) implements Comparable<Version> {
    static Version parse(String text) {
        String[] parts = text == null ? new String[0] : text.strip().split("\\.", -1);
        if (parts.length < 1 || parts.length > 3) throw new IllegalArgumentException("Invalid version: " + text);
        int[] numbers = new int[3];
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].matches("0|[1-9][0-9]{0,8}")) throw new IllegalArgumentException("Invalid version: " + text);
            numbers[i] = Integer.parseInt(parts[i]);
        }
        return new Version(numbers[0], numbers[1], numbers[2]);
    }

    @Override public int compareTo(Version other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) result = Integer.compare(minor, other.minor);
        return result != 0 ? result : Integer.compare(patch, other.patch);
    }

    @Override public String toString() { return major + "." + minor + "." + patch; }
}
```

`VersionRange.java`:

```java
package dev.jasper.app.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Comma-separated comparators that must all hold; blank text admits every version. */
record VersionRange(List<Bound> bounds) {
    static final VersionRange ANY = new VersionRange(List.of());

    enum Op {
        GE(">="), LE("<="), GT(">"), LT("<"), EQ("=");
        final String symbol;
        Op(String symbol) { this.symbol = symbol; }
    }

    record Bound(Op op, Version version) {
        boolean admits(Version candidate) {
            int order = candidate.compareTo(version);
            return switch (op) {
                case GE -> order >= 0; case LE -> order <= 0; case GT -> order > 0; case LT -> order < 0; case EQ -> order == 0;
            };
        }
        @Override public String toString() { return op.symbol + version; }
    }

    VersionRange { bounds = List.copyOf(bounds); }

    static VersionRange parse(String text) {
        if (text == null || text.isBlank()) return ANY;
        List<Bound> bounds = new ArrayList<>();
        for (String raw : text.split(",", -1)) {
            String part = raw.strip();
            Op found = null;
            for (Op op : Op.values()) if (part.startsWith(op.symbol)) { found = op; break; }
            if (found == null) throw new IllegalArgumentException("Invalid version range: " + text);
            bounds.add(new Bound(found, Version.parse(part.substring(found.symbol.length()))));
        }
        return new VersionRange(bounds);
    }

    boolean contains(Version candidate) { return bounds.stream().allMatch(bound -> bound.admits(candidate)); }

    @Override public String toString() {
        return bounds.isEmpty() ? "any" : bounds.stream().map(Bound::toString).collect(Collectors.joining(", "));
    }
}
```

`Op.values()` order matters: `GE` and `LE` are tried before `GT` and `LT`, so `>=1` is never read as `>` followed by `=1`. `=>1` fails because `EQ` matches and `>1` is not a version.

`PluginDescriptor.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.PluginInfo;
import java.util.List;
import java.util.Set;

/** A validated {@code plugin.toml}. */
record PluginDescriptor(String id, String name, Version version, String entry, VersionRange sdk,
                        String description, String vendor, Set<String> capabilities, Set<String> exports,
                        List<Requirement> requires) {
    record Requirement(String id, VersionRange version, boolean optional) { }

    PluginDescriptor {
        capabilities = Set.copyOf(capabilities);
        exports = Set.copyOf(exports);
        requires = List.copyOf(requires);
    }

    PluginInfo info() { return new PluginInfo(id, name, version.toString(), capabilities); }
}
```

`DescriptorParser.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.PluginInfo;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** Pure parsing and validation of {@code plugin.toml}; every failure names the offending key. */
final class DescriptorParser {
    static final Set<String> CAPABILITIES = Set.of("terminal.observe", "terminal.selection",
        "terminal.inject", "terminal.open", "session.provide");
    /** Packages a plugin may neither define nor export. */
    static final List<String> FORBIDDEN_PACKAGES = List.of("dev.jasper.sdk", "dev.jasper.app",
        "dev.jasper.terminal", "dev.jasper.buddy", "java", "javax", "jdk");
    private static final Set<String> KEYS = Set.of("id", "name", "version", "entry", "sdk", "description",
        "vendor", "capabilities", "exports", "requires");
    private static final String CLASS_NAME = "[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+";
    private static final String PACKAGE_NAME = "[a-z_][a-z0-9_]*(\\.[a-z_][a-z0-9_]*)*";

    static final class InvalidDescriptor extends Exception {
        private static final long serialVersionUID = 1L;
        InvalidDescriptor(String message) { super(message); }
    }

    private DescriptorParser() { }

    static PluginDescriptor parse(String text) throws InvalidDescriptor {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors()) throw new InvalidDescriptor("plugin.toml is not valid TOML: " + toml.errors().get(0).getMessage());
        for (String key : toml.keySet()) if (!KEYS.contains(key)) throw new InvalidDescriptor("Unknown key: " + key);
        String id = text(toml, "id", true);
        if (!PluginInfo.validId(id)) throw new InvalidDescriptor("id is malformed or reserved: " + id);
        String name = text(toml, "name", true);
        if (name.isBlank()) throw new InvalidDescriptor("name must not be blank");
        Version version = version(text(toml, "version", true), "version");
        String entry = text(toml, "entry", true);
        if (!entry.matches(CLASS_NAME)) throw new InvalidDescriptor("entry is not a class name: " + entry);
        VersionRange sdk = range(text(toml, "sdk", true), "sdk");
        Set<String> capabilities = strings(toml, "capabilities");
        for (String capability : capabilities)
            if (!CAPABILITIES.contains(capability)) throw new InvalidDescriptor("capabilities names an unknown capability: " + capability);
        Set<String> exports = strings(toml, "exports");
        for (String exported : exports) {
            if (!exported.matches(PACKAGE_NAME)) throw new InvalidDescriptor("exports is not a package name: " + exported);
            if (forbidden(exported)) throw new InvalidDescriptor("exports names a reserved package: " + exported);
        }
        return new PluginDescriptor(id, name, version, entry, sdk, text(toml, "description", false),
            text(toml, "vendor", false), capabilities, exports, requires(toml, id));
    }

    static boolean forbidden(String packageName) {
        for (String prefix : FORBIDDEN_PACKAGES)
            if (packageName.equals(prefix) || packageName.startsWith(prefix + ".")) return true;
        return false;
    }

    private static List<PluginDescriptor.Requirement> requires(TomlTable toml, String self) throws InvalidDescriptor {
        Object value = toml.get(List.of("requires"));
        if (value == null) return List.of();
        if (!(value instanceof TomlArray array)) throw new InvalidDescriptor("requires must be an array of tables");
        List<PluginDescriptor.Requirement> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof TomlTable table)) throw new InvalidDescriptor("requires must be an array of tables");
            for (String key : table.keySet())
                if (!Set.of("id", "version", "optional").contains(key)) throw new InvalidDescriptor("requires has an unknown key: " + key);
            String id = text(table, "id", true);
            if (!PluginInfo.validId(id)) throw new InvalidDescriptor("requires names a malformed id: " + id);
            if (id.equals(self)) throw new InvalidDescriptor("requires names the plugin itself: " + id);
            if (!seen.add(id)) throw new InvalidDescriptor("requires names a plugin twice: " + id);
            Object optional = table.get(List.of("optional"));
            if (optional != null && !(optional instanceof Boolean)) throw new InvalidDescriptor("requires.optional must be a boolean");
            result.add(new PluginDescriptor.Requirement(id, range(text(table, "version", false), "requires.version"),
                Boolean.TRUE.equals(optional)));
        }
        return result;
    }

    private static String text(TomlTable table, String key, boolean required) throws InvalidDescriptor {
        Object value = table.get(List.of(key));
        if (value == null) {
            if (required) throw new InvalidDescriptor("Missing required key: " + key);
            return "";
        }
        if (!(value instanceof String text)) throw new InvalidDescriptor(key + " must be a string");
        return text;
    }

    private static Set<String> strings(TomlTable table, String key) throws InvalidDescriptor {
        Object value = table.get(List.of(key));
        if (value == null) return Set.of();
        if (!(value instanceof TomlArray array)) throw new InvalidDescriptor(key + " must be an array of strings");
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof String text)) throw new InvalidDescriptor(key + " must be an array of strings");
            result.add(text);
        }
        return result;
    }

    private static Version version(String text, String key) throws InvalidDescriptor {
        try { return Version.parse(text); }
        catch (IllegalArgumentException invalid) { throw new InvalidDescriptor(key + ": " + invalid.getMessage()); }
    }

    private static VersionRange range(String text, String key) throws InvalidDescriptor {
        try { return VersionRange.parse(text); }
        catch (IllegalArgumentException invalid) { throw new InvalidDescriptor(key + ": " + invalid.getMessage()); }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*'`
Expected: PASS, 5 tests. The `name` case expects "name" in the message: removing the line yields "Missing required key: name".

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add jasper-app/build.gradle.kts jasper-app/src/main/java/dev/jasper/app/plugins jasper-app/src/test/java/dev/jasper/app/plugins
git commit -m "feat: parse plugin descriptors and version ranges

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: Locked plugin state (`plugins.toml`)

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginStateStore.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginStateStoreTest.java`

**Interfaces:**
- Produces:
  - `PluginStateStore(Path file, Path lock, Duration wait)`; `Map<String, Entry> read() throws IOException`; `void transact(UnaryOperator<Map<String, Entry>> edit) throws IOException`
  - `record PluginStateStore.Entry(boolean enabled, Set<String> consented, boolean remove)`; `Entry.DEFAULT` = enabled, nothing consented, not removed
  - `AppDirs`: `Path plugins()`, `Path pluginState()`, `Path pluginLock()`, `Path pluginData()`

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.plugins;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class PluginStateStoreTest {
    @TempDir Path dir;

    private PluginStateStore store(Duration wait) {
        return new PluginStateStore(dir.resolve("plugins.toml"), dir.resolve("plugins.lock"), wait);
    }

    @Test void absentFileReadsAsEmptyAndTransactionsRoundTrip() throws Exception {
        PluginStateStore store = store(Duration.ofSeconds(2));
        assertThat(store.read()).isEmpty();
        store.transact(state -> {
            state.put("dev.example.tool", new PluginStateStore.Entry(false, Set.of("terminal.inject", "terminal.observe"), true));
            return state;
        });
        assertThat(store.read()).containsExactly(Map.entry("dev.example.tool",
            new PluginStateStore.Entry(false, Set.of("terminal.inject", "terminal.observe"), true)));
        assertThat(Files.readString(dir.resolve("plugins.toml"))).isEqualTo("""
            version = 1

            [plugins."dev.example.tool"]
            enabled = false
            consented = ["terminal.inject", "terminal.observe"]
            remove = true
            """);
    }

    @Test void concurrentEditorsOfDifferentEntriesBothSurvive() throws Exception {
        int writers = 8;
        var start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < writers; i++) {
            String id = "dev.example.p" + i;
            threads.add(Thread.ofPlatform().start(() -> {
                try {
                    start.await();
                    store(Duration.ofSeconds(10)).transact(state -> { state.put(id, PluginStateStore.Entry.DEFAULT); return state; });
                } catch (Throwable failure) { failures.add(failure); }
            }));
        }
        start.countDown();
        for (Thread thread : threads) thread.join();
        assertThat(failures).isEmpty();
        assertThat(store(Duration.ofSeconds(2)).read()).hasSize(writers);
    }

    @Test void aHeldLockFailsTheTransactionAndWritesNothing() throws Exception {
        PluginStateStore store = store(Duration.ofMillis(150));
        store.transact(state -> { state.put("a.b", PluginStateStore.Entry.DEFAULT); return state; });
        String before = Files.readString(dir.resolve("plugins.toml"));
        try (FileChannel other = FileChannel.open(dir.resolve("plugins.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var held = other.lock()) {
            assertThatThrownBy(() -> store.transact(state -> { state.clear(); return state; }))
                .isInstanceOf(IOException.class).hasMessageContaining("lock");
        }
        assertThat(Files.readString(dir.resolve("plugins.toml"))).isEqualTo(before);
    }

    @Test void malformedStateIsAnErrorRatherThanSilentlyEmpty() throws Exception {
        Files.writeString(dir.resolve("plugins.toml"), "version = 1\n[plugins.\"a.b\"]\nenabled = \"yes\"\n");
        assertThatThrownBy(() -> store(Duration.ofSeconds(1)).read()).isInstanceOf(IOException.class);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*PluginStateStoreTest'`
Expected: compilation FAILS, `PluginStateStore` not found.

- [ ] **Step 3: Implement**

`PluginStateStore.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.persistence.TomlStateFile;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Enabled flags, consented capability sets and pending removals. More than one Jasper process can edit
 * the file, so every change is a locked read-modify-write; nothing writes it outside {@link #transact},
 * in particular not at shutdown. Callers choose the thread: never the EDT for {@code transact}.
 */
final class PluginStateStore {
    record Entry(boolean enabled, Set<String> consented, boolean remove) {
        static final Entry DEFAULT = new Entry(true, Set.of(), false);
        Entry { consented = Set.copyOf(consented); }
    }

    private static final int MAX_BYTES = 256 * 1024;
    /** File locks are per process; this serializes editors inside one. */
    private static final ReentrantLock IN_PROCESS = new ReentrantLock();
    private final Path file;
    private final Path lock;
    private final Duration wait;

    PluginStateStore(Path file, Path lock, Duration wait) {
        this.file = Objects.requireNonNull(file); this.lock = Objects.requireNonNull(lock); this.wait = Objects.requireNonNull(wait);
    }

    /** Lock-free: the file is only ever replaced atomically. */
    Map<String, Entry> read() throws IOException {
        var text = TomlStateFile.readBounded(file, MAX_BYTES, "Plugin state");
        if (text.isEmpty()) return new TreeMap<>();
        TomlParseResult toml = Toml.parse(text.get());
        if (toml.hasErrors()) throw new IOException("Plugin state is not valid TOML: " + file);
        Map<String, Entry> result = new TreeMap<>();
        Object plugins = toml.get(List.of("plugins"));
        if (plugins == null) return result;
        if (!(plugins instanceof TomlTable table)) throw new IOException("Plugin state has a malformed plugins table: " + file);
        for (String id : table.keySet()) {
            if (!(table.get(List.of(id)) instanceof TomlTable entry)) throw new IOException("Plugin state entry is not a table: " + id);
            result.put(id, new Entry(flag(entry, "enabled", true, id), strings(entry, id), flag(entry, "remove", false, id)));
        }
        return result;
    }

    void transact(UnaryOperator<Map<String, Entry>> edit) throws IOException {
        long deadline = System.nanoTime() + wait.toNanos();
        boolean inProcess;
        try { inProcess = IN_PROCESS.tryLock(wait.toNanos(), TimeUnit.NANOSECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("Interrupted waiting for the plugin state lock"); }
        if (!inProcess) throw new IOException("Timed out waiting for the plugin state lock");
        try {
            Files.createDirectories(lock.toAbsolutePath().getParent());
            try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock held = acquire(channel, deadline)) {
                Objects.requireNonNull(held);
                Map<String, Entry> next = edit.apply(read());
                TomlStateFile.writeAtomically(file, ".plugins-", render(next));
            }
        } finally { IN_PROCESS.unlock(); }
    }

    private static FileLock acquire(FileChannel channel, long deadline) throws IOException {
        while (true) {
            try {
                FileLock held = channel.tryLock();
                if (held != null) return held;
            } catch (OverlappingFileLockException heldInThisProcess) {
                // Another channel in this JVM holds it; treat exactly like another process.
            }
            if (System.nanoTime() >= deadline) throw new IOException("Timed out waiting for the plugin state lock");
            try { Thread.sleep(25); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("Interrupted waiting for the plugin state lock"); }
        }
    }

    private static boolean flag(TomlTable entry, String key, boolean fallback, String id) throws IOException {
        Object value = entry.get(List.of(key));
        if (value == null) return fallback;
        if (!(value instanceof Boolean flag)) throw new IOException("Plugin state " + id + "." + key + " must be a boolean");
        return flag;
    }

    private static Set<String> strings(TomlTable entry, String id) throws IOException {
        Object value = entry.get(List.of("consented"));
        if (value == null) return Set.of();
        if (!(value instanceof TomlArray array)) throw new IOException("Plugin state " + id + ".consented must be an array");
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof String text)) throw new IOException("Plugin state " + id + ".consented must hold strings");
            result.add(text);
        }
        return result;
    }

    /** Ids and capabilities are restricted to characters that need no TOML escaping. */
    private static String render(Map<String, Entry> state) {
        var text = new StringBuilder("version = 1\n");
        for (var item : new TreeMap<>(state).entrySet()) {
            Entry entry = item.getValue();
            text.append("\n[plugins.\"").append(item.getKey()).append("\"]\n")
                .append("enabled = ").append(entry.enabled()).append('\n')
                .append("consented = [").append(new TreeSet<>(entry.consented()).stream()
                    .map(capability -> "\"" + capability + "\"").collect(Collectors.joining(", "))).append("]\n")
                .append("remove = ").append(entry.remove()).append('\n');
        }
        return text.toString();
    }
}
```

In `AppDirs.java`, after `shellIntegration()`:

```java
    /** User-installed plugins, one directory per plugin id. */
    public Path plugins() {
        return root.resolve("plugins");
    }

    /** Enabled flags, consented capabilities and pending removals. */
    public Path pluginState() {
        return root.resolve("plugins.toml");
    }

    /** Cross-process lock held around every change to {@link #pluginState()}. */
    public Path pluginLock() {
        return root.resolve("plugins.lock");
    }

    /** Parent of each plugin's private data directory. */
    public Path pluginData() {
        return root.resolve("plugin-data");
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests '*PluginStateStoreTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/plugins/PluginStateStore.java jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java jasper-app/src/test/java/dev/jasper/app/plugins/PluginStateStoreTest.java
git commit -m "feat: keep plugin state in locked read-modify-write transactions

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: Discovery and resolution

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/{PluginCandidate,PluginStatus,PluginDiscovery,PluginResolver}.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/testsupport/PluginJars.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/{PluginDiscoveryTest,PluginResolverTest}.java`

**Interfaces:**
- Consumes: `PluginDescriptor`, `DescriptorParser`, `Version`, `PluginStateStore.Entry`.
- Produces:
  - `record PluginCandidate(PluginDescriptor descriptor, Path directory, List<Path> jars, Origin origin)`; `enum PluginCandidate.Origin { BUNDLED, USER, DEV }`; `String id()`
  - `record PluginStatus(String id, String name, String version, PluginCandidate.Origin origin, State state, String reason)`; `enum PluginStatus.State { ACTIVE, DISABLED, NEEDS_CONSENT, SKIPPED, FAILED }`
  - `PluginDiscovery.scan(Path root, PluginCandidate.Origin origin, List<String> problems)` → `List<PluginCandidate>`; `PluginDiscovery.single(Path directory, PluginCandidate.Origin origin, List<String> problems)` → `Optional<PluginCandidate>`
  - `PluginResolver.resolve(List<PluginCandidate> candidates, Map<String, PluginStateStore.Entry> state, Version sdk, boolean safeMode)` → `record PluginResolver.Resolution(List<PluginCandidate> load, List<PluginStatus> rejected)`
  - Test support `PluginJars.build(Path pluginDirectory, String jarName, String descriptorOrNull, Map<String, String> sources, List<Path> classpath)` → `Path` (the jar)

- [ ] **Step 1: Write the fixture builder**

Tests need real plugin jars. Compiling tiny sources at test time keeps fixtures next to the assertions and needs no Gradle wiring.

`jasper-app/src/test/java/dev/jasper/app/testsupport/PluginJars.java`:

```java
package dev.jasper.app.testsupport;

import dev.jasper.sdk.plugin.Plugin;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.ToolProvider;

/** Compiles small plugin sources against the SDK and packs them, with an optional descriptor, into a jar. */
public final class PluginJars {
    private PluginJars() { }

    /** A descriptor with the required keys; append further TOML as needed. */
    public static String descriptor(String id, String version, String entry) {
        return "id = \"" + id + "\"\nname = \"" + id + "\"\nversion = \"" + version + "\"\nentry = \"" + entry
            + "\"\nsdk = \">=0.1\"\n";
    }

    /** A source for a plugin whose start and stop do nothing. */
    public static String emptyPlugin(String packageName, String className) {
        return "package " + packageName + ";\npublic final class " + className
            + " implements dev.jasper.sdk.plugin.Plugin {\n    @Override public void start(dev.jasper.sdk.plugin.PluginContext context) { }\n}\n";
    }

    public static Path build(Path pluginDirectory, String jarName, String descriptorOrNull,
                             Map<String, String> sources, List<Path> classpath) throws IOException {
        Path work = Files.createTempDirectory("plugin-fixture");
        Path sourceRoot = Files.createDirectories(work.resolve("src"));
        Path classes = Files.createDirectories(work.resolve("classes"));
        List<Path> files = new ArrayList<>();
        for (var source : sources.entrySet()) {
            Path file = sourceRoot.resolve(source.getKey().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            files.add(file);
        }
        if (!files.isEmpty()) {
            var compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) throw new IOException("Tests need a JDK, not a JRE");
            List<String> entries = new ArrayList<>();
            try { entries.add(Path.of(Plugin.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString()); }
            catch (java.net.URISyntaxException impossible) { throw new IOException(impossible); }
            classpath.forEach(path -> entries.add(path.toString()));
            var output = new StringWriter();
            try (var manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
                boolean compiled = compiler.getTask(output, manager, null,
                    List.of("-d", classes.toString(), "-classpath", String.join(java.io.File.pathSeparator, entries), "-proc:none"),
                    null, manager.getJavaFileObjectsFromPaths(files)).call();
                if (!compiled) throw new IOException("Fixture did not compile:\n" + output);
            }
        }
        Files.createDirectories(pluginDirectory);
        Path jar = pluginDirectory.resolve(jarName);
        // A manifest guarantees at least one entry: a library jar with no classes must still open as a jar.
        var manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        try (var out = new JarOutputStream(Files.newOutputStream(jar), manifest); var walk = Files.walk(classes)) {
            if (descriptorOrNull != null) {
                out.putNextEntry(new JarEntry("plugin.toml"));
                out.write(descriptorOrNull.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                out.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                out.write(Files.readAllBytes(file));
                out.closeEntry();
            }
        }
        return jar;
    }
}
```

- [ ] **Step 2: Write the failing tests**

`PluginDiscoveryTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.testsupport.PluginJars;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class PluginDiscoveryTest {
    @TempDir Path root;

    @Test void findsOneDescriptorJarPerDirectoryAndCollectsEveryJar() throws Exception {
        Path tool = root.resolve("dev.example.tool");
        PluginJars.build(tool, "tool.jar", PluginJars.descriptor("dev.example.tool", "1.0.0", "fix.tool.Main"),
            Map.of("fix.tool.Main", PluginJars.emptyPlugin("fix.tool", "Main")), List.of());
        PluginJars.build(tool, "library.jar", null, Map.of(), List.of());
        Files.writeString(root.resolve("README.txt"), "not a plugin");
        List<String> problems = new ArrayList<>();
        List<PluginCandidate> found = PluginDiscovery.scan(root, PluginCandidate.Origin.USER, problems);
        assertThat(problems).isEmpty();
        assertThat(found).singleElement().satisfies(candidate -> {
            assertThat(candidate.id()).isEqualTo("dev.example.tool");
            assertThat(candidate.origin()).isEqualTo(PluginCandidate.Origin.USER);
            assertThat(candidate.jars()).extracting(path -> path.getFileName().toString())
                .containsExactly("library.jar", "tool.jar");
        });
    }

    @Test void reportsDirectoriesItCannotUseAndKeepsGoing() throws Exception {
        PluginJars.build(root.resolve("dev.example.nodescriptor"), "a.jar", null, Map.of(), List.of());
        Path twice = root.resolve("dev.example.twice");
        PluginJars.build(twice, "a.jar", PluginJars.descriptor("dev.example.twice", "1.0.0", "x.A"), Map.of(), List.of());
        PluginJars.build(twice, "b.jar", PluginJars.descriptor("dev.example.twice", "1.0.0", "x.A"), Map.of(), List.of());
        PluginJars.build(root.resolve("dev.example.wrongname"), "a.jar",
            PluginJars.descriptor("dev.example.other", "1.0.0", "x.A"), Map.of(), List.of());
        PluginJars.build(root.resolve("dev.example.invalid"), "a.jar", "id = 7\n", Map.of(), List.of());
        PluginJars.build(root.resolve("dev.example.good"), "a.jar",
            PluginJars.descriptor("dev.example.good", "1.0.0", "x.A"), Map.of(), List.of());
        List<String> problems = new ArrayList<>();
        List<PluginCandidate> found = PluginDiscovery.scan(root, PluginCandidate.Origin.USER, problems);
        assertThat(found).extracting(PluginCandidate::id).containsExactly("dev.example.good");
        assertThat(problems).hasSize(4).anySatisfy(p -> assertThat(p).contains("dev.example.nodescriptor", "no plugin.toml"))
            .anySatisfy(p -> assertThat(p).contains("dev.example.twice", "more than one"))
            .anySatisfy(p -> assertThat(p).contains("dev.example.wrongname", "dev.example.other"))
            .anySatisfy(p -> assertThat(p).contains("dev.example.invalid", "id"));
    }

    @Test void aMissingRootIsEmptyAndADevelopmentDirectoryNeedNotBeNamedAfterItsId() throws Exception {
        List<String> problems = new ArrayList<>();
        assertThat(PluginDiscovery.scan(root.resolve("absent"), PluginCandidate.Origin.USER, problems)).isEmpty();
        Path dev = root.resolve("build-output");
        PluginJars.build(dev, "a.jar", PluginJars.descriptor("dev.example.dev", "1.0.0", "x.A"), Map.of(), List.of());
        assertThat(PluginDiscovery.single(dev, PluginCandidate.Origin.DEV, problems)).get()
            .extracting(PluginCandidate::id).isEqualTo("dev.example.dev");
        assertThat(problems).isEmpty();
    }
}
```

`PluginResolverTest.java`:

```java
package dev.jasper.app.plugins;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PluginResolverTest {
    private static final Version SDK = Version.parse("0.1.0");

    private static PluginCandidate plugin(String id, String version, PluginCandidate.Origin origin,
                                          Set<String> capabilities, PluginDescriptor.Requirement... requires) {
        return plugin(id, version, origin, capabilities, ">=0.1", requires);
    }

    private static PluginCandidate plugin(String id, String version, PluginCandidate.Origin origin, Set<String> capabilities,
                                          String sdk, PluginDescriptor.Requirement... requires) {
        var descriptor = new PluginDescriptor(id, id, Version.parse(version), "x.Main", VersionRange.parse(sdk), "", "",
            capabilities, Set.of(), List.of(requires));
        return new PluginCandidate(descriptor, Path.of(id), List.of(), origin);
    }

    private static PluginDescriptor.Requirement hard(String id, String range) {
        return new PluginDescriptor.Requirement(id, VersionRange.parse(range), false);
    }

    private static PluginDescriptor.Requirement optional(String id) {
        return new PluginDescriptor.Requirement(id, VersionRange.ANY, true);
    }

    private static Map<String, String> reasons(PluginResolver.Resolution resolution) {
        return resolution.rejected().stream().collect(java.util.stream.Collectors.toMap(PluginStatus::id,
            status -> status.state() + ": " + status.reason()));
    }

    @Test void ordersDependenciesFirstAndIsStableById() {
        var resolution = PluginResolver.resolve(List.of(
            plugin("b.ssh", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of(), hard("a.vault", ">=1.0"), optional("c.extra")),
            plugin("c.extra", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of()),
            plugin("a.vault", "1.2.0", PluginCandidate.Origin.BUNDLED, Set.of()),
            plugin("a.alone", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of())), Map.of(), SDK, false);
        assertThat(resolution.rejected()).isEmpty();
        assertThat(resolution.load()).extracting(PluginCandidate::id).containsExactly("a.alone", "a.vault", "c.extra", "b.ssh");
    }

    @Test void userPluginsNeedConsentForEveryCapabilityAndBundledOnesDoNot() {
        var inject = Set.of("terminal.inject", "terminal.observe");
        var state = Map.of(
            "u.partial", new PluginStateStore.Entry(true, Set.of("terminal.observe"), false),
            "u.full", new PluginStateStore.Entry(true, inject, false),
            "u.off", new PluginStateStore.Entry(false, inject, false),
            "u.removed", new PluginStateStore.Entry(true, inject, true));
        var resolution = PluginResolver.resolve(List.of(
            plugin("u.new", "1.0.0", PluginCandidate.Origin.USER, inject),
            plugin("u.partial", "1.0.0", PluginCandidate.Origin.USER, inject),
            plugin("u.full", "1.0.0", PluginCandidate.Origin.USER, inject),
            plugin("u.off", "1.0.0", PluginCandidate.Origin.USER, inject),
            plugin("u.removed", "1.0.0", PluginCandidate.Origin.USER, inject),
            plugin("u.harmless", "1.0.0", PluginCandidate.Origin.USER, Set.of()),
            plugin("b.bundled", "1.0.0", PluginCandidate.Origin.BUNDLED, inject),
            plugin("d.dev", "1.0.0", PluginCandidate.Origin.DEV, inject)), state, SDK, false);
        assertThat(resolution.load()).extracting(PluginCandidate::id)
            .containsExactly("b.bundled", "d.dev", "u.full");
        assertThat(reasons(resolution)).containsOnlyKeys("u.new", "u.partial", "u.off", "u.removed", "u.harmless")
            .containsEntry("u.off", "DISABLED: disabled by the user")
            .containsEntry("u.removed", "DISABLED: marked for removal");
        assertThat(reasons(resolution).get("u.partial")).startsWith("NEEDS_CONSENT").contains("terminal.inject");
        assertThat(reasons(resolution).get("u.harmless")).as("a new user plugin is inert until reviewed, even with no capabilities")
            .startsWith("NEEDS_CONSENT");
    }

    @Test void safeModeDropsUserPluginsOnly() {
        var state = Map.of("u.tool", new PluginStateStore.Entry(true, Set.of(), false));
        var resolution = PluginResolver.resolve(List.of(
            plugin("u.tool", "1.0.0", PluginCandidate.Origin.USER, Set.of()),
            plugin("b.bundled", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of())), state, SDK, true);
        assertThat(resolution.load()).extracting(PluginCandidate::id).containsExactly("b.bundled");
        assertThat(reasons(resolution)).containsEntry("u.tool", "DISABLED: safe mode");
    }

    @Test void theHigherVersionOfADuplicateIdWins() {
        var state = Map.of("x.tool", new PluginStateStore.Entry(true, Set.of(), false));
        var resolution = PluginResolver.resolve(List.of(
            plugin("x.tool", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of()),
            plugin("x.tool", "1.1.0", PluginCandidate.Origin.USER, Set.of())), state, SDK, false);
        assertThat(resolution.load()).singleElement().satisfies(candidate -> {
            assertThat(candidate.origin()).isEqualTo(PluginCandidate.Origin.USER);
            assertThat(candidate.descriptor().version()).isEqualTo(Version.parse("1.1.0"));
        });
        assertThat(resolution.rejected()).singleElement().satisfies(status -> {
            assertThat(status.state()).isEqualTo(PluginStatus.State.SKIPPED);
            assertThat(status.reason()).contains("superseded", "1.1.0");
        });
    }

    @Test void skipsIncompatibleSdkMissingOrIncompatibleDependenciesAndCascades() {
        var resolution = PluginResolver.resolve(List.of(
            plugin("a.future", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of(), ">=0.2"),
            plugin("b.needs-future", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of(), hard("a.future", ">=1.0")),
            plugin("c.needs-b", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of(), hard("b.needs-future", "")),
            plugin("d.old-vault", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of(), hard("e.vault", ">=2.0")),
            plugin("e.vault", "1.5.0", PluginCandidate.Origin.BUNDLED, Set.of()),
            plugin("f.optional-missing", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of(), optional("z.absent"))),
            Map.of(), SDK, false);
        assertThat(resolution.load()).extracting(PluginCandidate::id).containsExactly("e.vault", "f.optional-missing");
        var reasons = reasons(resolution);
        assertThat(reasons.get("a.future")).startsWith("SKIPPED").contains("SDK", "0.1.0");
        assertThat(reasons.get("b.needs-future")).startsWith("SKIPPED").contains("a.future");
        assertThat(reasons.get("c.needs-b")).startsWith("SKIPPED").contains("b.needs-future");
        assertThat(reasons.get("d.old-vault")).startsWith("SKIPPED").contains("e.vault", ">=2.0.0", "1.5.0");
    }

    @Test void everyMemberOfACycleIsSkippedAndSoAreItsDependents() {
        var resolution = PluginResolver.resolve(List.of(
            plugin("a.one", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of(), hard("b.two", "")),
            plugin("b.two", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of(), hard("a.one", "")),
            plugin("c.leaf", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of(), hard("a.one", "")),
            plugin("d.free", "1.0.0", PluginCandidate.Origin.BUNDLED, Set.of())), Map.of(), SDK, false);
        assertThat(resolution.load()).extracting(PluginCandidate::id).containsExactly("d.free");
        assertThat(reasons(resolution).get("a.one")).contains("cycle");
        assertThat(reasons(resolution).get("b.two")).contains("cycle");
        assertThat(reasons(resolution).get("c.leaf")).contains("a.one");
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*PluginDiscoveryTest' --tests '*PluginResolverTest'`
Expected: compilation FAILS, `PluginCandidate` not found.

- [ ] **Step 4: Implement**

`PluginCandidate.java`:

```java
package dev.jasper.app.plugins;

import java.nio.file.Path;
import java.util.List;

/** A plugin found on disk with a valid descriptor; nothing of it has been loaded. */
record PluginCandidate(PluginDescriptor descriptor, Path directory, List<Path> jars, Origin origin) {
    /** Bundled and development plugins are pre-consented; user plugins need the user's review. */
    enum Origin { BUNDLED, USER, DEV }

    PluginCandidate { jars = List.copyOf(jars); }

    String id() { return descriptor.id(); }
}
```

`PluginStatus.java`:

```java
package dev.jasper.app.plugins;

/** What became of one discovered plugin, for the log now and the Plugins manager later. */
record PluginStatus(String id, String name, String version, PluginCandidate.Origin origin, State state, String reason) {
    enum State { ACTIVE, DISABLED, NEEDS_CONSENT, SKIPPED, FAILED }

    static PluginStatus of(PluginCandidate candidate, State state, String reason) {
        return new PluginStatus(candidate.id(), candidate.descriptor().name(), candidate.descriptor().version().toString(),
            candidate.origin(), state, reason);
    }

    String formatted() {
        return id + " " + version + " [" + origin + "] " + state + (reason.isEmpty() ? "" : ": " + reason);
    }
}
```

`PluginDiscovery.java`:

```java
package dev.jasper.app.plugins;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;

/** Finds plugin directories and reads their descriptors. Opens jars briefly; loads no classes. */
final class PluginDiscovery {
    private static final int MAX_DESCRIPTOR_BYTES = 64 * 1024;

    private PluginDiscovery() { }

    /** Each subdirectory of {@code root} named after its plugin id. A missing root is simply empty. */
    static List<PluginCandidate> scan(Path root, PluginCandidate.Origin origin, List<String> problems) {
        List<PluginCandidate> found = new ArrayList<>();
        if (root == null || !Files.isDirectory(root)) return found;
        List<Path> directories;
        try (var children = Files.list(root)) { directories = children.filter(Files::isDirectory).sorted().toList(); }
        catch (IOException failure) { problems.add(root + ": cannot be listed: " + failure.getMessage()); return found; }
        for (Path directory : directories) {
            Optional<PluginCandidate> candidate = single(directory, origin, problems);
            if (candidate.isEmpty()) continue;
            String name = directory.getFileName().toString();
            if (!candidate.get().id().equals(name)) {
                problems.add(directory + ": directory " + name + " holds plugin " + candidate.get().id() + "; rename it to the plugin id");
                continue;
            }
            found.add(candidate.get());
        }
        return found;
    }

    /** One plugin directory; used directly for {@code --plugin-dir}, where the directory name is free. */
    static Optional<PluginCandidate> single(Path directory, PluginCandidate.Origin origin, List<String> problems) {
        List<Path> jars;
        try (var children = Files.list(directory)) {
            jars = children.filter(path -> path.getFileName().toString().endsWith(".jar") && Files.isRegularFile(path)).sorted().toList();
        } catch (IOException failure) { problems.add(directory + ": cannot be listed: " + failure.getMessage()); return Optional.empty(); }
        String descriptor = null;
        for (Path jar : jars) {
            try (var file = new JarFile(jar.toFile())) {
                var entry = file.getEntry("plugin.toml");
                if (entry == null) continue;
                if (descriptor != null) { problems.add(directory + ": more than one jar has a plugin.toml"); return Optional.empty(); }
                try (var input = file.getInputStream(entry)) {
                    byte[] bytes = input.readNBytes(MAX_DESCRIPTOR_BYTES + 1);
                    if (bytes.length > MAX_DESCRIPTOR_BYTES) { problems.add(directory + ": plugin.toml is too large"); return Optional.empty(); }
                    descriptor = new String(bytes, StandardCharsets.UTF_8);
                }
            } catch (IOException failure) { problems.add(jar + ": cannot be read: " + failure.getMessage()); return Optional.empty(); }
        }
        if (descriptor == null) { problems.add(directory + ": no plugin.toml in any jar"); return Optional.empty(); }
        try { return Optional.of(new PluginCandidate(DescriptorParser.parse(descriptor), directory, jars, origin)); }
        catch (DescriptorParser.InvalidDescriptor invalid) { problems.add(directory + ": " + invalid.getMessage()); return Optional.empty(); }
    }
}
```

`PluginResolver.java`:

```java
package dev.jasper.app.plugins;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Pure selection and ordering: no I/O, no class loading. */
final class PluginResolver {
    record Resolution(List<PluginCandidate> load, List<PluginStatus> rejected) {
        Resolution { load = List.copyOf(load); rejected = List.copyOf(rejected); }
    }

    private PluginResolver() { }

    static Resolution resolve(List<PluginCandidate> candidates, Map<String, PluginStateStore.Entry> state,
                              Version sdk, boolean safeMode) {
        List<PluginStatus> rejected = new ArrayList<>();
        Map<String, PluginCandidate> chosen = new TreeMap<>();
        for (PluginCandidate candidate : candidates) {
            if (safeMode && candidate.origin() == PluginCandidate.Origin.USER) {
                rejected.add(PluginStatus.of(candidate, PluginStatus.State.DISABLED, "safe mode"));
                continue;
            }
            PluginCandidate rival = chosen.get(candidate.id());
            if (rival == null) { chosen.put(candidate.id(), candidate); continue; }
            boolean wins = ORDER.compare(candidate, rival) > 0;
            PluginCandidate winner = wins ? candidate : rival, loser = wins ? rival : candidate;
            chosen.put(candidate.id(), winner);
            rejected.add(PluginStatus.of(loser, PluginStatus.State.SKIPPED,
                "superseded by version " + winner.descriptor().version() + " from " + winner.origin()));
        }
        for (PluginCandidate candidate : List.copyOf(chosen.values())) {
            PluginStatus status = admit(candidate, state.get(candidate.id()), sdk);
            if (status != null) { chosen.remove(candidate.id()); rejected.add(status); }
        }
        // Members of a cycle first, so their dependents are then explained by the missing dependency.
        for (String id : cycleMembers(chosen)) {
            rejected.add(PluginStatus.of(chosen.remove(id), PluginStatus.State.SKIPPED, "dependency cycle"));
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (PluginCandidate candidate : List.copyOf(chosen.values())) {
                String problem = unmet(candidate, chosen);
                if (problem == null) continue;
                chosen.remove(candidate.id());
                rejected.add(PluginStatus.of(candidate, PluginStatus.State.SKIPPED, problem));
                changed = true;
            }
        }
        return new Resolution(order(chosen), rejected);
    }

    /** Higher version wins; on a tie a development copy beats a user copy beats the bundled one. */
    private static final Comparator<PluginCandidate> ORDER = Comparator
        .comparing((PluginCandidate candidate) -> candidate.descriptor().version())
        .thenComparing(candidate -> candidate.origin().ordinal());

    private static PluginStatus admit(PluginCandidate candidate, PluginStateStore.Entry entry, Version sdk) {
        if (!candidate.descriptor().sdk().contains(sdk))
            return PluginStatus.of(candidate, PluginStatus.State.SKIPPED,
                "needs SDK " + candidate.descriptor().sdk() + "; this is SDK " + sdk);
        if (entry != null && entry.remove()) return PluginStatus.of(candidate, PluginStatus.State.DISABLED, "marked for removal");
        if (entry != null && !entry.enabled()) return PluginStatus.of(candidate, PluginStatus.State.DISABLED, "disabled by the user");
        if (candidate.origin() != PluginCandidate.Origin.USER) return null;
        if (entry == null) return PluginStatus.of(candidate, PluginStatus.State.NEEDS_CONSENT, "not reviewed yet");
        Set<String> missing = new TreeSet<>(candidate.descriptor().capabilities());
        missing.removeAll(entry.consented());
        return missing.isEmpty() ? null
            : PluginStatus.of(candidate, PluginStatus.State.NEEDS_CONSENT, "new capabilities: " + String.join(", ", missing));
    }

    private static String unmet(PluginCandidate candidate, Map<String, PluginCandidate> available) {
        for (PluginDescriptor.Requirement requirement : candidate.descriptor().requires()) {
            if (requirement.optional()) continue;
            PluginCandidate provider = available.get(requirement.id());
            if (provider == null) return "requires " + requirement.id() + ", which is not available";
            if (!requirement.version().contains(provider.descriptor().version()))
                return "requires " + requirement.id() + " " + requirement.version() + "; found " + provider.descriptor().version();
        }
        return null;
    }

    private static Set<String> cycleMembers(Map<String, PluginCandidate> plugins) {
        Set<String> members = new TreeSet<>();
        for (String start : plugins.keySet()) {
            // A plugin is in a cycle exactly when it can reach itself through hard or optional edges.
            Set<String> seen = new LinkedHashSet<>();
            List<String> frontier = new ArrayList<>(edges(plugins, start));
            while (!frontier.isEmpty()) {
                String next = frontier.remove(frontier.size() - 1);
                if (next.equals(start)) { members.add(start); break; }
                if (seen.add(next)) frontier.addAll(edges(plugins, next));
            }
        }
        return members;
    }

    private static List<String> edges(Map<String, PluginCandidate> plugins, String id) {
        PluginCandidate candidate = plugins.get(id);
        if (candidate == null) return List.of();
        return candidate.descriptor().requires().stream().map(PluginDescriptor.Requirement::id)
            .filter(plugins::containsKey).toList();
    }

    /** Depth-first post-order over ids in sorted order: dependencies first, otherwise alphabetical. */
    private static List<PluginCandidate> order(Map<String, PluginCandidate> plugins) {
        Map<String, PluginCandidate> ordered = new LinkedHashMap<>();
        for (String id : plugins.keySet()) visit(id, plugins, ordered);
        return new ArrayList<>(ordered.values());
    }

    private static void visit(String id, Map<String, PluginCandidate> plugins, Map<String, PluginCandidate> ordered) {
        if (ordered.containsKey(id)) return;
        for (String dependency : new TreeSet<>(edges(plugins, id))) visit(dependency, plugins, ordered);
        ordered.put(id, plugins.get(id));
    }
}
```

`visit` cannot recurse forever: cycle members were removed before `order` runs, and removing further plugins cannot create a cycle.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests '*PluginDiscoveryTest' --tests '*PluginResolverTest'`
Expected: PASS, 9 tests. In `ordersDependenciesFirstAndIsStableById`, `b.ssh` follows `c.extra` because an optional dependency that is present is still ordered first.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/plugins jasper-app/src/test/java/dev/jasper/app/plugins jasper-app/src/test/java/dev/jasper/app/testsupport/PluginJars.java
git commit -m "feat: discover plugin directories and resolve what may load

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: Per-plugin classloaders and loading

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/{PluginClassLoader,PluginLoader}.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginLoaderTest.java`

**Interfaces:**
- Consumes: `PluginCandidate`, `DescriptorParser.FORBIDDEN_PACKAGES`, `PluginJars`.
- Produces:
  - `PluginClassLoader` (extends `URLClassLoader`): constructor `(String pluginId, List<Path> jars, ClassLoader sdk, Map<String, ClassLoader> imports)`; `imports` maps an exported package name to the exporting plugin's loader
  - `PluginLoader.load(PluginCandidate candidate, Map<String, Loaded> loadedById, ClassLoader sdk)` → `Loaded`, throws `PluginLoader.LoadFailure` (checked)
  - `record PluginLoader.Loaded(PluginCandidate candidate, PluginClassLoader loader)` with `Plugin instantiate() throws LoadFailure`

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.plugins;

import dev.jasper.app.testsupport.PluginJars;
import dev.jasper.sdk.plugin.Plugin;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class PluginLoaderTest {
    @TempDir Path root;
    private final ClassLoader sdk = Plugin.class.getClassLoader();

    private PluginCandidate candidate(String id, String descriptorExtra, Map<String, String> sources, List<Path> classpath) throws Exception {
        String entry = sources.keySet().stream().filter(name -> name.endsWith(".Main")).findFirst().orElse("x.Main");
        PluginJars.build(root.resolve(id), id + ".jar", PluginJars.descriptor(id, "1.0.0", entry) + descriptorExtra, sources, classpath);
        List<String> problems = new ArrayList<>();
        PluginCandidate found = PluginDiscovery.single(root.resolve(id), PluginCandidate.Origin.DEV, problems).orElseThrow();
        assertThat(problems).isEmpty();
        return found;
    }

    @Test void instantiatesTheEntryClassInItsOwnLoaderAndSharesTheSdk() throws Exception {
        var loaded = PluginLoader.load(candidate("fix.tool", "", Map.of("fix.tool.Main", PluginJars.emptyPlugin("fix.tool", "Main")), List.of()), Map.of(), sdk);
        try (var loader = loaded.loader()) {
            Plugin plugin = loaded.instantiate();
            assertThat(plugin.getClass().getClassLoader()).isSameAs(loader);
            assertThat(plugin.getClass().getName()).isEqualTo("fix.tool.Main");
            assertThat(loader.loadClass("dev.jasper.sdk.plugin.Plugin")).isSameAs(Plugin.class);
        }
    }

    @Test void hidesTheApplicationAndItsLibraries() throws Exception {
        var loaded = PluginLoader.load(candidate("fix.tool", "", Map.of("fix.tool.Main", PluginJars.emptyPlugin("fix.tool", "Main")), List.of()), Map.of(), sdk);
        try (var loader = loaded.loader()) {
            for (String hidden : List.of("dev.jasper.app.Main", "dev.jasper.app.plugins.PluginRuntime",
                    "dev.jasper.terminal.session.TerminalSession", "dev.jasper.buddy.view.BuddyCompanion", "org.tomlj.Toml"))
                assertThatThrownBy(() -> loader.loadClass(hidden)).as(hidden).isInstanceOf(ClassNotFoundException.class);
            assertThat(loader.getResource("dev/jasper/app/Main.class")).isNull();
            assertThat(loader.loadClass("javax.swing.JPanel")).isNotNull();
        }
    }

    @Test void importsOnlyTheExportedPackagesOfDeclaredDependencies() throws Exception {
        var vaultCandidate = candidate("fix.vault", "exports = [\"fix.vault.api\"]\n", Map.of(
            "fix.vault.Main", PluginJars.emptyPlugin("fix.vault", "Main"),
            "fix.vault.api.Api", "package fix.vault.api;\npublic interface Api { String secret(); }\n",
            "fix.vault.impl.Secret", "package fix.vault.impl;\npublic final class Secret { }\n"), List.of());
        var vault = PluginLoader.load(vaultCandidate, Map.of(), sdk);
        var sshCandidate = candidate("fix.ssh", "[[requires]]\nid = \"fix.vault\"\n", Map.of(
            "fix.ssh.Main", "package fix.ssh;\npublic final class Main implements dev.jasper.sdk.plugin.Plugin {\n"
                + "    public static Class<?> api() { return fix.vault.api.Api.class; }\n"
                + "    @Override public void start(dev.jasper.sdk.plugin.PluginContext context) { }\n}\n"),
            vaultCandidate.jars());
        var ssh = PluginLoader.load(sshCandidate, Map.of("fix.vault", vault), sdk);
        try (var vaultLoader = vault.loader(); var sshLoader = ssh.loader()) {
            Class<?> seenBySsh = (Class<?>) ssh.instantiate().getClass().getMethod("api").invoke(null);
            assertThat(seenBySsh).isSameAs(vaultLoader.loadClass("fix.vault.api.Api"));
            assertThatThrownBy(() -> sshLoader.loadClass("fix.vault.impl.Secret")).isInstanceOf(ClassNotFoundException.class);
            assertThatThrownBy(() -> sshLoader.loadClass("fix.vault.Main")).isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test void rejectsPluginsThatDefineReservedOrImportedPackages() throws Exception {
        var intruder = candidate("fix.intruder", "", Map.of(
            "fix.intruder.Main", PluginJars.emptyPlugin("fix.intruder", "Main"),
            "dev.jasper.sdk.evil.Shadow", "package dev.jasper.sdk.evil;\npublic final class Shadow { }\n"), List.of());
        assertThatThrownBy(() -> PluginLoader.load(intruder, Map.of(), sdk))
            .isInstanceOf(PluginLoader.LoadFailure.class).hasMessageContaining("dev.jasper.sdk.evil");

        var vaultCandidate = candidate("fix.vault", "exports = [\"fix.vault.api\"]\n", Map.of(
            "fix.vault.Main", PluginJars.emptyPlugin("fix.vault", "Main"),
            "fix.vault.api.Api", "package fix.vault.api;\npublic interface Api { }\n"), List.of());
        var vault = PluginLoader.load(vaultCandidate, Map.of(), sdk);
        try (var vaultLoader = vault.loader()) {
            var squatter = candidate("fix.squatter", "[[requires]]\nid = \"fix.vault\"\n", Map.of(
                "fix.squatter.Main", PluginJars.emptyPlugin("fix.squatter", "Main"),
                "fix.vault.api.Fake", "package fix.vault.api;\npublic final class Fake { }\n"), List.of());
            assertThatThrownBy(() -> PluginLoader.load(squatter, Map.of("fix.vault", vault), sdk))
                .isInstanceOf(PluginLoader.LoadFailure.class).hasMessageContaining("fix.vault.api");
        }
    }

    @Test void reportsAnEntryClassThatIsMissingOrNotAPlugin() throws Exception {
        var missing = PluginLoader.load(candidate("fix.missing", "", Map.of(), List.of()), Map.of(), sdk);
        try (var loader = missing.loader()) {
            assertThatThrownBy(missing::instantiate).isInstanceOf(PluginLoader.LoadFailure.class).hasMessageContaining("x.Main");
        }
        var wrong = PluginLoader.load(candidate("fix.wrong", "", Map.of("fix.wrong.Main",
            "package fix.wrong;\npublic final class Main { }\n"), List.of()), Map.of(), sdk);
        try (var loader = wrong.loader()) {
            assertThatThrownBy(wrong::instantiate).isInstanceOf(PluginLoader.LoadFailure.class).hasMessageContaining("Plugin");
        }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*PluginLoaderTest'`
Expected: compilation FAILS, `PluginLoader` not found.

- [ ] **Step 3: Implement**

`PluginClassLoader.java`:

```java
package dev.jasper.app.plugins;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Delegation order: the SDK from the application's loader; the exported packages of declared
 * dependencies from their loaders; then the platform loader and this plugin's own jars. Application,
 * terminal and Buddy packages are refused outright. The parent is the platform loader, so nothing on
 * the application classpath (its libraries, its resources) is visible. This is a hygiene boundary,
 * the runtime twin of the bytecode allowlists; it is not a sandbox.
 */
final class PluginClassLoader extends URLClassLoader {
    static { registerAsParallelCapable(); }

    private static final List<String> HIDDEN = List.of("dev.jasper.app.", "dev.jasper.terminal.", "dev.jasper.buddy.");
    private final ClassLoader sdk;
    private final Map<String, ClassLoader> imports;

    PluginClassLoader(String pluginId, List<Path> jars, ClassLoader sdk, Map<String, ClassLoader> imports) {
        super("plugin:" + pluginId, urls(jars), ClassLoader.getPlatformClassLoader());
        this.sdk = sdk;
        this.imports = Map.copyOf(imports);
    }

    private static URL[] urls(List<Path> jars) {
        URL[] urls = new URL[jars.size()];
        for (int i = 0; i < urls.length; i++) {
            try { urls[i] = jars.get(i).toUri().toURL(); }
            catch (MalformedURLException impossible) { throw new IllegalArgumentException(impossible); }
        }
        return urls;
    }

    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) loaded = route(name);
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }

    private Class<?> route(String name) throws ClassNotFoundException {
        if (name.startsWith("dev.jasper.sdk.")) return sdk.loadClass(name);
        for (String hidden : HIDDEN)
            if (name.startsWith(hidden)) throw new ClassNotFoundException(name + " is not visible to plugins");
        int dot = name.lastIndexOf('.');
        ClassLoader exporter = imports.get(dot < 0 ? "" : name.substring(0, dot));
        return exporter != null ? exporter.loadClass(name) : super.loadClass(name, false);
    }
}
```

`PluginLoader.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.plugin.Plugin;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarFile;

/** Builds a plugin's classloader after checking what its jars define; instantiates the entry class. */
final class PluginLoader {
    static final class LoadFailure extends Exception {
        private static final long serialVersionUID = 1L;
        LoadFailure(String message, Throwable cause) { super(message, cause); }
    }

    record Loaded(PluginCandidate candidate, PluginClassLoader loader) {
        Plugin instantiate() throws LoadFailure {
            String entry = candidate.descriptor().entry();
            try {
                Class<?> type = Class.forName(entry, true, loader);
                if (!Plugin.class.isAssignableFrom(type)) throw new LoadFailure(entry + " does not implement Plugin", null);
                return (Plugin) type.getConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw new LoadFailure("Cannot create entry class " + entry + ": " + failure, failure);
            }
        }
    }

    private PluginLoader() { }

    /** {@code loadedById} must already hold every present dependency, hard or optional. */
    static Loaded load(PluginCandidate candidate, Map<String, Loaded> loadedById, ClassLoader sdk) throws LoadFailure {
        Map<String, ClassLoader> imports = new LinkedHashMap<>();
        for (PluginDescriptor.Requirement requirement : candidate.descriptor().requires()) {
            Loaded dependency = loadedById.get(requirement.id());
            if (dependency == null) continue;
            for (String exported : dependency.candidate().descriptor().exports()) imports.put(exported, dependency.loader());
        }
        for (var jar : candidate.jars()) {
            try (var file = new JarFile(jar.toFile())) {
                var entries = file.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    int slash = name.lastIndexOf('/');
                    if (!name.endsWith(".class") || slash < 0) continue;
                    String packageName = name.substring(0, slash).replace('/', '.');
                    if (DescriptorParser.forbidden(packageName))
                        throw new LoadFailure(jar.getFileName() + " defines a class in the reserved package " + packageName, null);
                    if (imports.containsKey(packageName))
                        throw new LoadFailure(jar.getFileName() + " defines a class in " + packageName + ", which a dependency exports", null);
                }
            } catch (IOException failure) { throw new LoadFailure("Cannot read " + jar + ": " + failure.getMessage(), failure); }
        }
        return new Loaded(candidate, new PluginClassLoader(candidate.id(), candidate.jars(), sdk, imports));
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests '*PluginLoaderTest'`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/plugins jasper-app/src/test/java/dev/jasper/app/plugins/PluginLoaderTest.java
git commit -m "feat: load plugins in filtering per-plugin classloaders

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 8: Containment, the event bus and activities

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/{Containment,EventBus,ActivityHub}.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/EventBusTest.java`

**Interfaces:**
- Consumes: SDK `Topic`, `Subscription`, `Activities`, `ActivityEvent`, `ActivityHandle`, `ActivitySpec`.
- Produces:
  - `Containment(BooleanSupplier onUi)`: `Throwable attempt(String pluginId, String what, Callable<?> action)` (null on success; catches `Exception` and `LinkageError`), `boolean run(String pluginId, String what, Runnable action)`, `void record(String pluginId, String what, Throwable failure)`, `int failures(String pluginId)`, `String executing()` (the plugin callback currently on the UI thread, or null)
  - `EventBus(Consumer<Runnable> ui, Containment containment)`: `static final String APP = "jasper"`; `<T> Subscription subscribe(String pluginId, Topic<T> topic, Consumer<? super T> handler)`; `<T> void publish(String owner, Topic<T> topic, T payload)`; `void enqueue(Runnable delivery)`; `void deliver(String topicId, Object payload)`; `void checkType(Topic<?> topic)`; `void removeAll(String pluginId)`; `int pending()`
  - `ActivityHub(EventBus bus)`: `ActivityHandle begin(String pluginId, ActivitySpec spec)`; `List<ActivityEvent> current()`; `void failAll(String pluginId, String reason)`

The contract suite (Task 10) is the main test of these classes. This task's tests cover only what the contract cannot see: queue accounting, bulk removal, failure counting and the executing marker.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.activity.ActivitySpec;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.events.Topic;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EventBusTest {
    record Ping(int n) { }
    private static final Topic<Ping> PING = Topic.of("test.alpha.ping", Ping.class);

    private final Deque<Runnable> ui = new ArrayDeque<>();
    private final Containment containment = new Containment(() -> true);
    private final EventBus bus = new EventBus(ui::add, containment);

    private void drain() { while (!ui.isEmpty()) ui.poll().run(); }

    @Test void pendingCountsQueuedDeliveriesUntilTheyRun() {
        bus.publish("test.alpha", PING, new Ping(1));
        bus.publish("test.alpha", PING, new Ping(2));
        assertThat(bus.pending()).isEqualTo(2);
        drain();
        assertThat(bus.pending()).isZero();
    }

    @Test void rejectsNullAndMistypedPayloads() {
        assertThatNullPointerException().isThrownBy(() -> bus.publish("test.alpha", PING, null));
        @SuppressWarnings({"unchecked", "rawtypes"})
        Topic<Object> raw = (Topic) PING;
        assertThatIllegalArgumentException().isThrownBy(() -> bus.publish("test.alpha", raw, "not a ping"));
        assertThat(bus.pending()).isZero();
    }

    @Test void removeAllDropsOnePluginsSubscriptions() {
        List<String> seen = new ArrayList<>();
        bus.subscribe("test.alpha", PING, ping -> seen.add("alpha"));
        bus.subscribe("test.beta", PING, ping -> seen.add("beta"));
        bus.removeAll("test.alpha");
        bus.publish("test.alpha", PING, new Ping(1));
        drain();
        assertThat(seen).containsExactly("beta");
    }

    @Test void containmentCountsFailuresAndNamesTheExecutingCallback() {
        List<String> executing = new ArrayList<>();
        bus.subscribe("test.alpha", PING, ping -> { executing.add(containment.executing()); throw new IllegalStateException("x"); });
        bus.publish("test.alpha", PING, new Ping(1));
        drain();
        assertThat(executing).singleElement().asString().contains("test.alpha", "test.alpha.ping");
        assertThat(containment.executing()).isNull();
        assertThat(containment.failures("test.alpha")).isEqualTo(1);
        assertThat(containment.attempt("test.beta", "start", () -> { throw new java.io.IOException("checked"); }))
            .isInstanceOf(java.io.IOException.class);
        assertThat(containment.attempt("test.beta", "start", () -> null)).isNull();
        assertThatThrownBy(() -> containment.attempt("test.beta", "start", () -> { throw new AssertionError("not contained"); }))
            .isInstanceOf(AssertionError.class);
    }

    @Test void failAllEndsOnlyThatPluginsActivities() {
        var hub = new ActivityHub(bus);
        List<ActivityEvent> log = new ArrayList<>();
        bus.subscribe(EventBus.APP, Activities.TOPIC, log::add);
        hub.begin("test.alpha", ActivitySpec.of("A"));
        var beta = hub.begin("test.beta", ActivitySpec.of("B"));
        hub.failAll("test.alpha", "Plugin stopped");
        drain();
        assertThat(log).extracting(event -> event.title() + ":" + event.state())
            .containsExactly("A:STARTED", "B:STARTED", "A:FAILED");
        assertThat(log.get(2).detail()).isEqualTo("Plugin stopped");
        assertThat(hub.current()).singleElement().satisfies(event -> assertThat(event.id()).isEqualTo(beta.id()));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*EventBusTest'`
Expected: compilation FAILS, `Containment` not found.

- [ ] **Step 3: Implement**

`Containment.java`:

```java
package dev.jasper.app.plugins;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Every call from the application into plugin code goes through here: {@link Exception} and
 * {@link LinkageError} are caught, logged against the plugin and counted; other errors propagate.
 * While a callback runs on the UI thread it is remembered, so an exit deadline that fires can say
 * whose callback blocked. Safe from any thread.
 */
final class Containment {
    private static final long SLOW_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
    private final ConcurrentHashMap<String, AtomicInteger> failures = new ConcurrentHashMap<>();
    private final BooleanSupplier onUi;
    private volatile String executing;

    Containment(BooleanSupplier onUi) { this.onUi = onUi; }

    /** Returns the contained failure, or null when the action completed. */
    Throwable attempt(String pluginId, String what, Callable<?> action) {
        boolean ui = onUi.getAsBoolean();
        String previous = executing;
        if (ui) executing = pluginId + " (" + what + ")";
        long began = System.nanoTime();
        try {
            action.call();
            return null;
        } catch (Exception | LinkageError failure) {
            record(pluginId, what, failure);
            return failure;
        } finally {
            if (ui) executing = previous;
            long took = System.nanoTime() - began;
            if (ui && took > SLOW_NANOS)
                logger(pluginId).log(System.Logger.Level.INFO, "Slow plugin callback on the UI thread: " + what
                    + " took " + TimeUnit.NANOSECONDS.toMillis(took) + " ms");
        }
    }

    boolean run(String pluginId, String what, Runnable action) {
        return attempt(pluginId, what, () -> { action.run(); return null; }) == null;
    }

    void record(String pluginId, String what, Throwable failure) {
        failures.computeIfAbsent(pluginId, id -> new AtomicInteger()).incrementAndGet();
        logger(pluginId).log(System.Logger.Level.WARNING, "Plugin " + pluginId + " failed in " + what, failure);
    }

    int failures(String pluginId) {
        AtomicInteger count = failures.get(pluginId);
        return count == null ? 0 : count.get();
    }

    String executing() { return executing; }

    static System.Logger logger(String pluginId) { return System.getLogger("dev.jasper.plugins." + pluginId); }
}
```

`EventBus.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.events.Topic;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Publication always enqueues on the UI executor, so delivery is one global first-in, first-out order
 * and never re-entrant. Ownership: {@code jasper.*} is the application's, {@code <plugin id>.*} that
 * plugin's. Subscription and publication are safe from any thread; thread rules for plugins are
 * enforced by their context.
 */
final class EventBus {
    static final String APP = "jasper";

    private record Entry(String pluginId, String topicId, Consumer<Object> handler, AtomicBoolean closed) { }

    private final Consumer<Runnable> ui;
    private final Containment containment;
    private final ConcurrentHashMap<String, Class<?>> types = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Entry>> subscribers = new ConcurrentHashMap<>();
    private final AtomicInteger pending = new AtomicInteger();

    EventBus(Consumer<Runnable> ui, Containment containment) { this.ui = ui; this.containment = containment; }

    <T> Subscription subscribe(String pluginId, Topic<T> topic, Consumer<? super T> handler) {
        Objects.requireNonNull(handler, "handler");
        checkType(topic);
        @SuppressWarnings("unchecked")
        var entry = new Entry(pluginId, topic.id(), (Consumer<Object>) handler, new AtomicBoolean());
        var list = subscribers.computeIfAbsent(topic.id(), id -> new CopyOnWriteArrayList<>());
        list.add(entry);
        return () -> { entry.closed().set(true); list.remove(entry); };
    }

    <T> void publish(String owner, Topic<T> topic, T payload) {
        Objects.requireNonNull(payload, "payload");
        boolean owns = owner.equals(APP) ? topic.id().startsWith("jasper.") : topic.id().startsWith(owner + ".");
        if (!owns) throw new IllegalArgumentException(owner + " may not publish to " + topic.id());
        checkType(topic);
        if (!topic.payloadType().isInstance(payload))
            throw new IllegalArgumentException("Payload is not a " + topic.payloadType().getName());
        enqueue(() -> deliver(topic.id(), payload));
    }

    void checkType(Topic<?> topic) {
        Class<?> known = types.putIfAbsent(topic.id(), topic.payloadType());
        if (known != null && known != topic.payloadType())
            throw new IllegalArgumentException("Topic " + topic.id() + " already carries " + known.getName());
    }

    /** Queues work behind every delivery already queued; counted until it has run. */
    void enqueue(Runnable delivery) {
        pending.incrementAndGet();
        ui.accept(() -> {
            try { delivery.run(); }
            finally { pending.decrementAndGet(); }
        });
    }

    /** UI thread: dispatches to the subscribers present now; each handler is contained. */
    void deliver(String topicId, Object payload) {
        var list = subscribers.get(topicId);
        if (list == null) return;
        for (Entry entry : list) {
            if (entry.closed().get()) continue;
            if (entry.pluginId().equals(APP)) entry.handler().accept(payload);
            else containment.run(entry.pluginId(), "handler for " + topicId, () -> entry.handler().accept(payload));
        }
    }

    void removeAll(String pluginId) {
        for (var list : subscribers.values())
            list.removeIf(entry -> { boolean mine = entry.pluginId().equals(pluginId); if (mine) entry.closed().set(true); return mine; });
    }

    int pending() { return pending.get(); }
}
```

`ActivityHub.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns activity lifecycles: one STARTED, coalesced PROGRESS, exactly one terminal event, the source
 * stamped here rather than by the plugin. Handles are safe from any thread.
 */
final class ActivityHub {
    private final EventBus bus;
    private final ConcurrentHashMap<UUID, ActivityEvent> running = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Handle> handles = new ConcurrentHashMap<>();

    ActivityHub(EventBus bus) {
        this.bus = bus;
        bus.checkType(Activities.TOPIC);
    }

    ActivityHandle begin(String pluginId, ActivitySpec spec) {
        var handle = new Handle(pluginId, Objects.requireNonNull(spec, "spec"));
        ActivityEvent started = handle.event(ActivityEvent.State.STARTED, OptionalDouble.empty(), spec.detail());
        running.put(handle.id, started);
        handles.put(handle.id, handle);
        bus.enqueue(() -> bus.deliver(Activities.TOPIC.id(), started));
        return handle;
    }

    List<ActivityEvent> current() { return List.copyOf(running.values()); }

    /** Ends every activity the plugin left open. */
    void failAll(String pluginId, String reason) {
        for (Handle handle : List.copyOf(handles.values()))
            if (handle.pluginId.equals(pluginId)) handle.fail(reason);
    }

    private final class Handle implements ActivityHandle {
        private final UUID id = UUID.randomUUID();
        private final String pluginId;
        private final ActivitySpec spec;
        private boolean ended;
        private OptionalDouble fraction = OptionalDouble.empty();
        private ActivityEvent waiting;

        Handle(String pluginId, ActivitySpec spec) { this.pluginId = pluginId; this.spec = spec; }

        ActivityEvent event(ActivityEvent.State state, OptionalDouble value, String detail) {
            return new ActivityEvent(id, pluginId, spec.title(), state, value, detail, spec.activateActionId());
        }

        @Override public UUID id() { return id; }

        @Override public void progress(double value, String detail) { report(OptionalDouble.of(value), detail); }

        @Override public void detail(String detail) {
            OptionalDouble last;
            synchronized (this) { last = fraction; }
            report(last, detail);
        }

        private void report(OptionalDouble value, String detail) {
            ActivityEvent event = event(ActivityEvent.State.PROGRESS, value, Objects.requireNonNull(detail, "detail"));
            boolean schedule;
            synchronized (this) {
                if (ended) return;
                fraction = value;
                running.put(id, event);
                schedule = waiting == null;
                waiting = event;
            }
            // One queued delivery carries whatever is latest when it runs.
            if (schedule) bus.enqueue(() -> {
                ActivityEvent latest;
                synchronized (this) { latest = waiting; waiting = null; }
                if (latest != null) bus.deliver(Activities.TOPIC.id(), latest);
            });
        }

        @Override public void succeed(String detail) { end(ActivityEvent.State.SUCCEEDED, detail); }
        @Override public void fail(String detail) { end(ActivityEvent.State.FAILED, detail); }
        @Override public void cancelled() { end(ActivityEvent.State.CANCELLED, ""); }

        private void end(ActivityEvent.State state, String detail) {
            ActivityEvent event;
            synchronized (this) {
                if (ended) return;
                ended = true;
                waiting = null;
                event = event(state, fraction, Objects.requireNonNull(detail, "detail"));
                running.remove(id);
                handles.remove(id);
            }
            bus.enqueue(() -> bus.deliver(Activities.TOPIC.id(), event));
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests '*EventBusTest'`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/plugins jasper-app/src/test/java/dev/jasper/app/plugins/EventBusTest.java
git commit -m "feat: add the queued event bus, activities and plugin containment

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 9: Plugin configuration tables and diagnostics

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/{ConfigSnapshot,ConfigLoader}.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginSettings.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/config/PluginTablesTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/PluginSettingsTest.java`, extend `jasper-app/src/test/java/dev/jasper/app/application/` with `ConfigurationReportTest.java`

**Interfaces:**
- Produces:
  - `ConfigSnapshot.plugins()` → `Map<String, Map<String, Object>>` (plugin id → immutable table; values are `String`, `Long`, `Double`, `Boolean`, `List<String>` or a nested immutable `Map<String, Object>`); `ConfigSnapshot.Builder.plugins(Map<String, Map<String, Object>>)`. The existing 15-argument constructor stays and supplies `Map.of()`.
  - `ConfigurationController.report(String key, String message)` (package-private, EDT): adds a warning shown with the file's diagnostics until the next reload.
  - `PluginSettings(String pluginId, Map<String, Object> initial, Containment containment, BiConsumer<String, String> report)` implements `PluginConfig`; `void update(Map<String, Object> next)` (UI thread; runs listeners only when the table changed); `void close()`. It reports keys as `plugins."<id>".<key>`.

- [ ] **Step 1: Write the failing tests**

`jasper-app/src/test/java/dev/jasper/app/config/PluginTablesTest.java`:

```java
package dev.jasper.app.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PluginTablesTest {
    private static ConfigLoader.Result parse(String text) { return ConfigLoader.parse(Path.of("config.toml"), text, true); }

    @Test void quotedPluginTablesAreKeptVerbatimWithoutUnknownKeyWarnings() {
        var result = parse("""
            [plugins."dev.jasper.ssh"]
            default_user = "dustin"
            port = 22
            keepalive = true
            ratio = 0.5
            hosts = ["a", "b"]

            [plugins."dev.jasper.ssh".proxy]
            host = "bastion"

            [plugins."dev.example.absent"]
            anything = "goes"
            """);
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.rejected()).isFalse();
        Map<String, Object> ssh = result.snapshot().plugins().get("dev.jasper.ssh");
        assertThat(ssh).containsEntry("default_user", "dustin").containsEntry("port", 22L)
            .containsEntry("keepalive", true).containsEntry("ratio", 0.5).containsEntry("hosts", List.of("a", "b"))
            .containsEntry("proxy", Map.of("host", "bastion"));
        assertThat(result.snapshot().plugins()).containsKey("dev.example.absent");
        assertThatThrownBy(() -> ssh.put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void malformedIdsAndUnsupportedValuesWarnAndAreIgnored() {
        var result = parse("""
            [plugins."Not An Id"]
            a = 1

            [plugins."dev.example.tool"]
            mixed = ["a", 1]
            when = 1979-05-27
            fine = "yes"
            """);
        assertThat(result.snapshot().plugins()).containsOnlyKeys("dev.example.tool");
        assertThat(result.snapshot().plugins().get("dev.example.tool")).containsOnlyKeys("fine");
        assertThat(result.diagnostics()).extracting(ConfigDiagnostic::key)
            .containsExactlyInAnyOrder("plugins.Not An Id", "plugins.dev.example.tool.mixed", "plugins.dev.example.tool.when");
    }

    @Test void defaultsAndBuilderCarryAnEmptyOrGivenPluginMap() {
        assertThat(ConfigSnapshot.defaults().plugins()).isEmpty();
        var tables = Map.of("a.b", Map.<String, Object>of("k", "v"));
        assertThat(ConfigSnapshot.builder().plugins(tables).build().plugins()).isEqualTo(tables);
        assertThat(ConfigSnapshot.builder().plugins(tables).build().toBuilder().tabHeight(40).build().plugins()).isEqualTo(tables);
    }
}
```

`jasper-app/src/test/java/dev/jasper/app/plugins/PluginSettingsTest.java`:

```java
package dev.jasper.app.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PluginSettingsTest {
    @Test void typedGettersNestedTablesChangeDetectionAndQualifiedReports() {
        List<String> reports = new ArrayList<>();
        List<String> changes = new ArrayList<>();
        var containment = new Containment(() -> true);
        var settings = new PluginSettings("dev.example.tool", Map.of("name", "x", "count", 3L, "on", true,
            "hosts", List.of("a"), "proxy", Map.of("port", 22L)), containment, (key, message) -> reports.add(key + "=" + message));
        assertThat(settings.string("name")).hasValue("x");
        assertThat(settings.string("count")).isEmpty();
        assertThat(settings.integer("count")).hasValue(3L);
        assertThat(settings.bool("on")).hasValue(true);
        assertThat(settings.stringList("hosts")).containsExactly("a");
        assertThat(settings.stringList("name")).isEmpty();
        assertThat(settings.table("proxy").orElseThrow().integer("port")).hasValue(22L);
        assertThat(settings.table("name")).isEmpty();

        var subscription = settings.onChanged(() -> changes.add("changed"));
        settings.onChanged(() -> { throw new IllegalStateException("listener failure"); });
        settings.update(Map.of("name", "x", "count", 3L, "on", true, "hosts", List.of("a"), "proxy", Map.of("port", 22L)));
        assertThat(changes).as("an equal table is not a change").isEmpty();
        settings.update(Map.of("name", "y"));
        assertThat(changes).containsExactly("changed");
        assertThat(containment.failures("dev.example.tool")).isEqualTo(1);
        assertThat(settings.string("name")).hasValue("y");
        subscription.close();
        settings.update(Map.of());
        assertThat(changes).hasSize(1);

        settings.report("name", "too short");
        new PluginSettings("dev.example.tool", Map.of("proxy", Map.of()), containment, (key, message) -> reports.add(key + "=" + message))
            .table("proxy").orElseThrow().report("port", "missing");
        assertThat(reports).containsExactly("plugins.\"dev.example.tool\".name=too short",
            "plugins.\"dev.example.tool\".proxy.port=missing");
    }
}
```

`jasper-app/src/test/java/dev/jasper/app/application/ConfigurationReportTest.java` (if `ConfigurationTestSupport` in the same directory offers a helper that builds this controller, prefer it; the assertions stay the same):

```java
package dev.jasper.app.application;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ConfigurationReportTest {
    @TempDir Path dir;

    @Test void reportedProblemsJoinTheDiagnosticsUntilTheNextState() throws Exception {
        Path file = dir.resolve("config.toml");
        Files.writeString(file, "[window]\nlines = 40\n");
        var worker = Executors.newSingleThreadScheduledExecutor();
        try (var service = new ConfigService(file, true, worker, Runnable::run)) {
            var controller = new ConfigurationController(new ThemeController(theme -> true), service, path -> { });
            assertThat(controller.shown().diagnostics()).isEmpty();
            controller.report("plugins.\"a.b\".port", "out of range");
            assertThat(controller.shown().diagnostics()).singleElement().satisfies(diagnostic -> {
                assertThat(diagnostic.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING);
                assertThat(diagnostic.key()).isEqualTo("plugins.\"a.b\".port");
                assertThat(diagnostic.message()).isEqualTo("out of range");
                assertThat(diagnostic.file()).isEqualTo(file);
            });
            controller.accept(service.initialState());
            assertThat(controller.shown().diagnostics()).as("a new state clears plugin reports").isEmpty();
            controller.close();
        } finally { worker.shutdownNow(); }
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*PluginTablesTest' --tests '*PluginSettingsTest' --tests '*ConfigurationReportTest'`
Expected: compilation FAILS (`plugins()`, `PluginSettings`, `shown()`, `report` not found).

- [ ] **Step 3: Extend `ConfigSnapshot`**

Add `Map<String, Map<String, Object>> plugins` as the last record component:

```java
public record ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusBar,
                      FontConfig font, Appearance variant, Map<String, String> keybindings,
                      int columns, int lines, TerminalConfig terminal, boolean buddyEnabled,
                      boolean historyEnabled, int maxResults, List<String> trivialCommands,
                      int longCommandSeconds, boolean backgroundEnabled,
                      Map<String, Map<String, Object>> plugins) {
```

In the compact constructor, after `trivialCommands = List.copyOf(trivialCommands);`:

```java
        // Plugin tables arrive deeply immutable from the loader; only the outer map is copied here.
        plugins = Map.copyOf(plugins);
```

Add, directly after the compact constructor, the constructor every existing caller keeps using:

```java
    /** Every constructor that predates plugin tables: no plugin settings. */
    public ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusBar,
                   FontConfig font, Appearance variant, Map<String, String> keybindings,
                   int columns, int lines, TerminalConfig terminal, boolean buddyEnabled,
                   boolean historyEnabled, int maxResults, List<String> trivialCommands,
                   int longCommandSeconds, boolean backgroundEnabled) {
        this(tabHeight, toolbar, statusBar, font, variant, keybindings, columns, lines, terminal, buddyEnabled,
            historyEnabled, maxResults, trivialCommands, longCommandSeconds, backgroundEnabled, Map.of());
    }
```

In `Builder`: add the field `private Map<String, Map<String, Object>> plugins;`, the copy `plugins = source.plugins();` in the constructor, the setter

```java
        public Builder plugins(Map<String, Map<String, Object>> value) { plugins = Map.copyOf(value); return this; }
```

and pass `plugins` as the sixteenth argument in `build()`.

- [ ] **Step 4: Read plugin tables in `ConfigLoader`**

Add `"plugins"` to the root entry of `FIELDS`:

```java
        Map.entry(List.of(), Set.of("window", "font", "ui", "keybindings", "terminal", "buddy", "palette",
            "notifications", "background", "plugins")),
```

Add the field beside `keybindings`:

```java
    private Map<String, Map<String, Object>> plugins = Map.of();
    private static final java.util.regex.Pattern PLUGIN_ID = java.util.regex.Pattern.compile("[a-z][a-z0-9_.-]{0,127}");
```

In `readTable`, extend the dispatch (the new lines are the `pluginTables` ones):

```java
            boolean bindings = path.equals(List.of("keybindings"));
            boolean environment = path.equals(List.of("terminal", "env"));
            boolean pluginTables = path.equals(List.of("plugins"));
            if (FIELDS.containsKey(path) || bindings || environment || pluginTables) {
                if (!(value instanceof TomlTable nested)) typeError(path, "a table");
                else if (bindings) readBindings(nested);
                else if (environment) readEnvironment(nested);
                else if (pluginTables) readPlugins(nested);
                else readTable(path, nested);
            } else {
```

Add the two methods (import `org.tomlj.TomlArray`, `java.util.LinkedHashMap`, `java.util.Collections`):

```java
    /** Plugin tables are opaque to the application: no schema, so no unknown-key warnings inside them. */
    private void readPlugins(TomlTable table) {
        var staged = new LinkedHashMap<String, Map<String, Object>>();
        for (String id : table.keySet()) {
            List<String> path = List.of("plugins", id);
            Object value = table.get(List.of(id));
            if (!PLUGIN_ID.matcher(id).matches()) { warning(path, "Not a plugin id; ignored."); continue; }
            if (!(value instanceof TomlTable nested)) { typeError(path, "a table"); continue; }
            staged.put(id, freeze(path, nested));
        }
        plugins = Map.copyOf(staged);
    }

    private Map<String, Object> freeze(List<String> parent, TomlTable table) {
        var result = new LinkedHashMap<String, Object>();
        for (String key : table.keySet()) {
            var path = new ArrayList<>(parent);
            path.add(key);
            Object value = table.get(List.of(key));
            switch (value) {
                case String text -> result.put(key, text);
                case Long number -> result.put(key, number);
                case Double number -> result.put(key, number);
                case Boolean flag -> result.put(key, flag);
                case TomlTable nested -> result.put(key, freeze(path, nested));
                case TomlArray array -> {
                    List<String> items = new ArrayList<>();
                    for (int i = 0; i < array.size(); i++) if (array.get(i) instanceof String text) items.add(text);
                    if (items.size() == array.size()) result.put(key, List.copyOf(items));
                    else warning(path, "Only arrays of strings are supported in plugin settings; ignored.");
                }
                default -> warning(path, "Unsupported value type in plugin settings; ignored.");
            }
        }
        return Collections.unmodifiableMap(result);
    }
```

In `parse()`, pass `plugins` as the final argument of `new ConfigSnapshot(...)`.

If `warning` renders the diagnostic key differently from `String.join(".", path)`, adjust the expected keys in `PluginTablesTest` to what `warning` produces for an ordinary nested key; do not change `warning`.

- [ ] **Step 5: Add `report` and `shown` to `ConfigurationController`**

Add the field and methods, and route every `owner.setConfigurationState(state)` through `shown()`:

```java
    private final List<dev.jasper.app.config.ConfigDiagnostic> reported = new java.util.ArrayList<>();

    /** The saved state plus problems plugins reported about their own tables since the last reload. */
    ConfigService.State shown() {
        requireEdt();
        if (reported.isEmpty()) return state;
        var merged = new java.util.ArrayList<>(state.diagnostics());
        merged.addAll(reported);
        return new ConfigService.State(state.snapshot(), merged, state.file(), state.present());
    }

    /** A plugin's complaint about one of its settings; shown with the file's diagnostics until the next reload. */
    void report(String key, String message) {
        requireEdt();
        if (closed) return;
        reported.add(new dev.jasper.app.config.ConfigDiagnostic(dev.jasper.app.config.ConfigDiagnostic.Severity.WARNING,
            state.file(), 0, 0, key, message));
        for (WindowContent owner : List.copyOf(owners)) owner.setConfigurationState(shown());
    }
```

In `register`, replace `owner.setConfigurationState(state);` with `owner.setConfigurationState(shown());`. In `accept`, clear reports before listeners run (plugins re-report from their `onChanged` handlers) and show the merged state:

```java
        state = next;
        reported.clear();
        applicationListener.accept(next.snapshot());
        for (WindowContent owner : List.copyOf(owners)) {
            owner.applyConfiguration(next.snapshot(), service.macOs());
            owner.setConfigurationState(shown());
        }
```

- [ ] **Step 6: Write `PluginSettings`**

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.plugin.PluginConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** One plugin's {@code [plugins."<id>"]} table. Getters are safe from any thread; {@link #update} is UI-thread only. */
final class PluginSettings implements PluginConfig {
    private final String pluginId;
    private final Containment containment;
    private final BiConsumer<String, String> report;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final View root;
    private volatile Map<String, Object> values;

    PluginSettings(String pluginId, Map<String, Object> initial, Containment containment, BiConsumer<String, String> report) {
        this.pluginId = pluginId; this.containment = containment; this.report = report;
        this.values = Map.copyOf(initial);
        this.root = new View(() -> values, "");
    }

    void update(Map<String, Object> next) {
        if (next.equals(values)) return;
        values = Map.copyOf(next);
        for (Runnable listener : listeners) containment.run(pluginId, "configuration change", listener);
    }

    void close() { listeners.clear(); }

    @Override public Optional<String> string(String key) { return root.string(key); }
    @Override public OptionalLong integer(String key) { return root.integer(key); }
    @Override public Optional<Boolean> bool(String key) { return root.bool(key); }
    @Override public List<String> stringList(String key) { return root.stringList(key); }
    @Override public Optional<PluginConfig> table(String key) { return root.table(key); }
    @Override public Subscription onChanged(Runnable handler) { return root.onChanged(handler); }
    @Override public void report(String key, String message) { root.report(key, message); }

    private final class View implements PluginConfig {
        private final Supplier<Map<String, Object>> table;
        private final String prefix;

        View(Supplier<Map<String, Object>> table, String prefix) { this.table = table; this.prefix = prefix; }

        @Override public Optional<String> string(String key) {
            return table.get().get(key) instanceof String text ? Optional.of(text) : Optional.empty();
        }
        @Override public OptionalLong integer(String key) {
            return table.get().get(key) instanceof Long number ? OptionalLong.of(number) : OptionalLong.empty();
        }
        @Override public Optional<Boolean> bool(String key) {
            return table.get().get(key) instanceof Boolean flag ? Optional.of(flag) : Optional.empty();
        }
        @Override public List<String> stringList(String key) {
            if (!(table.get().get(key) instanceof List<?> list)) return List.of();
            for (Object item : list) if (!(item instanceof String)) return List.of();
            return list.stream().map(String.class::cast).toList();
        }
        @Override public Optional<PluginConfig> table(String key) {
            if (!(table.get().get(key) instanceof Map<?, ?>)) return Optional.empty();
            return Optional.of(new View(() -> nested(key), prefix + key + "."));
        }
        @SuppressWarnings("unchecked")
        private Map<String, Object> nested(String key) {
            return table.get().get(key) instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        }
        @Override public Subscription onChanged(Runnable handler) {
            listeners.add(java.util.Objects.requireNonNull(handler, "handler"));
            return () -> listeners.remove(handler);
        }
        @Override public void report(String key, String message) {
            report.accept("plugins.\"" + pluginId + "\"." + prefix + key, message);
        }
    }
}
```

`Map.copyOf` rejects null values and keeps nested immutable maps as they are, which is what the loader produces.

- [ ] **Step 7: Run the tests, then the whole config and application suites**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.application.*' --tests '*PluginSettingsTest'`
Expected: PASS, including every pre-existing `ConfigLoaderTest`, `ConfigTemplateTest` and `ExpandedConfigTest` case (the 15-argument constructor keeps them compiling).

- [ ] **Step 8: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/config jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java jasper-app/src/main/java/dev/jasper/app/plugins/PluginSettings.java jasper-app/src/test/java/dev/jasper/app
git commit -m "feat: carry plugin configuration tables and plugin-reported diagnostics

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 10: Service registry, plugin lifetimes and the contract suite against the application

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/{ServiceRegistry,HostedPlugin,HostedContext,PluginHost}.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/{AppContractTest,PluginHostTest}.java`

**Interfaces:**
- Consumes: `Containment`, `EventBus`, `ActivityHub`, `PluginSettings`, `PluginStatus.State`, the SDK API, `PluginContractTest`/`ContractHarness`.
- Produces:
  - `record HostedPlugin(PluginInfo info, Set<String> requires, Set<String> optional, Set<String> exports, ClassLoader loader, Callable<Plugin> instantiate)`
  - `PluginHost(PluginHost.Environment environment)`; `record PluginHost.Environment(Consumer<Runnable> ui, BooleanSupplier onUi, Function<String, Path> dataDirectory, Function<String, Map<String, Object>> settings, BiConsumer<String, String> configReport, Duration drainGrace)`
  - `PluginHost`: `Outcome start(HostedPlugin hosted)` (UI thread); `record Outcome(PluginStatus.State state, String reason)`; `boolean active(String pluginId)`; `void settingsChanged()` (UI thread; re-reads every active plugin's table through `Environment.settings`); `List<CompletableFuture<?>> stop()` (UI thread, reverse start order); `String executing()`; `int failures(String pluginId)`; fields `final EventBus bus`, `final ActivityHub activities`
  - `ServiceRegistry`: `void stage(HostedPlugin owner, Class<?> api, Function<PluginInfo, ?> factory)`, `void commit(String pluginId)`, `void discard(String pluginId)`, `void prepare(PluginInfo consumer, Set<String> providerIds, Containment containment)`, `<T> Optional<T> find(String consumerId, Class<T> api)`

- [ ] **Step 1: Write the failing tests**

`AppContractTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.testing.contract.ContractHarness;
import dev.jasper.sdk.testing.contract.PluginContractTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/** The application's runtime must pass the same contract as the testkit fake, on the real EDT. */
class AppContractTest extends PluginContractTest {
    static void onEdt(Runnable action) {
        try { SwingUtilities.invokeAndWait(action); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Error error) throw error;
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(failure.getCause());
        }
    }

    static PluginHost host(Path data, Map<String, Map<String, Object>> tables, Duration drainGrace) {
        var created = new AtomicReference<PluginHost>();
        onEdt(() -> created.set(new PluginHost(new PluginHost.Environment(SwingUtilities::invokeLater,
            SwingUtilities::isEventDispatchThread, data::resolve, id -> tables.getOrDefault(id, Map.of()),
            (key, message) -> { }, drainGrace))));
        return created.get();
    }

    @Override protected ContractHarness newHarness() {
        Path data;
        try { data = Files.createTempDirectory("jasper-contract"); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
        PluginHost host = host(data, Map.of(), Duration.ofMillis(200));
        List<ActivityEvent> log = new CopyOnWriteArrayList<>();
        onEdt(() -> host.bus.subscribe(EventBus.APP, Activities.TOPIC, log::add));
        return new ContractHarness() {
            @Override public void start(PluginInfo info, Set<String> requires, Set<String> optional, Plugin plugin) {
                onEdt(() -> host.start(new HostedPlugin(info, requires, optional,
                    Set.of(PluginContractTest.class.getPackageName()), PluginContractTest.class.getClassLoader(), () -> plugin)));
            }
            @Override public boolean active(String pluginId) { return host.active(pluginId); }
            @Override public void ui(Runnable action) { onEdt(action); }
            @Override public void flush() {
                do { onEdt(() -> { }); } while (host.bus.pending() > 0);
            }
            @Override public <T> void publishApp(Topic<T> topic, T payload) { host.bus.publish(EventBus.APP, topic, payload); }
            @Override public List<ActivityEvent> activityLog() { return List.copyOf(log); }
            @Override public void stopAll() {
                var pending = new AtomicReference<List<CompletableFuture<?>>>(List.of());
                onEdt(() -> pending.set(host.stop()));
                CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).join();
            }
            @Override public void close() { stopAll(); flush(); }
        };
    }
}
```

`PluginHostTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.contract.PluginContractTest;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static org.assertj.core.api.Assertions.*;

class PluginHostTest {
    @TempDir Path data;

    private static HostedPlugin hosted(String id, Set<String> exports, Plugin plugin) {
        return new HostedPlugin(new PluginInfo(id, id, "1.0.0", Set.of()), Set.of(), Set.of(), exports,
            PluginHostTest.class.getClassLoader(), () -> plugin);
    }

    @Test void registrationCallsAreRejectedOffTheUiThread() {
        PluginHost host = AppContractTest.host(data, Map.of(), Duration.ofMillis(200));
        var context = new AtomicReference<PluginContext>();
        onEdt(() -> host.start(hosted("test.alpha", Set.of(), context::set)));
        assertThatIllegalStateException().isThrownBy(() ->
            context.get().events().subscribe(dev.jasper.sdk.events.AppEvents.CONFIG_RELOADED, event -> { }))
            .withMessageContaining("UI thread");
        assertThatIllegalStateException().as("lifetimes belong to the UI thread too")
            .isThrownBy(() -> host.start(hosted("test.beta", Set.of(), started -> { })));
    }

    @Test void reportsStartOutcomesWithReasons() {
        PluginHost host = AppContractTest.host(data, Map.of(), Duration.ofMillis(200));
        var outcomes = new ConcurrentHashMap<String, PluginHost.Outcome>();
        onEdt(() -> {
            outcomes.put("ok", host.start(hosted("test.ok", Set.of(), context -> assertThat(host.executing()).contains("test.ok", "start"))));
            outcomes.put("boom", host.start(hosted("test.boom", Set.of(), context -> { throw new java.io.IOException("disk"); })));
            outcomes.put("entry", host.start(new HostedPlugin(new PluginInfo("test.entry", "e", "1.0.0", Set.of()), Set.of(), Set.of(),
                Set.of(), PluginHostTest.class.getClassLoader(), () -> { throw new NoClassDefFoundError("gone/Missing"); })));
            outcomes.put("needs", host.start(new HostedPlugin(new PluginInfo("test.needs", "n", "1.0.0", Set.of()), Set.of("test.boom"),
                Set.of(), Set.of(), PluginHostTest.class.getClassLoader(), () -> context -> { })));
            outcomes.put("unexported", host.start(hosted("test.unexported", Set.of(),
                context -> context.services().publish(PluginContractTest.Greeter.class, name -> name))));
        });
        assertThat(outcomes.get("ok")).isEqualTo(new PluginHost.Outcome(PluginStatus.State.ACTIVE, ""));
        assertThat(outcomes.get("boom").state()).isEqualTo(PluginStatus.State.FAILED);
        assertThat(outcomes.get("boom").reason()).contains("IOException", "disk");
        assertThat(outcomes.get("entry").reason()).contains("NoClassDefFoundError");
        assertThat(outcomes.get("needs")).isEqualTo(new PluginHost.Outcome(PluginStatus.State.SKIPPED,
            "requires test.boom, which failed to start"));
        assertThat(outcomes.get("unexported").reason()).contains("exported");
        assertThat(host.failures("test.boom")).isEqualTo(1);
        assertThat(host.executing()).isNull();
    }

    @Test void backgroundWorkIsContainedDrainedAndInterruptedAfterTheGrace() throws Exception {
        PluginHost host = AppContractTest.host(data, Map.of(), Duration.ofMillis(150));
        var interrupted = new AtomicBoolean();
        var running = new CountDownLatch(1);
        var quick = new CountDownLatch(1);
        onEdt(() -> host.start(hosted("test.worker", Set.of(), context -> {
            context.background().execute(() -> { throw new IllegalStateException("contained"); });
            context.background().execute(quick::countDown);
            context.background().execute(() -> {
                running.countDown();
                try { Thread.sleep(60_000); } catch (InterruptedException stop) { interrupted.set(true); }
            });
        })));
        assertThat(quick.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
        var pending = new AtomicReference<List<CompletableFuture<?>>>();
        onEdt(() -> pending.set(host.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        assertThat(interrupted).isTrue();
        assertThat(host.failures("test.worker")).isEqualTo(1);
    }

    @Test void stopRunsInReverseOrderAndSettingsChangesReachOnlyChangedPlugins() {
        var tables = new ConcurrentHashMap<String, Map<String, Object>>();
        tables.put("test.first", Map.<String, Object>of("k", "v"));
        PluginHost host = AppContractTest.host(data, tables, Duration.ofMillis(200));
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        for (String id : List.of("test.first", "test.second")) {
            onEdt(() -> host.start(hosted(id, Set.of(), new Plugin() {
                @Override public void start(PluginContext context) {
                    assertThat(context.dataDirectory()).isDirectory().hasFileName(id);
                    context.config().onChanged(() -> order.add("changed:" + id + ":" + context.config().string("k").orElse("")));
                }
                @Override public void stop() { order.add("stop:" + id); }
            })));
        }
        tables.put("test.first", Map.<String, Object>of("k", "w"));
        onEdt(host::settingsChanged);
        onEdt(host::stop);
        assertThat(order).containsExactly("changed:test.first:w", "stop:test.second", "stop:test.first");
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*AppContractTest' --tests '*PluginHostTest'`
Expected: compilation FAILS, `PluginHost` not found.

- [ ] **Step 3: Implement the registry**

`HostedPlugin.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * What the host needs to run one plugin, independent of how it was loaded: production passes a
 * {@link PluginClassLoader} and reflective instantiation, tests pass an in-memory plugin.
 * {@code requires} are hard dependencies; services flow from {@code requires} and {@code optional}.
 */
record HostedPlugin(PluginInfo info, Set<String> requires, Set<String> optional, Set<String> exports,
                    ClassLoader loader, Callable<Plugin> instantiate) {
    HostedPlugin {
        requires = Set.copyOf(requires);
        optional = Set.copyOf(optional);
        exports = Set.copyOf(exports);
    }
}
```

`ServiceRegistry.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.PluginInfo;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Publications are staged during a provider's start and committed only when it succeeds. Factories
 * run on the UI thread in {@link #prepare}, just before a consumer starts; lookups are cache reads.
 * Staging, commit, discard and prepare are UI-thread only; {@link #find} is safe from any thread.
 */
final class ServiceRegistry {
    private record Provider(String pluginId, Function<PluginInfo, ?> factory) { }

    private final Map<Class<?>, Provider> committed = new LinkedHashMap<>();
    private final Map<String, Map<Class<?>, Provider>> staged = new LinkedHashMap<>();
    private final ConcurrentHashMap<String, Map<Class<?>, Object>> resolved = new ConcurrentHashMap<>();

    void stage(HostedPlugin owner, Class<?> api, Function<PluginInfo, ?> factory) {
        Objects.requireNonNull(factory, "factory");
        String id = owner.info().id();
        if (!api.isInterface()) throw new IllegalArgumentException("A service API must be an interface: " + api.getName());
        if (api.getClassLoader() != owner.loader() || !owner.exports().contains(api.getPackageName()))
            throw new IllegalArgumentException(api.getName() + " is not from one of " + id + "'s exported packages");
        var mine = staged.computeIfAbsent(id, key -> new LinkedHashMap<>());
        if (committed.containsKey(api) || mine.containsKey(api))
            throw new IllegalArgumentException("Service already published: " + api.getName());
        mine.put(api, new Provider(id, factory));
    }

    void commit(String pluginId) {
        Map<Class<?>, Provider> mine = staged.remove(pluginId);
        if (mine != null) committed.putAll(mine);
    }

    void discard(String pluginId) { staged.remove(pluginId); }

    void prepare(PluginInfo consumer, Set<String> providerIds, Containment containment) {
        Map<Class<?>, Object> mine = new ConcurrentHashMap<>();
        for (var service : committed.entrySet()) {
            Provider provider = service.getValue();
            if (!providerIds.contains(provider.pluginId())) continue;
            containment.attempt(provider.pluginId(), "service factory for " + service.getKey().getName(), () -> {
                Object instance = provider.factory().apply(consumer);
                if (instance != null) mine.put(service.getKey(), service.getKey().cast(instance));
                return null;
            });
        }
        resolved.put(consumer.id(), mine);
    }

    <T> Optional<T> find(String consumerId, Class<T> api) {
        Map<Class<?>, Object> mine = resolved.get(consumerId);
        return mine == null ? Optional.empty() : Optional.ofNullable(api.cast(mine.get(api)));
    }
}
```

- [ ] **Step 4: Implement the context**

`HostedContext.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import dev.jasper.sdk.events.Events;
import dev.jasper.sdk.events.Topic;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginConfig;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.services.ServiceUnavailableException;
import dev.jasper.sdk.services.Services;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/** One plugin's view of the host. Closed after a failed start and after stop: nothing can be contributed through it. */
final class HostedContext implements PluginContext {
    enum State { STARTING, ACTIVE, STOPPING, CLOSED }

    private final PluginHost host;
    final HostedPlugin hosted;
    final PluginSettings settings;
    private final String id;
    private final List<Subscription> owned = new ArrayList<>();
    private final ExecutorService executor;
    volatile State state = State.STARTING;
    Plugin plugin;

    HostedContext(PluginHost host, HostedPlugin hosted, PluginSettings settings) {
        this.host = host; this.hosted = hosted; this.settings = settings;
        this.id = hosted.info().id();
        this.executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("jasper-plugin-" + id + "-", 0).factory());
    }

    private void requireOpen() {
        if (state == State.CLOSED) throw new IllegalStateException("Plugin context is closed: " + id);
    }

    private void requireUi(String what) {
        if (!host.environment.onUi().getAsBoolean())
            throw new IllegalStateException(what + " must be called on the UI thread: " + id);
    }

    /** Closes everything the context handed out. Interrupting is for a failed start; shutdown drains first. */
    void teardown(boolean interrupt, String reason) {
        state = State.CLOSED;
        List<Subscription> copy;
        synchronized (owned) { copy = new ArrayList<>(owned); owned.clear(); }
        for (int i = copy.size() - 1; i >= 0; i--) copy.get(i).close();
        host.bus.removeAll(id);
        host.activities.failAll(id, reason);
        settings.close();
        if (interrupt) executor.shutdownNow(); else executor.shutdown();
    }

    /** Completes when accepted background work has finished, interrupting it once the grace elapses. */
    CompletableFuture<Void> drain(Duration grace) {
        var done = new CompletableFuture<Void>();
        Thread.ofPlatform().daemon().name("jasper-plugin-drain-" + id).start(() -> {
            try {
                if (!executor.awaitTermination(grace.toMillis(), TimeUnit.MILLISECONDS)) executor.shutdownNow();
            } catch (InterruptedException interrupted) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            done.complete(null);
        });
        return done;
    }

    @Override public PluginInfo plugin() { return hosted.info(); }
    @Override public System.Logger log() { return Containment.logger(id); }

    @Override public Path dataDirectory() {
        try { return Files.createDirectories(host.environment.dataDirectory().apply(id)); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    @Override public PluginConfig config() { return settings; }

    @Override public Executor background() {
        return task -> {
            Objects.requireNonNull(task, "task");
            if (state == State.CLOSED) throw new RejectedExecutionException("Plugin context is closed: " + id);
            executor.execute(() -> host.containment.run(id, "background task", task));
        };
    }

    @Override public Events events() {
        return new Events() {
            @Override public <T> Subscription subscribe(Topic<T> topic, Consumer<? super T> handler) {
                requireOpen();
                requireUi("subscribe");
                Subscription subscription = host.bus.subscribe(id, topic, handler);
                synchronized (owned) { owned.add(subscription); }
                return subscription;
            }
            @Override public <T> void publish(Topic<T> topic, T payload) {
                requireOpen();
                host.bus.publish(id, topic, payload);
            }
        };
    }

    @Override public Activities activities() {
        return new Activities() {
            @Override public ActivityHandle begin(ActivitySpec spec) {
                requireOpen();
                return host.activities.begin(id, spec);
            }
            @Override public List<ActivityEvent> current() { return host.activities.current(); }
        };
    }

    @Override public Services services() {
        return new Services() {
            @Override public <T> void publish(Class<T> api, T implementation) {
                Objects.requireNonNull(implementation, "implementation");
                publishPerConsumer(api, consumer -> implementation);
            }
            @Override public <T> void publishPerConsumer(Class<T> api, Function<PluginInfo, T> perConsumer) {
                requireUi("publish");
                if (state != State.STARTING)
                    throw new IllegalStateException("Services may be published only during start(): " + id);
                host.services.stage(hosted, api, perConsumer);
            }
            @Override public <T> T require(Class<T> api) {
                return find(api).orElseThrow(() -> new ServiceUnavailableException(api));
            }
            @Override public <T> Optional<T> find(Class<T> api) { return host.services.find(id, api); }
        };
    }
}
```

- [ ] **Step 5: Implement the host**

`PluginHost.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.plugin.Plugin;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Plugin lifetimes on the UI thread: start in the order given, roll back a failed start completely,
 * stop in reverse. It knows nothing about jars or descriptors; see {@link HostedPlugin}.
 */
final class PluginHost {
    record Environment(Consumer<Runnable> ui, BooleanSupplier onUi, Function<String, Path> dataDirectory,
                       Function<String, Map<String, Object>> settings, BiConsumer<String, String> configReport,
                       Duration drainGrace) { }

    record Outcome(PluginStatus.State state, String reason) { }

    final Environment environment;
    final Containment containment;
    final EventBus bus;
    final ActivityHub activities;
    final ServiceRegistry services = new ServiceRegistry();
    private final Map<String, HostedContext> contexts = new LinkedHashMap<>();
    private final Map<String, PluginStatus.State> notRunning = new HashMap<>();

    PluginHost(Environment environment) {
        this.environment = Objects.requireNonNull(environment);
        this.containment = new Containment(environment.onUi());
        this.bus = new EventBus(environment.ui(), containment);
        this.activities = new ActivityHub(bus);
    }

    private void requireUi() {
        if (!environment.onUi().getAsBoolean()) throw new IllegalStateException("Plugin lifetimes belong to the UI thread");
    }

    Outcome start(HostedPlugin hosted) {
        requireUi();
        String id = hosted.info().id();
        if (contexts.containsKey(id)) throw new IllegalArgumentException("Already started: " + id);
        for (String required : hosted.requires()) {
            if (active(required)) continue;
            notRunning.put(id, PluginStatus.State.SKIPPED);
            PluginStatus.State why = notRunning.get(required);
            return new Outcome(PluginStatus.State.SKIPPED, "requires " + required + ", which "
                + (why == PluginStatus.State.FAILED ? "failed to start" : why == PluginStatus.State.SKIPPED ? "was skipped" : "is not running"));
        }
        var context = new HostedContext(this, hosted, new PluginSettings(id, environment.settings().apply(id),
            containment, environment.configReport()));
        contexts.put(id, context);
        Set<String> providers = new HashSet<>(hosted.requires());
        providers.addAll(hosted.optional());
        services.prepare(hosted.info(), providers, containment);
        Throwable failure = containment.attempt(id, "start", () -> {
            Plugin plugin = hosted.instantiate().call();
            context.plugin = plugin;
            plugin.start(context);
            return null;
        });
        if (failure != null) {
            // Everything the context handed out goes, not only registrations; the publication was never visible.
            context.teardown(true, "Plugin failed to start");
            services.discard(id);
            notRunning.put(id, PluginStatus.State.FAILED);
            return new Outcome(PluginStatus.State.FAILED, failure.toString());
        }
        services.commit(id);
        context.state = HostedContext.State.ACTIVE;
        return new Outcome(PluginStatus.State.ACTIVE, "");
    }

    boolean active(String pluginId) {
        HostedContext context = contexts.get(pluginId);
        return context != null && context.state == HostedContext.State.ACTIVE;
    }

    void settingsChanged() {
        requireUi();
        for (HostedContext context : List.copyOf(contexts.values()))
            if (context.state == HostedContext.State.ACTIVE)
                context.settings.update(environment.settings().apply(context.hosted.info().id()));
    }

    /** Reverse start order. The returned futures complete when each plugin's background work has drained. */
    List<CompletableFuture<?>> stop() {
        requireUi();
        List<CompletableFuture<?>> pending = new ArrayList<>();
        List<HostedContext> order = new ArrayList<>(contexts.values());
        for (int i = order.size() - 1; i >= 0; i--) {
            HostedContext context = order.get(i);
            if (context.state != HostedContext.State.ACTIVE) continue;
            context.state = HostedContext.State.STOPPING;
            String id = context.hosted.info().id();
            containment.run(id, "stop", context.plugin::stop);
            context.teardown(false, "Plugin stopped");
            pending.add(context.drain(environment.drainGrace()));
        }
        return pending;
    }

    String executing() { return containment.executing(); }

    int failures(String pluginId) { return containment.failures(pluginId); }
}
```

A `NoClassDefFoundError` from `instantiate` is a `LinkageError`, so `attempt` contains it; `AssertionError` propagates, which is what lets assertions inside test plugins fail their tests.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests '*AppContractTest' --tests '*PluginHostTest'`
Expected: PASS: 9 inherited contract tests and 4 host tests. If a contract test fails here but passes for the fake (or the reverse), the two implementations disagree: fix the implementation, never the contract, unless the spec says the contract is wrong.

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/plugins jasper-app/src/test/java/dev/jasper/app/plugins
git commit -m "feat: host plugin lifetimes with rollback and commit-on-success services

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 11: Standalone launch flags

`--safe-mode`, `--plugin-dir <path>` and `--standalone` follow the `--config` precedent: never hand off, never bind the endpoint, never resident.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/{AppArguments,ApplicationBootstrap}.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/bootstrap/{AppArgumentsTest,ApplicationBootstrapTest}.java`

**Interfaces:**
- Produces: `record AppArguments(Path configOverride, boolean help, boolean background, boolean safeMode, Path pluginDir, boolean standalone)`; the existing two- and three-argument constructors remain; `boolean standaloneLaunch()`.

- [ ] **Step 1: Write the failing tests**

Append to `AppArgumentsTest`:

```java
    @Test void pluginFlagsParseOnceAndMakeTheLaunchStandalone() {
        AppArguments plain = AppArguments.parse(new String[0], cwd);
        assertThat(plain.safeMode()).isFalse();
        assertThat(plain.pluginDir()).isNull();
        assertThat(plain.standalone()).isFalse();
        assertThat(plain.standaloneLaunch()).isFalse();

        assertThat(AppArguments.parse(new String[]{"--safe-mode"}, cwd).standaloneLaunch()).isTrue();
        assertThat(AppArguments.parse(new String[]{"--standalone"}, cwd).standaloneLaunch()).isTrue();
        assertThat(AppArguments.parse(new String[]{"--config", "c.toml"}, cwd).standaloneLaunch()).isTrue();
        AppArguments dev = AppArguments.parse(new String[]{"--plugin-dir", "build/../out", "--safe-mode"}, cwd);
        assertThat(dev.pluginDir()).isEqualTo(cwd.resolve("out"));
        assertThat(dev.safeMode()).isTrue();
        assertThat(dev.standaloneLaunch()).isTrue();

        for (String[] args : new String[][]{{"--safe-mode", "--safe-mode"}, {"--standalone", "--standalone"},
                {"--plugin-dir"}, {"--plugin-dir", "--help"}, {"--plugin-dir", ""}, {"--plugin-dir", "a", "--plugin-dir", "b"}}) {
            assertThatIllegalArgumentException().as(java.util.Arrays.toString(args))
                .isThrownBy(() -> AppArguments.parse(args, cwd)).withMessageContaining("Usage:");
        }
        assertThat(AppArguments.USAGE).contains("--safe-mode", "--plugin-dir <path>", "--standalone");
    }
```

In `ApplicationBootstrapTest.neitherAnotherConfigurationNorAResidentProcessHandsOff`, after the `--background` assertion and still inside the `try`, add:

```java
            // Recovery and development launches must never be swallowed by the process they are
            // meant to get away from.
            assertThat(ApplicationBootstrap.handsOff(new AppArguments(null, false, false, true, null, false), dirs)).isFalse();
            assertThat(ApplicationBootstrap.handsOff(new AppArguments(null, false, false, false, dir.resolve("p"), false), dirs)).isFalse();
            assertThat(ApplicationBootstrap.handsOff(new AppArguments(null, false, false, false, null, true), dirs)).isFalse();
```

Append to `residentRoleIsOnlyTrueWithTheSettingOnAndNoConfigOverride`:

```java
        assertThat(ApplicationBootstrap.residentRole(new AppArguments(null, false, false, true, null, false), true)).isFalse();
        assertThat(ApplicationBootstrap.residentRole(new AppArguments(null, false, false, false, Path.of("p"), false), true)).isFalse();
        assertThat(ApplicationBootstrap.residentRole(new AppArguments(null, false, false, false, null, true), true)).isFalse();
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.bootstrap.*'`
Expected: compilation FAILS, no six-argument `AppArguments`.

- [ ] **Step 3: Implement `AppArguments`**

Replace the record header, usage, compatibility constructors and parser:

```java
/** Startup options parsed before any desktop initialization. */
record AppArguments(Path configOverride, boolean help, boolean background, boolean safeMode, Path pluginDir,
                    boolean standalone) {
    static final String USAGE = "Usage: jasper [--config <path>] [--background] [--safe-mode] "
        + "[--plugin-dir <path>] [--standalone] [--help]";

    /** The form that predates residency: an ordinary foreground launch. */
    AppArguments(Path configOverride, boolean help) {
        this(configOverride, help, false);
    }

    /** The form that predates plugins: every plugin option off. */
    AppArguments(Path configOverride, boolean help, boolean background) {
        this(configOverride, help, background, false, null, false);
    }

    /**
     * A standalone launch never hands off to a resident process, never binds the shared endpoint and
     * is never resident: it is a different Jasper (another config, another plugin set) or a recovery
     * launch that must not reopen the process it is escaping.
     */
    boolean standaloneLaunch() {
        return configOverride != null || safeMode || pluginDir != null || standalone;
    }

    static AppArguments parse(String[] args, Path workingDirectory) {
        Path override = null;
        Path pluginDir = null;
        boolean help = false, background = false, safeMode = false, standalone = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help" -> {
                    if (help) throw invalid("Duplicate --help option.");
                    help = true;
                }
                case "--background" -> {
                    if (background) throw invalid("Duplicate --background option.");
                    background = true;
                }
                case "--safe-mode" -> {
                    if (safeMode) throw invalid("Duplicate --safe-mode option.");
                    safeMode = true;
                }
                case "--standalone" -> {
                    if (standalone) throw invalid("Duplicate --standalone option.");
                    standalone = true;
                }
                case "--config" -> {
                    if (override != null) throw invalid("Duplicate --config option.");
                    override = path(args, ++i, "--config", "a file path", workingDirectory);
                }
                case "--plugin-dir" -> {
                    if (pluginDir != null) throw invalid("Duplicate --plugin-dir option.");
                    pluginDir = path(args, ++i, "--plugin-dir", "a directory path", workingDirectory);
                }
                default -> throw invalid("Unknown argument.");
            }
        }
        return new AppArguments(override, help, background, safeMode, pluginDir, standalone);
    }

    private static Path path(String[] args, int index, String option, String what, Path workingDirectory) {
        if (index >= args.length || args[index].isBlank() || args[index].startsWith("--"))
            throw invalid(option + " requires " + what + ".");
        try { return workingDirectory.resolve(Path.of(args[index])).toAbsolutePath().normalize(); }
        catch (InvalidPathException ignored) { throw invalid(option + " requires a valid path."); }
    }
```

Keep the existing `invalid` helper. The existing test expects `--config requires a file path.`-style failures only through `Usage:`, which still holds.

- [ ] **Step 4: Use `standaloneLaunch()` in `ApplicationBootstrap`**

Three replacements:

```java
    static boolean handsOff(AppArguments options, AppDirs dirs) {
        if (options.background() || options.standaloneLaunch()) return false;
```

```java
    static boolean residentRole(AppArguments options, boolean enabled) {
        return enabled && !options.standaloneLaunch();
    }
```

and in `startDesktop`:

```java
            boolean standalone = options.standaloneLaunch();
```

with the warning reworded, because `--config` is no longer the only cause:

```java
                        LOG.log(System.Logger.Level.WARNING, standalone
                            ? "Started with --background, but a --config, --safe-mode, --plugin-dir or --standalone launch is always standalone and cannot be resident; exiting"
                            : "Started with --background but the handoff endpoint is unavailable; exiting");
```

Update the Javadoc of `handsOff` and `residentRole` to say "a standalone launch (`--config`, `--safe-mode`, `--plugin-dir`, `--standalone`)" where they say "a launch pointed at another configuration". Run `grep -rn "always standalone" jasper-app/src/test` and update any test that asserts the old wording.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.bootstrap.*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/bootstrap jasper-app/src/test/java/dev/jasper/app/bootstrap
git commit -m "feat: add standalone --safe-mode, --plugin-dir and --standalone launches

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 12: Exit deadline armed before shutdown work

`ApplicationShutdown.await` bounds asynchronous futures and is itself started from the EDT, so it cannot fire if an EDT callback, such as a plugin's `stop()`, never returns. The deadline must be armed first and must not need the EDT.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/ApplicationShutdown.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/ApplicationShutdownTest.java`

**Interfaces:**
- Produces: `ApplicationShutdown(Runnable terminate, Duration grace, Duration deadline)`; the one- and two-argument constructors remain (deadline = twice the grace); `void arm(Supplier<String> culprit)` (EDT, idempotent); `await` unchanged in signature. `terminate` runs at most once across both paths.

- [ ] **Step 1: Write the failing tests**

Append to `ApplicationShutdownTest`:

```java
    @Test void anArmedDeadlineTerminatesOffEdtEvenWhenTheEdtNeverReachesAwait() throws Exception {
        var terminated = new CountDownLatch(1);
        var onEdt = new java.util.concurrent.atomic.AtomicBoolean(true);
        var asked = new java.util.concurrent.atomic.AtomicBoolean();
        var release = new CountDownLatch(1);
        var shutdown = new ApplicationShutdown(() -> { onEdt.set(SwingUtilities.isEventDispatchThread()); terminated.countDown(); },
            Duration.ofSeconds(30), Duration.ofMillis(100));
        try {
            DesktopTestSupport.edt(() -> shutdown.arm(() -> { asked.set(true); return "test.plugin (stop)"; }));
            // A plugin's stop() that never returns: the EDT is stuck and await is never called.
            SwingUtilities.invokeLater(() -> { try { release.await(); } catch (InterruptedException ignored) { } });
            assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(onEdt).isFalse();
            assertThat(asked).as("the deadline names the callback that was running").isTrue();
        } finally { release.countDown(); }
    }

    @Test void normalTerminationDisarmsTheDeadlineAndTerminationRunsOnce() throws Exception {
        var calls = new AtomicInteger();
        var terminated = new CountDownLatch(1);
        var shutdown = new ApplicationShutdown(() -> { calls.incrementAndGet(); terminated.countDown(); },
            Duration.ofSeconds(5), Duration.ofMillis(150));
        DesktopTestSupport.edt(() -> { shutdown.arm(() -> null); shutdown.arm(() -> null); shutdown.await(List.of()); });
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(400);
        assertThat(calls).hasValue(1);
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*ApplicationShutdownTest'`
Expected: compilation FAILS, no three-argument constructor or `arm`.

- [ ] **Step 3: Implement**

Replace `ApplicationShutdown` with:

```java
package dev.jasper.app.application;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * EDT-started, once-only shutdown. {@link #arm} starts an exit deadline that needs no EDT to fire,
 * because a blocked EDT callback cannot be abandoned: cleanup after it never runs, and only the
 * deadline ends the process. {@link #await} is the normal path, a bounded wait for asynchronous
 * cleanup. Termination always runs on a separate platform thread, at most once.
 */
final class ApplicationShutdown {
    private static final System.Logger LOG = System.getLogger(ApplicationShutdown.class.getName());
    private final Runnable terminate;
    private final Duration grace;
    private final Duration deadline;
    private final AtomicBoolean terminated = new AtomicBoolean();
    private final CountDownLatch finished = new CountDownLatch(1);
    private boolean armed;
    private boolean started;

    ApplicationShutdown(Runnable terminate) { this(terminate, Duration.ofSeconds(2)); }
    ApplicationShutdown(Runnable terminate, Duration grace) { this(terminate, grace, grace.multipliedBy(2)); }
    ApplicationShutdown(Runnable terminate, Duration grace, Duration deadline) {
        this.terminate = Objects.requireNonNull(terminate);
        this.grace = Objects.requireNonNull(grace);
        this.deadline = Objects.requireNonNull(deadline);
        if (grace.isNegative() || grace.isZero()) throw new IllegalArgumentException("Shutdown grace must be positive");
        if (deadline.isNegative() || deadline.isZero()) throw new IllegalArgumentException("Shutdown deadline must be positive");
    }

    /**
     * Call before any shutdown work that runs code the application does not own. {@code culprit}
     * is read off the EDT when the deadline fires and names what was running, or returns null.
     */
    void arm(Supplier<String> culprit) {
        Objects.requireNonNull(culprit);
        if (armed) return;
        armed = true;
        Thread.ofPlatform().daemon().name("jasper-exit-deadline").start(() -> {
            try {
                if (finished.await(deadline.toMillis(), TimeUnit.MILLISECONDS)) return;
            } catch (InterruptedException interrupted) { return; }
            String running = culprit.get();
            LOG.log(System.Logger.Level.WARNING, "Shutdown deadline elapsed"
                + (running == null ? "" : " while running " + running) + "; terminating");
            terminateOnce();
        });
    }

    void await(List<CompletableFuture<?>> pending) {
        if (started) return;
        started = true;
        long began = System.nanoTime();
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
            .orTimeout(grace.toMillis(), TimeUnit.MILLISECONDS)
            .whenComplete((ignored, failure) -> Thread.ofPlatform().name("jasper-exit").start(() -> {
                LOG.log(System.Logger.Level.INFO, "Shutdown finished in " + (System.nanoTime() - began) / 1_000_000
                    + " ms" + (failure == null ? "" : " (cleanup timed out)"));
                terminateOnce();
            }));
    }

    private void terminateOnce() {
        if (!terminated.compareAndSet(false, true)) return;
        try { terminate.run(); }
        finally { finished.countDown(); }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*'`
Expected: PASS, including the two pre-existing `ApplicationShutdownTest` cases and `JasperApplicationShutdownTest`.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/application/ApplicationShutdown.java jasper-app/src/test/java/dev/jasper/app/application/ApplicationShutdownTest.java
git commit -m "feat: arm an EDT-independent exit deadline before shutdown work

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 13: Activity notices for Buddy

The notifications package gains an app-native producer; it never sees an SDK type. `jasper-buddy` is unchanged: `BuddyNoticeId(source, key)` was designed for other producers.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/notifications/ActivityNotifier.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/notifications/ActivityNotifierTest.java`

**Interfaces:**
- Produces (public, EDT only): `ActivityNotifier(BuddyCompanion deck, Runnable activate)`; `void started(String source, UUID id, String title, String detail)`; `void progress(String source, UUID id, String detail)`; `void finished(String source, UUID id, String title, boolean succeeded, String detail)`; `void cancelled(String source, UUID id)`; `void close()`

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.notifications;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ActivityNotifierTest {
    private final BuddyTestSupport deck = new BuddyTestSupport();
    private final UUID id = UUID.randomUUID();

    @Test void aRunningActivityIsOneCardWhoseDetailFollowsProgress() {
        var notifier = new ActivityNotifier(deck.companion(), () -> { });
        notifier.started("dev.jasper.sftp", id, "Upload\nsite.tar", "starting");
        notifier.progress("dev.jasper.sftp", id, "40% · 4 of 10 MB");
        assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.id()).isEqualTo(new BuddyNoticeId("dev.jasper.sftp", id));
            assertThat(notice.kind()).isEqualTo(BuddyNotice.Kind.TASK);
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.RUNNING);
            assertThat(notice.title()).isEqualTo("Upload site.tar");
            assertThat(notice.detail().get()).isEqualTo("40% · 4 of 10 MB");
            assertThat(notice.live()).isTrue();
        });
    }

    @Test void theOutcomeReplacesTheCardAndCancellationRemovesIt() {
        var notifier = new ActivityNotifier(deck.companion(), () -> { });
        UUID other = UUID.randomUUID();
        notifier.started("p.one", id, "Upload", "");
        notifier.started("p.one", other, "Download", "");
        notifier.finished("p.one", id, "Upload", false, "connection reset");
        notifier.cancelled("p.one", other);
        assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.FAILED);
            assertThat(notice.detail().get()).isEqualTo("connection reset");
        });
        notifier.finished("p.one", UUID.randomUUID(), "Never started", true, "done");
        assertThat(deck.notices()).as("an outcome with no running card still records the result").hasSize(2);
    }

    @Test void nothingIsPostedAfterClose() {
        var notifier = new ActivityNotifier(deck.companion(), () -> { });
        notifier.close();
        notifier.close();
        notifier.started("p.one", id, "Upload", "");
        notifier.progress("p.one", id, "x");
        notifier.finished("p.one", id, "Upload", true, "done");
        notifier.cancelled("p.one", id);
        assertThat(deck.notices()).isEmpty();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*ActivityNotifierTest'`
Expected: compilation FAILS, `ActivityNotifier` not found.

- [ ] **Step 3: Implement**

```java
package dev.jasper.app.notifications;

import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.view.BuddyCompanion;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Producer for work that something other than a terminal pane volunteers: today, plugin activities.
 * Each activity is one task card keyed by its source and id. A card appears when the activity starts;
 * unlike a shell command it was declared worth showing, so there is no threshold. Progress changes the
 * detail the running card already reads, so nothing is re-posted. This producer never drives Buddy's
 * working animation, which the command notifier owns. EDT only; holds no Swing state.
 */
public final class ActivityNotifier implements AutoCloseable {
    private final Map<BuddyNoticeId, String> details = new HashMap<>();
    private BuddyCompanion deck;
    private Runnable activate;

    /**
     * Binds the producer to the companion.
     *
     * @param deck the companion that shows the cards
     * @param activate what a click on a card does; an activity has no pane to focus, so the application raises itself
     */
    public ActivityNotifier(BuddyCompanion deck, Runnable activate) {
        this.deck = Objects.requireNonNull(deck, "deck");
        this.activate = Objects.requireNonNull(activate, "activate");
    }

    /**
     * Posts a running card.
     *
     * @param source the producer namespace, a plugin id
     * @param id the activity's identity within that source
     * @param title display title; line breaks become spaces
     * @param detail initial detail line
     */
    public void started(String source, UUID id, String title, String detail) {
        if (deck == null) return;
        var key = new BuddyNoticeId(source, id);
        details.put(key, detail);
        deck.post(new BuddyNotice(key, BuddyNotice.Kind.TASK, singleLine(title), BuddyNotice.State.RUNNING,
            () -> details.getOrDefault(key, ""), activate));
    }

    /**
     * Changes a running card's detail; ignored when no such card is running.
     *
     * @param source the producer namespace
     * @param id the activity's identity
     * @param detail the new detail line
     */
    public void progress(String source, UUID id, String detail) {
        if (deck == null) return;
        details.computeIfPresent(new BuddyNoticeId(source, id), (key, previous) -> detail);
    }

    /**
     * Replaces the card with its outcome.
     *
     * @param source the producer namespace
     * @param id the activity's identity
     * @param title display title
     * @param succeeded whether the work succeeded
     * @param detail final detail line
     */
    public void finished(String source, UUID id, String title, boolean succeeded, String detail) {
        if (deck == null) return;
        var key = new BuddyNoticeId(source, id);
        details.remove(key);
        deck.post(new BuddyNotice(key, BuddyNotice.Kind.TASK, singleLine(title),
            succeeded ? BuddyNotice.State.DONE : BuddyNotice.State.FAILED, () -> detail, activate));
    }

    /**
     * Removes the card of work the user cancelled: there is no outcome worth remembering.
     *
     * @param source the producer namespace
     * @param id the activity's identity
     */
    public void cancelled(String source, UUID id) {
        if (deck == null) return;
        var key = new BuddyNoticeId(source, id);
        details.remove(key);
        deck.dismiss(key);
    }

    private static String singleLine(String title) {
        return title.replace("\r", "").replace("\n", " ");
    }

    @Override public void close() {
        deck = null;
        activate = () -> { };
        details.clear();
    }
}
```

Update `notifications/package-info.java`'s first sentence to: `EDT terminal and activity notice production, active producer identities, attention and visibility policy. Application closes notifiers before companion.` (its allowed dependencies do not change).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.notifications.*'`
Expected: PASS. If `BuddyTestSupport.notices()` keeps dismissed notices, read `BuddyTestSupport` and assert on what it exposes for dismissal instead; do not change Buddy.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/notifications jasper-app/src/test/java/dev/jasper/app/notifications/ActivityNotifierTest.java
git commit -m "feat: post volunteered activities to Buddy

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 14: `PluginRuntime` and application wiring

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/{JasperApplication,package-info}.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginRuntimeTest.java`, `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java`

**Interfaces:**
- Consumes: everything in `dev.jasper.app.plugins`, `ActivityNotifier`, `ApplicationShutdown.arm`, `ConfigSnapshot.plugins()`, `ConfigurationController.report`, `AppDirs.plugins()/pluginState()/pluginLock()/pluginData()`, `AppArguments.safeMode()/pluginDir()`.
- Produces (public, EDT unless noted):
  - `record PluginRuntime.Options(Path bundledDirectory, Path userDirectory, Path developmentDirectory, boolean safeMode, Path stateFile, Path lockFile, Path dataRoot)` (`bundledDirectory` and `developmentDirectory` may be null)
  - `PluginRuntime(Options options, ActivityNotifier notifier, BiConsumer<String, String> configReport)`
  - `static Path bundledDirectory(Path codeSource)`; `void start(Map<String, Map<String, Object>> tables)`; `void configurationChanged(Map<String, Map<String, Object>> tables)`; `void themeChanged(boolean dark)`; `List<String> statusLines()`; `String executing()` (any thread); `List<CompletableFuture<?>> stop()`
  - `JasperApplication.startPlugins(Path codeSource, Path developmentDirectory, boolean safeMode, AppDirs dirs)`

- [ ] **Step 1: Write the failing runtime test**

`PluginRuntimeTest.java`. The fixture plugin writes what it observes into its data directory, so the test needs no shared classes with it:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.testsupport.PluginJars;
import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static org.assertj.core.api.Assertions.assertThat;

class PluginRuntimeTest {
    static final String FIXTURE = """
        package fix.probe;
        import dev.jasper.sdk.activity.ActivitySpec;
        import dev.jasper.sdk.events.AppEvents;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import java.nio.file.Files;
        public final class Main implements Plugin {
            private PluginContext context;
            private static void note(PluginContext context, String name, String text) {
                try { Files.writeString(context.dataDirectory().resolve(name), text); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
            @Override public void start(PluginContext context) {
                this.context = context;
                note(context, "started", context.config().string("greeting").orElse("none"));
                context.config().onChanged(() -> note(context, "changed", context.config().string("greeting").orElse("none")));
                context.events().subscribe(AppEvents.THEME_CHANGED, event -> note(context, "theme", event.variant().name()));
                context.events().subscribe(AppEvents.CONFIG_RELOADED, event -> note(context, "reloaded", "yes"));
                context.activities().begin(ActivitySpec.of("Probe work")).progress(0.5, "half way");
            }
            @Override public void stop() { note(context, "stopped", "yes"); }
        }
        """;

    @TempDir Path root;
    private final BuddyTestSupport deck = new BuddyTestSupport();

    private PluginRuntime runtime(Path bundled, Path user, Path dev, boolean safeMode, List<String> reports) {
        var created = new AtomicReference<PluginRuntime>();
        onEdt(() -> created.set(new PluginRuntime(new PluginRuntime.Options(bundled, user, dev, safeMode,
            root.resolve("plugins.toml"), root.resolve("plugins.lock"), root.resolve("plugin-data")),
            new ActivityNotifier(deck.companion(), () -> { }), (key, message) -> reports.add(key + ": " + message))));
        return created.get();
    }

    private static void settle() { for (int i = 0; i < 3; i++) onEdt(() -> { }); }

    private Path probe(Path parent, String id) throws Exception {
        Path directory = parent.resolve(id);
        PluginJars.build(directory, "probe.jar", PluginJars.descriptor(id, "1.0.0", "fix.probe.Main"),
            Map.of("fix.probe.Main", FIXTURE), List.of());
        return directory;
    }

    @Test void runsADevelopmentPluginBridgesItsActivityAndStopsIt() throws Exception {
        Path dev = probe(root.resolve("dev"), "dev.example.probe");
        PluginRuntime runtime = runtime(null, root.resolve("absent"), dev, false, new ArrayList<>());
        onEdt(() -> runtime.start(Map.of("dev.example.probe", Map.<String, Object>of("greeting", "hello"))));
        settle();
        Path data = root.resolve("plugin-data/dev.example.probe");
        assertThat(data.resolve("started")).hasContent("hello");
        assertThat(runtime.statusLines()).singleElement().asString().contains("dev.example.probe", "DEV", "ACTIVE");
        onEdt(() -> assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.id().source()).isEqualTo("dev.example.probe");
            assertThat(notice.title()).isEqualTo("Probe work");
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.RUNNING);
            assertThat(notice.detail().get()).isEqualTo("50% · half way");
        }));

        onEdt(() -> runtime.configurationChanged(Map.of("dev.example.probe", Map.<String, Object>of("greeting", "again"))));
        onEdt(() -> runtime.themeChanged(false));
        settle();
        assertThat(data.resolve("changed")).hasContent("again");
        assertThat(data.resolve("reloaded")).hasContent("yes");
        assertThat(data.resolve("theme")).hasContent("LIGHT");

        var pending = new AtomicReference<List<CompletableFuture<?>>>();
        onEdt(() -> pending.set(runtime.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        settle();
        assertThat(data.resolve("stopped")).hasContent("yes");
        onEdt(() -> assertThat(deck.notices()).singleElement()
            .satisfies(notice -> assertThat(notice.state()).isEqualTo(BuddyNotice.State.FAILED)));
    }

    @Test void userPluginsWaitForConsentAndSafeModeSkipsThem() throws Exception {
        Path user = root.resolve("user");
        probe(user, "dev.example.probe");
        PluginRuntime unreviewed = runtime(null, user, null, false, new ArrayList<>());
        onEdt(() -> unreviewed.start(Map.of()));
        assertThat(unreviewed.statusLines()).singleElement().asString().contains("NEEDS_CONSENT");
        assertThat(root.resolve("plugin-data/dev.example.probe/started")).doesNotExist();

        Files.writeString(root.resolve("plugins.toml"), "version = 1\n[plugins.\"dev.example.probe\"]\nenabled = true\nconsented = []\n");
        PluginRuntime consented = runtime(null, user, null, false, new ArrayList<>());
        onEdt(() -> consented.start(Map.of()));
        assertThat(consented.statusLines()).singleElement().asString().contains("ACTIVE");
        onEdt(consented::stop);

        PluginRuntime safe = runtime(null, user, null, true, new ArrayList<>());
        onEdt(() -> safe.start(Map.of()));
        assertThat(safe.statusLines()).singleElement().asString().contains("DISABLED", "safe mode");
    }

    @Test void aPluginThatCannotLoadIsReportedAndTheRestStillStart() throws Exception {
        Path bundled = root.resolve("bundled");
        probe(bundled, "dev.example.probe");
        PluginJars.build(bundled.resolve("dev.example.broken"), "broken.jar",
            PluginJars.descriptor("dev.example.broken", "1.0.0", "fix.broken.Missing"), Map.of(), List.of());
        PluginRuntime runtime = runtime(bundled, root.resolve("absent"), null, false, new ArrayList<>());
        onEdt(() -> runtime.start(Map.of()));
        assertThat(runtime.statusLines()).hasSize(2)
            .anySatisfy(line -> assertThat(line).contains("dev.example.broken", "FAILED", "fix.broken.Missing"))
            .anySatisfy(line -> assertThat(line).contains("dev.example.probe", "ACTIVE"));
        onEdt(runtime::stop);
    }

    @Test void theBundledDirectorySitsBesideTheApplicationJarUnlessOverridden() throws Exception {
        Path jar = Files.createFile(root.resolve("jasper-app.jar"));
        assertThat(PluginRuntime.bundledDirectory(jar)).isEqualTo(root.resolve("plugins"));
        assertThat(PluginRuntime.bundledDirectory(root)).as("classes directory during development").isNull();
        assertThat(PluginRuntime.bundledDirectory(null)).isNull();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*PluginRuntimeTest'`
Expected: compilation FAILS, `PluginRuntime` not found.

- [ ] **Step 3: Implement `PluginRuntime`**

```java
package dev.jasper.app.plugins;

import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.sdk.JasperSdk;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.events.AppEvents;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import javax.swing.SwingUtilities;

/**
 * The application's single entry to plugins: discover, resolve, load and start them once at launch,
 * forward configuration and theme changes, show their activities on Buddy, and stop them at shutdown.
 * Install, enable, disable and update take effect at the next start. EDT only, except {@link #executing}.
 */
public final class PluginRuntime {
    /**
     * Where plugins and their state live.
     *
     * @param bundledDirectory plugins shipped with the application, or null
     * @param userDirectory plugins the user installed; need consent
     * @param developmentDirectory one plugin directory from {@code --plugin-dir}, or null
     * @param safeMode skip user plugins
     * @param stateFile {@code plugins.toml}
     * @param lockFile cross-process lock for the state file
     * @param dataRoot parent of each plugin's data directory
     */
    public record Options(Path bundledDirectory, Path userDirectory, Path developmentDirectory, boolean safeMode,
                          Path stateFile, Path lockFile, Path dataRoot) { }

    private static final System.Logger LOG = System.getLogger(PluginRuntime.class.getName());
    private static final Duration DRAIN_GRACE = Duration.ofMillis(1500);
    private static final Duration LOCK_WAIT = Duration.ofSeconds(2);

    private final Options options;
    private final ActivityNotifier notifier;
    private final BiConsumer<String, String> configReport;
    private final List<PluginStatus> statuses = new ArrayList<>();
    private final List<PluginClassLoader> loaders = new ArrayList<>();
    private volatile Map<String, Map<String, Object>> tables = Map.of();
    private volatile PluginHost host;
    private Subscription bridge;

    /**
     * Creates an idle runtime.
     *
     * @param options locations and mode
     * @param notifier receives plugin activities for Buddy
     * @param configReport receives plugin complaints about their settings as key and message
     */
    public PluginRuntime(Options options, ActivityNotifier notifier, BiConsumer<String, String> configReport) {
        this.options = Objects.requireNonNull(options);
        this.notifier = Objects.requireNonNull(notifier);
        this.configReport = Objects.requireNonNull(configReport);
    }

    /**
     * The bundled plugin directory: the {@code jasper.plugins.bundled} system property when set (Gradle
     * run and tests), otherwise {@code plugins} beside the application jar. A classes directory has none.
     *
     * @param codeSource the application's code source, or null
     * @return the directory, which need not exist, or null
     */
    public static Path bundledDirectory(Path codeSource) {
        String override = System.getProperty("jasper.plugins.bundled");
        if (override != null && !override.isBlank()) return Path.of(override);
        if (codeSource == null || !Files.isRegularFile(codeSource)) return null;
        return codeSource.toAbsolutePath().getParent().resolve("plugins");
    }

    /**
     * Discovers, resolves, loads and starts plugins in dependency order. Call once, before the first window.
     *
     * @param pluginTables the {@code [plugins."<id>"]} tables of the current configuration
     */
    public void start(Map<String, Map<String, Object>> pluginTables) {
        if (host != null) throw new IllegalStateException("Plugins already started");
        long began = System.nanoTime();
        tables = Map.copyOf(pluginTables);
        List<String> problems = new ArrayList<>();
        List<PluginCandidate> candidates = new ArrayList<>(
            PluginDiscovery.scan(options.bundledDirectory(), PluginCandidate.Origin.BUNDLED, problems));
        candidates.addAll(PluginDiscovery.scan(options.userDirectory(), PluginCandidate.Origin.USER, problems));
        if (options.developmentDirectory() != null)
            PluginDiscovery.single(options.developmentDirectory(), PluginCandidate.Origin.DEV, problems).ifPresent(candidates::add);
        Map<String, PluginStateStore.Entry> state;
        try { state = new PluginStateStore(options.stateFile(), options.lockFile(), LOCK_WAIT).read(); }
        catch (IOException unreadable) {
            // Without readable consent, user plugins stay inert rather than running unreviewed.
            LOG.log(System.Logger.Level.WARNING, "Plugin state is unreadable; user plugins will need consent", unreadable);
            state = Map.of();
        }
        var resolution = PluginResolver.resolve(candidates, state, Version.parse(JasperSdk.VERSION), options.safeMode());
        statuses.addAll(resolution.rejected());
        PluginHost created = new PluginHost(new PluginHost.Environment(SwingUtilities::invokeLater,
            SwingUtilities::isEventDispatchThread, id -> options.dataRoot().resolve(id),
            id -> tables.getOrDefault(id, Map.of()), configReport, DRAIN_GRACE));
        host = created;
        bridge = created.bus.subscribe(EventBus.APP, Activities.TOPIC, this::forward);
        Map<String, PluginLoader.Loaded> loaded = new LinkedHashMap<>();
        for (PluginCandidate candidate : resolution.load()) {
            PluginLoader.Loaded unit;
            try { unit = PluginLoader.load(candidate, loaded, PluginRuntime.class.getClassLoader()); }
            catch (PluginLoader.LoadFailure failure) {
                statuses.add(PluginStatus.of(candidate, PluginStatus.State.FAILED, failure.getMessage()));
                continue;
            }
            loaded.put(candidate.id(), unit);
            loaders.add(unit.loader());
            Set<String> hard = new HashSet<>(), optional = new HashSet<>();
            for (var requirement : candidate.descriptor().requires())
                (requirement.optional() ? optional : hard).add(requirement.id());
            PluginHost.Outcome outcome = created.start(new HostedPlugin(candidate.descriptor().info(), hard, optional,
                candidate.descriptor().exports(), unit.loader(), unit::instantiate));
            statuses.add(PluginStatus.of(candidate, outcome.state(), outcome.reason()));
        }
        problems.forEach(problem -> LOG.log(System.Logger.Level.WARNING, "Plugin discovery: " + problem));
        statusLines().forEach(line -> LOG.log(System.Logger.Level.INFO, "Plugin " + line));
        LOG.log(System.Logger.Level.INFO, "Plugins started in " + (System.nanoTime() - began) / 1_000_000 + " ms");
    }

    /**
     * Applies reloaded plugin tables, then announces the reload.
     *
     * @param pluginTables the new tables
     */
    public void configurationChanged(Map<String, Map<String, Object>> pluginTables) {
        PluginHost current = host;
        if (current == null) return;
        tables = Map.copyOf(pluginTables);
        current.settingsChanged();
        current.bus.publish(EventBus.APP, AppEvents.CONFIG_RELOADED, new AppEvents.ConfigReloaded());
    }

    /**
     * Announces a change of look.
     *
     * @param dark whether the new look is dark
     */
    public void themeChanged(boolean dark) {
        PluginHost current = host;
        if (current != null) current.bus.publish(EventBus.APP, AppEvents.THEME_CHANGED,
            new AppEvents.ThemeChanged(dark ? Variant.DARK : Variant.LIGHT));
    }

    /**
     * One line per discovered plugin: id, version, origin, state and reason.
     *
     * @return the lines, sorted by id
     */
    public List<String> statusLines() {
        return statuses.stream().map(PluginStatus::formatted).sorted().toList();
    }

    /**
     * The plugin callback running on the EDT right now, for the exit deadline's log line. Any thread.
     *
     * @return for example {@code dev.example.tool (stop)}, or null
     */
    public String executing() {
        PluginHost current = host;
        return current == null ? null : current.executing();
    }

    /**
     * Stops plugins in reverse order. The futures complete when background work has drained and the
     * classloaders are closed; the caller bounds the wait.
     *
     * @return asynchronous remainders of the shutdown
     */
    public List<CompletableFuture<?>> stop() {
        PluginHost current = host;
        if (current == null) return List.of();
        List<CompletableFuture<?>> pending = new ArrayList<>(current.stop());
        List<PluginClassLoader> closing = List.copyOf(loaders);
        loaders.clear();
        pending.add(CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).whenComplete((done, failure) -> {
            for (PluginClassLoader loader : closing) {
                try { loader.close(); }
                catch (IOException ignored) { LOG.log(System.Logger.Level.DEBUG, "Could not close " + loader.getName()); }
            }
        }));
        return pending;
    }

    private void forward(ActivityEvent event) {
        String detail = event.fraction().isPresent()
            ? Math.round(event.fraction().getAsDouble() * 100) + "%" + (event.detail().isEmpty() ? "" : " · " + event.detail())
            : event.detail();
        switch (event.state()) {
            case STARTED -> notifier.started(event.sourcePluginId(), event.id(), event.title(), detail);
            case PROGRESS -> notifier.progress(event.sourcePluginId(), event.id(), detail);
            case SUCCEEDED -> notifier.finished(event.sourcePluginId(), event.id(), event.title(), true, event.detail());
            case FAILED -> notifier.finished(event.sourcePluginId(), event.id(), event.title(), false, event.detail());
            case CANCELLED -> notifier.cancelled(event.sourcePluginId(), event.id());
        }
    }
}
```

`bridge` is deliberately never closed: activities failed by `stop()` are still queued when `stop()` returns and must reach Buddy.

- [ ] **Step 4: Run the runtime test**

Run: `./gradlew :jasper-app:test --tests '*PluginRuntimeTest'`
Expected: PASS, 4 tests.

- [ ] **Step 5: Write the failing application test**

`JasperApplicationPluginsTest.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.history.CommandHistory;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.testsupport.PluginJars;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(OS.WINDOWS)
class JasperApplicationPluginsTest {
    private static final String FIXTURE = """
        package fix.life;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import java.nio.file.Files;
        public final class Main implements Plugin {
            private PluginContext context;
            @Override public void start(PluginContext context) throws Exception {
                this.context = context;
                Files.writeString(context.dataDirectory().resolve("started"), "yes");
            }
            @Override public void stop() {
                try { Files.writeString(context.dataDirectory().resolve("stopped"), "yes"); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        }
        """;

    @TempDir Path home;

    @Test void pluginsStartBeforeWindowsAndStopBeforeTermination() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("dev-plugin");
        PluginJars.build(dev, "life.jar", PluginJars.descriptor("dev.example.life", "1.0.0", "fix.life.Main"),
            Map.of("fix.life.Main", FIXTURE), List.of());
        var stoppedBeforeExit = new boolean[1];
        var terminated = new CountDownLatch(1);
        Path data = dirs.pluginData().resolve("dev.example.life");
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> {
                stoppedBeforeExit[0] = java.nio.file.Files.exists(data.resolve("stopped"));
                terminated.countDown();
            });
            application[0].startPlugins(null, dev, false, dirs);
            application[0].startPlugins(null, dev, false, dirs);
        });
        assertThat(data.resolve("started")).exists();
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(stoppedBeforeExit[0]).isTrue();
    }
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*JasperApplicationPluginsTest'`
Expected: compilation FAILS, `startPlugins` not found.

- [ ] **Step 7: Wire `JasperApplication`**

Add imports `dev.jasper.app.notifications.ActivityNotifier`, `dev.jasper.app.platform.AppDirs`, `dev.jasper.app.plugins.PluginRuntime`. Add fields beside `notifications`:

```java
    private PluginRuntime plugins;
    private ActivityNotifier activityNotifier;
    private dev.jasper.app.lifecycle.Subscription pluginTheme;
```

In the constructor's `configuration.onSnapshot` listener add one line, so every reload reaches plugins (the listener also fires once at registration, before plugins exist, which the null check covers):

```java
        if (configuration != null) configuration.onSnapshot(snapshot -> {
            buddy.configured(snapshot.buddyEnabled()); updateBuddyActions();
            if (snippets != null) snippets.reload();
            loginItems.accept(snapshot.backgroundEnabled());
            if (plugins != null) plugins.configurationChanged(snapshot.plugins());
        });
```

Add the method after `warmUp()`:

```java
    /**
     * Starts plugins once, before the first window, so their contributions are in place when windows
     * appear. {@code codeSource} locates bundled plugins beside the application jar and may be null;
     * {@code developmentDirectory} is the {@code --plugin-dir} value or null.
     */
    public void startPlugins(Path codeSource, Path developmentDirectory, boolean safeMode, AppDirs dirs) {
        if (plugins != null || quitting || stopped) return;
        activityNotifier = new ActivityNotifier(buddy.companion(), this::raiseTerminal);
        plugins = new PluginRuntime(new PluginRuntime.Options(PluginRuntime.bundledDirectory(codeSource), dirs.plugins(),
            developmentDirectory, safeMode, dirs.pluginState(), dirs.pluginLock(), dirs.pluginData()), activityNotifier,
            (key, message) -> { if (configuration != null) configuration.report(key, message); });
        plugins.start(configuration == null ? Map.of() : configuration.snapshot().plugins());
        boolean[] replayed = new boolean[1];
        // subscribe replays the current theme at once; plugins read the look on demand, so only later changes are events.
        pluginTheme = themes.subscribe((theme, chromeChanged) -> {
            if (replayed[0] && chromeChanged) plugins.themeChanged(theme.chrome() == BuiltinTheme.DARK);
            replayed[0] = true;
        });
    }
```

Rewrite the head of `shutdown()` so the deadline is armed first, application-owned state is closed before any plugin code runs, and plugin remainders join the bounded wait:

```java
    private void shutdown() {
        if (stopped) return;
        stopped = true;
        quitting = true;
        // Armed before anything else: a plugin's stop() that blocks the EDT cannot be abandoned, and
        // await() below is never reached in that case.
        shutdown.arm(() -> plugins == null ? null : plugins.executing());
        launches.close();
        // Application-owned state first, so none of it depends on plugins behaving.
        history.close();
        shellHistory.close();
        if (snippets != null) snippets.close();
        List<CompletableFuture<?>> pluginWork = plugins == null ? List.of() : plugins.stop();
        if (pluginTheme != null) pluginTheme.close();
        notifications.close();
        if (activityNotifier != null) activityNotifier.close();
        buddyAppearance.close();
        buddy.close();
        if (configuration != null) configuration.close();
        if (quitHandlerInstalled) { Desktop.getDesktop().setQuitHandler(null); quitHandlerInstalled = false; }
        if (reopenListener != null) { Desktop.getDesktop().removeAppEventListener(reopenListener); reopenListener = null; }
        // Cross-process locks and socket probes must not hold up Swing or the exit deadline.
        List<CompletableFuture<?>> pending = new ArrayList<>(launches.pendingExits());
        pending.addAll(pluginWork);
        for (Runnable action : java.util.List.copyOf(shutdownActions)) pending.add(processCleanup(action));
        shutdownActions.clear();
        // Nothing else ends the JVM: without an explicit exit, AWT waits a full quiet second before it lets go.
        pending.add(history.closedFuture());
        shutdown.await(pending);
    }
```

The plugin drain grace (1.5 s) is inside the 2 s bounded wait, which is inside the 4 s deadline.

Update `application/package-info.java`: add `dev.jasper.app.plugins` to the allowed outgoing dependencies, in alphabetical position after `dev.jasper.app.platform`.

- [ ] **Step 8: Wire the bootstrap**

In `ApplicationBootstrap.startDesktop`, pass the options through: `history -> createApplication(service, history, dirs, options)`. Change `createApplication`'s signature to `(ConfigService service, CommandHistory history, AppDirs dirs, AppArguments options)` and, immediately after `new JasperApplication(...)` and before `acquired.transfer()`:

```java
            application.startPlugins(HandoffSocket.codeSource(), options.pluginDir(), options.safeMode(), dirs);
```

If `startPlugins` throws, the existing `catch` rolls the acquired resources back; the application object itself holds nothing that needs closing before its first window.

- [ ] **Step 9: Run the application and bootstrap suites**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.bootstrap.*' --tests 'dev.jasper.app.plugins.*'`
Expected: PASS, including `JasperApplicationShutdownTest` and `JasperApplicationResidencyTest` unchanged.

- [ ] **Step 10: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app jasper-app/src/test/java/dev/jasper/app
git commit -m "feat: run plugins from the application with ordered, bounded shutdown

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 15: Sample plugin, `verifyPluginArchitecture` and bundling

**Files:**
- Modify: `settings.gradle.kts`, `build.gradle.kts`, `jasper-app/build.gradle.kts`, `gradle/packaging.gradle`
- Create: `gradle/plugin-architecture.gradle.kts`
- Create: `plugins/sample/build.gradle.kts`, `plugins/sample/src/main/resources/plugin.toml`, `plugins/sample/src/main/java/dev/jasper/sample/{package-info,SamplePlugin}.java`
- Test: `plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java`

**Interfaces:**
- Consumes: the SDK, `FakePluginHost`, `PluginRuntime`, `ActivityNotifier`, `BuddyTestSupport`.
- Produces: plugin `dev.jasper.sample` (entry `dev.jasper.sample.SamplePlugin`); Gradle task `:jasper-app:stagePlugins` producing `jasper-app/build/plugins/dev.jasper.sample/jasper-plugin-sample.jar`; system property `jasper.plugins.bundled` for `run` and tests; root task `verifyPluginArchitecture`.

- [ ] **Step 1: Register the project**

`settings.gradle.kts`:

```kotlin
rootProject.name = "jasper"
include("jasper-terminal", "jasper-app", "jasper-buddy", "jasper-sdk", "jasper-sdk-testkit", "jasper-plugin-sample")
project(":jasper-plugin-sample").projectDir = file("plugins/sample")
```

`plugins/sample/build.gradle.kts`:

```kotlin
// A plugin compiles against the SDK only; the application supplies it at run time.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
}
```

`plugins/sample/src/main/resources/plugin.toml`:

```toml
id = "dev.jasper.sample"
name = "Sample"
version = "0.1.0"
entry = "dev.jasper.sample.SamplePlugin"
sdk = ">=0.1, <0.2"
description = "Exercises the plugin runtime end to end; inert unless demo_activity is set."
vendor = "Jasper"
```

- [ ] **Step 2: Write the failing plugin test**

```java
package dev.jasper.sample;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.activity.ActivityEvent;
import dev.jasper.sdk.testing.FakePluginHost;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SamplePluginTest {
    private static final PluginInfo INFO = new PluginInfo("dev.jasper.sample", "Sample", "0.1.0", Set.of());

    @Test void isInertByDefault() {
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.active("dev.jasper.sample")).isTrue();
            assertThat(host.runBackground()).isZero();
            host.flush();
            assertThat(host.activityLog()).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void runsItsDemoActivityWhenConfigured() {
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.sample", Map.of("demo_activity", true, "demo_step_millis", 0L));
            host.start(INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.runBackground()).isEqualTo(1);
            host.flush();
            assertThat(host.activityLog()).first().satisfies(event -> {
                assertThat(event.state()).isEqualTo(ActivityEvent.State.STARTED);
                assertThat(event.title()).isEqualTo("Sample plugin");
            });
            assertThat(host.activityLog()).last().satisfies(event -> {
                assertThat(event.state()).isEqualTo(ActivityEvent.State.SUCCEEDED);
                assertThat(event.detail()).isEqualTo("Ready");
            });
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void reportsAnOutOfRangeStepDelay() {
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.sample", Map.of("demo_step_millis", 60_000L));
            host.start(INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.reports()).containsExactly("dev.jasper.sample: demo_step_millis: Use 0 to 5000; using 300.");
        }
    }
}
```

`setConfig` before `start` supplies the plugin's initial table (Task 3).

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :jasper-plugin-sample:test`
Expected: compilation FAILS, `SamplePlugin` not found.

- [ ] **Step 4: Write the plugin**

`package-info.java`:

```java
/** The bundled sample plugin: the smallest complete plugin, and the runtime's end-to-end fixture. */
package dev.jasper.sample;
```

`SamplePlugin.java`:

```java
package dev.jasper.sample;

import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import dev.jasper.sdk.events.AppEvents;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import java.util.Optional;

/** Logs, listens for theme changes and, when {@code demo_activity = true}, shows a short activity on Buddy. */
public final class SamplePlugin implements Plugin {
    private static final int STEPS = 10;

    /** Created by the runtime. */
    public SamplePlugin() { }

    // example:plugin:start
    @Override public void start(PluginContext context) {
        context.log().log(System.Logger.Level.INFO, "Sample plugin " + context.plugin().version() + " started");
        context.events().subscribe(AppEvents.THEME_CHANGED,
            event -> context.log().log(System.Logger.Level.INFO, "The look is now " + event.variant()));
        long delay = context.config().integer("demo_step_millis").orElse(300);
        if (delay < 0 || delay > 5000) {
            context.config().report("demo_step_millis", "Use 0 to 5000; using 300.");
            delay = 300;
        }
        long stepMillis = delay;
        if (context.config().bool("demo_activity").orElse(false))
            context.background().execute(() -> demo(context, stepMillis));
    }
    // example:plugin:end

    private static void demo(PluginContext context, long stepMillis) {
        ActivityHandle activity = context.activities().begin(
            new ActivitySpec("Sample plugin", "Warming up", Optional.empty(), Optional.empty()));
        try {
            for (int step = 1; step <= STEPS; step++) {
                Thread.sleep(stepMillis);
                activity.progress(step / (double) STEPS, "Step " + step + " of " + STEPS);
            }
            activity.succeed("Ready");
        } catch (InterruptedException stopped) {
            activity.cancelled();
            Thread.currentThread().interrupt();
        }
    }
}
```

- [ ] **Step 5: Run the plugin's tests**

Run: `./gradlew :jasper-plugin-sample:test :jasper-sdk-testkit:test`
Expected: PASS.

- [ ] **Step 6: Add `verifyPluginArchitecture`**

`gradle/plugin-architecture.gradle.kts`:

```kotlin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec

// The build-time twin of PluginClassLoader: an in-repo plugin may reference only the JDK, the SDK,
// its own packages, libraries it bundles, and the exported packages of plugins it requires.
// Map each plugin project to the exported packages of its declared dependencies.
val pluginImports = mapOf(":jasper-plugin-sample" to listOf<String>())
val pluginProjects = pluginImports.keys.associateWith { project(it) }
val verifyPluginArchitecture = tasks.register("verifyPluginArchitecture") {
    pluginImports.keys.forEach { dependsOn("$it:classes") }
    doLast {
        val launcher = pluginProjects.values.first().extensions.getByType<JavaToolchainService>().launcherFor {
            languageVersion = JavaLanguageVersion.of(25)
            vendor = JvmVendorSpec.JETBRAINS
        }.get()
        val bin = launcher.metadata.installationPath.dir("bin").asFile
        val suffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
        fun tool(name: String, args: List<String>): String {
            val process = ProcessBuilder(listOf(bin.resolve(name + suffix).absolutePath) + args)
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { output }; return output
        }
        fun jdk(name: String) = name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.")
        val edge = Regex("^\\s*(\\S+)\\s+->\\s+(\\S+)\\s*(.*)$")
        for ((path, imports) in pluginImports) {
            val module = pluginProjects.getValue(path)
            val classes = module.layout.buildDirectory.dir("classes/java/main").get().asFile
            val own = classes.walkTopDown().filter { it.extension == "class" }.map {
                it.relativeTo(classes).invariantSeparatorsPath.removeSuffix(".class").replace('/', '.').substringBeforeLast('.')
            }.toSet()
            // Bundled libraries are whatever the plugin ships beside its jar: its runtime classpath.
            val bundled = module.configurations.getByName("runtimeClasspath").asPath
            val args = mutableListOf("--multi-release", "25", "--ignore-missing-deps", "-verbose:class", "-filter:none")
            if (bundled.isNotEmpty()) args += listOf("--class-path", bundled)
            tool("jdeps", args + classes.absolutePath).lineSequence().forEach { line ->
                val match = edge.matchEntire(line) ?: return@forEach
                val (from, to, where) = match.destructured
                if (from.substringBeforeLast('.') !in own) return@forEach
                val target = to.substringBeforeLast('.')
                val allowed = jdk(to) || target in own || imports.any { target == it }
                    || (to.startsWith("dev.jasper.sdk.") && !to.startsWith("dev.jasper.sdk.testing."))
                    || (bundled.isNotEmpty() && where.isNotBlank() && !where.contains("not found"))
                check(allowed) { "$path references a type plugins cannot see: $line" }
            }
        }
    }
}
gradle.projectsEvaluated {
    pluginProjects.values.forEach { it.tasks.named("check") { dependsOn(verifyPluginArchitecture) } }
}
```

Append to the root `build.gradle.kts`:

```kotlin
apply(from = "gradle/plugin-architecture.gradle.kts")
```

Run: `./gradlew verifyPluginArchitecture`
Expected: `BUILD SUCCESSFUL`. Then prove it bites: temporarily add `compileOnly(project(":jasper-buddy"))` to the sample and a field `static final Object LEAK = dev.jasper.buddy.notice.BuddyNoticeId.class;`; the task must FAIL with "references a type plugins cannot see". Remove both.

- [ ] **Step 7: Stage bundled plugins for `run`, tests and packaging**

In `jasper-app/build.gradle.kts`, after the `application { }` block:

```kotlin
// Bundled plugins are separate jars in plugins/<id>/, never on the application classpath.
val stagePlugins = tasks.register<Sync>("stagePlugins") {
    from(project(":jasper-plugin-sample").tasks.named("jar")) { into("dev.jasper.sample") }
    into(layout.buildDirectory.dir("plugins"))
}
tasks.named<JavaExec>("run") {
    dependsOn(stagePlugins)
    systemProperty("jasper.plugins.bundled", layout.buildDirectory.dir("plugins").get().asFile.absolutePath)
}
```

In the first `tasks.test { }` block add:

```kotlin
    dependsOn(stagePlugins)
    systemProperty("jasper.stagedPlugins", layout.buildDirectory.dir("plugins").get().asFile.absolutePath)
```

(`jasper.stagedPlugins`, not `jasper.plugins.bundled`: tests must choose their bundled directory explicitly.)

In the documentation-inputs `tasks.test { }` block at the end of the file, add `rootProject.file("jasper-sdk/README.md")` and `rootProject.fileTree("plugins") { include("**/*.java") }` to `inputs.files(...)`.

In `gradle/packaging.gradle`, in `stagePackage` add, after `from(rootProject.file('packaging/README.txt'))`:

```groovy
    from(tasks.named('stagePlugins')) { into('plugins') }
```

and in `verifyPackage` make the staged-file loop skip directories and verify plugins recursively:

```groovy
        inputDirectory.get().asFile.eachFile { staged ->
            if (staged.isDirectory()) return
            File packaged = new File(appDir, staged.name)
            if (!packaged.isFile() || !Arrays.equals(Files.readAllBytes(staged.toPath()), Files.readAllBytes(packaged.toPath()))) {
                throw new GradleException("Package verification failed: staged file differs or is missing: ${staged.name}")
            }
        }
        File stagedPlugins = new File(inputDirectory.get().asFile, 'plugins')
        stagedPlugins.eachFileRecurse(groovy.io.FileType.FILES) { staged ->
            File packaged = new File(appDir, 'plugins/' + stagedPlugins.toPath().relativize(staged.toPath()).toString())
            if (!packaged.isFile() || !Arrays.equals(Files.readAllBytes(staged.toPath()), Files.readAllBytes(packaged.toPath()))) {
                throw new GradleException("Package verification failed: bundled plugin file differs or is missing: ${staged}")
            }
        }
```

jpackage may list a plugin jar on `app.classpath`. That is harmless, and Task 7's tests prove why: a plugin's classes always come from its own loader, whose parent is the platform loader, never the application loader. Native packaging runs only on request; do not run `packageApp` in this task.

- [ ] **Step 8: Write the end-to-end test**

`jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static org.assertj.core.api.Assertions.assertThat;

/** The real sample jar, staged the way the application image bundles it, through the real runtime to Buddy. */
class BundledSamplePluginTest {
    @TempDir Path root;

    @Test void theStagedSampleLoadsFromItsJarAndItsActivityReachesBuddy() throws Exception {
        Path staged = Path.of(System.getProperty("jasper.stagedPlugins"));
        assertThat(staged.resolve("dev.jasper.sample")).isDirectory();
        var deck = new BuddyTestSupport();
        var runtime = new AtomicReference<PluginRuntime>();
        onEdt(() -> {
            runtime.set(new PluginRuntime(new PluginRuntime.Options(staged, root.resolve("user"), null, false,
                root.resolve("plugins.toml"), root.resolve("plugins.lock"), root.resolve("plugin-data")),
                new ActivityNotifier(deck.companion(), () -> { }), (key, message) -> { }));
            runtime.get().start(Map.of("dev.jasper.sample", Map.<String, Object>of("demo_activity", true, "demo_step_millis", 0L)));
        });
        assertThat(runtime.get().statusLines()).singleElement().asString()
            .contains("dev.jasper.sample", "0.1.0", "BUNDLED", "ACTIVE");
        var state = new AtomicReference<BuddyNotice.State>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (state.get() != BuddyNotice.State.DONE && System.nanoTime() < deadline) {
            onEdt(() -> state.set(deck.notices().isEmpty() ? null : deck.notices().get(0).state()));
            Thread.sleep(20);
        }
        onEdt(() -> assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.id().source()).isEqualTo("dev.jasper.sample");
            assertThat(notice.title()).isEqualTo("Sample plugin");
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.DONE);
            assertThat(notice.detail().get()).isEqualTo("Ready");
        }));
        var pending = new AtomicReference<List<CompletableFuture<?>>>();
        onEdt(() -> pending.set(runtime.get().stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
    }
}
```

- [ ] **Step 9: Run everything**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL`, including `:jasper-plugin-sample:check`, `verifySdkArchitecture`, `verifyPluginArchitecture` and `BundledSamplePluginTest`.

- [ ] **Step 10: Commit**

```bash
git branch --show-current
git add settings.gradle.kts build.gradle.kts gradle jasper-app/build.gradle.kts plugins jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java jasper-sdk-testkit
git commit -m "feat: bundle a sample plugin and guard what in-repo plugins may reference

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 16: Confine the SDK in the application, and document

**Files:**
- Modify: `gradle/application-architecture.gradle.kts`
- Modify: `jasper-app/src/test/java/dev/jasper/app/documentation/AppDocumentationTest.java`
- Create: `docs/sdk-architecture.md`, `docs/plugin-authoring.md`, `jasper-sdk/README.md`
- Modify: `AGENTS.md`, `docs/README.md`, `docs/STATUS.md`, `docs/app-architecture.md`, `docs/configuration.md`, this plan's status banner

- [ ] **Step 1: Confine SDK types to `dev.jasper.app.plugins`**

In `gradle/application-architecture.gradle.kts`, inside the `output.lineSequence().forEach` block, after the Buddy check:

```kotlin
                if (module == app && to.startsWith("dev.jasper.sdk."))
                    check(packageOf(from) == "dev.jasper.app.plugins" || packageOf(from).startsWith("dev.jasper.app.plugins.")) {
                        "SDK types are confined to dev.jasper.app.plugins: $line"
                    }
```

Add `":jasper-sdk:jar"` to the task's `dependsOn(...)`.

Run: `./gradlew verifyApplicationArchitecture`
Expected: `BUILD SUCCESSFUL`. Prove it bites: temporarily add `private static final Object LEAK = dev.jasper.sdk.Variant.DARK;` to `JasperApplication` (an enum constant, because a `String` constant such as `JasperSdk.VERSION` is inlined and leaves no bytecode reference); the task must FAIL with "SDK types are confined". Remove it.

- [ ] **Step 2: Extend the documentation tests first (failing)**

In `AppDocumentationTest`: add `"jasper-sdk/README.md"`, `"docs/sdk-architecture.md"`, `"docs/plugin-authoring.md"` to `GUIDES`; append the sample plugin to the example sources:

```java
        source += "\n" + Files.readString(ROOT.resolve("plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java"));
```

and make `everyProductionPackageHasAnOwnershipContract` cover the SDK by changing its module list to `List.of("app", "buddy", "sdk")`.

Run: `./gradlew :jasper-app:test --tests '*AppDocumentationTest'`
Expected: FAIL, the three new guides do not exist.

- [ ] **Step 3: Write `jasper-sdk/README.md`**

````markdown
# jasper-sdk

The API Jasper plugins compile against. It depends only on the JDK; `verifySdkArchitecture`
enforces that from bytecode, and `check` runs Javadoc with doclint.

| Package | Contents |
| --- | --- |
| `dev.jasper.sdk` | `JasperSdk.VERSION`, `PluginInfo`, `Variant`, `Subscription` |
| `dev.jasper.sdk.plugin` | `Plugin` (the entry point), `PluginContext`, `PluginConfig` |
| `dev.jasper.sdk.events` | `Topic`, `Events`, `AppEvents` |
| `dev.jasper.sdk.activity` | `Activities`, `ActivitySpec`, `ActivityHandle`, `ActivityEvent` |
| `dev.jasper.sdk.services` | `Services`, `ServiceUnavailableException` |

The SDK is **0.x: no compatibility promise** until the Vault and SSH plugins ship. Plugins
implement only `Plugin` and functional callbacks; the application and `jasper-sdk-testkit`
implement everything else, so methods can be added without breaking plugins.

Start with the [authoring guide](../docs/plugin-authoring.md); the
[architecture](../docs/sdk-architecture.md) explains loading, threading and lifetimes.
Unit-test a plugin with `dev.jasper.sdk.testing.FakePluginHost` from `jasper-sdk-testkit`.
````

- [ ] **Step 4: Write `docs/plugin-authoring.md`**

````markdown
# Writing a Jasper plugin

This guide covers what the SDK offers today (0.1): lifecycle, configuration, events,
activities and services. Panels, toolbar and menu items, status items, windows and terminal
access arrive in later SDK versions. The contract is in the
[design](superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md); the runtime is described
in the [SDK architecture](sdk-architecture.md).

## Layout

A plugin is a directory of jars named after its id. Exactly one jar has `plugin.toml` at its root:

```toml
id = "dev.example.tool"            # [a-z][a-z0-9_.-]{0,127}; "jasper" and "jasper." are reserved
name = "Tool"
version = "1.0.0"
entry = "dev.example.tool.ToolPlugin"
sdk = ">=0.1, <0.2"
capabilities = []                  # terminal.observe, terminal.selection, terminal.inject, terminal.open, session.provide
exports = []                       # packages other plugins may use

[[requires]]
id = "dev.example.other"
version = ">=1.0"
optional = false
```

Compile against `jasper-sdk` as `compileOnly`; ship any other library as a jar in the same
directory. A plugin cannot see the application's classes or libraries, and may not define
classes in `dev.jasper.*` platform packages or in a package a dependency exports.

## Entry point

<!-- EXAMPLE-MARKER:plugin -->
```java
@Override public void start(PluginContext context) {
    context.log().log(System.Logger.Level.INFO, "Sample plugin " + context.plugin().version() + " started");
    context.events().subscribe(AppEvents.THEME_CHANGED,
        event -> context.log().log(System.Logger.Level.INFO, "The look is now " + event.variant()));
    long delay = context.config().integer("demo_step_millis").orElse(300);
    if (delay < 0 || delay > 5000) {
        context.config().report("demo_step_millis", "Use 0 to 5000; using 300.");
        delay = 300;
    }
    long stepMillis = delay;
    if (context.config().bool("demo_activity").orElse(false))
        context.background().execute(() -> demo(context, stepMillis));
}
```

`start` and `stop` run on the Swing event dispatch thread and must return promptly. If
`start` throws, everything the context handed out is rolled back and the context is closed.
`stop` must not block: a blocked event thread cannot be abandoned, and the application's exit
deadline will end the process.

## Rules that matter

- **Threads.** Subscribe and publish services on the event thread. `publish`, activity
  handles, `require`/`find`, `log()` and `Subscription.close()` work from any thread. Slow
  work goes on `context.background()`.
- **Events.** A topic id starts with its owner's id. Only the owner publishes; anyone who can
  load the payload type may subscribe. Delivery is queued, ordered and never re-entrant. There
  is no replay: read current state, then listen.
- **Activities.** `context.activities().begin(...)` returns a handle; end it exactly once.
  Progress is coalesced. Jasper shows activities on Buddy. An activity left open when the
  plugin stops is ended as failed.
- **Services.** Publish interfaces from an exported package during `start` only, with
  `publish` or, to learn who is calling, `publishPerConsumer`. Consumers must declare
  `requires` on the provider. Put the interfaces in a separate API jar that consumers compile
  against.
- **Configuration.** Users write `[plugins."<id>"]` in `config.toml`. It is read-only to
  the plugin; keep your own state under `context.dataDirectory()`.
- **Your own threads and sockets** are yours to release, including before `start` throws.

## Testing

```java
try (var host = new FakePluginHost()) {
    host.setConfig("dev.example.tool", Map.of("enabled", true));
    host.start(new PluginInfo("dev.example.tool", "Tool", "1.0.0", Set.of()), Set.of(), Set.of(), new ToolPlugin());
    host.runBackground();   // background tasks run when you say so
    host.flush();           // events are delivered when you say so
    assertThat(host.activityLog()).isNotEmpty();
}
```

## Running a plugin in Jasper

```sh
jasper --plugin-dir /path/to/build/plugin-directory
```

`--plugin-dir` loads one plugin directory with consent pre-granted. Like `--safe-mode`
(no user plugins) and `--standalone`, it never hands off to or becomes a resident process.
Installed plugins live in `~/.config/jasper/plugins/<id>/` and stay inert until reviewed;
the Plugins manager that performs the review arrives with SDK plan 3. Until then a user
plugin can be consented by hand in `~/.config/jasper/plugins.toml`:

```toml
version = 1

[plugins."dev.example.tool"]
enabled = true
consented = []
remove = false
```
````

In the file you write, replace `EXAMPLE-MARKER` with `example` (it is spelled differently here only because the documentation test also scans this plan, before `SamplePlugin.java` exists). The block after that marker must equal the text between the `// example:plugin:start` and `// example:plugin:end` markers in `SamplePlugin.java` after `stripIndent().strip()`; copy it from the source rather than retyping it.

- [ ] **Step 5: Write `docs/sdk-architecture.md`**

````markdown
# Plugin SDK architecture

Three modules and one application package implement the
[plugin SDK design](superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md). This page
describes what exists after SDK plan 1; the [authoring guide](plugin-authoring.md) is the
plugin writer's view.

| Piece | Role | May depend on |
| --- | --- | --- |
| `jasper-sdk` | Interfaces and values plugins compile against | JDK |
| `jasper-sdk-testkit` | `FakePluginHost` and the abstract `PluginContractTest` | JDK, SDK |
| `dev.jasper.app.plugins` | The runtime; `PluginRuntime` is its only public type | SDK, `notifications`, `persistence` |
| `plugins/sample` | Bundled end-to-end fixture and documentation example | SDK (`compileOnly`) |

`verifySdkArchitecture`, `verifyPluginArchitecture` and `verifyApplicationArchitecture`
enforce those columns from bytecode. SDK types appear in the application only inside
`dev.jasper.app.plugins`; every other package offers app-native hooks
(`ConfigSnapshot.plugins()`, `ConfigurationController.report`, `ActivityNotifier`,
`ApplicationShutdown.arm`).

## Launch

`JasperApplication.startPlugins` runs once on the EDT before the first window:

1. `PluginDiscovery` reads `plugin.toml` from the bundled directory (`plugins/` beside the
   application jar, or `-Djasper.plugins.bundled`), the user directory and `--plugin-dir`.
2. `PluginResolver` drops user plugins in safe mode, keeps the higher version of a duplicate
   id, checks the SDK range, applies `plugins.toml` (disabled, marked for removal, consent),
   removes dependency cycles and unmet hard dependencies, and orders dependencies first.
3. `PluginLoader` refuses jars that define reserved or imported packages and builds a
   `PluginClassLoader`: SDK from the application, exported packages of declared dependencies
   from their loaders, then the platform loader and the plugin's jars. Application classes and
   libraries are invisible. This is hygiene, not a sandbox.
4. `PluginHost` starts each plugin with its own `HostedContext`. A throwing `start` tears down
   everything the context handed out, discards staged services and closes the context; hard
   dependents are skipped.

Every outcome is a `PluginStatus` line in the log.

## Runtime rules

- **Containment.** Every call into plugin code catches `Exception` and `LinkageError`, logs
  against the plugin and counts the failure.
- **Events.** `EventBus.publish` always enqueues through `SwingUtilities.invokeLater`: one
  global order, never re-entrant. `jasper.*` topics belong to the application.
- **Activities.** `ActivityHub` stamps the source, coalesces progress and ends each activity
  once. `PluginRuntime` forwards them to `notifications.ActivityNotifier`, which posts Buddy
  cards keyed by `BuddyNoticeId(pluginId, activityId)`.
- **Services.** Staged during `start`, committed on success. Per-consumer factories run on
  the EDT just before each consumer starts; lookups are cache reads.
- **State.** `PluginStateStore` changes `plugins.toml` only inside a `FileLock` transaction
  and never at shutdown, because standalone launches mean several processes may edit it.

## Shutdown

`JasperApplication.shutdown` arms `ApplicationShutdown`'s exit deadline, closes
application-owned state, then stops plugins in reverse order on the EDT. Each plugin's
executor stops admitting tasks; accepted work drains for 1.5 s and is then interrupted, inside
the existing 2 s bounded wait. A `stop()` that blocks the EDT cannot be abandoned: the 4 s
deadline ends the process and logs which callback was running.

## Contract suite

`PluginContractTest` states the semantics once. `FakeContractTest` runs it against the
testkit; `AppContractTest` runs it against `PluginHost` on the real EDT. When the two
disagree, the implementation is wrong, not the contract.

## Not yet implemented

Actions, toolbar, menus and status items (plan 2); the rail, panels, plugin windows and the
Plugins manager with install, consent and restart (plan 3); terminal handles, injection,
plugin-provided sessions, capability gating and the cleanup worker (plan 4).
````

- [ ] **Step 6: Update the existing documents**

`AGENTS.md` (the replacement texts are fenced here only so this plan's own links are not checked as if they were the plan's):

- In the Modules bullet, after the Buddy sentence, add:

```markdown
`jasper-sdk` (`dev.jasper.sdk`, JDK-only) is the plugin API and `jasper-sdk-testkit` its fake and contract suite; in-repo plugins live under `plugins/` and compile against the SDK only. SDK types appear in the app only inside `dev.jasper.app.plugins`. `verifySdkArchitecture` and `verifyPluginArchitecture` enforce this.
```

- Replace the sentence `No interface without two real implementations. No plugin API in phase 1. No abstraction over "emulator backends".` with:

```markdown
No interface without two real implementations (SDK interfaces are implemented by the app and by the testkit). The plugin API is the SDK; the user lifted "no plugin API in phase 1" on 2026-09-21 ([design](docs/superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md)). No abstraction over "emulator backends".
```

- In the source-hygiene script, replace the default glob with one that covers every module:

```
for name in sys.argv[1:] or [str(p) for root in ("jasper-terminal/src", "jasper-app/src", "jasper-buddy/src", "jasper-sdk/src", "jasper-sdk-testkit/src", "plugins") for p in pathlib.Path(root).rglob("*.java")]:
```

- In "App and Buddy contributor entry points", add:

```markdown
Plugin work starts with [SDK architecture](docs/sdk-architecture.md), [plugin authoring](docs/plugin-authoring.md) and the [SDK README](jasper-sdk/README.md).
```

`docs/README.md`: change "Jasper has three Gradle modules; the application composes two independent libraries. Public Java visibility inside the app does not constitute a plugin API." to "Jasper's application composes two independent libraries and hosts plugins written against `jasper-sdk`. Public Java visibility inside the app does not constitute a plugin API; the SDK does." and add a table row:

```markdown
| `jasper-sdk` and plugins | [SDK packages](../jasper-sdk/README.md) | [Loading, threading and lifetimes](sdk-architecture.md) | [Writing and testing a plugin](plugin-authoring.md) |
```

`docs/app-architecture.md`:
- Replace "It introduces no plugin loader, stable plugin ABI or third-party permission model." with:

```markdown
Plugins are hosted by `dev.jasper.app.plugins` against the separate `jasper-sdk`; see [SDK architecture](sdk-architecture.md).
```

- Replace the paragraph beginning "App Java-public classes enable feature collaboration" with "App Java-public classes enable feature collaboration; they are not the plugin API. The supported API is `jasper-sdk`, and only `dev.jasper.app.plugins` may reference it. Do not infer SDK guarantees from Java visibility."
- Add the package table row, after `persistence`: `` | `plugins` | The plugin runtime: descriptors, locked consent state, resolution, per-plugin classloaders, the queued EDT event bus, activities, the service registry and plugin lifetimes. Application owns and stops PluginRuntime; it is the only package that may reference the SDK. | ``
- In "Startup, lifetime and shutdown", add one paragraph: "Plugins start once, before the first window. Shutdown arms an exit deadline first, closes application-owned state, then stops plugins in reverse order; their drained background work joins the bounded wait. A blocked EDT callback cannot be abandoned, so the deadline, not the bounded wait, ends the process in that case."

`docs/configuration.md`:
- In "Location and startup options", after the `--config` paragraph add: "`--safe-mode` starts without user-installed plugins, `--plugin-dir <path>` additionally loads one development plugin directory, and `--standalone` changes nothing else. All three, like `--config`, make the launch standalone: it never hands off to a resident Jasper and never becomes resident."
- Before "## Shortcuts" add:

````markdown
### Plugins

Each plugin reads its own table, keyed by its quoted id. Jasper does not validate the
contents; a plugin reports problems with its settings through the same diagnostics as the
rest of the file. A table for a plugin that is not installed is ignored without a warning.
Values may be strings, integers, floats, booleans, arrays of strings and nested tables.

```toml
[plugins."dev.jasper.sample"]
demo_activity = true      # show a short demonstration activity on Buddy at startup
demo_step_millis = 300    # 0 to 5000
```

Installed plugins live in `plugins/<id>/` beside `config.toml`; their enabled state and
consented capabilities are in `plugins.toml`, and their private data under `plugin-data/<id>/`.
````

`docs/STATUS.md`: replace "No plugin SDK has been implemented." with "Plugin SDK plan 1 (core and runtime) is implemented on `claude/plugin-sdk-plan-1`; plans 2–4 are not." and add, under "Current state", a dated section "Plugin SDK plan 1" that records: the four deliberate deviations listed at the top of this plan plus `publishPerConsumer`; the exact test counts read from `*/build/test-results/test/*.xml`; that the sample plugin ships in the application image and should leave it when the Vault plugin arrives; that user plugins can only be consented by editing `plugins.toml` until plan 3; and the native checklist below as pending user acceptance.

Set this plan's **Status** banner to "Implemented on `claude/plugin-sdk-plan-1`; native acceptance pending" and list any further deviation.

- [ ] **Step 7: Run the documentation tests and the hygiene check**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*'`
Expected: PASS: links resolve, the `plugin` example matches `SamplePlugin.java`, every SDK package has a `package-info.java`.

Run the AGENTS.md Python check with its new default glob.
Expected: no output.

- [ ] **Step 8: Full verification**

Run: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist`
Expected: `BUILD SUCCESSFUL`. Read the counts from `*/build/test-results/test/*.xml` for STATUS.md.

- [ ] **Step 9: Commit**

```bash
git branch --show-current
git add -A
git commit -m "docs: document the plugin SDK and confine it within the application

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Native acceptance (user-run; agents must not launch the GUI)

1. `./gradlew :jasper-app:run` — Jasper opens as before; the log shows `Plugin dev.jasper.sample 0.1.0 [BUNDLED] ACTIVE` and "Plugins started in N ms".
2. Add `[plugins."dev.jasper.sample"]` with `demo_activity = true` to `config.toml`, relaunch: Buddy shows "Sample plugin" ticking through ten steps to "Ready".
3. Set `demo_step_millis = 99999`, reload the configuration: the status bar's configuration diagnostics show the sample's warning; fixing the value and reloading clears it.
4. Toggle View → Appearance: the log shows "The look is now LIGHT" / "DARK".
5. With background residency enabled and a resident Jasper running, run `jasper --safe-mode`: a second, standalone process opens instead of a window from the resident one; quitting it leaves the resident running.
6. Quit with Cmd+Q: exit is as fast as before.

## Self-review record

- **Spec coverage.** §2 modules, packaging, descriptor, locations, resolution, classloaders, versioning → Tasks 1, 4–7, 15. §3 entry, lifecycle, rollback, context invalidation, threading, containment, shutdown order and deadline → Tasks 2, 8, 10, 12, 14. §7 events and activities → Tasks 2, 8, 10, 13, 14. §8 services (start-only, commit on success, EDT factories, ownership) and failure walkthrough 5 → Tasks 2, 10. §9 configuration → Task 9. §10 flags and locked `plugins.toml` transactions → Tasks 5, 11. §11 testkit, contract suite, runtime and lifecycle tests, sample → Tasks 3, 6, 7, 10, 12, 14, 15. §12 architecture checks and documentation → Tasks 1, 15, 16. Out of this plan by design: capabilities' gated methods, `TerminalEvents`, the cleanup worker and walkthroughs 1–4 (plan 4); the Plugins manager, retire request and Restart now (plan 3); all UI surfaces (plans 2–3).
- **Deviations from the spec** are listed at the top, plus one found while writing the contract suite: `Services.publish(Class<T>, Function<PluginInfo, T>)` is named `publishPerConsumer`, because a lambda passed for a functional service interface made the two `publish` overloads ambiguous to the compiler.
- **Type consistency.** `HostedPlugin`, `PluginHost.Environment`, `PluginHost.Outcome`, `PluginRuntime.Options`, `ContractHarness` and `AppArguments` signatures in each task's Interfaces block match their definitions and every call site above.
