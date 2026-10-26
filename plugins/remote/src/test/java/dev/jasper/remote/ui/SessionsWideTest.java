package dev.jasper.remote.ui;

import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.workstation.Layout;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Wide tool window layouts, Sessions: in a window of its own or docked at the bottom the list sits beside the selected
 * row's detail (the default look's fields and actions, the classic look's tree beside its Properties), and with nothing
 * listed the wide layout is the docked one.
 */
class SessionsWideTest {
    final List<String> events = new ArrayList<>();
    final HostsPanel.Actions actions = new HostsPanel.Actions(h -> events.add("connect " + h.name()), h -> events.add("split " + h.name()),
        h -> events.add("edit " + h.map(RemoteHost::name).orElse("new")), h -> events.add("duplicate " + h.name()), h -> events.add("delete " + h.name()),
        (h, favorite) -> events.add("favorite " + h.name() + " " + favorite), () -> events.add("import"));
    final RemoteHost mac = RemoteHost.create("Mac", "192.168.1.119", 22, "dustin", Auth.AGENT, "Local Network", Optional.empty());
    final RemoteHost minecraft = RemoteHost.create("minecraft-local", "192.168.1.124", 22, "dustin", new Auth.Vault(UUID.randomUUID()),
        "Local Network", Optional.empty()).withFavorite(true);
    final ShellProfileInfo ubuntu = new ShellProfileInfo("wsl:Ubuntu", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.of("WSL 2"));

    HostsPanel panel(Layout layout) {
        var panel = new HostsPanel(new FakeKit("dev.jasper.remote"), actions, () -> 0, 500_000_000L, layout);
        panel.setCredentialLabels(host -> "SSH agent");
        panel.setHosts(List.of(mac, minecraft), Optional.empty());
        return panel;
    }

    static Nodes.Panel root(HostsPanel panel) { return (Nodes.Panel) panel.view().spec().root(); }

    static Nodes.Split columns(HostsPanel panel) { return (Nodes.Split) root(panel).body(); }

    static String text(HostsPanel panel, String id) { return node(panel.view(), id, Nodes.Text.class).text(); }

    static String label(HostsPanel panel, String id) { return node(panel.view(), id, Nodes.Button.class).label(); }

    /** The detail card's rows as caption to value text. */
    static List<Nodes.Text> captions(HostsPanel panel) { return fieldTexts(panel, 0); }

    static List<Nodes.Text> values(HostsPanel panel) { return fieldTexts(panel, 1); }

    private static List<Nodes.Text> fieldTexts(HostsPanel panel, int index) {
        var fields = node(panel.view(), HostsPanel.DETAIL_FIELDS, Nodes.Card.class);
        assertThat(fields.kind()).isEqualTo(Nodes.CardKind.FORM);
        return fields.children().stream().map(row -> (Nodes.Text) ((Nodes.Stack) row).children().get(index)).toList();
    }

    @Test void wideIsTheListBesideTheSelectedHostsDetail() throws Exception {
        edt(() -> {
            var panel = panel(Layout.ROWS);
            panel.wide(true);
            assertThat(panel.wide()).isTrue();
            var root = root(panel);
            assertThat(root.id()).isEqualTo(HostsPanel.ROOT);
            assertThat(root.searchOrNavigation()).as("the root has no search row: the list column has it").isEmpty();
            var split = columns(panel);
            assertThat(split.kind()).isEqualTo(Nodes.SplitKind.SIDEBAR_DETAIL);
            assertThat(split.id()).isEqualTo(HostsPanel.COLUMNS);
            assertThat(split.children()).extracting(Nodes.Node::id).containsExactly(HostsPanel.LIST_COLUMN, HostsPanel.DETAIL_COLUMN);
            var list = (Nodes.Panel) split.children().getFirst();
            assertThat(list.searchOrNavigation()).hasValueSatisfying(search -> assertThat(search.id()).isEqualTo(HostsPanel.SEARCH));
            assertThat(list.body()).isInstanceOf(Nodes.ItemList.class);
            assertThat(list.body().id()).isEqualTo(HostsPanel.ROWS);
            assertThat(list.header().state().visible()).as("the column's header is hidden").isFalse();
            assertThat(list.header().count()).as("the same count as the docked header").isEqualTo(OptionalInt.of(2));
            assertThat(find(panel.view(), HostsPanel.CARD)).as("no card beside the list").isEmpty();
            assertThat(text(panel, HostsPanel.CARD_NAME)).isEqualTo("Mac");
            assertThat(text(panel, HostsPanel.CARD_ADDRESS)).isEqualTo(mac.label());
            assertThat(captions(panel)).extracting(Nodes.Text::text).containsExactly("Host", "User", "Port", "Credentials", "Group", "Sessions");
            assertThat(values(panel)).extracting(Nodes.Text::text).containsExactly("192.168.1.119", "dustin", "22", "SSH agent", "Local Network", "Not connected");
            assertThat(label(panel, HostsPanel.CONNECT)).isEqualTo("Connect");
            assertThat(label(panel, HostsPanel.EDIT)).isEqualTo("Edit…");
            assertThat(label(panel, HostsPanel.SPLIT_BUTTON)).isEqualTo("Connect in Split");
            assertThat(find(panel.view(), HostsPanel.BROWSE_BUTTON)).as("no browsing wired").isEmpty();
            press(panel.view(), HostsPanel.CONNECT);
            press(panel.view(), node(panel.view(), HostsPanel.SPLIT_BUTTON, Nodes.Button.class).action());
            assertThat(events).containsExactly("connect Mac", "split Mac");
        });
    }

