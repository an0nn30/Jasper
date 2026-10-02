package dev.jasper.app.persistence;

import dev.jasper.app.dock.Slot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class UiStateTest {
    @TempDir Path dir;

    @Test void roundTripsTheDockPanelsWindowsAndTheRail() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        UiState state = UiState.load(file);
        assertThat(state.railVisible()).isTrue();
        assertThat(state.dock()).isEqualTo(UiState.Dock.DEFAULT);
        assertThat(state.panel("dev.x.hosts")).isEmpty();
        state.putPanel("dev.x.hosts", new UiState.Panel(Slot.RIGHT_TOP, 0, true));
        state.putPanel("dev.x.files", new UiState.Panel(Slot.LEFT_BOTTOM, 1, false));
        state.setDock(new UiState.Dock(300, 280, 180, 0.25, 0.5, 0.75));
        state.putWindow("dev.x.manager", new UiState.Bounds(-20, 40, 800, 600));
        state.setRailVisible(false);
        state.save();
        assertThat(Files.readString(file)).isEqualTo("""
            version = 2
            rail_visible = false

            [dock]
            left_width = 300
            right_width = 280
            bottom_height = 180
            left_ratio = 0.25
            right_ratio = 0.5
            bottom_ratio = 0.75

            [panels."dev.x.files"]
            slot = "LEFT_BOTTOM"
            order = 1
            visible = false

            [panels."dev.x.hosts"]
            slot = "RIGHT_TOP"
            order = 0
            visible = true

            [windows."dev.x.manager"]
            x = -20
            y = 40
            width = 800
            height = 600
            """);
        UiState again = UiState.load(file);
        assertThat(again.railVisible()).isFalse();
        assertThat(again.dock()).isEqualTo(new UiState.Dock(300, 280, 180, 0.25, 0.5, 0.75));
        assertThat(again.panel("dev.x.hosts")).hasValue(new UiState.Panel(Slot.RIGHT_TOP, 0, true));
        assertThat(again.panels()).containsOnlyKeys("dev.x.files", "dev.x.hosts");
        assertThat(again.window("dev.x.manager")).hasValue(new UiState.Bounds(-20, 40, 800, 600));
    }

    @Test void aVersionOneFileMigratesRegionsSizesAndVisibilityAndKeepsTheOtherSections() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, """
            version = 1
            rail_visible = true

            [panels."dev.a.sessions"]
            region = "LEFT"
            visible = true
            size = 300

            [panels."dev.b.tunnels"]
            region = "LEFT"
            visible = false
            size = 250

            [panels."dev.c.files"]
            region = "RIGHT"
            visible = false
            size = 50

            [panels."dev.d.log"]
            region = "BOTTOM"
            visible = true
            size = 150

            [panels."dev.e.other"]
            region = "BOTTOM"
            visible = true
            size = 400

            [panels."dev.f.odd"]
            region = "TOP"
            visible = true
            size = 300

            [windows."dev.x.manager"]
            x = 10
            y = 20
            width = 640
            height = 480

            [chooser]
            recent = ["/work"]
            pinned = []
            preview = true
            show_hidden = false
            """);
        UiState state = UiState.load(file);
        assertThat(state.panel("dev.a.sessions")).hasValue(new UiState.Panel(Slot.LEFT_TOP, UiState.Panel.LAST, true));
        assertThat(state.panel("dev.b.tunnels")).hasValue(new UiState.Panel(Slot.LEFT_TOP, UiState.Panel.LAST, false));
        assertThat(state.panel("dev.c.files")).hasValue(new UiState.Panel(Slot.RIGHT_TOP, UiState.Panel.LAST, false));
        assertThat(state.panel("dev.d.log")).hasValue(new UiState.Panel(Slot.BOTTOM_LEFT, UiState.Panel.LAST, true));
        assertThat(state.panel("dev.e.other")).as("the first visible panel of a region wins")
            .hasValue(new UiState.Panel(Slot.BOTTOM_LEFT, UiState.Panel.LAST, false));
        assertThat(state.panel("dev.f.odd")).as("an unknown region drops that entry only").isEmpty();
        assertThat(state.dock()).as("sizes come from the visible panel, else the first, clamped to the limits")
            .isEqualTo(new UiState.Dock(300, 120, 150, 0.5, 0.5, 0.5));
        assertThat(state.window("dev.x.manager")).hasValue(new UiState.Bounds(10, 20, 640, 480));
        assertThat(state.chooser()).isEqualTo(new UiState.Chooser(List.of("/work"), List.of(), true, false));

        state.save();
        assertThat(Files.readString(file)).startsWith("version = 2\n").contains("[windows.\"dev.x.manager\"]", "[chooser]")
            .doesNotContain("region", "size =");
        assertThat(UiState.load(file).panel("dev.a.sessions")).hasValue(new UiState.Panel(Slot.LEFT_TOP, UiState.Panel.LAST, true));
    }

    @Test void aBadVersionTwoEntryOrKeyFallsBackAlone() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, """
            version = 2
            rail_visible = false

            [dock]
            left_width = 5
            right_width = 300
            bottom_height = "tall"
            left_ratio = 0.95
            right_ratio = 0.3
            bottom_ratio = 1

            [panels."dev.x.good"]
            slot = "BOTTOM_RIGHT"
            order = 2
            visible = true

            [panels."dev.x.unknown"]
            slot = "MIDDLE"
            order = 0
            visible = true

            [panels."dev.x.disordered"]
            slot = "LEFT_TOP"
            order = -3
            visible = "yes"
            """);
        UiState state = UiState.load(file);
        assertThat(state.railVisible()).isFalse();
        assertThat(state.dock()).isEqualTo(new UiState.Dock(260, 300, 200, 0.5, 0.3, 0.5));
        assertThat(state.panel("dev.x.good")).hasValue(new UiState.Panel(Slot.BOTTOM_RIGHT, 2, true));
        assertThat(state.panel("dev.x.unknown")).isEmpty();
        assertThat(state.panel("dev.x.disordered")).hasValue(new UiState.Panel(Slot.LEFT_TOP, UiState.Panel.LAST, false));
    }

    @Test void invalidValuesAreRejectedAndInvalidFilesFallBackToDefaults() throws Exception {
        assertThatNullPointerException().isThrownBy(() -> new UiState.Panel(null, 0, true));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Panel(Slot.LEFT_TOP, -1, true));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Panel(Slot.LEFT_TOP, UiState.Panel.LAST + 1, true));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Dock(100, 260, 200, 0.5, 0.5, 0.5));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Dock(260, 260, 200, 0.5, Double.NaN, 0.5));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Bounds(0, 0, 0, 10));
        Path file = dir.resolve("ui-state.toml");
        for (String bad : new String[]{"not toml = = =", "version = 3\n", "rail_visible = false\n",
                "version = 2\nrail_visible = false\n[windows.\"a.b\"]\nx = \"left\"\n"}) {
            Files.writeString(file, bad);
            UiState state = UiState.load(file);
            assertThat(state.railVisible()).as(bad).isTrue();
            assertThat(state.dock()).as(bad).isEqualTo(UiState.Dock.DEFAULT);
            assertThat(state.panels()).as(bad).isEmpty();
        }
    }

    @Test void inMemoryStateNeverTouchesDisk() {
        UiState state = UiState.inMemory();
        state.putPanel("dev.x.hosts", new UiState.Panel(Slot.LEFT_TOP, 0, false));
        assertThatCode(state::save).doesNotThrowAnyException();
        assertThat(state.panel("dev.x.hosts")).isPresent();
    }

    @Test void remembersTheFileChoosersFoldersAndToggles() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        UiState state = UiState.load(file);
        assertThat(state.chooser()).isEqualTo(UiState.Chooser.DEFAULT);
        state.setChooser(new UiState.Chooser(List.of("/work/a \"quoted\"", "C:\\Users\\me"), List.of("/pinned\ttab"), true, false));
        state.save();
        assertThat(Files.readString(file)).contains("[chooser]",
            "recent = [\"/work/a \\\"quoted\\\"\", \"C:\\\\Users\\\\me\"]", "pinned = [\"/pinned\\u0009tab\"]",
            "preview = true", "show_hidden = false");
        assertThat(UiState.load(file).chooser()).isEqualTo(state.chooser());
    }

    @Test void unreadableChooserValuesFallBackOneKeyAtATime() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, "version = 2\n[chooser]\nrecent = [1, \"/ok\"]\npinned = \"not a list\"\npreview = \"yes\"\n");
        assertThat(UiState.load(file).chooser()).isEqualTo(new UiState.Chooser(List.of("/ok"), List.of(), false, true));
    }
}
