# WSL B: SDK Shell Profiles and the Remote WSL Group Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Implemented on `worktree-wsl-hosts` (subagent-driven), 2026-09-28; Windows `check` shows only the known
failures; macOS/Linux check and native acceptance handed to the user. `./gradlew check --continue` on Windows: 3479
tests, 234 skipped, 8 failed, every one on "Known failed test cases" in `docs/windows-startup-verification.md` (app
1187/141 skipped/2 failed, terminal 391/19/0, Editor 1172/13/0, Remote 278/54/6, Vault 139/7/0, Buddy 163/0/0,
History 40/0/0, SDK 27/0/0, SDK testkit 60/0/0, Sample 11/0/0, Snippets 11/0/0). Plan A is implemented on this
branch, which satisfies "plan A merged".
Record every deviation from this text here and in `docs/STATUS.md`.

Deviations so far: none in code. Tasks 1-3 applied as written: plan A landed `ShellProfile`, `ShellProfile.Kind`, `ShellProfileCatalog.current()/onChanged(Runnable)`, `OpenSpec.Local(Optional<Path>, Optional<String>)` and the `JasperApplication.shellProfiles` field (created in the constructor, before `startPlugins`) under the names this plan assumed, and every edit anchor still matched. `knownProfile` compares ids exactly (as the fake does), although plan A resolves `wsl:` ids case-insensitively when launching. Plugins match profile ids exactly; they receive canonical ids from profiles(), so plan A's case-insensitive wsl: matching is not needed there. Tasks 4-7 applied as written, with one hygiene fix: the Task 5 and 6 commits left the replaced `HostsPanel.java` and `RemoteScope.java` (and `HostsPanelTest.java`) with raw non-ASCII literals (the dash, ellipsis, stars, dot and middle dot, as the files had before); Task 7's commit rewrites them as Java escapes, as the plan's code intended. Task 8
(documentation) additions beyond the text: `docs/remote.md` gains native item 15 (the Sessions dropdown and `>ssh`
rows, which items 11 to 14 did not exercise), item 13 names the "Detect shells again" button, the WSL-group checks
are copied into `docs/windows-startup-verification.md`'s native checklist, and the full check ran as `check
--continue` on Windows, where it is not green only because of the known failures (Step 4 expected BUILD SUCCESSFUL).

**Goal:** Plugins can list the shells Jasper detected (SDK 0.8.6 `Terminals.profiles()`) and open one by id; the
Remote plugin uses that to show a read-only "WSL" group of distributions first in its panel (renamed "Sessions"),
in the Sessions toolbar menu and in the `>ssh` palette scope, opening each distribution as a local tab or split.

**Architecture:** The SDK gains the value `ShellProfileInfo`, a `profile` component on `LocalSpec`, the factory
`OpenRequest.localProfile(id)` and two binary-compatible default methods on `Terminals` (`profiles()`,
`onProfilesChanged(Runnable)`), gated by `terminal.open`. In the app, plan A's `launch.ShellProfileCatalog` reaches
the plugin runtime as `PluginRuntime.ShellProfiles` (a supplier plus a listener registration, so
`dev.jasper.app.plugins` depends only on the `launch.ShellProfile` value); `HostedTerminals` maps profiles to SDK
values, closes listeners with the plugin, maps `LocalSpec.profile` into `OpenSpec.Local`, and refuses an id the
catalog does not list (empty result plus a warning) instead of falling back to the default shell. The testkit fake
gets a scripted profile list, and the contract suite pins the known, unknown and default cases for both runtimes.
Remote adds `HostRows.Distro`, keeps its own pane-to-profile bookkeeping, and refreshes every surface when the
profiles change.

**Tech Stack:** Java 25 (JetBrains Runtime), Swing, Gradle wrapper, JUnit 5, AssertJ, jasper-sdk-testkit.

**Spec:** `docs/superpowers/specs/2026-09-28-jasper-wsl-shell-profiles-design.md`, sections 6, 7 and 9 (read all of it
first). Plan A implements sections 3 to 5 and must be merged into this branch before Task 1.

**Decisions this plan makes (flag them to the user when reporting):**
- `ShellProfileInfo.Kind` is `LOGIN_SHELL, LOCAL_SHELL, WSL, CUSTOM`, mirroring the app (the spec draft said
  `DEFAULT`; renamed so it cannot be read as "the profile the user chose as default"). The SDK does not expose
  which profile is the user's default; the default WSL distribution carries "· default" in its detail.
- `detail` is the app profile's detail, passed through verbatim. Remote shows it as the badge, so the spec's
  default marker comes from plan A, which gives the default distribution the detail "WSL N · default".
- `profiles()` and `onProfilesChanged` need `terminal.open` ("no capability beyond `terminal.open`").
- The new `Terminals` methods are default methods (`List.of()`, inert subscription), as SDK 0.8.3 and 0.8.4 added
  methods, so hosts built against 0.8.5 still link.
- An empty `LocalSpec.profile` means "what Jasper's own New Tab or Split would start" (plan A's split inherits the
  pane's profile, spec section 9.1).
- The SDK toolbar dropdown has no separators, so the Sessions menu lists distributions **first, titled "Ubuntu (WSL 2)"**
  instead of under a "WSL" separator (spec section 7). Adding separators to `ToolbarItem` is out of scope.
- Distribution rows open a new tab on double-click (spec); they do not refocus an existing pane as hosts do.
- The `dev.jasper.remote.hosts` action keeps its title "Toggle SSH hosts" (an app test pins it); only the panel
  title becomes "Sessions".

## Global Constraints

- Build and test only with `./gradlew` from the worktree root; never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`).
- Work only in the worktree `C:\Users\dustin\Documents\moray\.claude\worktrees\wsl-hosts`; run `git branch --show-current`
  before every commit and confirm `worktree-wsl-hosts`. Never commit on `main`. Do not push.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Plan A's types are consumed, never redefined: `dev.jasper.app.launch.ShellProfile(String id, String name, Kind kind,
  Optional<String> detail, List<String> command)` with the nested `enum Kind { LOGIN_SHELL, LOCAL_SHELL, WSL, CUSTOM }`;
  `dev.jasper.app.launch.ShellProfileCatalog` with `List<ShellProfile> current()`, `void refresh()` and
  `dev.jasper.app.lifecycle.Subscription onChanged(Runnable)` (EDT); `dev.jasper.app.terminals.OpenSpec.Local(Optional<Path>
  directory, Optional<String> profile)` with the one-argument constructor kept. If plan A landed them differently
  (for example `Kind` as a top-level type, or another field name for the catalog in `JasperApplication`), adapt the
  code here to the merged names and record it as a deviation.
- Before editing any file, read it whole as it is after plan A's merge (plan A may have touched `TerminalFixture`,
  `JasperApplication`, `AppContractTest` or `HostedTerminals`); apply the edits below to the current text.
- SDK types appear in the app only inside `dev.jasper.app.plugins`. `verifySdkArchitecture`, `verifyPluginArchitecture`
  and `verifyApplicationArchitecture` must pass. Remote compiles against the SDK only.
- `jasper-sdk` runs Javadoc with `-Xdoclint:all` in `check`: every new public type, constructor, method, record
  component and enum constant needs a comment with its tags.
- Source hygiene per AGENTS.md: write non-ASCII characters in Java as escapes (`"\u25CF"` for the dot, `"\u2014"`,
  `"\u2026"`, `"\u2605"`, `"\u2606"`).
- Tests are headless and must pass on macOS, Linux and Windows: WSL appears only through scripted profiles.
- Plain text only in Swing labels (`html.disable`), as elsewhere in Remote.
- No Markdown links in this plan outside code blocks (plans are link-checked by `AppDocumentationTest`).

## File Structure

SDK (`jasper-sdk/src/main/java/dev/jasper/sdk/`):
- Create `terminal/ShellProfileInfo.java`: a detected shell as plugins see it.
- Modify `terminal/LocalSpec.java` (profile component), `terminal/OpenRequest.java` (`localProfile`),
  `terminal/Terminals.java` (`profiles`, `onProfilesChanged`), `JasperSdk.java` (0.8.6).
- Tests: `terminal/TerminalValuesTest.java`, `palette/PaletteValuesTest.java`, `ui/AppearanceContractTest.java`.

App (`jasper-app/src/`):
- Modify `main/.../plugins/PluginRuntime.java` (record `ShellProfiles`, 9-argument constructor), `PluginHost.java`
  (`Environment.profiles`), `HostedContext.java`, `HostedTerminals.java`, `package-info.java`;
  `main/.../application/JasperApplication.java` (wiring).
- Tests: create `test/.../plugins/TestShellProfiles.java`; modify `HostedTerminalsTest.java`, `TerminalFixture.java`,
  `AppContractTest.java`, `HostedPaletteTest.java`.

Testkit (`jasper-sdk-testkit/src/`):
- Modify `main/.../FakeWorkspace.java`, `FakeTerminals.java`, `FakePluginContext.java`, `FakePluginHost.java`;
  `testFixtures/.../contract/ContractHarness.java`, `PluginContractTest.java`; `test/.../FakeContractTest.java`,
  `FakeTerminalsTest.java`.

Remote (`plugins/remote/src/`):
- Modify `main/resources/plugin.toml`, `main/.../ui/HostRows.java`, `ui/HostsPanel.java`, `ui/RemoteScope.java`,
  `RemotePlugin.java`, `SessionsToolbar.java`.
- Tests: `ui/HostRowsTest.java`, `ui/HostsPanelTest.java`, `ui/RemoteScopeTest.java`, `ui/UiTestAccess.java`,
  `RemotePluginTest.java`.

Docs: `docs/remote.md`, `docs/sdk-architecture.md`, `docs/plugin-authoring.md`, `jasper-sdk/README.md`, `docs/README.md`,
`docs/STATUS.md`, this plan's status banner.

---

### Task 1: SDK 0.8.6 values, the profile on LocalSpec and the Terminals profile methods

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/ShellProfileInfo.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/LocalSpec.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/OpenRequest.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/Terminals.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/terminal/TerminalValuesTest.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java`, `jasper-sdk/src/test/java/dev/jasper/sdk/ui/AppearanceContractTest.java` (version)

**Interfaces:**
- Produces: `record ShellProfileInfo(String id, String name, ShellProfileInfo.Kind kind, Optional<String> detail)`,
  `enum ShellProfileInfo.Kind { LOGIN_SHELL, LOCAL_SHELL, WSL, CUSTOM }`; `record LocalSpec(Optional<Path> workingDirectory,
  Optional<String> profile)` plus `LocalSpec(Optional<Path>)` and `static LocalSpec ofProfile(String)`;
  `static OpenRequest OpenRequest.localProfile(String)`; `default List<ShellProfileInfo> Terminals.profiles()`;
  `default Subscription Terminals.onProfilesChanged(Runnable)`; `JasperSdk.VERSION = "0.8.6"`.
- Consumes: nothing new.

- [x] **Step 1: Write the failing tests**

Add to `TerminalValuesTest.java` (inside the class, after `openRequestsAreLocalAndTakeAnAbsoluteDirectory`):

```java
    @Test void shellProfilesAreNamedValuesAndALocalSpecMayNameOne() throws Exception {
        var ubuntu = new ShellProfileInfo("wsl:Ubuntu", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.of("WSL 2"));
        assertThat(ubuntu.detail()).contains("WSL 2");
        assertThat(ShellProfileInfo.Kind.values()).containsExactly(ShellProfileInfo.Kind.LOGIN_SHELL, ShellProfileInfo.Kind.LOCAL_SHELL,
            ShellProfileInfo.Kind.WSL, ShellProfileInfo.Kind.CUSTOM);
        assertThatIllegalArgumentException().isThrownBy(() -> new ShellProfileInfo(" ", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new ShellProfileInfo("wsl:Ubuntu", "", ShellProfileInfo.Kind.WSL, Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new ShellProfileInfo("pwsh", "PowerShell 7", null, Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new ShellProfileInfo("pwsh", "PowerShell 7", ShellProfileInfo.Kind.LOCAL_SHELL, null));

        assertThat(new LocalSpec(Optional.empty()).profile()).as("the old constructor means the default shell").isEmpty();
        assertThat(OpenRequest.localProfile("wsl:Ubuntu"))
            .isEqualTo(new OpenRequest.Local(new LocalSpec(Optional.empty(), Optional.of("wsl:Ubuntu"))))
            .isEqualTo(OpenRequest.local(LocalSpec.ofProfile("wsl:Ubuntu")));
        assertThatIllegalArgumentException().isThrownBy(() -> new LocalSpec(Optional.empty(), Optional.of(" ")));
        assertThatNullPointerException().isThrownBy(() -> new LocalSpec(Optional.empty(), null));
        assertThatIllegalArgumentException().as("the directory rule still applies")
            .isThrownBy(() -> new LocalSpec(Optional.of(Path.of("relative")), Optional.of("pwsh")));

        assertThat(Terminals.class.getMethod("profiles").isDefault()).isTrue();
        assertThat(Terminals.class.getMethod("onProfilesChanged", Runnable.class).isDefault()).isTrue();
        Terminals older = (Terminals) java.lang.reflect.Proxy.newProxyInstance(Terminals.class.getClassLoader(), new Class<?>[]{Terminals.class},
            (proxy, method, args) -> method.isDefault() ? java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args) : null);
        assertThat(older.profiles()).as("a host that detects nothing").isEmpty();
        var heard = new java.util.concurrent.atomic.AtomicInteger();
        older.onProfilesChanged(heard::incrementAndGet).close();
        assertThat(heard).hasValue(0);
        assertThatNullPointerException().isThrownBy(() -> older.onProfilesChanged(null));
    }
```

In `PaletteValuesTest.java` and `AppearanceContractTest.java`, change `assertThat(JasperSdk.VERSION).isEqualTo("0.8.5");` to
`assertThat(JasperSdk.VERSION).isEqualTo("0.8.6");` (one line in each).

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests "dev.jasper.sdk.terminal.TerminalValuesTest" --tests "dev.jasper.sdk.palette.PaletteValuesTest" --tests "dev.jasper.sdk.ui.AppearanceContractTest"`
Expected: FAIL: compilation errors (`ShellProfileInfo`, `LocalSpec(Optional, Optional)`, `localProfile`, `ofProfile`, `profiles`, `onProfilesChanged` do not exist).

- [x] **Step 3: Write the implementation**

Create `ShellProfileInfo.java`:

```java
package dev.jasper.sdk.terminal;

import java.util.Objects;
import java.util.Optional;

/**
 * A shell Jasper detected and can start in a local pane, as {@link Terminals#profiles()} lists it. Open one with
 * {@link OpenRequest#localProfile(String)}.
 *
 * @param id the stable id to open it by, such as {@code pwsh} or {@code wsl:Ubuntu}
 * @param name the display name, such as {@code Ubuntu}
 * @param kind what sort of shell it is
 * @param detail a short qualifier to show beside the name; for a WSL distribution {@code WSL 1} or {@code WSL 2}
 * @since 0.8.6
 */
public record ShellProfileInfo(String id, String name, Kind kind, Optional<String> detail) {
    /** What sort of shell a profile starts. */
    public enum Kind {
        /** The platform's login shell, which Jasper starts when nothing else is configured (macOS and Linux, id {@code login}). */
        LOGIN_SHELL,
        /** A shell installed on this machine, such as PowerShell, PowerShell 7 or Command Prompt. */
        LOCAL_SHELL,
        /** A Windows Subsystem for Linux distribution. */
        WSL,
        /** The program and arguments the user configured in Jasper's settings. */
        CUSTOM
    }

