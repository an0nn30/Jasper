package dev.jasper.editor.ui;

import dev.jasper.editor.session.Question;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The Editor's question and Saving dialogs as owned kit views from the plugin's {@link UiKit} (Plan 2 Task 9a).
 * Each view is UI-thread only; the surface that mounts it disposes
 * it, and closing its owner makes late events inert.
 */
final class Forms {
    static final String QUESTION = "question", MESSAGE = "question.message", CHOICE = "question.choice.", ANSWER = "answer.";
    static final String SAVING = "saving", SAVING_MESSAGE = "saving.message", SAVING_PROGRESS = "saving.progress",
        SAVING_CANCEL = "saving.cancel", CANCEL = "cancel";

    private Forms() { }

    /** @return the choice whose button Enter presses: the last (Cancel) for a destructive question, else the first */
    static String defaultChoice(Question question) {
        return question.destructive() ? question.dismissed() : question.choices().getFirst();
    }

    /**
     * A question's sheet: its literal message as wrapping body text and one button per choice. The default choice
     * ({@link #defaultChoice}) is the only PRIMARY and, as on every canonical surface, the right-most button; the other
     * choices keep their order before it (so "Save, Don't Save, Cancel" reads "Don't Save, Cancel, Save" and a
     * discarding choice never sits beside the primary when there are three). Each button keeps its choice's index in its
     * id and action. Enter presses the primary from anywhere, as the former root default button did; Escape answers
     * {@link Question#dismissed()}, what closing the window means (for a destructive question that is the safe Cancel).
     * Space presses the focused button. The sheet has no input, so the kit form keeps the initial focus itself (no
     * button), as before; Tab then follows the visual order.
     */
    static final class QuestionView implements AutoCloseable {
        private final UiView view;
        private boolean closed;

        QuestionView(UiKit kit, Question question, Consumer<String> answer) {
            List<String> choices = question.choices();
            int preferred = question.destructive() ? choices.size() - 1 : 0;
            var buttons = new ArrayList<Nodes.Button>();
            for (int i = 0; i < choices.size(); i++)
                if (i != preferred) buttons.add(new Nodes.Button(CHOICE + i, ControlState.READY, choices.get(i), "", Nodes.ButtonKind.SECONDARY, ANSWER + i, ""));
            buttons.add(new Nodes.Button(CHOICE + preferred, ControlState.READY, choices.get(preferred), "", Nodes.ButtonKind.PRIMARY, ANSWER + preferred, ""));
            var form = new Nodes.Form(QUESTION, ControlState.READY, Nodes.FormKind.SHEET, question.title(), Optional.empty(),
                List.of(new Nodes.Text(MESSAGE, ControlState.READY, question.message(), Nodes.TextRole.BODY)), Optional.empty(), buttons,
                ANSWER + (choices.size() - 1));
            view = kit.create(new ViewSpec(form), event -> {
                if (closed || !(event instanceof UiEvent.Action action) || !action.id().startsWith(ANSWER)) return;
                int index;
                try { index = Integer.parseInt(action.id().substring(ANSWER.length())); }
                catch (NumberFormatException unknown) { return; }
                if (index >= 0 && index < choices.size()) answer.accept(choices.get(index));
            });
        }

        UiView view() { return view; }

        @Override public void close() { if (closed) return; closed = true; view.close(); }
    }

    /**
     * "Saving 3 files…" over the kit's indeterminate progress, with Cancel. Nothing is Enter's default (the former dialog
     * had no default button); Escape cancels, as closing the window does.
     */
    static final class ProgressView implements AutoCloseable {
        private final UiView view;
        private boolean closed;

        ProgressView(UiKit kit, String message, Runnable cancel) {
            var form = new Nodes.Form(SAVING, ControlState.READY, Nodes.FormKind.FULL, "Saving", Optional.empty(), List.of(
                    new Nodes.Text(SAVING_MESSAGE, ControlState.READY, message, Nodes.TextRole.BODY),
                    new Nodes.Progress(SAVING_PROGRESS, ControlState.READY, -1, "Saving")),
                Optional.empty(), List.of(new Nodes.Button(SAVING_CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, "")),
                CANCEL);
            view = kit.create(new ViewSpec(form), event -> {
                if (!closed && event instanceof UiEvent.Action action && action.id().equals(CANCEL)) cancel.run();
            });
        }

        UiView view() { return view; }

        @Override public void close() { if (closed) return; closed = true; view.close(); }
    }

}
