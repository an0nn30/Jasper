package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Anchor;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PanelHost;
import dev.jasper.sdk.ui.PanelSpec;
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

    @Test void panelHeadersContainFailuresAndIgnoreRetainedActionsAfterClose() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> {});
            var panels = new ArrayList<PanelHost>();
            var calls = new ArrayList<String>();
            String id = "dev.x.tool.headers";
            var registration = context.panels().register(new PanelSpec(id, "Headers", "file", Anchor.LEFT),
                panel -> { panels.add(panel); return KitFixtures.view(context); });
            host.addTerminalWindow();
            host.openPanel(id); host.openPanel(id);
            assertThat(panels).as("one instance, in the main window").hasSize(1);
            var icon = "file";
            panels.getFirst().setHeader(new dev.jasper.sdk.ui.PanelHeader("5", false, List.of(
                new dev.jasper.sdk.ui.PanelHeader.Action("Add", icon, () -> calls.add("first")),
                new dev.jasper.sdk.ui.PanelHeader.Action("Failure", icon, () -> { throw new IllegalStateException("header failed"); }),
                new dev.jasper.sdk.ui.PanelHeader.Action("Linkage failure", icon, () -> { throw new NoClassDefFoundError("header dependency"); }))));
            var stale = host.panelHeader(id);
            assertThat(stale.detail()).isEqualTo("5");
            panels.getFirst().setHeader(new dev.jasper.sdk.ui.PanelHeader("amp", true, List.of(
                new dev.jasper.sdk.ui.PanelHeader.Action("Add", icon, () -> calls.add("second")))));
            assertThat(host.panelHeader(id).detail()).isEqualTo("amp");
            stale.actions().forEach(action -> action.invoke().run());
            host.panelHeader(id).actions().getFirst().invoke().run();
            assertThat(host.failures()).hasSize(2);
            registration.close();
            panels.getFirst().setHeader(new dev.jasper.sdk.ui.PanelHeader("late", false, List.of()));
            stale.actions().forEach(action -> action.invoke().run());
            assertThat(host.panelHeader(id)).isEqualTo(dev.jasper.sdk.ui.PanelHeader.EMPTY);
            assertThat(calls).containsExactly("first", "second");
            assertThat(host.failures()).hasSize(2);
        }
    }); }

    @Test void aPlacementHandlerThatThrowsIsRecordedAndTheOthersStillHearTheMove() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> {});
            var heard = new ArrayList<dev.jasper.sdk.ui.PanelPlacement>();
            String id = "dev.x.tool.placed";
            context.panels().register(new PanelSpec(id, "Placed", "file", Anchor.BOTTOM), panel -> {
                panel.onPlacement(placement -> { throw new IllegalStateException("placement failed"); });
                panel.onPlacement(heard::add);
                return KitFixtures.view(context);
            });
            var moved = new dev.jasper.sdk.ui.PanelPlacement(Anchor.LEFT, true, dev.jasper.sdk.ui.PanelMode.WINDOW);
            host.placePanel(id, moved);
            host.addTerminalWindow();
            host.placePanel(id, moved);
            assertThat(heard).as("a panel that is not open is not moved").isEmpty();
            host.openPanel(id);
            host.placePanel(id, moved);
            assertThat(heard).containsExactly(moved);
            assertThat(host.failures()).singleElement().asString().contains("dev.x.tool", "panel placement", "placement failed");
            host.placePanel(id, moved);
            assertThat(heard).hasSize(1);
            assertThat(host.failures()).as("the same placement is silent").hasSize(1);
        }
    }); }

    @Test void fileSelectionWorksForDialogsAndRequiresALiveShownOwner(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> { });
            var tab = context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("manager", "Manager"), null, KitFixtures.view(context));
            var dialog = context.windows().dialog(new DialogSpec("Edit", tab, true));
            var initial = java.util.Optional.of(directory.resolve("original"));
            assertThatIllegalStateException().isThrownBy(() -> dialog.chooseFile("Choose key", initial));
            dialog.show();
            host.queueFileSelection(java.util.Optional.of(directory.resolve("selected")));
            assertThat(dialog.chooseFile("Choose key", initial)).contains(directory.resolve("selected"));
            assertThat(dialog.chooseFile("Choose key", initial)).isEmpty();
            assertThatIllegalArgumentException().isThrownBy(() -> dialog.chooseFile(" ", initial));
            tab.close();
            assertThatIllegalStateException().isThrownBy(() -> dialog.chooseFile("Choose key", initial));
        }
    }); }

    @Test void recordsPanelsRailAndDialogs() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
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
            assertThat(host.openPanel("dev.x.tool.hosts")).as("no main window to show it in").isNull();
            UUID window = host.addTerminalWindow();
            assertThat(host.openPanel("dev.x.tool.hosts")).isNotNull();
            assertThat(hosts.get(0).window().id()).isEqualTo(window);
            assertThat(hosts.get(0).visible()).isTrue();
            assertThat(host.openPanel("dev.x.tool.broken")).isNull();
            assertThat(host.openPanel("dev.x.tool.absent")).isNull();
            assertThat(host.failures()).hasSize(1);

            var manager = context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("manager", "Manager"), null, KitFixtures.view(context));
            var unlock = context.windows().dialog(new DialogSpec("Unlock", manager, true));
            var veto = unlock.onClosing(() -> false);
            unlock.show();
            assertThat(host.windows()).containsExactly("dialog|Unlock|true");
            assertThat(host.requestClose("dialog")).isFalse();
            veto.close();
            manager.close();
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

    /** Workspace tabs spec 14: panels live in the main window; a child window's toggle shows the panel there. */
    @Test void aChildWindowsToggleShowsThePanelInTheMainWindow() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> {});
            List<PanelHost> instances = new ArrayList<>();
            context.panels().register(new PanelSpec("dev.x.tool.hosts", "Hosts", "file", Anchor.LEFT), panel -> { instances.add(panel); return KitFixtures.view(context); });
            UUID main = host.addTerminalWindow(), child = host.addTerminalWindow();
            context.panels().toggle("dev.x.tool.hosts", context.terminals().window(child).orElseThrow());
            assertThat(instances).singleElement().satisfies(panel -> {
                assertThat(panel.window().id()).isEqualTo(main);
                assertThat(panel.window().isMain()).isTrue();
                assertThat(panel.visible()).isTrue();
            });
            context.panels().toggle("dev.x.tool.hosts", context.terminals().window(child).orElseThrow());
            assertThat(instances).as("a child window's toggle shows it again, as the application's does").singleElement()
                .satisfies(panel -> assertThat(panel.visible()).isTrue());
            context.panels().toggle("dev.x.tool.hosts", context.terminals().window(main).orElseThrow());
            assertThat(instances).as("one instance, hidden by the main window's toggle").singleElement()
                .satisfies(panel -> assertThat(panel.visible()).isFalse());
            assertThat(host.openRequests()).as("each child window's toggle raises the main window; the main window's raises nothing")
                .containsExactly("front|" + main, "front|" + main);
        }
    }); }

}
