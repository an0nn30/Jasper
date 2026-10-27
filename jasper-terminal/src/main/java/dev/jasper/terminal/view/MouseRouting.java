package dev.jasper.terminal.view;

import dev.jasper.terminal.internal.text.MouseInput;

import dev.jasper.terminal.internal.text.MouseInput.Button;

/** Decides what a mouse event means for the terminal view. */
final class MouseRouting {

    /** One local routing decision before any gesture mutation. */
    enum Action {
        REPORT, START_SELECTION, SELECT_WORD, SELECT_LINE, EXTEND_SELECTION, END_SELECTION,
        OPEN_LINK, SCROLL_VIEW, SEND_ARROWS, NONE
    }

    private MouseRouting() {
    }

    /**
     * @param shift        Shift held: the mouse stays local even when the program wants reports
     * @param linkModifier ⌘ on macOS, Ctrl on Linux and Windows
     * @param reporting    the program enabled mouse reporting
     */
    static Action decide(MouseInput.Type type, Button button, int clickCount, boolean shift, boolean linkModifier,
                         boolean reporting, boolean alternateBuffer) {
        if (reporting && !shift) {
            return Action.REPORT;
        }
        if (type == MouseInput.Type.WHEEL) {
            return alternateBuffer ? Action.SEND_ARROWS : Action.SCROLL_VIEW;
        }
        if (button != Button.LEFT) {
            return Action.NONE;
        }
        return switch (type) {
            case PRESSED -> linkModifier ? Action.OPEN_LINK
                : clickCount >= 3 ? Action.SELECT_LINE
                : clickCount == 2 ? Action.SELECT_WORD
                : Action.START_SELECTION;
            case DRAGGED -> Action.EXTEND_SELECTION;
            case RELEASED -> Action.END_SELECTION;
            default -> Action.NONE;
        };
    }

    /**
     * The gesture of a click that landed on a web link. Shift with the link modifier is a split; a Ctrl-click on Linux
     * or Windows is reported to a program that asks for mouse reports unless Shift is held, so there Shift cannot also
     * mean a split and the click is an open. A Cmd-click is local on macOS whether or not the program asks.
     *
     * @param shift        Shift held
     * @param linkModifier the link modifier held
     * @param macOs        the platform is macOS
     * @param reporting    the program enabled mouse reporting
     */
    static TerminalLink.Gesture linkGesture(boolean shift, boolean linkModifier, boolean macOs, boolean reporting) {
        return shift && linkModifier && (macOs || !reporting) ? TerminalLink.Gesture.SPLIT : TerminalLink.Gesture.OPEN;
    }
}
