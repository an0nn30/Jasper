package dev.jasper.terminal.view;

import dev.jasper.terminal.internal.text.MouseInput;

import dev.jasper.terminal.internal.TerminalAccess;
import dev.jasper.terminal.internal.text.MouseGeometry;
import dev.jasper.terminal.internal.text.MouseInput.Type;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** EDT mouse gestures retain press ownership and modifiers through their matching release. */
final class MouseController {
    private static final int WHEEL_LINES = 3;
    private final TerminalAccess terminal;
    private final SelectionController selection;
    private final Viewport viewport;
    private final boolean macOs;
    private final IntSupplier cellWidth, cellHeight;
    private final BooleanSupplier copyOnSelect;
    private final Runnable requestFocus, copySelection, repaint;
    private final Consumer<String> openLink;
    private final Consumer<MouseEvent> contextMenu;
    MouseController(TerminalAccess terminal, SelectionController selection, Viewport viewport, boolean macOs,
                    IntSupplier cellWidth, IntSupplier cellHeight, BooleanSupplier copyOnSelect,
                    Runnable requestFocus, Runnable copySelection, Consumer<String> openLink,
                    Consumer<MouseEvent> contextMenu, Runnable repaint) {
        this.terminal = terminal; this.selection = selection; this.viewport = viewport; this.macOs = macOs;
        this.cellWidth = cellWidth; this.cellHeight = cellHeight; this.copyOnSelect = copyOnSelect;
        this.requestFocus = requestFocus; this.copySelection = copySelection; this.openLink = openLink;
        this.contextMenu = contextMenu; this.repaint = repaint;
    }
    void focusLost() { gestures.entrySet().removeIf(entry -> entry.getValue().action == MouseRouting.Action.OPEN_LINK); }
    /** Press ownership and report modifiers survive until that button's matching release. */
    private final java.util.EnumMap<MouseInput.Button, Gesture> gestures =
        new java.util.EnumMap<>(MouseInput.Button.class);
    private long gestureSequence;
    private double wheelRemainder;

    /** Ownership and modifier state captured on press and retained through release. */
    private static final class Gesture {
        final MouseRouting.Action action;
        final boolean shift, alt, control;
        final long sequence;
        boolean popupShown;

        Gesture(MouseRouting.Action action, boolean shift, boolean alt, boolean control, long sequence) {
            this.action = action;
            this.shift = shift; this.alt = alt; this.control = control;
            this.sequence = sequence;
        }
    }

    void handle(MouseEvent e) {
        Type type = typeOf(e);
        if (type == null || (type == Type.MOVED && !terminal.mouseReporting())) return;
        if (macOs && type == Type.WHEEL && e.isShiftDown()) return;
        int notches = type == Type.WHEEL ? notches((MouseWheelEvent) e) : 0;
        if (type == Type.WHEEL && notches == 0) return;

        MouseGeometry grid = terminal.mouseGeometry(viewport.topRow());
        int column = Math.max(0, Math.min(grid.width() - 1, e.getX() / cellWidth.getAsInt()));
        int row = Math.max(0, Math.min(grid.height() - 1, e.getY() / cellHeight.getAsInt()));
        long absoluteRow = grid.firstRow() + row;
        MouseInput.Button button = gestureButton(e, type);
        Gesture gesture = gestures.get(button);
        MouseRouting.Action action;
        if (type == Type.PRESSED) {
            requestFocus.run();
            boolean linkModifier = macOs ? e.isMetaDown() : e.isControlDown();
            action = MouseRouting.decide(type, button, e.getClickCount(), e.isShiftDown(),
                linkModifier, terminal.mouseReporting(), grid.alternateBuffer());
            // Command-click is deliberately local on macOS, even if the program requests reports.
            if (button == MouseInput.Button.LEFT && ((macOs && linkModifier)
                || action == MouseRouting.Action.OPEN_LINK)) {
                Optional<String> link = terminal.linkAt(absoluteRow, column);
                if (link.isPresent()) {
                    openLink.accept(link.get());
                    action = MouseRouting.Action.OPEN_LINK;
                } else {
                    action = MouseRouting.Action.START_SELECTION;
                }
            }
            gesture = new Gesture(action, e.isShiftDown(), e.isAltDown(), e.isControlDown(), ++gestureSequence);
            if (button != MouseInput.Button.NONE) gestures.put(button, gesture);
        } else if (type == Type.DRAGGED || type == Type.RELEASED) {
            action = gesture == null
                ? (terminal.mouseReporting() && !e.isShiftDown() ? MouseRouting.Action.REPORT : MouseRouting.Action.NONE)
                : switch (gesture.action) {
                case REPORT -> MouseRouting.Action.REPORT;
                case START_SELECTION, SELECT_WORD, SELECT_LINE -> type == Type.DRAGGED
                    ? MouseRouting.Action.EXTEND_SELECTION : MouseRouting.Action.END_SELECTION;
                default -> MouseRouting.Action.NONE;
            };
        } else {
            action = MouseRouting.decide(type, button, e.getClickCount(), e.isShiftDown(), false,
                terminal.mouseReporting(), grid.alternateBuffer());
        }

        if (action == MouseRouting.Action.REPORT) {
            int screenRow = row - grid.scrollOffset();
            if (screenRow >= 0) {
                boolean owned = gesture != null && type != Type.WHEEL && type != Type.MOVED;
                MouseInput input = new MouseInput(type, button,
                    owned ? gesture.shift : e.isShiftDown(), owned ? gesture.alt : e.isAltDown(),
                    owned ? gesture.control : e.isControlDown(), notches);
                int count = type == Type.WHEEL ? Math.abs(notches) : 1;
                for (int i = 0; i < count; i++) {
                    terminal.reportMouse(column, screenRow, input);
                }
            }
            if (type == Type.RELEASED) gestures.remove(button);
            return; // Reports need no copied screen content and no repaint.
        }
        switch (action) {
            case START_SELECTION -> selection.start(absoluteRow, column, e.isAltDown());
            case SELECT_WORD -> selection.word(absoluteRow, column);
            case SELECT_LINE -> selection.line(absoluteRow);
            case EXTEND_SELECTION -> selection.extend(absoluteRow, column);
            case END_SELECTION -> {
                selection.finish();
                if (copyOnSelect.getAsBoolean() && selection.hasSelection()) copySelection.run();
            }
            case SCROLL_VIEW -> { viewport.scrollBy(notches * WHEEL_LINES, terminal.snapshot(viewport.topRow())); repaint.run(); }
            case SEND_ARROWS -> sendArrows(notches);
            default -> { }
        }
        if (button == MouseInput.Button.RIGHT && gesture != null && e.isPopupTrigger() && !gesture.popupShown) {
            gesture.popupShown = true;
            contextMenu.accept(e);
        }
        if (type == Type.RELEASED) gestures.remove(button);
        if (action != MouseRouting.Action.NONE && action != MouseRouting.Action.OPEN_LINK) repaint.run();
    }

