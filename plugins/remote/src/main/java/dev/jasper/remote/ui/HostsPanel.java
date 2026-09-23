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
    private String defaultGroup = HostRows.OTHER;
    private Runnable renameDefaultGroup = () -> {};
    private Consumer<RemoteHost> activate;
    private Function<RemoteHost, String> metadata = host -> "";
    private java.util.function.ToIntFunction<RemoteHost> sessionCount = host -> 0;

    public HostsPanel(Actions actions) {
        super(new BorderLayout(0, 6));
        this.actions = actions; this.activate = actions.connect();
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
        list = new JList<>(model) {
            @Override public boolean getScrollableTracksViewportWidth() { return true; }
        };
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        var center = new JPanel(new BorderLayout());
        var scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        center.add(scroll, BorderLayout.CENTER);
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
                if (index < 0 || !list.getCellBounds(index, index).contains(event.getPoint())) return;
                if (SwingUtilities.isRightMouseButton(event)) { list.setSelectedIndex(index); JPopupMenu menu = menuFor(index); if (menu != null) menu.show(list, event.getX(), event.getY()); }
                else if (event.getX() < 36 && model.get(index) instanceof HostRows.Host row) {
                    if (event.getClickCount() == 1) actions.favorite().accept(row.host(), !row.host().favorite());
                }
                else if (event.getClickCount() == 2) activate(index);
                else if (model.get(index) instanceof HostRows.Group) toggle(index);
            }
        });
        list.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "activateHost");
        list.getActionMap().put("activateHost", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { activate(list.getSelectedIndex()); }
        });
        add.addActionListener(event -> actions.edit().accept(Optional.empty()));
        importButton.addActionListener(event -> actions.importConfig().run());
        connect.addActionListener(event -> selectedHost().ifPresent(actions.connect()));
        edit.addActionListener(event -> selectedHost().ifPresent(host -> actions.edit().accept(Optional.of(host))));
        rebuild();
    }

    public void setDefaultGroup(String name) { defaultGroup = name; rebuild(); }
    public void onRenameDefaultGroup(Runnable rename) { renameDefaultGroup = rename; }
    public void onActivate(Consumer<RemoteHost> action) { activate = action; }
    public void setHostDetails(Function<RemoteHost, String> details, java.util.function.ToIntFunction<RemoteHost> sessions) {
        metadata = details; sessionCount = sessions; rebuild();
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
            case HostRows.Host row -> activate.accept(row.host());
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
        if (index < 0 || index >= model.size()) return null;
        if (model.get(index) instanceof HostRows.Group group && group.name().equals(defaultGroup)) {
            var menu = new JPopupMenu(); menu.add(item("Rename group…", renameDefaultGroup)); return menu;
        }
        if (!(model.get(index) instanceof HostRows.Host row)) return null;
        RemoteHost host = row.host();
        var menu = new JPopupMenu();
        menu.add(item("Connect in new tab", () -> actions.connect().accept(host)));
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
        String query = search.getText().strip().toLowerCase(java.util.Locale.ROOT);
        List<RemoteHost> matching = hosts.stream().filter(host -> HostRows.matches(host, query)
            || (host.group().isEmpty() && defaultGroup.toLowerCase(java.util.Locale.ROOT).contains(query))
            || metadata.apply(host).toLowerCase(java.util.Locale.ROOT).contains(query)).toList();
        HostRows.rows(matching, "", query.isEmpty() ? collapsed : Set.of(), defaultGroup).forEach(model::addElement);
        boolean searching = !search.getText().strip().isEmpty();
        empty.setText(hosts.isEmpty() ? "No hosts yet — Add or Import from ~/.ssh/config" : "No hosts match");
        empty.setVisible(hosts.isEmpty() || (searching && model.size() == (error.isPresent() ? 1 : 0)));
        if (keep != null) select(keep);
        selectionChanged();
    }

    private void selectionChanged() {
        HostRows.Row row = list.getSelectedValue();
        selected = row instanceof HostRows.Host host ? host.host().id() : null;
        refreshCard();
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

    private final class Renderer implements javax.swing.ListCellRenderer<HostRows.Row> {
        @Override public Component getListCellRendererComponent(JList<? extends HostRows.Row> owner, HostRows.Row value, int index, boolean selected, boolean focused) {
            if (value instanceof HostRows.Host row) return hostCard(owner, row.host(), selected, focused);
            JLabel label = new JLabel(); label.putClientProperty("html.disable", true);
            label.setOpaque(selected);
            label.setBackground(selected ? owner.getSelectionBackground() : owner.getBackground());
            label.setForeground(selected ? owner.getSelectionForeground() : owner.getForeground());
            label.setFont(owner.getFont().deriveFont(Font.BOLD));
            label.setBorder(BorderFactory.createEmptyBorder(12, 4, 8, 4));
            if (value instanceof HostRows.Group group) {
                label.setText((group.collapsed() ? "▸  " : "▾  ") + group.name() + "   " + group.count());
                if (group.name().equals(defaultGroup)) label.setToolTipText("Right-click to rename this group");
            } else if (value instanceof HostRows.Error error) label.setText(error.message());
            return label;
        }
    }

    private Component hostCard(JList<?> owner, RemoteHost host, boolean selected, boolean focused) {
        java.awt.Color fill = selected ? owner.getSelectionBackground() : javax.swing.UIManager.getColor("TextField.background");
        if (fill == null) fill = owner.getBackground();
        java.awt.Color edge = javax.swing.UIManager.getColor("Component.borderColor");
        if (edge == null) edge = owner.getForeground().darker();
        var body = new RoundedCard(fill, edge);
        body.setLayout(new BorderLayout(8, 7));
        body.setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 12));
        java.awt.Color foreground = selected ? owner.getSelectionForeground() : owner.getForeground();
        var favorite = text(host.favorite() ? "★" : "☆", foreground, owner.getFont().deriveFont(18f));
        favorite.setVerticalAlignment(JLabel.TOP); favorite.setPreferredSize(new java.awt.Dimension(20, 22));
        body.add(favorite, BorderLayout.WEST);
        var content = new JPanel(new BorderLayout(0, 5)); content.setOpaque(false);
        var top = new JPanel(new BorderLayout(8, 0)); top.setOpaque(false);
        top.add(text(host.name(), foreground, owner.getFont().deriveFont(Font.BOLD, owner.getFont().getSize2D() + 2)), BorderLayout.CENTER);
        int sessions = sessionCount.applyAsInt(host);
        if (sessions > 0) {
            var badge = text(sessions == 1 ? "●  1 session" : sessions + " sessions", foreground, owner.getFont().deriveFont(Math.max(10f, owner.getFont().getSize2D() - 1)));
            top.add(badge, BorderLayout.EAST);
        }
        content.add(top, BorderLayout.NORTH);
        var details = new JPanel(new java.awt.GridLayout(0, 1, 0, 4)); details.setOpaque(false);
        details.add(text(host.label(), foreground, owner.getFont()));
        String facts = metadata.apply(host);
        details.add(text(facts.isBlank() ? "SSH host" : facts, foreground, owner.getFont().deriveFont(Math.max(10f, owner.getFont().getSize2D() - 1))));
        content.add(details, BorderLayout.CENTER); body.add(content, BorderLayout.CENTER);
        var margin = new JPanel(new BorderLayout()); margin.setOpaque(false);
        margin.setBorder(BorderFactory.createEmptyBorder(0, 1, 7, 1)); margin.add(body);
        margin.getAccessibleContext().setAccessibleName(host.name() + ", " + host.label()
            + (facts.isBlank() ? "" : ", " + facts) + ", " + sessions + (sessions == 1 ? " session" : " sessions"));
        return margin;
    }

    private static JLabel text(String value, java.awt.Color foreground, Font font) {
        var label = new JLabel(value); label.putClientProperty("html.disable", true);
        label.setForeground(foreground); label.setFont(font); label.setToolTipText(value); return label;
    }

    private static final class RoundedCard extends JPanel {
        private final java.awt.Color fill, edge;
        RoundedCard(java.awt.Color fill, java.awt.Color edge) { this.fill = fill; this.edge = edge; setOpaque(false); }
        @Override protected void paintComponent(java.awt.Graphics graphics) {
            var g = (java.awt.Graphics2D) graphics.create();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill); g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g.setColor(edge); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12); g.dispose();
            super.paintComponent(graphics);
        }
    }
}
