package dev.jasper.app.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.dock.ToolWindowMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Full windows spec 6.1: a window's tool windows, which {@code workspace.toml} saves under {@code [window.tool_windows]}
 * with the keys {@code ui-state.toml} used for them; each look keeps its own dock (spec 10.7), and a bad entry falls back
 * alone.
 */
class ToolWindowsTest {
    static final String ENTERPRISE = "dev.jasper.enterprise";
    private static final String TABLE = "window.tool_windows";

    @Test void forNewWindowTurnsZenOffAndHidesDetachedToolWindows() {
        var floating = new UiState.Panel(Slot.RIGHT_TOP, 0, true, ToolWindowMode.FLOAT, Optional.of(new UiState.Bounds(10, 10, 300, 200)));
        var docked = new UiState.Panel(Slot.LEFT_TOP, 0, true, ToolWindowMode.DOCKED, Optional.empty());
        var state = new ToolWindowState(new ToolWindows(true, true, Map.of("", new ToolWindows.Look(Map.of("a.f", floating, "a.d", docked), UiState.Dock.DEFAULT))), "");
        var copy = state.forNewWindow();
        assertThat(copy.zenMode()).isFalse();
        assertThat(copy.panel("a.f").orElseThrow().visible()).isFalse();
        assertThat(copy.panel("a.f").orElseThrow().mode()).isEqualTo(ToolWindowMode.FLOAT);
        assertThat(copy.panel("a.f").orElseThrow().window()).contains(new UiState.Bounds(10, 10, 300, 200));
        assertThat(copy.panel("a.d").orElseThrow().visible()).isTrue();
        assertThat(state.zenMode()).as("the original is untouched").isTrue();
        assertThat(state.panel("a.f").orElseThrow().visible()).isTrue();
    }

    @Test void forNewWindowKeepsTheLookAndNotTheListener() {
        var state = new ToolWindowState(ToolWindows.BUILT_IN, ENTERPRISE);
        var heard = new AtomicInteger();
        state.onChanged(heard::incrementAndGet);
        var copy = state.forNewWindow();
        copy.changed();
        assertThat(copy.look()).isEqualTo(ENTERPRISE);
        assertThat(heard).hasValue(0);
        state.changed();
        assertThat(heard).hasValue(1);
    }

    @Test void aLookTheStateHasNoneForStartsFromItsBuiltInDock() {
        var standard = new UiState.Dock(300, 280, 180, 0.25, 0.5, 0.75);
        var saved = new ToolWindows(true, false, Map.of("", new ToolWindows.Look(
            Map.of("dev.x.hosts", new UiState.Panel(Slot.LEFT_TOP, 0, true)), standard)));
        var enterprise = new ToolWindowState(saved, ENTERPRISE);
        assertThat(enterprise.dock()).isEqualTo(UiState.Dock.DEFAULT);
        assertThat(enterprise.panels()).isEmpty();
        assertThat(enterprise.panel("dev.x.hosts")).as("a first start under the look: the registered defaults").isEmpty();
        var mine = new UiState.Dock(260, 350, 200, 0.5, 0.55, 0.5);
        enterprise.setDock(mine);
        assertThat(enterprise.snapshot().looks()).containsOnlyKeys("", ENTERPRISE);
        assertThat(enterprise.snapshot().looks().get("").dock()).as("the default look's dock is kept as it came").isEqualTo(standard);
        assertThat(enterprise.snapshot().looks().get(ENTERPRISE).dock()).isEqualTo(mine);
    }

    @Test void theBuiltInStateShowsTheRailOutOfZenWithTheDefaultDock() {
        var state = new ToolWindowState(ToolWindows.BUILT_IN, "");
        assertThat(state.railVisible()).isTrue();
        assertThat(state.zenMode()).isFalse();
        assertThat(state.dock()).isEqualTo(UiState.Dock.DEFAULT);
        assertThat(state.panels()).isEmpty();
        assertThat(state.snapshot()).isEqualTo(ToolWindows.BUILT_IN);
    }

