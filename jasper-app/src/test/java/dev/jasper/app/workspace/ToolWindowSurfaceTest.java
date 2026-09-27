package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ToolWindowSurfaceTest {
    @Test void panelRegionsPaintTheToolWindowColourWhileControlsKeepTheirOwn() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            ThemeTestSupport.install(Theme.LIGHT);
            var content = new JPanel(new BorderLayout());
            var inner = new JPanel();
            var field = new JTextField("hosts");
            var scroll = new JScrollPane(new JList<>(new String[]{"prod", "stage"}));
            var fixed = new JPanel(); fixed.setBackground(Color.MAGENTA);
            inner.add(field);
            content.add(inner, BorderLayout.NORTH); content.add(scroll); content.add(fixed, BorderLayout.SOUTH);
            var regions = new WorkspaceRegions(new JPanel());
            regions.show(PanelRegion.LEFT, content, 250);

            Color surface = UIManager.getColor("ToolWindow.background");
            assertThat(surface).isEqualTo(Color.WHITE);
            assertThat(UIManager.getColor("Panel.background")).isNotEqualTo(surface);
            assertThat(content.getParent().getBackground()).as("region host").isEqualTo(surface);
            assertThat(content.getBackground()).isEqualTo(surface);
            assertThat(inner.getBackground()).isEqualTo(surface);
            assertThat(scroll.getViewport().getBackground()).isEqualTo(surface);
            assertThat(field.getBackground()).isEqualTo(UIManager.getColor("TextField.background"));
            assertThat(fixed.getBackground()).as("a plugin's own colour").isEqualTo(Color.MAGENTA);
            var later = new JPanel();
            inner.add(later);
            assertThat(later.getBackground()).as("added after the panel opened").isEqualTo(surface);

            ThemeTestSupport.install(Theme.DARK);
            SwingUtilities.updateComponentTreeUI(regions);
            regions.refreshTheme();
            Color dark = UIManager.getColor("ToolWindow.background");
            assertThat(dark).isEqualTo(new Color(0x191c20));
            for (var component : List.of(content, inner, later)) assertThat(component.getBackground()).isEqualTo(dark);
            assertThat(fixed.getBackground()).isEqualTo(Color.MAGENTA);
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void roundedHostsClipOpaquePluginContentInEveryRegionAndTheme() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            for (var theme : List.of(Theme.DARK, Theme.LIGHT)) {
                ThemeTestSupport.install(theme);
                for (var region : PanelRegion.values()) {
                    var plugin = new JPanel(); plugin.setBackground(Color.MAGENTA);
                    var regions = new WorkspaceRegions(new JPanel()); regions.show(region, plugin, 240);
                    regions.setSize(700, 500);
                    dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(regions);
                    dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(regions);
                    var host = (JComponent) plugin.getParent();
                    var image = new java.awt.image.BufferedImage(host.getWidth(), host.getHeight(), 2);
                    var graphics = image.createGraphics();
                    try { host.paint(graphics); } finally { graphics.dispose(); }
                    assertThat(new Color(image.getRGB(0, 0))).isEqualTo(UIManager.getColor("Jasper.titleBackground"));
                    var insets = host.getInsets();
                    assertThat(new Color(image.getRGB(insets.left, insets.top))).as("opaque plugin cannot square the rounded corner")
                        .isEqualTo(UIManager.getColor("Jasper.titleBackground"));
                    assertThat(new Color(image.getRGB(host.getWidth() / 2, host.getHeight() / 2))).isEqualTo(Color.MAGENTA);
                    assertThat(host.contains(insets.left, insets.top)).isFalse();
                    assertThat(host.contains(host.getWidth() / 2, host.getHeight() / 2)).isTrue();
                }
                renderToolPanels(theme);
            }
        } finally { UIManager.setLookAndFeel(original); }
    }

    private static void renderToolPanels(Theme theme) throws Exception {
        var terminal = new JTextArea("\n  ~ / projects / jasper\n\n  $ ");
        terminal.setBackground(theme.palette().background()); terminal.setForeground(theme.palette().foreground());
        terminal.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 14));
        var regions = new WorkspaceRegions(terminal);
        var plugin = new JPanel(new BorderLayout(0, 8));
        plugin.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        var heading = new JPanel(new BorderLayout(0, 10));
        var title = new JLabel("Sessions"); title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 14));
        heading.add(title, BorderLayout.NORTH);
        var search = new JTextField(); search.putClientProperty("JTextField.placeholderText", "Search hosts…");
        heading.add(search, BorderLayout.SOUTH); plugin.add(heading, BorderLayout.NORTH);
        var list = new JList<>(new String[]{"Local terminal", "staging-01", "production"}); list.setSelectedIndex(1);
        list.setFixedCellHeight(30); var scroll = new JScrollPane(list); scroll.setBorder(BorderFactory.createEmptyBorder());
        plugin.add(scroll); regions.show(PanelRegion.LEFT, plugin, 270);
        var bottom = new JPanel(new BorderLayout()); bottom.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        bottom.add(new JLabel("Transfers"), BorderLayout.NORTH); bottom.add(new JLabel("No active transfers", SwingConstants.CENTER));
        regions.show(PanelRegion.BOTTOM, bottom, 140);
        var rail = new WindowRail(); rail.setRailWidth(36);
        rail.render(List.of(new WindowRail.PanelButton("sessions", "Sessions", dev.jasper.app.platform.AppIcons.named("SERVER"), PanelRegion.LEFT, true),
            new WindowRail.PanelButton("vault", "Vault", dev.jasper.app.platform.AppIcons.named("LOCK"), PanelRegion.LEFT, false)), List.of());
        rail.refreshTheme();
        assertThat(rail.buttons().getFirst().getSelectedIcon()).as("rail retains the original theme-aware icon").isNull();
        var root = new JPanel(new BorderLayout()); root.add(rail, BorderLayout.WEST); root.add(regions);
        root.setSize(820, 480);
        dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(root);
        dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(root);
        var selectedButton = rail.buttons().getFirst();
        var buttonImage = new java.awt.image.BufferedImage(selectedButton.getWidth(), selectedButton.getHeight(), 2);
        var bg = buttonImage.createGraphics(); selectedButton.paint(bg); bg.dispose();
        assertThat(new Color(buttonImage.getRGB(4, selectedButton.getHeight() / 2)))
            .as("rail uses the active theme's neutral toolbar selection")
            .isEqualTo(UIManager.getColor("ToggleButton.toolbar.selectedBackground"));
        var image = new java.awt.image.BufferedImage(1640, 960, 2); var g = image.createGraphics();
        try { g.scale(2, 2); root.printAll(g); } finally { g.dispose(); }
        var output = java.nio.file.Path.of("build/render-preview"); java.nio.file.Files.createDirectories(output);
        javax.imageio.ImageIO.write(image, "png", output.resolve("tool-panels-" + (theme.dark() ? "dark" : "light") + ".png").toFile());
    }

    @Test void renderersAndEditorsOfListsTablesTreesAndCombosKeepTheColoursTheirOwnerGaveThem() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            ThemeTestSupport.install(Theme.LIGHT);
            var list = new JList<>(new String[]{"prod", "stage"});
            var table = new JTable(2, 2);
            var tree = new JTree();
            var combo = new JComboBox<>(new String[]{"Password", "Key"});
            var content = new JPanel();
            content.add(new JScrollPane(list)); content.add(new JScrollPane(table)); content.add(new JScrollPane(tree)); content.add(combo);
            new WorkspaceRegions(new JPanel()).show(PanelRegion.LEFT, content, 250);
            assertThat(content.getBackground()).isEqualTo(UIManager.getColor("ToolWindow.background"));

            // A renderer painted with the owner's selection colour, as the Remote plugin's host rows are.
            var selection = new ColorUIResource(UIManager.getColor("List.selectionBackground"));
            for (JComponent owner : List.<JComponent>of(list, table, tree, combo)) {
                var panes = Arrays.stream(owner.getComponents()).filter(CellRendererPane.class::isInstance).toList();
                assertThat(panes).as(owner.getClass().getSimpleName() + " renderer pane").isNotEmpty();
                for (var pane : panes) {
                    var row = new JPanel(); row.setBackground(selection);
                    ((CellRendererPane) pane).add(row);
                    assertThat(row.getBackground()).as(owner.getClass().getSimpleName() + " renderer").isEqualTo(selection);
                }
                var editor = new JPanel(); editor.setBackground(selection);
                owner.add(editor);
                assertThat(editor.getBackground()).as(owner.getClass().getSimpleName() + " editor").isEqualTo(selection);
            }
        } finally { UIManager.setLookAndFeel(original); }
    }
}