    /** Rejects nulls and a blank id or name. */
    public ShellProfileInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(detail, "detail");
        if (id.isBlank()) throw new IllegalArgumentException("A shell profile needs an id");
        if (name.isBlank()) throw new IllegalArgumentException("A shell profile needs a name");
    }
}
```

Replace `LocalSpec.java` with:

```java
package dev.jasper.sdk.terminal;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A local terminal to open: the user's configured shell, or one of the shell profiles Jasper detected.
 *
 * @param workingDirectory where it starts; empty means where Jasper's own New Tab would start
 * @param profile the id of a profile from {@link Terminals#profiles()} (since 0.8.6); empty means the shell Jasper's own
 *     New Tab or Split would start. An id the host does not list opens nothing.
 */
public record LocalSpec(Optional<Path> workingDirectory, Optional<String> profile) {
    /** Rejects null, a relative directory and a blank profile id. */
    public LocalSpec {
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(profile, "profile");
        if (workingDirectory.isPresent() && !workingDirectory.get().isAbsolute())
            throw new IllegalArgumentException("A working directory must be absolute: " + workingDirectory.get());
        if (profile.isPresent() && profile.get().isBlank()) throw new IllegalArgumentException("A profile id cannot be blank");
    }

    /**
     * The user's shell.
     *
     * @param workingDirectory where it starts; empty means where Jasper's own New Tab would start
     */
    public LocalSpec(Optional<Path> workingDirectory) { this(workingDirectory, Optional.empty()); }

    /**
     * A detected shell profile, where Jasper's own New Tab would start it.
     *
     * @param profileId an id from {@link Terminals#profiles()}
     * @return the spec
     * @since 0.8.6
     */
    public static LocalSpec ofProfile(String profileId) { return new LocalSpec(Optional.empty(), Optional.of(profileId)); }
}
```

In `OpenRequest.java`, after the `local(LocalSpec spec)` factory, add:

```java
    /**
     * A detected shell profile, where New Tab would start it. Needs {@code terminal.open}.
     *
     * @param profileId an id from {@link Terminals#profiles()}
     * @return the request
     * @since 0.8.6
     */
    static OpenRequest localProfile(String profileId) { return new Local(LocalSpec.ofProfile(profileId)); }
```

In `Terminals.java`, add `import dev.jasper.sdk.Subscription;` after `package dev.jasper.sdk.terminal;` (before
`import java.util.List;`), and add at the end of the interface, after `split`:

```java

    /**
     * The shells Jasper detected for local panes, in the order its settings list them: on Windows PowerShell,
     * PowerShell 7, Command Prompt and each WSL distribution, elsewhere the login shell, and the user's custom
     * command when one is configured. Needs {@code terminal.open}. UI thread only.
     *
     * @return the profiles; empty when the host lists none, or has not finished detecting them
     * @since 0.8.6
     */
    default List<ShellProfileInfo> profiles() { return List.of(); }

    /**
     * Calls the listener on the UI thread after {@link #profiles()} may have changed, for example when detection
     * finishes or the user refreshes it after installing a WSL distribution. Needs {@code terminal.open}. The
     * registration ends with the plugin.
     *
     * @param listener called with nothing; read {@link #profiles()} again
     * @return the registration
     * @since 0.8.6
     */
    default Subscription onProfilesChanged(Runnable listener) {
        java.util.Objects.requireNonNull(listener, "listener");
        return () -> { };
    }
```

Also change `openTab`'s and `split`'s Javadoc sentence "A local request needs {@code terminal.open}." to
"A local request needs {@code terminal.open}; one naming a profile the host does not list opens nothing." and their
`@return` lines to "the new tab's pane, or empty when the window is gone or the profile is unknown" and
"the new pane, or empty when the target is gone, its session has ended or the profile is unknown".

In `JasperSdk.java`, change `VERSION = "0.8.5"` to `VERSION = "0.8.6"`.

- [x] **Step 4: Run the SDK check**

Run: `./gradlew :jasper-sdk:check`
Expected: BUILD SUCCESSFUL (tests, Javadoc doclint and `verifySdkArchitecture`).

Run: `./gradlew :jasper-app:compileJava :jasper-sdk-testkit:compileJava`
Expected: BUILD SUCCESSFUL (the default methods keep both implementers compiling).

- [x] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-sdk
git commit -m "Add SDK 0.8.6 shell profiles to Terminals and LocalSpec

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: The app lists the catalog's profiles to plugins and opens only known ones

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedTerminals.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/package-info.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/plugins/TestShellProfiles.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/TerminalFixture.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`, `HostedPaletteTest.java` (the new `Environment` argument)
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/HostedTerminalsTest.java` (two new tests)

**Interfaces:**
- Produces: `public record PluginRuntime.ShellProfiles(Supplier<List<ShellProfile>> current, Function<Runnable,
  dev.jasper.app.lifecycle.Subscription> onChanged)` with `ShellProfiles.NONE`; a 9-argument `PluginRuntime` constructor
  ending in `ShellProfiles profiles` (the 6- and 8-argument constructors kept, using `NONE`);
  `PluginHost.Environment(..., Consumer<Path> editor, PluginRuntime.ShellProfiles profiles)`;
  `HostedTerminals(..., HostedSessions sessions, PluginRuntime.ShellProfiles shellProfiles, Containment containment)`
  (the 7-argument constructor kept); `AppContractTest.host(..., List<Path> edited, PluginRuntime.ShellProfiles profiles)`;
  test-only `TestShellProfiles` with `set(List<ShellProfile>)`, `listeners()` and `source()`. `TerminalFixture.opened()`
  appends `|<profile>` to `tab|` and `split|` lines whose spec names a profile.
- Consumes: plan A's `ShellProfile`, `ShellProfile.Kind`, `ShellProfileCatalog.current()/onChanged(Runnable)` and
  `OpenSpec.Local(Optional<Path>, Optional<String>)` with its `profile()` accessor; Task 1's SDK types.

- [x] **Step 1: Write the failing tests**

Create `TestShellProfiles.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.launch.ShellProfile;
import dev.jasper.app.lifecycle.Subscription;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A scripted shell profile catalog: {@link #set} replaces the list and runs every listener on the caller's thread. */
final class TestShellProfiles {
    private volatile List<ShellProfile> current = List.of();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    void set(List<ShellProfile> profiles) {
        current = List.copyOf(profiles);
        for (Runnable listener : listeners) listener.run();
    }

    int listeners() { return listeners.size(); }

    PluginRuntime.ShellProfiles source() {
        return new PluginRuntime.ShellProfiles(() -> current, listener -> {
            Runnable entry = listener::run;
            listeners.add(entry);
            return new Subscription(() -> listeners.remove(entry));
        });
    }
}
```

In `TerminalFixture.java`, in the split callback replace

```java
                        opened.add("split|" + pane.id + "|" + axis + "|" + local.directory().map(Path::toString).orElse("-"));
```

with

```java
                        opened.add("split|" + pane.id + "|" + axis + "|" + local.directory().map(Path::toString).orElse("-")
                            + local.profile().map(profile -> "|" + profile).orElse(""));
```

and in `addWindow` replace

```java
                    opened.add("tab|" + window.id + "|" + local.directory().map(Path::toString).orElse("-"));
```

with

```java
                    opened.add("tab|" + window.id + "|" + local.directory().map(Path::toString).orElse("-")
                        + local.profile().map(profile -> "|" + profile).orElse(""));
```

In `HostedTerminalsTest.java`, add the imports `import dev.jasper.app.launch.ShellProfile;` (before
`import dev.jasper.app.testsupport.EdtTestExtension;`) and `import dev.jasper.sdk.terminal.ShellProfileInfo;` (after
`import dev.jasper.sdk.terminal.SessionState;`), then add inside the class, after the `terminals(...)` helper:

```java
    private final TestShellProfiles shells = new TestShellProfiles();

    private HostedTerminals profiled(String... capabilities) {
        return new HostedTerminals("dev.x.tool", new CapabilityGate("dev.x.tool", Set.of(capabilities)), fixture.registry, posted::add,
            onUi::get, open::get, new HostedSessions("dev.x.tool", new Containment(() -> true), Runnable::run, id -> null),
            shells.source(), new Containment(() -> true));
    }

    static final ShellProfile LOGIN = new ShellProfile("login", "Login shell", ShellProfile.Kind.LOGIN_SHELL, Optional.empty(),
        List.of("/bin/zsh", "-l"));
    static final ShellProfile UBUNTU = new ShellProfile("wsl:Ubuntu", "Ubuntu", ShellProfile.Kind.WSL, Optional.of("WSL 2"),
        List.of("wsl.exe", "-d", "Ubuntu"));

    @Test void profilesMirrorTheCatalogAndTheirListenersCloseWithThePlugin() {
        shells.set(List.of(LOGIN, UBUNTU));
        HostedTerminals terminals = profiled(Capabilities.TERMINAL_OPEN);
        assertThat(terminals.profiles()).containsExactly(
            new ShellProfileInfo("login", "Login shell", ShellProfileInfo.Kind.LOGIN_SHELL, Optional.empty()),
            new ShellProfileInfo("wsl:Ubuntu", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.of("WSL 2")));
        assertThatThrownBy(() -> profiled().profiles()).isInstanceOf(MissingCapabilityException.class);
        assertThatThrownBy(() -> profiled().onProfilesChanged(() -> { })).isInstanceOf(MissingCapabilityException.class);
        List<String> heard = new ArrayList<>();
        dev.jasper.sdk.Subscription first = terminals.onProfilesChanged(() -> heard.add("first"));
        terminals.onProfilesChanged(() -> { throw new IllegalStateException("a plugin bug"); });
        terminals.onProfilesChanged(() -> heard.add("second"));
        shells.set(List.of(UBUNTU));
        assertThat(heard).as("a throwing listener is contained").containsExactly("first", "second");
        first.close();
        first.close();
        shells.set(List.of(LOGIN));
        assertThat(heard).containsExactly("first", "second", "second");
        assertThat(shells.listeners()).isEqualTo(2);
        terminals.closeAll();
        assertThat(shells.listeners()).as("closed with the plugin").isZero();
        shells.set(List.of());
        assertThat(heard).hasSize(3);
    }

    @Test void aNamedProfileOpensOnlyWhenTheCatalogListsIt() {
        shells.set(List.of(UBUNTU));
        HostedTerminals terminals = profiled(Capabilities.TERMINAL_OPEN);
        UUID window = fixture.addWindow(), tab = fixture.addTab(window, "build"), only = fixture.addPane(tab, "make", Path.of("/src"));
        assertThat(terminals.openTab(terminals.windowHandle(window), OpenRequest.localProfile("wsl:Ubuntu"))).isPresent();
        assertThat(terminals.openTab(terminals.windowHandle(window), OpenRequest.localProfile("wsl:Debian"))).isEmpty();
        assertThat(terminals.split(terminals.paneHandle(only), Direction.RIGHT, OpenRequest.localProfile("pwsh"))).isEmpty();
        assertThat(terminals.split(terminals.paneHandle(only), Direction.DOWN, OpenRequest.local())).as("the default needs no profile").isPresent();
        assertThat(fixture.opened()).containsExactly("tab|" + window + "|-|wsl:Ubuntu", "split|" + only + "|DOWN|-");
    }
```

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.plugins.HostedTerminalsTest"`
Expected: FAIL: compilation errors (`PluginRuntime.ShellProfiles` and the 9-argument `HostedTerminals` constructor do not exist).

- [x] **Step 3: Write the implementation**

In `PluginRuntime.java`, add the imports `import dev.jasper.app.launch.ShellProfile;` (after
`import dev.jasper.app.contributions.Contributions;`), `import java.util.function.Function;` and
`import java.util.function.Supplier;` (after `import java.util.function.Consumer;`). After the `SettingsReports` record add:

```java

    /**
     * The shell profiles the application detected, as plugins see them through {@code Terminals.profiles()}. Both
     * functions are called on the UI thread.
     *
     * @param current the profiles now, in display order
     * @param onChanged registers a listener the catalog calls on the UI thread after a refresh; closing the result removes it
     */
    public record ShellProfiles(Supplier<List<ShellProfile>> current, Function<Runnable, dev.jasper.app.lifecycle.Subscription> onChanged) {
        /** No profiles and no changes, for a runtime built without the catalog. */
        public static final ShellProfiles NONE = new ShellProfiles(List::of, listener -> new dev.jasper.app.lifecycle.Subscription(() -> { }));

        /** Rejects nulls. */
        public ShellProfiles {
            Objects.requireNonNull(current, "current");
            Objects.requireNonNull(onChanged, "onChanged");
        }
    }
```

Add the field `private final ShellProfiles profiles;` after `private final Consumer<Path> editor;`. Replace the
8-argument constructor (its Javadoc stays) and add a 9-argument one after it:

```java
    public PluginRuntime(Options options, ActivityNotifier notifier, SettingsReports reports,
                         Contributions contributions, AuxiliaryWindows windows, TerminalRegistry terminals,
                         Consumer<String> notice, Consumer<Path> editor) {
        this(options, notifier, reports, contributions, windows, terminals, notice, editor, ShellProfiles.NONE);
    }

    /**
     * As the eight-argument constructor, plus the shell profiles plugins may list and open.
     *
     * @param options locations and mode
     * @param notifier receives plugin activities for Buddy
     * @param reports receives plugin complaints about their settings, and clears them per file
     * @param contributions the application-wide model that plugin chrome contributions are written to
     * @param windows builds plugin windows and dialogs
     * @param terminals the application-wide directory of terminal windows, tabs and panes
     * @param notice shows a plugin's error notice to the user, on the UI thread
     * @param editor opens a file in the user's editor, off the UI thread; may throw
     * @param profiles the detected shell profiles
     */
    public PluginRuntime(Options options, ActivityNotifier notifier, SettingsReports reports,
                         Contributions contributions, AuxiliaryWindows windows, TerminalRegistry terminals,
                         Consumer<String> notice, Consumer<Path> editor, ShellProfiles profiles) {
        this.notice = Objects.requireNonNull(notice);
        this.editor = Objects.requireNonNull(editor);
        this.profiles = Objects.requireNonNull(profiles);
        this.options = Objects.requireNonNull(options);
        this.notifier = Objects.requireNonNull(notifier);
        this.reports = Objects.requireNonNull(reports);
        this.settingsFiles = new PluginSettingsFiles(options.userDirectory(), diagnostic -> onUi(() -> reports.report().accept(diagnostic)));
        this.contributions = Objects.requireNonNull(contributions);
        this.windows = Objects.requireNonNull(windows);
        this.terminals = Objects.requireNonNull(terminals);
    }
```

In `start`, the `new PluginHost.Environment(...)` call ends with `windows, terminals, notice, editor));`; change it to
`windows, terminals, notice, editor, profiles));`.

In `PluginHost.java`, change the `Environment` record's last line from
`AuxiliaryWindows windows, TerminalRegistry terminals, Consumer<String> notice, Consumer<Path> editor) { }` to
`AuxiliaryWindows windows, TerminalRegistry terminals, Consumer<String> notice, Consumer<Path> editor,
PluginRuntime.ShellProfiles profiles) { }`.

In `HostedContext.java`, replace

```java
        this.terminals = new HostedTerminals(id, gate, host.environment.terminals(), host.environment.ui(), host.environment.onUi(),
            () -> state != State.CLOSED, sessions);
```

with

```java
        this.terminals = new HostedTerminals(id, gate, host.environment.terminals(), host.environment.ui(), host.environment.onUi(),
            () -> state != State.CLOSED, sessions, host.environment.profiles(), host.containment);
```

In `HostedTerminals.java`:

Add imports: `import dev.jasper.app.launch.ShellProfile;` (first import), `import dev.jasper.sdk.Subscription;` (after
`import dev.jasper.sdk.Capabilities;`), `import dev.jasper.sdk.terminal.ShellProfileInfo;` (after
`import dev.jasper.sdk.terminal.SessionState;`), `import java.util.ArrayList;` (after `import java.nio.charset.StandardCharsets;`)
and `import java.util.concurrent.atomic.AtomicBoolean;` (after `import java.util.concurrent.CompletableFuture;`).

Replace the `sessions` field and the constructor:

```java
    private final HostedSessions sessions;

    HostedTerminals(String pluginId, CapabilityGate gate, TerminalRegistry registry, Consumer<Runnable> ui, BooleanSupplier onUi,
                    BooleanSupplier open, HostedSessions sessions) {
        this.pluginId = pluginId; this.gate = gate; this.registry = registry; this.ui = ui; this.onUi = onUi; this.open = open;
        this.sessions = sessions;
    }
```

with

```java
    private final HostedSessions sessions;
    private final PluginRuntime.ShellProfiles shellProfiles;
    private final Containment containment;
    /** Live {@link #onProfilesChanged} registrations, closed with the plugin. UI thread. */
    private final List<dev.jasper.app.lifecycle.Subscription> profileRegistrations = new ArrayList<>();

    /** A view that lists no shell profiles, for tests that do not need them. */
    HostedTerminals(String pluginId, CapabilityGate gate, TerminalRegistry registry, Consumer<Runnable> ui, BooleanSupplier onUi,
                    BooleanSupplier open, HostedSessions sessions) {
        this(pluginId, gate, registry, ui, onUi, open, sessions, PluginRuntime.ShellProfiles.NONE, new Containment(onUi));
    }

    HostedTerminals(String pluginId, CapabilityGate gate, TerminalRegistry registry, Consumer<Runnable> ui, BooleanSupplier onUi,
                    BooleanSupplier open, HostedSessions sessions, PluginRuntime.ShellProfiles shellProfiles, Containment containment) {
        this.pluginId = pluginId; this.gate = gate; this.registry = registry; this.ui = ui; this.onUi = onUi; this.open = open;
        this.sessions = sessions; this.shellProfiles = shellProfiles; this.containment = containment;
    }
```

Replace `openTab` and `split` with:

```java
    @Override public Optional<PaneHandle> openTab(WindowHandle window, OpenRequest request) {
        Objects.requireNonNull(window, "window");
        OpenSpec spec = spec(request);
        requireUi("openTab");
        if (!knownProfile(spec)) return Optional.empty();
        Optional<WindowEntry> target = registry.window(window.id());
        if (target.isEmpty()) return Optional.empty();
        gate.audit(capability(request), "opened a tab in window " + window.id());
        return target.get().openTab().apply(spec).map(Pane::new);
    }

    @Override public Optional<PaneHandle> split(PaneHandle target, Direction direction, OpenRequest request) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(direction, "direction");
        OpenSpec spec = spec(request);
        requireUi("split");
        if (!knownProfile(spec)) return Optional.empty();
        Optional<PaneEntry> entry = registry.pane(target.id());
        if (entry.isEmpty()) return Optional.empty();
        gate.audit(capability(request), "split pane " + target.id());
        return entry.get().split().apply(direction == Direction.RIGHT ? SplitAxis.RIGHT : SplitAxis.DOWN, spec)
            .map(Pane::new);
    }
```

In `spec(...)`, replace `yield new OpenSpec.Local(local.spec().workingDirectory());` with
`yield new OpenSpec.Local(local.spec().workingDirectory(), local.spec().profile());`.

Insert before `/** A handle that finds its tab lazily, for callers that are not on the UI thread. */`:

```java
    @Override public List<ShellProfileInfo> profiles() {
        gate.require(Capabilities.TERMINAL_OPEN);
        requireUi("profiles");
        return shellProfiles.current().get().stream().map(HostedTerminals::profileInfo).toList();
    }

    @Override public Subscription onProfilesChanged(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        gate.require(Capabilities.TERMINAL_OPEN);
        requireUi("onProfilesChanged");
        dev.jasper.app.lifecycle.Subscription registration = shellProfiles.onChanged().apply(() -> {
            if (open.getAsBoolean()) containment.run(pluginId, "shell profile listener", listener);
        });
        profileRegistrations.add(registration);
        var closed = new AtomicBoolean();
        Runnable removal = () -> { profileRegistrations.remove(registration); registration.close(); };
        return () -> {
            if (!closed.compareAndSet(false, true)) return;
            if (onUi.getAsBoolean()) removal.run(); else ui.accept(removal);
        };
    }

    /** The kinds map one to one. */
    private static ShellProfileInfo profileInfo(ShellProfile profile) {
        ShellProfileInfo.Kind kind = switch (profile.kind()) {
            case LOGIN_SHELL -> ShellProfileInfo.Kind.LOGIN_SHELL;
            case LOCAL_SHELL -> ShellProfileInfo.Kind.LOCAL_SHELL;
            case WSL -> ShellProfileInfo.Kind.WSL;
            case CUSTOM -> ShellProfileInfo.Kind.CUSTOM;
        };
        return new ShellProfileInfo(profile.id(), profile.name(), kind, profile.detail());
    }

    /** A named profile must be one the catalog lists; otherwise nothing opens and the plugin's mistake is logged. */
    private boolean knownProfile(OpenSpec spec) {
        if (!(spec instanceof OpenSpec.Local local) || local.profile().isEmpty()) return true;
        String id = local.profile().get();
        if (shellProfiles.current().get().stream().anyMatch(profile -> profile.id().equals(id))) return true;
        LOG.log(System.Logger.Level.WARNING, "Plugin " + pluginId + " asked for the unknown shell profile " + id + "; nothing opened");
        return false;
    }

```

Replace `closeAll`:

```java
    /** The plugin is stopping. */
    void closeAll() { sessions.cancelAll(); }
```

with

```java
    /** The plugin is stopping: cancel its sessions and drop its profile listeners. UI thread. */
    void closeAll() {
        sessions.cancelAll();
        var registrations = List.copyOf(profileRegistrations);
        profileRegistrations.clear();
        registrations.forEach(dev.jasper.app.lifecycle.Subscription::close);
    }
```

In `plugins/package-info.java`, add `dev.jasper.app.launch, ` after `dev.jasper.app.contributions, ` in the
"Allowed outgoing Jasper dependencies" list.

In `JasperApplication.startPlugins`, the `new PluginRuntime(...)` call ends with
`this::notice, new dev.jasper.app.platform.ConfigEditor()::open);`. Replace that line with:

```java
            this::notice, new dev.jasper.app.platform.ConfigEditor()::open,
            new PluginRuntime.ShellProfiles(shellProfiles::current, shellProfiles::onChanged));
```

`shellProfiles` is plan A's `ShellProfileCatalog` field in `JasperApplication`; check with
`grep -n "ShellProfileCatalog" jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` and use its
actual name. It must be created before `startPlugins` runs; if plan A creates it later, move its creation earlier and
record the deviation.

In `AppContractTest.java`, replace the 9-argument `host(...)` helper with:

```java
    static PluginHost host(Path data, Map<String, Map<String, Object>> tables, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark, AuxiliaryWindows auxiliary,
                           TerminalRegistry terminals, List<String> notices, List<Path> edited) {
        return host(data, tables, drainGrace, contributions, dark, auxiliary, terminals, notices, edited, PluginRuntime.ShellProfiles.NONE);
    }

    static PluginHost host(Path data, Map<String, Map<String, Object>> tables, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark, AuxiliaryWindows auxiliary,
                           TerminalRegistry terminals, List<String> notices, List<Path> edited, PluginRuntime.ShellProfiles profiles) {
        return onEdtValue(() -> new PluginHost(new PluginHost.Environment(SwingUtilities::invokeLater,
            SwingUtilities::isEventDispatchThread, data::resolve, id -> tables.getOrDefault(id, Map.of()), id -> data.resolve(id + ".toml"),
            (id, path) -> java.util.Optional.empty(), diagnostic -> { }, drainGrace, contributions, dark::get, auxiliary, terminals, notices::add, edited::add,
            profiles)));
    }
```

In `HostedPaletteTest.java`, change `notices::add, edited::add)));` (the end of the `Environment` construction) to
`notices::add, edited::add, PluginRuntime.ShellProfiles.NONE)));`.

- [x] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.plugins.*"`
Expected: BUILD SUCCESSFUL; the two new `HostedTerminalsTest` tests pass and nothing else changed.

Run: `./gradlew verifyApplicationArchitecture`
Expected: BUILD SUCCESSFUL (the new edge `plugins -> launch` is acyclic; SDK types stay in `dev.jasper.app.plugins`).

- [x] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app
git commit -m "Give plugins the detected shell profiles and refuse unknown ones

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: The testkit scripts profiles; the contract suite pins known, unknown and default

**Files:**
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeWorkspace.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeTerminals.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginContext.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java`
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java`
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java`
- Modify: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`
- Test: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeTerminalsTest.java`

**Interfaces:**
- Produces: `public void FakePluginHost.setShellProfiles(List<ShellProfileInfo>)`; `FakePluginHost.openRequests()` lines
  gain `|<profile id>` when a request names one; `ContractHarness.setShellProfiles(List<ShellProfileInfo>)`;
  contract test `shellProfilesAreListedWatchedAndOpenedByIdOnly`.
- Consumes: Task 1 SDK, Task 2 `TestShellProfiles` and the 10-argument `AppContractTest.host`.

- [x] **Step 1: Write the failing tests**

Add to `FakeTerminalsTest.java` the imports `import dev.jasper.sdk.terminal.OpenRequest;` and
`import dev.jasper.sdk.terminal.ShellProfileInfo;` (after `import dev.jasper.sdk.terminal.SessionState;`) and
`import java.util.List;` (after `import java.nio.file.Path;`), then this test inside the class:

```java
    @Test void profilesAreScriptedAndOnlyListedOnesOpen() {
        try (var host = new FakePluginHost()) {
            var ubuntu = new ShellProfileInfo("wsl:Ubuntu", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.of("WSL 2"));
            UUID window = host.addTerminalWindow();
            var heard = new java.util.ArrayList<String>();
            var context = host.start(new PluginInfo("dev.x.tool", "Tool", "1.0.0", Set.of(Capabilities.TERMINAL_OPEN)), Set.of(), Set.of(),
                plugin -> plugin.terminals().onProfilesChanged(() -> heard.add("changed")));
            assertThat(context.terminals().profiles()).isEmpty();
            host.setShellProfiles(List.of(ubuntu));
            assertThat(heard).containsExactly("changed");
            assertThat(context.terminals().profiles()).containsExactly(ubuntu);
            var handle = context.terminals().window(window).orElseThrow();
            assertThat(context.terminals().openTab(handle, OpenRequest.localProfile("wsl:Ubuntu"))).isPresent();
            assertThat(context.terminals().openTab(handle, OpenRequest.localProfile("wsl:Debian"))).isEmpty();
            assertThat(host.openRequests()).containsExactly("tab|" + window + "|-|wsl:Ubuntu");
            host.stopAll();
            host.setShellProfiles(List.of());
            assertThat(heard).as("closed with the plugin").hasSize(1);
        }
    }
```

In `ContractHarness.java`, replace

```java
    /** What plugins asked to open: {@code tab|<window id>|<directory or ->} or {@code split|<pane id>|<RIGHT or DOWN>|<directory or ->}. */
    List<String> openRequests();
```

with

```java
    /**
     * What plugins asked to open: {@code tab|<window id>|<directory or ->} or {@code split|<pane id>|<RIGHT or DOWN>|<directory or ->},
     * each followed by {@code |<profile id>} when the request named a shell profile.
     */
    List<String> openRequests();

    /** UI thread: replaces the shell profiles the application detected and runs every plugin's change listener. */
    void setShellProfiles(List<dev.jasper.sdk.terminal.ShellProfileInfo> profiles);
```

In `PluginContractTest.java`, add `import dev.jasper.sdk.terminal.ShellProfileInfo;` after
`import dev.jasper.sdk.terminal.SessionSpec;`, and add after `openingTabsAndSplitsNeedsTheCapabilityAndReturnsTheNewPane`:

```java
    @Test void shellProfilesAreListedWatchedAndOpenedByIdOnly() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build"), only = h.addTerminalPane(tab, "make", Path.of("/src"));
        var pwsh = new ShellProfileInfo("pwsh", "PowerShell 7", ShellProfileInfo.Kind.LOCAL_SHELL, Optional.empty());
        var ubuntu = new ShellProfileInfo("wsl:Ubuntu", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.of("WSL 2"));
        h.setShellProfiles(List.of(pwsh));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OPEN), Set.of(), Set.of(), alpha::set);
        var bare = new AtomicReference<PluginContext>();
        h.start(info("test.bare"), Set.of(), Set.of(), bare::set);
        var changes = new AtomicInteger();
        var subscription = new AtomicReference<Subscription>();
        h.ui(() -> {
            assertThat(alpha.get().terminals().profiles()).containsExactly(pwsh);
            subscription.set(alpha.get().terminals().onProfilesChanged(changes::incrementAndGet));
            assertThatThrownBy(() -> bare.get().terminals().profiles()).isInstanceOf(MissingCapabilityException.class);
        });
        h.setShellProfiles(List.of(pwsh, ubuntu));
        h.flush();
        assertThat(changes.get()).isEqualTo(1);
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            assertThat(terminals.profiles()).containsExactly(pwsh, ubuntu);
            assertThat(terminals.openTab(terminals.window(window).orElseThrow(), OpenRequest.localProfile("wsl:Ubuntu"))).as("known").isPresent();
            assertThat(terminals.openTab(terminals.window(window).orElseThrow(), OpenRequest.localProfile("wsl:Debian"))).as("unknown").isEmpty();
            assertThat(terminals.split(terminals.pane(only).orElseThrow(), Direction.RIGHT, OpenRequest.localProfile("cmd"))).as("unknown").isEmpty();
            assertThat(terminals.split(terminals.pane(only).orElseThrow(), Direction.DOWN, OpenRequest.local())).as("the default").isPresent();
        });
        assertThat(h.openRequests()).containsExactly("tab|" + window + "|-|wsl:Ubuntu", "split|" + only + "|DOWN|-");
        subscription.get().close();
        h.setShellProfiles(List.of(pwsh));
        h.flush();
        assertThat(changes.get()).as("a closed registration hears nothing").isEqualTo(1);
        h.ui(() -> alpha.get().terminals().onProfilesChanged(changes::incrementAndGet));
        h.stopAll();
        h.setShellProfiles(List.of());
        h.flush();
        assertThat(changes.get()).as("a stopped plugin hears nothing").isEqualTo(1);
    }
