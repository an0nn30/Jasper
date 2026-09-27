package dev.jasper.editor.ui;

import dev.jasper.editor.session.Question;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FormsTest {
    static List<JButton> buttons(Container root) {
        List<JButton> out = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button) out.add(button);
            if (child instanceof Container container) out.addAll(buttons(container));
        }
        return out;
    }

    @Test void aQuestionHasOneButtonPerChoiceAndReportsTheOneClicked() {
        List<String> answers = new ArrayList<>();
        var content = Forms.question(new Question("Unsaved changes", "a.txt has unsaved changes.", List.of("Save All", "Discard", "Cancel")), answers::add);
        List<JButton> buttons = buttons(content);
        assertThat(buttons).extracting(JButton::getText).containsExactly("Save All", "Discard", "Cancel");
        buttons.get(1).doClick();
        assertThat(answers).containsExactly("Discard");
    }

    @Test void enterPicksTheFirstChoiceUnlessTheQuestionIsDestructive() {
        assertThat(Forms.defaultChoice(new Question("Unsaved changes", "a.txt has unsaved changes.", List.of("Save All", "Discard", "Cancel"))))
            .isEqualTo("Save All");
        assertThat(Forms.defaultChoice(new Question("File changed", "a.txt changed on disk since you opened it.",
            List.of("Overwrite", "Reload", "Cancel"), true))).isEqualTo("Cancel");
    }
}
