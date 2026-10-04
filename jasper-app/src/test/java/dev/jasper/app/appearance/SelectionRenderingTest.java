package dev.jasper.app.appearance;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class SelectionRenderingTest {
    @Test void selectionsRoundAcrossThemesAndCompositePluginRenderers() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            var standard = new JList<>(new String[]{"Default row", "Second row"});
            var composite = new JList<>(new String[]{"Plugin row", "Second row"});
            composite.setCellRenderer((list, value, index, selected, focus) -> {
                var panel = new JPanel(new BorderLayout());
                panel.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
                var label = new JLabel(value);
                label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
                panel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 8));
                panel.add(label);
                // No native peers in this preview; lay out the composite at the cell dimensions.
                panel.setSize(280, 28); panel.doLayout();
                return panel;
            });
            var tree = new JTree(new Object[]{"First node", "Second node"});
            tree.setRootVisible(false); tree.setRowHeight(28); tree.setSelectionRow(0);
            var table = new JTable(new Object[][]{{"First", "Value"}, {"Second", "Value"}}, new Object[]{"Name", "Detail"});
            table.setRowHeight(28); table.setRowSelectionInterval(0, 0); table.setShowGrid(false);
            var menu = new JMenuItem("Menu choice"); menu.setArmed(true);
            var all = List.of(standard, composite, tree, table, menu);
            for (Theme theme : List.of(Theme.DARK, dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT, Theme.DARK)) {
                ThemeTestSupport.install(theme);
                var preview = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
                var g = preview.createGraphics();
                g.setColor(UIManager.getColor("Panel.background")); g.fillRect(0, 0, 600, 400);
                int y = 12;
                for (JComponent component : all) {
                    component.putClientProperty("JComponent.focusOwner", (Predicate<JComponent>) c -> true);
                    SwingUtilities.updateComponentTreeUI(component);
                    if (component instanceof JList<?> list) { list.setFixedCellHeight(28); list.setSelectedIndex(0); }
                    component.setSize(280, component == menu ? 28 : 56); component.doLayout();
                    var image = new BufferedImage(280, component.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    var cg = image.createGraphics(); component.paint(cg); cg.dispose();
                    String key = component instanceof JList<?> ? "List" : component == tree ? "Tree" : component == table ? "Table" : "MenuItem";
                    Color selection = UIManager.getColor(key + ".selectionBackground");
                    assertThat(new Color(image.getRGB(270, 14), true)).as(theme.name() + " " + key + " selection").isEqualTo(selection);
                    int rowTop = component == tree ? tree.getRowBounds(0).y : 0;
                    assertThat(new Color(image.getRGB(140, rowTop), true)).as(theme.name() + " " + key + " top edge").isEqualTo(selection);
                    assertThat(new Color(image.getRGB(275, rowTop), true)).as(theme.name() + " " + key + " rounded corner").isNotEqualTo(selection);
                    assertThat(new Color(image.getRGB(279, 14), true)).as(theme.name() + " " + key + " inset").isNotEqualTo(selection);
                    if (component == composite) {
                        int textPixels = 0;
                        for (int px = 12; px < 100; px++) for (int py = 4; py < 24; py++)
                            if (image.getRGB(px, py) == composite.getSelectionForeground().getRGB()) textPixels++;
                        assertThat(textPixels).as("composite renderer retains its text").isGreaterThan(10);
                    }
                    g.drawImage(image, 12, y, null); y += component.getHeight() + 12;
                }
                g.dispose();
                Files.createDirectories(Path.of("build/render-preview"));
                ImageIO.write(preview, "png", Path.of("build/render-preview/selections-" + (theme.dark() ? "dark" : "light") + ".png").toFile());
            }
        } finally { UIManager.setLookAndFeel(original); }
    }
}
