package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.hosts.RemoteHost;
import java.util.UUID;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTextField;

/** Owner-package access to the tunnel dialogs' controls for plugin tests in other packages. */
public final class TunnelsTestAccess {
    private TunnelsTestAccess() { }
    public static JTextField remotePort(ForwardPanel panel) { return panel.remotePort; }
    public static JTextField targetHost(ForwardPanel panel) { return panel.targetHost; }
    public static JComboBox<RemoteHost> host(ForwardPanel panel) { return panel.host; }
    public static JButton forward(ForwardPanel panel) { return panel.forward; }
    public static JButton cancel(TunnelEditor editor) { return editor.cancel; }
    public static JTextField name(TunnelEditor editor) { return editor.name; }
    public static JButton save(TunnelEditor editor) { return editor.save; }

    /** The panel's double-click on a tunnel's row: edit a saved tunnel, Save as Tunnel for a quick forward. */
    public static void activate(TunnelsPanel panel, UUID id) { panel.activate(rowOf(panel, id)); }

    /** The panel's Delete key on a tunnel's row. */
    public static void delete(TunnelsPanel panel, UUID id) { panel.delete(rowOf(panel, id)); }

    private static int rowOf(TunnelsPanel panel, UUID id) {
        var model = panel.list.getModel();
        for (int i = 0; i < model.getSize(); i++) if (model.getElementAt(i) instanceof TunnelsPanel.Item item && item.tunnel().id().equals(id)) return i;
        throw new AssertionError("The panel lists no row for " + id);
    }
}
