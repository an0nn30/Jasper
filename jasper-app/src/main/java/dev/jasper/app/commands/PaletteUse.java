package dev.jasper.app.commands;

/**
 * One palette row the user ran: its scope and row id, the title that finds it again, how often and when last
 * (epoch milliseconds). Only scopes that allow it are recorded; the palette ranks these for its empty query.
 */
public record PaletteUse(String scopeId, String rowId, String title, int count, long lastUsed) {
    static final int MAX_TEXT = 512;

    public PaletteUse {
        if (!valid(scopeId, rowId, title)) throw new IllegalArgumentException("Invalid palette use");
        if (count < 1) throw new IllegalArgumentException("A use counts at least once");
    }

    static boolean valid(String scopeId, String rowId, String title) {
        return scopeId != null && scopeId.matches("[a-z][a-z0-9_.-]{0,127}") && rowId != null && !rowId.isEmpty()
            && rowId.length() <= MAX_TEXT && title != null && title.length() <= MAX_TEXT;
    }

    /** Frecency: uses, discounted by age in weeks. */
    public double score(long now) {
        double weeks = Math.max(0, now - lastUsed) / 604_800_000.0;
        return count / (1 + weeks);
    }

    boolean same(PaletteUse other) { return scopeId.equals(other.scopeId) && rowId.equals(other.rowId); }
}
