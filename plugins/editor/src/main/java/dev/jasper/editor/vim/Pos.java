package dev.jasper.editor.vim;

/**
 * A mutable position, as Vim's {@code pos_T}: a 0-based line and a 0-based column counted in UTF-16 units of that
 * line's text. The column may equal the line's length ("on the NUL" after the last character).
 */
final class Pos implements Comparable<Pos> {
    int line, col;

    Pos(int line, int col) {
        this.line = line;
        this.col = col;
    }

    Pos copy() { return new Pos(line, col); }

    void set(Pos other) {
        line = other.line;
        col = other.col;
    }

    @Override public int compareTo(Pos other) { return line != other.line ? Integer.compare(line, other.line) : Integer.compare(col, other.col); }

    @Override public boolean equals(Object other) { return other instanceof Pos pos && pos.line == line && pos.col == col; }

    @Override public int hashCode() { return line * 31 + col; }

    @Override public String toString() { return (line + 1) + "," + (col + 1); }

    static Pos min(Pos a, Pos b) { return a.compareTo(b) <= 0 ? a : b; }

    static Pos max(Pos a, Pos b) { return a.compareTo(b) >= 0 ? a : b; }
}
