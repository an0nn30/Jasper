# Plugin Settings C3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Implemented on claude/plugin-settings-c3; ./gradlew check green, 4,117 tests, 0 failures (5 skipped).
Record every deviation from this text here and in `docs/STATUS.md`.

Deviations so far:
- Task 5: `jasper-app`'s `RemoteShortcutConfigTest`, which the plan missed, drove Remote's `[shortcuts]` table
  through the staged jar and the app's key resolver. It now checks the default bindings, app overrides, menu
  placement, one toggle per panel in View > Tools, and that an old `[shortcuts]` table replaces nothing. Its "bad
  or conflicting shortcut" case is gone: an app override with a bad key or a collision is rejected when the
  configuration is read, and `KeyBindingsTest.builtInsBeatPluginDefaultsAndEarlierPluginsBeatLaterOnes` already covers
  plugin defaults that collide.
- Task 5: `BundledSamplePluginTest` expected the Sample at version 0.1.0; it now expects 0.2.0.
- Tasks 1–4 and 6: none.

Scope notes (decided while writing this plan):
- The plugins move to declarations first (Tasks 1–4), while `PluginConfig` still exists and the SDK is still 0.8.5,
  so `check` stays green after every task. Task 5 is the break: it removes `PluginConfig` and
  `PluginContext.config()`, makes the SDK 0.9.0 and moves every bundled plugin to `sdk = ">=0.9.0, <0.10"`.
