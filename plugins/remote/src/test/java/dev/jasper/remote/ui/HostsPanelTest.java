package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.*;

/**
 * The Sessions panel as a host-rendered kit view (Plan 2 Task 8a). Each baseline (f16ae32f) behaviour is driven
 * through the kit's events and asserted on the descriptor; colours, fonts and geometry are kit roles now, and the
 * app's real-host Remote tests cover the mounted panel and its borrowed header.
 */
class HostsPanelTest {
    final List<String> events = new ArrayList<>();
    final HostsPanel.Actions actions = new HostsPanel.Actions(h -> events.add("connect " + h.name()), h -> events.add("split " + h.name()),
        h -> events.add("edit " + h.map(RemoteHost::name).orElse("new")), h -> events.add("duplicate " + h.name()), h -> events.add("delete " + h.name()),
        (h, favorite) -> events.add("favorite " + h.name() + " " + favorite), () -> events.add("import"));
    final RemoteHost prod = RemoteHost.create("prod", "api.example", 22, "deploy", new Auth.Vault(UUID.randomUUID()), "Production", Optional.empty());
    final RemoteHost nas = RemoteHost.create("nas", "nas.local", 2222, "me", Auth.AGENT, "", Optional.of(prod.id()));

    final ShellProfileInfo ubuntu = new ShellProfileInfo("wsl:Ubuntu", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.of("WSL 1"));
    final ShellProfileInfo debian = new ShellProfileInfo("wsl:Debian", "Debian", ShellProfileInfo.Kind.WSL, Optional.of("WSL 2"));
    final ShellProfileInfo pwsh = new ShellProfileInfo("pwsh", "PowerShell 7", ShellProfileInfo.Kind.LOCAL_SHELL, Optional.empty());

    HostsPanel panel() { return new HostsPanel(new FakeKit("dev.jasper.remote"), actions, dev.jasper.sdk.testing.FakeBundledIcon::new); }

    HostsPanel withDistros() {
        var panel = panel();
        panel.onDistros(profile -> events.add("open " + profile.name()), profile -> events.add("split " + profile.name()));
        panel.setDistros(List.of(ubuntu, pwsh, debian), profile -> profile.equals(debian) ? 2 : 0);
        panel.setHosts(List.of(prod), Optional.empty());
        return panel;
    }

    static List<HostRows.Row> rows(HostsPanel panel) { return panel.rows(); }
    static Nodes.ItemList list(HostsPanel panel) { return node(panel.view(), HostsPanel.ROWS, Nodes.ItemList.class); }
    static ListItem row(HostsPanel panel, int index) { return list(panel).items().get(index); }
    static void search(HostsPanel panel, String text) { type(panel.view(), HostsPanel.SEARCH, text); }
    static void select(HostsPanel panel, int index) { emit(panel.view(), new UiEvent.Selected(HostsPanel.ROWS, Set.of(row(panel, index).id()))); }
    /** A plain left click: the kit reports the selection, then the click. */
    static void clickRow(HostsPanel panel, int index) { select(panel, index); emit(panel.view(), new UiEvent.Clicked(HostsPanel.ROWS, row(panel, index).id())); }
    /** Enter or a double click on the selected row. */
    static void activate(HostsPanel panel) { press(panel.view(), list(panel).activateAction()); }
    static void menu(HostsPanel panel, int index, String label) { press(panel.view(), menuItem(row(panel, index), label).action()); }
    static Optional<Nodes.Node> card(HostsPanel panel) { return find(panel.view(), HostsPanel.CARD); }
    static String cardText(HostsPanel panel, String id) { return node(panel.view(), id, Nodes.Text.class).text(); }
    static boolean shown(HostsPanel panel, String id) { return find(panel.view(), id).map(n -> n.state().visible()).orElse(false); }
    static Nodes.Button star(HostsPanel panel, int index) {
        return row(panel, index).trailing().stream().filter(n -> n instanceof Nodes.Button b && b.action().startsWith(HostsPanel.FAVORITE))
            .map(Nodes.Button.class::cast).findFirst().orElseThrow();
    }
    static List<String> rowTexts(ListItem item) {
        var out = new ArrayList<String>();
        for (var part : List.of(item.title(), item.subtitle(), item.trailing()))
            for (var node : part) if (node instanceof Nodes.Text text) out.add(text.text()); else if (node instanceof Nodes.Chip chip) out.add(chip.label());
        return out;
    }
    static Optional<Nodes.StatusKind> dot(ListItem item) {
        var all = new ArrayList<Nodes.Node>(); item.leading().ifPresent(all::add); all.addAll(item.trailing());
        return all.stream().filter(n -> n instanceof Nodes.StatusDot).map(n -> ((Nodes.StatusDot) n).kind()).findFirst();
    }

