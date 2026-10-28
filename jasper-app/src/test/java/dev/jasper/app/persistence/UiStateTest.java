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

    /** A window entry that reads back: whether the rest of a file was read. */
    private static final String MARKER = "\n[windows.\"dev.x.marker\"]\nx = 1\ny = 2\nwidth = 3\nheight = 4\n";
    private static final UiState.Bounds MARKED = new UiState.Bounds(1, 2, 3, 4);

    @Test void roundTripsTheWindows() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        UiState state = UiState.load(file);
        state.putWindow("dev.x.manager", new UiState.Bounds(-20, 40, 800, 600));
        state.save();
        assertThat(Files.readString(file)).isEqualTo("""
            version = 3

            [windows."dev.x.manager"]
            x = -20
            y = 40
            width = 800
            height = 600
            """);
        assertThat(UiState.load(file).window("dev.x.manager")).hasValue(new UiState.Bounds(-20, 40, 800, 600));
    }

    /** Full windows spec 6.1: an older file is read as no saved state; Jasper keeps no migration. */
    @Test void aVersionTwoFileIsReadAsDefaults() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, """
            version = 2
            rail_visible = false
            zen_mode = true

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

            [windows."dev.x.manager"]
            x = -30
            y = 40
            width = 800
            height = 600

            [palette]
            center_x = 0.5
            top = 0.2
            """);
        UiState state = UiState.load(file);
        assertThat(state.window("dev.x.manager")).isEmpty();
        assertThat(state.palettePosition()).isEmpty();
        assertThat(state.chooser()).isEqualTo(UiState.Chooser.DEFAULT);
    }

    /** Full windows spec 6.1: the stripes, Zen and the dock are each window's own, in workspace.toml. */
    @Test void windowShapedKeysAreNeitherReadNorWritten() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, """
            version = 3
            rail_visible = false
            zen_mode = true

            [dock]
            left_width = 300

            [panels."dev.x.hosts"]
            slot = "RIGHT_BOTTOM"
            order = 1
            visible = true

            [look."dev.jasper.enterprise".dock]
            left_width = 300

            [main_window]
            x = 10
            y = 20
            width = 800
            height = 700
            extended_state = 6
            """ + MARKER);
        UiState state = UiState.load(file);
        assertThat(state.window("dev.x.marker")).as("the rest still reads").hasValue(MARKED);
        state.save();
        assertThat(Files.readString(file)).doesNotContain("rail_visible", "zen_mode", "[dock]", "[panels", "[look", "main_window")
            .contains("[windows.\"dev.x.marker\"]");
    }

    @Test void aBadWindowEntryFallsBackAlone() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, """
            version = 3

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
        assertThat(state.window("dev.x.manager")).hasValue(new UiState.Bounds(-30, 40, 800, 600));
        assertThat(state.window("dev.x.flat")).isEmpty();
        assertThat(state.window("dev.x.lost")).isEmpty();
    }

    /** A window-sized plugin dialog remembers its bounds under its plugin id and title, which TOML quotes. */
    @Test void aPluginDialogsKeyWithItsTitleRoundTrips() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        UiState state = UiState.load(file);
        String key = "dev.jasper.vault|Credential \"Vault\"";
        state.putWindow(key, new UiState.Bounds(10, 20, 900, 583));
        state.putWindow("bad\nkey", new UiState.Bounds(10, 20, 900, 583));
        state.putWindow("", new UiState.Bounds(10, 20, 900, 583));
        state.save();
        assertThat(Files.readString(file)).contains("[windows.\"dev.jasper.vault|Credential \\\"Vault\\\"\"]");
        UiState again = UiState.load(file);
        assertThat(again.window(key)).hasValue(new UiState.Bounds(10, 20, 900, 583));
        assertThat(again.window("bad\nkey")).as("a control character").isEmpty();
        assertThat(again.window("")).isEmpty();
    }

    /** Review fix round 1 (M3): a key with half a surrogate pair, which TOML cannot write, and one past the limit are refused. */
    @Test void aWindowKeyWithALoneSurrogateOrPastItsLengthIsRefused() throws Exception {
        UiState state = UiState.inMemory();
        var bounds = new UiState.Bounds(10, 20, 900, 583);
        String lone = "dev.x|" + (char) 0xD83D, pair = "dev.x|" + new String(new char[] {(char) 0xD83D, (char) 0xDE00});
        String longest = "d" + "x".repeat(UiState.WINDOW_KEY_LENGTH - 1);
        for (String key : List.of(lone, pair, longest, longest + "x")) state.putWindow(key, bounds);
        assertThat(state.window(lone)).as("a lone surrogate").isEmpty();
        assertThat(state.window(pair)).as("a whole pair").hasValue(bounds);
        assertThat(state.window(longest)).as("the longest key").hasValue(bounds);
        assertThat(state.window(longest + "x")).as("one past it").isEmpty();
    }

    @Test void invalidValuesAreRejectedAndInvalidFilesFallBackToDefaults() throws Exception {
        assertThatNullPointerException().isThrownBy(() -> new UiState.Panel(null, 0, true));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Panel(Slot.LEFT_TOP, -1, true));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Panel(Slot.LEFT_TOP, UiState.Panel.LAST + 1, true));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Dock(100, 260, 200, 0.5, 0.5, 0.5));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Dock(260, 260, 200, 0.5, Double.NaN, 0.5));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Bounds(0, 0, 0, 10));
        Path file = dir.resolve("ui-state.toml");
        for (String bad : new String[]{"not toml = = =", "version = 4\n" + MARKER, MARKER}) {
            Files.writeString(file, bad);
            assertThat(UiState.load(file).window("dev.x.marker")).as(bad).isEmpty();
        }
        Files.writeString(file, "version = 3\n[windows.\"a.b\"]\nx = \"left\"\n" + MARKER);
        UiState badWindow = UiState.load(file);
        assertThat(badWindow.window("dev.x.marker")).as("a bad window entry is not a bad file").hasValue(MARKED);
        assertThat(badWindow.window("a.b")).isEmpty();
    }

    @Test void inMemoryStateNeverTouchesDisk() {
        UiState state = UiState.inMemory();
        state.putWindow("dev.x.hosts", new UiState.Bounds(0, 0, 10, 10));
        assertThatCode(state::save).doesNotThrowAnyException();
        assertThat(state.window("dev.x.hosts")).isPresent();
    }

    @Test void remembersTheFileChoosersFoldersAndToggles() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        UiState state = UiState.load(file);
        assertThat(state.chooser()).isEqualTo(UiState.Chooser.DEFAULT);
        state.setChooser(new UiState.Chooser(List.of("/work/a \"quoted\"", "C:\\Users\\me"), List.of("/pinned\ttab"), true, false, "name", true, java.util.Map.of()));
        state.save();
        assertThat(Files.readString(file)).contains("[chooser]",
            "recent = [\"/work/a \\\"quoted\\\"\", \"C:\\\\Users\\\\me\"]", "pinned = [\"/pinned\\u0009tab\"]",
            "preview = true", "show_hidden = false");
        assertThat(UiState.load(file).chooser()).isEqualTo(state.chooser());
    }

    @Test void chooserSortAndWidthsRoundTrip() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        UiState state = UiState.load(file);
        state.setChooser(new UiState.Chooser(List.of(), List.of(), false, true, "size", false, java.util.Map.of("name", 320, "size", 90)));
        state.save();
        assertThat(Files.readString(file)).contains("sort = \"size\"", "sort_ascending = false", "[chooser.widths]", "\"name\" = 320");
        assertThat(UiState.load(file).chooser()).isEqualTo(state.chooser());
        Files.writeString(file, "version = 3\n[chooser]\nsort = 7\nwidths = \"x\"\n");
        assertThat(UiState.load(file).chooser().sortColumn()).isEqualTo("name");
        assertThat(UiState.load(file).chooser().widths()).isEmpty();
    }

    @Test void unreadableChooserValuesFallBackOneKeyAtATime() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, "version = 3\n[chooser]\nrecent = [1, \"/ok\"]\npinned = \"not a list\"\npreview = \"yes\"\n");
        assertThat(UiState.load(file).chooser()).isEqualTo(new UiState.Chooser(List.of("/ok"), List.of(), false, true, "name", true, java.util.Map.of()));
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
            Files.writeString(file, "version = 3\n" + MARKER + "\n[palette]\n" + bad + "\n");
            UiState loaded = UiState.load(file);
            assertThat(loaded.palettePosition()).as(bad).isEmpty();
            assertThat(loaded.window("dev.x.marker")).as("%s leaves the rest alone", bad).hasValue(MARKED);
        }
        Files.writeString(file, "version = 3\n\n[palette]\ncenter_x = 0\ntop = 1\n");
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

        Files.writeString(file, "version = 3\n\n[palette.run_window]\nleft = 0.25\nbottom = 0.75\n");
        assertThat(UiState.load(file).runWindowPosition()).as("spec 11.5's table alone").hasValue(new UiState.RunWindowPosition(0.25, 0.75));
        for (String bad : List.of("left = 1.5\nbottom = 0.2", "left = 0.5", "left = 0.5\nbottom = -1", "left = \"a\"\nbottom = 0.2")) {
            Files.writeString(file, "version = 3\n" + MARKER + "\n[palette]\ncenter_x = 0.5\ntop = 0.2\n\n[palette.run_window]\n" + bad + "\n");
            UiState loaded = UiState.load(file);
            assertThat(loaded.runWindowPosition()).as(bad).isEmpty();
            assertThat(loaded.palettePosition()).as("%s leaves the card's place alone", bad).hasValue(new UiState.PalettePosition(0.5, 0.2));
            assertThat(loaded.window("dev.x.marker")).hasValue(MARKED);
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.RunWindowPosition(0.5, 1.1));
    }
}
