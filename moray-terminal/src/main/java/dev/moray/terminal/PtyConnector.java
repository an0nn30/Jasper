package dev.moray.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/** Connects JediTerm to a pty4j process: UTF-8 output in, bytes out, window size changes. */
final class PtyConnector implements TtyConnector {
    private final PtyProcess process;
    private final Reader reader;
    private final OutputStream input;

    PtyConnector(PtyProcess process) {
        this.process = process;
        this.reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
        this.input = process.getOutputStream();
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        return reader.read(buf, offset, length);
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        input.write(bytes);
        input.flush();
    }

    @Override
    public void write(String string) throws IOException {
        write(string.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isConnected() {
        return process.isAlive();
    }

    @Override
    public void resize(TermSize size) {
        process.setWinSize(new WinSize(size.getColumns(), size.getRows()));
    }

    @Override
    public int waitFor() throws InterruptedException {
        return process.waitFor();
    }

    @Override
    public boolean ready() throws IOException {
        return reader.ready();
    }

    @Override
    public String getName() {
        return "pty";
    }

    @Override
    public void close() {
        process.destroy();
    }
}
