package dev.jasper.editor.vim;

/**
 * The text an operator acts on (Vim's oap->start, oap->end, motion_type and inclusive).
 *
 * @param start the first position
 * @param end the last position: included when {@code kind} is inclusive, excluded when exclusive, and for linewise
 *     spans only its line counts
 * @param kind exclusive, inclusive or linewise
 * @param regOne whether a delete always writes register 1
 */
record Span(Pos start, Pos end, Target.Kind kind, boolean regOne) {
    Span(Pos start, Pos end, Target.Kind kind) { this(start, end, kind, false); }

    boolean linewise() { return kind == Target.Kind.LINEWISE; }

    int lineCount() { return end.line - start.line + 1; }
}
