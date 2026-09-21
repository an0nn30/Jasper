# Jasper Plugin SDK Plan 4a: Terminal API — Observe, Inject and Open — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Not started. Record every deviation from this text here and in `docs/STATUS.md`.

**Goal:** Let a plugin find the active window, tab and pane, read pane metadata, follow terminal events, type or paste into a pane, read the selection, and open local tabs and splits, each behind the capability the user consented to.

**Architecture:** SDK types stay confined to `dev.jasper.app.plugins`. A new app-native leaf package `dev.jasper.app.terminals` holds a `TerminalRegistry`: windows register pull-based entries (records of suppliers and callbacks over their tabs and panes) and publish a sealed family of id-only events; the registry derives the active pane. `workspace.WindowTerminals` connects one `WindowContent` to it, the way `WindowContributions` connects the contributions model. In `plugins`, `HostedTerminals` adapts the registry to per-plugin SDK handles whose gated methods consult a `CapabilityGate`, and `TerminalBridge` republishes registry events as `TerminalEvents` topics. The testkit gets a fake workspace with the same rules, and the shared contract suite grows to cover both.

**Tech Stack:** Java 25 on JBR 25, Gradle wrapper, Swing, JUnit 6.1.3, AssertJ 3.27.7. No new dependency. `jasper-terminal` is not touched.

**Spec:** `docs/superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md` (section 3 "Capabilities" and "Threading", section 6 "Handles and queries" and "Opening terminals", section 7 "Built-in topics", section 11). Earlier plans and their recorded deviations: `docs/superpowers/plans/2026-09-21-jasper-plugin-sdk-plan-1-core-runtime.md`, `…-plan-2-actions-chrome.md`, `…-plan-3a-rail-panels-windows.md`, `…-plan-3b-manager-install-restart.md`. Executors read all of them.

## Global Constraints

- Use `./gradlew`, never a system Gradle. Java 25, JetBrains vendor toolchain. `./gradlew check` is headless and must pass at the end of every task.
- **Never launch the GUI** (`:jasper-app:run`, benchmarks, previews that open windows). GUI checks are the user's; the end of this plan lists them.
- Work on branch `claude/plugin-sdk-plan-4` in `.worktrees/plugin-sdk-plan-4` (this plan is committed there). Run `git branch --show-current` before every commit and commit only when the verification command exited 0. End commit messages with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- SDK modules reference only the JDK and `dev.jasper.sdk.*`. SDK types appear in the app only inside `dev.jasper.app.plugins`. `terminals` and `workspace` must not import `dev.jasper.sdk`.
- No interface without two real implementations: new SDK interfaces are implemented by the app and by the testkit; app-native seams are final classes, records and JDK functional types.
- Capability names are exactly `terminal.observe`, `terminal.selection`, `terminal.inject`, `terminal.open`, `session.provide`. A gated call without the capability throws `MissingCapabilityException` on the calling thread, before anything else happens. Audit log lines never contain injected or selected content.
- Threading: `Terminals` and every handle method are EDT-only, except `PaneHandle.sendText`, `sendBytes` and `paste`, which are callable from any thread and keep their submission order. No plugin callback runs on a session reader thread or under the terminal buffer lock.
- A closing pane is a race, not a programmer error: mutating calls on a closed handle are ignored and logged at debug, queries return the last known value, `isOpen()` tells. A missing capability still throws.
- `TerminalEvents` payloads are delivered like every other event: queued, later, on the EDT, no replay.
- Source hygiene, package-info contracts and Javadoc doclint apply as in plans 1 to 3b.
- `AppDocumentationTest` link-checks and example-checks every Markdown file under `docs/superpowers`, including this plan: keep documentation links inside code fences here and never write a literal example marker comment (Task 9 spells it `EXAMPLE-MARKER`).
- **Never build file content in an unquoted shell heredoc**: backticks in it are executed. Use quoted heredocs (`<<'PY'`) or script files.
- Known flake, not to be fixed here: `TerminalAppIntegrationTest` `"reflow"` case (see `docs/STATUS.md`). If it is the only failure, rerun.

### Deliberate scope decisions and deviations from the spec

1. **The spec's plan 4 is split.** This plan (4a) delivers handles and queries, capability gating with audit logging, the `TerminalEvents` bridge, injection, selection and opening local tabs and splits. Plan 4b delivers `TerminalSession.attach` with the app-owned writer, drain and exactly-once `close`; `PendingSession`, the pending and disconnected pane states and Reconnect; the cleanup worker; `OpenRequest.session`; and working-directory provenance in `jasper-terminal`. Each is a runnable deliverable; only 4b touches `jasper-terminal`.
2. **Event payloads carry ids, not handles.** A handle is bound to the plugin that obtained it, because its gated methods check that plugin's capabilities; a payload is one object shared by every subscriber. A handler calls `context.terminals().pane(id)`.
3. **`Terminals.openTab` and `split` return `Optional<PaneHandle>`**, empty when the window or target pane is gone or the target has exited. The spec's bare `PaneHandle` has no honest value for that race.
4. **`LocalSpec` carries only an optional working directory** in 4a: the tab runs the user's configured shell through the existing launch path. An explicit command and environment need a per-pane launcher and have no consumer yet; a plugin that wants to run a command opens a tab and uses `sendText`.
5. **What `terminal.observe` gates:** `PaneHandle.info()`, `PaneHandle.foregroundJob()`, `TabHandle.title()` and subscribing to any `jasper.terminal.*` topic. Identity, structure (`tabs()`, `panes()`, `activePane()`, `isOpen()`, `isActive()`) and visible navigation (`toFront()`, `select()`, `focus()`) need no capability: the user sees them, and the spec's table lists only metadata, directory, titles, command text and events.
6. **Audit logging:** injection (plugin, pane, byte count), selection reads (plugin, pane, character count), opening (plugin, what) and the first subscription to a terminal topic per plugin are logged at INFO. Metadata queries are not logged per call: they run on every event.
7. The shapes of 4b are already in the SDK: `PaneInfo` has `remoteDirectory`, `kind`, `providerPluginId` and `SessionState.CONNECTING`; `CwdChanged` and `CommandFinished` carry a remote directory. In 4a the remote directory is always empty, the kind is always `LOCAL`, and `CONNECTING` means a local shell that has not started yet.
8. `foregroundJob()`'s future completes on an application worker thread, not the EDT.
9. `JasperSdk.VERSION` becomes `0.4.0`; the sample's range becomes `>=0.4, <0.5`. The bundled sample declares `terminal.observe` and `terminal.inject`.

## File Structure

```
jasper-sdk/src/main/java/dev/jasper/sdk/
  Capabilities.java, MissingCapabilityException.java                create
  terminal/{Direction,SessionKind,SessionState,RemoteDirectory,PaneInfo,LocalSpec,OpenRequest}.java   create
  terminal/{TabHandle,Terminals,TerminalEvents}.java                create
  terminal/{WindowHandle,PaneHandle}.java                           grow (Task 4)
  plugin/PluginContext.java, JasperSdk.java                         modify (Task 6)
jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/
  FakeWorkspace.java, FakeTerminals.java                            create
  FakeUi.java, FakePluginContext.java, FakePluginHost.java          modify
jasper-app/src/main/java/dev/jasper/app/
  terminals/{package-info,SplitAxis,PaneSnapshot,PaneEntry,TabEntry,WindowEntry,TerminalEvent,TerminalRegistry}.java   create
  workspace/WindowTerminals.java                                    create
  workspace/{TerminalPane,TerminalTab,WindowContent,package-info}.java   modify
  plugins/{CapabilityGate,HostedTerminals,TerminalBridge}.java      create
  plugins/{HostedUi,HostedContext,PluginHost,PluginRuntime,package-info}.java   modify
  application/{JasperApplication,package-info}.java                 modify
plugins/sample/                                                     demo_terminal
docs/                                                               plugin-authoring, sdk-architecture, app-architecture, configuration, STATUS
```

---

### Task 0: Baseline

**Files:** none

- [ ] **Step 1: Confirm the workspace**

Run: `git branch --show-current`
Expected: `claude/plugin-sdk-plan-4`, in `.worktrees/plugin-sdk-plan-4`. The branch starts from the plan 3b tip `4cf8b3a`.

- [ ] **Step 2: Confirm the baseline is green**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL` with 1,326 tests (rerun if the only failure is the known terminal flake). Nothing to commit.

---

### Task 1: SDK terminal values, capabilities and event topics

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/{Capabilities,MissingCapabilityException}.java`
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/{Direction,SessionKind,SessionState,RemoteDirectory,PaneInfo,LocalSpec,OpenRequest,TabHandle,Terminals,TerminalEvents}.java`
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/terminal/TerminalValuesTest.java`

**Interfaces:**
- Produces: the types below. `WindowHandle` and `PaneHandle` stay identity-only until Task 4, so everything that builds them from a lambda keeps compiling.

- [ ] **Step 1: Write the failing test**

`jasper-sdk/src/test/java/dev/jasper/sdk/terminal/TerminalValuesTest.java`:

```java
package dev.jasper.sdk.terminal;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.events.Topic;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TerminalValuesTest {
    @Test void paneInfoRejectsImpossibleValues() {
        PaneInfo unknown = PaneInfo.unknown();
        assertThat(unknown.state()).isEqualTo(SessionState.EXITED);
        assertThat(unknown.kind()).isEqualTo(SessionKind.LOCAL);
        assertThatIllegalArgumentException().isThrownBy(() -> new PaneInfo("t", Optional.empty(), Optional.empty(), -1, 24, false,
            SessionKind.LOCAL, Optional.empty(), SessionState.RUNNING, OptionalInt.empty()));
        assertThatIllegalArgumentException().as("an exit status belongs to an exited session").isThrownBy(() -> new PaneInfo("t",
            Optional.empty(), Optional.empty(), 80, 24, false, SessionKind.LOCAL, Optional.empty(), SessionState.RUNNING, OptionalInt.of(0)));
        assertThatNullPointerException().isThrownBy(() -> new PaneInfo(null, Optional.empty(), Optional.empty(), 80, 24, false,
            SessionKind.LOCAL, Optional.empty(), SessionState.RUNNING, OptionalInt.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new RemoteDirectory(" ", "/srv"));
    }

    @Test void openRequestsAreLocalAndTakeAnAbsoluteDirectory() {
        assertThat(OpenRequest.local()).isEqualTo(new OpenRequest.Local(new LocalSpec(Optional.empty())));
        Path home = Path.of(System.getProperty("user.home"));
        assertThat(OpenRequest.localIn(home)).isEqualTo(OpenRequest.local(new LocalSpec(Optional.of(home))));
        assertThatIllegalArgumentException().isThrownBy(() -> new LocalSpec(Optional.of(Path.of("relative"))));
    }

    @Test void everyTerminalTopicIsNamespacedAndDistinct() throws Exception {
        List<String> ids = new ArrayList<>();
        for (var field : TerminalEvents.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != Topic.class) continue;
            Topic<?> topic = (Topic<?>) field.get(null);
            assertThat(TerminalEvents.owns(topic)).as(field.getName()).isTrue();
            assertThat(topic.id()).isEqualTo(TerminalEvents.PREFIX + field.getName().toLowerCase(java.util.Locale.ROOT));
            ids.add(topic.id());
        }
        assertThat(ids).hasSize(16).doesNotHaveDuplicates();
        assertThat(TerminalEvents.owns(Topic.of("jasper.activity", String.class))).isFalse();
    }

    @Test void aMissingCapabilityNamesThePluginAndTheCapability() {
        var failure = new MissingCapabilityException("dev.example.tool", Capabilities.TERMINAL_INJECT);
        assertThat(failure.pluginId()).isEqualTo("dev.example.tool");
        assertThat(failure.capability()).isEqualTo("terminal.inject");
        assertThat(failure).hasMessageContaining("dev.example.tool").hasMessageContaining("terminal.inject");
        assertThat(Capabilities.ALL).containsExactly("terminal.observe", "terminal.selection", "terminal.inject", "terminal.open", "session.provide");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-sdk:compileTestJava`
Expected: compilation FAILS (`PaneInfo`, `Capabilities`, `TerminalEvents` not found).

- [ ] **Step 3: Write the types**

`jasper-sdk/src/main/java/dev/jasper/sdk/Capabilities.java`:

```java
package dev.jasper.sdk;

import java.util.List;

/**
 * The capability names a plugin declares in {@code plugin.toml} and the user consents to. They cover
 * effects the user cannot see; contributions to the window need none. They are not a sandbox.
 */
public final class Capabilities {
    /** Pane metadata, working directories, titles, command text, and every {@code jasper.terminal.*} topic. */
    public static final String TERMINAL_OBSERVE = "terminal.observe";
    /** Reading the text selected in a pane. */
    public static final String TERMINAL_SELECTION = "terminal.selection";
    /** Typing and pasting into a pane. */
    public static final String TERMINAL_INJECT = "terminal.inject";
    /** Opening local tabs and splits. */
    public static final String TERMINAL_OPEN = "terminal.open";
    /** Providing a pane's session, such as a remote connection. */
    public static final String SESSION_PROVIDE = "session.provide";
    /** Every capability this SDK version knows. */
    public static final List<String> ALL = List.of(TERMINAL_OBSERVE, TERMINAL_SELECTION, TERMINAL_INJECT, TERMINAL_OPEN, SESSION_PROVIDE);

    private Capabilities() { }
}
```

`jasper-sdk/src/main/java/dev/jasper/sdk/MissingCapabilityException.java`:

```java
package dev.jasper.sdk;

/** A gated call by a plugin that did not declare the capability, or whose user did not consent to it. */
public final class MissingCapabilityException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String pluginId;
    private final String capability;

    /**
     * Creates the exception.
     *
     * @param pluginId the calling plugin
     * @param capability the capability it lacks
     */
    public MissingCapabilityException(String pluginId, String capability) {
        super("Plugin " + pluginId + " needs the capability " + capability + "; declare it in plugin.toml");
        this.pluginId = pluginId;
        this.capability = capability;
    }

    /**
     * The calling plugin.
     *
     * @return its id
     */
    public String pluginId() { return pluginId; }

    /**
     * The missing capability.
     *
     * @return its name
     */
    public String capability() { return capability; }
}
```

`terminal/Direction.java`:

```java
package dev.jasper.sdk.terminal;

/** Where a split places the new pane. */
public enum Direction {
    /** Beside the target, to its right. */
    RIGHT,
    /** Below the target. */
    DOWN
}
```

`terminal/SessionKind.java`:

```java
package dev.jasper.sdk.terminal;

/** Who provides a pane's session. */
public enum SessionKind {
    /** A local process on a pseudo-terminal. */
    LOCAL,
    /** A plugin-provided connection. */
    PLUGIN
}
```

`terminal/SessionState.java`:

```java
package dev.jasper.sdk.terminal;

/** Where a pane's session is in its life. */
public enum SessionState {
    /** Not running yet: a local shell is starting, or a plugin is connecting. */
    CONNECTING,
    /** Running. */
    RUNNING,
    /** Ended; the pane may still be open and showing its last output. */
    EXITED
}
```

`terminal/RemoteDirectory.java`:

```java
package dev.jasper.sdk.terminal;

import java.util.Objects;

/**
 * A working directory the shell reported under a host that is not this machine. It is unauthenticated
 * text from whatever runs in the pane: a hint, never a fact.
 *
 * @param host the reported host name
 * @param path the reported path, as text
 */
public record RemoteDirectory(String host, String path) {
    /** Rejects nulls and a blank host. */
    public RemoteDirectory {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(path, "path");
        if (host.isBlank()) throw new IllegalArgumentException("A remote directory needs a host");
    }
}
```

`terminal/PaneInfo.java`:

```java
package dev.jasper.sdk.terminal;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A snapshot of one pane. Needs {@code terminal.observe}.
 *
 * @param title the program's title, or the running command when it set none
 * @param workingDirectory the local working directory the shell last reported; a hint that may not exist
 * @param remoteDirectory a directory reported under another host
 * @param columns the grid width in cells; zero before the session starts
 * @param rows the grid height in cells; zero before the session starts
 * @param shellIntegration whether the shell reports prompts and commands
 * @param kind who provides the session
 * @param providerPluginId the providing plugin, for a plugin session
 * @param state where the session is in its life
 * @param exitStatus the exit status; present only when the session exited with a known status
 */
public record PaneInfo(String title, Optional<Path> workingDirectory, Optional<RemoteDirectory> remoteDirectory, int columns, int rows,
                       boolean shellIntegration, SessionKind kind, Optional<String> providerPluginId, SessionState state,
                       OptionalInt exitStatus) {
    /** Rejects nulls, a negative grid, and an exit status on a session that has not exited. */
    public PaneInfo {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(remoteDirectory, "remoteDirectory");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(providerPluginId, "providerPluginId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(exitStatus, "exitStatus");
        if (columns < 0 || rows < 0) throw new IllegalArgumentException("A grid cannot be negative");
        if (exitStatus.isPresent() && state != SessionState.EXITED) throw new IllegalArgumentException("Only an exited session has an exit status");
    }

    /**
     * What a handle reports for a pane it never saw open.
     *
     * @return an exited local pane with no title, directory or grid
     */
    public static PaneInfo unknown() {
        return new PaneInfo("", Optional.empty(), Optional.empty(), 0, 0, false, SessionKind.LOCAL, Optional.empty(),
            SessionState.EXITED, OptionalInt.empty());
    }
}
```

`terminal/LocalSpec.java`:

```java
package dev.jasper.sdk.terminal;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A local terminal to open: the user's configured shell.
 *
 * @param workingDirectory where it starts; empty means where Jasper's own New Tab would start
 */
public record LocalSpec(Optional<Path> workingDirectory) {
    /** Rejects null and a relative directory. */
    public LocalSpec {
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        if (workingDirectory.isPresent() && !workingDirectory.get().isAbsolute())
            throw new IllegalArgumentException("A working directory must be absolute: " + workingDirectory.get());
    }
}
```

`terminal/OpenRequest.java`:

```java
package dev.jasper.sdk.terminal;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** What to run in a new tab or split. Plugin-provided sessions join this family in a later SDK version. */
public sealed interface OpenRequest permits OpenRequest.Local {
    /**
     * A local shell. Needs {@code terminal.open}.
     *
     * @param spec what to start
     */
    record Local(LocalSpec spec) implements OpenRequest {
        /** Rejects null. */
        public Local { Objects.requireNonNull(spec, "spec"); }
    }

    /**
     * The user's shell, where New Tab would start it.
     *
     * @return the request
     */
    static OpenRequest local() { return new Local(new LocalSpec(Optional.empty())); }

    /**
     * The user's shell in a directory.
     *
     * @param workingDirectory an absolute directory
     * @return the request
     */
    static OpenRequest localIn(Path workingDirectory) { return new Local(new LocalSpec(Optional.of(workingDirectory))); }

    /**
     * A local shell from a spec.
     *
     * @param spec what to start
     * @return the request
     */
    static OpenRequest local(LocalSpec spec) { return new Local(spec); }
}
```

`terminal/TabHandle.java`:

```java
package dev.jasper.sdk.terminal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** One tab. Handles keep an id and no Swing object; two handles for the same tab are equal. EDT only. */
public interface TabHandle {
    /**
     * The tab's stable id.
     *
     * @return the id
     */
    UUID id();

    /**
     * The window the tab was last seen in.
     *
     * @return its handle
     */
    WindowHandle window();

    /**
     * The tab's panes, in creation order; empty once the tab is closed.
     *
     * @return the panes
     */
    List<PaneHandle> panes();

    /**
     * The pane that has, or would get, keyboard focus in this tab.
     *
     * @return the pane, or empty once the tab is closed
     */
    Optional<PaneHandle> activePane();

    /**
     * The tab's title as its header shows it. Needs {@code terminal.observe}.
     *
     * @return the title; the last known one once the tab is closed
     */
    String title();

    /** Selects the tab in its window. Ignored once the tab is closed. */
    void select();

    /**
     * Whether the tab still exists.
     *
     * @return true while it is open
     */
    boolean isOpen();
}
```

`terminal/Terminals.java`:

```java
package dev.jasper.sdk.terminal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The windows, tabs and panes of this Jasper process. EDT only. */
public interface Terminals {
    /**
     * The window the user used last, even while Jasper is in the background.
     *
     * @return the window, or empty when none has been active or the last one closed
     */
    Optional<WindowHandle> activeWindow();

    /**
     * The focused pane of the active window's selected tab.
     *
     * @return the pane, or empty
     */
    Optional<PaneHandle> activePane();

    /**
     * Every open terminal window, in opening order.
     *
     * @return the windows
     */
    List<WindowHandle> windows();

    /**
     * An open pane by id, as events name it.
     *
     * @param id the pane id
     * @return the pane, or empty when it is not open
     */
    Optional<PaneHandle> pane(UUID id);

    /**
     * An open tab by id.
     *
     * @param id the tab id
     * @return the tab, or empty when it is not open
     */
    Optional<TabHandle> tab(UUID id);

    /**
     * An open window by id.
     *
     * @param id the window id
     * @return the window, or empty when it is not open
     */
    Optional<WindowHandle> window(UUID id);

    /**
     * Opens and selects a new tab. A local request needs {@code terminal.open}.
     *
     * @param window where
     * @param request what to run
     * @return the new tab's pane, or empty when the window is gone
     */
    Optional<PaneHandle> openTab(WindowHandle window, OpenRequest request);

    /**
     * Splits a pane. A local request needs {@code terminal.open}.
     *
     * @param target the pane to split
     * @param direction where the new pane goes
     * @param request what to run
     * @return the new pane, or empty when the target is gone or its session has ended
     */
    Optional<PaneHandle> split(PaneHandle target, Direction direction, OpenRequest request);
}
```

