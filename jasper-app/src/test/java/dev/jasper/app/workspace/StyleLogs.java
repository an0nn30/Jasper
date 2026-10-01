package dev.jasper.app.workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.*;

/** Scoped capture for expected plugin diagnostics, with exact logger restoration. */
final class StyleLogs implements AutoCloseable {
    private final Logger host = Logger.getLogger(WorkspaceStyleLayer.class.getName());
    private final Logger flat = Logger.getLogger("com.formdev.flatlaf");
    private final boolean hostParents = host.getUseParentHandlers(), flatParents = flat.getUseParentHandlers();
    private final Level hostLevel = host.getLevel(), flatLevel = flat.getLevel();
    private final List<LogRecord> records = new ArrayList<>();
    private final Handler handler = new Handler() {
        @Override public void publish(LogRecord record) { records.add(record); }
        @Override public void flush() {}
        @Override public void close() {}
    };
    StyleLogs() {
        host.setUseParentHandlers(false); flat.setUseParentHandlers(false);
        host.setLevel(Level.ALL); flat.setLevel(Level.ALL);
        host.addHandler(handler); flat.addHandler(handler);
    }
    List<LogRecord> host() { return records.stream().filter(r -> r.getLoggerName().equals(host.getName())).toList(); }
    List<LogRecord> flat() { return records.stream().filter(r -> r.getLoggerName().startsWith(flat.getName())).toList(); }
    @Override public void close() {
        host.removeHandler(handler); flat.removeHandler(handler);
        host.setLevel(hostLevel); flat.setLevel(flatLevel);
        host.setUseParentHandlers(hostParents); flat.setUseParentHandlers(flatParents);
    }
}
