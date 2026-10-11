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

    @Test void compactHeroPackingUsesContentWidthAndUnifiedUntitledChrome() {
        var controller = new ThemeController();
        var resolved = controller.current();
        var binding = new dev.jasper.ui.theme.ThemeBinding(resolved.pair(), failure -> { throw new AssertionError(failure); });
        var icons = new dev.jasper.ui.icons.IconRegistry();
        var context = new dev.jasper.ui.swing.KitContext(binding, dev.jasper.ui.theme.ThemeRoute.CHROME,
            dev.jasper.ui.metrics.DesignMetrics.DEFAULT, icons, () -> false);
        var form = new dev.jasper.ui.model.Nodes.Form("hero", dev.jasper.ui.model.ControlState.READY,
            dev.jasper.ui.model.Nodes.FormKind.HERO_COMPACT, "Unlock Vault", java.util.Optional.empty(), java.util.List.of(), java.util.Optional.empty(), java.util.List.of());
        try (var view = new dev.jasper.ui.swing.KitView(context, new dev.jasper.ui.model.ViewSpec(form), e -> {})) {
            var surface = new AuxiliarySurface("", "Unlock Vault", AuxiliarySurface.Kind.DIALOG, true,
                new Dimension(400, 300), null, null, ignored -> { throw new AssertionError("No native shell in tests"); });
            surface.setContent(view); surface.fitContent(true);
            var root = new JRootPane();
            try (var bar = NativeShells.installTitleBar(root, surface, true)) {
                assertThat(root.getPreferredSize().width).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(380));
                assertThat(bar.getPreferredSize().height).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(20));
                assertThat(((JLabel)bar.getComponent(0)).isVisible()).isFalse();
                assertThat(bar.getBackground()).isEqualTo(resolved.chrome().semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_DIALOG));
            }
        } finally { binding.close(); icons.close(); controller.close(); }
    }

    @Test void actualChromeMountsTitleActionsOutsideBodyWithCenteredNativeTitleAndFallback() {
        for (boolean supported : java.util.List.of(true, false)) {
            var surface = new AuxiliarySurface("test", "Manager", AuxiliarySurface.Kind.WINDOW, false,
                new Dimension(900, 555), null, null, ignored -> { throw new AssertionError(); });
            var actions = new javax.swing.JButton("Lock / Add"); actions.setPreferredSize(new Dimension(180, 36));
            var border = actions.getBorder(); surface.setTitleActions(actions);
            var root = new JRootPane();
            try (var chrome = NativeShells.installChrome(root, surface, supported)) {
                var bar = chrome.bar();
                root.setSize(900, 620); layout(root);
                assertThat(javax.swing.SwingUtilities.isDescendingFrom(actions, root)).isTrue();
                assertThat(javax.swing.SwingUtilities.isDescendingFrom(actions, surface.holder())).isFalse();
                assertThat(actions.getBorder()).isSameAs(border);
                int bodyTop = javax.swing.SwingUtilities.convertPoint(surface.holder(), 0, 0, root).y;
                assertThat(bodyTop).isGreaterThanOrEqualTo(36);
                assertThat(chrome.body().boundsInRoot()).contains(new java.awt.Rectangle(0, bodyTop, 900, 620 - bodyTop));
                surface.contentSize(true);
                assertThat(NativeShells.initialSize(surface, new Dimension(924, 624), new Dimension(900, 580), new java.awt.Rectangle(0,0,2000,1500))).isEqualTo(new Dimension(924,599));
                assertThat(root.getSize()).isEqualTo(new Dimension(900,620));
                if (supported) {
                    var title = (JLabel) bar.getComponent(0);
                    assertThat(title.isVisible()).isTrue();
                    assertThat(title.getX() * 2 + title.getWidth()).isEqualTo(900);
                    assertThat(title.getX() + title.getWidth()).isLessThanOrEqualTo(surface.titleActions().getX());
                }
            }
        }
    }
    private static void layout(java.awt.Container node) {
        node.doLayout(); for (var child : node.getComponents()) if (child instanceof java.awt.Container container) layout(container);
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
