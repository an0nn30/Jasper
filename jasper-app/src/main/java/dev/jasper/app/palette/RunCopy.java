package dev.jasper.app.palette;

import java.util.List;
import java.util.Locale;

/**
 * The Run window's words (spec 11.2 and 11.3), as pure functions of the entries the controller made: its headings,
 * the item noun, the detail line, the primary button and the matches count. The card keeps its own strings.
 */
final class RunCopy {
    /** The help row's fixed text when no scope is chosen, or the chosen scope describes nothing. */
    static final String HELP = "Type the name of a session, command, file or snippet, and Jasper will open it for you.";
    /** The primary button's label while no row's verb names it. */
    static final String OPEN = "Open";

    private RunCopy() { }

    /** The Run window's word for {@code header}: one "Matches" for every scope's matches. */
    static String heading(PaletteEntry.Header header) {
        return switch (header.kind()) {
            case RECENT -> "Recent";
            case USED -> "Most used";
            case SUGGESTED -> "Suggested";
            case SECTION -> header.label();
            case MATCHES -> "Matches";
        };
    }

    /**
     * "i of n" under a query: the position of the selected entry among the rows of MATCHES sections, counting no
     * "More in…" row; blank without a query's headings, with no selection or with a "More in…" row selected.
     */
    static String position(List<PaletteEntry> entries, int selected) {
        boolean matches = false;
        int count = 0, index = 0;
        for (int i = 0; i < entries.size(); i++) {
            PaletteEntry entry = entries.get(i);
            if (entry instanceof PaletteEntry.Header header) { matches = header.kind() == PaletteEntry.Header.Kind.MATCHES; continue; }
            if (!matches || !(entry instanceof PaletteEntry.Item)) continue;
            count++;
            if (i == selected) index = count;
        }
        return index == 0 ? "" : index + " of " + count;
    }

    /** {@code scope}'s item noun with its first letter in upper case ("session" reads "Session"). */
    static String noun(PaletteScope scope) {
        String noun = scope.itemNoun();
        return noun.isEmpty() ? noun : noun.substring(0, 1).toUpperCase(Locale.ROOT) + noun.substring(1);
    }

    /** The detail line's label, "{Noun}:", for a selected row; blank for anything else. */
    static String detailLabel(PaletteEntry entry) {
        return entry instanceof PaletteEntry.Item item ? noun(item.scope()) + ":" : "";
    }

    /** The detail line's value: the row's detail, or its title when it has none; blank for anything else. */
    static String detailValue(PaletteEntry entry) {
        if (!(entry instanceof PaletteEntry.Item item)) return "";
        return item.row().detail() != null ? item.row().detail() : item.row().title();
    }

    /** The primary button's label: the selected row's verb 0, else "Open". */
    static String primaryLabel(PaletteEntry entry) {
        return entry instanceof PaletteEntry.Item item ? item.scope().verbs().getFirst().label() : OPEN;
    }

    /** Whether the primary button can run: a row or a "More in…" row is selected. */
    static boolean primaryEnabled(PaletteEntry entry) { return entry != null && entry.selectable(); }

    /** The index of {@code scope}'s SPLIT verb, or -1 when it has none. */
    static int splitVerb(PaletteScope scope) {
        var verbs = scope.verbs();
        for (int i = 0; i < verbs.size(); i++) if (verbs.get(i).role() == PaletteVerb.Role.SPLIT) return i;
        return -1;
    }
}
