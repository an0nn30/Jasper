package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SystemAppearanceTest {
    @Test void coalescesEventsAndStopsQueuedDeliveryAfterClose() {
        var queue = new ArrayDeque<Runnable>();
        var monitor = new SystemAppearance(SystemAppearance.Observation.dark(), queue::add);
        var seen = new ArrayList<SystemAppearance.Observation>();
        Runnable unsubscribe = monitor.subscribe(seen::add);
        monitor.observed(SystemAppearance.Observation.light());
        monitor.observed(SystemAppearance.Observation.dark());
        monitor.observed(SystemAppearance.Observation.light());
        assertThat(queue).hasSize(1);
        queue.remove().run();
        assertThat(seen).containsExactly(SystemAppearance.Observation.light());
        monitor.observed(SystemAppearance.Observation.light());
        assertThat(queue).isEmpty();
        monitor.observed(SystemAppearance.Observation.dark());
        monitor.close(); queue.remove().run();
        assertThat(seen).hasSize(1);
        unsubscribe.run(); monitor.close();
    }
    @Test void unavailableIsDistinctAndCanRecoverWithListenerIsolation() {
        var queue = new ArrayDeque<Runnable>();
        var monitor = new SystemAppearance(SystemAppearance.Observation.unavailable("Not supported"), queue::add);
        monitor.subscribe(value -> { throw new IllegalStateException("listener"); });
        var seen = new ArrayList<SystemAppearance.Observation>(); monitor.subscribe(seen::add);
        monitor.observed(SystemAppearance.Observation.dark()); queue.remove().run();
        assertThat(seen).containsExactly(SystemAppearance.Observation.dark());
        assertThat(monitor.current().preference()).isEqualTo(SystemAppearance.Preference.DARK);
        monitor.close();
    }
    @Test void headlessDefaultStartupDoesNotCreateNativeDesktopChildren() {
        assertThat(java.awt.GraphicsEnvironment.isHeadless()).isTrue();
        var before = ProcessHandle.current().descendants().map(ProcessHandle::pid).toList();
        try (var monitor = SystemAppearance.open()) {
            assertThat(ProcessHandle.current().descendants().map(ProcessHandle::pid).toList()).containsExactlyInAnyOrderElementsOf(before);
            assertThat(monitor.current().preference()).isEqualTo(SystemAppearance.Preference.UNAVAILABLE);
            assertThat(monitor.current().reason()).contains("headless");
        }
    }
    @Test void injectedLinuxOpenFeedsPortalIntoThePolicyMonitorAndRemovesSubscription() {
        try (var f = new DesktopProcessTest.Fixture()) {
            var desktop = DesktopColorsTest.service(f, new ArrayList<>(), "echo");
            var queue = new ArrayDeque<Runnable>();
            var monitor = SystemAppearance.open(desktop, "Linux", false, queue::add);
            f.await(() -> desktop.current().portal().appearance() == DesktopObservation.Polarity.DARK);
            f.time.advance(100, java.util.concurrent.TimeUnit.MILLISECONDS); f.pump();
            while (!queue.isEmpty()) queue.remove().run();
            assertThat(monitor.current()).isEqualTo(SystemAppearance.Observation.dark());
            monitor.close(); f.children.getFirst().destroyForcibly();
            f.await(() -> desktop.current().portal().appearance() == DesktopObservation.Polarity.UNKNOWN);
            f.time.advance(100, java.util.concurrent.TimeUnit.MILLISECONDS); f.pump();
            assertThat(queue).isEmpty(); assertThat(monitor.current()).isEqualTo(SystemAppearance.Observation.dark());
            desktop.close(); f.pump();
        }
    }
    @Test void developmentLibraryDoesNotBroadenThePackagedNotificationLoader() {
        assertThat(MacNative.libraryPath("/tmp/jasper/bin/jasper-app")).isNull();
        assertThat(MacAppearance.library("/tmp/dev/libappearance.dylib", null, null)).isEqualTo(java.nio.file.Path.of("/tmp/dev/libappearance.dylib"));
        assertThat(MacAppearance.library(null, "/Applications/Jasper.app/Contents/MacOS/Jasper", null)).isEqualTo(java.nio.file.Path.of("/Applications/Jasper.app/Contents/app/libjasper-appearance.dylib"));
        assertThat(MacAppearance.library(null, null, java.nio.file.Path.of("/tmp/dist/lib/jasper-app.jar"))).isEqualTo(java.nio.file.Path.of("/tmp/dist/lib/libjasper-appearance.dylib"));
    }
}
