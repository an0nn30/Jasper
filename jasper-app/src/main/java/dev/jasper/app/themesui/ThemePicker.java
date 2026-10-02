package dev.jasper.app.themesui;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.appearance.ThemeManager;
import dev.jasper.app.appearance.Token;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** One searchable choice list. Model refreshes cannot masquerade as user selections. EDT only. */
final class ThemePicker extends JPanel {
    record Row(String value, String label, Theme theme) {}
    final JTextField query = new JTextField(16);
    final JComboBox<ThemeChoices.Tone> tone = new JComboBox<>(ThemeChoices.Tone.values());
    final JComboBox<ThemeChoices.Source> source = new JComboBox<>(ThemeChoices.Source.values());
    final JList<Row> list = new JList<>();
    final JLabel selection = literal("");
    final JLabel empty = literal("");
    private final JLabel label;
    private final boolean workspace;
    private final Consumer<String> selected;
    private final Map<Theme, Icon> swatches = new HashMap<>();
    private ThemeCatalog.Snapshot catalog = new ThemeCatalog.Snapshot(List.of(), List.of());
    private String requested = "";
    private boolean updating;

    ThemePicker(boolean workspace, Consumer<String> selected) {
        super(new BorderLayout(0, 6)); this.workspace = workspace; this.selected = selected;
        label = literal(workspace ? "Workspace theme" : "UI theme"); label.setLabelFor(list);
        query.getAccessibleContext().setAccessibleName(label.getText() + " search");
        tone.getAccessibleContext().setAccessibleName(label.getText() + " darkness");
        source.getAccessibleContext().setAccessibleName(label.getText() + " source");
        list.getAccessibleContext().setAccessibleName(label.getText());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); list.setVisibleRowCount(7);
        list.setCellRenderer(new DefaultListCellRenderer() {
            { putClientProperty("html.disable", Boolean.TRUE); }
            @Override public Component getListCellRendererComponent(JList<?> owner, Object value, int index, boolean chosen, boolean focus) {
                Row row = (Row) value;
                super.getListCellRendererComponent(owner, row.label(), index, chosen, focus);
                setIcon(row.theme() == null ? null : swatches.computeIfAbsent(row.theme(), ThemePicker::swatch));
                return this;
            }
        });
        var header = new JPanel(new BorderLayout(0, 5)); header.add(label, BorderLayout.NORTH);
        var filters = new JPanel(new FlowLayout(FlowLayout.LEADING, 5, 0)); filters.add(query); filters.add(tone); filters.add(source);
        header.add(filters, BorderLayout.CENTER); add(header, BorderLayout.NORTH); add(new JScrollPane(list), BorderLayout.CENTER);
        var footer = new JPanel(new BorderLayout()); footer.add(selection, BorderLayout.NORTH); footer.add(empty, BorderLayout.SOUTH); add(footer, BorderLayout.SOUTH);
        query.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { rebuild(); }
            public void removeUpdate(DocumentEvent event) { rebuild(); }
            public void changedUpdate(DocumentEvent event) { rebuild(); }
        });
        tone.addActionListener(event -> rebuild()); source.addActionListener(event -> rebuild());
        list.addListSelectionListener(event -> {
            if (updating || event.getValueIsAdjusting() || list.getSelectedValue() == null) return;
            requested = list.getSelectedValue().value(); selected.accept(requested); selectionStatus();
        });
    }

    void update(ThemeCatalog.Snapshot next, String value) {
        if (catalog.equals(next) && requested.equals(value)) return;
        catalog = next; requested = value; swatches.keySet().retainAll(next.themes()); rebuild();
    }
    String requested() { return requested; }
    void reveal() { query.requestFocusInWindow(); }
    void highlight(String text) {
        boolean match = !text.isBlank() && label.getText().toLowerCase(java.util.Locale.ROOT).contains(text.toLowerCase(java.util.Locale.ROOT));
        label.setFont(label.getFont().deriveFont(match ? Font.BOLD : Font.PLAIN));
    }
    private void rebuild() {
        List<Theme> matches = ThemeChoices.filter(catalog, query.getText(), (ThemeChoices.Tone) tone.getSelectedItem(), (ThemeChoices.Source) source.getSelectedItem());
        var model = new DefaultListModel<Row>();
        if (workspace) model.addElement(new Row("match", "Same as UI theme", null));
        matches.forEach(theme -> model.addElement(new Row(theme.name(), theme.name(), theme)));
        updating = true;
        try {
            list.setModel(model);
            for (int i = 0; i < model.size(); i++) if (model.get(i).value().equalsIgnoreCase(requested.strip())) { list.setSelectedIndex(i); break; }
        } finally { updating = false; }
        empty.setText(matches.isEmpty() ? "No themes match these filters." : ""); selectionStatus();
    }
    private void selectionStatus() {
        boolean available = workspace && requested.equalsIgnoreCase("match") || catalog.find(requested).isPresent();
        String shown = workspace && requested.equalsIgnoreCase("match") ? "Same as UI theme" : requested;
        selection.setText("Selected: " + shown + (available ? "" : " (unavailable; preview uses a fallback)"));
    }
    private static Icon swatch(Theme theme) {
        var values = ThemeManager.generated(theme);
        Color[] colors = {values.terminal().background(), values.tokens().get(Token.ACCENT), values.terminal().ansi().get(1),
            values.terminal().ansi().get(2), values.terminal().ansi().get(4), values.terminal().ansi().get(3)};
        return new Icon() {
            public int getIconWidth() { return com.formdev.flatlaf.util.UIScale.scale(90); }
            public int getIconHeight() { return com.formdev.flatlaf.util.UIScale.scale(12); }
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                int size = com.formdev.flatlaf.util.UIScale.scale(12), step = com.formdev.flatlaf.util.UIScale.scale(15);
                for (int i = 0; i < colors.length; i++) { graphics.setColor(colors[i]); graphics.fillRect(x + i * step, y, size, size); }
            }
        };
    }
    static JLabel literal(String text) { var label = new JLabel(); label.putClientProperty("html.disable", Boolean.TRUE); label.setText(text); return label; }
}