```

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:test --tests "dev.jasper.sdk.testing.FakeTerminalsTest"`
Expected: FAIL: compilation errors (`setShellProfiles` does not exist on `FakePluginHost` or `ContractHarness`).

- [x] **Step 3: Write the implementation**

In `FakeWorkspace.java`, add `import dev.jasper.sdk.terminal.ShellProfileInfo;` after
`import dev.jasper.sdk.terminal.PaneInfo;`, and after `final List<String> openRequests = new ArrayList<>();` add:

```java
    /** The shell profiles the test says the application detected. */
    List<ShellProfileInfo> profiles = List.of();
    /** Every live {@code onProfilesChanged} listener, of every plugin. */
    final List<Runnable> profileListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
```

In `FakePluginContext.java`, after `closeOwned()` add:

```java

    /** Closes the registration when the plugin stops, like everything else the context handed out. */
    void own(Subscription subscription) {
        synchronized (owned) { owned.add(subscription); }
    }
```

In `FakePluginHost.java`, replace the `openRequests()` Javadoc return line
`@return lines of the form {@code tab|<window id>|<directory or ->}, {@code split|<pane id>|<RIGHT or DOWN>|<directory or ->} or {@code front|<window id>}`
with
`@return lines of the form {@code tab|<window id>|<directory or ->}, {@code split|<pane id>|<RIGHT or DOWN>|<directory or ->} (each followed by {@code |<profile id>} when the request named a shell profile) or {@code front|<window id>}`,
and after `openRequests()` add:

