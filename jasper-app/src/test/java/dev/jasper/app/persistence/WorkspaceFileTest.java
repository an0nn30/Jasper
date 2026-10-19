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
import dev.jasper.app.layout.Split;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.persistence.WorkspaceFile.Role;
import dev.jasper.app.persistence.WorkspaceFile.Snapshot;
import dev.jasper.app.persistence.WorkspaceFile.TabRecord;
import dev.jasper.app.persistence.WorkspaceFile.WindowRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Workspace tabs spec 8: the workspace file's values and its exact text. */
class WorkspaceFileTest {
    @TempDir Path dir;

    static final UiState.Bounds BOUNDS = new UiState.Bounds(40, 60, 1400, 900);

    static TabId tab(int n) {
        return TabId.parse("00000000-0000-4000-8000-%012d".formatted(n));
    }

    static GroupId groupId(int n) {
        return GroupId.parse("00000000-0000-4000-9000-%012d".formatted(n));
    }

    /** Group {@code id} holding {@code tabs} in order, showing {@code selected}. */
    static Group group(int id, int selected, int... tabs) {
        return new Group(groupId(id), Arrays.stream(tabs).mapToObj(WorkspaceFileTest::tab).toList(), Optional.of(tab(selected)));
    }

    /**
     * Spec 8's example: a main window of two columns, the right one in two rows, its lower right group maximized, and a
     * child window without bounds. Listed child first: the snapshot puts the main window first.
     */
    static Snapshot snapshot() {
        Node root = new Split(Orientation.HORIZONTAL, List.of(new Child(group(1, 2, 1, 2), 0.5),
            new Child(new Split(Orientation.VERTICAL, List.of(new Child(group(2, 3, 3), 0.75), new Child(group(3, 4, 4), 0.25))), 0.5)));
        var main = new WindowRecord(Role.MAIN, Optional.of(BOUNDS), 6, new Layout(root, groupId(2), Optional.of(groupId(2))), Map.of(
            tab(1), new TabRecord.Terminal("build", "/Users/me/src/jasper", "zsh"),
            tab(2), new TabRecord.Content("README.md", "dev.jasper.editor", "document", Map.of("path", "/Users/me/src/jasper/README.md")),
            tab(3), new TabRecord.Terminal("", "C:\\work", ""),
            tab(4), new TabRecord.Content("Files \"prod\"", "dev.jasper.remote", "sftp", Map.of("remote path", "/srv/a", "host", "example"))));
        var child = new WindowRecord(Role.CHILD, Optional.empty(), 0, new Layout(group(4, 5, 5), groupId(4), Optional.empty()),
            Map.of(tab(5), new TabRecord.Terminal("", "/tmp", "")));
        return new Snapshot(List.of(child, main));
    }