`terminal/TerminalEvents.java`:

```java
package dev.jasper.sdk.terminal;

import dev.jasper.sdk.events.Topic;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * What happens in terminals. Subscribing to any of these topics needs {@code terminal.observe}. Payloads
 * name windows, tabs and panes by id, because a handle belongs to the plugin that obtained it; ask
 * {@link Terminals} for one. Like every event they arrive later, on the event thread, and are not
 * replayed: read the current state first, then listen.
 */
public final class TerminalEvents {
    /** The prefix of every terminal topic id. */
    public static final String PREFIX = "jasper.terminal.";

    /**
     * A window.
     *
     * @param windowId the window
     */
    public record WindowEvent(UUID windowId) {
        /** Rejects null. */
        public WindowEvent { Objects.requireNonNull(windowId, "windowId"); }
    }

    /**
     * A tab.
     *
     * @param windowId its window
     * @param tabId the tab
     */
    public record TabEvent(UUID windowId, UUID tabId) {
        /** Rejects nulls. */
        public TabEvent { Objects.requireNonNull(windowId, "windowId"); Objects.requireNonNull(tabId, "tabId"); }
    }

    /**
     * A pane.
     *
     * @param tabId its tab
     * @param paneId the pane
     */
    public record PaneEvent(UUID tabId, UUID paneId) {
        /** Rejects nulls. */
        public PaneEvent { Objects.requireNonNull(tabId, "tabId"); Objects.requireNonNull(paneId, "paneId"); }
    }

    /**
     * {@link Terminals#activePane()} changed.
     *
     * @param paneId the pane that is active now, or empty
     */
    public record ActivePaneChanged(Optional<UUID> paneId) {
        /** Rejects null. */
        public ActivePaneChanged { Objects.requireNonNull(paneId, "paneId"); }
    }

    /**
     * A pane's title changed.
     *
     * @param paneId the pane
     * @param title the new title
     */
    public record TitleChanged(UUID paneId, String title) {
        /** Rejects nulls. */
        public TitleChanged { Objects.requireNonNull(paneId, "paneId"); Objects.requireNonNull(title, "title"); }
    }

    /**
     * The shell reported a working directory.
     *
     * @param paneId the pane
     * @param workingDirectory the local directory, when the report was local
     * @param remoteDirectory the remote directory, when it was not
     */
    public record CwdChanged(UUID paneId, Optional<Path> workingDirectory, Optional<RemoteDirectory> remoteDirectory) {
        /** Rejects nulls. */
        public CwdChanged {
            Objects.requireNonNull(paneId, "paneId");
            Objects.requireNonNull(workingDirectory, "workingDirectory");
            Objects.requireNonNull(remoteDirectory, "remoteDirectory");
        }
    }

    /**
     * A command began, in a shell with integration.
     *
     * @param paneId the pane
     * @param command the command line
     */
    public record CommandStarted(UUID paneId, String command) {
        /** Rejects nulls. */
        public CommandStarted { Objects.requireNonNull(paneId, "paneId"); Objects.requireNonNull(command, "command"); }
    }

    /**
     * A command finished, in a shell with integration.
     *
     * @param paneId the pane
     * @param command the command line
     * @param exitStatus its exit status, when the shell reported one
     * @param duration how long it ran
     * @param workingDirectory the local directory it ran in, when known
     * @param remoteDirectory the remote directory it ran in, when the shell reported one
     */
    public record CommandFinished(UUID paneId, String command, OptionalInt exitStatus, Duration duration,
                                  Optional<Path> workingDirectory, Optional<RemoteDirectory> remoteDirectory) {
        /** Rejects nulls. */
        public CommandFinished {
            Objects.requireNonNull(paneId, "paneId"); Objects.requireNonNull(command, "command");
            Objects.requireNonNull(exitStatus, "exitStatus"); Objects.requireNonNull(duration, "duration");
            Objects.requireNonNull(workingDirectory, "workingDirectory"); Objects.requireNonNull(remoteDirectory, "remoteDirectory");
        }
    }

    /**
     * A pane's session changed state.
     *
     * @param paneId the pane
     * @param state the new state
     * @param exitStatus the exit status, when it exited with a known one
     */
    public record SessionStateChanged(UUID paneId, SessionState state, OptionalInt exitStatus) {
        /** Rejects nulls. */
        public SessionStateChanged {
            Objects.requireNonNull(paneId, "paneId"); Objects.requireNonNull(state, "state"); Objects.requireNonNull(exitStatus, "exitStatus");
        }
    }

    /** A terminal window opened. */
    public static final Topic<WindowEvent> WINDOW_OPENED = Topic.of(PREFIX + "window_opened", WindowEvent.class);
    /** A terminal window closed. */
    public static final Topic<WindowEvent> WINDOW_CLOSED = Topic.of(PREFIX + "window_closed", WindowEvent.class);
    /** The user turned to a terminal window. */
    public static final Topic<WindowEvent> WINDOW_ACTIVATED = Topic.of(PREFIX + "window_activated", WindowEvent.class);
    /** A tab opened. */
    public static final Topic<TabEvent> TAB_OPENED = Topic.of(PREFIX + "tab_opened", TabEvent.class);
    /** A tab closed. */
    public static final Topic<TabEvent> TAB_CLOSED = Topic.of(PREFIX + "tab_closed", TabEvent.class);
    /** A window's selected tab changed. */
    public static final Topic<TabEvent> TAB_SELECTED = Topic.of(PREFIX + "tab_selected", TabEvent.class);
    /** A pane opened. */
    public static final Topic<PaneEvent> PANE_OPENED = Topic.of(PREFIX + "pane_opened", PaneEvent.class);
    /** A pane closed. */
    public static final Topic<PaneEvent> PANE_CLOSED = Topic.of(PREFIX + "pane_closed", PaneEvent.class);
    /** A pane took keyboard focus. */
    public static final Topic<PaneEvent> PANE_FOCUSED = Topic.of(PREFIX + "pane_focused", PaneEvent.class);
    /** The active pane changed, for whatever reason: focus, tab selection, window activation, or a close. */
    public static final Topic<ActivePaneChanged> ACTIVE_PANE_CHANGED = Topic.of(PREFIX + "active_pane_changed", ActivePaneChanged.class);
    /** A pane's title changed. */
    public static final Topic<TitleChanged> TITLE_CHANGED = Topic.of(PREFIX + "title_changed", TitleChanged.class);
    /** A shell reported its working directory. */
    public static final Topic<CwdChanged> CWD_CHANGED = Topic.of(PREFIX + "cwd_changed", CwdChanged.class);
    /** A command began. */
    public static final Topic<CommandStarted> COMMAND_STARTED = Topic.of(PREFIX + "command_started", CommandStarted.class);
    /** A command finished. */
    public static final Topic<CommandFinished> COMMAND_FINISHED = Topic.of(PREFIX + "command_finished", CommandFinished.class);
    /** A pane's session started or ended. */
    public static final Topic<SessionStateChanged> SESSION_STATE_CHANGED = Topic.of(PREFIX + "session_state_changed", SessionStateChanged.class);
    /** A pane rang the bell. */
    public static final Topic<PaneEvent> BELL = Topic.of(PREFIX + "bell", PaneEvent.class);

    private TerminalEvents() { }

    /**
     * Whether a topic is one of these, and so needs {@code terminal.observe}.
     *
     * @param topic any topic
     * @return true for a terminal topic
     */
    public static boolean owns(Topic<?> topic) { return topic.id().startsWith(PREFIX); }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-sdk:check verifySdkArchitecture`
