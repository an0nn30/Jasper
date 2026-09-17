# Jasper Background Residency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Jasper an opt-in resident mode where the process outlives its windows and is started at login, so a launch reveals a window from an already-warm JVM instead of paying a cold start.

**Architecture:** One binary in three roles. A launcher tries an `AF_UNIX` handoff before touching AWT and exits if a resident process accepts; a resident process (`--background`) starts everything, warms the toolkit and fonts, binds the socket and opens no window; otherwise Jasper runs exactly as it does today and, with the setting on, stays resident when its last window closes. macOS Dock clicks arrive as an AWT reopen event and never reach the socket. Quit always terminates.

**Tech Stack:** Java 25 on the JetBrains Runtime, Swing/FlatLaf, `java.nio.channels` `AF_UNIX` sockets and `FileLock`, tomlj for configuration, JUnit 5 + AssertJ.

**Spec:** [`docs/superpowers/specs/2026-09-17-jasper-background-daemon-design.md`](../specs/2026-09-17-jasper-background-daemon-design.md)

## Global Constraints

- Java 25 on the **JetBrains Runtime** 25. Use the wrapper: `./gradlew`, never a system `gradle`.
- Modules: `jasper-terminal` (`dev.jasper.terminal`) and `jasper-app` (`dev.jasper.app`). `jasper-terminal` never depends on `jasper-app`. **All work in this plan is in `jasper-app`**, except that it reads `dev.jasper.terminal.FontSet`, which is already public.
- **No public method in `jasper-terminal` takes or returns a JediTerm type.** Nothing here adds one.
- **No interface without two real implementations. No plugin API.** Callbacks use JDK functional interfaces (`java.util.function.Function`), never a new one-off interface.
- Threading: the view and all `JasperApplication` state run on the Event Dispatch Thread. The socket accept loop runs on its own **non-daemon platform thread** — that thread is what keeps a windowless JVM alive — and reaches application state only through `SwingUtilities.invokeLater`.
- **Never put raw control, private-use or unpaired surrogate characters in source.** Write Java escapes. The protocol's field separator is a real tab written as `\t` in Java source; the spec writes it `<TAB>` only for legibility.
- **Never launch the GUI** (`./gradlew :jasper-app:run`) or the benchmark from an unattended agent. Every verification step in this plan is `./gradlew check` or a narrower `--tests` filter.
- Do not commit on `main`. This plan runs on `claude/background-daemon`. End every commit message with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

- `./gradlew check` runs headless-safe tests that **never create a native window**. `application.windowClosed(null)` is the established way this suite expresses "the last window just closed" (see `WindowCommandPaletteTest`); use it rather than constructing a `TerminalWindow`.
- Baseline before any change: 844 tests, 0 failures, 2 skips (`FontSetTest.fallsBackWhenPrimaryCannotDisplay`, and the fish script test that needs fish installed). Every task must end at 0 failures and exactly those 2 skips.
- The per-task test totals below (845, 847, 850, 860, 866, 873, 876) assume every test in this plan lands as written and nothing else changes. **0 failures and 2 skips are the requirements; the totals are a cross-check.** If a total is off, find out why before continuing — a missing test is the likely cause, and a merge from `main` is the innocent one.

## File structure

**Created** (all in `jasper-app/src/main/java/dev/jasper/app/`):

| File | Responsibility |
|---|---|
| `LaunchRequest.java` | The wire format: one record, `encode`/`decode`, and a nested `Response` enum. Pure, no I/O. |
| `HandoffSocket.java` | The single-instance endpoint: bind under a lock with stale recovery, token, accept loop, and the launcher's `handOff`. |
| `LoginItem.java` | Pure per-platform autostart resolution returning a `Plan` record, plus the thin executor that applies one. |

**Modified:**

| File | Change |
|---|---|
| `ConfigSnapshot.java` | `backgroundEnabled` component + a convenience constructor keeping the old arity |
| `ConfigLoader.java` | `[background]` table and the `background.enabled` field |
| `ConfigTemplate.java` | `[background]` section |
| `config.example.toml` | `[background]` section |
| `AppArguments.java` | `--background` flag + a 2-argument convenience constructor |
| `AppDirs.java` | `daemonDir()`, `daemonSocket()`, `daemonToken()`, `daemonLock()` |
| `JasperApplication.java` | residency flag, `openOrRaise`, reopen listener, `warmUp` |
| `Main.java` | role resolution, handoff before AWT, `--background`, login-item reconciliation |

**Tests created:** `LaunchRequestTest.java`, `HandoffSocketTest.java`, `LoginItemTest.java`, `JasperApplicationResidencyTest.java`. **Tests extended:** `ConfigLoaderTest`, `AppArgumentsTest`, `AppDirsTest`, `MainConfigurationTest`.

---

### Task 1: The `[background] enabled` setting

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/ConfigSnapshot.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/ConfigLoader.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/ConfigTemplate.java`
- Modify: `config.example.toml`
- Test: `jasper-app/src/test/java/dev/jasper/app/ConfigLoaderTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `ConfigSnapshot.backgroundEnabled()` returning `boolean`, default `false`. Every later task reads the setting through this accessor.

- [ ] **Step 1: Write the failing test**

Append to `ConfigLoaderTest`, next to `buddyEnabledParsesAndRejectsNonBooleans`:

```java
    @Test void backgroundEnabledDefaultsToOffAndRejectsNonBooleans() {
        var on = parse("[background]\nenabled = true\n");
        assertThat(on.rejected()).isFalse();
        assertThat(on.diagnostics()).isEmpty();
        assertThat(on.snapshot().backgroundEnabled()).isTrue();
        // Residency is opt-in: an absent table and an empty file both mean off.
        assertThat(ConfigSnapshot.defaults().backgroundEnabled()).isFalse();
        assertThat(parse("# empty").snapshot().backgroundEnabled()).isFalse();
        var bad = parse("[background]\nenabled = 1\n");
        assertThat(bad.rejected()).isTrue();
        assertThat(bad.snapshot().backgroundEnabled()).isFalse();
        assertDiagnostic(bad, "background.enabled", 2, 1, ConfigDiagnostic.Severity.ERROR);
        var unknown = parse("[background]\nstart_at_login = true\n");
        assertDiagnostic(unknown, "background.start_at_login", 2, 1, ConfigDiagnostic.Severity.WARNING);
    }
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :jasper-app:test --tests '*ConfigLoaderTest*'
```

Expected: compile failure — `cannot find symbol: method backgroundEnabled()`.

- [ ] **Step 3: Add the component to `ConfigSnapshot`**

Change the record header, adding `backgroundEnabled` as the final component:

```java
record ConfigSnapshot(int tabHeight, WindowContent.ToolbarMode toolbar, boolean statusBar,
                      FontConfig font, Appearance variant, Map<String, String> keybindings,
                      int columns, int lines, TerminalConfig terminal, boolean buddyEnabled,
                      boolean historyEnabled, int maxResults, List<String> trivialCommands,
                      boolean backgroundEnabled) {
```

Then add a convenience constructor so every existing 13-argument call site keeps compiling. Put it immediately **after** the compact constructor and before the existing convenience constructors:

```java
    /** Residency is opt-in, so every constructor that predates it means "not resident". */
    ConfigSnapshot(int tabHeight, WindowContent.ToolbarMode toolbar, boolean statusBar,
                   FontConfig font, Appearance variant, Map<String, String> keybindings,
                   int columns, int lines, TerminalConfig terminal, boolean buddyEnabled,
                   boolean historyEnabled, int maxResults, List<String> trivialCommands) {
        this(tabHeight, toolbar, statusBar, font, variant, keybindings, columns, lines, terminal,
            buddyEnabled, historyEnabled, maxResults, trivialCommands, false);
    }
```

No validation is added to the compact constructor: every `boolean` is valid.

- [ ] **Step 4: Add the table and field to `ConfigLoader`**

Three edits.

In `FIELDS`, add `"background"` to the root set and a new entry. The root entry becomes:

```java
        Map.entry(List.of(), Set.of("window", "font", "ui", "keybindings", "terminal", "buddy", "palette", "background")),
        Map.entry(List.of("background"), Set.of("enabled")),
```

Add the field beside `buddyEnabled`:

```java
    private boolean backgroundEnabled;
```

Add the case to `readField`, beside `buddy.enabled`:

```java
            case "background.enabled" -> backgroundEnabled = bool(path, value, backgroundEnabled);
```

And pass it to the snapshot in `parse()`. The constructor call becomes:

```java
                buddyEnabled, historyEnabled, maxResults, trivialCommands, backgroundEnabled);
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :jasper-app:test --tests '*ConfigLoaderTest*'
```

Expected: PASS.

- [ ] **Step 6: Document the setting in the template and the example**

In `ConfigTemplate.text`, add this block immediately after the `[buddy]` block, keeping every value commented out so the template still parses with no diagnostics:

```java
            [background]
            # Keep Jasper running with no windows after the last one closes, so the next launch is
            # instant, and start it in the background at login. Quit still exits completely.
            # Residency applies from the next start; the login item updates as soon as you save.
            # Autostart needs an installed Jasper, not a development run.
            # enabled = false

```

In `config.example.toml`, add the same section after the `[buddy]` block, with the value active as every other example value is:

