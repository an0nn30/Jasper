package dev.jasper.editor.ui;

import dev.jasper.editor.session.Question;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.editor.testsupport.Kits;
import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import static dev.jasper.sdk.contracts.ThemeMigrationCapture.save;
import static org.assertj.core.api.Assertions.assertThat;

class ThemeMigrationEvidenceTest {
    @Test void capturesFindReplaceInvalidPatternAndTheDocument() throws Exception {
        ThemeMigrationCapture.themes(theme -> {
            var area = new RSyntaxTextArea("example one\nexample two\n");
            var find = new FindBar(() -> Optional.of(area));
            find.open(false); find.findField().setText("example");
            assertThat(find.status()).isEqualTo("2 matches");
            save("editor", theme, "find", find, 1100, 70);
            find.open(true); save("editor", theme, "replace", find, 1100, 110);
            find.regex().doClick(); find.findField().setText("(");
            assertThat(find.status()).isEqualTo("Invalid pattern");
            save("editor", theme, "find-invalid-pattern", find, 1100, 110);
            find.close();
            var environment = new dev.jasper.editor.session.SessionEnvironment(Runnable::run, Runnable::run, Question::dismissed, () -> 1 << 20);
            var tab = new EditorTab(dev.jasper.editor.session.DocumentSession.untitled("Fixture document", environment), closed -> { }, false);
            try {
                tab.apply(new EditorSettings("Monospaced", 13, 4, false, 10, false), new EditorTheme(), dev.jasper.sdk.ui.EditorPalette.current(), dev.jasper.sdk.ui.SyntaxPalette.current());
                tab.area().setText("// Fixture document\nint value = 42;\n");
                tab.area().setSyntaxEditingStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JAVA);
                measure(tab.area());
                save("editor", theme, "document", tab, 780, 440);
                tab.vim(Optional.of(new dev.jasper.editor.vim.Vim(dev.jasper.editor.vim.VimHost.detached())));
                save("editor", theme, "document-vim", tab, 780, 440);
            } finally { tab.dispose(); }
        });
    }

    /**
     * Plan 2 Task 9a: the frozen go-to-line, go-to-line-invalid and unsaved-question captures, plus the destructive
     * question, the Language picker and the Saving dialog, as kit descriptor scenarios on the testkit fake with synthetic
     * text, written under build/theme-migration-evidence/editor-descriptors. Pixels of the real host rendering come from
     * the app-side EditorRenderEvidenceTest; the frozen before images are untouched.
     */
    @Test void describesGoToLineTheQuestionsTheLanguagePickerAndSaving() throws Exception {
        Languages.register();
        Kits.edt(() -> {
            var kit = new FakeKit("dev.jasper.editor");
            var line = new GoToLinePanel(kit, 1, 10, value -> { }, () -> { });
            describe("go-to-line", line.view());
            Kits.type(line.view(), GoToLinePanel.LINE, "20");
            line.submit();
            assertThat(Kits.node(line.view(), GoToLinePanel.LINE, Nodes.Field.class).state().error()).isEqualTo("Enter a line from 1 to 10");
            describe("go-to-line-invalid", line.view());
            var question = new Forms.QuestionView(kit, new Question("Unsaved changes", "example.txt has unsaved changes.", List.of("Save All", "Discard", "Cancel")), choice -> { });
            assertThat(Kits.primaries(question.view())).extracting(Nodes.Button::label).containsExactly("Save All");
            describe("unsaved-question", question.view());
            var changed = new Forms.QuestionView(kit, new Question("File changed", "example.txt changed on disk since you opened it.",
                List.of("Overwrite", "Reload", "Cancel"), true), choice -> { });
            assertThat(Kits.primaries(changed.view())).extracting(Nodes.Button::label).containsExactly("Cancel");
            describe("changed-question", changed.view());
            var language = new LanguagePicker(kit, SyntaxConstants.SYNTAX_STYLE_PYTHON, style -> { }, () -> { });
            describe("language", language.view());
            Kits.type(language.view(), LanguagePicker.FILTER, "tom");
            assertThat(language.visibleNames()).containsExactly("TOML");
            describe("language-filtered", language.view());
            var saving = new Forms.ProgressView(kit, "Saving 3 files…", () -> { });
            describe("saving", saving.view());
            try (var saved = Files.list(DESCRIPTORS)) { assertThat(saved.count()).isGreaterThanOrEqualTo(7); }
        });
    }

    static final Path DESCRIPTORS = Path.of("build/theme-migration-evidence/editor-descriptors");

    static void describe(String state, UiView view) throws Exception {
        Files.createDirectories(DESCRIPTORS);
        Files.writeString(DESCRIPTORS.resolve(state + ".txt"), view.spec().toString() + "\n");
    }
    // Existing EditorTabTest uses this pinned 3.6.0 headless-only first-font initialization too.
    private static void measure(RSyntaxTextArea area) {
        var image = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            var refresh = RSyntaxTextArea.class.getDeclaredMethod("refreshFontMetrics", java.awt.Graphics2D.class);
            refresh.setAccessible(true); refresh.invoke(area, graphics);
            var flag = RSyntaxTextArea.class.getDeclaredField("metricsNeverRefreshed");
            flag.setAccessible(true); flag.setBoolean(area, false);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        finally { graphics.dispose(); }
    }
}
