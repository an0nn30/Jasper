package dev.jasper.app.persistence;

import dev.jasper.app.dock.DockState;
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
            zen_mode = false

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

    @Test void aVersionOneSideTakesItsSizeFromTheVisiblePanelNotTheFirst() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, """
            version = 1

            [panels."dev.a.first"]
            region = "LEFT"
            visible = false
            size = 200

            [panels."dev.b.second"]
            region = "LEFT"
            visible = true
            size = 400
            """);
        UiState state = UiState.load(file);
        assertThat(state.panel("dev.b.second")).hasValue(new UiState.Panel(Slot.LEFT_TOP, UiState.Panel.LAST, true));
        assertThat(state.panel("dev.a.first")).hasValue(new UiState.Panel(Slot.LEFT_TOP, UiState.Panel.LAST, false));
        assertThat(state.dock().leftWidth()).as("the panel that showed, not the first one").isEqualTo(400);
        assertThat(state.dock()).isEqualTo(new UiState.Dock(400, 260, 200, 0.5, 0.5, 0.5));
    }

    @Test void aVersionOneSideWithNoVisiblePanelTakesTheFirstPanelsSize() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, """
            version = 1

            [panels."dev.a.first"]
            region = "LEFT"
            visible = false
            size = 350

            [panels."dev.b.second"]
            region = "LEFT"
            visible = false
            size = 180
            """);
        UiState state = UiState.load(file);
        assertThat(state.dock().leftWidth()).as("none showed, so the first panel's size").isEqualTo(350);
        assertThat(state.dock()).isEqualTo(new UiState.Dock(350, 260, 200, 0.5, 0.5, 0.5));
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

    @Test void dockValuesExactlyAtTheirLimitsAreAccepted() throws Exception {
        assertThat(loadDock("left_width = 120", "right_width = 120", "bottom_height = 120",
            "left_ratio = 0.1", "right_ratio = 0.1", "bottom_ratio = 0.1")).as("every minimum")
            .isEqualTo(new UiState.Dock(120, 120, 120, 0.1, 0.1, 0.1));
        assertThat(loadDock("left_width = 4000", "right_width = 4000", "bottom_height = 4000",
            "left_ratio = 0.9", "right_ratio = 0.9", "bottom_ratio = 0.9")).as("every maximum")
            .isEqualTo(new UiState.Dock(4000, 4000, 4000, 0.9, 0.9, 0.9));
    }

    @Test void aDockValueOneStepOutsideItsLimitsFallsBackAloneAndTheOtherKeysKeepTheirValues() throws Exception {
        String[] keys = {"left_width", "right_width", "bottom_height", "left_ratio", "right_ratio", "bottom_ratio"};
        UiState.Dock defaults = UiState.Dock.DEFAULT;
        int[] sizes = {300, 310, 320}, defaultSizes = {defaults.leftWidth(), defaults.rightWidth(), defaults.bottomHeight()};
        double[] ratios = {0.2, 0.3, 0.4};
        for (int bad = 0; bad < keys.length; bad++) {
            for (Object value : bad < 3 ? new Object[]{119, 4001} : new Object[]{0.09, 0.91}) {
                String[] lines = dockLines(sizes, ratios);
                lines[bad] = keys[bad] + " = " + value;
                int[] expectedSizes = sizes.clone();
                double[] expectedRatios = ratios.clone();
                if (bad < 3) expectedSizes[bad] = defaultSizes[bad]; else expectedRatios[bad - 3] = DockState.DEFAULT_RATIO;
                assertThat(loadDock(lines)).as(lines[bad]).isEqualTo(new UiState.Dock(expectedSizes[0], expectedSizes[1], expectedSizes[2],
                    expectedRatios[0], expectedRatios[1], expectedRatios[2]));
            }
        }
    }

    @Test void aScalarWhereTheDockTableBelongsFallsBackToDefaultsAndKeepsThePanels() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, """
            version = 2
            rail_visible = false
            dock = 5

            [panels."dev.x.good"]
            slot = "RIGHT_BOTTOM"
            order = 1
            visible = true
            """);
        UiState state = UiState.load(file);
        assertThat(state.dock()).isEqualTo(UiState.Dock.DEFAULT);
        assertThat(state.panel("dev.x.good")).hasValue(new UiState.Panel(Slot.RIGHT_BOTTOM, 1, true));
        assertThat(state.railVisible()).as("the rest of the file is read").isFalse();
    }

    @Test void aScalarWhereThePanelsTableBelongsLeavesNoPanelsAndKeepsTheDock() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, """
            version = 2
            panels = "x"

            [dock]
            left_width = 300
            right_width = 310
            bottom_height = 320
            left_ratio = 0.2
            right_ratio = 0.3
            bottom_ratio = 0.4
            """);
        UiState state = UiState.load(file);
        assertThat(state.panels()).isEmpty();
        assertThat(state.dock()).isEqualTo(new UiState.Dock(300, 310, 320, 0.2, 0.3, 0.4));
    }

    @Test void aPanelEntryThatIsAScalarIsSkippedAndTheOthersSurvive() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, """
            version = 2

            [panels]
            "dev.x.scalar" = 3

            [panels."dev.x.good"]
            slot = "BOTTOM_RIGHT"
            order = 2
            visible = true

            [panels."dev.x.other"]
            slot = "LEFT_BOTTOM"
            order = 0
            visible = false
            """);
        UiState state = UiState.load(file);
        assertThat(state.panel("dev.x.scalar")).isEmpty();
        assertThat(state.panels()).containsOnlyKeys("dev.x.good", "dev.x.other");
        assertThat(state.panel("dev.x.good")).hasValue(new UiState.Panel(Slot.BOTTOM_RIGHT, 2, true));
        assertThat(state.panel("dev.x.other")).hasValue(new UiState.Panel(Slot.LEFT_BOTTOM, 0, false));
    }

    @Test void aBadWindowEntryFallsBackAlone() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, """
            version = 2
            rail_visible = false

            [dock]
            left_width = 300
            right_width = 260
            bottom_height = 240
            left_ratio = 0.4
            right_ratio = 0.5
            bottom_ratio = 0.5

            [panels."dev.x.hosts"]
            slot = "RIGHT_BOTTOM"
            order = 1
            visible = true

            [windows."dev.x.flat"]
            x = 10
            y = 20
            width = 0
            height = 480

            [windows."dev.x.lost"]
            x = "left"
            y = 20
            width = 640
            height = 480

            [windows."dev.x.manager"]
            x = -30
            y = 40
            width = 800
            height = 600
            """);
        UiState state = UiState.load(file);
        assertThat(state.railVisible()).isFalse();
        assertThat(state.dock()).isEqualTo(new UiState.Dock(300, 260, 240, 0.4, 0.5, 0.5));
        assertThat(state.panel("dev.x.hosts")).hasValue(new UiState.Panel(Slot.RIGHT_BOTTOM, 1, true));
        assertThat(state.window("dev.x.manager")).hasValue(new UiState.Bounds(-30, 40, 800, 600));
        assertThat(state.window("dev.x.flat")).isEmpty();
        assertThat(state.window("dev.x.lost")).isEmpty();
    }

    @Test void invalidValuesAreRejectedAndInvalidFilesFallBackToDefaults() throws Exception {
        assertThatNullPointerException().isThrownBy(() -> new UiState.Panel(null, 0, true));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Panel(Slot.LEFT_TOP, -1, true));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Panel(Slot.LEFT_TOP, UiState.Panel.LAST + 1, true));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Dock(100, 260, 200, 0.5, 0.5, 0.5));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Dock(260, 260, 200, 0.5, Double.NaN, 0.5));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Bounds(0, 0, 0, 10));
        Path file = dir.resolve("ui-state.toml");
        for (String bad : new String[]{"not toml = = =", "version = 3\n", "rail_visible = false\n"}) {
            Files.writeString(file, bad);
            UiState state = UiState.load(file);
            assertThat(state.railVisible()).as(bad).isTrue();
            assertThat(state.dock()).as(bad).isEqualTo(UiState.Dock.DEFAULT);
            assertThat(state.panels()).as(bad).isEmpty();
        }
        Files.writeString(file, "version = 2\nrail_visible = false\n[windows.\"a.b\"]\nx = \"left\"\n");
        UiState badWindow = UiState.load(file);
        assertThat(badWindow.railVisible()).as("a bad window entry is not a bad file").isFalse();
        assertThat(badWindow.window("a.b")).isEmpty();
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

    /** The six dock keys as lines, in order: the three sizes, then the three ratios. */
    private static String[] dockLines(int[] sizes, double[] ratios) {
        return new String[]{"left_width = " + sizes[0], "right_width = " + sizes[1], "bottom_height = " + sizes[2],
            "left_ratio = " + ratios[0], "right_ratio = " + ratios[1], "bottom_ratio = " + ratios[2]};
    }

    /** Loads a version 2 file whose {@code [dock]} table holds exactly these lines. */
    private UiState.Dock loadDock(String... dockLines) throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, "version = 2\n\n[dock]\n" + String.join("\n", dockLines) + "\n");
        return UiState.load(file).dock();
    }

    @Test void roundTripsTheDraggedPalettePositionAndFallsBackAloneWhenItIsBad() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        UiState state = UiState.load(file);
        assertThat(state.palettePosition()).isEmpty();
        state.setPalettePosition(new UiState.PalettePosition(0.5, 0.2));
        state.save();
        assertThat(Files.readString(file)).contains("\n[palette]\ncenter_x = 0.5\ntop = 0.2\n");
        assertThat(UiState.load(file).palettePosition()).hasValue(new UiState.PalettePosition(0.5, 0.2));

        state.setPalettePosition(null);
        state.save();
        assertThat(Files.readString(file)).doesNotContain("[palette]");
        assertThat(UiState.load(file).palettePosition()).isEmpty();

        for (String bad : List.of("center_x = 1.5\ntop = 0.2", "center_x = 0.5", "center_x = 0.5\ntop = 2", "center_x = \"a\"\ntop = 0.2")) {
            Files.writeString(file, "version = 2\nrail_visible = false\n\n[palette]\n" + bad + "\n");
            UiState loaded = UiState.load(file);
            assertThat(loaded.palettePosition()).as(bad).isEmpty();
            assertThat(loaded.railVisible()).as("%s leaves the rest alone", bad).isFalse();
        }
        Files.writeString(file, "version = 2\n\n[palette]\ncenter_x = 0\ntop = 1\n");
        assertThat(UiState.load(file).palettePosition()).as("whole numbers are fractions too").hasValue(new UiState.PalettePosition(0, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.PalettePosition(-0.1, 0.5));
    }

    /** Spec 11.5 and Review Focus 4: the Run window's place is its own table; a bad value is ignored alone. */
    @Test void theRunWindowsPlaceIsItsOwnTableAndBadValuesAreIgnored() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        UiState state = UiState.load(file);
        assertThat(state.runWindowPosition()).isEmpty();
        state.setRunWindowPosition(new UiState.RunWindowPosition(0.0, 1.0));
        state.save();
        assertThat(Files.readString(file)).contains("\n[palette.run_window]\nleft = 0.0\nbottom = 1.0\n").doesNotContain("center_x");
        assertThat(UiState.load(file).runWindowPosition()).hasValue(new UiState.RunWindowPosition(0.0, 1.0));
        assertThat(UiState.load(file).palettePosition()).as("the card's place is another table").isEmpty();

        state.setPalettePosition(new UiState.PalettePosition(0.5, 0.2));
        state.save();
        var both = UiState.load(file);
        assertThat(both.palettePosition()).hasValue(new UiState.PalettePosition(0.5, 0.2));
        assertThat(both.runWindowPosition()).hasValue(new UiState.RunWindowPosition(0.0, 1.0));

        state.setRunWindowPosition(null);
        state.save();
        assertThat(Files.readString(file)).doesNotContain("run_window");
        assertThat(UiState.load(file).palettePosition()).as("forgetting one keeps the other").hasValue(new UiState.PalettePosition(0.5, 0.2));

        Files.writeString(file, "version = 2\n\n[palette.run_window]\nleft = 0.25\nbottom = 0.75\n");
        assertThat(UiState.load(file).runWindowPosition()).as("spec 11.5's table alone").hasValue(new UiState.RunWindowPosition(0.25, 0.75));
        for (String bad : List.of("left = 1.5\nbottom = 0.2", "left = 0.5", "left = 0.5\nbottom = -1", "left = \"a\"\nbottom = 0.2")) {
            Files.writeString(file, "version = 2\nrail_visible = false\n\n[palette]\ncenter_x = 0.5\ntop = 0.2\n\n[palette.run_window]\n" + bad + "\n");
            UiState loaded = UiState.load(file);
            assertThat(loaded.runWindowPosition()).as(bad).isEmpty();
            assertThat(loaded.palettePosition()).as("%s leaves the card's place alone", bad).hasValue(new UiState.PalettePosition(0.5, 0.2));
            assertThat(loaded.railVisible()).isFalse();
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.RunWindowPosition(0.5, 1.1));
    }
}
