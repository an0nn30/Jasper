package dev.jasper.editor.ui;

import dev.jasper.editor.session.Question;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.editor.testsupport.Kits;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import static org.assertj.core.api.Assertions.assertThat;

class ThemeMigrationEvidenceTest {
    @Test void describesFindReplaceInvalidPatternAndTheDocument() throws Exception {
        Kits.edt(() -> {
            var kit = new FakeKit("dev.jasper.editor");
            var environment = new dev.jasper.editor.session.SessionEnvironment(Runnable::run, Runnable::run, Question::dismissed, () -> 1 << 20);
            var tab = new EditorTab(kit, dev.jasper.editor.session.DocumentSession.untitled("Fixture document", environment), closed -> { }, false);
            var find = new FindBar(() -> Optional.of(new FindTarget.Source(tab.area())), tab::render, id -> { tab.view().focus(id); tab.view().selectAll(id); });
            tab.find(find);
            try {
                tab.area().setText("example one\nexample two\n");
                describe("document", tab.view());
                find.open(false); Kits.type(tab.view(), FindBar.FIND, "example");
                assertThat(find.status()).isEqualTo("2 matches"); describe("find", tab.view());
                find.open(true); describe("replace", tab.view());
                Kits.press(tab.view(), FindBar.REGEX); Kits.type(tab.view(), FindBar.FIND, "(");
                assertThat(find.status()).isEqualTo("Invalid pattern"); describe("find-invalid-pattern", tab.view());
                find.close();
                tab.vim(Optional.of(new dev.jasper.editor.vim.Vim(dev.jasper.editor.vim.VimHost.detached())));
                describe("document-vim-mode", tab.view());
                var vim=tab.vimEditor().orElseThrow();
                for(var key:dev.jasper.editor.vim.KeyInput.parse("/example<Left>"))vim.feed(key);
                assertThat(Kits.node(tab.view(),"vim.status",Nodes.Text.class).caret()).hasValue(7);
                describe("document-vim-command",tab.view());
                for(var key:dev.jasper.editor.vim.KeyInput.parse("<Esc>:%p<CR>"))vim.feed(key);
                describe("document-vim-output",tab.view());
                for(var key:dev.jasper.editor.vim.KeyInput.parse("<CR>\"qp"))vim.feed(key);
                assertThat(Kits.node(tab.view(),"vim.status",Nodes.Text.class).severity()).contains(Nodes.Severity.INFO);
                describe("document-vim-bell",tab.view());
            } finally { tab.dispose(); kit.close(); }
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
}
