package dev.moray.app;

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
            # Moray settings
            # Uncomment a setting to change its saved default. Changes reload live across windows.
            # View menu choices are temporary overrides until that saved setting changes.
            # New windows and panes inherit saved defaults. Reload Config forces a fresh read.
            # Invalid syntax or types keep the last valid settings; diagnostics explain errors.

            [window]
            # Tab height in logical pixels, 28-72; updates all open windows live.
            # tab_height = 38
            # Toolbar: "icons_and_labels", "icons", or "hidden"; updates live.
            # toolbar = "icons_and_labels"
            # Show the status bar; updates live.
            # status_bar = true

            [font]
            # Font size in points, 6-72; changes update all panes live.
            # Reset Font Size restores this saved default.
            # size = 16.0

            [colors]
            # Built-in theme: "moray-dark" or "moray-light"; updates all windows live.
            # theme = "moray-dark"

            [keybindings]
            # Shortcuts update live. Use "none" to disable an action; shortcuts must be unique.
            # Modifiers: cmd, ctrl, alt, shift. Brace aliases { and } include shift.
            """);
        text.append(macOs
            ? "# cmd is the Command key on macOS.\n"
            : "# cmd means Ctrl+Shift; some defaults add Alt to keep actions distinct.\n");
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
