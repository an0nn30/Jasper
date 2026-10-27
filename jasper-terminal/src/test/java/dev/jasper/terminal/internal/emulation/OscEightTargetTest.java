package dev.jasper.terminal.internal.emulation;

import dev.jasper.terminal.internal.text.LinkHit;
import dev.jasper.terminal.testsupport.Await;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** An OSC 8 link keeps the {@code target} its program named, and only its own: Review Focus 2 of browser plan 3b. */
class OscEightTargetTest {
    @Test
    void aLinkKeepsItsTargetAndTheNextLinkWithoutOneHasNone() throws Exception {
        try (EmulationFixture fixture = EmulationFixture.open(20, 4, 100)) {
            fixture.feed("\033]8;target=docs;https://a.example\007A\033]8;;\007 \033]8;;https://b.example\007B\033]8;;\007");
            Await.until(() -> "A B".equals(fixture.access().snapshot().lineText(0)), "linked text");

            assertThat(fixture.access().linkAt(0, 0)).contains(new LinkHit("https://a.example", Optional.of("docs")));
            assertThat(fixture.access().linkAt(0, 2)).contains(new LinkHit("https://b.example", Optional.empty()));
        }
    }

    @Test
    void aLinkWithADifferentTargetKeepsItsOwn() throws Exception {
        try (EmulationFixture fixture = EmulationFixture.open(20, 4, 100)) {
            fixture.feed("\033]8;target=one;https://a.example\007A\033]8;;\007 \033]8;target=two;https://b.example\007B\033]8;;\007");
            Await.until(() -> "A B".equals(fixture.access().snapshot().lineText(0)), "linked text");

            assertThat(fixture.access().linkAt(0, 0)).contains(new LinkHit("https://a.example", Optional.of("one")));
            assertThat(fixture.access().linkAt(0, 2)).contains(new LinkHit("https://b.example", Optional.of("two")));
        }
    }

    /**
     * JediTerm ends an OSC at the C1 string terminator U+009C, so an OSC 8 ended there opens no link; nor does one with
     * U+009C in its parameters. Neither target may reach a later link JediTerm reads without the shell filter: an
     * {@code OSC 08} or a C1-introduced (U+009D) OSC 8, even one that follows the U+009C before the next BEL.
     */
    @Test
    void anOsc8EndedByTheC1StringTerminatorLeavesNoTargetForALaterLink() throws Exception {
        String st = String.valueOf((char) 0x9C);
        String osc = String.valueOf((char) 0x9D);
        try (EmulationFixture fixture = EmulationFixture.open(40, 6, 100)) {
            fixture.feed("\033]8;target=evil;" + st + "\007\033]08;;https://v.example\007V\033]8;;\007\r\n"
                + "\033]8;target=evil;" + st + "\007" + osc + "8;;https://w.example\007W\033]8;;\007\r\n"
                + "\033]8;id=" + st + ":target=evil;https://x.example\007\r\n"
                + "\033]08;;https://y.example\007Y\033]8;;\007\r\n"
                + "\033]8;target=evil;" + st + osc + "8;;https://z.example\007Z\033]8;;\007");
            Await.until(() -> "Z".equals(fixture.access().snapshot().lineText(4)), "linked text");

            assertThat(fixture.access().linkAt(0, 0)).contains(new LinkHit("https://v.example", Optional.empty()));
            assertThat(fixture.access().linkAt(1, 0)).contains(new LinkHit("https://w.example", Optional.empty()));
            assertThat(fixture.access().linkAt(3, 0)).contains(new LinkHit("https://y.example", Optional.empty()));
            assertThat(fixture.access().linkAt(4, 0)).contains(new LinkHit("https://z.example", Optional.empty()));
        }
    }

    /**
     * A program can send the shell filter's command itself. The engine refuses a name the filter would refuse, and the
     * filter's own command replaces any name before an OSC 8 it rewrites.
     */
    @Test
    void aTargetAProgramSendsItselfIsCheckedAndReplaced() throws Exception {
        try (EmulationFixture fixture = EmulationFixture.open(20, 6, 100)) {
            fixture.feed("\033]1341;jasper;link-target;" + "x".repeat(257) + "\007\033]08;;https://a.example\007A\033]8;;\007\r\n"
                + "\033]1341;jasper;link-target;a;b\007\033]08;;https://b.example\007B\033]8;;\007\r\n"
                + "\033]1341;jasper;link-target;mine\007\033]8;;https://c.example\007C\033]8;;\007");
            Await.until(() -> "C".equals(fixture.access().snapshot().lineText(2)), "linked text");

            assertThat(fixture.access().linkAt(0, 0)).contains(new LinkHit("https://a.example", Optional.empty()));
            assertThat(fixture.access().linkAt(1, 0)).contains(new LinkHit("https://b.example", Optional.empty()));
            assertThat(fixture.access().linkAt(2, 0)).contains(new LinkHit("https://c.example", Optional.empty()));
        }
    }

    @Test
    void aUrlWrittenAfterALinkWithATargetHasNone() throws Exception {
        try (EmulationFixture fixture = EmulationFixture.open(40, 4, 100)) {
            fixture.feed("\033]8;target=docs;https://a.example\007A\033]8;;\007 https://c.example");
            Await.until(() -> "A https://c.example".equals(fixture.access().snapshot().lineText(0)), "linked text");

            assertThat(fixture.access().linkAt(0, 0)).contains(new LinkHit("https://a.example", Optional.of("docs")));
            assertThat(fixture.access().linkAt(0, 5)).contains(new LinkHit("https://c.example", Optional.empty()));
        }
    }
}