    @Test void connectedFooterActivatesExistingShellAndUpdatesWhenSessionsEnd() throws Exception {
        edt(() -> {
            var panel = panel();
            panel.onActivate(host -> events.add("activate " + host.name()));
            panel.setCredentialLabels(host -> "Vault locked");
            panel.setHosts(List.of(prod), Optional.empty());
            panel.setHostDetails(host -> "Ubuntu", host -> 1);
            assertThat(button(panel.view(), HostsPanel.CONNECT).label()).isEqualTo("Open Shell");
            assertThat(button(panel.view(), HostsPanel.CONNECT).icon()).isEqualTo("terminal");
            assertThat(cardText(panel, HostsPanel.CARD_CREDENTIAL)).isEqualTo("Connected");
            assertThat(node(panel.view(), HostsPanel.CARD_CONNECTED, Nodes.StatusDot.class).kind()).isEqualTo(Nodes.StatusKind.SUCCESS);
            assertThat(shown(panel, HostsPanel.CARD_INFO)).as("OS metadata is retained in the tooltip").isFalse();
            dev.jasper.remote.testsupport.Kits.click(panel.view(), HostsPanel.CONNECT);
            panel.setHostDetails(host -> "Ubuntu", host -> 0);
            assertThat(button(panel.view(), HostsPanel.CONNECT).label()).isEqualTo("Connect");
            assertThat(button(panel.view(), HostsPanel.CONNECT).kind()).as("the card's Connect keeps its primary styling").isEqualTo(Nodes.ButtonKind.PRIMARY);
            assertThat(cardText(panel, HostsPanel.CARD_CREDENTIAL)).isEqualTo("Vault locked");
            assertThat(node(panel.view(), HostsPanel.CARD_LOCKED, Nodes.Icon.class).name()).isEqualTo("lock");
            assertThat(find(panel.view(), HostsPanel.CARD_CONNECTED)).isEmpty();
            dev.jasper.remote.testsupport.Kits.click(panel.view(), HostsPanel.CONNECT);
            assertThat(events).containsExactly("activate prod", "connect prod");
        });
    }

    @Test void searchOccupiesTheOnlyTopRowAndUsesTheReferencePlaceholder() throws Exception {
        edt(() -> {
            var panel = panel();
            var root = (Nodes.Panel) panel.view().spec().root();
            assertThat(root.searchOrNavigation()).get().isInstanceOfSatisfying(Nodes.Field.class, field -> {
                assertThat(field.kind()).isEqualTo(Nodes.FieldKind.SEARCH);
                assertThat(field.hint()).isEqualTo("Search sessions");
                assertThat(field.label()).isEqualTo("Search sessions");
            });
            assertThat(all(panel.view())).as("new/import actions belong in the app header")
                .noneMatch(n -> n instanceof Nodes.Button b && (b.tooltip().equals("New session") || b.label().equals("Import")));
            assertThat(root.header().primary()).isEmpty();
        });
    }

    /** Task 6d acceptance: the count and New/Import actions travel through PanelHost.setHeader. */
    @Test void appHeaderPublishesTotalSessionsAndAccessibleAddAndImportActions() throws Exception {
        edt(() -> {
            var panel = panel();
            var headers = new ArrayList<dev.jasper.sdk.ui.PanelHeader>();
            panel.onHeaderChanged(headers::add);
            assertThat(headers.getLast().detail()).isEqualTo("0");
            panel.setHosts(List.of(nas, prod), Optional.empty());
            panel.setDistros(List.of(ubuntu, pwsh), profile -> 0);
            search(panel, "no match");
            var header = headers.getLast();
            assertThat(header.detail()).as("filtering doesn't change the saved-session count").isEqualTo("3");
            assertThat(header.active()).isFalse();
            assertThat(header.actions()).extracting(dev.jasper.sdk.ui.PanelHeader.Action::label).containsExactly("New session", "Import sessions");
            header.actions().forEach(action -> action.invoke().run());
            assertThat(events).containsExactly("edit new", "import");
            panel.setHosts(List.of(nas), Optional.empty());
            assertThat(headers.getLast().detail()).isEqualTo("2");
            int published = headers.size();
            panel.setHosts(List.of(nas), Optional.empty());
            assertThat(headers).as("an unchanged count publishes nothing").hasSize(published);
        });
    }

