package dev.jasper.remote.ui.transfers;

import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The classic Transfers bottom panel (Enterprise presentation spec 14.4): the window's transfer rows, which the default
 * look shows in the SFTP sidebar, or "No Transfers". One per window; UI thread only.
 */
public final class TransfersPanel implements AutoCloseable {
    /** The panel's id, its kit root, its header and its empty state. */
    public static final String PANEL = "dev.jasper.remote.transfers.panel", ROOT = "transfers.panel", HEADER = "transfers.panel.header",
        EMPTY = "transfers.panel.empty";
    private final TransferStrip strip;
    private final UiView view;
    private boolean closed;

    /** A panel over {@code strip}, which it renders again on every change and hands its events to. */
    public TransfersPanel(UiKit kit, TransferStrip strip) {
        this.strip = Objects.requireNonNull(strip);
        view = kit.create(new ViewSpec(node()), event -> { if (!closed) strip.handle(event); });
        strip.onChanged(this::render);
    }

    /** The panel's owned kit view. */
    public UiView view() { return view; }

    private void render() { if (!closed) view.update(new ViewSpec(node())); }

    private Nodes.Panel node() {
        var header = new Nodes.Header(HEADER, ControlState.READY, "Transfers", OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
        Nodes.Node body = strip.list().orElseGet(() -> new Nodes.Empty(EMPTY, ControlState.READY,
            new Nodes.Icon(EMPTY + ".icon", ControlState.READY, "download", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "Transfers"), "No Transfers",
            "Uploads and downloads from SFTP show here.", List.of()));
        return new Nodes.Panel(ROOT, ControlState.READY, header, Optional.empty(), body, Optional.empty());
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        strip.onChanged(() -> { });
        view.close();
    }
}
