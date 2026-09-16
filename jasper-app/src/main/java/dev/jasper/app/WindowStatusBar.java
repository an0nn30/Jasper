package dev.jasper.app;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.terminal.Palette;
import java.awt.*;
import javax.swing.*;

/** Bounded status segments: long shell/path metadata never displaces dimensions/defaults. */
final class WindowStatusBar extends JPanel {
    private final Segment left = new Segment(new JLabel());
    private final JButton configButton = new JButton("Built-in defaults");
    private final Segment right = new Segment(configButton);
    Runnable onConfigurationDetails = () -> {};
    private String configText = "Built-in defaults";
    private String configColor = "Jasper.configSuccessForeground";
    private String shell = "", directory = "", dimensions = "";
    private boolean running;
    private boolean integration;
    private Palette palette = Palette.jasperDark();
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

    void setMetadata(String shell, String directory, String dimensions, boolean running, boolean integration) {
        this.running = running; this.shell = shell; this.directory = directory; this.dimensions = dimensions;
        this.integration = integration;
        left.setParts(shellLabel(), directory);
        left.setToolTipText(directory);
        left.setFirstToolTip(shell.isEmpty() ? null : integration ? "Shell integration active" : "Shell integration not detected");
        updateText();
    }

    void setConfiguration(ConfigService.State state) {
        boolean error = state.diagnostics().stream().anyMatch(d -> d.severity() == ConfigDiagnostic.Severity.ERROR);
        boolean warning = !state.diagnostics().isEmpty();
        configText = error ? "Config error" : warning ? "Config warnings" : state.present() ? "Config loaded" : "Built-in defaults";
        if (warning) state.diagnostics().stream().filter(d -> d.line() > 0).findFirst()
            .ifPresent(d -> configText += " (line " + d.line() + ")");
        configColor = error ? "Jasper.configErrorForeground" : warning ? "Jasper.configWarningForeground" : "Jasper.configSuccessForeground";
        configButton.setToolTipText(state.file().toString());
        configButton.setEnabled(true);
        updateText(); refreshTheme();
    }

    /** The shell name with its integration dot: filled once the shell has marked a prompt. */
    private String shellLabel() {
        return shell.isEmpty() ? "" : shell + (integration ? " \u25cf" : " \u25cb");
    }

    private void updateText() {
        right.setParts(dimensions, configText);
        configButton.getAccessibleContext().setAccessibleName(configText);
        text = shell.isEmpty() ? configText
            : shellLabel() + "  |  " + directory + "  |  " + dimensions + "  |  " + configText;
        // A screen reader would otherwise announce the dot's Unicode name; say what it means instead.
        String spoken = shell.isEmpty() ? ""
            : shell + (integration ? ", shell integration active" : ", shell integration not detected");
        getAccessibleContext().setAccessibleDescription(shell.isEmpty() ? text
            : spoken + "  |  " + directory + "  |  " + dimensions + "  |  " + configText);
        revalidate(); repaint();
    }
    JButton configButton() { return configButton; }
    String getText() { return text; }
    void applyPalette(Palette next) { palette = java.util.Objects.requireNonNull(next); refreshTheme(); }
    private Color muted() { return UIManager.getColor("Jasper.mutedForeground"); }
    void refreshTheme() {
        setBackground(palette.background());
        left.refreshTheme(); right.refreshTheme();
        configButton.setForeground(UIManager.getColor(configColor));
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
        void setFirstToolTip(String tip) { first.setToolTipText(tip); }
        void refreshTheme() {
            for (JComponent label : new JComponent[]{first, slash, last}) {
                label.setForeground(label == slash ? UIManager.getColor("Separator.foreground") : muted());
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
        g.setColor(running ? UIManager.getColor("Jasper.runningForeground") : muted());
        var copy = (Graphics2D) g.create();
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = UIScale.scale(5);
            copy.fillOval(UIScale.scale(14), (getHeight() - size) / 2, size, size);
        } finally { copy.dispose(); }
    }
}
