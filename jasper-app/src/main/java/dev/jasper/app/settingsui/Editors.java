package dev.jasper.app.settingsui;

import com.formdev.flatlaf.FlatClientProperties;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.WorkspaceThemeChoice;
import dev.jasper.app.config.TerminalConfig;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingKind;
import java.awt.BorderLayout;
import java.awt.Color;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
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

    static final String MORE_THEMES = "Get More Themes\u2026";
    static final String MANAGE_THEMES = "Manage Themes\u2026";
    static final String MORE_TERMINAL_THEMES = "More Terminal Themes\u2026";
    /** Built-in workspace themes: stored value to shown text, in dropdown order. */
    private static final Map<String, String> BUILT_IN_COLORS = builtInColors();

    private static Map<String, String> builtInColors() {
        var names = new LinkedHashMap<String, String>();
        names.put("match", "Match the theme");
        names.put("Jasper Light", "Jasper Light");
        names.put("Jasper Dark", "Jasper Dark");
        return Collections.unmodifiableMap(names);
    }

    /** An editable combo of theme names ending in Get More Themes\u2026 (Manage Themes\u2026 while the Marketplace is off). */
    public static Factory<String> theme(Supplier<List<String>> names) {
        return new Factory<>((setting, context) -> new ActionCombo(new Choices(names, UnaryOperator.identity(), UnaryOperator.identity(),
            index -> false,
            () -> context.draft().value(CoreSettings.THEMES_MARKETPLACE_ENABLED) ? MORE_THEMES : MANAGE_THEMES,
            label -> context.navigate().accept(CorePages.INTELLIJ_THEMES + (MORE_THEMES.equals(label) ? "#marketplace" : "#installed")))),
            List.of("Get More Themes", "Marketplace"));
    }

    /**
     * Workspace theme: a checkbox for {@code match}, over separate native and Alacritty choices.
     */
    public static Factory<String> terminalColors(Supplier<List<String>> themes) {
        return new Factory<>((setting, context) -> new TerminalColorsEditor(themes, context),
            List.of(TerminalColorsEditor.FOLLOW, "follow couple match", "Alacritty", "More Terminal Themes"));
    }

    /**
     * A shown workspace theme entry as saved; typed names are preserved except for {@code match}.
     */
    static String storedColors(String shown) {
        for (var entry : BUILT_IN_COLORS.entrySet()) if (entry.getValue().equals(shown.strip())) return entry.getKey();
        return shown.isBlank() ? "" : WorkspaceThemeChoice.parse(shown).name();
    }

    /** A saved workspace theme value as shown. */
    static String shownColors(String stored) {
        if (stored.isBlank()) return "";
        String normalized = WorkspaceThemeChoice.parse(stored).name();
        return BUILT_IN_COLORS.getOrDefault(normalized, normalized);
    }

    /** A text field with Browse\u2026, for a program path. */
    public static Factory<String> executable() {
        return new Factory<>((setting, context) -> new Browse(context, "Choose a shell program"), List.of());
    }

    /** A text field with Browse\u2026, for a file path. */
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

    /** The detected shell profiles and a button that detects them again; an empty value shows the effective profile. */
    public static Factory<String> profile(ShellChoices shells) {
        return new Factory<>((setting, context) -> new Profile(shells, context.draft()),
            List.of("PowerShell", "Command Prompt", "WSL", "Custom", "default shell"));
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
        private final List<Runnable> listeners = new ArrayList<>();
        private boolean showing;
        Combo(Supplier<List<String>> options) {
            box = new JComboBox<>(options.get().toArray(String[]::new));
            box.setEditable(true);
            box.setPrototypeDisplayValue("JetBrains Mono NL Extra");
            // A look-and-feel update (every dialog replays the theme as it opens) gives the combo a new editor field,
            // so follow each editor the combo gets rather than only the first.
            onText(editor(), this::edited);
            box.addPropertyChangeListener("editor", event -> { if (box.getEditor() != null) onText(editor(), this::edited); });
        }
        private JTextComponent editor() { return (JTextComponent) box.getEditor().getEditorComponent(); }
        private void edited() { if (!showing) for (Runnable listener : List.copyOf(listeners)) listener.run(); }
        @Override public JComponent component() { return box; }
        @Override public String value() { return editor().getText(); }
        @Override public void show(String value) {
            showing = true;
            try { box.setSelectedItem(value); editor().setText(value); } finally { showing = false; }
        }
        @Override public void onEdit(Runnable listener) { listeners.add(listener); }
    }

    /**
     * Workspace theme choice: the checkbox is {@code match}; the dropdown holds a separate theme. Clearing
     * the checkbox returns to the last separate choice in this dialog, else the saved one, else Jasper Dark.
     */
    static final class TerminalColorsEditor implements ValueEditor<String> {
        static final String FOLLOW = "Workspace theme follows the UI theme";
        final JCheckBox follow = new JCheckBox(FOLLOW);
        final ActionCombo choice;
        private final JPanel component = new JPanel();
        private final List<Runnable> listeners = new ArrayList<>();
        private String separate = "Jasper Dark";
        private boolean showing;

        TerminalColorsEditor(Supplier<List<String>> themes, PageContext context) {
            choice = new ActionCombo(new Choices(() -> {
                    var all = new ArrayList<>(List.of(BUILT_IN_COLORS.get("Jasper Light"), BUILT_IN_COLORS.get("Jasper Dark")));
                    all.addAll(themes.get());
                    return all.stream().distinct().toList();
                }, Editors::storedColors, Editors::shownColors, index -> index == 2,
                () -> MORE_TERMINAL_THEMES, label -> context.navigate().accept(CorePages.ALACRITTY_THEMES)));
            String saved = context.draft().saved(CoreSettings.UI_THEME_WORKSPACE).map(WorkspaceThemeChoice::parse)
                .map(WorkspaceThemeChoice::name).orElse("");
            if (separate(saved)) separate = saved;
            follow.putClientProperty("html.disable", true);
            follow.setOpaque(false);
            component.setLayout(new BoxLayout(component, BoxLayout.Y_AXIS));
            component.setOpaque(false);
            for (JComponent part : List.of(follow, choice.component())) { part.setAlignmentX(0f); component.add(part); }
            follow.addActionListener(event -> {
                if (showing) return;
                if (!follow.isSelected()) choice.show(separate);
                sync();
                edited();
            });
            choice.onEdit(() -> {
                if (showing) return;
                String value = choice.value();
                if (separate(value)) separate = value;
                edited();
            });
            sync();
        }

        /** Whether {@code value} is a separate choice rather than following the UI theme. */
        private static boolean separate(String value) { return !value.isBlank() && !value.equals("match"); }

        private void sync() { choice.component().setEnabled(!follow.isSelected()); }
        private void edited() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }

        @Override public JComponent component() { return component; }
        @Override public String value() { return follow.isSelected() ? "match" : choice.value(); }
        @Override public void show(String value) {
            showing = true;
            try {
                String normalized = value.isBlank() ? "match" : WorkspaceThemeChoice.parse(value).name();
                boolean follows = !separate(normalized);
                follow.setSelected(follows);
                if (!follows) separate = normalized;
                choice.show(separate);
                sync();
            } finally { showing = false; }
        }
        @Override public void onEdit(Runnable listener) { listeners.add(listener); }
        @Override public void dispose() { choice.dispose(); }
    }

    /**
     * What an {@link ActionCombo} offers.
     *
     * @param entries the choices as shown
     * @param stored shown text to the stored value
     * @param shown a stored value to shown text
     * @param ruleBefore rows that start a group, drawn below a rule
     * @param actionLabel the trailing entry's label, read each time the list opens
     * @param action runs, with the label, when the trailing entry is chosen
     */
    record Choices(Supplier<List<String>> entries, UnaryOperator<String> stored, UnaryOperator<String> shown,
                   IntPredicate ruleBefore, Supplier<String> actionLabel, Consumer<String> action) { }

    /**
     * An editable combo of choices, re-read each time the list opens, ending in an entry that runs an action (such as
     * opening another page) instead of being chosen; the value stays as it was.
     */
    static final class ActionCombo implements ValueEditor<String> {
        private final Choices choices;
        private final Model model = new Model();
        private final JComboBox<String> box = new JComboBox<>(model);
        private final List<Runnable> listeners = new ArrayList<>();
        private String action = "";
        private boolean showing;

        ActionCombo(Choices choices) {
            this.choices = choices;
            box.setEditable(true);
            box.setPrototypeDisplayValue("JetBrains Mono NL Extra");
            box.setRenderer(new Renderer());
            reload();
            // A look-and-feel update gives the combo a new editor field; follow each one (see Combo).
            onText(editor(), this::edited);
            bindEnter(editor());
            box.addPropertyChangeListener("editor", event -> {
                if (box.getEditor() == null) return;
                onText(editor(), this::edited);
                bindEnter(editor());
            });
            box.addPopupMenuListener(new PopupMenuListener() {
                @Override public void popupMenuWillBecomeVisible(PopupMenuEvent event) { reload(); }
                @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent event) { }
                @Override public void popupMenuCanceled(PopupMenuEvent event) { }
            });
        }

        /** Re-reads the entries and the action's label, keeping the text shown. */
        void reload() {
            showing = true;
            try {
                String text = editor().getText();
                model.removeAllElements();
                for (String entry : choices.entries().get()) model.addElement(entry);
                action = choices.actionLabel().get();
                model.addElement(action);
                model.select(text);
                editor().setText(text);
            } finally { showing = false; }
        }

        private boolean isAction(Object item) { return item != null && item.equals(action); }

        /**
         * Whether choosing the action entry during {@code current} should run it. Basic and FlatLaf select as the arrow
         * and page keys move through the open list, so only a click, Enter or a programmatic choice (no event) runs it.
         */
        static boolean navigates(java.awt.AWTEvent current) {
            if (current instanceof java.awt.event.KeyEvent key) return key.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER
                || key.getKeyChar() == '\n';
            return true;
        }

        private JTextComponent editor() { return (JTextComponent) box.getEditor().getEditorComponent(); }
        private void edited() { if (!showing) for (Runnable listener : List.copyOf(listeners)) listener.run(); }

        /**
         * Binds Enter on the editor field. An editable combo's Enter takes the typed text, not the row highlighted in the
         * open list, and the arrow keys never select the action entry, so Enter on it would do nothing without this.
         */
        private void bindEnter(JTextComponent field) {
            field.getInputMap().put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "jasper.actionComboEnter");
            field.getActionMap().put("jasper.actionComboEnter", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent event) { enter(event, box.isPopupVisible()); }
            });
        }

        /** Enter: the highlighted action entry of the open list runs; anything else accepts the text as usual. */
        void enter(java.awt.event.ActionEvent event, boolean listOpen) {
            Object highlighted = popupList().map(JList::getSelectedValue).orElse(null);
            if (listOpen && isAction(highlighted)) { run(); return; }
            Action accept = editor().getActionMap().get(JTextField.notifyAction);
            if (accept != null) accept.actionPerformed(event);
        }

        /** Highlights a row of the list, as the arrow keys do. */
        void highlight(int index) { popupList().ifPresent(list -> list.setSelectedIndex(index)); }

        private Optional<JList<?>> popupList() {
            return box.getUI().getAccessibleChild(box, 0) instanceof javax.swing.plaf.basic.ComboPopup popup
                ? Optional.of(popup.getList()) : Optional.empty();
        }

        private void run() {
            box.hidePopup();
            choices.action().accept(action);
        }

        @Override public JComponent component() { return box; }
        @Override public String value() { return choices.stored().apply(editor().getText()); }
        @Override public void show(String value) {
            showing = true;
            try {
                String text = choices.shown().apply(value);
                model.select(text);
                editor().setText(text);
            } finally { showing = false; }
        }
        @Override public void onEdit(Runnable listener) { listeners.add(listener); }

        /** Refuses the action entry as a selection and runs it instead. */
        private final class Model extends DefaultComboBoxModel<String> {
            @Override public void setSelectedItem(Object item) {
                if (isAction(item)) {
                    if (showing || !navigates(java.awt.EventQueue.getCurrentEvent())) return;
                    // Enter on typed action text: put the chosen entry back so the action is never the value.
                    Object chosen = getSelectedItem();
                    String text = chosen == null ? "" : chosen.toString();
                    if (!editor().getText().equals(text)) editor().setText(text);
                    run();
                    return;
                }
                super.setSelectedItem(item);
            }
            void select(Object item) { super.setSelectedItem(item); }
        }

        /** Draws the action entry as a link, and a rule above it and above each row that starts a group. */
        private final class Renderer extends DefaultListCellRenderer {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
                var label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                label.putClientProperty("html.disable", true);
                boolean actionEntry = isAction(value);
                if (actionEntry && !selected) {
                    Color link = UIManager.getColor("Component.linkColor");
                    if (link != null) label.setForeground(link);
                }
                if (index >= 0 && (actionEntry || choices.ruleBefore().test(index))) {
                    Color rule = UIManager.getColor("Separator.foreground");
                    label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(1, 0, 0, 0, rule != null ? rule : Color.GRAY), label.getBorder()));
                }
                return label;
            }
        }
    }

    static final class Browse implements ValueEditor<String> {
        private final Line line = new Line();
        private final JComponent component;
        private final PageContext context;
        private final String title;
        Browse(PageContext context, String title) {
            this.context = context;
            this.title = title;
            var browse = new JButton("Browse\u2026");
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

    static final class Profile implements ValueEditor<String> {
        final JComboBox<String> box = new JComboBox<>();
        final JButton detect;
        private final List<String> ids = new ArrayList<>();
        private final JComponent component;
        private final ShellChoices shells;
        private final SettingsDraft draft;
        private final Subscription listening;
        private String shown = "";
        /** The id the box displays for {@link #shown} or the last edit; picking it again is no edit. */
        private String current = "";
        /** How many entries are choices; any after them is the one "(not detected)" entry. */
        private int detected;
        private boolean showing;
        private final List<Runnable> edits = new ArrayList<>();

        Profile(ShellChoices shells, SettingsDraft draft) {
            this.shells = shells;
            this.draft = draft;
            box.setPrototypeDisplayValue("Ubuntu-22.04 (WSL 2 \u00B7 default)");
            detect = Items.button("\u21BB", "Detect shells again", shells.refresh());
            component = line(box, detect);
            fill();
            listening = shells.onChanged().apply(this::fill);
            box.addActionListener(event -> {
                if (showing || value().equals(current)) return;
                current = value();
                for (Runnable listener : List.copyOf(edits)) listener.run();
            });
        }

        /** Rebuilds the entries from the current choices, keeping the selection. */
        void fill() {
            String keep = box.getSelectedIndex() < 0 ? shown : ids.get(box.getSelectedIndex());
            showing = true;
            try {
                box.removeAllItems();
                ids.clear();
                for (ShellChoices.Choice choice : shells.choices().get()) add(choice.id(), choice.label());
                if (!ids.contains(ShellChoices.CUSTOM)) add(ShellChoices.CUSTOM, "Custom");
                detected = ids.size();
                select(keep);
            } finally { showing = false; }
        }

        private void add(String id, String label) { ids.add(id); box.addItem(label); }

        private void select(String value) {
            // At most one undetected entry: the one for the value shown now.
            while (ids.size() > detected) {
                ids.removeLast();
                box.removeItemAt(box.getItemCount() - 1);
            }
            String wanted = effective(value);
            // WSL distribution names ignore case, as the launcher's resolution does: select the detected spelling.
            String id = ids.stream().filter(known -> known.equals(wanted)
                || known.startsWith(WSL_PREFIX) && wanted.startsWith(WSL_PREFIX) && known.equalsIgnoreCase(wanted))
                .findFirst().orElse(wanted);
            if (!ids.contains(id)) add(id, id + " (not detected)");
            box.setSelectedIndex(ids.indexOf(id));
            current = id;
        }

        private static final String WSL_PREFIX = "wsl:";

        /** Empty means Custom while the old program or args settings are in use, else the platform default (the first choice). */
        private String effective(String value) {
            if (!value.isEmpty()) return value;
            if (TerminalConfig.Shell.custom(draft.value(CoreSettings.TERMINAL_SHELL_PROGRAM), draft.value(CoreSettings.TERMINAL_SHELL_ARGS), ""))
                return ShellChoices.CUSTOM;
            return ids.stream().filter(id -> !id.equals(ShellChoices.CUSTOM)).findFirst().orElse(ShellChoices.CUSTOM);
        }

        @Override public JComponent component() { return component; }
        @Override public String value() {
            int index = box.getSelectedIndex();
            return index < 0 ? "" : ids.get(index);
        }
        @Override public void show(String value) {
            showing = true;
            try { shown = value; select(value); } finally { showing = false; }
        }
        /**
         * A pick that differs from what the box displayed. Swing reports picking the selected entry again too, which
         * would turn an empty setting shown as its effective profile into an explicit one.
         */
        @Override public void onEdit(Runnable listener) { edits.add(listener); }
        @Override public void dispose() { listening.close(); }
    }
}
