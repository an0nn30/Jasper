package dev.jasper.remote.ui.transfers;

import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.Anchor;
import dev.jasper.sdk.ui.PanelHost;
import dev.jasper.sdk.ui.PanelSpec;
import dev.jasper.sdk.ui.UiView;

/**
 * Registers the classic Transfers BOTTOM panel (spec 14.4), toggled by {@link TransferUi#SHOW} so its stripe reads
 * "3: Transfers", and shows it on that action. The panel, in the main window, shows its transfer rows. UI thread only.
 */
public final class TransfersPanels {
    private final PluginContext context;
    private final TransferUi transfers;
    /** The panel's host while it is open: panels live in the main window (workspace tabs spec 14). */
    private PanelHost host;

    /** Registers the panel with {@code context}; its rows come from {@code transfers}. */
    public TransfersPanels(PluginContext context, TransferUi transfers) {
        this.context = context; this.transfers = transfers;
        context.panels().register(new PanelSpec(TransfersPanel.PANEL, "Transfers", "download", Anchor.BOTTOM).withToggleActionId(TransferUi.SHOW), this::create);
    }

    private UiView create(PanelHost host) {
        WindowHandle window = host.window();
        var strip = transfers.strip(window);
        var panel = new TransfersPanel(context.kit(), strip);
        this.host = host;
        host.onClosed(() -> { panel.close(); transfers.release(window, strip); if (this.host == host) this.host = null; });
        return panel.view();
    }

    /** Shows the Transfers panel in {@code window}'s dock, opening it there when it is not open. */
    public void show(WindowHandle window) {
        context.panels().show(TransfersPanel.PANEL, window);
    }
}
