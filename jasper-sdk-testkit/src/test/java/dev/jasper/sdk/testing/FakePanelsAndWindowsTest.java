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

    @Test void panelHeadersAreIndependentContainFailuresAndIgnoreRetainedActionsAfterClose() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> {});
            var panels = new ArrayList<PanelHost>();
            var calls = new ArrayList<String>();
            String id = "dev.x.tool.headers";
            var registration = context.panels().register(new PanelSpec(id, "Headers", "file", Anchor.LEFT),
                panel -> { panels.add(panel); return KitFixtures.view(context); });
            UUID first = UUID.randomUUID(), second = UUID.randomUUID();
            host.openPanel(id, first); host.openPanel(id, second);
            var icon = "file";
            panels.getFirst().setHeader(new dev.jasper.sdk.ui.PanelHeader("5", false, List.of(
                new dev.jasper.sdk.ui.PanelHeader.Action("Add", icon, () -> calls.add("first")),
                new dev.jasper.sdk.ui.PanelHeader.Action("Failure", icon, () -> { throw new IllegalStateException("header failed"); }),
                new dev.jasper.sdk.ui.PanelHeader.Action("Linkage failure", icon, () -> { throw new NoClassDefFoundError("header dependency"); }))));
            panels.getLast().setHeader(new dev.jasper.sdk.ui.PanelHeader("amp", true, List.of(
                new dev.jasper.sdk.ui.PanelHeader.Action("Add", icon, () -> calls.add("second")))));
            var stale = host.panelHeader(id, first);
            assertThat(stale.detail()).isEqualTo("5");
            assertThat(host.panelHeader(id, second).detail()).isEqualTo("amp");
            stale.actions().forEach(action -> action.invoke().run());
            host.panelHeader(id, second).actions().getFirst().invoke().run();
            assertThat(host.failures()).hasSize(2);
            registration.close();
            panels.getFirst().setHeader(new dev.jasper.sdk.ui.PanelHeader("late", false, List.of()));
            stale.actions().forEach(action -> action.invoke().run());
            assertThat(host.panelHeader(id, first)).isEqualTo(dev.jasper.sdk.ui.PanelHeader.EMPTY);
            assertThat(host.panelHeader(id, second)).isEqualTo(dev.jasper.sdk.ui.PanelHeader.EMPTY);
            assertThat(calls).containsExactly("first", "second");
            assertThat(host.failures()).hasSize(2);
        }
    }); }

    @Test void fileSelectionWorksForWindowsAndDialogsAndRequiresALiveShownOwner(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
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
    }); }

    @Test void recordsPanelsRailAndWindows() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> { });
            List<PanelHost> hosts = new ArrayList<>();
            context.actions().register(ActionSpec.of("dev.x.tool.open", "Open"), invoked -> { });
            context.panels().register(new PanelSpec("dev.x.tool.hosts", "Hosts", "file", Anchor.RIGHT),
                panelHost -> { hosts.add(panelHost); return KitFixtures.view(context); });
            context.panels().register(new PanelSpec("dev.x.tool.broken", "Broken", "file", Anchor.LEFT),
                panelHost -> { throw new IllegalStateException("factory failure"); });
            context.rail().add("dev.x.tool.open");
            assertThat(host.panels()).containsExactly("dev.x.tool.hosts|Hosts|RIGHT", "dev.x.tool.broken|Broken|LEFT");
            assertThat(host.rail()).containsExactly("dev.x.tool.open");
            UUID window = UUID.randomUUID();
            assertThat(host.openPanel("dev.x.tool.hosts", window)).isNotNull();
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
    }); }
    @Test void toggleCreatesLazilyThenReusesAndHidesTheWindowPanel() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> {});
            List<PanelHost> instances = new ArrayList<>();
            context.panels().register(new PanelSpec("dev.x.tool.hosts", "Hosts", "file", Anchor.LEFT), panel -> { instances.add(panel); return KitFixtures.view(context); });
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
    }); }

}
