package dev.jasper.editor.vim;

import dev.jasper.editor.vim.regex.VimRegex;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Vim's two last patterns (search.c's spats and last_idx) and what the Ex commands need from a translated pattern. */
class SearchPatternsTest {
    @Test void theSearchAndSubstitutePatternsAreKeptApart() {
        var search = new Search();
        search.save(Search.SEARCH, "a");
        assertThat(search.pattern()).isEqualTo("a");
        assertThat(search.choose("b", Search.SUBSTITUTE, -1)).isEqualTo("b");
        assertThat(search.searchPattern()).as(":s leaves the search pattern").isEqualTo("a");
        assertThat(search.pattern()).as("the last used pattern, which 'hlsearch' shows").isEqualTo("b");
        assertThat(search.choose("", Search.SEARCH, -1)).as("empty means the last used, and it becomes the search pattern").isEqualTo("b");
        assertThat(search.searchPattern()).isEqualTo("b");
        assertThat(search.history()).containsExactly("b");
        assertThatThrownBy(() -> new Search().choose("", Search.SUBSTITUTE, Search.SUBSTITUTE))
            .hasMessage("E33: No previous substitute regular expression");
        assertThatThrownBy(() -> new Search().choose("", Search.SEARCH, -1)).hasMessage("E35: No previous regular expression");
    }

    @Test void aPatternKnowsWhetherItCanSpanLinesAndWhereItsGroupsAre() {
        var pattern = VimRegex.compile("a\\(b\\)c", VimRegex.Settings.DEFAULT);
        assertThat(pattern.multiline()).isFalse();
        var m = pattern.pattern().matcher("xabc");
        assertThat(m.find()).isTrue();
        assertThat(pattern.groupStart(m, 0)).isEqualTo(1);
        assertThat(pattern.groupStart(m, 1)).isEqualTo(2);
        assertThat(pattern.groupEnd(m, 1)).isEqualTo(3);
        assertThat(pattern.groupStart(m, 2)).as("a group the pattern does not have").isEqualTo(-1);
        assertThat(VimRegex.compile("a\\nb", VimRegex.Settings.DEFAULT).multiline()).isTrue();
        assertThat(VimRegex.compile("a\\_sb", VimRegex.Settings.DEFAULT).multiline()).isTrue();
        assertThat(VimRegex.compile("a\\%$", VimRegex.Settings.DEFAULT).multiline()).isTrue();
    }
}
