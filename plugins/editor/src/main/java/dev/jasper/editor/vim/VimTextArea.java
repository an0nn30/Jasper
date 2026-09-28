package dev.jasper.editor.vim;

import java.awt.event.InputMethodEvent;
import java.awt.event.KeyEvent;
import java.text.CharacterIterator;
import java.util.Locale;
import java.util.Optional;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RUndoManager;

/**
 * Every Editor tab's text area (spec 1.1). With Vim off it is an ordinary RSyntaxTextArea. With Vim on, key events
 * reach the attached {@link VimEditor} before Swing's key bindings: a key Vim takes is consumed here, so neither the
 * text area's bindings nor the Editor's window shortcuts see it (the handler rule, spec 2.2).
 */
public class VimTextArea extends RSyntaxTextArea {
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");

    // Assigned from the superclass constructor through createUndoManager, so it has no initializer.
    private VimUndoManager undo;
    private VimEditor vim;
    private boolean swallowTyped;
    private char highSurrogate;

    /** An empty text area. */
    public VimTextArea() { }

    @Override protected RUndoManager createUndoManager() {
        undo = new VimUndoManager(this);
        return undo;
    }

    VimUndoManager vimUndo() { return undo; }

    /** @return the attached Vim editor, if Vim is on */
    public Optional<VimEditor> vim() { return Optional.ofNullable(vim); }

    void attach(VimEditor editor) {
        vim = editor;
        undo.vim(editor != null);
    }

    @Override protected void processKeyEvent(KeyEvent event) {
        VimEditor editor = vim;
        if (editor != null && handled(editor, event)) {
            event.consume();
            return;
        }
        super.processKeyEvent(event);
    }

    private boolean handled(VimEditor editor, KeyEvent event) {
        switch (event.getID()) {
            case KeyEvent.KEY_PRESSED -> {
                swallowTyped = false;
                Optional<KeyInput> key = KeyTranslator.pressed(event.getKeyCode(), event.getModifiersEx(), MAC);
                swallowTyped = key.isPresent() && editor.offer(key.get());
                return swallowTyped;
            }
            case KeyEvent.KEY_TYPED -> {
                if (swallowTyped) return true;
                char c = event.getKeyChar();
                if (Character.isHighSurrogate(c)) {
                    highSurrogate = c;
                    return true;
                }
                int codePoint = Character.isLowSurrogate(c) && highSurrogate != 0 ? Character.toCodePoint(highSurrogate, c) : c;
                highSurrogate = 0;
                Optional<KeyInput> key = KeyTranslator.typed(codePoint, event.getModifiersEx(), MAC);
                return key.isPresent() && editor.offer(key.get());
            }
            default -> {
                return false;
            }
        }
    }

    @Override protected void processInputMethodEvent(InputMethodEvent event) {
        VimEditor editor = vim;
        String committed = editor != null && event.getID() == InputMethodEvent.INPUT_METHOD_TEXT_CHANGED ? committed(event) : "";
        super.processInputMethodEvent(event);
        if (!committed.isEmpty()) editor.typedByInputMethod(committed);
    }

    private static String committed(InputMethodEvent event) {
        StringBuilder text = new StringBuilder();
        CharacterIterator iterator = event.getText();
        if (iterator == null) return "";
        char c = iterator.first();
        for (int i = 0; i < event.getCommittedCharacterCount() && c != CharacterIterator.DONE; i++, c = iterator.next()) text.append(c);
        return text.toString();
    }
}
