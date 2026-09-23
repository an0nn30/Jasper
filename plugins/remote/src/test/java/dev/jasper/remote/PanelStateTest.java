package dev.jasper.remote;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
class PanelStateTest {
    @Test void renamedDefaultGroupSurvivesCollapseUpdatesAndRestart(@TempDir Path dir) throws Exception {
        var path = dir.resolve("panel-state.toml");
        var state = new PanelState(path);
        state.renameDefaultGroup("My machines");
        state.save(Set.of("My machines"));
        var reloaded = new PanelState(path);
        assertThat(reloaded.defaultGroup()).isEqualTo("My machines");
        assertThat(reloaded.collapsed()).containsExactly("My machines");
        assertThatIllegalArgumentException().isThrownBy(() -> reloaded.renameDefaultGroup("  "));
    }
}
