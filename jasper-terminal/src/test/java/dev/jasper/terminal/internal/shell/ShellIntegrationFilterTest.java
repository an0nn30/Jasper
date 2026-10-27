package dev.jasper.terminal.internal.shell;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShellIntegrationFilterTest {
    private static final String P = ShellIntegrationFilter.PREFIX;

    @Test
    void plainTextPassesThrough() {
        assertThat(run("hello\r\nworld")).isEqualTo("hello\r\nworld");
    }

    @Test
    void osc7WithBellBecomesACwdCommand() {
        assertThat(run("a\033]7;file://host/tmp\007b")).isEqualTo("a" + P + "cwd;file://host/tmp\007b");
    }

    @Test
    void osc7WithStringTerminatorBecomesACwdCommand() {
        assertThat(run("\033]7;file:///x\033\\")).isEqualTo(P + "cwd;file:///x\007");
    }

    @Test
    void osc133BecomesAMarkCommand() {
        assertThat(run("\033]133;A\007$ \033]133;D;0\007"))
            .isEqualTo(P + "mark;A\007$ " + P + "mark;D;0\007");
    }

    @Test
    void otherOscSequencesPassUnchanged() {
        String input = "\033]0;title\007\033]1341;other\007\033]13;x\007\033]80;x\007";
        assertThat(run(input)).isEqualTo(input);
    }

    /** JediTerm's hyperlink filter sees only an OSC 8's URI, so the link's {@code target} parameter goes ahead of it. */
    @Test
    void anOsc8LinkIsPrecededByItsTarget() {
        String input = "\033]8;id=1:target=docs;https://e.com\033\\";
        String expected = "\033]1341;jasper;link-target;docs\007\033]8;id=1:target=docs;https://e.com\033\\";

        assertThat(run(input)).isEqualTo(expected);
        assertThat(runOneCharAtATime(input)).isEqualTo(expected);
    }

    @Test
    void anOsc8LinkWithoutATargetClearsThePendingOne() {
        String input = "\033]8;;https://e.com\007";
        String expected = "\033]1341;jasper;link-target;\007\033]8;;https://e.com\007";

        assertThat(run(input)).isEqualTo(expected);
        assertThat(runOneCharAtATime(input)).isEqualTo(expected);
    }

    @Test
    void aTargetWithAControlCharacterOrOver256CharactersIsDropped() {
        assertThat(run("\033]8;target=" + "x".repeat(257) + ";https://e.com\007")).startsWith("\033]1341;jasper;link-target;\007");
        assertThat(run("\033]8;target=a\001b;https://e.com\007")).startsWith("\033]1341;jasper;link-target;\007");
    }

    @Test
    void aTargetOf256CharactersIsKept() {
        String target = "x".repeat(256);
        assertThat(run("\033]8;target=" + target + ";https://e.com\007")).startsWith(P + "link-target;" + target + "\007\033]8;");
    }

    @Test
    void anOsc8ThatEndsALinkIsPrecededByAnEmptyTarget() {
        String input = "\033]8;target=docs;https://e.com\007a\033]8;target=docs;\007b";
        String expected = P + "link-target;docs\007\033]8;target=docs;https://e.com\007a"
            + P + "link-target;\007\033]8;target=docs;\007b";

        assertThat(run(input)).isEqualTo(expected);
        assertThat(runOneCharAtATime(input)).isEqualTo(expected);
    }

    @Test
    void osc8LinksSplitAcrossReadsKeepTheirOwnTargets() {
        String input = "x\033]8;target=one;https://a.example\033\\A\033]8;;\033\\ \033]8;target=two;https://b.example\007B\033]8;;\007y";
        String expected = "x" + P + "link-target;one\007\033]8;target=one;https://a.example\033\\A"
            + P + "link-target;\007\033]8;;\033\\ "
            + P + "link-target;two\007\033]8;target=two;https://b.example\007B"
            + P + "link-target;\007\033]8;;\007y";

        assertThat(run(input)).isEqualTo(expected);
        assertThat(runOneCharAtATime(input)).isEqualTo(expected);
    }

    @Test
    void anOverlongOsc8PassesUnchangedAfterAnEmptyTarget() {
        String input = "\033]8;target=docs;https://e.com/" + "x".repeat(5000) + "\007";
        assertThat(run(input)).isEqualTo(P + "link-target;\007" + input);
    }

    @Test
    void anAbandonedOsc8PassesUnchangedAfterAnEmptyTarget() {
        String input = "\033]8;target=docs;https://e.com\033[0mc";
        assertThat(run(input)).isEqualTo(P + "link-target;\007" + input);
    }

    @Test
    void cursorStyleResetIsFollowedByACursorResetCommand() {
        assertThat(run("\033[0 q")).isEqualTo("\033[0 q" + P + "cursor-reset\007");
        assertThat(run("\033[ q")).isEqualTo("\033[ q" + P + "cursor-reset\007");
    }

    @Test
    void otherCsiSequencesPassUnchanged() {
        String input = "\033[2 q\033[0m\033[1;31mX\033[?1049h";
        assertThat(run(input)).isEqualTo(input);
    }

    /**
     * Only {@code CSI ... SP q} is DECSCUSR, but jediterm-core 3.76 reads every CSI ending in {@code q}
     * as one. tmux opens a session with the XTVERSION query {@code CSI > q}, which would otherwise
     * arrive as a request for a blinking block cursor and outrank the configured shape forever.
     */
    @Test
    void csiQueriesEndingInQAreDroppedBecauseTheEmulatorMisreadsThemAsCursorRequests() {
        assertThat(run("a\033[>qb")).isEqualTo("ab");
        assertThat(run("\033[>0;1q")).isEmpty();
        assertThat(run("\033[0q")).isEmpty();
        assertThat(run("\033[?q")).isEmpty();
    }

    @Test
    void sequencesSplitAcrossReadsAreStillRewritten() {
        String input = "x\033]7;file:///tmp\033\\y\033[0 qz";
        String expected = "x" + P + "cwd;file:///tmp\007y\033[0 q" + P + "cursor-reset\007z";

        assertThat(run(input)).isEqualTo(expected);
        assertThat(runOneCharAtATime(input)).isEqualTo(expected);
    }

    @Test
    void escapeInsideOscDataThatIsNotATerminatorPassesUnchanged() {
        String input = "\033]7;ab\033[0mc";
        assertThat(run(input)).isEqualTo(input);
    }

    @Test
    void overlongOscPassesUnchanged() {
        String input = "\033]7;" + "x".repeat(5000) + "\007";
        assertThat(run(input)).isEqualTo(input);
    }

    @Test
    void doubledEscapeKeepsLookingForASequence() {
        assertThat(run("\033\033]7;x\007")).isEqualTo("\033" + P + "cwd;x\007");
    }

    @Test
    void finishReleasesAnIncompleteSequence() {
        ShellIntegrationFilter filter = new ShellIntegrationFilter();
        StringBuilder out = new StringBuilder();
        char[] input = "\033]7;file".toCharArray();

        filter.filter(input, 0, input.length, out);
        assertThat(out.toString()).isEmpty();

        filter.finish(out);
        assertThat(out.toString()).isEqualTo("\033]7;file");
    }

    private static String run(String input) {
        ShellIntegrationFilter filter = new ShellIntegrationFilter();
        StringBuilder out = new StringBuilder();
        char[] chars = input.toCharArray();
        filter.filter(chars, 0, chars.length, out);
        filter.finish(out);
        return out.toString();
    }

    private static String runOneCharAtATime(String input) {
        ShellIntegrationFilter filter = new ShellIntegrationFilter();
        StringBuilder out = new StringBuilder();
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            filter.filter(chars, i, 1, out);
        }
        filter.finish(out);
        return out.toString();
    }
}
