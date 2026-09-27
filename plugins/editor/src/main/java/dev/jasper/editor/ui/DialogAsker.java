package dev.jasper.editor.ui;

import dev.jasper.editor.session.Question;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.Windows;
import java.util.Objects;
import java.util.function.Function;

/** Asks a question in a modal dialog over the Editor window; the application's modal show() returns after it closes. */
public final class DialogAsker implements Function<Question, String> {
    private final Windows windows;
    private final WindowOwner owner;

    /**
     * @param windows the plugin's windows
     * @param owner the Editor window
     */
    public DialogAsker(Windows windows, WindowOwner owner) {
        this.windows = Objects.requireNonNull(windows);
        this.owner = Objects.requireNonNull(owner);
    }

    @Override public String apply(Question question) {
        PluginDialog dialog = windows.dialog(new DialogSpec(question.title(), owner, true));
        String[] answer = {question.dismissed()};
        dialog.setContent(Forms.question(question, choice -> { answer[0] = choice; dialog.close(); }));
        dialog.show();
        return answer[0];
    }
}
