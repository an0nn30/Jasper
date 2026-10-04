package dev.jasper.app.notificationui;

import dev.jasper.app.notifications.Capture;
import dev.jasper.app.notifications.Incident;
import dev.jasper.app.notifications.IncidentReport;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Font;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ErrorsViewTest {
    /** A clock a test moves by hand. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-02T14:05:00Z");
        void advance(Duration by) { now = now.plus(by); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static final IncidentReport.Environment HERE = new IncidentReport.Environment("1.4.0", "TestOS 1 (arm)", "TestJava 25 (Vendor)");
    private final MutableClock clock = new MutableClock();
    private final Incidents incidents = new Incidents(clock);
    private final List<String> copied = new ArrayList<>();
    private final List<String> disabled = new ArrayList<>();
    private int logFolders;

    private ErrorsView view() {
        return new ErrorsView(incidents, new ErrorsView.Hooks(id -> id.equals("dev.jasper.remote") ? "Remote" : id,
            id -> id.equals("dev.jasper.remote") ? "2.1.0" : "", HERE, copied::add, disabled::add, () -> logFolders++),
            ZoneOffset.UTC);
    }

    private Incident record(String source, String operation, Throwable failure) {
        return incidents.record(source, operation, "AWT-EventQueue-0", Capture.of(failure));
    }

    @Test void incidentsAreListedNewestFirstWithSummarySourceAndTime() {
        var view = view();
        record("jasper", "thread main", new IllegalStateException("first broke\nmore detail"));
        clock.advance(Duration.ofSeconds(30));
        Incident second = record("dev.jasper.remote", "panel remote.sftp",
            new java.io.UncheckedIOException("no route", new java.io.IOException("no route")));
        assertThat(view.listed()).extracting(Incident::id).first().isEqualTo(second.id());
        assertThat(view.rowSummary(0)).isEqualTo("UncheckedIOException: no route");
        assertThat(view.rowDetail(0)).isEqualTo("Remote · 14:05:30");
        assertThat(view.rowSummary(1)).isEqualTo("IllegalStateException: first broke");
        assertThat(view.rowDetail(1)).isEqualTo("Jasper · 14:05:00");
    }

    @Test void theSelectedIncidentShowsItsSourceSightingsMessageAndMonospacedTrace() {
        var view = view();
        var failure = new IllegalStateException("could not open the panel\nbecause of reasons");
        Incident incident = record("dev.jasper.remote", "panel remote.sftp", failure);
        clock.advance(Duration.ofMinutes(2));
        record("dev.jasper.remote", "panel remote.sftp", failure);
        assertThat(view.selected()).map(Incident::id).contains(incident.id());
        assertThat(view.showingEmpty()).isFalse();
        assertThat(view.headerText()).isEqualTo("Remote · panel remote.sftp");
        assertThat(view.sightingsText()).isEqualTo("First seen 14:05:00 · last seen 14:07:00 · 2 times");
        assertThat(view.messageText()).isEqualTo("could not open the panel\nbecause of reasons");
        assertThat(view.traceText()).isEqualTo(incident.trace()).startsWith("java.lang.IllegalStateException: could not open the panel");
        assertThat(view.traceFont().getName()).isEqualTo(Font.MONOSPACED);
        assertThat(view.rowDetail(0)).isEqualTo("Remote · ×2 · 14:07:00");
    }

    @Test void aSingleSightingSaysWhenItWasSeen() {
        var view = view();
        record("jasper", "thread main", new IllegalStateException("once"));
        assertThat(view.sightingsText()).isEqualTo("Seen at 14:05:00");
        assertThat(view.headerText()).isEqualTo("Jasper · thread main");
    }

    @Test void copyHandsTheReportToTheClipboardHook() {
        var view = view();
        Incident incident = record("dev.jasper.remote", "panel remote.sftp", new IllegalStateException("broke"));
        view.copyButton().doClick();
        assertThat(copied).containsExactly(IncidentReport.text(incident, "2.1.0", HERE));
    }

    @Test void disablePluginIsOfferedOnlyForAPluginsIncident() {
        var view = view();
        Incident own = record("jasper", "thread main", new IllegalStateException("ours"));
        Incident plugin = record("dev.jasper.remote", "panel remote.sftp", new IllegalArgumentException("theirs"));
        view.select(own.id());
        assertThat(view.disableButton().isVisible()).isFalse();
        view.select(plugin.id());
        assertThat(view.disableButton().isVisible()).isTrue();
        view.disableButton().doClick();
        assertThat(disabled).containsExactly("dev.jasper.remote");
    }

    @Test void openLogFolderAndClear() {
        var view = view();
        assertThat(view.showingEmpty()).isTrue();
        assertThat(view.clearButton().isEnabled()).isFalse();
        assertThat(view.copyButton().isEnabled()).isFalse();
        record("jasper", "thread main", new IllegalStateException("broke"));
        assertThat(view.clearButton().isEnabled()).isTrue();
        view.logFolderButton().doClick();
        assertThat(logFolders).isEqualTo(1);
        view.clearButton().doClick();
        assertThat(incidents.list()).isEmpty();
        assertThat(view.listed()).isEmpty();
        assertThat(view.showingEmpty()).isTrue();
        assertThat(view.copyButton().isEnabled()).isFalse();
    }

    @Test void aNewIncidentKeepsTheUsersSelection() {
        var view = view();
        Incident older = record("jasper", "a", new IllegalStateException("older"));
        record("jasper", "b", new IllegalArgumentException("newer"));
        view.select(older.id());
        record("jasper", "c", new UnsupportedOperationException("newest"));
        assertThat(view.selected()).map(Incident::id).contains(older.id());
        assertThat(view.listed()).hasSize(3);
    }

    @Test void selectIgnoresAnIncidentThatIsNotListed() {
        var view = view();
        Incident listed = record("jasper", "a", new IllegalStateException("listed"));
        view.select(UUID.randomUUID());
        assertThat(view.selected()).map(Incident::id).contains(listed.id());
    }

    @Test void textIsNeverMarkup() {
        var view = new ErrorsView(incidents, new ErrorsView.Hooks(id -> "<html><b>Evil</b>", id -> "", HERE, copied::add,
            disabled::add, () -> { }), ZoneOffset.UTC);
        record("dev.example.evil", "<html><i>op</i>", new IllegalStateException("<html><u>message</u>"));
        assertThat(view.headerText()).isEqualTo("<html><b>Evil</b> · <html><i>op</i>");
        for (JLabel label : List.of(view.headerLabel(), view.rowLabels(0).get(0), view.rowLabels(0).get(1))) {
            assertThat(label.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
            assertThat(label.getClientProperty(BasicHTML.propertyKey)).isNull();
        }
    }

    @Test void closingStopsListening() {
        var view = view();
        view.close();
        record("jasper", "a", new IllegalStateException("after close"));
        assertThat(view.listed()).isEmpty();
    }
}
