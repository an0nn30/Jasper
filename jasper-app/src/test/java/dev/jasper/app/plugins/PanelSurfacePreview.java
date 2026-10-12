package dev.jasper.app.plugins;

import dev.jasper.app.appearance.SurfaceFixtures;
import java.awt.*;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.swing.*;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;

/**
 * Source-scale (device 2) captures of the actual Tunnels, SFTP and File Servers panels and the New Tunnel dialog, with
 * the raw bounds of their named anchors beside every PNG. Arguments follow {@code SurfacePreview}: output directory,
 * surface filter, theme, state, system font size (handled by SurfacePreview) and constrained logical width.
 */
public final class PanelSurfacePreview {
    private PanelSurfacePreview() {}
    public static void main(String[] args) throws Exception { capture(Path.of(args[0]), args); }

    static final String[][] SESSION_HOSTS = {{"amp-amp", "100.110.65.45", "amp", "Sessions"}, {"pve", "100.73.226.91", "root", ""},
        {"dustin-vm", "100.83.72.100", "ubuntu", ""}};

    public static void capture(Path output, String[] args) throws Exception {
        String filter = args.length > 1 ? args[1] : "";
        for (String theme : args.length > 2 ? List.of(args[2]) : List.of("calibration", "dark", "light", "custom")) {
            if (wanted(filter, "tunnels-panel")) tunnels(output, theme, args);
            if (wanted(filter, "sftp-panel")) sftp(output, theme, args);
            if (wanted(filter, "fileservers-panel")) fileServers(output, theme, args);
            if (wanted(filter, "tunnel-new")) tunnelNew(output, theme, args);
        }
    }
    /** Calibration colors are fixture themes; New Tunnel has its own measured family. */
    static dev.jasper.app.appearance.ResolvedTheme theme(String name) throws Exception {
        if (!name.equals("tunnel-calibration")) return SurfaceFixtures.theme(name);
        try (var input = PanelSurfacePreview.class.getResourceAsStream("/dev/jasper/app/appearance/desktop-tunnel.toml")) {
            if (input == null) throw new IllegalStateException("desktop-tunnel.toml");
            var generated = dev.jasper.ui.theme.ThemeGenerator.generate(dev.jasper.ui.themeio.ThemeToml.parse(
                new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), "desktop-tunnel.toml"));
            return new dev.jasper.app.appearance.ResolvedTheme(generated, generated);
        }
    }
    private static boolean wanted(String filter, String id) { return filter.isEmpty() || id.contains(filter); }
    private static String calibration(String theme) { return theme.equals("calibration") ? "sessions-calibration" : theme; }
    private static int width(String[] args) { return args.length > 5 ? Integer.parseInt(args[5]) : com.formdev.flatlaf.util.UIScale.scale(260); }
    private static int height() { return com.formdev.flatlaf.util.UIScale.scale(395); }

    private static void tunnels(Path output, String theme, String[] args) throws Exception {
        Path home = Files.createTempDirectory("jasper-tunnels-preview-");
        try (var opened = new PanelSurfaceFixture(home, theme(calibration(theme)), PanelSurfaceFixture.TUNNELS)) {
            int width = width(args), height = height();
            opened.size(width, height); save(output, theme, "tunnels-panel", "empty", opened, args);
            // plugin-panels.png's bottom-left Tunnels panel is 418 logical pixels tall.
            opened.size(width, com.formdev.flatlaf.util.UIScale.scale(418)); save(output, theme, "tunnels-panel", "empty-418", opened, args);
            var hosts = opened.hosts(SESSION_HOSTS);
            UUID postgres = opened.tunnel("Postgres", hosts.get(0), "LOCAL", 5432, "localhost", 5432);
            UUID web = opened.tunnel("Web preview", hosts.get(1), "LOCAL", 8080, "localhost", 80);
            UUID socks = opened.tunnel("SOCKS proxy", hosts.get(2), "DYNAMIC", 1080, "", 0);
            UUID metrics = opened.tunnel("Metrics", hosts.get(1), "REMOTE", 9090, "localhost", 9090);
            opened.tunnelStates(Map.of(web, "up", socks, "starting:Authenticating…", metrics, "failed:Port 9090 is in use on the server"));
            opened.size(width, height); save(output, theme, "tunnels-panel", "populated", opened, args);
            opened.size(com.formdev.flatlaf.util.UIScale.scale(200), height); save(output, theme, "tunnels-panel", "narrow", opened, args);
            opened.size(width, com.formdev.flatlaf.util.UIScale.scale(140)); save(output, theme, "tunnels-panel", "short", opened, args);
            SwingUtilities.invokeAndWait(() -> ((javax.swing.text.JTextComponent) opened.component("tunnels.filter.input")).setText("no matching synthetic tunnel"));
            opened.size(width, height); save(output, theme, "tunnels-panel", "filtered-empty", opened, args);
            SwingUtilities.invokeAndWait(() -> ((javax.swing.text.JTextComponent) opened.component("tunnels.filter.input")).setText(""));
            opened.size(width, height); save(output, theme, "tunnels-panel", "unfiltered", opened, args);
            if (postgres == null) throw new AssertionError();
        } finally { delete(home); }
    }

    private static void sftp(Path output, String theme, String[] args) throws Exception {
        Path home = Files.createTempDirectory("jasper-sftp-preview-");
        try (var opened = new PanelSurfaceFixture(home, theme(calibration(theme)), PanelSurfaceFixture.SFTP)) {
            int width = width(args), height = height();
            opened.size(width, height); save(output, theme, "sftp-panel", "disconnected", opened, args);
            // plugin-panels.png's top-right SFTP panel is 419 logical pixels tall.
            opened.size(width, com.formdev.flatlaf.util.UIScale.scale(419)); save(output, theme, "sftp-panel", "disconnected-419", opened, args);
            opened.hosts(SESSION_HOSTS);
            opened.sftp("busy", new Class<?>[] {boolean.class, String.class}, false, "Cancelled");
            opened.size(width, height); save(output, theme, "sftp-panel", "cancelled", opened, args);
            opened.sftp("busy", new Class<?>[] {boolean.class, String.class}, true, "Connecting…");
            opened.size(width, height); save(output, theme, "sftp-panel", "connecting", opened, args);
            opened.sftp("showPage", new Class<?>[] {String.class, String.class, List.class, long.class, long.class, boolean.class},
                "amp-amp", "/home/amp/projects", List.of(opened.fileEntry("logs", "DIRECTORY", 0), opened.fileEntry("src", "DIRECTORY", 0),
                    opened.fileEntry("README.md", "FILE", 4_210), opened.fileEntry("build.gradle.kts", "FILE", 18_400), opened.fileEntry("current", "LINK", 10)), 0L, 5L, false);
            opened.size(width, height); save(output, theme, "sftp-panel", "connected", opened, args);
            opened.sftp("error", new Class<?>[] {String.class}, "Permission denied: /root");
            opened.size(width, height); save(output, theme, "sftp-panel", "error", opened, args);
            opened.size(com.formdev.flatlaf.util.UIScale.scale(200), height); save(output, theme, "sftp-panel", "narrow", opened, args);
            opened.size(width, com.formdev.flatlaf.util.UIScale.scale(160)); save(output, theme, "sftp-panel", "short", opened, args);
            opened.sftp("clear", new Class<?>[] {String.class}, "");
            opened.size(width, height); save(output, theme, "sftp-panel", "cleared", opened, args);
        } finally { delete(home); }
    }

    private static void fileServers(Path output, String theme, String[] args) throws Exception {
        Path home = Files.createTempDirectory("jasper-fileservers-preview-");
        try (var opened = new PanelSurfaceFixture(home, theme(calibration(theme)), PanelSurfaceFixture.FILE_SERVERS)) {
            int width = width(args), height = height();
            opened.size(width, height); save(output, theme, "fileservers-panel", "empty", opened, args);
            opened.fileServer("tester", "HTTP");
            // plugin-panels.png's bottom-right File Servers panel is 418 logical pixels tall.
            opened.size(width, com.formdev.flatlaf.util.UIScale.scale(418)); save(output, theme, "fileservers-panel", "populated", opened, args);
            opened.fileServer("A shared downloads folder with a long synthetic name", "SFTP");
            opened.fileServer("ftp-drop", "FTP");
            opened.fileServer("pxe", "TFTP");
            opened.size(width, height); save(output, theme, "fileservers-panel", "many", opened, args);
            opened.size(com.formdev.flatlaf.util.UIScale.scale(200), height); save(output, theme, "fileservers-panel", "narrow", opened, args);
            opened.size(width, com.formdev.flatlaf.util.UIScale.scale(120)); save(output, theme, "fileservers-panel", "short", opened, args);
        } finally { delete(home); }
    }

    private static void tunnelNew(Path output, String theme, String[] args) throws Exception {
        for (String state : List.of("local", "remote", "dynamic", "valid", "invalid", "warning", "error", "allow-others", "edit", "quick")) {
            if (args.length > 3 && !args[3].equals(state)) continue;
            Path home = Files.createTempDirectory("jasper-tunnel-preview-");
            try (var opened = new TunnelSurfaceFixture(home, theme(theme.equals("calibration") ? "tunnel-calibration" : theme))) {
                var hosts = opened.hosts(new String[][] {{"amp-amp", "100.110.65.45", "amp", "Sessions"}, {"pve", "100.73.226.91", "root", ""}});
                if (args.length > 5) opened.width(Integer.parseInt(args[5]));
                switch (state) {
                    case "edit" -> opened.openEdit(opened.tunnel("Postgres", hosts.get(0), "LOCAL", "localhost", 5432, "localhost", 5432), "Edit Postgres");
                    case "quick" -> opened.openEdit(opened.quick(hosts.get(0), 3000), "Save as Tunnel");
                    case "warning" -> { opened.tunnel("Postgres", hosts.get(0), "LOCAL", "localhost", 5432, "localhost", 5432); opened.openNew(); }
                    default -> opened.openNew();
                }
                switch (state) {
                    case "remote" -> opened.click("tunnel.type.option.remote");
                    case "dynamic" -> opened.click("tunnel.type.option.dynamic");
                    case "valid" -> { opened.type("tunnel.name", "Grafana"); opened.type("tunnel.listen.port", "3000"); opened.type("tunnel.forward.port", "3000"); }
                    case "invalid" -> { opened.type("tunnel.name", "Grafana"); opened.type("tunnel.listen.port", "99999"); opened.type("tunnel.forward.port", "0"); }
                    case "warning" -> { opened.type("tunnel.name", "Second database"); opened.type("tunnel.listen.port", "5432"); opened.type("tunnel.forward.port", "5433"); }
                    case "error" -> { opened.type("tunnel.name", "Grafana"); opened.type("tunnel.listen.port", "3000"); opened.type("tunnel.forward.port", "3000");
                        opened.failWrites(); opened.click("tunnel.save"); StagedVaultHost.await(() -> StagedVaultHost.named(opened.surface().holder(), "tunnel.message") != null
                            && StagedVaultHost.named(opened.surface().holder(), "tunnel.message").isVisible(), "tunnel save error"); }
                    case "allow-others" -> opened.click("tunnel.allow-others");
                    default -> { }
                }
                StagedVaultHost.settle(); // a switch settles before capture
                Path directory = Files.createDirectories(output.resolve(theme).resolve("tunnel-new"));
                ImageIO.write(opened.capture(2), "png", directory.resolve(state + ".png").toFile());
                var metadata = new StringBuilder("provenance=actual Remote TunnelsUi action/editor + app host adapters + locked synthetic Vault; headless/no tunnel\ndeviceScale=2\nlogicalUiScale="
                    + com.formdev.flatlaf.util.UIScale.getUserScaleFactor() + "\npreferred=" + opened.preferred() + "\n");
                for (String id : List.of("tunnel.section", "tunnel.section.card", "tunnel.name", "tunnel.host", "tunnel.type", "tunnel.explanation", "tunnel.ports",
                        "tunnel.ports.card", "tunnel.listen.address", "tunnel.listen.port", "tunnel.forward.host", "tunnel.forward.port", "tunnel.route", "tunnel.allow-others",
                        "tunnel.preview", "tunnel.message", "tunnel.cancel", "tunnel.save")) {
                    try { metadata.append(id).append('=').append(opened.bounds(id)).append('\n'); }
                    catch (AssertionError absent) { metadata.append(id).append("=absent\n"); }
                }
                Files.writeString(directory.resolve(state + ".txt"), metadata);
            } finally { delete(home); }
        }
    }

    private static void save(Path output, String theme, String surface, String state, PanelSurfaceFixture opened, String[] args) throws Exception {
        if (args.length > 3 && !args[3].equals(state)) return;
        Path directory = Files.createDirectories(output.resolve(theme).resolve(surface));
        ImageIO.write(opened.capture(2), "png", directory.resolve(state + ".png").toFile());
        var metadata = new StringBuilder("provenance=actual staged plugin factory + app host adapters + locked synthetic Vault; headless/no connection\ndeviceScale=2\nlogicalUiScale="
            + com.formdev.flatlaf.util.UIScale.getUserScaleFactor() + "\n");
        metadata.append(onEdtValue(() -> anchors(opened.view(), opened.view(), "")));
        Files.writeString(directory.resolve(state + ".txt"), metadata);
    }

    /** Every named, visible kit component's bounds in the view, depth first. */
    private static String anchors(Container parent, Container root, String indent) {
        var text = new StringBuilder();
        for (var child : parent.getComponents()) {
            if (!child.isVisible()) continue;
            if (child.getName() != null) text.append(child.getName()).append('=').append(SwingUtilities.convertRectangle(child.getParent(), child.getBounds(), root))
                .append(child instanceof JComponent j && j.getFont() != null ? "; font=" + j.getFont().getFontName() + " " + j.getFont().getSize2D() : "").append('\n');
            if (child instanceof Container nested) text.append(anchors(nested, root, indent));
        }
        return text.toString();
    }

    private static void delete(Path root) throws Exception {
        try (var files = Files.walk(root)) { for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
    }
}
