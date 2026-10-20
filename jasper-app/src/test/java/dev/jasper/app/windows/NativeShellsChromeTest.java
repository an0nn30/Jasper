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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(EdtTestExtension.class)
class NativeShellsChromeTest {
    @Test void everyAuxiliarySurfaceGetsTheSharedTitleAndKeepsItsContent() {
        for (var kind : AuxiliarySurface.Kind.values()) verifyChrome(kind);
    }

    /**
     * Plan 14 final review: under a classic title on Windows an auxiliary window has FlatLaf's title pane (Rulings 16), so a
     * classic sheet drawn in it hangs below that pane, not over its caption and window buttons; the default look publishes nothing.
     */
    @Test void aWindowsClassicTitleStartsAnAuxiliaryWindowsSheetsBelowItsTitlePane() {
        var controller = ThemeTestSupport.controller(false);
        try {
            var windowsClassic = new ShellChrome(false, true, true, true, false);
            var manager = new AuxiliarySurface("test", "Credential Vault", AuxiliarySurface.Kind.WINDOW, false,
                new Dimension(900, 555), null, null, ignored -> { throw new AssertionError(); });
            var root = new JRootPane();
            int[] announced = {0};
            root.addPropertyChangeListener(dev.jasper.app.platform.TitleInset.CHANGED, event -> announced[0]++);
            try (var chrome = NativeShells.installChrome(root, manager, windowsClassic)) {
                assertThat(dev.jasper.app.platform.TitleInset.of(root)).as("no title pane before FlatLaf decorates").isZero();
                int before = announced[0];
                // FlatLaf decorates a Windows frame when its peer is created, after the chrome is installed.
                root.setWindowDecorationStyle(JRootPane.FRAME);
                root.setSize(900, 620); layout(root);
                var loop = java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
                javax.swing.SwingUtilities.invokeLater(loop::exit); loop.enter();
                assertThat(announced[0]).as("the inset is announced once decorations arrive").isGreaterThan(before);
                int top = root.getContentPane().getY();
                assertThat(top).as("FlatLaf's title pane").isPositive();
                assertThat(dev.jasper.app.platform.TitleInset.of(root)).isEqualTo(top);
                var body = new JPanel(); body.setPreferredSize(new Dimension(440, 300));
                var sheet = new dev.jasper.ui.swing.ClassicDialog(dev.jasper.app.testsupport.KitTestSupport.context(), body, "Add Login",
                    dev.jasper.ui.model.Nodes.Hue.BLUE, dev.jasper.ui.swing.ClassicDialog.Placement.SHEET);
                var addLogin = WindowOverlay.classic(root, sheet);
                try {
                    addLogin.show();
                    assertThat(sheet.getY()).as("joined under the title pane").isEqualTo(top);
                } finally { addLogin.close(); }
            }
            var standard = new JRootPane();
            standard.setWindowDecorationStyle(JRootPane.FRAME); standard.updateUI();
            try (var chrome = NativeShells.installChrome(standard, manager, new ShellChrome(false, false, false, false, false))) {
                standard.setSize(900, 620); layout(standard);
                assertThat(standard.getContentPane().getY()).isPositive();
                assertThat(dev.jasper.app.platform.TitleInset.of(standard)).as("the default look publishes no inset").isZero();
            }
        } finally { controller.close(); }
    }

