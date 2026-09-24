package dev.jasper.remote.client;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DirectoryProbeTest {
    @Test void parsesOneNulTerminatedAbsolutePath() {
        assertThat(DirectoryProbe.parse("/srv/app\0".getBytes(UTF_8))).contains("/srv/app");
        assertThat(DirectoryProbe.parse("/a b/c\nd\0".getBytes(UTF_8))).as("spaces and newlines survive").contains("/a b/c\nd");
        assertThat(DirectoryProbe.parse("/first\0/second\0".getBytes(UTF_8))).contains("/first");
        assertThat(DirectoryProbe.parse(new byte[0])).isEmpty();
        assertThat(DirectoryProbe.parse("/no-terminator".getBytes(UTF_8))).isEmpty();
        assertThat(DirectoryProbe.parse("relative\0".getBytes(UTF_8))).isEmpty();
        assertThat(DirectoryProbe.parse("\0".getBytes(UTF_8))).isEmpty();
        assertThat(DirectoryProbe.parse(new byte[] {'/', (byte) 0xFF, 0})).as("not UTF-8").isEmpty();
    }

    @Test void theScriptIsAFixedResource() {
        String script = new String(DirectoryProbe.script(), UTF_8);
        assertThat(script).contains("sshd-session", "dropbear", "JASPER_PROBE_ANCESTOR", "exit 3");
        assertThat(DirectoryProbe.COMMAND).isEqualTo("sh -s");
    }
}
