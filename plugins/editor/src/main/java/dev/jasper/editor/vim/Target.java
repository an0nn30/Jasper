package dev.jasper.editor.vim;

/**
 * Where a motion lands and how an operator treats the text up to it (Vim's oparg: motion_type and inclusive).
 *
 * @param pos the new cursor position
 * @param kind exclusive, inclusive or linewise
 * @param column what happens to the remembered column: reset from the new position, kept (j, k), or end of line ($)
 * @param jump whether the motion is a jump (plan V1b adds it to the jumplist)
 * @param regOne whether a delete with this motion always writes register 1
 * @param adjust whether Vim's exclusive-motion adjustment (spec: {@code :help exclusive-linewise}) applies
 * @param area whether an operator's text is a Visual area, as when an extension's mapping starts Visual mode for it
 *     (matchit's {@code norm! v}): a delete of several lines then stays charwise (Vim's is_VIsual)
 */
record Target(Pos pos, Kind kind, Column column, boolean jump, boolean regOne, boolean adjust, boolean area) {
    Target(Pos pos, Kind kind, Column column, boolean jump, boolean regOne, boolean adjust) { this(pos, kind, column, jump, regOne, adjust, false); }

    enum Kind { EXCLUSIVE, INCLUSIVE, LINEWISE }

    enum Column { RESET, KEEP, END }

    static Target exclusive(Pos pos) { return new Target(pos, Kind.EXCLUSIVE, Column.RESET, false, false, true); }

    static Target inclusive(Pos pos) { return new Target(pos, Kind.INCLUSIVE, Column.RESET, false, false, true); }

    static Target linewise(Pos pos, Column column) { return new Target(pos, Kind.LINEWISE, column, false, false, true); }

    Target asJump() { return new Target(pos, kind, column, true, regOne, adjust, area); }

    Target usingRegisterOne() { return new Target(pos, kind, column, jump, true, adjust, area); }

    Target withoutAdjust() { return new Target(pos, kind, column, jump, regOne, false, area); }

    Target withColumn(Column value) { return new Target(pos, kind, value, jump, regOne, adjust, area); }

    Target withKind(Kind value) { return new Target(pos, value, column, jump, regOne, adjust, area); }

    Target asArea() { return new Target(pos, kind, column, jump, regOne, adjust, true); }
}
