package dev.jasper.app.settingsui;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.settings.Setting;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitView;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.JComponent;

/**
 * A page generated from a list of settings: kit settings rows of title, comment, typed control and Reset, grouped in
 * sections. The page owns its kit view and the editors' state; the dialog's draft owns the values. EDT only.
 */
public final class SchemaPage implements SettingsPage {
    /** A button on an action row; {@code enabled} is read again on every refresh, and {@code whenDisabled} is its tooltip while disabled. */
    public record Button(String label, Runnable action, BooleanSupplier enabled, String whenDisabled) {
        public Button(String label, Runnable action, BooleanSupplier enabled) { this(label, action, enabled, ""); }
    }

    /**
     * A number row whose value lives outside config.toml, read, checked and saved through these hooks (spec section 5.1:
     * the heap limit in jasper.vmoptions). {@code saved} and {@code defaultValue} are read again on every refresh and
     * reset; {@code error} blocks Apply while it is non-empty; {@code comment} is the row's description for a value, read
     * on every render; {@code save} runs on Apply.
     */
    public record External(String id, String label, String unit, List<String> keywords, Nodes.Numeric bounds,
                           IntSupplier saved, IntSupplier defaultValue, IntFunction<String> error, IntFunction<String> comment,
                           IntFunction<CompletableFuture<Void>> save, BooleanSupplier enabled) {
        public External { keywords = List.copyOf(keywords); }
    }

    /** What a notice row shows while present: a title and a text, beside the row's buttons. */
    public record Notice(String title, String text) { }

    /**
     * A section of a page that is not a setting, such as a list with its own buttons, under {@code title}. {@code build}
     * runs once, when the page is first shown, with the page's context and a runnable that shows the page again (for
     * work that finishes later). A part ends the section before it.
     *
     * @param id the part's node id prefix, unique on its page
     * @param title the section's heading
     * @param build makes the part's {@link Section} from the page's context and its re-render
     * @param terms extra words the page's search matches
     */
    public record Part(String id, String title, BiFunction<PageContext, Runnable, Section> build, List<String> terms) {
        public Part {
            Objects.requireNonNull(id, "id"); Objects.requireNonNull(title, "title"); Objects.requireNonNull(build, "build");
            terms = List.copyOf(terms);
        }

        /**
         * A built part, as functions.
         *
         * @param node the section's content, given the part's id
         * @param event takes a kit event first; true when the part handled it (the page then shows itself again)
         * @param refresh reads again what the part shows; the page calls it on each refresh
         * @param dispose releases the part when its page is disposed
         */
        public record Section(Function<String, Nodes.Node> node, Predicate<UiEvent> event, Runnable refresh, Runnable dispose) {
            public Section {
                Objects.requireNonNull(node, "node"); Objects.requireNonNull(event, "event");
                Objects.requireNonNull(refresh, "refresh"); Objects.requireNonNull(dispose, "dispose");
            }
        }
    }

    private sealed interface Item permits RowItem, SectionItem, ActionItem, ExternalItem, NoticeItem, PartItem { }
    private record RowItem(Setting<?> setting, Editors.Factory<?> editor, String note, Predicate<SettingsDraft> enabled, boolean shown) implements Item { }
    private record SectionItem(String title) implements Item { }
    private record ActionItem(String title, Supplier<String> description, List<Button> buttons) implements Item { }
    private record ExternalItem(External value) implements Item { }
    private record NoticeItem(String id, Supplier<Optional<Notice>> notice, List<Button> buttons) implements Item { }
    private record PartItem(Part part) implements Item { }

    /** Starts the spec of a page. */
    public static Spec page(String id, String title) { return new Spec(id, title); }

    /** A page's rows, in order; build it once per dialog. */
    public static final class Spec {
        private final String id;
        private final String title;
        private String parent;
        private String anchor = "supported-settings";
        private final List<Item> items = new ArrayList<>();
        private final List<Function<Runnable, Subscription>> refreshers = new ArrayList<>();

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

        /** A settings row of buttons: {@code title} and {@code description} on the left, the buttons at its end. */
        public Spec action(String title, Supplier<String> description, Button... buttons) { items.add(new ActionItem(title, description, List.of(buttons))); return this; }

        /** A row whose value lives outside config.toml (see {@link External}). */
        public Spec external(External value) { items.add(new ExternalItem(Objects.requireNonNull(value, "value"))); return this; }

