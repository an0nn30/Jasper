package dev.jasper.sdk.ui;

import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Objects;

/** Immutable, semantic view vocabulary; no pixel dimensions, delegates or secret values. */
public final class Nodes {
    private Nodes() {}
    public sealed interface Node permits Text, Icon, Button, MenuButton, StatusDot, Field, Secret, Choice, Toggle, Chip, Progress, Strength, Stack, FormRow, SettingRow, Card, Section, Empty, ItemList, Header, Panel, Form, Split, Table, Tree, Breadcrumbs, Custom {
        String id();
        ControlState state();
    }
    public enum TextRole { HERO_TITLE, DETAIL_TITLE, WINDOW_TITLE, PANEL_TITLE, BODY, BODY_STRONG, LABEL, DESCRIPTION, CAPTION, SECTION_LABEL, COUNT, CHIP, MONO, MONO_SMALL }
    public enum Hue { BLUE, GREEN, AMBER, PURPLE, NEUTRAL, NONE }
    public enum IconSize { CAPTION, CONTROL, RAIL, EMPTY, HERO }
    public enum ButtonKind { PRIMARY, SECONDARY, GHOST, DESTRUCTIVE_GHOST, LINK }
    public enum StatusKind { SUCCESS, WARNING, DANGER, IDLE }
    public enum FieldKind { TEXT, SEARCH, PORT, PATH, TEXTAREA, NUMBER }
    public enum ChoiceKind { DROPDOWN, PICKER, SEGMENTED, SEARCHABLE }
    public enum CardKind { FORM, SETTINGS, MIXED }
    public enum SelectionMode { SINGLE, MULTIPLE }
    /**
     * RESULTS is a one-line result list driven by an external field (a command palette): it never takes focus, the host
     * moves its selection, a left press activates a row, and fixed row and heading heights keep it steady.
     */
    public enum ListKind { SINGLE, DOUBLE, COMPACT, ORDERED, RESULTS }
    public enum FormKind { HERO, SHEET, FULL, SETTINGS }
    public enum LayoutRole { VERTICAL, INLINE, ACTIONS, HERO, DETAIL, FOOTER, NAVIGATION }
    public enum SplitKind { SIDEBAR_DETAIL, THREE_COLUMN, DOCUMENT }
    public enum CustomKind { EDITOR, TERMINAL, CHART, PREVIEW }
    /** STANDARD reserves the scroll bar's width; OVERLAY shows a transient thumb over the rows only while scrolling. */
    public enum ScrollPolicy { STANDARD, OVERLAY }

    public record Text(String id, ControlState state, String text, TextRole role) implements Node {
        public Text {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(text);
            Objects.requireNonNull(role);
        }
    }

    public record Icon(String id, ControlState state, String name, Hue hue, IconSize size, String accessibleName) implements Node {
        public Icon {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.label(name, "name");
            Objects.requireNonNull(hue);
            Objects.requireNonNull(size);
            ModelChecks.label(accessibleName, "accessibleName");
        }
    }

    public record Button(String id, ControlState state, String label, String icon, ButtonKind kind, String action, String tooltip) implements Node {
        public Button {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.button(label, icon, tooltip);
            Objects.requireNonNull(kind);
            ModelChecks.label(action, "action");
        }
    }

    public record MenuButton(String id, ControlState state, String label, String icon, ButtonKind kind, String action, List<MenuItem> items, String tooltip) implements Node {
        public MenuButton {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.button(label, icon, tooltip);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(action);
            items = List.copyOf(items);
            ModelChecks.unique(items, MenuItem::id);
            if (items.isEmpty()) throw new IllegalArgumentException("Menu must have items");
        }
    }

    public record StatusDot(String id, ControlState state, StatusKind kind) implements Node {
        public StatusDot {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(kind);
        }
    }

    /**
     * Bounds and shape of a NUMBER field: the kit admits only numbers of this shape and steps by {@code step} with the
     * arrow keys, clamped to the bounds. The domain still validates the value and reports problems through state.
     */
    public record Numeric(double min, double max, double step, int decimals) {
        public Numeric {
            if (!Double.isFinite(min) || !Double.isFinite(max) || !Double.isFinite(step)) throw new IllegalArgumentException("Numeric bounds must be finite");
            if (min > max) throw new IllegalArgumentException("Numeric minimum exceeds maximum");
            if (step <= 0) throw new IllegalArgumentException("Numeric step must be positive");
            if (decimals < 0 || decimals > 6) throw new IllegalArgumentException("Numeric decimals must be in 0..6");
        }
    }

