package dev.jasper.terminal;

import java.awt.event.InputEvent;

/** The parts of a KeyEvent the encoder needs. {@code modifiers} is {@code KeyEvent.getModifiersEx()}. */
record KeyInput(int keyCode, char keyChar, int modifiers, boolean leftAltHeld, boolean rightAltHeld) {

    boolean shift() {
        return (modifiers & InputEvent.SHIFT_DOWN_MASK) != 0;
    }

    boolean ctrl() {
        return (modifiers & InputEvent.CTRL_DOWN_MASK) != 0;
    }

    boolean alt() {
        return (modifiers & InputEvent.ALT_DOWN_MASK) != 0;
    }

    boolean meta() {
        return (modifiers & InputEvent.META_DOWN_MASK) != 0;
    }
}