        /** A row shown only while {@code notice} supplies one (read on every render), with {@code buttons} at its end. */
        public Spec notice(String id, Supplier<Optional<Notice>> notice, Button... buttons) {
            items.add(new NoticeItem(Objects.requireNonNull(id, "id"), Objects.requireNonNull(notice, "notice"), List.of(buttons)));
            return this;
        }

        /** Refreshes the page whenever {@code subscribe}'s listener runs (on the EDT); the page closes the subscription when disposed. */
        public Spec refreshOn(Function<Runnable, Subscription> subscribe) { refreshers.add(Objects.requireNonNull(subscribe, "subscribe")); return this; }

        /** A section that is not a setting; see {@link Part}. */
        public Spec part(Part part) { items.add(new PartItem(Objects.requireNonNull(part, "part"))); return this; }

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

    /** An external row's state; package-private for tests. */
    static final class ExternalRow {
        private final External value;
        private final Editors.NumberField<Integer> editor;
        private final String searchText;
        private Integer pending;
        private boolean invalid;
        private String error = "";
        private boolean highlighted;

        ExternalRow(External value) {
            this.value = value;
            editor = new Editors.NumberField<>(value.label(), value.bounds(), value.unit(), Integer::valueOf, Nodes.FieldPresentation.STEPPER);
            searchText = String.join(" ", value.label(), String.join(" ", value.keywords()));
        }

        String id() { return value.id(); }
        String rowId() { return "row:" + value.id(); }
        String resetId() { return value.id() + ":reset"; }
        Integer pending() { return pending; }
        String error() { return error; }
        boolean highlighted() { return highlighted; }
        /** The value the row stands for: the pending edit, else the saved value. */
        int shown() { return pending != null ? pending : value.saved().getAsInt(); }
    }

    private final Spec spec;
    private final PageContext context;
    private final List<Row<?>> rows = new ArrayList<>();
    private final List<ActionRow> actions = new ArrayList<>();
    private final List<ExternalRow> externals = new ArrayList<>();
    private final List<Subscription> refreshing = new ArrayList<>();
    private String revealed;
    private final List<Part.Section> parts = new ArrayList<>();
    private KitView view;
    private PageBody body;
    private HierarchyListener waiting;
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
            } else if (item instanceof ExternalItem external) {
                terms.add(external.value().label());
                terms.add(String.join(" ", external.value().keywords()));
            } else if (item instanceof SectionItem section && sectionShown(section)) terms.add(section.title());
            else if (item instanceof PartItem part) { terms.add(part.part().title()); terms.addAll(part.part().terms()); }
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
            if (item instanceof SectionItem || item instanceof PartItem) break;
            if (item instanceof ActionItem || item instanceof ExternalItem || item instanceof NoticeItem) return true;
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
    /** The external rows, in page order; for tests. */
    List<ExternalRow> externals() { return externals; }
    /** The row {@link #reveal} asked for, until {@link #selected} focuses it; for tests. */
    String revealed() { return revealed; }