Expected: PASS, including Javadoc doclint.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-sdk
git commit -m "feat: define the SDK terminal values, capabilities and event topics

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: The app-native terminal registry

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/terminals/{package-info,SplitAxis,PaneSnapshot,PaneEntry,TabEntry,WindowEntry,TerminalEvent,TerminalRegistry}.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/terminals/TerminalRegistryTest.java`

**Interfaces:**
- Produces:
  - `enum SplitAxis { RIGHT, DOWN }`
  - `record PaneSnapshot(String title, Optional<Path> workingDirectory, int columns, int rows, boolean shellIntegration, State state, OptionalInt exitStatus)` with `enum State { STARTING, RUNNING, EXITED }`
  - `record PaneEntry(UUID id, UUID tabId, Supplier<PaneSnapshot> snapshot, Supplier<CompletableFuture<Optional<String>>> foregroundJob, Consumer<byte[]> write, Consumer<String> paste, Supplier<Optional<String>> selection, Runnable focus, BiFunction<SplitAxis, Optional<Path>, Optional<PaneEntry>> split)`
  - `record TabEntry(UUID id, UUID windowId, Supplier<List<PaneEntry>> panes, Supplier<Optional<PaneEntry>> focusedPane, Supplier<String> title, Runnable select)`
  - `record WindowEntry(UUID id, Supplier<List<TabEntry>> tabs, Supplier<Optional<TabEntry>> selectedTab, BooleanSupplier active, Runnable toFront, Function<Optional<Path>, Optional<PaneEntry>> openTab)`
  - `sealed interface TerminalEvent` with records `WindowOpened(UUID windowId)`, `WindowClosed(UUID windowId)`, `WindowActivated(UUID windowId)`, `TabOpened(UUID windowId, UUID tabId)`, `TabClosed(UUID windowId, UUID tabId)`, `TabSelected(UUID windowId, UUID tabId)`, `PaneOpened(UUID tabId, UUID paneId)`, `PaneClosed(UUID tabId, UUID paneId)`, `PaneFocused(UUID tabId, UUID paneId)`, `ActivePaneChanged(Optional<UUID> paneId)`, `TitleChanged(UUID paneId, String title)`, `DirectoryChanged(UUID paneId, Optional<Path> directory)`, `CommandStarted(UUID paneId, String command)`, `CommandFinished(UUID paneId, String command, OptionalInt exitStatus, Duration duration, Optional<Path> directory)`, `SessionStarted(UUID paneId)`, `SessionExited(UUID paneId, OptionalInt exitStatus)`, `Bell(UUID tabId, UUID paneId)`
  - `final class TerminalRegistry` (EDT only): `Subscription addWindow(WindowEntry)`, `void windowActivated(UUID)`, `void publish(TerminalEvent)`, `void atomically(Runnable)`, `void refresh()`, `Subscription onEvent(Consumer<TerminalEvent>)`, `List<WindowEntry> windows()`, `Optional<WindowEntry> window(UUID)`, `Optional<WindowEntry> activeWindow()`, `Optional<PaneEntry> activePane()`, `Optional<TabEntry> tab(UUID)`, `Optional<PaneEntry> pane(UUID)`

Structure is pulled through the entries' suppliers, so it is never stale; facts are pushed as events. The registry owns one derived fact, the active pane, and reports it whenever it changes.

- [ ] **Step 1: Write the failing test**

`jasper-app/src/test/java/dev/jasper/app/terminals/TerminalRegistryTest.java`:

```java
package dev.jasper.app.terminals;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class TerminalRegistryTest {
    private final TerminalRegistry registry = new TerminalRegistry();
    private final List<TerminalEvent> events = new ArrayList<>();

    /** A window whose tabs and panes are plain lists the test mutates. */
    private static final class Window {
        final UUID id = UUID.randomUUID();
        final List<TabEntry> tabs = new ArrayList<>();
        TabEntry selected;
        final WindowEntry entry = new WindowEntry(id, () -> List.copyOf(tabs), () -> Optional.ofNullable(selected), () -> true, () -> { },
            directory -> Optional.empty());
    }

    private static final class Tab {
        final UUID id = UUID.randomUUID();
        final List<PaneEntry> panes = new ArrayList<>();
        PaneEntry focused;
        final TabEntry entry;
        Tab(Window window) { entry = new TabEntry(id, window.id, () -> List.copyOf(panes), () -> Optional.ofNullable(focused), () -> "tab", () -> { }); }
        PaneEntry pane() {
            var snapshot = new PaneSnapshot("sh", Optional.empty(), 80, 24, false, PaneSnapshot.State.RUNNING, OptionalInt.empty());
            var created = new PaneEntry(UUID.randomUUID(), id, () -> snapshot, () -> CompletableFuture.completedFuture(Optional.empty()),
                bytes -> { }, text -> { }, Optional::empty, () -> { }, (axis, directory) -> Optional.empty());
            panes.add(created);
            return created;
        }
    }

    @Test void windowsAreFoundByIdAndTheirTabsAndPanesThroughThem() {
        var window = new Window();
        var tab = new Tab(window);
        PaneEntry pane = tab.pane();
        window.tabs.add(tab.entry);
        var registration = registry.addWindow(window.entry);
        assertThat(registry.windows()).containsExactly(window.entry);
        assertThat(registry.window(window.id)).contains(window.entry);
        assertThat(registry.tab(tab.id)).contains(tab.entry);
        assertThat(registry.pane(pane.id())).contains(pane);
        assertThat(registry.pane(UUID.randomUUID())).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> registry.addWindow(window.entry));
        registration.close();
        registration.close();
        assertThat(registry.windows()).isEmpty();
        assertThat(registry.pane(pane.id())).isEmpty();
    }

    @Test void theActivePaneFollowsActivationSelectionFocusAndClosing() {
        registry.onEvent(events::add);
        var first = new Window(); var second = new Window();
        var tab = new Tab(first); var other = new Tab(second);
        PaneEntry left = tab.pane(), right = tab.pane(), lone = other.pane();
        first.tabs.add(tab.entry); first.selected = tab.entry; tab.focused = left;
        second.tabs.add(other.entry); second.selected = other.entry; other.focused = lone;
        var closeFirst = registry.addWindow(first.entry);
        registry.addWindow(second.entry);
        assertThat(registry.activeWindow()).as("nothing was activated yet").isEmpty();
        assertThat(registry.activePane()).isEmpty();

        registry.windowActivated(first.id);
        assertThat(registry.activePane()).contains(left);
        tab.focused = right;
        registry.publish(new TerminalEvent.PaneFocused(tab.id, right.id()));
        registry.publish(new TerminalEvent.PaneFocused(tab.id, right.id()));
        registry.windowActivated(second.id);
        registry.windowActivated(UUID.randomUUID());
        closeFirst.close();
        assertThat(events).containsExactly(
            new TerminalEvent.WindowOpened(first.id), new TerminalEvent.WindowOpened(second.id),
            new TerminalEvent.WindowActivated(first.id), new TerminalEvent.ActivePaneChanged(Optional.of(left.id())),
            new TerminalEvent.PaneFocused(tab.id, right.id()), new TerminalEvent.ActivePaneChanged(Optional.of(right.id())),
            new TerminalEvent.PaneFocused(tab.id, right.id()),
            new TerminalEvent.WindowActivated(second.id), new TerminalEvent.ActivePaneChanged(Optional.of(lone.id())),
            new TerminalEvent.WindowClosed(first.id));

        events.clear();
        registry.windows().get(0);
        registry.window(second.id).orElseThrow();
        // Closing the active window leaves no active window until the user turns to another.
        registry.atomically(() -> { });
        assertThat(events).isEmpty();
    }

    @Test void anAtomicChangeReportsTheActivePaneOnceAndRefreshNoticesAQuietChange() {
        var window = new Window();
        var one = new Tab(window); var two = new Tab(window);
        PaneEntry a = one.pane(), b = two.pane();
        one.focused = a; two.focused = b;
        window.tabs.add(one.entry); window.tabs.add(two.entry); window.selected = one.entry;
        registry.addWindow(window.entry);
        registry.windowActivated(window.id);
        registry.onEvent(events::add);
        registry.atomically(() -> {
            one.panes.clear(); one.focused = null;
            registry.publish(new TerminalEvent.PaneClosed(one.id, a.id()));
            window.tabs.remove(one.entry);
            registry.publish(new TerminalEvent.TabClosed(window.id, one.id));
            window.selected = two.entry;
            registry.publish(new TerminalEvent.TabSelected(window.id, two.id));
        });
        assertThat(events).as("no transient 'no active pane' in the middle").containsExactly(
            new TerminalEvent.PaneClosed(one.id, a.id()), new TerminalEvent.TabClosed(window.id, one.id),
            new TerminalEvent.TabSelected(window.id, two.id), new TerminalEvent.ActivePaneChanged(Optional.of(b.id())));
        events.clear();
        PaneEntry c = two.pane();
        two.focused = c;
        registry.refresh();
        registry.refresh();
        assertThat(events).containsExactly(new TerminalEvent.ActivePaneChanged(Optional.of(c.id())));
    }

    @Test void aFailingListenerDoesNotStopTheOthersAndClosedSubscriptionsHearNothing() {
        List<String> heard = new ArrayList<>();
        registry.onEvent(event -> { throw new IllegalStateException("listener failure"); });
        var second = registry.onEvent(event -> heard.add("second"));
        registry.onEvent(event -> heard.add("third"));
        registry.publish(new TerminalEvent.Bell(UUID.randomUUID(), UUID.randomUUID()));
        second.close();
        registry.publish(new TerminalEvent.Bell(UUID.randomUUID(), UUID.randomUUID()));
        assertThat(heard).containsExactly("second", "third", "third");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation FAILS (package `dev.jasper.app.terminals` does not exist).

- [ ] **Step 3: Write the package**

`package-info.java`:

```java
/**
 * An app-native directory of terminal windows, tabs and panes for features that must not hold Swing objects:
 * windows register entries whose structure is pulled through suppliers, publish id-only facts as
 * {@link dev.jasper.app.terminals.TerminalEvent}s, and the registry derives the active pane. EDT only. The
 * application owns the {@link dev.jasper.app.terminals.TerminalRegistry}; each window closes its own registration.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.lifecycle.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.terminals;
```

`SplitAxis.java`:

```java
package dev.jasper.app.terminals;

/** Where a split places the new pane. */
public enum SplitAxis { RIGHT, DOWN }
```

`PaneSnapshot.java`:

```java
package dev.jasper.app.terminals;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** What a pane reports about itself at one moment. The grid is zero by zero until the session starts. */
public record PaneSnapshot(String title, Optional<Path> workingDirectory, int columns, int rows, boolean shellIntegration,
                           State state, OptionalInt exitStatus) {
    /** A session's life. */
    public enum State { STARTING, RUNNING, EXITED }

    public PaneSnapshot {
        Objects.requireNonNull(title); Objects.requireNonNull(workingDirectory); Objects.requireNonNull(state); Objects.requireNonNull(exitStatus);
    }
}
```

`PaneEntry.java`:

```java
package dev.jasper.app.terminals;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One open pane, as functions over it; nothing here retains a Swing type. Every function is EDT-only and
 * tolerates a pane that closed meanwhile. {@code foregroundJob} starts the query on a worker and returns
 * its future. {@code split} returns the new pane, or empty when this pane cannot be split now.
 */
public record PaneEntry(UUID id, UUID tabId, Supplier<PaneSnapshot> snapshot,
                        Supplier<CompletableFuture<Optional<String>>> foregroundJob, Consumer<byte[]> write, Consumer<String> paste,
                        Supplier<Optional<String>> selection, Runnable focus,
                        BiFunction<SplitAxis, Optional<Path>, Optional<PaneEntry>> split) {
    /** Entries are recreated on every query; a pane is its id. */
    @Override public boolean equals(Object other) { return other instanceof PaneEntry entry && entry.id.equals(id); }
    @Override public int hashCode() { return id.hashCode(); }
}
```

`TabEntry.java`:

```java
package dev.jasper.app.terminals;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** One open tab, as functions over it. EDT only. */
public record TabEntry(UUID id, UUID windowId, Supplier<List<PaneEntry>> panes, Supplier<Optional<PaneEntry>> focusedPane,
                       Supplier<String> title, Runnable select) {
    /** Entries are recreated on every query; a tab is its id. */
    @Override public boolean equals(Object other) { return other instanceof TabEntry entry && entry.id.equals(id); }
    @Override public int hashCode() { return id.hashCode(); }
}
```

`WindowEntry.java`:

```java
package dev.jasper.app.terminals;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One open terminal window, as functions over it. EDT only. {@code openTab} takes the directory to start in,
 * or empty for where New Tab would start, and returns the new tab's pane, or empty when the window is closing.
 */
public record WindowEntry(UUID id, Supplier<List<TabEntry>> tabs, Supplier<Optional<TabEntry>> selectedTab, BooleanSupplier active,
                          Runnable toFront, Function<Optional<Path>, Optional<PaneEntry>> openTab) {
    /** A window is its id. */
    @Override public boolean equals(Object other) { return other instanceof WindowEntry entry && entry.id.equals(id); }
    @Override public int hashCode() { return id.hashCode(); }
}
```

`TerminalEvent.java`:

```java
package dev.jasper.app.terminals;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/** Closed family of id-only terminal facts, delivered on the EDT. Not an external extension API. */
public sealed interface TerminalEvent {
    record WindowOpened(UUID windowId) implements TerminalEvent { }
    record WindowClosed(UUID windowId) implements TerminalEvent { }
    record WindowActivated(UUID windowId) implements TerminalEvent { }
    record TabOpened(UUID windowId, UUID tabId) implements TerminalEvent { }
    record TabClosed(UUID windowId, UUID tabId) implements TerminalEvent { }
    record TabSelected(UUID windowId, UUID tabId) implements TerminalEvent { }
    record PaneOpened(UUID tabId, UUID paneId) implements TerminalEvent { }
    record PaneClosed(UUID tabId, UUID paneId) implements TerminalEvent { }
    record PaneFocused(UUID tabId, UUID paneId) implements TerminalEvent { }
    /** Derived by the registry; windows never publish it. */
    record ActivePaneChanged(Optional<UUID> paneId) implements TerminalEvent { }
    record TitleChanged(UUID paneId, String title) implements TerminalEvent { }
    record DirectoryChanged(UUID paneId, Optional<Path> directory) implements TerminalEvent { }
    record CommandStarted(UUID paneId, String command) implements TerminalEvent { }
    record CommandFinished(UUID paneId, String command, OptionalInt exitStatus, Duration duration,
                           Optional<Path> directory) implements TerminalEvent { }
    record SessionStarted(UUID paneId) implements TerminalEvent { }
    record SessionExited(UUID paneId, OptionalInt exitStatus) implements TerminalEvent { }
    record Bell(UUID tabId, UUID paneId) implements TerminalEvent { }
}
```

`TerminalRegistry.java`:

```java
package dev.jasper.app.terminals;

import dev.jasper.app.lifecycle.Subscription;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * The open terminal windows and what happens in them. Structure is always read through the entries, so
 * it cannot go stale; the one thing derived here is the active pane: the focused pane of the selected tab
 * of the window the user used last. EDT only.
 */
public final class TerminalRegistry {
    private static final System.Logger LOG = System.getLogger(TerminalRegistry.class.getName());
    private final Map<UUID, WindowEntry> windows = new LinkedHashMap<>();
    private final List<Consumer<TerminalEvent>> listeners = new ArrayList<>();
    private UUID lastActive;
    private UUID reportedActivePane;
    private int holding;

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("The terminal registry belongs to the EDT");
    }

    /** Registers a window and announces it. Closing the subscription removes and announces that. */
    public Subscription addWindow(WindowEntry entry) {
        requireEdt();
        Objects.requireNonNull(entry, "entry");
        if (windows.putIfAbsent(entry.id(), entry) != null) throw new IllegalArgumentException("Window already registered: " + entry.id());
        publish(new TerminalEvent.WindowOpened(entry.id()));
        return new Subscription(() -> {
            if (windows.remove(entry.id()) == null) return;
            if (entry.id().equals(lastActive)) lastActive = null;
            publish(new TerminalEvent.WindowClosed(entry.id()));
        });
    }

    /** The user turned to this window. Unknown ids are ignored. */
    public void windowActivated(UUID windowId) {
        requireEdt();
        if (!windows.containsKey(windowId)) return;
        lastActive = windowId;
        publish(new TerminalEvent.WindowActivated(windowId));
    }

    /** Forwards a window's fact to every listener, then reports the active pane if it changed. */
    public void publish(TerminalEvent event) {
        requireEdt();
        Objects.requireNonNull(event, "event");
        emit(event);
        if (holding == 0) refresh();
    }

    /** Runs a change that publishes several facts, and looks at the active pane only when it is complete. */
    public void atomically(Runnable change) {
        requireEdt();
        holding++;
        try { change.run(); }
        finally { if (--holding == 0) refresh(); }
    }

    /** Reports the active pane if it changed without an event, for example by keyboard navigation inside a tab. */
    public void refresh() {
        requireEdt();
        if (holding > 0) return;
        UUID now = activePane().map(PaneEntry::id).orElse(null);
        if (Objects.equals(now, reportedActivePane)) return;
        reportedActivePane = now;
        emit(new TerminalEvent.ActivePaneChanged(Optional.ofNullable(now)));
    }

    private void emit(TerminalEvent event) {
        for (Consumer<TerminalEvent> listener : List.copyOf(listeners)) {
            try { listener.accept(event); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A terminal event listener failed", failure); }
        }
    }

    public Subscription onEvent(Consumer<TerminalEvent> listener) {
        requireEdt();
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> listeners.remove(listener));
    }

    public List<WindowEntry> windows() { requireEdt(); return List.copyOf(windows.values()); }
    public Optional<WindowEntry> window(UUID id) { requireEdt(); return Optional.ofNullable(windows.get(id)); }
    public Optional<WindowEntry> activeWindow() { requireEdt(); return Optional.ofNullable(lastActive).map(windows::get); }

    public Optional<PaneEntry> activePane() {
        return activeWindow().flatMap(window -> window.selectedTab().get()).flatMap(tab -> tab.focusedPane().get());
    }

    public Optional<TabEntry> tab(UUID id) {
        requireEdt();
        for (WindowEntry window : windows.values())
            for (TabEntry tab : window.tabs().get()) if (tab.id().equals(id)) return Optional.of(tab);
        return Optional.empty();
    }

    public Optional<PaneEntry> pane(UUID id) {
        requireEdt();
        for (WindowEntry window : windows.values())
            for (TabEntry tab : window.tabs().get())
                for (PaneEntry pane : tab.panes().get()) if (pane.id().equals(id)) return Optional.of(pane);
        return Optional.empty();
    }
}
```

In the second test, the three statements after `events.clear()` only prove that reads publish nothing; keep them.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.terminals.*' verifyApplicationArchitecture :jasper-app:javadoc`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: add an app-native registry of terminal windows, tabs, panes and their events

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: Connecting windows to the registry

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowTerminals.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/{TerminalPane,TerminalTab,WindowContent,package-info}.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowTerminalsTest.java`

**Interfaces:**
- Consumes: Task 2
- Produces:
  - `public void WindowContent.connectTerminals(TerminalRegistry registry, Runnable toFront)`: once per window; announces the window, its tabs and panes
  - `TerminalTab.id()`; `TerminalPane TerminalTab.split(TerminalPane target, SplitTree.Axis axis, Path directoryOrNull)`: the new pane, or null when the tab is closed or the target is not a running pane of this tab
  - `TerminalTab WindowContent.openTab(Path directory)`: what `newTab` did, returning the tab (null when the window is closed)
  - `TerminalPane`: `PaneSnapshot snapshot()`, `CompletableFuture<Optional<String>> queryForegroundJob()`, `void write(byte[])`, `void paste(String)`, `Optional<String> selectedText()`; callbacks `onDirectoryChanged`, `onBell`, `onStarted`, `onExited`; `CommandFinished.accept` gains a fourth parameter `Optional<Path> workingDirectory`
- Event order: a new tab is `TabOpened`, `PaneOpened`, `TabSelected`; closing a tab is `PaneClosed`…, `TabClosed`, `TabSelected` of the neighbor, inside one `atomically`; closing the window closes every tab that way and ends with `WindowClosed`.

- [ ] **Step 1: Write the failing test**

`jasper-app/src/test/java/dev/jasper/app/workspace/WindowTerminalsTest.java`. It uses the suite's real `/bin/sh` fixture (`printf 'alpha alpha\n'; read answer`), so writing a line makes the shell exit with status 0:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.terminals.PaneEntry;
import dev.jasper.app.terminals.PaneSnapshot;
import dev.jasper.app.terminals.SplitAxis;
import dev.jasper.app.terminals.TabEntry;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.terminals.WindowEntry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(OS.WINDOWS)
class WindowTerminalsTest {
    private final Queue<Runnable> pending = new ArrayDeque<>();
    private final List<TerminalEvent> events = new ArrayList<>();
    private TerminalRegistry registry;
    private WindowContent owner;
    private int fronted;

    @AfterEach void close() throws Exception { closeOwners(); }

    private void open() throws Exception {
        edt(() -> {
            owner = content(launcher(pending));
            registry = new TerminalRegistry();
            registry.onEvent(events::add);
            owner.connectTerminals(registry, () -> fronted++);
            owner.connectTerminals(registry, () -> fronted++);
        });
    }

    private <T> T onEdt(java.util.function.Supplier<T> query) throws Exception {
        Object[] result = new Object[1];
        edt(() -> result[0] = query.get());
        @SuppressWarnings("unchecked") T value = (T) result[0];
        return value;
    }

    @Test void aWindowAnnouncesItselfAndAnswersQueriesBeforeAnyShellRuns() throws Exception {
        open();
        edt(() -> {
            WindowEntry window = registry.windows().get(0);
            TabEntry tab = window.tabs().get().get(0);
            PaneEntry pane = tab.panes().get().get(0);
            assertThat(window.id()).isEqualTo(owner.id());
            assertThat(window.selectedTab().get()).contains(tab);
            assertThat(tab.focusedPane().get()).contains(pane);
            assertThat(tab.windowId()).isEqualTo(window.id());
            assertThat(pane.tabId()).isEqualTo(tab.id());
            assertThat(events).containsExactly(new TerminalEvent.WindowOpened(window.id()), new TerminalEvent.TabOpened(window.id(), tab.id()),
                new TerminalEvent.PaneOpened(tab.id(), pane.id()), new TerminalEvent.TabSelected(window.id(), tab.id()));
            PaneSnapshot starting = pane.snapshot().get();
            assertThat(starting.state()).isEqualTo(PaneSnapshot.State.STARTING);
            assertThat(starting.columns()).isZero();
            assertThat(starting.workingDirectory()).contains(HOME);
            pane.write().accept("ignored".getBytes(StandardCharsets.UTF_8));
            pane.paste().accept("ignored");
            assertThat(pane.selection().get()).isEmpty();
            assertThat(pane.foregroundJob().get()).isCompletedWithValue(Optional.empty());
            assertThat(pane.split().apply(SplitAxis.RIGHT, Optional.empty())).as("nothing runs there yet").isEmpty();
            registry.windowActivated(window.id());
            assertThat(registry.activePane()).contains(pane);
            window.toFront().run();
            assertThat(fronted).isEqualTo(1);
        });
    }

    @Test void tabsSplitsInjectionAndExitAreReported() throws Exception {
        open();
        UUID windowId = owner.id();
        PaneEntry first = onEdt(() -> registry.windows().get(0).tabs().get().get(0).panes().get().get(0));
        pending.remove().run();
        until(() -> first.snapshot().get().state() == PaneSnapshot.State.RUNNING);
        edt(() -> {
            assertThat(events).contains(new TerminalEvent.SessionStarted(first.id()));
            assertThat(first.snapshot().get().columns()).isPositive();
        });

        Optional<PaneEntry> split = onEdt(() -> first.split().apply(SplitAxis.DOWN, Optional.empty()));
        assertThat(split).isPresent();
        edt(() -> {
            assertThat(events).contains(new TerminalEvent.PaneOpened(first.tabId(), split.get().id()));
            assertThat(registry.tab(first.tabId()).orElseThrow().panes().get()).containsExactly(first, split.get());
        });

        Optional<PaneEntry> opened = onEdt(() -> registry.window(windowId).orElseThrow().openTab().apply(Optional.of(HOME)));
        assertThat(opened).isPresent();
        UUID secondTab = opened.get().tabId();
        edt(() -> {
            int at = events.indexOf(new TerminalEvent.TabOpened(windowId, secondTab));
            assertThat(events.subList(at, at + 3)).containsExactly(new TerminalEvent.TabOpened(windowId, secondTab),
                new TerminalEvent.PaneOpened(secondTab, opened.get().id()), new TerminalEvent.TabSelected(windowId, secondTab));
            assertThat(registry.window(windowId).orElseThrow().selectedTab().get().map(TabEntry::id)).contains(secondTab);
        });

        edt(() -> first.write().accept("bye\n".getBytes(StandardCharsets.UTF_8)));
        until(() -> events.contains(new TerminalEvent.SessionExited(first.id(), OptionalInt.of(0))));
        edt(() -> {
            assertThat(first.snapshot().get().state()).isEqualTo(PaneSnapshot.State.EXITED);
            assertThat(first.snapshot().get().exitStatus()).hasValue(0);
            assertThat(registry.pane(first.id())).as("the pane stays open after its shell exits").isPresent();
        });

        edt(() -> {
            events.clear();
            registry.windowActivated(windowId);
            owner.closeTab(owner.currentTab());
            assertThat(events).containsExactly(new TerminalEvent.WindowActivated(windowId),
                new TerminalEvent.ActivePaneChanged(Optional.of(opened.get().id())),
                new TerminalEvent.PaneClosed(secondTab, opened.get().id()), new TerminalEvent.TabClosed(windowId, secondTab),
                new TerminalEvent.TabSelected(windowId, first.tabId()),
                new TerminalEvent.ActivePaneChanged(registry.activePane().map(PaneEntry::id)));
            events.clear();
            owner.close();
            assertThat(events.get(events.size() - 1)).isEqualTo(new TerminalEvent.WindowClosed(windowId));
            assertThat(events).contains(new TerminalEvent.TabClosed(windowId, first.tabId()));
            assertThat(registry.windows()).isEmpty();
        });
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation FAILS (`connectTerminals` not found).

- [ ] **Step 3: Grow `TerminalPane`**

Add imports `dev.jasper.app.terminals.PaneSnapshot`, `java.util.Optional`, `java.util.OptionalInt`, `java.util.concurrent.CompletableFuture` (skip any present). Change the `CommandFinished` interface and its default:

```java
    /** A command finished: its text, exit status, how long it ran and where. Delivered on the EDT. */
    interface CommandFinished {
        void accept(String command, java.util.OptionalInt exitStatus, java.time.Duration duration, java.util.Optional<Path> workingDirectory);
    }

    CommandFinished onCommandFinished = (command, exitStatus, duration, workingDirectory) -> {};
    /** The shell reported a working directory. Delivered on the EDT. */
    Consumer<java.util.Optional<Path>> onDirectoryChanged = directory -> {};
    /** The terminal rang the bell. Delivered on the EDT. */
    Runnable onBell = () -> {};
    /** The session started. Delivered on the EDT. */
    Runnable onStarted = () -> {};
    /** The session ended, with its exit status when known. Delivered on the EDT, before the exit policy runs. */
    Consumer<java.util.OptionalInt> onExited = status -> {};
```

In the session listener, replace `workingDirectoryChanged`, add `bell`, and pass the directory on:

```java
        @Override public void workingDirectoryChanged(Path directory) {
            queueUpdate();
            SwingUtilities.invokeLater(() -> { if (!closed) onDirectoryChanged.accept(java.util.Optional.ofNullable(directory)); });
        }
        @Override public void bell() { SwingUtilities.invokeLater(() -> { if (!closed) onBell.run(); }); }
```

and in `commandExecuted`'s EDT block: `onCommandFinished.accept(command, exitStatus, duration, workingDirectory);`.

In `start()`, first thing inside the `exitFuture().whenComplete` EDT block after the `closed || session != created` guard:

```java
                onExited.accept(error == null && code != null ? java.util.OptionalInt.of(code) : java.util.OptionalInt.empty());
```

and directly after `onReady.accept(view); setActive(active);` add `onStarted.run();`.

Add below `session()`:

```java
    /** What this pane is right now, for the terminal registry. */
    PaneSnapshot snapshot() {
        if (session == null)
            return new PaneSnapshot(title(), Optional.of(launchDirectory), 0, 0, false, PaneSnapshot.State.STARTING, OptionalInt.empty());
        CompletableFuture<Integer> exit = session.exitFuture();
        boolean exited = exit.isDone();
        Integer code = exited && !exit.isCompletedExceptionally() ? exit.getNow(null) : null;
        return new PaneSnapshot(title(), session.workingDirectory(), session.columns(), session.rows(), session.shellIntegrationDetected(),
            exited ? PaneSnapshot.State.EXITED : PaneSnapshot.State.RUNNING, code == null ? OptionalInt.empty() : OptionalInt.of(code));
    }

    /** The foreground job, asked off the EDT like the pane's own poll; empty when nothing runs here. */
    CompletableFuture<Optional<String>> queryForegroundJob() {
        TerminalSession current = session;
        if (current == null || !running()) return CompletableFuture.completedFuture(Optional.empty());
        var result = new CompletableFuture<Optional<String>>();
        Thread.ofVirtual().name("jasper-foreground-job").start(() -> {
            try { result.complete(current.foregroundJob()); }
            catch (RuntimeException failure) { result.complete(Optional.empty()); }
        });
        return result;
    }

    /** Raw bytes to the running session; dropped when nothing runs here. */
    void write(byte[] bytes) { if (running()) session.write(bytes); }
    /** Through the view's paste path, so bracketed paste applies; dropped when nothing runs here. */
    void paste(String text) { if (running() && view != null) view.paste(text); }
    Optional<String> selectedText() { return view == null ? Optional.empty() : view.selectedText(); }
```

In `close()` reset the new callbacks next to the others: `onDirectoryChanged = directory -> {}; onBell = () -> {}; onStarted = () -> {}; onExited = status -> {};` and change the `onCommandFinished` reset to the four-parameter lambda. If `title()` dereferences `session` without a null check, guard it so a pane that has not started reports its running command or an empty title.

- [ ] **Step 4: Grow `TerminalTab`**

Add `private final UUID id = UUID.randomUUID();` and `public UUID id() { return id; }`, and replace `split(SplitTree.Axis)`:

```java
    public void split(SplitTree.Axis axis) { split(focusedPane(), axis, null); }

    /** Splits a given pane, starting the new one in {@code directoryOrNull} or where the target is. Null when it cannot. */
    TerminalPane split(TerminalPane target, SplitTree.Axis axis, Path directoryOrNull) {
        if (closed || target == null || panes.get(target.id()) != target || !target.running()) return null;
        if (focusedPane() != target) focus(target);
        TerminalPane pane = createPane(directoryOrNull == null ? target.directory() : directoryOrNull);
        tree.split(pane.id(), axis); render(); onChanged.run(); pane.start();
        return pane;
    }
```

- [ ] **Step 5: Write `WindowTerminals`**

```java
package dev.jasper.app.workspace;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.terminals.PaneEntry;
import dev.jasper.app.terminals.SplitAxis;
import dev.jasper.app.terminals.TabEntry;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.terminals.WindowEntry;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One window's presence in the terminal registry: entries that read this window's tabs and panes on demand,
 * and the facts the window publishes. Owned and closed by {@link WindowContent}. EDT only.
 */
final class WindowTerminals implements AutoCloseable {
    private final WindowContent owner;
    private final TerminalRegistry registry;
    private final Subscription registration;
    private UUID selected;
    private boolean closed;

    WindowTerminals(WindowContent owner, TerminalRegistry registry, Runnable toFront) {
        this.owner = owner; this.registry = registry;
        var entry = new WindowEntry(owner.id(), () -> owner.terminalTabs().stream().map(this::entry).toList(),
            () -> Optional.ofNullable(owner.currentTab()).map(this::entry), owner::isActiveAndOpen, toFront, this::openTab);
        registration = registry.addWindow(entry);
        registry.atomically(() -> {
            for (TerminalTab tab : owner.terminalTabs()) {
                tabOpened(tab);
                for (TerminalPane pane : tab.panes()) publish(new TerminalEvent.PaneOpened(tab.id(), pane.id()));
            }
            tabSelected();
        });
    }

    TabEntry entry(TerminalTab tab) {
        return new TabEntry(tab.id(), owner.id(), () -> tab.panes().stream().map(pane -> entry(tab, pane)).toList(),
            () -> Optional.ofNullable(tab.focusedPane()).map(pane -> entry(tab, pane)), tab::title, () -> owner.selectTab(tab));
    }

    PaneEntry entry(TerminalTab tab, TerminalPane pane) {
        return new PaneEntry(pane.id(), tab.id(), pane::snapshot, pane::queryForegroundJob, pane::write, pane::paste, pane::selectedText,
            () -> { owner.selectTab(tab); tab.focus(pane); pane.focusTerminal(); },
            (axis, directory) -> Optional.ofNullable(tab.split(pane, axis == SplitAxis.RIGHT ? SplitTree.Axis.RIGHT : SplitTree.Axis.DOWN,
                directory.orElse(null))).map(created -> entry(tab, created)));
    }

    private Optional<PaneEntry> openTab(Optional<Path> directory) {
        if (closed) return Optional.empty();
        TerminalTab tab = owner.openTab(directory.orElseGet(owner::directory));
        return tab == null || tab.focusedPane() == null ? Optional.empty() : Optional.of(entry(tab, tab.focusedPane()));
    }

    void publish(TerminalEvent event) { if (!closed) registry.publish(event); }
    void atomically(Runnable change) { if (closed) change.run(); else registry.atomically(change); }
    void refresh() { if (!closed) registry.refresh(); }

    void tabOpened(TerminalTab tab) { publish(new TerminalEvent.TabOpened(owner.id(), tab.id())); }
    void tabClosed(TerminalTab tab) { publish(new TerminalEvent.TabClosed(owner.id(), tab.id())); }

    /** Reports the selected tab when it actually changed; Swing fires selection events for other reasons too. */
    void tabSelected() {
        TerminalTab current = owner.currentTab();
        UUID now = current == null ? null : current.id();
        if (Objects.equals(now, selected)) return;
        selected = now;
        if (now != null) publish(new TerminalEvent.TabSelected(owner.id(), now));
    }

    @Override public void close() {
        if (closed) return;
        registration.close();
        closed = true;
    }
}
```

- [ ] **Step 6: Wire `WindowContent`**

Add imports `dev.jasper.app.terminals.TerminalEvent`, `dev.jasper.app.terminals.TerminalRegistry`; a field `private WindowTerminals terminals;`; and:

```java
    /** Connects this window to the application-wide terminal registry, once. {@code toFront} raises the native window. */
    public void connectTerminals(TerminalRegistry registry, Runnable toFront) {
        if (closed || terminals != null) return;
        terminals = new WindowTerminals(this, Objects.requireNonNull(registry), Objects.requireNonNull(toFront));
    }

    java.util.List<TerminalTab> terminalTabs() {
        var result = new ArrayList<TerminalTab>();
        for (int i = 0; i < tabs.getTabCount(); i++) result.add((TerminalTab) tabs.getComponentAt(i));
        return result;
    }
```

Replace `newTab` with a returning form, announcing the tab before its first pane:

```java
    public void newTab(Path directory) { openTab(directory); }

    TerminalTab openTab(Path directory) {
        if (closed) return null;
        TerminalTab tab = new TerminalTab(directory, launcher);
        if (terminals != null) terminals.tabOpened(tab);
```

keeping the rest of the old body and ending it with `return tab;`. In the tabs change listener, inside `if (!rearranging) {`, add first `if (terminals != null) terminals.tabSelected();`. At the end of `update()` add `if (terminals != null) terminals.refresh();`.

Replace `closeTab`:

```java
    public void closeTab(TerminalTab tab) {
        int index = tabs.indexOfComponent(tab);
        if (index < 0) return;
        Runnable change = () -> {
            tab.close();
            if (terminals != null) terminals.tabClosed(tab);
            tabs.removeTabAt(index); update();
        };
        if (terminals != null) terminals.atomically(change); else change.run();
        if (tabs.getTabCount() == 0 && !closed) onEmpty.run();
    }
```

Replace `connectActivity` so every pane fact also reaches the registry:

```java
    private void connectActivity(TerminalTab tab, TerminalPane pane) {
        emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.OPENED));
        report(new TerminalEvent.PaneOpened(tab.id(), pane.id()));
        pane.onCommandStarted = command -> {
            long startedAt = System.nanoTime();
            emit(new WorkspaceActivity.Started(pane.id(), command, () -> System.nanoTime() - startedAt,
                () -> { selectTab(tab); tab.focus(pane); pane.focusTerminal(); },
                pane.watched() && isActiveAndOpen() && tab == currentTab()));
            report(new TerminalEvent.CommandStarted(pane.id(), command));
        };
        pane.onTitleChanged = title -> {
            emit(new WorkspaceActivity.TitleChanged(pane.id(), title));
            report(new TerminalEvent.TitleChanged(pane.id(), title));
        };
        pane.onClosed = () -> {
            emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.CLOSED));
            report(new TerminalEvent.PaneClosed(tab.id(), pane.id()));
        };
        pane.onPaneFocused = () -> {
            emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.FOCUSED));
            report(new TerminalEvent.PaneFocused(tab.id(), pane.id()));
        };
        pane.onPaneBlurred = () -> emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.BLURRED));
        pane.onCommandFinished = (command, exitStatus, duration, workingDirectory) -> {
            emit(new WorkspaceActivity.Finished(pane.id(), command, exitStatus, duration,
                new WorkspaceActivity.Origin(anyWindowActive.getAsBoolean(), isActiveAndOpen(),
                    tab == currentTab(), pane.view() != null && pane.view().isFocusOwner()),
                () -> { selectTab(tab); tab.focus(pane); pane.focusTerminal(); }));
            report(new TerminalEvent.CommandFinished(pane.id(), command, exitStatus, duration, workingDirectory));
        };
        pane.onDirectoryChanged = directory -> report(new TerminalEvent.DirectoryChanged(pane.id(), directory));
        pane.onBell = () -> report(new TerminalEvent.Bell(tab.id(), pane.id()));
        pane.onStarted = () -> report(new TerminalEvent.SessionStarted(pane.id()));
        pane.onExited = status -> report(new TerminalEvent.SessionExited(pane.id(), status));
    }

    private void report(TerminalEvent event) { if (terminals != null) terminals.publish(event); }