    /** A NUMBER field states its {@link Numeric} shape; other kinds have none. */
    public record Field(String id, ControlState state, FieldKind kind, String value, String hint, String label, String trailingAction, Optional<Numeric> numeric) implements Node {
        public Field {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(value);
            Objects.requireNonNull(hint);
            ModelChecks.label(label, "label");
            Objects.requireNonNull(trailingAction);
            Objects.requireNonNull(numeric);
            if ((kind == FieldKind.NUMBER) != numeric.isPresent()) throw new IllegalArgumentException("Numeric bounds belong to NUMBER fields only, which require them");
        }
        public Field(String id, ControlState state, FieldKind kind, String value, String hint, String label, String trailingAction) {
            this(id, state, kind, value, hint, label, trailingAction, Optional.empty());
        }
    }

    public enum SecretKind { INPUT, DETAIL }

    public record Secret(String id, ControlState state, String hint, String label, String revealGroup, SecretKind kind, String copyAction) implements Node {
        public Secret {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(hint);
            ModelChecks.label(label, "label");
            Objects.requireNonNull(revealGroup); Objects.requireNonNull(kind); Objects.requireNonNull(copyAction);
        }
        public Secret(String id, ControlState state, String hint, String label, String revealGroup) {
            this(id,state,hint,label,revealGroup,SecretKind.INPUT,"");
        }
    }

    /**
     * Type-to-filter configuration of a SEARCHABLE choice. The first {@code pinned} options always show; {@code noMatches}
     * is the line shown when typing matches nothing. A present {@code entry} makes the value free text (options become
     * suggestions): the kit reports typed or picked text as {@link UiEvent.Edited}, and the choice has no selected id.
     */
    public record ChoiceSearch(int pinned, String noMatches, Optional<String> entry) {
        public ChoiceSearch {
            if (pinned < 0) throw new IllegalArgumentException("Negative pinned count");
            ModelChecks.label(noMatches, "noMatches");
            Objects.requireNonNull(entry);
        }
    }

    public record Choice(String id, ControlState state, ChoiceKind kind, List<Option> options, String selectedId, String label, String action, String icon, Optional<ChoiceSearch> search) implements Node {
        public Choice {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(kind);
            options = List.copyOf(options);
            Objects.requireNonNull(selectedId);
            ModelChecks.label(label, "label");
            Objects.requireNonNull(action);
            Objects.requireNonNull(icon);
            Objects.requireNonNull(search);
            if (kind != ChoiceKind.PICKER && !icon.isEmpty()) throw new IllegalArgumentException("Icon cue requires PICKER");
            if ((kind == ChoiceKind.SEARCHABLE) != search.isPresent()) throw new IllegalArgumentException("Search configuration belongs to SEARCHABLE choices only, which require it");
            if (search.isPresent() && search.get().pinned() > options.size()) throw new IllegalArgumentException("More pinned entries than options");
            if (search.isPresent() && search.get().entry().isPresent() && !selectedId.isEmpty()) throw new IllegalArgumentException("Free entry owns the value; no option is selected");
            ModelChecks.unique(options, Option::id);
        }
        /** A choice without search; SEARCHABLE requires the full constructor. */
        public Choice(String id, ControlState state, ChoiceKind kind, List<Option> options, String selectedId, String label, String action, String icon) {
            this(id, state, kind, options, selectedId, label, action, icon, Optional.empty());
        }
        /** Existing pickers retain the key cue; use the full constructor for an explicit cue or none. */
        public Choice(String id, ControlState state, ChoiceKind kind, List<Option> options, String selectedId, String label, String action) {
            this(id, state, kind, options, selectedId, label, action, kind == ChoiceKind.PICKER ? "key" : "");
        }
    }

    public record Toggle(String id, ControlState state, String label, boolean checked, String action, boolean compact) implements Node {
        public Toggle {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.label(label, "label");
            ModelChecks.label(action, "action");
        }
    }

    public record Chip(String id, ControlState state, String label, Hue hue) implements Node {
        public Chip {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.label(label, "label");
            Objects.requireNonNull(hue);
        }
    }

    public record Progress(String id, ControlState state, double fraction, String label) implements Node {
        public Progress {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(label);
            if (!Double.isFinite(fraction) || (fraction != -1 && (fraction < 0 || fraction > 1))) throw new IllegalArgumentException("Progress must be -1 or in 0..1");
        }
    }

    public record Strength(String id, ControlState state, int filled, String label) implements Node {
        public Strength {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(label);
            if (filled < 0 || filled > 4) throw new IllegalArgumentException("Strength must be in 0..4");
        }
    }

    public record Stack(String id, ControlState state, LayoutRole role, List<Node> children) implements Node {
        public Stack {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(role);
            children = List.copyOf(children);
        }
    }

