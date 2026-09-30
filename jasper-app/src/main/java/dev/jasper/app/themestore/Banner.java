package dev.jasper.app.themestore;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/** A one-line message above the tabs, with an optional action button; hidden while empty. EDT only. */
final class Banner extends JPanel {
    private final JLabel text = new JLabel();
    private final JButton action = new JButton();
    private Runnable onAction = () -> { };
    private boolean error;

    Banner() {
        super(new BorderLayout(8, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 20, 8, 20));
        text.putClientProperty("html.disable", true);
        action.putClientProperty("html.disable", true);
        action.addActionListener(event -> onAction.run());
        add(text, BorderLayout.CENTER);
        add(action, BorderLayout.EAST);
        clear();
    }

    void info(String message) { show(message, false, null, null); }
    void error(String message) { show(message, true, null, null); }
    void offer(String message, String label, Runnable run) { show(message, false, label, run); }
    void errorOffer(String message, String label, Runnable run) { show(message, true, label, run); }
    void clear() { show("", false, null, null); }

    private void show(String message, boolean isError, String label, Runnable run) {
        error = isError;
        text.setText(message);
        action.setText(label == null ? "" : label);
        action.setVisible(label != null);
        onAction = run == null ? () -> { } : run;
        setVisible(!message.isEmpty());
        restyle();
        revalidate();
        repaint();
    }

    @Override public void updateUI() {
        super.updateUI();
        // JPanel's constructor calls this before the fields exist.
        if (text != null) restyle();
    }

    private void restyle() { text.setForeground(UIManager.getColor(error ? "Actions.Red" : "Label.foreground")); }

    String text() { return text.getText(); }
    String actionText() { return action.isVisible() ? action.getText() : ""; }
    void click() { action.doClick(); }
}
