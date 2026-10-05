package dev.jasper.editor.ui;

import dev.jasper.editor.session.Question;
import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import static dev.jasper.sdk.contracts.ThemeMigrationCapture.save;
import static org.assertj.core.api.Assertions.assertThat;

class ThemeMigrationEvidenceTest {
    @Test void capturesFindReplaceInvalidPatternAndEditorQuestions() throws Exception {
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
            var line = new GoToLinePanel(1, 10, value -> { }, () -> { });
            save("editor", theme, "go-to-line", line, 500, 140);
            line.field().setText("20"); line.submit(); save("editor", theme, "go-to-line-invalid", line, 500, 140);
            var question = Forms.question(new Question("Unsaved changes", "example.txt has unsaved changes.", List.of("Save All", "Discard", "Cancel")), choice -> { });
            save("editor", theme, "unsaved-question", question, 540, 160);
        });
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
