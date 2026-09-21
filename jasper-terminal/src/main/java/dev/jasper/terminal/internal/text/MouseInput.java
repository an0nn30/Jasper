package dev.jasper.terminal.internal.text;

public record MouseInput(Type type, Button button, boolean shift, boolean alt,
                         boolean control, int wheelDirection) {
    public enum Type { PRESSED, RELEASED, DRAGGED, MOVED, WHEEL }
    public enum Button { LEFT, MIDDLE, RIGHT, NONE }
}
