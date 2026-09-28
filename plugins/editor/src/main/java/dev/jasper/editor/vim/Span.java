package dev.jasper.editor.vim;

/**
 * The text an operator acts on (Vim's oap->start, oap->end, motion_type and inclusive, block_mode and its
 * display columns).
 *
 * @param start the first position
 * @param end the last position: included when {@code kind} is inclusive, excluded when exclusive, and for linewise
 *     spans only its line counts
 * @param kind exclusive, inclusive or linewise
 * @param regOne whether a delete always writes register 1
 * @param block for a Visual block, its display columns; null otherwise
 * @param visual whether the span is a Visual area (Vim's is_VIsual)
 * @param endAdjusted whether an exclusive motion's end at column 0 moved back to the line above (Vim's
 *     end_adjusted; {@code gq} then leaves the cursor a line lower)
 */
record Span(Pos start, Pos end, Target.Kind kind, boolean regOne, Columns block, boolean visual, boolean endAdjusted) {
    /**
     * A block's columns (Vim's start_vcol and end_vcol).
     *
     * @param startVcol the first display column
     * @param endVcol the last display column, inclusive
     * @param max whether {@code $} made each line's block reach its end
     */
    record Columns(int startVcol, int endVcol, boolean max) { }

    Span(Pos start, Pos end, Target.Kind kind, boolean regOne, Columns block, boolean visual) { this(start, end, kind, regOne, block, visual, false); }

    Span(Pos start, Pos end, Target.Kind kind, boolean regOne) { this(start, end, kind, regOne, null, false, false); }

    Span(Pos start, Pos end, Target.Kind kind) { this(start, end, kind, false, null, false, false); }

    boolean linewise() { return kind == Target.Kind.LINEWISE; }

    boolean blockwise() { return block != null; }

    int lineCount() { return end.line - start.line + 1; }
}
