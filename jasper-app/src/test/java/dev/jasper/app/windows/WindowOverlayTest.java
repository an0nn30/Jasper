package dev.jasper.app.windows;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class WindowOverlayTest {
    /** The overlays' kit: motion off, so each appears and leaves at once. */
    private static dev.jasper.ui.swing.KitContext kit() { return dev.jasper.app.testsupport.KitTestSupport.context(); }

    /**
     * A dialog over a progress overlay (Vault unlock while Remote connects) stacks: the newest takes the keys, its
     * Escape reaches its content, and closing it hands the window back to the one below.
     */
    @Test void aDialogOverlayStacksOverAProgressOverlayAndHandsInputBack() {
        var root = new JRootPane(); root.getLayeredPane().setSize(800, 600);
        var behind = new JTextField(); root.setContentPane(behind);
        var progress = new JPanel(); var cancel = new JButton("Cancel"); progress.add(cancel);
        var prompt = new JPanel(); var password = new JTextField(); prompt.add(password);
        var connecting = new WindowOverlay(root, progress, kit(), false); connecting.show();
        var unlock = new WindowOverlay(root, prompt, kit(), true);
        try {
            unlock.show();
            assertThat(root.getClientProperty(dev.jasper.app.platform.WindowInput.OVERLAY)).isSameAs(unlock);
            var layers = root.getLayeredPane();
            assertThat(layers.getLayer(SwingUtilities.getAncestorOfClass(dev.jasper.ui.swing.ModalOverlay.class, prompt)))
                .as("above the progress overlay").isGreaterThan(layers.getLayer(SwingUtilities.getAncestorOfClass(dev.jasper.ui.swing.ModalOverlay.class, progress)));
            assertThat(unlock.filterKey(key(password, KeyEvent.VK_ESCAPE, 0))).as("a dialog's Escape reaches its Cancel").isFalse();
            assertThat(unlock.filterKey(key(cancel, KeyEvent.VK_SPACE, 0))).as("the overlay below is blocked").isTrue();
            assertThat(connecting.filterKey(key(password, KeyEvent.VK_A, 0))).as("only the newest filters").isFalse();
            unlock.close();
            assertThat(prompt.getParent()).isNull();
            assertThat(root.getClientProperty(dev.jasper.app.platform.WindowInput.OVERLAY)).isSameAs(connecting);
            assertThat(connecting.filterKey(key(cancel, KeyEvent.VK_ESCAPE, 0))).as("the progress overlay keeps Escape").isTrue();
            assertThat(connecting.filterKey(key(behind, KeyEvent.VK_A, 0))).isTrue();
        } finally { unlock.close(); connecting.close(); }
        assertThat(dev.jasper.app.platform.WindowInput.blocked(root)).isFalse();
        assertThat(root.getClientProperty("Jasper.windowOverlays")).isNull();
    }

    @Test void repaintingOpaqueContentPreservesTheRoundedCornerFill() {
        var root = new JRootPane(); root.setSize(800, 600);
        root.getContentPane().setBackground(java.awt.Color.WHITE);
        root.doLayout(); root.getLayeredPane().doLayout();
        var content = new JPanel(); content.setBackground(java.awt.Color.RED);
        content.setPreferredSize(new Dimension(400, 190));
        var overlay = new WindowOverlay(root, content, kit(), false); overlay.show();
        try {
            var card = (JPanel) content.getParent(); card.doLayout();
            for (int scale : new int[] {1, 2}) {
                var image = new java.awt.image.BufferedImage(800 * scale, 600 * scale, java.awt.image.BufferedImage.TYPE_INT_RGB);
                var g = image.createGraphics();
                try { g.scale(scale, scale); root.paint(g); } finally { g.dispose(); }
                int[] before = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
                g = image.createGraphics();
                try {
                    g.scale(scale, scale);
                    g.setClip(SwingUtilities.convertRectangle(card, content.getBounds(), root));
                    root.paint(g);
                } finally { g.dispose(); }
                int[] after = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
                int changed = 0;
                for (int i = 0; i < before.length; i++) if (before[i] != after[i]) changed++;
                assertThat(changed).as("pixels changed by partial child repaint at %sx", scale).isZero();
            }
        } finally { overlay.close(); }
    }

    @Test void roundedCardClipsOpaqueContentAndKeepsTheKitOutlineAcrossThemes() {
        var bindings = dev.jasper.app.testsupport.KitTestSupport.bindings(true);
        try {
            var context = bindings.context(dev.jasper.ui.theme.ThemeRoute.CHROME);
            var root = new JRootPane(); root.getLayeredPane().setSize(800, 600);
            var content = new JPanel(); content.setPreferredSize(new Dimension(400, 190));
            content.setBackground(java.awt.Color.RED);
            var overlay = new WindowOverlay(root, content, context, false);
            try {
                overlay.show();
                var card = (JPanel) content.getParent(); card.doLayout();
                for (boolean dark : new boolean[] {true, false}) {
                    bindings.binding().publish(dev.jasper.app.appearance.ThemeTestSupport.pair(dark));
                    var border = context.theme().semantic().color(dev.jasper.ui.theme.SemanticColor.BORDER_WINDOW);
                    var image = new java.awt.image.BufferedImage(card.getWidth(), card.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    var graphics = image.createGraphics();
                    try { card.paint(graphics); } finally { graphics.dispose(); }
                    assertThat(image.getRGB(1, 1) >>> 24).as("opaque plugin content must not square off the card").isZero();
                    assertThat(image.getRGB(card.getWidth() / 2, card.getHeight() / 2)).isEqualTo(java.awt.Color.RED.getRGB());
                    assertThat(new java.awt.Color(image.getRGB(card.getWidth() / 2, 0), true).getRGB() & 0xFFFFFF)
                        .as("the kit's window outline in the %s theme", dark ? "dark" : "light").isEqualTo(border.getRGB() & 0xFFFFFF);
                }
            } finally { overlay.close(); }
        } finally { bindings.close(); }
    }

    @Test void centersClampsAndReleasesTheRootAfterRepeatedUse() {
        var root = new JRootPane(); root.getLayeredPane().setSize(800, 600);
        var card = new JPanel(); card.setPreferredSize(new Dimension(400, 190));
        int listeners = root.getLayeredPane().getComponentListeners().length;
        for (int i = 0; i < 3; i++) {
            var overlay = new WindowOverlay(root, card, kit(), false);
            overlay.show(); overlay.show();
            assertThat(overlay.cardBounds()).isEqualTo(new Rectangle(199, 204, 402, 192));
            root.getLayeredPane().setSize(240, 140); overlay.layout();
            Rectangle bounds = overlay.cardBounds();
            assertThat(bounds.x * 2 + bounds.width).isEqualTo(240);
            assertThat(bounds.y * 2 + bounds.height).isEqualTo(140);
            assertThat(bounds.x).isGreaterThanOrEqualTo(0);
            assertThat(bounds.y).isGreaterThanOrEqualTo(0);
            overlay.close(); overlay.close();
            assertThat(card.getParent()).isNull();
            assertThat(root.getLayeredPane().getComponentListeners()).hasSize(listeners);
            root.getLayeredPane().setSize(800, 600);
        }
    }

    @Test void blocksBackgroundAndEscapeButAllowsOverlayControlsAndSeparatePromptRoots() {
        var root = new JRootPane(); root.getLayeredPane().setSize(800, 600);
        var behind = new JTextField(); root.setContentPane(behind);
        var card = new JPanel(); var cancel = new JButton("Cancel"); var text = new JTextField();
        card.add(cancel); card.add(text);
        var overlay = new WindowOverlay(root, card, kit(), false); overlay.show();
        try {
            var backdrop = root.getLayeredPane().getComponent(0);
            var outside = new java.awt.event.MouseEvent(backdrop, java.awt.event.MouseEvent.MOUSE_PRESSED,
                0, 0, 1, 1, 1, false);
            backdrop.dispatchEvent(outside);
            assertThat(outside.isConsumed()).isTrue();
            assertThat(dev.jasper.app.platform.WindowInput.blocked(root)).isTrue();
            assertThat(overlay.filterKey(key(behind, KeyEvent.VK_A, 0))).isTrue();
            assertThat(overlay.filterKey(key(cancel, KeyEvent.VK_ESCAPE, 0))).isTrue();
            assertThat(overlay.filterKey(key(cancel, KeyEvent.VK_N, KeyEvent.META_DOWN_MASK))).isTrue();
            assertThat(overlay.filterKey(key(cancel, KeyEvent.VK_SPACE, 0))).isFalse();
            assertThat(overlay.filterKey(key(text, KeyEvent.VK_V, KeyEvent.META_DOWN_MASK))).isFalse();
            var promptRoot = new JRootPane(); var prompt = new JTextField(); promptRoot.setContentPane(prompt);
            assertThat(overlay.filterKey(key(prompt, KeyEvent.VK_A, 0))).isFalse();
        } finally { overlay.close(); }
        assertThat(overlay.filterKey(key(behind, KeyEvent.VK_A, 0))).isFalse();
    }

    @Test void resizedContentIsRecenteredWithoutAddingAnotherLayer() {
        var root = new JRootPane(); root.getLayeredPane().setSize(800, 600);
        var card = new JPanel(); card.setPreferredSize(new Dimension(300, 150));
        var overlay = new WindowOverlay(root, card, kit(), false); overlay.show();
        try {
            int layers = root.getLayeredPane().getComponentCount();
            card.setPreferredSize(new Dimension(500, 250)); overlay.layout();
            assertThat(overlay.cardBounds()).isEqualTo(new Rectangle(149, 174, 502, 252));
            assertThat(root.getLayeredPane().getComponentCount()).isEqualTo(layers);
        } finally { overlay.close(); }
    }

    @Test void contentRevalidationRecentersNaturallyAndNormalSizeNeedsNoScrollbars() {
        var outer = new JPanel(new java.awt.BorderLayout());
        var root = new JRootPane(); outer.add(root); outer.setSize(800, 600); outer.addNotify(); outer.validate();
        var content = new JPanel(); content.setPreferredSize(new Dimension(400, 190));
        var overlay = new WindowOverlay(root, content, kit(), false);
        try {
            overlay.show(); root.validate();
            assertThat(hasScrollbars(root.getLayeredPane())).isFalse();
            var card = (JPanel) content.getParent();
            assertThat(content.getBounds()).isEqualTo(new Rectangle(1, 1, 400, 190));
            assertThat(card.getSize()).isEqualTo(new Dimension(402, 192));
            content.setPreferredSize(new Dimension(500, 250));
            content.revalidate();
            // Drive Swing's nearest validate root without needing a native Window in this test.
            java.awt.Container validationRoot = content;
            while (validationRoot instanceof JComponent component && !component.isValidateRoot())
                validationRoot = validationRoot.getParent();
            validationRoot.validate();
            assertThat(overlay.cardBounds()).isEqualTo(new Rectangle(149, 174, 502, 252));
        } finally { overlay.close(); outer.removeNotify(); }
    }

    @Test void constrainedOverlayLaysOutContentInItsAvailableInteriorWithoutScrolling() {
        var root = new JRootPane(); root.getLayeredPane().setSize(360, 260);
        var content = new JPanel(new java.awt.BorderLayout());
        var cancel = new JButton("Cancel"); content.add(cancel, java.awt.BorderLayout.SOUTH);
        content.setPreferredSize(new Dimension(500, 280));
        var overlay = new WindowOverlay(root, content, kit(), false);
        try {
            overlay.show(); content.doLayout();
            assertThat(hasScrollbars(root.getLayeredPane())).isFalse();
            Rectangle bounds = overlay.cardBounds();
            assertThat(content.getSize()).isEqualTo(new Dimension(bounds.width - 2, bounds.height - 2));
            assertThat(content.getBounds().contains(javax.swing.SwingUtilities.convertRectangle(cancel.getParent(), cancel.getBounds(), content.getParent()))).isTrue();
        } finally { overlay.close(); }
    }

    private static boolean hasScrollbars(java.awt.Container container) {
        for (var child : container.getComponents()) {
            if (child instanceof JScrollBar || child instanceof JScrollPane) return true;
            if (child instanceof java.awt.Container nested && hasScrollbars(nested)) return true;
        }
        return false;
    }

    private static KeyEvent key(JComponent source, int code, int modifiers) {
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, modifiers, code, KeyEvent.CHAR_UNDEFINED);
    }
}
