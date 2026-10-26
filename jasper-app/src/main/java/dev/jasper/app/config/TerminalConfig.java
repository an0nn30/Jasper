package dev.jasper.app.config;

import dev.jasper.terminal.config.BellMode;
import dev.jasper.terminal.config.CursorStyle;
import dev.jasper.terminal.config.OptionAsMeta;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validated session defaults and terminal behavior saved in the configuration. */
public record TerminalConfig(Shell shell, Map<String, String> env, int scrollback, OptionAsMeta optionAsMeta,
                      CursorStyle cursorShape, boolean cursorBlink, float dimInactivePanes,
                      boolean copyOnSelect, BellMode bell, ShellExitBehavior onExit,
                      ShellIntegrationMode shellIntegration, String tabName) {
    public TerminalConfig(Shell shell, Map<String, String> env, int scrollback, OptionAsMeta optionAsMeta,
                   CursorStyle cursorShape, boolean cursorBlink, float dimInactivePanes,
                   boolean copyOnSelect, BellMode bell) {
        this(shell, env, scrollback, optionAsMeta, cursorShape, cursorBlink, dimInactivePanes,
            copyOnSelect, bell, ShellExitBehavior.KEEP_OPEN);
    }

    public TerminalConfig(Shell shell, Map<String, String> env, int scrollback, OptionAsMeta optionAsMeta,
                   CursorStyle cursorShape, boolean cursorBlink, float dimInactivePanes,
                   boolean copyOnSelect, BellMode bell, ShellExitBehavior onExit) {
        this(shell, env, scrollback, optionAsMeta, cursorShape, cursorBlink, dimInactivePanes,
            copyOnSelect, bell, onExit, ShellIntegrationMode.AUTO);
    }

    public TerminalConfig(Shell shell, Map<String, String> env, int scrollback, OptionAsMeta optionAsMeta,
                   CursorStyle cursorShape, boolean cursorBlink, float dimInactivePanes,
                   boolean copyOnSelect, BellMode bell, ShellExitBehavior onExit, ShellIntegrationMode shellIntegration) {
        this(shell, env, scrollback, optionAsMeta, cursorShape, cursorBlink, dimInactivePanes, copyOnSelect, bell, onExit,
            shellIntegration, TabNames.DEFAULT);
    }

    /** This configuration with another {@code terminal.tab_name} pattern. */
    public TerminalConfig withTabName(String value) {
        return new TerminalConfig(shell, env, scrollback, optionAsMeta, cursorShape, cursorBlink, dimInactivePanes,
            copyOnSelect, bell, onExit, shellIntegration, value);
    }

    /**
     * The shell new panes start.
     *
     * @param program the Custom profile's executable; empty means the platform's default shell
     * @param args the Custom profile's exact arguments
     * @param profile the chosen profile id; empty means Custom when program or args are set, else the platform default
     */
    public record Shell(String program, List<String> args, String profile) {
        public Shell {
            Objects.requireNonNull(program, "program");
            if ((!program.isEmpty() && program.isBlank()) || program.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Shell program must be empty or nonblank and contain no NUL.");
            }
            args = List.copyOf(args);
            if (args.stream().anyMatch(arg -> arg.indexOf('\0') >= 0)) {
                throw new IllegalArgumentException("Shell arguments must contain no NUL.");
            }
            Objects.requireNonNull(profile, "profile");
            if (profile.indexOf('\0') >= 0) throw new IllegalArgumentException("Shell profile must contain no NUL.");
        }

        /** No profile chosen: the old program and args settings decide. */
        public Shell(String program, List<String> args) { this(program, args, ""); }

        /** Whether new panes run {@code program} and {@code args}. */
        public boolean custom() { return custom(program, args, profile); }

        /**
         * Whether these settings mean the Custom profile: it is chosen, or no profile is chosen and program or args
         * are set, which is how Jasper ran them before profiles existed.
         */
        public static boolean custom(String program, List<String> args, String profile) {
            return profile.equals("custom") || profile.isEmpty() && (!program.isEmpty() || !args.isEmpty());
        }
    }

    public TerminalConfig {
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
        Objects.requireNonNull(tabName, "tabName");
        if (!TabNames.valid(tabName)) throw new IllegalArgumentException("The tab name must be a valid pattern.");
    }

    static boolean validEnvName(String name) {
        return name.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    static boolean reservedEnvName(String name) {
        return name.equals("TERM") || name.equals("COLORTERM");
    }

    public static TerminalConfig defaults() {
        return new TerminalConfig(new Shell("", List.of()), Map.of(), 10_000, OptionAsMeta.LEFT,
            CursorStyle.BLOCK, true, .3f, false, BellMode.VISUAL);
    }
}
