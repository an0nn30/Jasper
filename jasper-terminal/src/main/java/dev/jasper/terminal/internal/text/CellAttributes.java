package dev.jasper.terminal.internal.text;

public record CellAttributes(int foreground, int background, int flags, String link) {
    public static final int BOLD = 1, ITALIC = 2, UNDERLINE = 4, INVERSE = 8, DIM = 16, HIDDEN = 32;
    public static final CellAttributes DEFAULT = new CellAttributes(-1,-1,0,null);
    public boolean has(int flag) { return (flags & flag) != 0; }
}