```

`connectActivity` runs for a new tab's first pane before `connectTerminals` could have seen the tab, which is why `openTab` announces the tab first; a window connected after it was built has its existing tabs and panes announced by `WindowTerminals`' constructor, and `report` is a no-op before that.

In `close()`, replace the loop that closes the tabs with:

```java
        Runnable closeTabs = () -> {
            for (TerminalTab tab : terminalTabs()) { tab.close(); if (terminals != null) terminals.tabClosed(tab); }
        };
        if (terminals != null) terminals.atomically(closeTabs); else closeTabs.run();
        tabs.removeAll(); removeRootBindings();
        if (terminals != null) { terminals.close(); terminals = null; }
```

In `workspace/package-info.java` add `dev.jasper.app.terminals` to the allowed outgoing dependencies.

- [ ] **Step 7: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.application.*' verifyApplicationArchitecture`
Expected: PASS. If `tabsSplitsInjectionAndExitAreReported` fails on the exact event list around `closeTab`, print `events` and compare with the order promised under Interfaces: that order is the contract; fix the code, not the list.

- [ ] **Step 8: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: report each window's tabs, panes and terminal facts to the registry

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: Handles, the capability gate and the fake workspace

This task grows `WindowHandle` and `PaneHandle`, which the app and the testkit both implement, so it changes the SDK and both implementations together. Nothing in it is reachable from a plugin until Task 6 adds `PluginContext.terminals()`; action contexts and panel hosts, however, hand out full handles from here on.

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/{WindowHandle,PaneHandle}.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/{CapabilityGate,HostedTerminals}.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/{HostedUi,HostedContext,PluginHost,PluginRuntime,package-info}.java`, `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Create: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/{FakeWorkspace,FakeTerminals}.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/{FakeUi,FakePluginContext,FakePluginHost}.java`
- Test: create `jasper-app/src/test/java/dev/jasper/app/plugins/{TerminalFixture,HostedTerminalsTest}.java`, `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeTerminalsTest.java`; update `HostedUiTest`, `AppContractTest`, `PluginRuntimeTest`, `BundledSamplePluginTest`, `jasper-app/src/test/java/dev/jasper/app/pluginmanager/PluginManagerTest.java`

**Interfaces:**
- Produces:
  - `WindowHandle`: `id()`, `List<TabHandle> tabs()`, `Optional<TabHandle> activeTab()`, `boolean isActive()`, `boolean isOpen()`, `void toFront()`
  - `PaneHandle`: `id()`, `TabHandle tab()`, `PaneInfo info()`, `CompletableFuture<Optional<String>> foregroundJob()`, `void sendText(String)`, `void sendBytes(byte[])`, `void paste(String)`, `Optional<String> selection()`, `void focus()`, `boolean isOpen()`
  - Handles of one kind are equal when their ids are equal, in both implementations.
  - `CapabilityGate(String pluginId, Set<String> granted)`: `void require(String capability)`, `boolean has(String capability)`, `void audit(String capability, String what)`
  - `HostedTerminals(String pluginId, CapabilityGate gate, TerminalRegistry registry, Consumer<Runnable> ui, BooleanSupplier onUi, BooleanSupplier open)` implements `Terminals`, plus `WindowHandle windowHandle(UUID)`, `PaneHandle paneHandle(UUID)`
  - `HostedUi`'s constructor gains a final parameter `HostedTerminals terminals`; `PluginHost.Environment` a tenth component `TerminalRegistry terminals`; `PluginRuntime`'s constructor a sixth parameter `TerminalRegistry terminals`
  - Test fixture `TerminalFixture` (app tests): `registry`, `UUID addWindow()`, `UUID addTab(UUID window, String title)`, `UUID addPane(UUID tab, String title, Path directory)`, `void activateWindow(UUID)`, `void focusPane(UUID)`, `void closePane(UUID)`, `void select(UUID pane, String text)`, `List<String> sent(UUID pane)`, `List<String> opened()`, `void finishCommand(UUID pane, String command, int exitStatus)`
  - `FakePluginHost`: `UUID addTerminalWindow()`, `UUID addTerminalTab(UUID windowId, String title)`, `UUID addTerminalPane(UUID tabId, PaneInfo info)`, `void activateTerminalWindow(UUID)`, `void focusTerminalPane(UUID)`, `void closeTerminalPane(UUID)`, `void setPaneInfo(UUID, PaneInfo)`, `void setSelection(UUID, String)`, `void setForegroundJob(UUID, String)`, `List<String> sent(UUID paneId)`, `List<String> openRequests()`
- **Formats both implementations' test drivers share:** `sent` lines are `write:<the bytes as UTF-8 text>` for `sendText` and `sendBytes`, and `paste:<text>`; open requests are `tab|<window id>|<directory or ->` and `split|<pane id>|<RIGHT or DOWN>|<directory or ->`. A pane whose last sibling closes takes its tab with it, and a tab its window.

- [ ] **Step 1: Write the failing tests**

`jasper-app/src/test/java/dev/jasper/app/plugins/TerminalFixture.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.terminals.PaneEntry;
import dev.jasper.app.terminals.PaneSnapshot;
import dev.jasper.app.terminals.TabEntry;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.terminals.WindowEntry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** A scripted workspace behind a real {@link TerminalRegistry}: what windows do, without windows. EDT only. */
final class TerminalFixture {
    private static final class Pane { UUID id = UUID.randomUUID(); Tab tab; String title; Path directory; String selection; List<String> sent = new ArrayList<>(); }
    private static final class Tab { UUID id = UUID.randomUUID(); Window window; String title; List<Pane> panes = new ArrayList<>(); Pane focused; }
    private static final class Window { UUID id = UUID.randomUUID(); List<Tab> tabs = new ArrayList<>(); Tab selected; Subscription registration; }

    final TerminalRegistry registry = new TerminalRegistry();
    private final Map<UUID, Window> windows = new LinkedHashMap<>();
    private final Map<UUID, Tab> tabs = new LinkedHashMap<>();
    private final Map<UUID, Pane> panes = new LinkedHashMap<>();
    /** Kept so a test can still read what was sent to a pane that closed, as the testkit's fake allows. */
    private final Map<UUID, Pane> closedPanes = new LinkedHashMap<>();
    private final List<String> opened = new ArrayList<>();

    private PaneEntry entry(Pane pane) {
        return new PaneEntry(pane.id, pane.tab.id,
            () -> new PaneSnapshot(pane.title, Optional.ofNullable(pane.directory), 80, 24, true, PaneSnapshot.State.RUNNING, OptionalInt.empty()),
            () -> CompletableFuture.completedFuture(Optional.of("vim")),
            bytes -> pane.sent.add("write:" + new String(bytes, StandardCharsets.UTF_8)), text -> pane.sent.add("paste:" + text),
            () -> Optional.ofNullable(pane.selection), () -> focusPane(pane.id),
            (axis, directory) -> {
                opened.add("split|" + pane.id + "|" + axis + "|" + directory.map(Path::toString).orElse("-"));
                UUID created = addPane(pane.tab.id, "split", directory.orElse(pane.directory));
                focusPane(created);
                return Optional.of(entry(panes.get(created)));
            });
    }

    private TabEntry entry(Tab tab) {
        return new TabEntry(tab.id, tab.window.id, () -> tab.panes.stream().map(this::entry).toList(),
            () -> Optional.ofNullable(tab.focused).map(this::entry), () -> tab.title, () -> selectTab(tab));
    }

    UUID addWindow() {
        var window = new Window();
        windows.put(window.id, window);
        window.registration = registry.addWindow(new WindowEntry(window.id, () -> window.tabs.stream().map(this::entry).toList(),
            () -> Optional.ofNullable(window.selected).map(this::entry), () -> registry.activeWindow().map(WindowEntry::id).equals(Optional.of(window.id)),
            () -> opened.add("front|" + window.id), directory -> {
                opened.add("tab|" + window.id + "|" + directory.map(Path::toString).orElse("-"));
                UUID tab = addTab(window.id, "opened");
                return Optional.of(entry(panes.get(addPane(tab, "opened", directory.orElse(null)))));
            }));
        return window.id;
    }

    UUID addTab(UUID windowId, String title) {
        Window window = windows.get(windowId);
        var tab = new Tab(); tab.window = window; tab.title = title;
        tabs.put(tab.id, tab);
        window.tabs.add(tab);
        registry.atomically(() -> { registry.publish(new TerminalEvent.TabOpened(window.id, tab.id)); selectTab(tab); });
        return tab.id;
    }

    private void selectTab(Tab tab) {
        if (tab.window.selected == tab) return;
        tab.window.selected = tab;
        registry.publish(new TerminalEvent.TabSelected(tab.window.id, tab.id));
    }

    UUID addPane(UUID tabId, String title, Path directory) {
        Tab tab = tabs.get(tabId);
        var pane = new Pane(); pane.tab = tab; pane.title = title; pane.directory = directory;
        panes.put(pane.id, pane);
        tab.panes.add(pane);
        if (tab.focused == null) tab.focused = pane;
        registry.publish(new TerminalEvent.PaneOpened(tab.id, pane.id));
        return pane.id;
    }

    void activateWindow(UUID windowId) { registry.windowActivated(windowId); }

    void focusPane(UUID paneId) {
        Pane pane = panes.get(paneId);
        if (pane == null) return;
        pane.tab.focused = pane;
        registry.publish(new TerminalEvent.PaneFocused(pane.tab.id, pane.id));
    }

    /** The last pane takes its tab with it, and the last tab its window, as in the application. */
    void closePane(UUID paneId) {
        Pane pane = panes.remove(paneId);
        if (pane == null) return;
        closedPanes.put(paneId, pane);
        Tab tab = pane.tab; Window window = tab.window;
        registry.atomically(() -> {
            tab.panes.remove(pane);
            if (tab.focused == pane) tab.focused = tab.panes.isEmpty() ? null : tab.panes.get(0);
            registry.publish(new TerminalEvent.PaneClosed(tab.id, pane.id));
            if (!tab.panes.isEmpty()) return;
            tabs.remove(tab.id); window.tabs.remove(tab);
            registry.publish(new TerminalEvent.TabClosed(window.id, tab.id));
            if (window.selected == tab) { window.selected = null; if (!window.tabs.isEmpty()) selectTab(window.tabs.get(0)); }
            if (window.tabs.isEmpty()) { windows.remove(window.id); window.registration.close(); }
        });
    }

    void select(UUID paneId, String text) { panes.get(paneId).selection = text; }
    List<String> sent(UUID paneId) {
        Pane pane = panes.containsKey(paneId) ? panes.get(paneId) : closedPanes.get(paneId);
        return pane == null ? List.of() : List.copyOf(pane.sent);
    }
    List<String> opened() { return List.copyOf(opened); }

    void finishCommand(UUID paneId, String command, int exitStatus) {
        registry.publish(new TerminalEvent.CommandFinished(paneId, command, OptionalInt.of(exitStatus), Duration.ofMillis(1500),
            Optional.ofNullable(panes.get(paneId)).map(pane -> pane.directory)));
    }
}
```

`jasper-app/src/test/java/dev/jasper/app/plugins/HostedTerminalsTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.terminal.Direction;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionState;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class HostedTerminalsTest {
    private final TerminalFixture fixture = new TerminalFixture();
    private final List<Runnable> posted = new ArrayList<>();
    private final AtomicBoolean onUi = new AtomicBoolean(true);
    private final AtomicBoolean open = new AtomicBoolean(true);

    private HostedTerminals terminals(String... capabilities) {
        return new HostedTerminals("dev.x.tool", new CapabilityGate("dev.x.tool", Set.of(capabilities)), fixture.registry, posted::add,
            onUi::get, open::get);
    }

    @Test void queriesMirrorTheRegistryAndHandlesAreEqualById() {
        HostedTerminals terminals = terminals(Capabilities.TERMINAL_OBSERVE);
        assertThat(terminals.windows()).isEmpty();
        assertThat(terminals.activeWindow()).isEmpty();
        UUID window = fixture.addWindow(), tab = fixture.addTab(window, "build"), left = fixture.addPane(tab, "make", Path.of("/src"));
        UUID right = fixture.addPane(tab, "top", null);
        fixture.activateWindow(window);
        WindowHandle handle = terminals.windows().get(0);
        assertThat(handle).isEqualTo(terminals.activeWindow().orElseThrow()).isEqualTo(terminals.windowHandle(window)).hasSameHashCodeAs(terminals.windowHandle(window));
        assertThat(handle.isOpen()).isTrue();
        assertThat(handle.isActive()).isTrue();
        TabHandle tabHandle = handle.activeTab().orElseThrow();
        assertThat(handle.tabs()).containsExactly(tabHandle);
        assertThat(tabHandle.id()).isEqualTo(tab);
        assertThat(tabHandle.window()).isEqualTo(handle);
        assertThat(tabHandle.title()).isEqualTo("build");
        assertThat(tabHandle.panes()).extracting(PaneHandle::id).containsExactly(left, right);
        PaneHandle pane = terminals.activePane().orElseThrow();
        assertThat(pane.id()).isEqualTo(left);
        assertThat(pane).isEqualTo(terminals.pane(left).orElseThrow()).isEqualTo(tabHandle.activePane().orElseThrow());
        assertThat(pane.tab()).isEqualTo(tabHandle);
        assertThat(pane.info()).isEqualTo(new PaneInfo("make", Optional.of(Path.of("/src")), Optional.empty(), 80, 24, true, SessionKind.LOCAL,
            Optional.empty(), SessionState.RUNNING, java.util.OptionalInt.empty()));
        assertThat(pane.foregroundJob()).isCompletedWithValue(Optional.of("vim"));
        assertThat(terminals.tab(tab)).contains(tabHandle);
        assertThat(terminals.window(window)).contains(handle);
        assertThat(terminals.pane(UUID.randomUUID())).isEmpty();

        terminals.pane(right).orElseThrow().focus();
        assertThat(terminals.activePane().map(PaneHandle::id)).contains(right);
        handle.toFront();
        assertThat(fixture.opened()).containsExactly("front|" + window);
    }

    @Test void aClosedPaneKeepsItsLastValuesAndIgnoresCommands() {
        HostedTerminals terminals = terminals(Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_INJECT, Capabilities.TERMINAL_SELECTION,
            Capabilities.TERMINAL_OPEN);
        UUID window = fixture.addWindow(), tab = fixture.addTab(window, "build"), only = fixture.addPane(tab, "make", Path.of("/src"));
        PaneHandle pane = terminals.pane(only).orElseThrow();
        TabHandle tabHandle = pane.tab();
        WindowHandle windowHandle = tabHandle.window();
        assertThat(pane.info().title()).isEqualTo("make");
        assertThat(tabHandle.title()).isEqualTo("build");
        fixture.closePane(only);
        assertThat(pane.isOpen()).isFalse();
        assertThat(tabHandle.isOpen()).isFalse();
        assertThat(windowHandle.isOpen()).as("the last tab took the window with it").isFalse();
        assertThat(pane.info().title()).as("the last known value").isEqualTo("make");
        assertThat(tabHandle.title()).isEqualTo("build");
        assertThat(tabHandle.panes()).isEmpty();
        assertThat(windowHandle.tabs()).isEmpty();
        assertThat(pane.selection()).isEmpty();
        assertThat(pane.foregroundJob()).isCompletedWithValue(Optional.empty());
        assertThatCode(() -> { pane.sendText("late"); pane.paste("late"); pane.focus(); tabHandle.select(); windowHandle.toFront(); })
            .doesNotThrowAnyException();
        assertThat(terminals.paneHandle(UUID.randomUUID()).info()).isEqualTo(PaneInfo.unknown());
        assertThat(terminals.openTab(windowHandle, OpenRequest.local())).isEmpty();
    }

    @Test void everyGatedCallNamesTheMissingCapabilityBeforeAnythingHappens() {
        UUID window = fixture.addWindow(), tab = fixture.addTab(window, "build"), only = fixture.addPane(tab, "make", null);
        HostedTerminals bare = terminals();
        PaneHandle pane = bare.pane(only).orElseThrow();
        assertThat(pane.isOpen()).as("identity and structure need nothing").isTrue();
        assertThat(pane.tab().panes()).hasSize(1);
        for (var gated : List.<java.util.Map.Entry<String, Runnable>>of(
                java.util.Map.entry(Capabilities.TERMINAL_OBSERVE, pane::info), java.util.Map.entry(Capabilities.TERMINAL_OBSERVE, pane::foregroundJob),
                java.util.Map.entry(Capabilities.TERMINAL_OBSERVE, () -> pane.tab().title()),
                java.util.Map.entry(Capabilities.TERMINAL_SELECTION, pane::selection),
                java.util.Map.entry(Capabilities.TERMINAL_INJECT, () -> pane.sendText("x")),
                java.util.Map.entry(Capabilities.TERMINAL_INJECT, () -> pane.sendBytes(new byte[]{1})),
                java.util.Map.entry(Capabilities.TERMINAL_INJECT, () -> pane.paste("x")),
                java.util.Map.entry(Capabilities.TERMINAL_OPEN, () -> bare.openTab(bare.windowHandle(window), OpenRequest.local())),
                java.util.Map.entry(Capabilities.TERMINAL_OPEN, () -> bare.split(pane, Direction.RIGHT, OpenRequest.local())))) {
            assertThatThrownBy(gated.getValue()::run).isInstanceOfSatisfying(MissingCapabilityException.class, failure -> {
                assertThat(failure.pluginId()).isEqualTo("dev.x.tool");
                assertThat(failure.capability()).isEqualTo(gated.getKey());
            });
        }
        assertThat(fixture.sent(only)).isEmpty();
        assertThat(fixture.opened()).isEmpty();
    }

    @Test void injectionKeepsItsOrderFromAnyThreadAndSelectionIsRead() {
        HostedTerminals terminals = terminals(Capabilities.TERMINAL_INJECT, Capabilities.TERMINAL_SELECTION);
        UUID window = fixture.addWindow(), tab = fixture.addTab(window, "build"), only = fixture.addPane(tab, "make", null);
        PaneHandle pane = terminals.pane(only).orElseThrow();
        pane.sendText("ls\n");
        byte[] bytes = "é".getBytes(StandardCharsets.UTF_8);
        pane.sendBytes(bytes);
        bytes[0] = 0;
        pane.paste("pasted");
        assertThat(fixture.sent(only)).containsExactly("write:ls\n", "write:é", "paste:pasted");

        onUi.set(false);
        pane.sendText("one"); pane.sendText("two");
        assertThat(fixture.sent(only)).as("posted, not run on the caller's thread").hasSize(3);
        assertThatIllegalStateException().isThrownBy(pane::selection);
        assertThatIllegalStateException().isThrownBy(terminals::windows);
        onUi.set(true);
        posted.forEach(Runnable::run);
        assertThat(fixture.sent(only)).endsWith("write:one", "write:two");

        fixture.select(only, "selected text");
        assertThat(pane.selection()).contains("selected text");
        open.set(false);
        pane.sendText("after stop");
        assertThat(fixture.sent(only)).hasSize(5);
        assertThatIllegalStateException().isThrownBy(terminals::windows);
    }

    @Test void openingATabOrASplitReturnsTheNewPane() {
        HostedTerminals terminals = terminals(Capabilities.TERMINAL_OPEN);
        UUID window = fixture.addWindow(), tab = fixture.addTab(window, "build"), only = fixture.addPane(tab, "make", Path.of("/src"));
        PaneHandle opened = terminals.openTab(terminals.windowHandle(window), OpenRequest.localIn(Path.of("/tmp"))).orElseThrow();
        assertThat(opened.isOpen()).isTrue();
        assertThat(opened.tab().id()).isNotEqualTo(tab);
        PaneHandle split = terminals.split(terminals.paneHandle(only), Direction.DOWN, OpenRequest.local()).orElseThrow();
        assertThat(split.tab().id()).isEqualTo(tab);
        assertThat(fixture.opened()).containsExactly("tab|" + window + "|/tmp", "split|" + only + "|DOWN|-");
        assertThat(terminals.split(terminals.paneHandle(UUID.randomUUID()), Direction.RIGHT, OpenRequest.local())).isEmpty();
    }
}
```

`jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeTerminalsTest.java` checks the fake's own drivers; the contract suite (Task 6) checks that it behaves like the application:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionState;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FakeTerminalsTest {
    private static PaneInfo info(String title) {
        return new PaneInfo(title, Optional.of(Path.of("/src")), Optional.empty(), 80, 24, true, SessionKind.LOCAL, Optional.empty(),
            SessionState.RUNNING, OptionalInt.empty());
    }

    @Test void theHostScriptsAWorkspaceThatActionContextsSee() {
        try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "build"), pane = host.addTerminalPane(tab, info("make"));
            host.activateTerminalWindow(window);
            host.setSelection(pane, "selected");
            host.setForegroundJob(pane, "vim");
            var seen = new java.util.ArrayList<PaneHandle>();
            var context = host.start(new PluginInfo("dev.x.tool", "Tool", "1.0.0",
                Set.of(Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_INJECT)), Set.of(), Set.of(), plugin ->
                plugin.actions().register(dev.jasper.sdk.ui.ActionSpec.of("dev.x.tool.type", "Type"), invoked -> seen.add(invoked.pane().orElseThrow())));
            assertThat(context).isNotNull();
            assertThat(host.invoke("dev.x.tool.type", window, pane)).isTrue();
            PaneHandle handle = seen.get(0);
            assertThat(handle.info()).isEqualTo(info("make"));
            assertThat(handle.foregroundJob()).isCompletedWithValue(Optional.of("vim"));
            assertThatThrownBy(handle::selection).isInstanceOf(MissingCapabilityException.class);
            handle.sendText("make test\n");
            handle.paste("pasted");
            assertThat(host.sent(pane)).containsExactly("write:make test\n", "paste:pasted");
            host.setPaneInfo(pane, info("make test"));
            assertThat(handle.info().title()).isEqualTo("make test");
            host.closeTerminalPane(pane);
            assertThat(handle.isOpen()).isFalse();
            assertThat(handle.info().title()).isEqualTo("make test");
            assertThat(host.sent(pane)).as("kept for the test to read after the pane closed").hasSize(2);
            assertThat(host.openRequests()).isEmpty();
        }
    }
}
```

