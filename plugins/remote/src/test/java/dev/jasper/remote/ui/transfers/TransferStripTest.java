package dev.jasper.remote.ui.transfers;

import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.transfer.*;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * The transfer rows as kit descriptors (Plan 2 Task 8c, ruling G8) and the transfer dialogs as kit forms. The strip owns
 * no view; the SFTP sidebar renders {@link TransferStrip#node()}. Component identity across refreshes (a focused button
 * keeps focus) is the kit reconciler's, asserted in the real host by {@code StagedRemoteSftpTest}.
 */
class TransferStripTest {
    final List<String> calls = new ArrayList<>();
    final TransferStrip strip = new TransferStrip(new TransferStrip.Actions((id, action) -> calls.add(action + " " + id), id -> calls.add("cancel " + id), id -> calls.add("dismiss " + id)));

    static TransferRows.Row row(UUID id, String status, Optional<TransferRows.Action> action, boolean finished) {
        return new TransferRows.Row(id, "↑", "report.pdf → prod:/srv/app", "Local: /tmp/report.pdf → dustin@host: /srv/app", status, OptionalDouble.of(0.5), false, action, finished, false);
    }

    Nodes.ItemList list() { return (Nodes.ItemList) find(strip.node().orElseThrow(), TransferStrip.ROWS).orElseThrow(); }
    ListItem item(UUID id) { return list().items().stream().filter(item -> item.id().equals(id.toString())).findFirst().orElseThrow(); }
    <T extends Nodes.Node> T part(UUID id, String nodeId, Class<T> type) { return type.cast(find(strip.node().orElseThrow(), nodeId).orElseThrow(() -> new AssertionError("no " + nodeId))); }
    void clickRow(UUID id, String buttonId) {
        var button = part(id, buttonId, Nodes.Button.class);
        if (!button.state().enabled()) throw new AssertionError(buttonId + " is disabled");
        assertThat(strip.handle(new UiEvent.Action(button.action()))).isTrue();
    }

    /** Was separatorReadsMountedZoneAtPaintAndKeepsPadding: the strip paints nothing; the kit footer owns its rule and zone colours. */
    @Test void theStripIsKitDescriptorsWithNoRawComponent() {
        var id = UUID.randomUUID();
        strip.rows(List.of(row(id, "Queued", Optional.empty(), false)));
        var node = strip.node().orElseThrow();
        assertThat(new ViewSpec(node).root()).isSameAs(node);
        assertThat(((Nodes.Stack) node).children()).extracting(Nodes.Node::id).containsExactly(TransferStrip.HEADING, TransferStrip.ROWS);
        assertThat(((Nodes.Text) ((Nodes.Stack) node).children().getFirst()).role()).isEqualTo(Nodes.TextRole.SECTION_LABEL);
        assertThat(list().kind()).isEqualTo(Nodes.ListKind.DOUBLE);
    }

    @Test void hiddenWhenEmptyAndShownWithRows() {
        assertThat(strip.visible()).isFalse();
        assertThat(strip.node()).isEmpty();
        var id = UUID.randomUUID();
        strip.rows(List.of(row(id, "58% · 4.1 MiB/s · 11 s left", Optional.empty(), false)));
        assertThat(strip.visible()).isTrue();
        assertThat(part(id, TransferStrip.titleId(id), Nodes.Text.class).text()).isEqualTo("↑ report.pdf → prod:/srv/app");
        assertThat(item(id).tooltip()).contains("/tmp/report.pdf", "/srv/app");
        assertThat(part(id, TransferStrip.statusId(id), Nodes.Text.class).text()).isEqualTo("58% · 4.1 MiB/s · 11 s left");
        assertThat(part(id, TransferStrip.progressId(id), Nodes.Progress.class).fraction()).isEqualTo(0.5);
        assertThat(find(strip.node().orElseThrow(), TransferStrip.actionId(id))).as("no action button").isEmpty();
        strip.rows(List.of());
        assertThat(strip.visible()).isFalse();
        assertThat(strip.row(id)).isEmpty();
        assertThat(strip.node()).isEmpty();
    }

    @Test void scanningShowsAnIndeterminateBarAndNoFractionHidesIt() {
        var scanning = UUID.randomUUID(); var queued = UUID.randomUUID();
        strip.rows(List.of(new TransferRows.Row(scanning, "↓", "photos", "tip", "Scanning… 3 items found", OptionalDouble.empty(), true, Optional.empty(), false, false),
            new TransferRows.Row(queued, "↓", "notes", "tip", "Queued", OptionalDouble.empty(), false, Optional.empty(), false, false)));
        var bar = part(scanning, TransferStrip.progressId(scanning), Nodes.Progress.class);
        assertThat(bar.fraction()).isEqualTo(-1);
        assertThat(bar.state().visible()).isTrue();
        assertThat(part(queued, TransferStrip.progressId(queued), Nodes.Progress.class).state().visible()).isFalse();
    }

