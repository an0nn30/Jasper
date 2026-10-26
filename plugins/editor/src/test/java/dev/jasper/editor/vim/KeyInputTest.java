package dev.jasper.editor.vim;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyInputTest {
    @Test void readsCharactersAndNamedKeys() {
        assertThat(KeyInput.parse("d2w")).containsExactly(KeyInput.of('d'), KeyInput.of('2'), KeyInput.of('w'));
        assertThat(KeyInput.parse("ihi<Esc>")).containsExactly(KeyInput.of('i'), KeyInput.of('h'), KeyInput.of('i'), KeyInput.named("Esc"));
        assertThat(KeyInput.parse("<C-w><S-Tab><M-x><D-s>")).containsExactly(
            new KeyInput("w", KeyInput.CTRL), new KeyInput("Tab", KeyInput.SHIFT), new KeyInput("x", KeyInput.ALT), new KeyInput("s", KeyInput.META));
    }

    @Test void namesAreCaseInsensitiveAndHaveAliases() {
        assertThat(KeyInput.parse("<esc><ESCAPE><Return><enter><bs><Del><space><lt><Bar><Bslash><f12>")).containsExactly(
            KeyInput.named("Esc"), KeyInput.named("Esc"), KeyInput.named("CR"), KeyInput.named("CR"), KeyInput.named("BS"),
            KeyInput.named("Del"), KeyInput.of(' '), KeyInput.of('<'), KeyInput.of('|'), KeyInput.of('\\'), KeyInput.named("F12"));
    }

    @Test void readsTheNotationMappingsUse() {
        assertThat(KeyInput.parse("<NL><nul><Char-65><char-0x42><Char-0103><Plug>")).containsExactly(
            new KeyInput("j", KeyInput.CTRL), new KeyInput("@", KeyInput.CTRL), KeyInput.of('A'), KeyInput.of('B'), KeyInput.of('C'),
            KeyInput.named("Plug"));
        assertThat(KeyInput.parse("<Char-x>")).as("not a number").hasSize(8);
        assertThat(KeyInput.named("Plug").notation()).isEqualTo("<Plug>");
    }

    @Test void anActionKeyCarriesAnEditorCommandId() {
        assertThat(KeyInput.parse("<Action>(save)x")).containsExactly(KeyInput.action("save"), KeyInput.of('x'));
        assertThat(KeyInput.action("find").notation()).isEqualTo("<Action>(find)");
        assertThat(KeyInput.action("find").actionId()).isEqualTo("find");
        assertThat(KeyInput.of('a').actionId()).isNull();
        assertThat(KeyInput.parse("<Action>()")).as("no id: literal").hasSize(10);
    }

    @Test void ctrlShiftLetterIsItsOwnKey() {
        int both = KeyInput.CTRL | KeyInput.SHIFT;
        assertThat(KeyInput.parse("<C-V>")).isEqualTo(KeyInput.parse("<C-v>"));
        assertThat(KeyInput.parse("<C-S-v>")).containsExactly(new KeyInput("v", both));
        assertThat(KeyInput.parse("<C-S-V>")).isEqualTo(KeyInput.parse("<C-S-v>"));
        assertThat(KeyInput.parse("<S-C-v>")).isEqualTo(KeyInput.parse("<C-S-v>"));
        assertThat(KeyInput.parse("<C-S-v>")).isNotEqualTo(KeyInput.parse("<C-v>"));
        assertThat(KeyInput.parse("<C-S-v>").getFirst().notation()).isEqualTo("<C-S-v>");
        assertThat(KeyInput.parse("<S-a>")).isEqualTo(KeyInput.parse("A"));
    }

    @Test void normalizesCtrlAndShiftAsVimDoes() {
        assertThat(KeyInput.parse("<C-W>")).containsExactly(new KeyInput("w", KeyInput.CTRL));
        assertThat(KeyInput.parse("<C-[>")).containsExactly(KeyInput.named("Esc"));
        assertThat(KeyInput.parse("<C-i><C-m>")).containsExactly(KeyInput.named("Tab"), KeyInput.named("CR"));
        assertThat(KeyInput.parse("<S-a>")).containsExactly(KeyInput.of('A'));
        assertThat(KeyInput.parse("<M-S-a>")).containsExactly(new KeyInput("A", KeyInput.ALT));
        assertThat(KeyInput.ctrl('[')).isEqualTo(KeyInput.named("Esc"));
    }

    @Test void aLessThanThatNamesNothingIsLiteral() {
        assertThat(KeyInput.parse("<a>")).containsExactly(KeyInput.of('<'), KeyInput.of('a'), KeyInput.of('>'));
        assertThat(KeyInput.parse("a<b")).containsExactly(KeyInput.of('a'), KeyInput.of('<'), KeyInput.of('b'));
        assertThat(KeyInput.parse("<")).containsExactly(KeyInput.of('<'));
    }

    @Test void supplementaryCharactersAreOneKey() {
        assertThat(KeyInput.parse("😀x")).containsExactly(KeyInput.of(0x1F600), KeyInput.of('x'));
    }

    @Test void formatsBackToTheSameKeys() {
        for (String notation : List.of("d2w", "ihello<Esc>", "<C-w>", "<S-Tab>", "<lt>", "a b", "<C-Space>", "<D-s>", "\"ayy", "f|"))
            assertThat(KeyInput.parse(KeyInput.format(KeyInput.parse(notation)))).isEqualTo(KeyInput.parse(notation));
        assertThat(KeyInput.of('<').notation()).isEqualTo("<lt>");
        assertThat(new KeyInput(" ", KeyInput.CTRL).notation()).isEqualTo("<C-Space>");
        assertThat(KeyInput.named("Esc").is("<esc>")).isTrue();
        assertThat(KeyInput.of('a').plainChar()).isEqualTo('a');
        assertThat(new KeyInput("a", KeyInput.CTRL).plainChar()).isEqualTo(-1);
    }

    @Test void rejectsWhatIsNotAKey() {
        assertThatThrownBy(() -> new KeyInput("ab", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KeyInput("", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
