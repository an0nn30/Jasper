# Jasper Plugin SDK Plan 4b: Plugin-Provided Sessions — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Not started. Record every deviation from this text here and in `docs/STATUS.md`.

**Goal:** Let a plugin open a pane whose session it provides (an SSH connection later, a loopback echo in the sample now): the pane appears first with a status line and Cancel, the connection attaches when it is ready, a stalled remote never freezes typing, the connection is closed exactly once whatever happens, and a disconnected pane offers Reconnect.

**Architecture:** `jasper-terminal` gains `TerminalSession.attach(AttachedConnection, GridSize, scrollback)`. A pure-JDK `internal.transport.AttachedTransport` owns the hard parts: one writer thread with a bounded outbound queue in which resizes are coalesced and ordered with writes, a bounded drain after the remote reports its exit, first-failure-wins transport errors, and an exactly-once `close`. A thin `AttachedConnector` adapts it to JediTerm inside `internal.emulation`, so attached output runs through the same `ShellIntegrationConnector` chain as a PTY. In the app, `dev.jasper.app.terminals` gains the app-native `SessionRequest`, `OpenSpec` and the `SessionAttempt` state machine (`PENDING` → `ATTACHED` | `FAILED` | `CANCELLED`, first transition wins, a connection offered to a finished attempt is closed at once). `workspace.TerminalPane` gains a pending and a disconnected state around the same view. In `plugins`, a `CleanupWorker` runs cancellation handlers and connection closes off the EDT, and `HostedTerminals` adapts `OpenRequest.session`. The testkit fakes all of it and the shared contract suite holds both to the same rules.

**Tech Stack:** Java 25 on JBR 25, Gradle wrapper, Swing, JediTerm core 3.76 (unchanged), JUnit 6.1.3, AssertJ 3.27.7. No new dependency.

**Spec:** `docs/superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md` (section 3 "Threading", section 4 items 4 and 7, section 6 "Plugin-provided sessions" entire, section 11). Earlier plans and their recorded deviations: `docs/superpowers/plans/2026-09-21-jasper-plugin-sdk-plan-1-core-runtime.md` through `…-plan-4a-terminal-api.md`. Executors read all of them, and `docs/terminal-architecture.md` and `docs/terminal-maintenance.md` before touching `jasper-terminal`.

## Global Constraints

- Use `./gradlew`, never a system Gradle. Java 25, JetBrains vendor toolchain. `./gradlew check` is headless and must pass at the end of every task.
- **Never launch the GUI** (`:jasper-app:run`, benchmarks, previews that open windows). GUI checks are the user's; the end of this plan lists them.
- Work on branch `claude/plugin-sdk-plan-4b` in `.worktrees/plugin-sdk-plan-4b` (this plan is committed there). Run `git branch --show-current` before every commit and commit only when the verification command exited 0. End commit messages with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- **No public method in `jasper-terminal` takes or returns a JediTerm type.** JediTerm types appear only in `dev.jasper.terminal.internal.emulation`; `verifyTerminalArchitecture` enforces that, an acyclic package graph, and the app-facing allowlist, which this plan extends by exactly one type, `session.AttachedConnection`.
- SDK types appear in the app only inside `dev.jasper.app.plugins`. `terminals` and `workspace` must not import `dev.jasper.sdk`.
- No interface without two real implementations: new SDK interfaces are implemented by the app and by the testkit; app-native seams are final classes, records and JDK functional types.
- **Ownership transfers at the call to `attach`, whatever the outcome.** Every connection passed to `attach` has its `close` invoked exactly once, on the cleanup worker, never on the EDT and never on a session reader thread.
- **Nothing a plugin provides is called on the EDT or under the terminal buffer lock**, except the session `connector`, which runs on the EDT by contract: `output.read` runs on the session's reader thread, `input.write`, `flush` and `resize` on the session's writer thread, `close` and `onCancelled` handlers on the cleanup worker.
- `TerminalSession.write` and `resize` on an attached session only enqueue and return. A write that does not fit the 4 MiB queue is rejected whole.
- The local PTY path does not change behavior.
- Threading of new SDK calls: `Terminals.openTab` and `split` stay EDT-only; every `PendingSession` method is safe from any thread.
- Source hygiene, package-info contracts and Javadoc doclint apply as in earlier plans.
- `AppDocumentationTest` link-checks and example-checks every Markdown file under `docs/superpowers`, including this plan: keep documentation links inside code fences here and never write a literal example marker comment (Task 9 spells it `EXAMPLE-MARKER`).
- Never build file content in an unquoted shell heredoc.
- Known flake, not to be fixed here: `TerminalAppIntegrationTest` `"reflow"` case (see `docs/STATUS.md`). If it is the only failure, rerun.

### Deliberate scope decisions and deviations from the spec

1. **Working-directory provenance moves to plan 4c.** Classifying OSC 7 reports by host, exposing `RemoteDirectory` and passing the machine's local names in `SessionLaunchOptions` is a behavior change to local shells that stands on its own. What 4b needs from it is one rule, and 4b implements that rule: **an attached session never reports a local working directory** (`workingDirectory()` stays empty, `workingDirectoryChanged` does not fire, completed commands carry no directory), so command history, "split in the same directory" and launch capture never receive a remote path. `PaneInfo.remoteDirectory` stays empty until 4c.
2. **The terminal layer does not print an exit line for attached sessions.** A PTY session writes `[process exited with code N]` into the buffer; an attached pane shows the banner instead, with the exit status or the failure message.
3. **An unknown exit status is an exceptionally completed `exitFuture()`**, which the application already reads as "no status". The PTY path keeps completing normally.
4. **Reconnect starts a fresh session and view in the same pane**; the previous scrollback is not carried over.
5. **Cancelling the first attempt closes the pane**; a failed first attempt leaves the pane with the message, Retry and Close. Cancelling or failing a reconnect returns to the disconnected banner, as the spec says.
6. **The attempt's `columns()` and `rows()` are the window's configured initial grid**, because a pending pane has no view to measure; the first layout then resizes the remote pty through the writer.
7. `SessionSpec.icon` is accepted and unused: tabs have no icons today.
8. A user's Split Right or Split Down on a plugin pane opens a local shell in the home directory, as the spec says; duplicating through the provider is not in v1.
9. `JasperSdk.VERSION` becomes `0.5.0`; the sample's range becomes `>=0.5, <0.6`, and the bundled sample additionally declares `terminal.open` and `session.provide`.

## File Structure

```
jasper-terminal/src/main/java/dev/jasper/terminal/
  internal/transport/{package-info,AttachedTransport}.java     create
  internal/emulation/AttachedConnector.java                     create
  internal/emulation/JediTermEngine.java                        modify
  internal/TerminalAccess.java                                  modify
  session/AttachedConnection.java                               create (joins the allowlist)
  session/{TerminalSession,TerminalSessionListener,PtySessionFactory}.java   modify
build.gradle.kts                                                modify: allowlist
jasper-app/src/main/java/dev/jasper/app/
  terminals/{OpenSpec,SessionRequest,SessionAttempt}.java       create
  terminals/{PaneSnapshot,PaneEntry,WindowEntry,TerminalEvent}.java   modify
  launch/ShellLauncher.java                                     modify: session defaults
  workspace/{TerminalPane,TerminalTab,WindowContent,WindowTerminals}.java   modify
  plugins/{CleanupWorker,HostedSessions}.java                   create
  plugins/{HostedTerminals,HostedContext,PluginHost,PluginRuntime,TerminalBridge}.java   modify
jasper-sdk/src/main/java/dev/jasper/sdk/terminal/
  {ExitPolicy,SessionSpec,PendingSession,TerminalConnection}.java   create
  OpenRequest.java                                              modify
jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/
  FakeSessions.java                                             create
  {FakeWorkspace,FakeTerminals,FakePluginHost}.java             modify
plugins/sample/                                                 demo_session
docs/                                                           terminal-architecture, plugin-authoring, sdk-architecture, configuration, STATUS
```

---

### Task 0: Baseline

**Files:** none

- [ ] **Step 1: Confirm the workspace**

Run: `git branch --show-current`
Expected: `claude/plugin-sdk-plan-4b`, in `.worktrees/plugin-sdk-plan-4b`, branched from `main` at `2eee9c7`.

- [ ] **Step 2: Confirm the baseline is green**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL` with 1,359 tests (rerun if the only failure is the known terminal flake). Nothing to commit.

---

### Task 1: The attached transport

**Files:**
- Create: `jasper-terminal/src/main/java/dev/jasper/terminal/internal/transport/{package-info,AttachedTransport}.java`
- Test: `jasper-terminal/src/test/java/dev/jasper/terminal/internal/transport/AttachedTransportTest.java`

**Interfaces:**
- Produces `public final class AttachedTransport implements AutoCloseable` (JDK only):
  - constructor `(InputStream output, OutputStream input, BiConsumer<Integer, Integer> resize, CompletableFuture<Integer> exited, Runnable close, Runnable inputDropped, int capacityBytes, Duration drain)`
  - `void start()`: starts the writer thread and begins watching `exited`; once only
  - `int read(char[] buffer, int offset, int length) throws IOException`: UTF-8 decoded; called by the session's reader thread
  - `boolean ready() throws IOException`
  - `void write(byte[] bytes)`: enqueue whole or drop whole and call `inputDropped`; never blocks, never throws
  - `void resize(int columns, int rows)`: coalesced with a directly preceding queued resize, ordered with writes
  - `boolean connected()`
  - `int awaitExit() throws InterruptedException`: after end of output, the exit status or `UNKNOWN_STATUS`; waits at most the drain window; closes
  - `Optional<String> failure()`: the first transport failure, written for the user
  - `void close()`: idempotent; invokes the connection's `close` exactly once; never throws
  - `static final int UNKNOWN_STATUS = Integer.MIN_VALUE`

- [ ] **Step 1: Write the failing test**

`jasper-terminal/src/test/java/dev/jasper/terminal/internal/transport/AttachedTransportTest.java`:

```java
package dev.jasper.terminal.internal.transport;

