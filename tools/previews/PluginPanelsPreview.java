package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.ui.HostsPanel;
import dev.jasper.remote.ui.sftp.SftpPanel;
import dev.jasper.remote.ui.tunnels.TunnelsPanel;
import dev.jasper.fileserver.ui.FileServersPanel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Actual plugin panels, theme styling and tool-window headers; never opens a native window. */
public final class PluginPanelsPreview {
    public static void main(String[] args) throws Exception {
        var output = Path.of(args[0]); Files.createDirectories(output);
        SwingUtilities.invokeAndWait(() -> {
            for (var theme : List.of(Theme.DARK, Theme.JASPER_LIGHT)) {
                var themes = new ThemeController(theme);
                for (int width : new int[]{300, 220, 360}) {
                    themes.configure(theme, new dev.jasper.app.config.UiFontConfig("system", width == 360 ? 18 : 0));
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
                    sessions.setHosts(hosts, Optional.empty()); sessions.setCredentialLabels(h -> "Vault locked"); sessions.select(hosts.getFirst().id());
                    var sftp = new SftpPanel(icon -> AppIcons.named(icon.name()));
                    var tunnels = new TunnelsPanel(new TunnelsPanel.Actions(t -> {}, t -> {}, t -> {}, t -> {}, t -> {}, t -> {}, t -> {}, t -> {}, () -> {}),
                        icon -> AppIcons.bundled(icon.id()), AppIcons.bundled("actions/swapPanels"));
                    var servers = new FileServersPanel(new FileServersPanel.Actions(id -> {}, id -> {}, id -> {}, id -> {}, id -> {}, text -> {}, id -> {}, id -> {}, id -> {}),
                        id -> List.of(), Path.of("/preview"), "", ZoneId.of("UTC"), AppIcons.named("SERVER"));
                    grid.add(frame("Sessions", sessions)); grid.add(frame("Tunnels", tunnels));
                    grid.add(frame("SFTP", sftp)); grid.add(frame("File Servers", servers));
                    grid.putClientProperty("Jasper.zone", "workspace");
                    try (var styler = new WorkspaceStyler(grid)) {
                        styler.apply(themes.current());
                        int w = width * 2 + 24, h = 780;
                        grid.setSize(w, h); grid.addNotify(); layout(grid); layout(grid);
                        var image = new BufferedImage(w * 2, h * 2, BufferedImage.TYPE_INT_RGB);
                        var graphics = image.createGraphics();
                        try { graphics.scale(2, 2); grid.printAll(graphics); } finally { graphics.dispose(); }
                        grid.removeNotify();
                        String name = (theme.dark() ? "dark" : "light") + "-" + width + ".png";
                        try { ImageIO.write(image, "png", output.resolve(name).toFile()); }
                        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                    }
                }
            }
        });
        System.out.println(output.toAbsolutePath());
    }
    private static JPanel frame(String title, JComponent panel) {
        var frame = new JPanel(new BorderLayout());
        frame.add(new ToolWindowHeader(title, JPopupMenu::new, () -> {}), BorderLayout.NORTH);
        frame.add(panel, BorderLayout.CENTER);
        return frame;
    }
    private static void layout(Container root) {
        root.doLayout();
        for (var component : root.getComponents()) if (component instanceof Container child) layout(child);
    }
}