In `HostedUiTest` add fields above `ui`:

```java
    private final TerminalFixture terminalFixture = new TerminalFixture();
    private final HostedTerminals terminals = new HostedTerminals("dev.x.tool", new CapabilityGate("dev.x.tool", java.util.Set.of()),
        terminalFixture.registry, Runnable::run, () -> true, () -> true);
```

pass `terminals` as the last constructor argument of both `HostedUi`s, and replace the cast lambda `(dev.jasper.sdk.terminal.WindowHandle) UUID::randomUUID` with `terminals.windowHandle(UUID.randomUUID())`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:compileTestJava :jasper-app:compileTestJava`
Expected: compilation FAILS (`HostedTerminals`, `addTerminalWindow`, `PaneHandle.info` not found).

- [ ] **Step 3: Grow the two SDK handles**

`terminal/WindowHandle.java`:

```java
package dev.jasper.sdk.terminal;

import dev.jasper.sdk.WindowOwner;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** One terminal window. Handles keep an id and no Swing object; two handles for the same window are equal. EDT only. */
public interface WindowHandle extends WindowOwner {
    /**
     * The window's stable id.
     *
     * @return the id
     */
    UUID id();

    /**
     * The window's tabs, in display order; empty once the window is closed.
     *
     * @return the tabs
     */
    List<TabHandle> tabs();

    /**
     * The selected tab.
     *
     * @return the tab, or empty once the window is closed
     */
    Optional<TabHandle> activeTab();

    /**
     * Whether this window has the user's attention right now.
     *
     * @return true while it is the active window of a foreground Jasper
     */
    boolean isActive();

    /**
     * Whether the window still exists.
     *
     * @return true while it is open
     */
    boolean isOpen();

    /** Raises the window. Ignored once it is closed. */
    void toFront();
}
```

`terminal/PaneHandle.java`:

```java
package dev.jasper.sdk.terminal;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * One pane. Handles keep an id and no Swing object; two handles for the same pane are equal. A pane can
 * close at any moment: commands to a closed pane are ignored, queries return the last known value, and
 * {@link #isOpen()} tells. A missing capability always throws. EDT only, except the three sending methods.
 */
public interface PaneHandle {
    /**
     * The pane's stable id.
     *
     * @return the id
     */
    UUID id();

    /**
     * The tab the pane was last seen in.
     *
     * @return its handle
     */
    TabHandle tab();

    /**
     * A snapshot of the pane. Needs {@code terminal.observe}.
     *
     * @return the snapshot; the last known one once the pane is closed
     */
    PaneInfo info();

    /**
     * The name of the program in the foreground, which has to be asked of the operating system off the
     * event thread. Needs {@code terminal.observe}. The future completes on an application worker thread.
     *
     * @return the future name, empty when it is unknown or the pane is closed
     */
    CompletableFuture<Optional<String>> foregroundJob();

    /**
     * Types text into the pane, as UTF-8, adding nothing: end a command with a newline yourself.
     * Needs {@code terminal.inject}. Callable from any thread; calls from one thread keep their order.
     *
     * @param text what to type
     */
    void sendText(String text);

    /**
     * Writes raw bytes to the pane. Needs {@code terminal.inject}. Callable from any thread.
     *
     * @param bytes what to write; copied before this returns
     */
    void sendBytes(byte[] bytes);

    /**
     * Pastes text the way the user's paste does, bracketed when the program asked for that.
     * Needs {@code terminal.inject}. Callable from any thread.
     *
     * @param text what to paste
     */
    void paste(String text);

    /**
     * The text selected in the pane. Needs {@code terminal.selection}.
     *
     * @return the selection, or empty when there is none or the pane is closed
     */
    Optional<String> selection();

    /** Selects the pane's tab and gives the pane keyboard focus. Ignored once it is closed. */
    void focus();

    /**
     * Whether the pane still exists. A pane whose program exited may still be open.
     *
     * @return true while it is open
     */
    boolean isOpen();
}
```

- [ ] **Step 4: Write the gate and the application's handles**

`jasper-app/src/main/java/dev/jasper/app/plugins/CapabilityGate.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.MissingCapabilityException;
import java.util.Set;

/**
 * One plugin's capabilities. The resolver loads a user plugin only when the user consented to everything
 * it declares, and bundled and development plugins are consented in advance, so the declared set is the
 * granted set. The audit log names the plugin and the amount, never the content.
 */
final class CapabilityGate {
    private static final System.Logger AUDIT = System.getLogger("dev.jasper.app.plugins.audit");
    private final String pluginId;
    private final Set<String> granted;

    CapabilityGate(String pluginId, Set<String> granted) { this.pluginId = pluginId; this.granted = Set.copyOf(granted); }

    boolean has(String capability) { return granted.contains(capability); }

    void require(String capability) {
        if (!granted.contains(capability)) throw new MissingCapabilityException(pluginId, capability);
    }

    void audit(String capability, String what) {
        AUDIT.log(System.Logger.Level.INFO, "Plugin " + pluginId + " used " + capability + ": " + what);
    }
}
```

`jasper-app/src/main/java/dev/jasper/app/plugins/HostedTerminals.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.terminals.PaneEntry;
import dev.jasper.app.terminals.PaneSnapshot;
import dev.jasper.app.terminals.SplitAxis;
import dev.jasper.app.terminals.TabEntry;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.terminals.WindowEntry;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.terminal.Direction;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionState;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.Terminals;
import dev.jasper.sdk.terminal.WindowHandle;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * One plugin's view of the terminal registry. Handles hold ids and the last values they saw, never an entry:
 * every call looks the target up again, so a closed pane is simply absent. Gated methods ask the plugin's
 * {@link CapabilityGate} first. EDT only, except the three sending methods.
 */
final class HostedTerminals implements Terminals {
    private static final System.Logger LOG = System.getLogger(HostedTerminals.class.getName());
    private static final UUID NOWHERE = new UUID(0, 0);

    private final String pluginId;
    private final CapabilityGate gate;
    private final TerminalRegistry registry;
    private final Consumer<Runnable> ui;
    private final BooleanSupplier onUi;
    private final BooleanSupplier open;

    HostedTerminals(String pluginId, CapabilityGate gate, TerminalRegistry registry, Consumer<Runnable> ui, BooleanSupplier onUi,
                    BooleanSupplier open) {
        this.pluginId = pluginId; this.gate = gate; this.registry = registry; this.ui = ui; this.onUi = onUi; this.open = open;
    }

    private void requireUi(String what) {
        if (!open.getAsBoolean()) throw new IllegalStateException("Plugin context is closed: " + pluginId);
        if (!onUi.getAsBoolean()) throw new IllegalStateException(what + " must be called on the UI thread: " + pluginId);
    }

    private static PaneInfo info(PaneSnapshot snapshot) {
        SessionState state = switch (snapshot.state()) {
            case STARTING -> SessionState.CONNECTING; case RUNNING -> SessionState.RUNNING; case EXITED -> SessionState.EXITED;
        };
        return new PaneInfo(snapshot.title(), snapshot.workingDirectory(), Optional.empty(), snapshot.columns(), snapshot.rows(),
            snapshot.shellIntegration(), SessionKind.LOCAL, Optional.empty(), state, snapshot.exitStatus());
    }

    private final class Window implements WindowHandle {
        private final UUID id;
        Window(UUID id) { this.id = id; }
        @Override public UUID id() { return id; }
        @Override public List<TabHandle> tabs() {
            requireUi("tabs");
            return registry.window(id).map(window -> window.tabs().get().stream().<TabHandle>map(Tab::new).toList()).orElse(List.of());
        }
        @Override public Optional<TabHandle> activeTab() {
            requireUi("activeTab");
            return registry.window(id).flatMap(window -> window.selectedTab().get()).map(Tab::new);
        }
        @Override public boolean isActive() { requireUi("isActive"); return registry.window(id).map(window -> window.active().getAsBoolean()).orElse(false); }
        @Override public boolean isOpen() { requireUi("isOpen"); return registry.window(id).isPresent(); }
        @Override public void toFront() { requireUi("toFront"); registry.window(id).ifPresent(window -> window.toFront().run()); }
        @Override public boolean equals(Object other) { return other instanceof WindowHandle handle && handle.id().equals(id); }
        @Override public int hashCode() { return id.hashCode(); }
        @Override public String toString() { return "window " + id; }
    }

    private final class Tab implements TabHandle {
        private final UUID id;
        private UUID windowId;
        private String title = "";
        Tab(TabEntry entry) { this(entry.id(), entry.windowId()); }
        Tab(UUID id, UUID windowId) { this.id = id; this.windowId = windowId; }
        private Optional<TabEntry> entry() {
            Optional<TabEntry> found = registry.tab(id);
            found.ifPresent(entry -> windowId = entry.windowId());
            return found;
        }
        @Override public UUID id() { return id; }
        @Override public WindowHandle window() { requireUi("window"); entry(); return new Window(windowId); }
        @Override public List<PaneHandle> panes() {
            requireUi("panes");
            return entry().map(entry -> entry.panes().get().stream().<PaneHandle>map(Pane::new).toList()).orElse(List.of());
        }
        @Override public Optional<PaneHandle> activePane() { requireUi("activePane"); return entry().flatMap(entry -> entry.focusedPane().get()).map(Pane::new); }
        @Override public String title() {
            gate.require(Capabilities.TERMINAL_OBSERVE);
            requireUi("title");
            entry().ifPresent(entry -> title = entry.title().get());
            return title;
        }
        @Override public void select() { requireUi("select"); entry().ifPresent(entry -> entry.select().run()); }
        @Override public boolean isOpen() { requireUi("isOpen"); return entry().isPresent(); }
        @Override public boolean equals(Object other) { return other instanceof TabHandle handle && handle.id().equals(id); }
        @Override public int hashCode() { return id.hashCode(); }
        @Override public String toString() { return "tab " + id; }
    }

    private final class Pane implements PaneHandle {
        private final UUID id;
        private UUID tabId;
        private PaneInfo last = PaneInfo.unknown();
        Pane(PaneEntry entry) { this(entry.id(), entry.tabId()); }
        Pane(UUID id, UUID tabId) { this.id = id; this.tabId = tabId; }
        private Optional<PaneEntry> entry() {
            Optional<PaneEntry> found = registry.pane(id);
            found.ifPresent(entry -> tabId = entry.tabId());
            return found;
        }
        @Override public UUID id() { return id; }
        @Override public TabHandle tab() {
            requireUi("tab");
            entry();
            return registry.tab(tabId).<TabHandle>map(Tab::new).orElseGet(() -> new Tab(tabId, NOWHERE));
        }
        @Override public PaneInfo info() {
            gate.require(Capabilities.TERMINAL_OBSERVE);
            requireUi("info");
            entry().ifPresent(entry -> last = HostedTerminals.info(entry.snapshot().get()));
            return last;
        }
        @Override public CompletableFuture<Optional<String>> foregroundJob() {
            gate.require(Capabilities.TERMINAL_OBSERVE);
            requireUi("foregroundJob");
            return entry().map(entry -> entry.foregroundJob().get()).orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()));
        }
        @Override public void sendText(String text) {
            byte[] bytes = Objects.requireNonNull(text, "text").getBytes(StandardCharsets.UTF_8);
            inject("typed", bytes.length, entry -> entry.write().accept(bytes));
        }
        @Override public void sendBytes(byte[] bytes) {
            byte[] copy = Objects.requireNonNull(bytes, "bytes").clone();
            inject("wrote", copy.length, entry -> entry.write().accept(copy));
        }
        @Override public void paste(String text) {
            Objects.requireNonNull(text, "text");
            inject("pasted", text.getBytes(StandardCharsets.UTF_8).length, entry -> entry.paste().accept(text));
        }
        private void inject(String verb, int byteCount, Consumer<PaneEntry> action) {
            gate.require(Capabilities.TERMINAL_INJECT);
            if (!open.getAsBoolean()) return;
            gate.audit(Capabilities.TERMINAL_INJECT, verb + " " + byteCount + " bytes into pane " + id);
            Runnable deliver = () -> entry().ifPresentOrElse(action,
                () -> LOG.log(System.Logger.Level.DEBUG, "Plugin " + pluginId + " sent input to the closed pane " + id));
            if (onUi.getAsBoolean()) deliver.run(); else ui.accept(deliver);
        }
        @Override public Optional<String> selection() {
            gate.require(Capabilities.TERMINAL_SELECTION);
            requireUi("selection");
            Optional<String> selected = entry().flatMap(entry -> entry.selection().get());
            gate.audit(Capabilities.TERMINAL_SELECTION, "read " + selected.map(String::length).orElse(0) + " selected characters from pane " + id);
            return selected;
        }
        @Override public void focus() { requireUi("focus"); entry().ifPresent(entry -> entry.focus().run()); }
        @Override public boolean isOpen() { requireUi("isOpen"); return entry().isPresent(); }
        @Override public boolean equals(Object other) { return other instanceof PaneHandle handle && handle.id().equals(id); }
        @Override public int hashCode() { return id.hashCode(); }
        @Override public String toString() { return "pane " + id; }
    }

    /** A handle for an id an action or a panel was given; the window may already be gone. */
    WindowHandle windowHandle(UUID id) { return new Window(id); }

    /** A handle for an id an action was given; it finds its tab if the pane is open. */
    PaneHandle paneHandle(UUID id) { return registry.pane(id).<PaneHandle>map(Pane::new).orElseGet(() -> new Pane(id, NOWHERE)); }

    @Override public Optional<WindowHandle> activeWindow() { requireUi("activeWindow"); return registry.activeWindow().map(window -> new Window(window.id())); }
    @Override public Optional<PaneHandle> activePane() { requireUi("activePane"); return registry.activePane().map(Pane::new); }
    @Override public List<WindowHandle> windows() {
        requireUi("windows");
        return registry.windows().stream().<WindowHandle>map(window -> new Window(window.id())).toList();
    }
    @Override public Optional<PaneHandle> pane(UUID id) { requireUi("pane"); return registry.pane(id).map(Pane::new); }
    @Override public Optional<TabHandle> tab(UUID id) { requireUi("tab"); return registry.tab(id).map(Tab::new); }
    @Override public Optional<WindowHandle> window(UUID id) { requireUi("window"); return registry.window(id).map(window -> new Window(window.id())); }

    @Override public Optional<PaneHandle> openTab(WindowHandle window, OpenRequest request) {
        Objects.requireNonNull(window, "window");
        OpenRequest.Local local = local(request);
        requireUi("openTab");
        Optional<WindowEntry> target = registry.window(window.id());
        if (target.isEmpty()) return Optional.empty();
        gate.audit(Capabilities.TERMINAL_OPEN, "opened a tab in window " + window.id());
        return target.get().openTab().apply(local.spec().workingDirectory()).map(Pane::new);
    }

    @Override public Optional<PaneHandle> split(PaneHandle target, Direction direction, OpenRequest request) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(direction, "direction");
        OpenRequest.Local local = local(request);
        requireUi("split");
        Optional<PaneEntry> entry = registry.pane(target.id());
        if (entry.isEmpty()) return Optional.empty();
        gate.audit(Capabilities.TERMINAL_OPEN, "split pane " + target.id());
        return entry.get().split().apply(direction == Direction.RIGHT ? SplitAxis.RIGHT : SplitAxis.DOWN, local.spec().workingDirectory())
            .map(Pane::new);
    }

    /** Every request kind names its capability here, before anything else happens. */
    private OpenRequest.Local local(OpenRequest request) {
        return switch (Objects.requireNonNull(request, "request")) {
            case OpenRequest.Local local -> { gate.require(Capabilities.TERMINAL_OPEN); yield local; }
        };
    }
}
```

In `HostedUi`: add the constructor parameter and field `private final HostedTerminals terminals;` (last), delete `private static WindowHandle handle(java.util.UUID id)` and use `terminals.windowHandle(site.windowId())` in `host(PanelSite)`, and build action contexts with real handles:

```java
                    handler.accept(new Context(terminals.windowHandle(invocation.windowId()), invocation.paneId().map(terminals::paneHandle)))));
```

In `PluginHost.Environment` add the tenth component `TerminalRegistry terminals` (import `dev.jasper.app.terminals.TerminalRegistry`). In `HostedContext` add fields and build them before `ui`:

```java
    final CapabilityGate gate;
    final HostedTerminals terminals;
```

```java
        this.gate = new CapabilityGate(id, hosted.info().capabilities());
        this.terminals = new HostedTerminals(id, gate, host.environment.terminals(), host.environment.ui(), host.environment.onUi(),
            () -> state != State.CLOSED);
```

and pass `terminals` as `HostedUi`'s last argument. `PluginRuntime` takes `TerminalRegistry terminals` as its sixth constructor parameter (Javadoc: "the application-wide directory of terminal windows, tabs and panes"), keeps it in a field and passes it as the environment's tenth component. In `plugins/package-info.java` add `dev.jasper.app.terminals` to the allowed outgoing dependencies.

Update the constructions: `AppContractTest.host(...)` gains a `TerminalRegistry` parameter on its six-argument form (the shorter forms pass `onEdtValue(TerminalRegistry::new)`); `PluginRuntimeTest`, `BundledSamplePluginTest` and `PluginManagerTest` pass `new dev.jasper.app.terminals.TerminalRegistry()` inside their EDT blocks; `JasperApplication.startPlugins` passes `new dev.jasper.app.terminals.TerminalRegistry()` until Task 7 gives the application its real one.

- [ ] **Step 5: Write the fake workspace**

`jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeWorkspace.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.TerminalEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The windows, tabs and panes a test scripts, with the application's rules: the active pane is the focused
 * pane of the selected tab of the window activated last, the last pane takes its tab with it and the last
 * tab its window, and every change is announced on the terminal topics.
 */
