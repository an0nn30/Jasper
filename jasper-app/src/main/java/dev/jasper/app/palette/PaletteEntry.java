package dev.jasper.app.palette;

import java.util.Objects;

/** One line of the palette list: a section heading, one scope's row, a jump to that scope's chip, or a scope in the mode list. */
public sealed interface PaletteEntry {
    /** A heading: a scope's label in All, or a scope's own section label ("Recent") in its tab. Never selected. */
    record Header(String label) implements PaletteEntry {
        public Header { Objects.requireNonNull(label); }
    }

    /** A row and the scope it came from; that scope's verbs act on it. */
    record Item(PaletteScope scope, PaletteRow row) implements PaletteEntry {
        public Item { Objects.requireNonNull(scope); Objects.requireNonNull(row); }
    }

    /** "More in Scope…": the scope matched more than All shows; choosing it sets the scope's chip. */
    record More(PaletteScope scope) implements PaletteEntry {
        public More { Objects.requireNonNull(scope); }
    }

    /** A scope in the mode list shown with no chip and an empty query; choosing it sets that scope's chip. */
    record Mode(PaletteScope scope, String prefix, String shortcut) implements PaletteEntry {
        public Mode {
            Objects.requireNonNull(scope); Objects.requireNonNull(prefix);
            shortcut = shortcut == null ? "" : shortcut;
        }
    }

    /** The identity a refresh keeps selected; null for a header. Scope ids never contain {@code /}. */
    default String key() {
        return switch (this) {
            case Header header -> null;
            case Item item -> key(item.scope(), item.row().id());
            case More more -> "more:" + more.scope().id();
            case Mode mode -> "mode:" + mode.scope().id();
        };
    }

    default boolean selectable() { return !(this instanceof Header); }

    static String key(PaletteScope scope, String rowId) { return scope.id() + "/" + rowId; }
}
