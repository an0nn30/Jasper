package dev.jasper.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.nio.charset.StandardCharsets;

/** In-memory stand-in for a PTY: tests feed "program output" and inspect what the terminal wrote back. */
final class FakeConnector implements TtyConnector {
    private final PipedWriter output = new PipedWriter();
    private final PipedReader reader;
    private final ByteArrayOutputStream written = new ByteArrayOutputStream();
    private volatile TermSize lastResize;

    FakeConnector() throws IOException {
        reader = new PipedReader(output, 1 << 16);
    }

    void feed(String text) throws IOException {
        output.write(text);
        output.flush();
    }

    /** Simulates the program exiting: the reader sees end of stream. */
    void finish() throws IOException {
        output.close();
    }

    synchronized String written() {
        return written.toString(StandardCharsets.UTF_8);
    }

    TermSize lastResize() {
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
}
