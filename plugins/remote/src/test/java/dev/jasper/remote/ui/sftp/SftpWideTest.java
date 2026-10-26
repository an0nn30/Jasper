package dev.jasper.remote.ui.sftp;

import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.workstation.Layout;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Column;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Wide tool window layouts, SFTP: in a window of its own or docked at the bottom the table adds Modified (date and time)
 * and Permissions columns in both looks, keeping the tools, breadcrumbs, footer and the selection; the Copy to host
 * browser never widens.
 */
class SftpWideTest {
    static final long MODIFIED = ZonedDateTime.of(2026, 9, 28, 14, 5, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli();
    static final FileEntry SRC = new FileEntry("src", FileEntry.Kind.DIRECTORY, 0, MODIFIED, 0755, ""),
        FILE = new FileEntry("a.txt", FileEntry.Kind.FILE, 1200, MODIFIED, 0644, "");

    static SftpPanel panel(Layout layout) { return new SftpPanel(new FakeKit("dev.jasper.remote"), layout); }

    static void list(SftpPanel panel) { panel.showPage("prod", "/srv", List.of(SRC, FILE), 0, 2, false); }

    static Nodes.Table table(SftpPanel panel) { return node(panel.view(), SftpPanel.FILES, Nodes.Table.class); }

    static List<String> titles(Nodes.Table table) { return table.columns().stream().map(Column::title).toList(); }

    static List<String> ids(Nodes.Table table) { return table.columns().stream().map(Column::id).toList(); }

    @Test void permissionsReadAsLsDoes() {
        assertThat(SftpPanel.permissions(new FileEntry("a", FileEntry.Kind.FILE, 1, 0, 0755, ""))).isEqualTo("rwxr-xr-x");
        assertThat(SftpPanel.permissions(new FileEntry("a", FileEntry.Kind.FILE, 1, 0, 0640, ""))).isEqualTo("rw-r-----");
        assertThat(SftpPanel.permissions(new FileEntry("a", FileEntry.Kind.FILE, 1, 0, 0, ""))).isEqualTo("---------");
        assertThat(SftpPanel.permissions(new FileEntry("a", FileEntry.Kind.FILE, 1, 0, -1, ""))).isEqualTo("—");
    }

    @Test void wideTheTableShowsModifiedAndPermissions() throws Exception {
        edt(() -> {
            var panel = panel(Layout.ROWS);
            list(panel);
            assertThat(titles(table(panel))).containsExactly("Name", "Size");
            assertThat(panel.wide()).isFalse();
            selectFile(panel);
            panel.wide(true);
            assertThat(panel.wide()).isTrue();
            var wide = table(panel);
            assertThat(titles(wide)).as("Size last, at the trailing edge as docked").containsExactly("Name", "Modified", "Permissions", "Size");
            assertThat(ids(wide)).containsExactly("name", "modified", "permissions", "size");
            assertThat(wide.columns().get(3).numeric()).as("the last column is the right-aligned Size").isTrue();
            var cells = wide.rows().stream().filter(row -> row.id().equals(SftpPanel.ROW + "a.txt")).findFirst().orElseThrow().cells();
            assertThat(cells).containsEntry("modified", "2026-09-28 14:05").containsEntry("permissions", "rw-r--r--").containsEntry("size", SftpPanel.formatSize(FILE));
            assertThat(wide.selectedIds()).as("the selection survives the switch").containsExactly(SftpPanel.ROW + "a.txt");
            panel.wide(false);
            assertThat(titles(table(panel))).containsExactly("Name", "Size");
            assertThat(table(panel).selectedIds()).containsExactly(SftpPanel.ROW + "a.txt");
        });
    }

    private static void selectFile(SftpPanel panel) {
        emit(panel.view(), new UiEvent.Selected(SftpPanel.FILES, Set.of(SftpPanel.ROW + "a.txt")));
    }

    @Test void classicWideAddsPermissionsAndTheFullModifiedTime() throws Exception {
        edt(() -> {
            var panel = panel(Layout.CLASSIC);
            list(panel);
            assertThat(titles(table(panel))).containsExactly("Name", "Size", "Modified");
            assertThat(table(panel).rows().get(1).cells()).containsEntry("modified", SftpPanel.shortModified(FILE));
            panel.wide(true);
            var wide = table(panel);
            assertThat(titles(wide)).containsExactly("Name", "Size", "Modified", "Permissions");
            assertThat(wide.rows().get(1).cells()).containsEntry("modified", SftpPanel.modified(FILE)).containsEntry("permissions", "rw-r--r--");
            panel.wide(false);
            assertThat(titles(table(panel))).containsExactly("Name", "Size", "Modified");
            assertThat(table(panel).rows().get(1).cells()).containsEntry("modified", SftpPanel.shortModified(FILE));
        });
    }

    @Test void theCopyToHostBrowserNeverWidens() throws Exception {
        edt(() -> {
            var picker = SftpPanel.destination(() -> { }, id -> { });
            picker.wide(true);
            picker.showPage("prod", "/srv", List.of(SRC, FILE), 0, 2, false);
            var table = (Nodes.Table) find(picker.destinationBody(), SftpPanel.FILES).orElseThrow();
            assertThat(titles(table)).containsExactly("Name", "Size");
        });
    }
}
