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

/** Internal module bridge; not an application or plugin API. No vendor objects escape. */
public final class TerminalAccess implements AutoCloseable {
    private final JediTermEngine engine;
    private final BufferQueries queries;
    private final ShellCommandTracker shell;
    public TerminalAccess(PtyChild child, int columns, int rows, int scrollback, JediTermEngine.Events events) {
        this(new JediTermEngine(child, columns, rows, scrollback, events), events, System::nanoTime);
    }
    public TerminalAccess(JediTermEngine engine, JediTermEngine.Events events, LongSupplier clock) {
        this.engine = engine;
        queries = engine.queries();
        shell = new ShellCommandTracker(clock, queries::cursor, queries::captureCommand, queries::recordPrompt,
            events.workingDirectoryChanged(), events.commandStarted(), events.commandFinished(),
            events.screenChanged(), engine::resetCursorShape);
        engine.setShellHooks(shell::accept, shell::discardUnusedPayload);
    }
    public Optional<Path> workingDirectory() { return shell.workingDirectory(); }
    public boolean shellIntegrationDetected() { return shell.detected(); }
    public ScreenSnapshot snapshot() { return queries.snapshot(); }

    /** The rows starting at an absolute top row (clamped to the scrollback), or the live screen for FOLLOW_OUTPUT. */
    public ScreenSnapshot snapshot(long topRow) { return queries.snapshot(topRow); }
    public MouseGeometry mouseGeometry(long requestedTopRow) { return queries.mouseGeometry(requestedTopRow); }
    /** Cursor eligibility without copying terminal lines; called only while a view can actually blink. */
    public boolean blinkingCursorInView(long requestedTopRow, boolean configuredBlink) { return queries.blinkingCursorInView(requestedTopRow, configuredBlink); }
    /** The text of the line at an absolute row, or null when it is no longer in the scrollback. */
    public String lineText(long absoluteRow) { return queries.lineText(absoluteRow); }
    /** The text of a selection: soft-wrapped rows joined, wide characters whole, trailing spaces trimmed. */
    public String text(Selection selection) { return queries.text(selection); }
    public List<SelectedCells> selectedLiveCells(Selection selection) { return queries.selectedLiveCells(selection); }
    public boolean selectionUnchanged(List<SelectedCells> cells) { return queries.selectionUnchanged(cells); }
    /** Validation and extraction share a lock so Copy cannot pick up an overwrite between the two. */
    public Optional<String> selectedText(Selection selection, List<SelectedCells> cells) { return queries.selectedText(selection, cells); }
    public List<TerminalSearch.Match> search(SearchQuery query) { return queries.search(query.text(), query.regex(), query.caseSensitive()); }
    /**
     * The link at an absolute row and column: an OSC 8 hyperlink, or else a URL written in the text. A program chooses
     * an OSC 8 target freely, so only web and mail schemes are opened; a cell whose OSC 8 target has any other scheme
     * has no link at all, whatever its text says.
     */
    public Optional<String> linkAt(long absoluteRow, int column) { return queries.linkAt(absoluteRow, column); }
    /** The word at an absolute row and column, as a stream selection. */
    public Selection wordSelection(long row, int column) { return queries.wordSelection(row, column); }
    /** A logical line, bounded by {@link LogicalLine}'s extreme-line fallback, always including the clicked row. */
    public Selection lineSelection(long row) { return queries.lineSelection(row); }
    public void write(byte[] bytes) { engine.write(bytes); }
    public void write(String text) { engine.write(text); }
    public void resize(int newColumns, int newRows) { engine.resize(newColumns, newRows); }
    /** Clears saved history while preserving the live screen and notifying listeners that absolute rows were reset. */
    public void clearScrollback() { engine.clearScrollback(); }
    public int columns() { return engine.columns(); }
    public int rows() { return engine.rows(); }
    public String title() { return engine.title(); }
    /** Current foreground job's executable name, when the OS exposes it. Query off the EDT. */
    public Optional<String> foregroundJob() { return engine.foregroundJob(); }
    /** Completes with the exit code once the program's output has ended. */
    public CompletableFuture<Integer> exitFuture() { return engine.exitFuture(); }
    public void close() { engine.close(); }
    public void startReading() { engine.startReading(); }
    public byte[] codeForKey(int keyCode, int modifiers) { return engine.codeForKey(keyCode, modifiers); }
    /** Monotonic generation for history, reflow and alternate-buffer changes that invalidate absolute rows. */
    public long absoluteRowEpoch() { return engine.absoluteRowEpoch(); }
    /** Absolute rows of the prompts the shell marked with OSC 133;A that are still in the scrollback, oldest first. */
    public List<Long> promptRows() { return engine.promptRows(); }
    /** Whether the program asked for mouse reports, so clicks go to it instead of to local selection. */
    public boolean mouseReporting() { return engine.mouseReporting(); }
    public boolean usingAlternateBuffer() { return engine.usingAlternateBuffer(); }
    /** Reports a mouse event at a screen cell, clamped onto the screen; false when the program did not ask for it. */
    public boolean reportMouse(int column, int row, MouseInput event) { return engine.reportMouse(column, row, event); }
    /** Pastes text: newlines become carriage returns, wrapped in bracketed-paste markers when the program asked. */
    public void paste(String text) { engine.paste(text); }
}
