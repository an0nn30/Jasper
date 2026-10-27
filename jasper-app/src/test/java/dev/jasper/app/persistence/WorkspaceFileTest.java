package dev.jasper.app.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import dev.jasper.app.dock.Slot;
import dev.jasper.app.dock.ToolWindowMode;
import dev.jasper.app.layout.Child;
import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.Node;
import dev.jasper.app.layout.Orientation;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.Split;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.persistence.WorkspaceFile.Snapshot;
import dev.jasper.app.persistence.WorkspaceFile.TabRecord;
import dev.jasper.app.persistence.WorkspaceFile.WindowRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Workspace tabs spec 8, tabs and panes spec 4 and 12: the workspace file's values and its exact text, in the spec's words. */
class WorkspaceFileTest {
    @TempDir Path dir;

    static final UiState.Bounds BOUNDS = new UiState.Bounds(40, 60, 1400, 900);

    static TabId tab(int n) {
        return TabId.parse("00000000-0000-4000-8000-%012d".formatted(n));
    }

    static GroupId groupId(int n) {
        return GroupId.parse("00000000-0000-4000-9000-%012d".formatted(n));
    }

    static ParentTabId parentTabId(int n) {
        return ParentTabId.parse("00000000-0000-4000-a000-%012d".formatted(n));
    }

    /** Pane {@code id} holding tab {@code n}, its one tab (workspace plan 8a Rulings 2). */
    static Group group(int id, int n) {
        return new Group(groupId(id), List.of(tab(n)), Optional.of(tab(n)));
    }

    /** Group {@code n} alone, holding tab {@code n}. */
    static Layout single(int n) {
        return new Layout(group(n, n), groupId(n), Optional.empty());
    }

    /** A window's only parent tab, {@code id}, holding {@code layout}. */
    static ParentTabs one(int id, Layout layout) {
        return ParentTabs.of(new ParentTab(parentTabId(id), layout));
    }

    /** A record of a terminal tab in /tmp for each tab of {@code layout}. */
    static Map<TabId, TabRecord> terminals(ParentTabs layout) {
        var tabs = new HashMap<TabId, TabRecord>();
        for (TabId tab : layout.tabs()) tabs.put(tab, new TabRecord.Terminal("", "/tmp", ""));
        return tabs;
    }

    /**
     * Spec 8's example as the window's one parent tab: two columns, each in two rows, the right column's upper pane active
     * and maximized.
     */
    static Snapshot snapshot() {
        Node left = new Split(Orientation.VERTICAL, List.of(new Child(group(1, 1), 0.5), new Child(group(5, 2), 0.5)));
        Node root = new Split(Orientation.HORIZONTAL, List.of(new Child(left, 0.5),
            new Child(new Split(Orientation.VERTICAL, List.of(new Child(group(2, 3), 0.75), new Child(group(3, 4), 0.25))), 0.5)));
        var main = new WindowRecord(Optional.of(BOUNDS), 6, one(1, new Layout(root, groupId(2), Optional.of(groupId(2)))), Map.of(
            tab(1), new TabRecord.Terminal("build", "/Users/me/src/jasper", "zsh"),
            tab(2), new TabRecord.Content("README.md", "dev.jasper.editor", "document", Map.of("path", "/Users/me/src/jasper/README.md")),
            tab(3), new TabRecord.Terminal("", "C:\\work", ""),
            tab(4), new TabRecord.Content("Files \"prod\"", "dev.jasper.remote", "sftp", Map.of("remote path", "/srv/a", "host", "example"))), ToolWindows.BUILT_IN);
        return new Snapshot(main);
    }

    /** A window of two parent tabs, the second showing (parent tabs spec 9). */
    static Snapshot parentTabs() {
        var main = new ParentTabs(List.of(new ParentTab(parentTabId(1), single(1)), new ParentTab(parentTabId(2), single(2))), parentTabId(2));
        return new Snapshot(new WindowRecord(Optional.of(BOUNDS), 0, main, terminals(main), ToolWindows.BUILT_IN));
    }

