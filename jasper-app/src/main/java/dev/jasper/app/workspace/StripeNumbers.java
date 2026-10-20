package dev.jasper.app.workspace;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.KeyStroke;

/**
 * The digit a panel's toggle shortcut gives its stripe label (spec 10.7), IntelliJ's "1: Project": a shortcut whose key
 * is 0 to 9, held with Ctrl, Alt or Meta. Shift alone does not count, as it types a symbol, and neither do the keypad's
 * digits.
 */
final class StripeNumbers {
    private static final int MODIFIERS = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK;

    private StripeNumbers() { }

    /** The digit of {@code stroke}, or none. */
    static OptionalInt of(Optional<KeyStroke> stroke) {
        if (stroke.isEmpty()) return OptionalInt.empty();
        KeyStroke key = stroke.get();
        int code = key.getKeyCode();
        if (code < KeyEvent.VK_0 || code > KeyEvent.VK_9 || (key.getModifiers() & MODIFIERS) == 0) return OptionalInt.empty();
        return OptionalInt.of(code - KeyEvent.VK_0);
    }
}
