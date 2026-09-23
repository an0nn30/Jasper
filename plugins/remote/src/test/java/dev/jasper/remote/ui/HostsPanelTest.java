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
}