final class FakeWorkspace {
    static final class Pane {
        final UUID id = UUID.randomUUID(); final Tab tab; PaneInfo info; String selection; String job; boolean open = true;
        final List<String> sent = new ArrayList<>();
        Pane(Tab tab, PaneInfo info) { this.tab = tab; this.info = info; }
    }
    static final class Tab {
        final UUID id = UUID.randomUUID(); final Window window; final String title; final List<Pane> panes = new ArrayList<>(); Pane focused; boolean open = true;
        Tab(Window window, String title) { this.window = window; this.title = title; }
    }
    static final class Window {
        final UUID id = UUID.randomUUID(); final List<Tab> tabs = new ArrayList<>(); Tab selected; boolean open = true;
    }

    private final FakePluginHost host;
    final List<Window> windows = new ArrayList<>();
    /** Every pane ever added, so a test can still read what was sent to one that closed. */
    private final List<Pane> everyPane = new ArrayList<>();
    private final List<Tab> everyTab = new ArrayList<>();
    final List<String> openRequests = new ArrayList<>();
    Window lastActive;
    private UUID reportedActive;

    FakeWorkspace(FakePluginHost host) { this.host = host; }

    Optional<Window> window(UUID id) { return windows.stream().filter(window -> window.id.equals(id)).findFirst(); }
    Optional<Tab> tab(UUID id) { return everyTab.stream().filter(tab -> tab.open && tab.id.equals(id)).findFirst(); }
    Optional<Pane> pane(UUID id) { return everyPane.stream().filter(pane -> pane.open && pane.id.equals(id)).findFirst(); }
    Optional<Pane> anyPane(UUID id) { return everyPane.stream().filter(pane -> pane.id.equals(id)).findFirst(); }
    Optional<Pane> activePane() {
        return Optional.ofNullable(lastActive).filter(window -> window.open).map(window -> window.selected).map(tab -> tab.focused);
    }

    private void refreshActive() {
        UUID now = activePane().map(pane -> pane.id).orElse(null);
        if (Objects.equals(now, reportedActive)) return;
        reportedActive = now;
        host.publishApp(TerminalEvents.ACTIVE_PANE_CHANGED, new TerminalEvents.ActivePaneChanged(Optional.ofNullable(now)));
    }

    Window addWindow() {
        var window = new Window();
        windows.add(window);
        host.publishApp(TerminalEvents.WINDOW_OPENED, new TerminalEvents.WindowEvent(window.id));
        return window;
    }

    Tab addTab(Window window, String title) {
        var tab = new Tab(window, title);
        everyTab.add(tab);
        window.tabs.add(tab);
        host.publishApp(TerminalEvents.TAB_OPENED, new TerminalEvents.TabEvent(window.id, tab.id));
        select(tab);
        return tab;
    }

    void select(Tab tab) {
        if (!tab.open || tab.window.selected == tab) return;
        tab.window.selected = tab;
        host.publishApp(TerminalEvents.TAB_SELECTED, new TerminalEvents.TabEvent(tab.window.id, tab.id));
        refreshActive();
    }

    Pane addPane(Tab tab, PaneInfo info) {
        var pane = new Pane(tab, info);
        everyPane.add(pane);
        tab.panes.add(pane);
        if (tab.focused == null) tab.focused = pane;
        host.publishApp(TerminalEvents.PANE_OPENED, new TerminalEvents.PaneEvent(tab.id, pane.id));
        refreshActive();
        return pane;
    }

    void activate(Window window) {
        if (!window.open) return;
        lastActive = window;
        host.publishApp(TerminalEvents.WINDOW_ACTIVATED, new TerminalEvents.WindowEvent(window.id));
        refreshActive();
    }

    void focus(Pane pane) {
        if (!pane.open) return;
        select(pane.tab);
        pane.tab.focused = pane;
        host.publishApp(TerminalEvents.PANE_FOCUSED, new TerminalEvents.PaneEvent(pane.tab.id, pane.id));
        refreshActive();
    }

    void close(Pane pane) {
        if (!pane.open) return;
        pane.open = false;
        Tab tab = pane.tab; Window window = tab.window;
        tab.panes.remove(pane);
        if (tab.focused == pane) tab.focused = tab.panes.isEmpty() ? null : tab.panes.get(0);
        host.publishApp(TerminalEvents.PANE_CLOSED, new TerminalEvents.PaneEvent(tab.id, pane.id));
        if (tab.panes.isEmpty()) {
            tab.open = false;
            window.tabs.remove(tab);
            host.publishApp(TerminalEvents.TAB_CLOSED, new TerminalEvents.TabEvent(window.id, tab.id));
            if (window.selected == tab) { window.selected = null; if (!window.tabs.isEmpty()) select(window.tabs.get(0)); }
            if (window.tabs.isEmpty()) {
                window.open = false;
                windows.remove(window);
                if (lastActive == window) lastActive = null;
                host.publishApp(TerminalEvents.WINDOW_CLOSED, new TerminalEvents.WindowEvent(window.id));
            }
        }
        refreshActive();
    }
}
```

`jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeTerminals.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.terminal.Direction;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PaneInfo;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.Terminals;
import dev.jasper.sdk.terminal.WindowHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** One fake plugin's view of the scripted workspace, with the application's capability rules. */
final class FakeTerminals implements Terminals {
    private final FakePluginContext context;
    private final FakeWorkspace workspace;

    FakeTerminals(FakePluginContext context, FakeWorkspace workspace) { this.context = context; this.workspace = workspace; }

    void require(String capability) {
        if (!context.plugin().capabilities().contains(capability)) throw new MissingCapabilityException(context.plugin().id(), capability);
    }

    private final class Window implements WindowHandle {
        private final UUID id;
        Window(UUID id) { this.id = id; }
        @Override public UUID id() { return id; }
        @Override public List<TabHandle> tabs() {
            context.requireOpen();
            return workspace.window(id).map(window -> window.tabs.stream().<TabHandle>map(tab -> new Tab(tab.id, id)).toList()).orElse(List.of());
        }
        @Override public Optional<TabHandle> activeTab() {
            context.requireOpen();
            return workspace.window(id).map(window -> window.selected).map(tab -> new Tab(tab.id, id));
        }
        @Override public boolean isActive() { context.requireOpen(); return workspace.lastActive != null && workspace.lastActive.open && workspace.lastActive.id.equals(id); }
        @Override public boolean isOpen() { context.requireOpen(); return workspace.window(id).isPresent(); }
        @Override public void toFront() { context.requireOpen(); workspace.window(id).ifPresent(window -> workspace.openRequests.add("front|" + id)); }
        @Override public boolean equals(Object other) { return other instanceof WindowHandle handle && handle.id().equals(id); }
        @Override public int hashCode() { return id.hashCode(); }
    }

    private final class Tab implements TabHandle {
        private final UUID id;
        private UUID windowId;
        private String title = "";
        Tab(UUID id, UUID windowId) { this.id = id; this.windowId = windowId; }
        private Optional<FakeWorkspace.Tab> model() {
            Optional<FakeWorkspace.Tab> found = workspace.tab(id);
            found.ifPresent(tab -> windowId = tab.window.id);
            return found;
        }
        @Override public UUID id() { return id; }
        @Override public WindowHandle window() { context.requireOpen(); model(); return new Window(windowId); }
        @Override public List<PaneHandle> panes() {
            context.requireOpen();
            return model().map(tab -> tab.panes.stream().<PaneHandle>map(pane -> new Pane(pane.id, id)).toList()).orElse(List.of());
        }
        @Override public Optional<PaneHandle> activePane() { context.requireOpen(); return model().map(tab -> tab.focused).map(pane -> new Pane(pane.id, id)); }
        @Override public String title() {
            require(Capabilities.TERMINAL_OBSERVE);
            context.requireOpen();
            model().ifPresent(tab -> title = tab.title);
            return title;
        }
        @Override public void select() { context.requireOpen(); model().ifPresent(workspace::select); }
        @Override public boolean isOpen() { context.requireOpen(); return model().isPresent(); }
        @Override public boolean equals(Object other) { return other instanceof TabHandle handle && handle.id().equals(id); }
        @Override public int hashCode() { return id.hashCode(); }
    }

    private final class Pane implements PaneHandle {
        private final UUID id;
        private UUID tabId;
        private PaneInfo last = PaneInfo.unknown();
        Pane(UUID id, UUID tabId) { this.id = id; this.tabId = tabId; }
        private Optional<FakeWorkspace.Pane> model() {
            Optional<FakeWorkspace.Pane> found = workspace.pane(id);
            found.ifPresent(pane -> tabId = pane.tab.id);
            return found;
        }
        @Override public UUID id() { return id; }
        @Override public TabHandle tab() {
            context.requireOpen();
            model();
            return new Tab(tabId, workspace.tab(tabId).map(tab -> tab.window.id).orElse(new UUID(0, 0)));
        }
        @Override public PaneInfo info() {
            require(Capabilities.TERMINAL_OBSERVE);
            context.requireOpen();
            model().ifPresent(pane -> last = pane.info);
            return last;
        }
        @Override public CompletableFuture<Optional<String>> foregroundJob() {
            require(Capabilities.TERMINAL_OBSERVE);
            context.requireOpen();
            return CompletableFuture.completedFuture(model().map(pane -> pane.job));
        }
        @Override public void sendText(String text) { send("write:" + Objects.requireNonNull(text, "text")); }
        @Override public void sendBytes(byte[] bytes) { send("write:" + new String(Objects.requireNonNull(bytes, "bytes"), StandardCharsets.UTF_8)); }
        @Override public void paste(String text) { send("paste:" + Objects.requireNonNull(text, "text")); }
        private void send(String line) {
            require(Capabilities.TERMINAL_INJECT);
            if (context.state == FakePluginContext.State.CLOSED) return;
            synchronized (workspace) { model().ifPresent(pane -> pane.sent.add(line)); }
        }
        @Override public Optional<String> selection() {
            require(Capabilities.TERMINAL_SELECTION);
            context.requireOpen();
            return model().map(pane -> pane.selection);
        }
        @Override public void focus() { context.requireOpen(); model().ifPresent(workspace::focus); }
        @Override public boolean isOpen() { context.requireOpen(); return model().isPresent(); }
        @Override public boolean equals(Object other) { return other instanceof PaneHandle handle && handle.id().equals(id); }
        @Override public int hashCode() { return id.hashCode(); }
    }

    WindowHandle windowHandle(UUID id) { return new Window(id); }
    PaneHandle paneHandle(UUID id) { return new Pane(id, workspace.pane(id).map(pane -> pane.tab.id).orElse(new UUID(0, 0))); }

    @Override public Optional<WindowHandle> activeWindow() {
        context.requireOpen();
        return Optional.ofNullable(workspace.lastActive).filter(window -> window.open).map(window -> new Window(window.id));
    }
    @Override public Optional<PaneHandle> activePane() { context.requireOpen(); return workspace.activePane().map(pane -> new Pane(pane.id, pane.tab.id)); }
    @Override public List<WindowHandle> windows() { context.requireOpen(); return workspace.windows.stream().<WindowHandle>map(window -> new Window(window.id)).toList(); }
    @Override public Optional<PaneHandle> pane(UUID id) { context.requireOpen(); return workspace.pane(id).map(pane -> new Pane(pane.id, pane.tab.id)); }
    @Override public Optional<TabHandle> tab(UUID id) { context.requireOpen(); return workspace.tab(id).map(tab -> new Tab(tab.id, tab.window.id)); }
    @Override public Optional<WindowHandle> window(UUID id) { context.requireOpen(); return workspace.window(id).map(window -> new Window(window.id)); }

    @Override public Optional<PaneHandle> openTab(WindowHandle window, OpenRequest request) {
        Objects.requireNonNull(window, "window");
        OpenRequest.Local local = local(request);
        context.requireOpen();
        return workspace.window(window.id()).map(target -> {
            workspace.openRequests.add("tab|" + target.id + "|" + local.spec().workingDirectory().map(Path::toString).orElse("-"));
            FakeWorkspace.Tab tab = workspace.addTab(target, "opened");
            FakeWorkspace.Pane pane = workspace.addPane(tab, openedInfo(local));
            return new Pane(pane.id, tab.id);
        });
    }

    @Override public Optional<PaneHandle> split(PaneHandle target, Direction direction, OpenRequest request) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(direction, "direction");
        OpenRequest.Local local = local(request);
        context.requireOpen();
        return workspace.pane(target.id()).map(model -> {
            workspace.openRequests.add("split|" + model.id + "|" + direction + "|" + local.spec().workingDirectory().map(Path::toString).orElse("-"));
            FakeWorkspace.Pane pane = workspace.addPane(model.tab, openedInfo(local));
            workspace.focus(pane);
            return new Pane(pane.id, model.tab.id);
        });
    }

    private static PaneInfo openedInfo(OpenRequest.Local local) {
        PaneInfo blank = PaneInfo.unknown();
        return new PaneInfo("", local.spec().workingDirectory(), Optional.empty(), 80, 24, false, blank.kind(), Optional.empty(),
            dev.jasper.sdk.terminal.SessionState.RUNNING, blank.exitStatus());
    }

    private OpenRequest.Local local(OpenRequest request) {
        return switch (Objects.requireNonNull(request, "request")) {
            case OpenRequest.Local local -> { require(Capabilities.TERMINAL_OPEN); yield local; }
        };
    }
}
```

In `FakePluginContext` add the field `final FakeTerminals terminals;`, set it in the constructor before `ui` with `this.terminals = new FakeTerminals(this, host.workspace);`, and add a concrete method (it becomes an `@Override` in Task 6):

```java
    /** The scripted windows, tabs and panes. */
    public dev.jasper.sdk.terminal.Terminals terminals() { return terminals; }
```

In `FakeUi` replace the three lambda handles: `invoke` builds `WindowHandle window = context.terminals.windowHandle(windowId);` and maps the pane id with `context.terminals::paneHandle`; `openPanel`'s `PanelHost.window()` returns `context.terminals.windowHandle(windowId)`.

In `FakePluginHost` add the field `final FakeWorkspace workspace = new FakeWorkspace(this);` (declare it before anything that starts a context) and the drivers, each with Javadoc carrying `@param` and `@return`:

```java
    public UUID addTerminalWindow() { return workspace.addWindow().id; }

    public UUID addTerminalTab(UUID windowId, String title) {
        return workspace.addTab(workspace.window(windowId).orElseThrow(() -> new IllegalArgumentException("No such window: " + windowId)), title).id;
    }

    public UUID addTerminalPane(UUID tabId, dev.jasper.sdk.terminal.PaneInfo info) {
        return workspace.addPane(workspace.tab(tabId).orElseThrow(() -> new IllegalArgumentException("No such tab: " + tabId)),
            Objects.requireNonNull(info, "info")).id;
    }

    public void activateTerminalWindow(UUID windowId) { workspace.window(windowId).ifPresent(workspace::activate); }
    public void focusTerminalPane(UUID paneId) { workspace.pane(paneId).ifPresent(workspace::focus); }
    public void closeTerminalPane(UUID paneId) { workspace.pane(paneId).ifPresent(workspace::close); }
    public void setPaneInfo(UUID paneId, dev.jasper.sdk.terminal.PaneInfo info) { workspace.pane(paneId).ifPresent(pane -> pane.info = Objects.requireNonNull(info, "info")); }
    public void setSelection(UUID paneId, String textOrNull) { workspace.pane(paneId).ifPresent(pane -> pane.selection = textOrNull); }
    public void setForegroundJob(UUID paneId, String nameOrNull) { workspace.pane(paneId).ifPresent(pane -> pane.job = nameOrNull); }

    public List<String> sent(UUID paneId) {
        synchronized (workspace) { return workspace.anyPane(paneId).map(pane -> List.copyOf(pane.sent)).orElse(List.of()); }
    }

    public List<String> openRequests() { return List.copyOf(workspace.openRequests); }
```

Document on `addTerminalWindow` that windows, tabs and panes announce themselves on the terminal topics like the application's, delivered by `flush()`; on `sent` and `openRequests` the line formats listed under Interfaces.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.pluginmanager.*' verifySdkArchitecture verifyApplicationArchitecture :jasper-app:javadoc`
Expected: PASS; both contract suites still pass their 17 cases.

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add jasper-sdk jasper-sdk-testkit jasper-app
git commit -m "feat: give plugins gated window, tab and pane handles in the app and the testkit

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: Terminal events

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/TerminalBridge.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/{HostedContext,PluginHost}.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/{FakePluginContext,FakePluginHost}.java`
- Test: create `jasper-app/src/test/java/dev/jasper/app/plugins/TerminalBridgeTest.java`; extend `FakeTerminalsTest`

**Interfaces:**
- Produces:
  - `TerminalBridge.connect(TerminalRegistry registry, EventBus bus)` returns the app `Subscription`; `PluginHost` connects it when constructed and closes it when it stops
  - Subscribing to a topic for which `TerminalEvents.owns` is true needs `terminal.observe`, in the app and in the fake; the first such subscription of a plugin is audited
  - `FakePluginHost` event drivers: `void commandStarted(UUID paneId, String command)`, `void commandFinished(UUID paneId, String command, OptionalInt exitStatus, Duration duration)`, `void titleChanged(UUID paneId, String title)`, `void cwdChanged(UUID paneId, Path directory)`, `void sessionExited(UUID paneId, OptionalInt exitStatus)`, `void bell(UUID paneId)`; `titleChanged`, `cwdChanged` and `sessionExited` also update the pane's `PaneInfo`
- Mapping: `SessionStarted` → `SESSION_STATE_CHANGED` with `RUNNING`; `SessionExited` → the same topic with `EXITED` and the status; `DirectoryChanged` and `CommandFinished` carry an empty remote directory in 4a.

- [ ] **Step 1: Write the failing tests**

`jasper-app/src/test/java/dev/jasper/app/plugins/TerminalBridgeTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.sdk.terminal.SessionState;
import dev.jasper.sdk.terminal.TerminalEvents;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class TerminalBridgeTest {
    private final List<Runnable> queued = new ArrayList<>();
    private final EventBus bus = new EventBus(queued::add, new Containment());
    private final TerminalFixture fixture = new TerminalFixture();
    private final List<Object> heard = new ArrayList<>();

    private void deliver() { while (!queued.isEmpty()) queued.remove(0).run(); }

    @Test void everyRegistryFactBecomesItsTopic() {
        var bridge = TerminalBridge.connect(fixture.registry, bus);
        for (var topic : List.of(TerminalEvents.WINDOW_OPENED, TerminalEvents.WINDOW_ACTIVATED, TerminalEvents.TAB_OPENED, TerminalEvents.TAB_SELECTED,
                TerminalEvents.PANE_OPENED, TerminalEvents.PANE_FOCUSED, TerminalEvents.PANE_CLOSED, TerminalEvents.TAB_CLOSED, TerminalEvents.WINDOW_CLOSED,
                TerminalEvents.ACTIVE_PANE_CHANGED, TerminalEvents.TITLE_CHANGED, TerminalEvents.CWD_CHANGED, TerminalEvents.COMMAND_STARTED,
                TerminalEvents.COMMAND_FINISHED, TerminalEvents.SESSION_STATE_CHANGED, TerminalEvents.BELL))
            bus.subscribe("dev.x.tool", topic, heard::add);
        UUID window = fixture.addWindow(), tab = fixture.addTab(window, "build"), pane = fixture.addPane(tab, "make", Path.of("/src"));
        fixture.activateWindow(window);
        fixture.registry.publish(new TerminalEvent.SessionStarted(pane));
        fixture.registry.publish(new TerminalEvent.TitleChanged(pane, "make test"));
        fixture.registry.publish(new TerminalEvent.DirectoryChanged(pane, Optional.of(Path.of("/src/app"))));
        fixture.registry.publish(new TerminalEvent.CommandStarted(pane, "make test"));
        fixture.finishCommand(pane, "make test", 2);
        fixture.registry.publish(new TerminalEvent.Bell(tab, pane));
        fixture.registry.publish(new TerminalEvent.SessionExited(pane, OptionalInt.of(0)));
        fixture.focusPane(pane);
        assertThat(heard).as("queued like every event").isEmpty();
        deliver();
        assertThat(heard).containsExactly(
            new TerminalEvents.WindowEvent(window), new TerminalEvents.TabEvent(window, tab), new TerminalEvents.TabEvent(window, tab),
            new TerminalEvents.PaneEvent(tab, pane), new TerminalEvents.WindowEvent(window), new TerminalEvents.ActivePaneChanged(Optional.of(pane)),
            new TerminalEvents.SessionStateChanged(pane, SessionState.RUNNING, OptionalInt.empty()),
            new TerminalEvents.TitleChanged(pane, "make test"),
            new TerminalEvents.CwdChanged(pane, Optional.of(Path.of("/src/app")), Optional.empty()),
            new TerminalEvents.CommandStarted(pane, "make test"),
            new TerminalEvents.CommandFinished(pane, "make test", OptionalInt.of(2), Duration.ofMillis(1500), Optional.of(Path.of("/src")), Optional.empty()),
            new TerminalEvents.PaneEvent(tab, pane),
            new TerminalEvents.SessionStateChanged(pane, SessionState.EXITED, OptionalInt.of(0)),
            new TerminalEvents.PaneEvent(tab, pane));
        heard.clear();
        fixture.closePane(pane);
        deliver();
        assertThat(heard).containsExactly(new TerminalEvents.PaneEvent(tab, pane), new TerminalEvents.TabEvent(window, tab),
            new TerminalEvents.WindowEvent(window), new TerminalEvents.ActivePaneChanged(Optional.empty()));
        bridge.close();
        fixture.addWindow();
        deliver();
        assertThat(heard).hasSize(4);
    }
}
```

If `EventBus` or `Containment` have different constructors, build them the way `EventBusTest` does.

Append to `FakeTerminalsTest`:

```java
    @Test void terminalTopicsNeedObserveAndTheDriversPublishThem() {
        try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "build"), pane = host.addTerminalPane(tab, info("make"));
            var heard = new java.util.ArrayList<Object>();
            host.start(new PluginInfo("dev.x.blind", "Blind", "1.0.0", Set.of()), Set.of(), Set.of(), plugin ->
                assertThatThrownBy(() -> plugin.events().subscribe(dev.jasper.sdk.terminal.TerminalEvents.BELL, heard::add))
                    .isInstanceOf(MissingCapabilityException.class));
            host.start(new PluginInfo("dev.x.tool", "Tool", "1.0.0", Set.of(Capabilities.TERMINAL_OBSERVE)), Set.of(), Set.of(), plugin -> {
                plugin.events().subscribe(dev.jasper.sdk.terminal.TerminalEvents.COMMAND_FINISHED, heard::add);
                plugin.events().subscribe(dev.jasper.sdk.terminal.TerminalEvents.TITLE_CHANGED, heard::add);
                plugin.events().subscribe(dev.jasper.sdk.terminal.TerminalEvents.SESSION_STATE_CHANGED, heard::add);
                plugin.events().subscribe(dev.jasper.sdk.terminal.TerminalEvents.ACTIVE_PANE_CHANGED, heard::add);
            });
            assertThat(host.failures()).isEmpty();
            host.flush();
            heard.clear();
            host.activateTerminalWindow(window);
            host.titleChanged(pane, "make test");
            host.commandFinished(pane, "make test", OptionalInt.of(2), java.time.Duration.ofSeconds(3));
            host.sessionExited(pane, OptionalInt.of(0));
            assertThat(heard).as("delivered by flush, like every event").isEmpty();
            host.flush();
            assertThat(heard).containsExactly(new dev.jasper.sdk.terminal.TerminalEvents.ActivePaneChanged(Optional.of(pane)),
                new dev.jasper.sdk.terminal.TerminalEvents.TitleChanged(pane, "make test"),
                new dev.jasper.sdk.terminal.TerminalEvents.CommandFinished(pane, "make test", OptionalInt.of(2), java.time.Duration.ofSeconds(3),
                    Optional.of(Path.of("/src")), Optional.empty()),
                new dev.jasper.sdk.terminal.TerminalEvents.SessionStateChanged(pane, SessionState.EXITED, OptionalInt.of(0)));
        }
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:compileTestJava :jasper-app:compileTestJava`
Expected: compilation FAILS (`TerminalBridge`, `titleChanged` not found).

- [ ] **Step 3: Implement the bridge and the subscription gate**

`jasper-app/src/main/java/dev/jasper/app/plugins/TerminalBridge.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.sdk.terminal.SessionState;
import dev.jasper.sdk.terminal.TerminalEvents;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Republishes the registry's facts as SDK topics. Publishing only enqueues, so plugin handlers run later on
 * the EDT like every other event, never inside the window code that reported the fact.
 */
