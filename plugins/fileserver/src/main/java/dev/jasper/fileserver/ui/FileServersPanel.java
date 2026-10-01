package dev.jasper.fileserver.ui;

import dev.jasper.sdk.ui.WorkspacePalette;
import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.runtime.ActivityLog;
import dev.jasper.fileserver.runtime.ServerRegistry;
import dev.jasper.fileserver.runtime.ServerState.Phase;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;

/**
 * The "File Servers" rail panel: one row per server with its state and a start/stop control at the right edge, and
 * below the list the selected server's details, addresses and activity. One per window; the plugin supplies the
 * servers and the actions.
 */
public final class FileServersPanel extends JPanel {
    public record Actions(Consumer<UUID> toggle, Consumer<Optional<UUID>> edit, Consumer<UUID> duplicate, Consumer<UUID> delete,
                          Consumer<UUID> reveal, Consumer<String> copy, Consumer<UUID> copyPassword, Consumer<UUID> clearActivity,
                          Consumer<UUID> fingerprints) { }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final int CONTROL_WIDTH = 28;

    final JButton add = new JButton("+");
    final JList<ServerRows.Row> list;
    final JTextArea empty = note("No file servers yet — press + to share a folder");
    final JPanel details = new JPanel(new BorderLayout(0, 6));
    final JLabel cardName = label(""), cardState = label(""), cardFolder = label(""), cardAccess = label(""), cardWarnings = label("");
    final JTextArea cardTls = note("");
    final JPanel addresses = new JPanel();
    final List<JButton> addressCopies = new ArrayList<>();
    final JButton toggle = new JButton("Start"), edit = new JButton("Edit…");
    final JTextArea activity = new JTextArea(8, 24);
    final JButton clearActivity = new JButton("Clear"), copyActivity = new JButton("Copy");
    final JSplitPane split;
    private final DefaultListModel<ServerRows.Row> model = new DefaultListModel<>();
    private final Actions actions;
    private final Function<UUID, List<ActivityLog.Entry>> log;
    private final Path home;
    private final String firewall;
    private final ZoneId zone;
    private final int dividerSize;
    private List<ServerRegistry.View> views = List.of();
    private Optional<String> problem = Optional.empty();
    private Optional<String> tlsFingerprint = Optional.empty();
    private UUID selected;
    private boolean rebuilding;