    /** {@code highlighted} marks a row matching the host's current search. */
    public record FormRow(String id, ControlState state, String label, String caption, Node control, Optional<Node> trailing, boolean highlighted) implements Node {
        public FormRow {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(label);
            Objects.requireNonNull(caption);
            Objects.requireNonNull(control);
            Objects.requireNonNull(trailing);
        }
        public FormRow(String id, ControlState state, String label, String caption, Node control, Optional<Node> trailing) {
            this(id, state, label, caption, control, trailing, false);
        }
    }

    /** {@code highlighted} marks a row matching the host's current search. */
    public record SettingRow(String id, ControlState state, String title, String description, Node control, boolean dependent, boolean highlighted) implements Node {
        public SettingRow {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.label(title, "title");
            Objects.requireNonNull(description);
            Objects.requireNonNull(control);
        }
        public SettingRow(String id, ControlState state, String title, String description, Node control, boolean dependent) {
            this(id, state, title, description, control, dependent, false);
        }
    }

    public record Card(String id, ControlState state, CardKind kind, List<Node> children) implements Node {
        public Card {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(kind);
            children = List.copyOf(children);
        }
    }

    public record Section(String id, ControlState state, String title, Node body) implements Node {
        public Section {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.label(title, "title");
            Objects.requireNonNull(body);
        }
    }

    public record Empty(String id, ControlState state, Node icon, String title, String description, List<Node> actions) implements Node {
        public Empty {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(icon);
            ModelChecks.label(title, "title");
            Objects.requireNonNull(description);
            actions = List.copyOf(actions);
        }
    }

    /**
     * Virtual row collection. MULTIPLE shows checkboxes; addAction is a footer, never a data item. A present
     * {@code visibleRows} is how many selectable rows the viewport shows before it scrolls (headings above them add their
     * own height); absent keeps the kit default. Headings are never selected and ORDERED lists have none.
     */
    public record ItemList(String id, ControlState state, ListKind kind, List<ListItem> items, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode, Optional<Button> addAction, OptionalInt visibleRows) implements Node {
        public ItemList {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(kind);
            items = List.copyOf(items);
            selectedIds = Set.copyOf(selectedIds);
            Objects.requireNonNull(activateAction);
            contextActions = List.copyOf(contextActions);
            Objects.requireNonNull(selectionMode); Objects.requireNonNull(addAction); Objects.requireNonNull(visibleRows);
            if (selectionMode == SelectionMode.SINGLE && selectedIds.size() > 1) throw new IllegalArgumentException("Single selection accepts at most one item");
            if (visibleRows.isPresent() && visibleRows.getAsInt() < 1) throw new IllegalArgumentException("Visible rows must be positive");
            ModelChecks.unique(items, ListItem::id);
            ModelChecks.unique(contextActions, MenuItem::id);
            for (var item : items) if (item.heading()) {
                if (kind == ListKind.ORDERED) throw new IllegalArgumentException("Ordered lists have no headings");
                if (selectedIds.contains(item.id())) throw new IllegalArgumentException("A heading is never selected");
            }
        }
        public ItemList(String id, ControlState state, ListKind kind, List<ListItem> items, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode, Optional<Button> addAction) {
            this(id, state, kind, items, selectedIds, activateAction, contextActions, selectionMode, addAction, OptionalInt.empty());
        }
        public ItemList(String id, ControlState state, ListKind kind, List<ListItem> items, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions) {
            this(id, state, kind, items, selectedIds, activateAction, contextActions, SelectionMode.SINGLE, Optional.empty());
        }
    }

    /** Optional total collection count: zero hides the panel count/navigation, absent leaves navigation declared visibility intact. */
    public record Header(String id, ControlState state, String title, OptionalInt count, Optional<Node> context, Optional<Node> primary, List<MenuItem> overflow) implements Node {
        public Header {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.label(title, "title");
            Objects.requireNonNull(count);
            Objects.requireNonNull(context);
            Objects.requireNonNull(primary);
            overflow = List.copyOf(overflow);
            ModelChecks.unique(overflow, MenuItem::id);
            if (count.isPresent() && count.getAsInt() < 0) throw new IllegalArgumentException("Negative count");
            if (primary.isPresent() && !(primary.get() instanceof Button || primary.get() instanceof MenuButton)) throw new IllegalArgumentException("Primary must be Button or MenuButton");
        }
    }

    public record Panel(String id, ControlState state, Header header, Optional<Node> searchOrNavigation, Node body, Optional<Node> footer) implements Node {
        public Panel {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(header);
            Objects.requireNonNull(searchOrNavigation);
            Objects.requireNonNull(body);
            Objects.requireNonNull(footer);
        }
    }