import dev.jasper.terminal.testsupport.Await;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachedTransportTest {
    private final PipedOutputStream remote = new PipedOutputStream();
    private final CompletableFuture<Integer> exited = new CompletableFuture<>();
    private final AtomicInteger closes = new AtomicInteger();
    private final AtomicInteger dropped = new AtomicInteger();
    private final List<String> sent = Collections.synchronizedList(new ArrayList<>());
    /** Counted down when the writer thread enters its first flush, so a test knows the queue is empty again. */
    private final CountDownLatch flushing = new CountDownLatch(1);
    private AttachedTransport transport;

    /** Records writes and resizes in the order the writer thread delivers them; {@code gate} can hold the first write. */
    private AttachedTransport open(CountDownLatch gate, OutputStream input, int capacity, Duration drain) throws IOException {
        var output = new PipedInputStream(remote, 1 << 16);
        OutputStream sink = input != null ? input : new OutputStream() {
            private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
            @Override public void write(int value) { pending.write(value); }
            @Override public void flush() throws IOException {
                flushing.countDown();
                try { if (gate != null) gate.await(); } catch (InterruptedException interrupted) { throw new IOException(interrupted); }
                sent.add("write:" + pending.toString(StandardCharsets.UTF_8));
                pending.reset();
            }
        };
        transport = new AttachedTransport(output, sink, (columns, rows) -> sent.add("resize:" + columns + "x" + rows), exited,
            () -> { closes.incrementAndGet(); try { remote.close(); output.close(); } catch (IOException ignored) { } },
            dropped::incrementAndGet, capacity, drain);
        transport.start();
        return transport;
    }

    @AfterEach void close() { if (transport != null) transport.close(); }

    @Test void outputIsDecodedAsUtf8AcrossReads() throws Exception {
        open(null, null, 1024, Duration.ofSeconds(5));
        byte[] bytes = "héllo".getBytes(StandardCharsets.UTF_8);
        remote.write(bytes, 0, 2);
        remote.flush();
        char[] buffer = new char[16];
        assertThat(transport.read(buffer, 0, buffer.length)).isEqualTo(1);
        remote.write(bytes, 2, bytes.length - 2);
        remote.flush();
        int read = transport.read(buffer, 1, buffer.length - 1);
        assertThat(new String(buffer, 0, 1 + read)).isEqualTo("héllo");
        assertThat(transport.connected()).isTrue();
    }

    @Test void writesAndResizesKeepTheirOrderAndQueuedResizesCoalesce() throws Exception {
        var gate = new CountDownLatch(1);
        open(gate, null, 1024, Duration.ofSeconds(5));
        transport.write("one".getBytes(StandardCharsets.UTF_8));
        assertThat(flushing.await(10, java.util.concurrent.TimeUnit.SECONDS)).as("the writer blocks in the first flush").isTrue();
        transport.resize(100, 30);
        transport.resize(120, 40);
        transport.write("two".getBytes(StandardCharsets.UTF_8));
        transport.resize(80, 24);
        gate.countDown();
        Await.until(() -> sent.size() == 4, "everything delivered");
        assertThat(sent).containsExactly("write:one", "resize:120x40", "write:two", "resize:80x24");
    }

    @Test void aWriteThatDoesNotFitIsDroppedWholeAndNothingBlocks() throws Exception {
        var gate = new CountDownLatch(1);
        open(gate, null, 8, Duration.ofSeconds(5));
        transport.write("1234".getBytes(StandardCharsets.UTF_8));
        assertThat(flushing.await(10, java.util.concurrent.TimeUnit.SECONDS)).as("the writer holds the first write").isTrue();
        transport.write("5678".getBytes(StandardCharsets.UTF_8));
        transport.write("abcdefghij".getBytes(StandardCharsets.UTF_8));
        transport.write("9".getBytes(StandardCharsets.UTF_8));
        transport.write("toolong!!".getBytes(StandardCharsets.UTF_8));
        assertThat(dropped).as("two whole writes were rejected, on the caller's thread, at once").hasValue(2);
        gate.countDown();
        Await.until(() -> sent.size() == 3, "the rest delivered");
        assertThat(sent).containsExactly("write:1234", "write:5678", "write:9");
    }

    @Test void aFailingWriteIsATransportFailureAndClosesOnce() throws Exception {
        open(null, new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("broken pipe"); }
        }, 1024, Duration.ofSeconds(5));
        transport.write("x".getBytes(StandardCharsets.UTF_8));
        Await.until(() -> closes.get() == 1, "closed after the failure");
        assertThat(transport.failure()).hasValueSatisfying(message -> assertThat(message).contains("broken pipe"));
        assertThat(transport.connected()).isFalse();
        transport.close();
        transport.write("late".getBytes(StandardCharsets.UTF_8));
        assertThat(closes).hasValue(1);
        assertThat(dropped).hasValue(0);
    }

    @Test void afterTheRemoteExitsOutputIsDrainedToTheEndThenClosedOnce() throws Exception {
        open(null, null, 1024, Duration.ofSeconds(5));
        remote.write("bye".getBytes(StandardCharsets.UTF_8));
        exited.complete(3);
        remote.close();
        char[] buffer = new char[16];
        assertThat(transport.read(buffer, 0, buffer.length)).isEqualTo(3);
        assertThat(transport.read(buffer, 0, buffer.length)).isEqualTo(-1);
        assertThat(transport.awaitExit()).isEqualTo(3);
        assertThat(transport.failure()).isEmpty();
        assertThat(closes).hasValue(1);
    }

    @Test void aRemoteThatExitsWithoutEndingItsOutputIsCutOffAfterTheDrainWindow() throws Exception {
        open(null, null, 1024, Duration.ofMillis(100));
        exited.complete(0);
        char[] buffer = new char[16];
        assertThatThrownBy(() -> { while (transport.read(buffer, 0, buffer.length) >= 0) { } }).isInstanceOf(IOException.class);
        assertThat(transport.awaitExit()).isEqualTo(0);
        assertThat(transport.failure()).as("closing our own side is not a failure").isEmpty();
        assertThat(closes).hasValue(1);
    }

    @Test void anUnknownExitAndAFailedExitAreSaidSo() throws Exception {
        open(null, null, 1024, Duration.ofMillis(100));
        remote.close();
        char[] buffer = new char[16];
        assertThat(transport.read(buffer, 0, buffer.length)).isEqualTo(-1);
        assertThat(transport.awaitExit()).isEqualTo(AttachedTransport.UNKNOWN_STATUS);
        assertThat(transport.failure()).hasValueSatisfying(message -> assertThat(message).contains("exit status"));
        assertThat(closes).hasValue(1);
    }

    @Test void aConnectionWhoseExitFailsReportsThatFailure() throws Exception {
        open(null, null, 1024, Duration.ofMillis(100));
        exited.completeExceptionally(new IOException("connection reset"));
        Await.until(() -> closes.get() == 1, "closed after the drain window");
        assertThat(transport.awaitExit()).isEqualTo(AttachedTransport.UNKNOWN_STATUS);
        assertThat(transport.failure()).hasValueSatisfying(message -> assertThat(message).contains("connection reset"));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-terminal:compileTestJava`
Expected: compilation FAILS (package `internal.transport` does not exist).

- [ ] **Step 3: Implement**

`package-info.java`:

```java
/**
 * Byte transport for a session whose program is not a local process: reading, an application-owned writer
 * thread behind a bounded queue, draining after exit, and closing exactly once. JDK only; no emulator types.
 * Unsupported for application or plugin use.
 */
package dev.jasper.terminal.internal.transport;
```

`AttachedTransport.java`:

```java
package dev.jasper.terminal.internal.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

/**
 * One attached connection. A stalled remote must never freeze typing, pasting or resizing, so callers only
 * enqueue: one writer thread delivers writes (each followed by a flush) and resizes in submission order,
 * a write that does not fit is rejected whole, and a resize directly behind another queued resize replaces it.
 * After the remote reports its exit, output is read to its end or until the drain window closes the
 * connection. {@code close} reaches the connection exactly once. Unsupported module collaboration API.
 */
public final class AttachedTransport implements AutoCloseable {
    /** The status of a session that ended without a usable exit status. */
    public static final int UNKNOWN_STATUS = Integer.MIN_VALUE;
    private static final System.Logger LOG = System.getLogger(AttachedTransport.class.getName());

    private final Reader reader;
    private final OutputStream input;
    private final BiConsumer<Integer, Integer> resize;
    private final CompletableFuture<Integer> exited;
    private final Runnable closeConnection;
    private final Runnable inputDropped;
    private final int capacity;
    private final Duration drain;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    /** {@code byte[]} is a write, {@code int[2]} a resize. */
    private final ArrayDeque<Object> queue = new ArrayDeque<>();
    private long queuedBytes;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<String> failure = new AtomicReference<>();

    /** Unsupported module collaboration API; used only by terminal owners. */
    public AttachedTransport(InputStream output, OutputStream input, BiConsumer<Integer, Integer> resize, CompletableFuture<Integer> exited,
                             Runnable close, Runnable inputDropped, int capacityBytes, Duration drain) {
        this.reader = new InputStreamReader(Objects.requireNonNull(output, "output"), StandardCharsets.UTF_8);
        this.input = Objects.requireNonNull(input, "input");
        this.resize = Objects.requireNonNull(resize, "resize");
        this.exited = Objects.requireNonNull(exited, "exited");
        this.closeConnection = Objects.requireNonNull(close, "close");
        this.inputDropped = Objects.requireNonNull(inputDropped, "inputDropped");
        if (capacityBytes <= 0) throw new IllegalArgumentException("The outbound queue needs a positive capacity");
        this.capacity = capacityBytes;
        this.drain = Objects.requireNonNull(drain, "drain");
    }

    /** Starts the writer thread and begins watching for the remote's exit. Once only. */
    public void start() {
        if (!started.compareAndSet(false, true)) throw new IllegalStateException("transport already started");
        Thread.ofPlatform().name("jasper-session-writer").daemon().start(this::writeLoop);
        exited.whenComplete((status, error) -> {
            if (error != null) fail("The connection ended: " + describe(error));
            // Final output may still be in flight: the reader goes on to the end of output, but not forever.
            CompletableFuture.delayedExecutor(drain.toMillis(), TimeUnit.MILLISECONDS).execute(this::close);
        });
    }

    private static String describe(Throwable error) {
        Throwable cause = error instanceof ExecutionException && error.getCause() != null ? error.getCause() : error;
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private void fail(String message) {
        if (failure.compareAndSet(null, message)) LOG.log(System.Logger.Level.INFO, "Attached session transport failure: " + message);
    }

    /** Reader thread. Blocks; {@link #close()} is what unblocks it. */
    public int read(char[] buffer, int offset, int length) throws IOException {
        try { return reader.read(buffer, offset, length); }
        catch (IOException broken) {
            // Our own close, or the cut-off after the drain window, is not the remote's failure.
            if (!closed.get() && !exited.isDone()) fail("Reading from the remote side failed: " + describe(broken));
            throw broken;
        }
    }

    /** Whether a read would not block. */
    public boolean ready() throws IOException { return reader.ready(); }

    /** Any thread. Enqueues the whole write or drops it whole; never blocks and never runs connection code. */
    public void write(byte[] bytes) {
        if (closed.get() || bytes.length == 0) return;
        boolean fits;
        lock.lock();
        try {
            fits = queuedBytes + bytes.length <= capacity;
            if (fits) { queue.add(bytes.clone()); queuedBytes += bytes.length; changed.signal(); }
        } finally { lock.unlock(); }
        if (fits) return;
        try { inputDropped.run(); }
        catch (RuntimeException listenerFailure) { LOG.log(System.Logger.Level.WARNING, "An input-dropped listener failed", listenerFailure); }
    }

    /** Any thread. Only the latest of consecutive queued resizes reaches the connection. */
    public void resize(int columns, int rows) {
        if (closed.get()) return;
        lock.lock();
        try {
            if (queue.peekLast() instanceof int[] pending) { pending[0] = columns; pending[1] = rows; }
            else { queue.add(new int[]{columns, rows}); changed.signal(); }
        } finally { lock.unlock(); }
    }

    private void writeLoop() {
        while (true) {
            Object next;
            lock.lock();
            try {
                while (queue.isEmpty() && !closed.get()) changed.await();
                if (closed.get()) return;
                next = queue.poll();
                if (next instanceof byte[] bytes) queuedBytes -= bytes.length;
            } catch (InterruptedException interrupted) {
                return;
            } finally { lock.unlock(); }
            try {
                if (next instanceof byte[] bytes) { input.write(bytes); input.flush(); }
                else { int[] size = (int[]) next; resize.accept(size[0], size[1]); }
            } catch (IOException | RuntimeException broken) {
                if (!closed.get()) fail("Sending to the remote side failed: " + describe(broken));
                close();
                return;
            }
        }
    }

    /** Whether the connection has not been closed. */
    public boolean connected() { return !closed.get(); }

    /**
     * Reader thread, after the end of output: the remote's exit status, waiting at most the drain window for it.
     * The connection is closed when this returns.
     */
    public int awaitExit() throws InterruptedException {
        try {
            Integer status = exited.get(drain.toMillis(), TimeUnit.MILLISECONDS);
            if (status != null) return status;
            fail("The connection ended without an exit status");
            return UNKNOWN_STATUS;
        } catch (TimeoutException silent) {
            fail("The connection ended without an exit status");
            return UNKNOWN_STATUS;
        } catch (ExecutionException | CancellationException broken) {
            fail("The connection ended: " + describe(broken));
            return UNKNOWN_STATUS;
        } finally { close(); }
    }

    /** The first transport failure, written for the user. */
    public Optional<String> failure() { return Optional.ofNullable(failure.get()); }

    /** Idempotent, any thread, never throws. The connection's close is invoked exactly once. */
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        lock.lock();
        try { queue.clear(); queuedBytes = 0; changed.signalAll(); }
        finally { lock.unlock(); }
        try { closeConnection.run(); }
        catch (RuntimeException broken) { LOG.log(System.Logger.Level.WARNING, "Closing an attached connection failed", broken); }
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-terminal:test --tests '*AttachedTransportTest' verifyTerminalArchitecture`
Expected: PASS. Run the test class five times (`--rerun-tasks`): it is a threading test and must be stable.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-terminal
git commit -m "feat: add the attached-session transport with an owned writer, drain and exactly-once close

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: `TerminalSession.attach`

**Files:**
- Create: `jasper-terminal/src/main/java/dev/jasper/terminal/session/AttachedConnection.java`, `jasper-terminal/src/main/java/dev/jasper/terminal/internal/emulation/AttachedConnector.java`
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/internal/emulation/JediTermEngine.java`, `.../internal/TerminalAccess.java`, `.../session/{TerminalSession,TerminalSessionListener}.java`, `build.gradle.kts`
- Test: `jasper-terminal/src/test/java/dev/jasper/terminal/session/AttachedSessionTest.java`

**Interfaces:**
- Produces:
  - `public record AttachedConnection(InputStream output, OutputStream input, BiConsumer<Integer, Integer> resize, CompletableFuture<Integer> exited, Runnable close)`: `close` must be idempotent, must unblock a pending read and must return promptly
  - `public static TerminalSession TerminalSession.attach(AttachedConnection connection, GridSize grid, int scrollback)`: any thread; ownership of the connection transfers at the call, also when it throws
  - `default void TerminalSessionListener.inputDropped()`: a write was rejected because the remote is not accepting input; any thread
  - For an attached session: `workingDirectory()` is always empty, `workingDirectoryChanged` never fires, `commandExecuted` carries no directory, `foregroundJob()` is empty, no exit line is written into the buffer, and `exitFuture()` completes exceptionally when the status is unknown or the transport failed
  - `TerminalSession.ATTACHED_QUEUE_BYTES = 4 * 1024 * 1024`, `TerminalSession.ATTACHED_DRAIN = Duration.ofSeconds(2)` (package-private constants)

- [ ] **Step 1: Write the failing test**

`jasper-terminal/src/test/java/dev/jasper/terminal/session/AttachedSessionTest.java`:

```java
package dev.jasper.terminal.session;

import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.testsupport.Await;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachedSessionTest {
    private final PipedOutputStream remote = new PipedOutputStream();
    private final ByteArrayOutputStream typed = new ByteArrayOutputStream();
    private final CompletableFuture<Integer> exited = new CompletableFuture<>();
    private final AtomicInteger closes = new AtomicInteger();
    private final List<String> resizes = Collections.synchronizedList(new ArrayList<>());
    private TerminalSession session;

    private TerminalSession attach() throws IOException {
        var output = new PipedInputStream(remote, 1 << 16);
        OutputStream input = new OutputStream() {
            @Override public synchronized void write(int value) { typed.write(value); }
        };
        session = TerminalSession.attach(new AttachedConnection(output, input, (columns, rows) -> resizes.add(columns + "x" + rows), exited,
            () -> { closes.incrementAndGet(); try { remote.close(); output.close(); } catch (IOException ignored) { } }), new GridSize(40, 10), 100);
        return session;
    }

    private void feed(String text) throws IOException { remote.write(text.getBytes(StandardCharsets.UTF_8)); remote.flush(); }
    private String typed() { return typed.toString(StandardCharsets.UTF_8); }

    @AfterEach void close() { if (session != null) session.close(); }

    @Test void outputReachesTheScreenAndInputAndResizesReachTheConnection() throws Exception {
        attach();
        feed("remote says hi");
        Await.until(() -> "remote says hi".equals(session.internalAccess().lineText(0).strip()), "output on the first row");
        session.write("ls\r");
        session.write(new byte[]{3});
        session.resize(100, 30);
        Await.until(() -> resizes.contains("100x30") && typed().equals("ls\r\u0003"), "input and resize delivered by the writer thread");
        assertThat(session.columns()).isEqualTo(100);
        assertThat(session.foregroundJob()).isEmpty();
        assertThat(session.exitFuture()).isNotDone();
    }

    @Test void shellIntegrationWorksButNeverReportsALocalDirectory() throws Exception {
        attach();
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        session.addListener(new TerminalSessionListener() {
            @Override public void workingDirectoryChanged(Path directory) { events.add("cwd " + directory); }
            @Override public void commandStarted(String command) { events.add("started " + command); }
            @Override public void commandExecuted(String command, OptionalInt exitStatus, Optional<Path> workingDirectory, Duration duration) {
                events.add("finished " + command + " " + exitStatus + " " + workingDirectory);
            }
        });
        feed("\033]7;file://build-host/srv/app\033\\\033]133;A\033\\$ \033]133;B\033\\make\033]133;C\033\\\r\nok\r\n\033]133;D;2\033\\");
        Await.until(() -> events.stream().anyMatch(event -> event.startsWith("finished")), "the command finished");
        assertThat(events).containsExactly("started make", "finished make OptionalInt[2] Optional.empty");
        assertThat(session.workingDirectory()).as("a remote path must never look local").isEmpty();
        assertThat(session.shellIntegrationDetected()).isTrue();
    }

    @Test void aNormalExitDrainsCompletesWithTheStatusWritesNoExitLineAndClosesOnce() throws Exception {
        attach();
        feed("last words");
        exited.complete(3);
        remote.close();
        assertThat(session.exitFuture().get(10, TimeUnit.SECONDS)).isEqualTo(3);
        assertThat(session.internalAccess().lineText(0).strip()).isEqualTo("last words");
        assertThat(session.internalAccess().lineText(1).strip()).as("the pane's banner reports the exit, not the buffer").isEmpty();
        assertThat(closes).hasValue(1);
        session.close();
        session.write("late");
        assertThat(closes).hasValue(1);
    }

    @Test void aTransportFailureCompletesTheExitExceptionallyWithItsMessage() throws Exception {
        attach();
        exited.completeExceptionally(new IOException("connection reset by peer"));
        assertThatThrownBy(() -> session.exitFuture().get(10, TimeUnit.SECONDS)).hasRootCauseMessage("The connection ended: connection reset by peer");
        assertThat(closes).hasValue(1);
    }

    @Test void closingTheSessionClosesTheConnectionOnceAndEndsTheReader() throws Exception {
        attach();
        feed("x");
        session.close();
        session.close();
        Await.until(() -> session.exitFuture().isDone(), "the reader ended after close unblocked it");
        assertThat(closes).hasValue(1);
    }

    @Test void aRejectedWriteIsReportedToListeners() throws Exception {
        attach();
        var dropped = new AtomicInteger();
        session.addListener(new TerminalSessionListener() { @Override public void inputDropped() { dropped.incrementAndGet(); } });
        session.write(new byte[TerminalSession.ATTACHED_QUEUE_BYTES + 1]);
        assertThat(dropped).hasValue(1);
    }

    @Test void attachValidatesAndStillOwnsTheConnectionWhenItFails() throws Exception {
        var output = new PipedInputStream(remote);
        var connection = new AttachedConnection(output, OutputStream.nullOutputStream(), (columns, rows) -> { }, exited, closes::incrementAndGet);
        assertThatThrownBy(() -> TerminalSession.attach(connection, new GridSize(40, 10), -1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(closes).as("ownership transferred at the call").hasValue(1);
        assertThatThrownBy(() -> new AttachedConnection(null, OutputStream.nullOutputStream(), (columns, rows) -> { }, exited, () -> { }))
            .isInstanceOf(NullPointerException.class);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-terminal:compileTestJava`
Expected: compilation FAILS (`AttachedConnection`, `attach`, `inputDropped` not found).

- [ ] **Step 3: Implement**

`session/AttachedConnection.java`:

```java
package dev.jasper.terminal.session;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * A program that is not a local process, for example a remote shell. The session reads {@code output} on its
 * reader thread, calls {@code input.write}, {@code flush} and {@code resize} only on its own writer thread, and
 * calls {@code close} exactly once. {@code close} must be idempotent, must unblock a pending read and must return
 * promptly; the caller chooses the thread it really runs on by what it passes here.
 *
 * @param output program to terminal, UTF-8; end of stream after the program ends
 * @param input terminal to program
 * @param resize columns and rows of the grid
 * @param exited the exit status; exceptional completion means the connection failed
 * @param close releases the connection
 */
public record AttachedConnection(InputStream output, OutputStream input, BiConsumer<Integer, Integer> resize,
                                 CompletableFuture<Integer> exited, Runnable close) {
    /** Rejects nulls. */
    public AttachedConnection {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(resize, "resize");
        Objects.requireNonNull(exited, "exited");
        Objects.requireNonNull(close, "close");
    }
}
```

`internal/emulation/AttachedConnector.java`:

```java
package dev.jasper.terminal.internal.emulation;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import dev.jasper.terminal.internal.transport.AttachedTransport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** JediTerm protocol adapter; the transport owns the threads, the queue and the connection's lifetime. */
final class AttachedConnector implements TtyConnector {
    private final AttachedTransport transport;
    AttachedConnector(AttachedTransport transport) { this.transport = transport; }
    AttachedTransport transport() { return transport; }
    public int read(char[] buffer, int offset, int length) throws IOException { return transport.read(buffer, offset, length); }
    public void write(byte[] bytes) { transport.write(bytes); }
    public void write(String text) { write(text.getBytes(StandardCharsets.UTF_8)); }
    public boolean isConnected() { return transport.connected(); }
    public boolean ready() throws IOException { return transport.ready(); }
    public void resize(TermSize size) { transport.resize(size.getColumns(), size.getRows()); }
    public int waitFor() throws InterruptedException { return transport.awaitExit(); }
    public String getName() { return "attached"; }
    public void close() { transport.close(); }
}
```

In `JediTermEngine`: import `dev.jasper.terminal.internal.transport.AttachedTransport`; next to `private final PtyConnector pty;` add `private final AttachedTransport attached;` and, in the package-private constructor next to the `pty` assignment, `this.attached = connector instanceof AttachedConnector value ? value.transport() : null;`. Add the public constructor next to the `PtyChild` one:

```java
    /** An attached connection instead of a child process. */
    public JediTermEngine(AttachedTransport transport, int columns, int rows, int scrollback, Events events) {
        this(new AttachedConnector(transport), columns, rows, scrollback, events);
    }
```

In `startReading()`, after the `compareAndSet` guard, add `if (attached != null) attached.start();`. In `readLoop()` replace the two lines `writeExitMessage(code); exit.complete(code);` with:

```java
        if (attached == null) {
            writeExitMessage(code);
            exit.complete(code);
            return;
        }
        // An attached pane's banner reports the end; a failed transport or an unknown status is not an exit code.
        Optional<String> failure = attached.failure();
        if (failure.isPresent()) exit.completeExceptionally(new IOException(failure.get()));
        else if (code == AttachedTransport.UNKNOWN_STATUS) exit.completeExceptionally(new IOException("The connection ended without an exit status"));
        else exit.complete(code);
```

In `TerminalAccess`: add a field `private final boolean local;`, imports `dev.jasper.terminal.internal.shell.CompletedCommand` and `dev.jasper.terminal.internal.transport.AttachedTransport`, and replace the existing `TerminalAccess(JediTermEngine, JediTermEngine.Events, LongSupplier)` constructor with a delegating one plus the shared private form and the attached form:

```java
    /** Unsupported module collaboration API; used only by terminal owners. */
    public TerminalAccess(JediTermEngine engine, JediTermEngine.Events events, LongSupplier clock) { this(engine, events, clock, true); }

    /** An attached connection instead of a child process. Unsupported module collaboration API. */
    public TerminalAccess(AttachedTransport transport, int columns, int rows, int scrollback, JediTermEngine.Events events) {
        this(transport, columns, rows, scrollback, withoutLocalDirectories(events), false);
    }

    private TerminalAccess(AttachedTransport transport, int columns, int rows, int scrollback, JediTermEngine.Events remote, boolean local) {
        this(new JediTermEngine(transport, columns, rows, scrollback, remote), remote, System::nanoTime, local);
    }

    private TerminalAccess(JediTermEngine engine, JediTermEngine.Events events, LongSupplier clock, boolean local) {
        this.engine = engine;
        this.local = local;
        queries = engine.queries();
        shell = new ShellCommandTracker(clock, queries::cursor, queries::captureCommand, queries::recordPrompt,
            events.workingDirectoryChanged(), events.commandStarted(), events.commandFinished(),
            events.screenChanged(), engine::resetCursorShape);
        engine.setShellHooks(shell::accept, shell::discardUnusedPayload);
    }

    /**
     * What an attached program reports as its directory is a path on another machine. Until reports are classified
     * by host, none of them may reach consumers that treat a directory as local.
     */
    private static JediTermEngine.Events withoutLocalDirectories(JediTermEngine.Events events) {
        return new JediTermEngine.Events(events.screenChanged(), events.titleChanged(), events.bell(), events.scrollbackReset(),
            events.alternateBufferChanged(), directory -> { }, events.commandStarted(),
            command -> events.commandFinished().accept(new CompletedCommand(command.command(), command.status(), Optional.empty(), command.duration())));
    }
```

`workingDirectory()` becomes `return local ? shell.workingDirectory() : Optional.empty();`. If the `Events` record's accessor names differ from the ones used here, use its real names; the component order is the one `TerminalSession`'s constructor passes.

In `TerminalSessionListener` add:

```java
    /**
     * An attached session rejected input because the remote side is not accepting it and the outbound queue is
     * full. Called on the thread that wrote; never wait for the EDT here.
     */
    public default void inputDropped() {
    }
```

In `TerminalSession` add imports `dev.jasper.terminal.config.GridSize`, `dev.jasper.terminal.internal.transport.AttachedTransport`, `java.time.Duration`, and:

```java
    static final int ATTACHED_QUEUE_BYTES = 4 * 1024 * 1024;
    static final Duration ATTACHED_DRAIN = Duration.ofSeconds(2);

    /**
     * Runs a program that is not a local process. Ownership of the connection transfers at this call, whatever
     * the outcome: its {@code close} is invoked exactly once, at the latest when the session is closed. Output
     * runs through the same shell-integration chain as a local session, but an attached session never reports a
     * local working directory. Writes and resizes only enqueue; see {@link TerminalSessionListener#inputDropped()}.
     * Callable from any thread.
     */
    public static TerminalSession attach(AttachedConnection connection, GridSize grid, int scrollback) {
        Objects.requireNonNull(connection, "connection");
        TerminalSession[] created = new TerminalSession[1];
        return PtySessionFactory.finish(connection.close(), () -> {
            Objects.requireNonNull(grid, "grid");
            if (scrollback < 0 || scrollback > 1_000_000) throw new IllegalArgumentException("Invalid scrollback capacity.");
            var transport = new AttachedTransport(connection.output(), connection.input(), connection.resize(), connection.exited(),
                connection.close(), () -> created[0].listeners.forEach(TerminalSessionListener::inputDropped), ATTACHED_QUEUE_BYTES, ATTACHED_DRAIN);
            created[0] = new TerminalSession(events -> new TerminalAccess(transport, grid.columns(), grid.rows(), scrollback, events));
            return created[0];
        });
    }
```

`PtySessionFactory.finish(Runnable, Supplier)` already closes on a failed construction and starts reading on success; the validation lives inside the supplier so a rejected argument still closes the connection.

In the root `build.gradle.kts`, add `"session.AttachedConnection"` to the `supported` set, after `"session.TerminalSessionListener"`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-terminal:check verifyTerminalArchitecture`
Expected: PASS: every existing terminal test still passes (the PTY path is unchanged), Javadoc doclint is clean, and the package graph stays acyclic (`session` → `internal` → `internal.emulation` → `internal.transport`).

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-terminal build.gradle.kts
git commit -m "feat: attach a terminal session to a connection instead of a child process

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: App-native session requests and attempts

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/terminals/{OpenSpec,SessionRequest,SessionAttempt}.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/terminals/{PaneSnapshot,PaneEntry,WindowEntry,TerminalEvent,package-info}.java`
- Modify callers: `jasper-app/src/main/java/dev/jasper/app/workspace/{TerminalPane,WindowTerminals}.java`, `jasper-app/src/main/java/dev/jasper/app/plugins/{HostedTerminals,TerminalBridge}.java`
- Test: create `jasper-app/src/test/java/dev/jasper/app/terminals/SessionAttemptTest.java`; update `TerminalRegistryTest`, `WindowTerminalsTest`, `TerminalFixture`

**Interfaces:**
- Produces:
  - `sealed interface OpenSpec` with `record Local(Optional<Path> directory)` and `record Session(SessionRequest request)`
  - `record SessionRequest(String providerId, String title, boolean closeOnExit, Consumer<SessionAttempt> connector, Executor cleanup)`: `connector` is called on the EDT once per attempt; `cleanup` runs connection closes and cancellation handlers
  - `final class SessionAttempt` (every method safe from any thread): constructor `(UUID paneId, int columns, int rows, Executor cleanup, Consumer<Runnable> ui)`; `enum State { PENDING, ATTACHED, FAILED, CANCELLED }`; `UUID paneId()`, `int columns()`, `int rows()`, `State state()`, `void status(String)`, `void attach(AttachedConnection)`, `void fail(String)`, `void cancel()`, `boolean isCancelled()`, `Subscription onCancelled(Runnable)`; pane callbacks set on the UI thread before the connector runs and invoked on it: `Consumer<String> onStatus`, `Consumer<AttachedConnection> onAttached`, `Consumer<String> onFailed`
  - The connection handed to `onAttached` is a guarded copy: its `close` runs the original `close` on `cleanup`, once. A connection offered to an attempt that is not `PENDING` is closed the same way at once.
  - `PaneSnapshot` gains an eighth component `Optional<String> providerId`; `WindowEntry.openTab` becomes `Function<OpenSpec, Optional<PaneEntry>>`; `PaneEntry.split` becomes `BiFunction<SplitAxis, OpenSpec, Optional<PaneEntry>>`; `TerminalEvent` gains `SessionConnecting(UUID paneId)`
- Until Task 4, `WindowTerminals` answers an `OpenSpec.Session` with `Optional.empty()`.

- [ ] **Step 1: Write the failing test**

`jasper-app/src/test/java/dev/jasper/app/terminals/SessionAttemptTest.java`:

```java
package dev.jasper.app.terminals;

import dev.jasper.terminal.session.AttachedConnection;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SessionAttemptTest {
    private final List<Runnable> cleanup = new ArrayList<>();
    private final List<Runnable> posted = new ArrayList<>();
    private final List<String> seen = new ArrayList<>();
    private final AtomicInteger closes = new AtomicInteger();

    private SessionAttempt attempt() {
        var attempt = new SessionAttempt(UUID.randomUUID(), 120, 40, cleanup::add, posted::add);
        attempt.onStatus = text -> seen.add("status " + text);
        attempt.onAttached = connection -> seen.add("attached");
        attempt.onFailed = message -> seen.add("failed " + message);
        return attempt;
    }

    private AttachedConnection connection() {
        return new AttachedConnection(InputStream.nullInputStream(), OutputStream.nullOutputStream(), (columns, rows) -> { },
            new CompletableFuture<>(), closes::incrementAndGet);
    }

    private void run(List<Runnable> queue) { while (!queue.isEmpty()) queue.remove(0).run(); }

    @Test void statusThenAttachReachThePaneOnTheUiThreadAndTheFirstOutcomeWins() {
        SessionAttempt attempt = attempt();
        assertThat(attempt.columns()).isEqualTo(120);
        assertThat(attempt.rows()).isEqualTo(40);
        attempt.status("Authenticating");
        assertThat(seen).as("posted, not called on the caller's thread").isEmpty();
        run(posted);
        attempt.attach(connection());
        attempt.fail("too late");
        attempt.status("too late");
        attempt.cancel();
        run(posted);
        assertThat(seen).containsExactly("status Authenticating", "attached");
        assertThat(attempt.state()).isEqualTo(SessionAttempt.State.ATTACHED);
        assertThat(attempt.isCancelled()).isFalse();
        assertThat(closes).as("the pane owns it now").hasValue(0);
    }

    @Test void theGuardedConnectionClosesOnceOnTheCleanupWorker() {
        SessionAttempt attempt = attempt();
        List<AttachedConnection> handed = new ArrayList<>();
        attempt.onAttached = handed::add;
        attempt.attach(connection());
        run(posted);
        handed.get(0).close().run();
        handed.get(0).close().run();
        assertThat(closes).as("never on the caller's thread").hasValue(0);
        run(cleanup);
        assertThat(closes).hasValue(1);
    }

    @Test void aConnectionOfferedToAFinishedAttemptIsClosedAtOnce() {
        SessionAttempt cancelled = attempt();
        cancelled.cancel();
        cancelled.attach(connection());
        SessionAttempt failed = attempt();
        failed.fail("refused");
        failed.attach(connection());
        SessionAttempt attached = attempt();
        attached.attach(connection());
        attached.attach(connection());
        run(posted);
        run(cleanup);
        assertThat(closes).as("three rejected connections, each closed once").hasValue(3);
        assertThat(seen).containsExactly("failed refused", "attached");
    }

    @Test void cancellationHandlersRunOnceOnTheCleanupWorkerEvenWhenRegisteredLate() {
        SessionAttempt attempt = attempt();
        attempt.onCancelled(() -> seen.add("first"));
        var withdrawn = attempt.onCancelled(() -> seen.add("withdrawn"));
        withdrawn.close();
        attempt.cancel();
        attempt.cancel();
        assertThat(attempt.isCancelled()).isTrue();
        assertThat(seen).as("not on the caller's thread").isEmpty();
        run(cleanup);
        attempt.onCancelled(() -> seen.add("late"));
        run(cleanup);
        assertThat(seen).containsExactly("first", "late");

        SessionAttempt done = attempt();
        done.attach(connection());
        done.onCancelled(() -> seen.add("never"));
        done.cancel();
        run(cleanup);
        assertThat(seen).doesNotContain("never");
    }
}
```

In `TerminalRegistryTest` and `TerminalFixture` add `Optional.empty()` as the last argument of every `new PaneSnapshot(...)`. In `WindowTerminalsTest` wrap the open arguments: `pane.split().apply(SplitAxis.RIGHT, new OpenSpec.Local(Optional.empty()))`, `first.split().apply(SplitAxis.DOWN, new OpenSpec.Local(Optional.empty()))` and `openTab().apply(new OpenSpec.Local(Optional.of(HOME)))` (import `dev.jasper.app.terminals.OpenSpec`). In `TerminalFixture` the two lambdas take the spec: read the directory with `spec instanceof OpenSpec.Local local ? local.directory() : Optional.<Path>empty()`; Task 5 teaches the fixture sessions.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation FAILS (`SessionAttempt`, `OpenSpec` not found).

- [ ] **Step 3: Implement**

`OpenSpec.java`:

```java
package dev.jasper.app.terminals;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** What a new tab or split runs. */
public sealed interface OpenSpec {
    /** The user's configured shell, in a directory or where New Tab would start. */
    record Local(Optional<Path> directory) implements OpenSpec {
        public Local { Objects.requireNonNull(directory, "directory"); }
    }

    /** A session somebody else provides. */
    record Session(SessionRequest request) implements OpenSpec {
        public Session { Objects.requireNonNull(request, "request"); }
    }
}
```

`SessionRequest.java`:

```java
package dev.jasper.app.terminals;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * A pane whose session somebody else provides. The pane appears first; {@code connector} is called on the EDT
 * once per attempt, first connect and every reconnect alike, and must not block. {@code cleanup} runs the
 * closes of connections and the cancellation handlers, never on the EDT.
 *
 * @param providerId who provides the session, for display and diagnostics
 * @param title the pane's title until the program sets one
 * @param closeOnExit close the pane when the session ends instead of offering Reconnect
 * @param connector starts one connection attempt
 * @param cleanup where closes and cancellation handlers run
 */
public record SessionRequest(String providerId, String title, boolean closeOnExit, Consumer<SessionAttempt> connector, Executor cleanup) {
    public SessionRequest {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(connector, "connector");
        Objects.requireNonNull(cleanup, "cleanup");
    }
}
```

`SessionAttempt.java`:

```java
package dev.jasper.app.terminals;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.terminal.session.AttachedConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * One attempt to connect a pane. Exactly one of attach, fail and cancel takes effect; the first wins and the
 * rest are ignored. Ownership of a connection transfers at {@link #attach}, whatever the outcome: a connection
 * offered to an attempt that is no longer pending is never read and is closed at once, so a connect that
 * finishes after the user cancelled cannot leak. Every method is safe from any thread; the pane's callbacks
 * run on the UI thread, closes and cancellation handlers on the cleanup executor.
 */
public final class SessionAttempt {
    /** Where the attempt is. */
    public enum State { PENDING, ATTACHED, FAILED, CANCELLED }

    private static final System.Logger LOG = System.getLogger(SessionAttempt.class.getName());
    private final UUID paneId;
    private final int columns;
    private final int rows;
    private final Executor cleanup;
    private final Consumer<Runnable> ui;
    private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);
    private final List<Runnable> cancelHandlers = new ArrayList<>();
    /** Pane callbacks: set on the UI thread before the connector runs, invoked on the UI thread. */
    public Consumer<String> onStatus = text -> { };
    public Consumer<AttachedConnection> onAttached = connection -> { };
    public Consumer<String> onFailed = message -> { };

    public SessionAttempt(UUID paneId, int columns, int rows, Executor cleanup, Consumer<Runnable> ui) {
        this.paneId = Objects.requireNonNull(paneId); this.columns = columns; this.rows = rows;
        this.cleanup = Objects.requireNonNull(cleanup); this.ui = Objects.requireNonNull(ui);
    }

    public UUID paneId() { return paneId; }
    /** The grid to request from the remote side; the pane resizes it once it has been laid out. */
    public int columns() { return columns; }
    public int rows() { return rows; }
    public State state() { return state.get(); }
    public boolean isCancelled() { return state.get() == State.CANCELLED; }

    public void status(String text) {
        Objects.requireNonNull(text, "text");
        if (state.get() != State.PENDING) { LOG.log(System.Logger.Level.DEBUG, "Status for a finished attempt ignored"); return; }
        ui.accept(() -> { if (state.get() == State.PENDING) onStatus.accept(text); });
    }

    public void attach(AttachedConnection connection) {
        AttachedConnection owned = owned(Objects.requireNonNull(connection, "connection"));
        if (!state.compareAndSet(State.PENDING, State.ATTACHED)) {
            LOG.log(System.Logger.Level.DEBUG, "A connection arrived for a finished attempt; closing it");
            owned.close().run();
            return;
        }
        ui.accept(() -> onAttached.accept(owned));
    }

    /** The same connection, whose close reaches the original exactly once and on the cleanup executor. */
    private AttachedConnection owned(AttachedConnection connection) {
        var once = new AtomicBoolean();
        return new AttachedConnection(connection.output(), connection.input(), connection.resize(), connection.exited(),
            () -> { if (once.compareAndSet(false, true)) cleanup.execute(connection.close()); });
    }

    public void fail(String message) {
        String text = message == null || message.isBlank() ? "The connection failed" : message;
        if (!state.compareAndSet(State.PENDING, State.FAILED)) { LOG.log(System.Logger.Level.DEBUG, "Failure of a finished attempt ignored"); return; }
        ui.accept(() -> onFailed.accept(text));
    }

    /** The user pressed Cancel, the pane closed, the provider stopped, or Jasper is shutting down. */
    public void cancel() {
        List<Runnable> handlers;
        synchronized (cancelHandlers) {
            if (!state.compareAndSet(State.PENDING, State.CANCELLED)) return;
            handlers = new ArrayList<>(cancelHandlers);
            cancelHandlers.clear();
        }
        handlers.forEach(cleanup::execute);
    }

    /** Runs at most once, on the cleanup executor; at once when the attempt is already cancelled. */
    public Subscription onCancelled(Runnable handler) {
        Objects.requireNonNull(handler, "handler");
        synchronized (cancelHandlers) {
            if (state.get() == State.PENDING) {
                cancelHandlers.add(handler);
                return new Subscription(() -> { synchronized (cancelHandlers) { cancelHandlers.remove(handler); } });
            }
        }
        if (state.get() == State.CANCELLED) cleanup.execute(handler);
        return new Subscription(() -> { });
    }
}
```

In `PaneSnapshot` add the last component `Optional<String> providerId` with a null check (the provider of a plugin session, empty for a local one). In `TerminalEvent` add, after `CommandFinished`: `/** A provided session started connecting, first or again. */ record SessionConnecting(UUID paneId) implements TerminalEvent { }`. In `WindowEntry` change the last component to `Function<OpenSpec, Optional<PaneEntry>> openTab` and its Javadoc to "`openTab` returns the new tab's pane, or empty when the window is closing or the spec cannot be opened"; in `PaneEntry` change the last component to `BiFunction<SplitAxis, OpenSpec, Optional<PaneEntry>> split`. In `terminals/package-info.java` add "Supported terminal session types are used for attached connections." after the first sentence; the allowed Jasper dependency list stays `dev.jasper.app.lifecycle`.

Update the callers so everything compiles with today's behavior:
- `TerminalPane.snapshot()`: pass `Optional.empty()` as the eighth argument, twice.
- `WindowTerminals`: `openTab(OpenSpec spec)` and the split lambda open a `Local` as before and return `Optional.empty()` for a `Session`:

```java
    private Optional<PaneEntry> openTab(OpenSpec spec) {
        if (closed || !(spec instanceof OpenSpec.Local local)) return Optional.empty();
        TerminalTab tab = owner.openTab(local.directory().orElseGet(owner::directory));
        return tab == null || tab.focusedPane() == null ? Optional.empty() : Optional.of(entry(tab, tab.focusedPane()));
    }
```

  and in `entry(tab, pane)`: `(axis, spec) -> spec instanceof OpenSpec.Local local ? Optional.ofNullable(tab.split(pane, axis == SplitAxis.RIGHT ? SplitTree.Axis.RIGHT : SplitTree.Axis.DOWN, local.directory().orElse(null))).map(created -> entry(tab, created)) : Optional.empty()`.
- `HostedTerminals`: pass `new OpenSpec.Local(local.spec().workingDirectory())` to `openTab` and `split`; map `snapshot.providerId()` into `PaneInfo` (`kind` is `PLUGIN` and `providerPluginId` is the id when present, otherwise `LOCAL` and empty).
- `TerminalBridge`: `case TerminalEvent.SessionConnecting fact -> bus.publish(EventBus.APP, TerminalEvents.SESSION_STATE_CHANGED, new TerminalEvents.SessionStateChanged(fact.paneId(), SessionState.CONNECTING, OptionalInt.empty()));`

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.terminals.*' --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.plugins.*' verifyTerminalArchitecture verifyApplicationArchitecture :jasper-app:javadoc`
Expected: PASS: `dev.jasper.app.terminals` uses only the allowlisted `AttachedConnection` from the terminal module.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app
git commit -m "feat: model provided sessions and their connection attempts app-natively

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: Pending and disconnected panes

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/launch/ShellLauncher.java`, `jasper-app/src/main/java/dev/jasper/app/workspace/{TerminalPane,TerminalTab,WindowContent,WindowTerminals}.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/SessionPaneTest.java`

**Interfaces:**
- Produces:
  - `public record ShellLauncher.SessionDefaults(int columns, int lines, int scrollback)`; `public SessionDefaults sessionDefaults()`: the configured initial grid and scrollback, or 80 by 24 and 10,000 when no settings are available
  - `TerminalPane(Path directory, ShellLauncher launcher, SessionRequest requestOrNull)`; the two-argument constructor remains. Package-private for tests: `String noticeText()` (empty when no notice shows), `JButton noticePrimary()`, `JButton noticeSecondary()`; callback `Runnable onConnecting`
  - `TerminalTab(Path directory, ShellLauncher launcher, SessionRequest requestOrNull)`; `TerminalPane split(TerminalPane target, SplitTree.Axis axis, Path directoryOrNull, SessionRequest requestOrNull)`
  - `TerminalTab WindowContent.openTab(Path directory, SessionRequest requestOrNull)`
  - `WindowTerminals` opens an `OpenSpec.Session` as a tab or split in the user's home directory
- Pane states for a provided session: **pending** (status text, Cancel), **running** (the ordinary view), **disconnected** (message, Reconnect or Retry, Close). Notice texts: `Connecting…` until the provider says otherwise; `Disconnected (exit N)`; `Disconnected: <failure>`; `Connection cancelled`; the provider's failure message; `Input dropped: the remote side is not accepting input` for four seconds.

- [ ] **Step 1: Write the failing test**

`jasper-app/src/test/java/dev/jasper/app/workspace/SessionPaneTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.terminals.PaneSnapshot;
import dev.jasper.app.terminals.SessionAttempt;
import dev.jasper.app.terminals.SessionRequest;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.terminal.session.AttachedConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(OS.WINDOWS)
class SessionPaneTest {
    /** An in-memory remote: what the pane types is recorded, and the test decides when it exits. */
    private static final class Loopback {
        final PipedOutputStream remote = new PipedOutputStream();
        final PipedInputStream output;
        final ByteArrayOutputStream typed = new ByteArrayOutputStream();
        final CompletableFuture<Integer> exited = new CompletableFuture<>();
        final AtomicInteger closes = new AtomicInteger();
        Loopback() throws IOException { output = new PipedInputStream(remote, 1 << 16); }
        AttachedConnection connection() {
            OutputStream input = new OutputStream() { @Override public void write(int value) { typed.write(value); } };
            return new AttachedConnection(output, input, (columns, rows) -> { }, exited,
                () -> { closes.incrementAndGet(); try { remote.close(); output.close(); } catch (IOException ignored) { } });
        }
        void exit(int status) throws IOException { exited.complete(status); remote.close(); }
        String typed() { return typed.toString(StandardCharsets.UTF_8); }
    }

    private final List<SessionAttempt> attempts = Collections.synchronizedList(new ArrayList<>());
    private final List<TerminalEvent> events = new ArrayList<>();
    private WindowContent owner;
    private TerminalRegistry registry;
    private TerminalPane pane;

    @AfterEach void close() throws Exception { closeOwners(); }

    private void open(boolean closeOnExit) throws Exception {
        edt(() -> {
            owner = content(launcher(new ArrayDeque<>()));
            registry = new TerminalRegistry();
            registry.onEvent(events::add);
            owner.connectTerminals(registry, () -> { });
            var request = new SessionRequest("dev.example.ssh", "build-host", closeOnExit, attempts::add, Runnable::run);
            pane = owner.openTab(HOME, request).focusedPane();
        });
    }

    private <T> T onEdt(java.util.function.Supplier<T> query) throws Exception {
        Object[] result = new Object[1];
        edt(() -> result[0] = query.get());
        @SuppressWarnings("unchecked") T value = (T) result[0];
        return value;
    }

    @Test void thePaneAppearsFirstThenShowsTheAttachedSessionAndTypesIntoIt() throws Exception {
        open(false);
        edt(() -> {
            assertThat(attempts).hasSize(1);
            assertThat(attempts.get(0).paneId()).isEqualTo(pane.id());
            assertThat(attempts.get(0).columns()).isPositive();
            assertThat(pane.noticeText()).isEqualTo("Connecting…");
            assertThat(pane.noticeSecondary().getText()).isEqualTo("Cancel");
            assertThat(pane.noticePrimary().isVisible()).isFalse();
            PaneSnapshot pending = pane.snapshot();
            assertThat(pending.state()).isEqualTo(PaneSnapshot.State.STARTING);
            assertThat(pending.providerId()).contains("dev.example.ssh");
            assertThat(pending.title()).isEqualTo("build-host");
            assertThat(pending.workingDirectory()).as("a remote pane has no local directory").isEmpty();
            assertThat(events).contains(new TerminalEvent.SessionConnecting(pane.id()));
        });
        attempts.get(0).status("Authenticating…");
        until(() -> pane.noticeText().equals("Authenticating…"));
        var loopback = new Loopback();
        attempts.get(0).attach(loopback.connection());
        until(() -> pane.view() != null);
        edt(() -> {
            assertThat(pane.noticeText()).isEmpty();
            assertThat(pane.snapshot().state()).isEqualTo(PaneSnapshot.State.RUNNING);
            assertThat(pane.snapshot().providerId()).contains("dev.example.ssh");
            assertThat(events).contains(new TerminalEvent.SessionStarted(pane.id()));
            assertThat(pane.directory()).as("a user's split starts a local shell at home").isEqualTo(HOME);
            pane.write("uptime\r".getBytes(StandardCharsets.UTF_8));
        });
        until(() -> loopback.typed().equals("uptime\r"));
        edt(() -> owner.closeTab(owner.currentTab()));
        until(() -> loopback.closes.get() == 1);
    }

    @Test void aDisconnectedPaneOffersReconnectAndStaleAttemptsCannotAttach() throws Exception {
        open(false);
        var first = new Loopback();
        attempts.get(0).attach(first.connection());
        until(() -> pane.view() != null);
        first.exit(3);
        until(() -> pane.noticeText().equals("Disconnected (exit 3)"));
        edt(() -> {
            assertThat(first.closes).as("closed before Reconnect is offered").hasValue(1);
            assertThat(pane.noticePrimary().getText()).isEqualTo("Reconnect");
            assertThat(pane.noticeSecondary().getText()).isEqualTo("Close");
            assertThat(pane.snapshot().state()).isEqualTo(PaneSnapshot.State.EXITED);
            assertThat(pane.snapshot().exitStatus()).hasValue(3);
            pane.noticePrimary().doClick();
            assertThat(attempts).hasSize(2);
            assertThat(pane.noticeText()).isEqualTo("Connecting…");
            assertThat(pane.snapshot().state()).isEqualTo(PaneSnapshot.State.STARTING);
        });
        var stale = new Loopback();
        attempts.get(0).attach(stale.connection());
        assertThat(stale.closes).as("a slow first attempt cannot attach into a later one").hasValue(1);
        attempts.get(1).fail("Connection refused");
        until(() -> pane.noticeText().equals("Connection refused"));
        edt(() -> {
            assertThat(pane.noticePrimary().getText()).isEqualTo("Reconnect");
            pane.noticePrimary().doClick();
        });
        var second = new Loopback();
        attempts.get(2).attach(second.connection());
        until(() -> pane.noticeText().isEmpty() && pane.snapshot().state() == PaneSnapshot.State.RUNNING);
        edt(() -> pane.write("ok".getBytes(StandardCharsets.UTF_8)));
        until(() -> second.typed().equals("ok"));
        assertThat(first.typed()).isEmpty();
    }

    @Test void cancellingTheFirstAttemptClosesThePaneAndRunsItsHandlers() throws Exception {
        open(false);
        List<String> cancelled = Collections.synchronizedList(new ArrayList<>());
        attempts.get(0).onCancelled(() -> cancelled.add("aborted"));
        edt(() -> pane.noticeSecondary().doClick());
        edt(() -> {
            assertThat(registry.pane(pane.id())).isEmpty();
            assertThat(attempts.get(0).isCancelled()).isTrue();
        });
        assertThat(cancelled).containsExactly("aborted");
        var late = new Loopback();
        attempts.get(0).attach(late.connection());
        assertThat(late.closes).hasValue(1);
    }

    @Test void aFailedFirstAttemptKeepsThePaneWithRetryAndClose() throws Exception {
        open(false);
        attempts.get(0).fail("Host key mismatch");
        until(() -> pane.noticeText().equals("Host key mismatch"));
        edt(() -> {
            assertThat(pane.noticePrimary().getText()).isEqualTo("Retry");
            assertThat(pane.snapshot().state()).isEqualTo(PaneSnapshot.State.EXITED);
            pane.noticeSecondary().doClick();
            assertThat(registry.pane(pane.id())).isEmpty();
        });
    }

    @Test void aFailedTransportSaysWhyAndClosePaneOnExitClosesIt() throws Exception {
        open(false);
        var broken = new Loopback();
        attempts.get(0).attach(broken.connection());
        until(() -> pane.view() != null);
        broken.exited.completeExceptionally(new IOException("connection reset"));
        until(() -> pane.noticeText().equals("Disconnected: The connection ended: connection reset"));
        edt(() -> assertThat(pane.snapshot().exitStatus()).isEmpty());
        closeOwners();

        open(true);
        var closing = new Loopback();
        attempts.get(attempts.size() - 1).attach(closing.connection());
        until(() -> pane.view() != null);
        closing.exit(0);
        until(() -> registry.pane(pane.id()).isEmpty());
        assertThat(closing.closes).hasValue(1);
    }

    @Test void aProviderThatThrowsFailsTheAttempt() throws Exception {
        edt(() -> {
            owner = content(launcher(new ArrayDeque<>()));
            var request = new SessionRequest("dev.example.ssh", "build-host", false, attempt -> { throw new IllegalStateException("no such host"); }, Runnable::run);
            pane = owner.openTab(HOME, request).focusedPane();
        });
        until(() -> pane.noticeText().equals("no such host"));
        assertThat(onEdt(() -> pane.noticePrimary().getText())).isEqualTo("Retry");
        assertThat(Optional.ofNullable(onEdt(() -> pane.view()))).isEmpty();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: compilation FAILS (`openTab(Path, SessionRequest)`, `noticeText` not found).

- [ ] **Step 3: `ShellLauncher.sessionDefaults`**

Add to `ShellLauncher`:

```java
    /** The grid and scrollback a session starts with when no view has measured the pane yet. */
    public record SessionDefaults(int columns, int lines, int scrollback) { }

    /** From the current launch settings; a conventional terminal when there are none. EDT, like {@link #launch}. */
    public SessionDefaults sessionDefaults() {
        try {
            LaunchSettings captured = settings == null ? null : settings.get();
            if (captured != null) return new SessionDefaults(captured.columns(), captured.lines(), captured.scrollback());
        } catch (RuntimeException unavailable) { /* fall through */ }
        return new SessionDefaults(80, 24, 10_000);
    }
```

- [ ] **Step 4: Grow `TerminalPane`**

Add imports `dev.jasper.app.terminals.SessionAttempt`, `dev.jasper.app.terminals.SessionRequest`, `dev.jasper.terminal.config.GridSize`; fields:

```java
    private final SessionRequest request;
    private SessionAttempt attempt;
    private final JLabel noticeLabel = new JLabel();
    private final JButton noticePrimary = new JButton();
    private final JButton noticeSecondary = new JButton();
    private final JPanel notice = new JPanel(new BorderLayout(12, 0));
    private Runnable primaryAction = () -> {};
    private Runnable secondaryAction = () -> {};
    private final Timer droppedTimer = new Timer(4000, event -> { if (noticeTransient) hideNotice(); });
    private boolean noticeTransient;
    /** A provided session started connecting, first or again. Delivered on the EDT. */
    Runnable onConnecting = () -> {};
```

Replace the constructor with a delegating pair:

```java
    TerminalPane(Path directory, ShellLauncher launcher) { this(directory, launcher, null); }

    /** {@code requestOrNull} makes this a pane whose session somebody else provides; it never starts a local shell. */
    TerminalPane(Path directory, ShellLauncher launcher, SessionRequest requestOrNull) {
        super(new BorderLayout());
        this.launchDirectory = directory;
        this.launcher = launcher;
        this.request = requestOrNull;
        this.shellLabel = requestOrNull == null ? launcher.label() : requestOrNull.title();
        if (requestOrNull != null) reportedTitle = requestOrNull.title();
        setPreferredSize(new Dimension(958, 821));
        add(new JLabel("Starting terminal…", SwingConstants.CENTER));
        setBackground(UIManager.getColor("Panel.background"));
        noticeLabel.putClientProperty("html.disable", Boolean.TRUE);
        var buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        buttons.setOpaque(false);
        buttons.add(noticeSecondary); buttons.add(noticePrimary);
        notice.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        notice.add(noticeLabel, BorderLayout.CENTER);
        notice.add(buttons, BorderLayout.EAST);
        noticePrimary.addActionListener(event -> primaryAction.run());
        noticeSecondary.addActionListener(event -> secondaryAction.run());
        droppedTimer.setRepeats(false);
        setActive(false);
    }
```

In the session listener add:

```java
        @Override public void inputDropped() {
            SwingUtilities.invokeLater(() -> { if (!closed && !notice.isVisible()) showDropped(); });
        }
```

Replace `start()` and extract what it did on success into `adopt`:

```java
    void start() {
        if (request != null) { connect(); return; }
        shellLabel = launcher.launch(launchDirectory, (created, failure) -> {
            if (closed) { if (created != null) created.close(); return; }
            if (failure != null) {
                LOG.log(System.Logger.Level.ERROR, "Terminal pane launch failed", failure);
                Throwable cause = failure;
                while (cause.getCause() != null) cause = cause.getCause();
                removeAll(); add(new JLabel("Could not start terminal: " + cause.getMessage()));
                revalidate(); repaint();
                onFailure.accept("Could not start terminal in " + launchDirectory + ":\n" + cause.getMessage());
                return;
            }
            adopt(created);
        });
    }

    /** Shows a running session here: the local shell, a provider's connection, or a reconnect replacing the last one. */
    private void adopt(TerminalSession created) {
        if (findBar != null) findBar.dispose();
        if (session != null) session.removeListener(listener);
        session = created;
        view = new TerminalView(session, applicationOptions());
        findBar = new FindBar(view);
        view.setOnCloseRequest(() -> onClose.run());
        view.addPropertyChangeListener("minimumSize", event -> onChanged.run());
        trackFocus(view);
        trackFocus(findBar);
        view.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) { queueUpdate(); }
        });
        session.addListener(listener);
        if (request == null) reportedTitle = session.title();
        refreshJob(); jobTimer.start();
        // Always defer: the process may already have exited before launch delivery.
        // Read the policy on the EDT after onReady has applied the latest configuration.
        created.exitFuture().whenComplete((code, error) -> SwingUtilities.invokeLater(() -> {
            if (closed || session != created) return;
            jobTimer.stop();
            onExited.accept(error == null && code != null ? java.util.OptionalInt.of(code) : java.util.OptionalInt.empty());
            if (request != null) { disconnected(created, code, error); return; }
            if (onExit == ShellExitBehavior.CLOSE
                || (onExit == ShellExitBehavior.CLOSE_ON_SUCCESS && error == null && Integer.valueOf(0).equals(code))) {
                onClose.run();
            } else {
                onChanged.run();
            }
        }));
        removeAll(); add(findBar, BorderLayout.NORTH); add(view, BorderLayout.CENTER);
        if (request != null) { add(notice, BorderLayout.SOUTH); hideNotice(); }
        onReady.accept(view); setActive(active);
        onStarted.run();
        revalidate(); repaint(); onChanged.run();
        // A shell finishing its launch must not steal focus from a newer pane or a find field.
        if (active && isShowing() && allowLaunchFocus.getAsBoolean()) focusTerminal();
    }
