package dev.jasper.remote.ui.transfers;

import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.Anchor;
import dev.jasper.sdk.ui.PanelHost;
import dev.jasper.sdk.ui.PanelSpec;
import dev.jasper.sdk.ui.UiView;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registers the classic Transfers BOTTOM panel (spec 14.4), toggled by {@link TransferUi#SHOW} so its stripe reads
 * "3: Transfers", and shows it on that action. Each window's panel shows that window's transfer rows. UI thread only.
 */
public final class TransfersPanels {
    private final PluginContext context;
    private final TransferUi transfers;
    private final Map<UUID, PanelHost> hosts = new HashMap<>();

    /** Registers the panel with {@code context}; its rows come from {@code transfers}. */
    public TransfersPanels(PluginContext context, TransferUi transfers) {
        this.context = context; this.transfers = transfers;
        context.panels().register(new PanelSpec(TransfersPanel.PANEL, "Transfers", "download", Anchor.BOTTOM).withToggleActionId(TransferUi.SHOW), this::create);
    }

    private UiView create(PanelHost host) {
        WindowHandle window = host.window();
        var strip = transfers.strip(window);
        var panel = new TransfersPanel(context.kit(), strip);
        hosts.put(window.id(), host);
        host.onClosed(() -> { panel.close(); transfers.release(window, strip); if (hosts.get(window.id()) == host) hosts.remove(window.id()); });
        return panel.view();
    }

    /** Shows the window's Transfers panel, opening it there when it is not open. */
    public void show(WindowHandle window) {
        PanelHost host = hosts.get(window.id());
        if (host == null) context.panels().toggle(TransfersPanel.PANEL, window); else host.show();
    }
}
