package dev.jasper.app.settingsui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.UIManager;

/** IntelliJ's section header: a title followed by a line to the right edge. */
final class TitledSeparator extends JPanel {
    final JLabel title;

    TitledSeparator(String text) {
        super(new GridBagLayout());
        setOpaque(false);
        title = new JLabel(text);
        title.putClientProperty("html.disable", true);
        title.setForeground(UIManager.getColor("Label.disabledForeground"));
        var c = new GridBagConstraints();
        c.gridx = 0; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(0, 0, 0, 8);
        add(title, c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(0, 0, 0, 0);
        add(new JSeparator(), c);
    }
}
