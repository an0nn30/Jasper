package dev.jasper.remote.ui;

import javax.swing.*;

/** Owner-package access to passive UI controls for plugin integration tests. */
public final class UiTestAccess {
    private UiTestAccess() {}
    public static JTextField groupName(GroupNamePanel panel) { return panel.name; }
    public static JButton saveGroup(GroupNamePanel panel) { return panel.save; }

    public static JButton cancel(ConnectionPanel panel) { return panel.cancel; }
    public static JButton retry(ConnectionPanel panel) { return panel.retry; }
    public static String connectionStatus(ConnectionPanel panel) { return panel.message.getText(); }

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
    public static JButton chooseCredential(ImportPanel panel, int row) { return panel.choose.get(row); }
    public static JButton refreshImport(ImportPanel panel) { return panel.refresh; }
    public static JButton cancelImport(ImportPanel panel) { return panel.cancel; }
    public static JCheckBox importCheck(ImportPanel panel, int row) { return panel.checks.get(row); }
    public static String importStatus(ImportPanel panel) { return panel.status.getText(); }
    public static JButton importButton(ImportPanel panel) { return panel.importButton; }
}