```

Keep every line of the old success branch that is not shown here exactly where it was relative to its neighbors; the only changes are the two guards at the top, the `request == null` condition on `reportedTitle`, the `disconnected` branch and the notice placement. Then add the session states:

```java
    /** One attempt: the first connect and every reconnect share this path. */
    private void connect() {
        if (closed) return;
        ShellLauncher.SessionDefaults defaults = launcher.sessionDefaults();
        var current = new SessionAttempt(id, defaults.columns(), defaults.lines(), request.cleanup(), SwingUtilities::invokeLater);
        attempt = current;
        current.onStatus = text -> { if (!closed && attempt == current) showPending(text); };
        current.onAttached = connection -> {
            // The pane may have closed, or moved on, between the attach and this hop to the EDT.
            if (closed || attempt != current) { connection.close().run(); return; }
            adopt(TerminalSession.attach(connection, new GridSize(defaults.columns(), defaults.lines()), defaults.scrollback()));
        };
        current.onFailed = message -> { if (!closed && attempt == current) showDisconnected(message); };
        showPending("Connecting…");
        onConnecting.run();
        onChanged.run();
        try { request.connector().accept(current); }
        catch (RuntimeException | LinkageError failure) {
            LOG.log(System.Logger.Level.WARNING, "A session provider failed to start connecting", failure);
            current.fail(failure.getMessage());
        }
    }

    private void disconnected(TerminalSession ended, Integer code, Throwable error) {
        // Reconnect is offered only after the previous connection's close has been invoked.
        ended.close();
        if (request.closeOnExit()) { onClose.run(); return; }
        Throwable cause = error;
        while (cause != null && cause.getCause() != null) cause = cause.getCause();
        showDisconnected(error == null ? "Disconnected (exit " + code + ")" : "Disconnected: " + (cause.getMessage() == null ? "the connection failed" : cause.getMessage()));
    }

    private void showPending(String text) {
        showNotice(text, null, () -> {}, "Cancel", this::cancelAttempt);
    }

    private void showDisconnected(String message) {
        showNotice(message, view == null ? "Retry" : "Reconnect", this::connect, "Close", () -> onClose.run());
        onChanged.run();
    }

    private void cancelAttempt() {
        if (attempt != null) attempt.cancel();
        // Nothing was ever shown in a pane whose first attempt is cancelled; a reconnect returns to the banner.
        if (view == null) onClose.run(); else showDisconnected("Connection cancelled");
    }

    private void showDropped() {
        showNotice("Input dropped: the remote side is not accepting input", null, () -> {}, null, () -> {});
        noticeTransient = true;
        droppedTimer.restart();
    }

    private void showNotice(String text, String primary, Runnable onPrimary, String secondary, Runnable onSecondary) {
        noticeTransient = false;
        droppedTimer.stop();
        noticeLabel.setText(text);
        noticePrimary.setVisible(primary != null); if (primary != null) noticePrimary.setText(primary);
        noticeSecondary.setVisible(secondary != null); if (secondary != null) noticeSecondary.setText(secondary);
        primaryAction = onPrimary; secondaryAction = onSecondary;
        if (view == null) { removeAll(); add(notice, BorderLayout.CENTER); }
        notice.setVisible(true);
        revalidate(); repaint();
    }

    private void hideNotice() { noticeTransient = false; droppedTimer.stop(); notice.setVisible(false); revalidate(); repaint(); }

    String noticeText() { return notice.isVisible() && notice.getParent() == this ? noticeLabel.getText() : ""; }
    JButton noticePrimary() { return noticePrimary; }
    JButton noticeSecondary() { return noticeSecondary; }
