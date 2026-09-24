package dev.jasper.remote.client;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ShellChannelsTest {
    @Test void enterWatchPassesBytesThroughAndReportsCarriageReturns() throws IOException {
        var sink = new ByteArrayOutputStream();
        int[] enters = {0};
        var watch = new ShellChannels.EnterWatch(sink, () -> enters[0]++);
        watch.write('l');
        assertThat(enters[0]).isZero();
        watch.write("s\r".getBytes(UTF_8), 0, 2);
        assertThat(enters[0]).isEqualTo(1);
        watch.write('\r');
        assertThat(enters[0]).isEqualTo(2);
        watch.write("a\rb\r".getBytes(UTF_8), 1, 2);
        assertThat(enters[0]).as("one signal per write").isEqualTo(3);
        watch.write("\n".getBytes(UTF_8), 0, 1);
        assertThat(enters[0]).isEqualTo(3);
        watch.flush();
        assertThat(sink.toString(UTF_8)).isEqualTo("ls\r\r\rb\n");
    }
}
