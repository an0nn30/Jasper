package dev.jasper.remote.ui.transfers;

import dev.jasper.remote.transfer.ConflictDecision;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Asked once before copying when selected items already exist at the destination, on a host-rendered kit form: Replace,
 * Skip existing or Cancel. No button is the form's primary, so Enter copies nothing (the former dialog had no default
 * button); Escape cancels. The message is literal text. UI thread only; {@link #close()} makes late callbacks inert.
 */
public final class ExistingItemsPanel implements AutoCloseable {
    public static final String FORM = "existing", TEXT = "existing.text", REPLACE = "existing.replace", SKIP = "existing.skip", CANCEL = "existing.cancel";
    private final String message;
    private final UiView view;
    private boolean closed;

    public ExistingItemsPanel(UiKit kit, String message, Consumer<ConflictDecision> choose, Runnable close) {
        this.message = message;
        var actions = List.of(
            new Nodes.Button(REPLACE, ControlState.READY, "Replace", "", Nodes.ButtonKind.SECONDARY, REPLACE, ""),
            new Nodes.Button(SKIP, ControlState.READY, "Skip existing", "", Nodes.ButtonKind.SECONDARY, SKIP, ""),
            new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""));
        var form = new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.FULL, "Items already exist", Optional.empty(),
            List.of(new Nodes.Text(TEXT, ControlState.READY, message, Nodes.TextRole.BODY)), Optional.empty(), actions, CANCEL);
        view = kit.create(new ViewSpec(form), event -> {
            if (closed || !(event instanceof UiEvent.Action action)) return;
            switch (action.id()) {
                case REPLACE -> choose.accept(ConflictDecision.REPLACE);
                case SKIP -> choose.accept(ConflictDecision.SKIP);
                case CANCEL -> close.run();
                default -> { }
            }
        });
    }

    public UiView view() { return view; }

    /** The question as shown. */
    public String message() { return message; }

    public static String message(List<String> existing, int selected, String destination) {
        if (selected == 1) return "\"" + existing.getFirst() + "\" already exists in " + destination + ".";
        return existing.size() + " of " + selected + " items already " + (existing.size() == 1 ? "exists" : "exist") + " in " + destination + ".";
    }

    @Override public void close() { if (closed) return; closed = true; view.close(); }
}
