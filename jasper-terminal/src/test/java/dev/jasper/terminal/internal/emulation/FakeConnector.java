package dev.jasper.terminal.internal.emulation;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.nio.charset.StandardCharsets;

/** In-memory stand-in for a PTY: tests feed "program output" and inspect what the terminal wrote back. */
public final class FakeConnector implements TtyConnector {
    private final PipedWriter output = new PipedWriter();
    private final PipedReader reader;
    private final ByteArrayOutputStream written = new ByteArrayOutputStream();
    private volatile TermSize lastResize;

    public FakeConnector() throws IOException {
        reader = new PipedReader(output, 1 << 16);
    }

    public void feed(String text) throws IOException {
        output.write(text);
        output.flush();
    }

    /** Simulates the program exiting: the reader sees end of stream. */
    public void finish() throws IOException {
        output.close();
    }

    public synchronized String written() {
        return written.toString(StandardCharsets.UTF_8);
    }

    public TermSize lastResize() {
        return lastResize;
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        return reader.read(buf, offset, length);
    }

    @Override
    public synchronized void write(byte[] bytes) {
        written.writeBytes(bytes);
    }

    @Override
    public void write(String string) {
        write(string.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void resize(TermSize size) {
        lastResize = size;
    }

    @Override
    public int waitFor() {
        return 0;
    }

    @Override
    public boolean ready() throws IOException {
        return reader.ready();
    }

    @Override
    public String getName() {
        return "fake";
    }

    @Override
    public void close() {
        try {
            output.close();
        } catch (IOException ignored) {
            // already closed
        }
    }
    public dev.jasper.terminal.config.GridSize lastGridSize() {
        TermSize size = lastResize();
        return size == null ? null : new dev.jasper.terminal.config.GridSize(size.getColumns(), size.getRows());
    }
}
