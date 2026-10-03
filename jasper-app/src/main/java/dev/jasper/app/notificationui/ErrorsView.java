package dev.jasper.app.notificationui;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.Incident;
import dev.jasper.app.notifications.IncidentReport;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.platform.AppIcons;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

/**
 * The Errors window's content (spec section 5): this session's incidents, newest first, beside the selected one's
 * source and operation, sightings, message and stack trace, with Copy, Disable Plugin (a plugin's incidents only),
 * Open Log Folder and Clear. Every text is plain. EDT only; {@link #close} stops it listening.
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

    private static final String EMPTY = "empty";
    private static final String DETAIL = "detail";
    private final Incidents incidents;
    private final Hooks hooks;
    private final DateTimeFormatter time;
    private final DefaultListModel<Incident> model = new DefaultListModel<>();
    private final JList<Incident> list = new JList<>(model) {
        // Rows take the list's width, so a long summary ends in an ellipsis instead of widening the list.
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
    };
    private final CardLayout cards = new CardLayout();
    private final JPanel detail = new JPanel(cards);
    private final JLabel header = plain();
    private final JLabel sightings = plain();
    private final ReadOnlyText message = new ReadOnlyText(false);
    private final ReadOnlyText trace = new ReadOnlyText(true);
    private final JButton copy = new JButton("Copy");
    private final JButton disable = new JButton("Disable Plugin");
    private final JButton logFolder = new JButton("Open Log Folder");
    private final JButton clear = new JButton("Clear");
    private final Subscription listening;
    private UUID shown;
    private boolean refreshing;
    private boolean closed;

    /**
     * @param incidents the store it lists and clears
     * @param hooks what it asks of the application
     * @param zone the zone sightings are shown in
     */
    public ErrorsView(Incidents incidents, Hooks hooks, ZoneId zone) {
        super(new BorderLayout());
        this.incidents = Objects.requireNonNull(incidents, "incidents");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Row());
        list.getAccessibleContext().setAccessibleName("Errors");
        list.addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) showSelected(); });
        var listScroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        header.setFont(header.getFont().deriveFont(Font.BOLD));
        header.setAlignmentX(LEFT_ALIGNMENT);
        sightings.setForeground(NotificationStyle.color("Jasper.mutedForeground", Color.GRAY));
        sightings.setAlignmentX(LEFT_ALIGNMENT);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setRows(3);
        message.getAccessibleContext().setAccessibleName("Message");
        trace.getAccessibleContext().setAccessibleName("Stack trace");
        var messageScroll = new JScrollPane(message, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        messageScroll.setAlignmentX(LEFT_ALIGNMENT);
        var heading = new JPanel();
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.setOpaque(false);
        heading.add(header);
        heading.add(Box.createVerticalStrut(UIScale.scale(2)));
        heading.add(sightings);
        heading.add(Box.createVerticalStrut(UIScale.scale(8)));
        heading.add(messageScroll);
        var details = new JPanel(new BorderLayout(0, UIScale.scale(8)));
        details.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        details.add(heading, BorderLayout.NORTH);
        details.add(new JScrollPane(trace), BorderLayout.CENTER);
        JLabel empty = plain();
        empty.setText("No errors");
        empty.setHorizontalAlignment(SwingConstants.CENTER);
        empty.setForeground(NotificationStyle.color("Jasper.mutedForeground", Color.GRAY));
        detail.add(empty, EMPTY);
        detail.add(details, DETAIL);

        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, detail);
        split.setResizeWeight(0.35);
        split.setDividerLocation(UIScale.scale(320));
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);

        var leading = new JPanel(new FlowLayout(FlowLayout.LEADING, UIScale.scale(6), 0));
        leading.add(copy);
        leading.add(disable);
        var trailing = new JPanel(new FlowLayout(FlowLayout.TRAILING, UIScale.scale(6), 0));
        trailing.add(logFolder);
        trailing.add(clear);
        var buttons = new JPanel(new BorderLayout());
        buttons.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));
        buttons.add(leading, BorderLayout.WEST);
        buttons.add(trailing, BorderLayout.EAST);
        add(buttons, BorderLayout.SOUTH);

        copy.addActionListener(event -> selected().ifPresent(incident -> hooks.copy().accept(report(incident))));
        disable.addActionListener(event -> selected().filter(Incident::fromPlugin)
            .ifPresent(incident -> hooks.disablePlugin().accept(incident.source())));
        logFolder.addActionListener(event -> hooks.openLogFolder().run());
        clear.addActionListener(event -> incidents.clear());
        listening = incidents.onChange(change -> refresh());
        refresh();
    }

    /**
     * Selects an incident when it is listed; Show more… uses this. An incident no longer listed is ignored.
     *
     * @param id the incident
     */
    public void select(UUID id) {
        int index = indexOf(id);
        if (index < 0) return;
        list.setSelectedIndex(index);
        list.ensureIndexIsVisible(index);
    }

    /** Stops listening; the window calls this when it closes. Idempotent. */
    public void close() {
        if (closed) return;
        closed = true;
        listening.close();
    }

    Optional<Incident> selected() { return Optional.ofNullable(list.getSelectedValue()); }

    /** Rebuilds the list, keeping the user's selection, else selecting the newest. */
    private void refresh() {
        UUID chosen = selected().map(Incident::id).orElse(null);
        List<Incident> now = incidents.list();
        refreshing = true;
        try {
            model.clear();
            now.forEach(model::addElement);
            int index = indexOf(chosen);
            if (index < 0 && !now.isEmpty()) index = 0;
            if (index >= 0) list.setSelectedIndex(index);
        } finally {
            refreshing = false;
        }
        clear.setEnabled(!now.isEmpty());
        showSelected();
    }

    private int indexOf(UUID id) {
        if (id == null) return -1;
        for (int i = 0; i < model.size(); i++) if (model.get(i).id().equals(id)) return i;
        return -1;
    }

    private void showSelected() {
        if (refreshing) return;
        Optional<Incident> chosen = selected();
        copy.setEnabled(chosen.isPresent());
        disable.setVisible(chosen.map(Incident::fromPlugin).orElse(false));
        if (chosen.isEmpty()) {
            shown = null;
            cards.show(detail, EMPTY);
            return;
        }
        Incident incident = chosen.get();
        header.setText(sourceName(incident) + " · " + incident.operation());
        sightings.setText(sightingsOf(incident));
        if (!incident.id().equals(shown)) {
            // The first sighting's text never changes, so a long trace is set once per selection.
            message.setText(incident.message().isEmpty() ? "No message" : incident.message());
            message.setCaretPosition(0);
            trace.setText(incident.trace());
            trace.setCaretPosition(0);
            shown = incident.id();
        }
        cards.show(detail, DETAIL);
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

    /** A label that never renders markup: the property goes in before any text is set. */
    private static JLabel plain() {
        var label = new JLabel();
        label.putClientProperty("html.disable", Boolean.TRUE);
        return label;
    }

    List<Incident> listed() { return Collections.list(model.elements()); }
    String headerText() { return header.getText(); }
    JLabel headerLabel() { return header; }
    String sightingsText() { return sightings.getText(); }
    String messageText() { return message.getText(); }
    String traceText() { return trace.getText(); }
    Font traceFont() { return trace.getFont(); }
    boolean showingEmpty() { return detail.getComponent(0).isVisible(); }
    JButton copyButton() { return copy; }
    JButton disableButton() { return disable; }
    JButton logFolderButton() { return logFolder; }
    JButton clearButton() { return clear; }
    String rowSummary(int index) { return rendered(index).summaryLabel.getText(); }
    String rowDetail(int index) { return rendered(index).detailLabel.getText(); }
    List<JLabel> rowLabels(int index) {
        Row row = rendered(index);
        return List.of(row.summaryLabel, row.detailLabel);
    }

    private Row rendered(int index) {
        return (Row) list.getCellRenderer().getListCellRendererComponent(list, model.get(index), index, false, false);
    }

    /** One list row: the error icon and summary, then the source, count and last sighting. */
    private final class Row extends JPanel implements ListCellRenderer<Incident> {
        private final JLabel summaryLabel = plain();
        private final JLabel detailLabel = plain();

        Row() {
            super(new BorderLayout(0, UIScale.scale(2)));
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            summaryLabel.setIcon(AppIcons.bundled("status/error"));
            detailLabel.setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(20), 0, 0));
            add(summaryLabel, BorderLayout.NORTH);
            add(detailLabel, BorderLayout.SOUTH);
        }

        @Override public Component getListCellRendererComponent(JList<? extends Incident> owner, Incident incident, int index,
                                                                boolean selected, boolean focused) {
            summaryLabel.setText(incident.summary());
            detailLabel.setText(detailLine(incident));
            Color foreground = selected ? owner.getSelectionForeground() : owner.getForeground();
            setOpaque(true);
            setBackground(selected ? owner.getSelectionBackground() : owner.getBackground());
            summaryLabel.setFont(owner.getFont());
            detailLabel.setFont(owner.getFont());
            summaryLabel.setForeground(foreground);
            detailLabel.setForeground(selected ? foreground : NotificationStyle.color("Jasper.mutedForeground", Color.GRAY));
            getAccessibleContext().setAccessibleName(incident.summary() + ", " + detailLine(incident));
            return this;
        }
    }

    /** Read-only text in the label font, or a monospaced one, kept through a look-and-feel change. */
    private static final class ReadOnlyText extends JTextArea {
        private final boolean monospaced;

        ReadOnlyText(boolean monospaced) {
            this.monospaced = monospaced;
            setEditable(false);
            restyle();
        }

        @Override public void updateUI() {
            super.updateUI();
            // JTextArea's constructor calls this before the field is set; the constructor restyles again.
            restyle();
        }

        private void restyle() {
            Font label = UIManager.getFont("Label.font");
            int size = label == null ? 12 : label.getSize();
            if (monospaced) setFont(new Font(Font.MONOSPACED, Font.PLAIN, size));
            else if (label != null) setFont(label.deriveFont(Font.PLAIN));
        }
    }
}
