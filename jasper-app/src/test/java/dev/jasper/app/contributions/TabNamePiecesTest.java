package dev.jasper.app.contributions;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tab names spec 6: the pieces plugins add, and their values per tab. */
class TabNamePiecesTest {
    private final TabNamePieces pieces = new TabNamePieces();
    private final UUID tab = UUID.randomUUID(), other = UUID.randomUUID();

    @Test void aPieceHoldsOneCleanedValuePerTab() {
        pieces.add("host", "the SSH host", "dev.jasper.remote");
        pieces.set("host", tab, " db1\n.example ");
        pieces.set("host", other, "db2");
        assertThat(pieces.values(tab)).isEqualTo(Map.of("host", "db1 .example"));
        pieces.set("host", tab, "  ");
        assertThat(pieces.values(tab)).as("blank clears").isEmpty();
        pieces.set("branch", tab, "main");
        assertThat(pieces.values(tab)).as("a piece nobody added").isEmpty();
        pieces.forget(other);
        assertThat(pieces.values(other)).isEmpty();
    }

    @Test void namesAreCheckedAndHeldByOneOwner() {
        assertThatIllegalArgumentException().isThrownBy(() -> pieces.add("dir", "x", "a"));
        assertThatIllegalArgumentException().isThrownBy(() -> pieces.add("Host", "x", "a"));
        var held = pieces.add("host", "x", "a");
        assertThatIllegalStateException().isThrownBy(() -> pieces.add("host", "y", "b"));
        held.close();
        held.close();
        pieces.add("host", "y", "b");
        assertThat(pieces.pieces()).containsExactly(new TabNamePieces.Piece("host", "y", "b"));
    }

    @Test void closingAPieceDropsItsValuesAndListenersHearEachChange() {
        var heard = new ArrayList<Optional<UUID>>();
        pieces.onChanged(heard::add);
        var host = pieces.add("host", "x", "a");
        pieces.set("host", tab, "db1");
        pieces.set("host", tab, "db1");
        host.close();
        assertThat(pieces.values(tab)).isEmpty();
        assertThat(heard).as("added, set once, closed").containsExactly(Optional.empty(), Optional.of(tab), Optional.empty());
    }
}
