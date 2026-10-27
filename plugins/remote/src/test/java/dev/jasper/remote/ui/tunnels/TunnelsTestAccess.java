package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.testsupport.Kits;
import dev.jasper.sdk.ui.UiEvent;
import java.util.Set;
import java.util.UUID;

/**
 * Owner-package access to the tunnels views for plugin tests in other packages: each call is the event a user's gesture
 * reports on the kit view. The views are EDT-confined, so call these on the EDT ({@link Kits#edt}).
 */
public final class TunnelsTestAccess {
    private TunnelsTestAccess() { }
    public static void remotePort(ForwardPanel panel, String text) { Kits.type(panel.view(), ForwardPanel.REMOTE_PORT, text); }
    public static void targetHost(ForwardPanel panel, String text) { Kits.type(panel.view(), ForwardPanel.TARGET_HOST, text); }
    public static void host(ForwardPanel panel, RemoteHost host) { Kits.emit(panel.view(), new UiEvent.Selected(ForwardPanel.HOST, Set.of(host.id().toString()))); }
    public static void forward(ForwardPanel panel) { Kits.click(panel.view(), ForwardPanel.FORWARD); }
    public static void cancel(TunnelEditor editor) { Kits.click(editor.view(), TunnelEditor.CANCEL); }
    public static void name(TunnelEditor editor, String text) { Kits.type(editor.view(), TunnelEditor.NAME, text); }
    public static void save(TunnelEditor editor) { Kits.click(editor.view(), TunnelEditor.SAVE); }
    public static String message(TunnelEditor editor) { return editor.message(); }
    public static void listenPort(TunnelEditor editor, String text) { Kits.type(editor.view(), TunnelEditor.LISTEN_PORT, text); }
    public static void forwardPort(TunnelEditor editor, String text) { Kits.type(editor.view(), TunnelEditor.FORWARD_PORT, text); }

    /** The Tunnels panel, once it was opened in the main window. */
    public static TunnelsPanel panel(TunnelsUi tunnels) { return tunnels.panel(); }

    /** {@code window}'s Tunnels panel while it is open, else null. */
    public static TunnelsPanel panel(TunnelsUi tunnels, UUID window) { return tunnels.panel(window); }

    /** The host of {@code window}'s Tunnels panel while it is open. */
    public static java.util.Optional<dev.jasper.sdk.ui.PanelHost> panelHost(TunnelsUi tunnels, UUID window) { return tunnels.panelHost(window); }

    /** The rows the panel lists, by row id. */
    public static java.util.List<String> rowIds(TunnelsPanel panel) { return panel.rowIds(); }

    /** The row id of a tunnel's row. */
    public static String rowId(UUID tunnel) { return TunnelsPanel.rowId(tunnel); }

    /** The panel's double click on a tunnel's row: edit a saved tunnel, Save as Tunnel for a quick forward. */
    public static void activate(TunnelsPanel panel, UUID id) { select(panel, id); Kits.press(panel.view(), TunnelsPanel.ACTIVATE); }

    /** The panel's Delete key on a tunnel's row. */
    public static void delete(TunnelsPanel panel, UUID id) { select(panel, id); Kits.press(panel.view(), TunnelsPanel.DELETE); }

    private static void select(TunnelsPanel panel, UUID id) {
        String row = TunnelsPanel.rowId(id);
        if (!panel.rowIds().contains(row)) throw new AssertionError("The panel lists no row for " + id);
        Kits.emit(panel.view(), new UiEvent.Selected(TunnelsPanel.ROWS, Set.of(row)));
    }
}
