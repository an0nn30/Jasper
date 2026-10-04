package dev.jasper.ui.swing;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import javax.swing.text.Position;
import javax.swing.text.Segment;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.UndoableEdit;

/** Wipeable Swing text storage without an undo history. All methods are EDT-only. Removal wipes old storage; no edit retains a secret. */
final class SecretBuffer extends PlainDocument implements AutoCloseable {
    private final ArraysContent storage;
    SecretBuffer() { this(new ArraysContent()); }
    private SecretBuffer(ArraysContent storage) { super(storage); this.storage = storage; }
    private boolean closed, clearing;
    private int notifying;
    private boolean clearPending;
    @Override public void insertString(int offset, String text, javax.swing.text.AttributeSet attributes) throws BadLocationException {
        if (closed) throw new IllegalStateException("Secret buffer is closed");
        super.insertString(offset, text, attributes);
    }
    @Override protected void fireUndoableEditUpdate(javax.swing.event.UndoableEditEvent event) {
        // PlainDocument's outer DefaultDocumentEvent can still be undoable even when content
        // edits are not. Never publish those events or allow an undo manager to retain them.
    }
    @Override protected void fireInsertUpdate(javax.swing.event.DocumentEvent event) {
        notifying++;
        try { super.fireInsertUpdate(event); } finally { afterNotification(); }
    }
    @Override protected void fireRemoveUpdate(javax.swing.event.DocumentEvent event) {
        notifying++;
        try { super.fireRemoveUpdate(event); } finally { afterNotification(); }
    }
    private void afterNotification() {
        if (--notifying == 0 && clearPending) { clearPending = false; clear(); }
    }
    @Override public void close() {
        if (closed) return;
        closed = true; clear();
    }
    boolean clearing() { return clearing || clearPending; }
    char[] snapshot() { return Arrays.copyOf(storage.data, getLength()); }
    void clear() {
        if (clearing) return;
        // Callback teardown may occur while Swing prohibits document writes. Wipe immediately,
        // then publish the structural removal after the outer notification has finished.
        if (notifying != 0) { storage.wipe(); clearPending = true; return; }
        clearing = true;
        try { remove(0, getLength()); }
        catch (BadLocationException impossible) { throw new IllegalStateException(impossible); }
        finally { clearing = false; }
    }

    private static final class ArraysContent implements AbstractDocument.Content {
        private char[] data = {'\n'};
        private final List<WeakReference<Mark>> positions = new ArrayList<>();
        private static final class Mark implements Position {
            int offset;
            Mark(int offset) { this.offset = offset; }
            @Override public int getOffset() { return offset; }
        }
        void wipe() { Arrays.fill(data, (char)0); data[data.length - 1] = '\n'; }
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
        /** Swing's required String boundary; kit code uses snapshot/Segment instead. */
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
