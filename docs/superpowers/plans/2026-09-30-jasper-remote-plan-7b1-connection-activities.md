# Remote tunnels plan 7b-1: SDK 0.10.2 connection activities Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Written 2026-09-30 on `claude/tunnels-spec`; executed inline 2026-09-30 on `claude/tunnels-7b1` (three commits), no deviations from the plan text. Not merged.

**Goal:** Give plugins a way to show connections (things that are up until they are not) on Buddy, let Buddy cards run the posting plugin's own action when clicked, and let plugins open web addresses in the user's browser — SDK 0.10.2.

**Architecture:** A new `ConnectionHub` in the app (beside `ActivityHub`) and a `FakeConnection` in the testkit implement `Activities.connection(ConnectionSpec)`: every state change is delivered in order on a new topic `Activities.CONNECTIONS`, a detail-only report joins the delivery still queued for the same state, and `close()` publishes `CLOSED` once. `PluginRuntime` forwards those events to `notifications.ActivityNotifier`, which posts `BuddyNotice.Kind.CONNECTION` cards (re-post on a state change, detail update otherwise, dismiss on `CLOSED`) and gives every card an activation that raises the app and, when the event names an action the source plugin registered, invokes it in the last active window and acknowledges the card. `Platform.browse(URI)` runs a new `platform.Browser` on the plugin's executor.

**Tech Stack:** Java 25 (JetBrains Runtime), Gradle wrapper, JUnit 5 + AssertJ, Swing (EDT), the Buddy companion.

**Spec:** `docs/superpowers/specs/2026-09-30-jasper-remote-tunnels-design.md`, section 8 (Buddy and SDK 0.10.2) and section 12 (plan 7b-1).

## Global Constraints

- Work in a worktree: `git worktree add -b claude/tunnels-7b1 .worktrees/tunnels-7b1 claude/tunnels-spec`, and run every command from `.worktrees/tunnels-7b1`. Verify `git branch --show-current` prints `claude/tunnels-7b1` before every commit (other sessions switch branches in the main checkout).
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- `JasperSdk.VERSION` becomes exactly `"0.10.2"`. Every new SDK type and member carries `@since 0.10.2`. No bundled plugin's `sdk` range changes (0.10.2 is inside `<0.11`).
- Existing activities are unchanged: `ActivityEvent.State` gains no constants and `Activities.TOPIC` is untouched.
- The connection topic id is exactly `"jasper.connection"`.
- `Platform.browse` accepts only an absolute, hierarchical `http` or `https` URI (scheme compared case-insensitively); anything else throws `IllegalArgumentException`, and `null` throws `NullPointerException`.
- SDK types appear in the app only inside `dev.jasper.app.plugins`; `dev.jasper.app.notifications` depends only on `dev.jasper.buddy.notice` and `dev.jasper.buddy.view`; `dev.jasper.app.platform` depends on no other Jasper package.
- Source hygiene: no raw control, private-use or unpaired surrogate characters in Java sources (run the Python check from `AGENTS.md` before each commit).
- `./gradlew check` is green at the end of every task. For counts read `*/build/test-results/test/*.xml`.
- Never launch the GUI.

---

### Task 1: Connection activities in the SDK and both runtimes

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/activity/ConnectionEvent.java`
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/activity/ConnectionSpec.java`
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/activity/ConnectionHandle.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/activity/Activities.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/activity/package-info.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`
- Modify: `jasper-sdk/src/test/java/dev/jasper/sdk/activity/ActivityValuesTest.java`
- Modify: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/AppearanceContractTest.java`, `jasper-sdk/src/test/java/dev/jasper/sdk/files/FilesValuesTest.java`, `jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java` (version string only)
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/ConnectionHub.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginContext.java`
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java`
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java`
- Modify: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`
- Modify: `jasper-sdk/README.md`, `docs/plugin-authoring.md`

**Interfaces:**
- Consumes: `Topic.of`, `EventBus.enqueue(Runnable)`, `EventBus.deliver(String, Object)`, `EventBus.checkType(Topic)` (app); the fake's private `events` queue, `checkType` and `deliver`.
- Produces (later tasks rely on these exact names):
  - `record ConnectionEvent(UUID id, String sourcePluginId, String title, ConnectionEvent.State state, String detail, Optional<String> activateActionId)`, `enum ConnectionEvent.State { UP, DEGRADED, DOWN, CLOSED }`
  - `record ConnectionSpec(String title, String detail, ConnectionEvent.State state, Optional<String> activateActionId)`, `static ConnectionSpec of(String title, ConnectionEvent.State state)`
  - `interface ConnectionHandle { UUID id(); void up(String); void degraded(String); void down(String); void close(); }`
  - `Activities.CONNECTIONS` (`Topic<ConnectionEvent>`, id `jasper.connection`), `ConnectionHandle Activities.connection(ConnectionSpec)`
  - App: `final class ConnectionHub` with `ConnectionHandle open(String pluginId, ConnectionSpec spec)` and `void closeAll(String pluginId)`; `PluginHost.connections` (field)
  - Testkit: `FakePluginHost.connectionLog()` returning `List<ConnectionEvent>`; `ContractHarness.connectionLog()`

- [ ] **Step 1: Write the failing SDK value tests**

Add these two tests to `ActivityValuesTest` (same package, so no imports are needed), after `topicLivesInTheApplicationNamespace`:

```java
    @Test void connectionSpecNeedsATitleAndAnOpenFirstState() {
        assertThat(ConnectionSpec.of("Tunnels via db", ConnectionEvent.State.DEGRADED).detail()).isEmpty();
        assertThat(ConnectionSpec.of("Tunnels via db", ConnectionEvent.State.DEGRADED).activateActionId()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> ConnectionSpec.of(" ", ConnectionEvent.State.UP));
        assertThatIllegalArgumentException().isThrownBy(() -> ConnectionSpec.of("t", ConnectionEvent.State.CLOSED));
        assertThatNullPointerException().isThrownBy(() -> new ConnectionSpec("t", null, ConnectionEvent.State.UP, Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new ConnectionSpec("t", "", null, Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new ConnectionSpec("t", "", ConnectionEvent.State.UP, null));
    }

    @Test void connectionEventsRejectNullPartsAndTravelOnTheirOwnTopic() {
        UUID id = UUID.randomUUID();
        assertThatNullPointerException().isThrownBy(() -> new ConnectionEvent(id, "a.b", "t", null, "", Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new ConnectionEvent(id, "a.b", "t", ConnectionEvent.State.UP, null, Optional.empty()));
        assertThat(Activities.CONNECTIONS.id()).isEqualTo("jasper.connection");
        assertThat(Activities.CONNECTIONS.payloadType()).isEqualTo(ConnectionEvent.class);
        assertThat(ConnectionEvent.State.values()).containsExactly(ConnectionEvent.State.UP, ConnectionEvent.State.DEGRADED,
            ConnectionEvent.State.DOWN, ConnectionEvent.State.CLOSED);
    }
```

In `AppearanceContractTest`, `FilesValuesTest` and `PaletteValuesTest`, change the expected version:

```bash
sed -i '' 's/isEqualTo("0.10.1")/isEqualTo("0.10.2")/' jasper-sdk/src/test/java/dev/jasper/sdk/ui/AppearanceContractTest.java jasper-sdk/src/test/java/dev/jasper/sdk/files/FilesValuesTest.java jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java
grep -rn '0.10.1' jasper-sdk/src/test
```

Expected: the `grep` prints nothing.

- [ ] **Step 2: Run the SDK tests to verify they fail**

Run: `./gradlew :jasper-sdk:test`
Expected: FAIL — compilation errors "cannot find symbol: class ConnectionSpec" / "ConnectionEvent" / "CONNECTIONS".

- [ ] **Step 3: Add the SDK types**

Create `jasper-sdk/src/main/java/dev/jasper/sdk/activity/ConnectionEvent.java`:

```java
package dev.jasper.sdk.activity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One step in a connection's life, stamped by the runtime with the plugin that owns it. A connection is up until
 * it is not and never completes, so it travels on its own topic, {@link Activities#CONNECTIONS}, rather than as
 * activity states.
 *
 * @param id the connection's identity
 * @param sourcePluginId the owning plugin, set by the runtime
 * @param title the title given when the connection was announced
 * @param state the step
 * @param detail current detail line
 * @param activateActionId the spec's activation action, if any
 * @since 0.10.2
 */
public record ConnectionEvent(UUID id, String sourcePluginId, String title, State state, String detail,
                              Optional<String> activateActionId) {
    /** The steps of a connection. */
    public enum State {
        /** Available. */
        UP,
        /** Available with a problem that needs attention, or trying to recover. */
        DEGRADED,
        /** Unavailable. */
        DOWN,
        /** No longer reported: its plugin closed it or stopped. Always the last event. */
        CLOSED
    }

    /** Rejects null parts. */
    public ConnectionEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourcePluginId, "sourcePluginId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(activateActionId, "activateActionId");
    }
}
```

