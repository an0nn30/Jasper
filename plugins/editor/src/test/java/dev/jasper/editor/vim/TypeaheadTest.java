package dev.jasper.editor.vim;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Keys waiting in the typeahead, mapped before they run (spec 2.3 and 7.2), with mappings added to the table directly. */
class TypeaheadTest {
    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    private static void onEdt(Runnable test) throws Exception { SwingUtilities.invokeAndWait(test); }

    private static VimEditor editor(String text) {
        var area = new VimTextArea();
        area.setText(text);
        area.setCaretPosition(0);
        return new VimEditor(new Vim(new VimHost(r -> Optional.empty(), (r, t) -> { })), area, EditorDefaults.VIM);
    }

    private static void map(VimEditor editor, int modes, String lhs, String rhs, boolean noremap, boolean nowait) {
        editor.vim().mappings().add(modes, KeyInput.parse(lhs), KeyInput.parse(rhs), noremap, nowait);
    }

    private static void nmap(VimEditor editor, String lhs, String rhs) { map(editor, Mappings.NORMAL, lhs, rhs, false, false); }

    @Test void aMappingRunsItsRightHandSideMappedAgain() throws Exception {
        onEdt(() -> {
            var editor = editor("one\ntwo\nthree\n");
            nmap(editor, "x", "dd");
            nmap(editor, "y", "x");
            map(editor, Mappings.NORMAL, "z", "x", true, false);
            type(editor, "y");
            assertThat(editor.buffer().line(0)).as("y is x is dd").isEqualTo("two");
            type(editor, "z");
            assertThat(editor.buffer().line(0)).as("noremap: z is the x command").isEqualTo("wo");
            type(editor, "2x");
            assertThat(editor.buffer().count()).as("a count applies to the first command").isEqualTo(1);
        });
    }

    @Test void mappingsThatMapEachOtherFailWithE223() throws Exception {
        onEdt(() -> {
            var editor = editor("one\ntwo\n");
            nmap(editor, "x", "y");
            nmap(editor, "y", "x");
            int bells = editor.bells();
            type(editor, "xj");
            assertThat(editor.messageLog()).contains("E223: Recursive mapping");
            assertThat(editor.bells()).isEqualTo(bells + 1);
            assertThat(editor.cursor().line).as("the typed j still runs").isEqualTo(1);
        });
    }

    @Test void aFailingCommandDropsTheRestOfTheMappingButNotTypedKeys() throws Exception {
        onEdt(() -> {
            var editor = editor("one\ntwo\n");
            nmap(editor, "x", "jjdd");
            type(editor, "xx");
            assertThat(editor.buffer().count()).as("dd never ran").isEqualTo(2);
            assertThat(editor.cursor().line).isEqualTo(1);
        });
    }

    @Test void aMappingsChangesUndoAsOneStep() throws Exception {
        onEdt(() -> {
            var editor = editor("a\nb\nc\nd\n");
            nmap(editor, "x", "ddjdd");
            type(editor, "xu");
            assertThat(editor.buffer().line(0)).isEqualTo("a");
            assertThat(editor.buffer().count()).as("both deletes undone").isEqualTo(4);
            type(editor, "ddddu");
            assertThat(editor.buffer().count()).as("typed commands undo one at a time").isEqualTo(3);
        });
    }

    @Test void ctrlCStopsAMappingThatRunsOn() throws Exception {
        onEdt(() -> {
            var editor = editor("one\n");
            nmap(editor, "x", "ggx");
            int[] looks = { 0 };
            editor.typeahead().interruptCheck(() -> ++looks[0] % 3 == 0);
            int bells = editor.bells();
            type(editor, "x");
            assertThat(looks[0]).as("a look every %d keys", Typeahead.BREAK_CHECK).isEqualTo(3);
            assertThat(editor.bells()).isEqualTo(bells + 1);
            assertThat(editor.typeahead().waiting()).isFalse();
            type(editor, ":normal x<CR>");
            assertThat(looks[0]).as(":normal looks too").isEqualTo(6);
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
        });
    }

    @Test void aPrefixWaitsUntilTheTimeoutResolvesIt() throws Exception {
        onEdt(() -> {
            var editor = editor("one\ntwo\n");
            nmap(editor, "ab", "dd");
            type(editor, "2a");
            assertThat(editor.mode()).as("a waits for b").isEqualTo(Mode.NORMAL);
            assertThat(editor.showcmd()).as("the count and the waiting key").isEqualTo("2a");
            assertThat(editor.typeahead().timing()).isTrue();
            editor.timeoutNow();
            assertThat(editor.mode()).as("a alone appends").isEqualTo(Mode.INSERT);
            assertThat(editor.typeahead().timing()).isFalse();
        });
    }

    @Test void theNextKeyEndsTheWait() throws Exception {
        onEdt(() -> {
            var editor = editor("one\ntwo\n");
            nmap(editor, "ab", "dd");
            type(editor, "a");
            type(editor, "b");
            assertThat(editor.buffer().line(0)).isEqualTo("two");
            assertThat(editor.typeahead().timing()).isFalse();
            nmap(editor, "xy", "yy");
            map(editor, Mappings.NORMAL, "x", "dd", false, true);
            type(editor, "x");
            assertThat(editor.buffer().count()).as("<nowait> does not wait for xy").isEqualTo(1);
        });
    }

