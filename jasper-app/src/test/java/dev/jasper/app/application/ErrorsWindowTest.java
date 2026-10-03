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
import java.awt.Dimension;
import java.awt.Rectangle;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    private ErrorsWindow window() {
        return new ErrorsWindow(contributions, windows, incidents, new ErrorsView.Hooks(id -> id, id -> "",
            new IncidentReport.Environment("1", "os", "java"), text -> { }, id -> { }, () -> { }), ZoneOffset.UTC);
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
