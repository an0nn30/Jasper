package dev.jasper.app.notificationui;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.Incident;
import dev.jasper.app.notifications.IncidentReport;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.ListItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.BorderLayout;
import java.awt.Font;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * The Errors tab's content (spec section 5; workspace tabs spec 6.6): this session's incidents, most recently seen
 * first, beside the selected one's detail. One kit view on the workspace route composes it as a manager (the Vault
 * manager's arrangement without its rail) and paints its surface, as a plugin's content tab does: a list column of
 * incidents; a detail column with the incident's heading (its summary, source and operation, then Disable Plugin for a
 * plugin's incident and Copy), its message and stack trace each in a detail card and when it was seen; and the footer
 * band with the count, Open Log Folder and Clear. The tab strip carries its title and closes it. The message and
 * trace are read-only specialized text (Custom EDITOR) so they stay selectable and scrollable; the trace card shows at
 * most {@code ERRORS_TRACE_ROWS} lines before it scrolls. With no incidents the detail column is the standard empty
 * state. Every text is literal. EDT only; {@link #close} stops it listening.
 */
public final class ErrorsView extends JPanel {
    /**
     * What the view asks of the application.
     *
     * @param pluginName a plugin's display name by id
     * @param pluginVersion a plugin's version by id, empty when unknown
     * @param environment what Copy's report names
     * @param copy puts a report on the clipboard
     * @param disablePlugin disables a plugin by id from the next launch
     * @param openLogFolder shows Jasper's log folder
     */
    public record Hooks(Function<String, String> pluginName, Function<String, String> pluginVersion,
                        IncidentReport.Environment environment, Consumer<String> copy, Consumer<String> disablePlugin,
                        Runnable openLogFolder) {
        public Hooks {
            Objects.requireNonNull(pluginName, "pluginName");
            Objects.requireNonNull(pluginVersion, "pluginVersion");
            Objects.requireNonNull(environment, "environment");
            Objects.requireNonNull(copy, "copy");
            Objects.requireNonNull(disablePlugin, "disablePlugin");
            Objects.requireNonNull(openLogFolder, "openLogFolder");
        }
    }

    static final String LIST = "errors", TITLE = "detail.title", HEADER = "detail.source", SIGHTINGS = "detail.sightings";
    static final String COPY = "copy", DISABLE = "disable", LOG_FOLDER = "log-folder", CLEAR = "clear";
    static final String EMPTY = "no-errors", STATUS = "status.count";
    private static final String ROOT = "errors.window", MESSAGE = "errors.message", TRACE = "errors.trace";
    private final KitContext kit;
    private final Incidents incidents;
    private final Hooks hooks;
    private final DateTimeFormatter time;
    private final KitView view;
    private final JTextArea message = new JTextArea(), trace = new JTextArea();
    private final JScrollPane traceScroll;
    private final Subscription listening;
    private final AutoCloseable theme;
    private final AutoCloseable typography;
    private List<Incident> listed = List.of();
    private UUID selectedId, shown;
    /** Whether the message and trace widgets are registered with their mounted Custom slots. */
    private boolean messageMounted, traceMounted;
    private boolean closed;

    /**
     * @param kit the workspace route's kit context: the view is a content tab's
     * @param incidents the store it lists and clears
     * @param hooks what it asks of the application
     * @param zone the zone sightings are shown in
     * @throws IllegalArgumentException when {@code kit} is not on the workspace route
     */
    public ErrorsView(KitContext kit, Incidents incidents, Hooks hooks, ZoneId zone) {
        super(new BorderLayout());
        this.kit = Objects.requireNonNull(kit, "kit");
        if (kit.route() != dev.jasper.ui.theme.ThemeRoute.WORKSPACE)
            throw new IllegalArgumentException("The Errors tab paints from the workspace route, not " + kit.route());
        this.incidents = Objects.requireNonNull(incidents, "incidents");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone);
        setOpaque(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.getAccessibleContext().setAccessibleName("Message");
        trace.getAccessibleContext().setAccessibleName("Stack trace");
        // A narrow preferred width: the card gives the trace its width and the scroll pane shows the long lines.
        trace.setColumns(1);
        for (var area : List.of(message, trace)) { area.setEditable(false); area.setOpaque(false); area.setBorder(BorderFactory.createEmptyBorder()); }
        traceScroll = new JScrollPane(trace, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        traceScroll.setBorder(BorderFactory.createEmptyBorder());
        traceScroll.setViewportBorder(BorderFactory.createEmptyBorder());
        traceScroll.setOpaque(false);
        traceScroll.getViewport().setOpaque(false);
        listed = List.copyOf(incidents.list());
        if (!listed.isEmpty()) selectedId = listed.getFirst().id();
        view = new KitView(kit, spec(), this::event);
        // A content tab paints its route's surface, as a plugin's content tab does.
        view.paintsSurface(true);
        view.getAccessibleContext().setAccessibleName("Errors");
        add(view, BorderLayout.CENTER);
        theme = kit.binding().subscribe(pair -> restyle());
        restyle();
        listening = incidents.onChange(change -> refresh());
        refresh();
        typography = dev.jasper.ui.swing.NativeStyles.installed().observeTypography(this::restyle);
    }

    /** The read-only texts take the module's body and mono roles and the theme's text and selection colours. */
    private void restyle() {
        if (closed) return;
        message.setFont(NotificationStyle.font(kit, Typography.Role.BODY));
        trace.setFont(NotificationStyle.font(kit, Typography.Role.MONO_SMALL));
        for (var area : List.of(message, trace)) {
            area.setForeground(NotificationStyle.color(kit, SemanticColor.TEXT_PRIMARY));
            area.setCaretColor(area.getForeground());
            area.setSelectionColor(NotificationStyle.color(kit, SemanticColor.SELECTION_BG));
            area.setSelectedTextColor(NotificationStyle.color(kit, SemanticColor.TEXT_PRIMARY));
        }
        revalidate();
        repaint();
    }

    private void event(UiEvent event) {
        if (closed) return;
        switch (event) {
            case UiEvent.Selected chosen when chosen.id().equals(LIST) -> chosen.ids().stream().findFirst().map(UUID::fromString)
                .ifPresent(id -> { selectedId = id; render(); });
            case UiEvent.Action action -> {
                switch (action.id()) {
                    case COPY -> selected().ifPresent(incident -> hooks.copy().accept(report(incident)));
                    case DISABLE -> selected().filter(Incident::fromPlugin).ifPresent(incident -> hooks.disablePlugin().accept(incident.source()));
                    case LOG_FOLDER -> hooks.openLogFolder().run();
                    case CLEAR -> incidents.clear();
                    default -> { }
                }
            }
            default -> { }
        }
    }

    /**
     * Selects an incident when it is listed; Show more… uses this. An incident no longer listed is ignored.
     *
     * @param id the incident
     */
    public void select(UUID id) {
        if (closed || indexOf(id) < 0) return;
        selectedId = id;
        render();
    }

    /** Scrolls the selected row into view again; the window calls this once it has been laid out. */
    public void revealSelection() {
        if (closed || selectedId == null || indexOf(selectedId) < 0) return;
        view.reveal(summaryId(selectedId));
    }

    /** Stops listening and closes the kit view; the window calls this when it closes. Idempotent. */
    public void close() {
        if (closed) return;
        closed = true;
        listening.close();
        try { typography.close(); } catch (Exception ignored) { /* idempotent module subscription */ }
        try { theme.close(); } catch (Exception ignored) { /* the binding outlives the view */ }
        view.close();
    }

    Optional<Incident> selected() {
        int index = indexOf(selectedId);
        return index < 0 ? Optional.empty() : Optional.of(listed.get(index));
    }

    /** Rebuilds the list, keeping the user's selection, else selecting the newest. */
    private void refresh() {
        if (closed) return;
        listed = List.copyOf(incidents.list());
        if (indexOf(selectedId) < 0) selectedId = listed.isEmpty() ? null : listed.getFirst().id();
        render();
    }

    private int indexOf(UUID id) {
        if (id == null) return -1;
        for (int i = 0; i < listed.size(); i++) if (listed.get(i).id().equals(id)) return i;
        return -1;
    }

    /** Shows the list and the selection; a newly selected incident's texts are set once, as its first sighting never changes. */
    private void render() {
        if (closed) return;
        Optional<Incident> chosen = selected();
        if (chosen.isEmpty()) shown = null;
        else if (!chosen.get().id().equals(shown)) {
            Incident incident = chosen.get();
            message.setText(incident.message().isEmpty() ? "No message" : incident.message());
            message.setCaretPosition(0);
            trace.setText(incident.trace());
            trace.setCaretPosition(0);
            trace.setRows(Math.clamp(trace.getLineCount(), DesignMetrics.NATIVE_BODY_VISIBLE_UNITS, kit.metrics().value(DesignMetrics.Role.ERRORS_TRACE_ROWS)));
            shown = incident.id();
        }
        view.update(spec());
        // The detail's Custom slots come and go with the selection; the same widgets (and their selection) return to them.
        if (chosen.isPresent() && !messageMounted) { view.registerContent(MESSAGE, message, () -> messageMounted = false); messageMounted = true; }
        if (chosen.isPresent() && !traceMounted) { view.registerContent(TRACE, traceScroll, () -> traceMounted = false); traceMounted = true; }
    }

    private static String summaryId(UUID id) { return "summary:" + id; }

    private ViewSpec spec() {
        boolean any = !listed.isEmpty();
        var listPanel = new Nodes.Panel("errors.list", new ControlState(any, true, false, ""), hiddenHeader("errors.list.header", "Errors"), Optional.empty(),
            incidentList(), Optional.empty());
        var detailPanel = new Nodes.Panel("errors.detail", ControlState.READY, hiddenHeader("errors.detail.header", "Details"), Optional.empty(),
            detail(), Optional.empty());
        var columns = new Nodes.Split("errors.columns", ControlState.READY, Nodes.SplitKind.SIDEBAR_DETAIL, List.of(listPanel, detailPanel));
        var actions = List.of(
            button(LOG_FOLDER, "Open Log Folder", ControlState.READY, Nodes.ButtonKind.SECONDARY),
            button(CLEAR, "Clear", new ControlState(true, any, false, ""), Nodes.ButtonKind.SECONDARY));
        // The WINDOW form's full-bleed body and pinned footer band; the kit draws no title in it ("Errors" names it for
        // assistive technology) and nothing cancels it: Escape leaves the tab open.
        return new ViewSpec(new Nodes.Form(ROOT, ControlState.READY, Nodes.FormKind.WINDOW, "Errors", Optional.empty(), List.of(columns),
            Optional.of(status()), actions, "", Nodes.FormSubmission.NONE));
    }

    private static Nodes.Header hiddenHeader(String id, String title) {
        return new Nodes.Header(id, new ControlState(false, true, false, ""), title, OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of());
    }

    private Nodes.Node incidentList() {
        var items = new ArrayList<ListItem>();
        for (Incident incident : listed) {
            String id = incident.id().toString();
            items.add(new ListItem(id, Optional.of(new Nodes.Icon("icon:" + id, ControlState.READY, "error", Nodes.Hue.NONE, Nodes.IconSize.RAIL, "Error",
                Optional.of(Nodes.Severity.DANGER))), List.of(new Nodes.Text(summaryId(incident.id()), ControlState.READY, incident.summary(), Nodes.TextRole.BODY)),
                List.of(new Nodes.Text("detail:" + id, ControlState.READY, detailLine(incident), Nodes.TextRole.CAPTION)), List.of()));
        }
        return new Nodes.ItemList(LIST, ControlState.READY, Nodes.ListKind.DOUBLE, items,
            selectedId == null ? Set.of() : Set.of(selectedId.toString()), "", List.of());
    }

    /** The selected incident in the manager detail arrangement, or the standard empty state when nothing is listed. */
    private Nodes.Node detail() {
        Optional<Incident> chosen = selected();
        if (chosen.isEmpty()) return new Nodes.Empty(EMPTY, ControlState.READY,
            new Nodes.Icon(EMPTY + ".icon", ControlState.READY, "check", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "No errors"), "No Errors",
            "Problems in Jasper and its plugins during this session are listed here.", List.of());
        Incident incident = chosen.get();
        boolean plugin = incident.fromPlugin();
        var heading = new Nodes.Stack("detail.heading", ControlState.READY, Nodes.LayoutRole.NAVIGATION, List.of(
            new Nodes.Icon("detail.icon", ControlState.READY, "error", Nodes.Hue.NEUTRAL, Nodes.IconSize.PROMPT, "Error", Optional.of(Nodes.Severity.DANGER)),
            new Nodes.Stack("detail.identity", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
                text(TITLE, incident.summary(), Nodes.TextRole.DETAIL_TITLE),
                text(HEADER, sourceName(incident) + " · " + incident.operation(), Nodes.TextRole.DESCRIPTION))),
            button(DISABLE, "Disable Plugin", new ControlState(plugin, plugin, false, ""), Nodes.ButtonKind.SECONDARY),
            button(COPY, "Copy", ControlState.READY, Nodes.ButtonKind.SECONDARY)));
        return new Nodes.Stack("detail.content", ControlState.READY, Nodes.LayoutRole.DETAIL, List.of(heading,
            card("message", "Message", new Nodes.Custom("message", ControlState.READY, MESSAGE, Nodes.CustomKind.EDITOR, "Message")),
            card("trace", "Stack Trace", new Nodes.Custom("trace", ControlState.READY, TRACE, Nodes.CustomKind.EDITOR, "Stack trace")),
            text(SIGHTINGS, sightingsOf(incident), Nodes.TextRole.CAPTION)));
    }

    /** A detail card holding one captioned value, as the Vault manager's detail rows. */
    private static Nodes.Card card(String id, String caption, Nodes.Node value) {
        return new Nodes.Card("detail." + id + ".card", ControlState.READY, Nodes.CardKind.FORM, List.of(
            new Nodes.Stack("detail." + id + ".row", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
                text("detail." + id + ".label", caption, Nodes.TextRole.CAPTION), value))));
    }

    /** The footer band's status: how many incidents this session has, after the error glyph. */
    private Nodes.Node status() {
        return new Nodes.Stack("status", ControlState.READY, Nodes.LayoutRole.FOOTER, List.of(
            new Nodes.Stack("status.group", ControlState.READY, Nodes.LayoutRole.NAVIGATION, List.of(
                new Nodes.Icon("status.icon", ControlState.READY, listed.isEmpty() ? "check" : "error", Nodes.Hue.NEUTRAL, Nodes.IconSize.CAPTION, "Errors"),
                text(STATUS, statusLine(), Nodes.TextRole.CAPTION)))));
    }

    private String statusLine() {
        int count = listed.size();
        return count == 0 ? "No errors this session" : count == 1 ? "1 error this session" : count + " errors this session";
    }

    private static Nodes.Text text(String id, String text, Nodes.TextRole role) {
        return new Nodes.Text(id, ControlState.READY, text, role);
    }

    private static Nodes.Button button(String id, String label, ControlState state, Nodes.ButtonKind kind) {
        return new Nodes.Button(id, state, label, "", kind, id, "");
    }

    private String sourceName(Incident incident) {
        return incident.fromPlugin() ? hooks.pluginName().apply(incident.source()) : "Jasper";
    }

    private String sightingsOf(Incident incident) {
        if (incident.count() == 1) return "Seen at " + time.format(incident.firstSeen());
        return "First seen " + time.format(incident.firstSeen()) + " · last seen " + time.format(incident.lastSeen())
            + " · " + incident.count() + " times";
    }

    private String detailLine(Incident incident) {
        return sourceName(incident) + (incident.count() > 1 ? " · ×" + incident.count() : "") + " · "
            + time.format(incident.lastSeen());
    }

    private String report(Incident incident) {
        String version = incident.fromPlugin() ? hooks.pluginVersion().apply(incident.source()) : "";
        return IncidentReport.text(incident, version == null ? "" : version, hooks.environment());
    }

    List<Incident> listed() { return listed; }
    String titleText() { return selected().isEmpty() ? "" : textOf(TITLE); }
    String headerText() { return selected().isEmpty() ? "" : textOf(HEADER); }
    String sightingsText() { return selected().isEmpty() ? "" : textOf(SIGHTINGS); }
    JLabel headerLabel() { return (JLabel) NotificationCard.find(view, HEADER); }
    JLabel titleLabel() { return (JLabel) NotificationCard.find(view, TITLE); }
    JLabel sightingsLabel() { return (JLabel) NotificationCard.find(view, SIGHTINGS); }
    String statusText() { return textOf(STATUS); }
    String messageText() { return message.getText(); }
    String traceText() { return trace.getText(); }
    Font traceFont() { return trace.getFont(); }
    boolean showingEmpty() { return selected().isEmpty(); }
    AbstractButton copyButton() { return (AbstractButton) NotificationCard.find(view, COPY); }
    AbstractButton disableButton() { return (AbstractButton) NotificationCard.find(view, DISABLE); }
    AbstractButton logFolderButton() { return (AbstractButton) NotificationCard.find(view, LOG_FOLDER); }
    AbstractButton clearButton() { return (AbstractButton) NotificationCard.find(view, CLEAR); }
    KitView kitView() { return view; }
    JTextArea messageArea() { return message; }
    JTextArea traceArea() { return trace; }
    JComponent part(String id) { return NotificationCard.find(view, id); }

    private String textOf(String id) {
        Optional<Incident> chosen = selected();
        return switch (id) {
            case TITLE -> chosen.map(Incident::summary).orElse("");
            case HEADER -> chosen.map(incident -> sourceName(incident) + " · " + incident.operation()).orElse("");
            case SIGHTINGS -> chosen.map(this::sightingsOf).orElse("");
            case STATUS -> statusLine();
            default -> "";
        };
    }

    /** The incident list as the kit view was last given it: the form's split, its list column's body. */
    Nodes.ItemList listNode() {
        var form = (Nodes.Form) view.spec().root();
        var columns = (Nodes.Split) form.sections().getFirst();
        return (Nodes.ItemList) ((Nodes.Panel) columns.children().getFirst()).body();
    }

    private ListItem item(int index) { return listNode().items().get(index); }
    String rowSummary(int index) { return ((Nodes.Text) item(index).title().getFirst()).text(); }
    String rowDetail(int index) { return ((Nodes.Text) item(index).subtitle().getFirst()).text(); }

    /** The row's mounted summary and detail labels (the view is laid out first, so the row is mounted). */
    List<JLabel> rowLabels(int index) {
        if (getWidth() == 0) setSize(900, 560);
        for (int i = 0; i < 3; i++) layoutTree(this);
        var item = item(index);
        return List.of((JLabel) NotificationCard.find(view, item.title().getFirst().id()), (JLabel) NotificationCard.find(view, item.subtitle().getFirst().id()));
    }

    private static void layoutTree(java.awt.Component component) {
        component.doLayout();
        if (component instanceof java.awt.Container container) for (var child : container.getComponents()) layoutTree(child);
    }
}
