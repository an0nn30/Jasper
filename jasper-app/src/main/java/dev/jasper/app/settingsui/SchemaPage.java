package dev.jasper.app.settingsui;

import com.formdev.flatlaf.FlatClientProperties;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.settings.Setting;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.UIManager;

/** A page generated from a list of settings: IntelliJ-style rows of label, control, comment and Reset. EDT only. */
public final class SchemaPage implements SettingsPage {
    /** A button on an action row; {@code enabled} is read again on every refresh. */
    public record Button(String label, Runnable action, BooleanSupplier enabled) { }

    private sealed interface Item permits RowItem, SectionItem, ActionItem { }
    private record RowItem(Setting<?> setting, Editors.Factory<?> editor, String note, Predicate<SettingsDraft> enabled, boolean shown) implements Item { }
    private record SectionItem(String title) implements Item { }
    private record ActionItem(Supplier<String> text, List<Button> buttons) implements Item { }

    /** Starts the spec of a page. */
    public static Spec page(String id, String title) { return new Spec(id, title); }

    /** A page's rows, in order; build it once per dialog. */
    public static final class Spec {
        private final String id;
        private final String title;
        private String parent;
        private String anchor = "supported-settings";
        private final List<Item> items = new ArrayList<>();

        private Spec(String id, String title) { this.id = Objects.requireNonNull(id); this.title = Objects.requireNonNull(title); }

        public String id() { return id; }
        public Spec parent(String value) { parent = value; return this; }
        public Spec anchor(String value) { anchor = value; return this; }
        public Spec section(String value) { items.add(new SectionItem(value)); return this; }
        public <T> Spec row(Setting<T> setting) { return row(setting, Editors.byKind(setting)); }
        public <T> Spec row(Setting<T> setting, Editors.Factory<T> editor) { items.add(new RowItem(setting, editor, "", draft -> true, true)); return this; }

        /** Adds a note to the previous row's comment. */
        public Spec note(String text) {
            if (items.isEmpty() || !(items.getLast() instanceof RowItem)) throw new IllegalStateException("A note follows a row");
            RowItem row = (RowItem) items.removeLast();
            items.add(new RowItem(row.setting(), row.editor(), text, row.enabled(), row.shown()));
            return this;
        }

        /** Enables the previous row's control only while {@code condition} holds for the dialog's draft. */
        public Spec enabledWhen(Predicate<SettingsDraft> condition) {
            if (items.isEmpty() || !(items.getLast() instanceof RowItem)) throw new IllegalStateException("A condition follows a row");
            RowItem row = (RowItem) items.removeLast();
            items.add(new RowItem(row.setting(), row.editor(), row.note(), java.util.Objects.requireNonNull(condition, "condition"), row.shown()));
            return this;
        }

        /**
         * Builds the previous row only when {@code shown}; the page still owns its setting (for example a setting that
         * only one platform uses).
         */
        public Spec shownWhen(boolean shown) {
            if (items.isEmpty() || !(items.getLast() instanceof RowItem)) throw new IllegalStateException("A condition follows a row");
            RowItem row = (RowItem) items.removeLast();
            items.add(new RowItem(row.setting(), row.editor(), row.note(), row.enabled(), shown));
            return this;
        }

        public Spec action(Supplier<String> text, Button... buttons) { items.add(new ActionItem(text, List.of(buttons))); return this; }

        /** Every setting this page shows, in order. */
        public List<Setting<?>> settings() {
            return items.stream().filter(RowItem.class::isInstance).<Setting<?>>map(item -> ((RowItem) item).setting()).toList();
        }

        public SchemaPage build(PageContext context) { return new SchemaPage(this, context); }
    }

