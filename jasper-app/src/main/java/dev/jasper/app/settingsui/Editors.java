package dev.jasper.app.settingsui;

import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.TerminalConfig;
import dev.jasper.app.config.AppearanceMode;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingKind;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.ListItem;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.model.UiEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Editor factories for settings, chosen by kind unless a page asks for another. Each editor maps its setting to a typed
 * kit role: switches for booleans, number fields for ranges, dropdowns for choices, text and path fields, searchable
 * choices for themes and font families, ordered lists and name/value rows. EDT only.
 */
public final class Editors {
    private Editors() { }

    /**
     * Builds one setting's editor on a page.
     *
     * @param build creates the editor
     * @param terms extra words the search should match, such as choice names
     * @param lookTerms further words that depend on the page's context, such as the look's copy for a row
     */
    public record Factory<T>(BiFunction<Setting<T>, PageContext, ValueEditor<T>> build, List<String> terms,
                             Function<PageContext, List<String>> lookTerms) {
        public Factory { terms = List.copyOf(terms); java.util.Objects.requireNonNull(lookTerms); }

        /**
         * A factory whose search words do not depend on the page's context.
         *
         * @param build creates the editor
         * @param terms extra words the search should match, such as choice names
         */
        public Factory(BiFunction<Setting<T>, PageContext, ValueEditor<T>> build, List<String> terms) { this(build, terms, context -> List.of()); }

        /**
         * The words the search matches on a page with {@code context}: {@link #terms()} and then {@link #lookTerms()}.
         *
         * @param context the page's context
         * @return the search words
         */
        public List<String> terms(PageContext context) {
            var extra = lookTerms.apply(context);
            if (extra.isEmpty()) return terms;
            var all = new ArrayList<String>(terms); all.addAll(extra);
            return List.copyOf(all);
        }
    }

    /** The editor a setting's kind implies. */
    @SuppressWarnings("unchecked")
    public static <T> Factory<T> byKind(Setting<T> setting) {
        Factory<?> factory = switch (setting.kind()) {
            case SettingKind.Bool ignored -> checkbox();
            case SettingKind.IntegerRange ignored -> stepper("");
            case SettingKind.NumberRange ignored -> number(1, "");
            case SettingKind.Choice<?> ignored -> choice(Map.of());
            case SettingKind.Text ignored -> text();
            case SettingKind.StringList ignored -> textList();
            case SettingKind.StringMap ignored -> table();
        };
        return (Factory<T>) factory;
    }

    /** A switch carrying the setting's label. */
    public static Factory<Boolean> checkbox() {
        return new Factory<>((setting, context) -> new Check(setting.label()), List.of());
    }

    /** A whole-number field within the kind's range; Up and Down step by one. */
    public static Factory<Integer> integer(String unit) {
        return new Factory<>((setting, context) -> {
            var kind = (SettingKind.IntegerRange) setting.kind();
            return new NumberField<>(setting.label(), new Nodes.Numeric(kind.min(), kind.max(), 1, 0), unit, Integer::valueOf);
        }, List.of());
    }

    /** A bounded whole-number stepper with an editable retained value and an integrated unit. */
    public static Factory<Integer> stepper(String unit) {
        return new Factory<>((setting, context) -> {
            var kind = (SettingKind.IntegerRange) setting.kind();
            return new NumberField<>(setting.label(), new Nodes.Numeric(kind.min(), kind.max(), 1, 0), unit, Integer::valueOf, Nodes.FieldPresentation.STEPPER);
        }, List.of());
    }

    /** A bounded duration, keeping its unit in the stepper and its row title concise. */
    public static Factory<Integer> notificationDelay() {
        return new Factory<>((setting,context)->{
            var kind=(SettingKind.IntegerRange)setting.kind();
            return new NumberField<Integer>(setting.label(),new Nodes.Numeric(kind.min(),kind.max(),1,0),"s",Integer::valueOf,Nodes.FieldPresentation.STEPPER){
                @Override public String rowLabel(String label){return "Notify after";}
                @Override public boolean dependent(){return true;}
            };
        },List.of("Notify after"));
    }

