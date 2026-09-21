package dev.jasper.terminal.internal.emulation;

import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.internal.TerminalAccess;
import dev.jasper.terminal.internal.text.CellAttributes;
import dev.jasper.terminal.internal.text.CursorRequest;
import dev.jasper.terminal.internal.text.TerminalRow;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.session.TestSession;

import com.jediterm.terminal.TtyConnector;
import java.io.IOException;
import java.util.function.LongSupplier;

/** Headless emulator fixture with explicit started and unstarted construction paths. */
public final class EmulationFixture implements AutoCloseable {
    private final FakeConnector connector;
    private final TerminalSession session;
    private EmulationFixture(FakeConnector connector, TerminalSession session) {
        this.connector = connector; this.session = session;
    }
    public static EmulationFixture open(int columns, int rows, int scrollback) throws IOException {
        return open(columns, rows, scrollback, System::nanoTime);
    }
    public static EmulationFixture open(int columns, int rows, int scrollback, LongSupplier clock) throws IOException {
        var connector = new FakeConnector();
        try {
            var session = unstarted(connector, columns, rows, scrollback, clock);
            session.internalAccess().startReading();
            return new EmulationFixture(connector, session);
        } catch (RuntimeException | Error failure) {
            connector.close(); throw failure;
        }
    }
    public static TerminalSession unstarted(FakeConnector connector, int columns, int rows, int scrollback) {
        return unstarted(connector, columns, rows, scrollback, System::nanoTime);
    }
    public static TerminalSession unstarted(FakeConnector connector, int columns, int rows, int scrollback, LongSupplier clock) {
        return compose(connector, columns, rows, scrollback, clock);
    }
    static TerminalSession unstarted(TtyConnector connector, int columns, int rows, int scrollback) {
        return compose(connector, columns, rows, scrollback, System::nanoTime);
    }
    private static TerminalSession compose(TtyConnector connector, int columns, int rows, int scrollback, LongSupplier clock) {
        try {
            return TestSession.create(events -> new TerminalAccess(
                new JediTermEngine(connector, columns, rows, scrollback, events), events, clock));
        } catch (RuntimeException | Error failure) {
            connector.close(); throw failure;
        }
    }
    public TerminalSession session() { return session; }
    public TerminalAccess access() { return session.internalAccess(); }
    public void feed(String text) throws IOException { connector.feed(text); }
    public void finish() throws IOException { connector.finish(); }
    public String written() { return connector.written(); }
    public GridSize lastResize() {
        var size = connector.lastResize();
        return size == null ? null : new GridSize(size.getColumns(), size.getRows());
    }
    @Override public void close() { session.close(); }

    public static TerminalRow capture(com.jediterm.terminal.model.TerminalLine line, int width) {
        return new JediCellReader().capture(line, width);
    }
    public static CellAttributes attributes(com.jediterm.terminal.TextStyle style) {
        return new JediCellReader().attributes(style);
    }
    public static int color(com.jediterm.terminal.TerminalColor color) { return JediCellReader.color(color); }
    public static CursorRequest cursor(com.jediterm.terminal.CursorShape shape) { return JediCellReader.cursor(shape); }
}
