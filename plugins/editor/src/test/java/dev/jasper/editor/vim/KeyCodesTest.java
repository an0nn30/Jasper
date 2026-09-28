package dev.jasper.editor.vim;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Keys as a register's text, as Vim records them (spec 4.3). */
class KeyCodesTest {
    @Test void charactersAndControlKeysAreThemselves() {
        assertThat(KeyCodes.text(KeyInput.parse("ab<C-a><Esc><CR><Tab><C-j>é"))).isEqualTo("ab\u0001\u001b\r\t\né");
    }

    @Test void namedKeysAreVimsSpecialSequences() {
        assertThat(KeyCodes.text(KeyInput.parse("<BS><Left><End><F10><S-Tab>"))).isEqualTo("\u0080kb\u0080kl\u0080@7\u0080k;\u0080kB");
        assertThat(KeyCodes.text(KeyInput.parse("<S-Up><C-Left>"))).isEqualTo("\u0080ý\u0004\u0080ýU");
        assertThat(KeyCodes.text(KeyInput.parse("<D-s><C-Up>"))).as("other modified keys carry their modifiers")
            .isEqualTo("\u0080ü\u0080s\u0080ü\u0004\u0080ku");
    }

    @Test void theTextReadsBackAsTheSameKeys() {
        for (String notation : List.of("dw<Esc>", "i1<BS>2<Left><Esc>", "<S-Up><C-Left><D-s><C-Up><M-x><F12><S-F3><Plug>", "<C-@>x", "a<C-j>b"))
            assertThat(KeyCodes.keys(KeyCodes.text(KeyInput.parse(notation)))).as(notation).isEqualTo(KeyInput.parse(notation));
        assertThat(KeyCodes.keys("\u007f")).as("DEL").containsExactly(KeyInput.named("Del"));
        assertThat(KeyCodes.keys("\u0080")).as("a lone special byte").containsExactly(KeyInput.of(0x80));
        assertThat(KeyCodes.keys("\u0080zz")).as("an unknown code").containsExactly(KeyInput.of(0x80), KeyInput.of('z'), KeyInput.of('z'));
    }

    @Test void registersShowTheBytesAsVimDoes() {
        assertThat(String.join("", KeyCodes.shown("i\u0080kb\u001b\u0080ý\u0004é"))).isEqualTo("i<80>kb^[<80><fd>^Dé");
        assertThat(KeyCodes.cells("<80>")).isEqualTo(4);
        assertThat(KeyCodes.cells("日")).isEqualTo(2);
    }
}