    @Test void writtenAndReadBackEveryLookComesBack() {
        var state = new ToolWindowState(ToolWindows.BUILT_IN, "");
        state.putPanel("dev.x.hosts", new UiState.Panel(Slot.RIGHT_TOP, 0, true));
        state.putPanel("dev.x.files", new UiState.Panel(Slot.LEFT_BOTTOM, 1, false));
        state.setDock(new UiState.Dock(300, 280, 180, 0.25, 0.5, 0.75));
        state.setRailVisible(false);
        state.setZenMode(true);
        var enterprise = new ToolWindowState(state.snapshot(), ENTERPRISE);
        enterprise.putPanel("dev.x.hosts", new UiState.Panel(Slot.RIGHT_BOTTOM, 0, true));
        String text = written(enterprise.snapshot());
        assertThat(text).isEqualTo("""

            [window.tool_windows]
            rail_visible = false
            zen_mode = true

            [window.tool_windows.dock]
            left_width = 300
            right_width = 280
            bottom_height = 180
            left_ratio = 0.25
            right_ratio = 0.5
            bottom_ratio = 0.75

            [window.tool_windows.panels."dev.x.files"]
            slot = "LEFT_BOTTOM"
            order = 1
            visible = false

            [window.tool_windows.panels."dev.x.hosts"]
            slot = "RIGHT_TOP"
            order = 0
            visible = true

            [window.tool_windows.look."dev.jasper.enterprise".dock]
            left_width = 260
            right_width = 260
            bottom_height = 200
            left_ratio = 0.5
            right_ratio = 0.5
            bottom_ratio = 0.5

            [window.tool_windows.look."dev.jasper.enterprise".panels."dev.x.hosts"]
            slot = "RIGHT_BOTTOM"
            order = 0
            visible = true
            """);
        assertThat(readBack(text)).isEqualTo(enterprise.snapshot());
    }

    @Test void aBadEntryOrKeyFallsBackAlone() {
        var state = read("""
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
            """, "");
        assertThat(state.railVisible()).isFalse();
        assertThat(state.dock()).isEqualTo(new UiState.Dock(260, 300, 200, 0.5, 0.3, 0.5));
        assertThat(state.panel("dev.x.good")).hasValue(new UiState.Panel(Slot.BOTTOM_RIGHT, 2, true));
        assertThat(state.panel("dev.x.unknown")).isEmpty();
        assertThat(state.panel("dev.x.disordered")).hasValue(new UiState.Panel(Slot.LEFT_TOP, UiState.Panel.LAST, false));
    }

    @Test void dockValuesExactlyAtTheirLimitsAreAccepted() {
        assertThat(readDock("left_width = 120", "right_width = 120", "bottom_height = 120",
            "left_ratio = 0.1", "right_ratio = 0.1", "bottom_ratio = 0.1")).as("every minimum")
            .isEqualTo(new UiState.Dock(120, 120, 120, 0.1, 0.1, 0.1));
        assertThat(readDock("left_width = 4000", "right_width = 4000", "bottom_height = 4000",
            "left_ratio = 0.9", "right_ratio = 0.9", "bottom_ratio = 0.9")).as("every maximum")
            .isEqualTo(new UiState.Dock(4000, 4000, 4000, 0.9, 0.9, 0.9));
    }

