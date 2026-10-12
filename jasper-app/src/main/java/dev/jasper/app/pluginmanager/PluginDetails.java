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
import javax.swing.JTextPane;
import javax.swing.text.StyleConstants;
import javax.swing.text.SimpleAttributeSet;

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
    private final dev.jasper.ui.icons.ReceivingArtwork artwork;
    private String artName="plugin";
    private final JTextPane text = new JTextPane();
    private final AutoCloseable theme;
    private final AutoCloseable typography;
    private PluginRuntime.Row row;
    private boolean busy;
    private String section = OVERVIEW;

    PluginDetails(KitContext kit, Consumer<UiEvent> events) {
        this.kit = kit;
        artwork=new dev.jasper.ui.icons.ReceivingArtwork(kit.icons());
        header = new KitView(kit, headerSpec(), event -> {
            if (event instanceof UiEvent.Selected chosen && chosen.id().equals(SECTION)) {
                chosen.ids().stream().findFirst().ifPresent(this::section);
                return;
            }
            events.accept(event);
        });
        body = new KitView(kit, new ViewSpec(new Nodes.Custom("plugin-text", ControlState.READY, TEXT, Nodes.CustomKind.EDITOR, "Overview")), events);
        // Plain text in a styled pane, so its paragraphs can take the reading line spacing; it wraps at words.
        text.setEditable(false);
        text.setOpaque(false);
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
        typography=dev.jasper.ui.swing.NativeControlStyles.observeTypography(this::restyle);
    }

    JComponent component() { return component; }

    /** Geometry and colours from the module: the former gaps and paddings as named metrics. */
    private void restyle() {
        // The manager page leaves vertical padding to its lanes, so the details add the page's own above and below.
        int edge = metric(DesignMetrics.Role.PLUGIN_DETAILS_EDGE_PADDING)+metric(DesignMetrics.Role.PLUGIN_MANAGER_PADDING);
        int leading=metric(DesignMetrics.Role.PLUGIN_DETAILS_LEADING_PADDING);
        component.setBorder(BorderFactory.createEmptyBorder(edge, leading, edge, metric(DesignMetrics.Role.PLUGIN_DETAILS_TRAILING_PADDING)));
        // The source-sized identity section is also the readable minimum for this split receiver.
        // FillingPage lets Settings' existing horizontal viewport reveal it at constrained widths.
        component.setMinimumSize(new Dimension(leading+metric(DesignMetrics.Role.IDENTITY_SECTION_WIDTH)+metric(DesignMetrics.Role.PLUGIN_DETAILS_TRAILING_PADDING),0));
        ((BorderLayout) component.getLayout()).setVgap(metric(DesignMetrics.Role.PLUGIN_DETAILS_GAP));
        int vertical = metric(DesignMetrics.Role.PLUGIN_TEXT_VERTICAL_PADDING), side = metric(DesignMetrics.Role.PLUGIN_TEXT_SIDE_PADDING);
        text.setBorder(BorderFactory.createEmptyBorder(vertical, side, vertical, side));
        text.setFont(dev.jasper.ui.swing.NativeControlStyles.font(kit,Typography.Role.BODY));
        spacing();
        var palette = kit.theme().semantic();
        text.setForeground(palette.color(SemanticColor.TEXT_PRIMARY));
        text.setCaretColor(text.getForeground());
        text.setSelectionColor(palette.color(SemanticColor.SELECTION_BG));
        text.setSelectedTextColor(palette.color(SemanticColor.TEXT_PRIMARY));
        component.revalidate();
        component.repaint();
    }

    private int metric(DesignMetrics.Role role) { return UIScale.scale(kit.metrics().value(role)); }

    /** Extra room below each line, as a share of the line, so the overview reads as a paragraph. */
    private void spacing() {
        var attributes = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(attributes, kit.metrics().value(DesignMetrics.Role.PLUGIN_TEXT_LINE_SPACING) / 100f);
        // Paragraph attributes (not the shared default style) reach the views, which read them when they change.
        text.getStyledDocument().setParagraphAttributes(0, text.getDocument().getLength(), attributes, false);
    }

    /** Shows {@code shown}, or nothing when it is null; {@code busy} disables the actions. */
    void show(PluginRuntime.Row shown, boolean busy) {
        this.row = shown;
        this.busy = busy;
        component.setVisible(shown != null);
        renderHeader();
        fill();
    }

    private void renderHeader() {
        if(header.isClosed())return;
        artwork.reconcile(()->{
            artName=row==null?"plugin":PluginIcons.name(artwork,row);
            header.update(headerSpec());
            header.setArtworkPresentation("icon",dev.jasper.ui.swing.ArtworkPresentation.IDENTITY_HEADER);
        });
    }

    /** Overview or Details: the chosen text replaces the other in the one text area. */
    void section(String value) {
        if (!value.equals(OVERVIEW) && !value.equals(DETAILS) || value.equals(section)) return;
        section = value;
        renderHeader();
        fill();
    }

    String section() { return section; }

    private void fill() {
        String next = row == null ? "" : section.equals(OVERVIEW) ? overview(row) : describe(row);
        if (!next.equals(text.getText())) {
            text.setText(next);
            spacing();
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
        String status=!present?"":!PluginCard.badge(shown).isEmpty()?PluginCard.badge(shown):shown.state().equals("ACTIVE") && shown.enabled()?"Enabled":stateLabel(shown.state());
        var metadata=new Nodes.Stack("metadata",ControlState.READY,Nodes.LayoutRole.INLINE,List.of(
            new Nodes.Text("vendor",ControlState.READY,present?(shown.vendor().isBlank()?shown.origin():shown.vendor()):" ",Nodes.TextRole.CAPTION),
            new Nodes.Text("version",ControlState.READY,present?"·  Version "+shown.version()+"  ·":" ",Nodes.TextRole.CAPTION),
            // The state's dot stays close to its label.
            new Nodes.Stack("state-line",ControlState.READY,Nodes.LayoutRole.NAVIGATION,List.of(
                new Nodes.StatusDot("state-dot",new ControlState(present,true,false,""),present && shown.enabled() && shown.state().equals("ACTIVE") ? Nodes.StatusKind.SUCCESS : Nodes.StatusKind.WARNING),
                new Nodes.Text("state",ControlState.READY,status,Nodes.TextRole.CAPTION)))));
        var identity = new Nodes.Stack("identity", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Text("name", ControlState.READY, present ? shown.name() : " ", Nodes.TextRole.DETAIL_TITLE),metadata));
        var head = new Nodes.Stack("head", ControlState.READY, Nodes.LayoutRole.NAVIGATION, List.of(
            new Nodes.Icon("icon",new ControlState(true,!present || !PluginCard.greyed(shown),false,""),artName,Nodes.Hue.NEUTRAL,Nodes.IconSize.HERO,present?shown.name():"Plugin"),identity,primary));
        var ancillary=new Nodes.Stack("ancillary",new ControlState(section.equals(DETAILS),true,false,""),Nodes.LayoutRole.NAVIGATION,List.of(
            new Nodes.Button(HOMEPAGE, present && homepage(shown).isPresent() ? ControlState.READY : hidden, "Plugin homepage ↗", "", Nodes.ButtonKind.LINK, HOMEPAGE, ""),more));
        var choice = new Nodes.Choice(SECTION, ControlState.READY, Nodes.ChoiceKind.SEGMENTED,
            List.of(new Option(OVERVIEW, "Overview", Optional.empty()), new Option(DETAILS, "Details", Optional.empty())), section,
            "Plugin information", "");
        return new ViewSpec(new Nodes.Stack("details-header", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(head, new Nodes.Stack("section-lane",ControlState.READY,Nodes.LayoutRole.INLINE,List.of(new Nodes.Card("section-card",ControlState.READY,Nodes.CardKind.SETTINGS,List.of(choice)))), ancillary)));
    }

    /** Everything the pane shows as text, for tests and search: the header, the overview and the details. */
    String text() {
        if (row == null) return "";
        String vendor = row.vendor().isBlank() ? row.origin() : row.vendor();
        return row.name() + " " + row.version() + "\n" + vendor + "\n\n" + overview(row) + "\n\n" + describe(row);
    }

    /** The text the one text area shows now. */
    String shownText() { return text.getText(); }

    JTextPane textArea() { return text; }

    KitView header() { return header; }

    /** The More actions menu as labels, a separator as "-". */
    List<String> menuLabels() {
        if (row == null) return List.of();
        return PluginCard.menu(row, busy).stream().map(item -> item.separator() ? "-" : item.label()).toList();
    }

    boolean homepageShown() { return row != null && homepage(row).isPresent(); }

    void close() {
        try { typography.close(); } catch (Exception ignored) { /* idempotent module subscription */ }
        try { theme.close(); } catch (Exception ignored) { /* the binding outlives nothing that needs this */ }
        try { header.close(); } finally { try {body.close();} finally {artwork.close();} }
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