```toml
[background]
# Restart required for residency; the login item updates on reload. Keep Jasper running with no
# windows after the last one closes so the next launch is instant, and start it in the background
# at login. Quit (Cmd+Q) still exits completely. Autostart requires an installed Jasper: a
# ./gradlew run development session stays resident but never registers a login item.
enabled = false
```

- [ ] **Step 7: Run the full check**

```bash
./gradlew check
```

Expected: 845 tests, 0 failures, 2 skips. `ConfigTemplateTest` and `ExpandedConfigTest` must still pass — they parse the template and the example, so a stray active key or a typo shows up here.

- [ ] **Step 8: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/ConfigSnapshot.java jasper-app/src/main/java/dev/jasper/app/ConfigLoader.java jasper-app/src/main/java/dev/jasper/app/ConfigTemplate.java config.example.toml jasper-app/src/test/java/dev/jasper/app/ConfigLoaderTest.java
git commit -m "$(cat <<'EOF'
feat: add the background.enabled setting

Opt-in and off by default. Residency reads it at startup; the login
item reconciles against it on every config load.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Startup plumbing — the `--background` flag and the daemon paths

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/AppArguments.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/AppDirs.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/AppArgumentsTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/AppDirsTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `AppArguments.background()` returning `boolean`; the two-argument `AppArguments(Path, boolean)` constructor defaulting it to `false`. `AppDirs.daemonDir()`, `daemonSocket()`, `daemonToken()`, `daemonLock()`, each returning `Path` and touching no filesystem.

- [ ] **Step 1: Write the failing tests**

Append to `AppArgumentsTest`:

```java
    @Test void backgroundIsOffByDefaultAndParsesOnceAlongsideOtherOptions() {
        assertThat(AppArguments.parse(new String[0], cwd).background()).isFalse();
        assertThat(AppArguments.parse(new String[]{"--background"}, cwd))
            .isEqualTo(new AppArguments(null, false, true));
        assertThat(AppArguments.parse(new String[]{"--config", "c.toml", "--background"}, cwd))
            .isEqualTo(new AppArguments(cwd.resolve("c.toml"), false, true));
        for (String[] args : new String[][]{{"--background", "--background"}, {"--background", "--wat"}}) {
            assertThatIllegalArgumentException().as(java.util.Arrays.toString(args))
                .isThrownBy(() -> AppArguments.parse(args, cwd)).withMessageContaining("Usage:");
        }
        assertThat(AppArguments.USAGE).contains("--background");
    }
```

Append to `AppDirsTest`:

```java
    @Test void daemonFilesShareOwnTheirOwnSubdirectoryOfTheRoot() {
        AppDirs dirs = AppDirs.resolve("Mac OS X", Map.of(), Path.of("/Users/example"));
        Path daemon = Path.of("/Users/example/.config/jasper/daemon");
        // Its own directory, not the root: the root also holds config.toml and the endpoint
        // needs its parent to be owner-only.
        assertThat(dirs.daemonDir()).isEqualTo(daemon);
        assertThat(dirs.daemonSocket()).isEqualTo(daemon.resolve("socket"));
        assertThat(dirs.daemonToken()).isEqualTo(daemon.resolve("token"));
        assertThat(dirs.daemonLock()).isEqualTo(daemon.resolve("lock"));
        assertThat(daemon).doesNotExist();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
./gradlew :jasper-app:test --tests '*AppArgumentsTest*' --tests '*AppDirsTest*'
```

Expected: compile failure — `cannot find symbol: method background()` and `method daemonDir()`.

- [ ] **Step 3: Add the flag to `AppArguments`**

Replace the whole record body's header, usage string and parse loop:

```java
/** Startup options parsed before any desktop initialization. */
record AppArguments(Path configOverride, boolean help, boolean background) {
    static final String USAGE = "Usage: jasper [--config <path>] [--background] [--help]";

    /** The form that predates residency: an ordinary foreground launch. */
    AppArguments(Path configOverride, boolean help) {
        this(configOverride, help, false);
    }

    static AppArguments parse(String[] args, Path workingDirectory) {
        Path override = null;
        boolean help = false;
        boolean background = false;
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
                case "--config" -> {
                    if (override != null) throw invalid("Duplicate --config option.");
                    if (++i == args.length || args[i].isBlank() || args[i].startsWith("--")) {
                        throw invalid("--config requires a file path.");
                    }
                    try {
                        override = workingDirectory.resolve(Path.of(args[i])).toAbsolutePath().normalize();
                    } catch (InvalidPathException ignored) {
                        throw invalid("--config requires a valid file path.");
                    }
                }
                default -> throw invalid("Unknown argument.");
            }
        }
        return new AppArguments(override, help, background);
    }
```

Leave `invalid` and the closing brace unchanged.

- [ ] **Step 4: Add the daemon paths to `AppDirs`**

Add these four methods beside `shellIntegration()`:

```java
    /**
     * The handoff endpoint's own directory. It is a subdirectory rather than {@link #root()}
     * because binding sets its parent to owner-only, and the root also holds the user's config.
     */
    Path daemonDir() {
        return root.resolve("daemon");
    }

    Path daemonSocket() {
        return daemonDir().resolve("socket");
    }

    Path daemonToken() {
        return daemonDir().resolve("token");
    }

    Path daemonLock() {
        return daemonDir().resolve("lock");
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
./gradlew :jasper-app:test --tests '*AppArgumentsTest*' --tests '*AppDirsTest*'
```

Expected: PASS.

- [ ] **Step 6: Run the full check**

```bash
./gradlew check
```

Expected: 847 tests, 0 failures, 2 skips.

- [ ] **Step 7: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/AppArguments.java jasper-app/src/main/java/dev/jasper/app/AppDirs.java jasper-app/src/test/java/dev/jasper/app/AppArgumentsTest.java jasper-app/src/test/java/dev/jasper/app/AppDirsTest.java
git commit -m "$(cat <<'EOF'
feat: add the --background flag and the daemon paths

