package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.List;
import javax.swing.*;
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
            assertThat(dark).isEqualTo(new Color(0x21252b));
            for (var component : List.of(content, inner, later)) assertThat(component.getBackground()).isEqualTo(dark);
            assertThat(fixed.getBackground()).isEqualTo(Color.MAGENTA);
        } finally { UIManager.setLookAndFeel(original); }
    }
}
