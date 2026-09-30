package dev.jasper.app.files;

import dev.jasper.app.persistence.UiState;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ChooserPrefsTest {
    private final UiState state = UiState.inMemory();
    private final ChooserPrefs prefs = new ChooserPrefs(state);
    private final Path root = Path.of("/").toAbsolutePath();

    @Test void recentKeepsTheNewestTenWithoutDuplicates() {
        for (int i = 0; i < 12; i++) prefs.recordRecent(root.resolve("f" + i));
        prefs.recordRecent(root.resolve("f5"));
        assertThat(prefs.recent()).hasSize(10).doesNotHaveDuplicates().doesNotContain(root.resolve("f0"), root.resolve("f1"));
        assertThat(prefs.recent().getFirst()).isEqualTo(root.resolve("f5"));
    }

    @Test void pinsAndTogglesAreStoredInTheUiState() {
        prefs.pin(root.resolve("work"));
        prefs.pin(root.resolve("work"));
        assertThat(prefs.pinned()).containsExactly(root.resolve("work"));
        assertThat(prefs.isPinned(root.resolve("work/../work"))).isTrue();
        prefs.unpin(root.resolve("work"));
        assertThat(prefs.pinned()).isEmpty();
        prefs.setPreview(true);
        prefs.setShowHidden(false);
        assertThat(state.chooser()).isEqualTo(new UiState.Chooser(List.of(), List.of(), true, false));
        assertThat(prefs.preview()).isTrue();
        assertThat(prefs.showHidden()).isFalse();
    }

    @Test void ignoresRelativeStoredPaths() {
        state.setChooser(new UiState.Chooser(List.of("relative", root.resolve("ok").toString()), List.of(), false, true));
        assertThat(prefs.recent()).containsExactly(root.resolve("ok"));
    }
}
