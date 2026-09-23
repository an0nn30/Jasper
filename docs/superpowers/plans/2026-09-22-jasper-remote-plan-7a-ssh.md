# Remote Plan 7a — SSH Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The bundled `dev.jasper.remote` plugin's SSH slice: saved hosts in `hosts.toml`, import from `~/.ssh/config`, host-key trust, Vault or SSH-agent authentication, ProxyJump, one shared MINA session per host with reference-counted shell channels, a hosts panel, host editor, palette scope, actions, SSH menu and status item.

**Architecture:** Pure layers (`hosts`, `trust`, `agent`) under a `client` layer that owns MINA: `Connections` is a UI-thread registry of shared sessions, `ConnectPipeline` runs each hop's connect / verify / authenticate on the background executor and completes on the UI thread, `ShellChannels` adapts a MINA shell channel to the SDK's `TerminalConnection`. `RemotePlugin` wires those to the SDK (session provider, panel, palette, actions, status). Every screen is a `JPanel` with package-private fields tests drive headless; the host supplies windows and dialogs.

**Tech Stack:** Java 25 (JBR) Swing, `jasper-sdk` 0.7, `dev.jasper.vault.api` (`compileOnly`), Apache MINA sshd `sshd-core:2.19.0` (bundled, with `slf4j-jdk14` so its logs reach JUL) and `bcprov-jdk18on:1.85.2` (bundled, so MINA parses the Vault's Ed25519 and passphrase-protected OpenSSH keys), tomlj (bundled), JUnit 6 + AssertJ, `jasper-sdk-testkit`, an embedded MINA `SshServer` on loopback for integration tests.

**Spec:** `docs/superpowers/specs/2026-09-22-jasper-remote-design.md`

> **Execution:** Native inline on `codex/remote-7a` in the Codex-managed worktree, from current main. All eight tasks complete. One independent final review completed, all findings fixed with regressions. Full check and installDist pass (1,604 tests: 1,601 passed, three expected skips). Corrections and deviations are recorded in `docs/STATUS.md` and `docs/remote-7a-verification.md`; the SDK gained Panels.toggle (0.7.2). Original snippets below are retained as the approved plan. Native acceptance remains user-run.

## Global Constraints

- The plugin imports only the JDK, `dev.jasper.sdk.*`, `dev.jasper.vault.api.*`, its own packages and its bundled libraries (`verifyPluginArchitecture`; `gradle/plugin-architecture.gradle.kts` gains `":jasper-plugin-remote" to listOf("dev.jasper.vault.api")`).
- Descriptor: id `dev.jasper.remote`, name `Remote`, `capabilities = ["terminal.open", "session.provide", "terminal.observe", "palette.contribute"]`, `requires = [{ id = "dev.jasper.vault", version = ">=0.1", optional = true }]`, sdk `>=0.7, <0.8`.
- `hosts.toml` never holds a secret; `known_hosts` under `~/.ssh` is never written.
- The SDK and the registry are used on the UI thread only; connect, verify and authenticate run on `context.background()`; MINA callbacks hop to the UI thread before touching plugin state. Timeouts: connect 10 s, auth 30 s (settings).
- A shell channel's `TerminalConnection.close` closes only that channel and releases one reference; the session closes after `session_linger_seconds` (5) with no references, or at once when it dies.
- Every failure reaches the user as one of the spec's section-5 messages through `pending.fail`; store and trust-file write failures use `notices().error`.
- Headless tests only; no external host is contacted (loopback MINA server); never launch the GUI; one commit per task with the `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` trailer; never commit on `main` (branch `claude/remote-7a`, worktree `.worktrees/remote-7a`).
- No raw control, private-use or surrogate characters in source (`—`, `·` as escapes).
- Run `./gradlew :jasper-plugin-remote:test -q` per task; `./gradlew check -q` before the final commit.
- zsh is the shell: quote `echo '=== x'` separators (a word starting with `=` is a command lookup).

---

### Task 1: Module, settings, host model and the hosts.toml store

**Files:**
- Create: `plugins/remote/build.gradle.kts`, `plugins/remote/src/main/resources/plugin.toml`, `plugins/remote/src/main/resources/settings.toml`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/package-info.java`, `RemoteSettings.java`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/hosts/package-info.java`, `RemoteHost.java`, `Auth.java`, `HostFile.java`, `HostStore.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/hosts/HostFileTest.java`, `HostStoreTest.java`, `plugins/remote/src/test/java/dev/jasper/remote/RemoteSettingsTest.java`

**Interfaces:**
- Produces: `RemoteHost(UUID id, String name, String hostname, int port, String username, Auth auth, String group, boolean favorite, Optional<UUID> jump, Instant created, Instant updated)` with `RemoteHost.create(...)`, `withEdited(...)`, `withFavorite(boolean)`, `label()` = `user@host:port`; sealed `Auth` { `Vault(UUID credentialId)`, `Agent` } with `Auth.AGENT`; `HostFile.parse(String) -> Parsed(List<RemoteHost> hosts, List<String> warnings)` (throws `IOException` on TOML errors), `HostFile.format(List<RemoteHost>) -> String`, `HostFile.HEADER`, `HostFile.validate(List<RemoteHost>)` (duplicate names, dangling or cyclic jumps → `IllegalArgumentException`); `HostStore(Path file, Executor background, Executor ui)` with `hosts()`, `Optional<RemoteHost> host(UUID)`, `Optional<String> error()`, `Subscription onChanged(Runnable)`, `void load()`, `void poll()`, `CompletableFuture<Void> save(List<RemoteHost>)`, `CompletableFuture<Void> put(RemoteHost)`, `CompletableFuture<Void> remove(UUID)`; `RemoteSettings.read(PluginConfig) -> RemoteSettings(Duration connectTimeout, Duration authTimeout, Duration keepalive, Duration linger, boolean readUserKnownHosts, boolean useAgent)`.

- [x] **Step 1: Create the module files**

`plugins/remote/build.gradle.kts`:

```kotlin
// A plugin compiles against the SDK and the exported API of plugins it requires; the application
// supplies both at run time. Bundled: Apache MINA sshd (with slf4j routed to JUL), BouncyCastle so MINA
// reads every OpenSSH key format the Vault produces, and tomlj for hosts.toml. stagePlugins copies the
// runtime classpath beside the plugin jar, and PluginClassLoader loads it.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    compileOnly(project(":jasper-plugin-vault"))
    implementation("org.apache.sshd:sshd-core:2.19.0")
    implementation("org.slf4j:slf4j-jdk14:2.0.13")
    implementation("org.bouncycastle:bcprov-jdk18on:1.85.2")
    implementation("org.tomlj:tomlj:1.1.1")
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
    testImplementation(project(":jasper-plugin-vault"))
}
```

`plugins/remote/src/main/resources/plugin.toml`:

```toml
id = "dev.jasper.remote"
name = "Remote"
version = "0.1.0"
entry = "dev.jasper.remote.RemotePlugin"
sdk = ">=0.7, <0.8"
description = "SSH hosts: saved hosts with groups and favorites, Vault or SSH-agent authentication, ProxyJump, host-key trust, a hosts panel and a palette scope."
vendor = "Jasper"
capabilities = ["terminal.open", "session.provide", "terminal.observe", "palette.contribute"]
requires = [{ id = "dev.jasper.vault", version = ">=0.1", optional = true }]
```

`plugins/remote/src/main/resources/settings.toml`:

```toml
# Settings for Remote. Jasper reads this file live; changes apply to the next connection.
# connect_timeout_seconds = 10
# auth_timeout_seconds = 30
# keepalive_seconds = 30          # server-alive interval; 0 disables
# session_linger_seconds = 5      # how long an idle shared session stays open after its last channel
# read_user_known_hosts = true    # also match host keys against ~/.ssh/known_hosts (never written)
# use_ssh_agent = true            # offer identities from SSH_AUTH_SOCK / the OpenSSH named pipe
```

`package-info.java` (root):

```java
/** The Remote plugin: {@link dev.jasper.remote.RemotePlugin} wires hosts, trust, the agent and the MINA client to the SDK. */
package dev.jasper.remote;
```

`hosts/package-info.java`:

```java
/** Saved hosts: the model, the hosts.toml file and the store that polls it. No secrets, no network. */
package dev.jasper.remote.hosts;
```

- [x] **Step 2: Write the failing tests**

`RemoteSettingsTest.java`:

```java
package dev.jasper.remote;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RemoteSettingsTest {
    static final PluginInfo INFO = new PluginInfo("dev.jasper.remote", "Remote", "0.1.0", Set.of());

    @Test void defaultsAndOverrides() {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), c -> { });
            RemoteSettings settings = RemoteSettings.read(context.config());
            assertThat(settings).isEqualTo(new RemoteSettings(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofSeconds(5), true, true));
        }
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.remote", Map.of("connect_timeout_seconds", 3L, "keepalive_seconds", 0L, "session_linger_seconds", -1L, "read_user_known_hosts", false, "use_ssh_agent", false));
            var context = host.start(INFO, Set.of(), Set.of(), c -> { });
            RemoteSettings settings = RemoteSettings.read(context.config());
            assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(settings.keepalive()).isEqualTo(Duration.ZERO);
            assertThat(settings.linger()).as("negative clamps to zero").isEqualTo(Duration.ZERO);
            assertThat(settings.readUserKnownHosts()).isFalse();
            assertThat(settings.useAgent()).isFalse();
        }
    }
}
```

`hosts/HostFileTest.java`:

```java
package dev.jasper.remote.hosts;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HostFileTest {
    static final UUID BASTION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID CRED = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    static RemoteHost bastion() {
        return new RemoteHost(BASTION, "bastion", "bastion.example", 22, "ops", Auth.AGENT, "Homelab", false, Optional.empty(), Instant.ofEpochSecond(1), Instant.ofEpochSecond(2));
    }

    static RemoteHost prod() {
        return new RemoteHost(UUID.fromString("00000000-0000-0000-0000-000000000002"), "api \"prod\"", "10.0.0.5", 2222, "", new Auth.Vault(CRED), "", true, Optional.of(BASTION), Instant.ofEpochSecond(3), Instant.ofEpochSecond(4));
    }

    @Test void roundTripsEveryField() throws IOException {
        String text = HostFile.format(List.of(bastion(), prod()));
        assertThat(text).startsWith(HostFile.HEADER).contains("[[host]]", "name = \"api \\\"prod\\\"\"", "auth = \"vault\"", "credential = \"" + CRED + "\"", "jump = \"" + BASTION + "\"", "favorite = true", "port = 2222", "created = 1970-01-01T00:00:03Z");
        assertThat(text).as("agent hosts carry no credential key").doesNotContain("credential = \"\"");
        HostFile.Parsed parsed = HostFile.parse(text);
        assertThat(parsed.warnings()).isEmpty();
        assertThat(parsed.hosts()).containsExactly(bastion(), prod());
    }

    @Test void skipsBadEntriesAndReportsThem() throws IOException {
        String text = HostFile.HEADER + "\n[[host]]\nname = \"a\"\nhostname = \"a.example\"\nauth = \"agent\"\nusername = \"u\"\n\n[[host]]\nname = \"b\"\nport = 70000\nauth = \"agent\"\nusername = \"u\"\nhostname = \"b\"\n\n[[host]]\nname = \"a\"\nhostname = \"dup\"\nauth = \"agent\"\nusername = \"u\"\n\n[[host]]\nname = \"c\"\nhostname = \"c\"\nauth = \"vault\"\nusername = \"\"\n";
        HostFile.Parsed parsed = HostFile.parse(text);
        assertThat(parsed.hosts()).singleElement().satisfies(host -> {
            assertThat(host.name()).isEqualTo("a");
            assertThat(host.port()).as("default port").isEqualTo(22);
            assertThat(host.id()).as("a missing id is minted").isNotNull();
            assertThat(host.created()).isNotNull();
        });
        assertThat(parsed.warnings()).hasSize(3)
            .anySatisfy(w -> assertThat(w).contains("host 2", "port"))
            .anySatisfy(w -> assertThat(w).contains("host 3", "duplicate name"))
            .anySatisfy(w -> assertThat(w).contains("host 4", "credential"));
        assertThatThrownBy(() -> HostFile.parse("[[host]\nname = ")).isInstanceOf(IOException.class).hasMessageContaining("TOML");
        assertThat(HostFile.parse("").hosts()).isEmpty();
    }

    @Test void validateRejectsCyclesDanglingJumpsAndDuplicateNames() {
        RemoteHost a = new RemoteHost(UUID.randomUUID(), "a", "a", 22, "u", Auth.AGENT, "", false, Optional.empty(), Instant.EPOCH, Instant.EPOCH);
        RemoteHost b = new RemoteHost(UUID.randomUUID(), "b", "b", 22, "u", Auth.AGENT, "", false, Optional.of(a.id()), Instant.EPOCH, Instant.EPOCH);
        HostFile.validate(List.of(a, b));
        RemoteHost aViaB = a.withEdited("a", "a", 22, "u", Auth.AGENT, "", Optional.of(b.id()));
        assertThatThrownBy(() -> HostFile.validate(List.of(aViaB, b))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cycle");
        RemoteHost dangling = b.withEdited("b", "b", 22, "u", Auth.AGENT, "", Optional.of(UUID.randomUUID()));
        assertThatThrownBy(() -> HostFile.validate(List.of(a, dangling))).hasMessageContaining("jump host missing");
        RemoteHost sameName = b.withEdited("A", "b", 22, "u", Auth.AGENT, "", Optional.empty());
        assertThatThrownBy(() -> HostFile.validate(List.of(a, sameName))).hasMessageContaining("A host named");
        assertThatThrownBy(() -> RemoteHost.create("x", "x", 0, "u", Auth.AGENT, "", Optional.empty())).hasMessageContaining("port");
        assertThatThrownBy(() -> RemoteHost.create("x", "x", 22, "", Auth.AGENT, "", Optional.empty())).hasMessageContaining("username");
        assertThat(RemoteHost.create("x", "x", 22, "", new Auth.Vault(UUID.randomUUID()), "", Optional.empty()).label()).isEqualTo("x:22");
        assertThat(a.label()).isEqualTo("u@a:22");
    }
}
```

`hosts/HostStoreTest.java`:

```java
package dev.jasper.remote.hosts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class HostStoreTest {
    final Deque<Runnable> background = new ArrayDeque<>();
    final Executor queued = background::add;
    int changes;

    void run() { while (!background.isEmpty()) background.poll().run(); }

    HostStore store(Path dir) {
        var store = new HostStore(dir.resolve("hosts.toml"), queued, Runnable::run);
        store.onChanged(() -> changes++);
        return store;
    }

    @Test void loadsSavesAndNoticesOutsideEdits(@TempDir Path dir) throws Exception {
        HostStore store = store(dir);
        store.load();
        run();
        assertThat(store.hosts()).isEmpty();
        assertThat(store.error()).isEmpty();
        RemoteHost a = RemoteHost.create("a", "a.example", 22, "u", Auth.AGENT, "G", Optional.empty());
        CompletableFuture<Void> saved = store.put(a);
        run();
        assertThat(saved).isCompleted();
        assertThat(store.hosts()).containsExactly(a);
        assertThat(changes).isEqualTo(2);
        assertThat(Files.readString(dir.resolve("hosts.toml"))).contains("name = \"a\"");
        try (var listing = Files.list(dir)) { assertThat(listing).as("no temp file").containsExactly(dir.resolve("hosts.toml")); }
        RemoteHost edited = a.withEdited("a", "b.example", 22, "u", Auth.AGENT, "G", Optional.empty());
        store.put(edited);
        run();
        assertThat(store.host(a.id())).map(RemoteHost::hostname).contains("b.example");
        // An outside edit: the poll sees a new size or time and re-reads.
        Files.writeString(dir.resolve("hosts.toml"), HostFile.format(List.of(a, RemoteHost.create("z", "z", 22, "u", Auth.AGENT, "", Optional.empty()))));
        Files.setLastModifiedTime(dir.resolve("hosts.toml"), FileTime.from(Instant.now().plusSeconds(5)));
        store.poll();
        run();
        assertThat(store.hosts()).extracting(RemoteHost::name).containsExactly("a", "z");
        store.remove(a.id());
        run();
        assertThat(store.hosts()).extracting(RemoteHost::name).containsExactly("z");
    }

    @Test void aBrokenFileKeepsTheLastGoodHostsAndRefusesSaves(@TempDir Path dir) throws Exception {
        HostStore store = store(dir);
        RemoteHost a = RemoteHost.create("a", "a", 22, "u", Auth.AGENT, "", Optional.empty());
        store.put(a);
        run();
        Files.writeString(dir.resolve("hosts.toml"), "[[host]\nbroken");
        Files.setLastModifiedTime(dir.resolve("hosts.toml"), FileTime.from(Instant.now().plusSeconds(5)));
        store.poll();
        run();
        assertThat(store.hosts()).containsExactly(a);
        assertThat(store.error()).isPresent().get().asString().contains("TOML");
        CompletableFuture<Void> refused = store.put(RemoteHost.create("b", "b", 22, "u", Auth.AGENT, "", Optional.empty()));
        run();
        assertThatThrownBy(refused::join).isInstanceOf(CompletionException.class).hasMessageContaining("hosts.toml has errors");
        assertThat(store.hosts()).containsExactly(a);
        Files.writeString(dir.resolve("hosts.toml"), HostFile.format(List.of(a)));
        Files.setLastModifiedTime(dir.resolve("hosts.toml"), FileTime.from(Instant.now().plusSeconds(10)));
        store.poll();
        run();
        assertThat(store.error()).isEmpty();
    }

    @Test void saveValidatesBeforeWriting(@TempDir Path dir) {
        HostStore store = store(dir);
        RemoteHost a = RemoteHost.create("a", "a", 22, "u", Auth.AGENT, "", Optional.empty());
        RemoteHost b = RemoteHost.create("b", "b", 22, "u", Auth.AGENT, "", Optional.of(UUID.randomUUID()));
        CompletableFuture<Void> refused = store.save(List.of(a, b));
        run();
        assertThatThrownBy(refused::join).hasMessageContaining("jump host missing");
        assertThat(dir.resolve("hosts.toml")).doesNotExist();
    }
}
```

- [x] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test -q`
Expected: compilation failure (the module is discovered by `settings.gradle.kts`; the types are missing).

- [x] **Step 4: Implement**

`RemoteSettings.java`:

```java
package dev.jasper.remote;

import dev.jasper.sdk.plugin.PluginConfig;
import java.time.Duration;

/** The six keys of {@code dev.jasper.remote.toml}, with the spec's defaults; negative durations clamp to zero. */
public record RemoteSettings(Duration connectTimeout, Duration authTimeout, Duration keepalive, Duration linger, boolean readUserKnownHosts, boolean useAgent) {
    public static RemoteSettings read(PluginConfig config) {
        return new RemoteSettings(seconds(config, "connect_timeout_seconds", 10), seconds(config, "auth_timeout_seconds", 30),
            seconds(config, "keepalive_seconds", 30), seconds(config, "session_linger_seconds", 5),
            config.bool("read_user_known_hosts").orElse(true), config.bool("use_ssh_agent").orElse(true));
    }

    private static Duration seconds(PluginConfig config, String key, long fallback) {
        return Duration.ofSeconds(Math.max(0, config.integer(key).orElse(fallback)));
    }
}
```

`hosts/Auth.java`:

```java
package dev.jasper.remote.hosts;

import java.util.Objects;
import java.util.UUID;

/** How a host authenticates: a Vault credential by id, or whatever the SSH agent holds. */
public sealed interface Auth {
    Agent AGENT = new Agent();

    record Vault(UUID credentialId) implements Auth {
        public Vault { Objects.requireNonNull(credentialId, "credentialId"); }
    }

    record Agent() implements Auth { }
}
```

`hosts/RemoteHost.java`:

```java
package dev.jasper.remote.hosts;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** A saved host: a reference to its credential, never the secret itself. */
public record RemoteHost(UUID id, String name, String hostname, int port, String username, Auth auth, String group, boolean favorite,
                         Optional<UUID> jump, Instant created, Instant updated) {
    public RemoteHost {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(auth, "auth"); Objects.requireNonNull(jump, "jump");
        Objects.requireNonNull(created, "created"); Objects.requireNonNull(updated, "updated");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A host needs a name");
        if (hostname == null || hostname.isBlank()) throw new IllegalArgumentException("A host needs a hostname");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("The port must be 1 to 65535");
        username = username == null ? "" : username.strip();
        if (username.isEmpty() && auth instanceof Auth.Agent) throw new IllegalArgumentException("An SSH agent host needs a username");
        group = group == null ? "" : group.strip();
        name = name.strip(); hostname = hostname.strip();
        if (jump.isPresent() && jump.get().equals(id)) throw new IllegalArgumentException("A host cannot jump through itself");
    }

    public static RemoteHost create(String name, String hostname, int port, String username, Auth auth, String group, Optional<UUID> jump) {
        Instant now = Instant.now();
        return new RemoteHost(UUID.randomUUID(), name, hostname, port, username, auth, group, false, jump, now, now);
    }

    public RemoteHost withEdited(String name, String hostname, int port, String username, Auth auth, String group, Optional<UUID> jump) {
        return new RemoteHost(id, name, hostname, port, username, auth, group, favorite, jump, created, Instant.now());
    }

    public RemoteHost withFavorite(boolean value) { return new RemoteHost(id, name, hostname, port, username, auth, group, value, jump, created, updated); }

    /** {@code user@host:port}, or {@code host:port} when the username comes from the Vault login. */
    public String label() { return (username.isEmpty() ? "" : username + "@") + hostname + ":" + port; }
}
```

`hosts/HostFile.java`:

```java
package dev.jasper.remote.hosts;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** The hosts.toml format: an array of [[host]] tables. Lenient per entry, strict per file. */
public final class HostFile {
    public static final String HEADER = "# Jasper Remote hosts. Edit freely; Jasper rewrites this file when you save from the panel.\n";
    public static final int MAX_BYTES = 4 * 1024 * 1024;

    public record Parsed(List<RemoteHost> hosts, List<String> warnings) { }

    private HostFile() { }

    /** Throws when the text is not valid TOML; skips and reports individual bad entries. */
    public static Parsed parse(String text) throws IOException {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors()) throw new IOException("hosts.toml has TOML errors: " + toml.errors().getFirst().toString());
        Object value = toml.get("host");
        if (value == null) return new Parsed(List.of(), List.of());
        if (!(value instanceof TomlArray array)) return new Parsed(List.of(), List.of("'host' must be an array of tables"));
        var hosts = new ArrayList<RemoteHost>();
        var warnings = new ArrayList<String>();
        var names = new HashSet<String>();
        var ids = new HashSet<UUID>();
        for (int i = 0; i < array.size(); i++) {
            String where = "host " + (i + 1) + ": ";
            if (!(array.get(i) instanceof TomlTable table)) { warnings.add(where + "not a table"); continue; }
            try {
                RemoteHost host = host(table);
                if (!names.add(host.name().toLowerCase(Locale.ROOT))) { warnings.add(where + "duplicate name " + host.name()); continue; }
                if (!ids.add(host.id())) { warnings.add(where + "duplicate id " + host.id()); continue; }
                hosts.add(host);
            } catch (IllegalArgumentException invalid) {
                warnings.add(where + invalid.getMessage());
            }
        }
        return new Parsed(List.copyOf(hosts), List.copyOf(warnings));
    }

    private static RemoteHost host(TomlTable table) {
        UUID id = optional(table, "id").map(HostFile::uuid).orElseGet(UUID::randomUUID);
        String auth = optional(table, "auth").orElse("agent");
        Auth resolved = switch (auth) {
            case "agent" -> Auth.AGENT;
            case "vault" -> new Auth.Vault(uuid(optional(table, "credential").orElseThrow(() -> new IllegalArgumentException("'credential' is required for auth = \"vault\""))));
            default -> throw new IllegalArgumentException("'auth' must be \"vault\" or \"agent\"");
        };
        long port = table.get("port") instanceof Long value ? value : 22;
        if (port < 1 || port > 65535) throw new IllegalArgumentException("'port' must be 1 to 65535");
        Instant created = instant(table, "created").orElseGet(Instant::now);
        return new RemoteHost(id, optional(table, "name").orElse(""), optional(table, "hostname").orElse(""), (int) port, optional(table, "username").orElse(""),
            resolved, optional(table, "group").orElse(""), table.get("favorite") instanceof Boolean favorite && favorite,
            optional(table, "jump").map(HostFile::uuid), created, instant(table, "updated").orElse(created));
    }

    private static Optional<String> optional(TomlTable table, String key) {
        Object value = table.get(key);
        if (value == null) return Optional.empty();
        if (!(value instanceof String text)) throw new IllegalArgumentException("'" + key + "' must be a string");
        return Optional.of(text);
    }

    private static Optional<Instant> instant(TomlTable table, String key) {
        Object value = table.get(key);
        if (value == null) return Optional.empty();
        if (value instanceof OffsetDateTime time) return Optional.of(time.toInstant());
        if (value instanceof String text) { try { return Optional.of(Instant.parse(text)); } catch (RuntimeException bad) { throw new IllegalArgumentException("'" + key + "' is not a timestamp"); } }
        throw new IllegalArgumentException("'" + key + "' is not a timestamp");
    }

    private static UUID uuid(String text) {
        try { return UUID.fromString(text); } catch (IllegalArgumentException bad) { throw new IllegalArgumentException("'" + text + "' is not an id"); }
    }

    /** Rejects duplicate names (case-insensitive), dangling jump references and jump cycles. */
    public static void validate(List<RemoteHost> hosts) {
        Map<UUID, RemoteHost> byId = new HashMap<>();
        Set<String> names = new HashSet<>();
        for (RemoteHost host : hosts) {
            if (!names.add(host.name().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("A host named " + host.name() + " exists");
            byId.put(host.id(), host);
        }
        for (RemoteHost host : hosts) {
            Set<UUID> seen = new HashSet<>();
            RemoteHost current = host;
            while (current.jump().isPresent()) {
                if (!seen.add(current.id())) throw new IllegalArgumentException("Jump hosts form a cycle at " + host.name());
                current = byId.get(current.jump().get());
                if (current == null) throw new IllegalArgumentException("jump host missing for " + host.name());
            }
            if (current != host && seen.contains(current.id())) throw new IllegalArgumentException("Jump hosts form a cycle at " + host.name());
        }
    }

    public static String format(List<RemoteHost> hosts) {
        var out = new StringBuilder(HEADER);
        for (RemoteHost host : hosts) {
            out.append("\n[[host]]\n");
            out.append("id = \"").append(host.id()).append("\"\n");
            out.append("name = ").append(tomlString(host.name())).append('\n');
            out.append("hostname = ").append(tomlString(host.hostname())).append('\n');
            out.append("port = ").append(host.port()).append('\n');
            out.append("username = ").append(tomlString(host.username())).append('\n');
            switch (host.auth()) {
                case Auth.Agent agent -> out.append("auth = \"agent\"\n");
                case Auth.Vault vault -> out.append("auth = \"vault\"\ncredential = \"").append(vault.credentialId()).append("\"\n");
            }
            if (!host.group().isEmpty()) out.append("group = ").append(tomlString(host.group())).append('\n');
            out.append("favorite = ").append(host.favorite()).append('\n');
            host.jump().ifPresent(jump -> out.append("jump = \"").append(jump).append("\"\n"));
            out.append("created = ").append(DateTimeFormatter.ISO_INSTANT.format(host.created())).append('\n');
            out.append("updated = ").append(DateTimeFormatter.ISO_INSTANT.format(host.updated())).append('\n');
        }
        return out.toString();
    }

    /** A TOML basic string: backslash, quote, tab, newlines and other control characters escaped. */
    static String tomlString(String text) {
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
}
```

Note on `validate`'s cycle check: the loop follows `jump` links from each host, collecting ids; revisiting an id means a cycle. Simplify to this exact form (replace the two `throw ... cycle` lines with one walk):

```java
        for (RemoteHost host : hosts) {
            Set<UUID> seen = new HashSet<>();
            RemoteHost current = host;
            while (current.jump().isPresent()) {
                if (!seen.add(current.id())) throw new IllegalArgumentException("Jump hosts form a cycle at " + host.name());
                RemoteHost next = byId.get(current.jump().get());
                if (next == null) throw new IllegalArgumentException("jump host missing for " + host.name());
                if (next.id().equals(host.id())) throw new IllegalArgumentException("Jump hosts form a cycle at " + host.name());
                current = next;
            }
        }
```

`hosts/HostStore.java`:

```java
package dev.jasper.remote.hosts;

import dev.jasper.sdk.Subscription;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The saved hosts, read from and written to hosts.toml. Reads and writes run on {@code background};
 * the list, the error and the listeners live on the UI thread. A file that fails to parse keeps the
 * last good hosts and refuses saves until it parses again.
 */
public final class HostStore {
    private final Path file;
    private final Executor background, ui;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private List<RemoteHost> hosts = List.of();
    private String error;
    private long size = -1;
    private FileTime modified;
    private CompletableFuture<Void> queue = CompletableFuture.completedFuture(null);

    private record Read(List<RemoteHost> hosts, List<String> warnings, String error, long size, FileTime modified) { }

    public HostStore(Path file, Executor background, Executor ui) { this.file = file; this.background = background; this.ui = ui; }

    public Path file() { return file; }
    public List<RemoteHost> hosts() { return hosts; }
    public Optional<RemoteHost> host(UUID id) { return hosts.stream().filter(host -> host.id().equals(id)).findFirst(); }
    /** The last parse failure, present while the file is broken. */
    public Optional<String> error() { return Optional.ofNullable(error); }
    public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }

    /** Reads the file unconditionally. */
    public void load() { enqueue(() -> onBackground(() -> read(true)).thenAccept(this::apply)); }

    /** Reads the file when its size or modification time changed; the plugin calls this once a second. */
    public void poll() { enqueue(() -> onBackground(() -> read(false)).thenAccept(read -> { if (read != null) apply(read); })); }

    public CompletableFuture<Void> put(RemoteHost host) {
        var next = new ArrayList<RemoteHost>();
        boolean replaced = false;
        for (RemoteHost existing : hosts) { if (existing.id().equals(host.id())) { next.add(host); replaced = true; } else next.add(existing); }
        if (!replaced) next.add(host);
        return save(next);
    }

    public CompletableFuture<Void> remove(UUID id) { return save(hosts.stream().filter(host -> !host.id().equals(id)).toList()); }

    /** Validates, then writes the whole list (temp file and rename) and re-reads; refused while the file is broken. */
    public CompletableFuture<Void> save(List<RemoteHost> next) {
        List<RemoteHost> copy = List.copyOf(next);
        var result = new CompletableFuture<Void>();
        enqueue(() -> {
            try { HostFile.validate(copy); } catch (IllegalArgumentException invalid) { result.completeExceptionally(invalid); return CompletableFuture.completedFuture(null); }
            if (error != null) { result.completeExceptionally(new IOException("hosts.toml has errors; fix it before saving: " + error)); return CompletableFuture.completedFuture(null); }
            return onBackground(() -> { write(HostFile.format(copy)); return read(true); })
                .whenComplete((read, failure) -> { if (failure == null) { apply(read); result.complete(null); } else result.completeExceptionally(failure); });
        });
        return result;
    }

    private void apply(Read read) {
        size = read.size(); modified = read.modified();
        if (read.error() == null) { hosts = read.hosts(); error = null; }
        else error = read.error();
        listeners.forEach(Runnable::run);
    }

    // Worker side. Returns null when nothing changed and the read was conditional.
    private Read read(boolean force) throws IOException {
        if (!Files.isRegularFile(file)) return force || size != -1 ? new Read(List.of(), List.of(), null, -1, null) : null;
        long currentSize = Files.size(file);
        FileTime currentModified = Files.getLastModifiedTime(file);
        if (!force && currentSize == size && currentModified.equals(modified)) return null;
        if (currentSize > HostFile.MAX_BYTES) return new Read(List.of(), List.of(), "hosts.toml is larger than " + HostFile.MAX_BYTES + " bytes", currentSize, currentModified);
        String text = Files.readString(file, StandardCharsets.UTF_8);
        try {
            HostFile.Parsed parsed = HostFile.parse(text);
            return new Read(parsed.hosts(), parsed.warnings(), null, currentSize, currentModified);
        } catch (IOException bad) {
            return new Read(List.of(), List.of(), bad.getMessage(), currentSize, currentModified);
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

    private <T> CompletableFuture<T> onBackground(java.util.concurrent.Callable<T> work) {
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

`load()`/`poll()` use `thenAccept`, which does not return `CompletableFuture<Void>` of the right shape for `enqueue` — write both as `enqueue(() -> onBackground(...).thenAccept(...))` exactly as shown; `thenAccept` returns `CompletableFuture<Void>`, which is what `enqueue` takes. The `warnings` of a read are logged by the plugin (Task 7) through the store's `warnings()` accessor: add `private List<String> warnings = List.of();` set in `apply` and `public List<String> warnings()`.

- [x] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test -q`
Expected: PASS. If `HostStoreTest.loadsSavesAndNoticesOutsideEdits` sees `changes` other than 2 after the first `put`, count listener calls: `load` (1) and the save's re-read (1); adjust only if the implementation differs, not the test's intent.

- [x] **Step 6: Commit**

```bash
git add plugins/remote
git commit -m "feat(remote): start the Remote plugin with settings, the host model and the hosts.toml store"
```

---

### Task 2: `~/.ssh/config` parsing and the import mapping

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/hosts/SshConfig.java`, `ConfigImport.java`, `Fingerprints.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/hosts/SshConfigTest.java`, `ConfigImportTest.java`

**Interfaces:**
- Consumes: `RemoteHost`, `Auth`.
- Produces: `SshConfig.Entry(String alias, Optional<String> hostname, OptionalInt port, Optional<String> user, Optional<String> proxyJump, Optional<String> identityFile)`; `SshConfig.Parsed(List<Entry> entries, List<String> skipped)`; `SshConfig.parse(Path config)` (one level of `Include`, globbed relative to the config's directory) and `SshConfig.parse(String text, Function<String, List<String>> includes)`; `Fingerprints.sha256(byte[] blob) -> "SHA256:…"`, `Fingerprints.ofPublicKeyLine(String line) -> Optional<String>`; `ConfigImport.Candidate(SshConfig.Entry entry, RemoteHost host, boolean exists, List<String> notes)`; `ConfigImport.plan(SshConfig.Parsed, List<RemoteHost> existing, Map<String, UUID> vaultKeysByFingerprint, Function<Path, Optional<String>> publicKeyLine, Path home, String localUser) -> List<Candidate>`.

- [x] **Step 1: Write the failing tests**

`SshConfigTest.java`:

```java
package dev.jasper.remote.hosts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SshConfigTest {
    static final String CONFIG = """
        # comment
        User global
        Host prod
            HostName api.prod.example
            Port 2222
            User deploy
            User ignored
            IdentityFile ~/.ssh/id_ed25519
            ProxyJump bastion
        Host bastion staging
          HostName=bastion.example
        Host *.internal
            User ops
        Match host db
            User dba
        Host *
            Port 22
        """;

    @Test void readsEntriesWithFirstValueWinsAndGlobalDefaults() {
        SshConfig.Parsed parsed = SshConfig.parse(CONFIG, include -> List.of());
        assertThat(parsed.entries()).extracting(SshConfig.Entry::alias).containsExactly("prod", "bastion", "staging");
        SshConfig.Entry prod = parsed.entries().getFirst();
        assertThat(prod.hostname()).contains("api.prod.example");
        assertThat(prod.port()).isEqualTo(OptionalInt.of(2222));
        assertThat(prod.user()).as("first value wins").contains("deploy");
        assertThat(prod.identityFile()).contains("~/.ssh/id_ed25519");
        assertThat(prod.proxyJump()).contains("bastion");
        SshConfig.Entry bastion = parsed.entries().get(1);
        assertThat(bastion.hostname()).contains("bastion.example");
        assertThat(bastion.user()).as("global default").contains("global");
        assertThat(bastion.port()).as("Host * default").isEqualTo(OptionalInt.of(22));
        assertThat(parsed.skipped()).containsExactly("Host *.internal (pattern)", "Match host db (Match block)");
    }

    @Test void includesAreExpandedOnce() {
        SshConfig.Parsed parsed = SshConfig.parse("Include extra/*\nHost a\n HostName a.example\n", include -> include.equals("extra/*") ? List.of("Host b\n HostName b.example\n") : List.of());
        assertThat(parsed.entries()).extracting(SshConfig.Entry::alias).containsExactly("b", "a");
    }

    @Test void readsFromDiskWithGlobbedIncludes(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("conf.d"));
        Files.writeString(dir.resolve("conf.d/one"), "Host one\n HostName one.example\n");
        Files.writeString(dir.resolve("config"), "Include conf.d/*\nHost two\n");
        SshConfig.Parsed parsed = SshConfig.parse(dir.resolve("config"));
        assertThat(parsed.entries()).extracting(SshConfig.Entry::alias).containsExactly("one", "two");
        assertThat(SshConfig.parse(dir.resolve("missing")).entries()).isEmpty();
    }
}
```

`ConfigImportTest.java`:

```java
package dev.jasper.remote.hosts;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConfigImportTest {
    static final String PUB = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGb3mJ8dR0JZQm3W3n9oQjzN1tYw0N3HkL5wS2hXlY1X me@laptop";
    static final UUID KEY = UUID.randomUUID();

    @Test void mapsEntriesToHostsWithVaultKeysAgentFallbackAndJumps() {
        String fingerprint = Fingerprints.ofPublicKeyLine(PUB).orElseThrow();
        var parsed = SshConfig.parse("""
            Host prod
              HostName api.example
              User deploy
              IdentityFile ~/.ssh/id_ed25519
              ProxyJump bastion
            Host bastion
              HostName bastion.example
              IdentityFile ~/.ssh/other
            Host lonely
              ProxyJump nowhere
            """, include -> List.of());
        RemoteHost existing = RemoteHost.create("Bastion", "old.example", 22, "u", Auth.AGENT, "", Optional.empty());
        List<ConfigImport.Candidate> plan = ConfigImport.plan(parsed, List.of(existing), Map.of(fingerprint, KEY),
            path -> path.equals(Path.of("/home/me/.ssh/id_ed25519.pub")) ? Optional.of(PUB) : Optional.empty(), Path.of("/home/me"), "me");
        assertThat(plan).hasSize(3);
        ConfigImport.Candidate prod = plan.get(0), bastion = plan.get(1), lonely = plan.get(2);
        assertThat(prod.host().name()).isEqualTo("prod");
        assertThat(prod.host().hostname()).isEqualTo("api.example");
        assertThat(prod.host().username()).isEqualTo("deploy");
        assertThat(prod.host().auth()).isEqualTo(new Auth.Vault(KEY));
        assertThat(prod.host().jump()).as("jumps resolve to the existing host of that name").contains(existing.id());
        assertThat(prod.exists()).isFalse();
        assertThat(prod.notes()).isEmpty();
        assertThat(bastion.exists()).as("name matches an existing host, case-insensitively").isTrue();
        assertThat(bastion.host().auth()).isEqualTo(Auth.AGENT);
        assertThat(bastion.host().username()).as("no User: the local user").isEqualTo("me");
        assertThat(bastion.notes()).containsExactly("key not in the vault: uses the agent", "no User: uses your local username");
        assertThat(lonely.host().hostname()).as("no HostName: the alias").isEqualTo("lonely");
        assertThat(lonely.host().jump()).isEmpty();
        assertThat(lonely.notes()).contains("ProxyJump nowhere dropped: no such host");
    }

    @Test void fingerprintsMatchOpenSsh() {
        assertThat(Fingerprints.ofPublicKeyLine(PUB)).contains("SHA256:" + Fingerprints.sha256(java.util.Base64.getDecoder().decode(PUB.split(" ")[1])).substring(7));
        assertThat(Fingerprints.ofPublicKeyLine("not a key")).isEmpty();
        assertThat(Fingerprints.sha256(new byte[] {1, 2, 3})).startsWith("SHA256:").doesNotEndWith("=");
    }
}
```

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test -q`
Expected: compilation failure.

- [x] **Step 3: Implement**

`hosts/Fingerprints.java`:

```java
package dev.jasper.remote.hosts;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

/** OpenSSH-style SHA256 fingerprints of public-key blobs; the same form the Vault shows for its keys. */
public final class Fingerprints {
    private Fingerprints() { }

    public static String sha256(byte[] blob) {
        try { return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /** The fingerprint of a {@code type base64 [comment]} line; empty when the line is not one. */
    public static Optional<String> ofPublicKeyLine(String line) {
        String[] parts = line.strip().split("\\s+");
        if (parts.length < 2 || !parts[0].startsWith("ssh-") && !parts[0].startsWith("ecdsa-") && !parts[0].startsWith("sk-")) return Optional.empty();
        try { return Optional.of(sha256(Base64.getDecoder().decode(parts[1]))); }
        catch (IllegalArgumentException bad) { return Optional.empty(); }
    }
}
```

`hosts/SshConfig.java`:

```java
package dev.jasper.remote.hosts;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A small reader of OpenSSH client configuration: Host blocks with HostName, Port, User, ProxyJump and
 * IdentityFile; the first value wins per key, global lines and {@code Host *} supply defaults; one level of
 * Include. Wildcard Host patterns and Match blocks are skipped and listed.
 */
public final class SshConfig {
    public record Entry(String alias, Optional<String> hostname, OptionalInt port, Optional<String> user, Optional<String> proxyJump, Optional<String> identityFile) { }
    public record Parsed(List<Entry> entries, List<String> skipped) { }

    private SshConfig() { }

    /** Reads {@code config}; a missing file is empty. Includes resolve relative to the file's directory. */
    public static Parsed parse(Path config) throws IOException {
        if (!Files.isRegularFile(config)) return new Parsed(List.of(), List.of());
        Path base = config.toAbsolutePath().getParent();
        return parse(Files.readString(config), include -> {
            Path pattern = Path.of(include);
            Path root = pattern.isAbsolute() ? pattern.getParent() : base.resolve(pattern).getParent();
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + (pattern.isAbsolute() ? pattern : base.resolve(pattern)));
            var texts = new ArrayList<String>();
            if (root == null || !Files.isDirectory(root)) return texts;
            try (Stream<Path> files = Files.list(root)) {
                for (Path file : files.sorted().toList())
                    if (matcher.matches(file) && Files.isRegularFile(file)) texts.add(Files.readString(file));
            } catch (IOException unreadable) { return texts; }
            return texts;
        });
    }

    public static Parsed parse(String text, Function<String, List<String>> includes) {
        var blocks = new ArrayList<Map.Entry<List<String>, Map<String, String>>>();
        var skipped = new ArrayList<String>();
        var global = new LinkedHashMap<String, String>();
        Map<String, String> current = global;
        boolean skipping = false;
        for (String raw : expand(text, includes).split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] kv = line.split("[\\s=]+", 2);
            String key = kv[0].toLowerCase(Locale.ROOT), value = kv.length > 1 ? kv[1].strip() : "";
            if (key.equals("host")) {
                List<String> aliases = new ArrayList<>();
                for (String alias : value.split("\\s+")) {
                    if (alias.isEmpty()) continue;
                    if (alias.equals("*")) aliases.add(alias);
                    else if (alias.contains("*") || alias.contains("?") || alias.startsWith("!")) skipped.add("Host " + alias + " (pattern)");
                    else aliases.add(alias);
                }
                current = new LinkedHashMap<>();
                blocks.add(Map.entry(aliases, current));
                skipping = false;
            } else if (key.equals("match")) {
                skipped.add("Match " + value + " (Match block)");
                current = new LinkedHashMap<>();
                skipping = true;
            } else if (!skipping) current.putIfAbsent(key, value);
        }
        var entries = new ArrayList<Entry>();
        var wildcard = new LinkedHashMap<String, String>();
        for (var block : blocks) if (block.getKey().contains("*")) wildcard.putAll(block.getValue());
        for (var block : blocks) {
            for (String alias : block.getKey()) {
                if (alias.equals("*")) continue;
                Map<String, String> values = new LinkedHashMap<>(block.getValue());
                global.forEach(values::putIfAbsent);
                wildcard.forEach(values::putIfAbsent);
                OptionalInt port = OptionalInt.empty();
                if (values.containsKey("port")) { try { port = OptionalInt.of(Integer.parseInt(values.get("port"))); } catch (NumberFormatException bad) { skipped.add("Host " + alias + " (bad Port)"); continue; } }
                entries.add(new Entry(alias, Optional.ofNullable(values.get("hostname")), port, Optional.ofNullable(values.get("user")),
                    Optional.ofNullable(values.get("proxyjump")), Optional.ofNullable(values.get("identityfile"))));
            }
        }
        return new Parsed(List.copyOf(entries), List.copyOf(skipped));
    }

    private static String expand(String text, Function<String, List<String>> includes) {
        var out = new StringBuilder();
        for (String raw : text.split("\n")) {
            String line = raw.strip();
            if (line.toLowerCase(Locale.ROOT).startsWith("include ")) {
                for (String pattern : line.substring(8).strip().split("\\s+")) for (String included : includes.apply(pattern)) out.append(included).append('\n');
            } else out.append(raw).append('\n');
        }
        return out.toString();
    }
}
```

`hosts/ConfigImport.java`:

```java
package dev.jasper.remote.hosts;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Turns parsed config entries into hosts the user can tick in the import dialog. */
public final class ConfigImport {
    public record Candidate(SshConfig.Entry entry, RemoteHost host, boolean exists, List<String> notes) { }

    private ConfigImport() { }

    /**
     * @param vaultKeysByFingerprint the Vault's SSH_KEY descriptors, fingerprint (subtitle) to id
     * @param publicKeyLine          reads {@code <IdentityFile>.pub}; empty when absent
     */
    public static List<Candidate> plan(SshConfig.Parsed parsed, List<RemoteHost> existing, Map<String, UUID> vaultKeysByFingerprint,
                                       Function<Path, Optional<String>> publicKeyLine, Path home, String localUser) {
        Map<String, UUID> idsByName = new HashMap<>();
        for (RemoteHost host : existing) idsByName.put(host.name().toLowerCase(Locale.ROOT), host.id());
        record Draft(SshConfig.Entry entry, UUID id, String hostname, int port, String username, Auth auth, List<String> notes) { }
        var drafts = new ArrayList<Draft>();
        for (SshConfig.Entry entry : parsed.entries()) {
            var notes = new ArrayList<String>();
            Auth auth = Auth.AGENT;
            if (entry.identityFile().isPresent()) {
                Path key = expand(entry.identityFile().get(), home);
                Optional<String> fingerprint = publicKeyLine.apply(key.resolveSibling(key.getFileName() + ".pub")).flatMap(Fingerprints::ofPublicKeyLine);
                UUID vaultKey = fingerprint.map(vaultKeysByFingerprint::get).orElse(null);
                if (vaultKey != null) auth = new Auth.Vault(vaultKey);
                else notes.add("key not in the vault: uses the agent");
            }
            String username = entry.user().orElse("");
            if (username.isEmpty() && auth instanceof Auth.Agent) { username = localUser; notes.add("no User: uses your local username"); }
            UUID id = UUID.randomUUID();
            drafts.add(new Draft(entry, id, entry.hostname().orElse(entry.alias()), entry.port().orElse(22), username, auth, notes));
            idsByName.putIfAbsent(entry.alias().toLowerCase(Locale.ROOT), id);
        }
        var candidates = new ArrayList<Candidate>();
        for (Draft draft : drafts) {
            Optional<UUID> jump = Optional.empty();
            if (draft.entry().proxyJump().isPresent()) {
                String first = draft.entry().proxyJump().get().split(",")[0].strip();
                String name = first.contains("@") ? first.substring(first.indexOf('@') + 1) : first;
                UUID target = idsByName.get(name.toLowerCase(Locale.ROOT));
                if (target != null && !target.equals(draft.id())) jump = Optional.of(target);
                else draft.notes().add("ProxyJump " + first + " dropped: no such host");
            }
            boolean exists = existing.stream().anyMatch(host -> host.name().equalsIgnoreCase(draft.entry().alias()));
            var host = new RemoteHost(draft.id(), draft.entry().alias(), draft.hostname(), draft.port(), draft.username(), draft.auth(), "", false, jump,
                java.time.Instant.now(), java.time.Instant.now());
            candidates.add(new Candidate(draft.entry(), host, exists, List.copyOf(draft.notes())));
        }
        return List.copyOf(candidates);
    }

    static Path expand(String file, Path home) {
        if (file.equals("~")) return home;
        if (file.startsWith("~/")) return home.resolve(file.substring(2));
        return Path.of(file);
    }
}
```

Note for the executor: when an imported name matches an existing host, `idsByName.putIfAbsent` keeps the existing id, so a `ProxyJump` to that name resolves to the existing host (what `ConfigImportTest` asserts for `prod` → the existing `Bastion`).

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test -q`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add plugins/remote
git commit -m "feat(remote): read ~/.ssh/config and plan an import into hosts.toml"
```

---

### Task 3: Host-key trust

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/trust/package-info.java`, `KnownHosts.java`, `CorruptTrustFileException.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/trust/KnownHostsTest.java`

**Interfaces:**
- Produces: `KnownHosts(Path own, Optional<Path> user)`; sealed `KnownHosts.Verdict` { `Match()`, `Mismatch(String knownFingerprint)`, `Unknown()` }; `Verdict verify(String host, int port, PublicKey key)` (throws `CorruptTrustFileException` when the own file cannot be parsed); `void trust(String host, int port, PublicKey key) throws IOException` (re-checks and appends `host key` / `[host]:port key`); `static String fingerprint(PublicKey)`; `static String hostPattern(String host, int port)`.

- [x] **Step 1: Write the failing test**

```java
package dev.jasper.remote.trust;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class KnownHostsTest {
    static PublicKey key(String algorithm, int size) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
        generator.initialize(size);
        KeyPair pair = generator.generateKeyPair();
        return pair.getPublic();
    }

    static String entry(PublicKey key) throws Exception { return PublicKeyEntry.appendPublicKeyEntry(new StringBuilder(), key).toString(); }

    /** An OpenSSH hashed host: {@code |1|base64(salt)|base64(HMAC-SHA1(salt, pattern))}. */
    static String hashed(String pattern) throws Exception {
        byte[] salt = new byte[20];
        for (int i = 0; i < salt.length; i++) salt[i] = (byte) (i * 7);
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(salt, "HmacSHA1"));
        return "|1|" + Base64.getEncoder().encodeToString(salt) + "|" + Base64.getEncoder().encodeToString(mac.doFinal(pattern.getBytes()));
    }

    @Test void matchesPlainAndHashedEntriesInEitherFile(@TempDir Path dir) throws Exception {
        PublicKey rsa = key("RSA", 2048), ec = key("EC", 256);
        Path own = dir.resolve("known_hosts"), user = dir.resolve("user_known_hosts");
        Files.writeString(own, "# mine\nplain.example " + entry(rsa) + "\n[odd.example]:2222 " + entry(ec) + "\n");
        Files.writeString(user, hashed("hashed.example") + " " + entry(ec) + "\nbroken line here\n");
        var trust = new KnownHosts(own, Optional.of(user));
        assertThat(trust.verify("plain.example", 22, rsa)).isEqualTo(new KnownHosts.Verdict.Match());
        assertThat(trust.verify("odd.example", 2222, ec)).isEqualTo(new KnownHosts.Verdict.Match());
        assertThat(trust.verify("odd.example", 22, ec)).as("port is part of the pattern").isEqualTo(new KnownHosts.Verdict.Unknown());
        assertThat(trust.verify("hashed.example", 22, ec)).isEqualTo(new KnownHosts.Verdict.Match());
        assertThat(trust.verify("hashed.example", 22, rsa)).isEqualTo(new KnownHosts.Verdict.Mismatch(KnownHosts.fingerprint(ec)));
        assertThat(trust.verify("plain.example", 22, ec)).isEqualTo(new KnownHosts.Verdict.Mismatch(KnownHosts.fingerprint(rsa)));
        assertThat(trust.verify("new.example", 22, rsa)).isEqualTo(new KnownHosts.Verdict.Unknown());
        assertThat(new KnownHosts(own, Optional.empty()).verify("hashed.example", 22, ec)).as("user file off").isEqualTo(new KnownHosts.Verdict.Unknown());
        assertThat(new KnownHosts(dir.resolve("absent"), Optional.of(dir.resolve("also-absent"))).verify("x", 22, rsa)).isEqualTo(new KnownHosts.Verdict.Unknown());
        assertThat(KnownHosts.fingerprint(rsa)).startsWith("SHA256:");
    }

    @Test void revokedIsAMismatchAndACorruptOwnFileRefuses(@TempDir Path dir) throws Exception {
        PublicKey rsa = key("RSA", 2048);
        Path own = dir.resolve("known_hosts");
        Files.writeString(own, "@revoked gone.example " + entry(rsa) + "\n");
        var trust = new KnownHosts(own, Optional.empty());
        assertThat(trust.verify("gone.example", 22, rsa)).isInstanceOf(KnownHosts.Verdict.Mismatch.class);
        Files.writeString(own, "this is not a known_hosts line\n");
        assertThatThrownBy(() -> trust.verify("gone.example", 22, rsa)).isInstanceOf(CorruptTrustFileException.class).hasMessageContaining("known_hosts");
    }

    @Test void trustAppendsAndRefusesToOverwriteAConflict(@TempDir Path dir) throws Exception {
        PublicKey rsa = key("RSA", 2048), ec = key("EC", 256);
        Path own = dir.resolve("deep/known_hosts");
        var trust = new KnownHosts(own, Optional.empty());
        trust.trust("new.example", 22, rsa);
        trust.trust("new.example", 2222, ec);
        assertThat(Files.readString(own)).contains("new.example " + entry(rsa), "[new.example]:2222 " + entry(ec));
        assertThat(trust.verify("new.example", 22, rsa)).isEqualTo(new KnownHosts.Verdict.Match());
        trust.trust("new.example", 22, rsa);
        assertThat(Files.readString(own).lines().filter(line -> line.startsWith("new.example ")).count()).as("idempotent").isEqualTo(1);
        assertThatThrownBy(() -> trust.trust("new.example", 22, ec)).isInstanceOf(java.io.IOException.class).hasMessageContaining("different key");
        assertThat(KnownHosts.hostPattern("h", 22)).isEqualTo("h");
        assertThat(KnownHosts.hostPattern("h", 23)).isEqualTo("[h]:23");
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-plugin-remote:test -q`
Expected: compilation failure.

- [x] **Step 3: Implement**

`trust/package-info.java`:

```java
/** Host-key trust: Jasper's own known_hosts plus read-only matching against the user's. */
package dev.jasper.remote.trust;
```

`trust/CorruptTrustFileException.java`:

```java
package dev.jasper.remote.trust;

/** Jasper's own known_hosts could not be parsed; connections are refused rather than treating it as empty. */
public final class CorruptTrustFileException extends RuntimeException {
    public CorruptTrustFileException(String message) { super(message); }
}
```

`trust/KnownHosts.java`:

```java
package dev.jasper.remote.trust;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.sshd.client.config.hosts.KnownHostEntry;
import org.apache.sshd.client.config.hosts.KnownHostHashValue;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;

/**
 * OpenSSH known_hosts files: Jasper's own (strict: a line that fails to parse makes the file corrupt)
 * and optionally the user's (lenient: bad lines are skipped, nothing is ever written there). Hashed
 * entries match; {@code @revoked} entries mismatch; {@code @cert-authority} entries are ignored.
 */
public final class KnownHosts {
    public sealed interface Verdict {
        record Match() implements Verdict { }
        record Mismatch(String knownFingerprint) implements Verdict { }
        record Unknown() implements Verdict { }
    }

    private record Known(String pattern, PublicKey key, boolean revoked) { }

    private final Path own;
    private final Optional<Path> user;

    public KnownHosts(Path own, Optional<Path> user) { this.own = own; this.user = user; }

    public static String fingerprint(PublicKey key) { return KeyUtils.getFingerPrint(key); }
    public static String hostPattern(String host, int port) { return KnownHostHashValue.createHostPattern(host, port); }

    public Verdict verify(String host, int port, PublicKey key) {
        List<Known> candidates = new ArrayList<>(entries(own, true, host, port));
        user.ifPresent(file -> candidates.addAll(entries(file, false, host, port)));
        boolean matched = false;
        String mismatch = null;
        for (Known known : candidates) {
            boolean same = KeyUtils.findMatchingKey(key, List.of(known.key())) != null;
            if (known.revoked() && same) return new Verdict.Mismatch(fingerprint(known.key()));
            if (known.revoked()) continue;
            if (same) matched = true; else if (mismatch == null) mismatch = fingerprint(known.key());
        }
        if (mismatch != null) return new Verdict.Mismatch(mismatch);
        return matched ? new Verdict.Match() : new Verdict.Unknown();
    }

    /** Appends {@code pattern key} to the own file unless it is already there; refuses when the file holds a different key. */
    public synchronized void trust(String host, int port, PublicKey key) throws IOException {
        for (Known known : entries(own, true, host, port)) {
            if (KeyUtils.findMatchingKey(key, List.of(known.key())) != null) { if (!known.revoked()) return; }
            else throw new IOException("known_hosts already holds a different key for " + hostPattern(host, port) + " (" + fingerprint(known.key()) + ")");
        }
        String line = hostPattern(host, port) + " " + PublicKeyEntry.appendPublicKeyEntry(new StringBuilder(), key) + "\n";
        String existing = Files.isRegularFile(own) ? Files.readString(own, StandardCharsets.UTF_8) : "";
        if (!existing.isEmpty() && !existing.endsWith("\n")) existing += "\n";
        Files.createDirectories(own.toAbsolutePath().getParent());
        Path temp = own.resolveSibling(own.getFileName() + ".tmp");
        Files.writeString(temp, existing + line, StandardCharsets.UTF_8);
        try { Files.move(temp, own, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, own, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static List<Known> entries(Path file, boolean strict, String host, int port) {
        if (!Files.isRegularFile(file)) return List.of();
        List<String> lines;
        try { lines = Files.readAllLines(file, StandardCharsets.UTF_8); }
        catch (IOException unreadable) { if (strict) throw new CorruptTrustFileException("known_hosts is unreadable: " + unreadable.getMessage()); return List.of(); }
        var out = new ArrayList<Known>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            try {
                KnownHostEntry entry = KnownHostEntry.parseKnownHostEntry(line);
                if (entry == null) continue;
                String marker = entry.getMarker();
                if ("cert-authority".equals(marker)) continue;
                if (!entry.isHostMatch(host, port)) continue;
                PublicKey key = entry.getKeyEntry().resolvePublicKey(null, PublicKeyEntryResolver.IGNORING);
                if (key == null) continue;
                out.add(new Known(line, key, "revoked".equals(marker)));
            } catch (RuntimeException | java.security.GeneralSecurityException | IOException bad) {
                if (strict) throw new CorruptTrustFileException("known_hosts line " + (i + 1) + " is not a known_hosts entry");
            }
        }
        return out;
    }
}
```

If `KnownHostEntry.getMarker()` returns the marker with its leading `@`, compare against `"@revoked"` / `"@cert-authority"`; the test tells which.

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test -q`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add plugins/remote
git commit -m "feat(remote): known_hosts trust with read-only matching against the user's file"
```

---

### Task 4: SSH agent client and its MINA adapter

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/agent/package-info.java`, `AgentClient.java`, `MinaAgent.java`, `MinaAgentFactory.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/agent/FakeAgent.java`, `AgentClientTest.java`

**Interfaces:**
- Produces: `AgentClient(Function<byte[], byte[]> exchange)` (one framed request in, one framed reply out, without the length prefix) with `List<Identity> identities() throws IOException`, `Map.Entry<String, byte[]> sign(Identity, byte[] data, int flags) throws IOException`, `static Optional<AgentClient> forEnvironment(Map<String, String> env, String osName)`, constants `FLAG_RSA_SHA2_256 = 2`, `FLAG_RSA_SHA2_512 = 4`; `AgentClient.Identity(PublicKey key, byte[] blob, String comment)`; `MinaAgent(AgentClient) implements SshAgent`; `MinaAgentFactory(AgentClient) implements SshAgentFactory`. Test fixture `FakeAgent` (a protocol implementation over JDK keys with `byte[] handle(byte[] request)` and `serveUnixSocket(Path)`).

- [x] **Step 1: Write the fake agent and the failing tests**

`agent/FakeAgent.java` (test source):

```java
package dev.jasper.remote.agent;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;

/** An ssh-agent that holds JDK key pairs and answers the two requests the plugin sends. */
public final class FakeAgent implements AutoCloseable {
    public final List<KeyPair> keys = new ArrayList<>();
    public final List<Integer> flagsSeen = new ArrayList<>();
    private ServerSocketChannel server;
    private Thread thread;

    public FakeAgent(KeyPair... pairs) { keys.addAll(List.of(pairs)); }

    /** The reply to one unframed request, unframed. */
    public byte[] handle(byte[] request) {
        try {
            var in = new ByteArrayBuffer(request);
            int type = in.getByte() & 0xff;
            var out = new ByteArrayBuffer();
            if (type == 11) {
                out.putByte((byte) 12);
                out.putInt(keys.size());
                for (KeyPair pair : keys) { out.putBytes(blob(pair.getPublic())); out.putString("fake " + KeyUtils.getKeyType(pair.getPublic())); }
            } else if (type == 13) {
                byte[] blob = in.getBytes(); byte[] data = in.getBytes(); int flags = in.getInt();
                flagsSeen.add(flags);
                PublicKey key = new ByteArrayBuffer(blob).getRawPublicKey();
                KeyPair pair = keys.stream().filter(candidate -> KeyUtils.findMatchingKey(key, List.of(candidate.getPublic())) != null).findFirst().orElse(null);
                if (pair == null) { out.putByte((byte) 5); return out.getCompactData(); }
                String algorithm; Signature signature;
                if (key.getAlgorithm().equals("RSA")) {
                    algorithm = (flags & 4) != 0 ? "rsa-sha2-512" : (flags & 2) != 0 ? "rsa-sha2-256" : "ssh-rsa";
                    signature = Signature.getInstance((flags & 4) != 0 ? "SHA512withRSA" : (flags & 2) != 0 ? "SHA256withRSA" : "SHA1withRSA");
                } else { algorithm = "ssh-ed25519"; signature = Signature.getInstance("Ed25519"); }
                signature.initSign(pair.getPrivate());
                signature.update(data);
                var sig = new ByteArrayBuffer();
                sig.putString(algorithm); sig.putBytes(signature.sign());
                out.putByte((byte) 14); out.putBytes(sig.getCompactData());
            } else out.putByte((byte) 5);
            return out.getCompactData();
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    static byte[] blob(PublicKey key) { var buffer = new ByteArrayBuffer(); buffer.putRawPublicKey(key); return buffer.getCompactData(); }

    /** Serves the agent protocol on a Unix-domain socket until closed; one request per connection, like the real client. */
    public void serveUnixSocket(Path socket) throws IOException {
        server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        server.bind(UnixDomainSocketAddress.of(socket));
        thread = Thread.ofPlatform().daemon().start(() -> {
            while (server.isOpen()) {
                try (SocketChannel channel = server.accept()) {
                    while (true) {
                        ByteBuffer length = ByteBuffer.allocate(4);
                        while (length.hasRemaining()) if (channel.read(length) < 0) throw new IOException("closed");
                        length.flip();
                        ByteBuffer body = ByteBuffer.allocate(length.getInt());
                        while (body.hasRemaining()) if (channel.read(body) < 0) throw new IOException("closed");
                        byte[] reply = handle(body.array());
                        ByteBuffer frame = ByteBuffer.allocate(4 + reply.length).putInt(reply.length).put(reply).flip();
                        while (frame.hasRemaining()) channel.write(frame);
                    }
                } catch (IOException ended) { /* next connection */ }
            }
        });
    }

    @Override public void close() throws IOException { if (server != null) server.close(); }

    static String text(byte[] bytes) { return new String(bytes, StandardCharsets.UTF_8); }
}
```

`agent/AgentClientTest.java`:

```java
package dev.jasper.remote.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class AgentClientTest {
    static KeyPair generate(String algorithm, int size) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
        if (size > 0) generator.initialize(size);
        return generator.generateKeyPair();
    }

    @Test void listsIdentitiesAndSignsThroughTheAgent() throws Exception {
        KeyPair rsa = generate("RSA", 2048), ed = generate("Ed25519", 0);
        var agent = new FakeAgent(rsa, ed);
        var client = new AgentClient(agent::handle);
        List<AgentClient.Identity> identities = client.identities();
        assertThat(identities).hasSize(2);
        assertThat(identities.get(0).comment()).isEqualTo("fake ssh-rsa");
        assertThat(KeyUtils.findMatchingKey(rsa.getPublic(), List.of(identities.get(0).key()))).isNotNull();
        byte[] data = "sign me".getBytes();
        Map.Entry<String, byte[]> signed = client.sign(identities.get(0), data, AgentClient.FLAG_RSA_SHA2_256);
        assertThat(signed.getKey()).isEqualTo("rsa-sha2-256");
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(rsa.getPublic());
        verifier.update(data);
        assertThat(verifier.verify(signed.getValue())).isTrue();
        assertThat(agent.flagsSeen).containsExactly(2);
        assertThat(client.sign(identities.get(1), data, 0).getKey()).isEqualTo("ssh-ed25519");
    }

    @Test void aFailureReplyIsAnIOException() throws Exception {
        var client = new AgentClient(request -> new byte[] {5});
        assertThatThrownBy(client::identities).isInstanceOf(IOException.class).hasMessageContaining("agent");
    }

    @Test void minaAdapterMapsAlgorithmsToFlags() throws Exception {
        KeyPair rsa = generate("RSA", 2048);
        var agent = new FakeAgent(rsa);
        var mina = new MinaAgent(new AgentClient(agent::handle));
        var identities = new java.util.ArrayList<Map.Entry<java.security.PublicKey, String>>();
        mina.getIdentities().forEach(identities::add);
        assertThat(identities).hasSize(1);
        assertThat(mina.sign(null, rsa.getPublic(), "rsa-sha2-512", "x".getBytes()).getKey()).isEqualTo("rsa-sha2-512");
        assertThat(mina.sign(null, rsa.getPublic(), "ssh-rsa", "x".getBytes()).getKey()).isEqualTo("ssh-rsa");
        assertThat(agent.flagsSeen).containsExactly(4, 0);
        assertThatThrownBy(() -> mina.sign(null, generate("RSA", 2048).getPublic(), "ssh-rsa", "x".getBytes())).isInstanceOf(IOException.class).hasMessageContaining("not in the agent");
        assertThatThrownBy(() -> mina.addIdentity(rsa, "c")).isInstanceOf(UnsupportedOperationException.class);
        var factory = new MinaAgentFactory(new AgentClient(agent::handle));
        assertThat(factory.getChannelForwardingFactories(null)).isEmpty();
        assertThat(factory.createClient(null, null)).isInstanceOf(MinaAgent.class);
    }

    @Test void talksToAUnixSocketAgent(@TempDir Path dir) throws Exception {
        assumeFalse(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"));
        Path socket = Files.createTempDirectory(dir, "a").resolve("agent.sock");
        try (var agent = new FakeAgent(generate("RSA", 2048))) {
            agent.serveUnixSocket(socket);
            Optional<AgentClient> client = AgentClient.forEnvironment(Map.of("SSH_AUTH_SOCK", socket.toString()), "Mac OS X");
            assertThat(client).isPresent();
            assertThat(client.get().identities()).hasSize(1);
            assertThat(client.get().identities()).as("a fresh connection per request").hasSize(1);
        }
        assertThat(AgentClient.forEnvironment(Map.of(), "Linux")).isEmpty();
        assertThat(AgentClient.forEnvironment(Map.of("SSH_AUTH_SOCK", socket.toString()), "Linux")).isPresent();
        assertThat(AgentClient.forEnvironment(Map.of(), "Windows 11")).as("the OpenSSH pipe is always a candidate on Windows").isPresent();
        assertThatThrownBy(() -> AgentClient.forEnvironment(Map.of("SSH_AUTH_SOCK", dir.resolve("gone").toString()), "Linux").get().identities())
            .isInstanceOf(IOException.class).hasMessageContaining("agent");
    }
}
```

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test -q`
Expected: compilation failure.

- [x] **Step 3: Implement**

`agent/package-info.java`:

```java
/** A minimal ssh-agent client (list identities, sign) over the agent socket, and its adapter for MINA. */
package dev.jasper.remote.agent;
```

`agent/AgentClient.java`:

```java
package dev.jasper.remote.agent;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;

/**
 * Speaks only {@code SSH_AGENTC_REQUEST_IDENTITIES} and {@code SSH_AGENTC_SIGN_REQUEST}. One short-lived
 * connection per request: a Unix-domain socket at {@code SSH_AUTH_SOCK}, or the OpenSSH named pipe on Windows.
 */
public final class AgentClient {
    public static final int FLAG_RSA_SHA2_256 = 2;
    public static final int FLAG_RSA_SHA2_512 = 4;
    static final int REQUEST_IDENTITIES = 11, IDENTITIES_ANSWER = 12, SIGN_REQUEST = 13, SIGN_RESPONSE = 14;
    static final String WINDOWS_PIPE = "\\\\.\\pipe\\openssh-ssh-agent";
    private static final int MAX_REPLY = 1 << 20;

    /** One key the agent holds; {@code blob} is its SSH wire encoding, sent back when asking for a signature. */
    public record Identity(PublicKey key, byte[] blob, String comment) { }

    private final Function<byte[], byte[]> exchange;

    public AgentClient(Function<byte[], byte[]> exchange) { this.exchange = exchange; }

    /** The agent named by the environment: {@code SSH_AUTH_SOCK}, or the OpenSSH pipe on Windows; empty when neither applies. */
    public static Optional<AgentClient> forEnvironment(Map<String, String> env, String osName) {
        boolean windows = osName.toLowerCase(Locale.ROOT).contains("win");
        String socket = env.get("SSH_AUTH_SOCK");
        if (socket != null && !socket.isBlank()) return Optional.of(new AgentClient(request -> unixSocket(Path.of(socket), request)));
        if (windows) return Optional.of(new AgentClient(request -> namedPipe(WINDOWS_PIPE, request)));
        return Optional.empty();
    }

    public List<Identity> identities() throws IOException {
        ByteArrayBuffer reply = request(new byte[] {(byte) REQUEST_IDENTITIES}, IDENTITIES_ANSWER);
        int count = reply.getInt();
        var out = new ArrayList<Identity>();
        for (int i = 0; i < count; i++) {
            byte[] blob = reply.getBytes();
            String comment = reply.getString(StandardCharsets.UTF_8);
            try { out.add(new Identity(new ByteArrayBuffer(blob).getRawPublicKey(), blob, comment)); }
            catch (RuntimeException | org.apache.sshd.common.SshException unsupported) { /* a key type MINA cannot use; skip it */ }
        }
        return List.copyOf(out);
    }

    /** The agent's signature over {@code data} with {@code identity}: the algorithm name and the raw signature. */
    public Map.Entry<String, byte[]> sign(Identity identity, byte[] data, int flags) throws IOException {
        var request = new ByteArrayBuffer();
        request.putByte((byte) SIGN_REQUEST);
        request.putBytes(identity.blob());
        request.putBytes(data);
        request.putInt(flags);
        ByteArrayBuffer reply = request(request.getCompactData(), SIGN_RESPONSE);
        var signature = new ByteArrayBuffer(reply.getBytes());
        return Map.entry(signature.getString(StandardCharsets.UTF_8), signature.getBytes());
    }

    private ByteArrayBuffer request(byte[] message, int expectedType) throws IOException {
        byte[] reply;
        try { reply = exchange.apply(message); }
        catch (UncheckedIOException failure) { throw new IOException("SSH agent not available: " + failure.getCause().getMessage(), failure.getCause()); }
        if (reply.length == 0) throw new IOException("SSH agent sent an empty reply");
        int type = reply[0] & 0xff;
        if (type != expectedType) throw new IOException("SSH agent refused the request (reply type " + type + ")");
        return new ByteArrayBuffer(reply, 1, reply.length - 1);
    }

    static byte[] unixSocket(Path socket, byte[] message) {
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(socket));
            ByteBuffer frame = ByteBuffer.allocate(4 + message.length).putInt(message.length).put(message).flip();
            while (frame.hasRemaining()) channel.write(frame);
            ByteBuffer length = ByteBuffer.allocate(4);
            while (length.hasRemaining()) if (channel.read(length) < 0) throw new IOException("agent closed the connection");
            int size = length.flip().getInt();
            if (size < 1 || size > MAX_REPLY) throw new IOException("agent reply of " + size + " bytes");
            ByteBuffer body = ByteBuffer.allocate(size);
            while (body.hasRemaining()) if (channel.read(body) < 0) throw new IOException("agent closed the connection");
            return body.array();
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    static byte[] namedPipe(String pipe, byte[] message) {
        try (var file = new RandomAccessFile(pipe, "rw")) {
            file.write(ByteBuffer.allocate(4 + message.length).putInt(message.length).put(message).array());
            int size = file.readInt();
            if (size < 1 || size > MAX_REPLY) throw new IOException("agent reply of " + size + " bytes");
            byte[] body = new byte[size];
            file.readFully(body);
            return body;
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
```

`agent/MinaAgent.java`:

```java
package dev.jasper.remote.agent;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.sshd.agent.SshAgent;
import org.apache.sshd.agent.SshAgentKeyConstraint;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.session.SessionContext;

/** MINA's view of the agent: identities to offer and signing that goes back to the agent. Read-only. */
public final class MinaAgent implements SshAgent {
    private final AgentClient client;
    private volatile boolean open = true;

    public MinaAgent(AgentClient client) { this.client = client; }

    @Override public Iterable<? extends Map.Entry<PublicKey, String>> getIdentities() throws IOException {
        var out = new ArrayList<Map.Entry<PublicKey, String>>();
        for (AgentClient.Identity identity : client.identities()) out.add(Map.entry(identity.key(), identity.comment()));
        return out;
    }

    @Override public Map.Entry<String, byte[]> sign(SessionContext session, PublicKey key, String algorithm, byte[] data) throws IOException {
        for (AgentClient.Identity identity : client.identities()) {
            if (KeyUtils.findMatchingKey(key, List.of(identity.key())) == null) continue;
            int flags = switch (algorithm) { case "rsa-sha2-256" -> AgentClient.FLAG_RSA_SHA2_256; case "rsa-sha2-512" -> AgentClient.FLAG_RSA_SHA2_512; default -> 0; };
            return client.sign(identity, data, flags);
        }
        throw new IOException("The key is not in the agent");
    }

    @Override public void addIdentity(KeyPair key, String comment, SshAgentKeyConstraint... constraints) { throw new UnsupportedOperationException("Jasper does not add keys to the agent"); }
    @Override public void removeIdentity(PublicKey key) { throw new UnsupportedOperationException("Jasper does not remove keys from the agent"); }
    @Override public void removeAllIdentities() { throw new UnsupportedOperationException("Jasper does not remove keys from the agent"); }
    @Override public boolean isOpen() { return open; }
    @Override public void close() { open = false; }
}
```

`agent/MinaAgentFactory.java`:

```java
package dev.jasper.remote.agent;

import java.util.List;
import org.apache.sshd.agent.SshAgent;
import org.apache.sshd.agent.SshAgentFactory;
import org.apache.sshd.agent.SshAgentServer;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.channel.ChannelFactory;
import org.apache.sshd.common.session.ConnectionService;
import org.apache.sshd.common.session.Session;

/** Hands MINA a {@link MinaAgent} per session; no agent forwarding, no agent server. */
public final class MinaAgentFactory implements SshAgentFactory {
    private final AgentClient client;

    public MinaAgentFactory(AgentClient client) { this.client = client; }

    @Override public List<ChannelFactory> getChannelForwardingFactories(FactoryManager manager) { return List.of(); }
    @Override public SshAgent createClient(Session session, FactoryManager manager) { return new MinaAgent(client); }
    @Override public SshAgentServer createServer(ConnectionService service) { throw new UnsupportedOperationException("Jasper does not forward the agent"); }
}
```

If `SshAgent` does not extend `java.nio.channels.Channel` in 2.19, drop the `isOpen`/`close` overrides (the compiler says which). `ByteArrayBuffer.putBytes(byte[])` and `putString(String)` come from `Buffer`; if only the three-argument `putBytes` exists, call `putBytes(blob, 0, blob.length)`.

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test -q`
Expected: PASS (`talksToAUnixSocketAgent` runs on macOS).

- [x] **Step 5: Commit**

```bash
git add plugins/remote
git commit -m "feat(remote): ssh-agent client over the agent socket, adapted for MINA"
```

---

### Task 5: Shared sessions, the connect pipeline and shell channels

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/client/package-info.java`, `HostKeyVerifier.java`, `Failures.java`, `ShellChannels.java`, `Connections.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/client/LoopbackServer.java`, `ConnectionsTest.java`

**Interfaces:**
- Consumes: `RemoteSettings`, `RemoteHost`, `Auth`, `KnownHosts`, `CorruptTrustFileException`, `AgentClient`, `MinaAgentFactory`, `dev.jasper.vault.api.Credential`, SDK `TerminalConnection`.
- Produces: `HostKeyVerifier.Question(String host, int port, String keyType, String fingerprint)`, `HostKeyVerifier.Decision { CANCEL, ONCE, TRUST }`, `HostKeyVerifier(KnownHosts, Function<Question, CompletableFuture<Decision>> prompt, Supplier<Duration> timeout) implements ServerKeyVerifier`, attribute keys `HostKeyVerifier.TARGET` (`Target(String host, int port)`) and `HostKeyVerifier.REJECTION` (`String`); `Failures.message(Throwable, Duration connectTimeout) -> String`; `Connections(Supplier<RemoteSettings>, KnownHosts, Optional<AgentClient>, Function<UUID, Optional<RemoteHost>> hosts, Optional<Function<UUID, CompletableFuture<Optional<Credential>>>> credentials, Function<HostKeyVerifier.Question, CompletableFuture<HostKeyVerifier.Decision>> prompt, Executor background, Executor ui, BiFunction<Duration, Runnable, Runnable> schedule)` with `CompletableFuture<Shell> shell(UUID hostId, int columns, int rows, Consumer<String> status)`, `int channelCount()`, `boolean connected(UUID hostId)`, `Subscription onChanged(Runnable)`, `void close()`; `Connections.Shell(UUID hostId, TerminalConnection connection)`; exceptions surface as `Failures.Failure(String message)` (a `RuntimeException` whose message is the user-facing text).

- [x] **Step 1: Write the loopback server fixture and the failing tests**

`client/LoopbackServer.java` (test source):

```java
package dev.jasper.remote.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.Signal;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

/**
 * An SSH server on 127.0.0.1 with an echo shell: it prints {@code READY <TERM> <COLUMNS>x<LINES>}, echoes
 * every byte, prints {@code WINCH <COLUMNS>x<LINES>} on a window change, and exits with 7 on {@code q}.
 * Password {@code deploy}/{@code s3cret}; public keys from {@link #allow}.
 */
public final class LoopbackServer implements AutoCloseable {
    public final SshServer server = SshServer.setUpDefaultServer();
    public final List<PublicKey> allowed = new ArrayList<>();
    public final SimpleGeneratorHostKeyProvider hostKey = new SimpleGeneratorHostKeyProvider();

    public LoopbackServer() throws IOException { this(0); }

    public LoopbackServer(int port) throws IOException {
        hostKey.setAlgorithm("RSA"); hostKey.setKeySize(2048);
        server.setHost("127.0.0.1"); server.setPort(port);
        server.setKeyPairProvider(hostKey);
        server.setPasswordAuthenticator((user, password, session) -> user.equals("deploy") && password.equals("s3cret"));
        server.setPublickeyAuthenticator((user, key, session) -> KeyUtils.findMatchingKey(key, allowed) != null);
        server.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);
        server.setShellFactory(channel -> new Echo());
        server.start();
    }

    public int port() { return server.getPort(); }
    public PublicKey hostPublicKey() throws IOException, java.security.GeneralSecurityException { return hostKey.loadKeys(null).iterator().next().getPublic(); }
    public void allow(PublicKey key) { allowed.add(key); }
    @Override public void close() throws IOException { server.stop(true); }

    static final class Echo implements Command {
        private InputStream in; private OutputStream out; private ExitCallback exit; private Thread thread;

        @Override public void setInputStream(InputStream in) { this.in = in; }
        @Override public void setOutputStream(OutputStream out) { this.out = out; }
        @Override public void setErrorStream(OutputStream err) { }
        @Override public void setExitCallback(ExitCallback callback) { this.exit = callback; }

        @Override public void start(ChannelSession channel, Environment env) {
            env.addSignalListener((ch, signal) -> write("WINCH " + env.getEnv().get(Environment.ENV_COLUMNS) + "x" + env.getEnv().get(Environment.ENV_LINES) + "\r\n"), Signal.WINCH);
            thread = Thread.ofPlatform().daemon().start(() -> {
                write("READY " + env.getEnv().get(Environment.ENV_TERM) + " " + env.getEnv().get(Environment.ENV_COLUMNS) + "x" + env.getEnv().get(Environment.ENV_LINES) + "\r\n");
                try {
                    int b;
                    while ((b = in.read()) >= 0) {
                        if (b == 'q') { exit.onExit(7); return; }
                        out.write(b); out.flush();
                    }
                } catch (IOException ended) { /* channel closed */ }
                exit.onExit(0);
            });
        }

        private synchronized void write(String text) { try { out.write(text.getBytes(StandardCharsets.UTF_8)); out.flush(); } catch (IOException ignored) { } }
        @Override public void destroy(ChannelSession channel) { if (thread != null) thread.interrupt(); }
    }
}
```

`client/ConnectionsTest.java`:

```java
package dev.jasper.remote.client;

import dev.jasper.remote.RemoteSettings;
import dev.jasper.remote.agent.AgentClient;
import dev.jasper.remote.agent.FakeAgent;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.sdk.terminal.TerminalConnection;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.Kind;
import dev.jasper.vault.keygen.KeyAlgorithm;
import dev.jasper.vault.keygen.KeyGenerator;
import dev.jasper.vault.model.SshKey;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ConnectionsTest {
    final ExecutorService background = Executors.newCachedThreadPool(Thread.ofPlatform().daemon().name("bg-", 0).factory());
    final ExecutorService ui = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("ui").factory());
    final Map<UUID, RemoteHost> hosts = new HashMap<>();
    final Map<UUID, Credential> credentials = new HashMap<>();
    final List<HostKeyVerifier.Question> questions = new ArrayList<>();
    final List<Runnable> scheduled = new ArrayList<>();
    HostKeyVerifier.Decision decision = HostKeyVerifier.Decision.TRUST;
    RemoteSettings settings = new RemoteSettings(Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ZERO, Duration.ZERO, false, true);
    Connections connections;
    KnownHosts trust;

    @AfterEach void stop() { if (connections != null) onUi(connections::close); background.shutdownNow(); ui.shutdownNow(); }

    /** Runs on the fake UI thread and waits: the registry is UI-thread-only. */
    <T> T onUi(java.util.function.Supplier<T> work) { try { return ui.submit(work::get).get(10, TimeUnit.SECONDS); } catch (Exception failure) { throw new IllegalStateException(failure); } }
    void onUi(Runnable work) { onUi(() -> { work.run(); return null; }); }

    Connections connections(Path dir, Optional<AgentClient> agent) {
        trust = new KnownHosts(dir.resolve("known_hosts"), Optional.empty());
        Function<UUID, CompletableFuture<Optional<Credential>>> vault = id -> CompletableFuture.completedFuture(Optional.ofNullable(credentials.get(id)));
        connections = new Connections(() -> settings, trust, agent, id -> Optional.ofNullable(hosts.get(id)), Optional.of(vault),
            question -> { questions.add(question); return CompletableFuture.completedFuture(decision); }, background, ui,
            (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); });
        return connections;
    }

    RemoteHost host(String name, int port, String username, Auth auth, Optional<UUID> jump) {
        RemoteHost host = RemoteHost.create(name, "127.0.0.1", port, username, auth, "", jump);
        hosts.put(host.id(), host);
        return host;
    }

    UUID passwordCredential() {
        UUID id = UUID.randomUUID();
        credentials.put(id, new Credential(id, "deploy login", Kind.ACCOUNT_PASSWORD, "deploy", "s3cret".toCharArray(), null, null));
        return id;
    }

    Connections.Shell shell(RemoteHost host) { return onUi(() -> connections.shell(host.id(), 100, 30, status -> { })).join(); }

    static String readUntil(TerminalConnection connection, String marker) throws IOException {
        var out = new StringBuilder();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!out.toString().contains(marker)) {
            if (System.nanoTime() > deadline) throw new IOException("Timed out waiting for " + marker + " in: " + out);
            int b = connection.output().read();
            if (b < 0) throw new IOException("EOF before " + marker + " in: " + out);
            out.append((char) b);
        }
        return out.toString();
    }

    static void type(TerminalConnection connection, String text) throws IOException { connection.input().write(text.getBytes(StandardCharsets.UTF_8)); connection.input().flush(); }

    static String failure(CompletableFuture<?> future) {
        try { future.get(15, TimeUnit.SECONDS); return "no failure"; }
        catch (java.util.concurrent.ExecutionException failure) { return failure.getCause().getMessage(); }
        catch (Exception other) { return other.toString(); }
    }

    @Test void passwordAuthOpensAShellThatEchoesResizesAndExits(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            connections(dir, Optional.empty());
            RemoteHost host = host("prod", server.port(), "", new Auth.Vault(passwordCredential()), Optional.empty());
            List<String> statuses = new ArrayList<>();
            CompletableFuture<Connections.Shell> future = onUi(() -> connections.shell(host.id(), 100, 30, statuses::add));
            Connections.Shell shell = future.get(15, TimeUnit.SECONDS);
            TerminalConnection connection = shell.connection();
            assertThat(readUntil(connection, "\r\n")).isEqualTo("READY xterm-256color 100x30\r\n");
            assertThat(statuses).containsSubsequence("Connecting…", "Authenticating…", "Opening shell…");
            assertThat(questions).as("an unknown host key was trusted").hasSize(1);
            assertThat(questions.getFirst().fingerprint()).isEqualTo(KnownHosts.fingerprint(server.hostPublicKey()));
            assertThat(Files.readString(dir.resolve("known_hosts"))).contains("[127.0.0.1]:" + server.port() + " ");
            type(connection, "hi");
            assertThat(readUntil(connection, "hi")).endsWith("hi");
            connection.resize().accept(120, 40);
            assertThat(readUntil(connection, "WINCH")).contains("WINCH 120x40");
            assertThat(onUi(connections::channelCount)).isEqualTo(1);
            assertThat(onUi(() -> connections.connected(host.id()))).isTrue();
            type(connection, "q");
            assertThat(connection.exited().get(10, TimeUnit.SECONDS)).isEqualTo(7);
            connection.close().run();
            assertThat(onUi(connections::channelCount)).isZero();
            assertThat(scheduled).as("linger scheduled after the last channel").hasSize(1);
            onUi(scheduled.getFirst());
            Thread.sleep(200);
            assertThat(onUi(() -> connections.connected(host.id()))).isFalse();
            assertThat(credentials.values()).allSatisfy(credential -> assertThatThrownBy(credential::password).as("closed after use").isInstanceOf(IllegalStateException.class));
        }
    }

    @Test void keyAuthUsesTheVaultKeyFileAndSharesOneSession(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            SshKey generated = new KeyGenerator(dir.resolve("keys")).generate(KeyAlgorithm.ED25519, "test", "t");
            server.allow(PublicKeyEntry.parsePublicKeyEntry(Files.readString(generated.publicPath()).strip()).resolvePublicKey(null, null, null));
            connections(dir, Optional.empty());
            UUID id = UUID.randomUUID();
            credentials.put(id, new Credential(id, "key", Kind.SSH_KEY, null, null, generated.privatePath(), null));
            RemoteHost host = host("keyed", server.port(), "deploy", new Auth.Vault(id), Optional.empty());
            Connections.Shell first = shell(host), second = shell(host);
            assertThat(readUntil(first.connection(), "\r\n")).startsWith("READY");
            assertThat(readUntil(second.connection(), "\r\n")).startsWith("READY");
            assertThat(onUi(connections::channelCount)).isEqualTo(2);
            assertThat(questions).as("the same host key is asked about once").hasSize(1);
            first.connection().close().run();
            assertThat(scheduled).as("no linger while a channel remains").isEmpty();
            server.close();
            assertThat(failure(second.connection().exited())).contains("connection lost");
            Thread.sleep(200);
            assertThat(onUi(() -> connections.connected(host.id()))).isFalse();
        }
    }

    @Test void agentAuthSignsThroughTheAgent(@TempDir Path dir) throws Exception {
        assumeFalse(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"));
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        Path socket = Files.createTempDirectory(dir, "a").resolve("agent.sock");
        try (var server = new LoopbackServer(); var agent = new FakeAgent(pair)) {
            agent.serveUnixSocket(socket);
            server.allow(pair.getPublic());
            connections(dir, AgentClient.forEnvironment(Map.of("SSH_AUTH_SOCK", socket.toString()), "Linux"));
            RemoteHost host = host("agent", server.port(), "deploy", Auth.AGENT, Optional.empty());
            assertThat(readUntil(shell(host).connection(), "\r\n")).startsWith("READY");
            assertThat(agent.flagsSeen).isNotEmpty();
        }
        connections(dir, Optional.empty());
        RemoteHost noAgent = host("noagent", 1, "deploy", Auth.AGENT, Optional.empty());
        assertThat(failure(onUi(() -> connections.shell(noAgent.id(), 80, 24, s -> { })))).isEqualTo("SSH agent not available");
    }

    @Test void hostKeyDecisionsAndChangesAreEnforced(@TempDir Path dir) throws Exception {
        int port;
        try (var server = new LoopbackServer()) {
            port = server.port();
            connections(dir, Optional.empty());
            RemoteHost host = host("prod", port, "", new Auth.Vault(passwordCredential()), Optional.empty());
            decision = HostKeyVerifier.Decision.CANCEL;
            assertThat(failure(onUi(() -> connections.shell(host.id(), 80, 24, s -> { })))).isEqualTo("Host key not trusted");
            decision = HostKeyVerifier.Decision.ONCE;
            assertThat(readUntil(shell(host).connection(), "\r\n")).startsWith("READY");
            assertThat(dir.resolve("known_hosts")).doesNotExist();
            onUi(connections::close);
            connections(dir, Optional.empty());
            decision = HostKeyVerifier.Decision.TRUST;
            assertThat(readUntil(shell(host).connection(), "\r\n")).startsWith("READY");
            assertThat(questions).hasSize(3);
            onUi(connections::close);
        }
        try (var replaced = new LoopbackServer(port)) {
            connections(dir, Optional.empty());
            RemoteHost host = host("prod", port, "", new Auth.Vault(passwordCredential()), Optional.empty());
            assertThat(failure(onUi(() -> connections.shell(host.id(), 80, 24, s -> { })))).startsWith("Host key rejected: fingerprint changed");
            assertThat(questions).as("a changed key never prompts").hasSize(3);
        }
    }

    @Test void failuresHaveTheSpecsMessages(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer()) {
            connections(dir, Optional.empty());
            UUID wrong = UUID.randomUUID();
            credentials.put(wrong, new Credential(wrong, "bad", Kind.ACCOUNT_PASSWORD, "deploy", "nope".toCharArray(), null, null));
            assertThat(failure(onUi(() -> connections.shell(host("bad", server.port(), "", new Auth.Vault(wrong), Optional.empty()).id(), 80, 24, s -> { })))).isEqualTo("Authentication failed (tried password)");
            assertThat(failure(onUi(() -> connections.shell(host("denied", server.port(), "", new Auth.Vault(UUID.randomUUID()), Optional.empty()).id(), 80, 24, s -> { })))).isEqualTo("Credential denied");
            RemoteHost unresolvable = RemoteHost.create("nowhere", "no-such-host.invalid", 22, "u", Auth.AGENT, "", Optional.empty());
            hosts.put(unresolvable.id(), unresolvable);
            assertThat(failure(onUi(() -> connections.shell(unresolvable.id(), 80, 24, s -> { })))).isEqualTo("Could not resolve host no-such-host.invalid");
            int closed; try (var probe = new ServerSocket(0)) { closed = probe.getLocalPort(); }
            assertThat(failure(onUi(() -> connections.shell(host("refused", closed, "deploy", Auth.AGENT, Optional.empty()).id(), 80, 24, s -> { })))).isEqualTo("Connection refused");
            assertThat(failure(onUi(() -> connections.shell(UUID.randomUUID(), 80, 24, s -> { })))).isEqualTo("Host not found");
        }
    }

    @Test void cancellingDuringConnectLeavesNothingBehind(@TempDir Path dir) throws Exception {
        try (var silent = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            connections(dir, Optional.empty());
            RemoteHost host = host("silent", silent.getLocalPort(), "", new Auth.Vault(passwordCredential()), Optional.empty());
            CompletableFuture<Connections.Shell> future = onUi(() -> connections.shell(host.id(), 80, 24, s -> { }));
            Thread.sleep(300);
            future.cancel(true);
            Thread.sleep(300);
            assertThat(onUi(() -> connections.connected(host.id()))).isFalse();
            assertThat(onUi(connections::channelCount)).isZero();
        }
    }

    @Test void proxyJumpGoesThroughTheJumpHostsSharedSession(@TempDir Path dir) throws Exception {
        try (var bastion = new LoopbackServer(); var target = new LoopbackServer()) {
            connections(dir, Optional.empty());
            RemoteHost jump = host("bastion", bastion.port(), "", new Auth.Vault(passwordCredential()), Optional.empty());
            RemoteHost inner = host("inner", target.port(), "", new Auth.Vault(passwordCredential()), Optional.of(jump.id()));
            assertThat(readUntil(shell(inner).connection(), "\r\n")).startsWith("READY");
            assertThat(onUi(() -> connections.connected(jump.id()))).as("the jump session is shared and kept").isTrue();
            assertThat(questions).extracting(HostKeyVerifier.Question::port).containsExactly(bastion.port(), target.port());
            assertThat(readUntil(shell(jump).connection(), "\r\n")).startsWith("READY");
            assertThat(questions).as("no new prompt for the shared jump session").hasSize(2);
        }
    }
}
```

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test -q`
Expected: compilation failure.

- [x] **Step 3: Implement the verifier and the failure messages**

`client/package-info.java`:

```java
/**
 * MINA behind one door: shared sessions per host, the connect / verify / authenticate pipeline, ProxyJump
 * through a local forward, and shell channels as {@code TerminalConnection}s. The registry is UI-thread-only.
 */
package dev.jasper.remote.client;
```

`client/HostKeyVerifier.java`:

```java
package dev.jasper.remote.client;

import dev.jasper.remote.trust.CorruptTrustFileException;
import dev.jasper.remote.trust.KnownHosts;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.config.keys.KeyUtils;

/**
 * Answers MINA's host-key question from the trust files, asking the user (on the UI thread, while MINA's
 * I/O thread waits) only for an unknown host. The real host and port travel in the {@link #TARGET}
 * attribute, because a ProxyJump hop connects to a loopback forward.
 */
public final class HostKeyVerifier implements ServerKeyVerifier {
    public record Target(String host, int port) { }
    public record Question(String host, int port, String keyType, String fingerprint) { }
    public enum Decision { CANCEL, ONCE, TRUST }

    public static final AttributeRepository.AttributeKey<Target> TARGET = new AttributeRepository.AttributeKey<>();
    /** Why the last verification said no, for the failure message. */
    public static final AttributeRepository.AttributeKey<String> REJECTION = new AttributeRepository.AttributeKey<>();

    private final KnownHosts trust;
    private final Function<Question, CompletableFuture<Decision>> prompt;
    private final Supplier<Duration> timeout;

    public HostKeyVerifier(KnownHosts trust, Function<Question, CompletableFuture<Decision>> prompt, Supplier<Duration> timeout) {
        this.trust = trust; this.prompt = prompt; this.timeout = timeout;
    }

    @Override public boolean verifyServerKey(ClientSession session, SocketAddress remote, PublicKey key) {
        Target target = session.getAttribute(TARGET);
        if (target == null && remote instanceof InetSocketAddress inet) target = new Target(inet.getHostString(), inet.getPort());
        if (target == null) { session.setAttribute(REJECTION, "Host key rejected: unknown target"); return false; }
        try {
            KnownHosts.Verdict verdict = trust.verify(target.host(), target.port(), key);
            return switch (verdict) {
                case KnownHosts.Verdict.Match match -> true;
                case KnownHosts.Verdict.Mismatch mismatch -> {
                    session.setAttribute(REJECTION, "Host key rejected: fingerprint changed (known " + mismatch.knownFingerprint() + ", offered " + KnownHosts.fingerprint(key) + ")");
                    yield false;
                }
                case KnownHosts.Verdict.Unknown unknown -> ask(session, target, key);
            };
        } catch (CorruptTrustFileException corrupt) {
            session.setAttribute(REJECTION, corrupt.getMessage());
            return false;
        }
    }

    private boolean ask(ClientSession session, Target target, PublicKey key) {
        Decision decision;
        try {
            decision = prompt.apply(new Question(target.host(), target.port(), KeyUtils.getKeyType(key), KnownHosts.fingerprint(key))).get(timeout.get().toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception cancelled) {
            session.setAttribute(REJECTION, "Host key not trusted");
            return false;
        }
        switch (decision) {
            case CANCEL -> { session.setAttribute(REJECTION, "Host key not trusted"); return false; }
            case ONCE -> { return true; }
            case TRUST -> {
                try { trust.trust(target.host(), target.port(), key); return true; }
                catch (IOException failure) { session.setAttribute(REJECTION, "Could not save the host key: " + failure.getMessage()); return false; }
            }
        }
        return false;
    }
}
```

`client/Failures.java`:

```java
package dev.jasper.remote.client;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

/** The user-facing text for every way a connection can fail (spec section 5). */
public final class Failures {
    /** Carries a finished message; the pipeline throws these once it knows what to say. */
    public static final class Failure extends RuntimeException {
        public Failure(String message) { super(message); }
        public Failure(String message, Throwable cause) { super(message, cause); }
    }

    private Failures() { }

    public static String message(Throwable failure, Duration connectTimeout, String hostname) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof Failure ready) return ready.getMessage();
        if (cause instanceof UnknownHostException || cause instanceof UnresolvedAddressException) return "Could not resolve host " + hostname;
        String text = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
        if (cause instanceof ConnectException || text.contains("connection refused")) return "Connection refused";
        if (cause instanceof TimeoutException || text.contains("timeout") || text.contains("timed out")) return "Timed out after " + connectTimeout.toSeconds() + " s";
        if (text.contains("connection lost") || text.contains("closed") || text.contains("reset")) return "Connection lost";
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
```

- [x] **Step 4: Implement the shell channel adapter**

`client/ShellChannels.java`:

```java
package dev.jasper.remote.client;

import dev.jasper.sdk.terminal.TerminalConnection;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.PtyChannelConfiguration;

/** Opens a PTY shell channel and wraps it as the SDK's connection record. */
final class ShellChannels {
    private ShellChannels() { }

    /**
     * Blocks (background thread) until the channel is open. {@code released} runs exactly once, on the
     * caller's thread of {@code close}, after the channel is closed; the registry drops a reference there.
     */
    static TerminalConnection open(ClientSession session, int columns, int rows, Duration timeout, Runnable released) throws IOException {
        var pty = new PtyChannelConfiguration();
        pty.setPtyType(TerminalConnection.TERM);
        pty.setPtyColumns(Math.max(1, columns)); pty.setPtyLines(Math.max(1, rows));
        pty.setPtyWidth(Math.max(1, columns) * 8); pty.setPtyHeight(Math.max(1, rows) * 16);
        ChannelShell channel = session.createShellChannel(pty, Map.of("TERM", TerminalConnection.TERM, "COLORTERM", "truecolor"));
        var exited = new CompletableFuture<Integer>();
        channel.addCloseFutureListener(closed -> {
            Integer status = channel.getExitStatus();
            if (status != null) exited.complete(status);
            else exited.completeExceptionally(new IOException("connection lost"));
        });
        channel.open().verify(timeout);
        var closedOnce = new AtomicBoolean();
        return new TerminalConnection(channel.getInvertedOut(), channel.getInvertedIn(),
            (newColumns, newRows) -> { try { channel.sendWindowChange(Math.max(1, newColumns), Math.max(1, newRows)); } catch (IOException ignored) { /* closing */ } },
            exited,
            () -> { if (!closedOnce.compareAndSet(false, true)) return; try { channel.close(false); } finally { released.run(); } });
    }
}
```

- [x] **Step 5: Implement the registry and pipeline**

`client/Connections.java`:

```java
package dev.jasper.remote.client;

import dev.jasper.remote.RemoteSettings;
import dev.jasper.remote.agent.AgentClient;
import dev.jasper.remote.agent.MinaAgentFactory;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.TerminalConnection;
import dev.jasper.vault.api.Credential;
import java.io.IOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.session.SessionHeartbeatController;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.common.util.security.SecurityUtils;

/**
 * At most one live session per host, reference-counted by channels, lingering briefly when idle, evicted
 * when it dies. Two MINA clients: one that offers the agent's identities (Agent hosts) and one that does not
 * (Vault hosts). Registry state is touched on the UI executor only; the pipeline runs on the background one.
 */
public final class Connections {
    public record Shell(UUID hostId, TerminalConnection connection) { }

    private static final System.Logger LOG = System.getLogger(Connections.class.getName());

    private final Supplier<RemoteSettings> settings;
    private final HostKeyVerifier verifier;
    private final Optional<AgentClient> agent;
    private final Function<UUID, Optional<RemoteHost>> hosts;
    private final Optional<Function<UUID, CompletableFuture<Optional<Credential>>>> credentials;
    private final Executor background, ui;
    private final BiFunction<Duration, Runnable, Runnable> schedule;
    private final Map<UUID, Shared> sessions = new HashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private SshClient plainClient, agentClient;
    private int channels;
    private boolean closed;

    /** One host's session: connecting (future pending) or live; {@code refs} counts open channels. */
    private final class Shared {
        final UUID hostId;
        final CompletableFuture<ClientSession> ready = new CompletableFuture<>();
        ClientSession session;
        int refs;
        int waiters;
        Runnable cancelLinger;
        SshdSocketAddress forward;
        ClientSession forwardOwner;
        Shared(UUID hostId) { this.hostId = hostId; }
    }

    public Connections(Supplier<RemoteSettings> settings, KnownHosts trust, Optional<AgentClient> agent, Function<UUID, Optional<RemoteHost>> hosts,
                       Optional<Function<UUID, CompletableFuture<Optional<Credential>>>> credentials,
                       Function<HostKeyVerifier.Question, CompletableFuture<HostKeyVerifier.Decision>> prompt,
                       Executor background, Executor ui, BiFunction<Duration, Runnable, Runnable> schedule) {
        this.settings = settings; this.agent = agent; this.hosts = hosts; this.credentials = credentials;
        this.background = background; this.ui = ui; this.schedule = schedule;
        this.verifier = new HostKeyVerifier(trust, prompt, () -> settings.get().authTimeout());
    }

    public int channelCount() { return channels; }
    public boolean connected(UUID hostId) { Shared shared = sessions.get(hostId); return shared != null && shared.session != null && shared.session.isOpen(); }
    public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }

    /** A new shell channel on the host's shared session; cancelling the future withdraws this request. */
    public CompletableFuture<Shell> shell(UUID hostId, int columns, int rows, Consumer<String> status) {
        var result = new CompletableFuture<Shell>();
        Optional<RemoteHost> host = hosts.apply(hostId);
        if (closed || host.isEmpty()) { result.completeExceptionally(new Failures.Failure(closed ? "Remote is stopping" : "Host not found")); return result; }
        CompletableFuture<ClientSession> session = sessionFor(host.get(), status);
        result.whenComplete((ignored, failure) -> { if (result.isCancelled()) ui.execute(() -> withdraw(hostId)); });
        session.whenComplete((ready, failure) -> ui.execute(() -> {
            Shared shared = sessions.get(hostId);
            if (shared != null) shared.waiters--;
            if (result.isDone()) { return; }
            if (failure != null) { result.completeExceptionally(new Failures.Failure(Failures.message(failure, settings.get().connectTimeout(), host.get().hostname()))); return; }
            status.accept("Opening shell…");
            if (shared != null) shared.refs++;
            onBackground(() -> ShellChannels.open(ready, columns, rows, settings.get().connectTimeout(), () -> ui.execute(() -> release(hostId))))
                .whenComplete((connection, openFailure) -> ui.execute(() -> {
                    if (openFailure != null) { release(hostId); result.completeExceptionally(new Failures.Failure(Failures.message(openFailure, settings.get().connectTimeout(), host.get().hostname()))); return; }
                    channels++; notifyChanged();
                    if (!result.complete(new Shell(hostId, connection))) connection.close().run();
                }));
        }));
        return result;
    }

    /** Stops every session and both clients. */
    public void close() {
        closed = true;
        for (Shared shared : List.copyOf(sessions.values())) evict(shared, "Remote is stopping");
        for (SshClient client : Arrays.asList(plainClient, agentClient)) if (client != null) client.stop();
        plainClient = null; agentClient = null;
    }

    // ---- sessions

    private CompletableFuture<ClientSession> sessionFor(RemoteHost host, Consumer<String> status) {
        Shared shared = sessions.get(host.id());
        if (shared != null) {
            if (shared.session != null && !shared.session.isOpen()) { evict(shared, "connection lost"); shared = null; }
            else { shared.waiters++; if (shared.cancelLinger != null) { shared.cancelLinger.run(); shared.cancelLinger = null; } return shared.ready; }
        }
        var fresh = new Shared(host.id());
        fresh.waiters = 1;
        sessions.put(host.id(), fresh);
        CompletableFuture<Optional<Credential>> credential = credentialFor(host);
        CompletableFuture<Optional<ClientSession>> via = host.jump().isPresent()
            ? hosts.apply(host.jump().get()).map(jump -> sessionFor(jump, status).thenApply(Optional::of)).orElseGet(() -> CompletableFuture.failedFuture(new Failures.Failure("jump host missing")))
            : CompletableFuture.completedFuture(Optional.empty());
        credential.thenCombine(via, Map::entry).whenComplete((pair, failure) -> ui.execute(() -> {
            if (fresh.ready.isDone()) { if (pair != null) pair.getKey().ifPresent(Credential::close); return; }
            if (failure != null) { fail(fresh, failure); return; }
            status.accept("Connecting…");
            Optional<Credential> secret = pair.getKey();
            Optional<ClientSession> jumpSession = pair.getValue();
            onBackground(() -> connect(host, secret, jumpSession, fresh, status)).whenComplete((session, connectFailure) -> ui.execute(() -> {
                if (connectFailure != null) { fail(fresh, connectFailure); return; }
                if (fresh.ready.isDone()) { session.close(false); return; }
                fresh.session = session;
                session.addCloseFutureListener(closedFuture -> ui.execute(() -> { if (sessions.get(host.id()) == fresh) evict(fresh, "connection lost"); }));
                fresh.ready.complete(session);
                notifyChanged();
            }));
        }));
        return fresh.ready;
    }

    private CompletableFuture<Optional<Credential>> credentialFor(RemoteHost host) {
        return switch (host.auth()) {
            case Auth.Agent agentAuth -> agent.isPresent() && settings.get().useAgent() ? CompletableFuture.completedFuture(Optional.empty()) : CompletableFuture.failedFuture(new Failures.Failure("SSH agent not available"));
            case Auth.Vault vault -> credentials.map(source -> source.apply(vault.credentialId()).thenApply(found -> {
                if (found.isEmpty()) throw new Failures.Failure("Credential denied");
                return found;
            })).orElseGet(() -> CompletableFuture.failedFuture(new Failures.Failure("needs Credential Vault")));
        };
    }

    /** Background: TCP (through the jump's forward when given), host key, authentication. Closes the credential. */
    private ClientSession connect(RemoteHost host, Optional<Credential> credential, Optional<ClientSession> jump, Shared shared, Consumer<String> status) throws Exception {
        RemoteSettings current = settings.get();
        String username = host.username().isEmpty() ? credential.flatMap(Credential::username).orElse("") : host.username();
        try {
            if (username.isEmpty()) throw new Failures.Failure("No username for " + host.name());
            SshClient client = client(host.auth() instanceof Auth.Agent);
            String address = host.hostname(); int port = host.port();
            if (jump.isPresent()) {
                SshdSocketAddress bound = jump.get().startLocalPortForwarding(new SshdSocketAddress(SshdSocketAddress.LOCALHOST_IPV4, 0), new SshdSocketAddress(host.hostname(), host.port()));
                shared.forward = bound; shared.forwardOwner = jump.get();
                address = bound.getHostName(); port = bound.getPort();
            }
            ClientSession session = client.connect(username, address, port, AttributeRepository.ofKeyValuePair(HostKeyVerifier.TARGET, new HostKeyVerifier.Target(host.hostname(), host.port())))
                .verify(current.connectTimeout()).getSession();
            try {
                if (!current.keepalive().isZero()) session.setSessionHeartbeat(SessionHeartbeatController.HeartbeatType.IGNORE, current.keepalive());
                List<String> tried = new ArrayList<>();
                if (credential.isPresent()) {
                    Credential secret = credential.get();
                    if (secret.keyPath().isPresent()) {
                        String passphrase = secret.passphrase() == null ? null : new String(secret.passphrase());
                        try (var stream = Files.newInputStream(secret.keyPath().get())) {
                            Iterable<KeyPair> pairs = SecurityUtils.loadKeyPairIdentities(session, NamedResource.ofName(secret.keyPath().get().toString()), stream,
                                passphrase == null ? FilePasswordProvider.EMPTY : FilePasswordProvider.of(passphrase));
                            for (KeyPair pair : pairs) session.addPublicKeyIdentity(pair);
                        } catch (IOException | GeneralSecurityException unreadable) { throw new Failures.Failure("Could not read the key " + secret.keyPath().get().getFileName() + ": " + unreadable.getMessage()); }
                        tried.add("publickey");
                    }
                    if (secret.password() != null) { session.addPasswordIdentity(new String(secret.password())); tried.add("password"); }
                } else tried.add("publickey (agent)");
                status.accept("Authenticating…");
                try { session.auth().verify(current.authTimeout()); }
                catch (IOException denied) {
                    String rejection = session.getAttribute(HostKeyVerifier.REJECTION);
                    if (rejection != null) throw new Failures.Failure(rejection);
                    String text = String.valueOf(denied.getMessage());
                    if (text.contains("timeout") || text.contains("timed out")) throw new Failures.Failure("Timed out after " + current.authTimeout().toSeconds() + " s");
                    throw new Failures.Failure("Authentication failed (tried " + String.join(", ", tried) + ")");
                }
                return session;
            } catch (Exception failure) {
                session.close(false);
                throw failure;
            }
        } catch (Exception failure) {
            String rejection = failure instanceof Failures.Failure ? null : rejectionOf(failure);
            throw rejection != null ? new Failures.Failure(rejection, failure) : failure;
        } finally {
            credential.ifPresent(Credential::close);
        }
    }

    /** A host-key refusal surfaces as a connect failure whose session carried the reason. */
    private static String rejectionOf(Throwable failure) {
        String text = String.valueOf(failure.getMessage());
        return text.contains("Key exchange") || text.contains("verification") ? "Host key rejected" : null;
    }

    private SshClient client(boolean withAgent) {
        if (withAgent) {
            if (agentClient == null) { agentClient = SshClient.setUpDefaultClient(); agentClient.setServerKeyVerifier(verifier); agent.ifPresent(a -> agentClient.setAgentFactory(new MinaAgentFactory(a))); agentClient.start(); }
            return agentClient;
        }
        if (plainClient == null) { plainClient = SshClient.setUpDefaultClient(); plainClient.setServerKeyVerifier(verifier); plainClient.start(); }
        return plainClient;
    }

    // ---- bookkeeping (UI thread)

    private void fail(Shared shared, Throwable failure) {
        if (sessions.get(shared.hostId) == shared) sessions.remove(shared.hostId);
        shared.ready.completeExceptionally(failure);
    }

    private void withdraw(UUID hostId) {
        Shared shared = sessions.get(hostId);
        if (shared == null || shared.ready.isDone()) return;
        shared.waiters--;
        if (shared.waiters <= 0) { sessions.remove(hostId); shared.ready.completeExceptionally(new Failures.Failure("Cancelled")); }
    }

    private void release(UUID hostId) {
        channels = Math.max(0, channels - 1);
        Shared shared = sessions.get(hostId);
        if (shared != null) {
            shared.refs = Math.max(0, shared.refs - 1);
            if (shared.refs == 0 && shared.waiters <= 0 && shared.cancelLinger == null)
                shared.cancelLinger = schedule.apply(settings.get().linger(), () -> ui.execute(() -> { if (sessions.get(hostId) == shared && shared.refs == 0 && shared.waiters <= 0) evict(shared, "idle"); }));
        }
        notifyChanged();
    }

    private void evict(Shared shared, String why) {
        if (sessions.get(shared.hostId) == shared) sessions.remove(shared.hostId);
        if (shared.cancelLinger != null) { shared.cancelLinger.run(); shared.cancelLinger = null; }
        if (!shared.ready.isDone()) shared.ready.completeExceptionally(new Failures.Failure("Connection lost"));
        ClientSession session = shared.session;
        SshdSocketAddress forward = shared.forward; ClientSession owner = shared.forwardOwner;
        background.execute(() -> {
            try { if (session != null) session.close(false); } catch (RuntimeException ignored) { }
            try { if (forward != null && owner != null && owner.isOpen()) owner.stopLocalPortForwarding(forward); } catch (IOException | RuntimeException ignored) { }
        });
        LOG.log(System.Logger.Level.DEBUG, "Session for {0} closed: {1}", shared.hostId, why);
        notifyChanged();
    }

    private void notifyChanged() { listeners.forEach(Runnable::run); }

    private <T> CompletableFuture<T> onBackground(Callable<T> work) {
        var result = new CompletableFuture<T>();
        background.execute(() -> {
            T value;
            try { value = work.call(); } catch (Throwable failure) { result.completeExceptionally(failure); return; }
            result.complete(value);
        });
        return result;
    }
}
```

Notes for the executor:
- The channel reference is taken (`refs++`) before the channel opens and released on open failure, so a session never lingers while a channel is being opened. `waiters` counts requests still waiting for `ready`; `withdraw` drops the last one and fails the pending connect, whose late `connect` result is then closed by the `fresh.ready.isDone()` guard.
- MINA's `session.auth().verify` throws an `SshException` for a host-key refusal *or* for authentication; the `REJECTION` attribute distinguishes them. If a refusal surfaces from `connect(...).verify(...)` instead (key exchange failing before `auth`), `rejectionOf` and the attribute are consulted in that order: read `REJECTION` from the connect future's session when it is available (`future.getSession()` may be non-null on failure), else fall back to `rejectionOf`. Adjust the message so `hostKeyDecisionsAndChangesAreEnforced` passes with the exact spec text.
- `FilePasswordProvider.EMPTY` exists in 2.19; if not, pass `null` for an absent passphrase.
- The `channelCount` test expects one channel per shell: `channels++` only after the channel opened.

- [x] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test -q`
Expected: PASS. These tests run real MINA threads; allow up to a minute. Flakiness candidates: the 200 ms sleeps around eviction — replace with a poll loop (`Await`-style, up to 5 s) if a run fails only there.

- [x] **Step 7: Commit**

```bash
git add plugins/remote
git commit -m "feat(remote): shared MINA sessions per host with the connect pipeline, ProxyJump and shell channels"
```

---

### Task 6: The hosts panel, host editor, import and host-key dialogs

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/ui/package-info.java`, `HostRows.java`, `HostsPanel.java`, `HostEditor.java`, `ImportPanel.java`, `HostKeyPanel.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/ui/HostRowsTest.java`, `HostsPanelTest.java`, `HostEditorTest.java`, `ImportPanelTest.java`, `HostKeyPanelTest.java`

**Interfaces:**
- Consumes: `RemoteHost`, `Auth`, `ConfigImport.Candidate`, `SshConfig.Parsed`, `HostKeyVerifier.Question/Decision`, `dev.jasper.vault.api.CredentialDescriptor`.
- Produces: sealed `HostRows.Row` { `Group(String name, int count, boolean collapsed)`, `Host(RemoteHost host)` }, `HostRows.rows(List<RemoteHost>, String query, Set<String> collapsed) -> List<Row>`, `HostRows.matches(RemoteHost, String query)`, `HostRows.OTHER = "Other"`; `HostsPanel(HostsPanel.Actions)` with `Actions(Consumer<RemoteHost> connect, Consumer<RemoteHost> connectSplit, Consumer<Optional<RemoteHost>> edit, Consumer<RemoteHost> duplicate, Consumer<RemoteHost> delete, BiConsumer<RemoteHost, Boolean> favorite, Runnable importConfig)`, `setHosts(List<RemoteHost>, Optional<String> error)`, `setCredentialLabels(Function<RemoteHost, String>)`, `select(UUID)`, `Set<String> collapsed()`, `setCollapsed(Set<String>)`, `Subscription onCollapsedChanged(Runnable)` (a `Runnable` list), package-private `search`, `list`, `card`, `cardName`, `cardAddress`, `cardCredential`, `cardJump`, `connect`, `edit`, `add`, `importButton`, `empty`, `activate(int index)`, `toggle(int index)`, `menuFor(int index)`; `HostEditor(List<RemoteHost> others, Optional<RemoteHost> editing, boolean vaultPresent, Function<UUID, Optional<String>> credentialName, Supplier<CompletableFuture<Optional<CredentialDescriptor>>> pick, Consumer<RemoteHost> save, Runnable cancel)` with fields `name, hostname, port, username, group, favorite, vaultAuth, agentAuth, choose, credentialLabel, jump, save, cancel, message`; `ImportPanel(List<ConfigImport.Candidate>, List<String> skipped, Consumer<List<RemoteHost>> importSelected, Runnable cancel)` with `checks`, `importButton`, `cancel`; `HostKeyPanel(HostKeyVerifier.Question, Consumer<HostKeyVerifier.Decision>)` with `cancel`, `once`, `trust`, `text`.

- [x] **Step 1: Write the failing tests**

`ui/HostRowsTest.java`:

```java
package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HostRowsTest {
    static RemoteHost host(String name, String group, boolean favorite) {
        return RemoteHost.create(name, name + ".example", 22, "u", Auth.AGENT, group, Optional.empty()).withFavorite(favorite);
    }

    final List<RemoteHost> hosts = List.of(host("web", "Production", false), host("api", "Production", true), host("nas", "", false), host("app", "Staging", false));

    @Test void groupsSortFavoritesFirstAndOtherLast() {
        List<HostRows.Row> rows = HostRows.rows(hosts, "", Set.of());
        assertThat(rows).extracting(HostRowsTest::describe).containsExactly("Production(2)", "api*", "web", "Staging(1)", "app", "Other(1)", "nas");
    }

    @Test void collapsedGroupsHideTheirHostsUnlessSearching() {
        assertThat(HostRows.rows(hosts, "", Set.of("Production", HostRows.OTHER))).extracting(HostRowsTest::describe).containsExactly("Production(2)-", "Staging(1)", "app", "Other(1)-");
        assertThat(HostRows.rows(hosts, "ap", Set.of("Production", "Staging"))).extracting(HostRowsTest::describe).containsExactly("Production(1)", "api*", "Staging(1)", "app");
        assertThat(HostRows.rows(hosts, "nothing", Set.of())).isEmpty();
        assertThat(HostRows.matches(hosts.get(2), "NAS.EX")).isTrue();
        assertThat(HostRows.matches(hosts.get(0), "u")).as("username matches").isTrue();
        assertThat(HostRows.matches(hosts.get(0), "prod")).as("group matches").isTrue();
    }

    static String describe(HostRows.Row row) {
        return switch (row) {
            case HostRows.Group group -> group.name() + "(" + group.count() + ")" + (group.collapsed() ? "-" : "");
            case HostRows.Host host -> host.host().name() + (host.host().favorite() ? "*" : "");
        };
    }
}
```

`ui/HostsPanelTest.java`:

```java
package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HostsPanelTest {
    final List<String> events = new ArrayList<>();
    final HostsPanel.Actions actions = new HostsPanel.Actions(h -> events.add("connect " + h.name()), h -> events.add("split " + h.name()),
        h -> events.add("edit " + h.map(RemoteHost::name).orElse("new")), h -> events.add("duplicate " + h.name()), h -> events.add("delete " + h.name()),
        (h, favorite) -> events.add("favorite " + h.name() + " " + favorite), () -> events.add("import"));
    final RemoteHost prod = RemoteHost.create("prod", "api.example", 22, "deploy", new Auth.Vault(UUID.randomUUID()), "Production", Optional.empty());
    final RemoteHost nas = RemoteHost.create("nas", "nas.local", 2222, "me", Auth.AGENT, "", Optional.of(prod.id()));

    @Test void showsRowsCardAndRunsActions() {
        var panel = new HostsPanel(actions);
        assertThat(panel.empty.isVisible()).isTrue();
        panel.setCredentialLabels(host -> host.auth() instanceof Auth.Agent ? "SSH agent" : "Work key");
        panel.setHosts(List.of(prod, nas), Optional.empty());
        assertThat(panel.empty.isVisible()).isFalse();
        assertThat(panel.list.getModel().getSize()).as("two groups, two hosts").isEqualTo(4);
        panel.select(nas.id());
        assertThat(panel.card.isVisible()).isTrue();
        assertThat(panel.cardName.getText()).isEqualTo("nas");
        assertThat(panel.cardAddress.getText()).isEqualTo("me@nas.local:2222");
        assertThat(panel.cardCredential.getText()).isEqualTo("SSH agent");
        assertThat(panel.cardJump.getText()).isEqualTo("via prod");
        panel.connect.doClick();
        panel.edit.doClick();
        panel.activate(panel.list.getSelectedIndex());
        panel.add.doClick();
        panel.importButton.doClick();
        assertThat(events).containsExactly("connect nas", "edit nas", "connect nas", "edit new", "import");
        events.clear();
        javax.swing.JPopupMenu menu = panel.menuFor(panel.list.getSelectedIndex());
        assertThat(menu.getComponentCount()).isEqualTo(6);
        ((javax.swing.JMenuItem) menu.getComponent(1)).doClick();
        ((javax.swing.JMenuItem) menu.getComponent(5)).doClick();
        assertThat(events).containsExactly("split nas", "favorite nas true");
        panel.select(prod.id());
        assertThat(panel.cardCredential.getText()).isEqualTo("Work key");
        assertThat(panel.cardJump.isVisible()).isFalse();
    }

    @Test void searchFiltersAndCollapseIsRemembered() {
        var panel = new HostsPanel(actions);
        panel.setHosts(List.of(prod, nas), Optional.empty());
        int[] collapsedChanges = {0};
        panel.onCollapsedChanged(() -> collapsedChanges[0]++);
        panel.toggle(0);
        assertThat(panel.collapsed()).containsExactly("Production");
        assertThat(panel.list.getModel().getSize()).isEqualTo(3);
        assertThat(collapsedChanges[0]).isEqualTo(1);
        panel.search.setText("prod");
        assertThat(panel.list.getModel().getSize()).as("search reveals the collapsed group").isEqualTo(2);
        assertThat(panel.collapsed()).as("without changing the saved state").containsExactly("Production");
        panel.search.setText("zzz");
        assertThat(panel.list.getModel().getSize()).isZero();
        assertThat(panel.empty.getText()).contains("No hosts match");
        panel.search.setText("");
        panel.setCollapsed(Set.of());
        assertThat(panel.list.getModel().getSize()).isEqualTo(4);
        panel.setHosts(List.of(prod, nas), Optional.of("hosts.toml has TOML errors"));
        assertThat(panel.list.getModel().getSize()).as("an error row leads").isEqualTo(5);
        assertThat(panel.list.getModel().getElementAt(0)).isInstanceOf(HostRows.Error.class);
    }
}
```

`ui/HostEditorTest.java`:

```java
package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HostEditorTest {
    final List<RemoteHost> saved = new ArrayList<>();
    final RemoteHost bastion = RemoteHost.create("bastion", "b.example", 22, "ops", Auth.AGENT, "Homelab", Optional.empty());
    final CredentialDescriptor login = new CredentialDescriptor(UUID.randomUUID(), "Work login", "deploy", Kind.ACCOUNT_PASSWORD);

    @Test void createsAHostWithAVaultCredentialAndAJump() {
        var editor = new HostEditor(List.of(bastion), Optional.empty(), true, id -> id.equals(login.id()) ? Optional.of(login.name()) : Optional.empty(),
            () -> CompletableFuture.completedFuture(Optional.of(login)), saved::add, () -> { });
        assertThat(editor.port.getText()).isEqualTo("22");
        assertThat(editor.vaultAuth.isSelected()).isTrue();
        assertThat(editor.jump.getItemCount()).as("none + bastion").isEqualTo(2);
        editor.save.doClick();
        assertThat(editor.message.getText()).contains("name");
        editor.name.setText("prod"); editor.hostname.setText("api.example"); editor.port.setText("2222");
        editor.save.doClick();
        assertThat(editor.message.getText()).contains("credential");
        editor.choose.doClick();
        assertThat(editor.credentialLabel.getText()).isEqualTo("Work login");
        editor.group.setSelectedItem("Production");
        editor.jump.setSelectedIndex(1);
        editor.favorite.setSelected(true);
        editor.save.doClick();
        assertThat(saved).singleElement().satisfies(host -> {
            assertThat(host.name()).isEqualTo("prod");
            assertThat(host.port()).isEqualTo(2222);
            assertThat(host.auth()).isEqualTo(new Auth.Vault(login.id()));
            assertThat(host.username()).as("the login supplies it").isEmpty();
            assertThat(host.group()).isEqualTo("Production");
            assertThat(host.jump()).contains(bastion.id());
            assertThat(host.favorite()).isTrue();
        });
    }

    @Test void editsKeepTheIdAndAgentNeedsAUsername() {
        boolean[] cancelled = {false};
        var editor = new HostEditor(List.of(), Optional.of(bastion), false, id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()), saved::add, () -> cancelled[0] = true);
        assertThat(editor.name.getText()).isEqualTo("bastion");
        assertThat(editor.vaultAuth.isEnabled()).as("no Vault installed").isFalse();
        assertThat(editor.agentAuth.isSelected()).isTrue();
        assertThat(editor.group.getSelectedItem()).isEqualTo("Homelab");
        editor.username.setText("");
        editor.save.doClick();
        assertThat(editor.message.getText()).contains("username");
        editor.username.setText("root"); editor.port.setText("abc");
        editor.save.doClick();
        assertThat(editor.message.getText()).contains("port");
        editor.port.setText("22");
        editor.save.doClick();
        assertThat(saved).singleElement().satisfies(host -> { assertThat(host.id()).isEqualTo(bastion.id()); assertThat(host.username()).isEqualTo("root"); assertThat(host.created()).isEqualTo(bastion.created()); });
        editor.cancel.doClick();
        assertThat(cancelled[0]).isTrue();
    }
}
```

`ui/ImportPanelTest.java`:

```java
package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.ConfigImport;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.hosts.SshConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ImportPanelTest {
    static ConfigImport.Candidate candidate(String name, boolean exists, String... notes) {
        var entry = new SshConfig.Entry(name, Optional.empty(), OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        return new ConfigImport.Candidate(entry, RemoteHost.create(name, name, 22, "u", Auth.AGENT, "", Optional.empty()), exists, List.of(notes));
    }

    @Test void checksNewEntriesByDefaultAndImportsTheChecked() {
        List<RemoteHost> imported = new ArrayList<>();
        var panel = new ImportPanel(List.of(candidate("a", false), candidate("b", true, "key not in the vault: uses the agent")), List.of("Host *.internal (pattern)"), imported::addAll, () -> { });
        assertThat(panel.checks).hasSize(2);
        assertThat(panel.checks.get(0).isSelected()).isTrue();
        assertThat(panel.checks.get(1).isSelected()).as("exists: unchecked").isFalse();
        assertThat(panel.checks.get(1).getText()).contains("b", "exists", "key not in the vault");
        assertThat(panel.skipped.getText()).contains("*.internal");
        panel.importButton.doClick();
        assertThat(imported).extracting(RemoteHost::name).containsExactly("a");
    }
}
```

`ui/HostKeyPanelTest.java`:

```java
package dev.jasper.remote.ui;

import dev.jasper.remote.client.HostKeyVerifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HostKeyPanelTest {
    @Test void showsTheKeyAndReportsTheDecision() {
        List<HostKeyVerifier.Decision> decisions = new ArrayList<>();
        var panel = new HostKeyPanel(new HostKeyVerifier.Question("api.example", 2222, "ssh-ed25519", "SHA256:abc"), decisions::add);
        assertThat(panel.text.getText()).contains("api.example", "2222", "ssh-ed25519", "SHA256:abc");
        panel.once.doClick(); panel.trust.doClick(); panel.cancel.doClick();
        assertThat(decisions).containsExactly(HostKeyVerifier.Decision.ONCE, HostKeyVerifier.Decision.TRUST, HostKeyVerifier.Decision.CANCEL);
    }
}
```

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test -q`
Expected: compilation failure.

- [x] **Step 3: Implement**

`ui/package-info.java`:

```java
/** Swing content for the hosts panel and the plugin's dialogs; the host supplies windows and dialogs. */
package dev.jasper.remote.ui;
```

`ui/HostRows.java`:

```java
package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.RemoteHost;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

/** The panel's list model: group headers with counts and hosts, favorites first, "Other" last. */
public final class HostRows {
    public static final String OTHER = "Other";

    public sealed interface Row { }
    public record Group(String name, int count, boolean collapsed) implements Row { }
    public record Host(RemoteHost host) implements Row { }
    public record Error(String message) implements Row { }

    private HostRows() { }

    public static boolean matches(RemoteHost host, String query) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;
        return (host.name() + " " + host.hostname() + " " + host.username() + " " + host.group()).toLowerCase(Locale.ROOT).contains(q);
    }

    /** Collapsed groups hide their hosts unless a search is active; the search never changes {@code collapsed}. */
    public static List<Row> rows(List<RemoteHost> hosts, String query, Set<String> collapsed) {
        boolean searching = !query.strip().isEmpty();
        var groups = new TreeMap<String, List<RemoteHost>>(Comparator.comparing((String name) -> name.equals(OTHER) ? 1 : 0).thenComparing(name -> name.toLowerCase(Locale.ROOT)));
        for (RemoteHost host : hosts) {
            if (!matches(host, query)) continue;
            groups.computeIfAbsent(host.group().isEmpty() ? OTHER : host.group(), name -> new ArrayList<>()).add(host);
        }
        var rows = new ArrayList<Row>();
        for (var entry : groups.entrySet()) {
            boolean hidden = !searching && collapsed.contains(entry.getKey());
            rows.add(new Group(entry.getKey(), entry.getValue().size(), hidden));
            if (hidden) continue;
            entry.getValue().stream().sorted(Comparator.comparing((RemoteHost host) -> !host.favorite()).thenComparing(host -> host.name().toLowerCase(Locale.ROOT)))
                .forEach(host -> rows.add(new Host(host)));
        }
        return List.copyOf(rows);
    }
}
```

`ui/HostsPanel.java`:

```java
package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.Subscription;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** The "SSH hosts" rail panel: search, grouped hosts, the selected host's card with Connect. One per window. */
public final class HostsPanel extends JPanel {
    public record Actions(Consumer<RemoteHost> connect, Consumer<RemoteHost> connectSplit, Consumer<Optional<RemoteHost>> edit, Consumer<RemoteHost> duplicate,
                          Consumer<RemoteHost> delete, BiConsumer<RemoteHost, Boolean> favorite, Runnable importConfig) { }

    final JTextField search = new JTextField();
    final JList<HostRows.Row> list;
    final JPanel card = new JPanel();
    final JLabel cardName = new JLabel(), cardAddress = new JLabel(), cardCredential = new JLabel(), cardJump = new JLabel();
    final JButton connect = new JButton("Connect"), edit = new JButton("Edit"), add = new JButton("+"), importButton = new JButton("Import");
    final JLabel empty = new JLabel("No hosts yet — Add or Import from ~/.ssh/config");
    private final Actions actions;
    private final DefaultListModel<HostRows.Row> model = new DefaultListModel<>();
    private final Set<String> collapsed = new HashSet<>();
    private final List<Runnable> collapseListeners = new ArrayList<>();
    private List<RemoteHost> hosts = List.of();
    private Optional<String> error = Optional.empty();
    private Function<RemoteHost, String> credentialLabels = host -> "";
    private UUID selected;

    public HostsPanel(Actions actions) {
        super(new BorderLayout(0, 6));
        this.actions = actions;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        var header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        var title = new JLabel("SSH hosts");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        header.add(title); header.add(Box.createHorizontalGlue()); header.add(importButton); header.add(Box.createHorizontalStrut(4)); header.add(add);
        var north = new JPanel(new BorderLayout(0, 6));
        north.add(header, BorderLayout.NORTH);
        search.putClientProperty("JTextField.placeholderText", "Search hosts…");
        north.add(search, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        var center = new JPanel(new BorderLayout());
        center.add(new JScrollPane(list), BorderLayout.CENTER);
        empty.setBorder(BorderFactory.createEmptyBorder(12, 4, 12, 4));
        center.add(empty, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        cardName.setFont(cardName.getFont().deriveFont(Font.BOLD));
        card.add(cardName); card.add(cardAddress); card.add(cardCredential); card.add(cardJump);
        var buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(edit); buttons.add(Box.createHorizontalGlue()); buttons.add(connect);
        card.add(Box.createVerticalStrut(6)); card.add(buttons);
        card.setVisible(false);
        add(card, BorderLayout.SOUTH);

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { rebuild(); }
            @Override public void removeUpdate(DocumentEvent e) { rebuild(); }
            @Override public void changedUpdate(DocumentEvent e) { rebuild(); }
        });
        list.addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) selectionChanged(); });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                int index = list.locationToIndex(event.getPoint());
                if (index < 0) return;
                if (SwingUtilities.isRightMouseButton(event)) { list.setSelectedIndex(index); JPopupMenu menu = menuFor(index); if (menu != null) menu.show(list, event.getX(), event.getY()); }
                else if (event.getClickCount() == 2) activate(index);
                else if (model.get(index) instanceof HostRows.Group) toggle(index);
            }
        });
        add.addActionListener(event -> actions.edit().accept(Optional.empty()));
        importButton.addActionListener(event -> actions.importConfig().run());
        connect.addActionListener(event -> selectedHost().ifPresent(actions.connect()));
        edit.addActionListener(event -> selectedHost().ifPresent(host -> actions.edit().accept(Optional.of(host))));
        rebuild();
    }

    public void setHosts(List<RemoteHost> hosts, Optional<String> error) { this.hosts = List.copyOf(hosts); this.error = error; rebuild(); }
    public void setCredentialLabels(Function<RemoteHost, String> labels) { credentialLabels = labels; refreshCard(); }
    public Set<String> collapsed() { return Set.copyOf(collapsed); }
    public void setCollapsed(Set<String> groups) { collapsed.clear(); collapsed.addAll(groups); rebuild(); }
    public Subscription onCollapsedChanged(Runnable listener) { collapseListeners.add(listener); return () -> collapseListeners.remove(listener); }

    public void select(UUID hostId) {
        for (int i = 0; i < model.size(); i++) if (model.get(i) instanceof HostRows.Host row && row.host().id().equals(hostId)) { list.setSelectedIndex(i); return; }
    }

    Optional<RemoteHost> selectedHost() { return hosts.stream().filter(host -> host.id().equals(selected)).findFirst(); }

    /** Double-click or Enter on a row: connect to a host, toggle a group. */
    void activate(int index) {
        if (index < 0 || index >= model.size()) return;
        switch (model.get(index)) {
            case HostRows.Host row -> actions.connect().accept(row.host());
            case HostRows.Group group -> toggle(index);
            case HostRows.Error ignored -> { }
        }
    }

    void toggle(int index) {
        if (!(model.get(index) instanceof HostRows.Group group)) return;
        if (!collapsed.remove(group.name())) collapsed.add(group.name());
        collapseListeners.forEach(Runnable::run);
        rebuild();
    }

    JPopupMenu menuFor(int index) {
        if (index < 0 || !(model.get(index) instanceof HostRows.Host row)) return null;
        RemoteHost host = row.host();
        var menu = new JPopupMenu();
        menu.add(item("Connect", () -> actions.connect().accept(host)));
        menu.add(item("Connect in split", () -> actions.connectSplit().accept(host)));
        menu.add(item("Edit…", () -> actions.edit().accept(Optional.of(host))));
        menu.add(item("Duplicate", () -> actions.duplicate().accept(host)));
        menu.add(item("Delete…", () -> actions.delete().accept(host)));
        menu.add(item(host.favorite() ? "Remove from favorites" : "Add to favorites", () -> actions.favorite().accept(host, !host.favorite())));
        return menu;
    }

    private static JMenuItem item(String title, Runnable action) { var item = new JMenuItem(title); item.addActionListener(event -> action.run()); return item; }

    private void rebuild() {
        UUID keep = selected;
        model.clear();
        error.ifPresent(message -> model.addElement(new HostRows.Error(message)));
        HostRows.rows(hosts, search.getText(), collapsed).forEach(model::addElement);
        boolean searching = !search.getText().strip().isEmpty();
        empty.setText(hosts.isEmpty() ? "No hosts yet — Add or Import from ~/.ssh/config" : "No hosts match");
        empty.setVisible(hosts.isEmpty() || (searching && model.size() == (error.isPresent() ? 1 : 0)));
        if (keep != null) select(keep);
        selectionChanged();
    }

    private void selectionChanged() {
        HostRows.Row row = list.getSelectedValue();
        selected = row instanceof HostRows.Host host ? host.host().id() : selected;
        if (row instanceof HostRows.Host) refreshCard();
    }

    private void refreshCard() {
        Optional<RemoteHost> host = selectedHost();
        card.setVisible(host.isPresent());
        host.ifPresent(h -> {
            cardName.setText(h.name());
            cardAddress.setText(h.label());
            cardCredential.setText(credentialLabels.apply(h));
            String via = h.jump().flatMap(id -> hosts.stream().filter(other -> other.id().equals(id)).findFirst()).map(RemoteHost::name).orElse(null);
            cardJump.setVisible(via != null);
            cardJump.setText(via == null ? "" : "via " + via);
        });
    }

    private static final class Renderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
            switch (value) {
                case HostRows.Group group -> { label.setText((group.collapsed() ? "▸ " : "▾ ") + group.name() + "  " + group.count()); label.setFont(label.getFont().deriveFont(Font.BOLD)); }
                case HostRows.Host host -> label.setText("    " + host.host().name() + (host.host().favorite() ? "  ★" : ""));
                case HostRows.Error error -> label.setText("⚠ " + error.message());
                default -> { }
            }
            return label;
        }
    }
}
```

`ui/HostEditor.java`:

```java
package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.vault.api.CredentialDescriptor;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
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

/** Add or edit one host. Validation is inline; Save hands back a {@link RemoteHost} and the dialog closes it. */
public final class HostEditor extends JPanel {
    final JTextField name = new JTextField(24), hostname = new JTextField(24), port = new JTextField("22", 6), username = new JTextField(16);
    final JComboBox<String> group = new JComboBox<>();
    final JCheckBox favorite = new JCheckBox("Favorite");
    final JRadioButton vaultAuth = new JRadioButton("Vault credential"), agentAuth = new JRadioButton("SSH agent");
    final JButton choose = new JButton("Choose…");
    final JLabel credentialLabel = new JLabel("none chosen");
    final JComboBox<Object> jump = new JComboBox<>();
    final JButton save, cancel = new JButton("Cancel");
    final JLabel message = new JLabel(" ");
    private UUID credentialId;

    public HostEditor(List<RemoteHost> others, Optional<RemoteHost> editing, boolean vaultPresent, Function<UUID, Optional<String>> credentialName,
                      Supplier<CompletableFuture<Optional<CredentialDescriptor>>> pick, Consumer<RemoteHost> onSave, Runnable onCancel) {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        save = new JButton(editing.isPresent() ? "Save" : "Add Host");
        group.setEditable(true);
        group.addItem("");
        new TreeSet<>(others.stream().map(RemoteHost::group).filter(g -> !g.isEmpty()).toList()).forEach(group::addItem);
        jump.addItem("none");
        others.stream().filter(other -> editing.map(host -> !host.id().equals(other.id())).orElse(true)).forEach(jump::addItem);
        jump.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value instanceof RemoteHost host ? host.name() : value, index, selected, focus);
            }
        });
        var authGroup = new ButtonGroup(); authGroup.add(vaultAuth); authGroup.add(agentAuth);
        vaultAuth.setEnabled(vaultPresent);
        if (!vaultPresent) { vaultAuth.setText("Vault credential (needs Credential Vault)"); }
        editing.ifPresentOrElse(host -> {
            name.setText(host.name()); hostname.setText(host.hostname()); port.setText(String.valueOf(host.port())); username.setText(host.username());
            group.setSelectedItem(host.group()); favorite.setSelected(host.favorite());
            switch (host.auth()) {
                case Auth.Vault vault -> { vaultAuth.setSelected(true); credentialId = vault.credentialId(); credentialLabel.setText(credentialName.apply(credentialId).orElse("credential missing")); }
                case Auth.Agent agent -> agentAuth.setSelected(true);
            }
            host.jump().ifPresent(id -> { for (int i = 1; i < jump.getItemCount(); i++) if (((RemoteHost) jump.getItemAt(i)).id().equals(id)) jump.setSelectedIndex(i); });
        }, () -> (vaultPresent ? vaultAuth : agentAuth).setSelected(true));

        var form = new JPanel(new GridBagLayout());
        var at = new GridBagConstraints();
        at.insets = new Insets(3, 3, 3, 3); at.anchor = GridBagConstraints.WEST; at.gridy = 0;
        row(form, at, "Name", name); row(form, at, "Hostname", hostname); row(form, at, "Port", port); row(form, at, "Username", username);
        row(form, at, "Group", group); row(form, at, "", favorite);
        var auth = new JPanel(); auth.setLayout(new BoxLayout(auth, BoxLayout.X_AXIS));
        auth.add(vaultAuth); auth.add(Box.createHorizontalStrut(6)); auth.add(choose); auth.add(Box.createHorizontalStrut(6)); auth.add(credentialLabel); auth.add(Box.createHorizontalStrut(12)); auth.add(agentAuth);
        row(form, at, "Authentication", auth); row(form, at, "Jump host", jump);
        add(form, BorderLayout.CENTER);
        var south = new JPanel(); south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(message);
        var buttons = new JPanel(); buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(Box.createHorizontalGlue()); buttons.add(cancel); buttons.add(Box.createHorizontalStrut(8)); buttons.add(save);
        south.add(buttons);
        add(south, BorderLayout.SOUTH);

        Runnable syncAuth = () -> choose.setEnabled(vaultAuth.isSelected());
        vaultAuth.addActionListener(e -> syncAuth.run()); agentAuth.addActionListener(e -> syncAuth.run()); syncAuth.run();
        choose.addActionListener(event -> pick.get().thenAccept(chosen -> chosen.ifPresent(descriptor -> { credentialId = descriptor.id(); credentialLabel.setText(descriptor.name()); })));
        cancel.addActionListener(event -> onCancel.run());
        save.addActionListener(event -> {
            try {
                int portValue;
                try { portValue = Integer.parseInt(port.getText().strip()); } catch (NumberFormatException bad) { throw new IllegalArgumentException("The port must be a number from 1 to 65535"); }
                Auth chosen;
                if (vaultAuth.isSelected()) { if (credentialId == null) throw new IllegalArgumentException("Choose a credential, or use the SSH agent"); chosen = new Auth.Vault(credentialId); }
                else chosen = Auth.AGENT;
                String groupValue = group.getEditor().getItem() == null ? "" : group.getEditor().getItem().toString();
                Optional<UUID> jumpValue = jump.getSelectedItem() instanceof RemoteHost target ? Optional.of(target.id()) : Optional.empty();
                if (name.getText().isBlank()) throw new IllegalArgumentException("A host needs a name");
                for (RemoteHost other : others)
                    if (other.name().equalsIgnoreCase(name.getText().strip()) && editing.map(host -> !host.id().equals(other.id())).orElse(true)) throw new IllegalArgumentException("A host named " + other.name() + " exists");
                RemoteHost host = editing.map(existing -> existing.withEdited(name.getText(), hostname.getText(), portValue, username.getText(), chosen, groupValue, jumpValue))
                    .orElseGet(() -> RemoteHost.create(name.getText(), hostname.getText(), portValue, username.getText(), chosen, groupValue, jumpValue));
                onSave.accept(favorite.isSelected() ? host.withFavorite(true) : host.withFavorite(false));
            } catch (IllegalArgumentException invalid) { message.setText(invalid.getMessage()); }
        });
    }

    private static void row(JPanel form, GridBagConstraints at, String label, java.awt.Component field) {
        at.gridx = 0; form.add(new JLabel(label), at); at.gridx = 1; form.add(field, at); at.gridy++;
    }
}
```

`ui/ImportPanel.java`:

```java
package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.ConfigImport;
import dev.jasper.remote.hosts.RemoteHost;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** "Import from ~/.ssh/config": one checkbox per entry with what it becomes; existing names start unchecked. */
public final class ImportPanel extends JPanel {
    final List<JCheckBox> checks = new ArrayList<>();
    final JButton importButton = new JButton("Import"), cancel = new JButton("Cancel");
    final JTextArea skipped = new JTextArea();

    public ImportPanel(List<ConfigImport.Candidate> candidates, List<String> skippedEntries, Consumer<List<RemoteHost>> importSelected, Runnable onCancel) {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        var rows = new JPanel(); rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        if (candidates.isEmpty()) rows.add(new JLabel("No importable Host entries found"));
        for (ConfigImport.Candidate candidate : candidates) {
            RemoteHost host = candidate.host();
            var notes = new ArrayList<String>(candidate.notes());
            if (candidate.exists()) notes.addFirst("exists");
            var check = new JCheckBox(host.name() + "  —  " + host.label() + (notes.isEmpty() ? "" : "  (" + String.join("; ", notes) + ")"), !candidate.exists());
            checks.add(check); rows.add(check);
        }
        add(new JScrollPane(rows), BorderLayout.CENTER);
        skipped.setEditable(false); skipped.setLineWrap(true);
        skipped.setText(skippedEntries.isEmpty() ? "" : "Not importable: " + String.join(", ", skippedEntries));
        var south = new JPanel(); south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(skipped);
        var buttons = new JPanel(); buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(Box.createHorizontalGlue()); buttons.add(cancel); buttons.add(Box.createHorizontalStrut(8)); buttons.add(importButton);
        south.add(buttons);
        add(south, BorderLayout.SOUTH);
        importButton.setEnabled(!candidates.isEmpty());
        importButton.addActionListener(event -> {
            var chosen = new ArrayList<RemoteHost>();
            for (int i = 0; i < checks.size(); i++) if (checks.get(i).isSelected()) chosen.add(candidates.get(i).host());
            importSelected.accept(List.copyOf(chosen));
        });
        cancel.addActionListener(event -> onCancel.run());
    }
}
```

`ui/HostKeyPanel.java`:

```java
package dev.jasper.remote.ui;

import dev.jasper.remote.client.HostKeyVerifier;
import java.awt.BorderLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** The unknown-host-key question: Cancel, Connect once, Trust and connect. */
public final class HostKeyPanel extends JPanel {
    final JLabel text;
    final JButton cancel = new JButton("Cancel"), once = new JButton("Connect once"), trust = new JButton("Trust and connect");

    public HostKeyPanel(HostKeyVerifier.Question question, Consumer<HostKeyVerifier.Decision> decide) {
        super(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        text = new JLabel("<html>The authenticity of <b>" + escape(question.host()) + "</b> port " + question.port() + " can't be established.<br>"
            + escape(question.keyType()) + " key fingerprint: <code>" + escape(question.fingerprint()) + "</code><br>Compare it with the server's before trusting.</html>");
        add(text, BorderLayout.CENTER);
        var buttons = new JPanel(); buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(cancel); buttons.add(Box.createHorizontalGlue()); buttons.add(once); buttons.add(Box.createHorizontalStrut(8)); buttons.add(trust);
        add(buttons, BorderLayout.SOUTH);
        cancel.addActionListener(event -> decide.accept(HostKeyVerifier.Decision.CANCEL));
        once.addActionListener(event -> decide.accept(HostKeyVerifier.Decision.ONCE));
        trust.addActionListener(event -> decide.accept(HostKeyVerifier.Decision.TRUST));
    }

    static String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
```

- [x] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test -q`
Expected: PASS. `HostsPanelTest` counts the popup's items (6) and clicks the second (Connect in split) and sixth (favorite toggle); `HostRows.Error` rows never select.

- [x] **Step 5: Commit**

```bash
git add plugins/remote
git commit -m "feat(remote): hosts panel, host editor, import and host-key dialog panels"
```

---

### Task 7: The plugin: session provider, scope, actions, menu, status item, dialogs

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`, `PanelState.java`, `plugins/remote/src/main/java/dev/jasper/remote/ui/RemoteScope.java`, `ConfirmPanel.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/trust/KnownHosts.java` (a `Supplier<Optional<Path>>` constructor)
- Create: `plugins/remote/src/main/resources/dev/jasper/remote/server.svg`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/FakeVault.java`, `RemotePluginTest.java`, `plugins/remote/src/test/java/dev/jasper/remote/ui/RemoteScopeTest.java`

**Interfaces:**
- Consumes: everything above; SDK `Plugin`, `PluginContext`, `Panels`, `PanelSpec`, `PanelHost`, `Menus`, `StatusBar`, `Windows`, `Palette`, `Terminals`, `TerminalEvents.ACTIVE_PANE_CHANGED`/`PANE_CLOSED`, `VaultApi`.
- Produces: `RemotePlugin()` and the test constructor `RemotePlugin(Executor ui, Function<PluginContext, Optional<AgentClient>> agent, BiFunction<Duration, Runnable, Runnable> schedule, Path sshDir)`; constants `CONNECT = "dev.jasper.remote.connect"`, `HOSTS = "dev.jasper.remote.hosts"`, `SPLIT = "dev.jasper.remote.split"`, `IMPORT = "dev.jasper.remote.import"`, `STATUS = "dev.jasper.remote.status"`, `MENU = "dev.jasper.remote.menu"`, `PANEL = "dev.jasper.remote.panel"`; package-private `store()`, `connections()`, `askHostKey(Question)`, `currentHostKeyPanel()`, `currentEditor()`, `currentImport()`, `currentConfirm()`; `RemoteScope(Supplier<List<RemoteHost>> hosts, Supplier<Optional<String>> error, BiConsumer<WindowHandle, RemoteHost> connect, BiConsumer<PaneHandle, RemoteHost> connectSplit, BiConsumer<WindowHandle, RemoteHost> edit)` with `ID = "dev.jasper.remote.scope"`, verbs `CONNECT`, `SPLIT`, `EDIT`, `changed()`; `PanelState(Path file)` with `Set<String> collapsed()`, `save(Set<String>)`; `ConfirmPanel(String text, String verb, Runnable confirm, Runnable cancel)` with `confirm`, `cancel`.

- [x] **Step 1: Write the fake Vault and the failing tests**

`FakeVault.java` (test source):

```java
package dev.jasper.remote;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.Kind;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.VaultApi;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** A stand-in Vault plugin: always unlocked, every credential granted, {@code pick} answers the first descriptor. */
public final class FakeVault implements Plugin {
    public static final PluginInfo INFO = new PluginInfo("dev.jasper.vault", "Credential Vault", "0.1.0", Set.of());
    public final Map<UUID, CredentialDescriptor> descriptors = new LinkedHashMap<>();
    public final Map<UUID, java.util.function.Supplier<Credential>> secrets = new LinkedHashMap<>();
    public final List<String> consumers = new ArrayList<>();
    public LockState state = LockState.UNLOCKED;

    public UUID password(String name, String username, String password) {
        UUID id = UUID.randomUUID();
        descriptors.put(id, new CredentialDescriptor(id, name, username, Kind.ACCOUNT_PASSWORD));
        secrets.put(id, () -> new Credential(id, name, Kind.ACCOUNT_PASSWORD, username, password.toCharArray(), null, null));
        return id;
    }

    public UUID key(String name, String fingerprint, java.nio.file.Path privatePath) {
        UUID id = UUID.randomUUID();
        descriptors.put(id, new CredentialDescriptor(id, name, fingerprint, Kind.SSH_KEY));
        secrets.put(id, () -> new Credential(id, name, Kind.SSH_KEY, null, null, privatePath, null));
        return id;
    }

    @Override public void start(PluginContext context) {
        context.services().publishPerConsumer(VaultApi.class, consumer -> {
            consumers.add(consumer.id());
            return new VaultApi() {
                @Override public LockState lockState() { return state; }
                @Override public CompletableFuture<Boolean> ensureUnlocked(WindowHandle owner) { return CompletableFuture.completedFuture(state == LockState.UNLOCKED); }
                @Override public List<CredentialDescriptor> credentials() { return state == LockState.UNLOCKED ? List.copyOf(descriptors.values()) : List.of(); }
                @Override public CompletableFuture<Optional<Credential>> credential(UUID id) {
                    return CompletableFuture.completedFuture(state == LockState.UNLOCKED ? Optional.ofNullable(secrets.get(id)).map(java.util.function.Supplier::get) : Optional.empty());
                }
                @Override public CompletableFuture<Optional<CredentialDescriptor>> pick(WindowHandle owner) { return CompletableFuture.completedFuture(descriptors.values().stream().findFirst()); }
            };
        });
    }
}
```

`ui/RemoteScopeTest.java`:

```java
package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RemoteScopeTest {
    static WindowHandle window() {
        return new WindowHandle() {
            final UUID id = UUID.randomUUID();
            @Override public UUID id() { return id; }
            @Override public List<TabHandle> tabs() { return List.of(); }
            @Override public Optional<TabHandle> activeTab() { return Optional.empty(); }
            @Override public boolean isActive() { return true; }
            @Override public boolean isOpen() { return true; }
            @Override public void toFront() { }
        };
    }

    final List<String> events = new ArrayList<>();
    final RemoteHost prod = RemoteHost.create("prod", "api.example", 22, "deploy", Auth.AGENT, "Production", Optional.empty()).withFavorite(true);
    final RemoteHost nas = RemoteHost.create("nas", "nas.local", 22, "me", Auth.AGENT, "", Optional.empty());
    Optional<String> error = Optional.empty();
    final RemoteScope scope = new RemoteScope(() -> List.of(nas, prod), () -> error, (w, h) -> events.add("connect " + h.name()), (p, h) -> events.add("split " + h.name()), (w, h) -> events.add("edit " + h.name()));
    final PaletteQuery query = new PaletteQuery(window(), Optional.empty(), 5, true);

    @Test void rowsVerbsAndErrors() {
        assertThat(scope.spec().id()).isEqualTo(RemoteScope.ID);
        assertThat(scope.spec().aliases()).containsExactly("ssh", "remote", "hosts");
        assertThat(scope.spec().shortcutActionId()).contains("dev.jasper.remote.connect");
        List<PaletteRow> rows = scope.search("", query).rows();
        assertThat(rows).extracting(PaletteRow::title).as("favorites first").containsExactly("prod", "nas");
        assertThat(rows.getFirst().detail()).contains("deploy@api.example:22");
        assertThat(rows.getFirst().tag()).contains("Production");
        assertThat(scope.search("nas.lo", query).rows()).extracting(PaletteRow::title).containsExactly("nas");
        assertThat(scope.available(rows.getFirst(), RemoteScope.SPLIT, query)).as("no target pane").isFalse();
        assertThat(scope.available(rows.getFirst(), RemoteScope.CONNECT, query)).isTrue();
        scope.execute(rows.getFirst(), RemoteScope.CONNECT, query);
        scope.execute(rows.getFirst(), RemoteScope.EDIT, query);
        assertThat(events).containsExactly("connect prod", "edit prod");
        error = Optional.of("hosts.toml has TOML errors");
        List<PaletteRow> withError = scope.search("", query).rows();
        assertThat(withError.getFirst().title()).contains("hosts.toml has errors");
        assertThat(withError.getFirst().enabled()).isFalse();
    }
}
```

`RemotePluginTest.java`:

```java
package dev.jasper.remote;

import dev.jasper.remote.client.HostKeyVerifier;
import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.remote.ui.HostsPanel;
import dev.jasper.remote.ui.RemoteScope;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class RemotePluginTest {
    static final PluginInfo INFO = new PluginInfo("dev.jasper.remote", "Remote", "0.1.0",
        Set.of(Capabilities.TERMINAL_OPEN, Capabilities.SESSION_PROVIDE, Capabilities.TERMINAL_OBSERVE, Capabilities.PALETTE_CONTRIBUTE));

    final List<Runnable> scheduled = new ArrayList<>();

    RemotePlugin plugin(Path sshDir) {
        return new RemotePlugin(Runnable::run, context -> Optional.empty(), (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); }, sshDir);
    }

    static void settle(FakePluginHost host) { for (int i = 0; i < 20; i++) { host.runBackground(); host.flush(); } }

    @Test void registersItsSurfaceAndConnectsThroughTheScope(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var vault = new FakeVault();
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            RemotePlugin plugin = plugin(dir.resolve("ssh"));
            var context = host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            assertThat(host.failures()).isEmpty();
            assertThat(host.actions()).contains("dev.jasper.remote.connect|Connect to SSH Host...|true", "dev.jasper.remote.hosts|SSH Hosts|true",
                "dev.jasper.remote.split|Split with Same Host|false", "dev.jasper.remote.import|Import from ~/.ssh/config...|true");
            assertThat(host.panels()).containsExactly("dev.jasper.remote.panel|SSH hosts|LEFT");
            assertThat(host.scopes()).containsExactly("dev.jasper.remote.scope|SSH|connect,split,edit");
            assertThat(host.menu("top:dev.jasper.remote.menu")).isNotEmpty();
            assertThat(host.status()).as("hidden at zero").isEmpty();
            assertThat(vault.consumers).containsExactly("dev.jasper.remote");

            UUID credential = vault.password("deploy login", "deploy", "s3cret");
            RemoteHost prod = RemoteHost.create("prod", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "Production", Optional.empty());
            plugin.store().put(prod);
            settle(host);
            new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
            UUID window = host.addTerminalWindow();
            List<String> rows = host.searchScope(RemoteScope.ID, "", window, null);
            assertThat(rows).containsExactly("host." + prod.id() + "|prod|true");
            host.executeInScope(RemoteScope.ID, "host." + prod.id(), "connect", window, null);
            assertThat(host.openRequests()).containsExactly("session-tab|" + window + "|prod");
            UUID pane = host.terminalPanes().getFirst();
            settle(host);
            assertThat(host.sessionState(pane)).isEqualTo("RUNNING|");
            assertThat(host.status()).containsExactly("dev.jasper.remote.status|RIGHT|1 SSH session||dev.jasper.remote.hosts");
            Thread.sleep(300);
            assertThat(host.sessionOutput(pane)).startsWith("READY xterm-256color 80x24");
            host.focusTerminalPane(pane);
            host.flush();
            assertThat(host.actions()).contains("dev.jasper.remote.split|Split with Same Host|true");
            assertThat(host.invoke(RemotePlugin.SPLIT, window, pane)).isTrue();
            settle(host);
            assertThat(host.openRequests()).hasSize(2).last().asString().startsWith("split");
            assertThat(host.terminalPanes()).hasSize(2);
            assertThat(host.status().getFirst()).contains("2 SSH sessions");
            host.closeTerminalPane(pane);
            host.flush();
            assertThat(host.status().getFirst()).contains("1 SSH session");
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void hostKeyPromptIsAWindowModalDialog(@TempDir Path dir) {
        try (var host = new FakePluginHost()) {
            RemotePlugin plugin = plugin(dir);
            host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            host.addTerminalWindow();
            CompletableFuture<HostKeyVerifier.Decision> decision = plugin.askHostKey(new HostKeyVerifier.Question("h", 22, "ssh-ed25519", "SHA256:x"));
            assertThat(host.windows()).containsExactly("dialog|Verify host key h|true");
            plugin.currentHostKeyPanel().trust.doClick();
            assertThat(decision).isCompletedWithValue(HostKeyVerifier.Decision.TRUST);
            assertThat(host.windows()).isEmpty();
            CompletableFuture<HostKeyVerifier.Decision> closed = plugin.askHostKey(new HostKeyVerifier.Question("h", 22, "ssh-ed25519", "SHA256:x"));
            host.requestClose("dialog");
            assertThat(closed).isCompletedWithValue(HostKeyVerifier.Decision.CANCEL);
        }
    }

    @Test void thePanelAddsEditsDeletesAndImports(@TempDir Path dir) throws Exception {
        Path sshDir = Files.createDirectories(dir.resolve("ssh"));
        Files.writeString(sshDir.resolve("config"), "Host imported\n  HostName imported.example\n  User me\n");
        try (var host = new FakePluginHost()) {
            var vault = new FakeVault();
            host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
            RemotePlugin plugin = plugin(sshDir);
            host.start(INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
            UUID window = host.addTerminalWindow();
            var panel = (HostsPanel) host.openPanel(RemotePlugin.PANEL, window);
            assertThat(panel).isNotNull();
            panel.add.doClick();
            assertThat(host.windows()).containsExactly("dialog|Add Host|true");
            plugin.currentEditor().name.setText("new"); plugin.currentEditor().hostname.setText("new.example"); plugin.currentEditor().agentAuth.doClick();
            plugin.currentEditor().username.setText("root");
            plugin.currentEditor().save.doClick();
            settle(host);
            assertThat(host.windows()).isEmpty();
            assertThat(plugin.store().hosts()).extracting(RemoteHost::name).containsExactly("new");
            assertThat(panel.list.getModel().getSize()).as("Other group + host").isEqualTo(2);
            panel.select(plugin.store().hosts().getFirst().id());
            assertThat(panel.cardCredential.getText()).isEqualTo("SSH agent");
            assertThat(host.invoke(RemotePlugin.IMPORT, window, null)).isTrue();
            settle(host);
            assertThat(host.windows()).containsExactly("dialog|Import from ~/.ssh/config|true");
            plugin.currentImport().importButton.doClick();
            settle(host);
            assertThat(plugin.store().hosts()).extracting(RemoteHost::name).containsExactly("new", "imported");
            RemoteHost imported = plugin.store().hosts().get(1);
            javax.swing.JPopupMenu menu = panel.menuFor(indexOf(panel, imported));
            ((javax.swing.JMenuItem) menu.getComponent(4)).doClick();
            assertThat(host.windows()).containsExactly("dialog|Delete imported?|true");
            plugin.currentConfirm().confirm.doClick();
            settle(host);
            assertThat(plugin.store().hosts()).extracting(RemoteHost::name).containsExactly("new");
            panel.toggle(0);
            assertThat(Files.readString(dir.resolve("ssh").getParent().resolve("plugins")).isEmpty()).isFalse();
        }
    }

    static int indexOf(HostsPanel panel, RemoteHost host) {
        for (int i = 0; i < panel.list.getModel().getSize(); i++)
            if (panel.list.getModel().getElementAt(i) instanceof dev.jasper.remote.ui.HostRows.Host row && row.host().id().equals(host.id())) return i;
        throw new AssertionError("host row not shown");
    }
}
```

The last two lines of the third test are wrong as written (the fake host's data root is not under `dir`); replace them with a check through the plugin's data directory: after `panel.toggle(0)`, `settle(host)` and `assertThat(Files.readString(context.dataDirectory().resolve("panel-state.toml"))).contains("Other")`, capturing `context` from `host.start(...)`.

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test -q`
Expected: compilation failure.

- [x] **Step 3: Implement the small pieces**

`KnownHosts.java`: change the field to `private final Supplier<Optional<Path>> user;`, add `public KnownHosts(Path own, Supplier<Optional<Path>> user) { this.own = own; this.user = user; }`, make the existing constructor `this(own, () -> user)`, and use `user.get()` in `verify`.

`server.svg` (resources, `dev/jasper/remote/server.svg`):

```xml
<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#6e6e6e" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
  <rect x="3" y="4" width="18" height="8" rx="3" />
  <rect x="3" y="12" width="18" height="8" rx="3" />
  <path d="M7 8h.01M7 16h.01" />
</svg>
```

`PanelState.java`:

```java
package dev.jasper.remote;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

/** Which groups the hosts panel shows collapsed, in {@code data/panel-state.toml}; a broken file means "all expanded". */
final class PanelState {
    private final Path file;

    PanelState(Path file) { this.file = file; }

    Set<String> collapsed() {
        try {
            if (!Files.isRegularFile(file)) return Set.of();
            TomlParseResult toml = Toml.parse(Files.readString(file, StandardCharsets.UTF_8));
            var out = new LinkedHashSet<String>();
            if (toml.get("collapsed") instanceof TomlArray array) for (int i = 0; i < array.size(); i++) if (array.get(i) instanceof String name) out.add(name);
            return out;
        } catch (IOException | RuntimeException unreadable) { return Set.of(); }
    }

    void save(Set<String> groups) throws IOException {
        var out = new StringBuilder("# Jasper Remote panel state.\ncollapsed = [");
        boolean first = true;
        for (String group : groups) { if (!first) out.append(", "); first = false; out.append(dev.jasper.remote.hosts.HostFileAccess.tomlString(group)); }
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, out.append("]\n").toString(), StandardCharsets.UTF_8);
    }
}
```

`HostFile.tomlString` is package-private; make it `public static` in `HostFile` and call `HostFile.tomlString(group)` instead of the `HostFileAccess` name above.

`ui/ConfirmPanel.java`:

```java
package dev.jasper.remote.ui;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** "Delete prod?" with one verb button and Cancel. */
public final class ConfirmPanel extends JPanel {
    public final JButton confirm, cancel = new JButton("Cancel");

    public ConfirmPanel(String text, String verb, Runnable onConfirm, Runnable onCancel) {
        super(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        confirm = new JButton(verb);
        add(new JLabel(text), BorderLayout.CENTER);
        var buttons = new JPanel(); buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(Box.createHorizontalGlue()); buttons.add(cancel); buttons.add(Box.createHorizontalStrut(8)); buttons.add(confirm);
        add(buttons, BorderLayout.SOUTH);
        confirm.addActionListener(event -> onConfirm.run());
        cancel.addActionListener(event -> onCancel.run());
    }
}
```

`ui/RemoteScope.java`:

```java
package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** {@code >ssh}: saved hosts. Connect (Enter), Connect in split (Cmd/Ctrl+Enter), Edit host (Shift+Enter). */
public final class RemoteScope implements PaletteScope {
    public static final String ID = "dev.jasper.remote.scope";
    public static final PaletteVerb CONNECT = new PaletteVerb("connect", "Connect");
    public static final PaletteVerb SPLIT = new PaletteVerb("split", "Connect in split");
    public static final PaletteVerb EDIT = new PaletteVerb("edit", "Edit host…");
    static final String CONNECT_ACTION = "dev.jasper.remote.connect";
    static final String ERROR_ROW = "error";

    private final Supplier<List<RemoteHost>> hosts;
    private final Supplier<Optional<String>> error;
    private final BiConsumer<WindowHandle, RemoteHost> connect;
    private final BiConsumer<PaneHandle, RemoteHost> connectSplit;
    private final BiConsumer<WindowHandle, RemoteHost> edit;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public RemoteScope(Supplier<List<RemoteHost>> hosts, Supplier<Optional<String>> error, BiConsumer<WindowHandle, RemoteHost> connect,
                       BiConsumer<PaneHandle, RemoteHost> connectSplit, BiConsumer<WindowHandle, RemoteHost> edit) {
        this.hosts = hosts; this.error = error; this.connect = connect; this.connectSplit = connectSplit; this.edit = edit;
    }

    @Override public ScopeSpec spec() {
        return ScopeSpec.of(ID, "SSH", "Search saved hosts, or > to switch scope", List.of(CONNECT, SPLIT, EDIT))
            .withDescription("Connect to a saved SSH host").withAliases(List.of("ssh", "remote", "hosts")).withShortcutActionId(CONNECT_ACTION);
    }

    @Override public PaletteResults search(String query, PaletteQuery context) {
        var rows = new ArrayList<PaletteRow>();
        error.get().ifPresent(message -> rows.add(PaletteRow.of(ERROR_ROW, "hosts.toml has errors: " + message).withEnabled(false)));
        hosts.get().stream().filter(host -> HostRows.matches(host, query))
            .sorted(Comparator.comparing((RemoteHost host) -> !host.favorite()).thenComparing(host -> host.name().toLowerCase(Locale.ROOT)))
            .limit(context.maxResults())
            .forEach(host -> rows.add(PaletteRow.of("host." + host.id(), host.name()).withDetail(host.label()).withTag(host.group().isEmpty() ? null : host.group()).withToken(host)));
        return PaletteResults.of(rows);
    }

    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!(row.token() instanceof RemoteHost)) return false;
        return !verb.equals(SPLIT) || context.target().map(PaneHandle::isOpen).orElse(false);
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!(row.token() instanceof RemoteHost host)) return;
        if (verb.equals(EDIT)) edit.accept(context.window(), host);
        else if (verb.equals(SPLIT)) context.target().ifPresent(pane -> connectSplit.accept(pane, host));
        else connect.accept(context.window(), host);
    }

    @Override public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }
    public void changed() { listeners.forEach(Runnable::run); }
}
```

- [x] **Step 4: Implement the plugin**

`RemotePlugin.java`:

```java
package dev.jasper.remote;

import dev.jasper.remote.agent.AgentClient;
import dev.jasper.remote.client.Connections;
import dev.jasper.remote.client.HostKeyVerifier;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.ConfigImport;
import dev.jasper.remote.hosts.HostStore;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.hosts.SshConfig;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.remote.ui.ConfirmPanel;
import dev.jasper.remote.ui.HostEditor;
import dev.jasper.remote.ui.HostKeyPanel;
import dev.jasper.remote.ui.HostsPanel;
import dev.jasper.remote.ui.ImportPanel;
import dev.jasper.remote.ui.RemoteScope;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.Direction;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PendingSession;
import dev.jasper.sdk.terminal.SessionSpec;
import dev.jasper.sdk.terminal.TerminalEvents;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Anchor;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PanelHost;
import dev.jasper.sdk.ui.PanelSpec;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StatusItem;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.Kind;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.VaultApi;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.swing.Icon;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Wires the plugin: the hosts store and its poll, trust, the agent, the connection registry, the session
 * provider, the panel, the scope, the actions, the SSH menu, the status item and the dialogs.
 */
public class RemotePlugin implements Plugin {
    public static final String CONNECT = "dev.jasper.remote.connect", HOSTS = "dev.jasper.remote.hosts", SPLIT = "dev.jasper.remote.split";
    public static final String IMPORT = "dev.jasper.remote.import", STATUS = "dev.jasper.remote.status", MENU = "dev.jasper.remote.menu", PANEL = "dev.jasper.remote.panel";

    private final Executor ui;
    private final Function<PluginContext, Optional<AgentClient>> agentFactory;
    private final BiFunction<Duration, Runnable, Runnable> schedule;
    private final Path sshDir;
    private PluginContext context;
    private RemoteSettings settings;
    private HostStore store;
    private PanelState panelState;
    private KnownHosts trust;
    private Connections connections;
    private Optional<VaultApi> vault = Optional.empty();
    private RemoteScope scope;
    private StatusItem status;
    private PluginAction splitAction;
    private Timer poll;
    private Icon icon;
    private final Map<UUID, HostsPanel> panels = new HashMap<>();
    private final Map<UUID, PanelHost> panelHosts = new HashMap<>();
    private final Map<UUID, UUID> panes = new HashMap<>();
    private HostKeyPanel currentHostKeyPanel;
    private HostEditor currentEditor;
    private ImportPanel currentImport;
    private ConfirmPanel currentConfirm;

    public RemotePlugin() {
        this(SwingUtilities::invokeLater, context -> AgentClient.forEnvironment(System.getenv(), System.getProperty("os.name", "")),
            (delay, task) -> { var timer = new Timer((int) Math.max(1, delay.toMillis()), event -> task.run()); timer.setRepeats(false); timer.start(); return timer::stop; },
            Path.of(System.getProperty("user.home"), ".ssh"));
    }

    RemotePlugin(Executor ui, Function<PluginContext, Optional<AgentClient>> agentFactory, BiFunction<Duration, Runnable, Runnable> schedule, Path sshDir) {
        this.ui = ui; this.agentFactory = agentFactory; this.schedule = schedule; this.sshDir = sshDir;
    }

    @Override public void start(PluginContext context) throws Exception {
        this.context = context;
        Files.createDirectories(context.dataDirectory());
        settings = RemoteSettings.read(context.config());
        context.config().onChanged(() -> settings = RemoteSettings.read(context.config()));
        store = new HostStore(context.dataDirectory().resolve("hosts.toml"), context.background(), ui);
        panelState = new PanelState(context.dataDirectory().resolve("panel-state.toml"));
        trust = new KnownHosts(context.dataDirectory().resolve("known_hosts"), () -> settings.readUserKnownHosts() ? Optional.of(sshDir.resolve("known_hosts")) : Optional.empty());
        vault = context.services().find(VaultApi.class);
        connections = new Connections(() -> settings, trust, agentFactory.apply(context), store::host,
            vault.map(api -> id -> api.credential(id)), this::askHostKey, context.background(), ui, schedule);
        connections.onChanged(this::refreshStatus);
        icon = context.appearance().icon("dev/jasper/remote/server.svg");

        context.actions().register(ActionSpec.of(CONNECT, "Connect to SSH Host...").withIcon(icon).withKeywords(List.of("ssh", "remote", "host", "connect")).withDefaultBinding("cmd+shift+h"),
            invoked -> context.palette().open(invoked.window(), RemoteScope.ID, Optional.empty(), Optional.empty()));
        context.actions().register(ActionSpec.of(HOSTS, "SSH Hosts").withIcon(icon).withKeywords(List.of("ssh", "hosts", "panel")), invoked -> showPanel(invoked.window()));
        splitAction = context.actions().register(ActionSpec.of(SPLIT, "Split with Same Host").withKeywords(List.of("ssh", "split")), invoked -> invoked.pane().ifPresent(this::splitSameHost));
        splitAction.setEnabled(false);
        context.actions().register(ActionSpec.of(IMPORT, "Import from ~/.ssh/config...").withKeywords(List.of("ssh", "import", "config")), invoked -> importConfig(invoked.window()));
        PluginMenu menu = context.menus().create(MENU, "SSH");
        menu.add(CONNECT); menu.add(HOSTS); menu.add(SPLIT); menu.addSeparator(); menu.add(IMPORT);

        scope = new RemoteScope(store::hosts, store::error, this::openHost, this::splitHost, (window, host) -> editHost(window, Optional.of(host)));
        context.palette().register(scope);
        status = context.statusBar().add(new StatusItemSpec(STATUS, Side.RIGHT, 60));
        status.setIcon(icon); status.setAction(HOSTS); status.setVisible(false);
        context.panels().register(new PanelSpec(PANEL, "SSH hosts", icon, Anchor.LEFT), this::createPanel);

        store.onChanged(() -> { scope.changed(); refreshPanels(); store.warnings().forEach(warning -> context.log().log(System.Logger.Level.WARNING, "hosts.toml: {0}", warning)); });
        store.load();
        poll = new Timer(1000, event -> store.poll());
        poll.start();
        context.events().subscribe(TerminalEvents.ACTIVE_PANE_CHANGED, event -> splitAction.setEnabled(event.paneId().map(panes::containsKey).orElse(false)));
        context.events().subscribe(TerminalEvents.PANE_CLOSED, event -> panes.remove(event.paneId()));
        vault.ifPresent(api -> context.events().subscribe(VaultApi.LOCK_STATE_CHANGED, state -> refreshPanels()));
    }

    @Override public void stop() {
        if (poll != null) poll.stop();
        if (connections != null) connections.close();
    }

    // ---- sessions

    SessionSpec session(RemoteHost host) { return SessionSpec.of(host.name(), pending -> connect(pending, host.id())); }

    private void connect(PendingSession pending, UUID hostId) {
        CompletableFuture<Connections.Shell> future = connections.shell(hostId, pending.columns(), pending.rows(), pending::status);
        var cancellation = pending.onCancelled(() -> ui.execute(() -> future.cancel(true)));
        future.whenComplete((shell, failure) -> ui.execute(() -> {
            cancellation.close();
            if (failure != null) { pending.fail(message(failure)); return; }
            UUID paneId = pending.pane().id();
            panes.put(paneId, hostId);
            shell.connection().exited().whenComplete((ignored, exit) -> ui.execute(() -> { panes.remove(paneId); refreshSplit(); }));
            pending.attach(shell.connection());
            refreshSplit();
        }));
    }

    void openHost(WindowHandle window, RemoteHost host) { context.terminals().openTab(window, OpenRequest.session(session(host))); }
    void splitHost(PaneHandle pane, RemoteHost host) { context.terminals().split(pane, Direction.RIGHT, OpenRequest.session(session(host))); }

    private void splitSameHost(PaneHandle pane) {
        UUID hostId = panes.get(pane.id());
        Optional<RemoteHost> host = hostId == null ? Optional.empty() : store.host(hostId);
        if (host.isEmpty()) { context.notices().error("Split with Same Host needs a connected SSH pane"); return; }
        splitHost(pane, host.get());
    }

    private void refreshSplit() {
        splitAction.setEnabled(context.terminals().activePane().map(pane -> panes.containsKey(pane.id())).orElse(false));
    }

    private static String message(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    // ---- prompts and dialogs

    /** The host-key question, answered through a window-modal dialog; closing it answers Cancel. */
    CompletableFuture<HostKeyVerifier.Decision> askHostKey(HostKeyVerifier.Question question) {
        var decision = new CompletableFuture<HostKeyVerifier.Decision>();
        ui.execute(() -> {
            Optional<WindowHandle> owner = owner();
            if (owner.isEmpty()) { decision.complete(HostKeyVerifier.Decision.CANCEL); return; }
            PluginDialog dialog = context.windows().dialog(new DialogSpec("Verify host key " + question.host(), owner.get(), true));
            currentHostKeyPanel = new HostKeyPanel(question, answer -> { decision.complete(answer); dialog.close(); });
            dialog.setContent(currentHostKeyPanel);
            dialog.onClosed(() -> { currentHostKeyPanel = null; decision.complete(HostKeyVerifier.Decision.CANCEL); });
            dialog.show();
        });
        return decision;
    }

    private Optional<WindowHandle> owner() { return context.terminals().activeWindow().or(() -> context.terminals().windows().stream().findFirst()); }

    void editHost(WindowHandle window, Optional<RemoteHost> editing) {
        PluginDialog dialog = context.windows().dialog(new DialogSpec(editing.isPresent() ? "Edit " + editing.get().name() : "Add Host", window, true));
        currentEditor = new HostEditor(store.hosts(), editing, vault.isPresent(), this::credentialName,
            () -> vault.map(api -> api.pick(window)).orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())),
            host -> store.put(host).whenComplete((ignored, failure) -> ui.execute(() -> { if (failure == null) dialog.close(); else currentEditor.message.setText(message(failure)); })),
            dialog::close);
        dialog.setContent(currentEditor);
        dialog.onClosed(() -> currentEditor = null);
        dialog.show();
    }

    private void deleteHost(WindowHandle window, RemoteHost host) {
        PluginDialog dialog = context.windows().dialog(new DialogSpec("Delete " + host.name() + "?", window, true));
        currentConfirm = new ConfirmPanel("Delete the saved host " + host.name() + "? Open sessions stay connected.", "Delete",
            () -> store.remove(host.id()).whenComplete((ignored, failure) -> ui.execute(() -> { if (failure != null) context.notices().error(message(failure)); dialog.close(); })), dialog::close);
        dialog.setContent(currentConfirm);
        dialog.onClosed(() -> currentConfirm = null);
        dialog.show();
    }

    private void importConfig(WindowHandle window) {
        Path config = sshDir.resolve("config");
        Map<String, UUID> vaultKeys = new HashMap<>();
        vault.ifPresent(api -> { for (CredentialDescriptor descriptor : api.credentials()) if (descriptor.kind() == Kind.SSH_KEY) vaultKeys.put(descriptor.subtitle(), descriptor.id()); });
        Path home = Path.of(System.getProperty("user.home"));
        String user = System.getProperty("user.name", "");
        List<RemoteHost> existing = store.hosts();
        context.background().execute(() -> {
            SshConfig.Parsed parsed;
            try { parsed = SshConfig.parse(config); }
            catch (IOException unreadable) { ui.execute(() -> context.notices().error("Could not read " + config + ": " + unreadable.getMessage())); return; }
            List<ConfigImport.Candidate> plan = ConfigImport.plan(parsed, existing, vaultKeys, pub -> { try { return Files.isRegularFile(pub) ? Optional.of(Files.readString(pub)) : Optional.empty(); } catch (IOException e) { return Optional.empty(); } }, home, user);
            ui.execute(() -> {
                PluginDialog dialog = context.windows().dialog(new DialogSpec("Import from ~/.ssh/config", window, true));
                currentImport = new ImportPanel(plan, parsed.skipped(), chosen -> {
                    var next = new java.util.ArrayList<>(store.hosts());
                    Set<String> names = new java.util.HashSet<>(next.stream().map(host -> host.name().toLowerCase(java.util.Locale.ROOT)).toList());
                    for (RemoteHost host : chosen) if (names.add(host.name().toLowerCase(java.util.Locale.ROOT))) next.add(host);
                    store.save(next).whenComplete((ignored, failure) -> ui.execute(() -> { if (failure != null) context.notices().error(message(failure)); dialog.close(); }));
                }, dialog::close);
                dialog.setContent(currentImport);
                dialog.onClosed(() -> currentImport = null);
                dialog.show();
            });
        });
    }

    // ---- panel and status

    private javax.swing.JComponent createPanel(PanelHost host) {
        WindowHandle window = host.window();
        var panel = new HostsPanel(new HostsPanel.Actions(h -> openHost(window, h), h -> window.activeTab().flatMap(tab -> tab.activePane()).ifPresent(pane -> splitHost(pane, h)),
            editing -> editHost(window, editing), h -> store.put(duplicate(h)), h -> deleteHost(window, h),
            (h, favorite) -> store.put(h.withFavorite(favorite)), () -> importConfig(window)));
        panel.setCredentialLabels(this::credentialLabel);
        panel.setCollapsed(panelState.collapsed());
        panel.onCollapsedChanged(() -> { try { panelState.save(panel.collapsed()); } catch (IOException failure) { context.log().log(System.Logger.Level.WARNING, "panel state: {0}", failure.getMessage()); } });
        panel.setHosts(store.hosts(), store.error());
        panels.put(window.id(), panel); panelHosts.put(window.id(), host);
        host.onClosed(() -> { panels.remove(window.id()); panelHosts.remove(window.id()); });
        return panel;
    }

    private RemoteHost duplicate(RemoteHost host) {
        String base = host.name() + " copy";
        String name = base;
        for (int i = 2; store.hosts().stream().anyMatch(other -> other.name().equalsIgnoreCase(name)); i++) name = base + " " + i;
        return RemoteHost.create(name, host.hostname(), host.port(), host.username(), host.auth(), host.group(), host.jump());
    }

    private void showPanel(WindowHandle window) {
        PanelHost host = panelHosts.get(window.id());
        if (host != null) host.show();
        else context.palette().open(window, RemoteScope.ID, Optional.empty(), Optional.empty());
    }

    private void refreshPanels() { for (HostsPanel panel : panels.values()) { panel.setCredentialLabels(this::credentialLabel); panel.setHosts(store.hosts(), store.error()); } }

    private String credentialLabel(RemoteHost host) {
        return switch (host.auth()) {
            case Auth.Agent agent -> "SSH agent";
            case Auth.Vault credential -> vault.map(api -> api.lockState() != LockState.UNLOCKED ? "Vault locked" : credentialName(credential.credentialId()).orElse("credential missing")).orElse("needs Credential Vault");
        };
    }

    private Optional<String> credentialName(UUID id) {
        return vault.flatMap(api -> api.credentials().stream().filter(descriptor -> descriptor.id().equals(id)).findFirst()).map(CredentialDescriptor::name);
    }

    private void refreshStatus() {
        int count = connections.channelCount();
        status.setText(count == 1 ? "1 SSH session" : count + " SSH sessions");
        status.setVisible(count > 0);
    }

    HostStore store() { return store; }
    Connections connections() { return connections; }
    HostKeyPanel currentHostKeyPanel() { return currentHostKeyPanel; }
    HostEditor currentEditor() { return currentEditor; }
    ImportPanel currentImport() { return currentImport; }
    ConfirmPanel currentConfirm() { return currentConfirm; }
}
```

The `duplicate` loop's `name` must be effectively final for the lambda: use a `String[] candidate` holder or a helper `private boolean nameTaken(String)`; rewrite as `while (nameTaken(name)) name = base + " " + i++;`.

- [x] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test -q`
Expected: PASS. Adjustment points: the fake host's exact `menu(...)` line format; the fake's `openRequests` line for a session split (assert only its prefix); `host.status()` text; if `ACTIVE_PANE_CHANGED` is not published by `focusTerminalPane`, call `plugin`'s `refreshSplit` path through a second `host.flush()` after `focusTerminalPane` or assert the enabled state after `SPLIT` is invoked from the pane.

- [x] **Step 6: Commit**

```bash
git add plugins/remote
git commit -m "feat(remote): the Remote plugin with session provider, hosts panel, scope, actions, menu and status item"
```

---

### Task 8: Bundle the plugin, document it, record status

**Files:**
- Modify: `jasper-app/build.gradle.kts` (`stagePlugins`), `gradle/plugin-architecture.gradle.kts` (`declaredImports`), `jasper-app/src/test/java/dev/jasper/app/plugins/PluginZipsTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java`
- Create: `docs/remote.md`
- Modify: `docs/README.md`, `README.md`, `docs/app-maintenance.md`, `docs/plugin-authoring.md`, `AGENTS.md`, `docs/STATUS.md`, `docs/superpowers/specs/2026-09-22-jasper-remote-design.md` (two recorded deviations)

- [x] **Step 1: Stage and guard the module**

`jasper-app/build.gradle.kts`, in `stagePlugins` after the vault lines:

```kotlin
    from(project(":jasper-plugin-remote").tasks.named("jar")) { into("dev.jasper.remote") }
    from(project(":jasper-plugin-remote").configurations.named("runtimeClasspath")) { into("dev.jasper.remote") }
```

`gradle/plugin-architecture.gradle.kts`:

```kotlin
val declaredImports = mapOf(":jasper-plugin-history" to listOf("dev.jasper.snippets.api"), ":jasper-plugin-remote" to listOf("dev.jasper.vault.api"))
```

- [x] **Step 2: Extend the app's bundled-plugin tests**

`PluginZipsTest.java`: `hasSize(5)`; add before the final `else`:

```java
            else if (descriptor.id().equals("dev.jasper.remote"))
                assertThat(jars).as("MINA travels with Remote").anySatisfy(jar -> assertThat(jar).startsWith("sshd-core-"));
```

and the ids line gains `"dev.jasper.remote"`.

`BundledSamplePluginTest.java`: the panels assertion becomes `assertThat(contributions.panels()).extracting(panel -> panel.id()).containsExactly("dev.jasper.sample.panel", "dev.jasper.remote.panel")` (order as registered; adjust to `containsExactlyInAnyOrder` if the runtime orders otherwise); `statusLines()` `hasSize(6)` with `.anySatisfy(line -> assertThat(line).contains("dev.jasper.remote", "BUNDLED", "ACTIVE"))`; the rail assertion stays (Remote adds no rail action; its panel is the rail's business).

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*' -q`
Expected: PASS. If Remote fails to start under the real runtime, read its status line: the likely causes are `Timer` or `Toolkit` use at start (both work headless) or the descriptor's `requires` (optional, so the Vault's presence must not matter).

- [x] **Step 3: Documentation**

`docs/remote.md`:

````markdown
# Remote: SSH hosts

Remote is bundled with Jasper. Press **Cmd+Shift+H** (Ctrl+Shift+H elsewhere) or type `>ssh` in the
command palette to pick a saved host; Enter connects in a new tab, Cmd/Ctrl+Enter connects in a
split beside the current pane, Shift+Enter edits the host. The **SSH hosts** panel in the rail lists
hosts by group with favorites first; select one for its card and Connect, double-click to connect,
right-click for Connect, Connect in split, Edit, Duplicate, Delete and Favorite. The **SSH** menu
carries the same commands plus **Import from ~/.ssh/config...**.

A host is a name, hostname, port, username, group, an optional jump host and how it authenticates:
a **Vault credential** (a login with a password, a key or both, or a standalone key with the host's
username) or the **SSH agent** (`SSH_AUTH_SOCK`, or the OpenSSH agent pipe on Windows). Hosts live in
`plugins/dev.jasper.remote/data/hosts.toml`, which you may edit; Jasper notices the change within a
second. The file never holds a secret. A jump host makes the connection go through that host first,
with its own authentication and host-key check.

**Import** reads `~/.ssh/config` (`Host`, `HostName`, `Port`, `User`, `ProxyJump`, `IdentityFile`,
one level of `Include`) and shows what each entry would become; wildcard hosts and `Match` blocks are
listed as not importable. An `IdentityFile` whose public key is a key in the Vault becomes that
credential; any other becomes the agent. Nothing is imported silently, and `~/.ssh` is never written.

The first connection to a host shows its key type and SHA256 fingerprint with **Cancel**,
**Connect once** and **Trust and connect**; trusting writes `plugins/dev.jasper.remote/data/known_hosts`.
Keys already in `~/.ssh/known_hosts` (hashed entries included) connect without asking. A key that
differs from either file is rejected. A corrupt Jasper `known_hosts` refuses every connection until
it is fixed.

One SSH connection per host is shared by every tab, split and (later) tunnel and SFTP browser for
that host; it stays open for a few seconds after its last use. When it drops, every pane on that host
shows the disconnected banner and Reconnect makes a new connection. The status bar shows how many
SSH sessions are open; clicking it opens the panel. Locking the Vault leaves live sessions alone;
only new connections need it.

Settings live in `plugins/dev.jasper.remote/dev.jasper.remote.toml` (Plugins manager > Open Settings):

```toml
connect_timeout_seconds = 10
auth_timeout_seconds = 30
keepalive_seconds = 30          # 0 disables
session_linger_seconds = 5
read_user_known_hosts = true
use_ssh_agent = true
```

The actions are `dev.jasper.remote.connect`, `dev.jasper.remote.hosts`, `dev.jasper.remote.split` and
`dev.jasper.remote.import`; bind them like other contributed actions. Tunnels and SFTP follow in
later versions of this plugin.

## Native acceptance

Headless tests cover the store, the config import, trust decisions, the agent protocol, the shared
session, ProxyJump and every failure message against an SSH server on loopback. The following need a
real desktop and a host you control:

1. Connect to a real host with a Vault login (password), a Vault key and the system agent; verify the
   fingerprint prompt against the server's own, then that Trust and connect stops the prompt.
2. Connect through a real bastion (jump host); confirm two host-key prompts on first use and one
   shared bastion session in the status count.
3. Open a second tab and a split on the same host: no new authentication, both interactive; close all
   and confirm the session drops after the linger.
4. Import your `~/.ssh/config`; check the mapped fields and the "not importable" list.
5. Pull the network or stop `sshd`: every pane on that host shows the banner; Reconnect works after.
6. Lock the Vault while connected (sessions stay), then Connect a Vault host (unlock prompt appears).
````

`docs/README.md`: after the Credential Vault bullet add a bullet whose link text is `Remote`, whose target is `remote.md`, and whose description is "SSH hosts, import from ~/.ssh/config, host-key trust, shared sessions and the hosts panel."

`README.md`: the bundled-plugin sentence gains "Remote (SSH)"; the directory-layout line gains "Remote".

`docs/app-maintenance.md`, after the Credential Vault section:

```markdown
## Remote lives in a plugin

SSH is the bundled `dev.jasper.remote` plugin (`plugins/remote`): `hosts` (model, `hosts.toml`
store with a one-second poll, `~/.ssh/config` import), `trust` (Jasper's `known_hosts` plus read-only
matching of the user's), `agent` (a two-message ssh-agent client over the agent socket, adapted to
MINA), `client` (`Connections`: one shared MINA session per host, reference-counted channels, a
linger, ProxyJump through a local forward; `HostKeyVerifier`; `ShellChannels` to `TerminalConnection`)
and `ui` (panel, editor, import and host-key panels, the `>ssh` scope). It compiles `compileOnly`
against `dev.jasper.vault.api` (`requires` optional). Integration tests run an embedded MINA server
on loopback (`LoopbackServer`); nothing external is contacted. Run `./gradlew :jasper-plugin-remote:test`.
```

`docs/plugin-authoring.md`, after the Credential Vault paragraph: "Remote (`plugins/remote`) is the worked example for `session.provide`: its connector runs on the background executor, reports `status`, attaches a `TerminalConnection` whose `close` releases a reference on a shared MINA session, and cancels through `onCancelled`."

`AGENTS.md` architecture bullet: after the Credential Vault sentence add "Remote (SSH; tunnels and SFTP later) is a bundled plugin too (`plugins/remote`, bundling Apache MINA sshd); it consumes the Vault through `dev.jasper.vault.api` only."

Spec `2026-09-22-jasper-remote-design.md`: add to section 2 a bullet 8, "**Recorded deviations during plan 7a:** the SDK cannot open a new terminal window, so the palette's second verb is *Connect in split* rather than *Connect in new window* (the panel's context menu likewise); group collapse state is one set in `data/panel-state.toml` rather than per window, because window ids do not survive a restart." and adjust section 7's verb list and section 4's collapse-state sentence to match.

`docs/STATUS.md`: opening paragraph: "The Remote (SSH/tunnels/SFTP) design (`superpowers/specs/2026-09-22-jasper-remote-design.md`) is approved; plan 7a (SSH) is implemented on `claude/remote-7a`; 7b tunnels and 7c SFTP are next." Add a section before the Credential Vault ones:

```markdown
### Remote plan 7a (SSH) — 2026-09-22

Implemented on `claude/remote-7a` (not merged, not pushed): the bundled `dev.jasper.remote` plugin's
SSH slice per the [Remote design](superpowers/specs/2026-09-22-jasper-remote-design.md) and the
[7a plan](superpowers/plans/2026-09-22-jasper-remote-plan-7a-ssh.md). Saved hosts in `hosts.toml`
(polled), import from `~/.ssh/config`, own `known_hosts` plus read-only matching of the user's, Vault
or ssh-agent authentication, ProxyJump through saved hosts, one shared MINA session per host with
reference-counted shell channels and a linger, the hosts panel, host editor, import and host-key
dialogs, the `>ssh` scope, four actions, an SSH menu and a session-count status item. Guide:
[Remote](remote.md) with the native acceptance list. Deviations: Connect in split replaces Connect in
new window (no SDK window creation); panel collapse state is one saved set. Plans 7b (tunnels) and 7c
(SFTP) follow.
```

- [x] **Step 4: Full check and commit**

Run: `./gradlew check -q`
Expected: PASS, including `verifyPluginArchitecture` (Remote imports the JDK, the SDK, `dev.jasper.vault.api`, its own packages and bundled MINA/BouncyCastle/tomlj) and the documentation link test.

```bash
git add jasper-app gradle README.md AGENTS.md docs
git commit -m "feat(remote): bundle the Remote plugin and document it"
```

---

## Self-review

- **Spec coverage.** Section 3 shape and descriptor: Task 1 (module), Task 8 (bundling, architecture map). Section 4 hosts model, store, import: Tasks 1, 2. Section 5 registry, pipeline, trust, agent, ProxyJump, shell channel, cancellation, failure copy: Tasks 3, 4, 5 (the messages are asserted verbatim in `ConnectionsTest`). Section 6 settings: Task 1 (`RemoteSettings`, `settings.toml`), live re-read in Task 7. Section 7 UI: Tasks 6, 7 (panel, editor, host-key and import dialogs, scope, actions, menu, status). Section 8 tests: unit tests per task, loopback integration in Task 5, `FakePluginHost` with a fake Vault provider in Task 7 (a deviation from the spec's "start the real Vault plugin": the real one would touch the keychain when creating a vault, so a fake `VaultApi` publisher stands in), native list in Task 8.
- **Placeholders.** None; every file in a task's list has its code. `RemotePlugin.duplicate`'s lambda-capture note and the `KnownHosts` marker note are instructions, not gaps.
- **Type consistency.** `HostStore(Path, Executor, Executor)` with `hosts()/host()/error()/warnings()/put/remove/save/load/poll` is what `RemotePlugin` and `RemotePluginTest` use; `Connections.shell(UUID, int, int, Consumer<String>)` returning `CompletableFuture<Shell>` and `Shell.connection()` are used identically in `ConnectionsTest` and `RemotePlugin.connect`; `HostKeyVerifier.Question/Decision` are shared by `HostKeyPanel`, `Connections` and `RemotePlugin.askHostKey`; `ConfigImport.plan(...)`'s six parameters match `RemotePlugin.importConfig`; `KnownHosts` gains the `Supplier` constructor in Task 7 before `RemotePlugin` uses it; `HostsPanel.Actions` has seven components in both the panel and the plugin.
- **Recorded deviations** (also written into the spec in Task 8): Connect in split instead of Connect in new window; one saved collapse set; the credential is fetched before the TCP connect (MINA needs the username first) and closed right after authentication.
