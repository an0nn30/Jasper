package dev.jasper.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;

import java.io.IOException;

/** Runs everything read from the program through a {@link ShellIntegrationFilter}; all else is delegated. */
final class ShellIntegrationConnector implements TtyConnector {
    private final TtyConnector inner;
    private final ShellIntegrationFilter filter = new ShellIntegrationFilter();
    private final StringBuilder pending = new StringBuilder();
    private final char[] chunk = new char[8192];
    private boolean innerEnded;

    ShellIntegrationConnector(TtyConnector inner) {
        this.inner = inner;
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        while (pending.isEmpty()) {
            if (innerEnded) {
                return -1;
            }
            int count = inner.read(chunk, 0, chunk.length);
            if (count < 0) {
                innerEnded = true;
                filter.finish(pending);
            } else {
                filter.filter(chunk, 0, count, pending);
            }
        }
        int count = Math.min(length, pending.length());
        pending.getChars(0, count, buf, offset);
        pending.delete(0, count);
        return count;
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        inner.write(bytes);
    }

    @Override
    public void write(String string) throws IOException {
        inner.write(string);
    }

    @Override
    public boolean isConnected() {
        return inner.isConnected();
    }

    @Override
    public void resize(TermSize size) {
        inner.resize(size);
    }

    @Override
    public int waitFor() throws InterruptedException {
        return inner.waitFor();
    }

    @Override
    public boolean ready() throws IOException {
        return !pending.isEmpty() || inner.ready();
    }

    @Override
    public String getName() {
        return inner.getName();
    }

    @Override
    public void close() {
        inner.close();
    }
}