The endpoint gets its own subdirectory of the per-user root, because
binding makes its parent owner-only and the root holds config.toml.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: `LaunchRequest` — the wire format

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/LaunchRequest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/LaunchRequestTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `record LaunchRequest(String token, Path codeSource, long codeSourceModified)` with `String encode()` and `static LaunchRequest decode(String line)` returning `null` for anything unparseable; nested `enum LaunchRequest.Response { OK, TOKEN, STALE, PROTOCOL }` with `String line()` and `static Response of(String line)`. Task 4 uses all of these.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/LaunchRequestTest.java`:

```java
package dev.jasper.app;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class LaunchRequestTest {

    @Test void aRequestSurvivesEncodingIncludingPathsWithSeparatorsInThem() {
        // A tab separates fields and a newline ends the line, so a path containing either must
        // still arrive intact; that is what the base64 is for.
        Path awkward = Path.of("/Users/a b\tc/Jasper.app/Contents/app/jasper-app.jar");
        var request = new LaunchRequest("tok-en_123", awkward, 1_726_500_000_000L);
        String line = request.encode();
        assertThat(line).endsWith("\n").startsWith("jasper\t1\ttok-en_123\t");
        assertThat(line.chars().filter(c -> c == '\t').count()).isEqualTo(4);
        assertThat(LaunchRequest.decode(line)).isEqualTo(request);
    }

    @Test void anythingThisBuildCannotUnderstandDecodesToNullRatherThanThrowing() {
        var valid = new LaunchRequest("t", Path.of("/x.jar"), 1L).encode();
        // Five fields, except where the case is about the field count. `L3guamFy` is base64 of
        // "/x.jar": a path that decodes cleanly, so each line fails for the reason its comment
        // names instead of being masked by an earlier check.
        for (String line : new String[]{
                "", "\n", "garbage\n",
                "jasper\t1\tt\n",                                  // too few fields
                "jasper\t1\tt\tL3guamFy\t1\textra\n",              // too many fields
                "notjasper\t1\tt\tL3guamFy\t1\n",                  // wrong magic
                "jasper\t2\tt\tL3guamFy\t1\n",                     // a future protocol
                "jasper\tx\tt\tL3guamFy\t1\n",                     // unparseable protocol
                "jasper\t1\tt\tL3guamFy\tx\n",                     // unparseable timestamp
                "jasper\t1\tt\t!not base64!\t1\n",                 // undecodable base64
                "jasper\t1\tt\tAA==\t1\n"}) {                      // decodes to an illegal path
            assertThat(LaunchRequest.decode(line)).as(line.strip()).isNull();
        }
        assertThat(LaunchRequest.decode(valid)).isNotNull();
    }

    @Test void responsesRoundTripAndAnythingUnrecognisedIsAProtocolError() {
        assertThat(LaunchRequest.Response.OK.line()).isEqualTo("ok\n");
        assertThat(LaunchRequest.Response.STALE.line()).isEqualTo("refused\tstale\n");
        assertThat(LaunchRequest.Response.TOKEN.line()).isEqualTo("refused\ttoken\n");
        for (var response : LaunchRequest.Response.values()) {
            assertThat(LaunchRequest.Response.of(response.line())).isEqualTo(response);
        }
        for (String line : new String[]{"", "yes\n", "refused\n", "refused\tok\n", "ok extra\n"}) {
            assertThat(LaunchRequest.Response.of(line)).as(line.strip())
                .isEqualTo(LaunchRequest.Response.PROTOCOL);
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :jasper-app:test --tests '*LaunchRequestTest*'
```

Expected: compile failure — `cannot find symbol: class LaunchRequest`.

- [ ] **Step 3: Write the implementation**

Create `jasper-app/src/main/java/dev/jasper/app/LaunchRequest.java`:

```java
package dev.jasper.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;

/**
 * One line of the handoff protocol: what a launching process asks a resident one to do.
 *
 * <p>There is deliberately no working directory. A resident process opens a window exactly where a
 * cold launch would, so that {@code jasper} from a shell does not behave differently depending on
 * whether a daemon happened to be running.
 */
record LaunchRequest(String token, Path codeSource, long codeSourceModified) {
    private static final String MAGIC = "jasper";
    private static final int PROTOCOL = 1;
    private static final int FIELDS = 5;

    LaunchRequest {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(codeSource, "codeSource");
    }

    /** One newline-terminated line. The path is base64 so a tab or newline in it survives. */
    String encode() {
        return String.join("\t", MAGIC, Integer.toString(PROTOCOL), token,
            encode(codeSource), Long.toString(codeSourceModified)) + "\n";
    }

    /** Null for anything this build cannot act on, including a future protocol. Never throws. */
    static LaunchRequest decode(String line) {
        String[] parts = line.strip().split("\t", -1);
        if (parts.length != FIELDS || !MAGIC.equals(parts[0])) return null;
        try {
            if (Integer.parseInt(parts[1]) != PROTOCOL) return null;
            return new LaunchRequest(parts[2], decodePath(parts[3]), Long.parseLong(parts[4]));
        } catch (IllegalArgumentException malformed) {
            // Covers NumberFormatException, a bad base64 body and an unusable path alike.
            return null;
        }
    }

    private static String encode(Path path) {
        return Base64.getEncoder().encodeToString(path.toString().getBytes(StandardCharsets.UTF_8));
    }

    // Named decodePath, not decode: a private `decode(String)` would collide with the static
    // factory above — same erasure, so the file would not compile.
    private static Path decodePath(String encoded) {
        return Path.of(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8));
    }

    /** What a resident process says back. Anything unrecognised is a protocol error, never an accept. */
    enum Response {
        OK, TOKEN, STALE, PROTOCOL;

        String line() {
            return this == OK ? "ok\n" : "refused\t" + name().toLowerCase(java.util.Locale.ROOT) + "\n";
        }

        static Response of(String line) {
            String text = line.strip();
            if (text.equals("ok")) return OK;
            if (text.equals("refused\tstale")) return STALE;
            if (text.equals("refused\ttoken")) return TOKEN;
            return PROTOCOL;
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :jasper-app:test --tests '*LaunchRequestTest*'
```

Expected: PASS, 3 tests.

Note `Response.of(Response.PROTOCOL.line())` must return `PROTOCOL`: the string is `refused\tprotocol`, which matches none of the three explicit cases and falls through to `PROTOCOL`. That is the assertion in the round-trip loop.

- [ ] **Step 5: Verify no raw control characters entered the source**

```bash
python3 - jasper-app/src/main/java/dev/jasper/app/LaunchRequest.java jasper-app/src/test/java/dev/jasper/app/LaunchRequestTest.java <<'PY'
import pathlib, sys
for name in sys.argv[1:]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF
              or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    print(name, "bad chars:", bad)
PY
```

Expected: `bad chars: 0` for both. Literal tabs used for Java indentation are allowed; the protocol separator must be written `\t`.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/LaunchRequest.java jasper-app/src/test/java/dev/jasper/app/LaunchRequestTest.java
git commit -m "$(cat <<'EOF'
feat: add the handoff wire format

One tab-separated line with a base64 path, and a response enum where
anything unrecognised is a protocol error rather than an accept.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: `HandoffSocket` — the single-instance endpoint

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/HandoffSocket.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/HandoffSocketTest.java`

**Interfaces:**
- Consumes: `LaunchRequest`, `LaunchRequest.Response` from Task 3.
- Produces:
  - `static HandoffSocket bind(Path socket, Path token, Path lock, Function<LaunchRequest, LaunchRequest.Response> handler)` — returns `null` when this process is not the owner or the endpoint is unavailable; never throws.
  - `static boolean handOff(Path socket, Path token, Path codeSource, long modified)` — `true` only on `ok`.
  - `static Path codeSource()` and `static long lastModified(Path)` — what a launcher sends about itself.
  - `void close()` (from `AutoCloseable`).

  Task 7 calls `bind` and `handOff`; Task 6 supplies the handler.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/HandoffSocketTest.java`:

```java
package dev.jasper.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class HandoffSocketTest {
    @TempDir Path dir;

    private Path socket() { return dir.resolve("socket"); }
    private Path token() { return dir.resolve("token"); }
    private Path lock() { return dir.resolve("lock"); }

    private HandoffSocket bind(Function<LaunchRequest, LaunchRequest.Response> handler) {
        HandoffSocket endpoint = HandoffSocket.bind(socket(), token(), lock(), handler);
        assertThat(endpoint).as("bound the endpoint").isNotNull();
        return endpoint;
    }

    @Test void anAcceptedRequestReachesTheHandlerAndTheTokenFileIsOwnerOnly() throws Exception {
        List<LaunchRequest> seen = new CopyOnWriteArrayList<>();
        try (HandoffSocket endpoint = bind(request -> { seen.add(request); return LaunchRequest.Response.OK; })) {
            assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 7L)).isTrue();
            assertThat(seen).hasSize(1);
            assertThat(seen.get(0).codeSource()).isEqualTo(Path.of("/app.jar"));
            assertThat(seen.get(0).codeSourceModified()).isEqualTo(7L);
            if (token().getFileSystem().supportedFileAttributeViews().contains("posix")) {
                assertThat(java.nio.file.attribute.PosixFilePermissions
                    .toString(Files.getPosixFilePermissions(token()))).isEqualTo("rw-------");
            }
        }
        assertThat(socket()).doesNotExist();
        assertThat(token()).doesNotExist();
    }

    @Test void aWrongTokenIsRefusedWithoutReachingTheHandler() throws Exception {
        List<LaunchRequest> seen = new CopyOnWriteArrayList<>();
        try (HandoffSocket endpoint = bind(request -> { seen.add(request); return LaunchRequest.Response.OK; })) {
            Files.writeString(token(), "not-the-real-token\n");
            assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 7L)).isFalse();
            assertThat(seen).as("the handler never saw it").isEmpty();
        }
    }

    @Test void aStaleOwnerRefusesAndReleasesTheEndpointSoTheNewBuildCanTakeIt() throws Exception {
        try (HandoffSocket ignored = bind(request -> LaunchRequest.Response.STALE)) {
            assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/new.jar"), 9L)).isFalse();
            // Releasing is the point: the launcher that was refused must be able to become the owner.
            DesktopTestSupport.until(() -> !Files.exists(socket()));
        }
        try (HandoffSocket second = bind(request -> LaunchRequest.Response.OK)) {
            assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/new.jar"), 9L)).isTrue();
        }
    }

    @Test void handingOffToNothingFailsQuietly() {
        assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 1L)).isFalse();
    }

    @Test void aTokenThatCannotBeWrittenDeclinesAndLeavesNoSocketBehind() {
        // The token gates every request, so an endpoint without one must not exist at all.
        assertThat(HandoffSocket.bind(socket(), dir.resolve("absent/directory/token"), lock(),
            request -> LaunchRequest.Response.OK)).isNull();
        assertThat(socket()).as("a half-built endpoint is cleaned up, not left to confuse the next bind")
            .doesNotExist();
    }

    @Test void aSocketFileLeftByACrashIsReplacedButALiveOwnerIsNot() throws Exception {
        Files.createFile(socket());
        Files.writeString(token(), "left-over\n");
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK)) {
            assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 1L))
                .as("the stale file was replaced by a working endpoint").isTrue();
            // A second bind must decline rather than steal a live endpoint.
            assertThat(HandoffSocket.bind(socket(), token(), lock(), request -> LaunchRequest.Response.OK)).isNull();
        }
    }

    @Test void aPathTooLongForTheOperatingSystemDeclinesInsteadOfThrowing() throws Exception {
        Path deep = dir;
        for (int i = 0; i < 12; i++) deep = deep.resolve("a-directory-with-a-long-name");
        Files.createDirectories(deep);
        assertThat(deep.toString().length()).isGreaterThan(104);
        assertThat(HandoffSocket.bind(deep.resolve("socket"), deep.resolve("token"), deep.resolve("lock"),
            request -> LaunchRequest.Response.OK)).isNull();
    }

    @Test void theCodeSourceOfThisBuildIsResolvableAndItsTimestampIsReadable() {
        Path source = HandoffSocket.codeSource();
        assertThat(source).as("tests run from a jar or a classes directory").isNotNull();
        assertThat(source.isAbsolute()).isTrue();
        assertThat(HandoffSocket.lastModified(source)).isPositive();
        assertThat(HandoffSocket.lastModified(dir.resolve("absent"))).isZero();
    }

    @Test void theAcceptLoopSurvivesAConnectionThatSendsNothingUsable() throws Exception {
        try (HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK)) {
            try (var junk = java.nio.channels.SocketChannel.open(UnixDomainSocketAddress.of(socket()))) {
                junk.write(java.nio.ByteBuffer.wrap("nonsense\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }
            assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 1L))
                .as("still serving after junk").isTrue();
        }
    }

    @Test void afterClosingNoFurtherRequestIsAccepted() throws Exception {
        HandoffSocket endpoint = bind(request -> LaunchRequest.Response.OK);
        assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 1L)).isTrue();
        endpoint.close();
        endpoint.close(); // Idempotent.
        assertThat(HandoffSocket.handOff(socket(), token(), Path.of("/app.jar"), 1L)).isFalse();
        try (ServerSocketChannel proof = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            proof.bind(UnixDomainSocketAddress.of(socket()));  // The path was released.
        }
        Files.deleteIfExists(socket());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :jasper-app:test --tests '*HandoffSocketTest*'