Create `jasper-sdk/src/main/java/dev/jasper/sdk/activity/ConnectionSpec.java`:

```java
package dev.jasper.sdk.activity;

import java.util.Objects;
import java.util.Optional;

/**
 * What a connection is when it is announced.
 *
 * @param title non-blank title shown to the user
 * @param detail initial detail line, possibly empty
 * @param state the first state: {@code UP}, {@code DEGRADED} or {@code DOWN}
 * @param activateActionId one of the plugin's own actions that brings the user to this connection, run when the
 *                         user clicks its card; absent when there is none
 * @since 0.10.2
 */
public record ConnectionSpec(String title, String detail, ConnectionEvent.State state, Optional<String> activateActionId) {
    /** Rejects a blank title, a first state of {@code CLOSED} and null parts. */
    public ConnectionSpec {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A connection needs a title");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(state, "state");
        if (state == ConnectionEvent.State.CLOSED) throw new IllegalArgumentException("A connection cannot begin closed");
        Objects.requireNonNull(activateActionId, "activateActionId");
    }

    /**
     * A connection with only a title and its first state.
     *
     * @param title non-blank title
     * @param state {@code UP}, {@code DEGRADED} or {@code DOWN}
     * @return the spec
     */
    public static ConnectionSpec of(String title, ConnectionEvent.State state) {
        return new ConnectionSpec(title, "", state, Optional.empty());
    }
}
```

Create `jasper-sdk/src/main/java/dev/jasper/sdk/activity/ConnectionHandle.java`:

```java
package dev.jasper.sdk.activity;

import java.util.UUID;

/**
 * The publishing side of one connection; every method is safe from any thread. Every change of state is
 * delivered, in order. A report that keeps the state and only changes the detail joins the delivery still waiting
 * for that state, so between two deliveries only the latest detail survives. {@link #close} publishes
 * {@code CLOSED} once and every later call is ignored.
 *
 * @since 0.10.2
 */
public interface ConnectionHandle {
    /**
     * The connection's identity in every {@link ConnectionEvent}.
     *
     * @return the id
     */
    UUID id();

    /**
     * Reports that the connection is available.
     *
     * @param detail detail line
     */
    void up(String detail);

    /**
     * Reports that the connection needs attention or is recovering.
     *
     * @param detail detail line
     */
    void degraded(String detail);

    /**
     * Reports that the connection is unavailable.
     *
     * @param detail detail line
     */
    void down(String detail);

    /** Stops reporting the connection: publishes {@code CLOSED} once. */
    void close();
}
```

Replace the whole of `jasper-sdk/src/main/java/dev/jasper/sdk/activity/Activities.java` with:

```java
package dev.jasper.sdk.activity;

import dev.jasper.sdk.events.Topic;
import java.util.List;

/**
 * Begins activities, announces connections and lists the running activities. Anyone may subscribe to
 * {@link #TOPIC} and {@link #CONNECTIONS}; only handles returned by {@link #begin} and {@link #connection}
 * publish to them, so each lifecycle is well-formed and its source is verified.
 */
public interface Activities {
    /** Every activity's events, from every plugin. */
    Topic<ActivityEvent> TOPIC = Topic.of("jasper.activity", ActivityEvent.class);

    /**
     * Every connection's events, from every plugin.
     *
     * @since 0.10.2
     */
    Topic<ConnectionEvent> CONNECTIONS = Topic.of("jasper.connection", ConnectionEvent.class);

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
     * Announces a connection and publishes its first state. A connection still open when its plugin
     * stops is closed by the runtime.
     *
     * @param spec what the connection is
     * @return the handle that reports its state and closes it
     * @throws IllegalStateException when the plugin's context is closed
     * @since 0.10.2
     */
    ConnectionHandle connection(ConnectionSpec spec);

    /**
     * The latest event of each running activity, the one deliberate exception to "no replay", so a
     * consumer that starts late can show work already under way.
     *
     * @return an immutable snapshot
     */
    List<ActivityEvent> current();
}
```

Replace the whole of `jasper-sdk/src/main/java/dev/jasper/sdk/activity/package-info.java` with:

```java
/**
 * Long-running work a plugin volunteers to show: begin an activity, report progress, end it once; or announce a
 * connection, report its state as it changes and close it. Depends on {@code dev.jasper.sdk.events}.
 */
package dev.jasper.sdk.activity;
```

In `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java` replace

```java
    public static final String VERSION = "0.10.1";
```

with

```java
    public static final String VERSION = "0.10.2";
```

- [ ] **Step 4: Run the SDK tests to verify they pass**

Run: `./gradlew :jasper-sdk:test :jasper-sdk:javadoc`
Expected: PASS, and Javadoc builds with no errors.

- [ ] **Step 5: Write the failing contract cases**

In `ContractHarness.java` add the import `import dev.jasper.sdk.activity.ConnectionEvent;` beside the `ActivityEvent` import, and after the `activityLog()` declaration add:

```java
    /** Every connection event delivered so far, in delivery order, as the application's own observer sees them. */
    List<ConnectionEvent> connectionLog();
```

In `PluginContractTest.java` add the imports

```java
import dev.jasper.sdk.activity.ConnectionEvent;
import dev.jasper.sdk.activity.ConnectionHandle;
import dev.jasper.sdk.activity.ConnectionSpec;
```

beside the other `dev.jasper.sdk.activity` imports, and insert these two tests immediately before `    @Test void servicesCommitAfterStartAndAreBuiltOncePerRequirer() {`:

```java
    @Test void connectionsAreStampedOrderedCoalescedAndCloseOnce() {
        PluginContext alpha = started("test.alpha", Set.of());
        var handle = new AtomicReference<ConnectionHandle>();
        h.ui(() -> {
            ConnectionHandle connection = alpha.activities().connection(new ConnectionSpec("Tunnels via db", "reconnecting",
                ConnectionEvent.State.DEGRADED, Optional.of("test.alpha.show")));
            handle.set(connection);
            connection.degraded("attempt 2");
            connection.degraded("attempt 3");
            connection.down("gave up");
            connection.up("back");
            connection.up("back again");
        });
        h.flush();
        assertThat(h.connectionLog()).extracting(ConnectionEvent::state)
            .containsExactly(ConnectionEvent.State.DEGRADED, ConnectionEvent.State.DOWN, ConnectionEvent.State.UP);
        assertThat(h.connectionLog()).extracting(ConnectionEvent::detail).containsExactly("attempt 3", "gave up", "back again");
        ConnectionEvent first = h.connectionLog().getFirst();
        assertThat(first.id()).isEqualTo(handle.get().id());
        assertThat(first.sourcePluginId()).isEqualTo("test.alpha");
        assertThat(first.title()).isEqualTo("Tunnels via db");
        assertThat(first.activateActionId()).contains("test.alpha.show");

        handle.get().degraded("delivered on its own");
        h.flush();
        h.ui(() -> {
            handle.get().close();
            handle.get().close();
            handle.get().up("ignored after close");
        });
        h.flush();
        assertThat(h.connectionLog()).extracting(ConnectionEvent::state).containsExactly(ConnectionEvent.State.DEGRADED,
            ConnectionEvent.State.DOWN, ConnectionEvent.State.UP, ConnectionEvent.State.DEGRADED, ConnectionEvent.State.CLOSED);
        assertThat(h.connectionLog().getLast().detail()).isEmpty();
    }

    @Test void connectionsLeftOpenCloseWhenTheirPluginStops() {
        PluginContext alpha = started("test.alpha", Set.of());
        alpha.activities().connection(ConnectionSpec.of("Tunnel", ConnectionEvent.State.UP));
        h.flush();
        h.stopAll();
        h.flush();
        assertThat(h.connectionLog()).extracting(ConnectionEvent::state)
            .containsExactly(ConnectionEvent.State.UP, ConnectionEvent.State.CLOSED);
        assertThatIllegalStateException().isThrownBy(() -> alpha.activities().connection(ConnectionSpec.of("late", ConnectionEvent.State.UP)));
    }
```

In `FakeContractTest.java`, after the `activityLog()` override add:

```java
            @Override public List<dev.jasper.sdk.activity.ConnectionEvent> connectionLog() { return host.connectionLog(); }
```

In `AppContractTest.java` (`newHarness()`), replace

```java
        List<ActivityEvent> log = new CopyOnWriteArrayList<>();
        onEdt(() -> host.bus.subscribe(EventBus.APP, Activities.TOPIC, log::add));
```

