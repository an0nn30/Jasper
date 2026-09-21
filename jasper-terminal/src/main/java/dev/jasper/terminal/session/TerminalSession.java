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

/** A running terminal session. Owns lifecycle and listener registration; the view owns presentation. */
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
            command -> listeners.forEach(l -> l.commandExecuted(command.command(), command.status(), command.directory(), command.duration())));
        access = Objects.requireNonNull(create.apply(events), "access");
    }

    /** Internal module bridge; unsupported for application or plugin use. */
    public TerminalAccess internalAccess() { return access; }

    /** Starts a validated launch description and owns cleanup if session construction fails. */
    public static TerminalSession start(SessionLaunchOptions options) throws IOException {
        return PtySessionFactory.start(Objects.requireNonNull(options, "options"));
    }
    public void addListener(TerminalSessionListener listener) {
        listeners.add(listener);
    }
    public void removeListener(TerminalSessionListener listener) {
        listeners.remove(listener);
    }
    public void write(byte[] bytes) { access.write(bytes); }
    public void write(String text) { access.write(text); }
    public void resize(int newColumns, int newRows) { access.resize(newColumns, newRows); }
    /** Clears saved history while preserving the live screen and notifying listeners that absolute rows were reset. */
    public void clearScrollback() { access.clearScrollback(); }
    public int columns() { return access.columns(); }
    public int rows() { return access.rows(); }
    public String title() { return access.title(); }
    /** Current foreground job's executable name, when the OS exposes it. Query off the EDT. */
    public Optional<String> foregroundJob() { return access.foregroundJob(); }
    /** Completes with the exit code once the program's output has ended. */
    public CompletableFuture<Integer> exitFuture() { return access.exitFuture(); }
    public void close() { access.close(); }
    void startReading() { access.startReading(); }

    /** The directory the shell last reported with OSC 7, if any. */
    public Optional<Path> workingDirectory() { return access.workingDirectory(); }
    /** True once the shell has reported its first prompt (mark A) through Jasper's shell integration. */
    public boolean shellIntegrationDetected() { return access.shellIntegrationDetected(); }
}
