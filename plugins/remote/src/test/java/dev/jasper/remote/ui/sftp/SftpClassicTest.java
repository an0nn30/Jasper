package dev.jasper.remote.ui.sftp;

import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.workstation.Layout;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.Option;
import dev.jasper.sdk.ui.UiEvent;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 14.4: the classic SFTP navigation row, its disconnected state and the Modified column. */
class SftpClassicTest {
    final RemoteHost mac = RemoteHost.create("Mac", "192.168.1.119", 22, "dustin", Auth.AGENT, "", Optional.empty());

    SftpPanel panel() { return new SftpPanel(new FakeKit("dev.jasper.remote"), Layout.CLASSIC); }

    @Test void disconnectedWithoutHostsIsInert() throws Exception {
        edt(() -> {
            var panel = panel();
            var choose = node(panel.view(), SftpPanel.CHOOSE_DROPDOWN, Nodes.Choice.class);
            assertThat(choose.options()).extracting(Option::label).containsExactly("No saved sessions");
            assertThat(choose.state().enabled()).isFalse();
            var crumbs = node(panel.view(), SftpPanel.PATH, Nodes.Breadcrumbs.class);
            assertThat(crumbs.crumbs()).extracting(crumb -> crumb.label()).containsExactly("/");
            assertThat(crumbs.state().enabled()).isFalse();
            var table = node(panel.view(), SftpPanel.FILES, Nodes.Table.class);
            assertThat(table.columns()).extracting(column -> column.title()).containsExactly("Name", "Size", "Modified");
            assertThat(table.rows()).isEmpty();
            assertThat(table.empty()).hasValueSatisfying(empty -> assertThat(empty.title()).isEqualTo("Not Connected"));
            assertThat(find(panel.view(), SftpPanel.BACK)).as("no navigation tools while disconnected").isEmpty();
        });
    }

    @Test void chooseSessionBrowsesTheHostAndReturnsToItsPrompt() throws Exception {
        edt(() -> {
            var panel = panel();
            var chosen = new ArrayList<String>();
            panel.onChooseSession(host -> chosen.add(host.name()));
            panel.sessions(List.of(mac));
            var choose = node(panel.view(), SftpPanel.CHOOSE_DROPDOWN, Nodes.Choice.class);
            assertThat(choose.options()).extracting(Option::label).containsExactly("Choose Session…", "Mac");
            assertThat(choose.selectedId()).isEqualTo("choose");
            emit(panel.view(), new UiEvent.Selected(SftpPanel.CHOOSE_DROPDOWN, Set.of("session:" + mac.id())));
            assertThat(chosen).containsExactly("Mac");
            assertThat(node(panel.view(), SftpPanel.CHOOSE_DROPDOWN, Nodes.Choice.class).selectedId()).isEqualTo("choose");
        });
    }

    @Test void aListingShowsTheClassicRowAndTheModifiedColumn() throws Exception {
        edt(() -> {
            var panel = panel();
            long september = ZonedDateTime.of(2026, 9, 28, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli();
            panel.showPage("Mac", "/Users/dustin", List.of(new FileEntry("Applications", FileEntry.Kind.DIRECTORY, 0, september, 0755, "")), 0, 1, false);
            var tools = node(panel.view(), SftpPanel.TOOLS, Nodes.Stack.class);
            assertThat(tools.children()).extracting(node -> node.id()).containsExactly(SftpPanel.BACK, SftpPanel.FORWARD, SftpPanel.REFRESH,
                SftpPanel.TOOLS_SEPARATOR, SftpPanel.HIDDEN, SftpPanel.UPLOAD_SEPARATOR, SftpPanel.UPLOAD);
            assertThat(node(panel.view(), SftpPanel.HIDDEN, Nodes.Button.class).label()).isEqualTo("Hidden files");
            assertThat(node(panel.view(), SftpPanel.UPLOAD, Nodes.MenuButton.class).label()).isEqualTo("Upload");
            var table = node(panel.view(), SftpPanel.FILES, Nodes.Table.class);
            assertThat(table.rows().getFirst().cells()).containsEntry("modified", "Sep 28");
            assertThat(table.empty()).isEmpty();
            assertThat(find(panel.view(), SftpPanel.CHOOSE_DROPDOWN)).isEmpty();
        });
    }
}
