package dev.jasper.sdk.palette;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.JasperSdk;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PaletteValuesTest {
    static final PaletteVerb PASTE = new PaletteVerb("paste", "Paste");

    @Test void aScopeSpecIsNamespacedHasOneToThreeDistinctVerbsAndTakesPartInAllUnlessItOptsOut() {
        ScopeSpec spec = ScopeSpec.of("dev.x.things", "Things", "Search things", List.of(PASTE))
            .withDescription("All the things").withMonospaceRows(true).withShortcutActionId("dev.x.open");
        assertThat(spec.inAll()).isTrue();
        assertThat(spec.withInAll(false).inAll()).isFalse();
        assertThat(spec.withInAll(false).withDescription("x").inAll()).as("derivations keep the flag").isFalse();
        assertThat(spec.inRecents()).as("offered unprompted unless it opts out").isTrue();
        assertThat(spec.withInRecents(false).inRecents()).isFalse();
        assertThat(spec.withInRecents(false).withIcon(null).withInAll(false).inRecents()).as("derivations keep the flag").isFalse();
        assertThat(new ScopeSpec("dev.x.old", "Old", "", "Search", List.of(PASTE), false, Optional.empty(), Optional.empty(), true).inRecents())
            .as("the 0.9.1 constructor still works").isTrue();
        assertThat(spec.description()).isEqualTo("All the things");
        assertThat(spec.shortcutActionId()).contains("dev.x.open");
        assertThat(spec.icon()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> ScopeSpec.of("things", "Things", "Search", List.of(PASTE)));
        assertThatIllegalArgumentException().isThrownBy(() -> ScopeSpec.of("dev.x.things", " ", "Search", List.of(PASTE)));
        assertThatIllegalArgumentException().isThrownBy(() -> ScopeSpec.of("dev.x.things", "Things", "Search", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> ScopeSpec.of("dev.x.things", "Things", "Search",
            List.of(PASTE, new PaletteVerb("a", "A"), new PaletteVerb("b", "B"), new PaletteVerb("c", "C"))));
        assertThatIllegalArgumentException().as("verb ids are distinct").isThrownBy(() -> ScopeSpec.of("dev.x.things", "Things", "Search",
            List.of(PASTE, new PaletteVerb("paste", "Paste again"))));
        assertThatIllegalArgumentException().isThrownBy(() -> new PaletteVerb("Paste", "Paste"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PaletteVerb("paste", ""));
    }

    /** SDK 0.18 (spec 8.2): a hue and an item noun for presentations that colour and label rows by scope. */
    @Test void aScopeCarriesAHueAndAnItemNounAndAtMostOneSplitVariantAfterItsFirstVerb() {
        var none = dev.jasper.sdk.ui.Nodes.Hue.NONE;
        var green = dev.jasper.sdk.ui.Nodes.Hue.GREEN;
        var split = new PaletteVerb("split", "Connect in split", PaletteVerb.Role.SPLIT);
        ScopeSpec spec = ScopeSpec.of("dev.x.hosts", "Hosts", "Search hosts", List.of(PASTE, split));
        assertThat(spec.hue()).isEqualTo(none);
        assertThat(spec.itemNoun()).isEmpty();
        var named = spec.withHue(green).withItemNoun("session").withDescription("Saved hosts").withInAll(false).withIcon(null)
            .withMonospaceRows(true).withShortcutActionId("dev.x.open").withInRecents(false);
        assertThat(named.hue()).as("derivations keep the hue").isEqualTo(green);
        assertThat(named.itemNoun()).as("and the noun").isEqualTo("session");
        assertThat(spec.withItemNoun("file server").itemNoun()).isEqualTo("file server");
        assertThat(spec.withItemNoun("  ").itemNoun()).as("blank means the label").isEmpty();
        assertThat(spec.withItemNoun(null).itemNoun()).isEmpty();
        assertThat(new ScopeSpec("dev.x.old", "Old", "", "Search", List.of(PASTE), false, Optional.empty(), Optional.empty(), true, true).hue())
            .as("the 0.9.2 constructor still works").isEqualTo(none);
        assertThat(new ScopeSpec("dev.x.old", "Old", "", "Search", List.of(PASTE), false, Optional.empty(), Optional.empty(), true).itemNoun())
            .as("and the 0.9.1 one").isEmpty();
        assertThatIllegalArgumentException().as("lowercase").isThrownBy(() -> spec.withItemNoun("Session"));
        assertThatIllegalArgumentException().as("no surrounding space").isThrownBy(() -> spec.withItemNoun(" session"));
        assertThatIllegalArgumentException().as("no control characters").isThrownBy(() -> spec.withItemNoun("ses\tsion"));
        assertThatIllegalArgumentException().as("short").isThrownBy(() -> spec.withItemNoun("s".repeat(41)));
        assertThatNullPointerException().isThrownBy(() -> spec.withHue(null));
        assertThatIllegalArgumentException().as("never the first verb")
            .isThrownBy(() -> ScopeSpec.of("dev.x.hosts", "Hosts", "Search", List.of(split, PASTE)));
        assertThatIllegalArgumentException().as("at most one").isThrownBy(() -> ScopeSpec.of("dev.x.hosts", "Hosts", "Search",
            List.of(PASTE, split, new PaletteVerb("split_down", "Split down", PaletteVerb.Role.SPLIT))));
    }

    @Test void aVerbIsStandardUnlessMarkedSplit() {
        assertThat(PASTE.role()).isEqualTo(PaletteVerb.Role.STANDARD);
        assertThat(new PaletteVerb("split", "Split", PaletteVerb.Role.SPLIT).role()).isEqualTo(PaletteVerb.Role.SPLIT);
        assertThat(new PaletteVerb("paste", "Paste")).isEqualTo(new PaletteVerb("paste", "Paste", PaletteVerb.Role.STANDARD));
        assertThatNullPointerException().isThrownBy(() -> new PaletteVerb("split", "Split", null));
    }

    @Test void rowsResultsAndStepsValidateLikeTheApplicationsOwn() {
        PaletteRow row = PaletteRow.of("r1", "Row").withDetail(" ").withTag("3 fields").withToken(42);
        assertThat(row.detail()).isEmpty();
        assertThat(row.tag()).contains("3 fields");
        assertThat(row.token()).isEqualTo(42);
        assertThat(row.enabled()).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> PaletteRow.of(" ", "Row"));
        assertThatIllegalArgumentException().isThrownBy(() -> PaletteRow.of("x".repeat(257), "Row"));
        assertThatIllegalArgumentException().isThrownBy(() -> PaletteRow.of("r1", ""));

        PaletteResults results = PaletteResults.of(List.of(row, PaletteRow.of("r2", "Other")));
        assertThat(results.initialSelectionId()).as("defaults to the first row").contains("r1");
        assertThat(results.sectionLabel()).isEmpty();
        assertThat(PaletteResults.none().rows()).isEmpty();
        assertThat(PaletteResults.none().initialSelectionId()).isEmpty();
        assertThat(new PaletteResults(List.of(row), Optional.of("Recent"), Optional.of("r1")).sectionLabel()).contains("Recent");
        List<PaletteRow> many = java.util.stream.IntStream.range(0, 201).mapToObj(i -> PaletteRow.of("r" + i, "Row " + i)).toList();
        assertThatIllegalArgumentException().isThrownBy(() -> PaletteResults.of(many));

        PaletteStep step = new PaletteStep("Name it", List.of(new PaletteStep.Field("name", "Name", null)), (values, done) -> done.accept(PaletteStep.Result.done()));
        assertThat(step.fields().getFirst().prefill()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> new PaletteStep("Name it", List.of(), (values, done) -> { }));
        assertThat(PaletteStep.Result.error("Taken").error()).contains("Taken");
        assertThat(PaletteStep.Result.reopen("dev.x.other", Optional.of("r9"), Optional.empty()).reopenScopeId()).contains("dev.x.other");
        assertThat(PaletteStep.Result.done().reopenScopeId()).isEmpty();
        assertThatNullPointerException().isThrownBy(() -> PaletteStep.Result.error(null));
    }

    @Test void aQueryHasABoundedResultCountAndTheCapabilityAndVersionExist() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PaletteQuery(null, Optional.empty(), 5, true));
        assertThat(Capabilities.PALETTE_CONTRIBUTE).isEqualTo("palette.contribute");
        assertThat(Capabilities.ALL).contains("palette.contribute");
        assertThat(JasperSdk.VERSION).isEqualTo("0.20.0");
    }
}