    /**
     * A dialog sized to its content (a plugin's dialog, a confirmation) is fixed-size; only a dialog that remembers its
     * bounds resizes, a window-sized plugin dialog among them.
     */
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
        assertThat(NativeShells.resizable(windowSized())).as("a window-sized plugin dialog").isTrue();
    }

    /** A modeless plugin dialog showing a WINDOW form, as HostedUi makes it window-sized before its first show. */
    private static AuxiliarySurface windowSized() {
        var surface = new AuxiliarySurface("", "Credential Vault", AuxiliarySurface.Kind.DIALOG, false, new Dimension(1, 1), null, null,
            ignored -> { throw new AssertionError("No native shell in tests"); });
        surface.windowSized("dev.jasper.vault|Credential Vault");
        return surface;
    }

    /**
     * A window-sized dialog remembers its bounds under its plugin and title, opens at least a window's content size (900 x
     * 555, 820 x 571 under the classic mapping) plus its chrome, within the usable screen, and the shell never re-packs it,
     * so the user's size stands; a plugin dialog packed to its content keeps following it.
     */
    @Test void aWindowSizedDialogOpensAtAWindowsSizeAndIsNeverRepacked() {
        java.util.function.Function<Boolean, AuxiliarySurface> plain = modal -> new AuxiliarySurface("", "Manager", AuxiliarySurface.Kind.DIALOG,
            modal, new Dimension(1, 1), null, null, ignored -> { throw new AssertionError("No native shell in tests"); });
        assertThat(NativeShells.fitted(plain.apply(false))).as("a modeless dialog packed to its content follows it").isTrue();
        assertThat(NativeShells.fitted(plain.apply(true))).as("so does a modal one").isTrue();
        assertThatThrownBy(() -> plain.apply(true).windowSized("test.kit|Manager")).as("a modal dialog stays packed")
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AuxiliarySurface("", "Busy", AuxiliarySurface.Kind.OVERLAY, false, new Dimension(1, 1), null, null,
            ignored -> { throw new AssertionError(); }).windowSized("test.kit|Busy")).isInstanceOf(IllegalStateException.class);
        var surface = windowSized();
        assertThat(surface.windowSized()).isTrue();
        assertThat(surface.id()).as("remembered under its plugin and title").isEqualTo("dev.jasper.vault|Credential Vault");
        assertThat(NativeShells.fitted(surface)).as("never re-packed: the user's size stands").isFalse();
        var content = new JPanel(); content.setPreferredSize(new Dimension(810, 185)); surface.setContent(content);
        var chrome = new Dimension(0, 28); var screen = new java.awt.Rectangle(0, 0, 2000, 1500);
        assertThat(NativeShells.windowSize(surface, chrome, screen)).as("the floor, a smaller content's").isEqualTo(new Dimension(900, 583));
        surface.classic(true);
        assertThat(NativeShells.windowSize(surface, chrome, screen)).as("the classic floor").isEqualTo(new Dimension(820, 599));
        content.setPreferredSize(new Dimension(964, 640));
        assertThat(NativeShells.windowSize(surface, chrome, screen)).as("a larger content, per dimension").isEqualTo(new Dimension(964, 668));
        assertThat(NativeShells.windowSize(surface, chrome, new java.awt.Rectangle(0, 0, 800, 500))).as("within the usable screen")
            .isEqualTo(new Dimension(800, 500));
    }

    @Test void compactPromptPackingUsesContentWidthAndUnifiedUntitledChrome() {
        var controller = ThemeTestSupport.controller(true);
        var resolved = controller.current();
        var binding = new dev.jasper.ui.theme.ThemeBinding(resolved.pair(), failure -> { throw new AssertionError(failure); });
        var icons = new dev.jasper.ui.icons.IconRegistry();
        var context = dev.jasper.ui.swing.KitContext.of(dev.jasper.ui.kit.Kit.DEFAULT, binding, dev.jasper.ui.theme.ThemeRoute.CHROME, icons, () -> false, action -> "");
        var form = new dev.jasper.ui.model.Nodes.Form("hero", dev.jasper.ui.model.ControlState.READY,
            dev.jasper.ui.model.Nodes.FormKind.PROMPT_COMPACT, "Unlock Vault", java.util.Optional.empty(), java.util.List.of(), java.util.Optional.empty(), java.util.List.of());
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

    /** Plan 3d: DialogSpec.swatch is a square of its hue before a dialog's title in Jasper's title row; NONE draws none. */
    @Test void aDialogSwatchSitsBeforeItsTitleInItsHuesColour() {
        var controller = ThemeTestSupport.controller(true);
        try {
            for (var hue : java.util.List.of(dev.jasper.ui.model.Nodes.Hue.BLUE, dev.jasper.ui.model.Nodes.Hue.NONE)) {
                var surface = new AuxiliarySurface("", "Add Login", AuxiliarySurface.Kind.DIALOG, true, new Dimension(400, 300), null, null,
                    ignored -> { throw new AssertionError("No native shell in tests"); });
                assertThat(surface.swatch()).as("none until asked for").isEqualTo(dev.jasper.ui.model.Nodes.Hue.NONE);
                surface.swatch(hue);
                try (var bar = NativeShells.installTitleBar(new JRootPane(), surface, true)) {
                    var title = (JLabel) bar.getComponent(0);
                    assertThat(title.isVisible()).as("a dialog's title row shows its title").isTrue();
                    if (hue == dev.jasper.ui.model.Nodes.Hue.NONE) { assertThat(title.getIcon()).isNull(); continue; }
                    var icon = title.getIcon();
                    int side = com.formdev.flatlaf.util.UIScale.scale(12);
                    assertThat(icon.getIconWidth()).as("the caption icon size").isEqualTo(side);
                    assertThat(icon.getIconHeight()).isEqualTo(side);
                    var image = new java.awt.image.BufferedImage(side, side, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    var graphics = image.createGraphics();
                    try { icon.paintIcon(title, graphics, 0, 0); } finally { graphics.dispose(); }
                    assertThat(new java.awt.Color(image.getRGB(side / 2, side / 2), true))
                        .isEqualTo(controller.current().chrome().semantic().color(dev.jasper.ui.theme.SemanticColor.HUE_BLUE));
                }
            }
        } finally { controller.close(); }
    }

    @Test void actualChromeCentresTheNativeTitleOverTheBodyAndFallsBack() {
        for (boolean supported : java.util.List.of(true, false)) {
            var surface = new AuxiliarySurface("test", "Manager", AuxiliarySurface.Kind.WINDOW, false,
                new Dimension(900, 555), null, null, ignored -> { throw new AssertionError(); });
            var root = new JRootPane();
            try (var chrome = NativeShells.installChrome(root, surface, supported)) {
                var bar = chrome.bar();
                root.setSize(900, 620); layout(root);
                int bodyTop = javax.swing.SwingUtilities.convertPoint(surface.holder(), 0, 0, root).y;
                assertThat(chrome.body().boundsInRoot()).contains(new java.awt.Rectangle(0, bodyTop, 900, 620 - bodyTop));
                assertThat(NativeShells.initialSize(surface, new java.awt.Rectangle(0, 0, 2000, 1500))).isEqualTo(new Dimension(900, 555));
                assertThat(NativeShells.initialSize(surface, new java.awt.Rectangle(0, 0, 800, 500))).as("within the usable screen")
                    .isEqualTo(new Dimension(800, 500));
                assertThat(root.getSize()).isEqualTo(new Dimension(900, 620));
                if (supported) {
                    var title = (JLabel) bar.getComponent(0);
                    assertThat(title.isVisible()).isTrue();
                    assertThat(title.getX() * 2 + title.getWidth()).isEqualTo(900);
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
