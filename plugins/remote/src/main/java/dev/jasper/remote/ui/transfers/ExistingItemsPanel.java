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
 * Asked once before copying when selected items already exist at the destination, as the kit's compact question
 * dialog: a warning tile, the question and what exists, then Cancel, Skip existing and Replace. Replace is the visual
 * primary, but the form opts out of submission, so Enter copies nothing (the former dialog had no default button);
 * Escape cancels. The message is literal text. UI thread only; {@link #close()} makes late callbacks inert.
 */
public final class ExistingItemsPanel implements AutoCloseable {
    public static final String FORM = "existing", TEXT = "existing.text", REPLACE = "existing.replace", SKIP = "existing.skip", CANCEL = "existing.cancel";
    static final String HEADER = "existing.header";
    private final String message;
    private final UiView view;
    private boolean closed;

    public ExistingItemsPanel(UiKit kit, String message, Consumer<ConflictDecision> choose, Runnable close) {
        this.message = message;
        var actions = List.of(
            new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""),
            new Nodes.Button(SKIP, ControlState.READY, "Skip existing", "", Nodes.ButtonKind.SECONDARY, SKIP, ""),
            new Nodes.Button(REPLACE, ControlState.READY, "Replace", "", Nodes.ButtonKind.PRIMARY, REPLACE, ""));
        var hero = new Nodes.Stack(HEADER, ControlState.READY, Nodes.LayoutRole.PROMPT_HEADER, List.of(
            new Nodes.Icon(HEADER + ".icon", ControlState.READY, "warning", Nodes.Hue.AMBER, Nodes.IconSize.PROMPT, "Items already exist"),
            new Nodes.Text(HEADER + ".title", ControlState.READY, "Replace Existing Items?", Nodes.TextRole.PROMPT_TITLE),
            new Nodes.Text(TEXT, ControlState.READY, message, Nodes.TextRole.DESCRIPTION)));
        var form = new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.PROMPT_COMPACT, "Items already exist", Optional.of(hero),
            List.of(), Optional.empty(), actions, CANCEL, Nodes.FormSubmission.NONE);
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
