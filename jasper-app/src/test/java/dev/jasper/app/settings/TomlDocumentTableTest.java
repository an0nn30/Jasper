package dev.jasper.app.settings;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class TomlDocumentTableTest {
    @Test void moveCarriesTheValueAsWrittenAndItsComment() {
        var moved = TomlDocument.parse("[ui.theme]\nname = 'x'\nterminal = \"dark\" # always dark\n[terminal]\nscrollback = 5\n")
            .move(List.of("ui", "theme", "terminal"), List.of("terminal", "colors"));
        assertThat(moved.text()).isEqualTo("[ui.theme]\nname = 'x'\n[terminal]\nscrollback = 5\ncolors = \"dark\" # always dark\n");
        var fraction = TomlDocument.parse("font.size = 16.0\n").move(List.of("font", "size"), List.of("terminal", "font", "size"));
        assertThat(fraction.text()).isEqualTo("[terminal.font]\nsize = 16.0\n");
        var taken = TomlDocument.parse("a = 1\nb = 2\n");
        assertThatIllegalArgumentException().isThrownBy(() -> taken.move(List.of("a"), List.of("b")));
        assertThatIllegalArgumentException().isThrownBy(() -> taken.move(List.of("zzz"), List.of("c")));
    }

    @Test void renameTableRewritesHeadersAndRefusesKeysItCannotReach() {
        var renamed = TomlDocument.parse("[font] # mine\n# The font I like\nfamily = 'Iosevka'\n[font.extra]\nx = 1\n")
            .renameTable(List.of("font"), List.of("terminal", "font"));
        assertThat(renamed.text()).isEqualTo("[terminal.font] # mine\n# The font I like\nfamily = 'Iosevka'\n[terminal.font.extra]\nx = 1\n");
        assertThatIllegalArgumentException().isThrownBy(() -> TomlDocument.parse("font.size = 1\n").renameTable(List.of("font"), List.of("terminal", "font")));
        assertThatIllegalArgumentException().isThrownBy(() -> TomlDocument.parse("[font]\nsize = 1\nfont2 = 1\n[x]\nfont.q = 2\n")
            .renameTable(List.of("x"), List.of("font")));
    }

    @Test void removeTableTakesValuesHeadersAndTheBlankLineAboveEachHeader() {
        String text = "[a]\nkeep = 1\n\n[palette.scopes.history]\nenabled = false\n\n[plugins.\"dev.x\"]\nx = 1\n";
        var document = TomlDocument.parse(text).removeTable(List.of("palette", "scopes"));
        assertThat(document.text()).isEqualTo("[a]\nkeep = 1\n\n[plugins.\"dev.x\"]\nx = 1\n");
        assertThat(document.removeTable(List.of("plugins", "dev.x")).text()).isEqualTo("[a]\nkeep = 1\n");
        var dotted = TomlDocument.parse("palette.scopes.history.enabled = false\nb = 1\n").removeTable(List.of("palette", "scopes"));
        assertThat(dotted.text()).isEqualTo("b = 1\n");
        var none = TomlDocument.parse("b = 1\n");
        assertThat(none.removeTable(List.of("nothing"))).isSameAs(none);
    }

    @Test void removeTableIfEmptyOnlyRemovesAHeaderWithNothingUnderIt() {
        var empty = TomlDocument.parse("x = 1\n\n[background]\n\n[ui]\ny = 2\n").removeTableIfEmpty(List.of("background"));
        assertThat(empty.text()).isEqualTo("x = 1\n\n[ui]\ny = 2\n");
        var used = TomlDocument.parse("[background]\nz = 1\n");
        assertThat(used.removeTableIfEmpty(List.of("background"))).isSameAs(used);
        var commented = TomlDocument.parse("[background] # old\n").removeTableIfEmpty(List.of("background"));
        assertThat(commented.text()).isEqualTo("# old\n");
        var absent = TomlDocument.parse("x = 1\n");
        assertThat(absent.removeTableIfEmpty(List.of("background"))).isSameAs(absent);
    }
}
