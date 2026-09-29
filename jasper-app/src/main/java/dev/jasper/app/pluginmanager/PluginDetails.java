package dev.jasper.app.pluginmanager;

import com.formdev.flatlaf.FlatClientProperties;
import dev.jasper.app.plugins.PluginRuntime;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/** The selected plugin: header, primary action and ▾ menu, then Overview and Details tabs. A passive view; EDT only. */
final class PluginDetails extends JPanel {
    final JButton primary = new JButton();
    final JButton more = new JButton("▾");
    final JButton homepage = new JButton("Plugin homepage ↗");
    final JTabbedPane tabs = new JTabbedPane();
    private final JLabel icon = new JLabel();
    private final JLabel name = PluginManagerPanel.plain(new JLabel(" "));
    private final JLabel vendor = PluginManagerPanel.plain(new JLabel());
    private final JLabel version = PluginManagerPanel.plain(new JLabel());
    private final JTextArea overview = area();
    private final JTextArea details = area();
    private final JPanel header = new JPanel(new BorderLayout(14, 0));
    private final PluginManagerPanel.Handlers handlers;
    private PluginRuntime.Row row;

    PluginDetails(PluginManagerPanel.Handlers handlers) {
        super(new BorderLayout(0, 12));
        this.handlers = handlers;
        setBorder(BorderFactory.createEmptyBorder(4, 16, 4, 4));
        homepage.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        more.setToolTipText("More actions");
        more.getAccessibleContext().setAccessibleName("More actions");
        homepage.setMargin(new java.awt.Insets(0, 0, 0, 0));
        homepage.setHorizontalAlignment(javax.swing.SwingConstants.LEADING);
        var actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        actions.setOpaque(false);
        actions.add(primary);
        actions.add(more);
        actions.add(Box.createHorizontalStrut(12));
        actions.add(version);
        var text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        // The homepage has a line of its own: beside a long vendor name it would wrap out of sight.
        for (JComponent part : List.of(name, vendor, homepage, actions)) {
            part.setAlignmentX(0f);
            text.add(part);
            text.add(Box.createVerticalStrut(6));
        }
        header.setOpaque(false);
        header.add(icon, BorderLayout.WEST);
        header.add(text, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);
        tabs.addTab("Overview", scroll(overview));
        tabs.addTab("Details", scroll(details));
        add(tabs, BorderLayout.CENTER);
        primary.addActionListener(event -> {
            if (row != null) (row.needsConsent() ? handlers.review() : handlers.toggle()).accept(row);
        });
        more.addActionListener(event -> { if (row != null) menu().show(more, 0, more.getHeight()); });
        homepage.addActionListener(event -> { if (row != null) homepage(row).ifPresent(handlers.browse()); });
        restyle();
        show(null, false);
    }

    @Override public void updateUI() {
        super.updateUI();
        // Called by JPanel's constructor, before the labels exist.
        if (version != null) restyle();
    }

