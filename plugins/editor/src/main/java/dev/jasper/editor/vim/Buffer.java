package dev.jasper.editor.vim;

import java.util.Arrays;
import java.util.List;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/**
 * The text as Vim sees it: lines without their newline, where a final newline ends the last line rather than
 * starting an empty one ({@code "a\nb\n"} is two lines, as a file is in Vim). An empty document is one empty line.
 * Offsets are the document's; lines and columns are 0-based. Every edit goes through the document, so the undo
 * manager sees it.
 */
final class Buffer {
    private final Document document;
    private final DocumentListener listener = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent event) { lines = null; }
        @Override public void removeUpdate(DocumentEvent event) { lines = null; }
        @Override public void changedUpdate(DocumentEvent event) { }
    };
    private String[] lines;
    private int[] starts;

    Buffer(Document document) {
        this.document = document;
        document.addDocumentListener(listener);
    }

    void close() { document.removeDocumentListener(listener); }

    Document document() { return document; }

    /** @return the whole text, including a final newline */
    String text() {
        try { return document.getText(0, document.getLength()); }
        catch (BadLocationException impossible) { throw new IllegalStateException(impossible); }
    }

    int length() { return document.getLength(); }

    /** @return the number of lines, at least 1 */
    int count() { return lines().length; }

    String line(int line) { return lines()[line]; }

    /** @return the offset of the line's first character */
    int start(int line) {
        lines();
        return starts[line];
    }

    /** @return the offset just after the line's last character (its newline, or the end of the text) */
    int end(int line) { return start(line) + line(line).length(); }

    int offset(Pos pos) { return start(pos.line) + pos.col; }

    int offset(int line, int col) { return start(line) + col; }

    /** @return the line holding {@code offset}; an offset past the last line is on the last line */
    int lineOf(int offset) {
        lines();
        int found = Arrays.binarySearch(starts, offset);
        int line = found >= 0 ? found : -found - 2;
        return Math.max(0, Math.min(line, lines.length - 1));
    }

    Pos pos(int offset) {
        int line = lineOf(offset);
        return new Pos(line, Math.min(Math.max(0, offset - start(line)), line(line).length()));
    }

    /** @return lines {@code first} to {@code last}, inclusive */
    List<String> lines(int first, int last) { return List.of(Arrays.copyOfRange(lines(), first, last + 1)); }

    void insert(int offset, String text) {
        if (text.isEmpty()) return;
        try { document.insertString(offset, text, null); }
        catch (BadLocationException bad) { throw new IllegalStateException(bad); }
    }

    void remove(int from, int to) {
        if (to <= from) return;
        try { document.remove(from, to - from); }
        catch (BadLocationException bad) { throw new IllegalStateException(bad); }
    }

    void replace(int from, int to, String text) {
        if (to <= from) {
            insert(from, text);
            return;
        }
        try {
            if (document instanceof AbstractDocument abstractDocument) abstractDocument.replace(from, to - from, text, null);
            else {
                document.remove(from, to - from);
                document.insertString(from, text, null);
            }
        } catch (BadLocationException bad) { throw new IllegalStateException(bad); }
    }

    /** Replaces one line's text, leaving its newline. */
    void setLine(int line, String text) {
        if (!text.equals(line(line))) replace(start(line), end(line), text);
    }

    /** Inserts whole lines before {@code line}; {@code line == count()} appends after the last line. */
    void insertLines(int line, List<String> text) {
        String block = String.join("\n", text);
        if (line < count()) {
            insert(start(line), block + "\n");
            return;
        }
        int length = length();
        boolean newline = length > 0 && text().charAt(length - 1) == '\n';
        insert(length, newline ? block + "\n" : "\n" + block);
    }

    /** Deletes lines {@code first} to {@code last}, keeping a final newline; deleting every line empties the text. */
    void deleteLines(int first, int last) {
        int count = count();
        if (first == 0 && last == count - 1) remove(0, length());
        else if (last < count - 1) remove(start(first), start(last + 1));
        else remove(end(first - 1), end(last));
    }

    private String[] lines() {
        if (lines == null) {
            String text = text();
            if (text.endsWith("\n")) text = text.substring(0, text.length() - 1);
            lines = text.split("\n", -1);
            starts = new int[lines.length];
            for (int i = 1; i < lines.length; i++) starts[i] = starts[i - 1] + lines[i - 1].length() + 1;
        }
        return lines;
    }
}
