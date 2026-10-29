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

    private Location at(String name) { return Location.local(root.resolve(name)); }

    @Test void recentKeepsTheNewestTenWithoutDuplicates() {
        for (int i = 0; i < 12; i++) prefs.recordRecent(at("f" + i));
        prefs.recordRecent(at("f5"));
        assertThat(prefs.recent()).hasSize(10).doesNotHaveDuplicates().doesNotContain(at("f0"), at("f1"));
        assertThat(prefs.recent().getFirst()).isEqualTo(at("f5"));
    }

    @Test void pinsTogglesAndRemoteFoldersAreStoredInTheUiState() {
        var remote = new Location("dev.x.hosts", "box", "/srv");
        prefs.pin(at("work"));
        prefs.pin(at("work"));
        prefs.pin(remote);
        assertThat(prefs.pinned()).containsExactly(at("work"), remote);
        assertThat(prefs.isPinned(remote)).isTrue();
        prefs.unpin(at("work"));
        assertThat(prefs.pinned()).containsExactly(remote);
        prefs.setPreview(true);
        prefs.setShowHidden(false);
        assertThat(state.chooser()).isEqualTo(new UiState.Chooser(List.of(), List.of("@dev.x.hosts|box|/srv"), true, false, "name", true, java.util.Map.of()));
    }

    @Test void ignoresUnreadableStoredPaths() {
        state.setChooser(new UiState.Chooser(List.of("relative", "@broken", root.resolve("ok").toString()), List.of(), false, true, "name", true, java.util.Map.of()));
        assertThat(prefs.recent()).containsExactly(at("ok"));
    }
}
