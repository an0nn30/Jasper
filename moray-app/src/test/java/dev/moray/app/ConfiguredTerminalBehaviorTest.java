package dev.moray.app;

import dev.moray.terminal.TerminalSession;
import dev.moray.terminal.TerminalView;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import static dev.moray.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

/** Real TOML, owner wiring and BEL delivery, with a controlled non-login child. */
@DisabledOnOs(OS.WINDOWS)
class ConfiguredTerminalBehaviorTest {
    @TempDir Path directory;
    ConfigService service;
    ConfigurationController controller;
    WindowContent owner;
    JPanel host;
    TerminalView view;
    TerminalSession session;
    final AtomicInteger sounds = new AtomicInteger();

    @BeforeEach void start() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[terminal]\nbell='none'\n");
        service = new ConfigService(file, false);
        var pending = new ArrayDeque<Runnable>();
        edt(() -> {
            var themes = new ThemeController();
            controller = new ConfigurationController(themes, service);
            var launcher = new ShellLauncher(pending::add, path -> {
                try {
                    return TerminalSession.start(List.of("/bin/sh", "-c", """
                        stty -echo
                        printf 'READY\n'
                        while IFS= read -r command; do
                            case "$command" in
                                bell) printf '\\007' ;;
                                *) : ;;
                            esac
                        done
                        """), System.getenv(), path, 80, 24, 100);
                } catch (Exception failure) { throw new CompletionException(failure); }
            }, "controlled-sh");
            owner = new WindowContent(launcher, directory, path -> {}, () -> {}, () -> {}, themes);
            controller.register(owner);
        });
        pending.remove().run();
        edt(() -> {
            view = owner.currentPane().view(); session = owner.currentPane().session();
            call("setBellSound", new Class<?>[] {Runnable.class}, (Runnable) () -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isTrue(); sounds.incrementAndGet();
            });
            // TerminalBellTest covers the 150ms callback; hold expiry here to test owner lifecycle deterministically.
            timer().setInitialDelay(Integer.MAX_VALUE);
            view.setSize(view.getPreferredSize());
            var root = new JRootPane();
            root.setContentPane(owner);
            owner.installRootBindings(root);
            host = new JPanel(new java.awt.BorderLayout());
            host.add(root);
            host.addNotify();
        });
        until(() -> view.find("READY", false, true).count() == 1);
    }

    @AfterEach void close() throws Exception {
        edt(() -> {
            if (host != null && host.isDisplayable()) host.removeNotify();
            if (owner != null) owner.close();
            if (controller != null) controller.close();
        });
        if (service != null) service.close();
    }

    @Test void reloadedBellModesUseRealBelAndCloseRejectsQueuedDeliveryAndStopsTimers() throws Exception {
        edt(this::bell);
        edt(() -> { assertThat(sounds).hasValue(0); assertThat(timer().isRunning()).isFalse(); });
        reload("[terminal]\nbell='sound'\n");
        edt(this::bell);
        edt(() -> assertThat(sounds).hasValue(1));
        reload("[terminal]\nbell='visual'\n");
        edt(this::bell);
        edt(() -> {
            assertThat(timer().isRunning()).isTrue();
            assertThat(timer().isRepeats()).isFalse();
            assertThat(field("visualBell")).isEqualTo(true);
        });
        reload("[terminal]\nbell='none'\n");
        edt(() -> { assertThat(timer().isRunning()).isFalse(); assertThat(field("visualBell")).isEqualTo(false); });
        reload("[terminal]\nbell='visual'\n");
        edt(this::bell);
        edt(() -> {
            assertThat(timer().isRunning()).isTrue();
            bell(); // queued on the EDT while the reader completes
            owner.close();
            assertThat(view.isDisplayable()).isFalse();
            assertThat(timer().isRunning()).isFalse();
        });
        edt(() -> {
            assertThat(field("visualBell")).isEqualTo(false);
            assertThat(timer().isRunning()).isFalse();
            assertThat(sounds).hasValue(1);
        });
        reload("[terminal]\nbell='sound'\n");
        edt(() -> assertThat(view.options().bell()).isEqualTo(dev.moray.terminal.BellMode.VISUAL));
    }

    private void reload(String text) throws Exception {
        Files.writeString(directory.resolve("config.toml"), text);
        service.reload().get(); edt(() -> {});
    }

    /** Await reader BEL while holding EDT so the queued attachment check is deterministic. */
    private void bell() {
        var received = new CountDownLatch(1);
        var probe = new TerminalSession.Listener() {
            @Override public void bell() { received.countDown(); }
        };
        session.addListener(probe);
        try {
            session.write("bell\n");
            assertThat(received.await(5, TimeUnit.SECONDS)).as("controlled child emitted BEL").isTrue();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt(); throw new AssertionError(failure);
        } finally { session.removeListener(probe); }
    }

    private javax.swing.Timer timer() { return (javax.swing.Timer) field("bellTimer"); }
    private Object field(String name) {
        try {
            var field = TerminalView.class.getDeclaredField(name);
            field.setAccessible(true); return field.get(view);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private void call(String name, Class<?>[] parameters, Object... args) {
        try {
            var method = TerminalView.class.getDeclaredMethod(name, parameters);
            method.setAccessible(true); method.invoke(view, args);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