    /** G4 (controller ruling): the outline star is always shown (visual-only); a favourite's is filled and amber. */
    @Test void rowsShowAnOutlineStarAndFavouritesAFilledWarningStar() throws Exception {
        edt(() -> {
            var panel = panel();
            panel.setHosts(List.of(nas, prod.withFavorite(true)), Optional.empty());
            int nasRow = indexOf(panel, nas), prodRow = indexOf(panel, prod);
            var plain = star(panel, nasRow);
            assertThat(plain.icon()).isEqualTo("star");
            assertThat(plain.iconSeverity()).isEmpty();
            assertThat(plain.tooltip()).isEqualTo("Add to favorites");
            assertThat(plain.kind()).isEqualTo(Nodes.ButtonKind.GHOST);
            assertThat(plain.presentation()).isEqualTo(Nodes.ButtonPresentation.EMBEDDED);
            assertThat(plain.label()).isEmpty();
            var favourite = star(panel, prodRow);
            assertThat(favourite.icon()).isEqualTo("star.filled");
            assertThat(favourite.iconSeverity()).contains(Nodes.Severity.WARNING);
            assertThat(favourite.tooltip()).isEqualTo("Remove from favorites");
            assertThat(row(panel, nasRow).trailing().getLast()).as("the star sits at the trailing edge").isSameAs(plain);
        });
    }

    /** A star click reports only its action: it toggles the favourite without selecting or activating the row. */
    @Test void theStarTogglesTheFavouriteWithoutSelectingOrActivating() throws Exception {
        edt(() -> {
            var panel = panel();
            panel.onActivate(host -> events.add("activate " + host.name()));
            panel.setHosts(List.of(prod, nas), Optional.empty());
            panel.select(prod.id());
            int nasRow = indexOf(panel, nas);
            press(panel.view(), star(panel, nasRow).action());
            assertThat(events).containsExactly("favorite nas true");
            assertThat(panel.selectedHost()).as("selection unchanged").contains(prod);
            assertThat(list(panel).selectedIds()).containsExactly(HostsPanel.rowId(new HostRows.Host(prod)));
        });
    }

    @Test void theCardsEditAndConnectControlsAreNamedAndReachable() throws Exception {
        edt(() -> {
            var panel = panel();
            panel.setHostDetails(host -> "Ubuntu", host -> 1);
            panel.setHosts(List.of(prod), Optional.empty());
            var edit = button(panel.view(), HostsPanel.EDIT);
            assertThat(edit.label()).isEmpty();
            assertThat(edit.icon()).isEqualTo("edit");
            assertThat(edit.tooltip()).as("its accessible name").isEqualTo("Edit session");
            assertThat(edit.kind()).isEqualTo(Nodes.ButtonKind.GHOST);
            assertThat(edit.state().visible() && edit.state().enabled()).isTrue();
            assertThat(button(panel.view(), HostsPanel.CONNECT).state().visible()).isTrue();
            assertThat(node(panel.view(), "card.actions", Nodes.Stack.class).role())
                .as("a narrow panel wraps the card's controls at full width (real-host geometry: StagedRemoteSessionsTest)").isEqualTo(Nodes.LayoutRole.ACTIONS);
        });
    }

    @Test void hostRowsShowTheirSessionStateAndDistrosTheirBadgeAndSessions() throws Exception {
        edt(() -> {
            var panel = withDistros();
            panel.setHostDetails(host -> "detail", host -> 1);
            for (int i = 0; i < rows(panel).size(); i++) {
                var model = rows(panel).get(i);
                var item = row(panel, i);
                if (model instanceof HostRows.Host) assertThat(dot(item)).contains(Nodes.StatusKind.SUCCESS);
                if (model instanceof HostRows.Distro distro && distro.sessions() > 0) assertThat(dot(item)).contains(Nodes.StatusKind.SUCCESS);
                if (model instanceof HostRows.Distro distro && distro.sessions() == 0) assertThat(dot(item)).isEmpty();
                if (model instanceof HostRows.Distro distro) assertThat(item.trailing()).anySatisfy(n -> assertThat(n).isInstanceOfSatisfying(Nodes.Chip.class,
                    chip -> { assertThat(chip.label()).isEqualTo(distro.profile().detail().orElseThrow()); assertThat(chip.hue()).isEqualTo(Nodes.Hue.NEUTRAL); }));
            }
            panel.setHostDetails(host -> "detail", host -> 0);
            assertThat(dot(row(panel, indexOf(panel, prod)))).as("no session: an idle dot").contains(Nodes.StatusKind.IDLE);
        });
    }

