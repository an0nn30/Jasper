package dev.moray.terminal;

import java.awt.event.KeyEvent;

/** Test-only access to the same package-private terminal key path used by terminal tests. */
public final class TerminalKeyTestSupport {
    private TerminalKeyTestSupport() {}
    public static void handleKey(TerminalView view, KeyEvent event) { view.handleKey(event); }
}
