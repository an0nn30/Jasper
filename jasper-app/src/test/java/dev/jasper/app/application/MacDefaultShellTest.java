package dev.jasper.app.application;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.launch.LaunchSettings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledOnOs(OS.MAC)
class MacDefaultShellTest {
    @TempDir Path directory;

    @Test void applicationUsesTheCurrentAccountShell() throws Exception {
        Process query = new ProcessBuilder("/usr/bin/dscl", "/Search", "-read",
            "/Users/" + System.getProperty("user.name"), "UserShell").start();
        try {
            assertThat(query.waitFor(2, TimeUnit.SECONDS)).isTrue();
            assertThat(query.exitValue()).isZero();
            String output = new String(query.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            assertThat(output).startsWith("UserShell: ");
            String accountShell = output.substring("UserShell: ".length());
            var pending = new ArrayDeque<Runnable>();
            var received = new AtomicReference<LaunchSettings>();
            SwingUtilities.invokeAndWait(() -> JasperApplication.windowLauncher(pending::add,
                ConfigSnapshot::defaults, (path, settings) -> { received.set(settings); return null; })
                .launch(directory, (session, failure) -> assertThat(failure).isNull()));
            pending.remove().run();
            SwingUtilities.invokeAndWait(() -> { });
            assertThat(received.get().command()).containsExactly(accountShell, "-l");
            assertThat(received.get().environment()).containsEntry("SHELL", accountShell);
        } finally {
            query.destroyForcibly();
        }
    }
}
