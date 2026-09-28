package dev.jasper.app.settingsui;

import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/** A page that describes a feature kept in its own window and opens it (Keymap and Plugins until plan B2). */
public final class LauncherPage implements SettingsPage {
    private final String id;
    private final String title;
    private final String description;
    private final List<String> terms;
    private final JButton button;
    private JPanel panel;

    public LauncherPage(String id, String title, String description, String button, Runnable open, List<String> terms, PageContext context) {
        this.id = id; this.title = title; this.description = description;
        var all = new ArrayList<String>();
        all.add(title);
        all.addAll(terms);
        this.terms = List.copyOf(all);
        this.button = new JButton(button);
        this.button.addActionListener(event -> context.closeThen().accept(open));
    }

    JButton button() { return button; }

    @Override public String id() { return id; }
    @Override public String title() { return title; }
    @Override public Optional<String> parent() { return Optional.empty(); }
    @Override public List<String> searchTerms() { return terms; }

    @Override public JComponent component() {
        if (panel != null) return panel;
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        var text = new JTextArea(description);
        text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true); text.setOpaque(false);
        text.setAlignmentX(0);
        panel.add(text);
        var row = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 12));
        row.setOpaque(false);
        row.setAlignmentX(0);
        row.add(button);
        panel.add(row);
        return panel;
    }

    @Override public boolean modified() { return false; }
    @Override public void reset() { }
    @Override public CompletableFuture<Void> apply() { return CompletableFuture.completedFuture(null); }
    @Override public void highlight(String query) { }
    @Override public void dispose() { }
}