    @Override public JComponent component() {
        if (body != null) return body;
        int actionIndex = 0;
        for (Item item : spec.items) {
            if (item instanceof RowItem row && row.shown()) addRow(row);
            else if (item instanceof ActionItem action) actions.add(new ActionRow(action, actionIndex++));
            else if (item instanceof ExternalItem external) externals.add(new ExternalRow(external.value()));
            else if (item instanceof PartItem part) parts.add(part.part().build().apply(context, this::render));
        }
        listening = context.draft().onChanged(this::showValues);
        for (var subscribe : spec.refreshers) refreshing.add(subscribe.apply(() -> { if (!disposed) refresh(); }));
        for (var row : externals) row.editor.show(row.shown());
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
        int sections = 0, rowIndex = 0, actionIndex = 0, externalIndex = 0, partIndex = 0;
        for (Item item : spec.items) {
            switch (item) {
                case SectionItem section -> {
                    if (!sectionShown(section)) continue;
                    close(children, group, title, sections++);
                    title = section.title();
                }
                case RowItem row -> {
                    if(row.shown()) {
                        var owner=rows.get(rowIndex++);
                        if(owner.editor.cardRows())group.addAll(((Nodes.Stack)row(owner)).children());
                        else if(owner.editor.fullSpan()) {
                            if(!group.isEmpty()){close(children,group,title,sections++);title=null;}
                            Nodes.Node content=row(owner);
                            children.add(title==null?content:new Nodes.Section("section-"+sections++,ControlState.READY,title,content));title=null;
                        } else group.add(row(owner));
                    }
                }
                case ActionItem ignored -> group.add(actionNode(actions.get(actionIndex++)));
                case ExternalItem ignored -> group.add(externalNode(externals.get(externalIndex++)));
                case NoticeItem notice -> group.add(noticeNode(notice));
                case PartItem part -> {
                    close(children, group, title, sections++);
                    title = null;
                    children.add(new Nodes.Section("section-" + part.part().id(), ControlState.READY, part.part().title(),
                        parts.get(partIndex++).node().apply(part.part().id())));
                }
            }
        }
        close(children, group, title, sections);
        return new ViewSpec(new Nodes.Stack("page:" + spec.id, ControlState.READY, Nodes.LayoutRole.SETTINGS_PAGE, children));
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
        var reset = new Nodes.Button(row.resetId(), new ControlState(row.resettable, true, false, ""), "", "reset",
            Nodes.ButtonKind.GHOST, row.resetId(), "Reset " + row.setting.label() + " to default");
        if (row.editor.fullSpan()) return row.editor.node(row.id(), state, row.highlighted);
        var control = new Nodes.Stack(row.id() + ":controls", ControlState.READY, Nodes.LayoutRole.SETTINGS_TRAILING, List.of(row.editor.node(row.id(), state), reset));
        return new Nodes.SettingRow(row.rowId(), ControlState.READY, row.editor.rowLabel(row.setting.label()), row.comment, control, row.editor.dependent(), row.highlighted);
    }

    private Nodes.Node actionNode(ActionRow action) {
        var buttons = new ArrayList<Nodes.Node>();
        for (int i = 0; i < action.item().buttons().size(); i++) {
            var button = action.item().buttons().get(i);
            boolean enabled = button.enabled().getAsBoolean();
            buttons.add(new Nodes.Button(action.buttonId(i), new ControlState(true, enabled, false, ""), button.label(), "",
                Nodes.ButtonKind.SECONDARY, action.buttonId(i), enabled ? "" : button.whenDisabled()));
        }
        var control = new Nodes.Stack("action-" + action.index() + ":buttons", ControlState.READY, Nodes.LayoutRole.INLINE, buttons);
        return new Nodes.SettingRow("action-" + action.index(), ControlState.READY, action.item().title(), action.item().description().get(), control, false, false);
    }

    private Nodes.Node externalNode(ExternalRow row) {
        boolean enabled = row.value.enabled().getAsBoolean();
        var state = new ControlState(true, enabled, false, row.error);
        boolean resettable = enabled && row.shown() != row.value.defaultValue().getAsInt();
        var reset = new Nodes.Button(row.resetId(), new ControlState(resettable, true, false, ""), "", "reset",
            Nodes.ButtonKind.GHOST, row.resetId(), "Reset " + row.value.label() + " to default");
        var control = new Nodes.Stack(row.id() + ":controls", ControlState.READY, Nodes.LayoutRole.SETTINGS_TRAILING,
            List.of(row.editor.node(row.id(), state), reset));
        return new Nodes.SettingRow(row.rowId(), ControlState.READY, row.value.label(), row.value.comment().apply(row.shown()),
            control, false, row.highlighted);
    }

    /** A notice row: hidden, with its buttons, while its supplier has nothing to say. */
    private Nodes.Node noticeNode(NoticeItem item) {
        Optional<Notice> notice = item.notice().get();
        var hidden = new ControlState(false, true, false, "");
        var buttons = new ArrayList<Nodes.Node>();
        for (int i = 0; i < item.buttons().size(); i++) {
            var button = item.buttons().get(i);
            boolean enabled = button.enabled().getAsBoolean();
            buttons.add(new Nodes.Button(noticeButtonId(item, i), notice.isPresent() ? new ControlState(true, enabled, false, "") : hidden,
                button.label(), "", Nodes.ButtonKind.SECONDARY, noticeButtonId(item, i), enabled ? "" : button.whenDisabled()));
        }
        var control = new Nodes.Stack("notice-" + item.id() + ":buttons", ControlState.READY, Nodes.LayoutRole.INLINE, buttons);
        // A row's title must not be blank, even hidden: the id stands in.
        return new Nodes.SettingRow("notice-" + item.id(), notice.isPresent() ? ControlState.READY : hidden,
            notice.map(Notice::title).orElse(item.id()), notice.map(Notice::text).orElse(""), control, false, false);
    }

