package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;

import javax.swing.JRootPane;
import org.junit.jupiter.api.Test;

class WindowInputTest {
    /** Browser plan 4a: a window whose root holds an overlay shows a modal; no window shows none. */
    @Test void aRootWithAnOverlayShowsAModal() {
        var root = new JRootPane();
        assertThat(WindowInput.showsModal(root)).isFalse();
        root.putClientProperty(WindowInput.OVERLAY, new Object());
        assertThat(WindowInput.showsModal(root)).isTrue();
        assertThat(WindowInput.showsModal(null)).isFalse();
    }
}