    @Test void theActionButtonAndCloseDoTheRightThing() {
        var running = UUID.randomUUID();
        var paused = UUID.randomUUID();
        var done = UUID.randomUUID();
        var cancelling = UUID.randomUUID();
        strip.rows(List.of(row(running, "40%", Optional.empty(), false), row(paused, "Paused", Optional.of(TransferRows.Action.RESUME), false),
            row(done, "Cancelled", Optional.empty(), true),
            new TransferRows.Row(cancelling, "↑", "x", "tip", "Cancelling…", OptionalDouble.empty(), false, Optional.empty(), false, true)));
        var resume = part(paused, TransferStrip.actionId(paused), Nodes.Button.class);
        assertThat(resume.label()).isEqualTo("Resume");
        clickRow(paused, TransferStrip.actionId(paused));
        clickRow(running, TransferStrip.closeId(running));
        clickRow(done, TransferStrip.closeId(done));
        assertThat(calls).containsExactly("RESUME " + paused, "cancel " + running, "dismiss " + done);
        assertThat(part(running, TransferStrip.closeId(running), Nodes.Button.class).tooltip()).isEqualTo("Cancel transfer");
        assertThat(part(done, TransferStrip.closeId(done), Nodes.Button.class).tooltip()).isEqualTo("Dismiss");
        var close = part(cancelling, TransferStrip.closeId(cancelling), Nodes.Button.class);
        assertThat(close.state().enabled()).as("disabled while cancelling").isFalse();
        strip.handle(new UiEvent.Action(close.action()));
        assertThat(calls).as("a stale close while cancelling does nothing").hasSize(3);
        assertThat(strip.handle(new UiEvent.Action("sftp.refresh"))).as("not the strip's").isFalse();
    }

    @Test void atMostThreeRowsAreVisible() {
        var rows = new ArrayList<TransferRows.Row>();
        for (int i = 0; i < 5; i++) rows.add(row(UUID.randomUUID(), "Queued", Optional.empty(), false));
        strip.rows(rows);
        assertThat(list().items()).hasSize(5);
        assertThat(list().visibleRows()).hasValue(3);
    }

    /** Ruling G8(a): the kit truncates at the end; the tooltip has the whole title, both paths and the status. */
    @Test void longTitlesKeepTheirFullTextInTheTooltip() {
        String text = "a-very-long-folder-name-for-photos (340 items) → production-server:/srv/app/uploads/2026";
        var id = UUID.randomUUID();
        strip.rows(List.of(new TransferRows.Row(id, "↑", text, "full tooltip", "Queued", OptionalDouble.empty(), false, Optional.empty(), false, false)));
        assertThat(part(id, TransferStrip.titleId(id), Nodes.Text.class).text()).isEqualTo("↑ " + text);
        assertThat(item(id).tooltip()).isEqualTo("↑ " + text + " — full tooltip — Queued");
    }

    @Test void anotherInstanceShowsOneLine() {
        strip.unavailable("Transfers are managed by another Jasper instance");
        assertThat(strip.visible()).isTrue();
        assertThat(strip.unavailableText()).isEqualTo("Transfers are managed by another Jasper instance");
        assertThat(((Nodes.Text) find(strip.node().orElseThrow(), TransferStrip.UNAVAILABLE).orElseThrow()).text()).isEqualTo("Transfers are managed by another Jasper instance");
        strip.rows(List.of());
        assertThat(strip.unavailableText()).isEmpty();
        assertThat(strip.visible()).isFalse();
    }