- Values the plugins used to clamp (Remote's negative seconds, SFTP limits out of range) are now rejected by their
  kind and fall back to the default, with a report. This is the spec's "the app validates values; plugins drop their
  own checks".
- Remote's three navigation actions register once, with fixed default bindings; Keymap rebinds them. Old
  `[shortcuts]` lines in a user's file are reported as unknown keys, as the spec decides (no migration).
- The Editor's Vim toggle saves through `Setting.set`, which completes on any thread; the "could not be saved"
  question is shown on the UI thread when the save fails.
- A plugin that declares nothing now has its file read and its keys reported as unknown. C1 skipped such plugins
  while `PluginConfig` still read their files.
- Of the items C2 deferred to C3, this plan does the Sample `settings.toml` wording and contract cases for
  `PluginSettings.page`. These stay deferred, and are listed in `docs/STATUS.md`:
  - a pending-changed callback for plugin controls
  - de-duplicating warnings from a throwing `modified()` or `keywords()`, per page
  - the stub's Open in Editor failure going to a notice while Settings is modal
  - section-heading carry-over for settings without a section
  - the discard count per plugin page

**Goal:** Every bundled plugin reads its settings through SDK 0.9's declarations, and `PluginConfig` is gone.

**Architecture:** Each plugin declares its settings in `start()` through `context.settings()`. It keeps a small
holder of `Setting` handles and builds the values it uses from `get()`; the app validates and reports. Remote
registers its navigation actions once, with default bindings. The Editor's Vim toggle calls `Setting.set`. The SDK
then drops `PluginConfig`, `PluginContext.config()`, the testkit's `setConfig`/`reports` and the application's
`PluginSettings` view, and becomes 0.9.0.

**Tech Stack:** Java 25 (JetBrains Runtime), Swing, JUnit 5, AssertJ, Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-09-28-jasper-plugin-settings-design.md` (section 3 "Plugin rework", section 4
"Testkit", "Delivery" C3). The C1 and C2 plans (`docs/superpowers/plans/2026-09-28-jasper-plugin-settings-c1.md`,
`docs/superpowers/plans/2026-09-28-jasper-plugin-settings-c2.md`) built the API this plan uses.

## Global Constraints

- Build and test only with `./gradlew` from `/Users/dustin/projects/moray/.worktrees/plugin-settings-c3`. Never launch
  the GUI (`:jasper-app:run`, `bench`).
- Run `git branch --show-current` before every commit and confirm it prints `claude/plugin-settings-c3`.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Do not push or merge.
- SDK: JDK-only, with complete Javadoc under `-Xdoclint:all`. Write each Javadoc tag with its description on the same
  line, as the surrounding code does.
- `JasperSdk.VERSION` stays `0.8.5` until Task 5, which makes it `0.9.0`. From Task 5, every bundled plugin declares
  `sdk = ">=0.9.0, <0.10"`.
- Plugins compile against the SDK only (`verifyPluginArchitecture`), and they drop their own value checks and
  `report` calls. SDK types appear in the application only inside `dev.jasper.app.plugins`
  (`verifyApplicationArchitecture`, `verifySdkArchitecture`).
- The testkit reports a plugin's settings problems as `key: message` through
  `FakePluginHost.settingsReports(pluginId)`. The messages are the SDK's, for example
  `"Use a whole number from 8–48."`, where the dash is U+2013. A value of the wrong type reports
  `key: Expected …; the last good value stays in force.`. An undeclared key reports
  `key: Unknown setting or table; ignored.`.
- `FakePluginHost.setSetting(pluginId, dottedKey, rawValue)` takes TOML's raw types: `Long` for integers,
  `Boolean`, `String`, and `List` of `String`.
- Source hygiene per AGENTS.md: no raw non-ASCII in Java source. Write `–` for the en dash and `…` for the
  ellipsis.
- `AppDocumentationTest` checks the Markdown links in `docs/` (plans included), and checks that the guide's
  `example:plugin` block equals the Sample source between its `example:plugin` markers.
- Snippets declares nothing and gets no page.

---

## File Structure

- `plugins/history/src/main/java/dev/jasper/history/HistorySettings.java`: new. History's two declared settings and
  the trivial-command set.
- `plugins/history/src/main/java/dev/jasper/history/HistoryPlugin.java`: declares through `HistorySettings`.
- `plugins/vault/src/main/java/dev/jasper/vault/VaultSettings.java`: the value record plus a nested `Declared`
  holder.
- `plugins/vault/src/main/java/dev/jasper/vault/VaultPlugin.java`: reads through `VaultSettings.Declared`.
- `plugins/remote/src/main/java/dev/jasper/remote/RemoteSettings.java`: the value records plus a nested `Declared`
  holder with sections Connection and SFTP.
- `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`: registers its navigation actions once and
  reads settings through handles.
- `plugins/remote/src/main/java/dev/jasper/remote/RemoteShortcuts.java`: deleted.
- `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettings.java`: the value record plus a nested
  `Declared` holder with `current()` and `saveVim(boolean)`.
- `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorEnvironment.java`: gains `saveVim`.
- `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`: `toggleVim` saves through `saveVim`.
- `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java`: declares through `EditorSettings.Declared`.
- `plugins/editor/src/main/java/dev/jasper/editor/ui/SettingsFile.java` and its test: deleted.
- SDK: `PluginConfig.java` deleted; `PluginContext.config()` removed; `JasperSdk.VERSION` becomes `0.9.0`.
- Testkit: `FakePluginConfig.java` deleted; `FakePluginContext.config()`, and `FakePluginHost.setConfig` and
  `reports()` removed; `PluginContractTest` gains a `page()` case.
- Application: `dev.jasper.app.plugins.PluginSettings` deleted; `HostedContext`, `PluginHost.Environment`,
  `PluginRuntime`, `PluginSettingsFiles`, `DeclaredSettings` and `TomlText` updated.
- Every bundled plugin's `plugin.toml`: new version and SDK range. Every `settings.toml`: says when changes apply.
- Docs: `plugin-authoring.md`, `sdk-architecture.md`, `jasper-sdk/README.md`, `configuration.md`, `remote.md`,
  `remote-7c-verification.md`, `STATUS.md`, and this banner.

---

### Task 1: Shell History on declarations

**Files:**
- Create: `plugins/history/src/main/java/dev/jasper/history/HistorySettings.java`
- Modify: `plugins/history/src/main/java/dev/jasper/history/HistoryPlugin.java`
- Modify: `plugins/history/src/main/resources/settings.toml`
- Test: `plugins/history/src/test/java/dev/jasper/history/HistoryScopeTest.java`

**Interfaces:**
- Consumes: SDK `PluginSettings.declare`, `SettingSpec`, `SettingKind.stringList(Predicate)`, `SettingKind.bool()`,
  and the testkit's `setSetting`, `settingsReports` and `declared`.
- Produces: package-private `final class HistorySettings` with `HistorySettings(PluginSettings)` and
  `Set<String> trivial()`.

- [ ] **Step 1: Write the failing tests**

In `HistoryScopeTest.java`, replace the test
`trivialCommandsSortBelowRealWorkUnlessTheSettingIsOffAndAConfiguredListReplacesTheBuiltInOne` with:

```java
    @Test void trivialCommandsSortBelowRealWorkUnlessTheSettingIsOffAndAConfiguredListReplacesTheBuiltInOne() {
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), new HistoryPlugin(context -> fourCommands()));
            UUID window = host.addTerminalWindow();
            assertThat(host.searchScope(SCOPE, "", window, null)).extracting(line -> line.split("\\|")[1]).as("defaults").containsExactly("make", "clear", "ls", "cd /x");
            assertThat(host.declared("dev.jasper.history")).containsExactly("trivial_commands", "deprioritize_trivial");
        }
        try (var host = new FakePluginHost()) {
            host.setSetting("dev.jasper.history", "deprioritize_trivial", false);
            host.start(INFO, Set.of(), Set.of(), new HistoryPlugin(context -> fourCommands()));
            UUID window = host.addTerminalWindow();
            assertThat(host.searchScope(SCOPE, "", window, null)).extracting(line -> line.split("\\|")[1]).as("off").containsExactly("clear", "ls", "make", "cd /x");
        }
        try (var host = new FakePluginHost()) {
            host.setSetting("dev.jasper.history", "trivial_commands", List.of("make"));
            host.start(INFO, Set.of(), Set.of(), new HistoryPlugin(context -> fourCommands()));
            UUID window = host.addTerminalWindow();
            assertThat(host.searchScope(SCOPE, "", window, null)).extracting(line -> line.split("\\|")[1]).as("replaced").containsExactly("clear", "ls", "cd /x", "make");
            assertThat(host.searchScope(SCOPE, "make", window, null)).as("still found when searched for").hasSize(1);
        }
    }

    @Test void anEmptyListMeansNoTrivialCommandsAndAWordWithSpacesIsRejected() {
        try (var host = new FakePluginHost()) {
            host.setSetting("dev.jasper.history", "trivial_commands", List.of());
            host.start(INFO, Set.of(), Set.of(), new HistoryPlugin(context -> fourCommands()));
            UUID window = host.addTerminalWindow();
            assertThat(host.searchScope(SCOPE, "", window, null)).extracting(line -> line.split("\\|")[1]).as("empty list").containsExactly("clear", "ls", "make", "cd /x");
        }
        try (var host = new FakePluginHost()) {
            host.setSetting("dev.jasper.history", "trivial_commands", List.of("git status"));
            host.start(INFO, Set.of(), Set.of(), new HistoryPlugin(context -> fourCommands()));
            UUID window = host.addTerminalWindow();
            assertThat(host.searchScope(SCOPE, "", window, null)).extracting(line -> line.split("\\|")[1]).as("the default list").containsExactly("make", "clear", "ls", "cd /x");
            assertThat(host.settingsReports("dev.jasper.history")).singleElement().asString().startsWith("trivial_commands: ");
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-history:test --tests 'dev.jasper.history.HistoryScopeTest'`
Expected: FAIL. `declared("dev.jasper.history")` is empty and `setSetting` values are ignored, because the plugin
still reads `context.config()`.

- [ ] **Step 3: Declare the settings**

Create `HistorySettings.java`:

```java
package dev.jasper.history;

import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shell History's declared settings. They apply at once and are safe to read from any thread. */
final class HistorySettings {
    private final Setting<List<String>> trivialCommands;
    private final Setting<Boolean> deprioritizeTrivial;

    HistorySettings(PluginSettings settings) {
        trivialCommands = settings.declare(SettingSpec.of("trivial_commands",
                SettingKind.stringList(word -> word.matches("\\S+")), HistoryPlugin.DEFAULT_TRIVIAL)
            .label("Trivial commands")
            .description("Single words compared with a command's first word; a command of at most two words that starts with one ranks below other commands on an empty History query.")
            .keywords("history", "rank"));
        deprioritizeTrivial = settings.declare(SettingSpec.of("deprioritize_trivial", SettingKind.bool(), true)
            .label("Rank trivial commands last")
            .description("Off: an empty History query lists commands by recency alone.")
            .keywords("history", "rank"));
    }

    /** The lower-cased trivial commands; none when ranking them last is off or the list is empty. */
    Set<String> trivial() {
        if (!deprioritizeTrivial.get()) return Set.of();
        var set = new HashSet<String>();
        for (String command : trivialCommands.get()) set.add(command.toLowerCase(Locale.ROOT));
        return Set.copyOf(set);
    }
}
```

In `HistoryPlugin.java`:
- Replace the line
  `        context.palette().register(new HistoryScope(index, snippets, () -> trivial(context), OPEN));`
  with these two lines:

```java
        HistorySettings settings = new HistorySettings(context.settings());
        context.palette().register(new HistoryScope(index, snippets, settings::trivial, OPEN));
```

- Delete the whole `static Set<String> trivial(PluginContext context)` method and its Javadoc line.
- Delete the now-unused imports `java.util.HashSet` and `java.util.Locale`.

- [ ] **Step 4: Say when changes apply**

Replace the whole of `plugins/history/src/main/resources/settings.toml` with:

```toml
# Settings for Shell History. Change them in Settings > Tools > Shell History or here; changes apply at once.
# On an empty History query these commands rank below more substantial ones. Each entry is a single
# word compared with a command's first word; a command of at most two words counts. Setting the key
# replaces the default list, and an empty list means no trivial commands; deprioritize_trivial = false
# turns the rule off.
# trivial_commands = ["exit", "clear", "ls", "ll", "la", "cd", "pwd", "c", "q", "logout"]
# deprioritize_trivial = true
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-history:test verifyPluginArchitecture`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add plugins/history
git commit -m "Declare Shell History's settings

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Credential Vault on declarations

**Files:**
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/VaultSettings.java`
- Modify: `plugins/vault/src/main/java/dev/jasper/vault/VaultPlugin.java`
- Modify: `plugins/vault/src/main/resources/settings.toml`
- Test: `plugins/vault/src/test/java/dev/jasper/vault/VaultPluginTest.java`

**Interfaces:**
- Consumes: SDK `SettingKind.integer`, `SettingKind.text()`, `SettingKind.bool()` and `EditorHint.directory()`.
- Produces:
  - `VaultSettings.Declared`, a package-private nested class with `Declared(PluginSettings)` and
    `VaultSettings read(Path dataDirectory)`.
  - `VaultPlugin.settings()`, package-private, returning `VaultSettings`.

- [ ] **Step 1: Write the failing tests**

In `VaultPluginTest.java`, in `autoLockFollowsTheSettingsAndStopLocks`, replace
`host.setConfig("dev.jasper.vault", Map.of("auto_lock_minutes", 1L));` with
`host.setSetting("dev.jasper.vault", "auto_lock_minutes", 1L);` and
`host.setConfig("dev.jasper.vault", Map.of("auto_lock_minutes", 0L));` with
`host.setSetting("dev.jasper.vault", "auto_lock_minutes", 0L);`. Replace the test
`settingsResolveTheKeysDirectoryAndDefaults` with:

```java
    @Test void settingsResolveTheKeysDirectoryAndDefaults() {
        try (var host = new FakePluginHost()) {
            host.setSetting("dev.jasper.vault", "keys_directory", "/elsewhere/keys");
            host.setSetting("dev.jasper.vault", "bind_new_vaults_to_device", false);
            VaultPlugin plugin = plugin();
            host.start(INFO, Set.of(), Set.of(), plugin);
            assertThat(plugin.settings().keysDirectory()).isEqualTo(java.nio.file.Path.of("/elsewhere/keys"));
            assertThat(plugin.settings().bindByDefault()).isFalse();
            assertThat(plugin.settings().autoLock()).isEqualTo(Duration.ofMinutes(15));
            assertThat(host.declared("dev.jasper.vault")).containsExactly("auto_lock_minutes", "keys_directory", "bind_new_vaults_to_device");
        }
        try (var host = new FakePluginHost()) {
            host.setSetting("dev.jasper.vault", "auto_lock_minutes", -5L);
            VaultPlugin plugin = plugin();
            var context = host.start(INFO, Set.of(), Set.of(), plugin);
            assertThat(plugin.settings().keysDirectory()).isEqualTo(context.dataDirectory().resolve("keys"));
            assertThat(plugin.settings().autoLock()).as("out of range: the default").isEqualTo(Duration.ofMinutes(15));
            assertThat(host.settingsReports("dev.jasper.vault")).singleElement().asString().startsWith("auto_lock_minutes: ");
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-vault:test --tests 'dev.jasper.vault.VaultPluginTest'`
Expected: compilation FAILS: `VaultPlugin.settings()` does not exist.

- [ ] **Step 3: Declare the settings**

Replace the whole of `VaultSettings.java` with:

```java
package dev.jasper.vault;

import dev.jasper.sdk.settings.EditorHint;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.nio.file.Path;
import java.time.Duration;

/**
 * The vault's settings as the plugin uses them.
 *
 * @param autoLock idle time before the vault locks; zero never locks
 * @param keysDirectory where generated SSH keys go
 * @param bindByDefault the initial state of "Bind to this device"
 */
record VaultSettings(Duration autoLock, Path keysDirectory, boolean bindByDefault) {
    /** The three declared settings of {@code dev.jasper.vault.toml}. */
    static final class Declared {
        private final Setting<Integer> autoLockMinutes;
        private final Setting<String> keysDirectory;
        private final Setting<Boolean> bindNewVaults;

        Declared(PluginSettings settings) {
            autoLockMinutes = settings.declare(SettingSpec.of("auto_lock_minutes", SettingKind.integer(0, 1440), 15)
                .label("Lock after idle minutes")
                .description("Lock after this many minutes without keyboard or mouse activity; 0 never locks automatically.")
                .keywords("vault", "lock", "timeout"));
            keysDirectory = settings.declare(SettingSpec.of("keys_directory", SettingKind.text(), "")
                .label("Generated keys folder")
                .description("Where generated SSH keys go; empty means the keys folder in this plugin's data folder.")
                .editor(EditorHint.directory()).keywords("ssh", "keys"));
            bindNewVaults = settings.declare(SettingSpec.of("bind_new_vaults_to_device", SettingKind.bool(), true)
                .label("Bind new vaults to this device")
                .description("The initial state of \"Bind to this device\" when you create a vault."));
        }

        /** The current values; an empty keys folder resolves under {@code dataDirectory}. */
        VaultSettings read(Path dataDirectory) {
            String keys = keysDirectory.get().strip();
            return new VaultSettings(Duration.ofMinutes(autoLockMinutes.get()),
                keys.isEmpty() ? dataDirectory.resolve("keys") : Path.of(keys), bindNewVaults.get());
        }
    }
}
```

In `VaultPlugin.java`:
- After the field `    private VaultSettings settings;`, add `    private VaultSettings.Declared declared;`.
- Replace `        settings = VaultSettings.read(context.config(), context.dataDirectory());` with these two lines:

```java
        declared = new VaultSettings.Declared(context.settings());
        settings = declared.read(context.dataDirectory());
```

- Replace
  `        context.config().onChanged(() -> { settings = VaultSettings.read(context.config(), context.dataDirectory()); timer.setTimeout(settings.autoLock()); refreshStatus(); vaultChanged(); });`
  with:

```java
        context.settings().onChanged(() -> { settings = declared.read(context.dataDirectory()); timer.setTimeout(settings.autoLock()); refreshStatus(); vaultChanged(); });
```

- After `    InactivityTimer timer() { return timer; }`, add `    VaultSettings settings() { return settings; }`.

- [ ] **Step 4: Say when changes apply**

Replace the first line of `plugins/vault/src/main/resources/settings.toml` with:

```toml
# Settings for Credential Vault. Change them in Settings > Tools > Credential Vault or here; changes apply at once.
```

Replace the line with `auto_lock_minutes` with:

```toml
# auto_lock_minutes = 15            # 0 to 1440 minutes without keyboard or mouse activity; 0 never locks automatically
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-vault:test verifyPluginArchitecture`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add plugins/vault
git commit -m "Declare Credential Vault's settings

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Remote on declarations, shortcuts to the Keymap

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemoteSettings.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`
- Delete: `plugins/remote/src/main/java/dev/jasper/remote/RemoteShortcuts.java`
- Modify: `plugins/remote/src/main/resources/settings.toml`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/RemoteSettingsTest.java`, `SftpPluginTest.java`,
  `RemotePluginTest.java`

**Interfaces:**
- Consumes: SDK `SettingKind.integer`, `SettingKind.bool()`, `SettingSpec.section`, `ActionSpec.withDefaultBinding`.
- Produces:
  - `RemoteSettings.Declared`, a package-private nested class with `Declared(PluginSettings)`,
    `RemoteSettings connection()` and `RemoteSettings.Sftp sftp()`.
  - `RemotePlugin.sftpSettings()`, package-private.
  - The public records `RemoteSettings` and `RemoteSettings.Sftp` keep their components.

- [ ] **Step 1: Write the failing tests**

Replace the whole of `RemoteSettingsTest.java` with:

```java
package dev.jasper.remote;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RemoteSettingsTest {
    static final PluginInfo INFO = new PluginInfo("dev.jasper.remote", "Remote", "0.1.0", Set.of());

    private static RemoteSettings.Declared start(FakePluginHost host) {
        var declared = new AtomicReference<RemoteSettings.Declared>();
        host.start(INFO, Set.of(), Set.of(), context -> declared.set(new RemoteSettings.Declared(context.settings())));
        return declared.get();
    }

    @Test void defaultsAndOverrides() {
        try (var host = new FakePluginHost()) {
            var declared = start(host);
            assertThat(declared.connection()).isEqualTo(new RemoteSettings(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofSeconds(5), true, true));
            assertThat(declared.sftp()).isEqualTo(new RemoteSettings.Sftp(2, Duration.ofSeconds(30)));
            assertThat(host.declared("dev.jasper.remote")).containsExactly("connect_timeout_seconds", "auth_timeout_seconds",
                "keepalive_seconds", "session_linger_seconds", "read_user_known_hosts", "use_ssh_agent",
                "sftp.max_parallel_files", "sftp.request_timeout_seconds");
        }
        try (var host = new FakePluginHost()) {
            host.setSetting("dev.jasper.remote", "connect_timeout_seconds", 3L);
            host.setSetting("dev.jasper.remote", "keepalive_seconds", 0L);
            host.setSetting("dev.jasper.remote", "session_linger_seconds", -1L);
            host.setSetting("dev.jasper.remote", "read_user_known_hosts", false);
            host.setSetting("dev.jasper.remote", "use_ssh_agent", false);
            host.setSetting("dev.jasper.remote", "sftp.max_parallel_files", 99L);
            host.setSetting("dev.jasper.remote", "sftp.request_timeout_seconds", 0L);
            var declared = start(host);
            RemoteSettings settings = declared.connection();
            assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(settings.keepalive()).isEqualTo(Duration.ZERO);
            assertThat(settings.linger()).as("out of range: the default").isEqualTo(Duration.ofSeconds(5));
            assertThat(settings.readUserKnownHosts()).isFalse();
            assertThat(settings.useAgent()).isFalse();
            assertThat(declared.sftp()).as("out of range: the defaults").isEqualTo(new RemoteSettings.Sftp(2, Duration.ofSeconds(30)));
            assertThat(host.settingsReports("dev.jasper.remote")).extracting(line -> line.substring(0, line.indexOf(':')))
                .containsExactly("session_linger_seconds", "sftp.max_parallel_files", "sftp.request_timeout_seconds");
        }
    }
}
```

In `SftpPluginTest.java`, replace the two lines

```java
            host.setConfig("dev.jasper.remote",Map.of("sftp",Map.of("max_parallel_files",99L,"request_timeout_seconds",0L),"shortcuts",Map.of("toggle_sftp","cmd+alt+f","toggle_transfers","cmd+alt+t")));
            assertThat(RemoteSettings.sftp(context.config()).maxParallelFiles()).isEqualTo(8);assertThat(RemoteSettings.sftp(context.config()).requestTimeout().toSeconds()).isEqualTo(1);
```

with:

```java
            host.setSetting("dev.jasper.remote","sftp.max_parallel_files",8L);host.setSetting("dev.jasper.remote","sftp.request_timeout_seconds",1L);
            assertThat(plugin.sftpSettings().maxParallelFiles()).isEqualTo(8);assertThat(plugin.sftpSettings().requestTimeout().toSeconds()).isEqualTo(1);
```

In `RemotePluginTest.java`, add this test after `hostRequestsNetworkAndServerIcons`:

```java
    @Test void navigationActionsKeepTheirDefaultBindingsAndAnOldShortcutsTableIsUnknown(@TempDir Path dir) {
        try (var host = new FakePluginHost()) {
            host.setSetting("dev.jasper.remote", "shortcuts.toggle_panel", "cmd+alt+p");
            var delegate = plugin(dir.resolve("ssh"));
            var bindings = new java.util.LinkedHashMap<String, Optional<String>>();
            host.start(INFO, Set.of(), Set.of(), new dev.jasper.sdk.plugin.Plugin() {
                public void start(dev.jasper.sdk.plugin.PluginContext context) throws Exception {
                    var actions = (dev.jasper.sdk.ui.Actions) java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(), new Class<?>[]{dev.jasper.sdk.ui.Actions.class}, (proxy, method, args) -> {
                            if (method.getName().equals("register") && args != null && args[0] instanceof dev.jasper.sdk.ui.ActionSpec spec)
                                bindings.put(spec.id(), spec.defaultBinding());
                            try { return method.invoke(context.actions(), args); }
                            catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                        });
                    var wrapped = (dev.jasper.sdk.plugin.PluginContext) java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(), new Class<?>[]{dev.jasper.sdk.plugin.PluginContext.class}, (proxy, method, args) -> {
                            if (method.getName().equals("actions")) return actions;
                            try { return method.invoke(context, args); }
                            catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                        });
                    delegate.start(wrapped);
                }
                public void stop() { delegate.stop(); }
            });
            settle(host);
            assertThat(host.failures()).isEmpty();
            assertThat(bindings).containsEntry(RemotePlugin.HOSTS, Optional.of("cmd+shift+s"))
                .containsEntry(RemotePlugin.CONNECT, Optional.of("cmd+shift+h")).containsEntry(RemotePlugin.SFTP_TOGGLE, Optional.empty());
            assertThat(host.settingsReports("dev.jasper.remote")).containsExactly("shortcuts.toggle_panel: Unknown setting or table; ignored.");
        }
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.RemoteSettingsTest' --tests 'dev.jasper.remote.SftpPluginTest' --tests 'dev.jasper.remote.RemotePluginTest'`
Expected: compilation FAILS: `RemoteSettings.Declared` and `RemotePlugin.sftpSettings()` do not exist.

- [ ] **Step 3: Declare the settings**

Replace the whole of `RemoteSettings.java` with:

```java
package dev.jasper.remote;

import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.time.Duration;

/**
 * Remote's connection settings as the client uses them.
 *
 * @param connectTimeout how long to wait for the server
 * @param authTimeout how long authentication may take
 * @param keepalive the server-alive interval; zero disables it
 * @param linger how long an idle shared session stays open after its last channel
 * @param readUserKnownHosts whether {@code ~/.ssh/known_hosts} is also read
 * @param useAgent whether the SSH agent offers identities
 */
public record RemoteSettings(Duration connectTimeout, Duration authTimeout, Duration keepalive, Duration linger, boolean readUserKnownHosts, boolean useAgent) {
    /**
     * SFTP limits.
     *
     * @param maxParallelFiles files copied at once
     * @param requestTimeout how long each SFTP request may take
     */
    public record Sftp(int maxParallelFiles, Duration requestTimeout) { }

    /** Remote's declared settings; values are safe to read from any thread. */
    static final class Declared {
        private final Setting<Integer> connectTimeout, authTimeout, keepalive, linger, maxParallelFiles, requestTimeout;
        private final Setting<Boolean> readUserKnownHosts, useAgent;

        Declared(PluginSettings settings) {
            connectTimeout = seconds(settings, "connect_timeout_seconds", 10, "Connect timeout", "Seconds to wait for the server to answer.");
            authTimeout = seconds(settings, "auth_timeout_seconds", 30, "Authentication timeout", "Seconds authentication may take.");
            keepalive = seconds(settings, "keepalive_seconds", 30, "Keepalive interval", "Seconds between server-alive messages; 0 turns them off.");
            linger = seconds(settings, "session_linger_seconds", 5, "Session linger", "Seconds an idle shared session stays open after its last channel closes.");
            readUserKnownHosts = settings.declare(SettingSpec.of("read_user_known_hosts", SettingKind.bool(), true)
                .label("Read ~/.ssh/known_hosts").description("Also match host keys against your OpenSSH known_hosts file; Jasper never writes it.")
                .section("Connection").keywords("ssh", "host keys"));
            useAgent = settings.declare(SettingSpec.of("use_ssh_agent", SettingKind.bool(), true)
                .label("Use the SSH agent").description("Offer identities from SSH_AUTH_SOCK or the OpenSSH named pipe.")
                .section("Connection").keywords("ssh", "agent"));
            maxParallelFiles = settings.declare(SettingSpec.of("sftp.max_parallel_files", SettingKind.integer(1, 8), 2)
                .label("Parallel files").description("Files copied at once; at most two per endpoint.")
                .section("SFTP").keywords("sftp", "transfer"));
            requestTimeout = settings.declare(SettingSpec.of("sftp.request_timeout_seconds", SettingKind.integer(1, 300), 30)
                .label("Request timeout").description("Seconds each SFTP request may take.")
                .section("SFTP").keywords("sftp", "timeout"));
        }

        /** The current connection settings; they apply to the next connection. */
        RemoteSettings connection() {
            return new RemoteSettings(Duration.ofSeconds(connectTimeout.get()), Duration.ofSeconds(authTimeout.get()),
                Duration.ofSeconds(keepalive.get()), Duration.ofSeconds(linger.get()), readUserKnownHosts.get(), useAgent.get());
        }

        /** The current SFTP limits. */
        Sftp sftp() { return new Sftp(maxParallelFiles.get(), Duration.ofSeconds(requestTimeout.get())); }

        private static Setting<Integer> seconds(PluginSettings settings, String key, int fallback, String label, String description) {
            return settings.declare(SettingSpec.of(key, SettingKind.integer(0, 3600), fallback).label(label)
                .description(description + " Applies to the next connection.").section("Connection").keywords("ssh", "timeout"));
        }
    }
}
```

Delete `plugins/remote/src/main/java/dev/jasper/remote/RemoteShortcuts.java`.

In `RemotePlugin.java`, make these replacements. Each old text appears exactly once.

1. Delete the line `    private volatile RemoteSettings.Sftp sftpSettings;`.
2. Delete the line `    private PluginAction sftpToggle;`.
3. Replace `    private RemoteSettings settings;` with `    private RemoteSettings.Declared settings;`.
4. Delete the two lines `    private PluginAction connectAction, hostsAction;` and `    private RemoteShortcuts shortcuts;`.
5. Replace `        settings = RemoteSettings.read(context.config());` (the first one, in `start`) with
   `        settings = new RemoteSettings.Declared(context.settings());`.
6. Replace `() -> settings.readUserKnownHosts() ?` with `() -> settings.connection().readUserKnownHosts() ?`.
7. Replace `connections = new Connections(() -> settings, trust,` with `connections = new Connections(settings::connection, trust,`.
8. Replace these four lines

```java
        // Before the panels: each names its toggle action, which must already be registered.
        configureShortcuts();

        sftpSettings=RemoteSettings.sftp(context.config());
```

   with:

```java
        // Before the panels: each names its toggle action, which must already be registered.
        registerNavigation();

```

9. Replace `()->sftpSettings.requestTimeout()` with `()->settings.sftp().requestTimeout()`, and
   `()->sftpSettings.maxParallelFiles()` with `()->settings.sftp().maxParallelFiles()`.
10. Delete these five lines at the end of `start`:

```java
        context.config().onChanged(() -> {
            if (stopped) return;
            settings = RemoteSettings.read(context.config());sftpSettings=RemoteSettings.sftp(context.config());
            configureShortcuts();
        });
```

11. Replace the whole `private void configureShortcuts() { ... }` method, from its first line through the closing
    brace after `shortcuts = next;`, with:

```java
    /** Remote's navigation actions with their default bindings; Keymap rebinds them. */
    private void registerNavigation() {
        context.actions().register(ActionSpec.of(CONNECT, "Connect to SSH Host...").withIcon(icon)
                .withKeywords(List.of("ssh", "remote", "host", "connect")).withDefaultBinding("cmd+shift+h"),
            invoked -> context.palette().open(invoked.window(), RemoteScope.ID, Optional.empty(), Optional.empty()));
        context.actions().register(ActionSpec.of(HOSTS, "Toggle SSH hosts").withIcon(icon)
                .withKeywords(List.of("ssh", "hosts", "panel", "show", "hide")).withDefaultBinding("cmd+shift+s"),
            invoked -> showPanel(invoked.window()));
        context.actions().register(ActionSpec.of(SFTP_TOGGLE, "Toggle SFTP")
                .withIcon(context.appearance().icon(dev.jasper.sdk.ui.IconName.FOLDER)).withKeywords(List.of("sftp", "files", "panel", "show", "hide")),
            invoked -> sftpUi.toggle(invoked.window()));
    }

    RemoteSettings.Sftp sftpSettings() { return settings.sftp(); }
```

- [ ] **Step 4: The example file**

Replace the whole of `plugins/remote/src/main/resources/settings.toml` with:

```toml
# Settings for Remote. Change them in Settings > Tools > Remote or here. Connection settings apply to the next
# connection; SFTP limits apply at once. Rebind Remote's shortcuts in Settings > Keymap.
# connect_timeout_seconds = 10      # 0 to 3600
# auth_timeout_seconds = 30         # 0 to 3600
# keepalive_seconds = 30            # server-alive interval, 0 to 3600; 0 disables
# session_linger_seconds = 5        # how long an idle shared session stays open after its last channel, 0 to 3600
# read_user_known_hosts = true      # also match host keys against ~/.ssh/known_hosts (never written)
# use_ssh_agent = true              # offer identities from SSH_AUTH_SOCK / the OpenSSH named pipe

# [sftp]
# max_parallel_files = 2            # 1 to 8 at once; at most two files per endpoint
# request_timeout_seconds = 30      # 1 to 300 seconds per SFTP request
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-remote:test verifyPluginArchitecture`
Expected: PASS. If `RemotePluginTest.endingAPanesSessionClearsTheSftpView` fails, re-run that test alone. It has
failed intermittently before this plan.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add -A plugins/remote
git commit -m "Declare Remote's settings and leave its shortcuts to the Keymap

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Editor on declarations, Vim saved through its setting

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorSettings.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorEnvironment.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java`
- Delete: `plugins/editor/src/main/java/dev/jasper/editor/ui/SettingsFile.java`,
  `plugins/editor/src/test/java/dev/jasper/editor/ui/SettingsFileTest.java`
- Modify: `plugins/editor/src/main/resources/settings.toml`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorSettingsTest.java`, `EditorWindowTest.java`,
  `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java`

**Interfaces:**
- Consumes: SDK `SettingKind.text(Predicate, String)`, `EditorHint.font(true)`, `Setting.set`.
- Produces:
  - `EditorSettings.Declared`, a public nested class with:
    - `Declared(PluginSettings settings, Set<String> installed)`
    - `EditorSettings current()`
    - `CompletableFuture<Void> saveVim(boolean on)`
  - `EditorEnvironment`, which gains the component `Function<Boolean, CompletableFuture<Void>> saveVim` after
    `settings`.
  - `EditorEnvironment.production(PluginContext, Executor, EditorSettings.Declared)`.
  - `EditorPlugin`'s package-private constructor, which takes
    `BiFunction<PluginContext, EditorSettings.Declared, EditorEnvironment>`.

- [ ] **Step 1: Write the failing tests**

Replace the whole of `EditorSettingsTest.java` with:

```java
package dev.jasper.editor.ui;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.awt.Font;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class EditorSettingsTest {
    @TempDir Path home;
    private static final String ID = "dev.jasper.editor";
    private static final PluginInfo INFO = new PluginInfo(ID, "Editor", "0.1.0", Set.of());

    private static EditorSettings.Declared start(FakePluginHost host, Set<String> installed) {
        var declared = new AtomicReference<EditorSettings.Declared>();
        host.start(INFO, Set.of(), Set.of(), context -> declared.set(new EditorSettings.Declared(context.settings(), installed)));
        return declared.get();
    }

    @Test void defaultsPickTheFirstInstalledPreferredFont() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            assertThat(start(host, Set.of("Menlo", "JetBrains Mono")).current())
                .isEqualTo(new EditorSettings("JetBrains Mono", 13, 4, false, 10, false));
            assertThat(host.declared(ID)).containsExactly("font_family", "font_size", "tab_size", "soft_wrap", "vim", "max_file_mib");
            assertThat(host.settingsReports(ID)).isEmpty();
        }
        try (var host = new FakePluginHost(home.resolve("other"))) {
            assertThat(start(host, Set.of()).current().fontFamily()).isEqualTo("Monospaced");
        }
    }

    @Test void badValuesAreReportedAndTheirDefaultsUsed() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.setSetting(ID, "font_family", "Nope Mono");
            host.setSetting(ID, "font_size", 99L);
            host.setSetting(ID, "tab_size", "four");
            host.setSetting(ID, "soft_wrap", true);
            host.setSetting(ID, "max_file_mib", 20L);
            host.setSetting(ID, "vim", "yes");
            var settings = start(host, Set.of("Menlo", "Consolas")).current();
            assertThat(settings).isEqualTo(new EditorSettings("Menlo", 13, 4, true, 20, false));
            assertThat(settings.maxFileBytes()).isEqualTo(20L * 1024 * 1024);
            assertThat(settings.font()).isEqualTo(new Font("Menlo", Font.PLAIN, 13));
            assertThat(host.settingsReports(ID)).extracting(line -> line.substring(0, line.indexOf(':')))
                .containsExactly("font_family", "font_size", "tab_size", "vim");
        }
    }

    @Test void savingVimModeWritesTheFileAndTheValueFollows() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            var declared = start(host, Set.of());
            declared.saveVim(true).join();
            assertThat(host.savedSettings(ID)).containsEntry("vim", true);
            assertThat(declared.current().vim()).isTrue();
        }
    }
}
```

In `EditorPluginTest.java`, replace the `plugin()` method and the test `badSettingsAreReportedAtStartAndOnEveryChange`
with:

```java
    private static EditorPlugin plugin() {
        return new EditorPlugin(Runnable::run, (context, settings) -> new EditorEnvironment(context, Runnable::run,
            owner -> Question::dismissed, owner -> (message, saves) -> saves.getNow(false), settings::current, settings::saveVim, false));
    }
```

```java
    @Test void badSettingsAreReportedAtStartAndOnEveryChange() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.setSetting("dev.jasper.editor", "font_size", 99L);
            host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(host.settingsReports("dev.jasper.editor")).containsExactly("font_size: Use a whole number from 8–48.");
            host.setSetting("dev.jasper.editor", "tab_size", 0L);
            assertThat(host.settingsReports("dev.jasper.editor")).contains("tab_size: Use a whole number from 1–16.");
        }
    }
```

In `EditorWindowTest.java`:
- After the field `private boolean cancelWait;`, add:

```java
    private final List<Boolean> savedVim = new ArrayList<>();
    private java.util.concurrent.CompletableFuture<Void> vimSave = java.util.concurrent.CompletableFuture.completedFuture(null);
```

- In `open()`, replace the environment's last line `            settings::get, false);` with
  `            settings::get, on -> { savedVim.add(on); return vimSave; }, false);`.
- In `toggleVimModeSwitchesEveryTabAndWritesTheSetting`, replace
  `assertThat(Files.readString(context.config().file())).contains("vim = true");` with
  `assertThat(savedVim).containsExactly(true);`, and
  `assertThat(Files.readString(context.config().file())).contains("vim = false");` with
  `assertThat(savedVim).containsExactly(true, false);`.
- Add this test after `toggleVimModeSwitchesEveryTabAndWritesTheSetting`:

```java
    @Test void aVimModeThatCannotBeSavedLastsForTheSessionAndSaysSo() throws Exception {
        opened(file("one.txt", "one\n"));
        vimSave = java.util.concurrent.CompletableFuture.failedFuture(new java.io.IOException("disk full"));
        window.toggleVim();
        assertThat(window.vimOn()).isTrue();
        assertThat(asked).extracting(Question::message)
            .contains("Vim mode is on until the Editor closes: the setting could not be saved (disk full).");
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.EditorSettingsTest' --tests 'dev.jasper.editor.ui.EditorWindowTest' --tests 'dev.jasper.editor.EditorPluginTest'`
Expected: compilation FAILS: `EditorSettings.Declared` does not exist and `EditorEnvironment` takes six arguments.

- [ ] **Step 3: Declare the settings**

Replace the whole of `EditorSettings.java` with:

```java
package dev.jasper.editor.ui;

import dev.jasper.sdk.settings.EditorHint;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.awt.Font;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The Editor's settings as its window uses them (spec 7).
 *
 * @param fontFamily the editor font's family
 * @param fontSize points, 8 to 48
 * @param tabSize columns per tab stop, 1 to 16
 * @param softWrap whether new tabs wrap long lines
 * @param maxFileMib the largest file opened, 1 to 256 MiB
 * @param vim whether Vim mode is on (Vim spec 9)
 */
public record EditorSettings(String fontFamily, int fontSize, int tabSize, boolean softWrap, int maxFileMib, boolean vim) {
    /** Tried in order for the default font. */
    public static final List<String> PREFERRED_FONTS = List.of("JetBrains Mono", "Menlo", "Consolas", "DejaVu Sans Mono");

    /** @return {@link #maxFileMib()} in bytes */
    public long maxFileBytes() { return maxFileMib * 1024L * 1024L; }

    /** @return the plain editor font */
    public Font font() { return new Font(fontFamily, Font.PLAIN, fontSize); }

    /** The Editor's declared settings; Settings checks their values and reports bad ones. */
    public static final class Declared {
        private final Setting<String> fontFamily;
        private final Setting<Integer> fontSize, tabSize, maxFileMib;
        private final Setting<Boolean> softWrap, vim;

        /**
         * Declares the settings; call while the plugin starts.
         *
         * @param settings the plugin's settings
         * @param installed the installed font families; the default font is the first of {@link #PREFERRED_FONTS} installed, else Monospaced
         */
        public Declared(PluginSettings settings, Set<String> installed) {
            String fallback = PREFERRED_FONTS.stream().filter(installed::contains).findFirst().orElse("Monospaced");
            fontFamily = settings.declare(SettingSpec.of("font_family",
                    SettingKind.text(family -> family.equals(fallback) || installed.contains(family), "No installed font family has that name."), fallback)
                .label("Font").description("An installed font family.").editor(EditorHint.font(true)).section("Font").keywords("editor", "font"));
            fontSize = settings.declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13)
                .label("Font size").description("Points.").section("Font").keywords("editor", "font"));
            tabSize = settings.declare(SettingSpec.of("tab_size", SettingKind.integer(1, 16), 4)
                .label("Tab size").description("Columns per tab stop.").section("Editing").keywords("editor", "indent"));
            softWrap = settings.declare(SettingSpec.of("soft_wrap", SettingKind.bool(), false)
                .label("Soft wrap").description("Wrap long lines in tabs opened from now on.").section("Editing").keywords("editor", "wrap"));
            vim = settings.declare(SettingSpec.of("vim", SettingKind.bool(), false)
                .label("Vim mode").description("Modal editing as in IdeaVim; the Vim status item and Toggle Vim Mode change it.")
                .section("Editing").keywords("editor", "vim", "modal"));
            maxFileMib = settings.declare(SettingSpec.of("max_file_mib", SettingKind.integer(1, 256), 10)
                .label("Largest file (MiB)").description("Larger files are refused.").section("Files").keywords("editor", "size"));
        }

        /** @return the current values */
        public EditorSettings current() {
            return new EditorSettings(fontFamily.get(), fontSize.get(), tabSize.get(), softWrap.get(), maxFileMib.get(), vim.get());
        }

        /**
         * Saves Vim mode to the settings file.
         *
         * @param on the new value
         * @return completes when the file is written, exceptionally when it could not be
         */
        public CompletableFuture<Void> saveVim(boolean on) { return vim.set(on); }
    }
}
```

Replace the whole of `EditorEnvironment.java` with:

```java
package dev.jasper.editor.ui;

import dev.jasper.editor.session.Question;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.plugin.PluginContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What the Editor window needs from the plugin.
 *
 * @param context the plugin's context
 * @param ui the UI thread
 * @param askers builds the window's question asker
 * @param waits builds the window's Save All wait
 * @param settings the current settings
 * @param saveVim saves Vim mode to the settings file; completes on any thread
 * @param timers whether the watcher, notice and typing timers run; tests drive them by hand
 */
public record EditorEnvironment(PluginContext context, Executor ui, Function<WindowOwner, Function<Question, String>> askers,
                                Function<WindowOwner, BiFunction<String, CompletableFuture<Boolean>, Boolean>> waits,
                                Supplier<EditorSettings> settings, Function<Boolean, CompletableFuture<Void>> saveVim, boolean timers) {
    /**
     * @param context the plugin's context
     * @param ui the UI thread
     * @param settings the declared settings
     * @return modal dialogs, the nested-loop wait and running timers
     */
    public static EditorEnvironment production(PluginContext context, Executor ui, EditorSettings.Declared settings) {
        return new EditorEnvironment(context, ui, owner -> new DialogAsker(context.windows(), owner),
            owner -> new SaveWait(context.windows(), owner), settings::current, settings::saveVim, true);
    }
}
```

In `EditorPlugin.java`:
- Replace
  `    private final BiFunction<PluginContext, Supplier<EditorSettings>, EditorEnvironment> environments;` with
  `    private final BiFunction<PluginContext, EditorSettings.Declared, EditorEnvironment> environments;`.
- Delete the field `    private EditorSettings settings;`.
- Replace
  `    EditorPlugin(Executor ui, BiFunction<PluginContext, Supplier<EditorSettings>, EditorEnvironment> environments) {`
  with `    EditorPlugin(Executor ui, BiFunction<PluginContext, EditorSettings.Declared, EditorEnvironment> environments) {`.
- Replace these six lines in `start`

```java
        settings = EditorSettings.read(context.config(), fonts);
        environment = environments.apply(context, () -> settings);
        context.config().onChanged(() -> ui.execute(() -> {
            settings = EditorSettings.read(context.config(), fonts);
            if (window != null) window.apply();
        }));
```

  with:

```java
        EditorSettings.Declared settings = new EditorSettings.Declared(context.settings(), fonts);
        environment = environments.apply(context, settings);
        context.settings().onChanged(() -> ui.execute(() -> { if (window != null) window.apply(); }));
```

- Delete the import `java.util.function.Supplier`.

In `EditorWindow.java`, replace the whole `toggleVim` method and its Javadoc with:

```java
    /**
     * Toggle Vim Mode and the Vim status item: switches every tab at once and saves {@code vim} through the declared
     * setting so the choice lasts (Vim spec 1.2). A failed save keeps the choice for this session and says so.
     */
    public void toggleVim() {
        boolean on = !vimOn();
        vimToggled = on;
        for (EditorTab tab : editorTabs()) tab.vim(on ? Optional.of(vim) : Optional.empty());
        refreshStatus();
        CompletableFuture<Void> saved;
        try { saved = environment.saveVim().apply(on); }
        catch (RuntimeException failed) { saved = CompletableFuture.failedFuture(failed); }
        saved.whenComplete((done, failed) -> {
            if (failed == null) return;
            Throwable cause = failed instanceof java.util.concurrent.CompletionException && failed.getCause() != null ? failed.getCause() : failed;
            String reason = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            environment.ui().execute(() -> ask.apply(new Question("Vim mode", "Vim mode is " + (on ? "on" : "off")
                + " until the Editor closes: the setting could not be saved (" + reason + ").", List.of("OK"))));
        });
    }
```

Delete the import `java.io.IOException` from `EditorWindow.java`; nothing else there uses it.

Delete `SettingsFile.java` and `SettingsFileTest.java`.

- [ ] **Step 4: Say where to change the settings**

Replace the first line of `plugins/editor/src/main/resources/settings.toml` with:

```toml
# Editor settings. Change them in Settings > Tools > Editor or here; changes apply at once to open tabs.
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test verifyPluginArchitecture`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add -A plugins/editor
git commit -m "Declare the Editor's settings and save Vim mode through its setting

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The 0.9 break

**Files:**
- Delete: `jasper-sdk/src/main/java/dev/jasper/sdk/plugin/PluginConfig.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/plugin/PluginContext.java`,
  `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`
- Modify: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/AppearanceContractTest.java`,
  `jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java`
- Delete: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginConfig.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginContext.java`, `FakePluginHost.java`
- Modify: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePluginHostTest.java`
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java`
- Delete: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginSettings.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/PluginSettingsTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java`, `PluginHost.java`,
  `PluginRuntime.java`, `PluginSettingsFiles.java`, `DeclaredSettings.java`, `TomlText.java`
- Modify (tests): `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`, `HostedPaletteTest.java`,
  `PluginHostTest.java`, `PluginRuntimeTest.java`, `PluginSettingsFilesTest.java`, `DeclaredSettingsTest.java`,
  `VersionRangeTest.java`, `PluginPanelPageTest.java`, `PluginSettingsPageTest.java`,
  `StagedTransferLifecycleTest.java`, `PluginPreviewSupport.java`
- Modify: every `plugins/*/src/main/resources/plugin.toml`

**Interfaces:**
- Consumes: Tasks 1–4. No bundled plugin calls `context.config()` any more.
- Produces:
  - SDK 0.9.0 without `PluginConfig`.
  - `PluginHost.Environment` without `settings`, `settingsFile` and `positions`.
  - Test helpers `AppContractTest.host(Path data, Duration drainGrace, ...)` without the `tables` parameter.

- [ ] **Step 1: Write the failing tests**

In `VersionRangeTest.java`, replace the test `pluginsBuiltForSdkZeroSevenAreIncompatibleWithZeroEight` with:

```java
    @Test void pluginsBuiltForSdkZeroEightAreIncompatibleWithZeroNine() {
        var host = Version.parse(dev.jasper.sdk.JasperSdk.VERSION);
        assertThat(VersionRange.parse(">=0.8.5, <0.9").contains(host)).isFalse();
        assertThat(VersionRange.parse(">=0.9.0, <0.10").contains(host)).isTrue();
    }
```

In `AppearanceContractTest.java` and `PaletteValuesTest.java`, replace `isEqualTo("0.8.5")` with `isEqualTo("0.9.0")`.

In `PluginContractTest.java`, add the import `import dev.jasper.sdk.settings.SettingsPanel;` beside the other
`dev.jasper.sdk.settings` imports, and add this case after `settingsAreDeclaredOnlyWhileStartingWithUniqueLabelledKeys`:

```java
    @Test void aPluginGivesOneSettingsPageAndOnlyWhileStarting() {
        var settings = new AtomicReference<PluginSettings>();
        SettingsPanel panel = panelHost -> new JLabel("Mine");
        h.start(info("test.page"), Set.of(), Set.of(), context -> {
            settings.set(context.settings());
            context.settings().page(panel);
            assertThatIllegalStateException().isThrownBy(() -> context.settings().page(panel));
        });
        assertThat(h.active("test.page")).isTrue();
        h.ui(() -> assertThatIllegalStateException().isThrownBy(() -> settings.get().page(panel)));
    }
```

In `DeclaredSettingsTest.java`, replace the test `aPluginThatDeclaresNothingGetsNoReports` with:

```java
    @Test void aPluginThatDeclaresNothingHasItsKeysReportedAsUnknown() throws Exception {
        write("greeting = \"hello\"\n");
        start(settings -> { });
        assertThat(reports()).containsExactly("WARNING greeting 1 Unknown setting or table; ignored.");
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test :jasper-app:test --tests 'dev.jasper.app.plugins.VersionRangeTest' --tests 'dev.jasper.app.plugins.DeclaredSettingsTest' --tests 'dev.jasper.app.plugins.AppContractTest'`
Expected: FAIL. The version is 0.8.5, and a plugin that declares nothing gets no reports. The new contract case
passes already, because `page()` is enforced since C2.

- [ ] **Step 3: Remove `PluginConfig` from the SDK and make it 0.9.0**

- Delete `jasper-sdk/src/main/java/dev/jasper/sdk/plugin/PluginConfig.java`.
- In `PluginContext.java`, delete these lines:

```java
    /**
     * The plugin's configuration table.
     *
     * @return the read-only view
     */
    PluginConfig config();

```

- In `JasperSdk.java`, replace `public static final String VERSION = "0.8.5";` with
  `public static final String VERSION = "0.9.0";`.

- [ ] **Step 4: Remove it from the testkit**

- Delete `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginConfig.java`.
- In `FakePluginContext.java`:
  - Delete the import `dev.jasper.sdk.plugin.PluginConfig;`.
  - Delete the fields `volatile Map<String, Object> table = Map.of();` and
    `final CopyOnWriteArrayList<Runnable> configListeners = new CopyOnWriteArrayList<>();`.
  - Delete the whole `@Override public PluginConfig config() { ... }` method, five lines ending with
    `host.dataRoot().resolve(info.id()).resolve(info.id() + ".toml"));` and its closing brace.
- In `FakePluginHost.java`:
  - Delete the field `private final Map<String, Map<String, Object>> presets = new HashMap<>();`.
  - Delete the field `final List<String> reports = new CopyOnWriteArrayList<>();`.
  - Delete the line `        context.table = presets.getOrDefault(info.id(), Map.of());`.
  - Delete the whole `setConfig` method with its Javadoc.
  - Delete the whole `reports()` method with its Javadoc, which begins `Configuration problems plugins reported.`.
- In `FakePluginHostTest.java`, delete the whole test `configurationIsTypedNestedObservableAndReportable`.

- [ ] **Step 5: Remove it from the application**

- Delete `jasper-app/src/main/java/dev/jasper/app/plugins/PluginSettings.java` and
  `jasper-app/src/test/java/dev/jasper/app/plugins/PluginSettingsTest.java`.
- In `HostedContext.java`:
  - Delete the import `dev.jasper.sdk.plugin.PluginConfig;`.
  - Delete the field `    final PluginSettings settings;`.
  - Replace the constructor's first two lines

```java
    HostedContext(PluginHost host, HostedPlugin hosted, PluginSettings settings, DeclaredSettings declared) {
        this.host = host; this.hosted = hosted; this.settings = settings; this.declared = declared;
```

    with:

```java
    HostedContext(PluginHost host, HostedPlugin hosted, DeclaredSettings declared) {
        this.host = host; this.hosted = hosted; this.declared = declared;
```

  - Delete the line `        settings.close();` in the teardown.
  - Delete the line `    @Override public PluginConfig config() { return settings; }`.
- In `PluginHost.java`:
  - Replace the `Environment` record's first two lines

```java
    record Environment(Consumer<Runnable> ui, BooleanSupplier onUi, Function<String, Path> dataDirectory,
                       Function<String, Map<String, Object>> settings, Function<String, Path> settingsFile, java.util.function.BiFunction<String, List<String>, java.util.Optional<PluginSettingsFiles.Position>> positions, Consumer<dev.jasper.app.config.ConfigDiagnostic> configReport,
```

    with:

```java
    record Environment(Consumer<Runnable> ui, BooleanSupplier onUi, Function<String, Path> dataDirectory,
                       Consumer<dev.jasper.app.config.ConfigDiagnostic> configReport,
```

  - Replace the two lines that build the context in `start`

```java
        var context = new HostedContext(this, hosted, new PluginSettings(id, environment.settingsFile().apply(id), environment.settings().apply(id),
            containment, path -> environment.positions().apply(id, path), environment.configReport()), declared);
```

    with `        var context = new HostedContext(this, hosted, declared);`.

  - In `settingsChanged(Set<String> reloaded)`, delete the line
    `                context.settings.update(environment.settings().apply(id), reloaded.contains(id));`.
- In `PluginRuntime.java`, replace the three lines that build `created`

```java
        PluginHost created = new PluginHost(new PluginHost.Environment(SwingUtilities::invokeLater,
            SwingUtilities::isEventDispatchThread, id -> options.userDirectory().resolve(id).resolve("data"),
            id -> settingsFiles.current(id), id -> PluginSettingsFiles.file(options.userDirectory(), id), settingsFiles::position, reports.report(), DRAIN_GRACE, contributions, () -> this.dark, windows, terminals, notice, editor, settingsFiles, reports.clear()));
```

  with:

```java
        PluginHost created = new PluginHost(new PluginHost.Environment(SwingUtilities::invokeLater,
            SwingUtilities::isEventDispatchThread, id -> options.userDirectory().resolve(id).resolve("data"),
            reports.report(), DRAIN_GRACE, contributions, () -> this.dark, windows, terminals, notice, editor, settingsFiles, reports.clear()));
```

- In `PluginSettingsFiles.java`:
  - Delete the import `org.tomlj.TomlPosition;`.
  - Delete the two lines `    /** A 1-based line and column in a plugin's settings file. */` and
    `    record Position(int line, int column) { }`.
  - Delete the whole `position(String id, List<String> path)` method with its Javadoc line (seven lines).
- In `PluginSettingsFilesTest.java`, delete the line
  `        assertThat(files.position("dev.example.tool", List.of("greeting"))).hasValue(new PluginSettingsFiles.Position(1, 1));`.
- In `DeclaredSettings.java`:
  - Replace `        if (closed || open || handles.isEmpty()) return;` with `        if (closed || open) return;`.
  - In the Javadoc of `reload`, replace

```java
     * keeps the last good values. Listeners of changed values run. A plugin that declared nothing still reads its file
     * through {@code PluginConfig} until SDK 0.9, so nothing is read or reported for it here. UI thread.
```

    with:

```java
     * keeps the last good values. Listeners of changed values run; a plugin that declared nothing has every key
     * reported as unknown. UI thread.
```

- In `TomlText.java`, replace the class Javadoc line with
  `/** Writes an old {@code [plugins."<id>"]} table as TOML: scalars and string lists first, then one table per nested map. */`.

- [ ] **Step 6: The application's tests**

In `AppContractTest.java`:
- Replace the Environment construction

```java
        return onEdtValue(() -> new PluginHost(new PluginHost.Environment(SwingUtilities::invokeLater,
            SwingUtilities::isEventDispatchThread, data::resolve, id -> tables.getOrDefault(id, Map.of()), id -> data.resolve(id + ".toml"),
            (id, path) -> java.util.Optional.empty(), diagnostics::add, drainGrace, contributions, dark::get, auxiliary, terminals,
            notices::add, edited::add, files, file -> diagnostics.removeIf(diagnostic -> diagnostic.file().equals(file)))));
```

  with:

```java
        return onEdtValue(() -> new PluginHost(new PluginHost.Environment(SwingUtilities::invokeLater,
            SwingUtilities::isEventDispatchThread, data::resolve, diagnostics::add, drainGrace, contributions, dark::get, auxiliary, terminals,
            notices::add, edited::add, files, file -> diagnostics.removeIf(diagnostic -> diagnostic.file().equals(file)))));
```

- Remove the `tables` parameter everywhere with this script, run from the worktree root:

```bash
python3 - <<'PY'
import pathlib, re
base = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/plugins")
contract = base / "AppContractTest.java"
text = contract.read_text(encoding="utf-8")
text = text.replace("Path data, Map<String, Map<String, Object>> tables, Duration drainGrace", "Path data, Duration drainGrace")
text = text.replace("host(data, tables, drainGrace,", "host(data, drainGrace,")
text = text.replace("host(data, Map.of(), Duration.ofMillis(200), contributions,", "host(data, Duration.ofMillis(200), contributions,")
contract.write_text(text, encoding="utf-8")
for name in ["PluginPanelPageTest.java", "PluginSettingsPageTest.java", "StagedTransferLifecycleTest.java",
             "PluginHostTest.java", "PluginPreviewSupport.java", "DeclaredSettingsTest.java"]:
    path = base / name
    source = path.read_text(encoding="utf-8")
    updated = re.sub(r"AppContractTest\.host\(([^,]+),\s*Map\.of\(\),", r"AppContractTest.host(\1,", source)
    path.write_text(updated, encoding="utf-8")
PY
grep -rn "tables" jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java
```

  Expected: the `grep` prints nothing.

In `HostedPaletteTest.java`, replace the Environment construction

```java
        host = onEdtValue(() -> new PluginHost(new PluginHost.Environment(javax.swing.SwingUtilities::invokeLater,
            javax.swing.SwingUtilities::isEventDispatchThread, data::resolve, id -> Map.of(), id -> data.resolve(id + ".toml"), (id, path) -> java.util.Optional.empty(), diagnostic -> { },
```

with:

```java
        host = onEdtValue(() -> new PluginHost(new PluginHost.Environment(javax.swing.SwingUtilities::invokeLater,
            javax.swing.SwingUtilities::isEventDispatchThread, data::resolve, diagnostic -> { },
```

In `PluginHostTest.java`:
- Add these imports:
  - `import dev.jasper.sdk.settings.Setting;`
  - `import dev.jasper.sdk.settings.SettingKind;`
  - `import dev.jasper.sdk.settings.SettingSpec;`
  - `import java.nio.file.Files;`
- Replace the test `stopRunsInReverseOrderAndSettingsChangesReachOnlyChangedPlugins` (after the script above ran)
  with:

```java
    @Test void stopRunsInReverseOrderAndSettingsChangesReachOnlyChangedPlugins() throws Exception {
        Files.createDirectories(data.resolve("test.first"));
        Files.writeString(data.resolve("test.first/test.first.toml"), "k = \"v\"\n");
        PluginHost host = AppContractTest.host(data, Duration.ofMillis(200));
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        for (String id : List.of("test.first", "test.second")) {
            onEdt(() -> host.start(hosted(id, Set.of(), new Plugin() {
                @Override public void start(PluginContext context) {
                    assertThat(context.dataDirectory()).isDirectory().hasFileName(id);
                    Setting<String> k = context.settings().declare(SettingSpec.of("k", SettingKind.text(), "").label("K"));
                    k.onChanged(value -> order.add("changed:" + id + ":" + value));
                }
                @Override public void stop() { order.add("stop:" + id); }
            })));
        }
        Files.writeString(data.resolve("test.first/test.first.toml"), "k = \"w\"\n");
        onEdt(() -> host.settingsChanged(host.environment.settingsFiles().refresh(true)));
        onEdt(host::stop);
        assertThat(order).containsExactly("changed:test.first:w", "stop:test.second", "stop:test.first");
    }
```

In `PluginRuntimeTest.java`, in the `FIXTURE` source:
- Add these imports after `import dev.jasper.sdk.plugin.PluginContext;`:
  - `import dev.jasper.sdk.settings.Setting;`
  - `import dev.jasper.sdk.settings.SettingKind;`
  - `import dev.jasper.sdk.settings.SettingSpec;`
- Replace the two lines

```java
                note(context, "started", context.config().string("greeting").orElse("none"));
                context.config().onChanged(() -> note(context, "changed", context.config().string("greeting").orElse("none")));
```

  with:

```java
                Setting<String> greeting = context.settings().declare(SettingSpec.of("greeting", SettingKind.text(), "none").label("Greeting"));
                note(context, "started", greeting.get());
                greeting.onChanged(value -> note(context, "changed", value));
```

  Replace the comment lines in `aChangedSettingsFileReachesThePluginWithoutAConfigReload`

```java
            // Reuse the probe fixture of the test above: it writes context.config().string("greeting") to data/started at start
            // and, on config change, to data/changed. Read that fixture's source before writing this test and adapt the file names.
```

  with:

```java
            // The probe fixture above writes its declared greeting to data/started at start and, when it changes, to data/changed.
```

- [ ] **Step 7: Every bundled plugin requires SDK 0.9**

In each `plugins/<name>/src/main/resources/plugin.toml`, set `version` and `sdk` to exactly these values:

| Plugin | `version` | `sdk` |
|---|---|---|
| editor | `"0.3.0"` | `">=0.9.0, <0.10"` |
| history | `"0.2.0"` | `">=0.9.0, <0.10"` |
| remote | `"0.5.0"` | `">=0.9.0, <0.10"` |
| sample | `"0.2.0"` | `">=0.9.0, <0.10"` |
| snippets | `"0.2.0"` | `">=0.9.0, <0.10"` |
| vault | `"0.3.0"` | `">=0.9.0, <0.10"` |

- [ ] **Step 8: Find what still names the old API**

Run:

```bash
grep -rn -e 'PluginConfig' -e 'config()' -e 'setConfig' -e 'host.reports()' --include='*.java' jasper-sdk jasper-sdk-testkit jasper-app/src plugins | grep -v '/build/'
```

Expected: no line names the SDK's `PluginConfig`, a plugin context's `config()`, `setConfig` or `FakePluginHost.reports()`.
The application's own configuration code (`ConfigService`, `ConfigLoader` and similar, outside
`dev.jasper.app.plugins`) may still match `config()`. Fix any other match the same way as above.

- [ ] **Step 9: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check :jasper-app:test verifySdkArchitecture verifyApplicationArchitecture verifyPluginArchitecture`
Then: `./gradlew :jasper-plugin-editor:test :jasper-plugin-history:test :jasper-plugin-remote:test :jasper-plugin-sample:test :jasper-plugin-snippets:test :jasper-plugin-vault:test`
Expected: PASS. If a test starts a plugin that declares nothing over a settings file with keys, it now sees an
unknown-key report. Where that report is not what the test is about, give the test's file no keys rather than
asserting on the report.

- [ ] **Step 10: Commit**

```bash
git branch --show-current
git add -A jasper-sdk jasper-sdk-testkit jasper-app plugins
git commit -m "Remove PluginConfig: SDK 0.9.0, and every bundled plugin requires it

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Documentation, the Sample's file, and the full check

**Files:**
- Modify: `plugins/sample/src/main/resources/settings.toml`, `docs/configuration.md`, `docs/plugin-authoring.md`,
  `docs/sdk-architecture.md`, `jasper-sdk/README.md`, `docs/remote.md`, `docs/remote-7c-verification.md`,
  `docs/credential-vault.md`, `docs/STATUS.md`, and this plan's status banner

**Interfaces:**
- Consumes: Tasks 1–5.
- Produces: docs only.

- [ ] **Step 1: The Sample's file**

Replace the first line of `plugins/sample/src/main/resources/settings.toml` with:

```toml
# Settings for the sample plugin. Change them in Settings > Tools > Sample or here; each applies after Jasper restarts. Every demo is off until you set it.
```

- [ ] **Step 2: `docs/configuration.md`**

In the Plugins section:
- Replace the text that starts at `Values may` (the end of a line) and ends at
  `through the same diagnostics.` (mid-line, before `Right-click`). It reads, across its line breaks:
  `Values may be strings, integers, floats, booleans, arrays of strings and nested tables; a plugin reports problems with its settings through the same diagnostics.`
  Replace it with this text, keeping the lines at most 120 characters:
  `Each plugin declares its settings: a value its declaration does not allow is reported with its line and the default is used, and a key the plugin does not declare is reported as unknown and ignored.`

- Replace the first line inside the sample's `toml` block with the new first line from Step 1.

In the Editor section, replace `` `font_family`, `font_size`, `tab_size`, `soft_wrap` and `max_file_mib`, applied live. ``
with `` `font_family`, `font_size`, `tab_size`, `soft_wrap`, `max_file_mib` and `vim`, applied live; they are also on its page under Settings › Tools. ``

In the Shell history section, replace

```markdown
Setting the key **replaces** the default list rather than adding to it, so include any defaults you
want to keep. `deprioritize_trivial = false` turns trivial-command de-ranking off;
```

with:

```markdown
Setting the key **replaces** the default list rather than adding to it, so include any defaults you
want to keep; an empty list means no trivial commands. `deprioritize_trivial = false` turns trivial-command de-ranking off;
```

- [ ] **Step 3: `docs/plugin-authoring.md`**

- In the manifest example, replace `sdk = ">=0.6, <0.7"` with `sdk = ">=0.9, <0.10"`.
- Replace `` bundled plugins declare `sdk = ">=0.8.0, <0.9"` `` with `` bundled plugins declare `sdk = ">=0.9.0, <0.10"` ``.
- Replace these three lines

```markdown
- **Configuration.** Users edit `plugins/<id>/<id>.toml`; see [plugin settings](configuration.md#plugins). It is read-only to
  the plugin; keep your own state under `context.dataDirectory()`. Problems you report with
  `config().report(key, message)` are shown against that file, at the key's line, until the file is next reloaded.
```

  with:

```markdown
- **Settings.** Declare them while starting (see "Declared settings" below); users edit them in Settings › Tools or in
  `plugins/<id>/<id>.toml` (see [plugin settings](configuration.md#plugins)). Jasper checks and reports the values;
  keep your own state under `context.dataDirectory()`.
```

- Replace `` reported as unknown. Declaring after `start` returns throws. `context.config()` still works until SDK 0.9 removes it. ``
  with `` reported as unknown. Declaring after `start` returns throws. SDK 0.9 removed `PluginConfig` and `context.config()`. ``
- In the Testing block, replace `    host.setConfig("dev.example.tool", Map.of("enabled", true));` with
  `    host.setSetting("dev.example.tool", "enabled", true);   // as if the user edited the file`.
- Directly after that block's closing fence, add this paragraph:

```markdown
`host.settingsReports(id)` lists what the last read of the plugin's file reported, as `key: message`, and
`host.savedSettings(id)` what `set` wrote.
```

- [ ] **Step 4: `docs/sdk-architecture.md` and the SDK README**

- In `docs/sdk-architecture.md`, replace

```markdown
one-second poller re-reads changed files into `PluginSettings`, which fires `PluginConfig.onChanged`;
```

  with:

```markdown
one-second poller re-reads changed files, and each plugin's `DeclaredSettings` updates its handles and runs their listeners;
```

- Replace `` suite holds the fake and the application to the same behaviour. `PluginConfig` remains until SDK 0.9 removes it. ``
  with `` suite holds the fake and the application to the same behaviour. SDK 0.9.0 removed `PluginConfig`; every bundled plugin declares its settings. ``
- In `jasper-sdk/README.md`, replace ``| `dev.jasper.sdk.plugin` | `Plugin` (the entry point), `PluginContext`, `PluginConfig` |``
  with ``| `dev.jasper.sdk.plugin` | `Plugin` (the entry point), `PluginContext` |``, and in the next row add
  `` `SettingsPanel`, `SettingsPanelHost` `` after `` `Applies` `` so the row reads
  ``| `dev.jasper.sdk.settings` | `PluginSettings`, `Setting`, `SettingSpec`, `SettingKind`, `Choice`, `EditorHint`, `Applies`, `SettingsPanel`, `SettingsPanelHost` |``.

- [ ] **Step 5: Remote and Vault docs**

In `docs/remote.md`, replace the block from `` Settings live in `plugins/dev.jasper.remote/dev.jasper.remote.toml` ``
through the paragraph ending `` Connection settings remain above the `[shortcuts]` table. `` with:

````markdown
Settings live in `plugins/dev.jasper.remote/dev.jasper.remote.toml`; change them in Settings › Tools › Remote or in the
file (Plugins manager > Open Settings):

```toml
connect_timeout_seconds = 10    # 0 to 3600
auth_timeout_seconds = 30       # 0 to 3600
keepalive_seconds = 30          # 0 disables
session_linger_seconds = 5
read_user_known_hosts = true
use_ssh_agent = true
```

Connection settings apply to the next connection. A value out of range is reported and its default used.
Toggle SSH Hosts (Cmd+Shift+S), Connect to SSH Host… (Cmd+Shift+H) and Toggle SFTP (no default) are bound in
Settings › Keymap like any other action; `cmd` means Command on macOS and Ctrl+Shift elsewhere. A `[shortcuts]`
table from an older Remote is reported as unknown; delete it and rebind in Keymap.
````

In the same file, replace

````markdown
Add these options to Remote's existing TOML tables (do not duplicate a table):

```toml
[shortcuts]
# Existing toggle_panel and open_palette entries may remain here.
toggle_sftp = ""                 # e.g. "cmd+alt+f"

[sftp]
max_parallel_files = 2           # clamped to 1..8; at most two copies per endpoint
request_timeout_seconds = 30    # clamped to 1..300
```
````

with:

````markdown
SFTP limits are in the same file, and apply at once:

```toml
[sftp]
max_parallel_files = 2           # 1 to 8; at most two copies per endpoint
request_timeout_seconds = 30    # 1 to 300
```
````

In `docs/remote-7c-verification.md`, replace
``8. Set `toggle_sftp` in Remote's TOML; verify live changes and app override precedence. Set copy``
with ``8. Bind Toggle SFTP in Settings › Keymap and check it in every window. Set copy``.

In `docs/credential-vault.md`, replace
`` settings through the Plugins manager's **Open Settings** action: `` with
`` settings in Settings › Tools › Credential Vault, or in the file through the Plugins manager's **Open Settings** action: ``,
and replace `` auto_lock_minutes = 15            # 0 disables inactivity locking `` with
`` auto_lock_minutes = 15            # 0 to 1440; 0 disables inactivity locking ``.

- [ ] **Step 6: Record the outcome**

Run `./gradlew check`. It must pass. Read the counts from `*/build/test-results/test/*.xml` and
`plugins/*/build/test-results/test/*.xml`.

Add at the top of `docs/STATUS.md`'s "Current state" list:

```markdown
- **Plugin settings C3** (`claude/plugin-settings-c3`, 2026-09-28; plan
  `docs/superpowers/plans/2026-09-28-jasper-plugin-settings-c3.md`): SDK 0.9.0. Editor, Shell History, Remote and
  Credential Vault declare their settings and get generated pages under Settings › Tools; Snippets declares none.
  `PluginConfig`, `PluginContext.config()`, the testkit's `setConfig` and `reports()`, and the application's
  `PluginSettings` view are gone; every bundled plugin requires `>=0.9.0, <0.10`. Remote's `[shortcuts]` table is
  gone: its actions keep Cmd+Shift+S and Cmd+Shift+H as default bindings, rebindable in Keymap, and old lines warn
  as unknown keys. The Editor's Vim toggle saves through its setting. History's empty list means no trivial
  commands. Values the plugins used to clamp now fall back to their defaults with a report. `./gradlew check`: N tests,
  0 failures, S skipped. GUI checks pending with the user: each plugin's page under Tools; the Editor's Vim toggle
  and its page's Vim mode; a Remote file with an old `[shortcuts]` table; Keymap rebinding Toggle SSH Hosts.
  Still deferred from C2:
  - a pending-changed callback for plugin controls
  - per-page dedupe of warnings from a throwing `modified()`/`keywords()`
  - the stub's Open in Editor failure going to a notice while Settings is modal
  - section-heading carry-over for unsectioned settings
  - the discard count per plugin page

  Deviations: see the plan's status banner. Not pushed; not merged.
```

Replace N and S with the real counts. Set this plan's `**Status:**` line to
`**Status:** Implemented on claude/plugin-settings-c3; ./gradlew check green, N tests, 0 failures (S skipped).` with
the same counts. Under it, add a `Deviations so far:` list, or "none".

Run: `./gradlew :jasper-app:test --tests '*AppDocumentationTest*'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add -A docs jasper-sdk/README.md plugins/sample/src/main/resources/settings.toml
git commit -m "Document plugin settings on SDK 0.9 (plan C3)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