    /** One row's parts; package-private for tests. */
    static final class Row<T> {
        private final Setting<T> setting;
        private final ValueEditor<T> editor;
        private final Predicate<SettingsDraft> enabled;
        private final HighlightBox box;
        private final WrappingText comment = new WrappingText("Label.disabledForeground", Color.GRAY, 1f);
        private final WrappingText error = new WrappingText("Jasper.configErrorForeground", Color.RED, 0f);
        private final JButton reset = new JButton();
        private final String searchText;
        private boolean invalid;
        private boolean shownEnabled = true;

        Row(Setting<T> setting, ValueEditor<T> editor, String note, List<String> terms, Predicate<SettingsDraft> enabled) {
            this.setting = setting;
            this.editor = editor;
            this.enabled = enabled;
            JComponent labelled = editor.labelled() ? editor.component() : label(setting.label() + ":");
            box = new HighlightBox(labelled);
            String applies = switch (setting.applies()) {
                case LIVE -> "";
                case NEW_PANES -> "Applies to new panes";
                case NEW_WINDOWS -> "Applies to new windows";
                case RESTART -> "Takes effect after restart";
            };
            String text = applies.isEmpty() ? note : note.isEmpty() ? applies : applies + ". " + note;
            comment.setText(text);
            comment.setVisible(!text.isEmpty());
            error.setVisible(false);
            reset.setIcon(AppIcons.bundled("general/reset"));
            reset.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
            reset.setToolTipText("Reset to default");
            reset.getAccessibleContext().setAccessibleName("Reset " + setting.label() + " to default");
            searchText = String.join(" ", List.of(setting.label(), setting.description(), String.join(" ", setting.keywords()),
                String.join(" ", terms)));
        }

        Setting<T> setting() { return setting; }
        ValueEditor<T> editor() { return editor; }
        JButton reset() { return reset; }
        WrappingText error() { return error; }
        WrappingText comment() { return comment; }
        HighlightBox box() { return box; }
    }

    private final Spec spec;
    private final PageContext context;
    private final List<Row<?>> rows = new ArrayList<>();
    private final List<JButton> actionButtons = new ArrayList<>();
    private final List<Runnable> actionRefreshers = new ArrayList<>();
    private JPanel panel;
    private Subscription listening;

    private SchemaPage(Spec spec, PageContext context) { this.spec = spec; this.context = context; }

    @Override public String id() { return spec.id; }
    @Override public String title() { return spec.title; }
    @Override public Optional<String> parent() { return Optional.ofNullable(spec.parent); }
    @Override public String helpAnchor() { return spec.anchor; }

    @Override public List<String> searchTerms() {
        var terms = new ArrayList<String>();
        terms.add(spec.title);
        for (Item item : spec.items) {
            if (item instanceof RowItem row && row.shown()) {
                terms.add(row.setting().label());
                terms.add(row.setting().description());
                terms.add(String.join(" ", row.setting().keywords()));
                terms.addAll(row.editor().terms());
            } else if (item instanceof SectionItem section && sectionShown(section)) terms.add(section.title());
        }
        return List.copyOf(terms);
    }

    /**
     * Whether a section's header shows: not when it heads rows that are all hidden (for example rows for a setting
     * only another platform uses), so no empty header is left behind.
     */
    private boolean sectionShown(SectionItem section) {
        boolean after = false, anyRow = false;
        for (Item item : spec.items) {
            if (!after) { after = item == section; continue; }
            if (item instanceof SectionItem) break;
            if (item instanceof ActionItem) return true;
            if (item instanceof RowItem row) { if (row.shown()) return true; anyRow = true; }
        }
        return !anyRow;
    }

    List<Row<?>> rows() { return rows; }
    List<JButton> actionButtons() { return actionButtons; }

