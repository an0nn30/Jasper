package dev.jasper.remote.ui;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HostsPanelTest {
    final List<String> events = new ArrayList<>();
    final HostsPanel.Actions actions = new HostsPanel.Actions(h -> events.add("connect " + h.name()), h -> events.add("split " + h.name()),
        h -> events.add("edit " + h.map(RemoteHost::name).orElse("new")), h -> events.add("duplicate " + h.name()), h -> events.add("delete " + h.name()),
        (h, favorite) -> events.add("favorite " + h.name() + " " + favorite), () -> events.add("import"));
    final RemoteHost prod = RemoteHost.create("prod", "api.example", 22, "deploy", new Auth.Vault(UUID.randomUUID()), "Production", Optional.empty());
    final RemoteHost nas = RemoteHost.create("nas", "nas.local", 2222, "me", Auth.AGENT, "", Optional.of(prod.id()));

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
        panel.setHostDetails(host -> host.id().equals(nas.id()) ? "Ubuntu · 10.0.0.2" : "macOS", host -> host.id().equals(nas.id()) ? 2 : 0);
        panel.setHosts(List.of(nas, prod), Optional.empty());
        panel.search.setText("ubuntu");
        assertThat(panel.list.getModel().getSize()).isEqualTo(2);
        var component = panel.list.getCellRenderer().getListCellRendererComponent(panel.list, panel.list.getModel().getElementAt(1), 1, false, false);
        assertThat(labels(component)).contains("nas");
        assertThat(component.getAccessibleContext().getAccessibleName()).contains("2 sessions");
        assertThat(((javax.swing.JComponent) component).getToolTipText()).contains("me@nas.local:2222", "Ubuntu · 10.0.0.2");
        panel.select(nas.id());
        assertThat(panel.cardAddress.getText()).isEqualTo("me@nas.local:2222");
        assertThat(panel.cardInfo.getText()).isEqualTo("Ubuntu · 10.0.0.2");
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