    @Test void writesSpecEightsWorkspaceExactly() throws Exception {
        Path file = dir.resolve("nested/workspace.toml");
        WorkspaceFile.write(file, snapshot());
        assertThat(Files.readString(file)).isEqualTo("""
            version = 2

            [window]
            x = 40
            y = 60
            width = 1400
            height = 900
            extended_state = 6
            shown_tab = "00000000-0000-4000-a000-000000000001"

            [window.tool_windows]
            rail_visible = true
            zen_mode = false

            [[window.tabs]]
            id = "00000000-0000-4000-a000-000000000001"
            active_pane = "00000000-0000-4000-9000-000000000002"
            maximized = "00000000-0000-4000-9000-000000000002"

            [[window.tabs.nodes]]
            id = "e8b5eccb-037b-3d65-a6c6-5e4889602b31"
            kind = "split"
            orientation = "horizontal"
            parent = ""
            weight = 1.0

            [[window.tabs.nodes]]
            id = "92cc2645-e802-3d4b-aa67-3432c68ad0b2"
            kind = "split"
            orientation = "vertical"
            parent = "e8b5eccb-037b-3d65-a6c6-5e4889602b31"
            weight = 0.5

            [[window.tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000001"
            kind = "pane"
            parent = "92cc2645-e802-3d4b-aa67-3432c68ad0b2"
            weight = 0.5
            item = "00000000-0000-4000-8000-000000000001"

            [[window.tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000005"
            kind = "pane"
            parent = "92cc2645-e802-3d4b-aa67-3432c68ad0b2"
            weight = 0.5
            item = "00000000-0000-4000-8000-000000000002"

            [[window.tabs.nodes]]
            id = "59943c66-5e92-39d1-a8ea-1f88b440a667"
            kind = "split"
            orientation = "vertical"
            parent = "e8b5eccb-037b-3d65-a6c6-5e4889602b31"
            weight = 0.5

            [[window.tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000002"
            kind = "pane"
            parent = "59943c66-5e92-39d1-a8ea-1f88b440a667"
            weight = 0.75
            item = "00000000-0000-4000-8000-000000000003"

            [[window.tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000003"
            kind = "pane"
            parent = "59943c66-5e92-39d1-a8ea-1f88b440a667"
            weight = 0.25
            item = "00000000-0000-4000-8000-000000000004"

            [[window.items]]
            id = "00000000-0000-4000-8000-000000000001"
            kind = "terminal"
            title = "build"
            directory = "/Users/me/src/jasper"
            profile = "zsh"

            [[window.items]]
            id = "00000000-0000-4000-8000-000000000002"
            kind = "content:dev.jasper.editor:document"
            title = "README.md"
            state = { path = "/Users/me/src/jasper/README.md" }

            [[window.items]]
            id = "00000000-0000-4000-8000-000000000003"
            kind = "terminal"
            title = ""
            directory = "C:\\\\work"
            profile = ""

            [[window.items]]
            id = "00000000-0000-4000-8000-000000000004"
            kind = "content:dev.jasper.remote:sftp"
            title = "Files \\"prod\\""
            state = { host = "example", "remote path" = "/srv/a" }
            """);
        try (var listing = Files.list(file.getParent())) {
            assertThat(listing).as("no temporary file is left").containsExactly(file);
        }
    }

    @Test void writesEveryParentTabOfTheWindowInBarOrder() {
        assertThat(WorkspaceFile.encode(parentTabs())).isEqualTo("""
            version = 2

            [window]
            x = 40
            y = 60
            width = 1400
            height = 900
            extended_state = 0
            shown_tab = "00000000-0000-4000-a000-000000000002"

            [window.tool_windows]
            rail_visible = true
            zen_mode = false

            [[window.tabs]]
            id = "00000000-0000-4000-a000-000000000001"
            active_pane = "00000000-0000-4000-9000-000000000001"
            maximized = ""

            [[window.tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000001"
            kind = "pane"
            parent = ""
            weight = 1.0
            item = "00000000-0000-4000-8000-000000000001"

            [[window.tabs]]
            id = "00000000-0000-4000-a000-000000000002"
            active_pane = "00000000-0000-4000-9000-000000000002"
            maximized = ""

            [[window.tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000002"
            kind = "pane"
            parent = ""
            weight = 1.0
            item = "00000000-0000-4000-8000-000000000002"

            [[window.items]]
            id = "00000000-0000-4000-8000-000000000001"
            kind = "terminal"
            title = ""
            directory = "/tmp"
            profile = ""

            [[window.items]]
            id = "00000000-0000-4000-8000-000000000002"
            kind = "terminal"
            title = ""
            directory = "/tmp"
            profile = ""
            """);
    }

