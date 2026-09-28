package dev.jasper.app.settingsui;

import com.formdev.flatlaf.FlatClientProperties;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingKind;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.JTextComponent;

/** Editor factories for settings, chosen by kind unless a page asks for another. */
public final class Editors {
    private Editors() { }

    /**
     * Builds one setting's editor on a page.
     *
     * @param build creates the editor
     * @param terms extra words the search should match, such as choice names
     */
    public record Factory<T>(BiFunction<Setting<T>, PageContext, ValueEditor<T>> build, List<String> terms) {
        public Factory { terms = List.copyOf(terms); }
    }

    /** The editor a setting's kind implies. */
    @SuppressWarnings("unchecked")
    public static <T> Factory<T> byKind(Setting<T> setting) {
        Factory<?> factory = switch (setting.kind()) {
            case SettingKind.Bool ignored -> checkbox();
            case SettingKind.IntegerRange ignored -> integer("");
            case SettingKind.NumberRange ignored -> number(1, "");
            case SettingKind.Choice<?> ignored -> choice(Map.of());
            case SettingKind.Text ignored -> text();
            case SettingKind.StringList ignored -> textList();
            case SettingKind.StringMap ignored -> table();
        };
        return (Factory<T>) factory;
    }

    public static Factory<Boolean> checkbox() {
        return new Factory<>((setting, context) -> new Check(setting.label()), List.of());
    }

    public static Factory<Integer> integer(String unit) {
        return new Factory<>((setting, context) -> {
            var kind = (SettingKind.IntegerRange) setting.kind();
            return new Spin<>(new SpinnerNumberModel(kind.min(), kind.min(), kind.max(), 1), unit, "0", Number::intValue);
        }, List.of());
    }

    public static Factory<Float> number(double step, String unit) {
        return new Factory<>((setting, context) -> {
            var kind = (SettingKind.NumberRange) setting.kind();
            return new Spin<>(new SpinnerNumberModel(kind.min(), kind.min(), kind.max(), step), unit, "0.##", Number::floatValue);
        }, List.of());
    }

    /** A combo box; {@code names} maps tokens to display names, and other tokens are prettified. */
    public static <E> Factory<E> choice(Map<String, String> names) {
        return new Factory<>((setting, context) -> new Pick<>((SettingKind.Choice<E>) setting.kind(), names), List.copyOf(names.values()));
    }

    public static Factory<String> text() {
        return new Factory<>((setting, context) -> new Line(), List.of());
    }

    /** An editable combo of installed font families. */
    public static Factory<String> font(boolean monospaced) {
        return new Factory<>((setting, context) -> new Combo(() -> Fonts.families(monospaced)), List.of());
    }

    /** An editable combo of theme names. */
    public static Factory<String> theme(Supplier<List<String>> names) {
        return new Factory<>((setting, context) -> new Combo(names), List.of());
    }

    /** A text field with Browse…, for a program path. */
    public static Factory<String> executable() {
        return new Factory<>((setting, context) -> new Executable(context), List.of());
    }

    /** A text field with Browse…, for a file path. */
    public static Factory<String> path(String title) {
        return new Factory<>((setting, context) -> new Browse(context, title), List.of());
    }

    /** An ordered list of strings; + asks for text. */
    public static Factory<List<String>> textList() {
        return new Factory<>((setting, context) -> new Items(parent ->
            Optional.ofNullable(JOptionPane.showInputDialog(parent, "Add:")).filter(text -> !text.isEmpty())), List.of());
    }