    @Test void aDockValueOneStepOutsideItsLimitsFallsBackAloneAndTheOtherKeysKeepTheirValues() {
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
                assertThat(readDock(lines)).as(lines[bad]).isEqualTo(new UiState.Dock(expectedSizes[0], expectedSizes[1], expectedSizes[2],
                    expectedRatios[0], expectedRatios[1], expectedRatios[2]));
            }
        }
    }

    @Test void aScalarWhereTheDockTableBelongsFallsBackToDefaultsAndKeepsThePanels() {
        var state = read("""
            rail_visible = false
            dock = 5

            [panels."dev.x.good"]
            slot = "RIGHT_BOTTOM"
            order = 1
            visible = true
            """, "");
        assertThat(state.dock()).isEqualTo(UiState.Dock.DEFAULT);
        assertThat(state.panel("dev.x.good")).hasValue(new UiState.Panel(Slot.RIGHT_BOTTOM, 1, true));
        assertThat(state.railVisible()).as("the rest of the table is read").isFalse();
    }

    @Test void aScalarWhereThePanelsTableBelongsLeavesNoPanelsAndKeepsTheDock() {
        var state = read("""
            panels = "x"

            [dock]
            left_width = 300
            right_width = 310
            bottom_height = 320
            left_ratio = 0.2
            right_ratio = 0.3
            bottom_ratio = 0.4
            """, "");
        assertThat(state.panels()).isEmpty();
        assertThat(state.dock()).isEqualTo(new UiState.Dock(300, 310, 320, 0.2, 0.3, 0.4));
    }

    @Test void aPanelEntryThatIsAScalarIsSkippedAndTheOthersSurvive() {
        var state = read("""
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
            """, "");
        assertThat(state.panel("dev.x.scalar")).isEmpty();
        assertThat(state.panels()).containsOnlyKeys("dev.x.good", "dev.x.other");
        assertThat(state.panel("dev.x.good")).hasValue(new UiState.Panel(Slot.BOTTOM_RIGHT, 2, true));
        assertThat(state.panel("dev.x.other")).hasValue(new UiState.Panel(Slot.LEFT_BOTTOM, 0, false));
    }

    /** Floating tool windows spec: a detached panel keeps its slot and saves its mode and its own window's bounds with it. */
    @Test void aDetachedPanelsModeAndWindowRoundTrip() {
        var state = new ToolWindowState(ToolWindows.BUILT_IN, "");
        var floating = new UiState.Panel(Slot.LEFT_TOP, 0, true, ToolWindowMode.FLOAT, Optional.of(new UiState.Bounds(-1200, 40, 420, 610)));
        var window = new UiState.Panel(Slot.BOTTOM_LEFT, 1, false, ToolWindowMode.WINDOW, Optional.of(new UiState.Bounds(30, 50, 700, 300)));
        state.putPanel("dev.x.floating", floating);
        state.putPanel("dev.x.window", window);
        String text = written(state.snapshot());
        assertThat(text).contains("""

            [window.tool_windows.panels."dev.x.floating"]
            slot = "LEFT_TOP"
            order = 0
            visible = true
            mode = "float"
            window = { x = -1200, y = 40, width = 420, height = 610 }
            """).contains("mode = \"window\"\nwindow = { x = 30, y = 50, width = 700, height = 300 }\n");
        var again = new ToolWindowState(readBack(text), "");
        assertThat(again.panel("dev.x.floating")).hasValue(floating);
        assertThat(again.panel("dev.x.window")).hasValue(window);
    }

    @Test void aDetachedPanelWithoutWindowBoundsWritesOnlyItsMode() {
        var state = new ToolWindowState(ToolWindows.BUILT_IN, "");
        var floating = new UiState.Panel(Slot.RIGHT_TOP, 2, true, ToolWindowMode.FLOAT, Optional.empty());
        state.putPanel("dev.x.floating", floating);
        String text = written(state.snapshot());
        assertThat(text).contains("mode = \"float\"\n").doesNotContain("window =");
        assertThat(new ToolWindowState(readBack(text), "").panel("dev.x.floating")).hasValue(floating);
    }

    /** The bounds are the panel's own window's last place: they outlast docking it back. */
    @Test void aDockedPanelKeepsItsWindowBounds() {
        var state = new ToolWindowState(ToolWindows.BUILT_IN, "");
        var docked = new UiState.Panel(Slot.RIGHT_BOTTOM, 0, true, ToolWindowMode.DOCKED, Optional.of(new UiState.Bounds(10, 20, 300, 400)));
        state.putPanel("dev.x.docked", docked);
        String text = written(state.snapshot());
        assertThat(text).doesNotContain("\nmode = ").contains("window = { x = 10, y = 20, width = 300, height = 400 }\n");
        assertThat(new ToolWindowState(readBack(text), "").panel("dev.x.docked")).hasValue(docked);
    }

    @Test void anUnknownModeReadsAsDocked() {
        var state = read("""
            [panels."dev.x.unknown"]
            slot = "LEFT_BOTTOM"
            order = 3
            visible = true
            mode = "fullscreen"

            [panels."dev.x.typed"]
            slot = "RIGHT_TOP"
            order = 1
            visible = false
            mode = 2

            [panels."dev.x.upper"]
            slot = "BOTTOM_LEFT"
            order = 0
            visible = true
            mode = "FLOAT"

            [panels."dev.x.float"]
            slot = "RIGHT_BOTTOM"
            order = 4
            visible = true
            mode = "float"
            """, "");
        assertThat(state.panel("dev.x.unknown")).hasValue(new UiState.Panel(Slot.LEFT_BOTTOM, 3, true));
        assertThat(state.panel("dev.x.typed")).hasValue(new UiState.Panel(Slot.RIGHT_TOP, 1, false));
        assertThat(state.panel("dev.x.upper")).as("the names are the lower-case ones the file writes").hasValue(new UiState.Panel(Slot.BOTTOM_LEFT, 0, true));
        assertThat(state.panel("dev.x.float").orElseThrow().mode()).isEqualTo(ToolWindowMode.FLOAT);
    }

    @Test void aBadWindowTableIsDroppedAndThePanelStillLoads() {
        var state = read("""
            [panels."dev.x.flat"]
            slot = "LEFT_TOP"
            order = 0
            visible = true
            mode = "float"
            window = { x = 10, y = 20, width = 0, height = 480 }

            [panels."dev.x.typed"]
            slot = "RIGHT_TOP"
            order = 2
            visible = true
            window = { x = "left", y = 20, width = 640, height = 480 }

            [panels."dev.x.scalar"]
            slot = "RIGHT_BOTTOM"
            order = 3
            visible = true
            mode = "float"
            window = 5
            """, "");
        assertThat(state.panel("dev.x.flat")).hasValue(new UiState.Panel(Slot.LEFT_TOP, 0, true, ToolWindowMode.FLOAT, Optional.empty()));
        assertThat(state.panel("dev.x.typed")).hasValue(new UiState.Panel(Slot.RIGHT_TOP, 2, true));
        assertThat(state.panel("dev.x.scalar")).hasValue(new UiState.Panel(Slot.RIGHT_BOTTOM, 3, true, ToolWindowMode.FLOAT, Optional.empty()));
    }

    @Test void aBadLookEntryFallsBackAlone() {
        String text = """
            [dock]
            left_width = 300

            [look."dev.jasper.enterprise".dock]
            left_width = 99999
            right_width = 400

            [look."dev.jasper.enterprise".panels."dev.x.bad"]
            slot = "NOWHERE"

            [look."dev.jasper.enterprise".panels."dev.x.good"]
            slot = "BOTTOM_LEFT"
            order = 2
            visible = false

            [look."Not An Id".panels."dev.x.good"]
            slot = "RIGHT_TOP"
            """;
        var enterprise = read(text, ENTERPRISE);
        assertThat(enterprise.dock().leftWidth()).as("an out-of-range width falls back alone").isEqualTo(UiState.Dock.DEFAULT.leftWidth());
        assertThat(enterprise.dock().rightWidth()).isEqualTo(400);
        assertThat(enterprise.panel("dev.x.bad")).isEmpty();
        assertThat(enterprise.panel("dev.x.good")).hasValue(new UiState.Panel(Slot.BOTTOM_LEFT, 2, false));
        assertThat(read(text, "").dock().leftWidth()).isEqualTo(300);
        assertThat(read(text, "").panel("dev.x.good")).isEmpty();
        assertThat(read(text, "").snapshot().looks()).containsOnlyKeys("", ENTERPRISE);
    }

    @Test void aLookKeyIsAPluginIdOrEmptyAndAToolWindowIdNeedsNoEscaping() {
        assertThat(new ToolWindowState(ToolWindows.BUILT_IN, "").look()).isEmpty();
        assertThat(new ToolWindowState(ToolWindows.BUILT_IN, ENTERPRISE).look()).isEqualTo(ENTERPRISE);
        assertThatIllegalArgumentException().isThrownBy(() -> new ToolWindowState(ToolWindows.BUILT_IN, "Not An Id"));
        assertThatIllegalArgumentException().isThrownBy(() -> new ToolWindows(true, false, Map.of("Not An Id", new ToolWindows.Look(Map.of(), UiState.Dock.DEFAULT))));
        var state = new ToolWindowState(ToolWindows.BUILT_IN, "");
        state.putPanel("Bad \"id\"", new UiState.Panel(Slot.LEFT_TOP, 0, true));
        assertThat(state.panels()).isEmpty();
    }

    /** {@code text} read as a window's {@code tool_windows} table, under the look {@code look}. */
    private static ToolWindowState read(String text, String look) {
        TomlParseResult toml = Toml.parse(text);
        assertThat(toml.errors()).isEmpty();
        return new ToolWindowState(ToolWindows.read(toml), look);
    }

    private static String written(ToolWindows tools) {
        var text = new StringBuilder();
        tools.append(text, TABLE);
        return text.toString();
    }

    private static ToolWindows readBack(String text) {
        TomlParseResult toml = Toml.parse(text);
        assertThat(toml.errors()).isEmpty();
        TomlTable table = toml.getTable(List.of("window", "tool_windows"));
        return ToolWindows.read(table);
    }

    /** The six dock keys as lines, in order: the three sizes, then the three ratios. */
    private static String[] dockLines(int[] sizes, double[] ratios) {
        return new String[]{"left_width = " + sizes[0], "right_width = " + sizes[1], "bottom_height = " + sizes[2],
            "left_ratio = " + ratios[0], "right_ratio = " + ratios[1], "bottom_ratio = " + ratios[2]};
    }

    private static UiState.Dock readDock(String... dockLines) {
        return read("[dock]\n" + String.join("\n", dockLines) + "\n", "").dock();
    }
}
