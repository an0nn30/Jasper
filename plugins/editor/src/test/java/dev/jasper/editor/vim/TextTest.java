package dev.jasper.editor.vim;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TextTest {
    @Test void stepsByGrapheme() {
        String combining = "café!";
        assertThat(Text.next(combining, 3)).isEqualTo(5);
        assertThat(Text.previous(combining, 5)).isEqualTo(3);
        String emoji = "a😀b";
        assertThat(Text.next(emoji, 1)).isEqualTo(3);
        assertThat(Text.previous(emoji, 3)).isEqualTo(1);
        assertThat(Text.last("abc")).isEqualTo(2);
        assertThat(Text.last("")).isZero();
        assertThat(Text.characters(combining, 5)).isEqualTo(4);
        assertThat(Text.column(combining, 4)).isEqualTo(5);
    }

    @Test void measuresDisplayColumnsWithTabsAndWideCharacters() {
        assertThat(Text.virtcol("a\tb", 2, 8)).isEqualTo(8);
        assertThat(Text.virtcol("日本x", 2, 8)).isEqualTo(4);
        assertThat(Text.columnAt("a\tb", 5, 8)).isEqualTo(1);
        assertThat(Text.columnAt("abc", 10, 8)).isEqualTo(3);
        assertThat(Text.width(0x0301)).isZero();
    }

    @Test void readsAndBuildsIndents() {
        assertThat(Text.firstNonBlank("  \tx")).isEqualTo(3);
        assertThat(Text.blank(" \t")).isTrue();
        assertThat(Text.indent("\t  x", 4)).isEqualTo(6);
        assertThat(Text.makeIndent(10, 4, false)).isEqualTo("\t\t  ");
        assertThat(Text.makeIndent(10, 4, true)).isEqualTo(" ".repeat(10));
        assertThat(Text.makeIndent(0, 4, false)).isEmpty();
    }
}
