package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.testsupport.KitFixtures;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Spec 16.1.1: every face's declared (slot, against, guard) table, frozen; a face change edits face-pairs.tsv with it. */
class PaintSpecTest {
    static List<String> rows() {
        var rows = new ArrayList<String>();
        for (var kit : List.of(Kit.DEFAULT, Kit.CLASSIC))
            for (var face : KitFaces.of(kit).all()) for (var pair : face.pairs())
                rows.add(String.join("\t", kit.id(), face.getClass().getSimpleName(), pair.slot().key(), pair.against().key(),
                    String.format(Locale.ROOT, "%s:%s", pair.guard().kind(), pair.guard().floor())));
        return rows;
    }

    @Test void theDeclaredPairsAreFrozen() throws Exception {
        var frozen = new ArrayList<String>();
        try (var in = PaintSpecTest.class.getResourceAsStream("face-pairs.tsv")) {
            for (var line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
                if (!line.isBlank() && !line.startsWith("#")) frozen.add(line);
        }
        assertThat(rows()).containsExactlyElementsOf(frozen);
    }

    @Test void everyPairStandsOnAnotherRoleAndNoFaceDeclaresOneTwice() {
        for (var kit : List.of(Kit.DEFAULT, Kit.CLASSIC, KitFixtures.enterprise(), KitFixtures.complement()))
            for (var face : KitFaces.of(kit).all()) {
                var seen = new HashSet<>();
                for (var pair : face.pairs()) {
                    assertThat(pair.slot()).as("%s %s", kit.id(), face).isNotEqualTo(pair.against());
                    assertThat(seen.add(pair)).as("%s %s declares %s twice", kit.id(), face, pair).isTrue();
                }
            }
    }

    @Test void everyClassicFaceDeclaresWhatItPaints() {
        for (var face : KitFaces.of(Kit.CLASSIC).all())
            assertThat(face.pairs()).as(face.getClass().getSimpleName()).isNotEmpty();
    }
}