    @Test void groupRowsShowTheirStateWhenExpandedCollapsedAndRevealedBySearch() throws Exception {
        edt(() -> {
            var panel = panel();
            panel.setHosts(List.of(prod), Optional.empty());
            java.util.function.Supplier<Nodes.Icon> glyph = () -> (Nodes.Icon) row(panel, 0).leading().orElseThrow();
            assertThat(glyph.get().name()).isEqualTo("chevron.down");
            assertThat(glyph.get().accessibleName()).isEqualTo("Expanded group");
            assertThat(rowTexts(row(panel, 0))).containsExactly("Production", "1");
            assertThat(row(panel, 0).title().getLast()).isInstanceOfSatisfying(Nodes.Text.class, count -> assertThat(count.role()).isEqualTo(Nodes.TextRole.COUNT));
            clickRow(panel, 0);
            assertThat(glyph.get().name()).isEqualTo("chevron.right");
            assertThat(glyph.get().accessibleName()).isEqualTo("Collapsed group");
            search(panel, "prod");
            assertThat(glyph.get().name()).as("a search shows the group open").isEqualTo("chevron.down");
            assertThat(panel.collapsed()).containsExactly("Production");
            search(panel, "");
            assertThat(glyph.get().name()).isEqualTo("chevron.right");
        });
    }

    @Test void theKitHeaderCarriesTheTitleAndThereIsNoWslGroupWithoutDistributions() throws Exception {
        edt(() -> {
            var panel = panel();
            var root = (Nodes.Panel) panel.view().spec().root();
            assertThat(root.header().title()).as("the window borrows this header").isEqualTo("Sessions");
            assertThat(texts(panel.view())).doesNotContain("Sessions");
            panel.setDistros(List.of(pwsh), profile -> 0);
            assertThat(rows(panel)).isEmpty();
            assertThat(shown(panel, HostsPanel.HINT)).isTrue();
            assertThat(cardText(panel, HostsPanel.HINT)).as("without WSL the panel holds only hosts")
                .isEqualTo("No hosts yet — Add or Import from ~/.ssh/config");
            panel.setHosts(List.of(prod), Optional.empty());
            assertThat(rows(panel)).containsExactly(new HostRows.Group("Production", 1, false), new HostRows.Host(prod));
            assertThat(find(panel.view(), HostsPanel.HINT)).isEmpty();
        });
    }

    @Test void theEmptyHintSpeaksOfSshHostsWhenTheWslGroupIsShown() throws Exception {
        edt(() -> {
            var panel = panel();
            panel.setDistros(List.of(ubuntu), profile -> 0);
            assertThat(rows(panel)).containsExactly(new HostRows.Group(HostRows.WSL, 1, false, true), new HostRows.Distro(ubuntu, 0));
            assertThat(cardText(panel, HostsPanel.HINT)).isEqualTo("No SSH hosts yet — Add or Import from ~/.ssh/config");
            panel.setHosts(List.of(prod), Optional.empty());
            search(panel, "zzz");
            assertThat(rows(panel)).isEmpty();
            assertThat(cardText(panel, HostsPanel.HINT)).isEqualTo("No sessions match");
            panel.setDistros(List.of(), profile -> 0);
            assertThat(cardText(panel, HostsPanel.HINT)).isEqualTo("No hosts match");
        });
    }

    @Test void wslDistributionsComeFirstWithBadgesSessionsAndTheirOwnCard() throws Exception {
        edt(() -> {
            var panel = withDistros();
            assertThat(rows(panel)).containsExactly(new HostRows.Group(HostRows.WSL, 2, false, true),
                new HostRows.Distro(ubuntu, 0), new HostRows.Distro(debian, 2), new HostRows.Group("Production", 1, false), new HostRows.Host(prod));
            assertThat(rowTexts(row(panel, 0))).containsExactly("WSL", "2");
            assertThat(row(panel, 0).tooltip()).as("only the default host group can be renamed").isEmpty();
            var busy = row(panel, 2);
            assertThat(rowTexts(busy)).contains("Debian", "WSL 2", "2");
            assertThat(dot(busy)).contains(Nodes.StatusKind.SUCCESS);
            assertThat(busy.tooltip()).isEqualTo("Debian, WSL 2, 2 sessions");
            assertThat(rowTexts(row(panel, 1))).contains("Ubuntu", "WSL 1");
            assertThat(dot(row(panel, 1))).isEmpty();
            assertThat(panel.selectedDistro()).as("the first selectable row").contains(ubuntu);
            assertThat(card(panel)).isPresent();
            assertThat(cardText(panel, HostsPanel.CARD_NAME)).isEqualTo("Ubuntu");
            assertThat(cardText(panel, HostsPanel.CARD_ADDRESS)).isEqualTo("WSL 1");
            assertThat(cardText(panel, HostsPanel.CARD_INFO)).isEqualTo("wsl.exe -d Ubuntu");
            assertThat(shown(panel, HostsPanel.CARD_INFO)).isTrue();
            assertThat(shown(panel, HostsPanel.CARD_CREDENTIAL)).isFalse();
            assertThat(shown(panel, HostsPanel.EDIT)).isFalse();
            assertThat(button(panel.view(), HostsPanel.CONNECT).label()).isEqualTo("Open");
            dev.jasper.remote.testsupport.Kits.click(panel.view(), HostsPanel.CONNECT);
            select(panel, 2); activate(panel);
            assertThat(events).containsExactly("open Ubuntu", "open Debian");
            panel.selectDistro("wsl:Debian");
            panel.setDistros(List.of(ubuntu, debian), profile -> 1);
            assertThat(panel.selectedDistro()).as("a refresh keeps the selection").contains(debian);
            panel.select(prod.id());
            assertThat(panel.selectedDistro()).isEmpty();
            assertThat(button(panel.view(), HostsPanel.CONNECT).label()).isEqualTo("Connect");
            assertThat(shown(panel, HostsPanel.EDIT)).isTrue();
            assertThat(shown(panel, HostsPanel.CARD_CREDENTIAL)).isTrue();
        });
    }

