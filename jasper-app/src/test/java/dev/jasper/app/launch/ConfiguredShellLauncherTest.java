package dev.jasper.app.launch;

import dev.jasper.app.config.ConfigSnapshot;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.testsupport.ConfigTestSupport.snapshot;
import static org.assertj.core.api.Assertions.assertThat;

class ConfiguredShellLauncherTest {
    @TempDir Path directory;

    @Test void successiveStartsRefreshAccountShellAndIntegrationOffEdt() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        var received = new ArrayList<LaunchSettings>();
        var labels = new ArrayList<String>();
        var failures = new ArrayList<Throwable>();
        var account = new AtomicReference<>("/bin/zsh");
        var lookups = new AtomicInteger();
        var launcher = ShellLauncher.configured(pending::add, ConfigSnapshot::defaults, "Mac OS X",
            Map.of("SHELL", "/old/bash"), 150, 45, directory,
            (path, settings) -> { received.add(settings); return null; }, () -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isFalse();
                lookups.incrementAndGet();
                return Optional.of(account.get());
            });
        for (String shell : List.of("/bin/zsh", "/bin/bash")) {
            SwingUtilities.invokeAndWait(() -> {
                launcher.sessionDefaults();
                launcher.launch(directory, (session, failure) -> {
                    assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
                    failures.add(failure);
                }, label -> {
                    assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
                    labels.add(label);
                });
            });
            // A change after admission must still be seen at the actual start.
            account.set(shell);
            pending.remove().run();
            SwingUtilities.invokeAndWait(() -> { });
        }
        assertThat(lookups.get()).isEqualTo(2);
        assertThat(failures).containsExactly(null, null);
        assertThat(labels).containsExactly("zsh", "bash");
        assertThat(received.get(0).command()).containsExactly("/bin/zsh", "-l");
        assertThat(received.get(0).environment()).containsEntry("SHELL", "/bin/zsh")
            .containsEntry("ZDOTDIR", directory.resolve("zsh").toString()).doesNotContainKey("JASPER_LOGIN_SHELL");
        assertThat(received.get(1).command()).containsExactly("/bin/bash", "--rcfile", directory.resolve("bash/rc.bash").toString());
        assertThat(received.get(1).environment()).containsEntry("SHELL", "/bin/bash")
            .containsEntry("JASPER_LOGIN_SHELL", "1").doesNotContainKey("ZDOTDIR");
    }

    @Test void queuedLaunchKeepsConfigAndEnvironmentOverrides() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        var received = new AtomicReference<LaunchSettings>();
        var current = new AtomicReference<>(snapshot("/custom/shell", List.of("arg"), Map.of("SHELL", "/env/override"), 12, 150, 45));
        var launcher = ShellLauncher.configured(pending::add, () -> {
            assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
            return current.get();
        }, "Mac OS X", Map.of("SHELL", "/old/bash"), 150, 45, directory,
            (path, settings) -> { received.set(settings); return null; }, () -> Optional.of("/bin/zsh"));
        SwingUtilities.invokeAndWait(() -> launcher.launch(directory, (session, failure) -> { }));
        current.set(ConfigSnapshot.defaults());
        pending.remove().run();
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(received.get().command()).containsExactly("/custom/shell", "arg");
        assertThat(received.get().environment()).containsEntry("SHELL", "/env/override").doesNotContainKey("ZDOTDIR");
        assertThat(received.get().scrollback()).isEqualTo(12);
    }

    @Test void shellEnvironmentOverlayDoesNotChooseTheDefaultCommand() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        var received = new AtomicReference<LaunchSettings>();
        var config = snapshot("", List.of("-i"), Map.of("SHELL", "/env/override"), 12, 150, 45);
        var launcher = ShellLauncher.configured(pending::add, () -> config, "Mac OS X",
            Map.of("SHELL", "/old/bash"), 150, 45, directory,
            (path, settings) -> { received.set(settings); return null; }, () -> Optional.of("/bin/zsh"));
        SwingUtilities.invokeAndWait(() -> launcher.launch(directory, (session, failure) -> { }));
        pending.remove().run();
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(received.get().command()).containsExactly("/bin/zsh", "-l", "-i");
        assertThat(received.get().environment()).containsEntry("SHELL", "/env/override")
            .containsEntry("ZDOTDIR", directory.resolve("zsh").toString());
    }

    @Test void unavailableAccountFallsBackAndOtherPlatformsNeverQueryMac() {
        assertThat(DefaultShell.command("Mac OS X", DefaultShell.loginEnvironment("Mac OS X",
            Map.of("SHELL", "/bin/bash"), Optional::empty))).containsExactly("/bin/bash", "-l");
        assertThat(DefaultShell.command("Mac OS X", DefaultShell.loginEnvironment("Mac OS X",
            Map.of(), Optional::empty))).containsExactly("/bin/zsh", "-l");
        for (String os : List.of("Linux", "Windows 11")) {
            var inherited = Map.of("SHELL", "/custom/fish");
            assertThat(DefaultShell.loginEnvironment(os, inherited, () -> { throw new AssertionError("macOS lookup"); }))
                .isEqualTo(inherited);
        }
    }
}
