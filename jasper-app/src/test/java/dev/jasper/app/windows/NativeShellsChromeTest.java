package dev.jasper.app.windows;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Dimension;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class NativeShellsChromeTest {
    @Test void everyAuxiliarySurfaceGetsTheSharedTitleAndKeepsItsContent() {
        for (var kind : AuxiliarySurface.Kind.values()) verifyChrome(kind);
    }

    private void verifyChrome(AuxiliarySurface.Kind kind) {
        new ThemeController();
        var surface = new AuxiliarySurface("dev.test.window", "Plugin editor", kind, true,
            new Dimension(400, 300), null, null, ignored -> { throw new AssertionError("No native shell in tests"); });
        var content = new JPanel(); surface.setContent(content);
        var root = new JRootPane();
        try (var bar = NativeShells.installTitleBar(root, surface, true)) {
            assertThat(bar).isNotNull();
            var menu = new javax.swing.JMenuBar();
            bar.setMenuBar(menu);
            assertThat(root.getJMenuBar()).as("menus live on the macOS screen menu bar").isSameAs(menu);
            assertThat(root.getClientProperty("apple.awt.fullWindowContent")).isEqualTo(true);
            assertThat(root.getClientProperty("apple.awt.transparentTitleBar")).isEqualTo(true);
            assertThat(root.getClientProperty("apple.awt.windowTitleVisible")).isEqualTo(false);
            assertThat(((JLabel) bar.getComponent(0)).getText()).isEqualTo("Plugin editor");
            assertThat(surface.holder().getParent()).isSameAs(root.getContentPane());
            assertThat(content.getParent()).isSameAs(surface.holder());
        }
    }
}