```

Expected: compile failure — `cannot find symbol: class HandoffSocket`.

- [ ] **Step 3: Write the implementation**

Create `jasper-app/src/main/java/dev/jasper/app/HandoffSocket.java`:

```java
package dev.jasper.app;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.URISyntaxException;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * The single-instance endpoint. One resident process owns it; every other launch hands off to it
 * and exits.
 *
 * <p>The accept loop runs on a <strong>non-daemon</strong> platform thread, and that is deliberate:
 * AWT shuts its event dispatch thread down once nothing is displayable, so this thread is what keeps
 * a windowless resident JVM alive. Closing the endpoint ends the loop, which is how a resident
 * process with no windows exits.
 */
final class HandoffSocket implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(HandoffSocket.class.getName());
    /** A request is five short fields; anything longer is not one of ours. */
    private static final int MAX_LINE_BYTES = 8 * 1024;
    /** macOS caps sun_path at 104 bytes; a long home directory can reach it. */
    private static final int MAX_SOCKET_PATH_BYTES = 100;
    private static final long HANDOFF_TIMEOUT_MILLIS = 2_000;

    private final ServerSocketChannel channel;
    private final Path socketPath;
    private final Path tokenPath;
    private final String token;
    private final Function<LaunchRequest, LaunchRequest.Response> handler;
    private volatile boolean closed;

    private HandoffSocket(ServerSocketChannel channel, Path socketPath, Path tokenPath, String token,
                          Function<LaunchRequest, LaunchRequest.Response> handler) {
        this.channel = channel;
        this.socketPath = socketPath;
        this.tokenPath = tokenPath;
        this.token = token;
        this.handler = handler;
    }

    /**
     * Becomes the owner, or returns null. Null means another process already owns the endpoint, or
     * it is unavailable here; residency is an optimization, so failing to get it is never fatal.
     */
    static HandoffSocket bind(Path socketPath, Path tokenPath, Path lockPath,
                              Function<LaunchRequest, LaunchRequest.Response> handler) {
        if (socketPath.toString().getBytes(StandardCharsets.UTF_8).length > MAX_SOCKET_PATH_BYTES) {
            LOG.log(System.Logger.Level.WARNING,
                "Background residency is off: " + socketPath + " is too long for a Unix socket path");
            return null;
        }
        ServerSocketChannel opened = null;
        try {
            Files.createDirectories(socketPath.getParent());
            restrict(socketPath.getParent(), "rwx------");
            try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
                 FileLock ignored = lockChannel.lock()) {
                if (Files.exists(socketPath)) {
                    if (owned(socketPath)) return null;
                    Files.deleteIfExists(socketPath);
                }
                opened = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
                opened.bind(UnixDomainSocketAddress.of(socketPath));
                String token = writeToken(tokenPath);
                var endpoint = new HandoffSocket(opened, socketPath, tokenPath, token, handler);
                Thread.ofPlatform().name("jasper-handoff-accept").start(endpoint::acceptLoop);
                return endpoint;
            }
        } catch (IOException | RuntimeException failure) {
            // A bind that got as far as the socket file but no further must not leave it: the next
            // start would find a path nothing is listening on and have to recover from it.
            if (opened != null) {
                try { opened.close(); } catch (IOException ignored) { }
                try { Files.deleteIfExists(socketPath); } catch (IOException ignored) { }
            }
            LOG.log(System.Logger.Level.WARNING,
                "Background residency is unavailable; running as an ordinary application", failure);
            return null;
        }
    }

    /**
     * Asks a resident process to reveal a window. True only when it replied {@code ok}; no daemon,
     * a stale build, a bad token or a wedged process all return false and leave the caller to start
     * normally.
     */
    static boolean handOff(Path socketPath, Path tokenPath, Path codeSource, long modified) {
        String token = readToken(tokenPath);
        if (token == null) return false;
        var request = new LaunchRequest(token, codeSource, modified);
        var answer = new CompletableFuture<LaunchRequest.Response>();
        // A wedged owner must not hang the launch, so the exchange is bounded from outside it.
        Thread worker = Thread.ofPlatform().daemon().name("jasper-handoff").start(() -> {
            try (SocketChannel client = SocketChannel.open(UnixDomainSocketAddress.of(socketPath))) {
                write(client, request.encode());
                answer.complete(LaunchRequest.Response.of(readLine(client)));
            } catch (IOException | RuntimeException failure) {
                answer.complete(LaunchRequest.Response.PROTOCOL);
            }
        });
        try {
            return answer.get(HANDOFF_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) == LaunchRequest.Response.OK;
        } catch (TimeoutException | ExecutionException failure) {
            worker.interrupt();
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** The jar or classes directory this build was loaded from; null when it cannot be determined. */
    static Path codeSource() {
        try {
            var source = HandoffSocket.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) return null;
            return Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException | InvalidPathException | RuntimeException unavailable) {
            return null;
        }
    }

    /** Epoch milliseconds, or zero when the file cannot be read; zero never equals a real stamp. */
    static long lastModified(Path path) {
        if (path == null) return 0;
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException | RuntimeException unavailable) {
            return 0;
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try { channel.close(); } catch (IOException ignored) { }
        try { Files.deleteIfExists(socketPath); } catch (IOException ignored) { }
        try { Files.deleteIfExists(tokenPath); } catch (IOException ignored) { }
    }

    private void acceptLoop() {
        while (!closed) {
            try (SocketChannel client = channel.accept()) {
                serve(client);
            } catch (ClosedChannelException stopped) {
                return;
            } catch (IOException | RuntimeException failure) {
                if (closed) return;
                LOG.log(System.Logger.Level.WARNING, "A handoff request failed", failure);
            }
        }
    }

    private void serve(SocketChannel client) throws IOException {
        LaunchRequest request = LaunchRequest.decode(readLine(client));
        LaunchRequest.Response response;
        if (request == null) response = LaunchRequest.Response.PROTOCOL;
        else if (!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                request.token().getBytes(StandardCharsets.UTF_8))) {
            LOG.log(System.Logger.Level.WARNING, "A handoff request presented the wrong token; refused");
            response = LaunchRequest.Response.TOKEN;
        } else response = handler.apply(request);
        write(client, response.line());
        // An older build must release the endpoint so the newer launcher can own it. The reply is
        // already written, and the caller starts normally once it reads the refusal.
        if (response == LaunchRequest.Response.STALE) close();
    }

    /** True when something is listening: a connect that succeeds proves a live owner. */
    private static boolean owned(Path socketPath) {
        try (SocketChannel probe = SocketChannel.open(UnixDomainSocketAddress.of(socketPath))) {
            return true;
        } catch (IOException | RuntimeException gone) {
            return false;
        }
    }

    private static String writeToken(Path tokenPath) throws IOException {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Files.writeString(tokenPath, token + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        restrict(tokenPath, "rw-------");
        return token;
    }

    private static String readToken(Path tokenPath) {
        try {
            String token = Files.readString(tokenPath, StandardCharsets.UTF_8).strip();
            return token.isEmpty() ? null : token;
        } catch (IOException | RuntimeException unavailable) {
            return null;
        }
    }

    /** Owner-only where the filesystem understands it; Windows relies on the per-user directory ACL. */
    private static void restrict(Path path, String permissions) throws IOException {
        if (!path.getFileSystem().supportedFileAttributeViews().contains("posix")) return;
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
    }

    private static String readLine(SocketChannel channel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(MAX_LINE_BYTES);
        int scanned = 0;
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) break;
            for (; scanned < buffer.position(); scanned++) {
                if (buffer.get(scanned) == '\n') {
                    return new String(buffer.array(), 0, scanned, StandardCharsets.UTF_8);
                }
            }
        }
        return new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
    }

    private static void write(SocketChannel channel, String text) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
        while (buffer.hasRemaining()) channel.write(buffer);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :jasper-app:test --tests '*HandoffSocketTest*'
