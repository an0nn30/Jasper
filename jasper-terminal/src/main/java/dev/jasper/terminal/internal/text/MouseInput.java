package dev.jasper.terminal.internal.text;

/**
 * Unsupported vendor-free mouse report captured with press-time modifier state. Coordinates are supplied separately.
 * @param type AWT-independent event kind
 * @param button button owning the gesture, or NONE
 * @param shift Shift modifier state captured for the gesture
 * @param alt Alt modifier state captured for the gesture
 * @param control Control modifier state captured for the gesture
 * @param wheelDirection signed wheel direction
 */
public record MouseInput(Type type, Button button, boolean shift, boolean alt,
                         boolean control, int wheelDirection) {
    /** Protocol-independent mouse event category. */
    public enum Type { PRESSED, RELEASED, DRAGGED, MOVED, WHEEL }
    /** Logical mouse button, including the no-button sentinel. */
    public enum Button { LEFT, MIDDLE, RIGHT, NONE }
}