final class TerminalBridge {
    private TerminalBridge() { }

    static Subscription connect(TerminalRegistry registry, EventBus bus) {
        return registry.onEvent(event -> {
            switch (event) {
                case TerminalEvent.WindowOpened fact -> bus.publish(EventBus.APP, TerminalEvents.WINDOW_OPENED, new TerminalEvents.WindowEvent(fact.windowId()));
                case TerminalEvent.WindowClosed fact -> bus.publish(EventBus.APP, TerminalEvents.WINDOW_CLOSED, new TerminalEvents.WindowEvent(fact.windowId()));
                case TerminalEvent.WindowActivated fact -> bus.publish(EventBus.APP, TerminalEvents.WINDOW_ACTIVATED, new TerminalEvents.WindowEvent(fact.windowId()));
                case TerminalEvent.TabOpened fact -> bus.publish(EventBus.APP, TerminalEvents.TAB_OPENED, new TerminalEvents.TabEvent(fact.windowId(), fact.tabId()));
                case TerminalEvent.TabClosed fact -> bus.publish(EventBus.APP, TerminalEvents.TAB_CLOSED, new TerminalEvents.TabEvent(fact.windowId(), fact.tabId()));
                case TerminalEvent.TabSelected fact -> bus.publish(EventBus.APP, TerminalEvents.TAB_SELECTED, new TerminalEvents.TabEvent(fact.windowId(), fact.tabId()));
                case TerminalEvent.PaneOpened fact -> bus.publish(EventBus.APP, TerminalEvents.PANE_OPENED, new TerminalEvents.PaneEvent(fact.tabId(), fact.paneId()));
                case TerminalEvent.PaneClosed fact -> bus.publish(EventBus.APP, TerminalEvents.PANE_CLOSED, new TerminalEvents.PaneEvent(fact.tabId(), fact.paneId()));
                case TerminalEvent.PaneFocused fact -> bus.publish(EventBus.APP, TerminalEvents.PANE_FOCUSED, new TerminalEvents.PaneEvent(fact.tabId(), fact.paneId()));
                case TerminalEvent.ActivePaneChanged fact -> bus.publish(EventBus.APP, TerminalEvents.ACTIVE_PANE_CHANGED, new TerminalEvents.ActivePaneChanged(fact.paneId()));
                case TerminalEvent.TitleChanged fact -> bus.publish(EventBus.APP, TerminalEvents.TITLE_CHANGED, new TerminalEvents.TitleChanged(fact.paneId(), fact.title()));
                case TerminalEvent.DirectoryChanged fact -> bus.publish(EventBus.APP, TerminalEvents.CWD_CHANGED,
                    new TerminalEvents.CwdChanged(fact.paneId(), fact.directory(), Optional.empty()));
                case TerminalEvent.CommandStarted fact -> bus.publish(EventBus.APP, TerminalEvents.COMMAND_STARTED, new TerminalEvents.CommandStarted(fact.paneId(), fact.command()));
                case TerminalEvent.CommandFinished fact -> bus.publish(EventBus.APP, TerminalEvents.COMMAND_FINISHED,
                    new TerminalEvents.CommandFinished(fact.paneId(), fact.command(), fact.exitStatus(), fact.duration(), fact.directory(), Optional.empty()));
                case TerminalEvent.SessionStarted fact -> bus.publish(EventBus.APP, TerminalEvents.SESSION_STATE_CHANGED,
                    new TerminalEvents.SessionStateChanged(fact.paneId(), SessionState.RUNNING, OptionalInt.empty()));
                case TerminalEvent.SessionExited fact -> bus.publish(EventBus.APP, TerminalEvents.SESSION_STATE_CHANGED,
                    new TerminalEvents.SessionStateChanged(fact.paneId(), SessionState.EXITED, fact.exitStatus()));
                case TerminalEvent.Bell fact -> bus.publish(EventBus.APP, TerminalEvents.BELL, new TerminalEvents.PaneEvent(fact.tabId(), fact.paneId()));
            }
        });
    }
}
```

In `PluginHost` add a field `private final dev.jasper.app.lifecycle.Subscription terminalBridge;`, set it at the end of the constructor with `terminalBridge = TerminalBridge.connect(environment.terminals(), bus);`, and close it first thing in `stop()`. Construction is on the EDT already, which the registry requires.

In `HostedContext.events().subscribe`, after `requireUi("subscribe");`:

```java
                if (TerminalEvents.owns(topic)) {
                    gate.require(Capabilities.TERMINAL_OBSERVE);
                    if (!observing) { observing = true; gate.audit(Capabilities.TERMINAL_OBSERVE, "subscribed to terminal events"); }
                }
```

with a field `private boolean observing;` and imports `dev.jasper.sdk.Capabilities`, `dev.jasper.sdk.terminal.TerminalEvents`.

- [ ] **Step 4: Implement the fake's gate and drivers**

In `FakePluginContext.events().subscribe`, after `requireOpen();`: `if (dev.jasper.sdk.terminal.TerminalEvents.owns(topic)) terminals.require(dev.jasper.sdk.Capabilities.TERMINAL_OBSERVE);`.

In `FakePluginHost` add, each with Javadoc carrying `@param`:

```java
    private static dev.jasper.sdk.terminal.PaneInfo with(dev.jasper.sdk.terminal.PaneInfo info, String title, Optional<Path> directory,
            dev.jasper.sdk.terminal.SessionState state, OptionalInt exitStatus) {
        return new dev.jasper.sdk.terminal.PaneInfo(title, directory, info.remoteDirectory(), info.columns(), info.rows(), info.shellIntegration(),
            info.kind(), info.providerPluginId(), state, exitStatus);
    }

    public void commandStarted(UUID paneId, String command) {
        workspace.pane(paneId).ifPresent(pane -> publishApp(TerminalEvents.COMMAND_STARTED, new TerminalEvents.CommandStarted(paneId, command)));
    }

    public void commandFinished(UUID paneId, String command, OptionalInt exitStatus, Duration duration) {
        workspace.pane(paneId).ifPresent(pane -> publishApp(TerminalEvents.COMMAND_FINISHED, new TerminalEvents.CommandFinished(paneId, command,
            exitStatus, duration, pane.info.workingDirectory(), pane.info.remoteDirectory())));
    }

    public void titleChanged(UUID paneId, String title) {
        workspace.pane(paneId).ifPresent(pane -> {
            pane.info = with(pane.info, title, pane.info.workingDirectory(), pane.info.state(), pane.info.exitStatus());
            publishApp(TerminalEvents.TITLE_CHANGED, new TerminalEvents.TitleChanged(paneId, title));
        });
    }

    public void cwdChanged(UUID paneId, Path directory) {
        workspace.pane(paneId).ifPresent(pane -> {
            pane.info = with(pane.info, pane.info.title(), Optional.ofNullable(directory), pane.info.state(), pane.info.exitStatus());
            publishApp(TerminalEvents.CWD_CHANGED, new TerminalEvents.CwdChanged(paneId, Optional.ofNullable(directory), Optional.empty()));
        });
    }

    public void sessionExited(UUID paneId, OptionalInt exitStatus) {
        workspace.pane(paneId).ifPresent(pane -> {
            pane.info = with(pane.info, pane.info.title(), pane.info.workingDirectory(), dev.jasper.sdk.terminal.SessionState.EXITED, exitStatus);
            publishApp(TerminalEvents.SESSION_STATE_CHANGED, new TerminalEvents.SessionStateChanged(paneId,
                dev.jasper.sdk.terminal.SessionState.EXITED, exitStatus));
        });
    }

    public void bell(UUID paneId) {
        workspace.pane(paneId).ifPresent(pane -> publishApp(TerminalEvents.BELL, new TerminalEvents.PaneEvent(pane.tab.id, paneId)));
    }
```

with imports `dev.jasper.sdk.terminal.TerminalEvents`, `java.nio.file.Path`, `java.time.Duration`, `java.util.Optional`, `java.util.OptionalInt` where missing.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-sdk-testkit:check :jasper-app:test --tests 'dev.jasper.app.plugins.*' verifySdkArchitecture verifyApplicationArchitecture :jasper-app:javadoc`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add jasper-sdk-testkit jasper-app
git commit -m "feat: publish terminal events to plugins that may observe terminals

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: `PluginContext.terminals()` and the contract suite

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/plugin/PluginContext.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`; `plugins/sample/src/main/resources/plugin.toml`
- Modify: `jasper-sdk-testkit/.../FakePluginContext.java`, `.../contract/{ContractHarness,PluginContractTest}.java`, `.../FakeContractTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java`; `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`

**Interfaces:**
- Produces: `PluginContext.terminals()`; `JasperSdk.VERSION` = `"0.4.0"`; sample `sdk = ">=0.4, <0.5"`; `ContractHarness` additions `UUID addTerminalWindow()`, `UUID addTerminalTab(UUID windowId, String title)`, `UUID addTerminalPane(UUID tabId, String title, Path directory)`, `void activateTerminalWindow(UUID windowId)`, `void focusTerminalPane(UUID paneId)`, `void closeTerminalPane(UUID paneId)`, `void selectInPane(UUID paneId, String text)`, `List<String> sentToPane(UUID paneId)`, `List<String> openRequests()`, `void finishCommand(UUID paneId, String command, int exitStatus)`. Every driver runs on the UI thread and waits. A harness pane is a running local pane, 80 by 24, with shell integration.

- [ ] **Step 1: Grow the harness and write the failing contract cases**

Add the ten methods to `ContractHarness`, documenting the formats from Task 4, and append to `PluginContractTest` (imports `dev.jasper.sdk.Capabilities`, `dev.jasper.sdk.MissingCapabilityException`, `dev.jasper.sdk.terminal.Direction`, `OpenRequest`, `PaneHandle`, `SessionState`, `TerminalEvents`, `java.nio.file.Path`):

```java
    private static PluginInfo info(String id, String... capabilities) { return new PluginInfo(id, id, "1.0.0", Set.of(capabilities)); }

    @Test void terminalsAreFoundThroughTheContextAndHandlesAreEqualById() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build");
        UUID left = h.addTerminalPane(tab, "make", Path.of("/src")), right = h.addTerminalPane(tab, "top", Path.of("/"));
        h.activateTerminalWindow(window);
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            assertThat(terminals.windows()).extracting(handle -> handle.id()).containsExactly(window);
            assertThat(terminals.activeWindow()).contains(terminals.windows().get(0));
            var tabHandle = terminals.windows().get(0).activeTab().orElseThrow();
            assertThat(tabHandle.title()).isEqualTo("build");
            assertThat(tabHandle.panes()).extracting(PaneHandle::id).containsExactly(left, right);
            PaneHandle pane = terminals.activePane().orElseThrow();
            assertThat(pane).isEqualTo(terminals.pane(left).orElseThrow());
            assertThat(pane.tab()).isEqualTo(tabHandle);
            assertThat(pane.tab().window().isActive()).isTrue();
            assertThat(pane.info().title()).isEqualTo("make");
            assertThat(pane.info().workingDirectory()).contains(Path.of("/src"));
            assertThat(pane.info().state()).isEqualTo(SessionState.RUNNING);
            terminals.pane(right).orElseThrow().focus();
            assertThat(terminals.activePane().map(PaneHandle::id)).contains(right);
        });
        h.closeTerminalPane(right);
        h.ui(() -> assertThat(alpha.get().terminals().activePane().map(PaneHandle::id)).contains(left));
    }

    @Test void gatedTerminalCallsNameTheMissingCapability() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build"), only = h.addTerminalPane(tab, "make", Path.of("/src"));
        var bare = new AtomicReference<PluginContext>();
        h.start(info("test.bare"), Set.of(), Set.of(), context -> {
            bare.set(context);
            assertThatThrownBy(() -> context.events().subscribe(TerminalEvents.COMMAND_FINISHED, event -> { }))
                .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> {
                    assertThat(failure.pluginId()).isEqualTo("test.bare");
                    assertThat(failure.capability()).isEqualTo(Capabilities.TERMINAL_OBSERVE);
                });
        });
        assertThat(h.active("test.bare")).isTrue();
        h.ui(() -> {
            PaneHandle pane = bare.get().terminals().pane(only).orElseThrow();
            assertThat(pane.isOpen()).as("structure needs no capability").isTrue();
            assertThatThrownBy(pane::info).isInstanceOf(MissingCapabilityException.class);
            assertThatThrownBy(() -> pane.tab().title()).isInstanceOf(MissingCapabilityException.class);
            assertThatThrownBy(pane::selection).isInstanceOf(MissingCapabilityException.class);
            assertThatThrownBy(() -> pane.sendText("x")).isInstanceOf(MissingCapabilityException.class);
            assertThatThrownBy(() -> bare.get().terminals().split(pane, Direction.RIGHT, OpenRequest.local())).isInstanceOf(MissingCapabilityException.class);
        });
        assertThat(h.sentToPane(only)).isEmpty();
        assertThat(h.openRequests()).isEmpty();
    }

    @Test void injectionArrivesInOrderFromAnyThreadAndAClosedPaneIgnoresIt() throws Exception {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build");
        UUID only = h.addTerminalPane(tab, "make", Path.of("/src")), other = h.addTerminalPane(tab, "top", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_INJECT, Capabilities.TERMINAL_SELECTION), Set.of(), Set.of(), alpha::set);
        var pane = new AtomicReference<PaneHandle>();
        h.ui(() -> {
            pane.set(alpha.get().terminals().pane(only).orElseThrow());
            pane.get().sendText("ls\n");
            pane.get().paste("pasted");
        });
        Thread worker = new Thread(() -> { pane.get().sendText("one"); pane.get().sendBytes("two".getBytes(java.nio.charset.StandardCharsets.UTF_8)); });
        worker.start();
        worker.join();
        h.flush();
        assertThat(h.sentToPane(only)).containsExactly("write:ls\n", "paste:pasted", "write:one", "write:two");
        h.selectInPane(only, "selected");
        h.ui(() -> assertThat(pane.get().selection()).contains("selected"));
        h.closeTerminalPane(only);
        h.ui(() -> {
            assertThat(pane.get().isOpen()).isFalse();
            assertThatCode(() -> pane.get().sendText("late")).doesNotThrowAnyException();
            assertThat(pane.get().selection()).isEmpty();
        });
        h.flush();
        assertThat(h.sentToPane(other)).isEmpty();
    }

    @Test void terminalEventsNameIdsAndFollowTheActivePane() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build");
        UUID left = h.addTerminalPane(tab, "make", Path.of("/src")), right = h.addTerminalPane(tab, "top", Path.of("/"));
        List<Object> heard = Collections.synchronizedList(new ArrayList<>());
        h.start(info("test.alpha", Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), context -> {
            context.events().subscribe(TerminalEvents.ACTIVE_PANE_CHANGED, heard::add);
            context.events().subscribe(TerminalEvents.COMMAND_FINISHED, heard::add);
            context.events().subscribe(TerminalEvents.PANE_CLOSED, heard::add);
        });
        h.flush();
        heard.clear();
        h.activateTerminalWindow(window);
        h.focusTerminalPane(right);
        h.finishCommand(left, "make test", 2);
        h.closeTerminalPane(right);
        h.flush();
        assertThat(heard).hasSize(5);
        assertThat(heard.get(0)).isEqualTo(new TerminalEvents.ActivePaneChanged(Optional.of(left)));
        assertThat(heard.get(1)).isEqualTo(new TerminalEvents.ActivePaneChanged(Optional.of(right)));
        assertThat(heard.get(2)).isInstanceOfSatisfying(TerminalEvents.CommandFinished.class, finished -> {
            assertThat(finished.paneId()).isEqualTo(left);
            assertThat(finished.command()).isEqualTo("make test");
            assertThat(finished.exitStatus()).hasValue(2);
            assertThat(finished.workingDirectory()).contains(Path.of("/src"));
        });
        assertThat(heard.subList(3, 5)).containsExactlyInAnyOrder(new TerminalEvents.PaneEvent(tab, right),
            new TerminalEvents.ActivePaneChanged(Optional.of(left)));
    }

    @Test void openingTabsAndSplitsNeedsTheCapabilityAndReturnsTheNewPane() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build"), only = h.addTerminalPane(tab, "make", Path.of("/src"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.TERMINAL_OPEN), Set.of(), Set.of(), alpha::set);
        h.ui(() -> {
            var terminals = alpha.get().terminals();
            PaneHandle opened = terminals.openTab(terminals.window(window).orElseThrow(), OpenRequest.localIn(Path.of("/tmp"))).orElseThrow();
            assertThat(opened.isOpen()).isTrue();
            assertThat(opened.tab().id()).isNotEqualTo(tab);
            PaneHandle split = terminals.split(terminals.pane(only).orElseThrow(), Direction.DOWN, OpenRequest.local()).orElseThrow();
            assertThat(split.tab().id()).isEqualTo(tab);
        });
        assertThat(h.openRequests()).containsExactly("tab|" + window + "|/tmp", "split|" + only + "|DOWN|-");
    }

    @Test void actionContextsCarryHandlesOfTheInvokingPlugin() {
        UUID window = h.addTerminalWindow(), tab = h.addTerminalTab(window, "build"), only = h.addTerminalPane(tab, "make", Path.of("/src"));
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        h.start(info("test.alpha", Capabilities.TERMINAL_INJECT), Set.of(), Set.of(), context ->
            context.actions().register(ActionSpec.of("test.alpha.type", "Type"), invoked -> {
                PaneHandle pane = invoked.pane().orElseThrow();
                seen.add(invoked.window().isOpen() + " " + pane.isOpen() + " " + pane.tab().id().equals(tab));
                pane.sendText("typed");
                try { pane.info(); } catch (MissingCapabilityException expected) { seen.add(expected.capability()); }
            }));
        assertThat(h.invoke("test.alpha.type", window, only)).isTrue();
        h.flush();
        assertThat(seen).containsExactly("true true true", Capabilities.TERMINAL_OBSERVE);
        assertThat(h.sentToPane(only)).containsExactly("write:typed");
    }
```

If `PluginContractTest` already declares `info(String)`, keep it and let the new varargs overload sit beside it; remove the one-argument form only if the compiler reports an ambiguity (it does not: a fixed-arity match wins).

In `FakeContractTest` delegate the ten methods to the host (`addTerminalPane` builds a running local `PaneInfo`, 80 by 24, shell integration on, from the title and directory; `selectInPane` is `host.setSelection`; `sentToPane` is `host.sent`; `finishCommand` is `host.commandFinished(pane, command, OptionalInt.of(status), Duration.ofMillis(1500))`).

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-sdk-testkit:test`
Expected: compilation FAILS: `PluginContext` has no `terminals()`.

