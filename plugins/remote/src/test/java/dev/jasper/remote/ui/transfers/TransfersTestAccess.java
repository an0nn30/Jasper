package dev.jasper.remote.ui.transfers;

import dev.jasper.sdk.ui.PanelHost;
import java.util.Optional;
import java.util.UUID;

/** Owner-package access to the classic Transfers panels for plugin tests in other packages. UI thread only. */
public final class TransfersTestAccess {
    private TransfersTestAccess() { }

    /** The host of {@code window}'s Transfers panel while it is open. */
    public static Optional<PanelHost> panelHost(TransfersPanels panels, UUID window) { return panels.panelHost(window); }
}