    /** Form content only; cancellation uses an explicit action key, blank means none. */
    public record Form(String id, ControlState state, FormKind kind, String title, Optional<Node> hero, List<Node> sections, Optional<Node> preview, List<Button> actions, String cancelAction) implements Node {
        public Form {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(kind);
            ModelChecks.label(title, "title");
            Objects.requireNonNull(hero);
            sections = List.copyOf(sections);
            Objects.requireNonNull(preview);
            actions = List.copyOf(actions);
            Objects.requireNonNull(cancelAction);
            if (actions.stream().filter(button -> button.kind() == ButtonKind.PRIMARY).count() > 1)
                throw new IllegalArgumentException("Form supports at most one primary action");
        }
        public Form(String id, ControlState state, FormKind kind, String title, Optional<Node> hero,
                    List<Node> sections, Optional<Node> preview, List<Button> actions) {
            this(id, state, kind, title, hero, sections, preview, actions, "");
        }
    }

    public record Split(String id, ControlState state, SplitKind kind, List<Node> children) implements Node {
        public Split {
            ModelChecks.id(id); Objects.requireNonNull(state);
            Objects.requireNonNull(kind);
            children = List.copyOf(children);
            if (children.size() < 2) throw new IllegalArgumentException("Split needs at least two children");
        }
    }

    /** Column collection with explicit single or checkbox-based multiple selection and an explicit scroll policy. */
    public record Table(String id, ControlState state, List<Column> columns, List<TableRow> rows, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode, ScrollPolicy scroll) implements Node {
        public Table {
            ModelChecks.id(id); Objects.requireNonNull(state);
            columns = List.copyOf(columns);
            rows = List.copyOf(rows);
            selectedIds = Set.copyOf(selectedIds);
            Objects.requireNonNull(activateAction);
            contextActions = List.copyOf(contextActions);
            Objects.requireNonNull(selectionMode); Objects.requireNonNull(scroll);
            if (selectionMode == SelectionMode.SINGLE && selectedIds.size() > 1) throw new IllegalArgumentException("Single selection accepts at most one item");
            ModelChecks.unique(columns, Column::id);
            ModelChecks.unique(rows, TableRow::id);
            ModelChecks.unique(contextActions, MenuItem::id);
        }
        public Table(String id, ControlState state, List<Column> columns, List<TableRow> rows, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions, SelectionMode selectionMode) {
            this(id, state, columns, rows, selectedIds, activateAction, contextActions, selectionMode, ScrollPolicy.STANDARD);
        }
        public Table(String id, ControlState state, List<Column> columns, List<TableRow> rows, Set<String> selectedIds, String activateAction, List<MenuItem> contextActions) {
            this(id, state, columns, rows, selectedIds, activateAction, contextActions, SelectionMode.SINGLE);
        }
    }

    public record Tree(String id, ControlState state, List<TreeItem> roots, Set<String> expandedIds, Set<String> selectedIds, String activateAction) implements Node {
        public Tree {
            ModelChecks.id(id); Objects.requireNonNull(state);
            roots = List.copyOf(roots);
            expandedIds = Set.copyOf(expandedIds);
            selectedIds = Set.copyOf(selectedIds);
            Objects.requireNonNull(activateAction);
        }
    }

    /**
     * A path of crumbs that can turn into a typed path. Clicking a crumb reports {@link UiEvent.Selected} with its id;
     * a crumb's context action reports only its {@link UiEvent.Action} (it does not navigate). Clicking the free area
     * reports {@code editAction}; {@code focus(id)} focuses the field while editing. While {@code editing} the kit shows a text field holding
     * {@code value}, reports typing as {@link UiEvent.Edited}, Enter as {@code submitAction} and Escape or leaving the
     * field as {@code cancelAction}. When the crumbs do not fit, the leading ones slide out so the last stays visible.
     */
    public record Breadcrumbs(String id, ControlState state, List<Crumb> crumbs, String label, boolean editing, String value, String editAction, String submitAction, String cancelAction) implements Node {
        public Breadcrumbs {
            ModelChecks.id(id); Objects.requireNonNull(state);
            crumbs = List.copyOf(crumbs); ModelChecks.unique(crumbs, Crumb::id);
            ModelChecks.label(label, "label");
            Objects.requireNonNull(value);
            Objects.requireNonNull(editAction); Objects.requireNonNull(submitAction); Objects.requireNonNull(cancelAction);
            if (editing && (submitAction.isBlank() || cancelAction.isBlank())) throw new IllegalArgumentException("Editing needs submit and cancel actions");
        }
    }

    public record Custom(String id, ControlState state, String contentId, CustomKind kind, String accessibleName) implements Node {
        public Custom {
            ModelChecks.id(id); Objects.requireNonNull(state);
            ModelChecks.id(contentId);
            Objects.requireNonNull(kind);
            ModelChecks.label(accessibleName, "accessibleName");
        }
    }
}
