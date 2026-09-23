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

    @Test void retroSupportedFramesAndDialogsUseTheCompactTitle() {
        new ThemeController(dev.jasper.app.config.ThemeStyle.RETRO, dev.jasper.app.config.Appearance.LIGHT);
        try {
            for (var kind : AuxiliarySurface.Kind.values()) {
                var surface = new AuxiliarySurface("dev.test.retro", "Credential Vault", kind, true,
                    new Dimension(400, 300), null, null, ignored -> { throw new AssertionError("No native shell"); });
                var root = new JRootPane();
                try (var bar = NativeShells.installTitleBar(root, surface, true)) {
                    assertThat(bar).isNotNull();
                    bar.setLight(true);
                    assertThat(bar.getPreferredSize().height).isEqualTo(32);
                    var title = (JLabel) bar.getComponent(0);
                    assertThat(title.getText()).isEqualTo("Credential Vault");
                    assertThat(title.getFont().isBold()).isFalse();
                    assertThat(surface.holder().getParent()).isSameAs(root.getContentPane());
                }
            }
        } finally { new ThemeController(); }
    }

    private void verifyChrome(AuxiliarySurface.Kind kind) {
        new ThemeController();
        var surface = new AuxiliarySurface("dev.test.window", "Plugin editor", kind, true,
            new Dimension(400, 300), null, null, ignored -> { throw new AssertionError("No native shell in tests"); });
        var content = new JPanel(); surface.setContent(content);
        var root = new JRootPane();
        try (var bar = NativeShells.installTitleBar(root, surface, true)) {
            assertThat(bar).isNotNull();
            assertThat(root.getClientProperty("apple.awt.fullWindowContent")).isEqualTo(true);
            assertThat(root.getClientProperty("apple.awt.transparentTitleBar")).isEqualTo(true);
            assertThat(root.getClientProperty("apple.awt.windowTitleVisible")).isEqualTo(false);
            assertThat(((JLabel) bar.getComponent(0)).getText()).isEqualTo("Plugin editor");
            assertThat(surface.holder().getParent()).isSameAs(root.getContentPane());
            assertThat(content.getParent()).isSameAs(surface.holder());
        }
    }
@Test void retroUnsupportedPlatformsKeepNormalWindowDecorationsForFramesAndDialogs() {
    new ThemeController(dev.jasper.app.config.ThemeStyle.RETRO, dev.jasper.app.config.Appearance.DARK);
    try {
        for (var kind : AuxiliarySurface.Kind.values()) {
            var surface = new AuxiliarySurface("dev.test.retro", "Retro editor", kind, true,
                new java.awt.Dimension(400, 300), null, null,
                ignored -> { throw new AssertionError("No native shell in tests"); });
            var content = new javax.swing.JPanel(); surface.setContent(content);
            var root = new javax.swing.JRootPane();
            assertThat(NativeShells.installTitleBar(root, surface, false)).isNull();
            assertThat(root.getContentPane()).isSameAs(surface.holder());
            assertThat(content.getParent()).isSameAs(surface.holder());
            assertThat(root.getClientProperty("apple.awt.fullWindowContent")).isNull();
            assertThat(root.getClientProperty("apple.awt.transparentTitleBar")).isNull();
            assertThat(root.getClientProperty("apple.awt.windowTitleVisible")).isNull();
        }
    } finally { new ThemeController(); }
}

}