    /** AWT drag events usually have NOBUTTON; prefer the latest owned button still held. */
    private MouseInput.Button gestureButton(MouseEvent event, Type type) {
        if (type != Type.DRAGGED || event.getButton() != MouseEvent.NOBUTTON) return buttonOf(event);
        MouseInput.Button result = MouseInput.Button.NONE;
        long newest = -1;
        int held = event.getModifiersEx() & (InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK
            | InputEvent.BUTTON3_DOWN_MASK);
        for (var entry : gestures.entrySet()) {
            int mask = switch (entry.getKey()) {
                case LEFT -> InputEvent.BUTTON1_DOWN_MASK;
                case MIDDLE -> InputEvent.BUTTON2_DOWN_MASK;
                case RIGHT -> InputEvent.BUTTON3_DOWN_MASK;
                case NONE -> 0;
            };
            if ((held == 0 || (held & mask) != 0) && entry.getValue().sequence > newest) {
                result = entry.getKey();
                newest = entry.getValue().sequence;
            }
        }
        return result;
    }

    /**
     * Accumulates a wheel event's precise rotation and returns the whole notches it now adds up to (rounded toward
     * zero), keeping the fractional remainder for the next event. Trackpads deliver many events whose precise
     * rotation is well under one notch, and some events report an integer rotation of 0 despite a nonzero precise
     * value, so acting on {@code getWheelRotation()} directly would under- or over-react.
     */
    private int notches(MouseWheelEvent e) {
        wheelRemainder += e.getPreciseWheelRotation();
        int whole = (int) wheelRemainder;
        wheelRemainder -= whole;
        return whole;
    }

    private static Type typeOf(MouseEvent e) {
        return switch (e.getID()) {
            case MouseEvent.MOUSE_PRESSED -> Type.PRESSED;
            case MouseEvent.MOUSE_RELEASED -> Type.RELEASED;
            case MouseEvent.MOUSE_DRAGGED -> Type.DRAGGED;
            case MouseEvent.MOUSE_MOVED -> Type.MOVED;
            case MouseEvent.MOUSE_WHEEL -> Type.WHEEL;
            default -> null;
        };
    }

    private static MouseInput.Button buttonOf(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            return MouseInput.Button.LEFT;
        }
        if (SwingUtilities.isMiddleMouseButton(e)) {
            return MouseInput.Button.MIDDLE;
        }
        if (SwingUtilities.isRightMouseButton(e)) {
            return MouseInput.Button.RIGHT;
        }
        return MouseInput.Button.NONE;
    }

    private void sendArrows(int rotation) {
        byte[] arrow = terminal.codeForKey(rotation < 0 ? KeyEvent.VK_UP : KeyEvent.VK_DOWN, 0);
        if (arrow == null) {
            return;
        }
        for (int i = 0; i < Math.abs(rotation); i++) {
            terminal.write(arrow);
        }
    }
}
