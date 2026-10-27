package dev.jasper.remote.ui.transfers;

import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.Anchor;
import dev.jasper.sdk.ui.PanelHost;
import dev.jasper.sdk.ui.PanelSpec;
import dev.jasper.sdk.ui.UiView;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Registers the classic Transfers BOTTOM panel (spec 14.4), toggled by {@link TransferUi#SHOW} so its stripe reads
 * "3: Transfers", and shows it on that action. Each window's dock builds its own panel, which shows that window's
 * transfer rows. UI thread only.
 */
public final class TransfersPanels {
    private final PluginContext context;
    private final TransferUi transfers;
    /** One Transfers panel in each window's dock that built it, by window id (full windows spec 9). */
    private record Instance(PanelHost host, TransfersPanel panel) {}
    private final Map<UUID, Instance> panels = new LinkedHashMap<>();

    /** Registers the panel with {@code context}; its rows come from {@code transfers}. */
    public TransfersPanels(PluginContext context, TransferUi transfers) {
        this.context = context; this.transfers = transfers;
        context.panels().register(new PanelSpec(TransfersPanel.PANEL, "Transfers", "download", Anchor.BOTTOM).withToggleActionId(TransferUi.SHOW), this::create);
    }

    private UiView create(PanelHost host) {
        WindowHandle window = host.window();
        var strip = transfers.strip(window);
        var panel = new TransfersPanel(context.kit(), strip);
        var instance = new Instance(host, panel);
        panels.put(window.id(), instance);
        host.onClosed(() -> { panel.close(); transfers.release(window, strip); panels.remove(window.id(), instance); });
        return panel.view();
    }

    /** The host of {@code window}'s Transfers panel, while it is open. */
    Optional<PanelHost> panelHost(UUID window) { return Optional.ofNullable(panels.get(window)).map(Instance::host); }

    /** Shows the Transfers panel in {@code window}'s dock, opening it there when it is not open. */
    public void show(WindowHandle window) {
        context.panels().show(TransfersPanel.PANEL, window);
    }
}
