package dev.jasper.app.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

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
import dev.jasper.app.persistence.WorkspaceFile.Role;
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

/** Workspace tabs spec 8 and parent tabs spec 4 and 9: the workspace file's values and its exact text. */
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

    /** A main or full window's only parent tab, {@code id}, holding {@code layout}. */
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
     * Spec 8's example as the main window's one parent tab: two columns, each in two rows, the right column's upper pane
     * active and maximized; and a full window without bounds. Listed full window first: the snapshot puts the main
     * window first.
     */
    static Snapshot snapshot() {
        Node left = new Split(Orientation.VERTICAL, List.of(new Child(group(1, 1), 0.5), new Child(group(5, 2), 0.5)));
        Node root = new Split(Orientation.HORIZONTAL, List.of(new Child(left, 0.5),
            new Child(new Split(Orientation.VERTICAL, List.of(new Child(group(2, 3), 0.75), new Child(group(3, 4), 0.25))), 0.5)));
        var main = new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 6, one(1, new Layout(root, groupId(2), Optional.of(groupId(2)))), Map.of(
            tab(1), new TabRecord.Terminal("build", "/Users/me/src/jasper", "zsh"),
            tab(2), new TabRecord.Content("README.md", "dev.jasper.editor", "document", Map.of("path", "/Users/me/src/jasper/README.md")),
            tab(3), new TabRecord.Terminal("", "C:\\work", ""),
            tab(4), new TabRecord.Content("Files \"prod\"", "dev.jasper.remote", "sftp", Map.of("remote path", "/srv/a", "host", "example"))));
        var full = new WindowRecord(Role.CHILD, Optional.empty(), 0, one(5, new Layout(group(4, 5), groupId(4), Optional.empty())),
            Map.of(tab(5), new TabRecord.Terminal("", "/tmp", "")));
        return new Snapshot(List.of(full, main));
    }

    /** A main window of two parent tabs, the second showing, and a full window of two, the first showing (parent tabs spec 9). */
    static Snapshot parentTabs() {
        var main = new ParentTabs(List.of(new ParentTab(parentTabId(1), single(1)), new ParentTab(parentTabId(2), single(2))), parentTabId(2));
        var full = new ParentTabs(List.of(new ParentTab(parentTabId(3), single(3)), new ParentTab(parentTabId(4), single(4))), parentTabId(3));
        return new Snapshot(List.of(new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 0, main, terminals(main)),
            new WindowRecord(Role.CHILD, Optional.empty(), 0, full, terminals(full))));
    }

    @Test void writesSpecEightsWorkspaceWithAFullWindowExactly() throws Exception {
        Path file = dir.resolve("nested/workspace.toml");
        WorkspaceFile.write(file, snapshot());
        assertThat(Files.readString(file)).isEqualTo("""
            version = 1

            [[windows]]
            role = "main"
            x = 40
            y = 60
            width = 1400
            height = 900
            extended_state = 6
            active_parent_tab = "00000000-0000-4000-a000-000000000001"

            [[windows.parent_tabs]]
            id = "00000000-0000-4000-a000-000000000001"
            active_group = "00000000-0000-4000-9000-000000000002"
            maximized = "00000000-0000-4000-9000-000000000002"

            [[windows.parent_tabs.nodes]]
            id = "e8b5eccb-037b-3d65-a6c6-5e4889602b31"
            kind = "split"
            orientation = "horizontal"
            parent = ""
            weight = 1.0

            [[windows.parent_tabs.nodes]]
            id = "92cc2645-e802-3d4b-aa67-3432c68ad0b2"
            kind = "split"
            orientation = "vertical"
            parent = "e8b5eccb-037b-3d65-a6c6-5e4889602b31"
            weight = 0.5

            [[windows.parent_tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000001"
            kind = "group"
            parent = "92cc2645-e802-3d4b-aa67-3432c68ad0b2"
            weight = 0.5
            tabs = ["00000000-0000-4000-8000-000000000001"]
            selected = "00000000-0000-4000-8000-000000000001"

            [[windows.parent_tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000005"
            kind = "group"
            parent = "92cc2645-e802-3d4b-aa67-3432c68ad0b2"
            weight = 0.5
            tabs = ["00000000-0000-4000-8000-000000000002"]
            selected = "00000000-0000-4000-8000-000000000002"

            [[windows.parent_tabs.nodes]]
            id = "59943c66-5e92-39d1-a8ea-1f88b440a667"
            kind = "split"
            orientation = "vertical"
            parent = "e8b5eccb-037b-3d65-a6c6-5e4889602b31"
            weight = 0.5

            [[windows.parent_tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000002"
            kind = "group"
            parent = "59943c66-5e92-39d1-a8ea-1f88b440a667"
            weight = 0.75
            tabs = ["00000000-0000-4000-8000-000000000003"]
            selected = "00000000-0000-4000-8000-000000000003"

            [[windows.parent_tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000003"
            kind = "group"
            parent = "59943c66-5e92-39d1-a8ea-1f88b440a667"
            weight = 0.25
            tabs = ["00000000-0000-4000-8000-000000000004"]
            selected = "00000000-0000-4000-8000-000000000004"

            [[windows.tabs]]
            id = "00000000-0000-4000-8000-000000000001"
            kind = "terminal"
            title = "build"
            directory = "/Users/me/src/jasper"
            profile = "zsh"

            [[windows.tabs]]
            id = "00000000-0000-4000-8000-000000000002"
            kind = "content:dev.jasper.editor:document"
            title = "README.md"
            state = { path = "/Users/me/src/jasper/README.md" }

            [[windows.tabs]]
            id = "00000000-0000-4000-8000-000000000003"
            kind = "terminal"
            title = ""
            directory = "C:\\\\work"
            profile = ""

            [[windows.tabs]]
            id = "00000000-0000-4000-8000-000000000004"
            kind = "content:dev.jasper.remote:sftp"
            title = "Files \\"prod\\""
            state = { host = "example", "remote path" = "/srv/a" }

            [[windows]]
            role = "full"
            extended_state = 0
            active_parent_tab = "00000000-0000-4000-a000-000000000005"

            [[windows.parent_tabs]]
            id = "00000000-0000-4000-a000-000000000005"
            active_group = "00000000-0000-4000-9000-000000000004"
            maximized = ""

            [[windows.parent_tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000004"
            kind = "group"
            parent = ""
            weight = 1.0
            tabs = ["00000000-0000-4000-8000-000000000005"]
            selected = "00000000-0000-4000-8000-000000000005"

            [[windows.tabs]]
            id = "00000000-0000-4000-8000-000000000005"
            kind = "terminal"
            title = ""
            directory = "/tmp"
            profile = ""
            """);
        try (var listing = Files.list(file.getParent())) {
            assertThat(listing).as("no temporary file is left").containsExactly(file);
        }
    }

    @Test void writesEveryParentTabOfTheMainWindowAndAFullWindowInBarOrder() {
        assertThat(WorkspaceFile.encode(parentTabs())).isEqualTo("""
            version = 1

            [[windows]]
            role = "main"
            x = 40
            y = 60
            width = 1400
            height = 900
            extended_state = 0
            active_parent_tab = "00000000-0000-4000-a000-000000000002"

            [[windows.parent_tabs]]
            id = "00000000-0000-4000-a000-000000000001"
            active_group = "00000000-0000-4000-9000-000000000001"
            maximized = ""

            [[windows.parent_tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000001"
            kind = "group"
            parent = ""
            weight = 1.0
            tabs = ["00000000-0000-4000-8000-000000000001"]
            selected = "00000000-0000-4000-8000-000000000001"

            [[windows.parent_tabs]]
            id = "00000000-0000-4000-a000-000000000002"
            active_group = "00000000-0000-4000-9000-000000000002"
            maximized = ""

            [[windows.parent_tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000002"
            kind = "group"
            parent = ""
            weight = 1.0
            tabs = ["00000000-0000-4000-8000-000000000002"]
            selected = "00000000-0000-4000-8000-000000000002"

            [[windows.tabs]]
            id = "00000000-0000-4000-8000-000000000001"
            kind = "terminal"
            title = ""
            directory = "/tmp"
            profile = ""

            [[windows.tabs]]
            id = "00000000-0000-4000-8000-000000000002"
            kind = "terminal"
            title = ""
            directory = "/tmp"
            profile = ""

            [[windows]]
            role = "full"
            extended_state = 0
            active_parent_tab = "00000000-0000-4000-a000-000000000003"

            [[windows.parent_tabs]]
            id = "00000000-0000-4000-a000-000000000003"
            active_group = "00000000-0000-4000-9000-000000000003"
            maximized = ""

            [[windows.parent_tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000003"
            kind = "group"
            parent = ""
            weight = 1.0
            tabs = ["00000000-0000-4000-8000-000000000003"]
            selected = "00000000-0000-4000-8000-000000000003"

            [[windows.parent_tabs]]
            id = "00000000-0000-4000-a000-000000000004"
            active_group = "00000000-0000-4000-9000-000000000004"
            maximized = ""

            [[windows.parent_tabs.nodes]]
            id = "00000000-0000-4000-9000-000000000004"
            kind = "group"
            parent = ""
            weight = 1.0
            tabs = ["00000000-0000-4000-8000-000000000004"]
            selected = "00000000-0000-4000-8000-000000000004"

            [[windows.tabs]]
            id = "00000000-0000-4000-8000-000000000003"
            kind = "terminal"
            title = ""
            directory = "/tmp"
            profile = ""

            [[windows.tabs]]
            id = "00000000-0000-4000-8000-000000000004"
            kind = "terminal"
            title = ""
            directory = "/tmp"
            profile = ""
            """);
    }

    @Test void theEmptyMainWorkspaceWritesItsParentTabWithAGroupWithNoTabs() {
        Layout empty = new Layout(new Group(groupId(1), List.of(), Optional.empty()), groupId(1), Optional.empty());
        assertThat(WorkspaceFile.encode(new Snapshot(List.of(new WindowRecord(Role.MAIN, Optional.of(new UiState.Bounds(0, 0, 800, 600)), 0,
            one(1, empty), Map.of())))))
            .isEqualTo("""
                version = 1

                [[windows]]
                role = "main"
                x = 0
                y = 0
                width = 800
                height = 600
                extended_state = 0
                active_parent_tab = "00000000-0000-4000-a000-000000000001"

                [[windows.parent_tabs]]
                id = "00000000-0000-4000-a000-000000000001"
                active_group = "00000000-0000-4000-9000-000000000001"
                maximized = ""

                [[windows.parent_tabs.nodes]]
                id = "00000000-0000-4000-9000-000000000001"
                kind = "group"
                parent = ""
                weight = 1.0
                tabs = []
                selected = ""
                """);
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
            .isThrownBy(() -> new WindowRecord(Role.MAIN, Optional.empty(), 0, held, Map.of()));
        assertThatIllegalArgumentException().as("a record without a tab")
            .isThrownBy(() -> new WindowRecord(Role.MAIN, Optional.empty(), 0, one(1, Layout.empty()), record));
        assertThatIllegalArgumentException().as("an empty full window")
            .isThrownBy(() -> new WindowRecord(Role.CHILD, Optional.empty(), 0, one(2, Layout.empty()), Map.of()));
        assertThatIllegalArgumentException().as("minimized is not kept")
            .isThrownBy(() -> new WindowRecord(Role.MAIN, Optional.empty(), 1, held, record));
        var main = new WindowRecord(Role.MAIN, Optional.empty(), 6, held, record);
        var child = new WindowRecord(Role.CHILD, Optional.empty(), 0, one(2, Layout.of(tab(2))), Map.of(tab(2), new TabRecord.Terminal("", "", "")));
        var other = new WindowRecord(Role.CHILD, Optional.empty(), 0, one(3, Layout.of(tab(3))),
            Map.of(tab(3), new TabRecord.Terminal("", "", "")));
        assertThat(new Snapshot(List.of(other, child, main)).windows()).as("the main window first").containsExactly(main, other, child);
        assertThat(new Snapshot(List.of(child, main)).main()).isEqualTo(main);
        assertThat(new Snapshot(List.of(child, main, other)).children()).containsExactly(child, other);
        assertThatIllegalArgumentException().as("no main window").isThrownBy(() -> new Snapshot(List.of(child)));
        assertThatIllegalArgumentException().as("two main windows").isThrownBy(() -> new Snapshot(List.of(main, main)));
        assertThatIllegalArgumentException().as("a tab in two windows")
            .isThrownBy(() -> new Snapshot(List.of(main, new WindowRecord(Role.CHILD, Optional.empty(), 0, one(2, Layout.of(tab(1))), record))));
        Layout sameGroup = new Layout(new Group(held.activeParentTab().layout().activeGroup(), List.of(tab(2)), Optional.of(tab(2))),
            held.activeParentTab().layout().activeGroup(), Optional.empty());
        assertThatIllegalArgumentException().as("a group in two windows").isThrownBy(() -> new Snapshot(List.of(main,
            new WindowRecord(Role.CHILD, Optional.empty(), 0, one(4, sameGroup), Map.of(tab(2), new TabRecord.Terminal("", "", ""))))));
        assertThatIllegalArgumentException().as("a parent tab in two windows (workspace plan 7b Rulings 10)").isThrownBy(() -> new Snapshot(List.of(main,
            new WindowRecord(Role.CHILD, Optional.empty(), 0, one(1, Layout.of(tab(2))), Map.of(tab(2), new TabRecord.Terminal("", "", ""))))));
        assertThatIllegalArgumentException().isThrownBy(() -> new TabRecord.Content("", "", "document", Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new TabRecord.Content("", "dev.x:y", "document", Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new TabRecord.Content("", "dev.x", "", Map.of()));
        assertThatNullPointerException().isThrownBy(() -> new TabRecord.Terminal(null, "", ""));
    }

    /** Workspace plan 6a Rulings 14: what stays of a window when only some of its tabs can stay, as closing the others would. */
    @Test void keepingLeavesWhatClosingTheOtherTabsWouldLeave() {
        // Shown in turn 3, 1 and 2: the one used before 2 is 1, while 3 is next to 2 in the bar.
        ParentTabs window = new ParentTabs(List.of(new ParentTab(parentTabId(1), single(1)), new ParentTab(parentTabId(2), single(2)),
            new ParentTab(parentTabId(3), single(3))), parentTabId(3)).select(parentTabId(1)).select(parentTabId(2));
        assertThat(WorkspaceFile.keeping(window, tab -> !tab.equals(tab(2)), false)).as("the shown one goes: the one used before it shows")
            .contains(new ParentTabs(List.of(new ParentTab(parentTabId(1), single(1)), new ParentTab(parentTabId(3), single(3))), parentTabId(1)));
        assertThat(WorkspaceFile.keeping(window, tab -> false, true)).as("the main window keeps its shown parent tab, empty")
            .contains(ParentTabs.of(new ParentTab(parentTabId(2), single(2).remove(tab(2)))));
        assertThat(WorkspaceFile.keeping(window, tab -> false, false)).as("a full window keeps nothing").isEmpty();
        Layout two = single(4).split(tab(5), groupId(4), Side.RIGHT);
        assertThat(WorkspaceFile.keeping(one(4, two), tab -> !tab.equals(tab(5)), false)).as("a full window keeps the pane that stays")
            .contains(one(4, two.remove(tab(5))));
    }
}
