package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HostsPanelTest {
    @Test void groupRowsUseFolderIconsWhenExpandedCollapsedAndRevealedBySearch() {
        var folderIcons = new java.util.EnumMap<dev.jasper.sdk.ui.BundledIcon, javax.swing.Icon>(dev.jasper.sdk.ui.BundledIcon.class);
        folderIcons.put(dev.jasper.sdk.ui.BundledIcon.NODES_FOLDER, new dev.jasper.sdk.testing.FakeBundledIcon(dev.jasper.sdk.ui.BundledIcon.NODES_FOLDER));
        folderIcons.put(dev.jasper.sdk.ui.BundledIcon.GENERAL_OPEN, new dev.jasper.sdk.testing.FakeBundledIcon(dev.jasper.sdk.ui.BundledIcon.GENERAL_OPEN));
        var panel = new HostsPanel(actions, folderIcons::get);
        panel.setHosts(List.of(prod), Optional.empty());
        java.util.function.Supplier<javax.swing.JLabel> group = () -> (javax.swing.JLabel) panel.list.getCellRenderer()
            .getListCellRendererComponent(panel.list, panel.list.getModel().getElementAt(0), 0, true, false);
        var open = group.get().getIcon();
        assertThat(open).isSameAs(folderIcons.get(dev.jasper.sdk.ui.BundledIcon.GENERAL_OPEN));
        assertThat(group.get().getText()).isEqualTo("Production   1");
        panel.toggle(0);
        var closed = group.get().getIcon();
        assertThat(closed).isSameAs(folderIcons.get(dev.jasper.sdk.ui.BundledIcon.NODES_FOLDER));
        panel.search.setText("prod");
        assertThat(group.get().getIcon()).isSameAs(open);
        assertThat(panel.collapsed()).containsExactly("Production");
        panel.search.setText("");
        assertThat(group.get().getIcon()).isSameAs(closed);
    }

    final List<String> events = new ArrayList<>();
    final HostsPanel.Actions actions = new HostsPanel.Actions(h -> events.add("connect " + h.name()), h -> events.add("split " + h.name()),
        h -> events.add("edit " + h.map(RemoteHost::name).orElse("new")), h -> events.add("duplicate " + h.name()), h -> events.add("delete " + h.name()),
        (h, favorite) -> events.add("favorite " + h.name() + " " + favorite), () -> events.add("import"));
    final RemoteHost prod = RemoteHost.create("prod", "api.example", 22, "deploy", new Auth.Vault(UUID.randomUUID()), "Production", Optional.empty());
    final RemoteHost nas = RemoteHost.create("nas", "nas.local", 2222, "me", Auth.AGENT, "", Optional.of(prod.id()));

    final ShellProfileInfo ubuntu = new ShellProfileInfo("wsl:Ubuntu", "Ubuntu", ShellProfileInfo.Kind.WSL, Optional.of("WSL 1"));
    final ShellProfileInfo debian = new ShellProfileInfo("wsl:Debian", "Debian", ShellProfileInfo.Kind.WSL, Optional.of("WSL 2"));
    final ShellProfileInfo pwsh = new ShellProfileInfo("pwsh", "PowerShell 7", ShellProfileInfo.Kind.LOCAL_SHELL, Optional.empty());

    HostsPanel withDistros(java.util.function.Function<dev.jasper.sdk.ui.BundledIcon, javax.swing.Icon> icons) {
        var panel = new HostsPanel(actions, icons);
        panel.onDistros(profile -> events.add("open " + profile.name()), profile -> events.add("split " + profile.name()));
        panel.setDistros(List.of(ubuntu, pwsh, debian), profile -> profile.equals(debian) ? 2 : 0);
        panel.setHosts(List.of(prod), Optional.empty());
        return panel;
    }

    static List<HostRows.Row> rows(HostsPanel panel) {
        var out = new ArrayList<HostRows.Row>();
        for (int i = 0; i < panel.list.getModel().getSize(); i++) out.add(panel.list.getModel().getElementAt(i));
        return out;
    }

    static java.awt.Component render(HostsPanel panel, int index) {
        return panel.list.getCellRenderer().getListCellRendererComponent(panel.list, panel.list.getModel().getElementAt(index), index, false, false);
    }

    @Test void theHeadingReadsSessionsAndThereIsNoWslGroupWithoutDistributions() {
        var panel = new HostsPanel(actions);
        assertThat(panel.title.getText()).isEqualTo("Sessions");
        panel.setDistros(List.of(pwsh), profile -> 0);
        assertThat(rows(panel)).isEmpty();
        assertThat(panel.empty.isVisible()).isTrue();
        assertThat(panel.empty.getText()).isEqualTo("No hosts yet \u2014 Add or Import from ~/.ssh/config");
        panel.setHosts(List.of(prod), Optional.empty());
        assertThat(rows(panel)).containsExactly(new HostRows.Group("Production", 1, false), new HostRows.Host(prod));
    }

    @Test void wslDistributionsComeFirstWithBadgesSessionsAndTheirOwnCard() {
        var panel = withDistros(name -> null);
        assertThat(rows(panel)).containsExactly(new HostRows.Group(HostRows.WSL, 2, false, HostRows.WSL_KEY),
            new HostRows.Distro(ubuntu, 0), new HostRows.Distro(debian, 2), new HostRows.Group("Production", 1, false), new HostRows.Host(prod));
        var header = (javax.swing.JLabel) render(panel, 0);
        assertThat(header.getText()).isEqualTo("WSL   2");
        assertThat(header.getToolTipText()).as("only the default host group can be renamed").isNull();
        var busy = render(panel, 2);
        assertThat(labels(busy)).contains("Debian", "WSL 2", "\u25CF", "2");
        assertThat(busy.getAccessibleContext().getAccessibleName()).isEqualTo("Debian, WSL 2, 2 sessions");
        assertThat(labels(render(panel, 1))).contains("Ubuntu", "WSL 1").doesNotContain("\u25CF");
        assertThat(panel.selectedDistro()).as("the first selectable row").contains(ubuntu);
        assertThat(panel.card.isVisible()).isTrue();
        assertThat(panel.cardName.getText()).isEqualTo("Ubuntu");
        assertThat(panel.cardAddress.getText()).isEqualTo("WSL 1");
        assertThat(panel.cardInfo.getText()).isEqualTo("wsl.exe -d Ubuntu");
        assertThat(panel.cardCredential.isVisible()).isFalse();
        assertThat(panel.edit.isVisible()).isFalse();
        assertThat(panel.connect.getText()).isEqualTo("Open");
        panel.connect.doClick();
        panel.activate(2);
        assertThat(events).containsExactly("open Ubuntu", "open Debian");
        panel.selectDistro("wsl:Debian");
        panel.setDistros(List.of(ubuntu, debian), profile -> 1);
        assertThat(panel.selectedDistro()).as("a refresh keeps the selection").contains(debian);
        panel.select(prod.id());
        assertThat(panel.selectedDistro()).isEmpty();
        assertThat(panel.connect.getText()).isEqualTo("Connect");
        assertThat(panel.edit.isVisible()).isTrue();
        assertThat(panel.cardCredential.isVisible()).isTrue();
    }

    @Test void distroMenusOfferOnlyOpeningAndTheWslGroupCollapsesUnderItsOwnKey() {
        var icons = new java.util.EnumMap<dev.jasper.sdk.ui.BundledIcon, javax.swing.Icon>(dev.jasper.sdk.ui.BundledIcon.class);
        for (var name : List.of(dev.jasper.sdk.ui.BundledIcon.NODES_FOLDER, dev.jasper.sdk.ui.BundledIcon.GENERAL_OPEN,
                dev.jasper.sdk.ui.BundledIcon.TERMINAL_TOOLWINDOW_TERMINAL))
            icons.put(name, new dev.jasper.sdk.testing.FakeBundledIcon(name));
        var panel = withDistros(icons::get);
        var row = (java.awt.Container) render(panel, 1);
        assertThat(((javax.swing.JLabel) row.getComponent(0)).getIcon()).isSameAs(icons.get(dev.jasper.sdk.ui.BundledIcon.TERMINAL_TOOLWINDOW_TERMINAL));
        javax.swing.JPopupMenu menu = panel.menuFor(1);
        assertThat(menu.getComponentCount()).isEqualTo(2);
        assertThat(((javax.swing.JMenuItem) menu.getComponent(0)).getText()).isEqualTo("Open in new tab");
        assertThat(((javax.swing.JMenuItem) menu.getComponent(1)).getText()).isEqualTo("Open in split");
        ((javax.swing.JMenuItem) menu.getComponent(0)).doClick();
        ((javax.swing.JMenuItem) menu.getComponent(1)).doClick();
        assertThat(events).containsExactly("open Ubuntu", "split Ubuntu");
        assertThat(panel.menuFor(0)).as("the WSL header offers no rename").isNull();
        int[] changes = {0};
        panel.onCollapsedChanged(() -> changes[0]++);
        panel.toggle(0);
        assertThat(changes[0]).isEqualTo(1);
        assertThat(panel.collapsed()).containsExactly(HostRows.WSL_KEY);
        assertThat(rows(panel)).startsWith(new HostRows.Group(HostRows.WSL, 2, true, HostRows.WSL_KEY), new HostRows.Group("Production", 1, false));
        assertThat(((javax.swing.JLabel) render(panel, 0)).getIcon()).isSameAs(icons.get(dev.jasper.sdk.ui.BundledIcon.NODES_FOLDER));
        panel.search.setText("deb");
        assertThat(rows(panel)).containsExactly(new HostRows.Group(HostRows.WSL, 1, false, HostRows.WSL_KEY), new HostRows.Distro(debian, 2));
        panel.search.setText("");
        panel.setDistros(List.of(), profile -> 0);
        assertThat(rows(panel)).containsExactly(new HostRows.Group("Production", 1, false), new HostRows.Host(prod));
    }

    @Test void showingSelectsFirstHostAndArrowKeysNavigateImmediately() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var panel = new HostsPanel(actions);
            var second = RemoteHost.create("staging", "staging.example", 22, "me", Auth.AGENT, "Production", Optional.empty());
            panel.setHosts(List.of(second, prod, nas), Optional.of("A host file warning"));
            panel.select(nas.id());
            panel.addNotify();
            try {
                assertThat(panel.selectedHost()).contains(prod);
                assertThat(panel.list.getSelectedValue()).isInstanceOf(HostRows.Host.class);
                panel.list.getActionMap().get("selectNextRow").actionPerformed(
                    new java.awt.event.ActionEvent(panel.list, 0, "Down"));
                assertThat(panel.selectedHost()).contains(second);
                panel.list.getActionMap().get("activateHost").actionPerformed(
                    new java.awt.event.ActionEvent(panel.list, 0, "Enter"));
                assertThat(events).containsExactly("connect staging");
                panel.removeNotify();
                panel.addNotify();
                assertThat(panel.selectedHost()).contains(prod);
            } finally { panel.removeNotify(); }
        });
    }

    @Test void selectedGroupSurvivesCollapseExpandAndHostRefresh() {
        var panel = new HostsPanel(actions);
        panel.setHosts(List.of(prod, nas), Optional.empty());
        panel.list.setSelectedIndex(0);
        var enter = panel.list.getActionMap().get("activateHost");
        var event = new java.awt.event.ActionEvent(panel.list, 0, "Enter");
        enter.actionPerformed(event);
        assertThat(panel.list.getSelectedValue()).isEqualTo(new HostRows.Group("Production", 1, true));
        panel.setHosts(List.of(prod, nas), Optional.empty());
        assertThat(panel.list.getSelectedValue()).isEqualTo(new HostRows.Group("Production", 1, true));
        enter.actionPerformed(event);
        assertThat(panel.list.getSelectedValue()).isEqualTo(new HostRows.Group("Production", 1, false));
        assertThat(events).isEmpty();
    }

    @Test void lateHostsSelectFirstButRefreshKeepsKeyboardSelection() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var panel = new HostsPanel(actions);
            panel.addNotify();
            try {
                panel.setHosts(List.of(prod, nas), Optional.empty());
                assertThat(panel.selectedHost()).contains(prod);
                panel.select(nas.id());
                panel.setHosts(List.of(prod, nas), Optional.empty());
                assertThat(panel.selectedHost()).contains(nas);
                panel.search.setText("no match");
                assertThat(panel.selectedHost()).isEmpty();
                panel.search.setText("prod");
                assertThat(panel.selectedHost()).contains(prod);
            } finally { panel.removeNotify(); }
        });
    }

    @Test void openHostDetailsAndConnectionHeadingFollowUiFontChanges() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var panel = new HostsPanel(actions);
            panel.setHosts(List.of(prod), Optional.empty()); panel.select(prod.id());
            var progress = new ConnectionPanel("prod", "user@host", () -> {}, () -> {});
            Object previous = javax.swing.UIManager.get("Label.font");
            try {
                javax.swing.UIManager.put("Label.font", new javax.swing.plaf.FontUIResource("Serif", java.awt.Font.PLAIN, 20));
                javax.swing.SwingUtilities.updateComponentTreeUI(panel);
                javax.swing.SwingUtilities.updateComponentTreeUI(progress);
                assertThat(panel.cardName.getFont().getFamily()).isEqualTo(java.awt.Font.SERIF);
                assertThat(panel.cardName.getFont().getSize2D()).isEqualTo(20);
                assertThat(panel.cardName.getFont().isBold()).isTrue();
                var heading = (javax.swing.JPanel) progress.getComponent(0);
                var title = (javax.swing.JLabel) heading.getComponent(0);
                assertThat(title.getFont().getFamily()).isEqualTo(java.awt.Font.SERIF);
                assertThat(title.getFont().getSize2D()).isEqualTo(23);
            } finally { javax.swing.UIManager.put("Label.font", previous); }
        });
    }

    @Test void showsRowsCardAndRunsActions() {
        var panel = new HostsPanel(actions);
        assertThat(panel.empty.isVisible()).isTrue();
        panel.setCredentialLabels(host -> host.auth() instanceof Auth.Agent ? "SSH agent" : "Work key");
        panel.setHosts(List.of(prod, nas), Optional.empty());
        assertThat(panel.empty.isVisible()).isFalse();
        assertThat(panel.list.getModel().getSize()).as("two groups, two hosts").isEqualTo(4);
        panel.select(nas.id());
        assertThat(panel.card.isVisible()).isTrue();
        assertThat(panel.cardName.getText()).isEqualTo("nas");
        assertThat(panel.cardAddress.getText()).isEqualTo("me@nas.local:2222");
        assertThat(panel.cardCredential.getText()).isEqualTo("SSH agent");
        assertThat(panel.cardJump.getText()).isEqualTo("via prod");
        panel.connect.doClick();
        panel.edit.doClick();
        panel.activate(panel.list.getSelectedIndex());
        panel.add.doClick();
        panel.importButton.doClick();
        assertThat(events).containsExactly("connect nas", "edit nas", "connect nas", "edit new", "import");
        events.clear();
        javax.swing.JPopupMenu menu = panel.menuFor(panel.list.getSelectedIndex());
        assertThat(menu.getComponentCount()).isEqualTo(6);
        ((javax.swing.JMenuItem) menu.getComponent(1)).doClick();
        ((javax.swing.JMenuItem) menu.getComponent(5)).doClick();
        assertThat(events).containsExactly("split nas", "favorite nas true");
        panel.select(prod.id());
        assertThat(panel.cardCredential.getText()).isEqualTo("Work key");
        assertThat(panel.cardJump.isVisible()).isFalse();
    }

    @Test void searchFiltersAndCollapseIsRemembered() {
        var panel = new HostsPanel(actions);
        panel.setHosts(List.of(prod, nas), Optional.empty());
        int[] collapsedChanges = {0};
        panel.onCollapsedChanged(() -> collapsedChanges[0]++);
        panel.toggle(0);
        assertThat(panel.collapsed()).containsExactly("Production");
        assertThat(panel.list.getModel().getSize()).isEqualTo(3);
        assertThat(collapsedChanges[0]).isEqualTo(1);
        panel.search.setText("prod");
        assertThat(panel.list.getModel().getSize()).as("search reveals the collapsed group").isEqualTo(2);
        assertThat(panel.collapsed()).as("without changing the saved state").containsExactly("Production");
        panel.search.setText("zzz");
        assertThat(panel.list.getModel().getSize()).isZero();
        assertThat(panel.empty.getText()).contains("No hosts match");
        panel.search.setText("");
        panel.setCollapsed(Set.of());
        assertThat(panel.list.getModel().getSize()).isEqualTo(4);
        panel.setHosts(List.of(prod, nas), Optional.of("hosts.toml has TOML errors"));
        assertThat(panel.list.getModel().getSize()).as("an error row leads").isEqualTo(5);
        assertThat(panel.list.getModel().getElementAt(0)).isInstanceOf(HostRows.Error.class);
    }
    @Test void clearsRemovedSelectionAndBindsEnterAndFavoriteClick() {
        var panel = new HostsPanel(actions);
        panel.setHosts(List.of(nas), Optional.empty());
        panel.select(nas.id());
        Object action = panel.list.getInputMap().get(javax.swing.KeyStroke.getKeyStroke("ENTER"));
        assertThat(action).isNotNull();
        panel.list.getActionMap().get(action).actionPerformed(new java.awt.event.ActionEvent(panel.list, 0, ""));
        panel.list.setSize(300, 100);
        var bounds = panel.list.getCellBounds(1, 1);
        var click = new java.awt.event.MouseEvent(panel.list, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 8, bounds.y + bounds.height / 2, 1, false, java.awt.event.MouseEvent.BUTTON1);
        for (var listener : panel.list.getMouseListeners()) listener.mouseClicked(click);
        assertThat(events).containsExactly("connect nas", "favorite nas true");
        panel.setHosts(List.of(), Optional.empty());
        assertThat(panel.card.isVisible()).isFalse();
    }

    @Test void rowsKeepSessionCountsAndSearchableDetails() {
        var panel = new HostsPanel(actions);
        panel.setHostDetails(host -> host.id().equals(nas.id()) ? "Ubuntu \u00B7 10.0.0.2" : "macOS", host -> host.id().equals(nas.id()) ? 2 : 0);
        panel.setHosts(List.of(nas, prod), Optional.empty());
        panel.search.setText("ubuntu");
        assertThat(panel.list.getModel().getSize()).isEqualTo(2);
        var component = panel.list.getCellRenderer().getListCellRendererComponent(panel.list, panel.list.getModel().getElementAt(1), 1, false, false);
        assertThat(labels(component)).contains("nas");
        assertThat(component.getAccessibleContext().getAccessibleName()).contains("2 sessions");
        assertThat(((javax.swing.JComponent) component).getToolTipText()).contains("me@nas.local:2222", "Ubuntu \u00B7 10.0.0.2");
        panel.select(nas.id());
        assertThat(panel.cardAddress.getText()).isEqualTo("me@nas.local:2222");
        assertThat(panel.cardInfo.getText()).isEqualTo("Ubuntu \u00B7 10.0.0.2");
    }
    private static List<String> labels(java.awt.Component component) {
        var out = new ArrayList<String>();
        if (component instanceof javax.swing.JLabel label) out.add(label.getText());
        if (component instanceof java.awt.Container container) for (var child : container.getComponents()) out.addAll(labels(child));
        return out;
    }

    @Test void rowsExposeTheirIdentityToAssistiveTechnology() {
        var panel = new HostsPanel(actions);
        panel.setHostDetails(host -> "Ubuntu", host -> 1);
        panel.setHosts(List.of(nas), Optional.empty());
        var row = panel.list.getAccessibleContext().getAccessibleChild(1).getAccessibleContext();
        assertThat(row.getAccessibleName()).contains("nas", "me@nas.local:2222", "Ubuntu", "1 session");
    }

    @Test void keyboardSelectionIsVisibleOnGroupHeaders() {
        var panel = new HostsPanel(actions); panel.setHosts(List.of(nas), Optional.empty());
        var renderer = panel.list.getCellRenderer().getListCellRendererComponent(panel.list, panel.list.getModel().getElementAt(0), 0, true, true);
        assertThat(((javax.swing.JComponent) renderer).isOpaque()).isTrue();
        assertThat(renderer.getBackground()).isEqualTo(panel.list.getSelectionBackground());
    }

}
