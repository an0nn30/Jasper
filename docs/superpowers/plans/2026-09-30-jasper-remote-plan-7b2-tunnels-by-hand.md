# Remote tunnels plan 7b-2: tunnels you run by hand Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Written 2026-09-30 on `claude/tunnels-7b2`; not executed.

**Goal:** Saved SSH tunnels (local, remote, dynamic SOCKS5) and quick forwards that you start and stop by hand from a Tunnels rail panel, a palette scope and the SSH menu, with live counters, Open in Browser and Copy Address — Remote 0.9.0.

**Architecture:** A new package `dev.jasper.remote.tunnels` holds the model (`Tunnel`, `TunnelKind`, `TunnelStart`), the `tunnels.toml` format and store, and an own forwarding engine: `Listener` binds JDK sockets first, `TunnelManager` then leases the host's shared session from `Connections` and forwards every accepted connection over its own `direct-tcpip` channel (`Pump` copies and counts bytes, `Socks5` answers dynamic tunnels); a remote tunnel asks the server for its port with MINA's `startRemotePortForwarding`, pointed at a loopback `Listener` that connects on to the target. `dev.jasper.remote.ui.tunnels` holds the panel, editor, Forward a Port dialog, palette scope and `TunnelsUi`, which registers the surface and keeps the manager in step with the store. In this plan a lost session fails the tunnel; plan 7b-3 reconnects.

**Tech Stack:** Java 25 (JetBrains Runtime), Apache MINA sshd 2.19.0, tomlj, Swing, JUnit 5 + AssertJ, the SDK testkit's `FakePluginHost`, Remote's loopback `SshServer` (`LoopbackServer`).

**Spec:** `docs/superpowers/specs/2026-09-30-jasper-remote-tunnels-design.md` — sections 3 (model and lifecycle, minus reconnect and at-launch), 4 (engine), 5 (persistence), 6 (UI), 9 (errors), 10 and 12 (plan 7b-2). Plan 7b-1 (SDK 0.10.2, merged to main at `f7b9cd76`) supplies `Platform.browse`.

## Global Constraints

- Work in `.worktrees/tunnels-7b2` on `claude/tunnels-7b2` (it already exists and holds this plan). Verify `git branch --show-current` prints `claude/tunnels-7b2` before every commit.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Only `plugins/remote` and docs change; no SDK or application code. Remote becomes `version = "0.9.0"` with `sdk = ">=0.10.2, <0.11"`; its `requires` stay as they are.
- Tunnels have their own lifecycle: starting one never opens a terminal; stopping one releases only its own lease; closing terminals never stops one.
- Bind first: a tunnel's listener (or a remote tunnel's loopback relay) binds before any lease is requested, so a port conflict is reported before any prompt.
- Error copy is exact: "Port 5432 is already in use on this computer", "Port 80 needs administrator rights", "Cannot listen on 192.168.1.5: not an address of this computer", "The server refused port 8080: already in use or not allowed", "Host missing", "Connection lost", warning "Connections are being refused: <reason>"; connection-stage failures keep `Failures`' wording.
- Threading: `TunnelManager`, the panel and `TunnelsUi` run on the UI executor; connection threads (virtual threads) read only a run's volatile session and write its `Counters` and tracked ends; MINA callbacks hop to the UI executor.
- Tests bind loopback addresses only (never `*` or a LAN address, which would raise the macOS firewall prompt) and contact no external host.
- Never launch the GUI. Run the Python hygiene check from `AGENTS.md` before each commit.
- `./gradlew check` is green at the end of every task. For counts read `*/build/test-results/test/*.xml`.
- Recorded deviations from the spec (copy them into `docs/STATUS.md` in Task 11): `*` is sent to the server as `0.0.0.0` (MINA turns an empty host into `0.0.0.0` anyway); there is no `RemoteRelay` class — the relay is a `Listener` on `127.0.0.1:0`; the palette scope has no aliases (`ScopeSpec` has none); the rail and status icon is the catalog's `BundledIcon.ACTIONS_SWAP_PANELS` (no semantic `IconName` fits) and the failing status uses `BundledIcon.STATUS_WARNING`, so `RemotePluginTest`'s "semantic icons only" check admits that one catalog icon; `Pump` uses `java.net` sockets.

---