    /** An ordered list of font families; + offers the installed ones. */
    public static Factory<List<String>> fontList(boolean monospaced) {
        return new Factory<>((setting, context) -> new Items(parent -> {
            var box = new JComboBox<>(Fonts.families(monospaced).toArray(String[]::new));
            box.setEditable(true);
            int answer = JOptionPane.showConfirmDialog(parent, box, "Add a fallback font", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            return answer == JOptionPane.OK_OPTION ? Optional.ofNullable((String) box.getSelectedItem()).filter(name -> !name.isBlank()) : Optional.empty();
        }), List.of());
    }

    /** A Name/Value table. */
    public static Factory<Map<String, String>> table() {
        return new Factory<>((setting, context) -> new Table(), List.of());
    }

    /** A checkbox meaning "use the default", and a spinner enabled when it is cleared; the default removes the key. */
    public static Factory<Float> optionalNumber(String label, double step, String unit) {
        return new Factory<>((setting, context) -> new OptionalNumber(setting, label, step, unit), List.of());
    }

    /** {@code icons_and_labels} becomes "Icons and labels". */
    public static String pretty(String token) {
        String spaced = token.replace('_', ' ');
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static JPanel line(JComponent... parts) {
        var panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        panel.setOpaque(false);
        for (JComponent part : parts) panel.add(part);
        return panel;
    }

    private static void onText(JTextComponent field, Runnable listener) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { listener.run(); }
            @Override public void removeUpdate(DocumentEvent event) { listener.run(); }
            @Override public void changedUpdate(DocumentEvent event) { listener.run(); }
        });
    }

    static final class Check implements ValueEditor<Boolean> {
        private final JCheckBox box;
        Check(String label) {
            box = new JCheckBox(label);
            box.putClientProperty("html.disable", true);
            box.setOpaque(false);
        }
        @Override public JComponent component() { return box; }
        @Override public Boolean value() { return box.isSelected(); }
        @Override public void show(Boolean value) { box.setSelected(value); }
        @Override public void onEdit(Runnable listener) { box.addActionListener(event -> listener.run()); }
        @Override public boolean labelled() { return true; }
    }

    static final class Spin<T extends Number> implements ValueEditor<T> {
        final JSpinner spinner;
        private final JComponent component;
        private final Function<Number, T> convert;
        private boolean showing;
        Spin(SpinnerNumberModel model, String unit, String pattern, Function<Number, T> convert) {
            this.convert = convert;
            spinner = new JSpinner(model);
            spinner.setEditor(new JSpinner.NumberEditor(spinner, pattern));
            component = unit.isEmpty() ? spinner : line(spinner, new JLabel(unit));
        }
        @Override public JComponent component() { return component; }
        @Override public T value() { return convert.apply((Number) spinner.getValue()); }
        @Override public void show(T value) {
            showing = true;
            try {
                spinner.setValue(spinner.getModel().getValue() instanceof Double ? (Object) value.doubleValue() : (Object) value.intValue());
            } finally { showing = false; }
        }
        @Override public void onEdit(Runnable listener) { spinner.addChangeListener(event -> { if (!showing) listener.run(); }); }
    }

    static final class Pick<E> implements ValueEditor<E> {
        private final JComboBox<String> box = new JComboBox<>();
        private final List<E> values = new ArrayList<>();
        private boolean showing;
        Pick(SettingKind.Choice<E> kind, Map<String, String> names) {
            for (var token : kind.tokens()) {
                box.addItem(names.getOrDefault(token.getKey(), pretty(token.getKey())));
                values.add(token.getValue());
            }
        }
        @Override public JComponent component() { return box; }
        @Override public E value() { return values.get(box.getSelectedIndex()); }
        @Override public void show(E value) {
            showing = true;
            try { box.setSelectedIndex(values.indexOf(value)); } finally { showing = false; }
        }
        @Override public void onEdit(Runnable listener) { box.addActionListener(event -> { if (!showing) listener.run(); }); }
    }

    static final class Line implements ValueEditor<String> {
        final JTextField field = new JTextField(24);
        private boolean showing;
        @Override public JComponent component() { return field; }
        @Override public String value() { return field.getText(); }
        @Override public void show(String value) {
            showing = true;
            try { field.setText(value); } finally { showing = false; }
        }
        @Override public void onEdit(Runnable listener) { onText(field, () -> { if (!showing) listener.run(); }); }
    }

    static final class Combo implements ValueEditor<String> {
        private final JComboBox<String> box;
        private boolean showing;
        Combo(Supplier<List<String>> options) {
            box = new JComboBox<>(options.get().toArray(String[]::new));
            box.setEditable(true);
            box.setPrototypeDisplayValue("JetBrains Mono NL Extra");
        }
        private JTextComponent editor() { return (JTextComponent) box.getEditor().getEditorComponent(); }
        @Override public JComponent component() { return box; }
        @Override public String value() { return editor().getText(); }
        @Override public void show(String value) {
            showing = true;
            try { box.setSelectedItem(value); editor().setText(value); } finally { showing = false; }
        }
        @Override public void onEdit(Runnable listener) { onText(editor(), () -> { if (!showing) listener.run(); }); }
    }

    static final class Executable implements ValueEditor<String> {
        private final Line line = new Line();
        private final JComponent component;
        private final PageContext context;
        Executable(PageContext context) {
            this.context = context;
            var browse = new JButton("Browse…");
            browse.addActionListener(event -> browse());
            component = line(line.field, browse);
        }
        void browse() {
            String current = line.value();
            context.chooseFile().apply("Choose a shell program", current.isBlank() ? Optional.empty() : Optional.of(java.nio.file.Path.of(current)))
                .ifPresent(path -> line.field.setText(path.toString()));
        }
        @Override public JComponent component() { return component; }
        @Override public String value() { return line.value(); }
        @Override public void show(String value) { line.show(value); }
        @Override public void onEdit(Runnable listener) { line.onEdit(listener); }
    }

    static final class Browse implements ValueEditor<String> {
        private final Line line = new Line();
        private final JComponent component;
        private final PageContext context;
        private final String title;
        Browse(PageContext context, String title) {
            this.context = context;
            this.title = title;
            var browse = new JButton("Browse…");
            browse.addActionListener(event -> browse());
            component = line(line.field, browse);
        }
        void browse() {
            String current = line.value();
            context.chooseFile().apply(title, current.isBlank() ? Optional.empty() : Optional.of(java.nio.file.Path.of(current)))
                .ifPresent(path -> line.field.setText(path.toString()));
        }
        @Override public JComponent component() { return component; }
        @Override public String value() { return line.value(); }
        @Override public void show(String value) { line.show(value); }
        @Override public void onEdit(Runnable listener) { line.onEdit(listener); }
    }

    static final class Items implements ValueEditor<List<String>> {
        private final DefaultListModel<String> model = new DefaultListModel<>();
        private final JList<String> list = new JList<>(model);
        private final JPanel component = new JPanel(new BorderLayout(0, 4));
        private final List<Runnable> listeners = new ArrayList<>();
        Items(Function<Component, Optional<String>> ask) {
            component.setOpaque(false);
            var scroll = new JScrollPane(list);
            scroll.setPreferredSize(new Dimension(320, 110));
            component.add(scroll, BorderLayout.CENTER);
            var add = button("+", "Add", () -> ask.apply(component).ifPresent(this::add));
            var remove = button("−", "Remove", this::removeSelected);
            var up = button("↑", "Move up", this::moveUp);
            var down = button("↓", "Move down", this::moveDown);
            component.add(line(add, remove, up, down), BorderLayout.SOUTH);
        }
        static JButton button(String text, String name, Runnable action) {
            var button = new JButton(text);
            button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            button.setToolTipText(name);
            button.getAccessibleContext().setAccessibleName(name);
            button.addActionListener(event -> action.run());
            return button;
        }
        void add(String item) { model.addElement(item); edited(); }
        void select(int index) { list.setSelectedIndex(index); }
        void removeSelected() {
            int index = list.getSelectedIndex();
            if (index < 0) return;
            model.remove(index);
            edited();
        }
        void moveUp() { move(-1); }
        void moveDown() { move(1); }
        private void move(int by) {
            int index = list.getSelectedIndex(), target = index + by;
            if (index < 0 || target < 0 || target >= model.size()) return;
            String item = model.remove(index);
            model.add(target, item);
            list.setSelectedIndex(target);
            edited();
        }
        private void edited() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }
        @Override public JComponent component() { return component; }
        @Override public List<String> value() { return List.copyOf(Collections.list(model.elements())); }
        @Override public void show(List<String> value) { model.clear(); value.forEach(model::addElement); }
        @Override public void onEdit(Runnable listener) { listeners.add(listener); }
    }

    static final class Table implements ValueEditor<Map<String, String>> {
        private final DefaultTableModel model = new DefaultTableModel(new Object[]{"Name", "Value"}, 0);
        private final JTable table = new JTable(model);
        private final JPanel component = new JPanel(new BorderLayout(0, 4));
        private boolean showing;
        Table() {
            component.setOpaque(false);
            table.putClientProperty("terminateEditOnFocusLost", true);
            var scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(420, 130));
            component.add(scroll, BorderLayout.CENTER);
            var add = Items.button("+", "Add variable", () -> addRow("", ""));
            var remove = Items.button("−", "Remove variable", () -> {
                int row = table.getSelectedRow();
                if (row >= 0) model.removeRow(row);
            });
            component.add(line(add, remove), BorderLayout.SOUTH);
        }
        void addRow(String name, String value) { model.addRow(new Object[]{name, value}); }
        @Override public JComponent component() { return component; }
        @Override public Map<String, String> value() {
            var values = new LinkedHashMap<String, String>();
            for (int row = 0; row < model.getRowCount(); row++) {
                String name = String.valueOf(model.getValueAt(row, 0));
                if (!name.isBlank()) values.put(name, String.valueOf(model.getValueAt(row, 1)));
            }
            return values;
        }
        @Override public void show(Map<String, String> value) {
            showing = true;
            try {
                model.setRowCount(0);
                new TreeMap<>(value).forEach(this::addRow);
            } finally { showing = false; }
        }
        @Override public void onEdit(Runnable listener) { model.addTableModelListener(event -> { if (!showing) listener.run(); }); }
    }

    static final class OptionalNumber implements ValueEditor<Float> {
        final JCheckBox platform;
        private final Spin<Float> spin;
        private final JComponent component;
        private final float unset;
        private final float fallback;
        private final List<Runnable> listeners = new ArrayList<>();
        OptionalNumber(Setting<Float> setting, String label, double step, String unit) {
            var kind = (SettingKind.NumberRange) setting.kind();
            unset = setting.defaultValue();
            fallback = setting.example().orElse((float) kind.min());
            platform = new JCheckBox(label);
            platform.setOpaque(false);
            spin = new Spin<>(new SpinnerNumberModel(fallback, kind.min(), kind.max(), step), unit, "0.##", Number::floatValue);
            platform.addActionListener(event -> { spin.spinner.setEnabled(!platform.isSelected()); edited(); });
            spin.onEdit(this::edited);
            component = line(platform, spin.component());
        }
        private void edited() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }
        @Override public JComponent component() { return component; }
        @Override public Float value() { return platform.isSelected() ? unset : spin.value(); }
        @Override public void show(Float value) {
            boolean useDefault = value == unset;
            platform.setSelected(useDefault);
            spin.spinner.setEnabled(!useDefault);
            spin.show(useDefault ? fallback : value);
        }
        @Override public void onEdit(Runnable listener) { listeners.add(listener); }
        @Override public boolean removesAtDefault() { return true; }
    }
}
