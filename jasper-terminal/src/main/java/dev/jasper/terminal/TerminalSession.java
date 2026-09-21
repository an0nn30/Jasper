package dev.jasper.terminal;

import com.jediterm.terminal.TtyConnector;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;

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
    TerminalSession(TtyConnector connector, int columns, int rows, int scrollback) {
        this(connector, columns, rows, scrollback, System::nanoTime);
    }
    TerminalSession(TtyConnector connector, int columns, int rows, int scrollback, LongSupplier clock) {
        this(events -> new TerminalAccess(new JediTermEngine(connector, columns, rows, scrollback, events), events, clock));
    }
    /** Internal module bridge; unsupported for application or plugin use. */
    public TerminalAccess internalAccess() { return access; }
    /** Starts {@code command} in a new pseudo-terminal and begins emulating its output. */
    public static TerminalSession start(List<String> command, Map<String, String> environment, Path workingDirectory,
                                        int columns, int rows, int scrollback) throws IOException {
        return start(new SessionLaunchOptions(command, environment, workingDirectory,
            new GridSize(columns, rows), scrollback));
    }
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
    byte[] codeForKey(int keyCode, int modifiers) { return access.codeForKey(keyCode, modifiers); }
    /** Monotonic generation for history, reflow and alternate-buffer changes that invalidate absolute rows. */
    long absoluteRowEpoch() { return access.absoluteRowEpoch(); }
    /** Absolute rows of the prompts the shell marked with OSC 133;A that are still in the scrollback, oldest first. */
    List<Long> promptRows() { return access.promptRows(); }
    /** Whether the program asked for mouse reports, so clicks go to it instead of to local selection. */
    boolean mouseReporting() { return access.mouseReporting(); }
    boolean usingAlternateBuffer() { return access.usingAlternateBuffer(); }
    /** Reports a mouse event at a screen cell, clamped onto the screen; false when the program did not ask for it. */
    boolean reportMouse(int column, int row, MouseInput event) { return access.reportMouse(column, row, event); }
    /** Pastes text: newlines become carriage returns, wrapped in bracketed-paste markers when the program asked. */
    void paste(String text) { access.paste(text); }
    /** The directory the shell last reported with OSC 7, if any. */
    public Optional<Path> workingDirectory() { return access.workingDirectory(); }
    /** True once the shell has reported its first prompt (mark A) through Jasper's shell integration. */
    public boolean shellIntegrationDetected() { return access.shellIntegrationDetected(); }
    ScreenSnapshot snapshot() { return access.snapshot(); }
    /** The rows starting at an absolute top row (clamped to the scrollback), or the live screen for FOLLOW_OUTPUT. */
    ScreenSnapshot snapshot(long topRow) { return access.snapshot(topRow); }
    MouseGeometry mouseGeometry(long requestedTopRow) { return access.mouseGeometry(requestedTopRow); }
    /** Cursor eligibility without copying terminal lines; called only while a view can actually blink. */
    boolean blinkingCursorInView(long requestedTopRow, boolean configuredBlink) { return access.blinkingCursorInView(requestedTopRow, configuredBlink); }
    /** The text of the line at an absolute row, or null when it is no longer in the scrollback. */
    String lineText(long absoluteRow) { return access.lineText(absoluteRow); }
    /** The text of a selection: soft-wrapped rows joined, wide characters whole, trailing spaces trimmed. */
    String text(Selection selection) { return access.text(selection); }
    List<SelectedCells> selectedLiveCells(Selection selection) { return access.selectedLiveCells(selection); }
    boolean selectionUnchanged(List<SelectedCells> cells) { return access.selectionUnchanged(cells); }
    /** Validation and extraction share a lock so Copy cannot pick up an overwrite between the two. */
    Optional<String> selectedText(Selection selection, List<SelectedCells> cells) { return access.selectedText(selection, cells); }
    /**
     * Every match in the scrollback and on screen, oldest first; an empty query finds nothing. On the alternate
     * screen only its own rows are searched, since the scrollback behind it is not what the user is looking at.
     */
    List<TerminalSearch.Match> search(String query, boolean regex, boolean caseSensitive) { return access.search(new SearchQuery(query, regex, caseSensitive)); }
    /**
     * The link at an absolute row and column: an OSC 8 hyperlink, or else a URL written in the text. A program chooses
     * an OSC 8 target freely, so only web and mail schemes are opened; a cell whose OSC 8 target has any other scheme
     * has no link at all, whatever its text says.
     */
    Optional<String> linkAt(long absoluteRow, int column) { return access.linkAt(absoluteRow, column); }
    /** The word at an absolute row and column, as a stream selection. */
    Selection wordSelection(long row, int column) { return access.wordSelection(row, column); }
    /** A logical line, bounded by {@link LogicalLine}'s extreme-line fallback, always including the clicked row. */
    Selection lineSelection(long row) { return access.lineSelection(row); }
}
