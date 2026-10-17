package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Optional;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

/** Spec 10.7 and Rulings 10: which toggle strokes number a stripe label. */
class StripeNumbersTest {
    private static Optional<KeyStroke> stroke(int code, int modifiers) { return Optional.of(KeyStroke.getKeyStroke(code, modifiers)); }

    @Test void aDigitWithCtrlAltOrMetaNumbersTheLabel() {
        assertThat(StripeNumbers.of(stroke(KeyEvent.VK_1, InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK))).hasValue(1);
        assertThat(StripeNumbers.of(stroke(KeyEvent.VK_2, InputEvent.ALT_DOWN_MASK))).hasValue(2);
        assertThat(StripeNumbers.of(stroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))).hasValue(0);
    }

    @Test void anythingElseLeavesTheLabelUnnumbered() {
        assertThat(StripeNumbers.of(Optional.empty())).isEmpty();
        assertThat(StripeNumbers.of(stroke(KeyEvent.VK_1, 0))).as("a bare digit").isEmpty();
        assertThat(StripeNumbers.of(stroke(KeyEvent.VK_1, InputEvent.SHIFT_DOWN_MASK))).as("Shift alone types a symbol").isEmpty();
        assertThat(StripeNumbers.of(stroke(KeyEvent.VK_S, InputEvent.ALT_DOWN_MASK))).as("not a digit").isEmpty();
        assertThat(StripeNumbers.of(stroke(KeyEvent.VK_NUMPAD1, InputEvent.ALT_DOWN_MASK))).as("the keypad's digits do not count").isEmpty();
    }
}
