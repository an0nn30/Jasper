package dev.jasper.app.contributions;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class PanelContributionsTest {
    private final Contributions model = new Contributions();

    @Test void aPanelBringsItsToggleActionAndViewMenuEntryAndTakesThemWithIt() {
        List<Contributions.Kind> changes = new ArrayList<>();
        List<Contributions.PanelRequest> requests = new ArrayList<>();
        model.onChanged(changes::add);
        model.onPanelRequest(requests::add);
        PanelEntry hosts = model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), PanelRegion.LEFT, site -> new JLabel("hosts"));
        assertThatIllegalArgumentException().isThrownBy(() ->
            model.addPanel("dev.x.hosts", "Again", new ImageIcon(), PanelRegion.RIGHT, site -> new JLabel()));
        assertThat(model.panels()).containsExactly(hosts);
        assertThat(changes).contains(Contributions.Kind.PANELS, Contributions.Kind.ACTIONS, Contributions.Kind.MENUS);

        ActionEntry toggle = model.action("dev.x.hosts.toggle").orElseThrow();
        assertThat(toggle.title()).isEqualTo("Toggle Hosts");
        UUID window = UUID.randomUUID();
        toggle.invoke(new Contributions.Invocation(window, Optional.empty()));
        assertThat(requests).containsExactly(new Contributions.PanelRequest(window, "dev.x.hosts", Contributions.PanelRequest.Op.TOGGLE));
        assertThat(model.menus()).singleElement().satisfies(section -> {
            assertThat(section.target()).isEqualTo(MenuTarget.standard(MenuTarget.Slot.VIEW));
            assertThat(section.entries()).containsExactly(new MenuEntry.Submenu("Panels", List.of(new MenuEntry.Item("dev.x.hosts.toggle"))));
        });

        hosts.close();
        hosts.close();
        assertThat(model.panels()).isEmpty();
        assertThat(model.action("dev.x.hosts.toggle")).isEmpty();
        assertThat(model.menus()).isEmpty();
    }

    @Test void railActionsKeepOrderAndSitesNotifyOnce() {
        List<Contributions.Kind> changes = new ArrayList<>();
        model.onChanged(changes::add);
        var first = model.addRailAction("dev.x.open");
        model.addRailAction("dev.x.other");
        first.close();
        assertThat(model.railActions()).containsExactly("dev.x.other");
        assertThat(changes).containsOnly(Contributions.Kind.RAIL);

        List<String> events = new ArrayList<>();
        boolean[] visible = {false};
        var site = new PanelSite(UUID.randomUUID(), () -> events.add("show"), () -> events.add("hide"), () -> visible[0]);
        var watching = site.onVisibility(value -> events.add("visible:" + value));
        site.onVisibility(value -> { throw new IllegalStateException("listener failure"); });
        site.onClosed(() -> events.add("closed"));
        site.show(); site.hide();
        site.notifyVisibility(true);
        watching.close();
        site.notifyVisibility(false);
        site.notifyClosed(); site.notifyClosed();
        assertThat(events).containsExactly("show", "hide", "visible:true", "closed");
        assertThat(site.visible()).isFalse();
    }
}
