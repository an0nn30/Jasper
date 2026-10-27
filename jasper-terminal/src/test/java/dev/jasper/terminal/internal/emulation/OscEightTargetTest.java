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
