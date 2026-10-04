package dev.jasper.remote.ui.tunnels;

import dev.jasper.sdk.ui.WorkspacePalette;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.sdk.ui.BundledIcon;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolTip;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The "Tunnels" rail panel: a filter, saved tunnels by name, then quick forwards under their own heading. Each row
 * shows a state dot, the name, the mapping, a live detail line and a start/stop button at its end. One per window.
 */
public final class TunnelsPanel extends JPanel {
    /** What the panel asks for; the plugin decides how. */
    public record Actions(Consumer<Tunnel> start, Consumer<Tunnel> stop, Consumer<Tunnel> edit, Consumer<Tunnel> duplicate,
                          Consumer<Tunnel> delete, Consumer<Tunnel> save, Consumer<Tunnel> browse, Consumer<Tunnel> copyAddress,
                          Runnable add) { }

    /** A line in the list. */
    sealed interface Row permits Item, Heading, Problem { }
    /** A tunnel. */
    record Item(Tunnel tunnel) implements Row { }
    /** The quick forwards' heading. */
    record Heading(String text) implements Row { }
    /** The broken-file message. */
    record Problem(String message) implements Row { }

    /** How wide the start/stop button at the end of a row is, for hit testing. */
    static final int BUTTON_WIDTH = 30;

    final JTextField filter = new JTextField();
    final JButton add = new JButton("+");
    final JTextArea empty = new WrappedNote();
    final JList<Row> list;
    private final DefaultListModel<Row> model = new DefaultListModel<>();
    private final Actions actions;
    private final Function<BundledIcon, Icon> icons;
    private List<Tunnel> saved = List.of(), quick = List.of();
    private Optional<String> error = Optional.empty();
    private Function<UUID, TunnelState> states = id -> TunnelState.STOPPED;
    private Function<UUID, Optional<String>> hostNames = id -> Optional.empty();
    private Supplier<Instant> clock = Instant::now;

