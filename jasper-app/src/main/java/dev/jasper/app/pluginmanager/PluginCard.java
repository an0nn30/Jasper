package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/** One plugin in the list: its icon, name, version and vendor, a badge when it needs attention, and its checkbox. EDT only. */
final class PluginCard extends JPanel {
    final JCheckBox enabled = new JCheckBox();
    final JLabel name;
    private final JLabel meta;
    private final JLabel badge;
    private final PluginRuntime.Row row;
    private boolean selected;

    PluginCard(PluginRuntime.Row row, Runnable toggle, Runnable select, Consumer<MouseEvent> popup) {
        super(new BorderLayout(10, 0));
        this.row = row;
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        add(new JLabel(PluginIcons.of(row, 40, greyed(row))), BorderLayout.WEST);
        name = PluginManagerPanel.plain(new JLabel(row.name()));
        meta = PluginManagerPanel.plain(new JLabel(row.vendor().isBlank() ? row.version() : row.version() + "  " + row.vendor()));
        badge = PluginManagerPanel.plain(new JLabel(badge(row)));
        badge.setVisible(!badge.getText().isEmpty());
        var text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        text.add(name);
        text.add(meta);
        text.add(badge);
        add(text, BorderLayout.CENTER);
        enabled.setOpaque(false);
        // The list is one focus stop: Space toggles the selected card, so the checkbox takes no focus of its own.
        enabled.setFocusable(false);
        enabled.setSelected(row.enabled());
        enabled.getAccessibleContext().setAccessibleName("Enable " + row.name());
        if (!toggleable(row)) enabled.setToolTipText(!row.pending().isEmpty() ? row.pending()
            : !row.reason().isEmpty() ? row.reason() : "This plugin cannot be enabled or disabled here.");
        // The checkbox shows the snapshot's state; the next snapshot says whether anything changed.
        enabled.addActionListener(event -> { enabled.setSelected(row.enabled()); toggle.run(); });
        add(enabled, BorderLayout.EAST);
        var mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { select.run(); if (event.isPopupTrigger()) popup.accept(event); }
            @Override public void mouseReleased(MouseEvent event) { if (event.isPopupTrigger()) popup.accept(event); }
        };
        addMouseListener(mouse);
        restyle();
        busy(false);
    }

    @Override public void updateUI() {
        super.updateUI();
        // Called by JPanel's constructor, before the labels exist.
        if (badge != null) restyle();
    }

    private void restyle() {
        Font base = UIManager.getFont("Label.font");
        if (base != null) {
            name.setFont(base.deriveFont(Font.BOLD));
            badge.setFont(base.deriveFont(base.getSize2D() - 1f));
        }
        meta.setForeground(UIManager.getColor("Label.disabledForeground"));
        Color warning = UIManager.getColor("Actions.Red");
        badge.setForeground(row.state().equals("FAILED") || row.state().equals("SKIPPED") ? warning
            : UIManager.getColor("Component.accentColor"));
        setSelected(selected);
    }

    PluginRuntime.Row row() { return row; }

    /** The card as a line: name, version and badge. */
    String text() { return row.name() + "  " + row.version() + (badge.getText().isEmpty() ? "" : " — " + badge.getText()); }

    void busy(boolean busy) { enabled.setEnabled(!busy && toggleable(row)); }

    void setSelected(boolean value) {
        selected = value;
        Color background = UIManager.getColor(value ? "List.selectionInactiveBackground" : "List.background");
        if (value && background == null) background = UIManager.getColor("List.selectionBackground");
        setBackground(background);
        setOpaque(value);
    }

    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

    /** The first badge that applies, or empty. */
    static String badge(PluginRuntime.Row row) {
        if (row.pendingRemoval()) return "Remove at restart";
        if (row.pendingInstall()) return "Install at restart";
        if (!row.pending().isEmpty()) return "Restart to apply";
        if (row.needsConsent()) return "Needs review";
        return switch (row.state()) {
            case "FAILED" -> "Failed";
            case "SKIPPED" -> "Skipped";
            default -> "";
        };
    }

    /** Disabled, or not running because it needs review, failed or was skipped. */
    static boolean greyed(PluginRuntime.Row row) {
        return !row.enabled() || row.needsConsent() || row.state().equals("FAILED") || row.state().equals("SKIPPED");
    }

    /** Whether the checkbox can act: toggle it, or review it first. */
    static boolean toggleable(PluginRuntime.Row row) { return row.canToggle() || row.needsConsent(); }
}