    @Override public JComponent component() {
        if (panel != null) return panel;
        panel = new WidthTracking();
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        int y = 0;
        boolean first = true;
        for (Item item : spec.items) {
            switch (item) {
                case SectionItem section -> {
                    if (!sectionShown(section)) continue;
                    var c = constraints(0, y++, 3);
                    c.fill = GridBagConstraints.HORIZONTAL;
                    c.insets = new Insets(first ? 0 : 14, 0, 6, 0);
                    panel.add(new TitledSeparator(section.title()), c);
                }
                case RowItem row -> { if (row.shown()) y = addRow(row, y); }
                case ActionItem action -> y = addAction(action, y);
            }
            first = false;
        }
        var filler = constraints(0, y, 3);
        filler.weighty = 1;
        filler.weightx = 1;
        filler.fill = GridBagConstraints.BOTH;
        var space = new JPanel();
        space.setOpaque(false);
        panel.add(space, filler);
        listening = context.draft().onChanged(this::showValues);
        showValues();
        refresh();
        return panel;
    }

    @SuppressWarnings("unchecked")
    private <T> int addRow(RowItem item, int y) {
        var setting = (Setting<T>) item.setting();
        var factory = (Editors.Factory<T>) item.editor();
        ValueEditor<T> editor = factory.build().apply(setting, context);
        var row = new Row<>(setting, editor, item.note(), factory.terms(), item.enabled());
        rows.add(row);
        if (editor.labelled()) {
            var c = constraints(0, y, 2);
            c.insets = new Insets(4, 0, 2, 0);
            panel.add(row.box, c);
        } else {
            var label = constraints(0, y, 1);
            label.insets = new Insets(4, 0, 2, 12);
            panel.add(row.box, label);
            var control = constraints(1, y, 1);
            control.insets = new Insets(4, 0, 2, 0);
            panel.add(new Natural(editor.component()), control);
        }
        var reset = constraints(2, y, 1);
        reset.weightx = 1;
        reset.insets = new Insets(4, 4, 2, 0);
        panel.add(row.reset, reset);
        int column = editor.labelled() ? 0 : 1;
        var comment = constraints(column, y + 1, 3 - column);
        comment.fill = GridBagConstraints.HORIZONTAL;
        comment.insets = new Insets(0, editor.labelled() ? 24 : 0, 2, 0);
        panel.add(row.comment, comment);
        var error = constraints(column, y + 2, 3 - column);
        error.fill = GridBagConstraints.HORIZONTAL;
        error.insets = new Insets(0, editor.labelled() ? 24 : 0, 4, 0);
        panel.add(row.error, error);
        editor.onEdit(() -> edited(row));
        row.reset.addActionListener(event -> {
            row.invalid = false;
            row.error.setVisible(false);
            context.draft().stage(setting, Optional.empty());
            context.changed().run();
        });
        return y + 3;
    }

    private int addAction(ActionItem action, int y) {
        var text = new JLabel();
        text.putClientProperty("html.disable", true);
        var c = constraints(0, y, 3);
        c.insets = new Insets(8, 0, 2, 0);
        panel.add(text, c);
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        buttons.setOpaque(false);
        var made = new ArrayList<JButton>();
        for (Button button : action.buttons()) {
            var control = new JButton(button.label());
            control.addActionListener(event -> button.action().run());
            buttons.add(control);
            made.add(control);
            actionButtons.add(control);
        }
        var b = constraints(0, y + 1, 3);
        b.insets = new Insets(2, 0, 6, 0);
        panel.add(buttons, b);
        actionRefreshers.add(() -> {
            text.setText(action.text().get());
            for (int i = 0; i < made.size(); i++) made.get(i).setEnabled(action.buttons().get(i).enabled().getAsBoolean());
        });
        return y + 2;
    }

    /**
     * The page follows the dialog's width while it fits; narrower than its editors need, it scrolls sideways rather than
     * squashing them.
     */
    private static final class WidthTracking extends JPanel implements Scrollable {
        WidthTracking() { super(new GridBagLayout()); }
        @Override public java.awt.Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) { return visible.height; }
        @Override public boolean getScrollableTracksViewportWidth() {
            return !(getParent() instanceof javax.swing.JViewport viewport) || viewport.getWidth() >= getMinimumSize().width;
        }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /**
     * Holds an editor at its preferred size. GridBagLayout lays every child out at its minimum size once the page is
     * narrower than it prefers, which squashed lists and tables to a single line.
     */
    private static final class Natural extends JPanel {
        Natural(JComponent editor) {
            super(new java.awt.BorderLayout());
            setOpaque(false);
            add(editor);
        }
        @Override public java.awt.Dimension getMinimumSize() { return getPreferredSize(); }
    }