    /** A height subordinate to the preceding visibility control. */
    public static Factory<Integer> dependentHeight() {
        return new Factory<>((setting,context)-> {
            var kind=(SettingKind.IntegerRange)setting.kind();
            return new NumberField<Integer>(setting.label(),new Nodes.Numeric(kind.min(),kind.max(),1,0),"px",Integer::valueOf,Nodes.FieldPresentation.STEPPER){
                @Override public String rowLabel(String label){return "Height";}
                @Override public boolean dependent(){return true;}
            };
        },List.of("Height"));
    }

    /** A compact exclusive segmented choice. */
    public static <E> Factory<E> segments(Map<String,String> names) {
        return new Factory<>((setting,context) -> new Pick<>(setting.label(),(SettingKind.Choice<E>)setting.kind(),names,Nodes.ChoiceKind.EXCLUSIVE),List.copyOf(names.values()));
    }

    /** A − value + stepper within the kind's range with up to two decimals; its buttons, Up and Down step by {@code step}. */
    public static Factory<Float> number(double step, String unit) {
        return new Factory<>((setting, context) -> {
            var kind = (SettingKind.NumberRange) setting.kind();
            return new NumberField<>(setting.label(), new Nodes.Numeric(kind.min(), kind.max(), step, DECIMALS), unit, Float::valueOf, Nodes.FieldPresentation.STEPPER);
        }, List.of());
    }

    /** The decimals a number setting shows and accepts, as the former spinners displayed them. */
    static final int DECIMALS = 2;

    /** A dropdown; {@code names} maps tokens to display names, and other tokens are prettified. */
    public static <E> Factory<E> choice(Map<String, String> names) {
        return new Factory<>((setting, context) -> new Pick<>(setting.label(), (SettingKind.Choice<E>) setting.kind(), names), List.copyOf(names.values()));
    }

    public static Factory<String> text() {
        return new Factory<>((setting, context) -> new Line(setting.label()), List.of());
    }

    /** A text field for a data value (a host, a port range, a path or a command), in the data face. */
    public static Factory<String> technical() {
        return new Factory<>((setting, context) -> new Line(setting.label(), Nodes.FieldPresentation.TECHNICAL), List.of());
    }

    /** A font family: free text, with the installed families as suggestions that filter as you type. */
    public static Factory<String> font(boolean monospaced) {
        return new Factory<>((setting, context) -> new FontField(setting.label(), () -> Fonts.families(monospaced)), List.of());
    }

    /** What a font family editor says when no installed family matches the text typed. */
    public static final String NO_FONT_MATCHES = "No installed font matches";

    /**
     * The families a font family editor offers, read once; plugin pages' standard rows offer the same.
     *
     * @param monospaced only the families that draw 'i' and 'W' at the same width
     * @return the installed and bundled families
     */
    public static List<String> fontFamilies(boolean monospaced) { return Fonts.families(monospaced); }

    /** Interface font with a readable platform-default choice, retaining arbitrary typed families. */
    public static Factory<String> interfaceFont() {
        return new Factory<>((setting,context)->new FontField(setting.label(),()->Fonts.families(false),true),List.of("System"));
    }

    /** The terminal scheme entry for {@code match}. */
    static final String SAME_AS_UI_THEME = "Same as UI theme";

    /** {@code terminal.scheme}: "Same as UI theme", then the installed schemes {@code appearance} lists, read again on refresh. */
    public static Factory<String> schemeChoice(AppearanceChoices appearance) {
        return new Factory<>((setting, context) -> new SchemePick(setting.label(), appearance.themes(), appearance.imports(), context, menuIcon(context)),
            List.of(SAME_AS_UI_THEME, "Slate Cobalt Dark", "Slate Cobalt Light", "High Contrast", "Import theme", "Remove theme"));
    }

    /** The glyph of a theme menu's button: the classic composition's ⋯, else the gear (spec 12.2). */
    static String menuIcon(PageContext context) {
        return context.composition() == dev.jasper.app.presentation.Composition.CLASSIC ? "more" : "settings";
    }

