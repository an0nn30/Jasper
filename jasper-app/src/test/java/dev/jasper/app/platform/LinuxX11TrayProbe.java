package dev.jasper.app.platform;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Structure;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static dev.jasper.app.platform.LinuxTrayXcbTest.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in headless transport gate. Each native case runs under a separate process watchdog. */
public final class LinuxX11TrayProbe {
    private LinuxX11TrayProbe() {}
    public static void main(String[] args) throws Exception {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if ((args[i].equals("--require-native") || args[i].equals("--expect-unavailable") || args[i].equals("--selection-test"))) options.put(args[i], "true");
            else if (Set.of("--gate", "--output", "--case", "--display", "--panel", "--compositor", "--cycles", "--slot", "--background", "--lifecycle").contains(args[i]) && i + 1 < args.length)
                options.put(args[i], args[++i]);
            else throw new IllegalArgumentException("Unknown or incomplete probe option");
        }
        if (!System.getProperty("os.name").equals("Linux")) throw new IllegalStateException("Linux native gate required");
        Path output = Path.of(options.getOrDefault("--output", "build/tray-gate/transport")).toAbsolutePath();
        Files.createDirectories(output);
        Files.writeString(output.resolve("probe.pid"), ProcessHandle.current().pid() + "\n");
        if (options.getOrDefault("--gate", "transport").equals("legacy")) { legacy(options, output); return; }
        if (options.containsKey("--case")) {
            String name = options.get("--case");
            warm();
            int fds = fdCount(); Set<String> threads = threads();
            if (name.equals("abi")) abi(output);
            else if (name.equals("authority")) heldAuthority();
            else if (name.equals("reuse")) reuse();
            else if (name.equals("cycles")) cycles();
            else {
                String[] parts = name.split("-"); stall(parts[0], parts[1].equals("abstract"), parts[2].equals("close"));
            }
            LinuxX11TestServer.await(() -> threads().equals(threads), "native threads did not return to baseline");
            assertThat(fdCount()).as("FD baseline after " + name).isEqualTo(fds);
            System.out.println("PASS " + name + " fdBaseline=" + fds + " fdAfter=" + fdCount()
                + " threadBaseline=" + threads.size() + " threadAfter=" + threads().size()
                + " liveDuplicates=0 liveAuthRecords=0 stuckWorkers=0 unexpectedExceptions=0");
            return;
        }
        if (!options.getOrDefault("--gate", "transport").equals("transport"))
            throw new IllegalArgumentException("Legacy/popup gate requires its later implementation task");
        var cases = new ArrayList<>(List.of("abi", "authority", "reuse", "cycles"));
        for (String phase : List.of("setup", "reply", "explicit", "implicit"))
            for (String socket : List.of("filesystem", "abstract"))
                for (String finish : List.of("timeout", "close")) cases.add(phase + "-" + socket + "-" + finish);
        var summary = new ArrayList<String>();
        for (String name : cases) {
            Path log = output.resolve(name + ".log");
            Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "--enable-native-access=ALL-UNNAMED", "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
                LinuxX11TrayProbe.class.getName(), "--case", name, "--output", output.toString(), "--require-native")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if (!child.waitFor(10, TimeUnit.SECONDS)) {
                child.destroyForcibly(); child.waitFor(2, TimeUnit.SECONDS);
                Files.writeString(output.resolve("FAILED"), name + ": watchdog expired; native case FAILED\n");
                throw new AssertionError("Native child stuck: " + name);
            }
            if (child.exitValue() != 0) {
                Files.writeString(output.resolve("FAILED"), name + ": child exit " + child.exitValue() + "\n");
                throw new AssertionError("Native child failed: " + name + "\n" + Files.readString(log));
            }
            summary.add(Files.readString(log).strip());
            System.out.println("PASS child=" + name);
        }
        Files.deleteIfExists(output.resolve("FAILED"));
        Files.write(output.resolve("summary.txt"), summary);
        System.out.println("PASS transport requiredCases=" + cases.size() + " requiredSkips=0 watchdogFailures=0");
    }

    private static void legacy(Map<String, String> options, Path output) throws Exception {
        java.util.logging.Logger.getLogger("dev.jasper.app.platform.LinuxLegacyTray").setLevel(java.util.logging.Level.FINE);
        for (var handler : java.util.logging.Logger.getLogger("").getHandlers()) handler.setLevel(java.util.logging.Level.FINE);
        String panel = options.getOrDefault("--panel", "lxde");
        int cycles = Integer.parseInt(options.getOrDefault("--cycles", "20"));
        int slot = Integer.parseInt(options.getOrDefault("--slot", "24"));
        String background = options.getOrDefault("--background", "light");
        if (cycles < 0 || cycles > 20 || !Set.of(16, 24, 32, 48).contains(slot)
                || !Set.of("light", "dark", "pattern").contains(background)) throw new IllegalArgumentException("Unsupported native matrix cell");
        if (!Set.of("lxde", "xfce").contains(panel)) throw new IllegalArgumentException("Unknown panel");
        if (System.getenv("DISPLAY") == null || System.getenv("DBUS_SESSION_BUS_ADDRESS") == null)
            throw new IllegalStateException("Run legacy gate inside an isolated Xvfb and private bus");
        var unexpected = new java.util.concurrent.CopyOnWriteArrayList<Throwable>();
        var oldHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> { unexpected.add(failure); failure.printStackTrace(); });
        Path home = Files.createTempDirectory(output, "private-home-");
        var environment = new java.util.HashMap<>(System.getenv());
        environment.remove("WAYLAND_DISPLAY"); environment.remove("WAYLAND_SOCKET"); environment.remove("SESSION_MANAGER");
        environment.put("GDK_BACKEND", "x11"); environment.put("QT_QPA_PLATFORM", "xcb");
        environment.put("XDG_SESSION_TYPE", "x11");
        Path runtime = Files.createDirectory(home.resolve("runtime"));
        Files.setPosixFilePermissions(runtime, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        environment.put("XDG_RUNTIME_DIR", runtime.toString());
        environment.put("GIO_USE_VFS", "local"); environment.put("NO_AT_BRIDGE", "1");
        environment.put("HOME", home.toString()); environment.put("XDG_CONFIG_HOME", home.resolve("config").toString());
        environment.put("XDG_CACHE_HOME", home.resolve("cache").toString()); environment.put("XDG_DATA_HOME", home.resolve("data").toString());
        Files.writeString(output.resolve("isolation.txt"), "DISPLAY=" + environment.get("DISPLAY")
            + "\nprivateBusCaptured=true\nGDK_BACKEND=" + environment.get("GDK_BACKEND")
            + "\nXDG_SESSION_TYPE=" + environment.get("XDG_SESSION_TYPE")
            + "\nWAYLAND_DISPLAY_present=" + environment.containsKey("WAYLAND_DISPLAY")
            + "\nWAYLAND_SOCKET_present=" + environment.containsKey("WAYLAND_SOCKET")
            + "\nHOME=" + home + "\nXDG_RUNTIME_DIR=" + runtime + "\n");
        var owned = new ArrayList<Process>();
        var observations = new java.util.concurrent.CopyOnWriteArrayList<Boolean>();
        var actions = new java.util.concurrent.atomic.AtomicInteger();
        var menus = new java.util.concurrent.CopyOnWriteArrayList<LinuxLegacyTray.MenuRequest>();
        var tray = new LinuxLegacyTray(environment, value -> { observations.add(value); System.out.println("availability=" + value); },
            request -> { menus.add(request); System.out.println("menu=" + request.rootBounds()); }, () -> {});
        try {
            tray.start(); LinuxX11TestServer.await(() -> observations.contains(false), "initial unavailable snapshot");
            Thread.sleep(200); assertThat(observations).doesNotContain(true);
            Files.writeString(output.resolve("host.txt"), command(environment, "uname", "-a")
                + command(environment, "rpm", "-q", "lxpanel", "xfce4-panel", "xorg-x11-server-Xvfb")
                + "DISPLAY=" + environment.get("DISPLAY") + "\nJBR=" + System.getProperty("java.runtime.version") + "\n");
            var texture = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) texture.setRGB(x, y,
                background.equals("light") ? 0xeeeeee : background.equals("dark") ? 0x20242b
                    : (x / 4 + y / 4) % 2 == 0 ? 0xd8d8d8 : 0x8399a4);
            Path texturePath = output.resolve("panel-background.png"); javax.imageio.ImageIO.write(texture, "png", texturePath.toFile());
            if (panel.equals("lxde")) {
                Path profile = home.resolve("config/lxpanel/jasper-gate/panels/panel"); Files.createDirectories(profile.getParent());
                String config = "Global {\n edge=top\n align=left\n widthtype=percent\n width=100\n height=" + (slot + 8) + "\n iconsize=" + slot + "\n transparent=0\n background=1\n backgroundfile=" + texturePath + "\n}\nPlugin {\n type=tray\n}\n";
                Files.writeString(profile, config); Files.writeString(output.resolve("panel-profile.txt"), config);
            } else {
                String xfconfd = List.of("/usr/lib64/xfce4/xfconf/xfconfd", "/usr/lib/xfce4/xfconf/xfconfd", "/usr/libexec/xfconfd")
                    .stream().filter(path -> Files.isExecutable(Path.of(path))).findFirst().orElseThrow();
                owned.add(launch(environment, output, "xfconf", xfconfd));
                command(environment, "gdbus", "wait", "--session", "org.xfce.Xfconf");
                command(environment, "xfconf-query", "-c", "xfce4-panel", "-p", "/panels", "-n", "-a", "-t", "int", "-s", "1");
                for (String[] property : new String[][] {{"/panels/panel-1/position", "string", "p=6;x=0;y=0"},
                        {"/panels/panel-1/length", "uint", "100"}, {"/panels/panel-1/size", "uint", Integer.toString(slot + 8)},
                        {"/panels/panel-1/background-style", "uint", "2"}, {"/panels/panel-1/background-image", "string", texturePath.toString()},
                        {"/plugins/plugin-1/icon-size", "int", Integer.toString(slot)},
                        {"/panels/panel-1/position-locked", "bool", "true"}, {"/plugins/plugin-1", "string", "systray"}})
                    command(environment, "xfconf-query", "-c", "xfce4-panel", "-p", property[0], "-n", "-t", property[1], "-s", property[2]);
                command(environment, "xfconf-query", "-c", "xfce4-panel", "-p", "/panels/panel-1/plugin-ids", "-n", "-a", "-t", "int", "-s", "1");
                Files.writeString(output.resolve("panel-profile.txt"), command(environment, "xfconf-query", "-c", "xfce4-panel", "-lv"));
            }
            owned.add(launch(environment, output, "wm", "openbox"));
            long wmDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!command(environment, "xprop", "-root", "_NET_SUPPORTING_WM_CHECK").contains("window id #")) {
                if (System.nanoTime() >= wmDeadline) throw new AssertionError("Owned WM did not become ready");
                Thread.sleep(20);
            }
            if (options.containsKey("--compositor")) {
                owned.add(launch(environment, output, "compositor", options.get("--compositor"), "-n"));
                try (var cancel = new LinuxTrayXcb.Cancellation(); var connection = LinuxTrayXcb.connect(environment, deadline(), cancel)) {
                    int selection = connection.internAtom("_NET_WM_CM_S0", deadline());
                    LinuxX11TestServer.await(() -> connection.selectionOwner(selection, deadline()) != 0, "owned compositor ready");
                }
            }
            owned.add(panel.equals("lxde") ? launch(environment, output, "panel", "lxpanel", "--profile", "jasper-gate")
                : launch(environment, output, "panel", "xfce4-panel", "--disable-wm-check"));
            if (options.containsKey("--expect-unavailable")) {
                try (var cancel = new LinuxTrayXcb.Cancellation(); var connection = LinuxTrayXcb.connect(environment, deadline(), cancel)) {
                    int selection = connection.internAtom("_NET_SYSTEM_TRAY_S0", deadline());
                    LinuxX11TestServer.await(() -> connection.selectionOwner(selection, deadline()) != 0, "real unsupported owner ready");
                    int owner = connection.selectionOwner(selection, deadline());
                    int visualId = connection.property32(owner, connection.internAtom("_NET_SYSTEM_TRAY_VISUAL", deadline()),
                        connection.internAtom("VISUALID", deadline()), deadline());
                    var visual = connection.setup().visuals().stream().filter(v -> v.id() == visualId).findFirst().orElseThrow();
                    assertThat(connection.alpha(visual, deadline())).isTrue();
                    assertThat(connection.selectionOwner(connection.internAtom("_NET_WM_CM_S0", deadline()), deadline())).isZero();
                }
                Thread.sleep(250); assertThat(observations).doesNotContain(true); assertThat(tray.install(id -> actions.incrementAndGet())).isFalse();
                assertThat(command(environment, "xwininfo", "-root", "-tree")).doesNotContain("\"Jasper\"");
                Files.writeString(output.resolve("summary.txt"), "PASS alpha-without-compositor declined cleanly; no icon or pending publication\n");
                return;
            }
            LinuxX11TestServer.await(() -> observations.getLast(), "real panel owner available");
            Thread.sleep(200);
            var before = capture(environment, output, "before");
            assertThat(tray.install(id -> { actions.incrementAndGet(); System.out.println("action=" + id); })).as("real XEmbed docking").isTrue();
            Thread.sleep(400);
            Files.writeString(output.resolve("tree.txt"), command(environment, "xwininfo", "-root", "-tree"));
            var embedded = capture(environment, output, "embedded");
            var nativeBounds = (java.awt.Rectangle)field(tray, "bounds");
            command(environment, "xwd", "-id", Integer.toUnsignedString((Integer)field(tray, "icon")), "-silent", "-out", output.resolve("native-icon.xwd").toString());
            Object nativeConnection = field(tray, "xcb"); Object drawing = field(nativeConnection, "drawing");
            Files.writeString(output.resolve("render-formats.txt"), "source=" + field(drawing, "sourceFormat") + "\ndestinations=" + field(drawing, "visualFormats") + "\n");
            verifyArtwork(before, embedded, nativeBounds, output);
            javax.imageio.ImageIO.write(embedded.getSubimage(nativeBounds.x, nativeBounds.y, nativeBounds.width, nativeBounds.height),
                "png", output.resolve("slot.png").toFile());
            String windows = command(environment, "xdotool", "search", "--name", "^Jasper$").strip();
            assertThat(windows.lines().count()).as("exactly one Jasper icon").isEqualTo(1);
            String info = command(environment, "xwininfo", "-id", windows, "-all");
            Files.writeString(output.resolve("icon-info.txt"), info);
            String parent = info.lines().filter(line -> line.contains("Parent window id:")).findFirst().orElseThrow().trim().split(" ")[3];
            String parentInfo = command(environment, "xwininfo", "-id", parent, "-all");
            Files.writeString(output.resolve("embedder-info.txt"), parentInfo);
            String ancestor = parentInfo.lines().filter(line -> line.contains("Parent window id:")).findFirst().orElseThrow().trim().split(" ")[3];
            Files.writeString(output.resolve("panel-info.txt"), command(environment, "xwininfo", "-id", ancestor, "-all"));
            command(environment, "xdotool", "mousemove", "--window", windows, "12", "12", "click", "1");
            Files.writeString(output.resolve("pointer.txt"), command(environment, "xdotool", "getmouselocation", "--shell"));
            LinuxX11TestServer.await(() -> actions.get() == 1, "native primary release action");
            command(environment, "xdotool", "click", "3");
            LinuxX11TestServer.await(() -> menus.size() == 1, "native context request");
            command(environment, "xdotool", "key", "--window", windows, "Return", "space", "Menu", "shift+F10", "Tab", "shift+Tab");
            LinuxX11TestServer.await(() -> actions.get() == 3 && menus.size() == 3, "native keyboard and menu actions");
            if (options.containsKey("--lifecycle")) {
                Process livePanel = owned.getLast(); long panelPid = livePanel.pid();
                int marker = observations.size(); var staleMenu = menus.getLast();
                String preferences = null;
                if (panel.equals("lxde")) {
                    command(environment, "lxpanelctl", "config"); Thread.sleep(300);
                    preferences = command(environment, "xdotool", "search", "--name", "^Panel Preferences$").strip();
                    command(environment, "xdotool", "mousemove", "--window", preferences, "220", "20", "click", "1");
                    command(environment, "xdotool", "mousemove", "--window", preferences, "100", "80", "click", "1");
                    command(environment, "xdotool", "mousemove", "--window", preferences, "370", "84", "click", "1");
                } else {
                    command(environment, "xfce4-panel", "--preferences=1"); Thread.sleep(250);
                    preferences = command(environment, "xdotool", "search", "--name", "^Panel Preferences$").strip();
                    command(environment, "xdotool", "mousemove", "--window", preferences, "230", "74", "click", "1"); Thread.sleep(150);
                    command(environment, "xdotool", "mousemove", "--window", preferences, "130", "118", "click", "1");
                    command(environment, "xdotool", "mousemove", "--window", preferences, "100", "473", "click", "1"); Thread.sleep(150);
                    capture(environment, output, "xfce-confirm");
                    Files.writeString(output.resolve("preferences-tree.txt"), command(environment, "xwininfo", "-root", "-tree"));
                    command(environment, "xdotool", "mousemove", "--window", preferences, "305", "347", "click", "1");
                }
                LinuxX11TestServer.await(() -> observations.size() > marker && !observations.getLast(), "live applet removed");
                assertThat(livePanel.isAlive()).isTrue(); tray.removeAndAwait();
                assertThat(command(environment, "xwininfo", "-root", "-tree")).doesNotContain("\"Jasper\"");
                assertThat(staleMenu.valid().getAsBoolean()).isFalse();
                int actionCount = actions.get(); staleMenu.action().accept(1); javax.swing.SwingUtilities.invokeAndWait(() -> {});
                assertThat(actions.get()).isEqualTo(actionCount);
                if (panel.equals("lxde")) {
                    command(environment, "xdotool", "mousemove", "--window", preferences, "370", "52", "click", "1"); Thread.sleep(200);
                    capture(environment, output, "add-applet");
                    Files.writeString(output.resolve("add-tree.txt"), command(environment, "xwininfo", "-root", "-tree"));
                    String add = command(environment, "xdotool", "search", "--name", "^Add plugin to panel$").strip();
                    command(environment, "xdotool", "type", "System Tray"); command(environment, "xdotool", "key", "Escape");
                    command(environment, "xdotool", "mousemove", "--window", add, "270", "377", "click", "1");
                    command(environment, "xdotool", "mousemove", "--window", preferences, "380", "344", "click", "1");
                } else {
                    command(environment, "xdotool", "mousemove", "--window", preferences, "50", "473", "click", "1"); Thread.sleep(150);
                    capture(environment, output, "xfce-add");
                    Files.writeString(output.resolve("add-tree.txt"), command(environment, "xwininfo", "-root", "-tree"));
                    String add = command(environment, "xdotool", "search", "--name", "^Add New Items$").strip();
                    command(environment, "xdotool", "mousemove", "--window", add, "130", "380", "click", "1");
                    command(environment, "xdotool", "mousemove", "--window", add, "266", "481", "click", "1");
                    command(environment, "xdotool", "mousemove", "--window", add, "355", "481", "click", "1");
                    command(environment, "xdotool", "mousemove", "--window", preferences, "313", "532", "click", "1");
                }
                LinuxX11TestServer.await(() -> observations.size() >= marker + 2 && observations.getLast(), "live applet restored");
                assertThat(livePanel.isAlive()).isTrue(); assertThat(livePanel.pid()).isEqualTo(panelPid);
                assertThat(tray.install(id -> actions.incrementAndGet())).isTrue();
                assertThat(command(environment, "xdotool", "search", "--name", "^Jasper$").lines().count()).isEqualTo(1);
                Files.writeString(output.resolve("live-applet.txt"), "PASS samePanelPid=" + panelPid + " removed=true restored=true staleMenuDropped=true oneIcon=true\n");
            }
            var trace = new ArrayList<String>();
            Process currentPanel = owned.getLast();
            for (int cycle = 0; cycle < cycles; cycle++) {
                int marker = observations.size();
                long oldEpoch = (Long)field(tray, "hostEpoch"); int oldIcon = (Integer)field(tray, "icon");
                stop(currentPanel);
                if ((cycle & 1) == 0) {
                    LinuxX11TestServer.await(() -> !observations.getLast(), "slow host loss"); tray.removeAndAwait(); Thread.sleep(150);
                }
                currentPanel = panel.equals("lxde") ? launch(environment, output, "panel-cycle-" + cycle, "lxpanel", "--profile", "jasper-gate")
                    : launch(environment, output, "panel-cycle-" + cycle, "xfce4-panel", "--disable-wm-check");
                owned.add(currentPanel);
                LinuxX11TestServer.await(() -> observations.size() >= marker + 2 && observations.getLast(), "replacement host pair");
                tray.removeAndAwait();
                assertThat(command(environment, "xwininfo", "-root", "-tree")).doesNotContain("\"Jasper\"");
                int actionCount = actions.get(); menus.getFirst().action().accept(1); javax.swing.SwingUtilities.invokeAndWait(() -> {});
                assertThat(actions.get()).isEqualTo(actionCount); assertThat(menus.getFirst().valid().getAsBoolean()).isFalse();
                assertThat(tray.install(id -> actions.incrementAndGet())).as("redock cycle " + cycle).isTrue();
                assertThat((Long)field(tray, "hostEpoch")).isGreaterThan(oldEpoch);
                assertThat(command(environment, "xdotool", "search", "--name", "^Jasper$").lines().count()).isEqualTo(1);
                String row = "cycle=" + cycle + " speed=" + ((cycle & 1) == 0 ? "slow" : "fast") + " oldIcon=" + oldIcon
                    + " newIcon=" + field(tray, "icon") + " hostEpoch=" + field(tray, "hostEpoch") + " oneIcon=true oldActionDropped=true";
                trace.add(row); System.out.println(row);
            }
            Files.write(output.resolve("cycles.txt"), trace);
            if (options.containsKey("--selection-test")) selectionRows(environment, output, tray, currentPanel, observations, menus, actions);
            tray.removeAndAwait(); Thread.sleep(200);
            var removed = capture(environment, output, "removed");
            int trails = 0;
            for (int y = nativeBounds.y; y < nativeBounds.y + nativeBounds.height; y++)
                for (int x = nativeBounds.x; x < nativeBounds.x + nativeBounds.width; x++)
                    if (colourDistance(before.getRGB(x, y), removed.getRGB(x, y)) > 3) trails++;
            assertThat(trails).as("no artwork trails after confirmed removal").isZero();
            stop(currentPanel); LinuxX11TestServer.await(() -> !observations.getLast(), "real panel loss observed");
            assertThat(unexpected).isEmpty();
            Files.writeString(output.resolve("summary.txt"), "PASS legacy " + panel + " noHost=true lateHost=true embedded=true primaryAndKeys=3 menus=3 cycles="
                + cycles + " requestedSlot=" + slot + " actualSlot=" + nativeBounds.width + "x" + nativeBounds.height
                + " background=" + background + " uiScale=" + System.getProperty("sun.java2d.uiScale", "1")
                + " alpha=" + field(tray, "alpha") + " hostLoss=true unexpected=0 trails=0\n");
            System.out.println(Files.readString(output.resolve("summary.txt")));
        } finally {
            // Diagnostics must never prevent cleanup of this harness's explicitly owned processes.
            try {
                Files.writeString(output.resolve("final-tree.txt"), command(environment, "xwininfo", "-root", "-tree"));
                if (panel.equals("xfce")) Files.writeString(output.resolve("final-profile.txt"), command(environment, "xfconf-query", "-c", "xfce4-panel", "-lv"));
                try (var cancel = new LinuxTrayXcb.Cancellation(); var connection = LinuxTrayXcb.connect(environment, deadline(), cancel)) {
                    int selection = connection.internAtom("_NET_SYSTEM_TRAY_S0", deadline());
                    int owner = connection.selectionOwner(selection, deadline());
                    String diagnostic = "selectionOwner=" + owner;
                    if (owner != 0) {
                        int visualId = connection.property32(owner, connection.internAtom("_NET_SYSTEM_TRAY_VISUAL", deadline()),
                            connection.internAtom("VISUALID", deadline()), deadline());
                        diagnostic += " visual=" + visualId + " setupVisual=" + connection.setup().visuals().stream().filter(v -> v.id() == visualId).toList();
                    }
                    Files.writeString(output.resolve("final-owner.txt"), diagnostic + "\n");
                }
            } catch (Exception | AssertionError diagnostic) { Files.writeString(output.resolve("diagnostic-failure.txt"), diagnostic.toString()); }
            Throwable cleanupFailure = null;
            try {
                tray.close(); var worker = (Thread)field(tray, "worker");
                if (worker != null) { worker.join(2500); if (worker.isAlive()) throw new AssertionError("Native worker did not close"); }
            } catch (Exception | AssertionError failure) { cleanupFailure = failure; }
            for (int i = owned.size() - 1; i >= 0; i--) {
                try { stop(owned.get(i)); }
                catch (Exception | AssertionError failure) { if (cleanupFailure == null) cleanupFailure = failure; else cleanupFailure.addSuppressed(failure); }
            }
            Thread.setDefaultUncaughtExceptionHandler(oldHandler);
            try (var paths = Files.walk(home)) { for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path); }
            Files.writeString(output.resolve("cleanup.txt"), "ownedProcesses=" + owned.size() + " alive=" + owned.stream().filter(Process::isAlive).count() + " privateHomeRemoved=" + !Files.exists(home) + "\n");
            if (cleanupFailure != null) throw new AssertionError("Native gate cleanup failed", cleanupFailure);
        }
    }

    /** Supplementary real-server owner; no protocol mock and no production native ownership. */
    public interface FixtureXcb extends com.sun.jna.Library {
        LinuxTrayXcb.Cookie xcb_set_selection_owner(com.sun.jna.Pointer connection, int owner, int selection, int time);
        LinuxTrayXcb.Cookie xcb_reparent_window(com.sun.jna.Pointer connection, int window, int parent, short x, short y);
    }
    private static void fixtureMutation(LinuxTrayXcb connection, java.util.function.Supplier<LinuxTrayXcb.Cookie> request) throws Exception {
        invoke(connection, "mutate", new Class<?>[] {long.class, java.util.function.Supplier.class}, deadline(), request);
        connection.barrier(deadline());
    }
    private static int dockRequest(LinuxTrayXcb connection, int opcode, int owner) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < until) for (var event : connection.poll(until)) {
            var b = java.nio.ByteBuffer.wrap(event.bytes()).order(java.nio.ByteOrder.nativeOrder());
            if (event.type() == 33 && b.get(1) == 32 && b.getInt(4) == owner && b.getInt(8) == opcode && b.getInt(16) == 0)
                return b.getInt(20);
        }
        throw new AssertionError("Actual server fixture did not receive dock request");
    }
    private static void selectionRows(Map<String, String> environment, Path output, LinuxLegacyTray tray, Process panel,
            List<Boolean> observations, List<LinuxLegacyTray.MenuRequest> menus, java.util.concurrent.atomic.AtomicInteger actions) throws Exception {
        var fixture = Native.load("xcb", FixtureXcb.class);
        try (var cancel = new LinuxTrayXcb.Cancellation(); var connection = LinuxTrayXcb.connect(environment, deadline(), cancel)) {
            var pointer = (com.sun.jna.Pointer)field(connection, "connection"); var setup = connection.setup();
            int selection = connection.internAtom("_NET_SYSTEM_TRAY_S0", deadline());
            int manager = connection.internAtom("MANAGER", deadline()); int opcode = connection.internAtom("_NET_SYSTEM_TRAY_OPCODE", deadline());
            int xembed = connection.internAtom("_XEMBED", deadline());
            var visual = setup.visuals().stream().filter(v -> v.id() == setup.rootVisual()).findFirst().orElseThrow();
            int owner = connection.createIcon(setup.root(), visual, 0, 24, deadline());
            connection.property32(owner, connection.internAtom("_NET_SYSTEM_TRAY_VISUAL", deadline()), connection.internAtom("VISUALID", deadline()), new int[] {visual.id()}, deadline());
            int marker = observations.size(); var stale = menus.getLast();
            fixtureMutation(connection, () -> fixture.xcb_set_selection_owner(pointer, owner, selection, 0));
            connection.operationDeadline(deadline()); connection.sendClientMessage(setup.root(), setup.root(), manager, 1 << 17, 0, selection, owner, 0, 0); connection.barrier(deadline());
            LinuxX11TestServer.await(() -> observations.size() >= marker + 2 && observations.getLast(), "selection replacement without panel death");
            assertThat(panel.isAlive()).isTrue(); tray.removeAndAwait();
            assertThat(command(environment, "xwininfo", "-root", "-tree")).doesNotContain("\"Jasper\"");
            int before = actions.get(); stale.action().accept(1); javax.swing.SwingUtilities.invokeAndWait(() -> {});
            assertThat(actions.get()).isEqualTo(before); assertThat(stale.valid().getAsBoolean()).isFalse();
            for (boolean accept : new boolean[] {true, false}) {
                var result = new AtomicReference<Boolean>(); var failure = new AtomicReference<Throwable>();
                Thread installer = new Thread(() -> { try { result.set(tray.install(id -> actions.incrementAndGet())); } catch (Throwable error) { failure.set(error); } }, "fixture-install-caller");
                installer.setDaemon(true); installer.start();
                try {
                    int icon = dockRequest(connection, opcode, owner);
                    if (accept) {
                        fixtureMutation(connection, () -> fixture.xcb_reparent_window(pointer, icon, owner, (short)0, (short)0));
                        connection.operationDeadline(deadline()); connection.sendClientMessage(icon, icon, xembed, 0, 0, 0, 0, owner, 0); connection.barrier(deadline());
                    } else {
                        fixtureMutation(connection, () -> fixture.xcb_set_selection_owner(pointer, 0, selection, 0));
                        connection.operationDeadline(deadline()); connection.sendClientMessage(setup.root(), setup.root(), manager, 1 << 17, 0, selection, 0, 0, 0); connection.barrier(deadline());
                    }
                    installer.join(2500); assertThat(installer.isAlive()).isFalse(); assertThat(failure.get()).isNull();
                    assertThat(result.get()).isEqualTo(accept); tray.removeAndAwait();
                    assertThat(command(environment, "xwininfo", "-root", "-tree")).doesNotContain("\"Jasper\"");
                } finally { if (installer.isAlive()) { tray.close(); installer.join(2500); } }
            }
            assertThat(panel.isAlive()).isTrue();
            Files.writeString(output.resolve("selection-rows.txt"), "PASS replacement=true samePanelPid=" + panel.pid() + " acceptedNewOwner=true lossDuringPendingDock=falseConfirmedClean staleMenuDropped=true\n");
        }
    }

    private static java.awt.image.BufferedImage capture(Map<String, String> environment, Path output, String name) throws Exception {
        command(environment, "xdotool", "mousemove", "1200", "700");
        command(environment, "xdotool", "getmouselocation", "--shell");
        Thread.sleep(50); // Let the owned compositor consume the completed pointer/damage events.
        command(environment, "xwd", "-root", "-silent", "-out", output.resolve(name + ".xwd").toString());
        String framebuffer = environment.get("JASPER_TRAY_FRAMEBUFFER");
        if (framebuffer != null) {
            Files.move(output.resolve(name + ".xwd"), output.resolve(name + "-root.xwd"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.copy(Path.of(framebuffer), output.resolve(name + ".xwd"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        command(environment, "magick", output.resolve(name + ".xwd").toString(), "PNG24:" + output.resolve(name + ".png"));
        return javax.imageio.ImageIO.read(output.resolve(name + ".png").toFile());
    }
    private static void verifyArtwork(java.awt.image.BufferedImage before, java.awt.image.BufferedImage after,
                                      java.awt.Rectangle bounds, Path output) throws Exception {
        assertThat(bounds.x).isGreaterThanOrEqualTo(0); assertThat(bounds.y).isGreaterThanOrEqualTo(0);
        assertThat(bounds.x + bounds.width).isLessThanOrEqualTo(after.getWidth());
        assertThat(bounds.y + bounds.height).isLessThanOrEqualTo(after.getHeight());
        var artwork = LinuxLegacyTray.raster(bounds.width, bounds.height); int mismatches = 0, corners = 0, visible = 0, maxError = 0;
        for (int y = 0; y < bounds.height; y++) for (int x = 0; x < bounds.width; x++) {
            int source = artwork.getRGB(x, y), alpha = source >>> 24, bg = before.getRGB(bounds.x + x, bounds.y + y), expected = 0;
            for (int shift : new int[] {0, 8, 16}) expected |= ((((source >>> shift & 255) * alpha
                + (bg >>> shift & 255) * (255 - alpha) + 127) / 255) << shift);
            int error = colourDistance(expected, after.getRGB(bounds.x + x, bounds.y + y));
            maxError = Math.max(maxError, error); if (error > 3) mismatches++;
            if (alpha == 0) { corners++; assertThat(error).as("transparent background pixel " + x + "," + y).isLessThanOrEqualTo(3); }
            else visible++;
        }
        Files.writeString(output.resolve("pixels.txt"), "visiblePixels=" + visible + " transparentPixels=" + corners + " mismatches=" + mismatches + " maxChannelError=" + maxError + "\n");
        assertThat(visible).isPositive(); assertThat(corners).isPositive();
        assertThat(mismatches).as("full artwork matches premultiplied composition").isLessThanOrEqualTo(1);
    }
    private static int colourDistance(int first, int second) {
        int result = 0; for (int shift : new int[] {0, 8, 16}) result = Math.max(result, Math.abs((first >>> shift & 255) - (second >>> shift & 255)));
        return result;
    }

    private static Process launch(Map<String, String> env, Path output, String name, String... command) throws Exception {
        var builder = new ProcessBuilder(command); builder.environment().clear(); builder.environment().putAll(env);
        Process process = builder.redirectErrorStream(true).redirectOutput(output.resolve(name + ".log").toFile()).start();
        Files.writeString(output.resolve(name + ".pid"), process.pid() + "\n"); return process;
    }
    private static String command(Map<String, String> env, String... command) throws Exception {
        var builder = new ProcessBuilder(command); builder.environment().clear(); builder.environment().putAll(env);
        var process = builder.redirectErrorStream(true).start();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) throw new AssertionError("Command timed out: " + command[0]);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) throw new AssertionError("Command failed: " + command[0] + " " + output);
            return output;
        } finally { stop(process); }
    }
    private static void stop(Process process) throws Exception {
        var descendants = process.descendants().toList();
        if (process.isAlive()) {
            process.destroy(); if (!process.waitFor(2, TimeUnit.SECONDS)) { process.destroyForcibly(); if (!process.waitFor(2, TimeUnit.SECONDS)) throw new AssertionError("Owned child stuck"); }
        }
        for (var child : descendants) if (child.isAlive()) {
            child.destroy();
            try { child.onExit().get(2, TimeUnit.SECONDS); }
            catch (java.util.concurrent.TimeoutException timeout) { child.destroyForcibly(); child.onExit().get(2, TimeUnit.SECONDS); }
        }
    }

    private static void warm() throws Exception {
        try (var server = LinuxX11TestServer.filesystem(); var cancel = new LinuxTrayXcb.Cancellation()) {
            var audit = new Audit(cancel);
            try (var xcb = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)) {
                xcb.internAtom("warm", deadline()); xcb.barrier(deadline());
            }
            audit.clean();
        }
        var file = Files.createTempFile("jasper-xcb-warm-", ""); Files.delete(file);
        Thread.sleep(200);
    }
    private static int fdCount() {
        try (var files = Files.list(Path.of("/proc/self/fd"))) { return (int)files.count(); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static Set<String> threads() {
        return Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive)
            .map(Thread::getName).collect(java.util.stream.Collectors.toSet());
    }

    static void stall(String phase, boolean abstractAddress, boolean close) throws Exception {
        try (var server = abstractAddress ? LinuxX11TestServer.abstractSocket() : LinuxX11TestServer.filesystem()) {
            server.mode = phase.equals("setup") ? LinuxX11TestServer.Mode.NO_SETUP : LinuxX11TestServer.Mode.NO_REPLY;
            if (phase.equals("explicit") || phase.equals("implicit")) server.pauseReads();
            var holder = new AtomicReference<LinuxTrayXcb.Cancellation>();
            var auditHolder = new AtomicReference<Audit>();
            var failure = new AtomicReference<Throwable>();
            var entered = new CountDownLatch(1);
            var returned = new CountDownLatch(1);
            long[] operationStart = {0};
            var worker = Thread.ofPlatform().daemon().name("jasper-xcb-gate-worker").start(() -> {
                try (var cancel = new LinuxTrayXcb.Cancellation()) {
                    holder.set(cancel); var audit = new Audit(cancel); auditHolder.set(audit);
                    if (phase.equals("setup")) audit.before = (name, args) -> {
                        if (name.equals("xcb_connect_to_fd")) { operationStart[0] = System.nanoTime(); entered.countDown(); }
                    };
                    try (var xcb = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)) {
                        if (phase.equals("setup")) throw new AssertionError("setup unexpectedly returned");
                        if (phase.equals("reply")) {
                            operationStart[0] = System.nanoTime(); entered.countDown();
                            xcb.internAtom("unanswered", deadline());
                        } else {
                            server.awaitPaused();
                            int fd = (Integer)field(xcb, "socketFd");
                            fillSocket(fd);
                            String expected = phase.equals("explicit") ? "xcb_flush" : "xcb_intern_atom";
                            audit.before = (name, args) -> {
                                if (name.equals(expected)) { operationStart[0] = System.nanoTime(); entered.countDown(); }
                            };
                            if (phase.equals("explicit")) {
                                invoke(xcb, "requestAtom", new Class<?>[] {String.class, long.class}, "queued", deadline());
                                invoke(xcb, "flush", new Class<?>[] {long.class}, deadline());
                            } else invoke(xcb, "requestAtom", new Class<?>[] {String.class, long.class}, "x".repeat(65535), deadline());
                        }
                        throw new AssertionError("stalled operation unexpectedly succeeded");
                    }
                } catch (Throwable e) { failure.set(e); }
                finally { returned.countDown(); }
            });
            try {
                if (!entered.await(5, TimeUnit.SECONDS)) throw new AssertionError("native phase not entered: " + phase, failure.get());
                server.awaitSetup();
                if (phase.equals("reply")) LinuxX11TestServer.await(() -> server.requests > 0, "reply request never reached fixture");
                Thread.sleep(100);
                assertThat(returned.getCount()).as("operation must be genuinely stalled").isEqualTo(1);
                if (!phase.equals("reply")) assertThat(auditHolder.get().active.get()).as("native call still active").isEqualTo(1);
                if (close) {
                    long start = System.nanoTime(); holder.get().requestClose();
                    assertThat(System.nanoTime() - start).as("nonblocking close nanos").isLessThan(TimeUnit.MILLISECONDS.toNanos(100));
                }
                long remaining = operationStart[0] + TimeUnit.MILLISECONDS.toNanos(2500) - System.nanoTime();
                worker.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
                assertThat(worker.isAlive()).as("native worker must actually terminate").isFalse();
                assertThat(failure.get()).isInstanceOf(LinuxTrayXcb.Unavailable.class);
                Audit audit = auditHolder.get(); audit.clean();
                assertThat(audit.disconnects.get()).isEqualTo(1);
                assertThat((Integer)field(holder.get(), "duplicate")).isEqualTo(-1);
                System.out.println("phase=" + phase + " socket=" + (abstractAddress ? "abstract" : "filesystem") + " cancel=" + close
                    + " elapsedMs=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - operationStart[0]) + " nativeEntered=true workerCompleted=true");
            } finally {
                if (holder.get() != null) holder.get().requestClose(); server.drop();
                worker.join(500);
                if (worker.isAlive()) throw new AssertionError("native worker stranded after cleanup");
            }
        }
    }
    private static void fillSocket(int fd) {
        long total = 0;
        try (var bytes = new Memory(65536)) {
            bytes.clear();
            for (;;) {
                long sent = LinuxX11TestServer.C.send(fd, bytes, new NativeLong(bytes.size()), 0x40 | 0x4000).longValue();
                if (sent < 0) {
                    if (Native.getLastError() == 4) continue;
                    assertThat(Native.getLastError()).isEqualTo(11); break;
                }
                total += sent;
                if (total > 16 * 1024 * 1024) throw new AssertionError("socket never backpressured");
            }
        }
        assertThat(total).isPositive();
        System.out.println("backpressureBytes=" + total + " sendReturnedEagain=true");
    }

    static void reuse() throws Exception {
        try (var cancel = new LinuxTrayXcb.Cancellation(); var byteValue = new Memory(1)) {
            for (int i = 0; i < 32; i++) {
                int[] pair = {-1, -1}; assertThat(LinuxX11TestServer.C.socketpair(1, 1 | 0x80000, 0, pair)).isZero();
                invoke(cancel, "arm", new Class<?>[] {int.class, long.class}, pair[0], deadline());
                int oldDuplicate = (Integer)field(cancel, "duplicate"); long oldEpoch = (Long)field(cancel, "epoch");
                invoke(cancel, "disarm", new Class<?>[0]);
                LinuxX11TestServer.C.close(pair[0]); LinuxX11TestServer.C.close(pair[1]);
                int[] replacement = {-1, -1}; assertThat(LinuxX11TestServer.C.socketpair(1, 1 | 0x80000, 0, replacement)).isZero();
                assertThat(oldDuplicate).isNotEqualTo(replacement[1]);
                assertThat(LinuxX11TestServer.C.dup2(replacement[0], oldDuplicate)).isEqualTo(oldDuplicate);
                try {
                    var failure = new AtomicReference<Throwable>();
                    Thread callback = Thread.ofPlatform().name("jasper-old-deadline").start(() -> {
                        try { invoke(cancel, "expire", new Class<?>[] {long.class}, oldEpoch); }
                        catch (Throwable e) { failure.set(e); }
                    });
                    callback.join(1000); assertThat(callback.isAlive()).isFalse(); assertThat(failure.get()).isNull();
                    byteValue.setByte(0, (byte)42);
                    assertThat(LinuxX11TestServer.C.send(oldDuplicate, byteValue, new NativeLong(1), 0x4000).longValue()).isEqualTo(1);
                    assertThat(LinuxX11TestServer.C.read(replacement[1], byteValue, new NativeLong(1)).longValue()).isEqualTo(1);
                    assertThat(byteValue.getByte(0)).isEqualTo((byte)42);
                } finally {
                    LinuxX11TestServer.C.close(oldDuplicate);
                    if (replacement[0] != oldDuplicate) LinuxX11TestServer.C.close(replacement[0]);
                    LinuxX11TestServer.C.close(replacement[1]);
                }
            }
        }
        System.out.println("fdReuseIterations=32 delayedOldCallbacks=32 replacementSocketsUsable=32");
    }

    static void heldAuthority() throws Exception {
        for (String heldCall : List.of("open", "read")) {
            Path path = Files.createTempFile("jasper-held-authority-", ".fixture"); Files.write(path, new byte[10]);
            try (var server = LinuxX11TestServer.filesystem()) {
                var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
                var holder = new AtomicReference<LinuxTrayXcb.Cancellation>(); var auditHolder = new AtomicReference<Audit>();
                var failure = new AtomicReference<Throwable>();
                Thread worker = Thread.ofPlatform().daemon().name("jasper-held-authority").start(() -> {
                    try (var cancel = new LinuxTrayXcb.Cancellation()) {
                        holder.set(cancel); var audit = new Audit(cancel); auditHolder.set(audit);
                        audit.before = (name, args) -> {
                            if (name.equals(heldCall)) {
                                entered.countDown();
                                try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("held authority fixture not released"); }
                                catch (InterruptedException e) { throw new AssertionError(e); }
                            }
                        };
                        try (var ignored = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address(), "XAUTHORITY", path.toString()), deadline(), cancel)) {
                            throw new AssertionError("late authority result connected to root " + ignored.setup().root());
                        }
                    } catch (Throwable e) { failure.set(e); }
                });
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue(); assertThat(worker.isDaemon()).isTrue();
                    long start = System.nanoTime(); holder.get().requestClose();
                    assertThat(System.nanoTime() - start).isLessThan(TimeUnit.MILLISECONDS.toNanos(100));
                    assertThat(worker.isAlive()).isTrue();
                    assertThat(auditHolder.get().connects.get()).isZero();
                    assertThatThrownBy(() -> LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), holder.get()))
                        .isInstanceOf(IllegalStateException.class); // owner/slot refuses a concurrent attempt
                    release.countDown(); worker.join(1000); assertThat(worker.isAlive()).isFalse();
                    assertThat(failure.get()).isInstanceOf(LinuxTrayXcb.Unavailable.class);
                    auditHolder.get().clean(); assertThat(auditHolder.get().connects.get()).isZero();
                    assertThat(server.bytesRead()).isZero();
                    System.out.println("heldAuthority=" + heldCall + " pendingWorkers=1 closeResponsive=true lateConnects=0 cleanupAfterRelease=true");
                } finally { release.countDown(); worker.join(1000); if (worker.isAlive()) throw new AssertionError("held authority fixture stranded"); }
            } finally { Files.delete(path); }
        }
    }
    private static void cycles() throws Exception {
        for (int i = 0; i < 4; i++) {
            for (var mode : List.of(LinuxX11TestServer.Mode.BAD_AUTH, LinuxX11TestServer.Mode.BAD_WINDOW, LinuxX11TestServer.Mode.BAD_MATCH, LinuxX11TestServer.Mode.NORMAL)) {
                try (var server = i % 2 == 0 ? LinuxX11TestServer.filesystem() : LinuxX11TestServer.abstractSocket(); var cancel = new LinuxTrayXcb.Cancellation()) {
                    server.mode = mode; var audit = new Audit(cancel);
                    if (mode == LinuxX11TestServer.Mode.BAD_AUTH) {
                        assertThatThrownBy(() -> LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)).isInstanceOf(LinuxTrayXcb.Unavailable.class);
                    } else try (var xcb = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)) {
                        if (mode != LinuxX11TestServer.Mode.NORMAL)
                            assertThatThrownBy(() -> xcb.selectionOwner(77, deadline())).isInstanceOf(LinuxTrayXcb.Unavailable.class);
                        else { assertThat(xcb.internAtom("reconnected", deadline())).isEqualTo(77); server.drop();
                            assertThatThrownBy(() -> xcb.barrier(deadline())).isInstanceOf(LinuxTrayXcb.Unavailable.class); }
                    }
                    audit.clean(); assertThat(audit.disconnects.get()).isEqualTo(1);
                }
            }
        }
        System.out.println("fastRecoveryCycles=4 errors=BadAuth,BadWindow,BadMatch,EOF reconnects=4");
    }

    static void abi(Path output) throws Exception {
        Files.createDirectories(output);
        Files.writeString(output.resolve("probe.pid"), ProcessHandle.current().pid() + "\n");
        Path source = Path.of("src/test/native/linux-tray-abi.c").toAbsolutePath();
        if (!Files.exists(source)) source = Path.of("jasper-app/src/test/native/linux-tray-abi.c").toAbsolutePath();
        Path binary = output.toAbsolutePath().resolve("linux-tray-abi");
        var compile = new ArrayList<>(List.of("cc", "-std=c11", "-Wall", "-Wextra", "-Werror"));
        Process flags = new ProcessBuilder("pkg-config", "--cflags", "xcb", "xcb-render", "xau").redirectErrorStream(true).start();
        if (!flags.waitFor(5, TimeUnit.SECONDS)) { flags.destroyForcibly(); throw new AssertionError("pkg-config stuck"); }
        String cflags = new String(flags.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (flags.exitValue() == 0 && !cflags.isEmpty()) compile.addAll(List.of(cflags.split("\\s+")));
        String include = System.getProperty("jasper.tray.headers"); if (include != null) compile.add("-I" + include);
        compile.addAll(List.of(source.toString(), "-o", binary.toString()));
        Path compilerLog = output.resolve("abi-compiler.log");
        Process compiler = new ProcessBuilder(compile).redirectErrorStream(true).redirectOutput(compilerLog.toFile()).start();
        if (!compiler.waitFor(5, TimeUnit.SECONDS)) { compiler.destroyForcibly(); throw new AssertionError("ABI compiler stuck"); }
        assertThat(compiler.exitValue()).withFailMessage("ABI compiler failed: %s", Files.readString(compilerLog)).isZero();
        Path cOutput = output.resolve("abi-c.txt");
        Process helper = new ProcessBuilder(binary.toString()).redirectErrorStream(true).redirectOutput(cOutput.toFile()).start();
        if (!helper.waitFor(5, TimeUnit.SECONDS)) { helper.destroyForcibly(); throw new AssertionError("ABI helper stuck"); }
        assertThat(helper.exitValue()).isZero();
        Map<String, Long> actual = values(Files.readString(cOutput));
        String expectedText = """
            void *=8
            long=8
            xcb_window_t=4
            xcb_atom_t=4
            xcb_void_cookie_t=4
            xcb_void_cookie_t.sequence=0
            xcb_generic_event_t=36
            xcb_generic_event_t.response_type=0
            xcb_generic_event_t.sequence=2
            xcb_generic_event_t.full_sequence=32
            xcb_generic_error_t=36
            xcb_generic_error_t.error_code=1
            xcb_generic_error_t.sequence=2
            xcb_generic_error_t.full_sequence=32
            xcb_client_message_event_t=32
            xcb_client_message_event_t.format=1
            xcb_client_message_event_t.window=4
            xcb_client_message_event_t.type=8
            xcb_client_message_event_t.data=12
            xcb_auth_info_t=32
            xcb_auth_info_t.namelen=0
            xcb_auth_info_t.name=8
            xcb_auth_info_t.datalen=16
            xcb_auth_info_t.data=24
            Xauth=64
            Xauth.family=0
            Xauth.address_length=2
            Xauth.address=8
            Xauth.number_length=16
            Xauth.number=24
            Xauth.name_length=32
            Xauth.name=40
            Xauth.data_length=48
            Xauth.data=56
            struct pollfd=8
            struct pollfd.fd=0
            struct pollfd.events=4
            struct pollfd.revents=6
            struct sockaddr_un=110
            struct sockaddr_un.sun_family=0
            struct sockaddr_un.sun_path=2
            struct stat.st_dev=0
            struct stat.st_ino=8
            struct stat.st_size=48
            struct stat.st_mtim.tv_sec=88
            struct stat.st_mtim.tv_nsec=96
            struct stat.st_ctim.tv_sec=104
            struct stat.st_ctim.tv_nsec=112
            xcb_setup_t=40
            xcb_setup_t.status=0
            xcb_setup_t.length=6
            xcb_setup_t.vendor_len=24
            xcb_setup_t.roots_len=28
            xcb_setup_t.pixmap_formats_len=29
            xcb_setup_t.image_byte_order=30
            xcb_screen_t=40
            xcb_screen_t.root=0
            xcb_screen_t.root_visual=32
            xcb_screen_t.root_depth=38
            xcb_screen_t.allowed_depths_len=39
            xcb_depth_t=8
            xcb_depth_t.depth=0
            xcb_depth_t.visuals_len=2
            xcb_visualtype_t=24
            xcb_visualtype_t.visual_id=0
            xcb_visualtype_t.red_mask=8
            xcb_visualtype_t.green_mask=12
            xcb_visualtype_t.blue_mask=16
            xcb_format_t=8
            xcb_format_t.depth=0
            xcb_format_t.bits_per_pixel=1
            xcb_format_t.scanline_pad=2
            xcb_generic_reply_t=8
            xcb_generic_reply_t.response_type=0
            xcb_generic_reply_t.sequence=2
            xcb_generic_reply_t.length=4
            xcb_intern_atom_reply_t.atom=8
            xcb_get_selection_owner_reply_t.owner=8
            xcb_get_property_reply_t=32
            xcb_get_property_reply_t.format=1
            xcb_get_property_reply_t.type=8
            xcb_get_property_reply_t.bytes_after=12
            xcb_get_property_reply_t.value_len=16
            xcb_get_geometry_reply_t.depth=1
            xcb_get_geometry_reply_t.width=16
            xcb_get_geometry_reply_t.height=18
            xcb_query_tree_reply_t.parent=12
            xcb_translate_coordinates_reply_t.dst_x=12
            xcb_translate_coordinates_reply_t.dst_y=14
            xcb_get_keyboard_mapping_reply_t.keysyms_per_keycode=1
            xcb_render_query_version_reply_t.major_version=8
            xcb_render_query_version_reply_t.minor_version=12
            xcb_render_query_pict_formats_reply_t=32
            xcb_render_query_pict_formats_reply_t.num_formats=8
            xcb_render_query_pict_formats_reply_t.num_screens=12
            xcb_render_query_pict_formats_reply_t.num_depths=16
            xcb_render_query_pict_formats_reply_t.num_visuals=20
            xcb_render_query_pict_formats_reply_t.num_subpixel=24
            xcb_render_pictforminfo_t=28
            xcb_render_pictforminfo_t.id=0
            xcb_render_pictforminfo_t.type=4
            xcb_render_pictforminfo_t.depth=5
            xcb_render_pictforminfo_t.direct=8
            xcb_render_directformat_t=16
            xcb_render_directformat_t.red_shift=0
            xcb_render_directformat_t.red_mask=2
            xcb_render_directformat_t.green_shift=4
            xcb_render_directformat_t.blue_shift=8
            xcb_render_directformat_t.alpha_shift=12
            xcb_render_pictscreen_t=8
            xcb_render_pictdepth_t=8
            xcb_render_pictdepth_t.num_visuals=2
            xcb_render_pictvisual_t=8
            AF_UNIX=1
            SOCK_STREAM=1
            SOCK_NONBLOCK=2048
            SOCK_CLOEXEC=524288
            SHUT_RDWR=2
            SOL_SOCKET=1
            SO_ERROR=4
            EINPROGRESS=115
            EAGAIN=11
            EINTR=4
            POLLIN=1
            POLLOUT=4
            POLLERR=8
            POLLHUP=16
            O_NONBLOCK=2048
            O_CLOEXEC=524288
            O_NOFOLLOW=131072
            F_DUPFD_CLOEXEC=1030
            S_IFMT=61440
            S_IFREG=32768
            FamilyLocal=256
            FamilyWild=65535
            """;
        Map<String, Long> expected = values(expectedText);
        boolean arm = Set.of("aarch64", "arm64").contains(System.getProperty("os.arch"));
        expected.put("O_NOFOLLOW", arm ? 32768L : 131072L);
        expected.put("struct stat", arm ? 128L : 144L); expected.put("struct stat.st_mode", arm ? 16L : 24L);
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
        assertThat(Native.POINTER_SIZE).isEqualTo(8); assertThat(Native.LONG_SIZE).isEqualTo(8);
        var cookie = new LinuxTrayXcb.Cookie(); var auth = new LinuxTrayXcb.Auth();
        try {
            assertThat(cookie.size()).isEqualTo(4); assertThat(auth.size()).isEqualTo(32);
            var offset = Structure.class.getDeclaredMethod("fieldOffset", String.class); offset.setAccessible(true);
            assertThat(offset.invoke(cookie, "sequence")).isEqualTo(0);
            for (String name : List.of("namelen", "name", "datalen", "data"))
                assertThat(((Number)offset.invoke(auth, name)).longValue()).isEqualTo(actual.get("xcb_auth_info_t." + name));
        } finally { ((Memory)cookie.getPointer()).close(); ((Memory)auth.getPointer()).close(); }
        Files.writeString(output.resolve("abi-java.txt"), "PASS exact C values=" + actual.size() + " JNA cookie/auth layouts match\n" + expected);
        System.out.println("abiExactValues=" + actual.size() + " pointerBytes=8 longBytes=8 xidBytes=4");
    }
    private static Map<String, Long> values(String text) {
        var result = new LinkedHashMap<String, Long>();
        for (String line : text.strip().split("\\R")) {
            String[] entry = line.strip().split("=", 2); result.put(entry[0], Long.parseLong(entry[1]));
        }
        return result;
    }
}
