package dev.jasper.app.platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.interfaces.Introspectable;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@EnabledOnOs(OS.LINUX)
class LinuxTrayWireTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void publishesStandardPropertiesAndMenu(boolean abstractAddress) throws Exception {
        try (var f = new Fixture(abstractAddress, true)) {
            f.publish();
            var properties = f.properties("/StatusNotifierItem").GetAll(LinuxTrayDbus.ITEM);
            assertThat(properties.get("Category").getValue()).isEqualTo("ApplicationStatus");
            assertThat(properties.get("Status").getValue()).isEqualTo("Active");
            assertThat(properties.get("ItemIsMenu").getValue()).isEqualTo(false);
            assertThat(f.properties("/MenuBar").GetAll(LinuxTrayDbus.MENU).get("Version").getValue()).isEqualTo(new UInt32(3));
            assertThat(f.menu().GetLayout(0, -1, new String[0]).revision).isEqualTo(new UInt32(1));
            f.menu().Event(1, "clicked", new Variant<>(0), new UInt32(0));
            assertThat(f.actions.poll(2, TimeUnit.SECONDS)).isEqualTo(1);
            f.menu().Event(99, "clicked", new Variant<>(0), new UInt32(0));
            assertThat(f.actions.poll(100, TimeUnit.MILLISECONDS)).isNull();
            assertThatThrownBy(() -> f.properties("/MenuBar").GetAll("missing"))
                .isInstanceOf(org.freedesktop.dbus.errors.UnknownInterface.class);
            assertThatThrownBy(() -> f.properties("/MenuBar").Get(LinuxTrayDbus.MENU, "missing"))
                .isInstanceOf(org.freedesktop.dbus.errors.UnknownProperty.class);
            assertThatThrownBy(() -> f.properties("/MenuBar").Set(LinuxTrayDbus.MENU, "Version", 9))
                .isInstanceOf(org.freedesktop.dbus.errors.PropertyReadOnly.class);
            for (String path : List.of("/StatusNotifierItem", "/MenuBar")) {
                var process = new ProcessBuilder("gdbus", "introspect", "--address", f.bus.address(),
                    "--dest", f.publication, "--object-path", path, "--xml").redirectErrorStream(true).start();
                try {
                    assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
                    String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    assertThat(process.exitValue()).as(output).isZero();
                    Files.createDirectories(Path.of("build/linux-tray-wire"));
                    Files.writeString(Path.of("build/linux-tray-wire", (abstractAddress ? "abstract" : "path") + path.replace('/', '-') + ".txt"), output);
                    assertSignatures(output, path.equals("/MenuBar"));
                } finally { process.destroyForcibly(); }
            }
        }
    }

    @ParameterizedTest(name = "cleanup at handoff, close={0}") @ValueSource(booleans = {false, true})
    void cleanupAtWorkerHandoffCannotBeLost(boolean close) throws Exception {
        var armed = new java.util.concurrent.atomic.AtomicBoolean();
        var boundary = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        Runnable handoff = () -> {
            if (!armed.compareAndSet(true, false)) return;
            boundary.countDown();
            boolean interrupted = false;
            try {
                while (true) {
                    try { if (!release.await(4, TimeUnit.SECONDS)) throw new AssertionError("Handoff not released"); break; }
                    catch (InterruptedException cancelled) { interrupted = true; }
                }
            } finally { if (interrupted) Thread.currentThread().interrupt(); }
        };
        try (var f = new Fixture(false, true, fixture -> { }, handoff)) {
            f.publish();
            var daemon = f.observer.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", org.freedesktop.dbus.interfaces.DBus.class);
            var discovered = java.util.Arrays.stream(daemon.ListNames()).filter(name -> name.startsWith(":"))
                .filter(name -> !name.equals(f.observer.getUniqueName()) && !name.equals(f.watcherConnection.getUniqueName())
                    && !name.equals(f.publication)).toList();
            assertThat(discovered).hasSize(1);
            var publicationLost = new CountDownLatch(1);
            var discoveryLost = new CountDownLatch(1);
            try (var subscription = f.observer.addSigHandler(org.freedesktop.dbus.interfaces.DBus.NameOwnerChanged.class, event -> {
                if (!event.newOwner.isEmpty()) return;
                if (event.name.equals(f.publication)) publicationLost.countDown();
                if (event.name.equals(discovered.getFirst())) discoveryLost.countDown();
            })) {
                assertThat(subscription).isNotNull();
                armed.set(true);
                f.adapter.start();
                assertThat(boundary.await(2, TimeUnit.SECONDS)).isTrue();
                if (close) f.adapter.close(); else f.adapter.withdraw();
                release.countDown();
                assertThat(publicationLost.await(2, TimeUnit.SECONDS)).as("publication dropped after handoff").isTrue();
                if (close) assertThat(discoveryLost.await(2, TimeUnit.SECONDS)).as("discovery dropped after close").isTrue();
                assertThat(daemon.NameHasOwner(f.publication)).isFalse();
            } finally { release.countDown(); }
        }
    }

    @ParameterizedTest(name = "confirmed withdrawal, timeout={0}") @ValueSource(booleans = {false, true})
    void confirmedWithdrawalWaitsForNameLossAndTimeoutKeepsCleanupOwed(boolean timeout) throws Exception {
        var armed = new java.util.concurrent.atomic.AtomicBoolean();
        var boundary = new CountDownLatch(1); var release = new CountDownLatch(1);
        Runnable handoff = () -> {
            if (!armed.compareAndSet(true, false)) return;
            boundary.countDown();
            boolean interrupted = false;
            try {
                while (true) {
                    try { if (!release.await(4, TimeUnit.SECONDS)) throw new AssertionError("Handoff not released"); break; }
                    catch (InterruptedException cancelled) { interrupted = true; }
                }
            } finally { if (interrupted) Thread.currentThread().interrupt(); }
        };
        try (var f = new Fixture(false, true, fixture -> { }, handoff)) {
            f.publish(); armed.set(true); f.adapter.start();
            assertThat(boundary.await(2, TimeUnit.SECONDS)).isTrue();
            var withdrawal = new java.util.concurrent.FutureTask<Void>(() -> { f.adapter.withdrawAndAwait(); return null; });
            var worker = Thread.ofPlatform().daemon().start(withdrawal);
            try {
                if (timeout) {
                    assertThatThrownBy(() -> withdrawal.get(3, TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class)
                        .hasCauseInstanceOf(IllegalStateException.class);
                } else assertThat(withdrawal.isDone()).isFalse();
                release.countDown();
                if (!timeout) assertThat(withdrawal.get(3, TimeUnit.SECONDS)).isNull();
                // A timeout must retain the cleanup request; the next bounded confirmation observes it drained.
                f.adapter.withdrawAndAwait();
                var daemon = f.observer.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", org.freedesktop.dbus.interfaces.DBus.class);
                assertThat(daemon.NameHasOwner(f.publication)).isFalse();
            } finally { release.countDown(); worker.join(3000); }
            assertThat(worker.isAlive()).isFalse();
        }
    }

    @Test void publicationStaysOnDiscoveryBusWhenEarlierAddressAppears() throws Exception {
        Path earlierDirectory = Files.createTempDirectory(Path.of("/tmp"), "jta-");
        Path selectedDirectory = Files.createTempDirectory(Path.of("/tmp"), "jtb-");
        try (var selected = LinuxTestBus.start(selectedDirectory, false);
             var selectedWatcher = DBusConnectionBuilder.forAddress(selected.address()).withShared(false).build()) {
            var expected = new Watcher(true);
            selectedWatcher.exportObject("/StatusNotifierWatcher", expected);
            selectedWatcher.requestBusName(LinuxTrayDbus.WATCHER);
            var hosts = new LinkedBlockingQueue<LinuxStatusNotifier.Host>();
            String addresses = "unix:path=" + earlierDirectory.resolve("bus") + ";" + selected.address();
            try (var adapter = new LinuxStatusNotifier(addresses, hosts::add)) {
                adapter.start();
                var host = hosts.poll(3, TimeUnit.SECONDS);
                assertThat(host).isNotNull();
                assertThat(host.owner()).isEqualTo(selectedWatcher.getUniqueName());
                try (var earlier = LinuxTestBus.start(earlierDirectory, false);
                     var unrelatedWatcher = DBusConnectionBuilder.forAddress(earlier.address()).withShared(false).build()) {
                    var unrelated = new Watcher(true);
                    unrelatedWatcher.exportObject("/StatusNotifierWatcher", unrelated);
                    unrelatedWatcher.requestBusName(LinuxTrayDbus.WATCHER);
                    assertThat(unrelatedWatcher.getUniqueName()).isEqualTo(host.owner());
                    assertThat(adapter.publish(host, id -> { })).isTrue();
                    assertThat(expected.registrations.poll(2, TimeUnit.SECONDS)).as("registration on discovered bus").isNotNull();
                    assertThat(unrelated.registrations).as("no registration on earlier unrelated bus").isEmpty();
                }
            }
        } finally {
            Files.deleteIfExists(earlierDirectory.resolve("bus")); Files.delete(earlierDirectory);
            Files.deleteIfExists(selectedDirectory.resolve("bus")); Files.delete(selectedDirectory);
        }
    }

    @Test void pinnedPropertyErrorsHaveExactWireNames() throws Exception {
        try (var f = new Fixture(false, true)) {
            f.publish();
            var cases = Map.of(
                "UnknownInterface", List.of("GetAll", "missing"),
                "UnknownProperty", List.of("Get", LinuxTrayDbus.MENU, "missing"),
                "PropertyReadOnly", List.of("Set", LinuxTrayDbus.MENU, "Version", "<uint32 9>"));
            for (var test : cases.entrySet()) {
                var command = new java.util.ArrayList<>(List.of("gdbus", "call", "--address", f.bus.address(),
                    "--dest", f.publication, "--object-path", "/MenuBar", "--method",
                    "org.freedesktop.DBus.Properties." + test.getValue().getFirst()));
                command.addAll(test.getValue().subList(1, test.getValue().size()));
                var process = new ProcessBuilder(command).redirectErrorStream(true).start();
                try {
                    assertThat(process.waitFor(3, TimeUnit.SECONDS)).isTrue();
                    String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    assertThat(process.exitValue()).isNotZero();
                    assertThat(output).contains("GDBus.Error:org.freedesktop.dbus.errors." + test.getKey() + ":");
                    Files.createDirectories(Path.of("build/linux-tray-wire"));
                    Files.writeString(Path.of("build/linux-tray-wire", test.getKey() + ".txt"), output);
                } finally { process.destroyForcibly(); }
            }
        }
    }

    @Test @SuppressWarnings("try")
    void connectionStateRecoversWithoutHostEvenWhenHostSnapshotIsUnchanged() throws Exception {
        Path directory = Files.createTempDirectory(Path.of("/tmp"), "jtc-");
        var hosts = new LinkedBlockingQueue<LinuxStatusNotifier.Host>();
        var states = new LinkedBlockingQueue<Boolean>();
        try {
            try (var first = LinuxTestBus.start(directory, false);
                 var adapter = new LinuxStatusNotifier(first.address(), hosts::add, states::add)) {
                adapter.start();
                assertThat(states.poll(3, TimeUnit.SECONDS)).isTrue();
                assertThat(hosts.poll(2, TimeUnit.SECONDS)).isNotNull().extracting(LinuxStatusNotifier.Host::registered).isEqualTo(false);
                first.close();
                assertThat(states.poll(3, TimeUnit.SECONDS)).isFalse();
                try (var second = LinuxTestBus.start(directory, false)) {
                    assertThat(second.address()).startsWith("unix:path=" + directory.resolve("bus"));
                    adapter.start();
                    assertThat(states.poll(3, TimeUnit.SECONDS)).isTrue();
                    // Both daemon lifetimes have no watcher, so host deduplication cannot carry restoration.
                    assertThat(hosts).isEmpty();
                }
            }
        } finally { Files.deleteIfExists(directory.resolve("bus")); Files.delete(directory); }
    }

    @Test @SuppressWarnings("try") // Deliberately stop the first private bus before its scope ends.
    void disconnectReportsUnavailableAndCapturedAddressCanReconnect() throws Exception {
        Path directory = Files.createTempDirectory(Path.of("/tmp"), "jtr-");
        var hosts = new LinkedBlockingQueue<LinuxStatusNotifier.Host>();
        try {
            try (var first = LinuxTestBus.start(directory, false);
                 var adapter = new LinuxStatusNotifier(first.address(), hosts::add)) {
                try (var watcher = DBusConnectionBuilder.forAddress(first.address()).withShared(false).build()) {
                    watcher.exportObject("/StatusNotifierWatcher", new Watcher(true));
                    watcher.requestBusName(LinuxTrayDbus.WATCHER);
                    adapter.start();
                    assertThat(hosts.poll(3, TimeUnit.SECONDS).registered()).isTrue();
                    first.close();
                    assertThat(hosts.poll(3, TimeUnit.SECONDS).registered()).isFalse();
                }
                Files.deleteIfExists(directory.resolve("bus"));
                try (var second = LinuxTestBus.start(directory, false);
                     var watcher = DBusConnectionBuilder.forAddress(second.address()).withShared(false).build()) {
                    watcher.exportObject("/StatusNotifierWatcher", new Watcher(true));
                    watcher.requestBusName(LinuxTrayDbus.WATCHER);
                    adapter.start();
                    assertThat(hosts.poll(3, TimeUnit.SECONDS).registered()).isTrue();
                }
            }
        } finally { Files.deleteIfExists(directory.resolve("bus")); Files.delete(directory); }
    }

    @Test void getLayoutHonorsDepthAndPropertyFilter() throws Exception {
        try (var f = new Fixture(false, true)) {
            f.publish();
            assertThat(f.menu().GetLayout(0, 0, new String[0]).layout.children).isEmpty();
            var layout = f.menu().GetLayout(0, -1, new String[]{"label"}).layout;
            assertThat(layout.properties).isEmpty();
            assertThat(layout.children).hasSize(5);
            assertThat(f.menu().GetGroupProperties(new int[]{1}, new String[]{"label"}).getFirst().properties).containsOnlyKeys("label");
            assertThat(f.menu().GetGroupProperties(new int[0], new String[0])).hasSize(6);
            assertThatThrownBy(() -> f.menu().GetLayout(0, -2, new String[0])).isInstanceOf(org.freedesktop.dbus.exceptions.DBusExecutionException.class);
            assertThatThrownBy(() -> f.menu().GetProperty(99, "label")).isInstanceOf(org.freedesktop.dbus.exceptions.DBusExecutionException.class);
            assertThatThrownBy(() -> f.menu().GetProperty(1, "missing")).isInstanceOf(org.freedesktop.dbus.errors.UnknownProperty.class);
        }
    }

    @Test void groupEventsReportUnknownIds() throws Exception {
        try (var f = new Fixture(false, true)) {
            f.publish();
            assertThat(f.menu().EventGroup(List.of(new LinuxTrayDbus.MenuEvent(1, "clicked", new Variant<>(0), new UInt32(0)),
                new LinuxTrayDbus.MenuEvent(99, "clicked", new Variant<>(0), new UInt32(0)),
                new LinuxTrayDbus.MenuEvent(4, "clicked", new Variant<>(0), new UInt32(0))))).containsExactly(99);
            assertThat(f.actions.poll(2, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(f.actions.poll(100, TimeUnit.MILLISECONDS)).isNull();
            var shown = f.menu().AboutToShowGroup(new int[]{1, 99});
            assertThat(shown.updates).isEmpty();
            assertThat(shown.errors).containsExactly(99);
        }
    }

    @Test void pixmapsAndEmptyArraysHaveExactSignatures() throws Exception {
        try (var f = new Fixture(false, true)) {
            f.publish();
            var props = f.properties("/StatusNotifierItem").GetAll(LinuxTrayDbus.ITEM);
            for (String key : List.of("IconPixmap", "AttentionIconPixmap", "OverlayIconPixmap"))
                assertThat(props.get(key).getSig()).isEqualTo("a(iiay)");
            assertThat(props.get("ToolTip").getSig()).isEqualTo("(sa(iiay)ss)");
            assertThat(props.get("Menu").getSig()).isEqualTo("o");
            assertThat(f.properties("/MenuBar").GetAll(LinuxTrayDbus.MENU).get("IconThemePath").getSig()).isEqualTo("as");
            Object empty = f.properties("/StatusNotifierItem").Get(LinuxTrayDbus.ITEM, "OverlayIconPixmap");
            assertThat(empty).usingRecursiveComparison().isEqualTo(props.get("OverlayIconPixmap").getValue());
            Object tooltip = f.properties("/StatusNotifierItem").Get(LinuxTrayDbus.ITEM, "ToolTip");
            Object iconThemePath = f.properties("/MenuBar").Get(LinuxTrayDbus.MENU, "IconThemePath");
            assertThat(tooltip).usingRecursiveComparison().isEqualTo(props.get("ToolTip").getValue());
            assertThat(iconThemePath).usingRecursiveComparison().isEqualTo(
                f.properties("/MenuBar").GetAll(LinuxTrayDbus.MENU).get("IconThemePath").getValue());
            String xml = f.observer.getRemoteObject(f.publication, "/StatusNotifierItem", Introspectable.class).Introspect();
            assertThat(xml).contains("a(iiay)", "(sa(iiay)ss)");
            String menu = f.observer.getRemoteObject(f.publication, "/MenuBar", Introspectable.class).Introspect();
            assertThat(menu).contains("(ia{sv}av)", "a(isvu)");
        }
    }

    @Test void watcherWithoutHostDoesNotPublish() throws Exception {
        try (var f = new Fixture(false, false)) {
            assertThat(f.nextHost().registered()).isFalse();
            assertThat(f.adapter.publish(new LinuxStatusNotifier.Host(LinuxTrayDbus.WATCHER, f.watcherConnection.getUniqueName(), false), f.actions::add)).isFalse();
            assertThat(f.watcher.registrations).isEmpty();
        }
    }

    @Test void subscribesBeforeInitialSnapshot() throws Exception {
        var reading = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var f = new Fixture(false, false, fixture -> fixture.watcher.onRead = () -> {
            reading.countDown();
            try { release.await(1, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        })) {
            assertThat(reading.await(1, TimeUnit.SECONDS)).isTrue();
            f.watcher.host = true;
            f.watcherConnection.sendMessage(new LinuxTrayDbus.Watcher.StatusNotifierHostRegistered("/StatusNotifierWatcher"));
            release.countDown();
            assertThat(f.nextHost().registered()).isFalse();
            assertThat(f.nextHost().registered()).isTrue();
        } finally { release.countDown(); }
    }

    @Test void ownerReplacementRejectsOldRegistration() throws Exception {
        try (var f = new Fixture(false, true)) {
            var old = f.nextHost();
            f.watcher.release = new CountDownLatch(1);
            var attempt = new java.util.concurrent.FutureTask<Boolean>(() -> f.adapter.publish(old, f.actions::add));
            var thread = Thread.ofPlatform().daemon().start(attempt);
            try (var replacement = DBusConnectionBuilder.forAddress(f.bus.address()).withShared(false).build()) {
                assertThat(f.watcher.registrations.poll(1, TimeUnit.SECONDS)).isNotNull();
                f.watcherConnection.releaseBusName(LinuxTrayDbus.WATCHER);
                replacement.exportObject("/StatusNotifierWatcher", new Watcher(true));
                replacement.requestBusName(LinuxTrayDbus.WATCHER);
                f.watcher.release.countDown();
                assertThat(attempt.get(3, TimeUnit.SECONDS)).isFalse();
            } finally { f.watcher.release.countDown(); thread.join(3000); }
        }
    }

    @Test void freedesktopHostIsUsedWhenKdeHasNoHost() throws Exception {
        try (var f = new Fixture(false, false)) {
            assertThat(f.nextHost().registered()).isFalse();
            try (var secondary = DBusConnectionBuilder.forAddress(f.bus.address()).withShared(false).build()) {
                var watcher = new FreedesktopWatcher();
                secondary.exportObject("/StatusNotifierWatcher", watcher);
                secondary.requestBusName(LinuxTrayDbus.FREEDESKTOP_WATCHER);
                var host = f.nextHost();
                assertThat(host.service()).isEqualTo(LinuxTrayDbus.FREEDESKTOP_WATCHER);
                assertThat(f.adapter.publish(host, f.actions::add)).isTrue();
                f.publication = watcher.registrations.poll(2, TimeUnit.SECONDS);
                assertThat(f.properties("/StatusNotifierItem").GetAll(LinuxTrayDbus.FREEDESKTOP_ITEM)).containsKey("Menu");
                assertThat(f.properties("/StatusNotifierItem").GetAll(LinuxTrayDbus.ITEM)).containsKey("Menu");
                f.observer.getRemoteObject(f.publication, "/StatusNotifierItem", LinuxTrayDbus.FreedesktopItem.class).Activate(0, 0);
                assertThat(f.actions.poll(2, TimeUnit.SECONDS)).isEqualTo(1);
                String xml = f.observer.getRemoteObject(f.publication, "/StatusNotifierItem", Introspectable.class).Introspect();
                assertThat(xml).contains("org.freedesktop.StatusNotifierItem", "org.kde.StatusNotifierItem");
            }
        }
    }

    @Test void oldOwnerHostSignalCannotChangeSelection() throws Exception {
        try (var f = new Fixture(false, true)) {
            var host = f.nextHost();
            try (var imposter = DBusConnectionBuilder.forAddress(f.bus.address()).withShared(false).build()) {
                imposter.sendMessage(new LinuxTrayDbus.Watcher.StatusNotifierHostUnregistered("/StatusNotifierWatcher"));
                assertThat(f.hosts.poll(150, TimeUnit.MILLISECONDS)).isNull();
                assertThat(f.adapter.publish(host, f.actions::add)).isTrue();
            }
        }
    }

    @Test void withdrawDropsThePublicationName() throws Exception {
        try (var f = new Fixture(false, true)) {
            f.publish();
            var lost = new CountDownLatch(1);
            try (var sub = f.observer.addSigHandler(org.freedesktop.dbus.interfaces.DBus.NameOwnerChanged.class,
                event -> { if (event.name.equals(f.publication) && event.newOwner.isEmpty()) lost.countDown(); })) {
                assertThat(sub).isNotNull();
                f.adapter.withdraw();
                assertThat(lost.await(3, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test void unresponsiveWatcherTimesOut() throws Exception {
        try (var f = new Fixture(false, true)) {
            var host = f.nextHost();
            f.watcher.release = new CountDownLatch(1);
            try {
                assertTimeoutPreemptively(Duration.ofSeconds(3), () -> assertThat(f.adapter.publish(host, f.actions::add)).isFalse());
            } finally { f.watcher.release.countDown(); }
            f.adapter.close();
        }
    }


    private static void assertSignatures(String xml, boolean menu) throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var document = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        var actual = new java.util.TreeMap<String, String>();
        var interfaces = document.getElementsByTagName("interface");
        String wanted = menu ? LinuxTrayDbus.MENU : LinuxTrayDbus.ITEM;
        for (int i = 0; i < interfaces.getLength(); i++) {
            var face = (org.w3c.dom.Element) interfaces.item(i);
            if (!wanted.equals(face.getAttribute("name"))) continue;
            for (var node = face.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (!(node instanceof org.w3c.dom.Element member)) continue;
                String tag = member.getTagName();
                if (tag.equals("property")) {
                    assertThat(member.getAttribute("access")).isEqualTo("read");
                    actual.put("property:" + member.getAttribute("name"), member.getAttribute("type"));
                } else if (tag.equals("method") || tag.equals("signal")) {
                    var in = new StringBuilder(); var out = new StringBuilder();
                    var args = member.getElementsByTagName("arg");
                    for (int a = 0; a < args.getLength(); a++) {
                        var arg = (org.w3c.dom.Element) args.item(a);
                        (arg.getAttribute("direction").equals("in") ? in : out).append(arg.getAttribute("type"));
                    }
                    actual.put(tag + ":" + member.getAttribute("name"), in + "->" + out);
                }
            }
        }
        var expected = new java.util.TreeMap<String, String>();
        if (menu) {
            expected.put("method:GetLayout", "iias->u(ia{sv}av)");
            expected.put("method:GetGroupProperties", "aias->a(ia{sv})");
            expected.put("method:GetProperty", "is->v");
            expected.put("method:Event", "isvu->");
            expected.put("method:EventGroup", "a(isvu)->ai");
            expected.put("method:AboutToShow", "i->b");
            expected.put("method:AboutToShowGroup", "ai->aiai");
            expected.put("signal:LayoutUpdated", "->ui");
            expected.put("signal:ItemsPropertiesUpdated", "->a(ia{sv})a(ias)");
            expected.put("signal:ItemActivationRequested", "->iu");
            expected.put("property:Version", "u");
            expected.put("property:TextDirection", "s");
            expected.put("property:Status", "s");
            expected.put("property:IconThemePath", "as");
        } else {
            for (String name : List.of("Activate", "SecondaryActivate", "ContextMenu")) expected.put("method:" + name, "ii->");
            expected.put("method:Scroll", "is->");
            for (String name : List.of("Category", "Id", "Title", "Status", "IconName", "OverlayIconName", "AttentionIconName", "AttentionMovieName")) expected.put("property:" + name, "s");
            for (String name : List.of("IconPixmap", "OverlayIconPixmap", "AttentionIconPixmap")) expected.put("property:" + name, "a(iiay)");
            expected.put("property:WindowId", "u");
            expected.put("property:ToolTip", "(sa(iiay)ss)");
            expected.put("property:ItemIsMenu", "b");
            expected.put("property:Menu", "o");
            for (String name : List.of("NewTitle", "NewIcon", "NewAttentionIcon", "NewOverlayIcon", "NewToolTip")) expected.put("signal:" + name, "->");
            expected.put("signal:NewStatus", "->s");
        }
        assertThat(actual).isEqualTo(expected);
    }

    public static class Watcher implements LinuxTrayDbus.Watcher, Properties {
        volatile boolean host;
        volatile CountDownLatch release;
        volatile Runnable onRead;
        final LinkedBlockingQueue<String> registrations = new LinkedBlockingQueue<>();
        Watcher(boolean host) { this.host = host; }
        @Override public String getObjectPath() { return "/StatusNotifierWatcher"; }
        @Override public void RegisterStatusNotifierItem(String service) {
            registrations.add(service);
            if (release != null) try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        @Override public Map<String, Variant<?>> GetAll(String name) { return Map.of("IsStatusNotifierHostRegistered", new Variant<>(host)); }
        @Override @SuppressWarnings("unchecked") public <A> A Get(String name, String property) {
            boolean value = host;
            Runnable callback = onRead;
            onRead = null;
            if (callback != null) callback.run();
            return (A) Boolean.valueOf(value);
        }
        @Override public <A> void Set(String name, String property, A value) { throw new org.freedesktop.dbus.errors.PropertyReadOnly(property); }
    }

    public static final class FreedesktopWatcher extends Watcher implements LinuxTrayDbus.FreedesktopWatcher {
        FreedesktopWatcher() { super(true); }
    }

    static final class Fixture implements AutoCloseable {
        final Path directory;
        final LinuxTestBus bus;
        final DBusConnection watcherConnection;
        final DBusConnection observer;
        final Watcher watcher;
        final LinuxStatusNotifier adapter;
        final LinkedBlockingQueue<LinuxStatusNotifier.Host> hosts = new LinkedBlockingQueue<>();
        final LinkedBlockingQueue<Integer> actions = new LinkedBlockingQueue<>();
        String publication;
        Fixture(boolean abstractAddress, boolean host) throws Exception { this(abstractAddress, host, f -> { }); }
        Fixture(boolean abstractAddress, boolean host, java.util.function.Consumer<Fixture> setup) throws Exception {
            this(abstractAddress, host, setup, () -> { });
        }
        Fixture(boolean abstractAddress, boolean host, java.util.function.Consumer<Fixture> setup, Runnable beforeHandoff) throws Exception {
            directory = Files.createTempDirectory(Path.of("/tmp"), "jtw-");
            bus = LinuxTestBus.start(directory, abstractAddress);
            watcherConnection = DBusConnectionBuilder.forAddress(bus.address()).withShared(false).build();
            observer = DBusConnectionBuilder.forAddress(bus.address()).withShared(false).build();
            watcher = new Watcher(host);
            watcherConnection.exportObject("/StatusNotifierWatcher", watcher);
            watcherConnection.requestBusName(LinuxTrayDbus.WATCHER);
            setup.accept(this);
            adapter = new LinuxStatusNotifier(bus.address(), hosts::add, beforeHandoff);
            adapter.start();
        }
        LinuxStatusNotifier.Host nextHost() throws Exception {
            var host = hosts.poll(4, TimeUnit.SECONDS);
            assertThat(host).isNotNull();
            return host;
        }
        void publish() throws Exception {
            assertThat(adapter.publish(nextHost(), actions::add)).isTrue();
            publication = watcher.registrations.poll(2, TimeUnit.SECONDS);
            assertThat(publication).startsWith(":");
        }
        Properties properties(String path) throws Exception { return observer.getRemoteObject(publication, path, Properties.class); }
        LinuxTrayDbus.Menu menu() throws Exception { return observer.getRemoteObject(publication, "/MenuBar", LinuxTrayDbus.Menu.class); }
        @Override public void close() throws java.io.IOException {
            adapter.close(); observer.close(); watcherConnection.close(); bus.close();
            Files.deleteIfExists(directory.resolve("bus")); Files.delete(directory);
        }
    }
}