    @Test void theEmptyWorkspaceWritesItsTabWithAPaneThatNamesNoItem() {
        Layout empty = new Layout(new Group(groupId(1), List.of(), Optional.empty()), groupId(1), Optional.empty());
        assertThat(WorkspaceFile.encode(new Snapshot(new WindowRecord(Optional.of(new UiState.Bounds(0, 0, 800, 600)), 0,
            one(1, empty), Map.of(), ToolWindows.BUILT_IN))))
            .isEqualTo("""
                version = 2

                [window]
                x = 0
                y = 0
                width = 800
                height = 600
                extended_state = 0
                shown_tab = "00000000-0000-4000-a000-000000000001"

                [window.tool_windows]
                rail_visible = true
                zen_mode = false

                [[window.tabs]]
                id = "00000000-0000-4000-a000-000000000001"
                active_pane = "00000000-0000-4000-9000-000000000001"
                maximized = ""

                [[window.tabs.nodes]]
                id = "00000000-0000-4000-9000-000000000001"
                kind = "pane"
                parent = ""
                weight = 1.0
                item = ""
                """);
    }

    /**
     * Workspace plan 6b Rulings 16 (6b Task 2's review): one counted pass gives the file's size in UTF-8 bytes, and for each
     * item what leaving it out alone takes out of the file: its entry, its pane's node and, as its tab's only item, its tab.
     */
    @Test void sizesCountTheFilesBytesAndWhatLeavesWithEachItem() {
        assertThat(WorkspaceFile.sizes(snapshot()).total()).as("spec 8's file, splits and all").isEqualTo((long) WorkspaceFile.encodedBytes(snapshot()));
        assertThat(WorkspaceFile.sizes(snapshot()).items()).containsOnlyKeys(tab(1), tab(2), tab(3), tab(4));
        // Escaped, and wider in UTF-8 than in characters.
        var wide = new TabRecord.Content("\u4e2d\u6587 \"notes\"", "dev.jasper.editor", "document",
            Map.of("path", "/\u00e9t\u00e9/\uD83D\uDE00", "bell", "\u0007"));
        var two = new ParentTabs(List.of(new ParentTab(parentTabId(1), single(1)), new ParentTab(parentTabId(2), single(2))), parentTabId(2));
        var items = new HashMap<>(terminals(two));
        items.put(tab(1), wide);
        var both = new Snapshot(new WindowRecord(Optional.of(BOUNDS), 0, two, items, ToolWindows.BUILT_IN));
        var without = new Snapshot(new WindowRecord(Optional.of(BOUNDS), 0,
            ParentTabs.of(new ParentTab(parentTabId(2), single(2))), Map.of(tab(2), items.get(tab(2))), ToolWindows.BUILT_IN));
        WorkspaceFile.Sizes sizes = WorkspaceFile.sizes(both);
        assertThat(sizes.total()).as("the file's bytes").isEqualTo((long) WorkspaceFile.encodedBytes(both));
        assertThat(sizes.items().get(tab(1))).as("its entry, its pane and its tab")
            .isEqualTo((long) (WorkspaceFile.encodedBytes(both) - WorkspaceFile.encodedBytes(without)));
    }

    @Test void splitIdsAreNameBasedUuidsOfTheirPath() {
        assertThat(WorkspaceFile.splitId("")).isEqualTo("e8b5eccb-037b-3d65-a6c6-5e4889602b31");
        assertThat(WorkspaceFile.splitId("1")).isEqualTo("59943c66-5e92-39d1-a8ea-1f88b440a667");
        assertThat(UUID.fromString(WorkspaceFile.splitId("1.0")).version()).as("name-based, never a random id").isEqualTo(3);
    }

