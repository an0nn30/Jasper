package dev.jasper.app.settingsui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.UIManager;

/**
 * The {@code notifications.groups} editor: one line per known group with its name, who declared it, how its
 * notifications appear (the default marked) and a Reset. The value is the whole table. A group set back to its
 * default has no entry; a group the user has not touched keeps its entry exactly as written, even one naming the
 * default; and entries for groups not known now, such as a plugin that is not running, are kept as they are. EDT only.
 */
final class NotificationGroupsEditor implements ValueEditor<Map<String, String>> {
    /** The modes the combo offers, in order, with the names it shows. */
    static final Map<String, String> MODES = modes();

    private final NotificationGroupChoices choices;
    private final JPanel component = new JPanel(new GridBagLayout());
    private final List<Runnable> listeners = new ArrayList<>();
    private final Map<String, JComboBox<String>> combos = new LinkedHashMap<>();
    private final Map<String, JButton> resets = new HashMap<>();
    private List<NotificationGroupChoices.Group> groups = List.of();
    /** The table as last shown. */
    private Map<String, String> shown = Map.of();
    /** The user's choices since then, by group id; a null choice means "back to the default". */
    private final Map<String, String> chosen = new HashMap<>();
    private boolean showing;

    NotificationGroupsEditor(NotificationGroupChoices choices) {
        this.choices = Objects.requireNonNull(choices, "choices");
        component.setOpaque(false);
        rebuild(choices.groups().get());
    }

    @Override public JComponent component() { return component; }

    @Override public Map<String, String> value() {
        var value = new LinkedHashMap<>(shown);
        chosen.forEach((id, token) -> {
            if (token == null) value.remove(id);
            else value.put(id, token);
        });
        return value;
    }

    @Override public void show(Map<String, String> value) {
        shown = Map.copyOf(value);
        chosen.clear();
        update();
    }

    @Override public void onEdit(Runnable listener) { listeners.add(listener); }

    /** The table spans the page; the section above it names it. */
    @Override public boolean labelled() { return true; }

    /** No overrides at all removes the table, rather than writing an empty one. */
    @Override public boolean removesAtDefault() { return true; }

    /** Lists the groups declared since the page last looked, keeping what the user chose. */
    @Override public void refresh() {
        var next = choices.groups().get();
        if (!next.equals(groups)) rebuild(next);
    }

    JComboBox<String> combo(String id) { return combos.get(id); }

    JButton reset(String id) { return resets.get(id); }

    private void rebuild(List<NotificationGroupChoices.Group> next) {
        groups = List.copyOf(next);
        component.removeAll();
        combos.clear();
        resets.clear();
        if (groups.isEmpty()) {
            component.add(muted("No notification groups yet."), cell(0, 0));
        } else {
            component.add(heading("Group"), cell(0, 0));
            component.add(heading("From"), cell(1, 0));
            component.add(heading("Shows"), cell(2, 0));
            int y = 1;
            for (var group : groups) {
                var name = new JLabel(group.name());
                name.putClientProperty("html.disable", true);
                component.add(name, cell(0, y));
                component.add(muted(group.owner()), cell(1, y));
                var combo = new JComboBox<>(MODES.keySet().toArray(String[]::new));
                combo.setRenderer(renderer(group.defaultMode()));
                combo.getAccessibleContext().setAccessibleName(group.name() + ": how its notifications appear");
                combo.addActionListener(event -> {
                    if (!showing) choose(group, (String) combo.getSelectedItem());
                });
                component.add(combo, cell(2, y));
                var reset = Editors.Items.button("Reset", "Reset " + group.name() + " to its default", () -> choose(group, null));
                component.add(reset, cell(3, y));
                combos.put(group.id(), combo);
                resets.put(group.id(), reset);
                y++;
            }
        }
        update();
        component.revalidate();
        component.repaint();
    }

    /** The user chose {@code token} for {@code group}; null, or the group's default, means "no entry". */
    private void choose(NotificationGroupChoices.Group group, String token) {
        chosen.put(group.id(), token == null || token.equals(group.defaultMode()) ? null : token);
        update();
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    /** Shows the current value on every line without counting as an edit. */
    private void update() {
        Map<String, String> value = value();
        boolean was = showing;
        showing = true;
        try {
            for (var group : groups) {
                String token = value.get(group.id());
                combos.get(group.id()).setSelectedItem(token != null && MODES.containsKey(token) ? token : group.defaultMode());
                resets.get(group.id()).setEnabled(value.containsKey(group.id()));
            }
        } finally {
            showing = was;
        }
    }

    private static DefaultListCellRenderer renderer(String defaultMode) {
        return new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean selected, boolean focused) {
                String token = String.valueOf(value);
                String text = MODES.getOrDefault(token, token) + (token.equals(defaultMode) ? " (default)" : "");
                return super.getListCellRendererComponent(list, text, index, selected, focused);
            }
        };
    }

    private static JLabel heading(String text) {
        var label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private static JLabel muted(String text) {
        var label = new JLabel(text);
        label.putClientProperty("html.disable", true);
        Color color = UIManager.getColor("Label.disabledForeground");
        if (color != null) label.setForeground(color);
        return label;
    }

    private static GridBagConstraints cell(int x, int y) {
        var constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(y == 0 ? 0 : 2, 0, 2, 16);
        return constraints;
    }

    private static Map<String, String> modes() {
        var modes = new LinkedHashMap<String, String>();
        modes.put("card", "Card");
        modes.put("sticky", "Sticky card");
        modes.put("history", "History only");
        modes.put("off", "Off");
        return Collections.unmodifiableMap(modes);
    }
}
