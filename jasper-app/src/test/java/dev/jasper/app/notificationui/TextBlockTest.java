package dev.jasper.app.notificationui;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TextBlockTest {
    @Test void wordsWrapToTheWidth() {
        assertThat(TextBlock.wrap("aaaa bbbb cccc", String::length, 10, 0)).containsExactly("aaaa bbbb", "cccc");
    }

    @Test void aWordWiderThanTheLineBreaksByCharacter() {
        assertThat(TextBlock.wrap("abcdefghijklm", String::length, 5, 0)).containsExactly("abcde", "fghij", "klm");
    }

    @Test void lineBreaksAreKept() {
        assertThat(TextBlock.wrap("one\n\ntwo", String::length, 10, 0)).containsExactly("one", "", "two");
    }

    @Test void cutTextEndsInAnEllipsis() {
        assertThat(TextBlock.wrap("a b c d e f", String::length, 3, 2)).containsExactly("a b", "c…");
        assertThat(TextBlock.wrap("a b", String::length, 3, 2)).containsExactly("a b");
    }

    @Test void surrogatePairsStayWhole() {
        assertThat(TextBlock.wrap("😀😀", String::length, 3, 0)).containsExactly("😀", "😀");
    }

    @Test void emptyTextHasNoLines() {
        assertThat(TextBlock.wrap("", String::length, 10, 5)).isEqualTo(List.of());
    }
}
