package dev.jasper.remote.ui.transfers;

import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.transfer.*;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * Plan 3 Task 7: the Remote transfer companion UI's canonical composition on the testkit fake: the strip's rows
 * (progress, status, the one applicable action and the close button, keyed by the job), the compact Items already exist
 * question, and the grouped Resolve and Rename forms. Their behavior is TransferStripTest's.
 */
class TransferCompositionTest {
    final List<String> calls = new ArrayList<>();
    final TransferStrip strip = new TransferStrip(new TransferStrip.Actions((id, action) -> calls.add(action + " " + id), id -> calls.add("cancel " + id), id -> calls.add("dismiss " + id)));

    static TransferRows.Row row(UUID id, String status, double fraction, Optional<TransferRows.Action> action, boolean finished, boolean cancelling) {
        return new TransferRows.Row(id, "↑", "report.pdf → prod:/srv/app", "Local: /tmp/report.pdf → prod: /srv/app", status,
            fraction < 0 ? OptionalDouble.empty() : OptionalDouble.of(fraction), fraction < 0, action, finished, cancelling);
    }

    @Test void stripRowsKeepTheirJobIdentityProgressStatusAndActions() {
        var running = UUID.randomUUID(); var paused = UUID.randomUUID(); var scanning = UUID.randomUUID();
        strip.rows(List.of(row(running, "50% · 1.0 MiB/s · 3 s left", .5, Optional.empty(), false, false),
            row(paused, "Paused", .25, Optional.of(TransferRows.Action.RESUME), false, false),
            row(scanning, "Scanning… 3 items found", -1, Optional.empty(), false, true)));
        var root = (Nodes.Stack) strip.node().orElseThrow();
        assertThat(root.children().getFirst()).isInstanceOfSatisfying(Nodes.Text.class, heading -> assertThat(heading.role()).isEqualTo(Nodes.TextRole.SECTION_LABEL));
        var list = (Nodes.ItemList) root.children().getLast();
        assertThat(list.kind()).isEqualTo(Nodes.ListKind.DOUBLE);
        assertThat(list.items()).extracting(item -> item.id()).as("rows are keyed by the job").containsExactly(running.toString(), paused.toString(), scanning.toString());
        assertThat(list.visibleRows()).hasValue(TransferStrip.VISIBLE_ROWS);
        var first = list.items().getFirst();
        assertThat(first.subtitle().getFirst()).isInstanceOfSatisfying(Nodes.Progress.class, progress -> assertThat(progress.fraction()).isEqualTo(.5));
        assertThat(first.subtitle().getLast()).isInstanceOfSatisfying(Nodes.Text.class, status -> assertThat(status.text()).startsWith("50%"));
        assertThat(first.trailing()).singleElement().isInstanceOfSatisfying(Nodes.Button.class, close -> assertThat(close.tooltip()).isEqualTo("Cancel transfer"));
        var second = list.items().get(1);
        assertThat(second.trailing()).hasSize(2).first().isInstanceOfSatisfying(Nodes.Button.class, resume -> {
            assertThat(resume.kind()).isEqualTo(Nodes.ButtonKind.LINK);
            assertThat(resume.label()).isEqualTo("Resume");
        });
        var third = list.items().getLast();
        assertThat(((Nodes.Progress) third.subtitle().getFirst()).fraction()).as("scanning is indeterminate").isEqualTo(-1);
        assertThat(((Nodes.Button) third.trailing().getLast()).state().enabled()).as("a cancelling job's close waits").isFalse();
        assertThat(strip.handle(new UiEvent.Action(((Nodes.Button) second.trailing().getFirst()).action()))).isTrue();
        assertThat(calls).containsExactly("RESUME " + paused);
    }

    @Test void existingItemsIsTheCompactQuestionDialog() throws Exception {
        edt(() -> {
            var chosen = new ArrayList<Object>();
            var panel = new ExistingItemsPanel(new FakeKit("dev.jasper.remote"), "2 of 3 items already exist in prod:/srv/app.", chosen::add, () -> chosen.add("cancel"));
            var form = form(panel.view());
            assertThat(form.kind()).isEqualTo(Nodes.FormKind.PROMPT_COMPACT);
            assertThat(form.hero()).get().isInstanceOfSatisfying(Nodes.Stack.class, hero -> {
                assertThat(hero.role()).isEqualTo(Nodes.LayoutRole.PROMPT_HEADER);
                assertThat(hero.children().getFirst()).isInstanceOfSatisfying(Nodes.Icon.class, icon -> assertThat(icon.name()).isEqualTo("warning"));
                assertThat(((Nodes.Text) hero.children().getLast()).text()).isEqualTo("2 of 3 items already exist in prod:/srv/app.");
            });
            assertThat(form.actions()).extracting(Nodes.Button::id).containsExactly(ExistingItemsPanel.CANCEL, ExistingItemsPanel.SKIP, ExistingItemsPanel.REPLACE);
            assertThat(form.actions().getLast().kind()).isEqualTo(Nodes.ButtonKind.PRIMARY);
            assertThat(form.submission()).as("Enter copies nothing").isEqualTo(Nodes.FormSubmission.NONE);
            click(panel.view(), ExistingItemsPanel.SKIP);
            assertThat(chosen).containsExactly(ConflictDecision.SKIP);
        });
    }

    @Test void resolveAndRenameAreGroupedForms() throws Exception {
        edt(() -> {
            var folder = new FileEntry("assets", FileEntry.Kind.DIRECTORY, 0, 1000, 0755, "", "dir-1");
            var entry = new TransferEntry(3, UUID.randomUUID(), "assets", "/tmp/assets", "/srv/app/assets", folder, "", Optional.empty(),
                TransferEntry.Phase.PENDING, 0, "", TransferEntry.Publication.NONE, Optional.of(folder), ConflictDecision.ASK, TransferEntry.Outcome.PENDING, "A folder with that name exists");
            var resolve = new ResolvePanel(new FakeKit("dev.jasper.remote"), entry, choice -> { }, () -> { }, () -> { });
            var form = form(resolve.view());
            assertThat(form.sections()).singleElement().isInstanceOfSatisfying(Nodes.Section.class, section -> {
                assertThat(section.title()).isEqualTo("Conflict");
                var card = (Nodes.Card) section.body();
                assertThat(card.kind()).isEqualTo(Nodes.CardKind.MIXED);
                assertThat(((Nodes.FormRow) card.children().getFirst()).label()).isEqualTo("Folder");
                assertThat(((Nodes.Text) ((Nodes.FormRow) card.children().getFirst()).control()).role()).as("the path is technical text").isEqualTo(Nodes.TextRole.MONO);
                assertThat(card.children().getLast()).isInstanceOf(Nodes.SettingRow.class);
            });
            assertThat(primaries(resolve.view())).extracting(Nodes.Button::id).as("a folder's visual primary is Merge").containsExactly(ResolvePanel.MERGE);
            assertThat(form.submission()).isEqualTo(Nodes.FormSubmission.NONE);

            var rename = new RenamePanel(new FakeKit("dev.jasper.remote"), "report.pdf", name -> { }, () -> { });
            var renameForm = form(rename.view());
            assertThat(renameForm.sections()).singleElement().isInstanceOfSatisfying(Nodes.Section.class, section -> assertThat(section.title()).isEqualTo("Destination"));
            assertThat(node(rename.view(), RenamePanel.NAME, Nodes.Field.class).presentation()).isEqualTo(Nodes.FieldPresentation.TECHNICAL);
        });
    }
}
