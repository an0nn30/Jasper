package dev.jasper.app;

import dev.jasper.terminal.Palette;
import java.awt.*;
import javax.swing.*;

/** Bounded status segments: long shell/path metadata never displaces dimensions/defaults. */
final class WindowStatusBar extends JPanel {
    private final Segment left = new Segment(new JLabel());
    private final JButton configButton = new JButton("Built-in defaults");
    private final Segment right = new Segment(configButton);
    private final JButton vaultButton = new JButton(VaultIcons.icon("lock"));
    Runnable onVaultClick = () -> {};
    private static final int VAULT_WIDTH = 28;
    Runnable onConfigurationDetails = () -> {};
    private String configText = "Built-in defaults";
    private String shell = "", directory = "", dimensions = "";
    private String text = "Built-in defaults";

    WindowStatusBar() {
        super(null);
        configButton.setEnabled(false); configButton.putClientProperty("html.disable", true);
        configButton.addActionListener(event -> onConfigurationDetails.run());
        vaultButton.setEnabled(false); vaultButton.setFocusable(false);
        vaultButton.setMargin(new Insets(0, 0, 0, 0)); vaultButton.setBorderPainted(false);
        vaultButton.setContentAreaFilled(false); vaultButton.putClientProperty("html.disable", true);
        vaultButton.setToolTipText("Credential vault is not available");
        vaultButton.getAccessibleContext().setAccessibleName("Credential vault is not available");
        vaultButton.addActionListener(event -> onVaultClick.run());
        add(vaultButton);
        add(left); add(right); refreshTheme();
        getAccessibleContext().setAccessibleName("Terminal status");
    }

    void setMetadata(String shell, String directory, String dimensions, boolean running) {
        this.shell = shell; this.directory = directory; this.dimensions = dimensions;
        left.setParts(shell.isEmpty() || running ? shell : shell + " (exited)", directory);
        left.setToolTipText(directory);
        updateText();
    }

    void setConfiguration(ConfigService.State state) {
        boolean error = state.diagnostics().stream().anyMatch(d -> d.severity() == ConfigDiagnostic.Severity.ERROR);
        boolean warning = !state.diagnostics().isEmpty();
        configText = error ? "Config error" : warning ? "Config warnings" : state.present() ? "Config loaded" : "Built-in defaults";
        if (warning) state.diagnostics().stream().filter(d -> d.line() > 0).findFirst()
            .ifPresent(d -> configText += " (line " + d.line() + ")");
        configButton.setToolTipText(state.file().toString());
        configButton.setEnabled(true);
        updateText(); refreshTheme();
    }

    private void updateText() {
        right.setParts(dimensions, configText);
        configButton.getAccessibleContext().setAccessibleName(configText);
        text = shell.isEmpty() ? configText : shell + "  |  " + directory + "  |  " + dimensions + "  |  " + configText;
        getAccessibleContext().setAccessibleDescription(text);
        revalidate(); repaint();
    }
    JButton configButton() { return configButton; }
    JButton vaultButton() { return vaultButton; }

    void setVault(boolean connected, boolean unlocked, String tooltip) {
        vaultButton.setEnabled(connected);
        vaultButton.setIcon(VaultIcons.icon(unlocked ? "unlock" : "lock"));
        vaultButton.setToolTipText(tooltip);
        vaultButton.getAccessibleContext().setAccessibleName(tooltip);
        revalidate(); repaint();
    }
    String getText() { return text; }
    void applyPalette(Palette next) { java.util.Objects.requireNonNull(next); refreshTheme(); }
    static double contrast(Color a, Color b) {
        double first = luminance(a), second = luminance(b);
        return (Math.max(first, second) + .05) / (Math.min(first, second) + .05);
    }
    private static double luminance(Color color) {
        double[] rgb = {color.getRed() / 255.0, color.getGreen() / 255.0, color.getBlue() / 255.0};
        for (int i = 0; i < rgb.length; i++) rgb[i] = rgb[i] <= .04045 ? rgb[i] / 12.92 : Math.pow((rgb[i] + .055) / 1.055, 2.4);
        return .2126 * rgb[0] + .7152 * rgb[1] + .0722 * rgb[2];
    }
    void refreshTheme() {
        setBackground(UIManager.getColor("Panel.background"));
        left.refreshTheme(); right.refreshTheme();
        configButton.setForeground(UIManager.getColor("Button.foreground"));
        vaultButton.setForeground(UIManager.getColor("Label.foreground"));
    }

    private final class Segment extends JPanel {
        private final JLabel first = new JLabel(), slash = new JLabel("/", SwingConstants.CENTER);
        private final JComponent last;
        Segment(JComponent last) {
            super(null); setOpaque(false); this.last = last;
            first.putClientProperty("html.disable", true); last.putClientProperty("html.disable", true);
            add(first); add(slash); add(last);
        }
        void setParts(String first, String last) {
            this.first.setText(first);
            if (this.last instanceof JLabel label) label.setText(last);
            else ((JButton) this.last).setText(last);
            slash.setVisible(!first.isEmpty());
        }
        void refreshTheme() {
            for (JComponent label : new JComponent[]{first, slash, last}) {
                label.setForeground(UIManager.getColor(label instanceof JButton ? "Button.foreground" : "Label.foreground"));
                label.setFont(UIManager.getFont(label instanceof JButton ? "Button.font" : "Label.font"));
            }
        }
        @Override public Dimension getPreferredSize() {
            return new Dimension(first.getPreferredSize().width + (slash.isVisible() ? 28 : 0)
                + last.getPreferredSize().width, 30);
        }
        @Override public void doLayout() {
            int firstWidth = Math.min(getWidth(), first.getPreferredSize().width);
            int separatorWidth = Math.min(Math.max(0, getWidth() - firstWidth), slash.isVisible() ? 28 : 0);
            first.setBounds(0, 0, firstWidth, getHeight());
            slash.setBounds(firstWidth, 0, separatorWidth, getHeight());
            last.setBounds(firstWidth + separatorWidth, 0, Math.max(0, getWidth() - firstWidth - separatorWidth), getHeight());
        }
    }
    @Override public Dimension getMinimumSize() { return new Dimension(0, 30); }
    @Override public Dimension getPreferredSize() { return new Dimension(0, 30); }
    @Override public void doLayout() {
        int edge = Math.min(6, getWidth() / 2), leftInset = edge;
        int vaultWidth = Math.min(VAULT_WIDTH, Math.max(0, getWidth() - edge));
        vaultButton.setBounds(Math.max(0, getWidth() - edge - vaultWidth), 0, vaultWidth, getHeight());
        int available = Math.max(0, vaultButton.getX() - edge * 2);
        int rightWidth = Math.min(available, right.getPreferredSize().width);
        right.setBounds(Math.max(edge, vaultButton.getX() - edge - rightWidth), 0, rightWidth, getHeight());
        left.setBounds(leftInset, 0, Math.max(0, right.getX() - leftInset - edge), getHeight());
    }
}
