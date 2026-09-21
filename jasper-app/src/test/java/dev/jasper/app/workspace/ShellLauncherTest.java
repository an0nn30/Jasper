package dev.jasper.app.workspace;

import dev.jasper.app.application.ApplicationTestSupport;
import dev.jasper.app.testsupport.ConfigTestSupport;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.launch.LaunchSettings;
import dev.jasper.app.application.JasperApplication;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.workspace.TerminalPane;
import dev.jasper.terminal.session.TerminalSession;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static dev.jasper.app.testsupport.ConfigTestSupport.snapshot;
import static org.assertj.core.api.Assertions.*;

class ShellLauncherTest {
    @TempDir Path directory;

    @Test @DisabledOnOs(OS.WINDOWS)
    void queuedRequestsCaptureSettingsAndPaneLabelsBeforeWorkerRuns() throws Exception {
        var current = new AtomicReference<>(snapshot("/first shell", List.of("old arg"), Map.of("VALUE", "old"), 12, 150, 45));
        Queue<Runnable> pending = new ArrayDeque<>(); var received = new ArrayList<LaunchSettings>();
        ShellLauncher launcher = new ShellLauncher(pending::add, () -> {
            assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
            return LaunchSettings.resolve(current.get(), "Linux", Map.of(), 150, 45);
        }, (path, settings) -> {
            assertThat(SwingUtilities.isEventDispatchThread()).isFalse();
            assertThat(path).isEqualTo(directory); received.add(settings); return shell(path);
        });
        TerminalPane[] panes = new TerminalPane[2];
        try {
            edt(() -> {
                panes[0] = new TerminalPane(directory, launcher); panes[0].start();
                current.set(snapshot("/next shell", List.of("new", "args"), Map.of("VALUE", "new"), 34, 90, 20));
                panes[1] = new TerminalPane(directory, launcher); panes[1].start();
                assertThat(panes[0].shellLabel()).isEqualTo("first shell");
                assertThat(panes[1].shellLabel()).isEqualTo("next shell");
                for (var pane : panes) pane.onReady = view -> assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
            });
            while (!pending.isEmpty()) pending.remove().run();
            edt(() -> {
                assertThat(panes[0].session()).isNotNull(); assertThat(panes[1].session()).isNotNull();
                assertThat(panes[0].shellLabel()).isEqualTo("first shell");
            });
            assertThat(received.get(0).command()).containsExactly("/first shell", "old arg");
            assertThat(received.get(0).environment()).containsEntry("VALUE", "old");
            assertThat(received.get(0).scrollback()).isEqualTo(12);
            assertThat(received.get(1).command()).containsExactly("/next shell", "new", "args");
            assertThat(received.get(1).environment()).containsEntry("VALUE", "new");
            assertThat(received.get(1).scrollback()).isEqualTo(34);
            assertThat(received).allSatisfy(s -> { assertThat(s.columns()).isEqualTo(150); assertThat(s.lines()).isEqualTo(45); });
        } finally { edt(() -> { for (var pane : panes) if (pane != null) pane.close(); }); }
    }

    @Test void applicationWindowLauncherFreezesGridButReadsSessionDefaultsForEachRequest() throws Exception {
        var current = new AtomicReference<>(snapshot("/initial", List.of(), Map.of(), 10, 150, 45));
        Queue<Runnable> pending = new ArrayDeque<>(); var received = new ArrayList<LaunchSettings>();
        var errors = new ArrayList<Throwable>();
        edt(() -> {
            var launcher = ApplicationTestSupport.windowLauncher(pending::add, () -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isTrue(); return current.get();
            }, (path, settings) -> {
                assertThat(SwingUtilities.isEventDispatchThread()).isFalse(); received.add(settings);
                throw new IllegalStateException("controlled launch boundary");
            });
            current.set(snapshot("/before-request", List.of("a"), Map.of("VALUE", "first"), 20, 90, 25));
            launcher.launch(directory, (session, failure) -> errors.add(failure));
            current.set(snapshot("/after-request", List.of("b"), Map.of("VALUE", "second"), 30, 80, 20));
            launcher.launch(directory, (session, failure) -> errors.add(failure));
            ApplicationTestSupport.windowLauncher(pending::add, current::get, (path, settings) -> {
                received.add(settings); throw new IllegalStateException("controlled launch boundary");
            }).launch(directory, (session, failure) -> errors.add(failure));
        });
        while (!pending.isEmpty()) pending.remove().run(); edt(() -> {});
        assertThat(received).hasSize(3);
        assertThat(received.get(0).command()).containsExactly("/before-request", "a");
        assertThat(received.get(0).environment()).containsEntry("VALUE", "first");
        assertThat(received.get(0).scrollback()).isEqualTo(20);
        assertThat(received.get(1).command()).containsExactly("/after-request", "b");
        assertThat(received.get(1).environment()).containsEntry("VALUE", "second");
        assertThat(received.get(1).scrollback()).isEqualTo(30);
        assertThat(received.subList(0, 2)).allSatisfy(s -> {
            assertThat(s.columns()).isEqualTo(150); assertThat(s.lines()).isEqualTo(45);
        });
        assertThat(received.get(2).columns()).isEqualTo(80); assertThat(received.get(2).lines()).isEqualTo(20);
        assertThat(errors).hasSize(3).allSatisfy(e -> assertThat(e).hasMessage("controlled launch boundary"));
    }

    @Test void captureSchedulingDirectoryAndStartFailuresReachEdt() throws Exception {
        var failure = new IllegalArgumentException("capture failed"); var errors = new ArrayList<Throwable>();
        BiConsumer<TerminalSession, Throwable> completion = (session, error) -> {
            assertThat(SwingUtilities.isEventDispatchThread()).isTrue(); assertThat(session).isNull(); errors.add(error);
        };
        Queue<Runnable> pending = new ArrayDeque<>();
        var settings = LaunchSettings.resolve(ConfigSnapshot.defaults(), "Linux", Map.of(), 150, 45);
        edt(() -> {
            var capture = new ShellLauncher(pending::add, () -> { throw failure; }, (p, s) -> { throw new AssertionError(); });
            assertThat(capture.launch(directory, completion)).isNotBlank(); assertThat(pending).isEmpty();
            new ShellLauncher(task -> { throw new RejectedExecutionException("closed"); }, () -> settings,
                (p, s) -> { throw new AssertionError(); }).launch(directory, completion);
            new ShellLauncher(pending::add, () -> settings, (p, s) -> { throw new AssertionError(); })
                .launch(directory.resolve("missing"), completion);
            new ShellLauncher(pending::add, () -> settings, (p, s) -> { throw new IllegalStateException("start failed"); })
                .launch(directory, completion);
        });
        while (!pending.isEmpty()) pending.remove().run(); edt(() -> {});
        assertThat(errors).hasSize(4); assertThat(errors.get(0)).isSameAs(failure);
        assertThat(errors.get(1)).isInstanceOf(RejectedExecutionException.class);
        assertThat(errors.get(2)).hasMessageContaining("Directory is unavailable");
        assertThat(errors.get(3)).hasMessage("start failed");
    }
}
