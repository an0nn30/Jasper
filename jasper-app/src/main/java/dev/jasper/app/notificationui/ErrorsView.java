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
import java.awt.CardLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

/**
 * The Errors window's content (spec section 5): this session's incidents, most recently seen first, beside the
 * selected one's source and operation, sightings, message and stack trace, with Copy, Disable Plugin (a plugin's
 * incidents only), Open Log Folder and Clear. A layout-only shell (split, detail cards, button row; former geometry as
 * named metrics) of chrome kit views: a kit list, kit text, kit buttons and read-only Custom EDITOR message and trace
 * (ruling G4, so they stay selectable and scrollable). Every text is literal. EDT only; {@link #close} stops it listening.
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

    static final String LIST = "errors", HEADER = "header", SIGHTINGS = "sightings";
    static final String COPY = "copy", DISABLE = "disable", LOG_FOLDER = "log-folder", CLEAR = "clear";
    private static final String EMPTY = "empty", DETAIL = "detail", MESSAGE = "errors.message", TRACE = "errors.trace";
    /** The list's share of extra width when the window grows, as before. */
    private static final double LIST_SHARE = 0.35;
    private final KitContext kit;
    private final Incidents incidents;
    private final Hooks hooks;
    private final DateTimeFormatter time;
    private final KitView listView, headerView, sightingsView, messageView, traceView, emptyView, copyView, disableView, logView, clearView;
    /** The former heading column: header, a 2 px gap, sightings, an 8 px gap, the message. */
    private final JPanel heading = new JPanel();
    private final JPanel leadingButtons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING, 0, 0));
    private final JPanel trailingButtons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.TRAILING, 0, 0));
    private final CardLayout cards = new CardLayout();
    private final JPanel detail = new JPanel(cards), details = new JPanel(new BorderLayout()), buttons = new JPanel(new BorderLayout());
    private final JSplitPane split;
    private final JTextArea message = new JTextArea(), trace = new JTextArea();
    private final Subscription listening;
    private final AutoCloseable theme;
    private List<Incident> listed = List.of();
    private UUID selectedId, shown;
    private boolean closed, showingEmpty = true;

    /**
     * @param kit the chrome kit context
     * @param incidents the store it lists and clears
     * @param hooks what it asks of the application
     * @param zone the zone sightings are shown in
     */
    public ErrorsView(KitContext kit, Incidents incidents, Hooks hooks, ZoneId zone) {
        super(new BorderLayout());
        this.kit = Objects.requireNonNull(kit, "kit");
        this.incidents = Objects.requireNonNull(incidents, "incidents");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone);
        listView = new KitView(kit, listSpec(), this::event);
        listView.getAccessibleContext().setAccessibleName("Errors");
        message.setRows(kit.metrics().value(DesignMetrics.Role.ERRORS_MESSAGE_ROWS));
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.getAccessibleContext().setAccessibleName("Message");
        trace.getAccessibleContext().setAccessibleName("Stack trace");
        for (var area : List.of(message, trace)) { area.setEditable(false); area.setOpaque(false); }
        headerView = new KitView(kit, textSpec(HEADER, " ", Nodes.TextRole.BODY_STRONG), this::event);
        sightingsView = new KitView(kit, textSpec(SIGHTINGS, " ", Nodes.TextRole.DESCRIPTION), this::event);
        messageView = new KitView(kit, new ViewSpec(new Nodes.Custom("message", ControlState.READY, MESSAGE, Nodes.CustomKind.EDITOR, "Message")), this::event);
        messageView.registerContent(MESSAGE, scroll(message), () -> { });
        heading.setLayout(new javax.swing.BoxLayout(heading, javax.swing.BoxLayout.Y_AXIS));
        heading.setOpaque(false);
        traceView = new KitView(kit, new ViewSpec(new Nodes.Custom("trace", ControlState.READY, TRACE, Nodes.CustomKind.EDITOR, "Stack trace")), this::event);
        traceView.registerContent(TRACE, scroll(trace), () -> { });
        emptyView = new KitView(kit, new ViewSpec(new Nodes.Empty("no-errors", ControlState.READY,
            new Nodes.Icon("no-errors-icon", ControlState.READY, "check", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "No errors"), "No errors", "", List.of())), this::event);
        copyView = new KitView(kit, buttonSpec(COPY, "Copy", new ControlState(true, false, false, "")), this::event);
        disableView = new KitView(kit, buttonSpec(DISABLE, "Disable Plugin", new ControlState(false, true, false, "")), this::event);
        logView = new KitView(kit, buttonSpec(LOG_FOLDER, "Open Log Folder", ControlState.READY), this::event);
        clearView = new KitView(kit, buttonSpec(CLEAR, "Clear", new ControlState(true, false, false, "")), this::event);
        disableView.setVisible(false);
        leadingButtons.setOpaque(false);
        leadingButtons.add(copyView);
        leadingButtons.add(disableView);
        trailingButtons.setOpaque(false);
        trailingButtons.add(logView);
        trailingButtons.add(clearView);

        details.setOpaque(false);
        details.add(heading, BorderLayout.NORTH);
        details.add(traceView, BorderLayout.CENTER);
        detail.setOpaque(false);
        detail.add(emptyView, EMPTY);
        detail.add(details, DETAIL);
        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listView, detail) {
            @Override public void updateUI() {
                // An unpainted resize gutter, as in the chooser; Swing keeps its keyboard and drag handling.
                setUI(new BasicSplitPaneUI() {
                    @Override public BasicSplitPaneDivider createDefaultDivider() {
                        return new BasicSplitPaneDivider(this) { @Override public void paint(Graphics graphics) { } };
                    }
                });
                setOpaque(false);
            }
        };
        split.setResizeWeight(LIST_SHARE);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setContinuousLayout(true);
        split.setDividerLocation(metric(DesignMetrics.Role.ERRORS_LIST_WIDTH));
        add(split, BorderLayout.CENTER);
        buttons.setOpaque(false);
        buttons.add(leadingButtons, BorderLayout.WEST);
        buttons.add(trailingButtons, BorderLayout.EAST);
        add(buttons, BorderLayout.SOUTH);
        theme = kit.binding().subscribe(pair -> restyle());
        restyle();
        listening = incidents.onChange(change -> refresh());
        refresh();
    }

    private static JScrollPane scroll(JTextArea area) {
        var scroll = new JScrollPane(area, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        return scroll;
    }

    private int metric(DesignMetrics.Role role) { return NotificationStyle.metric(kit, role); }

    /** The former paddings and gaps from named metrics; the read-only texts take the module's body and mono roles. */
    private void restyle() {
        int vertical = metric(DesignMetrics.Role.ERRORS_DETAIL_VERTICAL_PADDING), side = metric(DesignMetrics.Role.ERRORS_DETAIL_SIDE_PADDING);
        details.setBorder(BorderFactory.createEmptyBorder(vertical, side, vertical, side));
        ((BorderLayout) details.getLayout()).setVgap(metric(DesignMetrics.Role.ERRORS_DETAIL_GAP));
        int buttonsVertical = metric(DesignMetrics.Role.ERRORS_BUTTONS_VERTICAL_PADDING), buttonsSide = metric(DesignMetrics.Role.ERRORS_BUTTONS_SIDE_PADDING);
        buttons.setBorder(BorderFactory.createEmptyBorder(buttonsVertical, buttonsSide, buttonsVertical, buttonsSide));
        // The former FlowLayout gap between the buttons of each group.
        int buttonGap = metric(DesignMetrics.Role.ERRORS_BUTTON_GAP);
        ((java.awt.FlowLayout) leadingButtons.getLayout()).setHgap(buttonGap);
        ((java.awt.FlowLayout) trailingButtons.getLayout()).setHgap(buttonGap);
        // The former heading struts: 2 px under the header, 8 px above the message.
        heading.removeAll();
        for (java.awt.Component part : List.of(headerView, javax.swing.Box.createVerticalStrut(metric(DesignMetrics.Role.ERRORS_SIGHTINGS_GAP)), sightingsView,
                javax.swing.Box.createVerticalStrut(metric(DesignMetrics.Role.ERRORS_MESSAGE_GAP)), messageView)) {
            if (part instanceof JComponent component) component.setAlignmentX(LEFT_ALIGNMENT);
            heading.add(part);
        }
        split.setDividerSize(metric(DesignMetrics.Role.WORKSPACE_GUTTER));
        message.setFont(NotificationStyle.font(kit, Typography.Role.BODY));
        trace.setFont(NotificationStyle.font(kit, Typography.Role.MONO));
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
                .ifPresent(id -> { selectedId = id; listView.update(listSpec()); showSelected(); });
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
        listView.update(listSpec());
        showSelected();
    }

    /** Scrolls the selected row into view again; the window calls this once it has been laid out. */
    public void revealSelection() {
        if (closed || selectedId == null || indexOf(selectedId) < 0) return;
        listView.reveal(summaryId(selectedId));
    }

    /** Stops listening and closes the kit views; the window calls this when it closes. Idempotent. */
    public void close() {
        if (closed) return;
        closed = true;
        listening.close();
        try { theme.close(); } catch (Exception ignored) { /* the binding outlives the view */ }
        for (var view : List.of(listView, headerView, sightingsView, messageView, traceView, emptyView, copyView, disableView, logView, clearView)) view.close();
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
        listView.update(listSpec());
        showSelected();
    }

    private int indexOf(UUID id) {
        if (id == null) return -1;
        for (int i = 0; i < listed.size(); i++) if (listed.get(i).id().equals(id)) return i;
        return -1;
    }

    private void showSelected() {
        Optional<Incident> chosen = selected();
        boolean plugin = chosen.map(Incident::fromPlugin).orElse(false);
        copyView.update(buttonSpec(COPY, "Copy", new ControlState(true, chosen.isPresent(), false, "")));
        disableView.update(buttonSpec(DISABLE, "Disable Plugin", new ControlState(plugin, true, false, "")));
        disableView.setVisible(plugin);
        clearView.update(buttonSpec(CLEAR, "Clear", new ControlState(true, !listed.isEmpty(), false, "")));
        if (chosen.isEmpty()) {
            shown = null;
            showingEmpty = true;
            cards.show(detail, EMPTY);
            return;
        }
        Incident incident = chosen.get();
        headerView.update(textSpec(HEADER, sourceName(incident) + " · " + incident.operation(), Nodes.TextRole.BODY_STRONG));
        sightingsView.update(textSpec(SIGHTINGS, sightingsOf(incident), Nodes.TextRole.DESCRIPTION));
        if (!incident.id().equals(shown)) {
            // The first sighting's text never changes, so a long trace is set once per selection.
            message.setText(incident.message().isEmpty() ? "No message" : incident.message());
            message.setCaretPosition(0);
            trace.setText(incident.trace());
            trace.setCaretPosition(0);
            shown = incident.id();
        }
        showingEmpty = false;
        cards.show(detail, DETAIL);
    }

    private static String summaryId(UUID id) { return "summary:" + id; }

    private ViewSpec listSpec() {
        var items = new ArrayList<ListItem>();
        for (Incident incident : listed) {
            String id = incident.id().toString();
            items.add(new ListItem(id, Optional.of(new Nodes.Icon("icon:" + id, ControlState.READY, "error", Nodes.Hue.NONE, Nodes.IconSize.RAIL, "Error",
                Optional.of(Nodes.Severity.DANGER))), List.of(new Nodes.Text(summaryId(incident.id()), ControlState.READY, incident.summary(), Nodes.TextRole.BODY)),
                List.of(new Nodes.Text("detail:" + id, ControlState.READY, detailLine(incident), Nodes.TextRole.CAPTION)), List.of()));
        }
        return new ViewSpec(new Nodes.ItemList(LIST, ControlState.READY, Nodes.ListKind.DOUBLE, items,
            selectedId == null ? Set.of() : Set.of(selectedId.toString()), "", List.of()));
    }

    private static ViewSpec textSpec(String id, String text, Nodes.TextRole role) {
        return new ViewSpec(new Nodes.Text(id, ControlState.READY, text, role));
    }

    private static ViewSpec buttonSpec(String id, String label, ControlState state) {
        return new ViewSpec(new Nodes.Button(id, state, label, "", Nodes.ButtonKind.SECONDARY, id, ""));
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
    String headerText() { return showingEmpty ? "" : headingText(0); }
    JLabel headerLabel() { return (JLabel) NotificationCard.find(headerView, HEADER); }
    JLabel sightingsLabel() { return (JLabel) NotificationCard.find(sightingsView, SIGHTINGS); }
    KitView messageView() { return messageView; }
    JPanel headingColumn() { return heading; }
    String sightingsText() { return showingEmpty ? "" : headingText(1); }
    String messageText() { return message.getText(); }
    String traceText() { return trace.getText(); }
    Font traceFont() { return trace.getFont(); }
    boolean showingEmpty() { return showingEmpty; }
    AbstractButton copyButton() { return (AbstractButton) NotificationCard.find(copyView, COPY); }
    AbstractButton disableButton() { return (AbstractButton) NotificationCard.find(disableView, DISABLE); }
    AbstractButton logFolderButton() { return (AbstractButton) NotificationCard.find(logView, LOG_FOLDER); }
    AbstractButton clearButton() { return (AbstractButton) NotificationCard.find(clearView, CLEAR); }
    List<KitView> buttonViews() { return List.of(copyView, disableView, logView, clearView); }
    KitView listView() { return listView; }
    JTextArea messageArea() { return message; }
    JTextArea traceArea() { return trace; }

    private String headingText(int index) {
        return ((Nodes.Text) (index == 0 ? headerView : sightingsView).spec().root()).text();
    }

    private ListItem item(int index) { return ((Nodes.ItemList) listView.spec().root()).items().get(index); }
    String rowSummary(int index) { return ((Nodes.Text) item(index).title().getFirst()).text(); }
    String rowDetail(int index) { return ((Nodes.Text) item(index).subtitle().getFirst()).text(); }

    /** The row's mounted summary and detail labels (the list is laid out first, so the row is mounted). */
    List<JLabel> rowLabels(int index) {
        if (listView.getWidth() == 0) listView.setSize(metric(DesignMetrics.Role.ERRORS_LIST_WIDTH), metric(DesignMetrics.Role.ERRORS_LIST_WIDTH));
        layoutTree(listView);
        var item = item(index);
        return List.of((JLabel) NotificationCard.find(listView, item.title().getFirst().id()), (JLabel) NotificationCard.find(listView, item.subtitle().getFirst().id()));
    }

    private static void layoutTree(java.awt.Component component) {
        component.doLayout();
        if (component instanceof java.awt.Container container) for (var child : container.getComponents()) layoutTree(child);
    }
}
