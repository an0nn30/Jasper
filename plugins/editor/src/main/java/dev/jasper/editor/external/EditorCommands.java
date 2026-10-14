package dev.jasper.editor.external;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The {@code EDITOR} values and command lines of the default editor. Pure. POSIX shells get single quotes (each
 * {@code '} written {@code '\''}); Windows gets double quotes, which a Windows path never holds.
 */
public final class EditorCommands {
    private EditorCommands() { }

    /**
     * @param text a path or an argument
     * @param osName the {@code os.name} property
     * @return the text quoted for {@code sh}, or for {@code cmd.exe} on Windows
     */
    public static String quote(String text, String osName) {
        return windows(osName) ? "\"" + text + "\"" : "'" + text.replace("'", "'\\''") + "'";
    }

    /**
     * What {@code EDITOR} and {@code VISUAL} hold for a choice. Programs read the value as a shell command line and add
     * the file, so it is the quoted program, its fixed arguments and the arguments that make it wait.
     *
     * @param choice what the settings choose
     * @param osName the {@code os.name} property
     * @return the value; empty for the system's app, a blank custom command, or the Jasper Editor without a
     *     {@code jasper} command
     */
    public static Optional<String> variable(Choice choice, String osName) {
        return switch (choice) {
            case Choice.Jasper jasper -> jasper.client().map(client -> quote(client.toString(), osName) + " editor --wait");
            case Choice.Installed installed -> {
                var words = new ArrayList<String>();
                words.add(quote(installed.editor().executable().toString(), osName));
                words.addAll(installed.editor().editor().extraArgs());
                words.addAll(installed.editor().editor().waitArgs());
                yield Optional.of(String.join(" ", words));
            }
            case Choice.Custom custom -> custom.command().isBlank() ? Optional.empty() : Optional.of(custom.command());
            case Choice.System system -> Optional.empty();
        };
    }

    /**
     * The command that opens a file in an installed editor at a line. It never waits: Jasper runs terminal editors in a
     * tab and starts app editors on their own.
     *
     * @param editor the editor and its program
     * @param file the file, absolute
     * @param line 1-based; empty, or below 1, opens the file without a line
     * @return the program and its arguments
     */
    public static List<String> openAt(Detected editor, Path file, OptionalInt line) {
        var command = new ArrayList<String>();
        command.add(editor.executable().toString());
        command.addAll(editor.editor().extraArgs());
        String path = file.toString();
        if (line.isEmpty() || line.getAsInt() < 1) {
            command.add(path);
            return List.copyOf(command);
        }
        String at = Integer.toString(line.getAsInt());
        switch (editor.editor().lineForm()) {
            case PLUS_LINE_BEFORE -> command.addAll(List.of("+" + at, path));
            case FILE_COLON_LINE -> command.add(path + ":" + at);
            case DASH_G_FILE_COLON_LINE -> command.addAll(List.of("-g", path + ":" + at));
            case DASH_L_LINE_BEFORE -> command.addAll(List.of("-l", at, path));
            case LINE_OPTION_BEFORE -> command.addAll(List.of("--line", at, path));
            case DASH_N_LINE_BEFORE -> command.addAll(List.of("-n" + at, path));
            case NONE -> command.add(path);
        }
        return List.copyOf(command);
    }

    /**
     * A custom command with the quoted file added, run by {@code /bin/sh -c}, or {@code cmd.exe /d /s /c} on Windows, so
     * the command line may carry its own arguments and quoting. On Windows the whole line is one argument wrapped in one
     * more pair of quotes: without {@code /s}, {@code cmd /c} strips the first and last quote of a line that starts with
     * a quote, which splits a quoted program path at its space; with it, cmd strips exactly the outer pair and keeps the
     * rest literal. {@code /d} skips the AutoRun commands.
     *
     * @param command the command line as written
     * @param file the file, absolute
     * @param osName the {@code os.name} property
     * @return the shell and its arguments
     */
    public static List<String> custom(String command, Path file, String osName) {
        String line = command + " " + quote(file.toString(), osName);
        return windows(osName) ? List.of("cmd.exe", "/d", "/s", "/c", "\"" + line + "\"") : List.of("/bin/sh", "-c", line);
    }

    /**
     * Whether Jasper's shells keep the {@code EDITOR} and {@code VISUAL} Jasper itself was started with. git prefers
     * {@code VISUAL}, so setting one beside the user's other would take over: either one keeps both.
     *
     * @param jasperEnvironment Jasper's own environment
     * @param keepSetting {@code keep_environment_editor}
     * @return true when the setting is on and either variable is set and not blank
     */
    public static boolean keepsEnvironment(Map<String, String> jasperEnvironment, boolean keepSetting) {
        return keepSetting && (set(jasperEnvironment.get("EDITOR")) || set(jasperEnvironment.get("VISUAL")));
    }

    private static boolean set(String value) { return value != null && !value.isBlank(); }

    private static boolean windows(String osName) { return osName.toLowerCase(Locale.ROOT).startsWith("windows"); }
}
