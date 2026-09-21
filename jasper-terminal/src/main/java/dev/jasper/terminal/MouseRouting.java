package dev.jasper.terminal;

import dev.jasper.terminal.MouseInput.Button;

/** Decides what a mouse event means for the terminal view. */
final class MouseRouting {


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
}
