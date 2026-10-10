package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.contributions.PanelHeaderState;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.tunnels.Counters;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.remote.ui.HostsPanel;
import dev.jasper.remote.ui.sftp.SftpPanel;
import dev.jasper.remote.ui.tunnels.TunnelsPanel;
import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.Login;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.runtime.ServerRegistry;
import dev.jasper.fileserver.runtime.ServerState;
import dev.jasper.fileserver.ui.FileServersPanel;
import dev.jasper.sdk.ui.PanelHeader;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * Historical pre-0.15 panel preview, excluded from builds. Its old raw panel/icon APIs no longer exist.
 * Use the staged real-plugin render tests for current SDK-owned views; Task 12 audits archival tooling.
 */
public final class PluginPanelsPreview {
    public static void main(String[] args) throws Exception {
        var output = Path.of(args[0]); Files.createDirectories(output);
        SwingUtilities.invokeAndWait(() -> {
            for (var theme : List.of(Theme.DARK, Theme.JASPER_LIGHT)) {
                var themes = new ThemeController(theme);
                for (int width : new int[]{300, 220, 360}) {
                    themes.configure(theme, new dev.jasper.app.config.UiFontConfig("system", width == 360 ? 18 : 0));
                    for (boolean populated : new boolean[]{false, true}) {
                        var grid = panels(populated);
                        int w = width * 2 + 24, h = 780;
                        grid.setSize(w, h); ToolWindowSurface.apply(grid); grid.addNotify(); layout(grid); layout(grid);
                        var image = new BufferedImage(w * 2, h * 2, BufferedImage.TYPE_INT_RGB);
                        var graphics = image.createGraphics();
                        try { graphics.scale(2, 2); grid.printAll(graphics); } finally { graphics.dispose(); }
                        grid.removeNotify();
                        String name = (theme.dark() ? "dark" : "light") + "-" + width + (populated ? "-populated" : "") + ".png";
                        try { ImageIO.write(image, "png", output.resolve(name).toFile()); }
                        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                    }
                }
            }
        });
        System.out.println(output.toAbsolutePath());
    }
    private static JPanel panels(boolean populated) {
        var grid = new JPanel(new GridLayout(2, 2, 8, 8));
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        var sessions = new HostsPanel(new HostsPanel.Actions(h -> {}, h -> {}, h -> {}, h -> {}, h -> {}, (h, f) -> {}, () -> {}),
            icon -> AppIcons.bundled(icon.id()));
        var hosts = List.of(
            RemoteHost.create("amp", "100.110.65.45", 22, "ubuntu", Auth.AGENT, "", Optional.empty()).withFavorite(true),
            RemoteHost.create("amp-amp", "100.110.65.46", 22, "ubuntu", Auth.AGENT, "", Optional.empty()),
            RemoteHost.create("dustin-vm", "192.168.1.40", 22, "dustin", Auth.AGENT, "", Optional.empty()),
            RemoteHost.create("minecraft-local", "192.168.1.52", 22, "dustin", Auth.AGENT, "", Optional.empty()),
            RemoteHost.create("pve", "192.168.1.10", 22, "root", Auth.AGENT, "", Optional.empty()).withFavorite(true));
        sessions.setHosts(hosts, Optional.empty()); sessions.setCredentialLabels(h -> "Vault locked");
        sessions.setHostDetails(h -> "", h -> populated && (h.name().equals("amp") || h.name().equals("amp-amp")) ? 1 : 0);
        sessions.select(hosts.getFirst().id());
        var sftp = new SftpPanel(icon -> AppIcons.named(icon.name()));
        if (populated) {
            sftp.showPage("amp", "/home/ubuntu", List.of(
                folder(".config"), folder(".ssh"), folder("apps"), folder("backups"),
                file(".bashrc", 3700), file("docker-compose.yml", 1200), file("deploy.sh", 840)), 0, 7, false);
            sftp.table().clearSelection();
        }
        var tunnels = new TunnelsPanel(new TunnelsPanel.Actions(t -> {}, t -> {}, t -> {}, t -> {}, t -> {}, t -> {}, t -> {}, t -> {}, () -> {}),
            icon -> AppIcons.bundled(icon.id()), AppIcons.bundled("actions/swapPanels"));
        if (populated) {
            var values = List.of(
                Tunnel.create("Postgres", hosts.getFirst().id(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "localhost", 5432),
                Tunnel.create("Grafana", hosts.getFirst().id(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 3000, "localhost", 3000),
                Tunnel.create("Proxmox UI", hosts.getLast().id(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 8006, "localhost", 8006));
            var since = Instant.now().minusSeconds(3600);
            tunnels.setTunnels(values, List.of(), Optional.empty(), id -> values.stream().filter(t -> t.id().equals(id)).findFirst()
                .map(t -> t.name().equals("Proxmox UI") ? TunnelState.STOPPED : new TunnelState.Up(since, t.bindPort(), new Counters())).orElse(TunnelState.STOPPED),
                id -> hosts.stream().filter(h -> h.id().equals(id)).map(RemoteHost::name).findFirst());
        }
        Path home = Path.of(System.getProperty("user.home"));
        var servers = new FileServersPanel(new FileServersPanel.Actions(id -> {}, id -> {}, id -> {}, id -> {}, id -> {}, text -> {}, id -> {}, id -> {}, id -> {}),
            id -> List.of(), home, "", ZoneId.of("UTC"), AppIcons.named("SERVER"));
        if (populated) servers.setServers(List.of(
            server("Downloads", Protocol.HTTP, 8080, home, true), server("Builds", Protocol.SFTP, 2222, home, true),
            server("Media", Protocol.FTP, 21, home, true), server("Backups", Protocol.TFTP, 873, home, false)), Optional.empty());
        grid.add(frame("Sessions", sessions, sessions::onHeaderChanged)); grid.add(frame("Tunnels", tunnels, tunnels::onHeaderChanged));
        grid.add(frame("SFTP", sftp, sftp::onHeaderChanged)); grid.add(frame("File Servers", servers, servers::onHeaderChanged));
        return grid;
    }
    private static FileEntry folder(String name) { return new FileEntry(name, FileEntry.Kind.DIRECTORY, 0, 0, 0755, ""); }
    private static FileEntry file(String name, long size) { return new FileEntry(name, FileEntry.Kind.FILE, size, 0, 0644, ""); }
    private static ServerRegistry.View server(String name, Protocol protocol, int port, Path home, boolean running) {
        var definition = new ServerDefinition(UUID.randomUUID(), name, protocol, home.resolve(name), Bind.ALL, port,
            Optional.empty(), false, protocol == Protocol.SFTP ? Login.SESSION : Login.ANONYMOUS, List.of(), false, false, false);
        return new ServerRegistry.View(definition, false, running ? ServerState.RUNNING : ServerState.STOPPED,
            running ? List.of(URI.create(protocol.scheme() + "://192.168.1.5:" + port + "/")) : List.of(), Optional.empty(), false);
    }
    private static JPanel frame(String title, JComponent panel, Consumer<Consumer<PanelHeader>> bind) {
        var frame = new JPanel(new BorderLayout());
        var header = new ToolWindowHeader(title, JPopupMenu::new, () -> {});
        bind.accept(state -> header.presentation(new PanelHeaderState(state.detail(), state.active(), state.actions().stream()
            .map(action -> new PanelHeaderState.Action(action.label(), action.icon(), action.invoke())).toList())));
        frame.add(header, BorderLayout.NORTH); frame.add(panel, BorderLayout.CENTER);
        return frame;
    }
    private static void layout(Container root) {
        root.doLayout();
        for (var component : root.getComponents()) if (component instanceof Container child) layout(child);
    }
}