    private void restyle() {
        Font base = UIManager.getFont("Label.font");
        if (base != null) name.setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 5f));
        Color secondary = UIManager.getColor("Label.disabledForeground");
        vendor.setForeground(secondary);
        version.setForeground(secondary);
        Color link = UIManager.getColor("Component.linkColor");
        if (link != null) homepage.setForeground(link);
    }

    private static JTextArea area() {
        var area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
        return area;
    }

    private static JScrollPane scroll(JTextArea area) {
        var scroll = new JScrollPane(area, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        return scroll;
    }

    /** Shows {@code shown}, or nothing when it is null; {@code busy} disables the actions. */
    void show(PluginRuntime.Row shown, boolean busy) {
        row = shown;
        header.setVisible(row != null);
        tabs.setVisible(row != null);
        if (row == null) {
            overview.setText("");
            details.setText("");
            return;
        }
        icon.setIcon(PluginIcons.of(row, 64, PluginCard.greyed(row)));
        name.setText(row.name());
        vendor.setText(row.vendor().isBlank() ? row.origin() : row.vendor());
        homepage.setVisible(homepage(row).isPresent());
        version.setText(row.version());
        primary.setVisible(PluginCard.toggleable(row));
        primary.setText(row.needsConsent() ? "Review…" : row.enabled() ? "Disable" : "Enable");
        primary.setEnabled(!busy);
        more.setEnabled(!busy);
        overview.setText(row.description().isBlank() ? "No description." : row.description());
        overview.setCaretPosition(0);
        details.setText(describe(row));
        details.setCaretPosition(0);
    }

    /** Everything the pane shows as text, for tests and search. */
    String text() {
        if (row == null) return "";
        return name.getText() + " " + version.getText() + "\n" + vendor.getText() + "\n\n" + overview.getText() + "\n\n" + details.getText();
    }

    /** The selected plugin's other actions: the three openers, then Remove…, Keep or Discard Install when it has one. */
    JPopupMenu menu() {
        var menu = new JPopupMenu();
        if (row == null) return menu;
        PluginRuntime.Row shown = row;
        item(menu, "Open Settings", () -> handlers.openSettings().accept(shown));
        item(menu, "Open Plugin Folder", () -> handlers.openFolder().accept(shown));
        item(menu, "Open Data Folder", () -> handlers.openData().accept(shown));
        String removal = removal(shown);
        if (!removal.isEmpty()) {
            menu.addSeparator();
            item(menu, removal, () -> (shown.pendingInstall() ? handlers.discard() : handlers.remove()).accept(shown));
        }
        return menu;
    }

    private static void item(JPopupMenu menu, String label, Runnable action) {
        var item = new JMenuItem(label);
        item.addActionListener(event -> action.run());
        menu.add(item);
    }

    List<String> menuLabels() {
        List<String> labels = new ArrayList<>();
        for (Component component : menu().getComponents())
            labels.add(component instanceof JMenuItem item ? item.getText() : "-");
        return labels;
    }

    void clickMenu(String label) {
        for (Component component : menu().getComponents())
            if (component instanceof JMenuItem item && item.getText().equals(label)) { item.doClick(); return; }
        throw new IllegalArgumentException("No menu item " + label);
    }

    static String removal(PluginRuntime.Row row) {
        if (row.pendingInstall()) return "Discard Install";
        if (row.canRemove()) return row.pendingRemoval() ? "Keep" : "Remove…";
        return "";
    }

    /** The plugin's homepage when it is an http or https address. */
    static Optional<URI> homepage(PluginRuntime.Row row) {
        if (row.homepage().isBlank()) return Optional.empty();
        try {
            URI uri = new URI(row.homepage());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null ? Optional.of(uri) : Optional.empty();
        } catch (java.net.URISyntaxException malformed) {
            return Optional.empty();
        }
    }

    static String stateLabel(String state) {
        return switch (state) {
            case "ACTIVE" -> "Active"; case "DISABLED" -> "Disabled"; case "NEEDS_CONSENT" -> "Needs review";
            case "SKIPPED" -> "Skipped"; case "FAILED" -> "Failed"; case "NOT_LOADED" -> "Not loaded yet";
            default -> state;
        };
    }

    static String describe(PluginRuntime.Row row) {
        var text = new StringBuilder("Status\n  ").append(stateLabel(row.state()));
        if (!row.reason().isEmpty()) text.append(": ").append(row.reason());
        if (!row.pending().isEmpty()) text.append("\n  ").append(row.pending());
        if (row.errors() > 0)
            text.append("\n  ").append(row.errors()).append(row.errors() == 1 ? " error" : " errors").append(" since Jasper started; see the log");
        switch (row.origin()) {
            case "Bundled" -> text.append("\n  Bundled with Jasper; disable it here, or remove it from the application image.");
            case "Development" -> text.append("\n  Loaded from --plugin-dir.");
            default -> text.append("\n  Installed by you.");
        }
        text.append("\n  Id: ").append(row.id());
        text.append("\n\nCapabilities");
        if (row.capabilities().isEmpty()) text.append("\n  None");
        for (String capability : row.capabilities())
            text.append("\n  • ").append(Capabilities.describe(capability)).append(row.unconsented().contains(capability) ? " — not reviewed" : "");
        text.append("\n\nRequires");
        if (row.requires().isEmpty()) text.append("\n  None");
        for (String requirement : row.requires()) text.append("\n  • ").append(requirement);
        text.append("\n\nExports");
        if (row.exports().isEmpty()) text.append("\n  None");
        for (String exported : row.exports()) text.append("\n  • ").append(exported);
        text.append("\n\nLocations");
        text.append("\n  Folder: ").append(row.directory());
        text.append("\n  Settings: ").append(row.settingsFile());
        text.append("\n  Data: ").append(row.dataDirectory());
        return text.toString();
    }
}