- [ ] **Step 3: Grow the SDK and both implementations**

In `PluginContext` add, after `windows()`, with an import for `dev.jasper.sdk.terminal.Terminals`:

```java
    /**
     * The terminal windows, tabs and panes. Finding them needs no capability; what a handle may do does.
     *
     * @return the terminals service
     */
    Terminals terminals();
```

Set `JasperSdk.VERSION = "0.4.0"` and the sample's `sdk = ">=0.4, <0.5"`. Add `@Override` to the fake's `terminals()`. In `HostedContext` add `@Override public Terminals terminals() { return terminals; }`.

- [ ] **Step 4: Teach the application harness the ten operations**

In `AppContractTest.newHarness()` create the fixture on the EDT and hand its registry to the host:

```java
        TerminalFixture terminalFixture = onEdtValue(TerminalFixture::new);
        PluginHost host = host(data, Map.of(), Duration.ofMillis(200), contributions, dark, auxiliary, terminalFixture.registry);
```

and add:

```java
            @Override public UUID addTerminalWindow() { return onEdtValue(terminalFixture::addWindow); }
            @Override public UUID addTerminalTab(UUID windowId, String title) { return onEdtValue(() -> terminalFixture.addTab(windowId, title)); }
            @Override public UUID addTerminalPane(UUID tabId, String title, java.nio.file.Path directory) {
                return onEdtValue(() -> terminalFixture.addPane(tabId, title, directory));
            }
            @Override public void activateTerminalWindow(UUID windowId) { onEdt(() -> terminalFixture.activateWindow(windowId)); }
            @Override public void focusTerminalPane(UUID paneId) { onEdt(() -> terminalFixture.focusPane(paneId)); }
            @Override public void closeTerminalPane(UUID paneId) { onEdt(() -> terminalFixture.closePane(paneId)); }
            @Override public void selectInPane(UUID paneId, String text) { onEdt(() -> terminalFixture.select(paneId, text)); }
            @Override public List<String> sentToPane(UUID paneId) { return onEdtValue(() -> terminalFixture.sent(paneId)); }
            @Override public List<String> openRequests() {
                return onEdtValue(() -> terminalFixture.opened().stream().filter(line -> !line.startsWith("front|")).toList());
            }
            @Override public void finishCommand(UUID paneId, String command, int exitStatus) { onEdt(() -> terminalFixture.finishCommand(paneId, command, exitStatus)); }
```

- [ ] **Step 5: Run everything**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL`; 23 contract cases pass for the fake and for the application.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add -A jasper-sdk jasper-sdk-testkit jasper-app plugins
git commit -m "feat: expose terminals through PluginContext and hold both implementations to one contract

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: Application wiring

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/{JasperApplication,package-info}.java`
- Test: extend `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java`

**Interfaces:**
- Produces: `JasperApplication` owns one `TerminalRegistry` for its lifetime, connects every terminal window to it with the window's `toFront`, reports window activation to it, and hands it to `PluginRuntime`. Package-private accessor `TerminalRegistry terminals()` for tests.

A native window cannot be built headlessly, so the test checks what can be: plugins started by the application get the application's registry. The per-window wiring is two lines and is covered by `WindowTerminalsTest` plus the native checklist.

- [ ] **Step 1: Write the failing test**

Append to `JasperApplicationPluginsTest`:

```java
    private static final String TERMINALS_FIXTURE = """
        package fix.terms;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import java.nio.file.Files;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) throws Exception {
                Files.writeString(context.dataDirectory().resolve("windows"),
                    context.terminals().windows().size() + " " + context.terminals().activePane().isPresent());
            }
        }
        """;

    @Test void pluginsSeeTheApplicationsTerminalRegistry() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("terms-plugin");
        PluginJars.build(dev, "terms.jar", PluginJars.descriptor("dev.example.terms", "1.0.0", "fix.terms.Main"),
            Map.of("fix.terms.Main", TERMINALS_FIXTURE), List.of());
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].startPlugins(null, dev, false, dirs);
            assertThat(application[0].terminals().windows()).isEmpty();
        });
        assertThat(dirs.pluginData().resolve("dev.example.terms").resolve("windows")).hasContent("0 false");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation FAILS (`terminals()` not found on `JasperApplication`).

- [ ] **Step 3: Implement**

In `JasperApplication` add the import `dev.jasper.app.terminals.TerminalRegistry` and the field and accessor:

```java
    /** Every terminal window's tabs and panes, for features that must not hold Swing objects. */
    private final TerminalRegistry terminals = new TerminalRegistry();

    TerminalRegistry terminals() { return terminals; }
```

In `newWindow`, next to `window.content().connectContributions(contributions, uiState);`:

```java
        window.content().connectTerminals(terminals, window::toFront);
```

In `windowActivated`, after `lastActive = window;`:

```java
        terminals.windowActivated(window.content().id());
```

In `startPlugins` replace the placeholder `new dev.jasper.app.terminals.TerminalRegistry()` from Task 4 with `terminals`. In `application/package-info.java` add `dev.jasper.app.terminals` to the allowed outgoing dependencies, keeping the list sorted.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.workspace.*' verifyApplicationArchitecture`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: connect every terminal window to the application's terminal registry

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: The sample plugin's terminal demo

**Files:**
- Modify: `plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java`, `plugins/sample/src/main/resources/plugin.toml`, `plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java`

**Interfaces:**
- Produces, when `demo_terminal = true`: action `dev.jasper.sample.greet` ("Insert Sample Greeting"), also in the terminal context menu, which types `echo 'hello from the sample plugin'` without a newline into the pane it was invoked on, or else the active pane; and a hidden status item `dev.jasper.sample.last` on the left that shows the last finished command and its exit status. The descriptor declares `capabilities = ["terminal.observe", "terminal.inject"]`. A sample started without those capabilities logs that and skips the demo.

- [ ] **Step 1: Write the failing tests**

Append to `SamplePluginTest` (imports `dev.jasper.sdk.Capabilities`, `dev.jasper.sdk.PluginInfo`, `dev.jasper.sdk.terminal.PaneInfo`, `dev.jasper.sdk.terminal.SessionKind`, `dev.jasper.sdk.terminal.SessionState`, `java.nio.file.Path`, `java.time.Duration`, `java.util.Optional`, `java.util.OptionalInt`, `java.util.UUID` where missing):

```java
    private static final PluginInfo OBSERVING = new PluginInfo("dev.jasper.sample", "Sample", "0.1.0",
        Set.of(Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_INJECT));

    @Test void theTerminalDemoTypesIntoAPaneAndShowsTheLastExitStatus() {
        try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "build");
            UUID pane = host.addTerminalPane(tab, new PaneInfo("zsh", Optional.of(Path.of("/src")), Optional.empty(), 80, 24, true,
                SessionKind.LOCAL, Optional.empty(), SessionState.RUNNING, OptionalInt.empty()));
            host.activateTerminalWindow(window);
            host.setConfig("dev.jasper.sample", Map.of("demo_terminal", true));
            host.start(OBSERVING, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.failures()).isEmpty();
            assertThat(host.menu("context")).contains("item:dev.jasper.sample.greet");
            assertThat(host.status()).as("hidden until a command finishes").noneMatch(line -> line.startsWith("dev.jasper.sample.last"));

            assertThat(host.invoke("dev.jasper.sample.greet", window, pane)).isTrue();
            assertThat(host.invoke("dev.jasper.sample.greet", window, null)).as("falls back to the active pane").isTrue();
            assertThat(host.sent(pane)).containsExactly("write:echo 'hello from the sample plugin'", "write:echo 'hello from the sample plugin'");

            host.commandFinished(pane, "make test", OptionalInt.of(2), Duration.ofMillis(1500));
            host.flush();
            assertThat(host.status()).anyMatch(line -> line.startsWith("dev.jasper.sample.last|LEFT|make test: exit 2"));
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void theTerminalDemoIsSkippedWithoutItsCapabilities() {
        try (var host = new FakePluginHost()) {
            host.setConfig("dev.jasper.sample", Map.of("demo_terminal", true));
            host.start(INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.active("dev.jasper.sample")).isTrue();
            assertThat(host.failures()).isEmpty();
            assertThat(host.actions()).noneMatch(line -> line.startsWith("dev.jasper.sample.greet"));
        }
    }
```

If the existing `INFO` constant of that test already declares capabilities, build a capability-free `PluginInfo` inline for the second test.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-sample:test`
Expected: FAIL: no `dev.jasper.sample.greet` action.

- [ ] **Step 3: Extend the plugin**

In `plugin.toml` add `capabilities = ["terminal.observe", "terminal.inject"]` above any table. In `SamplePlugin` add imports `dev.jasper.sdk.Capabilities` and `dev.jasper.sdk.terminal.TerminalEvents`, a constant `private static final String GREET = "dev.jasper.sample.greet";`, the line `if (context.config().bool("demo_terminal").orElse(false)) installTerminalDemo(context);` after the `demo_ui` line in `start`, and:

```java
    // example:pluginterminal:start
    private static void installTerminalDemo(PluginContext context) {
        // Capabilities are declared in plugin.toml and consented to by the user. A gated call without one
        // throws MissingCapabilityException, so a plugin that can live without a capability checks first.
        if (!context.plugin().capabilities().containsAll(List.of(Capabilities.TERMINAL_OBSERVE, Capabilities.TERMINAL_INJECT))) {
            context.log().log(System.Logger.Level.INFO, "The terminal demo needs terminal.observe and terminal.inject");
            return;
        }
        context.actions().register(ActionSpec.of(GREET, "Insert Sample Greeting").withKeywords(List.of("sample", "type", "terminal")), invoked ->
            // The pane the action was invoked on, or else the one the user used last. sendText adds nothing:
            // without a newline the text waits at the prompt, and the user decides whether to run it.
            invoked.pane().or(() -> context.terminals().activePane())
                .ifPresent(pane -> pane.sendText("echo 'hello from the sample plugin'")));
        context.menus().terminalContext().add(GREET);

        StatusItem last = context.statusBar().add(new StatusItemSpec("dev.jasper.sample.last", Side.LEFT, 100));
        last.setVisible(false);
        // Terminal events name panes by id and arrive later, on the event thread; there is no replay.
        context.events().subscribe(TerminalEvents.COMMAND_FINISHED, finished -> {
            last.setText(finished.command() + ": " + (finished.exitStatus().isPresent() ? "exit " + finished.exitStatus().getAsInt() : "done"));
            last.setVisible(true);
        });
    }
    // example:pluginterminal:end
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-plugin-sample:check :jasper-app:test --tests '*BundledSamplePluginTest' verifyPluginArchitecture`
Expected: PASS: the bundled jar's descriptor, now with capabilities, still loads. Do not commit yet: the compiled documentation example comes with Task 9, which shares the commit.

---

### Task 9: Documentation

**Files:**
- Modify: `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `docs/app-architecture.md`, `docs/configuration.md`, `docs/STATUS.md`, `jasper-sdk/README.md`, this plan's status banner

- [ ] **Step 1: Update the authoring guide**

In `docs/plugin-authoring.md`: change "(0.3)" to "(0.4)" and the sentence after it so it lists "…application-built windows and dialogs, and terminals: finding panes, following terminal events, typing into a pane and opening tabs. Plugin-provided sessions arrive in a later SDK version."; change the descriptor range to `sdk = ">=0.4, <0.5"` and show `capabilities = ["terminal.observe"]` in the descriptor example if it has no capabilities line yet; and add after "Panels, the rail and windows":

````markdown
## Terminals and capabilities

`context.terminals()` finds windows, tabs and panes; an action's context names the window and pane
it was invoked on. Handles keep an id, never a Swing object, and two handles for the same pane are
equal. Finding things needs no capability. What a handle may do does:

| Capability | Lets your plugin |
|---|---|
| `terminal.observe` | read `PaneHandle.info()`, `foregroundJob()` and `TabHandle.title()`, and subscribe to `TerminalEvents` |
| `terminal.selection` | read `PaneHandle.selection()` |
| `terminal.inject` | `sendText`, `sendBytes`, `paste` |
| `terminal.open` | `Terminals.openTab` and `split` with `OpenRequest.local()` |

Declare them in `plugin.toml`; the user is shown the list before your plugin loads. A gated call
without the capability throws `MissingCapabilityException`. Jasper logs gated calls with your plugin
id and the amount of data, never the content.

<!-- EXAMPLE-MARKER:pluginterminal -->
```java
REPLACE-WITH-SOURCE
```

- **A pane can close at any moment.** Commands to a closed handle are ignored, queries return the
  last value the handle saw, and `isOpen()` tells. Do not treat that as an error.
- **Events carry ids**, because a handle belongs to the plugin that obtained it: call
  `context.terminals().pane(event.paneId())`. They arrive later, on the event thread, and there is
  no replay: read the current state first, then subscribe.
- **Threads.** Everything here is event-thread only, except `sendText`, `sendBytes` and `paste`,
  which you may call from any thread; calls from one thread keep their order.
  `foregroundJob()` completes on a worker thread.
- **`sendText` adds nothing.** End a command with `"\n"` if you mean to run it; leaving it out lets
  the user look first. `paste` behaves like the user's own paste, bracketed when the program asked.
- **Working directories are hints.** They are whatever the shell last reported and may not exist.
- To run a command in a new tab, open one with `OpenRequest.local()` or `localIn(directory)` and
  `sendText` to the pane you get back; the tab runs the user's configured shell.
````

In the file you write, replace `EXAMPLE-MARKER` with `example` and `REPLACE-WITH-SOURCE` with the source text between `// example:pluginterminal:start` and `// example:pluginterminal:end` after `stripIndent().strip()`; copy it from `SamplePlugin.java`, not from this plan.

In "Testing" add: "`host.addTerminalWindow()`, `addTerminalTab`, `addTerminalPane`, `activateTerminalWindow`, `focusTerminalPane` and `closeTerminalPane` script a workspace; `host.sent(paneId)` and `host.openRequests()` show what your plugin did; `commandStarted`, `commandFinished`, `titleChanged`, `cwdChanged`, `sessionExited` and `bell` publish terminal events, delivered by `flush()`. Give the `PluginInfo` you start with the capabilities your `plugin.toml` declares."

- [ ] **Step 2: Update the other documents**

`jasper-sdk/README.md`: extend the `dev.jasper.sdk` row with "`Capabilities`, `MissingCapabilityException`" and the `dev.jasper.sdk.terminal` row with "`Terminals`, `WindowHandle`, `TabHandle`, `PaneHandle`, `PaneInfo`, `OpenRequest`, `LocalSpec`, `Direction`, `TerminalEvents`".

`docs/sdk-architecture.md`: change the `dev.jasper.app.plugins` row's dependencies to include `terminals`; add the row "`dev.jasper.app.terminals` | App-native registry of terminal windows, tabs and panes: pull-based entries, id-only events, the derived active pane | `lifecycle`"; add before "Plugins manager, install and restart":

```markdown
## Terminal API

`dev.jasper.app.terminals.TerminalRegistry` is the seam between windows and everything that must
not hold a Swing object. Each `WindowContent` registers a `WindowEntry` through
`workspace.WindowTerminals`: records of suppliers and callbacks that read the window's tabs and
panes on demand, so structure is never stale, plus id-only `TerminalEvent`s for what happens. The
registry derives one fact itself, the active pane (the focused pane of the selected tab of the
window used last), and reports it once per change; `atomically` keeps a tab or window close from
reporting a transient "no active pane".

In `dev.jasper.app.plugins`, `HostedTerminals` gives each plugin its own handles. A handle holds
ids and the last values it saw and looks its target up on every call, which is what makes a closed
pane harmless. Gated methods ask the plugin's `CapabilityGate` first; the declared capability set
is the granted set, because the resolver loads a user plugin only after consent to everything it
declares. `TerminalBridge` republishes registry events as `TerminalEvents` topics on the queued
bus, and subscribing to a `jasper.terminal.*` topic needs `terminal.observe`. Payloads carry ids,
not handles: a handle is bound to one plugin's gate, a payload is shared by all subscribers.
```

Replace "Not yet implemented" with: "Plugin-provided sessions (`TerminalSession.attach`, the app-owned writer, drain, `PendingSession`, Reconnect), the cleanup worker, `OpenRequest.session`, working-directory provenance with `RemoteDirectory`, and explicit commands in `LocalSpec` (plan 4b)."

`docs/app-architecture.md`: add after the `snippets` row:

```markdown
| `terminals` | App-native registry of terminal windows, tabs and panes: pull-based entries, id-only events and the derived active pane. Application owns the TerminalRegistry; each window closes its own registration. |
```

and extend the `workspace` row's first sentence with "and each window's presence in the terminal registry".

`docs/configuration.md`: in the sample plugin's table add `demo_terminal = true       # add "Insert Sample Greeting" and show the last command's exit status`.

`docs/STATUS.md`: update the opening paragraph to say plan 4a is implemented on `claude/plugin-sdk-plan-4` and that the spec's plan 4 was split into 4a and 4b; add a dated "Plugin SDK plan 4a" section with the nine scope decisions at the top of this plan, exact test counts, and native acceptance pending.

Set this plan's **Status** banner to "Implemented on `claude/plugin-sdk-plan-4`; native acceptance pending" and list any deviation.

- [ ] **Step 3: Verify everything**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*'`
Expected: PASS: four examples match `SamplePlugin.java`.

Run the AGENTS.md Python hygiene check. Expected: no output.

Run: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
Expected: `BUILD SUCCESSFUL` (rerun if the only failure is the known terminal flake).

- [ ] **Step 4: Commit Tasks 8 and 9**

```bash
git branch --show-current
git add -A
git commit -m "feat: show terminal access in the sample plugin and document the terminal API

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Native acceptance (user-run; agents must not launch the GUI)

With `demo_terminal = true` under `[plugins."dev.jasper.sample"]`:

1. `./gradlew :jasper-app:run`. File → Manage Plugins… lists the sample with "See your terminals…" and "Type into your terminals" under Capabilities.
2. Right-click in a pane → Insert Sample Greeting: `echo 'hello from the sample plugin'` appears at the prompt and waits; Enter runs it. The palette's "Insert Sample Greeting" does the same in the focused pane.
3. With shell integration on, run `false`: the left of the status bar shows `false: exit 1`; run `true`: it shows `true: exit 0`. It follows whichever pane finished last, in any tab or window.
4. Split the pane, open a second window, and use the greeting from the palette in each: the text lands in the pane that has focus, including after switching windows with Jasper in the background and coming back.
5. Close the pane the greeting was last used in, then invoke it from the palette in another: no error dialog, no exception in the log.
6. The log (`~/.config/jasper/logs/`) has one line per greeting of the form "Plugin dev.jasper.sample used terminal.inject: typed 35 bytes into pane …" and never the text itself, and one "subscribed to terminal events" line at startup.
7. Set `demo_terminal = false` and restart: the action, the context-menu entry and the status item are gone, and the window behaves exactly as before.

## Self-review record

- **Spec coverage.** §6 "Handles and queries": `Terminals` (active window and pane, windows, pane by id, `openTab`, `split`), `WindowHandle`, `TabHandle`, `PaneHandle` members, `PaneInfo` with every listed component, stable ids, no Swing object retained → Tasks 1, 4; `foregroundJob` as a future → Tasks 3, 4; `sendText` without a newline, `paste` through the bracketed-paste path → Tasks 3, 4; closed-handle rules → Task 4 and contract cases in Task 6. "Opening terminals": `OpenRequest.local` behind `terminal.open` → Tasks 1, 3, 4 (explicit command and environment deferred by decision 4). §3 "Capabilities": `MissingCapabilityException` naming plugin and capability, audit logging without content → Task 4 (decisions 5 and 6). §3 "Threading": sending methods from any thread, everything else EDT-only, no plugin callback on a reader thread → Tasks 3 (every pane callback hops to the EDT), 4, 5. §7 "Built-in topics": all sixteen `TerminalEvents` topics behind `terminal.observe`, bridged in `dev.jasper.app.plugins` from workspace facts, internal `WorkspaceActivity` and the `CommandNotifier` path unchanged → Tasks 1, 3, 5. §11 testkit and contract → Tasks 4 to 6. §13 item 4 demo "injects text into the active pane" → Task 8; the loopback echo session is plan 4b. Deferred to plan 4b by decision 1: `TerminalSession.attach`, writer, drain, `PendingSession`, pane pending and disconnected states, Reconnect, cleanup worker, `OpenRequest.session`, working-directory provenance.
- **Type consistency.** `PaneSnapshot`'s seven components, `PaneEntry`'s nine, `TabEntry`'s six, `WindowEntry`'s six, the seventeen `TerminalEvent` records, `TerminalRegistry.{addWindow, windowActivated, publish, atomically, refresh, onEvent}`, `TerminalPane.CommandFinished`'s four parameters, `TerminalTab.split(target, axis, directoryOrNull)`, `WindowContent.{connectTerminals, openTab, terminalTabs}`, `CapabilityGate(pluginId, granted)`, `HostedTerminals`' six-argument constructor with `windowHandle` and `paneHandle`, `HostedUi`'s eleven-argument constructor, `PluginHost.Environment`'s ten components, `PluginRuntime`'s six-argument constructor, the `sent` and open-request line formats, and the harness's ten terminal operations are used identically wherever they appear.
- **Build stays green per task** except between Tasks 8 and 9, which share one commit.
