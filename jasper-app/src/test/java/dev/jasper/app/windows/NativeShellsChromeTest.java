package dev.jasper.app.windows;

import dev.jasper.app.appearance.ThemeTestSupport;
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

    /** A dialog sized to its content (every plugin dialog, a confirmation) is fixed-size; only a dialog that remembers its bounds resizes. */
    @Test void onlyADialogThatRemembersItsBoundsCanBeResized() {
        java.util.function.BiFunction<String, Boolean, AuxiliarySurface> dialog = (id, sheet) -> {
            var surface = new AuxiliarySurface(id, "New Tunnel", AuxiliarySurface.Kind.DIALOG, true, new Dimension(1, 1), null, null,
                ignored -> { throw new AssertionError("No native shell in tests"); });
            surface.attachedSheet(sheet);
            return surface;
        };
        assertThat(NativeShells.resizable(dialog.apply("", false))).as("a dialog packed to its content").isFalse();
        assertThat(NativeShells.resizable(dialog.apply("", true))).as("an attached sheet").isFalse();
        assertThat(NativeShells.resizable(dialog.apply("file-chooser", false))).as("a dialog with remembered bounds").isTrue();
    }

    @Test void compactHeroPackingUsesContentWidthAndUnifiedUntitledChrome() {
        var controller = ThemeTestSupport.controller(true);
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
    /**
     * The 2026-10-10 references: a window's title row is 45 above a hairline, with the controls and title on its middle;
     * a dialog's 30-logical band continues the dialog surface with no rule, its title and controls centred 21 down;
     * Settings draws its own title row, so its window shows no strip and the controls centre on the content's row.
     */
    @Test void titleRowsFollowTheReferencesForWindowsDialogsAndContentThatDrawsItsOwn() {
        var controller = ThemeTestSupport.controller(true);
        try {
            var semantic = controller.current().chrome().semantic();
            int line = com.formdev.flatlaf.util.UIScale.scale(1);
            var window = new AuxiliarySurface("test", "Credential Vault", AuxiliarySurface.Kind.WINDOW, false,
                new Dimension(900, 555), null, null, ignored -> { throw new AssertionError(); });
            var root = new JRootPane();
            try (var chrome = NativeShells.installChrome(root, window, true)) {
                var bar = chrome.bar(); root.setSize(900, 600); layout(root);
                assertThat(bar.style()).isEqualTo(dev.jasper.app.platform.MacTitleBar.Style.WINDOW);
                assertThat(bar.getHeight()).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(45) + line);
                assertThat(bar.controlsHeight()).isEqualTo(bar.getHeight());
                var title = (JLabel) bar.getComponent(0);
                assertThat(title.getY() + title.getHeight() / 2).isEqualTo((bar.getHeight() - line) / 2);
                // border.subtle carries alpha: the rule shows it over the bar.
                assertThat(row(bar, bar.getHeight() - line)).as("the rule")
                    .isEqualTo(dev.jasper.app.appearance.ThemeTestSupport.over(semantic.color(dev.jasper.ui.theme.SemanticColor.BORDER_SUBTLE), bar.getBackground()));
            }
            var dialog = new AuxiliarySurface("", "Add Host", AuxiliarySurface.Kind.DIALOG, true,
                new Dimension(500, 700), null, null, ignored -> { throw new AssertionError(); });
            root = new JRootPane();
            try (var chrome = NativeShells.installChrome(root, dialog, true)) {
                var bar = chrome.bar(); root.setSize(500, 700); layout(root);
                assertThat(bar.style()).isEqualTo(dev.jasper.app.platform.MacTitleBar.Style.DIALOG);
                assertThat(bar.getHeight()).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(30));
                assertThat(bar.controlsHeight()).as("controls centred 21 down").isEqualTo(2 * com.formdev.flatlaf.util.UIScale.scale(21));
                var title = (JLabel) bar.getComponent(0);
                assertThat(title.getY() + title.getHeight() / 2).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(21));
                assertThat(row(bar, bar.getHeight() - line)).as("no rule; the band continues the dialog")
                    .isEqualTo(semantic.color(dev.jasper.ui.theme.SemanticColor.SURFACE_DIALOG));
            }
            var settings = new AuxiliarySurface("app.settings", "Settings", AuxiliarySurface.Kind.DIALOG, true,
                new Dimension(1000, 700), null, null, ignored -> { throw new AssertionError(); });
            var content = new JPanel(); settings.setContent(content);
            settings.integratedTitle(() -> com.formdev.flatlaf.util.UIScale.scale(45));
            root = new JRootPane();
            try (var chrome = NativeShells.installChrome(root, settings, true)) {
                var bar = chrome.bar(); root.setSize(1000, 700); layout(root);
                assertThat(bar.style()).isEqualTo(dev.jasper.app.platform.MacTitleBar.Style.INTEGRATED);
                assertThat(bar.getHeight()).as("no strip").isZero();
                assertThat(bar.controlsHeight()).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(45));
                assertThat(javax.swing.SwingUtilities.convertPoint(content, 0, 0, root).y).as("content reaches the window top").isZero();
                assertThat(((JLabel) bar.getComponent(0)).isVisible()).isFalse();
            }
        } finally { controller.close(); }
    }
    /** The bar's painted colour in the middle of row {@code y}. */
    private static java.awt.Color row(javax.swing.JComponent bar, int y) {
        var image = new java.awt.image.BufferedImage(bar.getWidth(), bar.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { bar.paint(graphics); } finally { graphics.dispose(); }
        return new java.awt.Color(image.getRGB(bar.getWidth() / 2 + 200, y));
    }
    private static void layout(java.awt.Container node) {
        node.doLayout(); for (var child : node.getComponents()) if (child instanceof java.awt.Container container) layout(container);
    }

    private void verifyChrome(AuxiliarySurface.Kind kind) {
        ThemeTestSupport.controller(true);
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
