package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.Subscription;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** The "SSH hosts" rail panel: search, grouped hosts, the selected host's card with Connect. One per window. */
public final class HostsPanel extends JPanel {
    public record Actions(Consumer<RemoteHost> connect, Consumer<RemoteHost> connectSplit, Consumer<Optional<RemoteHost>> edit, Consumer<RemoteHost> duplicate,
                          Consumer<RemoteHost> delete, BiConsumer<RemoteHost, Boolean> favorite, Runnable importConfig) { }

    final JTextField search = new JTextField();
    final JList<HostRows.Row> list;
    final JPanel card = new JPanel();
    final JLabel cardName = new JLabel(), cardAddress = new JLabel(), cardCredential = new JLabel(), cardJump = new JLabel();
    final JButton connect = new JButton("Connect"), edit = new JButton("Edit"), add = new JButton("+"), importButton = new JButton("Import");
    final JLabel empty = new JLabel("No hosts yet — Add or Import from ~/.ssh/config");
    private final Actions actions;
    private final DefaultListModel<HostRows.Row> model = new DefaultListModel<>();
    private final Set<String> collapsed = new HashSet<>();
    private final List<Runnable> collapseListeners = new ArrayList<>();
    private List<RemoteHost> hosts = List.of();
    private Optional<String> error = Optional.empty();
    private Function<RemoteHost, String> credentialLabels = host -> "";
    private UUID selected;