### Task 1: The tunnel model and `tunnels.toml`

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/tunnels/TunnelKind.java`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/tunnels/TunnelStart.java`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/tunnels/Tunnel.java`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/tunnels/TunnelFile.java`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/tunnels/package-info.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/tunnels/TunnelFileTest.java`

**Interfaces:**
- Consumes: `dev.jasper.remote.hosts.HostFile.tomlString(String)`.
- Produces: `enum TunnelKind { LOCAL, REMOTE, DYNAMIC; String letter(); String key(); }`; `enum TunnelStart { MANUAL, LAUNCH; String key(); }`; `record Tunnel(UUID id, String name, UUID hostId, TunnelKind kind, String bindAddress, int bindPort, String targetHost, int targetPort, TunnelStart start, Instant created, Instant updated)` with `Tunnel.ALL = "*"`, `Tunnel.LOCALHOST = "localhost"`, `static Tunnel create(String name, UUID hostId, TunnelKind kind, String bindAddress, int bindPort, String targetHost, int targetPort)`, `Tunnel withEdited(String name, UUID hostId, TunnelKind kind, String bindAddress, int bindPort, String targetHost, int targetPort)`, `boolean quick()`, `boolean sameForwarding(Tunnel other)`, `static boolean validBindAddress(String)`; `TunnelFile.HEADER`, `TunnelFile.MAX_BYTES`, `record TunnelFile.Parsed(List<Tunnel> tunnels, List<String> warnings)`, `static Parsed parse(String) throws IOException`, `static void validate(List<Tunnel>)`, `static String format(List<Tunnel>)`.

- [ ] **Step 1: Write the failing test**

Create `plugins/remote/src/test/java/dev/jasper/remote/tunnels/TunnelFileTest.java`:

```java
package dev.jasper.remote.tunnels;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TunnelFileTest {
    static final UUID HOST = UUID.fromString("5f2c1d6e-0000-4000-8000-000000000001");
    static final Instant MADE = Instant.parse("2026-09-30T10:00:00Z");

    static Tunnel tunnel(String name, TunnelKind kind, int bindPort, String targetHost, int targetPort) {
        return new Tunnel(UUID.randomUUID(), name, HOST, kind, Tunnel.LOCALHOST, bindPort, targetHost, targetPort, TunnelStart.MANUAL, MADE, MADE);
    }

    @Test void aSavedListComesBackUnchanged() throws IOException {
        Tunnel db = tunnel("pg-prod", TunnelKind.LOCAL, 5432, "db.internal", 5432);
        Tunnel socks = tunnel("socks", TunnelKind.DYNAMIC, 1080, "", 0);
        Tunnel api = new Tunnel(UUID.randomUUID(), "api \"dev\"", HOST, TunnelKind.REMOTE, Tunnel.ALL, 0, "localhost", 8080,
            TunnelStart.LAUNCH, MADE, MADE.plusSeconds(60));
        String text = TunnelFile.format(List.of(db, socks, api));
        assertThat(text).startsWith(TunnelFile.HEADER).contains("kind = \"dynamic\"").contains("start = \"launch\"")
            .contains("name = \"api \\\"dev\\\"\"");
        assertThat(text.split("\\[\\[tunnel]]")[2]).as("a dynamic tunnel has no target").doesNotContain("target_");
        TunnelFile.Parsed parsed = TunnelFile.parse(text);
        assertThat(parsed.warnings()).isEmpty();
        assertThat(parsed.tunnels()).containsExactly(db, socks, api);
    }

    @Test void badEntriesAreSkippedAndReportedOneByOne() throws IOException {
        String text = TunnelFile.HEADER + """
            [[tunnel]]
            name = "ok"
            host = "%1$s"
            bind_port = 3000
            target_host = "localhost"
            target_port = 3000

            [[tunnel]]
            host = "%1$s"
            bind_port = 1

            [[tunnel]]
            name = "kind"
            host = "%1$s"
            kind = "sideways"
            bind_port = 1

            [[tunnel]]
            name = "OK"
            host = "%1$s"
            kind = "dynamic"
            bind_port = 1080

            [[tunnel]]
            name = "address"
            host = "%1$s"
            bind_address = "my.laptop"
            bind_port = 1
            target_host = "x"
            target_port = 1

            [[tunnel]]
            name = "port"
            host = "%1$s"
            bind_port = 70000
            target_host = "x"
            target_port = 1
            """.formatted(HOST);
        TunnelFile.Parsed parsed = TunnelFile.parse(text);
        assertThat(parsed.tunnels()).singleElement().satisfies(tunnel -> {
            assertThat(tunnel.name()).isEqualTo("ok");
            assertThat(tunnel.kind()).as("local by default").isEqualTo(TunnelKind.LOCAL);
            assertThat(tunnel.bindAddress()).as("localhost by default").isEqualTo(Tunnel.LOCALHOST);
            assertThat(tunnel.start()).isEqualTo(TunnelStart.MANUAL);
        });
        assertThat(parsed.warnings()).containsExactly(
            "tunnel 2: a tunnel needs a name",
            "tunnel 3: 'kind' must be \"local\", \"remote\" or \"dynamic\"",
            "tunnel 4: duplicate name OK",
            "tunnel 5: Listen on localhost, * or an IP address",
            "tunnel 6: 'bind_port' must be a port number");
    }

    @Test void brokenTomlIsAnError() {
        assertThatThrownBy(() -> TunnelFile.parse("[[tunnel]\nname = ")).isInstanceOf(IOException.class)
            .hasMessageStartingWith("tunnels.toml has TOML errors");
    }

    @Test void savedTunnelsNeedUniqueNames() {
        Tunnel db = tunnel("db", TunnelKind.LOCAL, 5432, "db", 5432);
        assertThatIllegalArgumentException().isThrownBy(() -> TunnelFile.validate(List.of(db, tunnel("DB", TunnelKind.LOCAL, 1, "x", 1))))
            .withMessage("A tunnel named DB exists");
        assertThatIllegalArgumentException().isThrownBy(() -> TunnelFile.validate(List.of(tunnel("", TunnelKind.LOCAL, 1, "x", 1))))
            .withMessage("A saved tunnel needs a name");
        assertThatCode(() -> TunnelFile.validate(List.of(db))).doesNotThrowAnyException();
    }

    @Test void theRecordNormalisesAndChecksItsParts() {
        Tunnel dynamic = Tunnel.create("socks", HOST, TunnelKind.DYNAMIC, " localhost ", 1080, "ignored", 99);
        assertThat(dynamic.bindAddress()).isEqualTo("localhost");
        assertThat(dynamic.targetHost()).isEmpty();
        assertThat(dynamic.targetPort()).isZero();
        assertThat(Tunnel.create("", HOST, TunnelKind.LOCAL, "::1", 0, "x", 1).quick()).isTrue();
        assertThat(Tunnel.validBindAddress("10.0.0.5")).isTrue();
        assertThat(Tunnel.validBindAddress("*")).isTrue();
        assertThat(Tunnel.validBindAddress("db.example")).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> Tunnel.create("x", HOST, TunnelKind.LOCAL, "localhost", 1, " ", 1))
            .withMessage("A tunnel needs a host to forward to");
        assertThatIllegalArgumentException().isThrownBy(() -> Tunnel.create("x", HOST, TunnelKind.REMOTE, "localhost", 1, "x", 0))
            .withMessage("The target port must be 1 to 65535");
        Tunnel db = Tunnel.create("db", HOST, TunnelKind.LOCAL, "localhost", 5432, "db", 5432);
        Tunnel renamed = db.withEdited("database", HOST, TunnelKind.LOCAL, "localhost", 5432, "db", 5432);
        assertThat(renamed.id()).isEqualTo(db.id());
        assertThat(renamed.created()).isEqualTo(db.created());
        assertThat(renamed.sameForwarding(db)).as("a rename forwards the same way").isTrue();
        assertThat(db.withEdited("db", HOST, TunnelKind.LOCAL, "localhost", 5433, "db", 5432).sameForwarding(db)).isFalse();
        assertThat(TunnelKind.DYNAMIC.letter()).isEqualTo("D");
        assertThat(TunnelKind.REMOTE.key()).isEqualTo("remote");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-plugin-remote:test --tests dev.jasper.remote.tunnels.TunnelFileTest`
Expected: FAIL — compilation errors, `package dev.jasper.remote.tunnels does not exist` / `cannot find symbol: class Tunnel`.

- [ ] **Step 3: Write the model and the file format**

Create `plugins/remote/src/main/java/dev/jasper/remote/tunnels/package-info.java`:

```java
/**
 * SSH tunnels without Swing: the model, tunnels.toml, and the engine that binds listeners, leases the host's shared session
 * from {@code client.Connections} and forwards each connection over its own channel. {@link dev.jasper.remote.tunnels.TunnelManager}
 * is UI-thread-only; connection threads touch only a run's session, its counters and its tracked ends.
 */
package dev.jasper.remote.tunnels;
```

Create `plugins/remote/src/main/java/dev/jasper/remote/tunnels/TunnelKind.java`:

```java
package dev.jasper.remote.tunnels;

import java.util.Locale;

/** Which way a tunnel forwards. */
public enum TunnelKind {
    /** {@code -L}: a port on this computer reaches a target through the server. */
    LOCAL,
    /** {@code -R}: a port on the server reaches a target through this computer. */
    REMOTE,
    /** {@code -D}: a SOCKS5 proxy on this computer; each connection names its own target, reached through the server. */
    DYNAMIC;

    /** The letter the panel shows: L, R or D. */
    public String letter() { return name().substring(0, 1); }

    /** The value in tunnels.toml. */
    public String key() { return name().toLowerCase(Locale.ROOT); }
}
```

Create `plugins/remote/src/main/java/dev/jasper/remote/tunnels/TunnelStart.java`:

```java
package dev.jasper.remote.tunnels;

import java.util.Locale;

/** When a saved tunnel starts. Plan 7b-2 stores it; plan 7b-3 starts {@code LAUNCH} tunnels when Jasper starts. */
public enum TunnelStart {
    /** Only when you start it. */
    MANUAL,
    /** When Jasper starts, without prompting. */
    LAUNCH;

    /** The value in tunnels.toml. */
    public String key() { return name().toLowerCase(Locale.ROOT); }
}
```

Create `plugins/remote/src/main/java/dev/jasper/remote/tunnels/Tunnel.java`:

```java
package dev.jasper.remote.tunnels;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A tunnel: a saved one from tunnels.toml, or a quick forward that lives in memory until it is saved (a quick forward
 * has an empty name). It goes through a saved host, which holds the credential, the host-key trust and the jump chain.
 * A dynamic tunnel has no target.
 */
public record Tunnel(UUID id, String name, UUID hostId, TunnelKind kind, String bindAddress, int bindPort,
                     String targetHost, int targetPort, TunnelStart start, Instant created, Instant updated) {
    /** Listen on every address. */
    public static final String ALL = "*";
    /** Listen on the loopback addresses only, the default. */
    public static final String LOCALHOST = "localhost";

    public Tunnel {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(hostId, "hostId"); Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(start, "start"); Objects.requireNonNull(created, "created"); Objects.requireNonNull(updated, "updated");
        name = name == null ? "" : name.strip();
        bindAddress = bindAddress == null ? "" : bindAddress.strip();
        if (!validBindAddress(bindAddress)) throw new IllegalArgumentException("Listen on localhost, * or an IP address");
        if (bindPort < 0 || bindPort > 65535) throw new IllegalArgumentException("The listen port must be 0 to 65535");
        if (kind == TunnelKind.DYNAMIC) { targetHost = ""; targetPort = 0; }
        else {
            targetHost = targetHost == null ? "" : targetHost.strip();
            if (targetHost.isEmpty()) throw new IllegalArgumentException("A tunnel needs a host to forward to");
            if (targetPort < 1 || targetPort > 65535) throw new IllegalArgumentException("The target port must be 1 to 65535");
        }
    }

    /** A new manual tunnel; an empty name makes it a quick forward. */
    public static Tunnel create(String name, UUID hostId, TunnelKind kind, String bindAddress, int bindPort, String targetHost, int targetPort) {
        Instant now = Instant.now();
        return new Tunnel(UUID.randomUUID(), name, hostId, kind, bindAddress, bindPort, targetHost, targetPort, TunnelStart.MANUAL, now, now);
    }

    /** The same tunnel (id, start option, creation time) with new settings. */
    public Tunnel withEdited(String name, UUID hostId, TunnelKind kind, String bindAddress, int bindPort, String targetHost, int targetPort) {
        return new Tunnel(id, name, hostId, kind, bindAddress, bindPort, targetHost, targetPort, start, created, Instant.now());
    }

    /** A quick forward: unsaved, shown by its listen address. */
    public boolean quick() { return name.isEmpty(); }

    /** Whether the two forward the same way, which is what a running tunnel must restart for. */
    public boolean sameForwarding(Tunnel other) {
        return hostId.equals(other.hostId) && kind == other.kind && bindAddress.equals(other.bindAddress) && bindPort == other.bindPort
            && targetHost.equals(other.targetHost) && targetPort == other.targetPort;
    }

    /** {@code localhost}, {@code *} or an IP literal; a host name is refused so binding never waits on DNS. */
    public static boolean validBindAddress(String address) {
        if (address.equals(LOCALHOST) || address.equals(ALL)) return true;
        try { InetAddress.ofLiteral(address); return true; }
        catch (IllegalArgumentException notALiteral) { return false; }
    }
}
```

Create `plugins/remote/src/main/java/dev/jasper/remote/tunnels/TunnelFile.java`:

```java
package dev.jasper.remote.tunnels;

import dev.jasper.remote.hosts.HostFile;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
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

/** The tunnels.toml format: an array of [[tunnel]] tables. Lenient per entry, strict per file. */
public final class TunnelFile {
    public static final String HEADER = "# Jasper Remote tunnels. Edit freely; Jasper rewrites this file when you save from the panel.\n";
    public static final int MAX_BYTES = 1024 * 1024;

    /** The good tunnels, and one line for each entry that was skipped. */
    public record Parsed(List<Tunnel> tunnels, List<String> warnings) { }

    private TunnelFile() { }

    /** Throws when the text is not valid TOML; skips and reports individual bad entries. */
    public static Parsed parse(String text) throws IOException {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors()) throw new IOException("tunnels.toml has TOML errors: " + toml.errors().getFirst().toString());
        Object value = toml.get("tunnel");
        if (value == null) return new Parsed(List.of(), List.of());
        if (!(value instanceof TomlArray array)) return new Parsed(List.of(), List.of("'tunnel' must be an array of tables"));
        var tunnels = new ArrayList<Tunnel>();
        var warnings = new ArrayList<String>();
        var names = new HashSet<String>();
        var ids = new HashSet<UUID>();
        for (int i = 0; i < array.size(); i++) {
            String where = "tunnel " + (i + 1) + ": ";
            if (!(array.get(i) instanceof TomlTable table)) { warnings.add(where + "not a table"); continue; }
            try {
                Tunnel tunnel = tunnel(table);
                if (!names.add(tunnel.name().toLowerCase(Locale.ROOT))) { warnings.add(where + "duplicate name " + tunnel.name()); continue; }
                if (!ids.add(tunnel.id())) { warnings.add(where + "duplicate id " + tunnel.id()); continue; }
                tunnels.add(tunnel);
            } catch (IllegalArgumentException invalid) {
                warnings.add(where + invalid.getMessage());
            }
        }
        return new Parsed(List.copyOf(tunnels), List.copyOf(warnings));
    }

    private static Tunnel tunnel(TomlTable table) {
        UUID id = text(table, "id").map(TunnelFile::uuid).orElseGet(UUID::randomUUID);
        String name = text(table, "name").orElse("");
        if (name.isBlank()) throw new IllegalArgumentException("a tunnel needs a name");
        UUID host = uuid(text(table, "host").orElseThrow(() -> new IllegalArgumentException("'host' is required")));
        TunnelKind kind = switch (text(table, "kind").orElse("local")) {
            case "local" -> TunnelKind.LOCAL;
            case "remote" -> TunnelKind.REMOTE;
            case "dynamic" -> TunnelKind.DYNAMIC;
            default -> throw new IllegalArgumentException("'kind' must be \"local\", \"remote\" or \"dynamic\"");
        };
        TunnelStart start = switch (text(table, "start").orElse("manual")) {
            case "manual" -> TunnelStart.MANUAL;
            case "launch" -> TunnelStart.LAUNCH;
            default -> throw new IllegalArgumentException("'start' must be \"manual\" or \"launch\"");
        };
        int bindPort = port(table, "bind_port").orElseThrow(() -> new IllegalArgumentException("'bind_port' is required"));
        int targetPort = kind == TunnelKind.DYNAMIC ? 0
            : port(table, "target_port").orElseThrow(() -> new IllegalArgumentException("'target_port' is required"));
        Instant created = instant(table, "created").orElseGet(Instant::now);
        return new Tunnel(id, name, host, kind, text(table, "bind_address").orElse(Tunnel.LOCALHOST), bindPort,
            text(table, "target_host").orElse(""), targetPort, start, created, instant(table, "updated").orElse(created));
    }

    private static Optional<Integer> port(TomlTable table, String key) {
        Object value = table.get(key);
        if (value == null) return Optional.empty();
        if (!(value instanceof Long number) || number < 0 || number > 65535) throw new IllegalArgumentException("'" + key + "' must be a port number");
        return Optional.of(number.intValue());
    }

    private static Optional<String> text(TomlTable table, String key) {
        Object value = table.get(key);
        if (value == null) return Optional.empty();
        if (!(value instanceof String text)) throw new IllegalArgumentException("'" + key + "' must be a string");
        return Optional.of(text);
    }

    private static Optional<Instant> instant(TomlTable table, String key) {
        Object value = table.get(key);
        if (value == null) return Optional.empty();
        if (value instanceof OffsetDateTime time) return Optional.of(time.toInstant());
        if (value instanceof String text) {
            try { return Optional.of(Instant.parse(text)); }
            catch (RuntimeException bad) { throw new IllegalArgumentException("'" + key + "' is not a timestamp"); }
        }
        throw new IllegalArgumentException("'" + key + "' is not a timestamp");
    }

    private static UUID uuid(String text) {
        try { return UUID.fromString(text); }
        catch (IllegalArgumentException bad) { throw new IllegalArgumentException("'" + text + "' is not an id"); }
    }

    /** Rejects a blank name, duplicate names (case-insensitive) and duplicate ids: every saved tunnel is named. */
    public static void validate(List<Tunnel> tunnels) {
        var names = new HashSet<String>();
        var ids = new HashSet<UUID>();
        for (Tunnel tunnel : tunnels) {
            if (tunnel.name().isBlank()) throw new IllegalArgumentException("A saved tunnel needs a name");
            if (!names.add(tunnel.name().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("A tunnel named " + tunnel.name() + " exists");
            if (!ids.add(tunnel.id())) throw new IllegalArgumentException("Duplicate tunnel id");
        }
    }

    public static String format(List<Tunnel> tunnels) {
        var out = new StringBuilder(HEADER);
        for (Tunnel tunnel : tunnels) {
            out.append("\n[[tunnel]]\n");
            out.append("id = \"").append(tunnel.id()).append("\"\n");
            out.append("name = ").append(HostFile.tomlString(tunnel.name())).append('\n');
            out.append("host = \"").append(tunnel.hostId()).append("\"\n");
            out.append("kind = \"").append(tunnel.kind().key()).append("\"\n");
            out.append("bind_address = ").append(HostFile.tomlString(tunnel.bindAddress())).append('\n');
            out.append("bind_port = ").append(tunnel.bindPort()).append('\n');
            if (tunnel.kind() != TunnelKind.DYNAMIC) {
                out.append("target_host = ").append(HostFile.tomlString(tunnel.targetHost())).append('\n');
                out.append("target_port = ").append(tunnel.targetPort()).append('\n');
            }
            out.append("start = \"").append(tunnel.start().key()).append("\"\n");
            out.append("created = ").append(DateTimeFormatter.ISO_INSTANT.format(tunnel.created())).append('\n');
            out.append("updated = ").append(DateTimeFormatter.ISO_INSTANT.format(tunnel.updated())).append('\n');
        }
        return out.toString();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-plugin-remote:test --tests dev.jasper.remote.tunnels.TunnelFileTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

Run the hygiene check from `AGENTS.md` (expected: no output), then:

```bash
git branch --show-current
git add plugins/remote/src/main/java/dev/jasper/remote/tunnels plugins/remote/src/test/java/dev/jasper/remote/tunnels
git commit -m "Remote: the tunnel model and tunnels.toml

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: `TunnelStore`

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/tunnels/TunnelStore.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/tunnels/TunnelStoreTest.java`

**Interfaces:**
- Consumes: Task 1's `Tunnel`, `TunnelFile`.
- Produces: `TunnelStore(Path file, Executor background, Executor ui)` with `Path file()`, `List<Tunnel> tunnels()`, `Optional<Tunnel> tunnel(UUID)`, `Optional<String> error()`, `List<String> warnings()`, `Subscription onChanged(Runnable)`, `void load()`, `void poll()`, `CompletableFuture<Void> put(Tunnel)`, `CompletableFuture<Void> remove(UUID)`. Listeners run on the UI executor before a mutation's future completes.

- [ ] **Step 1: Write the failing test**

Create `plugins/remote/src/test/java/dev/jasper/remote/tunnels/TunnelStoreTest.java`:

```java
package dev.jasper.remote.tunnels;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class TunnelStoreTest {
    static final UUID HOST = UUID.randomUUID();
    final Deque<Runnable> background = new ArrayDeque<>();
    int changes;

    void run() { while (!background.isEmpty()) background.poll().run(); }

    TunnelStore store(Path dir) {
        var store = new TunnelStore(dir.resolve("tunnels.toml"), background::add, Runnable::run);
        store.onChanged(() -> changes++);
        return store;
    }

    static void touch(Path file, int seconds) throws Exception {
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(seconds)));
    }

    @Test void loadsSavesAndNoticesOutsideEdits(@TempDir Path dir) throws Exception {
        TunnelStore store = store(dir);
        store.load();
        run();
        assertThat(store.tunnels()).isEmpty();
        assertThat(store.error()).isEmpty();
        Tunnel db = Tunnel.create("db", HOST, TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db.internal", 5432);
        CompletableFuture<Void> saved = store.put(db);
        run();
        assertThat(saved).isCompleted();
        assertThat(store.tunnels()).containsExactly(db);
        assertThat(store.tunnel(db.id())).contains(db);
        assertThat(changes).isEqualTo(2);
        Path file = dir.resolve("tunnels.toml");
        assertThat(Files.readString(file)).startsWith(TunnelFile.HEADER).contains("name = \"db\"");
        try (var listing = Files.list(dir)) { assertThat(listing).as("no temp file").containsExactly(file); }

        Tunnel socks = Tunnel.create("socks", HOST, TunnelKind.DYNAMIC, Tunnel.LOCALHOST, 1080, "", 0);
        Files.writeString(file, TunnelFile.format(List.of(db, socks)));
        touch(file, 5);
        store.poll();
        run();
        assertThat(store.tunnels()).extracting(Tunnel::name).containsExactly("db", "socks");
        store.remove(db.id());
        run();
        assertThat(store.tunnels()).extracting(Tunnel::name).containsExactly("socks");
    }

    @Test void aBrokenFileKeepsTheLastGoodTunnelsAndRefusesSaves(@TempDir Path dir) throws Exception {
        TunnelStore store = store(dir);
        store.load();
        Tunnel db = Tunnel.create("db", HOST, TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db.internal", 5432);
        store.put(db);
        run();
        Path file = dir.resolve("tunnels.toml");
        Files.writeString(file, "[[tunnel]\nname = ");
        touch(file, 5);
        store.poll();
        run();
        assertThat(store.error()).get().asString().startsWith("tunnels.toml has TOML errors");
        assertThat(store.tunnels()).containsExactly(db);
        CompletableFuture<Void> refused = store.put(Tunnel.create("other", HOST, TunnelKind.LOCAL, Tunnel.LOCALHOST, 1, "x", 1));
        run();
        assertThat(refused).isCompletedExceptionally();
        assertThatThrownBy(refused::join).hasMessageContaining("fix it before saving");
        Files.writeString(file, TunnelFile.format(List.of(db)));
        touch(file, 10);
        store.poll();
        run();
        assertThat(store.error()).isEmpty();
    }

    @Test void aDuplicateNameIsRefusedAndTheFileIsLeftAlone(@TempDir Path dir) throws Exception {
        TunnelStore store = store(dir);
        store.load();
        store.put(Tunnel.create("db", HOST, TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db", 5432));
        run();
        String before = Files.readString(dir.resolve("tunnels.toml"));
        CompletableFuture<Void> refused = store.put(Tunnel.create("DB", HOST, TunnelKind.LOCAL, Tunnel.LOCALHOST, 1, "x", 1));
        run();
        assertThatThrownBy(refused::join).hasMessageContaining("A tunnel named DB exists");
        assertThat(Files.readString(dir.resolve("tunnels.toml"))).isEqualTo(before);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-plugin-remote:test --tests dev.jasper.remote.tunnels.TunnelStoreTest`
Expected: FAIL — `cannot find symbol: class TunnelStore`.

- [ ] **Step 3: Write the store**

Create `plugins/remote/src/main/java/dev/jasper/remote/tunnels/TunnelStore.java`:

```java
package dev.jasper.remote.tunnels;

import dev.jasper.sdk.Subscription;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
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
 * The saved tunnels, read from and written to tunnels.toml the way {@code hosts.HostStore} keeps hosts: reads and writes
 * run on {@code background}; the list, the error and the listeners live on the UI thread. A file that fails to parse
 * keeps the last good tunnels and refuses saves until it parses again.
 */
public final class TunnelStore {
    private final Path file;
    private final Executor background, ui;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private List<Tunnel> tunnels = List.of();
    private String error;
    private List<String> warnings = List.of();
    private long size = -1;
    private FileTime modified;
    private CompletableFuture<Void> queue = CompletableFuture.completedFuture(null);

    private record Read(List<Tunnel> tunnels, List<String> warnings, String error, long size, FileTime modified) { }

    public TunnelStore(Path file, Executor background, Executor ui) { this.file = file; this.background = background; this.ui = ui; }

    public Path file() { return file; }
    public List<Tunnel> tunnels() { return tunnels; }
    public Optional<Tunnel> tunnel(UUID id) { return tunnels.stream().filter(tunnel -> tunnel.id().equals(id)).findFirst(); }
    /** The last parse failure, present while the file is broken. */
    public Optional<String> error() { return Optional.ofNullable(error); }
    /** The entries the last good read skipped. */
    public List<String> warnings() { return warnings; }
    public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }

    /** Reads the file unconditionally. */
    public void load() { enqueue(() -> onBackground(() -> read(true)).thenAccept(this::apply)); }

    /** Reads the file when its size or modification time changed; the plugin calls this once a second. */
    public void poll() { enqueue(() -> onBackground(() -> read(false)).thenAccept(read -> { if (read != null) apply(read); })); }

    public CompletableFuture<Void> put(Tunnel tunnel) {
        return mutate(current -> {
            var next = new ArrayList<Tunnel>();
            boolean replaced = false;
            for (Tunnel existing : current) {
                if (existing.id().equals(tunnel.id())) { next.add(tunnel); replaced = true; } else next.add(existing);
            }
            if (!replaced) next.add(tunnel);
            return next;
        });
    }

    public CompletableFuture<Void> remove(UUID id) { return mutate(current -> current.stream().filter(tunnel -> !tunnel.id().equals(id)).toList()); }

    private CompletableFuture<Void> mutate(Function<List<Tunnel>, List<Tunnel>> mutation) {
        var result = new CompletableFuture<Void>();
        enqueue(() -> onBackground(() -> {
            Read current = read(true);
            if (current.error() != null) return current;
            List<Tunnel> next = List.copyOf(mutation.apply(current.tunnels()));
            TunnelFile.validate(next);
            write(TunnelFile.format(next));
            return read(true);
        }).handle((read, failure) -> {
            if (failure != null) result.completeExceptionally(failure);
            else {
                apply(read);
                if (read.error() != null) result.completeExceptionally(new IOException("tunnels.toml has errors; fix it before saving: " + read.error()));
                else result.complete(null);
            }
            return null;
        }));
        return result;
    }

    private void apply(Read read) {
        size = read.size(); modified = read.modified(); warnings = read.warnings();
        if (read.error() == null) { tunnels = read.tunnels(); error = null; }
        else error = read.error();
        listeners.forEach(Runnable::run);
    }

    // Worker side. Returns null when nothing changed and the read was conditional.
    private Read read(boolean force) {
        try { return readFile(force); }
        catch (IOException failure) { return new Read(List.of(), List.of(), "tunnels.toml is unreadable: " + failure.getMessage(), -2, null); }
    }

    private Read readFile(boolean force) throws IOException {
        if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) return force || size != -1 ? new Read(List.of(), List.of(), null, -1, null) : null;
        if (!Files.isRegularFile(file)) throw new IOException("not a readable regular file");
        long currentSize = Files.size(file);
        FileTime currentModified = Files.getLastModifiedTime(file);
        if (!force && currentSize == size && currentModified.equals(modified)) return null;
        if (currentSize > TunnelFile.MAX_BYTES)
            return new Read(List.of(), List.of(), "tunnels.toml is larger than " + TunnelFile.MAX_BYTES + " bytes", currentSize, currentModified);
        String text = Files.readString(file, StandardCharsets.UTF_8);
        try {
            TunnelFile.Parsed parsed = TunnelFile.parse(text);
            return new Read(parsed.tunnels(), parsed.warnings(), null, currentSize, currentModified);
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

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-plugin-remote:test --tests dev.jasper.remote.tunnels.TunnelStoreTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

Run the hygiene check (expected: no output), then:

```bash
git branch --show-current
git add plugins/remote/src/main/java/dev/jasper/remote/tunnels/TunnelStore.java plugins/remote/src/test/java/dev/jasper/remote/tunnels/TunnelStoreTest.java
git commit -m "Remote: TunnelStore keeps tunnels.toml like hosts.toml

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---
### Task 3: Engine pieces — `Counters`, `Pump`, `Listener`, `Socks5`

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/tunnels/Counters.java`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/tunnels/Pump.java`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/tunnels/Listener.java`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/tunnels/Socks5.java`
- Create (test support): `plugins/remote/src/test/java/dev/jasper/remote/tunnels/EchoServer.java`
- Create (test support): `plugins/remote/src/test/java/dev/jasper/remote/tunnels/TunnelsEngineTestAccess.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/tunnels/ListenerTest.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/tunnels/PumpTest.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/tunnels/Socks5Test.java`

**Interfaces:**
- Consumes: `Tunnel.LOCALHOST`, `Tunnel.ALL`; `dev.jasper.remote.client.Failures.Failure` (an unchecked exception whose message is already in the user's words).
- Produces:
  - `public final class Counters` with `long received()`, `long sent()`, `int open()`, `Optional<String> warning()`, and package-private `opened()`, `closed()`, `received(long)`, `sent(long)`, `refused(String)`.
  - `final class Pump` with `interface End { InputStream in() throws IOException; OutputStream out() throws IOException; void finish() throws IOException; void close(); }`, `static End of(Socket)`, `static End of(ChannelDirectTcpip)`, `static void run(End near, End far, Counters counters)`.
  - `final class Listener implements AutoCloseable` with `static Listener open(String bindAddress, int port, Consumer<Socket> accepted) throws IOException` (bind failures throw `Failures.Failure`), `int port()`, `void close()`, `static List<InetAddress> addresses(String bindAddress)`, `static String bindMessage(BindException, String address, int port)`.
  - `final class Socks5` with `record Target(String host, int port)`, `static Target accept(InputStream, OutputStream) throws IOException`, `static void reply(OutputStream, int code) throws IOException`, codes `SUCCEEDED = 0`, `HOST_UNREACHABLE = 4`, `COMMAND_NOT_SUPPORTED = 7`, `ADDRESS_NOT_SUPPORTED = 8`, `static final class Refused extends IOException`.
  - Test support (public, in `dev.jasper.remote.tunnels` test sources, used by later tasks and other packages): `EchoServer implements AutoCloseable` with `int port()`, `static String roundTrip(int port, String text) throws IOException`; `TunnelsEngineTestAccess.count(Counters, int open, long received, long sent)`, `TunnelsEngineTestAccess.refuse(Counters, String)`.

- [ ] **Step 1: Write the test support and the failing tests**

Create `plugins/remote/src/test/java/dev/jasper/remote/tunnels/EchoServer.java`:

```java
package dev.jasper.remote.tunnels;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** A TCP echo server on 127.0.0.1 for tunnel tests: each connection gets its own bytes back, then a half-close. */
public final class EchoServer implements AutoCloseable {
    private final ServerSocket socket;

    public EchoServer() throws IOException {
        socket = new ServerSocket(0, 50, InetAddress.ofLiteral("127.0.0.1"));
        Thread.ofVirtual().name("echo-accept").start(this::serve);
    }

    public int port() { return socket.getLocalPort(); }

    private void serve() {
        while (!socket.isClosed()) {
            Socket client;
            try { client = socket.accept(); } catch (IOException closed) { return; }
            Thread.ofVirtual().name("echo").start(() -> {
                try (client) { client.getInputStream().transferTo(client.getOutputStream()); client.shutdownOutput(); }
                catch (IOException ignored) { /* the test hung up */ }
            });
        }
    }

    /** Connects to {@code 127.0.0.1:port}, sends {@code text}, half-closes and returns everything that comes back. */
    public static String roundTrip(int port, String text) throws IOException {
        try (var client = new Socket(InetAddress.ofLiteral("127.0.0.1"), port)) {
            client.setSoTimeout(10_000);
            client.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
            client.shutdownOutput();
            return new String(client.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override public void close() throws IOException { socket.close(); }
}
```

Create `plugins/remote/src/test/java/dev/jasper/remote/tunnels/TunnelsEngineTestAccess.java`:

```java
package dev.jasper.remote.tunnels;

/** Owner-package access to counters for UI tests in other packages. */
public final class TunnelsEngineTestAccess {
    private TunnelsEngineTestAccess() { }

    /** Sets what a running tunnel would have counted. */
    public static void count(Counters counters, int open, long received, long sent) {
        for (int i = 0; i < open; i++) counters.opened();
        counters.received(received);
        counters.sent(sent);
    }

    /** Records a refused connection, as the engine does. */
    public static void refuse(Counters counters, String reason) { counters.refused(reason); }
}
```

Create `plugins/remote/src/test/java/dev/jasper/remote/tunnels/ListenerTest.java`:

```java
package dev.jasper.remote.tunnels;

import dev.jasper.remote.client.Failures;
import java.io.IOException;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ListenerTest {
    @Test void acceptsOnLocalhostUntilClosed() throws Exception {
        Listener listener = Listener.open(Tunnel.LOCALHOST, 0, socket -> {
            try (socket) { socket.getOutputStream().write("hi".getBytes(StandardCharsets.UTF_8)); }
            catch (IOException ignored) { }
        });
        int port = listener.port();
        assertThat(port).isPositive();
        try (var client = new Socket(InetAddress.ofLiteral("127.0.0.1"), port)) {
            assertThat(new String(client.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hi");
        }
        listener.close();
        listener.close();
        assertThatThrownBy(() -> new Socket(InetAddress.ofLiteral("127.0.0.1"), port).close()).isInstanceOf(ConnectException.class);
    }

    @Test void aBusyPortIsReportedInTheUsersWords() throws Exception {
        try (var busy = new ServerSocket(0, 50, InetAddress.ofLiteral("127.0.0.1"))) {
            assertThatThrownBy(() -> Listener.open("127.0.0.1", busy.getLocalPort(), socket -> { }))
                .isInstanceOf(Failures.Failure.class).hasMessage("Port " + busy.getLocalPort() + " is already in use on this computer");
        }
    }

    @Test void anAddressThisComputerDoesNotHaveIsReported() {
        // 192.0.2.0/24 is TEST-NET-1: never assigned to a real interface.
        assertThatThrownBy(() -> Listener.open("192.0.2.1", 0, socket -> { }))
            .isInstanceOf(Failures.Failure.class).hasMessage("Cannot listen on 192.0.2.1: not an address of this computer");
    }

    @Test void bindFailuresNameTheirCause() {
        assertThat(Listener.bindMessage(new BindException("Permission denied"), "localhost", 80)).isEqualTo("Port 80 needs administrator rights");
        assertThat(Listener.bindMessage(new BindException("Address already in use"), "localhost", 5432))
            .isEqualTo("Port 5432 is already in use on this computer");
        assertThat(Listener.bindMessage(new BindException("Something else"), "localhost", 1)).isEqualTo("Cannot listen on localhost:1: Something else");
    }

    @Test void localhostMeansBothLoopbacksAndAStarMeansEveryAddress() {
        assertThat(Listener.addresses(Tunnel.LOCALHOST)).extracting(InetAddress::getHostAddress).containsExactly("127.0.0.1", "0:0:0:0:0:0:0:1");
        assertThat(Listener.addresses(Tunnel.ALL)).singleElement().matches(InetAddress::isAnyLocalAddress);
        assertThat(Listener.addresses("10.1.2.3")).extracting(InetAddress::getHostAddress).containsExactly("10.1.2.3");
    }
}
```

Create `plugins/remote/src/test/java/dev/jasper/remote/tunnels/PumpTest.java`:

```java
package dev.jasper.remote.tunnels;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PumpTest {
    @Test void copiesBothWaysCountsBytesAndPassesTheHalfCloseOn() throws Exception {
        try (var echo = new EchoServer(); var front = new ServerSocket(0, 50, InetAddress.ofLiteral("127.0.0.1"))) {
            var counters = new Counters();
            Thread pumping = Thread.ofVirtual().start(() -> {
                try {
                    Socket near = front.accept();
                    Socket far = new Socket(InetAddress.ofLiteral("127.0.0.1"), echo.port());
                    Pump.run(Pump.of(near), Pump.of(far), counters);
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
            });
            assertThat(EchoServer.roundTrip(front.getLocalPort(), "hello tunnel")).isEqualTo("hello tunnel");
            pumping.join(10_000);
            assertThat(pumping.isAlive()).as("both directions ended").isFalse();
            assertThat(counters.sent()).isEqualTo(12);
            assertThat(counters.received()).isEqualTo(12);
        }
    }

    @Test void countersStartEmptyAndTheLatestRefusalIsClearedByTheNextConnection() {
        var counters = new Counters();
        assertThat(counters.open()).isZero();
        assertThat(counters.warning()).isEmpty();
        counters.refused("Connections are being refused: Connection refused");
        assertThat(counters.warning()).contains("Connections are being refused: Connection refused");
        counters.opened();
        assertThat(counters.warning()).isEmpty();
        assertThat(counters.open()).isEqualTo(1);
        counters.closed();
        assertThat(counters.open()).isZero();
    }
}
```

Create `plugins/remote/src/test/java/dev/jasper/remote/tunnels/Socks5Test.java`:

```java
package dev.jasper.remote.tunnels;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class Socks5Test {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();

    static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }

    Socks5.Target accept(byte[] request) throws IOException { return Socks5.accept(new ByteArrayInputStream(request), out); }

    @Test void aDomainNameIsPassedOnUnresolved() throws IOException {
        byte[] name = "db.internal".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var request = new ByteArrayOutputStream();
        request.writeBytes(bytes(5, 2, 0, 2, 5, 1, 0, 3, name.length));
        request.writeBytes(name);
        request.writeBytes(bytes(0x15, 0x38));
        assertThat(accept(request.toByteArray())).isEqualTo(new Socks5.Target("db.internal", 5432));
        assertThat(out.toByteArray()).as("no authentication chosen").containsExactly(5, 0);
    }

    @Test void ipv4AndIpv6AddressesAreRead() throws IOException {
        assertThat(accept(bytes(5, 1, 0, 5, 1, 0, 1, 127, 0, 0, 1, 0x1F, 0x90))).isEqualTo(new Socks5.Target("127.0.0.1", 8080));
        out.reset();
        assertThat(accept(bytes(5, 1, 0, 5, 1, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 22)))
            .isEqualTo(new Socks5.Target("0:0:0:0:0:0:0:1", 22));
    }

    @Test void aClientThatInsistsOnAuthenticationIsTurnedAway() {
        assertThatThrownBy(() -> accept(bytes(5, 1, 2))).isInstanceOf(Socks5.Refused.class);
        assertThat(out.toByteArray()).containsExactly(5, 0xFF);
    }

    @Test void onlyConnectIsServed() {
        assertThatThrownBy(() -> accept(bytes(5, 1, 0, 5, 2, 0, 1, 127, 0, 0, 1, 0, 80))).isInstanceOf(Socks5.Refused.class);
        assertThat(out.toByteArray()).containsExactly(5, 0, 5, Socks5.COMMAND_NOT_SUPPORTED, 0, 1, 0, 0, 0, 0, 0, 0);
    }

    @Test void anUnknownAddressTypeIsRefused() {
        assertThatThrownBy(() -> accept(bytes(5, 1, 0, 5, 1, 0, 9))).isInstanceOf(Socks5.Refused.class);
        assertThat(out.toByteArray()).containsExactly(5, 0, 5, Socks5.ADDRESS_NOT_SUPPORTED, 0, 1, 0, 0, 0, 0, 0, 0);
    }

    @Test void socks4IsNotSpokenAndTheReplyCarriesItsCode() throws IOException {
        assertThatThrownBy(() -> accept(bytes(4, 1, 0, 80, 127, 0, 0, 1, 0))).isInstanceOf(Socks5.Refused.class);
        assertThat(out.toByteArray()).isEmpty();
        Socks5.reply(out, Socks5.HOST_UNREACHABLE);
        assertThat(out.toByteArray()).containsExactly(5, 4, 0, 1, 0, 0, 0, 0, 0, 0);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.tunnels.*'`
Expected: FAIL — compilation errors, `cannot find symbol: class Counters` / `Listener` / `Pump` / `Socks5`.

- [ ] **Step 3: Write the engine pieces**

Create `plugins/remote/src/main/java/dev/jasper/remote/tunnels/Counters.java`:

```java
package dev.jasper.remote.tunnels;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/** Live numbers for one running tunnel: written by its connection threads, read by the UI once a second. */
public final class Counters {
    private final LongAdder received = new LongAdder(), sent = new LongAdder();
    private final AtomicInteger open = new AtomicInteger();
    private volatile String warning;

    /** Bytes that came back from the far end toward whoever connected. */
    public long received() { return received.sum(); }
    /** Bytes that went from whoever connected toward the far end. */
    public long sent() { return sent.sum(); }
    /** Connections forwarding right now. */
    public int open() { return open.get(); }
    /** Why the most recent connection could not be forwarded, until another one is. */
    public Optional<String> warning() { return Optional.ofNullable(warning); }

    void opened() { open.incrementAndGet(); warning = null; }
    void closed() { open.decrementAndGet(); }
    void received(long bytes) { received.add(bytes); }
    void sent(long bytes) { sent.add(bytes); }
    void refused(String reason) { warning = reason; }
}
```

Create `plugins/remote/src/main/java/dev/jasper/remote/tunnels/Pump.java`:

```java
package dev.jasper.remote.tunnels;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.function.LongConsumer;
import org.apache.sshd.client.channel.ChannelDirectTcpip;

/** Copies bytes both ways between whoever connected and the far end, counting them and passing half-closes on. */
final class Pump {
    /** One side of a forwarded connection. */
    interface End {
        InputStream in() throws IOException;
        OutputStream out() throws IOException;
        /** Nothing more will be written to this side: a TCP half-close, or EOF on an SSH channel. */
        void finish() throws IOException;
        /** Closes this side; safe to call more than once and from any thread. */
        void close();
    }

    private static final int BUFFER = 32 * 1024;

    private Pump() { }

    static End of(Socket socket) {
        return new End() {
            @Override public InputStream in() throws IOException { return socket.getInputStream(); }
            @Override public OutputStream out() throws IOException { return socket.getOutputStream(); }
            @Override public void finish() throws IOException { socket.shutdownOutput(); }
            @Override public void close() { try { socket.close(); } catch (IOException ignored) { /* already gone */ } }
        };
    }

    static End of(ChannelDirectTcpip channel) {
        return new End() {
            @Override public InputStream in() { return channel.getInvertedOut(); }
            @Override public OutputStream out() { return channel.getInvertedIn(); }
            // Closing the channel's output stream sends SSH_MSG_CHANNEL_EOF; the server half-closes its socket.
            @Override public void finish() throws IOException { channel.getInvertedIn().close(); }
            @Override public void close() { channel.close(false); }
        };
    }

    /** Blocks until both directions end, then closes both ends. {@code near} is whoever connected; {@code far} the forwarded side. */
    static void run(End near, End far, Counters counters) {
        Runnable abort = () -> { near.close(); far.close(); };
        Thread upstream = Thread.ofVirtual().name("jasper-tunnel-pump").start(() -> copy(near, far, counters::sent, abort));
        copy(far, near, counters::received, abort);
        try { upstream.join(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        abort.run();
    }

    private static void copy(End from, End to, LongConsumer counted, Runnable abort) {
        byte[] buffer = new byte[BUFFER];
        try {
            InputStream in = from.in();
            OutputStream out = to.out();
            for (int read; (read = in.read(buffer)) >= 0; ) {
                if (read == 0) continue;
                out.write(buffer, 0, read);
                out.flush();
                counted.accept(read);
            }
            to.finish();
        } catch (IOException | RuntimeException broken) {
            // One side failed: closing both ends the other direction too.
            abort.run();
        }
    }
}
```

Create `plugins/remote/src/main/java/dev/jasper/remote/tunnels/Listener.java`:

```java
package dev.jasper.remote.tunnels;

import dev.jasper.remote.client.Failures;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A tunnel's listening sockets on this computer: bound at start, closed at stop. {@code localhost} binds both loopback
 * addresses, like OpenSSH (::1 only where the machine has it); {@code *} binds the wildcard. One virtual thread per
 * socket accepts, and each connection gets its own virtual thread.
 */
final class Listener implements AutoCloseable {
    private final List<ServerSocket> sockets;

    private Listener(List<ServerSocket> sockets) { this.sockets = List.copyOf(sockets); }

    /**
     * Binds and starts accepting; {@code accepted} runs on a new virtual thread per connection and owns its socket.
     *
     * @throws Failures.Failure when binding fails, with the reason in the user's words
     */
    static Listener open(String bindAddress, int port, Consumer<Socket> accepted) throws IOException {
        List<InetAddress> addresses = addresses(bindAddress);
        var sockets = new ArrayList<ServerSocket>();
        try {
            sockets.add(bind(addresses.getFirst(), port, bindAddress));
            int bound = sockets.getFirst().getLocalPort();
            for (InetAddress extra : addresses.subList(1, addresses.size())) {
                try { sockets.add(bind(extra, bound, bindAddress)); }
                catch (IOException | RuntimeException unavailable) { /* best effort: no IPv6 loopback here */ }
            }
        } catch (IOException | RuntimeException failure) {
            sockets.forEach(Listener::quietly);
            throw failure;
        }
        var listener = new Listener(sockets);
        for (ServerSocket socket : listener.sockets) Thread.ofVirtual().name("jasper-tunnel-accept").start(() -> accept(socket, accepted));
        return listener;
    }

    /** The port every socket listens on. */
    int port() { return sockets.getFirst().getLocalPort(); }

    @Override public void close() { sockets.forEach(Listener::quietly); }

    /** The addresses a bind address stands for; never asks DNS. */
    static List<InetAddress> addresses(String bindAddress) {
        return switch (bindAddress) {
            case Tunnel.LOCALHOST -> List.of(InetAddress.ofLiteral("127.0.0.1"), InetAddress.ofLiteral("::1"));
            case Tunnel.ALL -> List.of(new InetSocketAddress(0).getAddress());
            default -> List.of(InetAddress.ofLiteral(bindAddress));
        };
    }

    static String bindMessage(BindException failure, String address, int port) {
        String text = String.valueOf(failure.getMessage()).toLowerCase(Locale.ROOT);
        if (text.contains("in use")) return "Port " + port + " is already in use on this computer";
        if (text.contains("permission") || text.contains("access")) return "Port " + port + " needs administrator rights";
        if (text.contains("assign")) return "Cannot listen on " + address + ": not an address of this computer";
        return "Cannot listen on " + address + ":" + port + ": " + failure.getMessage();
    }

    private static ServerSocket bind(InetAddress address, int port, String shown) throws IOException {
        var socket = new ServerSocket();
        try {
            socket.bind(new InetSocketAddress(address, port), 50);
            return socket;
        } catch (BindException refused) {
            quietly(socket);
            throw new Failures.Failure(bindMessage(refused, shown, port), refused);
        } catch (IOException | RuntimeException other) {
            quietly(socket);
            throw other;
        }
    }

    private static void accept(ServerSocket socket, Consumer<Socket> accepted) {
        while (!socket.isClosed()) {
            Socket client;
            try { client = socket.accept(); }
            catch (IOException closedOrBroken) { return; }
            Thread.ofVirtual().name("jasper-tunnel-connection").start(() -> accepted.accept(client));
        }
    }

    private static void quietly(ServerSocket socket) {
        try { socket.close(); } catch (IOException ignored) { /* closing */ }
    }
}
```

Create `plugins/remote/src/main/java/dev/jasper/remote/tunnels/Socks5.java`:

```java
package dev.jasper.remote.tunnels;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/** The server side of a SOCKS5 handshake (RFC 1928): no authentication, CONNECT only. The caller owns the streams. */
final class Socks5 {
    static final int SUCCEEDED = 0, HOST_UNREACHABLE = 4, COMMAND_NOT_SUPPORTED = 7, ADDRESS_NOT_SUPPORTED = 8;

    /** Where the client asked to go. A domain name stays unresolved, so the server resolves it (the socks5h behaviour). */
    record Target(String host, int port) { }

    /** A request this proxy does not serve; the client has already been told why when the protocol allows it. */
    static final class Refused extends IOException {
        Refused(String message) { super(message); }
    }

    private Socks5() { }

    /** Reads the greeting and the request and answers the greeting; the caller sends the final {@link #reply}. */
    static Target accept(InputStream input, OutputStream out) throws IOException {
        var in = new DataInputStream(input);
        if (in.readUnsignedByte() != 5) throw new Refused("not SOCKS5");
        int methods = in.readUnsignedByte();
        boolean noAuthentication = false;
        for (int i = 0; i < methods; i++) if (in.readUnsignedByte() == 0) noAuthentication = true;
        if (!noAuthentication) {
            out.write(new byte[] {5, (byte) 0xFF});
            out.flush();
            throw new Refused("no acceptable authentication method");
        }
        out.write(new byte[] {5, 0});
        out.flush();
        if (in.readUnsignedByte() != 5) throw new Refused("not SOCKS5");
        int command = in.readUnsignedByte();
        in.readUnsignedByte();
        int type = in.readUnsignedByte();
        String host = switch (type) {
            case 1 -> { byte[] address = new byte[4]; in.readFully(address); yield InetAddress.getByAddress(address).getHostAddress(); }
            case 3 -> { byte[] name = new byte[in.readUnsignedByte()]; in.readFully(name); yield new String(name, StandardCharsets.US_ASCII); }
            case 4 -> { byte[] address = new byte[16]; in.readFully(address); yield InetAddress.getByAddress(address).getHostAddress(); }
            default -> { reply(out, ADDRESS_NOT_SUPPORTED); throw new Refused("address type " + type + " is not supported"); }
        };
        int port = in.readUnsignedShort();
        if (command != 1) { reply(out, COMMAND_NOT_SUPPORTED); throw new Refused("only CONNECT is supported"); }
        return new Target(host, port);
    }

    /** The answer to the request, with an unspecified bound address. */
    static void reply(OutputStream out, int code) throws IOException {
        out.write(new byte[] {5, (byte) code, 0, 1, 0, 0, 0, 0, 0, 0});
        out.flush();
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.tunnels.*'`
Expected: PASS (TunnelFileTest 5, TunnelStoreTest 3, ListenerTest 5, PumpTest 2, Socks5Test 6).

- [ ] **Step 5: Commit**

Run the hygiene check (expected: no output), then:

```bash
git branch --show-current
git add plugins/remote/src/main/java/dev/jasper/remote/tunnels plugins/remote/src/test/java/dev/jasper/remote/tunnels
git commit -m "Remote: tunnel engine pieces (listener, pump, counters, SOCKS5)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---
### Task 4: `TunnelManager` — start, stop and forward all three kinds

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/tunnels/TunnelState.java`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/tunnels/TunnelManager.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/tunnels/TunnelManagerTest.java`

**Interfaces:**
- Consumes: Task 1's `Tunnel`, `TunnelKind`; Task 3's `Counters`, `Pump`, `Listener`, `Socks5`, `EchoServer`; `Connections.identity(UUID)` (throws `Failures.Failure("Host not found")` or `("jump host missing")`), `Connections.lease(ConnectionIdentity, WindowHandle, Consumer<String>)` (its status consumer is called on the UI executor), `SessionLease.session()` / `close()`, `Failures.message(Throwable, Duration, String)`; test-only `LoopbackServer` (`dev.jasper.remote.client`, allows all forwarding).
- Produces:
  - `sealed interface TunnelState` with `record Stopped()`, `record Starting(String stage)`, `record Up(Instant since, int boundPort, Counters counters)`, `record Failed(String reason)`, `TunnelState.STOPPED`, `default boolean running()` (Starting or Up).
  - `TunnelManager(Connections connections, Supplier<Duration> timeout, Executor background, Executor ui)` with `TunnelState state(UUID)`, `Optional<Tunnel> running(UUID)`, `List<Tunnel> runningTunnels()`, `List<Tunnel> quickForwards()`, `int active()`, `int failed()`, `Subscription onChanged(Runnable)`, `void start(Tunnel, WindowHandle ownerOrNull)`, `void forward(Tunnel quickForward, WindowHandle ownerOrNull)`, `void saved(UUID)`, `void stop(UUID)`, `void stopAll()`, `void close()`; all UI-thread-only.

- [ ] **Step 1: Write the failing test**

Create `plugins/remote/src/test/java/dev/jasper/remote/tunnels/TunnelManagerTest.java`:

```java
package dev.jasper.remote.tunnels;

import dev.jasper.remote.RemoteSettings;
import dev.jasper.remote.client.Connections;
import dev.jasper.remote.client.HostKeyVerifier;
import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.vault.api.Credential;
import dev.jasper.vault.api.Kind;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class TunnelManagerTest {
    final ExecutorService background = Executors.newCachedThreadPool(Thread.ofPlatform().daemon().name("bg-", 0).factory());
    final ExecutorService ui = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("ui").factory());
    final Map<UUID, RemoteHost> hosts = new HashMap<>();
    final List<Runnable> lingering = new CopyOnWriteArrayList<>();
    final RemoteSettings settings = new RemoteSettings(Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ZERO, Duration.ZERO, false, true);
    @TempDir Path dir;
    LoopbackServer server;
    EchoServer echo;
    Connections connections;
    TunnelManager manager;
    RemoteHost prod;
    volatile String password = "s3cret";

    @BeforeEach void open() throws Exception {
        server = new LoopbackServer();
        echo = new EchoServer();
        var trust = new KnownHosts(dir.resolve("known_hosts"), Optional.empty());
        trust.trust("127.0.0.1", server.port(), server.hostPublicKey());
        Function<UUID, CompletableFuture<Optional<Credential>>> vault = id -> CompletableFuture.completedFuture(Optional.of(
            new Credential(id, "deploy login", Kind.ACCOUNT_PASSWORD, "deploy", password.toCharArray(), null, null)));
        connections = new Connections(() -> settings, trust, Optional.empty(), id -> Optional.ofNullable(hosts.get(id)), Optional.of(vault),
            question -> CompletableFuture.completedFuture(HostKeyVerifier.Decision.CANCEL), background, ui,
            (delay, task) -> { lingering.add(task); return () -> lingering.remove(task); });
        manager = new TunnelManager(connections, () -> Duration.ofSeconds(5), background, ui);
        prod = RemoteHost.create("prod", "127.0.0.1", server.port(), "", new Auth.Vault(UUID.randomUUID()), "", Optional.empty());
        hosts.put(prod.id(), prod);
    }

    @AfterEach void close() throws Exception {
        onUi(() -> { manager.close(); connections.close(); });
        echo.close();
        server.close();
        background.shutdownNow();
        ui.shutdownNow();
    }

    /** Runs on the fake UI thread and waits: the manager is UI-thread-only. */
    <T> T onUi(Supplier<T> work) {
        try { return ui.submit(work::get).get(10, TimeUnit.SECONDS); } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    void onUi(Runnable work) { onUi(() -> { work.run(); return null; }); }

    TunnelState await(UUID id, Predicate<TunnelState> wanted, String what) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (true) {
            TunnelState state = onUi(() -> manager.state(id));
            if (wanted.test(state)) return state;
            if (System.nanoTime() > deadline) throw new AssertionError("timed out waiting for " + what + "; last state " + state);
            Thread.sleep(20);
        }
    }

    static void awaitTrue(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("timed out waiting for " + what);
            Thread.sleep(20);
        }
    }

    int up(Tunnel tunnel) throws InterruptedException {
        onUi(() -> manager.start(tunnel, null));
        return ((TunnelState.Up) await(tunnel.id(), TunnelState.Up.class::isInstance, tunnel.name() + " up")).boundPort();
    }

    Counters counters(Tunnel tunnel) { return ((TunnelState.Up) onUi(() -> manager.state(tunnel.id()))).counters(); }

    Tunnel local(String bindAddress, int bindPort, int targetPort) {
        return Tunnel.create("db", prod.id(), TunnelKind.LOCAL, bindAddress, bindPort, "127.0.0.1", targetPort);
    }

    static int closedPort() throws IOException {
        try (var probe = new ServerSocket(0, 50, InetAddress.ofLiteral("127.0.0.1"))) { return probe.getLocalPort(); }
    }

    static boolean refused(int port) {
        try (var socket = new Socket(InetAddress.ofLiteral("127.0.0.1"), port)) { return false; }
        catch (ConnectException refused) { return true; }
        catch (IOException other) { return false; }
    }

    /** Whether the other side hung up: end of stream or a reset. */
    static boolean ended(Socket socket) {
        try { return socket.getInputStream().read() == -1; } catch (IOException reset) { return true; }
    }

    @Test void aLocalTunnelForwardsBothWaysAndCounts() throws Exception {
        var changes = new AtomicInteger();
        onUi(() -> manager.onChanged(changes::incrementAndGet));
        Tunnel db = local(Tunnel.LOCALHOST, 0, echo.port());
        int port = up(db);
        assertThat(EchoServer.roundTrip(port, "hello")).isEqualTo("hello");
        assertThat(EchoServer.roundTrip(port, "again")).isEqualTo("again");
        Counters counters = counters(db);
        awaitTrue(() -> counters.open() == 0 && counters.received() == 10 && counters.sent() == 10, "the bytes to be counted");
        assertThat(onUi(() -> manager.active())).isEqualTo(1);
        assertThat(onUi(() -> manager.running(db.id()))).contains(db);
        assertThat(onUi(() -> manager.runningTunnels())).containsExactly(db);
        assertThat(server.server.getActiveSessions()).as("one session for every connection").hasSize(1);
        assertThat(changes.get()).as("binding, connecting, up").isGreaterThanOrEqualTo(3);
    }

    @Test void stopClosesTheListenerAndLiveConnectionsAndReleasesTheSession() throws Exception {
        Tunnel db = local("127.0.0.1", 0, echo.port());
        int port = up(db);
        try (var client = new Socket(InetAddress.ofLiteral("127.0.0.1"), port)) {
            client.setSoTimeout(10_000);
            client.getOutputStream().write('x');
            assertThat(client.getInputStream().read()).isEqualTo('x');
            onUi(() -> manager.stop(db.id()));
            assertThat(ended(client)).as("the live connection was closed").isTrue();
        }
        assertThat(onUi(() -> manager.state(db.id()))).isEqualTo(TunnelState.STOPPED);
        assertThat(onUi(() -> manager.active())).isZero();
        awaitTrue(() -> refused(port), "the port to close");
        awaitTrue(() -> !lingering.isEmpty(), "the session to linger");
        List.copyOf(lingering).forEach(Runnable::run);
        awaitTrue(() -> server.server.getActiveSessions().isEmpty(), "the session to close");
    }

    @Test void aBusyPortFailsBeforeAnyConnection() throws Exception {
        try (var busy = new ServerSocket(0, 50, InetAddress.ofLiteral("127.0.0.1"))) {
            Tunnel db = local("127.0.0.1", busy.getLocalPort(), echo.port());
            onUi(() -> manager.start(db, null));
            assertThat(onUi(() -> manager.state(db.id())))
                .isEqualTo(new TunnelState.Failed("Port " + busy.getLocalPort() + " is already in use on this computer"));
            assertThat(server.server.getActiveSessions()).as("nothing connected").isEmpty();
            assertThat(onUi(() -> manager.failed())).isEqualTo(1);
            onUi(() -> manager.stop(db.id()));
            assertThat(onUi(() -> manager.state(db.id()))).as("Stop clears a failure").isEqualTo(TunnelState.STOPPED);
            assertThat(onUi(() -> manager.failed())).isZero();
        }
    }

    @Test void aMissingHostFails() {
        Tunnel orphan = Tunnel.create("orphan", UUID.randomUUID(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 0, "x", 1);
        onUi(() -> manager.start(orphan, null));
        assertThat(onUi(() -> manager.state(orphan.id()))).isEqualTo(new TunnelState.Failed("Host missing"));
    }

    @Test void aRejectedLoginFailsTheTunnel() throws Exception {
        password = "wrong";
        Tunnel db = local(Tunnel.LOCALHOST, 0, echo.port());
        onUi(() -> manager.start(db, null));
        var failed = (TunnelState.Failed) await(db.id(), TunnelState.Failed.class::isInstance, "authentication to fail");
        assertThat(failed.reason()).startsWith("Authentication failed");
    }

    @Test void losingTheSessionFailsTheTunnelAndFreesThePort() throws Exception {
        Tunnel db = local("127.0.0.1", 0, echo.port());
        int port = up(db);
        server.server.getActiveSessions().forEach(session -> session.close(true));
        assertThat(await(db.id(), TunnelState.Failed.class::isInstance, "the loss")).isEqualTo(new TunnelState.Failed("Connection lost"));
        awaitTrue(() -> refused(port), "the port to close");
    }

    @Test void aRefusedTargetWarnsButTheTunnelStaysUp() throws Exception {
        Tunnel nowhere = local("127.0.0.1", 0, closedPort());
        int port = up(nowhere);
        try (var client = new Socket(InetAddress.ofLiteral("127.0.0.1"), port)) {
            client.setSoTimeout(10_000);
            assertThat(ended(client)).isTrue();
        }
        Counters counters = counters(nowhere);
        awaitTrue(() -> counters.warning().isPresent(), "a warning");
        assertThat(counters.warning().orElseThrow()).startsWith("Connections are being refused: ");
        assertThat(onUi(() -> manager.state(nowhere.id()))).isInstanceOf(TunnelState.Up.class);
    }

    @Test void aDynamicTunnelIsASocksProxyThatResolvesOnTheServer() throws Exception {
        Tunnel socks = Tunnel.create("socks", prod.id(), TunnelKind.DYNAMIC, "127.0.0.1", 0, "", 0);
        int port = up(socks);
        var proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(InetAddress.ofLiteral("127.0.0.1"), port));
        try (var client = new Socket(proxy)) {
            client.setSoTimeout(10_000);
            client.connect(InetSocketAddress.createUnresolved("127.0.0.1", echo.port()), 10_000);
            client.getOutputStream().write("hi".getBytes(StandardCharsets.UTF_8));
            client.shutdownOutput();
            assertThat(new String(client.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hi");
        }
        int closed = closedPort();
        try (var client = new Socket(proxy)) {
            assertThatThrownBy(() -> client.connect(InetSocketAddress.createUnresolved("127.0.0.1", closed), 10_000))
                .as("the SOCKS reply says the host is unreachable").isInstanceOf(IOException.class);
        }
        assertThat(counters(socks).warning()).get().asString().startsWith("Connections are being refused: ");
    }

    @Test void aRemoteTunnelOpensAPortOnTheServerThatComesBackToTheTarget() throws Exception {
        Tunnel back = Tunnel.create("back", prod.id(), TunnelKind.REMOTE, "127.0.0.1", 0, "127.0.0.1", echo.port());
        int serverPort = up(back);
        assertThat(serverPort).as("the server chose a port").isPositive();
        assertThat(EchoServer.roundTrip(serverPort, "through the server")).isEqualTo("through the server");
        Counters counters = counters(back);
        awaitTrue(() -> counters.sent() == 18 && counters.received() == 18, "the relay to count");
        onUi(() -> manager.stop(back.id()));
        awaitTrue(() -> refused(serverPort), "the server to close its port");
    }

    @Test void aRemotePortTheServerCannotOpenFailsTheTunnel() throws Exception {
        try (var busy = new ServerSocket(0, 50, InetAddress.ofLiteral("127.0.0.1"))) {
            Tunnel back = Tunnel.create("back", prod.id(), TunnelKind.REMOTE, "127.0.0.1", busy.getLocalPort(), "127.0.0.1", echo.port());
            onUi(() -> manager.start(back, null));
            assertThat(await(back.id(), TunnelState.Failed.class::isInstance, "the refusal"))
                .isEqualTo(new TunnelState.Failed("The server refused port " + busy.getLocalPort() + ": already in use or not allowed"));
        }
    }

    @Test void quickForwardsAreListedUntilStoppedOrSaved() throws Exception {
        Tunnel quick = Tunnel.create("", prod.id(), TunnelKind.LOCAL, "127.0.0.1", 0, "127.0.0.1", echo.port());
        onUi(() -> manager.forward(quick, null));
        assertThat(onUi(() -> manager.quickForwards())).containsExactly(quick);
        await(quick.id(), TunnelState.Up.class::isInstance, "up");
        onUi(() -> manager.stop(quick.id()));
        assertThat(onUi(() -> manager.quickForwards())).as("a stopped quick forward disappears").isEmpty();

        Tunnel kept = Tunnel.create("", prod.id(), TunnelKind.LOCAL, "127.0.0.1", 0, "127.0.0.1", echo.port());
        onUi(() -> manager.forward(kept, null));
        await(kept.id(), TunnelState.Up.class::isInstance, "up");
        onUi(() -> manager.saved(kept.id()));
        assertThat(onUi(() -> manager.quickForwards())).isEmpty();
        assertThat(onUi(() -> manager.running(kept.id()))).as("saving does not stop it").isPresent();
    }

    @Test void stopAllStopsEverythingAndClearsFailures() throws Exception {
        Tunnel db = local("127.0.0.1", 0, echo.port());
        up(db);
        Tunnel orphan = Tunnel.create("orphan", UUID.randomUUID(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 0, "x", 1);
        onUi(() -> manager.start(orphan, null));
        onUi(() -> manager.stopAll());
        assertThat(onUi(() -> manager.active())).isZero();
        assertThat(onUi(() -> manager.failed())).isZero();
        assertThat(onUi(() -> manager.state(db.id()))).isEqualTo(TunnelState.STOPPED);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-plugin-remote:test --tests dev.jasper.remote.tunnels.TunnelManagerTest`
Expected: FAIL — `cannot find symbol: class TunnelManager` / `TunnelState`.

- [ ] **Step 3: Write the state and the manager**

Create `plugins/remote/src/main/java/dev/jasper/remote/tunnels/TunnelState.java`:

```java
package dev.jasper.remote.tunnels;

import java.time.Instant;
import java.util.Objects;

/** Where a tunnel is. Plan 7b-3 adds Reconnecting and WaitingForVault. */
public sealed interface TunnelState {
    /** Not running. */
    record Stopped() implements TunnelState { }

    /** Binding, connecting, verifying the host key, authenticating or asking the server for its port; {@code stage} says which. */
    record Starting(String stage) implements TunnelState {
        public Starting { Objects.requireNonNull(stage, "stage"); }
    }

    /** Forwarding since {@code since} on {@code boundPort}: this computer's port, or the server's for a remote tunnel. */
    record Up(Instant since, int boundPort, Counters counters) implements TunnelState {
        public Up { Objects.requireNonNull(since, "since"); Objects.requireNonNull(counters, "counters"); }
    }

    /** Stopped by a failure it will not retry; {@code reason} is already in the user's words. */
    record Failed(String reason) implements TunnelState {
        public Failed { Objects.requireNonNull(reason, "reason"); }
    }

    TunnelState STOPPED = new Stopped();

    /** Whether it holds a listener: Starting or Up. */
    default boolean running() { return this instanceof Starting || this instanceof Up; }
}
```

Create `plugins/remote/src/main/java/dev/jasper/remote/tunnels/TunnelManager.java`:

```java
package dev.jasper.remote.tunnels;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.client.Connections;
import dev.jasper.remote.client.Failures;
import dev.jasper.remote.client.SessionLease;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import org.apache.sshd.client.channel.ChannelDirectTcpip;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.future.CloseFuture;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.util.net.SshdSocketAddress;

/**
 * Runs tunnels on the UI thread. A start binds first, then leases the host's shared session from {@link Connections}
 * and forwards every accepted connection over its own channel; a remote tunnel asks the server for its port, pointed at
 * a loopback relay. Each running tunnel holds its own lease, so closing terminals never stops it and stopping it never
 * touches a terminal. Connection threads read only a run's session and write its counters and tracked ends. In plan
 * 7b-2 a lost session fails the tunnel.
 */
public final class TunnelManager implements AutoCloseable {
    /** The loopback address a remote tunnel's relay listens on. */
    static final String RELAY = "127.0.0.1";

    /** What a SOCKS client hears once its channel opened or failed; nothing for a local tunnel. */
    private interface Answer { void opened(boolean ok) throws IOException; }

    private final Connections connections;
    private final Supplier<Duration> timeout;
    private final Executor background, ui;
    private final Map<UUID, Run> runs = new HashMap<>();
    private final Map<UUID, TunnelState> states = new HashMap<>();
    private final Map<UUID, Tunnel> quick = new LinkedHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private boolean closed;

    public TunnelManager(Connections connections, Supplier<Duration> timeout, Executor background, Executor ui) {
        this.connections = connections; this.timeout = timeout; this.background = background; this.ui = ui;
    }

    public TunnelState state(UUID id) { return states.getOrDefault(id, TunnelState.STOPPED); }
    /** The definition a tunnel runs with while it is Starting or Up. */
    public Optional<Tunnel> running(UUID id) { return Optional.ofNullable(runs.get(id)).map(run -> run.tunnel); }
    public List<Tunnel> runningTunnels() { return runs.values().stream().map(run -> run.tunnel).toList(); }
    /** Unsaved forwards, oldest first, until they are stopped or saved. */
    public List<Tunnel> quickForwards() { return List.copyOf(quick.values()); }
    /** Tunnels Starting or Up. */
    public int active() { return runs.size(); }
    public int failed() { return (int) states.values().stream().filter(TunnelState.Failed.class::isInstance).count(); }
    public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }

    /** Starts a tunnel; prompts (a Vault unlock, an unknown host key) go to {@code owner}, which may be null. A running one is left alone. */
    public void start(Tunnel tunnel, WindowHandle owner) {
        if (closed || runs.containsKey(tunnel.id())) return;
        UUID id = tunnel.id();
        var run = new Run(tunnel);
        runs.put(id, run);
        set(id, new TunnelState.Starting("Binding…"));
        ConnectionIdentity identity;
        try { identity = connections.identity(tunnel.hostId()); }
        catch (RuntimeException missing) { fail(run, "Host not found".equals(missing.getMessage()) ? "Host missing" : message(missing)); return; }
        try {
            run.listener = switch (tunnel.kind()) {
                case LOCAL -> Listener.open(tunnel.bindAddress(), tunnel.bindPort(),
                    socket -> carry(run, socket, new SshdSocketAddress(tunnel.targetHost(), tunnel.targetPort()), ok -> { }));
                case DYNAMIC -> Listener.open(tunnel.bindAddress(), tunnel.bindPort(), socket -> socks(run, socket));
                case REMOTE -> Listener.open(RELAY, 0, socket -> relay(run, socket));
            };
        } catch (IOException | RuntimeException failure) { fail(run, message(failure)); return; }
        set(id, new TunnelState.Starting("Connecting…"));
        run.leasing = connections.lease(identity, owner, stage -> { if (runs.get(id) == run) set(id, new TunnelState.Starting(stage)); });
        run.leasing.whenComplete((lease, failure) -> ui.execute(() -> leased(run, lease, failure)));
    }

    /** Lists a quick forward and starts it. */
    public void forward(Tunnel quickForward, WindowHandle owner) {
        if (closed) return;
        quick.put(quickForward.id(), quickForward);
        changed();
        start(quickForward, owner);
    }

    /** A quick forward was saved: it leaves the quick list and keeps running as the saved tunnel with the same id. */
    public void saved(UUID id) { if (quick.remove(id) != null) changed(); }

    /** Stops a tunnel: a running one closes its listener and connections and releases its lease; a failed one returns to Stopped; a quick forward disappears. */
    public void stop(UUID id) {
        Run run = runs.remove(id);
        if (run != null) teardown(run);
        boolean known = states.remove(id) != null | quick.remove(id) != null;
        if (run != null || known) changed();
    }

    public void stopAll() {
        var ids = new ArrayList<UUID>(states.keySet());
        ids.addAll(quick.keySet());
        for (UUID id : ids) stop(id);
    }

    /** Plugin stop: closes everything without telling listeners. */
    @Override public void close() {
        closed = true;
        for (Run run : List.copyOf(runs.values())) teardown(run);
        runs.clear(); states.clear(); quick.clear(); listeners.clear();
    }

    /** What the server is asked to listen on: {@code *} goes as 0.0.0.0, which MINA sends for an empty host anyway. */
    static String serverAddress(String bindAddress) { return bindAddress.equals(Tunnel.ALL) ? "0.0.0.0" : bindAddress; }

    private void leased(Run run, SessionLease lease, Throwable failure) {
        UUID id = run.tunnel.id();
        if (runs.get(id) != run) { if (lease != null) lease.close(); return; }
        if (failure != null) {
            if (cause(failure) instanceof CancellationException) stop(id); else fail(run, message(failure));
            return;
        }
        run.lease = lease;
        ClientSession session = lease.session();
        run.leased = session;
        run.watch = done -> ui.execute(() -> { if (runs.get(id) == run && run.leased == session) fail(run, "Connection lost"); });
        session.addCloseFutureListener(run.watch);
        if (!session.isOpen()) { fail(run, "Connection lost"); return; }
        if (run.tunnel.kind() != TunnelKind.REMOTE) { up(run, session, run.listener.port()); return; }
        set(id, new TunnelState.Starting("Requesting port on the server…"));
        var requested = new SshdSocketAddress(serverAddress(run.tunnel.bindAddress()), run.tunnel.bindPort());
        var relay = new SshdSocketAddress(RELAY, run.listener.port());
        background(() -> {
            SshdSocketAddress bound = null;
            try { bound = session.startRemotePortForwarding(requested, relay); }
            catch (IOException | RuntimeException refused) { /* reported below */ }
            SshdSocketAddress answer = bound;
            ui.execute(() -> {
                if (runs.get(id) != run) { if (answer != null) background(() -> stopRemote(session, answer)); return; }
                if (answer == null) {
                    String port = run.tunnel.bindPort() == 0 ? "a port" : "port " + run.tunnel.bindPort();
                    fail(run, "The server refused " + port + ": already in use or not allowed");
                    return;
                }
                run.remoteBound = answer;
                up(run, session, answer.getPort());
            });
        });
    }

    private void up(Run run, ClientSession session, int port) {
        run.session = session;
        set(run.tunnel.id(), new TunnelState.Up(Instant.now(), port, run.counters));
    }

    private void fail(Run run, String reason) {
        runs.remove(run.tunnel.id(), run);
        teardown(run);
        set(run.tunnel.id(), new TunnelState.Failed(reason));
    }

    private void teardown(Run run) {
        run.stop();
        if (run.leasing != null && !run.leasing.isDone()) run.leasing.cancel(true);
        ClientSession session = run.leased;
        if (session != null && run.watch != null) session.removeCloseFutureListener(run.watch);
        SessionLease lease = run.lease;
        SshdSocketAddress bound = run.remoteBound;
        if (lease == null) return;
        if (bound == null) { lease.close(); return; }
        background(() -> { stopRemote(session, bound); lease.close(); });
    }

    private static void stopRemote(ClientSession session, SshdSocketAddress bound) {
        try { session.stopRemotePortForwarding(bound); } catch (IOException | RuntimeException gone) { /* the session is closing */ }
    }

    private void set(UUID id, TunnelState state) { states.put(id, state); changed(); }
    private void changed() { listeners.forEach(Runnable::run); }

    private void background(Runnable work) {
        try { background.execute(work); } catch (RejectedExecutionException stopping) { work.run(); }
    }

    // ---- connection threads

    /** On the connection's own thread: a channel to {@code target} over the run's session, then the pump. */
    private void carry(Run run, Socket client, SshdSocketAddress target, Answer answer) {
        Pump.End near = Pump.of(client);
        ClientSession session = run.session;
        if (session == null || !run.track(near)) { near.close(); return; }
        ChannelDirectTcpip channel = null;
        try {
            channel = session.createDirectTcpipChannel(new SshdSocketAddress(client.getInetAddress().getHostAddress(), client.getPort()), target);
            channel.open().verify(timeout.get());
        } catch (IOException | RuntimeException refused) {
            if (channel != null) channel.close(true);
            run.counters.refused(refusal(refused, target.getHostName()));
            try { answer.opened(false); } catch (IOException gone) { /* the client left */ }
            run.untrack(near);
            near.close();
            return;
        }
        Pump.End far = Pump.of(channel);
        if (!run.track(far)) { run.untrack(near); near.close(); return; }
        try { answer.opened(true); }
        catch (IOException gone) { run.untrack(near); run.untrack(far); near.close(); far.close(); return; }
        pump(run, near, far);
    }

    /** A dynamic tunnel's connection: the SOCKS5 handshake names the target. */
    private void socks(Run run, Socket client) {
        if (run.session == null) { quietly(client); return; }
        Socks5.Target target;
        try {
            client.setSoTimeout(millis(timeout.get()));
            target = Socks5.accept(client.getInputStream(), client.getOutputStream());
            client.setSoTimeout(0);
        } catch (IOException refused) { quietly(client); return; }
        carry(run, client, new SshdSocketAddress(target.host(), target.port()),
            ok -> Socks5.reply(client.getOutputStream(), ok ? Socks5.SUCCEEDED : Socks5.HOST_UNREACHABLE));
    }

    /** A connection the server forwarded to a remote tunnel's relay: on to the target from this computer. */
    private void relay(Run run, Socket fromServer) {
        Pump.End near = Pump.of(fromServer);
        if (!run.track(near)) return;
        var target = new Socket();
        try { target.connect(new InetSocketAddress(run.tunnel.targetHost(), run.tunnel.targetPort()), millis(timeout.get())); }
        catch (IOException | RuntimeException refused) {
            quietly(target);
            run.counters.refused(refusal(refused, run.tunnel.targetHost()));
            run.untrack(near);
            near.close();
            return;
        }
        Pump.End far = Pump.of(target);
        if (!run.track(far)) { run.untrack(near); near.close(); return; }
        pump(run, near, far);
    }

    private static void pump(Run run, Pump.End near, Pump.End far) {
        run.counters.opened();
        try { Pump.run(near, far, run.counters); }
        finally { run.counters.closed(); run.untrack(near); run.untrack(far); }
    }

    private String refusal(Throwable failure, String host) {
        return "Connections are being refused: " + Failures.message(failure, timeout.get(), host);
    }

    private static int millis(Duration duration) { return (int) Math.min(Integer.MAX_VALUE, Math.max(0, duration.toMillis())); }

    private static void quietly(Socket socket) {
        try { socket.close(); } catch (IOException ignored) { /* closing */ }
    }

    private static Throwable cause(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) cause = cause.getCause();
        return cause;
    }

    private static String message(Throwable failure) {
        Throwable cause = cause(failure);
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    /** One running tunnel. Fields without volatile belong to the UI thread. */
    private static final class Run {
        final Tunnel tunnel;
        final Counters counters = new Counters();
        private final Set<Pump.End> open = ConcurrentHashMap.newKeySet();
        private volatile boolean stopped;
        /** The session connections go over; null until Up and after stop. */
        volatile ClientSession session;
        Listener listener;
        CompletableFuture<SessionLease> leasing;
        SessionLease lease;
        ClientSession leased;
        SshFutureListener<CloseFuture> watch;
        SshdSocketAddress remoteBound;

        Run(Tunnel tunnel) { this.tunnel = tunnel; }

        /** Keeps {@code end} to close on stop; false, with {@code end} closed, once the run has stopped. */
        boolean track(Pump.End end) {
            open.add(end);
            if (!stopped) return true;
            open.remove(end);
            end.close();
            return false;
        }

        void untrack(Pump.End end) { open.remove(end); }

        /** Stops accepting and closes every connection; releasing the lease is the manager's. */
        void stop() {
            stopped = true;
            session = null;
            if (listener != null) listener.close();
            for (Pump.End end : List.copyOf(open)) end.close();
            open.clear();
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-plugin-remote:test --tests dev.jasper.remote.tunnels.TunnelManagerTest`
Expected: PASS (12 tests). Run it three times (`--rerun-tasks`) to check for timing flakes; every wait is bounded at 15 s.

- [ ] **Step 5: Commit**

Run the hygiene check (expected: no output), then:

```bash
git branch --show-current
git add plugins/remote/src/main/java/dev/jasper/remote/tunnels plugins/remote/src/test/java/dev/jasper/remote/tunnels/TunnelManagerTest.java
git commit -m "Remote: TunnelManager runs local, remote and dynamic tunnels

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---
### Task 5: Row text and the Tunnels panel

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelRows.java`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsPanel.java`
- Create: `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/package-info.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelRowsTest.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelsPanelTest.java`

**Interfaces:**
- Consumes: `Tunnel`, `TunnelKind`, `TunnelState`, `Counters` (public getters); test-only `TunnelsEngineTestAccess`; `dev.jasper.sdk.ui.BundledIcon` (`RUN_RUN`, `RUN_STOP`).
- Produces:
  - `TunnelRows` (public, static, pure): `title(Tunnel, TunnelState)`, `mapping(Tunnel, TunnelState)`, `detail(Tunnel, TunnelState, Optional<String> hostName, Instant now)`, `stateText(TunnelState)`, `status(int active, int failing)`, `address(Tunnel, TunnelState, Optional<String> serverHostname)` → `Optional<String>`, `browseUri(Tunnel, TunnelState)` → `Optional<URI>`, `matches(Tunnel, String hostName, String query)`; package-private `port`, `connections`, `bytes`, `duration`.
  - `TunnelsPanel(Actions actions, Function<BundledIcon, Icon> icons)` with `record Actions(Consumer<Tunnel> start, Consumer<Tunnel> stop, Consumer<Tunnel> edit, Consumer<Tunnel> duplicate, Consumer<Tunnel> delete, Consumer<Tunnel> save, Consumer<Tunnel> browse, Consumer<Tunnel> copyAddress, Runnable add)`, `void setTunnels(List<Tunnel> saved, List<Tunnel> quick, Optional<String> error, Function<UUID, TunnelState> states, Function<UUID, Optional<String>> hostNames)`, `void refresh()`; package-private `filter`, `add`, `empty`, `list`, `setClock(Supplier<Instant>)`, `toggle(int)`, `activate(int)`, `menuFor(int)`, row types `Row`/`Item`/`Heading`/`Problem`, key actions `"toggleTunnel"` (Enter, Space) and `"deleteTunnel"` (Delete, Backspace).

- [ ] **Step 1: Write the failing tests**

Create `plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelRowsTest.java`:

```java
package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.tunnels.Counters;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.remote.tunnels.TunnelsEngineTestAccess;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TunnelRowsTest {
    static final UUID HOST = UUID.randomUUID();
    final Instant now = Instant.parse("2026-09-30T12:00:00Z");
    final Tunnel db = Tunnel.create("pg-prod", HOST, TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db.internal", 5432);
    final Tunnel socks = Tunnel.create("socks", HOST, TunnelKind.DYNAMIC, Tunnel.LOCALHOST, 1080, "", 0);
    final Tunnel api = Tunnel.create("api", HOST, TunnelKind.REMOTE, Tunnel.ALL, 8080, "localhost", 8080);
    final Tunnel quick = Tunnel.create("", HOST, TunnelKind.LOCAL, Tunnel.LOCALHOST, 0, "localhost", 3000);

    TunnelState.Up up(int port, int open, long received, long sent, Duration age) {
        var counters = new Counters();
        TunnelsEngineTestAccess.count(counters, open, received, sent);
        return new TunnelState.Up(now.minus(age), port, counters);
    }

    @Test void mappingsNameBothEnds() {
        assertThat(TunnelRows.mapping(db, TunnelState.STOPPED)).isEqualTo("localhost:5432 → db.internal:5432");
        assertThat(TunnelRows.mapping(socks, TunnelState.STOPPED)).isEqualTo("localhost:1080 · SOCKS5");
        assertThat(TunnelRows.mapping(api, TunnelState.STOPPED)).isEqualTo("server *:8080 → localhost:8080");
        assertThat(TunnelRows.mapping(quick, TunnelState.STOPPED)).isEqualTo("localhost:any port → localhost:3000");
        assertThat(TunnelRows.mapping(quick, up(54321, 0, 0, 0, Duration.ZERO))).isEqualTo("localhost:54321 → localhost:3000");
        assertThat(TunnelRows.title(quick, up(54321, 0, 0, 0, Duration.ZERO))).isEqualTo("localhost:54321");
        assertThat(TunnelRows.title(db, TunnelState.STOPPED)).isEqualTo("pg-prod");
    }

    @Test void theDetailLineFollowsTheState() {
        Optional<String> host = Optional.of("db-primary");
        assertThat(TunnelRows.detail(db, TunnelState.STOPPED, host, now)).isEqualTo("via db-primary");
        assertThat(TunnelRows.detail(db, new TunnelState.Starting("Authenticating…"), host, now)).isEqualTo("via db-primary · Authenticating…");
        assertThat(TunnelRows.detail(db, new TunnelState.Failed("Connection lost"), host, now)).isEqualTo("via db-primary · Connection lost");
        assertThat(TunnelRows.detail(db, up(5432, 3, 12L * 1024 * 1024, 1_468_006, Duration.ofMinutes(134)), host, now))
            .isEqualTo("via db-primary · 3 conns · ↓12 MB ↑1.4 MB · 2h 14m");
        TunnelState.Up warned = up(5432, 0, 0, 0, Duration.ZERO);
        TunnelsEngineTestAccess.refuse(warned.counters(), "Connections are being refused: Connection refused");
        assertThat(TunnelRows.detail(db, warned, host, now)).isEqualTo("via db-primary · Connections are being refused: Connection refused");
        assertThat(TunnelRows.detail(quick, up(3000, 1, 0, 0, Duration.ofSeconds(5)), host, now))
            .isEqualTo("via db-primary · 1 conn · ↓0 B ↑0 B · 5 s · unsaved");
        assertThat(TunnelRows.detail(db, TunnelState.STOPPED, Optional.empty(), now)).isEqualTo("Host missing — edit to choose another");
    }

    @Test void sizesAndAgesAreShort() {
        assertThat(TunnelRows.bytes(0)).isEqualTo("0 B");
        assertThat(TunnelRows.bytes(1536)).isEqualTo("1.5 KB");
        assertThat(TunnelRows.bytes(3L * 1024 * 1024 * 1024)).isEqualTo("3.0 GB");
        assertThat(TunnelRows.duration(Duration.ofSeconds(59))).isEqualTo("59 s");
        assertThat(TunnelRows.duration(Duration.ofMinutes(4))).isEqualTo("4 min");
        assertThat(TunnelRows.duration(Duration.ofHours(26))).isEqualTo("1d 2h");
    }

    @Test void statusAndStateWords() {
        assertThat(TunnelRows.status(1, 0)).isEqualTo("1 tunnel");
        assertThat(TunnelRows.status(2, 1)).isEqualTo("2 tunnels · 1 failing");
        assertThat(TunnelRows.status(0, 1)).isEqualTo("1 tunnel failing");
        assertThat(TunnelRows.status(0, 3)).isEqualTo("3 tunnels failing");
        assertThat(TunnelRows.stateText(new TunnelState.Failed("Host missing"))).isEqualTo("failed: Host missing");
        assertThat(TunnelRows.stateText(new TunnelState.Starting("Binding…"))).isEqualTo("starting");
        assertThat(TunnelRows.stateText(TunnelState.STOPPED)).isEqualTo("stopped");
    }

    @Test void addressesAndBrowserLinks() {
        assertThat(TunnelRows.address(db, TunnelState.STOPPED, Optional.empty())).contains("localhost:5432");
        assertThat(TunnelRows.address(quick, TunnelState.STOPPED, Optional.empty())).as("any port, not bound yet").isEmpty();
        assertThat(TunnelRows.address(api, TunnelState.STOPPED, Optional.of("api.example"))).contains("api.example:8080");
        Tunnel v6 = Tunnel.create("v6", HOST, TunnelKind.LOCAL, "::1", 8080, "x", 1);
        assertThat(TunnelRows.address(v6, TunnelState.STOPPED, Optional.empty())).contains("[::1]:8080");
        assertThat(TunnelRows.browseUri(db, up(5432, 0, 0, 0, Duration.ZERO))).contains(URI.create("http://localhost:5432/"));
        assertThat(TunnelRows.browseUri(db, TunnelState.STOPPED)).isEmpty();
        assertThat(TunnelRows.browseUri(socks, up(1080, 0, 0, 0, Duration.ZERO))).as("only local tunnels open in a browser").isEmpty();
    }

    @Test void theFilterLooksAtNamesHostsAddressesAndPorts() {
        assertThat(TunnelRows.matches(db, "db-primary", "")).isTrue();
        assertThat(TunnelRows.matches(db, "db-primary", "PRIMARY")).isTrue();
        assertThat(TunnelRows.matches(db, "db-primary", "5432")).isTrue();
        assertThat(TunnelRows.matches(db, "db-primary", "db.internal")).isTrue();
        assertThat(TunnelRows.matches(socks, "", "dynamic")).isTrue();
        assertThat(TunnelRows.matches(db, "db-primary", "grafana")).isFalse();
    }
}
```

Create `plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelsPanelTest.java`:

```java
package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.tunnels.Counters;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TunnelsPanelTest {
    static final UUID PROD = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    final List<String> events = new ArrayList<>();
    final Map<UUID, TunnelState> states = new HashMap<>();
    final Tunnel db = Tunnel.create("db", PROD, TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db.internal", 5432);
    final Tunnel api = Tunnel.create("API", PROD, TunnelKind.REMOTE, Tunnel.LOCALHOST, 8080, "localhost", 8080);
    final Tunnel quick = Tunnel.create("", PROD, TunnelKind.LOCAL, Tunnel.LOCALHOST, 3000, "localhost", 3000);
    final TunnelsPanel panel = new TunnelsPanel(new TunnelsPanel.Actions(t -> note("start", t), t -> note("stop", t), t -> note("edit", t),
        t -> note("duplicate", t), t -> note("delete", t), t -> note("save", t), t -> note("browse", t), t -> note("copy", t),
        () -> events.add("add")), icon -> null);

    void note(String what, Tunnel tunnel) { events.add(what + " " + (tunnel.quick() ? "quick" : tunnel.name())); }

    void show() {
        panel.setTunnels(List.of(db, api), List.of(quick), Optional.empty(), id -> states.getOrDefault(id, TunnelState.STOPPED),
            id -> id.equals(PROD) ? Optional.of("prod") : Optional.empty());
    }

    static TunnelState.Up up(int port) { return new TunnelState.Up(NOW, port, new Counters()); }

    List<String> rows() {
        var rows = new ArrayList<String>();
        for (int i = 0; i < panel.list.getModel().getSize(); i++) {
            rows.add(switch (panel.list.getModel().getElementAt(i)) {
                case TunnelsPanel.Item item -> TunnelRows.title(item.tunnel(), states.getOrDefault(item.tunnel().id(), TunnelState.STOPPED));
                case TunnelsPanel.Heading heading -> "heading " + heading.text();
                case TunnelsPanel.Problem problem -> "problem " + problem.message();
            });
        }
        return rows;
    }

    List<String> menu(int index) {
        var lines = new ArrayList<String>();
        for (var component : panel.menuFor(index).getComponents()) {
            if (component instanceof JMenuItem item) lines.add(item.getText() + (item.isEnabled() ? "" : " (off)"));
            else lines.add("---");
        }
        return lines;
    }

    @Test void savedTunnelsByNameThenQuickForwardsUnderTheirHeading() {
        show();
        assertThat(rows()).containsExactly("API", "db", "heading Quick forwards", "localhost:3000");
        assertThat(panel.empty.isVisible()).isFalse();
    }

    @Test void theFilterNarrowsAndSaysWhenNothingMatches() {
        show();
        panel.filter.setText("5432");
        assertThat(rows()).containsExactly("db");
        panel.filter.setText("prod");
        assertThat(rows()).containsExactly("API", "db", "heading Quick forwards", "localhost:3000");
        panel.filter.setText("grafana");
        assertThat(rows()).isEmpty();
        assertThat(panel.empty.getText()).isEqualTo("No tunnels match");
        assertThat(panel.empty.isVisible()).isTrue();
    }

    @Test void anEmptyPanelSaysHowToStartAndABrokenFileShowsOneRow() {
        panel.setTunnels(List.of(), List.of(), Optional.empty(), id -> TunnelState.STOPPED, id -> Optional.empty());
        assertThat(panel.empty.getText()).isEqualTo("No tunnels yet — add one with +, or use Forward a Port... from an SSH pane");
        assertThat(panel.empty.isVisible()).isTrue();
        panel.setTunnels(List.of(db), List.of(), Optional.of("line 2"), id -> TunnelState.STOPPED, id -> Optional.of("prod"));
        assertThat(rows()).containsExactly("problem tunnels.toml has errors: line 2", "db");
        assertThat(panel.menuFor(0)).isNull();
    }

    @Test void theMenuOffersWhatTheStateAllows() {
        states.put(db.id(), up(5432));
        states.put(api.id(), new TunnelState.Failed("The server refused port 8080: already in use or not allowed"));
        show();
        assertThat(menu(1)).containsExactly("Stop", "---", "Open in Browser", "Copy Address", "---", "Edit…", "Duplicate", "Delete…");
        assertThat(menu(0)).containsExactly("Start", "Stop", "---", "Open in Browser (off)", "Copy Address", "---", "Edit…", "Duplicate", "Delete…");
        assertThat(menu(3)).containsExactly("Start", "---", "Open in Browser (off)", "Copy Address", "---", "Save as Tunnel…");
        assertThat(panel.menuFor(2)).as("the heading has no menu").isNull();
        ((JMenuItem) panel.menuFor(1).getComponent(2)).doClick();
        assertThat(events).containsExactly("browse db");
    }

    @Test void theRowButtonEnterAndDoubleClickActOnTheRow() {
        states.put(db.id(), up(5432));
        show();
        panel.toggle(1);
        panel.toggle(0);
        panel.activate(1);
        panel.activate(3);
        panel.list.setSelectedIndex(1);
        panel.list.getActionMap().get("toggleTunnel").actionPerformed(null);
        panel.list.getActionMap().get("deleteTunnel").actionPerformed(null);
        panel.list.setSelectedIndex(3);
        panel.list.getActionMap().get("deleteTunnel").actionPerformed(null);
        panel.add.doClick();
        assertThat(events).containsExactly("stop db", "start API", "edit db", "save quick", "stop db", "delete db", "add");
    }

    @Test void theSelectionSurvivesAnUpdateAndTheRowSaysEverything() {
        panel.setClock(() -> NOW);
        show();
        panel.list.setSelectedIndex(1);
        states.put(db.id(), up(5432));
        show();
        assertThat(panel.list.getSelectedIndex()).isEqualTo(1);
        var row = (JComponent) panel.list.getCellRenderer().getListCellRendererComponent(panel.list, panel.list.getModel().getElementAt(1), 1, false, false);
        assertThat(row.getToolTipText()).isEqualTo("db, localhost:5432 → db.internal:5432, via prod · 0 conns · ↓0 B ↑0 B · 0 s");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.ui.tunnels.*'`
Expected: FAIL — `package dev.jasper.remote.ui.tunnels does not exist`.

- [ ] **Step 3: Write the row text and the panel**

Create `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/package-info.java`:

```java
/**
 * The Tunnels UI: the rail panel, the editor, Forward a Port, the palette scope and {@link dev.jasper.remote.ui.tunnels.TunnelsUi},
 * which registers them and keeps the manager in step with tunnels.toml. Swing on the UI thread; it never touches MINA.
 */
package dev.jasper.remote.ui.tunnels;
```

Create `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelRows.java`:

```java
package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/** The words a tunnel shows in the panel, its tooltip, the palette and the status bar. Pure, so tests read them. */
public final class TunnelRows {
    private TunnelRows() { }

    /** The port it listens on now: the bound one while up, else the saved one, where 0 reads "any port". */
    static String port(Tunnel tunnel, TunnelState state) {
        if (state instanceof TunnelState.Up up) return String.valueOf(up.boundPort());
        return tunnel.bindPort() == 0 ? "any port" : String.valueOf(tunnel.bindPort());
    }

    /** The saved name, or a quick forward's listen address. */
    public static String title(Tunnel tunnel, TunnelState state) {
        return tunnel.quick() ? tunnel.bindAddress() + ":" + port(tunnel, state) : tunnel.name();
    }

    /** "localhost:5432 → db.internal:5432", "localhost:1080 · SOCKS5" or "server localhost:8080 → localhost:8080". */
    public static String mapping(Tunnel tunnel, TunnelState state) {
        String listen = tunnel.bindAddress() + ":" + port(tunnel, state);
        String target = tunnel.targetHost() + ":" + tunnel.targetPort();
        return switch (tunnel.kind()) {
            case LOCAL -> listen + " → " + target;
            case DYNAMIC -> listen + " · SOCKS5";
            case REMOTE -> "server " + listen + " → " + target;
        };
    }

    /** The row's last line: the host and what is happening, or that the host is gone. */
    public static String detail(Tunnel tunnel, TunnelState state, Optional<String> hostName, Instant now) {
        if (hostName.isEmpty()) return "Host missing — edit to choose another";
        String via = "via " + hostName.get();
        String line = switch (state) {
            case TunnelState.Stopped stopped -> via;
            case TunnelState.Starting starting -> via + " · " + starting.stage();
            case TunnelState.Failed failed -> via + " · " + failed.reason();
            case TunnelState.Up up -> up.counters().warning().map(warning -> via + " · " + warning).orElseGet(() ->
                via + " · " + connections(up.counters().open()) + " · ↓" + bytes(up.counters().received()) + " ↑" + bytes(up.counters().sent())
                    + " · " + duration(Duration.between(up.since(), now)));
        };
        return tunnel.quick() ? line + " · unsaved" : line;
    }

    /** One word or phrase for the palette and the status tooltip. */
    public static String stateText(TunnelState state) {
        return switch (state) {
            case TunnelState.Stopped stopped -> "stopped";
            case TunnelState.Starting starting -> "starting";
            case TunnelState.Up up -> "up";
            case TunnelState.Failed failed -> "failed: " + failed.reason();
        };
    }

    /** The status item: "2 tunnels", "2 tunnels · 1 failing" or "1 tunnel failing". */
    public static String status(int active, int failing) {
        String running = active == 1 ? "1 tunnel" : active + " tunnels";
        if (failing == 0) return running;
        if (active == 0) return failing == 1 ? "1 tunnel failing" : failing + " tunnels failing";
        return running + " · " + failing + " failing";
    }

    /**
     * What Copy Address copies; empty while the port is not known. A remote tunnel open to everyone is reached at
     * {@code serverHostname}.
     */
    public static Optional<String> address(Tunnel tunnel, TunnelState state, Optional<String> serverHostname) {
        int port = state instanceof TunnelState.Up up ? up.boundPort() : tunnel.bindPort();
        if (port == 0) return Optional.empty();
        String host = tunnel.kind() == TunnelKind.REMOTE && tunnel.bindAddress().equals(Tunnel.ALL)
            ? serverHostname.orElse("server") : hostPart(tunnel.bindAddress());
        return Optional.of(host + ":" + port);
    }

    /** What Open in Browser opens: {@code http://localhost:5432/} for a running local tunnel; empty otherwise. */
    public static Optional<URI> browseUri(Tunnel tunnel, TunnelState state) {
        if (tunnel.kind() != TunnelKind.LOCAL || !(state instanceof TunnelState.Up up)) return Optional.empty();
        return Optional.of(URI.create("http://" + hostPart(tunnel.bindAddress()) + ":" + up.boundPort() + "/"));
    }

    /** Whether {@code query} finds the tunnel in its name, host, addresses, ports or kind. */
    public static boolean matches(Tunnel tunnel, String hostName, String query) {
        String wanted = query.strip().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) return true;
        String haystack = String.join(" ", tunnel.name(), hostName, tunnel.bindAddress() + ":" + tunnel.bindPort(),
            tunnel.targetHost() + ":" + tunnel.targetPort(), tunnel.kind().key()).toLowerCase(Locale.ROOT);
        return haystack.contains(wanted);
    }

    static String connections(int open) { return open == 1 ? "1 conn" : open + " conns"; }

    static String bytes(long count) {
        if (count < 1024) return count + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = count;
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
        return (value < 10 ? String.format(Locale.ROOT, "%.1f", value) : String.valueOf(Math.round(value))) + " " + units[unit];
    }

    static String duration(Duration elapsed) {
        long seconds = Math.max(0, elapsed.toSeconds());
        if (seconds < 60) return seconds + " s";
        if (seconds < 3600) return seconds / 60 + " min";
        if (seconds < 86_400) return seconds / 3600 + "h " + seconds % 3600 / 60 + "m";
        return seconds / 86_400 + "d " + seconds % 86_400 / 3600 + "h";
    }

    /** {@code *} and {@code localhost} are reached as localhost; an IPv6 literal goes in brackets. */
    private static String hostPart(String bindAddress) {
        if (bindAddress.equals(Tunnel.ALL) || bindAddress.equals(Tunnel.LOCALHOST)) return "localhost";
        return bindAddress.contains(":") ? "[" + bindAddress + "]" : bindAddress;
    }
}
```

Create `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsPanel.java`:

```java
package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.sdk.ui.BundledIcon;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The "Tunnels" rail panel: a filter, saved tunnels by name, then quick forwards under their own heading. Each row
 * shows a state dot, the name, the mapping, a live detail line and a start/stop button at its end. One per window.
 */
public final class TunnelsPanel extends JPanel {
    /** What the panel asks for; the plugin decides how. */
    public record Actions(Consumer<Tunnel> start, Consumer<Tunnel> stop, Consumer<Tunnel> edit, Consumer<Tunnel> duplicate,
                          Consumer<Tunnel> delete, Consumer<Tunnel> save, Consumer<Tunnel> browse, Consumer<Tunnel> copyAddress,
                          Runnable add) { }

    /** A line in the list. */
    sealed interface Row permits Item, Heading, Problem { }
    /** A tunnel. */
    record Item(Tunnel tunnel) implements Row { }
    /** The quick forwards' heading. */
    record Heading(String text) implements Row { }
    /** The broken-file message. */
    record Problem(String message) implements Row { }

    /** How wide the start/stop button at the end of a row is, for hit testing. */
    static final int BUTTON_WIDTH = 30;

    final JTextField filter = new JTextField();
    final JButton add = new JButton("+");
    final JLabel empty = new JLabel();
    final JList<Row> list;
    private final DefaultListModel<Row> model = new DefaultListModel<>();
    private final Actions actions;
    private final Function<BundledIcon, Icon> icons;
    private List<Tunnel> saved = List.of(), quick = List.of();
    private Optional<String> error = Optional.empty();
    private Function<UUID, TunnelState> states = id -> TunnelState.STOPPED;
    private Function<UUID, Optional<String>> hostNames = id -> Optional.empty();
    private Supplier<Instant> clock = Instant::now;

    public TunnelsPanel(Actions actions, Function<BundledIcon, Icon> icons) {
        super(new BorderLayout(0, 6));
        this.actions = actions;
        this.icons = icons;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        var title = new JLabel("Tunnels");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        var header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.add(title); header.add(Box.createHorizontalGlue()); header.add(add);
        add.setToolTipText("New Tunnel");
        filter.putClientProperty("JTextField.placeholderText", "Filter tunnels…");
        var north = new JPanel(new BorderLayout(0, 6));
        north.add(header, BorderLayout.NORTH);
        north.add(filter, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        list = new JList<>(model) {
            @Override public boolean getScrollableTracksViewportWidth() { return true; }
        };
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((owner, value, index, selected, focused) -> render(owner, value, selected));
        var scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        var center = new JPanel(new BorderLayout());
        center.add(scroll, BorderLayout.CENTER);
        empty.setBorder(BorderFactory.createEmptyBorder(12, 4, 12, 4));
        center.add(empty, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { rebuild(); }
            @Override public void removeUpdate(DocumentEvent e) { rebuild(); }
            @Override public void changedUpdate(DocumentEvent e) { rebuild(); }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                int index = list.locationToIndex(event.getPoint());
                if (index < 0) return;
                Rectangle cell = list.getCellBounds(index, index);
                if (cell == null || !cell.contains(event.getPoint())) return;
                list.setSelectedIndex(index);
                if (SwingUtilities.isRightMouseButton(event)) {
                    JPopupMenu menu = menuFor(index);
                    if (menu != null) menu.show(list, event.getX(), event.getY());
                } else if (event.getX() >= cell.x + cell.width - BUTTON_WIDTH) toggle(index);
                else if (event.getClickCount() == 2) activate(index);
            }
        });
        bind("ENTER", "toggleTunnel", () -> toggle(list.getSelectedIndex()));
        bind("SPACE", "toggleTunnel", () -> toggle(list.getSelectedIndex()));
        bind("DELETE", "deleteTunnel", () -> delete(list.getSelectedIndex()));
        bind("BACK_SPACE", "deleteTunnel", () -> delete(list.getSelectedIndex()));
        add.addActionListener(event -> actions.add().run());
        rebuild();
    }

    /** Replaces what the panel lists: saved tunnels by name, then quick forwards under their own heading. */
    public void setTunnels(List<Tunnel> saved, List<Tunnel> quick, Optional<String> error, Function<UUID, TunnelState> states,
                           Function<UUID, Optional<String>> hostNames) {
        this.saved = saved.stream().sorted(Comparator.comparing((Tunnel tunnel) -> tunnel.name().toLowerCase(Locale.ROOT))).toList();
        this.quick = List.copyOf(quick);
        this.error = error;
        this.states = states;
        this.hostNames = hostNames;
        rebuild();
    }

    /** Repaints counters and uptimes; the plugin calls this once a second while a tunnel is up. */
    public void refresh() { list.repaint(); }

    void setClock(Supplier<Instant> value) { clock = value; list.repaint(); }

    /** The row button, Enter and Space: stop a running tunnel, start any other. */
    void toggle(int index) {
        tunnelAt(index).ifPresent(tunnel -> (states.apply(tunnel.id()).running() ? actions.stop() : actions.start()).accept(tunnel));
    }

    /** Double-click: edit a saved tunnel, name a quick forward. */
    void activate(int index) { tunnelAt(index).ifPresent(tunnel -> (tunnel.quick() ? actions.save() : actions.edit()).accept(tunnel)); }

    /** Delete and Backspace: only saved tunnels; a quick forward goes away when stopped. */
    void delete(int index) { tunnelAt(index).filter(tunnel -> !tunnel.quick()).ifPresent(actions.delete()); }

    JPopupMenu menuFor(int index) {
        Optional<Tunnel> found = tunnelAt(index);
        if (found.isEmpty()) return null;
        Tunnel tunnel = found.get();
        TunnelState state = states.apply(tunnel.id());
        var menu = new JPopupMenu();
        if (state.running()) menu.add(item("Stop", true, () -> actions.stop().accept(tunnel)));
        else menu.add(item("Start", true, () -> actions.start().accept(tunnel)));
        if (state instanceof TunnelState.Failed) menu.add(item("Stop", true, () -> actions.stop().accept(tunnel)));
        menu.addSeparator();
        menu.add(item("Open in Browser", TunnelRows.browseUri(tunnel, state).isPresent(), () -> actions.browse().accept(tunnel)));
        menu.add(item("Copy Address", state instanceof TunnelState.Up || tunnel.bindPort() != 0, () -> actions.copyAddress().accept(tunnel)));
        menu.addSeparator();
        if (tunnel.quick()) menu.add(item("Save as Tunnel…", true, () -> actions.save().accept(tunnel)));
        else {
            menu.add(item("Edit…", true, () -> actions.edit().accept(tunnel)));
            menu.add(item("Duplicate", true, () -> actions.duplicate().accept(tunnel)));
            menu.add(item("Delete…", true, () -> actions.delete().accept(tunnel)));
        }
        return menu;
    }

    private void bind(String stroke, String name, Runnable action) {
        list.getInputMap().put(KeyStroke.getKeyStroke(stroke), name);
        list.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    private Optional<Tunnel> tunnelAt(int index) {
        if (index < 0 || index >= model.size()) return Optional.empty();
        return model.get(index) instanceof Item item ? Optional.of(item.tunnel()) : Optional.empty();
    }

    private static JMenuItem item(String title, boolean enabled, Runnable action) {
        var item = new JMenuItem(title);
        item.setEnabled(enabled);
        item.addActionListener(event -> action.run());
        return item;
    }

    private void rebuild() {
        Optional<UUID> keep = list == null || !(list.getSelectedValue() instanceof Item item) ? Optional.empty() : Optional.of(item.tunnel().id());
        model.clear();
        error.ifPresent(message -> model.addElement(new Problem("tunnels.toml has errors: " + message)));
        String query = filter.getText();
        saved.stream().filter(tunnel -> matches(tunnel, query)).forEach(tunnel -> model.addElement(new Item(tunnel)));
        List<Tunnel> quickShown = quick.stream().filter(tunnel -> matches(tunnel, query)).toList();
        if (!quickShown.isEmpty()) {
            model.addElement(new Heading("Quick forwards"));
            quickShown.forEach(tunnel -> model.addElement(new Item(tunnel)));
        }
        boolean none = saved.isEmpty() && quick.isEmpty();
        empty.setText(none ? "No tunnels yet — add one with +, or use Forward a Port... from an SSH pane" : "No tunnels match");
        empty.setVisible(none || model.size() == (error.isPresent() ? 1 : 0));
        keep.ifPresent(id -> {
            for (int i = 0; i < model.size(); i++) if (model.get(i) instanceof Item item && item.tunnel().id().equals(id)) { list.setSelectedIndex(i); return; }
        });
    }

    private boolean matches(Tunnel tunnel, String query) {
        return TunnelRows.matches(tunnel, hostNames.apply(tunnel.hostId()).orElse(""), query);
    }

    private Component render(JList<? extends Row> owner, Row value, boolean selected) {
        if (value instanceof Item item) return row(owner, item.tunnel(), selected);
        var label = new JLabel();
        label.putClientProperty("html.disable", true);
        label.setOpaque(selected);
        label.setBackground(selected ? owner.getSelectionBackground() : owner.getBackground());
        label.setForeground(selected ? owner.getSelectionForeground() : owner.getForeground());
        label.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 4));
        label.setFont(owner.getFont().deriveFont(Font.BOLD));
        label.setText(value instanceof Heading heading ? heading.text() : ((Problem) value).message());
        return label;
    }

    private Component row(JList<?> owner, Tunnel tunnel, boolean selected) {
        TunnelState state = states.apply(tunnel.id());
        Color foreground = selected ? owner.getSelectionForeground() : owner.getForeground();
        Color muted = selected ? foreground : color("Label.disabledForeground", foreground);
        Font font = owner.getFont();
        var row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(true);
        row.setBackground(selected ? owner.getSelectionBackground() : owner.getBackground());
        row.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 6));
        var dot = text("●", dotColor(state, foreground), font.deriveFont(Math.max(8f, font.getSize2D() - 2f)));
        dot.setVerticalAlignment(SwingConstants.TOP);
        row.add(dot, BorderLayout.WEST);
        String title = TunnelRows.title(tunnel, state);
        String mapping = TunnelRows.mapping(tunnel, state);
        String detail = TunnelRows.detail(tunnel, state, hostNames.apply(tunnel.hostId()), clock.get());
        var lines = new JPanel();
        lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
        lines.setOpaque(false);
        lines.add(text(title, foreground, font.deriveFont(Font.BOLD)));
        lines.add(text(tunnel.kind().letter() + "  " + mapping, foreground, font));
        lines.add(text(detail, state instanceof TunnelState.Failed && !selected ? color("Actions.Red", muted) : muted, font));
        row.add(lines, BorderLayout.CENTER);
        var button = new JLabel(icons.apply(state.running() ? BundledIcon.RUN_STOP : BundledIcon.RUN_RUN));
        button.setVerticalAlignment(SwingConstants.TOP);
        button.setPreferredSize(new Dimension(BUTTON_WIDTH - 8, button.getPreferredSize().height));
        row.add(button, BorderLayout.EAST);
        String description = title + ", " + mapping + ", " + detail;
        row.setToolTipText(description);
        row.getAccessibleContext().setAccessibleName(description);
        return row;
    }

    private static Color dotColor(TunnelState state, Color fallback) {
        String key = switch (state) {
            case TunnelState.Up up -> up.counters().warning().isPresent() ? "Actions.Yellow" : "Actions.Green";
            case TunnelState.Starting starting -> "Actions.Yellow";
            case TunnelState.Failed failed -> "Actions.Red";
            case TunnelState.Stopped stopped -> "Label.disabledForeground";
        };
        return color(key, fallback);
    }

    private static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color == null ? fallback : color;
    }

    private static JLabel text(String value, Color foreground, Font font) {
        var label = new JLabel(value);
        label.putClientProperty("html.disable", true);
        label.setForeground(foreground);
        label.setFont(font);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.ui.tunnels.*'`
Expected: PASS (TunnelRowsTest 6, TunnelsPanelTest 6).

- [ ] **Step 5: Commit**

Run the hygiene check (expected: no output), then:

```bash
git branch --show-current
git add plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels
git commit -m "Remote: the Tunnels panel and its row text

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---
### Task 6: The tunnel editor

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelEditor.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelEditorTest.java`

**Interfaces:**
- Consumes: `Tunnel` (`create`, `withEdited`, `ALL`, `LOCALHOST`), `TunnelKind`, `RemoteHost` (`id()`, `name()`, `label()`).
- Produces: `TunnelEditor(List<RemoteHost> hosts, List<Tunnel> others, Optional<Tunnel> editing, Optional<UUID> preferredHost, Consumer<Tunnel> onSave, Runnable onCancel)` with `void showError(String)`; package-private fields `name`, `host`, `local`, `remote`, `dynamic`, `explanation`, `listenLabel`, `forwardLabel`, `listenAddress`, `listenPort`, `forwardHost`, `forwardPort`, `allowOthers`, `exposure`, `preview`, `warning`, `message`, `save`, `cancel`. Editing a quick forward (empty name) keeps its id, so saving names it.

- [ ] **Step 1: Write the failing test**

Create `plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelEditorTest.java`:

```java
package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TunnelEditorTest {
    final RemoteHost prod = RemoteHost.create("prod", "db.example", 22, "deploy", Auth.AGENT, "", Optional.empty());
    final RemoteHost web = RemoteHost.create("web", "web.example", 22, "deploy", Auth.AGENT, "", Optional.empty());
    final List<Tunnel> saved = new ArrayList<>();

    TunnelEditor editor(List<Tunnel> others, Optional<Tunnel> editing, Optional<UUID> preferred) {
        return new TunnelEditor(List.of(prod, web), others, editing, preferred, saved::add, () -> { });
    }

    @Test void createsALocalTunnelThroughTheChosenHost() {
        var editor = editor(List.of(), Optional.empty(), Optional.of(web.id()));
        assertThat(editor.host.getSelectedItem()).isEqualTo(web);
        assertThat(editor.local.isSelected()).isTrue();
        assertThat(editor.listenAddress.getText()).isEqualTo("localhost");
        assertThat(editor.save.getText()).isEqualTo("Add Tunnel");
        editor.save.doClick();
        assertThat(editor.message.getText()).isEqualTo("A tunnel needs a name");
        editor.name.setText("pg");
        editor.save.doClick();
        assertThat(editor.message.getText()).isEqualTo("The listen port must be a number from 0 to 65535");
        editor.listenPort.setText("5432");
        editor.forwardHost.setText("db.internal");
        editor.forwardPort.setText("5432");
        assertThat(editor.preview.getText()).isEqualTo("localhost:5432 on this computer → db.internal:5432 as seen from web");
        assertThat(editor.forwardLabel.getText()).isEqualTo("Forward to (as seen from web)");
        assertThat(editor.listenLabel.getText()).isEqualTo("Listen on (this computer)");
        editor.save.doClick();
        assertThat(saved).singleElement().satisfies(tunnel -> {
            assertThat(tunnel.name()).isEqualTo("pg");
            assertThat(tunnel.hostId()).isEqualTo(web.id());
            assertThat(tunnel.kind()).isEqualTo(TunnelKind.LOCAL);
            assertThat(tunnel.bindAddress()).isEqualTo("localhost");
            assertThat(tunnel.bindPort()).isEqualTo(5432);
            assertThat(tunnel.targetHost()).isEqualTo("db.internal");
            assertThat(tunnel.targetPort()).isEqualTo(5432);
        });
    }

    @Test void aDynamicTunnelHasNoTargetAndARemoteOneSpeaksOfTheServer() {
        var editor = editor(List.of(), Optional.empty(), Optional.empty());
        assertThat(editor.host.getSelectedItem()).as("the first host by default").isEqualTo(prod);
        editor.name.setText("socks");
        editor.dynamic.doClick();
        assertThat(editor.forwardLabel.isVisible()).isFalse();
        assertThat(editor.explanation.getText()).isEqualTo("A SOCKS5 proxy on this computer; each connection goes out through prod.");
        editor.listenPort.setText("1080");
        assertThat(editor.preview.getText()).isEqualTo("A SOCKS5 proxy on localhost:1080; connections leave from prod");
        editor.save.doClick();
        assertThat(saved.getLast().kind()).isEqualTo(TunnelKind.DYNAMIC);
        assertThat(saved.getLast().targetHost()).isEmpty();

        editor.remote.doClick();
        assertThat(editor.forwardLabel.isVisible()).isTrue();
        assertThat(editor.listenLabel.getText()).isEqualTo("Listen on (the server)");
        assertThat(editor.forwardLabel.getText()).isEqualTo("Forward to (as seen from this computer)");
        editor.allowOthers.doClick();
        assertThat(editor.listenAddress.getText()).isEqualTo("*");
        assertThat(editor.listenAddress.isEnabled()).isFalse();
        assertThat(editor.exposure.getText())
            .isEqualTo("Other computers on the network can connect to this port. The server's GatewayPorts setting must allow it.");
        editor.allowOthers.doClick();
        assertThat(editor.listenAddress.getText()).isEqualTo("localhost");
        assertThat(editor.listenAddress.isEnabled()).isTrue();
    }

    @Test void namesAreUniqueAndASharedPortIsOnlyAWarning() {
        Tunnel existing = Tunnel.create("pg", prod.id(), TunnelKind.LOCAL, "localhost", 5432, "db", 5432);
        var editor = editor(List.of(existing), Optional.empty(), Optional.empty());
        editor.name.setText("PG");
        editor.listenPort.setText("5432");
        editor.forwardPort.setText("5432");
        assertThat(editor.warning.getText()).isEqualTo("pg listens on the same port; they cannot run at once.");
        editor.save.doClick();
        assertThat(editor.message.getText()).isEqualTo("A tunnel named pg exists");
        editor.name.setText("pg2");
        editor.save.doClick();
        assertThat(saved).as("a shared port does not block saving").hasSize(1);
        editor.remote.doClick();
        assertThat(editor.warning.getText()).as("a port on the server is another side").isBlank();
    }

    @Test void editingKeepsTheIdentityAndSavingAQuickForwardNamesIt() {
        Tunnel existing = Tunnel.create("pg", prod.id(), TunnelKind.REMOTE, Tunnel.ALL, 8080, "localhost", 3000);
        var editor = editor(List.of(existing), Optional.of(existing), Optional.empty());
        assertThat(editor.save.getText()).isEqualTo("Save");
        assertThat(editor.remote.isSelected()).isTrue();
        assertThat(editor.allowOthers.isSelected()).isTrue();
        assertThat(editor.listenAddress.isEnabled()).isFalse();
        assertThat(editor.forwardPort.getText()).isEqualTo("3000");
        editor.forwardPort.setText("3001");
        editor.save.doClick();
        assertThat(saved.getLast().id()).isEqualTo(existing.id());
        assertThat(saved.getLast().created()).isEqualTo(existing.created());
        assertThat(saved.getLast().targetPort()).isEqualTo(3001);

        Tunnel quick = Tunnel.create("", web.id(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 8080, "localhost", 8080);
        var naming = editor(List.of(existing), Optional.of(quick), Optional.empty());
        assertThat(naming.name.getText()).isEmpty();
        assertThat(naming.host.getSelectedItem()).isEqualTo(web);
        naming.name.setText("web app");
        naming.save.doClick();
        assertThat(saved.getLast().id()).isEqualTo(quick.id());
        assertThat(saved.getLast().name()).isEqualTo("web app");
    }

    @Test void aTunnelWhoseHostIsGoneMustChooseAnother() {
        Tunnel orphan = Tunnel.create("orphan", UUID.randomUUID(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 1, "x", 1);
        var editor = editor(List.of(orphan), Optional.of(orphan), Optional.empty());
        assertThat(editor.host.getSelectedItem()).isNull();
        editor.save.doClick();
        assertThat(editor.message.getText()).isEqualTo("Choose a host to go through");
        editor.showError("tunnels.toml has errors; fix it before saving");
        assertThat(editor.message.getText()).isEqualTo("tunnels.toml has errors; fix it before saving");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-plugin-remote:test --tests dev.jasper.remote.ui.tunnels.TunnelEditorTest`
Expected: FAIL — `cannot find symbol: class TunnelEditor`.

- [ ] **Step 3: Write the editor**

Create `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelEditor.java`:

```java
package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Add or edit one tunnel, or name a quick forward to keep it. Validation is inline; Save hands back the tunnel. */
public final class TunnelEditor extends JPanel {
    final JTextField name = new JTextField(20);
    final JComboBox<RemoteHost> host = new JComboBox<>();
    final JRadioButton local = new JRadioButton("Local"), remote = new JRadioButton("Remote"), dynamic = new JRadioButton("Dynamic (SOCKS)");
    final JLabel explanation = new JLabel(), listenLabel = new JLabel(), forwardLabel = new JLabel();
    final JTextField listenAddress = new JTextField(Tunnel.LOCALHOST, 12), listenPort = new JTextField(6);
    final JTextField forwardHost = new JTextField("localhost", 14), forwardPort = new JTextField(6);
    final JCheckBox allowOthers = new JCheckBox("Allow other computers to connect");
    final JLabel exposure = new JLabel(" "), preview = new JLabel(" "), warning = new JLabel(" "), message = new JLabel(" ");
    final JButton save, cancel = new JButton("Cancel");
    private final JPanel forwardFields;
    private final List<Tunnel> others;
    private final UUID editingId;

    public TunnelEditor(List<RemoteHost> hosts, List<Tunnel> others, Optional<Tunnel> editing, Optional<UUID> preferredHost,
                        Consumer<Tunnel> onSave, Runnable onCancel) {
        super(new BorderLayout(0, 8));
        this.others = List.copyOf(others);
        this.editingId = editing.map(Tunnel::id).orElse(null);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        save = new JButton(editing.isEmpty() ? "Add Tunnel" : "Save");
        hosts.forEach(host::addItem);
        host.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value instanceof RemoteHost via ? via.name() + " — " + via.label() : "", index, selected, focus);
                putClientProperty("html.disable", true);
                return this;
            }
        });
        UUID wanted = editing.map(Tunnel::hostId).or(() -> preferredHost).orElse(null);
        host.setSelectedIndex(-1);
        for (int i = 0; i < host.getItemCount(); i++) if (host.getItemAt(i).id().equals(wanted)) host.setSelectedIndex(i);
        if (host.getSelectedIndex() < 0 && editing.isEmpty() && host.getItemCount() > 0) host.setSelectedIndex(0);
        var types = new ButtonGroup();
        types.add(local); types.add(remote); types.add(dynamic);
        editing.ifPresentOrElse(tunnel -> {
            name.setText(tunnel.name());
            (tunnel.kind() == TunnelKind.REMOTE ? remote : tunnel.kind() == TunnelKind.DYNAMIC ? dynamic : local).setSelected(true);
            listenAddress.setText(tunnel.bindAddress());
            listenPort.setText(String.valueOf(tunnel.bindPort()));
            allowOthers.setSelected(tunnel.bindAddress().equals(Tunnel.ALL));
            if (tunnel.kind() != TunnelKind.DYNAMIC) {
                forwardHost.setText(tunnel.targetHost());
                forwardPort.setText(String.valueOf(tunnel.targetPort()));
            }
        }, () -> local.setSelected(true));
        listenAddress.setEnabled(!allowOthers.isSelected());

        var form = new JPanel(new GridBagLayout());
        var at = new GridBagConstraints();
        at.insets = new Insets(3, 3, 3, 3);
        at.anchor = GridBagConstraints.WEST;
        at.gridy = 0;
        row(form, at, new JLabel("Name"), name);
        row(form, at, new JLabel("Via host"), host);
        var typeRow = new JPanel();
        typeRow.setLayout(new BoxLayout(typeRow, BoxLayout.X_AXIS));
        typeRow.add(local); typeRow.add(Box.createHorizontalStrut(8)); typeRow.add(remote); typeRow.add(Box.createHorizontalStrut(8)); typeRow.add(dynamic);
        row(form, at, new JLabel("Type"), typeRow);
        row(form, at, new JLabel(""), explanation);
        row(form, at, listenLabel, pair(listenAddress, listenPort));
        row(form, at, new JLabel(""), allowOthers);
        row(form, at, new JLabel(""), exposure);
        forwardFields = pair(forwardHost, forwardPort);
        row(form, at, forwardLabel, forwardFields);
        row(form, at, new JLabel(""), preview);
        add(form, BorderLayout.CENTER);
        var south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        warning.setAlignmentX(Component.LEFT_ALIGNMENT);
        message.setAlignmentX(Component.LEFT_ALIGNMENT);
        south.add(warning);
        south.add(message);
        var buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.add(Box.createHorizontalGlue()); buttons.add(cancel); buttons.add(Box.createHorizontalStrut(8)); buttons.add(save);
        south.add(buttons);
        add(south, BorderLayout.SOUTH);

        DocumentListener changed = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refresh(); }
            @Override public void removeUpdate(DocumentEvent e) { refresh(); }
            @Override public void changedUpdate(DocumentEvent e) { refresh(); }
        };
        for (JTextField field : List.of(listenAddress, listenPort, forwardHost, forwardPort)) field.getDocument().addDocumentListener(changed);
        for (JRadioButton type : List.of(local, remote, dynamic)) type.addActionListener(event -> refresh());
        host.addActionListener(event -> refresh());
        allowOthers.addActionListener(event -> {
            listenAddress.setText(allowOthers.isSelected() ? Tunnel.ALL : Tunnel.LOCALHOST);
            listenAddress.setEnabled(!allowOthers.isSelected());
            refresh();
        });
        cancel.addActionListener(event -> onCancel.run());
        save.addActionListener(event -> {
            try { onSave.accept(tunnel(editing)); }
            catch (IllegalArgumentException invalid) { message.setText(invalid.getMessage()); }
        });
        refresh();
    }

    public void showError(String error) { message.setText(error); }

    private Tunnel tunnel(Optional<Tunnel> editing) {
        String nameValue = name.getText().strip();
        if (nameValue.isEmpty()) throw new IllegalArgumentException("A tunnel needs a name");
        for (Tunnel other : others)
            if (!other.id().equals(editingId) && other.name().equalsIgnoreCase(nameValue)) throw new IllegalArgumentException("A tunnel named " + other.name() + " exists");
        RemoteHost via = selectedHost().orElseThrow(() -> new IllegalArgumentException("Choose a host to go through"));
        TunnelKind kind = kind();
        int bindPort = port(listenPort, 0, "The listen port must be a number from 0 to 65535");
        int targetPort = kind == TunnelKind.DYNAMIC ? 0 : port(forwardPort, 1, "The target port must be a number from 1 to 65535");
        return editing.map(existing -> existing.withEdited(nameValue, via.id(), kind, listenAddress.getText(), bindPort, forwardHost.getText(), targetPort))
            .orElseGet(() -> Tunnel.create(nameValue, via.id(), kind, listenAddress.getText(), bindPort, forwardHost.getText(), targetPort));
    }

    private void refresh() {
        TunnelKind kind = kind();
        boolean server = kind == TunnelKind.REMOTE;
        String via = selectedHost().map(RemoteHost::name).orElse("the host");
        listenLabel.setText(server ? "Listen on (the server)" : "Listen on (this computer)");
        forwardLabel.setText(server ? "Forward to (as seen from this computer)" : "Forward to (as seen from " + via + ")");
        forwardLabel.setVisible(kind != TunnelKind.DYNAMIC);
        forwardFields.setVisible(kind != TunnelKind.DYNAMIC);
        explanation.setText(switch (kind) {
            case LOCAL -> "Connections to a port on this computer go through " + via + " to the target.";
            case REMOTE -> "Connections to a port on the server come back through this computer to the target.";
            case DYNAMIC -> "A SOCKS5 proxy on this computer; each connection goes out through " + via + ".";
        });
        exposure.setText(allowOthers.isSelected()
            ? "Other computers on the network can connect to this port." + (server ? " The server's GatewayPorts setting must allow it." : "") : " ");
        preview.setText(preview(kind, via));
        warning.setText(sharedPort(kind).map(other -> other.name() + " listens on the same port; they cannot run at once.").orElse(" "));
        revalidate();
    }

    private String preview(TunnelKind kind, String via) {
        String port = listenPort.getText().strip();
        String listen = listenAddress.getText().strip() + ":" + (port.isEmpty() ? "?" : port.equals("0") ? "any port" : port);
        String target = forwardHost.getText().strip() + ":" + (forwardPort.getText().strip().isEmpty() ? "?" : forwardPort.getText().strip());
        return switch (kind) {
            case LOCAL -> listen + " on this computer → " + target + " as seen from " + via;
            case REMOTE -> listen + " on " + via + " → " + target + " as seen from this computer";
            case DYNAMIC -> "A SOCKS5 proxy on " + listen + "; connections leave from " + via;
        };
    }

    /** Another saved tunnel on the same side, address and port: both may exist, but not run at once. */
    private Optional<Tunnel> sharedPort(TunnelKind kind) {
        int port;
        try { port = Integer.parseInt(listenPort.getText().strip()); } catch (NumberFormatException none) { return Optional.empty(); }
        if (port <= 0) return Optional.empty();
        String address = listenAddress.getText().strip();
        UUID via = selectedHost().map(RemoteHost::id).orElse(null);
        return others.stream()
            .filter(other -> !other.id().equals(editingId) && other.bindPort() == port && other.bindAddress().equals(address))
            .filter(other -> (other.kind() == TunnelKind.REMOTE) == (kind == TunnelKind.REMOTE) && (kind != TunnelKind.REMOTE || other.hostId().equals(via)))
            .findFirst();
    }

    private TunnelKind kind() { return remote.isSelected() ? TunnelKind.REMOTE : dynamic.isSelected() ? TunnelKind.DYNAMIC : TunnelKind.LOCAL; }

    private Optional<RemoteHost> selectedHost() { return Optional.ofNullable((RemoteHost) host.getSelectedItem()); }

    private static int port(JTextField field, int minimum, String problem) {
        try {
            int value = Integer.parseInt(field.getText().strip());
            if (value < minimum || value > 65535) throw new IllegalArgumentException(problem);
            return value;
        } catch (NumberFormatException bad) { throw new IllegalArgumentException(problem); }
    }

    private static JPanel pair(JTextField address, JTextField port) {
        var pair = new JPanel();
        pair.setLayout(new BoxLayout(pair, BoxLayout.X_AXIS));
        pair.add(address); pair.add(new JLabel(" : ")); pair.add(port);
        return pair;
    }

    private static void row(JPanel form, GridBagConstraints at, JLabel label, Component field) {
        at.gridx = 0; form.add(label, at);
        at.gridx = 1; form.add(field, at);
        at.gridy++;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-plugin-remote:test --tests dev.jasper.remote.ui.tunnels.TunnelEditorTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

Run the hygiene check (expected: no output), then:

```bash
git branch --show-current
git add plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelEditor.java plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelEditorTest.java
git commit -m "Remote: the tunnel editor

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Forward a Port

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/ForwardPanel.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/ForwardPanelTest.java`
- Create (test support): `plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelsTestAccess.java`

**Interfaces:**
- Consumes: `Tunnel.create`, `Tunnel.LOCALHOST`, `TunnelKind.LOCAL`, `RemoteHost`.
- Produces: `ForwardPanel(List<RemoteHost> hosts, Optional<RemoteHost> paneHost, IntPredicate free, Consumer<Tunnel> onForward, Runnable onCancel)` with `void showError(String)`; package-private `heading`, `host`, `hostRow`, `remotePort`, `localPort`, `targetHost`, `more`, `moreRow`, `keep`, `saveName`, `message`, `forward`, `cancel`. It hands back a LOCAL tunnel on `localhost`: unnamed (a quick forward) unless "Keep as a saved tunnel" is ticked. Test support `TunnelsTestAccess` (public) exposes `remotePort`, `targetHost`, `host`, `forward` for plugin tests in `dev.jasper.remote`.

- [ ] **Step 1: Write the failing test and the test access**

Create `plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/ForwardPanelTest.java`:

```java
package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ForwardPanelTest {
    final RemoteHost prod = RemoteHost.create("prod", "db.example", 22, "deploy", Auth.AGENT, "", Optional.empty());
    final RemoteHost web = RemoteHost.create("web", "web.example", 22, "deploy", Auth.AGENT, "", Optional.empty());
    final List<Tunnel> forwarded = new ArrayList<>();
    final Set<Integer> taken = new HashSet<>();

    ForwardPanel panel(Optional<RemoteHost> paneHost) {
        return new ForwardPanel(List.of(prod, web), paneHost, port -> !taken.contains(port), forwarded::add, () -> { });
    }

    @Test void theLocalPortFollowsTheRemoteOneUnlessItIsTakenOrEdited() {
        taken.add(8080);
        var panel = panel(Optional.of(prod));
        panel.remotePort.setText("3000");
        assertThat(panel.localPort.getText()).isEqualTo("3000");
        panel.remotePort.setText("8080");
        assertThat(panel.localPort.getText()).as("the next free port").isEqualTo("8081");
        panel.localPort.setText("9000");
        panel.remotePort.setText("3001");
        assertThat(panel.localPort.getText()).as("an edited local port stays").isEqualTo("9000");
    }

    @Test void fromAnSshPaneItForwardsThroughThatHost() {
        var panel = panel(Optional.of(prod));
        assertThat(panel.hostRow.isVisible()).isFalse();
        assertThat(panel.heading.getText()).isEqualTo("Forward a port on prod to this computer");
        panel.remotePort.setText("8080");
        panel.forward.doClick();
        assertThat(forwarded).singleElement().satisfies(tunnel -> {
            assertThat(tunnel.quick()).isTrue();
            assertThat(tunnel.hostId()).isEqualTo(prod.id());
            assertThat(tunnel.kind()).isEqualTo(TunnelKind.LOCAL);
            assertThat(tunnel.bindAddress()).isEqualTo(Tunnel.LOCALHOST);
            assertThat(tunnel.bindPort()).isEqualTo(8080);
            assertThat(tunnel.targetHost()).isEqualTo("localhost");
            assertThat(tunnel.targetPort()).isEqualTo(8080);
        });
    }

    @Test void elsewhereItAsksForTheHostAndMoreRevealsTheTarget() {
        var panel = panel(Optional.empty());
        assertThat(panel.hostRow.isVisible()).isTrue();
        assertThat(panel.heading.getText()).isEqualTo("Forward a port on a host to this computer");
        panel.host.setSelectedItem(web);
        assertThat(panel.moreRow.isVisible()).isFalse();
        panel.more.doClick();
        assertThat(panel.moreRow.isVisible()).isTrue();
        panel.targetHost.setText("10.0.0.7");
        panel.remotePort.setText("5432");
        panel.forward.doClick();
        assertThat(forwarded.getLast().hostId()).isEqualTo(web.id());
        assertThat(forwarded.getLast().targetHost()).isEqualTo("10.0.0.7");
    }

    @Test void keepingItNeedsAName() {
        var panel = panel(Optional.of(prod));
        panel.remotePort.setText("8080");
        assertThat(panel.saveName.isEnabled()).isFalse();
        panel.keep.doClick();
        assertThat(panel.saveName.isEnabled()).isTrue();
        assertThat(panel.saveName.getText()).isEqualTo("Port 8080 on prod");
        panel.saveName.setText(" ");
        panel.forward.doClick();
        assertThat(panel.message.getText()).isEqualTo("Name the tunnel to keep it");
        panel.saveName.setText("web app");
        panel.forward.doClick();
        assertThat(forwarded.getLast().name()).isEqualTo("web app");
    }

    @Test void badPortsAreExplained() {
        var panel = panel(Optional.of(prod));
        panel.remotePort.setText("http");
        panel.forward.doClick();
        assertThat(panel.message.getText()).isEqualTo("The remote port must be a number from 1 to 65535");
        assertThat(forwarded).isEmpty();
        panel.showError("A tunnel named web app exists");
        assertThat(panel.message.getText()).isEqualTo("A tunnel named web app exists");
    }
}
```

Create `plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelsTestAccess.java`:

```java
package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.hosts.RemoteHost;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTextField;

/** Owner-package access to the Forward a Port controls for plugin tests in other packages. */
public final class TunnelsTestAccess {
    private TunnelsTestAccess() { }
    public static JTextField remotePort(ForwardPanel panel) { return panel.remotePort; }
    public static JTextField targetHost(ForwardPanel panel) { return panel.targetHost; }
    public static JComboBox<RemoteHost> host(ForwardPanel panel) { return panel.host; }
    public static JButton forward(ForwardPanel panel) { return panel.forward; }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-plugin-remote:test --tests dev.jasper.remote.ui.tunnels.ForwardPanelTest`
Expected: FAIL — `cannot find symbol: class ForwardPanel`.

- [ ] **Step 3: Write the dialog content**

Create `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/ForwardPanel.java`:

```java
package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Forward a Port: a remote port reached on this computer's loopback, through the SSH pane's host or a chosen one. The
 * local port follows the remote one (or the next free port) until edited. It starts a quick forward, or a saved tunnel
 * when "Keep as a saved tunnel" is ticked.
 */
public final class ForwardPanel extends JPanel {
    final JLabel heading = new JLabel();
    final JComboBox<RemoteHost> host = new JComboBox<>();
    final JPanel hostRow = new JPanel(new BorderLayout(6, 0)), moreRow = new JPanel(new BorderLayout(6, 0));
    final JTextField remotePort = new JTextField(6), localPort = new JTextField(6), targetHost = new JTextField("localhost", 14);
    final JTextField saveName = new JTextField(16);
    final JButton more = new JButton("More ▸");
    final JCheckBox keep = new JCheckBox("Keep as a saved tunnel");
    final JLabel message = new JLabel(" ");
    final JButton forward = new JButton("Forward"), cancel = new JButton("Cancel");
    private final Optional<RemoteHost> paneHost;
    private final IntPredicate free;
    private boolean localEdited, filling;

    /**
     * @param paneHost the host of the SSH pane it was opened from; empty asks for one
     * @param free whether a port on this computer's loopback is free
     * @param onForward receives the quick forward, or the named tunnel to save and start
     */
    public ForwardPanel(List<RemoteHost> hosts, Optional<RemoteHost> paneHost, IntPredicate free, Consumer<Tunnel> onForward, Runnable onCancel) {
        super(new BorderLayout(0, 8));
        this.paneHost = paneHost;
        this.free = free;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        heading.setText(paneHost.map(via -> "Forward a port on " + via.name() + " to this computer").orElse("Forward a port on a host to this computer"));
        hosts.forEach(host::addItem);
        host.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value instanceof RemoteHost via ? via.name() : "", index, selected, focus);
                putClientProperty("html.disable", true);
                return this;
            }
        });
        hostRow.add(new JLabel("Host"), BorderLayout.WEST);
        hostRow.add(host, BorderLayout.CENTER);
        hostRow.setVisible(paneHost.isEmpty());
        moreRow.add(new JLabel("Forward to host"), BorderLayout.WEST);
        moreRow.add(targetHost, BorderLayout.CENTER);
        moreRow.setVisible(false);
        saveName.setEnabled(false);

        var form = new JPanel(new GridBagLayout());
        var at = new GridBagConstraints();
        at.insets = new Insets(3, 3, 3, 3);
        at.anchor = GridBagConstraints.WEST;
        at.gridx = 0;
        at.gridwidth = 2;
        at.gridy = 0;
        form.add(heading, at); at.gridy++;
        form.add(hostRow, at); at.gridy++;
        at.gridwidth = 1;
        row(form, at, "Remote port", remotePort);
        row(form, at, "Local port", localPort);
        at.gridwidth = 2;
        form.add(more, at); at.gridy++;
        form.add(moreRow, at); at.gridy++;
        form.add(keep, at); at.gridy++;
        at.gridwidth = 1;
        row(form, at, "Name", saveName);
        add(form, BorderLayout.CENTER);
        var south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        message.setAlignmentX(Component.LEFT_ALIGNMENT);
        south.add(message);
        var buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.add(Box.createHorizontalGlue()); buttons.add(cancel); buttons.add(Box.createHorizontalStrut(8)); buttons.add(forward);
        south.add(buttons);
        add(south, BorderLayout.SOUTH);

        remotePort.getDocument().addDocumentListener(listener(this::suggest));
        localPort.getDocument().addDocumentListener(listener(() -> { if (!filling) localEdited = true; }));
        more.addActionListener(event -> {
            moreRow.setVisible(!moreRow.isVisible());
            more.setText(moreRow.isVisible() ? "Less ▾" : "More ▸");
            revalidate();
        });
        keep.addActionListener(event -> {
            saveName.setEnabled(keep.isSelected());
            if (keep.isSelected() && saveName.getText().isBlank()) saveName.setText("Port " + remotePort.getText().strip() + " on " + via().map(RemoteHost::name).orElse("the host"));
        });
        cancel.addActionListener(event -> onCancel.run());
        forward.addActionListener(event -> {
            try {
                int remote = port(remotePort, 1, "The remote port must be a number from 1 to 65535");
                int local = port(localPort, 0, "The local port must be a number from 0 to 65535");
                RemoteHost through = via().orElseThrow(() -> new IllegalArgumentException("Choose a host"));
                String name = keep.isSelected() ? saveName.getText().strip() : "";
                if (keep.isSelected() && name.isEmpty()) throw new IllegalArgumentException("Name the tunnel to keep it");
                onForward.accept(Tunnel.create(name, through.id(), TunnelKind.LOCAL, Tunnel.LOCALHOST, local, targetHost.getText(), remote));
            } catch (IllegalArgumentException invalid) { message.setText(invalid.getMessage()); }
        });
    }

    public void showError(String error) { message.setText(error); }

    private Optional<RemoteHost> via() { return paneHost.or(() -> Optional.ofNullable((RemoteHost) host.getSelectedItem())); }

    /** The local port follows the remote one until someone edits it: the same number, else the next free one. */
    private void suggest() {
        if (localEdited) return;
        filling = true;
        try { localPort.setText(suggestion(remotePort.getText().strip())); }
        finally { filling = false; }
    }

    private String suggestion(String remote) {
        int port;
        try { port = Integer.parseInt(remote); } catch (NumberFormatException none) { return ""; }
        if (port < 1 || port > 65535) return "";
        for (int candidate = port; candidate <= Math.min(65535, port + 100); candidate++) if (free.test(candidate)) return String.valueOf(candidate);
        return String.valueOf(port);
    }

    private static int port(JTextField field, int minimum, String problem) {
        try {
            int value = Integer.parseInt(field.getText().strip());
            if (value < minimum || value > 65535) throw new IllegalArgumentException(problem);
            return value;
        } catch (NumberFormatException bad) { throw new IllegalArgumentException(problem); }
    }

    private static DocumentListener listener(Runnable action) {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { action.run(); }
            @Override public void removeUpdate(DocumentEvent e) { action.run(); }
            @Override public void changedUpdate(DocumentEvent e) { action.run(); }
        };
    }

    private static void row(JPanel form, GridBagConstraints at, String label, Component field) {
        at.gridx = 0; form.add(new JLabel(label), at);
        at.gridx = 1; form.add(field, at);
        at.gridx = 0; at.gridy++;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-plugin-remote:test --tests dev.jasper.remote.ui.tunnels.ForwardPanelTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

Run the hygiene check (expected: no output), then:

```bash
git branch --show-current
git add plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/ForwardPanel.java plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels
git commit -m "Remote: Forward a Port

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: The Tunnels palette scope

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsScope.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelsScopeTest.java`

**Interfaces:**
- Consumes: `TunnelRows` (`title`, `mapping`, `stateText`, `browseUri`, `matches`), `Tunnel`, `TunnelState`; SDK `PaletteScope`, `ScopeSpec`, `PaletteVerb`, `PaletteRow`, `PaletteResults`, `PaletteQuery`.
- Produces: `TunnelsScope(Supplier<List<Tunnel>> tunnels, Supplier<Optional<String>> error, Function<UUID, TunnelState> states, Function<UUID, Optional<String>> hostNames, BiConsumer<WindowHandle, Tunnel> toggle, BiConsumer<WindowHandle, Tunnel> browse, BiConsumer<WindowHandle, Tunnel> edit)`, `TunnelsScope.ID = "dev.jasper.remote.tunnels.scope"`, verbs `TOGGLE` ("toggle", Enter), `BROWSE` ("browse", Cmd/Ctrl+Enter), `EDIT` ("edit", Shift+Enter), `void changed()`. Row ids are `"tunnel." + id`.

- [ ] **Step 1: Write the failing test**

Create `plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelsScopeTest.java`:

```java
package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.tunnels.Counters;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TunnelsScopeTest {
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

    static final UUID PROD = UUID.randomUUID();
    final List<String> events = new ArrayList<>();
    final Map<UUID, TunnelState> states = new HashMap<>();
    final Tunnel db = Tunnel.create("db", PROD, TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db.internal", 5432);
    final Tunnel socks = Tunnel.create("socks", PROD, TunnelKind.DYNAMIC, Tunnel.LOCALHOST, 1080, "", 0);
    final Tunnel quick = Tunnel.create("", PROD, TunnelKind.LOCAL, Tunnel.LOCALHOST, 3000, "localhost", 3000);
    Optional<String> error = Optional.empty();
    final TunnelsScope scope = new TunnelsScope(() -> List.of(quick, socks, db), () -> error, id -> states.getOrDefault(id, TunnelState.STOPPED),
        id -> id.equals(PROD) ? Optional.of("prod") : Optional.empty(),
        (w, t) -> events.add("toggle " + t.bindPort()), (w, t) -> events.add("browse " + t.bindPort()), (w, t) -> events.add("edit " + t.bindPort()));
    final PaletteQuery query = new PaletteQuery(window(), Optional.empty(), 10, true);

    @Test void savedTunnelsComeFirstWithTheirMappingStateAndHost() {
        assertThat(scope.spec().id()).isEqualTo(TunnelsScope.ID);
        assertThat(scope.spec().label()).isEqualTo("Tunnels");
        assertThat(scope.spec().verbs()).extracting(PaletteVerb::id).containsExactly("toggle", "browse", "edit");
        List<PaletteRow> rows = scope.search("", query).rows();
        assertThat(rows).extracting(PaletteRow::title).containsExactly("db", "socks", "localhost:3000");
        assertThat(rows.getFirst().id()).isEqualTo("tunnel." + db.id());
        assertThat(rows.getFirst().detail()).contains("localhost:5432 → db.internal:5432 · stopped");
        assertThat(rows.getFirst().tag()).contains("prod");
        assertThat(scope.search("socks", query).rows()).extracting(PaletteRow::title).containsExactly("socks");
    }

    @Test void verbsFollowTheTunnel() {
        states.put(db.id(), new TunnelState.Up(Instant.now(), 5432, new Counters()));
        List<PaletteRow> rows = scope.search("", query).rows();
        PaletteRow running = rows.get(0), proxy = rows.get(1), unsaved = rows.get(2);
        assertThat(scope.available(running, TunnelsScope.BROWSE, query)).isTrue();
        assertThat(scope.available(proxy, TunnelsScope.BROWSE, query)).isFalse();
        assertThat(scope.available(unsaved, TunnelsScope.EDIT, query)).as("a quick forward is saved from the panel").isFalse();
        assertThat(scope.available(unsaved, TunnelsScope.TOGGLE, query)).isTrue();
        scope.execute(running, TunnelsScope.TOGGLE, query);
        scope.execute(running, TunnelsScope.BROWSE, query);
        scope.execute(proxy, TunnelsScope.EDIT, query);
        assertThat(events).containsExactly("toggle 5432", "browse 5432", "edit 1080");
    }

    @Test void aBrokenFileShowsOneDisabledRowAndChangesAreAnnounced() {
        error = Optional.of("line 1");
        PaletteRow problem = scope.search("", query).rows().getFirst();
        assertThat(problem.title()).isEqualTo("tunnels.toml has errors: line 1");
        assertThat(problem.enabled()).isFalse();
        assertThat(scope.available(problem, TunnelsScope.TOGGLE, query)).isFalse();
        var heard = new AtomicInteger();
        scope.onChanged(heard::incrementAndGet);
        scope.changed();
        assertThat(heard).hasValue(1);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-plugin-remote:test --tests dev.jasper.remote.ui.tunnels.TunnelsScopeTest`
Expected: FAIL — `cannot find symbol: class TunnelsScope`.

- [ ] **Step 3: Write the scope**

Create `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsScope.java`:

```java
package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.palette.PaletteQuery;
import dev.jasper.sdk.palette.PaletteResults;
import dev.jasper.sdk.palette.PaletteRow;
import dev.jasper.sdk.palette.PaletteScope;
import dev.jasper.sdk.palette.PaletteVerb;
import dev.jasper.sdk.palette.ScopeSpec;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The Tunnels tab: saved tunnels by name, then quick forwards, each with its mapping, state and host. Start or stop
 * (Enter), Open in browser (Cmd/Ctrl+Enter, a running local tunnel only), Edit tunnel (Shift+Enter, saved tunnels only).
 */
public final class TunnelsScope implements PaletteScope {
    public static final String ID = "dev.jasper.remote.tunnels.scope";
    public static final PaletteVerb TOGGLE = new PaletteVerb("toggle", "Start or stop");
    public static final PaletteVerb BROWSE = new PaletteVerb("browse", "Open in browser");
    public static final PaletteVerb EDIT = new PaletteVerb("edit", "Edit tunnel…");
    static final String ERROR_ROW = "error";

    private final Supplier<List<Tunnel>> tunnels;
    private final Supplier<Optional<String>> error;
    private final Function<UUID, TunnelState> states;
    private final Function<UUID, Optional<String>> hostNames;
    private final BiConsumer<WindowHandle, Tunnel> toggle, browse, edit;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public TunnelsScope(Supplier<List<Tunnel>> tunnels, Supplier<Optional<String>> error, Function<UUID, TunnelState> states,
                        Function<UUID, Optional<String>> hostNames, BiConsumer<WindowHandle, Tunnel> toggle,
                        BiConsumer<WindowHandle, Tunnel> browse, BiConsumer<WindowHandle, Tunnel> edit) {
        this.tunnels = tunnels; this.error = error; this.states = states; this.hostNames = hostNames;
        this.toggle = toggle; this.browse = browse; this.edit = edit;
    }

    @Override public ScopeSpec spec() {
        return ScopeSpec.of(ID, "Tunnels", "Search tunnels", List.of(TOGGLE, BROWSE, EDIT)).withDescription("Start, stop or edit an SSH tunnel");
    }

    @Override public PaletteResults search(String query, PaletteQuery context) {
        var rows = new ArrayList<PaletteRow>();
        error.get().ifPresent(message -> rows.add(PaletteRow.of(ERROR_ROW, "tunnels.toml has errors: " + message).withEnabled(false)));
        tunnels.get().stream()
            .filter(tunnel -> TunnelRows.matches(tunnel, hostNames.apply(tunnel.hostId()).orElse(""), query))
            .sorted(Comparator.comparing(Tunnel::quick).thenComparing(tunnel -> TunnelRows.title(tunnel, states.apply(tunnel.id())).toLowerCase(Locale.ROOT)))
            .limit(Math.max(0, context.maxResults()))
            .forEach(tunnel -> {
                TunnelState state = states.apply(tunnel.id());
                rows.add(PaletteRow.of("tunnel." + tunnel.id(), TunnelRows.title(tunnel, state))
                    .withDetail(TunnelRows.mapping(tunnel, state) + " · " + TunnelRows.stateText(state))
                    .withTag(hostNames.apply(tunnel.hostId()).orElse(null)).withToken(tunnel));
            });
        return PaletteResults.of(rows);
    }

    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!(row.token() instanceof Tunnel tunnel)) return false;
        if (verb.equals(BROWSE)) return TunnelRows.browseUri(tunnel, states.apply(tunnel.id())).isPresent();
        if (verb.equals(EDIT)) return !tunnel.quick();
        return true;
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (!(row.token() instanceof Tunnel tunnel)) return;
        if (verb.equals(BROWSE)) browse.accept(context.window(), tunnel);
        else if (verb.equals(EDIT)) edit.accept(context.window(), tunnel);
        else toggle.accept(context.window(), tunnel);
    }

    @Override public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }

    /** Tells the palette the rows changed. */
    public void changed() { listeners.forEach(Runnable::run); }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-plugin-remote:test --tests dev.jasper.remote.ui.tunnels.TunnelsScopeTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

Run the hygiene check (expected: no output), then:

```bash
git branch --show-current
git add plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsScope.java plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelsScopeTest.java
git commit -m "Remote: the Tunnels palette scope

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---
### Task 9: `TunnelsUi` and the plugin wiring (Remote 0.9.0)

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsUi.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`
- Modify: `plugins/remote/src/main/resources/plugin.toml`
- Modify: `plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/TunnelsPluginTest.java`

**Interfaces:**
- Consumes: everything above; `HostStore.hosts()` / `host(UUID)`; `RemotePlugin`'s `panes` map (pane id → host id), `settings.connection().connectTimeout()`, `MENU`, `PANEL`, `currentConfirm()`; SDK `Actions`, `Panels`, `PanelHost`, `Menus.terminalContext()`, `Palette.register`, `StatusBar.add`, `Windows.dialog` (a modal dialog's `show()` returns after it closes), `Platform.browse`; test support `FakeVault`, `LoopbackServer`, `EchoServer`, `TunnelsTestAccess`, `UiTestAccess`, `RemotePluginTest.settle`/`INFO`.
- Produces: `TunnelsUi(PluginContext, Executor ui, TunnelStore, TunnelManager, Supplier<List<RemoteHost>> hosts, Function<UUID, Optional<RemoteHost>> host, Function<UUID, Optional<RemoteHost>> paneHost)` (plus an overload taking `Consumer<String> clipboard`) with constants `PANEL`, `TOGGLE`, `SHOW`, `NEW`, `FORWARD`, `STOP_ALL`, `STATUS`, and `addToMenu(PluginMenu)`, `hostsChanged()`, `usersOf(UUID hostId)`, `manager()`, `currentEditor()`, `currentForward()`, `currentConfirm()`, `close()`; package-private `RemotePlugin.tunnels()` and `RemotePlugin.tunnelStore()`.

- [ ] **Step 1: Write the failing plugin test and update the existing expectations**

Create `plugins/remote/src/test/java/dev/jasper/remote/TunnelsPluginTest.java`:

```java
package dev.jasper.remote;

import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.remote.tunnels.EchoServer;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelFile;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.remote.ui.HostRows;
import dev.jasper.remote.ui.HostsPanel;
import dev.jasper.remote.ui.tunnels.TunnelsScope;
import dev.jasper.remote.ui.tunnels.TunnelsTestAccess;
import dev.jasper.remote.ui.tunnels.TunnelsUi;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.remote.ui.UiTestAccess.list;
import static dev.jasper.remote.ui.UiTestAccess.menuFor;
import static org.assertj.core.api.Assertions.*;

class TunnelsPluginTest {
    final List<Runnable> scheduled = new ArrayList<>();

    RemotePlugin plugin(Path sshDir) {
        return new TestRemotePlugin(Runnable::run, context -> Optional.empty(), (delay, task) -> { scheduled.add(task); return () -> scheduled.remove(task); }, sshDir);
    }

    /** Drains background work until the condition holds; sockets and MINA run on their own threads meanwhile. */
    static void await(FakePluginHost host, BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (true) {
            RemotePluginTest.settle(host);
            if (condition.getAsBoolean()) return;
            if (System.nanoTime() > deadline) throw new AssertionError("timed out waiting for " + what);
            Thread.sleep(10);
        }
    }

    /** Remote beside the fake Vault, one trusted host on the loopback server, and an active window. */
    record Started(RemotePlugin plugin, PluginContext context, RemoteHost prod, UUID window) { }

    Started start(FakePluginHost host, LoopbackServer server, Path dir) throws Exception {
        var vault = new FakeVault();
        host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
        RemotePlugin plugin = plugin(dir.resolve("ssh"));
        var context = host.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault"), plugin);
        UUID credential = vault.password("deploy login", "deploy", "s3cret");
        RemoteHost prod = RemoteHost.create("prod", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty());
        plugin.store().put(prod);
        RemotePluginTest.settle(host);
        new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
        UUID window = host.addTerminalWindow();
        host.activateTerminalWindow(window);
        return new Started(plugin, context, prod, window);
    }

    @Test void registersTheTunnelsSurface(@TempDir Path dir) {
        try (var host = new FakePluginHost()) {
            host.start(RemotePluginTest.INFO, Set.of(), Set.of(), plugin(dir.resolve("ssh")));
            assertThat(host.failures()).isEmpty();
            assertThat(host.actions()).contains(TunnelsUi.TOGGLE + "|Toggle Tunnels|true", TunnelsUi.SHOW + "|Show Tunnels|true",
                TunnelsUi.NEW + "|New Tunnel...|true", TunnelsUi.FORWARD + "|Forward a Port...|true", TunnelsUi.STOP_ALL + "|Stop All Tunnels|false");
            assertThat(host.panels()).contains(TunnelsUi.PANEL + "|Tunnels|LEFT|" + TunnelsUi.TOGGLE);
            assertThat(host.scopes()).contains(TunnelsScope.ID + "|Tunnels|toggle,browse,edit");
            assertThat(host.menu("top:" + RemotePlugin.MENU)).containsSubsequence("item:" + TunnelsUi.TOGGLE, "item:" + TunnelsUi.NEW,
                "item:" + TunnelsUi.FORWARD, "item:" + TunnelsUi.STOP_ALL);
            assertThat(host.menu("context")).contains("item:" + TunnelsUi.FORWARD);
            assertThat(host.status()).as("hidden while nothing runs").isEmpty();
        }
    }

    @Test void aSavedTunnelStartsFromThePaletteForwardsAndOpensInTheBrowser(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var echo = new EchoServer(); var host = new FakePluginHost()) {
            Started started = start(host, server, dir);
            Tunnel db = Tunnel.create("db", started.prod().id(), TunnelKind.LOCAL, "127.0.0.1", 0, "127.0.0.1", echo.port());
            started.plugin().tunnelStore().put(db);
            RemotePluginTest.settle(host);
            var manager = started.plugin().tunnels().manager();
            assertThat(host.searchScope(TunnelsScope.ID, "", started.window(), null)).containsExactly("tunnel." + db.id() + "|db|true");
            host.executeInScope(TunnelsScope.ID, "tunnel." + db.id(), "toggle", started.window(), null);
            await(host, () -> manager.state(db.id()) instanceof TunnelState.Up, "the tunnel to come up");
            int port = ((TunnelState.Up) manager.state(db.id())).boundPort();
            assertThat(EchoServer.roundTrip(port, "hello")).isEqualTo("hello");
            assertThat(host.status()).containsExactly(TunnelsUi.STATUS + "|RIGHT|1 tunnel|db: up|" + TunnelsUi.TOGGLE);
            assertThat(host.actions()).contains(TunnelsUi.STOP_ALL + "|Stop All Tunnels|true");
            host.searchScope(TunnelsScope.ID, "", started.window(), null);
            host.executeInScope(TunnelsScope.ID, "tunnel." + db.id(), "browse", started.window(), null);
            assertThat(host.browsed()).containsExactly(URI.create("http://127.0.0.1:" + port + "/"));
            host.searchScope(TunnelsScope.ID, "", started.window(), null);
            host.executeInScope(TunnelsScope.ID, "tunnel." + db.id(), "toggle", started.window(), null);
            assertThat(manager.state(db.id())).isEqualTo(TunnelState.STOPPED);
            RemotePluginTest.settle(host);
            assertThat(host.status()).isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void forwardAPortStartsAQuickForwardThatStoppingForgets(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var echo = new EchoServer(); var host = new FakePluginHost()) {
            Started started = start(host, server, dir);
            var tunnels = started.plugin().tunnels();
            assertThat(host.invoke(TunnelsUi.FORWARD, started.window(), null)).isTrue();
            assertThat(host.windows()).containsExactly("dialog|Forward a Port|true");
            var panel = tunnels.currentForward();
            TunnelsTestAccess.host(panel).setSelectedItem(started.prod());
            TunnelsTestAccess.targetHost(panel).setText("127.0.0.1");
            TunnelsTestAccess.remotePort(panel).setText(String.valueOf(echo.port()));
            TunnelsTestAccess.forward(panel).doClick();
            assertThat(host.windows()).as("the dialog closed").isEmpty();
            var manager = tunnels.manager();
            assertThat(manager.quickForwards()).singleElement().satisfies(tunnel -> assertThat(tunnel.quick()).isTrue());
            Tunnel quick = manager.quickForwards().getFirst();
            await(host, () -> manager.state(quick.id()) instanceof TunnelState.Up, "the quick forward to come up");
            int port = ((TunnelState.Up) manager.state(quick.id())).boundPort();
            assertThat(EchoServer.roundTrip(port, "quick")).isEqualTo("quick");
            assertThat(host.searchScope(TunnelsScope.ID, "", started.window(), null)).containsExactly("tunnel." + quick.id() + "|localhost:" + port + "|true");
            host.executeInScope(TunnelsScope.ID, "tunnel." + quick.id(), "toggle", started.window(), null);
            assertThat(manager.quickForwards()).as("stopping a quick forward forgets it").isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void deletingAHostNamesTheTunnelsThatUseIt(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            Started started = start(host, server, dir);
            started.plugin().tunnelStore().put(Tunnel.create("db", started.prod().id(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db", 5432));
            RemotePluginTest.settle(host);
            var hosts = (HostsPanel) host.openPanel(RemotePlugin.PANEL, started.window());
            int row = -1;
            for (int i = 0; i < list(hosts).getModel().getSize(); i++)
                if (list(hosts).getModel().getElementAt(i) instanceof HostRows.Host entry && entry.host().id().equals(started.prod().id())) row = i;
            JMenuItem delete = null;
            for (var item : menuFor(hosts, row).getComponents()) if (item instanceof JMenuItem entry && entry.getText().equals("Delete…")) delete = entry;
            assertThat(delete).isNotNull();
            delete.doClick();
            var confirm = started.plugin().currentConfirm();
            assertThat(((JLabel) confirm.getComponent(0)).getText())
                .isEqualTo("Delete the saved host prod? Open sessions stay connected. 1 tunnel uses this host (db); they stay, marked Host missing.");
        }
    }

    @Test void anOutsideEditRestartsARunningTunnelAndARemovalStopsIt(@TempDir Path dir) throws Exception {
        try (var server = new LoopbackServer(); var first = new EchoServer(); var second = new EchoServer(); var host = new FakePluginHost()) {
            Started started = start(host, server, dir);
            var store = started.plugin().tunnelStore();
            var manager = started.plugin().tunnels().manager();
            Tunnel db = Tunnel.create("db", started.prod().id(), TunnelKind.LOCAL, "127.0.0.1", 0, "127.0.0.1", first.port());
            store.put(db);
            RemotePluginTest.settle(host);
            manager.start(db, null);
            await(host, () -> manager.state(db.id()) instanceof TunnelState.Up, "up");
            Tunnel moved = db.withEdited("db", db.hostId(), TunnelKind.LOCAL, "127.0.0.1", 0, "127.0.0.1", second.port());
            Files.writeString(store.file(), TunnelFile.format(List.of(moved)));
            Files.setLastModifiedTime(store.file(), FileTime.from(Instant.now().plusSeconds(5)));
            store.poll();
            await(host, () -> manager.running(db.id()).map(Tunnel::targetPort).orElse(0) == second.port()
                && manager.state(db.id()) instanceof TunnelState.Up, "the restart");
            int port = ((TunnelState.Up) manager.state(db.id())).boundPort();
            assertThat(EchoServer.roundTrip(port, "moved")).isEqualTo("moved");
            Files.writeString(store.file(), TunnelFile.format(List.of()));
            Files.setLastModifiedTime(store.file(), FileTime.from(Instant.now().plusSeconds(10)));
            store.poll();
            await(host, () -> manager.state(db.id()).equals(TunnelState.STOPPED), "a removed tunnel to stop");
        }
    }
}
```

In `RemotePluginTest.java`, replace (inside `hostRequestsNetworkAndServerIcons`)

```java
                            if (method.getName().equals("icon")) {
                                assertThat(args[0]).isInstanceOf(dev.jasper.sdk.ui.IconName.class);
                            }
```

with

```java
                            if (method.getName().equals("icon")) {
                                // Semantic names everywhere but the tunnels icon, which has none.
                                if (args[0] instanceof dev.jasper.sdk.ui.BundledIcon bundled)
                                    assertThat(bundled).isEqualTo(dev.jasper.sdk.ui.BundledIcon.ACTIONS_SWAP_PANELS);
                                else assertThat(args[0]).isInstanceOf(dev.jasper.sdk.ui.IconName.class);
                            }
```

Replace both occurrences (in `hostRequestsNetworkAndServerIcons` and `registersItsSurfaceAndConnectsThroughSessionsToolbar`) of

```java
            assertThat(host.panels()).containsExactly("dev.jasper.remote.sftp.panel|SFTP|LEFT|dev.jasper.remote.sftp.toggle",
                "dev.jasper.remote.panel|Sessions|LEFT|dev.jasper.remote.hosts");
```

with

```java
            assertThat(host.panels()).containsExactly("dev.jasper.remote.sftp.panel|SFTP|LEFT|dev.jasper.remote.sftp.toggle",
                "dev.jasper.remote.tunnels.panel|Tunnels|LEFT|dev.jasper.remote.tunnels",
                "dev.jasper.remote.panel|Sessions|LEFT|dev.jasper.remote.hosts");
```

and replace

```java
            assertThat(host.scopes()).containsExactly("dev.jasper.remote.scope|SSH|connect,split,edit");
```

with

```java
            assertThat(host.scopes()).containsExactly("dev.jasper.remote.tunnels.scope|Tunnels|toggle,browse,edit",
                "dev.jasper.remote.scope|SSH|connect,split,edit");
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests dev.jasper.remote.TunnelsPluginTest --tests dev.jasper.remote.RemotePluginTest`
Expected: FAIL — compilation errors: `cannot find symbol: class TunnelsUi`, `method tunnels()`, `method tunnelStore()`.

- [ ] **Step 3: Write `TunnelsUi`**

Create `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsUi.java`:

```java
package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelManager;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.remote.tunnels.TunnelStore;
import dev.jasper.remote.ui.ConfirmPanel;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Anchor;
import dev.jasper.sdk.ui.BundledIcon;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PanelHost;
import dev.jasper.sdk.ui.PanelSpec;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StatusItem;
import dev.jasper.sdk.ui.StatusItemSpec;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Registers the tunnels surface — the Tunnels panel, its actions, the SSH menu group, Forward a Port on the terminal
 * menu, the palette scope and the status item — and keeps the manager in step with tunnels.toml: a removed tunnel stops,
 * a tunnel whose forwarding changed restarts. UI thread only.
 */
public final class TunnelsUi implements AutoCloseable {
    public static final String PANEL = "dev.jasper.remote.tunnels.panel", TOGGLE = "dev.jasper.remote.tunnels",
        SHOW = "dev.jasper.remote.tunnels.show", NEW = "dev.jasper.remote.tunnels.new", FORWARD = "dev.jasper.remote.tunnels.forward",
        STOP_ALL = "dev.jasper.remote.tunnels.stop-all", STATUS = "dev.jasper.remote.tunnels.status";

    private final PluginContext context;
    private final Executor ui;
    private final TunnelStore store;
    private final TunnelManager manager;
    private final Supplier<List<RemoteHost>> hosts;
    private final Function<UUID, Optional<RemoteHost>> host;
    private final Consumer<String> clipboard;
    private final Icon icon;
    private Icon warningIcon;
    private final Map<UUID, TunnelsPanel> panels = new HashMap<>();
    private final Map<UUID, PanelHost> panelHosts = new HashMap<>();
    private final Set<PluginDialog> dialogs = new HashSet<>();
    private final TunnelsScope scope;
    private final StatusItem status;
    private final PluginAction stopAll;
    private final Timer ticker;
    private TunnelEditor currentEditor;
    private ForwardPanel currentForward;
    private ConfirmPanel currentConfirm;
    private boolean closed;

    public TunnelsUi(PluginContext context, Executor ui, TunnelStore store, TunnelManager manager, Supplier<List<RemoteHost>> hosts,
                     Function<UUID, Optional<RemoteHost>> host, Function<UUID, Optional<RemoteHost>> paneHost) {
        this(context, ui, store, manager, hosts, host, paneHost,
            text -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null));
    }

    /**
     * @param host a saved host by id
     * @param paneHost the saved host behind an SSH pane, by pane id; empty for any other pane
     * @param clipboard where Copy Address puts its text
     */
    public TunnelsUi(PluginContext context, Executor ui, TunnelStore store, TunnelManager manager, Supplier<List<RemoteHost>> hosts,
                     Function<UUID, Optional<RemoteHost>> host, Function<UUID, Optional<RemoteHost>> paneHost, Consumer<String> clipboard) {
        this.context = context; this.ui = ui; this.store = store; this.manager = manager; this.hosts = hosts; this.host = host;
        this.clipboard = clipboard;
        icon = context.appearance().icon(BundledIcon.ACTIONS_SWAP_PANELS);
        // Actions first: the panel names its toggle, and menus may only name registered actions.
        context.actions().register(ActionSpec.of(TOGGLE, "Toggle Tunnels").withIcon(icon)
            .withKeywords(List.of("tunnels", "ports", "forward", "panel", "show", "hide")), invoked -> context.panels().toggle(PANEL, invoked.window()));
        context.actions().register(ActionSpec.of(SHOW, "Show Tunnels").withIcon(icon).withKeywords(List.of("tunnels", "ports")),
            invoked -> reveal(invoked.window()));
        context.actions().register(ActionSpec.of(NEW, "New Tunnel...").withKeywords(List.of("tunnel", "port", "forward", "ssh", "socks")),
            invoked -> edit(invoked.window(), Optional.empty(), invoked.pane().flatMap(pane -> paneHost.apply(pane.id())).map(RemoteHost::id)));
        context.actions().register(ActionSpec.of(FORWARD, "Forward a Port...").withKeywords(List.of("tunnel", "port", "forward", "ssh")),
            invoked -> forward(invoked.window(), invoked.pane().flatMap(pane -> paneHost.apply(pane.id()))));
        stopAll = context.actions().register(ActionSpec.of(STOP_ALL, "Stop All Tunnels").withKeywords(List.of("tunnels", "stop")),
            invoked -> manager.stopAll());
        stopAll.setEnabled(false);
        context.menus().terminalContext().add(FORWARD);
        context.panels().register(new PanelSpec(PANEL, "Tunnels", icon, Anchor.LEFT).withToggleActionId(TOGGLE), this::create);
        scope = new TunnelsScope(this::all, store::error, manager::state, this::hostName,
            this::toggle, (window, tunnel) -> browse(tunnel), (window, tunnel) -> edit(window, Optional.of(tunnel), Optional.empty()));
        context.palette().register(scope);
        status = context.statusBar().add(new StatusItemSpec(STATUS, Side.RIGHT, 59));
        status.setIcon(icon);
        status.setAction(TOGGLE);
        status.setVisible(false);
        ticker = new Timer(1000, event -> panels.values().forEach(TunnelsPanel::refresh));
        store.onChanged(this::storeChanged);
        manager.onChanged(this::refresh);
    }

    /** The SSH menu's tunnels group. */
    public void addToMenu(PluginMenu menu) {
        menu.addSeparator();
        menu.add(TOGGLE);
        menu.add(NEW);
        menu.add(FORWARD);
        menu.add(STOP_ALL);
    }

    /** Saved hosts changed: the rows' host names follow. */
    public void hostsChanged() { refresh(); }

    /** The saved tunnels that go through {@code hostId}, for the delete-host confirmation. */
    public List<String> usersOf(UUID hostId) {
        return store.tunnels().stream().filter(tunnel -> tunnel.hostId().equals(hostId)).map(Tunnel::name).toList();
    }

    public TunnelManager manager() { return manager; }
    public TunnelEditor currentEditor() { return currentEditor; }
    public ForwardPanel currentForward() { return currentForward; }
    public ConfirmPanel currentConfirm() { return currentConfirm; }

    /** Plugin stop: every tunnel stops and the dialogs close. */
    @Override public void close() {
        closed = true;
        ticker.stop();
        for (PluginDialog dialog : List.copyOf(dialogs)) dialog.close();
        manager.close();
    }

    private JComponent create(PanelHost panelHost) {
        WindowHandle window = panelHost.window();
        var panel = new TunnelsPanel(new TunnelsPanel.Actions(tunnel -> manager.start(tunnel, window), tunnel -> manager.stop(tunnel.id()),
            tunnel -> edit(window, Optional.of(tunnel), Optional.empty()), this::duplicate, tunnel -> delete(window, tunnel),
            tunnel -> edit(window, Optional.of(tunnel), Optional.empty()), this::browse, this::copyAddress,
            () -> edit(window, Optional.empty(), Optional.empty())), context.appearance()::icon);
        panels.put(window.id(), panel);
        panelHosts.put(window.id(), panelHost);
        panelHost.onClosed(() -> { panels.remove(window.id()); panelHosts.remove(window.id()); });
        fill(panel);
        return panel;
    }

    private void fill(TunnelsPanel panel) { panel.setTunnels(store.tunnels(), quickOnly(), store.error(), manager::state, this::hostName); }

    /** Quick forwards not yet in the store; one that was just saved is listed once. */
    private List<Tunnel> quickOnly() {
        Set<UUID> saved = store.tunnels().stream().map(Tunnel::id).collect(Collectors.toSet());
        return manager.quickForwards().stream().filter(tunnel -> !saved.contains(tunnel.id())).toList();
    }

    private List<Tunnel> all() {
        var all = new ArrayList<>(store.tunnels());
        all.addAll(quickOnly());
        return all;
    }

    private Optional<String> hostName(UUID id) { return host.apply(id).map(RemoteHost::name); }

    private void refresh() {
        if (closed) return;
        for (TunnelsPanel panel : panels.values()) fill(panel);
        scope.changed();
        int active = manager.active(), failing = manager.failed();
        status.setText(TunnelRows.status(active, failing));
        status.setTooltip(all().stream().filter(tunnel -> !(manager.state(tunnel.id()) instanceof TunnelState.Stopped))
            .map(tunnel -> TunnelRows.title(tunnel, manager.state(tunnel.id())) + ": " + TunnelRows.stateText(manager.state(tunnel.id())))
            .collect(Collectors.joining(", ")));
        if (failing > 0) {
            if (warningIcon == null) warningIcon = context.appearance().icon(BundledIcon.STATUS_WARNING);
            status.setIcon(warningIcon);
        } else status.setIcon(icon);
        status.setVisible(active > 0 || failing > 0);
        stopAll.setEnabled(active > 0 || failing > 0);
        if (active > 0) ticker.start(); else ticker.stop();
    }

    /** tunnels.toml changed: a removed tunnel stops, a running one whose forwarding changed restarts. */
    private void storeChanged() {
        if (closed) return;
        store.warnings().forEach(warning -> context.log().log(System.Logger.Level.WARNING, "tunnels.toml: {0}", warning));
        Map<UUID, Tunnel> saved = store.tunnels().stream().collect(Collectors.toMap(Tunnel::id, tunnel -> tunnel));
        for (Tunnel running : manager.runningTunnels()) {
            Tunnel now = saved.get(running.id());
            if (now == null) { if (!running.quick()) manager.stop(running.id()); }
            else if (!now.sameForwarding(running)) { manager.stop(running.id()); manager.start(now, owner().orElse(null)); }
        }
        refresh();
    }

    private Optional<WindowHandle> owner() {
        return context.terminals().activeWindow().or(() -> context.terminals().windows().stream().findFirst());
    }

    private void toggle(WindowHandle window, Tunnel tunnel) {
        if (manager.state(tunnel.id()).running()) manager.stop(tunnel.id()); else manager.start(tunnel, window);
    }

    private void reveal(WindowHandle window) {
        PanelHost existing = panelHosts.get(window.id());
        if (existing == null) context.panels().toggle(PANEL, window); else existing.show();
    }

    private void edit(WindowHandle window, Optional<Tunnel> editing, Optional<UUID> preferredHost) {
        if (closed || !window.isOpen()) return;
        String title = editing.isEmpty() ? "New Tunnel" : editing.get().quick() ? "Save as Tunnel" : "Edit " + editing.get().name();
        PluginDialog dialog = dialog(window, title);
        TunnelEditor[] editor = new TunnelEditor[1];
        editor[0] = new TunnelEditor(hosts.get(), store.tunnels(), editing, preferredHost, tunnel -> store.put(tunnel).whenComplete((ignored, failure) -> ui.execute(() -> {
            if (closed) return;
            if (failure != null) { editor[0].showError(message(failure)); return; }
            editing.filter(Tunnel::quick).ifPresent(saved -> manager.saved(saved.id()));
            dialog.close();
        })), dialog::close);
        currentEditor = editor[0];
        dialog.setContent(editor[0]);
        dialog.onClosed(() -> { if (currentEditor == editor[0]) currentEditor = null; });
        dialog.show();
    }

    private void forward(WindowHandle window, Optional<RemoteHost> fromPane) {
        if (closed || !window.isOpen()) return;
        PluginDialog dialog = dialog(window, "Forward a Port");
        ForwardPanel[] panel = new ForwardPanel[1];
        panel[0] = new ForwardPanel(hosts.get(), fromPane, TunnelsUi::freeLoopbackPort, tunnel -> {
            if (tunnel.quick()) {
                manager.forward(tunnel, window);
                dialog.close();
                reveal(window);
                return;
            }
            store.put(tunnel).whenComplete((ignored, failure) -> ui.execute(() -> {
                if (closed) return;
                if (failure != null) { panel[0].showError(message(failure)); return; }
                manager.start(tunnel, window);
                dialog.close();
                reveal(window);
            }));
        }, dialog::close);
        currentForward = panel[0];
        dialog.setContent(panel[0]);
        dialog.onClosed(() -> { if (currentForward == panel[0]) currentForward = null; });
        dialog.show();
    }

    private void delete(WindowHandle window, Tunnel tunnel) {
        if (closed) return;
        PluginDialog dialog = dialog(window, "Delete " + tunnel.name() + "?");
        boolean running = manager.state(tunnel.id()).running();
        var confirm = new ConfirmPanel("Delete the tunnel " + tunnel.name() + "?" + (running ? " It is running; deleting it stops it." : ""), "Delete", () -> {
            manager.stop(tunnel.id());
            store.remove(tunnel.id()).whenComplete((ignored, failure) -> ui.execute(() -> {
                if (failure != null && !closed) context.notices().error(message(failure));
                dialog.close();
            }));
        }, dialog::close);
        currentConfirm = confirm;
        dialog.setContent(confirm);
        dialog.onClosed(() -> { if (currentConfirm == confirm) currentConfirm = null; });
        dialog.show();
    }

    private void duplicate(Tunnel tunnel) {
        String base = tunnel.name() + " copy";
        String name = base;
        for (int i = 2; taken(name); i++) name = base + " " + i;
        report(store.put(Tunnel.create(name, tunnel.hostId(), tunnel.kind(), tunnel.bindAddress(), tunnel.bindPort(), tunnel.targetHost(), tunnel.targetPort())));
    }

    private boolean taken(String name) { return store.tunnels().stream().anyMatch(tunnel -> tunnel.name().equalsIgnoreCase(name)); }

    private void browse(Tunnel tunnel) {
        TunnelRows.browseUri(tunnel, manager.state(tunnel.id())).ifPresent(uri -> context.platform().browse(uri));
    }

    private void copyAddress(Tunnel tunnel) {
        TunnelRows.address(tunnel, manager.state(tunnel.id()), host.apply(tunnel.hostId()).map(RemoteHost::hostname)).ifPresent(text -> {
            try { clipboard.accept(text); }
            catch (RuntimeException unavailable) { context.notices().error("Could not copy the address: " + message(unavailable)); }
        });
    }

    private PluginDialog dialog(WindowHandle window, String title) {
        PluginDialog dialog = context.windows().dialog(new DialogSpec(title, window, true));
        dialogs.add(dialog);
        dialog.onClosed(() -> dialogs.remove(dialog));
        return dialog;
    }

    private void report(CompletableFuture<Void> mutation) {
        mutation.whenComplete((ignored, failure) -> ui.execute(() -> { if (failure != null && !closed) context.notices().error(message(failure)); }));
    }

    /** Whether nothing listens on this computer's loopback at {@code port}. */
    static boolean freeLoopbackPort(int port) {
        try (var socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            return true;
        } catch (IOException taken) { return false; }
    }

    private static String message(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
```

- [ ] **Step 4: Wire it into `RemotePlugin`, bump the manifest**

In `RemotePlugin.java`:

After the field `    private dev.jasper.remote.ui.sftp.SftpUi sftpUi;` add:

```java
    private dev.jasper.remote.tunnels.TunnelStore tunnelStore;
    private dev.jasper.remote.ui.tunnels.TunnelsUi tunnelsUi;
```

In `start`, immediately before the line that begins `        remoteHosts=new dev.jasper.remote.chooser.RemoteHosts(`, add (so the panels register in the order SFTP, Tunnels, Sessions):

```java
        tunnelStore = new dev.jasper.remote.tunnels.TunnelStore(context.dataDirectory().resolve("tunnels.toml"), context.background(), ui);
        tunnelsUi = new dev.jasper.remote.ui.tunnels.TunnelsUi(context, ui, tunnelStore,
            new dev.jasper.remote.tunnels.TunnelManager(connections, () -> settings.connection().connectTimeout(), context.background(), ui),
            store::hosts, store::host, paneId -> Optional.ofNullable(panes.get(paneId)).flatMap(store::host));
```

Replace

```java
        menu.addSeparator();menu.add(SFTP);menu.add(dev.jasper.remote.ui.transfers.TransferUi.SHOW);
```

with

```java
        menu.addSeparator();menu.add(SFTP);menu.add(dev.jasper.remote.ui.transfers.TransferUi.SHOW);
        tunnelsUi.addToMenu(menu);
```

In the `store.onChanged(...)` line, replace `sessionsToolbar.refresh(); store.warnings()` with `sessionsToolbar.refresh(); tunnelsUi.hostsChanged(); store.warnings()`.

Replace

```java
        store.load();
        poll = new Timer(1000, event -> store.poll());
```

with

```java
        store.load();
        tunnelStore.load();
        poll = new Timer(1000, event -> { store.poll(); tunnelStore.poll(); });
```

In `stop()`, replace

```java
        if (sessionsToolbar != null) sessionsToolbar.close();
```

with

```java
        if (sessionsToolbar != null) sessionsToolbar.close();
        if (tunnelsUi != null) tunnelsUi.close();
```

In `deleteHost`, replace

```java
        currentConfirm = new ConfirmPanel("Delete the saved host " + host.name() + "? Open sessions stay connected.", "Delete",
```

with

```java
        currentConfirm = new ConfirmPanel(deleteHostText(host), "Delete",
```

and add this method right after `deleteHost`:

```java
    /** The confirmation names the tunnels that go through the host: they stay, marked Host missing. */
    private String deleteHostText(RemoteHost host) {
        List<String> users = tunnelsUi.usersOf(host.id());
        String tunnels = users.isEmpty() ? "" : " " + (users.size() == 1 ? "1 tunnel uses" : users.size() + " tunnels use")
            + " this host (" + String.join(", ", users) + "); they stay, marked Host missing.";
        return "Delete the saved host " + host.name() + "? Open sessions stay connected." + tunnels;
    }
```

After `    ConfirmPanel currentConfirm() { return currentConfirm; }` add:

```java
    dev.jasper.remote.ui.tunnels.TunnelsUi tunnels() { return tunnelsUi; }
    dev.jasper.remote.tunnels.TunnelStore tunnelStore() { return tunnelStore; }
```

In `plugins/remote/src/main/resources/plugin.toml` replace the `version`, `sdk` and `description` lines with:

```toml
version = "0.9.0"
```

```toml
sdk = ">=0.10.2, <0.11"
```

```toml
description = "SSH, SFTP and tunnels: browsing, persistent file transfers, editing remote files in the Editor, local, remote and SOCKS tunnels with live counters, and saved hosts with groups and favorites, managed Vault imports and optional SSH-agent authentication, ProxyJump, host-key trust, a Sessions panel that also lists WSL distributions, and palette scopes."
```

- [ ] **Step 5: Run the plugin tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test --tests dev.jasper.remote.TunnelsPluginTest --tests dev.jasper.remote.RemotePluginTest`
Expected: PASS (TunnelsPluginTest 5; RemotePluginTest unchanged count).

- [ ] **Step 6: Run everything and commit**

Run the hygiene check (expected: no output), then `./gradlew check`. Expected: BUILD SUCCESSFUL. The application tests that start the staged Remote (`RemoteShortcutConfigTest`, `PluginZipsTest`) must stay green.

```bash
git branch --show-current
git add plugins/remote
git commit -m "Remote 0.9.0: the Tunnels panel, actions, palette scope and status item

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Docs and status

**Files:**
- Modify: `docs/remote.md`
- Modify: `docs/STATUS.md`
- Modify: `docs/superpowers/plans/2026-09-30-jasper-remote-plan-7b2-tunnels-by-hand.md` (status banner)

- [ ] **Step 1: Document tunnels**

In `docs/remote.md`, replace the first line `# Remote: SSH and SFTP` with `# Remote: SSH, SFTP and tunnels`, and replace

```
contributed actions. Tunnels remain a later feature.
```

with

```
contributed actions. Tunnels have their own section at the end of this guide.
```

Append to the end of `docs/remote.md` (after the paragraph that ends with the SFTP verification link):

````markdown

## Tunnels

Remote 0.9 adds SSH tunnels. Open the **Tunnels** panel from its rail button, **SSH > Toggle Tunnels** or the
palette's Tunnels tab. A tunnel goes through a saved host — its credential, host-key trust and jump host apply — but has
its own life: starting one never opens a terminal, closing terminals never stops it, and it shares the host's SSH
connection with any terminals that are open.

- **Local** (`-L`): a port on this computer reaches a target as seen from the host, such as `localhost:5432 → db.internal:5432`.
- **Remote** (`-R`): a port on the server comes back to a target as seen from this computer. Allowing other computers to
  connect needs the server's `GatewayPorts` to permit it.
- **Dynamic** (`-D`): a SOCKS5 proxy on this computer (no authentication, CONNECT only); names resolve on the server.

Click **+** (or **SSH > New Tunnel...**) to add one: a name, the host, the type, where to listen (port 0 means any free
port) and where to forward to. Start and stop a tunnel with the button at the end of its row, Enter, or the palette
(Enter starts or stops, Cmd/Ctrl+Enter opens a running local tunnel in the browser, Shift+Enter edits). A running row
shows its open connections, the bytes moved each way and its uptime; right-click for Open in Browser, Copy Address,
Edit, Duplicate and Delete. The status bar counts running tunnels and failures.

**Forward a Port...** (a terminal's context menu, the SSH menu or the palette) forwards a remote port to the same port
on this computer's loopback (the next free one if it is taken) through the SSH pane's host. It starts a quick forward,
listed under *Quick forwards* until you stop it or Jasper quits; **Save as Tunnel...** names it and keeps it.

A row reports what went wrong: a busy port ("Port 5432 is already in use on this computer"), a refused remote port, a lost
connection ("Connection lost"), or the errors SSH sessions report. In this release a tunnel that loses its connection
stops; start it again. When the server refuses forwarded connections (for example `AllowTcpForwarding no`) the tunnel
stays up and its row says "Connections are being refused".

Tunnels are saved in `plugins/dev.jasper.remote/data/tunnels.toml`:

```toml
[[tunnel]]
id = "…"                      # keep it when editing by hand
name = "pg-prod"
host = "5f2c…"                # a saved host's id
kind = "local"                # local | remote | dynamic
bind_address = "localhost"    # localhost, an IP address, or * for all interfaces
bind_port = 5432              # 0 = any free port
target_host = "db.internal"   # local and remote only
target_port = 5432
start = "manual"
```

Edits to the file apply within a second: a running tunnel whose forwarding changed restarts, and a removed one stops. A
file that does not parse keeps the last good tunnels and shows one error row. The actions are `dev.jasper.remote.tunnels`
(Toggle Tunnels), `.tunnels.show`, `.tunnels.new`, `.tunnels.forward` and `.tunnels.stop-all`, none bound by default.

Native acceptance for tunnels (user-run):

1. A local tunnel to a real database or web server through a real host; Open in Browser for a web one.
2. A dynamic tunnel as a browser's SOCKS proxy with remote DNS: sites load and names resolve on the server.
3. A remote tunnel from the server back to a local web server; `curl localhost:<port>` on the server.
4. Forward a Port from an SSH pane, then Save as Tunnel; restart Jasper and find it saved.
5. Stop the network or `sshd`: the tunnel shows "Connection lost" and its port closes.
6. "Allow other computers to connect" on a local tunnel: macOS asks once to accept incoming connections.
````

- [ ] **Step 2: Record the status**

In `docs/STATUS.md`, directly under `## Current state — 2026-09-30` (above the existing first bullet), add — filling in the real counts from `*/build/test-results/test/*.xml` after Step 3, in the neighbouring entries' format:

```
- **Remote tunnels plan 7b-2: tunnels you run by hand** (branch `claude/tunnels-7b2`, plan
  `docs/superpowers/plans/2026-09-30-jasper-remote-plan-7b2-tunnels-by-hand.md`): Remote 0.9.0 (`sdk >=0.10.2`) adds
  saved tunnels (local, remote, dynamic SOCKS5) in `data/tunnels.toml` and quick forwards, run by hand from the new
  Tunnels rail panel, the Tunnels palette scope (Start or stop, Open in browser, Edit tunnel), the SSH menu (Toggle
  Tunnels, New Tunnel..., Forward a Port..., Stop All Tunnels) and the terminal context menu (Forward a Port...), with
  live counters, Open in Browser, Copy Address and a "N tunnels" status item. Package `dev.jasper.remote.tunnels`: an own
  engine (`Listener` binds before the lease, `TunnelManager` leases the shared session and forwards each connection over
  its own direct-tcpip channel, `Pump` counts bytes, `Socks5`), a remote tunnel through MINA's remote forwarding to a
  loopback relay; `dev.jasper.remote.ui.tunnels` holds the UI. A lost session fails the tunnel (7b-3 reconnects).
  Deviations from the spec: `*` is sent to the server as `0.0.0.0`; no `RemoteRelay` class (the relay is a `Listener`
  on `127.0.0.1:0`); the palette scope has no aliases; the rail icon is `BundledIcon.ACTIONS_SWAP_PANELS` and the
  failing status icon `BundledIcon.STATUS_WARNING`, so `RemotePluginTest`'s semantic-icon check admits that one catalog
  icon; `Pump` uses `java.net` sockets. `./gradlew check` green: <counts>. GUI checks pending with the user (docs/remote.md,
  Native acceptance for tunnels). Not merged.
```

- [ ] **Step 3: Verify everything and commit**

Run the hygiene check (expected: no output), then `./gradlew check`. Expected: BUILD SUCCESSFUL. Replace `<counts>` in the STATUS entry with the per-module counts, and change this plan's status banner to `**Status:** Written 2026-09-30 on claude/tunnels-7b2; executed <date> (<n> commits), deviations as recorded in docs/STATUS.md. Not merged.` with the real date and count.

```bash
git branch --show-current
git add docs/remote.md docs/STATUS.md docs/superpowers/plans/2026-09-30-jasper-remote-plan-7b2-tunnels-by-hand.md
git commit -m "Docs: Remote tunnels guide, native acceptance and status

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Merging `claude/tunnels-7b2` is the user's decision; plan 7b-3 (tunnels that stay up) builds on it.
