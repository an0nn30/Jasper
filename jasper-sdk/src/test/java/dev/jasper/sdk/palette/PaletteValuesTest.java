package dev.jasper.sdk.palette;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.JasperSdk;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PaletteValuesTest {
    static final PaletteVerb PASTE = new PaletteVerb("paste", "Paste");

    @Test void aScopeSpecIsNamespacedHasOneToThreeDistinctVerbsAndLowercaseAliases() {
        ScopeSpec spec = ScopeSpec.of("dev.x.things", "Things", "Search things", List.of(PASTE))
            .withAliases(List.of("th", "things")).withDescription("All the things").withMonospaceRows(true)
            .withShortcutActionId("dev.x.open");
        assertThat(spec.aliases()).containsExactly("th", "things");
        assertThat(spec.shortcutActionId()).contains("dev.x.open");
        assertThat(spec.icon()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> ScopeSpec.of("things", "Things", "Search", List.of(PASTE)));
        assertThatIllegalArgumentException().isThrownBy(() -> ScopeSpec.of("dev.x.things", " ", "Search", List.of(PASTE)));
        assertThatIllegalArgumentException().isThrownBy(() -> ScopeSpec.of("dev.x.things", "Things", "Search", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> ScopeSpec.of("dev.x.things", "Things", "Search",
            List.of(PASTE, new PaletteVerb("a", "A"), new PaletteVerb("b", "B"), new PaletteVerb("c", "C"))));
        assertThatIllegalArgumentException().as("verb ids are distinct").isThrownBy(() -> ScopeSpec.of("dev.x.things", "Things", "Search",
            List.of(PASTE, new PaletteVerb("paste", "Paste again"))));
        assertThatIllegalArgumentException().isThrownBy(() -> spec.withAliases(List.of("Th")));
        assertThatIllegalArgumentException().isThrownBy(() -> new PaletteVerb("Paste", "Paste"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PaletteVerb("paste", ""));
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
        assertThat(JasperSdk.VERSION).isEqualTo("0.6.0");
    }
}
