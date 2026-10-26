package dev.jasper.sdk.terminal;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class TabNamePieceSpecTest {
    @Test void aPieceIsNamedAsUsersTypeItAndDescribedOnOneLine() {
        var host = new TabNamePieceSpec("host", "the SSH host this tab is connected to");
        assertThat(host.name()).isEqualTo("host");
        assertThat(new TabNamePieceSpec("git_branch2", "the branch").name()).isEqualTo("git_branch2");
        assertThat(new TabNamePieceSpec("a".repeat(32), "x").name()).hasSize(32);
        for (String bad : List.of("", "Host", "1st", "git-branch", "two words", "a".repeat(33)))
            assertThatIllegalArgumentException().as(bad).isThrownBy(() -> new TabNamePieceSpec(bad, "x"));
        assertThatIllegalArgumentException().isThrownBy(() -> new TabNamePieceSpec("host", " "));
        assertThatIllegalArgumentException().isThrownBy(() -> new TabNamePieceSpec("host", "two\nlines"));
        assertThatIllegalArgumentException().as("a line separator").isThrownBy(() -> new TabNamePieceSpec("host", "two\u2028lines"));
        assertThatIllegalArgumentException().as("a paragraph separator").isThrownBy(() -> new TabNamePieceSpec("host", "two\u2029lines"));
        assertThatIllegalArgumentException().as("a bidirectional override").isThrownBy(() -> new TabNamePieceSpec("host", "the \u202Ehost"))
            .withMessageContaining("one nonblank line");
        assertThatIllegalArgumentException().as("a bidirectional isolate").isThrownBy(() -> new TabNamePieceSpec("host", "the \u2066host\u2069"));
        assertThatNullPointerException().isThrownBy(() -> new TabNamePieceSpec(null, "x"));
    }

    @Test void jaspersOwnPiecesAreReserved() {
        assertThat(TabNamePieceSpec.BUILT_IN).containsExactlyInAnyOrder("dir", "full", "process", "title");
        for (String name : TabNamePieceSpec.BUILT_IN)
            assertThatIllegalArgumentException().as(name).isThrownBy(() -> new TabNamePieceSpec(name, "x"))
                .withMessageContaining("Jasper's own");
    }
}
