package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.hosts.RemoteHost;
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
}