with

```java
        List<ActivityEvent> log = new CopyOnWriteArrayList<>();
        onEdt(() -> host.bus.subscribe(EventBus.APP, Activities.TOPIC, log::add));
        List<dev.jasper.sdk.activity.ConnectionEvent> connections = new CopyOnWriteArrayList<>();
        onEdt(() -> host.bus.subscribe(EventBus.APP, Activities.CONNECTIONS, connections::add));
```

and after `            @Override public List<ActivityEvent> activityLog() { return List.copyOf(log); }` add:

```java
            @Override public List<dev.jasper.sdk.activity.ConnectionEvent> connectionLog() { return List.copyOf(connections); }
```

- [ ] **Step 6: Run the contract suites to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:test --tests dev.jasper.sdk.testing.FakeContractTest`
Expected: FAIL — compilation errors: `FakePluginContext`'s anonymous `Activities` does not override `connection(ConnectionSpec)`, and `FakePluginHost.connectionLog()` does not exist.

- [ ] **Step 7: Implement connections in the testkit fake**

In `FakePluginHost.java`:

Add imports beside the other `dev.jasper.sdk.activity` imports:

```java
import dev.jasper.sdk.activity.ConnectionEvent;
import dev.jasper.sdk.activity.ConnectionHandle;
import dev.jasper.sdk.activity.ConnectionSpec;
import java.util.concurrent.atomic.AtomicReference;
```

After the field `    private final List<ActivityEvent> activityLog = new CopyOnWriteArrayList<>();` add:

```java
    private final Map<UUID, FakeConnection> connections = new ConcurrentHashMap<>();
    private final List<ConnectionEvent> connectionLog = new CopyOnWriteArrayList<>();
```

After the `activityLog()` accessor (`    public List<ActivityEvent> activityLog() { return List.copyOf(activityLog); }`) add:

```java

    /**
     * Connection events delivered so far.
     *
     * @return the events in delivery order
     */
    public List<ConnectionEvent> connectionLog() { return List.copyOf(connectionLog); }
```

In `teardown(FakePluginContext context, String reason)`, after the loop that fails open activities (it ends with `open.fraction(), reason, open.activateActionId()));` and a closing brace), add:

```java
        for (FakeConnection open : List.copyOf(connections.values()))
            if (open.pluginId.equals(id)) open.close();
```

In `deliver(String topicId, Object payload)`, after the line
`        if (payload instanceof ActivityEvent event && topicId.equals(Activities.TOPIC.id())) activityLog.add(event);` add:

```java
        if (payload instanceof ConnectionEvent event && topicId.equals(Activities.CONNECTIONS.id())) connectionLog.add(event);
```

After the whole `FakeActivity` inner class (just before the Javadoc of `public List<String> panels()`), add:

```java
    ConnectionHandle connection(String pluginId, ConnectionSpec spec) {
        var connection = new FakeConnection(pluginId, Objects.requireNonNull(spec, "spec"));
        connections.put(connection.id, connection);
        connection.report(spec.state(), spec.detail());
        return connection;
    }

    private final class FakeConnection implements ConnectionHandle {
        private final UUID id = UUID.randomUUID();
        private final String pluginId;
        private final ConnectionSpec spec;
        private boolean closed;
        /** The last delivery queued; running empties it, so a report can only join one still waiting. */
        private AtomicReference<ConnectionEvent> queued;

        FakeConnection(String pluginId, ConnectionSpec spec) { this.pluginId = pluginId; this.spec = spec; }

        @Override public UUID id() { return id; }
        @Override public void up(String detail) { report(ConnectionEvent.State.UP, detail); }
        @Override public void degraded(String detail) { report(ConnectionEvent.State.DEGRADED, detail); }
        @Override public void down(String detail) { report(ConnectionEvent.State.DOWN, detail); }

        @Override public void close() {
            synchronized (this) {
                if (closed) return;
                closed = true;
                connections.remove(id);
                queue(event(ConnectionEvent.State.CLOSED, ""));
            }
        }

        void report(ConnectionEvent.State state, String detail) {
            ConnectionEvent event = event(state, Objects.requireNonNull(detail, "detail"));
            synchronized (this) {
                if (closed) return;
                AtomicReference<ConnectionEvent> last = queued;
                ConnectionEvent waiting = last == null ? null : last.get();
                // The same state still waiting: only the detail changed, so the newer text replaces it.
                if (waiting != null && waiting.state() == state && last.compareAndSet(waiting, event)) return;
                queue(event);
            }
        }

        private void queue(ConnectionEvent event) {
            checkType(Activities.CONNECTIONS);
            var slot = new AtomicReference<>(event);
            queued = slot;
            events.add(() -> {
                ConnectionEvent due = slot.getAndSet(null);
                if (due != null) deliver(Activities.CONNECTIONS.id(), due);
            });
        }

        private ConnectionEvent event(ConnectionEvent.State state, String detail) {
            return new ConnectionEvent(id, pluginId, spec.title(), state, detail, spec.activateActionId());
        }
    }
```

In `FakePluginContext.java` add the imports `import dev.jasper.sdk.activity.ConnectionHandle;` and `import dev.jasper.sdk.activity.ConnectionSpec;` beside the other activity imports, and replace

```java
            @Override public ActivityHandle begin(ActivitySpec spec) {
                requireOpen();
                return host.begin(info.id(), spec);
            }
```

with

```java
            @Override public ActivityHandle begin(ActivitySpec spec) {
                requireOpen();
                return host.begin(info.id(), spec);
            }
            @Override public ConnectionHandle connection(ConnectionSpec spec) {
                requireOpen();
                return host.connection(info.id(), spec);
            }
```

- [ ] **Step 8: Run the fake's contract suite to verify it passes**

Run: `./gradlew :jasper-sdk-testkit:test`
Expected: PASS (the two new cases included).

- [ ] **Step 9: Implement connections in the application**

Create `jasper-app/src/main/java/dev/jasper/app/plugins/ConnectionHub.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.sdk.activity.Activities;
import dev.jasper.sdk.activity.ConnectionEvent;
import dev.jasper.sdk.activity.ConnectionHandle;
import dev.jasper.sdk.activity.ConnectionSpec;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns connection lifecycles: every state change delivered in order, a detail-only report joined to the delivery
 * still queued for that state, exactly one CLOSED, the source stamped here rather than by the plugin. Handles are
 * safe from any thread.
 */
final class ConnectionHub {
    private final EventBus bus;
    private final ConcurrentHashMap<UUID, Handle> handles = new ConcurrentHashMap<>();

    ConnectionHub(EventBus bus) {
        this.bus = bus;
        bus.checkType(Activities.CONNECTIONS);
    }

    ConnectionHandle open(String pluginId, ConnectionSpec spec) {
        var handle = new Handle(pluginId, Objects.requireNonNull(spec, "spec"));
        handles.put(handle.id, handle);
        handle.report(spec.state(), spec.detail());
        return handle;
    }

    /** Closes every connection the plugin left open. */
    void closeAll(String pluginId) {
        for (Handle handle : List.copyOf(handles.values()))
            if (handle.pluginId.equals(pluginId)) handle.close();
    }

    private final class Handle implements ConnectionHandle {
        private final UUID id = UUID.randomUUID();
        private final String pluginId;
        private final ConnectionSpec spec;
        private boolean closed;
        /** The last delivery queued; running empties it, so a report can only join one still waiting. */
        private AtomicReference<ConnectionEvent> queued;

        Handle(String pluginId, ConnectionSpec spec) { this.pluginId = pluginId; this.spec = spec; }

        @Override public UUID id() { return id; }
        @Override public void up(String detail) { report(ConnectionEvent.State.UP, detail); }
        @Override public void degraded(String detail) { report(ConnectionEvent.State.DEGRADED, detail); }
        @Override public void down(String detail) { report(ConnectionEvent.State.DOWN, detail); }

        @Override public void close() {
            synchronized (this) {
                if (closed) return;
                closed = true;
                handles.remove(id);
                queue(event(ConnectionEvent.State.CLOSED, ""));
            }
        }

        void report(ConnectionEvent.State state, String detail) {
            ConnectionEvent event = event(state, Objects.requireNonNull(detail, "detail"));
            synchronized (this) {
                if (closed) return;
                AtomicReference<ConnectionEvent> last = queued;
                ConnectionEvent waiting = last == null ? null : last.get();
                // The same state still waiting: only the detail changed, so the newer text replaces it.
                if (waiting != null && waiting.state() == state && last.compareAndSet(waiting, event)) return;
                queue(event);
            }
        }

