package dev.jasper.app.palette;

import java.util.Objects;

/** One line of the palette list: a section heading, one scope's row, or a jump to that scope's tab. */
public sealed interface PaletteEntry {
    /** A heading: a scope's label in All, or a scope's own section label ("Recent") in its tab. Never selected. */
    record Header(String label) implements PaletteEntry {
        public Header { Objects.requireNonNull(label); }
    }

    /** A row and the scope it came from; that scope's verbs act on it. */
    record Item(PaletteScope scope, PaletteRow row) implements PaletteEntry {
        public Item { Objects.requireNonNull(scope); Objects.requireNonNull(row); }
    }

    /** "More in Scope…": the scope matched more than All shows; choosing it opens the scope's tab. */
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
