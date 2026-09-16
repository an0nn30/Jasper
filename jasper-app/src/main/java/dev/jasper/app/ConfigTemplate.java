package dev.jasper.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** The editable, commented defaults for settings supported by this application. */
final class ConfigTemplate {
    private ConfigTemplate() { }

    static String text(boolean macOs) {
        var text = new StringBuilder("""
            # Jasper settings
            # Uncomment a setting to change its saved default. Live settings reload across windows.
            # View menu choices are temporary overrides until that saved setting changes.
            # Session and grid defaults affect new panes/windows as described below.
            # Reload Config forces a fresh read.
            # Invalid syntax or types keep the last valid settings; diagnostics explain errors.

            [window]
            # Tab height in logical pixels, 28-72; updates all open windows live.
            # tab_height = 38
            # Toolbar: "icons_and_labels", "icons", or "hidden"; updates live.
            # toolbar = "icons_and_labels"
            # Show the status bar; updates live.
            # status_bar = true

            # Initial terminal grid for new windows only; existing windows keep their size.
            # columns = 150
            # lines = 45
            # Columns: 5-500; lines: 2-200. The window is clamped to the usable screen.

            [buddy]
            # Show the pixel-art Jasper floating above your windows while a terminal is open; updates live.
            # View > Show Jasper and a right-click on Jasper toggle him for this session only.
            # enabled = true

            [history]
            # Search shell history from the palette (Cmd+R on macOS, Ctrl+Shift+R elsewhere); updates live.
            # Reads zsh, bash, fish, nushell and PowerShell history files; Jasper writes no history of its own.
            # enabled = true
            # Commands ranked below more substantial ones in the History palette. Each entry is a
            # single word matched against a command's first word, so "cd .." is trivial but
            # "cd path && build" is not. Still listed and searchable, just never above real work.
            # An empty list turns this off; setting the key replaces the default list entirely.
            # trivial_commands = ["exit", "clear", "ls", "ll", "la", "cd", "pwd", "c", "q", "logout"]

            [palette]
            # Rows the command palette shows in every scope, 1-20; updates live. Cmd/Ctrl+1-5 still number the first five.
            # max_results = 5

            [font]
            # Font family and ordered fallbacks update live. Missing fonts use JBR/system fallback.
            # family = "JetBrains Mono"
            # fallback = ["Symbols Nerd Font Mono", "Apple Color Emoji"]
            # Fallback names must be nonblank; an empty fallback list is allowed.
            # ligatures = true
            # Line height multiplier, 1.0-3.0; updates live.
            # line_height = 1.0
            # Font size in points, 6-72; changes update all panes live.
            # Reset Font Size restores this saved default.
            # Temporary size/appearance choices survive reloads until their respective saved value changes.
            # size = 16.0

            [terminal]
            # Scrollback lines, 0-1000000; new panes only. Existing sessions keep their capacity.
            # scrollback = 10000
            # Option key sending Meta: "left", "right", "both", or "none"; updates live.
            # option_as_meta = "left"
            # Inactive pane dimming, 0.0-1.0; updates live.
            # dim_inactive_panes = 0.3
            # Copy a completed selection to the clipboard; updates live.
            # copy_on_select = false
            # Bell: "visual", "sound", or "none"; updates live.
            # bell = "visual"
            # Shell process exit: "keep_open", "close_on_success" (code 0), or "close" (any exit).
            # Only the exited pane closes; its tab/window closes when empty. Live for future exits.
            # "keep_open" retains output from the stopped shell; reload never closes retained output.
            # on_exit = "keep_open"
            # Shell integration: "auto" loads Jasper's zsh/bash/fish script after your own startup files (new panes only),
            # "manual" only exports JASPER_SHELL_INTEGRATION so you can source it yourself, "off" does neither.
            # shell_integration = "auto"

            [terminal.shell]
            # New panes only. Empty program resolves the default shell and appends configured args.
            # An explicit program gets only configured args, with no implicit login flag.
            # program = ""
            # Arguments are exact strings, without shell parsing; preserve each argument separately.
            # args = []
            # Program and arguments must not contain NUL; a nonempty program cannot be blank.

            [terminal.cursor]
            # Live fallback when the running program has not set its own cursor style.
            # Shape: "block", "beam", or "underline".
            # shape = "block"
            # blink = true

            [terminal.env]
            # New panes only: add string values to the inherited environment.
            # Names use letters, digits and underscore, starting with a letter or underscore.
            # Values must not contain NUL. Desktop launches remove inherited TERM_PROGRAM,
            # TERM_PROGRAM_VERSION, TERM_SESSION_ID, TMUX, TMUX_PANE, and ITERM_* variables,
            # then apply this overlay.
            # Explicit configured values are intentional overrides. On macOS, LANG defaults to
            # en_US.UTF-8 only when absent or blank; LC_* values are preserved. TERM and COLORTERM
            # remain reserved and are always forced to xterm-256color and truecolor.
            # This table does not change which default login shell is selected.

            [ui.theme]
            # Theme variant: "dark" or "light"; switches chrome and terminal colors live across windows.
            # variant = "dark"

            [keybindings]
            # Shortcuts update live. Use "none" to disable an action; shortcuts must be unique.
            # Modifiers: cmd, ctrl, alt, shift. Brace aliases { and } include shift.
            """);
        text.append(macOs
            ? "# cmd is the Command key on macOS.\n"
            : "# cmd means Ctrl+Shift; some defaults add Alt to keep actions distinct.\n# Command Palette uses plain Ctrl+K; Clear Scrollback uses Ctrl+Shift+K; Search Shell History uses Ctrl+Shift+R; Snippets uses Ctrl+Shift+J.\n");
        for (ActionId action : ActionId.values()) {
            text.append("# ").append(action.label()).append('\n');
            text.append("# ").append(action.id()).append(" = \"")
                .append(KeyBindings.effectiveDefaultBinding(action, macOs)).append("\"\n");
        }
        return text.toString();
    }

    static void ensureExists(Path file, boolean macOs) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try {
            Files.writeString(file, text(macOs), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException ignored) {
            // An existing file belongs to the user, including edits from concurrent Settings actions.
        }
    }
}
