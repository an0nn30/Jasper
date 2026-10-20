package dev.jasper.editor.ui;

import dev.jasper.editor.session.Question;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.Windows;
import java.util.Objects;
import java.util.function.Function;

/**
 * Asks a question in a modal dialog over the Editor window, on a kit sheet ({@link Forms.QuestionView}); the
 * application's modal show() returns after it closes. Closing the dialog without a choice answers the last choice.
 */
public final class DialogAsker implements Function<Question, String> {
    private final Windows windows;
    private final UiKit kit;
    private final WindowOwner owner;

    /**
     * @param windows the plugin's windows
     * @param kit the plugin's kit
     * @param owner the Editor window
     */
    public DialogAsker(Windows windows, UiKit kit, WindowOwner owner) {
        this.windows = Objects.requireNonNull(windows);
        this.kit = Objects.requireNonNull(kit);
        this.owner = Objects.requireNonNull(owner);
    }

    @Override public String apply(Question question) {
        PluginDialog dialog = windows.dialog(new DialogSpec(question.title(), owner, true));
        String[] answer = {question.dismissed()};
        var form = new Forms.QuestionView(kit, question, choice -> { answer[0] = choice; dialog.close(); });
        dialog.onClosed(form::close);
        dialog.setContent(form.view());
        dialog.show();
        return answer[0];
    }
}