    private static String noticeButtonId(NoticeItem item, int index) { return "notice-" + item.id() + "-" + index; }

    private void render() {
        if (view != null && !disposed && !view.isClosed()) view.update(viewSpec());
    }

    /** Routes a kit event to the row, action or notice it names. */
    void event(UiEvent event) {
        if (disposed) return;
        for (var part : parts) if (part.event().test(event)) { render(); return; }
        if (event instanceof UiEvent.Action action) {
            for (var row : rows) if (action.id().equals(row.resetId())) { reset(row); return; }
            for (var row : externals) if (action.id().equals(row.resetId())) { resetExternal(row); return; }
            for (var actionRow : actions)
                for (int i = 0; i < actionRow.item().buttons().size(); i++)
                    if (action.id().equals(actionRow.buttonId(i))) { actionRow.item().buttons().get(i).action().run(); render(); return; }
            for (Item item : spec.items)
                if (item instanceof NoticeItem notice)
                    for (int i = 0; i < notice.buttons().size(); i++)
                        if (action.id().equals(noticeButtonId(notice, i))) { notice.buttons().get(i).action().run(); render(); return; }
        }
        for (var row : rows) {
            if (row.editor.event(row.id(), event)) { edited(row); render(); return; }
        }
        for (var row : externals) {
            if (row.editor.event(row.id(), event)) { editedExternal(row); render(); return; }
        }
        render();
    }

    private void editedExternal(ExternalRow row) {
        int value;
        try {
            value = row.editor.value();
        } catch (RuntimeException unreadable) {
            invalidExternal(row, "Not a valid value.");
            return;
        }
        String problem = row.value.error().apply(value);
        if (!problem.isEmpty()) { invalidExternal(row, problem); return; }
        row.invalid = false;
        row.error = "";
        row.pending = value == row.value.saved().getAsInt() ? null : value;
        context.changed().run();
    }

    private void invalidExternal(ExternalRow row, String message) {
        row.invalid = true;
        row.error = message;
        context.changed().run();
    }

    private void resetExternal(ExternalRow row) {
        row.invalid = false;
        row.error = "";
        int target = row.value.defaultValue().getAsInt();
        row.pending = target == row.value.saved().getAsInt() ? null : target;
        row.editor.show(target);
        context.changed().run();
        render();
    }

