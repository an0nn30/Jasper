package dev.jasper.app.settingsui;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.settings.Setting;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.JComponent;

/**
 * A page generated from a list of settings: kit settings rows of title, comment, typed control and Reset, grouped in
 * sections. The page owns its kit view and the editors' state; the dialog's draft owns the values. EDT only.
 */
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
            items.add(new RowItem(row.setting(), row.editor(), row.note(), Objects.requireNonNull(condition, "condition"), row.shown()));
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

    /** One row's state; package-private for tests. */
    static final class Row<T> {
        private final Setting<T> setting;
        private final ValueEditor<T> editor;
        private final Predicate<SettingsDraft> enabled;
        private final String comment;
        private final String searchText;
        private boolean invalid;
        private String error = "";
        private boolean highlighted;
        private boolean resettable;

        Row(Setting<T> setting, ValueEditor<T> editor, String note, List<String> terms, Predicate<SettingsDraft> enabled) {
            this.setting = setting;
            this.editor = editor;
            this.enabled = enabled;
            String applies = switch (setting.applies()) {
                case LIVE -> "";
                case NEW_PANES -> "Applies to new panes";
                case NEW_WINDOWS -> "Applies to new windows";
                case RESTART -> "Takes effect after restart";
            };
            comment = applies.isEmpty() ? note : note.isEmpty() ? applies : applies + ". " + note;
            searchText = String.join(" ", List.of(setting.label(), setting.description(), String.join(" ", setting.keywords()),
                String.join(" ", terms)));
        }

        Setting<T> setting() { return setting; }
        ValueEditor<T> editor() { return editor; }
        /** The control's node id: the setting's name. */
        String id() { return setting.name(); }
        /** The settings row's node id. */
        String rowId() { return "row:" + setting.name(); }
        /** The Reset button's node id and action. */
        String resetId() { return setting.name() + ":reset"; }
        String comment() { return comment; }
        String error() { return error; }
        boolean highlighted() { return highlighted; }
        boolean resettable() { return resettable; }
    }

    private record ActionRow(ActionItem item, int index) {
        String buttonId(int button) { return "action-" + index + "-" + button; }
    }

    private final Spec spec;
    private final PageContext context;
    private final List<Row<?>> rows = new ArrayList<>();
    private final List<ActionRow> actions = new ArrayList<>();
    private KitView view;
    private PageBody body;
    private Subscription listening;
    private boolean disposed;

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
    /** The action buttons' node ids, in page order; for tests. */
    List<String> actionButtons() {
        var ids = new ArrayList<String>();
        for (var action : actions) for (int i = 0; i < action.item().buttons().size(); i++) ids.add(action.buttonId(i));
        return ids;
    }
    /** The page's kit view once built; for tests. */
    KitView view() { return view; }

    @Override public JComponent component() {
        if (body != null) return body;
        int actionIndex = 0;
        for (Item item : spec.items) {
            if (item instanceof RowItem row && row.shown()) addRow(row);
            else if (item instanceof ActionItem action) actions.add(new ActionRow(action, actionIndex++));
        }
        listening = context.draft().onChanged(this::showValues);
        showValues(false);
        view = new KitView(context.kit().get(), viewSpec(), this::event);
        body = new PageBody(view);
        return body;
    }

    @SuppressWarnings("unchecked")
    private <T> void addRow(RowItem item) {
        var setting = (Setting<T>) item.setting();
        var factory = (Editors.Factory<T>) item.editor();
        ValueEditor<T> editor = factory.build().apply(setting, context);
        rows.add(new Row<>(setting, editor, item.note(), factory.terms(), item.enabled()));
        editor.onChanged(this::render);
    }

    /** The page as kit sections of settings rows and action rows. */
    ViewSpec viewSpec() {
        var children = new ArrayList<Nodes.Node>();
        var group = new ArrayList<Nodes.Node>();
        String title = null;
        int sections = 0, rowIndex = 0, actionIndex = 0;
        for (Item item : spec.items) {
            switch (item) {
                case SectionItem section -> {
                    if (!sectionShown(section)) continue;
                    close(children, group, title, sections++);
                    title = section.title();
                }
                case RowItem row -> { if (row.shown()) group.add(row(rows.get(rowIndex++))); }
                case ActionItem ignored -> group.add(actionNode(actions.get(actionIndex++)));
            }
        }
        close(children, group, title, sections);
        return new ViewSpec(new Nodes.Stack("page:" + spec.id, ControlState.READY, Nodes.LayoutRole.DETAIL, children));
    }

    /** Ends a section: its title over a settings card of its rows; an empty untitled run adds nothing. */
    private static void close(List<Nodes.Node> children, List<Nodes.Node> group, String title, int index) {
        if (title == null && group.isEmpty()) return;
        Nodes.Node body = new Nodes.Card("card-" + index, ControlState.READY, Nodes.CardKind.SETTINGS, List.copyOf(group));
        children.add(title == null ? body : new Nodes.Section("section-" + index, ControlState.READY, title, body));
        group.clear();
    }

    private Nodes.Node row(Row<?> row) {
        boolean enabled = row.enabled.test(context.draft());
        var state = new ControlState(true, enabled, false, row.error);
        var reset = new Nodes.Button(row.resetId(), new ControlState(row.resettable, true, false, ""), "", "refresh",
            Nodes.ButtonKind.GHOST, row.resetId(), "Reset " + row.setting.label() + " to default");
        var control = new Nodes.Stack(row.id() + ":controls", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(row.editor.node(row.id(), state), reset));
        return new Nodes.SettingRow(row.rowId(), ControlState.READY, row.setting.label(), row.comment, control, false, row.highlighted);
    }

    private Nodes.Node actionNode(ActionRow action) {
        var buttons = new ArrayList<Nodes.Node>();
        for (int i = 0; i < action.item().buttons().size(); i++) {
            var button = action.item().buttons().get(i);
            buttons.add(new Nodes.Button(action.buttonId(i), new ControlState(true, button.enabled().getAsBoolean(), false, ""), button.label(), "",
                Nodes.ButtonKind.SECONDARY, action.buttonId(i), ""));
        }
        return new Nodes.Stack("action-" + action.index(), ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Text("action-" + action.index() + ":text", ControlState.READY, action.item().text().get(), Nodes.TextRole.DESCRIPTION),
            new Nodes.Stack("action-" + action.index() + ":buttons", ControlState.READY, Nodes.LayoutRole.INLINE, buttons)));
    }

    private void render() {
        if (view != null && !disposed && !view.isClosed()) view.update(viewSpec());
    }

    /** Routes a kit event to the row or action it names. */
    void event(UiEvent event) {
        if (disposed) return;
        if (event instanceof UiEvent.Action action) {
            for (var row : rows) if (action.id().equals(row.resetId())) { reset(row); return; }
            for (var actionRow : actions)
                for (int i = 0; i < actionRow.item().buttons().size(); i++)
                    if (action.id().equals(actionRow.buttonId(i))) { actionRow.item().buttons().get(i).action().run(); render(); return; }
        }
        for (var row : rows) {
            if (row.editor.event(row.id(), event)) { edited(row); render(); return; }
        }
        render();
    }

    private <T> void reset(Row<T> row) {
        row.invalid = false;
        row.error = "";
        context.draft().stage(row.setting, Optional.empty());
        context.changed().run();
        render();
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
        row.error = message;
        context.changed().run();
    }

    private void valid(Row<?> row) {
        boolean was = row.invalid;
        row.invalid = false;
        row.error = "";
        if (was) context.changed().run();
    }

    private void showValues() { showValues(true); }

    private void showValues(boolean render) {
        for (Row<?> row : rows) showValue(row);
        if (render) render();
    }

    private <T> void showValue(Row<T> row) {
        T value = context.draft().value(row.setting);
        if (!row.invalid) {
            T current;
            try { current = row.editor.value(); } catch (RuntimeException unreadable) { current = null; }
            if (!Objects.equals(current, value)) row.editor.show(value);
        }
        row.resettable = !Objects.equals(value, row.setting.defaultValue());
    }

    @Override public boolean modified() {
        return spec.settings().stream().anyMatch(context.draft()::modified);
    }

    @Override public boolean valid() { return rows.stream().noneMatch(row -> row.invalid); }

    @Override public void reset() {
        for (Row<?> row : rows) { row.invalid = false; row.error = ""; }
        context.draft().reset(spec.settings());
        showValues();
    }

    @Override public CompletableFuture<Void> apply() { return CompletableFuture.completedFuture(null); }

    /** Highlights the rows matching {@code query} and scrolls the first into view, leaving focus with the search. */
    @Override public void highlight(String query) {
        Row<?> first = null;
        for (Row<?> row : rows) {
            row.highlighted = SearchText.matches(row.searchText, query);
            if (row.highlighted && first == null) first = row;
        }
        render();
        if (first != null && view != null && !view.isClosed()) view.reveal(first.rowId());
    }

    @Override public void refresh() {
        for (Row<?> row : rows) row.editor.refresh();
        if (body != null) showValues();
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        if (listening != null) listening.close();
        for (Row<?> row : rows) row.editor.dispose();
        if (view != null) view.close();
    }
}
