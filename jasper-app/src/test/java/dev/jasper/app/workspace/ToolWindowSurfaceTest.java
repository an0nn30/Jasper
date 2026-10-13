package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
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
    private static ToolWindowArea showing(Slot slot, JComponent content, int size) {
        var area = new ToolWindowArea(new JPanel(), new DockState());
        DockTestSupport.show(area, slot, content, size);
        return area;
    }

    /** theme.md: a tool window's body is surface.panel; a vendor ToolWindow.background no longer reaches it. */
    @Test void toolWindowsPaintThePanelSurfaceWhileControlsKeepTheirOwn() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            ThemeTestSupport.install(false);
            var content = new JPanel(new BorderLayout());
            var inner = new JPanel();
            var field = new JTextField("hosts");
            var scroll = new JScrollPane(new JList<>(new String[]{"prod", "stage"}));
            var fixed = new JPanel(); fixed.setBackground(Color.MAGENTA);
            inner.add(field);
            content.add(inner, BorderLayout.NORTH); content.add(scroll); content.add(fixed, BorderLayout.SOUTH);
            var area = showing(Slot.LEFT_TOP, content, 250);

            assertThat(UIManager.getColor("ToolWindow.background")).as("the vendor key the tool window ignores")
                .isEqualTo(ThemeTestSupport.color(false, dev.jasper.ui.theme.SemanticColor.SURFACE_SIDEBAR));
            Color surface = dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL);
            assertThat(surface).isNotEqualTo(UIManager.getColor("ToolWindow.background"));
            assertThat(content.getParent().getBackground()).as("tool window frame").isEqualTo(surface);
            assertThat(area.headerOf(Slot.LEFT_TOP).getBackground()).as("the header has no bar colour").isEqualTo(surface);
            assertThat(content.getBackground()).isEqualTo(UIManager.getColor("Panel.background"));
            assertThat(inner.getBackground()).isEqualTo(UIManager.getColor("Panel.background"));
            assertThat(scroll.getViewport().getBackground()).isEqualTo(UIManager.getColor("Viewport.background"));
            assertThat(field.getBackground()).isEqualTo(UIManager.getColor("TextField.background"));
            assertThat(fixed.getBackground()).as("a plugin's own colour").isEqualTo(Color.MAGENTA);
            var later = new JPanel();
            inner.add(later);
            assertThat(later.getBackground()).as("no recursive observer after the panel opened").isEqualTo(UIManager.getColor("Panel.background"));

            ThemeTestSupport.install(true);
            SwingUtilities.updateComponentTreeUI(area);
            area.refreshTheme();
            Color dark = dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL);
            assertThat(dark).isEqualTo(ThemeTestSupport.color(true, dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL));
            assertThat(area.headerOf(Slot.LEFT_TOP).getBackground()).isEqualTo(dark);
            for (var component : List.<JComponent>of(content, inner, later))
                assertThat(component.getBackground()).isEqualTo(UIManager.getColor("Panel.background"));
            assertThat(fixed.getBackground()).isEqualTo(Color.MAGENTA);
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void roundedFramesClipOpaquePluginContentInEverySlotAndTheme() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            for (boolean themeDark : new boolean[]{true, false}) {
                ThemeTestSupport.install(themeDark);
                for (var slot : Slot.values()) {
                    var plugin = new JPanel(); plugin.setBackground(Color.MAGENTA);
                    var area = showing(slot, plugin, 240);
                    area.setSize(700, 500);
                    dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(area);
                    var host = (JComponent) plugin.getParent();
                    var image = new java.awt.image.BufferedImage(host.getWidth(), host.getHeight(), 2);
                    var graphics = image.createGraphics();
                    try { host.paint(graphics); } finally { graphics.dispose(); }
                    assertThat(new Color(image.getRGB(0, 0))).isEqualTo(dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_APP));
                    var insets = host.getInsets();
                    assertThat(new Color(image.getRGB(insets.left, insets.top))).as("opaque plugin cannot square the rounded corner")
                        .isEqualTo(dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_APP));
                    assertThat(new Color(image.getRGB(host.getWidth() / 2, host.getHeight() / 2))).isEqualTo(Color.MAGENTA);
                    assertThat(host.contains(insets.left, insets.top)).isFalse();
                    assertThat(host.contains(host.getWidth() / 2, host.getHeight() / 2)).isTrue();
                }
                renderToolPanels(themeDark);
            }
        } finally { UIManager.setLookAndFeel(original); }
    }

    private static void renderToolPanels(boolean dark) throws Exception {
        var terminal = new JTextArea("\n  ~ / projects / jasper\n\n  $ ");
        var palette = ThemeTestSupport.terminal(dark);
        terminal.setBackground(palette.background()); terminal.setForeground(palette.foreground());
        terminal.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 14));
        var area = new ToolWindowArea(terminal, new DockState());
        area.setStripesVisible(true);
        var plugin = new JPanel(new BorderLayout(0, 8));
        plugin.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        // The header carries the title now, so the panel starts with its search field.
        var search = new JTextField(); search.putClientProperty("JTextField.placeholderText", "Search hosts…");
        plugin.add(search, BorderLayout.NORTH);
        var list = new JList<>(new String[]{"Local terminal", "staging-01", "production"}); list.setSelectedIndex(1);
        list.setFixedCellHeight(30); var scroll = new JScrollPane(list); scroll.setBorder(BorderFactory.createEmptyBorder());
        plugin.add(scroll); DockTestSupport.show(area, Slot.LEFT_TOP, plugin, 270);
        var bottom = new JPanel(new BorderLayout()); bottom.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        bottom.add(new JLabel("No active transfers", SwingConstants.CENTER));
        DockTestSupport.show(area, Slot.BOTTOM_LEFT, bottom, 140);
        var stripe = new ToolStripe(Side.LEFT); stripe.setRailWidth(36);
        stripe.render(List.of(new ToolStripe.Button("sessions", "Sessions", dev.jasper.app.platform.AppIcons.named("SERVER"), Slot.LEFT_TOP, true),
            new ToolStripe.Button("vault", "Vault", dev.jasper.app.platform.AppIcons.named("LOCK"), Slot.LEFT_TOP, false),
            new ToolStripe.Button("transfers", "Transfers", dev.jasper.app.platform.AppIcons.named("FOLDER"), Slot.BOTTOM_LEFT, true)), List.of());
        stripe.refreshTheme();
        var right = new ToolStripe(Side.RIGHT); right.setRailWidth(36); right.refreshTheme();
        assertThat(stripe.buttons().getFirst().getSelectedIcon()).as("the stripe retains the original theme-aware icon").isNull();
        var root = new JPanel(new BorderLayout()); root.add(stripe, BorderLayout.WEST); root.add(area); root.add(right, BorderLayout.EAST);
        root.setSize(820, 480);
        dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(root);
        var selectedButton = stripe.buttons().getFirst();
        var buttonImage = new java.awt.image.BufferedImage(selectedButton.getWidth(), selectedButton.getHeight(), 2);
        var bg = buttonImage.createGraphics(); selectedButton.paint(bg); bg.dispose();
        assertThat(new Color(buttonImage.getRGB(4, selectedButton.getHeight() / 2)))
            .as("the stripe uses the active theme's neutral toolbar selection")
            .isEqualTo(dev.jasper.app.platform.ThemeZone.color(null, dev.jasper.ui.theme.SemanticColor.SURFACE_CONTROL_SELECTED));
        var image = new java.awt.image.BufferedImage(1640, 960, 2); var g = image.createGraphics();
        try { g.scale(2, 2); root.printAll(g); } finally { g.dispose(); }
        var output = java.nio.file.Path.of("build/render-preview"); java.nio.file.Files.createDirectories(output);
        javax.imageio.ImageIO.write(image, "png", output.resolve("tool-panels-" + (dark ? "dark" : "light") + ".png").toFile());
    }

    @Test void renderersAndEditorsOfListsTablesTreesAndCombosKeepTheColoursTheirOwnerGaveThem() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            ThemeTestSupport.install(false);
            var list = new JList<>(new String[]{"prod", "stage"});
            var table = new JTable(2, 2);
            var tree = new JTree();
            var combo = new JComboBox<>(new String[]{"Password", "Key"});
            var content = new JPanel();
            content.add(new JScrollPane(list)); content.add(new JScrollPane(table)); content.add(new JScrollPane(tree)); content.add(combo);
            showing(Slot.LEFT_TOP, content, 250);
            assertThat(content.getBackground()).isEqualTo(UIManager.getColor("Panel.background"));

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