```

Replace `snapshot()`:

```java
    /** What this pane is right now, for the terminal registry. */
    PaneSnapshot snapshot() {
        Optional<String> provider = request == null ? Optional.empty() : Optional.of(request.providerId());
        boolean connecting = attempt != null && attempt.state() == SessionAttempt.State.PENDING;
        if (session == null || connecting) {
            boolean failedBeforeAnySession = request != null && !connecting;
            return new PaneSnapshot(title(), request == null ? Optional.of(launchDirectory) : Optional.empty(), 0, 0, false,
                failedBeforeAnySession ? PaneSnapshot.State.EXITED : PaneSnapshot.State.STARTING, OptionalInt.empty(), provider);
        }
        CompletableFuture<Integer> exit = session.exitFuture();
        boolean exited = exit.isDone();
        Integer code = exited && !exit.isCompletedExceptionally() ? exit.getNow(null) : null;
        return new PaneSnapshot(title(), session.workingDirectory(), session.columns(), session.rows(), session.shellIntegrationDetected(),
            exited ? PaneSnapshot.State.EXITED : PaneSnapshot.State.RUNNING, code == null ? OptionalInt.empty() : OptionalInt.of(code), provider);
    }
```

In `close()`, before `if (session != null)`: `if (attempt != null) attempt.cancel(); droppedTimer.stop();` and reset `onConnecting = () -> {};` with the other callbacks. Cancelling an attempt that already attached is a no-op, and closing the session closes its connection.

- [ ] **Step 5: Open provided sessions in tabs, splits and windows**

`TerminalTab`: add imports for `SessionRequest`; replace the constructor and `createPane`, and extend `split`:

```java
    TerminalTab(Path directory, ShellLauncher launcher) { this(directory, launcher, null); }

    TerminalTab(Path directory, ShellLauncher launcher, SessionRequest requestOrNull) {
        super(new BorderLayout());
        this.launcher = launcher;
        TerminalPane pane = createPane(directory, requestOrNull);
        tree = new SplitTree(pane.id());
        render();
    }