```java

    /**
     * Replaces the shell profiles the application detected, as {@code Terminals.profiles()} returns them, and runs every
     * plugin's {@code onProfilesChanged} listener at once. A throwing listener is recorded in {@link #failures()}.
     *
     * @param profiles the profiles, in display order
     */
    public void setShellProfiles(List<dev.jasper.sdk.terminal.ShellProfileInfo> profiles) {
        workspace.profiles = List.copyOf(profiles);
        for (Runnable listener : workspace.profileListeners) {
            try { listener.run(); }
            catch (RuntimeException failure) { failures.add("profiles listener: " + failure); }
        }
    }
```

In `FakeTerminals.java`, add `import dev.jasper.sdk.Subscription;` after `import dev.jasper.sdk.MissingCapabilityException;`
and `import dev.jasper.sdk.terminal.ShellProfileInfo;` after `import dev.jasper.sdk.terminal.PaneInfo;`. Replace `openTab`
and `split` with:

```java
    @Override public Optional<PaneHandle> openTab(WindowHandle window, OpenRequest request) {
        Objects.requireNonNull(window, "window");
        requireFor(request);
        context.requireOpen();
        if (!knownProfile(request)) return Optional.empty();
        return workspace.window(window.id()).map(target -> switch (request) {
            case OpenRequest.Local local -> {
                workspace.openRequests.add("tab|" + target.id + "|" + local.spec().workingDirectory().map(Path::toString).orElse("-") + profile(local));
                FakeWorkspace.Tab tab = workspace.addTab(target, "opened");
                FakeWorkspace.Pane pane = workspace.addPane(tab, openedInfo(local));
                yield new Pane(pane.id, tab.id);
            }
            case OpenRequest.Session session -> {
                workspace.openRequests.add("session-tab|" + target.id + "|" + session.spec().title());
                FakeWorkspace.Tab tab = workspace.addTab(target, session.spec().title());
                FakeWorkspace.Pane pane = workspace.addPane(tab, sessionInfo(session));
                workspace.sessions.connect(context, pane, session.spec());
                yield new Pane(pane.id, tab.id);
            }
        });
    }

    @Override public Optional<PaneHandle> split(PaneHandle target, Direction direction, OpenRequest request) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(direction, "direction");
        requireFor(request);
        context.requireOpen();
        if (!knownProfile(request)) return Optional.empty();
        return workspace.pane(target.id()).map(model -> {
            FakeWorkspace.Pane pane = switch (request) {
                case OpenRequest.Local local -> {
                    workspace.openRequests.add("split|" + model.id + "|" + direction + "|" + local.spec().workingDirectory().map(Path::toString).orElse("-")
                        + profile(local));
                    yield workspace.addPane(model.tab, openedInfo(local));
                }
                case OpenRequest.Session session -> {
                    workspace.openRequests.add("session-split|" + model.id + "|" + direction + "|" + session.spec().title());
                    FakeWorkspace.Pane created = workspace.addPane(model.tab, sessionInfo(session));
                    workspace.sessions.connect(context, created, session.spec());
                    yield created;
                }
            };
            workspace.focus(pane);
            return new Pane(pane.id, model.tab.id);
        });
    }

    @Override public List<ShellProfileInfo> profiles() {
        require(Capabilities.TERMINAL_OPEN);
        context.requireOpen();
        return workspace.profiles;
    }

    @Override public Subscription onProfilesChanged(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        require(Capabilities.TERMINAL_OPEN);
        context.requireOpen();
        Runnable entry = listener::run;
        workspace.profileListeners.add(entry);
        Subscription subscription = () -> workspace.profileListeners.remove(entry);
        context.own(subscription);
        return subscription;
    }

    /** The default shell, or a profile the test listed; anything else opens nothing, as in the application. */
    private boolean knownProfile(OpenRequest request) {
        if (!(request instanceof OpenRequest.Local local) || local.spec().profile().isEmpty()) return true;
        String id = local.spec().profile().get();
        if (workspace.profiles.stream().anyMatch(profile -> profile.id().equals(id))) return true;
        context.log().log(System.Logger.Level.WARNING, "Unknown shell profile " + id + "; nothing opened");
        return false;
    }

    private static String profile(OpenRequest.Local local) { return local.spec().profile().map(id -> "|" + id).orElse(""); }
```

In `FakeContractTest.java`, add inside the anonymous harness (after `openRequests()`):

```java
            @Override public void setShellProfiles(List<dev.jasper.sdk.terminal.ShellProfileInfo> profiles) { host.setShellProfiles(profiles); }
```

In `AppContractTest.java`:
- In `newHarness()`, after `List<Path> edited = new CopyOnWriteArrayList<>();` add `TestShellProfiles shells = new TestShellProfiles();`
  and change `PluginHost host = host(data, Map.of(), Duration.ofMillis(200), contributions, dark, auxiliary, terminalFixture.registry, notices, edited);`
  to `PluginHost host = host(data, Map.of(), Duration.ofMillis(200), contributions, dark, auxiliary, terminalFixture.registry, notices, edited, shells.source());`.
- Inside the anonymous harness, after `openRequests()`, add:

```java
            @Override public void setShellProfiles(List<dev.jasper.sdk.terminal.ShellProfileInfo> profiles) {
                onEdt(() -> shells.set(profiles.stream().map(AppContractTest::appProfile).toList()));
            }
```

- Add this static helper after `headlessWindows(UiState state)`:

```java
    /** The application's value for an SDK profile; the command is never run by the contract fixture. */
    static dev.jasper.app.launch.ShellProfile appProfile(dev.jasper.sdk.terminal.ShellProfileInfo info) {
        var kind = switch (info.kind()) {
            case LOGIN_SHELL -> dev.jasper.app.launch.ShellProfile.Kind.LOGIN_SHELL;
            case LOCAL_SHELL -> dev.jasper.app.launch.ShellProfile.Kind.LOCAL_SHELL;
            case WSL -> dev.jasper.app.launch.ShellProfile.Kind.WSL;
            case CUSTOM -> dev.jasper.app.launch.ShellProfile.Kind.CUSTOM;
        };
        return new dev.jasper.app.launch.ShellProfile(info.id(), info.name(), kind, info.detail(), List.of("sh"));
    }
```

- [x] **Step 4: Run the tests**

Run: `./gradlew :jasper-sdk-testkit:test :jasper-app:test --tests "dev.jasper.app.plugins.*"`
Expected: BUILD SUCCESSFUL; `FakeContractTest` and `AppContractTest` both run `shellProfilesAreListedWatchedAndOpenedByIdOnly`.

- [x] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-sdk-testkit jasper-app/src/test
git commit -m "Script shell profiles in the testkit and pin them in the contract suite

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Remote's row model gains a WSL group of distributions

**Files:**
- Modify: `plugins/remote/src/main/resources/plugin.toml`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/HostRows.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/HostsPanel.java` (one switch case; Task 5 replaces the file)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/ui/HostRowsTest.java`

**Interfaces:**
- Produces: `HostRows.WSL = "WSL"`, `HostRows.WSL_KEY = "wsl"`; `record HostRows.Group(String name, int count, boolean collapsed,
  String key)` with the old 3-argument constructor (key = name) and `boolean wsl()`; `record HostRows.Distro(ShellProfileInfo
  profile, int sessions)`; `static boolean HostRows.matches(ShellProfileInfo, String)`; `static List<Row> HostRows.rows(List<RemoteHost>,
  String, Set<String>, String, List<Distro>)`.
- Consumes: Task 1's `ShellProfileInfo`.

- [x] **Step 1: Write the failing tests**

In `HostRowsTest.java`, add `import dev.jasper.sdk.terminal.ShellProfileInfo;` after `import dev.jasper.remote.hosts.RemoteHost;`,
replace `describe` with:

```java
    static String describe(HostRows.Row row) {
        return switch (row) {
            case HostRows.Error error -> error.message();
            case HostRows.Group group -> group.name() + "(" + group.count() + ")" + (group.collapsed() ? "-" : "");
            case HostRows.Host host -> host.host().name() + (host.host().favorite() ? "*" : "");
            case HostRows.Distro distro -> distro.profile().name() + (distro.sessions() > 0 ? "@" + distro.sessions() : "");
        };
    }
```

and add inside the class:

```java
    static ShellProfileInfo distro(String name, String version) {
        return new ShellProfileInfo("wsl:" + name, name, ShellProfileInfo.Kind.WSL, Optional.of(version));
    }

    @Test void wslDistributionsLeadInTheirOwnGroupOnlyWhenPresent() {
        var distros = List.of(new HostRows.Distro(distro("Ubuntu", "WSL 1"), 0), new HostRows.Distro(distro("Debian", "WSL 2"), 2),
            new HostRows.Distro(new ShellProfileInfo("pwsh", "PowerShell 7", ShellProfileInfo.Kind.LOCAL_SHELL, Optional.empty()), 0));
        assertThat(HostRows.rows(hosts, "", Set.of(), HostRows.OTHER, distros)).extracting(HostRowsTest::describe)
            .containsExactly("WSL(2)", "Ubuntu", "Debian@2", "Production(2)", "api*", "web", "Staging(1)", "app", "Other(1)", "nas");
        assertThat(HostRows.rows(hosts, "", Set.of(HostRows.WSL_KEY), HostRows.OTHER, distros)).extracting(HostRowsTest::describe)
            .startsWith("WSL(2)-", "Production(2)");
        assertThat(HostRows.rows(hosts, "deb", Set.of(HostRows.WSL_KEY), HostRows.OTHER, distros)).extracting(HostRowsTest::describe)
            .as("a search reveals the collapsed group and matches names").containsExactly("WSL(1)", "Debian@2");
        assertThat(HostRows.rows(hosts, "wsl", Set.of(), HostRows.OTHER, distros)).extracting(HostRowsTest::describe)
            .as("the group's name matches every distribution").containsExactly("WSL(2)", "Ubuntu", "Debian@2");
        assertThat(HostRows.rows(hosts, "", Set.of(), HostRows.OTHER, List.of())).isEqualTo(HostRows.rows(hosts, "", Set.of()));
        assertThat(HostRows.rows(hosts, "", Set.of(), HostRows.OTHER, distros).getFirst()).isInstanceOfSatisfying(HostRows.Group.class, group -> {
            assertThat(group.wsl()).isTrue();
            assertThat(group.key()).isEqualTo("wsl");
        });
        assertThat(new HostRows.Group("WSL", 1, false).wsl()).as("a host group that happens to be named WSL").isFalse();
        assertThat(new HostRows.Group("Prod", 1, true).key()).isEqualTo("Prod");
    }
```

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests "dev.jasper.remote.ui.HostRowsTest"`
Expected: FAIL: compilation errors (`HostRows.Distro`, `WSL`, `WSL_KEY`, `key()`, `wsl()` and the 5-argument `rows` do not exist).

- [x] **Step 3: Write the implementation**

Replace `HostRows.java` with:

```java
package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

/** The panel's list model: the WSL group first, then group headers with counts and hosts, favorites first, "Other" last. */
public final class HostRows {
    public static final String OTHER = "Other";
    /** The WSL group's title. */
    public static final String WSL = "WSL";
    /** The WSL group's entry in the collapsed set, as {@code panel-state.toml} stores it. */
    public static final String WSL_KEY = "wsl";

    public sealed interface Row { }

    /** A group header; {@code key} is its entry in the collapsed set: the name, or {@link #WSL_KEY} for the WSL group. */
    public record Group(String name, int count, boolean collapsed, String key) implements Row {
        public Group(String name, int count, boolean collapsed) { this(name, count, collapsed, name); }

        /** Whether this is the WSL group, not a host group that happens to share its name. */
        public boolean wsl() { return WSL.equals(name) && WSL_KEY.equals(key); }
    }

    public record Host(RemoteHost host) implements Row { }

    /** A WSL distribution the application detected, with the panes Remote opened for it that still run. Never saved. */
    public record Distro(ShellProfileInfo profile, int sessions) implements Row { }

    public record Error(String message) implements Row { }

    private HostRows() { }

