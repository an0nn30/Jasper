package dev.jasper.browser.page;

import java.util.Optional;

/**
 * An editing command a page's focused frame runs for a key the page and its scripts left alone. On macOS Chromium
 * leaves Cmd+A, Cmd+X, Cmd+Z and Cmd+Shift+Z to the browser's Edit menu, so an off-screen page does nothing with them
 * until it is told; on Linux and Windows Chromium runs them itself. Copy and Paste are Jasper's own actions.
 */
enum EditingCommand {
    SELECT_ALL, CUT, UNDO, REDO;

    // Chromium's event flags (cef_event_flags_t) for the modifier keys; lock keys and key positions are left out.
    private static final int SHIFT = 1 << 1, CONTROL = 1 << 2, ALT = 1 << 3, COMMAND = 1 << 7;
    private static final int MODIFIERS = SHIFT | CONTROL | ALT | COMMAND;
    // Chromium's Windows key codes for A, X and Z.
    private static final int A = 0x41, X = 0x58, Z = 0x5A;
    private static final boolean MAC_OS = System.getProperty("os.name", "").startsWith("Mac");

    /**
     * The command a key the page left alone runs on this platform.
     *
     * @param rawKeyDown whether the event is the key's press
     * @param modifiers its event flags
     * @param keyCode its Windows key code
     * @return the command, or empty
     */
    static Optional<EditingCommand> of(boolean rawKeyDown, int modifiers, int keyCode) {
        return of(MAC_OS, rawKeyDown, modifiers, keyCode);
    }

    /**
     * The command a key the page left alone runs on a platform.
     *
     * @param macOs whether the platform is macOS
     * @param rawKeyDown whether the event is the key's press
     * @param modifiers its event flags
     * @param keyCode its Windows key code
     * @return the command, or empty
     */
    static Optional<EditingCommand> of(boolean macOs, boolean rawKeyDown, int modifiers, int keyCode) {
        if (!macOs || !rawKeyDown) return Optional.empty();
        return switch (modifiers & MODIFIERS) {
            case COMMAND -> switch (keyCode) {
                case A -> Optional.of(SELECT_ALL);
                case X -> Optional.of(CUT);
                case Z -> Optional.of(UNDO);
                default -> Optional.empty();
            };
            case COMMAND | SHIFT -> keyCode == Z ? Optional.of(REDO) : Optional.empty();
            default -> Optional.empty();
        };
    }
}