    @Test void aHostGroupNamedWslCollapsesApartFromTheWslGroup() throws Exception {
        edt(() -> {
            var box = RemoteHost.create("box", "box.example", 22, "me", Auth.AGENT, "wsl", Optional.empty());
            var panel = panel();
            panel.setDistros(List.of(ubuntu), profile -> 0);
            panel.setHosts(List.of(box), Optional.empty());
            panel.setCollapsed(Set.of("wsl"));
            assertThat(rows(panel)).containsExactly(new HostRows.Group(HostRows.WSL, 1, false, true), new HostRows.Distro(ubuntu, 0),
                new HostRows.Group("wsl", 1, true));
            assertThat(row(panel, 0).id()).isNotEqualTo(row(panel, 2).id());
            clickRow(panel, 0);
            assertThat(panel.wslCollapsed()).isTrue();
            assertThat(panel.collapsed()).containsExactly("wsl");
            clickRow(panel, 1);
            assertThat(panel.collapsed()).isEmpty();
            assertThat(panel.wslCollapsed()).isTrue();
            assertThat(rows(panel)).containsExactly(new HostRows.Group(HostRows.WSL, 1, true, true), new HostRows.Group("wsl", 1, false), new HostRows.Host(box));
            panel.setWslCollapsed(false);
            assertThat(rows(panel)).startsWith(new HostRows.Group(HostRows.WSL, 1, false, true), new HostRows.Distro(ubuntu, 0));
        });
    }

    @Test void distroMenusOfferOnlyOpeningAndTheWslGroupCollapsesOnItsOwn() throws Exception {
        edt(() -> {
            var panel = withDistros();
            assertThat(row(panel, 1).leading()).get().isInstanceOfSatisfying(Nodes.Icon.class, icon -> assertThat(icon.name()).isEqualTo("terminal"));
            assertThat(menuLabels(row(panel, 1))).containsExactly("Open in new tab", "Open in split");
            menu(panel, 1, "Open in new tab");
            menu(panel, 1, "Open in split");
            assertThat(events).containsExactly("open Ubuntu", "split Ubuntu");
            assertThat(row(panel, 0).contextActions()).as("the WSL header offers no rename").isEmpty();
            int[] changes = {0};
            panel.onCollapsedChanged(() -> changes[0]++);
            clickRow(panel, 0);
            assertThat(changes[0]).isEqualTo(1);
            assertThat(panel.wslCollapsed()).isTrue();
            assertThat(panel.collapsed()).as("the WSL group is not in the host groups' collapsed set").isEmpty();
            assertThat(rows(panel)).startsWith(new HostRows.Group(HostRows.WSL, 2, true, true), new HostRows.Group("Production", 1, false));
            assertThat(((Nodes.Icon) row(panel, 0).leading().orElseThrow()).name()).isEqualTo("chevron.right");
            search(panel, "deb");
            assertThat(rows(panel)).containsExactly(new HostRows.Group(HostRows.WSL, 1, false, true), new HostRows.Distro(debian, 2));
            search(panel, "");
            panel.setDistros(List.of(), profile -> 0);
            assertThat(rows(panel)).containsExactly(new HostRows.Group("Production", 1, false), new HostRows.Host(prod));
        });
    }

    @Test void showingSelectsFirstHostAndKeyboardSelectionActivates() throws Exception {
        edt(() -> {
            var panel = panel();
            var second = RemoteHost.create("staging", "staging.example", 22, "me", Auth.AGENT, "Production", Optional.empty());
            panel.setHosts(List.of(second, prod, nas), Optional.of("A host file warning"));
            panel.select(nas.id());
            panel.shown();
            assertThat(panel.selectedHost()).contains(prod);
            assertThat(row(panel, 0).heading()).as("the error row is never selected").isTrue();
            assertThat(list(panel).selectedIds()).containsExactly(HostsPanel.rowId(new HostRows.Host(prod)));
            select(panel, indexOf(panel, second));
            assertThat(panel.selectedHost()).contains(second);
            activate(panel);
            assertThat(events).containsExactly("connect staging");
            panel.shown();
            assertThat(panel.selectedHost()).contains(prod);
        });
    }

