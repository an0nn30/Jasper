package dev.jasper.editor.vim;

import java.awt.event.InputEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.KeyEvent;
import java.text.CharacterIterator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.swing.KeyStroke;
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
    private final Set<Object> editorCommands = new HashSet<>();
    private boolean swallowTyped;
    private char highSurrogate;

    /** An empty text area. */
    public VimTextArea() { }

    @Override protected RUndoManager createUndoManager() {
        undo = new VimUndoManager(this);
        undo.vim(vim != null);
        return undo;
    }

    VimUndoManager vimUndo() { return undo; }

    /**
     * Marks an action name as one of the Editor's own commands: outside Insert and Replace mode, a key Vim declines
     * reaches the text area's bindings only when it holds ⌘ or is bound to one of these (spec 2.2).
     *
     * @param actionName the action map key the command is bound under
     */
    public void allowInNormalMode(Object actionName) { editorCommands.add(actionName); }

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
                if (key.isPresent() && editor.offer(key.get())) return swallowTyped = true;
                if (key.isPresent() ? editor.passesOn(key.get()) : !editor.editing()) return false;
                if (reachesEditor(event)) return false;
                // A key the translator leaves to KEY_TYPED (a plain character, a lone modifier) is not a Vim key:
                // only a binding it would trigger is held back, and its character still reaches Vim.
                if (key.isEmpty()) return bound(event) != null;
                editor.declined();
                return swallowTyped = true;
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
                if (key.isPresent() && editor.offer(key.get())) return true;
                if (key.isPresent() ? editor.passesOn(key.get()) : !editor.editing()) return false;
                if ((event.getModifiersEx() & InputEvent.META_DOWN_MASK) != 0) return false;
                if (key.isPresent()) editor.declined();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** @return whether a key Vim declined outside Insert mode goes on: a ⌘ key, or one bound to an Editor command */
    private boolean reachesEditor(KeyEvent event) {
        if ((event.getModifiersEx() & InputEvent.META_DOWN_MASK) != 0) return true;
        Object name = bound(event);
        return name != null && editorCommands.contains(name);
    }

    /** @return the action name the text area's focused input map (or a parent) binds the event's stroke to, or null */
    private Object bound(KeyEvent event) {
        // InputMap.get consults the parent maps (RSyntaxTextArea's defaults, the keymap) itself.
        return getInputMap(WHEN_FOCUSED).get(KeyStroke.getKeyStrokeForEvent(event));
    }

    @Override protected void processInputMethodEvent(InputMethodEvent event) {
        VimEditor editor = vim;
        if (editor != null && !editor.editing() && event.getID() == InputMethodEvent.INPUT_METHOD_TEXT_CHANGED) {
            // Input methods are off outside Insert mode, but macOS press-and-hold accents and the emoji picker still
            // commit text: it must not type into Normal mode.
            event.consume();
            return;
        }
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