    @Test void resolveOffersTheDecisionsThatFit() throws Exception {
        edt(() -> {
            var chosen = new ArrayList<Object>();
            var file = new FileEntry("report.pdf", FileEntry.Kind.FILE, 3, 1000, 0644, "", "file-1");
            var entry = new TransferEntry(7, UUID.randomUUID(), "report.pdf", "/tmp/report.pdf", "/srv/app/report.pdf", file, "", Optional.empty(),
                TransferEntry.Phase.PENDING, 0, "", TransferEntry.Publication.NONE, Optional.of(file), ConflictDecision.ASK, TransferEntry.Outcome.PENDING, "Destination changed");
            var panel = new ResolvePanel(new FakeKit("dev.jasper.remote"), entry, chosen::add, () -> chosen.add("restart"), () -> chosen.add("close"));
            assertThat(button(panel.view(), ResolvePanel.REPLACE).state().visible()).isTrue();
            assertThat(button(panel.view(), ResolvePanel.REPLACE).state().enabled()).isTrue();
            assertThat(button(panel.view(), ResolvePanel.MERGE).state().visible()).as("files cannot merge").isFalse();
            assertThat(texts(panel.view())).contains("report.pdf", "Destination changed");
            assertThat(form(panel.view()).submission()).as("Enter decides nothing").isEqualTo(Nodes.FormSubmission.NONE);
            assertThat(primaries(panel.view())).extracting(Nodes.Button::id).as("a file's visual primary is Replace").containsExactly(ResolvePanel.REPLACE);
            assertThat(form(panel.view()).cancelAction()).as("Escape cancels").isEqualTo(ResolvePanel.CANCEL);
            emit(panel.view(), new UiEvent.Toggled(ResolvePanel.REMAINING, true));
            assertThat(node(panel.view(), ResolvePanel.REMAINING, Nodes.Toggle.class).checked()).isTrue();
            click(panel.view(), ResolvePanel.REPLACE);
            click(panel.view(), ResolvePanel.RENAME);
            click(panel.view(), ResolvePanel.RESTART);
            click(panel.view(), ResolvePanel.CANCEL);
            press(panel.view(), ResolvePanel.MERGE);
            assertThat(chosen).containsExactly(new ResolvePanel.Choice(ConflictDecision.REPLACE, true), new ResolvePanel.Choice(ConflictDecision.RENAME, false), "restart", "close");
            var folderInfo = new FileEntry("photos", FileEntry.Kind.DIRECTORY, 0, 1000, 0755, "", "dir-1");
            var folder = new TransferEntry(8, UUID.randomUUID(), "photos", "/tmp/photos", "/srv/photos", folderInfo, "", Optional.empty(),
                TransferEntry.Phase.PENDING, 0, "", TransferEntry.Publication.NONE, Optional.of(folderInfo), ConflictDecision.ASK, TransferEntry.Outcome.PENDING, "Exists");
            var merge = new ResolvePanel(new FakeKit("dev.jasper.remote"), folder, chosen::add, () -> { }, () -> { });
            assertThat(button(merge.view(), ResolvePanel.MERGE).state().visible()).isTrue();
            assertThat(button(merge.view(), ResolvePanel.REPLACE).state().visible()).as("folders merge rather than replace").isFalse();
            merge.close();
            assertThat(closed(merge.view())).isTrue();
            click(panel.view(), ResolvePanel.SKIP);
            assertThat(chosen).as("Skip carries the switch").endsWith(new ResolvePanel.Choice(ConflictDecision.SKIP, true));
        });
    }

    @Test void existingItemsAsksOnce() throws Exception {
        assertThat(ExistingItemsPanel.message(List.of("report.pdf"), 1, "prod:/srv/app")).isEqualTo("\"report.pdf\" already exists in prod:/srv/app.");
        assertThat(ExistingItemsPanel.message(List.of("a", "b", "c"), 5, "prod:/srv/app")).isEqualTo("3 of 5 items already exist in prod:/srv/app.");
        assertThat(ExistingItemsPanel.message(List.of("a"), 5, "/Users/me")).isEqualTo("1 of 5 items already exists in /Users/me.");
        edt(() -> {
            var chosen = new ArrayList<Object>();
            var panel = new ExistingItemsPanel(new FakeKit("dev.jasper.remote"), "3 of 5 items already exist in prod:/srv/app.", chosen::add, () -> chosen.add("cancel"));
            assertThat(texts(panel.view())).as("the question's title, then the literal message").containsExactly("Replace Existing Items?", "3 of 5 items already exist in prod:/srv/app.");
            click(panel.view(), ExistingItemsPanel.REPLACE);
            click(panel.view(), ExistingItemsPanel.SKIP);
            click(panel.view(), ExistingItemsPanel.CANCEL);
            assertThat(chosen).containsExactly(ConflictDecision.REPLACE, ConflictDecision.SKIP, "cancel");
            assertThat(button(panel.view(), ExistingItemsPanel.SKIP).label()).isEqualTo("Skip existing");
            assertThat(form(panel.view()).submission()).as("Enter copies nothing").isEqualTo(Nodes.FormSubmission.NONE);
            assertThat(form(panel.view()).kind()).as("the kit's compact question dialog").isEqualTo(Nodes.FormKind.PROMPT_COMPACT);
            assertThat(form(panel.view()).cancelAction()).isEqualTo(ExistingItemsPanel.CANCEL);
            panel.close();
            assertThat(closed(panel.view())).isTrue();
        });
    }

