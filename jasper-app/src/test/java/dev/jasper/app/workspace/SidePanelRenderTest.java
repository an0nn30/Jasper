package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.function.Predicate;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Screenshot parity for a side panel: a headless render of a panel shaped like the SSH hosts panel
 * (search field, buttons, a list with a selected row) shows the theme's own colours.
 */
@ExtendWith(EdtTestExtension.class)
class SidePanelRenderTest {
    /** A side panel with a search field, two buttons and a host list whose rows are panels, like the Remote plugin's. */
    private static final class HostsLike {
        final JTextField search = new JTextField("prod", 16);
        final JButton connect = new JButton("Connect");
        final JButton add = new JButton("New Host");
        final JList<String> list = new JList<>(new String[]{"prod", "stage", "dev"});
        final JPanel header = new JPanel(new BorderLayout(0, 6));
        final JPanel root = new JPanel(new BorderLayout());

        HostsLike() {
            var actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            actions.add(connect); actions.add(add);
            header.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            header.add(search, BorderLayout.CENTER); header.add(actions, BorderLayout.SOUTH);
            list.setCellRenderer((owner, value, index, selected, focused) -> {
                var row = new JPanel(new BorderLayout());
                row.setOpaque(true);
                row.setBackground(selected ? owner.getSelectionBackground() : owner.getBackground());
                row.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 8));
                var name = new JLabel(value);
                name.setForeground(selected ? owner.getSelectionForeground() : owner.getForeground());
                row.add(name, BorderLayout.CENTER);
                return row;
            });
            // FlatLaf's focus-owner hook: the headless list paints its focused selection colours.
            list.putClientProperty("JComponent.focusOwner", (Predicate<JComponent>) component -> true);
            list.updateUI();
            list.setSelectedIndex(1);
            root.add(header, BorderLayout.NORTH);
            root.add(new JScrollPane(list), BorderLayout.CENTER);
        }
    }

    @Test void aSidePanelShowsTheThemesToolWindowFieldButtonAndListColours() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            ThemeTestSupport.install(Theme.LIGHT);
            var panel = new HostsLike();
            var regions = new WorkspaceRegions(new JPanel());
            regions.show(PanelRegion.LEFT, panel.root, 280);
            assertThemeColours(regions, panel, Theme.LIGHT);

            ThemeTestSupport.install(Theme.DARK);
            SwingUtilities.updateComponentTreeUI(regions);
            regions.refreshTheme();
            assertThemeColours(regions, panel, Theme.DARK);
        } finally { UIManager.setLookAndFeel(original); }
    }

    private static void assertThemeColours(WorkspaceRegions regions, HostsLike panel, Theme theme) {
        regions.setSize(900, 400);
        // Twice: a split learns its size in the first pass.
        LayoutTestSupport.layoutTree(regions);
        LayoutTestSupport.layoutTree(regions);
        var image = new BufferedImage(regions.getWidth(), regions.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { regions.paint(graphics); } finally { graphics.dispose(); }
        String name = theme.name() + " ";

        assertThat(pixel(image, regions, panel.header, 2, 2)).as(name + "panel").isEqualTo(UIManager.getColor("ToolWindow.background"));
        var field = panel.search;
        assertThat(pixel(image, regions, field, field.getWidth() - 10, field.getHeight() / 2)).as(name + "search field")
            .isEqualTo(UIManager.getColor("TextField.background"));
        for (var button : new JButton[]{panel.connect, panel.add})
            assertThat(pixel(image, regions, button, button.getWidth() / 2, 4)).as(name + button.getText())
                .isEqualTo(UIManager.getColor("Button.background"));
        assertThat(pixel(image, regions, panel.list, row(panel.list, 1))).as(name + "selected row")
            .isEqualTo(UIManager.getColor("List.selectionBackground"));
        assertThat(pixel(image, regions, panel.list, row(panel.list, 0))).as(name + "unselected row")
            .isEqualTo(UIManager.getColor("List.background"));
    }

    /** A point near the right end of a row, clear of its text. */
    private static Point row(JList<?> list, int index) {
        Rectangle cell = list.getCellBounds(index, index);
        return new Point(cell.x + cell.width - 4, cell.y + cell.height / 2);
    }

    private static Color pixel(BufferedImage image, JComponent root, Component component, Point at) {
        return pixel(image, root, component, at.x, at.y);
    }

    private static Color pixel(BufferedImage image, JComponent root, Component component, int x, int y) {
        Point point = SwingUtilities.convertPoint(component, x, y, root);
        return new Color(image.getRGB(point.x, point.y), true);
    }
}
