package dev.jasper.app.palette;

import java.util.Objects;

/** One line of the palette list: a section heading, one scope's row, or a jump to that scope's chip. */
public sealed interface PaletteEntry {
    /**
     * A heading: a scope's label in All, or a scope's own section label ("Recent") in its tab. Never selected. Its
     * {@code kind} says what it heads, so a view may word it its own way (spec 11.3); {@code label} is the card's words.
     */
    record Header(Kind kind, String label) implements PaletteEntry {
        /** What a heading heads. */
        public enum Kind {
            /** The rows run most recently. */ RECENT,
            /** The rows ranked by use. */ USED,
            /** The rows offered before anything is used. */ SUGGESTED,
            /** A chosen scope's own section label, shown as written. */ SECTION,
            /** One scope's matches under a query; the label is the scope's. */ MATCHES
        }
        public Header { Objects.requireNonNull(kind); Objects.requireNonNull(label); }
    }

    /** A row and the scope it came from; that scope's verbs act on it. */
    record Item(PaletteScope scope, PaletteRow row) implements PaletteEntry {
        public Item { Objects.requireNonNull(scope); Objects.requireNonNull(row); }
    }

    /** "More in Scope…": the scope matched more than All shows; choosing it sets the scope's chip. */
    record More(PaletteScope scope) implements PaletteEntry {
        public More { Objects.requireNonNull(scope); }
    }

    /** The identity a refresh keeps selected; null for a header. Scope ids never contain {@code /}. */
    default String key() {
        return switch (this) {
            case Header header -> null;
            case Item item -> key(item.scope(), item.row().id());
            case More more -> "more:" + more.scope().id();
        };
    }

    default boolean selectable() { return !(this instanceof Header); }

    static String key(PaletteScope scope, String rowId) { return scope.id() + "/" + rowId; }
}
