package dev.jasper.app.platform;

import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static dev.jasper.app.platform.LinuxTrayXcbTest.nativeOnly;
import static dev.jasper.app.platform.LinuxX11TestServer.await;
import static org.assertj.core.api.Assertions.*;

class LinuxLegacyTrayTest {
    @Test void menuRectangleCannotBeMutatedAcrossThreads() {
        var bounds = new Rectangle(10, 20, 24, 40);
        var screen = new Rectangle(0, 0, 1280, 800);
        var request = new LinuxLegacyTray.MenuRequest(1, bounds, screen, true, ignored -> {}, () -> true);
        screen.width = 1; request.rootScreenBounds().height = 1;
        assertThat(request.rootScreenBounds()).isEqualTo(new Rectangle(0, 0, 1280, 800));
        bounds.width = 500; request.rootBounds().height = 500;
        assertThat(request.rootBounds()).isEqualTo(new Rectangle(10, 20, 24, 40));
    }
    @Test void subscribesBeforeInitialOwnerSnapshot() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem()) {
            var host = server.new TrayHost(); host.owner = 0;
            host.beforeSnapshot = () -> host.owner = 0x123;
            var available = new CopyOnWriteArrayList<Boolean>();
            try (var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, ignored -> {}, () -> {})) {
                tray.start(); await(() -> available.contains(true), "current owner");
                assertThat(host.observed.indexOf("root-select")).isLessThan(host.observed.indexOf("snapshot"));
                assertThat(available.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
            }
        }
    }
    @Test void explicitProbeRefreshesOwnerAndDisabledDiscoveryDoesNotProbe() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem()) {
            var host = server.new TrayHost(); host.owner = 0;
            var available = new CopyOnWriteArrayList<Boolean>();
            try (var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, ignored -> {}, () -> {})) {
                tray.start(); await(() -> host.observed.contains("snapshot"), "initial snapshot");
                host.owner = 0x123; // Selection changes without a MANAGER event require the runtime's snapshot probe.
                tray.probe(); await(() -> available.contains(true), "explicit fresh snapshot");
                tray.discoveryEnabled(false);
                int snapshots = java.util.Collections.frequency(host.observed, "snapshot");
                for (int i = 0; i < 10000; i++) tray.probe();
                Thread.sleep(150);
                assertThat(java.util.Collections.frequency(host.observed, "snapshot")).isEqualTo(snapshots);
                host.owner = 0;
                tray.discoveryEnabled(true); tray.probe();
                await(() -> !available.getLast(), "fresh snapshot on resume");
            }
        }
    }

    @Test void idlePollBudgetIsNotTheNativeOperationDeadline() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem()) {
            var host = server.new TrayHost(); var available = new CopyOnWriteArrayList<Boolean>();
            try (var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, ignored -> {}, () -> {})) {
                tray.start(); await(() -> available.contains(true), "host"); assertThat(tray.install(ignored -> {})).isTrue();
                Object cancel = LinuxTrayXcbTest.field(tray, "cancellation"); Object bindings = LinuxTrayXcbTest.field(cancel, "bindings");
                var member = bindings.getClass().getDeclaredField("xcb"); member.setAccessible(true); Object original = member.get(bindings);
                var delayed = new java.util.concurrent.atomic.AtomicBoolean(); var polls = new AtomicInteger();
                var returned = new CountDownLatch(1); int baseline = available.size();
                member.set(bindings, java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {member.getType()}, (proxy, method, args) -> {
                    if (method.getName().equals("xcb_poll_for_event")) {
                        polls.incrementAndGet();
                        if (delayed.compareAndSet(false, true)) {
                            try { Thread.sleep(150); return method.invoke(original, args); }
                            finally { returned.countDown(); }
                        }
                    }
                    return method.invoke(original, args);
                }));
                assertThat(returned.await(2, TimeUnit.SECONDS)).isTrue();
                await(() -> polls.get() >= 2 || available.size() > baseline, "next event drain or erroneous host invalidation");
                assertThat(available).hasSize(baseline);
                assertThat(LinuxTrayXcbTest.field(tray, "installed")).isEqualTo(true);
                assertThat(host.docks).isEqualTo(1);
            }
        }
    }

    @Test void requiresHandshakeReparentAndCurrentOwner() throws Exception {
        nativeOnly();
        for (boolean handshake : List.of(false, true)) for (boolean reparent : List.of(false, true)) {
            try (var server = LinuxX11TestServer.filesystem()) {
                var host = server.new TrayHost(); host.handshake = handshake; host.reparent = reparent;
                var available = new CopyOnWriteArrayList<Boolean>();
                try (var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, ignored -> {}, () -> {})) {
                    tray.start(); await(() -> available.contains(true), "owner");
                    assertThat(tray.install(ignored -> {})).isEqualTo(handshake && reparent);
                    if (!handshake || !reparent) {
                        assertThat(host.mapped).isFalse();
                        await(() -> host.icon == 0 || server.connectionEnded, "fixture observed confirmed destruction or disconnected client");
                    } else { assertThat(host.mapped).isTrue(); tray.removeAndAwait(); assertThat(host.icon).isZero(); }
                }
            }
        }
    }
    @Test void rootEmbedderNotifyCannotMapAnUndockedRootChild() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem()) {
            var host = server.new TrayHost(); host.reparent = false; host.announcedEmbedder = 0x100;
            var available = new CopyOnWriteArrayList<Boolean>();
            try (var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, ignored -> {}, () -> {})) {
                tray.start(); await(() -> available.contains(true), "owner");
                assertThat(tray.install(ignored -> {})).isFalse();
                assertThat(host.docks).isEqualTo(1); assertThat(host.mapped).isFalse();
                assertThat(server.packets.stream().noneMatch(packet -> packet[0] == 8)).isTrue();
                await(() -> host.icon == 0 || server.connectionEnded, "root-parent icon cleanup confirmed");
            }
        }
    }
    @Test void ownerLossDuringDockNeverLeavesPendingIcon() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem()) {
            var host = server.new TrayHost(); host.handshake = false;
            var available = new CopyOnWriteArrayList<Boolean>();
            try (var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, ignored -> {}, () -> {})) {
                tray.start(); await(() -> available.contains(true), "owner");
                var installed = new AtomicReference<Boolean>();
                var install = Thread.ofPlatform().start(() -> installed.set(tray.install(ignored -> {})));
                await(() -> host.docks == 1, "dock received"); assertThat(host.mapped).isFalse(); host.owner(0);
                install.join(2500); assertThat(install.isAlive()).isFalse(); assertThat(installed.get()).isFalse();
                await(() -> host.icon == 0 || server.connectionEnded, "fixture observed confirmed destruction or disconnected client");
            }
        }
    }
    @Test void unconfirmedNativeCleanupThrowsAndRefusesAnotherInstall() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem()) {
            var host = server.new TrayHost(); var available = new CopyOnWriteArrayList<Boolean>();
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, ignored -> {}, () -> {});
            Thread remover = null; var failure = new AtomicReference<Throwable>();
            try {
                tray.start(); await(() -> available.contains(true), "owner"); assertThat(tray.install(ignored -> {})).isTrue();
                Object cancellation = LinuxTrayXcbTest.field(tray, "cancellation"); Object bindings = LinuxTrayXcbTest.field(cancellation, "bindings");
                var member = bindings.getClass().getDeclaredField("xcb"); member.setAccessible(true); Object original = member.get(bindings);
                member.set(bindings, java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {member.getType()}, (proxy, method, args) -> {
                    if (method.getName().equals("xcb_destroy_window")) {
                        entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("held native mutation not released");
                    }
                    try { return method.invoke(original, args); }
                    catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
                }));
                remover = Thread.ofPlatform().start(() -> { try { tray.removeAndAwait(); } catch (Throwable error) { failure.set(error); } });
                assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue(); remover.join(2500);
                assertThat(remover.isAlive()).isFalse(); assertThat(failure.get()).isInstanceOf(IllegalStateException.class);
                assertThatThrownBy(() -> tray.install(ignored -> {})).isInstanceOf(IllegalStateException.class);
                assertThat(host.docks).isEqualTo(1);
                long before = System.nanoTime(); tray.close(); assertThat(System.nanoTime() - before).isLessThan(TimeUnit.MILLISECONDS.toNanos(100));
            } finally {
                release.countDown(); tray.close(); if (remover != null) remover.join(2500);
                var worker = (Thread)LinuxTrayXcbTest.field(tray, "worker"); worker.join(2500); assertThat(worker.isAlive()).isFalse();
            }
        }
    }
    @Test void unavailableInstallWaitsForHeldOldIconDestruction() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem()) {
            var host = server.new TrayHost(); var available = new CopyOnWriteArrayList<Boolean>();
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, ignored -> {}, () -> {});
            try {
                tray.start(); await(() -> available.contains(true), "owner"); assertThat(tray.install(ignored -> {})).isTrue();
                Object cancellation = LinuxTrayXcbTest.field(tray, "cancellation"); Object bindings = LinuxTrayXcbTest.field(cancellation, "bindings");
                var member = bindings.getClass().getDeclaredField("xcb"); member.setAccessible(true); Object original = member.get(bindings);
                member.set(bindings, java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {member.getType()}, (proxy, method, args) -> {
                    if (method.getName().equals("xcb_destroy_window")) {
                        entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("held native mutation not released");
                    }
                    try { return method.invoke(original, args); }
                    catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
                }));
                host.owner(0); assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
                assertThat(available.getLast()).isFalse(); assertThat(host.icon).isNotZero();
                long started = System.nanoTime();
                assertThatThrownBy(() -> tray.install(ignored -> {})).isInstanceOf(IllegalStateException.class);
                assertThat(System.nanoTime() - started).isLessThan(TimeUnit.MILLISECONDS.toNanos(2250));
                assertThat(host.docks).isEqualTo(1);
                long before = System.nanoTime(); tray.close(); assertThat(System.nanoTime() - before).isLessThan(TimeUnit.MILLISECONDS.toNanos(100));
            } finally {
                release.countDown(); tray.close();
                var worker = (Thread)LinuxTrayXcbTest.field(tray, "worker"); worker.join(2500); assertThat(worker.isAlive()).isFalse();
            }
        }
    }
    @Test void hostLossBeforeQueuedActionDropsIt() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem()) {
            var host = server.new TrayHost(); var available = new CopyOnWriteArrayList<Boolean>();
            var actions = new AtomicInteger(); var held = new CountDownLatch(1); var release = new CountDownLatch(1);
            try (var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, ignored -> {}, () -> {})) {
                tray.start(); await(() -> available.contains(true), "owner"); assertThat(tray.install(actions::addAndGet)).isTrue();
                SwingUtilities.invokeLater(() -> { held.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
                assertThat(held.await(2, TimeUnit.SECONDS)).isTrue();
                host.input(5, 1, 2, 3, 0, 100); host.owner(0);
                await(() -> available.getLast().equals(false), "owner lost");
                release.countDown(); SwingUtilities.invokeAndWait(() -> {}); assertThat(actions.get()).isZero();
            } finally { release.countDown(); }
        }
    }
    @Test void inputAndTraversalUseCurrentEpoch() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem()) {
            var host = server.new TrayHost(); var available = new CopyOnWriteArrayList<Boolean>();
            var actions = new AtomicInteger(); var menus = new CopyOnWriteArrayList<LinuxLegacyTray.MenuRequest>();
            try (var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, menus::add, () -> {})) {
                tray.start(); await(() -> available.contains(true), "owner"); assertThat(tray.install(actions::addAndGet)).isTrue();
                host.input(5, 1, 2, 3, 0, 100); host.input(5, 1, 2, 3, 0, 100);
                host.input(4, 3, 2, 3, 0, 101); host.input(5, 3, 2, 3, 0, 101); host.input(4, 1, 2, 3, 0, 102);
                host.input(5, 2, 2, 3, 0, 103); host.input(5, 4, 2, 3, 0, 104); host.input(5, 1, 24, 3, 0, 105);
                host.input(2, 36, 0, 0, 0, 106); host.input(2, 65, 0, 0, 0, 107);
                host.input(2, 135, 0, 0, 0, 108); host.input(2, 76, 0, 0, 1, 109);
                host.input(2, 23, 0, 0, 0, 110); host.input(2, 23, 0, 0, 1, 111);
                await(() -> actions.get() == 3 && menus.size() == 3 && host.observed.contains("xembed-7"), "input callbacks");
                assertThat(host.observed).contains("xembed-6", "xembed-7");
                assertThat(menus.getFirst().rootBounds()).isEqualTo(new Rectangle(100, 50, 24, 40));
                tray.removeAndAwait(); assertThat(menus.getFirst().valid().getAsBoolean()).isFalse();
                menus.getFirst().action().accept(1); SwingUtilities.invokeAndWait(() -> {}); assertThat(actions.get()).isEqualTo(3);
            }
        }
    }
    @Test void embedderUnmapInvalidatesMenusAndPausesInputUntilRemap() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem()) {
            var host = server.new TrayHost(); var available = new CopyOnWriteArrayList<Boolean>();
            var menus = new CopyOnWriteArrayList<LinuxLegacyTray.MenuRequest>(); var actions = new AtomicInteger();
            var canceled = new CountDownLatch(1); var watching = new java.util.concurrent.atomic.AtomicBoolean();
            try (var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, menus::add,
                    () -> { if (watching.get()) canceled.countDown(); })) {
                tray.start(); await(() -> available.contains(true), "owner"); assertThat(tray.install(actions::addAndGet)).isTrue();
                host.input(4, 3, 2, 3, 0, 100); host.input(5, 3, 2, 3, 0, 100); await(() -> menus.size() == 1, "menu before hide"); watching.set(true);
                host.embedderMapped(false); assertThat(canceled.await(1, TimeUnit.SECONDS)).isTrue();
                assertThat(menus.getFirst().valid().getAsBoolean()).isFalse();
                host.input(5, 1, 2, 3, 0, 101); host.embedderMapped(true); host.input(5, 1, 2, 3, 0, 102);
                await(() -> actions.get() > 0, "fresh remapped input");
                menus.getFirst().action().accept(1); SwingUtilities.invokeAndWait(() -> {});
                assertThat(actions.get()).isEqualTo(1); tray.removeAndAwait();
            }
        }
    }
    @Test void mappedPropertyHideNeverRevivesOldMenuOrQueuedAction() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem()) {
            var host = server.new TrayHost(); var available = new CopyOnWriteArrayList<Boolean>();
            var menus = new CopyOnWriteArrayList<LinuxLegacyTray.MenuRequest>(); var actions = new AtomicInteger(); var cancels = new AtomicInteger();
            var held = new CountDownLatch(1); var release = new CountDownLatch(1);
            try (var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, menus::add, cancels::incrementAndGet)) {
                tray.start(); await(() -> available.contains(true), "owner"); assertThat(tray.install(actions::addAndGet)).isTrue();
                host.input(4, 3, 2, 3, 0, 100); host.input(5, 3, 2, 3, 0, 100); await(() -> menus.size() == 1, "menu before property hide");
                var oldMenu = menus.getFirst(); int before = cancels.get();
                SwingUtilities.invokeLater(() -> { held.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
                assertThat(held.await(1, TimeUnit.SECONDS)).isTrue(); oldMenu.action().accept(1);
                host.mappedProperty(false); await(() -> !oldMenu.valid().getAsBoolean(), "hidden property applied");
                int paints = host.paints; host.mappedProperty(true); await(() -> host.paints > paints, "remapped property painted");
                assertThat(oldMenu.valid().getAsBoolean()).isFalse(); assertThat(cancels.get()).isGreaterThan(before);
                release.countDown(); SwingUtilities.invokeAndWait(() -> {}); assertThat(actions.get()).isZero();
                oldMenu.action().accept(1); SwingUtilities.invokeAndWait(() -> {}); assertThat(actions.get()).isZero();
                host.input(5, 1, 2, 3, 0, 101); await(() -> actions.get() == 1, "fresh property-remapped input"); tray.removeAndAwait();
            } finally { release.countDown(); }
        }
    }
    @Test void modalityDoesNotSurviveRemovalReplacementOrReconnect() throws Exception {
        nativeOnly();
        for (int transition = 0; transition < 3; transition++) {
            try (var server = LinuxX11TestServer.filesystem()) {
                var host = server.new TrayHost(); var available = new CopyOnWriteArrayList<Boolean>(); var actions = new AtomicInteger();
                try (var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, ignored -> {}, () -> {})) {
                    tray.start(); await(() -> available.contains(true), "owner"); assertThat(tray.install(actions::addAndGet)).isTrue();
                    int paints = host.paints; host.xembed(10); host.expose(); await(() -> host.paints > paints, "modality event processed before redraw");
                    assertThat(LinuxTrayXcbTest.field(tray, "modality")).isEqualTo(true);
                    int observations = available.size();
                    if (transition == 0) tray.removeAndAwait();
                    else {
                        if (transition == 1) host.owner(0x125); else server.drop();
                        await(() -> available.size() >= observations + 2 && available.getLast(), "new host publication available");
                    }
                    assertThat(tray.install(actions::addAndGet)).isTrue();
                    host.input(5, 1, 2, 3, 0, 101); await(() -> actions.get() == 1, "replacement input without old-host modality off");
                    assertThat(LinuxTrayXcbTest.field(tray, "modality")).isEqualTo(false); tray.removeAndAwait();
                }
            }
        }
    }
    @Test void configuredPixelsDoNotUseJbrScale() {
        String previous = System.getProperty("sun.java2d.uiScale");
        try {
            for (String scale : List.of("1", "2")) {
                System.setProperty("sun.java2d.uiScale", scale);
                var image = LinuxLegacyTray.raster(24, 40);
                assertThat(image.getWidth()).isEqualTo(24); assertThat(image.getHeight()).isEqualTo(40);
                for (int y : new int[] {0, 7, 32, 39}) for (int x = 0; x < 24; x++) assertThat(image.getRGB(x, y) >>> 24).isZero();
                assertThat(image.getRGB(12, 20) >>> 24).isPositive();
            }
        } finally { if (previous == null) System.clearProperty("sun.java2d.uiScale"); else System.setProperty("sun.java2d.uiScale", previous); }
    }
    @Test void sameXidNewConnectionIsNewHost() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem()) {
            var host = server.new TrayHost(); var available = new CopyOnWriteArrayList<Boolean>();
            var menus = new CopyOnWriteArrayList<LinuxLegacyTray.MenuRequest>(); var actions = new AtomicInteger();
            try (var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, menus::add, () -> {})) {
                tray.start(); await(() -> available.contains(true), "owner"); assertThat(tray.install(actions::addAndGet)).isTrue();
                host.input(4, 3, 1, 1, 0, 100); host.input(5, 3, 1, 1, 0, 100); await(() -> menus.size() == 1, "old menu");
                long epoch = (Long)LinuxTrayXcbTest.field(tray, "connectionEpoch");
                server.drop(); await(() -> available.size() >= 4 && available.getLast(), "reconnected same XID");
                assertThat(available).startsWith(false, true, false, true);
                assertThat((Long)LinuxTrayXcbTest.field(tray, "connectionEpoch")).isGreaterThan(epoch);
                assertThat(tray.install(actions::addAndGet)).isTrue(); menus.getFirst().action().accept(1);
                SwingUtilities.invokeAndWait(() -> {}); assertThat(actions.get()).isZero();
                assertThat(host.docks).isEqualTo(2); tray.removeAndAwait();
            }
        }
    }
    @Test void resizeExposeAndBackgroundOnlyRedrawWhenDirty() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem()) {
            var host = server.new TrayHost(); var available = new CopyOnWriteArrayList<Boolean>();
            try (var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, ignored -> {}, () -> {})) {
                tray.start(); await(() -> available.contains(true), "owner"); assertThat(tray.install(ignored -> {})).isTrue();
                int baseline = host.paints; host.burst(10000, 48, 32);
                await(() -> host.paints > baseline, "dirty redraw");
                Thread.sleep(200); assertThat(host.paints).isEqualTo(baseline + 1);
                assertThat((Rectangle)LinuxTrayXcbTest.field(tray, "bounds")).isEqualTo(new Rectangle(100, 50, 48, 32));
                Thread.sleep(150); assertThat(host.paints).isEqualTo(baseline + 1); tray.removeAndAwait();
            }
        }
    }
    @Test void unsupportedSlotOrVisualFailsClosed() throws Exception {
        nativeOnly();
        for (int size : new int[] {0, 513}) {
            try (var server = LinuxX11TestServer.filesystem()) {
                var host = server.new TrayHost(); host.width = size; var available = new CopyOnWriteArrayList<Boolean>();
                try (var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, ignored -> {}, () -> {})) {
                    tray.start(); await(() -> available.contains(true), "owner"); assertThat(tray.install(ignored -> {})).isFalse();
                    assertThat(host.mapped).isFalse(); await(() -> host.icon == 0 || server.connectionEnded, "fixture observed confirmed destruction or disconnected client");
                }
            }
        }
        try (var server = LinuxX11TestServer.filesystem()) {
            var host = server.new TrayHost(); host.depth = 16; var available = new CopyOnWriteArrayList<Boolean>();
            try (var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, ignored -> {}, () -> {})) {
                tray.start(); await(() -> available.contains(true), "owner"); assertThat(tray.install(ignored -> {})).isFalse(); assertThat(host.mapped).isFalse();
            }
        }
    }
    @Test void boundedCommandSlotPrioritizesCleanup() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem()) {
            var host = server.new TrayHost(); var available = new CopyOnWriteArrayList<Boolean>();
            var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, ignored -> {}, () -> {});
            try {
                tray.start(); await(() -> available.contains(true), "owner"); assertThat(tray.install(ignored -> {})).isTrue();
                for (int i = 0; i < 10000; i++) tray.discoveryEnabled(true);
                server.pauseReads(); host.expose(); server.awaitPaused();
                long start = System.nanoTime(); tray.close();
                assertThat(System.nanoTime() - start).isLessThan(TimeUnit.MILLISECONDS.toNanos(100));
                var worker = (Thread)LinuxTrayXcbTest.field(tray, "worker"); worker.join(2000);
                assertThat(worker.isAlive()).isFalse(); assertThat(LinuxTrayXcbTest.field(tray, "pending")).isNull();
                assertThat(LinuxTrayXcbTest.field(tray, "active")).isNull();
            } finally { tray.close(); server.resumeReads(); }
        }
    }
    @Test void closeNeverWaitsForDockCleanupNativeCall() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem()) {
            var host = server.new TrayHost(); host.handshake = false;
            var available = new CopyOnWriteArrayList<Boolean>();
            var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, ignored -> {}, () -> {});
            Thread install = null, remove = null;
            try {
                tray.start(); await(() -> available.contains(true), "owner");
                install = Thread.ofPlatform().start(() -> tray.install(ignored -> {}));
                await(() -> host.docks == 1, "pending dock"); server.pauseReads();
                remove = Thread.ofPlatform().start(tray::removeAndAwait);
                server.awaitPaused();
                long start = System.nanoTime(); tray.close();
                assertThat(System.nanoTime() - start).isLessThan(TimeUnit.MILLISECONDS.toNanos(100));
            } finally {
                tray.close(); server.resumeReads();
                if (install != null) install.join(2500); if (remove != null) remove.join(2500);
            }
        }
    }
    @Test void coalescedFalseTrueRefreshesLegacyGeneration() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem()) {
            var host = server.new TrayHost(); var available = new CopyOnWriteArrayList<Boolean>();
            var work = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
            var edt = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
            var callbacks = new CopyOnWriteArrayList<java.util.function.IntConsumer>(); var actions = new AtomicInteger();
            var controllerRef = new AtomicReference<LinuxResidentTray>();
            var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), value -> {
                controllerRef.get().legacyChanged(value); available.add(value);
            }, ignored -> {}, () -> {});
            var controller = new LinuxResidentTray(new ResidentTray.Actions(actions::incrementAndGet, () -> {}, () -> {}, () -> {}),
                new LinuxResidentTray.Modern((owner, callback) -> false, () -> {}, () -> {}, () -> {}),
                new LinuxResidentTray.Legacy(callback -> { callbacks.add(callback); return tray.install(callback); }, tray::removeAndAwait, tray::close),
                work::add, (delay, callback) -> {}, edt::add);
            controllerRef.set(controller);
            try {
                tray.start(); await(() -> available.contains(true), "owner");
                while (!work.isEmpty()) work.remove().run(); assertThat(host.docks).isEqualTo(1);
                callbacks.getFirst().accept(1); host.owner(0x125);
                await(() -> available.size() >= 4 && available.getLast(), "replacement observations");
                while (!work.isEmpty()) work.remove().run(); while (!edt.isEmpty()) edt.remove().run();
                assertThat(actions.get()).isZero(); assertThat(host.docks).isEqualTo(2);
                assertThat(callbacks).hasSize(2); assertThat(available).startsWith(false, true, false, true);
            } finally { controller.close(); while (!work.isEmpty()) work.remove().run(); tray.close(); }
        }
    }
    @Test void alphaRequiresCurrentCompositorAndClearsBeforeOver() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem()) {
            server.setupReply = LinuxTrayXcbTest.alphaSetup();
            var host = server.new TrayHost(); host.visualId = 0x40; host.depth = 32;
            var available = new CopyOnWriteArrayList<Boolean>();
            try (var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, ignored -> {}, () -> {})) {
                tray.start(); await(() -> host.observed.contains("snapshot"), "alpha owner snapshot");
                assertThat(tray.install(ignored -> {})).isFalse(); assertThat(host.icon).isZero();
                host.compositor(0x555); await(() -> available.contains(true), "usable compositor");
                assertThat(tray.install(ignored -> {})).isTrue();
                var ops = server.packets.stream().filter(b -> b[0] == (byte)140 && b[1] == 8).map(b -> b[4]).toList();
                assertThat(ops).containsExactly((byte)0, (byte)3);
                host.compositor(0); await(() -> !available.getLast(), "compositor lost"); tray.removeAndAwait();
                assertThat(host.icon).isZero(); assertThat(tray.install(ignored -> {})).isFalse();
            }
        }
    }
    @Test void menuTimestampIsMatchingButtonPressOrInitiatingKeyPress() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem()) {
            var host = server.new TrayHost(); var available = new CopyOnWriteArrayList<Boolean>();
            var menus = new CopyOnWriteArrayList<LinuxLegacyTray.MenuRequest>();
            try (var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, menus::add, () -> {})) {
                tray.start(); await(() -> available.contains(true), "owner"); assertThat(tray.install(ignored -> {})).isTrue();
                host.input(5, 3, 1, 1, 0, 100); Thread.sleep(50); assertThat(menus).isEmpty();
                host.input(4, 3, 1, 1, 0, 200); host.input(5, 3, 1, 1, 0, 400);
                await(() -> menus.size() == 1, "matched pointer request"); assertThat(menus.getFirst().userTime()).isEqualTo(200L);
                host.input(2, 135, 0, 0, 0, (int)0xf1234567L);
                await(() -> menus.size() == 2, "keyboard request"); assertThat(menus.getLast().userTime()).isEqualTo(0xf1234567L);
            }
        }
    }
    @Test void popupHintFailureReleasesPeerOnlyAfterNativeConnectionTerminates() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem()) {
            var host = server.new TrayHost(); var available = new CopyOnWriteArrayList<Boolean>();
            var menus = new CopyOnWriteArrayList<LinuxLegacyTray.MenuRequest>();
            var results = new CopyOnWriteArrayList<Boolean>(); var disconnected = new AtomicInteger();
            var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, menus::add, () -> {});
            try {
                tray.start(); await(() -> available.contains(true), "owner"); assertThat(tray.install(ignored -> {})).isTrue();
                host.input(4, 3, 1, 1, 0, 200); host.input(5, 3, 1, 1, 0, 200); await(() -> !menus.isEmpty(), "menu request");
                server.pauseReads(); host.expose(); server.awaitPaused();
                menus.getFirst().hints().accept(new LinuxLegacyTray.PopupHint(0x991, success -> {
                    try { if (LinuxTrayXcbTest.field(tray, "xcb") == null) disconnected.incrementAndGet(); }
                    catch (Exception failure) { throw new AssertionError(failure); }
                    results.add(success);
                }));
                Thread.sleep(50); assertThat(results).isEmpty();
                tray.close(); ((Thread)LinuxTrayXcbTest.field(tray, "worker")).join(2500);
                assertThat(results).containsExactly(false); assertThat(disconnected).hasValue(1);
            } finally { tray.close(); server.resumeReads(); }
        }
    }
    @Test void visibleSizeHintsDoNotUseXfceHiddenSentinel() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem()) {
            var host = server.new TrayHost(); var available = new CopyOnWriteArrayList<Boolean>();
            try (var tray = new LinuxLegacyTray(Map.of("DISPLAY", server.address()), available::add, ignored -> {}, () -> {})) {
                tray.start(); await(() -> available.contains(true), "owner"); assertThat(tray.install(ignored -> {})).isTrue();
                var hints = server.packets.stream().map(bytes -> java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.nativeOrder()))
                    .filter(b -> b.get(0) == 18 && b.getInt(8) == host.atom("WM_NORMAL_HINTS")).findFirst().orElseThrow();
                assertThat(hints.getInt(44)).isEqualTo(2); assertThat(hints.getInt(48)).isEqualTo(2);
                assertThat(hints.getInt(36)).isEqualTo(24); assertThat(hints.getInt(40)).isEqualTo(24);
                assertThat(hints.getInt(52)).isEqualTo(512); assertThat(hints.getInt(56)).isEqualTo(512);
                tray.removeAndAwait();
            }
        }
    }
}
