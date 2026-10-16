package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.ToolbarEntry;
import dev.jasper.app.terminals.TerminalRegistry;
import java.awt.Dimension;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

/** Plan 3d: a contributed toolbar view is built once per window when the window opens, and closes with it or its entry. */
class WindowToolbarViewsTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    /** A view entry whose factory records each window it builds for, the components it made and each close. */
    static final class Recorder {
        final List<UUID> built = new ArrayList<>();
        final List<JComponent> components = new ArrayList<>();
        final AtomicInteger closed = new AtomicInteger();
        final ToolbarEntry.View entry = new ToolbarEntry.View(window -> {
            built.add(window);
            var component = new JPanel();
            component.setPreferredSize(new Dimension(120, 24));
            components.add(component);
            return Optional.of(new ToolbarEntry.Mounted(component, closed::incrementAndGet));
        });
    }

    static boolean inToolbar(WindowContent owner, JComponent component) {
        return Arrays.asList(owner.toolbar().getComponents()).contains(component);
    }

    @Test void aWindowBuildsItsViewOnlyOnceItOpensAndOnlyOnce() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var recorder = new Recorder();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.connectContributions(model);
            model.addToolbar(recorder.entry);
            assertThat(recorder.built).as("not before the window opens, as for the warm-up rehearsal's window").isEmpty();
            owner.connectTerminals(new TerminalRegistry(), () -> { });
            assertThat(recorder.built).containsExactly(owner.id());
            assertThat(inToolbar(owner, recorder.components.getFirst())).isTrue();
            owner.chrome().renderContributedToolbar();
            model.addToolbar(new ToolbarEntry.Button("dev.x.absent"));
            assertThat(recorder.built).as("re-rendering keeps the window's view").containsExactly(owner.id());
            assertThat(inToolbar(owner, recorder.components.getFirst())).isTrue();
        });
    }

    @Test void aViewClosesWhenItsEntryGoesAndWhenItsWindowCloses() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var first = new Recorder();
            var second = new Recorder();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.connectContributions(model);
            owner.connectTerminals(new TerminalRegistry(), () -> { });
            var registration = model.addToolbar(first.entry);
            model.addToolbar(second.entry);
            assertThat(first.built).as("an open window builds a new entry's view at once").containsExactly(owner.id());
            registration.close();
            assertThat(first.closed).hasValue(1);
            assertThat(inToolbar(owner, first.components.getFirst())).isFalse();
            assertThat(second.closed).hasValue(0);
            owner.close();
            assertThat(second.closed).as("the window's close closes the rest").hasValue(1);
            assertThat(first.closed).as("each view closes once").hasValue(1);
        });
    }

    @Test void aFactoryThatFailedIsNotAskedAgainForThatWindow() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var asked = new AtomicInteger();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.connectContributions(model);
            owner.connectTerminals(new TerminalRegistry(), () -> { });
            model.addToolbar(new ToolbarEntry.View(window -> { asked.incrementAndGet(); return Optional.empty(); }));
            owner.chrome().renderContributedToolbar();
            assertThat(asked).hasValue(1);
            assertThat(WindowTabsTest.named(owner.toolbar(), "pluginSeparator").isVisible()).as("nothing shown, no plugin group").isFalse();
        });
    }

    /** A factory that changes the contributions while it runs re-renders the toolbar from inside the pass that asked it. */
    @Test void aFactoryThatChangesTheContributionsIsAskedOnceAndItsViewClosesOnce() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var asked = new AtomicInteger();
            var closed = new AtomicInteger();
            List<JComponent> components = new ArrayList<>();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.connectContributions(model);
            owner.connectTerminals(new TerminalRegistry(), () -> { });
            var registration = model.addToolbar(new ToolbarEntry.View(window -> {
                int call = asked.incrementAndGet();
                // Both fire Contributions.Kind.ACTIONS synchronously, which renders the toolbar again.
                var action = model.addAction("dev.x.connect" + call, "Connect", null, List.of(), Optional.empty(), invocation -> { });
                action.setEnabled(false);
                var component = new JPanel();
                component.setPreferredSize(new Dimension(120, 24));
                components.add(component);
                return Optional.of(new ToolbarEntry.Mounted(component, closed::incrementAndGet));
            }));
            assertThat(asked).as("the nested pass does not ask the factory again").hasValue(1);
            assertThat(components).hasSize(1);
            assertThat(inToolbar(owner, components.getFirst())).isTrue();
            registration.close();
            assertThat(closed).as("the one view closes once").hasValue(1);
            assertThat(inToolbar(owner, components.getFirst())).isFalse();
        });
    }

    @Test void aKitViewIsPresentedAsAToolbarStrip() throws Exception {
        edt(() -> {
            var controller = ThemeTestSupport.controller(true);
            var binding = new dev.jasper.ui.theme.ThemeBinding(controller.current().pair(), failure -> { throw new AssertionError(failure); });
            var icons = new dev.jasper.ui.icons.IconRegistry();
            var context = dev.jasper.ui.swing.KitContext.of(dev.jasper.ui.kit.Kit.DEFAULT, binding, dev.jasper.ui.theme.ThemeRoute.CHROME,
                icons, () -> false, action -> "");
            var view = new dev.jasper.ui.swing.KitView(context, new dev.jasper.ui.model.ViewSpec(new dev.jasper.ui.model.Nodes.Text("label",
                dev.jasper.ui.model.ControlState.READY, "Session", dev.jasper.ui.model.Nodes.TextRole.LABEL)), event -> { });
            try {
                var model = new Contributions();
                WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), controller);
                owner.connectContributions(model);
                owner.connectTerminals(new TerminalRegistry(), () -> { });
                model.addToolbar(new ToolbarEntry.View(window -> Optional.of(new ToolbarEntry.Mounted(view, view::close))));
                assertThat(view.toolbarStrip()).isTrue();
                assertThat(inToolbar(owner, view)).isTrue();
                owner.close();
            } finally { if (!view.isClosed()) view.close(); binding.close(); icons.close(); controller.close(); }
        });
    }
}
