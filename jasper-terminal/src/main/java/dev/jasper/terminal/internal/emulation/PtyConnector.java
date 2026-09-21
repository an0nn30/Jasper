package dev.jasper.terminal.internal.emulation;

import dev.jasper.terminal.internal.process.PtyChild;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import java.util.Optional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** JediTerm protocol adapter; the child owns all native process resources. */
final class PtyConnector implements TtyConnector {
    private final PtyChild child;
    PtyConnector(PtyChild child) { this.child = child; }
    public int read(char[] b, int o, int n) throws IOException { return child.read(b,o,n); }
    public void write(byte[] b) throws IOException { child.write(b); }
    public void write(String s) throws IOException { write(s.getBytes(StandardCharsets.UTF_8)); }
    public boolean isConnected() { return child.isConnected(); }
    public boolean ready() throws IOException { return child.ready(); }
    public void resize(TermSize size) { child.resize(size.getColumns(),size.getRows()); }
    public int waitFor() throws InterruptedException { return child.waitFor(); }
    public String getName() { return "pty"; }
    public void close() { child.close(); }
    Optional<String> foregroundJob() { return child.foregroundJob(); }
}
