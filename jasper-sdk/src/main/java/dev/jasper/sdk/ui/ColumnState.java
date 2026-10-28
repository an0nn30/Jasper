package dev.jasper.sdk.ui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The sort and the widths a table's or tree's owner keeps for its columns: the sort column and direction, and the
 * widths a user dragged. {@link #apply(List)} marks a column list with it; {@link #encode()} and {@link #decode(String)}
 * keep it as one line of text, which {@link ColumnStateFile} stores.
 *
 * @param sortColumn the id of the column the rows are sorted by; empty when the owner's own order applies
 * @param ascending true sorts the sort column ascending; meaningless when {@code sortColumn} is empty
 * @param widths column id to unscaled width in pixels, each positive; copied
 * @since 0.25.0
 */
public record ColumnState(Optional<String> sortColumn, boolean ascending, Map<String, Integer> widths) {
    /** No sort and no widths. */
    public static final ColumnState NONE = new ColumnState(Optional.empty(), true, Map.of());

    /**
     * Validates the ids and widths and copies the widths.
     *
     * @throws IllegalArgumentException when a column id is blank or contains one of {@code ;,:=}, which the encoding
     *         uses, or a width is not positive
     */
    public ColumnState {
        Objects.requireNonNull(sortColumn); sortColumn.ifPresent(ColumnState::id);
        widths = Map.copyOf(widths);
        for (var e : widths.entrySet()) { id(e.getKey()); if (e.getValue() <= 0) throw new IllegalArgumentException("Column width must be positive"); }
    }

    /**
     * A state sorted by one column with no widths.
     *
     * @param columnId the sort column's id
     * @param ascending true for ascending
     * @return the state
     * @throws IllegalArgumentException when the id is blank or contains one of {@code ;,:=}
     */
    public static ColumnState sortedBy(String columnId, boolean ascending) { return new ColumnState(Optional.of(columnId), ascending, Map.of()); }

    /**
     * The state after a click on a column header: the same column flips direction, another column starts ascending.
     * The widths are kept.
     *
     * @param columnId the clicked column's id
     * @return the new state
     * @throws IllegalArgumentException when the id is blank or contains one of {@code ;,:=}
     */
    public ColumnState sorted(String columnId) {
        boolean same = sortColumn.filter(columnId::equals).isPresent();
        return new ColumnState(Optional.of(columnId), same ? !ascending : true, widths);
    }

    /**
     * This state with other widths.
     *
     * @param widths column id to unscaled width in pixels, each positive
     * @return the new state
     * @throws IllegalArgumentException when an id is blank or contains one of {@code ;,:=}, or a width is not positive
     */
    public ColumnState resized(Map<String, Integer> widths) { return new ColumnState(sortColumn, ascending, widths); }

    /** A column id the encoding can carry: not blank and free of its separators. */
    private static void id(String id) {
        ModelChecks.id(id);
        if (id.chars().anyMatch(c -> ";,:=".indexOf(c) >= 0)) throw new IllegalArgumentException("Column id must not contain ; , : or =: " + id);
    }

    /**
     * The columns marked with this state: the sort column carries the sort indicator, the others none, and every column
     * whose id has a width takes it. Ids that name no column are ignored.
     *
     * @param columns the owner's columns
     * @return the marked columns, in order
     */
    public List<Column> apply(List<Column> columns) {
        return columns.stream().map(c -> c.withSorted(sortColumn.filter(c.id()::equals).map(id -> ascending))
            .withWidth(widths.containsKey(c.id()) ? OptionalInt.of(widths.get(c.id())) : c.width())).toList();
    }

    /**
     * This state as text, {@code sort=<id>,<asc|desc>;widths=<id>:<n>,<id>:<n>}, either part left out when empty.
     *
     * @return the text; {@link #decode(String)} reads it back
     */
    public String encode() {
        var sort = sortColumn.map(id -> "sort=" + id + "," + (ascending ? "asc" : "desc"));
        var sizes = new TreeMap<>(widths).entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(","));
        return Stream.of(sort, Optional.of(sizes).filter(s -> !s.isEmpty()).map(s -> "widths=" + s)).flatMap(Optional::stream).collect(Collectors.joining(";"));
    }

    /**
     * Reads text written by {@link #encode()}.
     *
     * @param text the text
     * @return the state, or {@link #NONE} when the text is malformed
     */
    public static ColumnState decode(String text) {
        try {
            if (text == null || text.isBlank()) return NONE;
            Optional<String> sort = Optional.empty(); boolean ascending = true; var widths = new TreeMap<String, Integer>();
            for (var part : text.split(";", -1)) {
                int eq = part.indexOf('=');
                if (eq < 0) return NONE;
                String key = part.substring(0, eq), value = part.substring(eq + 1);
                switch (key) {
                    case "sort" -> {
                        var bits = value.split(",", -1);
                        if (bits.length != 2 || (!bits[1].equals("asc") && !bits[1].equals("desc"))) return NONE;
                        sort = Optional.of(bits[0]); ascending = bits[1].equals("asc");
                    }
                    case "widths" -> {
                        for (var item : value.split(",", -1)) {
                            int colon = item.indexOf(':');
                            if (colon < 0) return NONE;
                            widths.put(item.substring(0, colon), Integer.parseInt(item.substring(colon + 1)));
                        }
                    }
                    default -> { return NONE; }
                }
            }
            return new ColumnState(sort, ascending, widths);
        } catch (RuntimeException malformed) {
            return NONE;
        }
    }
}
