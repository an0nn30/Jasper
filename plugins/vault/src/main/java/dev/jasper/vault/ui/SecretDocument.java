package dev.jasper.vault.ui;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.event.DocumentEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import javax.swing.text.Position;
import javax.swing.text.Segment;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.UndoableEdit;

/** Wipeable Swing text storage without an undo history. All methods are UI-thread-only. */
public final class SecretDocument extends PlainDocument {
    private final ArraysContent storage;
    public SecretDocument() { this(new ArraysContent()); }
    private SecretDocument(ArraysContent storage) { super(storage); this.storage = storage; }
    public char[] snapshot() { return Arrays.copyOf(storage.data, getLength()); }
    public void clear() { replace(new char[0]); }
    public void replace(char[] text) {
        writeLock();
        try {
            remove(0, getLength());
            if (text.length == 0) return;
            storage.insertChars(0, text);
            var event = new DefaultDocumentEvent(0, text.length, DocumentEvent.EventType.INSERT);
            insertUpdate(event, null); event.end(); fireInsertUpdate(event);
        } catch (BadLocationException impossible) { throw new IllegalStateException(impossible); }
        finally { writeUnlock(); }
    }

    private static final class ArraysContent implements AbstractDocument.Content {
        private char[] data = {'\n'};
        private final List<WeakReference<Mark>> positions = new ArrayList<>();
        private static final class Mark implements Position {
            int offset;
            Mark(int offset) { this.offset = offset; }
            @Override public int getOffset() { return offset; }
        }
        @Override public int length() { return data.length; }
        @Override public Position createPosition(int offset) throws BadLocationException {
            range(offset, 0);
            var mark = new Mark(offset); positions.add(new WeakReference<>(mark)); return mark;
        }
        @Override public UndoableEdit insertString(int offset, String text) throws BadLocationException {
            char[] chars = text.toCharArray();
            try { insertChars(offset, chars); return noUndo(); }
            finally { Arrays.fill(chars, (char) 0); }
        }
        void insertChars(int offset, char[] chars) throws BadLocationException {
            range(offset, 0);
            char[] next = new char[data.length + chars.length];
            System.arraycopy(data, 0, next, 0, offset);
            System.arraycopy(chars, 0, next, offset, chars.length);
            System.arraycopy(data, offset, next, offset + chars.length, data.length - offset);
            Arrays.fill(data, (char) 0); data = next;
            positions.removeIf(ref -> ref.get() == null);
            for (var ref : positions) {
                Mark mark = ref.get();
                if (mark != null && mark.offset >= offset && mark.offset != 0) mark.offset += chars.length;
            }
        }
        @Override public UndoableEdit remove(int offset, int count) throws BadLocationException {
            range(offset, count);
            char[] next = new char[data.length - count];
            System.arraycopy(data, 0, next, 0, offset);
            System.arraycopy(data, offset + count, next, offset, data.length - offset - count);
            Arrays.fill(data, (char) 0); data = next;
            positions.removeIf(ref -> ref.get() == null);
            for (var ref : positions) {
                Mark mark = ref.get();
                if (mark != null && mark.offset > offset) mark.offset = Math.max(offset, mark.offset - count);
            }
            return noUndo();
        }
        /** Swing's required String boundary; vault code uses snapshot/Segment instead. */
        @Override public String getString(int offset, int count) throws BadLocationException {
            range(offset, count); return new String(data, offset, count);
        }
        @Override public void getChars(int offset, int count, Segment destination) throws BadLocationException {
            range(offset, count); destination.array = data; destination.offset = offset; destination.count = count;
        }
        private void range(int offset, int count) throws BadLocationException {
            if (offset < 0 || count < 0 || offset > data.length - count) throw new BadLocationException("Invalid text range", offset);
        }
        private static UndoableEdit noUndo() {
            return new AbstractUndoableEdit() {
                @Override public boolean canUndo() { return false; }
                @Override public boolean canRedo() { return false; }
            };
        }
    }
}
