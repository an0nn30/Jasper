package dev.jasper.editor.vim;

import dev.jasper.editor.vim.VimHost.Buffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/** {@code <Tab>} on the {@code :} command line where Vim's candidates differ from Jasper's or need files. */
class CompletionTest {
    @TempDir Path folder;

    private VimEditor editor() {
        Path file = folder.resolve("main.txt");
        var documents = new VimHost.Documents(
            editor -> List.of(new Buffer(1, file.toString(), Optional.of(file), true, false, false, false, 1),
                new Buffer(2, "/elsewhere/notes todo.md", Optional.of(Path.of("/elsewhere/notes todo.md")), false, false, false, false, 1),
                new Buffer(3, "Untitled 1", Optional.empty(), false, false, false, true, 1)),
            number -> CompletableFuture.completedFuture(true), (number, target) -> CompletableFuture.completedFuture(true),
            (number, discard) -> { }, number -> { }, number -> { }, (editor, target) -> { }, path -> Optional.empty());
        var area = new VimTextArea();
        area.setText("x\n");
        return new VimEditor(new Vim(new VimHost(r -> Optional.empty(), (r, t) -> { }, documents)), area, EditorDefaults.VIM);
    }

    private static List<String> matches(VimEditor editor, String line) { return Completion.complete(editor, line).matches(); }

    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    @Test void commandNamesAreTheCommandsJasperRuns() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor();
            assertThat(matches(editor, "tabn")).containsExactly("tabnew", "tabnext");
            assertThat(matches(editor, "  :3,$de")).containsExactly("delete", "delmarks");
            assertThat(Completion.complete(editor, "3,$de").start()).isEqualTo(3);
            assertThat(matches(editor, "ab")).as("Vim's :abbreviate is not Jasper's").isEmpty();
            assertThat(Completion.complete(editor, "s/a/b").completes()).as("nothing completes in :s").isFalse();
        });
    }

    @Test void optionsTheirNegationsAndTheirValues() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor();
            assertThat(matches(editor, "set sh")).containsExactly("shiftround", "shiftwidth", "showcmd", "showmode");
            assertThat(matches(editor, "setl nowr")).containsExactly("nowrap", "nowrapscan");
            assertThat(matches(editor, "set invhl")).containsExactly("invhlsearch");
            assertThat(matches(editor, "set a")).containsExactly("all", "autoindent");
            assertThat(matches(editor, "set ts=")).containsExactly("8");
            assertThat(Completion.complete(editor, "set ts=").start()).isEqualTo(7);
            assertThat(matches(editor, "set et=")).as("a boolean has no value").isEmpty();
            assertThat(matches(editor, "set sw=4 ts=")).containsExactly("8");
        });
    }

    @Test void pathsFromTheCurrentFilesFolder() throws Exception {
        Files.createDirectories(folder.resolve("src/deep"));
        Files.writeString(folder.resolve("src/one.java"), "");
        Files.writeString(folder.resolve("src/two words.txt"), "");
        Files.writeString(folder.resolve(".hidden"), "");
        Files.writeString(folder.resolve("main.txt"), "");
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor();
            assertThat(matches(editor, "e ")).containsExactly("main.txt", "src/");
            assertThat(matches(editor, "e .")).containsExactly(".hidden");
            assertThat(matches(editor, "w src/")).containsExactly("src/deep/", "src/one.java", "src/two\\ words.txt");
            assertThat(matches(editor, "r src/t")).containsExactly("src/two\\ words.txt");
            assertThat(matches(editor, "tabnew " + folder + "/m")).containsExactly(folder + "/main.txt");
            assertThat(matches(editor, "e nowhere/")).isEmpty();
        });
    }

    @Test void bufferNamesThatHoldTheText() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor();
            assertThat(matches(editor, "b todo")).containsExactly("/elsewhere/notes\\ todo.md");
            assertThat(matches(editor, "b ti")).containsExactly("Untitled\\ 1");
        });
    }

    @Test void tabCyclesAndShiftTabGoesBack() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var editor = editor();
            type(editor, ":tabn<Tab>");
            assertThat(editor.commandLineText()).contains(":tabnew");
            type(editor, "<Tab>");
            assertThat(editor.commandLineText()).contains(":tabnext");
            type(editor, "<Tab>");
            assertThat(editor.commandLineText()).as("back to what was typed").contains(":tabn");
            type(editor, "<S-Tab>");
            assertThat(editor.commandLineText()).contains(":tabnext");
            type(editor, "<C-u>zzz");
            int bells = editor.bells();
            type(editor, "<Tab>");
            assertThat(editor.bells()).as("nothing matches").isEqualTo(bells + 1);
            assertThat(editor.commandLineText()).contains(":zzz");
            type(editor, "<Esc>/a<Tab>");
            assertThat(editor.commandLineText()).as("a search types the tab").contains("/a\t");
        });
    }
}
