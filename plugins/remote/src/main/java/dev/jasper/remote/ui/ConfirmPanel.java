package dev.jasper.remote.ui;

import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.List;
import java.util.Optional;

/**
 * "Delete prod?" with one verb button and Cancel, on a host-rendered kit form. Neither button is the form's primary,
 * so Enter confirms nothing (the former dialog had no default button); Escape cancels. The question is literal text.
 * UI thread only; the surface that mounts {@link #view()} disposes it, and {@link #close()} makes late callbacks inert.
 */
public final class ConfirmPanel implements AutoCloseable {
    public static final String FORM = "confirm", TEXT = "confirm.text", CONFIRM = "confirm.verb", CANCEL = "confirm.cancel";
    private final UiView view;
    private final String text;
    private boolean closed;

    public ConfirmPanel(UiKit kit, String text, String verb, Runnable onConfirm, Runnable onCancel) {
        this.text = text;
        var actions = List.of(
            new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""),
            new Nodes.Button(CONFIRM, ControlState.READY, verb, "", Nodes.ButtonKind.SECONDARY, CONFIRM, ""));
        var form = new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.FULL, verb, Optional.empty(),
            List.of(new Nodes.Text(TEXT, ControlState.READY, text, Nodes.TextRole.BODY)), Optional.empty(), actions, CANCEL);
        view = kit.create(new ViewSpec(form), event -> {
            if (closed || !(event instanceof UiEvent.Action action)) return;
            if (action.id().equals(CONFIRM)) onConfirm.run();
            else if (action.id().equals(CANCEL)) onCancel.run();
        });
    }

    public UiView view() { return view; }

    /** The question as shown. */
    public String text() { return text; }

    @Override public void close() { if (closed) return; closed = true; view.close(); }
}
