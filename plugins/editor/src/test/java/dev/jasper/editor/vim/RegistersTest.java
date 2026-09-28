package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Registers.Content;
import dev.jasper.editor.vim.Registers.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RegistersTest {
    private final Map<Character, String> clipboard = new HashMap<>();
    private final Registers registers = new Registers(new VimHost(r -> Optional.ofNullable(clipboard.get(r)), clipboard::put));

    private static Content chars(String text) { return Content.of(text, Type.CHAR); }

    private static Content lines(String text) { return Content.of(text, Type.LINE); }

    @Test void contentKeepsVimsShape() {
        assertThat(lines("a\nb\n").lines()).containsExactly("a", "b");
        assertThat(lines("a\nb\n").text()).isEqualTo("a\nb\n");
        assertThat(chars("a\nb").lines()).containsExactly("a", "b");
        assertThat(chars("").lines()).containsExactly("");
    }

    @Test void aYankWritesRegisterZeroAndTheUnnamedRegisterPointsAtIt() {
        registers.yank((char) 0, chars("word"), "");
        assertThat(registers.get('0')).contains(chars("word"));
        assertThat(registers.get('"')).contains(chars("word"));
        registers.yank('a', chars("named"), "");
        assertThat(registers.get('0')).contains(chars("word"));
        assertThat(registers.get('"')).contains(chars("named"));
    }

    @Test void deletesShiftTheNumberedRegistersOrUseTheSmallDeleteRegister() {
        registers.delete((char) 0, lines("one\n"), 1, false, "");
        registers.delete((char) 0, lines("two\n"), 1, false, "");
        assertThat(registers.get('1')).contains(lines("two\n"));
        assertThat(registers.get('2')).contains(lines("one\n"));
        registers.delete((char) 0, chars("x"), 1, false, "");
        assertThat(registers.get('-')).contains(chars("x"));
        assertThat(registers.get('1')).contains(lines("two\n"));
        assertThat(registers.get('"')).contains(chars("x"));
        registers.delete((char) 0, chars("sentence"), 1, true, "");
        assertThat(registers.get('1')).as("% ( ) { } always use register 1").contains(chars("sentence"));
    }

    @Test void aNamedDeleteAlsoFillsRegisterOneAndAppendsWithUpperCase() {
        registers.delete('a', chars("one "), 1, false, "");
        assertThat(registers.get('1')).contains(chars("one "));
        assertThat(registers.get('-')).isEmpty();
        registers.delete('A', chars("two "), 1, false, "");
        assertThat(registers.get('a')).contains(chars("one two "));
        assertThat(registers.get('"')).contains(chars("two "));
        registers.yank('A', lines("line\n"), "");
        assertThat(registers.get('a')).contains(new Content(java.util.List.of("one two ", "line"), Type.LINE));
    }

    @Test void theBlackHoleKeepsEverything() {
        registers.yank((char) 0, chars("keep"), "");
        registers.delete('_', chars("gone"), 1, false, "");
        assertThat(registers.get('"')).contains(chars("keep"));
        assertThat(registers.get('_')).isEmpty();
    }

    @Test void plusAndStarAreTheClipboardAndRememberLinewiseText() {
        registers.yank('+', lines("a line\n"), "");
        assertThat(clipboard.get('+')).isEqualTo("a line\n");
        assertThat(registers.get('+')).contains(lines("a line\n"));
        clipboard.put('*', "from elsewhere");
        assertThat(registers.get('*')).contains(chars("from elsewhere"));
        clipboard.put('+', "two\nlines\n");
        assertThat(registers.get('+').orElseThrow().type()).isEqualTo(Type.LINE);
    }

    @Test void clipboardUnnamedSendsUnnamedYanksAndDeletesToTheClipboard() {
        registers.yank((char) 0, chars("yanked"), "unnamed");
        assertThat(clipboard.get('*')).isEqualTo("yanked");
        assertThat(registers.get('0')).isEmpty();
        registers.delete((char) 0, chars("x"), 1, false, "unnamedplus");
        assertThat(clipboard.get('+')).isEqualTo("x");
        assertThat(registers.get('-')).contains(chars("x"));
        assertThat(Registers.effective((char) 0, "unnamed")).isEqualTo('*');
        assertThat(Registers.effective('a', "unnamed")).isEqualTo('a');
    }

    @Test void theDotRegisterHoldsTheLastInsert() {
        registers.lastInserted("typed");
        assertThat(registers.get('.')).contains(chars("typed"));
        assertThat(Registers.valid('.', false)).isTrue();
        assertThat(Registers.valid('.', true)).isFalse();
        assertThat(Registers.valid('!', false)).isFalse();
    }

    @Test void windowsLineBreaksFromTheClipboardBecomeVimsLineBreaks() {
        clipboard.put('+', "one\r\ntwo\rthree\r\n");
        assertThat(registers.get('+')).contains(lines("one\ntwo\nthree\n"));
        registers.yank('+', chars("x\n"), "");
        clipboard.put('+', "x\r\n");
        assertThat(registers.get('+')).as("the text Jasper wrote, as the system clipboard gives it back").contains(chars("x\n"));
    }
}