        private void queue(ConnectionEvent event) {
            var slot = new AtomicReference<>(event);
            queued = slot;
            bus.enqueue(() -> {
                ConnectionEvent due = slot.getAndSet(null);
                if (due != null) bus.deliver(Activities.CONNECTIONS.id(), due);
            });
        }

        private ConnectionEvent event(ConnectionEvent.State state, String detail) {
            return new ConnectionEvent(id, pluginId, spec.title(), state, detail, spec.activateActionId());
        }
    }
}
```

In `PluginHost.java` replace

```java
    final ActivityHub activities;
```

with

```java
    final ActivityHub activities;
    final ConnectionHub connections;
```

and replace

```java
        this.activities = new ActivityHub(bus);
```

with

```java
        this.activities = new ActivityHub(bus);
        this.connections = new ConnectionHub(bus);
```

In `HostedContext.java` add the imports `import dev.jasper.sdk.activity.ConnectionHandle;` and `import dev.jasper.sdk.activity.ConnectionSpec;` beside the other activity imports. In `teardown`, replace

```java
        host.activities.failAll(id, reason);
```

with

```java
        host.activities.failAll(id, reason);
        host.connections.closeAll(id);
```

and in `activities()` replace

```java
            @Override public ActivityHandle begin(ActivitySpec spec) {
                requireOpen();
                return host.activities.begin(id, spec);
            }
```

with

```java
            @Override public ActivityHandle begin(ActivitySpec spec) {
                requireOpen();
                return host.activities.begin(id, spec);
            }
            @Override public ConnectionHandle connection(ConnectionSpec spec) {
                requireOpen();
                return host.connections.open(id, spec);
            }
```

- [ ] **Step 10: Run the application's contract suite to verify it passes**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.plugins.AppContractTest`
Expected: PASS (the two new cases included).

- [ ] **Step 11: Document connections**

In `jasper-sdk/README.md` replace the table row

```
| `dev.jasper.sdk.activity` | `Activities`, `ActivitySpec`, `ActivityHandle`, `ActivityEvent` |
```

with

```
| `dev.jasper.sdk.activity` | `Activities`, `ActivitySpec`, `ActivityHandle`, `ActivityEvent`, `ConnectionSpec`, `ConnectionHandle`, `ConnectionEvent` |
```

and after the paragraph that begins `SDK 0.10.1 lets` (it ends `runs scripted background work while it waits.`) add a blank line and:

```
SDK 0.10.2 adds connections, things that are up until they are not, such as a tunnel:
`Activities.connection(ConnectionSpec)` returns a `ConnectionHandle` whose `up`, `degraded` and `down` publish
`ConnectionEvent`s on `Activities.CONNECTIONS`. A new state is always delivered, a report that only changes the detail
is coalesced, and `close()` (or the plugin stopping) publishes `CLOSED` once. Jasper shows connections on Buddy.
`FakePluginHost.connectionLog()` records them.
```

In `docs/plugin-authoring.md`, after the bullet

```
- **Activities.** `context.activities().begin(...)` returns a handle; end it exactly once.
  Progress is coalesced. Jasper shows activities on Buddy. An activity left open when the
  plugin stops is ended as failed.
```

add

```
- **Connections.** `context.activities().connection(...)` announces something that is up until
  it is not; report `up`, `degraded` or `down` as it changes and `close()` it once it no longer
  matters. A new state is always delivered; a detail-only change is coalesced. Jasper shows
  connections on Buddy. A connection left open when the plugin stops is closed.
```

and replace

```
drive a contributed scope as the palette would; `paletteOpens()`, `notices()` and `openedInEditor()`
record what the plugin asked for.
```

with

```
drive a contributed scope as the palette would; `paletteOpens()`, `notices()` and `openedInEditor()`
record what the plugin asked for. `connectionLog()` lists delivered connection events, as
`activityLog()` does activities.
```

- [ ] **Step 12: Verify everything and commit**

Run the hygiene check from `AGENTS.md` (expected: no output), then `./gradlew check`.
Expected: BUILD SUCCESSFUL; the SDK, testkit and app test counts each grow by the new cases (SDK +2, testkit +2, app +2).

```bash
git branch --show-current
git add jasper-sdk jasper-sdk-testkit jasper-app/src/main/java/dev/jasper/app/plugins jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java docs/plugin-authoring.md
git commit -m "SDK 0.10.2: connection activities in the SDK, the fake and the app

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: `Platform.browse` in the SDK and both runtimes

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Platform.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/Browser.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/platform/BrowserTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java` (`Environment` record)
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java` (the `Environment` construction in `start`)
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`, `HostedFileSourcesTest.java`, `HostedPaletteTest.java` (the `Environment` constructions)
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java`, `FakePluginContext.java`
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java`, `PluginContractTest.java`
- Modify: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`
- Modify: `jasper-sdk/README.md`, `docs/plugin-authoring.md`, `docs/sdk-architecture.md`

**Interfaces:**
- Consumes: Task 1's SDK version 0.10.2.
- Produces: `void Platform.browse(URI uri)`; `public static void dev.jasper.app.platform.Browser.open(URI uri)`; `PluginHost.Environment` gains a last component `Consumer<java.net.URI> browser`; `FakePluginHost.browsed()` returning `List<URI>`; `ContractHarness.browsed()`.

- [ ] **Step 1: Write the failing tests**

In `ContractHarness.java`, after `List<java.nio.file.Path> openedInEditor();` add:

```java
        /** Every web address a plugin asked to open in the browser, in request order. */
        List<java.net.URI> browsed();
```

In `PluginContractTest.java`, insert immediately before `    @Test void hostedTabsPreserveIdentityAndDeferMovesDuringPromptsAndCloseDecisions() {`:

```java
    @Test void browseOpensOnlyWebAddressesAndReachesTheHost() {
        PluginContext a = started("test.a", Set.of());
        h.ui(() -> {
            a.platform().browse(java.net.URI.create("http://localhost:5432/"));
            assertThatIllegalArgumentException().isThrownBy(() -> a.platform().browse(java.net.URI.create("file:///etc/hosts")));
            assertThatIllegalArgumentException().isThrownBy(() -> a.platform().browse(java.net.URI.create("localhost:5432")));
            assertThatIllegalArgumentException().isThrownBy(() -> a.platform().browse(java.net.URI.create("/relative")));
            assertThatThrownBy(() -> a.platform().browse(null)).isInstanceOf(NullPointerException.class);
        });
        h.flush();
        for (int i = 0; i < 100 && h.browsed().isEmpty(); i++) { try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
        assertThat(h.browsed()).containsExactly(java.net.URI.create("http://localhost:5432/"));
    }

```

In `FakeContractTest.java`, after the `openedInEditor()` override add:

```java
            @Override public List<java.net.URI> browsed() { return host.browsed(); }
```

Create `jasper-app/src/test/java/dev/jasper/app/platform/BrowserTest.java`:

```java
package dev.jasper.app.platform;

import java.awt.GraphicsEnvironment;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BrowserTest {
    @Test void neverRunsOnTheEventDispatchThread() throws Exception {
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try { Browser.open(URI.create("https://example.com/")); }
            catch (Throwable thrown) { failure.set(thrown); }
        });
        assertThat(failure.get()).isInstanceOf(IllegalStateException.class);
    }

    @Test void aHeadlessDesktopCannotOpenABrowser() {
        // Guarded so running this test in a desktop session never opens a real browser.
        assumeTrue(GraphicsEnvironment.isHeadless(), "only meaningful headless");
        assertThatThrownBy(() -> Browser.open(URI.create("https://example.com/"))).isInstanceOf(UnsupportedOperationException.class);
    }
}
```

In `AppContractTest.java`, replace the last `host(...)` overload:

```java
    /** As above, with the shell profiles plugins list and open. */
    static PluginHost host(Path data, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark, AuxiliaryWindows auxiliary,
                           TerminalRegistry terminals, List<String> notices, List<Path> edited,
                           List<dev.jasper.app.config.ConfigDiagnostic> diagnostics, PluginRuntime.ShellProfiles profiles) {
        var files = new PluginSettingsFiles(data, diagnostics::add);
        return onEdtValue(() -> new PluginHost(new PluginHost.Environment(SwingUtilities::invokeLater,
            SwingUtilities::isEventDispatchThread, data::resolve, diagnostics::add, drainGrace, contributions, dark::get, auxiliary, terminals,
            notices::add, edited::add, files, file -> diagnostics.removeIf(diagnostic -> diagnostic.file().equals(file)), profiles)));
    }
