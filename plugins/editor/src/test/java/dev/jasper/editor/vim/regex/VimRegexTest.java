package dev.jasper.editor.vim.regex;

import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The translator on its own; the corpus's regex.case checks it against Vim by searching. */
class VimRegexTest {
    private static final VimRegex.Settings PLAIN = VimRegex.Settings.DEFAULT;

    /** @return the first match of a Vim pattern in the text, as Vim narrows it with \zs and \ze; null for none */
    private static String find(String pattern, String text) { return find(pattern, text, PLAIN); }

    private static String find(String pattern, String text, VimRegex.Settings settings) {
        VimRegex re = VimRegex.compile(pattern, settings);
        Matcher m = re.pattern().matcher(text);
        return m.find() ? text.substring(re.start(m), re.end(m)) : null;
    }

    @Test void magicLevelsDecideWhichCharactersAreSpecial() {
        assertThat(find("a.c", "abc")).isEqualTo("abc");
        assertThat(find("\\Ma.c", "abc a.c")).isEqualTo("a.c");
        assertThat(find("\\Va*c", "aac a*c")).isEqualTo("a*c");
        assertThat(find("\\v(ab){2}", "ab abab")).isEqualTo("abab");
        assertThat(find("\\(ab\\)\\{2}", "ab abab")).isEqualTo("abab");
        assertThat(find("a\\{-1,}", "aaa")).as("a lazy brace takes as few as it can").isEqualTo("a");
        assertThat(find("a\\{2,1}", "aaa")).as("Vim swaps reversed limits").isEqualTo("aa");
    }

    @Test void caretDollarAndStarFollowVimsPositionRules() {
        assertThat(find("a^b", "a^b")).isEqualTo("a^b");
        assertThat(find("a$b", "a$b")).isEqualTo("a$b");
        assertThat(find("*b", "a*b")).isEqualTo("*b");
        assertThat(find("^b", "a\nb")).isEqualTo("b");
        assertThat(find("a$", "ab a\nx")).isEqualTo("a");
        assertThat(find("x\\|^b", "a\nb")).isEqualTo("b");
    }

    @Test void classesAndLineBreaks() {
        assertThat(find("\\d\\+", "ab 123")).isEqualTo("123");
        assertThat(find("\\S\\+", "  ab\ncd")).isEqualTo("ab");
        assertThat(find("o\\_s*b", "foo\n  bar")).isEqualTo("oo\n  b".substring(1));
        assertThat(find("f\\_.*z", "fa\nbz")).isEqualTo("fa\nbz");
        assertThat(find("[^a]", "a\nb")).as("a negated bracket never takes the line break").isEqualTo("b");
        assertThat(find("[[:digit:]]\\+", "x42")).isEqualTo("42");
        assertThat(find("[a", "x [a")).as("[ without ] is a literal").isEqualTo("[a");
        assertThat(find("\\%d65", "xA")).isEqualTo("A");
    }

    @Test void wordBoundariesUseIskeyword() {
        assertThat(find("\\<cat\\>", "concat cat")).isEqualTo("cat");
        var dashes = new VimRegex.Settings(false, false, "a-z\\-", null);
        assertThat(find("\\<a-b\\>", "xa-b a-b", dashes)).as("with - in 'iskeyword', a-b is one word").isEqualTo("a-b");
    }

    @Test void zsAndZeNarrowTheMatch() {
        assertThat(find("foo\\zsbar", "foobar")).isEqualTo("bar");
        assertThat(find("foo\\zebar", "foobaz foobar")).isEqualTo("foo");
        assertThat(find("\\<fu\\%[nction]\\>", "fun")).isEqualTo("fun");
    }

    @Test void caseFollowsTheOptionsAndTheFlags() {
        var ic = new VimRegex.Settings(true, false, VimRegex.Settings.DEFAULT.keyword(), null);
        var scs = new VimRegex.Settings(true, true, VimRegex.Settings.DEFAULT.keyword(), null);
        assertThat(find("abc", "ABC", ic)).isEqualTo("ABC");
        assertThat(find("Abc", "abc Abc", scs)).as("smartcase: an upper-case letter matches case").isEqualTo("Abc");
        assertThat(find("\\Sbc", "xABC", scs)).as("an escaped letter does not count").isEqualTo("ABC");
        assertThat(find("\\cabc", "ABC")).isEqualTo("ABC");
        assertThat(find("\\Cabc", "ABC abc", ic)).isEqualTo("abc");
    }

    @Test void errorsNameTheProblem() {
        assertThatThrownBy(() -> VimRegex.compile("a\\%Vb", PLAIN)).hasMessage("E869: Unsupported in Jasper's Vim regex: \\%V");
        assertThatThrownBy(() -> VimRegex.compile("a\\@<=b", PLAIN)).hasMessage("E869: Unsupported in Jasper's Vim regex: \\@");
        assertThatThrownBy(() -> VimRegex.compile("a\\%23lb", PLAIN)).hasMessageStartingWith("E869: Unsupported in Jasper's Vim regex: \\%23l");
        assertThatThrownBy(() -> VimRegex.compile("\\(a", PLAIN)).hasMessage("E54: Unmatched \\(");
        assertThatThrownBy(() -> VimRegex.compile("a**", PLAIN)).hasMessage("E61: Nested *");
        assertThatThrownBy(() -> VimRegex.compile("\\+", PLAIN)).hasMessage("E64: \\+ follows nothing");
        assertThatThrownBy(() -> VimRegex.compile("\\1", PLAIN)).hasMessage("E65: Illegal back reference");
        assertThatThrownBy(() -> VimRegex.compile("[[=a=]]", PLAIN)).hasMessageStartingWith("E869");
    }
}