```

Expected: PASS, 10 tests.

If `aPathTooLongForTheOperatingSystemDeclinesInsteadOfThrowing` fails because the temporary directory is already long, the assertion `isGreaterThan(104)` on the constructed path is what matters — the guard triggers on length alone and never opens a channel.

- [ ] **Step 5: Run the full check**

```bash
./gradlew check
```

Expected: 860 tests, 0 failures, 2 skips.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/HandoffSocket.java jasper-app/src/test/java/dev/jasper/app/HandoffSocketTest.java
git commit -m "$(cat <<'EOF'
feat: add the single-instance handoff endpoint

Bind takes a file lock, refuses to steal a live owner and replaces a
socket left by a crash. The accept loop runs on a non-daemon thread,
which is what keeps a windowless resident JVM alive. A token file gates
requests so another local user cannot open a terminal on this display,
and a stale build releases the endpoint instead of serving old windows.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: `LoginItem` — per-platform autostart

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/LoginItem.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/LoginItemTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `record LoginItem.Plan(Path file, String contents, List<List<String>> commands)` with `LoginItem.Plan.NONE`; `static LoginItem.Plan plan(String osName, boolean enabled, String appPath, Path home)`; `static void apply(LoginItem.Plan plan)`. Task 7 calls `plan` then `apply`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/LoginItemTest.java`:

```java
package dev.jasper.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LoginItemTest {
    @TempDir Path home;

    private static final String MAC_APP = "/Applications/Jasper.app/Contents/MacOS/Jasper";

    @Test void macOsWritesALaunchAgentAndRunsNothing() {
        var plan = LoginItem.plan("Mac OS X", true, MAC_APP, Path.of("/Users/example"));
        assertThat(plan.file()).isEqualTo(
            Path.of("/Users/example/Library/LaunchAgents/dev.jasper.background.plist"));
        assertThat(plan.contents())
            .contains("<string>dev.jasper.background</string>")
            .contains("<string>" + MAC_APP + "</string>")
            .contains("<string>--background</string>")
            .contains("<key>RunAtLoad</key>\n    <true/>")
            .contains("<key>KeepAlive</key>\n    <false/>")
            .contains("<string>Aqua</string>");
        // launchd loads the agent at the next login, which is all this feature wants: the process
        // is already running when the setting is enabled.
        assertThat(plan.commands()).isEmpty();
    }

    @Test void macOsDisablingDeletesThePlistAndCarriesNoContents() {
        var plan = LoginItem.plan("Mac OS X", false, MAC_APP, Path.of("/Users/example"));
        assertThat(plan.file())
            .isEqualTo(Path.of("/Users/example/Library/LaunchAgents/dev.jasper.background.plist"));
        assertThat(plan.contents()).isNull();
        assertThat(plan.commands()).isEmpty();
    }

    @Test void windowsWritesARunValueAndDeletesIt() {
        String exe = "C:\\Program Files\\Jasper\\Jasper.exe";
        var on = LoginItem.plan("Windows 11", true, exe, Path.of("C:\\Users\\example"));
        assertThat(on.file()).isNull();
        assertThat(on.commands()).containsExactly(java.util.List.of("reg", "add",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
            "/v", "Jasper", "/t", "REG_SZ", "/d", "\"" + exe + "\" --background", "/f"));
        var off = LoginItem.plan("Windows 11", false, exe, Path.of("C:\\Users\\example"));
        assertThat(off.commands()).containsExactly(java.util.List.of("reg", "delete",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "Jasper", "/f"));
    }

    @Test void anUninstalledJasperRegistersNothingOnAnyPlatform() {
        // No jpackage.app-path means a development run: residency still works, autostart cannot.
        for (String os : new String[]{"Mac OS X", "Windows 11", "Linux"}) {
            for (boolean enabled : new boolean[]{true, false}) {
                assertThat(LoginItem.plan(os, enabled, null, home)).as(os).isEqualTo(LoginItem.Plan.NONE);
                assertThat(LoginItem.plan(os, enabled, "  ", home)).as(os).isEqualTo(LoginItem.Plan.NONE);
            }
        }
        // Linux autostart is out of scope; residency itself is not.
        assertThat(LoginItem.plan("Linux", true, "/opt/jasper/bin/Jasper", home)).isEqualTo(LoginItem.Plan.NONE);
    }

    @Test void anAppPathWithXmlMetacharactersCannotBreakThePlist() {
        var plan = LoginItem.plan("Mac OS X", true, "/Apps/A&B <beta>/Jasper", Path.of("/Users/example"));
        assertThat(plan.contents())
            .contains("<string>/Apps/A&amp;B &lt;beta&gt;/Jasper</string>")
            .doesNotContain("<beta>");
    }

    @Test void applyingWritesDeletesAndIsSafeToRepeat() throws Exception {
        Path plist = home.resolve("Library/LaunchAgents/dev.jasper.background.plist");
        LoginItem.apply(new LoginItem.Plan(plist, "<plist/>", java.util.List.of()));
        assertThat(plist).exists().content().isEqualTo("<plist/>");
        LoginItem.apply(new LoginItem.Plan(plist, "<plist/>", java.util.List.of()));
        assertThat(plist).exists();
        LoginItem.apply(new LoginItem.Plan(plist, null, java.util.List.of()));
        assertThat(plist).doesNotExist();
        LoginItem.apply(new LoginItem.Plan(plist, null, java.util.List.of()));  // Already gone.
        assertThat(plist).doesNotExist();
        LoginItem.apply(LoginItem.Plan.NONE);  // Does nothing at all.
        assertThat(Files.exists(home.resolve("Library"))).isTrue();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :jasper-app:test --tests '*LoginItemTest*'
```

Expected: compile failure — `cannot find symbol: class LoginItem`.

- [ ] **Step 3: Write the implementation**

Create `jasper-app/src/main/java/dev/jasper/app/LoginItem.java`:

```java
package dev.jasper.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The per-platform autostart entry. Resolution is pure, like {@link AppDirs} and
 * {@link LaunchSettings}, so both platforms' output is asserted on any host; {@link #apply} is the
 * only part that touches the system.
 */
final class LoginItem {
    private static final System.Logger LOG = System.getLogger(LoginItem.class.getName());
    private static final String LABEL = "dev.jasper.background";
    private static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String VALUE = "Jasper";

    private LoginItem() { }

    /**
     * A described change: a file to write ({@code contents} present), a file to delete
     * ({@code contents} null), and commands to run. {@link #NONE} changes nothing.
     */
    record Plan(Path file, String contents, List<List<String>> commands) {
        static final Plan NONE = new Plan(null, null, List.of());

        Plan {
            commands = List.copyOf(commands);
        }
    }

    /**
     * {@code appPath} is jpackage's {@code jpackage.app-path}: both the proof that this is an
     * installed Jasper and the executable the entry points at. A development run has none, so it
     * registers nothing.
     */
    static Plan plan(String osName, boolean enabled, String appPath, Path home) {
        Objects.requireNonNull(osName, "osName");
        Objects.requireNonNull(home, "home");
        if (appPath == null || appPath.isBlank()) return Plan.NONE;
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            Path plist = home.resolve("Library/LaunchAgents").resolve(LABEL + ".plist");
            // No launchctl: launchd loads an agent in this directory at the next login, which is
            // exactly and only what residency needs.
            return new Plan(plist, enabled ? plist(appPath) : null, List.of());
        }
        if (os.startsWith("windows")) {
            return new Plan(null, null, List.of(enabled
                ? List.of("reg", "add", RUN_KEY, "/v", VALUE, "/t", "REG_SZ",
                    "/d", "\"" + appPath + "\" --background", "/f")
                : List.of("reg", "delete", RUN_KEY, "/v", VALUE, "/f")));
        }
        return Plan.NONE;
    }

    /** Applies a plan, reporting rather than throwing: autostart is never worth failing a launch over. */
    static void apply(Plan plan) {
        if (plan.file() != null) {
            try {
                if (plan.contents() == null) Files.deleteIfExists(plan.file());
                else {
                    Files.createDirectories(plan.file().getParent());
                    Files.writeString(plan.file(), plan.contents(), StandardCharsets.UTF_8);
                }
            } catch (IOException failure) {
                LOG.log(System.Logger.Level.WARNING, "Could not update the login item " + plan.file(), failure);
            }
        }
        for (List<String> command : plan.commands()) run(command);
    }

    private static void run(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOG.log(System.Logger.Level.WARNING, "Timed out running " + String.join(" ", command));
                return;
            }
            // "reg delete" exits nonzero when the value is already absent, which is the state we want.
            int status = process.exitValue();
            if (status != 0) {
                LOG.log(System.Logger.Level.INFO,
                    String.join(" ", command) + " exited " + status + "; the login item may already be as requested");
            }
        } catch (IOException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not run " + String.join(" ", command), failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static String plist(String appPath) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Label</key>
                <string>%s</string>
                <key>ProgramArguments</key>
                <array>
                    <string>%s</string>
                    <string>--background</string>
                </array>
                <key>RunAtLoad</key>
                <true/>
                <key>KeepAlive</key>
                <false/>
                <key>LimitLoadToSessionType</key>
                <string>Aqua</string>
            </dict>
            </plist>
            """.formatted(LABEL, escape(appPath));
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :jasper-app:test --tests '*LoginItemTest*'
```

Expected: PASS, 6 tests.

- [ ] **Step 5: Run the full check**

```bash
./gradlew check
```

Expected: 866 tests, 0 failures, 2 skips.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/LoginItem.java jasper-app/src/test/java/dev/jasper/app/LoginItemTest.java
git commit -m "$(cat <<'EOF'
feat: resolve the per-platform login item

