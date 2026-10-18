package dev.jasper.app.bootstrap;

import static dev.jasper.app.bootstrap.LaunchAction.AWAIT_LOCK;
import static dev.jasper.app.bootstrap.LaunchAction.EXIT;
import static dev.jasper.app.bootstrap.LaunchAction.HAND_OFF;
import static dev.jasper.app.bootstrap.LaunchAction.OWN;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 5.2: what a launch does, by whether it took the lock. */
class LaunchActionTest {
    @TempDir Path cwd;

    private record Row(List<String> args, LaunchAction free, LaunchAction held) { }

    @Test void theLaunchTable() {
        for (Row row : List.of(
                new Row(List.of(), OWN, HAND_OFF),
                new Row(List.of("notes.txt", "src"), OWN, HAND_OFF),
                new Row(List.of("--safe-mode"), OWN, HAND_OFF),
                new Row(List.of("--config", "c.toml"), OWN, HAND_OFF),
                new Row(List.of("--plugin-dir", "p", "notes.txt"), OWN, HAND_OFF),
                new Row(List.of("--background"), OWN, EXIT),
                new Row(List.of("--no-window"), OWN, EXIT),
                new Row(List.of("--replacing", "4242"), OWN, AWAIT_LOCK),
                new Row(List.of("--replacing", "4242", "--safe-mode", "notes.txt"), OWN, AWAIT_LOCK))) {
            AppArguments options = AppArguments.parse(row.args().toArray(String[]::new), cwd);
            assertThat(LaunchAction.of(true, options)).as("lock free: %s", row.args()).isEqualTo(row.free());
            assertThat(LaunchAction.of(false, options)).as("lock held: %s", row.args()).isEqualTo(row.held());
        }
    }
}
