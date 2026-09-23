package dev.jasper.remote.ui;

import javax.swing.*;

/** Owner-package access to passive UI controls for plugin integration tests. */
public final class UiTestAccess {
    private UiTestAccess() {}
    public static JButton trust(HostKeyPanel panel) { return panel.trust; }
    public static JButton add(HostsPanel panel) { return panel.add; }
    public static JList<HostRows.Row> list(HostsPanel panel) { return panel.list; }
    public static JLabel cardCredential(HostsPanel panel) { return panel.cardCredential; }
    public static JPopupMenu menuFor(HostsPanel panel, int index) { return panel.menuFor(index); }
    public static void toggle(HostsPanel panel, int index) { panel.toggle(index); }
    public static JTextField name(HostEditor editor) { return editor.name; }
    public static JTextField hostname(HostEditor editor) { return editor.hostname; }
    public static JTextField username(HostEditor editor) { return editor.username; }
    public static JRadioButton agentAuth(HostEditor editor) { return editor.agentAuth; }
    public static JButton save(HostEditor editor) { return editor.save; }
    public static JButton importButton(ImportPanel panel) { return panel.importButton; }
}