Resolution is pure, so both platforms are asserted on any host. macOS
writes a LaunchAgent plist and runs nothing at all: launchd loads it at
the next login, which is all residency needs. Windows adds an HKCU Run
value. Without jpackage.app-path there is no plan, so a development run
never points a login item into a build directory.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Residency, reopen and warm-up in `JasperApplication`

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/JasperApplication.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/JasperApplicationResidencyTest.java`

**Interfaces:**
- Consumes: `ConfigSnapshot.backgroundEnabled()` (Task 1); `dev.jasper.terminal.FontSet`, already public.
- Produces: `void residency(boolean resident)`, `boolean resident()`, `void openOrRaise(Path directory)`, `void warmUp()`, `void loginItems(java.util.function.Consumer<Boolean> reconcile)` — all EDT-confined, package-private. Task 7 calls `residency`, `loginItems`, `warmUp` and (from the socket handler) `openOrRaise`.

**Why `loginItems` is injected rather than called directly:** the setting's two halves have different timing — residency is fixed at startup, but the login item must follow the setting live, which means reacting to the existing configuration listener. Reconciling from inside `JasperApplication` would have its tests write into the developer's real `~/Library/LaunchAgents`. The seam keeps the default a no-op.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/JasperApplicationResidencyTest.java`:

```java
package dev.jasper.app;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.jasper.app.DesktopTestSupport.edt;
import static dev.jasper.app.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

/** Residency changes exactly one thing: the last window closing no longer ends the process. */
class JasperApplicationResidencyTest {

    private JasperApplication application(Runnable terminate) throws Exception {
        JasperApplication[] held = new JasperApplication[1];
        edt(() -> held[0] = new JasperApplication(null, launcher(new ArrayDeque<>()),
            new CommandHistory(), null, terminate));
        return held[0];
    }

    @Test void closingTheLastWindowOfAResidentApplicationDoesNotTerminate() throws Exception {
        AtomicInteger terminations = new AtomicInteger();
        JasperApplication application = application(terminations::incrementAndGet);
        edt(() -> application.residency(true));
        assertThat(application.resident()).isTrue();
        // windowClosed(null) is how this suite says "the last window just closed" without
        // constructing a native window; the branch under test runs when the window set empties.
        edt(() -> application.windowClosed(null));
        edt(() -> { });
        assertThat(terminations.get()).as("still resident").isZero();
        // And it is still usable: a second close is not a shutdown either.
        edt(() -> application.windowClosed(null));
        edt(() -> { });
        assertThat(terminations.get()).isZero();
    }

    @Test void quitTerminatesAResidentApplicationJustTheSame() throws Exception {
        CountDownLatch terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown);
        edt(() -> application.residency(true));
        edt(application::quit);
        assertThat(terminated.await(2, TimeUnit.SECONDS)).as("Quit always exits").isTrue();
    }

    @Test void withoutResidencyTheLastWindowStillEndsTheProcess() throws Exception {
        CountDownLatch terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown);
        assertThat(application.resident()).isFalse();
        edt(() -> application.windowClosed(null));
        assertThat(terminated.await(2, TimeUnit.SECONDS)).as("unchanged behaviour").isTrue();
    }

    @Test void residencyCanBeTurnedOffAgainBeforeTheLastWindowCloses() throws Exception {
        CountDownLatch terminated = new CountDownLatch(1);
        JasperApplication application = application(terminated::countDown);
        edt(() -> application.residency(true));
        edt(() -> application.residency(false));
        edt(() -> application.windowClosed(null));
        assertThat(terminated.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test void warmingUpBuildsTheFontSetAndRefreshesHistoryWithoutAWindow() throws Exception {
        JasperApplication application = application(() -> { });
        // The point of warm-up is that it pays the first window's costs and creates nothing:
        // it must be safe to call, repeatedly, with no configuration and no window.
        edt(application::warmUp);
        edt(application::warmUp);
        edt(() -> { });
        edt(application::quit);
    }

    @Test void theLoginItemFollowsTheSettingWhileResidencyDoesNot(@org.junit.jupiter.api.io.TempDir
                                                                 java.nio.file.Path dir) throws Exception {
        java.nio.file.Path file = dir.resolve("config.toml");
        java.nio.file.Files.writeString(file, "[background]\nenabled = true\n");
        ConfigService service = new ConfigService(file, true);
        java.util.List<Boolean> reconciled = new java.util.concurrent.CopyOnWriteArrayList<>();
        JasperApplication[] held = new JasperApplication[1];
        try {
            edt(() -> {
                held[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(),
                    null, () -> { });
                held[0].loginItems(reconciled::add);
            });
            // Registering a listener replays the current snapshot, so the first value arrives at once.
            edt(() -> { });
            assertThat(reconciled).as("the setting as loaded").containsExactly(true);
            java.nio.file.Files.writeString(file, "[background]\nenabled = false\n");
            edt(() -> service.reload());
            DesktopTestSupport.until(() -> reconciled.size() == 2);
            assertThat(reconciled).last().as("a saved change applies without a restart").isEqualTo(false);
            // Residency is startup-scoped and must not have moved with it.
            assertThat(held[0].resident()).isFalse();
        } finally {
            edt(() -> { if (held[0] != null) held[0].quit(); });
            service.close();
        }
    }

    @Test void openOrRaiseIsInertOnceTheApplicationHasQuit() throws Exception {
        JasperApplication application = application(() -> { });
        edt(() -> application.residency(true));
        edt(application::quit);
        // A request arriving during shutdown must not resurrect a window.
        edt(() -> application.openOrRaise(DesktopTestSupport.HOME));
        edt(() -> { });
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :jasper-app:test --tests '*JasperApplicationResidencyTest*'
```

Expected: compile failure — `cannot find symbol: method residency(boolean)`.

- [ ] **Step 3: Add the residency flag and the close behaviour**

In `JasperApplication`, add the field beside `quitting`:

```java
    private boolean resident;
```

Add these methods immediately after `newWindow`:

```java
    /**
     * Whether this process outlives its windows. Decided once at startup: which process owns the
     * handoff endpoint is not something to renegotiate while running.
     */
    void residency(boolean resident) { this.resident = resident; }

    boolean resident() { return resident; }

    /** The handoff and the macOS reopen event share this: raise what is open, or open the first window. */
    void openOrRaise(Path directory) {
        if (quitting || stopped) return;
        if (windows.isEmpty()) newWindow(directory);
        else raiseTerminal();
    }
```

Change `windowClosed` so the empty case respects residency:

```java
    void windowClosed(TerminalWindow window) {
        windows.remove(window);
        buddyVisibility.remove(window);
        if (lastActive == window) lastActive = null;
        // Residency keeps the warm process: the command history, the shell-history index, the
        // snippets and the configuration watcher are precisely what makes the next window fast,
        // and shutdown would close all of them. Quit still terminates.
        if (windows.isEmpty() && !resident) requestShutdown();
        else syncBuddy();
    }
```

- [ ] **Step 4: Route the login item through the existing configuration listener**

`ConfigurationController.onSnapshot` is documented as *one* application-level observer and assigns a single slot, so registering a second listener would silently replace `JasperApplication`'s own. The login item therefore rides on the existing one.

Add the field beside `resident`:

```java
    private java.util.function.Consumer<Boolean> loginItems = enabled -> { };
```

Extend the existing `onSnapshot` registration in the constructor with its third line:

```java
        if (configuration != null) configuration.onSnapshot(snapshot -> {
            buddyVisibility.configure(snapshot.buddyEnabled()); syncBuddy();
            if (snippets != null) snippets.reload();
            loginItems.accept(snapshot.backgroundEnabled());
        });
```

Add the setter after `resident()`:

```java
    /**
     * Who reconciles the user's login items with the setting. Injected, because reconciling from
     * here would have these tests write into the developer's real login items. Setting it replays
     * the current value at once: the constructor's own listener registration has already fired by
     * the time production can install this.
     */
    void loginItems(java.util.function.Consumer<Boolean> reconcile) {
        loginItems = Objects.requireNonNull(reconcile, "reconcile");
        if (configuration != null) loginItems.accept(configuration.snapshot().backgroundEnabled());
    }
```

`java.util.Objects` is already imported through `java.util.*`.

- [ ] **Step 5: Add the warm-up**

Add this method after `openOrRaise`:

```java
    /**
     * Pays the first window's one-time costs with no window on screen. Constructing this
     * application already installed the look and feel; the font set is the remaining expensive
     * piece, and building one realizes the toolkit's font machinery and the cell metrics.
     */
    void warmUp() {
        ConfigSnapshot snapshot = configuration == null ? ConfigSnapshot.defaults() : configuration.snapshot();
        FontConfig font = snapshot.font();
        try {
            new dev.jasper.terminal.FontSet(font.family(), font.size(), font.fallback(),
                font.ligatures(), font.lineHeight());
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Font warm-up failed; the first window will pay for it", failure);
        }
        shellHistory.refresh();
    }
```

- [ ] **Step 6: Register the macOS reopen listener**

Replace the `supportsNativeQuit()` helper with one that takes an action, and add the listener in the constructor.

Replace the method:

```java
    private static boolean supports(Desktop.Action action) {
        return !GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
            && Desktop.getDesktop().isSupported(action);
    }
```

Update both existing call sites — the constructor's `if (supportsNativeQuit())` and `shutdown()`'s `if (supportsNativeQuit())` — to `if (supports(Desktop.Action.APP_QUIT_HANDLER))`.