    /** The Dark / Light / Match System tiles, then the Dark theme and Light theme rows over the installed themes. */
    public static Factory<AppearanceMode> appearance(AppearanceChoices choices) {
        return new Factory<>((setting, context) -> new AppearanceSelection(choices, context),
            List.of("Dark", "Light", "Match System", "Theme", "Dark theme", "Light theme", "Slate Cobalt", "Import theme", "Remove theme"));
    }

    /** A path field with Browse…, for a program path. */
    public static Factory<String> executable() {
        return new Factory<>((setting, context) -> new Browse(setting.label(), context, "Choose a shell program"), List.of());
    }

    /** A path field with Browse…, for a file path. */
    public static Factory<String> path(String title) {
        return new Factory<>((setting, context) -> new Browse(setting.label(), context, title), List.of());
    }

    /** An ordered list of strings; a field below adds one. */
    public static Factory<List<String>> textList() {
        return new Factory<>((setting, context) -> new Items(setting.label(), Optional.empty()), List.of());
    }

    /** An ordered list of font families; the entry below suggests the installed ones. */
    public static Factory<List<String>> fontList(boolean monospaced) {
        return new Factory<>((setting, context) -> new Items(setting.label(), Optional.of(() -> Fonts.families(monospaced))), List.of());
    }

    /** Name/Value rows. */
    public static Factory<Map<String, String>> table() {
        return new Factory<>((setting, context) -> new Table(), List.of());
    }

    /** The {@code notifications.groups} editor: one line per group {@code choices} lists. */
    public static Factory<Map<String, String>> notificationGroups(NotificationGroupChoices choices) {
        return new Factory<>((setting, context) -> new NotificationGroupsEditor(choices, context.kit(), context.chrome().inlineReset()),
            List.of("group", "card", "sticky", "history only", "off", "mute"));
    }

    /** A switch meaning "use the default", and a number field enabled while it is off; the default removes the key. */
    public static Factory<Float> optionalNumber(String label, double step, String unit) {
        return new Factory<>((setting, context) -> new OptionalNumber(setting, label, step, unit, context.draft()), List.of());
    }

    /** {@code setting}'s effective default under {@code draft} (spec 5.8); the core default for a page made without a draft. */
    static <T> T effectiveDefault(SettingsDraft draft, Setting<T> setting) {
        return draft == null ? setting.defaultValue() : draft.defaultValue(setting);
    }

    /** The detected shell profiles and a button that detects them again; an empty value shows the effective profile. */
    public static Factory<String> profile(ShellChoices shells) {
        return new Factory<>((setting, context) -> new Profile(setting.label(), shells, context.draft()),
            List.of("PowerShell", "Command Prompt", "WSL", "Custom", "default shell"));
    }