```

`createPane(Path directory)` becomes `createPane(Path directory, SessionRequest requestOrNull)` and builds `new TerminalPane(directory, launcher, requestOrNull)`; `split(TerminalPane, SplitTree.Axis, Path)` delegates to a four-argument form that passes the request to `createPane`. The user's own split keeps passing null: a split of a provided pane is a local shell.

`WindowContent`: `openTab(Path directory)` delegates to `TerminalTab openTab(Path directory, SessionRequest requestOrNull)`, which builds `new TerminalTab(directory, launcher, requestOrNull)`; in `connectActivity` add `pane.onConnecting = () -> report(new TerminalEvent.SessionConnecting(pane.id()));`. The first pane of a new tab starts connecting inside `tab.start()`, after `connectActivity` ran, so the event is reported.

`WindowTerminals`: replace the two `Optional.empty()` answers for sessions from Task 3:

```java
    private static Path home() { return Path.of(System.getProperty("user.home")); }

    private Optional<PaneEntry> openTab(OpenSpec spec) {
        if (closed) return Optional.empty();
        TerminalTab tab = switch (spec) {
            case OpenSpec.Local local -> owner.openTab(local.directory().orElseGet(owner::directory));
            case OpenSpec.Session session -> owner.openTab(home(), session.request());
        };
        return tab == null || tab.focusedPane() == null ? Optional.empty() : Optional.of(entry(tab, tab.focusedPane()));
    }
```

and the split lambda in `entry(tab, pane)`:

```java
            (axis, spec) -> {
                SplitTree.Axis direction = axis == SplitAxis.RIGHT ? SplitTree.Axis.RIGHT : SplitTree.Axis.DOWN;
                TerminalPane created = switch (spec) {
                    case OpenSpec.Local local -> tab.split(pane, direction, local.directory().orElse(null), null);
                    case OpenSpec.Session session -> tab.split(pane, direction, home(), session.request());
                };
                return Optional.ofNullable(created).map(split -> entry(tab, split));
            }
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.application.*' verifyTerminalArchitecture verifyApplicationArchitecture`
Expected: PASS: every existing workspace test still passes (the local path goes through `adopt` now), and `SessionPaneTest` passes three times in a row with `--rerun-tasks`.

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "feat: show provided sessions in panes with pending, disconnected and reconnect states

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: SDK session types, the cleanup worker and the application adapter

