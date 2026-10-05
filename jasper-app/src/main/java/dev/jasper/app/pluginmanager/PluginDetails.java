package dev.jasper.app.pluginmanager;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * The selected plugin: its icon, name, vendor and homepage, the primary action, More actions and the version, then an
 * Overview | Details choice over one read-only text area. A passive view built from kit views; EDT only.
 */
final class PluginDetails {
    static final String PRIMARY = "primary", MORE = "more", HOMEPAGE = "homepage", SECTION = "section";
    static final String OVERVIEW = "overview", DETAILS = "details";
    private static final String ICON = "plugin.icon", TEXT = "plugin.text";
    private final KitContext kit;
    private final JPanel component = new JPanel(new BorderLayout());
    private final KitView header, body;
    private final IconCanvas icon = new IconCanvas();
    private final JTextArea text = new JTextArea();
    private final AutoCloseable theme;
    private PluginRuntime.Row row;
    private boolean busy;
    private String section = OVERVIEW;

    PluginDetails(KitContext kit, Consumer<UiEvent> events) {
        this.kit = kit;
        header = new KitView(kit, headerSpec(), event -> {
            if (event instanceof UiEvent.Selected chosen && chosen.id().equals(SECTION)) {
                chosen.ids().stream().findFirst().ifPresent(this::section);
                return;
            }
            events.accept(event);
        });
        header.registerContent(ICON, icon, () -> { });
        body = new KitView(kit, new ViewSpec(new Nodes.Custom("plugin-text", ControlState.READY, TEXT, Nodes.CustomKind.EDITOR, "Overview")), events);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.putClientProperty("html.disable", Boolean.TRUE);
        // Specialized read-only text: selectable and scrollable like the former tabs, painted from module roles.
        var scroll = new JScrollPane(text, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        body.registerContent(TEXT, scroll, () -> { });
        component.setOpaque(false);
        component.add(header, BorderLayout.NORTH);
        component.add(body, BorderLayout.CENTER);
        theme = kit.binding().subscribe(pair -> restyle());
        restyle();
        show(null, false);
    }

    JComponent component() { return component; }

    /** Geometry and colours from the module: the former gaps and paddings as named metrics. */
    private void restyle() {
        int edge = metric(DesignMetrics.Role.PLUGIN_DETAILS_EDGE_PADDING);
        component.setBorder(BorderFactory.createEmptyBorder(edge, metric(DesignMetrics.Role.PLUGIN_DETAILS_LEADING_PADDING), edge, edge));
        ((BorderLayout) component.getLayout()).setVgap(metric(DesignMetrics.Role.PLUGIN_DETAILS_GAP));
        int vertical = metric(DesignMetrics.Role.PLUGIN_TEXT_VERTICAL_PADDING), side = metric(DesignMetrics.Role.PLUGIN_TEXT_SIDE_PADDING);
        text.setBorder(BorderFactory.createEmptyBorder(vertical, side, vertical, side));
        var font = kit.typography().font(Typography.Role.BODY);
        text.setFont(font.deriveFont(UIScale.scale(font.getSize2D())));
        var palette = kit.theme().semantic();
        text.setForeground(palette.color(SemanticColor.TEXT_PRIMARY));
        text.setCaretColor(text.getForeground());
        text.setSelectionColor(palette.color(SemanticColor.SELECTION_BG));
        text.setSelectedTextColor(palette.color(SemanticColor.TEXT_PRIMARY));
        icon.revalidate();
        icon.repaint();
        component.revalidate();
        component.repaint();
    }

    private int metric(DesignMetrics.Role role) { return UIScale.scale(kit.metrics().value(role)); }

    /** Shows {@code shown}, or nothing when it is null; {@code busy} disables the actions. */
    void show(PluginRuntime.Row shown, boolean busy) {
        this.row = shown;
        this.busy = busy;
        component.setVisible(shown != null);
        icon.icon = shown == null ? null : PluginIcons.of(shown, kit.metrics().value(DesignMetrics.Role.PLUGIN_DETAIL_ICON), PluginCard.greyed(shown));
        icon.revalidate();
        icon.repaint();
        if (!header.isClosed()) header.update(headerSpec());
        fill();
    }

    /** Overview or Details: the chosen text replaces the other in the one text area. */
    void section(String value) {
        if (!value.equals(OVERVIEW) && !value.equals(DETAILS) || value.equals(section)) return;
        section = value;
        if (!header.isClosed()) header.update(headerSpec());
        fill();
    }

    String section() { return section; }

    private void fill() {
        String next = row == null ? "" : section.equals(OVERVIEW) ? overview(row) : describe(row);
        if (!next.equals(text.getText())) {
            text.setText(next);
            text.setCaretPosition(0);
        }
        text.getAccessibleContext().setAccessibleName(section.equals(OVERVIEW) ? "Overview" : "Details");
    }

    private static String overview(PluginRuntime.Row row) { return row.description().isBlank() ? "No description." : row.description(); }

    private ViewSpec headerSpec() {
        var shown = row;
        boolean present = shown != null;
        var hidden = new ControlState(false, true, false, "");
        var actionsState = new ControlState(true, !busy, false, "");
        List<MenuItem> items = present ? PluginCard.menu(shown, busy) : List.of(new MenuItem("none", "None", "", "none", false, false));
        var primary = new Nodes.Button(PRIMARY, present && PluginCard.toggleable(shown) ? actionsState : hidden,
            present && shown.needsConsent() ? "Review…" : present && shown.enabled() ? "Disable" : "Enable", "", Nodes.ButtonKind.SECONDARY, PRIMARY, "");
        var more = new Nodes.MenuButton(MORE, present ? actionsState : hidden, "", "chevron.down", Nodes.ButtonKind.SECONDARY, "", items, "More actions");
        var actions = new Nodes.Stack("actions", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(primary, more,
            new Nodes.Text("version", ControlState.READY, present ? shown.version() : " ", Nodes.TextRole.CAPTION)));
        var identity = new Nodes.Stack("identity", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Text("name", ControlState.READY, present ? shown.name() : " ", Nodes.TextRole.DETAIL_TITLE),
            new Nodes.Text("vendor", ControlState.READY, present ? (shown.vendor().isBlank() ? shown.origin() : shown.vendor()) : " ", Nodes.TextRole.DESCRIPTION),
            new Nodes.Button(HOMEPAGE, present && homepage(shown).isPresent() ? ControlState.READY : hidden, "Plugin homepage ↗", "", Nodes.ButtonKind.LINK, HOMEPAGE, ""),
            actions));
        var head = new Nodes.Stack("head", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Custom("icon", ControlState.READY, ICON, Nodes.CustomKind.PREVIEW, present ? shown.name() + " icon" : "Plugin icon"), identity));
        var choice = new Nodes.Choice(SECTION, ControlState.READY, Nodes.ChoiceKind.SEGMENTED,
            List.of(new Option(OVERVIEW, "Overview", Optional.empty()), new Option(DETAILS, "Details", Optional.empty())), section,
            "Plugin information", "");
        return new ViewSpec(new Nodes.Stack("details-header", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(head, choice)));
    }

    /** Everything the pane shows as text, for tests and search: the header, the overview and the details. */
    String text() {
        if (row == null) return "";
        String vendor = row.vendor().isBlank() ? row.origin() : row.vendor();
        return row.name() + " " + row.version() + "\n" + vendor + "\n\n" + overview(row) + "\n\n" + describe(row);
    }

    /** The text the one text area shows now. */
    String shownText() { return text.getText(); }

    JTextArea textArea() { return text; }

    KitView header() { return header; }

    /** The More actions menu as labels, a separator as "-". */
    List<String> menuLabels() {
        if (row == null) return List.of();
        return PluginCard.menu(row, busy).stream().map(item -> item.separator() ? "-" : item.label()).toList();
    }

    boolean homepageShown() { return row != null && homepage(row).isPresent(); }

    void close() {
        try { theme.close(); } catch (Exception ignored) { /* the binding outlives nothing that needs this */ }
        header.close();
        body.close();
    }

    /** The plugin's manifest icon (or Jasper's plugin logo) at its named size: specialized artwork, not a control. */
    private final class IconCanvas extends JComponent {
        Icon icon;
        @Override public Dimension getPreferredSize() {
            int size = metric(DesignMetrics.Role.PLUGIN_DETAIL_ICON);
            return new Dimension(size, size);
        }
        @Override protected void paintComponent(Graphics graphics) { if (icon != null) icon.paintIcon(this, graphics, 0, 0); }
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
