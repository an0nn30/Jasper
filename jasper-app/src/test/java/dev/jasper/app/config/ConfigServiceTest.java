package dev.jasper.app.config;

import dev.jasper.app.commands.ActionId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class ConfigServiceTest {
    @TempDir Path directory;

    @Test void missingInitialConfigUsesDefaultsWithoutCreatingDirectories() {
        Path file = directory.resolve("absent/config.toml");
        try (var service = new ConfigService(file, false)) {
            assertThat(service.initialState().snapshot()).isEqualTo(ConfigSnapshot.defaults());
            assertThat(service.initialState().diagnostics()).isEmpty();
            assertThat(service.initialState().file()).isEqualTo(file);
            assertThat(service.initialState().present()).isFalse();
            assertThat(service.macOs()).isFalse();
            assertThat(Files.exists(file.getParent())).isFalse();
        }
    }

    @Test void invalidReloadRetainsLastGoodThenRecoversAndDeletionRestoresDefaults() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[window]\ntab_height=44");
        try (var service = new ConfigService(file, true)) {
            assertThat(service.initialState().snapshot().tabHeight()).isEqualTo(44);
            assertThat(service.initialState().present()).isTrue();
            Files.writeString(file, "[window");
            var invalid = service.reload().get(5, TimeUnit.SECONDS);
            assertThat(invalid.snapshot().tabHeight()).isEqualTo(44);
            assertThat(invalid.present()).isTrue();
            assertThat(invalid.diagnostics()).isNotEmpty();
            Files.writeString(file, "window.tab_height='wrong type'");
            assertThat(service.reload().get(5, TimeUnit.SECONDS).snapshot().tabHeight()).isEqualTo(44);
            Files.writeString(file, "window.tab_height=50");
            var recovered = service.reload().get(5, TimeUnit.SECONDS);
            assertThat(recovered.snapshot().tabHeight()).isEqualTo(50);
            assertThat(recovered.diagnostics()).isEmpty();
            Files.delete(file);
            var missing = service.reload().get(5, TimeUnit.SECONDS);
            assertThat(missing.snapshot()).isEqualTo(ConfigSnapshot.defaults());
            assertThat(missing.present()).isFalse();
            assertThat(missing.diagnostics()).isEmpty();
            assertThat(service.initialState().snapshot().tabHeight()).isEqualTo(44);
        }
    }

    @Test void initialMalformedFileUsesDefaultsWithDiagnostics() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[window");
        try (var service = new ConfigService(file, true)) {
            assertThat(service.initialState().snapshot()).isEqualTo(ConfigSnapshot.defaults());
            assertError(service.initialState());
        }
    }

    @Test void invalidValuesApplyOtherValidFieldsAndRetainDiagnostics() throws Exception {
        Path file = directory.resolve("config.toml");
        try (var service = new ConfigService(file, false)) {
            Files.writeString(file, "window.tab_height=44\nfont.size=100\nfont.future=1");
            var state = service.reload().get(5, TimeUnit.SECONDS);
            assertThat(state.snapshot().tabHeight()).isEqualTo(44);
            assertThat(state.snapshot().fontSize()).isEqualTo(16f);
            assertThat(state.diagnostics()).extracting(ConfigDiagnostic::severity)
                .containsExactly(ConfigDiagnostic.Severity.ERROR, ConfigDiagnostic.Severity.WARNING);
        }
    }

    @Test void forcedReloadReadsSameTimestampAndSizeThatPollingSkips() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "window.tab_height=44");
        FileTime modified = Files.getLastModifiedTime(file);
        var worker = new PollWorker();
        var delivered = new ArrayList<ConfigService.State>();
        try (var service = new ConfigService(file, true, worker, Runnable::run)) {
            service.start(delivered::add);
            Files.writeString(file, "window.tab_height=45");
            Files.setLastModifiedTime(file, modified);
            worker.poll();
            assertThat(delivered).hasSize(1);
            var state = service.reload().get(5, TimeUnit.SECONDS);
            assertThat(state.snapshot().tabHeight()).isEqualTo(45);
            assertThat(delivered).hasSize(2);
        }
    }

    @Test void unchangedExplicitReloadPublishesOnceAndRetainsStaleAndCloseGuards() throws Exception {
        Path file = directory.resolve("config.toml");
        var queued = new ConcurrentLinkedQueue<Runnable>();
        var delivered = new ArrayList<ConfigService.State>();
        var service = new ConfigService(file, true, new PollWorker(), queued::add);
        try {
            service.start(delivered::add);
            queued.remove().run();
            var initial = service.initialState();
            assertThat(service.reload().get(5, TimeUnit.SECONDS)).isEqualTo(initial);
            assertThat(queued).hasSize(1);
            queued.remove().run();
            assertThat(delivered).containsExactly(initial, initial);
            service.reload().get(5, TimeUnit.SECONDS);
            Files.writeString(file, "window.tab_height=44");
            service.reload().get(5, TimeUnit.SECONDS);
            assertThat(queued).hasSize(2);
            queued.remove().run();
            assertThat(delivered).hasSize(2);
            queued.remove().run();
            assertThat(delivered).hasSize(3);
            assertThat(delivered.getLast().snapshot().tabHeight()).isEqualTo(44);
            service.reload().get(5, TimeUnit.SECONDS);
            assertThat(queued).hasSize(1);
            service.close();
            queued.remove().run();
            assertThat(delivered).hasSize(3);
        } finally {
            service.close();
        }
    }

    @Test void pollingPublishesChangedStatesOnlyIncludingDiagnosticsAndPresence() throws Exception {
        Path file = directory.resolve("config.toml");
        var worker = new PollWorker();
        var delivered = new ArrayList<ConfigService.State>();
        try (var service = new ConfigService(file, true, worker, Runnable::run)) {
            service.start(delivered::add);
            assertThat(worker.delaySeconds).isEqualTo(1);
            worker.poll();
            assertThat(delivered).hasSize(1);
            Files.writeString(file, "# defaults now in a file");
            worker.poll();
            assertThat(delivered).hasSize(2);
            assertThat(delivered.getLast().present()).isTrue();
            Files.writeString(file, "# different bytes but equal defaults\n");
            worker.poll();
            assertThat(delivered).hasSize(2);
            Files.writeString(file, "font.future=1");
            worker.poll();
            assertThat(delivered).hasSize(3);
            assertThat(delivered.getLast().diagnostics()).hasSize(1);
            worker.poll();
            assertThat(delivered).hasSize(3);
            service.openSettings(ignored -> {}).get(5, TimeUnit.SECONDS);
            assertThat(delivered).hasSize(3);
            Files.delete(file);
            worker.poll();
            assertThat(delivered).hasSize(4);
            assertThat(delivered.getLast().present()).isFalse();
        }
    }

    @Test void unreadableNonRegularFileRetainsLastGoodAndRecovers() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "window.tab_height=44");
        try (var service = new ConfigService(file, true)) {
            Files.delete(file);
            Files.createDirectory(file);
            var unreadable = service.reload().get(5, TimeUnit.SECONDS);
            assertThat(unreadable.snapshot().tabHeight()).isEqualTo(44);
            assertError(unreadable);
            Files.delete(file);
            Files.writeString(file, "window.tab_height=46");
            assertThat(service.reload().get(5, TimeUnit.SECONDS).snapshot().tabHeight()).isEqualTo(46);
        }
    }

    @Test void ioAccessFailureRetainsLastGoodEvenWhenConfigParentBecomesAFile() throws Exception {
        Path parent = Files.createDirectory(directory.resolve("parent"));
        Path file = parent.resolve("config.toml");
        Files.writeString(file, "window.tab_height=44");
        try (var service = new ConfigService(file, true)) {
            Path moved = directory.resolve("moved");
            Files.move(parent, moved);
            Files.writeString(parent, "user bytes");
            var failed = service.reload().get(5, TimeUnit.SECONDS);
            assertThat(failed.snapshot().tabHeight()).isEqualTo(44);
            assertError(failed);
            assertThat(Files.readString(parent)).isEqualTo("user bytes");
            Files.delete(parent);
            Files.move(moved, parent);
            var recovered = service.reload().get(5, TimeUnit.SECONDS);
            assertThat(recovered.snapshot().tabHeight()).isEqualTo(44);
            assertThat(recovered.diagnostics()).isEmpty();
        }
    }

    @Test void serviceRetainsParsePlatformForNonMacShortcutCompatibility() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "keybindings.split_down='cmd+shift+d'");
        try (var service = new ConfigService(file, false)) {
            var state = service.initialState();
            assertThat(state.diagnostics()).hasSize(1);
            assertThat(state.snapshot().keybindings()).isEmpty();
            assertThat(state.snapshot().bindings(service.macOs()).strokeFor(ActionId.SPLIT_DOWN))
                .contains(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_D,
                    java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK
                        | java.awt.event.InputEvent.ALT_DOWN_MASK));
            Files.writeString(file, "keybindings.split_down='alt+cmd+shift+d'");
            var recovered = service.reload().get(5, TimeUnit.SECONDS);
            assertThat(recovered.diagnostics()).isEmpty();
            assertThat(recovered.snapshot().keybindings()).containsEntry("split_down", "alt+cmd+shift+d");
        }
    }

    @Test void oneMiBIsAcceptedButExtraByteIsRejectedWithoutDiscardingLastGood() throws Exception {
        Path file = directory.resolve("config.toml");
        String prefix = "window.tab_height=44\n#";
        String bounded = prefix + "x".repeat(1024 * 1024 - prefix.length());
        Files.writeString(file, bounded);
        try (var service = new ConfigService(file, true)) {
            assertThat(service.initialState().snapshot().tabHeight()).isEqualTo(44);
            assertThat(service.initialState().diagnostics()).isEmpty();
            Files.writeString(file, bounded + "x");
            var oversized = service.reload().get(5, TimeUnit.SECONDS);
            assertThat(oversized.snapshot().tabHeight()).isEqualTo(44);
            assertError(oversized);
            assertThat(oversized.diagnostics().getFirst().message()).contains("1 MiB");
        }
    }

    @Test void malformedUtf8IsReportedWithoutAcceptingReplacementCharacters() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "window.tab_height=44");
        try (var service = new ConfigService(file, true)) {
            Files.write(file, new byte[]{'#', (byte) 0xC3, (byte) 0x28});
            var invalid = service.reload().get(5, TimeUnit.SECONDS);
            assertThat(invalid.snapshot().tabHeight()).isEqualTo(44);
            assertError(invalid);
        }
    }

    @Test void defaultPublisherDeliversOnEdt() throws Exception {
        var delivered = new CompletableFuture<ConfigService.State>();
        var onEdt = new AtomicBoolean();
        Path file = directory.resolve("config.toml");
        try (var service = new ConfigService(file, true)) {
            service.start(state -> {
                if (state.snapshot().tabHeight() == 44) {
                    onEdt.set(SwingUtilities.isEventDispatchThread());
                    delivered.complete(state);
                }
            });
            Files.writeString(file, "window.tab_height=44");
            service.reload().get(5, TimeUnit.SECONDS);
            assertThat(delivered.get(5, TimeUnit.SECONDS).snapshot().tabHeight()).isEqualTo(44);
            assertThat(onEdt).isTrue();
        }
    }

    @Test void staleAndPostCloseQueuedPublicationsAreDropped() throws Exception {
        Path file = directory.resolve("config.toml");
        var queued = new ConcurrentLinkedQueue<Runnable>();
        var delivered = new ArrayList<ConfigService.State>();
        var worker = new PollWorker();
        var service = new ConfigService(file, true, worker, queued::add);
        try {
            service.start(delivered::add);
            Files.writeString(file, "window.tab_height=44");
            service.reload().get(5, TimeUnit.SECONDS);
            assertThat(queued).hasSize(2);
            queued.remove().run();
            assertThat(delivered).isEmpty();
            queued.remove().run();
            assertThat(delivered).singleElement().satisfies(state -> assertThat(state.snapshot().tabHeight()).isEqualTo(44));
            Files.writeString(file, "window.tab_height=46");
            service.reload().get(5, TimeUnit.SECONDS);
            service.close();
            queued.remove().run();
            assertThat(delivered).hasSize(1);
            assertThat(worker.isShutdown()).isTrue();
        } finally {
            service.close();
        }
    }

    @Test void initialReadCannotRunOnEdt() throws Exception {
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try (var service = new ConfigService(directory.resolve("config.toml"), true)) {
                service.initialState(); // Construction must reject before doing I/O.
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        assertThat(failure.get()).isInstanceOf(IllegalStateException.class);
    }

    @Test void reloadFromEdtQueuesFileReadOnWorker() throws Exception {
        Path file = directory.resolve("config.toml");
        var worker = new PollWorker();
        try (var service = new ConfigService(file, true, worker, Runnable::run)) {
            var release = block(worker);
            try {
                Files.writeString(file, "window.tab_height=44");
                var request = new AtomicReference<CompletableFuture<ConfigService.State>>();
                SwingUtilities.invokeAndWait(() -> request.set(service.reload()));
                assertThat(request.get()).isNotDone();
                Files.writeString(file, "window.tab_height=46");
                release.countDown();
                assertThat(request.get().get(5, TimeUnit.SECONDS).snapshot().tabHeight()).isEqualTo(46);
            } finally {
                release.countDown();
            }
        }
    }

    @Test void settingsCreatesTemplateAndCallsOpenerOffEdtWithoutRewritingExistingFile() throws Exception {
        Path file = directory.resolve("nested/config.toml");
        var opened = new AtomicReference<Path>();
        var onEdt = new AtomicBoolean(true);
        try (var service = new ConfigService(file, false)) {
            var request = new AtomicReference<CompletableFuture<Path>>();
            SwingUtilities.invokeAndWait(() -> request.set(service.openSettings(path -> {
                opened.set(path);
                onEdt.set(SwingUtilities.isEventDispatchThread());
            })));
            assertThat(request.get().get(5, TimeUnit.SECONDS)).isEqualTo(file);
            assertThat(opened).hasValue(file);
            assertThat(onEdt).isFalse();
            assertThat(Files.readString(file)).isEqualTo(ConfigTemplate.text(false));
            assertThat(service.reload().get(5, TimeUnit.SECONDS).present()).isTrue();
            Files.writeString(file, "window.tab_height=48");
            service.openSettings(opened::set).get(5, TimeUnit.SECONDS);
            assertThat(Files.readString(file)).isEqualTo("window.tab_height=48");
            assertThat(service.reload().get(5, TimeUnit.SECONDS).snapshot().tabHeight()).isEqualTo(48);
        }
    }

    @Test void settingsReloadsBeforeCompletingAndEditorFailureDoesNotRollbackConfig() throws Exception {
        Path file = directory.resolve("nested/config.toml");
        var delivered = new ArrayList<ConfigService.State>();
        try (var service = new ConfigService(file, true, new PollWorker(), Runnable::run)) {
            service.start(delivered::add);
            var failed = service.openSettings(path -> { throw new IllegalStateException("Editor unavailable"); });
            assertThatThrownBy(() -> failed.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
            assertThat(delivered).hasSize(2);
            assertThat(delivered.getLast().present()).isTrue();
            assertThat(delivered.getLast().diagnostics()).isEmpty();
            assertThat(Files.readString(file)).isEqualTo(ConfigTemplate.text(true));
        }
    }

    @Test void settingsCreationFailureCompletesExceptionallyAndDoesNotCallOpener() throws Exception {
        Path parent = directory.resolve("file");
        Files.writeString(parent, "original");
        var opened = new AtomicBoolean();
        try (var service = new ConfigService(parent.resolve("config.toml"), true)) {
            var failed = service.openSettings(path -> opened.set(true));
            assertThatThrownBy(() -> failed.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(java.io.IOException.class);
            assertThat(opened).isFalse();
            assertThat(Files.readString(parent)).isEqualTo("original");
        }
    }

    @Test void closeSettlesQueuedRequestsAndRejectsNewRequestsWithoutOpeningAnything() throws Exception {
        Path file = directory.resolve("config.toml");
        var worker = new PollWorker();
        var service = new ConfigService(file, true, worker, Runnable::run);
        var release = block(worker);
        var opened = new AtomicBoolean();
        try {
            var reload = service.reload();
            var settings = service.openSettings(path -> opened.set(true));
            service.close();
            for (var future : List.of(reload, settings, service.reload(), service.openSettings(path -> opened.set(true)))) {
                assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
            }
            assertThatIllegalStateException().isThrownBy(() -> service.start(state -> { }));
            assertThat(opened).isFalse();
            assertThat(Files.exists(file)).isFalse();
            assertThat(worker.isShutdown()).isTrue();
        } finally {
            release.countDown();
            service.close();
        }
    }

    @Test void startAcceptsExactlyOneListenerAndStateCopiesDiagnostics() {
        Path file = directory.resolve("config.toml");
        try (var service = new ConfigService(file, true, new PollWorker(), Runnable::run)) {
            service.start(state -> { });
            assertThatIllegalStateException().isThrownBy(() -> service.start(state -> { }));
        }
        var diagnostics = new ArrayList<ConfigDiagnostic>();
        var state = new ConfigService.State(ConfigSnapshot.defaults(), diagnostics, file, false);
        diagnostics.add(new ConfigDiagnostic(ConfigDiagnostic.Severity.ERROR, file, 0, 0, "", "Failure"));
        assertThat(state.diagnostics()).isEmpty();
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> state.diagnostics().clear());
    }

    private static void assertError(ConfigService.State state) {
        assertThat(state.present()).isTrue();
        assertThat(state.diagnostics()).isNotEmpty().allSatisfy(diagnostic -> {
            assertThat(diagnostic.severity()).isEqualTo(ConfigDiagnostic.Severity.ERROR);
            assertThat(diagnostic.file()).isEqualTo(state.file());
            assertThat(diagnostic.message()).isNotBlank();
        });
    }

    private static CountDownLatch block(PollWorker worker) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        worker.execute(() -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        return release;
    }

    /** Only scheduling time is controlled; file I/O and request execution use the real single worker. */
    private static final class PollWorker extends ScheduledThreadPoolExecutor {
        private Runnable poll;
        private long delaySeconds;

        PollWorker() { super(1); }

        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            poll = command;
            delaySeconds = unit.toSeconds(delay);
            return super.scheduleWithFixedDelay(command, 1, 1, TimeUnit.DAYS);
        }

        void poll() throws Exception {
            submit(poll).get(5, TimeUnit.SECONDS);
        }
    }
}
