package dev.jasper.app.config;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingKind;
import dev.jasper.app.settings.TomlLiterals;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** The editable, commented defaults for settings supported by this application. */
public final class ConfigTemplate {
    private ConfigTemplate() { }

    private static final String HEADER = """
        # Jasper settings
        # Uncomment a setting to change its saved default. Live settings reload across windows.
        # View menu choices (toolbar, status bar, terminal scheme) are saved here.
        # Session and grid defaults affect new panes/windows as described below.
        # Reload Config forces a fresh read.
        # Invalid syntax or types keep the last valid settings; diagnostics explain errors.
        version = 2
        """;

    /** The commented defaults for every setting in {@link CoreSettings#SCHEMA}, then the platform's shortcuts. */
    public static String text(boolean macOs) {
        var text = new StringBuilder(HEADER);
        List<String> table = null;
        for (Setting<?> setting : CoreSettings.SCHEMA.settings()) {
            boolean map = setting.kind() instanceof SettingKind.StringMap;
            List<String> next = map ? setting.key() : setting.table();
            if (!next.equals(table)) {
                table = next;
                text.append("\n[").append(TomlLiterals.path(table)).append("]\n");
                CoreSettings.SCHEMA.tableNote(table).ifPresent(note -> comment(text, note));
            }
            comment(text, setting.description());
            if (!map) text.append("# ").append(TomlLiterals.key(setting.leaf())).append(" = ").append(literal(setting)).append('\n');
        }
        text.append("""

            [keybindings]
            # Shortcuts update live. Use "none" to disable an action; shortcuts must be unique.
            # Modifiers: cmd, ctrl, alt, shift. Brace aliases { and } include shift.
            """);
        text.append(macOs
            ? "# cmd is the Command key on macOS.\n"
            : "# cmd means Ctrl+Shift outside macOS. The defaults below name ctrl, alt and shift literally.\n");
        for (ActionId action : ActionId.values()) {
            text.append("# ").append(action.label()).append('\n');
            text.append("# ").append(action.id()).append(" = \"")
                .append(KeyBindings.effectiveDefaultBinding(action, macOs)).append("\"\n");
        }
        return text.toString();
    }

    private static void comment(StringBuilder text, String lines) {
        for (String line : lines.split("\n", -1)) text.append("# ").append(line).append('\n');
    }

    private static <T> String literal(Setting<T> setting) {
        return setting.kind().literal(setting.example().orElse(setting.defaultValue()));
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
