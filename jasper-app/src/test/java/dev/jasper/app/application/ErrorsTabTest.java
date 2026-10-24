package dev.jasper.app.application;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.contributions.WorkspaceHost;
import dev.jasper.app.notifications.Capture;
import dev.jasper.app.notifications.Incident;
import dev.jasper.app.notifications.IncidentReport;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.notificationui.ErrorsView;
import dev.jasper.app.notificationui.ErrorsViewTestSupport;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

/** Workspace tabs spec 6.6: the Errors tab, an app-owned content tab of the main window. */
@ExtendWith(EdtTestExtension.class)
class ErrorsTabTest {
    private final List<String> workspace = new ArrayList<>();
    private final Contributions contributions = new Contributions();
    private final Incidents incidents = new Incidents(Clock.systemUTC());
    private final UUID main = UUID.randomUUID();

    /** The main window's workspace, as the tab registry sees it: what it opens, closes and selects. */
    private Contributions withMainWindow(Contributions model) {
        model.tabs().addHost(new WorkspaceHost(main, tab -> workspace.add("open " + tab.title()), tab -> workspace.add("close " + tab.title()),
            tab -> workspace.add("select " + tab.title()), () -> { }));
        return model;
    }

    private ErrorsTab tab() { return tab(withMainWindow(contributions)); }

    private ErrorsTab tab(Contributions in) {
        return new ErrorsTab(in, incidents, new ErrorsView.Hooks(id -> id, id -> "",
            new IncidentReport.Environment("1", "os", "java"), text -> { }, id -> { }, () -> { }), ZoneOffset.UTC,
            dev.jasper.app.testsupport.KitTestSupport::workspaceContext);
    }

