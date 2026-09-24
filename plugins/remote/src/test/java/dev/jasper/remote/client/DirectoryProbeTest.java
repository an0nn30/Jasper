package dev.jasper.remote.client;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DirectoryProbeTest {
    @Test void parsesTheFieldAfterTheLastMarker() {
        assertThat(DirectoryProbe.parse("jasper-cwd\0/srv/app\0".getBytes(UTF_8))).contains("/srv/app");
        assertThat(DirectoryProbe.parse("jasper-cwd\0/a b/c\nd\0".getBytes(UTF_8))).as("spaces and newlines survive").contains("/a b/c\nd");
        assertThat(DirectoryProbe.parse("Welcome!\r\njasper-cwd\0/srv/app\0".getBytes(UTF_8))).as("leading junk before the marker").contains("/srv/app");
        assertThat(DirectoryProbe.parse("jasper-cwd\0/first\0jasper-cwd\0/second\0".getBytes(UTF_8))).as("the last marker wins").contains("/second");
        assertThat(DirectoryProbe.parse("MOTD with no marker at all\0".getBytes(UTF_8))).as("junk without a marker").isEmpty();
        assertThat(DirectoryProbe.parse(new byte[0])).isEmpty();
        assertThat(DirectoryProbe.parse("jasper-cwd\0/no-terminator".getBytes(UTF_8))).isEmpty();
        assertThat(DirectoryProbe.parse("jasper-cwd\0relative\0".getBytes(UTF_8))).isEmpty();
        assertThat(DirectoryProbe.parse("jasper-cwd\0\0".getBytes(UTF_8))).isEmpty();
        var notUtf8 = new byte[] {'j', 'a', 's', 'p', 'e', 'r', '-', 'c', 'w', 'd', 0, '/', (byte) 0xFF, 0};
        assertThat(DirectoryProbe.parse(notUtf8)).as("not UTF-8").isEmpty();
    }

    @Test void theScriptIsAFixedResource() {
        String script = new String(DirectoryProbe.script(), UTF_8);
        assertThat(script).contains("sshd-session", "dropbear", "JASPER_PROBE_ANCESTOR", "exit 3", "jasper-cwd");
        assertThat(DirectoryProbe.COMMAND).isEqualTo("sh -s");
    }
}
