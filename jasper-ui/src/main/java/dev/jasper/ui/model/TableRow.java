package dev.jasper.ui.model;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * Immutable tablerow descriptor. A {@code muted} row shows every cell in the de-emphasized text color (for example
 * entries that cannot be chosen); a present {@code contextActions} replaces the table's context menu for this row, and
 * an empty list means the row has none. {@code tooltip} is the row's literal tooltip and accessible description, shown
 * over every cell; blank shows none.
 * <p>
 * A column's content is a plain cell ({@code cells}) or a cell node ({@code cellNodes}), never both. A cell node is a
 * {@link Nodes.Text}, {@link Nodes.StatusDot}, {@link Nodes.Icon} or {@link Nodes.Chip}, an INLINE {@link Nodes.Stack}
 * of those, or a DROPDOWN {@link Nodes.Choice}, which reports {@link UiEvent.Selected} and is drawn without a border
 * (its value, then its chevron) until it is hovered or focused. A muted row dims its plain cells and its Text cell
 * nodes. Cell nodes are view nodes, so their ids are unique in the view; {@code cellNodes} iterates in column-id order.
 * <p>
 * A heading row ({@link #heading(String, String, OptionalInt, String)}) has a {@link Heading} and nothing else: it
 * spans the row, groups the rows below it, and is never selected or activated.
 *
 * @since 0.18.0 for {@code cellNodes} and {@code heading}
 */
public record TableRow(String id, Map<String,String> cells, Optional<String> icon, boolean muted, Optional<List<MenuItem>> contextActions, String tooltip,
                       Map<String, Nodes.Node> cellNodes, Optional<TableRow.Heading> heading) {
    /**
     * A heading row's title and an optional count, shown muted at the row's end and followed by {@code countNoun}: a
     * lowercase singular noun to which the presentation adds "s" unless the count is 1 ("6 groups", "1 group"). A blank
     * noun shows the bare number; a noun needs a count.
     */
    public record Heading(String title, OptionalInt count, String countNoun) {
        public Heading {
            ModelChecks.label(title, "title");
            Objects.requireNonNull(count); Objects.requireNonNull(countNoun);
            if (count.isPresent() && count.getAsInt() < 0) throw new IllegalArgumentException("Negative count");
            if (!countNoun.isBlank() && count.isEmpty()) throw new IllegalArgumentException("A count noun needs a count");
            if (!countNoun.equals(countNoun.strip().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("A count noun is lowercase, without spaces around it");
        }
    }
    public TableRow {
        ModelChecks.id(id);
        cells = Map.copyOf(cells); cells.keySet().forEach(ModelChecks::id); Objects.requireNonNull(icon); icon.ifPresent(name -> ModelChecks.label(name, "icon"));
        Objects.requireNonNull(contextActions); contextActions = contextActions.map(List::copyOf); contextActions.ifPresent(items -> ModelChecks.unique(items, MenuItem::id));
        Objects.requireNonNull(tooltip);
        Objects.requireNonNull(cellNodes); Objects.requireNonNull(heading);
        var nodes = new TreeMap<String, Nodes.Node>();
        for (var entry : cellNodes.entrySet()) {
            ModelChecks.id(entry.getKey());
            cell(Objects.requireNonNull(entry.getValue(), "cell node"));
            if (cells.containsKey(entry.getKey())) throw new IllegalArgumentException("A column is a plain cell or a cell node, not both: " + entry.getKey());
            nodes.put(entry.getKey(), entry.getValue());
        }
        cellNodes = Collections.unmodifiableMap(nodes);
        if (heading.isPresent() && (!cells.isEmpty() || !cellNodes.isEmpty() || icon.isPresent() || muted || contextActions.isPresent() || !tooltip.isBlank()))
            throw new IllegalArgumentException("A heading row has a title and a count only");
    }
    /** A row without cell nodes that is not a heading. */
    public TableRow(String id, Map<String,String> cells, Optional<String> icon, boolean muted, Optional<List<MenuItem>> contextActions, String tooltip) {
        this(id, cells, icon, muted, contextActions, tooltip, Map.of(), Optional.empty());
    }
    /** A row without a tooltip. */
    public TableRow(String id, Map<String,String> cells, Optional<String> icon, boolean muted, Optional<List<MenuItem>> contextActions) {
        this(id, cells, icon, muted, contextActions, "");
    }
    public TableRow(String id, Map<String,String> cells, Optional<String> icon) {
        this(id, cells, icon, false, Optional.empty());
    }
    /**
     * A heading row: {@code title} over the rows below it, with an optional count and its noun.
     *
     * @param id stable row id
     * @param title the group's title
     * @param count how many the group holds; absent shows no count
     * @param countNoun the lowercase singular noun after the count; blank for the bare number
     * @return the heading row
     */
    public static TableRow heading(String id, String title, OptionalInt count, String countNoun) {
        return new TableRow(id, Map.of(), Optional.empty(), false, Optional.empty(), "", Map.of(), Optional.of(new Heading(title, count, countNoun)));
    }
    /** Cell content is read-only presentation, or a DROPDOWN whose choice the owner applies. */
    private static void cell(Nodes.Node node) {
        switch (node) {
            case Nodes.Text _, Nodes.StatusDot _, Nodes.Icon _, Nodes.Chip _ -> { }
            case Nodes.Choice choice when choice.kind() == Nodes.ChoiceKind.DROPDOWN -> { }
            case Nodes.Stack stack when stack.role() == Nodes.LayoutRole.INLINE -> {
                for (var child : stack.children()) switch (child) {
                    case Nodes.Text _, Nodes.StatusDot _, Nodes.Icon _, Nodes.Chip _ -> { }
                    default -> throw new IllegalArgumentException("An inline cell stack holds text, status dots, icons and chips only");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported table cell node: " + node.getClass().getSimpleName());
        }
    }
}