    public static boolean matches(RemoteHost host, String query) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;
        return (host.name() + " " + host.hostname() + " " + host.username() + " " + host.group()).toLowerCase(Locale.ROOT).contains(q);
    }

    /** A distribution matches on its name or on the group's name. */
    public static boolean matches(ShellProfileInfo profile, String query) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;
        return (profile.name() + " " + WSL).toLowerCase(Locale.ROOT).contains(q);
    }

    /** Collapsed groups hide their hosts unless a search is active; the search never changes {@code collapsed}. */
    public static List<Row> rows(List<RemoteHost> hosts, String query, Set<String> collapsed) {
        return rows(hosts, query, collapsed, OTHER);
    }

    public static List<Row> rows(List<RemoteHost> hosts, String query, Set<String> collapsed, String defaultGroup) {
        boolean searching = !query.strip().isEmpty();
        var groups = new TreeMap<String, List<RemoteHost>>(Comparator.comparing((String name) -> name.equals(defaultGroup) ? 1 : 0).thenComparing(name -> name.toLowerCase(Locale.ROOT)));
        for (RemoteHost host : hosts) {
            if (!matches(host, query) && !(host.group().isEmpty() && defaultGroup.toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT)))) continue;
            groups.computeIfAbsent(host.group().isEmpty() ? defaultGroup : host.group(), name -> new ArrayList<>()).add(host);
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

    /**
     * As {@link #rows(List, String, Set, String)}, led by a "WSL" group of the matching WSL distributions in the order
     * given; other kinds of profile are ignored, and there is no group when no distribution matches.
     */
    public static List<Row> rows(List<RemoteHost> hosts, String query, Set<String> collapsed, String defaultGroup, List<Distro> distros) {
        boolean searching = !query.strip().isEmpty();
        var rows = new ArrayList<Row>();
        List<Distro> shown = distros.stream()
            .filter(row -> row.profile().kind() == ShellProfileInfo.Kind.WSL && matches(row.profile(), query)).toList();
        if (!shown.isEmpty()) {
            boolean hidden = !searching && collapsed.contains(WSL_KEY);
            rows.add(new Group(WSL, shown.size(), hidden, WSL_KEY));
            if (!hidden) rows.addAll(shown);
        }
        rows.addAll(rows(hosts, query, collapsed, defaultGroup));
        return List.copyOf(rows);
    }
}
```

In `HostsPanel.java`, in `activate(int index)`, after `case HostRows.Group group -> toggle(index);` add
`            case HostRows.Distro ignored -> { }` (Task 5 gives it behaviour; the switch must stay exhaustive).

In `plugin.toml`, change `version = "0.4.1"` to `version = "0.5.0"` and `sdk = ">=0.8.5, <0.9"` to
`sdk = ">=0.8.6, <0.9"`, and in `description` replace `a hosts panel and a palette scope.` with
`a Sessions panel that also lists WSL distributions, and a palette scope.`.

- [x] **Step 4: Run the tests**

Run: `./gradlew :jasper-plugin-remote:test --tests "dev.jasper.remote.ui.*"`
Expected: BUILD SUCCESSFUL.

- [x] **Step 5: Commit**

```bash
git branch --show-current
git add plugins/remote
git commit -m "Model a WSL group of distributions in Remote's host rows

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The panel becomes "Sessions" and shows, opens and remembers the WSL group

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/HostsPanel.java` (replace)
- Modify: `plugins/remote/src/test/java/dev/jasper/remote/ui/UiTestAccess.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/ui/HostsPanelTest.java`

**Interfaces:**
- Produces: `HostsPanel.title` (package field); `public void onDistros(Consumer<ShellProfileInfo> open, Consumer<ShellProfileInfo> openSplit)`;
  `public void setDistros(List<ShellProfileInfo> profiles, ToIntFunction<ShellProfileInfo> sessions)`;
  package-private `Optional<ShellProfileInfo> selectedDistro()` and `void selectDistro(String profileId)`;
  `UiTestAccess.activate(HostsPanel, int)`.
- Consumes: Task 4's `HostRows`.

- [x] **Step 1: Write the failing tests**

In `HostsPanelTest.java`, add `import dev.jasper.sdk.terminal.ShellProfileInfo;` after `import dev.jasper.remote.hosts.RemoteHost;`,
and add inside the class:

```java
    final ShellProfileInfo ubuntu = new ShellProfileInfo("wsl:Ubuntu", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.of("WSL 1"));
    final ShellProfileInfo debian = new ShellProfileInfo("wsl:Debian", "Debian", ShellProfileInfo.Kind.WSL, Optional.of("WSL 2"));
    final ShellProfileInfo pwsh = new ShellProfileInfo("pwsh", "PowerShell 7", ShellProfileInfo.Kind.LOCAL_SHELL, Optional.empty());

    HostsPanel withDistros(java.util.function.Function<dev.jasper.sdk.ui.BundledIcon, javax.swing.Icon> icons) {
        var panel = new HostsPanel(actions, icons);
        panel.onDistros(profile -> events.add("open " + profile.name()), profile -> events.add("split " + profile.name()));
        panel.setDistros(List.of(ubuntu, pwsh, debian), profile -> profile.equals(debian) ? 2 : 0);
        panel.setHosts(List.of(prod), Optional.empty());
        return panel;
    }

    static List<HostRows.Row> rows(HostsPanel panel) {
        var out = new ArrayList<HostRows.Row>();
        for (int i = 0; i < panel.list.getModel().getSize(); i++) out.add(panel.list.getModel().getElementAt(i));
        return out;
    }

    static java.awt.Component render(HostsPanel panel, int index) {
        return panel.list.getCellRenderer().getListCellRendererComponent(panel.list, panel.list.getModel().getElementAt(index), index, false, false);
    }

    @Test void theHeadingReadsSessionsAndThereIsNoWslGroupWithoutDistributions() {
        var panel = new HostsPanel(actions);
        assertThat(panel.title.getText()).isEqualTo("Sessions");
        panel.setDistros(List.of(pwsh), profile -> 0);
        assertThat(rows(panel)).isEmpty();
        assertThat(panel.empty.isVisible()).isTrue();
        assertThat(panel.empty.getText()).isEqualTo("No hosts yet \u2014 Add or Import from ~/.ssh/config");
        panel.setHosts(List.of(prod), Optional.empty());
        assertThat(rows(panel)).containsExactly(new HostRows.Group("Production", 1, false), new HostRows.Host(prod));
    }

    @Test void wslDistributionsComeFirstWithBadgesSessionsAndTheirOwnCard() {
        var panel = withDistros(name -> null);
        assertThat(rows(panel)).containsExactly(new HostRows.Group(HostRows.WSL, 2, false, HostRows.WSL_KEY),
            new HostRows.Distro(ubuntu, 0), new HostRows.Distro(debian, 2), new HostRows.Group("Production", 1, false), new HostRows.Host(prod));
        var header = (javax.swing.JLabel) render(panel, 0);
        assertThat(header.getText()).isEqualTo("WSL   2");
        assertThat(header.getToolTipText()).as("only the default host group can be renamed").isNull();
        var busy = render(panel, 2);
        assertThat(labels(busy)).contains("Debian", "WSL 2", "\u25CF", "2");
        assertThat(busy.getAccessibleContext().getAccessibleName()).isEqualTo("Debian, WSL 2, 2 sessions");
        assertThat(labels(render(panel, 1))).contains("Ubuntu", "WSL 1").doesNotContain("\u25CF");
        assertThat(panel.selectedDistro()).as("the first selectable row").contains(ubuntu);
        assertThat(panel.card.isVisible()).isTrue();
        assertThat(panel.cardName.getText()).isEqualTo("Ubuntu");
        assertThat(panel.cardAddress.getText()).isEqualTo("WSL 1");
        assertThat(panel.cardInfo.getText()).isEqualTo("wsl.exe -d Ubuntu");
        assertThat(panel.cardCredential.isVisible()).isFalse();
        assertThat(panel.edit.isVisible()).isFalse();
        assertThat(panel.connect.getText()).isEqualTo("Open");
        panel.connect.doClick();
        panel.activate(2);
        assertThat(events).containsExactly("open Ubuntu", "open Debian");
        panel.selectDistro("wsl:Debian");
        panel.setDistros(List.of(ubuntu, debian), profile -> 1);
        assertThat(panel.selectedDistro()).as("a refresh keeps the selection").contains(debian);
        panel.select(prod.id());
        assertThat(panel.selectedDistro()).isEmpty();
        assertThat(panel.connect.getText()).isEqualTo("Connect");
        assertThat(panel.edit.isVisible()).isTrue();
        assertThat(panel.cardCredential.isVisible()).isTrue();
    }

    @Test void distroMenusOfferOnlyOpeningAndTheWslGroupCollapsesUnderItsOwnKey() {
        var icons = new java.util.EnumMap<dev.jasper.sdk.ui.BundledIcon, javax.swing.Icon>(dev.jasper.sdk.ui.BundledIcon.class);
        for (var name : List.of(dev.jasper.sdk.ui.BundledIcon.NODES_FOLDER, dev.jasper.sdk.ui.BundledIcon.GENERAL_OPEN,
                dev.jasper.sdk.ui.BundledIcon.TERMINAL_TOOLWINDOW_TERMINAL))
            icons.put(name, new dev.jasper.sdk.testing.FakeBundledIcon(name));
        var panel = withDistros(icons::get);
        var row = (java.awt.Container) render(panel, 1);
        assertThat(((javax.swing.JLabel) row.getComponent(0)).getIcon()).isSameAs(icons.get(dev.jasper.sdk.ui.BundledIcon.TERMINAL_TOOLWINDOW_TERMINAL));
        javax.swing.JPopupMenu menu = panel.menuFor(1);
        assertThat(menu.getComponentCount()).isEqualTo(2);
        assertThat(((javax.swing.JMenuItem) menu.getComponent(0)).getText()).isEqualTo("Open in new tab");
        assertThat(((javax.swing.JMenuItem) menu.getComponent(1)).getText()).isEqualTo("Open in split");
        ((javax.swing.JMenuItem) menu.getComponent(0)).doClick();
        ((javax.swing.JMenuItem) menu.getComponent(1)).doClick();
        assertThat(events).containsExactly("open Ubuntu", "split Ubuntu");
        assertThat(panel.menuFor(0)).as("the WSL header offers no rename").isNull();
        int[] changes = {0};
        panel.onCollapsedChanged(() -> changes[0]++);
        panel.toggle(0);
        assertThat(changes[0]).isEqualTo(1);
        assertThat(panel.collapsed()).containsExactly(HostRows.WSL_KEY);
        assertThat(rows(panel)).startsWith(new HostRows.Group(HostRows.WSL, 2, true, HostRows.WSL_KEY), new HostRows.Group("Production", 1, false));
        assertThat(((javax.swing.JLabel) render(panel, 0)).getIcon()).isSameAs(icons.get(dev.jasper.sdk.ui.BundledIcon.NODES_FOLDER));
        panel.search.setText("deb");
        assertThat(rows(panel)).containsExactly(new HostRows.Group(HostRows.WSL, 1, false, HostRows.WSL_KEY), new HostRows.Distro(debian, 2));
        panel.search.setText("");
        panel.setDistros(List.of(), profile -> 0);
        assertThat(rows(panel)).containsExactly(new HostRows.Group("Production", 1, false), new HostRows.Host(prod));
    }
```

In `UiTestAccess.java`, after `public static void toggle(HostsPanel panel, int index) { panel.toggle(index); }` add:

```java
    public static void activate(HostsPanel panel, int index) { panel.activate(index); }
```

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests "dev.jasper.remote.ui.HostsPanelTest"`
Expected: FAIL: compilation errors (`title`, `onDistros`, `setDistros`, `selectedDistro`, `selectDistro` do not exist).

- [x] **Step 3: Write the implementation**

Replace `HostsPanel.java` with:

