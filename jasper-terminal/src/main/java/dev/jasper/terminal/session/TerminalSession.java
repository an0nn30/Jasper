package dev.jasper.terminal.session;

import dev.jasper.terminal.internal.TerminalAccess;
import dev.jasper.terminal.internal.emulation.JediTermEngine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import java.util.function.Function;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.internal.transport.AttachedTransport;
import java.time.Duration;

/**
 * A running terminal session. Start off the EDT; the application owns and closes it.
 * The view owns presentation and detaching it does not close this session.
 * Listener callbacks are synchronous on the event source thread; never wait for the EDT in a callback.
 */
public final class TerminalSession implements AutoCloseable {
    private final List<TerminalSessionListener> listeners = new CopyOnWriteArrayList<>();
    private final TerminalAccess access;
    TerminalSession(Function<JediTermEngine.Events,TerminalAccess> create) {
        var events = new JediTermEngine.Events(
            () -> listeners.forEach(TerminalSessionListener::screenChanged),
            title -> listeners.forEach(l -> l.titleChanged(title)),
            () -> listeners.forEach(TerminalSessionListener::bell),
            () -> listeners.forEach(TerminalSessionListener::scrollbackReset),
            alternate -> listeners.forEach(l -> l.alternateBufferChanged(alternate)),
            directory -> listeners.forEach(l -> l.workingDirectoryChanged(directory)),
            command -> listeners.forEach(l -> l.commandStarted(command)),
            command -> listeners.forEach(l -> l.commandExecuted(command.command(), command.status(), command.directory(), command.duration())),
            location -> listeners.forEach(l -> l.remoteDirectoryChanged(new RemoteDirectory(location.host(), location.path()))));
        access = Objects.requireNonNull(create.apply(events), "access");
    }

    /** Internal module bridge; unsupported for application or plugin use. */
    public TerminalAccess internalAccess() { return access; }

    /** Starts a validated launch description and owns cleanup if session construction fails. */
    public static TerminalSession start(SessionLaunchOptions options) throws IOException {
        return PtySessionFactory.start(Objects.requireNonNull(options, "options"));
    }
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
    /** Registers for future synchronous notifications; does not replay current state. See TerminalSessionListener for thread/lock rules. */
    public void addListener(TerminalSessionListener listener) {
        listeners.add(listener);
    }
    /** Unregisters this listener; an already in-flight callback may still finish. */
    public void removeListener(TerminalSessionListener listener) {
        listeners.remove(listener);
    }
    /** Writes raw input bytes to the child. No key or paste translation is applied. */
    public void write(byte[] bytes) { access.write(bytes); }
    /** Writes UTF-8 input to the child. Use the view paste method for bracketed-paste semantics. */
    public void write(String text) { access.write(text); }
    /** Resizes the emulator and child grid; small sizes are clamped. May synchronously notify listeners on this calling thread. */
    public void resize(int newColumns, int newRows) { access.resize(newColumns, newRows); }
    /** Clears saved history while preserving the live screen and notifying listeners that absolute rows were reset. */
    public void clearScrollback() { access.clearScrollback(); }
    /** Returns the last requested, clamped grid width from published state; not an atomic width/height snapshot. */
    public int columns() { return access.columns(); }
    /** Returns the last requested, clamped grid height from published state; not an atomic width/height snapshot. */
    public int rows() { return access.rows(); }
    /** Returns the last program-reported title, initially empty. */
    public String title() { return access.title(); }
    /** Current foreground job's executable name, when the OS exposes it. Query off the EDT. */
    public Optional<String> foregroundJob() { return access.foregroundJob(); }
    /** Completes with the exit code after output ends, the child wait completes, and the exit message reaches the buffer. */
    public CompletableFuture<Integer> exitFuture() { return access.exitFuture(); }
    /** Closes this session once, initiating bounded asynchronous child cleanup. Safe to repeat; does not remove any Swing view. */
    public void close() { access.close(); }
    void startReading() { access.startReading(); }

    /**
     * The directory the shell last reported with OSC 7, if any. Local directories only: empty while the program
     * reports a directory under another host, and always for an attached session.
     */
    public Optional<Path> workingDirectory() { return access.workingDirectory(); }

    /**
     * The working directory the program last reported when it is not a directory of this machine. Exactly one
     * of this and {@link #workingDirectory()} is present once the program reported anything. A hint, never a fact.
     */
    public Optional<RemoteDirectory> remoteDirectory() {
        return access.remoteDirectory().map(location -> new RemoteDirectory(location.host(), location.path()));
    }
    /** True once the shell has reported its first prompt (mark A) through Jasper's shell integration. */
    public boolean shellIntegrationDetected() { return access.shellIntegrationDetected(); }
}