`OpenRequest` is sealed, so adding `Session` breaks the exhaustive switches in both implementations at once. This task gives the application its real adapter and the testkit a one-line bridge that Task 6 replaces.

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/terminal/{ExitPolicy,SessionSpec,PendingSession,TerminalConnection}.java`; modify `OpenRequest.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/{CleanupWorker,HostedSessions}.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/{HostedTerminals,HostedContext,PluginHost}.java`, `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeTerminals.java`
- Test: create `jasper-app/src/test/java/dev/jasper/app/plugins/HostedSessionsTest.java`, `jasper-sdk/src/test/java/dev/jasper/sdk/terminal/SessionValuesTest.java`; extend `TerminalFixture`; update `HostedTerminalsTest`, `HostedUiTest`

**Interfaces:**
- Produces (SDK):
  - `enum ExitPolicy { KEEP_OPEN, CLOSE_PANE }`
  - `record SessionSpec(String title, Optional<Icon> icon, ExitPolicy onExit, Consumer<PendingSession> connector)` with `static SessionSpec of(String title, Consumer<PendingSession> connector)` (no icon, `KEEP_OPEN`)
  - `interface PendingSession`: `PaneHandle pane()`, `int columns()`, `int rows()`, `void status(String)`, `void attach(TerminalConnection)`, `void fail(String)`, `boolean isCancelled()`, `Subscription onCancelled(Runnable)`; every method safe from any thread
  - `record TerminalConnection(InputStream output, OutputStream input, BiConsumer<Integer, Integer> resize, CompletableFuture<Integer> exited, Runnable close)` with `String TERM = "xterm-256color"`
  - `OpenRequest.Session(SessionSpec spec)`, `OpenRequest.session(SessionSpec)`; needs `session.provide`
- Produces (app):
  - `final class CleanupWorker implements Executor`: one daemon thread `jasper-plugin-cleanup`; `execute` never rejects and never lets a task's failure escape; `CompletableFuture<Void> drained()`; `void shutdown()` (later tasks each get their own daemon thread)
  - `HostedSessions(String pluginId, Containment containment, Executor cleanup, Function<UUID, PaneHandle> panes)`: `SessionRequest request(SessionSpec)`, `void cancelAll()`
  - `HostedTerminals`' constructor gains a seventh parameter `HostedSessions sessions`; new `void closeAll()` and `PaneHandle detachedPaneHandle(UUID)` (no registry lookup, so it is safe off the EDT)
  - `PluginHost` owns one `CleanupWorker` (field `cleanup`); `stop()` cancels through each context's teardown and adds `cleanup.drained()` to the futures it returns
- `TerminalFixture` session drivers: `String sessionState(UUID pane)` (`CONNECTING|<status>`, `RUNNING|`, `EXITED|<message>`, `CLOSED|` for a pane that is gone), `void cancelSession(UUID)`, `void reconnectSession(UUID)`, `void typeIntoSession(UUID, String)`, `String sessionOutput(UUID)`

- [ ] **Step 1: Write the failing tests**

`jasper-sdk/src/test/java/dev/jasper/sdk/terminal/SessionValuesTest.java`:

```java
package dev.jasper.sdk.terminal;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SessionValuesTest {
    @Test void aSessionSpecDefaultsToKeepingThePaneOpen() {
        SessionSpec spec = SessionSpec.of("build-host", pending -> { });
        assertThat(spec.onExit()).isEqualTo(ExitPolicy.KEEP_OPEN);
        assertThat(spec.icon()).isEmpty();
        assertThat(OpenRequest.session(spec)).isEqualTo(new OpenRequest.Session(spec));
        assertThatIllegalArgumentException().isThrownBy(() -> SessionSpec.of(" ", pending -> { }));
        assertThatNullPointerException().isThrownBy(() -> new SessionSpec("t", Optional.empty(), null, pending -> { }));
    }

    @Test void aConnectionNeedsEveryPart() {
        assertThat(TerminalConnection.TERM).isEqualTo("xterm-256color");
        assertThatNullPointerException().isThrownBy(() -> new TerminalConnection(InputStream.nullInputStream(), OutputStream.nullOutputStream(),
            (columns, rows) -> { }, new CompletableFuture<>(), null));
    }
}
```

Extend `TerminalFixture` with provided sessions. Add fields to its `Pane` class: `SessionRequest request; SessionAttempt attempt; AttachedConnection connection; boolean everAttached; String status = ""; String state = "";` and replace the two open lambdas' bodies so a `Session` spec creates a session pane:

```java
    private UUID addSessionPane(UUID tabId, SessionRequest request) {
        UUID id = addPane(tabId, request.title(), null);
        panes.get(id).request = request;
        connect(panes.get(id));
        return id;
    }

    /** What a pane does for a provided session, without a pane: the attempt, the attach, the exit and the close. */
    private void connect(Pane pane) {
        // Attempts are driven from any thread; the registry belongs to the EDT. Inline when already there, so EDT tests stay synchronous.
        var attempt = new SessionAttempt(pane.id, 80, 24, pane.request.cleanup(),
            task -> { if (javax.swing.SwingUtilities.isEventDispatchThread()) task.run(); else javax.swing.SwingUtilities.invokeLater(task); });
        pane.attempt = attempt;
        pane.state = "CONNECTING"; pane.status = "";
        attempt.onStatus = text -> { if (pane.attempt == attempt) pane.status = text; };
        attempt.onAttached = connection -> {
            if (pane.attempt != attempt || !panes.containsKey(pane.id)) { connection.close().run(); return; }
            pane.connection = connection; pane.everAttached = true; pane.state = "RUNNING"; pane.status = "";
            registry.publish(new TerminalEvent.SessionStarted(pane.id));
            connection.exited().whenComplete((status, error) -> javax.swing.SwingUtilities.invokeLater(() -> {
                if (pane.connection != connection) return;
                connection.close().run();
                pane.connection = null; pane.state = "EXITED";
                pane.status = error == null ? "exit " + status : String.valueOf(error.getMessage());
                registry.publish(new TerminalEvent.SessionExited(pane.id, error == null ? OptionalInt.of(status) : OptionalInt.empty()));
                if (pane.request.closeOnExit()) closePane(pane.id);
            }));
        };
        attempt.onFailed = message -> { if (pane.attempt == attempt) { pane.state = "EXITED"; pane.status = message; } };
        registry.publish(new TerminalEvent.SessionConnecting(pane.id));
        pane.request.connector().accept(attempt);
    }

    String sessionState(UUID paneId) {
        Pane pane = panes.get(paneId);
        return pane == null ? "CLOSED|" : pane.state + "|" + pane.status;
    }

    void cancelSession(UUID paneId) {
        Pane pane = panes.get(paneId);
        if (pane == null || pane.attempt == null) return;
        pane.attempt.cancel();
        // As in a real pane: with nothing ever shown there is nothing to return to.
        if (!pane.everAttached) closePane(paneId); else { pane.state = "EXITED"; pane.status = "Connection cancelled"; }
    }

    void reconnectSession(UUID paneId) {
        Pane pane = panes.get(paneId);
        if (pane != null && pane.request != null && pane.state.equals("EXITED")) connect(pane);
    }

    void typeIntoSession(UUID paneId, String text) {
        Pane pane = panes.get(paneId);
        if (pane == null || pane.connection == null) return;
        try { pane.connection.input().write(text.getBytes(StandardCharsets.UTF_8)); pane.connection.input().flush(); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    String sessionOutput(UUID paneId) {
        Pane pane = panes.get(paneId);
        if (pane == null || pane.connection == null) return "";
        try {
            var stream = pane.connection.output();
            return new String(stream.readNBytes(stream.available()), StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
```

In `closePane`, before the pane is dropped: `if (pane.attempt != null) pane.attempt.cancel(); if (pane.connection != null) { pane.connection.close().run(); pane.connection = null; }`. The open lambdas become:

```java
            spec -> switch (spec) {
                case OpenSpec.Local local -> {
                    opened.add("tab|" + window.id + "|" + local.directory().map(Path::toString).orElse("-"));
                    yield Optional.of(entry(panes.get(addPane(addTab(window.id, "opened"), "opened", local.directory().orElse(null)))));
                }
                case OpenSpec.Session session -> {
                    opened.add("session-tab|" + window.id + "|" + session.request().title());
                    yield Optional.of(entry(panes.get(addSessionPane(addTab(window.id, session.request().title()), session.request()))));
                }
            }
```

and, for a split, the same two cases with `split|<pane>|<axis>|<directory or ->` and `session-split|<pane>|<axis>|<title>`, adding the pane to the target's tab and focusing it. `entry(Pane)`'s snapshot reports `STARTING` while `state` is `CONNECTING`, `EXITED` while it is `EXITED`, and the request's provider id. Imports: `dev.jasper.app.terminals.OpenSpec`, `SessionAttempt`, `SessionRequest`, `dev.jasper.terminal.session.AttachedConnection`.

`jasper-app/src/test/java/dev/jasper/app/plugins/HostedSessionsTest.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.terminal.ExitPolicy;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PendingSession;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionSpec;
import dev.jasper.sdk.terminal.SessionState;
import dev.jasper.sdk.terminal.TerminalConnection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class HostedSessionsTest {
    private final TerminalFixture fixture = new TerminalFixture();
    private final Containment containment = new Containment(() -> true);
    private final List<Runnable> cleanup = new ArrayList<>();
    private final List<PendingSession> pendings = new ArrayList<>();
    private final ByteArrayOutputStream typed = new ByteArrayOutputStream();
    private final AtomicInteger closes = new AtomicInteger();
    private final CompletableFuture<Integer> exited = new CompletableFuture<>();
    private HostedSessions sessions;

    private HostedTerminals terminals(String... capabilities) {
        HostedTerminals[] holder = new HostedTerminals[1];
        sessions = new HostedSessions("dev.x.ssh", containment, cleanup::add, id -> holder[0].detachedPaneHandle(id));
        holder[0] = new HostedTerminals("dev.x.ssh", new CapabilityGate("dev.x.ssh", Set.of(capabilities)), fixture.registry, Runnable::run,
            () -> true, () -> true, sessions);
        return holder[0];
    }

    private TerminalConnection connection(String output) {
        return new TerminalConnection(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)), typed, (columns, rows) -> { }, exited,
            closes::incrementAndGet);
    }

    private void runCleanup() { while (!cleanup.isEmpty()) cleanup.remove(0).run(); }

    @Test void aProvidedSessionAppearsFirstThenAttachesAndCarriesBytes() {
        HostedTerminals terminals = terminals(Capabilities.SESSION_PROVIDE, Capabilities.TERMINAL_OBSERVE);
        UUID window = fixture.addWindow();
        PaneHandle pane = terminals.openTab(terminals.windowHandle(window), OpenRequest.session(SessionSpec.of("build-host", pendings::add))).orElseThrow();
        assertThat(fixture.opened()).containsExactly("session-tab|" + window + "|build-host");
        assertThat(pendings).hasSize(1);
        PendingSession pending = pendings.get(0);
        assertThat(pending.pane()).isEqualTo(pane);
        assertThat(pending.columns()).isEqualTo(80);
        assertThat(pending.rows()).isEqualTo(24);
        assertThat(pane.info().kind()).isEqualTo(SessionKind.PLUGIN);
        assertThat(pane.info().providerPluginId()).contains("dev.x.ssh");
        assertThat(pane.info().state()).isEqualTo(SessionState.CONNECTING);
        pending.status("Authenticating");
        assertThat(fixture.sessionState(pane.id())).isEqualTo("CONNECTING|Authenticating");
        pending.attach(connection("welcome"));
        assertThat(fixture.sessionState(pane.id())).isEqualTo("RUNNING|");
        assertThat(pane.info().state()).isEqualTo(SessionState.RUNNING);
        fixture.typeIntoSession(pane.id(), "uptime\r");
        assertThat(typed.toString(StandardCharsets.UTF_8)).isEqualTo("uptime\r");
        assertThat(fixture.sessionOutput(pane.id())).isEqualTo("welcome");
        fixture.closePane(pane.id());
        assertThat(closes).as("never on the caller's thread").hasValue(0);
        runCleanup();
        assertThat(closes).hasValue(1);
    }

    @Test void sessionsNeedTheirCapabilityAndAThrowingConnectorFailsTheAttempt() {
        UUID window = fixture.addWindow();
        HostedTerminals bare = terminals(Capabilities.TERMINAL_OPEN);
        assertThatThrownBy(() -> bare.openTab(bare.windowHandle(window), OpenRequest.session(SessionSpec.of("x", pendings::add))))
            .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> assertThat(failure.capability()).isEqualTo(Capabilities.SESSION_PROVIDE));
        assertThat(fixture.opened()).isEmpty();
        HostedTerminals terminals = terminals(Capabilities.SESSION_PROVIDE);
        PaneHandle pane = terminals.openTab(terminals.windowHandle(window),
            OpenRequest.session(SessionSpec.of("x", pending -> { throw new IllegalStateException("no such host"); }))).orElseThrow();
        assertThat(fixture.sessionState(pane.id())).isEqualTo("EXITED|no such host");
        assertThat(containment.failures("dev.x.ssh")).isEqualTo(1);
    }

    @Test void cancellationReconnectAndStaleAttemptsFollowTheRules() {
        HostedTerminals terminals = terminals(Capabilities.SESSION_PROVIDE);
        UUID window = fixture.addWindow();
        UUID keep = fixture.addTab(window, "local");
        fixture.addPane(keep, "zsh", null);
        PaneHandle pane = terminals.openTab(terminals.windowHandle(window), OpenRequest.session(new SessionSpec("build-host", Optional.empty(),
            ExitPolicy.KEEP_OPEN, pendings::add))).orElseThrow();
        List<String> seen = new ArrayList<>();
        var withdrawn = pendings.get(0).onCancelled(() -> seen.add("withdrawn"));
        withdrawn.close();
        withdrawn.close();
        pendings.get(0).fail("Connection refused");
        assertThat(fixture.sessionState(pane.id())).isEqualTo("EXITED|Connection refused");
        fixture.reconnectSession(pane.id());
        assertThat(pendings).as("the same connector, a fresh attempt").hasSize(2);
        pendings.get(0).attach(connection("stale"));
        runCleanup();
        assertThat(closes).as("a slow first attempt cannot attach into a later one").hasValue(1);
        assertThat(fixture.sessionState(pane.id())).isEqualTo("CONNECTING|");

        pendings.get(1).onCancelled(() -> { seen.add("aborted"); throw new IllegalStateException("handler failure"); });
        sessions.cancelAll();
        assertThat(pendings.get(1).isCancelled()).isTrue();
        assertThat(seen).as("on the cleanup worker").isEmpty();
        runCleanup();
        pendings.get(1).onCancelled(() -> seen.add("late"));
        runCleanup();
        assertThat(seen).containsExactly("aborted", "late");
        assertThat(containment.failures("dev.x.ssh")).as("a failing handler is contained").isEqualTo(1);
        pendings.get(1).attach(connection("after stop"));
        runCleanup();
        assertThat(closes).hasValue(2);
    }
}
```

In `HostedTerminalsTest` and `HostedUiTest` pass a `HostedSessions` as the new last argument of `new HostedTerminals(...)`: `new HostedSessions("dev.x.tool", new Containment(() -> true), Runnable::run, id -> null)`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk:compileTestJava :jasper-app:compileTestJava`
Expected: compilation FAILS (`SessionSpec`, `HostedSessions` not found).

- [ ] **Step 3: Write the SDK types**

`terminal/ExitPolicy.java`:

```java
package dev.jasper.sdk.terminal;

/** What happens to a provided session's pane when the session ends. */
public enum ExitPolicy {
    /** The pane stays, shows how the session ended, and offers Reconnect. What plugins normally choose. */
    KEEP_OPEN,
    /** The pane closes once the final output has been shown. */
    CLOSE_PANE
}
```

`terminal/TerminalConnection.java`:

```java
package dev.jasper.sdk.terminal;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * A connected session, handed to {@link PendingSession#attach}. From that call on Jasper owns it: it invokes
 * {@code close} exactly once, whatever happens, so release everything that belongs to the session there.
 * None of these members is ever called on the event thread: {@code output} is read on the session's reader
 * thread, {@code input} and {@code resize} are used only on Jasper's writer thread for this session, and
 * {@code close} runs on Jasper's cleanup thread. They must not touch Swing or event-thread-only SDK methods.
 *
 * @param output remote to terminal, UTF-8; end of stream after the remote side ends. A read may block; {@code close} must unblock it
 * @param input terminal to remote; Jasper flushes after every write and never blocks the user on it
 * @param resize columns and rows of the pane, to pass to the remote pty; request {@link #TERM} there
 * @param exited the exit status; completing it exceptionally means the connection failed, and the message is shown
 * @param close idempotent; unblocks a pending read; returns promptly
 */
public record TerminalConnection(InputStream output, OutputStream input, BiConsumer<Integer, Integer> resize,
                                 CompletableFuture<Integer> exited, Runnable close) {
    /** The terminal type to request on the remote pty. */
    public static final String TERM = "xterm-256color";

    /** Rejects nulls. */
    public TerminalConnection {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(resize, "resize");
        Objects.requireNonNull(exited, "exited");
        Objects.requireNonNull(close, "close");
    }
}
```

`terminal/PendingSession.java`:

```java
package dev.jasper.sdk.terminal;

import dev.jasper.sdk.Subscription;

/**
 * One attempt to connect a pane: the first connect, or one Reconnect. Exactly one of {@link #attach},
 * {@link #fail} and cancellation takes effect; the first wins and later calls are ignored. Every method is safe
 * from any thread, also after the plugin stopped.
 */
public interface PendingSession {
    /**
     * The pane that is waiting.
     *
     * @return its handle
     */
    PaneHandle pane();

    /**
     * The width to request for the remote pty. The pane resizes it once it has been laid out.
     *
     * @return columns
     */
    int columns();

    /**
     * The height to request for the remote pty.
     *
     * @return rows
     */
    int rows();

    /**
     * Shows progress in the waiting pane, for example "Authenticating".
     *
     * @param text one line
     */
    void status(String text);

    /**
     * Hands over a connected session. Ownership transfers at this call, whatever the outcome: when the attempt
     * was cancelled or already finished, the connection is never read and its {@code close} is invoked at once.
     *
     * @param connection the connected session
     */
    void attach(TerminalConnection connection);

    /**
     * Ends the attempt unsuccessfully; the pane shows the message and offers to try again.
     *
     * @param message why, for the user
     */
    void fail(String message);

    /**
     * Whether the user pressed Cancel, the pane closed, the plugin stopped or Jasper is quitting.
     *
     * @return true once cancelled
     */
    boolean isCancelled();

    /**
     * Registers work that aborts a blocking connect. It runs at most once, on Jasper's cleanup thread, and
     * at once when the attempt is already cancelled.
     *
     * @param handler what to run
     * @return closes the registration
     */
    Subscription onCancelled(Runnable handler);
}
```

`terminal/SessionSpec.java`:

```java
package dev.jasper.sdk.terminal;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.Icon;

/**
 * A session your plugin provides. The pane appears first, waiting; {@code connector} is then called on the
 * event thread, once for the first connect and once for every Reconnect, each time with a fresh
 * {@link PendingSession}. It must not block: start the connection on {@code context.background()}.
 *
 * @param title the pane's title until the remote program sets one
 * @param icon reserved for a tab icon
 * @param onExit what happens to the pane when the session ends
 * @param connector starts one connection attempt
 */
public record SessionSpec(String title, Optional<Icon> icon, ExitPolicy onExit, Consumer<PendingSession> connector) {
    /** Rejects nulls and a blank title. */
    public SessionSpec {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(onExit, "onExit");
        Objects.requireNonNull(connector, "connector");
        if (title.isBlank()) throw new IllegalArgumentException("A session needs a title");
    }

    /**
     * A session whose pane stays open when it ends.
     *
     * @param title the pane's title until the remote program sets one
     * @param connector starts one connection attempt
     * @return the spec
     */
    public static SessionSpec of(String title, Consumer<PendingSession> connector) {
        return new SessionSpec(title, Optional.empty(), ExitPolicy.KEEP_OPEN, connector);
    }
}
```

In `OpenRequest`: change the header to `public sealed interface OpenRequest permits OpenRequest.Local, OpenRequest.Session`, replace its class Javadoc with "What to run in a new tab or split.", and add:

```java
    /**
     * A session the plugin provides. Needs {@code session.provide}.
     *
     * @param spec how to connect it
     */
    record Session(SessionSpec spec) implements OpenRequest {
        /** Rejects null. */
        public Session { Objects.requireNonNull(spec, "spec"); }
    }

    /**
     * A session the plugin provides.
     *
     * @param spec how to connect it
     * @return the request
     */
    static OpenRequest session(SessionSpec spec) { return new Session(spec); }
```

- [ ] **Step 4: Write the cleanup worker and the adapter**

`jasper-app/src/main/java/dev/jasper/app/plugins/CleanupWorker.java`:

```java
package dev.jasper.app.plugins;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * The application-owned thread for plugin code that must run when nobody else can run it: cancellation handlers,
 * which usually abort blocking network work, and the closes of connections. It is not a plugin's background
 * executor, because that stops admitting tasks during shutdown while a connect may still be running, and never
 * the EDT. It never rejects: after shutdown each task gets a daemon thread of its own.
 */
final class CleanupWorker implements Executor {
    private static final System.Logger LOG = System.getLogger(CleanupWorker.class.getName());
    private final ExecutorService worker =
        Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("jasper-plugin-cleanup").factory());

    @Override public void execute(Runnable task) {
        Runnable safe = () -> {
            try { task.run(); }
            catch (RuntimeException | LinkageError failure) { LOG.log(System.Logger.Level.WARNING, "A cleanup task failed", failure); }
        };
        try { worker.execute(safe); }
        catch (RejectedExecutionException afterShutdown) { Thread.ofPlatform().daemon().name("jasper-plugin-cleanup-late").start(safe); }
    }

    /** Completes when everything queued before this call has run; joins the bounded shutdown wait. */
    CompletableFuture<Void> drained() {
        var done = new CompletableFuture<Void>();
        execute(() -> done.complete(null));
        return done;
    }

    void shutdown() { worker.shutdown(); }
}
```

`jasper-app/src/main/java/dev/jasper/app/plugins/HostedSessions.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.terminals.SessionAttempt;
import dev.jasper.app.terminals.SessionRequest;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.ExitPolicy;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PendingSession;
import dev.jasper.sdk.terminal.SessionSpec;
import dev.jasper.sdk.terminal.TerminalConnection;
import dev.jasper.terminal.session.AttachedConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * One plugin's provided sessions. It turns a {@link SessionSpec} into the app-native request a pane runs, keeps
 * the attempts that are still pending so stopping the plugin cancels them, and contains every call into the
 * plugin: the connector on the EDT, cancellation handlers and closes on the cleanup worker.
 */
final class HostedSessions {
    private final String pluginId;
    private final Containment containment;
    private final Executor cleanup;
    private final Function<UUID, PaneHandle> panes;
    private final List<SessionAttempt> pending = new ArrayList<>();

    HostedSessions(String pluginId, Containment containment, Executor cleanup, Function<UUID, PaneHandle> panes) {
        this.pluginId = pluginId; this.containment = containment; this.cleanup = cleanup; this.panes = panes;
    }

    SessionRequest request(SessionSpec spec) {
        Objects.requireNonNull(spec, "spec");
        Executor contained = task -> cleanup.execute(() -> containment.run(pluginId, "session cleanup", task));
        return new SessionRequest(pluginId, spec.title(), spec.onExit() == ExitPolicy.CLOSE_PANE, attempt -> {
            track(attempt);
            Throwable failure = containment.attempt(pluginId, "session connector", () -> { spec.connector().accept(new Pending(attempt)); return null; });
            if (failure != null) attempt.fail(failure.getMessage() == null ? failure.toString() : failure.getMessage());
        }, contained);
    }

    private void track(SessionAttempt attempt) {
        synchronized (pending) {
            pending.removeIf(earlier -> earlier.state() != SessionAttempt.State.PENDING);
            pending.add(attempt);
        }
    }

    /** The plugin is stopping: whatever is still connecting is cancelled. Attached sessions belong to their panes. */
    void cancelAll() {
        List<SessionAttempt> copy;
        synchronized (pending) { copy = new ArrayList<>(pending); pending.clear(); }
        copy.forEach(SessionAttempt::cancel);
    }

    private final class Pending implements PendingSession {
        private final SessionAttempt attempt;
        Pending(SessionAttempt attempt) { this.attempt = attempt; }
        @Override public PaneHandle pane() { return panes.apply(attempt.paneId()); }
        @Override public int columns() { return attempt.columns(); }
        @Override public int rows() { return attempt.rows(); }
        @Override public void status(String text) { attempt.status(text); }
        @Override public void attach(TerminalConnection connection) {
            Objects.requireNonNull(connection, "connection");
            attempt.attach(new AttachedConnection(connection.output(), connection.input(), connection.resize(), connection.exited(), connection.close()));
        }
        @Override public void fail(String message) { attempt.fail(message); }
        @Override public boolean isCancelled() { return attempt.isCancelled(); }
        @Override public Subscription onCancelled(Runnable handler) {
            var registration = attempt.onCancelled(handler);
            var closed = new AtomicBoolean();
            return () -> { if (closed.compareAndSet(false, true)) registration.close(); };
        }
    }
}
```

`SessionAttempt.onCancelled`'s subscription only removes a handler from a list, so closing it from any thread is safe.

In `HostedTerminals`: add the constructor parameter and field `HostedSessions sessions` (last); replace `local(OpenRequest)` with a method that maps either request and names its capability, and use it in `openTab` and `split`:

```java
    /** Every request kind names its capability here, before anything else happens. */
    private OpenSpec spec(OpenRequest request) {
        return switch (Objects.requireNonNull(request, "request")) {
            case OpenRequest.Local local -> { gate.require(Capabilities.TERMINAL_OPEN); yield new OpenSpec.Local(local.spec().workingDirectory()); }
            case OpenRequest.Session session -> { gate.require(Capabilities.SESSION_PROVIDE); yield new OpenSpec.Session(sessions.request(session.spec())); }
        };
    }
```

`openTab` and `split` call `spec(request)` first, then `requireUi`, then audit with `request instanceof OpenRequest.Session ? Capabilities.SESSION_PROVIDE : Capabilities.TERMINAL_OPEN`, then pass the spec to the entry. Add:

```java
    /** A handle that finds its tab lazily, for callers that are not on the UI thread. */
    PaneHandle detachedPaneHandle(UUID id) { return new Pane(id, NOWHERE); }

    /** The plugin is stopping. */
    void closeAll() { sessions.cancelAll(); }
```

In `PluginHost` add `final CleanupWorker cleanup = new CleanupWorker();` and, at the end of `stop()` before `return pending;`, `pending.add(cleanup.drained());`. In `HostedContext` build the sessions before the terminals:

```java
        HostedTerminals[] self = new HostedTerminals[1];
        var sessions = new HostedSessions(id, host.containment, host.cleanup, paneId -> self[0].detachedPaneHandle(paneId));
        this.terminals = new HostedTerminals(id, gate, host.environment.terminals(), host.environment.ui(), host.environment.onUi(),
            () -> state != State.CLOSED, sessions);
        self[0] = terminals;
```

and call `terminals.closeAll();` in `teardown` next to `ui.closeAll();`. A failed start therefore cancels what the plugin began connecting, like everything else it registered.

In `FakeTerminals`, bridge the sealed switch until Task 6: in `local(OpenRequest)` add `case OpenRequest.Session session -> throw new UnsupportedOperationException("provided sessions arrive with the next commit");`.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-sdk:check :jasper-sdk-testkit:check :jasper-app:test --tests 'dev.jasper.app.plugins.*' verifySdkArchitecture verifyApplicationArchitecture verifyTerminalArchitecture :jasper-app:javadoc`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add jasper-sdk jasper-sdk-testkit jasper-app
git commit -m "feat: let plugins provide sessions through gated open requests and a cleanup worker

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: Provided sessions in the testkit

**Files:**
- Create: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeSessions.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/{FakeWorkspace,FakeTerminals,FakePluginHost,FakePluginContext}.java`
- Test: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeSessionsTest.java`

**Interfaces:**
- Produces `FakePluginHost` drivers with the fixture's formats: `String sessionState(UUID paneId)`, `void cancelSession(UUID paneId)`, `void reconnectSession(UUID paneId)`, `void typeIntoSession(UUID paneId, String text)`, `String sessionOutput(UUID paneId)`, `void pumpSessions()` (notices exits of attached sessions; `flush()` calls it)
- The fake is single-threaded by design: cancellation handlers and closes run inline where the application would use its cleanup thread. Everything else follows the application: first outcome wins, a connection offered to a finished attempt is closed at once, a closed pane closes its connection, exactly once in every case, and stopping a plugin cancels its pending attempts.

- [ ] **Step 1: Write the failing test**

`jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeSessionsTest.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PendingSession;
import dev.jasper.sdk.terminal.SessionSpec;
import dev.jasper.sdk.terminal.TerminalConnection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FakeSessionsTest {
    @Test void aPluginProvidesASessionAndTheTestDrivesIt() {
        try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow();
            List<PendingSession> pendings = new ArrayList<>();
            var typed = new ByteArrayOutputStream();
            var closes = new AtomicInteger();
            var exited = new CompletableFuture<Integer>();
            PluginContext context = host.start(new PluginInfo("dev.x.ssh", "SSH", "1.0.0", Set.of(Capabilities.SESSION_PROVIDE)), Set.of(), Set.of(), plugin -> { });
            PaneHandle pane = context.terminals().openTab(context.terminals().window(window).orElseThrow(),
                OpenRequest.session(SessionSpec.of("build-host", pendings::add))).orElseThrow();
            assertThat(host.sessionState(pane.id())).isEqualTo("CONNECTING|");
            pendings.get(0).status("Authenticating");
            assertThat(host.sessionState(pane.id())).isEqualTo("CONNECTING|Authenticating");
            pendings.get(0).attach(new TerminalConnection(new ByteArrayInputStream("welcome".getBytes(StandardCharsets.UTF_8)), typed,
                (columns, rows) -> { }, exited, closes::incrementAndGet));
            assertThat(host.sessionState(pane.id())).isEqualTo("RUNNING|");
            host.typeIntoSession(pane.id(), "uptime\r");
            assertThat(typed.toString(StandardCharsets.UTF_8)).isEqualTo("uptime\r");
            assertThat(host.sessionOutput(pane.id())).isEqualTo("welcome");
            exited.complete(3);
            host.flush();
            assertThat(host.sessionState(pane.id())).isEqualTo("EXITED|exit 3");
            assertThat(closes).hasValue(1);
            host.reconnectSession(pane.id());
            assertThat(pendings).hasSize(2);
            host.cancelSession(pane.id());
            assertThat(pendings.get(1).isCancelled()).isTrue();
            assertThat(host.sessionState(pane.id())).isEqualTo("EXITED|Connection cancelled");
            assertThat(host.failures()).isEmpty();
        }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-sdk-testkit:compileTestJava`
Expected: compilation FAILS (`sessionState` not found).

- [ ] **Step 3: Implement**

In `FakeWorkspace.Pane` add fields `FakeSessions.Session session;`. `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeSessions.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.ExitPolicy;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PendingSession;
import dev.jasper.sdk.terminal.SessionSpec;
import dev.jasper.sdk.terminal.SessionState;
import dev.jasper.sdk.terminal.TerminalConnection;
import dev.jasper.sdk.terminal.TerminalEvents;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provided sessions with the application's rules and none of its threads: cancellation handlers and closes run
 * inline where the application uses its cleanup thread, and the test decides when an exit is noticed.
 */
final class FakeSessions {
    enum Outcome { PENDING, ATTACHED, FAILED, CANCELLED }

    /** What one pane knows about its provided session. */
    static final class Session {
        final FakePluginContext owner; final SessionSpec spec;
        Attempt attempt; TerminalConnection connection; boolean everAttached; String state = "CONNECTING"; String status = "";
        Session(FakePluginContext owner, SessionSpec spec) { this.owner = owner; this.spec = spec; }
    }

    final class Attempt implements PendingSession {
        final FakeWorkspace.Pane pane;
        final AtomicReference<Outcome> outcome = new AtomicReference<>(Outcome.PENDING);
        final List<Runnable> handlers = new ArrayList<>();
        Attempt(FakeWorkspace.Pane pane) { this.pane = pane; }

        @Override public PaneHandle pane() { return pane.session.owner.terminals.paneHandle(pane.id); }
        @Override public int columns() { return 80; }
        @Override public int rows() { return 24; }
        @Override public void status(String text) {
            Objects.requireNonNull(text, "text");
            if (outcome.get() == Outcome.PENDING && pane.session.attempt == this) pane.session.status = text;
        }
        @Override public void attach(TerminalConnection connection) {
            Objects.requireNonNull(connection, "connection");
            if (!outcome.compareAndSet(Outcome.PENDING, Outcome.ATTACHED) || pane.session.attempt != this || !pane.open) { close(connection); return; }
            pane.session.connection = connection; pane.session.everAttached = true; pane.session.state = "RUNNING"; pane.session.status = "";
            pane.info = FakeTerminals.withState(pane.info, SessionState.RUNNING, OptionalInt.empty());
            host.publishApp(TerminalEvents.SESSION_STATE_CHANGED, new TerminalEvents.SessionStateChanged(pane.id, SessionState.RUNNING, OptionalInt.empty()));
        }
        @Override public void fail(String message) {
            if (!outcome.compareAndSet(Outcome.PENDING, Outcome.FAILED) || pane.session.attempt != this) return;
            ended(pane, message == null || message.isBlank() ? "The connection failed" : message, OptionalInt.empty());
        }
        @Override public boolean isCancelled() { return outcome.get() == Outcome.CANCELLED; }
        @Override public Subscription onCancelled(Runnable handler) {
            Objects.requireNonNull(handler, "handler");
            synchronized (handlers) {
                if (outcome.get() == Outcome.PENDING) {
                    handlers.add(handler);
                    var closed = new AtomicBoolean();
                    return () -> { if (closed.compareAndSet(false, true)) synchronized (handlers) { handlers.remove(handler); } };
                }
            }
            if (outcome.get() == Outcome.CANCELLED) contained(pane.session.owner, "session cancellation", handler);
            return () -> { };
        }
        void cancel() {
            List<Runnable> copy;
            synchronized (handlers) {
                if (!outcome.compareAndSet(Outcome.PENDING, Outcome.CANCELLED)) return;
                copy = new ArrayList<>(handlers);
                handlers.clear();
            }
            copy.forEach(handler -> contained(pane.session.owner, "session cancellation", handler));
        }
    }

    private final FakePluginHost host;
    private final FakeWorkspace workspace;

    FakeSessions(FakePluginHost host, FakeWorkspace workspace) { this.host = host; this.workspace = workspace; }

    private void contained(FakePluginContext owner, String what, Runnable action) {
        try { action.run(); }
        catch (RuntimeException | LinkageError failure) { host.recordFailure(owner.plugin().id() + " " + what + ": " + failure); }
    }

    /** Ownership transferred to the host at attach: the original close runs exactly once. */
    private void close(TerminalConnection connection) {
        try { connection.close().run(); }
        catch (RuntimeException | LinkageError failure) { host.recordFailure("session close: " + failure); }
    }

    private void ended(FakeWorkspace.Pane pane, String status, OptionalInt exitStatus) {
        pane.session.state = "EXITED"; pane.session.status = status;
        pane.info = FakeTerminals.withState(pane.info, SessionState.EXITED, exitStatus);
        host.publishApp(TerminalEvents.SESSION_STATE_CHANGED, new TerminalEvents.SessionStateChanged(pane.id, SessionState.EXITED, exitStatus));
    }

    void connect(FakePluginContext owner, FakeWorkspace.Pane pane, SessionSpec spec) {
        if (pane.session == null) pane.session = new Session(owner, spec);
        var attempt = new Attempt(pane);
        pane.session.attempt = attempt;
        pane.session.state = "CONNECTING"; pane.session.status = "";
        pane.info = FakeTerminals.withState(pane.info, SessionState.CONNECTING, OptionalInt.empty());
        host.publishApp(TerminalEvents.SESSION_STATE_CHANGED, new TerminalEvents.SessionStateChanged(pane.id, SessionState.CONNECTING, OptionalInt.empty()));
        try { spec.connector().accept(attempt); }
        catch (RuntimeException | LinkageError failure) {
            host.recordFailure(owner.plugin().id() + " session connector: " + failure);
            attempt.fail(failure.getMessage() == null ? failure.toString() : failure.getMessage());
        }
    }

    String state(FakeWorkspace.Pane paneOrNull) {
        if (paneOrNull == null || !paneOrNull.open) return "CLOSED|";
        return paneOrNull.session == null ? "LOCAL|" : paneOrNull.session.state + "|" + paneOrNull.session.status;
    }

    void cancel(FakeWorkspace.Pane pane) {
        if (pane.session == null || pane.session.attempt == null) return;
        pane.session.attempt.cancel();
        // As in a real pane: with nothing ever shown there is nothing to return to.
        if (!pane.session.everAttached) workspace.close(pane);
        else ended(pane, "Connection cancelled", OptionalInt.empty());
    }

    void reconnect(FakeWorkspace.Pane pane) {
        if (pane.session != null && pane.session.state.equals("EXITED")) connect(pane.session.owner, pane, pane.session.spec);
    }

    /** The pane is closing: a pending attempt is cancelled and an attached connection is closed. */
    void closing(FakeWorkspace.Pane pane) {
        if (pane.session == null) return;
        if (pane.session.attempt != null) pane.session.attempt.cancel();
        if (pane.session.connection != null) { close(pane.session.connection); pane.session.connection = null; }
    }

    /** The plugin is stopping: whatever it is still connecting is cancelled. */
    void stopping(FakePluginContext owner) {
        for (FakeWorkspace.Pane pane : workspace.everyOpenPane())
            if (pane.session != null && pane.session.owner == owner && pane.session.attempt != null) pane.session.attempt.cancel();
    }

    void type(FakeWorkspace.Pane pane, String text) {
        if (pane.session == null || pane.session.connection == null) return;
        try { pane.session.connection.input().write(text.getBytes(StandardCharsets.UTF_8)); pane.session.connection.input().flush(); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    String output(FakeWorkspace.Pane pane) {
        if (pane.session == null || pane.session.connection == null) return "";
        try {
            var stream = pane.session.connection.output();
            return new String(stream.readNBytes(stream.available()), StandardCharsets.UTF_8);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    /** Notices attached sessions whose {@code exited} completed: closes them, reports the exit, applies the exit policy. */
    void pump() {
        for (FakeWorkspace.Pane pane : workspace.everyOpenPane()) {
            if (pane.session == null || pane.session.connection == null || !pane.session.connection.exited().isDone()) continue;
            TerminalConnection connection = pane.session.connection;
            pane.session.connection = null;
            close(connection);
            Integer status = connection.exited().isCompletedExceptionally() ? null : connection.exited().getNow(null);
            String message = status != null ? "exit " + status
                : String.valueOf(connection.exited().handle((value, error) -> error == null ? "unknown" : String.valueOf(error.getMessage())).join());
            ended(pane, message, status == null ? OptionalInt.empty() : OptionalInt.of(status));
            if (pane.session.spec.onExit() == ExitPolicy.CLOSE_PANE) workspace.close(pane);
        }
    }
}
```

Supporting changes:
- `FakeWorkspace`: add `final FakeSessions sessions;` created in the constructor (`sessions = new FakeSessions(host, this);`); `List<Pane> everyOpenPane()` returning the open panes; and first thing in `close(Pane)` after the `open` check, `sessions.closing(pane);`.
- `FakeTerminals`: a static helper `static PaneInfo withState(PaneInfo info, SessionState state, OptionalInt exitStatus)` that copies every other component; replace the Task 5 bridge with a `spec(...)`-style method like the application's: `Local` needs `terminal.open`, `Session` needs `session.provide`. For a `Session`, `openTab` records `session-tab|<window id>|<title>`, adds a tab titled like the session and a pane whose `PaneInfo` has the title, no directory, `SessionKind.PLUGIN`, the plugin's id as provider and `CONNECTING`, then calls `workspace.sessions.connect(context, pane, spec)`; `split` does the same in the target's tab with `session-split|<pane id>|<direction>|<title>` and focuses the new pane.
- `FakePluginHost`: the six drivers, documented, delegating to `workspace.sessions` (`sessionState` looks the pane up with `workspace.anyPane`); call `workspace.sessions.pump()` at the start of `flush()`; and where a context is stopped or closed, call `workspace.sessions.stopping(context)` before its UI contributions are cleared.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-sdk-testkit:check verifySdkArchitecture`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-sdk-testkit
git commit -m "feat: fake provided sessions in the testkit with the application's ownership rules

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: The contract for provided sessions

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`; `plugins/sample/src/main/resources/plugin.toml`
- Modify: `jasper-sdk-testkit/.../contract/{ContractHarness,PluginContractTest}.java`, `.../FakeContractTest.java`; `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`

**Interfaces:**
- Produces: `JasperSdk.VERSION` = `"0.5.0"`; sample `sdk = ">=0.5, <0.6"`; `ContractHarness` additions `String sessionState(UUID paneId)`, `void cancelSession(UUID paneId)`, `void reconnectSession(UUID paneId)`, `void typeIntoSession(UUID paneId, String text)`, `String sessionOutput(UUID paneId)`. `flush()` also waits until closes and cancellation handlers have run and exits have been noticed.

- [ ] **Step 1: Grow the harness and write the failing contract cases**

Add the five methods to `ContractHarness` with the state format from Task 5, and append to `PluginContractTest` (imports `dev.jasper.sdk.terminal.ExitPolicy`, `PendingSession`, `SessionKind`, `SessionSpec`, `TerminalConnection`, `java.io.ByteArrayInputStream`, `java.io.ByteArrayOutputStream`, `java.util.concurrent.CompletableFuture`, `java.util.concurrent.atomic.AtomicInteger`):

```java
    /** A connection the test can watch: what was typed, how often it was closed, and when it exits. */
    private static final class Probe {
        final ByteArrayOutputStream typed = new ByteArrayOutputStream();
        final AtomicInteger closes = new AtomicInteger();
        final CompletableFuture<Integer> exited = new CompletableFuture<>();
        TerminalConnection connection(String output) {
            return new TerminalConnection(new ByteArrayInputStream(output.getBytes(java.nio.charset.StandardCharsets.UTF_8)), typed,
                (columns, rows) -> { }, exited, closes::incrementAndGet);
        }
    }

    private PaneHandle openSession(AtomicReference<PluginContext> context, UUID window, SessionSpec spec) {
        var pane = new AtomicReference<PaneHandle>();
        h.ui(() -> pane.set(context.get().terminals().openTab(context.get().terminals().window(window).orElseThrow(), OpenRequest.session(spec)).orElseThrow()));
        return pane.get();
    }

    @Test void aProvidedSessionAppearsFirstThenAttachesAndCarriesBytesBothWays() {
        UUID window = h.addTerminalWindow();
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.SESSION_PROVIDE, Capabilities.TERMINAL_OBSERVE), Set.of(), Set.of(), alpha::set);
        List<PendingSession> pendings = Collections.synchronizedList(new ArrayList<>());
        PaneHandle pane = openSession(alpha, window, SessionSpec.of("build-host", pendings::add));
        assertThat(pendings).as("the connector ran, on the event thread, before openTab returned").hasSize(1);
        assertThat(h.sessionState(pane.id())).isEqualTo("CONNECTING|");
        assertThat(pendings.get(0).pane()).isEqualTo(pane);
        assertThat(pendings.get(0).columns()).isPositive();
        h.ui(() -> {
            assertThat(pane.info().kind()).isEqualTo(SessionKind.PLUGIN);
            assertThat(pane.info().providerPluginId()).contains("test.alpha");
            assertThat(pane.info().state()).isEqualTo(SessionState.CONNECTING);
        });
        pendings.get(0).status("Authenticating");
        h.flush();
        assertThat(h.sessionState(pane.id())).isEqualTo("CONNECTING|Authenticating");
        var probe = new Probe();
        pendings.get(0).attach(probe.connection("welcome"));
        h.flush();
        assertThat(h.sessionState(pane.id())).isEqualTo("RUNNING|");
        h.typeIntoSession(pane.id(), "uptime\r");
        assertThat(probe.typed.toString(java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("uptime\r");
        assertThat(h.sessionOutput(pane.id())).isEqualTo("welcome");
        probe.exited.complete(3);
        h.flush();
        assertThat(h.sessionState(pane.id())).isEqualTo("EXITED|exit 3");
        assertThat(probe.closes).hasValue(1);
    }

    @Test void ownershipTransfersAtAttachAndEveryConnectionIsClosedExactlyOnce() {
        UUID window = h.addTerminalWindow(), keep = h.addTerminalTab(window, "local");
        h.addTerminalPane(keep, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.SESSION_PROVIDE), Set.of(), Set.of(), alpha::set);
        List<PendingSession> pendings = Collections.synchronizedList(new ArrayList<>());
        PaneHandle pane = openSession(alpha, window, SessionSpec.of("build-host", pendings::add));
        var first = new Probe(); var second = new Probe(); var late = new Probe();
        pendings.get(0).attach(first.connection(""));
        pendings.get(0).attach(second.connection(""));
        pendings.get(0).fail("ignored after attach");
        h.flush();
        assertThat(h.sessionState(pane.id())).isEqualTo("RUNNING|");
        assertThat(second.closes).as("offered to an attempt that already attached").hasValue(1);
        assertThat(first.closes).hasValue(0);
        h.closeTerminalPane(pane.id());
        h.flush();
        assertThat(first.closes).as("the pane closed").hasValue(1);
        pendings.get(0).attach(late.connection(""));
        h.flush();
        assertThat(late.closes).hasValue(1);
        assertThat(h.sessionState(pane.id())).isEqualTo("CLOSED|");
    }

    @Test void failureCancellationAndReconnectFollowTheAttemptRules() {
        UUID window = h.addTerminalWindow(), keep = h.addTerminalTab(window, "local");
        h.addTerminalPane(keep, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.SESSION_PROVIDE), Set.of(), Set.of(), alpha::set);
        List<PendingSession> pendings = Collections.synchronizedList(new ArrayList<>());
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        PaneHandle pane = openSession(alpha, window, new SessionSpec("build-host", Optional.empty(), ExitPolicy.KEEP_OPEN, pendings::add));
        pendings.get(0).fail("Connection refused");
        h.flush();
        assertThat(h.sessionState(pane.id())).isEqualTo("EXITED|Connection refused");
        h.reconnectSession(pane.id());
        assertThat(pendings).as("the same connector, a fresh attempt").hasSize(2);
        var stale = new Probe();
        pendings.get(0).attach(stale.connection(""));
        h.flush();
        assertThat(stale.closes).as("a finished attempt cannot attach into a later one").hasValue(1);
        assertThat(h.sessionState(pane.id())).isEqualTo("CONNECTING|");

        var live = new Probe();
        pendings.get(1).attach(live.connection(""));
        h.flush();
        live.exited.complete(0);
        h.flush();
        assertThat(h.sessionState(pane.id())).isEqualTo("EXITED|exit 0");
        assertThat(live.closes).as("closed before a reconnect is possible").hasValue(1);
        h.reconnectSession(pane.id());
        assertThat(pendings).hasSize(3);
        pendings.get(2).onCancelled(() -> seen.add("aborted"));
        var withdrawn = pendings.get(2).onCancelled(() -> seen.add("withdrawn"));
        withdrawn.close();
        h.cancelSession(pane.id());
        h.flush();
        assertThat(pendings.get(2).isCancelled()).isTrue();
        assertThat(h.sessionState(pane.id())).as("a cancelled reconnect returns to the disconnected pane").isEqualTo("EXITED|Connection cancelled");
        pendings.get(2).onCancelled(() -> seen.add("late"));
        h.flush();
        assertThat(seen).containsExactly("aborted", "late");
    }

    @Test void cancellingAnAttemptThatNeverShowedAnythingClosesThePane() {
        UUID window = h.addTerminalWindow(), keep = h.addTerminalTab(window, "local");
        h.addTerminalPane(keep, "zsh", Path.of("/"));
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.SESSION_PROVIDE), Set.of(), Set.of(), alpha::set);
        List<PendingSession> pendings = Collections.synchronizedList(new ArrayList<>());
        PaneHandle pane = openSession(alpha, window, SessionSpec.of("build-host", pendings::add));
        h.cancelSession(pane.id());
        h.flush();
        assertThat(pendings.get(0).isCancelled()).isTrue();
        assertThat(h.sessionState(pane.id())).isEqualTo("CLOSED|");
        h.ui(() -> assertThat(pane.isOpen()).isFalse());
    }

    @Test void providingSessionsNeedsTheCapabilityAndAThrowingConnectorFailsTheAttempt() {
        UUID window = h.addTerminalWindow(), keep = h.addTerminalTab(window, "local");
        h.addTerminalPane(keep, "zsh", Path.of("/"));
        var bare = new AtomicReference<PluginContext>();
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.bare", Capabilities.TERMINAL_OPEN), Set.of(), Set.of(), bare::set);
        h.start(info("test.alpha", Capabilities.SESSION_PROVIDE), Set.of(), Set.of(), alpha::set);
        h.ui(() -> assertThatThrownBy(() -> bare.get().terminals().openTab(bare.get().terminals().window(window).orElseThrow(),
            OpenRequest.session(SessionSpec.of("x", pending -> { })))).isInstanceOfSatisfying(MissingCapabilityException.class,
                failure -> assertThat(failure.capability()).isEqualTo(Capabilities.SESSION_PROVIDE)));
        assertThat(h.openRequests()).isEmpty();
        PaneHandle pane = openSession(alpha, window, SessionSpec.of("x", pending -> { throw new IllegalStateException("no such host"); }));
        h.flush();
        assertThat(h.sessionState(pane.id())).isEqualTo("EXITED|no such host");
        assertThat(h.active("test.alpha")).as("a failing connector is contained").isTrue();
    }

    @Test void stoppingAPluginCancelsWhatItIsStillConnecting() {
        UUID window = h.addTerminalWindow();
        var alpha = new AtomicReference<PluginContext>();
        h.start(info("test.alpha", Capabilities.SESSION_PROVIDE), Set.of(), Set.of(), alpha::set);
        List<PendingSession> pendings = Collections.synchronizedList(new ArrayList<>());
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        openSession(alpha, window, SessionSpec.of("build-host", pending -> { pendings.add(pending); pending.onCancelled(() -> seen.add("aborted")); }));
        h.stopAll();
        h.flush();
        assertThat(pendings.get(0).isCancelled()).isTrue();
        assertThat(seen).containsExactly("aborted");
        var late = new Probe();
        pendings.get(0).attach(late.connection(""));
        h.flush();
        assertThat(late.closes).as("after the plugin stopped, too").hasValue(1);
    }
```

`openRequests()` in both harnesses already hides `front|` lines; make it hide nothing else, so `session-tab|…` lines are visible to the cases that assert an empty list.

In `FakeContractTest` delegate the five methods to the host. In `AppContractTest` delegate them to the fixture on the EDT, and extend `flush()`: after the event loop has drained, `host.cleanup.drained().join()`, then one more `onEdt(() -> { })`, because an exit is noticed on the EDT after the cleanup thread completed a future.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-sdk-testkit:test :jasper-app:test --tests '*AppContractTest'`
Expected: compilation FAILS until the harness methods exist; once they do, all six cases PASS in both suites. If a case fails in only one suite, that implementation is wrong, not the case: the two must agree.

- [ ] **Step 3: Version**

Set `JasperSdk.VERSION = "0.5.0"` and the sample's `sdk = ">=0.5, <0.6"`.

- [ ] **Step 4: Run everything**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL`; 29 contract cases pass for the fake and for the application.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add -A jasper-sdk jasper-sdk-testkit jasper-app plugins
git commit -m "feat: hold provided sessions in the app and the testkit to one contract

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: The sample plugin's echo session

**Files:**
- Create: `plugins/sample/src/main/java/dev/jasper/sample/EchoSession.java`
- Modify: `plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java`, `plugins/sample/src/main/resources/plugin.toml`, `plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java`

**Interfaces:**
- Produces, when `demo_session = true`: action `dev.jasper.sample.echo` ("Open Sample Echo Session") that opens a tab whose session the plugin provides. The connector reports "Connecting to the sample echo…", waits `demo_step_millis` on the background executor, and attaches a loopback: a greeting, then every typed byte echoed back (Enter as a new line), and Ctrl-D ends the session with exit status 0. The descriptor additionally declares `terminal.open` and `session.provide`.
- `EchoSession` uses a blocking queue, not `PipedInputStream`: a piped stream fails with "write end dead" once the thread that wrote last has ended, and the greeting is written by a short-lived background task.

- [ ] **Step 1: Write the failing test**

Append to `SamplePluginTest` (imports `dev.jasper.sdk.terminal.PaneHandle` where missing):

```java
    @Test void theSessionDemoProvidesAnEchoThatEndsOnControlD() {
        try (var host = new FakePluginHost()) {
            UUID window = host.addTerminalWindow();
            host.activateTerminalWindow(window);
            host.setConfig("dev.jasper.sample", Map.of("demo_session", true, "demo_step_millis", 0L));
            host.start(new PluginInfo("dev.jasper.sample", "Sample", "0.1.0", Set.of(Capabilities.SESSION_PROVIDE)), Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.failures()).isEmpty();
            assertThat(host.invoke("dev.jasper.sample.echo", window, null)).isTrue();
            assertThat(host.openRequests()).containsExactly("session-tab|" + window + "|Sample echo");
            UUID pane = host.terminalPanes().stream().filter(id -> host.sessionState(id).startsWith("CONNECTING")).findFirst().orElseThrow();
            assertThat(host.sessionState(pane)).isEqualTo("CONNECTING|");
            host.runBackground();
            assertThat(host.sessionState(pane)).isEqualTo("RUNNING|");
            assertThat(host.sessionOutput(pane)).contains("Sample echo session").endsWith("\r\n");
            host.typeIntoSession(pane, "hi\r");
            assertThat(host.sessionOutput(pane)).isEqualTo("hi\r\n");
            host.typeIntoSession(pane, "\u0004");
            host.flush();
            assertThat(host.sessionState(pane)).isEqualTo("EXITED|exit 0");
            assertThat(host.failures()).isEmpty();
        }
    }
```

The test finds the new pane through `host.terminalPanes()`, which this task adds to `FakePluginHost`: `public List<UUID> terminalPanes()`, the ids of every open pane in creation order, with Javadoc.

If `runBackground()` does not run a task that sleeps zero milliseconds to completion, call it until it returns 0.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-plugin-sample:test`
Expected: FAIL: no `dev.jasper.sample.echo` action.

- [ ] **Step 3: Write the echo**

`plugins/sample/src/main/java/dev/jasper/sample/EchoSession.java`:

```java
package dev.jasper.sample;

import dev.jasper.sdk.terminal.TerminalConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A loopback "remote": whatever is typed comes back, Enter starts a new line, Ctrl-D ends the session. It shows
 * the whole contract of a connection in a few lines: a blocking read that close unblocks, an exit status, and a
 * close that is safe to call more than once.
 */
final class EchoSession {
    private static final byte[] END = new byte[0];
    private final LinkedBlockingQueue<byte[]> toTerminal = new LinkedBlockingQueue<>();
    private final CompletableFuture<Integer> exited = new CompletableFuture<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    private final InputStream output = new InputStream() {
        private byte[] current = END;
        private int position;
        private boolean ended;
        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
        }
        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) return 0;
            while (position >= current.length) {
                if (ended) return -1;
                try { current = toTerminal.take(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
                position = 0;
                if (current == END) { ended = true; return -1; }
            }
            int count = Math.min(length, current.length - position);
            System.arraycopy(current, position, buffer, offset, count);
            position += count;
            return count;
        }
        @Override public int available() {
            int queued = 0;
            for (byte[] chunk : toTerminal) queued += chunk.length;
            return current.length - position + queued;
        }
    };

    private final OutputStream input = new OutputStream() {
        @Override public void write(int value) { write(new byte[]{(byte) value}, 0, 1); }
        @Override public void write(byte[] bytes, int offset, int length) {
            for (int i = offset; i < offset + length && !exited.isDone(); i++) {
                if (bytes[i] == 4) { say("\r\n[sample echo ended]\r\n"); toTerminal.add(END); exited.complete(0); }
                else if (bytes[i] == '\r') say("\r\n");
                else toTerminal.add(new byte[]{bytes[i]});
            }
        }
    };

    EchoSession() { say("Sample echo session. Type anything; Ctrl-D ends it.\r\n"); }

    private void say(String text) { toTerminal.add(text.getBytes(StandardCharsets.UTF_8)); }

    TerminalConnection connection() {
        return new TerminalConnection(output, input, (columns, rows) -> { }, exited, () -> {
            // Idempotent, prompt, and it unblocks a reader waiting in take().
            if (closed.compareAndSet(false, true)) toTerminal.add(END);
        });
    }
}
```

In `plugin.toml` change the capabilities line to `capabilities = ["terminal.observe", "terminal.inject", "terminal.open", "session.provide"]`. In `SamplePlugin` add imports `dev.jasper.sdk.terminal.OpenRequest`, `dev.jasper.sdk.terminal.PendingSession`, `dev.jasper.sdk.terminal.SessionSpec`, a constant `private static final String ECHO = "dev.jasper.sample.echo";`, the line `if (context.config().bool("demo_session").orElse(false)) installSessionDemo(context, stepMillis);` after the `demo_terminal` line in `start`, and:

```java
    // example:pluginsession:start
    private static void installSessionDemo(PluginContext context, long stepMillis) {
        if (!context.plugin().capabilities().contains(Capabilities.SESSION_PROVIDE)) {
            context.log().log(System.Logger.Level.INFO, "The session demo needs session.provide");
            return;
        }
        context.actions().register(ActionSpec.of(ECHO, "Open Sample Echo Session").withKeywords(List.of("sample", "session", "echo")), invoked ->
            // The pane appears at once, waiting. The connector runs on the event thread for the first connect
            // and for every Reconnect, so it only hands the work to the background executor.
            context.terminals().openTab(invoked.window(), OpenRequest.session(SessionSpec.of("Sample echo",
                pending -> context.background().execute(() -> connectEcho(pending, stepMillis))))));
    }

    private static void connectEcho(PendingSession pending, long stepMillis) {
        pending.status("Connecting to the sample echo…");
        // A real connect blocks here; onCancelled is where it would be aborted.
        var waiting = Thread.currentThread();
        var registration = pending.onCancelled(waiting::interrupt);
        try { Thread.sleep(stepMillis); }
        catch (InterruptedException cancelled) { return; }
        finally { registration.close(); }
        // From attach on, Jasper owns the connection and closes it exactly once, even if the user cancelled meanwhile.
        pending.attach(new EchoSession().connection());
    }
    // example:pluginsession:end
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-plugin-sample:check :jasper-sdk-testkit:check :jasper-app:test --tests '*BundledSamplePluginTest' verifyPluginArchitecture`
Expected: PASS. Do not commit yet: the compiled documentation example comes with Task 9, which shares the commit.

---

### Task 9: Documentation

**Files:**
- Modify: `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `docs/terminal-architecture.md`, `docs/app-architecture.md`, `docs/configuration.md`, `docs/STATUS.md`, `jasper-sdk/README.md`, `jasper-terminal/README.md`, `AGENTS.md`, this plan's status banner

- [ ] **Step 1: Update the authoring guide**

In `docs/plugin-authoring.md`: change "(0.4)" to "(0.5)" and replace "Plugin-provided sessions arrive in a later SDK version." with "and providing a pane's session, such as a remote connection."; change the descriptor range to `sdk = ">=0.5, <0.6"`; add a row to the capability table, `| `session.provide` | `Terminals.openTab` and `split` with `OpenRequest.session(...)` |`; re-copy every compiled example from `SamplePlugin.java` (`start()` gained a line); and add after "Terminals and capabilities":

````markdown
## Providing a session

A provided session is a pane whose program is yours: an SSH channel, a serial line, a container
shell. You describe it with a `SessionSpec` and open it like any tab. The pane appears at once
with a status line and Cancel; your `connector` is then called, on the event thread, once for the
first connect and once for every Reconnect, each time with a fresh `PendingSession`.

<!-- EXAMPLE-MARKER:pluginsession -->
```java
REPLACE-WITH-SOURCE
```

- **One attempt, one outcome.** Exactly one of `attach`, `fail` and cancellation takes effect; the
  first wins and later calls are ignored. Every `PendingSession` method is safe from any thread.
- **Ownership transfers at `attach`, whatever the outcome.** Jasper invokes your connection's
  `close` exactly once: when the pane closes, when the session ends, or at once if the attempt was
  already cancelled. Release everything that belongs to the session there, and never count on
  `attach` having "worked".
- **Cancellation** happens when the user presses Cancel, the pane closes, your plugin stops or
  Jasper quits. `onCancelled` handlers run at most once, on Jasper's cleanup thread, and at once
  when registered late. Use them to abort a blocking connect.
- **Threads.** `output.read` runs on the session's reader thread and may block; `close` must
  unblock it. `input.write`, `flush` and `resize` run only on Jasper's writer thread for that
  session, so a stalled network never freezes the user's typing; when its 4 MiB queue is full the
  pane says input was dropped. `close` runs on the cleanup thread. None of them runs on the event
  thread, and none of them may touch Swing.
- **Ending.** Complete `exited` with the status. Jasper keeps reading `output` to its end, for at
  most two seconds, so the last output is on screen before the pane says "Disconnected (exit N)".
  Completing `exited` exceptionally, or an `IOException` from a stream, is a connection failure, and
  its message is shown. `ExitPolicy.CLOSE_PANE` closes the pane instead.
- **The remote pty** should be requested with `TerminalConnection.TERM` and the attempt's
  `columns()` and `rows()`; the pane resizes it through `resize` once it has been laid out.
- A remote shell with Jasper's shell integration produces the same command events as a local one.
  Working directories it reports are not exposed yet: a remote path must never look local.
- `PipedInputStream` fails once the thread that wrote last has ended. The sample uses a queue.
````

Replace `EXAMPLE-MARKER` with `example` and `REPLACE-WITH-SOURCE` with the source between `// example:pluginsession:start` and `// example:pluginsession:end` after `stripIndent().strip()`, copied from `SamplePlugin.java`.

In "Testing" add: "A provided session is driven with `host.sessionState(paneId)`, `typeIntoSession`, `sessionOutput`, `cancelSession` and `reconnectSession`; `flush()` notices exits. The fake runs cancellation handlers and closes inline where Jasper uses its cleanup thread."

- [ ] **Step 2: Update the other documents**

`jasper-sdk/README.md`: extend the `dev.jasper.sdk.terminal` row with "`SessionSpec`, `PendingSession`, `TerminalConnection`, `ExitPolicy`".

`jasper-terminal/README.md` and `docs/terminal-architecture.md`: add `session.AttachedConnection` to the supported allowlist wherever it is listed, document `TerminalSession.attach` and `TerminalSessionListener.inputDropped` beside `start`, and add to the architecture document:

```markdown
## Attached sessions

`TerminalSession.attach(AttachedConnection, GridSize, scrollback)` runs a program that is not a
local process. `internal.transport.AttachedTransport` (JDK only) owns the connection: the reader
thread decodes `output`; one writer thread delivers writes, each followed by a flush, and resizes in
submission order from a 4 MiB queue, so `write` and `resize` only enqueue and a stalled remote
cannot block the EDT; a write that does not fit is rejected whole and reported through
`TerminalSessionListener.inputDropped`; a resize directly behind another queued resize replaces it.
After the connection's `exited` future completes, output is read to its end or until a two-second
drain window closes the connection. `close` reaches the connection exactly once, from whichever
side ends first. `internal.emulation.AttachedConnector` adapts the transport to JediTerm, so
attached output runs through the same `ShellIntegrationConnector` chain as a PTY.

An attached session differs from a PTY session in four ways: it writes no exit line into the
buffer; `exitFuture()` completes exceptionally when the status is unknown or the transport failed;
`foregroundJob()` is empty; and it never reports a local working directory, because the path a
remote shell reports is a path on another machine.
```

`docs/sdk-architecture.md`: add before "Plugins manager, install and restart":

```markdown
## Provided sessions

`OpenRequest.session` needs `session.provide`. `HostedSessions` turns the `SessionSpec` into the
app-native `terminals.SessionRequest`; the pane creates one `terminals.SessionAttempt` per connect
and reconnect and calls the connector on the EDT. The attempt is the ownership boundary:
`PENDING` becomes `ATTACHED`, `FAILED` or `CANCELLED` atomically, the first transition wins, and a
connection offered to an attempt that is no longer pending is closed at once. The connection handed
to the pane is a guarded copy whose `close` reaches the plugin's exactly once, on the
`CleanupWorker`: an application-owned daemon thread that also runs cancellation handlers, never
rejects, outlives the plugin's own executor, and joins the bounded shutdown wait. Stopping a plugin
cancels what it is still connecting; attached sessions belong to their panes and close with them.

`workspace.TerminalPane` shows a provided session in three states around one view: pending (status
line, Cancel), running, and disconnected (how it ended, Reconnect or Retry, Close). Cancelling an
attempt in a pane that never showed a session closes the pane.
```

Replace "Not yet implemented" with: "Working-directory provenance (classifying OSC 7 reports by host and exposing `RemoteDirectory`) and explicit commands in `LocalSpec` (plan 4c)."

`docs/app-architecture.md`: extend the `terminals` row with "and the app-native session request and attempt state machine", and the `workspace` row with "and the pending and disconnected states of provided sessions".

`docs/configuration.md`: in the sample plugin's table add `demo_session = true       # add "Open Sample Echo Session", a pane whose session the plugin provides`.

`AGENTS.md`, "Architecture rules": after the JediTerm bullet add "`TerminalSession.attach` runs a connection instead of a child process; its transport lives in `internal.transport` (JDK only), writes and resizes only enqueue, and an attached session never reports a local working directory."

`docs/STATUS.md`: update the opening paragraph to say plan 4b is implemented on `claude/plugin-sdk-plan-4b` and that provenance moved to plan 4c; add a dated "Plugin SDK plan 4b" section with the nine scope decisions at the top of this plan, exact test counts, and native acceptance pending.

Set this plan's **Status** banner to "Implemented on `claude/plugin-sdk-plan-4b`; native acceptance pending" and list any deviation.

- [ ] **Step 3: Verify everything**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*' :jasper-terminal:check`
Expected: PASS: five examples match `SamplePlugin.java`, and the terminal module's documentation checks accept the new section.

Run the AGENTS.md Python hygiene check. Expected: no output.

Run: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
Expected: `BUILD SUCCESSFUL` (rerun if the only failure is the known terminal flake).

- [ ] **Step 4: Commit Tasks 8 and 9**

```bash
git branch --show-current
git add -A
git commit -m "feat: provide an echo session from the sample plugin and document provided sessions

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Native acceptance (user-run; agents must not launch the GUI)

With `demo_session = true` and `demo_step_millis = 2000` under `[plugins."dev.jasper.sample"]`:

1. `./gradlew :jasper-app:run`. The palette's "Open Sample Echo Session" opens a tab titled "Sample echo" at once, showing "Connecting to the sample echo…" with Cancel; two seconds later the terminal appears with the greeting. Typing echoes; Enter starts a new line; the pane resizes and reflows like any other.
2. Ctrl-D: "[sample echo ended]" appears, then a bar at the bottom of the pane reads "Disconnected (exit 0)" with Reconnect and Close. Reconnect shows "Connecting…" in the bar with Cancel, then a fresh echo session. Close closes the pane.
3. Open another echo session and press Cancel during the two seconds: the tab closes. Reconnect after an exit, then Cancel: the bar reads "Connection cancelled" and Reconnect is still there.
4. Split Right on the echo pane opens an ordinary local shell in your home directory.
5. Close the echo tab, a window holding one, and quit Jasper with one open: no error dialog, no exception in the log, and the process exits promptly.
6. File → Manage Plugins… lists "Run its own terminal sessions, such as remote connections" and "Open terminal tabs, splits and windows" for the sample. The log has "used session.provide: opened a tab in window …" for each session.
7. With `demo_terminal = true` as well: "Insert Sample Greeting" types into the echo pane like into any other, and the text comes back.
8. Ordinary local tabs behave exactly as before: start, exit line `[process exited with code N]`, working directory in the status bar, "new tab in the same directory".

## Self-review record

- **Spec coverage.** §6 "Plugin-provided sessions": pane first, connector on the EDT, a throwing connector is a failure → Tasks 4, 5; one attempt with `PENDING` → `ATTACHED` | `FAILED` | `CANCELLED`, atomic, first wins → Task 3; cancellation causes (Cancel, pane or tab or window close, plugin stop, shutdown), handlers at most once on the cleanup worker, late registration scheduled at once, also after the context is `CLOSED` → Tasks 3, 4, 5; ownership transfers at `attach`, rejected connections closed at once, exactly-once `close` → Tasks 1, 3, 5 and contract cases in Task 7; `status` and `fail` on a finished attempt ignored → Task 3; Reconnect isolation, same connector, fresh `PendingSession`, stale attempts cannot attach, a failed reconnect returns to the banner → Tasks 3, 4. "Exit and drain": normal and exceptional `exited`, stream `IOException`, bounded drain before `close`, state and banner after the final output, `CLOSE_PANE`, abandoned reader on a `close` that does not unblock → Tasks 1, 2, 4 (the reader is a daemon thread; an unresponsive `close` leaves it blocked and harmless). "Transport contract": one writer thread, 4 MiB queue, enqueue-only `write` and `resize`, submission order, coalesced resizes, whole-write rejection with a pane notice, failure on `write` or `flush` → Tasks 1, 2, 4. "Terminal integration": `TerminalSession.attach` on the allowlist, same `ShellIntegrationFilter` chain, no JediTerm type in a signature, `TerminalConnection.TERM`, a user's split opens a local shell → Tasks 2, 4, 5. §3 cleanup worker and the transport threading table → Tasks 1, 5. §4 item 4 pane states → Task 4; item 7 `TerminalSession.attach` with an app-owned writer → Tasks 1, 2. §11 testkit and contract → Tasks 6, 7. §13 item 4 demo "the sample opens a loopback echo session" → Task 8. Deferred by decision 1: working-directory provenance (plan 4c).
- **Type consistency.** `AttachedTransport`'s eight-argument constructor, `AttachedConnection`'s five components, `TerminalSession.attach(connection, grid, scrollback)`, `OpenSpec.{Local, Session}`, `SessionRequest`'s five components, `SessionAttempt`'s five-argument constructor with `onStatus`, `onAttached`, `onFailed`, `PaneSnapshot`'s eight components, `TerminalPane`'s three-argument constructor with `noticeText`, `noticePrimary`, `noticeSecondary`, `TerminalTab.split(target, axis, directoryOrNull, requestOrNull)`, `WindowContent.openTab(directory, requestOrNull)`, `SessionSpec`'s four components, `TerminalConnection`'s five, `HostedSessions`' four-argument constructor, `HostedTerminals`' seven-argument constructor, the session state strings and the `session-tab|` and `session-split|` request lines are used identically wherever they appear.
- **Build stays green per task** except between Tasks 8 and 9, which share one commit.