    @Test void selectedGroupSurvivesCollapseExpandAndHostRefresh() throws Exception {
        edt(() -> {
            var panel = panel();
            panel.setHosts(List.of(prod, nas), Optional.empty());
            select(panel, 0);
            activate(panel);
            assertThat(panel.selectedRow()).contains(new HostRows.Group("Production", 1, true));
            panel.setHosts(List.of(prod, nas), Optional.empty());
            assertThat(panel.selectedRow()).contains(new HostRows.Group("Production", 1, true));
            activate(panel);
            assertThat(panel.selectedRow()).contains(new HostRows.Group("Production", 1, false));
            assertThat(events).isEmpty();
        });
    }

    /** G2: a group toggles on a single click, as the old list did; a host's single click only selects it. */
    @Test void aSingleClickTogglesAGroupButOnlySelectsAHost() throws Exception {
        edt(() -> {
            var panel = panel();
            panel.onActivate(host -> events.add("activate " + host.name()));
            panel.setHosts(List.of(prod, nas), Optional.empty());
            clickRow(panel, indexOf(panel, nas));
            assertThat(panel.selectedHost()).contains(nas);
            assertThat(events).as("a single click never connects").isEmpty();
            clickRow(panel, 0);
            assertThat(panel.collapsed()).containsExactly("Production");
            assertThat(list(panel).selectedIds()).as("the clicked group stays selected").containsExactly(row(panel, 0).id());
            select(panel, 0);
            assertThat(panel.collapsed()).as("keyboard selection never toggles").containsExactly("Production");
        });
    }

    @Test void lateHostsSelectFirstButRefreshKeepsKeyboardSelection() throws Exception {
        edt(() -> {
            var panel = panel();
            panel.shown();
            panel.setHosts(List.of(prod, nas), Optional.empty());
            assertThat(panel.selectedHost()).contains(prod);
            panel.select(nas.id());
            panel.setHosts(List.of(prod, nas), Optional.empty());
            assertThat(panel.selectedHost()).contains(nas);
            search(panel, "no match");
            assertThat(panel.selectedHost()).isEmpty();
            search(panel, "prod");
            assertThat(panel.selectedHost()).contains(prod);
        });
    }

    /** Typography is the kit's (it follows the UI font live): the card asks for roles, never fonts. */
    @Test void theCardUsesKitTextRoles() throws Exception {
        edt(() -> {
            var panel = panel();
            panel.setHosts(List.of(prod), Optional.empty()); panel.select(prod.id());
            assertThat(node(panel.view(), HostsPanel.CARD_NAME, Nodes.Text.class).role()).isEqualTo(Nodes.TextRole.BODY_STRONG);
            assertThat(node(panel.view(), HostsPanel.CARD_ADDRESS, Nodes.Text.class).role()).isEqualTo(Nodes.TextRole.MONO);
            assertThat(node(panel.view(), HostsPanel.CARD_TILE, Nodes.Icon.class).name()).isEqualTo("server");
        });
    }

    @Test void showsRowsCardAndRunsActions() throws Exception {
        edt(() -> {
            var panel = panel();
            assertThat(shown(panel, HostsPanel.HINT)).isTrue();
            panel.setCredentialLabels(host -> host.auth() instanceof Auth.Agent ? "SSH agent" : "Work key");
            panel.setHosts(List.of(prod, nas), Optional.empty());
            assertThat(find(panel.view(), HostsPanel.HINT)).isEmpty();
            assertThat(list(panel).items()).as("two groups, two hosts").hasSize(4);
            panel.select(nas.id());
            assertThat(card(panel)).isPresent();
            assertThat(cardText(panel, HostsPanel.CARD_NAME)).isEqualTo("nas");
            assertThat(cardText(panel, HostsPanel.CARD_ADDRESS)).isEqualTo("me@nas.local:2222");
            assertThat(cardText(panel, HostsPanel.CARD_CREDENTIAL)).isEqualTo("SSH agent");
            assertThat(cardText(panel, HostsPanel.CARD_JUMP)).isEqualTo("via prod");
            dev.jasper.remote.testsupport.Kits.click(panel.view(), HostsPanel.CONNECT);
            dev.jasper.remote.testsupport.Kits.click(panel.view(), HostsPanel.EDIT);
            activate(panel);
            assertThat(events).containsExactly("connect nas", "edit nas", "connect nas");
            events.clear();
            int nasRow = indexOf(panel, nas);
            assertThat(menuLabels(row(panel, nasRow))).containsExactly("Connect in new tab", "Connect in split", "Edit…", "Duplicate", "Delete…", "Add to favorites");
            menu(panel, nasRow, "Connect in split");
            menu(panel, nasRow, "Add to favorites");
            assertThat(events).containsExactly("split nas", "favorite nas true");
            panel.select(prod.id());
            assertThat(cardText(panel, HostsPanel.CARD_CREDENTIAL)).isEqualTo("Work key");
            assertThat(shown(panel, HostsPanel.CARD_JUMP)).isFalse();
        });
    }

