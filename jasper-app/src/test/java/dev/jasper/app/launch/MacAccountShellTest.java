package dev.jasper.app.launch;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(OS.WINDOWS)
class MacAccountShellTest {
    @Test void readsAccountOutputAndRejectsMissingOrMalformedValues() {
        assertThat(read("UserShell: /bin/zsh\n")).contains("/bin/zsh");
        assertThat(read("UserShell: /opt/My Shell/bin/fish\n")).contains("/opt/My Shell/bin/fish");
        for (String output : new String[]{"", "UserShell: ", "UserShell: bash", "error: unknown user",
                "UserShell: /bin/zsh\nOther: value", "UserShell: /bin/zsh\0bad"}) {
            assertThat(read(output)).as("malformed output").isEmpty();
        }
        assertThat(DefaultShell.readAccountShell(new ProcessBuilder("/bin/sh", "-c", "printf 'UserShell: /bin/zsh'; exit 1"))).isEmpty();
        assertThat(DefaultShell.readAccountShell(new ProcessBuilder("/nonexistent/jasper-dscl"))).isEmpty();
    }

    @Test void hungQueryTimesOutAndIsTerminated() {
        long started = System.nanoTime();
        assertThat(DefaultShell.readAccountShell(new ProcessBuilder("/bin/sh", "-c", "exec sleep 30"))).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
    }

    @Test void interruptionIsPreserved() {
        Thread.currentThread().interrupt();
        try {
            assertThat(DefaultShell.readAccountShell(new ProcessBuilder("/bin/sh", "-c", "exec sleep 30"))).isEmpty();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private static java.util.Optional<String> read(String output) {
        // A controlled program, no user startup files; printf's argument carries the fixture literally.
        if (output.indexOf(0) >= 0) {
            return DefaultShell.readAccountShell(new ProcessBuilder("/bin/sh", "-c", "printf 'UserShell: /bin/zsh\\000bad'"));
        }
        return DefaultShell.readAccountShell(new ProcessBuilder("/bin/sh", "-c", "printf '%s' \"$1\"", "fixture", output));
    }
}