    @Test void writesSpecEightsWorkspaceExactly() throws Exception {
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
            active_group = "00000000-0000-4000-9000-000000000002"
            maximized = "00000000-0000-4000-9000-000000000002"

            [[windows.nodes]]
            id = "e8b5eccb-037b-3d65-a6c6-5e4889602b31"
            kind = "split"
            orientation = "horizontal"
            parent = ""
            weight = 1.0

            [[windows.nodes]]
            id = "00000000-0000-4000-9000-000000000001"
            kind = "group"
            parent = "e8b5eccb-037b-3d65-a6c6-5e4889602b31"
            weight = 0.5
            tabs = ["00000000-0000-4000-8000-000000000001", "00000000-0000-4000-8000-000000000002"]
            selected = "00000000-0000-4000-8000-000000000002"

            [[windows.nodes]]
            id = "59943c66-5e92-39d1-a8ea-1f88b440a667"
            kind = "split"
            orientation = "vertical"
            parent = "e8b5eccb-037b-3d65-a6c6-5e4889602b31"
            weight = 0.5

            [[windows.nodes]]
            id = "00000000-0000-4000-9000-000000000002"
            kind = "group"
            parent = "59943c66-5e92-39d1-a8ea-1f88b440a667"
            weight = 0.75
            tabs = ["00000000-0000-4000-8000-000000000003"]
            selected = "00000000-0000-4000-8000-000000000003"

            [[windows.nodes]]
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
            role = "child"
            extended_state = 0
            active_group = "00000000-0000-4000-9000-000000000004"
            maximized = ""

            [[windows.nodes]]
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

    @Test void theEmptyMainWorkspaceWritesAGroupWithNoTabs() {
        Layout empty = new Layout(new Group(groupId(1), List.of(), Optional.empty()), groupId(1), Optional.empty());
        assertThat(WorkspaceFile.encode(new Snapshot(List.of(new WindowRecord(Role.MAIN, Optional.of(new UiState.Bounds(0, 0, 800, 600)), 0, empty, Map.of())))))
            .isEqualTo("""
                version = 1

                [[windows]]
                role = "main"
                x = 0
                y = 0
                width = 800
                height = 600
                extended_state = 0
                active_group = "00000000-0000-4000-9000-000000000001"
                maximized = ""

                [[windows.nodes]]
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
        Layout one = Layout.of(tab(1));
        Map<TabId, TabRecord> record = Map.of(tab(1), new TabRecord.Terminal("", "/tmp", ""));
        assertThatIllegalArgumentException().as("a tab without a record")
            .isThrownBy(() -> new WindowRecord(Role.MAIN, Optional.empty(), 0, one, Map.of()));
        assertThatIllegalArgumentException().as("a record without a tab")
            .isThrownBy(() -> new WindowRecord(Role.MAIN, Optional.empty(), 0, Layout.empty(), record));
        assertThatIllegalArgumentException().as("an empty child window")
            .isThrownBy(() -> new WindowRecord(Role.CHILD, Optional.empty(), 0, Layout.empty(), Map.of()));
        assertThatIllegalArgumentException().as("minimized is not kept")
            .isThrownBy(() -> new WindowRecord(Role.MAIN, Optional.empty(), 1, one, record));
        var main = new WindowRecord(Role.MAIN, Optional.empty(), 6, one, record);
        var child = new WindowRecord(Role.CHILD, Optional.empty(), 0, Layout.of(tab(2)), Map.of(tab(2), new TabRecord.Terminal("", "", "")));
        assertThat(new Snapshot(List.of(child, main)).windows()).as("the main window first").containsExactly(main, child);
        assertThat(new Snapshot(List.of(child, main)).main()).isEqualTo(main);
        assertThat(new Snapshot(List.of(child, main)).children()).containsExactly(child);
        assertThatIllegalArgumentException().as("no main window").isThrownBy(() -> new Snapshot(List.of(child)));
        assertThatIllegalArgumentException().as("two main windows").isThrownBy(() -> new Snapshot(List.of(main, main)));
        assertThatIllegalArgumentException().as("a tab in two windows")
            .isThrownBy(() -> new Snapshot(List.of(main, new WindowRecord(Role.CHILD, Optional.empty(), 0, Layout.of(tab(1)), record))));
        Layout sameGroup = new Layout(new Group(one.activeGroup(), List.of(tab(2)), Optional.of(tab(2))), one.activeGroup(), Optional.empty());
        assertThatIllegalArgumentException().as("a group in two windows").isThrownBy(() -> new Snapshot(List.of(main,
            new WindowRecord(Role.CHILD, Optional.empty(), 0, sameGroup, Map.of(tab(2), new TabRecord.Terminal("", "", ""))))));
        assertThatIllegalArgumentException().isThrownBy(() -> new TabRecord.Content("", "", "document", Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new TabRecord.Content("", "dev.x:y", "document", Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new TabRecord.Content("", "dev.x", "", Map.of()));
        assertThatNullPointerException().isThrownBy(() -> new TabRecord.Terminal(null, "", ""));
    }
}
