package dev.jasper.app.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class UiStateTest {
    @TempDir Path dir;

    @Test void roundTripsPanelsWindowsAndTheRail() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        UiState state = UiState.load(file);
        assertThat(state.railVisible()).isTrue();
        assertThat(state.panel("dev.x.hosts")).isEmpty();
        state.putPanel("dev.x.hosts", new UiState.Panel("RIGHT", true, 300));
        state.putWindow("dev.x.manager", new UiState.Bounds(-20, 40, 800, 600));
        state.setRailVisible(false);
        state.save();
        assertThat(Files.readString(file)).isEqualTo("""
            version = 1
            rail_visible = false

            [panels."dev.x.hosts"]
            region = "RIGHT"
            visible = true
            size = 300

            [windows."dev.x.manager"]
            x = -20
            y = 40
            width = 800
            height = 600
            """);
        UiState again = UiState.load(file);
        assertThat(again.railVisible()).isFalse();
        assertThat(again.panel("dev.x.hosts")).hasValue(new UiState.Panel("RIGHT", true, 300));
        assertThat(again.window("dev.x.manager")).hasValue(new UiState.Bounds(-20, 40, 800, 600));
    }

    @Test void invalidValuesAreRejectedAndInvalidFilesFallBackToDefaults() throws Exception {
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Panel("TOP", true, 300));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Panel("LEFT", true, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Bounds(0, 0, 0, 10));
        Path file = dir.resolve("ui-state.toml");
        for (String bad : new String[]{"not toml = = =", "version = 2\n", "version = 1\n[panels.\"a.b\"]\nregion = \"TOP\"\nvisible = true\nsize = 300\n"}) {
            Files.writeString(file, bad);
            UiState state = UiState.load(file);
            assertThat(state.railVisible()).as(bad).isTrue();
            assertThat(state.panel("a.b")).as(bad).isEmpty();
        }
    }

    @Test void inMemoryStateNeverTouchesDisk() {
        UiState state = UiState.inMemory();
        state.putPanel("dev.x.hosts", new UiState.Panel("LEFT", false, 200));
        assertThatCode(state::save).doesNotThrowAnyException();
        assertThat(state.panel("dev.x.hosts")).isPresent();
    }
}
