package dev.jasper.terminal.internal.emulation;

import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.TerminalDisplay;
import com.jediterm.terminal.emulator.mouse.MouseFormat;
import com.jediterm.terminal.emulator.mouse.MouseMode;
import com.jediterm.terminal.model.TerminalSelection;

import java.util.function.Consumer;

/**
 * Receives display callbacks on the emulator's calling thread (normally the reader, or a
 * resize caller) and publishes the resulting state for the view to read. Drawing is done by {@code TerminalView}, not here.
 */
final class SessionDisplay implements TerminalDisplay {
    private final Consumer<String> onTitle;
    private final Runnable onBell;
    private final Runnable onCursorChange;
    private final Consumer<Boolean> onAlternateBufferChange;
    private volatile boolean cursorVisible = true;
    private volatile CursorShape cursorShape;
    private volatile String title = "";
    private volatile MouseMode mouseMode = MouseMode.MOUSE_REPORTING_NONE;
    private volatile MouseFormat mouseFormat;
    private volatile boolean bracketedPaste;

    SessionDisplay(Consumer<String> onTitle, Runnable onBell, Runnable onCursorChange,
                   Consumer<Boolean> onAlternateBufferChange) {
        this.onTitle = onTitle;
        this.onBell = onBell;
        this.onCursorChange = onCursorChange;
        this.onAlternateBufferChange = onAlternateBufferChange;
    }

    @Override
    public void setCursor(int x, int y) {
        // The view reads the cursor position from JediTerminal when it snapshots;
        // we only need to notify listeners that a repaint is due.
        onCursorChange.run();
    }

    @Override
    public void setCursorShape(CursorShape shape) {
        cursorShape = shape;
        onCursorChange.run();
    }

    /** DECSCUSR 0 (via the shell-integration filter): back to the configured cursor. */
    void resetCursorShape() {
        cursorShape = null;
        onCursorChange.run();
    }

    @Override
    public void beep() {
        onBell.run();
    }

    @Override
    public void scrollArea(int scrollRegionTop, int scrollRegionSize, int dy) {
        // The view repaints from the buffer; nothing to scroll here.
    }

    @Override
    public void setCursorVisible(boolean visible) {
        cursorVisible = visible;
        onCursorChange.run();
    }

    @Override
    public void useAlternateScreenBuffer(boolean useAlternateScreenBuffer) {
        // TerminalTextBuffer tracks the state itself; owners still need to discard row-based view state.
        onAlternateBufferChange.accept(useAlternateScreenBuffer);
    }

    @Override
    public String getWindowTitle() {
        return title;
    }

    @Override
    public void setWindowTitle(String newTitle) {
        title = newTitle == null ? "" : newTitle;
        onTitle.accept(title);
    }

    @Override
    public TerminalSelection getSelection() {
        return null; // selection belongs to the Swing SelectionController, not the vendor display
    }

    @Override
    public void terminalMouseModeSet(MouseMode mode) {
        mouseMode = mode;
    }

    @Override
    public void setMouseFormat(MouseFormat format) {
        mouseFormat = format;
    }

    @Override
    public boolean ambiguousCharsAreDoubleWidth() {
        return false;
    }

    @Override
    public void setBracketedPasteMode(boolean enabled) {
        bracketedPaste = enabled;
    }

    boolean cursorVisible() {
        return cursorVisible;
    }

    /** The shape last requested by the application, or null for "use the configured shape". */
    CursorShape cursorShape() {
        return cursorShape;
    }

    String title() {
        return title;
    }

    MouseMode mouseMode() {
        return mouseMode;
    }

    MouseFormat mouseFormat() {
        return mouseFormat;
    }

    boolean bracketedPaste() {
        return bracketedPaste;
    }
}
