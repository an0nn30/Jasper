package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.ui.transfers.TransferRows;
import dev.jasper.remote.ui.transfers.TransferStrip;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Plan 3 Task 6: the SFTP panel's composition on the testkit fake: the disconnected full-body empty state with the
 * Choose Session menu, the persistent footer whose Follow terminal folder row stays last, and the connected browser with
 * only its navigation row above the table.
 */
class SftpCompositionTest {
    final List<String> commands = new ArrayList<>();
    final List<Boolean> followed = new ArrayList<>();
    final RemoteHost amp = RemoteHost.create("amp-amp", "100.110.65.45", 22, "amp", Auth.AGENT, "", Optional.empty());
    final RemoteHost pve = RemoteHost.create("<b>pve</b>", "100.73.226.91", 22, "root", Auth.AGENT, "", Optional.empty());

    SftpPanel.Actions actions() {
        return new SftpPanel.Actions(path -> commands.add("navigate " + path), () -> { }, () -> commands.add("refresh"), followed::add,
            () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> { }, () -> commands.add("cancel"), (page, order) -> { }, shown -> { });
    }

    static Nodes.Panel root(SftpPanel panel) { return (Nodes.Panel) panel.view().spec().root(); }
    static Nodes.Stack footer(SftpPanel panel) { return (Nodes.Stack) root(panel).footer().orElseThrow(); }

    @Test void disconnectedTheChooseSessionMenuListsSavedHostsAndDispatchesTheChosenOne() throws Exception {
        edt(() -> {
            var panel = new SftpPanel(new FakeKit("dev.jasper.remote"));
            var chosen = new ArrayList<RemoteHost>();
            panel.onChooseSession(chosen::add);
            panel.sessions(List.of(amp, pve));
            var body = root(panel).body();
            assertThat(body).as("the empty state is the body, centered in it").isInstanceOf(Nodes.Empty.class);
            var choose = node(panel.view(), SftpPanel.CHOOSE, Nodes.MenuButton.class);
            assertThat(choose.label()).isEqualTo("Choose Session");
            assertThat(choose.icon()).as("no leading glyph: the menu chevron follows the label").isEmpty();
            assertThat(choose.action()).as("menu-only").isEmpty();
            assertThat(choose.items()).extracting(MenuItem::label).containsExactly("amp-amp", "<b>pve</b>");
            assertThat(choose.items()).extracting(MenuItem::icon).containsOnly("server");
            press(panel.view(), choose.items().get(1).action());
            assertThat(chosen).containsExactly(pve);
            panel.busy(true, "Connecting…");
            assertThat(node(panel.view(), SftpPanel.CHOOSE, Nodes.MenuButton.class).state().enabled()).isFalse();
            press(panel.view(), choose.items().getFirst().action());
            assertThat(chosen).as("nothing is chosen while one connects").containsExactly(pve);
        });
    }

    @Test void theFollowRowStaysLastInThePersistentFooterAndWorksWhileDisconnected() throws Exception {
        edt(() -> {
            var panel = new SftpPanel(new FakeKit("dev.jasper.remote"));
            panel.actions(actions());
            assertThat(footer(panel).role()).isEqualTo(Nodes.LayoutRole.VERTICAL);
            assertThat(footer(panel).children().getLast()).isInstanceOfSatisfying(Nodes.SettingRow.class, row -> {
                assertThat(row.title()).isEqualTo("Follow terminal folder");
                assertThat(row.control()).isInstanceOfSatisfying(Nodes.Toggle.class, toggle -> assertThat(toggle.compact()).isTrue());
            });
            panel.busy(false, "Cancelled");
            assertThat(footer(panel).children()).extracting(Nodes.Node::id).as("the status line sits above the persistent row")
                .containsSubsequence(SftpPanel.STATUS, SftpPanel.FOLLOW_ROW);
            assertThat(node(panel.view(), SftpPanel.MESSAGE, Nodes.Text.class).text()).isEqualTo("Cancelled");
            assertThat(node(panel.view(), SftpPanel.MESSAGE, Nodes.Text.class).role()).as("body text, as bright as the row below it").isEqualTo(Nodes.TextRole.BODY);
            emit(panel.view(), new UiEvent.Toggled(SftpPanel.FOLLOW, false));
            assertThat(followed).containsExactly(false);
            var strip = new TransferStrip(new TransferStrip.Actions((id, action) -> { }, id -> { }, id -> { }));
            panel.transfers(strip);
            strip.rows(List.of(new TransferRows.Row(UUID.randomUUID(), "↑", "a.txt → amp-amp:/srv", "tip", "Queued", OptionalDouble.empty(), false, Optional.empty(), false, false)));
            assertThat(footer(panel).children().getFirst().id()).as("transfers lead the footer").isEqualTo(TransferStrip.ROOT);
            assertThat(footer(panel).children().getLast().id()).as("the follow row stays last").isEqualTo(SftpPanel.FOLLOW_ROW);
        });
    }

    @Test void connectedTheBrowserHasOnlyItsNavigationRowAboveTheTable() throws Exception {
        edt(() -> {
            var panel = new SftpPanel(new FakeKit("dev.jasper.remote"));
            panel.actions(actions());
            panel.showPage("amp-amp", "/srv/app", List.of(new FileEntry("logs", FileEntry.Kind.DIRECTORY, 0, 1000, 0755, "")), 0, 1, false);
            var root = root(panel);
            assertThat(root.body()).isInstanceOf(Nodes.Table.class);
            assertThat(root.searchOrNavigation()).get().isInstanceOfSatisfying(Nodes.Stack.class, navigation -> {
                assertThat(navigation.id()).isEqualTo(SftpPanel.NAVIGATION);
                assertThat(navigation.children()).extracting(Nodes.Node::id).containsExactly(SftpPanel.TOOLS, SftpPanel.PATH);
            });
            assertThat(find(panel.view(), SftpPanel.CHOOSE)).isEmpty();
            press(panel.view(), "sftp.refresh");
            assertThat(commands).containsExactly("refresh");
            assertThat(footer(panel).children().getLast().id()).isEqualTo(SftpPanel.FOLLOW_ROW);
        });
    }
}
