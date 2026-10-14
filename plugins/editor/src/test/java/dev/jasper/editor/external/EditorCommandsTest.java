package dev.jasper.editor.external;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class EditorCommandsTest {
    private static final Path ROOT = Path.of("").toAbsolutePath().getRoot();
    private static final Path FILE = ROOT.resolve("work").resolve("config.toml");
    private static final String MAC = "Mac OS X", LINUX = "Linux", WINDOWS = "Windows 11";

    /** The editor as detection would report it, its first program name in {@code <root>/bin}. */
    private static Detected detected(String id) {
        KnownEditor editor = KnownEditors.byId(id).orElseThrow();
        return new Detected(editor, ROOT.resolve("bin").resolve(editor.executables().getFirst()));
    }

    private static String bin(String name) { return ROOT.resolve("bin").resolve(name).toString(); }

    @Test void posixShellsGetSingleQuotesAndWindowsDoubleQuotes() {
        assertThat(EditorCommands.quote("/opt/homebrew/bin/nvim", MAC)).isEqualTo("'/opt/homebrew/bin/nvim'");
        assertThat(EditorCommands.quote("/Applications/Jasper Beta.app/Contents/Resources/bin/jasper", LINUX))
            .isEqualTo("'/Applications/Jasper Beta.app/Contents/Resources/bin/jasper'");
        assertThat(EditorCommands.quote("it's $(x) `y` \"z\"", MAC)).as("nothing expands inside single quotes")
            .isEqualTo("'it'\\''s $(x) `y` \"z\"'");
        assertThat(EditorCommands.quote("''", LINUX)).isEqualTo("''\\'''\\'''");
        assertThat(EditorCommands.quote("", LINUX)).isEqualTo("''");
        assertThat(EditorCommands.quote("C:\\Program Files\\Microsoft VS Code\\bin\\code.cmd", WINDOWS))
            .isEqualTo("\"C:\\Program Files\\Microsoft VS Code\\bin\\code.cmd\"");
    }

    @Test void editorAndVisualHoldTheQuotedProgramAndWhatMakesItWait() {
        Path client = ROOT.resolve("Applications").resolve("Jasper Beta.app").resolve("Contents").resolve("Resources").resolve("bin").resolve("jasper");
        assertThat(EditorCommands.variable(new Choice.Jasper(Optional.of(client)), MAC)).contains("'" + client + "' editor --wait");
        assertThat(EditorCommands.variable(new Choice.Jasper(Optional.empty()), MAC)).as("no jasper command").isEmpty();
        assertThat(EditorCommands.variable(new Choice.Installed(detected("nvim")), MAC)).contains("'" + bin("nvim") + "'");
        assertThat(EditorCommands.variable(new Choice.Installed(detected("emacs")), LINUX)).contains("'" + bin("emacs") + "' -nw");
        assertThat(EditorCommands.variable(new Choice.Installed(detected("code")), MAC)).contains("'" + bin("code") + "' --wait");
        assertThat(EditorCommands.variable(new Choice.Installed(detected("sublime")), LINUX)).contains("'" + bin("subl") + "' -w");
        assertThat(EditorCommands.variable(new Choice.Installed(detected("notepadpp")), WINDOWS))
            .contains("\"" + bin("notepad++") + "\" -multiInst -nosession");
        assertThat(EditorCommands.variable(new Choice.Installed(detected("notepad")), WINDOWS)).contains("\"" + bin("notepad") + "\"");
        assertThat(EditorCommands.variable(new Choice.Custom("subl -w", true), MAC)).as("as written").contains("subl -w");
        assertThat(EditorCommands.variable(new Choice.Custom("  ", false), MAC)).isEmpty();
        assertThat(EditorCommands.variable(new Choice.System(), MAC)).isEmpty();
    }

    @Test void openAtPutsTheLineWhereEachEditorReadsItAndNeverWaits() {
        String file = FILE.toString();
        var atLine = new LinkedHashMap<String, List<String>>();
        atLine.put("nvim", List.of(bin("nvim"), "+42", file));
        atLine.put("vim", List.of(bin("vim"), "+42", file));
        atLine.put("vi", List.of(bin("vi"), "+42", file));
        atLine.put("nano", List.of(bin("nano"), "+42", file));
        atLine.put("micro", List.of(bin("micro"), "+42", file));
        atLine.put("helix", List.of(bin("hx"), file + ":42"));
        atLine.put("emacs", List.of(bin("emacs"), "-nw", "+42", file));
        atLine.put("code", List.of(bin("code"), "-g", file + ":42"));
        atLine.put("cursor", List.of(bin("cursor"), "-g", file + ":42"));
        atLine.put("zed", List.of(bin("zed"), file + ":42"));
        atLine.put("sublime", List.of(bin("subl"), file + ":42"));
        atLine.put("bbedit", List.of(bin("bbedit"), "+42", file));
        atLine.put("textmate", List.of(bin("mate"), "-l", "42", file));
        atLine.put("idea", List.of(bin("idea"), "--line", "42", file));
        atLine.put("notepadpp", List.of(bin("notepad++"), "-n42", file));
        atLine.put("notepad", List.of(bin("notepad"), file));
        assertThat(atLine.keySet()).as("every known editor").containsExactlyElementsOf(KnownEditors.ALL.stream().map(KnownEditor::id).toList());
        atLine.forEach((id, expected) -> assertThat(EditorCommands.openAt(detected(id), FILE, OptionalInt.of(42))).as(id).isEqualTo(expected));
    }

    @Test void withoutALineEveryEditorGetsJustTheFile() {
        for (KnownEditor editor : KnownEditors.ALL) {
            var expected = new ArrayList<String>();
            expected.add(bin(editor.executables().getFirst()));
            expected.addAll(editor.extraArgs());
            expected.add(FILE.toString());
            assertThat(EditorCommands.openAt(detected(editor.id()), FILE, OptionalInt.empty())).as(editor.id()).isEqualTo(expected);
            assertThat(EditorCommands.openAt(detected(editor.id()), FILE, OptionalInt.of(0))).as(editor.id() + " at line 0").isEqualTo(expected);
            assertThat(EditorCommands.openAt(detected(editor.id()), FILE, OptionalInt.of(-3))).as(editor.id() + " at line -3").isEqualTo(expected);
        }
        assertThat(EditorCommands.openAt(detected("cursor"), FILE, OptionalInt.empty())).as("no -g without a line").doesNotContain("-g");
    }

    /**
     * Controller Ruling 12: on POSIX the line first adds detection's extra folders to Jasper's PATH, each quoted, so a
     * Dock-started Jasper's custom command finds what the user's shell finds; Windows runs the line as written.
     */
    @Test void aCustomCommandGetsTheQuotedFileThroughTheShell() {
        Path file = ROOT.resolve("notes").resolve("it's \u00fc.txt");
        Path home = ROOT.resolve("Users").resolve("Dana Smith's");
        String folders = "'" + Path.of("/opt/homebrew/bin") + "':'" + Path.of("/usr/local/bin") + "':'"
            + home.resolve(".local").resolve("bin").toString().replace("'", "'\\''") + "'";
        assertThat(EditorCommands.custom("subl -w", file, home, MAC)).containsExactly("/bin/sh", "-c",
            "export PATH=\"$PATH\":" + folders + "; subl -w '" + file.toString().replace("'", "'\\''") + "'");
        assertThat(EditorCommands.custom("subl -w", file, home, LINUX).get(2)).as("every POSIX system").startsWith("export PATH=\"$PATH\":" + folders + "; ");
        assertThat(EditorCommands.custom("notepad++ -multiInst", file, home, WINDOWS))
            .containsExactly("cmd.exe", "/d", "/s", "/c", "\"notepad++ -multiInst \"" + file + "\"\"");
    }

    /** What a failed custom command's notice names: the program, never its arguments or the file. */
    @Test void aCustomCommandsProgramIsItsFirstWordWithoutItsQuotes() {
        assertThat(EditorCommands.program("subl -w")).isEqualTo("subl");
        assertThat(EditorCommands.program("  code\t--new-window --wait ")).isEqualTo("code");
        assertThat(EditorCommands.program("'/Applications/Sublime Text.app/Contents/SharedSupport/bin/subl' -w"))
            .isEqualTo("/Applications/Sublime Text.app/Contents/SharedSupport/bin/subl");
        assertThat(EditorCommands.program("\"C:\\Program Files\\Notepad++\\notepad++.exe\" -multiInst"))
            .isEqualTo("C:\\Program Files\\Notepad++\\notepad++.exe");
        assertThat(EditorCommands.program("'unterminated")).isEqualTo("unterminated");
        assertThat(EditorCommands.program("emacsclient")).isEqualTo("emacsclient");
        assertThat(EditorCommands.program("   ")).isEmpty();
    }

    /** Controller Ruling 8: a line that starts with a quote would lose its first and last quote under {@code cmd /c} alone. */
    @Test void aWindowsCustomCommandStartingWithAQuotedProgramIsWrappedForCmd() {
        Path file = ROOT.resolve("Users").resolve("Dana Smith").resolve("notes.txt");
        String command = "\"C:\\Program Files\\Notepad++\\notepad++.exe\" -multiInst";
        assertThat(EditorCommands.custom(command, file, ROOT.resolve("Users").resolve("Dana Smith"), WINDOWS)).containsExactly("cmd.exe", "/d", "/s", "/c",
            "\"\"C:\\Program Files\\Notepad++\\notepad++.exe\" -multiInst \"" + file + "\"\"");
    }

    @Test void everyKnownEditorsValueIsItsQuotedProgramThenWhatItAlwaysGetsThenWhatMakesItWait() {
        for (KnownEditor editor : KnownEditors.ALL) {
            for (String os : List.of(MAC, WINDOWS)) {
                String quote = os.equals(WINDOWS) ? "\"" : "'";
                var words = new ArrayList<String>();
                words.add(quote + bin(editor.executables().getFirst()) + quote);
                words.addAll(editor.extraArgs());
                if (editor.kind() == KnownEditor.Kind.APP) words.addAll(editor.waitArgs());
                assertThat(EditorCommands.variable(new Choice.Installed(detected(editor.id())), os)).as(editor.id() + " on " + os)
                    .contains(String.join(" ", words));
            }
        }
    }

    /** Review Focus 2: git prefers VISUAL, so either one in Jasper's environment keeps both, unless the setting is off. */
    @Test void editorOrVisualInJaspersEnvironmentKeepsBothUnlessTheSettingIsOff() {
        assertThat(EditorCommands.keepsEnvironment(Map.of("EDITOR", "vim"), true)).isTrue();
        assertThat(EditorCommands.keepsEnvironment(Map.of("VISUAL", "code --wait"), true)).as("VISUAL alone").isTrue();
        assertThat(EditorCommands.keepsEnvironment(Map.of("EDITOR", " ", "VISUAL", ""), true)).as("blank values").isFalse();
        assertThat(EditorCommands.keepsEnvironment(Map.of("PATH", "/usr/bin"), true)).as("neither").isFalse();
        assertThat(EditorCommands.keepsEnvironment(Map.of("EDITOR", "vim"), false)).as("the setting off").isFalse();
    }

    /**
     * Review Focus 1: a program under a folder whose name holds a space, a quote and a non-ASCII letter, and a file
     * named like that, survive {@code /bin/sh -c "$EDITOR file"} and git's own {@code sh -c '$EDITOR "$@"'}. Runs only
     * {@code /bin/sh} and this test's script, which records its arguments.
     */
    @Test @DisabledOnOs(value = OS.WINDOWS, disabledReason = "EDITOR is read by a POSIX shell")
    void quotedValuesRunThatProgramWithThatFileThroughTheShell(@TempDir Path temp) throws Exception {
        Path bin = Files.createDirectories(temp.resolve("Jasper Beta's \u00fc.app").resolve("bin"));
        Path program = Files.writeString(bin.resolve("record"), "#!/bin/sh\nprintf '%s\\n' \"$@\" > \"$RECORD\"\n");
        Files.setPosixFilePermissions(program, PosixFilePermissions.fromString("rwx------"));
        Path file = Files.writeString(temp.resolve("it's \u00fc.txt"), "text\n");
        String os = System.getProperty("os.name");
        Detected nvim = new Detected(KnownEditors.byId("nvim").orElseThrow(), program);
        Detected code = new Detected(KnownEditors.byId("code").orElseThrow(), program);
        assertThat(shell(temp, EditorCommands.variable(new Choice.Jasper(Optional.of(program)), os).orElseThrow(), file))
            .containsExactly("editor", "--wait", file.toString());
        assertThat(shell(temp, EditorCommands.variable(new Choice.Installed(nvim), os).orElseThrow(), file)).containsExactly(file.toString());
        assertThat(shell(temp, EditorCommands.variable(new Choice.Installed(code), os).orElseThrow(), file)).containsExactly("--wait", file.toString());
        String custom = EditorCommands.quote(program.toString(), os) + " --new-window";
        assertThat(shell(temp, EditorCommands.variable(new Choice.Custom(custom, false), os).orElseThrow(), file))
            .containsExactly("--new-window", file.toString());
        assertThat(run(temp, EditorCommands.custom(custom, file, temp, os))).as("Jasper's own custom launch line")
            .containsExactly("--new-window", file.toString());
    }

    /**
     * Controller Ruling 12: a Dock-started Jasper's PATH lacks ~/.local/bin, Homebrew's folder and /usr/local/bin; a
     * custom command naming a program that lives only in ~/.local/bin (here under a home whose name holds a space and a
     * quote) still runs. Runs only {@code /bin/sh} and this test's script, which records its arguments.
     */
    @Test @DisabledOnOs(value = OS.WINDOWS, disabledReason = "custom commands run by cmd on Windows")
    void aCustomCommandFindsAProgramInLocalBinThatJaspersPathLacks(@TempDir Path temp) throws Exception {
        Path home = Files.createDirectories(temp.resolve("Dana Smith's home"));
        Path bin = Files.createDirectories(home.resolve(".local").resolve("bin"));
        Path program = Files.writeString(bin.resolve("record-local"), "#!/bin/sh\nprintf '%s\\n' \"$@\" > \"$RECORD\"\n");
        Files.setPosixFilePermissions(program, PosixFilePermissions.fromString("rwx------"));
        Path file = Files.writeString(temp.resolve("it's \u00fc.txt"), "text\n");
        assertThat(run(temp, EditorCommands.custom("record-local --wait", file, home, System.getProperty("os.name")), "/usr/bin:/bin"))
            .containsExactly("--wait", file.toString());
    }

    /** What the program got from {@code sh -c "<value> <quoted file>"}, checked against git's form. */
    private static List<String> shell(Path temp, String value, Path file) throws Exception {
        List<String> quoted = run(temp, List.of("/bin/sh", "-c", value + " " + EditorCommands.quote(file.toString(), System.getProperty("os.name"))));
        assertThat(run(temp, List.of("/bin/sh", "-c", value + " \"$@\"", value, file.toString()))).as("as git runs it").isEqualTo(quoted);
        return quoted;
    }

    private static List<String> run(Path temp, List<String> command) throws Exception { return run(temp, command, null); }

    /** As above, with {@code PATH} set to {@code path} when it is not null. */
    private static List<String> run(Path temp, List<String> command, String path) throws Exception {
        Path record = temp.resolve("arguments.txt");
        Files.deleteIfExists(record);
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("RECORD", record.toString());
        if (path != null) builder.environment().put("PATH", path);
        Process process = builder.start();
        process.getOutputStream().close();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        assertThat(finished).as("the shell finished").isTrue();
        assertThat(process.exitValue()).as(String.join(" ", command)).isZero();
        return Files.readAllLines(record, StandardCharsets.UTF_8);
    }
}