    @Test void theDetailBrowsesFilesWhileBrowsingIsWiredAndActsOnTheSelectedRow() throws Exception {
        edt(() -> {
            var panel = panel(Layout.ROWS);
            var browsed = new ArrayList<String>();
            panel.onBrowseFiles(host -> browsed.add(host.name()));
            panel.wide(true);
            panel.select(minecraft.id());
            assertThat(label(panel, HostsPanel.BROWSE_BUTTON)).isEqualTo("Browse Files");
            press(panel.view(), node(panel.view(), HostsPanel.BROWSE_BUTTON, Nodes.Button.class).action());
            press(panel.view(), HostsPanel.EDIT);
            assertThat(browsed).containsExactly("minecraft-local");
            assertThat(events).containsExactly("edit minecraft-local");
        });
    }

    @Test void aConnectedHostsDetailOpensItsShellAndALockedVaultWarns() throws Exception {
        edt(() -> {
            var panel = panel(Layout.ROWS);
            panel.setHostDetails(host -> "Ubuntu 24.04", host -> 2);
            panel.setCredentialLabels(host -> "Vault locked");
            panel.wide(true);
            assertThat(label(panel, HostsPanel.CONNECT)).isEqualTo("Open Shell");
            assertThat(captions(panel)).extracting(Nodes.Text::text).containsExactly("Host", "User", "Port", "Credentials", "Group", "System", "Sessions");
            var values = values(panel);
            assertThat(values).extracting(Nodes.Text::text).contains("Ubuntu 24.04").endsWith("2 sessions");
            assertThat(values.get(3).text()).isEqualTo("Vault locked");
            assertThat(values.get(3).severity()).contains(Nodes.Severity.WARNING);
            assertThat(values.get(0).severity()).as("only a locked vault warns").isEmpty();
            panel.setHostDetails(host -> "", host -> 1);
            assertThat(values(panel).getLast().text()).isEqualTo("1 session");
        });
    }

    @Test void aJumpHostAndTheDefaultGroupAreFieldsToo() throws Exception {
        edt(() -> {
            var loose = RemoteHost.create("nas", "nas.local", 2222, "me", Auth.AGENT, "", Optional.of(mac.id()));
            var panel = panel(Layout.ROWS);
            panel.setHosts(List.of(mac, loose), Optional.empty());
            panel.setDefaultGroup("Other");
            panel.select(loose.id());
            panel.wide(true);
            assertThat(captions(panel)).extracting(Nodes.Text::text).containsExactly("Host", "User", "Port", "Credentials", "Jump host", "Group", "Sessions");
            assertThat(values(panel)).extracting(Nodes.Text::text).containsExactly("nas.local", "me", "2222", "SSH agent", "Mac", "Other", "Not connected");
        });
    }

    @Test void aDistributionsDetailOpensIt() throws Exception {
        edt(() -> {
            var panel = panel(Layout.ROWS);
            panel.onDistros(profile -> events.add("open " + profile.name()), profile -> events.add("open-split " + profile.name()));
            panel.setDistros(List.of(ubuntu), profile -> 0);
            panel.selectDistro(ubuntu.id());
            panel.wide(true);
            assertThat(text(panel, HostsPanel.CARD_NAME)).isEqualTo("Ubuntu");
            assertThat(text(panel, HostsPanel.CARD_ADDRESS)).isEqualTo("WSL 2");
            assertThat(label(panel, HostsPanel.CONNECT)).isEqualTo("Open");
            assertThat(captions(panel)).extracting(Nodes.Text::text).containsExactly("Distribution", "Version", "Command", "Sessions");
            assertThat(values(panel)).extracting(Nodes.Text::text).startsWith("Ubuntu", "WSL 2", "wsl.exe -d Ubuntu");
            assertThat(label(panel, HostsPanel.SPLIT_BUTTON)).isEqualTo("Open in Split");
            assertThat(find(panel.view(), HostsPanel.EDIT)).as("a distribution has nothing to edit").isEmpty();
            press(panel.view(), HostsPanel.CONNECT);
            press(panel.view(), node(panel.view(), HostsPanel.SPLIT_BUTTON, Nodes.Button.class).action());
            assertThat(events).containsExactly("open Ubuntu", "open-split Ubuntu");
        });
    }