    private <T> void reset(Row<T> row) {
        row.invalid = false;
        row.error = "";
        var defaults = new java.util.LinkedHashMap<Setting<?>, Optional<?>>();
        defaults.put(row.setting, Optional.empty());
        row.editor.relatedSettings().forEach(setting -> defaults.put(setting, Optional.empty()));
        context.draft().stageAll(defaults);
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
            var defaults = new java.util.LinkedHashMap<Setting<?>, Optional<?>>();
        defaults.put(row.setting, Optional.empty());
        row.editor.relatedSettings().forEach(setting -> defaults.put(setting, Optional.empty()));
        context.draft().stageAll(defaults);
            return;
        }
        Optional<String> problem = row.setting.kind().check(value);
        if (problem.isPresent()) { invalid(row, problem.get()); return; }
        var changes=new java.util.LinkedHashMap<Setting<?>,Optional<?>>();
        // A row with companions stages its own value only when it changes it, so picking a companion leaves the row as saved.
        if (row.editor.relatedSettings().isEmpty() || !Objects.equals(value, context.draft().value(row.setting))) changes.put(row.setting,Optional.of(value));
        try { changes.putAll(row.editor.companionValues()); } catch(RuntimeException unreadable) {invalid(row,"Not a valid value.");return;}
        valid(row);
        context.draft().stageAll(changes);
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
            row.editor.showRelated();
            T current;
            try { current = row.editor.value(); } catch (RuntimeException unreadable) { current = null; }
            if (!Objects.equals(current, value)) row.editor.show(value);
        }
        row.resettable = !Objects.equals(value, row.setting.defaultValue()) || row.editor.relatedSettings().stream()
            .anyMatch(setting -> !Objects.equals(context.draft().value(setting), setting.defaultValue()));
    }

    @Override public boolean modified() {
        return spec.settings().stream().anyMatch(context.draft()::modified) || externalModified();
    }

    /** Whether an external row holds an edit to save; the dialog applies such a page after the draft. */
    public boolean externalModified() { return externals.stream().anyMatch(row -> row.pending != null); }

    @Override public boolean valid() {
        return rows.stream().noneMatch(row -> row.invalid) && externals.stream().noneMatch(row -> row.invalid);
    }

    @Override public void reset() {
        for (Row<?> row : rows) { row.invalid = false; row.error = ""; }
        for (var row : externals) {
            row.invalid = false;
            row.error = "";
            row.pending = null;
            row.editor.show(row.shown());
        }
        context.draft().reset(spec.settings());
        showValues();
    }

    /**
     * Saves the external rows' edits in page order; config.toml rows are saved by the dialog's draft. A failed save keeps
     * the edit and shows the failure under its row (spec section 7); the dialog shows it too.
     */
    @Override public CompletableFuture<Void> apply() {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (var row : externals) {
            Integer value = row.pending;
            if (value == null) continue;
            chain = chain.thenCompose(ignored -> row.value.save().apply(value).whenComplete((saved, failure) -> {
                if (failure != null) onEdt(() -> { row.error = message(failure); render(); });
            }));
        }
        var done = new CompletableFuture<Void>();
        chain.whenComplete((ignored, failure) -> onEdt(() -> {
            if (failure != null) {
                done.completeExceptionally(failure);
                return;
            }
            for (var row : externals) { row.pending = null; row.error = ""; }
            refresh();
            done.complete(null);
        }));
        return done;
    }

    private static String message(Throwable failure) {
        Throwable cause = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        return String.valueOf(cause.getMessage());
    }

    private static void onEdt(Runnable action) {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) action.run();
        else javax.swing.SwingUtilities.invokeLater(action);
    }

    /** Highlights the rows matching {@code query} and scrolls the first into view, leaving focus with the search. */
    @Override public void highlight(String query) {
        String first = null;
        for (Row<?> row : rows) {
            row.highlighted = SearchText.matches(row.searchText, query);
            if (row.highlighted && first == null) first = row.editor.fullSpan() ? row.editor.revealTarget(row.id()) : row.rowId();
        }
        for (var row : externals) {
            row.highlighted = SearchText.matches(row.searchText, query);
            if (row.highlighted && first == null) first = row.rowId();
        }
        render();
        if (first != null && view != null && !view.isClosed()) view.reveal(first);
    }

    @Override public void refresh() {
        for (Row<?> row : rows) row.editor.refresh();
        for (var row : externals) if (row.pending == null && !row.invalid) row.editor.show(row.value.saved().getAsInt());
        for (var part : parts) part.refresh().run();
        if (body != null) showValues();
    }

    /** Remembers the row to focus once the dialog shows this page; {@code fragment} is an external row's id. */
    @Override public void reveal(String fragment) { revealed = fragment; }

    /**
     * Focuses the revealed row once the page shows; until then the request waits. A modal dialog selects its first page
     * before it is visible, when the page has no native peer and focus requests are refused.
     */
    @Override public void selected() {
        if (disposed || revealed == null || view == null || view.isClosed()) return;
        if (body.isShowing()) {
            focusRevealed();
        } else if (waiting == null) {
            waiting = new HierarchyListener() {
                @Override public void hierarchyChanged(HierarchyEvent event) {
                    if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0 || !body.isShowing()) return;
                    stopWaiting();
                    // After the window's own initial focus; selected() waits again if the page was hidden meanwhile.
                    javax.swing.SwingUtilities.invokeLater(SchemaPage.this::selected);
                }
            };
            body.addHierarchyListener(waiting);
        }
    }

    private void focusRevealed() {
        String target = revealed;
        revealed = null;
        for (var row : externals) {
            if (row.id().equals(target)) {
                view.reveal(row.rowId());
                view.focus(row.id());
                return;
            }
        }
    }

    private void stopWaiting() {
        if (waiting == null) return;
        body.removeHierarchyListener(waiting);
        waiting = null;
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        stopWaiting();
        RuntimeException failure=null;
        var cleanup=new java.util.ArrayList<Runnable>();
        if(listening!=null)cleanup.add(listening::close);
        for(var subscription:refreshing)cleanup.add(subscription::close);
        if(view!=null)cleanup.add(view::close);
        for(Row<?> row:rows)cleanup.add(row.editor::dispose);
        for(var part:parts)cleanup.add(part.dispose());
        for(var action:cleanup)try{action.run();}catch(RuntimeException problem){if(failure==null)failure=problem;else failure.addSuppressed(problem);}
        if(failure!=null)throw failure;
    }
}
