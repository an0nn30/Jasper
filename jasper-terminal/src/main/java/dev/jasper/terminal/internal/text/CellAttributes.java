package dev.jasper.terminal.internal.text;

/**
 * Unsupported immutable cell styling. Encoded color values distinguish defaults, indexed colors and RGB; link and
 * linkTarget are nullable.
 * @param foreground -1 for default, indexed 0–255, or RGB with the high color marker bit
 * @param background same encoding as foreground
 * @param flags bitwise style flags
 * @param link OSC 8 hyperlink URI or null
 * @param linkTarget the OSC 8 hyperlink's {@code target=} name, or null when it has none
 */
public record CellAttributes(int foreground, int background, int flags, String link, String linkTarget) {
    public static final int BOLD = 1, ITALIC = 2, UNDERLINE = 4, INVERSE = 8, DIM = 16, HIDDEN = 32;
    public static final CellAttributes DEFAULT = new CellAttributes(-1,-1,0,null,null);
    public boolean has(int flag) { return (flags & flag) != 0; }
}
