package dev.jasper.remote.projects;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ExecTest {
    static byte[] script(String text) { return text.getBytes(StandardCharsets.UTF_8); }

    @Test void fieldsAfterTheMarkerArriveAndEarlierOutputIsIgnored(@TempDir Path dir) throws Exception {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows"));
        try (var fixture = new ProjectsFixture(dir); var lease = fixture.lease()) {
            var fields = new ArrayList<String>();
            var outcome = Exec.run(lease.session(), script("printf 'noise\\000'\nprintf 'm\\000a\\000b\\000'\nexit 5\n"), "m",
                fields::add, Duration.ofSeconds(10), () -> false);
            assertThat(fields).containsExactly("a", "b");
            assertThat(outcome).isEqualTo(new Exec.Outcome(5, true, false));
            assertThat(fixture.server.execCommands).containsExactly("sh -s");
        }
    }

    @Test void aFieldThatSaysStopEndsTheRun(@TempDir Path dir) throws Exception {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows"));
        try (var fixture = new ProjectsFixture(dir); var lease = fixture.lease()) {
            var fields = new ArrayList<String>();
            var outcome = Exec.run(lease.session(), script("printf 'm\\000'\nwhile :; do printf 'x\\000'; done\n"), "m",
                field -> fields.add(field) && fields.size() < 3, Duration.ofSeconds(10), () -> false);
            assertThat(outcome.stopped()).isTrue();
            assertThat(fields).hasSize(3);
        }
    }

    @Test void timeoutsAndCancellationEndTheRun(@TempDir Path dir) throws Exception {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows"));
        try (var fixture = new ProjectsFixture(dir); var lease = fixture.lease()) {
            assertThatThrownBy(() -> Exec.run(lease.session(), script("sleep 5\n"), "m", field -> true, Duration.ofMillis(300), () -> false))
                .isInstanceOf(IOException.class).hasMessageContaining("timed out");
            assertThatThrownBy(() -> Exec.run(lease.session(), script("sleep 5\n"), "m", field -> true, Duration.ofSeconds(10), () -> true))
                .isInstanceOf(CancellationException.class);
            var quiet = Exec.run(lease.session(), script("exit 4\n"), "m", field -> true, Duration.ofSeconds(10), () -> false);
            assertThat(quiet).isEqualTo(new Exec.Outcome(4, false, false));
        }
    }
}
