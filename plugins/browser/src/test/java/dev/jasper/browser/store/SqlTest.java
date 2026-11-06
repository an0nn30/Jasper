package dev.jasper.browser.store;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** The stores' one search rule (plan 5's H16), which history and bookmarks both search by. */
class SqlTest {
    private static final String PAIR = "\uD83D\uDE00";

    @Test void aSearchCountsItsFirst256CharactersThenTheirFirstEightWordsAsTyped() {
        assertThat(Sql.searched("")).isEmpty();
        assertThat(Sql.searched("  \t ")).isEmpty();
        assertThat(Sql.searched("  one  two\tthree ")).isEqualTo("one  two\tthree");
        assertThat(Sql.searched("w1 w2 w3 w4 w5 w6 w7 w8 w9 w10")).isEqualTo("w1 w2 w3 w4 w5 w6 w7 w8");
        assertThat(Sql.searched("y".repeat(300))).isEqualTo("y".repeat(256));
        assertThat(Sql.searched(" ".repeat(250) + "abcdefghij")).as("the cut comes before the words").isEqualTo("abcdef");
        assertThat(Sql.searched("a ".repeat(32_768))).isEqualTo("a a a a a a a a");
    }

    @Test void theCutNeverSplitsACharactersTwoHalves() {
        assertThat(Sql.searched("y".repeat(255) + PAIR + "z")).isEqualTo("y".repeat(255));
        assertThat(Sql.searched("y".repeat(254) + PAIR + "z")).isEqualTo("y".repeat(254) + PAIR);
    }
}
