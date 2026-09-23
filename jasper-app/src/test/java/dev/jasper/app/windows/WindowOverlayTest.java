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
    @Test void centersClampsAndReleasesTheRootAfterRepeatedUse() {
        var root = new JRootPane(); root.getLayeredPane().setSize(800, 600);
        var card = new JPanel(); card.setPreferredSize(new Dimension(400, 190));
        int listeners = root.getLayeredPane().getComponentListeners().length;
        for (int i = 0; i < 3; i++) {
            var overlay = new WindowOverlay(root, card);
            overlay.show(); overlay.show();
            assertThat(overlay.cardBounds()).isEqualTo(new Rectangle(200, 205, 400, 190));
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
        var overlay = new WindowOverlay(root, card); overlay.show();
        try {
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
        var overlay = new WindowOverlay(root, card); overlay.show();
        try {
            int layers = root.getLayeredPane().getComponentCount();
            card.setPreferredSize(new Dimension(500, 250)); overlay.layout();
            assertThat(overlay.cardBounds()).isEqualTo(new Rectangle(150, 175, 500, 250));
            assertThat(root.getLayeredPane().getComponentCount()).isEqualTo(layers);
        } finally { overlay.close(); }
    }

    private static KeyEvent key(JComponent source, int code, int modifiers) {
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, modifiers, code, KeyEvent.CHAR_UNDEFINED);
    }
}
