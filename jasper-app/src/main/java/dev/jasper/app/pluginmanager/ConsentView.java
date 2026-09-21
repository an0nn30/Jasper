package dev.jasper.app.pluginmanager;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/** What the user reads before a plugin may load: who it is, what it asks for, and that this is no sandbox. */
final class ConsentView extends JPanel {
    final JButton allow;
    final JButton cancel = new JButton("Cancel");
    private final JTextArea body = new JTextArea();

    ConsentView(String name, String version, String vendor, List<String> capabilities, boolean update, String allowLabel,
                Runnable onAllow, Runnable onCancel) {
        super(new BorderLayout(0, 12));
        allow = new JButton(allowLabel);
        var text = new StringBuilder(name).append(' ').append(version);
        if (!vendor.isBlank()) text.append("\nFrom ").append(vendor);
        if (update) text.append("\n\nThis replaces the installed version when Jasper restarts.");
        if (capabilities.isEmpty()) text.append("\n\nIt asks for no access to your terminals.");
        else {
            text.append("\n\nIt asks to:");
            for (String capability : capabilities) text.append("\n  • ").append(Capabilities.describe(capability));
        }
        text.append("\n\nA plugin is unrestricted code running inside Jasper, with everything your account can reach. "
            + "The list above is what the plugin says it does; it is not a sandbox. Allow only plugins you trust.");
        body.setText(text.toString());
        body.setEditable(false); body.setLineWrap(true); body.setWrapStyleWord(true); body.setOpaque(false);
        body.setFont(UIManager.getFont("Label.font"));
        body.setColumns(44);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        add(body, BorderLayout.CENTER);
        var buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        buttons.add(cancel); buttons.add(allow);
        add(buttons, BorderLayout.SOUTH);
        allow.addActionListener(event -> onAllow.run());
        cancel.addActionListener(event -> onCancel.run());
    }

    String text() { return body.getText(); }
}
