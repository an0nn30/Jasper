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
 * Compact connection progress, with retry after failure, on a host-rendered kit form; contains no network work. The
 * motion is the kit's indeterminate progress, which follows the motion setting and stops with the view. Escape runs
 * Cancel (Close after a failure); nothing is Enter's default. Updates after {@link #close()} (a stale completion) are
 * ignored. UI thread only.
 */
public final class ConnectionPanel implements AutoCloseable {
    public static final String FORM = "connection", TITLE = "connection.title", ADDRESS = "connection.address", MESSAGE = "connection.message",
        PROGRESS = "connection.progress", RETRY = "connection.retry", CANCEL = "connection.cancel";
    private final UiView view;
    private final String name, address;
    private String message = "Preparing connection…";
    private boolean working = true, failed, closed;

    public ConnectionPanel(UiKit kit, String name, String address, Runnable retryAction, Runnable cancelAction) {
        this.name = name; this.address = address;
        view = kit.create(new ViewSpec(form()), event -> {
            if (closed || !(event instanceof UiEvent.Action action)) return;
            if (action.id().equals(RETRY)) retryAction.run();
            else if (action.id().equals(CANCEL)) cancelAction.run();
        });
    }

    public UiView view() { return view; }

    /** The status line as shown. */
    public String message() { return message; }

    public void working() { failed = false; working = true; message = "Preparing connection…"; render(); }
    public void status(String text) { message = text; render(); }
    public void failed(String text) { message = text; failed = true; working = false; render(); }
    public void finished() { working = false; render(); }

    private void render() { if (!closed) view.update(new ViewSpec(form())); }

    private Nodes.Form form() {
        var sections = List.<Nodes.Node>of(
            new Nodes.Text(TITLE, ControlState.READY, name, Nodes.TextRole.DETAIL_TITLE),
            new Nodes.Text(ADDRESS, ControlState.READY, address, Nodes.TextRole.DESCRIPTION),
            new Nodes.Text(MESSAGE, ControlState.READY, message, Nodes.TextRole.BODY),
            new Nodes.Progress(PROGRESS, new ControlState(working, true, false, ""), -1, "Connecting"));
        var actions = List.of(
            new Nodes.Button(RETRY, new ControlState(failed, true, false, ""), "Retry", "", Nodes.ButtonKind.SECONDARY, RETRY, ""),
            new Nodes.Button(CANCEL, ControlState.READY, failed ? "Close" : "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""));
        return new Nodes.Form(FORM, ControlState.READY, Nodes.FormKind.FULL, "Connecting to " + name, Optional.empty(), sections, Optional.empty(), actions, CANCEL);
    }

    @Override public void close() { if (closed) return; closed = true; working = false; view.close(); }
}
