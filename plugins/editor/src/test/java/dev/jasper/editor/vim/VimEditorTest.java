package dev.jasper.editor.vim;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.awt.event.ActionEvent;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import org.fife.ui.rtextarea.CaretStyle;
import org.fife.ui.rtextarea.ConfigurableCaret;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

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

    @Test void enterAndOOnALastLineWithoutAFinalBreakOpenANewLine() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "");
            type(editor, "iabc<CR>");
            assertThat(editor.cursor()).as("a new, empty file").isEqualTo(new Pos(1, 0));
            type(editor, "d<CR>");
            assertThat(area.getText()).isEqualTo("abc\nd\n\n");
            assertThat(editor.cursor()).isEqualTo(new Pos(2, 0));
            editor = editor(area, "abc");
            type(editor, "ox");
            assertThat(area.getText()).isEqualTo("abc\nx\n");
            assertThat(editor.cursor()).isEqualTo(new Pos(1, 1));
        });
    }

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

    @Test void discardAllEditsKeepsVimModeOnTheReplacedUndoManager() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc\n");
            // RTextArea.discardAllEdits() replaces the undo manager through createUndoManager(); the fresh one
            // must still know Vim is on, or edits made outside a Vim command (a reload, the Editor's own actions)
            // fall back to RSyntaxTextArea's own grouping instead of becoming their own step.
            area.setText("y\n");
            area.discardAllEdits();
            editor.textReplaced();
            try {
                area.getDocument().insertString(0, "a", null);
                area.getDocument().insertString(1, "b", null);
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
            type(editor, "u");
            assertThat(area.getText()).as("each edit outside Vim is its own step, even on the replaced manager").isEqualTo("ay\n");
        });
    }

    @Test void anOutsideUndoDuringAnInsertReopensTheStepSoTheRestUndoesTogether() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "");
            type(editor, "ihello");
            // Simulates an outside undo (a real ⌘Z) arriving mid-insert: the engine still believes its step
            // is open, so the undo manager must reopen it, or every later character becomes its own step.
            area.undoLastAction();
            type(editor, "world<Esc>");
            type(editor, "u");
            assertThat(area.getText()).as("the whole \"world\" insert undoes as one step").isEqualTo("");
        });
    }

    @Test void aFailedRepeatOfAnInsertKeyIsNotReplayed() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc");
            // <Del> at the end of the buffer fails every time (nothing follows to join); real Vim 9.1 does not
            // replay a key that failed, so only the successful "z" repeats.
            type(editor, "2A<Del>z<Esc>");
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
            assertThat(area.getText()).isEqualTo("abczz");
        });
    }

    @Test void aFailedRepeatOfABackspaceIsNotReplayedEither() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc");
            // Real Vim 9.1: the second <BS> hits the start of the insert and fails, so only "x<BS>y" (net "y")
            // repeats, not "x<BS><BS>y".
            type(editor, "2ix<BS><BS>y<Esc>");
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
            assertThat(area.getText()).isEqualTo("yyabc");
        });
    }

    @Test void aFailureDuringTheRepeatStillLeavesInsertMode() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "a");
            // The first <Del> succeeds (removing the only character); replaying it for the count then fails on
            // the now-empty buffer. That failure must not leave the command stuck in Insert mode.
            type(editor, "2i<Del><Esc>");
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
            assertThat(area.getText()).isEmpty();
        });
    }

    private static KeyEvent pressed(VimTextArea area, int modifiers, int keyCode) {
        return new KeyEvent(area, KeyEvent.KEY_PRESSED, 0, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    private static KeyEvent typed(VimTextArea area, int modifiers, char c) {
        return new KeyEvent(area, KeyEvent.KEY_TYPED, 0, modifiers, KeyEvent.VK_UNDEFINED, c);
    }

    /** Binds a stroke in the area's own map, as EditorTab.bind does, to an action that counts its runs. */
    private static AtomicInteger bind(VimTextArea area, KeyStroke stroke, String id) {
        var runs = new AtomicInteger();
        area.getInputMap(JComponent.WHEN_FOCUSED).put(stroke, id);
        area.getActionMap().put(id, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { runs.incrementAndGet(); }
        });
        return runs;
    }

    @Test void ctrlShiftMappingsAreVimKeys() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "one\ntwo\n");
            editor.vim().mappings().add(Mappings.NORMAL, KeyInput.parse("<C-S-j>"), KeyInput.parse("dd"), true, false);
            assertThat(editor.offer(KeyInput.parse("<C-S-J>").getFirst())).isTrue();
            assertThat(area.getText()).isEqualTo("two\n");
            editor.offer(KeyInput.parse("<S-C-j>").getFirst());
            assertThat(area.getText()).isEmpty();
            var plain = editor(new VimTextArea(), "abc\n");
            var ctrlShiftV = KeyInput.parse("<C-S-v>").getFirst();
            assertThat(plain.offer(ctrlShiftV)).as("Normal").isFalse();
            type(plain, "v");
            assertThat(plain.offer(ctrlShiftV)).as("Visual").isFalse();
            type(plain, "<Esc>i");
            assertThat(plain.offer(ctrlShiftV)).as("Insert").isFalse();
        });
    }

    @Test void ctrlShiftVReachesPasteFromAListAndCtrlShiftWInInsertModeLeavesVim() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc def\n");
            int ctrlShift = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
            var pasted = new AtomicInteger();
            area.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_V, ctrlShift), org.fife.ui.rtextarea.RTextAreaEditorKit.clipboardHistoryAction);
            area.getActionMap().put(org.fife.ui.rtextarea.RTextAreaEditorKit.clipboardHistoryAction, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) { pasted.incrementAndGet(); }
            });
            area.processKeyEvent(pressed(area, ctrlShift, KeyEvent.VK_V));
            assertThat(pasted).as("Normal mode").hasValue(1);
            type(editor, "v");
            area.processKeyEvent(pressed(area, ctrlShift, KeyEvent.VK_V));
            assertThat(pasted).as("Visual mode").hasValue(2);
            type(editor, "<Esc>$a");
            var parent = new javax.swing.JPanel();
            parent.add(area);
            var closed = new AtomicInteger();
            parent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_W, ctrlShift), "close");
            parent.getActionMap().put("close", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) { closed.incrementAndGet(); }
            });
            area.processKeyEvent(pressed(area, ctrlShift, KeyEvent.VK_W));
            assertThat(closed).as("Ctrl+Shift+W in Insert mode is not Vim's").hasValue(1);
            assertThat(area.getText()).isEqualTo("abc def\n");
        });
    }

    @Test void aKeyVimDeclinesInNormalModeRingsTheBellInsteadOfEditing() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc\n");
            int bells = editor.bells();
            var tab = pressed(area, 0, KeyEvent.VK_TAB);
            area.processKeyEvent(tab);
            var tabTyped = typed(area, 0, '\t');
            area.processKeyEvent(tabTyped);
            assertThat(tab.isConsumed()).isTrue();
            assertThat(tabTyped.isConsumed()).isTrue();
            area.processKeyEvent(pressed(area, InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_BACK_SPACE));
            area.processKeyEvent(pressed(area, InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_TAB));
            assertThat(area.getText()).as("RSyntaxTextArea's editing actions do not run in Normal mode").isEqualTo("abc\n");
            assertThat(editor.bells()).isEqualTo(bells + 3);
            var notAllowed = bind(area, KeyStroke.getKeyStroke(KeyEvent.VK_F9, InputEvent.CTRL_DOWN_MASK), "not.an.editor.command");
            area.processKeyEvent(pressed(area, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_F9));
            assertThat(notAllowed).as("a text-area binding that is not an Editor command").hasValue(0);
            type(editor, "i");
            area.processKeyEvent(pressed(area, 0, KeyEvent.VK_TAB));
            area.processKeyEvent(typed(area, 0, '\t'));
            assertThat(area.getText()).as("Insert mode keeps the text area's bindings").isEqualTo("\tabc\n");
        });
    }

    @Test void theEditorsOwnCommandsAndCommandKeysStillReachTheirBindingsInNormalMode() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc\n");
            var save = bind(area, KeyStroke.getKeyStroke(KeyEvent.VK_F9, InputEvent.CTRL_DOWN_MASK), "editor.test");
            area.allowInNormalMode("editor.test");
            int bells = editor.bells();
            var event = pressed(area, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_F9);
            area.processKeyEvent(event);
            assertThat(save).hasValue(1);
            var meta = bind(area, KeyStroke.getKeyStroke(KeyEvent.VK_F10, InputEvent.META_DOWN_MASK), "not.allowed.but.command");
            area.processKeyEvent(pressed(area, InputEvent.META_DOWN_MASK, KeyEvent.VK_F10));
            assertThat(meta).as("a ⌘ key always goes on").hasValue(1);
            assertThat(editor.bells()).isEqualTo(bells);
            assertThat(area.getText()).isEqualTo("abc\n");
        });
    }

    @Test void inputMethodTextIsDroppedOutsideInsertMode() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc\n");
            var text = new java.text.AttributedString("\u00e9").getIterator();
            var event = new java.awt.event.InputMethodEvent(area, java.awt.event.InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, text, 1, null, null);
            area.processInputMethodEvent(event);
            assertThat(area.getText()).isEqualTo("abc\n");
            assertThat(event.isConsumed()).isTrue();
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
        });
    }

    @Test void aDotWhoseReplayedDelFailsEndsInNormalMode() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc\n\n");
            type(editor, "i<Del><Esc>j.");
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
            assertThat(area.vimUndo().stepOpen()).isFalse();
            assertThat(area.getText()).isEqualTo("bc\n\n");
        });
    }

    @Test void aRedoDuringAnInsertKeepsTheInsertOneStep() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc");
            type(editor, "xu");
            type(editor, "ipq");
            area.redoLastAction();
            type(editor, "rs<Esc>");
            assertThat(area.vimUndo().stepOpen()).isFalse();
            type(editor, "u");
            assertThat(area.getText()).as("the whole insert undoes as one step").isEqualTo("abc");
        });
    }

    @Test void deletingCharactersInALargeFileDoesNotRescanTheText() throws Exception {
        // The first line is the longest, so RSyntaxTextArea's own longest-line rescan stays out of the measurement.
        var line = "0123456789".repeat(9) + "\n";
        var text = "x".repeat(200) + "\n" + line.repeat(1_048_576 / line.length());
        long[] took = new long[1];
        assertTimeout(Duration.ofSeconds(10), () -> onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, text);
            type(editor, "j");
            long start = System.nanoTime();
            for (int i = 0; i < 1000; i++) type(editor, "xj");
            took[0] = (System.nanoTime() - start) / 1_000_000;
            assertThat(area.getText()).startsWith("x".repeat(200) + "\n" + line.substring(1));
        }));
        System.out.println("1000 xj on a 1 MB document: " + took[0] + " ms");
    }

    /** Puts the area in a panel whose ancestor map binds the stroke, as the app's workspace and hosted commands are. */
    private static AtomicInteger bindInAncestor(VimTextArea area, KeyStroke stroke, String id) {
        var parent = new javax.swing.JPanel();
        parent.add(area);
        var runs = new AtomicInteger();
        parent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(stroke, id);
        parent.getActionMap().put(id, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { runs.incrementAndGet(); }
        });
        return runs;
    }

    @Test void theAppsShortcutsOnAncestorsStillFireInNormalMode() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            var editor = editor(area, "abc\n");
            int ctrlShift = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
            var newTab = bindInAncestor(area, KeyStroke.getKeyStroke(KeyEvent.VK_T, ctrlShift), "app.newTab");
            ((JComponent) area.getParent()).getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK), "app.newTab");
            int bells = editor.bells();
            var event = pressed(area, ctrlShift, KeyEvent.VK_T);
            area.processKeyEvent(event);
            assertThat(newTab).as("Ctrl+Shift+T reaches the workspace's binding").hasValue(1);
            assertThat(event.isConsumed()).isTrue();
            var closeTab = pressed(area, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_W);
            area.processKeyEvent(closeTab);
            area.processKeyEvent(typed(area, InputEvent.CTRL_DOWN_MASK, (char) 23));
            assertThat(newTab).as("Ctrl+W reaches the hosted Close Pane binding").hasValue(2);
            assertThat(editor.bells()).as("a key an ancestor handled does not ring").isEqualTo(bells);
            area.processKeyEvent(pressed(area, 0, KeyEvent.VK_TAB));
            area.processKeyEvent(typed(area, 0, '\t'));
            area.processKeyEvent(pressed(area, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_F11));
            assertThat(area.getText()).isEqualTo("abc\n");
            assertThat(editor.bells()).as("Tab (held back) and an unhandled Ctrl+F11 ring").isEqualTo(bells + 2);
            area.processKeyEvent(pressed(area, InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_SHIFT));
            assertThat(editor.bells()).as("a lone modifier never rings").isEqualTo(bells + 2);
        });
    }

    /**
     * Vim's file marks are the file's (pane flexibility spec 6): when the editor of the view a mark was set in closes, the
     * mark goes to another editor of the file, still moving with the text, and it goes, untracked, with the file's last.
     */
    @Test void anUppercaseMarkStaysWithItsFileUntilItsLastEditorCloses() throws Exception {
        onEdt(() -> {
            var shown = new VimTextArea();
            shown.setText("1\n2\n3\n4\n");
            var other = new VimTextArea();
            other.share(shown);
            var first = new VimEditor(vim, shown, EditorDefaults.VIM);
            var second = new VimEditor(vim, other, EditorDefaults.VIM);
            type(second, "3GmA");
            second.close();
            assertThat(vim.globalMarks().get('A')).as("the mark is the file's other editor's").isNotNull()
                .extracting(Vim.GlobalMark::editor).isSameAs(first);
            type(first, "ggO0<Esc>'A");
            assertThat(first.cursor().line).as("it still moves with the text").isEqualTo(3);
            Marks marks = first.marks();
            first.close();
            assertThat(vim.globalMarks()).doesNotContainKey('A');
            assertThat(marks.tracked()).as("the file's last editor untracks it").isEmpty();
        });
    }
}
