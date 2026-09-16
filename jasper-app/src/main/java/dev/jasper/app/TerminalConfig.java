package dev.jasper.app;

import dev.jasper.terminal.BellMode;
import dev.jasper.terminal.CursorStyle;
import dev.jasper.terminal.OptionAsMeta;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validated session defaults and terminal behavior saved in the configuration. */
record TerminalConfig(Shell shell, Map<String, String> env, int scrollback, OptionAsMeta optionAsMeta,
                      CursorStyle cursorShape, boolean cursorBlink, float dimInactivePanes,
                      boolean copyOnSelect, BellMode bell, ShellExitBehavior onExit,
                      ShellIntegrationMode shellIntegration) {
    TerminalConfig(Shell shell, Map<String, String> env, int scrollback, OptionAsMeta optionAsMeta,
                   CursorStyle cursorShape, boolean cursorBlink, float dimInactivePanes,
                   boolean copyOnSelect, BellMode bell) {
        this(shell, env, scrollback, optionAsMeta, cursorShape, cursorBlink, dimInactivePanes,
            copyOnSelect, bell, ShellExitBehavior.KEEP_OPEN);
    }

    TerminalConfig(Shell shell, Map<String, String> env, int scrollback, OptionAsMeta optionAsMeta,
                   CursorStyle cursorShape, boolean cursorBlink, float dimInactivePanes,
                   boolean copyOnSelect, BellMode bell, ShellExitBehavior onExit) {
        this(shell, env, scrollback, optionAsMeta, cursorShape, cursorBlink, dimInactivePanes,
            copyOnSelect, bell, onExit, ShellIntegrationMode.AUTO);
    }

    record Shell(String program, List<String> args) {
        Shell {
            Objects.requireNonNull(program, "program");
            if ((!program.isEmpty() && program.isBlank()) || program.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Shell program must be empty or nonblank and contain no NUL.");
            }
            args = List.copyOf(args);
            if (args.stream().anyMatch(arg -> arg.indexOf('\0') >= 0)) {
                throw new IllegalArgumentException("Shell arguments must contain no NUL.");
            }
        }
    }

    TerminalConfig {
        Objects.requireNonNull(shell, "shell");
        env = Map.copyOf(env);
        env.forEach((name, value) -> {
            if (!validEnvName(name) || value.indexOf('\0') >= 0 || reservedEnvName(name)) {
                throw new IllegalArgumentException("Invalid or reserved environment entry.");
            }
        });
        if (scrollback < 0 || scrollback > 1_000_000) {
            throw new IllegalArgumentException("Scrollback must be from 0–1000000.");
        }
        Objects.requireNonNull(optionAsMeta, "optionAsMeta");
        Objects.requireNonNull(cursorShape, "cursorShape");
        if (!Float.isFinite(dimInactivePanes) || dimInactivePanes < 0 || dimInactivePanes > 1) {
            throw new IllegalArgumentException("Inactive pane dimming must be a finite number from 0–1.");
        }
        Objects.requireNonNull(bell, "bell");
        Objects.requireNonNull(onExit, "onExit");
        Objects.requireNonNull(shellIntegration, "shellIntegration");
    }

    static boolean validEnvName(String name) {
        return name.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    static boolean reservedEnvName(String name) {
        return name.equals("TERM") || name.equals("COLORTERM");
    }

    static TerminalConfig defaults() {
        return new TerminalConfig(new Shell("", List.of()), Map.of(), 10_000, OptionAsMeta.LEFT,
            CursorStyle.BLOCK, true, .3f, false, BellMode.VISUAL);
    }
}