    /** Row menus act on the row they belong to, whatever is selected (a context press never selects). */
    @Test void rowMenusNameTheirRowAndNeverNeedTheSelection() throws Exception {
        edt(() -> {
            var panel = panel();
            panel.onBrowseFiles(host -> events.add("browse " + host.name()));
            panel.setHosts(List.of(prod, nas), Optional.empty());
            panel.select(prod.id());
            int nasRow = indexOf(panel, nas);
            assertThat(menuLabels(row(panel, nasRow))).contains("Browse files");
            menu(panel, nasRow, "Delete…");
            menu(panel, nasRow, "Browse files");
            menu(panel, nasRow, "Duplicate");
            menu(panel, nasRow, "Edit…");
            assertThat(events).containsExactly("delete nas", "browse nas", "duplicate nas", "edit nas");
            assertThat(panel.selectedHost()).contains(prod);
            assertThat(row(panel, nasRow).contextActions().orElseThrow()).extracting(dev.jasper.sdk.ui.MenuItem::action)
                .allSatisfy(action -> assertThat(action).endsWith(HostsPanel.rowId(new HostRows.Host(nas))));
            assertThat(list(panel).contextActions()).as("no shared menu acting on the selection").isEmpty();
        });
    }

    @Test void theDefaultGroupRenamesFromItsRowOrAFlatList() throws Exception {
        edt(() -> {
            int[] renames = {0};
            var panel = panel();
            panel.onRenameDefaultGroup(() -> renames[0]++);
            panel.setHosts(List.of(nas), Optional.empty());
            assertThat(rows(panel)).as("a flat list has no group row").containsExactly(new HostRows.Host(nas));
            menu(panel, 0, "Rename group…");
            panel.setHosts(List.of(prod, nas), Optional.empty());
            int other = rows(panel).indexOf(new HostRows.Group(HostRows.OTHER, 1, false));
            assertThat(row(panel, other).tooltip()).isEqualTo("Right-click to rename this group");
            assertThat(menuLabels(row(panel, other))).containsExactly("Rename group…");
            menu(panel, other, "Rename group…");
            assertThat(menuLabels(row(panel, indexOf(panel, nas)))).as("grouped hosts rename through the group row").doesNotContain("Rename group…");
            assertThat(row(panel, 0).contextActions()).as("a named group has no menu").isEmpty();
            assertThat(renames[0]).isEqualTo(2);
        });
    }

    @Test void searchFiltersAndCollapseIsRemembered() throws Exception {
        edt(() -> {
            var panel = panel();
            panel.setHosts(List.of(prod, nas), Optional.empty());
            int[] collapsedChanges = {0};
            panel.onCollapsedChanged(() -> collapsedChanges[0]++);
            clickRow(panel, 0);
            assertThat(panel.collapsed()).containsExactly("Production");
            assertThat(rows(panel)).hasSize(3);
            assertThat(collapsedChanges[0]).isEqualTo(1);
            search(panel, "prod");
            assertThat(rows(panel)).as("search reveals the collapsed group").hasSize(2);
            assertThat(panel.collapsed()).as("without changing the saved state").containsExactly("Production");
            search(panel, "zzz");
            assertThat(rows(panel)).isEmpty();
            assertThat(cardText(panel, HostsPanel.HINT)).contains("No hosts match");
            search(panel, "");
            panel.setCollapsed(Set.of());
            assertThat(rows(panel)).hasSize(4);
            panel.setHosts(List.of(prod, nas), Optional.of("hosts.toml has TOML errors"));
            assertThat(rows(panel)).as("an error row leads").hasSize(5);
            assertThat(rows(panel).getFirst()).isInstanceOf(HostRows.Error.class);
            assertThat(rowTexts(row(panel, 0))).containsExactly("hosts.toml has TOML errors");
            assertThat(node(panel.view(), HostsPanel.SEARCH, Nodes.Field.class).value()).isEmpty();
        });
    }

