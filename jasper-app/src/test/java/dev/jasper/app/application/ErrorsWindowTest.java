package dev.jasper.app.application;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.notifications.Capture;
import dev.jasper.app.notifications.Incident;
import dev.jasper.app.notifications.IncidentReport;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.notificationui.ErrorsView;
import dev.jasper.app.notificationui.ErrorsViewTestSupport;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
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

@ExtendWith(EdtTestExtension.class)
class ErrorsWindowTest {
    private final List<String> shell = new ArrayList<>();
    private final Contributions contributions = new Contributions();
    private final Incidents incidents = new Incidents(Clock.systemUTC());
    private final AuxiliaryWindows windows = new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(
        () -> shell.add("show"), () -> shell.add("front"), () -> shell.add("dispose"), title -> { },
        () -> new Rectangle(0, 0, 900, 560), (title, initial) -> Optional.empty()));

    private ErrorsWindow window() { return window(windows); }

    private ErrorsWindow window(AuxiliaryWindows in) {
        return new ErrorsWindow(contributions, in, incidents, new ErrorsView.Hooks(id -> id, id -> "",
            new IncidentReport.Environment("1", "os", "java"), text -> { }, id -> { }, () -> { }), ZoneOffset.UTC,
            dev.jasper.app.testsupport.KitTestSupport::context);
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

    /** Lays out the whole tree, as a native window does when it shows (without a peer, {@code validate} does not). */
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

    @Test void showErrorsOpensOneErrorsWindow() {
        window();
        var action = contributions.action(ErrorsWindow.SHOW_ERRORS).orElseThrow();
        assertThat(action.title()).isEqualTo("Notifications: Show Errors");
        action.invoke(new Contributions.Invocation(UUID.randomUUID(), Optional.empty()));
        action.invoke(new Contributions.Invocation(UUID.randomUUID(), Optional.empty()));
        assertThat(windows.open()).singleElement().satisfies(surface -> {
            assertThat(surface.id()).isEqualTo(ErrorsWindow.ID);
            assertThat(surface.title()).isEqualTo("Errors");
            assertThat(surface.preferredSize()).isEqualTo(new Dimension(900, 560));
        });
        assertThat(shell).containsExactly("show", "front", "show", "front");
    }

    /**
     * A window whose view failed to build is closed again, so the next Show more… opens a working one; it used to stay
     * open without a view, and every later Show more… failed.
     */
    @Test void aViewThatFailsToBuildLeavesNoWindowBehindAndTheNextShowWorks() {
        boolean[] fail = {true};
        var errors = new ErrorsWindow(contributions, windows, incidents, new ErrorsView.Hooks(id -> id, id -> "",
            new IncidentReport.Environment("1", "os", "java"), text -> { }, id -> { }, () -> { }), ZoneOffset.UTC,
            () -> {
                if (fail[0]) { fail[0] = false; throw new NoClassDefFoundError("dev/jasper/ui/swing/Stacks$PromptLayout"); }
                return dev.jasper.app.testsupport.KitTestSupport.context();
            });
        Incident incident = incidents.record("jasper", "a", "t", Capture.of(new IllegalStateException("boom")));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> errors.show(Optional.of(incident.id()))).isInstanceOf(NoClassDefFoundError.class);
        assertThat(windows.open()).as("the half-built window is gone").isEmpty();
        errors.show(Optional.of(incident.id()));
        assertThat(windows.open()).singleElement().satisfies(surface -> assertThat(surface.shown()).isTrue());
        assertThat(ErrorsViewTestSupport.selected(errors.view().orElseThrow())).contains(incident.id());
    }

    @Test void showMoreSelectsItsIncidentInTheSameWindow() {
        var errors = window();
        Incident first = incidents.record("jasper", "a", "t", Capture.of(new IllegalStateException("first")));
        Incident second = incidents.record("jasper", "b", "t", Capture.of(new IllegalArgumentException("second")));
        errors.show(Optional.of(first.id()));
        assertThat(ErrorsViewTestSupport.selected(errors.view().orElseThrow())).contains(first.id());
        errors.show(Optional.of(second.id()));
        assertThat(windows.open()).hasSize(1);
        assertThat(ErrorsViewTestSupport.selected(errors.view().orElseThrow())).contains(second.id());
    }

    @Test void showMoreOnAFreshWindowRevealsItsIncidentOnceTheWindowIsLaidOut() {
        // A native window lays its content out when it shows, which can leave the list scrolled to the top.
        var laidOut = new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(() -> {
            surface.holder().setSize(900, 560);
            layOut(surface.holder());
            find((Container) ErrorsViewTestSupport.list((ErrorsView) surface.holder().getComponent(0)), JViewport.class).setViewPosition(new Point(0, 0));
        }, () -> { }, () -> { }, title -> { }, () -> new Rectangle(0, 0, 900, 560), (title, initial) -> Optional.empty()));
        var errors = window(laidOut);
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

    @Test void closingTheWindowStopsItsViewAndTheNextShowOpensAFreshOne() {
        var errors = window();
        errors.show(Optional.empty());
        ErrorsView first = errors.view().orElseThrow();
        errors.surface().orElseThrow().close();
        assertThat(errors.view()).isEmpty();
        incidents.record("jasper", "a", "t", Capture.of(new IllegalStateException("after close")));
        assertThat(ErrorsViewTestSupport.listed(first)).as("the closed view stopped listening").isEmpty();
        errors.show(Optional.empty());
        assertThat(errors.view().orElseThrow()).isNotSameAs(first);
        assertThat(ErrorsViewTestSupport.listed(errors.view().orElseThrow())).hasSize(1);
    }
}
