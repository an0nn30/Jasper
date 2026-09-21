package dev.jasper.terminal.session;

import dev.jasper.terminal.internal.TerminalAccess;
import dev.jasper.terminal.internal.emulation.JediTermEngine;

import java.util.function.Function;

/** Test-source-only entry to session composition; never present in the library jar. */
public final class TestSession {
    private TestSession() { }
    public static TerminalSession create(Function<JediTermEngine.Events, TerminalAccess> make) {
        return new TerminalSession(make);
    }
}