    @Test void aSearchThatHidesEveryRowEmptiesTheDetail() throws Exception {
        edt(() -> {
            var panel = panel(Layout.ROWS);
            panel.wide(true);
            type(panel.view(), HostsPanel.SEARCH, "zzz");
            var empty = node(panel.view(), HostsPanel.DETAIL_EMPTY, Nodes.Empty.class);
            assertThat(empty.title()).isEqualTo("No Session Selected");
            assertThat(text(panel, HostsPanel.HINT)).isEqualTo("No hosts match");
            assertThat(find(panel.view(), HostsPanel.DETAIL_FIELDS)).isEmpty();
            type(panel.view(), HostsPanel.SEARCH, "");
            assertThat(text(panel, HostsPanel.CARD_NAME)).as("clearing the search brings a host back").isEqualTo("Mac");
        });
    }

    @Test void theSelectionSurvivesTheSwitchBothWays() throws Exception {
        edt(() -> {
            var panel = panel(Layout.ROWS);
            panel.select(minecraft.id());
            panel.wide(true);
            assertThat(text(panel, HostsPanel.CARD_NAME)).isEqualTo("minecraft-local");
            panel.wide(false);
            assertThat(find(panel.view(), HostsPanel.COLUMNS)).isEmpty();
            assertThat(text(panel, HostsPanel.CARD_NAME)).isEqualTo("minecraft-local");
            assertThat(panel.wide()).isFalse();
        });
    }

    @Test void aDeletedHostsDetailFollowsTheNextRow() throws Exception {
        edt(() -> {
            var panel = panel(Layout.ROWS);
            panel.wide(true);
            panel.select(minecraft.id());
            panel.setHosts(List.of(mac), Optional.empty());
            assertThat(text(panel, HostsPanel.CARD_NAME)).isEqualTo("Mac");
        });
    }

    @Test void withNothingToListTheWideLayoutIsTheDockedOne() throws Exception {
        edt(() -> {
            var panel = panel(Layout.ROWS);
            panel.setHosts(List.of(), Optional.empty());
            panel.wide(true);
            assertThat(find(panel.view(), HostsPanel.COLUMNS)).isEmpty();
            assertThat(text(panel, HostsPanel.HINT)).startsWith("No hosts yet");
            panel.setHosts(List.of(mac), Optional.empty());
            assertThat(find(panel.view(), HostsPanel.COLUMNS)).as("the first host makes it wide").isPresent();
            panel.setHosts(List.of(), Optional.empty());
            assertThat(find(panel.view(), HostsPanel.COLUMNS)).as("and none takes it back").isEmpty();
        });
    }

    @Test void aDistributionAloneIsEnoughToListWide() throws Exception {
        edt(() -> {
            var panel = panel(Layout.ROWS);
            panel.setHosts(List.of(), Optional.empty());
            panel.setDistros(List.of(ubuntu), profile -> 0);
            panel.wide(true);
            assertThat(find(panel.view(), HostsPanel.COLUMNS)).isPresent();
            assertThat(text(panel, HostsPanel.CARD_NAME)).isEqualTo("Ubuntu");
        });
    }

