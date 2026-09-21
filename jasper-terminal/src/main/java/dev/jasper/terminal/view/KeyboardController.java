package dev.jasper.terminal.view;

import dev.jasper.terminal.internal.TerminalAccess;
import java.awt.event.KeyEvent;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** EDT keyboard state: modifier tracking, shortcut arbitration and terminal encoding. */
final class KeyboardController {
    private final TerminalAccess terminal;
    private KeyEncoder keys;
    private final Predicate<KeyEvent> shortcut;
    private final BooleanSupplier exited;
    private final Runnable closeRequest, inputAccepted;
    private boolean suppressNextTyped, leftAltHeld, rightAltHeld;
    KeyboardController(TerminalAccess terminal, KeyEncoder keys, Predicate<KeyEvent> shortcut,
                       BooleanSupplier exited, Runnable closeRequest, Runnable inputAccepted) {
        this.terminal = terminal; this.keys = keys; this.shortcut = shortcut;
        this.exited = exited; this.closeRequest = closeRequest; this.inputAccepted = inputAccepted;
    }
    void setEncoder(KeyEncoder keys) { this.keys = keys; }
    void focusLost() { leftAltHeld = false; rightAltHeld = false; }
    void handle(KeyEvent e) {
        trackAltKeys(e);
        if (e.getID() == KeyEvent.KEY_PRESSED && shortcut.test(e)) {
            suppressNextTyped = true;
            e.consume();
            return;
        }
        if (exited.getAsBoolean()) {
            if (e.getID() == KeyEvent.KEY_PRESSED && !isModifierOnly(e.getKeyCode())) {
                closeRequest.run();
            }
            e.consume();
            return;
        }
        KeyInput input = new KeyInput(e.getKeyCode(), e.getKeyChar(), e.getModifiersEx(), leftAltHeld, rightAltHeld);
        byte[] bytes = switch (e.getID()) {
            case KeyEvent.KEY_PRESSED -> {
                byte[] pressed = keys.pressed(input, terminal::codeForKey);
                suppressNextTyped = pressed != null; // re-decided on every press
                yield pressed;
            }
            case KeyEvent.KEY_TYPED -> {
                if (suppressNextTyped) {
                    suppressNextTyped = false;
                    e.consume();
                    yield null;
                }
                yield keys.typed(input);
            }
            default -> null;
        };
        if (bytes != null) {
            terminal.write(bytes);
            inputAccepted.run();
            e.consume();
        }
    }

    private void trackAltKeys(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.VK_ALT) {
            return;
        }
        boolean down = e.getID() == KeyEvent.KEY_PRESSED;
        if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT) {
            rightAltHeld = down;
        } else {
            leftAltHeld = down;
        }
    }

    private static boolean isModifierOnly(int keyCode) {
        return keyCode == KeyEvent.VK_SHIFT || keyCode == KeyEvent.VK_CONTROL || keyCode == KeyEvent.VK_ALT
            || keyCode == KeyEvent.VK_ALT_GRAPH || keyCode == KeyEvent.VK_META;
    }
}
