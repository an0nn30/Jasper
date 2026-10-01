package dev.jasper.fileserver.ui;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** Owner-package access to passive UI controls for plugin integration tests. */
public final class UiTestAccess {
    private UiTestAccess() { }

    public static JButton add(FileServersPanel panel) { return panel.add; }
    public static JButton toggle(FileServersPanel panel) { return panel.toggle; }
    public static JTextArea activity(FileServersPanel panel) { return panel.activity; }
    public static JLabel cardState(FileServersPanel panel) { return panel.cardState; }
    public static JPopupMenu menuFor(FileServersPanel panel, int index) { return panel.menuFor(index); }

    public static JTextField name(ServerEditor editor) { return editor.name; }
    public static JTextField folder(ServerEditor editor) { return editor.folder; }
    public static JTextField port(ServerEditor editor) { return editor.port; }
    public static JRadioButton vault(ServerEditor editor) { return editor.vault; }
    public static JButton chooseAccount(ServerEditor editor) { return editor.chooseAccount; }
    public static JButton save(ServerEditor editor) { return editor.save; }
    public static JLabel message(ServerEditor editor) { return editor.message; }

    public static JLabel message(ConfirmPanel panel) { return panel.message; }
    public static JButton confirm(ConfirmPanel panel) { return panel.confirm; }
    public static JButton cancel(ConfirmPanel panel) { return panel.cancel; }
    public static JTextArea cardTls(FileServersPanel panel) { return panel.cardTls; }
}