Then, in the constructor immediately after the quit-handler block, add:

```java
        // Clicking the Dock icon of a running Jasper creates no process: AppKit sends this instead.
        // Registered whether or not residency is on, because a window that is merely minimized
        // should come back the same way.
        if (supports(Desktop.Action.APP_EVENT_REOPENED)) {
            Desktop.getDesktop().addAppEventListener((java.awt.desktop.AppReopenedListener) event ->
                SwingUtilities.invokeLater(() -> openOrRaise(Path.of(System.getProperty("user.home")))));
        }
```

- [ ] **Step 7: Run the test to verify it passes**

```bash
./gradlew :jasper-app:test --tests '*JasperApplicationResidencyTest*' --tests '*JasperApplicationShutdownTest*'
```

Expected: PASS, 9 tests. The shutdown test must still pass unchanged — residency defaults to off, so nothing about today's behaviour moves.

- [ ] **Step 8: Run the full check**

```bash
./gradlew check
```

Expected: 873 tests, 0 failures, 2 skips.

- [ ] **Step 9: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/JasperApplication.java jasper-app/src/test/java/dev/jasper/app/JasperApplicationResidencyTest.java
git commit -m "$(cat <<'EOF'
feat: let the application outlive its windows

With residency on, the last window closing no longer runs shutdown, so
the command history, shell-history index, snippets and configuration
watcher stay alive -- they are what makes the next window fast. Quit is
untouched and still terminates. Warm-up builds a font set with nothing
on screen, and the macOS reopen event now raises or opens a window.

