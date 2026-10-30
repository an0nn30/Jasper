package dev.jasper.browser.page;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which keys a page left alone run an editing command of its focused frame: on macOS Cmd+A, Cmd+X, Cmd+Z and
 * Cmd+Shift+Z, which Chromium leaves to the browser's Edit menu there; elsewhere none, as Chromium runs them itself.
 * The modifiers are Chromium's event flags and the keys its Windows key codes.
 */
class EditingCommandTest {
    // Chromium's event flags (cef_event_flags_t).
    private static final int CAPS_LOCK = 1, SHIFT = 1 << 1, CONTROL = 1 << 2, ALT = 1 << 3, COMMAND = 1 << 7, NUM_LOCK = 1 << 8;
    private static final int A = 0x41, C = 0x43, V = 0x56, X = 0x58, Z = 0x5A;

    @Test void onMacOsCommandWithAXAndZRunSelectAllCutAndUndoAndWithShiftZRedo() {
        assertThat(EditingCommand.of(true, true, COMMAND, A)).contains(EditingCommand.SELECT_ALL);
        assertThat(EditingCommand.of(true, true, COMMAND, X)).contains(EditingCommand.CUT);
        assertThat(EditingCommand.of(true, true, COMMAND, Z)).contains(EditingCommand.UNDO);
        assertThat(EditingCommand.of(true, true, COMMAND | SHIFT, Z)).contains(EditingCommand.REDO);
    }

    @Test void lockKeysDoNotChangeTheCommand() {
        assertThat(EditingCommand.of(true, true, COMMAND | CAPS_LOCK, A)).contains(EditingCommand.SELECT_ALL);
        assertThat(EditingCommand.of(true, true, COMMAND | SHIFT | NUM_LOCK, Z)).contains(EditingCommand.REDO);
    }

    @Test void onMacOsAnyOtherKeyOrModifierRunsNone() {
        assertThat(EditingCommand.of(true, true, COMMAND | SHIFT, A)).isEmpty();
        assertThat(EditingCommand.of(true, true, COMMAND | SHIFT, X)).isEmpty();
        assertThat(EditingCommand.of(true, true, COMMAND | ALT, Z)).isEmpty();
        assertThat(EditingCommand.of(true, true, COMMAND | CONTROL, A)).isEmpty();
        assertThat(EditingCommand.of(true, true, CONTROL, A)).as("Ctrl+A moves to the line's start").isEmpty();
        assertThat(EditingCommand.of(true, true, 0, Z)).isEmpty();
        assertThat(EditingCommand.of(true, true, COMMAND, C)).as("Copy is Jasper's").isEmpty();
        assertThat(EditingCommand.of(true, true, COMMAND, V)).as("Paste is Jasper's").isEmpty();
    }

    @Test void onlyTheKeysPressRunsOne() {
        assertThat(EditingCommand.of(true, false, COMMAND, A)).as("its character or release").isEmpty();
    }

    @Test void onLinuxAndWindowsNoKeyRunsOne() {
        for (int key : new int[] { A, X, Z })
            for (int modifiers : new int[] { COMMAND, COMMAND | SHIFT, CONTROL, CONTROL | SHIFT })
                assertThat(EditingCommand.of(false, true, modifiers, key)).isEqualTo(Optional.empty());
    }
}
