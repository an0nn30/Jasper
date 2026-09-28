package dev.jasper.editor.vim;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.fife.ui.rtextarea.CaretStyle;
import org.fife.ui.rtextarea.ConfigurableCaret;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VimEditorTest {
    private final Map<Character, String> clipboard = new HashMap<>();
    private Vim vim = new Vim(new VimHost(r -> Optional.ofNullable(clipboard.get(r)), clipboard::put));

    private static void onEdt(Runnable test) throws Exception { SwingUtilities.invokeAndWait(test); }

    private VimEditor editor(VimTextArea area, String text) {
        area.setText(text);
        area.discardAllEdits();
        area.setCaretPosition(0);
        return new VimEditor(vim, area, EditorDefaults.VIM);
    }

    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    private static CaretStyle caret(VimTextArea area) { return ((ConfigurableCaret) area.getCaret()).getStyle(); }

    /** Remembers whether input methods are on, which a component does not report. */
    private static final class InputMethodArea extends VimTextArea {
        Boolean inputMethods;

        @Override public void enableInputMethods(boolean enable) {
            inputMethods = enable;
            super.enableInputMethods(enable);
        }
    }

    @Test void theCaretAndInputMethodsFollowTheMode() throws Exception {
        onEdt(() -> {
            var area = new InputMethodArea();
            CaretStyle before = caret(area);
            var editor = editor(area, "abc\n");
            assertThat(caret(area)).isEqualTo(CaretStyle.BLOCK_STYLE);
            assertThat(area.inputMethods).as("an IME must not eat Normal-mode commands").isFalse();
            type(editor, "d");
            assertThat(editor.mode()).isEqualTo(Mode.OPERATOR_PENDING);
            assertThat(caret(area)).isEqualTo(CaretStyle.UNDERLINE_STYLE);
            type(editor, "<Esc>i");
            assertThat(editor.mode()).isEqualTo(Mode.INSERT);
            assertThat(caret(area)).isEqualTo(CaretStyle.VERTICAL_LINE_STYLE);
            assertThat(area.inputMethods).isTrue();
            type(editor, "<Esc>R");
            assertThat(caret(area)).isEqualTo(CaretStyle.UNDERLINE_STYLE);
            type(editor, "<Esc>");
            editor.close();
            assertThat(caret(area)).isEqualTo(before);
            assertThat(area.inputMethods).isTrue();
            assertThat(area.vim()).isEmpty();
        });
    }

    @Test void theHandlerRuleGivesVimOnlyKeysItDefines() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc\n");
            assertThat(editor.offer(new KeyInput("s", KeyInput.CTRL))).as("Vim has no <C-s>: the Editor saves").isFalse();
            assertThat(editor.offer(new KeyInput("s", KeyInput.META))).as("⌘ keys stay with the Editor").isFalse();
            assertThat(editor.offer(new KeyInput("r", KeyInput.CTRL))).as("<C-r> redoes").isTrue();
            assertThat(editor.offer(KeyInput.named("F5"))).isFalse();
            assertThat(editor.offer(KeyInput.of('i'))).isTrue();
            assertThat(editor.offer(new KeyInput("w", KeyInput.CTRL))).as("<C-w> deletes a word in Insert mode").isTrue();
            assertThat(editor.offer(new KeyInput("f", KeyInput.CTRL))).as("<C-f> is not an Insert-mode key").isFalse();
            editor.offer(KeyInput.named("Esc"));
            vim.handlers().set(new KeyInput("r", KeyInput.CTRL), "n", KeyHandlers.Handler.IDE);
            assertThat(editor.offer(new KeyInput("r", KeyInput.CTRL))).isFalse();
            vim.handlers().set(new KeyInput("s", KeyInput.CTRL), "a", KeyHandlers.Handler.VIM);
            assertThat(editor.offer(new KeyInput("s", KeyInput.CTRL))).isTrue();
        });
    }

    @Test void aFailingCommandRingsTheBellAndShowsVimsMessage() throws Exception {
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "abc\n");
            type(editor, "\"zp");
            assertThat(editor.bells()).isEqualTo(1);
            assertThat(editor.message()).isEqualTo("E353: Nothing in register z");
            assertThat(editor.messageIsError()).isTrue();
            type(editor, "h");
            assertThat(editor.bells()).isEqualTo(2);
            assertThat(editor.message()).isEmpty();
        });
    }

    @Test void anUnexpectedErrorCancelsTheCommandAndReturnsToNormalMode() throws Exception {
        vim = new Vim(new VimHost(r -> { throw new IllegalStateException("clipboard exploded"); }, (r, t) -> { }));
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc\n");
            type(editor, "\"+p");
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
            assertThat(editor.message()).isEqualTo("Internal error; the command was cancelled");
            assertThat(area.vimUndo().stepOpen()).isFalse();
            type(editor, "x");
            assertThat(area.getText()).isEqualTo("bc\n");
        });
    }

    @Test void yanksAndPutsReachTheClipboardThroughTheHost() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "one\ntwo\n");
            type(editor, "\"+yy");
            assertThat(clipboard.get('+')).isEqualTo("one\n");
            clipboard.put('+', "pasted");
            type(editor, "j\"+P");
            assertThat(area.getText()).isEqualTo("one\npastedtwo\n");
        });
    }

    @Test void showcmdShowsThePartlyTypedCommand() throws Exception {
        onEdt(() -> {
            var editor = editor(new VimTextArea(), "abc\n");
            type(editor, "2\"a");
            assertThat(editor.showcmd()).isEqualTo("2\"a");
            type(editor, "d");
            assertThat(editor.showcmd()).isEqualTo("2\"ad");
            type(editor, "<Esc>");
            assertThat(editor.showcmd()).isEmpty();
            editor.options().set("showcmd", false);
            type(editor, "2");
            assertThat(editor.showcmd()).isEmpty();
        });
    }

    @Test void theTabsOptionsFollowTheEditorsDefaultsAndDriveTheTextArea() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            area.setText("x\n");
            var editor = new VimEditor(vim, area, new EditorDefaults(4, true, true));
            assertThat(editor.options().number("tabstop")).isEqualTo(4);
            assertThat(editor.options().number("shiftwidth")).isEqualTo(4);
            assertThat(area.getTabsEmulated()).isTrue();
            editor.editorDefaults(new EditorDefaults(2, false, true));
            assertThat(area.getTabSize()).isEqualTo(2);
            assertThat(area.getTabsEmulated()).isFalse();
        });
    }

    @Test void reloadingTheTextDropsAnInsertInProgress() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc\n");
            type(editor, "ix");
            area.setText("new\n");
            editor.textReplaced();
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
            assertThat(area.vimUndo().stepOpen()).isFalse();
        });
    }

    @Test void keyEventsReachVimBeforeTheTextAreasBindings() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc\n");
            var typedX = new KeyEvent(area, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'x');
            area.processKeyEvent(typedX);
            assertThat(typedX.isConsumed()).isTrue();
            assertThat(area.getText()).isEqualTo("bc\n");
            var escape = new KeyEvent(area, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
            area.processKeyEvent(escape);
            assertThat(escape.isConsumed()).isTrue();
            var escapeTyped = new KeyEvent(area, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, (char) 27);
            area.processKeyEvent(escapeTyped);
            assertThat(escapeTyped.isConsumed()).as("the character of a key Vim took is swallowed").isTrue();
            int bells = editor.bells();
            var command = new KeyEvent(area, KeyEvent.KEY_PRESSED, 0, InputEvent.META_DOWN_MASK, KeyEvent.VK_F7, KeyEvent.CHAR_UNDEFINED);
            area.processKeyEvent(command);
            assertThat(command.isConsumed()).as("a ⌘ key goes on to the text area's and the window's bindings").isFalse();
            assertThat(editor.bells()).isEqualTo(bells);
            type(editor, "i");
            area.processKeyEvent(new KeyEvent(area, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, '\uD83D'));
            area.processKeyEvent(new KeyEvent(area, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, '\uDE00'));
            assertThat(area.getText()).isEqualTo("😀bc\n");
        });
    }

    @Test void aMouseClickPastTheEndOfALineIsPulledBackInNormalMode() throws Exception {
        var area = new AtomicReference<VimTextArea>();
        onEdt(() -> {
            area.set(new VimTextArea());
            editor(area.get(), "abc\n");
            area.get().setCaretPosition(3);
        });
        onEdt(() -> assertThat(area.get().getCaretPosition()).isEqualTo(2));
    }
}