```

with

```java
    /** As above, with the shell profiles plugins list and open. */
    static PluginHost host(Path data, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark, AuxiliaryWindows auxiliary,
                           TerminalRegistry terminals, List<String> notices, List<Path> edited,
                           List<dev.jasper.app.config.ConfigDiagnostic> diagnostics, PluginRuntime.ShellProfiles profiles) {
        return host(data, drainGrace, contributions, dark, auxiliary, terminals, notices, edited, diagnostics, profiles, new CopyOnWriteArrayList<>());
    }

    /** As above; the web addresses plugins ask to open land in {@code browsed}. */
    static PluginHost host(Path data, Duration drainGrace,
                           Contributions contributions, java.util.concurrent.atomic.AtomicBoolean dark, AuxiliaryWindows auxiliary,
                           TerminalRegistry terminals, List<String> notices, List<Path> edited,
                           List<dev.jasper.app.config.ConfigDiagnostic> diagnostics, PluginRuntime.ShellProfiles profiles,
                           List<java.net.URI> browsed) {
        var files = new PluginSettingsFiles(data, diagnostics::add);
        return onEdtValue(() -> new PluginHost(new PluginHost.Environment(SwingUtilities::invokeLater,
            SwingUtilities::isEventDispatchThread, data::resolve, diagnostics::add, drainGrace, contributions, dark::get, auxiliary, terminals,
            notices::add, edited::add, files, file -> diagnostics.removeIf(diagnostic -> diagnostic.file().equals(file)), profiles,
            browsed::add)));
    }
```

In `newHarness()` replace

```java
        PluginHost host = host(data, Duration.ofMillis(200), contributions, dark, auxiliary, terminalFixture.registry, notices, edited,
            diagnostics, shells.source());
```

with

```java
        List<java.net.URI> browsed = new CopyOnWriteArrayList<>();
        PluginHost host = host(data, Duration.ofMillis(200), contributions, dark, auxiliary, terminalFixture.registry, notices, edited,
            diagnostics, shells.source(), browsed);
```

and after `                        @Override public List<Path> openedInEditor() { return List.copyOf(edited); }` add:

```java
                        @Override public List<java.net.URI> browsed() { return List.copyOf(browsed); }
```

In `HostedFileSourcesTest.java` and `HostedPaletteTest.java`, replace `PluginRuntime.ShellProfiles.NONE)));` (one occurrence in each file) with `PluginRuntime.ShellProfiles.NONE, uri -> { })));`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:test --tests dev.jasper.sdk.testing.FakeContractTest`
Expected: FAIL — compilation errors: `Platform` has no `browse(URI)`, `FakePluginHost` has no `browsed()`.

- [ ] **Step 3: Add `browse` to the SDK**

Replace the whole of `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Platform.java` with:

```java
package dev.jasper.sdk.ui;

import java.net.URI;
import java.nio.file.Path;

/**
 * Desktop integration the application already does for its own files and links. Implemented by the
 * application and by the testkit.
 */
public interface Platform {
    /**
     * Opens a file in the user's editor: the configured editor, then the desktop's edit or open
     * action, then revealing the file. Runs in the background; a failure is reported through
     * {@link Notices}, never thrown.
     *
     * @param file the file
     */
    void openInEditor(Path file);

    /**
     * Opens a web address in the user's browser. Runs in the background; a failure is reported
     * through {@link Notices}, never thrown.
     *
     * @param uri an absolute {@code http} or {@code https} address
     * @throws IllegalArgumentException when the address is not an absolute http or https one
     * @since 0.10.2
     */
    void browse(URI uri);
}
```

- [ ] **Step 4: Implement `browse` in the fake**

In `FakePluginHost.java`, after the field `        final List<Path> openedInEditor = new ArrayList<>();` add:

```java
    final List<java.net.URI> browsed = new CopyOnWriteArrayList<>();
```

and after the accessor `        public List<Path> openedInEditor() { return List.copyOf(openedInEditor); }` add:

```java

    /**
     * Every web address a plugin asked to open in the browser.
     *
     * @return the addresses in request order
     */
    public List<java.net.URI> browsed() { return List.copyOf(browsed); }
```

In `FakePluginContext.java`, replace

```java
        @Override public dev.jasper.sdk.ui.Platform platform() { return file -> { requireOpen(); host.openedInEditor.add(Objects.requireNonNull(file, "file")); }; }
```

with

```java
        @Override public dev.jasper.sdk.ui.Platform platform() {
            return new dev.jasper.sdk.ui.Platform() {
                @Override public void openInEditor(Path file) { requireOpen(); host.openedInEditor.add(Objects.requireNonNull(file, "file")); }
                @Override public void browse(java.net.URI uri) { requireOpen(); host.browsed.add(webAddress(uri)); }
            };
        }

        /** Only an absolute http or https address opens in a browser. */
        private static java.net.URI webAddress(java.net.URI uri) {
            Objects.requireNonNull(uri, "uri");
            String scheme = uri.getScheme();
            if (scheme == null || uri.isOpaque() || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")))
                throw new IllegalArgumentException("Only an http or https address opens in a browser: " + uri);
            return uri;
        }
```

- [ ] **Step 5: Run the fake's tests to verify they pass**

Run: `./gradlew :jasper-sdk-testkit:test`
Expected: PASS.

- [ ] **Step 6: Implement `browse` in the application**

Create `jasper-app/src/main/java/dev/jasper/app/platform/Browser.java`:

```java
package dev.jasper.app.platform;

import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import javax.swing.SwingUtilities;

/** Opens web addresses in the desktop's browser, for plugins that ask through the SDK. */
public final class Browser {
    private Browser() { }

    /**
     * Opens an address in the desktop's browser; blocks while the desktop takes it, so never on the EDT.
     *
     * @param uri the address, already checked by the caller
     * @throws IllegalStateException on the event dispatch thread
     * @throws UnsupportedOperationException when this desktop cannot open a browser
     * @throws UncheckedIOException when the desktop fails to open it
     */
    public static void open(URI uri) {
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Opening a browser requires a worker thread");
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
            throw new UnsupportedOperationException("This desktop cannot open a browser");
        try { Desktop.getDesktop().browse(uri); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
```

In `PluginHost.java`, replace the `Environment` record header

```java
                       PluginSettingsFiles settingsFiles, Consumer<Path> clearReports, PluginRuntime.ShellProfiles profiles) { }
```

with

```java
                       PluginSettingsFiles settingsFiles, Consumer<Path> clearReports, PluginRuntime.ShellProfiles profiles,
                       Consumer<java.net.URI> browser) { }
```

In `PluginRuntime.java` (`start`), replace

```java
            reports.report(), DRAIN_GRACE, contributions, () -> this.dark, windows, terminals, notice, editor, settingsFiles, reports.clear(), profiles));
```

with

```java
            reports.report(), DRAIN_GRACE, contributions, () -> this.dark, windows, terminals, notice, editor, settingsFiles, reports.clear(), profiles,
            dev.jasper.app.platform.Browser::open));
```

In `HostedContext.java` add `import java.net.URI;` beside the other `java.` imports, and replace the whole `platform()` method

```java
        @Override public Platform platform() {
            return file -> {
                requireOpen();
                Objects.requireNonNull(file, "file");
                background().execute(() -> {
                    try { host.environment.editor().accept(file); }
                    catch (RuntimeException failure) {
                        log().log(System.Logger.Level.WARNING, "Could not open " + file + " in an editor", failure);
                        notices().error("Could not open " + file.getFileName() + " in an editor.");
                    }
                });
            };
        }
```

with

```java
        @Override public Platform platform() {
            return new Platform() {
                @Override public void openInEditor(Path file) {
                    requireOpen();
                    Objects.requireNonNull(file, "file");
                    background().execute(() -> {
                        try { host.environment.editor().accept(file); }
                        catch (RuntimeException failure) {
                            log().log(System.Logger.Level.WARNING, "Could not open " + file + " in an editor", failure);
                            notices().error("Could not open " + file.getFileName() + " in an editor.");
                        }
                    });
                }
                @Override public void browse(URI uri) {
                    requireOpen();
                    URI address = webAddress(uri);
                    background().execute(() -> {
                        try { host.environment.browser().accept(address); }
                        catch (RuntimeException failure) {
                            log().log(System.Logger.Level.WARNING, "Could not open " + address + " in a browser", failure);
                            notices().error("Could not open " + address + " in a browser.");
                        }
                    });
                }
            };
        }

        /** Only an absolute http or https address opens in a browser. */
        private static URI webAddress(URI uri) {
            Objects.requireNonNull(uri, "uri");
            String scheme = uri.getScheme();
            if (scheme == null || uri.isOpaque() || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")))
                throw new IllegalArgumentException("Only an http or https address opens in a browser: " + uri);
            return uri;
        }
```