    @Test void renameUsesTheTypedNameAndEnterAppliesNothing() throws Exception {
        edt(() -> {
            var used = new ArrayList<String>();
            var panel = new RenamePanel(new FakeKit("dev.jasper.remote"), "report.pdf", used::add, () -> used.add("closed"));
            var field = node(panel.view(), RenamePanel.NAME, Nodes.Field.class);
            assertThat(field.value()).isEqualTo("report.pdf");
            assertThat(field.trailingAction()).as("Enter in the field applies nothing, as before").isEmpty();
            assertThat(form(panel.view()).submission()).as("the form opts out of Enter").isEqualTo(Nodes.FormSubmission.NONE);
            assertThat(primaries(panel.view())).extracting(Nodes.Button::id).containsExactly(RenamePanel.USE);
            assertThat(form(panel.view()).actions()).extracting(Nodes.Button::id).as("Cancel beside Use name").containsExactly(RenamePanel.CLOSE, RenamePanel.USE);
            type(panel.view(), RenamePanel.NAME, " ");
            assertThat(button(panel.view(), RenamePanel.USE).state().enabled()).as("a blank name cannot be used").isFalse();
            click(panel.view(), RenamePanel.USE);
            type(panel.view(), RenamePanel.NAME, "report (2).pdf");
            click(panel.view(), RenamePanel.USE);
            press(panel.view(), form(panel.view()).cancelAction());
            assertThat(used).containsExactly("report (2).pdf", "closed");
        });
    }

    @Test void rowOrderFollowsDisplayOrderWhenReordered() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var c = UUID.randomUUID();
        strip.rows(List.of(row(a, "40%", Optional.empty(), false), row(b, "50%", Optional.empty(), false)));
        assertThat(strip.order()).containsExactly(a, b);
        strip.rows(List.of(row(c, "30%", Optional.empty(), false), row(a, "40%", Optional.empty(), false), row(b, "50%", Optional.empty(), false)));
        assertThat(strip.order()).containsExactly(c, a, b);
        assertThat(list().items()).extracting(ListItem::id).as("rows keep their job id, so the kit keeps their components")
            .containsExactly(c.toString(), a.toString(), b.toString());
    }

    @Test void anUnchangedListRendersNothingAndAChangeRendersOnce() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        int[] renders = {0};
        strip.onChanged(() -> renders[0]++);
        strip.rows(List.of(row(a, "40%", Optional.empty(), false), row(b, "Queued", Optional.empty(), false)));
        strip.rows(List.of(row(a, "40%", Optional.empty(), false), row(b, "Queued", Optional.empty(), false)));
        assertThat(renders[0]).as("a refresh tick with nothing new").isEqualTo(1);
        strip.rows(List.of(row(a, "41%", Optional.empty(), false), row(b, "Paused", Optional.of(TransferRows.Action.RESUME), false)));
        assertThat(renders[0]).isEqualTo(2);
        assertThat(part(a, TransferStrip.statusId(a), Nodes.Text.class).text()).isEqualTo("41%");
        assertThat(part(b, TransferStrip.statusId(b), Nodes.Text.class).text()).isEqualTo("Paused");
        assertThat(part(b, TransferStrip.actionId(b), Nodes.Button.class).label()).isEqualTo("Resume");
        assertThat(list().items()).extracting(ListItem::id).containsExactly(a.toString(), b.toString());
    }

    /** Was rowButtonsAreFlatToolbarButtons: the close is an embedded ghost icon button, the action an embedded link. */
    @Test void rowButtonsAreEmbeddedGhostAndLinkButtons() {
        var id = UUID.randomUUID();
        strip.rows(List.of(row(id, "Paused", Optional.of(TransferRows.Action.RESUME), false)));
        var close = part(id, TransferStrip.closeId(id), Nodes.Button.class);
        assertThat(close.kind()).isEqualTo(Nodes.ButtonKind.GHOST);
        assertThat(close.presentation()).isEqualTo(Nodes.ButtonPresentation.EMBEDDED);
        assertThat(close.label()).isEmpty();
        assertThat(close.icon()).isEqualTo("close");
        var action = part(id, TransferStrip.actionId(id), Nodes.Button.class);
        assertThat(action.kind()).isEqualTo(Nodes.ButtonKind.LINK);
        assertThat(action.presentation()).isEqualTo(Nodes.ButtonPresentation.EMBEDDED);
        assertThat(item(id).trailing()).extracting(Nodes.Node::id).containsExactly(TransferStrip.actionId(id), TransferStrip.closeId(id));
    }
}
