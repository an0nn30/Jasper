package dev.jasper.app.launch;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.TerminalConfig;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ShellProfileCatalogTest {
    private static final TerminalConfig.Shell DEFAULT = new TerminalConfig.Shell("", List.of());
    private final Queue<Runnable> background = new ArrayDeque<>();
    private final AtomicReference<List<WslDistro>> distros = new AtomicReference<>(List.of(new WslDistro("Ubuntu", 1, true)));
    private final AtomicReference<TerminalConfig.Shell> shell = new AtomicReference<>(DEFAULT);

    private ShellProfileCatalog catalog(String os) {
        return new ShellProfileCatalog(os, Map.of("ComSpec", "C:\\Windows\\system32\\cmd.exe"), shell::get, () -> {
            assertThat(SwingUtilities.isEventDispatchThread()).as("detection runs off the EDT").isFalse();
            return distros.get();
        }, () -> Optional.of("pwsh.exe"), background::add);
    }

    private static void edt(Runnable task) throws Exception { SwingUtilities.invokeAndWait(task); }

    private void detect() throws Exception {
        while (!background.isEmpty()) background.remove().run();
        edt(() -> { });
    }

    @Test void cygwinDiscoveryRefreshesAndDropsRemovedBash() throws Exception {
        var found = new AtomicReference<>(Optional.of("C:/owned/root/bin/bash.exe"));
        var catalog = new AtomicReference<ShellProfileCatalog>();
        edt(() -> {
            catalog.set(new ShellProfileCatalog("Windows 11", Map.of(), () -> DEFAULT, List::of, Optional::empty, found::get, background::add));
            catalog.get().refresh();
        });
        detect();
        edt(() -> assertThat(catalog.get().current()).extracting(ShellProfile::id).contains("cygwin"));
        found.set(Optional.empty()); edt(() -> catalog.get().refresh()); detect();
        edt(() -> { assertThat(catalog.get().current()).extracting(ShellProfile::id).doesNotContain("cygwin"); catalog.get().close(); });
    }

    @Test void detectionRunsOffTheEdtAndPublishesOnIt() throws Exception {
        var catalog = new AtomicReference<ShellProfileCatalog>();
        var changes = new AtomicInteger();
        edt(() -> {
            catalog.set(catalog("Windows 11"));
            catalog.get().onChanged(changes::incrementAndGet);
            assertThat(catalog.get().complete()).isFalse();
            assertThat(catalog.get().current()).extracting(ShellProfile::id).containsExactly("powershell", "cmd", "custom");
            assertThat(catalog.get().resolve(Optional.of("wsl:Ubuntu"), DEFAULT).profile().command())
                .as("a distribution is trusted until detection completes").containsExactly("wsl.exe", "-d", "Ubuntu");
            catalog.get().refresh();
        });
        assertThat(background).hasSize(1);
        detect();
        edt(() -> {
            assertThat(catalog.get().complete()).isTrue();
            assertThat(changes).hasValue(1);
            assertThat(catalog.get().current()).extracting(ShellProfile::id)
                .containsExactly("powershell", "pwsh", "cmd", "wsl:Ubuntu", "custom");
            assertThat(catalog.get().resolve(Optional.of("wsl:Gone"), DEFAULT).warning()).isPresent();
            shell.set(new TerminalConfig.Shell("C:\\msys64\\usr\\bin\\bash.exe", List.of("-l")));
            assertThat(catalog.get().current().getLast().command()).as("Custom follows the saved settings")
                .containsExactly("C:\\msys64\\usr\\bin\\bash.exe", "-l");
            catalog.get().refresh();
        });
        detect();
        edt(() -> assertThat(changes).as("nothing changed, nobody is told").hasValue(1));
        edt(() -> { catalog.get().refresh(); catalog.get().refresh(); });
        distros.set(List.of());
        background.remove().run();
        distros.set(List.of(new WslDistro("Ubuntu", 1, true)));
        background.remove().run();
        edt(() -> { });
        edt(() -> assertThat(changes).as("the older of two detections is dropped").hasValue(1));
        distros.set(List.of());
        edt(() -> catalog.get().refresh());
        detect();
        edt(() -> {
            assertThat(changes).hasValue(2);
            assertThat(catalog.get().current()).extracting(ShellProfile::id).doesNotContain("wsl:Ubuntu");
            catalog.get().close();
            catalog.get().refresh();
        });
        assertThat(background).as("a closed catalog detects nothing").isEmpty();
    }

    @Test void awaitingTheFirstDetectionIsBoundedOffTheEdtAndNeverWaitsForNothing() throws Exception {
        var catalog = new AtomicReference<ShellProfileCatalog>();
        edt(() -> catalog.set(catalog("Windows 11")));
        long started = System.nanoTime();
        assertThat(catalog.get().awaitDetection(Duration.ofSeconds(20))).as("nobody refreshed, so nothing will complete").isFalse();
        edt(() -> catalog.get().refresh());
        assertThat(catalog.get().awaitDetection(Duration.ofMillis(50))).as("still detecting: the wait times out").isFalse();
        var waiting = CompletableFuture.supplyAsync(() -> catalog.get().awaitDetection(Duration.ofSeconds(20)));
        detect();
        assertThat(waiting.get(20, TimeUnit.SECONDS)).isTrue();
        assertThat(catalog.get().awaitDetection(Duration.ZERO)).as("completed stays completed").isTrue();
        edt(() -> assertThatIllegalStateException().as("the EDT publishes detection, so it must never wait for it")
            .isThrownBy(() -> catalog.get().awaitDetection(Duration.ZERO)));
        var closed = new AtomicReference<ShellProfileCatalog>();
        edt(() -> { closed.set(catalog("Windows 11")); closed.get().refresh(); closed.get().close(); });
        background.clear();
        assertThat(closed.get().awaitDetection(Duration.ofSeconds(20))).as("a closed catalog completes nothing").isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(10));
    }

    @Test void aFailedDetectionCountsAsNoneAndEverythingIsEdtOnly() throws Exception {
        var catalog = new AtomicReference<ShellProfileCatalog>();
        edt(() -> catalog.set(new ShellProfileCatalog("Windows 11", Map.of(), () -> DEFAULT,
            () -> { throw new IllegalStateException("reg.exe broke"); }, () -> { throw new IllegalStateException("no disk"); },
            background::add)));
        edt(() -> catalog.get().refresh());
        detect();
        edt(() -> {
            assertThat(catalog.get().complete()).isTrue();
            assertThat(catalog.get().current()).extracting(ShellProfile::id).containsExactly("powershell", "cmd", "custom");
        });
        assertThatIllegalStateException().isThrownBy(() -> catalog.get().current());
        assertThatIllegalStateException().isThrownBy(() -> catalog.get().refresh());
        edt(() -> {
            var mac = catalog("Mac OS X");
            assertThat(mac.current()).extracting(ShellProfile::id).containsExactly("login", "custom");
        });
    }
}
