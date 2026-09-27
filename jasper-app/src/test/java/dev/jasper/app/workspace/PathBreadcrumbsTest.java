package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.terminals.PaneSnapshot;
import dev.jasper.app.terminals.RemoteLocation;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class PathBreadcrumbsTest {
    private static final Path HOME = Path.of("/Users/example");
    private static PaneSnapshot pane(Optional<Path> local, Optional<RemoteLocation> remote, boolean provided) {
        return new PaneSnapshot("title", local, 80, 24, true, PaneSnapshot.State.RUNNING,
            OptionalInt.empty(), provided ? Optional.of("ssh") : Optional.empty(), remote, provided ? "staging-01" : "zsh");
    }
    @Test void localAndRemoteLocationsKeepTheirIdentityAndFullPaths() {
        var local = PathBreadcrumbs.Location.from(pane(Optional.of(HOME.resolve("projects/moray/src")), Optional.empty(), false), HOME);
        assertThat(local.crumbs()).extracting(PathBreadcrumbs.Crumb::label).containsExactly("~", "projects", "moray", "src");
        assertThat(local.path()).isEqualTo(HOME.resolve("projects/moray/src").toAbsolutePath().normalize().toString());
        assertThat(local.crumbs().get(2).path()).isEqualTo(HOME.resolve("projects/moray").toAbsolutePath().normalize().toString());
        var remote = PathBreadcrumbs.Location.from(pane(Optional.empty(), Optional.of(new RemoteLocation("real-host", "/srv/app folder/src")), true), HOME);
        assertThat(remote.crumbs()).extracting(PathBreadcrumbs.Crumb::label).containsExactly("staging-01", "srv", "app folder", "src");
        assertThat(remote.path()).isEqualTo("real-host:/srv/app folder/src");
        var unknown = PathBreadcrumbs.Location.from(pane(Optional.empty(), Optional.empty(), true), HOME);
        assertThat(unknown.crumbs()).extracting(PathBreadcrumbs.Crumb::label).containsExactly("staging-01");
        assertThat(unknown.path()).doesNotContain(HOME.toString()).contains("not reported");
        var root = PathBreadcrumbs.Location.from(pane(Optional.empty(), Optional.of(new RemoteLocation("host", "/")), false), HOME);
        assertThat(root.crumbs()).extracting(PathBreadcrumbs.Crumb::label).containsExactly("host", "/");
    }
    @Test void statusFollowsFocusedPaneAndKeepsRemotePathsOutOfLocalBreadcrumbs() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            var status = new WindowStatusBar();
            var panes = List.of(
                pane(Optional.of(HOME.resolve("projects/moray/plugins/remote/src")), Optional.empty(), false),
                pane(Optional.empty(), Optional.of(new RemoteLocation("ssh.example", "/srv/moray/src")), true),
                pane(Optional.empty(), Optional.empty(), true));
            for (var theme : List.of(Theme.DARK, Theme.LIGHT)) {
                ThemeTestSupport.install(theme); SwingUtilities.updateComponentTreeUI(status); status.refreshTheme();
                var preview = new BufferedImage(1000, 144, BufferedImage.TYPE_INT_ARGB);
                var graphics = preview.createGraphics();
                for (int i = 0; i < panes.size(); i++) {
                    status.setMetadata(panes.get(i), "120 × 36", true);
                    status.setSize(1000, 24); layout(status);
                    status.paint(graphics); graphics.translate(0, 24);
                    status.setSize(440, 24); layout(status);
                    status.paint(graphics); graphics.translate(0, 24);
                    var expected = PathBreadcrumbs.Location.from(panes.get(i), Path.of(System.getProperty("user.home")));
                    assertThat(status.getText()).contains(expected.path());
                    if (i > 0) assertThat(status.getText()).doesNotContain(HOME.toString());
                }
                graphics.dispose(); Files.createDirectories(Path.of("build/render-preview"));
                ImageIO.write(preview, "png", Path.of("build/render-preview/status-breadcrumbs-" + (theme.dark() ? "dark" : "light") + ".png").toFile());
            }
            UIManager.put("Label.font", UIManager.getFont("Label.font").deriveFont(40f));
            status.refreshTheme();
            assertThat(status.getPreferredSize().height).isGreaterThanOrEqualTo(status.getFontMetrics(UIManager.getFont("Label.font")).getHeight());
        } finally { UIManager.put("Label.font", null); UIManager.setLookAndFeel(original); }
    }
    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container nested) layout(nested);
    }
    @Test void breadcrumbsCollapseParentsKeepTheLeafAndFollowLiveThemes() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            var breadcrumbs = new PathBreadcrumbs();
            var location = PathBreadcrumbs.Location.local(HOME.resolve("projects/a-very-long-workspace/plugins/remote/src"), HOME);
            breadcrumbs.setLocation(location);
            for (var theme : List.of(Theme.DARK, Theme.LIGHT)) {
                ThemeTestSupport.install(theme); SwingUtilities.updateComponentTreeUI(breadcrumbs); breadcrumbs.refreshTheme();
                breadcrumbs.setSize(240, 28); breadcrumbs.doLayout();
                var visible = java.util.Arrays.stream(breadcrumbs.getComponents()).filter(Component::isVisible)
                    .filter(JLabel.class::isInstance).map(JLabel.class::cast).map(JLabel::getText).filter(java.util.Objects::nonNull).toList();
                assertThat(visible).contains("…", "src").doesNotContain("a-very-long-workspace");
                assertThat(breadcrumbs.getToolTipText()).isEqualTo(location.path());
                for (int width : new int[]{600, 240, 40, 0}) {
                    breadcrumbs.setSize(width, 28); breadcrumbs.doLayout();
                    for (Component child : breadcrumbs.getComponents()) if (child.isVisible()) {
                        assertThat(child.getX()).isGreaterThanOrEqualTo(0);
                        assertThat(child.getX() + child.getWidth()).isLessThanOrEqualTo(width);
                    }
                }
                breadcrumbs.setSize(600, 28); breadcrumbs.doLayout();
                var image = new BufferedImage(600, 40, 2); var g = image.createGraphics();
                g.setColor(UIManager.getColor("StatusBar.background")); g.fillRect(0, 0, 600, 40); breadcrumbs.paint(g); g.dispose();
                Files.createDirectories(Path.of("build/render-preview"));
                ImageIO.write(image, "png", Path.of("build/render-preview/breadcrumbs-" + (theme.dark() ? "dark" : "light") + ".png").toFile());
            }
        } finally { UIManager.setLookAndFeel(original); }
    }
}