- [ ] **Step 7: Run the application's tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.plugins.AppContractTest --tests dev.jasper.app.platform.BrowserTest --tests dev.jasper.app.plugins.HostedFileSourcesTest --tests dev.jasper.app.plugins.HostedPaletteTest`
Expected: PASS.

- [ ] **Step 8: Document `browse`**

In `jasper-sdk/README.md`, in the SDK 0.10.2 paragraph added in Task 1, replace its last line

```
`FakePluginHost.connectionLog()` records them.
```

with

```
`FakePluginHost.connectionLog()` records them. `Platform.browse(URI)` opens an http or https address in the user's
browser in the background; `FakePluginHost.browsed()` records the requests.
```

In `docs/plugin-authoring.md` replace

```
window the user is using. `context.platform().openInEditor(file)` opens a file with the user's
editor (configured editor, then the desktop, then revealing the file), in the background; a failure
is reported as a notice.
```

with

```
window the user is using. `context.platform().openInEditor(file)` opens a file with the user's
editor (configured editor, then the desktop, then revealing the file), in the background; a failure
is reported as a notice. `context.platform().browse(uri)` opens an http or https address in the
user's browser the same way (SDK 0.10.2); any other address throws `IllegalArgumentException`.
```

and replace

```
drive a contributed scope as the palette would; `paletteOpens()`, `notices()` and `openedInEditor()`
record what the plugin asked for.
```

with

```
drive a contributed scope as the palette would; `paletteOpens()`, `notices()`, `openedInEditor()`
and `browsed()` record what the plugin asked for.
```

In `docs/sdk-architecture.md` replace

```
reaches the last active window's error handler; `platform().openInEditor` runs the application's
`ConfigEditor` on the plugin's executor and reports failure as a notice. `PaneInfo.shell` is
```

with

```
reaches the last active window's error handler; `platform().openInEditor` runs the application's
`ConfigEditor` on the plugin's executor and reports failure as a notice, and `platform().browse`
does the same with `platform.Browser` after refusing anything but an http or https address. `PaneInfo.shell` is
```

- [ ] **Step 9: Verify everything and commit**

Run the hygiene check from `AGENTS.md` (expected: no output), then `./gradlew check`.
Expected: BUILD SUCCESSFUL.

```bash
git branch --show-current
git add jasper-sdk jasper-sdk-testkit jasper-app docs/plugin-authoring.md docs/sdk-architecture.md
git commit -m "SDK 0.10.2: Platform.browse in the SDK, the fake and the app

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Buddy connection cards and card activation

**Files:**
- Modify (whole file): `jasper-app/src/main/java/dev/jasper/app/notifications/ActivityNotifier.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/notifications/ActivityNotifierTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginRuntimeTest.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/activity/ActivitySpec.java` (Javadoc only)
- Modify: `docs/sdk-architecture.md`, `docs/plugin-authoring.md`, `docs/STATUS.md`

**Interfaces:**
- Consumes: Task 1's `Activities.CONNECTIONS`, `ConnectionEvent`, `ConnectionSpec`; `Contributions.action(String)`, `ActionEntry.invoke(Contributions.Invocation)`, `TerminalRegistry.activeWindow()` / `activePane()`, `WindowEntry.id()`, `PaneEntry.id()`; test fixtures `TerminalFixture` (`registry`, `addWindow()`, `activateWindow(UUID)`), `AppContractTest.onEdt/onEdtValue/headlessWindows`, `PluginJars.build/descriptor`, `BuddyTestSupport` (`notices()`, `acknowledged`, `acknowledge`, `generation()`).
- Produces: `ActivityNotifier.ConnectionState { UP, DEGRADED, DOWN }`; `ActivityNotifier.started(String, UUID, String, String, Optional<Runnable>)`, `finished(String, UUID, String, boolean, String, Optional<Runnable>)`, `connection(String, UUID, String, ConnectionState, String, Optional<Runnable>)`, `connectionClosed(String, UUID)` (the old four- and five-argument `started`/`finished` stay); `PluginHost.ownsAction(String pluginId, String actionId)`; `HostedContext.ownsAction(String)`; `HostedUi.owns(String)`.

- [ ] **Step 1: Write the failing notifier tests**

In `ActivityNotifierTest.java` add the imports

```java
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
```

add these tests after `theOutcomeReplacesTheCardAndCancellationRemovesIt`:

```java
    @Test void aConnectionIsOneCardWhoseNewStateIsNewsAndWhoseDetailIsNot() {
        var notifier = new ActivityNotifier(deck.companion(), () -> { });
        var key = new BuddyNoticeId("dev.jasper.remote", id);
        notifier.connection("dev.jasper.remote", id, "Tunnels via\ndb", ActivityNotifier.ConnectionState.DEGRADED,
            "reconnecting in 1 s", Optional.empty());
        assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.id()).isEqualTo(key);
            assertThat(notice.kind()).isEqualTo(BuddyNotice.Kind.CONNECTION);
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.DEGRADED);
            assertThat(notice.title()).isEqualTo("Tunnels via db");
            assertThat(notice.live()).isTrue();
        });
        deck.acknowledge(key);
        int generation = deck.generation();
        notifier.connection("dev.jasper.remote", id, "Tunnels via db", ActivityNotifier.ConnectionState.DEGRADED,
            "reconnecting in 2 s", Optional.empty());
        assertThat(deck.generation()).as("the same state is not new news").isEqualTo(generation);
        assertThat(deck.acknowledged(key)).isTrue();
        assertThat(deck.notices().getFirst().detail().get()).isEqualTo("reconnecting in 2 s");

        notifier.connection("dev.jasper.remote", id, "Tunnels via db", ActivityNotifier.ConnectionState.DOWN, "gave up", Optional.empty());
        assertThat(deck.acknowledged(key)).as("a new state speaks up again").isFalse();
        assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.DOWN);
            assertThat(notice.detail().get()).isEqualTo("gave up");
        });

        notifier.connectionClosed("dev.jasper.remote", id);
        assertThat(deck.notices()).isEmpty();
    }

    @Test void aCardNamingItsProducersActionRaisesRunsItAndIsAcknowledged() {
        var raised = new AtomicInteger();
        var ran = new AtomicInteger();
        var notifier = new ActivityNotifier(deck.companion(), raised::incrementAndGet);
        UUID other = UUID.randomUUID();
        notifier.connection("p.one", id, "Tunnels", ActivityNotifier.ConnectionState.DOWN, "gave up",
            Optional.<Runnable>of(ran::incrementAndGet));
        notifier.finished("p.one", other, "Upload", false, "reset");
        var byId = deck.notices().stream().collect(Collectors.toMap(BuddyNotice::id, notice -> notice));

        byId.get(new BuddyNoticeId("p.one", id)).activate().run();
        assertThat(raised).hasValue(1);
        assertThat(ran).hasValue(1);
        assertThat(deck.acknowledged(new BuddyNoticeId("p.one", id))).isTrue();

        byId.get(new BuddyNoticeId("p.one", other)).activate().run();
        assertThat(raised).hasValue(2);
        assertThat(ran).hasValue(1);
        assertThat(deck.acknowledged(new BuddyNoticeId("p.one", other))).as("a card without an action only raises").isFalse();
    }
```

and in `nothingIsPostedAfterClose` add, after `notifier.cancelled("p.one", id);`:

```java
        notifier.connection("p.one", id, "Tunnel", ActivityNotifier.ConnectionState.UP, "", Optional.empty());
        notifier.connectionClosed("p.one", id);
```

- [ ] **Step 2: Run the notifier tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.notifications.ActivityNotifierTest`
Expected: FAIL — compilation errors: `ActivityNotifier.ConnectionState`, `connection(...)` and `connectionClosed(...)` do not exist.

- [ ] **Step 3: Rewrite `ActivityNotifier`**

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/notifications/ActivityNotifier.java` with:

```java
package dev.jasper.app.notifications;

import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.view.BuddyCompanion;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Producer for what something other than a terminal pane volunteers: today, plugin activities and plugin
 * connections, each keyed by its source and id. An activity is one task card that appears when it starts; unlike
 * a shell command it was declared worth showing, so there is no threshold. A connection is one connection card: a
 * new state posts it again, which is new news even after it was acknowledged, and closing the connection removes
 * it. Progress, and a report that keeps a connection's state, only change the detail the card already reads, so
 * nothing is re-posted. A click raises the application; a card whose producer named an action then runs it and is
 * acknowledged. This producer never drives Buddy's working animation, which the command notifier owns. EDT only;
 * holds no Swing state.
 */
public final class ActivityNotifier implements AutoCloseable {
    /** What a connection card can say. */
    public enum ConnectionState {
        /** Available. */
        UP,
        /** Available with a problem, or recovering. */
        DEGRADED,
        /** Unavailable. */
        DOWN
    }

