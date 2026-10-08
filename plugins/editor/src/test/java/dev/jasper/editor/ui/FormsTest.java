package dev.jasper.editor.ui;

import dev.jasper.editor.session.Question;
import dev.jasper.editor.testsupport.Kits.RecordingKit;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static dev.jasper.editor.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The Editor's question and Saving dialogs as kit descriptors (Plan 2 Task 9a). Baseline f16ae32f: the question's
 * default button was its first choice, or the last (Cancel) for a destructive question, and Enter pressed it from
 * anywhere; closing the window answered the last choice. Saving had no default button and closing it cancelled.
 */
class FormsTest {
    private static final Question UNSAVED = new Question("Unsaved changes", "a.txt has unsaved changes.", List.of("Save All", "Discard", "Cancel"));
    private static final Question CHANGED = new Question("File changed", "a.txt changed on disk since you opened it.",
        List.of("Overwrite", "Reload", "Cancel"), true);

    @Test void aQuestionHasOneButtonPerChoiceAndReportsTheOneClicked() throws Exception {
        edt(() -> {
            List<String> answers = new ArrayList<>();
            var question = new Forms.QuestionView(new FakeKit("dev.jasper.editor"), UNSAVED, answers::add);
            assertThat(form(question.view()).actions()).extracting(Nodes.Button::label).containsExactly("Save All", "Discard", "Cancel");
            click(question.view(), form(question.view()).actions().get(1).id());
            assertThat(answers).containsExactly("Discard");
        });
    }

    @Test void enterPicksTheFirstChoiceUnlessTheQuestionIsDestructive() throws Exception {
        assertThat(Forms.defaultChoice(UNSAVED)).isEqualTo("Save All");
        assertThat(Forms.defaultChoice(CHANGED)).isEqualTo("Cancel");
        edt(() -> {
            var kit = new FakeKit("dev.jasper.editor");
            List<String> answers = new ArrayList<>();
            var plain = new Forms.QuestionView(kit, UNSAVED, answers::add);
            assertThat(primaries(plain.view())).as("the default choice is the form's only primary, which Enter presses")
                .extracting(Nodes.Button::label).containsExactly("Save All");
            press(plain.view(), primaries(plain.view()).getFirst().action());
            var destructive = new Forms.QuestionView(kit, CHANGED, answers::add);
            assertThat(primaries(destructive.view())).as("a destructive question's Enter picks Cancel, never Overwrite")
                .extracting(Nodes.Button::label).containsExactly("Cancel");
            press(destructive.view(), primaries(destructive.view()).getFirst().action());
            assertThat(answers).containsExactly("Save All", "Cancel");
        });
    }

    @Test void escapeAnswersWhatClosingTheWindowMeans() throws Exception {
        edt(() -> {
            List<String> answers = new ArrayList<>();
            var kit = new FakeKit("dev.jasper.editor");
            escape(new Forms.QuestionView(kit, UNSAVED, answers::add).view());
            escape(new Forms.QuestionView(kit, CHANGED, answers::add).view());
            assertThat(answers).as("Escape picks the safe, dismissing choice, as closing the window did").containsExactly("Cancel", "Cancel");
        });
    }

    @Test void theMessageIsLiteralWrappingBodyTextAndTheSheetIsTitledByTheQuestion() throws Exception {
        edt(() -> {
            var question = new Forms.QuestionView(new FakeKit("dev.jasper.editor"),
                new Question("Replace", "<html><b>x</b> already exists.", List.of("Replace", "Cancel")), answer -> { });
            var form = form(question.view());
            assertThat(form.kind()).isEqualTo(Nodes.FormKind.SHEET);
            assertThat(form.title()).isEqualTo("Replace");
            var message = node(question.view(), Forms.MESSAGE, Nodes.Text.class);
            assertThat(message.text()).isEqualTo("<html><b>x</b> already exists.");
            assertThat(message.role()).isEqualTo(Nodes.TextRole.BODY);
        });
    }

    @Test void repeatedChoiceLabelsKeepDistinctButtonsAndASingleChoiceIsItsOwnDefault() throws Exception {
        edt(() -> {
            List<String> answers = new ArrayList<>();
            var kit = new FakeKit("dev.jasper.editor");
            var twice = new Forms.QuestionView(kit, new Question("Odd", "Twice?", List.of("OK", "OK")), answers::add);
            assertThat(form(twice.view()).actions()).extracting(Nodes.Button::id).doesNotHaveDuplicates().hasSize(2);
            var single = new Forms.QuestionView(kit, new Question("Done", "Saved.", List.of("OK")), answers::add);
            assertThat(primaries(single.view())).extracting(Nodes.Button::label).containsExactly("OK");
            escape(single.view());
            assertThat(answers).containsExactly("OK");
        });
    }

    @Test void aClosedQuestionNeverTouchesItsViewAgain() throws Exception {
        edt(() -> {
            List<String> answers = new ArrayList<>();
            var kit = new RecordingKit("dev.jasper.editor");
            var question = new Forms.QuestionView(kit, UNSAVED, answers::add);
            var view = question.view();
            question.close();
            question.close();
            assertThat(closed(view)).isTrue();
            assertThat(kit.lateCalls).isEmpty();
            assertThat(answers).isEmpty();
        });
    }

    @Test void savingShowsTheMessageOverAnIndeterminateProgressWithCancelAndNoDefault() throws Exception {
        edt(() -> {
            int[] cancelled = {0};
            var saving = new Forms.ProgressView(new FakeKit("dev.jasper.editor"), "Saving 3 files…", () -> cancelled[0]++);
            var form = form(saving.view());
            assertThat(form.title()).isEqualTo("Saving");
            assertThat(node(saving.view(), Forms.SAVING_MESSAGE, Nodes.Text.class).text()).isEqualTo("Saving 3 files…");
            assertThat(node(saving.view(), Forms.SAVING_PROGRESS, Nodes.Progress.class).fraction()).as("indeterminate").isEqualTo(-1);
            assertThat(primaries(saving.view())).as("nothing is Enter's default, as at baseline").isEmpty();
            assertThat(form.actions()).extracting(Nodes.Button::label, Nodes.Button::kind).containsExactly(tuple("Cancel", Nodes.ButtonKind.SECONDARY));
            click(saving.view(), Forms.SAVING_CANCEL);
            escape(saving.view());
            assertThat(cancelled[0]).as("Cancel and Escape both cancel").isEqualTo(2);
            saving.close();
            assertThat(closed(saving.view())).isTrue();
        });
    }
}
