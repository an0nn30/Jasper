package dev.jasper.app.testsupport;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.TerminalConfig;
import java.util.List;
import java.util.Map;

/** Shared headless test fixture; never shipped. */
public final class ConfigTestSupport {
    public static ConfigSnapshot snapshot(String program, List<String> args, Map<String, String> env, int scrollback,
                                   int columns, int lines) {
        var d = ConfigSnapshot.defaults(); var t = d.terminal();
        return new ConfigSnapshot(d.tabHeight(), d.toolbar(), d.statusBar(), d.font(), d.variant(), d.keybindings(),
            columns, lines, new TerminalConfig(new TerminalConfig.Shell(program, args), env, scrollback,
            t.optionAsMeta(), t.cursorShape(), t.cursorBlink(), t.dimInactivePanes(), t.copyOnSelect(), t.bell()));
    }
}