```java
package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import dev.jasper.sdk.ui.BundledIcon;
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
import java.util.function.ToIntFunction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
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

/**
 * The "Sessions" rail panel: search, the WSL distributions the application detected, grouped hosts, and the selected
 * row's card with Connect (a host) or Open (a distribution). One per window.
 */
public final class HostsPanel extends JPanel {
    public record Actions(Consumer<RemoteHost> connect, Consumer<RemoteHost> connectSplit, Consumer<Optional<RemoteHost>> edit, Consumer<RemoteHost> duplicate,
                          Consumer<RemoteHost> delete, BiConsumer<RemoteHost, Boolean> favorite, Runnable importConfig) { }

    final JTextField search = new JTextField();
    final JList<HostRows.Row> list;
    final JPanel card = new JPanel();
    final JLabel title = heading("Sessions");
    final JLabel cardName = heading(""), cardAddress = new JLabel(), cardCredential = new JLabel(), cardJump = new JLabel(), cardInfo = new JLabel();
    final JButton connect = new JButton("Connect"), edit = new JButton("Edit"), add = new JButton("+"), importButton = new JButton("Import");
    final JLabel empty = new JLabel("No hosts yet \u2014 Add or Import from ~/.ssh/config");
    private final Actions actions;
    private final Function<BundledIcon, javax.swing.Icon> icons;
    private final DefaultListModel<HostRows.Row> model = new DefaultListModel<>();
    private final Set<String> collapsed = new HashSet<>();
    private final List<Runnable> collapseListeners = new ArrayList<>();
    private List<RemoteHost> hosts = List.of();
    private Optional<String> error = Optional.empty();
    private Function<RemoteHost, String> credentialLabels = host -> "";
    private UUID selected;
    private String selectedDistro;
    private String defaultGroup = HostRows.OTHER;
    private Runnable renameDefaultGroup = () -> {};
    private Consumer<RemoteHost> activate;
    private Consumer<RemoteHost> browseFiles;
    private Function<RemoteHost, String> metadata = host -> "";
    private ToIntFunction<RemoteHost> sessionCount = host -> 0;
    private List<ShellProfileInfo> distros = List.of();
    private ToIntFunction<ShellProfileInfo> distroSessions = profile -> 0;
    private Consumer<ShellProfileInfo> openDistro = profile -> { };
    private Consumer<ShellProfileInfo> splitDistro = profile -> { };

    private static JLabel heading(String text) {
        return new JLabel(text) {
            @Override public void updateUI() {
                super.updateUI();
                setFont(javax.swing.UIManager.getFont("Label.font").deriveFont(Font.BOLD));
            }
        };
    }

    public HostsPanel(Actions actions) {
        this(actions, name -> javax.swing.UIManager.getIcon(name == BundledIcon.NODES_FOLDER ? "Tree.closedIcon" : "Tree.openIcon"));
    }

    public HostsPanel(Actions actions, Function<BundledIcon, javax.swing.Icon> icons) {
        super(new BorderLayout(0, 6));
        this.icons = java.util.Objects.requireNonNull(icons);
        this.actions = actions; this.activate = actions.connect();
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        var header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.add(title); header.add(Box.createHorizontalGlue()); header.add(importButton); header.add(Box.createHorizontalStrut(4)); header.add(add);
        var north = new JPanel(new BorderLayout(0, 6));
        north.add(header, BorderLayout.NORTH);
        search.putClientProperty("JTextField.placeholderText", "Search hosts\u2026");
        north.add(search, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        list = new JList<>(model) {
            @Override public boolean getScrollableTracksViewportWidth() { return true; }
        };
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        var center = new JPanel(new BorderLayout());
        var scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        center.add(scroll, BorderLayout.CENTER);
        empty.setBorder(BorderFactory.createEmptyBorder(12, 4, 12, 4));
        center.add(empty, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        for (JLabel detail : List.of(cardName, cardAddress, cardInfo, cardCredential, cardJump))
            detail.setAlignmentX(Component.LEFT_ALIGNMENT);
        cardInfo.putClientProperty("html.disable", true);
        card.add(cardName); card.add(cardAddress); card.add(cardInfo); card.add(cardCredential); card.add(cardJump);
        var buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
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
                if (index < 0 || !list.getCellBounds(index, index).contains(event.getPoint())) return;
                if (SwingUtilities.isRightMouseButton(event)) { list.setSelectedIndex(index); JPopupMenu menu = menuFor(index); if (menu != null) menu.show(list, event.getX(), event.getY()); }
                else if (event.getX() < 14 + favoriteWidth(list) && model.get(index) instanceof HostRows.Host row) {
                    if (event.getClickCount() == 1) actions.favorite().accept(row.host(), !row.host().favorite());
                }
                else if (event.getClickCount() == 2) activate(index);
                else if (model.get(index) instanceof HostRows.Group) toggle(index);
            }
        });
        list.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "activateHost");
        list.getActionMap().put("activateHost", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { activate(list.getSelectedIndex()); }
        });
        add.addActionListener(event -> actions.edit().accept(Optional.empty()));
        importButton.addActionListener(event -> actions.importConfig().run());
        connect.addActionListener(event -> {
            Optional<ShellProfileInfo> distro = selectedDistro();
            if (distro.isPresent()) openDistro.accept(distro.get());
            else selectedHost().ifPresent(actions.connect());
        });
        edit.addActionListener(event -> selectedHost().ifPresent(host -> actions.edit().accept(Optional.of(host))));
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                selectFirstHost();
            }
        });
        rebuild();
    }

    /** Focus an explicit opening in a visible window, never a restored panel before startup. */
    public void focusWhenOpened() {
        if (!isShowing()) return;
        SwingUtilities.invokeLater(() -> { if (isShowing()) list.requestFocusInWindow(); });
    }

    public void setDefaultGroup(String name) { defaultGroup = name; rebuild(); }
    public void onRenameDefaultGroup(Runnable rename) { renameDefaultGroup = rename; }
    public void onBrowseFiles(Consumer<RemoteHost> action) { browseFiles=action; }
    public void onActivate(Consumer<RemoteHost> action) { activate = action; }
    public void setHostDetails(Function<RemoteHost, String> details, ToIntFunction<RemoteHost> sessions) {
        metadata = details; sessionCount = sessions; rebuild();
    }

    /** What Open (double-click, Enter, the card, the first menu item) and Open in split do for a WSL distribution. */
    public void onDistros(Consumer<ShellProfileInfo> open, Consumer<ShellProfileInfo> openSplit) { openDistro = open; splitDistro = openSplit; }

    /** The WSL group's rows and their running-pane counts; other kinds of profile are ignored, and none hides the group. */
    public void setDistros(List<ShellProfileInfo> profiles, ToIntFunction<ShellProfileInfo> sessions) {
        distros = profiles.stream().filter(profile -> profile.kind() == ShellProfileInfo.Kind.WSL).toList();
        distroSessions = sessions;
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

    void selectDistro(String profileId) {
        for (int i = 0; i < model.size(); i++) if (model.get(i) instanceof HostRows.Distro row && row.profile().id().equals(profileId)) { list.setSelectedIndex(i); return; }
    }

    private void selectFirstHost() {
        int firstGroup = -1;
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i) instanceof HostRows.Host || model.get(i) instanceof HostRows.Distro) {
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
                return;
            }
            if (firstGroup < 0 && model.get(i) instanceof HostRows.Group) firstGroup = i;
        }
        // When all folders are collapsed, Enter can expand the first folder. Never select an error.
        if (firstGroup >= 0) { list.setSelectedIndex(firstGroup); list.ensureIndexIsVisible(firstGroup); }
        else list.clearSelection();
    }

    Optional<RemoteHost> selectedHost() { return hosts.stream().filter(host -> host.id().equals(selected)).findFirst(); }

    Optional<ShellProfileInfo> selectedDistro() { return distros.stream().filter(profile -> profile.id().equals(selectedDistro)).findFirst(); }

    /** Double-click or Enter on a row: connect to a host, open a distribution, toggle a group. */
    void activate(int index) {
        if (index < 0 || index >= model.size()) return;
        switch (model.get(index)) {
            case HostRows.Host row -> activate.accept(row.host());
            case HostRows.Group group -> toggle(index);
            case HostRows.Distro row -> openDistro.accept(row.profile());
            case HostRows.Error ignored -> { }
        }
    }

    void toggle(int index) {
        if (!(model.get(index) instanceof HostRows.Group group)) return;
        if (!collapsed.remove(group.key())) collapsed.add(group.key());
        collapseListeners.forEach(Runnable::run);
        rebuild();
    }

    JPopupMenu menuFor(int index) {
        if (index < 0 || index >= model.size()) return null;
        if (model.get(index) instanceof HostRows.Distro row) {
            var menu = new JPopupMenu();
            menu.add(item("Open in new tab", () -> openDistro.accept(row.profile())));
            menu.add(item("Open in split", () -> splitDistro.accept(row.profile())));
            return menu;
        }
        if (model.get(index) instanceof HostRows.Group group && !group.wsl() && group.name().equals(defaultGroup)) {
            var menu = new JPopupMenu(); menu.add(item("Rename group\u2026", renameDefaultGroup)); return menu;
        }
        if (!(model.get(index) instanceof HostRows.Host row)) return null;
        RemoteHost host = row.host();
        var menu = new JPopupMenu();
        menu.add(item("Connect in new tab", () -> actions.connect().accept(host)));
        menu.add(item("Connect in split", () -> actions.connectSplit().accept(host)));
        if(browseFiles!=null) menu.add(item("Browse files",()->browseFiles.accept(host)));
        menu.add(item("Edit\u2026", () -> actions.edit().accept(Optional.of(host))));
        menu.add(item("Duplicate", () -> actions.duplicate().accept(host)));
        menu.add(item("Delete\u2026", () -> actions.delete().accept(host)));
        menu.add(item(host.favorite() ? "Remove from favorites" : "Add to favorites", () -> actions.favorite().accept(host, !host.favorite())));
        return menu;
    }

    private static JMenuItem item(String title, Runnable action) { var item = new JMenuItem(title); item.addActionListener(event -> action.run()); return item; }

    private void rebuild() {
        UUID keep = selected;
        String keepDistro = selectedDistro;
        HostRows.Row previous = list.getSelectedValue();
        model.clear();
        error.ifPresent(message -> model.addElement(new HostRows.Error(message)));
        String query = search.getText().strip().toLowerCase(java.util.Locale.ROOT);
        List<RemoteHost> matching = hosts.stream().filter(host -> HostRows.matches(host, query)
            || (host.group().isEmpty() && defaultGroup.toLowerCase(java.util.Locale.ROOT).contains(query))
            || metadata.apply(host).toLowerCase(java.util.Locale.ROOT).contains(query)).toList();
        List<HostRows.Distro> distroRows = distros.stream().filter(profile -> HostRows.matches(profile, query))
            .map(profile -> new HostRows.Distro(profile, distroSessions.applyAsInt(profile))).toList();
        HostRows.rows(matching, "", query.isEmpty() ? collapsed : Set.of(), defaultGroup, distroRows).forEach(model::addElement);
        boolean searching = !search.getText().strip().isEmpty();
        empty.setText(hosts.isEmpty() ? "No hosts yet \u2014 Add or Import from ~/.ssh/config" : "No hosts match");
        empty.setVisible(hosts.isEmpty() || (searching && model.size() == (error.isPresent() ? 1 : 0)));
        if (keep != null) select(keep);
        else if (keepDistro != null) selectDistro(keepDistro);
        else if (previous instanceof HostRows.Group group) {
            for (int i = 0; i < model.size(); i++)
                if (model.get(i) instanceof HostRows.Group next && next.key().equals(group.key())) {
                    list.setSelectedIndex(i); break;
                }
        }
        if (list.isSelectionEmpty()) selectFirstHost();
        selectionChanged();
    }

    private void selectionChanged() {
        HostRows.Row row = list.getSelectedValue();
        selected = row instanceof HostRows.Host host ? host.host().id() : null;
        selectedDistro = row instanceof HostRows.Distro distro ? distro.profile().id() : null;
        refreshCard();
    }

    private void refreshCard() {
        Optional<ShellProfileInfo> distro = selectedDistro();
        if (distro.isPresent()) { distroCard(distro.get()); return; }
        Optional<RemoteHost> host = selectedHost();
        card.setVisible(host.isPresent());
        connect.setText("Connect"); edit.setVisible(true); cardCredential.setVisible(true);
        host.ifPresent(h -> {
            cardName.setText(h.name());
            cardAddress.setText(h.label());
            cardCredential.setText(credentialLabels.apply(h));
            String facts = metadata.apply(h);
            cardInfo.setText(facts); cardInfo.setVisible(!facts.isBlank());
            String via = h.jump().flatMap(id -> hosts.stream().filter(other -> other.id().equals(id)).findFirst()).map(RemoteHost::name).orElse(null);
            cardJump.setVisible(via != null);
            cardJump.setText(via == null ? "" : "via " + via);
        });
    }

    /** A distribution's card: its name, WSL version and the command Jasper runs; nothing to edit. */
    private void distroCard(ShellProfileInfo profile) {
        card.setVisible(true);
        cardName.setText(profile.name());
        cardAddress.setText(profile.detail().orElse(HostRows.WSL));
        cardInfo.setText("wsl.exe -d " + profile.name()); cardInfo.setVisible(true);
        cardCredential.setText(""); cardCredential.setVisible(false);
        cardJump.setText(""); cardJump.setVisible(false);
        edit.setVisible(false);
        connect.setText("Open");
    }

    private final class Renderer implements javax.swing.ListCellRenderer<HostRows.Row> {
        @Override public Component getListCellRendererComponent(JList<? extends HostRows.Row> owner, HostRows.Row value, int index, boolean selected, boolean focused) {
            if (value instanceof HostRows.Host row) return hostRow(owner, row.host(), selected);
            if (value instanceof HostRows.Distro row) return distroRow(owner, row, selected);
            JLabel label = new JLabel(); label.putClientProperty("html.disable", true);
            label.setOpaque(selected);
            label.setBackground(selected ? owner.getSelectionBackground() : owner.getBackground());
            label.setForeground(selected ? owner.getSelectionForeground() : owner.getForeground());
            label.setFont(owner.getFont().deriveFont(Font.BOLD));
            label.setBorder(BorderFactory.createEmptyBorder(5, 4, 4, 4));
            if (value instanceof HostRows.Group group) {
                label.setIcon(icons.apply(group.collapsed() ? BundledIcon.NODES_FOLDER : BundledIcon.GENERAL_OPEN));
                label.setIconTextGap(6);
                label.setText(group.name() + "   " + group.count());
                label.getAccessibleContext().setAccessibleDescription(group.collapsed() ? "Collapsed group" : "Expanded group");
                if (!group.wsl() && group.name().equals(defaultGroup)) label.setToolTipText("Right-click to rename this group");
            } else if (value instanceof HostRows.Error error) label.setText(error.message());
            return label;
        }
    }

    private static int favoriteWidth(JList<?> owner) {
        var metrics = owner.getFontMetrics(owner.getFont().deriveFont(owner.getFont().getSize2D() + 1));
        return Math.max(16, Math.max(metrics.stringWidth("\u2605"), metrics.stringWidth("\u2606")) + 2);
    }

    private Component hostRow(JList<?> owner, RemoteHost host, boolean selected) {
        var row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(true);
        row.setBackground(selected ? owner.getSelectionBackground() : owner.getBackground());
        row.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 8));
        java.awt.Color foreground = selected ? owner.getSelectionForeground() : owner.getForeground();
        Font font = owner.getFont().deriveFont(owner.getFont().getSize2D() + 1);
        var favorite = text(host.favorite() ? "\u2605" : "\u2606", foreground, font);
        favorite.setPreferredSize(new java.awt.Dimension(favoriteWidth(owner), favorite.getPreferredSize().height));
        row.add(favorite, BorderLayout.WEST);
        row.add(text(host.name(), foreground, font), BorderLayout.CENTER);
        int sessions = sessionCount.applyAsInt(host);
        if (sessions > 0) {
            var status = new JPanel(new BorderLayout(4, 0)); status.setOpaque(false);
            java.awt.Color active = javax.swing.UIManager.getColor("Actions.Green");
            var dot = text("\u25CF", active == null ? foreground : active, owner.getFont().deriveFont(Math.max(8f, owner.getFont().getSize2D() - 3f)));
            status.add(dot, BorderLayout.WEST);
            if (sessions > 1) status.add(text(String.valueOf(sessions), foreground, owner.getFont()), BorderLayout.CENTER);
            row.add(status, BorderLayout.EAST);
        }
        String facts = metadata.apply(host);
        String description = host.name() + ", " + host.label() + (facts.isBlank() ? "" : ", " + facts)
            + ", " + sessions + (sessions == 1 ? " session" : " sessions");
        row.setToolTipText(description);
        row.getAccessibleContext().setAccessibleName(description);
        return row;
    }

    /** A distribution: terminal icon, name, its WSL version as a badge, and the session dot and count as for hosts. */
    private Component distroRow(JList<?> owner, HostRows.Distro distro, boolean selected) {
        var row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(true);
        row.setBackground(selected ? owner.getSelectionBackground() : owner.getBackground());
        row.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 8));
        java.awt.Color foreground = selected ? owner.getSelectionForeground() : owner.getForeground();
        Font font = owner.getFont().deriveFont(owner.getFont().getSize2D() + 1);
        var icon = new JLabel(icons.apply(BundledIcon.TERMINAL_TOOLWINDOW_TERMINAL));
        icon.setPreferredSize(new java.awt.Dimension(favoriteWidth(owner), icon.getPreferredSize().height));
        row.add(icon, BorderLayout.WEST);
        row.add(text(distro.profile().name(), foreground, font), BorderLayout.CENTER);
        var east = new JPanel();
        east.setLayout(new BoxLayout(east, BoxLayout.X_AXIS));
        east.setOpaque(false);
        String badge = distro.profile().detail().orElse("");
        if (!badge.isEmpty()) {
            java.awt.Color muted = javax.swing.UIManager.getColor("Label.disabledForeground");
            east.add(text(badge, selected || muted == null ? foreground : muted, owner.getFont().deriveFont(Math.max(9f, owner.getFont().getSize2D() - 1f))));
        }
        int sessions = distro.sessions();
        if (sessions > 0) {
            java.awt.Color active = javax.swing.UIManager.getColor("Actions.Green");
            east.add(Box.createHorizontalStrut(6));
            east.add(text("\u25CF", active == null ? foreground : active, owner.getFont().deriveFont(Math.max(8f, owner.getFont().getSize2D() - 3f))));
            if (sessions > 1) { east.add(Box.createHorizontalStrut(4)); east.add(text(String.valueOf(sessions), foreground, owner.getFont())); }
        }
        row.add(east, BorderLayout.EAST);
        String description = distro.profile().name() + ", " + (badge.isEmpty() ? HostRows.WSL : badge) + ", " + sessions
            + (sessions == 1 ? " session" : " sessions");
        row.setToolTipText(description);
        row.getAccessibleContext().setAccessibleName(description);
        return row;
    }

    private static JLabel text(String value, java.awt.Color foreground, Font font) {
        var label = new JLabel(value); label.putClientProperty("html.disable", true);
        label.setForeground(foreground); label.setFont(font); return label;
    }
}
```

- [x] **Step 4: Run the tests**

