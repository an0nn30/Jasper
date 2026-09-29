package dev.jasper.remote;
import java.nio.file.Files;
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

    @Test void theWslGroupsCollapsedFlagIsKeptApartFromHostGroups(@TempDir Path dir) throws Exception {
        var path = dir.resolve("panel-state.toml");
        Files.writeString(path, "default_group = \"Other\"\ncollapsed = [\"wsl\", \"Production\"]\n");
        var state = new PanelState(path);
        assertThat(state.wslCollapsed()).as("a saved host group named wsl is not the WSL group").isFalse();
        assertThat(state.collapsed()).containsExactly("wsl", "Production");

        state.save(Set.of("Production"), true);
        assertThat(new PanelState(path).wslCollapsed()).isTrue();
        assertThat(new PanelState(path).collapsed()).containsExactly("Production");

        state.save(Set.of("Other"));
        assertThat(new PanelState(path).wslCollapsed()).as("saving host groups keeps the WSL flag").isTrue();
        state.renameDefaultGroup("wsl");
        assertThat(new PanelState(path).collapsed()).containsExactly("wsl");
        assertThat(new PanelState(path).wslCollapsed()).as("renaming the default group keeps the WSL flag").isTrue();

        state.save(Set.of("wsl"), false);
        assertThat(new PanelState(path).wslCollapsed()).isFalse();
        assertThat(new PanelState(path).collapsed()).as("the default group renamed to wsl stays collapsed").containsExactly("wsl");
    }
}