    public FileServersPanel(Actions actions, Function<UUID, List<ActivityLog.Entry>> log, Path home, String firewallHint, ZoneId zone) {
        super(new BorderLayout(0, 6));
        this.actions = actions; this.log = log; this.home = home; this.firewall = firewallHint; this.zone = zone;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        var title = label("File Servers");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        var header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.add(title);
        header.add(Box.createHorizontalGlue());
        add.setToolTipText("New file server");
        header.add(add);
        add(header, BorderLayout.NORTH);

        list = new JList<>(model) {
            @Override public boolean getScrollableTracksViewportWidth() { return true; }
            @Override public String getToolTipText(MouseEvent event) {
                int index = locationToIndex(event.getPoint());
                Rectangle cell = index < 0 ? null : getCellBounds(index, index);
                return cell != null && cell.contains(event.getPoint()) && model.get(index) instanceof ServerRows.Server row ? tooltip(row.view()) : null;
            }
        };
        ToolTipManager.sharedInstance().registerComponent(list);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        var top = new JPanel(new BorderLayout());
        var scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        top.add(scroll, BorderLayout.CENTER);
        empty.setBorder(BorderFactory.createEmptyBorder(12, 4, 12, 4));
        top.add(empty, BorderLayout.SOUTH);

        var card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        cardName.setFont(cardName.getFont().deriveFont(Font.BOLD));
        addresses.setLayout(new BoxLayout(addresses, BoxLayout.Y_AXIS));
        for (JComponent part : List.<JComponent>of(cardName, cardState, cardFolder, cardAccess, cardWarnings, cardTls, addresses)) {
            part.setAlignmentX(LEFT_ALIGNMENT);
            card.add(part);
        }
        var buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.add(toggle);
        buttons.add(Box.createHorizontalStrut(6));
        buttons.add(edit);
        buttons.add(Box.createHorizontalGlue());
        card.add(Box.createVerticalStrut(6));
        card.add(buttons);
        details.add(card, BorderLayout.NORTH);
        var activityHeader = new JPanel();
        activityHeader.setLayout(new BoxLayout(activityHeader, BoxLayout.X_AXIS));
        activityHeader.add(label("Activity"));
        activityHeader.add(Box.createHorizontalGlue());
        activityHeader.add(clearActivity);
        activityHeader.add(Box.createHorizontalStrut(4));
        activityHeader.add(copyActivity);
        activity.setEditable(false);
        var activityPanel = new JPanel(new BorderLayout(0, 4));
        activityPanel.add(activityHeader, BorderLayout.NORTH);
        activityPanel.add(new JScrollPane(activity), BorderLayout.CENTER);
        details.add(activityPanel, BorderLayout.CENTER);
        details.setVisible(false);
        split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, details);
        dividerSize = split.getDividerSize();
        split.setResizeWeight(0.5);
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);

        list.addListSelectionListener(event -> { if (!event.getValueIsAdjusting() && !rebuilding) selectionChanged(); });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                int index = list.locationToIndex(event.getPoint());
                if (index < 0) return;
                Rectangle cell = list.getCellBounds(index, index);
                if (cell == null || !cell.contains(event.getPoint())) return;
                if (SwingUtilities.isRightMouseButton(event)) {
                    list.setSelectedIndex(index);
                    JPopupMenu menu = menuFor(index);
                    if (menu != null) menu.show(list, event.getX(), event.getY());
                    return;
                }
                if (!(model.get(index) instanceof ServerRows.Server row)) return;
                boolean onControl = event.getX() >= cell.x + cell.width - CONTROL_WIDTH;
                if (onControl ? event.getClickCount() == 1 : event.getClickCount() == 2) actions.toggle().accept(row.view().definition().id());
            }
        });
        list.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "toggleServer");
        list.getActionMap().put("toggleServer", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { selectedView().ifPresent(view -> actions.toggle().accept(view.definition().id())); }
        });
        add.addActionListener(event -> actions.edit().accept(Optional.empty()));
        toggle.addActionListener(event -> selectedView().ifPresent(view -> actions.toggle().accept(view.definition().id())));
        edit.addActionListener(event -> selectedView().ifPresent(view -> actions.edit().accept(Optional.of(view.definition().id()))));
        clearActivity.addActionListener(event -> selectedView().ifPresent(view -> actions.clearActivity().accept(view.definition().id())));
        copyActivity.addActionListener(event -> actions.copy().accept(activity.getText()));
        rebuild();
    }

    public void setServers(List<ServerRegistry.View> views, Optional<String> problem) {
        this.views = List.copyOf(views);
        this.problem = problem;
        rebuild();
    }

    /** The shared certificate's fingerprint, or empty before it exists; shown for servers that use TLS. */
    public void setTlsFingerprint(Optional<String> fingerprint) {
        if (fingerprint.equals(tlsFingerprint)) return;
        tlsFingerprint = fingerprint;
        refreshCard();
    }

    /** Re-reads the selected server's activity log. */
    public void refreshActivity() {
        String text = selectedView().map(view -> log.apply(view.definition().id()).stream()
            .map(entry -> TIME.format(entry.at().atZone(zone)) + " " + entry.text()).collect(Collectors.joining("\n"))).orElse("");
        if (!text.equals(activity.getText())) {
            activity.setText(text);
            activity.setCaretPosition(activity.getDocument().getLength());
        }
    }

    public Optional<UUID> selected() { return Optional.ofNullable(selected); }

    public void select(UUID id) {
        int index = indexOf(id);
        if (index >= 0) list.setSelectedIndex(index);
    }

    Optional<ServerRegistry.View> selectedView() {
        return views.stream().filter(view -> view.definition().id().equals(selected)).findFirst();
    }

    JPopupMenu menuFor(int index) {
        if (index < 0 || index >= model.size() || !(model.get(index) instanceof ServerRows.Server row)) return null;
        ServerRegistry.View view = row.view();
        UUID id = view.definition().id();
        Phase phase = view.state().phase();
        var menu = new JPopupMenu();
        var toggleItem = item(phase.startable() ? "Start" : phase == Phase.STARTING ? "Cancel Start" : "Stop", () -> actions.toggle().accept(id));
        toggleItem.setEnabled(phase != Phase.STOPPING);
        menu.add(toggleItem);
        menu.addSeparator();
        if (view.temporary()) {
            menu.add(item("Save as a Server…", () -> actions.edit().accept(Optional.of(id))));
        } else {
            menu.add(item("Edit…", () -> actions.edit().accept(Optional.of(id))));
            menu.add(item("Duplicate", () -> actions.duplicate().accept(id)));
            menu.add(item("Delete…", () -> actions.delete().accept(id)));
        }
        menu.add(item("Reveal Folder", () -> actions.reveal().accept(id)));
        var copyAddress = item("Copy Address", () -> view.addresses().stream().findFirst().ifPresent(uri -> actions.copy().accept(uri.toString())));
        copyAddress.setEnabled(!view.addresses().isEmpty());
        menu.add(copyAddress);
        var copyPassword = item("Copy Password", () -> actions.copyPassword().accept(id));
        copyPassword.setEnabled(phase == Phase.RUNNING && view.user().isPresent());
        menu.add(copyPassword);
        var fingerprints = item("Show Fingerprints…", () -> actions.fingerprints().accept(id));
        fingerprints.setEnabled(view.definition().tls());
        menu.add(fingerprints);
        return menu;
    }

    private void rebuild() {
        rebuilding = true;
        try {
            model.clear();
            ServerRows.rows(views, problem).forEach(model::addElement);
            if (selectedView().isEmpty()) selected = firstServer();
            int index = indexOf(selected);
            if (index >= 0) list.setSelectedIndex(index); else list.clearSelection();
        } finally {
            rebuilding = false;
        }
        empty.setVisible(views.isEmpty());
        refreshCard();
        refreshActivity();
    }

    private void selectionChanged() {
        if (list.getSelectedValue() instanceof ServerRows.Server row) selected = row.view().definition().id();
        refreshCard();
        refreshActivity();
    }

    private void refreshCard() {
        Optional<ServerRegistry.View> chosen = selectedView();
        details.setVisible(chosen.isPresent());
        split.setDividerSize(chosen.isPresent() ? dividerSize : 0);
        addresses.removeAll();
        addressCopies.clear();
        if (chosen.isPresent()) {
            ServerRegistry.View view = chosen.get();
            ServerDefinition definition = view.definition();
            cardName.setText(definition.name());
            cardState.setText(ServerRows.state(view.state()));
            cardFolder.setText(ServerRows.folder(definition.folder(), home));
            cardAccess.setText(definition.protocol().label() + " :" + definition.port() + " · " + ServerRows.access(definition) + " · "
                + definition.bind().label() + view.user().map(user -> " · user " + user).orElse(""));
            List<String> warnings = ServerRows.warnings(definition);
            cardWarnings.setText(warnings.isEmpty() ? "" : "⚠ " + String.join(" · ", warnings));
            cardWarnings.setVisible(!warnings.isEmpty());
            cardTls.setText(tlsFingerprint.map(fingerprint -> "TLS certificate SHA-256 " + fingerprint)
                .orElse("TLS certificate: created when the server first starts"));
            cardTls.setVisible(definition.tls());
            for (URI uri : view.addresses()) {
                var row = new JPanel();
                row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
                row.setAlignmentX(LEFT_ALIGNMENT);
                row.add(label(uri.toString()));
                row.add(Box.createHorizontalStrut(6));
                var copy = new JButton("Copy");
                copy.addActionListener(event -> actions.copy().accept(uri.toString()));
                addressCopies.add(copy);
                row.add(copy);
                addresses.add(row);
            }
            Phase phase = view.state().phase();
            toggle.setText(switch (phase) {
                case STOPPED, WAITING_FOR_VAULT -> "Start";
                case FAILED -> "Retry";
                case STARTING -> "Cancel";
                case RUNNING -> "Stop";
                case STOPPING -> "Stopping…";
            });
            toggle.setEnabled(phase != Phase.STOPPING);
            edit.setText(view.temporary() ? "Save as a Server…" : "Edit…");
        }
        revalidate();
        repaint();
    }

    private String tooltip(ServerRegistry.View view) {
        var parts = new ArrayList<String>();
        parts.add(ServerRows.state(view.state()));
        parts.addAll(ServerRows.warnings(view.definition()));
        if (view.state().phase() == Phase.RUNNING && !(view.definition().bind() instanceof Bind.Loopback)) parts.add(firewall);
        return String.join(" · ", parts);
    }

    private UUID firstServer() {
        for (int i = 0; i < model.size(); i++) if (model.get(i) instanceof ServerRows.Server row) return row.view().definition().id();
        return null;
    }

    private int indexOf(UUID id) {
        if (id == null) return -1;
        for (int i = 0; i < model.size(); i++) if (model.get(i) instanceof ServerRows.Server row && row.view().definition().id().equals(id)) return i;
        return -1;
    }

    private static JMenuItem item(String title, Runnable action) {
        var item = new JMenuItem(title);
        item.addActionListener(event -> action.run());
        return item;
    }

    private static JLabel label(String text) {
        var label = new JLabel();
        // html.disable must be set before the text: the UI installs the HTML view when the text is set
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setText(text);
        return label;
    }

    /** Wrapping, read-only text that looks like a label; a JTextArea never renders HTML. */
    private static JTextArea note(String text) {
        var note = new JTextArea(text) {
            @Override public void setBounds(int x, int y, int width, int height) {
                // Hidden children are laid out at a degenerate size; a wrapped text area would keep measuring at it.
                if (width <= 0 || height <= 0) { width = 0; height = 0; }
                super.setBounds(x, y, width, height);
            }
            @Override protected void paintComponent(java.awt.Graphics graphics) {
                Color foreground = WorkspacePalette.current(this).textMuted();
                var style = java.util.Map.of("foreground", foreground);
                if (!style.equals(getClientProperty("FlatLaf.style"))) putClientProperty("FlatLaf.style", style);
                setForeground(foreground);
                super.paintComponent(graphics);
            }
            @Override public void updateUI() {
                super.updateUI();
                setFont(UIManager.getFont("Label.font"));

                setOpaque(false);
            }
        };
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setEditable(false);
        note.setFocusable(false);
        return note;
    }

    private final class Renderer implements ListCellRenderer<ServerRows.Row> {
        @Override public Component getListCellRendererComponent(JList<? extends ServerRows.Row> owner, ServerRows.Row value, int index,
                                                                boolean isSelected, boolean focused) {
            Color background = isSelected ? owner.getSelectionBackground() : owner.getBackground();
            Color foreground = isSelected ? owner.getSelectionForeground() : owner.getForeground();
            return switch (value) {
                case ServerRows.Problem problem -> plain("servers.toml: " + problem.message(), isSelected ? foreground : WorkspacePalette.current(owner).error(), background, owner.getFont());
                case ServerRows.Header header -> plain(header.title(), isSelected ? foreground : WorkspacePalette.current(owner).textMuted(), background, owner.getFont().deriveFont(Font.BOLD));
                case ServerRows.Server server -> serverRow(owner, server.view(), foreground, background);
            };
        }

        private Component plain(String text, Color foreground, Color background, Font font) {
            var label = label(text);
            label.setOpaque(true);
            label.setBackground(background);
            label.setForeground(foreground);
            label.setFont(font);
            label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            return label;
        }

        private Component serverRow(JList<?> owner, ServerRegistry.View view, Color foreground, Color background) {
            ServerDefinition definition = view.definition();
            Phase phase = view.state().phase();
            var row = new JPanel(new BorderLayout(6, 0));
            row.setOpaque(true);
            row.setBackground(background);
            row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            Color dot = switch (phase) {
                case RUNNING -> WorkspacePalette.current(owner).success();
                case STARTING, STOPPING, WAITING_FOR_VAULT -> WorkspacePalette.current(owner).warning();
                case FAILED -> WorkspacePalette.current(owner).error();
                case STOPPED -> WorkspacePalette.current(owner).textMuted();
            };
            row.add(text("●", dot, owner.getFont()), BorderLayout.WEST);
            Font font = view.temporary() ? owner.getFont().deriveFont(Font.ITALIC) : owner.getFont();
            row.add(text(definition.name() + "   " + definition.protocol().label() + " :" + definition.port(), foreground, font), BorderLayout.CENTER);
            String control = (ServerRows.warnings(definition).isEmpty() ? "" : "⚠ ") + (phase == Phase.RUNNING || phase == Phase.STARTING ? "■" : "▶");
            row.add(text(control, foreground, owner.getFont()), BorderLayout.EAST);
            row.getAccessibleContext().setAccessibleName(definition.name() + ", " + ServerRows.state(view.state()));
            return row;
        }

        private JLabel text(String value, Color foreground, Font font) {
            var label = label(value);
            label.setForeground(foreground);
            label.setFont(font);
            return label;
        }
    }
}
