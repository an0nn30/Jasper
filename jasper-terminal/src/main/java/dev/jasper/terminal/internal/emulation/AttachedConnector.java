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
