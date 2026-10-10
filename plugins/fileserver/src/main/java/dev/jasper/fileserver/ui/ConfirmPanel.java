package dev.jasper.fileserver.ui;

import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.List;
import java.util.Optional;

/** A two-answer question in a host dialog. */
public final class ConfirmPanel implements AutoCloseable {
    private final UiView view;
    private boolean closed;

    public ConfirmPanel(UiKit kit, String text, String confirmLabel, String cancelLabel, Runnable onConfirm, Runnable onCancel) {
        view = kit.create(new ViewSpec(new Nodes.Form("fileserver.confirm", ControlState.READY, Nodes.FormKind.SHEET,
            "Confirm", Optional.empty(), List.of(new Nodes.Text("fileserver.confirm.message", ControlState.READY,
                text, Nodes.TextRole.BODY)), Optional.empty(), List.of(
                new Nodes.Button("fileserver.confirm.cancel", ControlState.READY, cancelLabel, "", Nodes.ButtonKind.SECONDARY, "cancel", ""),
                new Nodes.Button("fileserver.confirm.accept", ControlState.READY, confirmLabel, "", Nodes.ButtonKind.SECONDARY, "confirm", "")),
            "cancel")), event -> {
            if (closed || !(event instanceof UiEvent.Action action)) return;
            switch (action.id()) {
                case "fileserver.confirm.accept" -> onConfirm.run();
                case "fileserver.confirm.cancel" -> onCancel.run();
                default -> { }
            }
        });
    }

    public UiView view() { return view; }
    @Override public void close() { if (closed) return; closed = true; view.close(); }
}
