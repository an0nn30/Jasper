package dev.jasper.remote.ui.transfers;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.transfer.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class TransferStripTest {
    final List<String> calls = new ArrayList<>();
    final TransferStrip strip = new TransferStrip(new TransferStrip.Actions((id, action) -> calls.add(action + " " + id), id -> calls.add("cancel " + id), id -> calls.add("dismiss " + id)));

    static TransferRows.Row row(UUID id, String status, Optional<TransferRows.Action> action, boolean finished) {
        return new TransferRows.Row(id, "↑", "report.pdf → prod:/srv/app", "Local: /tmp/report.pdf → dustin@host: /srv/app", status, OptionalDouble.of(0.5), false, action, finished, false);
    }

    @Test void hiddenWhenEmptyAndShownWithRows() {
        assertThat(strip.isVisible()).isFalse();
        var id = UUID.randomUUID();
        strip.rows(List.of(row(id, "58% · 4.1 MiB/s · 11 s left", Optional.empty(), false)));
        assertThat(strip.isVisible()).isTrue();
        var view = strip.view(id).orElseThrow();
        assertThat(view.fullTitle()).isEqualTo("↑ report.pdf → prod:/srv/app");
        assertThat(view.tooltip()).contains("/tmp/report.pdf", "/srv/app");
        assertThat(view.statusText()).isEqualTo("58% · 4.1 MiB/s · 11 s left");
        assertThat(view.progress().getValue()).isEqualTo(500);
        assertThat(view.actionButton().isVisible()).isFalse();
        strip.rows(List.of());
        assertThat(strip.isVisible()).isFalse();
        assertThat(strip.view(id)).isEmpty();
    }

    @Test void theActionButtonAndCloseDoTheRightThing() {
        var running = UUID.randomUUID();
        var paused = UUID.randomUUID();
        var done = UUID.randomUUID();
        strip.rows(List.of(row(running, "40%", Optional.empty(), false), row(paused, "Paused", Optional.of(TransferRows.Action.RESUME), false),
            row(done, "Cancelled", Optional.empty(), true)));
        var resume = strip.view(paused).orElseThrow().actionButton();
        assertThat(resume.isVisible()).isTrue();
        assertThat(resume.getText()).isEqualTo("Resume");
        resume.doClick();
        strip.view(running).orElseThrow().closeButton().doClick();
        strip.view(done).orElseThrow().closeButton().doClick();
        assertThat(calls).containsExactly("RESUME " + paused, "cancel " + running, "dismiss " + done);
        assertThat(strip.view(running).orElseThrow().closeButton().getToolTipText()).isEqualTo("Cancel transfer");
        assertThat(strip.view(done).orElseThrow().closeButton().getToolTipText()).isEqualTo("Dismiss");
    }

    @Test void atMostThreeRowsAreVisible() {
        var rows = new ArrayList<TransferRows.Row>();
        for (int i = 0; i < 5; i++) rows.add(row(UUID.randomUUID(), "Queued", Optional.empty(), false));
        strip.rows(rows);
        assertThat(strip.visibleRows()).isEqualTo(3);
    }

    @Test void longTitlesAreElidedInTheMiddleWithTheFullTextInTheTooltip() {
        var metrics = new JLabel().getFontMetrics(new JLabel().getFont());
        String text = "a-very-long-folder-name-for-photos (340 items) → production-server:/srv/app/uploads/2026";
        String elided = TransferStrip.elideMiddle(text, metrics, metrics.stringWidth(text) / 2);
        assertThat(elided).contains("…").startsWith("a-very").endsWith("2026");
        assertThat(metrics.stringWidth(elided)).isLessThanOrEqualTo(metrics.stringWidth(text) / 2);
        assertThat(TransferStrip.elideMiddle("short", metrics, 1000)).isEqualTo("short");
        var id = UUID.randomUUID();
        strip.rows(List.of(new TransferRows.Row(id, "↑", text, "full tooltip", "Queued", OptionalDouble.empty(), false, Optional.empty(), false, false)));
        var view = strip.view(id).orElseThrow();
        view.setSize(160, 60);
        view.doLayout();
        assertThat(view.titleText()).contains("…").endsWith("2026");
        assertThat(view.tooltip()).isEqualTo("full tooltip");
    }

    @Test void anotherInstanceShowsOneLine() {
        strip.unavailable("Transfers are managed by another Jasper instance");
        assertThat(strip.isVisible()).isTrue();
        assertThat(strip.unavailableText()).isEqualTo("Transfers are managed by another Jasper instance");
        strip.rows(List.of());
        assertThat(strip.unavailableText()).isEmpty();
        assertThat(strip.isVisible()).isFalse();
    }

    @Test void resolveOffersTheDecisionsThatFit() {
        var chosen = new ArrayList<Object>();
        var file = new FileEntry("report.pdf", FileEntry.Kind.FILE, 3, 1000, 0644, "", "file-1");
        var entry = new TransferEntry(7, UUID.randomUUID(), "report.pdf", "/tmp/report.pdf", "/srv/app/report.pdf", file, "", Optional.empty(),
            TransferEntry.Phase.PENDING, 0, "", TransferEntry.Publication.NONE, Optional.of(file), ConflictDecision.ASK, TransferEntry.Outcome.PENDING, "Destination changed");
        var panel = new ResolvePanel(entry, chosen::add, () -> chosen.add("restart"), () -> chosen.add("close"));
        assertThat(panel.replace.isVisible()).isTrue();
        assertThat(panel.replace.isEnabled()).isTrue();
        assertThat(panel.merge.isVisible()).as("files cannot merge").isFalse();
        panel.remaining.setSelected(true);
        panel.replace.doClick();
        panel.rename.doClick();
        panel.restart.doClick();
        panel.cancel.doClick();
        assertThat(chosen).containsExactly(new ResolvePanel.Choice(ConflictDecision.REPLACE, true), new ResolvePanel.Choice(ConflictDecision.RENAME, false), "restart", "close");
    }

    @Test void existingItemsAsksOnce() {
        assertThat(ExistingItemsPanel.message(List.of("report.pdf"), 1, "prod:/srv/app")).isEqualTo("\"report.pdf\" already exists in prod:/srv/app.");
        assertThat(ExistingItemsPanel.message(List.of("a", "b", "c"), 5, "prod:/srv/app")).isEqualTo("3 of 5 items already exist in prod:/srv/app.");
        assertThat(ExistingItemsPanel.message(List.of("a"), 5, "/Users/me")).isEqualTo("1 of 5 items already exists in /Users/me.");
        var chosen = new ArrayList<Object>();
        var panel = new ExistingItemsPanel("3 of 5 items already exist in prod:/srv/app.", chosen::add, () -> chosen.add("cancel"));
        panel.replace.doClick();
        panel.skip.doClick();
        panel.cancel.doClick();
        assertThat(chosen).containsExactly(ConflictDecision.REPLACE, ConflictDecision.SKIP, "cancel");
        assertThat(panel.skip.getText()).isEqualTo("Skip existing");
    }

    @Test void rowOrderFollowsDisplayOrderWhenReordered() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var c = UUID.randomUUID();
        strip.rows(List.of(row(a, "40%", Optional.empty(), false), row(b, "50%", Optional.empty(), false)));
        var viewA = strip.view(a).orElseThrow();
        assertThat(strip.order()).containsExactly(a, b);
        strip.rows(List.of(row(c, "30%", Optional.empty(), false), row(a, "40%", Optional.empty(), false), row(b, "50%", Optional.empty(), false)));
        assertThat(strip.order()).containsExactly(c, a, b);
        assertThat(strip.view(a).orElseThrow()).isSameAs(viewA);
    }
}