    public TunnelsPanel(Actions actions, Function<BundledIcon, Icon> icons) {
        super(new BorderLayout(0, 6));
        this.actions = actions;
        this.icons = icons;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        // The tool window header carries the title.
        var header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.add(Box.createHorizontalGlue()); header.add(add);
        add.setToolTipText("New Tunnel…");
        filter.putClientProperty("JTextField.placeholderText", "Filter tunnels…");
        var north = new JPanel(new BorderLayout(0, 6));
        north.add(header, BorderLayout.NORTH);
        north.add(filter, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        list = new JList<>(model) {
            @Override public boolean getScrollableTracksViewportWidth() { return true; }
            // A row's tooltip starts with the tunnel's name; it is shown as text, whatever the name looks like.
            @Override public JToolTip createToolTip() {
                JToolTip tip = super.createToolTip();
                tip.putClientProperty("html.disable", true);
                return tip;
            }
        };
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((owner, value, index, selected, focused) -> render(owner, value, selected));
        var scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        var center = new JPanel(new BorderLayout());
        center.add(scroll, BorderLayout.CENTER);
        center.add(empty, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { rebuild(); }
            @Override public void removeUpdate(DocumentEvent e) { rebuild(); }
            @Override public void changedUpdate(DocumentEvent e) { rebuild(); }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                int index = list.locationToIndex(event.getPoint());
                if (index < 0) return;
                Rectangle cell = list.getCellBounds(index, index);
                if (cell == null || !cell.contains(event.getPoint())) return;
                list.setSelectedIndex(index);
                if (SwingUtilities.isRightMouseButton(event)) {
                    JPopupMenu menu = menuFor(index);
                    if (menu != null) menu.show(list, event.getX(), event.getY());
                } else if (event.getX() >= cell.x + cell.width - BUTTON_WIDTH) toggle(index);
                else if (event.getClickCount() == 2) activate(index);
            }
        });
        bind("ENTER", "toggleTunnel", () -> toggle(list.getSelectedIndex()));
        bind("SPACE", "toggleTunnel", () -> toggle(list.getSelectedIndex()));
        bind("DELETE", "deleteTunnel", () -> delete(list.getSelectedIndex()));
        bind("BACK_SPACE", "deleteTunnel", () -> delete(list.getSelectedIndex()));
        add.addActionListener(event -> actions.add().run());
        rebuild();
    }

    /** Replaces what the panel lists: saved tunnels by name, then quick forwards under their own heading. */
    public void setTunnels(List<Tunnel> saved, List<Tunnel> quick, Optional<String> error, Function<UUID, TunnelState> states,
                           Function<UUID, Optional<String>> hostNames) {
        this.saved = saved.stream().sorted(Comparator.comparing((Tunnel tunnel) -> tunnel.name().toLowerCase(Locale.ROOT))).toList();
        this.quick = List.copyOf(quick);
        this.error = error;
        this.states = states;
        this.hostNames = hostNames;
        rebuild();
    }

    /** Repaints counters and uptimes; the plugin calls this once a second while a tunnel is up. */
    public void refresh() { list.repaint(); }

    void setClock(Supplier<Instant> value) { clock = value; list.repaint(); }

    /** The row button, Enter and Space: stop a running tunnel, start any other. */
    void toggle(int index) {
        tunnelAt(index).ifPresent(tunnel -> (states.apply(tunnel.id()).running() ? actions.stop() : actions.start()).accept(tunnel));
    }

    /** Double-click: edit a saved tunnel, name a quick forward. */
    void activate(int index) { tunnelAt(index).ifPresent(tunnel -> (tunnel.quick() ? actions.save() : actions.edit()).accept(tunnel)); }

    /** Delete and Backspace: only saved tunnels; a quick forward goes away when stopped. */
    void delete(int index) { tunnelAt(index).filter(tunnel -> !tunnel.quick()).ifPresent(actions.delete()); }

    JPopupMenu menuFor(int index) {
        Optional<Tunnel> found = tunnelAt(index);
        if (found.isEmpty()) return null;
        Tunnel tunnel = found.get();
        TunnelState state = states.apply(tunnel.id());
        var menu = new JPopupMenu();
        if (state.running()) menu.add(item("Stop", true, () -> actions.stop().accept(tunnel)));
        else menu.add(item("Start", true, () -> actions.start().accept(tunnel)));
        if (state instanceof TunnelState.Failed) menu.add(item("Stop", true, () -> actions.stop().accept(tunnel)));
        menu.addSeparator();
        menu.add(item("Open in Browser", TunnelRows.browseUri(tunnel, state).isPresent(), () -> actions.browse().accept(tunnel)));
        menu.add(item("Copy Address", state instanceof TunnelState.Up || tunnel.bindPort() != 0, () -> actions.copyAddress().accept(tunnel)));
        menu.addSeparator();
        if (tunnel.quick()) menu.add(item("Save as Tunnel…", true, () -> actions.save().accept(tunnel)));
        else {
            menu.add(item("Edit…", true, () -> actions.edit().accept(tunnel)));
            menu.add(item("Duplicate", true, () -> actions.duplicate().accept(tunnel)));
            menu.add(item("Delete…", true, () -> actions.delete().accept(tunnel)));
        }
        return menu;
    }

    private void bind(String stroke, String name, Runnable action) {
        list.getInputMap().put(KeyStroke.getKeyStroke(stroke), name);
        list.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    private Optional<Tunnel> tunnelAt(int index) {
        if (index < 0 || index >= model.size()) return Optional.empty();
        return model.get(index) instanceof Item item ? Optional.of(item.tunnel()) : Optional.empty();
    }

    private static JMenuItem item(String title, boolean enabled, Runnable action) {
        var item = new JMenuItem(title);
        item.setEnabled(enabled);
        item.addActionListener(event -> action.run());
        return item;
    }

    private void rebuild() {
        Optional<UUID> keep = list == null || !(list.getSelectedValue() instanceof Item item) ? Optional.empty() : Optional.of(item.tunnel().id());
        model.clear();
        error.ifPresent(message -> model.addElement(new Problem("tunnels.toml has errors: " + message)));
        String query = filter.getText();
        saved.stream().filter(tunnel -> matches(tunnel, query)).forEach(tunnel -> model.addElement(new Item(tunnel)));
        List<Tunnel> quickShown = quick.stream().filter(tunnel -> matches(tunnel, query)).toList();
        if (!quickShown.isEmpty()) {
            model.addElement(new Heading("Quick forwards"));
            quickShown.forEach(tunnel -> model.addElement(new Item(tunnel)));
        }
        boolean none = saved.isEmpty() && quick.isEmpty();
        empty.setText(none ? "No tunnels yet — add one with +, or use Forward a Port… from an SSH pane" : "No tunnels match");
        empty.setVisible(none || model.size() == (error.isPresent() ? 1 : 0));
        keep.ifPresent(id -> {
            for (int i = 0; i < model.size(); i++) if (model.get(i) instanceof Item item && item.tunnel().id().equals(id)) { list.setSelectedIndex(i); return; }
        });
    }

    private boolean matches(Tunnel tunnel, String query) {
        return TunnelRows.matches(tunnel, hostNames.apply(tunnel.hostId()).orElse(""), query);
    }

    private Component render(JList<? extends Row> owner, Row value, boolean selected) {
        if (value instanceof Item item) return row(owner, item.tunnel(), selected);
        var label = new JLabel();
        label.putClientProperty("html.disable", true);
        label.setOpaque(selected);
        label.setBackground(selected ? owner.getSelectionBackground() : owner.getBackground());
        label.setForeground(selected ? owner.getSelectionForeground() : value instanceof Problem ? WorkspacePalette.current(owner).error() : WorkspacePalette.current(owner).textMuted());
        label.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 4));
        label.setFont(owner.getFont().deriveFont(Font.BOLD));
        label.setText(value instanceof Heading heading ? heading.text() : ((Problem) value).message());
        return label;
    }

    private Component row(JList<?> owner, Tunnel tunnel, boolean selected) {
        TunnelState state = states.apply(tunnel.id());
        Color foreground = selected ? owner.getSelectionForeground() : owner.getForeground();
        Color muted = selected ? foreground : WorkspacePalette.current(owner).textMuted();
        Font font = owner.getFont();
        var row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(true);
        row.setBackground(selected ? owner.getSelectionBackground() : owner.getBackground());
        row.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 6));
        var dot = text("●", dotColor(state, WorkspacePalette.current(owner)), font.deriveFont(Math.max(8f, font.getSize2D() - 2f)));
        dot.setVerticalAlignment(SwingConstants.TOP);
        row.add(dot, BorderLayout.WEST);
        String title = TunnelRows.title(tunnel, state);
        String mapping = TunnelRows.mapping(tunnel, state);
        String detail = TunnelRows.detail(tunnel, state, hostNames.apply(tunnel.hostId()), clock.get());
        var lines = new JPanel();
        lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
        lines.setOpaque(false);
        lines.add(text(title, foreground, font.deriveFont(Font.BOLD)));
        lines.add(text(tunnel.kind().letter() + "  " + mapping, foreground, font));
        lines.add(text(detail, state instanceof TunnelState.Failed && !selected ? WorkspacePalette.current(owner).error() : muted, font));
        row.add(lines, BorderLayout.CENTER);
        var button = new JLabel(icons.apply(state.running() ? BundledIcon.RUN_STOP : BundledIcon.RUN_RUN));
        button.setVerticalAlignment(SwingConstants.TOP);
        button.setPreferredSize(new Dimension(BUTTON_WIDTH - 8, button.getPreferredSize().height));
        row.add(button, BorderLayout.EAST);
        String description = title + ", " + mapping + ", " + detail;
        row.setToolTipText(description);
        row.getAccessibleContext().setAccessibleName(description);
        return row;
    }

    private static Color dotColor(TunnelState state, WorkspacePalette palette) {
        return switch (state) {
            case TunnelState.Up up -> up.counters().warning().isPresent() ? palette.warning() : palette.success();
            case TunnelState.Starting starting -> palette.warning();
            case TunnelState.Failed failed -> palette.error();
            case TunnelState.Stopped stopped -> palette.textMuted();
        };
    }

    private static JLabel text(String value, Color foreground, Font font) {
        var label = new JLabel();
        label.putClientProperty("html.disable", true); // before the text: a label builds its HTML view when the text is set
        label.setText(value);
        label.setForeground(foreground);
        label.setFont(font);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * A label that wraps: the empty-state message must fit a rail-wide panel. Container.validateTree lays out hidden
     * children too, at 0x0, and a wrapped text area keeps measuring at that size; a degenerate size is taken as none, which
     * the text UI measures as never laid out. The wrapped height depends on the width, which the layout only fixes after it
     * asked for the height, so a new width asks for another pass.
     */
    static final class WrappedNote extends JTextArea {
        private Color semanticForeground;
        WrappedNote() {
            setEditable(false);
            setFocusable(false);
            setOpaque(false);
            setLineWrap(true);
            setWrapStyleWord(true);
            setBorder(BorderFactory.createEmptyBorder(12, 4, 12, 4));
        }

        @Override public void updateUI() {
            super.updateUI();
            Font font = UIManager.getFont("Label.font");
            if (font != null) setFont(font);

        }

        @Override protected void paintComponent(java.awt.Graphics graphics) {
            Color foreground = WorkspacePalette.current(this).text();
            // The host may compose more keys into FlatLaf.style; compare our semantic value instead.
            if (!foreground.equals(semanticForeground)) {
                semanticForeground = foreground;
                putClientProperty("FlatLaf.style", java.util.Map.of("foreground", foreground));
            }
            setForeground(foreground);
            super.paintComponent(graphics);
        }

        @Override public void setBounds(int x, int y, int width, int height) {
            if (width <= 0 || height <= 0) { width = 0; height = 0; }
            boolean wider = width > 0 && width != getWidth();
            super.setBounds(x, y, width, height);
            if (wider) revalidate();
        }
    }
}
