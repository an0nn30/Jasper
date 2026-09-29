package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.UIManager;

/**
 * The plugins as cards under group headers (Development, User-installed, Bundled), filtered by a search. One visible card
 * is selected. The list is one focus stop: Up and Down move, Space toggles, Shift+F10 or the menu key opens the card's
 * menu. EDT only.
 */
final class PluginCardList extends JPanel implements Scrollable {
    /** A header's title and its plugins, sorted by name. */
    record Group(String title, List<PluginRuntime.Row> rows) { }

    private final Consumer<PluginRuntime.Row> toggle;
    private final Runnable selectionChanged;
    private final BiConsumer<Component, Point> menu;
    private final List<PluginCard> cards = new ArrayList<>();
    private final List<String> titles = new ArrayList<>();
    private final JLabel empty = PluginManagerPanel.plain(new JLabel());
    private List<PluginRuntime.Row> rows = List.of();
    private String query = "";
    private String selected;
    private boolean busy;

    PluginCardList(Consumer<PluginRuntime.Row> toggle, Runnable selectionChanged, BiConsumer<Component, Point> menu) {
        this.toggle = toggle;
        this.selectionChanged = selectionChanged;
        this.menu = menu;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setFocusable(true);
        getAccessibleContext().setAccessibleName("Plugins");
        empty.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "jasper.up", () -> move(-1));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "jasper.down", () -> move(1));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "jasper.toggle", () -> {
            PluginCard card = card(selected);
            if (card != null && card.enabled.isEnabled()) card.enabled.doClick();
        });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_F10, InputEvent.SHIFT_DOWN_MASK), "jasper.menu", this::menuAtSelection);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0), "jasper.menu", this::menuAtSelection);
        rebuild();
    }

    private void bind(KeyStroke stroke, String name, Runnable action) {
        getInputMap(JComponent.WHEN_FOCUSED).put(stroke, name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    /** Replaces the plugins, keeping the selection and the search. */
    void show(List<PluginRuntime.Row> next) { rows = List.copyOf(next); rebuild(); }

    /** Shows only plugins whose name, vendor, description or id contains {@code text}, ignoring case. */
    void search(String text) { query = text == null ? "" : text.strip(); rebuild(); }

    void busy(boolean value) { busy = value; cards.forEach(card -> card.busy(value)); }

    /** Selects the visible card for {@code id}; does nothing when it is not shown. */
    void select(String id) {
        PluginCard card = card(id);
        if (card == null) return;
        selected = id;
        applySelection();
        scrollRectToVisible(card.getBounds());
        selectionChanged.run();
    }

    String selected() { return selected; }

    PluginRuntime.Row selectedRow() {
        PluginCard card = card(selected);
        return card == null ? null : card.row();
    }

    PluginCard card(String id) {
        if (id == null) return null;
        for (PluginCard card : cards) if (card.row().id().equals(id)) return card;
        return null;
    }

    List<String> listed() { return cards.stream().map(PluginCard::text).toList(); }

    List<String> groupTitles() { return List.copyOf(titles); }

    /** The text shown when no card is, or empty. */
    String emptyText() { return empty.getParent() == this ? empty.getText() : ""; }

    private void rebuild() {
        removeAll();
        cards.clear();
        titles.clear();
        for (Group group : groups(rows)) {
            List<PluginRuntime.Row> shown = group.rows().stream().filter(row -> matches(row, query)).toList();
            if (shown.isEmpty()) continue;
            titles.add(group.title());
            add(header(group.title()));
            for (PluginRuntime.Row row : shown) {
                var card = new PluginCard(row, () -> toggle.accept(row), () -> { requestFocusInWindow(); select(row.id()); },
                    event -> popup(event));
                card.setAlignmentX(0f);
                card.busy(busy);
                cards.add(card);
                add(card);
            }
        }
        if (cards.isEmpty()) {
            empty.setText(rows.isEmpty() ? "No plugins installed" : "No plugins match");
            empty.setAlignmentX(0f);
            add(empty);
        }
        if (card(selected) == null) selected = cards.isEmpty() ? null : cards.get(0).row().id();
        applySelection();
        revalidate();
        repaint();
        selectionChanged.run();
    }

    private JComponent header(String title) {
        var label = PluginManagerPanel.plain(new JLabel(title));
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        label.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
        var holder = new JPanel(new java.awt.BorderLayout()) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        holder.setOpaque(false);
        holder.setAlignmentX(0f);
        holder.add(label);
        return holder;
    }

    private void applySelection() { cards.forEach(card -> card.setSelected(card.row().id().equals(selected))); }

    private void move(int by) {
        int index = cards.indexOf(card(selected));
        int target = Math.max(0, Math.min(cards.size() - 1, index + by));
        if (!cards.isEmpty() && target != index) select(cards.get(target).row().id());
    }

    private void menuAtSelection() {
        PluginCard card = card(selected);
        if (card != null) menu.accept(card, new Point(12, card.getHeight() / 2));
    }

    private void popup(MouseEvent event) { menu.accept(event.getComponent(), event.getPoint()); }

    /** The groups in page order, each sorted by name ignoring case; empty groups are left out. */
    static List<Group> groups(List<PluginRuntime.Row> rows) {
        var result = new ArrayList<Group>();
        for (String origin : List.of("Development", "Installed", "Bundled")) {
            List<PluginRuntime.Row> members = rows.stream().filter(row -> row.origin().equals(origin))
                .sorted(Comparator.comparing(PluginRuntime.Row::name, String.CASE_INSENSITIVE_ORDER)).toList();
            if (members.isEmpty()) continue;
            long on = members.stream().filter(PluginCard::checked).count();
            String title = switch (origin) {
                case "Development" -> "Development";
                case "Installed" -> "User-installed (" + on + " of " + members.size() + " enabled)";
                default -> "Bundled (" + on + " of " + members.size() + " enabled)";
            };
            result.add(new Group(title, members));
        }
        return result;
    }

    static boolean matches(PluginRuntime.Row row, String query) {
        if (query.isBlank()) return true;
        String haystack = String.join("\n", row.name(), row.vendor(), row.description(), row.id()).toLowerCase(Locale.ROOT);
        return haystack.contains(query.toLowerCase(Locale.ROOT));
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return visible.height; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