    @Test void classicWideIsTheTreeBesideProperties() throws Exception {
        edt(() -> {
            var panel = panel(Layout.CLASSIC);
            panel.wide(true);
            var split = columns(panel);
            assertThat(split.kind()).isEqualTo(Nodes.SplitKind.SIDEBAR_DETAIL);
            assertThat(split.children()).extracting(Nodes.Node::id).containsExactly(HostsPanel.LIST_COLUMN, HostsPanel.DETAIL_COLUMN);
            var list = (Nodes.Panel) split.children().getFirst();
            assertThat(list.body()).isInstanceOf(Nodes.Tree.class);
            assertThat(list.body().id()).isEqualTo(HostsPanel.ROWS);
            assertThat(list.searchOrNavigation()).hasValueSatisfying(search -> {
                assertThat(search.id()).isEqualTo(HostsPanel.SEARCH);
                assertThat(((Nodes.Field) search).hint()).isEqualTo("Filter sessions");
            });
            assertThat(root(panel).searchOrNavigation()).isEmpty();
            var detail = (Nodes.Panel) split.children().getLast();
            assertThat(find(detail, HostsPanel.PROPERTIES)).hasValueSatisfying(properties ->
                assertThat(((Nodes.Properties) properties).title()).isEqualTo("Properties — Mac"));
            assertThat(find(list, HostsPanel.PROPERTIES)).as("Properties sit in the detail column only").isEmpty();
            assertThat(label(panel, HostsPanel.EDIT)).isEqualTo("Edit…");
            assertThat(label(panel, HostsPanel.CONNECT)).isEqualTo("Connect");
            press(panel.view(), HostsPanel.CONNECT);
            assertThat(events).containsExactly("connect Mac");
        });
    }

    @Test void classicWideSaysSoWhenNothingIsSelected() throws Exception {
        edt(() -> {
            var panel = panel(Layout.CLASSIC);
            panel.wide(true);
            type(panel.view(), HostsPanel.SEARCH, "zzz");
            assertThat(text(panel, HostsPanel.DETAIL_EMPTY)).isEqualTo("Select a session to see its properties.");
            assertThat(find(panel.view(), HostsPanel.PROPERTIES)).isEmpty();
            assertThat(text(panel, HostsPanel.HINT)).isEqualTo("No hosts match");
        });
    }

    @Test void wideChangesNothingWhileItIsUnchangedOrAfterTheViewClosed() throws Exception {
        edt(() -> {
            var panel = panel(Layout.ROWS);
            var before = panel.view().spec();
            panel.wide(false);
            assertThat(panel.view().spec()).as("false to false").isSameAs(before);
            panel.wide(true);
            var wide = panel.view().spec();
            panel.wide(true);
            assertThat(panel.view().spec()).as("true to true").isSameAs(wide);
            panel.close();
            panel.wide(false);
            assertThat(panel.wide()).as("a closed panel stays as it was").isTrue();
        });
    }

    @Test void theDockedPanelIsUnchangedByTheWideCode() throws Exception {
        edt(() -> {
            var panel = panel(Layout.ROWS);
            assertThat(panel.wide()).isFalse();
            assertThat(find(panel.view(), HostsPanel.COLUMNS)).isEmpty();
            assertThat(root(panel).searchOrNavigation()).isPresent();
            assertThat(text(panel, HostsPanel.CARD_NAME)).isEqualTo("Mac");
            assertThat(find(panel.view(), HostsPanel.DETAIL_FIELDS)).isEmpty();
        });
    }

    @Test void everyIdOfTheWideViewIsUnique() throws Exception {
        edt(() -> {
            for (var layout : Layout.values()) {
                var panel = panel(layout);
                panel.wide(true);
                var ids = all(panel.view()).stream().map(Nodes.Node::id).toList();
                assertThat(ids).as(layout + " wide ids").doesNotHaveDuplicates();
            }
        });
    }

    @Test void theKitReusesTheControlsTheDockedLayoutHasWhenTheLayoutSwitches() throws Exception {
        edt(() -> {
            var panel = panel(Layout.ROWS);
            var docked = all(panel.view());
            panel.wide(true);
            var wide = all(panel.view());
            for (var id : List.of(HostsPanel.ROOT, HostsPanel.HEADER, HostsPanel.SEARCH, HostsPanel.ROWS, HostsPanel.CARD_TILE, HostsPanel.CARD_NAME,
                HostsPanel.CARD_ADDRESS, HostsPanel.EDIT, HostsPanel.CONNECT)) {
                var before = docked.stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow();
                var after = wide.stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow();
                assertThat(after.getClass()).as(id).isEqualTo(before.getClass());
            }
        });
    }

    @Test void theUiEventsStillReachThePanelThroughTheWideList() throws Exception {
        edt(() -> {
            var panel = panel(Layout.ROWS);
            panel.wide(true);
            var selections = new ArrayList<Optional<String>>();
            panel.onSelectionChanged(selections::add);
            emit(panel.view(), new UiEvent.Selected(HostsPanel.ROWS, java.util.Set.of(HostsPanel.rowId(new HostRows.Host(minecraft)))));
            assertThat(text(panel, HostsPanel.CARD_NAME)).as("selecting a row changes the detail").isEqualTo("minecraft-local");
            assertThat(selections).containsExactly(Optional.of("host:" + minecraft.id()));
        });
    }
}
