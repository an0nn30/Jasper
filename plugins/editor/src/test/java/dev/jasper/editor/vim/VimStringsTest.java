package dev.jasper.editor.vim;

import dev.jasper.editor.vim.regex.VimRegex;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Vim script's string functions as matchit's port uses them; every expectation is what Vim 9.1 returned. */
class VimStringsTest {
    private final VimStrings s = new VimStrings(false, VimRegex.Settings.DEFAULT.keyword());

    @Test void substituteReplacesAsVimDoes() {
        assertThat(s.substitute("a,b,,c", ",", ";", "g")).isEqualTo("a;b;;c");
        assertThat(s.substitute("a,b,,c", ",", ";", "")).isEqualTo("a;b,,c");
        assertThat(s.substitute("abc", "x*", "-", "g")).as("an empty match at every character").isEqualTo("-a-b-c-");
        assertThat(s.substitute("foo(bar)", "\\\\\\@1<!(", "\\\\%(", "g")).isEqualTo("foo\\%(bar)");
        assertThat(s.substitute("ab", "\\(a\\)\\(b\\)", "\\2\\1&\\\\", "")).isEqualTo("baab\\");
        assertThat(s.substitute("a:b\\:c", "\\\\\\@1<!\\%(\\\\\\\\\\)*\\zs:", "|", "g")).as("s:notslash").isEqualTo("a|b\\:c");
    }

    @Test void splitDropsEmptyEnds() {
        assertThat(s.split("a,b,,c,", ",")).containsExactly("a", "b", "", "c");
        assertThat(s.split(",a,b", ",")).containsExactly("a", "b");
        assertThat(s.split("abc", "\\zs")).containsExactly("a", "b", "c");
        assertThat(s.split("x:y,z", "\\zs:.\\{-},")).containsExactly("x", "z");
    }

    @Test void matchFindsWhereAsVimDoes() {
        assertThat(s.match("foobar", "o\\+")).isEqualTo(1);
        assertThat(s.matchend("foobar", "o\\+")).isEqualTo(3);
        assertThat(s.match("foobar", "x")).isEqualTo(-1);
        assertThat(s.match("foobar", "foo\\zsbar")).isEqualTo(3);
        assertThat(s.matchstr("foobar", "o\\+b")).isEqualTo("oob");
        assertThat(s.matches("foobar", "^f")).isTrue();
        assertThat(new VimStrings(true, VimRegex.Settings.DEFAULT.keyword()).matches("FOO", "foo")).as("'ignorecase'").isTrue();
        assertThat(VimStrings.escape("a*b,c", "*,")).isEqualTo("a\\*b\\,c");
        assertThat(VimStrings.strpart("abcdef", 2, 3)).isEqualTo("cde");
        assertThat(VimStrings.strpart("abc", -1, 2)).isEqualTo("a");
        assertThat(VimStrings.strpart("abc", 5)).isEmpty();
    }
}