    /** {@code icons_and_labels} becomes "Icons and labels". */
    public static String pretty(String token) {
        String spaced = token.replace('_', ' ');
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /** The row's state without its validation message, for a control's companions. */
    private static ControlState plain(ControlState row) { return new ControlState(row.visible(), row.enabled(), row.busy(), ""); }

    /** A control followed by its unit, such as "px", on one line. */
    private static Nodes.Node withUnit(String id, Nodes.Node control, String unit, ControlState state) {
        if (unit.isEmpty()) return control;
        return new Nodes.Stack(id + ":line", plain(state), Nodes.LayoutRole.INLINE,
            List.of(control, new Nodes.Text(id + ":unit", ControlState.READY, unit, Nodes.TextRole.LABEL)));
    }

    private static Nodes.Button iconButton(String id, String icon, String tooltip, ControlState state) {
        return new Nodes.Button(id, state, "", icon, Nodes.ButtonKind.GHOST, id, tooltip);
    }

    static final class Check implements ValueEditor<Boolean> {
        private final String label;
        private boolean checked;
        Check(String label) { this.label = label; }
        @Override public Nodes.Node node(String id, ControlState state) { return new Nodes.Toggle(id, state, label, checked, id, false); }
        @Override public Boolean value() { return checked; }
        @Override public void show(Boolean value) { checked = value; }
        @Override public boolean event(String id, UiEvent event) {
            if (!(event instanceof UiEvent.Toggled toggled) || !toggled.id().equals(id)) return false;
            checked = toggled.checked();
            return true;
        }
        @Override public boolean labelled() { return true; }
    }

    /** A number as text of the bounds' shape; reading text that is no number throws. */
    static class NumberField<T extends Number> implements ValueEditor<T> {
        private final String label;
        final Nodes.Numeric bounds;
        private final String unit;
        private final Function<String, T> parse;
        private final Nodes.FieldPresentation presentation;
        String text = "";
        NumberField(String label, Nodes.Numeric bounds, String unit, Function<String, T> parse) {
            this(label,bounds,unit,parse,Nodes.FieldPresentation.STANDARD);
        }
        NumberField(String label, Nodes.Numeric bounds, String unit, Function<String,T> parse, Nodes.FieldPresentation presentation) {
            this.label = label; this.bounds = bounds; this.unit = unit; this.parse = parse; this.presentation=presentation;
        }
        @Override public Nodes.Node node(String id, ControlState state) {
            if(presentation!=Nodes.FieldPresentation.STANDARD)return new Nodes.Field(id,state,Nodes.FieldKind.NUMBER,text,unit,label,"",Optional.of(bounds),presentation);
            return withUnit(id, new Nodes.Field(id, state, Nodes.FieldKind.NUMBER, text, "", label, "", Optional.of(bounds)), unit, state);
        }
        @Override public T value() { return parse.apply(text.strip()); }
        @Override public void show(T value) { text = format(value, bounds.decimals()); }
        @Override public boolean event(String id, UiEvent event) {
            if (!(event instanceof UiEvent.Edited edited) || !edited.id().equals(id)) return false;
            text = edited.text();
            return true;
        }
        static String format(Number value, int decimals) {
            String plain = new BigDecimal(value.toString()).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            return plain.equals("-0") ? "0" : plain;
        }
    }

    static final class Pick<E> implements ValueEditor<E> {
        private final String label;
        private final List<String> tokens = new ArrayList<>();
        private final List<String> labels = new ArrayList<>();
        private final List<E> values = new ArrayList<>();
        private int selected = -1;
        private final Nodes.ChoiceKind presentation;
        Pick(String label, SettingKind.Choice<E> kind, Map<String, String> names) {
            this(label,kind,names,Nodes.ChoiceKind.DROPDOWN);
        }
        Pick(String label, SettingKind.Choice<E> kind, Map<String,String> names, Nodes.ChoiceKind presentation) {
            this.label=label;this.presentation=presentation;
            for (var token : kind.tokens()) {
                tokens.add(token.getKey());
                labels.add(names.getOrDefault(token.getKey(), pretty(token.getKey())));
                values.add(token.getValue());
            }
        }
        List<String> labels() { return List.copyOf(labels); }
        String selectedLabel() { return selected < 0 ? null : labels.get(selected); }
        @Override public Nodes.Node node(String id, ControlState state) {
            var options = new ArrayList<Option>();
            for (int i = 0; i < tokens.size(); i++) options.add(new Option(tokens.get(i), labels.get(i), Optional.empty()));
            return new Nodes.Choice(id, state, presentation, options, selected < 0 ? "" : tokens.get(selected), label, "");
        }
        @Override public E value() {
            if (selected < 0) throw new IllegalArgumentException("Nothing is chosen");
            return values.get(selected);
        }
        @Override public void show(E value) { selected = values.indexOf(value); }
        @Override public boolean event(String id, UiEvent event) {
            if (!(event instanceof UiEvent.Selected picked) || !picked.id().equals(id)) return false;
            int index = tokens.indexOf(picked.ids().stream().findFirst().orElse(""));
            if (index < 0) return false;
            selected = index;
            return true;
        }
    }

    static final class Line implements ValueEditor<String> {
        private final String label;
        private final Nodes.FieldPresentation presentation;
        String text = "";
        Line(String label) { this(label, Nodes.FieldPresentation.STANDARD); }
        Line(String label, Nodes.FieldPresentation presentation) { this.label = label; this.presentation = presentation; }
        @Override public Nodes.Node node(String id, ControlState state) {
            return new Nodes.Field(id, state, Nodes.FieldKind.TEXT, text, "", label, "", Optional.empty(), presentation);
        }
        @Override public String value() { return text; }
        @Override public void show(String value) { text = value; }
        @Override public boolean event(String id, UiEvent event) {
            if (!(event instanceof UiEvent.Edited edited) || !edited.id().equals(id)) return false;
            text = edited.text();
            return true;
        }
    }

    /** A font family as free text over the installed families, read again on each refresh. */
    static final class FontField implements ValueEditor<String> {
        private final String label;
        private final Supplier<List<String>> families;
        private List<String> offered;
        String text = "";
        private final boolean system;
        FontField(String label, Supplier<List<String>> families) { this(label,families,false); }
        FontField(String label, Supplier<List<String>> families, boolean system) { this.label = label; this.families = families; this.system=system;offered = List.copyOf(families.get()); }
        @Override public String rowLabel(String label){return system?"Font":label;}
        List<String> suggestions() { return offered; }
        @Override public Nodes.Node node(String id, ControlState state) {
            return new Nodes.Choice(id, state, Nodes.ChoiceKind.SEARCHABLE, options(offered), "", label, "", "",
                Optional.of(new Nodes.ChoiceSearch(0, NO_FONT_MATCHES, Optional.of(system && text.equalsIgnoreCase("system") ? "System" : text))));
        }
        @Override public String value() { return text; }
        @Override public void show(String value) { text = value; }
        @Override public boolean event(String id, UiEvent event) {
            if (!(event instanceof UiEvent.Edited edited) || !edited.id().equals(id)) return false;
            text = system && edited.text().equals("System") ? "system" : edited.text();
            return true;
        }
        @Override public void refresh() { offered = List.copyOf(families.get()); }
    }

    private static List<Option> options(List<String> families) {
        var options = new ArrayList<Option>();
        for (int i = 0; i < families.size(); i++) options.add(new Option("family-" + i, families.get(i), Optional.empty()));
        return options;
    }

    static final class Browse implements ValueEditor<String> {
        private final String label;
        private final PageContext context;
        private final String title;
        String text = "";
        Browse(String label, PageContext context, String title) { this.label = label; this.context = context; this.title = title; }
        void browse() {
            String current = text;
            context.chooseFile().apply(title, current.isBlank() ? Optional.empty() : Optional.of(java.nio.file.Path.of(current)))
                .ifPresent(path -> text = path.toString());
        }
        @Override public Nodes.Node node(String id, ControlState state) {
            return new Nodes.Field(id, state, Nodes.FieldKind.PATH, text, "", label, id + ":browse");
        }
        @Override public String value() { return text; }
        @Override public void show(String value) { text = value; }
        @Override public boolean event(String id, UiEvent event) {
            if (event instanceof UiEvent.Action action && action.id().equals(id + ":browse")) {
                String before = text;
                browse();
                return !before.equals(text);
            }
            if (!(event instanceof UiEvent.Edited edited) || !edited.id().equals(id)) return false;
            text = edited.text();
            return true;
        }
    }

    /**
     * An ordered list: rows move and remove themselves; the entry below adds, with font suggestions when given. Rows are
     * keyed by position, so the editor keeps the selected row and moves it with its item: the classic side column acts on
     * the selection.
     */
    static final class Items implements ValueEditor<List<String>> {
        private final String label;
        private final Optional<Supplier<List<String>>> suggestions;
        private final List<String> items = new ArrayList<>();
        String pending = "";
        /** The selected row's index, or -1. */
        int selected = -1;
        Items(String label, Optional<Supplier<List<String>>> suggestions) { this.label = label; this.suggestions = suggestions; }

        @Override public Nodes.Node node(String id, ControlState state) {
            var rows = new ArrayList<ListItem>();
            for (int i = 0; i < items.size(); i++)
                rows.add(new ListItem("item-" + i, Optional.empty(),
                    List.of(new Nodes.Text(id + ":item-" + i, ControlState.READY, items.get(i), Nodes.TextRole.BODY)), List.of(), List.of()));
            var list = new Nodes.ItemList(id, state, Nodes.ListKind.ORDERED, rows, selectedIds(selected, items.size()), "", List.of());
            // Enter in the entry adds it (the entry's own action); an empty entry leaves Enter to the dialog's OK.
            String submit = pending.isBlank() ? "" : id + ":add";
            Nodes.Node entry = suggestions.isPresent()
                ? new Nodes.Choice(id + ":new", plain(state), Nodes.ChoiceKind.SEARCHABLE, options(suggestions.get().get()), "", "Add to " + label, id + ":add", "",
                    Optional.of(new Nodes.ChoiceSearch(0, NO_FONT_MATCHES, Optional.of(pending))))
                : new Nodes.Field(id + ":new", plain(state), Nodes.FieldKind.TEXT, pending, "Add…", "Add to " + label, submit);
            var addState = new ControlState(state.visible(), state.enabled() && !pending.isBlank(), state.busy(), "");
            var add = new Nodes.Button(id + ":add", addState, "Add", "add", Nodes.ButtonKind.SECONDARY, id + ":add", "");
            return new Nodes.Stack(id + ":editor", plain(state), Nodes.LayoutRole.VERTICAL,
                List.of(list, new Nodes.Stack(id + ":entry", plain(state), Nodes.LayoutRole.INLINE, List.of(entry, add))));
        }

        @Override public List<String> value() { return List.copyOf(items); }
        @Override public void show(List<String> value) { items.clear(); items.addAll(value); if (selected >= items.size()) selected = -1; }

        @Override public boolean event(String id, UiEvent event) {
            switch (event) {
                case UiEvent.Edited edited when edited.id().equals(id + ":new") -> { pending = edited.text(); return false; }
                case UiEvent.Selected chosen when chosen.id().equals(id) -> {
                    selected = chosen.ids().stream().mapToInt(this::index).filter(i -> i >= 0).min().orElse(-1);
                    return false;
                }
                case UiEvent.Action action when action.id().equals(id + ":add") -> {
                    if (pending.isBlank()) return false;
                    items.add(pending);
                    pending = "";
                    return true;
                }
                case UiEvent.Removed removed when removed.id().equals(id) -> {
                    int index = index(removed.itemId());
                    if (index < 0) return false;
                    items.remove(index);
                    selected = afterRemoval(selected, index);
                    return true;
                }
                case UiEvent.Reordered moved when moved.id().equals(id) -> {
                    int index = index(moved.itemId());
                    if (index < 0 || moved.destination() >= items.size()) return false;
                    items.add(moved.destination(), items.remove(index));
                    selected = afterMove(selected, index, moved.destination());
                    return true;
                }
                default -> { return false; }
            }
        }

        private int index(String itemId) {
            if (!itemId.startsWith("item-")) return -1;
            int index = Integer.parseInt(itemId.substring("item-".length()));
            return index < items.size() ? index : -1;
        }
    }

    /** An ordered list's {@code item-N} selection for {@code selected}, or none. */
    public static Set<String> selectedIds(int selected, int size) { return selected >= 0 && selected < size ? Set.of("item-" + selected) : Set.of(); }

    /** Where the selected row {@code selected} is once row {@code removed} is gone: -1 when it was the one removed. */
    public static int afterRemoval(int selected, int removed) { return selected == removed ? -1 : selected > removed ? selected - 1 : selected; }

    /** Where the selected row {@code selected} is once row {@code from} moves to {@code to}: with its item. */
    public static int afterMove(int selected, int from, int to) {
        if (selected < 0) return selected;
        if (selected == from) return to;
        if (from < selected && to >= selected) return selected - 1;
        if (from > selected && to <= selected) return selected + 1;
        return selected;
    }

    /** Name/Value rows; a blank name is left out of the value. */
    static final class Table implements ValueEditor<Map<String, String>> {
        private final List<String[]> rows = new ArrayList<>();

        @Override public Nodes.Node node(String id, ControlState state) {
            var lines = new ArrayList<Nodes.Node>();
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i);
                lines.add(new Nodes.Stack(id + ":row-" + i, plain(state), Nodes.LayoutRole.INLINE, List.of(
                    new Nodes.Field(id + ":name-" + i, plain(state), Nodes.FieldKind.TEXT, row[0], "Name", "Name", ""),
                    new Nodes.Field(id + ":value-" + i, plain(state), Nodes.FieldKind.TEXT, row[1], "Value", "Value", ""),
                    iconButton(id + ":remove-" + i, "remove", "Remove variable", plain(state)))));
            }
            lines.add(new Nodes.Button(id + ":add", plain(state), "Add variable", "add", Nodes.ButtonKind.SECONDARY, id + ":add", ""));
            return new Nodes.Stack(id, plain(state), Nodes.LayoutRole.VERTICAL, lines);
        }