    @Test void theTimerRunsOutAfterTimeoutlen() throws Exception {
        var editor = new AtomicReference<VimEditor>();
        onEdt(() -> {
            editor.set(editor("one\n"));
            nmap(editor.get(), "ab", "dd");
            type(editor.get(), ":set tm=40<CR>a");
        });
        long end = System.nanoTime() + 5_000_000_000L;
        var mode = new AtomicReference<Mode>();
        do {
            Thread.sleep(20);
            onEdt(() -> mode.set(editor.get().mode()));
        } while (mode.get() != Mode.INSERT && System.nanoTime() < end);
        assertThat(mode.get()).as("the Swing timer resolved a").isEqualTo(Mode.INSERT);
    }

    @Test void withNotimeoutKeysWaitForTheNextKey() throws Exception {
        onEdt(() -> {
            var editor = editor("one\n");
            nmap(editor, "ab", "dd");
            type(editor, ":set notimeout<CR>a");
            assertThat(editor.typeahead().timing()).isFalse();
            assertThat(editor.typeahead().waiting()).isTrue();
            type(editor, "x");
            assertThat(editor.mode()).as("a then x: append an x").isEqualTo(Mode.INSERT);
            assertThat(editor.buffer().line(0)).isEqualTo("oxne");
        });
    }

    @Test void theKeyAfterACountDigitOrACommandsFirstKeyIsNotMapped() throws Exception {
        onEdt(() -> {
            var editor = editor("a1\na2\na3\na4\na5\na6\na7\na8\na9\na10\na11\na12\n");
            nmap(editor, "0", "dd");
            nmap(editor, "e", "x");
            type(editor, "10j");
            assertThat(editor.cursor().line).as("10j, not 1 and dd").isEqualTo(10);
            type(editor, "fe");
            assertThat(editor.buffer().count()).as("f takes e as it is").isEqualTo(12);
            type(editor, "0");
            assertThat(editor.buffer().count()).as("0 alone is mapped").isEqualTo(11);
        });
    }

    @Test void mappingsApplyInTheirModes() throws Exception {
        onEdt(() -> {
            var editor = editor("one two\n");
            map(editor, Mappings.INSERT, "jk", "<Esc>", false, false);
            map(editor, Mappings.OP_PENDING, "w", "e", false, false);
            map(editor, Mappings.CMDLINE, "XX", "s/one/1/", false, false);
            type(editor, "iabjk");
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
            assertThat(editor.buffer().line(0)).isEqualTo("abone two");
            type(editor, "0dw");
            assertThat(editor.buffer().line(0)).as("dw is de").isEqualTo(" two");
            type(editor, "u:XX<CR>");
            assertThat(editor.buffer().line(0)).isEqualTo("ab1 two");
        });
    }

    @Test void normalUsesMappingsUnlessBang() throws Exception {
        onEdt(() -> {
            var editor = editor("one\ntwo\n");
            nmap(editor, "x", "dd");
            type(editor, ":normal! x<CR>");
            assertThat(editor.buffer().line(0)).isEqualTo("ne");
            type(editor, ":normal x<CR>");
            assertThat(editor.buffer().line(0)).isEqualTo("two");
        });
    }

    @Test void aMappedKeyIsVimsEvenWhenTheEditorWouldTakeIt() throws Exception {
        onEdt(() -> {
            var editor = editor("one\ntwo\n");
            nmap(editor, "<F2>", "dd");
            nmap(editor, "<D-d>", "j");
            assertThat(editor.offer(KeyInput.named("F2"))).isTrue();
            assertThat(editor.buffer().line(0)).isEqualTo("two");
            assertThat(editor.offer(new KeyInput("d", KeyInput.META))).as("a mapped ⌘ key").isTrue();
            assertThat(editor.offer(new KeyInput("e", KeyInput.META))).as("an unmapped one").isFalse();
            nmap(editor, "gx", "dd");
            assertThat(editor.offer(KeyInput.of('g'))).isTrue();
            assertThat(editor.offer(KeyInput.named("F3"))).as("keys waiting for a mapping take the next key").isTrue();
        });
    }

    @Test void commandLinesFromMappingsDoNotJoinTheHistory() throws Exception {
        onEdt(() -> {
            var editor = editor("a\n");
            nmap(editor, "x", ":s/a/b/<CR>");
            type(editor, "x");
            assertThat(editor.buffer().line(0)).isEqualTo("b");
            assertThat(editor.vim().commandHistory()).isEmpty();
            assertThat(editor.vim().registers().get(':').orElseThrow().text()).as("the : register has it").isEqualTo("s/a/b/");
            type(editor, ":s/b/c/<CR>");
            assertThat(editor.vim().commandHistory()).containsExactly("s/b/c/");
        });
    }
}