Run: `./gradlew :jasper-plugin-remote:test --tests "dev.jasper.remote.ui.*"`
Expected: BUILD SUCCESSFUL; every earlier `HostsPanelTest` test still passes (the heading's text is the only visible change without distributions).

- [x] **Step 5: Commit**

```bash
git branch --show-current
git add plugins/remote
git commit -m "Show and open WSL distributions in the Remote Sessions panel

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: The `>ssh` scope lists distributions as WSL rows

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/RemoteScope.java` (replace)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/ui/RemoteScopeTest.java`

**Interfaces:**
- Produces: `RemoteScope(Supplier<List<RemoteHost>>, Supplier<Optional<String>>, BiConsumer<WindowHandle, RemoteHost> connect,
  BiConsumer<PaneHandle, RemoteHost> connectSplit, BiConsumer<WindowHandle, RemoteHost> edit, Supplier<List<ShellProfileInfo>> distros,
  BiConsumer<WindowHandle, ShellProfileInfo> open, BiConsumer<PaneHandle, ShellProfileInfo> openSplit)`; the 5-argument constructor
  kept (no distributions). Rows `wsl.<name>` tagged `WSL`, detail = the profile's detail, token = the `ShellProfileInfo`.
- Consumes: Task 4's `HostRows.matches(ShellProfileInfo, String)` and `HostRows.WSL`.

- [x] **Step 1: Write the failing test**

In `RemoteScopeTest.java`, add `import dev.jasper.sdk.terminal.ShellProfileInfo;` after
`import dev.jasper.sdk.palette.PaletteRow;`, and inside the class:

```java
    @Test void wslDistributionsLeadTaggedWslAndCannotBeEdited() {
        var ubuntu = new ShellProfileInfo("wsl:Ubuntu", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.of("WSL 1"));
        var pwsh = new ShellProfileInfo("pwsh", "PowerShell 7", ShellProfileInfo.Kind.LOCAL_SHELL, Optional.empty());
        var withDistros = new RemoteScope(() -> List.of(nas, prod), () -> error, (w, h) -> events.add("connect " + h.name()),
            (p, h) -> events.add("split " + h.name()), (w, h) -> events.add("edit " + h.name()),
            () -> List.of(ubuntu, pwsh), (w, d) -> events.add("open " + d.name()), (p, d) -> events.add("split " + d.name()));
        List<PaletteRow> rows = withDistros.search("", query).rows();
        assertThat(rows).extracting(PaletteRow::id).containsExactly("wsl.Ubuntu", "host." + prod.id(), "host." + nas.id());
        assertThat(rows.getFirst().title()).isEqualTo("Ubuntu");
        assertThat(rows.getFirst().tag()).contains("WSL");
        assertThat(rows.getFirst().detail()).contains("WSL 1");
        assertThat(withDistros.search("ubu", query).rows()).extracting(PaletteRow::title).containsExactly("Ubuntu");
        assertThat(withDistros.available(rows.getFirst(), RemoteScope.EDIT, query)).isFalse();
        assertThat(withDistros.available(rows.getFirst(), RemoteScope.SPLIT, query)).as("no target pane").isFalse();
        assertThat(withDistros.available(rows.getFirst(), RemoteScope.CONNECT, query)).isTrue();
        withDistros.execute(rows.getFirst(), RemoteScope.CONNECT, query);
        withDistros.execute(rows.getFirst(), RemoteScope.EDIT, query);
        assertThat(events).containsExactly("open Ubuntu");
        assertThat(withDistros.search("", new PaletteQuery(window(), Optional.empty(), 1, true)).rows())
            .as("the result limit covers distributions and hosts together").extracting(PaletteRow::id).containsExactly("wsl.Ubuntu");
    }
```

- [x] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-plugin-remote:test --tests "dev.jasper.remote.ui.RemoteScopeTest"`
Expected: FAIL: compilation error (no 8-argument `RemoteScope` constructor).

- [x] **Step 3: Write the implementation**

Replace `RemoteScope.java` with:

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
import dev.jasper.sdk.terminal.ShellProfileInfo;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * {@code >ssh}: WSL distributions, then saved hosts. Connect (Enter) opens a distribution or connects to a host,
 * Connect in split (Cmd/Ctrl+Enter) does the same beside the current pane, Edit host (Shift+Enter) is for hosts only.
 */
public final class RemoteScope implements PaletteScope {
    public static final String ID = "dev.jasper.remote.scope";
    public static final PaletteVerb CONNECT = new PaletteVerb("connect", "Connect");
    public static final PaletteVerb SPLIT = new PaletteVerb("split", "Connect in split");
    public static final PaletteVerb EDIT = new PaletteVerb("edit", "Edit host\u2026");
    static final String CONNECT_ACTION = "dev.jasper.remote.connect";
    static final String ERROR_ROW = "error";

    private final Supplier<List<RemoteHost>> hosts;
    private final Supplier<Optional<String>> error;
    private final BiConsumer<WindowHandle, RemoteHost> connect;
    private final BiConsumer<PaneHandle, RemoteHost> connectSplit;
    private final BiConsumer<WindowHandle, RemoteHost> edit;
    private final Supplier<List<ShellProfileInfo>> distros;
    private final BiConsumer<WindowHandle, ShellProfileInfo> open;
    private final BiConsumer<PaneHandle, ShellProfileInfo> openSplit;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public RemoteScope(Supplier<List<RemoteHost>> hosts, Supplier<Optional<String>> error, BiConsumer<WindowHandle, RemoteHost> connect,
                       BiConsumer<PaneHandle, RemoteHost> connectSplit, BiConsumer<WindowHandle, RemoteHost> edit) {
        this(hosts, error, connect, connectSplit, edit, List::of, (window, profile) -> { }, (pane, profile) -> { });
    }

    public RemoteScope(Supplier<List<RemoteHost>> hosts, Supplier<Optional<String>> error, BiConsumer<WindowHandle, RemoteHost> connect,
                       BiConsumer<PaneHandle, RemoteHost> connectSplit, BiConsumer<WindowHandle, RemoteHost> edit,
                       Supplier<List<ShellProfileInfo>> distros, BiConsumer<WindowHandle, ShellProfileInfo> open,
                       BiConsumer<PaneHandle, ShellProfileInfo> openSplit) {
        this.hosts = hosts; this.error = error; this.connect = connect; this.connectSplit = connectSplit; this.edit = edit;
        this.distros = distros; this.open = open; this.openSplit = openSplit;
    }

    @Override public ScopeSpec spec() {
        return ScopeSpec.of(ID, "SSH", "Search saved hosts", List.of(CONNECT, SPLIT, EDIT))
            .withDescription("Connect to a saved SSH host").withShortcutActionId(CONNECT_ACTION);
    }

    @Override public PaletteResults search(String query, PaletteQuery context) {
        var rows = new ArrayList<PaletteRow>();
        error.get().ifPresent(message -> rows.add(PaletteRow.of(ERROR_ROW, "hosts.toml has errors: " + message).withEnabled(false)));
        List<ShellProfileInfo> matchingDistros = distros.get().stream()
            .filter(profile -> profile.kind() == ShellProfileInfo.Kind.WSL && HostRows.matches(profile, query))
            .limit(context.maxResults()).toList();
        matchingDistros.forEach(profile -> rows.add(PaletteRow.of("wsl." + profile.name(), profile.name())
            .withDetail(profile.detail().orElse(null)).withTag(HostRows.WSL).withToken(profile)));
        hosts.get().stream().filter(host -> HostRows.matches(host, query))
            .sorted(Comparator.comparing((RemoteHost host) -> !host.favorite()).thenComparing(host -> host.name().toLowerCase(Locale.ROOT)))
            .limit(context.maxResults() - matchingDistros.size())
            .forEach(host -> rows.add(PaletteRow.of("host." + host.id(), host.name()).withDetail(host.label()).withTag(host.group().isEmpty() ? null : host.group()).withToken(host)));
        return PaletteResults.of(rows);
    }

    @Override public boolean available(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        boolean distro = row.token() instanceof ShellProfileInfo;
        if (!distro && !(row.token() instanceof RemoteHost)) return false;
        if (distro && verb.equals(EDIT)) return false;
        return !verb.equals(SPLIT) || context.target().map(PaneHandle::isOpen).orElse(false);
    }

    @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteQuery context) {
        if (row.token() instanceof ShellProfileInfo profile) {
            if (verb.equals(SPLIT)) context.target().ifPresent(pane -> openSplit.accept(pane, profile));
            else if (!verb.equals(EDIT)) open.accept(context.window(), profile);
            return;
        }
        if (!(row.token() instanceof RemoteHost host)) return;
        if (verb.equals(EDIT)) edit.accept(context.window(), host);
        else if (verb.equals(SPLIT)) context.target().ifPresent(pane -> connectSplit.accept(pane, host));
        else connect.accept(context.window(), host);
    }

    @Override public Subscription onChanged(Runnable listener) { listeners.add(listener); return () -> listeners.remove(listener); }
    public void changed() { listeners.forEach(Runnable::run); }
}
```

- [x] **Step 4: Run the tests**

Run: `./gradlew :jasper-plugin-remote:test --tests "dev.jasper.remote.ui.RemoteScopeTest"`
Expected: BUILD SUCCESSFUL (the existing `rowsVerbsAndErrors` test is unchanged).

- [x] **Step 5: Commit**

```bash
git branch --show-current
git add plugins/remote
git commit -m "List WSL distributions in Remote's palette scope

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Remote opens distributions, counts their panes and keeps every surface current

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/SessionsToolbar.java` (replace)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java`

**Interfaces:**
- Produces: `RemotePlugin.openDistro(WindowHandle, ShellProfileInfo)`, `splitDistro(PaneHandle, ShellProfileInfo)` (package-private);
  panel title "Sessions"; distribution actions `dev.jasper.remote.session.w<UUID.nameUUIDFromBytes(profile id UTF-8)>`
  titled `<name> (<detail>)`, placed before the hosts in the Sessions dropdown; `SessionsToolbar.distroActionId(String)`.
- Consumes: Tasks 1 to 6; `FakePluginHost.setShellProfiles` (Task 3).

- [x] **Step 1: Write the failing tests**

In `RemotePluginTest.java`:
- Add imports `import dev.jasper.remote.ui.HostRows;` (after `import dev.jasper.remote.ui.HostsPanel;`),
  `import dev.jasper.sdk.terminal.ShellProfileInfo;` (after `import dev.jasper.sdk.testing.FakePluginHost;`) and
  `import java.util.OptionalInt;` (after `import java.util.Optional;`).
- In `hostRequestsNetworkAndServerIcons` and `registersItsSurfaceAndConnectsThroughSessionsToolbar`, change
  `"dev.jasper.remote.panel|SSH hosts|LEFT|dev.jasper.remote.hosts"` to `"dev.jasper.remote.panel|Sessions|LEFT|dev.jasper.remote.hosts"`.
- Add inside the class:

```java
    static final ShellProfileInfo UBUNTU = new ShellProfileInfo("wsl:Ubuntu", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.of("WSL 2"));

    @Test void wslDistributionsOpenLocalPanesFromThePanelAndCountTheirSessions(@TempDir Path dir) throws Exception {
        try (var host = new FakePluginHost()) {
            host.setShellProfiles(List.of(new ShellProfileInfo("pwsh", "PowerShell 7", ShellProfileInfo.Kind.LOCAL_SHELL, Optional.empty())));
            RemotePlugin plugin = plugin(dir);
            var context = host.start(INFO, Set.of(), Set.of(), plugin);
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            var panel = (HostsPanel) host.openPanel(RemotePlugin.PANEL, window);
            assertThat(list(panel).getModel().getSize()).as("no WSL group without distributions").isZero();

            host.setShellProfiles(List.of(UBUNTU));
            settle(host);
            assertThat(list(panel).getModel().getElementAt(0)).isEqualTo(new HostRows.Group(HostRows.WSL, 1, false, HostRows.WSL_KEY));
            assertThat(list(panel).getModel().getElementAt(1)).isEqualTo(new HostRows.Distro(UBUNTU, 0));
            activate(panel, 1);
            settle(host);
            assertThat(host.openRequests()).containsExactly("tab|" + window + "|-|wsl:Ubuntu");
            UUID first = host.terminalPanes().getLast();
            assertThat(list(panel).getModel().getElementAt(1)).isEqualTo(new HostRows.Distro(UBUNTU, 1));

            host.focusTerminalPane(first); host.flush();
            menuItem(menuFor(panel, 1), "Open in split").doClick();
            settle(host);
            assertThat(host.openRequests()).last().isEqualTo("split|" + first + "|RIGHT|-|wsl:Ubuntu");
            assertThat(list(panel).getModel().getElementAt(1)).isEqualTo(new HostRows.Distro(UBUNTU, 2));
            UUID second = host.terminalPanes().getLast();
            host.sessionExited(second, OptionalInt.of(0)); settle(host);
            assertThat(list(panel).getModel().getElementAt(1)).as("an exited shell is not a session").isEqualTo(new HostRows.Distro(UBUNTU, 1));
            host.closeTerminalPane(first); settle(host);
            assertThat(list(panel).getModel().getElementAt(1)).isEqualTo(new HostRows.Distro(UBUNTU, 0));
            assertThat(host.status()).as("the status item counts SSH sessions only").isEmpty();

            toggle(panel, 0); settle(host);
            assertThat(Files.readString(context.dataDirectory().resolve("panel-state.toml"))).contains("\"wsl\"");
            host.setShellProfiles(List.of()); settle(host);
            assertThat(list(panel).getModel().getSize()).as("the group leaves with the last distribution").isZero();
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void sessionsToolbarAndPaletteListWslDistributionsFirst(@TempDir Path dir) {
        try (var host = new FakePluginHost()) {
            host.setShellProfiles(List.of(UBUNTU));
            RemotePlugin plugin = plugin(dir);
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            String manage = "dev.jasper.remote.sessions.manage";
            String ubuntu = "dev.jasper.remote.session.w" + UUID.nameUUIDFromBytes("wsl:Ubuntu".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var alpha = RemoteHost.create("alpha", "a.example", 22, "root", new Auth.Agent(), "", Optional.empty());
            plugin.store().put(alpha); settle(host);
            String alphaAction = "dev.jasper.remote.session.h" + alpha.id();
            assertThat(host.toolbar()).containsExactly("menu:Sessions:" + ubuntu + "," + alphaAction + "," + manage);
            assertThat(host.actions()).contains(ubuntu + "|Ubuntu (WSL 2)|true");
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            assertThat(host.invoke(ubuntu, window, null)).isTrue();
            assertThat(host.openRequests()).containsExactly("tab|" + window + "|-|wsl:Ubuntu");

            assertThat(host.searchScope(RemoteScope.ID, "", window, null)).containsExactly("wsl.Ubuntu|Ubuntu|true", "host." + alpha.id() + "|alpha|true");
            assertThat(host.availableInScope(RemoteScope.ID, "wsl.Ubuntu", "edit", window, null)).isFalse();
            host.executeInScope(RemoteScope.ID, "wsl.Ubuntu", "connect", window, null);
            assertThat(host.openRequests()).hasSize(2).last().isEqualTo("tab|" + window + "|-|wsl:Ubuntu");

            host.setShellProfiles(List.of()); settle(host);
            assertThat(host.toolbar()).containsExactly("menu:Sessions:" + alphaAction + "," + manage);
            assertThat(host.invoke(ubuntu, window, null)).isFalse();
            assertThat(host.searchScope(RemoteScope.ID, "", window, null)).containsExactly("host." + alpha.id() + "|alpha|true");
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }
```

- [x] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests "dev.jasper.remote.RemotePluginTest"`
Expected: FAIL: the panel title assertions (still "SSH hosts"), no WSL rows, and no distribution action in the toolbar.

- [x] **Step 3: Write the implementation**

Replace `SessionsToolbar.java` with:

```java
package dev.jasper.remote;

import dev.jasper.remote.hosts.HostStore;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.IconName;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.ToolbarItem;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.Icon;

/**
 * Keeps the Sessions dropdown in sync with the WSL distributions and the saved hosts. Distributions come first, titled
 * with their WSL version because toolbar menus have no separators; hosts follow alphabetically. Owned by RemotePlugin on
 * the UI thread.
 */
final class SessionsToolbar implements AutoCloseable {
    private static final String MANAGE = "dev.jasper.remote.sessions.manage";
    private final PluginContext context;
    private final HostStore store;
    private final Icon icon;
    private final Icon distroIcon;
    private final BiConsumer<WindowHandle, RemoteHost> activate;
    private final Supplier<List<ShellProfileInfo>> distros;
    private final BiConsumer<WindowHandle, ShellProfileInfo> openDistro;
    private final PluginAction manage;
    private final Map<UUID, PluginAction> actions = new HashMap<>();
    private final Map<String, PluginAction> distroActions = new HashMap<>();
    private List<String> placed = List.of();
    private Subscription placement;

    SessionsToolbar(PluginContext context, HostStore store, Icon icon, BiConsumer<WindowHandle, RemoteHost> activate,
                    Consumer<WindowHandle> showHosts, Supplier<List<ShellProfileInfo>> distros,
                    BiConsumer<WindowHandle, ShellProfileInfo> openDistro) {
        this.context = context; this.store = store; this.icon = icon; this.activate = activate;
        this.distros = distros; this.openDistro = openDistro;
        this.distroIcon = context.appearance().icon(IconName.TERMINAL);
        manage = context.actions().register(ActionSpec.of(MANAGE, "Manage Sessions...").withIcon(icon),
            invoked -> showHosts.accept(invoked.window()));
        refresh();
    }

