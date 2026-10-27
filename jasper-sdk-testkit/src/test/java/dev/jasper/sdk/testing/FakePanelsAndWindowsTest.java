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
            assertThat(panels).as("one instance, in the window").hasSize(1);
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
            host.addTerminalWindow();
            host.openPanel(id);
            host.placePanel(id, moved);
            assertThat(heard).containsExactly(moved);
            assertThat(host.failures()).singleElement().asString().contains("dev.x.tool", "panel placement", "placement failed");
            host.placePanel(id, moved);
            assertThat(heard).hasSize(1);
            assertThat(host.failures()).as("the same placement is silent").hasSize(1);
        }
    }); }

    /**
     * The app contains a handler's {@code Exception} or {@code LinkageError} (a plugin's missing optional dependency):
     * the fake records a {@code LinkageError} from a placement, visibility or close handler, and the others still run.
     */
    @Test void aPanelHandlerThatThrowsALinkageErrorIsRecordedAndTheOthersStillRun() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> {});
            var heard = new ArrayList<String>();
            String id = "dev.x.tool.linked";
            context.panels().register(new PanelSpec(id, "Linked", "file", Anchor.BOTTOM), panel -> {
                panel.onPlacement(placement -> { throw new NoClassDefFoundError("placement dependency"); });
                panel.onPlacement(placement -> heard.add("placement " + placement.mode()));
                panel.onVisibility(visible -> { throw new NoClassDefFoundError("visibility dependency"); });
                panel.onVisibility(visible -> heard.add("visible " + visible));
                panel.onClosed(() -> { throw new NoClassDefFoundError("closed dependency"); });
                panel.onClosed(() -> heard.add("closed"));
                return KitFixtures.view(context);
            });
            host.addTerminalWindow();
            host.openPanel(id);
            host.placePanel(id, new dev.jasper.sdk.ui.PanelPlacement(Anchor.BOTTOM, false, dev.jasper.sdk.ui.PanelMode.FLOAT));
            context.panels().toggle(id, context.terminals().windows().getFirst());
            host.closePanel(id);
            assertThat(heard).containsExactly("placement FLOAT", "visible false", "closed");
            assertThat(host.failures()).hasSize(3).anySatisfy(line -> assertThat(line).contains("panel placement", "placement dependency"))
                .anySatisfy(line -> assertThat(line).contains("panel visibility", "visibility dependency"))
                .anySatisfy(line -> assertThat(line).contains("panel closed", "closed dependency"));
        }
    }); }

    /** As the app's site: a placement handler that removes the panel ends the round, and the handlers after it hear nothing. */
    @Test void aPanelClosedByAPlacementHandlerTellsTheHandlersAfterItNothing() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> {});
            var heard = new ArrayList<String>();
            String id = "dev.x.tool.removed";
            dev.jasper.sdk.Subscription[] registration = {null};
            registration[0] = context.panels().register(new PanelSpec(id, "Removed", "file", Anchor.BOTTOM), panel -> {
                panel.onPlacement(placement -> { heard.add("first"); registration[0].close(); });
                panel.onPlacement(placement -> heard.add("second"));
                panel.onClosed(() -> heard.add("closed " + panel.placement().mode()));
                return KitFixtures.view(context);
            });
            host.addTerminalWindow();
            host.openPanel(id);
            host.placePanel(id, new dev.jasper.sdk.ui.PanelPlacement(Anchor.LEFT, false, dev.jasper.sdk.ui.PanelMode.WINDOW));
            assertThat(heard).containsExactly("first", "closed WINDOW");
            assertThat(host.failures()).isEmpty();
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
            assertThat(host.openPanel("dev.x.tool.hosts")).as("no window to show it in").isNull();
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

    /** Full windows spec 9: every window's dock builds its own instance of a panel, and each closes with its window. */
    @Test void eachWindowGetsItsOwnPanelInstance() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> {});
            List<PanelHost> built = new ArrayList<>();
            String id = "dev.x.tool.hosts";
            context.panels().register(new PanelSpec(id, "Hosts", "file", Anchor.LEFT), panel -> { built.add(panel); return KitFixtures.view(context); });
            UUID a = host.addTerminalWindow(), b = host.addTerminalWindow();
            UUID only = host.addTerminalTab(a, "zsh");
            host.addTerminalTab(b, "zsh");
            var first = context.terminals().window(a).orElseThrow();
            context.panels().show(id, first);
            context.panels().show(id, context.terminals().window(b).orElseThrow());
            context.panels().show(id, first);
            assertThat(built).as("the factory ran once per window").hasSize(2);
            assertThat(built.get(0).window()).isEqualTo(first);
            assertThat(built.get(1).window().id()).isEqualTo(b);
            var closed = new ArrayList<String>();
            built.get(0).onClosed(() -> closed.add("a"));
            built.get(1).onClosed(() -> closed.add("b"));
            host.moveTab(only, b);
            assertThat(first.isOpen()).as("a's only tab left it").isFalse();
            assertThat(closed).as("a's instance closed with its window").containsExactly("a");
            assertThat(built.get(0).visible()).isFalse();
            assertThat(built.get(1).visible()).isTrue();
            context.panels().show(id, first);
            assertThat(built).as("a closed window's request has no effect").hasSize(2);
        }
    }); }

    /** Full windows spec 9: a toggle acts on the instance of the window it names, building it there first. */
    @Test void aToggleActsOnTheNamedWindowsInstance() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> {});
            List<PanelHost> instances = new ArrayList<>();
            String id = "dev.x.tool.hosts";
            context.panels().register(new PanelSpec(id, "Hosts", "file", Anchor.LEFT), panel -> { instances.add(panel); return KitFixtures.view(context); });
            UUID a = host.addTerminalWindow(), b = host.addTerminalWindow();
            context.panels().show(id, context.terminals().window(a).orElseThrow());
            context.panels().toggle(id, context.terminals().window(b).orElseThrow());
            assertThat(instances).hasSize(2);
            assertThat(instances.get(1).window().id()).isEqualTo(b);
            assertThat(instances.get(1).visible()).isTrue();
            context.panels().toggle(id, context.terminals().window(b).orElseThrow());
            assertThat(instances.get(1).visible()).as("b's toggle hides b's instance").isFalse();
            assertThat(instances.get(0).visible()).as("a's is untouched").isTrue();
            assertThat(host.openRequests()).as("no window is raised for another").isEmpty();
            assertThatIllegalArgumentException().isThrownBy(() -> context.panels().show("dev.x.tool.absent", context.terminals().window(a).orElseThrow()));
        }
    }); }

    /** Full windows spec 9: {@code moveToWindow} puts the tab at the end of that window's strip and shows it. */
    @Test void moveToWindowPutsTheTabAtTheEndOfThatWindowsStrip() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> {
        try (var host = new FakePluginHost()) {
            var context = host.start(INFO, Set.of(), Set.of(), plugin -> {});
            UUID a = host.addTerminalWindow(), b = host.addTerminalWindow();
            host.addTerminalTab(a, "zsh");
            UUID shell = host.addTerminalTab(b, "zsh");
            var first = context.terminals().window(a).orElseThrow();
            var second = context.terminals().window(b).orElseThrow();
            var tab = context.windows().openTab(new dev.jasper.sdk.ui.ContentSpec("manager", "Manager"), first, KitFixtures.view(context));
            tab.moveToWindow(second);
            assertThat(tab.window()).isEqualTo(second);
            assertThat(host.windowTabs(b)).extracting(List::getFirst).hasSize(2).first().isEqualTo(shell);
            assertThat(second.tabs().getLast().id()).isEqualTo(second.activeTab().orElseThrow().id());
            tab.moveToWindow(second);
            assertThat(host.windowTabs(b)).as("already there: nothing moves").hasSize(2);
            assertThat(first.isOpen()).as("a keeps its terminal tab").isTrue();
            tab.close();
            tab.moveToWindow(first);
            assertThat(tab.window()).as("a closed tab stays").isEqualTo(second);
        }
    }); }

}
