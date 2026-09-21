package dev.jasper.terminal.internal.emulation;

import dev.jasper.terminal.internal.shell.ShellIntegrationFilter;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ShellIntegrationConnectorTest {

    @Test
    void rewritesWhatItReads() throws Exception {
        FakeConnector inner = new FakeConnector();
        ShellIntegrationConnector connector = new ShellIntegrationConnector(inner);

        inner.feed("\033]7;file:///tmp\007x");
        inner.finish();

        assertThat(readAll(connector, 5)).isEqualTo(ShellIntegrationFilter.PREFIX + "cwd;file:///tmp\007x");
    }

    @Test
    void releasesAnIncompleteSequenceAtEndOfStream() throws Exception {
        FakeConnector inner = new FakeConnector();
        ShellIntegrationConnector connector = new ShellIntegrationConnector(inner);

        inner.feed("ok\033]7;par");
        inner.finish();

        assertThat(readAll(connector, 64)).isEqualTo("ok\033]7;par");
    }

    @Test
    void delegatesWritesAndResizes() throws Exception {
        FakeConnector inner = new FakeConnector();
        ShellIntegrationConnector connector = new ShellIntegrationConnector(inner);

        connector.write("ls\r");
        connector.resize(new TermSize(30, 5));

        assertThat(inner.written()).isEqualTo("ls\r");
        assertThat(inner.lastResize()).isEqualTo(new TermSize(30, 5));
    }

    private static String readAll(TtyConnector connector, int chunkSize) throws IOException {
        char[] buffer = new char[chunkSize];
        StringBuilder text = new StringBuilder();
        int count;
        while ((count = connector.read(buffer, 0, buffer.length)) != -1) {
            text.append(buffer, 0, count);
        }
        return text.toString();
    }
}
