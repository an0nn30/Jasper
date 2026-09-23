package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Anchor;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PanelHost;
import dev.jasper.sdk.ui.PanelSpec;
import dev.jasper.sdk.ui.WindowSpec;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FakePanelsAndWindowsTest {
    private static final PluginInfo INFO = new PluginInfo("dev.x.tool", "Tool", "1.0.0", Set.of());

    @Test void fileSelectionWorksForWindowsAndDialogsAndRequiresALiveShownOwner(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> { });
            var window = context.windows().create(new WindowSpec("dev.x.tool.manager", "Manager", new Dimension(640, 480), true));
            var dialog = context.windows().dialog(new DialogSpec("Edit", window, true));
            var initial = java.util.Optional.of(directory.resolve("original"));
            for (var surface : java.util.List.<dev.jasper.sdk.ui.WindowSurface>of(window, dialog)) {
                assertThatIllegalStateException().isThrownBy(() -> surface.chooseFile("Choose key", initial));
                surface.show();
                host.queueFileSelection(java.util.Optional.of(directory.resolve("selected")));
                assertThat(surface.chooseFile("Choose key", initial)).contains(directory.resolve("selected"));
                assertThat(surface.chooseFile("Choose key", initial)).isEmpty();
                assertThatIllegalArgumentException().isThrownBy(() -> surface.chooseFile(" ", initial));
            }
            window.close();
            assertThatIllegalStateException().isThrownBy(() -> dialog.chooseFile("Choose key", initial));
        }
    }

    @Test void recordsPanelsRailAndWindows() {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> { });
            List<PanelHost> hosts = new ArrayList<>();
            context.actions().register(ActionSpec.of("dev.x.tool.open", "Open"), invoked -> { });
            context.panels().register(new PanelSpec("dev.x.tool.hosts", "Hosts", new ImageIcon(), Anchor.RIGHT),
                panelHost -> { hosts.add(panelHost); return new JLabel("hosts"); });
            context.panels().register(new PanelSpec("dev.x.tool.broken", "Broken", new ImageIcon(), Anchor.LEFT),
                panelHost -> { throw new IllegalStateException("factory failure"); });
            context.rail().add("dev.x.tool.open");
            assertThat(host.panels()).containsExactly("dev.x.tool.hosts|Hosts|RIGHT", "dev.x.tool.broken|Broken|LEFT");
            assertThat(host.rail()).containsExactly("dev.x.tool.open");
            UUID window = UUID.randomUUID();
            assertThat(host.openPanel("dev.x.tool.hosts", window)).isInstanceOf(JLabel.class);
            assertThat(hosts.get(0).window().id()).isEqualTo(window);
            assertThat(hosts.get(0).visible()).isTrue();
            assertThat(host.openPanel("dev.x.tool.broken", window)).isNull();
            assertThat(host.openPanel("dev.x.tool.absent", window)).isNull();
            assertThat(host.failures()).hasSize(1);

            var manager = context.windows().create(new WindowSpec("dev.x.tool.manager", "Manager", new Dimension(640, 480), true));
            assertThat(context.windows().create(new WindowSpec("dev.x.tool.manager", "Manager", new Dimension(640, 480), true))).isSameAs(manager);
            var veto = manager.onClosing(() -> false);
            context.windows().dialog(new DialogSpec("Unlock", manager, true)).show();
            assertThat(host.windows()).containsExactly("dev.x.tool.manager|Manager|false", "dialog|Unlock|true");
            manager.show();
            assertThat(host.requestClose("dev.x.tool.manager")).isFalse();
            veto.close();
            assertThat(host.requestClose("dev.x.tool.manager")).isTrue();
            assertThat(host.windows()).as("the dialog went with its owner").isEmpty();
            assertThatIllegalArgumentException().isThrownBy(() -> context.windows().dialog(new DialogSpec("Late", manager, true)));
        }
    }
    @Test void toggleCreatesLazilyThenReusesAndHidesTheWindowPanel() {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> {});
            List<PanelHost> instances = new ArrayList<>();
            context.panels().register(new PanelSpec("dev.x.tool.hosts", "Hosts", new ImageIcon(), Anchor.LEFT), panel -> { instances.add(panel); return new JLabel("Hosts"); });
            UUID id = host.addTerminalWindow();
            var window = context.terminals().windows().getFirst();
            context.panels().toggle("dev.x.tool.hosts", window);
            assertThat(instances).singleElement().satisfies(panel -> assertThat(panel.visible()).isTrue());
            context.panels().toggle("dev.x.tool.hosts", window);
            assertThat(instances).singleElement().satisfies(panel -> assertThat(panel.visible()).isFalse());
            context.panels().toggle("dev.x.tool.hosts", window);
            assertThat(instances).singleElement().satisfies(panel -> assertThat(panel.visible()).isTrue());
            assertThatIllegalArgumentException().isThrownBy(() -> context.panels().toggle("dev.other.panel", window));
        }
    }

}
