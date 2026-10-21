package dev.jasper.editor.vim;

import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.KeyEvent;
import java.text.CharacterIterator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextAreaUI;
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
        revalidate();
    }

    /**
     * With Vim on in a scroll pane, the text area is taller by the viewport's height less one line, so the view may
     * scroll past the end of the text as Vim's window does (the rows below the last line stay empty, Vim's {@code ~}
     * rows) and any line can be the first one shown.
     */
    @Override public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        if (vim != null && getParent() instanceof JViewport port) {
            int extra = port.getExtentSize().height - getLineHeight();
            if (extra > 0) return new Dimension(size.width, size.height + extra);
        }
        return size;
    }

    private dev.jasper.sdk.ui.TerminalTypography typography;
    private dev.jasper.editor.textview.EditorFonts editorFonts;

    /** Updates the source font set, shaping and uniform line box without replacing the document. */
    public void typography(dev.jasper.sdk.ui.TerminalTypography value) {
        var previous = typography;
        typography = java.util.Objects.requireNonNull(value);
        editorFonts = new dev.jasper.editor.textview.EditorFonts(value, dev.jasper.editor.textview.EditorTextView.CONTEXT);
        setFont(editorFonts.primary());
        setFractionalFontMetricsEnabled(true);
        // Font family and point size can stay unchanged when only line height or fallbacks change.
        if (getUI() != null) getUI().getRootView(this).preferenceChanged(null, true, true);
        firePropertyChange("editorTypography", previous, value);
        revalidate(); repaint();
    }

    private dev.jasper.editor.textview.EditorFonts metrics() {
        if (editorFonts == null && getFont() != null) {
            var font=getFont();
            var value=new dev.jasper.sdk.ui.TerminalTypography(font.getFamily(),Math.max(6,Math.min(72,font.getSize2D())),java.util.List.of(),false,1);
            editorFonts=new dev.jasper.editor.textview.EditorFonts(value,dev.jasper.editor.textview.EditorTextView.CONTEXT);
        }
        return editorFonts;
    }
    @Override public void setFont(java.awt.Font font) {
        editorFonts=null;
        super.setFont(font);
        if (typography != null) editorFonts=new dev.jasper.editor.textview.EditorFonts(typography,dev.jasper.editor.textview.EditorTextView.CONTEXT);
    }
    @Override public int getLineHeight() { var fonts=metrics(); return fonts == null ? super.getLineHeight() : fonts.height(); }
    @Override public int getMaxAscent() { var fonts=metrics(); return fonts == null ? super.getMaxAscent() : Math.round(fonts.baseline()); }

    /** The geometry currently installed by the editor UI. */
    public dev.jasper.editor.textview.EditorTextView textView() {
        return (dev.jasper.editor.textview.EditorTextView)getUI().getRootView(this).getView(0);
    }

    @Override public void foldToggled(org.fife.ui.rsyntaxtextarea.folding.Fold fold) {
        if(getUI()!=null && getUI().getRootView(this).getViewCount()>0
            && getUI().getRootView(this).getView(0) instanceof dev.jasper.editor.textview.EditorTextView view)
            view.foldsChanged();
        super.foldToggled(fold);
    }

    @Override protected RTextAreaUI createRTextAreaUI() {
        return new dev.jasper.editor.textview.EditorTextAreaUI(this, () -> typography);
    }

    @Override protected void processKeyEvent(KeyEvent event) {
        VimEditor editor = vim;
        Verdict verdict = editor == null ? Verdict.PASS : handled(editor, event);
        switch (verdict) {
            case TAKEN -> event.consume();
            case PASS -> super.processKeyEvent(event);
            case HELD_BACK, HELD_BACK_RING -> {
                if (verdict == Verdict.HELD_BACK_RING) editor.declined();
                event.consume();
            }
            case ANCESTORS, ANCESTORS_RING -> {
                // Ancestor, window and menu bindings (the app's shortcuts, the host's Close Pane) still run;
                // nothing in the text area's own map would, as handled() checked.
                super.processKeyEvent(event);
                if (!event.isConsumed() && verdict == Verdict.ANCESTORS_RING) editor.declined();
                event.consume();
            }
        }
    }

    /** What happens to a key event with Vim on. */
    private enum Verdict {
        /** Vim took it. */
        TAKEN,
        /** It goes on to every binding as usual (Insert mode, ⌘, an Editor command, a sethandler ide entry). */
        PASS,
        /** Outside Insert mode it would run one of RSyntaxTextArea's editing actions: consumed. */
        HELD_BACK,
        /** As {@link #HELD_BACK}, and the bell rings: Vim would own the key. */
        HELD_BACK_RING,
        /** Outside Insert mode, bound nowhere in the text area: ancestors and the window may still take it. */
        ANCESTORS,
        /** As {@link #ANCESTORS}, and the bell rings if nothing takes it. */
        ANCESTORS_RING
    }

    private Verdict handled(VimEditor editor, KeyEvent event) {
        switch (event.getID()) {
            case KeyEvent.KEY_PRESSED -> {
                swallowTyped = false;
                Optional<KeyInput> key = KeyTranslator.pressed(event.getKeyCode(), event.getModifiersEx(), MAC);
                if (key.isPresent() && editor.offer(key.get())) {
                    swallowTyped = true;
                    return Verdict.TAKEN;
                }
                Verdict verdict = declined(editor, event, key);
                // A key the translator leaves to KEY_TYPED (a plain character, a lone modifier) keeps its character
                // for Vim; any other declined key's character must not reach DefaultKeyTypedAction in Normal mode.
                swallowTyped = verdict != Verdict.PASS && key.isPresent();
                return verdict;
            }
            case KeyEvent.KEY_TYPED -> {
                if (swallowTyped) return Verdict.TAKEN;
                char c = event.getKeyChar();
                if (Character.isHighSurrogate(c)) {
                    highSurrogate = c;
                    return Verdict.TAKEN;
                }
                int codePoint = Character.isLowSurrogate(c) && highSurrogate != 0 ? Character.toCodePoint(highSurrogate, c) : c;
                highSurrogate = 0;
                Optional<KeyInput> key = KeyTranslator.typed(codePoint, event.getModifiersEx(), MAC);
                if (key.isPresent() && editor.offer(key.get())) return Verdict.TAKEN;
                return declined(editor, event, key);
            }
            default -> {
                return Verdict.PASS;
            }
        }
    }

    /**
     * The handler rule for a key Vim declined (spec 2.2): in Insert and Replace mode, for ⌘ and for a {@code sethandler}
     * ide entry it goes on. Otherwise it is held back only when the text area's own map binds it to something other
     * than an Editor command (one of RSyntaxTextArea's editing actions); any other key goes on to the ancestors'
     * bindings, and rings the bell if none takes a key Vim would own.
     */
    private Verdict declined(VimEditor editor, KeyEvent event, Optional<KeyInput> key) {
        if (key.isPresent() ? editor.passesOn(key.get()) : !editor.editing()) return Verdict.PASS;
        if ((event.getModifiersEx() & InputEvent.META_DOWN_MASK) != 0) return Verdict.PASS;
        Object name = bound(event);
        if (name != null && editorCommands.contains(name)) return Verdict.PASS;
        if (name != null) return key.isPresent() ? Verdict.HELD_BACK_RING : Verdict.HELD_BACK;
        return key.isPresent() ? Verdict.ANCESTORS_RING : Verdict.ANCESTORS;
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