    private final Map<BuddyNoticeId, String> details = new HashMap<>();
    private final Map<BuddyNoticeId, ConnectionState> connections = new HashMap<>();
    private BuddyCompanion deck;
    private Runnable activate;

    /**
     * Binds the producer to the companion.
     *
     * @param deck the companion that shows the cards
     * @param activate what every click on a card does first; a producer has no pane to focus, so the application
     *                 raises itself
     */
    public ActivityNotifier(BuddyCompanion deck, Runnable activate) {
        this.deck = Objects.requireNonNull(deck, "deck");
        this.activate = Objects.requireNonNull(activate, "activate");
    }

    /**
     * Posts a running card whose click only raises the application.
     *
     * @param source the producer namespace, a plugin id
     * @param id the activity's identity within that source
     * @param title display title; line breaks become spaces
     * @param detail initial detail line
     */
    public void started(String source, UUID id, String title, String detail) {
        started(source, id, title, detail, Optional.empty());
    }

    /**
     * Posts a running card.
     *
     * @param source the producer namespace, a plugin id
     * @param id the activity's identity within that source
     * @param title display title; line breaks become spaces
     * @param detail initial detail line
     * @param action run after the application is raised when the card is clicked, which then acknowledges the
     *               card; empty to only raise the application
     */
    public void started(String source, UUID id, String title, String detail, Optional<Runnable> action) {
        if (deck == null) return;
        var key = new BuddyNoticeId(source, id);
        details.put(key, detail);
        deck.post(new BuddyNotice(key, BuddyNotice.Kind.TASK, singleLine(title), BuddyNotice.State.RUNNING,
            () -> details.getOrDefault(key, ""), activation(key, action)));
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
     * Replaces the card with its outcome; a click only raises the application.
     *
     * @param source the producer namespace
     * @param id the activity's identity
     * @param title display title
     * @param succeeded whether the work succeeded
     * @param detail final detail line
     */
    public void finished(String source, UUID id, String title, boolean succeeded, String detail) {
        finished(source, id, title, succeeded, detail, Optional.empty());
    }

    /**
     * Replaces the card with its outcome.
     *
     * @param source the producer namespace
     * @param id the activity's identity
     * @param title display title
     * @param succeeded whether the work succeeded
     * @param detail final detail line
     * @param action run after the application is raised when the card is clicked, which then acknowledges the
     *               card; empty to only raise the application
     */
    public void finished(String source, UUID id, String title, boolean succeeded, String detail, Optional<Runnable> action) {
        if (deck == null) return;
        var key = new BuddyNoticeId(source, id);
        details.remove(key);
        deck.post(new BuddyNotice(key, BuddyNotice.Kind.TASK, singleLine(title),
            succeeded ? BuddyNotice.State.DONE : BuddyNotice.State.FAILED, () -> detail, activation(key, action)));
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

    /**
     * Shows a connection. A new state posts the card again, which is new news even after it was acknowledged;
     * the same state only changes the detail the card reads.
     *
     * @param source the producer namespace, a plugin id
     * @param id the connection's identity within that source
     * @param title display title; line breaks become spaces
     * @param state what the card says
     * @param detail detail line
     * @param action run after the application is raised when the card is clicked, which then acknowledges the
     *               card; empty to only raise the application
     */
    public void connection(String source, UUID id, String title, ConnectionState state, String detail, Optional<Runnable> action) {
        if (deck == null) return;
        var key = new BuddyNoticeId(source, id);
        details.put(key, detail);
        if (connections.put(key, Objects.requireNonNull(state, "state")) == state) return;
        BuddyNotice.State shown = switch (state) {
            case UP -> BuddyNotice.State.UP;
            case DEGRADED -> BuddyNotice.State.DEGRADED;
            case DOWN -> BuddyNotice.State.DOWN;
        };
        deck.post(new BuddyNotice(key, BuddyNotice.Kind.CONNECTION, singleLine(title), shown,
            () -> details.getOrDefault(key, ""), activation(key, action)));
    }

    /**
     * Removes a connection's card: its producer no longer reports it.
     *
     * @param source the producer namespace
     * @param id the connection's identity
     */
    public void connectionClosed(String source, UUID id) {
        if (deck == null) return;
        var key = new BuddyNoticeId(source, id);
        details.remove(key);
        connections.remove(key);
        deck.dismiss(key);
    }

    /** Raises the application; a named action then runs and the card counts as seen. */
    private Runnable activation(BuddyNoticeId key, Optional<Runnable> action) {
        Objects.requireNonNull(action, "action");
        return () -> {
            if (deck == null) return;
            activate.run();
            if (action.isEmpty()) return;
            action.get().run();
            if (deck != null) deck.acknowledge(key);
        };
    }

    private static String singleLine(String title) {
        return title.replace("\r", "").replace("\n", " ");
    }

    @Override public void close() {
        deck = null;
        activate = () -> { };
        details.clear();
        connections.clear();
    }
}
```

- [ ] **Step 4: Run the notifier tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.notifications.ActivityNotifierTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Write the failing runtime test**

In `PluginRuntimeTest.java` add the imports `import java.util.UUID;` and `import java.util.concurrent.atomic.AtomicInteger;`, add this fixture after `FIXTURE`:

```java
    static final String LINK = """
        package fix.link;
        import dev.jasper.sdk.activity.ConnectionEvent;
        import dev.jasper.sdk.activity.ConnectionSpec;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import dev.jasper.sdk.ui.ActionSpec;
        import java.nio.file.Files;
        import java.util.Optional;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) {
                context.actions().register(ActionSpec.of("dev.example.link.show", "Show Link"), invoked -> {
                    try { Files.writeString(context.dataDirectory().resolve("shown"), invoked.window().id().toString()); }
                    catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                });
                context.activities().connection(new ConnectionSpec("Link via db", "reconnecting",
                    ConnectionEvent.State.DEGRADED, Optional.of("dev.example.link.show")));
                context.activities().connection(new ConnectionSpec("Stray", "gone",
                    ConnectionEvent.State.DOWN, Optional.of("dev.example.other.show")));
            }
        }
        """;
```

and add this test and helper after `runsADevelopmentPluginBridgesItsActivityAndStopsIt`:

```java
    @Test void connectionCardsFollowTheirPluginAndRunOnlyItsOwnAction() throws Exception {
        Path dev = root.resolve("dev/dev.example.link");
        PluginJars.build(dev, "link.jar", PluginJars.descriptor("dev.example.link", "1.0.0", "fix.link.Main"),
            Map.of("fix.link.Main", LINK), List.of());
        TerminalFixture fixture = AppContractTest.onEdtValue(TerminalFixture::new);
        UUID window = AppContractTest.onEdtValue(fixture::addWindow);
        onEdt(() -> fixture.activateWindow(window));
        var raised = new AtomicInteger();
        var created = new AtomicReference<PluginRuntime>();
        onEdt(() -> created.set(new PluginRuntime(new PluginRuntime.Options(null, root.resolve("absent"), dev, false,
            root.resolve("plugins.toml"), root.resolve("plugins.lock")),
            new ActivityNotifier(deck.companion(), raised::incrementAndGet), new PluginRuntime.SettingsReports(d -> { }, file -> { }),
            new dev.jasper.app.contributions.Contributions(), AppContractTest.headlessWindows(), fixture.registry)));
        PluginRuntime runtime = created.get();
        onEdt(() -> runtime.start(Map.of(), true));
        settle();
        onEdt(() -> {
            assertThat(deck.notices()).hasSize(2)
                .allSatisfy(notice -> assertThat(notice.kind()).isEqualTo(BuddyNotice.Kind.CONNECTION));
            BuddyNotice link = notice("Link via db"), stray = notice("Stray");
            assertThat(link.state()).isEqualTo(BuddyNotice.State.DEGRADED);
            assertThat(link.detail().get()).isEqualTo("reconnecting");
            assertThat(stray.state()).isEqualTo(BuddyNotice.State.DOWN);
            link.activate().run();
            assertThat(raised).hasValue(1);
            assertThat(deck.acknowledged(link.id())).isTrue();
            stray.activate().run();
            assertThat(raised).as("another plugin's action only raises the application").hasValue(2);
            assertThat(deck.acknowledged(stray.id())).isFalse();
        });
        assertThat(root.resolve("absent/dev.example.link/data/shown")).hasContent(window.toString());

        var pending = new AtomicReference<List<CompletableFuture<?>>>();
        onEdt(() -> pending.set(runtime.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        settle();
        onEdt(() -> assertThat(deck.notices()).as("stopping closes the plugin's connections").isEmpty());
    }

    private BuddyNotice notice(String title) {
        return deck.notices().stream().filter(notice -> notice.title().equals(title)).findFirst().orElseThrow();
    }
```

- [ ] **Step 6: Run the runtime test to verify it fails**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.plugins.PluginRuntimeTest`
Expected: FAIL in `connectionCardsFollowTheirPluginAndRunOnlyItsOwnAction`: `deck.notices()` is empty (expected size 2), because nothing forwards `Activities.CONNECTIONS` yet.

- [ ] **Step 7: Forward connections and resolve card actions**

In `HostedUi.java`, insert before `    void requireOwn(String actionId) {`:

```java
    /** Whether this plugin registered the action and has not closed it. */
    boolean owns(String actionId) { return ownActions.contains(actionId); }

```

In `HostedContext.java`, after `    @Override public Actions actions() { return ui.actions(); }` add:

```java
    /** UI thread: whether this plugin registered the action and has not closed it. */
    boolean ownsAction(String actionId) { return ui.owns(actionId); }
```

In `PluginHost.java`, after the method

```java
    boolean active(String pluginId) {
        HostedContext context = contexts.get(pluginId);
        return context != null && context.state == HostedContext.State.ACTIVE;
    }
```

add

```java

    /** UI thread: whether the plugin has not stopped and has this action registered. */
    boolean ownsAction(String pluginId, String actionId) {
        requireUi();
        HostedContext context = contexts.get(pluginId);
        return context != null && context.state != HostedContext.State.CLOSED && context.ownsAction(actionId);
    }
```

In `PluginRuntime.java`:

Add the imports `import dev.jasper.sdk.activity.ConnectionEvent;` (beside `ActivityEvent`) and `import dev.jasper.app.terminals.PaneEntry;` (beside `TerminalRegistry`).

Replace

```java
    private Subscription bridge;
```

with

```java
    private Subscription bridge, connectionBridge;
```

Replace

```java
        bridge = created.bus.subscribe(EventBus.APP, Activities.TOPIC, this::forward);
```

with

```java
        bridge = created.bus.subscribe(EventBus.APP, Activities.TOPIC, this::forward);
        connectionBridge = created.bus.subscribe(EventBus.APP, Activities.CONNECTIONS, this::forwardConnection);
```

Replace the whole `forward(ActivityEvent event)` method

```java
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
```

with

```java
    private void forward(ActivityEvent event) {
        String detail = event.fraction().isPresent()
            ? Math.round(event.fraction().getAsDouble() * 100) + "%" + (event.detail().isEmpty() ? "" : " · " + event.detail())
            : event.detail();
        Optional<Runnable> action = activation(event.sourcePluginId(), event.activateActionId());
        switch (event.state()) {
            case STARTED -> notifier.started(event.sourcePluginId(), event.id(), event.title(), detail, action);
            case PROGRESS -> notifier.progress(event.sourcePluginId(), event.id(), detail);
            case SUCCEEDED -> notifier.finished(event.sourcePluginId(), event.id(), event.title(), true, event.detail(), action);
            case FAILED -> notifier.finished(event.sourcePluginId(), event.id(), event.title(), false, event.detail(), action);
            case CANCELLED -> notifier.cancelled(event.sourcePluginId(), event.id());
        }
    }

    private void forwardConnection(ConnectionEvent event) {
        switch (event.state()) {
            case UP -> connection(event, ActivityNotifier.ConnectionState.UP);
            case DEGRADED -> connection(event, ActivityNotifier.ConnectionState.DEGRADED);
            case DOWN -> connection(event, ActivityNotifier.ConnectionState.DOWN);
            case CLOSED -> notifier.connectionClosed(event.sourcePluginId(), event.id());
        }
    }

    private void connection(ConnectionEvent event, ActivityNotifier.ConnectionState state) {
        notifier.connection(event.sourcePluginId(), event.id(), event.title(), state, event.detail(),
            activation(event.sourcePluginId(), event.activateActionId()));
    }

    /**
     * A card's own activation: the source plugin's action, invoked in the window the user used last. Empty when
     * the id names no action that plugin registered, so the card only raises the application.
     */
    private Optional<Runnable> activation(String source, Optional<String> actionId) {
        PluginHost current = host;
        if (current == null) return Optional.empty();
        return actionId.filter(id -> current.ownsAction(source, id)).<Runnable>map(id -> () -> {
            if (!current.ownsAction(source, id)) return;
            terminals.activeWindow().ifPresent(window -> contributions.action(id).ifPresent(entry ->
                entry.invoke(new Contributions.Invocation(window.id(), terminals.activePane().map(PaneEntry::id)))));
        });
    }
```

In `jasper-sdk/src/main/java/dev/jasper/sdk/activity/ActivitySpec.java` replace

```java
 * @param activateActionId id of an action that brings the user to this work; resolved once the
 *                         actions API exists, ignored before then
```

with

```java
 * @param activateActionId one of the plugin's own actions that brings the user to this work, run when the user
 *                         clicks its card (from SDK 0.10.2); any other id only raises the application
```

- [ ] **Step 8: Run the runtime and notifier tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.plugins.PluginRuntimeTest --tests dev.jasper.app.notifications.ActivityNotifierTest`
Expected: PASS.

- [ ] **Step 9: Document the bridge and record the status**

In `docs/sdk-architecture.md` replace

```
- **Activities.** `ActivityHub` stamps the source, coalesces progress and ends each activity
  once. `PluginRuntime` forwards them to `notifications.ActivityNotifier`, which posts Buddy
  cards keyed by `BuddyNoticeId(pluginId, activityId)`.
```

with

```
- **Activities.** `ActivityHub` stamps the source, coalesces progress and ends each activity
  once. `PluginRuntime` forwards them to `notifications.ActivityNotifier`, which posts Buddy
  cards keyed by `BuddyNoticeId(pluginId, activityId)`.
- **Connections** (SDK 0.10.2). `ConnectionHub` stamps the source, delivers every state change in
  order, joins a detail-only report to the delivery still queued for that state and publishes one
  `CLOSED`. `PluginRuntime` forwards `Activities.CONNECTIONS` to `ActivityNotifier.connection`, which
  posts a `Kind.CONNECTION` card on each new state (new news, even after it was acknowledged),
  updates only the detail otherwise, and dismisses it on `CLOSED`.
- **Card activation.** Every activity and connection card raises the application when clicked.
  When its `activateActionId` names an action the source plugin registered (`PluginHost.ownsAction`),
  the click then invokes it in the last active window and acknowledges the card.
```

In `docs/plugin-authoring.md` replace

```
  connections on Buddy. A connection left open when the plugin stops is closed.
```

with

```
  connections on Buddy. A connection left open when the plugin stops is closed. When an activity's
  or a connection's `activateActionId` names one of your own actions, clicking its Buddy card runs
  that action in the window the user used last.
```

In `docs/STATUS.md`, directly under `## Current state — 2026-09-30` (above the existing first bullet), add:

```
- **Remote tunnels plan 7b-1: SDK 0.10.2 connection activities** (branch `claude/tunnels-7b1`, plan
  `docs/superpowers/plans/2026-09-30-jasper-remote-plan-7b1-connection-activities.md`): `Activities.connection`
  returns a `ConnectionHandle` (`up`, `degraded`, `down`, `close`) publishing `ConnectionEvent`s on
  `Activities.CONNECTIONS` (states in order, detail-only reports coalesced, one `CLOSED`, closed when the plugin
  stops); `Platform.browse(URI)` opens http/https addresses through the new `platform.Browser`; Buddy shows
  connections as connection cards, and a card whose `activateActionId` names the source plugin's own action runs
  it in the last active window and is acknowledged (task cards too). Testkit: `connectionLog()`, `browsed()`;
  contract +3 cases. `./gradlew check` green: <fill in the per-module counts from the XML results>. Not merged.
```

Replace `<fill in the per-module counts from the XML results>` with the real counts from `*/build/test-results/test/*.xml` after Step 10 (format as in the neighbouring entries, e.g. `app 1437/0/1, terminal 391/0/4, …`).

- [ ] **Step 10: Verify everything and commit**

Run the hygiene check from `AGENTS.md` (expected: no output), then `./gradlew check`.
Expected: BUILD SUCCESSFUL. Fill in the STATUS counts, then:

```bash
git branch --show-current
git add jasper-app jasper-sdk/src/main/java/dev/jasper/sdk/activity/ActivitySpec.java docs/sdk-architecture.md docs/plugin-authoring.md docs/STATUS.md
git commit -m "Buddy shows plugin connections and runs a card's own action

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Merging `claude/tunnels-7b1` is the user's decision; plan 7b-2 (tunnels you run by hand) builds on it.
