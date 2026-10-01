# File Server FS-1: foundation and HTTP — Implementation Plan

Status: executed on `claude/file-server-fs1` (2026-09-30); not merged. Deviations from the plan text:

- `ResolvedLogin`'s instance predicate is `isAnonymous()` (Java forbids a static and an instance `anonymous()` with one signature).
- `ShareRoot` also refuses names the OS cannot represent (`InvalidPathException` becomes `PathRefusedException`) and C1 control characters; `attributes()` reports the requested entry's own name.
- HTTP login throttle: early or locked-out attempts get 429 with `Retry-After` instead of the server sleeping; a success during a lockout does not lift it; the Host check also runs before credentials.
- HTTP listing sends `Referrer-Policy: same-origin` (so the listing's own forms carry their Origin; `Origin: null` is refused); multipart parameter matching is anchored; 303/301 `Location` is built from canonical paths.
- Transfer ids are unique per JVM; `shutdown()` stops engines in parallel with one 5 s deadline (this can block `Plugin.stop()` up to 5 s, against the SDK's "must not block"; flagged for follow-up); the port-clash check uses the definition a running engine actually uses.
- Not implemented: FAILED from RUNNING (engines have no fatal-runtime channel; the JDK HTTP server does not die on its own).
- The panel is master/detail: rows show state, name, protocol, port, warning and start/stop; folder, access, URLs with Copy and the failure reason with Retry are in the selected server's card. The firewall hint shows only for running servers reachable from other computers.
- An Interface bind advertises one URL (the address it binds).
- Clicking the status item toggles the panel (the SDK has only `Panels.toggle`).
- Panel and dialog labels set `html.disable` before their text, so server names never render as Swing HTML.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship SDK 0.11.0 (`network.listen`, `Windows.onQuitting`) and a bundled File Server plugin that shares local folders over HTTP from a File Servers rail panel: saved and temporary servers, a jailed share root, Vault or session logins, an activity log, a status item, a palette scope, Serve Current Folder over HTTP and a quit confirmation.

**Architecture:** A new plugin `plugins/fileserver` (`dev.jasper.fileserver`). `share.ShareRoot` is the only way to reach the disk. `definition` holds saved servers (`servers.toml`). `engine` holds the protocol-neutral engine contract, events, login throttle and addresses, and `engine.http` the JDK `HttpServer` engine. `auth` turns a definition's login into credentials (Vault or session). `runtime.ServerRegistry` owns the start/stop state machine. `ui` holds Swing and the palette scope, and `FileServerPlugin` wires everything to the SDK. Dependency direction: `ui → runtime → engine.* → engine, share, auth, definition`.

**Tech Stack:** Java 25 (JBR), Gradle wrapper, Swing, JDK `com.sun.net.httpserver` (module `jdk.httpserver`, present in the packaged runtime), tomlj 1.1.1, JUnit 6 + AssertJ, `jasper-sdk-testkit`.

**Spec:** `docs/superpowers/specs/2026-09-30-jasper-file-server-design.md` (approved 2026-09-30). Read it before starting; this plan implements its "FS-1" delivery.

## Global Constraints

- Work on branch `claude/file-server-fs1` in `.worktrees/file-server-fs1`, created from `claude/file-server-spec`. Never commit on `main`. Verify the branch (`git branch --show-current`) before every commit: other sessions switch branches in the main checkout.
- End every commit message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Use `./gradlew`, never a system `gradle`. Tests are headless. Never launch the GUI (`:jasper-app:run`); GUI checks go to the user.
- `JasperSdk.VERSION` becomes `0.11.0`; every bundled plugin declares `sdk = ">=0.11.0, <0.12"`.
- New SDK members need complete Javadoc with `@since 0.11.0` (doclint runs in `check`).
- The plugin compiles against the SDK and, optionally, `dev.jasper.vault.api` only. Vendor types stay in `engine.*`. SDK types appear anywhere in the plugin; Vault types only in `auth.LoginResolver`, `FileServerPlugin` and tests.
- The plugin declares `capabilities = ["network.listen", "terminal.observe", "palette.contribute"]` and `requires = [{ id = "dev.jasper.vault", version = ">=0.2", optional = true }]`.
- Defaults: read-only, bind `loopback` (127.0.0.1), HTTP port 8080, login anonymous for HTTP. Only HTTP is offered in FS-1 (`Engines.AVAILABLE`); FTP, SFTP and TFTP definitions load and save but fail to start with "FTP servers are not available in this version of File Server" (FS-2..FS-4 add them).
- Source hygiene: no raw control, private-use or unpaired-surrogate characters in Java source (see AGENTS.md). Ordinary BMP symbols such as `…`, `—`, `‘ ’`, `●`, `▶`, `■`, `⚠`, `⇅`, `↑`, `↓` are fine, as elsewhere in the repo.
- Threading: `ServerRegistry`, `DefinitionStore` listeners, panels and dialogs live on the UI thread; engines start and stop on `context.background()`; engine events hop to the UI thread through the injected `ui` executor.

## Deviations from the spec text (decided while planning; record them in `docs/STATUS.md`)

1. User documentation goes to `docs/file-server.md` (linked from `docs/README.md`), not `plugins/fileserver/README.md`: no plugin has a README; Remote's user doc is `docs/remote.md`.
2. Setting keys use underscores like every other plugin: `confirm_quit`, `activity_entries`. `passive_ports` is declared in FS-2 with the FTP engine.
3. The privileged-port suggestion is `Protocol.suggestedPort(port)`: `port + 8000` below 1024 (21 → 8021, 80 → 8080), otherwise the protocol default. The spec's two examples disagreed (2121 vs 8021).
4. The HTTP engine also checks the `Host` header (IP literals, `localhost` and this computer's names only; 421 otherwise) against DNS rebinding, and refuses a state-changing request whose `Origin` names another site (403) against cross-site form posts. Neither is in the spec; both protect the defaults (an anonymous loopback share).
5. `Content-Type` comes from `URLConnection.getFileNameMap()` rather than `Files.probeContentType`; every file is sent as an attachment anyway.
6. Palette titles follow the house style ("Toggle File Servers", "New File Server…", "Stop All File Servers", "Start or Stop a File Server…", "Serve Current Folder over HTTP") rather than the spec's "File Server: …" prefixes; per-server Start/Stop is a palette scope (`>` rows with a Start or Stop verb), because actions are static.
7. "Save as a server…" on a temporary server adopts the running server under the same id instead of copying it.
8. TLS, fingerprints, the FTP passive range, authorized keys and "Serve Current Folder over FTP/SFTP/TFTP" arrive with FS-2..FS-4.
9. A folder deleted while its server runs is not flagged on the row: requests answer 404 and I/O errors appear in the activity log. The row warning waits for a later version.

## File map

SDK and app (Tasks 1–2):
- Modify `jasper-sdk/src/main/java/dev/jasper/sdk/Capabilities.java`, `JasperSdk.java`, `ui/Windows.java`
- Modify `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeUi.java`, `FakePluginHost.java`
- Modify `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java`
- Create `jasper-app/src/main/java/dev/jasper/app/contributions/QuitGuards.java`; modify `Contributions.java`
- Modify `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java`, `DescriptorParser.java`; `pluginmanager/Capabilities.java`; `application/JasperApplication.java`
- Six `plugins/*/src/main/resources/plugin.toml`; docs `jasper-sdk/README.md`, `docs/plugin-authoring.md`, `docs/sdk-architecture.md`

Plugin (Tasks 3–15), all under `plugins/fileserver/`:
- `build.gradle.kts`, `src/main/resources/plugin.toml`, `settings.toml`, `icons/fileserver.svg`
- `dev/jasper/fileserver/`: `FileServerPlugin`, `FileServerSettings`, `package-info`
- `share/`: `ShareRoot`, `ShareEntry`, `PathRefusedException`, `ReadOnlyShareException`, `IsFolderException`
- `definition/`: `Protocol`, `Bind`, `Login`, `PortRange`, `ServerDefinition`, `DefinitionFile`, `DefinitionStore`
- `engine/`: `ServerEngine`, `EngineFailure`, `ServerEvent`, `ServerEvents`, `Sizes`, `LoginThrottle`, `ServerAddresses`
- `engine/http/`: `HttpEngine`, `FileHandler`, `Range`, `Listing`, `HttpPaths`, `HostCheck`, `ThrottledAuthenticator`, `Multipart`
- `auth/`: `ResolvedLogin`, `SessionPasswords`, `LoginUnavailableException`, `LoginResolver`
- `runtime/`: `ServerState`, `ActivityLog`, `ServerRegistry`, `Engines`, `TransferActivities`
- `ui/`: `ServerRows`, `FileServersPanel`, `ServerEditor`, `ConfirmPanel`, `FileServerScope`
- Tests mirror the packages; shared test helpers in `src/test/java/dev/jasper/fileserver/`: `TestClock`, `Ports`, `FakeVault`, `TestUi`; `ui/UiTestAccess`.

Build wiring: `gradle/plugin-architecture.gradle.kts`, `jasper-app/build.gradle.kts` (`stagePlugins`), `jasper-app/src/test/java/dev/jasper/app/plugins/PluginZipsTest.java`, `BundledSamplePluginTest.java`.

Docs (Task 16): `docs/file-server.md`, `docs/README.md`, `docs/STATUS.md`.

---

### Task 1: SDK 0.11.0 and the `network.listen` capability

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/Capabilities.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`
- Create: `jasper-sdk/src/test/java/dev/jasper/sdk/CapabilitiesTest.java`
- Modify: `jasper-sdk/src/test/java/dev/jasper/sdk/terminal/TerminalValuesTest.java:89`, `ui/AppearanceContractTest.java:21`, `files/FilesValuesTest.java:54`, `palette/PaletteValuesTest.java:70`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/DescriptorParser.java:15-16`, `jasper-app/src/main/java/dev/jasper/app/pluginmanager/Capabilities.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/DescriptorParserTest.java:78-79`, `VersionRangeTest.java:32-36`
- Create: `jasper-app/src/test/java/dev/jasper/app/pluginmanager/CapabilityDescriptionsTest.java`
- Modify: `plugins/{editor,history,remote,sample,snippets,vault}/src/main/resources/plugin.toml` (the `sdk` line)
- Modify: `jasper-sdk/README.md`, `docs/plugin-authoring.md`

**Interfaces:**
- Produces: `Capabilities.NETWORK_LISTEN = "network.listen"` (last in `Capabilities.ALL`), `JasperSdk.VERSION = "0.11.0"`; the app accepts and describes `network.listen`.

- [ ] **Step 1: Write the failing SDK test**

Create `jasper-sdk/src/test/java/dev/jasper/sdk/CapabilitiesTest.java`:

```java
package dev.jasper.sdk;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CapabilitiesTest {
    @Test void theSdkIsVersionElevenWithTheNetworkListenCapability() {
        assertThat(JasperSdk.VERSION).isEqualTo("0.11.0");
        assertThat(Capabilities.NETWORK_LISTEN).isEqualTo("network.listen");
        assertThat(Capabilities.ALL).endsWith(Capabilities.NETWORK_LISTEN);
    }
}
```

In `jasper-sdk/src/test/java/dev/jasper/sdk/terminal/TerminalValuesTest.java` line 89, replace the enumeration with:

```java
        assertThat(Capabilities.ALL).containsExactly("terminal.observe", "terminal.selection", "terminal.inject", "terminal.open", "session.provide", "palette.contribute", "files.source", "network.listen");
```

In `ui/AppearanceContractTest.java:21`, `files/FilesValuesTest.java:54` and `palette/PaletteValuesTest.java:70`, replace `isEqualTo("0.10.1")` with `isEqualTo("0.11.0")`.

- [ ] **Step 2: Run the SDK tests to see them fail**

Run: `./gradlew :jasper-sdk:test`
Expected: FAIL — `NETWORK_LISTEN` does not compile.

- [ ] **Step 3: Add the capability and bump the version**

In `Capabilities.java`, after `FILES_SOURCE`, add the constant and extend `ALL`:

```java
    /** Add places, such as saved hosts, to Jasper's file chooser. @since 0.10.0 */
    public static final String FILES_SOURCE = "files.source";
    /**
     * Accepting connections from other programs or computers, such as serving files. Nothing gates it: the user
     * consents to it when the plugin is installed. @since 0.11.0
     */
    public static final String NETWORK_LISTEN = "network.listen";
    /** Every capability this SDK version knows. */
    public static final List<String> ALL = List.of(TERMINAL_OBSERVE, TERMINAL_SELECTION, TERMINAL_INJECT, TERMINAL_OPEN, SESSION_PROVIDE, PALETTE_CONTRIBUTE, FILES_SOURCE, NETWORK_LISTEN);
```

In `JasperSdk.java`: `public static final String VERSION = "0.11.0";`

- [ ] **Step 4: Run the SDK tests**

Run: `./gradlew :jasper-sdk:test`
Expected: PASS.

- [ ] **Step 5: Write the failing app tests**

In `DescriptorParserTest.java` lines 78-79, replace the set with:

```java
        assertThat(DescriptorParser.CAPABILITIES).isEqualTo(Set.of("terminal.observe", "terminal.selection",
            "terminal.inject", "terminal.open", "session.provide", "palette.contribute", "files.source", "network.listen"));
```

and add a test to the same class:

```java
    @Test void acceptsTheNetworkListenCapability() throws Exception {
        PluginDescriptor descriptor = DescriptorParser.parse(FULL.replace("\"session.provide\"", "\"network.listen\""));
        assertThat(descriptor.capabilities()).contains("network.listen");
    }
```

Replace `VersionRangeTest.pluginsBuiltForSdkZeroNineAreIncompatibleWithZeroTen` with:

```java
    @Test void pluginsBuiltForSdkZeroTenAreIncompatibleWithZeroEleven() {
        var host = Version.parse(dev.jasper.sdk.JasperSdk.VERSION);
        assertThat(VersionRange.parse(">=0.10.1, <0.11").contains(host)).isFalse();
        assertThat(VersionRange.parse(">=0.11.0, <0.12").contains(host)).isTrue();
    }
```

Create `jasper-app/src/test/java/dev/jasper/app/pluginmanager/CapabilityDescriptionsTest.java`:

```java
package dev.jasper.app.pluginmanager;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CapabilityDescriptionsTest {
    @Test void networkListenIsDescribedAndUnknownCapabilitiesAreShownByName() {
        assertThat(Capabilities.describe("network.listen"))
            .isEqualTo("Accept connections from other programs or computers, such as serving files (network.listen)");
        assertThat(Capabilities.describe("made.up")).isEqualTo("made.up");
    }
}
```

- [ ] **Step 6: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.DescriptorParserTest' --tests 'dev.jasper.app.plugins.VersionRangeTest' --tests 'dev.jasper.app.pluginmanager.CapabilityDescriptionsTest'`
Expected: FAIL — `network.listen` is unknown to the parser and undescribed.

- [ ] **Step 7: Teach the app the capability**

`DescriptorParser.java` lines 15-16:

```java
    static final Set<String> CAPABILITIES = Set.of("terminal.observe", "terminal.selection",
        "terminal.inject", "terminal.open", "session.provide", "palette.contribute", "files.source", "network.listen");
```

`pluginmanager/Capabilities.java`, add a case before `default`:

```java
            case "network.listen" -> "Accept connections from other programs or computers, such as serving files";
```

In every bundled manifest (`plugins/editor`, `history`, `remote`, `sample`, `snippets`, `vault`, file `src/main/resources/plugin.toml`, line 5) set:

```toml
sdk = ">=0.11.0, <0.12"
```

- [ ] **Step 8: Document it**

In `jasper-sdk/README.md`, after the "SDK 0.10.1 …" paragraph (line 74), add:

```markdown
SDK 0.11.0 adds the `network.listen` capability. A plugin declares it when it accepts connections from other
programs or computers, such as a file server; the user consents to it like any other capability, and nothing gates
it. Plugins declare `sdk = ">=0.11.0, <0.12"`.
```

In `docs/plugin-authoring.md`: in the example manifest (lines 19-20) set `sdk = ">=0.11, <0.12"` and append `, network.listen` to the capabilities comment; in the capability table (after the `files.source` row, line 243) add:

```markdown
| `network.listen` | nothing gated: declare it when your plugin accepts connections from other programs or computers, such as serving files |
```

- [ ] **Step 9: Run the affected suites**

Run: `./gradlew :jasper-sdk:check :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.pluginmanager.*' --tests 'dev.jasper.app.documentation.*'`
Expected: PASS (`PluginZipsTest` stages every bundled zip against 0.11.0).

- [ ] **Step 10: Commit**

```bash
git add jasper-sdk jasper-app plugins/*/src/main/resources/plugin.toml docs/plugin-authoring.md
git commit -m "SDK 0.11.0: the network.listen capability

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: `Windows.onQuitting` quit guards

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Windows.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeUi.java`, `FakePluginHost.java:690-703`
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java`, `ContractHarness.java:78-79` (Javadoc only)
- Create: `jasper-app/src/main/java/dev/jasper/app/contributions/QuitGuards.java`, `jasper-app/src/test/java/dev/jasper/app/contributions/QuitGuardsTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/contributions/Contributions.java`, `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java`, `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java:796-800`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java:250`, `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java`
- Modify: `jasper-sdk/README.md`, `docs/plugin-authoring.md:214-216`, `docs/sdk-architecture.md:335-341`

**Interfaces:**
- Consumes: Task 1's SDK version.
- Produces: `default Subscription Windows.onQuitting(Supplier<CompletableFuture<Boolean>> guard)`; `FakePluginHost.requestQuit()` also asks these guards; app `Contributions.quitGuards()` returning `QuitGuards` with `Subscription add(Supplier<CompletableFuture<Boolean>>)` and `boolean mayQuit()`.

Semantics (spec "SDK 0.11.0"): asked on quit and restart after plugin windows' and content tabs' guards, one at a time; `false` cancels; a guard that throws, returns `null`, or whose future fails **allows** the quit (as window guards do).

- [ ] **Step 1: Write the failing contract tests**

Add to `PluginContractTest.java` (after `quitAsksEveryPluginWindowsGuardsAndClosesNothing`); add `import java.util.concurrent.CompletableFuture;` if the file lacks it:

```java
    @Test void quitGuardsAreAskedInTurnAndOnlyAFalseDecisionRefuses() {
        var alpha = new AtomicReference<CompletableFuture<Boolean>>(CompletableFuture.completedFuture(false));
        List<String> asked = Collections.synchronizedList(new ArrayList<>());
        h.start(info("test.alpha"), Set.of(), Set.of(), context ->
            context.windows().onQuitting(() -> { asked.add("alpha"); return alpha.get(); }));
        h.start(info("test.beta"), Set.of(), Set.of(), context ->
            context.windows().onQuitting(() -> { asked.add("beta"); throw new IllegalStateException("broken guard"); }));
        h.ui(() -> assertThat(h.requestQuit()).isFalse());
        assertThat(asked).containsExactly("alpha");
        alpha.set(CompletableFuture.completedFuture(true));
        asked.clear();
        h.ui(() -> assertThat(h.requestQuit()).as("a guard that throws allows the quit").isTrue());
        assertThat(asked).containsExactly("alpha", "beta");
        alpha.set(CompletableFuture.failedFuture(new IllegalStateException("failed decision")));
        h.ui(() -> assertThat(h.requestQuit()).as("a failed decision allows the quit").isTrue());
    }

    @Test void aClosedQuitGuardIsNotAsked() {
        var registration = new AtomicReference<Subscription>();
        List<String> asked = Collections.synchronizedList(new ArrayList<>());
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> registration.set(
            context.windows().onQuitting(() -> { asked.add("alpha"); return CompletableFuture.completedFuture(false); })));
        h.ui(() -> assertThat(h.requestQuit()).isFalse());
        h.ui(() -> registration.get().close());
        h.ui(() -> assertThat(h.requestQuit()).isTrue());
        assertThat(asked).containsExactly("alpha");
    }
```

In `ContractHarness.java` line 78, update the Javadoc to: `/** UI thread: asks every open plugin window's closing guards, then every quit guard, as quitting Jasper does, closing nothing; true when the quit may go ahead. */`

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-sdk-testkit:test`
Expected: FAIL — `onQuitting` does not exist.

- [ ] **Step 3: Add the SDK method**

In `Windows.java` add imports `dev.jasper.sdk.Subscription`, `java.util.concurrent.CompletableFuture`, `java.util.function.Supplier`, and after `onTabsClosing`:

```java
    /**
     * Registers a guard asked when the user quits or restarts Jasper, after plugin windows' and content tabs' guards
     * and before anything closes. Guards are asked one at a time; one whose future completes with {@code false}
     * cancels the quit. A guard that throws, returns {@code null} or whose future fails allows the quit. The guard
     * runs on the UI thread and may show a modal dialog before completing its future.
     *
     * @param guard returns the decision, possibly after asking the user
     * @return the registration; closing it removes the guard
     * @throws IllegalStateException when the plugin's context is closed or the caller is off the UI thread
     * @throws UnsupportedOperationException if the host predates quit guards
     * @since 0.11.0
     */
    default Subscription onQuitting(Supplier<CompletableFuture<Boolean>> guard) {
        throw new UnsupportedOperationException("Quit guards are unavailable");
    }
```

- [ ] **Step 4: Implement it in the testkit fake**

In `FakeUi.java`, next to `contentGuard`, add the field:

```java
    final List<java.util.function.Supplier<java.util.concurrent.CompletableFuture<Boolean>>> quitGuards = new ArrayList<>();
```

In `closeAll()` add `quitGuards.clear();` as the first statement. In the anonymous `Windows` (after `onTabsClosing`) add:

```java
            @Override public Subscription onQuitting(java.util.function.Supplier<java.util.concurrent.CompletableFuture<Boolean>> guard) {
                context.requireOpen();
                java.util.Objects.requireNonNull(guard, "guard");
                quitGuards.add(guard);
                return () -> quitGuards.remove(guard);
            }
```

Replace `FakePluginHost.requestQuit()` (lines 690-703) with:

```java
    /**
     * Asks the closing guards of every open plugin window, then every quit guard, as quitting or restarting Jasper
     * does, and closes nothing. Dialogs and overlays are not asked. A quit guard whose future is still pending counts
     * as a refusal here: the fake does not pump events.
     *
     * @return whether the quit may go ahead
     * @since 0.8.4; quit guards since 0.11.0
     */
    public boolean requestQuit() {
        for (FakePluginContext context : contexts.values()) if (!context.ui.closeContent(List.copyOf(context.ui.contents), false).getNow(false)) return false;
        for (FakePluginContext context : contexts.values())
            for (FakeUi.FakeWindow window : List.copyOf(context.ui.windows))
                if (!window.id.equals("dialog") && !window.id.equals("overlay") && !window.mayClose()) return false;
        for (FakePluginContext context : contexts.values())
            for (var guard : List.copyOf(context.ui.quitGuards)) {
                java.util.concurrent.CompletableFuture<Boolean> decision;
                try { decision = guard.get(); }
                catch (RuntimeException failure) { recordFailure(context.plugin().id() + " quit guard: " + failure); continue; }
                if (decision == null) continue;
                if (!decision.handle((allowed, error) -> error != null || !Boolean.FALSE.equals(allowed)).getNow(false)) return false;
            }
        return true;
    }
```

- [ ] **Step 5: Run the testkit tests**

Run: `./gradlew :jasper-sdk-testkit:test`
Expected: PASS.

- [ ] **Step 6: Write the failing app tests**

Create `jasper-app/src/test/java/dev/jasper/app/contributions/QuitGuardsTest.java`:

```java
package dev.jasper.app.contributions;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class QuitGuardsTest {
    private final QuitGuards guards = new QuitGuards();
    private final List<String> asked = new ArrayList<>();

    @Test void asksInRegistrationOrderAndStopsAtTheFirstRefusal() {
        guards.add(() -> { asked.add("first"); return CompletableFuture.completedFuture(true); });
        var second = guards.add(() -> { asked.add("second"); return CompletableFuture.completedFuture(false); });
        guards.add(() -> { asked.add("third"); return CompletableFuture.completedFuture(true); });
        assertThat(guards.mayQuit()).isFalse();
        assertThat(asked).containsExactly("first", "second");
        second.close();
        asked.clear();
        assertThat(guards.mayQuit()).isTrue();
        assertThat(asked).containsExactly("first", "third");
    }

    @Test void waitsForAPendingDecisionWhilePumpingEvents() {
        var decision = new CompletableFuture<Boolean>();
        guards.add(() -> { SwingUtilities.invokeLater(() -> decision.complete(false)); return decision; });
        assertThat(guards.mayQuit()).isFalse();
    }

    @Test void aNestedAskRefuses() {
        boolean[] nested = {true};
        guards.add(() -> { nested[0] = guards.mayQuit(); return CompletableFuture.completedFuture(true); });
        assertThat(guards.mayQuit()).isTrue();
        assertThat(nested[0]).isFalse();
    }
}
```

In `JasperApplicationPluginsTest.java` add a fixture and a test:

```java
    private static final String QUIT_FIXTURE = """
        package fix.quit;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import java.nio.file.Files;
        import java.util.concurrent.CompletableFuture;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) {
                context.windows().onQuitting(() -> {
                    try { Files.writeString(context.dataDirectory().resolve("asked"), "yes"); }
                    catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    return CompletableFuture.completedFuture(Files.exists(context.dataDirectory().resolve("allow")));
                });
            }
        }
        """;

    @Test void quitAndRestartAskQuitGuardsAndARefusalKeepsJasperRunning() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("dev-plugin");
        PluginJars.build(dev, "quit.jar", PluginJars.descriptor("dev.example.quit", "1.0.0", "fix.quit.Main"),
            Map.of("fix.quit.Main", QUIT_FIXTURE), List.of());
        Path data = dirs.plugins().resolve("dev.example.quit/data");
        List<String> spawned = new CopyOnWriteArrayList<>();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].restartPlanner = mode -> Optional.of(List.of("jasper"));
            application[0].spawner = command -> spawned.add(String.join(" ", command));
            application[0].startPlugins(null, dev, false, dirs);
        });
        edt(application[0]::quit);
        edt(() -> assertThat(application[0].restart(RestartMode.SAME)).as("handled: the guard said no").isTrue());
        edt(() -> { });
        assertThat(data.resolve("asked")).exists();
        assertThat(terminated.getCount()).as("still running").isEqualTo(1);
        java.nio.file.Files.writeString(data.resolve("allow"), "yes");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(spawned).as("the refused restart left no relaunch behind").isEmpty();
    }
```

- [ ] **Step 7: Run them to see them fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.contributions.QuitGuardsTest' --tests 'dev.jasper.app.application.JasperApplicationPluginsTest' --tests 'dev.jasper.app.plugins.AppContractTest'`
Expected: FAIL — `QuitGuards` does not exist; the app's `Windows` throws `UnsupportedOperationException`.

- [ ] **Step 8: Implement `QuitGuards` and wire it**

Create `jasper-app/src/main/java/dev/jasper/app/contributions/QuitGuards.java`:

```java
package dev.jasper.app.contributions;

import dev.jasper.app.lifecycle.Subscription;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * Plugins' quit and restart guards, asked in registration order after plugin windows and content tabs, before
 * anything closes. EDT-only. Callers contain their guards: a supplier here never throws and its future never fails.
 */
public final class QuitGuards {
    private final List<Supplier<CompletableFuture<Boolean>>> guards = new ArrayList<>();
    private boolean asking;

    public Subscription add(Supplier<CompletableFuture<Boolean>> guard) {
        requireEdt();
        guards.add(Objects.requireNonNull(guard, "guard"));
        return new Subscription(() -> guards.remove(guard));
    }

    /**
     * Asks each guard in turn, pumping UI events while a decision is pending; the first refusal stops. An ask made
     * while one is already running (a guard's dialog quitting again) refuses.
     *
     * @return whether the quit may go ahead
     */
    public boolean mayQuit() {
        requireEdt();
        if (asking) return false;
        asking = true;
        try {
            for (var guard : List.copyOf(guards)) if (!await(guard.get())) return false;
            return true;
        } finally {
            asking = false;
        }
    }

    private static boolean await(CompletableFuture<Boolean> future) {
        if (!future.isDone()) {
            var loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
            future.whenComplete((value, error) -> SwingUtilities.invokeLater(loop::exit));
            loop.enter();
        }
        return future.getNow(false);
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Quit guards belong to the EDT");
    }
}
```

In `Contributions.java`, after `content()`:

```java
    private final QuitGuards quitGuards = new QuitGuards();
    public QuitGuards quitGuards() { return quitGuards; }
```

In `HostedUi.windows()`, after `onTabsClosing`:

```java
            @Override public Subscription onQuitting(Supplier<java.util.concurrent.CompletableFuture<Boolean>> guard) {
                HostedUi.this.guard("quit guard");
                java.util.Objects.requireNonNull(guard, "guard");
                var registration = model.quitGuards().add(() -> {
                    var result = new java.util.concurrent.CompletableFuture<Boolean>();
                    boolean invoked = containment.run(pluginId, "quit guard", () -> {
                        var decision = guard.get();
                        if (decision == null) { result.complete(true); return; }
                        decision.whenComplete((allowed, error) -> result.complete(error != null || !Boolean.FALSE.equals(allowed)));
                    });
                    if (!invoked) result.complete(true);
                    return result;
                });
                return tracked(registration::close);
            }
```

In `JasperApplication.pluginWindowsMayClose()` (line 796):

```java
    /** Plugin windows', content tabs' and quit guards, asked before quit or restart changes anything and before the exit deadline is armed. */
    private boolean pluginWindowsMayClose() {
        var requested = contributions.content().entries();
        return contributions.content().mayClose(requested) && (auxiliary == null || auxiliary.mayQuit())
            && requested.equals(contributions.content().entries()) && contributions.quitGuards().mayQuit();
    }
```

In `AppContractTest.java` line 250:

```java
            @Override public boolean requestQuit() { return onEdtValue(() -> contributions.content().mayClose(contributions.content().entries()) && auxiliary.mayQuit() && contributions.quitGuards().mayQuit()); }
```

- [ ] **Step 9: Run the app tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.contributions.*' --tests 'dev.jasper.app.application.JasperApplicationPluginsTest' --tests 'dev.jasper.app.plugins.*'`
Expected: PASS.

- [ ] **Step 10: Document it**

`jasper-sdk/README.md`, extend the SDK 0.11.0 paragraph with: `` `Windows.onQuitting` registers a guard asked when Jasper quits or restarts, after plugin windows and content tabs; a `false` decision cancels, and a guard that throws or fails allows the quit. `FakePluginHost.requestQuit` asks these guards too. ``

`docs/plugin-authoring.md`, after the `WindowSurface.onClosing` bullet (line 216) add:

```markdown
- `Windows.onQuitting` (SDK 0.11.0) registers a guard asked when Jasper quits or restarts, after plugin windows and
  content tabs, even when your plugin has no window: a background server can ask "stop the running servers?".
  Complete the future with false to keep Jasper running; a guard that throws or fails allows the quit.
```

`docs/sdk-architecture.md`, at the end of the quit paragraph (line 341) add: `Since SDK 0.11.0 the same check then asks `Windows.onQuitting` guards, kept in `Contributions.quitGuards()` (`QuitGuards`), one at a time, pumping UI events while a decision is pending; a refusal cancels, and a guard that throws or fails allows the quit.`

- [ ] **Step 11: Run the SDK, testkit and documentation checks**

Run: `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check :jasper-app:test --tests 'dev.jasper.app.documentation.*'`
Expected: PASS.

- [ ] **Step 12: Commit**

```bash
git add jasper-sdk jasper-sdk-testkit jasper-app docs/plugin-authoring.md docs/sdk-architecture.md
git commit -m "SDK 0.11.0: Windows.onQuitting quit guards

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Plugin skeleton, build wiring and bundling

**Files:**
- Create: `plugins/fileserver/build.gradle.kts`
- Create: `plugins/fileserver/src/main/resources/plugin.toml`, `settings.toml`, `icons/fileserver.svg`
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java`, `FileServerSettings.java`, `package-info.java`
- Create: `plugins/fileserver/src/test/java/dev/jasper/fileserver/FileServerPluginTest.java`
- Modify: `gradle/plugin-architecture.gradle.kts:10` and the `jdk()` helper
- Modify: `jasper-app/build.gradle.kts` (`stagePlugins`)
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginZipsTest.java`, `BundledSamplePluginTest.java`

**Interfaces:**
- Produces: module `:jasper-plugin-fileserver`; `FileServerPlugin` (public no-arg constructor); `FileServerSettings` with fields `Setting<Boolean> confirmQuit` (key `confirm_quit`, default true) and `Setting<Integer> activityEntries` (key `activity_entries`, 50–5000, default 500).

- [ ] **Step 1: Create the module**

`plugins/fileserver/build.gradle.kts`:

```kotlin
// A plugin compiles against the SDK and the exported API of plugins it requires (the Credential Vault, optional);
// the application supplies both at run time. Bundled: tomlj for servers.toml. HTTP uses the JDK's jdk.httpserver
// module. stagePlugins copies the runtime classpath beside the plugin jar, and PluginClassLoader loads it.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    compileOnly(project(":jasper-plugin-vault"))
    implementation("org.tomlj:tomlj:1.1.1")
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
    testImplementation(project(":jasper-plugin-vault"))
}
```

`plugins/fileserver/src/main/resources/plugin.toml`:

```toml
id = "dev.jasper.fileserver"
name = "File Server"
version = "0.1.0"
entry = "dev.jasper.fileserver.FileServerPlugin"
sdk = ">=0.11.0, <0.12"
description = "Share a local folder with other computers over HTTP: named servers in a File Servers panel, read-only and local-only by default, with an activity log, Vault logins and Serve Current Folder."
vendor = "Jasper"
icon = "icons/fileserver.svg"
homepage = "https://github.com/an0nn30/Jasper"
capabilities = ["network.listen", "terminal.observe", "palette.contribute"]
requires = [{ id = "dev.jasper.vault", version = ">=0.2", optional = true }]
```

`plugins/fileserver/src/main/resources/settings.toml`:

```toml
# Settings for File Server. Change them in Jasper's Settings or here. Saved servers live in data/servers.toml.
# confirm_quit = true           # ask before quitting or restarting Jasper while servers run
# activity_entries = 500        # 50 to 5000 activity entries kept per server; applies to servers started afterwards
```

`plugins/fileserver/src/main/resources/icons/fileserver.svg`:

```svg
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16" fill="none">
  <rect x="2.5" y="2.5" width="11" height="4" rx="1" stroke="#6C707E"/>
  <rect x="2.5" y="9.5" width="11" height="4" rx="1" stroke="#6C707E"/>
  <circle cx="5" cy="4.5" r="0.75" fill="#6C707E"/>
  <circle cx="5" cy="11.5" r="0.75" fill="#6C707E"/>
  <path d="M9 4.5h2.5M9 11.5h2.5" stroke="#6C707E" stroke-linecap="round"/>
</svg>
```

- [ ] **Step 2: Write the failing plugin test**

`plugins/fileserver/src/test/java/dev/jasper/fileserver/FileServerPluginTest.java`:

```java
package dev.jasper.fileserver;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FileServerPluginTest {
    static final PluginInfo INFO = new PluginInfo("dev.jasper.fileserver", "File Server", "0.1.0",
        Set.of(Capabilities.NETWORK_LISTEN, Capabilities.TERMINAL_OBSERVE, Capabilities.PALETTE_CONTRIBUTE));

    @Test void startsAndDeclaresItsSettings() {
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), new FileServerPlugin());
            assertThat(host.active("dev.jasper.fileserver")).isTrue();
            assertThat(host.declared("dev.jasper.fileserver"))
                .anySatisfy(line -> assertThat(line).startsWith("confirm_quit"))
                .anySatisfy(line -> assertThat(line).startsWith("activity_entries"));
        }
    }
}
```

- [ ] **Step 3: Run it to see it fail**

Run: `./gradlew :jasper-plugin-fileserver:test`
Expected: FAIL — `FileServerPlugin` does not exist.

- [ ] **Step 4: Write the skeleton**

`plugins/fileserver/src/main/java/dev/jasper/fileserver/package-info.java`:

```java
/**
 * The File Server plugin: {@link dev.jasper.fileserver.FileServerPlugin} wires saved servers, the registry, the
 * engines and the File Servers panel to the SDK. May depend on every package of the plugin, the SDK and, optionally,
 * {@code dev.jasper.vault.api}.
 */
package dev.jasper.fileserver;
```

`FileServerSettings.java`:

```java
package dev.jasper.fileserver;

import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;

/** File Server's declared settings, stored in {@code plugins/dev.jasper.fileserver/dev.jasper.fileserver.toml}. */
final class FileServerSettings {
    final Setting<Boolean> confirmQuit;
    final Setting<Integer> activityEntries;

    FileServerSettings(PluginSettings settings) {
        confirmQuit = settings.declare(SettingSpec.of("confirm_quit", SettingKind.bool(), true)
            .label("Ask before quitting while servers run")
            .description("Quitting or restarting Jasper stops every file server.")
            .section("File Servers").keywords("quit", "server", "share"));
        activityEntries = settings.declare(SettingSpec.of("activity_entries", SettingKind.integer(50, 5000), 500)
            .label("Activity entries kept per server")
            .description("Older entries are dropped. Applies to servers started afterwards.")
            .section("File Servers").keywords("log", "activity", "server"));
    }
}
```

`FileServerPlugin.java` (Task 14 replaces this file):

```java
package dev.jasper.fileserver;

import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import java.nio.file.Files;

/** The File Server plugin: shares local folders with other computers from the File Servers panel. */
public final class FileServerPlugin implements Plugin {
    private FileServerSettings settings;

    public FileServerPlugin() { }

    @Override public void start(PluginContext context) throws Exception {
        Files.createDirectories(context.dataDirectory());
        settings = new FileServerSettings(context.settings());
    }
}
```

- [ ] **Step 5: Wire the build**

`gradle/plugin-architecture.gradle.kts`, line 10:

```kotlin
val declaredImports = mapOf(":jasper-plugin-history" to listOf("dev.jasper.snippets.api"), ":jasper-plugin-remote" to listOf("dev.jasper.vault.api", "dev.jasper.editor.api"), ":jasper-plugin-fileserver" to listOf("dev.jasper.vault.api"))
```

and the `jdk()` helper (the JDK's HTTP server is exported API of module `jdk.httpserver`):

```kotlin
        fun jdk(name: String) = name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.") || name.startsWith("com.sun.net.httpserver.")
```

`jasper-app/build.gradle.kts`, inside `stagePlugins` after the editor lines:

```kotlin
    from(project(":jasper-plugin-fileserver").tasks.named("jar")) { into("dev.jasper.fileserver") }
    from(project(":jasper-plugin-fileserver").configurations.named("runtimeClasspath")) { into("dev.jasper.fileserver") }
```

`PluginZipsTest.java`: `hasSize(7)`; add a branch before the final `else`:

```java
            else if (descriptor.id().equals("dev.jasper.fileserver"))
                assertThat(jars).as("tomlj travels with the file server").contains("tomlj-1.1.1.jar");
```

and add `"dev.jasper.fileserver"` to the `containsExactlyInAnyOrder` list.

`BundledSamplePluginTest.java`: in the first test change `.as("the sample and the five bundled feature plugins").hasSize(6)` to `.as("the sample and the six bundled feature plugins").hasSize(7)` and add `.anySatisfy(line -> assertThat(line).contains("dev.jasper.fileserver", "BUNDLED", "ACTIVE"))`; in `everyBundledPluginShipsAnIconAndRemoteNamesItsHomepage` change `hasSize(6)` to `hasSize(7)`.

- [ ] **Step 6: Run the checks**

Run: `./gradlew :jasper-plugin-fileserver:check :jasper-app:test --tests 'dev.jasper.app.plugins.PluginZipsTest' --tests 'dev.jasper.app.plugins.BundledSamplePluginTest'`
Expected: PASS, including `verifyPluginArchitecture`.

- [ ] **Step 7: Commit**

```bash
git add plugins/fileserver gradle/plugin-architecture.gradle.kts jasper-app/build.gradle.kts jasper-app/src/test/java/dev/jasper/app/plugins
git commit -m "File Server plugin skeleton, bundled with the app

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: `ShareRoot`, the security boundary

**Files:**
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/share/{ShareRoot,ShareEntry,PathRefusedException,ReadOnlyShareException,IsFolderException,package-info}.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/share/ShareRootTest.java`

**Interfaces:**
- Produces (all in `dev.jasper.fileserver.share`):
  - `ShareRoot(Path folder, boolean readOnly) throws IOException`; `Path root()`; `boolean readOnly()`
  - `static List<String> segments(String clientPath) throws PathRefusedException` — `/` or `\` separated; `.` dropped; `..` applied; refusal when climbing above the root or for unshareable names
  - `Path resolve(String clientPath) throws IOException` — follows links, stays inside
  - `ShareEntry attributes(String)`, `List<ShareEntry> list(String)` (folders first, then names ignoring case; omits links leaving the share and unshareable names)
  - `SeekableByteChannel openRead(String)`, `OutputStream openWrite(String, boolean append)`, `void mkdir(String)`, `void delete(String)`, `void rename(String from, String to)` — writes never follow a final link
  - `record ShareEntry(String name, boolean directory, long size, Instant modified)`
  - `PathRefusedException extends IOException`, `ReadOnlyShareException extends IOException` ("This share is read-only"), `IsFolderException extends FileSystemException`

- [ ] **Step 1: Write the failing tests**

`plugins/fileserver/src/test/java/dev/jasper/fileserver/share/ShareRootTest.java`:

```java
package dev.jasper.fileserver.share;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.abort;

class ShareRootTest {
    @TempDir Path temp;
    Path shared, outside;

    @BeforeEach void layout() throws IOException {
        shared = Files.createDirectories(temp.resolve("shared"));
        outside = Files.createDirectories(temp.resolve("outside"));
        Files.createDirectories(shared.resolve("docs"));
        Files.writeString(shared.resolve("docs/a.txt"), "hello");
        Files.writeString(outside.resolve("secret.txt"), "secret");
    }

    static void link(Path link, Path target) {
        try { Files.createSymbolicLink(link, target); }
        catch (UnsupportedOperationException | IOException | SecurityException refused) { abort("symbolic links are not available: " + refused); }
    }

    @Test void resolvesInsideAndRefusesEveryClimbOut() throws IOException {
        var share = new ShareRoot(shared, true);
        Path real = shared.toRealPath();
        assertThat(share.root()).isEqualTo(real);
        assertThat(share.resolve("")).isEqualTo(real);
        assertThat(share.resolve("/")).isEqualTo(real);
        assertThat(share.resolve("/docs/a.txt")).isEqualTo(real.resolve("docs/a.txt"));
        assertThat(share.resolve("docs/./../docs//a.txt")).isEqualTo(real.resolve("docs/a.txt"));
        assertThat(share.resolve("docs\\a.txt")).as("a backslash separates too").isEqualTo(real.resolve("docs/a.txt"));
        for (String escape : new String[]{"..", "/../outside/secret.txt", "docs/../../outside", "docs\\..\\..\\outside", "\\\\server\\share"})
            assertThatThrownBy(() -> share.resolve(escape)).as(escape).isInstanceOf(PathRefusedException.class);
        assertThat(ShareRoot.segments("/a/b/../c/")).containsExactly("a", "c");
    }

    @Test void refusesNamesNoPlatformCanShareSafely() throws IOException {
        for (String bad : new String[]{"a:b", "C:", "CON", "con.txt", "Lpt1", "aux .log", "name.", "name ", "tab\tname", "nul\0", "del\177"})
            assertThatThrownBy(() -> ShareRoot.segments("docs/" + bad)).as(bad).isInstanceOf(PathRefusedException.class);
        assertThat(ShareRoot.segments(".hidden/console/com10/lpt.txt")).containsExactly(".hidden", "console", "com10", "lpt.txt");
    }

    @Test void readsAttributesAndListsFoldersFirst() throws IOException {
        Files.writeString(shared.resolve("B.txt"), "bb");
        Files.writeString(shared.resolve("a.txt"), "a");
        Files.createDirectories(shared.resolve("zeta"));
        var share = new ShareRoot(shared, true);
        assertThat(share.list("")).extracting(ShareEntry::name).containsExactly("docs", "zeta", "a.txt", "B.txt");
        ShareEntry file = share.attributes("docs/a.txt");
        assertThat(file.directory()).isFalse();
        assertThat(file.size()).isEqualTo(5);
        assertThat(share.attributes("docs").directory()).isTrue();
        assertThatThrownBy(() -> share.attributes("missing.txt")).isInstanceOf(NoSuchFileException.class);
        try (SeekableByteChannel channel = share.openRead("docs/a.txt")) {
            var buffer = ByteBuffer.allocate(16);
            channel.position(1);
            channel.read(buffer);
            assertThat(new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8)).isEqualTo("ello");
        }
        assertThatThrownBy(() -> share.openRead("docs")).isInstanceOf(IsFolderException.class);
    }

    @Test void linksLeavingTheShareAreRefusedAndHidden() throws IOException {
        link(shared.resolve("out-file"), outside.resolve("secret.txt"));
        link(shared.resolve("out-dir"), outside);
        link(shared.resolve("chain"), shared.resolve("out-file"));
        link(shared.resolve("dangling"), temp.resolve("nowhere"));
        link(shared.resolve("inward"), shared.resolve("docs/a.txt"));
        var share = new ShareRoot(shared, false);
        for (String refused : new String[]{"out-file", "out-dir/secret.txt", "chain", "dangling", "out-dir/new.txt"})
            assertThatThrownBy(() -> share.resolve(refused)).as(refused).isInstanceOf(PathRefusedException.class);
        assertThatThrownBy(() -> share.openWrite("out-dir/new.txt", false)).isInstanceOf(PathRefusedException.class);
        assertThat(share.resolve("inward")).isEqualTo(shared.toRealPath().resolve("docs/a.txt"));
        assertThat(share.list("")).extracting(ShareEntry::name).containsExactly("docs", "inward");
        share.delete("inward");
        assertThat(shared.resolve("docs/a.txt")).as("deleting a link removes the link, not its target").exists();
    }

    @Test void aReadOnlyShareRefusesEveryWrite() throws IOException {
        var share = new ShareRoot(shared, true);
        assertThatThrownBy(() -> share.openWrite("new.txt", false)).isInstanceOf(ReadOnlyShareException.class);
        assertThatThrownBy(() -> share.mkdir("new")).isInstanceOf(ReadOnlyShareException.class);
        assertThatThrownBy(() -> share.delete("docs/a.txt")).isInstanceOf(ReadOnlyShareException.class);
        assertThatThrownBy(() -> share.rename("docs/a.txt", "b.txt")).isInstanceOf(ReadOnlyShareException.class);
        assertThat(shared.resolve("docs/a.txt")).hasContent("hello");
    }

    @Test void aWritableShareCreatesAppendsRenamesAndDeletes() throws IOException {
        var share = new ShareRoot(shared, false);
        try (OutputStream out = share.openWrite("docs/new.txt", false)) { out.write("one".getBytes(StandardCharsets.UTF_8)); }
        try (OutputStream out = share.openWrite("docs/new.txt", true)) { out.write("two".getBytes(StandardCharsets.UTF_8)); }
        assertThat(shared.resolve("docs/new.txt")).hasContent("onetwo");
        share.mkdir("made");
        share.rename("docs/new.txt", "made/moved.txt");
        assertThat(shared.resolve("made/moved.txt")).hasContent("onetwo");
        assertThatThrownBy(() -> share.delete("made")).isInstanceOf(DirectoryNotEmptyException.class);
        share.delete("made/moved.txt");
        share.delete("made");
        assertThat(shared.resolve("made")).doesNotExist();
        assertThatThrownBy(() -> share.openWrite("docs", false)).isInstanceOf(IsFolderException.class);
        assertThatThrownBy(() -> share.delete("")).isInstanceOf(PathRefusedException.class);
        assertThatThrownBy(() -> share.rename("", "x")).isInstanceOf(PathRefusedException.class);
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.share.ShareRootTest'`
Expected: FAIL — the package does not exist.

- [ ] **Step 3: Write the exceptions and the entry**

`share/package-info.java`:

```java
/**
 * The shared folder as the engines see it: {@link dev.jasper.fileserver.share.ShareRoot} keeps every client path
 * inside the folder and enforces read-only. JDK only; depends on no other package of the plugin.
 */
package dev.jasper.fileserver.share;
```

`share/ShareEntry.java`:

```java
package dev.jasper.fileserver.share;

import java.time.Instant;

/** One file or folder in a share; a folder's size is 0. */
public record ShareEntry(String name, boolean directory, long size, Instant modified) { }
```

`share/PathRefusedException.java`:

```java
package dev.jasper.fileserver.share;

import java.io.IOException;

/** A client path that would leave the share or names something no platform can share safely. */
public final class PathRefusedException extends IOException {
    public PathRefusedException(String message) { super(message); }
}
```

`share/ReadOnlyShareException.java`:

```java
package dev.jasper.fileserver.share;

import java.io.IOException;

/** A write to a read-only share. */
public final class ReadOnlyShareException extends IOException {
    public ReadOnlyShareException() { super("This share is read-only"); }
}
```

`share/IsFolderException.java`:

```java
package dev.jasper.fileserver.share;

import java.nio.file.FileSystemException;

/** A file operation on a folder. */
public final class IsFolderException extends FileSystemException {
    public IsFolderException(String clientPath) { super(clientPath, null, "Is a folder"); }
}
```

- [ ] **Step 4: Write `ShareRoot`**

`share/ShareRoot.java`:

```java
package dev.jasper.fileserver.share;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One shared folder; every engine reaches the disk only through it. Client paths are {@code /}-separated (a
 * backslash separates too) and relative to the share. They never leave it, through {@code ..} or a symbolic link,
 * and every write is refused when the share is read-only. Reads follow links that stay inside; writes, deletes and
 * renames act on the named entry itself and never through a final link. Holds no mutable state.
 *
 * <p>Out of scope: a local user who can already write inside the share swapping a checked folder for a link between
 * the check and the open.
 */
public final class ShareRoot {
    private static final Set<String> RESERVED = Set.of("CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");
    private final Path root;
    private final boolean readOnly;

    public ShareRoot(Path folder, boolean readOnly) throws IOException {
        Path real = folder.toRealPath();
        if (!Files.isDirectory(real)) throw new NotDirectoryException(folder.toString());
        this.root = real;
        this.readOnly = readOnly;
    }

    public Path root() { return root; }
    public boolean readOnly() { return readOnly; }

    /**
     * The names a client path walks through after {@code .} and {@code ..} are applied.
     *
     * @throws PathRefusedException when the path climbs above the share or a name cannot be shared on every platform
     */
    public static List<String> segments(String clientPath) throws PathRefusedException {
        if (clientPath.startsWith("\\\\")) throw new PathRefusedException("Network paths are not allowed");
        var result = new ArrayList<String>();
        for (String segment : clientPath.split("[/\\\\]", -1)) {
            if (segment.isEmpty() || segment.equals(".")) continue;
            if (segment.equals("..")) {
                if (result.isEmpty()) throw new PathRefusedException("The path leaves the shared folder");
                result.removeLast();
                continue;
            }
            checkName(segment);
            result.add(segment);
        }
        return List.copyOf(result);
    }

    static void checkName(String name) throws PathRefusedException {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c == 0x7f) throw new PathRefusedException("The name contains a control character");
            if (c == ':') throw new PathRefusedException("The name contains a colon");
        }
        char last = name.charAt(name.length() - 1);
        if (last == '.' || last == ' ') throw new PathRefusedException("A name cannot end with a dot or a space");
        int dot = name.indexOf('.');
        String stem = (dot < 0 ? name : name.substring(0, dot)).stripTrailing().toUpperCase(Locale.ROOT);
        if (RESERVED.contains(stem)) throw new PathRefusedException(name + " is a reserved device name");
    }

    /** The real path a client path names, following links that stay inside; a missing target's parent must exist inside. */
    public Path resolve(String clientPath) throws IOException {
        List<String> segments = segments(clientPath);
        if (segments.isEmpty()) return root;
        Path candidate = root;
        for (String segment : segments) candidate = candidate.resolve(segment);
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            Path real;
            try { real = candidate.toRealPath(); }
            catch (NoSuchFileException dangling) { throw new PathRefusedException("The link points nowhere"); }
            return inside(real);
        }
        Path parent;
        try { parent = candidate.getParent().toRealPath(); }
        catch (NoSuchFileException missing) { throw new NoSuchFileException(clientPath); }
        return inside(parent).resolve(segments.getLast());
    }

    /** The entry itself: its parent resolved, its own name not followed. The root for an empty path. */
    private Path entry(String clientPath) throws IOException {
        List<String> segments = segments(clientPath);
        if (segments.isEmpty()) return root;
        Path parent = resolve(String.join("/", segments.subList(0, segments.size() - 1)));
        return parent.resolve(segments.getLast());
    }

    private Path inside(Path real) throws PathRefusedException {
        if (!real.startsWith(root)) throw new PathRefusedException("The path leaves the shared folder");
        return real;
    }

    public ShareEntry attributes(String clientPath) throws IOException {
        Path path = resolve(clientPath);
        var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return entry(path.equals(root) ? "" : path.getFileName().toString(), attributes);
    }

    /** A folder's entries, folders first, then by name ignoring case; unshareable names and outward links are left out. */
    public List<ShareEntry> list(String clientPath) throws IOException {
        Path directory = resolve(clientPath);
        var entries = new ArrayList<ShareEntry>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                String name = child.getFileName().toString();
                try { checkName(name); } catch (PathRefusedException unshareable) { continue; }
                Path real;
                try { real = child.toRealPath(); } catch (IOException dangling) { continue; }
                if (!real.startsWith(root)) continue;
                try { entries.add(entry(name, Files.readAttributes(real, BasicFileAttributes.class))); }
                catch (IOException vanished) { /* removed while listing */ }
            }
        }
        entries.sort(Comparator.comparing((ShareEntry e) -> !e.directory())
            .thenComparing(e -> e.name().toLowerCase(Locale.ROOT)).thenComparing(ShareEntry::name));
        return List.copyOf(entries);
    }

    public SeekableByteChannel openRead(String clientPath) throws IOException {
        Path path = resolve(clientPath);
        if (Files.isDirectory(path)) throw new IsFolderException(clientPath);
        return Files.newByteChannel(path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
    }

    public OutputStream openWrite(String clientPath, boolean append) throws IOException {
        requireWritable();
        Path path = entry(clientPath);
        if (path.equals(root) || Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IsFolderException(clientPath);
        return Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
            append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS);
    }

    public void mkdir(String clientPath) throws IOException {
        requireWritable();
        Files.createDirectory(entry(clientPath));
    }

    public void delete(String clientPath) throws IOException {
        requireWritable();
        Path path = entry(clientPath);
        if (path.equals(root)) throw new PathRefusedException("The shared folder itself cannot be deleted");
        Files.delete(path);
    }

    public void rename(String from, String to) throws IOException {
        requireWritable();
        Path source = entry(from), target = entry(to);
        if (source.equals(root) || target.equals(root)) throw new PathRefusedException("The shared folder itself cannot be renamed");
        Files.move(source, target);
    }

    private void requireWritable() throws ReadOnlyShareException {
        if (readOnly) throw new ReadOnlyShareException();
    }

    private static ShareEntry entry(String name, BasicFileAttributes attributes) {
        return new ShareEntry(name, attributes.isDirectory(), attributes.isDirectory() ? 0 : attributes.size(),
            attributes.lastModifiedTime().toInstant());
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.share.ShareRootTest'`
Expected: PASS (symlink tests are aborted, not failed, where links cannot be created).

- [ ] **Step 6: Commit**

```bash
git add plugins/fileserver/src
git commit -m "File Server: ShareRoot keeps every client path inside the shared folder

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Server definitions

**Files:**
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/definition/{Protocol,Bind,Login,PortRange,ServerDefinition,package-info}.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/definition/ServerDefinitionTest.java`

**Interfaces:**
- Produces (package `dev.jasper.fileserver.definition`):
  - `enum Protocol { HTTP, FTP, SFTP, TFTP }` with `String label()` ("HTTP"…), `String scheme()`/`token()` ("http"…), `int defaultPort()` (8080, 2121, 2222, 6969), `boolean allowsAnonymous()` (HTTP, FTP, TFTP), `boolean hasLogin()` (not TFTP), `boolean allowsTls()` (HTTP, FTP), `int suggestedPort(int requested)`, `static Protocol parse(String)`
  - `sealed interface Bind` with `record Loopback()`, `record All()`, `record Interface(String name)`, constants `LOOPBACK`, `ALL`, `String token()`, `String label()`, `boolean overlaps(Bind)`, `static Bind parse(String)`
  - `sealed interface Login` with `record Anonymous()`, `record Session()`, `record Vault(UUID credentialId)`, constants `ANONYMOUS`, `SESSION`, `String token()`, `static Login parse(String)`
  - `record PortRange(int first, int last)` with `static parse(String)`, `String token()`
  - `record ServerDefinition(UUID id, String name, Protocol protocol, Path folder, Bind bind, int port, Optional<PortRange> passiveRange, boolean writable, Login login, List<String> authorizedKeys, boolean tls, boolean requireTls, boolean autostart)` validating on construction (`IllegalArgumentException` with the user-facing message); `static create(String name, Protocol, Path folder)`; `copyAs(String name)`

- [ ] **Step 1: Write the failing tests**

`plugins/fileserver/src/test/java/dev/jasper/fileserver/definition/ServerDefinitionTest.java`:

```java
package dev.jasper.fileserver.definition;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static dev.jasper.fileserver.definition.Protocol.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerDefinitionTest {
    static final Path FOLDER = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath();

    static ServerDefinition definition(String name, Protocol protocol, Path folder, int port, Optional<PortRange> passive,
                                       Login login, List<String> keys, boolean tls, boolean requireTls) {
        return new ServerDefinition(UUID.randomUUID(), name, protocol, folder, Bind.LOOPBACK, port, passive, false, login, keys, tls, requireTls, false);
    }

    static void invalid(Supplier<ServerDefinition> build, String message) {
        assertThatThrownBy(build::get).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }

    @Test void createPicksSafeDefaultsForEachProtocol() {
        var http = ServerDefinition.create("  docs  ", HTTP, FOLDER);
        assertThat(http.name()).isEqualTo("docs");
        assertThat(http.bind()).isEqualTo(Bind.LOOPBACK);
        assertThat(http.port()).isEqualTo(8080);
        assertThat(http.writable()).isFalse();
        assertThat(http.login()).isEqualTo(Login.ANONYMOUS);
        assertThat(http.autostart()).isFalse();
        assertThat(ServerDefinition.create("f", FTP, FOLDER)).satisfies(d -> { assertThat(d.port()).isEqualTo(2121); assertThat(d.login()).isEqualTo(Login.SESSION); });
        assertThat(ServerDefinition.create("s", SFTP, FOLDER)).satisfies(d -> { assertThat(d.port()).isEqualTo(2222); assertThat(d.login()).isEqualTo(Login.SESSION); });
        assertThat(ServerDefinition.create("t", TFTP, FOLDER)).satisfies(d -> { assertThat(d.port()).isEqualTo(6969); assertThat(d.login()).isEqualTo(Login.ANONYMOUS); });
    }

    @Test void rejectsInvalidDefinitionsWithAUserFacingMessage() {
        Optional<PortRange> none = Optional.empty();
        invalid(() -> definition(" ", HTTP, FOLDER, 8080, none, Login.ANONYMOUS, List.of(), false, false), "needs a name");
        invalid(() -> definition("x".repeat(81), HTTP, FOLDER, 8080, none, Login.ANONYMOUS, List.of(), false, false), "at most 80");
        invalid(() -> definition("a", HTTP, Path.of("relative"), 8080, none, Login.ANONYMOUS, List.of(), false, false), "absolute");
        invalid(() -> definition("a", HTTP, FOLDER, 0, none, Login.ANONYMOUS, List.of(), false, false), "1 to 65535");
        invalid(() -> definition("a", HTTP, FOLDER, 65536, none, Login.ANONYMOUS, List.of(), false, false), "1 to 65535");
        invalid(() -> definition("a", HTTP, FOLDER, 8080, Optional.of(new PortRange(5000, 5010)), Login.ANONYMOUS, List.of(), false, false), "Only FTP has a passive port range");
        invalid(() -> definition("a", HTTP, FOLDER, 8080, none, Login.ANONYMOUS, List.of("ssh-ed25519 AAAA"), false, false), "Only SFTP has authorized keys");
        invalid(() -> definition("a", SFTP, FOLDER, 2222, none, Login.SESSION, List.of("ssh-ed25519\nAAAA"), false, false), "one non-blank line");
        invalid(() -> definition("a", TFTP, FOLDER, 6969, none, Login.ANONYMOUS, List.of(), true, false), "Only HTTP and FTP use TLS");
        invalid(() -> definition("a", HTTP, FOLDER, 8080, none, Login.ANONYMOUS, List.of(), true, true), "Only FTP can require TLS");
        invalid(() -> definition("a", FTP, FOLDER, 2121, none, Login.SESSION, List.of(), false, true), "Require TLS needs Use TLS");
        invalid(() -> definition("a", TFTP, FOLDER, 6969, none, Login.SESSION, List.of(), false, false), "TFTP has no login");
        invalid(() -> definition("a", SFTP, FOLDER, 2222, none, Login.ANONYMOUS, List.of(), false, false), "SFTP needs a password or authorized keys");
    }

    @Test void anSftpServerMayUseKeysAlone() {
        var keys = definition("k", SFTP, FOLDER, 2222, Optional.empty(), Login.ANONYMOUS, List.of("ssh-ed25519 AAAA me"), false, false);
        assertThat(keys.authorizedKeys()).containsExactly("ssh-ed25519 AAAA me");
    }

    @Test void aCopyGetsANewIdAndNeverStartsWithJasper() {
        var original = new ServerDefinition(UUID.randomUUID(), "docs", HTTP, FOLDER, Bind.ALL, 8081, Optional.empty(), true, Login.SESSION, List.of(), false, false, true);
        var copy = original.copyAs("docs copy");
        assertThat(copy.id()).isNotEqualTo(original.id());
        assertThat(copy.name()).isEqualTo("docs copy");
        assertThat(copy.autostart()).isFalse();
        assertThat(copy.bind()).isEqualTo(Bind.ALL);
        assertThat(copy.writable()).isTrue();
    }

    @Test void tokensRoundTripAndBadOnesAreNamed() {
        for (Bind bind : List.of(Bind.LOOPBACK, Bind.ALL, new Bind.Interface("en0"))) assertThat(Bind.parse(bind.token())).isEqualTo(bind);
        UUID id = UUID.randomUUID();
        for (Login login : List.of(Login.ANONYMOUS, Login.SESSION, new Login.Vault(id))) assertThat(Login.parse(login.token())).isEqualTo(login);
        assertThat(Login.parse("vault:" + id)).isEqualTo(new Login.Vault(id));
        assertThat(Protocol.parse("HTTP")).isEqualTo(HTTP);
        assertThat(PortRange.parse(" 50000 - 50100 ").token()).isEqualTo("50000-50100");
        assertThatThrownBy(() -> Bind.parse("everywhere")).hasMessageContaining("'bind'");
        assertThatThrownBy(() -> Bind.parse("interface:")).hasMessageContaining("interface");
        assertThatThrownBy(() -> Login.parse("vault:nope")).hasMessageContaining("Vault account id");
        assertThatThrownBy(() -> Login.parse("guest")).hasMessageContaining("'login'");
        assertThatThrownBy(() -> Protocol.parse("smb")).hasMessageContaining("'protocol'");
        assertThatThrownBy(() -> PortRange.parse("50100-50000")).hasMessageContaining("lower to a higher");
        assertThatThrownBy(() -> PortRange.parse("many")).hasMessageContaining("50000-50100");
    }

    @Test void bindsOverlapWhenEitherTakesEveryAddress() {
        assertThat(Bind.ALL.overlaps(Bind.LOOPBACK)).isTrue();
        assertThat(Bind.LOOPBACK.overlaps(Bind.ALL)).isTrue();
        assertThat(Bind.LOOPBACK.overlaps(Bind.LOOPBACK)).isTrue();
        assertThat(Bind.LOOPBACK.overlaps(new Bind.Interface("en0"))).isFalse();
        assertThat(new Bind.Interface("en0").overlaps(new Bind.Interface("en0"))).isTrue();
        assertThat(new Bind.Interface("en0").overlaps(new Bind.Interface("en1"))).isFalse();
    }

    @Test void suggestsAnUnprivilegedPort() {
        assertThat(HTTP.suggestedPort(80)).isEqualTo(8080);
        assertThat(FTP.suggestedPort(21)).isEqualTo(8021);
        assertThat(HTTP.suggestedPort(9000)).isEqualTo(8080);
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.definition.ServerDefinitionTest'`
Expected: FAIL — the package does not exist.

- [ ] **Step 3: Write the values**

`definition/package-info.java`:

```java
/**
 * Saved servers: the validated {@link dev.jasper.fileserver.definition.ServerDefinition} and its parts, and
 * {@code servers.toml} ({@link dev.jasper.fileserver.definition.DefinitionFile},
 * {@link dev.jasper.fileserver.definition.DefinitionStore}). May depend on tomlj and the SDK; no secrets, no network.
 */
package dev.jasper.fileserver.definition;
```

`definition/Protocol.java`:

```java
package dev.jasper.fileserver.definition;

import java.util.Locale;

/** The protocols File Server knows; FS-1 serves HTTP only. */
public enum Protocol {
    HTTP("HTTP", "http", 8080), FTP("FTP", "ftp", 2121), SFTP("SFTP", "sftp", 2222), TFTP("TFTP", "tftp", 6969);

    private final String label, scheme;
    private final int defaultPort;

    Protocol(String label, String scheme, int defaultPort) { this.label = label; this.scheme = scheme; this.defaultPort = defaultPort; }

    public String label() { return label; }
    public String scheme() { return scheme; }
    public String token() { return scheme; }
    public int defaultPort() { return defaultPort; }
    /** HTTP and FTP may serve without a login; TFTP has none. */
    public boolean allowsAnonymous() { return this == HTTP || this == FTP || this == TFTP; }
    public boolean hasLogin() { return this != TFTP; }
    public boolean allowsTls() { return this == HTTP || this == FTP; }

    /** A port a normal user can bind: below 1024, 8000 higher (21 becomes 8021); otherwise this protocol's default. */
    public int suggestedPort(int requested) { return requested < 1024 ? requested + 8000 : defaultPort; }

    public static Protocol parse(String token) {
        String lower = token.strip().toLowerCase(Locale.ROOT);
        for (Protocol protocol : values()) if (protocol.scheme.equals(lower)) return protocol;
        throw new IllegalArgumentException("'protocol' must be \"http\", \"ftp\", \"sftp\" or \"tftp\"");
    }
}
```

`definition/Bind.java`:

```java
package dev.jasper.fileserver.definition;

/** Where a server listens: this computer only, every network, or one network interface. */
public sealed interface Bind {
    record Loopback() implements Bind { }
    record All() implements Bind { }
    record Interface(String name) implements Bind {
        public Interface {
            if (name == null || name.isBlank() || name.chars().anyMatch(Character::isWhitespace))
                throw new IllegalArgumentException("A network interface needs a name without spaces");
        }
    }

    Bind LOOPBACK = new Loopback();
    Bind ALL = new All();

    default String token() {
        return switch (this) {
            case Loopback loopback -> "loopback";
            case All all -> "all";
            case Interface named -> "interface:" + named.name();
        };
    }

    default String label() {
        return switch (this) {
            case Loopback loopback -> "This computer only";
            case All all -> "All networks";
            case Interface named -> "Interface " + named.name();
        };
    }

    /** Whether two servers bound this way would compete for the same port. */
    default boolean overlaps(Bind other) {
        return this instanceof All || other instanceof All || equals(other);
    }

    static Bind parse(String token) {
        String text = token.strip();
        if (text.equals("loopback")) return LOOPBACK;
        if (text.equals("all")) return ALL;
        if (text.startsWith("interface:")) return new Interface(text.substring("interface:".length()));
        throw new IllegalArgumentException("'bind' must be \"loopback\", \"all\" or \"interface:<name>\"");
    }
}
```

`definition/Login.java`:

```java
package dev.jasper.fileserver.definition;

import java.util.Objects;
import java.util.UUID;

/** How clients log in: not at all, with a password generated for each run, or with a Vault account. */
public sealed interface Login {
    record Anonymous() implements Login { }
    record Session() implements Login { }
    record Vault(UUID credentialId) implements Login {
        public Vault { Objects.requireNonNull(credentialId, "credentialId"); }
    }

    Login ANONYMOUS = new Anonymous();
    Login SESSION = new Session();

    default String token() {
        return switch (this) {
            case Anonymous anonymous -> "anonymous";
            case Session session -> "session";
            case Vault vault -> "vault:" + vault.credentialId();
        };
    }

    static Login parse(String token) {
        String text = token.strip();
        if (text.equals("anonymous")) return ANONYMOUS;
        if (text.equals("session")) return SESSION;
        if (text.startsWith("vault:")) {
            try { return new Vault(UUID.fromString(text.substring("vault:".length()))); }
            catch (IllegalArgumentException bad) { throw new IllegalArgumentException("'login' names no Vault account id"); }
        }
        throw new IllegalArgumentException("'login' must be \"anonymous\", \"session\" or \"vault:<id>\"");
    }
}
```

`definition/PortRange.java`:

```java
package dev.jasper.fileserver.definition;

/** An inclusive range of ports, such as FTP's passive data ports. */
public record PortRange(int first, int last) {
    public PortRange {
        if (first < 1 || last > 65535 || first > last)
            throw new IllegalArgumentException("A port range runs from a lower to a higher port, 1 to 65535");
    }

    public static PortRange parse(String text) {
        int dash = text.indexOf('-');
        if (dash < 0) throw new IllegalArgumentException("A port range looks like 50000-50100");
        try { return new PortRange(Integer.parseInt(text.substring(0, dash).strip()), Integer.parseInt(text.substring(dash + 1).strip())); }
        catch (NumberFormatException bad) { throw new IllegalArgumentException("A port range looks like 50000-50100"); }
    }

    public String token() { return first + "-" + last; }
}
```

`definition/ServerDefinition.java`:

```java
package dev.jasper.fileserver.definition;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One saved (or temporary) server. Construction validates every rule the spec states and throws
 * {@link IllegalArgumentException} with a message the editor can show.
 */
public record ServerDefinition(UUID id, String name, Protocol protocol, Path folder, Bind bind, int port,
                               Optional<PortRange> passiveRange, boolean writable, Login login, List<String> authorizedKeys,
                               boolean tls, boolean requireTls, boolean autostart) {
    public static final int MAX_NAME = 80;

    public ServerDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(bind, "bind");
        Objects.requireNonNull(passiveRange, "passiveRange");
        Objects.requireNonNull(login, "login");
        name = name == null ? "" : name.strip();
        if (name.isEmpty()) throw new IllegalArgumentException("A server needs a name");
        if (name.length() > MAX_NAME) throw new IllegalArgumentException("A server name is at most " + MAX_NAME + " characters");
        if (!folder.isAbsolute()) throw new IllegalArgumentException("The folder must be an absolute path");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("The port must be a number from 1 to 65535");
        if (passiveRange.isPresent() && protocol != Protocol.FTP) throw new IllegalArgumentException("Only FTP has a passive port range");
        authorizedKeys = List.copyOf(authorizedKeys);
        if (!authorizedKeys.isEmpty() && protocol != Protocol.SFTP) throw new IllegalArgumentException("Only SFTP has authorized keys");
        for (String key : authorizedKeys)
            if (key.isBlank() || key.contains("\n") || key.contains("\r")) throw new IllegalArgumentException("An authorized key is one non-blank line");
        if (tls && !protocol.allowsTls()) throw new IllegalArgumentException("Only HTTP and FTP use TLS");
        if (requireTls && protocol != Protocol.FTP) throw new IllegalArgumentException("Only FTP can require TLS");
        if (requireTls && !tls) throw new IllegalArgumentException("Require TLS needs Use TLS");
        if (protocol == Protocol.TFTP && !(login instanceof Login.Anonymous)) throw new IllegalArgumentException("TFTP has no login");
        if (protocol == Protocol.SFTP && login instanceof Login.Anonymous && authorizedKeys.isEmpty())
            throw new IllegalArgumentException("SFTP needs a password or authorized keys");
    }

    /** A new, read-only server on this computer only, at the protocol's default port. */
    public static ServerDefinition create(String name, Protocol protocol, Path folder) {
        Login login = protocol == Protocol.HTTP || protocol == Protocol.TFTP ? Login.ANONYMOUS : Login.SESSION;
        return new ServerDefinition(UUID.randomUUID(), name, protocol, folder, Bind.LOOPBACK, protocol.defaultPort(),
            Optional.empty(), false, login, List.of(), false, false, false);
    }

    /** The same settings under a new id and name; a copy never starts with Jasper. */
    public ServerDefinition copyAs(String newName) {
        return new ServerDefinition(UUID.randomUUID(), newName, protocol, folder, bind, port, passiveRange, writable, login,
            authorizedKeys, tls, requireTls, false);
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.definition.ServerDefinitionTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/fileserver/src
git commit -m "File Server: validated server definitions

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: `servers.toml`

**Files:**
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/definition/{DefinitionFile,DefinitionStore}.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/definition/{DefinitionFileTest,DefinitionStoreTest}.java`

**Interfaces:**
- Consumes: Task 5's values.
- Produces:
  - `DefinitionFile.HEADER`, `MAX_BYTES`, `record Parsed(List<ServerDefinition> definitions, List<String> warnings)`, `static Parsed parse(String) throws IOException`, `static String format(List<ServerDefinition>)`, `static String tomlString(String)`
  - `DefinitionStore(Path file, Executor background, Executor ui)`; `List<ServerDefinition> definitions()`; `Optional<ServerDefinition> definition(UUID)`; `Optional<String> error()`; `List<String> warnings()`; `Subscription onChanged(Runnable)`; `CompletableFuture<Void> load()`, `put(ServerDefinition)`, `remove(UUID)` — futures complete on `ui`; a broken file keeps the last good list and refuses saves.

- [ ] **Step 1: Write the failing tests**

`definition/DefinitionFileTest.java`:

```java
package dev.jasper.fileserver.definition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefinitionFileTest {
    static final Path FOLDER = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath();
    static final String AT = "folder = " + DefinitionFile.tomlString(FOLDER.toString()) + "\n";

    @Test void roundTripsEveryField() throws IOException {
        var http = new ServerDefinition(UUID.randomUUID(), "docs \"main\"", Protocol.HTTP, FOLDER.resolve("a\\b"), Bind.ALL, 8081,
            Optional.empty(), true, new Login.Vault(UUID.randomUUID()), List.of(), true, false, true);
        var ftp = new ServerDefinition(UUID.randomUUID(), "drop", Protocol.FTP, FOLDER, new Bind.Interface("en0"), 2121,
            Optional.of(new PortRange(50000, 50010)), false, Login.ANONYMOUS, List.of(), true, true, false);
        var sftp = new ServerDefinition(UUID.randomUUID(), "keys", Protocol.SFTP, FOLDER, Bind.LOOPBACK, 2222,
            Optional.empty(), false, Login.ANONYMOUS, List.of("ssh-ed25519 AAAAC3Nza key \"one\""), false, false, false);
        String text = DefinitionFile.format(List.of(http, ftp, sftp));
        assertThat(text).startsWith(DefinitionFile.HEADER)
            .contains("[[server]]", "name = \"docs \\\"main\\\"\"", "bind = \"all\"", "login = \"vault:", "passive_ports = \"50000-50010\"", "authorized_keys = [", "require_tls = true");
        var parsed = DefinitionFile.parse(text);
        assertThat(parsed.warnings()).isEmpty();
        assertThat(parsed.definitions()).containsExactly(http, ftp, sftp);
    }

    @Test void skipsBadEntriesAndNamesThem() throws IOException {
        String text = DefinitionFile.HEADER
            + "\n[[server]]\nname = \"ok\"\nprotocol = \"http\"\n" + AT
            + "\n[[server]]\nname = \"bad port\"\nprotocol = \"http\"\nport = 70000\n" + AT
            + "\n[[server]]\nname = \"OK\"\nprotocol = \"http\"\n" + AT
            + "\n[[server]]\nname = \"no protocol\"\n" + AT
            + "\n[[server]]\nname = \"tftp login\"\nprotocol = \"tftp\"\nlogin = \"session\"\n" + AT;
        var parsed = DefinitionFile.parse(text);
        assertThat(parsed.definitions()).singleElement().satisfies(d -> {
            assertThat(d.name()).isEqualTo("ok");
            assertThat(d.port()).as("the protocol's default").isEqualTo(8080);
            assertThat(d.bind()).isEqualTo(Bind.LOOPBACK);
            assertThat(d.login()).isEqualTo(Login.ANONYMOUS);
            assertThat(d.id()).as("a missing id is minted").isNotNull();
        });
        assertThat(parsed.warnings()).hasSize(4)
            .anySatisfy(w -> assertThat(w).contains("server 2", "port"))
            .anySatisfy(w -> assertThat(w).contains("server 3", "duplicate name"))
            .anySatisfy(w -> assertThat(w).contains("server 4", "'protocol' is required"))
            .anySatisfy(w -> assertThat(w).contains("server 5", "TFTP has no login"));
        assertThatThrownBy(() -> DefinitionFile.parse("[[server]\nname = ")).isInstanceOf(IOException.class).hasMessageContaining("TOML");
        assertThat(DefinitionFile.parse("").definitions()).isEmpty();
    }
}
```

`definition/DefinitionStoreTest.java`:

```java
package dev.jasper.fileserver.definition;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefinitionStoreTest {
    final Deque<Runnable> background = new ArrayDeque<>();
    final Executor queued = background::add;
    int changes;

    void run() { while (!background.isEmpty()) background.poll().run(); }

    DefinitionStore store(Path dir) {
        var store = new DefinitionStore(dir.resolve("servers.toml"), queued, Runnable::run);
        store.onChanged(() -> changes++);
        return store;
    }

    @Test void loadsSavesAndRemoves(@TempDir Path dir) throws Exception {
        DefinitionStore store = store(dir);
        store.load();
        run();
        assertThat(store.definitions()).isEmpty();
        assertThat(store.error()).isEmpty();
        var docs = ServerDefinition.create("docs", Protocol.HTTP, dir.toAbsolutePath());
        CompletableFuture<Void> saved = store.put(docs);
        run();
        assertThat(saved).isCompleted();
        assertThat(store.definitions()).containsExactly(docs);
        assertThat(store.definition(docs.id())).contains(docs);
        assertThat(changes).isEqualTo(2);
        assertThat(Files.readString(dir.resolve("servers.toml"))).contains("name = \"docs\"");
        try (var listing = Files.list(dir)) { assertThat(listing).as("no temp file").containsExactly(dir.resolve("servers.toml")); }
        var renamed = new ServerDefinition(docs.id(), "papers", docs.protocol(), docs.folder(), docs.bind(), 8081, docs.passiveRange(),
            docs.writable(), docs.login(), docs.authorizedKeys(), docs.tls(), docs.requireTls(), docs.autostart());
        store.put(renamed);
        run();
        assertThat(store.definitions()).containsExactly(renamed);
        CompletableFuture<Void> clash = store.put(ServerDefinition.create("PAPERS", Protocol.HTTP, dir.toAbsolutePath()));
        run();
        assertThatThrownBy(clash::join).isInstanceOf(CompletionException.class).hasMessageContaining("A server named papers exists");
        store.remove(docs.id());
        run();
        assertThat(store.definitions()).isEmpty();
    }

    @Test void aBrokenFileKeepsTheLastGoodServersAndRefusesSaves(@TempDir Path dir) throws Exception {
        DefinitionStore store = store(dir);
        var docs = ServerDefinition.create("docs", Protocol.HTTP, dir.toAbsolutePath());
        store.put(docs);
        run();
        Files.writeString(dir.resolve("servers.toml"), "[[server]\nbroken");
        store.load();
        run();
        assertThat(store.definitions()).containsExactly(docs);
        assertThat(store.error()).isPresent().get().asString().contains("TOML");
        CompletableFuture<Void> refused = store.put(ServerDefinition.create("more", Protocol.HTTP, dir.toAbsolutePath()));
        run();
        assertThatThrownBy(refused::join).isInstanceOf(CompletionException.class).hasMessageContaining("servers.toml has errors");
        Files.writeString(dir.resolve("servers.toml"), DefinitionFile.format(List.of(docs)));
        store.load();
        run();
        assertThat(store.error()).isEmpty();
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.definition.*'`
Expected: FAIL — `DefinitionFile` and `DefinitionStore` do not exist.

- [ ] **Step 3: Write `DefinitionFile`**

```java
package dev.jasper.fileserver.definition;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** The servers.toml format: an array of [[server]] tables. Lenient per entry, strict per file. */
public final class DefinitionFile {
    public static final String HEADER = "# Jasper File Server: saved servers. Jasper rewrites this file when you save from the File Servers panel.\n";
    public static final int MAX_BYTES = 1024 * 1024;

    public record Parsed(List<ServerDefinition> definitions, List<String> warnings) { }

    private DefinitionFile() { }

    /** Throws when the text is not valid TOML; skips and reports individual bad entries. */
    public static Parsed parse(String text) throws IOException {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors()) throw new IOException("servers.toml has TOML errors: " + toml.errors().getFirst());
        Object value = toml.get("server");
        if (value == null) return new Parsed(List.of(), List.of());
        if (!(value instanceof TomlArray array)) return new Parsed(List.of(), List.of("'server' must be an array of tables"));
        var definitions = new ArrayList<ServerDefinition>();
        var warnings = new ArrayList<String>();
        var names = new HashSet<String>();
        var ids = new HashSet<UUID>();
        for (int i = 0; i < array.size(); i++) {
            String where = "server " + (i + 1) + ": ";
            if (!(array.get(i) instanceof TomlTable table)) { warnings.add(where + "not a table"); continue; }
            try {
                ServerDefinition definition = definition(table);
                if (!names.add(definition.name().toLowerCase(Locale.ROOT))) { warnings.add(where + "duplicate name " + definition.name()); continue; }
                if (!ids.add(definition.id())) { warnings.add(where + "duplicate id " + definition.id()); continue; }
                definitions.add(definition);
            } catch (IllegalArgumentException invalid) {
                warnings.add(where + invalid.getMessage());
            }
        }
        return new Parsed(List.copyOf(definitions), List.copyOf(warnings));
    }

    private static ServerDefinition definition(TomlTable table) {
        UUID id = string(table, "id").map(DefinitionFile::uuid).orElseGet(UUID::randomUUID);
        Protocol protocol = Protocol.parse(string(table, "protocol").orElseThrow(() -> new IllegalArgumentException("'protocol' is required")));
        String folder = string(table, "folder").orElseThrow(() -> new IllegalArgumentException("'folder' is required"));
        Path path;
        try { path = Path.of(folder); } catch (InvalidPathException bad) { throw new IllegalArgumentException("'folder' is not a path"); }
        long port = integer(table, "port").orElse((long) protocol.defaultPort());
        if (port < 1 || port > 65535) throw new IllegalArgumentException("'port' must be 1 to 65535");
        Login login = string(table, "login").map(Login::parse).orElse(protocol.allowsAnonymous() ? Login.ANONYMOUS : Login.SESSION);
        return new ServerDefinition(id, string(table, "name").orElse(""), protocol, path,
            string(table, "bind").map(Bind::parse).orElse(Bind.LOOPBACK), (int) port,
            string(table, "passive_ports").map(PortRange::parse), bool(table, "writable"), login,
            strings(table, "authorized_keys"), bool(table, "tls"), bool(table, "require_tls"), bool(table, "autostart"));
    }

    public static String format(List<ServerDefinition> definitions) {
        var out = new StringBuilder(HEADER);
        for (ServerDefinition d : definitions) {
            out.append("\n[[server]]\n");
            out.append("id = \"").append(d.id()).append("\"\n");
            out.append("name = ").append(tomlString(d.name())).append('\n');
            out.append("protocol = \"").append(d.protocol().token()).append("\"\n");
            out.append("folder = ").append(tomlString(d.folder().toString())).append('\n');
            out.append("bind = ").append(tomlString(d.bind().token())).append('\n');
            out.append("port = ").append(d.port()).append('\n');
            d.passiveRange().ifPresent(range -> out.append("passive_ports = \"").append(range.token()).append("\"\n"));
            out.append("writable = ").append(d.writable()).append('\n');
            out.append("login = \"").append(d.login().token()).append("\"\n");
            if (!d.authorizedKeys().isEmpty())
                out.append("authorized_keys = [").append(String.join(", ", d.authorizedKeys().stream().map(DefinitionFile::tomlString).toList())).append("]\n");
            if (d.tls()) out.append("tls = true\n");
            if (d.requireTls()) out.append("require_tls = true\n");
            out.append("autostart = ").append(d.autostart()).append('\n');
        }
        return out.toString();
    }

    /** A TOML basic string: backslash, quote, tab, newlines and other control characters escaped. */
    public static String tomlString(String text) {
        var out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20 || c == 0x7f) out.append(String.format(Locale.ROOT, "\\u%04X", (int) c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }

    private static Optional<String> string(TomlTable table, String key) {
        Object value = table.get(key);
        if (value == null) return Optional.empty();
        if (!(value instanceof String text)) throw new IllegalArgumentException("'" + key + "' must be a string");
        return Optional.of(text);
    }

    private static Optional<Long> integer(TomlTable table, String key) {
        Object value = table.get(key);
        if (value == null) return Optional.empty();
        if (!(value instanceof Long number)) throw new IllegalArgumentException("'" + key + "' must be a whole number");
        return Optional.of(number);
    }

    private static boolean bool(TomlTable table, String key) {
        Object value = table.get(key);
        if (value == null) return false;
        if (!(value instanceof Boolean flag)) throw new IllegalArgumentException("'" + key + "' must be true or false");
        return flag;
    }

    private static List<String> strings(TomlTable table, String key) {
        Object value = table.get(key);
        if (value == null) return List.of();
        if (!(value instanceof TomlArray array)) throw new IllegalArgumentException("'" + key + "' must be a list of strings");
        var result = new ArrayList<String>();
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof String text)) throw new IllegalArgumentException("'" + key + "' must be a list of strings");
            result.add(text);
        }
        return result;
    }

    private static UUID uuid(String text) {
        try { return UUID.fromString(text); } catch (IllegalArgumentException bad) { throw new IllegalArgumentException("'" + text + "' is not an id"); }
    }
}
```

- [ ] **Step 4: Write `DefinitionStore`**

```java
package dev.jasper.fileserver.definition;

import dev.jasper.sdk.Subscription;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The saved servers, read from and written to servers.toml. Reads and writes run on {@code background}, one at a
 * time; the list, the error and the listeners live on the UI thread. A file that fails to parse keeps the last good
 * servers and refuses saves until it parses again.
 */
public final class DefinitionStore {
    private final Path file;
    private final Executor background, ui;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private List<ServerDefinition> definitions = List.of();
    private List<String> warnings = List.of();
    private String error;
    private CompletableFuture<Void> queue = CompletableFuture.completedFuture(null);

    private record Read(List<ServerDefinition> definitions, List<String> warnings, String error) { }

    public DefinitionStore(Path file, Executor background, Executor ui) { this.file = file; this.background = background; this.ui = ui; }

    public Path file() { return file; }
    public List<ServerDefinition> definitions() { return definitions; }
    public Optional<ServerDefinition> definition(UUID id) { return definitions.stream().filter(d -> d.id().equals(id)).findFirst(); }
    /** The last read failure, present while the file is broken. */
    public Optional<String> error() { return Optional.ofNullable(error); }
    /** Entries the last read skipped, one message each. */
    public List<String> warnings() { return warnings; }
    public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }

    /** Reads the file; completes normally even when it is broken ({@link #error()} says why). */
    public CompletableFuture<Void> load() {
        var result = new CompletableFuture<Void>();
        enqueue(() -> onBackground(this::read).handle((read, failure) -> {
            if (failure != null) result.completeExceptionally(failure);
            else { apply(read); result.complete(null); }
            return null;
        }));
        return result;
    }

    /** Adds or replaces a server by id; refuses a name another server uses, ignoring case. */
    public CompletableFuture<Void> put(ServerDefinition definition) {
        return mutate(current -> {
            for (ServerDefinition other : current)
                if (!other.id().equals(definition.id()) && other.name().equalsIgnoreCase(definition.name()))
                    throw new IllegalArgumentException("A server named " + other.name() + " exists");
            var next = new ArrayList<ServerDefinition>();
            boolean replaced = false;
            for (ServerDefinition existing : current) {
                if (existing.id().equals(definition.id())) { next.add(definition); replaced = true; } else next.add(existing);
            }
            if (!replaced) next.add(definition);
            return next;
        });
    }

    public CompletableFuture<Void> remove(UUID id) {
        return mutate(current -> current.stream().filter(d -> !d.id().equals(id)).toList());
    }

    private CompletableFuture<Void> mutate(Function<List<ServerDefinition>, List<ServerDefinition>> mutation) {
        var result = new CompletableFuture<Void>();
        enqueue(() -> onBackground(() -> {
            Read current = read();
            if (current.error() != null) return current;
            write(DefinitionFile.format(List.copyOf(mutation.apply(current.definitions()))));
            return read();
        }).handle((read, failure) -> {
            if (failure != null) result.completeExceptionally(failure);
            else {
                apply(read);
                if (read.error() != null) result.completeExceptionally(new IOException("servers.toml has errors; fix it before saving: " + read.error()));
                else result.complete(null);
            }
            return null;
        }));
        return result;
    }

    private void apply(Read read) {
        warnings = read.warnings();
        if (read.error() == null) { definitions = read.definitions(); error = null; }
        else error = read.error();
        listeners.forEach(Runnable::run);
    }

    private Read read() {
        try {
            if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) return new Read(List.of(), List.of(), null);
            if (!Files.isRegularFile(file)) return new Read(List.of(), List.of(), "servers.toml is not a regular file");
            if (Files.size(file) > DefinitionFile.MAX_BYTES) return new Read(List.of(), List.of(), "servers.toml is larger than " + DefinitionFile.MAX_BYTES + " bytes");
            String text = Files.readString(file, StandardCharsets.UTF_8);
            try {
                var parsed = DefinitionFile.parse(text);
                return new Read(parsed.definitions(), parsed.warnings(), null);
            } catch (IOException bad) {
                return new Read(List.of(), List.of(), bad.getMessage());
            }
        } catch (IOException unreadable) {
            return new Read(List.of(), List.of(), "servers.toml is unreadable: " + unreadable.getMessage());
        }
    }

    private void write(String text) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }

    /** Serialises operations: each starts after the previous one's future settles. */
    private void enqueue(Supplier<CompletableFuture<Void>> operation) {
        queue = queue.handle((ignored, failure) -> null).thenCompose(ignored -> operation.get()).handle((ignored, failure) -> null);
    }

    private <T> CompletableFuture<T> onBackground(Callable<T> work) {
        var result = new CompletableFuture<T>();
        background.execute(() -> {
            T value;
            try { value = work.call(); } catch (Throwable failure) { ui.execute(() -> result.completeExceptionally(failure)); return; }
            ui.execute(() -> result.complete(value));
        });
        return result;
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.definition.*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add plugins/fileserver/src
git commit -m "File Server: servers.toml, read and written off the UI thread

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Engine contract, events, login throttle and addresses

**Files:**
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/engine/{ServerEngine,EngineFailure,ServerEvent,ServerEvents,Sizes,LoginThrottle,ServerAddresses,package-info}.java`
- Create (test support): `plugins/fileserver/src/test/java/dev/jasper/fileserver/TestClock.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/{SizesTest,ServerEventsTest,LoginThrottleTest,ServerAddressesTest}.java`

**Interfaces:**
- Consumes: `Protocol`, `Bind` (Task 5).
- Produces (package `dev.jasper.fileserver.engine`):
  - `interface ServerEngine { void start() throws EngineFailure; void stop(); List<URI> addresses(); }`
  - `EngineFailure extends Exception` (`(String)`, `(String, Throwable)`), `static EngineFailure bind(IOException, int port, String address, Protocol)`
  - `sealed interface ServerEvent { Instant at(); String remote(); }` with records `Connected`, `LoginSucceeded(at, remote, user)`, `LoginFailed(at, remote, user)`, `Changed(at, remote, description)`, `Failed(at, remote, message)`, `TransferStarted(at, remote, long id, Direction, String path, long total, Runnable cancel)`, `TransferProgress(at, remote, long id, long bytes, long total)`, `Transferred(at, remote, long id, Direction, String path, long bytes, Duration took, boolean complete)`; `enum Direction { DOWNLOAD, UPLOAD }`
  - `ServerEvents(Clock, Consumer<ServerEvent> sink)` with `connected`, `loginSucceeded`, `loginFailed`, `changed`, `error`, and `Transfer transfer(String remote, Direction, String path, long total, Runnable cancel)`; `ServerEvents.Transfer` has `add(long)`, `bytes()`, `finish(boolean complete)`; progress at most every 250 ms; a throwing sink is ignored
  - `Sizes.human(long)`, `Sizes.seconds(Duration)`
  - `LoginThrottle(Clock)` with `boolean lockedOut(String)`, `Duration delayFor(String)`, `void failed(String)`, `void succeeded(String)`
  - `ServerAddresses.Nic(String name, boolean up, boolean loopback, boolean virtual, List<InetAddress> addresses)` with `label()`; `ServerAddresses.system()`, `bindAddress(Bind, List<Nic>) throws EngineFailure`, `reachable(Bind, List<Nic>)`, `urls(Protocol, boolean tls, List<InetAddress>, int port, Optional<String> user)`, `describe(InetAddress)`, `hostText(InetAddress)`
- Test support: `dev.jasper.fileserver.TestClock extends Clock` with `advance(Duration)`; starts at 2026-09-30T12:00:00Z.

- [ ] **Step 1: Write the test clock and the failing tests**

`plugins/fileserver/src/test/java/dev/jasper/fileserver/TestClock.java`:

```java
package dev.jasper.fileserver;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock tests move by hand. */
public final class TestClock extends Clock {
    private Instant now;

    public TestClock() { this(Instant.parse("2026-09-30T12:00:00Z")); }
    public TestClock(Instant start) { now = start; }

    public synchronized void advance(Duration by) { now = now.plus(by); }
    @Override public synchronized Instant instant() { return now; }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
}
```

`engine/SizesTest.java`:

```java
package dev.jasper.fileserver.engine;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SizesTest {
    @Test void formatsBytesAndSeconds() {
        assertThat(Sizes.human(0)).isEqualTo("0 B");
        assertThat(Sizes.human(1023)).isEqualTo("1023 B");
        assertThat(Sizes.human(1536)).isEqualTo("1.5 KB");
        assertThat(Sizes.human(10 * 1024)).isEqualTo("10 KB");
        assertThat(Sizes.human((long) (3.1 * 1024 * 1024))).isEqualTo("3.1 MB");
        assertThat(Sizes.human(5L * 1024 * 1024 * 1024)).isEqualTo("5.0 GB");
        assertThat(Sizes.seconds(Duration.ofMillis(400))).isEqualTo("0.4 s");
        assertThat(Sizes.seconds(Duration.ofSeconds(12))).isEqualTo("12.0 s");
    }
}
```

`engine/ServerEventsTest.java`:

```java
package dev.jasper.fileserver.engine;

import dev.jasper.fileserver.TestClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ServerEventsTest {
    final TestClock clock = new TestClock();
    final List<ServerEvent> seen = new ArrayList<>();

    @Test void reportsEventsAndThrottlesTransferProgress() {
        var events = new ServerEvents(clock, seen::add);
        events.connected("10.0.0.5");
        events.loginSucceeded("10.0.0.5", "me");
        events.loginFailed("10.0.0.5", "root");
        events.changed("10.0.0.5", "deleted /a.txt");
        events.error("", "disk full");
        boolean[] cancelled = {false};
        var transfer = events.transfer("10.0.0.5", ServerEvent.Direction.UPLOAD, "/a.bin", 300, () -> cancelled[0] = true);
        transfer.add(100);
        clock.advance(Duration.ofMillis(300));
        transfer.add(100);
        transfer.add(50);
        clock.advance(Duration.ofMillis(100));
        transfer.finish(true);
        transfer.finish(false);
        assertThat(seen).extracting(event -> event.getClass().getSimpleName()).containsExactly(
            "Connected", "LoginSucceeded", "LoginFailed", "Changed", "Failed", "TransferStarted", "TransferProgress", "Transferred");
        var started = (ServerEvent.TransferStarted) seen.get(5);
        started.cancel().run();
        assertThat(cancelled[0]).isTrue();
        assertThat(((ServerEvent.TransferProgress) seen.get(6)).bytes()).isEqualTo(200);
        var done = (ServerEvent.Transferred) seen.get(7);
        assertThat(done.bytes()).isEqualTo(250);
        assertThat(done.took()).isEqualTo(Duration.ofMillis(400));
        assertThat(done.complete()).isTrue();
        assertThat(done.id()).isEqualTo(started.id());
    }

    @Test void aThrowingSinkDoesNotReachTheEngine() {
        var events = new ServerEvents(clock, event -> { throw new IllegalStateException("broken listener"); });
        events.connected("10.0.0.5");
        events.transfer("10.0.0.5", ServerEvent.Direction.DOWNLOAD, "/a", -1, () -> { }).finish(true);
    }
}
```

`engine/LoginThrottleTest.java`:

```java
package dev.jasper.fileserver.engine;

import dev.jasper.fileserver.TestClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LoginThrottleTest {
    final TestClock clock = new TestClock();
    final LoginThrottle throttle = new LoginThrottle(clock);

    @Test void delaysDoubleUpToThirtySecondsPerAddress() {
        assertThat(throttle.delayFor("a")).isZero();
        for (long seconds : new long[]{1, 2, 4, 8, 16, 30, 30}) {
            throttle.failed("a");
            assertThat(throttle.delayFor("a")).isEqualTo(Duration.ofSeconds(seconds));
            clock.advance(Duration.ofSeconds(seconds));
            assertThat(throttle.delayFor("a")).isZero();
        }
        assertThat(throttle.delayFor("b")).as("each address on its own").isZero();
        assertThat(throttle.lockedOut("a")).isFalse();
    }

    @Test void tenFailuresLockTheAddressOutForFiveMinutes() {
        for (int i = 0; i < 10; i++) throttle.failed("a");
        assertThat(throttle.lockedOut("a")).isTrue();
        assertThat(throttle.lockedOut("b")).isFalse();
        clock.advance(Duration.ofMinutes(4));
        assertThat(throttle.lockedOut("a")).isTrue();
        clock.advance(Duration.ofMinutes(1));
        assertThat(throttle.lockedOut("a")).isFalse();
        assertThat(throttle.delayFor("a")).isZero();
    }

    @Test void successAndTimeForgetFailures() {
        throttle.failed("a");
        throttle.failed("a");
        throttle.succeeded("a");
        assertThat(throttle.delayFor("a")).isZero();
        throttle.failed("a");
        throttle.failed("a");
        throttle.failed("a");
        clock.advance(Duration.ofMinutes(11));
        assertThat(throttle.delayFor("a")).isZero();
        throttle.failed("a");
        assertThat(throttle.delayFor("a")).as("old failures no longer count").isEqualTo(Duration.ofSeconds(1));
    }
}
```

`engine/ServerAddressesTest.java`:

```java
package dev.jasper.fileserver.engine;

import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Protocol;
import java.net.BindException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerAddressesTest {
    static InetAddress ip(String literal) {
        try { return InetAddress.getByName(literal); } catch (UnknownHostException impossible) { throw new AssertionError(impossible); }
    }

    final List<ServerAddresses.Nic> nics = List.of(
        new ServerAddresses.Nic("lo0", true, true, false, List.of(ip("127.0.0.1"), ip("::1"))),
        new ServerAddresses.Nic("en0", true, false, false, List.of(ip("fe80::1"), ip("192.168.1.20"), ip("2001:db8::5"))),
        new ServerAddresses.Nic("en1", false, false, false, List.of(ip("10.0.0.2"))),
        new ServerAddresses.Nic("utun3", true, false, true, List.of(ip("10.8.0.1"))),
        new ServerAddresses.Nic("en5", true, false, false, List.of(ip("169.254.10.10"))));

    @Test void reachableAddressesFollowTheBind() {
        assertThat(ServerAddresses.reachable(Bind.LOOPBACK, nics)).containsExactly(InetAddress.getLoopbackAddress());
        assertThat(ServerAddresses.reachable(Bind.ALL, nics)).as("IPv4 first; no link-local, down or virtual interfaces")
            .containsExactly(ip("192.168.1.20"), ip("2001:db8::5"));
        assertThat(ServerAddresses.reachable(new Bind.Interface("en0"), nics)).containsExactly(ip("192.168.1.20"));
        assertThat(ServerAddresses.reachable(Bind.ALL, List.of(nics.getFirst()))).as("nothing else up")
            .containsExactly(InetAddress.getLoopbackAddress());
    }

    @Test void formatsUrlsPerProtocol() {
        assertThat(ServerAddresses.urls(Protocol.HTTP, false, List.of(ip("192.168.1.20"), ip("2001:db8::5")), 8080, Optional.of("me")))
            .containsExactly(URI.create("http://192.168.1.20:8080/"), URI.create("http://[2001:db8:0:0:0:0:0:5]:8080/"));
        assertThat(ServerAddresses.urls(Protocol.HTTP, true, List.of(ip("10.0.0.2")), 8443, Optional.empty()))
            .containsExactly(URI.create("https://10.0.0.2:8443/"));
        assertThat(ServerAddresses.urls(Protocol.SFTP, false, List.of(ip("10.0.0.2")), 2222, Optional.of("me")))
            .containsExactly(URI.create("sftp://me@10.0.0.2:2222/"));
    }

    @Test void choosesTheBindAddressOrSaysWhyNot() throws Exception {
        assertThat(ServerAddresses.bindAddress(Bind.LOOPBACK, nics).isLoopbackAddress()).isTrue();
        assertThat(ServerAddresses.bindAddress(Bind.ALL, nics).isAnyLocalAddress()).isTrue();
        assertThat(ServerAddresses.bindAddress(new Bind.Interface("en0"), nics)).isEqualTo(ip("192.168.1.20"));
        assertThatThrownBy(() -> ServerAddresses.bindAddress(new Bind.Interface("en7"), nics)).isInstanceOf(EngineFailure.class)
            .hasMessage("Network interface en7 is not available");
        assertThatThrownBy(() -> ServerAddresses.bindAddress(new Bind.Interface("en1"), nics)).hasMessage("Network interface en1 is not available");
        assertThat(ServerAddresses.describe(ServerAddresses.bindAddress(Bind.ALL, nics))).isEqualTo("0.0.0.0");
        assertThat(nics.get(1).label()).isEqualTo("en0 (192.168.1.20)");
    }

    @Test void bindFailuresSayWhatToDo() {
        assertThat(EngineFailure.bind(new BindException("Address already in use"), 2121, "0.0.0.0", Protocol.FTP))
            .hasMessage("Port 2121 is already in use on 0.0.0.0");
        assertThat(EngineFailure.bind(new SocketException("Permission denied"), 21, "0.0.0.0", Protocol.FTP))
            .hasMessage("Port 21 needs administrator rights on this system — use a port above 1024 (for example 8021)");
        assertThat(EngineFailure.bind(new BindException("Cannot assign requested address"), 8080, "10.9.9.9", Protocol.HTTP))
            .hasMessage("Could not listen on port 8080: Cannot assign requested address");
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.*'`
Expected: FAIL — the package does not exist.

- [ ] **Step 3: Write the contract, failure and events**

`engine/package-info.java`:

```java
/**
 * What every protocol engine shares: the {@link dev.jasper.fileserver.engine.ServerEngine} contract, events for the
 * activity log, the login throttle and the addresses a server binds and advertises. Engines live in sub-packages and
 * are the only code that names a protocol library. May depend on {@code definition}, {@code share}, {@code auth} and
 * the JDK.
 */
package dev.jasper.fileserver.engine;
```

`engine/ServerEngine.java`:

```java
package dev.jasper.fileserver.engine;

import java.net.URI;
import java.util.List;

/** One running protocol server. The registry builds it, then starts and stops it on a worker thread. */
public interface ServerEngine {
    /** Binds and starts listening; returns once clients can connect. */
    void start() throws EngineFailure;

    /** Stops listening and drops every client. Idempotent; returns within about a second. */
    void stop();

    /** The URLs clients use, recomputed from the current network interfaces; empty while stopped. */
    List<URI> addresses();
}
```

`engine/EngineFailure.java`:

```java
package dev.jasper.fileserver.engine;

import dev.jasper.fileserver.definition.Protocol;
import java.io.IOException;
import java.util.Locale;

/** A server that could not start, with a message for the panel. */
public final class EngineFailure extends Exception {
    public EngineFailure(String message) { super(message); }
    public EngineFailure(String message, Throwable cause) { super(message, cause); }

    /** The message for a listening socket that could not be bound. */
    public static EngineFailure bind(IOException failure, int port, String address, Protocol protocol) {
        String text = String.valueOf(failure.getMessage()).toLowerCase(Locale.ROOT);
        if (text.contains("in use"))
            return new EngineFailure("Port " + port + " is already in use on " + address, failure);
        if (text.contains("permission") || text.contains("access permissions") || text.contains("forbidden"))
            return new EngineFailure("Port " + port + " needs administrator rights on this system — use a port above 1024 (for example "
                + protocol.suggestedPort(port) + ")", failure);
        return new EngineFailure("Could not listen on port " + port + ": " + failure.getMessage(), failure);
    }
}
```

`engine/ServerEvent.java`:

```java
package dev.jasper.fileserver.engine;

import java.time.Duration;
import java.time.Instant;

/** Something a client did; {@code remote} is the client's address, or empty for the server itself. */
public sealed interface ServerEvent {
    Instant at();
    String remote();

    enum Direction { DOWNLOAD, UPLOAD }

    record Connected(Instant at, String remote) implements ServerEvent { }
    record LoginSucceeded(Instant at, String remote, String user) implements ServerEvent { }
    record LoginFailed(Instant at, String remote, String user) implements ServerEvent { }
    record Changed(Instant at, String remote, String description) implements ServerEvent { }
    record Failed(Instant at, String remote, String message) implements ServerEvent { }
    /** A transfer began; {@code total} is -1 when unknown, and {@code cancel} drops the client's connection. */
    record TransferStarted(Instant at, String remote, long id, Direction direction, String path, long total, Runnable cancel) implements ServerEvent { }
    record TransferProgress(Instant at, String remote, long id, long bytes, long total) implements ServerEvent { }
    record Transferred(Instant at, String remote, long id, Direction direction, String path, long bytes, Duration took, boolean complete) implements ServerEvent { }
}
```

`engine/ServerEvents.java`:

```java
package dev.jasper.fileserver.engine;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** How an engine reports; safe from any thread. A sink that throws is ignored so a listener cannot break a server. */
public final class ServerEvents {
    static final Duration PROGRESS_INTERVAL = Duration.ofMillis(250);
    private final Clock clock;
    private final Consumer<ServerEvent> sink;
    private final AtomicLong ids = new AtomicLong();

    public ServerEvents(Clock clock, Consumer<ServerEvent> sink) { this.clock = clock; this.sink = sink; }

    public void connected(String remote) { emit(new ServerEvent.Connected(clock.instant(), remote)); }
    public void loginSucceeded(String remote, String user) { emit(new ServerEvent.LoginSucceeded(clock.instant(), remote, user)); }
    public void loginFailed(String remote, String user) { emit(new ServerEvent.LoginFailed(clock.instant(), remote, user)); }
    public void changed(String remote, String description) { emit(new ServerEvent.Changed(clock.instant(), remote, description)); }
    public void error(String remote, String message) { emit(new ServerEvent.Failed(clock.instant(), remote, message)); }

    /** Starts a transfer; {@code total} is -1 when unknown and {@code cancel} drops the client. */
    public Transfer transfer(String remote, ServerEvent.Direction direction, String path, long total, Runnable cancel) {
        var transfer = new Transfer(ids.incrementAndGet(), remote, direction, path, total, clock.instant());
        emit(new ServerEvent.TransferStarted(transfer.started, remote, transfer.id, direction, path, total, cancel));
        return transfer;
    }

    private void emit(ServerEvent event) {
        try { sink.accept(event); } catch (RuntimeException ignored) { /* a listener cannot break a server */ }
    }

    /** One transfer's progress; reports at most every 250 ms and finishes once. */
    public final class Transfer {
        private final long id;
        private final String remote, path;
        private final ServerEvent.Direction direction;
        private final long total;
        private final Instant started;
        private long bytes;
        private Instant reported;
        private boolean finished;

        private Transfer(long id, String remote, ServerEvent.Direction direction, String path, long total, Instant started) {
            this.id = id; this.remote = remote; this.direction = direction; this.path = path; this.total = total;
            this.started = started; this.reported = started;
        }

        public synchronized void add(long count) {
            bytes += count;
            Instant now = clock.instant();
            if (!now.isBefore(reported.plus(PROGRESS_INTERVAL))) {
                reported = now;
                emit(new ServerEvent.TransferProgress(now, remote, id, bytes, total));
            }
        }

        public synchronized long bytes() { return bytes; }

        public synchronized void finish(boolean complete) {
            if (finished) return;
            finished = true;
            Instant now = clock.instant();
            emit(new ServerEvent.Transferred(now, remote, id, direction, path, bytes, Duration.between(started, now), complete));
        }
    }
}
```

`engine/Sizes.java`:

```java
package dev.jasper.fileserver.engine;

import java.time.Duration;
import java.util.Locale;

/** Byte counts and durations as people read them. */
public final class Sizes {
    private static final String[] UNITS = {"KB", "MB", "GB", "TB"};

    private Sizes() { }

    public static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        int unit = -1;
        do { value /= 1024; unit++; } while (value >= 1024 && unit < UNITS.length - 1);
        return String.format(Locale.ROOT, value >= 10 ? "%.0f %s" : "%.1f %s", value, UNITS[unit]);
    }

    public static String seconds(Duration took) {
        return String.format(Locale.ROOT, "%.1f s", took.toMillis() / 1000.0);
    }
}
```

- [ ] **Step 4: Write `LoginThrottle`**

```java
package dev.jasper.fileserver.engine;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Slows down password guessing, per client address and shared by every server: after each failed login the next
 * attempt waits 1 s, doubling to at most 30 s; ten failures within ten minutes lock the address out for five
 * minutes. A success forgets the address. Thread-safe.
 */
public final class LoginThrottle {
    static final Duration WINDOW = Duration.ofMinutes(10);
    static final Duration LOCKOUT = Duration.ofMinutes(5);
    static final Duration MAX_DELAY = Duration.ofSeconds(30);
    static final int LIMIT = 10;
    static final int MAX_REMOTES = 4096;

    private static final class Record {
        final ArrayDeque<Instant> failures = new ArrayDeque<>();
        Instant lockedUntil;
    }

    private final Clock clock;
    private final Map<String, Record> records = new LinkedHashMap<>();

    public LoginThrottle(Clock clock) { this.clock = clock; }

    public synchronized boolean lockedOut(String remote) {
        Record record = current(remote);
        return record != null && record.lockedUntil != null;
    }

    /** How long the next attempt from this address waits before it is checked. */
    public synchronized Duration delayFor(String remote) {
        Record record = current(remote);
        if (record == null || record.failures.isEmpty()) return Duration.ZERO;
        int count = record.failures.size();
        Duration backoff = count > 5 ? MAX_DELAY : Duration.ofSeconds(1L << (count - 1));
        if (backoff.compareTo(MAX_DELAY) > 0) backoff = MAX_DELAY;
        Duration wait = Duration.between(clock.instant(), record.failures.getLast().plus(backoff));
        return wait.isNegative() ? Duration.ZERO : wait;
    }

    public synchronized void failed(String remote) {
        Instant now = clock.instant();
        Record record = current(remote);
        if (record == null) {
            record = new Record();
            records.put(remote, record);
            var oldest = records.keySet().iterator();
            while (records.size() > MAX_REMOTES) { oldest.next(); oldest.remove(); }
        }
        record.failures.addLast(now);
        if (record.failures.size() >= LIMIT) {
            record.lockedUntil = now.plus(LOCKOUT);
            record.failures.clear();
        }
    }

    public synchronized void succeeded(String remote) { records.remove(remote); }

    /** The address's record with expired failures and lockouts dropped; null, and forgotten, when nothing is left. */
    private Record current(String remote) {
        Record record = records.get(remote);
        if (record == null) return null;
        Instant now = clock.instant();
        while (!record.failures.isEmpty() && record.failures.getFirst().isBefore(now.minus(WINDOW))) record.failures.removeFirst();
        if (record.lockedUntil != null && !now.isBefore(record.lockedUntil)) record.lockedUntil = null;
        if (record.failures.isEmpty() && record.lockedUntil == null) { records.remove(remote); return null; }
        return record;
    }
}
```

- [ ] **Step 5: Write `ServerAddresses`**

```java
package dev.jasper.fileserver.engine;

import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Protocol;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

/** The address a server binds and the URLs it advertises, computed from a snapshot of the network interfaces. */
public final class ServerAddresses {
    /** One network interface as File Server sees it. */
    public record Nic(String name, boolean up, boolean loopback, boolean virtual, List<InetAddress> addresses) {
        public Nic { addresses = List.copyOf(addresses); }

        /** The name and preferred address, for example "en0 (192.168.1.20)". */
        public String label() { return addresses.isEmpty() ? name : name + " (" + hostText(preferred(this)) + ")"; }
    }

    private ServerAddresses() { }

    /** This computer's interfaces now; empty when the system will not say. */
    public static List<Nic> system() {
        var nics = new ArrayList<Nic>();
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            if (all == null) return List.of();
            for (NetworkInterface nic : Collections.list(all)) {
                try { nics.add(new Nic(nic.getName(), nic.isUp(), nic.isLoopback(), nic.isVirtual(), Collections.list(nic.getInetAddresses()))); }
                catch (SocketException vanished) { /* gone while listing */ }
            }
        } catch (SocketException unavailable) {
            return List.of();
        }
        return List.copyOf(nics);
    }

    /** The local address to bind: loopback, the wildcard, or the interface's first IPv4 address. */
    public static InetAddress bindAddress(Bind bind, List<Nic> nics) throws EngineFailure {
        return switch (bind) {
            case Bind.Loopback loopback -> InetAddress.getLoopbackAddress();
            case Bind.All all -> new InetSocketAddress(0).getAddress();
            case Bind.Interface named -> nics.stream()
                .filter(nic -> nic.name().equals(named.name()) && nic.up() && !nic.addresses().isEmpty()).findFirst()
                .map(ServerAddresses::preferred)
                .orElseThrow(() -> new EngineFailure("Network interface " + named.name() + " is not available"));
        };
    }

    /** The addresses other programs reach a server on: IPv4 first, then global IPv6; loopback when nothing else is up. */
    public static List<InetAddress> reachable(Bind bind, List<Nic> nics) {
        return switch (bind) {
            case Bind.Loopback loopback -> List.of(InetAddress.getLoopbackAddress());
            case Bind.Interface named -> nics.stream().filter(nic -> nic.name().equals(named.name()) && !nic.addresses().isEmpty())
                .findFirst().map(nic -> List.of(preferred(nic))).orElse(List.of());
            case Bind.All all -> {
                List<InetAddress> found = nics.stream().filter(nic -> nic.up() && !nic.loopback() && !nic.virtual())
                    .flatMap(nic -> nic.addresses().stream()).filter(ServerAddresses::shareable)
                    .sorted(Comparator.comparing((InetAddress address) -> !(address instanceof Inet4Address)))
                    .distinct().toList();
                yield found.isEmpty() ? List.of(InetAddress.getLoopbackAddress()) : found;
            }
        };
    }

    /** One URL per address; SFTP URLs name the user. */
    public static List<URI> urls(Protocol protocol, boolean tls, List<InetAddress> addresses, int port, Optional<String> user) {
        String scheme = protocol == Protocol.HTTP && tls ? "https" : protocol.scheme();
        String who = user.filter(name -> protocol == Protocol.SFTP)
            .map(name -> URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20") + "@").orElse("");
        var urls = new ArrayList<URI>();
        for (InetAddress address : addresses) {
            String host = address instanceof Inet6Address ? "[" + hostText(address) + "]" : hostText(address);
            urls.add(URI.create(scheme + "://" + who + host + ":" + port + "/"));
        }
        return List.copyOf(urls);
    }

    /** How a bound address reads in a message: 0.0.0.0 for every address. */
    public static String describe(InetAddress address) {
        return address.isAnyLocalAddress() ? "0.0.0.0" : hostText(address);
    }

    /** The address as text, without an IPv6 scope. */
    public static String hostText(InetAddress address) {
        String text = address.getHostAddress();
        int scope = text.indexOf('%');
        return scope < 0 ? text : text.substring(0, scope);
    }

    private static InetAddress preferred(Nic nic) {
        return nic.addresses().stream().filter(address -> address instanceof Inet4Address).findFirst().orElse(nic.addresses().getFirst());
    }

    private static boolean shareable(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isMulticastAddress()) return false;
        return !(address instanceof Inet6Address) || !address.isSiteLocalAddress();
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.*'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add plugins/fileserver/src
git commit -m "File Server: engine contract, events, login throttle and addresses

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Logins

**Files:**
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/auth/{ResolvedLogin,SessionPasswords,LoginUnavailableException,LoginResolver,package-info}.java`
- Create (test support): `plugins/fileserver/src/test/java/dev/jasper/fileserver/FakeVault.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/auth/{ResolvedLoginTest,LoginResolverTest}.java`

**Interfaces:**
- Consumes: `ServerDefinition`, `Login` (Task 5); `dev.jasper.vault.api.{VaultApi, Credential, CredentialDescriptor, Kind, LockState}`.
- Produces (package `dev.jasper.fileserver.auth`):
  - `ResolvedLogin implements AutoCloseable`: `static anonymous()`, `static password(String user, char[] password)` (copies), `boolean isAnonymous()`, `Optional<String> user()`, `boolean matches(String user, char[] password)` (constant time; false after close), `Optional<String> revealPassword()`, `close()` (zeroes)
  - `SessionPasswords.generate()` → 20 characters from `ALPHABET` (no `0 O o 1 l I`)
  - `LoginUnavailableException extends Exception`
  - `LoginResolver(Optional<VaultApi> vault, Supplier<String> user, Supplier<String> passwords)`; `boolean vaultPresent()`; `boolean needsUnlock(ServerDefinition)`; `CompletableFuture<ResolvedLogin> resolve(ServerDefinition, WindowHandle ownerOrNull)` failing with `LoginUnavailableException` messages `NO_VAULT`, `MISSING`, `NOT_PROVIDED`
- Test support: `dev.jasper.fileserver.FakeVault implements Plugin` with `INFO`, `descriptors`, `secrets`, `state`, `unlockOnRequest`, `password(name, user, password)`, `key(name)`, `api()`, `unlock()` (sets UNLOCKED and publishes `VaultApi.LOCK_STATE_CHANGED` once started).

- [ ] **Step 1: Write the fake Vault and the failing tests**

`plugins/fileserver/src/test/java/dev/jasper/fileserver/FakeVault.java`:

```java
package dev.jasper.fileserver;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.Kind;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.VaultApi;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** A stand-in Vault: every credential granted; {@code pick} answers the first descriptor. */
public final class FakeVault implements Plugin {
    public static final PluginInfo INFO = new PluginInfo("dev.jasper.vault", "Credential Vault", "0.3.0", Set.of());
    public final Map<UUID, CredentialDescriptor> descriptors = new LinkedHashMap<>();
    public final Map<UUID, Supplier<Credential>> secrets = new LinkedHashMap<>();
    public LockState state = LockState.UNLOCKED;
    /** A locked Vault asked for a secret unlocks first, as the real one prompts. */
    public boolean unlockOnRequest;
    private PluginContext context;

    public UUID password(String name, String username, String password) {
        UUID id = UUID.randomUUID();
        descriptors.put(id, new CredentialDescriptor(id, name, username, Kind.ACCOUNT_PASSWORD));
        secrets.put(id, () -> new Credential(id, name, Kind.ACCOUNT_PASSWORD, username, password.toCharArray(), null, null));
        return id;
    }

    public UUID key(String name) {
        UUID id = UUID.randomUUID();
        descriptors.put(id, new CredentialDescriptor(id, name, "SHA256:x", Kind.SSH_KEY));
        secrets.put(id, () -> new Credential(id, name, Kind.SSH_KEY, null, null, null, null));
        return id;
    }

    public void unlock() {
        state = LockState.UNLOCKED;
        if (context != null) context.events().publish(VaultApi.LOCK_STATE_CHANGED, LockState.UNLOCKED);
    }

    public VaultApi api() {
        return new VaultApi() {
            @Override public LockState lockState() { return state; }
            @Override public CompletableFuture<Boolean> ensureUnlocked(WindowHandle owner) {
                if (state != LockState.UNLOCKED && unlockOnRequest) state = LockState.UNLOCKED;
                return CompletableFuture.completedFuture(state == LockState.UNLOCKED);
            }
            @Override public List<CredentialDescriptor> credentials() { return state == LockState.UNLOCKED ? List.copyOf(descriptors.values()) : List.of(); }
            @Override public CompletableFuture<Optional<Credential>> credential(UUID id) {
                if (state != LockState.UNLOCKED && unlockOnRequest) state = LockState.UNLOCKED;
                return CompletableFuture.completedFuture(state == LockState.UNLOCKED ? Optional.ofNullable(secrets.get(id)).map(Supplier::get) : Optional.empty());
            }
            @Override public CompletableFuture<Optional<CredentialDescriptor>> pick(WindowHandle owner) {
                return CompletableFuture.completedFuture(descriptors.values().stream().findFirst());
            }
        };
    }

    @Override public void start(PluginContext context) {
        this.context = context;
        context.services().publishPerConsumer(VaultApi.class, consumer -> api());
    }
}
```

`auth/ResolvedLoginTest.java`:

```java
package dev.jasper.fileserver.auth;

import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResolvedLoginTest {
    @Test void matchesOnlyTheExactUserAndPasswordUntilClosed() {
        char[] secret = "s3cret".toCharArray();
        var login = ResolvedLogin.password("me", secret);
        secret[0] = 'X';
        assertThat(login.isAnonymous()).isFalse();
        assertThat(login.user()).contains("me");
        assertThat(login.matches("me", "s3cret".toCharArray())).as("the password was copied").isTrue();
        assertThat(login.matches("Me", "s3cret".toCharArray())).isFalse();
        assertThat(login.matches("me", "s3cret ".toCharArray())).isFalse();
        assertThat(login.matches(null, null)).isFalse();
        assertThat(login.revealPassword()).contains("s3cret");
        login.close();
        assertThat(login.matches("me", "s3cret".toCharArray())).isFalse();
        assertThat(login.revealPassword()).isEmpty();
        assertThat(ResolvedLogin.anonymous().isAnonymous()).isTrue();
        assertThat(ResolvedLogin.anonymous().matches("me", "x".toCharArray())).isFalse();
        assertThatThrownBy(() -> ResolvedLogin.password(" ", "x".toCharArray())).hasMessageContaining("user name");
    }

    @Test void sessionPasswordsAreLongAndUnambiguous() {
        String password = SessionPasswords.generate(new Random(7));
        assertThat(password).hasSize(20);
        assertThat(password.chars()).allMatch(c -> SessionPasswords.ALPHABET.indexOf(c) >= 0);
        assertThat(SessionPasswords.ALPHABET).doesNotContain("0", "O", "o", "1", "l", "I");
        assertThat(SessionPasswords.generate()).isNotEqualTo(SessionPasswords.generate());
    }
}
```

`auth/LoginResolverTest.java`:

```java
package dev.jasper.fileserver.auth;

import dev.jasper.fileserver.FakeVault;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.vault.api.LockState;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginResolverTest {
    static final Path FOLDER = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath();
    final FakeVault vault = new FakeVault();

    static ServerDefinition with(Login login) {
        var base = ServerDefinition.create("docs", Protocol.HTTP, FOLDER);
        return new ServerDefinition(base.id(), base.name(), base.protocol(), base.folder(), base.bind(), base.port(),
            base.passiveRange(), base.writable(), login, List.of(), false, false, false);
    }

    LoginResolver resolver(Optional<FakeVault> withVault) {
        return new LoginResolver(withVault.map(FakeVault::api), () -> "dustin", () -> "generated-password-1");
    }

    static String failure(CompletableFuture<ResolvedLogin> future) {
        assertThat(future).isCompletedExceptionally();
        try { future.join(); throw new AssertionError("expected a failure"); }
        catch (CompletionException failed) {
            assertThat(failed.getCause()).isInstanceOf(LoginUnavailableException.class);
            return failed.getCause().getMessage();
        }
    }

    @Test void anonymousAndSessionLoginsNeedNoVault() {
        var resolver = resolver(Optional.empty());
        assertThat(resolver.resolve(with(Login.ANONYMOUS), null).join().isAnonymous()).isTrue();
        ResolvedLogin session = resolver.resolve(with(Login.SESSION), null).join();
        assertThat(session.user()).contains("dustin");
        assertThat(session.revealPassword()).contains("generated-password-1");
        assertThat(resolver.vaultPresent()).isFalse();
        assertThat(failure(resolver.resolve(with(new Login.Vault(UUID.randomUUID())), null))).isEqualTo(LoginResolver.NO_VAULT);
    }

    @Test void aVaultAccountSuppliesTheUserAndPassword() {
        UUID id = vault.password("Share login", "guest", "pw");
        var resolver = resolver(Optional.of(vault));
        ResolvedLogin login = resolver.resolve(with(new Login.Vault(id)), null).join();
        assertThat(login.matches("guest", "pw".toCharArray())).isTrue();
        assertThat(resolver.needsUnlock(with(new Login.Vault(id)))).isFalse();
    }

    @Test void explainsWhyAVaultAccountIsUnavailable() {
        var resolver = resolver(Optional.of(vault));
        assertThat(failure(resolver.resolve(with(new Login.Vault(UUID.randomUUID())), null))).isEqualTo(LoginResolver.MISSING);
        UUID key = vault.key("Just a key");
        assertThat(failure(resolver.resolve(with(new Login.Vault(key)), null))).contains("Just a key", "no user name and password");
        UUID id = vault.password("Share login", "guest", "pw");
        vault.state = LockState.LOCKED;
        assertThat(resolver.needsUnlock(with(new Login.Vault(id)))).isTrue();
        assertThat(resolver.needsUnlock(with(Login.SESSION))).isFalse();
        assertThat(failure(resolver.resolve(with(new Login.Vault(id)), null))).isEqualTo(LoginResolver.NOT_PROVIDED);
        vault.unlockOnRequest = true;
        assertThat(resolver.resolve(with(new Login.Vault(id)), null).join().user()).contains("guest");
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.auth.*'`
Expected: FAIL — the package does not exist.

- [ ] **Step 3: Write the login types**

`auth/package-info.java`:

```java
/**
 * Turns a definition's login into credentials for one run: none, a session password generated now, or a Vault
 * account. Only {@link dev.jasper.fileserver.auth.LoginResolver} names {@code dev.jasper.vault.api} types, and it calls
 * them only when the Vault is installed. May depend on {@code definition}, the SDK and the Vault API.
 */
package dev.jasper.fileserver.auth;
```

`auth/ResolvedLogin.java`:

```java
package dev.jasper.fileserver.auth;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** The credentials one run of a server accepts. Thread-safe; closing zeroes the password. */
public final class ResolvedLogin implements AutoCloseable {
    private static final ResolvedLogin ANONYMOUS = new ResolvedLogin(null, null);
    private final String user;
    private final char[] password;
    private boolean closed;

    private ResolvedLogin(String user, char[] password) { this.user = user; this.password = password; }

    public static ResolvedLogin anonymous() { return ANONYMOUS; }

    public static ResolvedLogin password(String user, char[] password) {
        if (user == null || user.isBlank()) throw new IllegalArgumentException("A login needs a user name");
        Objects.requireNonNull(password, "password");
        return new ResolvedLogin(user, password.clone());
    }

    public boolean isAnonymous() { return user == null; }
    public Optional<String> user() { return Optional.ofNullable(user); }

    /** Compares both parts in constant time. */
    public synchronized boolean matches(String candidateUser, char[] candidatePassword) {
        if (user == null || closed || candidateUser == null || candidatePassword == null) return false;
        boolean sameUser = MessageDigest.isEqual(user.getBytes(StandardCharsets.UTF_8), candidateUser.getBytes(StandardCharsets.UTF_8));
        boolean samePassword = MessageDigest.isEqual(bytes(password), bytes(candidatePassword));
        return sameUser & samePassword;
    }

    public synchronized Optional<String> revealPassword() {
        return password == null || closed ? Optional.empty() : Optional.of(new String(password));
    }

    @Override public synchronized void close() {
        if (password == null || closed) return;
        closed = true;
        Arrays.fill(password, '\0');
    }

    private static byte[] bytes(char[] chars) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] out = new byte[encoded.remaining()];
        encoded.get(out);
        return out;
    }
}
```

`auth/SessionPasswords.java`:

```java
package dev.jasper.fileserver.auth;

import java.security.SecureRandom;
import java.util.Random;

/** Passwords generated for one run of a server: 20 characters that cannot be misread. */
public final class SessionPasswords {
    static final String ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SessionPasswords() { }

    public static String generate() { return generate(RANDOM); }

    static String generate(Random random) {
        var out = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) out.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return out.toString();
    }
}
```

`auth/LoginUnavailableException.java`:

```java
package dev.jasper.fileserver.auth;

/** A login that cannot be resolved, with a message for the panel. */
public final class LoginUnavailableException extends Exception {
    public LoginUnavailableException(String message) { super(message); }
}
```

- [ ] **Step 4: Write `LoginResolver`**

```java
package dev.jasper.fileserver.auth;

import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.VaultApi;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Resolves a server's login just before it binds. UI thread; the future completes on the UI thread (Vault futures
 * do) or at once. A Vault request may prompt to unlock and to allow File Server, over {@code owner} when given.
 */
public final class LoginResolver {
    static final String NO_VAULT = "Install or enable the Credential Vault to use a saved account";
    static final String MISSING = "Vault account no longer exists — edit the server";
    static final String NOT_PROVIDED = "The Vault did not provide the account";

    private final Optional<VaultApi> vault;
    private final Supplier<String> user;
    private final Supplier<String> passwords;

    public LoginResolver(Optional<VaultApi> vault, Supplier<String> user, Supplier<String> passwords) {
        this.vault = vault; this.user = user; this.passwords = passwords;
    }

    public boolean vaultPresent() { return vault.isPresent(); }

    /** Whether starting this server now would have to unlock the Vault. */
    public boolean needsUnlock(ServerDefinition definition) {
        return definition.login() instanceof Login.Vault && vault.isPresent() && vault.get().lockState() != LockState.UNLOCKED;
    }

    public CompletableFuture<ResolvedLogin> resolve(ServerDefinition definition, WindowHandle owner) {
        return switch (definition.login()) {
            case Login.Anonymous anonymous -> CompletableFuture.completedFuture(ResolvedLogin.anonymous());
            case Login.Session session -> CompletableFuture.completedFuture(ResolvedLogin.password(user.get(), passwords.get().toCharArray()));
            case Login.Vault saved -> fromVault(saved.credentialId(), owner);
        };
    }

    private CompletableFuture<ResolvedLogin> fromVault(UUID id, WindowHandle owner) {
        if (vault.isEmpty()) return CompletableFuture.failedFuture(new LoginUnavailableException(NO_VAULT));
        VaultApi api = vault.get();
        CompletableFuture<Optional<Credential>> request;
        try { request = owner == null ? api.credential(id) : api.credential(owner, id); }
        catch (RuntimeException failure) { return CompletableFuture.failedFuture(new LoginUnavailableException(NOT_PROVIDED)); }
        return request.thenApply(found -> {
            if (found.isEmpty()) {
                boolean gone = api.lockState() == LockState.UNLOCKED && api.credentials().stream().noneMatch(d -> d.id().equals(id));
                throw new CompletionException(new LoginUnavailableException(gone ? MISSING : NOT_PROVIDED));
            }
            try (Credential credential = found.get()) {
                char[] password = credential.password();
                Optional<String> name = credential.username().filter(text -> !text.isBlank());
                if (password == null || name.isEmpty())
                    throw new CompletionException(new LoginUnavailableException("The Vault account ‘" + credential.name() + "’ has no user name and password"));
                return ResolvedLogin.password(name.get(), password);
            }
        });
    }
}
```

- [ ] **Step 5: Run the tests and the architecture check**

Run: `./gradlew :jasper-plugin-fileserver:check`
Expected: PASS (`verifyPluginArchitecture` accepts `dev.jasper.vault.api`).

- [ ] **Step 6: Commit**

```bash
git add plugins/fileserver/src
git commit -m "File Server: logins from the Vault or a session password

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: HTTP engine — reading

**Files:**
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/engine/http/{HttpEngine,FileHandler,Range,Listing,HttpPaths,HostCheck,ThrottledAuthenticator,package-info}.java`
- Create (test support): `plugins/fileserver/src/test/java/dev/jasper/fileserver/Ports.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/http/{HttpPathsTest,HostCheckTest,RangeTest,HttpEngineReadTest}.java`

**Interfaces:**
- Consumes: `ShareRoot` (Task 4); `ServerDefinition`, `Bind` (Task 5); `ServerEngine`, `EngineFailure`, `ServerEvents`, `ServerEvent`, `LoginThrottle`, `ServerAddresses`, `Sizes` (Task 7); `ResolvedLogin` (Task 8).
- Produces: `public final class HttpEngine implements ServerEngine` with `HttpEngine(ServerDefinition, ShareRoot, ResolvedLogin, ServerEvents, LoginThrottle, Supplier<List<ServerAddresses.Nic>> nics)` and `int port()`. Package-private `FileHandler`, `Range`, `Listing`, `HttpPaths`, `HostCheck`, `ThrottledAuthenticator` (Task 10 extends `FileHandler` and `Listing`).
- Test support: `dev.jasper.fileserver.Ports.free()` → a port free on 127.0.0.1 a moment ago.

Behaviour (spec "HTTP"): listings with every name escaped and URL-encoded, folders first; a folder URL without a slash redirects; files stream as attachments with `nosniff`, `Accept-Ranges` and single-range `Range` (206/416; several ranges send the whole file); Basic auth when the login is not anonymous, through `LoginThrottle` (429 while locked out); the `Host` header must be an IP literal, `localhost` or this computer's name (421 otherwise; only loopback names for a loopback bind). In this task every other method answers 405.

- [ ] **Step 1: Write the failing unit tests**

`plugins/fileserver/src/test/java/dev/jasper/fileserver/Ports.java`:

```java
package dev.jasper.fileserver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;

/** Test ports. */
public final class Ports {
    private Ports() { }

    /** A port that was free on 127.0.0.1 a moment ago. */
    public static int free() {
        try (var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) { return socket.getLocalPort(); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
```

`engine/http/HttpPathsTest.java`:

```java
package dev.jasper.fileserver.engine.http;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpPathsTest {
    @Test void decodesEncodesAndEscapes() {
        assertThat(HttpPaths.decode("/a%20b/%C3%A9+c")).isEqualTo("/a b/é+c");
        assertThatThrownBy(() -> HttpPaths.decode("/bad%zz")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HttpPaths.decode("/cut%4")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HttpPaths.decode("/%C3")).as("not UTF-8").isInstanceOf(IllegalArgumentException.class);
        assertThat(HttpPaths.segment("x & <y>.txt")).isEqualTo("x%20%26%20%3Cy%3E.txt");
        assertThat(HttpPaths.segment("é")).isEqualTo("%C3%A9");
        assertThat(HttpPaths.href(List.of("docs", "a b"), true)).isEqualTo("/docs/a%20b/");
        assertThat(HttpPaths.href(List.of(), true)).isEqualTo("/");
        assertThat(HttpPaths.html("<a href=\"x\">'&'</a>")).isEqualTo("&lt;a href=&quot;x&quot;&gt;&#39;&amp;&#39;&lt;/a&gt;");
        assertThat(HttpPaths.contentDisposition("résumé \"v2\".pdf"))
            .isEqualTo("attachment; filename=\"r_sum_ _v2_.pdf\"; filename*=UTF-8''r%C3%A9sum%C3%A9%20%22v2%22.pdf");
    }
}
```

`engine/http/HostCheckTest.java`:

```java
package dev.jasper.fileserver.engine.http;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HostCheckTest {
    @Test void aLoopbackServerAnswersOnlyLoopbackNames() {
        var check = new HostCheck(Set.of("localhost"), true);
        assertThat(check.allows("127.0.0.1:8080")).isTrue();
        assertThat(check.allows("localhost:8080")).isTrue();
        assertThat(check.allows("[::1]:8080")).isTrue();
        assertThat(check.allows("192.168.1.20:8080")).isFalse();
        assertThat(check.allows("evil.example:8080")).isFalse();
        assertThat(check.allows(null)).isFalse();
    }

    @Test void aNetworkServerAnswersAddressesAndThisComputersNames() {
        var check = new HostCheck(Set.of("localhost", "studio", "studio.local"), false);
        assertThat(check.allows("192.168.1.20:8080")).isTrue();
        assertThat(check.allows("[2001:db8::5]:8080")).isTrue();
        assertThat(check.allows("Studio.local")).isTrue();
        assertThat(check.allows("evil.example")).isFalse();
        assertThat(check.allows("999.1.1.1")).isFalse();
    }
}
```

`engine/http/RangeTest.java`:

```java
package dev.jasper.fileserver.engine.http;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RangeTest {
    @Test void parsesOneRangeAndIgnoresTheRest() throws Exception {
        assertThat(Range.parse(null, 10)).isEmpty();
        assertThat(Range.parse("bytes=2-4", 10)).contains(new Range(2, 3));
        assertThat(Range.parse("bytes=7-", 10)).contains(new Range(7, 3));
        assertThat(Range.parse("bytes=-3", 10)).contains(new Range(7, 3));
        assertThat(Range.parse("bytes=-30", 10)).contains(new Range(0, 10));
        assertThat(Range.parse("bytes=5-99", 10)).contains(new Range(5, 5));
        assertThat(Range.parse("bytes=0-1,4-5", 10)).as("several ranges: the whole file").isEqualTo(Optional.empty());
        assertThat(Range.parse("bytes=4-2", 10)).isEmpty();
        assertThat(Range.parse("items=0-1", 10)).isEmpty();
        assertThat(Range.parse("bytes=x-1", 10)).isEmpty();
        assertThatThrownBy(() -> Range.parse("bytes=10-", 10)).isInstanceOf(Range.Unsatisfiable.class);
        assertThatThrownBy(() -> Range.parse("bytes=-1", 0)).isInstanceOf(Range.Unsatisfiable.class);
    }
}
```

- [ ] **Step 2: Write the failing engine test**

`engine/http/HttpEngineReadTest.java`:

```java
package dev.jasper.fileserver.engine.http;

import dev.jasper.fileserver.Ports;
import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpEngineReadTest {
    @TempDir Path temp;
    Path shared;
    final List<ServerEvent> events = new CopyOnWriteArrayList<>();
    final LoginThrottle throttle = new LoginThrottle(new TestClock());
    final HttpClient client = HttpClient.newHttpClient();
    HttpEngine engine;

    @BeforeEach void share() throws IOException {
        shared = Files.createDirectories(temp.resolve("shared"));
        Files.createDirectories(shared.resolve("docs"));
        Files.writeString(shared.resolve("docs/a.txt"), "hello");
        Files.writeString(shared.resolve("x & <y>.txt"), "odd");
        Files.writeString(temp.resolve("outside.txt"), "secret");
    }

    @AfterEach void stop() { if (engine != null) engine.stop(); }

    HttpEngine start(ResolvedLogin login) throws Exception {
        var definition = ServerDefinition.create("docs", Protocol.HTTP, shared);
        definition = new ServerDefinition(definition.id(), definition.name(), definition.protocol(), definition.folder(), definition.bind(),
            Ports.free(), definition.passiveRange(), false, definition.login(), List.of(), false, false, false);
        engine = new HttpEngine(definition, new ShareRoot(shared, true), login, new ServerEvents(Clock.systemUTC(), events::add), throttle, List::of);
        engine.start();
        return engine;
    }

    HttpResponse<String> get(String path, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + engine.port() + path));
        if (headers.length > 0) request.headers(headers);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    String raw(String request) throws IOException {
        try (var socket = new Socket(InetAddress.getLoopbackAddress(), engine.port())) {
            socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    @Test void listsFoldersWithEscapedNamesAndSafeHeaders() throws Exception {
        start(ResolvedLogin.anonymous());
        var response = get("/");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("x &amp; &lt;y&gt;.txt", "href=\"/x%20%26%20%3Cy%3E.txt\"", "href=\"/docs/\"")
            .doesNotContain("<y>").doesNotContain("enctype");
        assertThat(response.body().indexOf("docs/")).as("folders first").isLessThan(response.body().indexOf("x &amp;"));
        assertThat(response.headers().firstValue("Content-Security-Policy")).get().asString().contains("default-src 'none'");
        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        var redirect = get("/docs");
        assertThat(redirect.statusCode()).isEqualTo(301);
        assertThat(redirect.headers().firstValue("Location")).contains("/docs/");
        assertThat(get("/docs/").body()).contains("href=\"/\"", "a.txt");
    }

    @Test void sendsFilesAsAttachmentsWithRanges() throws Exception {
        start(ResolvedLogin.anonymous());
        var whole = get("/docs/a.txt");
        assertThat(whole.statusCode()).isEqualTo(200);
        assertThat(whole.body()).isEqualTo("hello");
        assertThat(whole.headers().firstValue("Content-Disposition")).get().asString().startsWith("attachment;");
        assertThat(whole.headers().firstValue("Accept-Ranges")).contains("bytes");
        var part = get("/docs/a.txt", "Range", "bytes=1-3");
        assertThat(part.statusCode()).isEqualTo(206);
        assertThat(part.body()).isEqualTo("ell");
        assertThat(part.headers().firstValue("Content-Range")).contains("bytes 1-3/5");
        assertThat(get("/docs/a.txt", "Range", "bytes=-2").body()).isEqualTo("lo");
        var unsatisfiable = get("/docs/a.txt", "Range", "bytes=9-");
        assertThat(unsatisfiable.statusCode()).isEqualTo(416);
        assertThat(unsatisfiable.headers().firstValue("Content-Range")).contains("bytes */5");
        var head = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + engine.port() + "/docs/a.txt"))
            .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(head.statusCode()).isEqualTo(200);
        assertThat(head.body()).isEmpty();
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.Transferred).anySatisfy(e -> {
            var done = (ServerEvent.Transferred) e;
            assertThat(done.direction()).isEqualTo(ServerEvent.Direction.DOWNLOAD);
            assertThat(done.path()).isEqualTo("/docs/a.txt");
            assertThat(done.bytes()).isEqualTo(5);
            assertThat(done.complete()).isTrue();
        });
    }

    @Test void refusesMissingEscapingAndForeignRequests() throws Exception {
        start(ResolvedLogin.anonymous());
        assertThat(get("/missing.txt").statusCode()).isEqualTo(404);
        String host = "Host: 127.0.0.1:" + engine.port() + "\r\nConnection: close\r\n\r\n";
        assertThat(raw("GET /../outside.txt HTTP/1.1\r\n" + host)).startsWith("HTTP/1.1 403");
        assertThat(raw("GET /%2e%2e/outside.txt HTTP/1.1\r\n" + host)).startsWith("HTTP/1.1 403");
        assertThat(raw("GET /bad%zz HTTP/1.1\r\n" + host)).startsWith("HTTP/1.1 400");
        assertThat(raw("GET / HTTP/1.1\r\nHost: evil.example\r\nConnection: close\r\n\r\n")).startsWith("HTTP/1.1 421");
        assertThat(raw("GET / HTTP/1.1\r\nHost: localhost:" + engine.port() + "\r\nConnection: close\r\n\r\n")).startsWith("HTTP/1.1 200");
        var patch = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + engine.port() + "/new.txt"))
            .method("PATCH", HttpRequest.BodyPublishers.ofString("x")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(patch.statusCode()).isEqualTo(405);
        assertThat(patch.headers().firstValue("Allow")).contains("GET, HEAD");
    }

    @Test void basicAuthChecksTheLoginThroughTheThrottle() throws Exception {
        start(ResolvedLogin.password("me", "pw".toCharArray()));
        String good = "Basic " + Base64.getEncoder().encodeToString("me:pw".getBytes(StandardCharsets.UTF_8));
        String bad = "Basic " + Base64.getEncoder().encodeToString("me:nope".getBytes(StandardCharsets.UTF_8));
        var challenge = get("/");
        assertThat(challenge.statusCode()).isEqualTo(401);
        assertThat(challenge.headers().firstValue("WWW-Authenticate")).get().asString().startsWith("Basic realm=\"docs\"");
        assertThat(get("/", "Authorization", good).statusCode()).isEqualTo(200);
        assertThat(get("/docs/a.txt", "Authorization", good).body()).isEqualTo("hello");
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.LoginSucceeded).as("logged once per client and user").hasSize(1);
        assertThat(get("/", "Authorization", bad).statusCode()).isEqualTo(401);
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.LoginFailed).singleElement()
            .satisfies(e -> assertThat(((ServerEvent.LoginFailed) e).user()).isEqualTo("me"));
        for (int i = 0; i < 9; i++) throttle.failed("127.0.0.1");
        assertThat(get("/", "Authorization", good).statusCode()).as("locked out").isEqualTo(429);
    }

    @Test void aBusyPortFailsWithAClearMessageAndStopCloses() throws Exception {
        start(ResolvedLogin.anonymous());
        int port = engine.port();
        var definition = ServerDefinition.create("again", Protocol.HTTP, shared);
        definition = new ServerDefinition(definition.id(), definition.name(), definition.protocol(), definition.folder(), definition.bind(),
            port, definition.passiveRange(), false, definition.login(), List.of(), false, false, false);
        var second = new HttpEngine(definition, new ShareRoot(shared, true), ResolvedLogin.anonymous(), new ServerEvents(Clock.systemUTC(), e -> { }), throttle, List::of);
        assertThatThrownBy(second::start).isInstanceOf(EngineFailure.class).hasMessage("Port " + port + " is already in use on 127.0.0.1");
        assertThat(engine.addresses()).containsExactly(URI.create("http://127.0.0.1:" + port + "/"));
        engine.stop();
        engine.stop();
        assertThat(engine.addresses()).isEmpty();
        assertThatThrownBy(() -> new Socket(InetAddress.getLoopbackAddress(), port).close()).isInstanceOf(ConnectException.class);
    }
}
```

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.http.*'`
Expected: FAIL — the package does not exist.

- [ ] **Step 4: Write the helpers**

`engine/http/package-info.java`:

```java
/**
 * The HTTP engine on the JDK's {@code com.sun.net.httpserver}: listings, downloads with ranges, Basic auth through
 * the login throttle and, on a writable share, uploads and deletes. May depend on {@code engine}, {@code share},
 * {@code auth}, {@code definition} and the JDK.
 */
package dev.jasper.fileserver.engine.http;
```

`engine/http/HttpPaths.java`:

```java
package dev.jasper.fileserver.engine.http;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Paths, links and text as HTTP needs them. */
final class HttpPaths {
    private HttpPaths() { }

    /** Percent-decodes a raw request path as UTF-8; a plus stays a plus. */
    static String decode(String raw) {
        var bytes = new ByteArrayOutputStream();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '%') {
                if (i + 2 >= raw.length()) throw new IllegalArgumentException("A bad escape in the path");
                int high = Character.digit(raw.charAt(i + 1), 16), low = Character.digit(raw.charAt(i + 2), 16);
                if (high < 0 || low < 0) throw new IllegalArgumentException("A bad escape in the path");
                bytes.write(high * 16 + low);
                i += 2;
            } else if (c < 0x80) {
                bytes.write(c);
            } else {
                throw new IllegalArgumentException("The path is not encoded");
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException notUtf8) {
            throw new IllegalArgumentException("The path is not UTF-8");
        }
    }

    /** One path segment with everything but unreserved characters percent-encoded. */
    static String segment(String name) {
        var out = new StringBuilder();
        for (byte b : name.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-' || c == '.' || c == '_' || c == '~')
                out.append((char) c);
            else out.append('%').append(String.format(Locale.ROOT, "%02X", c));
        }
        return out.toString();
    }

    /** An absolute link to a share path; folders end in a slash. */
    static String href(List<String> segments, boolean directory) {
        if (segments.isEmpty()) return "/";
        var out = new StringBuilder();
        for (String name : segments) out.append('/').append(segment(name));
        return directory ? out.append('/').toString() : out.toString();
    }

    static String html(String text) {
        var out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** Always an attachment: a shared HTML file never runs as this server's page. */
    static String contentDisposition(String name) {
        var fallback = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            fallback.append(c >= 0x20 && c < 0x7f && c != '"' && c != '\\' ? c : '_');
        }
        return "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + segment(name);
    }
}
```

`engine/http/HostCheck.java`:

```java
package dev.jasper.fileserver.engine.http;

import dev.jasper.fileserver.definition.Bind;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Refuses requests whose Host header names some other site's DNS name, which is how a web page reaches a local
 * server through DNS rebinding. IP literals, {@code localhost} and this computer's own names pass; a loopback server
 * accepts loopback names only.
 */
final class HostCheck {
    private final Set<String> names;
    private final boolean loopbackOnly;

    HostCheck(Set<String> names, boolean loopbackOnly) { this.names = Set.copyOf(names); this.loopbackOnly = loopbackOnly; }

    /** Looks up this computer's names, which may be slow: call it off the UI thread. */
    static HostCheck forBind(Bind bind) {
        var names = new HashSet<String>(Set.of("localhost"));
        if (!(bind instanceof Bind.Loopback)) {
            try {
                String host = InetAddress.getLocalHost().getHostName().toLowerCase(Locale.ROOT);
                int dot = host.indexOf('.');
                String shortName = dot < 0 ? host : host.substring(0, dot);
                names.add(host);
                names.add(shortName);
                names.add(shortName + ".local");
            } catch (IOException unknown) { /* addresses and localhost still work */ }
        }
        return new HostCheck(names, bind instanceof Bind.Loopback);
    }

    boolean allows(String header) {
        if (header == null || header.isBlank()) return false;
        String host = hostPart(header.strip()).toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) return !loopbackOnly || host.equals("[::1]");
        if (ipv4(host)) return !loopbackOnly || host.startsWith("127.");
        return names.contains(host);
    }

    private static String hostPart(String header) {
        if (header.startsWith("[")) {
            int close = header.indexOf(']');
            return close < 0 ? header : header.substring(0, close + 1);
        }
        int colon = header.lastIndexOf(':');
        return colon < 0 ? header : header.substring(0, colon);
    }

    private static boolean ipv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return false;
            for (int i = 0; i < part.length(); i++) if (part.charAt(i) < '0' || part.charAt(i) > '9') return false;
            if (Integer.parseInt(part) > 255) return false;
        }
        return true;
    }
}
```

`engine/http/Range.java`:

```java
package dev.jasper.fileserver.engine.http;

import java.util.Optional;

/** One satisfiable byte range of a file. */
record Range(long start, long length) {
    /** A range past the end of the file. */
    static final class Unsatisfiable extends Exception {
        Unsatisfiable() { super(null, null, false, false); }
    }

    /** Empty for no header, several ranges or a header this server ignores: the whole file is sent then. */
    static Optional<Range> parse(String header, long size) throws Unsatisfiable {
        if (header == null) return Optional.empty();
        String value = header.strip();
        if (!value.regionMatches(true, 0, "bytes=", 0, 6)) return Optional.empty();
        String spec = value.substring(6).strip();
        if (spec.contains(",")) return Optional.empty();
        int dash = spec.indexOf('-');
        if (dash < 0) return Optional.empty();
        String first = spec.substring(0, dash).strip(), last = spec.substring(dash + 1).strip();
        try {
            if (first.isEmpty()) {
                if (last.isEmpty()) return Optional.empty();
                long suffix = Long.parseLong(last);
                if (suffix <= 0 || size == 0) throw new Unsatisfiable();
                long length = Math.min(suffix, size);
                return Optional.of(new Range(size - length, length));
            }
            long start = Long.parseLong(first);
            if (start < 0) return Optional.empty();
            if (start >= size) throw new Unsatisfiable();
            long end = last.isEmpty() ? size - 1 : Math.min(Long.parseLong(last), size - 1);
            if (end < start) return Optional.empty();
            return Optional.of(new Range(start, end - start + 1));
        } catch (NumberFormatException malformed) {
            return Optional.empty();
        }
    }
}
```

`engine/http/Listing.java` (Task 10 replaces it to add the forms):

```java
package dev.jasper.fileserver.engine.http;

import dev.jasper.fileserver.engine.Sizes;
import dev.jasper.fileserver.share.ShareEntry;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A folder's HTML listing. */
final class Listing {
    static final String POLICY = "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'";
    private static final DateTimeFormatter MODIFIED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private Listing() { }

    static String render(List<String> segments, List<ShareEntry> entries, ZoneId zone) {
        String title = segments.isEmpty() ? "/" : "/" + String.join("/", segments) + "/";
        var out = new StringBuilder("<!doctype html>\n<html><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width\"><title>").append(HttpPaths.html(title)).append("</title>")
            .append("<style>body{font:14px system-ui,sans-serif;margin:24px}table{border-collapse:collapse}")
            .append("td,th{padding:4px 12px;text-align:left}td.size{text-align:right}</style></head><body>\n")
            .append("<h1>").append(HttpPaths.html(title)).append("</h1>\n")
            .append("<table><tr><th>Name</th><th>Size</th><th>Modified</th></tr>\n");
        if (!segments.isEmpty())
            out.append("<tr><td><a href=\"").append(HttpPaths.href(segments.subList(0, segments.size() - 1), true)).append("\">..</a></td><td></td><td></td></tr>\n");
        for (ShareEntry entry : entries) {
            var path = new ArrayList<>(segments);
            path.add(entry.name());
            out.append("<tr><td><a href=\"").append(HttpPaths.href(path, entry.directory())).append("\">")
                .append(HttpPaths.html(entry.name())).append(entry.directory() ? "/" : "").append("</a></td>")
                .append("<td class=\"size\">").append(entry.directory() ? "" : Sizes.human(entry.size())).append("</td>")
                .append("<td>").append(MODIFIED.format(entry.modified().atZone(zone))).append("</td></tr>\n");
        }
        return out.append("</table></body></html>\n").toString();
    }
}
```

- [ ] **Step 5: Write the authenticator, the handler and the engine**

`engine/http/ThrottledAuthenticator.java`:

```java
package dev.jasper.fileserver.engine.http;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerEvents;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** HTTP Basic auth against one login, through the shared throttle; a successful login is logged once per client and user. */
final class ThrottledAuthenticator extends Authenticator {
    private final String realm;
    private final ResolvedLogin login;
    private final LoginThrottle throttle;
    private final ServerEvents events;
    private final Set<String> welcomed = ConcurrentHashMap.newKeySet();

    ThrottledAuthenticator(String realm, ResolvedLogin login, LoginThrottle throttle, ServerEvents events) {
        this.realm = realm.replaceAll("[\"\\\\\\p{Cntrl}]", "'");
        this.login = login; this.throttle = throttle; this.events = events;
    }

    @Override public Result authenticate(HttpExchange exchange) {
        String remote = FileHandler.remote(exchange);
        if (throttle.lockedOut(remote)) return new Failure(429);
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) return challenge(exchange);
        String user;
        char[] password;
        try {
            String decoded = new String(Base64.getDecoder().decode(header.substring(6).strip()), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon < 0) return challenge(exchange);
            user = decoded.substring(0, colon);
            password = decoded.substring(colon + 1).toCharArray();
        } catch (IllegalArgumentException malformed) {
            return challenge(exchange);
        }
        try {
            Thread.sleep(throttle.delayFor(remote));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Failure(503);
        }
        boolean matches = login.matches(user, password);
        Arrays.fill(password, '\0');
        if (matches) {
            throttle.succeeded(remote);
            if (welcomed.add(remote + "\n" + user)) events.loginSucceeded(remote, user);
            return new Success(new HttpPrincipal(user, realm));
        }
        throttle.failed(remote);
        events.loginFailed(remote, user);
        return challenge(exchange);
    }

    private Result challenge(HttpExchange exchange) {
        exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"" + realm + "\", charset=\"UTF-8\"");
        return new Retry(401);
    }
}
```

`engine/http/FileHandler.java` (Task 10 adds the write methods):

```java
package dev.jasper.fileserver.engine.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.IsFolderException;
import dev.jasper.fileserver.share.PathRefusedException;
import dev.jasper.fileserver.share.ReadOnlyShareException;
import dev.jasper.fileserver.share.ShareEntry;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/** Answers every request of one HTTP server from its share. Runs on the engine's worker threads. */
final class FileHandler implements HttpHandler {
    static final int BUFFER = 64 * 1024;
    private final ShareRoot share;
    private final ServerEvents events;
    private final HostCheck hosts;

    FileHandler(ShareRoot share, ServerEvents events, HostCheck hosts) { this.share = share; this.events = events; this.hosts = hosts; }

    static String remote(HttpExchange exchange) {
        InetSocketAddress address = exchange.getRemoteAddress();
        return address == null || address.getAddress() == null ? "" : ServerAddresses.hostText(address.getAddress());
    }

    @Override public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                dispatch(exchange);
            } catch (PathRefusedException | ReadOnlyShareException refused) {
                text(exchange, 403, refused.getMessage());
            } catch (NoSuchFileException missing) {
                text(exchange, 404, "Not found");
            } catch (IsFolderException | FileAlreadyExistsException | DirectoryNotEmptyException conflict) {
                text(exchange, 409, conflict instanceof DirectoryNotEmptyException ? "The folder is not empty" : conflict.getMessage());
            } catch (IllegalArgumentException bad) {
                text(exchange, 400, bad.getMessage());
            } catch (IOException failure) {
                events.error(remote(exchange), failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
                text(exchange, 500, "The server could not complete the request");
            }
        }
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        if (!hosts.allows(exchange.getRequestHeaders().getFirst("Host"))) {
            text(exchange, 421, "Use this server's address, such as its IP address");
            return;
        }
        String raw = exchange.getRequestURI().getRawPath();
        if (raw == null || !raw.startsWith("/")) throw new IllegalArgumentException("A bad path");
        String path = HttpPaths.decode(raw);
        switch (exchange.getRequestMethod()) {
            case "GET" -> get(exchange, raw, path, true);
            case "HEAD" -> get(exchange, raw, path, false);
            default -> {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                text(exchange, 405, "Method not allowed");
            }
        }
    }

    private void get(HttpExchange exchange, String raw, String path, boolean body) throws IOException {
        ShareEntry entry = share.attributes(path);
        if (entry.directory()) {
            if (!raw.endsWith("/")) {
                exchange.getResponseHeaders().set("Location", raw + "/");
                send(exchange, 301, -1);
                return;
            }
            listing(exchange, path, body);
            return;
        }
        file(exchange, path, entry, body);
    }

    private void listing(HttpExchange exchange, String path, boolean body) throws IOException {
        byte[] page = Listing.render(ShareRoot.segments(path), share.list(path), ZoneId.systemDefault()).getBytes(StandardCharsets.UTF_8);
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/html; charset=utf-8");
        headers.set("Content-Security-Policy", Listing.POLICY);
        headers.set("Cache-Control", "no-store");
        headers.set("Referrer-Policy", "no-referrer");
        if (!body) { send(exchange, 200, -1); return; }
        send(exchange, 200, page.length);
        try (OutputStream out = exchange.getResponseBody()) { out.write(page); }
    }

    private void file(HttpExchange exchange, String path, ShareEntry entry, boolean body) throws IOException {
        long size = entry.size();
        var headers = exchange.getResponseHeaders();
        String type = URLConnection.getFileNameMap().getContentTypeFor(entry.name());
        headers.set("Content-Type", type == null ? "application/octet-stream" : type);
        headers.set("Content-Disposition", HttpPaths.contentDisposition(entry.name()));
        headers.set("Accept-Ranges", "bytes");
        headers.set("Last-Modified", DateTimeFormatter.RFC_1123_DATE_TIME.format(entry.modified().atZone(ZoneOffset.UTC)));
        Optional<Range> range;
        try { range = Range.parse(exchange.getRequestHeaders().getFirst("Range"), size); }
        catch (Range.Unsatisfiable unsatisfiable) {
            headers.set("Content-Range", "bytes */" + size);
            send(exchange, 416, -1);
            return;
        }
        long start = range.map(Range::start).orElse(0L);
        long length = range.map(Range::length).orElse(size);
        int code = range.isPresent() ? 206 : 200;
        range.ifPresent(r -> headers.set("Content-Range", "bytes " + r.start() + "-" + (r.start() + r.length() - 1) + "/" + size));
        if (!body || length == 0) { send(exchange, code, -1); return; }
        try (SeekableByteChannel channel = share.openRead(path)) {
            channel.position(start);
            send(exchange, code, length);
            var transfer = events.transfer(remote(exchange), ServerEvent.Direction.DOWNLOAD, display(path), length, exchange::close);
            boolean complete = false;
            try (OutputStream out = exchange.getResponseBody()) {
                var buffer = ByteBuffer.allocate(BUFFER);
                long remaining = length;
                while (remaining > 0) {
                    buffer.clear().limit((int) Math.min(buffer.capacity(), remaining));
                    int read = channel.read(buffer);
                    if (read < 0) throw new IOException("The file shrank while it was sent");
                    out.write(buffer.array(), 0, read);
                    remaining -= read;
                    transfer.add(read);
                }
                complete = true;
            } finally {
                transfer.finish(complete);
            }
        }
    }

    /** The share path as the activity log shows it, such as "/docs/a.txt". */
    static String display(String path) throws PathRefusedException {
        return "/" + String.join("/", ShareRoot.segments(path));
    }

    static void send(HttpExchange exchange, int code, long length) throws IOException {
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(code, length);
    }

    /** A short plain-text answer; does nothing once a response has started. */
    static void text(HttpExchange exchange, int code, String message) throws IOException {
        if (exchange.getResponseCode() != -1) return;
        byte[] body = (message + "\n").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        send(exchange, code, body.length);
        try (OutputStream out = exchange.getResponseBody()) { out.write(body); }
    }
}
```

`engine/http/HttpEngine.java`:

```java
package dev.jasper.fileserver.engine.http;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;
import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.ServerEngine;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** An HTTP server for one share on the JDK's HTTP server, with at most 16 requests handled at once. */
public final class HttpEngine implements ServerEngine {
    static final int WORKERS = 16;
    private final ServerDefinition definition;
    private final ShareRoot share;
    private final ResolvedLogin login;
    private final ServerEvents events;
    private final LoginThrottle throttle;
    private final Supplier<List<ServerAddresses.Nic>> nics;
    private HttpServer server;
    private ExecutorService workers;
    private int port;

    public HttpEngine(ServerDefinition definition, ShareRoot share, ResolvedLogin login, ServerEvents events, LoginThrottle throttle,
                      Supplier<List<ServerAddresses.Nic>> nics) {
        this.definition = definition; this.share = share; this.login = login; this.events = events; this.throttle = throttle; this.nics = nics;
    }

    @Override public synchronized void start() throws EngineFailure {
        if (server != null) return;
        InetAddress address = ServerAddresses.bindAddress(definition.bind(), nics.get());
        HttpServer created;
        try { created = HttpServer.create(new InetSocketAddress(address, definition.port()), 64); }
        catch (IOException failure) { throw EngineFailure.bind(failure, definition.port(), ServerAddresses.describe(address), definition.protocol()); }
        ExecutorService pool = Executors.newFixedThreadPool(WORKERS, Thread.ofPlatform().daemon().name("file-server-http-", 0).factory());
        HttpContext context = created.createContext("/", new FileHandler(share, events, HostCheck.forBind(definition.bind())));
        if (!login.isAnonymous()) context.setAuthenticator(new ThrottledAuthenticator(definition.name(), login, throttle, events));
        created.setExecutor(pool);
        created.start();
        server = created;
        workers = pool;
        port = created.getAddress().getPort();
    }

    @Override public synchronized void stop() {
        if (server == null) return;
        server.stop(0);
        workers.shutdownNow();
        server = null;
        workers = null;
    }

    @Override public synchronized List<URI> addresses() {
        if (server == null) return List.of();
        return ServerAddresses.urls(definition.protocol(), definition.tls(), ServerAddresses.reachable(definition.bind(), nics.get()), port, login.user());
    }

    /** The bound port; 0 before the first start. */
    public synchronized int port() { return port; }
}
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:check`
Expected: PASS (including `verifyPluginArchitecture` with `com.sun.net.httpserver`).

- [ ] **Step 7: Commit**

```bash
git add plugins/fileserver/src
git commit -m "File Server: HTTP engine serves listings and files with ranges and Basic auth

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: HTTP engine — uploads and deletes

**Files:**
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/engine/http/Multipart.java`
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/engine/http/FileHandler.java` (`dispatch`, the `listing` call, the catch list, new write methods)
- Replace: `plugins/fileserver/src/main/java/dev/jasper/fileserver/engine/http/Listing.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/http/{MultipartTest,HttpEngineWriteTest}.java`

**Interfaces:**
- Consumes: Task 9's handler and helpers.
- Produces: on a writable share, `PUT` creates (201) or replaces (204); `DELETE` removes a file or empty folder (204; 409 for a full folder); `POST` to a folder with `multipart/form-data` stores every file part (303 back to the folder) or with `_method=DELETE&name=…` deletes one entry (303). A request whose `Origin` names another site gets 403; a read-only share answers every write with 403. Package-private `Multipart` with `boundary(String)`, `baseName(String)`, `read(InputStream, String boundary, FileSink)` and `Multipart.Malformed extends IOException` (mapped to 400).

Clients that matter here: browsers (the listing's forms; they percent-encode `"` in file names and send `C:\fakepath\` on some systems) and `curl -T`/`curl -F`.

- [ ] **Step 1: Write the failing tests**

`engine/http/MultipartTest.java`:

```java
package dev.jasper.fileserver.engine.http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultipartTest {
    record Part(String fileName, String content) { }

    /** Hands out one byte per read, so every delimiter straddles reads. */
    static InputStream trickle(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new InputStream() {
            int at;
            @Override public int read() { return at < bytes.length ? bytes[at++] & 0xff : -1; }
            @Override public int read(byte[] target, int offset, int length) {
                if (at >= bytes.length) return -1;
                if (length == 0) return 0;
                target[offset] = bytes[at++];
                return 1;
            }
        };
    }

    static List<Part> read(String body) throws IOException {
        var parts = new ArrayList<Part>();
        Multipart.read(trickle(body), "XyZ", (name, content) -> parts.add(new Part(name, new String(content.readAllBytes(), StandardCharsets.UTF_8))));
        return parts;
    }

    @Test void readsFilePartsAndSkipsFields() throws IOException {
        String body = "preamble\r\n--XyZ\r\nContent-Disposition: form-data; name=\"note\"\r\n\r\nskip me\r\n--XyZ\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"C:\\fakepath\\a.txt\"\r\nContent-Type: text/plain\r\n\r\n"
            + "line one\r\n--not the boundary\r\n--XyZ\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"b %22q%22.bin\"\r\n\r\n\r\n--XyZ--\r\nepilogue";
        assertThat(read(body)).containsExactly(new Part("C:\\fakepath\\a.txt", "line one\r\n--not the boundary"), new Part("b \"q\".bin", ""));
        assertThat(read("--XyZ--\r\n")).isEmpty();
    }

    @Test void refusesMalformedForms() {
        assertThatThrownBy(() -> read("--XyZ\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a\"\r\n\r\nno end"))
            .isInstanceOf(Multipart.Malformed.class);
        assertThatThrownBy(() -> read("no boundary at all")).isInstanceOf(Multipart.Malformed.class);
        assertThatThrownBy(() -> read("--XyZ\r\n" + "X-Long: " + "a".repeat(20_000) + "\r\n\r\n")).isInstanceOf(Multipart.Malformed.class);
        assertThatThrownBy(() -> read("--XyZ??")).isInstanceOf(Multipart.Malformed.class);
    }

    @Test void findsTheBoundaryAndTheBaseName() {
        assertThat(Multipart.boundary("multipart/form-data; boundary=----WebKitFormBoundary7MA4")).isEqualTo("----WebKitFormBoundary7MA4");
        assertThat(Multipart.boundary("multipart/form-data; charset=utf-8; boundary=\"a b\"")).isEqualTo("a b");
        assertThatThrownBy(() -> Multipart.boundary("multipart/form-data")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Multipart.baseName("C:\\fakepath\\a.txt")).isEqualTo("a.txt");
        assertThat(Multipart.baseName("dir/sub/b.txt")).isEqualTo("b.txt");
        assertThat(Multipart.baseName("plain.txt")).isEqualTo("plain.txt");
    }
}
```

`engine/http/HttpEngineWriteTest.java`:

```java
package dev.jasper.fileserver.engine.http;

import dev.jasper.fileserver.Ports;
import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class HttpEngineWriteTest {
    @TempDir Path temp;
    Path shared;
    final List<ServerEvent> events = new CopyOnWriteArrayList<>();
    final HttpClient client = HttpClient.newHttpClient();
    HttpEngine engine;

    @BeforeEach void share() throws IOException {
        shared = Files.createDirectories(temp.resolve("shared"));
        Files.createDirectories(shared.resolve("docs"));
        Files.writeString(shared.resolve("docs/a.txt"), "hello");
        Files.writeString(shared.resolve("docs/keep.txt"), "keep");
    }

    @AfterEach void stop() { if (engine != null) engine.stop(); }

    void start(boolean writable) throws Exception {
        var base = ServerDefinition.create("drop", Protocol.HTTP, shared);
        var definition = new ServerDefinition(base.id(), base.name(), base.protocol(), base.folder(), base.bind(), Ports.free(),
            base.passiveRange(), writable, base.login(), List.of(), false, false, false);
        engine = new HttpEngine(definition, new ShareRoot(shared, !writable), ResolvedLogin.anonymous(),
            new ServerEvents(Clock.systemUTC(), events::add), new LoginThrottle(new TestClock()), List::of);
        engine.start();
    }

    HttpResponse<String> send(String method, String path, HttpRequest.BodyPublisher body, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + engine.port() + path)).method(method, body);
        if (headers.length > 0) request.headers(headers);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    String raw(String head, String body) throws IOException {
        try (var socket = new Socket(InetAddress.getLoopbackAddress(), engine.port())) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            String request = head + "Host: 127.0.0.1:" + engine.port() + "\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().write(bytes);
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    @Test void putCreatesThenReplacesAndReportsTheUpload() throws Exception {
        start(true);
        assertThat(send("PUT", "/docs/new.txt", HttpRequest.BodyPublishers.ofString("abc")).statusCode()).isEqualTo(201);
        assertThat(send("PUT", "/docs/new.txt", HttpRequest.BodyPublishers.ofString("abcd")).statusCode()).isEqualTo(204);
        assertThat(shared.resolve("docs/new.txt")).hasContent("abcd");
        assertThat(send("PUT", "/docs", HttpRequest.BodyPublishers.ofString("x")).statusCode()).isEqualTo(409);
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.Transferred).extracting(e -> ((ServerEvent.Transferred) e).bytes())
            .containsExactlyInAnyOrder(3L, 4L);
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.Transferred)
            .allSatisfy(e -> assertThat(((ServerEvent.Transferred) e).direction()).isEqualTo(ServerEvent.Direction.UPLOAD));
    }

    @Test void deleteRemovesFilesAndRefusesFullFolders() throws Exception {
        start(true);
        assertThat(send("DELETE", "/docs/a.txt", HttpRequest.BodyPublishers.noBody()).statusCode()).isEqualTo(204);
        assertThat(shared.resolve("docs/a.txt")).doesNotExist();
        assertThat(events).anySatisfy(e -> assertThat(e).isEqualTo(new ServerEvent.Changed(e.at(), "127.0.0.1", "deleted /docs/a.txt")));
        assertThat(send("DELETE", "/docs", HttpRequest.BodyPublishers.noBody()).statusCode()).isEqualTo(409);
        assertThat(send("DELETE", "/missing.txt", HttpRequest.BodyPublishers.noBody()).statusCode()).isEqualTo(404);
    }

    @Test void theListingsFormsUploadAndDelete() throws Exception {
        start(true);
        assertThat(send("GET", "/docs/", HttpRequest.BodyPublishers.noBody()).body())
            .contains("enctype=\"multipart/form-data\"", "name=\"_method\" value=\"DELETE\"", "name=\"name\" value=\"a.txt\"");
        var form = new ByteArrayOutputStream();
        form.writeBytes(("--XyZ\r\nContent-Disposition: form-data; name=\"file\"; filename=\"one.txt\"\r\n\r\nfirst\r\n"
            + "--XyZ\r\nContent-Disposition: form-data; name=\"file\"; filename=\"C:\\fakepath\\two.bin\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        form.writeBytes(new byte[]{0, 13, 10, 45, 45, 88, 121, 0});
        form.writeBytes("\r\n--XyZ--\r\n".getBytes(StandardCharsets.UTF_8));
        var upload = send("POST", "/docs/", HttpRequest.BodyPublishers.ofByteArray(form.toByteArray()), "Content-Type", "multipart/form-data; boundary=XyZ");
        assertThat(upload.statusCode()).isEqualTo(303);
        assertThat(upload.headers().firstValue("Location")).contains("/docs/");
        assertThat(shared.resolve("docs/one.txt")).hasContent("first");
        assertThat(Files.readAllBytes(shared.resolve("docs/two.bin"))).containsExactly(0, 13, 10, 45, 45, 88, 121, 0);
        var delete = send("POST", "/docs", HttpRequest.BodyPublishers.ofString("_method=DELETE&name=a.txt"), "Content-Type", "application/x-www-form-urlencoded");
        assertThat(delete.statusCode()).isEqualTo(303);
        assertThat(delete.headers().firstValue("Location")).contains("/docs/");
        assertThat(shared.resolve("docs/a.txt")).doesNotExist();
        assertThat(send("POST", "/docs/keep.txt", HttpRequest.BodyPublishers.ofString("_method=DELETE&name=x"), "Content-Type", "application/x-www-form-urlencoded").statusCode())
            .as("forms go to folders").isEqualTo(400);
        assertThat(send("POST", "/docs/", HttpRequest.BodyPublishers.ofString("--XyZ??"), "Content-Type", "multipart/form-data; boundary=XyZ").statusCode())
            .isEqualTo(400);
    }

    @Test void requestsFromOtherSitesAreRefused() throws Exception {
        start(true);
        String form = "Content-Type: application/x-www-form-urlencoded\r\n";
        assertThat(raw("POST /docs/ HTTP/1.1\r\nOrigin: http://evil.example\r\n" + form, "_method=DELETE&name=a.txt")).startsWith("HTTP/1.1 403");
        assertThat(shared.resolve("docs/a.txt")).exists();
        assertThat(raw("POST /docs/ HTTP/1.1\r\nOrigin: http://127.0.0.1:" + engine.port() + "\r\n" + form, "_method=DELETE&name=a.txt")).startsWith("HTTP/1.1 303");
        assertThat(shared.resolve("docs/a.txt")).doesNotExist();
    }

    @Test void aReadOnlyShareRefusesEveryWriteAndOffersNoForms() throws Exception {
        start(false);
        assertThat(send("PUT", "/docs/new.txt", HttpRequest.BodyPublishers.ofString("x")).statusCode()).isEqualTo(403);
        assertThat(send("DELETE", "/docs/a.txt", HttpRequest.BodyPublishers.noBody()).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/docs/", HttpRequest.BodyPublishers.ofString("_method=DELETE&name=a.txt"), "Content-Type", "application/x-www-form-urlencoded").statusCode())
            .isEqualTo(403);
        assertThat(shared.resolve("docs/a.txt")).exists();
        assertThat(send("GET", "/docs/", HttpRequest.BodyPublishers.noBody()).body()).doesNotContain("<form");
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.http.*'`
Expected: FAIL — `Multipart` does not exist; writes answer 405.

- [ ] **Step 3: Write `Multipart`**

```java
package dev.jasper.fileserver.engine.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A streaming reader of {@code multipart/form-data}: each file part reaches the sink as a stream that ends at the
 * next boundary, so uploads of any size never sit in memory. Fields without a file name are skipped.
 */
final class Multipart {
    /** Receives one file part; whatever it leaves unread is skipped. */
    @FunctionalInterface interface FileSink { void accept(String fileName, InputStream content) throws IOException; }

    /** A form this reader cannot make sense of; the handler answers 400. */
    static final class Malformed extends IOException {
        Malformed(String message) { super(message); }
    }

    private static final int MAX_HEADERS = 16 * 1024;
    private static final Pattern BOUNDARY = Pattern.compile("(?i)boundary=(?:\"([^\"]{1,70})\"|([^;\\s]{1,70}))");
    private static final Pattern FILENAME = Pattern.compile("(?i)filename=\"([^\"]*)\"");
    private final InputStream in;
    private final byte[] delimiter;
    private final byte[] buffer = new byte[64 * 1024];
    private int start, end;
    private boolean eof;

    private Multipart(InputStream in, String boundary) {
        this.in = in;
        this.delimiter = ("\r\n--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        // The body starts with "--boundary"; a virtual CRLF in front lets one delimiter match every boundary.
        buffer[0] = '\r';
        buffer[1] = '\n';
        end = 2;
    }

    static String boundary(String contentType) {
        Matcher match = BOUNDARY.matcher(contentType == null ? "" : contentType);
        if (!match.find()) throw new IllegalArgumentException("The form has no boundary");
        return match.group(1) != null ? match.group(1) : match.group(2);
    }

    /** The last component of a file name a browser sent, which may be a full Windows path. */
    static String baseName(String fileName) {
        int cut = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        return fileName.substring(cut + 1).strip();
    }

    static void read(InputStream in, String boundary, FileSink sink) throws IOException {
        var reader = new Multipart(in, boundary);
        if (!reader.skipPreamble()) return;
        while (true) {
            Map<String, String> headers = reader.headers();
            Optional<String> fileName = fileName(headers.getOrDefault("content-disposition", ""));
            var body = reader.new Body();
            if (fileName.isPresent()) sink.accept(fileName.get(), body);
            body.transferTo(OutputStream.nullOutputStream());
            if (!reader.afterDelimiter()) return;
        }
    }

    /** Browsers percent-encode a quote in a file name and leave backslashes alone. */
    static Optional<String> fileName(String disposition) {
        Matcher match = FILENAME.matcher(disposition);
        return match.find() ? Optional.of(match.group(1).replace("%22", "\"")) : Optional.empty();
    }

    private boolean skipPreamble() throws IOException {
        while (true) {
            int at = indexOf(start, end);
            if (at >= 0) { start = at + delimiter.length; return afterDelimiter(); }
            start = Math.max(start, end - (delimiter.length - 1));
            if (!fill()) throw new Malformed("The form has no parts");
        }
    }

    /** After a boundary: true when another part follows, false after the closing one. */
    private boolean afterDelimiter() throws IOException {
        need(2);
        byte first = buffer[start], second = buffer[start + 1];
        start += 2;
        if (first == '-' && second == '-') return false;
        if (first == '\r' && second == '\n') return true;
        throw new Malformed("The form is malformed");
    }

    private Map<String, String> headers() throws IOException {
        var headers = new HashMap<String, String>();
        int used = 0;
        while (true) {
            int eol = indexOfCrlf();
            while (eol < 0) {
                if (end - start > MAX_HEADERS) throw new Malformed("Form part headers are too long");
                if (!fill()) throw new Malformed("The form ended early");
                eol = indexOfCrlf();
            }
            String line = new String(buffer, start, eol - start, StandardCharsets.UTF_8);
            used += eol - start + 2;
            start = eol + 2;
            if (used > MAX_HEADERS) throw new Malformed("Form part headers are too long");
            if (line.isEmpty()) return headers;
            int colon = line.indexOf(':');
            if (colon > 0) headers.put(line.substring(0, colon).strip().toLowerCase(Locale.ROOT), line.substring(colon + 1).strip());
        }
    }

    private boolean fill() throws IOException {
        if (eof) return false;
        if (start > 0) {
            System.arraycopy(buffer, start, buffer, 0, end - start);
            end -= start;
            start = 0;
        }
        if (end == buffer.length) throw new Malformed("Form part headers are too long");
        int read = in.read(buffer, end, buffer.length - end);
        if (read < 0) { eof = true; return false; }
        end += read;
        return true;
    }

    private void need(int count) throws IOException {
        while (end - start < count) if (!fill()) throw new Malformed("The form ended early");
    }

    private int indexOf(int from, int to) {
        outer:
        for (int i = from; i <= to - delimiter.length; i++) {
            for (int j = 0; j < delimiter.length; j++) if (buffer[i + j] != delimiter[j]) continue outer;
            return i;
        }
        return -1;
    }

    private int indexOfCrlf() {
        for (int i = start; i + 1 < end; i++) if (buffer[i] == '\r' && buffer[i + 1] == '\n') return i;
        return -1;
    }

    /** One part's content, ending just before the next boundary. */
    private final class Body extends InputStream {
        private boolean done;

        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            int count = read(one, 0, 1);
            return count < 0 ? -1 : one[0] & 0xff;
        }

        @Override public int read(byte[] target, int offset, int length) throws IOException {
            if (done) return -1;
            if (length == 0) return 0;
            while (true) {
                int at = indexOf(start, end);
                if (at == start) { start += delimiter.length; done = true; return -1; }
                int available = at >= 0 ? at - start : end - start - (delimiter.length - 1);
                if (available > 0) {
                    int count = Math.min(length, available);
                    System.arraycopy(buffer, start, target, offset, count);
                    start += count;
                    return count;
                }
                if (!fill()) throw new Malformed("The form ended early");
            }
        }
    }
}
```

- [ ] **Step 4: Add the write paths to `FileHandler`**

Add imports `java.io.InputStream`, `java.net.URLDecoder`, `java.util.HashMap`, `java.util.Locale`, `java.util.Map`. Add a constant `static final int FORM_LIMIT = 64 * 1024;`.

In `handle`, add a catch before `catch (IllegalArgumentException bad)`:

```java
            } catch (Multipart.Malformed malformed) {
                text(exchange, 400, malformed.getMessage());
```

Replace `dispatch` with:

```java
    private void dispatch(HttpExchange exchange) throws IOException {
        if (!hosts.allows(exchange.getRequestHeaders().getFirst("Host"))) {
            text(exchange, 421, "Use this server's address, such as its IP address");
            return;
        }
        String raw = exchange.getRequestURI().getRawPath();
        if (raw == null || !raw.startsWith("/")) throw new IllegalArgumentException("A bad path");
        String path = HttpPaths.decode(raw);
        switch (exchange.getRequestMethod()) {
            case "GET" -> get(exchange, raw, path, true);
            case "HEAD" -> get(exchange, raw, path, false);
            case "PUT", "DELETE", "POST" -> {
                if (!sameOrigin(exchange)) { text(exchange, 403, "Requests from other sites are refused"); return; }
                if (share.readOnly()) throw new ReadOnlyShareException();
                switch (exchange.getRequestMethod()) {
                    case "PUT" -> put(exchange, path);
                    case "DELETE" -> delete(exchange, path);
                    default -> post(exchange, raw, path);
                }
            }
            default -> {
                exchange.getResponseHeaders().set("Allow", share.readOnly() ? "GET, HEAD" : "GET, HEAD, PUT, DELETE, POST");
                text(exchange, 405, "Method not allowed");
            }
        }
    }
```

In `listing`, pass whether the share is writable:

```java
        byte[] page = Listing.render(ShareRoot.segments(path), share.list(path), !share.readOnly(), ZoneId.systemDefault())
            .getBytes(StandardCharsets.UTF_8);
```

Add the write methods:

```java
    /** A browser form from another site carries its Origin; curl and the share's own pages pass. */
    private static boolean sameOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null) return true;
        String host = exchange.getRequestHeaders().getFirst("Host");
        return host != null && (origin.equalsIgnoreCase("http://" + host) || origin.equalsIgnoreCase("https://" + host));
    }

    private void put(HttpExchange exchange, String path) throws IOException {
        boolean existed;
        try { existed = !share.attributes(path).directory(); }
        catch (NoSuchFileException absent) { existed = false; }
        long total = -1;
        String length = exchange.getRequestHeaders().getFirst("Content-Length");
        if (length != null) {
            try { total = Long.parseLong(length.strip()); } catch (NumberFormatException unknown) { total = -1; }
        }
        receive(exchange, exchange.getRequestBody(), path, total);
        send(exchange, existed ? 204 : 201, -1);
    }

    private void delete(HttpExchange exchange, String path) throws IOException {
        share.delete(path);
        events.changed(remote(exchange), "deleted " + display(path));
        send(exchange, 204, -1);
    }

    /** The listing's forms: an upload of any number of files, or a delete of one entry. Answers with the folder. */
    private void post(HttpExchange exchange, String raw, String path) throws IOException {
        if (!share.attributes(path).directory()) throw new IllegalArgumentException("Forms are posted to a folder");
        List<String> folder = ShareRoot.segments(path);
        String contentType = Optional.ofNullable(exchange.getRequestHeaders().getFirst("Content-Type")).orElse("");
        String type = contentType.toLowerCase(Locale.ROOT);
        if (type.startsWith("multipart/form-data")) {
            Multipart.read(exchange.getRequestBody(), Multipart.boundary(contentType), (fileName, content) -> {
                String name = Multipart.baseName(fileName);
                if (!name.isEmpty()) receive(exchange, content, child(folder, name), -1);
            });
        } else if (type.startsWith("application/x-www-form-urlencoded")) {
            Map<String, String> form = form(exchange.getRequestBody());
            String name = form.getOrDefault("name", "");
            if (!"DELETE".equals(form.get("_method")) || name.isEmpty()) throw new IllegalArgumentException("This form is not understood");
            String target = child(folder, name);
            share.delete(target);
            events.changed(remote(exchange), "deleted " + display(target));
        } else {
            throw new IllegalArgumentException("This form is not understood");
        }
        exchange.getResponseHeaders().set("Location", raw.endsWith("/") ? raw : raw + "/");
        send(exchange, 303, -1);
    }

    private void receive(HttpExchange exchange, InputStream in, String path, long total) throws IOException {
        try (OutputStream out = share.openWrite(path, false)) {
            var transfer = events.transfer(remote(exchange), ServerEvent.Direction.UPLOAD, display(path), total, exchange::close);
            boolean complete = false;
            try {
                byte[] buffer = new byte[BUFFER];
                for (int read; (read = in.read(buffer)) >= 0; ) {
                    out.write(buffer, 0, read);
                    transfer.add(read);
                }
                complete = true;
            } finally {
                transfer.finish(complete);
            }
        }
    }

    private static String child(List<String> folder, String name) {
        return "/" + String.join("/", folder) + (folder.isEmpty() ? "" : "/") + name;
    }

    private static Map<String, String> form(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(FORM_LIMIT + 1);
        if (bytes.length > FORM_LIMIT) throw new IllegalArgumentException("The form is too large");
        var form = new HashMap<String, String>();
        for (String pair : new String(bytes, StandardCharsets.US_ASCII).split("&")) {
            if (pair.isEmpty()) continue;
            int equals = pair.indexOf('=');
            String key = URLDecoder.decode(equals < 0 ? pair : pair.substring(0, equals), StandardCharsets.UTF_8);
            String value = equals < 0 ? "" : URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
            form.putIfAbsent(key, value);
        }
        return form;
    }
```

- [ ] **Step 5: Replace `Listing` with the writable version**

```java
package dev.jasper.fileserver.engine.http;

import dev.jasper.fileserver.engine.Sizes;
import dev.jasper.fileserver.share.ShareEntry;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A folder's HTML listing; a writable share adds an upload form and a Delete button per entry. */
final class Listing {
    static final String POLICY = "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'";
    private static final DateTimeFormatter MODIFIED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private Listing() { }

    static String render(List<String> segments, List<ShareEntry> entries, boolean writable, ZoneId zone) {
        String title = segments.isEmpty() ? "/" : "/" + String.join("/", segments) + "/";
        var out = new StringBuilder("<!doctype html>\n<html><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width\"><title>").append(HttpPaths.html(title)).append("</title>")
            .append("<style>body{font:14px system-ui,sans-serif;margin:24px}table{border-collapse:collapse}")
            .append("td,th{padding:4px 12px;text-align:left}td.size{text-align:right}form{margin:0}</style></head><body>\n")
            .append("<h1>").append(HttpPaths.html(title)).append("</h1>\n");
        if (writable)
            out.append("<form method=\"post\" enctype=\"multipart/form-data\"><input type=\"file\" name=\"file\" multiple> ")
                .append("<button>Upload</button></form>\n");
        out.append("<table><tr><th>Name</th><th>Size</th><th>Modified</th>").append(writable ? "<th></th>" : "").append("</tr>\n");
        if (!segments.isEmpty())
            out.append("<tr><td><a href=\"").append(HttpPaths.href(segments.subList(0, segments.size() - 1), true)).append("\">..</a></td>")
                .append("<td></td><td></td>").append(writable ? "<td></td>" : "").append("</tr>\n");
        for (ShareEntry entry : entries) {
            var path = new ArrayList<>(segments);
            path.add(entry.name());
            out.append("<tr><td><a href=\"").append(HttpPaths.href(path, entry.directory())).append("\">")
                .append(HttpPaths.html(entry.name())).append(entry.directory() ? "/" : "").append("</a></td>")
                .append("<td class=\"size\">").append(entry.directory() ? "" : Sizes.human(entry.size())).append("</td>")
                .append("<td>").append(MODIFIED.format(entry.modified().atZone(zone))).append("</td>");
            if (writable)
                out.append("<td><form method=\"post\"><input type=\"hidden\" name=\"_method\" value=\"DELETE\">")
                    .append("<input type=\"hidden\" name=\"name\" value=\"").append(HttpPaths.html(entry.name())).append("\">")
                    .append("<button>Delete</button></form></td>");
            out.append("</tr>\n");
        }
        return out.append("</table></body></html>\n").toString();
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.http.*'`
Expected: PASS (Task 9's tests included).

- [ ] **Step 7: Commit**

```bash
git add plugins/fileserver/src
git commit -m "File Server: HTTP uploads and deletes on writable shares

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Runtime — the server registry

**Files:**
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/runtime/{ServerState,ActivityLog,ServerRegistry,Engines,TransferActivities,package-info}.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/runtime/{ActivityLogTest,TransferActivitiesTest,ServerRegistryTest,EnginesTest}.java`

**Interfaces:**
- Consumes: Tasks 4–10.
- Produces (package `dev.jasper.fileserver.runtime`):
  - `record ServerState(Phase phase, Optional<String> reason)` with `enum Phase { STOPPED, STARTING, RUNNING, STOPPING, FAILED, WAITING_FOR_VAULT; boolean startable(); }`, constants `STOPPED`, `STARTING`, `RUNNING`, `STOPPING`, `WAITING_FOR_VAULT`, `static failed(String)`
  - `ActivityLog(int capacity)`: `record Entry(Instant at, String text)`, `boolean add(ServerEvent)` (true when the event is logged), `List<Entry> entries()`, `void clear()`, `static Optional<String> describe(ServerEvent)`
  - `ServerRegistry(EngineFactory engines, LoginResolver logins, Executor background, Executor ui, Clock clock, IntSupplier activityEntries, Consumer<ServerEvent> observer)`; `@FunctionalInterface EngineFactory { ServerEngine create(ServerDefinition, ShareRoot, ResolvedLogin, ServerEvents) throws EngineFailure; }`; `record View(ServerDefinition definition, boolean temporary, ServerState state, List<URI> addresses, Optional<String> user, boolean restartNeeded)`; methods `setDefinitions(List)`, `List<View> servers()`, `Optional<View> server(UUID)`, `CompletableFuture<Void> start(UUID, WindowHandle ownerOrNull)`, `startTemporary(ServerDefinition, WindowHandle)`, `stop(UUID)`, `cancelStart(UUID)`, `autostart()`, `vaultUnlocked()`, `stopAll()`, `shutdown()`, `refreshAddresses()`, `int running()`, `int active()`, `Optional<String> password(UUID)`, `List<ActivityLog.Entry> activity(UUID)`, `clearActivity(UUID)`, `Subscription onChanged(Runnable)`, `Subscription onActivity(Consumer<UUID>)`. UI thread only; futures complete on the UI thread and never exceptionally.
  - `Engines.AVAILABLE` (`Set.of(Protocol.HTTP)`), `static EngineFactory factory(LoginThrottle, Supplier<List<ServerAddresses.Nic>>)`
  - `TransferActivities(Function<ActivitySpec, ActivityHandle> begin, String activateActionId)` with `void accept(ServerEvent)`: a transfer becomes an SDK activity once it has run for a second.

- [ ] **Step 1: Write the failing tests**

`runtime/ActivityLogTest.java`:

```java
package dev.jasper.fileserver.runtime;

import dev.jasper.fileserver.engine.ServerEvent;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ActivityLogTest {
    static final Instant AT = Instant.parse("2026-09-30T12:00:00Z");

    @Test void describesWhatPeopleCareAbout() {
        assertThat(ActivityLog.describe(new ServerEvent.LoginSucceeded(AT, "192.168.1.44", "dustin"))).contains("192.168.1.44 login dustin");
        assertThat(ActivityLog.describe(new ServerEvent.LoginFailed(AT, "192.168.1.44", "root"))).contains("192.168.1.44 login failed for root");
        assertThat(ActivityLog.describe(new ServerEvent.LoginFailed(AT, "192.168.1.44", ""))).contains("192.168.1.44 login failed");
        assertThat(ActivityLog.describe(new ServerEvent.Transferred(AT, "192.168.1.44", 1, ServerEvent.Direction.UPLOAD, "/IMG_2231.HEIC", 3_250_585, Duration.ofMillis(400), true)))
            .contains("192.168.1.44 ↑ /IMG_2231.HEIC  3.1 MB  0.4 s");
        assertThat(ActivityLog.describe(new ServerEvent.Transferred(AT, "10.0.0.2", 2, ServerEvent.Direction.DOWNLOAD, "/a", 10, Duration.ofSeconds(2), false)))
            .contains("10.0.0.2 ↓ /a  10 B  2.0 s  (stopped)");
        assertThat(ActivityLog.describe(new ServerEvent.Changed(AT, "10.0.0.2", "deleted /a"))).contains("10.0.0.2 deleted /a");
        assertThat(ActivityLog.describe(new ServerEvent.Failed(AT, "", "disk full"))).contains("error: disk full");
        assertThat(ActivityLog.describe(new ServerEvent.Connected(AT, "10.0.0.2"))).contains("10.0.0.2 connected");
        assertThat(ActivityLog.describe(new ServerEvent.TransferProgress(AT, "10.0.0.2", 1, 5, 10))).isEmpty();
    }

    @Test void keepsTheNewestEntries() {
        var log = new ActivityLog(2);
        assertThat(log.add(new ServerEvent.Connected(AT, "a"))).isTrue();
        assertThat(log.add(new ServerEvent.TransferProgress(AT, "a", 1, 1, 1))).isFalse();
        log.add(new ServerEvent.Connected(AT, "b"));
        log.add(new ServerEvent.Connected(AT, "c"));
        assertThat(log.entries()).extracting(ActivityLog.Entry::text).containsExactly("b connected", "c connected");
        log.clear();
        assertThat(log.entries()).isEmpty();
    }
}
```

`runtime/TransferActivitiesTest.java`:

```java
package dev.jasper.fileserver.runtime;

import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TransferActivitiesTest {
    static final Instant AT = Instant.parse("2026-09-30T12:00:00Z");
    final List<ActivitySpec> begun = new ArrayList<>();
    final List<String> updates = new ArrayList<>();

    ActivityHandle handle() {
        return new ActivityHandle() {
            @Override public UUID id() { return UUID.randomUUID(); }
            @Override public void progress(double fraction, String detail) { updates.add(Math.round(fraction * 100) + "% " + detail); }
            @Override public void detail(String detail) { updates.add(detail); }
            @Override public void succeed(String detail) { updates.add("done " + detail); }
            @Override public void fail(String detail) { updates.add("failed " + detail); }
            @Override public void cancelled() { updates.add("cancelled"); }
        };
    }

    @Test void onlyTransfersLongerThanASecondBecomeActivities() {
        var activities = new TransferActivities(spec -> { begun.add(spec); return handle(); }, "dev.jasper.fileserver.show-panel");
        boolean[] cancelled = {false};
        activities.accept(new ServerEvent.TransferStarted(AT, "10.0.0.2", 1, ServerEvent.Direction.DOWNLOAD, "/big.iso", 4096, () -> cancelled[0] = true));
        activities.accept(new ServerEvent.TransferProgress(AT.plusMillis(500), "10.0.0.2", 1, 1024, 4096));
        assertThat(begun).isEmpty();
        activities.accept(new ServerEvent.TransferProgress(AT.plusMillis(1200), "10.0.0.2", 1, 2048, 4096));
        assertThat(begun).singleElement().satisfies(spec -> {
            assertThat(spec.title()).isEqualTo("Sending big.iso");
            assertThat(spec.detail()).isEqualTo("10.0.0.2");
            assertThat(spec.activateActionId()).contains("dev.jasper.fileserver.show-panel");
            spec.cancel().orElseThrow().run();
        });
        assertThat(cancelled[0]).isTrue();
        activities.accept(new ServerEvent.Transferred(AT.plusSeconds(2), "10.0.0.2", 1, ServerEvent.Direction.DOWNLOAD, "/big.iso", 4096, Duration.ofSeconds(2), true));
        activities.accept(new ServerEvent.TransferStarted(AT, "10.0.0.2", 2, ServerEvent.Direction.UPLOAD, "/quick.txt", -1, () -> { }));
        activities.accept(new ServerEvent.Transferred(AT.plusMillis(100), "10.0.0.2", 2, ServerEvent.Direction.UPLOAD, "/quick.txt", 3, Duration.ofMillis(100), true));
        assertThat(updates).containsExactly("50% 2.0 KB of 4.0 KB", "done 4.0 KB in 2.0 s");
        assertThat(begun).hasSize(1);
    }
}
```

`runtime/EnginesTest.java`:

```java
package dev.jasper.fileserver.runtime;

import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.engine.http.HttpEngine;
import dev.jasper.fileserver.share.ShareRoot;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnginesTest {
    @Test void fs1ServesHttpOnly(@TempDir Path folder) throws Exception {
        var factory = Engines.factory(new LoginThrottle(new TestClock()), List::of);
        var events = new ServerEvents(new TestClock(), e -> { });
        var share = new ShareRoot(folder, true);
        assertThat(Engines.AVAILABLE).containsExactly(Protocol.HTTP);
        assertThat(factory.create(ServerDefinition.create("h", Protocol.HTTP, folder), share, ResolvedLogin.anonymous(), events)).isInstanceOf(HttpEngine.class);
        assertThatThrownBy(() -> factory.create(ServerDefinition.create("f", Protocol.FTP, folder), share, ResolvedLogin.anonymous(), events))
            .isInstanceOf(EngineFailure.class).hasMessage("FTP servers are not available in this version of File Server");
    }
}
```

`runtime/ServerRegistryTest.java`:

```java
package dev.jasper.fileserver.runtime;

import dev.jasper.fileserver.FakeVault;
import dev.jasper.fileserver.TestClock;
import dev.jasper.fileserver.auth.LoginResolver;
import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.ServerEngine;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.vault.api.LockState;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.fileserver.runtime.ServerState.Phase.*;
import static org.assertj.core.api.Assertions.assertThat;

class ServerRegistryTest {
    @TempDir Path folder;
    final Deque<Runnable> background = new ArrayDeque<>();
    final List<FakeEngine> engines = new ArrayList<>();
    final List<ServerEvent> observed = new ArrayList<>();
    final List<UUID> activity = new ArrayList<>();
    final FakeVault vault = new FakeVault();
    String failure;
    ServerRegistry registry;
    int changes;

    final class FakeEngine implements ServerEngine {
        final ServerDefinition definition;
        final ServerEvents events;
        boolean started, stopped;
        FakeEngine(ServerDefinition definition, ServerEvents events) { this.definition = definition; this.events = events; }
        @Override public void start() throws EngineFailure { if (failure != null) throw new EngineFailure(failure); started = true; }
        @Override public void stop() { stopped = true; }
        @Override public List<URI> addresses() { return List.of(URI.create("http://127.0.0.1:" + definition.port() + "/")); }
    }

    @BeforeEach void registry() {
        var logins = new LoginResolver(Optional.of(vault.api()), () -> "dustin", () -> "session-password");
        registry = new ServerRegistry((definition, share, login, events) -> {
            var engine = new FakeEngine(definition, events);
            engines.add(engine);
            return engine;
        }, logins, background::add, Runnable::run, new TestClock(), () -> 3, observed::add);
        registry.onChanged(() -> changes++);
        registry.onActivity(activity::add);
    }

    void run() { while (!background.isEmpty()) background.poll().run(); }

    ServerDefinition definition(String name, int port, Bind bind, Login login, boolean autostart) {
        var base = ServerDefinition.create(name, Protocol.HTTP, folder);
        return new ServerDefinition(base.id(), name, Protocol.HTTP, folder, bind, port, Optional.empty(), false, login, List.of(), false, false, autostart);
    }

    ServerState.Phase phase(UUID id) { return registry.server(id).orElseThrow().state().phase(); }

    @Test void startsAndStopsThroughEveryState() {
        var docs = definition("docs", 8080, Bind.LOOPBACK, Login.SESSION, false);
        registry.setDefinitions(List.of(docs));
        registry.start(docs.id(), null);
        assertThat(phase(docs.id())).isEqualTo(STARTING);
        run();
        var view = registry.server(docs.id()).orElseThrow();
        assertThat(view.state().phase()).isEqualTo(RUNNING);
        assertThat(view.addresses()).containsExactly(URI.create("http://127.0.0.1:8080/"));
        assertThat(view.user()).contains("dustin");
        assertThat(registry.password(docs.id())).contains("session-password");
        assertThat(registry.running()).isEqualTo(1);
        assertThat(registry.active()).isEqualTo(1);
        registry.stop(docs.id());
        assertThat(phase(docs.id())).isEqualTo(STOPPING);
        run();
        assertThat(phase(docs.id())).isEqualTo(STOPPED);
        assertThat(engines.getFirst().stopped).isTrue();
        assertThat(registry.password(docs.id())).isEmpty();
        assertThat(registry.running()).isZero();
        assertThat(changes).isGreaterThanOrEqualTo(5);
    }

    @Test void aFailedStartSaysWhyAndCanBeRetried() {
        var docs = definition("docs", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.setDefinitions(List.of(docs));
        failure = "Port 8080 is already in use on 127.0.0.1";
        registry.start(docs.id(), null);
        run();
        assertThat(registry.server(docs.id()).orElseThrow().state()).isEqualTo(ServerState.failed("Port 8080 is already in use on 127.0.0.1"));
        assertThat(registry.activity(docs.id())).extracting(ActivityLog.Entry::text).containsExactly("error: Port 8080 is already in use on 127.0.0.1");
        failure = null;
        registry.start(docs.id(), null);
        run();
        assertThat(phase(docs.id())).isEqualTo(RUNNING);
    }

    @Test void refusesAMissingFolderAndAPortClash() {
        var docs = definition("docs", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        var everywhere = definition("everywhere", 8080, Bind.ALL, Login.ANONYMOUS, false);
        var elsewhere = definition("elsewhere", 8080, new Bind.Interface("en0"), Login.ANONYMOUS, false);
        var gone = new ServerDefinition(UUID.randomUUID(), "gone", Protocol.HTTP, folder.resolve("missing"), Bind.LOOPBACK, 9090,
            Optional.empty(), false, Login.ANONYMOUS, List.of(), false, false, false);
        registry.setDefinitions(List.of(docs, everywhere, elsewhere, gone));
        registry.start(docs.id(), null);
        run();
        registry.start(everywhere.id(), null);
        assertThat(registry.server(everywhere.id()).orElseThrow().state().reason()).contains("Port 8080 is already used by ‘docs’");
        registry.start(elsewhere.id(), null);
        run();
        assertThat(phase(elsewhere.id())).as("loopback and an interface do not compete").isEqualTo(RUNNING);
        registry.start(gone.id(), null);
        assertThat(registry.server(gone.id()).orElseThrow().state().reason()).get().asString().startsWith("The folder ").endsWith(" does not exist");
    }

    @Test void cancellingAStartStopsTheEngineOnceBound() {
        var docs = definition("docs", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.setDefinitions(List.of(docs));
        registry.start(docs.id(), null);
        registry.cancelStart(docs.id());
        assertThat(phase(docs.id())).isEqualTo(STOPPED);
        run();
        assertThat(engines.getFirst().started).isTrue();
        assertThat(engines.getFirst().stopped).isTrue();
        assertThat(phase(docs.id())).isEqualTo(STOPPED);
    }

    @Test void temporaryServersVanishWhenStoppedAndSavingAdoptsThem() {
        var quick = definition("quick", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.startTemporary(quick, null);
        run();
        assertThat(registry.server(quick.id())).get().satisfies(view -> { assertThat(view.temporary()).isTrue(); assertThat(view.state().phase()).isEqualTo(RUNNING); });
        registry.setDefinitions(List.of(quick));
        assertThat(registry.server(quick.id())).get().satisfies(view -> {
            assertThat(view.temporary()).as("saved under the same id").isFalse();
            assertThat(view.state().phase()).isEqualTo(RUNNING);
            assertThat(view.restartNeeded()).isFalse();
        });
        var other = definition("other", 8081, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.startTemporary(other, null);
        run();
        registry.stop(other.id());
        run();
        assertThat(registry.server(other.id())).isEmpty();
    }

    @Test void editingARunningServerAsksForARestartAndDeletingItStopsIt() {
        var docs = definition("docs", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.setDefinitions(List.of(docs));
        registry.start(docs.id(), null);
        run();
        var moved = definition("docs", 8081, Bind.LOOPBACK, Login.ANONYMOUS, false);
        moved = new ServerDefinition(docs.id(), moved.name(), moved.protocol(), moved.folder(), moved.bind(), 8081, moved.passiveRange(),
            moved.writable(), moved.login(), moved.authorizedKeys(), false, false, false);
        registry.setDefinitions(List.of(moved));
        assertThat(registry.server(docs.id()).orElseThrow().restartNeeded()).isTrue();
        registry.setDefinitions(List.of());
        assertThat(registry.servers()).as("a deleted server leaves the list at once").isEmpty();
        run();
        assertThat(engines.getFirst().stopped).isTrue();
        assertThat(registry.server(docs.id())).isEmpty();
    }

    @Test void autostartWaitsForALockedVaultAndStartsOnUnlock() {
        UUID account = vault.password("Share login", "guest", "pw");
        vault.state = LockState.LOCKED;
        var vaulted = definition("vaulted", 8080, Bind.LOOPBACK, new Login.Vault(account), true);
        var plain = definition("plain", 8081, Bind.LOOPBACK, Login.ANONYMOUS, true);
        var manual = definition("manual", 8082, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.setDefinitions(List.of(vaulted, plain, manual));
        registry.autostart();
        run();
        assertThat(phase(vaulted.id())).isEqualTo(WAITING_FOR_VAULT);
        assertThat(phase(plain.id())).isEqualTo(RUNNING);
        assertThat(phase(manual.id())).isEqualTo(STOPPED);
        vault.state = LockState.UNLOCKED;
        registry.vaultUnlocked();
        run();
        assertThat(phase(vaulted.id())).isEqualTo(RUNNING);
        assertThat(registry.server(vaulted.id()).orElseThrow().user()).contains("guest");
    }

    @Test void aDeletedVaultAccountFailsTheStart() {
        var vaulted = definition("vaulted", 8080, Bind.LOOPBACK, new Login.Vault(UUID.randomUUID()), false);
        registry.setDefinitions(List.of(vaulted));
        registry.start(vaulted.id(), null);
        run();
        assertThat(registry.server(vaulted.id()).orElseThrow().state().reason()).contains("Vault account no longer exists — edit the server");
    }

    @Test void recordsActivityAndPassesEventsOn() {
        var docs = definition("docs", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.setDefinitions(List.of(docs));
        registry.start(docs.id(), null);
        run();
        var events = engines.getFirst().events;
        events.connected("10.0.0.2");
        events.transfer("10.0.0.2", ServerEvent.Direction.DOWNLOAD, "/a", 3, () -> { }).finish(true);
        assertThat(registry.activity(docs.id())).extracting(ActivityLog.Entry::text).containsExactly("10.0.0.2 connected", "10.0.0.2 ↓ /a  0 B  0.0 s");
        assertThat(activity).containsExactly(docs.id(), docs.id());
        assertThat(observed).hasSize(3);
        registry.clearActivity(docs.id());
        assertThat(registry.activity(docs.id())).isEmpty();
    }

    @Test void stopAllAndShutdownStopEverything() {
        var a = definition("a", 8080, Bind.LOOPBACK, Login.ANONYMOUS, false);
        var b = definition("b", 8081, Bind.LOOPBACK, Login.ANONYMOUS, false);
        registry.setDefinitions(List.of(a, b));
        registry.start(a.id(), null);
        registry.start(b.id(), null);
        run();
        var stopped = registry.stopAll();
        run();
        assertThat(stopped).isCompleted();
        assertThat(registry.running()).isZero();
        registry.start(a.id(), null);
        run();
        registry.shutdown();
        assertThat(engines.getLast().stopped).as("synchronously").isTrue();
        assertThat(registry.servers()).isEmpty();
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.runtime.*'`
Expected: FAIL — the package does not exist.

- [ ] **Step 3: Write the state, the log and the transfer activities**

`runtime/package-info.java`:

```java
/**
 * Running servers: {@link dev.jasper.fileserver.runtime.ServerRegistry} owns each server's state machine on the UI
 * thread, starts and stops engines on a worker, and keeps each server's in-memory activity log. May depend on
 * {@code engine} and its sub-packages, {@code auth}, {@code share}, {@code definition} and the SDK.
 */
package dev.jasper.fileserver.runtime;
```

`runtime/ServerState.java`:

```java
package dev.jasper.fileserver.runtime;

import java.util.Objects;
import java.util.Optional;

/** Where a server is in its life, and why it failed. */
public record ServerState(Phase phase, Optional<String> reason) {
    public enum Phase {
        STOPPED, STARTING, RUNNING, STOPPING, FAILED, WAITING_FOR_VAULT;

        public boolean startable() { return this == STOPPED || this == FAILED || this == WAITING_FOR_VAULT; }
    }

    public static final ServerState STOPPED = new ServerState(Phase.STOPPED, Optional.empty());
    public static final ServerState STARTING = new ServerState(Phase.STARTING, Optional.empty());
    public static final ServerState RUNNING = new ServerState(Phase.RUNNING, Optional.empty());
    public static final ServerState STOPPING = new ServerState(Phase.STOPPING, Optional.empty());
    public static final ServerState WAITING_FOR_VAULT = new ServerState(Phase.WAITING_FOR_VAULT, Optional.empty());

    public ServerState {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(reason, "reason");
    }

    public static ServerState failed(String reason) { return new ServerState(Phase.FAILED, Optional.of(reason)); }
}
```

`runtime/ActivityLog.java`:

```java
package dev.jasper.fileserver.runtime;

import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.Sizes;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

/** One server's recent activity, newest last, in memory only. UI thread. */
public final class ActivityLog {
    public record Entry(Instant at, String text) { }

    private final int capacity;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();

    public ActivityLog(int capacity) { this.capacity = Math.max(1, capacity); }

    /** Logs the event if people would care about it; true when it was logged. */
    public boolean add(ServerEvent event) {
        Optional<String> text = describe(event);
        if (text.isEmpty()) return false;
        entries.addLast(new Entry(event.at(), text.get()));
        while (entries.size() > capacity) entries.removeFirst();
        return true;
    }

    public List<Entry> entries() { return List.copyOf(entries); }
    public void clear() { entries.clear(); }

    public static Optional<String> describe(ServerEvent event) {
        String who = event.remote().isEmpty() ? "" : event.remote() + " ";
        return switch (event) {
            case ServerEvent.Connected connected -> Optional.of(who + "connected");
            case ServerEvent.LoginSucceeded login -> Optional.of(who + "login " + login.user());
            case ServerEvent.LoginFailed login -> Optional.of(who + "login failed" + (login.user().isEmpty() ? "" : " for " + login.user()));
            case ServerEvent.Changed changed -> Optional.of(who + changed.description());
            case ServerEvent.Failed failed -> Optional.of(who + "error: " + failed.message());
            case ServerEvent.Transferred done -> Optional.of(who + (done.direction() == ServerEvent.Direction.DOWNLOAD ? "↓ " : "↑ ")
                + done.path() + "  " + Sizes.human(done.bytes()) + "  " + Sizes.seconds(done.took()) + (done.complete() ? "" : "  (stopped)"));
            case ServerEvent.TransferStarted started -> Optional.empty();
            case ServerEvent.TransferProgress progress -> Optional.empty();
        };
    }
}
```

`runtime/TransferActivities.java`:

```java
package dev.jasper.fileserver.runtime;

import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.Sizes;
import dev.jasper.sdk.activity.ActivityHandle;
import dev.jasper.sdk.activity.ActivitySpec;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Turns transfers that last more than a second into SDK activities with progress and a Cancel that drops the client. UI thread. */
public final class TransferActivities {
    static final Duration AFTER = Duration.ofSeconds(1);
    private final Function<ActivitySpec, ActivityHandle> begin;
    private final String activateActionId;
    private final Map<Long, ServerEvent.TransferStarted> pending = new HashMap<>();
    private final Map<Long, ActivityHandle> handles = new HashMap<>();

    public TransferActivities(Function<ActivitySpec, ActivityHandle> begin, String activateActionId) {
        this.begin = begin;
        this.activateActionId = activateActionId;
    }

    public void accept(ServerEvent event) {
        switch (event) {
            case ServerEvent.TransferStarted started -> pending.put(started.id(), started);
            case ServerEvent.TransferProgress progress -> progress(progress);
            case ServerEvent.Transferred done -> {
                pending.remove(done.id());
                ActivityHandle handle = handles.remove(done.id());
                if (handle == null) return;
                if (done.complete()) handle.succeed(Sizes.human(done.bytes()) + " in " + Sizes.seconds(done.took()));
                else handle.fail("Stopped after " + Sizes.human(done.bytes()));
            }
            default -> { }
        }
    }

    private void progress(ServerEvent.TransferProgress progress) {
        ServerEvent.TransferStarted started = pending.get(progress.id());
        if (started == null) return;
        ActivityHandle handle = handles.get(progress.id());
        if (handle == null) {
            if (Duration.between(started.at(), progress.at()).compareTo(AFTER) < 0) return;
            handle = begin.apply(new ActivitySpec(title(started), started.remote(), Optional.of(activateActionId), Optional.of(started.cancel())));
            handles.put(progress.id(), handle);
        }
        String detail = Sizes.human(progress.bytes()) + (progress.total() > 0 ? " of " + Sizes.human(progress.total()) : "");
        if (progress.total() > 0) handle.progress(Math.min(1.0, progress.bytes() / (double) progress.total()), detail);
        else handle.detail(detail);
    }

    static String title(ServerEvent.TransferStarted started) {
        String name = started.path().substring(started.path().lastIndexOf('/') + 1);
        return (started.direction() == ServerEvent.Direction.DOWNLOAD ? "Sending " : "Receiving ") + name;
    }
}
```

`runtime/Engines.java`:

```java
package dev.jasper.fileserver.runtime;

import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.http.HttpEngine;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Which protocols this version serves, and the factory that builds their engines. */
public final class Engines {
    public static final Set<Protocol> AVAILABLE = Set.of(Protocol.HTTP);

    private Engines() { }

    public static ServerRegistry.EngineFactory factory(LoginThrottle throttle, Supplier<List<ServerAddresses.Nic>> nics) {
        return (definition, share, login, events) -> switch (definition.protocol()) {
            case HTTP -> new HttpEngine(definition, share, login, events, throttle, nics);
            case FTP, SFTP, TFTP -> throw new EngineFailure(definition.protocol().label() + " servers are not available in this version of File Server");
        };
    }
}
```

- [ ] **Step 4: Write `ServerRegistry`**

```java
package dev.jasper.fileserver.runtime;

import dev.jasper.fileserver.auth.LoginResolver;
import dev.jasper.fileserver.auth.ResolvedLogin;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.ServerEngine;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.runtime.ServerState.Phase;
import dev.jasper.fileserver.share.ShareRoot;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.WindowHandle;
import java.net.URI;
import java.nio.file.Files;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Every saved and temporary server and its state machine:
 * STOPPED → STARTING → RUNNING → STOPPING → STOPPED, with FAILED and WAITING_FOR_VAULT.
 * UI thread only. Engines start and stop on {@code background}; their events reach the UI thread through {@code ui}.
 * Returned futures complete on the UI thread and never exceptionally: the state says how it went.
 */
public final class ServerRegistry {
    @FunctionalInterface
    public interface EngineFactory {
        ServerEngine create(ServerDefinition definition, ShareRoot share, ResolvedLogin login, ServerEvents events) throws EngineFailure;
    }

    /** What the panel and the palette show about one server. */
    public record View(ServerDefinition definition, boolean temporary, ServerState state, List<URI> addresses,
                       Optional<String> user, boolean restartNeeded) { }

    private final class Slot {
        ServerDefinition definition;
        ServerDefinition running;
        boolean temporary, removed;
        ServerState state = ServerState.STOPPED;
        ServerEngine engine;
        ResolvedLogin login;
        List<URI> addresses = List.of();
        final ActivityLog log = new ActivityLog(activityEntries.getAsInt());
        long attempt;

        Slot(ServerDefinition definition, boolean temporary) { this.definition = definition; this.temporary = temporary; }
        UUID id() { return definition.id(); }
    }

    private final EngineFactory engines;
    private final LoginResolver logins;
    private final Executor background, ui;
    private final Clock clock;
    private final IntSupplier activityEntries;
    private final Consumer<ServerEvent> observer;
    private final Map<UUID, Slot> slots = new LinkedHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<UUID>> activityListeners = new CopyOnWriteArrayList<>();

    public ServerRegistry(EngineFactory engines, LoginResolver logins, Executor background, Executor ui, Clock clock,
                          IntSupplier activityEntries, Consumer<ServerEvent> observer) {
        this.engines = engines; this.logins = logins; this.background = background; this.ui = ui; this.clock = clock;
        this.activityEntries = activityEntries; this.observer = observer;
    }

    public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }
    public Subscription onActivity(Consumer<UUID> listener) { activityListeners.add(listener); return () -> activityListeners.remove(listener); }

    /**
     * The saved servers, in order. A temporary server saved under its own id becomes a saved one; a deleted server
     * that still runs leaves the list at once and stops in the background.
     */
    public void setDefinitions(List<ServerDefinition> saved) {
        var next = new LinkedHashMap<UUID, Slot>();
        for (ServerDefinition definition : saved) {
            Slot slot = slots.get(definition.id());
            if (slot == null) slot = new Slot(definition, false);
            else { slot.definition = definition; slot.temporary = false; slot.removed = false; }
            next.put(definition.id(), slot);
        }
        var stopping = new ArrayList<UUID>();
        for (Slot slot : slots.values()) {
            if (next.containsKey(slot.id())) continue;
            if (slot.temporary) { next.put(slot.id(), slot); continue; }
            if (slot.state.phase() == Phase.STARTING || slot.state.phase() == Phase.RUNNING || slot.state.phase() == Phase.STOPPING) {
                slot.removed = true;
                next.put(slot.id(), slot);
                stopping.add(slot.id());
            }
        }
        slots.clear();
        slots.putAll(next);
        stopping.forEach(this::stop);
        changed();
    }

    public List<View> servers() { return slots.values().stream().filter(slot -> !slot.removed).map(this::view).toList(); }
    public Optional<View> server(UUID id) { return Optional.ofNullable(slots.get(id)).filter(slot -> !slot.removed).map(this::view); }

    public int running() { return (int) slots.values().stream().filter(slot -> slot.state.phase() == Phase.RUNNING).count(); }

    /** Servers that are starting, running or stopping: the ones quitting would stop. */
    public int active() {
        return (int) slots.values().stream().filter(slot -> slot.state.phase() == Phase.STARTING
            || slot.state.phase() == Phase.RUNNING || slot.state.phase() == Phase.STOPPING).count();
    }

    /** The running server's password, for Copy Password; empty for anonymous or stopped servers. */
    public Optional<String> password(UUID id) {
        Slot slot = slots.get(id);
        if (slot == null || slot.state.phase() != Phase.RUNNING || slot.login == null) return Optional.empty();
        return slot.login.revealPassword();
    }

    public List<ActivityLog.Entry> activity(UUID id) {
        Slot slot = slots.get(id);
        return slot == null ? List.of() : slot.log.entries();
    }

    public void clearActivity(UUID id) {
        Slot slot = slots.get(id);
        if (slot == null) return;
        slot.log.clear();
        activityListeners.forEach(listener -> listener.accept(id));
    }

    /** Adds a server that is never saved and starts it; it disappears when stopped. */
    public CompletableFuture<Void> startTemporary(ServerDefinition definition, WindowHandle owner) {
        slots.put(definition.id(), new Slot(definition, true));
        changed();
        return start(definition.id(), owner);
    }

    /** Starts a stopped, failed or waiting server; a Vault login may prompt over {@code owner}. */
    public CompletableFuture<Void> start(UUID id, WindowHandle owner) {
        Slot slot = slots.get(id);
        if (slot == null || slot.removed || !slot.state.phase().startable()) return CompletableFuture.completedFuture(null);
        ServerDefinition definition = slot.definition;
        Optional<String> problem = startProblem(slot);
        if (problem.isPresent()) { fail(slot, problem.get()); return CompletableFuture.completedFuture(null); }
        long attempt = ++slot.attempt;
        slot.state = ServerState.STARTING;
        changed();
        var result = new CompletableFuture<Void>();
        CompletableFuture<ResolvedLogin> login;
        try { login = logins.resolve(definition, owner); }
        catch (RuntimeException failure) { login = CompletableFuture.failedFuture(failure); }
        login.whenComplete((resolved, failure) -> ui.execute(() -> {
            if (!current(slot, attempt)) { if (resolved != null) resolved.close(); result.complete(null); return; }
            if (failure != null) { fail(slot, message(failure)); result.complete(null); return; }
            try { background.execute(() -> bind(slot, attempt, definition, resolved, result)); }
            catch (RejectedExecutionException closing) { resolved.close(); fail(slot, "File Server is stopping"); result.complete(null); }
        }));
        return result;
    }

    /** Worker side: builds and starts the engine, then reports on the UI thread. */
    private void bind(Slot slot, long attempt, ServerDefinition definition, ResolvedLogin login, CompletableFuture<Void> result) {
        ServerEngine engine = null;
        Throwable error = null;
        try {
            ShareRoot share = new ShareRoot(definition.folder(), !definition.writable());
            engine = engines.create(definition, share, login, new ServerEvents(clock, event -> ui.execute(() -> record(slot, event))));
            engine.start();
        } catch (Exception failure) {
            error = failure;
            if (engine != null) { try { engine.stop(); } catch (RuntimeException ignored) { } }
            engine = null;
        }
        ServerEngine started = engine;
        Throwable failure = error;
        ui.execute(() -> {
            if (!current(slot, attempt)) {
                if (started != null) {
                    try { background.execute(started::stop); } catch (RejectedExecutionException closing) { started.stop(); }
                }
                login.close();
            } else if (failure != null) {
                login.close();
                fail(slot, message(failure));
            } else {
                slot.engine = started;
                slot.login = login;
                slot.running = definition;
                slot.addresses = started.addresses();
                slot.state = ServerState.RUNNING;
                changed();
            }
            result.complete(null);
        });
    }

    /** Stops a server; a starting one is cancelled. Completes once it has stopped. */
    public CompletableFuture<Void> stop(UUID id) {
        Slot slot = slots.get(id);
        if (slot == null) return CompletableFuture.completedFuture(null);
        switch (slot.state.phase()) {
            case STARTING -> { cancelStart(id); return CompletableFuture.completedFuture(null); }
            case STOPPED, FAILED, WAITING_FOR_VAULT -> { settle(slot); return CompletableFuture.completedFuture(null); }
            case STOPPING -> { return CompletableFuture.completedFuture(null); }
            case RUNNING -> { }
        }
        slot.state = ServerState.STOPPING;
        changed();
        ServerEngine engine = slot.engine;
        slot.engine = null;
        var result = new CompletableFuture<Void>();
        Runnable stop = () -> {
            try { engine.stop(); } catch (RuntimeException ignored) { }
            ui.execute(() -> {
                if (slot.login != null) slot.login.close();
                slot.login = null;
                slot.running = null;
                slot.addresses = List.of();
                settle(slot);
                result.complete(null);
            });
        };
        try { background.execute(stop); } catch (RejectedExecutionException closing) { stop.run(); }
        return result;
    }

    public void cancelStart(UUID id) {
        Slot slot = slots.get(id);
        if (slot == null || slot.state.phase() != Phase.STARTING) return;
        slot.attempt++;
        settle(slot);
    }

    /** Starts every saved server marked to start with Jasper; one that needs a locked Vault waits for it instead of prompting. */
    public void autostart() {
        for (Slot slot : List.copyOf(slots.values())) {
            if (slot.temporary || !slot.definition.autostart() || slot.state.phase() != Phase.STOPPED) continue;
            if (logins.needsUnlock(slot.definition)) { slot.state = ServerState.WAITING_FOR_VAULT; changed(); }
            else start(slot.id(), null);
        }
    }

    public void vaultUnlocked() {
        for (Slot slot : List.copyOf(slots.values()))
            if (slot.state.phase() == Phase.WAITING_FOR_VAULT) start(slot.id(), null);
    }

    public CompletableFuture<Void> stopAll() {
        var stops = new ArrayList<CompletableFuture<Void>>();
        for (Slot slot : List.copyOf(slots.values())) if (slot.state.phase() != Phase.STOPPED) stops.add(stop(slot.id()));
        return CompletableFuture.allOf(stops.toArray(CompletableFuture[]::new));
    }

    /** Plugin shutdown: stops every engine on this thread and forgets every server. */
    public void shutdown() {
        for (Slot slot : slots.values()) {
            slot.attempt++;
            if (slot.engine != null) { try { slot.engine.stop(); } catch (RuntimeException ignored) { } }
            if (slot.login != null) slot.login.close();
        }
        slots.clear();
        changed();
    }

    /** Recomputes running servers' URLs, for example after joining another network. */
    public void refreshAddresses() {
        boolean any = false;
        for (Slot slot : slots.values()) {
            if (slot.state.phase() != Phase.RUNNING || slot.engine == null) continue;
            List<URI> next = slot.engine.addresses();
            if (!next.equals(slot.addresses)) { slot.addresses = next; any = true; }
        }
        if (any) changed();
    }

    private Optional<String> startProblem(Slot slot) {
        ServerDefinition definition = slot.definition;
        if (!Files.isDirectory(definition.folder())) return Optional.of("The folder " + definition.folder() + " does not exist");
        for (Slot other : slots.values()) {
            if (other == slot || other.definition.port() != definition.port()) continue;
            Phase phase = other.state.phase();
            if ((phase == Phase.STARTING || phase == Phase.RUNNING) && other.definition.bind().overlaps(definition.bind()))
                return Optional.of("Port " + definition.port() + " is already used by ‘" + other.definition.name() + "’");
        }
        return Optional.empty();
    }

    private boolean current(Slot slot, long attempt) {
        return slots.get(slot.id()) == slot && slot.attempt == attempt && slot.state.phase() == Phase.STARTING;
    }

    /** STOPPED, or gone for a temporary or deleted server. */
    private void settle(Slot slot) {
        slot.state = ServerState.STOPPED;
        if ((slot.temporary || slot.removed) && slots.get(slot.id()) == slot) slots.remove(slot.id());
        changed();
    }

    private void fail(Slot slot, String reason) {
        slot.state = ServerState.failed(reason);
        if (slot.log.add(new ServerEvent.Failed(clock.instant(), "", reason))) activityListeners.forEach(listener -> listener.accept(slot.id()));
        changed();
    }

    private void record(Slot slot, ServerEvent event) {
        if (slots.get(slot.id()) == slot && slot.log.add(event)) activityListeners.forEach(listener -> listener.accept(slot.id()));
        observer.accept(event);
    }

    private View view(Slot slot) {
        return new View(slot.definition, slot.temporary, slot.state, slot.addresses,
            slot.login == null ? Optional.empty() : slot.login.user(),
            slot.state.phase() == Phase.RUNNING && slot.running != null && !slot.running.equals(slot.definition));
    }

    private void changed() { listeners.forEach(Runnable::run); }

    static String message(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.runtime.*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add plugins/fileserver/src
git commit -m "File Server: the server registry, activity log and transfer activities

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: The File Servers panel

**Files:**
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/{ServerRows,FileServersPanel,package-info}.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/{ServerRowsTest,FileServersPanelTest}.java`

**Interfaces:**
- Consumes: `ServerRegistry.View`, `ServerState`, `ActivityLog.Entry` (Task 11); definition types (Task 5).
- Produces (package `dev.jasper.fileserver.ui`):
  - `ServerRows`: `sealed interface Row` with `Problem(String message)`, `Header(String title)`, `Server(ServerRegistry.View view)`; `static List<Row> rows(List<View>, Optional<String> problem)` (problem first, saved servers, then a "Temporary" header and temporary servers); `access(ServerDefinition)`; `warnings(ServerDefinition)` and `warnings(Protocol, boolean tls, Login, boolean writable, Bind)`; `folder(Path folder, Path home)`; `state(ServerState)`; `tag(ServerState)`; `firewallHint(String osName)`
  - `FileServersPanel(Actions, Function<UUID, List<ActivityLog.Entry>> log, Path home, String firewallHint, ZoneId zone)` with `record Actions(Consumer<UUID> toggle, Consumer<Optional<UUID>> edit, Consumer<UUID> duplicate, Consumer<UUID> delete, Consumer<UUID> reveal, Consumer<String> copy, Consumer<UUID> copyPassword, Consumer<UUID> clearActivity)`; `setServers(List<View>, Optional<String> problem)`, `refreshActivity()`, `Optional<UUID> selected()`, `select(UUID)`; package-private controls `add`, `list`, `empty`, `details`, `cardName`, `cardState`, `cardFolder`, `cardAccess`, `cardWarnings`, `addressCopies`, `toggle`, `edit`, `activity`, `clearActivity`, `copyActivity`, `menuFor(int)`. `edit(Optional.empty())` means New; editing a temporary server is "Save as a Server…".

- [ ] **Step 1: Write the failing tests**

`ui/ServerRowsTest.java`:

```java
package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.runtime.ServerRegistry;
import dev.jasper.fileserver.runtime.ServerState;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ServerRowsTest {
    static final Path HOME = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().resolve("home");

    static ServerDefinition definition(String name, Protocol protocol, boolean writable, Bind bind, Login login) {
        return new ServerDefinition(UUID.randomUUID(), name, protocol, HOME.resolve("work"), bind, protocol.defaultPort(), Optional.empty(),
            writable, login, List.of(), false, false, false);
    }

    static ServerRegistry.View view(ServerDefinition definition, boolean temporary) {
        return new ServerRegistry.View(definition, temporary, ServerState.STOPPED, List.of(), Optional.empty(), false);
    }

    @Test void groupsTemporaryServersAfterTheSavedOnes() {
        var saved = view(definition("docs", Protocol.HTTP, false, Bind.LOOPBACK, Login.ANONYMOUS), false);
        var temporary = view(definition("work (HTTP)", Protocol.HTTP, false, Bind.LOOPBACK, Login.ANONYMOUS), true);
        assertThat(ServerRows.rows(List.of(temporary, saved), Optional.of("broken"))).containsExactly(
            new ServerRows.Problem("broken"), new ServerRows.Server(saved), new ServerRows.Header("Temporary"), new ServerRows.Server(temporary));
        assertThat(ServerRows.rows(List.of(saved), Optional.empty())).containsExactly(new ServerRows.Server(saved));
    }

    @Test void summarisesAccessAndWarnsAboutRiskyCombinations() {
        assertThat(ServerRows.access(definition("a", Protocol.HTTP, false, Bind.LOOPBACK, Login.ANONYMOUS))).isEqualTo("read-only · anonymous");
        assertThat(ServerRows.access(definition("b", Protocol.HTTP, true, Bind.LOOPBACK, Login.SESSION))).isEqualTo("writable · session password");
        assertThat(ServerRows.access(definition("c", Protocol.FTP, false, Bind.LOOPBACK, new Login.Vault(UUID.randomUUID())))).isEqualTo("read-only · Vault account");
        assertThat(ServerRows.access(definition("d", Protocol.TFTP, false, Bind.LOOPBACK, Login.ANONYMOUS))).isEqualTo("read-only · no login");
        assertThat(ServerRows.warnings(definition("a", Protocol.HTTP, false, Bind.LOOPBACK, Login.ANONYMOUS))).isEmpty();
        assertThat(ServerRows.warnings(definition("b", Protocol.HTTP, false, Bind.LOOPBACK, Login.SESSION))).containsExactly("Plain HTTP sends the password unencrypted");
        assertThat(ServerRows.warnings(definition("c", Protocol.HTTP, true, Bind.ALL, Login.ANONYMOUS)))
            .containsExactly("Anyone who can reach it can change files", "Writable and reachable from other computers");
        assertThat(ServerRows.warnings(definition("d", Protocol.TFTP, true, Bind.LOOPBACK, Login.ANONYMOUS)))
            .containsExactly("TFTP has no login: anyone who can reach it can change files");
    }

    @Test void namesFoldersStatesAndFirewalls() {
        assertThat(ServerRows.folder(HOME.resolve("work"), HOME)).isEqualTo("~" + File.separator + "work");
        assertThat(ServerRows.folder(HOME, HOME)).isEqualTo("~");
        assertThat(ServerRows.folder(Path.of("/srv").toAbsolutePath(), HOME)).isEqualTo(Path.of("/srv").toAbsolutePath().toString());
        assertThat(ServerRows.state(ServerState.failed("Port busy"))).isEqualTo("Failed: Port busy");
        assertThat(ServerRows.state(ServerState.WAITING_FOR_VAULT)).isEqualTo("Waiting for the Vault to unlock");
        assertThat(ServerRows.tag(ServerState.RUNNING)).isEqualTo("Running");
        assertThat(ServerRows.firewallHint("Mac OS X")).contains("macOS Application Firewall");
        assertThat(ServerRows.firewallHint("Windows 11")).contains("Windows Defender Firewall");
        assertThat(ServerRows.firewallHint("Linux")).contains("ufw or firewalld");
    }
}
```

`ui/FileServersPanelTest.java`:

```java
package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.runtime.ActivityLog;
import dev.jasper.fileserver.runtime.ServerRegistry;
import dev.jasper.fileserver.runtime.ServerState;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FileServersPanelTest {
    static final Path HOME = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().resolve("home");
    final List<String> events = new ArrayList<>();
    final Map<UUID, List<ActivityLog.Entry>> logs = new java.util.HashMap<>();
    final FileServersPanel.Actions actions = new FileServersPanel.Actions(
        id -> events.add("toggle " + name(id)), id -> events.add("edit " + id.map(this::name).orElse("new")),
        id -> events.add("duplicate " + name(id)), id -> events.add("delete " + name(id)), id -> events.add("reveal " + name(id)),
        text -> events.add("copy " + text), id -> events.add("password " + name(id)), id -> events.add("clear " + name(id)));
    final List<ServerRegistry.View> views = new ArrayList<>();

    String name(UUID id) { return views.stream().filter(v -> v.definition().id().equals(id)).findFirst().orElseThrow().definition().name(); }

    ServerRegistry.View view(String name, ServerState state, boolean temporary, boolean writable, Bind bind, List<URI> addresses, Optional<String> user) {
        var definition = new ServerDefinition(UUID.randomUUID(), name, Protocol.HTTP, HOME.resolve("work"), bind, 8080, Optional.empty(),
            writable, user.isPresent() ? Login.SESSION : Login.ANONYMOUS, List.of(), false, false, false);
        var view = new ServerRegistry.View(definition, temporary, state, addresses, user, false);
        views.add(view);
        return view;
    }

    FileServersPanel panel() { return new FileServersPanel(actions, id -> logs.getOrDefault(id, List.of()), HOME, "If other computers can't connect, check your firewall.", ZoneOffset.UTC); }

    static List<String> titles(JPopupMenu menu) {
        return Arrays.stream(menu.getComponents()).filter(c -> c instanceof JMenuItem).map(c -> ((JMenuItem) c).getText() + (c.isEnabled() ? "" : " (off)")).toList();
    }

    static void click(JPopupMenu menu, String title) {
        Arrays.stream(menu.getComponents()).filter(c -> c instanceof JMenuItem item && item.getText().equals(title)).map(c -> (JMenuItem) c).findFirst().orElseThrow().doClick();
    }

    @Test void showsTheSelectedServersDetailsAndRunsItsActions() {
        var panel = panel();
        assertThat(panel.empty.isVisible()).isTrue();
        assertThat(panel.details.isVisible()).isFalse();
        view("docs", ServerState.RUNNING, false, false, Bind.LOOPBACK, List.of(URI.create("http://127.0.0.1:8080/")), Optional.empty());
        view("firmware", ServerState.STOPPED, false, false, Bind.LOOPBACK, List.of(), Optional.empty());
        panel.setServers(views, Optional.empty());
        assertThat(panel.empty.isVisible()).isFalse();
        assertThat(panel.list.getModel().getSize()).isEqualTo(2);
        assertThat(panel.selected()).as("the first server is selected").contains(views.getFirst().definition().id());
        assertThat(panel.details.isVisible()).isTrue();
        assertThat(panel.cardName.getText()).isEqualTo("docs");
        assertThat(panel.cardState.getText()).isEqualTo("Running");
        assertThat(panel.cardFolder.getText()).isEqualTo("~" + java.io.File.separator + "work");
        assertThat(panel.cardAccess.getText()).isEqualTo("HTTP :8080 · read-only · anonymous · This computer only");
        assertThat(panel.toggle.getText()).isEqualTo("Stop");
        assertThat(panel.addressCopies).hasSize(1);
        panel.addressCopies.getFirst().doClick();
        panel.toggle.doClick();
        panel.edit.doClick();
        panel.add.doClick();
        panel.select(views.get(1).definition().id());
        assertThat(panel.toggle.getText()).isEqualTo("Start");
        assertThat(panel.addressCopies).isEmpty();
        assertThat(events).containsExactly("copy http://127.0.0.1:8080/", "toggle docs", "edit docs", "edit new");
    }

    @Test void menusOfferWhatMakesSense() {
        var panel = panel();
        view("docs", ServerState.RUNNING, false, false, Bind.LOOPBACK, List.of(URI.create("http://127.0.0.1:8080/")), Optional.of("dustin"));
        view("firmware", ServerState.STOPPED, false, false, Bind.LOOPBACK, List.of(), Optional.empty());
        view("work (HTTP)", ServerState.RUNNING, true, false, Bind.LOOPBACK, List.of(URI.create("http://127.0.0.1:8081/")), Optional.empty());
        panel.setServers(views, Optional.empty());
        assertThat(panel.list.getModel().getSize()).as("two saved, a header, one temporary").isEqualTo(4);
        assertThat(titles(panel.menuFor(0))).containsExactly("Stop", "Edit…", "Duplicate", "Delete…", "Reveal Folder", "Copy Address", "Copy Password");
        assertThat(titles(panel.menuFor(1))).containsExactly("Start", "Edit…", "Duplicate", "Delete…", "Reveal Folder", "Copy Address (off)", "Copy Password (off)");
        assertThat(panel.menuFor(2)).as("the header has no menu").isNull();
        assertThat(titles(panel.menuFor(3))).containsExactly("Stop", "Save as a Server…", "Reveal Folder", "Copy Address", "Copy Password (off)");
        click(panel.menuFor(0), "Copy Password");
        click(panel.menuFor(1), "Duplicate");
        click(panel.menuFor(1), "Delete…");
        click(panel.menuFor(1), "Reveal Folder");
        click(panel.menuFor(3), "Save as a Server…");
        assertThat(events).containsExactly("password docs", "duplicate firmware", "delete firmware", "reveal firmware", "edit work (HTTP)");
    }

    @Test void warningsProblemsAndActivityShow() {
        var panel = panel();
        var risky = view("drop", ServerState.failed("Port 8080 is already in use on 0.0.0.0"), false, true, Bind.ALL, List.of(), Optional.empty());
        logs.put(risky.definition().id(), List.of(new ActivityLog.Entry(Instant.parse("2026-09-30T12:00:01Z"), "10.0.0.2 connected"),
            new ActivityLog.Entry(Instant.parse("2026-09-30T12:00:02Z"), "10.0.0.2 ↓ /a  5 B  0.1 s")));
        panel.setServers(views, Optional.of("servers.toml has TOML errors"));
        assertThat(panel.list.getModel().getElementAt(0)).isEqualTo(new ServerRows.Problem("servers.toml has TOML errors"));
        assertThat(panel.cardState.getText()).isEqualTo("Failed: Port 8080 is already in use on 0.0.0.0");
        assertThat(panel.toggle.getText()).isEqualTo("Retry");
        assertThat(panel.cardWarnings.isVisible()).isTrue();
        assertThat(panel.cardWarnings.getText()).contains("Anyone who can reach it can change files", "Writable and reachable from other computers");
        assertThat(panel.activity.getText()).isEqualTo("12:00:01 10.0.0.2 connected\n12:00:02 10.0.0.2 ↓ /a  5 B  0.1 s");
        panel.clearActivity.doClick();
        panel.copyActivity.doClick();
        assertThat(events).containsExactly("clear drop", "copy 12:00:01 10.0.0.2 connected\n12:00:02 10.0.0.2 ↓ /a  5 B  0.1 s");
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.ui.*'`
Expected: FAIL — the package does not exist.

- [ ] **Step 3: Write `ServerRows`**

`ui/package-info.java`:

```java
/**
 * Swing for the File Servers panel and the plugin's dialogs, and the palette scope; the host supplies windows,
 * dialogs and panels. May depend on {@code runtime}, {@code engine} (addresses and sizes), {@code definition} and the
 * SDK; never on {@code dev.jasper.vault.api}.
 */
package dev.jasper.fileserver.ui;
```

`ui/ServerRows.java`:

```java
package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.runtime.ServerRegistry;
import dev.jasper.fileserver.runtime.ServerState;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The panel's rows and the words the panel, editor and palette use for servers. */
public final class ServerRows {
    public sealed interface Row { }
    public record Problem(String message) implements Row { }
    public record Header(String title) implements Row { }
    public record Server(ServerRegistry.View view) implements Row { }

    private ServerRows() { }

    /** The file problem first, then saved servers in order, then a "Temporary" header and the temporary servers. */
    public static List<Row> rows(List<ServerRegistry.View> views, Optional<String> problem) {
        var rows = new ArrayList<Row>();
        problem.ifPresent(message -> rows.add(new Problem(message)));
        views.stream().filter(view -> !view.temporary()).forEach(view -> rows.add(new Server(view)));
        var temporary = views.stream().filter(ServerRegistry.View::temporary).toList();
        if (!temporary.isEmpty()) {
            rows.add(new Header("Temporary"));
            temporary.forEach(view -> rows.add(new Server(view)));
        }
        return List.copyOf(rows);
    }

    public static String access(ServerDefinition definition) {
        String who = switch (definition.login()) {
            case Login.Anonymous anonymous -> definition.protocol() == Protocol.SFTP ? "keys only"
                : definition.protocol() == Protocol.TFTP ? "no login" : "anonymous";
            case Login.Session session -> "session password";
            case Login.Vault vault -> "Vault account";
        };
        return (definition.writable() ? "writable" : "read-only") + " · " + who + (definition.tls() ? " · TLS" : "");
    }

    public static List<String> warnings(ServerDefinition definition) {
        return warnings(definition.protocol(), definition.tls(), definition.login(), definition.writable(), definition.bind());
    }

    public static List<String> warnings(Protocol protocol, boolean tls, Login login, boolean writable, Bind bind) {
        var warnings = new ArrayList<String>();
        if (protocol == Protocol.HTTP && !tls && !(login instanceof Login.Anonymous)) warnings.add("Plain HTTP sends the password unencrypted");
        if (writable && login instanceof Login.Anonymous && protocol != Protocol.SFTP)
            warnings.add(protocol == Protocol.TFTP ? "TFTP has no login: anyone who can reach it can change files" : "Anyone who can reach it can change files");
        if (writable && !(bind instanceof Bind.Loopback)) warnings.add("Writable and reachable from other computers");
        return List.copyOf(warnings);
    }

    /** The folder with the home folder shortened to {@code ~}. */
    public static String folder(Path folder, Path home) {
        if (folder.equals(home)) return "~";
        if (folder.startsWith(home)) return "~" + File.separator + home.relativize(folder);
        return folder.toString();
    }

    public static String state(ServerState state) {
        return switch (state.phase()) {
            case STOPPED -> "Stopped";
            case STARTING -> "Starting…";
            case RUNNING -> "Running";
            case STOPPING -> "Stopping…";
            case FAILED -> "Failed: " + state.reason().orElse("unknown");
            case WAITING_FOR_VAULT -> "Waiting for the Vault to unlock";
        };
    }

    /** A short state for a palette row. */
    public static String tag(ServerState state) {
        return switch (state.phase()) {
            case STOPPED -> "Stopped";
            case STARTING -> "Starting";
            case RUNNING -> "Running";
            case STOPPING -> "Stopping";
            case FAILED -> "Failed";
            case WAITING_FOR_VAULT -> "Waiting";
        };
    }

    public static String firewallHint(String osName) {
        String os = osName.toLowerCase(Locale.ROOT);
        String firewall = os.contains("mac") ? "the macOS Application Firewall" : os.contains("win") ? "Windows Defender Firewall" : "ufw or firewalld";
        return "If other computers can't connect, check your firewall (" + firewall + ").";
    }
}
```

- [ ] **Step 4: Write `FileServersPanel`**

```java
package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.runtime.ActivityLog;
import dev.jasper.fileserver.runtime.ServerRegistry;
import dev.jasper.fileserver.runtime.ServerState.Phase;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;

/**
 * The "File Servers" rail panel: one row per server with its state and a start/stop control at the right edge, and
 * below the list the selected server's details, addresses and activity. One per window; the plugin supplies the
 * servers and the actions.
 */
public final class FileServersPanel extends JPanel {
    public record Actions(Consumer<UUID> toggle, Consumer<Optional<UUID>> edit, Consumer<UUID> duplicate, Consumer<UUID> delete,
                          Consumer<UUID> reveal, Consumer<String> copy, Consumer<UUID> copyPassword, Consumer<UUID> clearActivity) { }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final int CONTROL_WIDTH = 28;

    final JButton add = new JButton("+");
    final JList<ServerRows.Row> list;
    final JLabel empty = label("No file servers yet — press + to share a folder");
    final JPanel details = new JPanel(new BorderLayout(0, 6));
    final JLabel cardName = label(""), cardState = label(""), cardFolder = label(""), cardAccess = label(""), cardWarnings = label("");
    final JPanel addresses = new JPanel();
    final List<JButton> addressCopies = new ArrayList<>();
    final JButton toggle = new JButton("Start"), edit = new JButton("Edit…");
    final JTextArea activity = new JTextArea(8, 24);
    final JButton clearActivity = new JButton("Clear"), copyActivity = new JButton("Copy");
    private final DefaultListModel<ServerRows.Row> model = new DefaultListModel<>();
    private final Actions actions;
    private final Function<UUID, List<ActivityLog.Entry>> log;
    private final Path home;
    private final String firewall;
    private final ZoneId zone;
    private List<ServerRegistry.View> views = List.of();
    private Optional<String> problem = Optional.empty();
    private UUID selected;
    private boolean rebuilding;

    public FileServersPanel(Actions actions, Function<UUID, List<ActivityLog.Entry>> log, Path home, String firewallHint, ZoneId zone) {
        super(new BorderLayout(0, 6));
        this.actions = actions; this.log = log; this.home = home; this.firewall = firewallHint; this.zone = zone;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        var title = label("File Servers");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        var header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.add(title);
        header.add(Box.createHorizontalGlue());
        add.setToolTipText("New file server");
        header.add(add);
        add(header, BorderLayout.NORTH);

        list = new JList<>(model) {
            @Override public boolean getScrollableTracksViewportWidth() { return true; }
            @Override public String getToolTipText(MouseEvent event) {
                int index = locationToIndex(event.getPoint());
                return index >= 0 && model.get(index) instanceof ServerRows.Server row ? tooltip(row.view()) : null;
            }
        };
        ToolTipManager.sharedInstance().registerComponent(list);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        var top = new JPanel(new BorderLayout());
        var scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        top.add(scroll, BorderLayout.CENTER);
        empty.setBorder(BorderFactory.createEmptyBorder(12, 4, 12, 4));
        top.add(empty, BorderLayout.SOUTH);

        var card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        cardName.setFont(cardName.getFont().deriveFont(Font.BOLD));
        addresses.setLayout(new BoxLayout(addresses, BoxLayout.Y_AXIS));
        for (JComponent part : List.<JComponent>of(cardName, cardState, cardFolder, cardAccess, cardWarnings, addresses)) {
            part.setAlignmentX(LEFT_ALIGNMENT);
            card.add(part);
        }
        var buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.add(toggle);
        buttons.add(Box.createHorizontalStrut(6));
        buttons.add(edit);
        buttons.add(Box.createHorizontalGlue());
        card.add(Box.createVerticalStrut(6));
        card.add(buttons);
        details.add(card, BorderLayout.NORTH);
        var activityHeader = new JPanel();
        activityHeader.setLayout(new BoxLayout(activityHeader, BoxLayout.X_AXIS));
        activityHeader.add(label("Activity"));
        activityHeader.add(Box.createHorizontalGlue());
        activityHeader.add(clearActivity);
        activityHeader.add(Box.createHorizontalStrut(4));
        activityHeader.add(copyActivity);
        activity.setEditable(false);
        var activityPanel = new JPanel(new BorderLayout(0, 4));
        activityPanel.add(activityHeader, BorderLayout.NORTH);
        activityPanel.add(new JScrollPane(activity), BorderLayout.CENTER);
        details.add(activityPanel, BorderLayout.CENTER);
        details.setVisible(false);
        var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, details);
        split.setResizeWeight(0.5);
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);

        list.addListSelectionListener(event -> { if (!event.getValueIsAdjusting() && !rebuilding) selectionChanged(); });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                int index = list.locationToIndex(event.getPoint());
                if (index < 0) return;
                Rectangle cell = list.getCellBounds(index, index);
                if (cell == null || !cell.contains(event.getPoint())) return;
                if (SwingUtilities.isRightMouseButton(event)) {
                    list.setSelectedIndex(index);
                    JPopupMenu menu = menuFor(index);
                    if (menu != null) menu.show(list, event.getX(), event.getY());
                    return;
                }
                if (!(model.get(index) instanceof ServerRows.Server row)) return;
                boolean onControl = event.getX() >= cell.x + cell.width - CONTROL_WIDTH;
                if (onControl ? event.getClickCount() == 1 : event.getClickCount() == 2) actions.toggle().accept(row.view().definition().id());
            }
        });
        list.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "toggleServer");
        list.getActionMap().put("toggleServer", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { selectedView().ifPresent(view -> actions.toggle().accept(view.definition().id())); }
        });
        add.addActionListener(event -> actions.edit().accept(Optional.empty()));
        toggle.addActionListener(event -> selectedView().ifPresent(view -> actions.toggle().accept(view.definition().id())));
        edit.addActionListener(event -> selectedView().ifPresent(view -> actions.edit().accept(Optional.of(view.definition().id()))));
        clearActivity.addActionListener(event -> selectedView().ifPresent(view -> actions.clearActivity().accept(view.definition().id())));
        copyActivity.addActionListener(event -> actions.copy().accept(activity.getText()));
        rebuild();
    }

    public void setServers(List<ServerRegistry.View> views, Optional<String> problem) {
        this.views = List.copyOf(views);
        this.problem = problem;
        rebuild();
    }

    /** Re-reads the selected server's activity log. */
    public void refreshActivity() {
        String text = selectedView().map(view -> log.apply(view.definition().id()).stream()
            .map(entry -> TIME.format(entry.at().atZone(zone)) + " " + entry.text()).collect(Collectors.joining("\n"))).orElse("");
        if (!text.equals(activity.getText())) {
            activity.setText(text);
            activity.setCaretPosition(activity.getDocument().getLength());
        }
    }

    public Optional<UUID> selected() { return Optional.ofNullable(selected); }

    public void select(UUID id) {
        int index = indexOf(id);
        if (index >= 0) list.setSelectedIndex(index);
    }

    Optional<ServerRegistry.View> selectedView() {
        return views.stream().filter(view -> view.definition().id().equals(selected)).findFirst();
    }

    JPopupMenu menuFor(int index) {
        if (index < 0 || index >= model.size() || !(model.get(index) instanceof ServerRows.Server row)) return null;
        ServerRegistry.View view = row.view();
        UUID id = view.definition().id();
        Phase phase = view.state().phase();
        var menu = new JPopupMenu();
        var toggleItem = item(phase.startable() ? "Start" : phase == Phase.STARTING ? "Cancel Start" : "Stop", () -> actions.toggle().accept(id));
        toggleItem.setEnabled(phase != Phase.STOPPING);
        menu.add(toggleItem);
        menu.addSeparator();
        if (view.temporary()) {
            menu.add(item("Save as a Server…", () -> actions.edit().accept(Optional.of(id))));
        } else {
            menu.add(item("Edit…", () -> actions.edit().accept(Optional.of(id))));
            menu.add(item("Duplicate", () -> actions.duplicate().accept(id)));
            menu.add(item("Delete…", () -> actions.delete().accept(id)));
        }
        menu.add(item("Reveal Folder", () -> actions.reveal().accept(id)));
        var copyAddress = item("Copy Address", () -> view.addresses().stream().findFirst().ifPresent(uri -> actions.copy().accept(uri.toString())));
        copyAddress.setEnabled(!view.addresses().isEmpty());
        menu.add(copyAddress);
        var copyPassword = item("Copy Password", () -> actions.copyPassword().accept(id));
        copyPassword.setEnabled(phase == Phase.RUNNING && view.user().isPresent());
        menu.add(copyPassword);
        return menu;
    }

    private void rebuild() {
        rebuilding = true;
        try {
            model.clear();
            ServerRows.rows(views, problem).forEach(model::addElement);
            if (selectedView().isEmpty()) selected = firstServer();
            int index = indexOf(selected);
            if (index >= 0) list.setSelectedIndex(index); else list.clearSelection();
        } finally {
            rebuilding = false;
        }
        empty.setVisible(views.isEmpty());
        refreshCard();
        refreshActivity();
    }

    private void selectionChanged() {
        if (list.getSelectedValue() instanceof ServerRows.Server row) selected = row.view().definition().id();
        refreshCard();
        refreshActivity();
    }

    private void refreshCard() {
        Optional<ServerRegistry.View> chosen = selectedView();
        details.setVisible(chosen.isPresent());
        addresses.removeAll();
        addressCopies.clear();
        if (chosen.isPresent()) {
            ServerRegistry.View view = chosen.get();
            ServerDefinition definition = view.definition();
            cardName.setText(definition.name());
            cardState.setText(ServerRows.state(view.state()));
            cardFolder.setText(ServerRows.folder(definition.folder(), home));
            cardAccess.setText(definition.protocol().label() + " :" + definition.port() + " · " + ServerRows.access(definition) + " · "
                + definition.bind().label() + view.user().map(user -> " · user " + user).orElse(""));
            List<String> warnings = ServerRows.warnings(definition);
            cardWarnings.setText(warnings.isEmpty() ? "" : "⚠ " + String.join(" · ", warnings));
            cardWarnings.setVisible(!warnings.isEmpty());
            for (URI uri : view.addresses()) {
                var row = new JPanel();
                row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
                row.setAlignmentX(LEFT_ALIGNMENT);
                row.add(label(uri.toString()));
                row.add(Box.createHorizontalStrut(6));
                var copy = new JButton("Copy");
                copy.addActionListener(event -> actions.copy().accept(uri.toString()));
                addressCopies.add(copy);
                row.add(copy);
                addresses.add(row);
            }
            Phase phase = view.state().phase();
            toggle.setText(switch (phase) {
                case STOPPED, WAITING_FOR_VAULT -> "Start";
                case FAILED -> "Retry";
                case STARTING -> "Cancel";
                case RUNNING -> "Stop";
                case STOPPING -> "Stopping…";
            });
            toggle.setEnabled(phase != Phase.STOPPING);
            edit.setText(view.temporary() ? "Save as a Server…" : "Edit…");
        }
        revalidate();
        repaint();
    }

    private String tooltip(ServerRegistry.View view) {
        var parts = new ArrayList<String>();
        parts.add(ServerRows.state(view.state()));
        parts.addAll(ServerRows.warnings(view.definition()));
        if (view.state().phase() == Phase.RUNNING && !(view.definition().bind() instanceof Bind.Loopback)) parts.add(firewall);
        return String.join(" · ", parts);
    }

    private UUID firstServer() {
        for (int i = 0; i < model.size(); i++) if (model.get(i) instanceof ServerRows.Server row) return row.view().definition().id();
        return null;
    }

    private int indexOf(UUID id) {
        if (id == null) return -1;
        for (int i = 0; i < model.size(); i++) if (model.get(i) instanceof ServerRows.Server row && row.view().definition().id().equals(id)) return i;
        return -1;
    }

    private static JMenuItem item(String title, Runnable action) {
        var item = new JMenuItem(title);
        item.addActionListener(event -> action.run());
        return item;
    }

    private static JLabel label(String text) {
        var label = new JLabel(text);
        label.putClientProperty("html.disable", Boolean.TRUE);
        return label;
    }

    private static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color == null ? fallback : color;
    }

    private final class Renderer implements ListCellRenderer<ServerRows.Row> {
        @Override public Component getListCellRendererComponent(JList<? extends ServerRows.Row> owner, ServerRows.Row value, int index,
                                                                boolean isSelected, boolean focused) {
            Color background = isSelected ? owner.getSelectionBackground() : owner.getBackground();
            Color foreground = isSelected ? owner.getSelectionForeground() : owner.getForeground();
            return switch (value) {
                case ServerRows.Problem problem -> plain("servers.toml: " + problem.message(), color("Actions.Red", foreground), background, owner.getFont());
                case ServerRows.Header header -> plain(header.title(), color("Label.disabledForeground", foreground), background, owner.getFont().deriveFont(Font.BOLD));
                case ServerRows.Server server -> serverRow(owner, server.view(), foreground, background);
            };
        }

        private Component plain(String text, Color foreground, Color background, Font font) {
            var label = label(text);
            label.setOpaque(true);
            label.setBackground(background);
            label.setForeground(foreground);
            label.setFont(font);
            label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            return label;
        }

        private Component serverRow(JList<?> owner, ServerRegistry.View view, Color foreground, Color background) {
            ServerDefinition definition = view.definition();
            Phase phase = view.state().phase();
            var row = new JPanel(new BorderLayout(6, 0));
            row.setOpaque(true);
            row.setBackground(background);
            row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            Color dot = switch (phase) {
                case RUNNING -> color("Actions.Green", foreground);
                case STARTING, STOPPING, WAITING_FOR_VAULT -> color("Actions.Yellow", foreground);
                case FAILED -> color("Actions.Red", foreground);
                case STOPPED -> color("Label.disabledForeground", foreground);
            };
            row.add(text("●", dot, owner.getFont()), BorderLayout.WEST);
            Font font = view.temporary() ? owner.getFont().deriveFont(Font.ITALIC) : owner.getFont();
            row.add(text(definition.name() + "   " + definition.protocol().label() + " :" + definition.port(), foreground, font), BorderLayout.CENTER);
            String control = (ServerRows.warnings(definition).isEmpty() ? "" : "⚠ ") + (phase == Phase.RUNNING || phase == Phase.STARTING ? "■" : "▶");
            row.add(text(control, foreground, owner.getFont()), BorderLayout.EAST);
            row.getAccessibleContext().setAccessibleName(definition.name() + ", " + ServerRows.state(view.state()));
            return row;
        }

        private JLabel text(String value, Color foreground, Font font) {
            var label = label(value);
            label.setForeground(foreground);
            label.setFont(font);
            return label;
        }
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.ui.*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add plugins/fileserver/src
git commit -m "File Server: the File Servers panel

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 13: The server editor and confirmations

**Files:**
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/{ServerEditor,ConfirmPanel}.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/{ServerEditorTest,ConfirmPanelTest}.java`

**Interfaces:**
- Consumes: definition types (Task 5); `ServerAddresses.Nic` (Task 7); `ServerRows.warnings` (Task 12).
- Produces:
  - `ServerEditor(List<ServerDefinition> others, Optional<ServerDefinition> initial, boolean existing, Set<Protocol> protocols, List<ServerAddresses.Nic> interfaces, boolean vaultPresent, Function<UUID, Optional<String>> accountName, Supplier<CompletableFuture<Optional<Account>>> accountPicker, Supplier<Optional<Path>> folderChooser, boolean linux, Consumer<ServerDefinition> onSave, Runnable onCancel)`; `record Account(UUID id, String name)`; `record BindChoice(Bind bind, String label)`; `showError(String)`; package-private controls `name`, `protocol`, `folder`, `chooseFolder`, `autostart`, `reachable`, `port`, `portHint`, `writable`, `anonymous`, `vault`, `session`, `chooseAccount`, `accountLabel`, `warnings`, `message`, `save`, `cancel`. `existing` keeps the id and locks the protocol; a new server gets a new id. Settings FS-1 does not show (TLS, passive ports, authorized keys) are kept from `initial` when the protocol is unchanged.
  - `ConfirmPanel(String text, String confirmLabel, String cancelLabel, Runnable onConfirm, Runnable onCancel)` with package-private `message`, `confirm`, `cancel`.

- [ ] **Step 1: Write the failing tests**

`ui/ServerEditorTest.java`:

```java
package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.ServerAddresses;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class ServerEditorTest {
    static final UUID ACCOUNT = UUID.randomUUID();
    @TempDir Path folder;
    final List<ServerDefinition> saved = new ArrayList<>();
    boolean cancelled;

    ServerEditor editor(Optional<ServerDefinition> initial, boolean existing, boolean vaultPresent, boolean linux, List<ServerDefinition> others) throws Exception {
        var en0 = new ServerAddresses.Nic("en0", true, false, false, List.of(InetAddress.getByName("192.168.1.20")));
        return new ServerEditor(others, initial, existing, Set.of(Protocol.HTTP), List.of(en0), vaultPresent,
            id -> id.equals(ACCOUNT) ? Optional.of("Share login") : Optional.empty(),
            () -> CompletableFuture.completedFuture(Optional.of(new ServerEditor.Account(ACCOUNT, "Share login"))),
            () -> Optional.of(folder), linux, saved::add, () -> cancelled = true);
    }

    @Test void createsAnHttpServerWithSafeDefaults() throws Exception {
        var editor = editor(Optional.empty(), false, true, false, List.of());
        assertThat(editor.save.getText()).isEqualTo("Add Server");
        assertThat(editor.protocol.getItemCount()).isEqualTo(1);
        assertThat(editor.port.getText()).isEqualTo("8080");
        assertThat(editor.anonymous.isSelected()).isTrue();
        assertThat(editor.reachable.getSelectedItem()).hasToString("This computer only");
        assertThat(editor.reachable.getItemCount()).as("loopback, all, en0").isEqualTo(3);
        assertThat(editor.reachable.getItemAt(2)).hasToString("en0 (192.168.1.20)");
        assertThat(editor.writable.isSelected()).isFalse();
        editor.save.doClick();
        assertThat(editor.message.getText()).contains("needs a name");
        editor.name.setText("docs");
        editor.save.doClick();
        assertThat(editor.message.getText()).isEqualTo("Choose a folder to share");
        editor.folder.setText(folder.resolve("missing").toString());
        editor.save.doClick();
        assertThat(editor.message.getText()).endsWith("does not exist");
        editor.chooseFolder.doClick();
        assertThat(editor.folder.getText()).isEqualTo(folder.toString());
        editor.port.setText("http");
        editor.save.doClick();
        assertThat(editor.message.getText()).contains("1 to 65535");
        editor.port.setText("8081");
        editor.save.doClick();
        assertThat(saved).singleElement().satisfies(d -> {
            assertThat(d.name()).isEqualTo("docs");
            assertThat(d.protocol()).isEqualTo(Protocol.HTTP);
            assertThat(d.folder()).isEqualTo(folder.toAbsolutePath().normalize());
            assertThat(d.bind()).isEqualTo(Bind.LOOPBACK);
            assertThat(d.port()).isEqualTo(8081);
            assertThat(d.writable()).isFalse();
            assertThat(d.login()).isEqualTo(Login.ANONYMOUS);
            assertThat(d.autostart()).isFalse();
        });
        editor.cancel.doClick();
        assertThat(cancelled).isTrue();
    }

    @Test void aVaultLoginNeedsAnAccountAndRiskyChoicesWarn() throws Exception {
        var editor = editor(Optional.empty(), false, true, false, List.of());
        editor.name.setText("vaulted");
        editor.folder.setText(folder.toString());
        editor.vault.doClick();
        assertThat(editor.warnings.getText()).contains("Plain HTTP sends the password unencrypted");
        editor.save.doClick();
        assertThat(editor.message.getText()).contains("Choose a Vault account");
        editor.chooseAccount.doClick();
        assertThat(editor.accountLabel.getText()).isEqualTo("Share login");
        editor.writable.doClick();
        editor.reachable.setSelectedIndex(1);
        assertThat(editor.warnings.getText()).contains("Writable and reachable from other computers");
        editor.save.doClick();
        assertThat(saved).singleElement().satisfies(d -> {
            assertThat(d.login()).isEqualTo(new Login.Vault(ACCOUNT));
            assertThat(d.writable()).isTrue();
            assertThat(d.bind()).isEqualTo(Bind.ALL);
        });
    }

    @Test void withoutTheVaultASessionPasswordIsOffered() throws Exception {
        var editor = editor(Optional.empty(), false, false, false, List.of());
        assertThat(editor.vault.isEnabled()).isFalse();
        assertThat(editor.vault.getText()).contains("install or enable the Credential Vault");
        assertThat(editor.chooseAccount.isEnabled()).isFalse();
        editor.name.setText("guarded");
        editor.folder.setText(folder.toString());
        editor.session.doClick();
        editor.save.doClick();
        assertThat(saved).singleElement().satisfies(d -> assertThat(d.login()).isEqualTo(Login.SESSION));
    }

    @Test void editingKeepsTheIdAndHiddenSettingsAndRefusesTakenNames() throws Exception {
        var other = ServerDefinition.create("taken", Protocol.HTTP, folder);
        var original = new ServerDefinition(UUID.randomUUID(), "docs", Protocol.HTTP, folder, new Bind.Interface("en9"), 8443,
            Optional.empty(), true, new Login.Vault(ACCOUNT), List.of(), true, false, true);
        var editor = editor(Optional.of(original), true, true, false, List.of(other, original));
        assertThat(editor.save.getText()).isEqualTo("Save");
        assertThat(editor.protocol.isEnabled()).isFalse();
        assertThat(editor.reachable.getSelectedItem()).hasToString("en9 (not available)");
        assertThat(editor.accountLabel.getText()).isEqualTo("Share login");
        assertThat(editor.autostart.isSelected()).isTrue();
        editor.name.setText("TAKEN");
        editor.save.doClick();
        assertThat(editor.message.getText()).isEqualTo("A server named taken exists");
        editor.name.setText("docs");
        editor.save.doClick();
        assertThat(saved).singleElement().isEqualTo(original);
    }

    @Test void onLinuxAPrivilegedPortGetsAHint() throws Exception {
        var linux = editor(Optional.empty(), false, true, true, List.of());
        linux.port.setText("80");
        assertThat(linux.portHint.getText()).isEqualTo("Ports below 1024 need root or CAP_NET_BIND_SERVICE on Linux — try 8080");
        linux.port.setText("8080");
        assertThat(linux.portHint.getText().isBlank()).isTrue();
        var mac = editor(Optional.empty(), false, true, false, List.of());
        mac.port.setText("80");
        assertThat(mac.portHint.getText().isBlank()).isTrue();
    }
}
```

`ui/ConfirmPanelTest.java`:

```java
package dev.jasper.fileserver.ui;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ConfirmPanelTest {
    @Test void runsTheChosenAnswer() {
        List<String> answers = new ArrayList<>();
        var panel = new ConfirmPanel("2 file servers are running and will stop.", "Quit", "Cancel", () -> answers.add("quit"), () -> answers.add("cancel"));
        assertThat(panel.message.getText()).isEqualTo("2 file servers are running and will stop.");
        assertThat(panel.confirm.getText()).isEqualTo("Quit");
        panel.cancel.doClick();
        panel.confirm.doClick();
        assertThat(answers).containsExactly("cancel", "quit");
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.ui.ServerEditorTest' --tests 'dev.jasper.fileserver.ui.ConfirmPanelTest'`
Expected: FAIL — the classes do not exist.

- [ ] **Step 3: Write `ConfirmPanel`**

```java
package dev.jasper.fileserver.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** A question with two answers, shown in a host dialog. */
public final class ConfirmPanel extends JPanel {
    final JLabel message;
    final JButton confirm, cancel;

    public ConfirmPanel(String text, String confirmLabel, String cancelLabel, Runnable onConfirm, Runnable onCancel) {
        super(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 20));
        message = new JLabel(text);
        message.putClientProperty("html.disable", Boolean.TRUE);
        add(message, BorderLayout.CENTER);
        confirm = new JButton(confirmLabel);
        cancel = new JButton(cancelLabel);
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(cancel);
        buttons.add(confirm);
        add(buttons, BorderLayout.SOUTH);
        confirm.addActionListener(event -> onConfirm.run());
        cancel.addActionListener(event -> onCancel.run());
    }
}
```

- [ ] **Step 4: Write `ServerEditor`**

```java
package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.ServerAddresses;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Add a server, edit one, or save a temporary one. Validation is inline; Save hands back a definition. */
public final class ServerEditor extends JPanel {
    /** A Vault account as the editor shows it; the UI never sees Vault types. */
    public record Account(UUID id, String name) { }

    public record BindChoice(Bind bind, String label) {
        @Override public String toString() { return label; }
    }

    final JTextField name = new JTextField(24), folder = new JTextField(24), port = new JTextField(6);
    final JComboBox<Protocol> protocol = new JComboBox<>();
    final JButton chooseFolder = new JButton("Choose…"), chooseAccount = new JButton("Choose…");
    final JCheckBox autostart = new JCheckBox("Start when Jasper starts");
    final JComboBox<BindChoice> reachable = new JComboBox<>();
    final JCheckBox writable = new JCheckBox("Allow changes (upload, rename, delete)");
    final JRadioButton anonymous = new JRadioButton("Anonymous"), vault = new JRadioButton("Vault account"), session = new JRadioButton("Session password");
    final JLabel accountLabel = label("none chosen"), portHint = label(" "), warnings = label(" "), message = label(" ");
    final JButton save, cancel = new JButton("Cancel");
    private final List<ServerDefinition> others;
    private final Optional<ServerDefinition> initial;
    private final boolean existing, vaultPresent, linux;
    private Protocol previous;
    private UUID accountId;

    public ServerEditor(List<ServerDefinition> others, Optional<ServerDefinition> initial, boolean existing, Set<Protocol> protocols,
                        List<ServerAddresses.Nic> interfaces, boolean vaultPresent, Function<UUID, Optional<String>> accountName,
                        Supplier<CompletableFuture<Optional<Account>>> accountPicker, Supplier<Optional<Path>> folderChooser,
                        boolean linux, Consumer<ServerDefinition> onSave, Runnable onCancel) {
        super(new BorderLayout(0, 8));
        this.others = List.copyOf(others);
        this.initial = initial;
        this.existing = existing;
        this.vaultPresent = vaultPresent;
        this.linux = linux;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        save = new JButton(existing ? "Save" : "Add Server");
        for (Protocol each : Protocol.values())
            if (protocols.contains(each) || initial.map(d -> d.protocol() == each).orElse(false)) protocol.addItem(each);
        protocol.setEnabled(!existing && protocol.getItemCount() > 1);
        reachable.addItem(new BindChoice(Bind.LOOPBACK, Bind.LOOPBACK.label()));
        reachable.addItem(new BindChoice(Bind.ALL, Bind.ALL.label()));
        for (ServerAddresses.Nic nic : interfaces)
            if (nic.up() && !nic.loopback() && !nic.addresses().isEmpty()) reachable.addItem(new BindChoice(new Bind.Interface(nic.name()), nic.label()));
        var logins = new ButtonGroup();
        logins.add(anonymous);
        logins.add(vault);
        logins.add(session);
        if (!vaultPresent) vault.setText("Vault account (install or enable the Credential Vault)");
        if (initial.isPresent()) {
            ServerDefinition definition = initial.get();
            name.setText(definition.name());
            protocol.setSelectedItem(definition.protocol());
            folder.setText(definition.folder().toString());
            port.setText(String.valueOf(definition.port()));
            autostart.setSelected(definition.autostart());
            writable.setSelected(definition.writable());
            selectBind(definition.bind());
            switch (definition.login()) {
                case Login.Anonymous none -> anonymous.setSelected(true);
                case Login.Session password -> session.setSelected(true);
                case Login.Vault account -> {
                    vault.setSelected(true);
                    accountId = account.credentialId();
                    accountLabel.setText(accountName.apply(accountId).orElse(vaultPresent ? "account missing" : "needs the Credential Vault"));
                }
            }
        } else {
            port.setText(String.valueOf(selectedProtocol().defaultPort()));
            defaultLogin(selectedProtocol());
        }
        previous = selectedProtocol();

        var form = new JPanel(new GridBagLayout());
        var at = new GridBagConstraints();
        at.insets = new Insets(3, 3, 3, 3);
        at.anchor = GridBagConstraints.WEST;
        at.gridy = 0;
        row(form, at, "Name", name);
        row(form, at, "Protocol", protocol);
        row(form, at, "Folder", line(folder, chooseFolder));
        row(form, at, "", autostart);
        row(form, at, "Reachable from", reachable);
        row(form, at, "Port", port);
        row(form, at, "", portHint);
        row(form, at, "", writable);
        var login = new JPanel();
        login.setLayout(new BoxLayout(login, BoxLayout.Y_AXIS));
        login.add(anonymous);
        login.add(line(vault, chooseAccount, accountLabel));
        login.add(session);
        row(form, at, "Login", login);
        row(form, at, "", warnings);
        add(form, BorderLayout.CENTER);
        var south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        message.setAlignmentX(LEFT_ALIGNMENT);
        south.add(message);
        var buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.add(Box.createHorizontalGlue());
        buttons.add(cancel);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(save);
        south.add(buttons);
        add(south, BorderLayout.SOUTH);

        protocol.addActionListener(event -> protocolChanged());
        reachable.addActionListener(event -> refresh());
        writable.addActionListener(event -> refresh());
        anonymous.addActionListener(event -> refresh());
        vault.addActionListener(event -> refresh());
        session.addActionListener(event -> refresh());
        port.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { refresh(); }
            @Override public void removeUpdate(DocumentEvent event) { refresh(); }
            @Override public void changedUpdate(DocumentEvent event) { refresh(); }
        });
        chooseFolder.addActionListener(event -> folderChooser.get().ifPresent(path -> folder.setText(path.toString())));
        chooseAccount.addActionListener(event -> accountPicker.get().thenAccept(chosen -> chosen.ifPresent(account -> {
            accountId = account.id();
            accountLabel.setText(account.name());
            vault.setSelected(true);
            refresh();
        })));
        cancel.addActionListener(event -> onCancel.run());
        save.addActionListener(event -> {
            try { onSave.accept(build()); }
            catch (IllegalArgumentException invalid) { message.setText(invalid.getMessage()); }
        });
        refresh();
    }

    public void showError(String error) { message.setText(error); }

    ServerDefinition build() {
        String nameText = name.getText().strip();
        if (nameText.isEmpty()) throw new IllegalArgumentException("A server needs a name");
        for (ServerDefinition other : others)
            if (other.name().equalsIgnoreCase(nameText) && !(existing && initial.orElseThrow().id().equals(other.id())))
                throw new IllegalArgumentException("A server named " + other.name() + " exists");
        String folderText = folder.getText().strip();
        if (folderText.isEmpty()) throw new IllegalArgumentException("Choose a folder to share");
        Path folderPath;
        try { folderPath = Path.of(folderText).toAbsolutePath().normalize(); }
        catch (InvalidPathException bad) { throw new IllegalArgumentException("The folder is not a valid path"); }
        if (!Files.isDirectory(folderPath)) throw new IllegalArgumentException("The folder " + folderText + " does not exist");
        int portValue = parsedPort();
        if (portValue < 1) throw new IllegalArgumentException("The port must be a number from 1 to 65535");
        Protocol chosen = selectedProtocol();
        Login login;
        if (!chosen.hasLogin() || anonymous.isSelected()) login = Login.ANONYMOUS;
        else if (vault.isSelected()) {
            if (accountId == null) throw new IllegalArgumentException("Choose a Vault account, or use a session password");
            login = new Login.Vault(accountId);
        } else login = Login.SESSION;
        ServerDefinition base = initial.orElse(null);
        boolean same = base != null && base.protocol() == chosen;
        return new ServerDefinition(existing ? base.id() : UUID.randomUUID(), nameText, chosen, folderPath, selectedBind(), portValue,
            same ? base.passiveRange() : Optional.empty(), writable.isSelected(), login, same ? base.authorizedKeys() : List.of(),
            same && base.tls(), same && base.requireTls(), autostart.isSelected());
    }

    private void protocolChanged() {
        Protocol chosen = selectedProtocol();
        if (port.getText().strip().equals(String.valueOf(previous.defaultPort()))) port.setText(String.valueOf(chosen.defaultPort()));
        defaultLogin(chosen);
        previous = chosen;
        refresh();
    }

    private void refresh() {
        Protocol chosen = selectedProtocol();
        boolean login = chosen.hasLogin();
        anonymous.setText(login ? "Anonymous" : "No login (TFTP has none)");
        anonymous.setEnabled(login && chosen.allowsAnonymous());
        vault.setEnabled(login && vaultPresent);
        chooseAccount.setEnabled(login && vaultPresent);
        session.setEnabled(login);
        if (!login) anonymous.setSelected(true);
        else if (anonymous.isSelected() && !chosen.allowsAnonymous()) (vaultPresent ? vault : session).setSelected(true);
        int value = parsedPort();
        portHint.setText(linux && value > 0 && value < 1024
            ? "Ports below 1024 need root or CAP_NET_BIND_SERVICE on Linux — try " + chosen.suggestedPort(value) : " ");
        Login shown = anonymous.isSelected() ? Login.ANONYMOUS : vault.isSelected() ? new Login.Vault(new UUID(0, 0)) : Login.SESSION;
        boolean tls = initial.filter(d -> d.protocol() == chosen).map(ServerDefinition::tls).orElse(false);
        List<String> list = ServerRows.warnings(chosen, tls, shown, writable.isSelected(), selectedBind());
        warnings.setText(list.isEmpty() ? " " : "⚠ " + String.join(" · ", list));
    }

    private void defaultLogin(Protocol chosen) {
        if (chosen == Protocol.HTTP || !chosen.hasLogin()) anonymous.setSelected(true);
        else (vaultPresent ? vault : session).setSelected(true);
    }

    private void selectBind(Bind bind) {
        for (int i = 0; i < reachable.getItemCount(); i++)
            if (reachable.getItemAt(i).bind().equals(bind)) { reachable.setSelectedIndex(i); return; }
        String label = bind instanceof Bind.Interface named ? named.name() + " (not available)" : bind.label();
        reachable.addItem(new BindChoice(bind, label));
        reachable.setSelectedIndex(reachable.getItemCount() - 1);
    }

    private Protocol selectedProtocol() { return (Protocol) protocol.getSelectedItem(); }
    private Bind selectedBind() { return ((BindChoice) reachable.getSelectedItem()).bind(); }

    private int parsedPort() {
        try {
            int value = Integer.parseInt(port.getText().strip());
            return value >= 1 && value <= 65535 ? value : -1;
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    private static JPanel line(Component... parts) {
        var line = new JPanel();
        line.setLayout(new BoxLayout(line, BoxLayout.X_AXIS));
        line.setAlignmentX(LEFT_ALIGNMENT);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) line.add(Box.createHorizontalStrut(6));
            line.add(parts[i]);
        }
        return line;
    }

    private static void row(JPanel form, GridBagConstraints at, String label, Component field) {
        at.gridx = 0;
        form.add(label(label), at);
        at.gridx = 1;
        form.add(field, at);
        at.gridy++;
    }

    private static JLabel label(String text) {
        var label = new JLabel(text);
        label.putClientProperty("html.disable", Boolean.TRUE);
        return label;
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.ui.*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add plugins/fileserver/src
git commit -m "File Server: the server editor and confirmation panel

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 14: Wire the plugin

**Files:**
- Replace: `plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java`
- Create (test support): `plugins/fileserver/src/test/java/dev/jasper/fileserver/TestUi.java`, `plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/UiTestAccess.java`
- Replace: `plugins/fileserver/src/test/java/dev/jasper/fileserver/FileServerPluginTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java` (panels and status counts)

**Interfaces:**
- Consumes: everything above; SDK `PluginContext`, `Panels`, `StatusBar`, `Actions`, `Windows.dialog`, `Windows.chooseDirectory`, `Windows.onQuitting`, `Activities`, `Notices`, `Services.find`, `Events`.
- Produces: `FileServerPlugin` with `ID`, `PANEL` (`dev.jasper.fileserver.panel`), `SHOW` (`.show-panel`), `NEW` (`.new`), `STOP_ALL` (`.stop-all`), `STATUS` (`.status`); package-private `record Environment(Executor ui, Supplier<List<ServerAddresses.Nic>> nics, IntPredicate portFree, Consumer<String> clipboard, Consumer<Path> reveal, Path home, String osName, ZoneId zone, BiFunction<Duration, Runnable, Runnable> every)` with `static system()`; constructor `FileServerPlugin(Environment)`; package-private `store()`, `registry()`, `currentEditor()`, `currentConfirm()`, `edit(WindowHandle, Optional<ServerDefinition>, boolean existing)`, `confirmQuit()`, `toggle(WindowHandle, UUID)`. Task 15 adds the palette scope and Serve Current Folder.
- Test support: `dev.jasper.fileserver.TestUi implements Executor` (thread-safe queue, `drain()`); `dev.jasper.fileserver.ui.UiTestAccess` exposing the panel's, editor's and confirmation's controls.

Behaviour: the panel (right anchor) is toggled by `SHOW` ("Toggle File Servers"); `NEW` opens the editor; `STOP_ALL` is enabled while any server is active. The status item shows "⇅ 1 server" / "⇅ N servers" with each running server's name and first URL as tooltip, and is hidden otherwise. While any server is active and `confirm_quit` is on, a quit guard asks "N file servers are running and will stop." (Quit / Cancel) over the active terminal window. Saved servers load from `data/servers.toml`; bad entries and a broken file are reported once through `Notices`; autostart runs after the first load. A Vault unlock starts servers waiting for it. Saving a running server whose settings changed offers "Restart Now" / "Later". Delete asks first and stops a running server.

- [ ] **Step 1: Write the test support**

`plugins/fileserver/src/test/java/dev/jasper/fileserver/TestUi.java`:

```java
package dev.jasper.fileserver;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

/** The plugin's UI thread in tests: tasks queue from any thread and run when the test drains them. */
public final class TestUi implements Executor {
    private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();

    @Override public void execute(Runnable task) { queue.add(task); }

    public void drain() {
        for (Runnable task; (task = queue.poll()) != null; ) task.run();
    }
}
```

`plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/UiTestAccess.java`:

```java
package dev.jasper.fileserver.ui;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** Owner-package access to passive UI controls for plugin integration tests. */
public final class UiTestAccess {
    private UiTestAccess() { }

    public static JButton add(FileServersPanel panel) { return panel.add; }
    public static JButton toggle(FileServersPanel panel) { return panel.toggle; }
    public static JTextArea activity(FileServersPanel panel) { return panel.activity; }
    public static JLabel cardState(FileServersPanel panel) { return panel.cardState; }
    public static JPopupMenu menuFor(FileServersPanel panel, int index) { return panel.menuFor(index); }

    public static JTextField name(ServerEditor editor) { return editor.name; }
    public static JTextField folder(ServerEditor editor) { return editor.folder; }
    public static JTextField port(ServerEditor editor) { return editor.port; }
    public static JRadioButton vault(ServerEditor editor) { return editor.vault; }
    public static JButton chooseAccount(ServerEditor editor) { return editor.chooseAccount; }
    public static JButton save(ServerEditor editor) { return editor.save; }
    public static JLabel message(ServerEditor editor) { return editor.message; }

    public static JLabel message(ConfirmPanel panel) { return panel.message; }
    public static JButton confirm(ConfirmPanel panel) { return panel.confirm; }
    public static JButton cancel(ConfirmPanel panel) { return panel.cancel; }
}
```

- [ ] **Step 2: Write the failing plugin tests**

Replace `plugins/fileserver/src/test/java/dev/jasper/fileserver/FileServerPluginTest.java`:

```java
package dev.jasper.fileserver;

import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.DefinitionFile;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.runtime.ServerState.Phase;
import dev.jasper.fileserver.ui.FileServersPanel;
import dev.jasper.fileserver.ui.UiTestAccess;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.vault.api.LockState;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class FileServerPluginTest {
    static final PluginInfo INFO = new PluginInfo("dev.jasper.fileserver", "File Server", "0.1.0",
        Set.of(Capabilities.NETWORK_LISTEN, Capabilities.TERMINAL_OBSERVE, Capabilities.PALETTE_CONTRIBUTE));
    @TempDir Path home;
    @TempDir Path root;
    final TestUi ui = new TestUi();
    final List<String> copied = new ArrayList<>();
    final List<Path> revealed = new ArrayList<>();

    FileServerPlugin plugin() {
        return new FileServerPlugin(new FileServerPlugin.Environment(ui, List::of, port -> true, copied::add, revealed::add, home,
            "Mac OS X", ZoneOffset.UTC, (interval, task) -> () -> { }));
    }

    void settle(FakePluginHost host) {
        for (int i = 0; i < 20; i++) { host.runBackground(); ui.drain(); host.flush(); }
    }

    void await(FakePluginHost host, BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { settle(host); Thread.sleep(10); }
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }

    ServerDefinition http(String name, Path folder, int port, Login login, boolean autostart) {
        var base = ServerDefinition.create(name, Protocol.HTTP, folder);
        return new ServerDefinition(base.id(), name, Protocol.HTTP, folder, Bind.LOOPBACK, port, Optional.empty(), false, login,
            List.of(), false, false, autostart);
    }

    static void click(javax.swing.JPopupMenu menu, String title) {
        Arrays.stream(menu.getComponents()).filter(c -> c instanceof JMenuItem item && item.getText().equals(title))
            .map(c -> (JMenuItem) c).findFirst().orElseThrow().doClick();
    }

    @Test void registersItsPanelActionsSettingsAndAHiddenStatus() {
        try (var host = new FakePluginHost(root)) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            settle(host);
            assertThat(host.panels()).containsExactly("dev.jasper.fileserver.panel|File Servers|RIGHT|dev.jasper.fileserver.show-panel");
            assertThat(host.actions()).contains("dev.jasper.fileserver.show-panel|Toggle File Servers|true",
                "dev.jasper.fileserver.new|New File Server…|true", "dev.jasper.fileserver.stop-all|Stop All File Servers|false");
            assertThat(host.status()).isEmpty();
            assertThat(host.declared("dev.jasper.fileserver"))
                .anySatisfy(line -> assertThat(line).startsWith("confirm_quit"))
                .anySatisfy(line -> assertThat(line).startsWith("activity_entries"));
        }
    }

    @Test void addsStartsServesAndStopsAServer() throws Exception {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            UUID window = host.addTerminalWindow();
            var panel = (FileServersPanel) host.openPanel(FileServerPlugin.PANEL, window);
            UiTestAccess.add(panel).doClick();
            assertThat(host.windows()).containsExactly("dialog|New File Server|true");
            Path shared = Files.createDirectories(home.resolve("shared"));
            Files.writeString(shared.resolve("hello.txt"), "hi");
            int port = Ports.free();
            var editor = plugin.currentEditor();
            UiTestAccess.name(editor).setText("docs");
            UiTestAccess.folder(editor).setText(shared.toString());
            UiTestAccess.port(editor).setText(String.valueOf(port));
            UiTestAccess.save(editor).doClick();
            settle(host);
            assertThat(host.windows()).isEmpty();
            assertThat(plugin.store().definitions()).extracting(ServerDefinition::name).containsExactly("docs");
            UUID id = plugin.store().definitions().getFirst().id();
            UiTestAccess.toggle(panel).doClick();
            await(host, () -> plugin.registry().server(id).orElseThrow().state().phase() == Phase.RUNNING, "the server runs");
            assertThat(host.status()).containsExactly("dev.jasper.fileserver.status|RIGHT|⇅ 1 server|docs — http://127.0.0.1:" + port + "/|dev.jasper.fileserver.show-panel");
            assertThat(host.actions()).contains("dev.jasper.fileserver.stop-all|Stop All File Servers|true");
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/hello.txt")).build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(response.body()).isEqualTo("hi");
            await(host, () -> UiTestAccess.activity(panel).getText().contains("↓ /hello.txt"), "the download is logged");
            click(UiTestAccess.menuFor(panel, 0), "Copy Address");
            click(UiTestAccess.menuFor(panel, 0), "Reveal Folder");
            settle(host);
            assertThat(copied).containsExactly("http://127.0.0.1:" + port + "/");
            assertThat(revealed).containsExactly(shared);
            assertThat(host.invoke(FileServerPlugin.STOP_ALL, window, null)).isTrue();
            await(host, () -> plugin.registry().running() == 0, "the server stops");
            assertThat(host.status()).isEmpty();
        }
    }

    @Test void quittingAsksWhileAServerRunsUnlessTurnedOff() throws Exception {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            host.addTerminalWindow();
            assertThat(host.requestQuit()).as("nothing runs").isTrue();
            var docs = http("docs", home, Ports.free(), Login.ANONYMOUS, false);
            plugin.store().put(docs);
            settle(host);
            plugin.registry().start(docs.id(), null);
            await(host, () -> plugin.registry().running() == 1, "the server runs");
            assertThat(host.requestQuit()).as("the question is pending").isFalse();
            assertThat(host.windows()).containsExactly("dialog|Quit Jasper?|true");
            assertThat(UiTestAccess.message(plugin.currentConfirm()).getText()).isEqualTo("1 file server is running and will stop.");
            UiTestAccess.cancel(plugin.currentConfirm()).doClick();
            assertThat(host.windows()).isEmpty();
            var decision = plugin.confirmQuit();
            UiTestAccess.confirm(plugin.currentConfirm()).doClick();
            assertThat(decision).isCompletedWithValue(true);
            host.setSetting("dev.jasper.fileserver", "confirm_quit", false);
            settle(host);
            assertThat(host.requestQuit()).as("the setting is off").isTrue();
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().running() == 0, "the server stops");
        }
    }

    @Test void savingARunningServerOffersARestartAndDeleteAsksFirst() throws Exception {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            UUID window = host.addTerminalWindow();
            var panel = (FileServersPanel) host.openPanel(FileServerPlugin.PANEL, window);
            var docs = http("docs", home, Ports.free(), Login.ANONYMOUS, false);
            plugin.store().put(docs);
            settle(host);
            plugin.registry().start(docs.id(), null);
            await(host, () -> plugin.registry().running() == 1, "the server runs");
            var handle = plugin.registry().server(docs.id()).orElseThrow();
            plugin.edit(plugin.windowHandle(window), Optional.of(handle.definition()), true);
            int moved = Ports.free();
            UiTestAccess.port(plugin.currentEditor()).setText(String.valueOf(moved));
            UiTestAccess.save(plugin.currentEditor()).doClick();
            settle(host);
            assertThat(host.windows()).containsExactly("dialog|Restart docs?|true");
            UiTestAccess.confirm(plugin.currentConfirm()).doClick();
            await(host, () -> plugin.registry().server(docs.id()).orElseThrow().addresses().equals(List.of(URI.create("http://127.0.0.1:" + moved + "/"))),
                "the server restarted on the new port");
            click(UiTestAccess.menuFor(panel, 0), "Delete…");
            assertThat(host.windows()).containsExactly("dialog|Delete docs?|true");
            UiTestAccess.confirm(plugin.currentConfirm()).doClick();
            await(host, () -> plugin.store().definitions().isEmpty() && plugin.registry().running() == 0, "deleted and stopped");
        }
    }

    @Test void autostartWaitsForTheVaultAndBadEntriesAreReported() throws Exception {
        var vault = new FakeVault();
        UUID account = vault.password("Share login", "guest", "pw");
        vault.state = LockState.LOCKED;
        var vaulted = http("vaulted", home, Ports.free(), new Login.Vault(account), true);
        Path data = Files.createDirectories(root.resolve("dev.jasper.fileserver/data"));
        Files.writeString(data.resolve("servers.toml"), DefinitionFile.format(List.of(vaulted)) + "\n[[server]]\nname = \"broken\"\n");
        try (var host = new FakePluginHost(root)) {
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            settle(host);
            assertThat(host.notices()).containsExactly("dev.jasper.fileserver: servers.toml: server 2: 'protocol' is required");
            assertThat(plugin.registry().server(vaulted.id()).orElseThrow().state().phase()).isEqualTo(Phase.WAITING_FOR_VAULT);
            vault.unlock();
            await(host, () -> plugin.registry().server(vaulted.id()).orElseThrow().state().phase() == Phase.RUNNING, "the server starts on unlock");
            assertThat(plugin.registry().password(vaulted.id())).contains("pw");
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().running() == 0, "the server stops");
        }
    }
}
```

The test reaches a terminal window's handle through the plugin's package-private hook `windowHandle(UUID)` (Step 4).

In `BundledSamplePluginTest.theStagedSampleLoadsFromItsJarAndItsActivityReachesBuddy`, add `"dev.jasper.fileserver.panel"` to the `containsExactlyInAnyOrder` panel list and change `contributions.status()).hasSize(4)` to `hasSize(5)`.

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.FileServerPluginTest'`
Expected: FAIL — `FileServerPlugin(Environment)` does not exist.

- [ ] **Step 4: Write `FileServerPlugin`**

```java
package dev.jasper.fileserver;

import dev.jasper.fileserver.auth.LoginResolver;
import dev.jasper.fileserver.auth.SessionPasswords;
import dev.jasper.fileserver.definition.DefinitionStore;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.runtime.Engines;
import dev.jasper.fileserver.runtime.ServerRegistry;
import dev.jasper.fileserver.runtime.ServerState.Phase;
import dev.jasper.fileserver.runtime.TransferActivities;
import dev.jasper.fileserver.ui.ConfirmPanel;
import dev.jasper.fileserver.ui.FileServersPanel;
import dev.jasper.fileserver.ui.ServerEditor;
import dev.jasper.fileserver.ui.ServerRows;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Anchor;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.IconName;
import dev.jasper.sdk.ui.PanelHost;
import dev.jasper.sdk.ui.PanelSpec;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StatusItem;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.VaultApi;
import java.awt.Desktop;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** The File Server plugin: shares local folders with other computers from the File Servers panel. */
public final class FileServerPlugin implements Plugin {
    public static final String ID = "dev.jasper.fileserver";
    public static final String PANEL = ID + ".panel";
    public static final String SHOW = ID + ".show-panel";
    public static final String NEW = ID + ".new";
    public static final String STOP_ALL = ID + ".stop-all";
    public static final String STATUS = ID + ".status";

    /** What the plugin takes from the machine; tests replace each part. */
    record Environment(Executor ui, Supplier<List<ServerAddresses.Nic>> nics, IntPredicate portFree, Consumer<String> clipboard,
                       Consumer<Path> reveal, Path home, String osName, ZoneId zone, BiFunction<Duration, Runnable, Runnable> every) {
        static Environment system() {
            return new Environment(SwingUtilities::invokeLater, ServerAddresses::system, FileServerPlugin::portFree, FileServerPlugin::copy,
                FileServerPlugin::reveal, Path.of(System.getProperty("user.home")), System.getProperty("os.name", ""), ZoneId.systemDefault(),
                (interval, task) -> {
                    var timer = new Timer((int) interval.toMillis(), event -> task.run());
                    timer.start();
                    return timer::stop;
                });
        }

        boolean linux() { return osName.toLowerCase(Locale.ROOT).contains("linux"); }
    }

    private final Environment environment;
    private PluginContext context;
    private FileServerSettings settings;
    private Optional<VaultApi> vault = Optional.empty();
    private DefinitionStore store;
    private ServerRegistry registry;
    private StatusItem status;
    private PluginAction stopAll;
    private Subscription quitGuard;
    private Runnable addressTimer;
    private final Map<UUID, FileServersPanel> panels = new LinkedHashMap<>();
    private final Map<UUID, PanelHost> panelHosts = new LinkedHashMap<>();
    private List<String> reportedWarnings = List.of();
    private String reportedError;
    private boolean stopped;
    private ServerEditor currentEditor;
    private ConfirmPanel currentConfirm;

    public FileServerPlugin() { this(Environment.system()); }

    FileServerPlugin(Environment environment) { this.environment = environment; }

    @Override public void start(PluginContext context) throws Exception {
        this.context = context;
        Files.createDirectories(context.dataDirectory());
        settings = new FileServerSettings(context.settings());
        try { vault = context.services().find(VaultApi.class); } catch (NoClassDefFoundError absent) { vault = Optional.empty(); }
        var logins = new LoginResolver(vault, () -> System.getProperty("user.name", "jasper"), SessionPasswords::generate);
        var transfers = new TransferActivities(context.activities()::begin, SHOW);
        registry = new ServerRegistry(Engines.factory(new LoginThrottle(Clock.systemUTC()), environment.nics()), logins,
            context.background(), environment.ui(), Clock.systemUTC(), settings.activityEntries::get, transfers::accept);
        store = new DefinitionStore(context.dataDirectory().resolve("servers.toml"), context.background(), environment.ui());
        var icon = context.appearance().icon(IconName.SERVER);
        context.actions().register(ActionSpec.of(SHOW, "Toggle File Servers").withIcon(icon)
                .withKeywords(List.of("file server", "share", "http", "ftp", "sftp", "tftp", "panel")),
            invoked -> context.panels().toggle(PANEL, invoked.window()));
        context.actions().register(ActionSpec.of(NEW, "New File Server…").withIcon(context.appearance().icon(IconName.ADD))
                .withKeywords(List.of("file server", "share", "folder", "http")),
            invoked -> edit(invoked.window(), Optional.empty(), false));
        stopAll = context.actions().register(ActionSpec.of(STOP_ALL, "Stop All File Servers").withKeywords(List.of("file server", "stop", "share")),
            invoked -> registry.stopAll());
        stopAll.setEnabled(false);
        context.panels().register(new PanelSpec(PANEL, "File Servers", icon, Anchor.RIGHT).withToggleActionId(SHOW), this::createPanel);
        status = context.statusBar().add(new StatusItemSpec(STATUS, Side.RIGHT, 70));
        status.setIcon(icon);
        status.setAction(SHOW);
        status.setVisible(false);
        store.onChanged(this::definitionsChanged);
        registry.onChanged(this::serversChanged);
        registry.onActivity(id -> panels.values().forEach(FileServersPanel::refreshActivity));
        settings.confirmQuit.onChanged(value -> syncQuitGuard());
        if (vault.isPresent()) context.events().subscribe(VaultApi.LOCK_STATE_CHANGED, state -> {
            if (stopped) return;
            if (state == LockState.UNLOCKED) registry.vaultUnlocked();
            refreshPanels();
        });
        addressTimer = environment.every().apply(Duration.ofSeconds(10), () -> {
            if (!stopped && panelHosts.values().stream().anyMatch(PanelHost::visible)) registry.refreshAddresses();
        });
        store.load().whenComplete((ignored, failure) -> { if (!stopped) registry.autostart(); });
    }

    @Override public void stop() {
        stopped = true;
        if (addressTimer != null) addressTimer.run();
        if (registry != null) registry.shutdown();
    }

    DefinitionStore store() { return store; }
    ServerRegistry registry() { return registry; }
    ServerEditor currentEditor() { return currentEditor; }
    ConfirmPanel currentConfirm() { return currentConfirm; }
    WindowHandle windowHandle(UUID id) { return context.terminals().window(id).orElseThrow(); }

    private JComponent createPanel(PanelHost host) {
        WindowHandle window = host.window();
        var panel = new FileServersPanel(new FileServersPanel.Actions(
            id -> toggle(window, id),
            editing -> editing.ifPresentOrElse(id -> registry.server(id).ifPresent(view -> edit(window, Optional.of(view.definition()), true)),
                () -> edit(window, Optional.empty(), false)),
            this::duplicate, id -> delete(window, id), this::reveal, environment.clipboard(),
            id -> registry.password(id).ifPresent(environment.clipboard()), registry::clearActivity),
            registry::activity, environment.home(), ServerRows.firewallHint(environment.osName()), environment.zone());
        panel.setServers(registry.servers(), store.error());
        panels.put(window.id(), panel);
        panelHosts.put(window.id(), host);
        host.onVisibility(visible -> { if (visible) registry.refreshAddresses(); });
        host.onClosed(() -> { panels.remove(window.id()); panelHosts.remove(window.id()); });
        return panel;
    }

    void toggle(WindowHandle window, UUID id) {
        registry.server(id).ifPresent(view -> {
            switch (view.state().phase()) {
                case STOPPED, FAILED, WAITING_FOR_VAULT -> registry.start(id, window);
                case RUNNING -> registry.stop(id);
                case STARTING -> registry.cancelStart(id);
                case STOPPING -> { }
            }
        });
    }

    /** New ({@code initial} empty), edit ({@code existing}), or save a temporary server under its own id. */
    void edit(WindowHandle window, Optional<ServerDefinition> initial, boolean existing) {
        boolean temporary = initial.flatMap(d -> registry.server(d.id())).map(ServerRegistry.View::temporary).orElse(false);
        String title = initial.isEmpty() ? "New File Server" : temporary ? "Save " + initial.get().name() + " as a Server" : "Edit " + initial.get().name();
        PluginDialog dialog = context.windows().dialog(new DialogSpec(title, window, true));
        boolean[] closed = {false};
        ServerEditor[] editor = new ServerEditor[1];
        editor[0] = new ServerEditor(store.definitions(), initial, existing, Engines.AVAILABLE, environment.nics().get(), vault.isPresent(),
            this::accountName, () -> pickAccount(window),
            () -> context.windows().chooseDirectory(window, "Folder to share", initial.map(ServerDefinition::folder)),
            environment.linux(),
            definition -> store.put(definition).whenComplete((ignored, failure) -> environment.ui().execute(() -> {
                if (stopped) return;
                if (failure != null) { if (!closed[0]) editor[0].showError(message(failure)); return; }
                dialog.close();
                registry.server(definition.id()).filter(ServerRegistry.View::restartNeeded).ifPresent(view -> confirmRestart(window, view));
            })),
            dialog::close);
        currentEditor = editor[0];
        dialog.setContent(editor[0]);
        dialog.onClosed(() -> { closed[0] = true; if (currentEditor == editor[0]) currentEditor = null; });
        dialog.show();
    }

    private void confirmRestart(WindowHandle window, ServerRegistry.View view) {
        String name = view.definition().name();
        confirm(window, "Restart " + name + "?", "‘" + name + "’ is running with its old settings. Restart it now to apply the changes?",
            "Restart Now", "Later", () -> registry.stop(view.definition().id()).thenRun(() -> registry.start(view.definition().id(), window)), () -> { });
    }

    private void duplicate(UUID id) {
        registry.server(id).ifPresent(view -> report(store.put(view.definition().copyAs(uniqueName(view.definition().name() + " copy")))));
    }

    private void delete(WindowHandle window, UUID id) {
        registry.server(id).ifPresent(view -> confirm(window, "Delete " + view.definition().name() + "?",
            "Delete ‘" + view.definition().name() + "’? Its settings are removed; the shared folder is not touched.", "Delete", "Cancel",
            () -> registry.stop(id).thenRun(() -> report(store.remove(id))), () -> { }));
    }

    private void reveal(UUID id) {
        registry.server(id).ifPresent(view -> {
            Path folder = view.definition().folder();
            context.background().execute(() -> {
                try { environment.reveal().accept(folder); }
                catch (RuntimeException failure) { environment.ui().execute(() -> { if (!stopped) context.notices().error("Could not show " + folder + ": " + message(failure)); }); }
            });
        });
    }

    /** The quit guard: asks over the active terminal window; allows the quit when there is none. */
    CompletableFuture<Boolean> confirmQuit() {
        int count = registry.active();
        if (count == 0) return CompletableFuture.completedFuture(true);
        Optional<WindowHandle> owner = context.terminals().activeWindow()
            .or(() -> context.terminals().windows().stream().filter(WindowHandle::isOpen).findFirst());
        if (owner.isEmpty()) return CompletableFuture.completedFuture(true);
        var decision = new CompletableFuture<Boolean>();
        String text = count == 1 ? "1 file server is running and will stop." : count + " file servers are running and will stop.";
        confirm(owner.get(), "Quit Jasper?", text, "Quit", "Cancel", () -> decision.complete(true), () -> decision.complete(false));
        return decision;
    }

    private void syncQuitGuard() {
        boolean wanted = !stopped && registry.active() > 0 && settings.confirmQuit.get();
        if (wanted && quitGuard == null) quitGuard = context.windows().onQuitting(this::confirmQuit);
        else if (!wanted && quitGuard != null) { quitGuard.close(); quitGuard = null; }
    }

    /** A modal question; closing the dialog any other way counts as the cancel answer. */
    private void confirm(WindowHandle window, String title, String text, String confirmLabel, String cancelLabel, Runnable onConfirm, Runnable onCancel) {
        PluginDialog dialog = context.windows().dialog(new DialogSpec(title, window, true));
        boolean[] decided = {false};
        ConfirmPanel[] panel = new ConfirmPanel[1];
        panel[0] = new ConfirmPanel(text, confirmLabel, cancelLabel,
            () -> { decided[0] = true; dialog.close(); onConfirm.run(); },
            () -> { decided[0] = true; dialog.close(); onCancel.run(); });
        currentConfirm = panel[0];
        dialog.setContent(panel[0]);
        dialog.onClosed(() -> {
            if (currentConfirm == panel[0]) currentConfirm = null;
            if (!decided[0]) { decided[0] = true; onCancel.run(); }
        });
        dialog.show();
    }

    private void definitionsChanged() {
        if (stopped) return;
        registry.setDefinitions(store.definitions());
        List<String> warnings = store.warnings();
        if (!warnings.isEmpty() && !warnings.equals(reportedWarnings)) for (String warning : warnings) {
            context.log().log(System.Logger.Level.WARNING, "servers.toml: {0}", warning);
            context.notices().error("servers.toml: " + warning);
        }
        reportedWarnings = warnings;
        String error = store.error().orElse(null);
        if (error != null && !error.equals(reportedError)) context.notices().error(error + ". File Server will not save changes until it is fixed.");
        reportedError = error;
        refreshPanels();
    }

    private void serversChanged() {
        if (stopped) return;
        refreshPanels();
        int running = registry.running();
        status.setText(running == 1 ? "⇅ 1 server" : "⇅ " + running + " servers");
        status.setTooltip(registry.servers().stream().filter(view -> view.state().phase() == Phase.RUNNING)
            .map(view -> view.definition().name() + view.addresses().stream().findFirst().map(uri -> " — " + uri).orElse(""))
            .collect(Collectors.joining("\n")));
        status.setVisible(running > 0);
        stopAll.setEnabled(registry.active() > 0);
        syncQuitGuard();
    }

    private void refreshPanels() {
        for (FileServersPanel panel : panels.values()) {
            panel.setServers(registry.servers(), store.error());
            panel.refreshActivity();
        }
    }

    private Optional<String> accountName(UUID id) {
        if (vault.isEmpty()) return Optional.empty();
        return vault.get().credentials().stream().filter(d -> d.id().equals(id)).findFirst().map(CredentialDescriptor::name);
    }

    private CompletableFuture<Optional<ServerEditor.Account>> pickAccount(WindowHandle window) {
        if (vault.isEmpty()) return CompletableFuture.completedFuture(Optional.empty());
        return vault.get().pick(window).thenApply(chosen -> chosen.map(d -> new ServerEditor.Account(d.id(), d.name())));
    }

    private String uniqueName(String base) {
        Set<String> taken = new HashSet<>();
        store.definitions().forEach(d -> taken.add(d.name().toLowerCase(Locale.ROOT)));
        registry.servers().forEach(view -> taken.add(view.definition().name().toLowerCase(Locale.ROOT)));
        if (!taken.contains(base.toLowerCase(Locale.ROOT))) return base;
        for (int n = 2; ; n++) if (!taken.contains((base + " " + n).toLowerCase(Locale.ROOT))) return base + " " + n;
    }

    private void report(CompletableFuture<Void> mutation) {
        mutation.whenComplete((ignored, failure) -> environment.ui().execute(() -> {
            if (!stopped && failure != null) context.notices().error(message(failure));
        }));
    }

    static String message(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    static boolean portFree(int port) {
        try (var socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            return true;
        } catch (IOException taken) {
            return false;
        }
    }

    static void copy(String text) {
        try { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null); }
        catch (HeadlessException | IllegalStateException unavailable) { /* nothing to copy to */ }
    }

    static void reveal(Path folder) {
        if (!Desktop.isDesktopSupported()) throw new UnsupportedOperationException("This system cannot show folders");
        Desktop desktop = Desktop.getDesktop();
        try {
            if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) desktop.browseFileDirectory(folder.toFile());
            else if (desktop.isSupported(Desktop.Action.OPEN)) desktop.open(folder.toFile());
            else throw new UnsupportedOperationException("This system cannot show folders");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
```

- [ ] **Step 5: Run the plugin's checks and the bundled-plugin test**

Run: `./gradlew :jasper-plugin-fileserver:check :jasper-app:test --tests 'dev.jasper.app.plugins.BundledSamplePluginTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add plugins/fileserver/src jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java
git commit -m "File Server: panel, editor, status, quit confirmation and autostart wired to the SDK

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 15: Palette scope and Serve Current Folder

**Files:**
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/FileServerScope.java`
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/FileServerPlugin.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/FileServerScopeTest.java`; add tests to `FileServerPluginTest.java`

**Interfaces:**
- Consumes: `ServerRegistry.View`, `ServerRows` (Tasks 11–12); SDK `PaletteScope`, `ScopeSpec`, `PaletteVerb`, `PaletteRow`, `PaletteResults`, `PaletteQuery`, `ActionContext`, `PaneHandle.info().workingDirectory()`.
- Produces:
  - `FileServerScope(Supplier<List<View>> servers, Path home, BiConsumer<WindowHandle, UUID> toggle, BiConsumer<WindowHandle, UUID> edit, String shortcutActionId)` with `ID = "dev.jasper.fileserver.scope"`, verbs `TOGGLE` ("toggle", "Start or Stop") and `EDIT` ("edit", "Edit"), `changed()`; row ids `server.<uuid>`, token the server's id
  - `FileServerPlugin.SERVERS` (`.servers`, "Start or Stop a File Server…", opens the scope) and `SERVE_HTTP` (`.serve-http`, "Serve Current Folder over HTTP")
  - Serve Current Folder: the invoked pane's (else the active pane's) local working directory if it exists, else the home folder; a temporary, read-only, loopback, anonymous HTTP server named "<folder name> (HTTP)" (made unique, at most 80 characters) on the default port or the next free one up to 100 above; the panel is shown with it selected.

- [ ] **Step 1: Write the failing tests**

`ui/FileServerScopeTest.java`:

```java
package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.runtime.ServerRegistry;
import dev.jasper.fileserver.runtime.ServerState;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FileServerScopeTest {
    static final Path HOME = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().resolve("home");
    final List<String> events = new ArrayList<>();
    final List<ServerRegistry.View> views = new ArrayList<>();
    final WindowHandle window = new WindowHandle() {
        final UUID id = UUID.randomUUID();
        @Override public UUID id() { return id; }
        @Override public List<TabHandle> tabs() { return List.of(); }
        @Override public Optional<TabHandle> activeTab() { return Optional.empty(); }
        @Override public boolean isActive() { return true; }
        @Override public boolean isOpen() { return true; }
        @Override public void toFront() { }
    };
    final PaletteQuery query = new PaletteQuery(window, Optional.empty(), 50, true);
    final FileServerScope scope = new FileServerScope(() -> views, HOME, (w, id) -> events.add("toggle " + id), (w, id) -> events.add("edit " + id),
        "dev.jasper.fileserver.servers");

    ServerRegistry.View view(String name, ServerState state, boolean temporary) {
        var definition = new ServerDefinition(UUID.randomUUID(), name, Protocol.HTTP, HOME.resolve("work"), Bind.LOOPBACK, 8080, Optional.empty(),
            false, Login.ANONYMOUS, List.of(), false, false, false);
        var view = new ServerRegistry.View(definition, temporary, state, List.of(), Optional.empty(), false);
        views.add(view);
        return view;
    }

    @Test void listsServersWithTheirStateAndRunsTheChosenVerb() {
        var docs = view("docs", ServerState.RUNNING, false);
        var firmware = view("firmware", ServerState.STOPPED, false);
        var quick = view("work (HTTP)", ServerState.STOPPING, true);
        assertThat(scope.spec().id()).isEqualTo(FileServerScope.ID);
        assertThat(scope.spec().verbs()).containsExactly(FileServerScope.TOGGLE, FileServerScope.EDIT);
        assertThat(scope.spec().shortcutActionId()).contains("dev.jasper.fileserver.servers");
        List<PaletteRow> rows = scope.search("", query).rows();
        assertThat(rows).extracting(PaletteRow::title).containsExactly("docs", "firmware", "work (HTTP)");
        assertThat(rows.getFirst().detail()).contains("HTTP :8080 · ~" + File.separator + "work");
        assertThat(rows.getFirst().tag()).contains("Running");
        assertThat(scope.search("FIRM", query).rows()).extracting(PaletteRow::title).containsExactly("firmware");
        assertThat(scope.available(rows.get(2), FileServerScope.TOGGLE, query)).as("stopping").isFalse();
        assertThat(scope.available(rows.get(2), FileServerScope.EDIT, query)).isTrue();
        scope.execute(rows.get(1), FileServerScope.TOGGLE, query);
        scope.execute(rows.get(0), FileServerScope.EDIT, query);
        assertThat(events).containsExactly("toggle " + firmware.definition().id(), "edit " + docs.definition().id());
        int[] changes = {0};
        var subscription = scope.onChanged(() -> changes[0]++);
        scope.changed();
        subscription.close();
        scope.changed();
        assertThat(changes[0]).isEqualTo(1);
        assertThat(quick.temporary()).isTrue();
    }
}
```

Add to `FileServerPluginTest.java` (imports `dev.jasper.fileserver.ui.FileServerScope`, `dev.jasper.sdk.terminal.PaneInfo`, `dev.jasper.sdk.terminal.SessionKind`, `dev.jasper.sdk.terminal.SessionState`, `java.util.OptionalInt`, `java.util.function.IntPredicate`), plus a helper that lets a test mark ports as taken:

```java
    FileServerPlugin plugin(IntPredicate portFree) {
        return new FileServerPlugin(new FileServerPlugin.Environment(ui, List::of, portFree, copied::add, revealed::add, home,
            "Mac OS X", ZoneOffset.UTC, (interval, task) -> () -> { }));
    }

    static PaneInfo pane(Optional<Path> directory) {
        return new PaneInfo("zsh", directory, Optional.empty(), 80, 24, true, SessionKind.LOCAL, Optional.empty(), SessionState.RUNNING,
            OptionalInt.empty(), "zsh");
    }

    @Test void thePaletteListsServersAndStartsThem() throws Exception {
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            UUID window = host.addTerminalWindow();
            var docs = http("docs", home, Ports.free(), Login.ANONYMOUS, false);
            plugin.store().put(docs);
            settle(host);
            assertThat(host.searchScope(FileServerScope.ID, "docs", window, null)).singleElement().asString().contains("docs");
            assertThat(host.invoke(FileServerPlugin.SERVERS, window, null)).isTrue();
            assertThat(host.paletteOpens()).anySatisfy(line -> assertThat(line).contains(FileServerScope.ID));
            host.executeInScope(FileServerScope.ID, "server." + docs.id(), "toggle", window, null);
            await(host, () -> plugin.registry().running() == 1, "the palette started it");
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().running() == 0, "the server stops");
        }
    }

    @Test void servesTheFocusedFolderOnTheNextFreePort() throws Exception {
        Path project = Files.createDirectories(home.resolve("project"));
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin(port -> port != 8080);
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "zsh");
            UUID local = host.addTerminalPane(tab, pane(Optional.of(project)));
            var panel = (FileServersPanel) host.openPanel(FileServerPlugin.PANEL, window);
            assertThat(host.invoke(FileServerPlugin.SERVE_HTTP, window, local)).isTrue();
            await(host, () -> plugin.registry().servers().stream().noneMatch(v -> v.state().phase() == Phase.STARTING), "the start settles");
            var served = plugin.registry().servers().getFirst();
            assertThat(served.temporary()).isTrue();
            assertThat(served.definition().name()).isEqualTo("project (HTTP)");
            assertThat(served.definition().folder()).isEqualTo(project);
            assertThat(served.definition().port()).as("8080 was taken").isEqualTo(8081);
            assertThat(served.definition().bind()).isEqualTo(Bind.LOOPBACK);
            assertThat(served.definition().writable()).isFalse();
            assertThat(served.definition().login()).isEqualTo(Login.ANONYMOUS);
            assertThat(panel.selected()).contains(served.definition().id());
            UUID elsewhere = host.addTerminalPane(tab, pane(Optional.empty()));
            assertThat(host.invoke(FileServerPlugin.SERVE_HTTP, window, elsewhere)).isTrue();
            await(host, () -> plugin.registry().servers().size() == 2
                && plugin.registry().servers().stream().noneMatch(v -> v.state().phase() == Phase.STARTING), "the second start settles");
            assertThat(plugin.registry().servers().get(1).definition().folder()).as("no folder: home").isEqualTo(home);
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().active() == 0, "everything stops");
        }
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.ui.FileServerScopeTest' --tests 'dev.jasper.fileserver.FileServerPluginTest'`
Expected: FAIL — `FileServerScope`, `SERVERS` and `SERVE_HTTP` do not exist.

- [ ] **Step 3: Write `FileServerScope`**

```java
package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.runtime.ServerRegistry;
import dev.jasper.fileserver.runtime.ServerState.Phase;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.terminal.WindowHandle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** The palette's "File Servers" scope: every server with its state; Enter starts or stops it. Searches do no I/O. */
public final class FileServerScope implements PaletteScope {
    public static final String ID = "dev.jasper.fileserver.scope";
    public static final PaletteVerb TOGGLE = new PaletteVerb("toggle", "Start or Stop");
    public static final PaletteVerb EDIT = new PaletteVerb("edit", "Edit");

    private final Supplier<List<ServerRegistry.View>> servers;
    private final Path home;
    private final BiConsumer<WindowHandle, UUID> toggle, edit;
    private final String shortcutActionId;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public FileServerScope(Supplier<List<ServerRegistry.View>> servers, Path home, BiConsumer<WindowHandle, UUID> toggle,
                           BiConsumer<WindowHandle, UUID> edit, String shortcutActionId) {
        this.servers = servers; this.home = home; this.toggle = toggle; this.edit = edit; this.shortcutActionId = shortcutActionId;
    }

    @Override public ScopeSpec spec() {
        return ScopeSpec.of(ID, "File Servers", "Search file servers", List.of(TOGGLE, EDIT))
            .withDescription("Start or stop a file server").withShortcutActionId(shortcutActionId).withInRecents(false);
    }

    @Override public PaletteResults search(String query, PaletteQuery context) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        var rows = new ArrayList<PaletteRow>();
        for (ServerRegistry.View view : servers.get()) {
            ServerDefinition definition = view.definition();
            String detail = definition.protocol().label() + " :" + definition.port() + " · " + ServerRows.folder(definition.folder(), home);
            if (!needle.isEmpty() && !(definition.name() + " " + detail).toLowerCase(Locale.ROOT).contains(needle)) continue;
            rows.add(PaletteRow.of("server." + definition.id(), definition.name()).withDetail(detail).withTag(ServerRows.tag(view.state()))
                .withToken(definition.id()));
            if (rows.size() == PaletteResults.MAX_ROWS) break;
        }
        return PaletteResults.of(rows);
    }

    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        Optional<ServerRegistry.View> view = view(row);
        if (view.isEmpty()) return false;
        if (verb.equals(EDIT)) return true;
        return view.get().state().phase() != Phase.STOPPING;
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        view(row).ifPresent(view -> {
            if (verb.equals(EDIT)) edit.accept(context.window(), view.definition().id());
            else toggle.accept(context.window(), view.definition().id());
        });
    }

    @Override public Subscription onChanged(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void changed() { listeners.forEach(Runnable::run); }

    private Optional<ServerRegistry.View> view(PaletteRow row) {
        if (!(row.token() instanceof UUID id)) return Optional.empty();
        return servers.get().stream().filter(view -> view.definition().id().equals(id)).findFirst();
    }
}
```

- [ ] **Step 4: Wire the scope and the serve action into `FileServerPlugin`**

Add imports `dev.jasper.fileserver.definition.Bind`, `dev.jasper.fileserver.definition.Protocol`, `dev.jasper.fileserver.ui.FileServerScope`, `dev.jasper.sdk.ui.ActionContext`.

Add the constants after `STATUS`:

```java
    public static final String SERVERS = ID + ".servers";
    public static final String SERVE_HTTP = ID + ".serve-http";
```

Add the field `private FileServerScope scope;`.

In `start`, right after the `stopAll.setEnabled(false);` line:

```java
        context.actions().register(ActionSpec.of(SERVERS, "Start or Stop a File Server…").withIcon(icon)
                .withKeywords(List.of("file server", "start", "stop", "share")),
            invoked -> context.palette().open(invoked.window(), FileServerScope.ID, Optional.empty(), Optional.empty()));
        context.actions().register(ActionSpec.of(SERVE_HTTP, "Serve Current Folder over HTTP").withIcon(icon)
                .withKeywords(List.of("file server", "share", "serve", "http", "folder")),
            invoked -> serve(invoked, Protocol.HTTP));
        scope = new FileServerScope(() -> registry.servers(), environment.home(), this::toggle,
            (window, id) -> registry.server(id).ifPresent(view -> edit(window, Optional.of(view.definition()), true)), SERVERS);
        context.palette().register(scope);
```

In `serversChanged`, after `refreshPanels();` add `scope.changed();`.

Add the methods:

```java
    /** Shares the folder the shell is in, read-only and on this computer only, until stopped. */
    void serve(ActionContext invoked, Protocol protocol) {
        Path folder = invoked.pane().or(() -> context.terminals().activePane())
            .flatMap(pane -> pane.info().workingDirectory()).filter(Files::isDirectory).orElse(environment.home());
        Set<Integer> used = registry.servers().stream().filter(view -> !view.state().phase().startable())
            .map(view -> view.definition().port()).collect(Collectors.toSet());
        int port = protocol.defaultPort();
        while ((used.contains(port) || !environment.portFree().test(port)) && port < protocol.defaultPort() + 100) port++;
        Path leaf = folder.getFileName();
        String folderName = leaf == null ? folder.toString() : leaf.toString();
        if (folderName.length() > 60) folderName = folderName.substring(0, 60) + "…";
        var created = ServerDefinition.create(uniqueName(folderName + " (" + protocol.label() + ")"), protocol, folder);
        var definition = new ServerDefinition(created.id(), created.name(), protocol, folder, Bind.LOOPBACK, port, Optional.empty(), false,
            created.login(), List.of(), false, false, false);
        registry.startTemporary(definition, invoked.window());
        showPanel(invoked.window(), definition.id());
    }

    private void showPanel(WindowHandle window, UUID id) {
        PanelHost host = panelHosts.get(window.id());
        if (host == null) context.panels().toggle(PANEL, window);
        else if (!host.visible()) host.show();
        FileServersPanel panel = panels.get(window.id());
        if (panel != null) panel.select(id);
    }
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:check`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add plugins/fileserver/src
git commit -m "File Server: palette scope and Serve Current Folder over HTTP

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 16: Documentation, status and the whole-repo check

**Files:**
- Create: `docs/file-server.md`
- Modify: `docs/README.md` (link after the Remote entry), `docs/STATUS.md` (a new entry at the top of "Current state")
- Modify: this plan's header (status banner with deviations found while executing)

**Interfaces:**
- Consumes: everything above. Produces documentation only.

- [ ] **Step 1: Write the user guide**

`docs/file-server.md`:

```markdown
# File Server: share a folder with other computers

File Server is bundled with Jasper. It shares a folder on this computer with other programs and computers, the way
MobaXterm's Servers window does. This version serves **HTTP**; FTP/FTPS, SFTP and TFTP follow.

## The File Servers panel

Choose **Toggle File Servers** in the command palette (or click the server icon on the rail) to show the panel. Each
row is one server: a dot for its state, its name, protocol and port, and ▶ or ■ at the right to start or stop it
(double-click or Enter does the same). Below the list, the selected server shows its folder, who may log in, every
address other computers can use (each with **Copy**), warnings, and its activity: logins, failed logins, downloads
and uploads with their sizes, and errors. The activity stays in memory; **Clear** empties it.

Press **+** (or **New File Server…**) to add a server. Right-click a row for Start/Stop, Edit…, Duplicate, Delete…,
Reveal Folder, Copy Address and Copy Password. The status bar shows "⇅ 2 servers" while servers run; click it to show
the panel.

## Safe by default

- **Read-only.** Turn on *Allow changes (upload, rename, delete)* to let clients write.
- **This computer only.** *Reachable from* chooses *All networks* or one network interface; the panel then lists
  the addresses others can use. macOS and Windows ask once whether Jasper may accept connections; on macOS, Jasper
  also needs Privacy & Security › Local Network.
- **Unprivileged ports.** HTTP starts at 8080. On Linux, ports below 1024 need root or `CAP_NET_BIND_SERVICE`; the
  editor suggests one 8000 higher.
- **Jailed.** Clients cannot leave the folder, through `..` or through a link that points outside it.
- **Warnings.** The panel and the editor warn about plain HTTP with a password, a writable server anyone can reach,
  and a writable server open to other computers.

## Logins

*Anonymous* needs no login. *Session password* makes a new password each time the server starts (Copy Password
copies it; the user name is yours). *Vault account* uses an account you keep in the Credential Vault; choose it with
**Choose…**. The Vault may ask to unlock and to allow File Server. Repeated wrong passwords from one address are
slowed down and, after ten, refused for five minutes.

HTTP logins use Basic auth, which sends the password unencrypted; HTTPS arrives with FTP/FTPS in the next version.

## Serve Current Folder

**Serve Current Folder over HTTP** shares the folder your shell is in (or your home folder), read-only and on this
computer only, without saving it. It appears under *Temporary* and disappears when stopped; **Save as a Server…**
keeps it.

## Quitting

Servers run inside Jasper and stop when it quits. While any runs, quitting or restarting asks first; turn that off
with *Ask before quitting while servers run* in File Server's settings.

## Files

Saved servers live in `plugins/dev.jasper.fileserver/data/servers.toml` under Jasper's home; the file never holds a
password. Settings live in `plugins/dev.jasper.fileserver/dev.jasper.fileserver.toml`.

## For other programs

Any browser can download from the listing; on a writable server the listing also uploads and deletes. From a
terminal: `curl -O http://HOST:8080/file` downloads, `curl -T file http://HOST:8080/folder/file` uploads, and
`curl -X DELETE http://HOST:8080/folder/file` deletes. The server only answers requests addressed to an IP address,
`localhost` or this computer's name, and refuses forms posted from other web sites.
```

In `docs/README.md`, after the Remote line, add:

```markdown
- [File Server](file-server.md): share a folder over HTTP from the File Servers panel (FTP/FTPS, SFTP and TFTP follow).
```

- [ ] **Step 2: Record the status**

At the top of "## Current state — 2026-09-30" in `docs/STATUS.md`, add an entry in the house style:

```markdown
- **File Server FS-1: foundation and HTTP** (branch `claude/file-server-fs1`, spec
  `docs/superpowers/specs/2026-09-30-jasper-file-server-design.md`, plan
  `docs/superpowers/plans/2026-09-30-file-server-fs1.md`): SDK 0.11.0 adds the `network.listen` capability and
  `Windows.onQuitting` quit guards (app `QuitGuards`, testkit fake and contract tests); every bundled plugin declares
  `sdk = ">=0.11.0, <0.12"`. New bundled plugin File Server (`plugins/fileserver`, `dev.jasper.fileserver` 0.1.0):
  File Servers rail panel, editor, status item, palette scope, Serve Current Folder over HTTP, quit confirmation,
  autostart (waiting for a locked Vault), Vault or session logins, `ShareRoot` jail, login throttle, HTTP engine on
  the JDK server (listings, ranges, Basic auth, uploads and deletes on writable shares, Host and Origin checks).
  Deviations from the spec: user doc in `docs/file-server.md`; setting keys `confirm_quit`/`activity_entries`;
  privileged-port suggestion is port + 8000; Host/Origin checks added; palette titles in house style with a scope for
  per-server Start/Stop; saving a temporary server adopts it. `./gradlew check` green: <fill in the module counts from
  the XML>. Not merged. GUI checks pending with the user: New File Server and start it; open the URL from a phone on
  the LAN with All networks (macOS firewall and Local Network prompts); upload and delete from the listing on a
  writable server; a Vault account login with a locked Vault and Start With Jasper; Serve Current Folder from a
  terminal; the quit confirmation; Reveal Folder and Copy Address.
```

Replace `<fill in the module counts from the XML>` with the real counts from Step 3 before committing.

- [ ] **Step 3: Run everything**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Then count results:

```bash
python3 - <<'PY'
import pathlib, xml.etree.ElementTree as ET
from collections import defaultdict
totals = defaultdict(lambda: [0, 0, 0])
for path in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    root = ET.parse(path).getroot()
    module = path.parts[0] if path.parts[0] != "plugins" else "plugins/" + path.parts[1]
    totals[module][0] += int(root.get("tests")); totals[module][1] += int(root.get("failures")) + int(root.get("errors")); totals[module][2] += int(root.get("skipped"))
for module, (tests, failed, skipped) in sorted(totals.items()): print(f"{module}: {tests}/{failed}/{skipped}")
PY
```

and run the source-hygiene check from `AGENTS.md`:

```bash
python3 - <<'PY'
import pathlib
for p in [p for root in ("jasper-terminal/src", "jasper-app/src", "jasper-buddy/src", "jasper-sdk/src", "jasper-sdk-testkit/src", "plugins") for p in pathlib.Path(root).rglob("*.java")]:
    text = p.read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(p, "bad chars:", bad)
PY
```

Expected: no output from the hygiene check.

- [ ] **Step 4: Update the plan's status banner**

Under this plan's title add `Status: executed on claude/file-server-fs1 (<date>); deviations: …` listing anything that differed from the plan text (or "none beyond the planning deviations above").

- [ ] **Step 5: Commit**

```bash
git add docs/file-server.md docs/README.md docs/STATUS.md docs/superpowers/plans/2026-09-30-file-server-fs1.md
git commit -m "File Server FS-1: user guide and status

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

GUI checks are the user's; do not launch the app.