    /** The first component of {@code type} in {@code root}, depth first. */
    private static <T extends Component> T find(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container inner) {
                T found = find(inner, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Lays out the whole tree, as a window does when it shows its workspace (without a peer, {@code validate} does not). */
    private static void layOut(Component component) {
        if (!(component instanceof Container container)) return;
        container.doLayout();
        for (Component child : container.getComponents()) layOut(child);
    }

    /** Lets the EDT run what is already queued, as its next turn would. */
    private static void nextEdtTurn() {
        SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        SwingUtilities.invokeLater(loop::exit);
        loop.enter();
    }

    @Test void showErrorsOpensOneErrorsTabInTheMainWindow() {
        tab();
        var action = contributions.action(ErrorsTab.SHOW_ERRORS).orElseThrow();
        assertThat(action.title()).isEqualTo("Notifications: Show Errors");
        action.invoke(new Contributions.Invocation(UUID.randomUUID(), Optional.empty()));
        action.invoke(new Contributions.Invocation(UUID.randomUUID(), Optional.empty()));
        assertThat(contributions.tabs().hosted()).singleElement().satisfies(tab -> {
            assertThat(tab.window()).as("in the main window").isEqualTo(main);
            assertThat(tab.plugin()).as("Jasper's own tab").isEqualTo(TabRegistry.APP_OWNER);
            assertThat(tab.title()).isEqualTo("Errors");
            assertThat(tab.component()).isInstanceOf(ErrorsView.class);
            // Content, as a plugin's content tab is: the workspace route on its painted surface, and no Done.
            var kitView = ErrorsViewTestSupport.kitView((ErrorsView) tab.component());
            assertThat(kitView.route()).isEqualTo(dev.jasper.ui.theme.ThemeRoute.WORKSPACE);
            assertThat(kitView.isOpaque()).as("it paints its surface").isTrue();
            var form = (dev.jasper.ui.model.Nodes.Form) kitView.spec().root();
            assertThat(form.actions()).extracting(dev.jasper.ui.model.Nodes.Button::label).containsExactly("Open Log Folder", "Clear");
            assertThat(form.cancelAction()).as("Escape leaves it open").isEmpty();
        });
        assertThat(workspace).containsExactly("open Errors", "select Errors", "select Errors");
    }

    /**
     * A view that fails to build opens no tab, so the next Show more… opens a working one.
     */
    @Test void aViewThatFailsToBuildOpensNoTabAndTheNextShowWorks() {
        boolean[] fail = {true};
        var errors = new ErrorsTab(withMainWindow(contributions), incidents, new ErrorsView.Hooks(id -> id, id -> "",
            new IncidentReport.Environment("1", "os", "java"), text -> { }, id -> { }, () -> { }), ZoneOffset.UTC,
            () -> {
                if (fail[0]) { fail[0] = false; throw new NoClassDefFoundError("dev/jasper/ui/swing/Stacks$PromptLayout"); }
                return dev.jasper.app.testsupport.KitTestSupport.workspaceContext();
            });
        Incident incident = incidents.record("jasper", "a", "t", Capture.of(new IllegalStateException("boom")));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> errors.show(Optional.of(incident.id()))).isInstanceOf(NoClassDefFoundError.class);
        assertThat(contributions.tabs().hosted()).as("no tab without a view").isEmpty();
        errors.show(Optional.of(incident.id()));
        assertThat(contributions.tabs().hosted()).hasSize(1);
        assertThat(ErrorsViewTestSupport.selected(errors.view().orElseThrow())).contains(incident.id());
    }

    @Test void showMoreSelectsItsIncidentInTheSameTab() {
        var errors = tab();
        Incident first = incidents.record("jasper", "a", "t", Capture.of(new IllegalStateException("first")));
        Incident second = incidents.record("jasper", "b", "t", Capture.of(new IllegalArgumentException("second")));
        errors.show(Optional.of(first.id()));
        assertThat(ErrorsViewTestSupport.selected(errors.view().orElseThrow())).contains(first.id());
        errors.show(Optional.of(second.id()));
        assertThat(contributions.tabs().hosted()).hasSize(1);
        assertThat(ErrorsViewTestSupport.selected(errors.view().orElseThrow())).contains(second.id());
    }

    @Test void showMoreOnAFreshTabRevealsItsIncidentOnceTheTabIsLaidOut() {
        // A window lays a tab out when it shows it, which can leave the list scrolled to the top.
        var laidOut = new Contributions();
        laidOut.tabs().addHost(new WorkspaceHost(main, tab -> {
            var shown = (ErrorsView) tab.component();
            shown.setSize(900, 560);
            layOut(shown);
            find((Container) ErrorsViewTestSupport.list(shown), JViewport.class).setViewPosition(new Point(0, 0));
        }, tab -> { }, tab -> { }, () -> { }));
        var errors = tab(laidOut);
        for (int i = 0; i < 40; i++) incidents.record("jasper", "a", "t", new Capture("x.Older" + i, "", "x.Older" + i, ""));
        Incident wanted = incidents.record("jasper", "a", "t", Capture.of(new IllegalStateException("wanted")));
        for (int i = 0; i < 40; i++) incidents.record("jasper", "a", "t", new Capture("x.Newer" + i, "", "x.Newer" + i, ""));
        errors.show(Optional.of(wanted.id()));
        nextEdtTurn();
        var view = errors.view().orElseThrow();
        assertThat(ErrorsViewTestSupport.selected(view)).contains(wanted.id());
        assertThat(ErrorsViewTestSupport.listed(view).indexOf(incidents.list().stream().filter(i -> i.id().equals(wanted.id())).findFirst().orElseThrow())).isEqualTo(40);
        var viewport = find((Container) ErrorsViewTestSupport.list(view), JViewport.class);
        var summary = ErrorsViewTestSupport.summary(view, wanted.id());
        assertThat(summary).as("the selected row is mounted").isNotNull();
        var bounds = javax.swing.SwingUtilities.convertRectangle(summary.getParent(), summary.getBounds(), viewport.getView());
        assertThat(viewport.getViewPosition().y).as("scrolled away from the top").isPositive();
        assertThat(viewport.getViewRect().intersects(bounds)).as("the selected row is in view").isTrue();
    }

    @Test void closingTheTabStopsItsViewAndTheNextShowOpensAFreshOne() {
        var errors = tab();
        errors.show(Optional.empty());
        ErrorsView first = errors.view().orElseThrow();
        errors.tab().orElseThrow().close();
        assertThat(errors.view()).isEmpty();
        assertThat(workspace).contains("close Errors");
        incidents.record("jasper", "a", "t", Capture.of(new IllegalStateException("after close")));
        assertThat(ErrorsViewTestSupport.listed(first)).as("the closed view stopped listening").isEmpty();
        errors.show(Optional.empty());
        assertThat(errors.view().orElseThrow()).isNotSameAs(first);
        assertThat(ErrorsViewTestSupport.listed(errors.view().orElseThrow())).hasSize(1);
    }
}
