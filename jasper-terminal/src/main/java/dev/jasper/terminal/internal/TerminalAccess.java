package dev.jasper.terminal.internal;

import dev.jasper.terminal.internal.emulation.BufferQueries;
import dev.jasper.terminal.internal.emulation.JediTermEngine;
import dev.jasper.terminal.internal.process.PtyChild;
import dev.jasper.terminal.internal.rendering.ScreenSnapshot;
import dev.jasper.terminal.internal.shell.ShellCommandTracker;
import dev.jasper.terminal.internal.text.MouseGeometry;
import dev.jasper.terminal.internal.text.MouseInput;
import dev.jasper.terminal.internal.text.SelectedCells;
import dev.jasper.terminal.internal.text.Selection;
import dev.jasper.terminal.internal.text.TerminalSearch;
import dev.jasper.terminal.search.SearchQuery;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import dev.jasper.terminal.internal.shell.CompletedCommand;
import dev.jasper.terminal.internal.transport.AttachedTransport;

/** Internal module bridge; not an application or plugin API. No vendor objects escape. */
public final class TerminalAccess implements AutoCloseable {
    private final JediTermEngine engine;
    private final BufferQueries queries;
    private final ShellCommandTracker shell;
    private final boolean local;
    /** Unsupported module collaboration API; used only by terminal owners. */
    public TerminalAccess(PtyChild child, int columns, int rows, int scrollback, JediTermEngine.Events events) {
        this(new JediTermEngine(child, columns, rows, scrollback, events), events, System::nanoTime);
    }
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
    /** Unsupported module collaboration API; used only by terminal owners. */
    public Optional<Path> workingDirectory() { return local ? shell.workingDirectory() : Optional.empty(); }
    /** Unsupported module collaboration API; used only by terminal owners. */
    public boolean shellIntegrationDetected() { return shell.detected(); }
    /** Unsupported module collaboration API; used only by terminal owners. */
    public ScreenSnapshot snapshot() { return queries.snapshot(); }

    /** The rows starting at an absolute top row (clamped to the scrollback), or the live screen for FOLLOW_OUTPUT. Unsupported module collaboration API. */
    public ScreenSnapshot snapshot(long topRow) { return queries.snapshot(topRow); }
    /** Unsupported module collaboration API; used only by terminal owners. */
    public MouseGeometry mouseGeometry(long requestedTopRow) { return queries.mouseGeometry(requestedTopRow); }
    /** Cursor eligibility without copying terminal lines; called only while a view can actually blink. Unsupported module collaboration API. */
    public boolean blinkingCursorInView(long requestedTopRow, boolean configuredBlink) { return queries.blinkingCursorInView(requestedTopRow, configuredBlink); }
    /** The text of the line at an absolute row, or null when it is no longer in the scrollback. Unsupported module collaboration API. */
    public String lineText(long absoluteRow) { return queries.lineText(absoluteRow); }
    /** The text of a selection: soft-wrapped rows joined, wide characters whole, trailing spaces trimmed. Unsupported module collaboration API. */
    public String text(Selection selection) { return queries.text(selection); }
    /** Unsupported module collaboration API; used only by terminal owners. */
    public List<SelectedCells> selectedLiveCells(Selection selection) { return queries.selectedLiveCells(selection); }
    /** Unsupported module collaboration API; used only by terminal owners. */
    public boolean selectionUnchanged(List<SelectedCells> cells) { return queries.selectionUnchanged(cells); }
    /** Validation and extraction share a lock so Copy cannot pick up an overwrite between the two. Unsupported module collaboration API. */
    public Optional<String> selectedText(Selection selection, List<SelectedCells> cells) { return queries.selectedText(selection, cells); }
    /** Unsupported module collaboration API; used only by terminal owners. */
    public List<TerminalSearch.Match> search(SearchQuery query) { return queries.search(query.text(), query.regex(), query.caseSensitive()); }
    /**
     * The link at an absolute row and column: an OSC 8 hyperlink, or else a URL written in the text. A program chooses
     * an OSC 8 target freely, so only http, https, ftp and mailto schemes are accepted; a cell whose OSC 8 target has any other scheme
     * has no link at all, whatever its text says.
     Unsupported module collaboration API. */
    public Optional<String> linkAt(long absoluteRow, int column) { return queries.linkAt(absoluteRow, column); }
    /** The word at an absolute row and column, as a stream selection. Unsupported module collaboration API. */
    public Selection wordSelection(long row, int column) { return queries.wordSelection(row, column); }
    /** A logical line, bounded by {@link dev.jasper.terminal.internal.text.LogicalLine}'s extreme-line fallback, always including the clicked row. Unsupported module collaboration API. */
    public Selection lineSelection(long row) { return queries.lineSelection(row); }
    /** Unsupported module collaboration API; used only by terminal owners. */
    public void write(byte[] bytes) { engine.write(bytes); }
    /** Unsupported module collaboration API; used only by terminal owners. */
    public void write(String text) { engine.write(text); }
    /** Unsupported module collaboration API; used only by terminal owners. */
    public void resize(int newColumns, int newRows) { engine.resize(newColumns, newRows); }
    /** Clears saved history while preserving the live screen and notifying listeners that absolute rows were reset. Unsupported module collaboration API. */
    public void clearScrollback() { engine.clearScrollback(); }
    /** Unsupported module collaboration API; used only by terminal owners. */
    public int columns() { return engine.columns(); }
    /** Unsupported module collaboration API; used only by terminal owners. */
    public int rows() { return engine.rows(); }
    /** Unsupported module collaboration API; used only by terminal owners. */
    public String title() { return engine.title(); }
    /** Current foreground job's executable name, when the OS exposes it. Query off the EDT. Unsupported module collaboration API. */
    public Optional<String> foregroundJob() { return engine.foregroundJob(); }
    /** Completes with the exit code after output ends, the child wait completes, and the exit message reaches the buffer. Unsupported module collaboration API. */
    public CompletableFuture<Integer> exitFuture() { return engine.exitFuture(); }
    /** Unsupported module collaboration API; used only by terminal owners. */
    public void close() { engine.close(); }
    /** Unsupported module collaboration API; used only by terminal owners. */
    public void startReading() { engine.startReading(); }
    /** Unsupported module collaboration API; used only by terminal owners. */
    public byte[] codeForKey(int keyCode, int modifiers) { return engine.codeForKey(keyCode, modifiers); }
    /** Monotonic generation for history, reflow and alternate-buffer changes that invalidate absolute rows. Unsupported module collaboration API. */
    public long absoluteRowEpoch() { return engine.absoluteRowEpoch(); }
    /** Absolute rows of the prompts the shell marked with OSC 133;A retained in history or the live screen, oldest first. Unsupported module collaboration API. */
    public List<Long> promptRows() { return engine.promptRows(); }
    /** Whether the program asked for mouse reports, so clicks go to it instead of to local selection. Unsupported module collaboration API. */
    public boolean mouseReporting() { return engine.mouseReporting(); }
    /** Unsupported module collaboration API; used only by terminal owners. */
    public boolean usingAlternateBuffer() { return engine.usingAlternateBuffer(); }
    /** Reports a mouse event at a screen cell, clamped onto the screen; false when the program did not ask for it. Unsupported module collaboration API. */
    public boolean reportMouse(int column, int row, MouseInput event) { return engine.reportMouse(column, row, event); }
    /** Pastes text: newlines become carriage returns, wrapped in bracketed-paste markers when the program asked. Unsupported module collaboration API. */
    public void paste(String text) { engine.paste(text); }
}
