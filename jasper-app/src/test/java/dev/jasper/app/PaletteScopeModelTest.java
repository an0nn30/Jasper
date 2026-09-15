package dev.jasper.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PaletteScopeModelTest {
    static PaletteScope scope(String id, String... aliases) {
        return new PaletteScope() {
            @Override public String id() { return id; }
            @Override public String label() { return id; }
            @Override public String placeholder() { return "Search " + id; }
            @Override public List<String> aliases() { return List.of(aliases); }
            @Override public List<PaletteVerb> verbs() { return List.of(new PaletteVerb("run", "Run")); }
            @Override public PaletteResults search(String query, PaletteContext context) { return PaletteResults.none(); }
            @Override public void execute(PaletteRow row, PaletteVerb verb, PaletteContext context) {}
            @Override public CommandRegistry.Subscription onChanged(Runnable listener) {
                return new CommandRegistry.Subscription(() -> {});
            }
        };
    }

    @Test void rowsNormalizeBlankOptionalFieldsAndRejectMissingIdentity() {
        var row = new PaletteRow("a", "Alpha", " ", "", null, true, "token");
        assertThat(row.detail()).isNull();
        assertThat(row.tag()).isNull();
        assertThat(row.token()).isEqualTo("token");
        assertThat(PaletteRow.of("b", "Beta").enabled()).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> PaletteRow.of(" ", "x"));
        assertThatIllegalArgumentException().isThrownBy(() -> PaletteRow.of("x", ""));
        assertThatIllegalArgumentException().isThrownBy(() -> new PaletteVerb("Run", "Run"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PaletteVerb("run", " "));
    }

    @Test void resultsSelectTheFirstRowByDefaultAndCapTheList() {
        var rows = List.of(PaletteRow.of("a", "A"), PaletteRow.of("b", "B"));
        assertThat(new PaletteResults(rows, " ", null).initialSelectionId()).isEqualTo("a");
        assertThat(new PaletteResults(rows, " ", null).sectionLabel()).isNull();
        assertThat(new PaletteResults(rows, "Recent", "b").initialSelectionId()).isEqualTo("b");
        assertThat(PaletteResults.none().rows()).isEmpty();
        var many = new ArrayList<PaletteRow>();
        for (int i = 0; i <= PaletteResults.MAX_ROWS; i++) many.add(PaletteRow.of("r" + i, "Row " + i));
        assertThatIllegalArgumentException().isThrownBy(() -> new PaletteResults(many, null, null));
    }

    @Test void registryRejectsDuplicatesAndRemovesOnlyItsOwnRegistration() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var registry = new ScopeRegistry()) {
                var changes = new ArrayList<String>();
                registry.onChanged(() -> changes.add("changed"));
                var first = scope("test.one", "uno");
                var registration = registry.register(first);
                assertThatIllegalArgumentException().isThrownBy(() -> registry.register(scope("test.one")));
                assertThatIllegalArgumentException().isThrownBy(() -> registry.register(scope("Bad Id")));
                registry.register(scope("test.two"));
                assertThat(registry.scopes()).extracting(PaletteScope::id).containsExactly("test.one", "test.two");
                assertThat(registry.find("test.one")).contains(first);
                assertThat(registry.contains(first)).isTrue();
                registration.close(); registration.close();
                assertThat(registry.find("test.one")).isEmpty();
                assertThat(registry.contains(first)).isFalse();
                assertThat(registry.scopes()).extracting(PaletteScope::id).containsExactly("test.two");
                assertThat(changes).hasSize(3);
            }
        });
    }

    @Test void targetOfNothingIsInertAndNotLive() {
        var none = PaletteTarget.none();
        none.paste().accept("ignored"); none.sendReturn().run();
        assertThat(none.workingDirectory().get()).isEqualTo(Optional.empty());
        assertThat(none.shellName().get()).isEmpty();
        assertThat(none.live().getAsBoolean()).isFalse();
        assertThat(new PaletteContext(true, none).macOs()).isTrue();
    }
}