        void addRow(String name, String value) { rows.add(new String[]{name, value}); }

        @Override public Map<String, String> value() {
            var values = new LinkedHashMap<String, String>();
            for (var row : rows) if (!row[0].isBlank()) values.put(row[0], row[1]);
            return values;
        }

        @Override public void show(Map<String, String> value) {
            rows.clear();
            new TreeMap<>(value).forEach(this::addRow);
        }

        @Override public boolean event(String id, UiEvent event) {
            if (event instanceof UiEvent.Action action && action.id().equals(id + ":add")) { addRow("", ""); return true; }
            if (event instanceof UiEvent.Action action && action.id().startsWith(id + ":remove-")) {
                int index = Integer.parseInt(action.id().substring((id + ":remove-").length()));
                if (index >= rows.size()) return false;
                rows.remove(index);
                return true;
            }
            if (event instanceof UiEvent.Edited edited) {
                for (int i = 0; i < rows.size(); i++) {
                    if (edited.id().equals(id + ":name-" + i)) { rows.get(i)[0] = edited.text(); return true; }
                    if (edited.id().equals(id + ":value-" + i)) { rows.get(i)[1] = edited.text(); return true; }
                }
            }
            return false;
        }
    }

    static final class OptionalNumber implements ValueEditor<Float> {
        private final String label;
        private final NumberField<Float> number;
        private final float unset;
        private final float fallback;
        private boolean useDefault;
        OptionalNumber(Setting<Float> setting, String label, double step, String unit, SettingsDraft draft) {
            var kind = (SettingKind.NumberRange) setting.kind();
            this.label = label;
            unset = effectiveDefault(draft, setting);
            fallback = setting.example().orElse((float) kind.min());
            number = new NumberField<>(setting.label(), new Nodes.Numeric(kind.min(), kind.max(), step, DECIMALS), unit, Float::valueOf, Nodes.FieldPresentation.STEPPER);
            number.show(fallback);
        }
        boolean usesDefault() { return useDefault; }
        @Override public Nodes.Node node(String id, ControlState state) {
            var toggle = new Nodes.Toggle(id + ":default", plain(state), label, useDefault, id + ":default", true);
            var field = number.node(id, new ControlState(state.visible(), state.enabled() && !useDefault, state.busy(), state.error()));
            return new Nodes.Stack(id + ":optional", plain(state), Nodes.LayoutRole.INLINE, List.of(toggle, field));
        }
        @Override public Float value() { return useDefault ? unset : number.value(); }
        @Override public void show(Float value) {
            useDefault = value == unset;
            number.show(useDefault ? fallback : value);
        }
        @Override public boolean event(String id, UiEvent event) {
            if (event instanceof UiEvent.Toggled toggled && toggled.id().equals(id + ":default")) { useDefault = toggled.checked(); return true; }
            return number.event(id, event);
        }
        @Override public boolean removesAtDefault() { return true; }
    }

    static final class Profile implements ValueEditor<String> {
        private final String label;
        private final List<String> ids = new ArrayList<>();
        private final List<String> labels = new ArrayList<>();
        private final ShellChoices shells;
        private final SettingsDraft draft;
        private final Subscription listening;
        private Runnable render = () -> { };
        private int index = -1;
        private String shown = "";
        /** The id the dropdown displays for {@link #shown} or the last edit; picking it again is no edit. */
        private String current = "";
        /** How many entries are choices; any after them is the one "(not detected)" entry. */
        private int detected;

        Profile(String label, ShellChoices shells, SettingsDraft draft) {
            this.label = label;
            this.shells = shells;
            this.draft = draft;
            fill();
            listening = shells.onChanged().apply(() -> { fill(); render.run(); });
        }

        /** Every entry's label, the undetected one included; for tests. */
        List<String> labels() { return List.copyOf(labels); }
        String selectedLabel() { return index < 0 ? null : labels.get(index); }

        /** Rebuilds the entries from the current choices, keeping the selection. */
        void fill() {
            String keep = index < 0 ? shown : ids.get(index);
            ids.clear();
            labels.clear();
            for (ShellChoices.Choice choice : shells.choices().get()) add(choice.id(), choice.label());
            if (!ids.contains(ShellChoices.CUSTOM)) add(ShellChoices.CUSTOM, "Custom");
            detected = ids.size();
            index = -1;
            select(keep);
        }

        private void add(String id, String label) { ids.add(id); labels.add(label); }

        private void select(String value) {
            // At most one undetected entry: the one for the value shown now.
            while (ids.size() > detected) { ids.removeLast(); labels.removeLast(); }
            String wanted = effective(value);
            // WSL distribution names ignore case, as the launcher's resolution does: select the detected spelling.
            String id = ids.stream().filter(known -> known.equals(wanted)
                || known.startsWith(WSL_PREFIX) && wanted.startsWith(WSL_PREFIX) && known.equalsIgnoreCase(wanted))
                .findFirst().orElse(wanted);
            if (!ids.contains(id)) add(id, id + " (not detected)");
            index = ids.indexOf(id);
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

        @Override public Nodes.Node node(String id, ControlState state) {
            var options = new ArrayList<Option>();
            for (int i = 0; i < labels.size(); i++) options.add(new Option("profile-" + i, labels.get(i), Optional.empty()));
            var choice = new Nodes.Choice(id, state, Nodes.ChoiceKind.DROPDOWN, options, index < 0 ? "" : "profile-" + index, label, "");
            return new Nodes.Stack(id + ":line", plain(state), Nodes.LayoutRole.INLINE,
                List.of(choice, iconButton(id + ":detect", "refresh", "Detect shells again", plain(state))));
        }

        @Override public String value() { return index < 0 ? "" : ids.get(index); }

        @Override public void show(String value) { shown = value; select(value); }

        /**
         * A pick that differs from what the dropdown displayed. A pick of the displayed entry again would turn an empty
         * setting shown as its effective profile into an explicit one, so it is no edit.
         */
        @Override public boolean event(String id, UiEvent event) {
            if (event instanceof UiEvent.Action action && action.id().equals(id + ":detect")) { shells.refresh().run(); return false; }
            if (!(event instanceof UiEvent.Selected picked) || !picked.id().equals(id)) return false;
            String option = picked.ids().stream().findFirst().orElse("");
            if (!option.startsWith("profile-")) return false;
            int chosen = Integer.parseInt(option.substring("profile-".length()));
            if (chosen < 0 || chosen >= ids.size() || ids.get(chosen).equals(current)) return false;
            index = chosen;
            current = ids.get(chosen);
            return true;
        }

        @Override public void onChanged(Runnable render) { this.render = render; }
        @Override public void dispose() { listening.close(); }
    }
}