    void refresh() {
        var ids = new ArrayList<String>();
        Set<String> listed = new HashSet<>();
        for (ShellProfileInfo profile : distros.get()) {
            String profileId = profile.id();
            String title = profile.name() + profile.detail().map(detail -> " (" + detail + ")").orElse("");
            PluginAction action = distroActions.get(profileId);
            if (action == null) {
                action = context.actions().register(ActionSpec.of(distroActionId(profileId), title).withIcon(distroIcon),
                    invoked -> distros.get().stream().filter(current -> current.id().equals(profileId)).findFirst()
                        .ifPresent(current -> openDistro.accept(invoked.window(), current)));
                distroActions.put(profileId, action);
            } else action.setTitle(title);
            ids.add(distroActionId(profileId));
            listed.add(profileId);
        }
        var hosts = store.hosts().stream().sorted(Comparator.comparing(RemoteHost::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(RemoteHost::id)).toList();
        for (RemoteHost host : hosts) {
            UUID id = host.id();
            String title = host.name() + " (" + host.label() + ")";
            PluginAction action = actions.get(id);
            if (action == null) {
                action = context.actions().register(ActionSpec.of(actionId(id), title).withIcon(icon),
                    invoked -> store.host(id).ifPresent(current -> activate.accept(invoked.window(), current)));
                actions.put(id, action);
            } else action.setTitle(title);
            ids.add(actionId(id));
        }
        ids.add(MANAGE);
        if (!placed.equals(ids)) {
            if (placement != null) placement.close();
            placement = context.toolbar().add(ToolbarItem.menu(icon, "Sessions", ids));
            placed = List.copyOf(ids);
        }
        for (String profileId : List.copyOf(distroActions.keySet())) {
            if (!listed.contains(profileId)) distroActions.remove(profileId).close();
        }
        for (UUID id : List.copyOf(actions.keySet())) {
            if (store.host(id).isEmpty()) actions.remove(id).close();
        }
    }

    /** A valid action id for any profile id: distribution names may hold capitals and dots, which action ids may not. */
    static String distroActionId(String profileId) {
        return "dev.jasper.remote.session.w" + UUID.nameUUIDFromBytes(profileId.getBytes(StandardCharsets.UTF_8));
    }

    private static String actionId(UUID id) { return "dev.jasper.remote.session.h" + id; }

    @Override public void close() {
        if (placement != null) placement.close();
        actions.values().forEach(PluginAction::close);
        actions.clear();
        distroActions.values().forEach(PluginAction::close);
        distroActions.clear();
        manage.close();
    }
}
```

(`IconName.TERMINAL`, not a `BundledIcon`: `hostRequestsNetworkAndServerIcons` asserts that start-up asks only for semantic icons.)

In `RemotePlugin.java`:

1. Add `import dev.jasper.sdk.terminal.SessionKind;` after `import dev.jasper.sdk.terminal.PendingSession;` and
   `import dev.jasper.sdk.terminal.ShellProfileInfo;` after `import dev.jasper.sdk.terminal.SessionSpec;`.

2. After `private final java.util.LinkedHashMap<UUID, UUID> recentPanes = new java.util.LinkedHashMap<>();` add:

```java
    /** Panes Remote opened for a WSL distribution: pane id to profile id. Local panes, so not in {@link #panes}. */
    private final Map<UUID, String> distroPanes = new HashMap<>();
    /** The WSL profiles the application lists now, in its order. */
    private List<ShellProfileInfo> distros = List.of();
```

3. In `start`, replace

```java
        scope = new RemoteScope(store::hosts, store::error, this::openHost, this::splitHost, (window, host) -> editHost(window, Optional.of(host)));
```

with

```java
        distros = wslProfiles();
        scope = new RemoteScope(store::hosts, store::error, this::openHost, this::splitHost, (window, host) -> editHost(window, Optional.of(host)),
            () -> distros, this::openDistro, this::splitDistro);
```

4. Replace `context.panels().register(new PanelSpec(PANEL, "SSH hosts", icon, Anchor.LEFT).withToggleActionId(HOSTS), this::createPanel);`
   with `context.panels().register(new PanelSpec(PANEL, "Sessions", icon, Anchor.LEFT).withToggleActionId(HOSTS), this::createPanel);`.

5. Replace

```java
        sessionsToolbar = new SessionsToolbar(context, store, icon, this::activateHost, window -> {
            PanelHost panel = panelHosts.get(window.id());
            if (panel == null) showPanel(window); else panel.show();
        });
```

with

```java
        sessionsToolbar = new SessionsToolbar(context, store, icon, this::activateHost, window -> {
            PanelHost panel = panelHosts.get(window.id());
            if (panel == null) showPanel(window); else panel.show();
        }, () -> distros, this::openDistro);
```

6. After `poll.start();` add:

```java
        context.terminals().onProfilesChanged(() -> {
            if (stopped) return;
            distros = wslProfiles();
            scope.changed(); sessionsToolbar.refresh(); refreshPanels();
        });
```

7. Replace the `PANE_CLOSED` subscription line with these two lines:

```java
        context.events().subscribe(TerminalEvents.PANE_CLOSED, event -> { attachedPanes.remove(event.paneId()); panes.remove(event.paneId()); paneIdentities.remove(event.paneId());follower.forget(event.paneId());sftpUi.ended(event.paneId()); recentPanes.remove(event.paneId()); distroPanes.remove(event.paneId()); refreshPanels(); });
        context.events().subscribe(TerminalEvents.SESSION_STATE_CHANGED, event -> { if (distroPanes.containsKey(event.paneId())) refreshPanels(); });
```

8. After `activateHost(...)` (before `private void rememberPane(UUID id)`) add:

```java

    /** Opens a WSL distribution in a new local tab; the application runs it, Remote only counts the pane. */
    void openDistro(WindowHandle window, ShellProfileInfo profile) {
        if (stopped || !window.isOpen()) return;
        context.terminals().openTab(window, OpenRequest.localProfile(profile.id())).ifPresent(pane -> trackDistro(pane, profile));
    }

    void splitDistro(PaneHandle pane, ShellProfileInfo profile) {
        if (stopped) return;
        context.terminals().split(pane, Direction.RIGHT, OpenRequest.localProfile(profile.id())).ifPresent(created -> trackDistro(created, profile));
    }

    private void trackDistro(PaneHandle pane, ShellProfileInfo profile) {
        distroPanes.put(pane.id(), profile.id());
        refreshPanels();
    }

    private List<ShellProfileInfo> wslProfiles() {
        return context.terminals().profiles().stream().filter(profile -> profile.kind() == ShellProfileInfo.Kind.WSL).toList();
    }

    /** Local panes Remote opened for this distribution whose shell still runs. */
    private int distroSessions(ShellProfileInfo profile) {
        int count = 0;
        for (var entry : distroPanes.entrySet()) {
            if (!entry.getValue().equals(profile.id())) continue;
            var info = context.terminals().pane(entry.getKey()).map(PaneHandle::info);
            if (info.isPresent() && info.get().kind() == SessionKind.LOCAL && info.get().state() != dev.jasper.sdk.terminal.SessionState.EXITED) count++;
        }
        return count;
    }
```

9. In `createPanel`, replace

```java
        panel.setHostDetails(this::hostDetails, this::sessionCount);
        panel.setCollapsed(panelState.collapsed());
```

with

```java
        panel.setHostDetails(this::hostDetails, this::sessionCount);
        panel.onDistros(profile -> openDistro(window, profile),
            profile -> window.activeTab().flatMap(tab -> tab.activePane()).ifPresent(pane -> splitDistro(pane, profile)));
        panel.setDistros(distros, this::distroSessions);
        panel.setCollapsed(panelState.collapsed());
```

10. In `refreshPanels`, replace

```java
            panel.setHostDetails(this::hostDetails, this::sessionCount);
            panel.setHosts(store.hosts(), store.error());
```

with

```java
            panel.setHostDetails(this::hostDetails, this::sessionCount);
            panel.setDistros(distros, this::distroSessions);
            panel.setHosts(store.hosts(), store.error());
```

11. In the class Javadoc, change "the provider, the panel, the scope" wording to read "the session provider, the Sessions panel
    (saved hosts and WSL distributions), the scope". `refreshStatus` is unchanged: the status item counts SSH channels only.

- [x] **Step 4: Run the tests**

Run: `./gradlew :jasper-plugin-remote:test`
Expected: BUILD SUCCESSFUL; the two new tests pass and every existing Remote test still passes.

Run: `./gradlew verifyPluginArchitecture :jasper-app:test --tests "dev.jasper.app.plugins.BundledSamplePluginTest" --tests "dev.jasper.app.plugins.RemoteShortcutConfigTest"`
Expected: BUILD SUCCESSFUL (the staged Remote 0.5.0 loads against SDK 0.8.6 and keeps its toggle title).

- [x] **Step 5: Commit**

```bash
git branch --show-current
git add plugins/remote
git commit -m "Open WSL distributions from Remote and track their panes

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Documentation, native acceptance checklist and the full check

**Files:**
- Modify: `docs/remote.md`, `docs/sdk-architecture.md`, `docs/plugin-authoring.md`, `jasper-sdk/README.md`, `docs/README.md`,
  `docs/STATUS.md`, this plan (status banner)

**Interfaces:**
- Produces: documentation only.
- Consumes: Tasks 1 to 7.

- [x] **Step 1: Remote guide**

In `docs/remote.md`, in the first paragraph replace `The **SSH hosts** panel shows searchable` with
`The **Sessions** panel shows searchable`. After the paragraph that ends `The list updates when hosts are added, edited, imported or removed.`
add:

```markdown
On Windows with WSL installed, the Sessions panel starts with a read-only **WSL** group: the
distributions Jasper detected for its shell profiles, each with its WSL version (WSL 1 or WSL 2) as a
badge. Double-click or Enter, **Open** on the card, or **Open in new tab** in the context menu opens
the distribution in a new local tab; **Open in split** splits the current pane. A dot and count mark
the panes Remote opened for it whose shell still runs. Distributions are never saved in `hosts.toml`
and cannot be edited, duplicated, deleted or browsed with SFTP; the group's collapsed state is saved
as `wsl` in `panel-state.toml`, and search matches distribution names. The Sessions dropdown lists
them first, titled with their version, and `>ssh` lists them as rows tagged `WSL`. To make a
distribution the default shell for new panes, choose it under Settings › Terminal › Shell. The status
bar still counts SSH sessions only. Without WSL, nothing here changes.
```

Under `## Native acceptance`, after item 10 and before the paragraph starting `For native acceptance of the UI changes`, add:

```markdown
For the WSL group (Windows with at least one WSL 1 and one WSL 2 distribution), also check:

11. The panel is titled **Sessions** and starts with a WSL group listing each distribution with the
    right WSL 1 or WSL 2 badge; `docker-desktop` is absent. A Mac or a Windows machine without WSL
    shows no group.
12. Double-click a distribution: a tab titled with its name runs that distribution's shell, and the
    row shows a dot. **Open in split** adds a second pane (count 2). Exit one shell and close the other
    pane: the dot disappears.
13. Install or remove a distribution, then refresh the profiles under Settings › Terminal › Shell: the
    group, the Sessions dropdown and `>ssh` follow without a restart.
14. Collapse the WSL group, restart Jasper, and confirm it stays collapsed.
```

- [x] **Step 2: SDK guides**

In `docs/sdk-architecture.md`, append at the end of the file:

```markdown
### Shell profiles (SDK 0.8.6)

`Terminals.profiles()` lists the shells the application detected (`launch.ShellProfileCatalog`:
PowerShell, PowerShell 7, Command Prompt and each WSL distribution on Windows, the login shell
elsewhere, and Custom when `terminal.shell.program` is set) as `ShellProfileInfo(id, name, kind,
detail)`, and `onProfilesChanged` reports a refresh. Both are binary-compatible default methods (an
empty list, an inert subscription) and need `terminal.open`. Kinds mirror the application's one to one;
the SDK does not say which profile the user chose as default, and a plugin cannot
change that choice.

`LocalSpec.profile` names a profile. `HostedTerminals` checks the id against the catalog: an id it
does not list logs a warning and makes `openTab` and `split` return empty, rather than starting the
default shell. An empty profile means what Jasper's own New Tab or Split would start. The catalog
reaches `dev.jasper.app.plugins` as `PluginRuntime.ShellProfiles`, a supplier and a listener
registration, so the plugin runtime depends only on the `launch.ShellProfile` value; listener
registrations close with the plugin. Plugins open only shells the application detected: explicit
commands in `LocalSpec` remain a separate decision. Remote uses this for its WSL group.
```

In `docs/plugin-authoring.md`:
- In the capability table, replace the `terminal.open` row with
  `` | `terminal.open` | `Terminals.openTab` and `split` with `OpenRequest.local()` or `localProfile(id)`, and `Terminals.profiles()` and `onProfilesChanged` | ``.
- Replace the sentence fragment
  ``bundled plugins declare `sdk = ">=0.8.0, <0.9"` (Vault `>=0.8.2` for icon meanings; Remote `>=0.8.3` for remote directory hints; Editor `>=0.8.4` for `onCloseCommand`, quit guards and `chooseSaveFile`).``
  (it spans three lines) with
  ``bundled plugins declare `sdk = ">=0.8.0, <0.9"` (Vault `>=0.8.2` for icon meanings; Editor `>=0.8.5` for hosted document tabs; Remote `>=0.8.6` for shell profiles).``
- After the bullet that starts `- To run a command in a new tab, open one with`, add:

```markdown
- **Shell profiles (SDK 0.8.6).** `context.terminals().profiles()` lists the shells Jasper detected,
  such as PowerShell or a WSL distribution (`kind()` is `ShellProfileInfo.Kind.WSL` and `detail()`
  reads `WSL 1` or `WSL 2`). Open one with `OpenRequest.localProfile(profile.id())`; an id Jasper does
  not list opens nothing and returns empty. On Windows, detection may finish after your plugin starts:
  subscribe with `onProfilesChanged` and read the list again. Declare `sdk = ">=0.8.6, <0.9"`.
```

- In `## Testing`, after the sentence ending `` `host.remoteCwdChanged(paneId, host, path)` reports a remote directory. `` add:
  `` `host.setShellProfiles(list)` scripts the detected shell profiles and runs `onProfilesChanged` listeners; `openRequests()` then ends a line with `|<profile id>` when the request named one. ``

In `jasper-sdk/README.md`:
- In the `dev.jasper.sdk.terminal` table row, add `` `ShellProfileInfo`, `` after `` `LocalSpec`, ``.
- Replace `Vault requires `sdk = ">=0.8.2, <0.9"`; Remote and Editor require `sdk = ">=0.8.5, <0.9"` for app-hosted document tabs and theme palettes.`
  with `Vault requires `sdk = ">=0.8.2, <0.9"`; Editor requires `sdk = ">=0.8.5, <0.9"` for app-hosted document tabs and theme palettes; Remote requires `sdk = ">=0.8.6, <0.9"` for shell profiles.`
- Append at the end of the file:

```markdown
SDK 0.8.6 adds shell profiles: `Terminals.profiles()` lists the shells Jasper detected as
`ShellProfileInfo` (PowerShell, Command Prompt and WSL distributions on Windows, the login shell
elsewhere), `onProfilesChanged` reports a refresh, and `OpenRequest.localProfile(id)` opens one. An
id Jasper does not list opens nothing. Both methods need `terminal.open`; the testkit scripts the list
with `FakePluginHost.setShellProfiles`.
```

In `docs/README.md`, replace
`SSH hosts, import from ~/.ssh/config, host-key trust, shared sessions and the hosts panel.` with
`SSH hosts, import from ~/.ssh/config, host-key trust, shared sessions, and the Sessions panel with WSL distributions.`.

- [x] **Step 3: Status**

Add at the top of `## Current state` in `docs/STATUS.md` (fill in the counts from Step 4):

```markdown
- **WSL B: SDK shell profiles and the Remote WSL group** (`worktree-wsl-hosts`, 2026-09-28; spec
  `docs/superpowers/specs/2026-09-28-jasper-wsl-shell-profiles-design.md`, plan
  `docs/superpowers/plans/2026-09-28-jasper-wsl-b-remote.md`): SDK 0.8.6 adds `ShellProfileInfo`,
  `Terminals.profiles()`/`onProfilesChanged` (default methods, `terminal.open`), `LocalSpec.profile` and
  `OpenRequest.localProfile`; the app feeds plan A's `ShellProfileCatalog` to plugins through
  `PluginRuntime.ShellProfiles` and refuses unknown ids (empty result, warning); the testkit scripts profiles
  (`setShellProfiles`) and the contract suite covers known, unknown and default. Remote 0.5.0 (`sdk >=0.8.6`)
  titles its panel Sessions and shows a read-only WSL group first (badges, session dots, Open in new tab/split,
  collapsed key `wsl`), lists distributions first in the Sessions dropdown (no separator: the SDK dropdown has
  none) and as `WSL` rows in `>ssh`. `./gradlew check`: N tests, 0 failures, 0 errors, N skipped. Native WSL
  acceptance (docs/remote.md items 11 to 14) pending with the user. Deviations: see the plan's status banner.
  Not pushed; not merged.
```

Update this plan's `**Status:**` line to "Implemented on `worktree-wsl-hosts`" with the date and test count, and list
every deviation under "Deviations so far".

- [x] **Step 4: Run the full check and the hygiene scan**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Count results from `*/build/test-results/test/*.xml` and `plugins/*/build/test-results/test/*.xml`
(failures and errors 0) and put the totals in the STATUS entry.

Run the AGENTS.md hygiene scan (Python) over `jasper-sdk/src jasper-sdk-testkit/src jasper-app/src plugins`; expected no output.
Where Python is unavailable (the Windows worktree), run instead:
`LC_ALL=C.UTF-8 grep -rnP '[\x{E000}-\x{F8FF}]|[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]' --include=*.java jasper-sdk/src jasper-sdk-testkit/src jasper-app/src plugins`
Expected: no output.

- [x] **Step 5: Commit**

```bash
git branch --show-current
git add docs jasper-sdk/README.md
git commit -m "Document SDK shell profiles and the Remote WSL group

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Then stop: merging waits for the user, and so does the native checklist (docs/remote.md items 11 to 14 on a Windows
machine with WSL 1 and WSL 2 distributions).
