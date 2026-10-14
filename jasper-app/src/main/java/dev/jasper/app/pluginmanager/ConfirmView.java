package dev.jasper.app.pluginmanager;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import java.util.List;
import java.util.Optional;

/**
 * A question with one consequence and two answers, for a removal: a kit sheet whose primary answers yes (Enter) and
 * whose Cancel answers no (Escape); or a {@linkplain #notice notice} with OK alone. Kit text is literal, so
 * plugin-supplied text is never markup. EDT only; it closes with its dialog. Settings' theme gears use it too.
 */
public final class ConfirmView implements AutoCloseable {
    static final String OK = "ok", CANCEL = "cancel";
    private final KitView view;
    private final String question, consequence, okLabel;
    private final boolean cancellable;

    public ConfirmView(KitContext kit, String question, String consequence, String okLabel, Runnable onOk, Runnable onCancel) {
        this(kit, question, consequence, okLabel, true, onOk, onCancel);
    }

    /** A notice the user acknowledges: {@code heading}, {@code text} and one answer, OK (Enter or Escape), which runs {@code onOk}. */
    public static ConfirmView notice(KitContext kit, String heading, String text, Runnable onOk) {
        return new ConfirmView(kit, heading, text, "OK", false, onOk, onOk);
    }

    private ConfirmView(KitContext kit, String question, String consequence, String okLabel, boolean cancellable, Runnable onOk, Runnable onCancel) {
        this.question = question;
        this.consequence = consequence;
        this.okLabel = okLabel;
        this.cancellable = cancellable;
        view = new KitView(kit, spec(false), event -> {
            if (!(event instanceof UiEvent.Action action)) return;
            if (action.id().equals(OK)) onOk.run();
            else if (action.id().equals(CANCEL)) onCancel.run();
        });
    }

    private ViewSpec spec(boolean busy) {
        var ok = new Nodes.Button(OK, new ControlState(true, true, busy, ""), okLabel, "", Nodes.ButtonKind.PRIMARY, OK, "");
        var answers = cancellable ? List.of(new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""), ok)
            : List.of(ok);
        return new ViewSpec(new Nodes.Form("confirm", ControlState.READY, Nodes.FormKind.SHEET, question, Optional.empty(),
            List.of(new Nodes.Text("confirm-text", ControlState.READY, consequence, Nodes.TextRole.BODY)), Optional.empty(),
            answers, cancellable ? CANCEL : OK));
    }

    /** Shows the answer as busy (it cannot be given again) while the operation runs. */
    void busy(boolean value) { if (!view.isClosed()) view.update(spec(value)); }

    public KitView view() { return view; }

    /** The question and its consequence. */
    String text() { return question + "\n\n" + consequence; }

    @Override public void close() { view.close(); }
}
