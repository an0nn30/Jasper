package dev.jasper.editor.text;

/** A line break style. */
public enum LineSeparator {
    LF("\n", "LF"), CRLF("\r\n", "CRLF"), CR("\r", "CR");

    private final String chars;
    private final String label;

    LineSeparator(String chars, String label) { this.chars = chars; this.label = label; }

    /** @return the characters written for a line break */
    public String chars() { return chars; }

    /** @return the status line's label */
    public String label() { return label; }
}