    @Test void theRecordsCheckTheirInvariants() {
        ParentTabs held = one(1, Layout.of(tab(1)));
        Map<TabId, TabRecord> record = Map.of(tab(1), new TabRecord.Terminal("", "/tmp", ""));
        assertThatIllegalArgumentException().as("a tab without a record")
            .isThrownBy(() -> new WindowRecord(Optional.empty(), 0, held, Map.of(), ToolWindows.BUILT_IN));
        assertThatIllegalArgumentException().as("a record without a tab")
            .isThrownBy(() -> new WindowRecord(Optional.empty(), 0, one(1, Layout.empty()), record, ToolWindows.BUILT_IN));
        assertThatIllegalArgumentException().as("minimized is not kept")
            .isThrownBy(() -> new WindowRecord(Optional.empty(), 1, held, record, ToolWindows.BUILT_IN));
        assertThatNullPointerException().as("no tool windows").isThrownBy(() -> new WindowRecord(Optional.empty(), 0, held, record, null));
        assertThatNullPointerException().as("no window").isThrownBy(() -> new Snapshot(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new TabRecord.Content("", "", "document", Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new TabRecord.Content("", "dev.x:y", "document", Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new TabRecord.Content("", "dev.x", "", Map.of()));
        assertThatNullPointerException().isThrownBy(() -> new TabRecord.Terminal(null, "", ""));
    }

    /** Full windows spec 5.1: the window's workspace may be empty, and no window's role is written. */
    @Test void theWindowsLayoutMayBeEmptyAndRoleIsNotWritten() throws Exception {
        Layout empty = new Layout(new Group(groupId(9), List.of(), Optional.empty()), groupId(9), Optional.empty());
        var saved = new Snapshot(new WindowRecord(Optional.empty(), 0, one(2, empty), Map.of(), ToolWindows.BUILT_IN));
        String text = WorkspaceFile.encode(saved);
        assertThat(text).doesNotContain("role");
        Path file = dir.resolve("workspace.toml");
        WorkspaceFile.write(file, saved);
        assertThat(WorkspaceFile.read(file)).isEqualTo(new WorkspaceFile.Loaded.Restore(saved, Optional.empty()));
    }

    /** Full windows spec 6.1: the window's tool windows, every look's, are saved with it. */
    @Test void oneWindowWithItsToolWindowsRoundTrips() throws Exception {
        var floating = new UiState.Panel(Slot.RIGHT_TOP, 0, true, ToolWindowMode.FLOAT, Optional.of(new UiState.Bounds(-1200, 40, 420, 610)));
        var tools = new ToolWindows(false, true, Map.of(
            "", new ToolWindows.Look(Map.of("dev.x.hosts", floating, "dev.x.files", new UiState.Panel(Slot.LEFT_BOTTOM, 1, false)),
                new UiState.Dock(300, 280, 180, 0.25, 0.5, 0.75)),
            "dev.jasper.enterprise", new ToolWindows.Look(Map.of("dev.x.hosts", new UiState.Panel(Slot.BOTTOM_LEFT, 0, true)), UiState.Dock.DEFAULT)));
        WindowRecord window = snapshot().window();
        var saved = new Snapshot(new WindowRecord(window.bounds(), window.extendedState(), window.layout(), window.tabs(), tools));
        Path file = dir.resolve("workspace.toml");
        WorkspaceFile.write(file, saved);
        assertThat(Files.readString(file)).contains("""

            [window.tool_windows]
            rail_visible = false
            zen_mode = true

            [window.tool_windows.dock]
            left_width = 300
            """, """

            [window.tool_windows.panels."dev.x.hosts"]
            slot = "RIGHT_TOP"
            order = 0
            visible = true
            mode = "float"
            window = { x = -1200, y = 40, width = 420, height = 610 }
            """, """

            [window.tool_windows.look."dev.jasper.enterprise".panels."dev.x.hosts"]
            slot = "BOTTOM_LEFT"
            """);
        assertThat(WorkspaceFile.read(file)).isEqualTo(new WorkspaceFile.Loaded.Restore(saved, Optional.empty()));
    }

    /** Workspace plan 6a Rulings 14: what stays of a window when only some of its tabs can stay, as closing the others would. */
    @Test void keepingLeavesWhatClosingTheOtherTabsWouldLeave() {
        // Shown in turn 3, 1 and 2: the one used before 2 is 1, while 3 is next to 2 in the bar.
        ParentTabs window = new ParentTabs(List.of(new ParentTab(parentTabId(1), single(1)), new ParentTab(parentTabId(2), single(2)),
            new ParentTab(parentTabId(3), single(3))), parentTabId(3)).select(parentTabId(1)).select(parentTabId(2));
        assertThat(WorkspaceFile.keeping(window, tab -> !tab.equals(tab(2)))).as("the shown one goes: the one used before it shows")
            .isEqualTo(new ParentTabs(List.of(new ParentTab(parentTabId(1), single(1)), new ParentTab(parentTabId(3), single(3))), parentTabId(1)));
        assertThat(WorkspaceFile.keeping(window, tab -> false)).as("every window keeps its shown parent tab, empty")
            .isEqualTo(ParentTabs.of(new ParentTab(parentTabId(2), single(2).remove(tab(2)))));
        Layout two = single(4).split(tab(5), groupId(4), Side.RIGHT);
        assertThat(WorkspaceFile.keeping(one(4, two), tab -> !tab.equals(tab(5)))).as("a window keeps the pane that stays")
            .isEqualTo(one(4, two.remove(tab(5))));
    }
}