    public HostsPanel(Actions actions) {
        super(new BorderLayout(0, 6));
        this.actions = actions;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        var header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        var title = new JLabel("SSH hosts");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        header.add(title); header.add(Box.createHorizontalGlue()); header.add(importButton); header.add(Box.createHorizontalStrut(4)); header.add(add);
        var north = new JPanel(new BorderLayout(0, 6));
        north.add(header, BorderLayout.NORTH);
        search.putClientProperty("JTextField.placeholderText", "Search hosts…");
        north.add(search, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        var center = new JPanel(new BorderLayout());
        center.add(new JScrollPane(list), BorderLayout.CENTER);
        empty.setBorder(BorderFactory.createEmptyBorder(12, 4, 12, 4));
        center.add(empty, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        cardName.setFont(cardName.getFont().deriveFont(Font.BOLD));
        card.add(cardName); card.add(cardAddress); card.add(cardCredential); card.add(cardJump);
        var buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(edit); buttons.add(Box.createHorizontalGlue()); buttons.add(connect);
        card.add(Box.createVerticalStrut(6)); card.add(buttons);
        card.setVisible(false);
        add(card, BorderLayout.SOUTH);

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { rebuild(); }
            @Override public void removeUpdate(DocumentEvent e) { rebuild(); }
            @Override public void changedUpdate(DocumentEvent e) { rebuild(); }
        });
        list.addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) selectionChanged(); });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                int index = list.locationToIndex(event.getPoint());
                if (index < 0) return;
                if (SwingUtilities.isRightMouseButton(event)) { list.setSelectedIndex(index); JPopupMenu menu = menuFor(index); if (menu != null) menu.show(list, event.getX(), event.getY()); }
                else if (event.getClickCount() == 2) activate(index);
                else if (model.get(index) instanceof HostRows.Group) toggle(index);
            }
        });
        add.addActionListener(event -> actions.edit().accept(Optional.empty()));
        importButton.addActionListener(event -> actions.importConfig().run());
        connect.addActionListener(event -> selectedHost().ifPresent(actions.connect()));
        edit.addActionListener(event -> selectedHost().ifPresent(host -> actions.edit().accept(Optional.of(host))));
        rebuild();
    }

    public void setHosts(List<RemoteHost> hosts, Optional<String> error) { this.hosts = List.copyOf(hosts); this.error = error; rebuild(); }
    public void setCredentialLabels(Function<RemoteHost, String> labels) { credentialLabels = labels; refreshCard(); }
    public Set<String> collapsed() { return Set.copyOf(collapsed); }
    public void setCollapsed(Set<String> groups) { collapsed.clear(); collapsed.addAll(groups); rebuild(); }
    public Subscription onCollapsedChanged(Runnable listener) { collapseListeners.add(listener); return () -> collapseListeners.remove(listener); }

    public void select(UUID hostId) {
        for (int i = 0; i < model.size(); i++) if (model.get(i) instanceof HostRows.Host row && row.host().id().equals(hostId)) { list.setSelectedIndex(i); return; }
    }

    Optional<RemoteHost> selectedHost() { return hosts.stream().filter(host -> host.id().equals(selected)).findFirst(); }

    /** Double-click or Enter on a row: connect to a host, toggle a group. */
    void activate(int index) {
        if (index < 0 || index >= model.size()) return;
        switch (model.get(index)) {
            case HostRows.Host row -> actions.connect().accept(row.host());
            case HostRows.Group group -> toggle(index);
            case HostRows.Error ignored -> { }
        }
    }

    void toggle(int index) {
        if (!(model.get(index) instanceof HostRows.Group group)) return;
        if (!collapsed.remove(group.name())) collapsed.add(group.name());
        collapseListeners.forEach(Runnable::run);
        rebuild();
    }

    JPopupMenu menuFor(int index) {
        if (index < 0 || !(model.get(index) instanceof HostRows.Host row)) return null;
        RemoteHost host = row.host();
        var menu = new JPopupMenu();
        menu.add(item("Connect", () -> actions.connect().accept(host)));
        menu.add(item("Connect in split", () -> actions.connectSplit().accept(host)));
        menu.add(item("Edit…", () -> actions.edit().accept(Optional.of(host))));
        menu.add(item("Duplicate", () -> actions.duplicate().accept(host)));
        menu.add(item("Delete…", () -> actions.delete().accept(host)));
        menu.add(item(host.favorite() ? "Remove from favorites" : "Add to favorites", () -> actions.favorite().accept(host, !host.favorite())));
        return menu;
    }

    private static JMenuItem item(String title, Runnable action) { var item = new JMenuItem(title); item.addActionListener(event -> action.run()); return item; }

    private void rebuild() {
        UUID keep = selected;
        model.clear();
        error.ifPresent(message -> model.addElement(new HostRows.Error(message)));
        HostRows.rows(hosts, search.getText(), collapsed).forEach(model::addElement);
        boolean searching = !search.getText().strip().isEmpty();
        empty.setText(hosts.isEmpty() ? "No hosts yet — Add or Import from ~/.ssh/config" : "No hosts match");
        empty.setVisible(hosts.isEmpty() || (searching && model.size() == (error.isPresent() ? 1 : 0)));
        if (keep != null) select(keep);
        selectionChanged();
    }

    private void selectionChanged() {
        HostRows.Row row = list.getSelectedValue();
        selected = row instanceof HostRows.Host host ? host.host().id() : selected;
        if (row instanceof HostRows.Host) refreshCard();
    }

    private void refreshCard() {
        Optional<RemoteHost> host = selectedHost();
        card.setVisible(host.isPresent());
        host.ifPresent(h -> {
            cardName.setText(h.name());
            cardAddress.setText(h.label());
            cardCredential.setText(credentialLabels.apply(h));
            String via = h.jump().flatMap(id -> hosts.stream().filter(other -> other.id().equals(id)).findFirst()).map(RemoteHost::name).orElse(null);
            cardJump.setVisible(via != null);
            cardJump.setText(via == null ? "" : "via " + via);
        });
    }

    private static final class Renderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
            switch (value) {
                case HostRows.Group group -> { label.setText((group.collapsed() ? "▸ " : "▾ ") + group.name() + "  " + group.count()); label.setFont(label.getFont().deriveFont(Font.BOLD)); }
                case HostRows.Host host -> label.setText("    " + host.host().name() + (host.host().favorite() ? "  ★" : ""));
                case HostRows.Error error -> label.setText("⚠ " + error.message());
                default -> { }
            }
            return label;
        }
    }
}
