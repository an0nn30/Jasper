package dev.moray.app;

import com.formdev.flatlaf.util.UIScale;
import java.awt.*;
import javax.swing.*;

/** Bounded status segments: long shell/path metadata never displaces dimensions/defaults. */
final class WindowStatusBar extends JPanel {
    private final Segment left = new Segment(new JLabel());
    private final JButton configButton = new JButton("Built-in defaults");
    private final Segment right = new Segment(configButton);
    Runnable onConfigurationDetails = () -> {};
    private String configText = "Built-in defaults";
    private String configColor = "Moray.configSuccessForeground";
    private String shell = "", directory = "", dimensions = "";
    private boolean running;
    private String text = "Built-in defaults";

    WindowStatusBar() {
        super(null);
        configButton.setBorder(BorderFactory.createEmptyBorder());
        configButton.setContentAreaFilled(false); configButton.setOpaque(false);
        configButton.setEnabled(false); configButton.putClientProperty("html.disable", true);
        configButton.addActionListener(event -> onConfigurationDetails.run());
        add(left); add(right); refreshTheme();
        getAccessibleContext().setAccessibleName("Terminal status");
    }

    void setMetadata(String shell, String directory, String dimensions, boolean running) {
        this.running = running; this.shell = shell; this.directory = directory; this.dimensions = dimensions;
        left.setParts(shell, directory);
        left.setToolTipText(directory);
        updateText();
    }

    void setConfiguration(ConfigService.State state) {
        boolean error = state.diagnostics().stream().anyMatch(d -> d.severity() == ConfigDiagnostic.Severity.ERROR);
        boolean warning = !state.diagnostics().isEmpty();
        configText = error ? "Config error" : warning ? "Config warnings" : state.present() ? "Config loaded" : "Built-in defaults";
        if (warning) state.diagnostics().stream().filter(d -> d.line() > 0).findFirst()
            .ifPresent(d -> configText += " (line " + d.line() + ")");
        configColor = error ? "Moray.configErrorForeground" : warning ? "Moray.configWarningForeground" : "Moray.configSuccessForeground";
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
    String getText() { return text; }
    void refreshTheme() {
        setBackground(UIManager.getColor("Panel.background"));
        left.refreshTheme(); right.refreshTheme();
        configButton.setForeground(UIManager.getColor(configColor));
    }

    private static final class Segment extends JPanel {
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
                label.setForeground(UIManager.getColor(label == slash ? "Separator.foreground" : "Moray.mutedForeground"));
                label.setFont(UIManager.getFont("Label.font").deriveFont(UIScale.scale(10f)));
            }
        }
        @Override public Dimension getPreferredSize() {
            return new Dimension(first.getPreferredSize().width + (slash.isVisible() ? UIScale.scale(28) : 0)
                + last.getPreferredSize().width, UIScale.scale(30));
        }
        @Override public void doLayout() {
            int firstWidth = Math.min(getWidth(), first.getPreferredSize().width);
            int separatorWidth = Math.min(Math.max(0, getWidth() - firstWidth), slash.isVisible() ? UIScale.scale(28) : 0);
            first.setBounds(0, 0, firstWidth, getHeight());
            slash.setBounds(firstWidth, 0, separatorWidth, getHeight());
            last.setBounds(firstWidth + separatorWidth, 0, Math.max(0, getWidth() - firstWidth - separatorWidth), getHeight());
        }
    }
    @Override public Dimension getMinimumSize() { return new Dimension(0, UIScale.scale(30)); }
    @Override public Dimension getPreferredSize() { return new Dimension(0, UIScale.scale(30)); }
    @Override public void doLayout() {
        int edge = Math.min(UIScale.scale(14), getWidth() / 2), leftInset = Math.min(getWidth(), UIScale.scale(26));
        int available = Math.max(0, getWidth() - edge * 2);
        int rightWidth = Math.min(available, right.getPreferredSize().width);
        right.setBounds(Math.max(edge, getWidth() - edge - rightWidth), 0, rightWidth, getHeight());
        left.setBounds(leftInset, 0, Math.max(0, right.getX() - leftInset - edge), getHeight());
    }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(UIManager.getColor(running ? "Moray.runningForeground" : "Moray.mutedForeground"));
        var copy = (Graphics2D) g.create();
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = UIScale.scale(5);
            copy.fillOval(UIScale.scale(14), (getHeight() - size) / 2, size, size);
        } finally { copy.dispose(); }
    }
}