The login item rides on the existing configuration listener, since
there is only one application-level slot, and reconciling is injected
so these tests never write into real login items.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Wire the roles together in `Main`

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/Main.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/MainConfigurationTest.java`

**Interfaces:**
- Consumes: everything from Tasks 1–6.
- Produces: `Main.start(String[], PrintStream, PrintStream, BiConsumer<ConfigService, AppArguments>)` — the launch callback now receives the parsed options. `static boolean Main.handsOff(AppArguments, AppDirs)` and `static void Main.reconcileLoginItem(boolean, String, Path)` are package-private and directly testable.

- [ ] **Step 1: Write the failing test**

`MainConfigurationTest` has four `Main.start` call sites, each passing a `Consumer<ConfigService>`. Make exactly these four substitutions:

| Line | From | To |
|---|---|---|
| in `helpAndErrorsExitBeforeConfigurationOrDesktopStartup` (twice) | `service -> { throw new AssertionError("Unexpected startup"); }` | `(service, options) -> { throw new AssertionError("Unexpected startup"); }` |
| in `explicitConfigurationIsReadBeforeDispatchToSwing` | `service -> {` | `(service, options) -> {` |
| in `explicitConfigurationSuppliesTheSavedThemeVariant` | `received::set` | `(service, options) -> received.set(service)` |

Then append these tests:

```java
    @Test void aLaunchPointedAtAnotherConfigurationNeverHandsOff(@TempDir Path dir) {
        AppDirs dirs = new AppDirs(dir, dir.resolve("config.toml"), dir.resolve("logs"));
        // A different configuration file is a different Jasper; the resident one is holding another.
        assertThat(Main.handsOff(new AppArguments(dir.resolve("other.toml"), false, false), dirs)).isFalse();
        // Neither does the resident process itself.
        assertThat(Main.handsOff(new AppArguments(null, false, true), dirs)).isFalse();
        // And with nothing listening there is nothing to hand off to.
        assertThat(Main.handsOff(new AppArguments(null, false, false), dirs)).isFalse();
    }

    @Test void aHandedOffLaunchExitsZeroWithoutRunningTheApplication(@TempDir Path dir) throws Exception {
        AppDirs dirs = new AppDirs(dir, dir.resolve("config.toml"), dir.resolve("logs"));
        boolean[] launched = {false};
        try (HandoffSocket endpoint = HandoffSocket.bind(dirs.daemonSocket(), dirs.daemonToken(),
                dirs.daemonLock(), request -> LaunchRequest.Response.OK)) {
            assertThat(endpoint).isNotNull();
            assertThat(Main.handsOff(new AppArguments(null, false, false), dirs)).isTrue();
        }
        assertThat(launched[0]).isFalse();
    }

    @Test void reconcilingTheLoginItemWritesAndRemovesIt(@TempDir Path fakeHome) {
        Path plist = fakeHome.resolve("Library/LaunchAgents/dev.jasper.background.plist");
        Main.reconcileLoginItem(true, "/Applications/Jasper.app/Contents/MacOS/Jasper", fakeHome);
        assertThat(plist).exists();
        Main.reconcileLoginItem(false, "/Applications/Jasper.app/Contents/MacOS/Jasper", fakeHome);
        assertThat(plist).doesNotExist();
        // A development run has no installed path, so nothing is ever written.
        Main.reconcileLoginItem(true, null, fakeHome);
        assertThat(plist).doesNotExist();
    }
```

`reconcileLoginItem` resolves the operating system from `System.getProperty("os.name")`, so the plist assertions above hold on macOS. Add this guard above that last test so it is honest elsewhere:

```java
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.MAC)
```

Ensure `MainConfigurationTest` imports `org.junit.jupiter.api.io.TempDir`, `java.nio.file.Path` and `static org.assertj.core.api.Assertions.assertThat` if it does not already.

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :jasper-app:test --tests '*MainConfigurationTest*'
```

Expected: compile failure — `cannot find symbol: method handsOff`.

- [ ] **Step 3: Change the `start` seam to carry the options**

In `Main`, change the signature and the handoff decision:

```java
    /** Startup boundary: parsing, the handoff attempt and the first read finish before the desktop callback runs. */
    static int start(String[] args, java.io.PrintStream out, java.io.PrintStream error,
                     java.util.function.BiConsumer<ConfigService, AppArguments> launch) {
        AppArguments options;
        try { options = AppArguments.parse(args, Path.of(System.getProperty("user.dir"))); }
        catch (IllegalArgumentException failure) { error.println(failure.getMessage()); return 2; }
        if (options.help()) { out.println(AppArguments.USAGE); return 0; }
        String os = System.getProperty("os.name");
        Path home = Path.of(System.getProperty("user.home"));
        AppDirs dirs = AppDirs.resolve(os, System.getenv(), home);
        // Before any toolkit initialization: a handed-off launch must cost almost nothing and must
        // not settle a second icon into the Dock on its way out.
        if (handsOff(options, dirs)) return 0;
        Path file = options.configOverride() == null ? dirs.configFile() : options.configOverride();
        ConfigService service = new ConfigService(file, os.startsWith("Mac"));
        try { launch.accept(service, options); }
        catch (RuntimeException failure) { service.close(); throw failure; }
        return 0;
    }

    /**
     * True when a resident process accepted this launch and there is nothing left to do. A launch
     * pointed at another configuration file never hands off, because the resident process is
     * holding a different one; neither does a resident process starting up.
     */
    static boolean handsOff(AppArguments options, AppDirs dirs) {
        if (options.background() || options.configOverride() != null) return false;
        Path source = HandoffSocket.codeSource();
        return HandoffSocket.handOff(dirs.daemonSocket(), dirs.daemonToken(), source == null ? Path.of("") : source,
            HandoffSocket.lastModified(source));
    }

    /** Makes the user's login items match the setting. Never throws; autostart is not worth a failed launch. */
    static void reconcileLoginItem(boolean enabled, String appPath, Path home) {
        if (enabled && (appPath == null || appPath.isBlank())) {
            LOG.log(System.Logger.Level.INFO,
                "Background residency is on, but this Jasper is not an installed package, so it will not start at login");
        }
        LoginItem.apply(LoginItem.plan(System.getProperty("os.name"), enabled, appPath, home));
    }
```

- [ ] **Step 4: Wire the roles in `main`**

Replace the body of `main` so the callback takes both parameters and the resident role is honoured. Only the inner `SwingUtilities.invokeLater` body changes materially:

```java
    public static void main(String[] args) {
        int result = start(args, System.out, System.err, (service, options) -> {
            AppDirs dirs = AppDirs.resolve(System.getProperty("os.name"), System.getenv(),
                Path.of(System.getProperty("user.home")));
            AppLog log = AppLog.open(dirs.logs());
            UnexpectedExceptions exceptions;
            try { exceptions = installUnexpectedExceptionHandler(); }
            catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);
                service.close();
                log.close();
                return;
            }
            Thread shutdown = Thread.ofPlatform().name("jasper-log-shutdown").unstarted(() -> {
                exceptions.close();
                log.close();
            });
            try {
                Runtime.getRuntime().addShutdownHook(shutdown);
                System.setProperty("apple.awt.application.appearance", "system");
                System.setProperty("apple.laf.useScreenMenuBar", "true");
                SwingUtilities.invokeLater(() -> {
                    CommandHistory history = null;
                    JasperApplication application = null;
                    HandoffSocket endpoint = null;
                    try {
                        boolean resident = service.initialState().snapshot().backgroundEnabled();
                        Path home = Path.of(System.getProperty("user.home"));
                        String appPath = System.getProperty("jpackage.app-path");
                        reconcileLoginItem(resident, appPath, home);
                        // A login item that outlived the setting: leave rather than sit resident.
                        if (options.background() && !resident) {
                            LOG.log(System.Logger.Level.INFO,
                                "Started with --background while background.enabled is off; exiting");
                            service.close();
                            System.exit(0);
                            return;
                        }
                        history = new CommandHistory(dirs.commandHistory());
                        ApplicationIcon.installTaskbarIcon();
                        Path integrationDir = null;
                        try {
                            integrationDir = ShellIntegrationScripts.install(dirs.shellIntegration());
                        } catch (java.io.IOException failure) {
                            LOG.log(System.Logger.Level.WARNING,
                                "Shell integration scripts could not be installed; integration is off", failure);
                        }
                        application = new JasperApplication(service, null, history, dirs.buddyState(),
                            () -> System.exit(0), ShellHistoryIndex.discovered(),
                            new SnippetStore(dirs.snippets(), new ConfigEditor()::open), integrationDir);
                        if (resident) {
                            JasperApplication owner = application;
                            Path source = HandoffSocket.codeSource();
                            long modified = HandoffSocket.lastModified(source);
                            endpoint = HandoffSocket.bind(dirs.daemonSocket(), dirs.daemonToken(), dirs.daemonLock(),
                                request -> {
                                    // An older build must not serve windows built from newer code.
                                    if (!request.codeSource().equals(source == null ? Path.of("") : source)
                                            || request.codeSourceModified() != modified) {
                                        return LaunchRequest.Response.STALE;
                                    }
                                    SwingUtilities.invokeLater(() -> owner.openOrRaise(home));
                                    return LaunchRequest.Response.OK;
                                });
                            // Residency needs the endpoint: without it a windowless JVM has nothing
                            // holding it alive and nothing to be reached through.
                            application.residency(endpoint != null);
                        }
                        if (options.background() && application.resident()) application.warmUp();
                        else application.newWindow(Path.of(System.getProperty("user.home")));
                    }
                    catch (RuntimeException failure) {
                        LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);
                        if (endpoint != null) endpoint.close();
                        if (application != null) application.quit();
                        else if (history != null) history.close();
                        service.close();
                        closeLogAfterStartupFailure(log, () -> {
                            exceptions.close();
                            removeShutdownHook(shutdown);
                        });
                    }
                });
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);
                service.close();
                exceptions.close();
                log.close();
                removeShutdownHook(shutdown);
            }
        });
        if (result != 0) System.exit(result);
    }
```

`ConfigService.initialState().snapshot()` is the accessor `MainConfigurationTest` already uses; this is the first read of the configuration and it has happened before `launch` is called.

Then register the live half of the setting, immediately after the `JasperApplication` is constructed and before the `if (resident)` block:

```java
                        // The login item tracks the setting while Jasper runs; residency does not.
                        application.loginItems(enabled -> reconcileLoginItem(enabled, appPath, home));
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :jasper-app:test --tests '*MainConfigurationTest*' --tests '*MainTest*'
```

Expected: PASS.

- [ ] **Step 6: Run the full check**

```bash
./gradlew check
```

Expected: 876 tests, 0 failures, 2 skips.

- [ ] **Step 7: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/Main.java jasper-app/src/test/java/dev/jasper/app/MainConfigurationTest.java
git commit -m "$(cat <<'EOF'
feat: resolve the launcher, resident and app roles at startup

A launch tries the handoff before any toolkit initialization and exits
if a resident process accepts. --background warms up and opens no
window; --background with the setting off removes the stale login item
and leaves. The login item is reconciled against the setting on every
start, and residency is only claimed when the endpoint was actually
bound.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Documentation and the manual acceptance list

**Files:**
- Modify: `docs/configuration.md`
- Modify: `docs/packaging.md`
- Modify: `docs/STATUS.md`

**Interfaces:**
- Consumes: the behaviour built in Tasks 1–7.
- Produces: nothing code depends on.

- [ ] **Step 1: Run the full check and record the real numbers**

```bash
./gradlew check --rerun-tasks
```

Then read the exact counts rather than trusting the console:

```bash
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
for module in ("jasper-app", "jasper-terminal"):
    t=f=e=s=0
    for path in glob.glob(f"{module}/build/test-results/test/*.xml"):
        r = ET.parse(path).getroot()
        t += int(r.get("tests", 0)); f += int(r.get("failures", 0))
        e += int(r.get("errors", 0)); s += int(r.get("skipped", 0))
    print(f"{module}: {t} tests, {f} failures, {e} errors, {s} skipped")
PY
```

Expected: 0 failures, 0 errors, 2 skips in total. Use these numbers verbatim in Step 4.

- [ ] **Step 2: Document the setting**

Add this section to `docs/configuration.md` under *Supported settings*, after the buddy section:

```markdown
### Background residency

```toml
[background]
enabled = false
```

With `enabled = true`, closing the last window no longer ends Jasper. The process stays with no
windows, holding the initialized toolkit, fonts and theme, so the next launch reveals a window
without a cold start. **Quit (Cmd+Q, the palette's Quit, the Dock's Quit) still exits completely** —
closing a window and quitting are different things, and quitting is the off switch.

Nothing of yours keeps running: a resident Jasper holds no shell, no PTY and no child process. Your
shells have already exited through the usual pane-close path before the last window goes.

Enabling it also registers Jasper to start in the background at login:

- **macOS** writes `~/Library/LaunchAgents/dev.jasper.background.plist`. It takes effect at your
  next login.
- **Windows** adds a `Jasper` value under
  `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`.

Both are removed as soon as you set `enabled = false` and save. Autostart needs an installed
Jasper: a `./gradlew run` development session stays resident but never registers a login item.

The two halves of the key have different timing. The login item is reconciled every time the
configuration is read, so turning it on or off applies at once. **Residency itself applies from the
next start** — whether a given process owns the handoff endpoint is settled when it starts, not
renegotiated while it runs.

Jasper keeps a socket, a token and a lock in a `daemon` directory beside `config.toml`, owner-only.
A second launch hands its request to the resident process over that socket and exits; on macOS a
Dock click never creates a process at all. A launch with `--config` never hands off, because the
resident process is holding a different configuration. After you upgrade, the first launch of the
new build retires the old resident process rather than being served by it.
```

- [ ] **Step 3: Add the acceptance items**

In `docs/packaging.md`, add to the **macOS** checklist:

```markdown
- [ ] With `[background] enabled = true`, confirm `~/Library/LaunchAgents/dev.jasper.background.plist` appears, log out and back in, and confirm Jasper is running with no windows.
- [ ] Confirm the Dock icon is present with no windows open and that clicking it produces a window noticeably faster than a cold start; record both times.
- [ ] Confirm Cmd+Q exits completely and that the next launch is cold.
- [ ] Record the resident process's idle memory (`ps -o rss= -p <pid>`).
- [ ] Confirm Jasper survives sleep/wake and a monitor change while resident.
- [ ] Set `enabled = false`, save, and confirm the plist is removed without restarting Jasper.
```

And to the **Windows** checklist:

```markdown
- [ ] Confirm `AF_UNIX` sockets work: with `[background] enabled = true`, confirm a second `Jasper.exe` launch reveals a window and the second process exits rather than opening its own window.
- [ ] Confirm the `Jasper` value appears under `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, sign out and back in, and confirm Jasper is resident.
- [ ] Confirm Quit exits completely and that setting `enabled = false` removes the Run value.
```

- [ ] **Step 4: Record the work in STATUS.md**

Add a new entry at the top of `docs/STATUS.md`, in the style of the existing ones: the branch and base commit, what shipped, the decisions, the deviations from the spec found during implementation, the exact test counts from Step 1, links to the [design spec](superpowers/specs/2026-09-17-jasper-background-daemon-design.md) and this [plan](superpowers/plans/2026-09-17-jasper-background-daemon.md), and an explicit **Still user-run** list naming every item from Step 3 plus the cold-versus-warm timing, since none of it can be verified headlessly and no GUI was launched.

- [ ] **Step 5: Verify the documentation matches the code**

```bash
./gradlew check
```

Expected: unchanged from Step 1. `ExpandedConfigTest` and `ConfigTemplateTest` parse the example and the template, so a setting documented with a name the loader does not accept fails here.

- [ ] **Step 6: Commit**

```bash
git add docs/configuration.md docs/packaging.md docs/STATUS.md
git commit -m "$(cat <<'EOF'
docs: document background residency

The setting, the two different timings of its halves, what a resident
process does and does not hold, and the per-platform login items. The
acceptance checklists gain the items that cannot be verified headlessly
-- login start on both platforms, AF_UNIX on Windows, and the
cold-versus-warm timing this feature is judged on.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

## After the last task

Run `superpowers:requesting-code-review` for a whole-branch review before merging, as the shell-integration and palette-scopes branches did. The review should pay particular attention to:

- the accept loop's failure handling, since it is the only non-daemon thread and a thrown error there would silently end residency;
- whether anything in `shutdown()` is now reachable twice, or unreachable, on the residency path;
- the stale-build handoff, which is the one path that deliberately closes the endpoint from inside a request.

**Nothing in this plan verifies the feature's actual purpose.** The cold-versus-warm launch times, the idle memory, login start on either platform, `AF_UNIX` on Windows and the macOS Dock behaviour are all user-run, and the branch should not be described as working until those numbers exist.
