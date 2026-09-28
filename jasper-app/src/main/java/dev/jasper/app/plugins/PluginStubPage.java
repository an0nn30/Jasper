package dev.jasper.app.plugins;

import dev.jasper.app.settingsui.SettingsPage;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/** Settings' page for an installed plugin that is not running: why, and a way to edit its file. EDT only. */
final class PluginStubPage implements SettingsPage {
    private final String pluginId;
    private final String title;
    private final JLabel text;
    private final JLabel reason;
    final JButton open = new JButton("Open in Editor");
    private JPanel panel;

    PluginStubPage(String pluginId, String title, String why, Runnable openFile) {
        this.pluginId = pluginId;
        this.title = title;
        text = new JLabel("Start " + title + " to edit its settings.");
        reason = new JLabel(why.isBlank() ? "" : "Not running: " + why);
        text.putClientProperty("html.disable", true);
        reason.putClientProperty("html.disable", true);
        reason.setForeground(UIManager.getColor("Label.disabledForeground"));
        open.addActionListener(event -> openFile.run());
    }

    String text() { return text.getText(); }
    String reason() { return reason.getText(); }

    @Override public String id() { return PluginRuntime.settingsPageId(pluginId); }
    @Override public String title() { return title; }
    @Override public Optional<String> parent() { return Optional.of(PluginRuntime.TOOLS); }
    @Override public String helpAnchor() { return "plugins"; }
    @Override public List<String> searchTerms() { return List.of(title); }

    @Override public JComponent component() {
        if (panel != null) return panel;
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        text.setAlignmentX(0);
        reason.setAlignmentX(0);
        panel.add(text);
        panel.add(reason);
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 12));
        buttons.setOpaque(false);
        buttons.setAlignmentX(0);
        buttons.add(open);
        panel.add(buttons);
        return panel;
    }

    @Override public boolean modified() { return false; }
    @Override public void reset() { }
    @Override public CompletableFuture<Void> apply() { return CompletableFuture.completedFuture(null); }
    @Override public void highlight(String query) { }
    @Override public void dispose() { }
}
