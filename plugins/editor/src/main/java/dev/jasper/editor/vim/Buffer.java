package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;

/**
 * The text as Vim sees it: lines without their newline, where a final newline ends the last line rather than
 * starting an empty one ({@code "a\nb\n"} is two lines, as a file is in Vim). An empty document is one empty line.
 * Offsets are the document's; lines and columns are 0-based. Every edit goes through the document, so the undo
 * manager sees it.
 */
final class Buffer {
    private final Document document;
    private final Element root;

    Buffer(Document document) {
        this.document = document;
        this.root = document.getDefaultRootElement();
    }

    /** Nothing to release: the buffer reads the document's own line elements. */
    void close() { }

    Document document() { return document; }

    /** @return the whole text, including a final newline */
    String text() {
        try { return document.getText(0, document.getLength()); }
        catch (BadLocationException impossible) { throw new IllegalStateException(impossible); }
    }

    int length() { return document.getLength(); }

    /** @return the text from {@code from} to {@code to} */
    String text(int from, int to) {
        try { return document.getText(from, to - from); }
        catch (BadLocationException bad) { throw new IllegalStateException(bad); }
    }

    /** @return the character at {@code offset}, or -1 outside the text */
    int charAt(int offset) {
        if (offset < 0 || offset >= document.getLength()) return -1;
        return text(offset, offset + 1).charAt(0);
    }

    /**
     * @return the number of lines, at least 1: the document's line elements, less the empty one after a final
     *     newline
     */
    int count() {
        int elements = root.getElementCount();
        int length = document.getLength();
        return elements > 1 && length > 0 && charAt(length - 1) == '\n' ? elements - 1 : elements;
    }

    String line(int line) { return text(start(line), end(line)); }

    /** @return the offset of the line's first character */
    int start(int line) {
        check(line);
        return root.getElement(line).getStartOffset();
    }

    /** @return the offset just after the line's last character (its newline, or the end of the text) */
    int end(int line) {
        check(line);
        // A line element ends after its newline; the last one after the document's implied final break.
        return root.getElement(line).getEndOffset() - 1;
    }

    int offset(Pos pos) { return start(pos.line) + pos.col; }

    int offset(int line, int col) { return start(line) + col; }

    /** @return the line holding {@code offset}; an offset past the last line is on the last line */
    int lineOf(int offset) {
        int line = root.getElementIndex(Math.max(0, offset));
        return Math.max(0, Math.min(line, count() - 1));
    }

    Pos pos(int offset) {
        int line = lineOf(offset);
        int start = start(line);
        return new Pos(line, Math.min(Math.max(0, offset - start), end(line) - start));
    }

    /** @return lines {@code first} to {@code last}, inclusive */
    List<String> lines(int first, int last) {
        List<String> lines = new ArrayList<>(last - first + 1);
        for (int line = first; line <= last; line++) lines.add(line(line));
        return List.copyOf(lines);
    }

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

    /**
     * Replaces one line's text, leaving its newline. Only the part that differs is replaced, so the change starts
     * where Vim's does (for the {@code '.} mark) and the undo holds only the change.
     */
    void setLine(int line, String text) {
        if (text.endsWith("\n") && line == count() - 1 && !finalBreak()) text += "\n";
        String old = line(line);
        if (text.equals(old)) return;
        int prefix = 0, max = Math.min(old.length(), text.length());
        while (prefix < max && old.charAt(prefix) == text.charAt(prefix)) prefix++;
        if (prefix > 0 && Character.isHighSurrogate(old.charAt(prefix - 1))) prefix--;
        int suffix = 0;
        while (suffix < max - prefix && old.charAt(old.length() - 1 - suffix) == text.charAt(text.length() - 1 - suffix)) suffix++;
        if (suffix > 0 && Character.isLowSurrogate(old.charAt(old.length() - suffix))) suffix--;
        int start = start(line);
        replace(start + prefix, start + old.length() - suffix, text.substring(prefix, text.length() - suffix));
    }

    /** Inserts whole lines before {@code line}; {@code line == count()} appends after the last line. */
    void insertLines(int line, List<String> text) {
        String block = String.join("\n", text);
        if (line < count()) {
            insert(start(line), block + "\n");
            return;
        }
        if (finalBreak()) insert(length(), block + "\n");
        else insert(length(), "\n" + block + (!text.isEmpty() && text.getLast().isEmpty() ? "\n" : ""));
    }

    /**
     * @return whether the text ends with a line break; without one an empty last line added by an edit needs one,
     *     or it would only end the line before it
     */
    private boolean finalBreak() {
        int length = length();
        return length > 0 && charAt(length - 1) == '\n';
    }

    /** Deletes lines {@code first} to {@code last}, keeping a final newline; deleting every line empties the text. */
    void deleteLines(int first, int last) {
        int count = count();
        if (first == 0 && last == count - 1) remove(0, length());
        else if (last < count - 1) remove(start(first), start(last + 1));
        else remove(end(first - 1), end(last));
    }

    private void check(int line) {
        if (line < 0 || line >= count()) throw new ArrayIndexOutOfBoundsException("Line " + line + " of " + count());
    }
}