    private static GridBagConstraints constraints(int x, int y, int width) {
        var c = new GridBagConstraints();
        c.gridx = x; c.gridy = y; c.gridwidth = width;
        c.anchor = GridBagConstraints.WEST;
        return c;
    }

    private static JLabel label(String text) {
        var label = new JLabel(text);
        label.putClientProperty("html.disable", true);
        return label;
    }

    private <T> void edited(Row<T> row) {
        T value;
        try {
            value = row.editor.value();
        } catch (RuntimeException unreadable) {
            invalid(row, "Not a valid value.");
            return;
        }
        if (row.editor.removesAtDefault() && Objects.equals(value, row.setting.defaultValue())) {
            valid(row);
            context.draft().stage(row.setting, Optional.empty());
            return;
        }
        Optional<String> problem = row.setting.kind().check(value);
        if (problem.isPresent()) { invalid(row, problem.get()); return; }
        valid(row);
        context.draft().stage(row.setting, Optional.of(value));
    }

    private void invalid(Row<?> row, String message) {
        row.invalid = true;
        row.error.setText(message);
        row.error.setVisible(true);
        context.changed().run();
    }

    private void valid(Row<?> row) {
        boolean was = row.invalid;
        row.invalid = false;
        row.error.setVisible(false);
        if (was) context.changed().run();
    }

    private void showValues() { for (Row<?> row : rows) showValue(row); }

    private <T> void showValue(Row<T> row) {
        T value = context.draft().value(row.setting);
        if (!row.invalid) {
            T current;
            try { current = row.editor.value(); } catch (RuntimeException unreadable) { current = null; }
            if (!Objects.equals(current, value)) row.editor.show(value);
        }
        row.reset.setVisible(!Objects.equals(value, row.setting.defaultValue()));
        // Only on a change: an editor that disables a part of itself (the optional size's spinner) keeps it disabled.
        boolean enabled = row.enabled.test(context.draft());
        if (enabled != row.shownEnabled) {
            row.shownEnabled = enabled;
            enable(row.editor.component(), enabled);
        }
    }

    /** Swing does not disable a container's children with it. */
    private static void enable(java.awt.Component component, boolean enabled) {
        component.setEnabled(enabled);
        if (component instanceof java.awt.Container container) for (var child : container.getComponents()) enable(child, enabled);
    }

    @Override public boolean modified() {
        return spec.settings().stream().anyMatch(context.draft()::modified);
    }

    @Override public boolean valid() { return rows.stream().noneMatch(row -> row.invalid); }

    @Override public void reset() {
        for (Row<?> row : rows) { row.invalid = false; row.error.setVisible(false); }
        context.draft().reset(spec.settings());
        showValues();
    }

    @Override public CompletableFuture<Void> apply() { return CompletableFuture.completedFuture(null); }

    @Override public void highlight(String query) {
        JComponent first = null;
        for (Row<?> row : rows) {
            boolean match = SearchText.matches(row.searchText, query);
            row.box.setHighlighted(match);
            if (match && first == null) first = row.box;
        }
        if (first != null) first.scrollRectToVisible(new java.awt.Rectangle(0, 0, first.getWidth(), first.getHeight()));
    }

    @Override public void refresh() {
        actionRefreshers.forEach(Runnable::run);
        for (Row<?> row : rows) row.editor.refresh();
        if (panel != null) showValues();
    }

    @Override public void dispose() {
        if (listening != null) listening.close();
        for (Row<?> row : rows) row.editor.dispose();
    }
}