    @Test void clearsRemovedSelectionAndBindsEnterAndFavoriteClick() throws Exception {
        edt(() -> {
            var panel = panel();
            panel.setHosts(List.of(nas), Optional.empty());
            panel.select(nas.id());
            assertThat(list(panel).activateAction()).as("Enter and double click").isNotBlank();
            assertThat(list(panel).enterAction()).as("Enter is the activate action").isEmpty();
            activate(panel);
            press(panel.view(), star(panel, 0).action());
            assertThat(events).containsExactly("connect nas", "favorite nas true");
            panel.setHosts(List.of(), Optional.empty());
            assertThat(card(panel)).isEmpty();
        });
    }

    @Test void rowsKeepSessionCountsAndSearchableDetails() throws Exception {
        edt(() -> {
            var panel = panel();
            panel.setHostDetails(host -> host.id().equals(nas.id()) ? "Ubuntu · 10.0.0.2" : "macOS", host -> host.id().equals(nas.id()) ? 2 : 0);
            panel.setHosts(List.of(nas, prod), Optional.empty());
            search(panel, "ubuntu");
            assertThat(rows(panel)).hasSize(1);
            var item = row(panel, 0);
            assertThat(rowTexts(item)).contains("nas");
            assertThat(item.tooltip()).contains("2 sessions");
            assertThat(item.tooltip()).contains("me@nas.local:2222", "Ubuntu · 10.0.0.2");
            panel.select(nas.id());
            assertThat(cardText(panel, HostsPanel.CARD_ADDRESS)).isEqualTo("me@nas.local:2222");
            assertThat(cardText(panel, HostsPanel.CARD_INFO)).isEqualTo("Ubuntu · 10.0.0.2");
        });
    }

    @Test void sessionRowsShowAddressAndFavoriteAtTheTrailingEdge() throws Exception {
        edt(() -> {
            var panel = panel();
            panel.setHosts(List.of(prod), Optional.empty());
            var item = row(panel, 1);
            assertThat(item.title()).extracting(n -> ((Nodes.Text) n).text()).as("the host address is visible alongside its name")
                .containsExactly("prod", "api.example");
            assertThat(((Nodes.Text) item.title().getLast()).role()).isEqualTo(Nodes.TextRole.DESCRIPTION);
            assertThat(item.trailing().getLast()).isInstanceOf(Nodes.Button.class);
            assertThat(item.leading()).get().as("the leading status dot is not a favorite toggle").isInstanceOf(Nodes.StatusDot.class);
        });
    }

    @Test void rowsExposeTheirIdentityToAssistiveTechnology() throws Exception {
        edt(() -> {
            var panel = panel();
            panel.setHostDetails(host -> "Ubuntu", host -> 1);
            panel.setHosts(List.of(nas), Optional.empty());
            assertThat(row(panel, 0).tooltip()).as("the row's accessible description").contains("nas", "me@nas.local:2222", "Ubuntu", "1 session")
                .doesNotContain("via");
            panel.setHosts(List.of(nas, prod), Optional.empty());
            assertThat(row(panel, indexOf(panel, nas)).tooltip()).as("a jump host the old card's tooltip named").contains("nas", "me@nas.local:2222", "via prod");
        });
    }

    @Test void keyboardSelectionIsVisibleOnGroupHeaders() throws Exception {
        edt(() -> {
            var panel = panel(); panel.setHosts(List.of(nas), Optional.empty()); panel.setCollapsed(Set.of(HostRows.OTHER));
            assertThat(row(panel, 0).heading()).as("a group row is a selectable row, not a heading").isFalse();
            assertThat(list(panel).selectedIds()).containsExactly(row(panel, 0).id());
        });
    }

    /** No raw Swing reaches the host: the panel is a kit view the plugin owns, and closing it makes callbacks inert. */
    @Test void closingThePanelMakesLateUpdatesInert() throws Exception {
        edt(() -> {
            var kit = new RecordingKit("dev.jasper.remote");
            var panel = new HostsPanel(kit, actions, dev.jasper.sdk.testing.FakeBundledIcon::new);
            panel.setHosts(List.of(prod), Optional.empty());
            panel.close();
            panel.setHosts(List.of(prod, nas), Optional.empty()); panel.setDistros(List.of(ubuntu), p -> 0); panel.shown(); panel.focusWhenOpened();
            panel.setCredentialLabels(h -> "x"); panel.select(prod.id());
            assertThat(kit.lateCalls).isEmpty();
            assertThat(closed(panel.view())).isTrue();
        });
    }

    static int indexOf(HostsPanel panel, RemoteHost host) {
        for (int i = 0; i < rows(panel).size(); i++) if (rows(panel).get(i) instanceof HostRows.Host row && row.host().id().equals(host.id())) return i;
        throw new AssertionError("host row not shown");
    }
}
