package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.persistence.UiState;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class WindowPanelsTest {
    @TempDir Path dir;

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); edt(() -> new dev.jasper.app.appearance.ThemeController()); }

    private static WindowContent window() { return DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>())); }

    private static void toggle(Contributions model, WindowContent owner, String id) {
        model.requestPanel(new Contributions.PanelRequest(owner.id(), id, Contributions.PanelRequest.Op.TOGGLE));
    }

    @Test void anUnconnectedOrEmptyWindowHasNoRailAndTheOldLayout() throws Exception {
        edt(() -> {
            WindowContent owner = window();
            assertThat(owner.rail().isVisible()).isFalse();
            owner.connectContributions(new Contributions());
            assertThat(owner.rail().isVisible()).isFalse();
            assertThat(owner.regions().content(PanelRegion.LEFT)).isNull();
        });
    }

    @Test void panelsAreLazyPerWindowToggleFromTheRailAndShareARegionOneAtATime() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            List<PanelSite> sites = new ArrayList<>();
            List<String> events = new ArrayList<>();
            var hostsView = new JLabel("hosts");
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), PanelRegion.LEFT, site -> {
                sites.add(site);
                site.onVisibility(visible -> events.add("hosts:" + visible));
                site.onClosed(() -> events.add("hosts:closed"));
                return hostsView;
            });
            PanelEntry files = model.addPanel("dev.x.files", "Files", new ImageIcon(), PanelRegion.LEFT, site -> new JLabel("files"));
            assertThat(owner.rail().isVisible()).isTrue();
            assertThat(owner.rail().buttons()).extracting(AbstractButton::getToolTipText).containsExactly("Hosts", "Files");
            assertThat(sites).as("no instance until first shown").isEmpty();

            owner.rail().buttons().get(0).doClick();
            assertThat(sites).singleElement().satisfies(site -> {
                assertThat(site.windowId()).isEqualTo(owner.id());
                assertThat(site.visible()).isTrue();
            });
            assertThat(SwingUtilities.isDescendingFrom(hostsView, owner)).isTrue();
            assertThat(owner.regions().content(PanelRegion.LEFT)).isSameAs(hostsView);
            assertThat(owner.rail().buttons().get(0).isSelected()).isTrue();

            toggle(model, owner, "dev.x.files");
            assertThat(owner.regions().content(PanelRegion.LEFT)).isNotSameAs(hostsView);
            assertThat(owner.rail().buttons()).extracting(AbstractButton::isSelected).containsExactly(false, true);
            toggle(model, owner, "dev.x.hosts");
            assertThat(sites).as("the instance is reused").hasSize(1);
            sites.get(0).hide();
            assertThat(owner.regions().content(PanelRegion.LEFT)).isNull();
            assertThat(events).containsExactly("hosts:true", "hosts:false", "hosts:true", "hosts:false");

            model.requestPanel(new Contributions.PanelRequest(java.util.UUID.randomUUID(), "dev.x.hosts", Contributions.PanelRequest.Op.SHOW));
            assertThat(owner.regions().content(PanelRegion.LEFT)).as("another window's request").isNull();

            files.close();
            assertThat(owner.rail().buttons()).hasSize(1);
        });
    }

    @Test void movesPersistAndANewWindowRestoresThem() throws Exception {
        edt(() -> {
            var model = new Contributions();
            Path file = dir.resolve("ui-state.toml");
            WindowContent first = window();
            first.connectContributions(model, UiState.load(file));
            List<String> created = new ArrayList<>();
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), PanelRegion.LEFT, site -> { created.add("hosts"); return new JLabel("hosts"); });
            toggle(model, first, "dev.x.hosts");
            first.rail().onMove.accept("dev.x.hosts", PanelRegion.RIGHT);
            assertThat(first.regions().content(PanelRegion.LEFT)).isNull();
            assertThat(first.regions().content(PanelRegion.RIGHT)).isNotNull();
            assertThat(UiState.load(file).panel("dev.x.hosts")).get().satisfies(panel -> {
                assertThat(panel.region()).isEqualTo("RIGHT");
                assertThat(panel.visible()).isTrue();
            });

            WindowContent second = window();
            second.connectContributions(model, UiState.load(file));
            assertThat(second.regions().content(PanelRegion.RIGHT)).as("restored visible on the right").isNotNull();
            assertThat(created).hasSize(2);
        });
    }

    @Test void aFailedFactoryShowsAPlaceholderAndRemovalOrCloseNotifiesTheInstance() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            List<String> events = new ArrayList<>();
            model.addPanel("dev.x.broken", "Broken", new ImageIcon(), PanelRegion.BOTTOM, site -> null);
            PanelEntry live = model.addPanel("dev.x.live", "Live", new ImageIcon(), PanelRegion.RIGHT, site -> {
                site.onClosed(() -> events.add("closed"));
                return new JLabel("live");
            });
            toggle(model, owner, "dev.x.broken");
            JComponent placeholder = owner.regions().content(PanelRegion.BOTTOM);
            assertThat(placeholder).isInstanceOf(JLabel.class);
            assertThat(((JLabel) placeholder).getText()).contains("could not be loaded");

            toggle(model, owner, "dev.x.live");
            live.close();
            assertThat(owner.regions().content(PanelRegion.RIGHT)).isNull();
            assertThat(events).containsExactly("closed");
        });
    }

    @Test void railActionsAndTheRailToggleCommand() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var state = UiState.inMemory();
            WindowContent owner = window();
            owner.connectContributions(model, state);
            owner.setActive(true);
            List<Contributions.Invocation> seen = new ArrayList<>();
            model.addAction("dev.x.open", "Open Manager", new ImageIcon(), List.of(), Optional.empty(), seen::add);
            model.addRailAction("dev.x.open");
            model.addRailAction("dev.x.never-registered");
            assertThat(owner.rail().isVisible()).isTrue();
            assertThat(owner.rail().buttons()).singleElement().satisfies(button -> {
                assertThat(button.getToolTipText()).isEqualTo("Open Manager");
                button.doClick();
            });
            assertThat(seen).hasSize(1);

            owner.dispatchCommand(owner.commands().find("view.rail").orElseThrow());
            assertThat(owner.rail().isVisible()).isFalse();
            assertThat(state.railVisible()).isFalse();
            owner.setRailVisible(true);
            assertThat(owner.rail().isVisible()).isTrue();
        });
    }

    @Test void hiddenPanelKeepsItsComponentAndHandlersAcrossADelegateThemeUpdate() throws Exception {
        edt(() -> {
            var themes = new dev.jasper.app.appearance.ThemeController(dev.jasper.app.config.Appearance.DARK);
            var owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), themes);
            var model = new Contributions(); owner.connectContributions(model, UiState.inMemory());
            var clicks = new java.util.concurrent.atomic.AtomicInteger();
            var button = new javax.swing.JButton("Plugin action");
            button.addActionListener(event -> clicks.incrementAndGet());
            var instances = new java.util.concurrent.atomic.AtomicInteger();
            model.addPanel("dev.x.hidden", "Hidden panel", new ImageIcon(), PanelRegion.LEFT, site -> {
                instances.incrementAndGet(); return button;
            });
            toggle(model, owner, "dev.x.hidden");
            toggle(model, owner, "dev.x.hidden");
            owner.applyTheme(owner.theme(), true);
            toggle(model, owner, "dev.x.hidden");
            assertThat(owner.regions().content(PanelRegion.LEFT)).isSameAs(button);
            assertThat(instances.get()).isEqualTo(1);
            button.doClick(); assertThat(clicks.get()).isEqualTo(1);
        });
    }
}
